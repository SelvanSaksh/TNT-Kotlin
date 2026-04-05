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

val Brand = Color(0xFF163C66)
val BrandLight = Color(0xFFE8EFF7)

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
    backgroundColor: Color = Color.White,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    placeholderColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    errorColor: Color = MaterialTheme.colorScheme.error,
    focusedBorderColor: Color = Brand,
    unfocusedBorderColor: Color = Color(0xFFB0BEC5),
    successColor: Color = Brand,
    showSuccessIndicator: Boolean = false,
    isSuccess: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            isError -> errorColor
            isSuccess && showSuccessIndicator -> successColor
            isFocused -> focusedBorderColor
            else -> unfocusedBorderColor
        },
        animationSpec = tween(durationMillis = 200)
    )

    val animatedElevation by animateDpAsState(
        targetValue = if (isFocused) 2.dp else elevation,
        animationSpec = tween(durationMillis = 200)
    )

    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isFocused) 2.dp else borderWidth,
        animationSpec = tween(durationMillis = 200)
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = if (isError) errorColor else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = animatedElevation, shape = shape, clip = true)
                .clip(shape)
                .border(width = animatedBorderWidth, color = animatedBorderColor, shape = shape)
                .background(
                    color = if (isFocused) BrandLight else if (enabled) Color.White else Color.White.copy(alpha = 0.5f),
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
                label = null,
                isError = isError,
                enabled = enabled,
                readOnly = readOnly,
                singleLine = singleLine,
                maxLines = maxLines,
                visualTransformation = visualTransformation,
                keyboardOptions = keyboardOptions,
                interactionSource = interactionSource,
                textStyle = TextStyle(
                    color = if (enabled) Brand else disabledColor,
                    fontSize = 16.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Brand,
                    unfocusedTextColor = textColor,
                    disabledTextColor = disabledColor,
                    cursorColor = Brand,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    errorBorderColor = Color.Transparent,
                    errorCursorColor = errorColor
                ),
                shape = shape
            )
        }

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