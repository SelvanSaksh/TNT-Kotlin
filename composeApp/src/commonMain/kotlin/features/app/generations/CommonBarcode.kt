package features.app.generations

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import core.network.models.AuditLogRequest
import core.network.repository.AppRepository
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import core.network.models.AuditDetails
import core.network.models.LocationDetailsPayload
import core.storage.SessionManager
import core.storage.getLocalStorage
import utils.DeviceLocationProvider
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val Brand       = Color(0xFF133D63)
private val BrandLight  = Color(0xFFE8EFF7)
private val BrandBorder = Color(0xFFB8CCE0)

private data class SizeOption(val label: String, val px: Int)
private val sizeOptions = listOf(
    SizeOption("100", 100),
    SizeOption("150", 150),
    SizeOption("200", 200),
    SizeOption("250", 250),
)
@Serializable
private data class GenResponse(val filename: String? = null)

private val client = HttpClient {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
}

private const val GEN_URL      = "https://dlhub.8aiku.com/gen/gen-barcode"
private const val DOWNLOAD_URL = "https://dlhub.8aiku.com/gen/download-image"

@OptIn(ExperimentalTime::class)
@Composable
fun CommonBarcodeScreen(
    barcodeType: DynamicBarcodeType,
    onBack: () -> Unit,
    onShare: ((url: String) -> Unit)? = null,
    onSave:  ((url: String) -> Unit)? = null,
) {
    var input            by remember { mutableStateOf("") }
    var selectedSize     by remember { mutableStateOf(sizeOptions[0]) }
    var isLoading        by remember { mutableStateOf(false) }
    var imageUrl         by remember { mutableStateOf<String?>(null) }
    var errorMsg         by remember { mutableStateOf<String?>(null) }
    val scope            = rememberCoroutineScope()
    val sessionManager = SessionManager(getLocalStorage())
    val locationProvider = remember { DeviceLocationProvider() }

    val canGenerate = input.isNotBlank() && !isLoading

    fun generate() {
        val valid = when {
            barcodeType.maxLength != null ->
                input.length == barcodeType.maxLength &&
                        (!barcodeType.numericOnly || input.all { it.isDigit() })
            else -> input.isNotBlank()
        }

        if (!valid) {
            errorMsg = "Invalid input for ${barcodeType.displayName}"
            return
        }


        scope.launch {
            isLoading = true
            errorMsg = null

            try {
                // ✅ 1. Generate Barcode
                val encoded = input
                    .replace(" ", "%20")
                    .replace("&", "%26")
                    .replace("=", "%3D")
                    .replace("#", "%23")

                val url = "$GEN_URL?bc_type=${barcodeType.apiType}" +
                        "&data=$encoded" +
                        "&width=${selectedSize.px}" +
                        "&height=${selectedSize.px}"

                val res = client.get(url).body<GenResponse>()

                if (res.filename == null) {
                    errorMsg = "Generation failed"
                    return@launch
                }

                val finalImageUrl =
                    "$DOWNLOAD_URL?folder_variable=TMP_IMAGE_FOLDER&filename=${res.filename}"

                imageUrl = finalImageUrl

                // ✅ 2. Get Location
                var lat = 0.0
                var lon = 0.0
                var city: String? = null
                var state: String? = null

                var locationPair: Pair<Double, Double>? = null

                repeat(3) {
                    locationPair = locationProvider.getCurrentLocation()
                    println("📍 Attempt ${it + 1}: $locationPair")

                    if (locationPair != null) return@repeat
                    kotlinx.coroutines.delay(1000)
                }

                println("location: $locationPair")
                if (locationPair != null) {
                    lat = locationPair.first
                    lon = locationPair.second

                    println("generate: $lat, $lon")
                    val locationResult = AppRepository.getLocationDetails(lat, lon)

                    locationResult.onSuccess { locationData ->

                        city  = locationData.city ?: "Unknown"
                        state = locationData.state ?: "Unknown"
                        print("📍 LOCATION FETCH SUCCESS: $locationData")
                        println("🏙️ CITY: $city")
                        println("🌍 STATE: $state")

                    }.onFailure {
                        println("❌ LOCATION FETCH FAILED: ${it.message}")

                        // fallback (important)
                        city = "Unknown"
                        state = "Unknown"
                    }
                }

                val companyId = sessionManager.getCompanyId()

                if (companyId.isNullOrEmpty()) {
                    println("❌ COMPANY ID MISSING")
                    return@launch
                }


                val auditRequest = AuditLogRequest(
                    type = 1,
                    company_id = sessionManager.getCompanyId() ?: "",
                    user_id = sessionManager.getUserId()?.toString() ?: "",

                    location_details = LocationDetailsPayload(
                        lat = lat,
                        long = lon,
                        currentCity = city ?: "Unknown",
                        state = state ?: "Unknown"
                    ),

                    details = AuditDetails(
                        barcode = input,
                        status = "generated",
                        barcodeType = barcodeType.displayName,
                        device = "Android",
                        timestamp = Clock.System.now().toString()
                    )
                )

                println("🚀 AUDIT REQUEST:")
                println("barcode: ${input}")
                println("type: ${barcodeType.displayName}")
                println("lat: $lat, lon: $lon")

                // ✅ 4. Send Audit Log
                val result = AppRepository.sendAuditLog(auditRequest)

                if (result.isSuccess) {
                    println("✅ AUDIT SUCCESS")
                } else {
                    println("❌ AUDIT FAILED: ${result.exceptionOrNull()?.message}")
                }

            } catch (e: Exception) {
                e.printStackTrace()
                errorMsg = e.message ?: "Unexpected error"
            } finally {
                isLoading = false
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {

        Spacer(Modifier.height(25.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(24.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Brand)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(barcodeType.displayName, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Brand)
                Text(barcodeType.description, fontSize = 12.sp, color = Brand.copy(alpha = 0.5f))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            SectionCard {
                Text("Enter Data", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Brand)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { raw ->
                        val filtered = if (barcodeType.numericOnly) raw.filter { it.isDigit() } else raw
                        input = if (barcodeType.maxLength != null) filtered.take(barcodeType.maxLength) else filtered
                        errorMsg = null
                    },
                    placeholder = { Text(barcodeType.placeholder, color = Color.Gray.copy(alpha = 0.6f)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (barcodeType.numericOnly) KeyboardType.Number else KeyboardType.Text,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = Brand,
                        unfocusedBorderColor = BrandBorder,
                        focusedTextColor     = Brand,
                        unfocusedTextColor   = Brand,
                        cursorColor          = Brand,
                    ),
                )
                // char counter for fixed-length types
                if (barcodeType.maxLength != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            "${input.length} / ${barcodeType.maxLength}",
                            fontSize = 11.sp,
                            color = if (input.length == barcodeType.maxLength) Brand else Color.Gray,
                        )
                    }
                }
            }

            SectionCard {
                Text("Output Size", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Brand)
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    sizeOptions.forEach { size ->
                        val isSelected = selectedSize == size
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Brand else BrandLight)
                                .border(
                                    width = if (isSelected) 0.dp else 1.dp,
                                    color = BrandBorder,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .clickable { selectedSize = size }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    size.label,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = if (isSelected) Color.White else Brand,
                                )
                                Text(
                                    "${size.px}px",
                                    fontSize = 10.sp,
                                    color = if (isSelected) Color.White.copy(alpha = 0.7f) else Brand.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = errorMsg != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFEE2E2))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                    Text(errorMsg ?: "", fontSize = 13.sp, color = Color(0xFFDC2626))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(if (canGenerate) 4.dp else 0.dp, RoundedCornerShape(27.dp))
                    .clip(RoundedCornerShape(27.dp))
                    .background(if (canGenerate) Brand else Brand.copy(alpha = 0.4f))
                    .clickable(enabled = canGenerate) { generate() },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.QrCode, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Text(
                        if (isLoading) "Generating…" else "Generate ${barcodeType.displayName}",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }

            AnimatedVisibility(
                visible = imageUrl != null,
                enter   = fadeIn() + slideInVertically { it / 2 },
            ) {
                SectionCard {
                    // Section label
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(3.dp).height(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Brand)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Preview", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Brand)
                        Spacer(Modifier.weight(1f))
                        // size badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(BrandLight)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                "${selectedSize.label}  •  ${selectedSize.px}×${selectedSize.px}px",
                                fontSize = 11.sp,
                                color = Brand,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Image box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(BrandLight)
                            .border(1.dp, BrandBorder, RoundedCornerShape(14.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
//                         ↓ Replace with AsyncImage (Coil / Kamel):
                         AsyncImage(
                             model = imageUrl,
                             contentDescription = "Barcode",
                             modifier = Modifier.fillMaxSize(),
                             contentScale = ContentScale.Fit,
                         )
/*                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(barcodeType.icon, contentDescription = null, tint = Brand, modifier = Modifier.size(72.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(imageUrl ?: "", fontSize = 9.sp, color = Brand.copy(alpha = 0.4f))
                        }*/
                    }

                    Spacer(Modifier.height(14.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Share
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(BrandLight)
                                .border(1.dp, BrandBorder, RoundedCornerShape(12.dp))
                                .clickable { onShare?.invoke(imageUrl ?: "") },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, tint = Brand, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share", color = Brand, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                        // Save
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brand)
                                .clickable { onSave?.invoke(imageUrl ?: "") },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
            .border(1.dp, BrandBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}