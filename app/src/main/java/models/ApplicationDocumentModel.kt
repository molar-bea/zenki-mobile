package models

import org.json.JSONObject

// Transformed from ApplicationDocumentModel -> ChecklistProgressModel
data class ChecklistProgressModel(
    val id: String,
    val applicationId: String? = null,
    val requirementId: String? = null,
    val userId: String? = null,
    val reviewerRemarks: String? = null,
    val status: String = "prepared", // matches checklist_status_enum
    val isDeleted: Boolean = false,
    val isSynchronized: Boolean = false,
    val createdAt: String
) {
    fun toMap() = mapOf(
        "id" to id,
        "application_id" to applicationId,
        "requirement_id" to requirementId,
        "user_id" to userId,
        "reviewer_remarks" to reviewerRemarks,
        "status" to status,
        "is_deleted" to isDeleted,
        "is_synchronized" to isSynchronized,
        "created_at" to createdAt
    )

    fun toJson() = JSONObject(toMap()).toString()

    companion object {
        fun fromMap(map: Map<String, Any?>) = ChecklistProgressModel(
            id = map["id"]?.toString().orEmpty(),
            applicationId = map["application_id"]?.toString(),
            requirementId = map["requirement_id"]?.toString(),
            userId = map["user_id"]?.toString(),
            reviewerRemarks = map["reviewer_remarks"]?.toString(),
            status = map["status"]?.toString().orEmpty().ifEmpty { "prepared" },
            isDeleted = map["is_deleted"].toString().toBoolean(),
            isSynchronized = map["is_synchronized"].toString().toBoolean(),
            createdAt = map["created_at"]?.toString().orEmpty()
        )

        fun fromJson(source: String) = fromMap(JSONObject(source).toMap())
    }
}