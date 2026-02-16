package components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import theme.Black
import theme.White

@Composable
fun PrimaryButton(
    modifier: Modifier = Modifier,
    text: String,
    isLoading: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) White else Black
    val textColor = if (isDark) Black else White

    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        modifier = modifier
            .height(56.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = textColor,
            disabledContainerColor = backgroundColor.copy(alpha = 0.5f),
            disabledContentColor = textColor.copy(alpha = 0.5f)
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
                color = textColor
            )
        } else {
            Text(
                text = text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor
            )
        }
    }
}
