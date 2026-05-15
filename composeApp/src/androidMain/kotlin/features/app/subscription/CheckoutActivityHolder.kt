package features.app.subscription

import androidx.fragment.app.FragmentActivity
import java.lang.ref.WeakReference

internal object CheckoutActivityHolder {
    private var ref: WeakReference<FragmentActivity>? = null

    fun attach(activity: FragmentActivity) {
        ref = WeakReference(activity)
    }

    fun detach(activity: FragmentActivity) {
        if (ref?.get() === activity) ref = null
    }

    fun peek(): FragmentActivity? = ref?.get()
}
