package features.app

import navigation.appscreen.Screens

@androidx.compose.runtime.Composable
actual fun HomeScanButton(
    onNavigate: (Screens) -> Unit,
    shouldTriggerScan: Boolean,
    onScanTriggered: () -> Unit
) {
}