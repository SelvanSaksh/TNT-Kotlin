package features.app

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import core.storage.SessionManager
import core.storage.getLocalStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import navigation.appscreen.Screens
import network.models.UserDetail
import theme.White

// ── Colors from Swift ────────────────────────────────────────────────────────
private val NavyDark     = Color(0xFF163C66)
private val NavyDeep     = Color(0xFF0F2A47)
private val IconBg       = Color(0xFFE8EEF5)
private val PageBg       = Color(0xFFF5F6FA)
private val TextPrimary  = Color(0xFF111827)
private val TextMuted    = Color(0xFF9CA3AF)
private val TextSub      = Color(0xFF374151)
private val GreenAccent  = Color(0xFF059669)
private val GreenBg      = Color(0xFFF0FDF4)
private val GreenBorder  = Color(0xFFBBF7D0)
private val GreenIcon    = Color(0xFFD1FAE5)

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
    val isGeneration: Boolean
)

@Composable
fun Home(onNavigate: (Screens) -> Unit) {

    val sessionManager = remember { SessionManager(getLocalStorage()) }
    val json = remember { Json { ignoreUnknownKeys = true } }
    val scope = rememberCoroutineScope()

    val userDetail = remember {
        sessionManager.getUserDetail()?.let {
            try { json.decodeFromString<UserDetail>(it) } catch (e: Exception) { null }
        }
    }
    val userName     = userDetail?.firstName ?: "User"
    val companyId    = userDetail?.companyId ?: 0
    val accessToken  = sessionManager.getAccessToken() ?: ""

    var triggerScan       by remember { mutableStateOf(false) }
    var showLogoutDialog  by remember { mutableStateOf(false) }
    var isLoading         by remember { mutableStateOf(false) }
    var totalScans        by remember { mutableStateOf(0) }
    var totalGenerations  by remember { mutableStateOf(0) }
    var weeklyData        by remember { mutableStateOf(List(7) { 0f }) }
    var recentItems       by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }

    // ── Fetch dashboard ───────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        scope.launch {
            isLoading = true
            try {
                val client = HttpClient {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
                val response: BarcodeLogsResponse = client.get(
                    "https://api.tnt.sakksh.com/companies/barcode/logs"
                ) {
                    parameter("company_id", companyId)
                    parameter("page", 1)
                    parameter("limit", 20)
                    header("Authorization", "Bearer $accessToken")
                }.body()
                client.close()

                var scans = 0; var gens = 0
                val dailyCounts = mutableMapOf<Int, Int>()
                val mapped = response.data.map { log ->
                    if (log.type == 0) scans++ else gens++
                    val weekday = getWeekday(log.created_at)
                    dailyCounts[weekday] = (dailyCounts[weekday] ?: 0) + 1
                    HistoryItem(
                        fileName   = log.details?.barcode ?: "Unknown",
                        action     = log.details?.status ?: "Processed",
                        timeAgo    = formatTimeAgo(log.created_at),
                        isGeneration = log.type != 0
                    )
                }

                val chartRaw = (1..7).map { (dailyCounts[it] ?: 0).toFloat() }
                val maxVal = chartRaw.maxOrNull()?.takeIf { it > 0 } ?: 1f

                totalScans       = scans
                totalGenerations = gens
                weeklyData       = chartRaw.map { it / maxVal }
                recentItems      = mapped.take(5)
            } catch (e: Exception) {
                println("Dashboard error: ${e.message}")
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
                StatsRow(isLoading, totalScans, totalGenerations)
                ActivityOverview(isLoading, totalScans + totalGenerations, weeklyData)
                RecentHistory(isLoading, recentItems)
                UpgradeBanner()
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
                        showLogoutDialog = false
                        onNavigate(Screens.LoginScreen)
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
@Composable
fun ActivityOverview(isLoading: Boolean, totalActions: Int, weeklyData: List<Float>) {
    val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
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
                    Text("Past 7 days performance", fontSize = 12.sp, color = TextMuted)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(if (isLoading) "..." else "$totalActions",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NavyDark)
                    Text("Total actions", fontSize = 11.sp, color = TextMuted)
                }
            }

            if (isLoading) {
                SkeletonChart()
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    days.forEachIndexed { i, day ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((80 * (weeklyData.getOrElse(i) { 0f })).coerceAtLeast(10f).dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NavyDark)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(day, fontSize = 9.sp, color = TextMuted,
                                fontWeight = FontWeight.Medium)
                        }
                    }
                }
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
        modifier = Modifier.fillMaxWidth().height(100.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(0.6f, 0.3f, 0.8f, 0.4f, 0.7f, 0.2f, 0.5f).forEach { h ->
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(h)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Gray.copy(shimmer))
            )
        }
    }
}

// ── Recent History ────────────────────────────────────────────────────────────
@Composable
fun RecentHistory(isLoading: Boolean, items: List<HistoryItem>) {
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
                Text("See all", fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, color = NavyDark)
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
                    HistoryRow(item)
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
fun HistoryRow(item: HistoryItem) {
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
            Text(item.fileName, fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text("${item.action} • ${item.timeAgo}", fontSize = 12.sp, color = TextMuted)
        }

        Icon(Icons.Default.MoreVert, contentDescription = null,
            tint = TextMuted, modifier = Modifier.size(18.dp))
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

// ── Upgrade Banner ────────────────────────────────────────────────────────────
@Composable
fun UpgradeBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(GreenBg, RoundedCornerShape(16.dp))
            .border(1.dp, GreenBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(GreenIcon, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null,
                tint = GreenAccent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Upgrade to Pro", fontSize = 14.sp,
                fontWeight = FontWeight.Bold, color = GreenAccent)
            Text("Get unlimited generations and cloud sync.",
                fontSize = 12.sp, color = TextSub)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null,
            tint = GreenAccent, modifier = Modifier.size(16.dp))
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────
fun formatTimeAgo(dateString: String): String {
    // Platform-specific date parsing — stub returns raw string
    // On Android/iOS actual implementation via expect/actual
    return "recently"
}

fun getWeekday(dateString: String): Int {
    // Returns 1–7 (Sun–Sat), stub returns 1
    return 1
}