package core.network

import androidx.compose.runtime.Composable

@Composable
actual fun BindNetworkMonitor(monitor: NetworkMonitor) {
    monitor.isConnected = true
}

actual fun refreshNetworkStatus(monitor: NetworkMonitor) {
    monitor.isConnected = true
}
