package features.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.session.WarehouseAccess
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import navigation.AppScreen

// 🔷 Colors — aligned with SubscriptionScreen brand palette.
private val Brand = Color(0xFF1F4B73)
private val BrandDark = Color(0xFF143655)
private val BrandLight = Brand.copy(alpha = 0.08f)
private val PageBg = Color(0xFFF5F6FA)
private val TextMuted = Color(0xFF6B7280)
private val BorderMuted = Color(0xFFE6E6E6)
private val SuccessGreen = Color(0xFF10B981)
private val DangerRed = Color(0xFFDC2626)

// ─────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(
    userName: String? = "User",
    email: String? = "No email",
    role: Int? = 0,
    onNavigate: (AppScreen) -> Unit = {},
    onLogout: () -> Unit = {},
    onSwitchWarehouseRole: () -> Unit = {},
) {
    val sessionManager = remember { SessionManager(getLocalStorage()) }
    val warehouseAccess = remember { WarehouseAccess(sessionManager) }
    val isWarehouseStaffProfile = warehouseAccess.usesWarehouseStaffExperience
    val activeStaffRole = warehouseAccess.resolvedWarehouseStaffRole()
    val canSwitchWarehouseRole = warehouseAccess.availableWarehouseStaffRoles.size > 1

    val planInfo = remember {
        parseProfilePlan(
            rawJson = sessionManager.getSubscriptionData(),
            fallbackStatus = sessionManager.getSubscriptionStatus(),
            fallbackPlanId = sessionManager.getSubscriptionPlanId()
        )
    }

    var showLogout by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {

            // 🔷 HEADER
            ProfileHeader(
                userName = userName,
                email = email,
                role = role,
                roleLabel = profileRoleLabel(role, activeStaffRole?.displayName),
                isWarehouseStaffProfile = isWarehouseStaffProfile,
                onLogout = { showLogout = true },
            )

            if (isWarehouseStaffProfile) {
                WarehouseStaffSettingsSection(
                    canSwitchRole = canSwitchWarehouseRole,
                    activeRoleName = activeStaffRole?.displayName,
                    onSwitchRole = onSwitchWarehouseRole,
                    onLogout = { showLogout = true },
                )
            } else {
                PlanSection(planInfo = planInfo)

                Column(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SectionLabel("PREFERENCES")

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderMuted),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column {
                            SettingsRow(
                                icon = Icons.Outlined.Notifications,
                                title = "Notifications",
                                subtitle = "Scan and plan alerts",
                                trailing = {
                                    Switch(
                                        checked = notificationsEnabled,
                                        onCheckedChange = { notificationsEnabled = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Brand,
                                            uncheckedTrackColor = Color(0xFFE5E7EB)
                                        )
                                    )
                                }
                            )
                            RowDivider()
                            SettingsRow(
                                icon = Icons.Outlined.Lock,
                                title = "Security & Privacy",
                                subtitle = "Permissions and data",
                                trailing = { ChevronEnd() }
                            )
                            RowDivider()
                            SettingsRow(
                                icon = Icons.Outlined.Translate,
                                title = "Language",
                                subtitle = "English (US)",
                                trailing = { ChevronEnd() }
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, BorderMuted),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showLogout = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(DangerRed.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Logout,
                                    contentDescription = null,
                                    tint = DangerRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Sign Out",
                                color = DangerRed,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Text(
                        "Version 1.0.0 • Ratifye",
                        fontSize = 12.sp,
                        color = TextMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (showLogout) {
            AlertDialog(
                onDismissRequest = { showLogout = false },
                title = { Text("Sign Out", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        if (isWarehouseStaffProfile && activeStaffRole != null) {
                            "You will be signed out of your ${activeStaffRole.displayName} session and returned to the login screen."
                        } else {
                            "Are you sure you want to sign out?"
                        }
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showLogout = false
                        onLogout()
                    }) {
                        Text("Sign Out", color = DangerRed, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogout = false }) {
                        Text("Cancel", color = Brand)
                    }
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Header

@Composable
private fun ProfileHeader(
    userName: String?,
    email: String?,
    role: Int?,
    roleLabel: String,
    isWarehouseStaffProfile: Boolean,
    onLogout: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.linearGradient(listOf(Brand, BrandDark)))
            .padding(top = if (isWarehouseStaffProfile) 52.dp else 32.dp, bottom = 28.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            Box(
                modifier = Modifier
                    .size(92.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userName?.take(2)?.uppercase() ?: "U",
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                userName?.takeIf { it.isNotBlank() } ?: "User",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            if (!email.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    email,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(999.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    roleLabel,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp
                )
            }
        }

        if (isWarehouseStaffProfile) {
            IconButton(
                onClick = onLogout,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 16.dp)
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.Logout,
                    contentDescription = "Logout",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun WarehouseStaffSettingsSection(
    canSwitchRole: Boolean,
    activeRoleName: String?,
    onSwitchRole: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (canSwitchRole) {
            SectionLabel("WAREHOUSE")

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, BorderMuted),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            ) {
                SettingsRow(
                    icon = Icons.Outlined.Sync,
                    title = "Switch role",
                    subtitle = activeRoleName?.let { "Currently working as $it" },
                    onClick = onSwitchRole,
                    trailing = { ChevronEnd() },
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        SectionLabel("ACCOUNT")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, BorderMuted),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            SettingsRow(
                icon = Icons.Filled.Logout,
                title = "Logout",
                subtitle = "Sign out and return to login",
                onClick = onLogout,
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Version 1.0.0 • Ratifye",
            fontSize = 12.sp,
            color = TextMuted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            textAlign = TextAlign.Center,
        )
    }
}

private fun profileRoleLabel(role: Int?, staffRoleName: String?): String {
    if (!staffRoleName.isNullOrBlank()) return staffRoleName.uppercase()
    return roleLabel(role ?: 0)
}

private fun roleLabel(role: Int): String = when (role) {
    1 -> "ADMIN"
    2 -> "MANAGER"
    3 -> "USER"
    else -> "MEMBER"
}

// ─────────────────────────────────────────────────────────────
// Plan section

@Composable
private fun PlanSection(
    planInfo: ProfilePlanInfo,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionLabel("CURRENT PLAN")

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, Brand.copy(alpha = 0.25f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(BrandLight, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.WorkspacePremium,
                                contentDescription = null,
                                tint = Brand,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                planInfo.displayName,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black
                            )
                            if (planInfo.description.isNotBlank()) {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    planInfo.description,
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                        StatusBadge(status = planInfo.status, isActive = planInfo.isActive)
                    }

                    if (planInfo.startedAt != null || planInfo.expiresAt != null) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (planInfo.startedAt != null) {
                                MetaPill(label = "Started", value = planInfo.startedAt, modifier = Modifier.weight(1f))
                            }
                            if (planInfo.expiresAt != null) {
                                MetaPill(
                                    label = if (planInfo.isActive) "Renews" else "Ended",
                                    value = planInfo.expiresAt,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                if (planInfo.features.isNotEmpty()) {
                    HorizontalDivider(color = BorderMuted)
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            "Plan usage",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextMuted,
                            letterSpacing = 0.6.sp
                        )
                        planInfo.features.forEach { feature ->
                            FeatureUsageRow(feature)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String, isActive: Boolean) {
    val bg = if (isActive) SuccessGreen.copy(alpha = 0.12f) else DangerRed.copy(alpha = 0.10f)
    val fg = if (isActive) SuccessGreen else DangerRed
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            status.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            letterSpacing = 0.6.sp
        )
    }
}

@Composable
private fun MetaPill(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(PageBg, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            label.uppercase(),
            fontSize = 10.sp,
            color = TextMuted,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontSize = 13.sp,
            color = Color.Black,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun FeatureUsageRow(feature: ProfilePlanFeature) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                feature.label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
            Text(
                feature.usageText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (feature.exhausted) DangerRed else Brand
            )
        }
        if (feature.usageLimit != null && feature.usageLimit > 0) {
            LinearProgressIndicator(
                progress = { feature.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (feature.exhausted) DangerRed else Brand,
                trackColor = BrandLight
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Settings primitives

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = TextMuted,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(BrandLight, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Brand,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, fontSize = 12.sp, color = TextMuted)
            }
        }
        trailing()
    }
}

@Composable
private fun ChevronEnd() {
    Icon(
        imageVector = Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = TextMuted,
        modifier = Modifier.size(20.dp)
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = BorderMuted,
        modifier = Modifier.padding(start = 64.dp)
    )
}

// ─────────────────────────────────────────────────────────────
// Subscription parsing

private data class ProfilePlanFeature(
    val label: String,
    val usageCount: Int,
    val usageLimit: Int?,
    val isEnabled: Boolean
) {
    val unlimited: Boolean get() = usageLimit == null || usageLimit == 0
    val progress: Float
        get() = if (unlimited) 0f else (usageCount.toFloat() / usageLimit!!.toFloat()).coerceIn(0f, 1f)
    val exhausted: Boolean get() = !unlimited && usageCount >= (usageLimit ?: 0)
    val usageText: String
        get() = when {
            !isEnabled -> "Disabled"
            unlimited -> "Unlimited"
            else -> "$usageCount / $usageLimit"
        }
}

private data class ProfilePlanInfo(
    val displayName: String,
    val description: String,
    val status: String,
    val isActive: Boolean,
    val startedAt: String?,
    val expiresAt: String?,
    val features: List<ProfilePlanFeature>
)

private val parserJson = Json { ignoreUnknownKeys = true }

private fun parseProfilePlan(
    rawJson: String?,
    fallbackStatus: String?,
    fallbackPlanId: String?
): ProfilePlanInfo {
    val emptyPlan = ProfilePlanInfo(
        displayName = fallbackPlanId.prettifyPlanIdOrDefault("Free Plan"),
        description = "No active subscription. Choose a plan to unlock more.",
        status = fallbackStatus?.takeIf { it.isNotBlank() } ?: "Inactive",
        isActive = fallbackStatus.equals("active", ignoreCase = true),
        startedAt = null,
        expiresAt = null,
        features = emptyList()
    )

    val raw = rawJson?.trim().orEmpty()
    if (raw.isBlank()) return emptyPlan

    val root = runCatching { parserJson.parseToJsonElement(raw).jsonObject }.getOrNull()
        ?: return emptyPlan

    // Nested format from BillingRepository.storeSubscriptionFromBackendResponse:
    //   { "subscription": {...}, "plan": { "name": "...", "lookup_key": "..." } }
    val nestedPlan = root["plan"]?.jsonObject
    val nestedSub = root["subscription"]?.jsonObject

    val planName = nestedPlan?.string("name")
        ?: nestedPlan?.string("lookup_key")
        ?: fallbackPlanId.prettifyPlanIdOrNull()
        ?: root.string("plan_id").prettifyPlanIdOrNull()
        ?: "Current Plan"

    val description = nestedPlan?.string("description").orEmpty()

    val statusRaw = nestedSub?.string("status")
        ?: root.string("status")
        ?: fallbackStatus
        ?: "Inactive"

    val startedAtRaw = nestedSub?.string("started_at") ?: root.string("started_at")
    val expiresAtRaw = nestedSub?.string("expires_at") ?: root.string("expires_at")

    val isActiveFlag = nestedSub?.boolean("is_active")
        ?: root.boolean("is_active")
        ?: statusRaw.equals("active", ignoreCase = true)

    val featuresEl = nestedSub?.get("features")?.let { it as? JsonObject }
        ?: root["features"] as? JsonObject

    val features = featuresEl?.let { parseFeatures(it) } ?: emptyList()

    return ProfilePlanInfo(
        displayName = planName,
        description = description,
        status = if (isActiveFlag) "Active" else statusRaw.replaceFirstChar { it.uppercase() },
        isActive = isActiveFlag,
        startedAt = startedAtRaw?.toShortDate(),
        expiresAt = expiresAtRaw?.toShortDate(),
        features = features
    )
}

private fun parseFeatures(features: JsonObject): List<ProfilePlanFeature> {
    return features.entries.mapNotNull { (key, el) ->
        when (el) {
            is JsonPrimitive -> {
                val enabled = el.booleanOrNull
                if (enabled != null) {
                    ProfilePlanFeature(
                        label = humanize(key),
                        usageCount = 0,
                        usageLimit = null,
                        isEnabled = enabled
                    )
                } else null
            }
            is JsonObject -> {
                val enabled = el["enabled"]?.jsonPrimitive?.booleanOrNull ?: true
                val count = el["usage_count"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                val limit = el["usage_limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ProfilePlanFeature(
                    label = humanize(key),
                    usageCount = count,
                    usageLimit = limit,
                    isEnabled = enabled
                )
            }
            else -> null
        }
    }.sortedBy { it.label }
}

private fun JsonObject.string(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

private fun JsonObject.boolean(key: String): Boolean? =
    this[key]?.jsonPrimitive?.booleanOrNull

private fun String.toShortDate(): String {
    // ISO-8601 like "2026-05-09T12:34:56Z" → "2026-05-09".
    val datePart = substringBefore('T').takeIf { it.length == 10 } ?: this
    return datePart
}

private fun humanize(key: String): String =
    key.split('_', '-').joinToString(" ") { part ->
        part.replaceFirstChar { ch -> ch.uppercase() }
    }

private fun String?.prettifyPlanIdOrNull(): String? =
    this?.takeIf { it.isNotBlank() }?.let { humanize(it) }

private fun String?.prettifyPlanIdOrDefault(default: String): String =
    prettifyPlanIdOrNull() ?: default
