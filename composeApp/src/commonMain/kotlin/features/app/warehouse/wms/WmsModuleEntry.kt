package features.app.warehouse.wms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.network.repository.WmsRepository
import core.network.wms.WmsPickListItem
import core.network.wms.WmsUser
import core.session.WarehouseAccess
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch

@Composable
fun AdminPackingQueueScreen(
    onBack: () -> Unit,
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val access = remember { WarehouseAccess(session) }
    val repo = remember { WmsRepository() }
    val scope = rememberCoroutineScope()
    val companyId = remember { WmsSession.companyId(session) }

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var lists by remember { mutableStateOf<List<WmsPickListItem>>(emptyList()) }
    var packers by remember { mutableStateOf<List<WmsUser>>(emptyList()) }
    var assignTarget by remember { mutableStateOf<WmsPickListItem?>(null) }

    fun load() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                lists = repo.fetchPackingPickLists(companyId).pickLists
                packers = repo.fetchPackers(companyId)
            }.onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(companyId) { load() }

    assignTarget?.let { item ->
        AlertDialog(
            onDismissRequest = { assignTarget = null },
            title = { Text("Assign packer") },
            text = {
                Column {
                    packers.forEach { packer ->
                        TextButton(onClick = {
                            scope.launch {
                                val pickListId = item.pickListId ?: item.id
                                runCatching {
                                    repo.assignPackerToPickList(pickListId, packer.id, companyId)
                                    assignTarget = null
                                    load()
                                }.onFailure { error = it.message }
                            }
                        }) { Text(packer.name) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { assignTarget = null }) { Text("Cancel") } },
        )
    }

    Column(Modifier.fillMaxSize().background(WmsColors.PageBg)) {
        WmsLightHeader(title = "Packing queue", showBack = true, onBack = onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { WmsErrorBanner(it) }
            if (loading) {
                WmsListSkeleton(count = 4)
            } else {
            lists.forEach { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(14.dp))
                        .clickable { assignTarget = item }
                        .padding(16.dp),
                ) {
                    Text(item.displayTitle, fontWeight = FontWeight.Bold)
                    Text(item.orderNumber ?: "", fontSize = 12.sp, color = WmsColors.TextSecondary)
                    Text(
                        item.assignedPackerName?.let { "Packer: $it" } ?: "Tap to assign packer",
                        fontSize = 12.sp,
                        color = if (item.assignedPacker != null) WmsColors.Success else WmsColors.Warning,
                    )
                }
            }
            }
        }
    }
}

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
        // Admin ops uses picker lists for now; full ops dashboard is a follow-up.
        PickerWmsRoot(
            showBackNavigation = showBackNavigation,
            onBack = onBack,
            profileName = profileName,
            onLogout = onLogout,
        )
    } else {
        PickerWmsRoot(
            showBackNavigation = showBackNavigation,
            onBack = onBack,
            profileName = profileName,
            onLogout = onLogout,
        )
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
