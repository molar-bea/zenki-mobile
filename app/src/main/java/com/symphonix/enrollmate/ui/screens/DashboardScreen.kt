package com.symphonix.enrollmate.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import com.symphonix.enrollmate.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import services.ApiService
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsState()
    var announcements by remember { mutableStateOf<List<Announcement>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val calendar = Calendar.getInstance()
    val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.getDefault())
    val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
    val currentDate = dateFormat.format(calendar.time)
    val currentDay = dayFormat.format(calendar.time)

    LaunchedEffect(Unit) {
        viewModel.viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = ApiService.getAnnouncements()
                if (response.optInt("success") == 1) {
                    val data = response.optJSONArray("data") ?: JSONArray()
                    val list = mutableListOf<Announcement>()
                    for (i in 0 until data.length()) {
                        val obj = data.getJSONObject(i)
                        list.add(
                            Announcement(
                                title = obj.optString("title"),
                                body = obj.optString("body"),
                                createdAt = obj.optString("created_at")
                            )
                        )
                    }
                    withContext(Dispatchers.Main) {
                        announcements = list
                    }
                }
            } catch (e: Exception) {
                // handle error
            } finally {
                isLoading = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Welcome, ${settings.currentUserFullName ?: "User"}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "$currentDate ($currentDay)",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        Text(
            text = "Announcements",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(announcements) { announcement ->
                    AnnouncementCard(announcement)
                }
            }
        }
    }
}

@Composable
fun AnnouncementCard(announcement: Announcement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = announcement.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = announcement.createdAt.split("T")[0],
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = announcement.body,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

data class Announcement(
    val title: String,
    val body: String,
    val createdAt: String
)
