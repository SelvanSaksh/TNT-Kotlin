package features.app.warehouse.wms

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import core.network.wms.PickerMyListTask
import core.session.WarehouseAccess
import core.storage.SessionManager
import core.storage.getLocalStorage

@Composable
fun PickingWmsEntry(
    showBackNavigation: Boolean = false,
    onBack: () -> Unit = {},
    profileName: String? = null,
    onLogout: (() -> Unit)? = null,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val access = remember { WarehouseAccess(session) }
    if (access.isWarehouseAdminRole) {
        PickerWmsRoot(
            showBackNavigation = showBackNavigation,
            onBack = onBack,
            profileName = profileName,
            onLogout = onLogout,
        )
    } else {
        var selectedTask by remember { mutableStateOf<PickerMyListTask?>(null) }
        var pickingTask by remember { mutableStateOf<PickerMyListTask?>(null) }
        var pickingTote by remember { mutableStateOf("") }
        var pickingToteId by remember { mutableStateOf<Int?>(null) }
        var openStagingDirectly by remember { mutableStateOf(false) }
        val navigateToHome = remember {
            {
                selectedTask = null
                pickingTask = null
                pickingToteId = null
                openStagingDirectly = false
            }
        }

        when {
            pickingTask != null -> PickerBatchScanScreen(
                task = pickingTask!!,
                toteNumber = pickingTote,
                activeToteId = pickingToteId ?: 0,
                openStagingDirectly = openStagingDirectly,
                onBack = navigateToHome,
                onFinished = navigateToHome,
            )
            selectedTask != null -> PickerToteAssignScreen(
                task = selectedTask!!,
                onBack = { selectedTask = null },
                onContinue = { toteNumber, toteId ->
                    pickingTote = toteNumber
                    pickingToteId = toteId
                    pickingTask = selectedTask
                },
            )
            else -> PickerHomeScreen(
                showBackNavigation = showBackNavigation,
                onBack = onBack,
                profileName = profileName,
                onLogout = onLogout,
                onOpenTask = { selectedTask = it },
                onGoToStaging = { task ->
                    pickingTote = task.toteNumber.orEmpty()
                    pickingToteId = null
                    openStagingDirectly = true
                    pickingTask = task
                },
            )
        }
    }
}

@Composable
fun PackingWmsEntry(
    showBackNavigation: Boolean = false,
    onBack: () -> Unit = {},
    profileName: String? = null,
    onLogout: (() -> Unit)? = null,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val access = remember { WarehouseAccess(session) }
    if (access.isWarehouseAdminRole) {
        AdminPackingQueueScreen(onBack = onBack)
    } else {
        PackingWmsRoot(
            showBackNavigation = showBackNavigation,
            onBack = onBack,
            profileName = profileName,
            onLogout = onLogout,
        )
    }
}
