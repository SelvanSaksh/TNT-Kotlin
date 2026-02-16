package components.inputs

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.material3.CheckboxDefaults

@Composable
fun <T> MultiSelectInputField(
    items: List<T>,
    selectedItems: List<T>,
    onSelectionChange: (List<T>) -> Unit,
    itemLabel: (T) -> String,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }

    val fieldShape = RoundedCornerShape(8.dp)

    val displayText = remember(selectedItems) {
        selectedItems.joinToString(", ") { itemLabel(it) }
    }

    Box(modifier = modifier.fillMaxWidth()) {

        // 🔹 Outside click overlay
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        expanded = false
                    }
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {

            // Label
            label?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isError)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Input field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = if (isError)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.outline,
                        shape = fieldShape
                    )
                    .background(
                        color = Color.White,
                        shape = fieldShape
                    )
                    .clickable(enabled = enabled) {
                        expanded = !expanded
                    }
                    .padding(horizontal = 12.dp, vertical = 20.dp)
            ) {
                Text(
                    text = if (displayText.isEmpty()) placeholder else displayText,
                    color = if (displayText.isEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Dropdown list
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outline,
                            fieldShape
                        )
                        .background(
                            Color.White,
                            fieldShape
                        )
                        .padding(8.dp)
                ) {
                    items.forEach { item ->
                        val isSelected = selectedItems.contains(item)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated =
                                        if (isSelected) selectedItems - item
                                        else selectedItems + item
                                    onSelectionChange(updated)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = Color.Black,          // ✔ box fill when checked
                                    uncheckedColor = Color.Black,        // ✔ border when unchecked
                                    checkmarkColor = Color.White         // ✔ tick color
                                )
                            )

                            Spacer(Modifier.width(8.dp))

                            Text(
                                text = itemLabel(item),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }

            // Error message
            if (isError && errorMessage != null) {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}
