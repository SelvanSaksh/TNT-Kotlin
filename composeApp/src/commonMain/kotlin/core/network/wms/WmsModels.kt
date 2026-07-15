package core.network.wms

import network.Config
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.math.roundToInt

private val wmsJson = Json { ignoreUnknownKeys = true; explicitNulls = false; isLenient = true }

// ── Pick List Display Status (iOS PickListDisplayStatus) ──────────────────────

enum class PickListDisplayStatus {
    Created,
    Assigned,
    InProgress,
    Picked,
    Staged,
    Completed,
    Cancelled,
    Unknown;

    companion object {
        fun from(status: String?, packingStatus: String?): PickListDisplayStatus {
            val s = status?.trim()?.uppercase() ?: return Unknown
            return when (s) {
                "CREATED" -> Created
                "ASSIGNED" -> Assigned
                "IN_PROGRESS" -> InProgress
                "PICKED" -> Picked
                "STAGED" -> Staged
                "COMPLETED" -> Completed
                "CANCELLED" -> Cancelled
                else -> when (packingStatus?.trim()?.lowercase()) {
                    "packing" -> InProgress
                    "packed" -> Picked
                    "shipped" -> Completed
                    else -> Unknown
                }
            }
        }
    }
}

enum class PickListStatusFilter(val label: String, val apiStatuses: List<String>) {
    ALL("ALL", emptyList()),
    PENDING("Pending", listOf("CREATED")),
    ASSIGNED("Assigned", listOf("ASSIGNED")),
    IN_PROGRESS("In Progress", listOf("IN_PROGRESS")),
    COMPLETED("Completed", listOf("COMPLETED", "CANCELLED", "PICKED", "STAGED"));

    companion object {
        fun fromDisplayStatus(status: PickListDisplayStatus): PickListStatusFilter = when (status) {
            PickListDisplayStatus.Created -> PENDING
            PickListDisplayStatus.Assigned -> ASSIGNED
            PickListDisplayStatus.InProgress -> IN_PROGRESS
            PickListDisplayStatus.Picked,
            PickListDisplayStatus.Staged,
            PickListDisplayStatus.Completed,
            PickListDisplayStatus.Cancelled -> COMPLETED
            PickListDisplayStatus.Unknown -> ALL
        }
    }
}

// ── Picking ───────────────────────────────────────────────────────────────────

@Serializable
data class WmsPickListStatusCounts(
    val assigned: Int? = null,
    @SerialName("in_progress") val inProgress: Int? = null,
    val picked: Int? = null,
    val staged: Int? = null,
    val completed: Int? = null,
)

@Serializable
data class WmsProductBatchRow(
    val gtin: String? = null,
    val batchNumber: String? = null,
    @SerialName("batch_number") val batchNumberSnake: String? = null,
) {
    val resolvedBatchNumber: String
        get() = batchNumber?.takeIf { it.isNotBlank() } ?: batchNumberSnake.orEmpty()
}

@Serializable
data class WmsOrderProduct(
    val productName: String? = null,
    @SerialName("product_name") val productNameSnake: String? = null,
    val productShortName: String? = null,
    @SerialName("product_short_name") val productShortNameSnake: String? = null,
    val productSku: String? = null,
    @SerialName("product_sku") val productSkuSnake: String? = null,
    val sku: String? = null,
    val gtin: String? = null,
    val identifier: String? = null,
    val batchNumber: String? = null,
    @SerialName("batch_number") val batchNumberSnake: String? = null,
    val batches: List<WmsProductBatchRow>? = null,
) {
    val resolvedName: String
        get() = listOfNotNull(productName, productNameSnake, productShortName, productShortNameSnake)
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

    val scanGtin: String
        get() = listOfNotNull(gtin, identifier, batches?.firstNotNullOfOrNull { it.gtin?.takeIf(String::isNotBlank) })
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

    val scanBatchNumber: String
        get() = listOfNotNull(
            batchNumber,
            batchNumberSnake,
            batches?.firstNotNullOfOrNull { it.resolvedBatchNumber.takeIf(String::isNotBlank) },
        ).firstOrNull { it.isNotBlank() }.orEmpty()

    val resolvedSku: String
        get() = productSku?.takeIf { it.isNotBlank() }
            ?: productSkuSnake?.takeIf { it.isNotBlank() }
            ?: sku.orEmpty()
}

@Serializable
data class WmsPickListLine(
    val pickLineId: String? = null,
    val taskId: String? = null,
    val id: String? = null,
    val orderNumber: String? = null,
    val lineNumber: Int? = null,
    val productId: String? = null,
    val productName: String? = null,
    val productSku: String? = null,
    val requestedQty: String? = null,
    val pickedQty: String? = null,
    val quantity: Int? = null,
    val packedQty: Int? = null,
    val remainingQty: Int? = null,
    val status: String? = null,
    val locationCode: String? = null,
    val locationName: String? = null,
    val zone: String? = null,
    val bin: String? = null,
    val batch: String? = null,
    val batchNumber: String? = null,
    val pickInstruction: String? = null,
    val sourceLocationId: String? = null,
    val expiryDate: String? = null,
    val exceptionTypes: List<String> = emptyList(),
    val exceptionNotes: String? = null,
    val product: WmsOrderProduct? = null,
    @SerialName("gtin") val gtinFromTask: String? = null,
) {
    val hasException: Boolean get() = exceptionTypes.isNotEmpty()
    val resolvedId: String
        get() = pickLineId?.takeIf { it.isNotBlank() } ?: id.orEmpty()

    /** Industry packing uses task id for add-to-box PATCH. */
    val apiTaskOrLineId: String
        get() = taskId?.trim()?.takeIf { it.isNotBlank() }
            ?: pickLineId?.trim()?.takeIf { it.isNotBlank() }
            ?: id.orEmpty()

    fun resolvedRequestedQty(): Int {
        quantity?.takeIf { it > 0 }?.let { return it }
        parseQty(requestedQty)?.takeIf { it > 0 }?.let { return it }
        remainingQty?.takeIf { it > 0 }?.let { return it }
        return 0
    }

    fun resolvedPickedQty(): Int = parseQty(pickedQty) ?: 0

    fun resolvedPackedQty(): Int = packedQty ?: 0
    fun resolvedRemainingQty(): Int =
        remainingQty ?: maxOf(0, resolvedRequestedQty() - resolvedPickedQty())

    val displayName: String
        get() = listOfNotNull(
            product?.resolvedName?.takeIf { it.isNotBlank() },
            productName,
            product?.resolvedSku?.takeIf { it.isNotBlank() },
            productSku,
        ).firstOrNull { it.isNotBlank() } ?: "Line item"

    val locationLabel: String
        get() = displayLocation

    val displayLocation: String
        get() {
            locationCode?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            val parts = listOfNotNull(zone, bin).filter { it.isNotBlank() }
            if (parts.isNotEmpty()) return parts.joinToString("-")
            lineNumber?.let { return "LINE-$it" }
            return "—"
        }

    val displaySku: String
        get() = (product?.resolvedSku ?: productSku)?.trim()?.takeIf { it.isNotEmpty() } ?: "—"

    val displayBatch: String
        get() = batch?.trim()?.takeIf { it.isNotBlank() }
            ?: batchNumber?.trim()?.takeIf { it.isNotBlank() }
            ?: product?.scanBatchNumber?.trim()?.takeIf { it.isNotBlank() }
            ?: "—"

    val displayQty: String
        get() {
            val requested = resolvedRequestedQty()
            val picked = resolvedPickedQty()
            return if (picked > 0) "$picked/$requested" else "$requested"
        }

    val scanGtin: String
        get() = listOfNotNull(
            gtinFromTask?.trim()?.takeIf { it.isNotEmpty() },
            product?.scanGtin?.trim()?.takeIf { it.isNotEmpty() },
        ).firstOrNull().orEmpty()

    val scanBatchNumber: String
        get() = product?.scanBatchNumber.orEmpty()
}

data class WmsPickListLinesPayload(
    val pickListId: String? = null,
    val pickListNumber: String? = null,
    val pickType: String? = null,
    val status: String? = null,
    val zone: String? = null,
    val toteNumber: String? = null,
    val assignedPicker: Int? = null,
    val orderId: String? = null,
    val stagingLocationId: String? = null,
    val defaultSourceLocationId: String? = null,
    val lineCount: Int? = null,
    val totalRequestedQty: Int? = null,
    val totalPickedQty: Int? = null,
    val pctComplete: Int? = null,
    val pickLines: List<WmsPickListLine>,
) {
    val displayTitle: String
        get() = pickListNumber?.takeIf { it.isNotBlank() } ?: pickListId.orEmpty()
}

@Serializable
data class WmsPickListSummary(
    val skuCount: Int? = null,
    val batchCount: Int? = null,
    val pickedSkuCount: Int? = null,
    val progress: String? = null,
    val totalTasks: Int? = null,
    val pending: Int? = null,
    val picked: Int? = null,
    val totalPackedQty: Int? = null,
    val remainingToPack: Int? = null,
    val boxCount: Int? = null,
    val sealedBoxCount: Int? = null,
)

@Serializable
data class WmsPackerInfo(
    val firstName: String? = null,
    val lastName: String? = null,
    val email: String? = null,
) {
    val displayName: String
        get() = listOfNotNull(firstName, lastName).joinToString(" ").takeIf { it.isNotBlank() } ?: email.orEmpty()
}

@Serializable
data class WmsPickListItem(
    @SerialName("id")
    val numericId: Int? = null,
    val pickListId: String? = null,
    val pickListNumber: String? = null,
    val pickListCode: String? = null,
    val orderId: String? = null,
    val orderNumber: String? = null,
    val pickType: String? = null,
    val status: String? = null,
    val zone: String? = null,
    val zoneCode: String? = null,
    val assignedPicker: Int? = null,
    val assignedPickerId: Int? = null,
    val priority: String? = null,
    val lineCount: Int? = null,
    val totalRequestedQty: Int? = null,
    val totalPickedQty: Int? = null,
    val waveId: String? = null,
    val waveNumber: String? = null,
    val assignedPickerName: String? = null,
    val toteNumber: String? = null,
    val toteCount: Int? = null,
    val activeToteNumber: String? = null,
    val stagingLocationId: String? = null,
    val stagingLocationName: String? = null,
    val stagedAt: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    @SerialName("completed_at") val completedAtSnake: String? = null,
    val assignedPacker: Int? = null,
    val assignedPackerId: Int? = null,
    val assignedPackerName: String? = null,
    val packingOrderId: String? = null,
    val packingNumber: String? = null,
    val packingStatus: String? = null,
    val displayPackingStatus: String? = null,
    val packingStartedAt: String? = null,
    val packedAt: String? = null,
    val dispatchStatus: String? = null,
    val dispatchedAt: String? = null,
    val carrier: String? = null,
    val trackingNumber: String? = null,
    val receivingCompanyId: Int? = null,
    val revingCompanyId: Int? = null,
    val receivingCompanyName: String? = null,
    val invoiceFileUrl: String? = null,
    val invoiceNumber: String? = null,
    val invoiceDate: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val summary: WmsPickListSummary? = null,
    val packer: WmsPackerInfo? = null,
    val boxCount: Int? = null,
    val completedBoxCount: Int? = null,
    val openBoxCount: Int? = null,
    val canComplete: Boolean? = null,
    val lines: List<WmsPickListLine>? = null,
    val pickLines: List<WmsPickListLine>? = null,
    val tasks: List<WmsPickListLine>? = null,
    val boxes: List<WmsPackingBoxSummary>? = null,
) {
    val id: String
        get() = pickListId?.takeIf { it.isNotBlank() }
            ?: pickListCode?.takeIf { it.isNotBlank() }
            ?: pickListNumber?.takeIf { it.isNotBlank() }
            ?: orderId.orEmpty()

    /** Numeric id for industry API path params — matches iOS `resolvedAPIListId`. */
    val resolvedAPIListId: String
        get() {
            numericId?.takeIf { it > 0 }?.toString()?.let { return it }
            for (candidate in listOf(pickListId, pickListNumber)) {
                val raw = candidate?.trim().orEmpty()
                if (raw.isNotEmpty() && raw.all { it.isDigit() }) return raw
            }
            pickListId?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
            val number = pickListNumber?.trim().orEmpty()
            if (number.isNotEmpty()) return number
            return id
        }

    val resolvedLines: List<WmsPickListLine>
        get() = lines?.takeIf { it.isNotEmpty() }
            ?: pickLines?.takeIf { it.isNotEmpty() }
            ?: tasks?.takeIf { it.isNotEmpty() }
            ?: emptyList()

    val displayTitle: String
        get() = pickListCode?.takeIf { it.isNotBlank() }
            ?: pickListNumber?.takeIf { it.isNotBlank() }
            ?: orderNumber?.takeIf { it.isNotBlank() }
            ?: "Pick list"

    val waveDisplayLabel: String?
        get() = waveNumber?.takeIf { it.isNotBlank() }?.let { "Wave $it" }

    val resolvedZoneCode: String?
        get() = zoneCode?.takeIf { it.isNotBlank() } ?: zone?.takeIf { it.isNotBlank() }

    val resolvedAssignedPickerId: Int?
        get() = assignedPickerId?.takeIf { it > 0 } ?: assignedPicker?.takeIf { it > 0 }
}

fun WmsPickListItem.isAssignedToPicker(pickerId: Int): Boolean {
    if (pickerId <= 0) return false
    return resolvedAssignedPickerId == pickerId
}

data class WmsPickListsPayload(
    val pickLists: List<WmsPickListItem>,
    val count: Int,
    val statusCounts: WmsPickListStatusCounts? = null,
)

