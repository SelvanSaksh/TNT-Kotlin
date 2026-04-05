package utils

enum class PermissionType {
    CAMERA,
    LOCATION,
    NOTIFICATION
}

interface PermissionManager {
    suspend fun requestPermission(permission: PermissionType): Boolean
    suspend fun isPermissionGranted(permission: PermissionType): Boolean
}

