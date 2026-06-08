package models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChecklistProgressWithRequirement(
    val id: String,
    @SerialName("application_id")
    val applicationId: String? = null,
    @SerialName("requirement_id")
    val requirementId: String? = null,
    @SerialName("user_id")
    val userId: String? = null,
    @SerialName("reviewer_remarks")
    val reviewerRemarks: String? = null,
    val status: String,
    @SerialName("is_deleted")
    val isDeleted: Boolean = false,
    @SerialName("created_at")
    val createdAt: String,
    val requirement: Requirement? = null
)

@Serializable
data class Requirement(
    val id: String,
    val name: String,
    val description: String? = null,
    @SerialName("start_date")
    val startDate: String? = null,
    @SerialName("end_date")
    val endDate: String? = null,
    @SerialName("is_mandatory")
    val isMandatory: Boolean = true
)
