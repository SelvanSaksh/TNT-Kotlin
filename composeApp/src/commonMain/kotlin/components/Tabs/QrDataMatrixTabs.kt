package components.Tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QrDataMatrixTabs(
    selectedTab: Int,
    onTabChange: (Int) -> Unit
) {
    val tabs = listOf(
        "QR Code" to Icons.Default.QrCode,
        "Data Matrix" to Icons.Default.GridOn
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 16.dp)
            .background(Color(0xFFF5F5F5), RoundedCornerShape(12.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, (title, icon) ->
            val isSelected = selectedTab == index

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(
                        if (isSelected) Color.Black else Color.Transparent,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onTabChange(index) },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = if (isSelected) Color.White else Color.Black,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        color = if (isSelected) Color.White else Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
