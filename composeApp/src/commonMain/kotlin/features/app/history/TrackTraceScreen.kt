package features.app.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.models.BarcodeLookupQuery
import core.network.models.BarcodeLookupResponse
import core.network.models.BarcodeTraceEvent
import core.network.models.TraceProduct
import core.network.repository.AppRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private val PageBg = Color(0xFFF5F6FA)
private val TextPrimary = Color(0xFF111827)
private val TextMuted = Color(0xFF9CA3AF)
private val BlueAccent = Color(0xFF163C66)
private val BlueBg = Color(0xFFEFF6FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackTraceScreen(
    lookupQuery: BarcodeLookupQuery,
    onBack: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var response by remember { mutableStateOf<BarcodeLookupResponse?>(null) }
    var resolvedAddresses by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }

    LaunchedEffect(lookupQuery.gtin, lookupQuery.batch, lookupQuery.serial, lookupQuery.legacyKey) {
        loading = true
        error = null
        response = null
        resolvedAddresses = emptyMap()
        val result = AppRepository.lookupBarcodeLogs(lookupQuery)
        result.fold(
            onSuccess = { response = it },
            onFailure = { error = it.message ?: "Request failed" },
        )
        loading = false
    }

    val events = remember(response) {
        response?.data.orEmpty().sortedBy { ev ->
            runCatching { Instant.parse(normalizeIso(ev.event_time.orEmpty())) }.getOrNull()
                ?: Instant.DISTANT_FUTURE
        }
    }

    LaunchedEffect(events) {
        if (events.isEmpty()) return@LaunchedEffect
        coroutineScope {
            val pairs = events.mapIndexed { index, ev ->
                async {
                    index to resolveEventAddress(ev)
                }
            }.awaitAll()
            resolvedAddresses = pairs.toMap()
        }
    }

    Scaffold(
        containerColor = PageBg,
        topBar = {
            TopAppBar(
                title = {
                    Text("Track & Trace", fontWeight = FontWeight.Bold, color = TextPrimary)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
            )
        },
    ) { padding ->
        when {
            loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = BlueAccent)
            }
            error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(error.orEmpty(), color = Color(0xFFB91C1C), fontSize = 15.sp)
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                val product = events.mapNotNull { it.product }.firstOrNull() ?: TraceProduct()
                val summaryKey = response?.key?.takeIf { it.isNotBlank() }
                    ?: lookupQuery.displayKey()
                ProductSummaryCard(
                    lookupKey = summaryKey,
                    total = response?.total ?: events.size,
                    product = product,
                )
                Text(
                    "Supply chain timeline",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                )
                if (events.isEmpty()) {
                    Text("No events found for this key.", color = TextMuted, fontSize = 14.sp)
                } else {
                    events.forEachIndexed { index, ev ->
                        TimelineEventCard(
                            event = ev,
                            address = resolvedAddresses[index].orEmpty().ifBlank {
                                ev.geo_location.orEmpty().ifBlank { "—" }
                            },
                            isLast = index == events.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductSummaryCard(
    lookupKey: String,
    total: Int,
    product: TraceProduct,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(BlueBg, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = BlueAccent,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        product.traceProductName().ifBlank { "Product" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Text(
                        product.traceBrandName().ifBlank { "—" },
                        fontSize = 13.sp,
                        color = TextMuted,
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 14.dp), color = Color(0xFFE5E7EB))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("GTIN / Key", fontSize = 11.sp, color = TextMuted)
                    Text(lookupKey, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Events", fontSize = 11.sp, color = TextMuted)
                    Text("$total", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                }
            }
            product.traceSku()?.let { sku ->
                Spacer(Modifier.height(10.dp))
                Text("SKU: $sku", fontSize = 13.sp, color = TextSub)
            }
        }
    }
}

private val TextSub = Color(0xFF6B7280)

@Composable
private fun TimelineEventCard(
    event: BarcodeTraceEvent,
    address: String,
    isLast: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(24.dp)
                .fillMaxHeight()
                .padding(top = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(BlueAccent, CircleShape),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .weight(1f)
                        .padding(top = 4.dp)
                        .background(Color(0xFFE5E7EB)),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (isLast) 0.dp else 12.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        eventTypeTitle(event.event_type),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BlueBg,
                    ) {
                        Text(
                            statusChip(event),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BlueAccent,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    bizStepHuman(event.biz_step),
                    fontSize = 13.sp,
                    color = TextSub,
                )
                if (!isGenerationEvent(event)) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.Place,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(address, fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Default.PhoneIphone,
                        contentDescription = null,
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        event.device_type?.uppercase() ?: "—",
                        fontSize = 13.sp,
                        color = TextSub,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    formatEventTime(event.event_time.orEmpty()),
                    fontSize = 12.sp,
                    color = TextMuted,
                )
            }
        }
    }
}

private suspend fun resolveEventAddress(ev: BarcodeTraceEvent): String {
    if (isGenerationEvent(ev)) return ""
    val lat = ev.lat
    val lon = ev.lon
    if (lat != null && lon != null && (lat != 0.0 || lon != 0.0)) {
        return AppRepository.getLocationDetails(lat, lon).getOrNull()?.displayName
            ?: ev.geo_location.orEmpty().ifBlank { "Unknown" }
    }
    return ev.geo_location.orEmpty().ifBlank { "Unknown" }
}

private fun TraceProduct.traceProductName(): String =
    productName?.takeIf { it.isNotBlank() } ?: productNameSnake?.takeIf { it.isNotBlank() }.orEmpty()

private fun TraceProduct.traceBrandName(): String =
    brandName?.takeIf { it.isNotBlank() } ?: brandNameSnake?.takeIf { it.isNotBlank() }.orEmpty()

private fun TraceProduct.traceSku(): String? =
    sku?.takeIf { it.isNotBlank() } ?: skuUpper?.takeIf { it.isNotBlank() }

private fun normalizeIso(raw: String): String = when {
    raw.isBlank() -> raw
    raw.endsWith("Z", ignoreCase = true) -> raw
    raw.contains('T') && (raw.contains('+', ignoreCase = true) || Regex("""-\d{2}:\d{2}$""").containsMatchIn(raw)) -> raw
    raw.contains('T') -> "${raw}Z"
    raw.contains(' ') -> raw.replace(' ', 'T') + "Z"
    else -> "${raw}T00:00:00Z"
}

private fun formatEventTime(iso: String): String {
    if (iso.isBlank()) return "—"
    val instant = runCatching { Instant.parse(normalizeIso(iso)) }.getOrNull() ?: return iso
    val ldt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val month = ldt.month.name.lowercase().replaceFirstChar { it.uppercase() }
    val h24 = ldt.hour
    val amPm = if (h24 < 12) "AM" else "PM"
    val h12 = when {
        h24 == 0 -> 12
        h24 <= 12 -> h24
        else -> h24 - 12
    }
    val min = ldt.minute.toString().padStart(2, '0')
    return "$month ${ldt.dayOfMonth}, ${ldt.year} - $h12:$min $amPm"
}

private fun eventTypeTitle(type: String?): String = when (type?.uppercase()) {
    "AUTHENTICATE" -> "Product authentication"
    "SCAN" -> "Barcode scan"
    else -> type?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Event"
}

private fun isGenerationEvent(ev: BarcodeTraceEvent): Boolean {
    val biz = ev.biz_step?.lowercase().orEmpty()
    if (biz == "encoding" || biz.contains("encod")) return true
    val et = ev.event_type?.uppercase().orEmpty()
    if (et.contains("GENERATION") || et.contains("GENERATE")) return true
    return false
}

private fun statusChip(ev: BarcodeTraceEvent): String =
    if (isGenerationEvent(ev)) "GENERATED" else "SCANNED"

private fun bizStepHuman(step: String?): String {
    if (step.isNullOrBlank()) return "—"
    if (step.startsWith("urn:", ignoreCase = true)) return "Supply chain step"
    val s = step.lowercase()
    return when {
        s == "inspecting" || s.contains("inspect") -> "Inspected in the field"
        s == "encoding" || s.contains("encod") -> "Encoding"
        s.contains("receiv") -> "Receiving"
        else -> step.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
