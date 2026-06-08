package services

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import models.ChecklistProgressWithRequirement
import models.Requirement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ChecklistService {
    suspend fun getAllRequirements(): List<Requirement> {
        return withContext(Dispatchers.IO) {
            try {
                supabase.from("requirements")
                    .select() {
                        filter {
                            eq("is_deleted", false)
                        }
                    }
                    .decodeList<Requirement>()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }

    suspend fun getChecklistForUser(userId: String): List<ChecklistProgressWithRequirement> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Fetch all general requirements
                val allRequirements = getAllRequirements()

                // 2. Fetch user's existing progress
                val userProgress = supabase.from("checklist_progress")
                    .select {
                        filter {
                            eq("user_id", userId)
                            eq("is_deleted", false)
                        }
                    }
                    .decodeList<ChecklistProgressWithRequirement>()

                // 3. Find requirements that the user doesn't have progress for yet
                val existingRequirementIds = userProgress.map { it.requirementId }.toSet()
                val missingRequirements = allRequirements.filter { it.id !in existingRequirementIds }

                // 4. Create missing progress records (default to "todo")
                if (missingRequirements.isNotEmpty()) {
                    val newRecords = missingRequirements.map { req ->
                        mapOf(
                            "user_id" to userId,
                            "requirement_id" to req.id,
                            "status" to "todo",
                            "is_deleted" to false
                        )
                    }
                    supabase.from("checklist_progress").insert(newRecords)
                }

                // 5. Fetch the final list with requirement details joined
                supabase.from("checklist_progress")
                    .select(Columns.raw("*, requirement:requirements(*)")) {
                        filter {
                            eq("user_id", userId)
                            eq("is_deleted", false)
                        }
                    }
                    .decodeList<ChecklistProgressWithRequirement>()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
    }
}
