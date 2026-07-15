package features.app.warehouse.wms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import core.network.epcis.EpcisFlowService
import core.network.repository.WmsRepository
import core.network.wms.WmsAddPackingBoxItemRequest
import core.network.wms.WmsCreatePackingBoxRequest
import core.network.wms.WmsCreatedPackingBox
import core.network.wms.WmsPackingBoxSummary
import core.network.wms.WmsPackingPickListItem
import core.network.wms.WmsPackingPickListLine
import core.network.wms.enrichedFrom
import core.network.wms.mergeSessionState
import core.network.wms.industryOrphanCartonsOnPallet
import core.network.wms.industryBoxIdsMatch
import core.network.wms.industrySealedCartonsAvailableForPallet
import core.network.wms.industrySealedCartonsLinkedToPallet
import core.network.wms.packingBoxTypeConfig
import core.network.wms.toCreatedPackingBox
import core.network.wms.withItemMerged
import core.network.wms.withKnownPackId
import features.app.warehouse.validateWarehouseScan
import core.storage.SessionManager
import core.storage.getLocalStorage
import features.app.downloadImage
import features.app.shareImage
import kotlinx.coroutines.launch

@Composable
fun PackingBoxSessionScreen(
    list: WmsPackingPickListItem,
    box: WmsCreatedPackingBox,
    packTypeLabel: String,
    triggerNewPackageOnOpen: Boolean = false,
    onBack: () -> Unit,
    onAddQty: (WmsPackingPickListLine, WmsCreatedPackingBox) -> Unit,
    onCompletePackage: () -> Unit,
    onActiveBoxChanged: (WmsCreatedPackingBox) -> Unit = {},
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }
    val packerId = remember { WmsSession.userId(session) }
    var sessionUiPackType by remember(packTypeLabel) { mutableStateOf(packTypeLabel) }
    val isTertiaryPack = packingBoxTypeConfig(sessionUiPackType).second == 3

    var currentList by remember(list.id) { mutableStateOf(list) }
    var currentBox by remember(list.id, box.resolvedPackId) { mutableStateOf(box) }
    var completing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSealBoxFirstAlert by remember { mutableStateOf(false) }
    var sealBoxErrorMessage by remember { mutableStateOf<String?>(null) }
    var listBoxes by remember { mutableStateOf<List<WmsPackingBoxSummary>>(emptyList()) }
    var loadingListBoxes by remember { mutableStateOf(false) }
    var loadingSession by remember { mutableStateOf(box.resolvedItems.isEmpty()) }
    var palletCartons by remember { mutableStateOf<List<WmsPackingBoxSummary>>(emptyList()) }
    var sealedCartonsAvailable by remember { mutableStateOf<List<WmsPackingBoxSummary>>(emptyList()) }
    var orphanCartonsOnPallet by remember { mutableStateOf<List<WmsPackingBoxSummary>>(emptyList()) }
    var linkingCartonId by remember { mutableStateOf<String?>(null) }
    var isCreatingPallet by remember { mutableStateOf(false) }
    var isCreatingNewBox by remember { mutableStateOf(false) }
    var isSealingPallet by remember { mutableStateOf(false) }
    var sealingBoxId by remember { mutableStateOf<String?>(null) }
    var boxActionTarget by remember { mutableStateOf<WmsPackingBoxSummary?>(null) }
    var showFinishPackingSheet by remember { mutableStateOf(false) }
    var showSealPalletSheet by remember { mutableStateOf(false) }
    var finishSheetDismissed by remember { mutableStateOf(false) }
    var boxStateById by remember { mutableStateOf(mapOf<String, WmsCreatedPackingBox>()) }
    var sessionPackedByLine by remember(list.id) { mutableStateOf(mapOf<String, Int>()) }
    var actionMessage by remember { mutableStateOf<String?>(null) }

    fun persistActiveBoxState() {
        val id = currentBox.resolvedPackId
        if (id.isNotEmpty()) {
            boxStateById = boxStateById + (id to currentBox)
        }
    }

    suspend fun reloadListBoxes() {
        val pickListId = list.pickListId?.trim().orEmpty()
        if (pickListId.isNotEmpty()) {
            listBoxes = repo.fetchIndustryPackingBoxes(pickListId)
        }
    }

    suspend fun createNewBoxInSessionSuspend() {
        if (isTertiaryPack) {
            error = "The pallet is already created. Add sealed cartons to link them to this pallet."
            return
        }
        persistActiveBoxState()

        val pickListId = list.pickListId?.trim().orEmpty()
        if (pickListId.isEmpty()) {
            error = "Pick list id is missing."
            return
        }
        if (packerId <= 0) {
            error = "Packer is not configured."
            return
        }

        val (packType, hierarchyLevel) = packingBoxTypeConfig(sessionUiPackType)
        val created = runCatching {
            repo.createIndustryPackingBox(
                pickListId = pickListId,
                packerId = packerId,
                boxType = packType.lowercase(),
            )
        }.getOrElse { industryError ->
            val packingOrderId = list.packingOrderId?.trim().orEmpty()
            if (packingOrderId.isEmpty() || companyId <= 0) {
                throw industryError
            }
            val suffix = ((System.currentTimeMillis() / 1000) % 1000).toInt()
                .toString()
                .padStart(3, '0')
            repo.createPackingBox(
                WmsCreatePackingBoxRequest(
                    companyId = companyId,
                    packingOrderId = packingOrderId,
                    packLabel = "BOX-$suffix",
                    packType = packType,
                    hierarchyLevel = hierarchyLevel,
                    createdBy = packerId,
                ),
            )
        }

        var createdBox = created
        if (createdBox.resolvedPackId.isBlank()) {
            repo.fetchIndustryPackingBoxes(pickListId)
                .filter { !it.isCompleted && it.resolvedParentPackId.isNullOrBlank() }
                .lastOrNull()
                ?.toCreatedPackingBox()
                ?.takeIf { it.resolvedPackId.isNotBlank() }
                ?.let { recovered ->
                    createdBox = createdBox.mergeSessionState(recovered)
                }
        }

        currentBox = createdBox
        boxStateById = boxStateById + (createdBox.resolvedPackId to createdBox)
        actionMessage = "Created ${createdBox.resolvedPackLabel}."
        onActiveBoxChanged(createdBox)
        reloadListBoxes()
    }

    fun createNewBoxInSession() {
        if (isCreatingNewBox || completing) return
        scope.launch {
            isCreatingNewBox = true
            error = null
            actionMessage = null
            runCatching {
                createNewBoxInSessionSuspend()
            }.onFailure { err ->
                error = err.message ?: err.toString()
            }
            isCreatingNewBox = false
        }
    }

    fun applyPalletBoxesSnapshot(allBoxes: List<WmsPackingBoxSummary>) {
        val palletId = currentBox.resolvedPackId.trim()
        if (palletId.isEmpty()) return
        palletCartons = industrySealedCartonsLinkedToPallet(allBoxes, palletId)
            .sortedBy { it.id }
        sealedCartonsAvailable = industrySealedCartonsAvailableForPallet(allBoxes)
            .sortedBy { it.displayTitle.lowercase() }
        orphanCartonsOnPallet = industryOrphanCartonsOnPallet(allBoxes, palletId)
        allBoxes.firstOrNull { industryBoxIdsMatch(it.id, palletId) }?.let { summary ->
            currentBox = summary.toCreatedPackingBox()
                .withKnownPackId(palletId)
                .mergeSessionState(currentBox)
                .withKnownPackId(palletId)
            boxStateById = boxStateById + (palletId to currentBox)
        }
    }

    suspend fun refreshPalletFromServer() {
        if (!isTertiaryPack) return
        val pickListId = list.pickListId?.trim().orEmpty()
        if (pickListId.isEmpty()) return
        runCatching {
            val allBoxes = repo.fetchIndustryPackingBoxes(pickListId)
            listBoxes = allBoxes
            applyPalletBoxesSnapshot(allBoxes)
        }
    }

    fun linkSealedCartonToPallet(carton: WmsPackingBoxSummary) {
        scope.launch {
            if (currentList.isPackingStatusPacked || completing) return@launch
            if (!carton.isCompleted || carton.isPackedListSummary) {
                error = "Only sealed cartons can be added to the pallet."
                return@launch
            }
            if (!carton.hasIndustryBoxId) {
                error = "Carton id must be a numeric industry box id to link to the pallet."
                return@launch
            }
            val pickListId = list.pickListId?.trim().orEmpty()
            val palletId = currentBox.resolvedPackId.trim()
            val cartonId = carton.resolvedPackId.trim()
            if (pickListId.isEmpty() || palletId.isEmpty() || cartonId.isEmpty()) {
                error = "Missing pick list, pallet, or carton id."
                return@launch
            }
            if (packerId <= 0) {
                error = "Packer is not configured."
                return@launch
            }
            linkingCartonId = cartonId
            error = null
            actionMessage = null
            runCatching {
                repo.linkSealedCartonToIndustryPallet(pickListId, palletId, cartonId, packerId)
                val allBoxes = repo.fetchIndustryPackingBoxes(pickListId)
                listBoxes = allBoxes
                applyPalletBoxesSnapshot(allBoxes)
                val linked = industrySealedCartonsLinkedToPallet(allBoxes, palletId)
                if (linked.none { industryBoxIdsMatch(it.id, cartonId) }) {
                    error = "Carton $cartonId was not linked to pallet $palletId. The server did not update parent_box_id."
                    return@runCatching
                }
                val palletSscc = EpcisFlowService.ssccFromBox(currentBox)
                    ?: allBoxes.firstOrNull { industryBoxIdsMatch(it.id, palletId) }
                        ?.let { EpcisFlowService.ssccFromSummary(it) }
                val cartonSscc = EpcisFlowService.ssccFromSummary(carton)
                if (palletSscc != null && cartonSscc != null) {
                    EpcisFlowService.onCartonLinkedToPallet(session, palletSscc, cartonSscc)
                }
                actionMessage = "Linked sealed carton ${carton.displayTitle} (id: $cartonId) to pallet."
                val available = industrySealedCartonsAvailableForPallet(allBoxes)
                if (linked.isNotEmpty() && available.isEmpty()) {
                    showSealPalletSheet = true
                    actionMessage =
                        "All ${linked.size} sealed cartons on pallet. Seal the pallet to generate a barcode."
                }
            }.onFailure { err -> error = err.message }
            linkingCartonId = null
        }
    }

    fun createPalletInSession() {
        scope.launch {
            if (packingBoxTypeConfig(sessionUiPackType).second == 3 || isCreatingPallet || completing) return@launch
            val pickListId = list.pickListId?.trim().orEmpty()
            if (pickListId.isEmpty()) {
                error = "Pick list id is missing."
                return@launch
            }
            if (packerId <= 0) {
                error = "Packer is not configured."
                return@launch
            }
            isCreatingPallet = true
            error = null
            actionMessage = null
            showFinishPackingSheet = false
            runCatching {
                persistActiveBoxState()
                var created = repo.createIndustryPallet(pickListId, packerId)
                val allBoxes = repo.fetchIndustryPackingBoxes(pickListId)
                if (created.resolvedPackId.isBlank()) {
                    allBoxes
                        .filter { box ->
                            box.isTertiaryPackage &&
                                !box.isCompleted &&
                                box.resolvedParentPackId.isNullOrBlank()
                        }
                        .lastOrNull()
                        ?.toCreatedPackingBox()
                        ?.let { recovered -> created = created.mergeSessionState(recovered) }
                }
                sessionUiPackType = "Tertiary"
                currentBox = created
                val palletKey = created.resolvedPackId
                boxStateById = if (palletKey.isNotBlank()) mapOf(palletKey to created) else emptyMap()
                palletCartons = emptyList()
                sealedCartonsAvailable = emptyList()
                orphanCartonsOnPallet = emptyList()
                listBoxes = allBoxes
                applyPalletBoxesSnapshot(allBoxes)
                onActiveBoxChanged(created)
                actionMessage = "Pallet created. Link your sealed cartons below — they will be linked, not recreated."
            }.onFailure { err -> error = err.message }
            isCreatingPallet = false
        }
    }

    fun selectBox(summary: WmsPackingBoxSummary) {
        if (summary.isCompleted || summary.isPackedListSummary) return
        if (summary.resolvedPackId == currentBox.resolvedPackId) return
        persistActiveBoxState()
        error = null
        val cached = boxStateById[summary.id]
        currentBox = cached ?: summary.toCreatedPackingBox()
        if (cached == null) {
            boxStateById = boxStateById + (summary.id to currentBox)
        }
        actionMessage = "Selected ${currentBox.resolvedPackLabel}. Items will pack into this box."
    }

    fun sealBoxFromList(summary: WmsPackingBoxSummary) {
        scope.launch {
            if (sealingBoxId != null || completing) return@launch
            if (packerId <= 0) {
                error = "Packer is not configured."
                return@launch
            }
            sealingBoxId = summary.id
            error = null
            runCatching {
                if (summary.id == currentBox.resolvedPackId) {
                    persistActiveBoxState()
                }
                val sealed = repo.sealIndustryPackingBox(summary.id, packerId)
                if (!summary.isTertiaryPackage) {
                    EpcisFlowService.onCartonSealed(session, sealed)
                }
                boxStateById = boxStateById - summary.id
                reloadListBoxes()
                if (summary.id == currentBox.resolvedPackId) {
                    currentBox = sealed
                    val openBoxes = listBoxes.filter { !it.isCompleted && !it.isPackedListSummary }
                    openBoxes.firstOrNull()?.let { next ->
                        selectBox(next)
                    } ?: run {
                        listBoxes.firstOrNull { it.id == summary.id }?.let {
                            currentBox = it.toCreatedPackingBox()
                        } ?: run {
                            currentBox = sealed
                        }
                    }
                }
                val stillOpen = listBoxes.any { !it.isCompleted && !it.isPackedListSummary }
                val listFullyPacked = currentList.packLines.isNotEmpty() && currentList.packLines.all { line ->
                    line.isLineFullyPacked || line.packedCount >= line.packableQty
                }
                val awaitingFinish = listFullyPacked && !stillOpen && !currentList.isPackingStatusPacked
                if (currentList.canComplete == true || awaitingFinish) {
                    showFinishPackingSheet = true
                    finishSheetDismissed = false
                    actionMessage = "All cartons sealed. Create a pallet or complete packaging."
                } else {
                    actionMessage = "Sealed ${summary.displayTitle}."
                }
                boxActionTarget = null
            }.onFailure { err -> error = err.message }
            sealingBoxId = null
        }
    }

    fun sealActivePallet() {
        scope.launch {
            if (!isTertiaryPack || !currentBox.isPackageInProgress || isSealingPallet) return@launch
            if (palletCartons.isEmpty()) {
                error = "Add sealed cartons to the pallet before sealing."
                return@launch
            }
            if (packerId <= 0) {
                error = "Packer is not configured."
                return@launch
            }
            val palletId = currentBox.resolvedPackId.trim()
            if (palletId.isBlank()) {
                error = "Pallet id is missing."
                return@launch
            }
            isSealingPallet = true
            error = null
            runCatching {
                val sealed = repo.sealIndustryPackingBox(palletId, packerId)
                    .withKnownPackId(palletId)
                currentBox = sealed.withKnownPackId(palletId)
                boxStateById = boxStateById + (palletId to currentBox)
                reloadListBoxes()
                refreshPalletFromServer()
                if (currentBox.isPackageInProgress) {
                    error = "Pallet $palletId was not sealed on the server. Try again or refresh the list."
                    return@runCatching
                }
                val linkedSsccs = palletCartons.mapNotNull { EpcisFlowService.ssccFromSummary(it) }
                val l3Items = buildL3AggregateItems(sealed, palletCartons, session)
                if (l3Items.isNotEmpty()) {
                    EpcisFlowService.aggregateL3WithItems(
                        session = session,
                        parentSscc = palletId,
                        items = l3Items,
                        containerSealed = true,
                    )
                } else if (linkedSsccs.isNotEmpty()) {
                    EpcisFlowService.onPalletSealed(session, sealed, linkedSsccs)
                }
                actionMessage = "Pallet sealed. Barcode is ready for dispatch."
            }.onFailure { err -> error = err.message }
            isSealingPallet = false
        }
    }

    LaunchedEffect(box.resolvedPackId, box.resolvedItems.size, box.resolvedTotalPackedQty) {
        if (box.resolvedPackId.isBlank()) return@LaunchedEffect
        val merged = if (box.resolvedPackId == currentBox.resolvedPackId) {
            currentBox.mergeSessionState(box)
        } else {
            box
        }
        currentBox = merged
        boxStateById = boxStateById + (merged.resolvedPackId to merged)
        val packedInBox = merged.resolvedItems
            .mapNotNull { item ->
                val key = item.pickLineId?.trim()?.takeIf { it.isNotBlank() }
                    ?: item.taskIdAlt?.trim()?.takeIf { it.isNotBlank() }
                val qty = item.quantity ?: 0
                if (key.isNullOrBlank() || qty <= 0) null else key to qty
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, qtys) -> qtys.sum() }
        if (packedInBox.isNotEmpty()) {
            sessionPackedByLine = sessionPackedByLine + packedInBox.mapValues { (key, qty) ->
                maxOf(sessionPackedByLine[key] ?: 0, qty)
            }
        }
    }

    LaunchedEffect(box.resolvedTotalPackedQty, box.resolvedItems.size) {
        if (box.resolvedItems.isEmpty()) return@LaunchedEffect
        runCatching {
            repo.fetchPackingPickListsByPacker(packerId, companyId)
                .lists
                .find { it.id == list.id }
                ?.let { currentList = it }
        }
    }

    LaunchedEffect(list.pickListId) {
        val pickListId = list.pickListId?.trim().orEmpty()
        val activePackId = currentBox.resolvedPackId.ifBlank { box.resolvedPackId }
        val hasIncomingItems = currentBox.resolvedItems.isNotEmpty()
        loadingListBoxes = true
        loadingSession = !hasIncomingItems && activePackId.isBlank()
        runCatching {
            if (pickListId.isNotEmpty()) {
                listBoxes = repo.fetchIndustryPackingBoxes(pickListId)
                if (!hasIncomingItems) {
                    repo.fetchPackingPickListsByPacker(packerId, companyId)
                        .lists
                        .find { it.id == list.id }
                        ?.let { currentList = it }
                }
            }
            if (activePackId.isNotBlank()) {
                if (!hasIncomingItems) {
                    val refreshed = runCatching {
                        repo.fetchPackingBoxWithFallback(activePackId, companyId, pickListId)
                    }.getOrNull()
                    if (refreshed != null) {
                        currentBox = currentBox.mergeSessionState(refreshed)
                    }
                }
                listBoxes.firstOrNull { it.resolvedPackId == activePackId }?.let { summary ->
                    currentBox = currentBox.enrichedFrom(summary)
                }
            } else if (pickListId.isNotEmpty()) {
                listBoxes
                    .filter { !it.isCompleted && it.resolvedParentPackId.isNullOrBlank() }
                    .lastOrNull()
                    ?.toCreatedPackingBox()
                    ?.let { recovered ->
                        currentBox = recovered
                    }
            }
        }.onFailure {
            listBoxes = currentList.boxes.orEmpty()
        }
        loadingListBoxes = false
        loadingSession = false
    }

    LaunchedEffect(list.pickListId, isTertiaryPack, currentBox.resolvedPackId) {
        if (!isTertiaryPack) return@LaunchedEffect
        refreshPalletFromServer()
    }

    val sessionTitle = if (isTertiaryPack) "Pack Pallet" else "Pack Box"
    val packTypeDisplay = currentBox.packType?.takeIf { it.isNotBlank() }
        ?: packingBoxTypeConfig(sessionUiPackType).first

    val baselinePackedByLine = remember(list.id, currentBox.resolvedPackId) {
        currentList.packLines.associate { line ->
            line.apiPickLineId to maxOf(
                0,
                line.packedCount - currentBox.packedQtyForLine(line.apiPickLineId),
            )
        }
    }

    fun packedQtyInSession(lineKey: String): Int = currentBox.packedQtyForLine(lineKey)

    fun totalPackedQty(line: WmsPackingPickListLine): Int {
        val lineKey = line.apiPickLineId
        val baseline = baselinePackedByLine[lineKey]
            ?: maxOf(0, line.packedCount - packedQtyInSession(lineKey))
        val session = sessionPackedByLine[lineKey] ?: 0
        val inActiveBox = packedQtyInSession(lineKey)
        return maxOf(line.packedCount, baseline + inActiveBox, session, inActiveBox)
    }

    val isListFullyPacked = currentList.packLines.isNotEmpty() && currentList.packLines.all { line ->
        val packedTotal = totalPackedQty(line)
        line.isLineFullyPacked || packedTotal >= line.packableQty
    }

    val hasUnsealedBoxes = remember(listBoxes, currentBox.resolvedPackId, boxStateById) {
        if (listBoxes.isNotEmpty()) {
            listBoxes.any { !it.isCompleted && !it.isPackedListSummary }
        } else {
            currentBox.isPackageInProgress || boxStateById.values.any { it.isPackageInProgress }
        }
    }
    val isListPacked = currentList.isPackingStatusPacked
    val isAwaitingPackerFinish = remember(currentList.canComplete, isListFullyPacked, hasUnsealedBoxes, isListPacked) {
        currentList.canComplete == true ||
            (isListFullyPacked && !hasUnsealedBoxes && !isListPacked)
    }
    val shouldEnableCompleteButton = if (isTertiaryPack) {
        !currentBox.isPackageInProgress &&
            (currentList.canComplete == true || isAwaitingPackerFinish || isListFullyPacked)
    } else {
        isAwaitingPackerFinish
    }
    val canCreatePallet = !isTertiaryPack && shouldEnableCompleteButton
    val sealedCartonCount = listBoxes.count { it.isCompleted && !it.isPackedListSummary && it.isSecondaryPackage }
    val totalSealedCartonsCount = palletCartons.size + sealedCartonsAvailable.size
    val allSealedCartonsOnPallet = palletCartons.isNotEmpty() &&
        sealedCartonsAvailable.isEmpty() &&
        totalSealedCartonsCount > 0
    val canCompletePickList = (currentList.canComplete == true || (isListFullyPacked && !hasUnsealedBoxes)) &&
        !isListPacked

    LaunchedEffect(isAwaitingPackerFinish, loadingListBoxes, isTertiaryPack, finishSheetDismissed) {
        if (!loadingListBoxes && isAwaitingPackerFinish && !isTertiaryPack && !finishSheetDismissed) {
            showFinishPackingSheet = true
        }
    }

    LaunchedEffect(currentBox.hierarchyLevel, currentBox.packType) {
        if ((currentBox.hierarchyLevel ?: 0) == 3 || currentBox.packType?.uppercase() == "PALLET") {
            sessionUiPackType = "Tertiary"
        }
    }

    suspend fun tryMarkListPacked(): Boolean {
        println("=== COMPLETE PACKING === tryMarkListPacked called")
        println("=== COMPLETE PACKING === isListFullyPacked: $isListFullyPacked")
        if (!isListFullyPacked) return true
        val pickListId = list.pickListId?.trim().orEmpty()
        println("=== COMPLETE PACKING === pickListId: '$pickListId'")
        println("=== COMPLETE PACKING === packerId: $packerId")
        if (pickListId.isEmpty()) {
            error = "Pick list id is missing."
            return false
        }
        return runCatching {
            println("=== COMPLETE PACKING === calling completeIndustryPackingList...")
            repo.completeIndustryPackingList(pickListId, packerId)
            println("=== COMPLETE PACKING === completeIndustryPackingList SUCCESS")
        }.fold(
            onSuccess = { true },
            onFailure = { err ->
                println("=== COMPLETE PACKING === completeIndustryPackingList FAILED: ${err.message}")
                val msg = err.message.orEmpty()
                if (msg.contains("open", ignoreCase = true) || msg.contains("seal", ignoreCase = true)) {
                    sealBoxErrorMessage =
                        "The box is still open. It must be sealed before completing the picklist. Seal the box now?"
                    showSealBoxFirstAlert = true
                    false
                } else {
                    throw err
                }
            },
        )
    }

    fun completePackage(andStartNew: Boolean) {
        scope.launch {
            completing = true
            error = null
            println("=== COMPLETE PACKING === completePackage called, andStartNew: $andStartNew")
            println("=== COMPLETE PACKING === isTertiaryPack: $isTertiaryPack")
            println("=== COMPLETE PACKING === hasUnsealedBoxes: $hasUnsealedBoxes")
            println("=== COMPLETE PACKING === canCompletePickList: $canCompletePickList")
            println("=== COMPLETE PACKING === isListFullyPacked: $isListFullyPacked")
            runCatching {
                if (isTertiaryPack && currentBox.isPackageInProgress) {
                    error = "Seal the pallet before completing packaging."
                    return@runCatching
                }
                if (hasUnsealedBoxes) {
                    error = "Seal all open boxes before completing packing."
                    return@runCatching
                }
                if (!canCompletePickList && !andStartNew) {
                    error = "Pack all items and seal every box before completing."
                    return@runCatching
                }
                val packId = currentBox.resolvedPackId
                if (andStartNew) {
                    if (packId.isNotBlank() && currentBox.hasPackageContents) {
                        repo.sealIndustryPackingBox(packId, packerId)
                        boxStateById = boxStateById - packId
                    }
                    createNewBoxInSessionSuspend()
                } else {
                    if (packId.isNotBlank() && currentBox.hasPackageContents && !isTertiaryPack && !currentBox.isPackageCompleted) {
                        currentBox = repo.sealIndustryPackingBox(packId, packerId)
                    }
                    if (!tryMarkListPacked()) return@runCatching
                    onCompletePackage()
                }
            }.onFailure { error = it.message ?: it.toString() }
            completing = false
        }
    }

    var consumedNewPackageTrigger by remember(triggerNewPackageOnOpen) { mutableStateOf(false) }
    LaunchedEffect(triggerNewPackageOnOpen) {
        if (triggerNewPackageOnOpen && !consumedNewPackageTrigger) {
            consumedNewPackageTrigger = true
            createNewBoxInSession()
        }
    }

    fun sealBoxAndCompletePicklist() {
        scope.launch {
            completing = true
            error = null
            showSealBoxFirstAlert = false
            println("=== SEAL & COMPLETE === sealBoxAndCompletePicklist called")
            runCatching {
                val packId = currentBox.resolvedPackId
                println("=== SEAL & COMPLETE === packId: $packId")
                if (packId.isBlank()) {
                    error = "Package id is missing."
                    return@runCatching
                }
                println("=== SEAL & COMPLETE === sealing box...")
                currentBox = repo.sealIndustryPackingBox(packId, packerId)
                println("=== SEAL & COMPLETE === box sealed, isListFullyPacked: $isListFullyPacked")
                if (isListFullyPacked) {
                    val pickListId = list.pickListId?.trim().orEmpty()
                    println("=== SEAL & COMPLETE === pickListId: '$pickListId'")
                    if (pickListId.isEmpty()) {
                        error = "Pick list id is missing."
                        return@runCatching
                    }
                    println("=== SEAL & COMPLETE === calling completeIndustryPackingList...")
                    repo.completeIndustryPackingList(pickListId, packerId)
                    println("=== SEAL & COMPLETE === completeIndustryPackingList SUCCESS")
                }
                onCompletePackage()
            }.onFailure {
                println("=== SEAL & COMPLETE === FAILED: ${it.message}")
                error = it.message ?: it.toString()
            }
            completing = false
        }
    }

    WmsRichConfirmSheet(
        visible = showSealBoxFirstAlert,
        title = "Seal Box First",
        message = sealBoxErrorMessage
            ?: "The box is still open. It must be sealed before completing the picklist. Seal the box now?",
        icon = Icons.Default.Verified,
        iconTint = WmsColors.Success,
        iconBackground = WmsColors.SuccessBg,
        primaryLabel = "Seal & Complete",
        onPrimary = { sealBoxAndCompletePicklist() },
        onDismiss = { showSealBoxFirstAlert = false },
        loading = completing,
    )

    PackingFinishChoiceSheet(
        visible = showFinishPackingSheet,
        sealedCartonCount = maxOf(sealedCartonCount, listBoxes.count { it.isCompleted }),
        showCreatePallet = !isTertiaryPack,
        isCreatingPallet = isCreatingPallet,
        isCompleting = completing,
        onCreatePallet = {
            showFinishPackingSheet = false
            finishSheetDismissed = true
            createPalletInSession()
        },
        onCompletePackaging = {
            showFinishPackingSheet = false
            finishSheetDismissed = true
            completePackage(andStartNew = false)
        },
        onLater = {
            showFinishPackingSheet = false
            finishSheetDismissed = true
        },
    )

    boxActionTarget?.let { target ->
        PackingBoxActionSheet(
            box = target,
            isActiveBox = target.id == currentBox.resolvedPackId,
            isSealing = sealingBoxId == target.id,
            onResume = {
                if (target.id == currentBox.resolvedPackId) {
                    boxActionTarget = null
                } else {
                    selectBox(target)
                    boxActionTarget = null
                }
            },
            onSeal = { sealBoxFromList(target) },
            onDismiss = { boxActionTarget = null },
        )
    }

    PackingPalletSealSheet(
        visible = showSealPalletSheet,
        palletLabel = currentBox.resolvedPackLabel,
        palletSscc = currentBox.resolvedBarcodeData,
        cartons = palletCartons,
        attachedCount = palletCartons.size,
        totalCount = maxOf(totalSealedCartonsCount, palletCartons.size, 1),
        allAttached = allSealedCartonsOnPallet,
        isSealed = !currentBox.isPackageInProgress,
        isSealing = isSealingPallet,
        isCompleting = completing,
        barcodeImageUrl = currentBox.resolvedBarcodeImageUrl,
        barcodeData = currentBox.resolvedBarcodeData,
        onSeal = { sealActivePallet() },
        onComplete = {
            showSealPalletSheet = false
            completePackage(andStartNew = false)
        },
        onLater = { showSealPalletSheet = false },
    )

    Column(Modifier.fillMaxSize().background(WmsColors.PageBgAlt)) {
        WmsLightHeader(title = sessionTitle, showBack = true, onBack = onBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PackingBoxSummaryCard(
                list = currentList,
                box = currentBox,
                packTypeDisplay = packTypeDisplay,
                statusLabel = currentList.displayPackingStatusLabel.replace('_', ' '),
                loadingBarcode = loadingSession,
                isTertiaryPack = isTertiaryPack,
                linkedCartonCount = palletCartons.size,
                allSealedCartonsOnPallet = allSealedCartonsOnPallet,
            )

            actionMessage?.let {
                Text(
                    it,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = WmsColors.Success,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WmsColors.SuccessBg, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                )
            }

            if (canCreatePallet) {
                CreatePalletPromptBanner()
            }

            if (!isTertiaryPack && (listBoxes.isNotEmpty() || loadingListBoxes)) {
                PackingListBoxesSection(
                    boxes = listBoxes,
                    currentBoxId = currentBox.resolvedPackId,
                    isLoading = loadingListBoxes,
                    onBoxTap = { summary ->
                        if (!summary.isCompleted && !summary.isPackedListSummary &&
                            sealingBoxId == null && !isCreatingNewBox && !completing
                        ) {
                            boxActionTarget = summary
                        }
                    },
                )
            }

            if (!isTertiaryPack) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Line Items", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.clickable(
                            enabled = !completing && !isCreatingNewBox && !canCreatePallet,
                        ) { createNewBoxInSession() },
                    ) {
                        if (isCreatingNewBox) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = WmsColors.Navy,
                            )
                        } else {
                            Icon(
                                Icons.Default.AddCircle,
                                contentDescription = null,
                                tint = WmsColors.Navy,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Text(
                            text = if (isCreatingNewBox) "Creating…" else "New Package",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canCreatePallet) WmsColors.TextSecondary else WmsColors.Navy,
                        )
                    }
                }
            }

            val packingLineRows = if (!isTertiaryPack && !loadingSession && currentList.packLines.isNotEmpty()) {
                currentList.packLines
                    .map { line -> Triple(line, totalPackedQty(line), line.packableQty) }
                    .filter { (_, packed, target) -> target == 0 || packed < target }
                    .sortedBy { (line, _, _) -> line.productName.orEmpty().lowercase() }
            } else {
                emptyList()
            }

            if (!isTertiaryPack) {
                when {
                    loadingSession -> {
                        WmsLineListSkeleton(count = currentList.packLines.size.coerceIn(2, 4))
                    }
                    currentList.packLines.isEmpty() -> {
                        Text(
                            "No line items available.",
                            fontSize = 13.sp,
                            color = WmsColors.TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                        )
                    }
                    packingLineRows.isNotEmpty() -> {
                        packingLineRows.forEach { (line, packedTotal, target) ->
                            val statusLabel = line.linePackingStatusLabel(packedTotal)
                            val packedInActiveBox = currentBox.packedQtyForLine(line.apiPickLineId)
                            PackingLineItemCard(
                                line = line,
                                toteNumber = currentList.toteNumber,
                                packedTotal = packedTotal,
                                packedInActiveBox = packedInActiveBox,
                                activeBoxLabel = currentBox.resolvedPackLabel,
                                target = target,
                                statusLabel = statusLabel,
                                isDone = false,
                                isTertiaryPack = isTertiaryPack,
                                enabled = !completing && currentBox.isPackageInProgress,
                                onAddQty = { onAddQty(line, currentBox) },
                            )
                        }
                    }
                    currentList.packLines.any { line ->
                        line.packableQty > 0 && totalPackedQty(line) >= line.packableQty
                    } -> {
                        Text(
                            "All line items are packed. Seal boxes or create a pallet to finish.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = WmsColors.TextSecondary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(WmsColors.SuccessBg, RoundedCornerShape(12.dp))
                                .border(1.dp, WmsColors.SuccessBorder, RoundedCornerShape(12.dp))
                                .padding(14.dp),
                        )
                    }
                }
            }

            if (isTertiaryPack) {
                if (currentBox.isPackageInProgress && totalSealedCartonsCount > 0) {
                    PalletSealProgressBanner(
                        attachedCount = palletCartons.size,
                        totalCount = maxOf(totalSealedCartonsCount, palletCartons.size, 1),
                        allAttached = allSealedCartonsOnPallet,
                        remainingToLink = sealedCartonsAvailable.size,
                        onClick = { showSealPalletSheet = true },
                    )
                }

                if (orphanCartonsOnPallet.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFFBEB), RoundedCornerShape(12.dp))
                            .border(1.dp, Color(0xFFFCD34D), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = Color(0xFFD97706),
                            modifier = Modifier.size(20.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Wrong cartons on pallet",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = WmsColors.TextPrimary,
                            )
                            Text(
                                "Server created empty boxes instead of linking your sealed cartons. Link sealed cartons using the list below.",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = WmsColors.TextSecondary,
                            )
                        }
                    }
                }

                PackingPalletCartonsSection(
                    palletId = currentBox.resolvedPackId,
                    cartons = palletCartons,
                    linkedCount = palletCartons.size,
                    totalSealedCount = totalSealedCartonsCount,
                    allLinked = allSealedCartonsOnPallet,
                    isLoading = loadingListBoxes,
                    palletInProgress = currentBox.isPackageInProgress,
                    onSealPallet = if (currentBox.isPackageInProgress && allSealedCartonsOnPallet) {
                        { showSealPalletSheet = true }
                    } else {
                        null
                    },
                )

                if (sealedCartonsAvailable.isNotEmpty()) {
                    PackingSealedCartonsToLinkSection(
                        cartons = sealedCartonsAvailable,
                        linkingCartonId = linkingCartonId,
                        isBusy = completing || isSealingPallet,
                        onLink = { linkSealedCartonToPallet(it) },
                    )
                } else if (!loadingListBoxes && palletCartons.isEmpty()) {
                    Text(
                        "No sealed cartons available to link. Seal secondary boxes first, then return here.",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = WmsColors.TextSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                            .padding(14.dp),
                    )
                }
            }

            error?.let { WmsErrorBanner(it) }
        }

        Column(modifier = Modifier.fillMaxWidth().background(Color.White)) {
            HorizontalDivider(color = WmsColors.Border)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (canCreatePallet) {
                    Button(
                        onClick = { createPalletInSession() },
                        enabled = !isCreatingPallet && !completing && !isCreatingNewBox,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCreatingPallet) Color(0xFF9CA3AF) else WmsColors.Success,
                        ),
                    ) {
                        if (isCreatingPallet) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        } else {
                            Icon(Icons.Default.LocalShipping, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (isCreatingPallet) "Creating Pallet…" else "Create Pallet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }

                if (isTertiaryPack && currentBox.isPackageInProgress && totalSealedCartonsCount > 0) {
                    Button(
                        onClick = { showSealPalletSheet = true },
                        enabled = !isSealingPallet && !completing,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                isSealingPallet -> Color(0xFF9CA3AF)
                                allSealedCartonsOnPallet -> WmsColors.Success
                                else -> WmsColors.Navy
                            },
                        ),
                    ) {
                        if (isSealingPallet) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            when {
                                isSealingPallet -> "Sealing Pallet…"
                                allSealedCartonsOnPallet -> "Seal Pallet & Barcode"
                                else -> "View Pallet Progress"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }

                if (isTertiaryPack && !currentBox.isPackageInProgress && !currentBox.resolvedBarcodeImageUrl.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { showSealPalletSheet = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Verified, null, tint = WmsColors.Navy, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("View Pallet Barcode", fontWeight = FontWeight.Bold, color = WmsColors.Navy)
                    }
                }

                Button(
                    onClick = { completePackage(andStartNew = false) },
                    enabled = !completing && !isCreatingPallet && shouldEnableCompleteButton,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (shouldEnableCompleteButton) WmsColors.Navy else Color(0xFF9CA3AF),
                    ),
                ) {
                    if (completing) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        if (completing) "Completing…" else "Complete Packaging",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

private fun buildL3AggregateItems(
    sealed: WmsCreatedPackingBox,
    childCartons: List<WmsPackingBoxSummary>,
    session: SessionManager,
): List<core.network.models.EpcisL3AggregateItem> {
    val seen = mutableSetOf<String>()
    val items = mutableListOf<core.network.models.EpcisL3AggregateItem>()
    val companyId = WmsSession.companyId(session)

    fun addItem(gtin: String, serial: String, batch: String?) {
        val normalized = gtin.filter { it.isDigit() }
        val trimmedSerial = serial.trim()
        if (normalized.isEmpty() || trimmedSerial.isEmpty()) return
        val key = "$normalized|$trimmedSerial"
        if (!seen.add(key)) return
        items.add(core.network.models.EpcisL3AggregateItem(gtin = normalized, serial = trimmedSerial, batch = batch?.trim()))
    }

    val palletId = sealed.resolvedPackId.trim()
    for (carton in childCartons) {
        if (carton.resolvedParentPackId?.trim() != palletId) continue
        for (item in carton.items.orEmpty()) {
            val barcode = item.childBox?.barcodeData?.trim()?.takeIf { it.isNotEmpty() }
            if (barcode != null) {
                val parsed = utils.Gs1Parser.parse(barcode)
                val gtin = parsed?.gtin.orEmpty()
                val serial = parsed?.serial.orEmpty()
                if (gtin.isNotEmpty() && serial.isNotEmpty()) {
                    addItem(gtin, serial, parsed?.batch ?: item.batch)
                    continue
                }
            }
            val itemGtin = item.productName?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            val itemSerial = item.childBox?.barcodeData?.trim()?.takeIf { it.isNotEmpty() } ?: continue
            addItem(itemGtin, itemSerial, item.batch)
        }
    }
    for (item in sealed.resolvedItems) {
        val barcode = item.childBox?.barcodeData?.trim()?.takeIf { it.isNotEmpty() }
        if (barcode != null) {
            val parsed = utils.Gs1Parser.parse(barcode)
            val gtin = parsed?.gtin.orEmpty()
            val serial = parsed?.serial.orEmpty()
            if (gtin.isNotEmpty() && serial.isNotEmpty()) {
                addItem(gtin, serial, parsed?.batch ?: item.batch)
            }
        }
    }
    return items
}

@Composable
private fun PackingBoxSummaryCard(
    list: WmsPackingPickListItem,
    box: WmsCreatedPackingBox,
    packTypeDisplay: String,
    statusLabel: String,
    loadingBarcode: Boolean = false,
    isTertiaryPack: Boolean = false,
    linkedCartonCount: Int = 0,
    allSealedCartonsOnPallet: Boolean = false,
) {
    if (loadingBarcode) {
        WmsPackBoxSummarySkeleton()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(list.cardTitle, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)
                list.orderNumber?.takeIf { it.isNotBlank() }?.let {
                    Text(it, fontSize = 12.sp, color = WmsColors.TextSecondary, fontWeight = FontWeight.Medium)
                }
                Text(
                    box.resolvedPackLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WmsColors.Navy,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = if (box.isPackageInProgress) WmsColors.Navy else WmsColors.TextSecondary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        if (isTertiaryPack) "Pallet: ${box.resolvedPackLabel}" else "Packing into: ${box.resolvedPackLabel}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (box.isPackageInProgress) WmsColors.Navy else WmsColors.TextSecondary,
                    )
                }
                if (isTertiaryPack && box.isPackageInProgress) {
                    Text(
                        if (allSealedCartonsOnPallet) {
                            "All sealed cartons on pallet — seal pallet when ready."
                        } else {
                            "Add each sealed carton to the pallet (links existing cartons, does not create new ones)."
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFD97706),
                    )
                }
            }
            Text(
                text = statusLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.Navy,
                modifier = Modifier
                    .background(WmsColors.Navy.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        if (box.resolvedBarcodeData.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(box.resolvedBarcodeType, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextSecondary)
                Text(
                    box.resolvedBarcodeData,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WmsColors.TextSecondary,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        PackingBarcodeImageSection(
            imageUrl = box.resolvedBarcodeImageUrl,
            label = box.resolvedPackLabel,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (isTertiaryPack) {
                PackingSummaryChip("Cartons", "$linkedCartonCount")
            } else {
                PackingSummaryChip("Items", "${box.resolvedItemCount}")
            }
            PackingSummaryChip("Units", "${box.resolvedTotalPackedQty}")
            PackingSummaryChip("Type", packTypeDisplay)
        }
    }
}

@Composable
internal fun PackingBarcodeImageSection(imageUrl: String?, label: String) {
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var imageLoading by remember(imageUrl) { mutableStateOf(!imageUrl.isNullOrBlank()) }
    var imageFailed by remember(imageUrl) { mutableStateOf(false) }
    val hasImage = !imageUrl.isNullOrBlank() && !imageFailed

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                hasImage -> {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(96.dp),
                        onLoading = { imageLoading = true },
                        onSuccess = {
                            imageLoading = false
                            imageFailed = false
                        },
                        onError = {
                            imageLoading = false
                            imageFailed = true
                        },
                    )
                }
                imageLoading -> {
                    CircularProgressIndicator(
                        color = WmsColors.Navy,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                    )
                }
                else -> {
                    Text(
                        "Barcode preview unavailable",
                        fontSize = 12.sp,
                        color = WmsColors.TextSecondary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    imageUrl?.let { downloadImage(it) }
                    saveMessage = "Saved to Downloads"
                },
                enabled = hasImage,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(Icons.Default.Download, null, tint = WmsColors.Navy, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Download", fontWeight = FontWeight.Bold, color = WmsColors.Navy)
            }
            Button(
                onClick = { imageUrl?.let { shareImage(it) } },
                enabled = hasImage,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WmsColors.Navy,
                    disabledContainerColor = Color(0xFF9CA3AF),
                ),
            ) {
                Icon(Icons.Default.Share, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Share", fontWeight = FontWeight.Bold)
            }
        }

        saveMessage?.let {
            Text(it, fontSize = 11.sp, color = WmsColors.Success, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PackingListBoxesSection(
    boxes: List<WmsPackingBoxSummary>,
    currentBoxId: String,
    isLoading: Boolean,
    onBoxTap: ((WmsPackingBoxSummary) -> Unit)? = null,
) {
    val displayBoxes = boxes.filter { !it.isPackedListSummary }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Active Boxes",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                )
                Text(
                    "Tap the active box to seal it, or another open box to switch.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = WmsColors.TextSecondary,
                )
            }
            if (isLoading) {
                CircularProgressIndicator(
                    color = WmsColors.Navy,
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = displayBoxes,
                key = { box -> box.resolvedPackId.ifBlank { box.displayTitle } },
            ) { box ->
                val isTappable = !box.isCompleted && !box.isPackedListSummary
                PackingListBoxChip(
                    box = box,
                    isCurrent = box.resolvedPackId.isNotBlank() && box.resolvedPackId == currentBoxId,
                    onClick = if (isTappable) ({ onBoxTap?.invoke(box) }) else null,
                )
            }
        }
    }
}

@Composable
private fun PackingListBoxChip(
    box: WmsPackingBoxSummary,
    isCurrent: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val chipNavy = Color(0xFF0B3D78)
    val chipNavyLight = Color(0xFF1A5CA8)
    val chipGreen = Color(0xFF047857)
    val chipGreenLight = Color(0xFF10B981)
    val chipAmber = Color(0xFFEA580C)

    val status = (box.status ?: box.packageStatus).orEmpty().trim().uppercase()
    val isOpen = !box.isCompleted && !box.isPackedListSummary
    val isSealed = box.isCompleted
    val boxLabel = box.packLabel?.trim()?.takeIf { it.isNotBlank() }
        ?: box.barcodeData?.trim()?.takeIf { it.isNotBlank() }
        ?: "Box"

    val statusLabel = when {
        isCurrent -> "ACTIVE"
        isSealed -> "SEALED"
        status.isNotBlank() -> status
        else -> "OPEN"
    }

    val backgroundBrush = when {
        isCurrent -> Brush.linearGradient(listOf(chipNavyLight, chipNavy))
        isSealed -> Brush.linearGradient(listOf(chipGreenLight, chipGreen))
        else -> null
    }

    val borderColor = when {
        isCurrent -> Color(0xFF6EE7B7)
        isSealed -> Color(0xFF065F46)
        isOpen -> chipNavy
        else -> Color(0xFFD1D5DB)
    }
    val borderWidth = if (isCurrent || isSealed) 3.dp else 2.dp

    val shadowColor = when {
        isCurrent -> chipNavy.copy(alpha = 0.55f)
        isSealed -> chipGreen.copy(alpha = 0.5f)
        else -> Color.Black.copy(alpha = 0.08f)
    }
    val shadowElevation = if (isCurrent || isSealed) 10.dp else 4.dp

    val onGradient = isCurrent || isSealed
    val titleColor = if (onGradient) Color.White else WmsColors.TextPrimary
    val iconBg = if (onGradient) Color.White.copy(alpha = 0.22f) else Color(0xFFE8EEF5)
    val iconTint = if (onGradient) Color.White else chipNavy

    val statusPillBg = when {
        isCurrent -> Color.White
        isSealed -> chipGreen
        isOpen -> chipAmber
        else -> Color(0xFF6B7280)
    }
    val statusPillFg = if (isCurrent) chipNavy else Color.White
    val itemCountColor = if (onGradient) Color.White else WmsColors.TextSecondary

    Column(
        modifier = Modifier
            .defaultMinSize(minWidth = 140.dp)
            .shadow(shadowElevation, RoundedCornerShape(12.dp), ambientColor = shadowColor, spotColor = shadowColor)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (backgroundBrush != null) {
                    Modifier.background(backgroundBrush)
                } else {
                    Modifier.background(Color.White)
                },
            )
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSealed) Icons.Default.Verified else Icons.Default.Inventory2,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                boxLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = titleColor,
                maxLines = 1,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = statusLabel,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = statusPillFg,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(statusPillBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            box.itemCount?.takeIf { it > 0 }?.let { count ->
                Text(
                    "$count item${if (count == 1) "" else "s"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = itemCountColor,
                )
            }
        }
    }
}

@Composable
private fun PackingLineItemCard(
    line: WmsPackingPickListLine,
    toteNumber: String?,
    packedTotal: Int,
    packedInActiveBox: Int = 0,
    activeBoxLabel: String? = null,
    target: Int,
    statusLabel: String,
    isDone: Boolean,
    isTertiaryPack: Boolean,
    enabled: Boolean,
    onAddQty: () -> Unit,
) {
    val statusColor = when (statusLabel.uppercase()) {
        "PACKED" -> WmsColors.Success
        "PARTIAL" -> WmsColors.TextSecondary
        else -> packingStatusColor(statusLabel)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(
                1.dp,
                if (isDone) WmsColors.SuccessBorder else WmsColors.Border,
                RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(line.productName.orEmpty(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextPrimary)
                Text("SKU ${line.productSku.orEmpty()}", fontSize = 11.sp, color = WmsColors.TextSecondary, fontWeight = FontWeight.Medium)
                if (line.batchExpiryDetailLine.isNotBlank()) {
                    Text(
                        line.batchExpiryDetailLine,
                        fontSize = 10.sp,
                        color = WmsColors.TextSecondary,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                toteNumber?.takeIf { it.isNotBlank() }?.let {
                    Text("Tote $it", fontSize = 11.sp, color = WmsColors.Navy, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "$packedTotal/$target packed",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDone) WmsColors.Success else WmsColors.Navy,
                )
                if (packedInActiveBox > 0 && !activeBoxLabel.isNullOrBlank()) {
                    Text(
                        "$packedInActiveBox in $activeBoxLabel",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = WmsColors.TextSecondary,
                        maxLines = 1,
                    )
                }
                Text(
                    statusLabel,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                )
            }

            Button(
                onClick = onAddQty,
                enabled = !isDone && enabled,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDone) Color(0xFF9CA3AF) else WmsColors.Navy,
                ),
            ) {
                Icon(Icons.Default.AddCircle, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (isTertiaryPack) "Add to Pallet" else "Add Qty",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (line.hasException && line.pickedCount != line.requiredCount) {
            WmsLineExceptionBanner(
                detail = line.exceptionDetailText,
                compact = true,
            )
        }
    }
}

@Composable
private fun PackingLineItemCompactCard(
    line: WmsPackingPickListLine,
    packedTotal: Int,
    target: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WmsColors.SuccessBg.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
            .border(1.dp, WmsColors.SuccessBorder.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.Verified,
            contentDescription = null,
            tint = WmsColors.Success,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                line.productName.orEmpty(),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = WmsColors.TextPrimary,
                maxLines = 1,
            )
            Text(
                "SKU ${line.productSku.orEmpty()}",
                fontSize = 10.sp,
                color = WmsColors.TextSecondary,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        Text(
            "$packedTotal/$target",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = WmsColors.Success,
        )
    }
}

@Composable
private fun RowScope.PackingSummaryChip(title: String, value: String) {
    Column(modifier = Modifier.weight(1f)) {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextSecondary)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)
    }
}

private fun packingStatusColor(label: String): Color = when (label.uppercase().replace(' ', '_')) {
    "PACKED", "COMPLETED" -> WmsColors.Success
    "IN_PROGRESS", "IN PROGRESS" -> WmsColors.Navy
    "PENDING" -> WmsColors.Warning
    else -> WmsColors.TextSecondary
}

@Composable
fun PackingLineAddQtyScreen(
    list: WmsPackingPickListItem,
    line: WmsPackingPickListLine,
    box: WmsCreatedPackingBox,
    packTypeLabel: String,
    onBack: () -> Unit,
    onBoxUpdated: (WmsCreatedPackingBox) -> Unit,
    onRequestNewPackage: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }
    val packerId = remember { WmsSession.userId(session) }

    var qtyInput by remember { mutableStateOf("1") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showInvalidScanAlert by remember { mutableStateOf(false) }
    var invalidScanMessage by remember { mutableStateOf("") }
    var inputMode by remember { mutableStateOf(WmsScanInputMode.Scan) }
    var isLineVerified by remember { mutableStateOf(false) }
    var gtinVerified by remember { mutableStateOf(false) }
    var verificationMethod by remember { mutableStateOf<String?>(null) }
    var verifyCode by remember { mutableStateOf("") }

    val isTertiaryPack = packingBoxTypeConfig(packTypeLabel).second == 3
    val lineGtin = remember(line) { line.displayGtin }

    LaunchedEffect(line.apiPickLineId) {
        if (lineGtin.isNotEmpty()) {
            runCatching {
                val result = EpcisFlowService.verifyGtin(session, lineGtin)
                gtinVerified = result.getOrNull()?.isRegistered == true
                println("=== GTIN VERIFY === lineGtin: $lineGtin, isRegistered: ${result.getOrNull()?.isRegistered}, gtinVerified: $gtinVerified")
                println("=== GTIN VERIFY === result: ${result}")
            }.onFailure {
                println("=== GTIN VERIFY FAILED === ${it.message}")
            }
        } else {
            println("=== GTIN VERIFY SKIPPED — lineGtin is empty ===")
        }
    }
    val baselinePacked = remember(line.apiPickLineId, box.resolvedPackId) {
        maxOf(0, line.packedCount - box.packedQtyForLine(line.apiPickLineId))
    }
    val packedQtyInBox = baselinePacked + box.packedQtyForLine(line.apiPickLineId)
    val remainingQty = maxOf(line.packableQty - packedQtyInBox, 0)
    val resolvedQty = run {
        val parsed = qtyInput.filter { it.isDigit() }.toIntOrNull() ?: 0
        if (remainingQty <= 0) 0 else minOf(remainingQty, maxOf(1, parsed))
    }
    val expectedSku = line.productSku.orEmpty()
    val expectedBatch = line.displayBatch.takeIf { it != "—" }.orEmpty()

    fun verifyScan(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        val (matches, _) = validateWarehouseScan(trimmed, expectedBatch, "", expectedSku)
        if (matches) {
            isLineVerified = true
            verificationMethod = if (inputMode == WmsScanInputMode.Scan) "scan" else "manual"
            verifyCode = ""
        } else {
            invalidScanMessage = "\"$trimmed\" does not match SKU or batch"
            showInvalidScanAlert = true
        }
    }

    LaunchedEffect(remainingQty) {
        val startQty = maxOf(1, remainingQty).takeIf { remainingQty > 0 } ?: 1
        qtyInput = startQty.toString()
    }

    if (showInvalidScanAlert) {
        AlertDialog(
            onDismissRequest = { showInvalidScanAlert = false },
            title = { Text("Invalid Batch/SKU") },
            text = { Text(invalidScanMessage) },
            confirmButton = {
                TextButton(onClick = { showInvalidScanAlert = false }) { Text("OK") }
            },
        )
    }

    Column(Modifier.fillMaxSize().background(WmsColors.PageBgAlt)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WmsCircularBackButton(onClick = onBack)
            Column {
                list.toteNumber?.takeIf { it.isNotBlank() }?.let {
                    Text("Tote $it", fontSize = 12.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
                }
                Text(
                    line.productName.orEmpty(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                    maxLines = 2,
                )
            }
        }
        HorizontalDivider(color = WmsColors.Border)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "$packedQtyInBox/${line.packableQty} packed",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF1F2937),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(
                        1.dp,
                        if (isLineVerified) WmsColors.Success.copy(alpha = 0.35f) else WmsColors.Border,
                        RoundedCornerShape(12.dp),
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("PRODUCT", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextSecondary)
                        Text(line.productName.orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextPrimary)
                        Text("SKU ${line.productSku.orEmpty()}", fontSize = 13.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
                        Text(
                            line.batchExpiryDetailLine,
                            fontSize = 12.sp,
                            color = WmsColors.TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                        )
                        if (line.hasException && line.pickedCount != line.requiredCount) {
                            WmsLineExceptionBanner(detail = line.exceptionDetailText, compact = true)
                        }
                    }
                    if (isLineVerified) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = WmsColors.Success,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                if (!isLineVerified) {
                    HorizontalDivider(color = WmsColors.Border, modifier = Modifier.padding(horizontal = 4.dp))
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        WmsScanInputTabs(
                            mode = inputMode,
                            onModeChange = {
                                inputMode = it
                                verifyCode = ""
                            },
                            scanLabel = "Scan",
                            manualLabel = "Manual",
                        )
                        when (inputMode) {
                            WmsScanInputMode.Scan -> {
                                WmsBarcodeCameraPreview(
                                    instruction = "Scan product barcode to verify SKU/batch",
                                    enabled = !saving,
                                    onBarcodeScanned = ::verifyScan,
                                    modifier = Modifier.padding(horizontal = 0.dp),
                                    height = 160.dp,
                                )
                            }
                            WmsScanInputMode.Manual -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedTextField(
                                        value = verifyCode,
                                        onValueChange = { verifyCode = it },
                                        modifier = Modifier.weight(1f),
                                        placeholder = { Text("Enter SKU or batch") },
                                        singleLine = true,
                                        textStyle = androidx.compose.ui.text.TextStyle(
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Monospace,
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    Button(
                                        onClick = { verifyScan(verifyCode) },
                                        enabled = verifyCode.trim().isNotEmpty() && !saving,
                                        shape = RoundedCornerShape(999.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Success),
                                    ) {
                                        Text("Verify", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(12.dp))
                    .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Enter pack quantity", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextPrimary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        Icons.Default.RemoveCircle,
                        contentDescription = null,
                        tint = if (resolvedQty > 1 && remainingQty > 0) Color(0xFF9CA3AF) else Color(0xFFD1D5DB),
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(enabled = resolvedQty > 1 && remainingQty > 0) {
                                qtyInput = maxOf(1, resolvedQty - 1).toString()
                            },
                    )
                    OutlinedTextField(
                        value = qtyInput,
                        onValueChange = { raw -> qtyInput = raw.filter { it.isDigit() } },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                        ),
                        shape = RoundedCornerShape(10.dp),
                    )
                    Icon(
                        Icons.Default.AddCircle,
                        contentDescription = null,
                        tint = WmsColors.Navy,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(enabled = resolvedQty < remainingQty && remainingQty > 0) {
                                qtyInput = minOf(remainingQty, resolvedQty + 1).toString()
                            },
                    )
                }
                Text("Remaining: $remainingQty", fontSize = 13.sp, color = WmsColors.TextSecondary, fontWeight = FontWeight.Medium)
            }

            error?.let { WmsErrorBanner(it) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HorizontalDivider(color = WmsColors.Border, modifier = Modifier.padding(bottom = 4.dp))
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        error = null
                        runCatching {
                            val packId = box.resolvedPackId
                            if (packId.isBlank()) {
                                error = "Box id is missing. Go back and reopen the pack session."
                                return@runCatching
                            }
                            runCatching {
                                repo.addItemToIndustryPackingBox(
                                    boxId = packId,
                                    taskId = line.apiPickLineId,
                                    quantity = resolvedQty,
                                    packerId = packerId,
                                    verificationType = verificationMethod,
                                )
                            }.getOrElse { industryErr ->
                                val productId = line.intProductId
                                    ?: throw industryErr
                                repo.addItemToPackingBox(
                                    packId,
                                    WmsAddPackingBoxItemRequest(
                                        companyId = companyId,
                                        productId = productId,
                                        quantity = resolvedQty,
                                        packedBy = packerId,
                                        pickLineId = line.apiPickLineId,
                                    ),
                                )
                            }
                            val merged = box.withItemMerged(line, resolvedQty, packerId)
                            val l2Eligible = !isTertiaryPack
                            val boxBarcode = box.resolvedBarcodeData.ifBlank { null }
                            val boxSscc = (boxBarcode ?: packId).filter { it.isDigit() }.padStart(18, '0')
                            println("=== ADD TO BOX L2 CHECK ===")
                            println("l2Eligible: $l2Eligible")
                            println("isTertiaryPack: $isTertiaryPack")
                            println("lineGtin: '$lineGtin'")
                            println("gtinVerified: $gtinVerified")
                            println("packId: $packId")
                            println("boxSscc: $boxSscc")
                            println("============================")
                            if (l2Eligible) {
                                println("=== ADD TO BOX L2 CALLING aggregateL2Pack ===")
                                runCatching {
                                    EpcisFlowService.aggregateL2Pack(
                                        session = session,
                                        parentSscc = boxSscc,
                                        gtin = lineGtin,
                                        serials = emptyList(),
                                        containerSealed = false,
                                    )
                                }.onSuccess {
                                    println("=== ADD TO BOX L2 SUCCESS ===")
                                }.onFailure {
                                    println("=== ADD TO BOX L2 ERROR === ${it.message}")
                                    it.printStackTrace()
                                }
                            } else {
                                println("=== ADD TO BOX L2 SKIPPED — not eligible ===")
                            }
                            onBoxUpdated(merged)
                            if (resolvedQty >= remainingQty) {
                                onBack()
                            }
                        }.onFailure { err -> error = err.message ?: err.toString() }
                        saving = false
                    }
                },
                enabled = remainingQty > 0 && !saving && resolvedQty > 0,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (remainingQty > 0) WmsColors.Navy else Color(0xFF9CA3AF),
                ),
            ) {
                if (saving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (saving) "Saving…" else if (isTertiaryPack) "Add to Pallet" else "Add to Box",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }

            OutlinedButton(
                onClick = onRequestNewPackage,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Inventory2, null, tint = WmsColors.Navy, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isTertiaryPack) "New Pallet" else "New Package",
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.Navy,
                )
            }
        }
    }
}
