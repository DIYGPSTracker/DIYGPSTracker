package dev.csaba.diygpstracker.ui

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.material.snackbar.Snackbar
import dev.csaba.diygpstracker.ApplicationSingleton
import dev.csaba.diygpstracker.BuildConfig
import dev.csaba.diygpstracker.R
import dev.csaba.diygpstracker.viewmodel.TrackerViewModel
import timber.log.Timber
import java.util.Date
import kotlin.math.*


class TrackerActivity : AppCompatActivityWithActionBar(), android.location.LocationListener,
    GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
    LocationListener {

    companion object {
        private const val GPS_UPDATE_TIME_MS = 10000
        private const val DISPLACEMENT_THRESHOLD = 1.0f
        private const val EQATORIAL_EARTH_RADIUS = 6378137.0  // in m and not km
        private const val D2R = Math.PI / 180.0

        private const val REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE = 34
        private const val REQUEST_TURN_DEVICE_LOCATION_ON = 29
        private const val LOCATION_PERMISSION_INDEX = 0

        internal const val ACTION_GEO_FENCE_EVENT = "TrackerActivity.action.ACTION_GEOFENCE_EVENT"
        internal const val GEO_FENCE_SINGLETON_ID = "asset_on_demand_geofence"
        internal const val GEO_FENCE_SINGLETON_INDEX = 1
    }

    private lateinit var viewModel: TrackerViewModel
    private lateinit var locationManager: LocationManager
    private lateinit var geoFencingClient: GeofencingClient
    private lateinit var batteryManager: BatteryManager
    private lateinit var googleApiClient: GoogleApiClient
    @Volatile private var gotFirstObserve = false

    private val runningQOrLater =
        android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q

    /*
     * Triggered by the Geofence. We'll need to crank up the refresh interval and send notification
     */
    inner class GeoFenceBroadcastReceiver : BroadcastReceiver() {
        private fun errorMessage(context: Context, errorCode: Int): String {
            val resources = context.resources
            return when (errorCode) {
                GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE -> resources.getString(
                    R.string.geofence_not_available
                )
                GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES -> resources.getString(
                    R.string.geofence_too_many_geofences
                )
                GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS -> resources.getString(
                    R.string.geofence_too_many_pending_intents
                )
                else -> resources.getString(R.string.unknown_geofence_error)
            }
        }

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_GEO_FENCE_EVENT) {
                val geoFencingEvent = GeofencingEvent.fromIntent(intent)
                if (geoFencingEvent == null) {
                    Timber.e("Cannot instantiate GeofencingEvent from Intent")
                    return
                }

                if (geoFencingEvent.hasError()) {
                    val errorMessage = errorMessage(context, geoFencingEvent.errorCode)
                    Timber.e(errorMessage)
                    return
                }

                if (geoFencingEvent.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
                    Timber.w(context.getString(R.string.geofence_exited))

                    val fenceId = when {
                        geoFencingEvent.triggeringGeofences?.isNotEmpty() ?: false ->
                            geoFencingEvent.triggeringGeofences!![0].requestId
                        else -> {
                            Timber.e("No Geofence Trigger Found! Abort mission!")
                            return
                        }
                    }

                    if (fenceId != GEO_FENCE_SINGLETON_ID) {
                        Timber.e("Not our geofence => no action on our end")
                        return
                    }

                    geoFenceExitedHandler(true)
                }
            }
        }
    }

    // A PendingIntent for the Broadcast Receiver that handles geofence transitions.
    private val geoFencePendingIntent: PendingIntent by lazy {
        val intent = Intent(this, GeoFenceBroadcastReceiver::class.java)
        intent.action = ACTION_GEO_FENCE_EVENT
        // Use FLAG_UPDATE_CURRENT so that you get the same pending intent back when calling
        // addGeofences() and removeGeofences().
        PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracker)

        val appSingleton = application as ApplicationSingleton
        val assetId = intent.getStringExtra("assetId")
        if (assetId != null && appSingleton.firestore != null) {
            viewModel = TrackerViewModel(appSingleton.firestore!!, assetId)
            viewModel.asset.observe(this, {
                if (!gotFirstObserve) {
                    gotFirstObserve = true
                    if (BuildConfig.DEBUG && assetId != it.id) {
                        error("Assertion failed")
                    }
                    viewModel.updateState(it)
                    obtainLocationPermissionAndStartTracking()
                } else {
                    val lockChanged = it.lock != viewModel.lastLock
                    val intervalChanged = it.periodInterval != viewModel.lastPeriodInterval
                    val radiusChanged = it.lockRadius != viewModel.lockRadius
                    viewModel.updateState(it)
                    if (lockChanged) {
                        if (it.lock) {
                            // Manager is locking the asset => need to setup geo fence
                            viewModel.geoFenceLatch = true
                        } else {
                            removeGeoFences()
                        }
                    }
                    if (intervalChanged) {
                        // Manager manually overrides the current poll interval
                        reScheduleLocationUpdates()
                    }
                    if (radiusChanged && !viewModel.geoFenceLatch) {
                        removeGeoFences(true)
                        addNativeGeoFence(it.lockLat, it.lockLon)
                    }
                }
            })
        }
    }

    override fun onDestroy() {
        removeGeoFences()
        removeUpdates()
        super.onDestroy()
    }

    /*
     *  When we get the result from asking the user to turn on device location, we call
     *  checkLocationIsOnAndStartGpsTracking again to make sure it's actually on, but
     *  we don't resolve the check to keep the user from seeing an endless loop.
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TURN_DEVICE_LOCATION_ON) {
            // We don't rely on the result code, but just check the location setting again
            checkLocationIsOnAndStartGpsTracking(false)
        }
    }

    /*
     *  Uses the Location Client to check the current state of location settings, and gives the user
     *  the opportunity to turn on location services within our app.
     */
    private fun checkLocationIsOnAndStartGpsTracking(resolve: Boolean = true) {
        val locationRequest = LocationRequest.create().apply {
            smallestDisplacement = DISPLACEMENT_THRESHOLD
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        val builder = LocationSettingsRequest.Builder().addLocationRequest(locationRequest)

        val settingsClient = LocationServices.getSettingsClient(this)
        val locationSettingsResponseTask =
            settingsClient.checkLocationSettings(builder.build())

        locationSettingsResponseTask.addOnFailureListener { exception ->
            if (exception is ResolvableApiException && resolve) {
                showBgLocationWarning(R.string.location_required_error) { _, _ ->
                    // Location settings are not satisfied, but this can be fixed
                    // by showing the user a dialog.
                    try {
                        // Show the dialog by calling startResolutionForResult(),
                        // and check the result in onActivityResult().
                        exception.startResolutionForResult(
                            this@TrackerActivity,
                            REQUEST_TURN_DEVICE_LOCATION_ON
                        )
                    } catch (sendEx: IntentSender.SendIntentException) {
                        Timber.e(sendEx, getString(R.string.resolution_settings_error))
                    }
                }
            } else {
                showBgLocationWarning(R.string.location_required_error) { _, _ ->
                    checkLocationIsOnAndStartGpsTracking()
                }
            }
        }
        locationSettingsResponseTask.addOnCompleteListener {
            if (it.isSuccessful) {
                startGpsTracking()
            }
        }
    }

    private fun showBgLocationWarning(@StringRes resourceId: Int, listener: DialogInterface.OnClickListener) {
        val builder = AlertDialog.Builder(this)
        val bgLocationWarning = resources.getString(resourceId)
        val title = resources.getString(R.string.location_warning_title)
        builder.setMessage(bgLocationWarning).setTitle(title).setPositiveButton("OK", listener)
        val dialog = builder.create()
        dialog.show()
    }

    /*
     *  Determines whether the app has the appropriate permissions across Android 10+ and all other
     *  Android versions.
     */
    @TargetApi(29)
    private fun isForegroundLocationPermissionApproved(): Boolean {
        return ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    /*
     *  Requests ACCESS_FINE_LOCATION.
     */
    @TargetApi(29)
    private fun requestForegroundLocationPermissions() {
        showBgLocationWarning(R.string.location_warning) { _, _ ->
            Timber.d("Request foreground only location permission")
            ActivityCompat.requestPermissions(
                this@TrackerActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_FOREGROUND_ONLY_PERMISSIONS_REQUEST_CODE
            )
        }
    }

    // Checks if users have given their location and sets location enabled if so.
    private fun obtainLocationPermissionAndStartTracking() {
        if (isForegroundLocationPermissionApproved()) {
            checkLocationIsOnAndStartGpsTracking()
        } else {
            requestForegroundLocationPermissions()
        }
    }

    // Callback for the result from requesting permissions.
    // This method is invoked for every call on requestPermissions(android.app.Activity, String[],
    // int).
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>,
        grantResults: IntArray
    )
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (
            grantResults.isEmpty() ||
            grantResults[LOCATION_PERMISSION_INDEX] == PackageManager.PERMISSION_DENIED)
        {
            // Permission denied.
            Snackbar.make(
                window.decorView.rootView,
                R.string.permission_denied_explanation,
                Snackbar.LENGTH_INDEFINITE
            ).setAction(R.string.settings) {
                // Displays App settings screen.
                startActivity(Intent().apply {
                    action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                    data = Uri.fromParts("package", BuildConfig.APPLICATION_ID, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
            }.show()
        } else {
            obtainLocationPermissionAndStartTracking()
        }
    }

    private fun getLocationProvider(): String? {
        val criteria = Criteria().apply {
            accuracy = Criteria.ACCURACY_FINE
            isSpeedRequired = true
            isAltitudeRequired = false
            isBearingRequired = false
            isCostAllowed = true
            powerRequirement = Criteria.POWER_MEDIUM
        }
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

    private fun getLocationRequest(): LocationRequest {
        return LocationRequest().apply {
            interval = getRecentPeriodIntervalOrDefault()
            smallestDisplacement = DISPLACEMENT_THRESHOLD
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
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
        // 0. GeoFencing setup
        geoFencingClient = LocationServices.getGeofencingClient(this)
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
        LocationServices.FusedLocationApi.requestLocationUpdates(
            googleApiClient,
            getLocationRequest(),
            this
        )
    }

    /**
     * Removes geofences. This method should be called after the user has granted the location
     * permission.
     */
    private fun removeGeoFences(reinstateAfter: Boolean = false) {
        if (!isForegroundLocationPermissionApproved()) {
            return
        }
        geoFencingClient.removeGeofences(geoFencePendingIntent)?.run {
            addOnSuccessListener {
                viewModel.geoFenceIndex = 0
                // GeoFences removed
                Timber.d(getString(R.string.geofences_removed))
                Snackbar.make(
                    window.decorView.rootView,
                    getString(R.string.geofences_removed),
                    Snackbar.LENGTH_SHORT
                ).show()
            }
            addOnFailureListener {
                // Failed to remove geofences
                Timber.d(getString(R.string.geofences_not_removed))
            }
        }
    }

    /*
     * Adds a Geofence for the current clue if needed, and removes any existing Geofence. This
     * method should be called after the user has granted the location permission.  If there are
     * no more geofences, we remove the geofence and let the viewmodel know that the ending hint
     * is now "active."
     */
    @SuppressLint("MissingPermission")
    private fun addNativeGeoFence(lat: Double, lon: Double) {
        if (viewModel.geoFenceIndex > 0)
            return

        viewModel.geoFenceIndex = GEO_FENCE_SINGLETON_INDEX
        // Build the Geofence Object
        val geofence = Geofence.Builder()
            // Set the request ID, string to identify the geofence.
            .setRequestId(GEO_FENCE_SINGLETON_ID)
            // Set the circular region of this geofence.
            .setCircularRegion(
                viewModel.lockLat,
                viewModel.lockLon,
                viewModel.lockRadius.toFloat()
            )
            // This geofence won't expire until the asset is unlocked
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            // Set the transition types of interest. Alerts are only generated for these
            // transition. We track entry and exit transitions in this sample.
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        // Build the geofence request
        val geofencingRequest = GeofencingRequest.Builder()
            // The INITIAL_TRIGGER_EXIT flag indicates that geofencing service should trigger a
            // GEOFENCE_TRANSITION_EXIT notification when the geofence is added and if the device
            // is already outside that geofence.
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_EXIT)
            // Add the geofences to be monitored by geofencing service.
            .addGeofence(geofence)
            .build()

        // First, remove any existing geofences that use our pending intent
        geoFencingClient.removeGeofences(geoFencePendingIntent)?.run {
            // Regardless of success/failure of the removal, add the new geofence
            addOnCompleteListener {
                // Add the new geofence request with the new geofence
                geoFencingClient.addGeofences(geofencingRequest, geoFencePendingIntent)?.run {
                    addOnSuccessListener {
                        // Geofence added.
                        Snackbar.make(
                            window.decorView.rootView,
                            getString(R.string.geofence_added),
                            Snackbar.LENGTH_SHORT
                        ).show()
                        Timber.d("Added Geofence ${geofence.requestId}")
                        // Tell the viewmodel that we've reached the end of the game and
                        // activated the last "geofence" --- by removing the Geofence.
                        viewModel.geoFenceIndex = GEO_FENCE_SINGLETON_INDEX
                    }
                    addOnFailureListener {
                        // Failed to add geofence.
                        Snackbar.make(
                            window.decorView.rootView,
                            getString(R.string.geofence_not_added),
                            Snackbar.LENGTH_SHORT
                        ).show()
                        if (it.message != null) {
                            Timber.e(it, getString(R.string.geofence_not_added))
                        }
                    }
                }
            }
        }
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

    private fun geoFenceExitedHandler(nativeTrigger: Boolean) {
        viewModel.handleGeoFenceExited(10, true, nativeTrigger)
        // TODO: this arrives back to the observer and differential will be calculated
        // TODO: and reSchedule will be applied if needed (?)
    }

    override fun onLocationChanged(location: Location) {
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
            addNativeGeoFence(location.latitude, location.longitude)
            viewModel.geoFenceLatch = false
        }
        // Manual geo fence checking
        if (viewModel.lastLock && !viewModel.lockManualAlert &&
            abs(viewModel.lockLat) > 1e-6 && abs(viewModel.lockLon) > 1e-6)
        {
            val gpsDistance = haversineGPSDistance(
                viewModel.lockLat, viewModel.lockLon,
                location.latitude, location.longitude
            )
            // Asset exited the geo-fence
            if (gpsDistance >= viewModel.lockRadius) {
                geoFenceExitedHandler(false)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String, status: Int, extras: Bundle) {
        Timber.d("Location Service Status changes $status")
    }

    override fun onProviderEnabled(provider: String) {
        Timber.d("Location Service Provider $provider enabled")
    }

    override fun onProviderDisabled(provider: String) {
        Timber.d("Location Service Provider $provider disabled")
    }

    @SuppressLint("MissingPermission")
    override fun onConnected(extras: Bundle?) {
        // 2.3. Schedule Fused Location updates
        Timber.d("Google Api Client connected")
        LocationServices.FusedLocationApi.requestLocationUpdates(
            googleApiClient,
            getLocationRequest(),
            this
        )
    }

    override fun onConnectionSuspended(status: Int) {
        Timber.d("Google Api Client suspended $status")
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Timber.d("Google Api Client connection failed: ${connectionResult.errorCode} ${connectionResult.errorMessage}")
    }
}
