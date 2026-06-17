package features.app.warehouse.wms

import core.storage.SessionManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import network.models.UserDetail

object WmsSession {
    fun companyId(session: SessionManager): Int =
        session.getCompanyId()?.toIntOrNull()?.takeIf { it > 0 }
            ?: parseCompanyFromDetail(session)
            ?: 0

    fun userId(session: SessionManager): Int = session.getUserId() ?: 0

    private fun parseCompanyFromDetail(session: SessionManager): Int? {
        val raw = session.getUserDetail() ?: return null
        return runCatching {
            val obj = Json { ignoreUnknownKeys = true }.parseToJsonElement(raw).jsonObject
            obj["companyid"]?.jsonPrimitive?.intOrNull
                ?: obj["company_id"]?.jsonPrimitive?.intOrNull
                ?: obj["companyId"]?.jsonPrimitive?.intOrNull
        }.getOrNull()
    }

    fun userDetail(session: SessionManager): UserDetail? {
        val raw = session.getUserDetail() ?: return null
        return runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<UserDetail>(raw) }.getOrNull()
    }

    fun userName(session: SessionManager): String {
        val detail = userDetail(session)
        val name = listOfNotNull(detail?.firstName, detail?.lastName).joinToString(" ").trim()
        return name.ifBlank { detail?.email?.substringBefore("@") ?: "User" }
    }
}
