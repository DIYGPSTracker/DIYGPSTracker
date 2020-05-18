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
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import dev.csaba.diygpstracker.ApplicationSingleton
import dev.csaba.diygpstracker.R
import dev.csaba.diygpstracker.viewmodel.TrackerViewModel
import timber.log.Timber
import java.util.*


class TrackerActivity : AppCompatActivityWithActionBar(), android.location.LocationListener,
    GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
    com.google.android.gms.location.LocationListener {

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1
        private const val GPS_UPDATE_TIME_MS = 10000L
        private const val DISPLACEMENT_THRESHOLD = 1.0f
    }

    private lateinit var viewModel: TrackerViewModel
    private var isRestore = false
    private lateinit var locationManager: LocationManager
    private lateinit var batteryManager: BatteryManager
    private lateinit var googleApiClient: GoogleApiClient
    private lateinit var locationRequest: LocationRequest

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isRestore = savedInstanceState != null
        setContentView(R.layout.activity_tracker)

        val appSingleton = application as ApplicationSingleton
        val assetId = intent.getStringExtra("assetId")
        if (assetId != null && appSingleton.firestore != null) {
            viewModel = TrackerViewModel(appSingleton.firestore!!, assetId)
            obtainLocationPermission()
        }
    }

    private fun isPermissionGranted() : Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // Checks if users have given their location and sets location enabled if so.
    private fun obtainLocationPermission() {
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
                obtainLocationPermission()
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
        criteria.powerRequirement = Criteria.POWER_LOW
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

    private fun getLocationRequest() {
        locationRequest = LocationRequest()
        locationRequest.interval = GPS_UPDATE_TIME_MS
        locationRequest.smallestDisplacement = DISPLACEMENT_THRESHOLD
        locationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    @SuppressLint("MissingPermission")
    private fun startGpsTracking() {
        batteryManager = getSystemService(BATTERY_SERVICE) as BatteryManager
        // https://appus.software/blog/difference-between-locationmanager-and-google-location-api-services
        // 1. LocationManager technique
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val locationProvider = getLocationProvider()
        if (locationProvider != null) {
            locationManager.requestLocationUpdates(
                locationProvider,
                GPS_UPDATE_TIME_MS,
                DISPLACEMENT_THRESHOLD,
                this
            )
        }

        // 2. Google API Location Services
        buildGoogleApiClient()
        getLocationRequest()
    }

    private fun getBatteryLevel(): Double {
        return batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).toDouble()
    }

    override fun onLocationChanged(location: Location?) {
        if (location != null) {
            val batteryLevel = getBatteryLevel()
            viewModel.addReport(location.latitude, location.longitude, batteryLevel)
            val latTextView = findViewById<View>(R.id.latValue) as TextView
            latTextView.text = location.latitude.toString()
            val lonTextView = findViewById<View>(R.id.lonValue) as TextView
            lonTextView.text = location.longitude.toString()
            val battTextView = findViewById<View>(R.id.battValue) as TextView
            battTextView.text = batteryLevel.toString()
            val timeStampTextView = findViewById<View>(R.id.timeStamp) as TextView
            timeStampTextView.text = Date().toString()
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

    override fun onConnected(extras: Bundle?) {
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
