package screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import components.InputField
import components.PrimaryButton
import core.storage.SessionManager
import core.storage.getLocalStorage
import utils.DeviceLocationProvider
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// ─── Theme ────────────────────────────────────────────────────────────────────
private val Brand       = Color(0xFF133D63)
private val BrandLight  = Color(0xFFE8EFF7)
private val BrandBorder = Color(0xFFB8CCE0)
private val Background  = Color(0xFFF5F8FB)
private val ErrorRed    = Color(0xFFDC2626)

// ─── Rule types ───────────────────────────────────────────────────────────────
enum class RuleType(val label: String, val icon: ImageVector) {
    LOCATION("Location", Icons.Default.LocationOn),
    NUMBER_OF_SCANS("Number of scans", Icons.Default.QrCodeScanner),
    TIME("Time", Icons.Default.AccessTime),
    GEO_FENCING("Geo-fencing", Icons.Default.Navigation),
    DEVICE("Device", Icons.Default.Smartphone),
}

// ─── Data classes ─────────────────────────────────────────────────────────────
data class LocationData(
    val id: String,
    val url: String = "",
    val country: String = "",
    val state: String = "",
    val city: String = "",
)

data class ScanData(
    val id: String,
    val url: String = "",
    val noOfScan: String = "",
)

data class TimeData(
    val id: String,
    val url: String = "",
    val startTime: String = "",
    val endTime: String = "",
)

data class GeoFencingData(
    val id: String,
    val url: String = "",
    val latitude: String = "",
    val longitude: String = "",
    val radiusInMeter: String = "",
)

data class DeviceData(
    val id: String,
    val url: String = "",
    val device: String = "",
)

data class MultiLinkFormState(
    val barcodeName: String = "",
    val defaultURL: String = "",
    val selectedType: RuleType = RuleType.LOCATION,
    val locations: List<LocationData> = listOf(LocationData(id = "1")),
    val noOfScanData: List<ScanData> = listOf(ScanData(id = "1")),
    val time: List<TimeData> = listOf(TimeData(id = "1")),
    val geoFencing: List<GeoFencingData> = listOf(GeoFencingData(id = "1")),
    val device: List<DeviceData> = listOf(DeviceData(id = "1")),
)

// ─── Sample geo data (replace with country-state-city library or API) ─────────
private val sampleCountries = listOf("India", "United States", "United Kingdom", "Germany", "France")
private val sampleStates = mapOf(
    "India" to listOf("Tamil Nadu", "Maharashtra", "Karnataka", "Delhi"),
    "United States" to listOf("California", "New York", "Texas", "Florida"),
    "United Kingdom" to listOf("England", "Scotland", "Wales"),
)
private val sampleCities = mapOf(
    "Tamil Nadu" to listOf("Chennai", "Coimbatore", "Madurai"),
    "Maharashtra" to listOf("Mumbai", "Pune", "Nagpur"),
    "California" to listOf("Los Angeles", "San Francisco", "San Diego"),
    "New York" to listOf("New York City", "Buffalo", "Albany"),
)

private val availableDevices = listOf("Android", "iPhone")

// ─── Validation ───────────────────────────────────────────────────────────────
private fun isFormDisabled(form: MultiLinkFormState): Boolean {
    if (form.barcodeName.isBlank() || form.defaultURL.isBlank()) return true
    return when (form.selectedType) {
        RuleType.LOCATION       -> form.locations.any { it.url.isBlank() || it.country.isBlank() || it.state.isBlank() || it.city.isBlank() }
        RuleType.NUMBER_OF_SCANS -> form.noOfScanData.any { it.url.isBlank() || it.noOfScan.isBlank() }
        RuleType.TIME           -> form.time.any { it.url.isBlank() || it.startTime.isBlank() || it.endTime.isBlank() }
        RuleType.GEO_FENCING    -> form.geoFencing.any { it.url.isBlank() || it.latitude.isBlank() || it.longitude.isBlank() || it.radiusInMeter.isBlank() }
        RuleType.DEVICE         -> form.device.any { it.url.isBlank() || it.device.isBlank() }
    }
}

