package features.app.warehouse.wms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Tag
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
import kotlin.math.min

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
        WmsLightHeader(title = "Pick List", showBack = true, onBack = onBack)

        when {
            loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Loading packages…",
                            color = WmsColors.TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        )
                    }
                }
            }

            error != null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        error ?: "Could not load packages.",
                        color = WmsColors.TextSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            }

            receiverOrder != null -> {
                PackingPickListPackagesView(
                    pickList = pickList,
                    order = receiverOrder!!,
                )
            }
        }
    }
}

@Composable
private fun PackingPickListPackagesView(
    pickList: WmsPackingPickListItem,
    order: WmsPackingReceiverNode,
) {
    var collapsedPackageIds by remember { mutableStateOf(setOf<String>()) }

    val listNode = remember(pickList, order) { resolveListNode(pickList, order) }
    val pallets = remember(listNode) { listNode?.childBoxes?.filter { it.isPalletBox }.orEmpty() }
    val standaloneCartons = remember(listNode) { listNode?.childBoxes?.filter { !it.isPalletBox }.orEmpty() }
    val hasPallet = pallets.isNotEmpty()
    val viewModeTitle = if (hasPallet) "Pallet View" else "Carton View"
    val viewModeSubtitle = if (hasPallet) {
        "${pallets.size} pallet${if (pallets.size == 1) "" else "s"} with linked cartons"
    } else {
        "${standaloneCartons.size} sealed carton${if (standaloneCartons.size == 1) "" else "s"} on this list"
    }

    fun isNodeExpanded(id: String) = !collapsedPackageIds.contains(id)
    fun toggleExpanded(id: String) {
        collapsedPackageIds = if (collapsedPackageIds.contains(id)) {
            collapsedPackageIds - id
        } else {
            collapsedPackageIds + id
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 14.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PickListHeroCard(
            pickList = pickList,
            listNode = listNode,
            palletCount = pallets.size,
            standaloneCartonCount = standaloneCartons.size,
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, WmsColors.Border, RoundedCornerShape(14.dp)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(viewModeTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)
                Text(viewModeSubtitle, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextSecondary)
            }

            when {
                hasPallet -> {
                    pallets.forEach { pallet ->
                        PalletSection(
                            pallet = pallet,
                            isExpanded = isNodeExpanded(pallet.id),
                            onToggle = { toggleExpanded(pallet.id) },
                            isNodeExpanded = ::isNodeExpanded,
                            onToggleNode = ::toggleExpanded,
                        )
                    }
                }

                standaloneCartons.isNotEmpty() -> {
                    standaloneCartons.forEach { carton ->
                        CartonSection(
                            carton = carton,
                            depth = 0,
                            isExpanded = isNodeExpanded(carton.id),
                            onToggle = { toggleExpanded(carton.id) },
                        )
                    }
                }

                else -> {
                    EmptyPackagesCard(modifier = Modifier.padding(14.dp))
                }
            }
        }
    }
}

private fun resolveListNode(
    pickList: WmsPackingPickListItem,
    order: WmsPackingReceiverNode,
): WmsPackingReceiverNode? {
    val targetId = pickList.pickListId?.trim().orEmpty()
    if (targetId.isNotEmpty()) {
        order.pickListNodes.firstOrNull { (it.pickListId ?: "") == targetId }?.let { return it }
    }
    return order.pickListNodes.firstOrNull()
}

