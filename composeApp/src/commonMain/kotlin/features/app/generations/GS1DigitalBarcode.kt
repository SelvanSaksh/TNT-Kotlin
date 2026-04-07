package features.app.generations
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import components.AppSwitch
import components.InputField
import components.PrimaryButton
import core.network.models.AuditDetails
import core.network.models.FetchAi
import core.network.repository.AppRepository
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch
import utils.DeviceLocationProvider
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import core.network.models.AuditLogRequest
import core.network.models.LocationDetailsPayload


// ─── Theme ────────────────────────────────────────────────────────────────────
private val Brand       = Color(0xFF133D63)
private val BrandLight  = Color(0xFFE8EFF7)
private val BrandBorder = Color(0xFFB8CCE0)
private val Background  = Color(0xFFF5F8FB)

// ─── Domain options ───────────────────────────────────────────────────────────
private val domains = listOf("dl.ratifye.ai", "sakksh.com", "8aiku.com", "others")

data class GS1Identifier(val key: String, val value: String)

private val gs1ApplicationIdentifiers = listOf(
    GS1Identifier("(01)", "GTIN - Global Trade Item Number"),
    GS1Identifier("(10)", "Batch or Lot Number"),
    GS1Identifier("(11)", "Production Date (YYMMDD)"),
    GS1Identifier("(17)", "Expiration Date (YYMMDD)"),
    GS1Identifier("(21)", "Serial Number"),
    GS1Identifier("(22)", "Consumer Product Variant"),
)

private val gs1ApplicationIndicators = listOf(
    GS1Identifier("11", "Production Date"),
    GS1Identifier("12", "Due Date"),
    GS1Identifier("13", "Packaging Date"),
    GS1Identifier("15", "Best Before Date"),
    GS1Identifier("16", "Sell By Date"),
    GS1Identifier("17", "Expiration Date"),
    GS1Identifier("custom", "Custom Key"),
)

private val GS1_DATE_AIS = setOf("11", "12", "13", "15", "16", "17")

enum class BarcodeType { GS1_QR_CODE, GS1_DATA_MATRIX }

data class DataAttribute(
    val id: String,
    val key: String = "",
    val value: String = "",
    val customKey: String = "",
)

data class GS1FormState(
    val baseDomain: String = "dl.ratifye.ai",
    val customDomain: String = "",
    val identifiers: String = "",
    val identifierValue: String = "",
    val specifyKeyQualifiers: Boolean = false,
    val consumerProductVariant: String = "",
    val batchLotNumber: String = "",
    val serialNumber: String = "",
    val specifyLinkType: Boolean = false,
    val linkType: String = "",
    val enableDataAttributes: Boolean = false,
    val dataAttributes: List<DataAttribute> = emptyList(),
    val finalUrl: String = "",
)

// ─── Helper: URL builder ──────────────────────────────────────────────────────
private fun constructGS1DigitalLink(form: GS1FormState, selectedIdentifier: GS1Identifier?): String {
    val domain = if (form.baseDomain == "others") form.customDomain else form.baseDomain
    val segments = mutableListOf<String>()



    if (form.identifierValue.isNotBlank() && selectedIdentifier != null) {
        val ai = selectedIdentifier.key.replace("(", "").replace(")", "")
        segments += ai
        segments += form.identifierValue
    }

    if (form.specifyKeyQualifiers) {
        if (form.consumerProductVariant.isNotBlank()) { segments += "22"; segments += form.consumerProductVariant }
        if (form.batchLotNumber.isNotBlank())          { segments += "10"; segments += form.batchLotNumber }
        if (form.serialNumber.isNotBlank())             { segments += "21"; segments += form.serialNumber }
    }

    val path = if (segments.isNotEmpty()) "/${segments.joinToString("/")}" else ""

    val queryParts = mutableListOf<String>()
    if (form.specifyLinkType && form.linkType.isNotBlank()) queryParts += "linkType=${form.linkType}"
    form.dataAttributes.forEach { attr ->
        val k = if (attr.key == "custom") attr.customKey else attr.key
        if (k.isNotBlank() && attr.value.isNotBlank()) queryParts += "$k=${attr.value}"
    }

    val query = if (queryParts.isNotEmpty()) "?${queryParts.joinToString("&")}" else ""
    return "$domain$path$query"
}

