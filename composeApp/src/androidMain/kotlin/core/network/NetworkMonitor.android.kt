package core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import features.app.AppContextHolder

@Composable
actual fun BindNetworkMonitor(monitor: NetworkMonitor) {
    val context = LocalContext.current.applicationContext
    DisposableEffect(context) {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                monitor.isConnected = true
            }

            override fun onLost(network: Network) {
                monitor.isConnected = connectivityManager.hasInternetConnection()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                monitor.isConnected =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, callback)
        monitor.isConnected = connectivityManager.hasInternetConnection()
        onDispose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
}

actual fun refreshNetworkStatus(monitor: NetworkMonitor) {
    val context = AppContextHolder.context
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    monitor.isConnected = connectivityManager.hasInternetConnection()
}

private fun ConnectivityManager.hasInternetConnection(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val network = activeNetwork ?: return false
        val capabilities = getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    @Suppress("DEPRECATION")
    return activeNetworkInfo?.isConnected == true
}
