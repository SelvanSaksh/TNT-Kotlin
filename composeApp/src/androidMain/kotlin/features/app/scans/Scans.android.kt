package features.app.scans

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.scanner_sdk.customview.auth.AuthScannerView
import com.example.scanner_sdk.customview.authandsingle.CommonScannerView
import com.example.scanner_sdk.customview.authandsingle.VerificationScannerView
import com.example.scanner_sdk.customview.multi.view.MultiScannerView
import com.example.scanner_sdk.customview.single.ScannerController
import com.example.scanner_sdk.customview.single.view.SingleScannerView
import core.network.models.ScanLogCreateRequest
import core.network.repository.AppRepository
import core.storage.SessionManager
import core.storage.getLocalStorage
import dialog.AuthenticProductDialog
import dialog.parseScanResponse
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import utils.DeviceLocationProvider
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
actual fun ScannerView(
    scanMode: String,
    onScanResult: (String) -> Unit,
    onNavigate: (String) -> Unit
) {

    val sessionManager = SessionManager(getLocalStorage())
    val locationProvider = DeviceLocationProvider()

    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 👇 KMP SAFE — get FragmentManager from Android Activity
    val fragmentManager =
        (context as androidx.fragment.app.FragmentActivity).supportFragmentManager

    var controller by remember { mutableStateOf<ScannerController?>(null) }
    val jsonResponse = remember { mutableStateOf<String?>(null) }
    var dialogTrigger by remember { mutableStateOf(0) }
    var rawData by remember { mutableStateOf("") }

    fun normalizeBarcodeType(rawType: String): String {
        val t = rawType.trim().uppercase()
        return when {
            "128" in t || "CODE128" in t -> "CODE128"
            "EAN13" in t || "EAN-13" in t -> "EAN13"
            "EAN8" in t || "EAN-8" in t -> "EAN8"
            "DATAMATRIX" in t || "DATA_MATRIX" in t -> "DATAMATRIX"
            "QR" in t -> "QR"
            else -> "QR"
        }
    }

    fun extractScanPayload(scannedValue: String): Pair<String, String> {
        val trimmed = scannedValue.trim()
        return try {
            when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    if (arr.length() == 0) {
                        "QR" to trimmed
                    } else {
                        val obj = arr.getJSONObject(0)
                        val data = obj.optString("barcode_data")
                            .ifBlank { obj.optString("data") }
                            .ifBlank { obj.optString("raw") }
                            .ifBlank { trimmed }
                        val rawType = obj.optString("barcode_type")
                            .ifBlank { obj.optString("format") }
                            .ifBlank { obj.optString("symbology") }
                            .ifBlank {
                                if (data.startsWith("http", ignoreCase = true)) "QR" else "CODE128"
                            }
                        normalizeBarcodeType(rawType) to data
                    }
                }
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    val data = obj.optString("barcode_data")
                        .ifBlank { obj.optString("data") }
                        .ifBlank { obj.optString("raw") }
                        .ifBlank { trimmed }
                    val rawType = obj.optString("barcode_type")
                        .ifBlank { obj.optString("format") }
                        .ifBlank { obj.optString("symbology") }
                        .ifBlank {
                            if (data.startsWith("http", ignoreCase = true)) "QR" else "CODE128"
                        }
                    normalizeBarcodeType(rawType) to data
                }
                else -> {
                    val inferredType = if (trimmed.startsWith("http", ignoreCase = true)) "QR" else "CODE128"
                    inferredType to trimmed
                }
            }
        } catch (_: Exception) {
            val inferredType = if (trimmed.startsWith("http", ignoreCase = true)) "QR" else "CODE128"
            inferredType to trimmed
        }
    }

    /**
     * Extracts the GS1 identifiers (GTIN / serial / batch) from a SDK scan response.
     *
     * Response shape (per [parseScanResponse]):
     *   [{ "gs1_data": { "01": {"name":"GTIN","value":"..."},
     *                    "21": {"name":"Serial","value":"..."},
     *                    "10": {"name":"Batch/Lot","value":"..."} }, ... }]
     */
    fun extractGs1Identifiers(scannedValue: String): Triple<String, String, String> {
        val trimmed = scannedValue.trim()
        return try {
            val obj: JSONObject? = when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    if (arr.length() == 0) null else arr.optJSONObject(0)
                }
                trimmed.startsWith("{") -> JSONObject(trimmed)
                else -> null
            }
            val gs1 = obj?.optJSONObject("gs1_data") ?: return Triple("", "", "")

            val gtin = gs1.optJSONObject("01")?.optString("value").orEmpty()
            val serial = gs1.optJSONObject("21")?.optString("value").orEmpty()
            val batch = gs1.optJSONObject("10")?.optString("value").orEmpty()
            Triple(gtin, serial, batch)
        } catch (_: Exception) {
            Triple("", "", "")
        }
    }

    fun sendScanLog(scannedValue: String, epcCandidate: String, scanMode: String) {
        scope.launch {
            val tag = "SCANNERLOG"
            Log.d(
                tag,
                "[$scanMode] sendScanLog() ▶ START epcCandidate='${epcCandidate.take(60)}' scannedValueLen=${scannedValue.length} scannedValuePreview='${scannedValue.take(120)}'"
            )

            var lat = 0.0
            var lon = 0.0

            Log.d(tag, "[$scanMode] sendScanLog() ▶ requesting current location…")
            val locationPair = locationProvider.getCurrentLocation()
            if (locationPair != null) {
                lat = locationPair.first
                lon = locationPair.second
                Log.d(tag, "[$scanMode] sendScanLog() ✓ location lat=$lat lon=$lon")

                val locationResult = AppRepository.getLocationDetails(lat, lon)
                locationResult
                    .onSuccess { loc ->
                        Log.d(
                            tag,
                            "[$scanMode] sendScanLog() ✓ reverse-geocoded city=${loc.city} state=${loc.state} country=${loc.country}"
                        )
                    }
                    .onFailure { err ->
                        Log.w(tag, "[$scanMode] sendScanLog() ⚠ reverse-geocode failed: ${err.message}")
                    }
            } else {
                Log.w(tag, "[$scanMode] sendScanLog() ⚠ location unavailable, defaulting lat=0 lon=0")
            }

            val companyId = sessionManager.getCompanyId()?.toIntOrNull()
                ?: run {
                    // Fallback: company id is also inside stored user_detail JSON as "companyid"
                    val ud = sessionManager.getUserDetail().orEmpty()
                    Log.d(
                        tag,
                        "[$scanMode] sendScanLog() ▶ companyId missing in session, falling back to user_detail JSON (len=${ud.length})"
                    )
                    runCatching {
                        JSONObject(ud).optInt("companyid", 0)
                    }.getOrNull()?.takeIf { it > 0 }
                }
                ?: run {
                    Log.e("ScanLog", "[$scanMode] ❌ Log skipped: companyId missing")
                    return@launch
                }
            Log.d(tag, "[$scanMode] sendScanLog() ✓ companyId=$companyId")

            val (gtin, serial, batch) = extractGs1Identifiers(scannedValue)
            // Fall back to the raw scanned value when GS1 parsing yields no GTIN
            // (e.g. plain SINGLE-mode barcodes like "ABC-1234" without gs1_data).
            val epcId = gtin.ifBlank { epcCandidate.ifBlank { scannedValue.trim() } }
            val geoLocation = "$lat,$lon"
            Log.d(
                tag,
                "[$scanMode] sendScanLog() ✓ parsed gs1 gtin='$gtin' serial='$serial' batch='$batch' → epc_id='$epcId'"
            )

            val isAuthFlow = scanMode.equals("VERIFY", ignoreCase = true) ||
                    scanMode.equals("AUTH", ignoreCase = true)

            val scanRequest = ScanLogCreateRequest(
                event_type = if (isAuthFlow) "AUTHENTICATE" else "SCAN",
                epc_id = epcId,
                event_time = Clock.System.now().toString(),
                biz_step = "urn:epcglobal:cbv:bizstep:receiving",
                biz_location = "urn:epc:id:sgln:0000123.00000.0",
                geo_location = geoLocation,
                auth_result = if (isAuthFlow) "AUTHENTIC" else "UNKNOWN",
                scanner_id = "android_${scanMode.lowercase()}",
                signature = "0xandroid",
                company_id = companyId,
                serial = serial,
                batch = batch,
                device_type = "android"
            )

            Log.d(
                tag,
                "[$scanMode] sendScanLog() ▶ POSTing /companies/barcode/create event_type=${scanRequest.event_type} auth_result=${scanRequest.auth_result} scanner_id=${scanRequest.scanner_id} epc_id=${scanRequest.epc_id} serial=${scanRequest.serial} batch=${scanRequest.batch} geo_location=${scanRequest.geo_location} companyId=${scanRequest.company_id}"
            )

            val result = AppRepository.sendScanCreateLog(scanRequest)
            if (result.isSuccess) {
                Log.d(
                    "ScanLog",
                    "[$scanMode] ✅ Logged scan epc_id=$epcId serial=$serial batch=$batch"
                )
                Log.d(tag, "[$scanMode] sendScanLog() ◀ DONE success")
            } else {
                val err = result.exceptionOrNull()
                Log.e(
                    "ScanLog",
                    "[$scanMode] ❌ Log failed: ${err?.message}",
                    err
                )
                Log.d(tag, "[$scanMode] sendScanLog() ◀ DONE failure")
            }
        }
    }
    // ✅ Step 1 — Declare the launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        controller?.processGalleryImage(
            context = context,
            uri = uri,
            userId = "1",
            companyId = "48",
        )
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->

            when (scanMode) {

                "VERIFY" -> {
                    Log.d(
                        "SCANNERLOG",
                        "[VERIFY] ▶ ScannerView factory CALLED — building CommonScannerView (authentication flow)"
                    )
                    val view = CommonScannerView(ctx)
                    Log.d("SCANNERLOG", "[VERIFY] ✓ CommonScannerView instantiated, wiring ScannerController…")
                    controller = ScannerController(
                        singleScannerView = null,
                        verificationScanner = null,
                        commonScannerView = view,
                        multiScanner = null,
                        authScannerView = null,
                        lifecycleOwner = lifecycleOwner,
                        fragmentManager = fragmentManager,
                        openGallery = {
                            Log.d("SCANNERLOG", "[VERIFY] ▶ openGallery requested by SDK")
                            galleryLauncher.launch("image/*")
                        },
                        result = { scanResult ->

                            rawData = scanResult.first
                            val scannedValue = scanResult.second.toString()
                            Log.d(
                                "SCANNERLOG",
                                "[VERIFY] ✓ SDK result callback fired rawDataLen=${rawData.length} rawData='${rawData.take(80)}' scannedValueLen=${scannedValue.length} scannedValuePreview='${scannedValue.take(120)}'"
                            )
                            println("📦 SCANNED VALUE: $scannedValue")

                            onScanResult(scannedValue)
                            Log.d("SCANNERLOG", "[VERIFY] ▶ forwarding to sendScanLog()")
                            sendScanLog(
                                scannedValue = scannedValue,
                                epcCandidate = rawData,
                                scanMode = scanMode
                            )

                            jsonResponse.value = scannedValue
                            dialogTrigger++
                            Log.d(
                                "SCANNERLOG",
                                "[VERIFY] ✓ AuthenticProductDialog trigger incremented (dialogTrigger=$dialogTrigger)"
                            )
                        },

//                        result = {
//                            jsonResponse.value = it.toString()
//                            showAuthDialog.value = true
//                        },
                        error = { err ->
                            Log.e(
                                "SCANNERLOG",
                                "[VERIFY] ❌ SDK error callback code=${err.first} message=${err.second}"
                            )
                            Toast.makeText(context, "Error data received : ${err.second}", Toast.LENGTH_SHORT).show()
                        }
                    )

                    Log.d("SCANNERLOG", "[VERIFY] ▶ controller.startCommonScanner(userId=1, companyId=48)")
                    controller?.startCommonScanner(ctx, "1", "48")
                    Log.d("SCANNERLOG", "[VERIFY] ✓ startCommonScanner() returned, view ready")
                    view
                }

                "SINGLE" -> {
                    Log.d(
                        "SCANNERLOG",
                        "[SINGLE] ▶ ScannerView factory CALLED — building SingleScannerView"
                    )
                    val view = SingleScannerView(ctx)
                    Log.d("SCANNERLOG", "[SINGLE] ✓ SingleScannerView instantiated, wiring ScannerController…")
                    controller = ScannerController(
                        singleScannerView = view,
                        multiScanner = null,
                        authScannerView = null,
                        lifecycleOwner = lifecycleOwner,
                        fragmentManager = fragmentManager,
                        openGallery = {
                            Log.d("SCANNERLOG", "[SINGLE] ▶ openGallery requested by SDK")
                            galleryLauncher.launch("image/*")
                        },
                        result = { scanResult ->
                            rawData = scanResult.first
                            val scannedValue = scanResult.second.toString()
                            Log.d(
                                "SCANNERLOG",
                                "[SINGLE] ✓ SDK result callback fired rawDataLen=${rawData.length} rawData='${rawData.take(80)}' scannedValueLen=${scannedValue.length} scannedValuePreview='${scannedValue.take(120)}'"
                            )
                            onScanResult(scannedValue)
                            Log.d("SCANNERLOG", "[SINGLE] ▶ forwarding to sendScanLog()")
                            sendScanLog(
                                scannedValue = scannedValue,
                                epcCandidate = rawData,
                                scanMode = scanMode
                            )
                        },
                        error = { err ->
                            Log.e(
                                "SCANNERLOG",
                                "[SINGLE] ❌ SDK error callback code=${err.first} message=${err.second}"
                            )
                        }
                    )

                    Log.d("SCANNERLOG", "[SINGLE] ▶ controller.startSingleScanner()")
                    controller?.startSingleScanner(ctx)
                    Log.d("SCANNERLOG", "[SINGLE] ✓ startSingleScanner() returned, view ready")
                    view
                }

                "AUTH" -> {
                    Log.d(
                        "SCANNERLOG",
                        "[AUTH] ▶ ScannerView factory CALLED — building AuthScannerView (authentication flow)"
                    )
                    val view = AuthScannerView(ctx)
                    Log.d("SCANNERLOG", "[AUTH] ✓ AuthScannerView instantiated, wiring ScannerController…")
                    controller = ScannerController(
                        singleScannerView = null,
                        multiScanner = null,
                        authScannerView = view,
                        lifecycleOwner = lifecycleOwner,
                        fragmentManager = fragmentManager,
                        openGallery = {
                            Log.d("SCANNERLOG", "[AUTH] ▶ openGallery requested by SDK")
                            galleryLauncher.launch("image/*")
                        },
                        result = { scanResult ->
                            rawData = scanResult.first
                            val scannedValue = scanResult.second.toString()
                            Log.d(
                                "SCANNERLOG",
                                "[AUTH] ✓ SDK result callback fired rawDataLen=${rawData.length} rawData='${rawData.take(80)}' scannedValueLen=${scannedValue.length} scannedValuePreview='${scannedValue.take(120)}'"
                            )
                            onScanResult(scannedValue)
                            Log.d("SCANNERLOG", "[AUTH] ▶ forwarding to sendScanLog()")
                            sendScanLog(
                                scannedValue = scannedValue,
                                epcCandidate = rawData,
                                scanMode = scanMode
                            )
                        },
                        error = { err ->
                            Log.e(
                                "SCANNERLOG",
                                "[AUTH] ❌ SDK error callback code=${err.first} message=${err.second}"
                            )
                        }
                    )

                    Log.d("SCANNERLOG", "[AUTH] ▶ controller.startAuthScanner(userId='', companyId='')")
                    controller?.startAuthScanner(ctx, "", "")
                    Log.d("SCANNERLOG", "[AUTH] ✓ startAuthScanner() returned, view ready")
                    view
                }

                "MULTI" -> {
                    val view = MultiScannerView(ctx)
                    Log.d("SCANNERLOG", "ScannerView: Multi CALLED//////////////")
                    controller = ScannerController(
                        singleScannerView = null,
                        multiScanner = view,
                        authScannerView = null,
                        lifecycleOwner = lifecycleOwner,
                        fragmentManager = fragmentManager,
                        openGallery = { galleryLauncher.launch("image/*") },
                        result = { scanResult ->
                            rawData = scanResult.first
                            val scannedValue = scanResult.second.toString()
                            Log.d(
                                "SCANNERLOG",
                                "SDK result callback MULTI fired rawData=${rawData.take(60)} scannedValue=${scannedValue.take(80)}"
                            )
                            onScanResult(scannedValue)
                            sendScanLog(
                                scannedValue = scannedValue,
                                epcCandidate = rawData,
                                scanMode = scanMode
                            )
                        },
                        error = {}
                    )

                    controller?.startMultiScanner(ctx, "1", "48")
                    view
                }

                else -> SingleScannerView(ctx)
            }
        }
    )

    if (scanMode == "VERIFY" && dialogTrigger > 0) {
        jsonResponse.value?.let { json ->
            parseScanResponse(jsonString = json)?.let { result ->
                AuthenticProductDialog(
                    raw = rawData,
                    result = result,
                    onDismiss = { dialogTrigger = 0 },
                    onContinue = { dialogTrigger = 0 },
                    onLinkClick = {
                        val intent = Intent(Intent.ACTION_VIEW, it.toUri())
                        context.startActivity(intent)
                    }
                )

            }

        }
    }

    DisposableEffect(scanMode) {
        onDispose {
            controller?.stop()
        }
    }
}


