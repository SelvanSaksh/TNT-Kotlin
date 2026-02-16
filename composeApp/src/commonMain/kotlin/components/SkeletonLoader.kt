package components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ShimmerEffect(
    modifier: Modifier = Modifier
) {
    val shimmerColors = listOf(
        Color(0xFFE0E0E0),
        Color(0xFFF5F5F5),
        Color(0xFFE0E0E0)
    )
    
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    
    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(translateAnim - 1000f, translateAnim - 1000f),
        end = Offset(translateAnim, translateAnim)
    )
    
    Box(
        modifier = modifier.background(brush)
    )
}

@Composable
fun AssetCardSkeleton(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(16.dp)
    ) {
        // Image skeleton
        ShimmerEffect(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Content skeleton
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Title
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            
            // Tag
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            
            // Category
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Status badge skeleton
        ShimmerEffect(
            modifier = Modifier
                .width(60.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(12.dp))
        )
    }
}
