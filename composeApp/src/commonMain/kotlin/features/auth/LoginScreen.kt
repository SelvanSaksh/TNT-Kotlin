package features

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import components.InputField
import components.PrimaryButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import network.repository.AuthRepository
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import sakkshasset.composeapp.generated.resources.Res
import sakkshasset.composeapp.generated.resources.logo
import theme.Black
import theme.White

@OptIn(ExperimentalResourceApi::class)
@Composable
fun LoginScreen(
    modifier: Modifier = Modifier,
    onNavigateToOtp: (String, network.models.SendOtpResponse) -> Unit = { _, _ -> }
) {
    var userInput by remember { mutableStateOf("") }
    var showContent by remember { mutableStateOf(false) }
    var inputError by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val isDark = isSystemInDarkTheme()
    val bgColor = if (isDark) Black else White
    val textColor = if (isDark) White else Black

    // Intro Animation
    LaunchedEffect(Unit) {
        delay(150)
        showContent = true
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = showContent,
            enter = fadeIn(animationSpec = tween(900)),
            exit = fadeOut(animationSpec = tween(400))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp)
            ) {

                Image(
                    painter = painterResource(Res.drawable.logo),
                    contentDescription = "App Logo",
                    modifier = Modifier.size(150.dp)
                )

                InputField(
                    value = userInput,
                    onValueChange = {
                        userInput = it
                        inputError = null
                    },
                    placeholder = "Email or Phone"
                )

                PrimaryButton(
                    text = if (isLoading) "Please wait..." else "Continue",
                    isLoading = isLoading,
                    enabled = userInput.isNotEmpty() && !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (userInput.isBlank()) {
                            inputError = "Please enter Email or Phone"
                            return@PrimaryButton
                        }

                        isLoading = true
                        inputError = null
                        
                        scope.launch {
                            val result = AuthRepository.sendOtp(userInput.trim())
                            isLoading = false
                            
                            result.onSuccess { response ->
                                println("OTP Response: $response")
                                onNavigateToOtp(userInput.trim(), response)
                            }.onFailure { error ->
                                inputError = error.message ?: "Network error. Please try again."
                                snackbarHostState.showSnackbar(
                                    error.message ?: "Network error. Please try again."
                                )
                            }
                        }
                    }
                )

                inputError?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "By continuing, you agree to our Terms and Privacy Policy.",
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Snackbar for success/error messages
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}
