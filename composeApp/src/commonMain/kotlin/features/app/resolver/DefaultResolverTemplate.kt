package features.app.resolver

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import resolver.cms.cmsArray
import resolver.cms.cmsDict
import resolver.cms.cmsDouble
import resolver.cms.cmsMakeURL
import resolver.cms.cmsString
import resolver.cms.cmsUnwrappedString

data class DefaultRelatedProduct(
    val name: String,
    val subtitle: String,
    val imageUrl: String? = null,
)

data class DefaultTraceStep(
    val title: String,
    val subtitle: String,
    val flagged: Boolean = false,
)

enum class DefaultChainIcon { FACTORY, WAREHOUSE, DISTRIBUTOR, RETAIL, ALERT }

data class DefaultChainStep(
    val label: String,
    val subtitle: String,
    val note: String? = null,
    val icon: DefaultChainIcon,
    val flagged: Boolean = false,
)

data class DefaultIdentifierRow(
    val label: String,
    val value: String,
    val mono: Boolean = false,
)

data class DefaultResolverTemplateData(
    val productName: String,
    val brandLine: String,
    val manufacturer: String,
    val imageUrl: String? = null,
    val website: String? = null,
    val identifierRows: List<DefaultIdentifierRow> = emptyList(),
    val timesScanned: String,
    val scanLocation: String,
    val regionMatch: Boolean?,
    val regionTitle: String,
    val regionBody: String,
    val brandActionLabel: String,
    val related: List<DefaultRelatedProduct>,
    val chain: List<DefaultChainStep>,
    val scans: List<DefaultTraceStep>,
    val authorisedLocation: String,
    val firstScan: Boolean,
)

data class ScanEvent(
    val location: String?,
    val coords: String?,
    val whenTime: String?,
    val result: String?,
    val flagged: Boolean,
)

private fun readablePlace(value: JsonElement?): String? {
    val raw = cmsString(value)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (raw.startsWith("urn:", ignoreCase = true)) return null
    if (raw.equals("unknown", ignoreCase = true)) return null
    val latLngPattern = Regex("""^-?\d+(\.\d+)?\s*,\s*-?\d+(\.\d+)?$""")
    if (latLngPattern.matches(raw)) return null
    return raw
}

fun scanEventsFromDetails(root: JsonObject?): List<ScanEvent> {
    val rows = cmsArray(cmsDict(root?.get("scannedDetails"))?.get("data")) ?: JsonArray(emptyList())
    val events = mutableListOf<ScanEvent>()
    for (row in rows.take(8)) {
        val dict = cmsDict(row) ?: continue
        val place = readablePlace(dict["geo_location"])
            ?: readablePlace(dict["geoLocation"])
            ?: readablePlace(dict["city"])
            ?: readablePlace(dict["scanCity"])
            ?: readablePlace(dict["address"])
            ?: readablePlace(dict["location"])
        val lat = cmsDouble(dict["lat"])
        val lng = cmsDouble(dict["longitude"]) ?: cmsDouble(dict["lng"])
        val coords = if (
            lat != null && lng != null &&
            (kotlin.math.abs(lat) > 0.001 || kotlin.math.abs(lng) > 0.001)
        ) {
            val sLat = (lat * 10000).toLong()
            val sLng = (lng * 10000).toLong()
            val fLat = "${sLat / 10000}.${kotlin.math.abs(sLat % 10000).toString().padStart(4, '0')}"
            val fLng = "${sLng / 10000}.${kotlin.math.abs(sLng % 10000).toString().padStart(4, '0')}"
            "$fLat, $fLng"
        } else {
            null
        }
        val whenTime = cmsString(dict["eventTime"])
            ?: cmsString(dict["event_time"])
            ?: cmsString(dict["scanTime"])
            ?: cmsString(dict["createdAt"])
            ?: cmsString(dict["created_at"])
        val result = (cmsString(dict["authResult"]) ?: cmsString(dict["auth_result"]) ?: "")
            .trim().uppercase().ifBlank { null }
        events.add(
            ScanEvent(
                location = place,
                coords = coords,
                whenTime = whenTime?.take(10),
                result = result,
                flagged = result == "DIVERTED" || result == "COUNTERFEIT",
            ),
        )
    }
    return events
}

