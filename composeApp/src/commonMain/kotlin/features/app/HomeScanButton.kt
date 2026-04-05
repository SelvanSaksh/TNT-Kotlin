package features.app

import androidx.compose.runtime.Composable
import navigation.AppScreen
import navigation.appscreen.Screens

@Composable
expect fun HomeScanButton(
    onNavigate: (Screens) -> Unit,
    shouldTriggerScan: Boolean,
    onScanTriggered: () -> Unit
)