/*@Composable
actual fun ScannerView(
    scanMode: String,
    onScanResult: (String) -> Unit,
    onNavigate: (AppScreen) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 🔥 Hold the actual Android View instance
    var scannerView by remember { mutableStateOf<android.view.View?>(null) }

    // 🔥 Create controller ONLY when scanMode changes
    val controller = remember(scanMode) {
        Log.d("SCANNERLOG", "Controller created for mode: $scanMode")

        when (scanMode) {
            "SINGLE" -> ScannerController(
                singleScannerView = null,
                multiScannerView = null,
                authScannerView = null,
                lifecycleOwner = lifecycleOwner,
                onScanned = onScanResult
            )

            "AUTH" -> ScannerController(
                singleScannerView = null,
                multiScannerView = null,
                authScannerView = null,
                lifecycleOwner = lifecycleOwner,
                onScanned = onScanResult
            )

            "MULTI" -> ScannerController(
                singleScannerView = null,
                multiScannerView = null,
                authScannerView = null,
                lifecycleOwner = lifecycleOwner,
                onScanned = onScanResult
            )

            else -> ScannerController(
                singleScannerView = null,
                multiScannerView = null,
                authScannerView = null,
                lifecycleOwner = lifecycleOwner,
                onScanned = onScanResult
            )
        }
    }

    *//**
     * ⭐ Start / Stop camera cleanly with lifecycle
     *//*
    DisposableEffect(scanMode, scannerView) {

        val view = scannerView

        if (view != null) {
            when (scanMode) {
                "SINGLE" -> {
                    Log.d("SCANNERLOG", "Start SINGLE")
                    controller.apply {
                        val singleView = view as SingleScannerView
                        this.startSingleScanner(context)
                    }
                }

                "AUTH" -> {
                    Log.d("SCANNERLOG", "Start AUTH")
                    controller.apply {
                        val authView = view as AuthScannerView
                        this.startAuthScanner(context)
                    }
                }

                "MULTI" -> {
                    Log.d("SCANNERLOG", "Start MULTI")
                    controller.apply {
                        val multiView = view as MultiScannerView
                        this.startMultiScanner(context)
                    }
                }
            }
        }

        onDispose {
            Log.d("SCANNERLOG", "Dispose scanner")
//            controller.stop() Todo: enable this
        }
    }

    *//**
     * ⭐ AndroidView should ONLY create View
     * NO camera logic here
     *//*

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->

            val view: ConstraintLayout = when (scanMode) {

                "SINGLE" -> {
                    Log.d("SCANNERLOG", "Create SingleScannerView")
                    SingleScannerView(ctx)
                }

                "AUTH" -> {
                    Log.d("SCANNERLOG", "Create AuthScannerView")
                    AuthScannerView(ctx)
                }

                "MULTI" -> {
                    Log.d("SCANNERLOG", "Create MultiScannerView")
                    MultiScannerView(ctx)
                }

                else -> {
                    SingleScannerView(ctx)
                }
            }

            scannerView = view
            view
        }
    )
}*/