@Composable
private fun PickListHeroCard(
    pickList: WmsPackingPickListItem,
    listNode: WmsPackingReceiverNode?,
    palletCount: Int,
    standaloneCartonCount: Int,
) {
    val navy = WmsColors.Navy
    val packed = listNode?.totalPackedQty ?: pickList.resolvedTotalPackedQty
    val picked = listNode?.totalPickedQty ?: pickList.resolvedTotalRequestedQty
    val progress = if (picked > 0) min(packed.toFloat() / picked.toFloat(), 1f) else 0f
    val packageCount = listNode?.boxCount ?: (palletCount + standaloneCartonCount)
    val lineCount = listNode?.lineCount ?: pickList.packLines.size
    val status = listNode?.status?.takeIf { it.isNotBlank() } ?: pickList.cardStatusLabel

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(navy, Color(0xFF1E4A7A)),
                ),
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "PICK LIST",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Text(
                    listNode?.displayTitle ?: pickList.displayTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                pickList.orderNumber?.takeIf { it.isNotBlank() }?.let { orderNo ->
                    Text(
                        "Order $orderNo",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
            }
            if (status.isNotBlank()) {
                Text(
                    status.replace("_", " "),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            HeroStat(title = "Packed", value = "$packed/$picked", modifier = Modifier.weight(1f))
            HeroStat(title = "Packages", value = "$packageCount", modifier = Modifier.weight(1f))
            HeroStat(title = "Lines", value = "$lineCount", modifier = Modifier.weight(1f))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.25f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun HeroStat(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            title.uppercase(),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.65f),
        )
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun PalletSection(
    pallet: WmsPackingReceiverNode,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    isNodeExpanded: (String) -> Boolean,
    onToggleNode: (String) -> Unit,
) {
    val cartons = pallet.childBoxes.filter { it.isCartonBox }
    val units = pallet.totalPackedQty ?: cartons.sumOf { it.totalPackedQty ?: 0 }

    Column {
        PackageTreeRow(
            depth = 0,
            accent = WmsColors.Navy,
            icon = Icons.Default.LocalShipping,
            kind = "Pallet",
            title = pallet.displayTitle,
            details = palletDetailLines(pallet, cartons, units),
            status = pallet.status,
            qtyLabel = "$units units",
            isExpanded = isExpanded,
            hasChildren = cartons.isNotEmpty(),
            onToggle = onToggle,
        )

        if (isExpanded) {
            cartons.forEach { carton ->
                CartonSection(
                    carton = carton,
                    depth = 1,
                    isExpanded = isNodeExpanded(carton.id),
                    onToggle = { onToggleNode(carton.id) },
                )
            }
        }

        SectionDivider()
    }
}

@Composable
private fun CartonSection(
    carton: WmsPackingReceiverNode,
    depth: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val products = carton.packItems
    val units = carton.totalPackedQty ?: 0

    Column {
        PackageTreeRow(
            depth = depth,
            accent = WmsColors.Warning,
            icon = Icons.Default.ShoppingBag,
            kind = "Carton",
            title = carton.displayTitle,
            details = cartonDetailLines(carton, products, units),
            status = carton.status,
            qtyLabel = "$units units",
            isExpanded = isExpanded,
            hasChildren = true,
            onToggle = onToggle,
        )

        if (isExpanded) {
            if (products.isEmpty()) {
                EmptyProductsHint(depth = depth + 1)
            } else {
                products.forEach { product ->
                    ProductRow(product = product, depth = depth + 1)
                }
            }
        }

        if (depth == 0) {
            SectionDivider()
        }
    }
}

private fun palletDetailLines(
    pallet: WmsPackingReceiverNode,
    cartons: List<WmsPackingReceiverNode>,
    units: Int,
): List<String> {
    val lines = mutableListOf<String>()
    trimmed(pallet.sscc)?.let { lines.add("SSCC: $it") }
    lines.add("${cartons.size} carton${if (cartons.size == 1) "" else "s"} · $units units total")
    return lines
}

private fun cartonDetailLines(
    carton: WmsPackingReceiverNode,
    products: List<WmsPackingReceiverNode>,
    units: Int,
): List<String> {
    val lines = mutableListOf<String>()
    trimmed(carton.sscc)?.let { lines.add("SSCC: $it") }
    lines.add("${products.size} product${if (products.size == 1) "" else "s"} · $units units")
    return lines
}

private fun trimmed(value: String?): String? =
    value?.trim()?.takeIf { it.isNotEmpty() }

@Composable
private fun PackageTreeRow(
    depth: Int,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    kind: String,
    title: String,
    details: List<String>,
    status: String?,
    qtyLabel: String?,
    isExpanded: Boolean,
    hasChildren: Boolean,
    onToggle: () -> Unit,
) {
    val successGreen = WmsColors.Success

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TreeIndent(depth = depth)

        if (hasChildren) {
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = null,
                tint = WmsColors.TextSecondary,
                modifier = Modifier
                    .width(14.dp)
                    .padding(top = 3.dp),
            )
        } else {
            Box(
                modifier = Modifier
                    .width(14.dp)
                    .padding(top = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.5f)),
                )
            }
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier
                .width(18.dp)
                .padding(start = 10.dp, top = 1.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(kind.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextSecondary)
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextPrimary)
            details.forEach { line ->
                Text(line, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextSecondary)
            }
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            trimmed(status)?.let { statusLabel ->
                Text(
                    statusLabel.replace("_", " ").uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = successGreen,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(successGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
            qtyLabel?.let { label ->
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = accent)
            }
        }
    }
}

@Composable
private fun TreeIndent(depth: Int) {
    if (depth <= 0) return
    Row {
        repeat(depth) {
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(WmsColors.Border),
                )
            }
        }
    }
}

@Composable
private fun ProductRow(product: WmsPackingReceiverNode, depth: Int) {
    val lineAccent = WmsColors.ActiveBlue

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF9FAFB))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TreeIndent(depth = depth)

        Icon(
            imageVector = Icons.Default.Tag,
            contentDescription = null,
            tint = lineAccent,
            modifier = Modifier
                .width(14.dp)
                .padding(top = 2.dp),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                product.displayTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = WmsColors.TextPrimary,
            )
            trimmed(product.productSku)?.let { sku ->
                Text("SKU: $sku", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextSecondary)
            }
            trimmed(product.batch)?.let { batch ->
                Text("Batch: $batch", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextSecondary)
            }
            if (product.displaySubtitle.isNotBlank()) {
                Text(
                    product.displaySubtitle,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = WmsColors.TextSecondary,
                )
            }
        }

        Text(
            "${product.quantity ?: 0} units",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = lineAccent,
        )
    }
}

@Composable
private fun EmptyProductsHint(depth: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TreeIndent(depth = depth)
        Text(
            "No products in this carton.",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = WmsColors.TextSecondary,
        )
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 14.dp),
        color = WmsColors.Border,
        thickness = 1.dp,
    )
}

@Composable
private fun EmptyPackagesCard(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF8FAFC))
            .border(1.dp, WmsColors.Border, RoundedCornerShape(14.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Default.LocalShipping,
            contentDescription = null,
            tint = WmsColors.TextSecondary,
            modifier = Modifier.size(32.dp),
        )
        Text("No packages yet", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)
        Text(
            "Cartons or pallets will appear here once packing starts.",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = WmsColors.TextSecondary,
        )
    }
}