@OptIn(ExperimentalTime::class)
@Composable
fun GS1DigitalBarcodeScreen(
    onGenerate: (bcType: String, url: String) -> Unit = { _, _ -> },
    generatedBarcodeImageUrl: String? = null,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onErrorDismiss: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    val sessionManager = SessionManager(getLocalStorage())
    val locationProvider = remember { DeviceLocationProvider() }

    val scope = rememberCoroutineScope()
    var barcodeType by remember { mutableStateOf(BarcodeType.GS1_QR_CODE) }
    var form by remember { mutableStateOf(GS1FormState()) }
    var selectedIdentifier by remember { mutableStateOf<GS1Identifier?>(null) }

    var apiIdentifiers by remember { mutableStateOf<List<FetchAi>>(emptyList()) }
    var isDropdownLoading by remember { mutableStateOf(true) }

    var barcodeImageUrl by remember { mutableStateOf<String?>(null) }
    var isGenerating by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = AppRepository.fetchAI()

        result.onSuccess {
            apiIdentifiers = it
            isDropdownLoading = false
            println("result: $it")
        }

        result.onFailure {
            println("Failure")
            isDropdownLoading = false
        }
    }


    // Rebuild URL whenever relevant fields change
    LaunchedEffect(
        form.baseDomain, form.customDomain, form.identifierValue,
        form.consumerProductVariant, form.batchLotNumber, form.serialNumber,
        form.dataAttributes, form.linkType, form.specifyLinkType,
        selectedIdentifier,
    ) {
        val url = constructGS1DigitalLink(form, selectedIdentifier)
        if (url != form.finalUrl) form = form.copy(finalUrl = url)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // ── Page title ─────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            IconButton(
                onClick = onBack
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack, // ✅ better for RTL
                    contentDescription = "Back",
                    tint = Brand
                )
            }

            Text(
                text = "Create GS1 Digital Link Barcode",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Brand,
            )
        }

        // ── URL Preview ────────────────────────────────────────────────────
        SectionCard {
            Text(
                text = form.finalUrl.ifBlank {
                    if (form.baseDomain == "others") form.customDomain else form.baseDomain
                },
                fontSize = 13.sp,
                color = Brand,
                fontWeight = FontWeight.Medium,
            )
        }

        // ── Barcode type selector ──────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BarcodeTypeCard(
                modifier = Modifier.weight(1f),
                label = "GS1-DL QR Code",
                sublabel = "Standard QR format",
                icon = Icons.Default.QrCode,
                selected = barcodeType == BarcodeType.GS1_QR_CODE,
                onClick = { barcodeType = BarcodeType.GS1_QR_CODE },
            )
            BarcodeTypeCard(
                modifier = Modifier.weight(1f),
                label = "GS1-DL DataMatrix",
                sublabel = "Compact 2D format",
                icon = Icons.Default.GridView,
                selected = barcodeType == BarcodeType.GS1_DATA_MATRIX,
                onClick = { barcodeType = BarcodeType.GS1_DATA_MATRIX },
            )
        }

        // ── Main form ──────────────────────────────────────────────────────
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // Domain dropdown
                DropdownField(
                    label = "Domain *",
                    options = domains,
                    selectedOption = form.baseDomain,
                    onSelect = { form = form.copy(baseDomain = it) },
                    displayText = { it.toString() },
                )

                // Custom domain input
                if (form.baseDomain == "others") {
                    InputField(
                        value = form.customDomain,
                        onValueChange = { form = form.copy(customDomain = it) },
                        placeholder = "Enter your custom domain",
                        label = "Custom Domain *",
                    )
                }

                // Identifier dropdown
                if (isDropdownLoading) {

                    // 🔄 Loader UI
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Choose Identifier *",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Brand
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .border(2.dp, BrandBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                } else {

                    // ✅ Actual dropdown
                    DropdownField(
                        label = "Choose Identifier *",
                        options = apiIdentifiers.map {
                            GS1Identifier(
                                key = it.ai.replace(Regex("[^0-9]"), ""), // clean AI
                                value = (it.data_title ?: it.data_content)
                            )
                        },
                        selectedOption = selectedIdentifier,
                        onSelect = { identifier ->
                            selectedIdentifier = identifier
                            form = form.copy(identifiers = identifier?.key ?: "")
                        },
                        displayText = { it?.value ?: "Choose Identifier" },
                    )
                }

                // Identifier value input
                if (selectedIdentifier != null) {
                    InputField(
                        value = form.identifierValue,
                        onValueChange = { form = form.copy(identifierValue = it) },
                        placeholder = "Enter ${selectedIdentifier!!.key}",
                        label = "Enter ${selectedIdentifier!!.value} Value *",
                    )
                }
            }
        }

        // ── Optional toggles & sections ────────────────────────────────────
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                // Key Qualifiers toggle
                ToggleRow(
                    label = "Specify Key Qualifiers",
                    checked = form.specifyKeyQualifiers,
                    onCheckedChange = { form = form.copy(specifyKeyQualifiers = it) },
                )

                if (form.specifyKeyQualifiers) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        InputField(
                            value = form.consumerProductVariant,
                            onValueChange = { form = form.copy(consumerProductVariant = it) },
                            placeholder = "Enter consumer product variant",
                            label = "Consumer Product Variant (22)",
                        )
                        InputField(
                            value = form.batchLotNumber,
                            onValueChange = { form = form.copy(batchLotNumber = it) },
                            placeholder = "Enter batch or lot number",
                            label = "Batch or Lot Number (10)",
                        )
                        InputField(
                            value = form.serialNumber,
                            onValueChange = { form = form.copy(serialNumber = it) },
                            placeholder = "Enter serial number",
                            label = "Serial Number (21)",
                        )
                    }
                }

                Divider(color = BrandBorder, thickness = 0.5.dp)

                // Data Attributes toggle
                ToggleRow(
                    label = "Enable Data Attributes",
                    checked = form.enableDataAttributes,
                    onCheckedChange = { form = form.copy(enableDataAttributes = it) },
                )

                if (form.enableDataAttributes) {
                    // Add button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Data Attributes", fontWeight = FontWeight.SemiBold, color = Brand)
                        TextButton(
                            onClick = {
                                val newAttr = DataAttribute(id = Clock.System.now().toEpochMilliseconds().toString())
                                form = form.copy(dataAttributes = form.dataAttributes + newAttr)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Brand),
                        ) {
                            Text("+ Add Attribute", fontWeight = FontWeight.Medium)
                        }
                    }

                    if (form.dataAttributes.isEmpty()) {
                        EmptyAttributesPlaceholder()
                    } else {
                        form.dataAttributes.forEachIndexed { index, attribute ->
                            DataAttributeCard(
                                index = index,
                                attribute = attribute,
                                onUpdate = { updated ->
                                    form = form.copy(
                                        dataAttributes = form.dataAttributes.map {
                                            if (it.id == updated.id) updated else it
                                        }
                                    )
                                },
                                onRemove = {
                                    form = form.copy(
                                        dataAttributes = form.dataAttributes.filter { it.id != attribute.id }
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }

        PrimaryButton(
            text = if (isGenerating) "Generating…" else "Generate Barcode",
            isLoading = isGenerating,
            onClick = {
                val bcType = if (barcodeType == BarcodeType.GS1_QR_CODE)
                    "gs1dlqrcode"
                else
                    "gs1dldatamatrix"

                val url = "https://${form.finalUrl}"

                isGenerating = true

                scope.launch {
                    val result = AppRepository.generateBarcode(bcType, url)

                    result.onSuccess {

                        barcodeImageUrl = it
                        isGenerating = false

                        // 🔥 AUDIT LOG START
                        scope.launch {

                            var lat = 0.0
                            var lon = 0.0
                            var city: String? = null
                            var state: String? = null

                            var locationPair: Pair<Double, Double>? = null

                            repeat(3) {
                                locationPair = locationProvider.getCurrentLocation()
                                println("📍 Attempt ${it + 1}: $locationPair")

                                if (locationPair != null) return@repeat
                                kotlinx.coroutines.delay(1000)
                            }

                            if (locationPair != null) {
                                lat = locationPair!!.first
                                lon = locationPair!!.second

                                val locationResult = AppRepository.getLocationDetails(lat, lon)

                                locationResult.onSuccess {
                                    city = it.city ?: "Unknown"
                                    state = it.state ?: "Unknown"

                                    println("🏙️ CITY: $city")
                                    println("🌍 STATE: $state")
                                }.onFailure {
                                    println("❌ LOCATION FAILED: ${it.message}")
                                    city = "Unknown"
                                    state = "Unknown"
                                }
                            }

                            val companyId = sessionManager.getCompanyId()
                            if (companyId.isNullOrEmpty()) {
                                println("❌ COMPANY ID MISSING")
                                return@launch
                            }

                            val auditRequest = AuditLogRequest(
                                type = 1,
                                company_id = companyId,
                                user_id = sessionManager.getUserId()?.toString() ?: "",
                                location_details = LocationDetailsPayload(
                                    lat = lat,
                                    long = lon,
                                    currentCity = city,
                                    state = state
                                ),
                                details = AuditDetails(
                                    barcode = url,   // 🔥 IMPORTANT (Digital Link URL)
                                    status = "generated",
                                    barcodeType = if (barcodeType == BarcodeType.GS1_QR_CODE)
                                        "GS1-DL QR Code" else "GS1-DL DataMatrix",
                                    device = "Android",
                                    timestamp = Clock.System.now().toString()
                                )
                            )

                            println("🚀 DIGITAL LINK AUDIT:")
                            println("📦 URL: $url")
                            println("📍 lat: $lat, lon: $lon")

                            val auditResult = AppRepository.sendAuditLog(auditRequest)

                            if (auditResult.isSuccess) {
                                println("✅ DIGITAL LINK AUDIT SUCCESS")
                            } else {
                                println("❌ DIGITAL LINK AUDIT FAILED: ${auditResult.exceptionOrNull()?.message}")
                            }
                        }
                    }

                    result.onFailure {
                        isGenerating = false
                    }
                }
            },
        )


        SectionCard {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Brand
                        )
                    }

                    Text(
                        text = "Create GS1 Digital Link Barcode",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Brand,
                    )
                }
                Spacer(Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BrandLight)
                        .border(1.5.dp, BrandBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (barcodeImageUrl != null){
                        if (barcodeImageUrl != null) {
                            AsyncImage(
                                model = barcodeImageUrl,
                                contentDescription = "Barcode",
                                modifier = Modifier.size(200.dp)
                            )
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = null,
                                tint = BrandBorder,
                                modifier = Modifier.size(56.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Barcode preview will appear here", fontSize = 13.sp, color = Color.Gray)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Divider(color = BrandBorder)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Type:", fontSize = 13.sp, color = Brand, fontWeight = FontWeight.Medium)
                    Text(
                        text = if (barcodeType == BarcodeType.GS1_QR_CODE) "GS1-DL QR Code" else "GS1-DL DataMatrix",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Brand,
                    )
                }
            }
        }

        // Error snackbar / dialog (simplified inline)
        if (errorMessage != null) {
            AlertDialog(
                onDismissRequest = onErrorDismiss,
                title = { Text("Error", color = Brand) },
                text  = { Text(errorMessage) },
                confirmButton = {
                    TextButton(onClick = onErrorDismiss) { Text("OK", color = Brand) }
                },
            )
        }
    }
}

