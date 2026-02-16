package features.app.generations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Web
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.MoreHoriz
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
    onNavigate: (AppScreen) -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    val tabs = listOf("GS1 Code", "2D Code", "1D / Others")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Spacer(modifier = Modifier.height(25.dp))

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
                    tint = Color.Black
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Text(
                text = "Generate Code",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

       Column(
    modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
        .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
        .padding(4.dp)
) {
    Row(
    modifier = Modifier
        .fillMaxWidth()
        .height(44.dp)     
        .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
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
                    if (isSelected) Color.Black else Color.Transparent
                )
                .clickable { selectedTab = index },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = if (isSelected) Color.White else Color.Black,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
}

        
        Spacer(modifier = Modifier.height(16.dp))
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTab) {
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
            }.forEach { (option, icon) ->
                OptionCard(
                    title = option,
                    icon = icon,
                    onClick = {
                        if (option == "GS1 2D Barcode") {
                            onNavigate(AppScreen.GS12DBarcode)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun OptionCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
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
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF5F5F5)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = Color.Black,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = title,
                        color = Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Navigate",
                    tint = Color(0xFF666666),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}