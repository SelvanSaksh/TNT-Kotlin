package features.app

@androidx.compose.runtime.Composable
actual fun HomeScanButton(
    onNavigate: (navigation.AppScreen) -> Unit,
    shouldTriggerScan: Boolean,
    onScanTriggered: () -> Unit
) {
}