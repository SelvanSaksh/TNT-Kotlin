package features.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.models.BarcodeLookupQuery
import core.session.WarehouseAccess
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import features.app.warehouse.WarehouseHomeSection
import features.app.warehouse.WarehouseRoute
import features.app.warehouse.wms.PackingWmsEntry
import features.app.warehouse.wms.PickingWmsEntry
import features.app.warehouse.wms.ReceivingWmsRoot
import navigation.appscreen.Screens
import network.ApiClient
import network.models.UserDetail
import theme.White
import utils.openUrl
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

// ── Colors from Swift ────────────────────────────────────────────────────────
private val NavyDark     = Color(0xFF163C66)
private val NavyDeep     = Color(0xFF0F2A47)
private val IconBg       = Color(0xFFE8EEF5)
private val PageBg       = Color(0xFFF5F6FA)
private val TextPrimary  = Color(0xFF111827)
private val TextMuted    = Color(0xFF9CA3AF)
private val TextSub      = Color(0xFF374151)
// ── API Models ───────────────────────────────────────────────────────────────
@Serializable
data class BarcodeLog(
    val type: Int = 0,
    val details: BarcodeDetails? = null,
    val created_at: String = ""
)

@Serializable
data class BarcodeDetails(
    val barcode: String? = null,
    val status: String? = null
)

@Serializable
data class BarcodeLogsResponse(
    val data: List<BarcodeLog> = emptyList()
)

data class HistoryItem(
    val fileName: String,
    val action: String,
    val timeAgo: String,
    val isGeneration: Boolean,
    val lookupQuery: BarcodeLookupQuery = BarcodeLookupQuery(),
)

