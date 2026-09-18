package resolver.cms

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** True when the CMS bind root already carries a usable `product` object. */
fun cmsRootHasProduct(root: JsonObject): Boolean {
    val product = cmsDict(root["product"]) ?: return false
    if (product.isEmpty()) return false
    val name = cmsString(product["productName"]).orEmpty()
    val images = cmsArray(product["images"]) ?: JsonArray(emptyList())
    return name.isNotEmpty() || images.isNotEmpty() || cmsDouble(product["mrp"]) != null
}

/**
 * Maps a `/productmaster/config` response into the `product` shape that CMS
 * bindings expect, such as `product.images[0]` and `product.mrp`.
 */
fun cmsProductFromConfigData(configResponse: JsonElement?): JsonObject {
    val json = cmsDict(configResponse) ?: return JsonObject(emptyMap())
    val data = cmsDict(json["data"]) ?: json
    val basic = cmsDict(data["Basic Details"]) ?: JsonObject(emptyMap())

    return buildJsonObject {
        val name = cmsString(basic["Product Name"])
            ?: cmsString(basic["product_name"])
            ?: cmsString(data["productName"])
            ?: cmsString(data["product_name"])
        name?.takeIf { it.isNotEmpty() }?.let { put("productName", it) }
        cmsString(basic["Brand Name"])?.takeIf { it.isNotEmpty() }
            ?.let { put("brandName", it) }
        (cmsString(basic["Product Desc"]) ?: cmsString(basic["Product Details"]))
            ?.takeIf { it.isNotEmpty() }
            ?.let { put("description", it) }
        cmsString(basic["GTIN"])?.takeIf { it.isNotEmpty() }?.let {
            put("identifier", it)
            put("identifierType", "GTIN")
        }

        val images = cmsArray(basic["Product Images"])
        if (images != null && images.isNotEmpty()) {
            put("images", images)
        } else {
            cmsString(basic["Image URL"])?.takeIf { it.isNotEmpty() }
                ?.let { url -> putJsonArray("images") { add(url) } }
        }

        cmsDouble(basic["Product Mrp"])?.let { put("mrp", it) }
        cmsDouble(basic["Id"])?.let { put("productId", it.toInt()) }
    }
}

/** Injects the mapped product into the CMS bind root. */
fun enrichCmsRootWithProduct(root: JsonObject, product: JsonObject): JsonObject {
    if (product.isEmpty()) return root
    return fillProductName(
        JsonObject(
            root + mapOf(
                "product" to product,
                "exists" to JsonPrimitive(true),
            ),
        ),
    )
}

/**
 * Maps the `similarProducts` array from a `/productmaster/config` response into a
 * normalized JSON list mirroring the web resolver's `cmsSimilarProductsFromConfigData`,
 * so the CMS related-products widget can source live images and prices.
 */
fun cmsSimilarProductsFromConfigData(configResponse: JsonElement?): List<JsonObject> {
    val json = cmsDict(configResponse) ?: return emptyList()
    val data = cmsDict(json["data"]) ?: json
    val raw = cmsArray(data["similarProducts"]) ?: return emptyList()

    return raw.mapNotNull { cmsDict(it) }.map { item ->
        buildJsonObject {
            cmsDouble(item["productId"])?.let { id ->
                put("productId", id.toInt())
            } ?: cmsDouble(item["product_id"])?.let { id -> put("productId", id.toInt()) }

            val name = cmsString(item["productName"]) ?: cmsString(item["product_name"])
            name?.takeIf { it.isNotEmpty() }?.let { put("productName", it) }

            val brand = cmsString(item["brandName"]) ?: cmsString(item["brand_name"])
            brand?.takeIf { it.isNotEmpty() }?.let { put("brandName", it) }

            cmsDouble(item["mrp"])?.let { put("mrp", it) }
                ?: cmsDouble(item["product_mrp"])?.let { put("mrp", it) }

            val gtin = cmsString(item["identifier"]) ?: cmsString(item["gtin"])
            gtin?.takeIf { it.isNotEmpty() }?.let { put("gtin", it) }

            val images = cmsArray(item["images"]) ?: cmsArray(item["product_images"])
            if (images != null && images.isNotEmpty()) {
                put("images", images)
                cmsString(images.firstOrNull())?.takeIf { it.isNotEmpty() }?.let { put("image", it) }
            }
        }.takeIf { it.isNotEmpty() }
    }.filterNotNull()
}

/** Injects normalized similar products into the CMS bind root. */
fun enrichCmsRootWithSimilar(root: JsonObject, similar: List<JsonObject>): JsonObject {
    if (similar.isEmpty()) return root
    return JsonObject(
        root + mapOf(
            "similarProducts" to buildJsonArray { similar.forEach { add(it) } },
        ),
    )
}