data class PickerMyListTask(
    val id: String,
    val pickListId: String,
    val title: String,
    val lineCount: Int,
    val itemCount: Int,
    val pickedCount: Int,
    val status: String,
    val pickType: String? = null,
    val waveLabel: String? = null,
    val zone: String? = null,
    val toteNumber: String? = null,
    val locationLine: String,
    val isPriority: Boolean = false,
    val isLocked: Boolean = false,
    val lockReason: String? = null,
    val priority: Int = 5,
    val packingStatus: String? = null,
    val pickListCode: String? = null,
    val stagingLocationName: String? = null,
    val skuCount: Int = 0,
    val batchCount: Int = 0,
    val pickedSkuCount: Int = 0,
    val dispatchStatus: String? = null,
    val assignedPickerName: String? = null,
    val resolvedLines: List<WmsPickListLine> = emptyList(),
    val completedAt: String? = null,
    val stagedAt: String? = null,
    val updatedAt: String? = null,
) {
    val progress: Float
        get() = if (itemCount <= 0) 0f else (pickedCount.toFloat() / itemCount).coerceIn(0f, 1f)

    val isPickedStatus: Boolean
        get() = status == "PICKED" || status == "STAGED"

    val displayStatus: PickListDisplayStatus
        get() = PickListDisplayStatus.from(status, packingStatus)

    val statusColor: String
        get() = when (status.uppercase()) {
            "CREATED" -> "created"
            "IN_PROGRESS" -> "in_progress"
            "COMPLETED" -> "completed"
            else -> when (packingStatus?.lowercase()) {
                "packing" -> "packing"
                "packed" -> "packed"
                "shipped" -> "shipped"
                else -> status.lowercase()
            }
        }

    val subtitle: String
        get() = buildList {
            zone?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (skuCount > 0) add("$skuCount SKUs")
            if (batchCount > 0) add("$batchCount batches")
        }.joinToString(" • ")

    val assigneeLabel: String?
        get() = assignedPickerName?.takeIf { it.isNotBlank() }

    val progressLabel: String
        get() = if (itemCount > 0) "$pickedCount/$itemCount units" else ""

    val skuProgressLabel: String
        get() = if (skuCount > 0) "$pickedSkuCount/$skuCount SKUs" else ""

    val linePreview: List<WmsPickListLine>
        get() = resolvedLines.take(3)

    val hasAssignedTote: Boolean
        get() = toteNumber?.trim()?.isNotEmpty() == true

    val isCompletedStatus: Boolean
        get() = status.equals("COMPLETED", ignoreCase = true)

    val resolvedCompletedTimestamp: String?
        get() = sequenceOf(completedAt, stagedAt, updatedAt)
            .mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .firstOrNull()
}

enum class PickerPickFilterCategory(val label: String) {
    Pending("Pending"),
    InProgress("In Progress"),
    Picked("Picked"),
    Locked("Locked"),
    ;

    val filterColorHex: Long
        get() = when (this) {
            Pending -> 0xFF6B7280
            InProgress -> 0xFF2563EB
            Picked -> 0xFF059669
            Locked -> 0xFF9CA3AF
        }
}

fun PickerMyListTask.pickerCardCategory(): PickerPickFilterCategory = when {
    isLocked -> PickerPickFilterCategory.Locked
    isPickedStatus -> PickerPickFilterCategory.Picked
    hasAssignedTote -> PickerPickFilterCategory.InProgress
    else -> PickerPickFilterCategory.Pending
}

val PickerMyListTask.pickerStatusLabel: String
    get() = when (pickerCardCategory()) {
        PickerPickFilterCategory.Locked -> "LOCKED"
        PickerPickFilterCategory.Picked -> "PICKED"
        PickerPickFilterCategory.InProgress -> "IN PROGRESS"
        PickerPickFilterCategory.Pending -> "ASSIGNED"
    }

// ── Packing ───────────────────────────────────────────────────────────────────

@Serializable
data class WmsPackingPickListLine(
    val pickLineId: String? = null,
    val taskId: String? = null,
    val productId: Int? = null,
    val productName: String? = null,
    val productSku: String? = null,
    val gtin: String? = null,
    val batchNumber: String? = null,
    val expiryDate: String? = null,
    val requestedQty: Int? = null,
    val pickedQty: Int? = null,
    val packedQty: Int? = null,
    val remainingToPackQty: Int? = null,
    val packingStatus: String? = null,
    val pickLineStatus: String? = null,
    val status: String? = null,
    val exceptionTypes: List<String> = emptyList(),
    val exceptionNotes: String? = null,
) {
    val hasException: Boolean get() = exceptionTypes.isNotEmpty()

    val displayGtin: String
        get() = gtin?.trim()?.takeIf { it.isNotEmpty() } ?: ""

    val hasRegisteredGtinCandidate: Boolean get() = displayGtin.isNotEmpty()

    val resolvedId: String
        get() = pickLineId?.takeIf { it.isNotBlank() }
            ?: "${intProductId ?: 0}-${productSku.orEmpty()}"

    /** Industry add-to-box API uses task id — matches iOS `apiPickLineId`. */
    val apiPickLineId: String
        get() = taskId?.trim()?.takeIf { it.isNotBlank() }
            ?: pickLineId?.trim()?.takeIf { it.isNotBlank() }
            ?: resolvedId

    val intProductId: Int? get() = productId

    val requiredCount: Int
        get() {
            val requested = requestedQty ?: 0
            return if (requested > 0) requested else pickedCount
        }

    /** Max qty that can be packed into the current box for this line — matches iOS. */
    val packableQty: Int
        get() = pickedCount.takeIf { it > 0 } ?: requiredCount

    val pickedCount: Int get() = maxOf(pickedQty ?: 0, 0)

    val packedCount: Int get() = maxOf(packedQty ?: 0, 0)

    val remainingToPackCount: Int
        get() = remainingToPackQty?.let { maxOf(it, 0) }
            ?: maxOf(requiredCount - packedCount, 0)

    fun linePackingStatusLabel(packedTotal: Int): String {
        packingStatus?.trim()?.uppercase()?.takeIf { it.isNotBlank() }?.let { return it }
        pickLineStatus?.trim()?.uppercase()?.takeIf { it.isNotBlank() }?.let { return it }
        when {
            packedTotal >= requiredCount && requiredCount > 0 -> return "PACKED"
            packedTotal > 0 -> return "PARTIAL"
        }
        return status?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "PENDING"
    }

    val isLineFullyPacked: Boolean
        get() = linePackingStatusLabel(packedCount) == "PACKED" ||
            (remainingToPackCount <= 0 && packedCount >= requiredCount && requiredCount > 0)

    val displayBatch: String
        get() = batchNumber?.trim()?.takeIf { it.isNotBlank() } ?: "—"

    val displayExpiry: String
        get() = expiryDate?.trim()?.takeIf { it.isNotBlank() } ?: "—"

    val batchExpiryDetailLine: String
        get() = "Batch $displayBatch · Exp $displayExpiry"

    val exceptionDetailText: String
        get() = when {
            !exceptionNotes.isNullOrBlank() -> exceptionNotes.trim()
            exceptionTypes.isNotEmpty() -> exceptionTypes.joinToString(", ") { formatPackingExceptionType(it) }
            else -> "Exception"
        }
}

@Serializable
data class WmsPackingBoxSummary(
    val packId: String? = null,
    val packLabel: String? = null,
    val packType: String? = null,
    val hierarchyLevel: Int? = null,
    val parentPackId: String? = null,
    val sscc: String? = null,
    val barcodeType: String? = null,
    val barcodeData: String? = null,
    val barcodeImageUrl: String? = null,
    val status: String? = null,
    val displayStatus: String? = null,
    val packageStatus: String? = null,
    val itemCount: Int? = null,
    val totalPackedQty: Int? = null,
    val items: List<WmsPackingBoxItem>? = null,
) {
    val id: String get() = resolvedPackId

    val resolvedPackId: String
        get() = packId?.trim()?.takeIf { it.isNotBlank() }.orEmpty()
    val resolvedParentPackId: String? get() = parentPackId?.trim()?.takeIf { it.isNotBlank() }
    val resolvedPackType: String
        get() = packType?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            ?: when (hierarchyLevel) {
                3 -> "PALLET"
                else -> "CARTON"
            }
    val resolvedTotalPackedQty: Int
        get() = totalPackedQty ?: items?.sumOf { it.quantity ?: 0 } ?: 0
    val resolvedItemCount: Int
        get() = itemCount ?: items?.size ?: 0
    val isSecondaryPackage: Boolean get() = (hierarchyLevel ?: 2) == 2
    val isTertiaryPackage: Boolean get() = (hierarchyLevel ?: 0) == 3
    val isPackedListSummary: Boolean get() = resolvedPackType == "PACKED_LIST"
    val hasIndustryBoxId: Boolean
        get() = resolvedPackId.toIntOrNull()?.let { it > 0 } == true

    val normalizedBoxStatusLabel: String
        get() = WmsIndustryPackingStatus.normalizedBoxLabel(status, displayStatus ?: packageStatus)

    val isCompleted: Boolean
        get() = WmsIndustryPackingStatus.isBoxSealed(normalizedBoxStatusLabel)

    val isInProgress: Boolean
        get() = !isCompleted && WmsIndustryPackingStatus.isBoxOpen(normalizedBoxStatusLabel)

    val displayTitle: String
        get() = packLabel?.trim()?.takeIf { it.isNotBlank() }
            ?: sscc?.trim()?.takeIf { it.isNotBlank() }
            ?: "Package"

    val resolvedSscc: String get() = sscc?.trim().orEmpty()

    val displaySubtitle: String
        get() {
            val type = resolvedPackType
            val statusLabel = status?.trim().orEmpty().ifBlank { packageStatus?.trim().orEmpty() }
            return when {
                type.isNotBlank() && statusLabel.isNotBlank() -> "$type · $statusLabel"
                statusLabel.isNotBlank() -> statusLabel
                type.isNotBlank() -> type
                else -> ""
            }
        }

    fun packTypeUiLabel(): String =
        when {
            isTertiaryPackage || packType?.trim()?.uppercase() == "PALLET" -> "Tertiary"
            else -> "Secondary"
        }
}

@Serializable
data class WmsPackingPickListItem(
    val packingOrderId: String? = null,
    val packingNumber: String? = null,
    val packingStatus: String? = null,
    val displayPackingStatus: String? = null,
    val status: String? = null,
    val pickListId: String? = null,
    val pickListNumber: String? = null,
    val pickListCode: String? = null,
    val orderId: String? = null,
    val orderNumber: String? = null,
    val zoneCode: String? = null,
    val zone: String? = null,
    val toteNumber: String? = null,
    val stagingLocationName: String? = null,
    val packingStartedAt: String? = null,
    val packedAt: String? = null,
    val lineCount: Int? = null,
    val totalRequestedQty: Int? = null,
    val totalPickedQty: Int? = null,
    val totalPackedQty: Int? = null,
    val totalRemainingToPackQty: Int? = null,
    val packingProgress: String? = null,
    val boxCount: Int? = null,
    val inProgressBoxCount: Int? = null,
    val completedBoxCount: Int? = null,
    val openBoxCount: Int? = null,
    val canComplete: Boolean? = null,
    val lines: List<WmsPackingPickListLine>? = null,
    val boxes: List<WmsPackingBoxSummary>? = null,
) {
    val id: String
        get() = packingOrderId?.takeIf { it.isNotBlank() }
            ?: pickListId?.takeIf { it.isNotBlank() }
            ?: packingNumber.orEmpty()

    val displayTitle: String
        get() = packingNumber?.takeIf { it.isNotBlank() }
            ?: pickListNumber?.takeIf { it.isNotBlank() }
            ?: pickListCode?.takeIf { it.isNotBlank() }
            ?: "Packing list"

    /** Packer home card title — e.g. PICK-00023 from numeric pick list id. */
    val cardTitle: String
        get() = formatPickListDisplayId(pickListId, pickListNumber, pickListCode)

    val resolvedZoneCode: String?
        get() = zoneCode?.takeIf { it.isNotBlank() } ?: zone?.takeIf { it.isNotBlank() }

    val packingProgressLabel: String?
        get() {
            val progress = packingProgress?.trim().orEmpty()
            if (progress.isNotEmpty()) return progress.replace('_', ' ').uppercase()
            if (isPackingQtyComplete && !isPackingStatusPacked) {
                if (isReadyForPackerFinish) return "CREATE PALLET OR COMPLETE"
                return if (hasOpenPackingBoxes) "SEAL OPEN BOXES" else "FULLY PACKED"
            }
            if (displayPackingStatusLabel == "PENDING") return "NOT STARTED"
            return null
        }

    val displayPackingStatusLabel: String
        get() = WmsIndustryPackingStatus.normalizedListLabel(packingStatus, displayPackingStatus)

    val isPackingStatusPacked: Boolean
        get() = WmsIndustryPackingStatus.isListPacked(displayPackingStatusLabel)

    val packLines: List<WmsPackingPickListLine> get() = lines.orEmpty()

    val resolvedTotalPackedQty: Int
        get() = totalPackedQty?.takeIf { it > 0 }
            ?: packLines.sumOf { it.packedCount }.takeIf { it > 0 }
            ?: 0

    val resolvedTotalRequestedQty: Int
        get() = totalPickedQty?.takeIf { it > 0 }
            ?: totalRequestedQty?.takeIf { it > 0 }
            ?: packLines.sumOf { it.pickedCount }.takeIf { it > 0 }
            ?: 0

    val isPackingQtyComplete: Boolean
        get() = packingProgress?.uppercase() == "FULLY_PACKED"
            || (totalRemainingToPackQty != null && totalRemainingToPackQty <= 0 && resolvedTotalPackedQty > 0)
            || (packLines.isNotEmpty() && packLines.all { it.isLineFullyPacked })
            || (resolvedTotalRequestedQty > 0 && resolvedTotalPackedQty >= resolvedTotalRequestedQty)

    val isReadyToMarkPacked: Boolean
        get() = isPackingQtyComplete && !isPackingStatusPacked && !hasOpenPackingBoxes

    val hasOpenPackingBoxes: Boolean
        get() {
            if ((openBoxCount ?: 0) > 0) return true
            if ((inProgressBoxCount ?: 0) > 0) return true
            boxes?.let { list ->
                if (list.any { !it.isCompleted && !it.isPackedListSummary }) return true
            }
            return false
        }

    val isReadyForPackerFinish: Boolean
        get() = canComplete == true ||
            (isPackingQtyComplete && !hasOpenPackingBoxes && !isPackingStatusPacked)

    val isPackingWorkflowComplete: Boolean
        get() {
            if (hasOpenPackingBoxes) return false
            if (isPackingStatusPacked) return isPackingQtyComplete
            if (!isPackingQtyComplete) return false
            val anyBoxes = (boxCount ?: 0) > 0 || boxes.orEmpty().isNotEmpty()
            if (!anyBoxes) return false
            return boxes?.all { it.isCompleted } ?: false
        }

    val isPackingSessionActive: Boolean
        get() {
            if (isPackingStatusPacked) return false
            if (displayPackingStatusLabel == "IN_PROGRESS") {
                if (resolvedTotalPackedQty > 0) return true
                if (packLines.any { it.packedCount > 0 }) return true
            }
            if (hasOpenPackingBoxes && resolvedTotalPackedQty > 0) return true
            if (hasOpenPackingBoxes && packLines.any { it.packedCount > 0 }) return true
            if (isReadyForPackerFinish) return true
            if ((inProgressBoxCount ?: 0) > 0) return true
            if (boxes.orEmpty().any { !it.isCompleted && !it.isPackedListSummary }) return true
            val anyBoxes = (boxCount ?: 0) > 0 || boxes.orEmpty().isNotEmpty()
            if (anyBoxes && isPackingQtyComplete && !isPackingWorkflowComplete) return true
            return false
        }

    val cardStatusLabel: String
        get() = when {
            isPackingStatusPacked && isPackingQtyComplete -> "PACKED"
            isPackingSessionActive -> "IN PROGRESS"
            else -> "PENDING"
        }

    val isPackingInProgress: Boolean
        get() = isPackingSessionActive

    val resolvedInProgressBoxes: List<WmsPackingBoxSummary>
        get() = boxes.orEmpty().filter { it.isInProgress }

    val hasOpenBoxesForContinue: Boolean
        get() = isPackingSessionActive

    val openBoxesForContinue: List<WmsPackingBoxSummary>
        get() = boxes.orEmpty().filter { !it.isCompleted && !it.isPackedListSummary }
}

