package features.app.generations

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.SwipeDownAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import components.AppSwitch
import components.InputField
import components.PrimaryButton
import components.Tabs.QrDataMatrixTabs
import features.app.downloadImage
import features.app.shareImage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SingleBarcodeResponse(
    val filename: String
)

@Serializable
data class MultipleBarcodeResponse(
    val filenames: List<String>
)

sealed class BarcodeResult {
    data class Single(val url: String) : BarcodeResult()
    data class Multiple(val urls: List<String>) : BarcodeResult()
    data class Failure(val message: String) : BarcodeResult()
}


private val httpClient = HttpClient {
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            isLenient = true
        })
    }
}

private const val BASE_URL = "https://dlhub.8aiku.com/gen"

private fun buildGS1String(
    gtin: String,
    selectedFields: List<String>,
    fieldValues: Map<String, String>
): String {
    val sb = StringBuilder("(01)$gtin")
    if (selectedFields.contains("Expiration Date") && fieldValues["Expiration Date"].orEmpty().isNotBlank())
        sb.append("(17)${fieldValues["Expiration Date"]}")
    if (selectedFields.contains("Batch Number") && fieldValues["Batch Number"].orEmpty().isNotBlank())
        sb.append("(10)${fieldValues["Batch Number"]}")
    if (selectedFields.contains("Production Date") && fieldValues["Production Date"].orEmpty().isNotBlank())
        sb.append("(11)${fieldValues["Production Date"]}")
    if (selectedFields.contains("Packaging Date") && fieldValues["Packaging Date"].orEmpty().isNotBlank())
        sb.append("(13)${fieldValues["Packaging Date"]}")
    if (selectedFields.contains("Best Before Date") && fieldValues["Best Before Date"].orEmpty().isNotBlank())
        sb.append("(15)${fieldValues["Best Before Date"]}")
    if (selectedFields.contains("Serial No") && fieldValues["Serial No"].orEmpty().isNotBlank())
        sb.append("(21)${fieldValues["Serial No"]}")
    return sb.toString()
}

private val GS1_REGEX = Regex(
    """^\(01\)\d{14}(?:\(17\)\d{6})?(?:\(10\)[A-Z0-9\-\s]{1,20})?(?:\(11\)\d{6})?(?:\(13\)\d{6})?(?:\(15\)\d{6})?(?:\(21\)[A-Z0-9\-\s]{1,25})?$"""
)

private suspend fun generateBarcodeApi(
    gs1Data: String,
    barcodeType: String,
    width: Int,
    height: Int,
    serialize: Boolean,
    serialPrefix: String,
    serialStart: String,
    barcodeQty: String
): BarcodeResult {
    return try {
        // Normalize
        var normalised = barcodeType
            .lowercase()
            .replace("-", "")
            .replace(" ", "")
        if (normalised == "datamatrix") normalised = "gs1datamatrix"

        val response = httpClient.get("$BASE_URL/gen-barcode") {
            parameter("bc_type", normalised)
            parameter("width", width)
            parameter("height", height)
            parameter("data", gs1Data)

            if (serialize) {
                parameter("serial_Prefix", serialPrefix)
                parameter("serialised", "1")
                parameter("serial_start", serialStart)
                parameter("bc_nos", barcodeQty)
            }
        }

        println("🔥 STATUS: ${response.status}")

        val bodyText = response.body<String>()

        try {
            val multi = Json { ignoreUnknownKeys = true }
                .decodeFromString<MultipleBarcodeResponse>(bodyText)

            println("🔥 Parsed MULTIPLE response: ${multi.filenames}")

            if (multi.filenames.isNotEmpty()) {
                val urls = multi.filenames.map { buildDownloadUrl(it) }
                return BarcodeResult.Multiple(urls)
            }
        } catch (e: Exception) {
            println("❌ MULTI PARSE ERROR: ${e.message}")
        }

        try {
            val single = Json { ignoreUnknownKeys = true }
                .decodeFromString<SingleBarcodeResponse>(bodyText)

            println("🔥 Parsed SINGLE response: ${single.filename}")

            return BarcodeResult.Single(buildDownloadUrl(single.filename))
        } catch (e: Exception) {
            println("❌ SINGLE PARSE ERROR: ${e.message}")
        }

        println("❌ Unknown response format")
        BarcodeResult.Failure("Unexpected response format.")

    } catch (e: Exception) {
        println("❌ API EXCEPTION: ${e.message}")
        e.printStackTrace()
        BarcodeResult.Failure(e.message ?: "Unknown error")
    }
}

