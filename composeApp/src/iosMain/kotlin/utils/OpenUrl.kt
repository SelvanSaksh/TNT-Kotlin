package utils

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl)
}

actual fun openGeneratedPdf(fileName: String, title: String, lines: List<String>) {
    openUrl("https://ratifye.ai/report")
}
