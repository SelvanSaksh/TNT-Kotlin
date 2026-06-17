package core.session

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object WarehouseModuleParser {

    fun extractMobileModuleNames(
        topLevelModules: JsonElement?,
        accessModules: JsonElement?,
    ): List<String> {
        val fromAccess = mobileNamesFromElement(accessModules)
        if (fromAccess.isNotEmpty()) return fromAccess
        return mobileNamesFromElement(topLevelModules)
    }

    private fun mobileNamesFromElement(element: JsonElement?): List<String> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull?.takeIf { it.isNotBlank() }
                is JsonObject -> {
                    val name = item["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (name.isEmpty()) return@mapNotNull null
                    val type = item["moduleType"]?.jsonPrimitive?.contentOrNull?.trim()?.lowercase().orEmpty()
                    if (type == "mobile" || type.isEmpty()) name else null
                }
                else -> null
            }
        }.distinct()
    }
}
