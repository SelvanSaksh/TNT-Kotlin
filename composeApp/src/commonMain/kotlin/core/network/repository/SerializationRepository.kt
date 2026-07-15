package core.network.repository

import core.network.models.AggregateSerialRequest
import core.network.models.AggregateSerialResponse
import core.network.models.CommissionSerialRequest
import core.network.models.CommissionSerialResponse
import core.network.models.DispenseSerialRequest
import core.network.models.DispenseSerialResponse
import core.network.models.EpcisFlowEventRequest
import core.network.models.EpcisFlowEventResponse
import core.network.models.EpcisL2PackRequest
import core.network.models.EpcisL2PackResponse
import core.network.models.EpcisL3AggregateRequest
import core.network.models.EpcisL3AggregateResponse
import core.network.models.EventLineageResponse
import core.network.models.GenerateBarcodeRequest
import core.network.models.GenerateBarcodeResponse
import core.network.models.GenerationLogRequest
import core.network.models.GtinLookupResponse
import core.network.models.L3ShipmentRequest
import core.network.models.L3ShipmentResponse
import core.network.models.L3ByGtinResponse
import core.network.models.L4ReceiveRequest
import core.network.models.L4ReceiveResponse
import core.network.models.L4VerifyRequest
import core.network.models.L4VerifyResponse
import core.network.models.RecallSerialRequest
import core.network.models.RecallSerialResponse
import core.network.models.SerialMasterResponse
import core.network.models.SerialPoolDto
import core.network.models.VrsVerifyRequest
import core.network.models.VrsVerifyResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer
import network.Config
import network.HttpClientFactory
import utils.EpcUriBuilder

/**
 * Serialization / VRS / EPCIS APIs for pharma traceability.
 */
class SerializationRepository {

    private val client get() = HttpClientFactory.httpClient
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    suspend fun verifySerial(request: VrsVerifyRequest): VrsVerifyResponse =
        post("/serialization/vrs/verify", request)

    suspend fun getSerial(epcUri: String, companyId: Int): SerialMasterResponse {
        val encoded = EpcUriBuilder.encodeEpcUriForPath(epcUri)
        return get("/serialization/serials/$encoded", mapOf("companyId" to companyId.toString()))
    }

    suspend fun getEventLineage(epcUri: String): EventLineageResponse {
        val encoded = EpcUriBuilder.encodeEpcUriForPath(epcUri)
        return get("/serialization/serials/$encoded/event-lineage")
    }

    suspend fun logScan(body: GenerationLogRequest): Unit =
        postUnit("/companies/barcode/generation", body)

    suspend fun generateBarcode(body: GenerateBarcodeRequest): GenerateBarcodeResponse =
        post("/serialization/barcodes/generate", body)

    suspend fun commissionSerial(body: CommissionSerialRequest): CommissionSerialResponse =
        post("/serialization/serials/commission", body)

    suspend fun aggregateSerial(body: AggregateSerialRequest): AggregateSerialResponse =
        post("/serialization/serials/aggregate", body)

    suspend fun postEpcisEvent(body: EpcisFlowEventRequest): EpcisFlowEventResponse =
        post("/serialization/epcis/events", body)

    suspend fun dispenseSerial(body: DispenseSerialRequest): DispenseSerialResponse =
        post("/serialization/serials/dispense", body)

    suspend fun recallSerial(body: RecallSerialRequest): RecallSerialResponse =
        post("/serialization/serials/recall", body)

    suspend fun aggregateL2Pack(body: EpcisL2PackRequest): EpcisL2PackResponse =
        post("/serialization/serials/aggregate", body)

    suspend fun aggregateL3(body: EpcisL3AggregateRequest): EpcisL3AggregateResponse =
        post("/serialization/serials/aggregate", body)

    suspend fun shipL3(body: L3ShipmentRequest): L3ShipmentResponse =
        post("/serialization/shipments/l3", body)

    suspend fun verifyL4(body: L4VerifyRequest): L4VerifyResponse =
        post("/serialization/receipts/l4/verify", body)

    suspend fun receiveL4(body: L4ReceiveRequest): L4ReceiveResponse =
        post("/serialization/receipts/l4", body)

    suspend fun fetchL3ByGtin(companyId: Int, gtin: String): L3ByGtinResponse {
        val encodedGtin = java.net.URLEncoder.encode(gtin, "UTF-8")
        val response = client.get(Config.BASE_URL + "/serialization/shipments/l3/by-gtin") {
            parameter("companyId", companyId.toString())
            parameter("gtin", encodedGtin)
        }
        if (!response.status.isSuccess()) {
            throw IllegalStateException("L3 lookup failed: ${response.status}")
        }
        return response.body()
    }

    suspend fun lookupGtin(companyId: Int, gtin: String): GtinLookupResponse {
        val encodedGtin = java.net.URLEncoder.encode(gtin, "UTF-8")
        val response = client.get(Config.BASE_URL + "/productmaster/lookup/gtin") {
            parameter("companyId", companyId.toString())
            parameter("gtin", encodedGtin)
        }
        if (!response.status.isSuccess()) {
            throw SerializationException(parseMessage(response.bodyAsText()))
        }
        return response.body()
    }

    suspend fun listPools(companyId: Int, gtin: String? = null): List<SerialPoolDto> {
        val query = buildMap {
            put("companyId", companyId.toString())
            gtin?.takeIf { it.isNotBlank() }?.let { put("gtin", it) }
        }
        val raw = getText("/serialization/pools", query)
        return decodeList(raw)
    }

    private suspend inline fun <reified Req, reified Res> post(path: String, payload: Req): Res {
        println("=== SERIALIZATION POST === path: $path")
        println("=== SERIALIZATION POST === body: $payload")
        val response = client.post(Config.BASE_URL + path) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        println("=== SERIALIZATION POST === status: ${response.status}")
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            println("=== SERIALIZATION POST === error body: $body")
            throw SerializationException(parseMessage(body))
        }
        return response.body()
    }

    private suspend inline fun <reified Req> postUnit(path: String, payload: Req) {
        val response = client.post(Config.BASE_URL + path) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            throw SerializationException(parseMessage(response.bodyAsText()))
        }
    }

    private suspend inline fun <reified Res> get(path: String, query: Map<String, String> = emptyMap()): Res {
        val response = client.get(Config.BASE_URL + path) {
            query.forEach { (k, v) -> parameter(k, v) }
        }
        if (!response.status.isSuccess()) {
            throw SerializationException(parseMessage(response.bodyAsText()))
        }
        return response.body()
    }

    private suspend fun getText(path: String, query: Map<String, String>): String {
        val response = client.get(Config.BASE_URL + path) {
            query.forEach { (k, v) -> parameter(k, v) }
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw SerializationException(parseMessage(body))
        }
        return body
    }

    private inline fun <reified T> decodeList(raw: String): List<T> {
        val element = json.parseToJsonElement(raw)
        val deserializer = serializer<T>()
        return when (element) {
            is JsonArray ->
                element.mapNotNull { runCatching { json.decodeFromJsonElement(deserializer, it) }.getOrNull() }
            is JsonObject -> {
                val array = element["data"] ?: element["pools"] ?: return emptyList()
                if (array is JsonArray) {
                    array.mapNotNull { runCatching { json.decodeFromJsonElement(deserializer, it) }.getOrNull() }
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }

    private fun parseMessage(raw: String): String {
        return runCatching {
            json.parseToJsonElement(raw).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: raw.ifBlank { "Request failed" }
    }
}

class SerializationException(message: String) : Exception(message)