private fun ordinal(n: Int): String {
    val suffix = when {
        n % 100 in 11..13 -> "th"
        n % 10 == 1 -> "st"
        n % 10 == 2 -> "nd"
        n % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$n$suffix"
}

fun defaultTemplateFromDetails(
    root: JsonObject?,
    scanBatch: String?,
    scanMfg: String?,
    scanExpiry: String?,
    deviceCity: String?,
    deviceAddress: String? = null,
    locationMatched: Boolean?,
    expectedLocation: String?,
    gtin: String? = null,
    serial: String? = null,
): DefaultResolverTemplateData {
    val product = cmsDict(root?.get("product"))
    val company = cmsDict(root?.get("companyDetails"))
    val batch = cmsDict(root?.get("batchDetails"))
    val site = cmsDict(root?.get("locationDetails"))
    val scanned = cmsDict(root?.get("scannedDetails"))

    val productName = cmsString(product?.get("productName"))?.takeIf { it.isNotBlank() } ?: "Product"
    val brand = cmsString(product?.get("brandName"))?.takeIf { it.isNotBlank() }.orEmpty()
    val companyName = cmsString(product?.get("companyName"))
        ?: cmsString(company?.get("companyName"))
        ?: ""
    val hq = listOf(
        cmsString(company?.get("companyHq")),
        cmsString(company?.get("city")),
        cmsString(company?.get("state")),
        cmsString(company?.get("country")),
    ).mapNotNull { it?.takeIf { part -> part.isNotBlank() } }
        .distinct()
        .joinToString(", ")

    val mfg = scanMfg ?: cmsString(batch?.get("manufacturingDate")) ?: cmsString(batch?.get("mfgDate"))
    val exp = scanExpiry ?: cmsString(batch?.get("expiryDate"))

    val batchLabel = scanBatch
        ?: cmsString(batch?.get("batchNumber"))
        ?: cmsString(batch?.get("batch"))
    val times = cmsDouble(scanned?.get("total"))?.toInt()?.toString() ?: "0"
    val firstScan = (cmsDouble(scanned?.get("total")) ?: 0.0) <= 1.0
    val location = deviceCity?.takeIf { it.isNotBlank() }
        ?: cmsString(company?.get("city"))
        ?: "—"
    val websiteRaw = cmsString(company?.get("website"))?.trim().orEmpty()
    val website = when {
        websiteRaw.startsWith("http://") || websiteRaw.startsWith("https://") -> websiteRaw
        websiteRaw.contains('.') -> "https://$websiteRaw"
        else -> null
    }
    val mrp = cmsDouble(product?.get("mrp"))?.takeIf { it > 0 }

    val invoice = cmsDict(root?.get("invoiceDetails"))
    val expected = expectedLocation
        ?: listOf(
            cmsString(invoice?.get("customerCity")),
            cmsString(invoice?.get("customerState")),
        ).mapNotNull { it?.takeIf { part -> part.isNotBlank() } }.distinct().joinToString(", ")
            .ifBlank {
                listOf(
                    cmsString(site?.get("city")),
                    cmsString(site?.get("state")),
                ).mapNotNull { it?.takeIf { part -> part.isNotBlank() } }.joinToString(", ")
            }

    val identifier = gtin?.trim()?.takeIf { it.isNotEmpty() }
        ?: cmsString(product?.get("identifier"))?.trim()?.takeIf { it.isNotEmpty() }
        ?: ""
    val serialValue = serial?.trim()?.takeIf { it.isNotEmpty() } ?: ""

    val rows = mutableListOf<DefaultIdentifierRow>()
    fun pushRow(label: String, value: String?, mono: Boolean = false) {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() && it != "0" }
        if (clean != null) rows.add(DefaultIdentifierRow(label, clean, mono))
    }

    pushRow("GTIN", identifier, true)
    pushRow("Manufacturer", companyName.ifBlank { brand })
    pushRow("Batch No.", batchLabel, true)
    pushRow("Serial", serialValue, true)
    pushRow("Expiry", exp)
    mrp?.let { pushRow("MRP", it.toString().trimEnd('0').trimEnd('.')) }

    val related = (cmsArray(root?.get("similarProducts")) ?: JsonArray(emptyList())).mapNotNull { item ->
        val dict = cmsDict(item) ?: return@mapNotNull null
        val name = cmsString(dict["productName"])?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        DefaultRelatedProduct(
            name = name,
            subtitle = cmsString(dict["brandName"]) ?: companyName,
            imageUrl = firstProductImage(dict["images"]),
        )
    }

    val regionTitle = when (locationMatched) {
        true -> "LOCATION VERIFIED"
        false -> "DIVERSION DETECTED"
        null -> "Location check"
    }
    val regionBody = when (locationMatched) {
        true -> "Current location matches the authorised location" +
            (if (expected.isNotBlank()) " ($expected)" else "") + "."
        false -> {
            val zone = expected.ifBlank { "its authorised region" }
            val scan = deviceCity?.takeIf { it.isNotBlank() } ?: "a different location"
            "This batch is licensed for retail sale in $zone — this serial was scanned in $scan, outside its licensed zone. Flagged for the brand-protection team."
        }
        null -> "Share your location to verify this pack against the invoice destination."
    }

    val chain = mutableListOf<DefaultChainStep>()
    if (expected.isNotBlank()) {
        chain.add(
            DefaultChainStep(
                label = "Invoice destination",
                subtitle = expected,
                icon = DefaultChainIcon.DISTRIBUTOR,
            ),
        )
    }
    val scanAddress = deviceAddress?.trim()?.takeIf { it.isNotBlank() } ?: ""
    chain.add(
        DefaultChainStep(
            label = if (location == "—") "Scanned location" else "Scanned at $location",
            subtitle = scanAddress.ifBlank {
                if (location == "—") "Awaiting device location" else location
            },
            note = when (locationMatched) {
                true -> "Within licensed zone"
                false -> "Outside licensed zone"
                null -> null
            },
            icon = DefaultChainIcon.RETAIL,
        ),
    )
    if (locationMatched == false) {
        chain.add(
            DefaultChainStep(
                label = "⚠ Diversion Alert",
                subtitle = "Scanned in $location" +
                    if (expected.isNotBlank()) " — outside licensed $expected zone" else "",
                icon = DefaultChainIcon.ALERT,
                flagged = true,
            ),
        )
    }

    val scans = mutableListOf<DefaultTraceStep>()
    val scanEvents = scanEventsFromDetails(root)
    scanEvents.forEachIndexed { index, event ->
        val place = event.location ?: event.coords ?: return@forEachIndexed
        scans.add(
            DefaultTraceStep(
                title = if (event.flagged) "⚠ $place" else place,
                subtitle = listOfNotNull(
                    "${ordinal(index + 1)} scan",
                    event.whenTime,
                ).joinToString(" · "),
                flagged = event.flagged,
            ),
        )
    }
    val thisScanFlagged = locationMatched == false
    if (location.isNotBlank() || scanAddress.isNotBlank()) {
        scans.add(
            DefaultTraceStep(
                title = if (thisScanFlagged) "⚠ $location" else location,
                subtitle = listOfNotNull(
                    scanAddress.ifBlank { null },
                    if (firstScan) "1st scan" else "${ordinal(scanEvents.size + 1)} scan",
                    when {
                        locationMatched == true -> "within licensed zone"
                        thisScanFlagged -> "outside licensed ${expected.ifBlank { "zone" }}"
                        else -> null
                    },
                ).joinToString(" · "),
                flagged = thisScanFlagged,
            ),
        )
    }

    val brandLine = listOfNotNull(
        brand.takeIf { it.isNotBlank() },
        companyName.takeIf { it.isNotBlank() && !it.equals(brand, ignoreCase = true) },
        batchLabel?.let { "Batch $it" },
    ).joinToString(" · ")

    return DefaultResolverTemplateData(
        productName = productName,
        brandLine = brandLine.ifBlank { brand.ifBlank { companyName } },
        manufacturer = companyName.ifBlank { brand }.ifBlank { "the manufacturer" },
        imageUrl = firstProductImage(product?.get("images")),
        website = website,
        identifierRows = rows,
        timesScanned = times,
        scanLocation = location,
        regionMatch = locationMatched,
        regionTitle = regionTitle,
        regionBody = regionBody,
        brandActionLabel = "More From\n${companyName.ifBlank { brand }.ifBlank { "Brand" }}",
        related = related,
        chain = chain,
        scans = scans,
        authorisedLocation = expected,
        firstScan = firstScan,
    )
}

private fun firstProductImage(images: JsonElement?): String? {
    val fromArray = cmsArray(images)?.firstOrNull()
    val raw = cmsUnwrappedString(fromArray) ?: cmsUnwrappedString(images)
    return cmsMakeURL(raw)
}
