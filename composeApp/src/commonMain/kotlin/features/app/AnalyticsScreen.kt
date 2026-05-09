package features.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import network.ApiClient
import network.models.UserDetail
import theme.White

private val PageBg = Color(0xFFF5F6FA)
private val TextPrimary = Color(0xFF111827)
private val TextMuted = Color(0xFF9CA3AF)
private val ScanLine = Color(0xFF2563EB)
private val GenLine = Color(0xFF059669)
private val ChartTrack = Color(0xFFF3F4F6)

@Composable
fun AnalyticsScreen() {
    val sessionManager = remember { SessionManager(getLocalStorage()) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    val userDetail = remember {
        sessionManager.getUserDetail()?.let {
            try {
                json.decodeFromString(UserDetail.serializer(), it)
            } catch (_: Exception) {
                null
            }
        }
    }
    val companyId = userDetail?.companyId ?: 0

    var isLoading by remember { mutableStateOf(true) }
    var totalScans by remember { mutableStateOf(0) }
    var totalGens by remember { mutableStateOf(0) }
    var scanWeek by remember { mutableStateOf(List(7) { 0 }) }
    var genWeek by remember { mutableStateOf(List(7) { 0 }) }

    LaunchedEffect(companyId) {
        isLoading = true
        try {
            if (companyId <= 0) {
                totalScans = 0
                totalGens = 0
                scanWeek = List(7) { 0 }
                genWeek = List(7) { 0 }
                return@LaunchedEffect
            }
            val summary = ApiClient.get<JsonObject>(
                endpoint = "/companies/barcode/dashboard-summary?company_id=$companyId&recent_limit=50"
            )
            val countFields = summary.optObj("counts")
            totalScans = countFields.optInt("scanning")
            totalGens = countFields.optInt("generation")
            val recent = summary.optObj("recent")
            val scanLogs = recent.optArray("scanning")
            val genLogs = recent.optArray("generation")
            scanWeek = weekActivityCounts(scanLogs)
            genWeek = weekActivityCounts(genLogs)
        } catch (e: Exception) {
            println("Analytics error: ${e.message}")
            totalScans = 0
            totalGens = 0
            scanWeek = List(7) { 0 }
            genWeek = List(7) { 0 }
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Analytics",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                "Scans, generations, and weekly trends",
                fontSize = 13.sp,
                color = TextMuted
            )

            if (isLoading) {
                AnalyticsSkeleton()
            } else {
                SummaryStatsCard(totalScans, totalGens)
                MixRatioCard(totalScans, totalGens)
                WeeklyGroupedBarsCard(scanWeek, genWeek)
                TrendLineChartCard(scanWeek, genWeek)
            }
        }
    }
}

@Composable
private fun SummaryStatsCard(scans: Int, generations: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MiniStat(
            label = "TOTAL SCANS",
            value = "$scans",
            tint = ScanLine,
            modifier = Modifier.weight(1f)
        )
        MiniStat(
            label = "GENERATIONS",
            value = "$generations",
            tint = GenLine,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MiniStat(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = tint)
        }
    }
}

@Composable
private fun MixRatioCard(scans: Int, generations: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Activities", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            if (scans == 0 && generations == 0) {
                Text(
                    "No activity recorded yet.",
                    fontSize = 13.sp,
                    color = TextMuted
                )
                return@Column
            }
            val total = scans + generations
            val scanFrac = scans.toFloat() / total
            val genFrac = generations.toFloat() / total
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ChartTrack)
            ) {
                Box(
                    modifier = Modifier
                        .weight(scanFrac.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(ScanLine, ScanLine.copy(alpha = 0.85f))))
                )
                Box(
                    modifier = Modifier
                        .weight(genFrac.coerceAtLeast(0.001f))
                        .fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(GenLine, GenLine.copy(alpha = 0.85f))))
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Scans ${(scanFrac * 100).toInt()}%", fontSize = 12.sp, color = ScanLine)
                Text("Generations ${(genFrac * 100).toInt()}%", fontSize = 12.sp, color = GenLine)
            }
        }
    }
}

