package com.symphonix.enrollmate.ui.screens

import androidx.compose.foundation.background
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
import androidx.lifecycle.viewModelScope
import com.symphonix.enrollmate.AppViewModel
import com.symphonix.enrollmate.ui.theme.Secondary
import com.symphonix.enrollmate.ui.theme.Tertiary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import services.ApiService

@Composable
fun ChecklistScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsState()
    var tasks by remember { mutableStateOf<List<ChecklistTask>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(settings.currentUserId) {
        settings.currentUserId?.let { userId ->
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                try {
                    val response = ApiService.getChecklist(userId)
                    if (response.optInt("success") == 1) {
                        val data = response.optJSONArray("data") ?: JSONArray()
                        val list = mutableListOf<ChecklistTask>()
                        for (i in 0 until data.length()) {
                            val obj = data.getJSONObject(i)
                            val req = obj.optJSONObject("requirement") ?: org.json.JSONObject()
                            list.add(
                                ChecklistTask(
                                    title = req.optString("name"),
                                    startDate = req.optString("start_date"),
                                    endDate = req.optString("end_date"),
                                    status = obj.optString("status")
                                )
                            )
                        }
                        withContext(Dispatchers.Main) {
                            tasks = list
                        }
                    }
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

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Checklist",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks) { task ->
                    TaskCard(task)
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: ChecklistTask) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Date Start: ${task.startDate}",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "Date End: ${task.endDate}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            StatusPill(status = task.status)
        }
    }
}

@Composable
fun StatusPill(status: String) {
    val backgroundColor = when (status.uppercase()) {
        "COMPLETED" -> Tertiary
        "ON-GOING" -> Secondary
        else -> Color.Gray
    }
    val textColor = Color.Black

    Box(
        modifier = Modifier
            .background(backgroundColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = status.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

data class ChecklistTask(
    val title: String,
    val startDate: String,
    val endDate: String,
    val status: String
)
