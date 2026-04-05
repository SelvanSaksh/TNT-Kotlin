package features.app

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.net.URL

// 🔹 SHARE IMAGE
actual fun shareImage(url: String) {
    try {
        val context = AppContextHolder.context

        println("🔥 SHARE URL: $url")

        val inputStream = URL(url).openStream()
        val file = File(context.cacheDir, "barcode.png")

        val outputStream = FileOutputStream(file)
        inputStream.copyTo(outputStream)

        outputStream.close()
        inputStream.close()

        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(
            Intent.createChooser(intent, "Share Barcode")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

    } catch (e: Exception) {
        e.printStackTrace()
    }
}

// 🔹 DOWNLOAD IMAGE
actual fun downloadImage(url: String) {
    try {
        val context = AppContextHolder.context

        println("🔥 DOWNLOAD URL: $url")

        val inputStream = URL(url).openStream()

        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "barcode_${System.currentTimeMillis()}.png"
        )

        val outputStream = FileOutputStream(file)
        inputStream.copyTo(outputStream)

        outputStream.close()
        inputStream.close()

        println("✅ Saved: ${file.absolutePath}")

    } catch (e: Exception) {
        e.printStackTrace()
    }
}