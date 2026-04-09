package utils

import android.content.Intent
import android.net.Uri
import android.content.Context

lateinit var appContext: Context

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    appContext.startActivity(intent)
}