private fun buildDownloadUrl(filename: String) =
    "$BASE_URL/download-image?folder_variable=TMP_IMAGE_FOLDER&filename=$filename"

@Composable
fun GS12DBarcode(
    onBack: () -> Unit
) {
    // ── State ──────────────────────────────────
    var selectedType by remember { mutableStateOf("QR Code") }

    var gtin by remember { mutableStateOf("") }
    var gtinError by remember { mutableStateOf<String?>(null) }

    // Mirrors Swift default selection
    var selectedFields by remember { mutableStateOf(listOf("Batch Number", "Expiration Date")) }
    var fieldValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var fieldErrors by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }

    var selectedSize by remember { mutableStateOf("100 X 100") }
    var serialize by remember { mutableStateOf(false) }
    var serialPrefix by remember { mutableStateOf("") }
    var serialStart by remember { mutableStateOf("") }
    var barcodeQty by remember { mutableStateOf("") }

    var showIndicatorSheet by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }

    // Barcode result image URLs
    var barcodeImageUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var showImageViewer by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    val allIndicators = listOf(
        "Batch Number", "Expiration Date", "Production Date",
        "Packaging Date", "Best Before Date", "Serial No"
    )
    val sizes = listOf("100 X 100", "150 X 150", "200 X 200")

    // Disable serialize when Serial No is selected  (mirrors Swift .onChange)
    LaunchedEffect(selectedFields) {
        if (selectedFields.contains("Serial No")) {
            serialize = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF133D63)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "GS1 2D Barcode",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF133D63)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Type Tabs
            QrDataMatrixTabs(
                selectedTab = if (selectedType == "QR Code") 0 else 1,
                onTabChange = { index ->
                    selectedType = if (index == 0) "QR Code" else "Data Matrix"
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Scrollable content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // GTIN
                InputField(
                    value = gtin,
                    onValueChange = {
                        if (it.all { c -> c.isDigit() }) {
                            gtin = it
                            gtinError = null
                        }
                    },
                    placeholder = "GTIN",
                    isError = gtinError != null,
                    errorMessage = gtinError
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Dynamic fields for selected indicators
                selectedFields.forEach { field ->
                    InputField(
                        value = fieldValues[field].orEmpty(),
                        onValueChange = { v ->
                            fieldValues = fieldValues + (field to v)
                            fieldErrors = fieldErrors + (field to null)
                        },
                        placeholder = when (field) {
                            "Batch Number"    -> "Batch Number"
                            "Expiration Date" -> "Expire Date"
                            "Production Date" -> "Production Date"
                            "Packaging Date"  -> "Packaging Date"
                            "Best Before Date"-> "Best Before Date"
                            "Serial No"       -> "Serial No"
                            else              -> field
                        },
                        isError = fieldErrors[field] != null,
                        errorMessage = fieldErrors[field]
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedButton(
                    onClick = { showIndicatorSheet = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = Color(0xFFF2F2F7)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "+ Add Application Indicators",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDownward,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))

                // Size Selection
                Text(
                    text = "Barcode Size",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF133D63)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    sizes.forEach { size ->
                        SizeOption(
                            size = size,
                            isSelected = selectedSize == size,
                            onClick = { selectedSize = size }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Serialize warning  (mirrors Swift Text warning)
                if (selectedFields.contains("Serial No")) {
                    Text(
                        text = "Serialize is disabled when Serial No is selected.",
                        fontSize = 12.sp,
                        color = Color.Red
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Serialize Toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Serialize",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color =Color(0xFF133D63)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    AppSwitch(
                        checked = serialize,
                        onCheckedChange = { serialize = it },
                        enabled = !selectedFields.contains("Serial No")
                    )
                }

                // Serialize fields
                if (serialize) {
                    Spacer(modifier = Modifier.height(16.dp))
                    InputField(
                        value = serialPrefix,
                        onValueChange = { serialPrefix = it },
                        placeholder = "Serial Prefix"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    InputField(
                        value = serialStart,
                        onValueChange = { serialStart = it },
                        placeholder = "Serial Start"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    InputField(
                        value = barcodeQty,
                        onValueChange = { barcodeQty = it },
                        placeholder = "Barcode Qty"
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Generate Button
                PrimaryButton(
                    text = "Generate Code",
                    onClick = {
                        // ── Validation  (mirrors Swift button action) ──
                        val errors = mutableListOf<String>()

                        if (gtin.isEmpty()) {
                            errors.add("GTIN is required.")
                            gtinError = "GTIN is required."
                        } else if (gtin.length != 14) {
                            errors.add("GTIN must be exactly 14 digits.")
                            gtinError = "GTIN must be exactly 14 digits."
                        } else {
                            gtinError = null
                        }

                        val newFieldErrors = mutableMapOf<String, String?>()
                        selectedFields.forEach { field ->
                            val value = fieldValues[field].orEmpty()
                            when {
                                value.isBlank() -> {
                                    val label = if (field == "Serial No") "Serial Number" else field
                                    newFieldErrors[field] = "$label is required."
                                    errors.add("$label is required.")
                                }
                            }
                        }
                        fieldErrors = newFieldErrors

                        if (serialize) {
                            if (serialPrefix.isBlank()) errors.add("Serial prefix is required for serialization.")
                            if (serialStart.isBlank() || serialStart.toIntOrNull() == null)
                                errors.add("Valid serial start number is required.")
                            val qty = barcodeQty.toIntOrNull()
                            if (qty == null || qty <= 0)
                                errors.add("Valid quantity is required for serialization.")
                        }

                        if (errors.isNotEmpty()) {
                            alertMessage = errors.joinToString("\n")
                            return@PrimaryButton
                        }

                        // ── Build GS1 string ──
                        val gs1Data = buildGS1String(gtin, selectedFields, fieldValues)

                        // ── Regex validation  (mirrors Swift NSRegularExpression) ──
                        if (!GS1_REGEX.matches(gs1Data)) {
                            alertMessage = "Invalid GS1 barcode format."
                            return@PrimaryButton
                        }

                        // ── Parse size ──
                        val parts = selectedSize.split(" X ")
                        val w = parts.getOrNull(0)?.toIntOrNull() ?: 100
                        val h = parts.getOrNull(1)?.toIntOrNull() ?: 100

                        // ── API call ──
                        isLoading = true
                        coroutineScope.launch {
                            val result = generateBarcodeApi(
                                gs1Data = gs1Data,
                                barcodeType = selectedType,
                                width = w,
                                height = h,
                                serialize = serialize,
                                serialPrefix = serialPrefix,
                                serialStart = serialStart,
                                barcodeQty = barcodeQty
                            )
                            isLoading = false
                            when (result) {
                                is BarcodeResult.Single -> {
                                    barcodeImageUrls = listOf(result.url)
                                    showImageViewer = true
                                }
                                is BarcodeResult.Multiple -> {
                                    barcodeImageUrls = result.urls
                                    showImageViewer = true
                                }
                                is BarcodeResult.Failure -> {
                                    alertMessage = "Failed to generate barcode: ${result.message}"
                                }
                            }
                        }
                    }
                )

                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // ── Loading overlay  (mirrors Swift ZStack ProgressView) ──
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "Generating...")
                    }
                }
            }
        }
    }

    // ── Alert dialog  (mirrors Swift .alert) ──
    alertMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { alertMessage = null },
            title = { Text("Validation Error") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { alertMessage = null }) {
                    Text("OK")
                }
            }
        )
    }

    if (showIndicatorSheet) {
        IndicatorSelectionSheet(
            allIndicators = listOf(
                "Batch Number", "Expiration Date", "Production Date",
                "Packaging Date", "Best Before Date", "Serial No"
            ),
            selectedIndicators = selectedFields,
            onSelectionChange = { newSelection ->
                val removed = selectedFields - newSelection.toSet()
                selectedFields = newSelection
                fieldValues  = fieldValues.filterKeys  { it !in removed }
                fieldErrors  = fieldErrors.filterKeys  { it !in removed }
            },
            onDone = { showIndicatorSheet = false }
        )
    }

    if (showImageViewer && barcodeImageUrls.isNotEmpty()) {
        BarcodeImageViewerDialog(
            imageUrls = barcodeImageUrls,
            onClose = { showImageViewer = false }
        )
    }
}
@Composable
private fun SizeOption(
    size: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(108.dp)
            .height(108.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                Color(0xFFf1f7fe)        // Light blue selected background (as per screenshot)
            else
                Color(0xFFF8F9FA)        // Light gray for unselected
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 8.dp else 2.dp
        ),
        border = if (isSelected) {
            BorderStroke(3.dp, Color(0xFF114b7b))
        } else null
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {

                // Main Size Text
                Text(
                    text = size,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF114b7b) else Color(0xFF374151),
                    letterSpacing = 0.2.sp
                )
            }
        }
    }
}



