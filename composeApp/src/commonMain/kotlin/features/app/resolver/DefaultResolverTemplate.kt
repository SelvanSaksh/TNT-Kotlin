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
)

data class DefaultResolverTemplateData(
    val productName: String,
    val brandLine: String,
    val manufacturer: String,
    val imageUrl: String? = null,
    val website: String? = null,
    val detailRows: List<Pair<String, String>> = emptyList(),
    val timesScanned: String,
    val scanLocation: String,
    val regionMatch: Boolean?,
    val regionTitle: String,
    val regionBody: String,
    val brandActionLabel: String,
    val related: List<DefaultRelatedProduct>,
    val trace: List<DefaultTraceStep>,
    val firstScan: Boolean,
)

fun defaultTemplateFromDetails(
    root: JsonObject?,
    scanBatch: String?,
    scanMfg: String?,
    scanExpiry: String?,
    deviceCity: String?,
    locationMatched: Boolean?,
    expectedLocation: String?,
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
    val address = listOf(
        cmsString(company?.get("address1")),
        cmsString(company?.get("city")),
    ).mapNotNull { it?.takeIf { part -> part.isNotBlank() } }.joinToString(", ")

    val mfg = scanMfg ?: cmsString(batch?.get("manufacturingDate")) ?: cmsString(batch?.get("mfgDate"))
    val exp = scanExpiry ?: cmsString(batch?.get("expiryDate"))
    val mfgExp = listOfNotNull(mfg, exp).joinToString(" – ")

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

    val rows = buildList {
        cmsString(product?.get("description"))?.takeIf { it.isNotBlank() }?.let { add("Description" to it) }
        if (companyName.isNotBlank()) add("Manufacturer" to companyName)
        if (hq.isNotBlank()) add("HQ / Location" to hq)
        if (address.isNotBlank() && address != hq) add("Address" to address)
        if (mfgExp.isNotBlank()) add("Mfg / Exp Date" to mfgExp)
        cmsString(product?.get("hsn"))?.takeIf { it.isNotBlank() }?.let { add("HSN" to it) }
        cmsString(product?.get("countryOfOrigin"))?.takeIf { it.isNotBlank() }?.let { add("Origin" to it) }
        mrp?.let { add("MRP" to it.toString().trimEnd('0').trimEnd('.')) }
        cmsString(product?.get("identifier"))?.takeIf { it.isNotBlank() }?.let { add("GTIN" to it) }
        cmsString(site?.get("locationName"))?.takeIf { it.isNotBlank() }?.let { add("Location" to it) }
        expected.takeIf { it.isNotBlank() }?.let { add("Authorised location" to it) }
    }

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

    val trace = buildList {
        if (hq.isNotBlank() || companyName.isNotBlank()) {
            add(DefaultTraceStep("Brand / HQ", hq.ifBlank { companyName }))
        }
        cmsString(site?.get("locationName"))?.takeIf { it.isNotBlank() }?.let { name ->
            add(
                DefaultTraceStep(
                    name,
                    listOf(
                        cmsString(site?.get("city")),
                        cmsString(site?.get("state")),
                    ).mapNotNull { it?.takeIf { part -> part.isNotBlank() } }.joinToString(", "),
                ),
            )
        }
        if (batchLabel != null) {
            add(DefaultTraceStep("Batch", batchLabel))
        }
        if (locationMatched == false) {
            add(
                DefaultTraceStep(
                    "⚠ Diversion Alert — Retail Pharmacy",
                    "Scanned in $location" +
                        if (expected.isNotBlank()) " — outside licensed $expected zone" else "",
                ),
            )
        } else {
            add(DefaultTraceStep("This scan", location))
        }
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
        detailRows = rows,
        timesScanned = times,
        scanLocation = location,
        regionMatch = locationMatched,
        regionTitle = regionTitle,
        regionBody = regionBody,
        brandActionLabel = "More From\n${companyName.ifBlank { brand }.ifBlank { "Brand" }}",
        related = related,
        trace = trace,
        firstScan = firstScan,
    )
}

private fun firstProductImage(images: JsonElement?): String? {
    val fromArray = cmsArray(images)?.firstOrNull()
    val raw = cmsUnwrappedString(fromArray) ?: cmsUnwrappedString(images)
    return cmsMakeURL(raw)
}