/*
package features.app.scans

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.scanner_sdk.customview.auth.AuthScannerView
import com.example.scanner_sdk.customview.multi.view.MultiScannerView
import com.example.scanner_sdk.customview.single.ScannerController
import com.example.scanner_sdk.customview.single.view.SingleScannerView
import navigation.AppScreen

@Composable
actual fun ScannerView(
    scanMode: String,
    onScanResult: (String) -> Unit,
    onNavigate: (AppScreen) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentController by remember { mutableStateOf<ScannerController?>(null) }
    
    // Key ensures view is recreated when scanMode changes
    key(scanMode) {
        DisposableEffect(scanMode) {
            onDispose {
                currentController = null
            }
        }
        
        AndroidView(
            factory = { ctx ->
                when (scanMode) {
                    "SINGLE" -> {
                        Log.d("SCANNERLOG", "setupSingleScanner: CALLED......")
                        val view = SingleScannerView(ctx)
                        currentController = ScannerController(
                            singleScannerView = view,
                            multiScannerView = null,
                            authScannerView = null,
                            lifecycleOwner = lifecycleOwner,
                            onScanned = { data ->
                                onScanResult(data)
                            }
                        )
                        currentController?.startSingleScanner(ctx)
                        view as android.view.View
                    }
                    "AUTH" -> {
                        val view = AuthScannerView(ctx)
                        currentController = ScannerController(
                            singleScannerView = null,
                            multiScannerView = null,
                            authScannerView = view,
                            lifecycleOwner = lifecycleOwner,
                            onScanned = { data ->
                                onScanResult(data)
                            }
                        )
                        currentController?.startAuthScanner(ctx)
                        view as android.view.View
                    }
                    "MULTI" -> {
                        val view = MultiScannerView(ctx)
                        currentController = ScannerController(
                            singleScannerView = null,
                            multiScannerView = view,
                            authScannerView = null,
                            lifecycleOwner = lifecycleOwner,
                            onScanned = { data ->
                                onScanResult(data)
                            }
                        )
                        currentController?.startMultiScanner(ctx)
                        view as android.view.View
                    }
                    else -> {
                        val view = SingleScannerView(ctx)
                        currentController = ScannerController(
                            singleScannerView = view,
                            multiScannerView = null,
                            authScannerView = null,
                            lifecycleOwner = lifecycleOwner,
                            onScanned = { data ->
                                onScanResult(data)
                            }
                        )
                        currentController?.startSingleScanner(ctx)
                        view as android.view.View
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
*/