data class WmsPackingPickListsByPackerPayload(
    val packerId: Int?,
    val count: Int,
    val lists: List<WmsPackingPickListItem>,
)

@Serializable
data class WmsPackingBoxItem(
    val packItemId: String? = null,
    val pickLineId: String? = null,
    @SerialName("taskId") val taskIdAlt: String? = null,
    val pickListId: String? = null,
    val productId: Int? = null,
    val productName: String? = null,
    val productSku: String? = null,
    @SerialName("sku") val sku: String? = null,
    val batch: String? = null,
    @SerialName("batchNumber") val batchNumber: String? = null,
    val quantity: Int? = null,
    val childPackId: String? = null,
    val childPackLabel: String? = null,
    val childPackType: String? = null,
    @SerialName("childPackSSCC") val childPackSscc: String? = null,
    val childPackStatus: String? = null,
    val childBox: WmsPackingBoxNested? = null,
) {
    val resolvedProductSku: String?
        get() = productSku?.trim()?.takeIf { it.isNotBlank() } ?: sku?.trim()?.takeIf { it.isNotBlank() }

    val resolvedBatch: String?
        get() = batch?.trim()?.takeIf { it.isNotBlank() } ?: batchNumber?.trim()?.takeIf { it.isNotBlank() }

    val isChildPackReference: Boolean get() = !childPackId.isNullOrBlank()
}

@Serializable
data class WmsPackingBoxNested(
    val packId: String? = null,
    val packLabel: String? = null,
    val packType: String? = null,
    val hierarchyLevel: Int? = null,
    val sscc: String? = null,
    val barcodeType: String? = null,
    val barcodeData: String? = null,
    val barcodeImageUrl: String? = null,
    val itemCount: Int? = null,
    val totalPackedQty: Int? = null,
    val status: String? = null,
    val items: List<WmsPackingBoxItem>? = null,
)

@Serializable
data class WmsCreatedPackingBox(
    val message: String? = null,
    val packId: String? = null,
    val packingOrderId: String? = null,
    val packageId: String? = null,
    val packLabel: String? = null,
    val packType: String? = null,
    val hierarchyLevel: Int? = null,
    val sscc: String? = null,
    val barcodeType: String? = null,
    val barcodeData: String? = null,
    val barcodeImageUrl: String? = null,
    val itemCount: Int? = null,
    val totalPackedQty: Int? = null,
    val status: String? = null,
    val items: List<WmsPackingBoxItem>? = null,
    val box: WmsPackingBoxNested? = null,
) {
    val resolvedPackId: String
        get() = packId?.trim()?.takeIf { it.isNotBlank() }
            ?: box?.packId?.trim()?.takeIf { it.isNotBlank() }
            ?: ""

    val resolvedPackLabel: String
        get() = packLabel?.takeIf { it.isNotBlank() }
            ?: box?.packLabel?.takeIf { it.isNotBlank() }
            ?: "Box"

    val resolvedItems: List<WmsPackingBoxItem>
        get() = items?.takeIf { it.isNotEmpty() } ?: box?.items.orEmpty()

    val resolvedTotalPackedQty: Int
        get() = totalPackedQty
            ?: box?.totalPackedQty
            ?: resolvedItems.sumOf { it.quantity ?: 0 }

    val resolvedItemCount: Int
        get() = itemCount ?: box?.itemCount ?: resolvedItems.size

    val resolvedBarcodeType: String
        get() = barcodeType?.trim()?.takeIf { it.isNotBlank() }
            ?: box?.barcodeType?.trim()?.takeIf { it.isNotBlank() }
            ?: "CODE128"

    val resolvedBarcodeData: String
        get() = barcodeData?.trim()?.takeIf { it.isNotBlank() }
            ?: box?.barcodeData?.trim()?.takeIf { it.isNotBlank() }
            ?: sscc?.trim()?.takeIf { it.isNotBlank() }
            ?: box?.sscc?.trim().orEmpty()

    val resolvedBarcodeImageUrl: String?
        get() = resolveMediaUrl(
            barcodeImageUrl?.trim()?.takeIf { it.isNotBlank() }
                ?: box?.barcodeImageUrl?.trim()?.takeIf { it.isNotBlank() },
        )

    val resolvedPackageStatus: String
        get() = status?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            ?: box?.status?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
            ?: if (resolvedPackId.isNotBlank()) "IN_PROGRESS" else ""

    val isPackageCompleted: Boolean
        get() = resolvedPackageStatus in setOf("COMPLETED", "SEALED", "PACKED")

    val isPackageInProgress: Boolean
        get() = resolvedPackId.isNotBlank() && !isPackageCompleted

    val hasPackageContents: Boolean
        get() = resolvedTotalPackedQty > 0 || resolvedItems.isNotEmpty()

    fun packedQtyForLine(lineKey: String): Int {
        val key = lineKey.trim()
        if (key.isEmpty()) return 0
        return resolvedItems
            .filter { item ->
                val pickLine = item.pickLineId?.trim().orEmpty()
                val taskId = item.taskIdAlt?.trim().orEmpty()
                pickLine == key || taskId == key
            }
            .sumOf { it.quantity ?: 0 }
    }
}

/** Optimistic merge when box refresh fails — matches iOS `boxWithItemMerged`. */
fun WmsCreatedPackingBox.withItemMerged(
    line: WmsPackingPickListLine,
    quantity: Int,
    packerId: Int? = null,
): WmsCreatedPackingBox {
    val lineKey = line.apiPickLineId.trim()
    val newItem = WmsPackingBoxItem(
        pickLineId = line.apiPickLineId,
        taskIdAlt = line.taskId ?: line.apiPickLineId,
        productId = line.intProductId,
        productName = line.productName,
        productSku = line.productSku,
        quantity = quantity,
    )
    val mergedItems = resolvedItems.toMutableList()
    val existingIndex = mergedItems.indexOfFirst { item ->
        val pickLine = item.pickLineId?.trim().orEmpty()
        val taskId = item.taskIdAlt?.trim().orEmpty()
        pickLine == lineKey || taskId == lineKey
    }
    if (existingIndex >= 0) {
        val existing = mergedItems[existingIndex]
        mergedItems[existingIndex] = existing.copy(
            quantity = (existing.quantity ?: 0) + quantity,
        )
    } else {
        mergedItems += newItem
    }
    return copy(
        items = mergedItems,
        itemCount = mergedItems.size,
        totalPackedQty = mergedItems.sumOf { it.quantity ?: 0 },
    )
}

/** Keep the richer in-session box state when merging with a server refresh. */
fun WmsCreatedPackingBox.mergeSessionState(other: WmsCreatedPackingBox): WmsCreatedPackingBox {
    val preferThis = when {
        resolvedItems.isNotEmpty() && other.resolvedItems.isEmpty() -> true
        other.resolvedItems.isNotEmpty() && resolvedItems.isEmpty() -> false
        else -> resolvedTotalPackedQty >= other.resolvedTotalPackedQty
    }
    val primary = if (preferThis) this else other
    val secondary = if (preferThis) other else this
    val mergedItems = when {
        primary.resolvedItems.isNotEmpty() -> primary.resolvedItems
        secondary.resolvedItems.isNotEmpty() -> secondary.resolvedItems
        else -> emptyList()
    }
    return primary.copy(
        packLabel = primary.packLabel?.takeIf { it.isNotBlank() } ?: secondary.packLabel,
        sscc = primary.sscc?.takeIf { it.isNotBlank() } ?: secondary.sscc,
        barcodeType = primary.barcodeType?.takeIf { it.isNotBlank() } ?: secondary.barcodeType,
        barcodeData = primary.barcodeData?.takeIf { it.isNotBlank() } ?: secondary.barcodeData,
        barcodeImageUrl = primary.barcodeImageUrl?.takeIf { it.isNotBlank() } ?: secondary.barcodeImageUrl,
        items = mergedItems.takeIf { it.isNotEmpty() },
        itemCount = mergedItems.size.takeIf { it > 0 } ?: primary.itemCount ?: secondary.itemCount,
        totalPackedQty = maxOf(resolvedTotalPackedQty, other.resolvedTotalPackedQty),
        status = primary.status?.takeIf { it.isNotBlank() } ?: secondary.status,
    )
}

/** Keep a known box/pallet id when seal/link responses omit `id` / `packId` (iOS parity). */
fun WmsCreatedPackingBox.preservingPackId(fallback: String): WmsCreatedPackingBox {
    val trimmed = fallback.trim()
    if (resolvedPackId.isNotBlank() || trimmed.isEmpty()) return this
    return copy(packId = trimmed)
}

/** Force the session id after seal — some backends return a new row id instead of the sealed box. */
fun WmsCreatedPackingBox.withKnownPackId(knownId: String): WmsCreatedPackingBox {
    val known = knownId.trim()
    if (known.isEmpty()) return this
    return copy(packId = known)
}

/** Build a box session from list/summary data — matches iOS `WMSCreatedPackingBox(from: summary)`. */
fun WmsPackingBoxSummary.toCreatedPackingBox(): WmsCreatedPackingBox =
    WmsCreatedPackingBox(
        packId = resolvedPackId.takeIf { it.isNotBlank() } ?: packId,
        packLabel = packLabel,
        packType = packType ?: resolvedPackType,
        hierarchyLevel = hierarchyLevel,
        sscc = sscc,
        barcodeType = barcodeType,
        barcodeData = barcodeData?.trim()?.takeIf { it.isNotBlank() }
            ?: resolvedSscc.takeIf { it.isNotBlank() },
        barcodeImageUrl = barcodeImageUrl,
        itemCount = itemCount ?: items?.size,
        totalPackedQty = totalPackedQty ?: resolvedTotalPackedQty,
        status = status ?: packageStatus,
        items = items,
    )

/** Merge richer industry box metadata into an active session box (barcode image, counts, items). */
fun WmsCreatedPackingBox.enrichedFrom(summary: WmsPackingBoxSummary): WmsCreatedPackingBox {
    val sessionItems = resolvedItems
    return copy(
        packId = resolvedPackId.ifBlank { summary.resolvedPackId }.ifBlank { packId },
        packLabel = packLabel?.takeIf { it.isNotBlank() } ?: summary.packLabel,
        packType = packType?.takeIf { it.isNotBlank() } ?: summary.packType ?: summary.resolvedPackType,
        hierarchyLevel = hierarchyLevel ?: summary.hierarchyLevel,
        sscc = sscc?.takeIf { it.isNotBlank() } ?: summary.sscc,
        barcodeType = barcodeType?.takeIf { it.isNotBlank() } ?: summary.barcodeType,
        barcodeData = barcodeData?.takeIf { it.isNotBlank() }
            ?: summary.barcodeData?.trim()?.takeIf { it.isNotBlank() }
            ?: summary.resolvedSscc.takeIf { it.isNotBlank() },
        barcodeImageUrl = barcodeImageUrl?.takeIf { it.isNotBlank() } ?: summary.barcodeImageUrl,
        itemCount = sessionItems.size.takeIf { it > 0 } ?: itemCount ?: summary.itemCount,
        totalPackedQty = maxOf(resolvedTotalPackedQty, summary.resolvedTotalPackedQty),
        status = status?.takeIf { it.isNotBlank() } ?: summary.status ?: summary.packageStatus,
        items = sessionItems.takeIf { it.isNotEmpty() }
            ?: summary.items?.takeIf { it.isNotEmpty() },
    )
}

internal fun resolveMediaUrl(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)
    ) {
        return trimmed
    }
    if (trimmed.startsWith("/")) return Config.BASE_URL + trimmed
    return trimmed
}

// ── Receiving ─────────────────────────────────────────────────────────────────

