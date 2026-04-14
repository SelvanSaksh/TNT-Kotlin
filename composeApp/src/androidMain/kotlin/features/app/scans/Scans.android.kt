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
import com.example.scanner_sdk.customview.authandsingle.VerificationScannerView
import com.example.scanner_sdk.customview.multi.view.MultiScannerView
import com.example.scanner_sdk.customview.single.ScannerController
import com.example.scanner_sdk.customview.single.view.SingleScannerView
import core.network.models.AuditDetails
import core.network.models.AuditLogRequest
import core.network.models.LocationDetailsPayload
import core.network.repository.AppRepository
import core.storage.SessionManager
import core.storage.getLocalStorage
import dialog.AuthenticProductDialog
import dialog.parseScanResponse
import kotlinx.coroutines.launch
import org.json.JSONArray
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
                    val view = VerificationScannerView(ctx)
                    controller = ScannerController(
                        singleScannerView = null,
                        verificationScannerView = view,
                        multiScannerView = null,
                        authScannerView = null,
                        lifecycleOwner = lifecycleOwner,
                        fragmentManager = fragmentManager,
                        openGallery = { galleryLauncher.launch("image/*") },
                        result = { scanResult ->

                            rawData = scanResult.first
                            val scannedValue = scanResult.second.toString()
                            println("📦 SCANNED VALUE: $scannedValue")

                            // Parse JSON
                            val jsonArray = JSONArray(scannedValue)
                            val barcodeData = jsonArray.getJSONObject(0).getString("barcode_data")

                            println("📦 barcodeData VALUE: $barcodeData")

                            jsonResponse.value = scannedValue
                            dialogTrigger++

                            scope.launch {

                                var lat = 0.0
                                var lon = 0.0
                                var city: String? = null
                                var state: String? = null

                                val locationPair = locationProvider.getCurrentLocation()

                                if (locationPair != null) {
                                    lat = locationPair.first
                                    lon = locationPair.second

                                    val locationResult = AppRepository.getLocationDetails(lat, lon)

                                    locationResult.onSuccess {
                                        city = it.city ?: "Unknown"
                                        state = it.state ?: "Unknown"
                                    }.onFailure {
                                        city = "Unknown"
                                        state = "Unknown"
                                    }
                                }

                                val companyId = sessionManager.getCompanyId()
                                if (companyId.isNullOrEmpty()) return@launch

                                val auditRequest = AuditLogRequest(
                                    type = 0,
                                    company_id = companyId,
                                    user_id = sessionManager.getUserId()?.toString() ?: "",
                                    location_details = LocationDetailsPayload(
                                        lat = lat,
                                        long = lon,
                                        currentCity = city,
                                        state = state
                                    ),
                                    details = AuditDetails(
                                        barcode = scannedValue,
                                        status = "scanned",
                                        barcodeType = "Scan",
                                        device = "Android",
                                        timestamp = Clock.System.now().toString()
                                    )
                                )

                                AppRepository.sendAuditLog(auditRequest)
                            }
                        },

//                        result = {
//                            jsonResponse.value = it.toString()
//                            showAuthDialog.value = true
//                        },
                        error = {
                            Toast.makeText(context, "Error data received : ${it.second}", Toast.LENGTH_SHORT).show()
                        }
                    )

                    controller?.startVerifyScanner(ctx, "1", "48")
                    view
                }

                "SINGLE" -> {
                    val view = SingleScannerView(ctx)
                    Log.d("SCANNERLOG", "ScannerView: Single CALLED//////////////")
                    controller = ScannerController(
                        singleScannerView = view,
                        multiScannerView = null,
                        authScannerView = null,
                        lifecycleOwner = lifecycleOwner,
                        fragmentManager = fragmentManager,
                        openGallery = { galleryLauncher.launch("image/*") },
                        result = {},
                        error = {}
                    )

                    controller?.startSingleScanner(ctx)
                    view
                }

                "AUTH" -> {
                    val view = AuthScannerView(ctx)

                    Log.d("SCANNERLOG", "ScannerView: AUTHCALLED//////////////")
                    controller = ScannerController(
                        singleScannerView = null,
                        multiScannerView = null,
                        authScannerView = view,
                        lifecycleOwner = lifecycleOwner,
                        fragmentManager = fragmentManager,
                        openGallery = { galleryLauncher.launch("image/*") },
                        result = {},
                        error = {}
                    )

                    controller?.startAuthScanner(ctx, "", "")
                    view
                }

                "MULTI" -> {
                    val view = MultiScannerView(ctx)
                    Log.d("SCANNERLOG", "ScannerView: Multi CALLED//////////////")
                    controller = ScannerController(
                        singleScannerView = null,
                        multiScannerView = view,
                        authScannerView = null,
                        lifecycleOwner = lifecycleOwner,
                        fragmentManager = fragmentManager,
                        openGallery = { galleryLauncher.launch("image/*") },
                        result = {},
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
