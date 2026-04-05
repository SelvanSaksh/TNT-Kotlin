package com.app.sakkshasset

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import utils.PermissionType

class AndroidPermissionManager(
    private val context: Context
) {

    fun getPermissionString(permission: PermissionType): String {
        return when (permission) {
            PermissionType.CAMERA -> Manifest.permission.CAMERA
            PermissionType.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
            PermissionType.NOTIFICATION -> Manifest.permission.POST_NOTIFICATIONS
        }
    }

    fun isGranted(permission: PermissionType): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            getPermissionString(permission)
        ) == PackageManager.PERMISSION_GRANTED
    }
}