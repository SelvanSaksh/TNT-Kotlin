package features

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

@Composable
fun GuestSignInPromptDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    title: String = "Sign in to unlock more",
    message: String = "Sign in to use product authentication, picking, packing, analytics, history, and your full workspace. Scanning and barcode generation stay available as a guest.",
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onSignIn) {
                Text("Sign in")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Continue as guest")
            }
        },
    )
}
