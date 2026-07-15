package features.app.warehouse.wms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.epcis.EpcisFlowService
import core.network.repository.WmsRepository
import core.network.wms.DispatchInvoiceEntryMode
import core.network.wms.WmsDispatchCompany
import core.network.wms.WmsDispatchFullRequest
import core.network.wms.WmsDispatchManualInvoiceRequest
import core.network.wms.WmsPickListItem
import core.network.wms.adminPackingCardTitle
import core.network.wms.adminPackingLocationLine
import core.network.models.L3ShipmentProduct
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

private val DispatchPageTop = Color(0xFFEEF1FB)
private val DispatchPageBottom = Color(0xFFF7F8FC)
private val DispatchCardBorder = Color(0xFFECEEF5)
private val DispatchMuted = Color(0xFF8B90A0)
private val DispatchDark = Color(0xFF11142B)
private val DispatchAccentBlue = Color(0xFF2563EB)
private val DispatchAccentPurple = Color(0xFF7C3AED)
private val DispatchAccentOrange = Color(0xFFD97706)

@Composable
fun DispatchInvoiceScreen(
    pickList: WmsPickListItem,
    onBack: () -> Unit,
    onDispatched: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }
    val userId = remember { WmsSession.userId(session) }

    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    var companies by remember { mutableStateOf<List<WmsDispatchCompany>>(emptyList()) }
    var loadingCompanies by remember { mutableStateOf(true) }
    var companiesError by remember { mutableStateOf<String?>(null) }
    var selectedCompanyId by remember { mutableStateOf<Int?>(null) }
    var companySearch by remember { mutableStateOf("") }
    var showCompanyDropdown by remember { mutableStateOf(false) }

    var carrier by remember { mutableStateOf(pickList.carrier.orEmpty()) }
    var trackingNumber by remember { mutableStateOf(pickList.trackingNumber.orEmpty()) }
    var dispatchNotes by remember { mutableStateOf("") }

    var invoiceMode by remember { mutableStateOf(DispatchInvoiceEntryMode.Upload) }
    var invoiceNumber by remember { mutableStateOf(pickList.invoiceNumber.orEmpty()) }
    var invoiceDate by remember { mutableStateOf(today) }
    var pickedFile by remember { mutableStateOf<PickedInvoiceDocument?>(null) }

    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSuccess by remember { mutableStateOf(false) }
    var successMessage by remember { mutableStateOf("") }

    val pickingListId = pickList.numericId
        ?: pickList.pickListId?.toIntOrNull()
        ?: 0

    val launchDocumentPicker = rememberInvoiceDocumentPicker { doc ->
        if (doc != null) {
            pickedFile = doc
            error = null
        }
    }

    fun loadCompanies() {
        scope.launch {
            loadingCompanies = true
            companiesError = null
            runCatching {
                companies = repo.fetchDispatchCompanies().companies.filter { it.id > 0 }
            }.onFailure { companiesError = it.message }
            loadingCompanies = false
        }
    }

    LaunchedEffect(Unit) { loadCompanies() }

    val selectedCompanyName = companies.firstOrNull { it.id == selectedCompanyId }?.companyName.orEmpty()
    val filteredCompanies = companies.filter {
        companySearch.isBlank() || it.companyName.contains(companySearch, ignoreCase = true)
    }

    val canSubmit = selectedCompanyId != null && when (invoiceMode) {
        DispatchInvoiceEntryMode.Upload -> pickedFile != null
        DispatchInvoiceEntryMode.Manual -> invoiceNumber.trim().isNotEmpty()
    }

    if (showSuccess) {
        AlertDialog(
            onDismissRequest = { showSuccess = false; onDispatched() },
            title = { Text("Dispatched Successfully") },
            text = { Text(successMessage) },
            confirmButton = {
                TextButton(onClick = { showSuccess = false; onDispatched() }) { Text("OK") }
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DispatchPageTop, DispatchPageBottom))),
    ) {
        DispatchHeader(onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            DispatchPickListSummary(pickList = pickList)

            DispatchSectionLabel("RECEIVING COMPANY", required = true)
            when {
                loadingCompanies -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, DispatchCardBorder, RoundedCornerShape(14.dp))
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = WmsColors.Navy, modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    }
                }
                companiesError != null -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White)
                            .border(1.dp, DispatchCardBorder, RoundedCornerShape(14.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(companiesError.orEmpty(), color = WmsColors.ErrorFg, fontSize = 13.sp)
                        Button(onClick = { loadCompanies() }, colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy)) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                else -> {
                    DispatchCompanySelector(
                        selectedCompanyId = selectedCompanyId,
                        selectedCompanyName = selectedCompanyName,
                        expanded = showCompanyDropdown,
                        onToggle = { showCompanyDropdown = !showCompanyDropdown },
                        search = companySearch,
                        onSearchChange = { companySearch = it },
                        companies = filteredCompanies,
                        onSelect = {
                            selectedCompanyId = it
                            showCompanyDropdown = false
                            companySearch = ""
                        },
                    )
                }
            }

            DispatchSectionLabel("DISPATCH DETAILS")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DispatchDetailField(
                    icon = Icons.Default.LocalShipping,
                    tint = DispatchAccentBlue,
                    placeholder = "Carrier (e.g. BlueDart)",
                    value = carrier,
                    onValueChange = { carrier = it },
                )
                DispatchDetailField(
                    icon = Icons.Default.Tag,
                    tint = DispatchAccentPurple,
                    placeholder = "Tracking number",
                    value = trackingNumber,
                    onValueChange = { trackingNumber = it },
                )
                DispatchDetailField(
                    icon = Icons.Default.Note,
                    tint = DispatchAccentOrange,
                    placeholder = "Notes (optional)",
                    value = dispatchNotes,
                    onValueChange = { dispatchNotes = it },
                )
            }

            DispatchSectionLabel("INVOICE", required = true)
            DispatchInvoiceModeSelector(
                selected = invoiceMode,
                onSelect = {
                    invoiceMode = it
                    error = null
                },
            )

            when (invoiceMode) {
                DispatchInvoiceEntryMode.Upload -> {
                    if (pickedFile != null) {
                        DispatchPickedFileCard(
                            file = pickedFile!!,
                            onRemove = { pickedFile = null },
                        )
                    } else {
                        DispatchUploadDropZone(onClick = launchDocumentPicker)
                    }
                }
                DispatchInvoiceEntryMode.Manual -> {
                    DispatchDetailField(
                        icon = Icons.Default.Numbers,
                        tint = WmsColors.Navy,
                        placeholder = "e.g. INV-2026-001",
                        value = invoiceNumber,
                        onValueChange = { invoiceNumber = it },
                    )
                }
            }

            DispatchInvoiceDateField(
                date = invoiceDate,
                onDateChange = { invoiceDate = it },
            )

            error?.let { DispatchErrorBanner(it) { error = null } }
        }

        DispatchFooter(
            enabled = canSubmit && !submitting,
            submitting = submitting,
            onDispatch = {
                if (pickingListId <= 0) {
                    error = "Invalid pick list ID."
                    return@DispatchFooter
                }
                val receivingCompanyId = selectedCompanyId
                if (receivingCompanyId == null || receivingCompanyId <= 0) {
                    error = "Please select a receiving company."
                    return@DispatchFooter
                }

                val trimmedInvoice = invoiceNumber.trim()
                val finalInvoiceNumber = when (invoiceMode) {
                    DispatchInvoiceEntryMode.Upload -> {
                        trimmedInvoice.ifBlank {
                            pickedFile?.name?.substringBeforeLast('.')
                                ?: "INV-${Clock.System.now().epochSeconds}"
                        }
                    }
                    DispatchInvoiceEntryMode.Manual -> trimmedInvoice
                }
                if (finalInvoiceNumber.isBlank()) {
                    error = "Please enter an invoice number."
                    return@DispatchFooter
                }
                if (invoiceMode == DispatchInvoiceEntryMode.Upload && pickedFile == null) {
                    error = "Please select a file to upload."
                    return@DispatchFooter
                }

                val dateString = invoiceDate.toApiDateString()
                val carrierValue = carrier.trim().takeIf { it.isNotBlank() }
                val trackingValue = trackingNumber.trim().takeIf { it.isNotBlank() }
                val notesValue = dispatchNotes.trim().takeIf { it.isNotBlank() }

                scope.launch {
                    submitting = true
                    error = null
                    runCatching {
                        when (invoiceMode) {
                            DispatchInvoiceEntryMode.Upload -> {
                                val file = pickedFile ?: error("Please select a file to upload.")
                                repo.uploadDispatchInvoice(
                                    pickingListId = pickingListId,
                                    companyId = companyId,
                                    invoiceNumber = finalInvoiceNumber,
                                    invoiceDate = dateString,
                                    dispatchedBy = userId.takeIf { it > 0 },
                                    fileBytes = file.bytes,
                                    fileName = file.name,
                                )
                            }
                            DispatchInvoiceEntryMode.Manual -> {
                                repo.createManualDispatchInvoice(
                                    WmsDispatchManualInvoiceRequest(
                                        pickingListId = pickingListId,
                                        companyId = companyId,
                                        invoiceNumber = finalInvoiceNumber,
                                        invoiceDate = dateString,
                                        dispatchedBy = userId.takeIf { it > 0 },
                                    ),
                                )
                            }
                        }

                        val response = repo.dispatchWithInvoice(
                            WmsDispatchFullRequest(
                                pickingListId = pickingListId,
                                companyId = companyId,
                                receivingCompanyId = receivingCompanyId,
                                carrier = carrierValue,
                                trackingNumber = trackingValue,
                                invoiceNumber = finalInvoiceNumber,
                                invoiceDate = dateString,
                                dispatchedBy = userId.takeIf { it > 0 },
                                notes = notesValue,
                            ),
                        )
                        val pickListId = pickList.numericId?.toString()
                            ?: pickList.pickListId?.trim()?.takeIf { it.isNotEmpty() }
                            ?: pickList.pickListCode?.trim()?.takeIf { it.isNotEmpty() }
                            ?: ""
                        if (pickListId.isNotEmpty()) {
                            runCatching {
                                println("=== L3 SHIPMENT === Fetching products for pickListId: $pickListId")
                                val apiResponse = repo.fetchIndustryPickingProducts(pickListId)
                                println("=== L3 SHIPMENT === apiResponse: products=${apiResponse.products?.size ?: 0}")
                                val apiProducts = apiResponse.products.orEmpty()
                                val products = buildL3ShipmentProducts(apiProducts)
                                println("=== L3 SHIPMENT === built ${products.size} L3 products")
                                val bizId = finalInvoiceNumber.trim().ifEmpty { "ASN-$pickListId" }
                                if (products.isNotEmpty()) {
                                    println("=== L3 SHIPMENT === Calling shipL3...")
                                    EpcisFlowService.shipL3(
                                        session = session,
                                        sourceGln = companyId.toString().padStart(13, '0'),
                                        destinationGln = receivingCompanyId.toString().padStart(13, '0'),
                                        bizTransactionId = bizId,
                                        products = products,
                                    )
                                } else {
                                    println("=== L3 SHIPMENT === Skipped — no products built")
                                }
                            }.onFailure {
                                println("=== L3 SHIPMENT === FAILED: ${it.message}")
                                it.printStackTrace()
                            }
                        }
                        successMessage = response.message
                            ?: "Pick list dispatched to $selectedCompanyName with invoice $finalInvoiceNumber."
                        showSuccess = true
                    }.onFailure { error = it.message }
                    submitting = false
                }
            },
        )
    }
}

