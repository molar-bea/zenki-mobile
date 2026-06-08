package models

import models.toMap
import org.json.JSONObject

// Transformed from ApplicationDocumentModel -> ChecklistProgressModel
data class ChecklistProgressModel(
    val id: String,
    val requirementId: String? = null,
    val userId: String? = null,
    val status: String = "prepared", // matches checklist_status_enum
    val isDeleted: Boolean = false,
    val isSynchronized: Boolean = false,
    val createdAt: String
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "requirement_id" to requirementId,
        "user_id" to userId,
        "status" to status,
        "is_deleted" to isDeleted,
        "is_synchronized" to isSynchronized,
        "created_at" to createdAt
    )

    fun toJson(): String = JSONObject(toMap()).toString()

    companion object {
        fun fromMap(map: Map<String, Any?>): ChecklistProgressModel = ChecklistProgressModel(
            id = map["id"]?.toString().orEmpty(),
            requirementId = map["requirement_id"]?.toString(),
            userId = map["user_id"]?.toString(),
            status = map["status"]?.toString().orEmpty().ifEmpty { "prepared" },
            isDeleted = map["is_deleted"].toString().toBoolean(),
            isSynchronized = map["is_synchronized"].toString().toBoolean(),
            createdAt = map["created_at"]?.toString().orEmpty()
        )

        fun fromJson(source: String): ChecklistProgressModel = fromMap(JSONObject(source).toMap() as Map<String, Any?>)
    }
}
