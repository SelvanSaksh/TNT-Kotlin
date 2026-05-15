package network

/**
 * Production API host only. All authenticated app calls and payment endpoints
 * (`/payments/create-order`, `/payments/verify`, `/companies/subscriptions`, …)
 * use this URL over HTTPS. There is no local or staging override in the client.
 */
object Config {
    const val BASE_URL = "https://api.tnt.sakksh.com"
}
