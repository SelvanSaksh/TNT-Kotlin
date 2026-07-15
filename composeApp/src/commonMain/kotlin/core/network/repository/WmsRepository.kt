package core.network.repository

import core.network.wms.WmsAddIndustryPackingBoxItemRequest
import core.network.wms.WmsAssignPackerRequest
import core.network.wms.WmsDeliverDispatchRequest
import core.network.wms.WmsDeliverDispatchResponse
import core.network.wms.WmsDispatchFullRequest
import core.network.wms.WmsDispatchInvoiceResponse
import core.network.wms.WmsDispatchManualInvoiceRequest
import core.network.wms.WmsDispatchCompaniesPage
import core.network.wms.IndustryPickingProductsResponse
import core.network.wms.decodeDispatchCompaniesPage
import core.network.wms.WmsIndustryCompleteListRequest
import core.network.wms.WmsIndustryCreateBoxRequest
import core.network.wms.WmsIndustrySealBoxRequest
import core.network.wms.WmsIndustrySetBoxParentRequest
import core.network.wms.industryBoxIdsMatch
import core.network.wms.industryNumericBoxId
import core.network.wms.industryNumericBoxId
import core.network.wms.preservingPackId
import core.network.wms.withKnownPackId
import core.network.wms.WmsShippedPicklistsPage
import core.network.wms.WmsVerifyDispatchTaskRequest
import core.network.wms.WmsVerifyDispatchTaskResponse
import core.network.wms.decodeShippedPicklistsPage
import core.network.wms.toPackingPickListItem
import core.network.wms.toCreatedPackingBox
import core.network.wms.buildPickListHierarchyNode
import core.network.wms.wrapPickListHierarchyOrder
import core.network.wms.WmsAssignPickListRequest
import core.network.wms.decodeFefoPickListTasks
import core.network.wms.FefoProductGroup
import core.network.wms.FefoConfirmTaskRequest
import core.network.wms.PickExceptionRequest
import core.network.wms.WmsIndustryPickListActionResponse
import core.network.wms.StartPickListRequest
import core.network.wms.decodeIndustryPickListActionResponse
import core.network.wms.ToteAssignRequest
import core.network.wms.ToteCatalogItem
import core.network.wms.ToteCatalogResponse
import core.network.wms.WmsAddPackingBoxItemRequest
import core.network.wms.WmsCreatePackingBoxRequest
import core.network.wms.WmsCreatedPackingBox
import core.network.wms.mergeSessionState
import core.network.wms.WmsPackingPickListsByPackerPayload
import core.network.wms.WmsPackingReceiverCompleteRequest
import core.network.wms.WmsPackingReceiverScanPackRequest
import core.network.wms.WmsPackingReceiverScanPackResponse
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
import core.network.wms.WmsIndustryStagePickListRequest
import core.network.wms.WmsStagePickListRequest
import core.network.wms.decodeStagingLocations
import core.network.wms.stagingLocationCandidates
import core.network.wms.WmsWarehouseLocation
import core.network.wms.WmsUpdatePackingBoxStatusRequest
import core.network.wms.WmsUpdatePackingPickListStatusRequest
import core.network.wms.WmsUser
import core.network.wms.decodePackingBox
import core.network.wms.decodePackingBoxes
import core.network.wms.decodePackingByPackerPayload
import core.network.wms.decodePickListsPayload
import core.network.wms.isAssignedToPicker
import core.network.wms.decodeReceiverLines
import core.network.wms.decodeReceiverOrders
import core.network.wms.decodeReceiverReceivingStatus
import core.network.wms.decodeUsers
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
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
        android.util.Log.d("PICKLIST_DEBUG", "GET $path → status=${response.status.value}, bodyLength=${body.length}")
        if (body.length < 2000) {
            android.util.Log.d("PICKLIST_DEBUG", "Response body: $body")
        } else {
            android.util.Log.d("PICKLIST_DEBUG", "Response body (first 2000 chars): ${body.take(2000)}")
        }
        if (!response.status.isSuccess()) {
            throw WmsException(body.ifBlank { "Request failed (${response.status.value})" })
        }
        return body
    }

    private suspend inline fun <reified Res> get(path: String, query: Map<String, String> = emptyMap()): Res {
        val response = client.get(Config.BASE_URL + path) {
            query.forEach { (k, v) -> parameter(k, v) }
        }
        if (!response.status.isSuccess()) {
            throw WmsException(response.bodyAsText())
        }
        return response.body()
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

    private suspend fun patchWithoutBody(path: String) {
        val response = client.patch(Config.BASE_URL + path)
        if (!response.status.isSuccess()) {
            throw WmsException(response.bodyAsText())
        }
    }

    private suspend inline fun <reified Req> patchRequest(path: String, payload: Req) {
        println("=== WMS PATCH === path: $path")
        println("=== WMS PATCH === body: $payload")
        val response = client.patch(Config.BASE_URL + path) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        println("=== WMS PATCH === status: ${response.status}")
        if (!response.status.isSuccess()) {
            val body = response.bodyAsText()
            println("=== WMS PATCH ERROR === body: $body")
            throw WmsException(body)
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

    suspend fun fetchPickListTasks(pickListId: String, companyId: Int): List<FefoProductGroup> {
        require(pickListId.isNotBlank()) { "Pick list id is missing." }
        require(companyId > 0) { "Company is not configured." }
        val raw = getText("/wms/industry-picking/lists/$pickListId/tasks", mapOf("companyId" to companyId.toString()))
        return decodeFefoPickListTasks(raw)
    }

    suspend fun confirmPickTask(
        taskId: String,
        pickerId: Int,
        pickedQty: Int,
        toteId: Int,
        scannedGtin: String? = null,
        scannedBatch: String? = null,
        actualBin: String? = null,
    ) {
        require(taskId.isNotBlank()) { "Task id is missing." }
        require(toteId > 0) { "Active tote id is missing." }
        require(pickerId > 0) { "Picker id is missing." }
        patchRequest(
            "/wms/industry-picking/tasks/$taskId/confirm",
            FefoConfirmTaskRequest(
                pickerId = pickerId,
                pickedQty = pickedQty,
                toteId = toteId,
                scannedGtin = scannedGtin,
                scannedBatch = scannedBatch,
                actualBin = actualBin,
            ),
        )
    }

    suspend fun reportPickException(
        taskId: String,
        companyId: Int,
        pickerId: Int,
        exceptionTypes: List<String>,
        qtyFound: Int,
        notes: String? = null,
    ) {
        require(taskId.isNotBlank()) { "Task id is missing." }
        patchRequest(
            "/wms/industry-picking/tasks/$taskId/exception",
            PickExceptionRequest(
                companyId = companyId,
                pickerId = pickerId,
                exceptionTypes = exceptionTypes,
                qtyFound = qtyFound,
                notes = notes,
            ),
        )
    }

    suspend fun fillActiveTote(pickListId: String) {
        require(pickListId.isNotBlank()) { "Pick list id is missing." }
        patchWithoutBody("/wms/industry-picking/lists/$pickListId/totes/active/fill")
    }

    suspend fun fetchStagingLocations(companyId: Int): List<WmsWarehouseLocation> {
        require(companyId > 0) { "Company is not configured." }
        val raw = getText(
            "/wms/orders",
            mapOf(
                "companyId" to companyId.toString(),
                "status" to "PENDING",
            ),
        )
        return decodeStagingLocations(raw).stagingLocationCandidates()
    }

    suspend fun stageIndustryPickList(
        pickListId: String,
        companyId: Int,
        stagingLocationName: String,
    ) {
        require(pickListId.isNotBlank()) { "Pick list id is missing." }
        require(companyId > 0) { "Company is not configured." }
        val location = stagingLocationName.trim()
        require(location.isNotEmpty()) { "Staging location is required." }
        patchRequest(
            "/wms/industry-picking/lists/$pickListId/stage",
            WmsIndustryStagePickListRequest(
                companyId = companyId,
                stagingLocationName = location,
            ),
        )
    }

    suspend fun completeIndustryPickList(pickListId: String) {
        require(pickListId.isNotBlank()) { "Pick list id is missing." }
        patchWithoutBody("/wms/industry-picking/lists/$pickListId/complete")
    }

    suspend fun assignTote(
        pickListId: String,
        toteNumber: String,
        pickerId: Int,
        markPreviousFilled: Boolean = false,
    ): WmsIndustryPickListActionResponse {
        val response = client.patch(Config.BASE_URL + "/wms/industry-picking/lists/$pickListId/tote") {
            contentType(ContentType.Application.Json)
            setBody(
                ToteAssignRequest(
                    toteNumber = toteNumber.trim(),
                    pickerId = pickerId.takeIf { it > 0 },
                    markPreviousFilled = markPreviousFilled.takeIf { it },
                ),
            )
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw WmsException(body)
        }
        return decodeIndustryPickListActionResponse(body)
    }

    suspend fun startIndustryPickList(
        pickListId: String,
        toteNumber: String? = null,
    ): WmsIndustryPickListActionResponse {
        val response = client.patch(Config.BASE_URL + "/wms/industry-picking/lists/$pickListId/start") {
            contentType(ContentType.Application.Json)
            setBody(StartPickListRequest(toteNumber = toteNumber?.trim()?.takeIf { it.isNotEmpty() }))
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw WmsException(body)
        }
        return decodeIndustryPickListActionResponse(body)
    }

    suspend fun fetchToteCatalog(pickListId: String): List<ToteCatalogItem> {
        require(pickListId.isNotBlank()) { "Pick list id is missing." }
        val raw = getText("/wms/industry-picking/lists/$pickListId/totes", emptyMap())
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        return try {
            val response = json.decodeFromString<ToteCatalogResponse>(raw)
            response.totes
        } catch (_: Exception) {
            json.decodeFromString<List<ToteCatalogItem>>(raw)
        }
    }

    suspend fun assignPickList(
        pickListId: String,
        pickerId: Int,
    ) {
        patchRequest(
            "/wms/industry-picking/lists/$pickListId/assign",
            WmsAssignPickListRequest(pickerId),
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

    suspend fun fetchIndustryPickLists(companyId: Int, status: String? = null): WmsPickListsPayload {
        require(companyId > 0) { "Company is not configured." }
        val query = buildMap {
            put("companyId", companyId.toString())
            status?.takeIf { it.isNotBlank() }?.let { put("status", it.lowercase()) }
        }
        val raw = getText("/wms/industry-picking/lists", query)
        return decodePickListsPayload(raw)
    }

    /** Pick lists assigned to the logged-in picker (industry flow, iOS parity with packing by-packer). */
    suspend fun fetchIndustryPickListsByPicker(pickerId: Int, companyId: Int): WmsPickListsPayload {
        require(pickerId > 0) { "Picker id is missing." }
        require(companyId > 0) { "Company is not configured." }
        val payload = runCatching {
            val raw = getText(
                "/wms/industry-picking/lists",
                mapOf(
                    "companyId" to companyId.toString(),
                    "assignedPickerId" to pickerId.toString(),
                ),
            )
            decodePickListsPayload(raw)
        }.getOrElse {
            fetchPickListsByPicker(pickerId, companyId)
        }
        val assigned = payload.pickLists.filter { it.isAssignedToPicker(pickerId) }
        return payload.copy(pickLists = assigned, count = assigned.size)
    }

    /** Admin packing queue — completed industry pick lists ready for packer assignment. */
    suspend fun fetchIndustryPickListsForAdminPacking(companyId: Int): WmsPickListsPayload =
        fetchIndustryPickLists(companyId, status = "completed")

    suspend fun fetchAdminPackingPickLists(companyId: Int): WmsPickListsPayload {
        runCatching { return fetchIndustryPickListsForAdminPacking(companyId) }
        runCatching { return fetchPackingPickLists(companyId) }
        return fetchPickLists(companyId)
    }

    // ── Packing ───────────────────────────────────────────────────────────────

    suspend fun fetchPackingPickListsByPacker(packerId: Int, companyId: Int): WmsPackingPickListsByPackerPayload {
        runCatching { return fetchIndustryPackingListsByPacker(packerId, companyId) }
        val raw = getText(
            "/wms/packing/pick-lists/by-packer/$packerId",
            mapOf("companyId" to companyId.toString()),
        )
        return decodePackingByPackerPayload(raw)
    }

    private suspend fun fetchIndustryPackingListsByPacker(
        packerId: Int,
        companyId: Int,
    ): WmsPackingPickListsByPackerPayload {
        val raw = getText(
            "/wms/industry-packing/lists",
            mapOf(
                "companyId" to companyId.toString(),
                "assignedPackerId" to packerId.toString(),
            ),
        )
        val payload = decodePickListsPayload(raw)
        val lists = payload.pickLists.map { it.toPackingPickListItem() }
        return WmsPackingPickListsByPackerPayload(packerId, lists.size, lists)
    }

    suspend fun fetchPackingPickLists(companyId: Int): WmsPickListsPayload {
        val raw = getText("/wms/packing/pick-lists", mapOf("companyId" to companyId.toString()))
        return decodePickListsPayload(raw)
    }

    suspend fun assignPackerToPickList(pickListId: String, packerId: Int, companyId: Int) {
        val trimmedId = pickListId.trim()
        require(trimmedId.isNotEmpty()) { "Pick list id is missing." }
        require(packerId > 0) { "Select a packer to assign." }
        require(companyId > 0) { "Company is not configured." }
        val request = WmsAssignPackerRequest(packerId, companyId)
        runCatching {
            patchRequest("/wms/industry-packing/lists/$trimmedId/assign-packer", request)
        }.getOrElse {
            patchRequest("/wms/packing/pick-lists/$trimmedId/assign-packer", request)
        }
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

    private class IndustryPatchNotFound : Exception()

    private suspend inline fun <reified Req> patchIndustryBox(path: String, payload: Req): WmsCreatedPackingBox {
        val response = client.patch(Config.BASE_URL + path) {
            contentType(ContentType.Application.Json)
            setBody(payload)
        }
        if (response.status.value == 404 || response.status.value == 405) {
            throw IndustryPatchNotFound()
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

    /** Matches iOS post-add refresh — legacy box fetch, then industry list fallback. */
    suspend fun fetchPackingBoxWithFallback(
        packId: String,
        companyId: Int,
        pickListId: String?,
    ): WmsCreatedPackingBox {
        val trimmedPackId = packId.trim()
        require(trimmedPackId.isNotEmpty()) { "Box id is missing." }
        runCatching { return fetchPackingBox(trimmedPackId, companyId) }
        val trimmedPickListId = pickListId?.trim().orEmpty()
        if (trimmedPickListId.isNotEmpty()) {
            fetchIndustryPackingBoxes(trimmedPickListId)
                .firstOrNull { it.resolvedPackId == trimmedPackId }
                ?.toCreatedPackingBox()
                ?.let { return it }
        }
        throw WmsException("Unable to refresh box details.")
    }

    suspend fun fetchPackers(companyId: Int): List<WmsUser> =
        fetchPackersForAssignment(companyId)

    suspend fun fetchPackersForAssignment(companyId: Int): List<WmsUser> {
        runCatching {
            val raw = getText("/wms/industry-packing/packers", mapOf("companyId" to companyId.toString()))
            return decodeUsers(raw)
        }
        val raw = getText("/wms/users", mapOf("companyId" to companyId.toString(), "roleType" to "packer"))
        return decodeUsers(raw)
    }

    // ── Industry packing boxes ──────────────────────────────────────────────────

    suspend fun createIndustryPackingBox(
        pickListId: String,
        packerId: Int,
        boxType: String,
        parentBoxId: String? = null,
        boxId: String? = null,
    ): WmsCreatedPackingBox {
        val trimmedId = pickListId.trim()
        require(trimmedId.isNotEmpty()) { "Pick list id is missing." }
        require(packerId > 0) { "Packer id is missing." }
        return postPackingBox(
            "/wms/industry-packing/lists/$trimmedId/boxes",
            WmsIndustryCreateBoxRequest(
                packerId = packerId,
                boxType = boxType.lowercase(),
                parentBoxId = industryNumericBoxId(parentBoxId),
                boxId = industryNumericBoxId(boxId),
            ),
        )
    }

    suspend fun createIndustryPallet(pickListId: String, packerId: Int): WmsCreatedPackingBox =
        createIndustryPackingBox(pickListId, packerId, boxType = "pallet")

    suspend fun createCartonOnIndustryPallet(
        pickListId: String,
        palletId: String,
        packerId: Int,
    ): WmsCreatedPackingBox {
        val trimmedPalletId = palletId.trim()
        require(trimmedPalletId.isNotEmpty()) { "Pallet id is missing." }
        require(industryNumericBoxId(trimmedPalletId) != null) {
            "Pallet id must be a numeric industry box id."
        }
        return createIndustryPackingBox(
            pickListId = pickListId,
            packerId = packerId,
            boxType = "carton",
            parentBoxId = trimmedPalletId,
        )
    }

    suspend fun linkSealedCartonToIndustryPallet(
        pickListId: String,
        palletId: String,
        sealedCartonId: String,
        packerId: Int,
    ): WmsCreatedPackingBox {
        val trimmedCartonId = sealedCartonId.trim()
        val trimmedPalletId = palletId.trim()
        require(trimmedCartonId.isNotEmpty()) { "Sealed carton id is missing." }
        require(trimmedPalletId.isNotEmpty()) { "Pallet id is missing." }
        require(packerId > 0) { "Packer id is missing." }
        val numericCartonId = industryNumericBoxId(trimmedCartonId)
            ?: throw WmsException("Sealed carton id must be a numeric industry box id.")
        val numericPalletId = industryNumericBoxId(trimmedPalletId)
            ?: throw WmsException("Pallet id must be a numeric industry box id.")
        val request = WmsIndustrySetBoxParentRequest(packerId, numericPalletId)
        // attach-to-pallet first — generic PATCH on /boxes/{id} can create empty child rows on some backends.
        val patchPaths = listOf(
            "/wms/industry-packing/boxes/$numericCartonId/attach-to-pallet",
            "/wms/industry-packing/boxes/$numericCartonId",
        )
        var lastError: Throwable? = null
        for (path in patchPaths) {
            try {
                val decoded = patchIndustryBox(path, request).preservingPackId(trimmedCartonId)
                val returned = decoded.resolvedPackId.trim()
                if (
                    returned.isNotEmpty() &&
                    !industryBoxIdsMatch(returned, trimmedCartonId) &&
                    !industryBoxIdsMatch(returned, trimmedPalletId)
                ) {
                    throw WmsException(
                        "Server returned box $returned instead of linking sealed carton $trimmedCartonId. " +
                            "PATCH must update parent_box_id on carton $trimmedCartonId, not create a new row.",
                    )
                }
                if (industryBoxIdsMatch(returned, trimmedPalletId)) {
                    val linkedCarton = fetchIndustryPackingBox(pickListId, trimmedCartonId)
                    if (linkedCarton != null &&
                        !industryBoxIdsMatch(linkedCarton.resolvedParentPackId, trimmedPalletId)
                    ) {
                        throw WmsException(
                            "Carton $trimmedCartonId was not linked to pallet $trimmedPalletId. Server returned pallet only.",
                        )
                    }
                }
                return decoded
            } catch (_: IndustryPatchNotFound) {
                continue
            } catch (e: WmsException) {
                lastError = e
            } catch (e: Throwable) {
                lastError = e
            }
        }
        throw lastError ?: WmsException(
            "Could not link sealed carton $trimmedCartonId to pallet $trimmedPalletId. " +
                "Use PATCH /boxes/$trimmedCartonId with parentBoxId.",
        )
    }

    suspend fun addCartonToIndustryPallet(
        pickListId: String,
        packerId: Int,
        palletId: String,
        existingCartonId: String? = null,
    ): WmsCreatedPackingBox {
        val trimmedCartonId = existingCartonId?.trim().orEmpty()
        require(trimmedCartonId.isNotEmpty()) {
            "Sealed carton id is required. Use linkSealedCartonToIndustryPallet — never POST a new empty carton when linking."
        }
        return linkSealedCartonToIndustryPallet(pickListId, palletId, trimmedCartonId, packerId)
    }

    suspend fun fetchIndustryPackingBox(pickListId: String, boxId: String): WmsPackingBoxSummary? {
        val trimmedBoxId = boxId.trim()
        if (trimmedBoxId.isEmpty()) return null
        return fetchIndustryPackingBoxes(pickListId)
            .firstOrNull { industryBoxIdsMatch(it.resolvedPackId, trimmedBoxId) }
    }

    suspend fun sealIndustryPackingBox(
        boxId: String,
        packerId: Int,
        weightKg: Double = 0.0,
    ): WmsCreatedPackingBox {
        val trimmedId = boxId.trim()
        require(trimmedId.isNotEmpty()) { "Box id is missing." }
        val pathId = industryNumericBoxId(trimmedId)?.toString() ?: trimmedId
        return patchPackingBox(
            "/wms/industry-packing/boxes/$pathId/seal",
            WmsIndustrySealBoxRequest(packerId, weightKg),
        )
            .preservingPackId(trimmedId)
            .withKnownPackId(trimmedId)
    }

    suspend fun completeIndustryPackingList(pickListId: String, packerId: Int) {
        val trimmedId = pickListId.trim()
        require(trimmedId.isNotEmpty()) { "Pick list id is missing." }
        patchRequest(
            "/wms/industry-packing/lists/$trimmedId/complete",
            WmsIndustryCompleteListRequest(packerId),
        )
    }

    suspend fun addItemToIndustryPackingBox(
        boxId: String,
        taskId: String,
        quantity: Int,
        packerId: Int,
        verificationType: String? = null,
    ): WmsCreatedPackingBox {
        val trimmedBoxId = boxId.trim()
        val trimmedTaskId = taskId.trim()
        require(trimmedBoxId.isNotEmpty()) { "Box id is missing." }
        require(trimmedTaskId.isNotEmpty()) { "Task id is missing." }
        require(quantity > 0) { "Quantity must be greater than zero." }
        return patchPackingBox(
            "/wms/industry-packing/boxes/$trimmedBoxId/items/$trimmedTaskId",
            WmsAddIndustryPackingBoxItemRequest(quantity, packerId, verificationType),
        )
    }

    suspend fun fetchIndustryPackingBoxes(pickListId: String): List<WmsPackingBoxSummary> {
        val trimmedId = pickListId.trim()
        require(trimmedId.isNotEmpty()) { "Pick list id is missing." }
        return decodePackingBoxes(getText("/wms/industry-packing/lists/$trimmedId/boxes"))
    }

    suspend fun createPackingBoxWithFallback(
        pickListId: String?,
        packerId: Int,
        packTypeLabel: String,
        legacyRequest: WmsCreatePackingBoxRequest,
    ): WmsCreatedPackingBox {
        val trimmedPickListId = pickListId?.trim().orEmpty()
        if (trimmedPickListId.isNotEmpty()) {
            runCatching {
                val apiBoxType = when (packTypeLabel.trim().uppercase()) {
                    "TERTIARY", "PALLET", "MASTER" -> "pallet"
                    else -> "carton"
                }
                var box = createIndustryPackingBox(trimmedPickListId, packerId, apiBoxType)
                if (box.resolvedPackId.isBlank()) {
                    val recovered = fetchIndustryPackingBoxes(trimmedPickListId)
                        .filter { !it.isCompleted && it.resolvedParentPackId.isNullOrBlank() }
                        .lastOrNull()
                        ?.toCreatedPackingBox()
                    if (recovered != null && recovered.resolvedPackId.isNotBlank()) {
                        box = box.mergeSessionState(recovered)
                    }
                }
                return box
            }
        }
        return createPackingBox(legacyRequest)
    }

    suspend fun fetchIncompleteIndustryPackingBoxes(pickListId: String): List<WmsPackingBoxSummary> =
        fetchIndustryPackingBoxes(pickListId)
            .filter { !it.isCompleted }
            .distinctBy { box ->
                box.resolvedPackId.takeIf { it.isNotBlank() }
                    ?: listOfNotNull(box.sscc, box.packLabel).joinToString("|")
            }

    suspend fun fetchPickers(companyId: Int): List<WmsUser> {
        val raw = getText("/wms/users", mapOf("companyId" to companyId.toString(), "roleType" to "picker"))
        return decodeUsers(raw)
    }

    // ── Receiving ─────────────────────────────────────────────────────────────

    suspend fun fetchPackingReceiverOrders(companyId: Int): List<WmsPackingReceiverNode> {
        val raw = getText("/wms/packing/receiver/orders", mapOf("companyId" to companyId.toString()))
        return decodeReceiverOrders(raw)
    }

    /**
     * Hierarchy tree for a packed pick list — matches iOS `fetchPickListHierarchyOrder`.
     * Prefers receiver orders API, then industry packing boxes, then legacy boxes fallback.
     */
    suspend fun fetchPickListHierarchyOrder(
        pickList: WmsPackingPickListItem,
        companyId: Int,
    ): WmsPackingReceiverNode {
        require(companyId > 0) { "Company is not configured." }

        findPickListInReceiverOrders(pickList, companyId)?.let { pickListNode ->
            return pickList.wrapPickListHierarchyOrder(pickListNode)
        }

        var boxes: List<WmsPackingBoxSummary> = emptyList()
        val pickListId = pickList.pickListId?.trim().orEmpty()
        if (pickListId.isNotEmpty()) {
            boxes = runCatching { fetchIndustryPackingBoxes(pickListId) }.getOrDefault(emptyList())
        }
        if (boxes.isEmpty()) {
            boxes = pickList.boxes.orEmpty()
        }
        if (boxes.isEmpty()) {
            boxes = fetchPackingBoxes(
                companyId = companyId,
                packingOrderId = pickList.packingOrderId,
                pickListId = pickList.pickListId,
                packType = null,
                status = null,
                topLevelOnly = false,
            )
        }

        val pickListNode = pickList.buildPickListHierarchyNode(boxes)
        return pickList.wrapPickListHierarchyOrder(pickListNode)
    }

    private suspend fun findPickListInReceiverOrders(
        list: WmsPackingPickListItem,
        companyId: Int,
    ): WmsPackingReceiverNode? {
        val orders = fetchPackingReceiverOrders(companyId)
        val targetPickListId = list.pickListId?.trim().orEmpty()
        val targetPickListNumber = list.pickListNumber?.trim().orEmpty()
        val targetOrderId = list.orderId?.trim().orEmpty()
        val targetOrderNumber = list.orderNumber?.trim().orEmpty()

        for (order in orders) {
            if (targetOrderId.isNotEmpty()) {
                val orderId = order.orderId?.trim().orEmpty()
                if (orderId.isNotEmpty() && orderId != targetOrderId) continue
            }
            if (targetOrderNumber.isNotEmpty()) {
                val orderNumber = order.orderNumber?.trim().orEmpty()
                if (orderNumber.isNotEmpty() && orderNumber != targetOrderNumber) continue
            }

            order.pickListNodes.firstOrNull { pickList ->
                (targetPickListId.isNotEmpty() && pickList.pickListId?.trim() == targetPickListId) ||
                    (targetPickListNumber.isNotEmpty() && pickList.pickListNumber?.trim() == targetPickListNumber)
            }?.let { return it }

            findPickListNode(order, targetPickListId, targetPickListNumber)?.let { return it }
        }
        return null
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

    suspend fun scanPackingReceiverPack(
        orderId: String,
        request: WmsPackingReceiverScanPackRequest,
    ): WmsPackingReceiverScanPackResponse =
        post("/wms/packing/receiver/orders/$orderId/scan-pack", request)

    suspend fun completePackingReceiverOrder(
        orderId: String,
        request: WmsPackingReceiverCompleteRequest,
    ) {
        postRequest("/wms/packing/receiver/orders/$orderId/complete", request)
    }

    // ── Dispatch / shipped receiving ──────────────────────────────────────────

    suspend fun fetchShippedPicklists(
        receivingCompanyId: Int,
        dispatchStatus: String? = null,
        page: Int = 1,
        limit: Int = 50,
    ): WmsShippedPicklistsPage {
        require(receivingCompanyId > 0) { "Company is not configured." }
        val query = buildMap {
            put("receivingCompanyId", receivingCompanyId.toString())
            put("page", page.toString())
            put("limit", limit.toString())
            dispatchStatus?.takeIf { it.isNotBlank() }?.let { put("dispatchStatus", it) }
        }
        val raw = getText("/wms/dispatch/invoices/shipped", query)
        return decodeShippedPicklistsPage(raw, page)
    }

    suspend fun verifyDispatchTask(request: WmsVerifyDispatchTaskRequest): WmsVerifyDispatchTaskResponse =
        post("/wms/dispatch/invoices/verify", request)

    suspend fun markDispatchDelivered(request: WmsDeliverDispatchRequest): WmsDeliverDispatchResponse =
        post("/wms/dispatch/invoices/deliver", request)

    suspend fun createManualDispatchInvoice(request: WmsDispatchManualInvoiceRequest): WmsDispatchInvoiceResponse =
        post("/wms/dispatch/invoices/manual", request)

    suspend fun uploadDispatchInvoice(
        pickingListId: Int,
        companyId: Int,
        invoiceNumber: String,
        invoiceDate: String,
        dispatchedBy: Int?,
        fileBytes: ByteArray,
        fileName: String,
    ): WmsDispatchInvoiceResponse {
        val mimeType = dispatchInvoiceMimeType(fileName)
        val response = client.post(Config.BASE_URL + "/wms/dispatch/invoices/upload") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("pickingListId", pickingListId.toString())
                        append("companyId", companyId.toString())
                        append("invoiceNumber", invoiceNumber)
                        append("invoiceDate", invoiceDate)
                        dispatchedBy?.takeIf { it > 0 }?.let { append("dispatchedBy", it.toString()) }
                        append(
                            key = "file",
                            value = fileBytes,
                            headers = Headers.build {
                                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                                append(HttpHeaders.ContentType, mimeType)
                            },
                        )
                    },
                ),
            )
        }
        if (!response.status.isSuccess()) {
            throw WmsException(response.bodyAsText())
        }
        return response.body()
    }

    suspend fun dispatchWithInvoice(request: WmsDispatchFullRequest): WmsDispatchInvoiceResponse =
        post("/wms/dispatch/invoices/dispatch", request)

    suspend fun fetchDispatchCompanies(
        page: Int = 1,
        limit: Int = 50,
        search: String = "",
    ): WmsDispatchCompaniesPage {
        val raw = getText(
            "/companies",
            mapOf(
                "page" to page.toString(),
                "limit" to limit.toString(),
                "search" to search,
                "sort" to "ASC",
                "sortBy" to "createdAt",
            ),
        )
        return decodeDispatchCompaniesPage(raw, page)
    }

    suspend fun fetchIndustryPickingProducts(pickListId: String): IndustryPickingProductsResponse {
        val trimmedId = pickListId.trim()
        require(trimmedId.isNotEmpty()) { "Pick list id is missing." }
        return get("/wms/industry-picking/lists/$trimmedId/products")
    }

    suspend fun recordPickLine(pickListId: String, pickLineId: String, quantity: Int, pickedBy: Int) {
        require(pickListId.isNotBlank()) { "Pick list id is missing." }
        require(pickLineId.isNotBlank()) { "Pick line id is missing." }
        client.post(Config.BASE_URL + "/wms/picking/lists/$pickListId/lines/$pickLineId/pick") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("quantity" to quantity, "pickedBy" to pickedBy))
        }
    }
}

private fun dispatchInvoiceMimeType(fileName: String): String {
    val lower = fileName.lowercase()
    return when {
        lower.endsWith(".pdf") -> "application/pdf"
        lower.endsWith(".csv") -> "text/csv"
        lower.endsWith(".xlsx") || lower.endsWith(".xls") ->
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        lower.endsWith(".docx") || lower.endsWith(".doc") ->
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        else -> "application/octet-stream"
    }
}

class WmsException(message: String) : Exception(message)
