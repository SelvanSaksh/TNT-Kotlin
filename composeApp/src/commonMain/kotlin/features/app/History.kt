package features.app.history

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.datetime.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import utils.openUrl
// ── Colors ────────────────────────────────────────────────────────────────────
private val PageBg        = Color(0xFFF5F6FA)
private val TextPrimary   = Color(0xFF111827)
private val TextMuted     = Color(0xFF9CA3AF)
private val TextSub       = Color(0xFF6B7280)
private val BlueAccent = Color(0xFF163C66)
private val BlueBg        = Color(0xFFEFF6FF)
private val BorderColor   = Color(0xFFE5E7EB)
private val IconBg        = Color(0xFFEFF6FF)

// ── Models ────────────────────────────────────────────────────────────────────
@Serializable
data class HistoryLog(
    val type: Int = 0,
    val details: HistoryDetails? = null,
    val created_at: String = ""
)

@Serializable
data class HistoryDetails(
    val barcode: String? = null,
    val status: String? = null
)

@Serializable
data class HistoryResponse(
    val data: List<HistoryLog> = emptyList()
)

data class HistoryItemUi(
    val id: String,
    val fileName: String,
    val action: String,
    val timeAgo: String,
    val isGeneration: Boolean
)

enum class HistoryTab { Scans, Generations }

// ── History Screen ────────────────────────────────────────────────────────────
@Composable
fun History() {
    val sessionManager = remember { SessionManager(getLocalStorage()) }
    val accessToken    = sessionManager.getAccessToken() ?: ""
    val scope          = rememberCoroutineScope()

    var selectedTab  by remember { mutableStateOf(HistoryTab.Scans) }
    var searchText   by remember { mutableStateOf("") }
    var isLoading    by remember { mutableStateOf(false) }
    var items        by remember { mutableStateOf<List<HistoryItemUi>>(emptyList()) }

    val filteredItems = remember(items, searchText) {
        if (searchText.isBlank()) items
        else items.filter {
            it.fileName.contains(searchText, ignoreCase = true) ||
                    it.action.contains(searchText, ignoreCase = true)
        }
    }



    fun fetchHistory() {
        scope.launch {
            isLoading = true
            try {
                val type = if (selectedTab == HistoryTab.Scans) 0 else 1
                val client = HttpClient {
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
                val response: HistoryResponse = client.get(
                    "https://api.tnt.sakksh.com/companies/barcode/logs"
                ) {
                    parameter("type", type)
                    parameter("page", 1)
                    parameter("limit", 10)
                    header("Authorization", "Bearer $accessToken")
                }.body()
                client.close()

                items = response.data.mapIndexedNotNull { index, log ->
                    val barcode = log.details?.barcode ?: return@mapIndexedNotNull null
                    val status  = log.details.status  ?: return@mapIndexedNotNull null
                    HistoryItemUi(
                        id           = index.toString(),
                        fileName     = barcode,
                        action       = status,
                        timeAgo      = log.created_at,
                        isGeneration = log.type != 0
                    )
                }
            } catch (e: Exception) {
                println("History error: ${e.message}")
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit)        { fetchHistory() }
    LaunchedEffect(selectedTab) { fetchHistory() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
    ) {
        // Header
        HistoryHeader()

        // Tab Bar
        HistoryTabBar(
            selectedTab = selectedTab,
            onTabChange = { selectedTab = it }
        )

        // Search Bar
        HistorySearchBar(
            searchText  = searchText,
            onTextChange = { searchText = it }
        )

        // Content
        when {
            isLoading -> HistorySkeletonList()
            filteredItems.isEmpty() -> HistoryEmptyState(selectedTab, searchText)
            else -> HistoryList(filteredItems)
        }
    }
}

// ── Header ────────────────────────────────────────────────────────────────────
@Composable
fun HistoryHeader() {
    Surface(color = Color.White, shadowElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = "History",
                fontSize   = 26.sp,
                fontWeight = FontWeight.Bold,
                color      = TextPrimary
            )
            Box(
                modifier = Modifier
                    .background(BlueBg, RoundedCornerShape(20.dp))
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text       = "Upgrade",
                    fontSize   = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color      = BlueAccent
                )
            }
        }
    }
}

// ── Tab Bar ───────────────────────────────────────────────────────────────────
@Composable
fun HistoryTabBar(
    selectedTab: HistoryTab,
    onTabChange: (HistoryTab) -> Unit
) {
    Surface(color = Color.White) {
        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                HistoryTabButton(
                    title      = "Scans",
                    isSelected = selectedTab == HistoryTab.Scans,
                    onClick    = { onTabChange(HistoryTab.Scans) },
                    modifier   = Modifier.weight(1f)
                )
                HistoryTabButton(
                    title      = "Generations",
                    isSelected = selectedTab == HistoryTab.Generations,
                    onClick    = { onTabChange(HistoryTab.Generations) },
                    modifier   = Modifier.weight(1f)
                )
            }
            HorizontalDivider(color = BorderColor, thickness = 1.dp)
        }
    }
}

