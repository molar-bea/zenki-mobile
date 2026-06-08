package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.json.JSONObject

@Serializable
data class AnnouncementModel(
    val id: String,
    val title: String,
    val body: String? = null,
    @SerialName("target_role")
    val targetRole: String? = null,
    @SerialName("is_deleted")
    val isDeleted: Boolean = false,
    @SerialName("created_at")
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
        fun fromMap(map: Map<String, Any?>): AnnouncementModel {
            val isDeletedVal = map["is_deleted"]
            val isDeleted = when (isDeletedVal) {
                is Boolean -> isDeletedVal
                is Int -> isDeletedVal == 1
                is String -> isDeletedVal.lowercase() == "true" || isDeletedVal == "1"
                else -> false
            }

            return AnnouncementModel(
                id = map["id"]?.toString().orEmpty(),
                title = map["title"]?.toString().orEmpty(),
                body = map["body"]?.toString(),
                targetRole = map["target_role"]?.toString(),
                isDeleted = isDeleted,
                createdAt = (map["created_at"] ?: map["createdAt"])?.toString().orEmpty()
            )
        }

        fun fromJson(source: String) = fromMap(JSONObject(source).toMap() as Map<String, Any?>)
    }
}