// ─── Screen ───────────────────────────────────────────────────────────────────
@Composable
fun MultiLinkBarcodeScreen(
    onGenerate: (payload: Map<String, Any>) -> Unit = {},
    generatedBarcodeImageUrl: String? = null,
    generatedBarcodeTitle: String? = null,
    isLoading: Boolean = false,
    onReset: () -> Unit = {},
    onDownload: (url: String, name: String) -> Unit = { _, _ -> },
    onCopyUrl: (url: String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val sessionManager = SessionManager(getLocalStorage())
    val locationProvider = remember { DeviceLocationProvider() }
    val scope = rememberCoroutineScope()

    var form by remember { mutableStateOf(MultiLinkFormState()) }
    val disabled = remember(form) { isFormDisabled(form) }

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

            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Brand
                )
            }

            Text(
                text = "Generate Multi-Link Barcode",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Brand
            )
        }

        // ── Barcode Details card ───────────────────────────────────────────
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                InputField(
                    value = form.barcodeName,
                    onValueChange = { form = form.copy(barcodeName = it) },
                    placeholder = "Enter name for your barcode",
                    label = "Barcode Title *",
                )
                InputField(
                    value = form.defaultURL,
                    onValueChange = { form = form.copy(defaultURL = it) },
                    placeholder = "https://example.com/default",
                    label = "Default URL *",
                )

                // Rule type selector
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Rule Type *", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand)
                    RuleTypeSelector(
                        selected = form.selectedType,
                        onSelect = { form = form.copy(selectedType = it) },
                    )
                }
            }
        }

        // ── Rules card ────────────────────────────────────────────────────
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${form.selectedType.label} Rules",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Brand,
                    )
                    AddRuleButton { form = addRule(form) }
                }

                when (form.selectedType) {
                    RuleType.LOCATION -> form.locations.forEachIndexed { idx, loc ->
                        LocationRuleCard(
                            index = idx,
                            data = loc,
                            onUpdate = { updated ->
                                form = form.copy(locations = form.locations.map { if (it.id == updated.id) updated else it })
                            },
                            onRemove = if (idx > 0) ({ form = form.copy(locations = form.locations.filter { it.id != loc.id }) }) else null,
                        )
                    }
                    RuleType.NUMBER_OF_SCANS -> form.noOfScanData.forEachIndexed { idx, scan ->
                        ScanRuleCard(
                            index = idx,
                            data = scan,
                            onUpdate = { updated ->
                                form = form.copy(noOfScanData = form.noOfScanData.map { if (it.id == updated.id) updated else it })
                            },
                            onRemove = if (idx > 0) ({ form = form.copy(noOfScanData = form.noOfScanData.filter { it.id != scan.id }) }) else null,
                        )
                    }
                    RuleType.TIME -> form.time.forEachIndexed { idx, t ->
                        TimeRuleCard(
                            index = idx,
                            data = t,
                            onUpdate = { updated ->
                                form = form.copy(time = form.time.map { if (it.id == updated.id) updated else it })
                            },
                            onRemove = if (idx > 0) ({ form = form.copy(time = form.time.filter { it.id != t.id }) }) else null,
                        )
                    }
                    RuleType.GEO_FENCING -> form.geoFencing.forEachIndexed { idx, geo ->
                        GeoFencingRuleCard(
                            index = idx,
                            data = geo,
                            onUpdate = { updated ->
                                form = form.copy(geoFencing = form.geoFencing.map { if (it.id == updated.id) updated else it })
                            },
                            onRemove = if (idx > 0) ({ form = form.copy(geoFencing = form.geoFencing.filter { it.id != geo.id }) }) else null,
                        )
                    }
                    RuleType.DEVICE -> {
                        val usedDevices = form.device.map { it.device }.filter { it.isNotBlank() }
                        form.device.forEachIndexed { idx, dev ->
                            DeviceRuleCard(
                                index = idx,
                                data = dev,
                                availableDevices = availableDevices.filter { it == dev.device || it !in usedDevices },
                                onUpdate = { updated ->
                                    form = form.copy(device = form.device.map { if (it.id == updated.id) updated else it })
                                },
                                onRemove = if (idx > 0) ({ form = form.copy(device = form.device.filter { it.id != dev.id }) }) else null,
                            )
                        }
                    }
                }
            }
        }

        // ── Generate button ────────────────────────────────────────────────
        PrimaryButton(
            text = if (isLoading) "Generating…" else "Generate Smart Barcode",
            isLoading = isLoading,
            enabled = !disabled,
            onClick = { onGenerate(buildPayload(form)) },
        )

        // ── Preview & stats card ───────────────────────────────────────────
        SectionCard {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brand)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Barcode Preview", fontWeight = FontWeight.SemiBold, color = Brand, fontSize = 16.sp)
                }

                if (generatedBarcodeImageUrl != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BrandLight)
                            .border(1.dp, BrandBorder, RoundedCornerShape(12.dp))
                            .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Replace with AsyncImage (Coil/Kamel):
                        // AsyncImage(model = generatedBarcodeImageUrl, contentDescription = "Barcode")
                        Text(
                            "Image: $generatedBarcodeImageUrl",
                            fontSize = 11.sp,
                            color = Brand,
                        )
                    }

                    // Action buttons
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ActionButton(
                            label = "Download Barcode",
                            icon = Icons.Default.Download,
                            containerColor = Color(0xFF16A34A),
                            onClick = { onDownload(generatedBarcodeImageUrl, form.barcodeName) },
                        )
                        ActionButton(
                            label = "Copy URL",
                            icon = Icons.Default.ContentCopy,
                            containerColor = Brand,
                            onClick = { onCopyUrl(generatedBarcodeImageUrl) },
                        )
                        ActionButton(
                            label = "Create New",
                            icon = Icons.Default.Add,
                            containerColor = Color(0xFF4B5563),
                            onClick = {
                                form = MultiLinkFormState()
                                onReset()
                            },
                        )
                    }

                    // Details chip
                    if (generatedBarcodeTitle != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(BrandLight)
                                .padding(10.dp),
                        ) {
                            Text(
                                "$generatedBarcodeTitle • ${form.selectedType.label} • Dynamic",
                                fontSize = 12.sp,
                                color = Brand,
                            )
                        }
                    }

                } else {
                    // ── Empty state ────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 160.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BrandLight)
                            .border(1.5.dp, BrandBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = null,
                                tint = BrandBorder,
                                modifier = Modifier.size(52.dp),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("Your barcode will appear here", fontSize = 13.sp, color = Color.Gray)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Multiple URL redirections", "Smart conditional logic", "High resolution output").forEach {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Brand, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(it, fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }

                Divider(color = BrandBorder)

                // Stats
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Generation Stats", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand)
                    StatRow("Rule Type", form.selectedType.label)
                    StatRow("QR Type", "Dynamic")
                    StatRow("Rules Added", currentRuleCount(form).toString())
                }
            }
        }

    }
}

