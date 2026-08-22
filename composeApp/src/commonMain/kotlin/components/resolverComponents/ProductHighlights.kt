package components.resolverComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonObject
import resolver.cms.cmsDict
import resolver.cms.cmsString

private val HIGHLIGHT_TABS = listOf(
    "Composition",
    "Safety",
    "Allergen",
    "Side Effects",
    "Schedule",
    "Details",
    "Usage",
)

@Composable
fun ProductHighlights(
    productData: JsonObject,
    allergen: String,
    sideEffects: String,
    modifier: Modifier = Modifier,
) {
    var activeTab by remember { mutableStateOf(HIGHLIGHT_TABS.first()) }
    val basic = cmsDict(productData["Basic Details"])

    fun basicText(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key -> cmsString(basic?.get(key))?.takeIf { it.isNotBlank() } }

    val body = when (activeTab) {
        "Composition" -> basicText("Composition", "composition")
            ?: "No composition data available."

        "Safety" -> basicText("Safety", "safety") ?: "No safety data available."
        "Allergen" -> allergen.ifBlank { "No allergen information available." }
        "Side Effects" -> sideEffects.ifBlank { "No side effects information available." }
        "Details" -> basicText("Product Details") ?: "No product details available."
        "Usage" -> basicText("How To Use") ?: "No usage instructions available."
        else -> ""
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(ResolverPalette.Surface, RoundedCornerShape(12.dp))
            .border(1.dp, ResolverPalette.Border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "PRODUCT HIGHLIGHTS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = ResolverPalette.TextBody,
            )
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = ResolverPalette.TextFaint,
                modifier = Modifier.size(14.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HIGHLIGHT_TABS.forEach { tab ->
                val selected = tab == activeTab
                Text(
                    text = tab.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) ResolverPalette.Surface else ResolverPalette.TextMuted,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) ResolverPalette.Blue else ResolverPalette.Chip)
                        .clickable { activeTab = tab }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                )
            }
        }

        Text(
            text = body,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = ResolverPalette.TextBody,
        )
    }
}
