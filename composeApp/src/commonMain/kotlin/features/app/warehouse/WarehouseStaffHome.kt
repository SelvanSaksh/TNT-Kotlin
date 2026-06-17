package features.app.warehouse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import core.session.WarehouseStaffRole
import core.storage.SessionManager
import core.storage.getLocalStorage
import features.app.warehouse.wms.PackingWmsEntry
import features.app.warehouse.wms.PickingWmsEntry
import features.app.warehouse.wms.ReceivingWmsRoot
import features.app.warehouse.wms.WmsSession

@Composable
fun WarehouseStaffModuleRoot(
    role: WarehouseStaffRole,
    onLogout: () -> Unit = {},
) {
    val session = remember { SessionManager(getLocalStorage()) }
    val profileName = remember { WmsSession.userName(session) }

    when (role) {
        WarehouseStaffRole.Picker -> PickingWmsEntry(
            showBackNavigation = false,
            profileName = profileName,
            onLogout = onLogout,
        )
        WarehouseStaffRole.Packer -> PackingWmsEntry(
            showBackNavigation = false,
            profileName = profileName,
            onLogout = onLogout,
        )
        WarehouseStaffRole.Receiver -> ReceivingWmsRoot(
            showBackNavigation = false,
            profileName = profileName,
            onLogout = onLogout,
        )
    }
}
