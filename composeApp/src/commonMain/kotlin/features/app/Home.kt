package features.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AllInbox
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.serialization.json.Json
import navigation.AppScreen
import network.models.UserDetail
import theme.White

// Expect function for platform-specific scanner handling
@Composable
expect fun HomeScanButton(
    onNavigate: (AppScreen) -> Unit,
    shouldTriggerScan: Boolean,
    onScanTriggered: () -> Unit
)

@Composable
fun Home(
    onNavigate: (AppScreen) -> Unit
) {
    val sessionManager = remember { SessionManager(getLocalStorage()) }
    val json = remember { Json { ignoreUnknownKeys = true } }
    
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
    println("userDetail = $userDetail")
    
    var triggerScan by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    
    HomeScanButton(
        onNavigate = onNavigate,
        shouldTriggerScan = triggerScan,
        onScanTriggered = { triggerScan = false }
    )
    
    Scaffold(
        containerColor = White,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { triggerScan = true },
                containerColor = Color.Black,
                contentColor = White
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(White)
                .padding(24.dp)
        ) {
        Spacer(modifier = Modifier.height(1.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Welcome back,",
                    fontSize = 16.sp,
                    color = Color.Gray
                )
                Text(
                    text = userName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Notification Icon
                IconButton(
                    onClick = { /* TODO */ },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFF5F5F5), CircleShape)
                ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = Color.Black
                        )
                }
                
                IconButton(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFF5F5F5), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Logout",
                        tint = Color.Black
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Action Cards Grid - 2x2
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // First Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ActionCard(
                    title = "Generate",
                    icon = Icons.Default.Add,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onNavigate(AppScreen.GenerateCodeScreen)
                    }
                )
                ActionCard(
                    title = "Picking",
                    icon = Icons.Default.LocalShipping,
                    modifier = Modifier.weight(1f),
                    onClick = { /* TODO */ }
                )
            }
            
            // Second Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ActionCard(
                    title = "Packing",
                    icon = Icons.Default.AllInbox,
                    modifier = Modifier.weight(1f),
                    onClick = { /* TODO */ }
                )
                ActionCard(
                    title = "Receiving",
                    icon = Icons.Default.MoveToInbox,
                    modifier = Modifier.weight(1f),
                    onClick = { /* TODO */ }
                )
            }
        }
    }
    
    // Logout Confirmation Dialog - outside Scaffold
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
                            // Clear session data from local storage
                            sessionManager.clearSession()
                            showLogoutDialog = false
                            // Navigate to login screen
                            onNavigate(AppScreen.Login)
                        }
                    ) {
                        Text("Logout", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showLogoutDialog = false }
                    ) {
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
fun ActionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(160.dp)
            .shadow(4.dp, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = White
        ),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = Color.Black
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = White,
                    modifier = Modifier.padding(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
        }
    }
}
