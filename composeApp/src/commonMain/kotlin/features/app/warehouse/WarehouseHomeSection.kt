package features.app.warehouse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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

private val NavyDark = Color(0xFF163C66)
private val TextPrimary = Color(0xFF111827)
private val TextMuted = Color(0xFF9CA3AF)
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
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "Warehouse Features",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    Text(
                        "Pick, pack, and receive workflows",
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }
                Text(
                    "${modules.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NavyDark,
                    modifier = Modifier
                        .background(Color(0xFFE8EEF5), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }

            val rows = modules.chunked(2)
            rows.forEach { rowModules ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowModules.forEach { module ->
                        WarehouseModuleCard(
                            module = module,
                            modifier = Modifier.weight(1f),
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
                    if (rowModules.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WarehouseModuleCard(
    module: WarehouseModuleItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(146.dp)
            .background(Color(0xFFF9FAFB), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = Color(0xFFD1D5DB),
                modifier = Modifier.size(18.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                maxLines = 2,
            )
        }

        Text(
            "Available",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = module.accent,
            modifier = Modifier
                .background(module.accent.copy(alpha = 0.10f), CircleShape)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