@Composable
fun Home(
    onNavigate: (Screens) -> Unit,
    onHistoryClick: () -> Unit = {},
    onTrackTrace: (BarcodeLookupQuery) -> Unit = {},
) {

    val sessionManager = remember { SessionManager(getLocalStorage()) }
    val warehouseAccess = remember { WarehouseAccess(sessionManager) }
    val warehouseModules = remember(warehouseAccess) { warehouseAccess.homeWarehouseModules() }
    val json = remember { Json { ignoreUnknownKeys = true } }
    val scope = rememberCoroutineScope()

    val userDetail = remember {
        sessionManager.getUserDetail()?.let {
            try { json.decodeFromString<UserDetail>(it) } catch (e: Exception) { null }
        }
    }
    val userName     = userDetail?.firstName ?: "User"
    val companyId    = userDetail?.companyId ?: 0

    var triggerScan       by remember { mutableStateOf(false) }
    var showLogoutDialog  by remember { mutableStateOf(false) }
    var isLoading         by remember { mutableStateOf(false) }
    var totalScans        by remember { mutableStateOf(0) }
    var totalGenerations  by remember { mutableStateOf(0) }
    var weeklyData        by remember { mutableStateOf(List(7) { 0f }) }
    var weeklyCounts      by remember { mutableStateOf(List(7) { 0 }) }
    var recentItems       by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }

    // ── Fetch dashboard ───────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        val rawSubscription = sessionManager.getSubscriptionData().orEmpty()
        val prettySubscription = if (rawSubscription.isBlank()) {
            "{}"
        } else {
            runCatching {
                val element = Json.parseToJsonElement(rawSubscription)
                Json { prettyPrint = true; prettyPrintIndent = "  " }
                    .encodeToString(JsonElement.serializer(), element)
            }.getOrElse { rawSubscription }
        }
        println("SUBSCRIPTION_LOG_JSON:\n$prettySubscription")

        val assignedLocations = userDetail?.locations.orEmpty()
        println("LOCATION_LOG: assigned locations count=${assignedLocations.size}")
        assignedLocations.forEachIndexed { index, assigned ->
            println(
                "LOCATION_LOG: assigned[$index] locationId=${assigned.locationId} assignmentType=${assigned.assignmentType.orEmpty()}"
            )
        }

        val rawLocationDetails = sessionManager.getLocationDetails().orEmpty()
        val prettyLocationDetails = if (rawLocationDetails.isBlank()) {
            "[]"
        } else {
            runCatching {
                val element = Json.parseToJsonElement(rawLocationDetails)
                Json { prettyPrint = true; prettyPrintIndent = "  " }
                    .encodeToString(JsonElement.serializer(), element)
            }.getOrElse { rawLocationDetails }
        }
        println("LOCATION_DETAILS_LOG_JSON:\n$prettyLocationDetails")

        runCatching {
            val locationDetails = Json.parseToJsonElement(rawLocationDetails).jsonArray
            locationDetails.forEachIndexed { index, element ->
                val item = element.jsonObject
                val location = item.optObj("location")
                println(
                    "LOCATION_LOG: detail[$index] " +
                        "locationId=${item.optInt("locationId")} " +
                        "assignmentType=${item.optString("assignmentType")} " +
                        "name=${location.optString("locn_name")} " +
                        "city=${location.optString("locn_city")} " +
                        "sgln=${location.optString("locn_sgln")} " +
                        "lat=${location.optString("lat")} " +
                        "long=${location.optString("long")}"
                )
            }
        }.onFailure {
            if (rawLocationDetails.isNotBlank()) {
                println("LOCATION_LOG: failed to parse stored location_details: ${it.message}")
            }
        }

        scope.launch {
            isLoading = true
            try {
                if (companyId <= 0) {
                    totalScans = 0
                    totalGenerations = 0
                    weeklyData = List(7) { 0f }
                    weeklyCounts = List(7) { 0 }
                    recentItems = emptyList()
                    return@launch
                }

                val summary = ApiClient.get<JsonObject>(
                    endpoint = "/companies/barcode/dashboard-summary?company_id=$companyId&recent_limit=15"
                )

                val countFields = summary.optObj("counts")
                val scanningCount = countFields.optInt("scanning")
                val generationCount = countFields.optInt("generation")

                val recent = summary.optObj("recent")
                val scanLogs = recent.optArray("scanning")
                val generationLogs = recent.optArray("generation")
                val mergedLogs = (scanLogs.map { it to false } + generationLogs.map { it to true })
                    .sortedByDescending { parseAuditInstant(it.first.optString("created_at", it.first.optString("event_time"))) }

                val topRecent = mergedLogs.take(5).map { (log, isGeneration) ->
                    HistoryItem(
                        fileName = log.barcodeLogDisplayTitle(),
                        action = log.barcodeLogDisplayAction(),
                        timeAgo = formatTimeAgo(log.optString("created_at", log.optString("event_time"))),
                        isGeneration = isGeneration,
                        lookupQuery = log.barcodeLogLookupParams(),
                    )
                }

                val chartLogs = scanLogs + generationLogs
                val counts = weekActivityCounts(chartLogs)

                totalScans = scanningCount
                totalGenerations = generationCount
                weeklyCounts = counts
                weeklyData = normalizedWeekBarsFromCounts(counts)
                recentItems = topRecent
            } catch (e: Exception) {
                println("Dashboard error: ${e.message}")
                totalScans = 0
                totalGenerations = 0
                weeklyData = List(7) { 0f }
                weeklyCounts = List(7) { 0 }
                recentItems = emptyList()
            }
            isLoading = false
        }
    }

    HomeScanButton(
        onNavigate       = onNavigate,
        shouldTriggerScan = triggerScan,
        onScanTriggered  = { triggerScan = false }
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────
            HomeHeader(
                userName           = userName,
                onLogout           = { showLogoutDialog = true },
                onNotification     = { }
            )

            // ── Scrollable body ───────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HeroBanner(onNavigate = onNavigate)
                WarehouseHomeSection(
                    modules = warehouseModules,
                    onModuleClick = { route ->
                        val screen = when (route) {
                            WarehouseRoute.Picking -> Screens.PickingScreen
                            WarehouseRoute.Packing -> Screens.PackingScreen
                            WarehouseRoute.Receiving -> Screens.ReceivingScreen
                        }
                        onNavigate(screen)
                    },
                )
                StatsRow(isLoading, totalScans, totalGenerations)
                ActivityOverview(
                    isLoading = isLoading,
                    totalActions = totalScans + totalGenerations,
                    weeklyData = weeklyData,
                    weeklyCounts = weeklyCounts
                )
                RecentHistory(
                    isLoading = isLoading,
                    items = recentItems,
                    onSeeAllClick = onHistoryClick,
                    onTrackTrace = onTrackTrace,
                )
            }
        }

        // ── Logout dialog ─────────────────────────────────────────────────
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title   = { Text("Logout", fontWeight = FontWeight.Bold) },
                text    = { Text("Are you sure you want to logout?") },
                confirmButton = {
                    TextButton(onClick = {
                        sessionManager.clearSession()
                        core.storage.GuestPromptState.shownThisLaunch = false
                        showLogoutDialog = false
                        onNavigate(Screens.GuestHomeScreen)
                    }) { Text("Logout", color = Color.Red) }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
                },
                containerColor = White,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────