// ─── Rule card composables ─────────────────────────────────────────────────────

@Composable
private fun LocationRuleCard(
    index: Int,
    data: LocationData,
    onUpdate: (LocationData) -> Unit,
    onRemove: (() -> Unit)?,
) {
    RuleCardShell(
        title = "Location Rule ${index + 1}",
        icon = Icons.Default.LocationOn,
        iconTint = Color(0xFF2563EB),
        onRemove = onRemove,
    ) {
        InputField(
            value = data.url,
            onValueChange = { onUpdate(data.copy(url = it)) },
            placeholder = "Enter URL for this location",
            label = "URL *",
        )

        SimpleDropdown(
            label = "Country *",
            options = sampleCountries,
            selected = data.country,
            onSelect = { onUpdate(data.copy(country = it, state = "", city = "")) },
            placeholder = "Select Country",
        )

        val states = sampleStates[data.country] ?: emptyList()
        SimpleDropdown(
            label = "State *",
            options = states,
            selected = data.state,
            onSelect = { onUpdate(data.copy(state = it, city = "")) },
            placeholder = "Select State",
            enabled = data.country.isNotBlank(),
        )

        val cities = sampleCities[data.state] ?: emptyList()
        SimpleDropdown(
            label = "City *",
            options = cities,
            selected = data.city,
            onSelect = { onUpdate(data.copy(city = it)) },
            placeholder = "Select City",
            enabled = data.state.isNotBlank(),
        )
    }
}