@Composable
private fun DispatchHeader(onBack: () -> Unit) {
    WmsLightHeader(
        title = "Dispatch Pick List",
        subtitle = "Complete all details to dispatch",
        showBack = true,
        onBack = onBack,
    )
}

@Composable
private fun DispatchPickListSummary(pickList: WmsPickListItem) {
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .border(1.dp, DispatchCardBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(WmsColors.Navy, WmsColors.Navy.copy(alpha = 0.7f)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.LocalShipping, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("PICK LIST", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DispatchMuted, letterSpacing = 0.8.sp)
            Text(pickList.adminPackingCardTitle, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = DispatchDark)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = Color(0xFFDC2626), modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(4.dp))
                Text(pickList.adminPackingLocationLine, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = DispatchMuted)
            }
        }
    }
}

@Composable
private fun DispatchSectionLabel(text: String, required: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DispatchMuted, letterSpacing = 0.8.sp)
        if (required) {
            Text("*", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
        }
    }
}

@Composable
private fun DispatchCompanySelector(
    selectedCompanyId: Int?,
    selectedCompanyName: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    search: String,
    onSearchChange: (String) -> Unit,
    companies: List<WmsDispatchCompany>,
    onSelect: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(
                    width = if (selectedCompanyId != null) 1.6.dp else 1.dp,
                    color = if (selectedCompanyId != null) WmsColors.Success.copy(alpha = 0.55f) else DispatchCardBorder,
                    shape = RoundedCornerShape(14.dp),
                )
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(
                        if (selectedCompanyId != null) WmsColors.Success.copy(alpha = 0.12f)
                        else WmsColors.Navy.copy(alpha = 0.07f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Business,
                    null,
                    tint = if (selectedCompanyId != null) WmsColors.Success else Color(0xFF9CA3AF),
                    modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                if (selectedCompanyId != null) selectedCompanyName else "Select a company",
                fontSize = 14.sp,
                fontWeight = if (selectedCompanyId != null) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selectedCompanyId != null) DispatchDark else DispatchMuted,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                null,
                tint = DispatchMuted,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .border(1.dp, DispatchCardBorder, RoundedCornerShape(14.dp)),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFF3F4F8))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Search, null, tint = DispatchMuted, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(8.dp))
                        BasicTextField(
                            value = search,
                            onValueChange = onSearchChange,
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = DispatchDark,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            decorationBox = { inner ->
                                Box(contentAlignment = Alignment.CenterStart) {
                                    if (search.isEmpty()) {
                                        Text(
                                            "Search companies...",
                                            color = DispatchMuted,
                                            fontSize = 14.sp,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        if (search.isNotEmpty()) {
                            Icon(
                                Icons.Default.Clear,
                                contentDescription = "Clear search",
                                tint = DispatchMuted,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { onSearchChange("") },
                            )
                        }
                    }
                }

                HorizontalDivider(color = DispatchCardBorder)

                if (companies.isEmpty()) {
                    Text(
                        "No companies found",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
                        fontSize = 13.sp,
                        color = DispatchMuted,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 220.dp)) {
                        items(companies, key = { it.id }) { company ->
                            DispatchCompanyRow(
                                company = company,
                                selected = company.id == selectedCompanyId,
                                showDivider = company != companies.last(),
                                onSelect = { onSelect(company.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DispatchCompanyRow(
    company: WmsDispatchCompany,
    selected: Boolean,
    showDivider: Boolean,
    onSelect: () -> Unit,
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (selected) WmsColors.Success.copy(alpha = 0.06f) else Color.Transparent)
                .clickable(onClick = onSelect)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(
                        if (selected) WmsColors.Success else WmsColors.Navy.copy(alpha = 0.08f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    company.companyName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else WmsColors.Navy,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    company.companyName,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = DispatchDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                company.displayLocation?.let {
                    Text(
                        it,
                        fontSize = 11.sp,
                        color = DispatchMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (selected) {
                Icon(Icons.Default.CheckCircle, null, tint = WmsColors.Success, modifier = Modifier.size(18.dp))
            }
        }
        if (showDivider) {
            HorizontalDivider(
                color = DispatchCardBorder,
                modifier = Modifier.padding(start = 54.dp),
            )
        }
    }
}

@Composable
private fun DispatchDetailField(
    icon: ImageVector,
    tint: Color,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White)
            .border(1.dp, DispatchCardBorder, RoundedCornerShape(13.dp))
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(placeholder, color = DispatchMuted, fontSize = 14.sp)
                }
                inner()
            },
        )
    }
}

@Composable
private fun DispatchInvoiceModeSelector(
    selected: DispatchInvoiceEntryMode,
    onSelect: (DispatchInvoiceEntryMode) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        DispatchInvoiceEntryMode.entries.forEach { mode ->
            val active = selected == mode
            Row(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) WmsColors.Navy else Color.White)
                    .border(
                        width = if (active) 0.dp else 1.4.dp,
                        color = DispatchCardBorder,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .clickable { onSelect(mode) }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (mode == DispatchInvoiceEntryMode.Upload) Icons.Default.PostAdd else Icons.Default.Keyboard,
                    null,
                    tint = if (active) Color.White else WmsColors.Navy,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    mode.label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (active) Color.White else WmsColors.Navy,
                )
            }
        }
    }
}

@Composable
private fun DispatchUploadDropZone(onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DispatchAccentBlue.copy(alpha = 0.035f))
            .border(
                width = 2.dp,
                color = DispatchAccentBlue.copy(alpha = 0.35f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(DispatchAccentBlue.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.PostAdd, null, tint = DispatchAccentBlue, modifier = Modifier.size(24.dp))
        }
        Text("Tap to select invoice file", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF374151))
        Text("PDF, Word, Excel, or CSV", fontSize = 12.sp, color = DispatchMuted)
    }
}

