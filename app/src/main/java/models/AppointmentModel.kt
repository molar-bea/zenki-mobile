package models

import models.toMap
import org.json.JSONObject

data class AppointmentModel(
    val id: String,
    val appointmentType: String,
    val scheduledDate: String,
    val status: String = "scheduled",
    val isDeleted: Boolean = false,
    val createdAt: String
) {
    fun toMap() = mapOf(
        "id" to id,
        "appointment_type" to appointmentType,
        "scheduled_date" to scheduledDate,
        "status" to status,
        "is_deleted" to isDeleted,
        "created_at" to createdAt
    )

    fun toJson() = JSONObject(toMap()).toString()

    companion object {
        fun fromMap(map: Map<String, Any?>) = AppointmentModel(
            id = map["id"]?.toString().orEmpty(),
            appointmentType = map["appointment_type"]?.toString().orEmpty(),
            scheduledDate = map["scheduled_date"]?.toString().orEmpty(),
            status = map["status"]?.toString().orEmpty().ifEmpty { "scheduled" },
            isDeleted = map["is_deleted"].toString().toBoolean(),
            createdAt = map["created_at"]?.toString().orEmpty()
        )

        fun fromJson(source: String) = fromMap(JSONObject(source).toMap())
    }
}