@Composable
private fun ScanRuleCard(
    index: Int,
    data: ScanData,
    onUpdate: (ScanData) -> Unit,
    onRemove: (() -> Unit)?,
) {
    RuleCardShell(
        title = "Scan Rule ${index + 1}",
        icon = Icons.Default.QrCodeScanner,
        iconTint = Color(0xFF16A34A),
        onRemove = onRemove,
    ) {
        InputField(
            value = data.url,
            onValueChange = { onUpdate(data.copy(url = it)) },
            placeholder = "Enter URL",
            label = "URL *",
        )
        InputField(
            value = data.noOfScan,
            onValueChange = { onUpdate(data.copy(noOfScan = it.filter { c -> c.isDigit() })) },
            placeholder = "Enter number of scans",
            label = "Number of Scans *",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
    }
}

@Composable
private fun TimeRuleCard(
    index: Int,
    data: TimeData,
    onUpdate: (TimeData) -> Unit,
    onRemove: (() -> Unit)?,
) {
    RuleCardShell(
        title = "Time Rule ${index + 1}",
        icon = Icons.Default.AccessTime,
        iconTint = Color(0xFF7C3AED),
        onRemove = onRemove,
    ) {
        InputField(
            value = data.url,
            onValueChange = { onUpdate(data.copy(url = it)) },
            placeholder = "Enter URL",
            label = "URL *",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.weight(1f)) {
                InputField(
                    value = data.startTime,
                    onValueChange = { onUpdate(data.copy(startTime = it)) },
                    placeholder = "HH:MM",
                    label = "Start Time *",
                )
            }
            Box(Modifier.weight(1f)) {
                InputField(
                    value = data.endTime,
                    onValueChange = { onUpdate(data.copy(endTime = it)) },
                    placeholder = "HH:MM",
                    label = "End Time *",
                )
            }
        }
    }
}

@Composable
private fun GeoFencingRuleCard(
    index: Int,
    data: GeoFencingData,
    onUpdate: (GeoFencingData) -> Unit,
    onRemove: (() -> Unit)?,
) {
    RuleCardShell(
        title = "Geo-fencing Rule ${index + 1}",
        icon = Icons.Default.Navigation,
        iconTint = Color(0xFF0891B2),
        onRemove = onRemove,
    ) {
        InputField(
            value = data.url,
            onValueChange = { onUpdate(data.copy(url = it)) },
            placeholder = "Enter URL for this geo-fenced area",
            label = "URL *",
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.weight(1f)) {
                InputField(
                    value = data.latitude,
                    onValueChange = { onUpdate(data.copy(latitude = it)) },
                    placeholder = "e.g. 40.7128",
                    label = "Latitude *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            Box(Modifier.weight(1f)) {
                InputField(
                    value = data.longitude,
                    onValueChange = { onUpdate(data.copy(longitude = it)) },
                    placeholder = "e.g. -74.0060",
                    label = "Longitude *",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }
        InputField(
            value = data.radiusInMeter,
            onValueChange = { onUpdate(data.copy(radiusInMeter = it.filter { c -> c.isDigit() })) },
            placeholder = "e.g. 100",
            label = "Radius (meters) *",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        // Info hint
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BrandLight)
                .padding(10.dp),
        ) {
            Text(
                "💡 Enter center coordinates and radius to define the geo-fenced area.",
                fontSize = 11.sp,
                color = Brand,
            )
        }
    }
}

@Composable
private fun DeviceRuleCard(
    index: Int,
    data: DeviceData,
    availableDevices: List<String>,
    onUpdate: (DeviceData) -> Unit,
    onRemove: (() -> Unit)?,
) {
    RuleCardShell(
        title = "Device Rule ${index + 1}",
        icon = Icons.Default.Smartphone,
        iconTint = Brand,
        onRemove = onRemove,
    ) {
        InputField(
            value = data.url,
            onValueChange = { onUpdate(data.copy(url = it)) },
            placeholder = "Enter URL for this device",
            label = "URL *",
        )
        SimpleDropdown(
            label = "Device *",
            options = availableDevices,
            selected = data.device,
            onSelect = { onUpdate(data.copy(device = it)) },
            placeholder = "Choose a device type",
        )
        if (availableDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFEF3C7))
                    .padding(10.dp),
            ) {
                Text(
                    "⚠️ All devices have been selected in other rules.",
                    fontSize = 11.sp,
                    color = Color(0xFF92400E),
                )
            }
        }
    }
}

// ─── Shared layout composables ────────────────────────────────────────────────

