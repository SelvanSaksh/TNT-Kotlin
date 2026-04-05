package features.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import components.OtpInputField
import components.PrimaryButton
import org.jetbrains.compose.resources.painterResource
import sakkshasset.composeapp.generated.resources.Res
import sakkshasset.composeapp.generated.resources.logo
import theme.White

@Composable
fun OtpScreen(
    modifier: Modifier = Modifier,
    autoOtp: String? = null,
    isLoading: Boolean,
    verifyError: String? = null,
    onVerifyOtp: (String) -> Unit,
    onResendOtp: () -> Unit,
    onBack: () -> Unit
) {

    var otp by remember(autoOtp) { mutableStateOf(autoOtp ?: "") }
    var isError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(verifyError) {
        if (!verifyError.isNullOrBlank()) {
            isError = true
            errorMessage = verifyError
            otp = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(White)
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Spacer(modifier = Modifier.height(48.dp))

        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(200.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Enter Verification Code",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold
            ),
            textAlign = TextAlign.Center,
            fontSize = 22.sp,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "We have sent a 6-digit OTP to your mobile number",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OtpInputField(
            otp = otp,
            onOtpChange = {
                otp = it
                isError = false
            },
            isError = isError,
            errorMessage = errorMessage,
            isLoading = isLoading,
            onComplete = {
                if (it.length == 6) {
                    onVerifyOtp(it)
                }
            },
            onResendClick = { onResendOtp() }
        )

        Spacer(modifier = Modifier.height(48.dp))

        PrimaryButton(
            text = if (isLoading) "Verifying..." else "Verify OTP",
            isLoading = isLoading,
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                println("OtpScreen: Verify button clicked with OTP: $otp")

                if (otp.length == 6) {
                    println("OtpScreen: OTP is valid. Triggering verification")
                    onVerifyOtp(otp)
                } else {
                    println("OtpScreen: Invalid OTP entered")
                    isError = true
                    errorMessage = "Please enter valid OTP"
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onBack) {
            Text(
                text = "Back to Login",
                color = Color(0xFF163C66),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
