package features.app.warehouse.wms

import androidx.compose.runtime.Composable

@Composable
actual fun rememberInvoiceDocumentPicker(onResult: (PickedInvoiceDocument?) -> Unit): () -> Unit {
    return { onResult(null) }
}
