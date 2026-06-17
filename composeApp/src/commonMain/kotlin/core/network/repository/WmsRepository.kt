package core.network.repository

import core.network.wms.WmsAssignPackerRequest
import core.network.wms.WmsAssignPickListRequest
import core.network.wms.WmsAddPackingBoxItemRequest
import core.network.wms.WmsCreatePackingBoxRequest
import core.network.wms.WmsCreatedPackingBox
import core.network.wms.WmsPackingPickListsByPackerPayload
import core.network.wms.WmsPackingReceiverCompleteRequest
import core.network.wms.WmsPackingReceiverVerifyLinesRequest
import core.network.wms.WmsPatchPickListLinesRequest
import core.network.wms.WmsPickListItem
import core.network.wms.WmsPickListLine
import core.network.wms.WmsPickListLinesPayload
import core.network.wms.decodePickListLinesPayload
import core.network.wms.WmsPickListsPayload
import core.network.wms.WmsPackingReceiverLine
import core.network.wms.WmsPackingReceiverNode
import core.network.wms.WmsPackingReceiverReceivingStatus
import core.network.wms.WmsPackingBoxSummary
import core.network.wms.WmsPackingPickListItem
import core.network.wms.WmsStagePickListRequest
import core.network.wms.WmsUpdatePackingBoxStatusRequest
import core.network.wms.WmsUpdatePackingPickListStatusRequest
import core.network.wms.WmsUser
import core.network.wms.decodePackingBox
import core.network.wms.decodePackingBoxes
import core.network.wms.decodePackingByPackerPayload
import core.network.wms.decodePickListsPayload
import core.network.wms.decodeReceiverLines
import core.network.wms.decodeReceiverOrders
import core.network.wms.decodeReceiverReceivingStatus
import core.network.wms.decodeUsers
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import network.Config
import network.HttpClientFactory

class WmsRepository {

    private val client get() = HttpClientFactory.httpClient
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private suspend fun getText(path: String, query: Map<String, String> = emptyMap()): String {
        val response = client.get(Config.BASE_URL + path) {
            query.forEach { (k, v) -> parameter(k, v) }
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw WmsException(body.ifBlank { "Request failed (${response.status.value})" })
        }
        return body
    }

