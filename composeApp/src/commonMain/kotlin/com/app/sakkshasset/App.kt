package com.app.sakkshasset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import core.storage.SessionManager
import core.storage.getLocalStorage
import features.InitialScreen
import features.LoginScreen
import features.app.MainAppScreen
import features.app.assets.Assets
import features.app.generations.CommonBarcodeScreen
import features.app.generations.DynamicBarcodeType
import features.app.generations.GS12DBarcode
import features.app.generations.GS1DigitalBarcodeScreen
import features.app.generations.GenerateCodeScreen
import features.app.scans.Scans
import features.auth.OtpScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import navigation.AppScreen
import navigation.appscreen.Screens
import network.models.UserDetail
import network.repository.AuthRepository
import screens.MultiLinkBarcodeScreen

@Composable
fun App() {

    val navController = rememberNavController()

    var currentScreen by remember { mutableStateOf("") }
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
        if (sessionManager.isLoggedIn()) {
            val userDetailJson = sessionManager.getUserDetail()
            if (userDetailJson != null) {
                try {
                    json.decodeFromString<UserDetail>(userDetailJson)
                } catch (e: Exception) {
                }
            }
        }
    }

    MaterialTheme {
        Scaffold(
            contentWindowInsets = WindowInsets(0)
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Screens.SplashScreen.destRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                composable(Screens.SplashScreen.destRoute) {
                    InitialScreen {
                        if (sessionManager.isLoggedIn() && sessionManager.getUserDetail() != null) {
                            navController.navigate(Screens.HomeScreen.destRoute)
                        } else {
                            navController.navigate(Screens.LoginScreen.destRoute)
                        }
                    }
                }

                composable(Screens.LoginScreen.destRoute) {
                    LoginScreen(
                        onNavigateToOtp = { identifier, otpResponse ->
                            userIdentifier = identifier
                            autoOtp = if (otpResponse.isAutoGen) otpResponse.otp else null
                            navController.navigate(Screens.OTPScreen.destRoute)
                        }
                    )
                }

                composable(Screens.OTPScreen.destRoute) {
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
                                    navController.navigate(Screens.HomeScreen.destRoute)
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
                            navController.popBackStack()
                        }
                    )
                }

                composable(Screens.HomeScreen.destRoute) {
                    MainAppScreen(
                        onNavigate = { screen ->
                            navController.navigate(screen.destRoute)
                        }
                    )
                }

                composable(Screens.GenerateCodeScreen.destRoute) {
                    GenerateCodeScreen(
                        onBack = {
                            navController.popBackStack()
                        },
                        onNavigate = { screen ->
                            navController.navigate(screen)
                        },
                        onNavigateBarcode = { type ->
                            selectedBarcodeType = type
                            navController.navigate(Screens.CommonBarcodeScreen.destRoute)
                        }
                    )
                }

                composable(Screens.GS12DBarcode.destRoute) {
                    GS12DBarcode(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(Screens.GS1DigitalBarcodeScreen.destRoute) {
                    GS1DigitalBarcodeScreen(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(Screens.MultiLinkBarcodeScreen.destRoute) {
                    MultiLinkBarcodeScreen(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(Screens.CommonBarcodeScreen.destRoute) {
                    selectedBarcodeType?.let { type ->
                        CommonBarcodeScreen(
                            barcodeType = type,
                            onBack = {
                                navController.popBackStack()
                            },
                        )
                    }
                }

                composable(Screens.Scan.destRoute) {
                    Scans(
                        onNavigate = { screen ->
                            navController.navigate(screen)
                        }
                    )
                }

                composable(Screens.Assets.destRoute) {
                    Assets(
                        onNavigate = { screen ->
                            navController.navigate(screen)
                        }
                    )
                }
            }
        }
/*        Box(
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
                            },
                        )
                    }
                }



            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }*/
    }
}