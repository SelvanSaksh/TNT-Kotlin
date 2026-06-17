package features.app.warehouse.wms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.repository.WmsRepository
import core.network.wms.WmsPackingPickListItem
import core.network.wms.WmsPackingReceiverNode
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun PackingPickListHierarchyScreen(
    pickList: WmsPackingPickListItem,
    onBack: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val companyId = remember { WmsSession.companyId(session) }
    val repo = remember { WmsRepository() }

    var receiverOrder by remember { mutableStateOf<WmsPackingReceiverNode?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pickList.id) {
        loading = true
        error = null
        receiverOrder = null
        runCatching {
            repo.fetchPickListHierarchyOrder(pickList, companyId)
        }.onSuccess { receiverOrder = it }
            .onFailure { error = it.message }
        loading = false
    }

    Column(Modifier.fillMaxSize().background(WmsColors.PageBgAlt)) {
        WmsLightHeader(title = "Package Tree", showBack = true, onBack = onBack)

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text("Loading package tree…", color = WmsColors.TextSecondary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error ?: "Could not load package tree.", color = WmsColors.TextSecondary, fontWeight = FontWeight.SemiBold)
                }
            }

            receiverOrder != null -> {
                PackingReceiverPackageHierarchyView(receiverOrder = receiverOrder!!)
            }
        }
    }
}

@Composable
private fun PackingReceiverPackageHierarchyView(receiverOrder: WmsPackingReceiverNode) {
    // Swift default palette
    val navy = WmsColors.Navy
    val textPrimary = WmsColors.TextPrimary
    val textSecondary = WmsColors.TextSecondary
    val successGreen = Color(0xFF059669)
    val lineAccent = WmsColors.ActiveBlue
    val cartonAccent = WmsColors.Warning
    val palletAccent = WmsColors.Navy

    val pickLists = receiverOrder.pickListNodes
    val allBoxes = pickLists.flatMap { it.childBoxes }
    val completedBoxes = allBoxes.count { (it.status ?: "").equals("COMPLETED", ignoreCase = true) || (it.status ?: "")
        .equals("PACKED", ignoreCase = true) }

    Column(Modifier.fillMaxSize()) {
        // Hero card (Shipment header)
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            navy,
                            Color(0xFF1E4A7A),
                        ),
                    ),
                )
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "SHIPMENT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                    Text(
                        text = receiverOrder.orderNumber ?: receiverOrder.orderId ?: "Order",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    val customer = receiverOrder.customerName?.takeIf { it.isNotBlank() }
                    if (customer != null) {
                        Text(
                            customer,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
                if (!receiverOrder.orderStatus.isNullOrBlank()) {
                    StatusPill(
                        label = receiverOrder.orderStatus ?: "",
                        fg = Color.White,
                        bg = Color.White.copy(alpha = 0.2f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroMetric(title = "Pick Lists", value = "${pickLists.size}")
                HeroMetric(
                    title = "Boxes",
                    value = "$completedBoxes/${allBoxes.size} boxes",
                )
                receiverOrder.shippingMethod?.takeIf { it.isNotBlank() }?.let {
                    HeroMetric(title = "Ship", value = it)
                }
            }
        }

        // Legend header
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text("Hierarchy", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = textPrimary)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LegendDot(color = navy, label = "Pick List")
                LegendDot(color = lineAccent, label = "Line")
                LegendDot(color = cartonAccent, label = "Carton")
                LegendDot(color = palletAccent, label = "Pallet")
            }
        }

        Spacer(Modifier.height(14.dp))

        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            pickLists.forEach { pickListNode ->
                // We default-expand the matching pick-list and its boxes (if they contain pack-items)
                PackingPickListTreeCard(pickListNode = pickListNode)
            }
        }
    }
}

