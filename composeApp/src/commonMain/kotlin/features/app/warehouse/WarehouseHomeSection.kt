package features.app.warehouse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TextPrimary = Color(0xFF111827)
private val TextSub = Color(0xFF6B7280)

val defaultWarehouseModules: List<WarehouseModuleItem> = listOf(
    WarehouseModuleItem(
        id = 10_001,
        name = "Picking",
        subtitle = "Pick items for dispatch",
        icon = Icons.Default.Checklist,
        accent = Color(0xFF059669),
    ),
    WarehouseModuleItem(
        id = 10_002,
        name = "Packing",
        subtitle = "Pack and verify orders",
        icon = Icons.Default.Inventory2,
        accent = Color(0xFF2563EB),
    ),
    WarehouseModuleItem(
        id = 10_003,
        name = "Receiving",
        subtitle = "Receive incoming stock",
        icon = Icons.Default.MoveToInbox,
        accent = Color(0xFFD97706),
    ),
)

@Composable
fun WarehouseHomeSection(
    modules: List<WarehouseModuleItem> = defaultWarehouseModules,
    onModuleClick: (WarehouseRoute) -> Unit,
) {
    if (modules.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Warehouse Operations",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )

            modules.forEach { module ->
                WarehouseModuleListItem(
                    module = module,
                    onClick = {
                        val route = when {
                            module.name.contains("Picking", ignoreCase = true) -> WarehouseRoute.Picking
                            module.name.contains("Packing", ignoreCase = true) -> WarehouseRoute.Packing
                            else -> WarehouseRoute.Receiving
                        }
                        onModuleClick(route)
                    },
                )
            }
        }
    }
}

@Composable
private fun WarehouseModuleListItem(
    module: WarehouseModuleItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF9FAFB), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(module.accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                module.icon,
                contentDescription = null,
                tint = module.accent,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                module.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                maxLines = 1,
            )
            Text(
                module.subtitle,
                fontSize = 11.sp,
                color = TextSub,
                maxLines = 1,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFD1D5DB),
            modifier = Modifier.size(20.dp),
        )
    }
}