@Composable
private fun WeeklyGroupedBarsCard(scanWeek: List<Int>, genWeek: List<Int>) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val maxVal = listOf(
        scanWeek.maxOrNull() ?: 0,
        genWeek.maxOrNull() ?: 0
    ).max().coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Weekly volume", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            Text("Scans vs generations by weekday", fontSize = 12.sp, color = TextMuted)
            HorizontalDivider(color = ChartTrack)
            val barMax = 120.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barMax + 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                days.indices.forEach { i ->
                    val s = scanWeek.getOrElse(i) { 0 }
                    val g = genWeek.getOrElse(i) { 0 }
                    val hS = barMax * (s.toFloat() / maxVal)
                    val hG = barMax * (g.toFloat() / maxVal)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Row(
                            modifier = Modifier.height(barMax),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(maxOf(4.dp, hS))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(ScanLine.copy(alpha = 0.9f))
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(maxOf(4.dp, hG))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(GenLine.copy(alpha = 0.9f))
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(days[i], fontSize = 10.sp, color = TextMuted)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(ScanLine, "Scans")
                Spacer(Modifier.size(16.dp))
                LegendDot(GenLine, "Generations")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, fontSize = 12.sp, color = TextMuted)
    }
}

@Composable
private fun TrendLineChartCard(scanWeek: List<Int>, genWeek: List<Int>) {
    val days = listOf("M", "T", "W", "T", "F", "S", "S")
    val maxVal = listOf(
        scanWeek.maxOrNull() ?: 0,
        genWeek.maxOrNull() ?: 0
    ).max().coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("7-day trend", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChartTrack)
                    .padding(12.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val n = 7
                    fun points(values: List<Int>): List<Offset> {
                        return values.indices.map { i ->
                            val x = if (n <= 1) w / 2f else (i / (n - 1f)) * w
                            val v = values.getOrElse(i) { 0 }
                            val y = h - (v / maxVal.toFloat()) * h * 0.85f - h * 0.05f
                            Offset(x, y.coerceIn(0f, h))
                        }
                    }
                    val pathS = Path().apply {
                        val pts = points(scanWeek)
                        if (pts.isNotEmpty()) {
                            moveTo(pts.first().x, pts.first().y)
                            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                        }
                    }
                    val pathG = Path().apply {
                        val pts = points(genWeek)
                        if (pts.isNotEmpty()) {
                            moveTo(pts.first().x, pts.first().y)
                            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
                        }
                    }
                    drawPath(
                        pathS,
                        color = ScanLine,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                    drawPath(
                        pathG,
                        color = GenLine,
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                days.forEach { Text(it, fontSize = 10.sp, color = TextMuted) }
            }
        }
    }
}

@Composable
private fun AnalyticsSkeleton() {
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.15f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "a"
    )
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(3) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Gray.copy(shimmer))
            )
        }
    }
}

private fun weekActivityCounts(logs: List<JsonObject>): List<Int> {
    val dayCounts = MutableList(7) { 0 }
    logs.forEach { log ->
        val dayIndex = auditDayIndex(log.optString("created_at", log.optString("event_time")))
        if (dayIndex in 0..6) dayCounts[dayIndex]++
    }
    return dayCounts
}

private fun auditDayIndex(dateString: String): Int {
    val date = parseAnalyticsInstant(dateString)
        ?.toLocalDateTime(TimeZone.currentSystemDefault())
        ?.date ?: return 0
    return when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> 0
        DayOfWeek.TUESDAY -> 1
        DayOfWeek.WEDNESDAY -> 2
        DayOfWeek.THURSDAY -> 3
        DayOfWeek.FRIDAY -> 4
        DayOfWeek.SATURDAY -> 5
        DayOfWeek.SUNDAY -> 6
    }
}

private fun parseAnalyticsInstant(raw: String): Instant? {
    if (raw.isBlank()) return null
    val normalized = when {
        raw.contains("T") && (raw.endsWith("Z") || raw.contains("+")) -> raw
        raw.contains("T") -> "${raw}Z"
        raw.contains(" ") -> raw.replace(" ", "T") + "Z"
        else -> "${raw}T00:00:00Z"
    }
    return runCatching { Instant.parse(normalized) }.getOrNull()
}

private fun JsonObject.optObj(key: String): JsonObject =
    this[key]?.jsonObject ?: JsonObject(emptyMap())

private fun JsonObject.optArray(key: String): List<JsonObject> =
    (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

private fun JsonObject.optInt(key: String): Int =
    this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0

private fun JsonObject.optString(key: String, fallback: String = ""): String =
    this[key]?.jsonPrimitive?.contentOrNull ?: fallback
