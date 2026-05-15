package features.app.subscription

actual suspend fun presentRazorpayCheckout(args: RazorpayCheckoutArgs): RazorpayCheckoutOutcome =
    RazorpayCheckoutOutcome.Unsupported
