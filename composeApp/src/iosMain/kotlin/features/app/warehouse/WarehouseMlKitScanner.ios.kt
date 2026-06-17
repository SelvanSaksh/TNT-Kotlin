package features.app.warehouse

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

@Composable
actual fun WarehouseMlKitScanner(
    enabled: Boolean,
    onBarcodeScanned: (String) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF163C66)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (enabled) "Use Android build for ML Kit camera scan"
            else "Scanner paused",
            color = Color.White.copy(alpha = 0.75f),
            fontSize = 14.sp,
        )
    }
}
