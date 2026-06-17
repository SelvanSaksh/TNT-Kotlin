package navigation.appscreen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.models.BarcodeLookupQuery
import core.session.WarehouseAccess
import core.storage.GuestPromptState
import core.storage.SessionManager
import core.storage.getLocalStorage
import features.app.AnalyticsScreen
import features.app.Home as HomeScreen
import features.app.scans.Scans
import features.app.warehouse.WarehouseStaffModuleRoot
import kotlinx.serialization.json.Json
import navigation.AppScreen
import navigation.appscreen.Screens
import network.models.UserDetail

private val BlueAccent = Color(0xFF163C66)
private val NavGray = Color(0xFF9CA3AF)

data class BottomTab(
    val route: AppScreen,
    val label: String,
    val icon: ImageVector
)

@Composable
fun MainAppScreen(
    initialTab: AppScreen = AppScreen.Home,
    onNavigate: (Screens) -> Unit,
    onSwitchWarehouseRole: () -> Unit = {},
) {
    var activeTab by remember { mutableStateOf(initialTab) }
    var trackTraceQuery by remember { mutableStateOf<BarcodeLookupQuery?>(null) }

    val sessionManager = remember { SessionManager(getLocalStorage()) }
    val warehouseAccess = remember { WarehouseAccess(sessionManager) }
    val isWarehouseStaff = warehouseAccess.usesWarehouseStaffExperience
    val staffRole = warehouseAccess.resolvedWarehouseStaffRole()
    val json = remember { Json { ignoreUnknownKeys = true } }

    val userDetail = remember {
        sessionManager.getUserDetail()?.let {
            try { json.decodeFromString<UserDetail>(it) } catch (e: Exception) { null }
        }
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            if (isWarehouseStaff) {
                StaffBottomNavBar(
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it },
                )
            } else {
                BottomNavBar(
                    activeTab = activeTab,
                    onTabSelected = { activeTab = it },
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                AppScreen.Home -> {
                    if (staffRole != null) {
                        WarehouseStaffModuleRoot(
                            role = staffRole,
                            onLogout = {
                                sessionManager.clearSession()
                                GuestPromptState.shownThisLaunch = false
                                onNavigate(Screens.GuestHomeScreen)
                            },
                        )
                    } else {
                        HomeScreen(
                            onNavigate = onNavigate,
                            onHistoryClick = { activeTab = AppScreen.History },
                            onTrackTrace = { q: BarcodeLookupQuery -> trackTraceQuery = q },
                        )
                    }
                }
                AppScreen.History -> features.app.history.History(
                    onTrackTrace = { q -> trackTraceQuery = q },
                )
                AppScreen.Scan -> Scans(onNavigate = {})
                AppScreen.Analytics -> AnalyticsScreen()
                AppScreen.Profile -> features.profile.ProfileScreen(
                    userName = userDetail?.firstName,
                    email = userDetail?.email,
                    role = userDetail?.role,
                    onNavigate = {},
                    onLogout = {
                        sessionManager.clearSession()
                        GuestPromptState.shownThisLaunch = false
                        onNavigate(Screens.GuestHomeScreen)
                    },
                    onSwitchWarehouseRole = onSwitchWarehouseRole,
                )
                else -> HomeScreen(
                    onNavigate = onNavigate,
                    onHistoryClick = { activeTab = AppScreen.History },
                    onTrackTrace = { q: BarcodeLookupQuery -> trackTraceQuery = q },
                )
            }

            trackTraceQuery?.let { query ->
                features.app.history.TrackTraceScreen(
                    lookupQuery = query,
                    onBack = { trackTraceQuery = null },
                )
            }
        }
    }
}

@Composable
fun StaffBottomNavBar(
    activeTab: AppScreen,
    onTabSelected: (AppScreen) -> Unit,
) {
    val tabs = listOf(
        BottomTab(AppScreen.Home, "Home", Icons.Filled.Home),
        BottomTab(AppScreen.Profile, "Profile", Icons.Filled.Person),
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 12.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEach { tab ->
                NavTabItem(
                    tab = tab,
                    isSelected = activeTab == tab.route,
                    onClick = { onTabSelected(tab.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
fun BottomNavBar(
    activeTab: AppScreen,
    onTabSelected: (AppScreen) -> Unit
) {
    val leftTabs  = listOf(BottomTab(AppScreen.Home,    "Home",    Icons.Filled.Home),
        BottomTab(AppScreen.History, "History", Icons.Outlined.History))
    val rightTabs = listOf(BottomTab(AppScreen.Analytics, "Analytics", Icons.Outlined.BarChart),
        BottomTab(AppScreen.Profile, "Profile", Icons.Filled.Person))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = Color.White,
            shadowElevation = 12.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                leftTabs.forEach { tab ->
                    NavTabItem(
                        tab = tab,
                        isSelected = activeTab == tab.route,
                        onClick = { onTabSelected(tab.route) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                rightTabs.forEach { tab ->
                    NavTabItem(
                        tab = tab,
                        isSelected = activeTab == tab.route,
                        onClick = { onTabSelected(tab.route) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-20).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FloatingActionButton(
                onClick = { onTabSelected(AppScreen.Scan) },
                shape = CircleShape,
                containerColor = BlueAccent,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp
                ),
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = "Scan",
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .height(64.dp)
                .offset(x = 0.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Scan",
                fontSize = 11.sp,
                color = if (activeTab == AppScreen.Scan) BlueAccent else NavGray,
                modifier = Modifier.padding(bottom = 10.dp)
            )
        }
    }
}

@Composable
fun NavTabItem(
    tab: BottomTab,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = if (isSelected) BlueAccent else NavGray

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = tab.label,
            fontSize = 11.sp,
            color = color
        )
    }
}
