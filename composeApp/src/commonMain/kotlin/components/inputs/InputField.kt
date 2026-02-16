package components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import theme.Black
import theme.White
import theme.DarkGray
/**
 * Production-ready modern InputField component without icons
 */
@Composable
fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    shape: Shape = MaterialTheme.shapes.medium,
    elevation: Dp = 0.dp,
    borderWidth: Dp = 1.dp,
    cornerRadius: Dp = 12.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    placeholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    errorColor: Color = MaterialTheme.colorScheme.error,
    focusedBorderColor: Color = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor: Color = MaterialTheme.colorScheme.outline,
    successColor: Color = MaterialTheme.colorScheme.primary,
    showSuccessIndicator: Boolean = false,
    isSuccess: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current

    // Animated border color
    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            isError -> errorColor
            isSuccess && showSuccessIndicator -> successColor
            isFocused -> focusedBorderColor
            else -> unfocusedBorderColor
        },
        animationSpec = tween(durationMillis = 200)
    )

    // Animated elevation
    val animatedElevation by animateDpAsState(
        targetValue = if (isFocused) 2.dp else elevation,
        animationSpec = tween(durationMillis = 200)
    )

    // Animated border width
    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isFocused) 1.5.dp else borderWidth,
        animationSpec = tween(durationMillis = 200)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Label
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) errorColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // TextField Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = animatedElevation,
                    shape = shape,
                    clip = true
                )
                .clip(shape)
                .border(
                    width = animatedBorderWidth,
                    color = DarkGray,
                    shape = shape
                )
                .background(
                    color = if (enabled) White
                    else White.copy(alpha = 0.5f),
                    shape = shape
                )
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused && value.isNotEmpty() && singleLine) {
                            focusManager.clearFocus()
                        }
                    },
                placeholder = {
                    Text(
                        text = placeholder,
                        color = placeholderColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                label = null, // We handle label externally
                isError = isError,
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                maxLines = maxLines,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                interactionSource = interactionSource,
                textStyle = TextStyle(
                    color = if (enabled) textColor else disabledColor,
                    fontSize = 16.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = textColor,
                    unfocusedTextColor = textColor,
                    disabledTextColor = disabledColor,
                    cursorColor = focusedBorderColor,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    errorBorderColor = Color.Transparent,
                    errorCursorColor = errorColor
                ),
                shape = shape
            )
        }

        // Error/Success message
        if (errorMessage != null && isError) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.labelSmall,
                color = errorColor,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
@Composable
fun PasswordInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Password",
    modifier: Modifier = Modifier,
    label: String? = "Password",
    isError: Boolean = false,
    errorMessage: String? = null
) {
    InputField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        label = label,
        isError = isError,
        errorMessage = errorMessage,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
}

@Composable
fun EmailInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Email",
    modifier: Modifier = Modifier,
    label: String? = "Email",
    isError: Boolean = false,
    errorMessage: String? = null,
    showSuccessIndicator: Boolean = true
) {
    // Simple email validation regex
    fun isValidEmail(email: String): Boolean {
        val emailRegex = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$")
        return emailRegex.matches(email.trim())
    }

    val isEmailValid = remember(value) {
        isValidEmail(value)
    }

    InputField(
        value = value,
        onValueChange = onValueChange,
        placeholder = placeholder,
        modifier = modifier,
        label = label,
        isError = isError,
        errorMessage = errorMessage,
        showSuccessIndicator = showSuccessIndicator,
        isSuccess = isEmailValid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
}

@Composable
fun SearchInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "Search...",
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
    onClear: (() -> Unit)? = null
) {
    var isActive by remember { mutableStateOf(false) }

    InputField(
        value = value,
        onValueChange = {
            onValueChange(it)
            isActive = it.isNotEmpty()
        },
        placeholder = placeholder,
        modifier = modifier,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        shape = MaterialTheme.shapes.large,
        cornerRadius = 24.dp
    )
}