package features.app.warehouse.wms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.repository.WmsRepository
import core.network.wms.FefoBatchItem
import core.network.wms.PickerExceptionDetail
import core.network.wms.parsePickerExceptionSummary
import core.network.wms.batchPickKey
import core.network.wms.resolvedExceptionMessage
import core.network.wms.displayExceptionEntries
import core.network.wms.matchesBatch
import core.network.wms.stateKey
import core.network.wms.FefoProductGroup
import core.network.wms.ToteCatalogItem
import core.network.wms.WmsWarehouseLocation
import core.network.wms.PickerMyListTask
import core.network.wms.WmsPickListLine
import core.storage.SessionManager
import core.storage.getLocalStorage
import features.app.warehouse.batchMatchesScan
import features.app.warehouse.gtinMatchesScan
import features.app.warehouse.productMatchesScan
import features.app.warehouse.WarehouseMlKitScanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PickerBatchScanScreen(
    task: PickerMyListTask,
    toteNumber: String,
    activeToteId: Int = 0,
    openStagingDirectly: Boolean = false,
    onBack: () -> Unit,
    onFinished: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }

    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var productGroups by remember { mutableStateOf<List<FefoProductGroup>>(emptyList()) }
    var localBatchPickStates by remember { mutableStateOf<Map<String, LocalBatchPickState>>(emptyMap()) }

    var scanTargetBatch by remember { mutableStateOf<FefoBatchItem?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showSaveErrorPopup by remember { mutableStateOf(false) }
    var showLoadErrorPopup by remember { mutableStateOf(false) }
    var showStagingSheet by remember { mutableStateOf(false) }

    var currentTote by remember { mutableStateOf(toteNumber.trim()) }
    var activeToteIdState by remember { mutableIntStateOf(activeToteId) }
    var totesCatalog by remember { mutableStateOf<List<ToteCatalogItem>>(emptyList()) }
    var showTotePickerSheet by remember { mutableStateOf(false) }
    var newToteInput by remember { mutableStateOf("") }
    var showFillToteConfirm by remember { mutableStateOf(false) }

    val resolvedTote = currentTote

    val displayProductGroups = remember(productGroups, localBatchPickStates) {
        resolveDisplayProductGroups(productGroups, localBatchPickStates)
    }

    val totalBatches = displayProductGroups.sumOf { it.effectiveBatchCount }
    val pickedBatches = displayProductGroups.sumOf { it.pickedBatchCount }
    val totalSkus = displayProductGroups.size
    val completedSkus = displayProductGroups.count { it.isComplete }
    val progressFraction = if (totalBatches > 0) {
        (pickedBatches.toFloat() / totalBatches).coerceIn(0f, 1f)
    } else 0f

    fun allComplete() = displayProductGroups.isNotEmpty() && displayProductGroups.all { it.isComplete }

    fun showApiError(message: String, isLoad: Boolean = false) {
        if (isLoad) {
            loadError = message
            showLoadErrorPopup = true
        } else {
            saveError = message
            showSaveErrorPopup = true
        }
    }

    fun resolveActiveToteId(): Int {
        if (activeToteIdState > 0) return activeToteIdState
        return totesCatalog.firstOrNull { it.isToteActive }?.resolvedToteId ?: 0
    }

    fun refresh() {
        scope.launch {
            loading = true
            loadError = null
            runCatching {
                repo.fetchPickListTasks(task.pickListId, companyId)
            }.onSuccess { groups ->
                productGroups = groups.mapIndexed { groupIdx, group ->
                    group.copy(
                        batches = group.batches.mapIndexed { batchIdx, batch ->
                            val enriched = enrichBatchWithGroup(batch, group)
                            val key = enriched.batchPickKey(group.key, groupIdx, batchIdx)
                            mergeLocalBatchPickState(
                                enriched,
                                localBatchPickStates[key] ?: localBatchPickStates[enriched.stateKey()],
                            )
                        },
                    )
                }
            }.onFailure {
                showApiError(it.message ?: "Failed to load items.", isLoad = true)
            }
            loading = false
        }
    }

    fun loadTotesCatalog() {
        scope.launch {
            runCatching {
                totesCatalog = repo.fetchToteCatalog(task.pickListId)
                if (activeToteIdState <= 0) {
                    totesCatalog.firstOrNull { it.isToteActive }?.resolvedToteId?.takeIf { it > 0 }?.let {
                        activeToteIdState = it
                    }
                }
            }
        }
    }

    LaunchedEffect(task.pickListId, openStagingDirectly) {
        if (openStagingDirectly) {
            loading = true
            isSaving = true
            runCatching {
                totesCatalog = repo.fetchToteCatalog(task.pickListId)
                totesCatalog.firstOrNull { it.isToteActive }?.resolvedToteId?.takeIf { it > 0 }?.let {
                    activeToteIdState = it
                }
                if (totesCatalog.firstOrNull { it.isToteActive }?.isFilledOrStaged != true) {
                    runCatching { repo.fillActiveTote(task.pickListId) }
                    totesCatalog = repo.fetchToteCatalog(task.pickListId)
                }
                productGroups = repo.fetchPickListTasks(task.pickListId, companyId)
            }.onFailure {
                showApiError(it.message ?: "Failed to prepare staging.", isLoad = true)
            }
            loading = false
            isSaving = false
            showStagingSheet = true
        } else {
            refresh()
            loadTotesCatalog()
        }
    }
    

    fun nextTote(newToteNumber: String) {
        if (isSaving || newToteNumber.isBlank()) return
        scope.launch {
            isSaving = true
            saveError = null
            val pickerId = WmsSession.userId(session)
            runCatching {
                val response = repo.assignTote(
                    task.pickListId,
                    newToteNumber.trim(),
                    pickerId,
                    markPreviousFilled = false,
                )
                response.resolvedToteId?.takeIf { it > 0 }?.let { activeToteIdState = it }
                currentTote = newToteNumber.trim().uppercase()
                showTotePickerSheet = false
                newToteInput = ""
                loadTotesCatalog()
            }.onFailure {
                showApiError(it.message ?: "Could not switch tote.")
            }
            isSaving = false
        }
    }

    fun switchToExistingTote(tote: ToteCatalogItem) {
        if (isSaving) return
        val toteNum = tote.toteNumber.trim().uppercase()
        if (toteNum.isBlank()) return
        scope.launch {
            isSaving = true
            saveError = null
            val pickerId = WmsSession.userId(session)
            runCatching {
                val response = repo.assignTote(
                    task.pickListId,
                    toteNum,
                    pickerId,
                    markPreviousFilled = false,
                )
                response.resolvedToteId?.takeIf { it > 0 }?.let { activeToteIdState = it }
                currentTote = toteNum
                showTotePickerSheet = false
                loadTotesCatalog()
            }.onFailure {
                showApiError(it.message ?: "Could not switch tote.")
            }
            isSaving = false
        }
    }

    fun applyProductGroupBatchUpdate(
        batchItem: FefoBatchItem,
        transform: (FefoBatchItem) -> FefoBatchItem,
    ) {
        productGroups = productGroups.map { group ->
            group.copy(
                batches = group.batches.map { b ->
                    if (b.matchesBatch(batchItem)) transform(b) else b
                },
            ).let { updated ->
                updated.copy(
                    totalPicked = updated.batches.sumOf { it.resolvedPickedQty },
                    status = if (updated.batches.all { it.isPickerHandled }) {
                        "picked"
                    } else {
                        updated.status
                    },
                )
            }
        }
    }

    fun confirmBatch(
        batchItem: FefoBatchItem,
        qty: Int,
        scannedGtin: String?,
        scannedBatch: String?,
    ) {
        if (isSaving) return
        scope.launch {
            isSaving = true
            saveError = null
            if (companyId <= 0) {
                showApiError("Company is not set.")
                isSaving = false
                return@launch
            }
            val pickerId = WmsSession.userId(session)
            val toteId = resolveActiveToteId()
            if (toteId <= 0) {
                showApiError("Active tote id is missing. Go back and assign a tote again.")
                isSaving = false
                return@launch
            }
            val taskId = batchItem.resolvedTaskIdString
            if (taskId.isBlank()) {
                showApiError("Task id is missing for this batch.")
                isSaving = false
                return@launch
            }
            val batchKey = batchPickStateKey(productGroups, batchItem)
            runCatching {
                repo.confirmPickTask(
                    taskId = taskId,
                    pickerId = pickerId,
                    pickedQty = qty,
                    toteId = toteId,
                    scannedGtin = scannedGtin,
                    scannedBatch = scannedBatch,
                    actualBin = batchItem.resolvedLocation.ifBlank { null },
                )
                applyProductGroupBatchUpdate(batchItem) { b ->
                    val newPickedQty = qty
                    val shortfall = maxOf(0, b.resolvedRequestedQty - newPickedQty)
                    b.copy(
                        pickedQty = newPickedQty,
                        shortfallQty = shortfall.takeIf { it > 0 } ?: b.shortfallQty,
                        exceptionTypes = b.exceptionTypes,
                        exceptionDetails = b.exceptionDetails,
                        exceptionNotes = b.exceptionNotes,
                        status = "picked",
                        exceptionMessage = b.exceptionMessage,
                    )
                }
                val existingDetails = localBatchPickStates[batchKey]?.exceptionDetails
                    ?: batchItem.resolvedExceptionDetails
                val existingExceptionQty = existingDetails.sumOf { it.qty }
                localBatchPickStates = localBatchPickStates + (
                    batchKey to LocalBatchPickState(
                        pickedQty = qty,
                        exceptionMessage = if (qty + existingExceptionQty >= batchItem.resolvedRequestedQty) {
                            ""
                        } else {
                            batchItem.exceptionMessage
                        },
                        exceptionDetails = existingDetails,
                    )
                )
                scanTargetBatch = null
            }.onFailure {
                showApiError(it.message ?: "Could not save pick.")
            }
            isSaving = false
        }
    }

    fun reportBatchException(
        batchItem: FefoBatchItem,
        qtyFound: Int,
        exceptionQty: Int,
        exceptionDetails: List<PickerExceptionDetail>,
        notes: String?,
    ) {
        println("=== reportBatchException ===")
        println("qtyFound=$qtyFound exceptionQty=$exceptionQty details=$exceptionDetails notes=$notes")
        println("taskId=${batchItem.resolvedTaskIdString} companyId=$companyId")
        if (isSaving) return
        scope.launch {
            isSaving = true
            saveError = null
            if (companyId <= 0) {
                showApiError("Company is not set.")
                isSaving = false
                return@launch
            }
            val pickerId = WmsSession.userId(session)
            val batchKey = batchPickStateKey(productGroups, batchItem)
            val normalizedDetails = normalizePickerExceptionDetails(
                details = exceptionDetails,
                exceptionQty = exceptionQty,
                fallbackType = exceptionDetails.firstOrNull()?.type ?: "SHORTAGE",
            )
            val exceptionTypes = normalizedDetails.map { it.type }
            val effectiveExceptionQty = normalizedDetails.sumOf { it.qty }
            val taskId = batchItem.resolvedTaskIdString
            if (taskId.isBlank()) {
                showApiError("Task id is missing for this batch.")
                isSaving = false
                return@launch
            }
            runCatching {
                println("=== CALLING reportPickException API ===")
                println("taskId=$taskId exceptionTypes=$exceptionTypes qtyFound=$qtyFound notes=$notes")
                repo.reportPickException(
                    taskId = taskId,
                    companyId = companyId,
                    pickerId = pickerId,
                    exceptionTypes = exceptionTypes,
                    qtyFound = qtyFound,
                    notes = notes,
                )
                println("=== reportPickException API SUCCESS ===")
                val exMsg = formatPickerExceptionSummary(normalizedDetails, notes)
                val newPickedQty = qtyFound.coerceIn(0, batchItem.resolvedRequestedQty)
                localBatchPickStates = localBatchPickStates + (
                    batchKey to LocalBatchPickState(
                        pickedQty = newPickedQty,
                        exceptionMessage = exMsg,
                        exceptionDetails = normalizedDetails,
                    )
                )
                applyProductGroupBatchUpdate(batchItem) { b ->
                    b.copy(
                        pickedQty = newPickedQty,
                        shortfallQty = effectiveExceptionQty.takeIf { it > 0 },
                        exceptionTypes = exceptionTypes,
                        exceptionDetails = normalizedDetails,
                        exceptionNotes = notes,
                        status = "picked",
                        exceptionMessage = exMsg,
                    )
                }
                scanTargetBatch = null
            }.onFailure {
                println("=== reportPickException API FAILURE === ${it.message}")
                showApiError(it.message ?: "Could not save exception report.")
            }
            isSaving = false
        }
    }

    fun isActiveToteFilled(): Boolean =
        totesCatalog.firstOrNull { it.isToteActive }?.isFilledOrStaged == true

    fun openStagingFlow() {
        if (isActiveToteFilled() || currentTote.isBlank()) {
            showStagingSheet = true
        } else {
            showFillToteConfirm = true
        }
    }

    fun submitStaging(skipLocation: Boolean, locationName: String?) {
        scope.launch {
            isSaving = true
            saveError = null
            if (companyId <= 0) {
                showApiError("Company is not set.")
                isSaving = false
                return@launch
            }
            runCatching {
                if (allComplete() && !isActiveToteFilled()) {
                    runCatching { repo.fillActiveTote(task.pickListId) }
                    loadTotesCatalog()
                }
                val needsStagingLocation = !task.status.equals("STAGED", ignoreCase = true)
                if (!skipLocation && needsStagingLocation) {
                    val trimmed = locationName?.trim().orEmpty()
                    if (trimmed.isEmpty()) {
                        error("Enter a staging location.")
                    }
                    repo.stageIndustryPickList(task.pickListId, companyId, trimmed)
                }
                repo.completeIndustryPickList(task.pickListId)
                onFinished()
            }.onFailure {
                showApiError(it.message ?: "Could not save staging.")
            }
            isSaving = false
        }
    }

    fun fillAndGoToStaging() {
        if (isSaving) return
        scope.launch {
            isSaving = true
            saveError = null
            runCatching {
                if (!isActiveToteFilled() && currentTote.isNotBlank()) {
                    runCatching { repo.fillActiveTote(task.pickListId) }
                        .onFailure { /* iOS skips fill errors when tote state is already synced */ }
                    loadTotesCatalog()
                }
                showFillToteConfirm = false
                showStagingSheet = true
            }.onFailure {
                showApiError(it.message ?: "Could not fill tote.")
            }
            isSaving = false
        }
    }

    BackHandler {
        if (scanTargetBatch != null) {
            scanTargetBatch = null
        } else if (showStagingSheet) {
            if (openStagingDirectly) onBack() else showStagingSheet = false
        } else {
            onBack()
        }
    }

    if (openStagingDirectly && loading) {
        Box(
            modifier = Modifier.fillMaxSize().background(WmsColors.PageBgAlt),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = WmsColors.Navy, strokeWidth = 2.dp)
                Text("Preparing staging…", fontSize = 14.sp, color = WmsColors.TextSecondary)
            }
        }
        return
    }

    val firstUnpickedBatchKey = remember(displayProductGroups) {
        firstUnpickedBatchKey(displayProductGroups)
    }
    val firstUnpickedGroupIndex = remember(displayProductGroups) {
        firstUnpickedGroupIndex(displayProductGroups)
    }
    val lazyListState = rememberLazyListState()

    LaunchedEffect(firstUnpickedGroupIndex, loading, scanTargetBatch, displayProductGroups) {
        if (!loading && scanTargetBatch == null && firstUnpickedGroupIndex >= 0) {
            delay(150)
            lazyListState.animateScrollToItem(1 + firstUnpickedGroupIndex)
        }
    }

    scanTargetBatch?.let { batchItem ->
        PickerBatchScanLine(
            task = task,
            productGroup = displayProductGroups.findGroupContaining(batchItem),
            batchItem = batchItem,
            isSaving = isSaving,
            onBack = { scanTargetBatch = null },
            onConfirmPick = { qty, scannedGtin, scannedBatch ->
                confirmBatch(batchItem, qty, scannedGtin, scannedBatch)
            },
            onReportException = { qtyFound, exceptionQty, exceptionDetails, notes ->
                reportBatchException(batchItem, qtyFound, exceptionQty, exceptionDetails, notes)
            },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        BatchScanGridBackground()
        Column(Modifier.fillMaxSize()) {
            PickerBatchScanHeroHeader(
                pickListId = task.pickListCode ?: task.pickListId,
                waveLabel = task.waveLabel,
                onBack = onBack,
            )

            if (!loading && displayProductGroups.isNotEmpty()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp, end = 16.dp, top = 12.dp, bottom = 100.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        PickerBatchScanProgressCard(
                            progressPercent = (progressFraction * 100).toInt(),
                            completedBatches = pickedBatches,
                            totalBatches = totalBatches,
                            completedSkus = completedSkus,
                            totalSkus = totalSkus,
                            progressFraction = progressFraction,
                            modifier = Modifier.padding(horizontal = 0.dp),
                        )
                    }
                    item {
                        PickerBatchScanSummaryCard(
                            skuCount = totalSkus,
                            batchCount = totalBatches,
                            pickedCount = pickedBatches,
                            totalCount = totalBatches,
                            toteNumber = resolvedTote,
                            onSwitchTote = { showTotePickerSheet = true },
                        )
                    }
                    itemsIndexed(
                        items = displayProductGroups,
                        key = { _, group -> group.key.ifBlank { group.sku + group.productName } },
                    ) { _, group ->
                        PickerBatchScanProductCard(
                            group = group,
                            activeToteNumber = resolvedTote,
                            firstUnpickedBatchKey = firstUnpickedBatchKey,
                            onScanBatch = { batchItem ->
                                scanTargetBatch = batchItem
                            },
                        )
                    }
                }
            } else if (loading && productGroups.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(color = WmsColors.Navy, strokeWidth = 2.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("Loading pick items…", fontSize = 14.sp, color = WmsColors.TextSecondary)
                }
            } else if (productGroups.isEmpty() && loadError != null) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Could not load items", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Retry",
                        color = WmsColors.Navy,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { refresh() },
                    )
                }
            }
        }

        if (allComplete() && !showStagingSheet && displayProductGroups.isNotEmpty()) {
            PickerGoToStagingBottomBar(
                onClick = ::openStagingFlow,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    WmsRichErrorSheet(
        visible = showSaveErrorPopup,
        title = "Could not save",
        message = saveError.orEmpty(),
        onDismiss = { showSaveErrorPopup = false },
    )
    WmsRichErrorSheet(
        visible = showLoadErrorPopup,
        title = "Could not load items",
        message = loadError.orEmpty(),
        onDismiss = {
            showLoadErrorPopup = false
            refresh()
        },
    )

    TotePickerSheet(
        visible = showTotePickerSheet,
        currentTote = currentTote,
        totesCatalog = totesCatalog,
        newToteInput = newToteInput,
        onNewToteInputChange = { newToteInput = it.uppercase() },
        onSelectExistingTote = ::switchToExistingTote,
        onCreateNewTote = { nextTote(newToteInput) },
        onDismiss = {
            showTotePickerSheet = false
            newToteInput = ""
        },
        loading = isSaving,
    )

    WmsRichConfirmSheet(
        visible = showFillToteConfirm,
        title = "Fill Active Tote",
        message = buildString {
            append("Tote ")
            append(currentTote.ifBlank { "—" })
            append(" will be marked as filled, then you can complete the pick list.")
        },
        icon = Icons.Default.Inventory2,
        iconTint = Color(0xFF16A34A),
        iconBackground = Color(0xFF16A34A).copy(alpha = 0.12f),
        primaryLabel = "Confirm & Complete",
        onPrimary = ::fillAndGoToStaging,
        onDismiss = { showFillToteConfirm = false },
        loading = isSaving,
        primaryColor = Color(0xFF16A34A),
    )

    val stagingTotalBatches = totalBatches.takeIf { it > 0 } ?: task.batchCount
    val stagingPickedBatches = pickedBatches.takeIf { it > 0 } ?: task.batchCount
    PickerCompleteStagingBottomSheet(
        visible = showStagingSheet,
        task = task,
        pickListId = task.pickListCode ?: task.pickListId,
        productGroups = displayProductGroups,
        toteNumber = resolvedTote,
        totalBatches = stagingTotalBatches,
        pickedBatches = stagingPickedBatches,
        companyId = companyId,
        isSaving = isSaving,
        onDismiss = {
            if (openStagingDirectly) onBack() else showStagingSheet = false
        },
        onComplete = ::submitStaging,
    )
}

// ── Fixed staging CTA ────────────────────────────────────────────────────────

@Composable
private fun PickerGoToStagingBottomBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(12.dp, spotColor = Color.Black.copy(alpha = 0.08f))
            .background(Color.White)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
        ) {
            Icon(Icons.Default.Inventory2, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text("Complete Pick List", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(20.dp))
        }
    }
}