@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IndicatorSelectionSheet(
    allIndicators: List<String>,
    selectedIndicators: List<String>,
    onSelectionChange: (List<String>) -> Unit,
    onDone: () -> Unit
) {
    var localSelection by remember { mutableStateOf(selectedIndicators) }

    ModalBottomSheet(onDismissRequest = onDone) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Title + Done  (mirrors Swift .navigationTitle + toolbar Done)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Select Indicators",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = {
                    onSelectionChange(localSelection)
                    onDone()
                }) {
                    Text("Done", color = Color(0xFF007AFF))
                }
            }

            HorizontalDivider()

            allIndicators.forEach { indicator ->
                val checked = localSelection.contains(indicator)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            localSelection = if (checked)
                                localSelection - indicator
                            else
                                localSelection + indicator
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (checked) Icons.Default.Check else Icons.Default.ArrowOutward,
                        contentDescription = null,
                        tint = Color(0xFF007AFF),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = indicator, fontSize = 16.sp)
                }
                HorizontalDivider(modifier = Modifier.padding(start = 54.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BarcodeImageViewerDialog(
    imageUrls: List<String>,
    onClose: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Image pager  (mirrors Swift TabView PageTabViewStyle)
            if (imageUrls.isNotEmpty()) {
                AsyncImage(
                    model = imageUrls[currentIndex],
                    contentDescription = "Barcode",
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center)
                )

                // Page indicator
                if (imageUrls.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        imageUrls.indices.forEach { i ->
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (i == currentIndex) Color.White else Color.Gray,
                                        RoundedCornerShape(50)
                                    )
                            )
                        }
                    }

                    // Prev / Next
                    Row(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (currentIndex > 0) {
                            TextButton(onClick = { currentIndex-- }) {
                                Text("‹", color = Color.White, fontSize = 40.sp)
                            }
                        } else Spacer(Modifier.width(48.dp))

                        if (currentIndex < imageUrls.lastIndex) {
                            TextButton(onClick = { currentIndex++ }) {
                                Text("›", color = Color.White, fontSize = 40.sp)
                            }
                        } else Spacer(Modifier.width(48.dp))
                    }
                }
            }

            // Close button  (mirrors Swift xmark.circle.fill)
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(30.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        downloadImage(imageUrls[currentIndex])
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Save",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text("Save", color = Color.White, fontSize = 14.sp)
                }

                // Share action
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable {
                        // Platform-specific share sheet should be triggered here
                        // via expect/actual or a platform callback passed in.
                        shareImage(imageUrls[currentIndex])

                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Link,
                        contentDescription = "Share",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text("Share", color = Color.White, fontSize = 14.sp)
                }
            }
        }
    }
}