package features.app.subscription

import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import com.razorpay.Checkout
import com.razorpay.PaymentData
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

internal object RazorpayCheckoutCoordinator {

    private var pending: kotlinx.coroutines.CancellableContinuation<RazorpayCheckoutOutcome>? = null

    fun onPaymentSuccessPublic(razorpayPaymentID: String?, paymentData: PaymentData?) {
        val cont = pending ?: return
        pending = null
        try {
            val data = paymentData?.data
            val paymentId = data?.optString("razorpay_payment_id").orEmpty()
                .ifBlank { razorpayPaymentID.orEmpty() }
            val orderId = data?.optString("razorpay_order_id").orEmpty()
            val signature = data?.optString("razorpay_signature").orEmpty()
            if (paymentId.isBlank() || orderId.isBlank() || signature.isBlank()) {
                cont.resume(
                    RazorpayCheckoutOutcome.Error("Missing payment verification fields from Razorpay.")
                )
            } else {
                cont.resume(RazorpayCheckoutOutcome.Success(paymentId, orderId, signature))
            }
        } catch (e: Exception) {
            cont.resume(RazorpayCheckoutOutcome.Error(e.message ?: "Payment parse failed"))
        }
    }

    fun onPaymentErrorPublic(code: Int, response: String?, paymentData: PaymentData?) {
        val cont = pending ?: return
        pending = null
        if (code == Checkout.PAYMENT_CANCELED) {
            cont.resume(RazorpayCheckoutOutcome.Cancelled)
        } else {
            val msg = response?.takeIf { it.isNotBlank() }
                ?: paymentData?.data?.optString("error").orEmpty().ifBlank { null }
                ?: "Payment failed (code $code)"
            cont.resume(RazorpayCheckoutOutcome.Error(msg))
        }
    }

    private fun openCheckout(activity: FragmentActivity, args: RazorpayCheckoutArgs) {
        val checkout = Checkout()
        checkout.setKeyID(args.keyId)
        val options = JSONObject()
        options.put("name", args.businessName)
        options.put("description", args.description)
        options.put("order_id", args.orderId)
        options.put("currency", "INR")
        args.customerEmail?.trim()?.takeIf { it.isNotEmpty() }?.let { email ->
            options.put("prefill", JSONObject().put("email", email))
        }
        checkout.open(activity, options)
    }

    suspend fun present(args: RazorpayCheckoutArgs): RazorpayCheckoutOutcome =
        suspendCancellableCoroutine { cont ->
            val activity = CheckoutActivityHolder.peek()
            if (activity == null) {
                cont.resume(RazorpayCheckoutOutcome.Error("App screen is not ready. Try again."))
                return@suspendCancellableCoroutine
            }
            if (pending != null) {
                cont.resume(RazorpayCheckoutOutcome.Error("Another payment is already in progress."))
                return@suspendCancellableCoroutine
            }
            pending = cont
            cont.invokeOnCancellation {
                if (pending === cont) pending = null
            }
            Handler(Looper.getMainLooper()).post {
                try {
                    openCheckout(activity, args)
                } catch (e: Exception) {
                    if (pending === cont) {
                        pending = null
                        cont.resume(RazorpayCheckoutOutcome.Error(e.message ?: "Could not open Razorpay"))
                    }
                }
            }
        }
}

actual suspend fun presentRazorpayCheckout(args: RazorpayCheckoutArgs): RazorpayCheckoutOutcome =
    RazorpayCheckoutCoordinator.present(args)