// ── Hero header ──────────────────────────────────────────────────────────────

@Composable
private fun PickerBatchScanHeroHeader(
    pickListId: String,
    waveLabel: String?,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().background(Color.White)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            WmsCircularBackButton(onClick = onBack)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    pickListId,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                waveLabel?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        it,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF374151),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF3F4F6))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = WmsColors.Border)
    }
}

@Composable
private fun PickerBatchScanProgressCard(
    progressPercent: Int,
    completedBatches: Int,
    totalBatches: Int,
    completedSkus: Int,
    totalSkus: Int,
    progressFraction: Float,
    modifier: Modifier = Modifier,
) {
    val progress = progressFraction.coerceIn(0f, 1f)
    val isComplete = progressPercent >= 100
    val accent = if (isComplete) WmsColors.Success else WmsColors.Navy
    val cardBg = if (isComplete) Color(0xFFECFDF5) else Color(0xFFEFF6FF)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max)
            .shadow(4.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(1.5.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp)),
    ) {
        Box(
            Modifier
                .width(5.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(76.dp),
                        strokeWidth = 7.dp,
                        color = accent,
                        trackColor = Color.White.copy(alpha = 0.85f),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$progressPercent%",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                    }
                }

                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (isComplete) Icons.Default.CheckCircle else Icons.Default.Inventory2,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Overall Progress",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = WmsColors.TextPrimary,
                        )
                    }
                    Text(
                        if (isComplete) "All batches picked" else "Keep picking to complete this list",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = WmsColors.TextSecondary,
                        lineHeight = 16.sp,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PickerBatchProgressStatChip(
                    label = "Batches",
                    value = "$completedBatches/$totalBatches",
                    accent = accent,
                    modifier = Modifier.weight(1f),
                )
                if (totalSkus > 0) {
                    PickerBatchProgressStatChip(
                        label = "SKUs",
                        value = "$completedSkus/$totalSkus",
                        accent = accent,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Picking progress", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextMuted)
                    Text(
                        if (isComplete) "Complete" else "$completedBatches of $totalBatches done",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                    )
                }
                PickerBatchScanRichProgressBar(fraction = progress, accent = accent)
            }
        }
    }
}

