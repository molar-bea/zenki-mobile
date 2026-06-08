package com.symphonix.enrollmate.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    import java.text.SimpleDateFormat
import java.util.Locale

// ── Helpers ───────────────────────────────────────────────────────────────────

fun formatToWebDate(dateString: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formatter = SimpleDateFormat("EEEE, MMM d, yyyy", Locale.getDefault())
        val date = parser.parse(dateString)
        if (date != null) formatter.format(date) else dateString
    } catch (e: Exception) {
        dateString
    }
}

// ── Data Models ───────────────────────────────────────────────────────────────

@Serializable
data class AppointmentScheduleEntry(
    val id: String,
    @SerialName("appointment_type") val appointmentType: String,
    @SerialName("schedule_date") val scheduleDate: String,
    val capacity: Int,
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AppointmentScheduleRow(
    val id: String? = null,
    @SerialName("schedule_id") val scheduleId: String? = null,
    @SerialName("user_id") val userId: String?,
    @SerialName("appointment_type") val appointmentType: String,
    @SerialName("scheduled_date") val scheduledDate: String,
    val status: String = "scheduled",
    @SerialName("is_deleted") val isDeleted: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

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

    var availableSchedules by remember { mutableStateOf<List<AppointmentScheduleEntry>>(emptyList()) }
    var appointments by remember { mutableStateOf<List<AppointmentScheduleRow>>(emptyList()) }

    // NEW: Tracks how many people have booked each schedule ID globally
    var scheduleCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showReceiptDialog by remember { mutableStateOf(false) }
    var currentReceipt by remember { mutableStateOf<ReceiptData?>(null) }

    var appointmentToCancel by remember { mutableStateOf<AppointmentScheduleRow?>(null) }
    var isCancelling by remember { mutableStateOf(false) }

    LaunchedEffect(settings.currentUserId, selectedCategory) {
        isLoading = true
        errorMessage = null

        viewModel.viewModelScope.launch(Dispatchers.IO) {
            try {
                // 1. Fetch available schedules
                val schedules = supabase.postgrest["appointment_schedule"]
                    .select {
                        filter {
                            eq("appointment_type", selectedCategory)
                            eq("is_deleted", false)
                        }
                    }.decodeList<AppointmentScheduleEntry>()

                // 2. NEW: Fetch ALL active global appointments to calculate capacities
                val globalBookings = supabase.postgrest["appointment"]
                    .select {
                        filter {
                            eq("appointment_type", selectedCategory)
                            eq("is_deleted", false)
                            neq("status", "cancelled") // Do not count cancelled bookings!
                        }
                    }.decodeList<AppointmentScheduleRow>()

                // Count how many bookings exist for each schedule ID
                val counts = globalBookings.groupingBy { it.scheduleId ?: "" }.eachCount()

                withContext(Dispatchers.Main) {
                    availableSchedules = schedules
                    scheduleCounts = counts
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { errorMessage = "Admin Table Error: ${e.localizedMessage}" }
            }

            try {
                // 3. Fetch the logged-in user's personal appointments
                settings.currentUserId?.let { userId ->
                    val fetchedAppointments = supabase.postgrest["appointment"]
                        .select {
                            filter {
                                eq("user_id", userId)
                                eq("is_deleted", false)
                            }
                        }.decodeList<AppointmentScheduleRow>()
                    withContext(Dispatchers.Main) { appointments = fetchedAppointments }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { errorMessage = "User Table Error: ${e.localizedMessage}" }
            }
            withContext(Dispatchers.Main) { isLoading = false }
        }
    }

    fun bookAppointment(scheduleDate: String, type: String) {
        errorMessage = null
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
                val scheduleEntry = availableSchedules.find { it.scheduleDate == scheduleDate }
                val capacity = scheduleEntry?.capacity ?: 50

                // Use our global map to get the real-time capacity count
                val currentBooked = scheduleCounts[scheduleEntry?.id ?: ""] ?: 0

                if (currentBooked >= capacity) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "Sorry, all $type slots for $scheduleDate are fully booked."
                        isLoading = false
                    }
                    return@launch
                }

                val queueNumber = currentBooked + 1
                val newAppointment = AppointmentScheduleRow(
                    userId = settings.currentUserId,
                    scheduleId = scheduleEntry?.id,
                    appointmentType = type,
                    scheduledDate = scheduleDate
                )

                val insertedRow = supabase.postgrest["appointment"]
                    .insert(newAppointment) { select() }
                    .decodeSingle<AppointmentScheduleRow>()

                withContext(Dispatchers.Main) {
                    appointments = appointments + insertedRow

                    // Instantly update the UI slot count
                    val sId = scheduleEntry?.id
                    if (sId != null) {
                        scheduleCounts = scheduleCounts.toMutableMap().apply {
                            this[sId] = (this[sId] ?: 0) + 1
                        }
                    }

                    currentReceipt = ReceiptData(type, formatToWebDate(scheduleDate), queueNumber)
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
                Text("Appointment Confirmed!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Screenshot this receipt and present it on your scheduled date.", style = MaterialTheme.typography.bodySmall, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(vertical = 24.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("ENROLLMATE", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Spacer(Modifier.height(16.dp))
                        Text("QUEUE NO.", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, letterSpacing = 1.5.sp)
                        Text(
                            text = "#${String.format("%03d", currentReceipt!!.queueNo)}",
                            fontWeight = FontWeight.Black,
                            fontSize = 56.sp,
                            color = Color.White,
                            letterSpacing = (-1).sp,
                            lineHeight = 60.sp
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.3f), modifier = Modifier.padding(horizontal = 4.dp))
                        Spacer(Modifier.height(16.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("TYPE", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, letterSpacing = 1.sp)
                                Text(currentReceipt!!.type, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("DATE", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, letterSpacing = 1.sp)
                                Text(currentReceipt!!.date, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showReceiptDialog = false }, shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }

    // ── Cancellation Dialog ───────────────────────────────────────────────────
    if (appointmentToCancel != null) {
        AlertDialog(
            onDismissRequest = { if (!isCancelling) appointmentToCancel = null },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text("Cancel Appointment", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
            },
            text = {
                Text("Are you sure you want to cancel your ${appointmentToCancel?.appointmentType} appointment on ${formatToWebDate(appointmentToCancel?.scheduledDate ?: "")}? This action cannot be undone.", color = Color.Gray)
            },
            dismissButton = {
                Button(
                    onClick = { appointmentToCancel = null },
                    enabled = !isCancelling,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEEEEE), contentColor = Color(0xFF888888)),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text("Back", fontWeight = FontWeight.SemiBold)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isCancelling = true
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            try {
                                val targetId = appointmentToCancel?.id
                                if (targetId != null) {
                                    val updatedRow = appointmentToCancel!!.copy(status = "cancelled")
                                    supabase.postgrest["appointment"].update(updatedRow) {
                                        filter { eq("id", targetId) }
                                    }

                                    withContext(Dispatchers.Main) {
                                        // Update the user's booking history
                                        appointments = appointments.map {
                                            if (it.id == targetId) updatedRow else it
                                        }

                                        // Instantly free up a slot in the UI!
                                        val sId = appointmentToCancel?.scheduleId
                                        if (sId != null) {
                                            scheduleCounts = scheduleCounts.toMutableMap().apply {
                                                this[sId] = (this[sId] ?: 1) - 1
                                            }
                                        }

                                        appointmentToCancel = null
                                        isCancelling = false
                                    }
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    errorMessage = "Failed to cancel: ${e.localizedMessage}"
                                    appointmentToCancel = null
                                    isCancelling = false
                                }
                            }
                        }
                    },
                    enabled = !isCancelling,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    if (isCancelling) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Cancel Booking", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        )
    }

    // ── Main Layout ───────────────────────────────────────────────────────────
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp)
    ) {
        Text(
            text = "Appointments",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf("Document Submission", "Medical Appointment").forEach { category ->
                val isSelected = selectedCategory == category
                Button(
                    onClick = { selectedCategory = category; errorMessage = null },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFF3F4F6),
                        contentColor = if (isSelected) Color.White else Color(0xFF4B5563)
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text(category, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, textAlign = TextAlign.Center, lineHeight = 15.sp)
                }
            }
        }

        if (errorMessage != null) {
            Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.error.copy(alpha = 0.08f)).padding(horizontal = 12.dp, vertical = 8.dp).padding(bottom = 12.dp))
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {

            item {
                Text("Open Schedules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
            }

            if (isLoading && availableSchedules.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp, modifier = Modifier.size(26.dp)) } }
            } else if (availableSchedules.isEmpty()) {
                item { Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF9FAFB)).padding(24.dp), contentAlignment = Alignment.Center) { Text("No open schedules available.", color = Color(0xFF9CA3AF), style = MaterialTheme.typography.bodySmall) } }
            } else {
                items(availableSchedules, key = { it.id }) { schedule ->
                    // Pass the real-time capacity count down to the card
                    val bookedCount = scheduleCounts[schedule.id] ?: 0
                    OpenScheduleCard(
                        schedule = schedule,
                        bookedCount = bookedCount,
                        isLoading = isLoading,
                        onBook = { bookAppointment(schedule.scheduleDate, selectedCategory) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(16.dp))
                Text("Your Bookings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1F2937))
            }

            val filteredList = appointments.filter { it.appointmentType == selectedCategory }

            if (filteredList.isEmpty() && !isLoading) {
                item { Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF9FAFB)).padding(24.dp), contentAlignment = Alignment.Center) { Text("No upcoming appointments in this category.", color = Color(0xFF9CA3AF), style = MaterialTheme.typography.bodySmall) } }
            } else {
                items(filteredList, key = { it.id ?: it.scheduledDate }) { appointment ->
                    AppointmentCard(appointment, onCancelClick = { appointmentToCancel = appointment })
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun OpenScheduleCard(
    schedule: AppointmentScheduleEntry,
    bookedCount: Int,
    isLoading: Boolean,
    onBook: () -> Unit
) {
    val isFull = bookedCount >= schedule.capacity

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatToWebDate(schedule.scheduleDate),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1F2937)
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = schedule.appointmentType.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4B5563),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                // Displays dynamic slot usage! (e.g. "Slots: 3 / 40")
                Text(
                    text = "Slots: $bookedCount / ${schedule.capacity}",
                    color = if (isFull) Color(0xFFB91C1C) else Color(0xFF6B7280),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onBook,
                    enabled = !isLoading && !isFull, // Automatically disables if full
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        disabledContainerColor = Color(0xFFE5E7EB),
                        disabledContentColor = Color(0xFF9CA3AF)
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    } else {
                        Text(if (isFull) "Full" else "Reserve", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = if(isFull) Color(0xFF9CA3AF) else Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun AppointmentCard(appointment: AppointmentScheduleRow, onCancelClick: () -> Unit) {
    val statusColor = when (appointment.status.lowercase()) {
        "scheduled" -> Color(0xFF047857)
        "completed" -> Color(0xFF1D4ED8)
        "cancelled" -> Color(0xFFB91C1C)
        else -> Color(0xFF4B5563)
    }
    val statusBg = when (appointment.status.lowercase()) {
        "scheduled" -> Color(0xFFD1FAE5)
        "completed" -> Color(0xFFDBEAFE)
        "cancelled" -> Color(0xFFFEE2E2)
        else -> Color(0xFFF3F4F6)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatToWebDate(appointment.scheduledDate),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color(0xFF1F2937)
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = appointment.appointmentType.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4B5563),
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(statusBg)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = appointment.status.uppercase(),
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                if (appointment.status.lowercase() == "scheduled") {
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onCancelClick() }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Cancel Booking",
                            color = Color(0xFFB91C1C),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}