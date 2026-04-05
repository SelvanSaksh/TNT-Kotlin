
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

// ============================================================================
// COLOR EXTENSIONS (Hex to Color)
// ============================================================================

fun Color.Companion.hex(hex: String): Color {
    val cleanHex = hex.removePrefix("#")
    val bigint = cleanHex.toLong(16)
    return when (cleanHex.length) {
        6 -> Color(
            red = ((bigint shr 16) and 0xFF) / 255f,
            green = ((bigint shr 8) and 0xFF) / 255f,
            blue = (bigint and 0xFF) / 255f
        )
        8 -> Color(
            alpha = ((bigint shr 24) and 0xFF) / 255f,
            red = ((bigint shr 16) and 0xFF) / 255f,
            green = ((bigint shr 8) and 0xFF) / 255f,
            blue = (bigint and 0xFF) / 255f
        )
        else -> Color.Black
    }
}

// ============================================================================
// DATA CLASSES & ENUMS
// ============================================================================

enum class PlanType(
    val displayName: String,
    val price: String,
    val period: String,
    val savings: String?,
    val perMonth: String
) {
    MONTHLY("Monthly", "$9.99", "/ month", null, "$9.99/mo"),
    YEARLY("Yearly", "$59.99", "/ year", "Save 50%", "$5.00/mo"),
    LIFETIME("Lifetime", "$149.99", "one time", "Best Value", "Forever")
}

data class ProFeature(
    val icon: ImageVector,
    val iconColor: Color,
    val title: String,
    val description: String
) {
    companion object {
        val all = listOf(
            ProFeature(
                Icons.Default.AllInclusive,
                Color.hex("#2563EB"),
                "Unlimited Scans",
                "Scan as many documents as you need"
            ),
            ProFeature(
                Icons.Default.Cloud,
                Color.hex("#0EA5E9"),
                "Cloud Sync",
                "Access your files from any device"
            ),
            ProFeature(
                Icons.Default.People,
                Color.hex("#7C3AED"),
                "Team Collaboration",
                "Share and work with your team"
            ),
            ProFeature(
                Icons.Default.AutoFixHigh,
                Color.hex("#D97706"),
                "AI Enhancement",
                "Auto-improve scan quality with AI"
            ),
            ProFeature(
                Icons.Default.Security,
                Color.hex("#059669"),
                "Advanced Security",
                "Encrypted storage and secure sharing"
            ),
            ProFeature(
                Icons.Default.BarChart,
                Color.hex("#DC2626"),
                "Analytics Dashboard",
                "Track usage and performance metrics"
            ),
            ProFeature(
                Icons.Default.Headphones,
                Color.hex("#163C66"),
                "Priority Support",
                "24/7 dedicated customer support"
            )
        )
    }
}

data class Testimonial(
    val name: String,
    val role: String,
    val review: String,
    val rating: Int,
    val initials: String,
    val color: Color
) {
    companion object {
        val samples = listOf(
            Testimonial(
                "Sarah M.",
                "Product Manager",
                "Ratifye Pro has completely transformed how our team handles document scanning. Worth every penny!",
                5,
                "SM",
                Color.hex("#7C3AED")
            ),
            Testimonial(
                "James K.",
                "Freelancer",
                "The AI enhancement feature is a game changer. My scans look professional every single time.",
                5,
                "JK",
                Color.hex("#059669")
            ),
            Testimonial(
                "Priya R.",
                "Operations Lead",
                "Cloud sync saves me so much time. I can access everything from my phone or laptop instantly.",
                5,
                "PR",
                Color.hex("#D97706")
            )
        )
    }
}