@Composable
private fun PickerBatchProgressStatChip(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.72f))
            .border(1.dp, accent.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextMuted, letterSpacing = 0.5.sp)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accent, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun PickerBatchScanRichProgressBar(
    fraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val progress = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.7f)),
    ) {
        if (progress > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(progress)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(accent, accent.copy(alpha = 0.72f)),
                        ),
                    ),
            )
        }
    }
}

// ── Tote banner ──────────────────────────────────────────────────────────────

@Composable
private fun PickerBatchScanToteBanner(
    toteNumber: String,
    onNextTote: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WmsColors.Navy.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
            .border(1.dp, WmsColors.Navy.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Inventory2,
            contentDescription = null,
            tint = WmsColors.Navy,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Active Tote", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextSecondary)
            Text(
                toteNumber,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = WmsColors.Navy,
            )
        }
        Button(
            onClick = onNextTote,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(32.dp),
        ) {
            Text("Next Tote", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// ── Totes catalog ────────────────────────────────────────────────────────────

@Composable
private fun PickerTotesCatalogCard(totes: List<ToteCatalogItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, WmsColors.Border, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "TOTES",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = WmsColors.TextMuted,
            letterSpacing = 0.8.sp,
        )
        totes.forEach { tote ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (tote.isToteActive) WmsColors.Success else WmsColors.TextMuted),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    tote.toteNumber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = WmsColors.TextPrimary,
                )
                if (tote.isToteActive) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "ACTIVE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WmsColors.Success,
                        modifier = Modifier
                            .background(WmsColors.SuccessBg, RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (tote.batchCount > 0) {
                    Text(
                        "${tote.batchCount} batches",
                        fontSize = 11.sp,
                        color = WmsColors.TextSecondary,
                    )
                }
            }
        }
    }
}

// ── Summary card ─────────────────────────────────────────────────────────────

@Composable
private fun PickerBatchScanSummaryCard(
    skuCount: Int,
    batchCount: Int,
    pickedCount: Int,
    totalCount: Int,
    toteNumber: String,
    onSwitchTote: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(14.dp))
            .border(1.dp, WmsColors.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PickerBatchStatCell(label = "SKUs", value = "$skuCount", color = WmsColors.Navy)
            PickerBatchStatCell(label = "Batches", value = "$batchCount", color = WmsColors.ActiveBlue)
            PickerBatchStatCell(label = "Picked", value = "$pickedCount/$totalCount", color = WmsColors.Success)
            PickerBatchStatCell(
                label = "Tote",
                value = toteNumber.ifEmpty { "—" },
                color = WmsColors.Warning,
            )
        }
        Button(
            onClick = onSwitchTote,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = WmsColors.Navy.copy(alpha = 0.08f),
                disabledContainerColor = WmsColors.Navy.copy(alpha = 0.04f),
            ),
        ) {
            Icon(
                Icons.Default.Inventory2,
                contentDescription = null,
                tint = WmsColors.Navy,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Manage Totes",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = WmsColors.Navy,
            )
        }
    }
}

@Composable
private fun PickerBatchStatCell(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextMuted)
    }
}

// ── Product group card ───────────────────────────────────────────────────────

@Composable
private fun PickerBatchScanProductCard(
    group: FefoProductGroup,
    activeToteNumber: String,
    firstUnpickedBatchKey: String?,
    onScanBatch: (FefoBatchItem) -> Unit,
) {
    val isComplete = group.isComplete
    val hasPartialPicked = group.batches.any { it.isPartialPicked }
    val accentColor = when {
        isComplete && !hasPartialPicked -> WmsColors.Success
        hasPartialPicked -> Color(0xFFD97706)
        else -> WmsColors.Navy
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(
                1.dp,
                if (isComplete) WmsColors.SuccessBorder else WmsColors.Border,
                RoundedCornerShape(14.dp),
            ),
    ) {
        // Product header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(accentColor.copy(alpha = 0.06f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    group.productName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SKU: ${group.sku}",
                        fontSize = 11.sp,
                        color = WmsColors.TextSecondary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("·", fontSize = 11.sp, color = WmsColors.TextMuted)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "GTIN: ${group.gtin}",
                        fontSize = 11.sp,
                        color = WmsColors.TextSecondary,
                        maxLines = 1,
                    )
                }
            }
            if (isComplete && !hasPartialPicked) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CheckCircle, null, tint = WmsColors.Success, modifier = Modifier.size(22.dp))
                    Text("Done", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = WmsColors.Success)
                }
            } else if (hasPartialPicked) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠", fontSize = 16.sp)
                    Text("Partial", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${group.pickedBatchCount}/${group.effectiveBatchCount}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                    Text("batches", fontSize = 9.sp, color = WmsColors.TextMuted)
                }
            }
        }

        // Batch rows
        group.batches.forEachIndexed { index, batch ->
            PickerBatchScanRow(
                batch = batch,
                batchIndex = index,
                totalBatches = group.batches.size,
                isGroupComplete = isComplete,
                toteNumber = activeToteNumber,
                isFirstUnpicked = firstUnpickedBatchKey != null && batch.stateKey() == firstUnpickedBatchKey,
                onScan = { onScanBatch(batch) },
            )
        }
    }
}

// ── Batch row ────────────────────────────────────────────────────────────────

