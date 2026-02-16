package features

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.painterResource
import theme.Black
import theme.White
import sakkshasset.composeapp.generated.resources.Res
import sakkshasset.composeapp.generated.resources.logo

@OptIn(ExperimentalResourceApi::class)
@Composable
fun InitialScreen() {
    val bgColor = White
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(180.dp)
        )
    }
}
