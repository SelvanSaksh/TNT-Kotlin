package components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import theme.DarkGray
import theme.White

@Composable
fun OtpInputField(
    otp: String,
    onOtpChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
    isError: Boolean = false,
    errorMessage: String? = null,
    isLoading: Boolean = false,
    onComplete: (String) -> Unit = {},
    onResendClick: (() -> Unit)? = null,
    resendTimerSeconds: Int = 30
) {
    val focusRequester = remember { FocusRequester() }
    var remainingSeconds by remember { mutableStateOf(resendTimerSeconds) }
    var showResendTimer by remember { mutableStateOf(false) }
    var isAutoPopulated by remember(otp) { mutableStateOf(otp.length == length) }

    // OTP completion - only trigger on manual input, not auto-populate
    LaunchedEffect(otp) {
        if (otp.length == length && !isAutoPopulated) {
            onComplete(otp)
        }
        if (otp.length < length) {
            isAutoPopulated = false
        }
    }

    // Resend timer
    LaunchedEffect(showResendTimer) {
        if (showResendTimer && remainingSeconds > 0) {
            delay(1000)
            remainingSeconds--
        } else if (remainingSeconds == 0) {
            showResendTimer = false
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Hidden input field
        BasicTextField(
            value = otp,
            onValueChange = { newValue ->
                val filtered = newValue.filter { it.isDigit() }
                if (filtered.length <= length) {
                    onOtpChange(filtered)
                }
            },
            modifier = Modifier
                .size(0.dp)
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            )
        )

        // OTP boxes
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(length) { index ->
                val isFilled = index < otp.length
                val char = if (isFilled) otp[index].toString() else ""
                val isCurrent = index == otp.length

                OtpDigitBox(
                    char = char,
                    isFilled = isFilled,
                    isCurrent = isCurrent,
                    isError = isError && isFilled,
                    onClick = { /* No action - just visual */ }
                )
            }
        }

        // Error
        if (isError && errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Loading
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }

        // Resend
        if (onResendClick != null) {
            TextButton(
                onClick = {
                    onResendClick()
                    showResendTimer = true
                    remainingSeconds = resendTimerSeconds
                },
                enabled = !showResendTimer && !isLoading
            ) {
                Text(
                    text = if (showResendTimer) "Resend in ${remainingSeconds}s" else "Resend OTP",
                    color = if (showResendTimer)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun OtpDigitBox(
    char: String,
    isFilled: Boolean,
    isCurrent: Boolean,
    isError: Boolean,
    onClick: () -> Unit
) {
   val backgroundColor = White

    val borderColor = when {
        isError -> MaterialTheme.colorScheme.error
        isCurrent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }

    val borderWidth = if (isCurrent) 2.dp else 1.dp

    Box(
        modifier = Modifier
            .size(45.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(backgroundColor)
            .border(borderWidth, DarkGray, MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isFilled) char else "",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            fontSize = 24.sp
        )
    }
}
