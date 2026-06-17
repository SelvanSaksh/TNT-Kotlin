package core.location

import core.network.repository.AppRepository
import core.storage.SessionManager
import kotlinx.coroutines.delay
import utils.DeviceLocationProvider

/**
 * In-memory device location + reverse-geocoded label (OpenStreetMap / Nominatim).
 * Populated at app launch and refreshed when location permission is granted.
 */
object AppLocationCache {
    var latitude: Double = 0.0
        private set
    var longitude: Double = 0.0
        private set
    var geoLocation: String = "Unknown"
        private set
    var isReady: Boolean = false
        private set

    fun restoreFrom(sessionManager: SessionManager) {
        val lat = sessionManager.getCachedLatitude()
        val lon = sessionManager.getCachedLongitude()
        if (lat != null && lon != null) {
            latitude = lat
            longitude = lon
            geoLocation = sessionManager.getCachedGeoLabel().orEmpty().ifBlank { "Unknown" }
            isReady = lat != 0.0 || lon != 0.0
        }
    }

    fun persistTo(sessionManager: SessionManager) {
        sessionManager.saveCachedDeviceLocation(latitude, longitude, geoLocation)
    }

    suspend fun refresh(locationProvider: DeviceLocationProvider = DeviceLocationProvider()) {
        repeat(3) { attempt ->
            val pair = locationProvider.getCurrentLocation()
            if (pair != null) {
                latitude = pair.first
                longitude = pair.second
                AppRepository.getLocationDetails(latitude, longitude)
                    .onSuccess { loc ->
                        val full = loc.displayName.trim().take(512)
                        geoLocation = full.ifBlank {
                            listOfNotNull(loc.city, loc.state, loc.country)
                                .joinToString(", ")
                                .trim()
                                .ifBlank { "Unknown" }
                        }
                    }
                    .onFailure {
                        geoLocation = "Unknown"
                    }
                isReady = true
                return
            }
            if (attempt < 2) delay(1000)
        }
    }

    /** Uses cache when coordinates are already known; otherwise fetches GPS once. */
    suspend fun ensureFresh(locationProvider: DeviceLocationProvider = DeviceLocationProvider()) {
        if (isReady && (latitude != 0.0 || longitude != 0.0)) return
        refresh(locationProvider)
    }

    fun coordinates(): Pair<Double, Double> = latitude to longitude
}
