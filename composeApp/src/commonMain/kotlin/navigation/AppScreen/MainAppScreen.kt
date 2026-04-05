package features.app

import UpgradeView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.serialization.json.Json
import navigation.AppScreen
import network.models.UserDetail

private val BlueAccent = Color(0xFF163C66)
private val NavGray = Color(0xFF9CA3AF)

data class BottomTab(
    val route: AppScreen,
    val label: String,
    val icon: ImageVector
)

private val bottomTabs = listOf(
    BottomTab(AppScreen.Home,    "Home",    Icons.Filled.Home),
    BottomTab(AppScreen.History, "History", Icons.Outlined.History),
    BottomTab(AppScreen.Scan,    "",    Icons.Outlined.QrCodeScanner),
    BottomTab(AppScreen.Upgrade, "Upgrade", Icons.Outlined.RocketLaunch),
    BottomTab(AppScreen.Profile, "Profile", Icons.Filled.Person),
)
@Composable
fun MainAppScreen(
    initialTab: AppScreen = AppScreen.Home,
    onNavigate: (AppScreen) -> Unit
) {
    var activeTab by remember { mutableStateOf(initialTab) }

    val sessionManager = remember { SessionManager(getLocalStorage()) }
    val json = remember { Json { ignoreUnknownKeys = true } }
    val scope = rememberCoroutineScope()

    val userDetail = remember {
        sessionManager.getUserDetail()?.let {
            try { json.decodeFromString<UserDetail>(it) } catch (e: Exception) { null }
        }
    }


    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            BottomNavBar(
                activeTab = activeTab,
                onTabSelected = { activeTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (activeTab) {
                AppScreen.Home    -> Home(onNavigate = onNavigate)
                AppScreen.History -> features.app.history.History()
                AppScreen.Scan    -> ScanPlaceholder()
                AppScreen.Upgrade -> UpgradeView(

                )
                AppScreen.Profile -> features.profile.ProfileScreen(
                    userName = userDetail?.firstName,
                    email = userDetail?.email,
                    role = userDetail?.role,
                    onNavigate = onNavigate,
                    onLogout = {
                        onNavigate(AppScreen.Login) // or handle logout
                    }
                )
                else              -> Home(onNavigate = onNavigate)
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
    val rightTabs = listOf(BottomTab(AppScreen.Upgrade, "Upgrade", Icons.Outlined.RocketLaunch),
        BottomTab(AppScreen.Profile, "Profile", Icons.Filled.Person))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        // ── White nav bar background ─────────────────────────────────────
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
                // Left tabs
                leftTabs.forEach { tab ->
                    NavTabItem(
                        tab = tab,
                        isSelected = activeTab == tab.route,
                        onClick = { onTabSelected(tab.route) },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Empty space for FAB
                Spacer(modifier = Modifier.weight(1f))

                // Right tabs
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

        // ── Floating Scan button centered above nav bar ──────────────────
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

        // ── Scan label inside the nav bar ────────────────────────────────
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

@Composable
fun HistoryPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("History Screen")
    }
}

@Composable
fun ScanPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Scan Screen")
    }
}

@Composable
fun UpgradePlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Upgrade Screen")
    }
}

@Composable
fun ProfilePlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Profile Screen")
    }
}