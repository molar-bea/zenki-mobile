package com.symphonix.enrollmate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.symphonix.enrollmate.AppViewModel
import com.symphonix.enrollmate.ui.Screen

@Composable
fun ProfileScreen(navController: NavController, viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsState()
    var showSignOutDialog by remember { mutableStateOf(false) }

    // ── Sign-out confirmation dialog ──────────────────────────────────────────
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Sign Out",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to sign out of your account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF666666)
                )
            },
            dismissButton = {
                Button(
                    onClick = { showSignOutDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEEEEEE),
                        contentColor = Color(0xFF888888)
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text("Cancel", fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutDialog = false
                        viewModel.logout()
                        navController.navigate(Screen.Landing.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC62828),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // ── Main layout ───────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(24.dp)
    ) {

        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Profile",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = { showSignOutDialog = true },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFFEBEE))
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Sign Out",
                    tint = Color(0xFFC62828),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Avatar + name card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Avatar circle - first letter of full name
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (settings.currentUserFullName
                            ?.trim()
                            ?.firstOrNull()
                            ?.uppercaseChar()
                            ?.toString()
                            ?: "?"),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = settings.currentUserFullName ?: "User Name",   // ← full name
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                    Text(
                        text = settings.currentUserEmail ?: "email@example.com",  // ← email, not ID
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF999999)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Settings section label
        Column(modifier = Modifier.padding(bottom = 10.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            Text(
                text = "Customize your app experience",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF999999)
            )
        }

        // Settings card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column {
                SettingsToggleRow(
                    label = "Use large texts",
                    sublabel = "Increases font size across the app",
                    checked = settings.useLargeTexts,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(useLargeTexts = it)) }
                )

                HorizontalDivider(
                    color = Color(0xFFF0F1EE),
                    thickness = 1.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                SettingsToggleRow(
                    label = "Keep screen on",
                    sublabel = "Prevents screen from sleeping while in the app",
                    checked = settings.keepScreenOn,
                    onCheckedChange = { viewModel.updateSettings(settings.copy(keepScreenOn = it)) }
                )
            }
        }


    }
}

// ── Reusable settings row ─────────────────────────────────────────────────────

@Composable
private fun SettingsToggleRow(
    label: String,
    sublabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Color(0xFF1A1A1A)
            )
            Text(
                text = sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF999999)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color(0xFFDDDDDD),
                uncheckedBorderColor = Color(0xFFDDDDDD)
            )
        )
    }
}