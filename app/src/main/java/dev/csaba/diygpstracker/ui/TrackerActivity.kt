package dev.csaba.diygpstracker.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Observer
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import dev.csaba.diygpstracker.ApplicationSingleton
import dev.csaba.diygpstracker.R
import dev.csaba.diygpstracker.data.Report
import dev.csaba.diygpstracker.viewmodel.TrackerViewModel
import java.text.SimpleDateFormat


class TrackerActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1
    }

    private lateinit var map: GoogleMap
    private lateinit var viewModel: TrackerViewModel
    private var isRestore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isRestore = savedInstanceState != null
        setContentView(R.layout.activity_maps)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        val uiSettings = map.uiSettings
        uiSettings.isZoomControlsEnabled = true
        uiSettings.isCompassEnabled = true
        uiSettings.isMapToolbarEnabled = true
        uiSettings.setAllGesturesEnabled(true)

        setMapLongClick(map)
        setMarkerClick(map)
        enableMyLocation()

        val appSingleton = application as ApplicationSingleton
        val assetId = intent.getStringExtra("assetId")
        if (assetId != null && appSingleton.firestore != null) {
            val lookBackMinutes = intent.getIntExtra("lookBackMinutes", 10)
            viewModel = TrackerViewModel(appSingleton.firestore!!, assetId, lookBackMinutes)
            viewModel.reportList.observe(this, Observer {
                addPins(it)
            })
        }
    }

    fun addPins(pins: List<Report>) {
        val options = PolylineOptions()
        options.color(Color.RED)

        var lastLocation = LatLng(.0, .0)
        for (pin in pins) {
            val latLng = LatLng(pin.lat, pin.lon)

            // Don't record too close consecutive markers (avoid unnecessary crowding)
            // 10^-5 is about 1.1m (https://en.wikipedia.org/wiki/Decimal_degrees)
            if (Math.abs(lastLocation.latitude) > 1e-6 && Math.abs(lastLocation.longitude) > 1e-6 ||
                Math.abs(lastLocation.latitude - latLng.latitude) < 1e-5 &&
                Math.abs(lastLocation.longitude - latLng.longitude) < 1e-5)
            {
                continue
            }
            lastLocation = latLng
            options.add(latLng)

            val currentLocale = ConfigurationCompat.getLocales(resources.configuration)[0]
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", currentLocale)
            val dateString = dateFormat.format(pin.created)
            val timeFormat = SimpleDateFormat("HH:mm:ss", currentLocale)
            val timeString = timeFormat.format(pin.created)
            val marker = MarkerOptions().position(latLng).title(timeString).snippet(dateString)
            map.addMarker(marker)
        }

        map.addPolyline(options)

//        val cameraPosition = CameraPosition.Builder()
//            .target(LatLng(pins[0].lat, pins[0].lon))
//            .zoom(18f).build()
//        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
    }

    // Initializes contents of Activity's standard options menu. Only called the first time options
    // menu is displayed.
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.map_options, menu)
        return true
    }

    // Called whenever an item in your options menu is selected.
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        // Change the map type based on the user's selection.
        R.id.normal_map -> {
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            true
        }
        R.id.hybrid_map -> {
            map.mapType = GoogleMap.MAP_TYPE_HYBRID
            true
        }
        R.id.satellite_map -> {
            map.mapType = GoogleMap.MAP_TYPE_SATELLITE
            true
        }
        R.id.terrain_map -> {
            map.mapType = GoogleMap.MAP_TYPE_TERRAIN
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // Called when user makes a long press gesture on the map.
    private fun setMapLongClick(map: GoogleMap) {
        map.setOnMapLongClickListener { latLng ->
            val navigationIntentUri: Uri =
                Uri.parse("google.navigation:q=${latLng.latitude},${latLng.longitude}")
            val mapIntent = Intent(Intent.ACTION_VIEW, navigationIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            mapIntent.setClassName("com.google.android.apps.maps",
                "com.google.android.maps.MapsActivity")
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent)
            }
        }
    }

    private fun setMarkerClick(map: GoogleMap) {
        map.setOnMarkerClickListener(object : GoogleMap.OnMarkerClickListener {
            override fun onMarkerClick(marker: Marker): Boolean {
                marker.showInfoWindow()
                return true
            }
        })
    }

    // Checks that users have given permission
    private fun isPermissionGranted() : Boolean {
       return ContextCompat.checkSelfPermission(
            this,
           Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // Checks if users have given their location and sets location enabled if so.
    private fun enableMyLocation() {
        if (isPermissionGranted()) {
            map.isMyLocationEnabled = true

            if (!isRestore) {
                val locationManager =
                    getSystemService(LOCATION_SERVICE) as LocationManager
                val locationProvider = LocationManager.NETWORK_PROVIDER
                @SuppressLint("MissingPermission") val lastKnownLocation =
                    locationManager.getLastKnownLocation(locationProvider)

                // Default to the lattitude and longitude of the Googleplex if no location.
                val userLat = lastKnownLocation?.latitude ?: 37.422160
                val userLong = lastKnownLocation?.longitude ?: -122.084270
                val userLatLng = LatLng(userLat, userLong)
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 10f))
            }
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
                enableMyLocation()
            }
        }
    }
}
