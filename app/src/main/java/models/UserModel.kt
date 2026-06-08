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
    @SerialName("password_hash")
    val passwordHash: String? = null,
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
        "password_hash" to passwordHash,
        "phone_number" to phoneNumber,
        "role" to role,
        "is_deleted" to isDeleted,
        "is_synchronized" to isSynchronized,
        "created_at" to createdAt
    )

    fun toJson() = JSONObject(toMap()).toString()

    companion object {
        fun fromMap(map: Map<String, Any?>): UserModel {
            val isDeletedVal = map["is_deleted"]
            val isDeleted = when (isDeletedVal) {
                is Boolean -> isDeletedVal
                is Int -> isDeletedVal == 1
                is String -> isDeletedVal.lowercase() == "true" || isDeletedVal == "1"
                else -> false
            }

            val isSyncVal = map["is_synchronized"]
            val isSynchronized = when (isSyncVal) {
                is Boolean -> isSyncVal
                is Int -> isSyncVal == 1
                is String -> isSyncVal.lowercase() == "true" || isSyncVal == "1"
                else -> false
            }

            return UserModel(
                id = map["id"]?.toString().orEmpty(),
                fullName = (map["full_name"] ?: map["fullName"])?.toString().orEmpty(),
                email = map["email"]?.toString().orEmpty(),
                passwordHash = (map["password_hash"] ?: map["passwordHash"])?.toString(),
                phoneNumber = (map["phone_number"] ?: map["phoneNumber"])?.toString(),
                role = map["role"]?.toString().orEmpty(),
                isDeleted = isDeleted,
                isSynchronized = isSynchronized,
                createdAt = map["created_at"]?.toString().orEmpty()
            )
        }

        fun fromJson(source: String) = fromMap(JSONObject(source).toMap() as Map<String, Any?>)
    }
}