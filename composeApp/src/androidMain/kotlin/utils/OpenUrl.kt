package utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

lateinit var appContext: Context

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    appContext.startActivity(intent)
}

actual fun openGeneratedPdf(fileName: String, title: String, lines: List<String>) {
    try {
        val safeName = fileName.ifBlank { "Ratifye-Report.pdf" }.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(appContext.cacheDir, if (safeName.endsWith(".pdf")) safeName else "$safeName.pdf")
        file.writeBytes(buildSimplePdf(title, lines))
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.provider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
    } catch (_: Exception) {
        openUrl("https://ratifye.ai/report")
    }
}

private fun buildSimplePdf(title: String, lines: List<String>): ByteArray {
    fun esc(raw: String) = raw
        .replace("\\", "\\\\")
        .replace("(", "\\(")
        .replace(")", "\\)")
        .replace("\r", " ")
        .replace("\n", " ")
        .take(110)

    val ops = buildString {
        append("BT /F1 14 Tf 50 760 Td (${esc(title)}) Tj\n")
        append("/F1 10 Tf\n")
        lines.take(36).forEach { line ->
            append("0 -16 Td (${esc(line)}) Tj\n")
        }
        append("ET")
    }
    val contentBytes = ops.toByteArray(Charsets.ISO_8859_1)
    val objects = listOf(
        "<< /Type /Catalog /Pages 2 0 R >>",
        "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>",
        "<< /Length ${contentBytes.size} >>\nstream\n$ops\nendstream",
        "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
    )
    val pdf = StringBuilder("%PDF-1.4\n")
    val offsets = ArrayList<Int>(objects.size)
    objects.forEachIndexed { index, obj ->
        offsets += pdf.length
        pdf.append("${index + 1} 0 obj\n$obj\nendobj\n")
    }
    val xrefAt = pdf.length
    pdf.append("xref\n0 ${objects.size + 1}\n")
    pdf.append("0000000000 65535 f \n")
    offsets.forEach { offset ->
        pdf.append(offset.toString().padStart(10, '0'))
        pdf.append(" 00000 n \n")
    }
    pdf.append("trailer<< /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xrefAt\n%%EOF")
    return pdf.toString().toByteArray(Charsets.ISO_8859_1)
}