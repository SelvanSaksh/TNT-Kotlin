package features.app.scans

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.scanner_sdk.customview.auth.AuthScannerView
import com.example.scanner_sdk.customview.multi.view.MultiScannerView
import com.example.scanner_sdk.customview.single.ScannerController
import com.example.scanner_sdk.customview.single.view.SingleScannerView

/**
 * Scanner Activity supporting Single, Authentication, and Multi scan modes
 */
class ScannerActivity : ComponentActivity() {
    
    private var scannerController: ScannerController? = null
    private var singleScannerView: SingleScannerView? = null
    private var authScannerView: AuthScannerView? = null
    private var multiScannerView: MultiScannerView? = null
    private var containerLayout: FrameLayout? = null
    private var scanMode: String = "SINGLE"
    
    // Camera permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            initializeScanner()
        } else {
            Toast.makeText(this, "Camera permission is required for scanning", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Get scan mode from intent
        scanMode = intent.getStringExtra("SCAN_MODE") ?: "SINGLE"
        
        // Create container layout
        containerLayout = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(containerLayout)
        
        // Check and request camera permission
        checkCameraPermission()
    }
    
    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                initializeScanner()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    private fun initializeScanner() {
        try {
            containerLayout?.removeAllViews()
            
            when (scanMode) {
                "SINGLE" -> setupSingleScanner()
                "AUTH" -> setupAuthScanner()
                "MULTI" -> setupMultiScanner()
                else -> setupSingleScanner()
            }
            
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Scanner initialization failed: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            e.printStackTrace()
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }
    
    private fun setupSingleScanner() {
        val scannerView = SingleScannerView(this)
        singleScannerView = scannerView
        
        val viewToAdd = scannerView as? android.view.View
        if (viewToAdd != null) {
            containerLayout?.addView(viewToAdd)
        }
        
//        scannerController = ScannerController(
//            singleScannerView = singleScannerView,
//            multiScannerView = null,
//            authScannerView = null,
//            lifecycleOwner = this,
//            onScanned = { scannedData ->
//                returnResult(scannedData)
//            }
//        )
        
        scannerController?.startSingleScanner(this)
    }
    
    private fun setupAuthScanner() {
        val scannerView = AuthScannerView(this)
        authScannerView = scannerView
        
        val viewToAdd = scannerView as? android.view.View
        if (viewToAdd != null) {
            containerLayout?.addView(viewToAdd)
        }
        
//        scannerController = ScannerController(
//            singleScannerView = null,
//            multiScannerView = null,
//            authScannerView = authScannerView,
//            lifecycleOwner = this,
//            onScanned = { scannedData ->
//                returnResult(scannedData)
//            }
//        )
        
        scannerController?.startAuthScanner(this)
    }
    
    private fun setupMultiScanner() {
        val scannerView = MultiScannerView(this)
        multiScannerView = scannerView
        
        val viewToAdd = scannerView as? android.view.View
        if (viewToAdd != null) {
            containerLayout?.addView(viewToAdd)
        }
        
//        scannerController = ScannerController(
//            singleScannerView = null,
//            multiScannerView = multiScannerView,
//            authScannerView = null,
//            lifecycleOwner = this,
//            onScanned = { scannedData ->
//                returnResult(scannedData)
//            }
//        )
        
        scannerController?.startMultiScanner(this)
    }
    
    private fun returnResult(scannedData: String) {
        val resultIntent = Intent().apply {
            putExtra("SCAN_RESULT", scannedData)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            containerLayout?.removeAllViews()
            scannerController = null
            singleScannerView = null
            authScannerView = null
            multiScannerView = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
