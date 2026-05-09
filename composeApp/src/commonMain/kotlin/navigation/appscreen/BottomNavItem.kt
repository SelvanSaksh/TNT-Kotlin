import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    object Home : BottomNavItem("home", "Home", Icons.Filled.Home)
    object History : BottomNavItem("history", "History", Icons.Outlined.History)
    object Scan : BottomNavItem("scan", "Scan", Icons.Filled.Home)
    object Analytics : BottomNavItem("analytics", "Analytics", Icons.Outlined.BarChart)
    object Profile : BottomNavItem("profile", "Profile", Icons.Filled.Person)
}