@Composable
private fun PickerBatchScanRow(
    batch: FefoBatchItem,
    batchIndex: Int,
    totalBatches: Int,
    isGroupComplete: Boolean,
    toteNumber: String,
    isFirstUnpicked: Boolean,
    onScan: () -> Unit,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val isBatchPicked = batch.isPickerHandled
    val hasException = batch.hasException
    val isPartialPicked = batch.isPartialPicked
    val isInProgress = !isBatchPicked && batch.status.equals("in_progress", ignoreCase = true)
    val partialAccent = Color(0xFFD97706)
    val rowAccent = when {
        isPartialPicked -> partialAccent
        isBatchPicked -> WmsColors.Success
        isInProgress -> WmsColors.ActiveBlue
        else -> Color.Transparent
    }

    LaunchedEffect(isFirstUnpicked, isBatchPicked) {
        if (isFirstUnpicked && !isBatchPicked) {
            delay(200)
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .then(
                if (isFirstUnpicked && !isBatchPicked) {
                    Modifier.border(1.5.dp, WmsColors.ActiveBlue.copy(alpha = 0.55f), RoundedCornerShape(0.dp))
                } else {
                    Modifier.border(0.5.dp, WmsColors.Border)
                },
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Accent stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(IntrinsicSize.Min)
                    .background(rowAccent),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                // Top row: batch sequence + status
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        batch.batchLabel.trim().takeIf { it.isNotBlank() }
                            ?: "Batch ${batchIndex + 1} of $totalBatches",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WmsColors.TextMuted,
                        letterSpacing = 0.4.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    if (isBatchPicked) {
                        if (isPartialPicked) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(partialAccent.copy(alpha = 0.12f))
                                    .border(1.dp, partialAccent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    "Partial Picked",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = partialAccent,
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = WmsColors.Success, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    "Picked",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WmsColors.Success,
                                )
                            }
                        }
                    } else if (isInProgress) {
                        Text("In Progress", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WmsColors.ActiveBlue)
                    }
                }

                // Batch code only (product name shown in card header)
                Text(
                    batch.resolvedBatch,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = WmsColors.Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Location + Tote + Qty row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Location badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(WmsColors.Navy.copy(alpha = 0.07f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = WmsColors.Navy,
                            modifier = Modifier.size(11.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            batch.resolvedLocation,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = WmsColors.Navy,
                        )
                    }

                    if (toteNumber.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color(0xFF16A34A).copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        ) {
                            Text("🧺", fontSize = 10.sp)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                toteNumber,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF15803D),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    // Quantity pill
                    val qtyColor = when {
                        isPartialPicked -> partialAccent
                        isBatchPicked -> WmsColors.Success
                        isInProgress -> WmsColors.ActiveBlue
                        else -> WmsColors.TextSecondary
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(qtyColor.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "${batch.resolvedPickedQty}/${batch.resolvedRequestedQty}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = qtyColor,
                        )
                    }
                }

                val exceptionEntries = batch.displayExceptionEntries()
                val exceptionMessageText = batch.resolvedExceptionMessage()
                if (exceptionEntries.isNotEmpty() || exceptionMessageText.isNotBlank()) {
                    PickerBatchExceptionBreakdown(
                        entries = exceptionEntries.ifEmpty {
                            listOf(
                                PickerExceptionDetail(
                                    type = batch.exceptionTypes.firstOrNull()
                                        ?: batch.exceptionTypesSnake?.firstOrNull()
                                        ?: "OTHER",
                                    qty = maxOf(0, batch.resolvedRequestedQty - batch.resolvedPickedQty)
                                        .coerceAtLeast(1),
                                ),
                            )
                        },
                        message = exceptionMessageText.takeIf { it.isNotBlank() },
                    )
                } else if (isPartialPicked || hasException) {
                    val fallbackQty = maxOf(0, batch.resolvedRequestedQty - batch.resolvedPickedQty)
                    val fallbackType = batch.exceptionTypes.firstOrNull()
                        ?: batch.exceptionTypesSnake?.firstOrNull()
                        ?: "OTHER"
                    if (fallbackQty > 0) {
                        PickerBatchExceptionBreakdown(
                            entries = listOf(
                                PickerExceptionDetail(
                                    type = fallbackType,
                                    qty = fallbackQty.coerceAtLeast(1),
                                ),
                            ),
                        )
                    }
                }

                if (!isBatchPicked && !isGroupComplete) {
                    PickerBatchScanRichButton(
                        onClick = onScan,
                        isInProgress = isInProgress,
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerBatchScanRichButton(
    onClick: () -> Unit,
    isInProgress: Boolean,
) {
    val gradient = if (isInProgress) {
        listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))
    } else {
        listOf(WmsColors.Navy, Color(0xFF1E3A5F))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(gradient))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.QrCodeScanner,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Scan & Verify Batch",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Text(
                "Point camera at batch barcode",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.88f),
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}

// ── Scan line screen ─────────────────────────────────────────────────────────

@Composable
private fun PickerBatchScanLine(
    task: PickerMyListTask,
    productGroup: FefoProductGroup?,
    batchItem: FefoBatchItem,
    isSaving: Boolean,
    onBack: () -> Unit,
    onConfirmPick: (Int, String?, String?) -> Unit,
    onReportException: (Int, Int, List<PickerExceptionDetail>, String?) -> Unit,
) {
    val batch = batchItem.resolvedBatch
    val gtin = productGroup?.gtin ?: ""

    var isScanning by remember(batchItem.taskId) { mutableStateOf(true) }
    var productVerified by remember(batchItem.taskId) { mutableStateOf(false) }
    var verifiedGtin by remember(batchItem.taskId) { mutableStateOf("") }
    var verifiedBatch by remember(batchItem.taskId) { mutableStateOf("") }
    var pendingQty by remember(batchItem.taskId) {
        val remaining = batchItem.remainingPickQty
        mutableIntStateOf(if (remaining > 0) remaining else 1)
    }
    var showManualBatch by remember(batchItem.taskId) { mutableStateOf(false) }
    var manualBatch by remember(batchItem.taskId) { mutableStateOf("") }
    var scanError by remember(batchItem.taskId) { mutableStateOf<String?>(null) }
    var partialCompleteQty by remember(batchItem.taskId) { mutableStateOf<Int?>(null) }
    var showExceptionDialog by remember(batchItem.taskId) { mutableStateOf(false) }
    var exceptionQtyFound by remember(batchItem.taskId) { mutableStateOf("") }
    var exceptionTypes by remember(batchItem.taskId) { mutableStateOf(setOf<String>()) }
    var exceptionNotes by remember(batchItem.taskId) { mutableStateOf("") }

    val remainingQty = batchItem.remainingPickQty
    val resolvedPendingQty = if (remainingQty <= 0) {
        0
    } else {
        minOf(remainingQty, maxOf(1, pendingQty))
    }

    fun decreasePendingQty() {
        if (remainingQty <= 0) return
        pendingQty = maxOf(1, minOf(remainingQty, pendingQty) - 1)
    }

    fun increasePendingQty() {
        if (remainingQty <= 0) return
        pendingQty = minOf(remainingQty, maxOf(1, pendingQty) + 1)
    }

    fun markVerified(scannedGtin: String = gtin, scannedBatch: String = batch) {
        scanError = null
        productVerified = true
        isScanning = false
        verifiedGtin = scannedGtin
        verifiedBatch = scannedBatch
        pendingQty = if (remainingQty > 0) remainingQty else 1
    }

    fun handleBarcodeScan(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty() || productVerified) return
        when {
            batch.isBlank() && gtin.isBlank() -> {
                scanError = "GTIN or batch missing on this line."
                isScanning = true
                return
            }
            gtin.isNotBlank() -> {
                if (!productMatchesScan(trimmed, gtin, batch)) {
                    scanError = if (batch.isBlank()) {
                        "GTIN does not match this product."
                    } else {
                        "GTIN or batch does not match this product."
                    }
                    isScanning = true
                    return
                }
            }
            else -> {
                if (!batchMatchesScan(trimmed, batch)) {
                    scanError = "Batch does not match."
                    isScanning = true
                    return
                }
            }
        }
        markVerified(scannedGtin = gtin, scannedBatch = batch)
    }

    fun submitManualBatch() {
        val trimmed = manualBatch.trim()
        if (trimmed.isEmpty()) return
        when {
            batch.isBlank() -> {
                if (gtin.isBlank()) {
                    scanError = "GTIN or batch missing on this line."
                    return
                }
                if (!gtinMatchesScan(trimmed, gtin)) {
                    scanError = "GTIN does not match this product."
                    return
                }
            }
            else -> {
                if (!batchMatchesScan(trimmed, batch)) {
                    scanError = "Batch does not match."
                    return
                }
            }
        }
        showManualBatch = false
        manualBatch = ""
        markVerified(scannedGtin = gtin, scannedBatch = batch)
    }

    Column(Modifier.fillMaxSize().background(WmsColors.PageBgAlt)) {
        Column(Modifier.fillMaxWidth().background(Color.White)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                WmsCircularBackButton(onClick = onBack)
                Text(
                    task.pickListCode ?: task.pickListId,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.Navy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            HorizontalDivider(color = WmsColors.Border)
        }

        if (!productVerified && !showManualBatch) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, WmsColors.Navy.copy(alpha = 0.15f), RoundedCornerShape(16.dp)),
            ) {
                WarehouseMlKitScanner(
                    enabled = isScanning && !isSaving,
                    onBarcodeScanned = { handleBarcodeScan(it) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(14.dp))
                    .border(1.dp, WmsColors.Border, RoundedCornerShape(14.dp))
                    .background(Color.White, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "SCAN TO VERIFY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.6.sp,
                    color = Color(0xFF334155),
                )
                Text(
                    productGroup?.productName ?: "",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WmsColors.TextPrimary,
                )
                Text("SKU: ${productGroup?.sku ?: ""}", fontSize = 13.sp, color = Color(0xFF4B5563))
                Text("GTIN: ${gtin.ifBlank { "—" }}", fontSize = 13.sp, color = Color(0xFF4B5563))
                Text("Batch: ${batch.ifBlank { "—" }}", fontSize = 13.sp, color = Color(0xFF4B5563))
                if (batchItem.resolvedPickedQty > 0) {
                    Text(
                        "Picked: ${batchItem.resolvedPickedQty} / ${batchItem.resolvedRequestedQty}",
                        fontSize = 13.sp,
                        color = WmsColors.ActiveBlue,
                    )
                }
                Text("Required: $remainingQty", fontSize = 13.sp, color = Color(0xFF4B5563))
            }

            if (showManualBatch) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Enter Batch", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF334155))
                    OutlinedTextField(
                        value = manualBatch,
                        onValueChange = { manualBatch = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Type batch code", color = WmsColors.TextMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFFF8FAFC),
                            unfocusedContainerColor = Color(0xFFF8FAFC),
                        ),
                        shape = RoundedCornerShape(10.dp),
                    )
                    Button(
                        onClick = ::submitManualBatch,
                        enabled = manualBatch.trim().isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (manualBatch.trim().isNotEmpty()) WmsColors.Navy else Color.Gray,
                        ),
                    ) {
                        Text("Verify Batch", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (productVerified) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "VERIFIED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 2.2.sp,
                            color = WmsColors.Success,
                        )
                        Text(
                            productGroup?.productName ?: "",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1F2937),
                            maxLines = 2,
                        )
                        Text(
                            "GTIN $gtin · Batch $batch",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF64748B),
                        )
                    }
                    Icon(Icons.Default.CheckCircle, null, tint = WmsColors.Success, modifier = Modifier.size(28.dp))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Enter pick quantity", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextPrimary)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .wmsRepeatClickable(
                                    enabled = resolvedPendingQty > 1 && remainingQty > 0,
                                    onClick = { decreasePendingQty() },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.RemoveCircle,
                                contentDescription = "Decrease quantity",
                                tint = WmsColors.Navy,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        OutlinedTextField(
                            value = if (remainingQty <= 0) "0" else resolvedPendingQty.toString(),
                            onValueChange = { raw ->
                                val parsed = raw.filter { it.isDigit() }.toIntOrNull()
                                pendingQty = when {
                                    remainingQty <= 0 -> 1
                                    parsed == null || parsed <= 0 -> 1
                                    else -> minOf(remainingQty, parsed)
                                }
                            },
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
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .wmsRepeatClickable(
                                    enabled = resolvedPendingQty < remainingQty && remainingQty > 0,
                                    onClick = { increasePendingQty() },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.AddCircle,
                                contentDescription = "Increase quantity",
                                tint = WmsColors.Navy,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Text(
                        "Remaining: $remainingQty",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = WmsColors.TextSecondary,
                    )
                }

                Button(
                    onClick = {
                        val add = resolvedPendingQty
                        if (add > 0) {
                            val newTotal = minOf(batchItem.resolvedRequestedQty, batchItem.resolvedPickedQty + add)
                            if (newTotal < batchItem.resolvedRequestedQty) {
                                partialCompleteQty = newTotal
                            } else {
                                onConfirmPick(newTotal, verifiedGtin, verifiedBatch)
                            }
                        }
                    },
                    enabled = remainingQty > 0 && !isSaving,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (remainingQty > 0) WmsColors.Navy else Color.Gray,
                    ),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isSaving) "Saving…" else "Confirm Pick", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            if (!productVerified) {
                OutlinedButton(
                    onClick = {
                        scanError = null
                        showManualBatch = !showManualBatch
                        isScanning = showManualBatch.not()
                    },
                    enabled = !productVerified,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(2.dp, WmsColors.Navy),
                ) {
                    Icon(
                        if (showManualBatch) Icons.Default.QrCodeScanner else Icons.Default.Keyboard,
                        null,
                        tint = Color(0xFF334155),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (showManualBatch) "Resume Scanning" else "Manual Entry",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF334155),
                    )
                }
            }
        }
    }

    WmsRichErrorSheet(
        visible = scanError != null,
        title = if (scanError?.contains("Batch", ignoreCase = true) == true) "Invalid Batch" else "Verification failed",
        message = scanError.orEmpty(),
        onDismiss = { scanError = null },
    )

    partialCompleteQty?.let { qty ->
        AlertDialog(
            onDismissRequest = { partialCompleteQty = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFF59E0B).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⚠️", fontSize = 28.sp)
                }
            },
            title = {
                Text(
                    "Partial Pick",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.TextPrimary,
                    textAlign = TextAlign.Center,
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        productGroup?.productName ?: "Unknown Product",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WmsColors.TextPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Batch: $batch",
                        fontSize = 12.sp,
                        color = WmsColors.TextMuted,
                        textAlign = TextAlign.Center,
                    )

                    // Qty visual card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = 0.08f))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(14.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("Quantity Picked", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = WmsColors.TextMuted)
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    "$qty",
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B),
                                )
                                Text(
                                    " / ${batchItem.resolvedRequestedQty}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = WmsColors.TextMuted,
                                    modifier = Modifier.padding(bottom = 4.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            // Progress bar
                            val fraction = if (batchItem.resolvedRequestedQty > 0) qty.toFloat() / batchItem.resolvedRequestedQty else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE5E7EB)),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFF59E0B)),
                                )
                            }
                        }
                    }

                    Text(
                        "You picked $qty of ${batchItem.resolvedRequestedQty} units. How would you like to proceed?",
                        fontSize = 13.sp,
                        color = WmsColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { partialCompleteQty = null },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Cancel", fontSize = 14.sp, color = WmsColors.TextMuted)
                    }
                    Button(
                        onClick = {
                            partialCompleteQty = null
                            exceptionQtyFound = qty.toString()
                            showExceptionDialog = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    ) {
                        Text("Report Issue", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Color.White)
                    }
                }
            },
            dismissButton = {},
        )
    }

    if (showExceptionDialog) {
        val pickedQtyFound = exceptionQtyFound.toIntOrNull()?.coerceAtLeast(0)
            ?: batchItem.resolvedPickedQty
        val remainingShortfall = maxOf(
            0,
            batchItem.resolvedRequestedQty - pickedQtyFound - batchItem.resolvedExceptionQty,
        )
        PickerExceptionReportSheet(
            productName = productGroup?.productName ?: "Unknown Product",
            batchLabel = batch,
            requiredQty = batchItem.resolvedRequestedQty,
            pickedQty = batchItem.resolvedPickedQty,
            pickedQtyFound = pickedQtyFound,
            remainingShortfall = remainingShortfall,
            onDismiss = {
                showExceptionDialog = false
                exceptionTypes = emptySet()
                exceptionNotes = ""
                exceptionQtyFound = ""
            },
            onSubmit = { qtyFound, exceptionQty, exceptionDetails, notes ->
                showExceptionDialog = false
                onReportException(qtyFound, exceptionQty, exceptionDetails, notes)
                exceptionTypes = emptySet()
                exceptionNotes = ""
                exceptionQtyFound = ""
            },
        )
    }
}

