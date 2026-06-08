package models

import org.json.JSONObject

data class RequirementModel(
    val id: String,
    val programId: String? = null,
    val userId: String? = null,
    val name: String,
    val description: String? = null,
    val startDate: String? = null,
    val endDate: String? = null,
    val isMandatory: Boolean = true,
    val stepOrder: Int = 1,
    val isDeleted: Boolean = false,
    val createdAt: String
) {
    fun toMap() = mapOf(
        "id" to id,
        "program_id" to programId,
        "user_id" to userId,
        "name" to name,
        "description" to description,
        "start_date" to startDate,
        "end_date" to endDate,
        "is_mandatory" to isMandatory,
        "step_order" to stepOrder,
        "is_deleted" to isDeleted,
        "created_at" to createdAt
    )

    fun toJson() = JSONObject(toMap()).toString()

    companion object {
        fun fromMap(map: Map<String, Any?>) = RequirementModel(
            id = map["id"]?.toString().orEmpty(),
            programId = map["program_id"]?.toString(),
            userId = map["user_id"]?.toString(),
            name = map["name"]?.toString().orEmpty(),
            description = map["description"]?.toString(),
            startDate = map["start_date"]?.toString(),
            endDate = map["end_date"]?.toString(),
            isMandatory = map["is_mandatory"]?.toString()?.toBoolean() ?: true,
            stepOrder = map["step_order"]?.toString()?.toIntOrNull() ?: 1,
            isDeleted = map["is_deleted"].toString().toBoolean(),
            createdAt = map["created_at"]?.toString().orEmpty()
        )

        fun fromJson(source: String) = fromMap(JSONObject(source).toMap() as Map<String, Any?>)
    }
}