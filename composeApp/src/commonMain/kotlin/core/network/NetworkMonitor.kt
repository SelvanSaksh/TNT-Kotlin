package core.network

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class NetworkMonitor {
    var isConnected by mutableStateOf(true)
        internal set

    fun refresh() {
        refreshNetworkStatus(this)
    }
}

@Composable
fun rememberNetworkMonitor(): NetworkMonitor {
    val monitor = remember { NetworkMonitor() }
    BindNetworkMonitor(monitor)
    return monitor
}

@Composable
expect fun BindNetworkMonitor(monitor: NetworkMonitor)

expect fun refreshNetworkStatus(monitor: NetworkMonitor)
