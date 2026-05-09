package com.app.sakkshasset

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
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
import features.app.subscription.SubscriptionScreen
import features.auth.OtpScreen
import kotlinx.datetime.Clock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import navigation.AppScreen
import navigation.appscreen.Screens
import network.models.UserDetail
import network.repository.AuthRepository
import screens.MultiLinkBarcodeScreen
import kotlin.system.exitProcess

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
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var lastBackPressAt by remember { mutableLongStateOf(0L) }

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
            containerColor = Color.White,
            contentColor = Color.Black,
            contentWindowInsets = WindowInsets(0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { paddingValues ->
            BackHandler(enabled = currentRoute == Screens.HomeScreen.destRoute) {
                val now = Clock.System.now().toEpochMilliseconds()
                if (now - lastBackPressAt < 2000L) {
                    exitProcess(0)
                } else {
                    lastBackPressAt = now
                    scope.launch {
                        snackbarHostState.showSnackbar("Press back again to exit")
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = Screens.SplashScreen.destRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .background(Color.White),
            ) {
                composable(Screens.SplashScreen.destRoute) {
                    InitialScreen {
                        if (sessionManager.isLoggedIn() && sessionManager.getUserDetail() != null) {
                            val hasSubscription =
                                sessionManager.getSubscriptionStatus()?.equals("active", ignoreCase = true) == true
                            navController.navigate(if (hasSubscription) {
                                Screens.HomeScreen.destRoute
                            } else {
                                Screens.SubscriptionScreen.destRoute
                            }) {
                                popUpTo(Screens.SplashScreen.destRoute) { inclusive = true }
                                launchSingleTop = true
                            }
                        } else {
                            navController.navigate(Screens.LoginScreen.destRoute) {
                                popUpTo(Screens.SplashScreen.destRoute) { inclusive = true }
                                launchSingleTop = true
                            }
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
                                    val locationDetailsJson = response.locationDetails
                                        ?.let { json.encodeToString(it) }
                                        .orEmpty()
                                    sessionManager.saveLocationDetails(locationDetailsJson)
                                    println("LOCATION_LOG: saved ${response.locationDetails?.size ?: 0} location detail records")
                                    val prettyLocationDetails = if (locationDetailsJson.isBlank()) {
                                        "[]"
                                    } else {
                                        runCatching {
                                            val element =
                                                kotlinx.serialization.json.Json.parseToJsonElement(locationDetailsJson)
                                            kotlinx.serialization.json.Json {
                                                prettyPrint = true; prettyPrintIndent = "  "
                                            }.encodeToString(
                                                kotlinx.serialization.json.JsonElement.serializer(),
                                                element
                                            )
                                        }.getOrElse { locationDetailsJson }
                                    }
                                    println("LOCATION_DETAILS_FULL_LOG_JSON:\n$prettyLocationDetails")

                                    val companyIdFromUserDetail = response.userDetail.companyId
                                    val companyIdFromSubscription = response.subscription?.companyId ?: 0
                                    val companyIdFromRawUserDetail = runCatching {
                                        val obj = json.parseToJsonElement(userDetailJson).jsonObject
                                        obj["companyid"]?.jsonPrimitive?.content?.toIntOrNull()
                                            ?: obj["company_id"]?.jsonPrimitive?.content?.toIntOrNull()
                                    }.getOrNull() ?: 0

                                    val finalCompanyId = when {
                                        companyIdFromUserDetail > 0 -> companyIdFromUserDetail
                                        companyIdFromSubscription > 0 -> companyIdFromSubscription
                                        companyIdFromRawUserDetail > 0 -> companyIdFromRawUserDetail
                                        else -> 0
                                    }

                                    if (finalCompanyId > 0) {
                                        sessionManager.saveCompanyId(finalCompanyId.toString())
                                    }

                                    val otpSubscription = response.subscription
                                    val storedStatus = otpSubscription?.status
                                        ?: response.userDetail.subscriptionStatus
                                        ?: ""
                                    val storedPlanId = otpSubscription?.planId
                                        ?: response.userDetail.subscriptionPlan
                                        ?: ""
                                    val storedRawJson = when {
                                        otpSubscription != null -> json.encodeToString(otpSubscription)
                                        response.userDetail.subscriptionData != null -> response.userDetail.subscriptionData.toString()
                                        else -> ""
                                    }

                                    sessionManager.saveSubscription(
                                        rawJson = storedRawJson,
                                        status = storedStatus,
                                        planId = storedPlanId
                                    )

                                    val hasActiveSubscription =
                                        (otpSubscription?.isActive == true) ||
                                            storedStatus.equals("active", ignoreCase = true)

                                    if (hasActiveSubscription) {
                                        navController.navigate(Screens.HomeScreen.destRoute) {
                                            popUpTo(Screens.LoginScreen.destRoute) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    } else {
                                        navController.navigate(Screens.SubscriptionScreen.destRoute) {
                                            popUpTo(Screens.LoginScreen.destRoute) { inclusive = true }
                                            launchSingleTop = true
                                        }
                                    }
                                    isLoading = false
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
                        },
                        onNavigateToSubscription = {
                            navController.navigate(Screens.SubscriptionScreen.destRoute)
                        }
                    )
                }

                composable(Screens.GS1DigitalBarcodeScreen.destRoute) {
                    GS1DigitalBarcodeScreen(
                        onBack = {
                            navController.popBackStack()
                        },
                        onNavigateToSubscription = {
                            navController.navigate(Screens.SubscriptionScreen.destRoute)
                        }
                    )
                }

                composable(Screens.MultiLinkBarcodeScreen.destRoute) {
                    MultiLinkBarcodeScreen(
                        onBack = {
                            navController.popBackStack()
                        },
                        onNavigateToSubscription = {
                            navController.navigate(Screens.SubscriptionScreen.destRoute)
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
                            onNavigateToSubscription = {
                                navController.navigate(Screens.SubscriptionScreen.destRoute)
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

                composable(Screens.SubscriptionScreen.destRoute) {
                    SubscriptionScreen(
                        onSubscribed = {
                            navController.navigate(Screens.HomeScreen.destRoute) {
                                popUpTo(Screens.SubscriptionScreen.destRoute) { inclusive = true }
                            }
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
                AppScreen.Analytics,
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