data class FAQItem(
    val question: String,
    val answer: String
) {
    companion object {
        val samples = listOf(
            FAQItem(
                "Can I cancel anytime?",
                "Yes, you can cancel your subscription at any time. You'll retain access until the end of your billing period."
            ),
            FAQItem(
                "Is there a free trial?",
                "We offer a 7-day free trial for monthly plans. No credit card required to start."
            ),
            FAQItem(
                "What payment methods are accepted?",
                "We accept all major credit cards, Apple Pay, and PayPal."
            ),
            FAQItem(
                "Can I switch plans later?",
                "Absolutely. You can upgrade or downgrade your plan at any time from your profile settings."
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpgradeView(
    onNavigateBack: () -> Unit = {},
    onUpgradeComplete: (PlanType) -> Unit = {}
) {
    var selectedPlan by remember { mutableStateOf(PlanType.YEARLY) }
    var showingPaymentSheet by remember { mutableStateOf(false) }

    Scaffold(
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item { HeroSection() }
                item {
                    PlansSection(
                        selectedPlan = selectedPlan,
                        onPlanSelected = { selectedPlan = it },
                        onContinue = { showingPaymentSheet = true }
                    )
                }
                item { FeaturesSection() }
                item { TestimonialsSection() }
                item { FAQSection() }
                item {
                    CTASection(
                        selectedPlan = selectedPlan,
                        onUpgradeClick = { showingPaymentSheet = true }
                    )
                }
            }
        }
    }

    if (showingPaymentSheet) {
        PaymentSheetDialog(
            plan = selectedPlan,
            onDismiss = { showingPaymentSheet = false },
            onComplete = {
                showingPaymentSheet = false
                onUpgradeComplete(selectedPlan)
            }
        )
    }
}

@Composable
private fun HeroSection() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(320.dp)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.hex("#163C66"),
                        Color.hex("#2563EB")
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
    ) {
        // Decorative circles
        Box(
            modifier = Modifier
                .size(200.dp)
                .offset(x = 280.dp, y = (-40).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        )

        Box(
            modifier = Modifier
                .size(120.dp)
                .offset(x = (-30).dp, y = 200.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.04f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Crown Icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Crown",
                    modifier = Modifier.size(36.dp),
                    tint = Color(0xFFFFD700) // Gold/Yellow
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Go Pro",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Unlock the full power of Ratifye.\nNo limits. No compromise.",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Trust badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                TrustBadge(
                    icon = Icons.Default.Verified,
                    text = "Secure"
                )
                TrustBadge(
                    icon = Icons.Default.Refresh,
                    text = "Cancel Anytime"
                )
                TrustBadge(
                    icon = Icons.Default.Bolt,
                    text = "Instant Access"
                )
            }
        }
    }
}

@Composable
private fun TrustBadge(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(11.dp),
            tint = Color(0xFFFFD700)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White.copy(alpha = 0.9f)
        )
    }
}

// ============================================================================
// PLANS SECTION
// ============================================================================

@Composable
private fun PlansSection(
    selectedPlan: PlanType,
    onPlanSelected: (PlanType) -> Unit,
    onContinue: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Choose Your Plan",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Plan Cards
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PlanType.entries.forEach { plan ->
                PlanCard(
                    plan = plan,
                    isSelected = selectedPlan == plan,
                    onTap = { onPlanSelected(plan) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // CTA Button
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(
                    elevation = 10.dp,
                    spotColor = Color.hex("#163C66").copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp)
                ),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.hex("#163C66"),
                                Color.hex("#2563EB")
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFFFFD700)
                    )
                    Text(
                        text = "Start ${selectedPlan.displayName} Plan",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "No hidden fees. Cancel anytime.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PlanCard(
    plan: PlanType,
    isSelected: Boolean,
    onTap: () -> Unit
) {
    val animatedElevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 4.dp,
        animationSpec = tween(200)
    )

    Card(
        onClick = onTap,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = animatedElevation,
                spotColor = if (isSelected) Color.hex("#2563EB").copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.04f),
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, Color.hex("#2563EB"))
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Radio
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .border(
                            width = 2.dp,
                            color = if (isSelected) Color.hex("#2563EB") else MaterialTheme.colorScheme.outline,
                            shape = CircleShape
                        )
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(Color.hex("#2563EB"))
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Plan Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = plan.displayName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    plan.savings?.let { savings ->
                        val badgeColor = if (savings == "Best Value") {
                            Color.hex("#D1FAE5") to Color.hex("#059669")
                        } else {
                            Color.hex("#FEF3C7") to Color.hex("#D97706")
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.first)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = savings,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor.second
                            )
                        }
                    }
                }

                Text(
                    text = plan.perMonth,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Price
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = plan.price,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.hex("#2563EB") else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = plan.period,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================================================
// FEATURES SECTION
// ============================================================================

@Composable
private fun FeaturesSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Everything You Get",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column {
                ProFeature.all.forEachIndexed { index, feature ->
                    FeatureRow(feature = feature)
                    if (index < ProFeature.all.size - 1) {
                        Divider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(feature: ProFeature) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(feature.iconColor.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = feature.iconColor
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = feature.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = feature.description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Included",
            modifier = Modifier.size(18.dp),
            tint = Color.hex("#2563EB")
        )
    }
}

// ============================================================================
// TESTIMONIALS SECTION
// ============================================================================

@Composable
private fun TestimonialsSection() {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Loved by Thousands",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(Testimonial.samples) { testimonial ->
                TestimonialCard(testimonial = testimonial)
            }
        }
    }
}

