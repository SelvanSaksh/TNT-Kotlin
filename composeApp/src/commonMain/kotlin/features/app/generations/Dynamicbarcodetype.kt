package features.app.generations

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class DynamicBarcodeType(
    val displayName: String,
    val apiType: String,
    val description: String,
    val placeholder: String,
    val icon: ImageVector,
    val maxLength: Int? = null,
    val numericOnly: Boolean = false,
) {
    QR_CODE(
        displayName = "QR Code",
        apiType     = "qrcode",
        description = "2D barcode for URLs, text, and contact information",
        placeholder = "Enter text or URL…",
        icon        = Icons.Default.QrCode,
    ),
    DATA_MATRIX(
        displayName = "Data Matrix",
        apiType     = "datamatrix",
        description = "Compact 2D barcode for small item marking",
        placeholder = "Enter text or URL…",
        icon        = Icons.Default.GridOn,
    ),
    PDF417(
        displayName = "PDF417",
        apiType     = "pdf417",
        description = "Stacked linear barcode for large data capacity",
        placeholder = "Enter text or URL…",
        icon        = Icons.Default.ViewColumn,
    ),
    AZTEC(
        displayName = "Aztec Code",
        apiType     = "aztec",
        description = "Compact 2D barcode used in transport tickets and mobile boarding passes",
        placeholder = "Enter text or URL…",
        icon        = Icons.Default.Code,
    ),
    CODE_128(
        displayName = "Code 128",
        apiType     = "code128",
        description = "High-density linear barcode for alphanumeric data",
        placeholder = "Enter text or URL…",
        icon        = Icons.Default.BarChart,
    ),
    EAN_13(
        displayName = "EAN-13",
        apiType     = "ean13",
        description = "13-digit retail barcode standard",
        placeholder = "Enter 13 digits…",
        icon        = Icons.Default.BarChart,
        maxLength   = 13,
        numericOnly = true,
    ),
    EAN_8(
        displayName = "EAN-8",
        apiType     = "ean8",
        description = "Compact 8-digit retail barcode",
        placeholder = "Enter 8 digits…",
        icon        = Icons.Default.BarChart,
        maxLength   = 8,
        numericOnly = true,
    ),
    UPC_A(
        displayName = "UPC-A",
        apiType     = "upca",
        description = "12-digit North American retail barcode",
        placeholder = "Enter 12 digits…",
        icon        = Icons.Default.BarChart,
        maxLength   = 12,
        numericOnly = true,
    ),
    DATA_BAR(
        displayName = "DataBar",
        apiType     = "databar",
        description = "Reduced space symbology for small items",
        placeholder = "Enter data…",
        icon        = Icons.Default.BarChart,
    ),
}

fun DynamicBarcodeType.validate(input: String): Boolean = when {
    maxLength != null -> input.length == maxLength && (!numericOnly || input.all { it.isDigit() })
    else              -> input.isNotBlank()
}