    private suspend inline fun <reified Req, reified Res> post(path: String, payload: Req): Res {
        val response = client.post(Config.BASE_URL + path) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            throw WmsException(response.bodyAsText())
        }
        return response.body()
    }

    private suspend inline fun <reified Req, reified Res> patch(path: String, payload: Req): Res {
        val response = client.patch(Config.BASE_URL + path) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            throw WmsException(response.bodyAsText())
        }
        return response.body()
    }

    private suspend inline fun <reified Req> patchRequest(path: String, payload: Req) {
        val response = client.patch(Config.BASE_URL + path) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            throw WmsException(response.bodyAsText())
        }
    }

    private suspend inline fun <reified Req> postRequest(path: String, payload: Req) {
        val response = client.post(Config.BASE_URL + path) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            throw WmsException(response.bodyAsText())
        }
    }

    // ── Picking ───────────────────────────────────────────────────────────────

    suspend fun fetchPickListsByPicker(pickerId: Int, companyId: Int): WmsPickListsPayload {
        require(pickerId > 0) { "Picker id is missing." }
        require(companyId > 0) { "Company is not configured." }
        val raw = getText("/wms/picking/lists/by-picker/$pickerId", mapOf("companyId" to companyId.toString()))
        return decodePickListsPayload(raw)
    }

    suspend fun fetchPickListLines(pickListId: String, companyId: Int): WmsPickListLinesPayload {
        require(pickListId.isNotBlank()) { "Pick list id is missing." }
        require(companyId > 0) { "Company is not configured." }
        val raw = getText("/wms/picking/lists/$pickListId/lines", mapOf("companyId" to companyId.toString()))
        return decodePickListLinesPayload(raw)
    }

    suspend fun assignPickList(
        pickListId: String,
        pickerId: Int,
        zone: String,
        toteNumber: String?,
    ) {
        patchRequest(
            "/wms/picking/lists/$pickListId/assign",
            WmsAssignPickListRequest(pickerId, zone, toteNumber),
        )
    }

    suspend fun patchPickListLines(pickListId: String, request: WmsPatchPickListLinesRequest) {
        patchRequest("/wms/picking/lists/$pickListId/lines", request)
    }

    suspend fun stagePickList(pickListId: String, request: WmsStagePickListRequest) {
        patchRequest("/wms/picking/lists/$pickListId/stage", request)
    }

    suspend fun fetchPickLists(companyId: Int, status: String? = null): WmsPickListsPayload {
        val query = buildMap {
            put("companyId", companyId.toString())
            status?.takeIf { it.isNotBlank() }?.let { put("status", it) }
        }
        val raw = getText("/wms/picking/lists", query)
        return decodePickListsPayload(raw)
    }

    // ── Packing ───────────────────────────────────────────────────────────────

    suspend fun fetchPackingPickListsByPacker(packerId: Int, companyId: Int): WmsPackingPickListsByPackerPayload {
        val raw = getText(
            "/wms/packing/pick-lists/by-packer/$packerId",
            mapOf("companyId" to companyId.toString()),
        )
        return decodePackingByPackerPayload(raw)
    }

    suspend fun fetchPackingPickLists(companyId: Int): WmsPickListsPayload {
        val raw = getText("/wms/packing/pick-lists", mapOf("companyId" to companyId.toString()))
        return decodePickListsPayload(raw)
    }

    suspend fun assignPackerToPickList(pickListId: String, packerId: Int, companyId: Int) {
        patchRequest(
            "/wms/packing/pick-lists/$pickListId/assign-packer",
            WmsAssignPackerRequest(packerId, companyId),
        )
    }

    suspend fun updatePackingPickListStatus(
        pickListId: String,
        companyId: Int,
        status: String,
        packedBy: Int?,
    ) {
        patchRequest(
            "/wms/packing/pick-lists/$pickListId/status",
            WmsUpdatePackingPickListStatusRequest(companyId, status, packedBy),
        )
    }

    private suspend inline fun <reified Req> postPackingBox(path: String, payload: Req): WmsCreatedPackingBox {
        val response = client.post(Config.BASE_URL + path) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            throw WmsException(response.bodyAsText())
        }
        return decodePackingBox(response.bodyAsText())
    }

    private suspend inline fun <reified Req> patchPackingBox(path: String, payload: Req): WmsCreatedPackingBox {
        val response = client.patch(Config.BASE_URL + path) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (!response.status.isSuccess()) {
            throw WmsException(response.bodyAsText())
        }
        return decodePackingBox(response.bodyAsText())
    }

    suspend fun createPackingBox(request: WmsCreatePackingBoxRequest): WmsCreatedPackingBox =
        postPackingBox("/wms/packing/boxes", request)

    suspend fun addItemToPackingBox(packId: String, request: WmsAddPackingBoxItemRequest): WmsCreatedPackingBox {
        val trimmedPackId = packId.trim()
        require(trimmedPackId.isNotEmpty()) { "Box id is missing." }
        // iOS uses PATCH /wms/packing/boxes/{packId}/items (not POST)
        return patchPackingBox("/wms/packing/boxes/$trimmedPackId/items", request)
    }

    suspend fun updatePackingBoxStatus(
        packId: String,
        request: WmsUpdatePackingBoxStatusRequest,
    ): WmsCreatedPackingBox {
        val trimmedPackId = packId.trim()
        require(trimmedPackId.isNotEmpty()) { "Box id is missing." }
        return patchPackingBox("/wms/packing/boxes/$trimmedPackId/status", request)
    }

    suspend fun fetchPackingBoxes(
        companyId: Int,
        packingOrderId: String? = null,
        pickListId: String? = null,
        packType: String? = null,
        status: String? = null,
        topLevelOnly: Boolean = true,
    ): List<WmsPackingBoxSummary> {
        val trimmedOrderId = packingOrderId?.trim().orEmpty()
        val trimmedPickListId = pickListId?.trim().orEmpty()
        val candidatePaths = buildList {
            if (trimmedPickListId.isNotEmpty()) {
                add("/wms/packing/pick-lists/$trimmedPickListId/boxes")
            }
            if (trimmedOrderId.isNotEmpty()) {
                add("/wms/packing/orders/$trimmedOrderId/boxes")
            }
            add("/wms/packing/boxes")
        }

        val query = buildMap {
            put("companyId", companyId.toString())
            packType?.takeIf { it.isNotBlank() }?.let { put("packType", it) }
            status?.takeIf { it.isNotBlank() }?.let { put("status", it) }
            if (topLevelOnly) put("topLevelOnly", "true")
        }

        for ((index, path) in candidatePaths.withIndex()) {
            val pathQuery = if (path == "/wms/packing/boxes" && trimmedOrderId.isNotEmpty()) {
                query + ("packingOrderId" to trimmedOrderId)
            } else {
                query
            }
            try {
                return decodePackingBoxes(getText(path, pathQuery))
            } catch (_: WmsException) {
                if (index == candidatePaths.lastIndex) return emptyList()
            }
        }
        return emptyList()
    }

    suspend fun fetchIncompletePackingBoxes(
        companyId: Int,
        packingOrderId: String? = null,
        pickListId: String? = null,
        hierarchyLevel: Int? = null,
    ): List<WmsPackingBoxSummary> {
        val boxes = fetchPackingBoxes(
            companyId = companyId,
            packingOrderId = packingOrderId,
            pickListId = pickListId,
            packType = null,
            status = "IN_PROGRESS",
            topLevelOnly = true,
        )
        return boxes
            .filter { box ->
                box.isInProgress &&
                    (hierarchyLevel == null || box.hierarchyLevel == hierarchyLevel)
            }
            .distinctBy { it.resolvedPackId }
            .sortedBy { it.displayTitle.lowercase() }
    }

    suspend fun fetchPackingBox(packId: String, companyId: Int): WmsCreatedPackingBox {
        val trimmedPackId = packId.trim()
        require(trimmedPackId.isNotEmpty()) { "Box id is missing." }
        return decodePackingBox(
            getText("/wms/packing/boxes/$trimmedPackId", mapOf("companyId" to companyId.toString())),
        )
    }

    suspend fun fetchPackers(companyId: Int): List<WmsUser> {
        val raw = getText("/wms/users", mapOf("companyId" to companyId.toString(), "roleType" to "packer"))
        return decodeUsers(raw)
    }

    // ── Receiving ─────────────────────────────────────────────────────────────

    suspend fun fetchPackingReceiverOrders(companyId: Int): List<WmsPackingReceiverNode> {
        val raw = getText("/wms/packing/receiver/orders", mapOf("companyId" to companyId.toString()))
        return decodeReceiverOrders(raw)
    }

    /**
     * Android equivalent of iOS `WMSApi.fetchPickListHierarchyOrder(for:)`.
     * It fetches the receiver orders tree and returns a wrapper ORDER node
     * that contains ONLY the matching pick-list node.
     */
    suspend fun fetchPickListHierarchyOrder(
        pickList: WmsPackingPickListItem,
        companyId: Int,
    ): WmsPackingReceiverNode {
        require(companyId > 0) { "Company is not configured." }

        val targetPickListId = pickList.pickListId?.trim().orEmpty()
        val targetPickListNumber = pickList.pickListNumber?.trim().orEmpty()

        val orders = fetchPackingReceiverOrders(companyId)
        val matched = orders.firstNotNullOfOrNull { root ->
            findPickListNode(root, targetPickListId, targetPickListNumber)?.let { pickNode ->
                root to pickNode
            }
        }

        val (rootOrder, pickListNode) = matched
            ?: throw WmsException("Could not find pick-list hierarchy for ${pickList.id}")

        val orderStatusLabel = when {
            pickList.isPackingStatusPacked -> "PACKED"
            pickList.isPackingQtyComplete -> "PACKED"
            else -> pickList.displayPackingStatusLabel
        }

        return WmsPackingReceiverNode(
            nodeType = "ORDER",
            orderId = pickList.orderId,
            orderNumber = pickList.orderNumber,
            customerName = rootOrder.customerName,
            orderStatus = orderStatusLabel,
            children = listOf(pickListNode),
        )
    }

    private fun findPickListNode(
        node: WmsPackingReceiverNode,
        targetPickListId: String,
        targetPickListNumber: String,
    ): WmsPackingReceiverNode? {
        if (node.normalizedNodeType == "PICK_LIST") {
            val matchesId = targetPickListId.isNotBlank() && node.pickListId?.trim().orEmpty() == targetPickListId
            val matchesNumber = targetPickListNumber.isNotBlank() && node.pickListNumber?.trim().orEmpty() == targetPickListNumber
            if (matchesId || matchesNumber) return node
        }
        return node.children
            ?.asSequence()
            ?.mapNotNull { findPickListNode(it, targetPickListId, targetPickListNumber) }
            ?.firstOrNull()
    }

    suspend fun fetchPackingReceiverOrderLines(orderId: String, companyId: Int): List<WmsPackingReceiverLine> {
        val raw = getText(
            "/wms/packing/receiver/orders/$orderId/lines",
            mapOf("companyId" to companyId.toString()),
        )
        return decodeReceiverLines(raw)
    }

    suspend fun fetchPackingReceiverReceivingStatus(
        orderId: String,
        companyId: Int,
    ): WmsPackingReceiverReceivingStatus {
        val raw = getText(
            "/wms/packing/receiver/orders/$orderId/receiving-status",
            mapOf("companyId" to companyId.toString()),
        )
        return decodeReceiverReceivingStatus(raw)
    }

    suspend fun fetchPackingReceiverOrder(orderId: String, companyId: Int): WmsPackingReceiverNode? {
        val trimmed = orderId.trim()
        if (trimmed.isEmpty()) return null
        return fetchPackingReceiverOrders(companyId).firstOrNull { order ->
            order.orderId?.trim() == trimmed
                || order.orderNumber?.trim() == trimmed
                || order.resolvedAPIOrderId == trimmed
        }
    }

    suspend fun verifyPackingReceiverLines(
        orderId: String,
        request: WmsPackingReceiverVerifyLinesRequest,
    ) {
        patchRequest("/wms/packing/receiver/orders/$orderId/verify-lines", request)
    }

    suspend fun completePackingReceiverOrder(
        orderId: String,
        request: WmsPackingReceiverCompleteRequest,
    ) {
        postRequest("/wms/packing/receiver/orders/$orderId/complete", request)
    }
}

class WmsException(message: String) : Exception(message)
