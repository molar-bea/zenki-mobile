package models

import models.toMap
import org.json.JSONObject

data class ApplicationModel(
    val id: String,
    val applicantId: String? = null,
    val programId: String? = null,
    val userId: String? = null,
    val status: String = "pending",
    val remarks: String? = null,
    val submittedAt: String? = null,
    val isDeleted: Boolean = false,
    val isSynchronized: Boolean = false,
    val createdAt: String
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "applicant_id" to applicantId,
        "program_id" to programId,
        "user_id" to userId,
        "status" to status,
        "remarks" to remarks,
        "submitted_at" to submittedAt,
        "is_deleted" to isDeleted,
        "is_synchronized" to isSynchronized,
        "created_at" to createdAt
    )

    fun toJson(): String = JSONObject(toMap()).toString()

    companion object {
        fun fromMap(map: Map<String, Any?>): ApplicationModel = ApplicationModel(
            id = map["id"]?.toString().orEmpty(),
            applicantId = map["applicant_id"]?.toString(),
            programId = map["program_id"]?.toString(),
            userId = map["user_id"]?.toString(),
            status = map["status"]?.toString().orEmpty().ifEmpty { "pending" },
            remarks = map["remarks"]?.toString(),
            submittedAt = map["submitted_at"]?.toString(),
            isDeleted = map["is_deleted"].toString().toBoolean(),
            isSynchronized = map["is_synchronized"].toString().toBoolean(),
            createdAt = map["created_at"]?.toString().orEmpty()
        )

        fun fromJson(source: String): ApplicationModel = fromMap(JSONObject(source).toMap() as Map<String, Any?>)
    }
}
