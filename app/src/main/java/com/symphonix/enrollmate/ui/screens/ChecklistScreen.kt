package com.symphonix.enrollmate.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.symphonix.enrollmate.AppViewModel
import com.symphonix.enrollmate.ui.theme.Secondary
import kotlinx.coroutines.launch
import models.ChecklistProgressWithRequirement
import models.Requirement
import services.ChecklistService
import androidx.compose.runtime.collectAsState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(viewModel: AppViewModel) {
    val settingsState = viewModel.settings.collectAsState()
    val settings = settingsState.value
    var tasks by remember { mutableStateOf<List<ChecklistProgressWithRequirement>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(settings.currentUserId) {
        settings.currentUserId?.let { userId ->
            scope.launch {
                try {
                    val fetchedTasks = ChecklistService.getChecklistForUser(userId)
                    tasks = fetchedTasks
                } catch (e: Exception) {
                    // handle error
                } finally {
                    isLoading = false
                }
            }
        } ?: run {
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Checklist",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { /* Handle back - maybe pass navController */ }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
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
            if (isLoading) {
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
                        TaskCard(task)
                    }
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: ChecklistProgressWithRequirement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Secondary)
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
            
            StatusPill(status = task.status)
        }
    }
}

@Composable
fun StatusPill(status: String) {
    val (backgroundColor, textColor, label) = when (status.lowercase()) {
        "completed" -> Triple(Color(0xFFC8E6C9), Color(0xFF2E7D32), "COMPLETED")
        "on-going" -> Triple(Color(0xFFFFCCBC), Color(0xFFD84315), "ON-GOING")
        else -> Triple(Color(0xFFE0E0E0), Color(0xFF616161), "TO-DO")
    }

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true)
@Composable
fun ChecklistScreenPreview() {
    val mockTasks = listOf(
        ChecklistProgressWithRequirement(
            id = "1",
            status = "completed",
            createdAt = "2026-06-02",
            requirement = Requirement(
                id = "req1",
                name = "Submit credentials to the admissions office.",
                startDate = "06/02/2026",
                endDate = "06/15/2026"
            )
        ),
        ChecklistProgressWithRequirement(
            id = "2",
            status = "on-going",
            createdAt = "2026-06-02",
            requirement = Requirement(
                id = "req2",
                name = "Second Task",
                startDate = "06/02/2026",
                endDate = "06/15/2026"
            )
        ),
        ChecklistProgressWithRequirement(
            id = "3",
            status = "todo",
            createdAt = "2026-06-02",
            requirement = Requirement(
                id = "req3",
                name = "Third task",
                startDate = "06/02/2026",
                endDate = "06/15/2026"
            )
        )
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Checklist",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(mockTasks) { task ->
                    TaskCard(task)
                }
            }
        }
    }
}
