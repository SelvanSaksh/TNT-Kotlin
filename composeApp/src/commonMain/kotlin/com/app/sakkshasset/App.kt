package com.app.sakkshasset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import features.InitialScreen
import features.LoginScreen
import features.auth.OtpScreen
import navigation.AppScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import network.repository.AuthRepository
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import features.app.generations.GenerateCodeScreen
import features.app.generations.GS12DBarcode
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import features.app.MainAppScreen
import features.app.assets.Assets
import features.app.generations.CommonBarcodeScreen
import features.app.generations.DynamicBarcodeType
import features.app.generations.GS1DigitalBarcodeScreen
import features.app.scans.Scans
import network.models.UserDetail
import screens.MultiLinkBarcodeScreen

@Composable
fun App() {

    var currentScreen by remember { mutableStateOf(AppScreen.Initial) }
    var userIdentifier by remember { mutableStateOf("") }
    var autoOtp by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var verifyError by remember { mutableStateOf<String?>(null) }

    val sessionManager = remember { SessionManager(getLocalStorage()) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    var selectedBarcodeType: DynamicBarcodeType? by remember {
        mutableStateOf(null)
    }
    LaunchedEffect(Unit) {
        delay(1000)
        if (sessionManager.isLoggedIn()) {
            val userDetailJson = sessionManager.getUserDetail()
            if (userDetailJson != null) {
                try {
                    json.decodeFromString<UserDetail>(userDetailJson)
                    currentScreen = AppScreen.Home
                } catch (e: Exception) {
                    currentScreen = AppScreen.Home
                }
            } else {
                currentScreen = AppScreen.Home
            }
        } else {
            currentScreen = AppScreen.Login
        }
    }

    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center
        ) {
            when (currentScreen) {

                AppScreen.Initial -> {
                    InitialScreen()
                }

                AppScreen.Login -> {
                    LoginScreen(
                        onNavigateToOtp = { identifier, otpResponse ->
                            userIdentifier = identifier
                            autoOtp = if (otpResponse.isAutoGen) otpResponse.otp else null
                            currentScreen = AppScreen.Otp
                        }
                    )
                }

                AppScreen.Otp -> {
                    OtpScreen(
                        autoOtp = autoOtp,
                        isLoading = isLoading,
                        verifyError = verifyError,
                        onVerifyOtp = { otp ->
                            scope.launch {
                                isLoading = true
                                verifyError = null

                                val result = AuthRepository.verifyOtp(userIdentifier, otp)

                                result.onSuccess { response ->
                                    val userDetailJson = json.encodeToString(response.userDetail)
                                    sessionManager.saveSession(
                                        accessToken = response.accessToken,
                                        userId = response.userId,
                                        userEmail = response.userEmail,
                                        userDetail = userDetailJson
                                    )
                                    isLoading = false
                                    currentScreen = AppScreen.Home  // ← triggers MainAppScreen
                                }

                                result.onFailure { error ->
                                    isLoading = false
                                    verifyError = error.message ?: "Invalid OTP. Please try again."
                                }
                            }
                        },
                        onResendOtp = {
                            scope.launch {
                                verifyError = null
                                AuthRepository.sendOtp(userIdentifier)
                            }
                        },
                        onBack = {
                            autoOtp = null
                            verifyError = null
                            currentScreen = AppScreen.Login
                        }
                    )
                }

                // ── All bottom nav tabs go through MainAppScreen ──────────────
                AppScreen.Home,
                AppScreen.History,
                AppScreen.Scan,
                AppScreen.Upgrade,
                AppScreen.Profile -> {
                    MainAppScreen(
                        initialTab = currentScreen,
                        onNavigate = { screen ->
                            currentScreen = screen
                        }
                    )
                }

                AppScreen.GenerateCodeScreen -> {
                    GenerateCodeScreen(
                        onBack = {
                            currentScreen = AppScreen.Home
                        },
                        onNavigate = { screen ->
                            currentScreen = screen
                        },
                        onNavigateBarcode = { type ->
                            selectedBarcodeType = type
                            currentScreen = AppScreen.CommonBarcodeScreen
                        }
                    )
                }

                AppScreen.GS12DBarcode -> {
                    GS12DBarcode(
                        onBack = {
                            currentScreen = AppScreen.GenerateCodeScreen
                        }
                    )
                }

                AppScreen.Assets -> {
                    Assets(
                        onNavigate = { screen ->
                            currentScreen = screen
                        }
                    )
                }

                AppScreen.Scans -> {
                    Scans(
                        onNavigate = { screen ->
                            currentScreen = screen
                        }
                    )
                }

                AppScreen.GS1DigitalBarcodeScreen ->{
                    GS1DigitalBarcodeScreen(
                        onBack = {
                            currentScreen = AppScreen.GenerateCodeScreen
                        }
                    )
                }

                AppScreen.MultiLinkBarcodeScreen -> {
                    MultiLinkBarcodeScreen(
                        onBack = {
                            currentScreen = AppScreen.GenerateCodeScreen
                        }
                    )
                }


                 AppScreen.CommonBarcodeScreen -> {
                    selectedBarcodeType?.let { type ->
                        CommonBarcodeScreen(
                            barcodeType = type,
                            onBack = {
                                currentScreen = AppScreen.GenerateCodeScreen
                            }
                        )
                    }
                }



            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}