// ── Exception report sheet ──────────────────────────────────────────────────

private fun pickerExceptionTypeLabel(type: String): String = when (type.uppercase()) {
    "SHORTAGE" -> "Shortage"
    "DAMAGED" -> "Damaged"
    "BIN_EMPTY" -> "Bin empty"
    "WRONG_BATCH" -> "Wrong batch"
    "NOT_FOUND" -> "Not found"
    "OTHER" -> "Other"
    else -> type.split("_").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
}

private fun pickerExceptionTypeEmoji(type: String): String = when (type.uppercase()) {
    "SHORTAGE" -> "📉"
    "DAMAGED" -> "🔴"
    "BIN_EMPTY" -> "📭"
    "WRONG_BATCH" -> "🔀"
    "NOT_FOUND" -> "🔍"
    "OTHER" -> "📝"
    else -> "ℹ️"
}

private fun formatPickerExceptionSummary(
    details: List<PickerExceptionDetail>,
    notes: String?,
): String {
    val summary = details
        .filter { it.qty > 0 }
        .joinToString(" · ") { "${pickerExceptionTypeLabel(it.type)} ×${it.qty}" }
    if (summary.isBlank()) return notes.orEmpty()
    return if (notes.isNullOrBlank()) summary else "$summary — $notes"
}

