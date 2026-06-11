package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import models.toMap
import org.json.JSONObject

@Serializable
data class ChecklistProgressModel(
    val id: String,
    @SerialName("requirement_id")
    val requirementId: String? = null,
    @SerialName("user_id")
    val userId: String? = null,
    val status: String = "prepared", // matches checklist_status_enum
    @SerialName("is_deleted")
    val isDeleted: Boolean = false,
    val isSynchronized: Boolean = false,
    @SerialName("created_at")
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
