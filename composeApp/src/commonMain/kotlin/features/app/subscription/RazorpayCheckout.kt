package features.app.subscription

data class RazorpayCheckoutArgs(
    val keyId: String,
    val orderId: String,
    val businessName: String,
    val description: String,
    val customerEmail: String?,
)

sealed class RazorpayCheckoutOutcome {
    data class Success(
        val paymentId: String,
        val orderId: String,
        val signature: String,
    ) : RazorpayCheckoutOutcome()

    data class Error(val message: String) : RazorpayCheckoutOutcome()
    data object Cancelled : RazorpayCheckoutOutcome()
    data object Unsupported : RazorpayCheckoutOutcome()
}

expect suspend fun presentRazorpayCheckout(args: RazorpayCheckoutArgs): RazorpayCheckoutOutcome
