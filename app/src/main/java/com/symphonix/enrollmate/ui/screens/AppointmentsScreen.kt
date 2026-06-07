package com.symphonix.enrollmate.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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

@Composable
fun AppointmentsScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsState()
    var selectedCategory by remember { mutableStateOf("Documents") }
    var appointments by remember { mutableStateOf<List<Appointment>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(settings.currentUserId) {
        settings.currentUserId?.let { userId ->
            viewModel.viewModelScope.launch(Dispatchers.IO) {
                try {
                    val response = ApiService.getRecentAppointments(userId)
                    if (response.optInt("success") == 1) {
                        val data = response.optJSONArray("data") ?: JSONArray()
                        val list = mutableListOf<Appointment>()
                        for (i in 0 until data.length()) {
                            val obj = data.getJSONObject(i)
                            list.add(
                                Appointment(
                                    id = obj.optString("id"),
                                    date = obj.optString("scheduled_date"),
                                    status = obj.optString("status"),
                                    type = obj.optString("appoint_type_enum")
                                )
                            )
                        }
                        withContext(Dispatchers.Main) {
                            appointments = list
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
            text = "Appointments",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Documents", "Medical").forEach { category ->
                Button(
                    onClick = { selectedCategory = category },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedCategory == category) 
                            MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = if (selectedCategory == category) 
                            MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(category)
                }
            }
        }

        // Simplified Calendar Widget
        Card(
            modifier = Modifier.fillMaxWidth().height(200.dp).padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("Calendar Widget Placeholder", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Text(
            text = "Recent Appointment",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(appointments) { appointment ->
                    AppointmentCard(appointment)
                }
            }
        }
    }
}

@Composable
fun AppointmentCard(appointment: Appointment) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = appointment.type,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = appointment.status,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(
                text = "Scheduled: ${appointment.date}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

data class Appointment(
    val id: String,
    val date: String,
    val status: String,
    val type: String
)