@Composable
private fun RuleCardShell(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    onRemove: (() -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(1.dp, BrandBorder, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Brand)
            }
            if (onRemove != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEE2E2))
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = ErrorRed, modifier = Modifier.size(14.dp))
                }
            }
        }
        content()
    }
}

@Composable
private fun RuleTypeSelector(
    selected: RuleType,
    onSelect: (RuleType) -> Unit,
) {
    val types = RuleType.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Two per row
        types.chunked(2).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { type ->
                    val isSelected = selected == type
                    val bgColor by animateColorAsState(
                        if (isSelected) BrandLight else Color.White, tween(200)
                    )
                    val borderColor by animateColorAsState(
                        if (isSelected) Brand else BrandBorder, tween(200)
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(bgColor)
                            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(10.dp))
                            .clickable { onSelect(type) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            type.icon,
                            contentDescription = null,
                            tint = if (isSelected) Brand else Color.Gray,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            type.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Brand else Color.Gray,
                        )
                    }
                }
                // Fill last row if odd count
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SimpleDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    placeholder: String,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Brand else Color.Gray)
        ExposedDropdownMenuBox(
            expanded = expanded && enabled,
            onExpandedChange = { if (enabled) expanded = it },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
                    .clip(shape)
                    .background(if (enabled) Color.White else Color(0xFFF3F4F6))
                    .border(2.dp, if (enabled) BrandBorder else Color(0xFFE5E7EB), shape)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = selected.ifBlank { placeholder },
                    color = if (selected.isBlank()) Color.Gray else Brand,
                    fontSize = 14.sp,
                )
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
            ExposedDropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false },
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, color = Brand) },
                        onClick = { onSelect(option); expanded = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddRuleButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Brand)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text("Add Rule", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = Color.Gray)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand)
    }
}

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
private fun PageTitle(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brand)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Brand)
    }
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalTime::class)
private fun addRule(form: MultiLinkFormState): MultiLinkFormState {
    val id = Clock.System.now().toEpochMilliseconds().toString()
    return when (form.selectedType) {
        RuleType.LOCATION        -> form.copy(locations = form.locations + LocationData(id))
        RuleType.NUMBER_OF_SCANS -> form.copy(noOfScanData = form.noOfScanData + ScanData(id))
        RuleType.TIME            -> form.copy(time = form.time + TimeData(id))
        RuleType.GEO_FENCING     -> form.copy(geoFencing = form.geoFencing + GeoFencingData(id))
        RuleType.DEVICE          -> form.copy(device = form.device + DeviceData(id))
    }
}

private fun currentRuleCount(form: MultiLinkFormState): Int = when (form.selectedType) {
    RuleType.LOCATION        -> form.locations.size
    RuleType.NUMBER_OF_SCANS -> form.noOfScanData.size
    RuleType.TIME            -> form.time.size
    RuleType.GEO_FENCING     -> form.geoFencing.size
    RuleType.DEVICE          -> form.device.size
}

private fun buildPayload(form: MultiLinkFormState): Map<String, Any> {
    val data: List<Map<String, Any>> = when (form.selectedType) {
        RuleType.LOCATION -> form.locations.map {
            mapOf("type" to "Location", "details" to mapOf("url" to it.url, "country" to it.country, "state" to it.state, "city" to it.city))
        }
        RuleType.NUMBER_OF_SCANS -> form.noOfScanData.map {
            mapOf("type" to "Number of scans", "details" to mapOf("url" to it.url, "noOfScan" to it.noOfScan))
        }
        RuleType.TIME -> form.time.map {
            mapOf("type" to "Time", "details" to mapOf("url" to it.url, "startTime" to it.startTime, "endTime" to it.endTime))
        }
        RuleType.GEO_FENCING -> form.geoFencing.map {
            mapOf("type" to "Geo-fencing", "details" to mapOf("url" to it.url, "latitude" to it.latitude, "longitude" to it.longitude, "radiusInMeter" to it.radiusInMeter))
        }
        RuleType.DEVICE -> form.device.map {
            mapOf("type" to "Device", "details" to mapOf("url" to it.url, "device" to it.device))
        }
    }
    return mapOf(
        "defaultURL" to form.defaultURL,
        "title"      to form.barcodeName,
        "data"       to data,
        "qrType"     to "dynamic",
        "fromWeb"    to 2,
        "loggedIn"   to 1,
    )
}