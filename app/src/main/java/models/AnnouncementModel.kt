package models

import org.json.JSONObject

data class AnnouncementModel(
    val id: String,
    val title: String,
    val body: String? = null,
    val targetRole: String? = null,
    val isDeleted: Boolean = false,
    val createdAt: String
) {
    fun toMap() = mapOf(
        "id" to id,
        "title" to title,
        "body" to body,
        "target_role" to targetRole,
        "is_deleted" to isDeleted,
        "created_at" to createdAt
    )

    fun toJson() = JSONObject(toMap()).toString()

    companion object {
        fun fromMap(map: Map<String, Any?>) = AnnouncementModel(
            id = map["id"]?.toString().orEmpty(),
            title = map["title"]?.toString().orEmpty(),
            body = map["body"]?.toString(),
            targetRole = map["target_role"]?.toString(),
            isDeleted = map["is_deleted"].toString().toBoolean(),
            createdAt = map["created_at"]?.toString().orEmpty()
        )

        fun fromJson(source: String) = fromMap(JSONObject(source).toMap() as Map<String, Any?>)
    }
}