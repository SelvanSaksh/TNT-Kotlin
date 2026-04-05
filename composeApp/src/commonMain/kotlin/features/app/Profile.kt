package features.profile

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import navigation.AppScreen

// 🔷 Colors
private val Navy = Color(0xFF163C66)
private val NavyDark = Color(0xFF0D2540)
private val PageBg = Color(0xFFF5F6FA)

// ─────────────────────────────────────────────────────────────

@Composable
fun ProfileScreen(
    userName: String? = "User",
    email: String? = "No email",
    role: Int? = 0,
    onNavigate: (AppScreen) -> Unit = {},
    onLogout: () -> Unit = {}
) {
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(Navy, NavyDark))
                    )
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(Navy, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userName?.take(2)?.uppercase() ?: "U",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    if (userName != null) {
                        Text(
                            userName,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (email != null) {
                        Text(
                            email,
                            color = Color.White.copy(0.7f),
                            fontSize = 13.sp
                        )
                    }

                    Spacer(Modifier.height(6.dp))

//                    Text(
//                        role,
//                        color = Color.White,
//                        fontSize = 12.sp,
//                        modifier = Modifier
//                            .background(Color.White.copy(0.2f), RoundedCornerShape(50))
//                            .padding(horizontal = 12.dp, vertical = 4.dp)
//                    )
                }
            }

            // 🔷 PRO BANNER
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(listOf(Navy, Color(0xFF1E4A7A))),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp)
            ) {
                Column {

                    Text(
                        "Upgrade to Pro",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(8.dp))

                    Text(
                        "Unlock unlimited scans and cloud sync",
                        color = Color.White.copy(0.8f),
                        fontSize = 13.sp
                    )

                    Spacer(Modifier.height(12.dp))

                    Button(
                        onClick = {},
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("Upgrade Now", color = Navy)
                    }
                }
            }

            // 🔷 SETTINGS
            Column(modifier = Modifier.padding(16.dp)) {

                Text("Preferences", fontSize = 12.sp, color = Color.Gray)

                Spacer(Modifier.height(8.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column {

                        // Notifications
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Notifications", modifier = Modifier.weight(1f))
                            Switch(
                                checked = notificationsEnabled,
                                onCheckedChange = { notificationsEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Navy
                                )
                            )
                        }

                        Divider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text("Security & Privacy")
                        }

                        Divider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text("Language")
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 🔴 Logout
                Card {
                    TextButton(
                        onClick = { showLogout = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign Out", color = Color.Red)
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    "Version 1.0.0 • Ratifye",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }

        // 🔴 Logout Dialog
        if (showLogout) {
            AlertDialog(
                onDismissRequest = { showLogout = false },
                title = { Text("Sign Out") },
                text = { Text("Are you sure you want to sign out?") },
                confirmButton = {
                    TextButton(onClick = {
                        showLogout = false
                        onLogout()
                    }) {
                        Text("Sign Out", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLogout = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}