@Serializable
data class WmsPackingReceiverLine(
    val pickLineId: String? = null,
    val pickListId: String? = null,
    val pickListNumber: String? = null,
    val productId: Int? = null,
    val productName: String? = null,
    val productSku: String? = null,
    val packedQty: Int? = null,
    val receivedQty: Int? = null,
    val remainingToReceiveQty: Int? = null,
    val receivingStatus: String? = null,
) {
    val id: String
        get() = pickLineId?.takeIf { it.isNotBlank() }
            ?: "${productId ?: 0}-${productSku.orEmpty()}"

    val displayName: String
        get() = productName?.takeIf { it.isNotBlank() } ?: productSku ?: "Line item"

    val resolvedPackedQty: Int get() = maxOf(packedQty ?: 0, 0)
    val resolvedReceivedQty: Int get() = maxOf(receivedQty ?: 0, 0)
    val resolvedRemainingQty: Int
        get() = remainingToReceiveQty ?: maxOf(0, resolvedPackedQty - resolvedReceivedQty)

    val resolvedPickLineId: String
        get() = pickLineId?.trim()?.takeIf { it.isNotBlank() } ?: id

    val displaySku: String
        get() = productSku?.trim()?.takeIf { it.isNotBlank() } ?: "—"

    val normalizedReceivingStatus: String
        get() = receivingStatus?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: "PENDING"

    fun matchesPickList(pickList: WmsPackingReceiverNode): Boolean {
        val listId = pickList.pickListId?.trim().orEmpty()
        val listNumber = pickList.pickListNumber?.trim().orEmpty()
        if (listId.isNotEmpty() && pickListId?.trim() == listId) return true
        if (listNumber.isNotEmpty() && pickListNumber?.trim() == listNumber) return true
        return false
    }

    fun matchesTreeNode(node: WmsPackingReceiverNode): Boolean {
        val nodeLineId = node.pickLineId?.trim().orEmpty()
        if (nodeLineId.isNotEmpty() && nodeLineId == resolvedPickLineId) return true
        if (node.productId != null && productId != null && node.productId == productId) return true
        val nodeSku = node.productSku?.trim()?.lowercase().orEmpty()
        val lineSku = displaySku.lowercase()
        if (nodeSku.isNotEmpty() && nodeSku == lineSku && lineSku != "—") return true
        val nodeName = node.productName?.trim()?.lowercase().orEmpty()
        val lineName = displayName.lowercase()
        return nodeName.isNotEmpty() && nodeName == lineName
    }

    val isFullyReceived: Boolean
        get() = normalizedReceivingStatus == "RECEIVED"
            || (resolvedPackedQty > 0 && resolvedReceivedQty >= resolvedPackedQty)
}

@Serializable
data class WmsPackingReceiverReceivingStatus(
    val orderId: String? = null,
    val orderNumber: String? = null,
    val sessionStatus: String? = null,
    val totalLines: Int? = null,
    val receivedLines: Int? = null,
    val pendingLines: Int? = null,
    val partialLines: Int? = null,
    val totalPackedQty: Int? = null,
    val totalReceivedQty: Int? = null,
    val isComplete: Boolean? = null,
)

@Serializable
data class WmsPackingReceiverNode(
    val nodeType: String? = null,
    val orderId: String? = null,
    val orderNumber: String? = null,
    val customerName: String? = null,
    val orderStatus: String? = null,
    val receivingStatus: String? = null,
    val receivingSessionStatus: String? = null,
    val isReceivingComplete: Boolean? = null,
    val totalPackedQty: Int? = null,
    val totalReceivedQty: Int? = null,
    val totalPickedQty: Int? = null,
    val receivedQty: Int? = null,
    val packedLineCount: Int? = null,
    val receivedLineCount: Int? = null,
    val pendingLineCount: Int? = null,
    val pickListId: String? = null,
    val pickListNumber: String? = null,
    val pickLineId: String? = null,
    val packingOrderId: String? = null,
    val lineCount: Int? = null,
    val productId: Int? = null,
    val productName: String? = null,
    val productSku: String? = null,
    val requestedQty: Int? = null,
    val pickedQty: Int? = null,
    val packedQty: Int? = null,
    val packId: String? = null,
    val packLabel: String? = null,
    val packType: String? = null,
    val sscc: String? = null,
    val quantity: Int? = null,
    val batch: String? = null,
    val packingStatus: String? = null,
    val packingNumber: String? = null,
    val toteNumber: String? = null,
    val stagingLocationName: String? = null,
    val packerName: String? = null,
    val assignedPacker: Int? = null,
    val status: String? = null,
    val hierarchyLevel: Int? = null,
    val boxCount: Int? = null,
    val packedPickListCount: Int? = null,
    val completedBoxCount: Int? = null,
    val totalBoxCount: Int? = null,
    val shippingMethod: String? = null,
    val children: List<WmsPackingReceiverNode>? = null,
) {
    val normalizedNodeType: String get() = nodeType?.trim()?.uppercase().orEmpty()

    val id: String
        get() = when (normalizedNodeType) {
            "ORDER" -> "order-${orderId ?: orderNumber.orEmpty()}"
            "PICK_LIST" -> "pick-${pickListId ?: pickListNumber.orEmpty()}"
            "LINE" -> "line-${pickListId.orEmpty()}-${productSku.orEmpty()}"
            "BOX" -> "box-${packId ?: sscc.orEmpty()}"
            else -> "node-${orderId.orEmpty()}-${pickListId.orEmpty()}"
        }

    val displayTitle: String
        get() = when (normalizedNodeType) {
            "ORDER" -> orderNumber ?: orderId ?: "Order"
            "PICK_LIST" -> formatPickListDisplayId(pickListId, pickListNumber)
            "LINE" -> productName ?: productSku ?: "Line item"
            "PACK_ITEM" -> productName ?: productSku ?: batch ?: "Pack item"
            "BOX" -> packLabel ?: sscc ?: "Box"
            else -> nodeType ?: "Node"
        }

    val displaySubtitle: String
        get() = when (normalizedNodeType) {
            "ORDER" -> customerName ?: orderStatus.orEmpty()
            "PICK_LIST" -> status.orEmpty().ifBlank { receivingStatus.orEmpty() }
            "LINE" -> {
                val sku = productSku?.trim().orEmpty()
                if (sku.isNotEmpty()) "SKU $sku" else packingStatus.orEmpty()
            }
            "BOX" -> {
                val type = packType?.trim().orEmpty()
                val code = sscc?.trim().orEmpty()
                when {
                    type.isNotEmpty() && code.isNotEmpty() -> "$type · $code"
                    code.isNotEmpty() -> code
                    type.isNotEmpty() -> type
                    else -> ""
                }
            }
            "PACK_ITEM" -> {
                val sku = productSku?.trim().orEmpty()
                val qty = quantity?.let { "$it units" }.orEmpty()
                when {
                    sku.isNotEmpty() && qty.isNotEmpty() -> "SKU $sku · $qty"
                    sku.isNotEmpty() -> "SKU $sku"
                    else -> qty
                }
            }
            else -> productSku.orEmpty()
        }

    fun isReceivedForDisplay(): Boolean {
        if (isReceivingComplete == true) return true
        val status = receivingStatus?.uppercase().orEmpty()
        if (status in setOf("RECEIVED", "COMPLETE", "COMPLETED")) return true
        val session = receivingSessionStatus?.trim()?.uppercase().orEmpty()
        return session in setOf("COMPLETED", "RECEIVED")
    }

    val resolvedAPIOrderId: String?
        get() = orderId?.trim()?.takeIf { it.isNotBlank() }
            ?: orderNumber?.trim()?.takeIf { it.isNotBlank() }

    val displayReceivingStatusLabel: String
        get() {
            if (isReceivedForDisplay()) return "COMPLETED"
            val receiving = receivingStatus?.trim()?.uppercase()?.replace(' ', '_').orEmpty()
            if (receiving == "NOT_STARTED") return "NOT STARTED"
            if (receiving.isNotEmpty()) return receiving.replace('_', ' ')
            if ((totalPackedQty ?: 0) > 0) return "READY"
            return "PENDING"
        }

    val receivingProgressLabel: String?
        get() {
            val packed = totalPackedQty ?: return null
            if (packed <= 0) return null
            val received = totalReceivedQty ?: receivedQty ?: 0
            return "$received/$packed units"
        }

    val resolvedPackerName: String
        get() = packerName?.trim().orEmpty().ifBlank {
            assignedPacker?.let { "Packer #$it" }.orEmpty()
        }

    val resolvedToteNumber: String? get() = toteNumber?.trim()?.takeIf { it.isNotBlank() }
    val resolvedStagingLocationName: String? get() = stagingLocationName?.trim()?.takeIf { it.isNotBlank() }

    val resolvedPackType: String
        get() {
            val explicit = packType?.trim().orEmpty().uppercase()
            return if (explicit.isNotBlank()) {
                explicit
            } else {
                when (hierarchyLevel) {
                    3 -> "PALLET"
                    2 -> "CARTON"
                    else -> "CARTON"
                }
            }
        }

    val isCartonBox: Boolean
        get() = resolvedPackType in setOf("CARTON", "CASE", "INNER")

    val isPalletBox: Boolean
        get() = resolvedPackType == "PALLET" || hierarchyLevel == 3

    val pickListNodes: List<WmsPackingReceiverNode>
        get() = (children ?: emptyList()).filter { it.normalizedNodeType == "PICK_LIST" }

    val childLines: List<WmsPackingReceiverNode>
        get() = (children ?: emptyList()).filter { it.normalizedNodeType == "LINE" }

    val childBoxes: List<WmsPackingReceiverNode>
        get() = (children ?: emptyList()).filter { it.normalizedNodeType == "BOX" }

    val packItems: List<WmsPackingReceiverNode>
        get() = (children ?: emptyList()).filter { it.normalizedNodeType == "PACK_ITEM" }

    val boxProgressLabel: String?
        get() {
            val completed = completedBoxCount
            val total = totalBoxCount
            return if (completed != null && total != null && total > 0) {
                "$completed/$total boxes"
            } else null
        }
}

// ── Request bodies ────────────────────────────────────────────────────────────

@Serializable
data class WmsAssignPickListRequest(
    val pickerId: Int,
)

@Serializable
data class PickerExceptionDetail(
    val type: String = "",
    val qty: Int = 0,
)

fun parsePickerExceptionSummary(summary: String): List<PickerExceptionDetail> {
    val main = summary.substringBefore(" — ").trim()
    if (main.isBlank()) return emptyList()
    val pattern = Regex("""([^·×]+?)\s*×(\d+)""")
    return pattern.findAll(main).mapNotNull { match ->
        val label = match.groupValues[1].trim()
        val qty = match.groupValues[2].toIntOrNull() ?: return@mapNotNull null
        if (qty <= 0) return@mapNotNull null
        PickerExceptionDetail(pickerExceptionLabelToType(label), qty)
    }.toList()
}

fun pickerExceptionLabelToType(label: String): String = when (label.trim().lowercase()) {
    "shortage" -> "SHORTAGE"
    "damaged" -> "DAMAGED"
    "bin empty" -> "BIN_EMPTY"
    "wrong batch" -> "WRONG_BATCH"
    "not found" -> "NOT_FOUND"
    "other" -> "OTHER"
    else -> label.trim().uppercase().replace(' ', '_')
}

fun FefoBatchItem.stateKey(): String {
    val task = resolvedTaskIdString
    if (task.isNotBlank()) return "task:$task"
    return "batch:${resolvedBatch}|${resolvedLocation}|$batchSequence"
}

fun FefoBatchItem.batchPickKey(groupKey: String, groupIndex: Int, batchIndex: Int): String {
    val task = resolvedTaskIdString
    if (task.isNotBlank()) return "task:$task"
    val groupPart = groupKey.trim().ifBlank { "idx:$groupIndex" }
    return "pick:$groupPart:$batchIndex:${resolvedBatch}|${resolvedLocation}|$batchSequence"
}

fun FefoBatchItem.matchesBatch(other: FefoBatchItem): Boolean = stateKey() == other.stateKey()

fun FefoBatchItem.matchesBatchAt(
    other: FefoBatchItem,
    groupIndex: Int,
    batchIndex: Int,
    groupKey: String = "",
): Boolean {
    if (groupIndex >= 0 && batchIndex >= 0) {
        return batchPickKey(groupKey, groupIndex, batchIndex) ==
            other.batchPickKey(groupKey, groupIndex, batchIndex)
    }
    return matchesBatch(other)
}