@Composable
fun HistoryTabButton(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text       = title,
            fontSize   = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color      = if (isSelected) BlueAccent else TextMuted,
            modifier   = Modifier.padding(vertical = 14.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .background(
                    if (isSelected) BlueAccent else Color.Transparent,
                    RoundedCornerShape(2.dp)
                )
        )
    }
}

// ── Search Bar ────────────────────────────────────────────────────────────────
@Composable
fun HistorySearchBar(
    searchText: String,
    onTextChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PageBg)
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        OutlinedTextField(
            value         = searchText,
            onValueChange = onTextChange,
            modifier      = Modifier.fillMaxWidth(),
            placeholder   = {
                Text("Search your history...", color = TextMuted, fontSize = 15.sp)
            },
            leadingIcon   = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint     = TextSub,
                    modifier = Modifier.size(20.dp)
                )
            },
            singleLine    = true,
            shape         = RoundedCornerShape(12.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderColor,
                focusedBorderColor   = BlueAccent,
                unfocusedContainerColor = Color.White,
                focusedContainerColor   = Color.White,
                cursorColor          = BlueAccent
            )
        )
    }
}

// ── List ──────────────────────────────────────────────────────────────────────
@Composable
fun HistoryList(items: List<HistoryItemUi>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 0.dp, bottom = 16.dp)
    ) {
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    HistoryRowItem(item)
                    if (index < items.lastIndex) {
                        HorizontalDivider(
                            modifier  = Modifier.padding(start = 76.dp),
                            color     = BorderColor,
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }

        // End of history
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(Color(0xFFF3F4F6), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.History,
                    contentDescription = null,
                    tint     = Color(0xFFD1D5DB),
                    modifier = Modifier.size(28.dp)
                )
            }
            Text("End of history", fontSize = 14.sp, color = TextMuted)
        }
    }
}

// ── Row Item ──────────────────────────────────────────────────────────────────
@Composable
fun HistoryRowItem(item: HistoryItemUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icon
        Box(
            modifier = Modifier
                .size(50.dp)
                .background(IconBg, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (item.isGeneration) Icons.Default.QrCode
                else Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint     = BlueAccent,
                modifier = Modifier.size(22.dp)
            )
        }

        // Text info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val isUrl = item.fileName.startsWith("http")

            Text(
                text = item.fileName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isUrl) Color(0xFF2563EB) else TextPrimary,
                maxLines = 1,
                modifier = Modifier.clickable(enabled = isUrl) {
                    openUrl(item.fileName)
                }
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (item.action == "Imported")
                        Icons.Default.Download else Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint     = TextMuted,
                    modifier = Modifier.size(12.dp)
                )
                Text(
                    text     = "${item.action} ${item.timeAgo}",
                    fontSize = 12.sp,
                    color    = TextMuted
                )
            }
        }

        // More menu
        var showMenu by remember { mutableStateOf(false) }
        Box {
            IconButton(
                onClick  = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint     = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
            DropdownMenu(
                expanded        = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text    = { Text("Share") },
                    onClick = { showMenu = false },
                    leadingIcon = {
                        Icon(Icons.Default.Share, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                    }
                )
                DropdownMenuItem(
                    text    = { Text("Rename") },
                    onClick = { showMenu = false },
                    leadingIcon = {
                        Icon(Icons.Default.Edit, contentDescription = null,
                            modifier = Modifier.size(18.dp))
                    }
                )
                DropdownMenuItem(
                    text    = { Text("Delete", color = Color.Red) },
                    onClick = { showMenu = false },
                    leadingIcon = {
                        Icon(Icons.Default.Delete, contentDescription = null,
                            tint = Color.Red, modifier = Modifier.size(18.dp))
                    }
                )
            }
        }
    }
}

// ── Empty State ───────────────────────────────────────────────────────────────
@Composable
fun HistoryEmptyState(selectedTab: HistoryTab, searchText: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(PageBg),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(BlueBg, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selectedTab == HistoryTab.Scans)
                        Icons.Default.QrCodeScanner else Icons.Default.QrCode,
                    contentDescription = null,
                    tint     = Color(0xFFBFDBFE),
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                text       = if (searchText.isEmpty())
                    "No ${if (selectedTab == HistoryTab.Scans) "scans" else "generations"} yet"
                else "No results for \"$searchText\"",
                fontSize   = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color      = Color(0xFF374151)
            )
            Text(
                text  = if (searchText.isEmpty())
                    "Your ${if (selectedTab == HistoryTab.Scans) "scan" else "generation"} history will appear here"
                else "Try a different search term",
                fontSize = 14.sp,
                color    = TextMuted
            )
        }
    }
}


// ── Skeleton ──────────────────────────────────────────────────────────────────
@Composable
fun HistorySkeletonList() {
    val shimmer by rememberInfiniteTransition(label = "shimmer").animateFloat(
        initialValue   = 0.1f,
        targetValue    = 0.3f,
        animationSpec  = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label          = "alpha"
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .background(Color.Gray.copy(shimmer), RoundedCornerShape(12.dp))
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .background(Color.Gray.copy(shimmer), RoundedCornerShape(4.dp))
                    )
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(10.dp)
                            .background(Color.Gray.copy(shimmer), RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}