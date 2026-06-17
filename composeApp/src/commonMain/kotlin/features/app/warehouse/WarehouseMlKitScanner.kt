package features.app.warehouse

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Camera barcode preview. On Android uses ML Kit + CameraX; on other targets shows a placeholder.
 */
@Composable
expect fun WarehouseMlKitScanner(
    enabled: Boolean,
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
)
