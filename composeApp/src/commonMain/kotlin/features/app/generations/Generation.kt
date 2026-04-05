package features.app.generations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import navigation.AppScreen

@Composable
fun GenerateCodeScreen(
    onBack: () -> Unit,
    onNavigate: (AppScreen) -> Unit,
    onNavigateBarcode: (DynamicBarcodeType) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    val tabs = listOf("GS1 Code", "2D Code", "1D / Others")
    val primaryColor = Color(0xFF163C66)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Spacer(modifier = Modifier.height(25.dp))

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = primaryColor
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "Generate Code",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
        }

        // Tab Bar - Clean White Theme with Primary Color
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .background(Color(0xFFF8F9FA), RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(Color(0xFFF8F9FA), RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) primaryColor else Color.Transparent
                            )
                            .clickable { selectedTab = index },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) Color.White else primaryColor,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Content Area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(horizontal = 16.dp)
        ) {
            val options = when (selectedTab) {
                0 -> listOf(
                    Pair("GS1 2D Barcode", Icons.Default.QrCode),
                    Pair("GS1 Digital Link", Icons.Default.Link),
                    Pair("Multi URL", Icons.Default.Web)
                )
                1 -> listOf(
                    Pair("QR Code", Icons.Default.QrCode),
                    Pair("Aztec Code", Icons.Default.Code)
                )
                else -> listOf(
                    Pair("Code 128", Icons.Default.Numbers),
                    Pair("EAN-13", Icons.Default.QrCode),
                    Pair("EAN-8", Icons.Default.QrCode),
                    Pair("UPC-A", Icons.Default.QrCode),
                    Pair("DataBar", Icons.Default.ToggleOn),
                    Pair("Others", Icons.Default.MoreHoriz)
                )
            }

            options.forEach { (option, icon) ->
                OptionCard(
                    title = option,
                    icon = icon,
                    primaryColor = primaryColor,
                    onClick = {

                        when (option) {

                            "GS1 2D Barcode" -> {
                                onNavigate(AppScreen.GS12DBarcode)
                            }

                            "GS1 Digital Link" -> {
                                onNavigate(AppScreen.GS1DigitalBarcodeScreen)
                            }

                            "Multi URL" -> {
                                onNavigate(AppScreen.MultiLinkBarcodeScreen)
                            }

                            else -> {

                                val type = when (option) {
                                    "Code 128" -> DynamicBarcodeType.CODE_128
                                    "EAN-13" -> DynamicBarcodeType.EAN_13
                                    "EAN-8" -> DynamicBarcodeType.EAN_8
                                    "UPC-A" -> DynamicBarcodeType.UPC_A
                                    "QR Code" -> DynamicBarcodeType.QR_CODE
                                    "Aztec Code" -> DynamicBarcodeType.AZTEC
                                    else -> DynamicBarcodeType.CODE_128
                                }

                                onNavigateBarcode(type)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun OptionCard(
    title: String,
    icon: ImageVector,
    primaryColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(
            modifier = Modifier
                .border(1.dp, Color(0xFFEEEEEE), RoundedCornerShape(12.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Icon Container
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(primaryColor.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = primaryColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = title,
                        color = primaryColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                // Arrow
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Navigate",
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}