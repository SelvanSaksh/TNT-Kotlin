package features.app.warehouse.wms

import androidx.compose.runtime.Composable

data class PickedInvoiceDocument(
    val name: String,
    val bytes: ByteArray,
    val extension: String,
) {
    val sizeLabel: String
        get() = when {
            bytes.size >= 1_048_576 -> "${bytes.size / 1_048_576} MB"
            bytes.size >= 1024 -> "${bytes.size / 1024} KB"
            else -> "${bytes.size} B"
        }
}

@Composable
expect fun rememberInvoiceDocumentPicker(onResult: (PickedInvoiceDocument?) -> Unit): () -> Unit
