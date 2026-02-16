package features.app.generations

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import components.AppSwitch
import components.InputField
import components.PrimaryButton
import components.Tabs.QrDataMatrixTabs
import components.inputs.MultiSelectInputField

@Composable
fun GS12DBarcode(
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }

    var gtin by remember { mutableStateOf("") }
    var gtinError by remember { mutableStateOf<String?>(null) }
    var isEnabled by remember { mutableStateOf(false) }

    val indicators = listOf(
        "Expiration Date",
        "Production Date",
        "Best Before Date",
        "Packaging Date",
        "Serial Number"
    )

    var selectedIndicators by remember { mutableStateOf<List<String>>(emptyList()) }

    var indicatorValues by remember {
        mutableStateOf<Map<String, String>>(emptyMap())
    }

    var indicatorErrors by remember {
        mutableStateOf<Map<String, String?>>(emptyMap())
    }

    // 🔹 ROOT (no horizontal padding)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // 🔹 Header
        Row(
            modifier = Modifier
                .fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "GS1 2D Barcode",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        // 🔹 Tabs (full width)
        QrDataMatrixTabs(
            selectedTab = selectedTab,
            onTabChange = { selectedTab = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 🔹 INPUTS CONTAINER (ONLY place with horizontal padding)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {

            // GTIN
            InputField(
                value = gtin,
                onValueChange = {
                    if (it.all(Char::isDigit)) {
                        gtin = it
                        gtinError = null
                    }
                },
                placeholder = "Enter GTIN (14 digits)",
                isError = gtinError != null,
                errorMessage = gtinError
            )

            // Dynamic Indicator Inputs
            selectedIndicators.forEach { indicator ->
                Spacer(modifier = Modifier.height(12.dp))

                InputField(
                    value = indicatorValues[indicator].orEmpty(),
                    onValueChange = { value ->
                        indicatorValues = indicatorValues + (indicator to value)
                        indicatorErrors = indicatorErrors + (indicator to null)
                    },
                    placeholder = when (indicator) {
                        "Expiration Date" -> "Enter Expiry (MMYY)"
                        "Production Date" -> "Enter Production Date"
                        "Best Before Date" -> "Enter Best Before Date"
                        "Packaging Date" -> "Enter Packaging Date"
                        "Serial Number" -> "Enter Serial Number"
                        else -> "Enter $indicator"
                    },
                    isError = indicatorErrors[indicator] != null,
                    errorMessage = indicatorErrors[indicator]
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Multi Select
            MultiSelectInputField(
                items = indicators,
                selectedItems = selectedIndicators,
                onSelectionChange = { newSelection ->
                    selectedIndicators = newSelection

                    indicatorValues =
                        indicatorValues.filterKeys { it in newSelection } +
                                newSelection.associateWith { indicatorValues[it] ?: "" }

                    indicatorErrors =
                        indicatorErrors.filterKeys { it in newSelection }
                },
                itemLabel = { it },
                placeholder = "+ Add Application Indicators"
            )

            Spacer(modifier = Modifier.height(24.dp))
            AppSwitch(
                checked = isEnabled,
                onCheckedChange = { isEnabled = it }
            )


            // Submit Button
            PrimaryButton(
                text = "Generate Barcode",
                onClick = {
                    val errors = mutableMapOf<String, String?>()

                    if (gtin.length != 14) {
                        gtinError = "GTIN must be exactly 14 digits"
                    }

                    indicatorValues.forEach { (key, value) ->
                        when (key) {
                            "Expiration Date" ->
                                if (!value.matches(Regex("""\d{4}""")))
                                    errors[key] = "Use MMYY format"

                            "Serial Number" ->
                                if (value.isBlank())
                                    errors[key] = "Serial number required"

                            else ->
                                if (value.isBlank())
                                    errors[key] = "$key is required"
                        }
                    }

                    indicatorErrors = errors

                    if (gtinError == null && errors.isEmpty()) {
                        // ✅ Generate GS1 barcode
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
