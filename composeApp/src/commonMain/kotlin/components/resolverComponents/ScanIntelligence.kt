package components.resolverComponents

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val PrettyJson = Json { prettyPrint = true }

@Composable
fun ScanIntelligence(payload: JsonObject, modifier: Modifier = Modifier) {
    JsonDisclosureCard(
        title = "SCAN INTELLIGENCE",
        payload = payload,
        modifier = modifier,
    )
}

@Composable
fun RawParserOutput(payload: JsonObject, modifier: Modifier = Modifier) {
    JsonDisclosureCard(
        title = "RAW PARSER OUTPUT",
        payload = payload,
        leadingIcon = Icons.Default.Storage,
        modifier = modifier,
    )
}

@Composable
private fun JsonDisclosureCard(
    title: String,
    payload: JsonObject,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    val pretty = remember(payload) {
        runCatching { PrettyJson.encodeToString(JsonObject.serializer(), payload) }
            .getOrElse { payload.toString() }
    }

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
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                leadingIcon?.let {
                    Icon(
                        imageVector = it,
                        contentDescription = null,
                        tint = ResolverPalette.TextBody,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = ResolverPalette.TextBody,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = ResolverPalette.TextFaint,
                modifier = Modifier.size(16.dp),
            )
        }

        if (expanded) {
            Text(
                text = pretty,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace,
                color = ResolverPalette.TextMuted,
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                    .fillMaxWidth()
                    .background(ResolverPalette.BorderSoft, RoundedCornerShape(8.dp))
                    .heightIn(max = 192.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(8.dp),
            )
        }
    }
}
