package com.example.spacer.location

import android.content.Context
import android.location.Location
import android.location.LocationManager

data class LatLng(val latitude: Double, val longitude: Double)

/**
 * Last known fix (GPS or network). Matches HomeScreen resolution strategy.
 */
fun getLastKnownLatLng(context: Context): LatLng? {
    return try {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val best = pickBestLocation(gps, network) ?: return null
        LatLng(best.latitude, best.longitude)
    } catch (_: SecurityException) {
        null
    } catch (_: Exception) {
        null
    }
}

private fun pickBestLocation(first: Location?, second: Location?): Location? {
    if (first == null) return second
    if (second == null) return first
    return if (first.accuracy <= second.accuracy) first else second
}