@Composable
fun HomeHeader(
    userName: String,
    onLogout: () -> Unit,
    onNotification: () -> Unit
) {
    Surface(color = White, shadowElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Color(0xFFFDE8D8), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color(0xFFE8855A),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text("Welcome back,", fontSize = 13.sp, color = TextMuted)
                Text(userName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            // Notification
            IconButton(
                onClick = onNotification,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFF3F4F6), CircleShape)
            ) {
                Icon(Icons.Default.Notifications, contentDescription = "Notifications",
                    tint = TextSub, modifier = Modifier.size(18.dp))
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Logout
            IconButton(
                onClick = onLogout,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFF3F4F6), CircleShape)
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = "Logout",
                    tint = TextSub, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ── Hero Banner ───────────────────────────────────────────────────────────────
@Composable
fun HeroBanner(onNavigate: (Screens) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(NavyDark, NavyDeep)))
    ) {
        // Decorative circles
        Box(
            modifier = Modifier
                .size(140.dp)
                .offset(x = 240.dp, y = (-40).dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(90.dp)
                .offset(x = 270.dp, y = 60.dp)
                .background(Color.White.copy(alpha = 0.06f), CircleShape)
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Create New Barcode",
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Generate professional QR codes\nand barcodes in seconds.",
                fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f), lineHeight = 19.sp)
            Button(
                onClick = { onNavigate(Screens.GenerateCodeScreen) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null,
                    tint = Color(0xFF2563EB), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generate Now", color = Color(0xFF2563EB),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Stats Row ─────────────────────────────────────────────────────────────────
@Composable
fun StatsRow(isLoading: Boolean, totalScans: Int, totalGenerations: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isLoading) {
            SkeletonCard(Modifier.weight(1f))
            SkeletonCard(Modifier.weight(1f))
        } else {
            StatCard(
                icon       = Icons.Outlined.QrCodeScanner,
                title      = "TOTAL SCANS",
                value      = "$totalScans",
                modifier   = Modifier.weight(1f)
            )
            StatCard(
                icon       = Icons.Default.Settings,
                title      = "GENERATIONS",
                value      = "$totalGenerations",
                modifier   = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun StatCard(icon: ImageVector, title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(IconBg, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = NavyDark,
                    modifier = Modifier.size(18.dp))
            }
            Text(title, fontSize = 11.sp, color = TextMuted,
                fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
        }
    }
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "alpha"
    )
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).background(Color.Gray.copy(shimmer), RoundedCornerShape(6.dp)))
            Box(Modifier.width(80.dp).height(10.dp).background(Color.Gray.copy(shimmer), RoundedCornerShape(4.dp)))
            Box(Modifier.width(60.dp).height(20.dp).background(Color.Gray.copy(shimmer), RoundedCornerShape(4.dp)))
        }
    }
}

// ── Activity Overview ─────────────────────────────────────────────────────────
private val ChartTrackBg = Color(0xFFF3F4F6)
private val ChartBarIdle = Color(0xFFE5E7EB)
private val ChartAccent = Color(0xFF2563EB)

