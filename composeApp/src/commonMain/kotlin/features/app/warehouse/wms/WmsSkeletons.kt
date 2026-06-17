package features.app.warehouse.wms

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import components.ShimmerEffect

@Composable
private fun WmsShimmer(
    modifier: Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
) {
    ShimmerEffect(modifier = modifier.clip(shape))
}

@Composable
fun WmsPickerHomeSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        WmsShimmer(
            modifier = Modifier
                .width(120.dp)
                .height(20.dp),
        )
        WmsPriorityTaskCardSkeleton()
        WmsShimmer(
            modifier = Modifier
                .width(72.dp)
                .height(18.dp),
        )
        repeat(2) { WmsPickedTaskCardSkeleton() }
    }
}

@Composable
fun WmsPriorityTaskCardSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp))
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            WmsShimmer(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            WmsShimmer(
                modifier = Modifier
                    .width(56.dp)
                    .height(14.dp),
            )
        }
        WmsShimmer(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(16.dp),
        )
        WmsShimmer(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(12.dp),
        )
    }
}

@Composable
fun WmsPickedTaskCardSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WmsColors.SuccessBg)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WmsShimmer(
            modifier = Modifier
                .width(4.dp)
                .height(52.dp),
            shape = RoundedCornerShape(4.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WmsShimmer(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(18.dp),
            )
            WmsShimmer(
                modifier = Modifier
                    .width(56.dp)
                    .height(18.dp),
                shape = RoundedCornerShape(6.dp),
            )
            WmsShimmer(
                modifier = Modifier
                    .fillMaxWidth(0.55f)
                    .height(14.dp),
            )
        }
        WmsShimmer(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
        )
    }
}

@Composable
fun WmsListCardSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WmsShimmer(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(18.dp),
                )
                WmsShimmer(
                    modifier = Modifier
                        .fillMaxWidth(0.45f)
                        .height(14.dp),
                )
            }
            WmsShimmer(
                modifier = Modifier
                    .width(52.dp)
                    .height(18.dp),
                shape = RoundedCornerShape(8.dp),
            )
        }
        WmsShimmer(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(12.dp),
        )
    }
}

@Composable
fun WmsListSkeleton(count: Int = 3) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(count) { WmsListCardSkeleton() }
    }
}

@Composable
fun WmsStatPairSkeleton() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(2) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WmsShimmer(
                    modifier = Modifier
                        .width(48.dp)
                        .height(24.dp),
                )
                WmsShimmer(
                    modifier = Modifier
                        .width(72.dp)
                        .height(14.dp),
                )
            }
        }
    }
}

@Composable
fun WmsLineItemSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WmsShimmer(
            modifier = Modifier
                .fillMaxWidth(0.65f)
                .height(16.dp),
        )
        WmsShimmer(
            modifier = Modifier
                .fillMaxWidth(0.35f)
                .height(12.dp),
        )
        WmsShimmer(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(14.dp),
        )
    }
}

@Composable
fun WmsLineListSkeleton(count: Int = 4) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(count) { WmsLineItemSkeleton() }
    }
}
