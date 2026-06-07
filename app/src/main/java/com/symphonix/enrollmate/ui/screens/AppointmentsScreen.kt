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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import com.symphonix.enrollmate.AppViewModel
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import services.supabase

// ── Data model for appointment_schedule (admin-created open slots) ────────────

@Serializable
data class AppointmentScheduleEntry(
    val id: String,
    @SerialName("appointment_type") val appointmentType: String,
    @SerialName("schedule_date") val scheduleDate: String,
    val capacity: Int,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

// ── Data model for appointment (user bookings) — same as your original ────────

@Serializable
data class AppointmentScheduleRow(
    val id: String? = null,
    @SerialName("user_id") val userId: String?,
    @SerialName("appointment_type") val appointmentType: String,
    @SerialName("scheduled_date") val scheduledDate: String,
    val status: String = "scheduled",
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

// ── Receipt pop-up payload ────────────────────────────────────────────────────

data class ReceiptData(
    val type: String,
    val date: String,
    val queueNo: Int
)

// ── Screen ────────────────────────────────────────────────────────────────────

@Composable
fun AppointmentsScreen(viewModel: AppViewModel) {
    val settings by viewModel.settings.collectAsState()
    var selectedCategory by remember { mutableStateOf("Document Submission") }

    // Open schedules fetched from appointment_schedule table
    var availableSchedules by remember { mutableStateOf<List<AppointmentScheduleEntry>>(emptyList()) }

    // User's existing bookings from appointment table
    var appointments by remember { mutableStateOf<List<AppointmentScheduleRow>>(emptyList()) }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Receipt dialog state
    var showReceiptDialog by remember { mutableStateOf(false) }
    var currentReceipt by remember { mutableStateOf<ReceiptData?>(null) }

    // Fetch both open schedules and user's bookings
    LaunchedEffect(settings.currentUserId, selectedCategory) {
        isLoading = true
        errorMessage = null

        viewModel.viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Fetch open schedules from appointment_schedule for this category
                val schedules = supabase.postgrest["appointment_schedule"]
                    .select {
                        filter {
                            eq("appointment_type", selectedCategory)
                            eq("is_deleted", false)
                        }
                    }.decodeList<AppointmentScheduleEntry>()

                // 2. Fetch this user's existing bookings from appointment table
                val fetchedAppointments = settings.currentUserId?.let { userId ->
                    supabase.postgrest["appointment"]
                        .select {
                            filter {
                                eq("user_id", userId)
                                eq("is_deleted", false)
                            }
                        }.decodeList<AppointmentScheduleRow>()
                } ?: emptyList()

                withContext(Dispatchers.Main) {
                    availableSchedules = schedules
                    appointments = fetchedAppointments
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Could not load schedules."
                    isLoading = false
                }
            }
        }
    }

    fun bookAppointment(scheduleDate: String, type: String) {
        errorMessage = null

        // Guard: already has an active booking of this type
        val hasExisting = appointments.any {
            it.appointmentType == type && !it.isDeleted && it.status != "cancelled"
        }
        if (hasExisting) {
            errorMessage = "You already have an active $type appointment booked."
            return
        }

        isLoading = true

        viewModel.viewModelScope.launch(Dispatchers.IO) {
            try {
                // Count existing bookings for this slot to enforce capacity + get queue number
                val existingForSlot = supabase.postgrest["appointment"]
                    .select {
                        filter {
                            eq("scheduled_date", scheduleDate)
                            eq("appointment_type", type)
                            eq("is_deleted", false)
                        }
                    }.decodeList<AppointmentScheduleRow>()

                // Find the capacity for this schedule entry
                val scheduleEntry = availableSchedules.find { it.scheduleDate == scheduleDate }
                val capacity = scheduleEntry?.capacity ?: 50

                if (existingForSlot.size >= capacity) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Sorry, all $type slots for $scheduleDate are fully booked."
                        isLoading = false
                    }
                    return@launch
                }

                val queueNumber = existingForSlot.size + 1

                val newAppointment = AppointmentScheduleRow(
                    userId = settings.currentUserId,
                    appointmentType = type,
                    scheduledDate = scheduleDate
                )

                val insertedRow = supabase.postgrest["appointment"]
                    .insert(newAppointment) { select() }
                    .decodeSingle<AppointmentScheduleRow>()

                withContext(Dispatchers.Main) {
                    appointments = appointments + insertedRow
                    currentReceipt = ReceiptData(type, scheduleDate, queueNumber)
                    showReceiptDialog = true
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "Failed to book: ${e.localizedMessage}"
                    isLoading = false
                }
            }
        }
    }

    // ── Receipt Dialog ────────────────────────────────────────────────────────
    if (showReceiptDialog && currentReceipt != null) {
        AlertDialog(
            onDismissRequest = { showReceiptDialog = false },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(
                    text = "Appointment Confirmed!",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Screenshot this receipt and present it on your scheduled date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Dark receipt card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1C1C2E))
                            .padding(vertical = 24.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "ENROLLMATE",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 3.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "QUEUE NO.",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = "#${String.format("%03d", currentReceipt!!.queueNo)}",
                            fontWeight = FontWeight.Black,
                            fontSize = 56.sp,
                            color = Color.White,
                            letterSpacing = (-1).sp,
                            lineHeight = 60.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "TYPE",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 9.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = currentReceipt!!.type,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "DATE",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 9.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = currentReceipt!!.date,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showReceiptDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // ── Main Layout ───────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FA))
            .padding(24.dp)
    ) {
        Text(
            text = "Appointments",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Category tab row — same labels as original
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf("Document Submission", "Medical Appointment").forEach { category ->
                val isSelected = selectedCategory == category
                Button(
                    onClick = {
                        selectedCategory = category
                        errorMessage = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected)
                            MaterialTheme.colorScheme.primary else Color(0xFFEEEEEE),
                        contentColor = if (isSelected) Color.White else Color(0xFF888888)
                    ),
                    modifier = Modifier.weight(1f).height(44.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text(category, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }

        // Error message
        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .padding(bottom = 12.dp)
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {

            // ── Open Schedules ─────────────────────────────────────────────
            item {
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        text = "Open Schedules",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                    Text(
                        text = "Tap Set to reserve your slot",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF999999)
                    )
                }
            }

            if (isLoading && availableSchedules.isEmpty()) {
                item {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            } else if (availableSchedules.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF0F1EE))
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No open schedules available.",
                            color = Color(0xFFAAAAAA),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                items(availableSchedules, key = { it.id }) { schedule ->
                    OpenScheduleCard(
                        schedule = schedule,
                        isLoading = isLoading,
                        onBook = { bookAppointment(schedule.scheduleDate, selectedCategory) }
                    )
                }
            }

            // ── My Bookings ────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(8.dp))
                Column(modifier = Modifier.padding(bottom = 4.dp)) {
                    Text(
                        text = "Your Bookings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                    Text(
                        text = "Your active appointments in this category",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF999999)
                    )
                }
            }

            val filteredList = appointments.filter { it.appointmentType == selectedCategory }

            if (filteredList.isEmpty() && !isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFF0F1EE))
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No upcoming appointments in this category.",
                            color = Color(0xFFAAAAAA),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            } else {
                items(filteredList, key = { it.id ?: it.scheduledDate }) { appointment ->
                    AppointmentCard(appointment)
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Open Schedule Card ────────────────────────────────────────────────────────

@Composable
private fun OpenScheduleCard(
    schedule: AppointmentScheduleEntry,
    isLoading: Boolean,
    onBook: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = schedule.scheduleDate,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1A1A1A)
                )
                Text(
                    text = "${schedule.capacity} slots total",
                    color = Color(0xFF999999),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                onClick = onBook,
                enabled = !isLoading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = Color(0xFFDDDDDD),
                    disabledContentColor = Color(0xFFAAAAAA)
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Text("Set", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        }
    }
}

// ── Booking History Card ──────────────────────────────────────────────────────

@Composable
fun AppointmentCard(appointment: AppointmentScheduleRow) {
    val statusColor = when (appointment.status.lowercase()) {
        "scheduled" -> Color(0xFF2E7D32)
        "completed" -> Color(0xFF1565C0)
        "cancelled" -> Color(0xFFC62828)
        else -> Color(0xFF666666)
    }
    val statusBg = when (appointment.status.lowercase()) {
        "scheduled" -> Color(0xFFE8F5E9)
        "completed" -> Color(0xFFE3F2FD)
        "cancelled" -> Color(0xFFFFEBEE)
        else -> Color(0xFFF5F5F5)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F1EE)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = appointment.appointmentType,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1A1A1A)
                )
                Text(
                    text = "Scheduled: ${appointment.scheduledDate}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF666666)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(statusBg)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    text = appointment.status.uppercase(),
                    color = statusColor,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}