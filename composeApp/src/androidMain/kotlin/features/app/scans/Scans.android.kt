package features.app.scans

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.scanner_sdk.customview.ScanMode
import com.example.scanner_sdk.customview.authandsingle.CommonScannerView
import com.example.scanner_sdk.customview.model.ScannerConfig
import com.example.scanner_sdk.customview.single.ScannerController
import core.location.AppLocationCache
import core.network.repository.AppRepository
import core.util.AuditLogHelper
import core.util.ScanAuditLog
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import network.AUTH_TOKEN
import dialog.AuthenticProductDialog
import dialog.Gs1Field
import dialog.ScanResult
import dialog.inferBarcodeTypeFromRaw
import dialog.parseScanResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import utils.DeviceLocationProvider
import kotlin.time.ExperimentalTime

private fun scanModeLabel(verifyAuthenticity: Boolean, isMultiScan: Boolean): String = when {
    isMultiScan && verifyAuthenticity -> "MULTI_AUTH"
    isMultiScan -> "MULTI"
    verifyAuthenticity -> "VERIFY"
    else -> "SINGLE"
}

private fun buildGs1FieldsFromTriple(gtin: String, serial: String, batch: String): List<Gs1Field> {
    val out = mutableListOf<Gs1Field>()
    if (gtin.isNotBlank()) out.add(Gs1Field("01", "GTIN", gtin))
    if (batch.isNotBlank()) out.add(Gs1Field("10", "Batch / Lot", batch))
    if (serial.isNotBlank()) out.add(Gs1Field("21", "Serial number", serial))
    return out
}

