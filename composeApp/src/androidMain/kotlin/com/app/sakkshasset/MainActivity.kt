package com.app.sakkshasset

import android.Manifest
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.FragmentActivity
import core.storage.initAndroidContext
import features.app.AppContextHolder
import utils.PermissionType
import utils.appContext

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        appContext = applicationContext
        // Initialize Android context for LocalStorage
        initAndroidContext(this)
        AppContextHolder.context = applicationContext
        setContent {
            App()
            RequestAllPermissions(
                onAllGranted = {
                    // All permissions granted, you can proceed with your app logic
                },
                onDenied = {
                    // Handle the case where permissions are denied (e.g., show a message or disable features
                }
            )
        }
    }
}

@Composable
fun PermissionRequester(
    permission: PermissionType,
    onResult: (Boolean) -> Unit
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onResult(granted)
    }

    LaunchedEffect(Unit) {
        val permissionString = when (permission) {
            PermissionType.CAMERA -> Manifest.permission.CAMERA
            PermissionType.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
            PermissionType.NOTIFICATION -> Manifest.permission.POST_NOTIFICATIONS
        }

        launcher.launch(permissionString)
    }
}

@Composable
fun RequestAllPermissions(
    onAllGranted: () -> Unit,
    onDenied: () -> Unit
) {
    val permissions = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.POST_NOTIFICATIONS
    )

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->

        val allGranted = result.values.all { it }

        if (allGranted) {
            onAllGranted()
        } else {
            onDenied()
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissions.toTypedArray())
    }
}

@Composable
fun AppAndroidPreview() {
    App()
}