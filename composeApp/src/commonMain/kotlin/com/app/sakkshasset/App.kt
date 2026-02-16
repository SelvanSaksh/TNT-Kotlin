package com.app.sakkshasset

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.dp

@Composable
fun App() {

    var currentScreen by remember { mutableStateOf(AppScreen.Initial) }
    var userIdentifier by remember { mutableStateOf("") }
    var autoOtp by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    val sessionManager = remember { SessionManager(getLocalStorage()) }
    val json = remember { Json { ignoreUnknownKeys = true } }

    LaunchedEffect(Unit) {
        delay(1000)
        // Check if user is already logged in
        if (sessionManager.isLoggedIn()) {
            // Get user detail and check role
            val userDetailJson = sessionManager.getUserDetail()
            if (userDetailJson != null) {
                try {
                    val userDetail = json.decodeFromString<network.models.UserDetail>(userDetailJson)
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
                .background(MaterialTheme.colorScheme.background),
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
                        onVerifyOtp = { otp ->
                            scope.launch {

                                isLoading = true   // ✅ start loading

                                val result = AuthRepository.verifyOtp(userIdentifier, otp)

                                result.onSuccess { response ->

                                    val userDetailJson = json.encodeToString(response.userDetail)

                                    sessionManager.saveSession(
                                        accessToken = response.accessToken,
                                        userId = response.userId,
                                        userEmail = response.userEmail,
                                        userDetail = userDetailJson
                                    )

                                    snackbarHostState.showSnackbar(response.message)

                                    isLoading = false
                                    currentScreen = AppScreen.Home
                                }

                                result.onFailure {
                                    isLoading = false
                                    snackbarHostState.showSnackbar("Verification failed")
                                }
                            }
                        },
                        onResendOtp = {
                            scope.launch {
                                AuthRepository.sendOtp(userIdentifier)
                            }
                        },
                        onBack = {
                            autoOtp = null
                            currentScreen = AppScreen.Login
                        }
                    )
                }


                AppScreen.Home -> {
        features.app.Home(
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
                    features.app.assets.Assets(
                        onNavigate = { screen ->
                            currentScreen = screen
                        }
                    )
                }
                AppScreen.Scans -> {
                    features.app.scans.Scans(
                        onNavigate = { screen ->
                            currentScreen = screen
                        }
                    )
                }
}
            
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
