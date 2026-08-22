package features.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.storage.LocalScanRecord
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import resolver.parseDigitalLink
import resolver.stripAuthAis97And98

private val BlueAccent = Color(0xFF163C66)
private val PageBg = Color(0xFFF5F6FA)
private val TextPrimary = Color(0xFF111827)
private val TextMuted = Color(0xFF9CA3AF)
private val IconBg = Color(0xFFEFF6FF)
private val ChipBg = Color(0xFFF3F4F6)
private val DigitalLinkInText = Regex(
    """https?://[^\s"'<>\\]*dl\.ratifye\.ai[^\s"'<>\\]*""",
    RegexOption.IGNORE_CASE,
)

@Composable
fun GuestScanHistory() {
    val sessionManager = remember { SessionManager(getLocalStorage()) }
    var records by remember { mutableStateOf(sessionManager.getGuestScanHistory()) }

    LaunchedEffect(records) {
        println("GUEST_HISTORY: screen records=${records.size}")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Scan History",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (records.isNotEmpty()) {
                TextButton(onClick = {
                    sessionManager.clearGuestScanHistory()
                    records = emptyList()
                }) {
                    Text("Clear", color = BlueAccent, fontSize = 13.sp)
                }
            }
        }

        if (records.isEmpty()) {
            GuestHistoryEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(records, key = { it.timestamp }) { record ->
                    GuestHistoryRow(record)
                }
            }
        }
    }
}

@Composable
private fun GuestHistoryRow(record: LocalScanRecord) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(IconBg, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = BlueAccent,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                val display = remember(record) {
                    val resolved = historyDisplay(record)
                    println(
                        "GUEST_HISTORY: row type='${resolved.type}' gtin='${resolved.gtin}' " +
                            "batch='${resolved.batch}' serial='${resolved.serial}' " +
                            "title='${resolved.title.take(160)}' storedType='${record.barcodeType}' " +
                            "storedData='${record.barcodeData.take(160)}'",
                    )
                    resolved
                }
                Text(
                    text = display.title.ifBlank { "Unknown barcode" },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = display.type.ifBlank { "Barcode" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = BlueAccent,
                        modifier = Modifier
                            .background(IconBg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Text(
                        text = formatTimeAgo(record.timestamp),
                        fontSize = 12.sp,
                        color = TextMuted,
                    )
                }

                val fields = buildList {
                    display.gtin.takeIf { it.isNotBlank() }?.let { add("GTIN" to it) }
                    display.batch.takeIf { it.isNotBlank() }?.let { add("Batch" to it) }
                    display.serial.takeIf { it.isNotBlank() }?.let { add("Serial" to it) }
                }
                if (fields.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        fields.forEach { (label, value) ->
                            ScanFieldChip(label = label, value = value)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanFieldChip(label: String, value: String) {
    Row(
        modifier = Modifier
            .background(ChipBg, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun GuestHistoryEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(IconBg, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint = Color(0xFFBFDBFE),
                    modifier = Modifier.size(36.dp),
                )
            }
            Text(
                text = "No scans yet",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF374151),
            )
            Text(
                text = "Scans you make as a guest will appear here",
                fontSize = 14.sp,
                color = TextMuted,
            )
        }
    }
}

private data class HistoryDisplay(
    val title: String,
    val type: String,
    val gtin: String,
    val batch: String,
    val serial: String,
)

private fun historyDisplay(record: LocalScanRecord): HistoryDisplay {
    val link = DigitalLinkInText.find(record.barcodeData)?.value
    val source = link ?: record.barcodeData
    val parsed = runCatching {
        if (source.contains("dl.ratifye.ai", ignoreCase = true)) parseDigitalLink(source) else null
    }.getOrNull()
    val gtin = record.gtin.ifBlank { parsed?.gtin.orEmpty() }.ifBlank {
        Regex("""/01/([^/?#]+)""").find(source)?.groupValues?.getOrNull(1).orEmpty()
    }
    val batch = record.batch.ifBlank {
        parsed?.data?.identifiers?.firstOrNull { it.code == "10" }?.value.orEmpty()
    }.ifBlank {
        Regex("""/10/([^/?#]+)""").find(source)?.groupValues?.getOrNull(1).orEmpty()
    }
    val serial = record.serial.ifBlank {
        parsed?.data?.identifiers?.firstOrNull { it.code == "21" }?.value.orEmpty()
    }.ifBlank {
        Regex("""/21/([^/?#]+)""").find(source)?.groupValues?.getOrNull(1).orEmpty()
    }
    val looksLikeJson = source.trimStart().startsWith("[") || source.trimStart().startsWith("{")
    val title = stripAuthAis97And98(
        when {
            parsed != null -> parsed.cleanUrl
            link != null -> link
            !looksLikeJson -> source
            gtin.isNotBlank() -> gtin
            else -> source
        },
    )
    val type = when {
        record.barcodeType.isNotBlank() &&
            !(record.barcodeType.equals("CODE128", ignoreCase = true) &&
                title.startsWith("http", ignoreCase = true)) -> record.barcodeType
        title.startsWith("http", ignoreCase = true) -> "QR"
        else -> record.barcodeType
    }
    return HistoryDisplay(title = title, type = type, gtin = gtin, batch = batch, serial = serial)
}

private fun formatTimeAgo(epochMillis: Long): String {
    val instant = Instant.fromEpochMilliseconds(epochMillis)
    val now = Clock.System.now()
    val diffSeconds = (now - instant).inWholeSeconds

    return when {
        diffSeconds < 60 -> "just now"
        diffSeconds < 3600 -> {
            val mins = diffSeconds / 60
            if (mins == 1L) "1 min ago" else "$mins mins ago"
        }
        diffSeconds < 86400 -> {
            val hrs = diffSeconds / 3600
            if (hrs == 1L) "1 hr ago" else "$hrs hrs ago"
        }
        diffSeconds < 604800 -> {
            val days = diffSeconds / 86400
            if (days == 1L) "1 day ago" else "$days days ago"
        }
        else -> instant.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    }
}