package features.app.warehouse.tracktrace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.epcis.EpcisFlowService
import core.network.models.L3ShipmentProduct
import core.network.repository.AppRepository
import core.network.repository.WmsRepository
import features.app.warehouse.wms.WmsSession
import core.storage.SessionManager
import core.storage.getLocalStorage
import features.app.warehouse.wms.WmsColors
import kotlinx.coroutines.launch
import utils.Gs1Parser

private val TtBg = Color(0xFFF5F6FA)
private val TtCard = Color.White
private val TtNavy = Color(0xFF163C66)
private val TtMuted = Color(0xFF6B7280)
private val TtBorder = Color(0xFFE5E7EB)
private val TtGreen = Color(0xFF059669)
private val TtRed = Color(0xFFDC2626)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackTraceHomeScreen(
    onBack: () -> Unit,
    onNavigateToVerify: () -> Unit,
    onNavigateToPack: () -> Unit,
    onNavigateToReceive: () -> Unit,
    onNavigateToPick: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Warehouse", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TtCard),
            )
        },
        containerColor = TtBg,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                TrackTraceMenuItem(
                    icon = Icons.Default.Verified,
                    label = "Verify",
                    subtitle = "Scan and verify product authenticity",
                    color = TtGreen,
                    onClick = onNavigateToVerify,
                )
            }
            item {
                TrackTraceMenuItem(
                    icon = Icons.Default.RemoveShoppingCart,
                    label = "Pick",
                    subtitle = "Pick items from warehouse locations",
                    color = TtNavy,
                    onClick = onNavigateToPick,
                )
            }
            item {
                TrackTraceMenuItem(
                    icon = Icons.Default.Inventory2,
                    label = "Pack",
                    subtitle = "Pack items into boxes and pallets",
                    color = Color(0xFF7C3AED),
                    onClick = onNavigateToPack,
                )
            }
            item {
                TrackTraceMenuItem(
                    icon = Icons.Default.LocalShipping,
                    label = "Receive",
                    subtitle = "Receive incoming shipments",
                    color = Color(0xFFD97706),
                    onClick = onNavigateToReceive,
                )
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun TrackTraceMenuItem(
    icon: ImageVector,
    label: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TtCard)
            .border(1.dp, TtBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Color(0xFF111827))
            Text(subtitle, fontSize = 13.sp, color = TtMuted)
        }
    }
}

// ── Verify Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackTraceVerifyScreen(
    onBack: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val scope = rememberCoroutineScope()
    var scanInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var resultStatus by remember { mutableStateOf("") }
    var resultSerial by remember { mutableStateOf("") }
    var resultBatch by remember { mutableStateOf("") }
    var resultGtin by remember { mutableStateOf("") }
    var resultExpiry by remember { mutableStateOf("") }
    var resultTimeline by remember { mutableStateOf<List<core.network.models.BarcodeTraceEvent>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TtCard),
            )
        },
        containerColor = TtBg,
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = scanInput,
                    onValueChange = { scanInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Scan or enter barcode") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                )
                Button(
                    onClick = {
                        if (scanInput.isBlank()) return@Button
                        scope.launch {
                            isLoading = true
                            error = null
                            runCatching {
                                val parsed = Gs1Parser.parse(scanInput.trim())
                                val lookup = if (!parsed.gtin.isNullOrBlank()) {
                                    core.network.models.BarcodeLookupQuery(
                                        gtin = parsed.gtin.orEmpty(),
                                        serial = parsed.serial.orEmpty(),
                                        batch = parsed.batch.orEmpty(),
                                    )
                                } else {
                                    core.network.models.BarcodeLookupQuery(legacyKey = scanInput.trim())
                                }
                                val response = AppRepository.lookupBarcodeLogs(lookup)
                                response.fold(
                                    onSuccess = { data ->
                                        val firstEvent = data.data?.firstOrNull()
                                        resultGtin = firstEvent?.epc_id.orEmpty()
                                        resultSerial = firstEvent?.serial.orEmpty()
                                        resultBatch = firstEvent?.batch.orEmpty()
                                        resultStatus = firstEvent?.event_type ?: "Verified"
                                        resultExpiry = ""
                                        resultTimeline = data.data.orEmpty()
                                    },
                                    onFailure = { error = it.message ?: "Lookup failed" },
                                )
                            }.onFailure { error = it.message ?: "Request failed" }
                            isLoading = false
                        }
                    },
                    enabled = scanInput.isNotBlank() && !isLoading,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TtGreen),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Verify", fontWeight = FontWeight.Bold)
                    }
                }
            }

            error?.let {
                Text(it, color = TtRed, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
            }

            if (resultStatus.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TtCard).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Result", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TtNavy)
                    HorizontalDivider(color = TtBorder)
                    ResultRow("Status", resultStatus)
                    if (resultGtin.isNotEmpty()) ResultRow("GTIN", resultGtin)
                    if (resultSerial.isNotEmpty()) ResultRow("Serial", resultSerial)
                    if (resultBatch.isNotEmpty()) ResultRow("Batch", resultBatch)
                    if (resultExpiry.isNotEmpty()) ResultRow("Expiry", resultExpiry)
                }
            }

            if (resultTimeline.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TtCard).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Timeline (${resultTimeline.size} events)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TtNavy)
                    HorizontalDivider(color = TtBorder)
                    resultTimeline.forEach { event ->
                        Text(
                            "${event.event_type ?: "—"} · ${event.event_time ?: "—"}",
                            fontSize = 12.sp,
                            color = TtMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontSize = 13.sp, color = TtMuted)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF111827), textAlign = TextAlign.End)
    }
}