@Serializable
data class FefoBatchItem(
    val taskId: Int = 0,
    @SerialName("id") val id: Int? = null,
    @SerialName("pickLineId") val pickLineId: String? = null,
    @SerialName("pick_line_id") val pickLineIdSnake: String? = null,
    val batchSequence: Int = 0,
    val batchLabel: String = "",
    val batch: String = "",
    @SerialName("batchNumber") val batchNumber: String? = null,
    @SerialName("batch_number") val batchNumberSnake: String? = null,
    val location: String = "",
    @SerialName("locationCode") val locationCode: String? = null,
    @SerialName("location_code") val locationCodeSnake: String? = null,
    val bin: String = "",
    val requestedQty: Int = 0,
    @SerialName("quantity") val quantity: Int? = null,
    @SerialName("qty") val qty: Int? = null,
    val pickedQty: Int = 0,
    @SerialName("picked_qty") val pickedQtySnake: Int? = null,
    val status: String = "pending",
    val exceptionMessage: String = "",
    @SerialName("exception_message") val exceptionMessageSnake: String? = null,
    @SerialName("shortfallQty") val shortfallQty: Int? = null,
    @SerialName("shortfall_qty") val shortfallQtySnake: Int? = null,
    @SerialName("exceptionTypes") val exceptionTypes: List<String> = emptyList(),
    @SerialName("exception_types") val exceptionTypesSnake: List<String>? = null,
    @SerialName("exceptionNotes") val exceptionNotes: String? = null,
    @SerialName("exception_notes") val exceptionNotesSnake: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("exception") val exception: String? = null,
    val exceptionDetails: List<PickerExceptionDetail> = emptyList(),
) {
    val resolvedTaskId: Int
        get() = taskId.takeIf { it > 0 } ?: id?.takeIf { it > 0 } ?: 0

    val resolvedTaskIdString: String
        get() = sequenceOf(
            pickLineId?.trim()?.takeIf { it.isNotEmpty() },
            pickLineIdSnake?.trim()?.takeIf { it.isNotEmpty() },
            taskId.takeIf { it > 0 }?.toString(),
            id?.takeIf { it > 0 }?.toString(),
        ).filterNotNull().firstOrNull().orEmpty()
    /** Resolves batch from `batch`, `batchNumber`, or `batch_number` (same as iOS). */
    val resolvedBatch: String
        get() = sequenceOf(batch, batchNumber, batchNumberSnake)
            .mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .firstOrNull()
            .orEmpty()

    val resolvedRequestedQty: Int
        get() = when {
            requestedQty > 0 -> requestedQty
            quantity != null && quantity > 0 -> quantity
            qty != null && qty > 0 -> qty
            else -> requestedQty
        }

    val resolvedPickedQty: Int
        get() = pickedQty.takeIf { it > 0 } ?: pickedQtySnake ?: 0

    val resolvedExceptionQty: Int
        get() {
            val fromDetails = resolvedExceptionDetails.sumOf { it.qty }
            if (fromDetails > 0) return fromDetails
            return shortfallQty?.takeIf { it > 0 } ?: shortfallQtySnake?.takeIf { it > 0 } ?: 0
        }

    val resolvedExceptionDetails: List<PickerExceptionDetail>
        get() {
            val withQty = exceptionDetails.filter { it.qty > 0 && it.type.isNotBlank() }
            if (withQty.isNotEmpty()) return withQty

            val summaryText = sequenceOf(
                exceptionMessage,
                exceptionNotes,
                exceptionNotesSnake,
                exception,
            ).mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }.firstOrNull().orEmpty()
            val parsed = parsePickerExceptionSummary(summaryText)
            if (parsed.isNotEmpty()) return parsed

            val types = exceptionTypes.ifEmpty { exceptionTypesSnake.orEmpty() }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val total = shortfallQty?.takeIf { it > 0 } ?: shortfallQtySnake?.takeIf { it > 0 } ?: 0
            if (types.isNotEmpty() && total > 0) {
                return listOf(PickerExceptionDetail(types.first(), total))
            }
            if (types.isNotEmpty() && resolvedPickedQty < resolvedRequestedQty) {
                return listOf(
                    PickerExceptionDetail(
                        types.first(),
                        resolvedRequestedQty - resolvedPickedQty,
                    ),
                )
            }
            return emptyList()
        }

    val resolvedLocation: String
        get() = sequenceOf(location, locationCode, locationCodeSnake, bin)
            .mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .firstOrNull()
            .orEmpty()

    val hasException: Boolean
        get() {
            if (exceptionMessage.isNotBlank()) return true
            if (exceptionTypes.isNotEmpty() || !exceptionTypesSnake.isNullOrEmpty()) return true
            if (exceptionDetails.isNotEmpty()) return true
            if ((shortfallQty ?: 0) > 0 || (shortfallQtySnake ?: 0) > 0) return true
            if (!exceptionNotes.isNullOrBlank() || !exceptionNotesSnake.isNullOrBlank()) return true
            if (!exception.isNullOrBlank()) return true
            val normalized = status.trim().lowercase()
            return normalized.contains("exception")
        }

    val isPartialPicked: Boolean
        get() = hasException &&
            resolvedRequestedQty > 0 &&
            resolvedPickedQty < resolvedRequestedQty

    val isFullyPicked: Boolean
        get() = when {
            hasException -> true
            resolvedRequestedQty > 0 && resolvedPickedQty >= resolvedRequestedQty -> true
            else -> false
        }

    val remainingPickQty: Int
        get() = if (hasException) {
            0
        } else {
            maxOf(0, resolvedRequestedQty - resolvedPickedQty)
        }

    /** Picked normally or closed out via exception — counts toward picker progress/SKUs. */
    val isPickerHandled: Boolean
        get() {
            if (isFullyPicked) return true
            val normalized = status.trim().lowercase()
            if (normalized == "picked" || normalized == "completed") return true
            if (normalized.contains("exception")) return true
            return false
        }
}

/** Exception rows for line-item UI — always returns entries when [hasException]. */
fun FefoBatchItem.displayExceptionEntries(): List<PickerExceptionDetail> {
    if (!hasException) return emptyList()

    val structured = exceptionDetails.filter { it.qty > 0 && it.type.isNotBlank() }
    if (structured.isNotEmpty()) return structured

    val parsed = parsePickerExceptionSummary(
        sequenceOf(exceptionMessage, exceptionNotes, exceptionNotesSnake, exception)
            .mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .firstOrNull()
            .orEmpty(),
    )
    if (parsed.isNotEmpty()) return parsed

    val fromResolved = resolvedExceptionDetails
    if (fromResolved.isNotEmpty()) return fromResolved

    val types = exceptionTypes.ifEmpty { exceptionTypesSnake.orEmpty() }
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val missingQty = maxOf(0, resolvedRequestedQty - resolvedPickedQty)
    val exceptionQty = when {
        (shortfallQty ?: 0) > 0 -> shortfallQty!!
        (shortfallQtySnake ?: 0) > 0 -> shortfallQtySnake!!
        missingQty > 0 -> missingQty
        else -> 0
    }
    if (types.isNotEmpty() && exceptionQty > 0) {
        return listOf(PickerExceptionDetail(types.first(), exceptionQty))
    }
    if (types.isNotEmpty()) {
        return types.map { PickerExceptionDetail(it, missingQty.coerceAtLeast(1)) }
    }
    if (exceptionMessage.isNotBlank() && missingQty > 0) {
        return listOf(PickerExceptionDetail("OTHER", missingQty))
    }
    return emptyList()
}

/** Human-readable notes / message for line-item exception UI (excludes qty summary when breakdown is shown). */
fun FefoBatchItem.resolvedExceptionMessage(): String {
    val explicitNotes = sequenceOf(exceptionNotes, exceptionNotesSnake, notes, exception)
        .mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
        .firstOrNull()
    if (!explicitNotes.isNullOrBlank()) return explicitNotes

    val resolvedMsg = exceptionMessage.ifBlank { exceptionMessageSnake.orEmpty() }

    val trailingNotes = resolvedMsg.substringAfter(" — ", "").trim()
    if (trailingNotes.isNotBlank()) return trailingNotes

    if (resolvedMsg.isNotBlank()) {
        return resolvedMsg.trim()
    }
    return ""
}

@Serializable
data class FefoProductGroup(
    val key: String = "",
    val productName: String = "",
    val sku: String = "",
    val gtin: String = "",
    val status: String = "pending",
    val totalRequested: Int = 0,
    val totalPicked: Int = 0,
    val batchCount: Int = 0,
    val batches: List<FefoBatchItem> = emptyList(),
    @SerialName("exceptionTypes") val exceptionTypes: List<String> = emptyList(),
    @SerialName("exception_types") val exceptionTypesSnake: List<String>? = null,
    @SerialName("exceptionNotes") val exceptionNotes: String? = null,
    @SerialName("exception_notes") val exceptionNotesSnake: String? = null,
) {
    val resolvedExceptionTypes: List<String>
        get() = exceptionTypes.ifEmpty { exceptionTypesSnake.orEmpty() }

    val resolvedExceptionNotes: String?
        get() = exceptionNotes?.trim()?.takeIf { it.isNotEmpty() }
            ?: exceptionNotesSnake?.trim()?.takeIf { it.isNotEmpty() }

    val effectiveBatchCount: Int
        get() = maxOf(batchCount, batches.size).takeIf { it > 0 } ?: batches.size

    val isComplete: Boolean
        get() = batches.isNotEmpty() && batches.all { it.isPickerHandled }

    val isFullyPicked: Boolean
        get() = batches.isNotEmpty() && batches.all { it.isFullyPicked }

    val pickedBatchCount: Int
        get() = batches.count { it.isPickerHandled }
}

@Serializable
data class FefoTasksResponse(
    val tasks: List<FefoProductGroup> = emptyList(),
    val items: List<FefoProductGroup> = emptyList(),
)

internal fun decodeFefoPickListTasks(raw: String): List<FefoProductGroup> {
    val element = runCatching { wmsJson.parseToJsonElement(raw) }.getOrNull() ?: return emptyList()
    val groups = when (element) {
        is JsonArray -> element.mapNotNull { decodeFefoProductGroupElement(it) }
        is JsonObject -> {
            val nested = element.pickArray("tasks", "items", "data", "lines", "pickTasks")
            if (nested.isNotEmpty()) {
                nested.mapNotNull { decodeFefoProductGroupElement(it) }
            } else {
                listOfNotNull(decodeFefoProductGroupElement(element))
            }
        }
        else -> emptyList()
    }
    return normalizeFefoProductGroups(groups)
}

private fun decodeFefoProductGroupElement(element: JsonElement): FefoProductGroup? {
    val obj = element.jsonObject
    val batchArray = obj.pickArray("batches")
    if (batchArray.isNotEmpty()) {
        val base = runCatching {
            wmsJson.decodeFromJsonElement(FefoProductGroup.serializer(), element)
        }.getOrNull() ?: return null
        val parentTaskId = obj.optionalString("taskId", "task_id", "id", "pickLineId", "pick_line_id")
        val parentTypes = obj.decodeStringList("exceptionTypes", "exception_types")
        val parentNotes = obj.optionalString("exceptionNotes", "exception_notes", "notes")
        val parentShortfall = obj.optionalInt("shortfallQty", "shortfall_qty")
        val batches = batchArray.mapIndexed { index, batchElement ->
            val decoded = decodeFefoBatchFromJson(batchElement.jsonObject)
            val withParentTask = if (decoded.resolvedTaskIdString.isBlank() && !parentTaskId.isNullOrBlank()) {
                decoded.copy(pickLineId = parentTaskId)
            } else {
                decoded
            }
            withParentTask.copy(
                batchSequence = withParentTask.batchSequence.takeIf { it > 0 } ?: (index + 1),
                exceptionTypes = withParentTask.exceptionTypes.ifEmpty { parentTypes },
                exceptionNotes = withParentTask.exceptionNotes ?: parentNotes,
                shortfallQty = withParentTask.shortfallQty ?: parentShortfall,
            )
        }
        return normalizeFefoProductGroup(
            base.copy(
                batches = batches,
                exceptionTypes = base.exceptionTypes.ifEmpty { parentTypes },
                exceptionNotes = base.exceptionNotes ?: parentNotes,
            ),
        )
    }
    return normalizeFefoProductGroup(flatTaskToProductGroup(obj))
}

private fun flatTaskToProductGroup(obj: JsonObject): FefoProductGroup {
    val batch = decodeFefoBatchFromJson(obj)
    val key = obj.optionalString("key", "productId", "product_id", "sku")
        ?: batch.resolvedBatch.ifBlank { batch.resolvedLocation }.ifBlank { "task" }
    val requested = batch.resolvedRequestedQty
    val picked = batch.resolvedPickedQty
    return FefoProductGroup(
        key = key,
        productName = obj.optionalString("productName", "product_name", "name").orEmpty(),
        sku = obj.optionalString("sku", "productSku", "product_sku").orEmpty(),
        gtin = obj.optionalString("gtin").orEmpty(),
        status = obj.optionalString("status").orEmpty(),
        totalRequested = requested,
        totalPicked = picked,
        batchCount = 1,
        batches = listOf(batch),
        exceptionTypes = obj.decodeStringList("exceptionTypes", "exception_types"),
        exceptionNotes = obj.optionalString("exceptionNotes", "exception_notes", "notes"),
    )
}

private fun decodeFefoBatchFromJson(obj: JsonObject): FefoBatchItem {
    val base = runCatching {
        wmsJson.decodeFromJsonElement(FefoBatchItem.serializer(), obj)
    }.getOrNull()
    val taskIdStr = obj.optionalString("taskId", "task_id", "id", "pickLineId", "pick_line_id")
    val taskIdInt = taskIdStr?.toIntOrNull() ?: base?.taskId ?: 0
    val idInt = obj.optionalString("id")?.toIntOrNull() ?: base?.id
    val shortfall = obj.optionalInt("shortfallQty", "shortfall_qty")
    return (base ?: FefoBatchItem()).copy(
        taskId = taskIdInt,
        id = idInt,
        pickLineId = taskIdStr?.takeIf { taskIdInt <= 0 } ?: base?.pickLineId,
        batch = obj.optionalString("batch", "batchNumber", "batch_number") ?: base?.batch.orEmpty(),
        batchNumber = obj.optionalString("batchNumber", "batch_number") ?: base?.batchNumber,
        location = obj.optionalString("location", "locationCode", "location_code", "bin")
            ?: base?.location.orEmpty(),
        locationCode = obj.optionalString("locationCode", "location_code") ?: base?.locationCode,
        requestedQty = obj.optionalInt("requestedQty", "requested_qty", "quantity", "qty")
            ?: base?.requestedQty ?: 0,
        pickedQty = obj.optionalInt("pickedQty", "picked_qty") ?: base?.pickedQty ?: 0,
        status = obj.optionalString("status") ?: base?.status.orEmpty(),
        shortfallQty = shortfall ?: base?.shortfallQty,
        exceptionTypes = obj.decodeStringList("exceptionTypes", "exception_types")
            .ifEmpty { base?.exceptionTypes.orEmpty() },
        exceptionMessage = obj.optionalString("exceptionMessage", "exception_message")
            ?: base?.exceptionMessage.orEmpty(),
        exceptionNotes = obj.optionalString("exceptionNotes", "exception_notes", "notes")
            ?: base?.exceptionNotes,
        batchSequence = obj.optionalInt("batchSequence", "batch_sequence") ?: base?.batchSequence ?: 0,
        batchLabel = obj.optionalString("batchLabel", "batch_label") ?: base?.batchLabel.orEmpty(),
    )
}

private fun normalizeFefoProductGroups(groups: List<FefoProductGroup>): List<FefoProductGroup> {
    val expanded = groups.map { normalizeFefoProductGroup(it) }
    val grouped = expanded.groupBy { group ->
        group.key.trim().ifBlank {
            group.sku.trim().ifBlank { group.productName.trim().ifBlank { group.gtin } }
        }
    }
    return grouped.values.map { mergeFefoProductGroups(it) }
}

private fun mergeFefoProductGroups(groups: List<FefoProductGroup>): FefoProductGroup {
    val first = groups.first()
    val batches = groups.flatMap { it.batches }
    return first.copy(
        batches = batches,
        batchCount = maxOf(first.batchCount, batches.size),
        totalRequested = batches.sumOf { it.resolvedRequestedQty },
        totalPicked = batches.sumOf { it.resolvedPickedQty },
        exceptionTypes = groups.flatMap { it.resolvedExceptionTypes }.distinct(),
        exceptionNotes = groups.firstNotNullOfOrNull { it.resolvedExceptionNotes },
    )
}

private fun normalizeFefoProductGroup(group: FefoProductGroup): FefoProductGroup {
    val batches = group.batches
    return group.copy(
        batches = batches,
        batchCount = maxOf(group.batchCount, batches.size),
        totalRequested = batches.sumOf { it.resolvedRequestedQty }.takeIf { it > 0 } ?: group.totalRequested,
        totalPicked = batches.sumOf { it.resolvedPickedQty }.takeIf { it > 0 } ?: group.totalPicked,
    )
}

