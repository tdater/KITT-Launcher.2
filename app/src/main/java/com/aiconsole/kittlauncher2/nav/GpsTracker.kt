package com.aiconsole.kittlauncher2.nav

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A GPS fix reduced to what the dash shows. */
data class GpsFix(val speedKmh: Double?, val bearing: Float?, val timestamp: Long, val lat: Double = 0.0, val lon: Double = 0.0)

/**
 * GPS speed and heading while the launcher is showing: the speedometer's fallback when OBD
 * isn't connected, and the compass in the status strip. Needs location permission (the map
 * asks for it); without it [fix] stays null.
 */
class GpsTracker(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)
    private val _fix = MutableStateFlow<GpsFix?>(null)
    val fix: StateFlow<GpsFix?> = _fix.asStateFlow()
    private var running = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _fix.value = GpsFix(
                speedKmh = if (location.hasSpeed()) location.speed * 3.6 else null,
                bearing = if (location.hasBearing() && location.speed > 1.5f) location.bearing else _fix.value?.bearing,
                timestamp = System.currentTimeMillis(),
                lat = location.latitude,
                lon = location.longitude
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) {
            _fix.value = null
        }
    }

    fun gpsEnabled(): Boolean = runCatching { manager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true }.getOrDefault(false)

    fun start() {
        if (running || manager == null) return
        val allowed = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!allowed || !manager.allProviders.contains(LocationManager.GPS_PROVIDER)) return
        try {
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000L, 0f, listener, Looper.getMainLooper())
            running = true
        } catch (_: SecurityException) {
        }
    }

    fun stop() {
        if (!running) return
        manager?.removeUpdates(listener)
        running = false
    }

    /** Best known position (current fix, else the system's last known), for weather. */
    fun position(): Pair<Double, Double>? {
        _fix.value?.let { return it.lat to it.lon }
        val allowed = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!allowed || manager == null) return null
        return try {
            manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }?.let { it.latitude to it.longitude }
        } catch (_: SecurityException) {
            null
        }
    }

    /** Speed from a fix no older than a few seconds. */
    fun freshSpeedKmh(now: Long = System.currentTimeMillis()): Double? =
        _fix.value?.takeIf { now - it.timestamp < 4_000 }?.speedKmh
}