// ── Pack Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackTracePackScreen(
    onBack: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }
    val packerId = remember { WmsSession.userId(session) }

    var lists by remember { mutableStateOf<List<core.network.wms.WmsPackingPickListItem>>(emptyList()) }
    var activeList by remember { mutableStateOf<core.network.wms.WmsPackingPickListItem?>(null) }
    var activePackId by remember { mutableStateOf("") }
    var activeSscc by remember { mutableStateOf("") }
    var scanInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        runCatching {
            lists = repo.fetchPackingPickListsByPacker(packerId, companyId).lists
        }.onFailure { error = it.message }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pack", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    if (activePackId.isNotEmpty()) {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching {
                                    repo.sealIndustryPackingBox(activePackId, packerId)
                                    message = "Box sealed."
                                    activePackId = ""
                                    activeSscc = ""
                                }.onFailure { error = it.message }
                            }
                        }) { Text("Seal", color = TtGreen, fontWeight = FontWeight.Bold) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TtCard),
            )
        },
        containerColor = TtBg,
    ) { padding ->
        if (activeList == null) {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(lists) { list ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TtCard)
                            .border(1.dp, TtBorder, RoundedCornerShape(12.dp)).clickable { activeList = list }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(list.pickListNumber ?: list.id, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("${list.totalPackedQty ?: 0} packed", fontSize = 12.sp, color = TtMuted)
                        }
                        Text("→", fontSize = 18.sp, color = TtNavy)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (activePackId.isEmpty()) {
                    Button(
                        onClick = {
                            scope.launch {
                                runCatching {
                                    val response = repo.createPackingBox(
                                        core.network.wms.WmsCreatePackingBoxRequest(
                                            companyId = companyId,
                                            packingOrderId = activeList?.packingOrderId.orEmpty(),
                                            packLabel = "PACK-${System.currentTimeMillis()}",
                                            packType = "CARTON",
                                            hierarchyLevel = 3,
                                            createdBy = packerId,
                                        )
                                    )
                                    activePackId = response.packId.orEmpty()
                                    activeSscc = response.sscc.orEmpty()
                                    message = "Box created."
                                }.onFailure { error = it.message }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TtNavy),
                    ) {
                        Text("New Box", fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text("SSCC: $activeSscc", fontSize = 12.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, color = TtMuted)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = scanInput,
                            onValueChange = { scanInput = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Scan item barcode") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                        )
                        Button(
                            onClick = {
                                if (scanInput.isBlank()) return@Button
                                scope.launch {
                                    val gs1 = Gs1Parser.parse(scanInput.trim())
                                    runCatching {
                                        repo.addItemToIndustryPackingBox(
                                            boxId = activePackId,
                                            taskId = "",
                                            quantity = 1,
                                            packerId = packerId,
                                            verificationType = "scan",
                                        )
                                        message = "Item added."
                                        scanInput = ""
                                    }.onFailure { error = it.message }
                                }
                            },
                            enabled = scanInput.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text("Add")
                        }
                    }
                }

                message?.let {
                    Text(it, color = TtGreen, fontSize = 13.sp)
                }
                error?.let {
                    Text(it, color = TtRed, fontSize = 13.sp)
                }
            }
        }
    }
}

// ── Receive Screen ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackTraceReceiveScreen(
    onBack: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }
    val receiverId = remember { WmsSession.userId(session) }

    var shippedLists by remember { mutableStateOf<List<core.network.wms.WmsShippedPicklist>>(emptyList()) }
    var selectedPicklist by remember { mutableStateOf<core.network.wms.WmsShippedPicklist?>(null) }
    var scanInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        runCatching {
            shippedLists = repo.fetchShippedPicklists(receivingCompanyId = companyId).items
        }.onFailure { error = it.message }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Receive", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    if (selectedPicklist != null) {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching {
                                    val pickListId = selectedPicklist?.id ?: 0
                                    repo.markDispatchDelivered(
                                        core.network.wms.WmsDeliverDispatchRequest(
                                            pickingListId = pickListId,
                                            receivedBy = receiverId,
                                        )
                                    )
                                    message = "Delivery confirmed."
                                    selectedPicklist = null
                                    shippedLists = repo.fetchShippedPicklists(receivingCompanyId = companyId).items
                                }.onFailure { error = it.message }
                            }
                        }) { Text("Deliver", color = TtGreen, fontWeight = FontWeight.Bold) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TtCard),
            )
        },
        containerColor = TtBg,
    ) { padding ->
        if (selectedPicklist == null) {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(shippedLists) { picklist ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TtCard)
                            .border(1.dp, TtBorder, RoundedCornerShape(12.dp)).clickable { selectedPicklist = picklist }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(picklist.invoiceNumber ?: "Pick-${picklist.id}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(picklist.dispatchStatusLabel, fontSize = 12.sp, color = TtMuted)
                        }
                        Text("→", fontSize = 18.sp, color = TtNavy)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Invoice: ${selectedPicklist?.invoiceNumber ?: "—"}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = scanInput,
                        onValueChange = { scanInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Scan box SSCC barcode") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                    )
                    Button(
                        onClick = {
                            if (scanInput.isBlank()) return@Button
                            scope.launch {
                                val sscc = Gs1Parser.parse(scanInput.trim())?.sscc ?: scanInput.trim().filter { it.isDigit() }
                                runCatching {
                                    message = "Box scanned: $sscc"
                                    scanInput = ""
                                }.onFailure { error = it.message }
                            }
                        },
                        enabled = scanInput.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Scan")
                    }
                }

                message?.let {
                    Text(it, color = TtGreen, fontSize = 13.sp)
                }
                error?.let {
                    Text(it, color = TtRed, fontSize = 13.sp)
                }
            }
        }
    }
}

