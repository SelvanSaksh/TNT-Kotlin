package core.network.models

import kotlinx.serialization.Serializable

/**
 * Payload for `POST /productmaster/scan/authenticate`.
 *
 * Property names are the wire names: `encrypted_text` carries AI 98 and
 * `company_id` carries AI 97, both lifted out of the scanned Digital Link.
 */
@Serializable
data class BarcodeAuthRequest(
    val barcode_data: String,
    val encrypted_text: String,
    val company_id: String,
)
