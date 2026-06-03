package models

import org.json.JSONObject

data class ProgramModel(
    val id: String,
    val name: String,
    val description: String? = null,
    val department: String? = null,
    val slotsAvailable: Int? = null,
    val applicationStartDate: String? = null,
    val applicationEndDate: String? = null,
    val isDeleted: Boolean = false,
    val createdAt: String
) {
    fun toMap() = mapOf(
        "id" to id,
        "name" to name,
        "description" to description,
        "department" to department,
        "slots_available" to slotsAvailable,
        "application_start_date" to applicationStartDate,
        "application_end_date" to applicationEndDate,
        "is_deleted" to isDeleted,
        "created_at" to createdAt
    )

    fun toJson() = JSONObject(toMap()).toString()

    companion object {
        fun fromMap(map: Map<String, Any?>) = ProgramModel(
            id = map["id"]?.toString().orEmpty(),
            name = map["name"]?.toString().orEmpty(),
            description = map["description"]?.toString(),
            department = map["department"]?.toString(),
            slotsAvailable = map["slots_available"]?.toString()?.toIntOrNull(),
            applicationStartDate = map["application_start_date"]?.toString(),
            applicationEndDate = map["application_end_date"]?.toString(),
            isDeleted = map["is_deleted"].toString().toBoolean(),
            createdAt = map["created_at"]?.toString().orEmpty()
        )

        fun fromJson(source: String) = fromMap(JSONObject(source).toMap() as Map<String, Any?>)
    }
}