// ── Pick Screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackTracePickScreen(
    onBack: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }
    val pickerId = remember { WmsSession.userId(session) }

    var lists by remember { mutableStateOf<List<core.network.wms.WmsPickListItem>>(emptyList()) }
    var selectedList by remember { mutableStateOf<core.network.wms.WmsPickListItem?>(null) }
    var lines by remember { mutableStateOf<List<core.network.wms.WmsPickListLine>>(emptyList()) }
    var scanInput by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        runCatching {
            lists = repo.fetchPickListsByPicker(pickerId, companyId).pickLists
        }.onFailure { error = it.message }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pick", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                actions = {
                    if (selectedList != null) {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching {
                                    repo.startIndustryPickList(selectedList?.pickListId.orEmpty())
                                    message = "Pick list started."
                                }.onFailure { error = it.message }
                            }
                        }) { Text("Start", color = TtNavy, fontWeight = FontWeight.Bold) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TtCard),
            )
        },
        containerColor = TtBg,
    ) { padding ->
        if (selectedList == null) {
            LazyColumn(
                modifier = Modifier.padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(lists) { list ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TtCard)
                            .border(1.dp, TtBorder, RoundedCornerShape(12.dp)).clickable {
                                selectedList = list
                                scope.launch {
                                    isLoading = true
                                    runCatching {
                                        lines = repo.fetchPickListLines(list.pickListId.orEmpty(), companyId).pickLines
                                    }.onFailure { error = it.message }
                                    isLoading = false
                                }
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(list.pickListNumber ?: list.id, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("${lines.size} lines", fontSize = 12.sp, color = TtMuted)
                        }
                        Text("→", fontSize = 18.sp, color = TtNavy)
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = scanInput,
                        onValueChange = { scanInput = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Scan picked item") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                    )
                    Button(
                        onClick = {
                            if (scanInput.isBlank() || lines.isEmpty()) return@Button
                            scope.launch {
                                runCatching {
                                    val firstLine = lines.first()
                                    repo.recordPickLine(
                                        selectedList?.pickListId.orEmpty(),
                                        firstLine.resolvedId,
                                        1,
                                        pickerId,
                                    )
                                    message = "Item recorded."
                                    scanInput = ""
                                    lines = repo.fetchPickListLines(selectedList?.pickListId.orEmpty(), companyId).pickLines
                                }.onFailure { error = it.message }
                            }
                        },
                        enabled = scanInput.isNotBlank(),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Record")
                    }
                }

                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(lines) { line ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(TtCard).padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(line.displayName, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                Text("Loc: ${line.displayLocation}", fontSize = 12.sp, color = TtMuted)
                            }
                            Text("${line.resolvedPickedQty()}/${line.resolvedRequestedQty()}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TtNavy)
                        }
                    }
                }

                message?.let {
                    Text(it, color = TtGreen, fontSize = 13.sp)
                }
                error?.let {
                    Text(it, color = TtRed, fontSize = 13.sp)
                }
            }
        }
    }
}