@Composable
private fun DispatchPickedFileCard(
    file: PickedInvoiceDocument,
    onRemove: () -> Unit,
) {
    val iconColor = fileIconColor(file.extension)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.6.dp, WmsColors.Success.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Description, null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${file.sizeLabel} · ${file.extension.uppercase()}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = DispatchMuted,
            )
        }
        Icon(
            Icons.Default.Close,
            "Remove",
            tint = Color(0xFFC1C5D0),
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onRemove),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DispatchInvoiceDateField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val millis = remember(date) { date.atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds() }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = millis)

    if (showPicker) {
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { selected ->
                            onDateChange(
                                Instant.fromEpochMilliseconds(selected)
                                    .toLocalDateTime(TimeZone.currentSystemDefault())
                                    .date,
                            )
                        }
                        showPicker = false
                    },
                ) { Text("OK", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(13.dp))
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White)
            .border(1.dp, DispatchCardBorder, RoundedCornerShape(13.dp))
            .clickable { showPicker = true }
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DispatchAccentPurple.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.CalendarMonth, null, tint = DispatchAccentPurple, modifier = Modifier.size(14.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text("Invoice Date", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = DispatchDark, modifier = Modifier.weight(1f))
        Text(
            date.toDisplayDateString(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = DispatchDark,
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFFF3F4F6))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun DispatchErrorBanner(message: String, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFFEF2F2))
            .border(1.dp, Color(0xFFFCA5A5).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, color = WmsColors.ErrorFg, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Icon(Icons.Default.Close, "Dismiss", tint = WmsColors.ErrorFg, modifier = Modifier.size(16.dp).clickable(onClick = onDismiss))
    }
}

