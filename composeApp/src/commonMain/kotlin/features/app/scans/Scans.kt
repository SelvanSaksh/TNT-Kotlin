package features.app.scans

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import navigation.AppScreen
import theme.White

@Composable
expect fun ScannerView(
    scanMode: String,
    onScanResult: (String) -> Unit,
    onNavigate: (AppScreen) -> Unit
)

@Composable
fun Scans(
    onNavigate: (AppScreen) -> Unit
) {
    var currentScanMode by remember { mutableStateOf("SINGLE") }
    var scannedResult by remember { mutableStateOf<String?>(null) }
    var showResult by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera view
        key(currentScanMode) {
            ScannerView(
                scanMode = currentScanMode,
                onScanResult = { result ->
                    scannedResult = result
                    if (currentScanMode != "MULTI") {
                        showResult = true
                    }
                },
                onNavigate = onNavigate
            )
        }

        /*ScannerView(
            scanMode = currentScanMode,
            onScanResult = { result ->
                scannedResult = result
                // Only show popup for Single and Auth modes, not Multi
                if (currentScanMode != "MULTI") {
                    showResult = true
                }
            },
            onNavigate = onNavigate
        )*/
        
        // Result overlay (shows on top of camera)
        if (showResult && scannedResult != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = White)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(64.dp)
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Text(
                            text = "Scan Successful!",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(Modifier.height(16.dp))
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF5F5F5)
                            )
                        ) {
                            Text(
                                text = scannedResult ?: "",
                                fontSize = 14.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        
                        Spacer(Modifier.height(24.dp))
                        
                        Button(
                            onClick = {
                                showResult = false
                                scannedResult = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Black
                            )
                        ) {
                            Text("Scan Again")
                        }
                    }
                }
            }
        }
        
        // Bottom mode switcher buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ModeButton(
                title = "Single",
                icon = Icons.Default.QrCode2,
                isSelected = currentScanMode == "SINGLE",
                onClick = {
                    currentScanMode = "SINGLE"
                    showResult = false
                }
            )
            
            ModeButton(
                title = "Auth",
                icon = Icons.Default.VerifiedUser,
                isSelected = currentScanMode == "AUTH",
                onClick = {
                    currentScanMode = "AUTH"
                    showResult = false
                }
            )
            
            ModeButton(
                title = "Multi",
                icon = Icons.Default.QrCode,
                isSelected = currentScanMode == "MULTI",
                onClick = {
                    currentScanMode = "MULTI"
                    showResult = false
                }
            )
        }
        
        // Back button
        IconButton(
            onClick = { onNavigate(AppScreen.Home) },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back",
                tint = White
            )
        }
    }
}

@Composable
fun ModeButton(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) White else Color.Transparent,
            contentColor = if (isSelected) Color.Black else White
        ),
        modifier = Modifier.width(100.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(text = title, fontSize = 12.sp)
        }
    }
}
