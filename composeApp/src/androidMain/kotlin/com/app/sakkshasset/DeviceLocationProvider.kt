package com.app.sakkshasset

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class DeviceLocationProvider(
    private val context: Context
) {

    private val client = LocationServices
        .getFusedLocationProviderClient(context)


    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->


            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).addOnSuccessListener { location ->

                if (location != null) {
                    cont.resume(
                        Pair(location.latitude, location.longitude)
                    )
                } else {
                    cont.resume(null)
                }

            }.addOnFailureListener {
                cont.resume(null)
            }
        }
}