package core.session

import core.storage.SessionManager
import features.app.warehouse.WarehouseModuleItem
import features.app.warehouse.defaultWarehouseModules
import features.app.warehouse.resolveWarehouseModuleItem
import kotlinx.serialization.json.JsonElement

enum class WarehouseStaffRole(val rawValue: String) {
    Picker("picker"),
    Packer("packer"),
    Receiver("receiver");

    val displayName: String
        get() = when (this) {
            Picker -> "Picker"
            Packer -> "Packer"
            Receiver -> "Receiver"
        }

    val subtitle: String
        get() = when (this) {
            Picker -> "Pick items for dispatch"
            Packer -> "Pack and verify orders"
            Receiver -> "Receive incoming stock"
        }

    val mobileModuleName: String
        get() = when (this) {
            Picker -> "Picking"
            Packer -> "Packing"
            Receiver -> "Receiving"
        }

    companion object {
        val all: List<WarehouseStaffRole> = listOf(Picker, Packer, Receiver)

        fun fromRaw(raw: String?): WarehouseStaffRole? =
            all.firstOrNull { it.rawValue == raw?.trim()?.lowercase() }
    }
}

class WarehouseAccess(private val sessionManager: SessionManager) {

    val userRole: Int
        get() = sessionManager.getUserRole()

    /** `0` super admin, `1` admin — full warehouse features without mobile-module checks. */
    val isWarehouseAdminRole: Boolean
        get() = userRole == 0 || userRole == 1

    val requiresMobileModuleForWarehouseFeatures: Boolean
        get() = !isWarehouseAdminRole

    val mobileWarehouseModuleNames: List<String>
        get() = sessionManager.getMobileWarehouseModules()

    fun hasMobileWarehouseModule(moduleName: String): Boolean {
        if (!requiresMobileModuleForWarehouseFeatures) return true
        val target = moduleName.trim().lowercase()
        if (target.isEmpty()) return false
        return mobileWarehouseModuleNames.any { it.trim().lowercase() == target }
    }

    val availableWarehouseStaffRoles: List<WarehouseStaffRole>
        get() {
            if (!requiresMobileModuleForWarehouseFeatures) return emptyList()
            return WarehouseStaffRole.all.filter { hasMobileWarehouseModule(it.mobileModuleName) }
        }

    val isWarehouseFloorStaff: Boolean
        get() = availableWarehouseStaffRoles.isNotEmpty()

    fun resolvedWarehouseStaffRole(): WarehouseStaffRole? {
        val available = availableWarehouseStaffRoles
        if (available.isEmpty()) return null
        if (available.size == 1) return available.first()
        val stored = sessionManager.getActiveWarehouseStaffRole()
        return stored?.takeIf { available.contains(it) }
    }

    val needsWarehouseRoleSelection: Boolean
        get() = isWarehouseFloorStaff
            && availableWarehouseStaffRoles.size > 1
            && resolvedWarehouseStaffRole() == null

    val usesWarehouseStaffExperience: Boolean
        get() = isWarehouseFloorStaff && resolvedWarehouseStaffRole() != null

    fun selectWarehouseStaffRole(role: WarehouseStaffRole) {
        if (!availableWarehouseStaffRoles.contains(role)) return
        sessionManager.saveActiveWarehouseStaffRole(role.rawValue)
    }

    fun clearWarehouseStaffRoleSelection() {
        sessionManager.clearActiveWarehouseStaffRole()
    }

    fun homeWarehouseModules(): List<WarehouseModuleItem> {
        if (isWarehouseAdminRole) return defaultWarehouseModules
        val fromLogin = mobileWarehouseModuleNames.mapNotNull(::resolveWarehouseModuleItem)
        if (fromLogin.isNotEmpty()) return fromLogin
        return emptyList()
    }

    fun persistWarehouseSessionFromLogin(
        userRole: Int,
        topLevelModules: JsonElement?,
        accessModules: JsonElement?,
    ) {
        sessionManager.saveUserRole(userRole)
        val modules = WarehouseModuleParser.extractMobileModuleNames(topLevelModules, accessModules)
        sessionManager.saveMobileWarehouseModules(modules)

        val stored = sessionManager.getActiveWarehouseStaffRole()
        if (stored != null && !availableWarehouseStaffRoles.contains(stored)) {
            sessionManager.clearActiveWarehouseStaffRole()
        }
    }

    enum class PostAuthDestination {
        Home,
        RoleSelection,
        Subscription,
    }

    fun postAuthDestination(hasActiveSubscription: Boolean): PostAuthDestination = when {
        !hasActiveSubscription -> PostAuthDestination.Subscription
        needsWarehouseRoleSelection -> PostAuthDestination.RoleSelection
        else -> PostAuthDestination.Home
    }
}
