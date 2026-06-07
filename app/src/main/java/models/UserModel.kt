package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.json.JSONObject

@Serializable
data class UserModel(
    val id: String,
    @SerialName("full_name")
    val fullName: String,
    val email: String,
    @SerialName("phone_number")
    val phoneNumber: String? = null,
    val role: String,
    @SerialName("is_deleted")
    val isDeleted: Boolean = false,
    @SerialName("is_synchronized")
    val isSynchronized: Boolean = false,
    @SerialName("created_at")
    val createdAt: String
) {
    fun toMap() = mapOf(
        "id" to id,
        "full_name" to fullName,
        "email" to email,
        "phone_number" to phoneNumber,
        "role" to role,
        "is_deleted" to isDeleted,
        "is_synchronized" to isSynchronized,
        "created_at" to createdAt
    )

    fun toJson() = JSONObject(toMap()).toString()

    companion object {
        fun fromMap(map: Map<String, Any?>) = UserModel(
            id = map["id"]?.toString().orEmpty(),
            fullName = map["full_name"]?.toString().orEmpty(),
            email = map["email"]?.toString().orEmpty(),
            phoneNumber = map["phone_number"]?.toString(),
            role = map["role"]?.toString().orEmpty(),
            isDeleted = map["is_deleted"].toString().toBoolean(),
            isSynchronized = map["is_synchronized"].toString().toBoolean(),
            createdAt = map["created_at"]?.toString().orEmpty()
        )

        fun fromJson(source: String) = fromMap(JSONObject(source).toMap() as Map<String, Any?>)
    }
}