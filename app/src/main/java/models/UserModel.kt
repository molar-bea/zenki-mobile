package models

import org.json.JSONObject

data class UserModel(
    val id: String,
    val fullName: String,
    val email: String,
    val phoneNumber: String? = null,
    val role: String,
    val isDeleted: Boolean = false,
    val isSynchronized: Boolean = false,
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