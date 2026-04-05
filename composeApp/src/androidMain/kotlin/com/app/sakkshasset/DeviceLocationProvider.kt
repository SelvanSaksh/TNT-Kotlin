package utils

import android.annotation.SuppressLint
import com.app.sakkshasset.MyApp
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/*
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
}*/


actual class DeviceLocationProvider actual constructor() {  // ✅ actual keyword, no constructor param

    private val context = MyApp.appContext  // ✅ Get context from global app reference

    private val client = LocationServices
        .getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    actual suspend fun getCurrentLocation(): Pair<Double, Double>? =
        suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).addOnSuccessListener { location ->
                cont.resume(
                    if (location != null) Pair(location.latitude, location.longitude)
                    else null
                )
            }.addOnFailureListener {
                cont.resume(null)
            }
        }
}