@Composable
private fun DispatchFooter(
    enabled: Boolean,
    submitting: Boolean,
    onDispatch: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Button(
            onClick = onDispatch,
            enabled = enabled && !submitting,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WmsColors.Success,
                disabledContainerColor = Color.Gray.copy(alpha = 0.35f),
            ),
        ) {
            if (submitting) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Default.LocalShipping, null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Dispatch", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

private fun LocalDate.toApiDateString(): String {
    val month = monthNumber.toString().padStart(2, '0')
    val day = dayOfMonth.toString().padStart(2, '0')
    return "$year-$month-$day"
}

private fun LocalDate.toDisplayDateString(): String {
    val monthNames = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
    return "$dayOfMonth ${monthNames[monthNumber - 1]} $year"
}

private fun fileIconColor(extension: String): Color = when (extension.lowercase()) {
    "pdf" -> Color(0xFFDC2626)
    "csv" -> Color(0xFF059669)
    "xlsx", "xls" -> Color(0xFF16A34A)
    "docx", "doc" -> Color(0xFF2563EB)
    else -> Color(0xFF6B7280)
}

private fun buildL3ShipmentProducts(
    apiProducts: List<core.network.wms.IndustryPickingProductItem>,
): List<L3ShipmentProduct> {
    val order = mutableListOf<String>()
    val serialsByGtin = mutableMapOf<String, MutableList<String>>()
    val batchByGtin = mutableMapOf<String, String>()

    for (item in apiProducts) {
        val rawGtin = item.gtin?.trim().orEmpty()
        if (rawGtin.isEmpty()) continue
        val gtin = rawGtin.filter { it.isDigit() }
        if (gtin.isEmpty()) continue
        if (!serialsByGtin.containsKey(gtin)) {
            serialsByGtin[gtin] = mutableListOf()
            order.add(gtin)
        }
        val batch = item.batch?.trim().takeIf { it?.isNotEmpty() == true }
        if (batch != null && !batchByGtin.containsKey(gtin)) {
            batchByGtin[gtin] = batch
        }
    }

    return order.map { gtin ->
        L3ShipmentProduct(
            gtin = gtin,
            serials = serialsByGtin[gtin].orEmpty(),
            batch = batchByGtin[gtin],
        )
    }
}