@OptIn(ExperimentalTime::class)
@Composable
actual fun ScannerView(
    verifyAuthenticity: Boolean,
    isMultiScan: Boolean,
    onScanResult: (String) -> Unit,
    onNavigate: (String) -> Unit
) {
    val scanMode = scanModeLabel(verifyAuthenticity, isMultiScan)
    val sessionManager = remember { SessionManager(getLocalStorage()) }
    val locationProvider = remember { DeviceLocationProvider() }

    val scope = rememberCoroutineScope()

    LaunchedEffect(sessionManager) {
        AUTH_TOKEN = sessionManager.getAccessToken()
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 👇 KMP SAFE — get FragmentManager from Android Activity
    val fragmentManager =
        (context as androidx.fragment.app.FragmentActivity).supportFragmentManager

    var controller by remember { mutableStateOf<ScannerController?>(null) }
    var scanDialogResult by remember { mutableStateOf<ScanResult?>(null) }
    var scanDialogRaw by remember { mutableStateOf("") }
    var showScanResultDialog by remember { mutableStateOf(false) }
    var rawData by remember { mutableStateOf("") }
    var sdkScanError by remember { mutableStateOf<String?>(null) }

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
     * Supports two response shapes:
     *
     *  Legacy nested:
     *    [{ "gs1_data": { "01": {"name":"GTIN","value":"..."},
     *                     "21": {"name":"Serial","value":"..."},
     *                     "10": {"name":"Batch/Lot","value":"..."} } }]
     *
     *  Current flat AI list:
     *    [{"ai":"01","description":"GTIN","value":"..."},
     *     {"ai":"10","description":"Batch/Lot Number","value":"..."},
     *     {"ai":"21","description":"Serial Number","value":"..."}]
     *
     * Also falls back to parsing AIs from a parenthesized GS1 URL string when
     * the SDK returned nothing structured (e.g. raw barcode in SINGLE mode).
     */
    fun extractGs1Identifiers(scannedValue: String): Triple<String, String, String> {
        val trimmed = scannedValue.trim()
        return try {
            when {
                trimmed.startsWith("[") -> {
                    val arr = JSONArray(trimmed)
                    if (arr.length() == 0) return Triple("", "", "")
                    val first = arr.optJSONObject(0) ?: return Triple("", "", "")

                    // Current flat shape: [{"ai":..., "value":...}, ...]
                    if (first.has("ai") && first.has("value")) {
                        var gtin = ""
                        var serial = ""
                        var batch = ""
                        for (i in 0 until arr.length()) {
                            val item = arr.optJSONObject(i) ?: continue
                            when (item.optString("ai")) {
                                "01" -> gtin = item.optString("value")
                                "21" -> serial = item.optString("value")
                                "10" -> batch = item.optString("value")
                            }
                        }
                        return Triple(gtin, serial, batch)
                    }

                    // Legacy nested shape
                    val gs1 = first.optJSONObject("gs1_data") ?: return Triple("", "", "")
                    val gtin = gs1.optJSONObject("01")?.optString("value").orEmpty()
                    val serial = gs1.optJSONObject("21")?.optString("value").orEmpty()
                    val batch = gs1.optJSONObject("10")?.optString("value").orEmpty()
                    Triple(gtin, serial, batch)
                }
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    val gs1 = obj.optJSONObject("gs1_data") ?: return Triple("", "", "")
                    val gtin = gs1.optJSONObject("01")?.optString("value").orEmpty()
                    val serial = gs1.optJSONObject("21")?.optString("value").orEmpty()
                    val batch = gs1.optJSONObject("10")?.optString("value").orEmpty()
                    Triple(gtin, serial, batch)
                }
                else -> Triple("", "", "")
            }
        } catch (_: Exception) {
            Triple("", "", "")
        }
    }

    /**
     * Parses AIs directly from a GS1 Digital Link URL or parenthesized element
     * string, e.g. `https://dl.ratifye.ai/01/18907001962025/10/GTG1897A?...`
     * or `(01)18907001962025(10)GTG1897A(21)SN001`.
     *
     * Used as a fallback when the SDK didn't deliver a structured payload.
     */
    fun extractGs1IdentifiersFromUrl(raw: String): Triple<String, String, String> {
        if (raw.isBlank()) return Triple("", "", "")
        var gtin = ""
        var serial = ""
        var batch = ""

        // Path-style: /01/<gtin>/10/<batch>/21/<serial>
        Regex("""/01/([^/?#]+)""").find(raw)?.groupValues?.getOrNull(1)?.let { gtin = it }
        Regex("""/10/([^/?#]+)""").find(raw)?.groupValues?.getOrNull(1)?.let { batch = it }
        Regex("""/21/([^/?#]+)""").find(raw)?.groupValues?.getOrNull(1)?.let { serial = it }

        // Parenthesized: (01)<gtin>(10)<batch>(21)<serial>
        if (gtin.isBlank()) Regex("""\(01\)([^()]+)""").find(raw)?.groupValues?.getOrNull(1)?.let { gtin = it.trim() }
        if (batch.isBlank()) Regex("""\(10\)([^()]+)""").find(raw)?.groupValues?.getOrNull(1)?.let { batch = it.trim() }
        if (serial.isBlank()) Regex("""\(21\)([^()]+)""").find(raw)?.groupValues?.getOrNull(1)?.let { serial = it.trim() }

        return Triple(gtin, serial, batch)
    }

    fun presentParsedScanResult(raw: String, sdkJson: JSONArray?) {
        val jsonPayload: String = sdkJson?.toString().orEmpty().trim().let { j ->
            if (j.isNotEmpty() && j != "null") j else ""
        }
        val scannedValue: String = if (jsonPayload.isNotBlank()) jsonPayload else raw
        val (payloadType, payloadData) = extractScanPayload(scannedValue)
        val (jGtin, jSerial, jBatch) = extractGs1Identifiers(scannedValue)
        val (uGtin, uSerial, uBatch) = extractGs1IdentifiersFromUrl(raw)
        val mergedGtin = if (jGtin.isNotBlank()) jGtin else uGtin
        val mergedSerial = if (jSerial.isNotBlank()) jSerial else uSerial
        val mergedBatch = if (jBatch.isNotBlank()) jBatch else uBatch
        val fallbackGs1 = buildGs1FieldsFromTriple(mergedGtin, mergedSerial, mergedBatch)

        val parsed = when {
            jsonPayload.isNotEmpty() -> parseScanResponse(jsonPayload, raw)
            else -> parseScanResponse("", raw)
        }

        val mergedFields = when {
            parsed != null && parsed.gs1Fields.isNotEmpty() -> parsed.gs1Fields
            else -> fallbackGs1
        }

        scanDialogResult = if (parsed != null) {
            val mergedBarcodeData = when {
                parsed.barcodeData.isNotBlank() -> parsed.barcodeData
                payloadData.isNotBlank() -> payloadData
                else -> raw
            }
            val mergedBarcodeType = when {
                parsed.barcodeType.isNotBlank() -> parsed.barcodeType
                payloadType.isNotBlank() -> payloadType
                else -> inferBarcodeTypeFromRaw(raw)
            }
            parsed.copy(
                rawBarcode = raw,
                barcodeData = mergedBarcodeData,
                barcodeType = mergedBarcodeType,
                gs1Fields = mergedFields,
            )
        } else {
            val barcodeData = if (payloadData.isNotBlank()) payloadData else raw
            val barcodeType =
                if (payloadType.isNotBlank()) payloadType else inferBarcodeTypeFromRaw(raw)
            ScanResult(
                barcodeData = barcodeData,
                gs1Fields = mergedFields,
                encryptedText = "",
                quality = "",
                rawBarcode = raw,
                barcodeType = barcodeType,
            )
        }
        scanDialogRaw = raw
        showScanResultDialog = true
    }

    suspend fun submitScanAuditLog(scannedValue: String, epcCandidate: String, mode: String) {
        val tag = "SCAN_AUDIT"
        try {
            AUTH_TOKEN = sessionManager.getAccessToken()
            val loggedIn = sessionManager.isLoggedIn()
            val userId = sessionManager.getUserId() ?: 0
            val companyId = AuditLogHelper.resolveCompanyId(sessionManager)
            Log.i(tag, "[$mode] audit START loggedIn=$loggedIn userId=$userId companyId=$companyId hasToken=${!AUTH_TOKEN.isNullOrBlank()}")

            AppLocationCache.restoreFrom(sessionManager)
            withTimeoutOrNull(4_000L) {
                AppLocationCache.ensureFresh(locationProvider)
                AppLocationCache.persistTo(sessionManager)
            }
            Log.i(
                tag,
                "[$mode] location lat=${AppLocationCache.latitude} lon=${AppLocationCache.longitude} geo=${AppLocationCache.geoLocation}",
            )

            val (parsedGtin, parsedSerial, parsedBatch) = extractGs1Identifiers(scannedValue)
            val (urlGtin, urlSerial, urlBatch) = extractGs1IdentifiersFromUrl(epcCandidate)
            val gtin = if (parsedGtin.isNotBlank()) parsedGtin else urlGtin
            val serial = if (parsedSerial.isNotBlank()) parsedSerial else urlSerial
            val batch = if (parsedBatch.isNotBlank()) parsedBatch else urlBatch
            val epcId = when {
                gtin.isNotBlank() -> gtin
                epcCandidate.isNotBlank() -> epcCandidate
                else -> scannedValue.trim()
            }
            val payloadSource = if (epcCandidate.isNotBlank()) epcCandidate else scannedValue
            val (barcodeType, barcodeData) = extractScanPayload(payloadSource)

            val isAuthFlow = mode.equals("VERIFY", ignoreCase = true) ||
                mode.equals("AUTH", ignoreCase = true) ||
                mode.equals("MULTI_AUTH", ignoreCase = true)

            val scanRequest = AuditLogHelper.buildScanLogRequest(
                sessionManager = sessionManager,
                epcId = epcId,
                barcodeType = barcodeType,
                barcodeData = barcodeData,
                gtin = gtin,
                serial = serial,
                batch = batch,
                isAuthFlow = isAuthFlow,
                scannedValue = scannedValue,
            )

            Log.i(tag, "[$mode] POST body=${ScanAuditLog.formatRequestBody(scanRequest)}")
            val result = AppRepository.sendScanCreateLog(scanRequest)
            if (result.isSuccess) {
                Log.i(tag, "[$mode] audit OK epc_id=$epcId")
            } else {
                Log.e(tag, "[$mode] audit FAILED ${result.exceptionOrNull()?.message}", result.exceptionOrNull())
            }
        } catch (e: Exception) {
            Log.e(tag, "[$mode] audit crashed: ${e.message}", e)
            ScanAuditLog.line("[$mode] audit crashed: ${e.message}")
        }
    }

    fun handleScanFromSdk(raw: String, sdkJson: JSONArray?) {
        val jsonPayload = sdkJson?.toString().orEmpty()
        val scannedForLog = jsonPayload.trim().let { j ->
            if (j.isNotEmpty() && j != "null") j else raw
        }
        Log.d("SCAN_DETAIL", "========== SCAN [$scanMode] ========== rawLen=${raw.length}")

        scope.launch {
            launch(Dispatchers.IO) {
                submitScanAuditLog(
                    scannedValue = scannedForLog,
                    epcCandidate = raw,
                    mode = scanMode,
                )
            }
            rawData = raw
            onScanResult(scannedForLog)
            presentParsedScanResult(raw, sdkJson)
            delay(2000)
            controller?.shouldResumeScanning = true
        }
    }

    val onScanFromSdk = rememberUpdatedState(newValue = ::handleScanFromSdk)

    val userId = sessionManager.getUserId()?.toString().orEmpty().ifBlank { "0" }
    val companyId = sessionManager.getCompanyId()?.trim().orEmpty().ifBlank { "0" }

    val scannerConfig = ScannerConfig(
        scanMode = if (isMultiScan) ScanMode.MULTI else ScanMode.SINGLE,
        verifyAuthenticity = verifyAuthenticity,
    )

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        controller?.processGalleryImage(
            context = context,
            uri = uri,
            userId = userId,
            companyId = companyId,
        )
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            Log.d(
                "SCANNERLOG",
                "[$scanMode] ▶ CommonScannerView factory scanMode=${scannerConfig.scanMode} verifyAuth=${scannerConfig.verifyAuthenticity}"
            )
            val view = CommonScannerView(ctx)
            controller = ScannerController(
                config = scannerConfig,
                commonScannerView = view,
                lifecycleOwner = lifecycleOwner,
                fragmentManager = fragmentManager,
                openGallery = { galleryLauncher.launch("image/*") },
                result = { scanResult ->
                    onScanFromSdk.value(scanResult.first, scanResult.second)
                },
                error = { err ->
                    Log.e(
                        "SCANNERLOG",
                        "[$scanMode] ❌ SDK error code=${err.first} message=${err.second}",
                    )
                    sdkScanError = "Code ${err.first}: ${err.second}"
                    scope.launch {
                        delay(3000)
                        controller?.shouldResumeScanning = true
                    }
                }
            )
            Log.d("SCANNERLOG", "[$scanMode] ▶ startCommonScanner(userId=$userId, companyId=$companyId)")
            controller?.startCommonScanner(ctx, userId, companyId)
            view
        }
    )

    if (showScanResultDialog) {
        scanDialogResult?.let { result ->
            AuthenticProductDialog(
                raw = scanDialogRaw,
                result = result,
                onDismiss = {
                    showScanResultDialog = false
                    scanDialogResult = null
                },
                onContinue = {
                    showScanResultDialog = false
                    scanDialogResult = null
                },
                onLinkClick = { url ->
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    context.startActivity(intent)
                },
            )
        }
    }

    sdkScanError?.let { message ->
        AlertDialog(
            onDismissRequest = { sdkScanError = null },
            title = { Text("Scanner") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { sdkScanError = null }) {
                    Text("OK")
                }
            },
        )
    }

    DisposableEffect(verifyAuthenticity, isMultiScan) {
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
