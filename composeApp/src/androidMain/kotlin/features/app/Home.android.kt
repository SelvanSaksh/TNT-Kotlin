package features.app

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import features.app.scans.ScannerActivity
import navigation.AppScreen

@Composable
actual fun HomeScanButton(
    onNavigate: (AppScreen) -> Unit,
    shouldTriggerScan: Boolean,
    onScanTriggered: () -> Unit
) {
    val context = LocalContext.current
    
    // Scanner launcher - but we'll navigate to Scans screen instead
    LaunchedEffect(shouldTriggerScan) {
        if (shouldTriggerScan) {
            // Navigate to Scans screen which will handle the camera
            onNavigate(AppScreen.Scans)
            onScanTriggered()
        }
    }
}