@Composable
private fun PackingPickListTreeCard(pickListNode: WmsPackingReceiverNode) {
    // Default expand like iOS: expanded pick-list + boxes with items
    var expanded by remember { mutableStateOf(true) }
    // For simplicity we expand boxes inside pick-list without extra state.

    val lines = pickListNode.childLines
    val boxes = pickListNode.childBoxes
    val packageCount = pickListNode.boxCount ?: boxes.size

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable { expanded = !expanded },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (lines.isNotEmpty() || boxes.isNotEmpty()) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = WmsColors.Navy.copy(alpha = 0.85f),
                )
            } else {
                Spacer(Modifier.size(18.dp))
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(WmsColors.Navy.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingBag,
                    contentDescription = null,
                    tint = WmsColors.Navy,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                text = "${pickListNode.displayTitle} · ${(pickListNode.totalPackedQty ?: 0)}/${(pickListNode.totalPickedQty ?: 0)} units · $packageCount pkg",
                color = WmsColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }

        if (expanded) {
            HorizontalDivider(color = WmsColors.Border.copy(alpha = 0.5f), thickness = 1.dp)
            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                if (lines.isNotEmpty()) {
                    TreeSubsectionLabel(title = "Line Items", count = lines.size)
                    lines.forEach { line ->
                        LineTreeRow(line = line, showDivider = true)
                    }
                }
                if (boxes.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    TreeSubsectionLabel(title = "Packages", count = boxes.size)
                    boxes.forEach { box ->
                        BoxTreeCard(box = box)
                    }
                }
            }
        }
    }
}

@Composable
private fun TreeSubsectionLabel(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.width(14.dp).height(1.dp).background(WmsColors.Border),)
        Text(
            text = "$title ($count)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WmsColors.TextSecondary,
        )
    }
}

@Composable
private fun LineTreeRow(line: WmsPackingReceiverNode, showDivider: Boolean) {
    val target = line.requestedQty ?: line.pickedQty ?: 0
    val packed = line.packedQty ?: 0
    val isComplete = target > 0 && packed >= target

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(WmsColors.ActiveBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Default.ShoppingBag, contentDescription = null, tint = WmsColors.ActiveBlue)
        }
        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("LINE ITEM", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextSecondary)
            Text(line.displayTitle, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextPrimary, maxLines = 1)
            val sub = line.displaySubtitle
            if (sub.isNotBlank()) {
                Text(sub, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextSecondary, maxLines = 1)
            }
        }
        QtyBadge(packed = packed, target = max(target, 0), isComplete = isComplete)
    }
    if (showDivider) {
        Spacer(Modifier.height(2.dp))
        HorizontalDivider(color = WmsColors.Border.copy(alpha = 0.6f), thickness = 1.dp)
    }
}

@Composable
private fun BoxTreeCard(box: WmsPackingReceiverNode) {
    val isPallet = (box.resolvedPackType.uppercase() == "PALLET")
    val accent = if (isPallet) WmsColors.Navy else WmsColors.Warning
    val items = box.packItems
    val isExpanded = items.isNotEmpty() // default expand for completed view
    val successGreen = WmsColors.Success

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFF8FAFC))
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // left icon
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingBag,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(
                modifier = Modifier.padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    (box.resolvedPackType).uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextSecondary,
                )
                Text(box.displayTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)
                box.sscc?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextSecondary)
                }
                box.totalPackedQty?.let { qty ->
                    Text("${qty} units", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = accent)
                }
            }
            if (!box.status.isNullOrBlank()) {
                StatusPill(label = box.status ?: "", fg = successGreen, bg = WmsColors.SuccessBg)
            }
        }

        if (isExpanded && items.isNotEmpty()) {
            Column(modifier = Modifier.padding(start = 26.dp, top = 6.dp)) {
                items.forEachIndexed { index, item ->
                    PackItemTreeRow(item, subtitleColor = successGreen)
                    if (index != items.lastIndex) {
                        HorizontalDivider(color = WmsColors.Border.copy(alpha = 0.6f), thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PackItemTreeRow(item: WmsPackingReceiverNode, subtitleColor: Color) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Tag,
                contentDescription = null,
                tint = WmsColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                item.displayTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = WmsColors.TextPrimary,
                maxLines = 2,
            )
        }
        val sub = item.displaySubtitle
        if (sub.isNotBlank()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(sub, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = subtitleColor)
            }
        }
    }
}

@Composable
private fun QtyBadge(packed: Int, target: Int, isComplete: Boolean) {
    val bg = if (isComplete) WmsColors.SuccessBg else Color(0xFFEFF6FF)
    val fg = if (isComplete) Color(0xFF065F46) else WmsColors.Navy
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$packed/$target", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = fg)
        Text("packed", fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextSecondary)
    }
}

@Composable
private fun HeroMetric(title: String, value: String) {
    val navy = WmsColors.Navy
    Column(
        modifier = Modifier
            .width(128.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(10.dp),
    ) {
        Text(
            title.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.65f),
            lineHeight = 10.sp,
        )
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(color))
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextSecondary)
    }
}

@Composable
private fun StatusPill(
    label: String,
    fg: Color,
    bg: Color,
) {
    Text(
        text = label.replace("_", " ").uppercase(),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = fg,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        maxLines = 1,
    )
}

