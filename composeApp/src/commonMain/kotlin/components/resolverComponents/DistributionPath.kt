package components.resolverComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import resolver.isNA
import resolverModels.DistStep
import resolverModels.DistStepStatus

@Composable
fun DistributionPath(
    steps: List<DistStep>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ResolverPalette.Surface, RoundedCornerShape(12.dp))
            .border(1.dp, ResolverPalette.Border, RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "DISTRIBUTION PATH",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = ResolverPalette.TextBody,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = ResolverPalette.TextFaint,
                modifier = Modifier.size(16.dp),
            )
        }

        if (expanded) {
            Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                steps.forEachIndexed { index, step ->
                    DistributionStepRow(step = step, isLast = index == steps.lastIndex)
                }
            }
        }
    }
}

@Composable
private fun DistributionStepRow(step: DistStep, isLast: Boolean) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier.width(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(step.status.dotColor()),
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .width(2.dp)
                        .weight(1f)
                        .background(ResolverPalette.Border),
                )
            }
        }

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                when (step.status) {
                    DistStepStatus.DIVERTED -> StatusTagRow(
                        label = "DIVERTED",
                        labelColor = ResolverPalette.Red,
                        badge = step.badge,
                        badgeBackground = ResolverPalette.OrangeChip,
                        badgeColor = ResolverPalette.OrangeText,
                    )

                    DistStepStatus.INVOICED -> StatusTagRow(
                        label = "INVOICED",
                        labelColor = ResolverPalette.OrangeStrong,
                        badge = step.badge?.takeUnless { isNA(it) },
                        badgeBackground = ResolverPalette.GreenChip,
                        badgeColor = ResolverPalette.GreenStrong,
                    )

                    else -> Unit
                }

                Text(
                    text = step.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = when (step.status) {
                        DistStepStatus.TARGET -> ResolverPalette.TextFaint
                        DistStepStatus.TRANSIT -> ResolverPalette.Blue
                        DistStepStatus.INVOICED -> ResolverPalette.OrangeText
                        else -> ResolverPalette.TextStrong
                    },
                )

                val subParts = step.sub.split("|")
                Text(
                    text = subParts.first().trim(),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = ResolverPalette.TextFaint,
                )
                if (subParts.size > 1) {
                    Text(
                        text = subParts[1].trim(),
                        fontSize = 9.sp,
                        lineHeight = 12.sp,
                        color = ResolverPalette.TextGhost,
                    )
                }
            }

            when (step.status) {
                DistStepStatus.DONE -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = ResolverPalette.Green,
                    modifier = Modifier.padding(top = 2.dp).size(14.dp),
                )

                DistStepStatus.TRANSIT -> Text(
                    text = "IN TRANSIT",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = ResolverPalette.BlueText,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ResolverPalette.BlueSoft)
                        .border(1.dp, ResolverPalette.BlueBorder, RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )

                DistStepStatus.DIVERTED -> Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = ResolverPalette.Red,
                    modifier = Modifier.padding(top = 2.dp).size(14.dp),
                )

                DistStepStatus.INVOICED -> Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = ResolverPalette.Orange,
                    modifier = Modifier.padding(top = 2.dp).size(14.dp),
                )

                DistStepStatus.TARGET -> Unit
            }
        }
    }
}

@Composable
private fun StatusTagRow(
    label: String,
    labelColor: Color,
    badge: String?,
    badgeBackground: Color,
    badgeColor: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.6.sp,
            color = labelColor,
        )
        if (!badge.isNullOrBlank()) {
            Text(
                text = badge.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = badgeColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeBackground)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

private fun DistStepStatus.dotColor(): Color = when (this) {
    DistStepStatus.DONE -> ResolverPalette.Green
    DistStepStatus.DIVERTED -> ResolverPalette.Red
    DistStepStatus.TRANSIT -> ResolverPalette.BlueMid
    DistStepStatus.INVOICED -> ResolverPalette.Orange
    DistStepStatus.TARGET -> ResolverPalette.TextGhost
}
