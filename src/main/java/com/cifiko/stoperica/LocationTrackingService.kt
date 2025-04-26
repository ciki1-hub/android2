package com.cifiko.stoperica

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng

class LocationTrackingService : Service() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var lastTriggerTime = 0L
    private var isStartLapTriggered = false

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        startForegroundService()
        startLocationUpdates()
    }

    private fun startForegroundService() {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "location_tracking_service"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MapActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Location Tracking")
            .setContentText("Tracking your location for race events")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(1, notification)
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("LocationTrackingService", "Location permissions not granted")
            stopSelf()
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
            .setMinUpdateIntervalMillis(3000)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    checkProximityToMarkers(location)
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun checkProximityToMarkers(currentLocation: Location) {
        val proximityThreshold = 20.0
        val currentTime = System.currentTimeMillis()

        if (currentTime - lastTriggerTime < 5000) return

        startLapLatLng?.let { latLng ->
            val markerLocation = Location("marker").apply {
                latitude = latLng.latitude
                longitude = latLng.longitude
            }
            val distance = currentLocation.distanceTo(markerLocation)
            if (distance <= proximityThreshold) {
                lastTriggerTime = currentTime
                if (!isStartLapTriggered) {
                    sendBroadcast(Intent("com.cifiko.stoperica.START_LAP").setPackage(packageName))
                    isStartLapTriggered = true
                } else {
                    sendBroadcast(Intent("com.cifiko.stoperica.LAP").setPackage(packageName))
                }
            }
        }

        sectorLatLongs.forEach { latLng ->
            val markerLocation = Location("marker").apply {
                latitude = latLng.latitude
                longitude = latLng.longitude
            }
            val distance = currentLocation.distanceTo(markerLocation)
            if (distance <= proximityThreshold) {
                lastTriggerTime = currentTime
                sendBroadcast(Intent("com.cifiko.stoperica.SECTOR").setPackage(packageName))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    companion object {
        var startLapLatLng: LatLng? = null
        val sectorLatLongs = mutableListOf<LatLng>()
    }
}