package components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 2.dp,
        label = "switch_thumb_offset"
    )

    val trackColor = if (checked) Color.Black else Color.Gray
    val borderColor = Color.Black
    val thumbColor = if (checked) Color.White else Color.White

    Box(
        modifier = modifier
            .width(44.dp)
            .height(24.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable(enabled = enabled) {
                onCheckedChange(!checked)
            }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbColor)
        )
    }
}
