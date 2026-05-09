package features.app.subscription

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import core.storage.SessionManager
import core.storage.getLocalStorage
import kotlinx.coroutines.launch

private val Brand = Color(0xFF1F4B73) // (31,75,115)
private val BrandLight = Brand.copy(alpha = 0.08f)
private val ViewBackground = Color(0xFFF8F9FA)
private val TextMuted = Color(0xFF666666)
private val BorderMuted = Color(0xFFE6E6E6)

@Composable
fun SubscriptionScreen(
    onSubscribed: () -> Unit
) {
    val sessionManager = remember { SessionManager(getLocalStorage()) }
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var planItems by remember { mutableStateOf<List<PlanDisplayItem>>(emptyList()) }

    suspend fun load() {
        isLoading = true
        errorMessage = null
        val result = BillingRepository.fetchAll()
        result.onSuccess { bundle ->
            val activePlans = bundle.plans
                .filter { it.status == "published" }
                .sortedBy { it.displayOrder }

            val featureMap = bundle.features
                .filter { it.status == "published" }
                .associateBy { it.id }

            val priceMap = bundle.prices
                .filter { it.status == "published" }
                .associateBy { it.entityId }

            val entitlementsByPlan = bundle.entitlements
                .filter { it.status == "published" }
                .groupBy { it.planId }

            planItems = activePlans.map { plan ->
                val planEnts = entitlementsByPlan[plan.id].orEmpty()
                val featureItems = planEnts.mapNotNull { ent ->
                    val feat = featureMap[ent.featureId] ?: return@mapNotNull null
                    val value = when (ent.featureType) {
                        "boolean" -> if (ent.isEnabled) "Included" else null
                        "static" -> {
                            val sv = ent.staticValue?.trim().orEmpty()
                            if (sv.isNotEmpty()) {
                                val n = sv.toIntOrNull()
                                if (n != null) "$sv ${if (n == 1) "user" else "users"}" else sv
                            } else null
                        }
                        "metered" -> {
                            val limit = ent.usageLimit
                            if (limit != null) {
                                meteredFeatureDisplayValue(
                                    limit = limit,
                                    isSoftLimit = ent.isSoftLimit,
                                    featureName = feat.name
                                )
                            } else null
                        }
                        else -> null
                    }

                    FeatureDisplayItem(
                        id = ent.id,
                        name = feat.name,
                        value = value,
                        isEnabled = ent.isEnabled
                    )
                }

                PlanDisplayItem(
                    id = plan.id,
                    name = plan.name,
                    description = plan.description.orEmpty(),
                    price = priceMap[plan.id],
                    features = featureItems
                )
            }
        }.onFailure { e ->
            errorMessage = e.message ?: "Unable to load plans"
        }
        isLoading = false
    }

    LaunchedEffect(Unit) { load() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ViewBackground)
    ) {
        when {
            isLoading -> LoadingView()
            errorMessage != null -> ErrorView(message = errorMessage.orEmpty(), retry = { scope.launch { load() } })
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    HeaderSection()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        planItems.forEachIndexed { index, plan ->
                            PlanCard(
                                plan = plan,
                                isFeatured = planItems.size == 1 || index == planItems.size / 2,
                                sessionManager = sessionManager,
                                onSuccess = onSubscribed,
                                onError = { msg ->
                                    scope.launch { snack.showSnackbar(msg) }
                                }
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snack,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }
}

@Composable
private fun HeaderSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 28.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .background(BrandLight, RoundedCornerShape(999.dp))
                .border(1.dp, Brand.copy(alpha = 0.35f), RoundedCornerShape(999.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(7.dp).background(Brand.copy(alpha = 0.3f), CircleShape))
            Box(Modifier.size(5.dp).background(Brand, CircleShape))
            Text(
                "PRICING PLANS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = Brand
            )
        }

        Text(
            "Choose Your Plan",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Text(
            "Authenticate products, prevent counterfeits,\nand gain insights — at any scale.",
            fontSize = 14.sp,
            color = TextMuted,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun PlanCard(
    plan: PlanDisplayItem,
    isFeatured: Boolean,
    sessionManager: SessionManager,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var isSubscribing by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFeatured) 6.dp else 2.dp),
        border = BorderStroke(
            width = if (isFeatured) 2.dp else 1.dp,
            color = if (isFeatured) Brand else BorderMuted
        )
    ) {
        Column {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isFeatured) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Box(
                            modifier = Modifier
                                .background(Brand, RoundedCornerShape(999.dp))
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                "Most Popular",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                Text(plan.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                if (plan.description.isNotBlank()) {
                    Text(plan.description, fontSize = 13.sp, color = TextMuted, lineHeight = 16.sp)
                }

                Spacer(Modifier.height(6.dp))

                if (plan.price != null) {
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(plan.price.displayAmount, fontSize = 34.sp, fontWeight = FontWeight.Black, color = Brand)
                        Text("/ ${plan.price.billingPeriod.lowercase()}", fontSize = 13.sp, color = TextMuted)
                    }
                } else {
                    Text("Free", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            if (plan.features.isNotEmpty()) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    plan.features.forEachIndexed { idx, feature ->
                        FeatureRow(feature = feature)
                        if (idx < plan.features.lastIndex) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BrandLight)
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        if (isSubscribing) return@Button
                        isSubscribing = true
                        scope.launch {
                            val result = BillingRepository.subscribeWithRazorpay(
                                planId = plan.id,
                                planName = plan.name,
                                price = plan.price,
                                features = plan.features,
                                sessionManager = sessionManager
                            )
                            result.onSuccess {
                                BillingRepository.syncAndStoreSubscription(sessionManager)
                                onSuccess()
                            }.onFailure { e ->
                                val msg = e.message ?: "Subscription failed"
                                // Mirror iOS behavior: treat "already has an active subscription" as success.
                                if (msg.lowercase().contains("already has an active subscription")) {
                                    BillingRepository.syncAndStoreSubscription(sessionManager)
                                    onSuccess()
                                } else {
                                    onError(msg)
                                }
                            }
                            isSubscribing = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isFeatured) Brand else Color.White,
                        contentColor = if (isFeatured) Color.White else Brand
                    ),
                    border = if (isFeatured) null else BorderStroke(1.5.dp, Brand.copy(alpha = 0.4f)),
                    enabled = !isSubscribing
                ) {
                    if (isSubscribing) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = if (isFeatured) Color.White else Brand,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (plan.price != null) "Get ${plan.name}" else "Start for Free",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(feature: FeatureDisplayItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    if (feature.isEnabled) Brand else Color(0xFFE6E6E6),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (feature.isEnabled) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp, 1.5.dp)
                        .background(Color(0xFFB4B4B4), RoundedCornerShape(999.dp))
                )
            }
        }

        Text(
            feature.name,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )

        if (feature.value != null) {
            Text(
                feature.value,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Brand,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            androidx.compose.material3.CircularProgressIndicator(color = Brand, strokeWidth = 3.dp)
            Text("Loading plans...", fontSize = 14.sp, color = TextMuted)
        }
    }
}

@Composable
private fun ErrorView(message: String, retry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Unable to load plans", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.Black)
            Text(message, fontSize = 13.sp, color = TextMuted, textAlign = TextAlign.Center)
            Button(
                onClick = retry,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Brand)
            ) {
                Text("Try Again", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

