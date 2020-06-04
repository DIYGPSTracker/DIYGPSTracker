package dev.csaba.diygpstracker.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import dev.csaba.diygpstracker.ApplicationSingleton
import dev.csaba.diygpstracker.BuildConfig
import dev.csaba.diygpstracker.R
import dev.csaba.diygpstracker.viewmodel.TrackerViewModel
import timber.log.Timber
import java.util.Date
import kotlin.math.*


class TrackerActivity : AppCompatActivityWithActionBar(), android.location.LocationListener,
    GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
    com.google.android.gms.location.LocationListener {

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1
        private const val GPS_UPDATE_TIME_MS = 10000
        private const val DISPLACEMENT_THRESHOLD = 1.0f
        private const val EQATORIAL_EARTH_RADIUS = 6378.1370
        private const val D2R = Math.PI / 180.0
    }

    private lateinit var viewModel: TrackerViewModel
    private lateinit var locationManager: LocationManager
    private lateinit var batteryManager: BatteryManager
    private lateinit var googleApiClient: GoogleApiClient
    private lateinit var locationRequest: LocationRequest
    @Volatile private var gotFirstObserve = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracker)

        val appSingleton = application as ApplicationSingleton
        val assetId = intent.getStringExtra("assetId")
        if (assetId != null && appSingleton.firestore != null) {
            viewModel = TrackerViewModel(appSingleton.firestore!!, assetId)
            viewModel.asset.observe(this, Observer {
                if (!gotFirstObserve) {
                    gotFirstObserve = true
                    if (BuildConfig.DEBUG && assetId != it.id) {
                        error("Assertion failed")
                    }
                    viewModel.remoteAssetId = it.id
                    viewModel.lastLock = it.lock
                    viewModel.lockLat = it.lockLat
                    viewModel.lockLon = it.lockLon
                    viewModel.lockRadius = it.lockRadius
                    viewModel.lastPeriodInterval = it.periodInterval
                    obtainLocationPermissionAndStartTracking()
                } else {
                    if (it.lock != viewModel.lastLock) {
                        viewModel.lastLock = it.lock
                        if (it.lock) {
                            // Manager is locking the asset => need to setup geo fence
                            viewModel.geoFenceLatch = true
                        }
                    }
                    if (it.periodInterval != viewModel.lastPeriodInterval) {
                        // Manager manually overrides the current poll interval
                        reScheduleLocationUpdates()
                    }
                }
            })
        }
    }

    override fun onDestroy() {
        removeUpdates()
        super.onDestroy()
    }

    private fun isPermissionGranted() : Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // Checks if users have given their location and sets location enabled if so.
    private fun obtainLocationPermissionAndStartTracking() {
        if (isPermissionGranted()) {
            startGpsTracking()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf<String>(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        }
    }

    // Callback for the result from requesting permissions.
    // This method is invoked for every call on requestPermissions(android.app.Activity, String[],
    // int).
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray) {
        // Check if location permissions are granted and if so enable the
        // location data layer.
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
                obtainLocationPermissionAndStartTracking()
            }
        }
    }

    private fun getLocationProvider(): String? {
        val criteria = Criteria()
        criteria.accuracy = Criteria.ACCURACY_FINE
        criteria.isSpeedRequired = true
        criteria.isAltitudeRequired = false
        criteria.isBearingRequired = false
        criteria.isCostAllowed = true
        criteria.powerRequirement = Criteria.POWER_MEDIUM
        return locationManager.getBestProvider(criteria, true)
    }

    private fun buildGoogleApiClient() {
        googleApiClient = GoogleApiClient.Builder(applicationContext)
            .addConnectionCallbacks(this)
            .addOnConnectionFailedListener(this)
            .addApi(LocationServices.API)
            .build()
        googleApiClient.connect()
    }

    private fun getRecentPeriodIntervalOrDefault(): Long {
        return if (viewModel.lastPeriodInterval == 0) GPS_UPDATE_TIME_MS.toLong()
                else viewModel.lastPeriodInterval.toLong()
    }

    private fun getLocationRequest() {
        locationRequest = LocationRequest()
        locationRequest.interval = getRecentPeriodIntervalOrDefault()
        locationRequest.smallestDisplacement = DISPLACEMENT_THRESHOLD
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    @SuppressLint("MissingPermission")
    private fun scheduleLocationUpdates() {
        // 1.1. Get Location Manager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        // 1.2. Get Location Provider
        val locationProvider = getLocationProvider()
        if (locationProvider != null)
        {
            // 1.3. Schedule Location Manager location updates
            locationManager.requestLocationUpdates(
                locationProvider,
                getRecentPeriodIntervalOrDefault(),
                DISPLACEMENT_THRESHOLD,
                this
            )
        }
    }

    private fun startGpsTracking() {
        batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        // https://appus.software/blog/difference-between-locationmanager-and-google-location-api-services
        // 1. LocationManager technique
        scheduleLocationUpdates()

        // 2. Google API Location Services
        // 2.1. Construct Location Request
        getLocationRequest()
        // 2.2. Get the Google API Client
        buildGoogleApiClient()
    }

    private fun removeUpdates() {
        locationManager.removeUpdates(this)
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this)
    }

    @SuppressLint("MissingPermission")
    private fun reScheduleLocationUpdates() {
        removeUpdates()
        scheduleLocationUpdates()
        getLocationRequest()
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this)
    }

    private fun getBatteryLevel(): Int {
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun haversineGPSDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lonDiff = (lon2 - lon1) * D2R
        val latDiff = (lat2 - lat1) * D2R
        val latSin = sin(latDiff / 2.0)
        val lonSin = sin(lonDiff / 2.0)
        val a = latSin * latSin + (cos(lat1 * D2R) * cos(lat2 * D2R) * lonSin * lonSin)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        return EQATORIAL_EARTH_RADIUS * c
    }

    override fun onLocationChanged(location: Location?) {
        if (location != null) {
            val batteryLevel = getBatteryLevel()
            viewModel.addReport(location.latitude, location.longitude, location.speed, batteryLevel)
            val latTextView = findViewById<View>(R.id.latValue) as TextView
            latTextView.text = location.latitude.toString()
            val lonTextView = findViewById<View>(R.id.lonValue) as TextView
            lonTextView.text = location.longitude.toString()
            val speedTextView = findViewById<View>(R.id.speedValue) as TextView
            speedTextView.text = location.speed.toString()
            val battTextView = findViewById<View>(R.id.battValue) as TextView
            battTextView.text = batteryLevel.toString()
            val timeStampTextView = findViewById<View>(R.id.timeStamp) as TextView
            timeStampTextView.text = Date().toString()

            // Asset is being locked, waiting for the location of the lock
            if (viewModel.geoFenceLatch) {
                viewModel.setAssetLockLocation(location.latitude, location.longitude)
                viewModel.geoFenceLatch = false
            }
            // Manual geo fence checking
            if (viewModel.lastLock && abs(viewModel.lockLat) > 1e-6 && abs(viewModel.lockLon) > 1e-6) {
                val gpsDistance = haversineGPSDistance(viewModel.lockLat, viewModel.lockLon, location.latitude, location.longitude)
                // Asset exited the geo-fence
                if (gpsDistance >= viewModel.lockRadius) {
                    // Kick in the interval
                    viewModel.setAssetPeriodInterval(10)
                    // TODO: this arrives back to the observer and differential will be calculated
                    // TODO: and reSchedule will be applied if needed (?)
                }
            }
        }
    }

    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Timber.d("Location Service Status changes ${status}")
    }

    override fun onProviderEnabled(provider: String?) {
        Timber.d("Location Service Provider ${provider} enabled")
    }

    override fun onProviderDisabled(provider: String?) {
        Timber.d("Location Service Provider ${provider} disabled")
    }

    @SuppressLint("MissingPermission")
    override fun onConnected(extras: Bundle?) {
        // 2.3. Schedule Fused Location updates
        Timber.d("Google Api Client connected")
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this)
    }

    override fun onConnectionSuspended(status: Int) {
        Timber.d("Google Api Client suspended ${status}")
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Timber.d("Google Api Client connection failed: ${connectionResult.errorCode} ${connectionResult.errorMessage}")
    }
}