@Serializable
data class FefoConfirmTaskRequest(
    val pickerId: Int,
    val pickedQty: Int,
    val toteId: Int,
    val scannedGtin: String? = null,
    val scannedBatch: String? = null,
    val actualBin: String? = null,
)

@Serializable
data class PickExceptionRequest(
    val companyId: Int,
    val pickerId: Int,
    val exceptionTypes: List<String>,
    val qtyFound: Int,
    val notes: String? = null,
)

@Serializable
data class ToteAssignRequest(
    val toteNumber: String,
    val pickerId: Int? = null,
    val markPreviousFilled: Boolean? = null,
)

@Serializable
data class StartPickListRequest(
    val toteNumber: String? = null,
)

@Serializable
data class WmsIndustryPickListActionResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val status: String? = null,
    val pickListId: String? = null,
    val toteId: Int? = null,
    val activeToteId: Int? = null,
) {
    val resolvedToteId: Int?
        get() = listOfNotNull(toteId, activeToteId).firstOrNull { it > 0 }
}

@Serializable
data class ToteCatalogResponse(
    val pickingListId: Int = 0,
    val activeToteNumber: String = "",
    val totes: List<ToteCatalogItem> = emptyList(),
)

@Serializable
data class ToteCatalogItem(
    val toteId: Int? = null,
    val id: Int? = null,
    val toteNumber: String = "",
    val status: String = "",
    val sequence: Int = 0,
    val filledAt: String? = null,
    val scannedAt: String? = null,
    val scannedBy: Int? = null,
    val batchCount: Int = 0,
    val itemCount: Int = 0,
    val isActive: Boolean = false,
) {
    val resolvedToteId: Int?
        get() = listOfNotNull(toteId, id).firstOrNull { it > 0 }

    val isFilledOrStaged: Boolean
        get() = status.trim().lowercase() in setOf("filled", "staged")

    val isToteActive: Boolean
        get() = isActive || status.trim().lowercase() == "active"
}

@Serializable
data class WmsPatchPickListLineUpdate(
    val pickLineId: String,
    val pickedQty: Int? = null,
    val sourceLocationId: String? = null,
    val status: String? = null,
)

@Serializable
data class WmsPatchPickListLinesRequest(
    val companyId: Int,
    val lines: List<WmsPatchPickListLineUpdate>,
)

@Serializable
data class WmsStagePickListRequest(
    val companyId: Int,
    val stagingLocationId: String? = null,
    val stagingLocationName: String? = null,
)

@Serializable
data class WmsIndustryStagePickListRequest(
    val companyId: Int,
    val stagingLocationName: String,
)

@Serializable
data class WmsAssignPackerRequest(
    val packerId: Int,
    val companyId: Int,
)

@Serializable
data class WmsCreatePackingBoxRequest(
    val companyId: Int,
    val packingOrderId: String,
    val packLabel: String,
    val packType: String,
    val hierarchyLevel: Int,
    val gs1CompanyPrefix: String = "0614141",
    // iOS default used by WMSApi.createPackingBox
    val capacityUnits: Int = 25,
    val createdBy: Int,
    val generateBarcodeImage: Boolean = true,
)

@Serializable
data class WmsAddPackingBoxItemRequest(
    val companyId: Int,
    val productId: Int,
    val quantity: Int,
    val packedBy: Int,
    val pickLineId: String,
)

@Serializable
data class WmsUpdatePackingBoxStatusRequest(
    val companyId: Int,
    val status: String,
    val packedBy: Int? = null,
)

@Serializable
data class WmsUpdatePackingPickListStatusRequest(
    val companyId: Int,
    val status: String,
    val packedBy: Int? = null,
)

@Serializable
data class WmsPackingReceiverVerifyLineUpdate(
    val pickLineId: String,
    val receivedQty: Int,
)

@Serializable
data class WmsPackingReceiverVerifyLinesRequest(
    val companyId: Int,
    val receiverId: Int,
    val lines: List<WmsPackingReceiverVerifyLineUpdate>,
)

@Serializable
data class WmsPackingReceiverCompleteRequest(
    val companyId: Int,
    val receiverId: Int,
    val notes: String? = null,
    val requireLineQtyMatch: Boolean = true,
    val requireBoxScan: Boolean = false,
)

@Serializable
data class WmsUser(
    val id: Int = 0,
    val firstName: String? = null,
    val lastName: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val workStatus: String? = null,
    val isActive: Boolean? = true,
) {
    val name: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: listOfNotNull(firstName, lastName).joinToString(" ").takeIf { it.isNotBlank() }
            ?: email ?: "User"
}

// ── JSON helpers ──────────────────────────────────────────────────────────────

fun formatPickListDisplayId(
    pickListId: String?,
    pickListNumber: String? = null,
    pickListCode: String? = null,
): String {
    val fromNumber = pickListNumber?.trim().orEmpty()
    if (fromNumber.isNotEmpty()) {
        val match = Regex("(?i)PICK-?(\\d+)").find(fromNumber)
        if (match != null) {
            val digits = match.groupValues[1].toIntOrNull()
            if (digits != null) return "PICK-000$digits"
        }
    }
    val numeric = pickListId?.trim()?.toIntOrNull()
        ?: pickListId?.filter { it.isDigit() }?.toIntOrNull()
    if (numeric != null && numeric > 0) {
        return "PICK-000$numeric"
    }
    return fromNumber.ifBlank { pickListCode?.trim().orEmpty() }.ifBlank { "PICK-00000" }
}

fun WmsPackingPickListItem.buildPickListHierarchyNode(boxes: List<WmsPackingBoxSummary>): WmsPackingReceiverNode {
    val lineChildren = packLines.map { line ->
        WmsPackingReceiverNode(
            nodeType = "LINE",
            pickLineId = line.apiPickLineId,
            productId = line.productId,
            productName = line.productName ?: line.productSku,
            productSku = line.productSku,
            requestedQty = line.requiredCount,
            pickedQty = line.pickedCount,
            packedQty = line.packedCount,
            packingStatus = line.linePackingStatusLabel(line.packedCount),
        )
    }

    val filtered = boxes.filter { box ->
        box.resolvedPackId.isNotBlank() && !box.isPackedListSummary
    }
    val topLevel = filtered.filter { box ->
        box.resolvedParentPackId.isNullOrBlank()
    }

    fun buildBoxNode(box: WmsPackingBoxSummary): WmsPackingReceiverNode {
        val children = mutableListOf<WmsPackingReceiverNode>()
        val attachedChildIds = mutableSetOf<String>()

        if (box.isTertiaryPackage) {
            filtered.filter { it.resolvedParentPackId == box.resolvedPackId }
                .forEach { child ->
                    attachedChildIds += child.resolvedPackId
                    children += buildBoxNode(child)
                }
        }

        box.items.orEmpty().forEach { item ->
            when {
                item.childBox != null -> {
                    val summary = item.childBox.toBoxSummary()
                    val childId = summary.resolvedPackId
                    if (box.isTertiaryPackage && childId.isNotBlank() && childId !in attachedChildIds) {
                        attachedChildIds += childId
                        children += buildBoxNode(summary)
                    } else if (!box.isTertiaryPackage) {
                        children += item.toHierarchyProductItemNode()
                    }
                }
                item.isChildPackReference -> {
                    val childId = item.childPackId?.trim().orEmpty()
                    val matched = filtered.firstOrNull { it.resolvedPackId == childId }
                    if (box.isTertiaryPackage && matched != null && childId !in attachedChildIds) {
                        attachedChildIds += childId
                        children += buildBoxNode(matched)
                    } else if (box.isTertiaryPackage) {
                        children += item.toHierarchyCartonReferenceNode()
                    } else {
                        children += item.toHierarchyProductItemNode()
                    }
                }
                else -> children += item.toHierarchyProductItemNode()
            }
        }

        return WmsPackingReceiverNode(
            nodeType = "BOX",
            status = box.status ?: box.packageStatus,
            totalPackedQty = box.resolvedTotalPackedQty,
            packId = box.packId,
            packLabel = box.packLabel,
            packType = box.resolvedPackType,
            sscc = box.resolvedSscc.takeIf { it.isNotBlank() },
            hierarchyLevel = box.hierarchyLevel,
            children = children.takeIf { it.isNotEmpty() },
        )
    }

    val boxChildren = topLevel.map { buildBoxNode(it) }
    val packageCount = boxChildren.size.takeIf { it > 0 } ?: (boxCount ?: 0)
    return WmsPackingReceiverNode(
        nodeType = "PICK_LIST",
        pickListId = pickListId,
        pickListNumber = cardTitle,
        status = cardStatusLabel,
        packingOrderId = packingOrderId,
        lineCount = lineCount ?: packLines.size,
        totalPickedQty = totalPickedQty ?: resolvedTotalRequestedQty,
        totalPackedQty = resolvedTotalPackedQty,
        boxCount = packageCount,
        children = lineChildren + boxChildren,
    )
}

fun WmsPackingPickListItem.wrapPickListHierarchyOrder(pickListNode: WmsPackingReceiverNode): WmsPackingReceiverNode =
    WmsPackingReceiverNode(
        nodeType = "ORDER",
        orderId = orderId,
        orderNumber = orderNumber,
        orderStatus = cardStatusLabel,
        packedPickListCount = 1,
        completedBoxCount = completedBoxCount,
        totalBoxCount = boxCount ?: pickListNode.boxCount,
        children = listOf(pickListNode),
    )

private fun WmsPackingBoxItem.toHierarchyProductItemNode(): WmsPackingReceiverNode =
    WmsPackingReceiverNode(
        nodeType = "PACK_ITEM",
        productName = productName ?: resolvedProductSku ?: resolvedBatch ?: "Item",
        productSku = resolvedProductSku,
        quantity = quantity,
        batch = resolvedBatch,
    )

private fun WmsPackingBoxItem.toHierarchyCartonReferenceNode(): WmsPackingReceiverNode =
    WmsPackingReceiverNode(
        nodeType = "PACK_ITEM",
        status = childPackStatus,
        productName = childPackLabel ?: childPackSscc ?: "Carton",
        packType = childPackType ?: "CARTON",
        sscc = childPackSscc,
        quantity = quantity,
    )

private fun WmsPackingBoxItem.toHierarchyPackItemNode(): WmsPackingReceiverNode {
    childBox?.let { nested ->
        val summary = nested.toBoxSummary()
        return WmsPackingReceiverNode(
            nodeType = "PACK_ITEM",
            status = summary.status ?: summary.packageStatus,
            productName = summary.displayTitle,
            packType = summary.resolvedPackType,
            sscc = summary.resolvedSscc.takeIf { it.isNotBlank() },
            quantity = quantity ?: summary.resolvedTotalPackedQty,
        )
    }
    if (isChildPackReference) {
        return toHierarchyCartonReferenceNode()
    }
    return toHierarchyProductItemNode()
}

private fun WmsPackingBoxNested.toBoxSummary(): WmsPackingBoxSummary =
    WmsPackingBoxSummary(
        packId = packId,
        packLabel = packLabel,
        packType = packType ?: "CARTON",
        hierarchyLevel = hierarchyLevel ?: 2,
        status = status,
        sscc = sscc,
        itemCount = itemCount ?: items?.size,
        totalPackedQty = totalPackedQty,
        items = items,
    )

internal fun decodePickListsPayload(raw: String): WmsPickListsPayload {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        val lists = element.mapNotNull { decodePickListItem(it) }
        return WmsPickListsPayload(lists, lists.size, null)
    }
    val obj = element.jsonObject
    val listsArray = obj.pickArray("lists", "pickLists", "data")
    val lists = listsArray.mapNotNull { decodePickListItem(it) }
    val counts = obj["statusCounts"]?.let {
        runCatching { wmsJson.decodeFromJsonElement(WmsPickListStatusCounts.serializer(), it) }.getOrNull()
    }
    return WmsPickListsPayload(
        pickLists = lists,
        count = obj["count"]?.jsonPrimitive?.intOrNull ?: lists.size,
        statusCounts = counts,
    )
}

internal fun decodePackingByPackerPayload(raw: String): WmsPackingPickListsByPackerPayload {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        val lists = element.mapNotNull { decodePackingPickListItem(it) }
        return WmsPackingPickListsByPackerPayload(null, lists.size, lists)
    }
    val obj = element.jsonObject
    val listsArray = obj.pickArray("lists", "pickLists", "data")
    val lists = listsArray.mapNotNull { decodePackingPickListItem(it) }
    return WmsPackingPickListsByPackerPayload(
        packerId = obj["packerId"]?.jsonPrimitive?.intOrNull,
        count = obj["count"]?.jsonPrimitive?.intOrNull ?: lists.size,
        lists = lists,
    )
}

internal fun decodeReceiverOrders(raw: String): List<WmsPackingReceiverNode> {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        return element.mapNotNull { decodeReceiverNode(it) }
    }
    val obj = element.jsonObject
    val array = obj.pickArray("orders", "data", "nodes")
    return array.mapNotNull { decodeReceiverNode(it) }
}

internal fun decodeReceiverLines(raw: String): List<WmsPackingReceiverLine> {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        return element.mapNotNull {
            runCatching { wmsJson.decodeFromJsonElement(WmsPackingReceiverLine.serializer(), it) }.getOrNull()
        }
    }
    val obj = element.jsonObject
    return obj.pickArray("lines", "data").mapNotNull {
        runCatching { wmsJson.decodeFromJsonElement(WmsPackingReceiverLine.serializer(), it) }.getOrNull()
    }
}

internal fun decodeReceiverReceivingStatus(raw: String): WmsPackingReceiverReceivingStatus {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonObject) {
        val nested = element["status"] ?: element["data"]
        if (nested != null) {
            runCatching {
                return wmsJson.decodeFromJsonElement(WmsPackingReceiverReceivingStatus.serializer(), nested)
            }
        }
        runCatching {
            return wmsJson.decodeFromJsonElement(WmsPackingReceiverReceivingStatus.serializer(), element)
        }
    }
    return wmsJson.decodeFromString(WmsPackingReceiverReceivingStatus.serializer(), raw)
}

