package navigation.appscreen

import androidx.navigation.NamedNavArgument

sealed class Screens (val destRoute: String, val navArgument: List<NamedNavArgument> = emptyList()) {
    data object SplashScreen : Screens("splash_screen")
    data object LoginScreen : Screens("login_screen")
    data object OTPScreen : Screens("otp_screen")
    data object HomeScreen : Screens("home_screen")
    data object GenerateCodeScreen : Screens("generate_code_screen")
    data object Scan : Screens("Scan")
    data object GS12DBarcode : Screens("GS12DBarcode")
    data object GS1DigitalBarcodeScreen : Screens("GS1DigitalBarcodeScreen")
    data object MultiLinkBarcodeScreen : Screens("MultiLinkBarcodeScreen")
    data object CommonBarcodeScreen : Screens("CommonBarcodeScreen")
    data object Assets : Screens("Assets")




    data object CurrentAffairsList: Screens("current_affairs_list/{selected_date}") {
        fun createRoute(selectedDate: String): String {
            return "current_affairs_list/$selectedDate"
        }
    }
}