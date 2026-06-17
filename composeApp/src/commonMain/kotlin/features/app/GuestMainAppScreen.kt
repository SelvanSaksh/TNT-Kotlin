package features.app

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
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import core.storage.GuestPromptState
import features.GuestSignInPromptDialog
import features.app.generations.DynamicBarcodeType
import features.app.generations.GenerateCodeScreen
import features.app.scans.Scans
import navigation.AppScreen
import navigation.appscreen.Screens

private val BlueAccent = Color(0xFF163C66)
private val NavGray = Color(0xFF9CA3AF)

@Composable
fun GuestMainAppScreen(
    onNavigate: (Screens) -> Unit,
    onNavigateBarcode: (DynamicBarcodeType) -> Unit,
    onSignIn: () -> Unit,
) {
    var activeTab by remember { mutableStateOf(AppScreen.Scan) }
    var showSignInPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!GuestPromptState.shownThisLaunch) {
            showSignInPrompt = true
            GuestPromptState.shownThisLaunch = true
        }
    }

    GuestSignInPromptDialog(
        visible = showSignInPrompt,
        onDismiss = { showSignInPrompt = false },
        onSignIn = {
            showSignInPrompt = false
            onSignIn()
        },
    )

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            GuestBottomNavBar(
                activeTab = activeTab,
                onTabSelected = { activeTab = it },
                onSignIn = onSignIn,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (activeTab) {
                AppScreen.Scan -> Scans(
                    onNavigate = {},
                    isGuestMode = true,
                    onSignInRequired = onSignIn,
                )
                AppScreen.GenerateCodeScreen -> GenerateCodeScreen(
                    isGuestMode = true,
                    showBackButton = false,
                    onBack = { activeTab = AppScreen.Scan },
                    onNavigate = { route ->
                        when (route) {
                            Screens.GS12DBarcode.destRoute -> onNavigate(Screens.GS12DBarcode)
                            Screens.GS1DigitalBarcodeScreen.destRoute -> onNavigate(Screens.GS1DigitalBarcodeScreen)
                            Screens.MultiLinkBarcodeScreen.destRoute -> onNavigate(Screens.MultiLinkBarcodeScreen)
                            else -> Unit
                        }
                    },
                    onNavigateBarcode = onNavigateBarcode,
                    onSignInRequired = onSignIn,
                )
                else -> Scans(
                    onNavigate = {},
                    isGuestMode = true,
                    onSignInRequired = onSignIn,
                )
            }
        }
    }
}

@Composable
private fun GuestBottomNavBar(
    activeTab: AppScreen,
    onTabSelected: (AppScreen) -> Unit,
    onSignIn: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = Color.White,
            shadowElevation = 12.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                GuestNavTabItem(
                    label = "Generate",
                    icon = Icons.Filled.QrCode,
                    isSelected = activeTab == AppScreen.GenerateCodeScreen,
                    onClick = { onTabSelected(AppScreen.GenerateCodeScreen) },
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.weight(1f))
                GuestNavTabItem(
                    label = "Sign in",
                    icon = Icons.Filled.Login,
                    isSelected = false,
                    onClick = onSignIn,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-20).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FloatingActionButton(
                onClick = { onTabSelected(AppScreen.Scan) },
                shape = CircleShape,
                containerColor = BlueAccent,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier.size(60.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCodeScanner,
                    contentDescription = "Scan",
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .height(64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Scan",
                fontSize = 11.sp,
                color = if (activeTab == AppScreen.Scan) BlueAccent else NavGray,
                modifier = Modifier.padding(bottom = 10.dp),
            )
        }
    }
}

@Composable
private fun GuestNavTabItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (isSelected) BlueAccent else NavGray
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.height(3.dp))
        Text(text = label, fontSize = 11.sp, color = color)
    }
}
