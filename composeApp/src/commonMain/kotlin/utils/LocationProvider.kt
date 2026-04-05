package utils

expect class DeviceLocationProvider {
    suspend fun getCurrentLocation(): Pair<Double, Double>?
}