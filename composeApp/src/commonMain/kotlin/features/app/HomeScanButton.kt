package features.app

import androidx.compose.runtime.Composable
import navigation.AppScreen

@Composable
expect fun HomeScanButton(
    onNavigate: (AppScreen) -> Unit,
    shouldTriggerScan: Boolean,
    onScanTriggered: () -> Unit
)