internal fun decodePackingBoxes(raw: String): List<WmsPackingBoxSummary> {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        return element.mapNotNull { decodePackingBoxSummary(it) }
    }
    val obj = element.jsonObject
    return obj.pickArray("boxes", "data", "lists").mapNotNull { decodePackingBoxSummary(it) }
}

/** Matches iOS `WMSPackingBoxSummary.init(json:)` — resolves `packId` from aliases and nested `box`. */
internal fun decodePackingBoxSummary(element: JsonElement): WmsPackingBoxSummary? {
    val obj = element as? JsonObject ?: return null
    val nested = obj["box"]?.jsonObject
    val base = runCatching { wmsJson.decodeFromJsonElement(WmsPackingBoxSummary.serializer(), element) }.getOrNull()
        ?: nested?.let {
            runCatching { wmsJson.decodeFromJsonElement(WmsPackingBoxSummary.serializer(), it) }.getOrNull()
        }
        ?: WmsPackingBoxSummary()

    val resolvedPackId = base.packId?.trim()?.takeIf { it.isNotBlank() }
        ?: obj.optionalString("packId", "pack_id")
        ?: nested?.optionalString("packId", "pack_id")
        ?: obj.optionalString("boxId", "box_id")
        ?: nested?.optionalString("boxId", "box_id")
        ?: obj.optionalInt("id")?.toString()
        ?: nested?.optionalInt("id")?.toString()

    val packLabel = base.packLabel?.trim()?.takeIf { it.isNotBlank() }
        ?: obj.optionalString("packLabel", "pack_label")
        ?: nested?.optionalString("packLabel", "pack_label")
    val packType = base.packType?.trim()?.takeIf { it.isNotBlank() }
        ?: obj.optionalString("packType", "pack_type")
        ?: nested?.optionalString("packType", "pack_type")
    val hierarchyLevel = base.hierarchyLevel
        ?: obj.optionalInt("hierarchyLevel", "hierarchy_level")
        ?: nested?.optionalInt("hierarchyLevel", "hierarchy_level")
    val parentPackId = base.parentPackId?.trim()?.takeIf { it.isNotBlank() }
        ?: obj.optionalString("parentPackId", "parent_pack_id", "parentBoxId", "parent_box_id")
        ?: nested?.optionalString("parentPackId", "parent_pack_id", "parentBoxId", "parent_box_id")
        ?: obj.optionalInt("parentBoxId", "parent_box_id")?.toString()
        ?: nested?.optionalInt("parentBoxId", "parent_box_id")?.toString()
    val sscc = base.sscc?.trim()?.takeIf { it.isNotBlank() }
        ?: obj.optionalString("sscc")
        ?: nested?.optionalString("sscc")
    val barcodeType = base.barcodeType?.trim()?.takeIf { it.isNotBlank() }
        ?: obj.optionalString("barcodeType", "barcode_type")
        ?: nested?.optionalString("barcodeType", "barcode_type")
    val barcodeData = base.barcodeData?.trim()?.takeIf { it.isNotBlank() }
        ?: obj.optionalString("barcodeData", "barcode_data", "barcode")
        ?: nested?.optionalString("barcodeData", "barcode_data", "barcode", "boxBarcode")
    val barcodeImageUrl = base.barcodeImageUrl?.trim()?.takeIf { it.isNotBlank() }
        ?: obj.optionalString("barcodeImageUrl", "barcode_image_url")
        ?: nested?.optionalString("barcodeImageUrl", "barcode_image_url")
    val status = base.status?.trim()?.takeIf { it.isNotBlank() }
        ?: obj.optionalString("status")
        ?: nested?.optionalString("status")
    val packageStatus = base.packageStatus?.trim()?.takeIf { it.isNotBlank() }
        ?: obj.optionalString("packageStatus", "package_status")
        ?: nested?.optionalString("packageStatus", "package_status")
    val itemCount = base.itemCount
        ?: obj.optionalInt("itemCount", "item_count")
        ?: nested?.optionalInt("itemCount", "item_count")
    val totalPackedQty = base.totalPackedQty
        ?: obj.optionalInt("totalPackedQty", "total_packed_qty")
        ?: nested?.optionalInt("totalPackedQty", "total_packed_qty")
    val items = base.items?.takeIf { it.isNotEmpty() }
        ?: obj.pickArray("items").mapNotNull {
            runCatching { wmsJson.decodeFromJsonElement(WmsPackingBoxItem.serializer(), it) }.getOrNull()
        }.takeIf { it.isNotEmpty() }
        ?: nested?.pickArray("items")?.mapNotNull {
            runCatching { wmsJson.decodeFromJsonElement(WmsPackingBoxItem.serializer(), it) }.getOrNull()
        }?.takeIf { it.isNotEmpty() }

    return base.copy(
        packId = resolvedPackId,
        packLabel = packLabel,
        packType = packType,
        hierarchyLevel = hierarchyLevel,
        parentPackId = parentPackId,
        sscc = sscc,
        barcodeType = barcodeType,
        barcodeData = barcodeData,
        barcodeImageUrl = barcodeImageUrl,
        status = status,
        packageStatus = packageStatus,
        itemCount = itemCount,
        totalPackedQty = totalPackedQty,
        items = items ?: base.items,
    )
}

/** Matches iOS `decodePackingBoxResponse(from:)` envelope + nested `box` handling. */
internal fun decodePackingBox(raw: String): WmsCreatedPackingBox {
    val element = wmsJson.parseToJsonElement(raw)
    decodePackingBoxElement(element)?.let { return it }
    val obj = element as? JsonObject
    if (obj != null) {
        val decoded = runCatching {
            wmsJson.decodeFromJsonElement(WmsCreatedPackingBox.serializer(), element)
        }.getOrNull()
        if (decoded != null) return normalizeCreatedPackingBox(obj, decoded)
    }
    return wmsJson.decodeFromString(WmsCreatedPackingBox.serializer(), raw)
}

private fun resolvePackIdFromJson(obj: JsonObject): String? {
    obj.optionalString("packId", "pack_id", "boxId", "box_id", "id")?.let { return it }
    obj.optionalInt("id", "boxId", "box_id", "packId", "pack_id")?.let { return it.toString() }
    return null
}

private fun normalizeCreatedPackingBox(
    obj: JsonObject,
    decoded: WmsCreatedPackingBox,
): WmsCreatedPackingBox {
    val nestedElement = obj["box"]?.jsonObject
    val nestedDecoded = nestedElement?.let {
        runCatching { wmsJson.decodeFromJsonElement(WmsPackingBoxNested.serializer(), it) }.getOrNull()
    }
    val nestedPackId = nestedElement?.let { resolvePackIdFromJson(it) } ?: nestedDecoded?.packId
    val nested = nestedDecoded?.copy(packId = nestedPackId ?: nestedDecoded.packId)

    val resolvedPackId = resolvePackIdFromJson(obj)?.takeIf { it.isNotBlank() }
        ?: decoded.packId?.trim()?.takeIf { it.isNotBlank() }
        ?: nestedPackId?.trim()?.takeIf { it.isNotBlank() }

    return decoded.copy(
        packId = resolvedPackId,
        packLabel = decoded.packLabel?.takeIf { it.isNotBlank() } ?: nested?.packLabel,
        packType = decoded.packType?.takeIf { it.isNotBlank() } ?: nested?.packType,
        hierarchyLevel = decoded.hierarchyLevel ?: nested?.hierarchyLevel,
        sscc = decoded.sscc?.takeIf { it.isNotBlank() } ?: nested?.sscc,
        barcodeType = decoded.barcodeType?.takeIf { it.isNotBlank() } ?: nested?.barcodeType,
        barcodeData = decoded.barcodeData?.takeIf { it.isNotBlank() } ?: nested?.barcodeData,
        barcodeImageUrl = decoded.barcodeImageUrl?.takeIf { it.isNotBlank() } ?: nested?.barcodeImageUrl,
        itemCount = decoded.itemCount ?: nested?.itemCount,
        totalPackedQty = decoded.totalPackedQty ?: nested?.totalPackedQty,
        status = decoded.status?.takeIf { it.isNotBlank() } ?: nested?.status,
        items = decoded.items?.takeIf { it.isNotEmpty() } ?: nested?.items,
        box = nested ?: decoded.box,
    )
}

private fun decodePackingBoxElement(element: JsonElement): WmsCreatedPackingBox? {
    if (element is JsonObject) {
        element["data"]?.let { decodePackingBoxElement(it) }?.let { return it }

        val decoded = runCatching {
            wmsJson.decodeFromJsonElement(WmsCreatedPackingBox.serializer(), element)
        }.getOrNull() ?: return null

        return normalizeCreatedPackingBox(element, decoded)
    }

    return runCatching {
        wmsJson.decodeFromJsonElement(WmsCreatedPackingBox.serializer(), element)
    }.getOrNull()
}

internal fun decodeUsers(raw: String): List<WmsUser> {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        return element.mapNotNull {
            runCatching { wmsJson.decodeFromJsonElement(WmsUser.serializer(), it) }.getOrNull()
        }
    }
    val obj = element.jsonObject
    return obj.pickArray("users", "data").mapNotNull {
        runCatching { wmsJson.decodeFromJsonElement(WmsUser.serializer(), it) }.getOrNull()
    }
}

private fun JsonObject.pickArray(vararg keys: String): JsonArray {
    for (key in keys) {
        val arr = this[key] as? JsonArray
        if (arr != null) return arr
    }
    return JsonArray(emptyList())
}

private fun JsonObject.optionalInt(vararg keys: String): Int? {
    for (key in keys) {
        val prim = this[key]?.jsonPrimitive ?: continue
        prim.intOrNull?.let { return it }
        prim.contentOrNull?.trim()?.toIntOrNull()?.let { return it }
        prim.contentOrNull?.trim()?.toDoubleOrNull()?.let { return it.roundToInt() }
    }
    return null
}

private fun JsonObject.optionalString(vararg keys: String): String? {
    for (key in keys) {
        val el = this[key] ?: continue
        if (el is JsonNull) continue
        val prim = el.jsonPrimitive
        val value = prim.contentOrNull?.trim().orEmpty()
        if (value.isNotEmpty()) return value
    }
    return null
}

private fun JsonObject.optionalQtyString(vararg keys: String): String? {
    for (key in keys) {
        val el = this[key] ?: continue
        if (el is JsonNull) continue
        val prim = el.jsonPrimitive
        prim.intOrNull?.let { return it.toString() }
        prim.longOrNull?.let { return it.toString() }
        prim.doubleOrNull?.let { return it.roundToInt().toString() }
        prim.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return null
}

private fun parseQty(raw: String?): Int? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    trimmed.toIntOrNull()?.let { return it }
    trimmed.toDoubleOrNull()?.let { return it.roundToInt() }
    return null
}

private fun decodeOrderProduct(obj: JsonObject): WmsOrderProduct {
    val batches = (obj["batches"] as? JsonArray)?.mapNotNull { batchEl ->
        val batchObj = batchEl as? JsonObject ?: return@mapNotNull null
        WmsProductBatchRow(
            gtin = batchObj.optionalString("gtin"),
            batchNumber = batchObj.optionalString("batchNumber", "batch_number"),
            batchNumberSnake = batchObj.optionalString("batch_number"),
        )
    }
    return WmsOrderProduct(
        productName = obj.optionalString("productName", "product_name", "name"),
        productNameSnake = obj.optionalString("product_name"),
        productShortName = obj.optionalString("productShortName", "product_short_name", "shortName"),
        productShortNameSnake = obj.optionalString("product_short_name"),
        productSku = obj.optionalString("productSku", "product_sku"),
        productSkuSnake = obj.optionalString("product_sku"),
        sku = obj.optionalString("sku"),
        gtin = obj.optionalString("gtin"),
        identifier = obj.optionalString("identifier"),
        batchNumber = obj.optionalString("batchNumber", "batch_number"),
        batchNumberSnake = obj.optionalString("batch_number"),
        batches = batches,
    )
}

private fun decodePickListLine(element: JsonElement): WmsPickListLine? {
    val obj = element as? JsonObject ?: return null
    val location = obj.optionalNestedObject("location", "sourceLocation")
    val productObj = obj.optionalNestedObject("product")
    val orderObj = obj.optionalNestedObject("order")
    val orderLineObj = obj.optionalNestedObject("orderLine", "order_line")
    val product = productObj?.let(::decodeOrderProduct)

    val productName = obj.optionalString("productName", "product_name", "name")
        ?: product?.resolvedName?.takeIf { it.isNotBlank() }
    val productSku = obj.optionalString("productSku", "product_sku", "sku")
        ?: product?.resolvedSku?.takeIf { it.isNotBlank() }

    val lineId = obj.optionalString(
        "pickLineId",
        "pick_line_id",
        "id",
        "taskId",
        "orderLineId",
        "order_line_id",
    ) ?: return null

    return WmsPickListLine(
        pickLineId = lineId,
        taskId = obj.optionalString("taskId", "task_id"),
        id = obj.optionalString("id", "taskId"),
        orderNumber = obj.optionalString("orderNumber", "order_number", "sourceOrderRef")
            ?: orderObj?.optionalString("orderNumber", "order_number"),
        lineNumber = obj.optionalInt("lineNumber", "line_number", "sequence", "sequenceNumber")
            ?: orderLineObj?.optionalInt("lineNumber", "line_number"),
        productId = obj.optionalString("productId", "product_id")
            ?: obj.optionalInt("productId", "product_id")?.toString(),
        productName = productName,
        productSku = productSku,
        requestedQty = obj.optionalQtyString(
            "requestedQty",
            "requested_qty",
            "quantity",
            "qty",
            "requiredQty",
            "required_qty",
        ),
        pickedQty = obj.optionalQtyString("pickedQty", "picked_qty"),
        quantity = obj.optionalInt("quantity", "qty"),
        packedQty = obj.optionalInt("packedQty", "packed_qty"),
        remainingQty = obj.optionalInt("remainingQty", "remaining_qty"),
        status = obj.optionalString("status"),
        locationCode = obj.optionalString("locationCode", "location_code")
            ?: location?.optionalString("locationCode", "location_code"),
        locationName = obj.optionalString("locationName", "location_name")
            ?: location?.optionalString("locationName", "location_name"),
        zone = obj.optionalString("zone") ?: location?.optionalString("zone"),
        bin = obj.optionalString("bin") ?: location?.optionalString("bin"),
        batch = obj.optionalString("batch"),
        batchNumber = obj.optionalString("batchNumber", "batch_number"),
        expiryDate = obj.optionalString("expiryDate", "expiry_date", "expirationDate", "expiration_date")
            ?: product?.let { null },
        pickInstruction = obj.optionalString("pickInstruction", "pick_instruction", "instruction"),
        sourceLocationId = obj.optionalString("sourceLocationId", "source_location_id")
            ?: location?.optionalString("locationId", "id"),
        exceptionTypes = obj.decodeStringList("exceptionTypes", "exception_types"),
        exceptionNotes = obj.optionalString("exceptionNotes", "exception_notes", "notes"),
        product = product,
        gtinFromTask = obj.optionalString("gtin", "scannedGtin", "scanned_gtin"),
    )
}

