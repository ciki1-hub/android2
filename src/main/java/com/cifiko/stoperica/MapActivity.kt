package com.cifiko.stoperica

import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import android.os.*
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MapActivity : AppCompatActivity(), OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    // Map variables
    private lateinit var mMap: GoogleMap
    private var startLapMarker: Marker? = null
    private val sectorMarkers = mutableListOf<Marker>()
    private var startLapPosition: LatLng? = null
    private val sectorPositions = mutableListOf<LatLng>()
    private var currentPolyline: Polyline? = null
    private val pathPoints = mutableListOf<LatLng>()

    // Location variables
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastTriggerTime = 0L
    private var isStartLapTriggered = false
    private val LOCATION_PERMISSION_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Load saved markers
        if (savedInstanceState != null) {
            restoreMarkers(savedInstanceState)
        } else {
            loadMarkers()
        }

        // Initialize map
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Setup reset button
        findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.resetMarkersButton).setOnClickListener {
            resetMarkers()
        }

        // Initialize location callback
        setupLocationCallback()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap.apply {
            mapType = GoogleMap.MAP_TYPE_SATELLITE
            setOnMarkerClickListener(this@MapActivity)
            uiSettings.isZoomControlsEnabled = true
        }

        checkLocationPermission()

        // Add existing markers to map
        startLapPosition?.let { position ->
            startLapMarker = mMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("Start Lap")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 17f))
        }

        sectorPositions.forEach { position ->
            val sectorMarker = mMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("Sector ${sectorMarkers.size + 1}")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
            )
            sectorMarker?.let { sectorMarkers.add(it) }
        }

        // Handle map clicks
        mMap.setOnMapClickListener { latLng ->
            if (startLapMarker == null) {
                // Add start lap marker
                startLapMarker = mMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("Start Lap")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                )
                startLapPosition = latLng
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
            } else {
                // Add sector marker
                val sectorMarker = mMap.addMarker(
                    MarkerOptions()
                        .position(latLng)
                        .title("Sector ${sectorMarkers.size + 1}")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                )
                sectorMarker?.let {
                    sectorMarkers.add(it)
                    sectorPositions.add(latLng)
                }
            }
            saveMarkers()
        }
    }

    private fun setupLocationCallback() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val latLng = LatLng(location.latitude, location.longitude)
                    pathPoints.add(latLng)
                    updatePathPolyline()
                    checkProximityToMarkers(location)

                    if (pathPoints.size == 1) {
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 17f))
                    }
                }
            }
        }
    }

    private fun updatePathPolyline() {
        currentPolyline?.remove()
        if (pathPoints.size > 1) {
            currentPolyline = mMap.addPolyline(
                PolylineOptions()
                    .addAll(pathPoints)
                    .color(Color.BLUE)
                    .width(5f)
            )
        }
    }

    private fun checkProximityToMarkers(currentLocation: Location) {
        val proximityThreshold = 20.0
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastTriggerTime < 5000) return

        startLapPosition?.let { position ->
            val markerLocation = Location("marker").apply {
                latitude = position.latitude
                longitude = position.longitude
            }
            val distance = currentLocation.distanceTo(markerLocation)
            if (distance <= proximityThreshold) {
                lastTriggerTime = currentTime
                if (!isStartLapTriggered) {
                    sendBroadcast(Intent("com.cifiko.stoperica.START_LAP"))
                    isStartLapTriggered = true
                    Toast.makeText(this, "Start Lap Triggered", Toast.LENGTH_SHORT).show()
                } else {
                    sendBroadcast(Intent("com.cifiko.stoperica.LAP"))
                    Toast.makeText(this, "Lap Triggered", Toast.LENGTH_SHORT).show()
                }
            }
        }

        sectorPositions.forEachIndexed { index, position ->
            val markerLocation = Location("marker").apply {
                latitude = position.latitude
                longitude = position.longitude
            }
            val distance = currentLocation.distanceTo(markerLocation)
            if (distance <= proximityThreshold) {
                lastTriggerTime = currentTime
                sendBroadcast(Intent("com.cifiko.stoperica.SECTOR").apply {
                    putExtra("sectorNumber", index + 1)
                })
                Toast.makeText(this, "Sector ${index + 1} Triggered", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onMarkerClick(marker: Marker): Boolean {
        when (marker) {
            startLapMarker -> Toast.makeText(this, "Start Lap Marker", Toast.LENGTH_SHORT).show()
            in sectorMarkers -> Toast.makeText(this, "Sector Marker: ${marker.title}", Toast.LENGTH_SHORT).show()
        }
        return true
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST
            )
        } else {
            enableMyLocation()
        }
    }

    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            mMap.isMyLocationEnabled = true
            mMap.uiSettings.isMyLocationButtonEnabled = true
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(3000)
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun checkLocationServices(): Boolean {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun promptEnableLocationServices() {
        AlertDialog.Builder(this)
            .setTitle("Enable Location Services")
            .setMessage("Please enable location services to use this feature.")
            .setPositiveButton("Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun resetMarkers() {
        startLapMarker?.remove()
        startLapMarker = null
        startLapPosition = null
        sectorMarkers.forEach { it.remove() }
        sectorMarkers.clear()
        sectorPositions.clear()
        currentPolyline?.remove()
        currentPolyline = null
        pathPoints.clear()
        saveMarkers()
    }

    @SuppressLint("NewApi")
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable("startLapPosition", startLapPosition)
        outState.putParcelableArrayList("sectorPositions", ArrayList(sectorPositions))
    }

    override fun onPause() {
        super.onPause()
        saveMarkers()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    private fun saveMarkers() {
        getSharedPreferences("MapMarkers", MODE_PRIVATE).edit {
            putString("startLapPosition", Gson().toJson(startLapPosition))
            putString("sectorPositions", Gson().toJson(sectorPositions))
        }
    }

    @SuppressLint("NewApi")
    private fun restoreMarkers(savedInstanceState: Bundle) {
        startLapPosition = savedInstanceState.getParcelable("startLapPosition", LatLng::class.java)
        sectorPositions.clear()
        sectorPositions.addAll(
            savedInstanceState.getParcelableArrayList("sectorPositions", LatLng::class.java) ?: emptyList()
        )
    }

    private fun loadMarkers() {
        val prefs = getSharedPreferences("MapMarkers", MODE_PRIVATE)
        startLapPosition = Gson().fromJson(prefs.getString("startLapPosition", null), LatLng::class.java)
        sectorPositions.clear()
        Gson().fromJson<ArrayList<LatLng>>(
            prefs.getString("sectorPositions", null),
            object : TypeToken<ArrayList<LatLng>>() {}.type
        )?.let { sectorPositions.addAll(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}