@Composable
private fun PickerBatchExceptionBreakdown(
    entries: List<PickerExceptionDetail>,
    message: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "EXCEPTIONS",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFB45309),
            letterSpacing = 0.6.sp,
        )
        entries.filter { it.qty > 0 }.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFF7ED))
                    .border(1.dp, Color(0xFFFDBA74).copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(pickerExceptionTypeEmoji(entry.type), fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        pickerExceptionTypeLabel(entry.type),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF9A3412),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEA580C).copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        "×${entry.qty}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEA580C),
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
        message?.trim()?.takeIf { it.isNotEmpty() }?.let { note ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFFF7ED))
                    .border(1.dp, Color(0xFFFDBA74).copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("ℹ️", fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    note,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF92400E),
                    lineHeight = 15.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

private data class ExceptionIssueDef(
    val key: String,
    val label: String,
    val description: String,
    val emoji: String,
)

@Composable
private fun PickerExceptionRichQtyStepper(
    label: String,
    accentColor: Color,
    qty: Int,
    maxQty: Int,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onValueChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .shadow(3.dp, RoundedCornerShape(14.dp), spotColor = accentColor.copy(alpha = 0.15f))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, accentColor.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "#",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                    )
                }
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WmsColors.TextPrimary,
                )
            }
            Text(
                "Max $maxQty",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.08f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (qty > 0) accentColor.copy(alpha = 0.12f) else WmsColors.Border.copy(alpha = 0.35f))
                    .wmsRepeatClickable(enabled = qty > 0, onClick = onDecrease),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.RemoveCircle,
                    contentDescription = "Decrease quantity",
                    tint = if (qty > 0) accentColor else WmsColors.TextMuted,
                    modifier = Modifier.size(30.dp),
                )
            }
            OutlinedTextField(
                value = qty.toString(),
                onValueChange = { raw ->
                    val parsed = raw.filter { it.isDigit() }.toIntOrNull()
                    onValueChange(
                        when {
                            parsed == null -> 0
                            else -> minOf(maxQty, parsed.coerceAtLeast(0))
                        },
                    )
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = accentColor,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = accentColor.copy(alpha = 0.06f),
                    unfocusedContainerColor = Color(0xFFF8FAFC),
                    focusedBorderColor = accentColor.copy(alpha = 0.45f),
                    unfocusedBorderColor = WmsColors.Border,
                ),
                shape = RoundedCornerShape(12.dp),
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (qty < maxQty && maxQty > 0) {
                            accentColor.copy(alpha = 0.12f)
                        } else {
                            WmsColors.Border.copy(alpha = 0.35f)
                        },
                    )
                    .wmsRepeatClickable(
                        enabled = qty < maxQty && maxQty > 0,
                        onClick = onIncrease,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AddCircle,
                    contentDescription = "Increase quantity",
                    tint = if (qty < maxQty && maxQty > 0) accentColor else WmsColors.TextMuted,
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        if (maxQty > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(WmsColors.Border),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((qty.toFloat() / maxQty).coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(accentColor),
                    )
                }
                Text(
                    "$qty of $maxQty remaining units",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = WmsColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerExceptionReportSheet(
    productName: String,
    batchLabel: String,
    requiredQty: Int,
    pickedQty: Int,
    pickedQtyFound: Int,
    remainingShortfall: Int,
    onDismiss: () -> Unit,
    onSubmit: (qtyFound: Int, exceptionQty: Int, exceptionDetails: List<PickerExceptionDetail>, notes: String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val issueReasons = listOf(
        ExceptionIssueDef("SHORTAGE", "Shortage", "Shortage — fewer units in bin than expected", "📦"),
        ExceptionIssueDef("DAMAGED", "Damaged", "Damaged — carton or packaging compromised", "⚠️"),
        ExceptionIssueDef("BIN_EMPTY", "Bin empty", "Bin empty — no stock found", "📍"),
        ExceptionIssueDef("WRONG_BATCH", "Wrong batch", "Wrong batch / mismatch at bin", "🔁"),
        ExceptionIssueDef("OTHER", "Other", "Other — enter your own reason", "✏️"),
    )

    var selectedTypes by remember { mutableStateOf(setOf<String>()) }
    var shortageQty by remember { mutableIntStateOf(0) }
    var damagedQty by remember { mutableIntStateOf(0) }
    var binEmptyQty by remember { mutableIntStateOf(0) }
    var wrongBatchQty by remember { mutableIntStateOf(0) }
    var otherQty by remember { mutableIntStateOf(0) }
    var otherReasonText by remember { mutableStateOf("") }
    var customNotes by remember { mutableStateOf("") }

    val totalReportedQty = shortageQty + damagedQty + binEmptyQty + wrongBatchQty + otherQty
    val shortageDesc = if (remainingShortfall > 0) {
        "You picked $pickedQtyFound of $requiredQty. $remainingShortfall unit(s) remaining."
    } else {
        "You have picked all $requiredQty units."
    }

    fun otherIssueQty(excludeKey: String): Int = when (excludeKey) {
        "SHORTAGE" -> damagedQty + binEmptyQty + wrongBatchQty + otherQty
        "DAMAGED" -> shortageQty + binEmptyQty + wrongBatchQty + otherQty
        "BIN_EMPTY" -> shortageQty + damagedQty + wrongBatchQty + otherQty
        "WRONG_BATCH" -> shortageQty + damagedQty + binEmptyQty + otherQty
        "OTHER" -> shortageQty + damagedQty + binEmptyQty + wrongBatchQty
        else -> totalReportedQty
    }

    fun maxQtyForIssue(issueKey: String): Int =
        maxOf(0, remainingShortfall - otherIssueQty(issueKey))

    fun toggleIssueType(issueKey: String, isCurrentlySelected: Boolean) {
        if (isCurrentlySelected) {
            selectedTypes = selectedTypes - issueKey
            when (issueKey) {
                "SHORTAGE" -> shortageQty = 0
                "DAMAGED" -> damagedQty = 0
                "BIN_EMPTY" -> binEmptyQty = 0
                "WRONG_BATCH" -> wrongBatchQty = 0
                "OTHER" -> otherQty = 0
            }
        } else {
            selectedTypes = selectedTypes + issueKey
            val defaultQty = maxQtyForIssue(issueKey).coerceAtLeast(0)
            when (issueKey) {
                "SHORTAGE" -> shortageQty = defaultQty
                "DAMAGED" -> damagedQty = defaultQty
                "BIN_EMPTY" -> binEmptyQty = defaultQty
                "WRONG_BATCH" -> wrongBatchQty = defaultQty
                "OTHER" -> otherQty = defaultQty
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            // ── Red header ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFD32F2F))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📋", fontSize = 20.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("Exception Report", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        productName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                    Text(
                        "Batch: $batchLabel • Order • $requiredQty Units",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── Shortfall summary banner ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max)
                        .shadow(2.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFFF7ED))
                        .border(1.dp, Color(0xFFFDBA74).copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                ) {
                    Box(
                        Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(Color(0xFFF97316)),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "SHORTFALL SUMMARY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC2410C),
                            letterSpacing = 0.8.sp,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.7f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text("Picked", fontSize = 10.sp, color = WmsColors.TextMuted)
                                Text(
                                    "$pickedQtyFound",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WmsColors.Success,
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.7f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text("Required", fontSize = 10.sp, color = WmsColors.TextMuted)
                                Text(
                                    "$requiredQty",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WmsColors.Navy,
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.7f))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                            ) {
                                Text("Remaining", fontSize = 10.sp, color = WmsColors.TextMuted)
                                Text(
                                    "$remainingShortfall",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFEA580C),
                                )
                            }
                        }
                        Text(
                            shortageDesc,
                            fontSize = 12.sp,
                            color = Color(0xFF9A3412),
                            lineHeight = 16.sp,
                        )
                        if (totalReportedQty > 0) {
                            Text(
                                "Reported: $totalReportedQty of $remainingShortfall",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC2410C),
                            )
                        }
                    }
                }

                // ── Issue reason section ──
                Text("Issue Reason", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WmsColors.TextPrimary)

                issueReasons.forEach { issue ->
                    val isSelected = issue.key in selectedTypes
                    val issueQty = when (issue.key) {
                        "SHORTAGE" -> shortageQty
                        "DAMAGED" -> damagedQty
                        "BIN_EMPTY" -> binEmptyQty
                        "WRONG_BATCH" -> wrongBatchQty
                        "OTHER" -> otherQty
                        else -> 0
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                1.dp,
                                if (isSelected) WmsColors.ActiveBlue else Color(0xFFE0E0E0),
                                RoundedCornerShape(12.dp),
                            )
                            .background(if (isSelected) WmsColors.ActiveBlue.copy(alpha = 0.06f) else Color(0xFFFAFAFA))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { toggleIssueType(issue.key, isSelected) }
                                .padding(vertical = 2.dp),
                        ) {
                            // Checkbox
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .border(
                                        2.dp,
                                        if (isSelected) WmsColors.ActiveBlue else Color(0xFFBDBDBD),
                                        RoundedCornerShape(6.dp),
                                    )
                                    .background(if (isSelected) WmsColors.ActiveBlue else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            // Emoji
                            Text(issue.emoji, fontSize = 20.sp)
                            Spacer(Modifier.width(10.dp))
                            // Label + description
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    issue.label,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = WmsColors.TextPrimary,
                                )
                                Text(
                                    issue.description,
                                    fontSize = 11.sp,
                                    color = WmsColors.TextMuted,
                                    lineHeight = 14.sp,
                                )
                            }
                        }

                        // ── Rich qty stepper for selected reasons ──
                        if (isSelected) {
                            val accent = when (issue.key) {
                                "SHORTAGE" -> Color(0xFFEA580C)
                                "DAMAGED" -> Color(0xFFDC2626)
                                "BIN_EMPTY" -> Color(0xFF2563EB)
                                "WRONG_BATCH" -> Color(0xFF7C3AED)
                                else -> WmsColors.Navy
                            }
                            val qtyLabel = when (issue.key) {
                                "SHORTAGE" -> "Shortage quantity"
                                "DAMAGED" -> "Damaged quantity"
                                "BIN_EMPTY" -> "Bin empty quantity"
                                "WRONG_BATCH" -> "Wrong batch quantity"
                                else -> "Other issue quantity"
                            }
                            val issueMaxQty = maxQtyForIssue(issue.key)
                            PickerExceptionRichQtyStepper(
                                label = qtyLabel,
                                accentColor = accent,
                                qty = issueQty,
                                maxQty = issueMaxQty,
                                onDecrease = {
                                    when (issue.key) {
                                        "SHORTAGE" -> shortageQty = maxOf(0, shortageQty - 1)
                                        "DAMAGED" -> damagedQty = maxOf(0, damagedQty - 1)
                                        "BIN_EMPTY" -> binEmptyQty = maxOf(0, binEmptyQty - 1)
                                        "WRONG_BATCH" -> wrongBatchQty = maxOf(0, wrongBatchQty - 1)
                                        "OTHER" -> otherQty = maxOf(0, otherQty - 1)
                                    }
                                },
                                onIncrease = {
                                    when (issue.key) {
                                        "SHORTAGE" -> shortageQty = minOf(issueMaxQty, shortageQty + 1)
                                        "DAMAGED" -> damagedQty = minOf(issueMaxQty, damagedQty + 1)
                                        "BIN_EMPTY" -> binEmptyQty = minOf(issueMaxQty, binEmptyQty + 1)
                                        "WRONG_BATCH" -> wrongBatchQty = minOf(issueMaxQty, wrongBatchQty + 1)
                                        "OTHER" -> otherQty = minOf(issueMaxQty, otherQty + 1)
                                    }
                                },
                                onValueChange = { value ->
                                    when (issue.key) {
                                        "SHORTAGE" -> shortageQty = minOf(issueMaxQty, value.coerceAtLeast(0))
                                        "DAMAGED" -> damagedQty = minOf(issueMaxQty, value.coerceAtLeast(0))
                                        "BIN_EMPTY" -> binEmptyQty = minOf(issueMaxQty, value.coerceAtLeast(0))
                                        "WRONG_BATCH" -> wrongBatchQty = minOf(issueMaxQty, value.coerceAtLeast(0))
                                        "OTHER" -> otherQty = minOf(issueMaxQty, value.coerceAtLeast(0))
                                    }
                                },
                            )

                            // ── Custom reason text for OTHER ──
                            if (issue.key == "OTHER" && otherQty > 0) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(WmsColors.Navy.copy(alpha = 0.04f))
                                        .border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "Describe the issue",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = WmsColors.TextPrimary,
                                    )
                                    OutlinedTextField(
                                        value = otherReasonText,
                                        onValueChange = { otherReasonText = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        placeholder = { Text("Enter details…", color = WmsColors.TextMuted) },
                                        singleLine = true,
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White,
                                        ),
                                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Custom notes ──
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, WmsColors.Border, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Additional notes",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WmsColors.TextPrimary,
                    )
                    OutlinedTextField(
                        value = customNotes,
                        onValueChange = { customNotes = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Add any extra details…", color = WmsColors.TextMuted) },
                        minLines = 2,
                        maxLines = 3,
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                    )
                }

                Spacer(Modifier.height(4.dp))

                // ── Submit button ──
                Button(
                    onClick = {
                        println("=== OTHER EXCEPTION SUBMIT ===")
                        println("selectedTypes=$selectedTypes otherQty=$otherQty otherReasonText='$otherReasonText' customNotes='$customNotes'")
                        println("pickedQtyFound=$pickedQtyFound remainingShortfall=$remainingShortfall totalReportedQty=$totalReportedQty")
                        val effectiveOtherQty = if ("OTHER" in selectedTypes && otherQty <= 0 && otherReasonText.isNotBlank()) 1 else otherQty
                        val allNotes = buildList {
                            if (customNotes.isNotBlank()) add(customNotes)
                            if (otherReasonText.isNotBlank() && "OTHER" in selectedTypes) add(otherReasonText)
                        }.joinToString("; ").ifBlank { null }
                        val exceptionDetails = buildPickerExceptionDetails(
                            selectedTypes = selectedTypes,
                            shortageQty = shortageQty,
                            damagedQty = damagedQty,
                            binEmptyQty = binEmptyQty,
                            wrongBatchQty = wrongBatchQty,
                            otherQty = effectiveOtherQty,
                            totalReportedQty = if ("OTHER" in selectedTypes && otherQty <= 0 && otherReasonText.isNotBlank()) totalReportedQty + 1 else totalReportedQty,
                            remainingShortfall = remainingShortfall,
                        )
                        println("effectiveOtherQty=$effectiveOtherQty allNotes=$allNotes exceptionDetails=$exceptionDetails")
                        if (exceptionDetails.isNotEmpty() || pickedQtyFound > 0) {
                            println("CALLING onSubmit: pickedQtyFound=$pickedQtyFound")
                            onSubmit(
                                pickedQtyFound,
                                maxOf(totalReportedQty, exceptionDetails.sumOf { it.qty }),
                                exceptionDetails,
                                allNotes,
                            )
                        } else {
                            println("SKIPPED onSubmit: exceptionDetails empty AND pickedQtyFound==0")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    enabled = selectedTypes.isNotEmpty() || pickedQtyFound > 0,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = WmsColors.Navy,
                        disabledContainerColor = Color(0xFFB0BEC5),
                    ),
                ) {
                    Text("Submit Exception", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                // ── Cancel ──
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel", fontSize = 13.sp, color = WmsColors.TextMuted)
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Tote Picker Sheet ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TotePickerSheet(
    visible: Boolean,
    currentTote: String,
    totesCatalog: List<ToteCatalogItem>,
    newToteInput: String,
    onNewToteInputChange: (String) -> Unit,
    onSelectExistingTote: (ToteCatalogItem) -> Unit,
    onCreateNewTote: () -> Unit,
    onDismiss: () -> Unit,
    loading: Boolean,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showNewToteField by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = { if (!loading) onDismiss() },
        sheetState = sheetState,
        containerColor = Color.White,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC))
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Manage Totes",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = WmsColors.TextPrimary,
                    )
                    Text(
                        "Select an existing tote or add a new one",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = WmsColors.TextSecondary,
                    )
                }
                IconButton(onClick = { if (!loading) onDismiss() }) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = WmsColors.TextMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            HorizontalDivider(color = WmsColors.Border)

            // ── Current Tote Banner ──
            if (currentTote.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(WmsColors.Navy.copy(alpha = 0.05f))
                        .border(1.dp, WmsColors.Navy.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(WmsColors.Navy.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = WmsColors.Navy,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "ACTIVE TOTE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = WmsColors.TextMuted,
                            letterSpacing = 0.6.sp,
                        )
                        Text(
                            currentTote,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = WmsColors.Navy,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF16A34A).copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            "PICKING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF16A34A),
                            letterSpacing = 0.4.sp,
                        )
                    }
                }
            }

            // ── Existing Totes List ──
            val existingTotes = totesCatalog.filter { it.toteNumber.isNotBlank() }
            if (existingTotes.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                ) {
                    Text(
                        "EXISTING TOTES",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WmsColors.TextMuted,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    existingTotes.forEach { tote ->
                        val isCurrentTote = tote.toteNumber.trim().uppercase() == currentTote.trim().uppercase()
                        val isToteActive = tote.isToteActive
                        val isFilled = tote.isFilledOrStaged
                        val statusColor = when {
                            isCurrentTote -> Color(0xFF16A34A)
                            isToteActive -> WmsColors.ActiveBlue
                            else -> WmsColors.ActiveBlue
                        }
                        val statusLabel = when {
                            isCurrentTote -> "PICKING"
                            else -> "SELECT"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (isCurrentTote) {
                                        Modifier.background(WmsColors.Navy.copy(alpha = 0.04f))
                                            .border(1.5.dp, WmsColors.Navy.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    } else {
                                        Modifier.border(1.dp, WmsColors.Border, RoundedCornerShape(12.dp))
                                    }
                                )
                                .clickable(enabled = !loading && !isCurrentTote) {
                                    onSelectExistingTote(tote)
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(statusColor.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isCurrentTote) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Inventory2,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    tote.toteNumber.trim().uppercase(),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = WmsColors.TextPrimary,
                                )
                                if (tote.batchCount > 0 || tote.itemCount > 0) {
                                    Text(
                                        buildString {
                                            if (tote.batchCount > 0) append("${tote.batchCount} batches")
                                            if (tote.batchCount > 0 && tote.itemCount > 0) append(" · ")
                                            if (tote.itemCount > 0) append("${tote.itemCount} items")
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = WmsColors.TextMuted,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(statusColor.copy(alpha = 0.1f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    statusLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = statusColor,
                                    letterSpacing = 0.4.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            // ── Add New Tote ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            ) {
                HorizontalDivider(color = WmsColors.Border, modifier = Modifier.padding(bottom = 12.dp))

                if (!showNewToteField) {
                    Button(
                        onClick = { showNewToteField = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = WmsColors.Navy,
                        ),
                    ) {
                        Icon(
                            Icons.Default.AddCircle,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Add New Tote",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                } else {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            "NEW TOTE NUMBER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = WmsColors.TextMuted,
                            letterSpacing = 0.6.sp,
                        )
                        OutlinedTextField(
                            value = newToteInput,
                            onValueChange = onNewToteInputChange,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("e.g. TOTE-00142", color = WmsColors.TextMuted) },
                            singleLine = true,
                            enabled = !loading,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            ),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = WmsColors.Navy,
                                unfocusedBorderColor = WmsColors.Border,
                                focusedContainerColor = Color(0xFFF8FAFC),
                                unfocusedContainerColor = Color(0xFFF8FAFC),
                            ),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            OutlinedButton(
                                onClick = {
                                    showNewToteField = false
                                    onNewToteInputChange("")
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                enabled = !loading,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text("Cancel", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = WmsColors.TextMuted)
                            }
                            Button(
                                onClick = {
                                    showNewToteField = false
                                    onCreateNewTote()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                enabled = newToteInput.isNotBlank() && !loading,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                } else {
                                    Text("Create & Switch", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

// ── Staging screen ───────────────────────────────────────────────────────────

private fun enrichBatchWithGroup(batch: FefoBatchItem, group: FefoProductGroup): FefoBatchItem {
    val groupTypes = group.resolvedExceptionTypes
    val groupNotes = group.resolvedExceptionNotes.orEmpty()
    val mergedTypes = batch.exceptionTypes.ifEmpty { groupTypes }
    val mergedNotes = batch.exceptionNotes?.trim()?.takeIf { it.isNotEmpty() }
        ?: batch.exceptionNotesSnake?.trim()?.takeIf { it.isNotEmpty() }
        ?: groupNotes.takeIf { it.isNotEmpty() }
    val mergedMessage = when {
        batch.exceptionMessage.isNotBlank() -> batch.exceptionMessage
        !mergedNotes.isNullOrBlank() -> mergedNotes
        batch.exception?.isNotBlank() == true -> batch.exception.orEmpty()
        else -> ""
    }
    val missingQty = maxOf(0, batch.resolvedRequestedQty - batch.resolvedPickedQty)
    val inferredShortfall = when {
        (batch.shortfallQty ?: 0) > 0 -> batch.shortfallQty
        (batch.shortfallQtySnake ?: 0) > 0 -> batch.shortfallQtySnake
        mergedTypes.isNotEmpty() && missingQty > 0 -> missingQty
        batch.status.contains("exception", ignoreCase = true) && missingQty > 0 -> missingQty
        else -> null
    }
    return batch.copy(
        exceptionTypes = mergedTypes,
        exceptionNotes = mergedNotes ?: batch.exceptionNotes,
        exceptionMessage = mergedMessage.ifBlank { batch.exceptionMessage },
        shortfallQty = inferredShortfall,
    )
}

private fun buildPickerExceptionDetails(
    selectedTypes: Set<String>,
    shortageQty: Int,
    damagedQty: Int,
    binEmptyQty: Int,
    wrongBatchQty: Int,
    otherQty: Int,
    totalReportedQty: Int,
    remainingShortfall: Int,
): List<PickerExceptionDetail> {
    val entries = selectedTypes.mapNotNull { key ->
        val qty = when (key) {
            "SHORTAGE" -> shortageQty
            "DAMAGED" -> damagedQty
            "BIN_EMPTY" -> binEmptyQty
            "WRONG_BATCH" -> wrongBatchQty
            "OTHER" -> otherQty
            else -> 0
        }
        if (qty > 0) PickerExceptionDetail(key, qty) else null
    }
    if (entries.isNotEmpty()) return entries
    val fallbackQty = maxOf(totalReportedQty, remainingShortfall).takeIf { it > 0 } ?: return emptyList()
    val fallbackType = selectedTypes.firstOrNull() ?: "SHORTAGE"
    return listOf(PickerExceptionDetail(fallbackType, fallbackQty))
}

private fun normalizePickerExceptionDetails(
    details: List<PickerExceptionDetail>,
    exceptionQty: Int,
    fallbackType: String,
): List<PickerExceptionDetail> {
    val withQty = details.filter { it.qty > 0 && it.type.isNotBlank() }
    if (withQty.isNotEmpty()) {
        val sum = withQty.sumOf { it.qty }
        return if (exceptionQty > sum && withQty.size == 1) {
            listOf(withQty.first().copy(qty = exceptionQty))
        } else {
            withQty
        }
    }
    if (exceptionQty <= 0) return emptyList()
    return listOf(PickerExceptionDetail(fallbackType, exceptionQty))
}

private fun resolveDisplayProductGroups(
    productGroups: List<FefoProductGroup>,
    localBatchPickStates: Map<String, LocalBatchPickState>,
): List<FefoProductGroup> {
    return productGroups.mapIndexed { groupIdx, group ->
        group.copy(
            batches = sortBatchesForDisplay(
                group.batches.mapIndexed { batchIdx, batch ->
                    val enriched = enrichBatchWithGroup(batch, group)
                    val key = enriched.batchPickKey(group.key, groupIdx, batchIdx)
                    mergeLocalBatchPickState(
                        enriched,
                        localBatchPickStates[key] ?: localBatchPickStates[enriched.stateKey()],
                    )
                },
            ),
        )
    }
}

private fun sortBatchesForDisplay(batches: List<FefoBatchItem>): List<FefoBatchItem> {
    return batches.withIndex().sortedWith(
        compareBy(
            { (_, batch) -> batchDisplaySortRank(batch) },
            { (index, batch) -> batch.batchSequence.takeIf { it > 0 } ?: (index + 1) },
            { it.index },
        ),
    ).map { it.value }
}

/** Pending batches first so the next pick is always at the top of each card. */
private fun batchDisplaySortRank(batch: FefoBatchItem): Int = when {
    !batch.isPickerHandled -> 0
    batch.isPartialPicked -> 1
    else -> 2
}

private fun firstUnpickedBatchKey(groups: List<FefoProductGroup>): String? =
    groups.asSequence()
        .flatMap { it.batches.asSequence() }
        .firstOrNull { !it.isPickerHandled }
        ?.stateKey()

private fun firstUnpickedGroupIndex(groups: List<FefoProductGroup>): Int =
    groups.indexOfFirst { group -> group.batches.any { !it.isPickerHandled } }

private fun batchPickStateKey(
    productGroups: List<FefoProductGroup>,
    batchItem: FefoBatchItem,
): String {
    findBatchLocation(productGroups, batchItem)?.let { (groupIdx, batchIdx) ->
        val groupKey = productGroups[groupIdx].key
        return batchItem.batchPickKey(groupKey, groupIdx, batchIdx)
    }
    return batchItem.stateKey()
}

private fun findBatchLocation(
    productGroups: List<FefoProductGroup>,
    batchItem: FefoBatchItem,
): Pair<Int, Int>? {
    productGroups.forEachIndexed { groupIdx, group ->
        group.batches.forEachIndexed { batchIdx, batch ->
            if (batch.matchesBatch(batchItem)) return groupIdx to batchIdx
        }
    }
    return null
}

private fun List<FefoProductGroup>.findGroupContaining(batchItem: FefoBatchItem): FefoProductGroup? =
    firstOrNull { group -> group.batches.any { it.matchesBatch(batchItem) } }

private data class LocalBatchPickState(
    val pickedQty: Int,
    val exceptionMessage: String,
    val exceptionDetails: List<PickerExceptionDetail> = emptyList(),
)

private fun mergeLocalBatchPickState(
    batch: FefoBatchItem,
    local: LocalBatchPickState?,
): FefoBatchItem {
    if (local != null) {
        val hasLocalException = local.exceptionDetails.isNotEmpty() || local.exceptionMessage.isNotBlank()
        return batch.copy(
            pickedQty = local.pickedQty,
            exceptionMessage = local.exceptionMessage,
            exceptionNotes = batch.exceptionNotes,
            shortfallQty = local.exceptionDetails.sumOf { it.qty }.takeIf { it > 0 },
            exceptionTypes = local.exceptionDetails.map { it.type },
            exceptionDetails = local.exceptionDetails,
            status = if (hasLocalException || local.pickedQty >= batch.resolvedRequestedQty) "picked" else "in_progress",
        )
    }
    if (batch.isPickerHandled &&
        !batch.status.equals("picked", ignoreCase = true) &&
        !batch.status.equals("completed", ignoreCase = true)
    ) {
        return batch.copy(status = "picked")
    }
    return batch
}

private fun buildStagingLineItems(
    productGroups: List<FefoProductGroup>,
    fallbackLines: List<WmsPickListLine>,
): List<PickerStagingLineDisplay> {
    val fromGroups = productGroups.flatMap { group ->
        group.batches.map { batch ->
            val picked = batch.resolvedPickedQty
            val requested = batch.resolvedRequestedQty
            PickerStagingLineDisplay(
                productName = group.productName,
                sku = group.sku,
                batch = batch.resolvedBatch,
                location = batch.resolvedLocation,
                pickedQty = picked,
                requestedQty = requested,
                isComplete = batch.isPickerHandled,
            )
        }
    }
    if (fromGroups.isNotEmpty()) return fromGroups

    return fallbackLines.map { line ->
        val required = line.resolvedRequestedQty()
        val picked = line.resolvedPickedQty()
        PickerStagingLineDisplay(
            productName = line.displayName,
            sku = line.displaySku,
            batch = line.displayBatch.takeIf { it != "—" }.orEmpty(),
            location = line.displayLocation,
            pickedQty = picked,
            requestedQty = required,
            isComplete = required > 0 && picked >= required,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerCompleteStagingBottomSheet(
    visible: Boolean,
    task: PickerMyListTask,
    pickListId: String,
    productGroups: List<FefoProductGroup>,
    toteNumber: String,
    totalBatches: Int,
    pickedBatches: Int,
    companyId: Int,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onComplete: (skipLocation: Boolean, locationName: String?) -> Unit,
) {
    if (!visible) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val repo = remember { WmsRepository() }
    val listAlreadyStaged = task.status.equals("STAGED", ignoreCase = true)
    val needsStagingLocation = !listAlreadyStaged
    val stagingLines = remember(productGroups, task.resolvedLines) {
        buildStagingLineItems(productGroups, task.resolvedLines)
    }

    var stagingLocation by remember(task.id) {
        mutableStateOf(task.stagingLocationName?.trim().orEmpty())
    }
    var stagingLocations by remember { mutableStateOf<List<WmsWarehouseLocation>>(emptyList()) }
    var isLoadingLocations by remember { mutableStateOf(true) }

    LaunchedEffect(companyId, task.id) {
        if (companyId <= 0) {
            isLoadingLocations = false
            return@LaunchedEffect
        }
        isLoadingLocations = true
        runCatching {
            repo.fetchStagingLocations(companyId)
        }.onSuccess { locations ->
            stagingLocations = locations
            if (stagingLocation.isBlank()) {
                locations.firstOrNull()?.displayLabel?.let { stagingLocation = it }
            }
        }
        isLoadingLocations = false
    }

    val canComplete = !needsStagingLocation || stagingLocation.trim().isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Complete Pick List",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = WmsColors.Navy,
                )
                Text(
                    pickListId,
                    fontSize = 13.sp,
                    color = WmsColors.TextSecondary,
                )
            }

            PickerStagingSummaryCard(
                toteNumber = toteNumber,
                pickedBatches = pickedBatches,
                totalBatches = totalBatches,
                lineCount = stagingLines.size,
            )

            PickerStagingLocationSection(
                stagingLocation = stagingLocation,
                onStagingLocationChange = { stagingLocation = it },
                locations = stagingLocations,
                isLoadingLocations = isLoadingLocations,
                listAlreadyStaged = listAlreadyStaged,
            )

            Button(
                onClick = { onComplete(false, stagingLocation) },
                enabled = !isSaving && canComplete,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WmsColors.Navy),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (isSaving) "Finishing…" else "Complete Pick List",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Cancel", fontSize = 13.sp, color = WmsColors.TextMuted)
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

@Composable
private fun BatchScanGridBackground() {
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(WmsColors.PageBg))
        Canvas(Modifier.fillMaxSize()) {
            val step = 24.dp.toPx()
            val gridColor = Color(0xFFE5E7EB).copy(alpha = 0.45f)
            var x = 0f
            while (x < size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 0.5f)
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 0.5f)
                y += step
            }
        }
    }
}