@Composable
private fun TestimonialCard(testimonial: Testimonial) {
    Card(
        modifier = Modifier
            .width(260.dp)
            .height(180.dp)
            .shadow(
                elevation = 8.dp,
                spotColor = Color.Black.copy(alpha = 0.05f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
        ) {
            // Stars
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                repeat(testimonial.rating) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = Color(0xFFFFD700)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "\"${testimonial.review}\"",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(testimonial.color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = testimonial.initials,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = testimonial.color
                    )
                }

                Column {
                    Text(
                        text = testimonial.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = testimonial.role,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ============================================================================
// FAQ SECTION
// ============================================================================

@Composable
private fun FAQSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Frequently Asked",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column {
                FAQItem.samples.forEachIndexed { index, item ->
                    FAQRow(item = item)
                    if (index < FAQItem.samples.size - 1) {
                        Divider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FAQRow(item: FAQItem) {
    var isExpanded by remember { mutableStateOf(false) }

    Column {
        TextButton(
            onClick = { isExpanded = !isExpanded },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.question,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Text(
                text = item.answer,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 14.dp)
            )
        }
    }
}

// ============================================================================
// CTA SECTION
// ============================================================================

@Composable
private fun CTASection(
    selectedPlan: PlanType,
    onUpgradeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Divider(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Join 50,000+ Pro users today",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onUpgradeClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .shadow(
                    elevation = 10.dp,
                    spotColor = Color.hex("#163C66").copy(alpha = 0.35f),
                    shape = RoundedCornerShape(14.dp)
                ),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            ),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.hex("#163C66"),
                                Color.hex("#2563EB")
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color(0xFFFFD700)
                    )
                    Text(
                        text = "Upgrade to Pro — ${selectedPlan.price}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Secured by Apple Pay & Stripe",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ============================================================================
// PAYMENT SHEET DIALOG
// ============================================================================

@Composable
private fun PaymentSheetDialog(
    plan: PlanType,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Close button
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.TopEnd
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close"
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = Color(0xFFFFD700)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Ratifye Pro — ${plan.displayName}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${plan.price} ${plan.period}",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                Divider()

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Payment integration coming soon.\nThis is a placeholder sheet.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onComplete,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

// ============================================================================
// PREVIEW / USAGE EXAMPLE
// ============================================================================

/*
// In your App.kt or main entry point:

@Composable
fun App() {
    MaterialTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            UpgradeView(
                onNavigateBack = { /* Handle back navigation */ },
                onUpgradeComplete = { plan ->
                    println("User selected plan: $plan")
                    // Handle successful upgrade selection
                }
            )
        }
    }
}

// For iOS specific entry point (iosMain):
fun MainViewController(): UIViewController = ComposeUIViewController {
    App()
}

// For Android specific entry point (androidMain):
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}

// For Desktop specific entry point (desktopMain):
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Ratifye",
        state = rememberWindowState(width = 400.dp, height = 800.dp)
    ) {
        App()
    }
}

// For Web (WASM/JS) specific entry point (wasmJsMain/jsMain):
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(viewportContainerId = "composeApplication") {
        App()
    }
}
*/