// ─── Reusable sub-components ──────────────────────────────────────────────────

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, BrandBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun BarcodeTypeCard(
    label: String,
    sublabel: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) Brand else BrandBorder,
        animationSpec = tween(200),
    )
    val bgColor by animateColorAsState(
        targetValue = if (selected) BrandLight else Color.White,
        animationSpec = tween(200),
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(2.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) BrandLight else Background),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = Brand, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = Brand)
                Text(sublabel, fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand)
        AppSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> DropdownField(
    label: String,
    options: List<T>,
    selectedOption: T?,
    onSelect: (T) -> Unit,
    displayText: (T?) -> String,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand)

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .clip(shape)
                    .background(Color.White)
                    .border(2.dp, BrandBorder, shape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = displayText(selectedOption),
                    color = if (selectedOption == null) Color.Gray else Brand,
                    fontSize = 14.sp,
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(displayText(option), color = Brand) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DataAttributeCard(
    index: Int,
    attribute: DataAttribute,
    onUpdate: (DataAttribute) -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(BrandLight)
            .border(1.dp, BrandBorder, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Attribute ${index + 1}", fontWeight = FontWeight.SemiBold, color = Brand)
            TextButton(onClick = onRemove, colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)) {
                Text("Remove", fontSize = 12.sp)
            }
        }

        // Key dropdown
        DropdownField(
            label = "Key *",
            options = gs1ApplicationIndicators,
            selectedOption = gs1ApplicationIndicators.find { it.key == attribute.key },
            onSelect = { onUpdate(attribute.copy(key = it.key)) },
            displayText = { it?.value ?: "Select key" },
        )

        // Value input
        val isDateAI = attribute.key in GS1_DATE_AIS
        InputField(
            value = attribute.value,
            onValueChange = { raw ->
                val sanitized = if (isDateAI) raw.filter { it.isDigit() }.take(6) else raw
                onUpdate(attribute.copy(value = sanitized))
            },
            placeholder = if (isDateAI) "YYMMDD" else "Enter value",
            label = "Value *",
        )

        // Custom key
        if (attribute.key == "custom") {
            InputField(
                value = attribute.customKey,
                onValueChange = { onUpdate(attribute.copy(customKey = it)) },
                placeholder = "Enter custom key",
                label = "Custom Key *",
            )
        }
    }
}

@Composable
private fun EmptyAttributesPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(1.5.dp, BrandBorder, RoundedCornerShape(10.dp))
            .padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No data attributes added yet.", color = Color.Gray, fontSize = 13.sp)
            Text("Tap \"Add Attribute\" to get started.", color = Color.Gray, fontSize = 12.sp)
        }
    }
}