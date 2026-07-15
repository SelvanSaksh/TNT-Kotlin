package network

/**
 * Production API host only. All app API calls use this HTTPS URL.
 * Backend: deployed TrackandTrace-backend (same contract as iOS).
 */
object Config {
    const val BASE_URL = "https://api.tnt.sakksh.com"

    /** Fallback company id for guest scan/generation audit when session has none. */
    const val DEFAULT_COMPANY_ID = 4
}
