package features.app.assets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import components.AssetCardSkeleton
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import navigation.AppScreen
import network.models.Asset
import network.models.UserDetail
import network.repository.AssetRepository
import theme.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Assets(
    onNavigate: ((AppScreen) -> Unit)? = null
) {
    val sessionManager = remember { SessionManager(getLocalStorage()) }
    val json = remember { Json { ignoreUnknownKeys = true } }
    val scope = rememberCoroutineScope()
    
    // User details
    val userDetail = remember {
        sessionManager.getUserDetail()?.let { userDetailJson ->
            try {
                json.decodeFromString<UserDetail>(userDetailJson)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    val userName = userDetail?.firstName ?: "User"
    val companyId = userDetail?.companyId ?: 4
    
    // State management
    var showLogoutDialog by remember { mutableStateOf(false) }
    var assets by remember { mutableStateOf<List<Asset>>(emptyList()) }
    var filteredAssets by remember { mutableStateOf<List<Asset>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }
    
    // Function to fetch assets
    fun fetchAssets(isRefresh: Boolean = false) {
        scope.launch {
            if (isRefresh) {
                isRefreshing = true
            } else {
                isLoading = true
            }
            errorMessage = null
            
            val result = AssetRepository.getAssetsByCompany(companyId, sessionManager)
            
            result.onSuccess { assetList ->
                assets = assetList
                filteredAssets = assetList
                if (isRefresh) {
                    isRefreshing = false
                } else {
                    isLoading = false
                }
            }.onFailure { error ->
                errorMessage = error.message ?: "Failed to load assets"
                if (isRefresh) {
                    isRefreshing = false
                } else {
                    isLoading = false
                }
            }
        }
    }
    
    // Filter assets based on search query
    LaunchedEffect(searchQuery, assets) {
        filteredAssets = if (searchQuery.isEmpty()) {
            assets
        } else {
            assets.filter { asset ->
                asset.name.contains(searchQuery, ignoreCase = true) ||
                asset.tagNo.contains(searchQuery, ignoreCase = true) ||
                asset.category.name.contains(searchQuery, ignoreCase = true) ||
                asset.serialNo.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    // Fetch assets on launch
    LaunchedEffect(Unit) {
        fetchAssets()
    }
    
    Scaffold(
        containerColor = White,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO: Navigate to Add Asset screen */ },
                containerColor = Color.Black,
                contentColor = White
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Asset"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(White)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(15.dp))
            
            // Refresh button
            if (!isLoading && !isRefreshing) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(
                        onClick = { fetchAssets(isRefresh = true) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.Gray
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pull to refresh", fontSize = 12.sp)
                    }
                }
            }
            
            // Refresh indicator
            if (isRefreshing) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Black
                )
            }
            
            // Top Bar with Welcome Message
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Assets",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = "Hello, $userName",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Search Icon
                    IconButton(
                        onClick = { showSearchBar = !showSearchBar },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFF5F5F5), CircleShape)
                    ) {
                        Icon(
                            imageVector = if (showSearchBar) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (showSearchBar) "Close Search" else "Search",
                            tint = Color.Black
                        )
                    }
                    
                    // Filter Icon
                    IconButton(
                        onClick = { /* TODO: Show filter dialog */ },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFF5F5F5), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "Filter",
                            tint = Color.Black
                        )
                    }
                    
                    // Logout Icon
                    IconButton(
                        onClick = { showLogoutDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFF5F5F5), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = Color.Black
                        )
                    }
                }
            }
            
            // Search Bar (Expandable)
            if (showSearchBar) {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search assets, tags, categories...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear"
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color(0xFFF5F5F5),
                        focusedContainerColor = Color(0xFFF5F5F5),
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Black
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Assets count
            if (!isLoading && errorMessage == null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) {
                            "${filteredAssets.size} of ${assets.size} Assets"
                        } else {
                            "${assets.size} Assets Found"
                        },
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    
                    if (searchQuery.isNotEmpty()) {
                        Text(
                            text = "Searching...",
                            fontSize = 12.sp,
                            color = Color(0xFF2196F3),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
    
            // Content based on state
            when {
                isLoading -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(5) {
                            AssetCardSkeleton()
                        }
                    }
                }
                
                errorMessage != null -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = Color.Red,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = errorMessage ?: "Something went wrong",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { fetchAssets() }) {
                            Text("Retry")
                        }
                    }
                }
                
                filteredAssets.isEmpty() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory,
                            contentDescription = "No assets",
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No assets found",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                    }
                }
                
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredAssets) { asset ->
                            AssetCard(
                                asset = asset,
                                onViewClick = { /* TODO: Navigate to asset details */ },
                                onEditClick = { /* TODO: Navigate to edit asset */ }
                            )
                        }
                    }
                }
            }
        }
        
        // Logout Confirmation Dialog
        if (showLogoutDialog) {
            AlertDialog(
                onDismissRequest = { showLogoutDialog = false },
                title = {
                    Text(
                        text = "Logout",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to logout?",
                        fontSize = 16.sp
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            sessionManager.clearSession()
                            showLogoutDialog = false
                            onNavigate?.invoke(AppScreen.Login)
                        }
                    ) {
                        Text("Logout", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogoutDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = White,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun AssetCard(
    asset: Asset,
    modifier: Modifier = Modifier,
    onViewClick: () -> Unit = {},
    onEditClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = White
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Asset Image (placeholder)
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF5F5F5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = asset.name,
                        tint = Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Asset Details
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = asset.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tag,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "#${asset.tagNo}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Category,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.Gray
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = asset.category.name,
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    
                    asset.currentLocation?.let { location ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = Color.Gray
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = location.area ?: location.location_name ?: "Unknown",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                // Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (asset.status.lowercase()) {
                        "active" -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                        "inactive" -> Color(0xFFF44336).copy(alpha = 0.1f)
                        else -> Color(0xFF9E9E9E).copy(alpha = 0.1f)
                    }
                ) {
                    Text(
                        text = asset.status,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = when (asset.status.lowercase()) {
                            "active" -> Color(0xFF4CAF50)
                            "inactive" -> Color(0xFFF44336)
                            else -> Color(0xFF9E9E9E)
                        }
                    )
                }
            }
            
            // Divider
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = Color(0xFFE0E0E0)
            )
            
            // Action Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // View Button
                OutlinedButton(
                    onClick = onViewClick,
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Black
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = "View",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View", fontSize = 12.sp)
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Edit Button
                Button(
                    onClick = onEditClick,
                    modifier = Modifier.height(36.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Black,
                        contentColor = White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp)
                }
            }
        }
    }
}
