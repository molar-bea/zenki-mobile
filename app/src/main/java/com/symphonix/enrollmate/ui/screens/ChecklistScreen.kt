package com.symphonix.enrollmate.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.symphonix.enrollmate.AppViewModel
import com.symphonix.enrollmate.ui.theme.Secondary
import models.ChecklistProgressWithRequirement
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(viewModel: AppViewModel) {
    val tasks by viewModel.checklist.collectAsState()
    val isLoading by viewModel.isLoadingChecklist.collectAsState()

    // Find the first "ON-GOING" (pending_review) task to highlight
    val activeTaskId = tasks.firstOrNull { it.status == "pending_review" }?.requirementId

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Checklist",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (isLoading && tasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (tasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No tasks found.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(tasks) { task ->
                        TaskCard(
                            task = task,
                            isActive = task.requirementId == activeTaskId,
                            onStatusChange = { newStatus ->
                                task.requirementId?.let { reqId ->
                                    viewModel.updateChecklistStatus(reqId, newStatus)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskCard(
    task: ChecklistProgressWithRequirement,
    isActive: Boolean,
    onStatusChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Secondary),
        border = if (isActive) BorderStroke(2.dp, Color(0xFF2196F3)) else null
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.requirement?.name ?: "Unknown Task",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Date Start: ${task.requirement?.startDate ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
                Text(
                    text = "Date End: ${task.requirement?.endDate ?: "N/A"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
            }

            StatusPill(
                status = task.status,
                onStatusChange = onStatusChange
            )
        }
    }
}

@Composable
fun StatusPill(status: String, onStatusChange: (String) -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    val (backgroundColor, textColor, label) = when (status.lowercase()) {
        "verified", "completed" -> Triple(Color(0xFFC8E6C9), Color(0xFF2E7D32), "COMPLETED")
        "pending_review", "under review", "on-going" -> Triple(Color(0xFFFFCCBC), Color(0xFFD84315), "ON-GOING")
        else -> Triple(Color(0xFFE0E0E0), Color(0xFF616161), "TO-DO")
    }

    Box {
        Surface(
            color = backgroundColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.clickable { showMenu = true }
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("To-Do") },
                onClick = {
                    onStatusChange("prepared")
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("On-Going") },
                onClick = {
                    onStatusChange("pending_review")
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = { Text("Completed") },
                onClick = {
                    onStatusChange("verified")
                    showMenu = false
                }
            )
        }
    }
}