package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import models.toMap
import org.json.JSONObject

@Serializable
data class AnnouncementModel(
    val id: String,
    @SerialName("user_id")
    val userId: String? = null,
    val title: String,
    val body: String? = null,
    val priority: String = "Standard",
    @SerialName("is_pinned")
    val isPinned: Boolean = false,
    @SerialName("is_deleted")
    val isDeleted: Boolean? = false, // Nullable to prevent parse crashes
    @SerialName("created_at")
    val createdAt: String? = null    // Nullable to prevent parse crashes
) {
    fun toMap() = mapOf(
        "id" to id,
        "user_id" to userId,
        "title" to title,
        "body" to body,
        "priority" to priority,
        "is_pinned" to isPinned,
        "is_deleted" to isDeleted,
        "created_at" to createdAt
    )

    fun toJson() = JSONObject(toMap()).toString()

    companion object {
        fun fromMap(map: Map<String, Any?>): AnnouncementModel {
            val isDeletedVal = map["is_deleted"]
            val isDeleted = when (isDeletedVal) {
                is Boolean -> isDeletedVal
                is Int -> isDeletedVal == 1
                is String -> isDeletedVal.lowercase() == "true" || isDeletedVal == "1"
                else -> false
            }

            val isPinnedVal = map["is_pinned"]
            val isPinned = when (isPinnedVal) {
                is Boolean -> isPinnedVal
                is Int -> isPinnedVal == 1
                is String -> isPinnedVal.lowercase() == "true" || isPinnedVal == "1"
                else -> false
            }

            return AnnouncementModel(
                id = map["id"]?.toString().orEmpty(),
                userId = map["user_id"]?.toString(),
                title = map["title"]?.toString().orEmpty(),
                body = map["body"]?.toString(),
                priority = map["priority"]?.toString() ?: "Standard",
                isPinned = isPinned,
                isDeleted = isDeleted,
                createdAt = (map["created_at"] ?: map["createdAt"])?.toString()
            )
        }

        fun fromJson(source: String) = fromMap(JSONObject(source).toMap() as Map<String, Any?>)
    }
}