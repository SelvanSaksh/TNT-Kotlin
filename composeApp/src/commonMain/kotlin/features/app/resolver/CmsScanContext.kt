package features.app.resolver

/**
 * Live scan data handed to CMS widgets that render runtime-authored cards
 * (pack card, field grid, scan tree, distribution path, stat chips),
 * mirroring the web resolver's `ResolverCmsScanContext`.
 */
data class CmsScanContext(
    val expectedLocation: String? = null,
    val locationAddress: String? = null,
    val locationMatched: Boolean? = null,
    val timesScanned: Double? = null,
    val scanEvents: List<ScanEvent> = emptyList(),
    val brandName: String? = null,
    val gtin: String? = null,
    val batchNumber: String? = null,
    val serialNumber: String? = null,
    val mfgDate: String? = null,
    val expiryDate: String? = null,
    val locationLabel: String? = null,
    val companyLabel: String? = null,
)