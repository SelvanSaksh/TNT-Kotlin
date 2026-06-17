package features.app.warehouse

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.session.WarehouseAccess
import core.session.WarehouseStaffRole
import core.storage.SessionManager
import core.storage.getLocalStorage

private val Navy = Color(0xFF163C66)
private val PageBg = Color(0xFFF5F6FA)

@Composable
fun WarehouseRoleSelectionScreen(
    onRoleSelected: () -> Unit,
) {
    val sessionManager = remember { SessionManager(getLocalStorage()) }
    val access = remember { WarehouseAccess(sessionManager) }
    val roles = access.availableWarehouseStaffRoles

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Continue as",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
            )
            Text(
                "Your account has access to more than one warehouse role. Choose how you want to work in this session.",
                fontSize = 15.sp,
                color = Color(0xFF6B7280),
                lineHeight = 22.sp,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            roles.forEach { role ->
                WarehouseRoleCard(
                    role = role,
                    onClick = {
                        access.selectWarehouseStaffRole(role)
                        onRoleSelected()
                    },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WarehouseRoleCard(
    role: WarehouseStaffRole,
    onClick: () -> Unit,
) {
    val accent = roleAccent(role)
    val icon = roleIcon(role)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(accent.copy(alpha = 0.12f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(role.displayName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF111827))
            Text(role.subtitle, fontSize = 13.sp, color = Color(0xFF6B7280))
        }

        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF9CA3AF),
        )
    }
}

private fun roleAccent(role: WarehouseStaffRole): Color = when (role) {
    WarehouseStaffRole.Picker -> Color(0xFF059669)
    WarehouseStaffRole.Packer -> Navy
    WarehouseStaffRole.Receiver -> Color(0xFFD97706)
}

private fun roleIcon(role: WarehouseStaffRole): ImageVector = when (role) {
    WarehouseStaffRole.Picker -> Icons.Default.Checklist
    WarehouseStaffRole.Packer -> Icons.Default.Inventory2
    WarehouseStaffRole.Receiver -> Icons.Default.MoveToInbox
}
