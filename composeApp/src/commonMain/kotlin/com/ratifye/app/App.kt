package com.ratifye.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import components.NoInternetConnectionView
import core.network.rememberNetworkMonitor
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material3.MaterialTheme
import theme.AppTheme
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
import core.location.AppLocationCache
import core.network.AuthSessionEvents
import core.session.WarehouseAccess
import core.storage.GuestPromptState
import core.storage.SessionManager
import core.storage.getLocalStorage
import network.AUTH_TOKEN
import utils.DeviceLocationProvider
import features.InitialScreen
import features.LoginScreen
import features.app.GuestMainAppScreen
import features.app.assets.Assets
import features.app.generations.CommonBarcodeScreen
import features.app.generations.DynamicBarcodeType
import features.app.generations.GS12DBarcode
import features.app.generations.GS1DigitalBarcodeScreen
import features.app.generations.GenerateCodeScreen
import features.app.scans.Scans
import features.app.subscription.SubscriptionScreen
import features.app.warehouse.WarehouseRoleSelectionScreen
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
import navigation.appscreen.MainAppScreen
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
    var loginUserId by remember { mutableStateOf<Int?>(null) }
    var autoOtp by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var verifyError by remember { mutableStateOf<String?>(null) }

    val sessionManager = remember {
        SessionManager(getLocalStorage()).also { sm ->
            AUTH_TOKEN = sm.getAccessToken()
        }
    }
    val json = remember { Json { ignoreUnknownKeys = true } }
    val warehouseAccess = remember { WarehouseAccess(sessionManager) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var lastBackPressAt by remember { mutableLongStateOf(0L) }
    val networkMonitor = rememberNetworkMonitor()

    fun navigateAfterAuth(popUpToRoute: String, inclusive: Boolean = true) {
        val hasSubscription =
            sessionManager.getSubscriptionStatus()?.equals("active", ignoreCase = true) == true
        val destination = warehouseAccess.postAuthDestination(hasSubscription)
        val route = when (destination) {
            WarehouseAccess.PostAuthDestination.Subscription -> Screens.SubscriptionScreen.destRoute
            WarehouseAccess.PostAuthDestination.RoleSelection -> Screens.WarehouseRoleSelectionScreen.destRoute
            WarehouseAccess.PostAuthDestination.Home -> Screens.HomeScreen.destRoute
        }
        navController.navigate(route) {
            popUpTo(popUpToRoute) { this.inclusive = inclusive }
            launchSingleTop = true
        }
    }

    var selectedBarcodeType: DynamicBarcodeType? by remember {
        mutableStateOf(null)
    }
    LaunchedEffect(navController) {
        AuthSessionEvents.unauthorized.collect {
            val route = navController.currentDestination?.route
            if (route == Screens.LoginScreen.destRoute ||
                route == Screens.OTPScreen.destRoute
            ) {
                return@collect
            }
            sessionManager.clearSession()
            GuestPromptState.shownThisLaunch = false
            navController.navigate(Screens.LoginScreen.destRoute) {
                popUpTo(Screens.SplashScreen.destRoute) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(Unit) {
        AppLocationCache.restoreFrom(sessionManager)
        if (sessionManager.isLoggedIn()) {
            val userDetailJson = sessionManager.getUserDetail()
            if (userDetailJson != null) {
                try {
                    json.decodeFromString<UserDetail>(userDetailJson)
                } catch (e: Exception) {
                }
            }
        }
        withContext(Dispatchers.Default) {
            AppLocationCache.refresh(DeviceLocationProvider())
            AppLocationCache.persistTo(sessionManager)
        }
    }

    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = Color.White,
                contentColor = Color.Black,
                contentWindowInsets = WindowInsets(0),
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
            ) { paddingValues ->
            BackHandler(
                enabled = currentRoute == Screens.HomeScreen.destRoute ||
                    currentRoute == Screens.GuestHomeScreen.destRoute,
            ) {
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

            // Safety net: prevent back-navigating to guest screen when logged in
            BackHandler(
                enabled = sessionManager.isLoggedIn() &&
                    currentRoute != Screens.HomeScreen.destRoute &&
                    currentRoute != Screens.LoginScreen.destRoute &&
                    currentRoute != Screens.OTPScreen.destRoute &&
                    currentRoute != Screens.SplashScreen.destRoute &&
                    currentRoute != Screens.GuestHomeScreen.destRoute,
            ) {
                val prevRoute = navController.previousBackStackEntry?.destination?.route
                if (prevRoute == Screens.GuestHomeScreen.destRoute) {
                    navController.navigate(Screens.HomeScreen.destRoute) {
                        popUpTo(Screens.GuestHomeScreen.destRoute) { inclusive = true }
                        launchSingleTop = true
                    }
                } else {
                    navController.popBackStack()
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
                        if (sessionManager.isLoggedIn()) {
                            navigateAfterAuth(Screens.SplashScreen.destRoute)
                        } else {
                            GuestPromptState.shownThisLaunch = false
                            navController.navigate(Screens.GuestHomeScreen.destRoute) {
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
                            loginUserId = otpResponse.userId
                            autoOtp =
                                if (otpResponse.isAutoGen) otpResponse.otp?.takeIf { it.isNotBlank() } else null
                            navController.navigate(Screens.OTPScreen.destRoute)
                        },
                        onContinueAsGuest = {
                            navController.navigate(Screens.GuestHomeScreen.destRoute) {
                                popUpTo(Screens.LoginScreen.destRoute) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
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

                                val result = AuthRepository.verifyOtp(
                                    identifier = userIdentifier,
                                    otp = otp,
                                    userId = loginUserId,
                                )

                                result.onSuccess { response ->

                                    val userDetailJson = json.encodeToString(response.userDetail)

                                    sessionManager.saveSession(
                                        accessToken = response.accessToken,
                                        userId = response.userId,
                                        userEmail = response.userEmail,
                                        userDetail = userDetailJson
                                    )
                                    AUTH_TOKEN = response.accessToken
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

                                    warehouseAccess.persistWarehouseSessionFromLogin(
                                        userRole = response.userDetail.role,
                                        topLevelModules = response.modules,
                                        accessModules = response.userDetail.accessModules,
                                    )

                                    navigateAfterAuth(Screens.LoginScreen.destRoute)
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
                                AuthRepository.sendOtp(userIdentifier).onSuccess { response ->
                                    loginUserId = response.userId
                                    autoOtp =
                                        if (response.isAutoGen) response.otp?.takeIf { it.isNotBlank() } else null
                                }
                            }
                        },
                        onBack = {
                            autoOtp = null
                            loginUserId = null
                            verifyError = null
                            navController.popBackStack()
                        }
                    )
                }

                composable(Screens.GuestHomeScreen.destRoute) {
                    GuestMainAppScreen(
                        onNavigate = { screen ->
                            navController.navigate(screen.destRoute)
                        },
                        onNavigateBarcode = { type ->
                            selectedBarcodeType = type
                            navController.navigate(Screens.CommonBarcodeScreen.destRoute)
                        },
                        onSignIn = {
                            navController.navigate(Screens.LoginScreen.destRoute) {
                                popUpTo(Screens.GuestHomeScreen.destRoute) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable(Screens.HomeScreen.destRoute) {
                    MainAppScreen(
                        onNavigate = { screen ->
                            navController.navigate(screen.destRoute)
                        },
                        onSwitchWarehouseRole = {
                            sessionManager.clearActiveWarehouseStaffRole()
                            navController.navigate(Screens.WarehouseRoleSelectionScreen.destRoute) {
                                popUpTo(Screens.HomeScreen.destRoute) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }

                composable(Screens.WarehouseRoleSelectionScreen.destRoute) {
                    WarehouseRoleSelectionScreen(
                        onRoleSelected = {
                            navController.navigate(Screens.HomeScreen.destRoute) {
                                popUpTo(Screens.WarehouseRoleSelectionScreen.destRoute) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
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

                composable(Screens.PickingScreen.destRoute) {
                    features.app.warehouse.PickingScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Screens.PackingScreen.destRoute) {
                    features.app.warehouse.PackingScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Screens.ReceivingScreen.destRoute) {
                    features.app.warehouse.ReceivingScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Screens.SubscriptionScreen.destRoute) {
                    SubscriptionScreen(
                        onSubscribed = {
                            navigateAfterAuth(Screens.SubscriptionScreen.destRoute)
                        }
                    )
                }
            }
            }

            if (!networkMonitor.isConnected) {
                NoInternetConnectionView(
                    onRetry = { networkMonitor.refresh() },
                )
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
                            autoOtp =
                                if (otpResponse.isAutoGen) otpResponse.otp?.takeIf { it.isNotBlank() } else null
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

                                val result = AuthRepository.verifyOtp(
                                    identifier = userIdentifier,
                                    otp = otp,
                                    userId = loginUserId,
                                )

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