private fun JsonObject.decodeStringList(vararg keys: String): List<String> {
    for (key in keys) {
        val array = this[key] as? JsonArray ?: continue
        return array.mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf { s -> s.isNotEmpty() } }
    }
    return emptyList()
}

fun formatPackingExceptionType(raw: String): String =
    when (raw.trim().uppercase()) {
        "SHORTAGE" -> "Shortage"
        "DAMAGED" -> "Damaged"
        "BIN_EMPTY" -> "Bin empty"
        "WRONG_BATCH" -> "Wrong batch"
        "NOT_FOUND" -> "Not found"
        "OTHER" -> "Other"
        else -> raw.trim().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

internal fun decodePickListLinesPayload(raw: String): WmsPickListLinesPayload {
    val element = wmsJson.parseToJsonElement(raw)
    if (element is JsonArray) {
        return WmsPickListLinesPayload(
            pickLines = element.mapNotNull(::decodePickListLine),
        )
    }
    val root = element.jsonObject
    for (key in listOf("data", "pickList", "list")) {
        root[key]?.jsonObject?.let { nested ->
            val payload = decodePickListLinesPayloadObject(nested, root)
            if (payload.pickLines.isNotEmpty()) return payload
        }
    }
    return decodePickListLinesPayloadObject(root, root)
}

private fun decodePickListLinesPayloadObject(obj: JsonObject, root: JsonObject): WmsPickListLinesPayload {
    val nestedHeader = obj.optionalNestedObject("pickList", "list")
    var linesArray = obj.pickArray("lines", "pickLines", "tasks", "pickTasks")
    if (linesArray.isEmpty()) {
        linesArray = nestedHeader?.pickArray("lines", "pickLines", "tasks", "pickTasks") ?: JsonArray(emptyList())
    }
    if (linesArray.isEmpty()) {
        linesArray = root.pickArray("lines", "pickLines", "tasks", "pickTasks")
    }
    val header = nestedHeader ?: obj
    val lines = linesArray.mapNotNull(::decodePickListLine)
    return WmsPickListLinesPayload(
        pickListId = header.optionalString("pickListId", "pick_list_id", "id")
            ?: obj.optionalString("pickListId", "pick_list_id", "id"),
        pickListNumber = header.optionalString("pickListNumber", "pick_list_number")
            ?: obj.optionalString("pickListNumber", "pick_list_number"),
        pickType = header.optionalString("pickType", "pick_type") ?: obj.optionalString("pickType", "pick_type"),
        status = header.optionalString("status") ?: obj.optionalString("status"),
        zone = header.optionalString("zone") ?: obj.optionalString("zone"),
        toteNumber = header.optionalString("toteNumber", "tote_number") ?: obj.optionalString("toteNumber", "tote_number"),
        assignedPicker = header.optionalInt("assignedPicker", "assigned_picker")
            ?: obj.optionalInt("assignedPicker", "assigned_picker"),
        orderId = header.optionalString("orderId", "order_id") ?: obj.optionalString("orderId", "order_id"),
        stagingLocationId = header.optionalString("stagingLocationId", "staging_location_id")
            ?: obj.optionalString("stagingLocationId", "staging_location_id"),
        defaultSourceLocationId = header.optionalString("defaultSourceLocationId", "default_source_location_id")
            ?: obj.optionalString("defaultSourceLocationId", "default_source_location_id"),
        lineCount = header.optionalInt("lineCount", "line_count")
            ?: obj.optionalInt("lineCount", "line_count"),
        totalRequestedQty = header.optionalInt("totalRequestedQty", "total_requested_qty")
            ?: obj.optionalInt("totalRequestedQty", "total_requested_qty"),
        totalPickedQty = header.optionalInt("totalPickedQty", "total_picked_qty")
            ?: obj.optionalInt("totalPickedQty", "total_picked_qty"),
        pctComplete = header.optionalInt("pctComplete", "pct_complete")
            ?: obj.optionalInt("pctComplete", "pct_complete"),
        pickLines = lines,
    )
}

private fun JsonObject.optionalNestedObject(vararg keys: String): JsonObject? {
    for (key in keys) {
        val obj = this[key]?.jsonObject
        if (obj != null) return obj
    }
    return null
}

private fun JsonObject.resolvePickListNumericId(fallback: Int? = null): Int? {
    fallback?.takeIf { it > 0 }?.let { return it }
    optionalInt("id", "listId", "list_id", "pickListId", "pick_list_id")?.takeIf { it > 0 }?.let { return it }
    for (key in listOf("id", "listId", "list_id", "pickListId", "pick_list_id")) {
        val raw = optionalString(key)?.trim().orEmpty()
        if (raw.isNotEmpty() && raw.all { it.isDigit() }) {
            raw.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        }
    }
    return null
}

private fun decodePickListItem(element: JsonElement): WmsPickListItem? {
    val obj = element.jsonObject
    val base = runCatching { wmsJson.decodeFromJsonElement(WmsPickListItem.serializer(), element) }.getOrNull()
        ?: return null
    val sidecar = obj.pickArray("lines", "pickLines", "tasks", "pickTasks")
    val manualLines = sidecar.mapNotNull(::decodePickListLine)
    val lines = manualLines.takeIf { it.isNotEmpty() } ?: base.resolvedLines
    val boxesArray = obj.pickArray("boxes")
    val boxes = if (boxesArray.isNotEmpty()) {
        boxesArray.mapNotNull { decodePackingBoxSummary(it) }
    } else {
        base.boxes
    }
    val fromTasks = obj["tasks"] != null || obj["pickTasks"] != null
    val merged = when {
        manualLines.isEmpty() && boxes == base.boxes -> base
        fromTasks -> base.copy(tasks = lines, boxes = boxes)
        else -> base.copy(lines = lines, boxes = boxes)
    }
    val numericId = obj.resolvePickListNumericId(merged.numericId)
    val withNumeric = if (numericId != null && numericId != merged.numericId) {
        merged.copy(numericId = numericId)
    } else {
        merged
    }
    return withNumeric.copy(
        displayPackingStatus = withNumeric.displayPackingStatus
            ?: obj.optionalString("displayPackingStatus", "display_packing_status"),
        openBoxCount = withNumeric.openBoxCount ?: obj.optionalInt("openBoxCount", "open_box_count"),
        canComplete = withNumeric.canComplete ?: obj["canComplete"]?.jsonPrimitive?.let {
            when (it.content) {
                "true" -> true
                "false" -> false
                else -> null
            }
        },
        boxCount = withNumeric.boxCount ?: obj.optionalInt("boxCount", "box_count"),
        completedBoxCount = withNumeric.completedBoxCount ?: obj.optionalInt("completedBoxCount", "completed_box_count", "sealedBoxCount", "sealed_box_count"),
    )
}

private fun decodePackingPickListItem(element: JsonElement): WmsPackingPickListItem? =
    runCatching { wmsJson.decodeFromJsonElement(WmsPackingPickListItem.serializer(), element) }.getOrNull()

private fun decodeReceiverNode(element: JsonElement): WmsPackingReceiverNode? =
    runCatching { wmsJson.decodeFromJsonElement(WmsPackingReceiverNode.serializer(), element) }.getOrNull()

fun packingBoxTypeConfig(uiLabel: String): Pair<String, Int> =
    // Must match iOS WMSApi.packingBoxTypeConfig(for:)
    // - Secondary/Carton/Case/Inner => (CARTON, level=2)
    // - Tertiary/Pallet/Master => (PALLET, level=3)
    when (uiLabel.trim().uppercase()) {
        "TERTIARY", "PALLET", "MASTER" -> "PALLET" to 3
        "SECONDARY", "CARTON", "CASE", "INNER" -> "CARTON" to 2
        else -> "CARTON" to 2
    }

internal fun decodeIndustryPickListActionResponse(raw: String): WmsIndustryPickListActionResponse {
    val element = wmsJson.parseToJsonElement(raw)
    val obj = when (element) {
        is JsonObject -> when {
            element["data"] is JsonObject -> element["data"]!!.jsonObject
            element["pickList"] is JsonObject -> element["pickList"]!!.jsonObject
            else -> element
        }
        else -> return WmsIndustryPickListActionResponse()
    }
    val decoded = runCatching {
        wmsJson.decodeFromJsonElement(WmsIndustryPickListActionResponse.serializer(), obj)
    }.getOrNull() ?: WmsIndustryPickListActionResponse()
    return decoded.copy(
        toteId = decoded.resolvedToteId ?: resolveIndustryToteIdFromJson(obj),
        activeToteId = decoded.activeToteId ?: resolveIndustryToteIdFromJson(obj),
    )
}

private fun resolveIndustryToteIdFromJson(obj: JsonObject): Int? {
    for (key in listOf("toteId", "activeToteId")) {
        obj[key]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }?.let { return it }
    }
    obj["tote"]?.jsonObject?.let { tote ->
        (tote["toteId"] ?: tote["id"])?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }?.let { return it }
    }
    obj["totes"]?.jsonArray?.forEach { entry ->
        val tote = entry.jsonObject
        val status = tote["status"]?.jsonPrimitive?.content.orEmpty().lowercase()
        if (status == "active") {
            (tote["toteId"] ?: tote["id"])?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }?.let { return it }
        }
    }
    return null
}

fun mapPickListToTask(item: WmsPickListItem): PickerMyListTask {
    val status = (item.status ?: "PENDING").uppercase()
    val summary = item.summary
    val lines = item.lineCount ?: summary?.totalTasks ?: item.resolvedLines.size
    val total = item.totalRequestedQty ?: summary?.batchCount ?: lines
    val picked = item.totalPickedQty ?: summary?.picked ?: 0
    val skuCount = summary?.skuCount ?: 0
    val batchCount = summary?.batchCount ?: 0
    val pickedSkuCount = summary?.pickedSkuCount ?: 0
    val locationParts = buildList {
        item.resolvedZoneCode?.takeIf { it.isNotBlank() }?.let { add(it) }
        item.stagingLocationName?.takeIf { it.isNotBlank() }?.let { add(it) }
    }
    return PickerMyListTask(
        id = item.id,
        pickListId = item.numericId?.toString() ?: item.pickListId ?: item.id,
        title = item.displayTitle,
        lineCount = lines,
        itemCount = total,
        pickedCount = picked,
        status = status,
        pickType = item.pickType,
        waveLabel = item.waveDisplayLabel,
        zone = item.resolvedZoneCode,
        toteNumber = item.toteNumber?.takeIf { it.isNotBlank() },
        locationLine = locationParts.joinToString(" • ").ifBlank { "Warehouse floor" },
        isLocked = status == "CREATED" && item.assignedPickerId == null && item.assignedPicker == null,
        lockReason = if (status == "CREATED") "Wait for assignment" else null,
        priority = when (item.priority?.uppercase()) {
            "URGENT", "HIGH" -> 1
            "NORMAL" -> 5
            "LOW" -> 9
            else -> 5
        },
        packingStatus = item.packingStatus,
        pickListCode = item.pickListCode,
        stagingLocationName = item.stagingLocationName,
        skuCount = skuCount,
        batchCount = batchCount,
        pickedSkuCount = pickedSkuCount,
        dispatchStatus = item.dispatchStatus,
        assignedPickerName = item.assignedPickerName?.takeIf { it.isNotBlank() }
            ?: item.packer?.displayName?.takeIf { it.isNotBlank() },
        resolvedLines = item.resolvedLines,
        completedAt = item.completedAt?.takeIf { it.isNotBlank() }
            ?: item.completedAtSnake?.takeIf { it.isNotBlank() },
        stagedAt = item.stagedAt?.takeIf { it.isNotBlank() },
        updatedAt = item.updatedAt?.takeIf { it.isNotBlank() },
    )
}

// ── Warehouse locations (staging picker) ─────────────────────────────────────

data class WmsWarehouseLocation(
    val locationId: String,
    val locationCode: String? = null,
    val locationName: String? = null,
    val locationType: String? = null,
) {
    val displayLabel: String
        get() {
            val name = locationName?.trim().orEmpty()
            val code = locationCode?.trim().orEmpty()
            return when {
                name.isNotEmpty() && code.isNotEmpty() -> "$name · $code"
                name.isNotEmpty() -> name
                code.isNotEmpty() -> code
                else -> locationId
            }
        }
}

fun List<WmsWarehouseLocation>.stagingLocationCandidates(): List<WmsWarehouseLocation> {
    val stagingType = filter { (it.locationType ?: "").uppercase().contains("STAG") }
    val source = stagingType.ifEmpty { this }
    return source
        .filter { it.locationId.trim().isNotEmpty() }
        .distinctBy { it.locationId.trim() }
        .sortedBy { it.displayLabel.uppercase() }
}

internal fun decodeStagingLocations(raw: String): List<WmsWarehouseLocation> {
    val root = runCatching { wmsJson.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return emptyList()
    val payload = root["data"]?.jsonObject ?: root
    val locationsArray = payload["locations"] as? JsonArray ?: return emptyList()
    return locationsArray.mapNotNull { element ->
        val obj = element as? JsonObject ?: return@mapNotNull null
        val locationId = obj.optionalString("locationId", "location_id", "id") ?: return@mapNotNull null
        WmsWarehouseLocation(
            locationId = locationId,
            locationCode = obj.optionalString("locationCode", "location_code", "code"),
            locationName = obj.optionalString("locationName", "location_name", "name"),
            locationType = obj.optionalString("locationType", "location_type", "type"),
        )
    }
}