@Composable
fun ActivityOverview(
    isLoading: Boolean,
    totalActions: Int,
    weeklyData: List<Float>,
    weeklyCounts: List<Int>
) {
    val days = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Activity Overview", fontSize = 16.sp,
                        fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Scans & generations — past week", fontSize = 12.sp, color = TextMuted)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (isLoading) "..." else "$totalActions",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                    Text("Total actions", fontSize = 11.sp, color = TextMuted)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ChartTrackBg)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
            ) {
                if (isLoading) {
                    SkeletonChart()
                } else {
                    WeeklyActivityBarChart(
                        weeklyData = weeklyData,
                        weeklyCounts = weeklyCounts,
                        dayLabels = days
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyActivityBarChart(
    weeklyData: List<Float>,
    weeklyCounts: List<Int>,
    dayLabels: List<String>
) {
    val maxBarArea = 104.dp
    val labelHeight = 18.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(maxBarArea + labelHeight + 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        dayLabels.indices.forEach { i ->
            val fraction = weeklyData.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            val count = weeklyCounts.getOrElse(i) { 0 }
            val barBrush = if (count > 0) {
                Brush.verticalGradient(listOf(ChartAccent, NavyDark))
            } else {
                Brush.verticalGradient(listOf(ChartBarIdle, Color(0xFFD1D5DB)))
            }
            val barH = maxOf(4.dp, maxBarArea * fraction)

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (count > 0) {
                        Text(
                            "$count",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = NavyDark
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .height(maxBarArea)
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .height(barH)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(barBrush)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    dayLabels.getOrElse(i) { "" },
                    fontSize = 10.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SkeletonChart() {
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp + 18.dp + 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        repeat(7) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                Box(Modifier.height(16.dp))
                Box(
                    Modifier
                        .height(104.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    val h = listOf(0.6f, 0.3f, 0.8f, 0.4f, 0.7f, 0.2f, 0.5f)[it]
                    Box(
                        Modifier
                            .fillMaxWidth(0.78f)
                            .fillMaxHeight(h)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(Color.Gray.copy(shimmer))
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(20.dp).height(8.dp).background(Color.Gray.copy(shimmer), RoundedCornerShape(2.dp)))
            }
        }
    }
}

// ── Recent History ────────────────────────────────────────────────────────────
@Composable
fun RecentHistory(
    isLoading: Boolean,
    items: List<HistoryItem>,
    onSeeAllClick: () -> Unit = {},
    onTrackTrace: (BarcodeLookupQuery) -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Recent History", fontSize = 16.sp,
                    fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(
                    "See all",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NavyDark,
                    modifier = Modifier.clickable { onSeeAllClick() }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            if (isLoading) {
                repeat(3) { SkeletonHistoryRow() }
            } else if (items.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("No recent activity", fontSize = 14.sp, color = TextMuted)
                }
            } else {
                items.forEach { item ->
                    HistoryRow(item, onTrackTrace)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
fun HistoryRow(
    item: HistoryItem,
    onTrackTrace: (BarcodeLookupQuery) -> Unit = {},
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(IconBg, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (item.isGeneration) Icons.Default.QrCode else Icons.Default.QrCodeScanner,
                contentDescription = null, tint = NavyDark,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val openAsLink = item.fileName.trimStart().startsWith("http", ignoreCase = true)
            Text(
                item.fileName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (openAsLink) Color(0xFF2563EB) else TextPrimary,
                maxLines = 2,
                modifier = Modifier.clickable(enabled = openAsLink) {
                    openUrl(item.fileName.trim())
                }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccessTime,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(12.dp),
                )
                Text(item.timeAgo, fontSize = 12.sp, color = TextMuted)
            }
        }

        var showMenu by remember { mutableStateOf(false) }
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = TextMuted,
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                if (item.lookupQuery.canOpenTrackTrace()) {
                    DropdownMenuItem(
                        text = { Text("Track & Trace") },
                        onClick = {
                            showMenu = false
                            onTrackTrace(item.lookupQuery)
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Route,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SkeletonHistoryRow() {
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha"
    )
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).background(Color.Gray.copy(shimmer), RoundedCornerShape(10.dp)))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.width(120.dp).height(12.dp).background(Color.Gray.copy(shimmer), RoundedCornerShape(4.dp)))
            Box(Modifier.width(80.dp).height(10.dp).background(Color.Gray.copy(shimmer), RoundedCornerShape(4.dp)))
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
fun formatTimeAgo(dateString: String): String {
    val instant = parseAuditInstant(dateString) ?: return "recently"
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
        else -> "recently"
    }
}

fun getWeekday(dateString: String): Int {
    val date = parseAuditInstant(dateString)
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

private fun weekActivityCounts(logs: List<JsonObject>): List<Int> {
    val dayCounts = MutableList(7) { 0 }
    logs.forEach { log ->
        val dayIndex = getWeekday(log.optString("created_at", log.optString("event_time")))
        if (dayIndex in 0..6) dayCounts[dayIndex]++
    }
    return dayCounts
}

private fun normalizedWeekBarsFromCounts(dayCounts: List<Int>): List<Float> {
    val max = dayCounts.maxOrNull()?.takeIf { it > 0 } ?: 1
    return dayCounts.map { it.toFloat() / max }
}

private fun parseAuditInstant(raw: String): Instant? {
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