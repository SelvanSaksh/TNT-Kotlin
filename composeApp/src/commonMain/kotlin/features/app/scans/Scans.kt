package features.app.scans

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import navigation.AppScreen
import navigation.appscreen.Screens
import theme.White

@Composable
expect fun ScannerView(
    verifyAuthenticity: Boolean,
    isMultiScan: Boolean,
    onScanResult: (String) -> Unit,
    onNavigate: (String) -> Unit
)

@Composable
fun Scans(
    onNavigate: (String) -> Unit,
    isGuestMode: Boolean = false,
) {
    var verifyAuthenticity by remember { mutableStateOf(!isGuestMode) }
    var isMultiScan by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }

    var currentScanMode by remember { mutableStateOf("VERIFY") }

    LaunchedEffect(verifyAuthenticity, isMultiScan) {
        currentScanMode = when {
            isMultiScan && verifyAuthenticity -> "MULTI_AUTH"
            isMultiScan -> "MULTI"
            verifyAuthenticity -> "VERIFY"
            else -> "SINGLE"
        }
        println("SCANNERLOG: [Scans] mode resolved → $currentScanMode (verifyAuthenticity=$verifyAuthenticity, isMultiScan=$isMultiScan)")
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        // Camera view
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.weight(1f)
            ) {
                key(verifyAuthenticity, isMultiScan) {
                    ScannerView(
                        verifyAuthenticity = verifyAuthenticity,
                        isMultiScan = isMultiScan,
                        onScanResult = { result ->
                            println("SCANNERLOG: [Scans] onScanResult mode=$currentScanMode resultLen=${result.length} preview='${result.take(120)}'")
                        },
                        onNavigate = onNavigate
                    )
                }
            }
            // Bottom mode switcher buttons
/*            Row(
                modifier = Modifier
                    .fillMaxWidth()
//                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = "Single Scan",
                    color = Color.White,
                    modifier = Modifier
                        .background(
                            if (currentScanMode == "VERIFY") Color.Blue else Color.Transparent,
                        )
                        .clickable {
                        currentScanMode = "VERIFY"
                        showResult = false
                    }
                )
                Text(
                    "Multi Scan",
                    modifier = Modifier.clickable {
                        currentScanMode = "MULTI"
                        showResult = false
                    }
                )
            }*/

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
        
        // Parsed scan popup is shown from ScannerView (Android) for guest and logged-in users
        
        // Scan options (single / multi / authenticity) — logged-in users only
        if (!isGuestMode) {
            IconButton(
                onClick = { showMoreSheet = true },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 16.dp, top = 16.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More options",
                    tint = White
                )
            }
        }

/*        Box(
            modifier = Modifier
                .padding(bottom = 12.dp)
                .align(Alignment.BottomCenter)
        ) {
            ScanModeSelector(
                currentScanMode = currentScanMode,
                onModeChange = { mode ->
                    currentScanMode = mode
                    showResult = false
                }
            )
        }*/
    }

    @OptIn(ExperimentalMaterial3Api::class)
    if (!isGuestMode && showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Scan Options",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "SCAN MODE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF6B7280)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(
                            selected = !isMultiScan,
                            onClick = { isMultiScan = false },
                            label = { Text("Single Scan") }
                        )
                        FilterChip(
                            selected = isMultiScan,
                            onClick = { isMultiScan = true },
                            label = { Text("Multi Scan") }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF3F4F6))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "Verify Authenticity",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF111827)
                        )
                        Text(
                            when {
                                verifyAuthenticity && isMultiScan ->
                                    "Enabled — multi scan with authentication"
                                verifyAuthenticity ->
                                    "Enabled — barcodes will be authenticated"
                                isMultiScan ->
                                    "Disabled — multi scan without authentication"
                                else ->
                                    "Disabled — raw barcode data only"
                            },
                            fontSize = 12.sp,
                            color = Color(0xFF6B7280)
                        )
                    }
                    Switch(
                        checked = verifyAuthenticity,
                        onCheckedChange = { enabled ->
                            verifyAuthenticity = enabled
                        },
                        enabled = !isGuestMode
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun ScanModeSelector(
    currentScanMode: String,
    onModeChange: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xCC111827))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Single Scan Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (currentScanMode == "VERIFY") Color(0xFF2563EB)
                        else Color.Transparent
                    )
                    .clickable {
                        onModeChange("VERIFY")
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Single Scan",
                    color = if (currentScanMode == "SINGLE") Color.White
                    else Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Multi Scan Tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (currentScanMode == "MULTI") Color(0xFF2563EB)
                        else Color.Transparent
                    )
                    .clickable {
                        onModeChange("MULTI")
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Multi Scan",
                    color = if (currentScanMode == "MULTI") Color.White
                    else Color.White.copy(alpha = 0.6f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
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
