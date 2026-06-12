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
                val reqs = supabase.from("requirement")
                    .select {
                        filter {
                            eq("is_deleted", false)
                        }
                    }
                    .decodeList<Requirement>()
                reqs
            } catch (e: Exception) {
                emptyList<Requirement>()
            }
        }
    }

    suspend fun getChecklistForUser(userId: String): List<ChecklistProgressWithRequirement> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Fetch all general requirements
                val allRequirements = getAllRequirements()

                // 2. Fetch user's existing progress
                val userProgress = try {
                    supabase.from("checklist_progress")
                        .select {
                            filter {
                                eq("user_id", userId)
                                eq("is_deleted", false)
                            }
                        }
                        .decodeList<ChecklistProgressWithRequirement>()
                } catch (e: Exception) {
                    emptyList<ChecklistProgressWithRequirement>()
                }
                
                // 3. Find requirements that the user doesn't have progress for yet
                val existingRequirementIds = userProgress.mapNotNull { it.requirementId }.toSet()
                val missingRequirements = allRequirements.filter { it.id !in existingRequirementIds }
                
                // 4. Create missing progress records (default to "prepared")
                if (missingRequirements.isNotEmpty()) {
                    val newRecords = missingRequirements.map { req ->
                        models.ChecklistProgressModel(
                            id = java.util.UUID.randomUUID().toString(),
                            userId = userId,
                            requirementId = req.id,
                            status = "prepared",
                            isDeleted = false,
                            createdAt = java.time.OffsetDateTime.now().toString()
                        )
                    }
                    try {
                        supabase.from("checklist_progress").insert(newRecords)
                    } catch (e: Exception) {
                        // ignore insert errors
                    }
                }

                // 5. Fetch the final list with requirement details joined
                val rawList = try {
                    supabase.from("checklist_progress")
                        .select(Columns.raw("*, requirement:requirement(*)")) {
                            filter {
                                eq("user_id", userId)
                                eq("is_deleted", false)
                            }
                        }
                        .decodeList<ChecklistProgressWithRequirement>()
                } catch (e: Exception) {
                    emptyList<ChecklistProgressWithRequirement>()
                }

                // Remove duplicates by requirementId to ensure UI only shows each task once
                val list = rawList.distinctBy { it.requirementId }

                // 6. Cache locally in DatabaseService
                if (list is List<ChecklistProgressWithRequirement>) {
                    for (item in list) {
                        try {
                            val model = models.ChecklistProgressModel(
                                id = item.id,
                                requirementId = item.requirementId,
                                userId = item.userId,
                                status = item.status,
                                isDeleted = item.isDeleted,
                                isSynchronized = true,
                                createdAt = item.createdAt
                            )
                            DatabaseService.upsertChecklistProgress(model)
                        } catch (e: Exception) {
                            // ignore cache errors
                        }
                    }
                }

                list
            } catch (e: Exception) {
                emptyList<ChecklistProgressWithRequirement>()
            }
        }
    }

    suspend fun updateChecklistStatus(
        requirementId: String,
        userId: String,
        newStatus: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Update Supabase
                supabase.from("checklist_progress")
                    .update(mapOf("status" to newStatus)) {
                        filter {
                            eq("requirement_id", requirementId)
                            eq("user_id", userId)
                        }
                    }
                
                // 2. Update local DB (find the record first to get its ID)
                val localData = DatabaseService.queryAll("checklist_progress") as List<models.ChecklistProgressModel>
                val record = localData.find { it.requirementId == requirementId && it.userId == userId }
                
                if (record != null) {
                    DatabaseService.upsertChecklistProgress(record.copy(status = newStatus, isSynchronized = true))
                }

                true
            } catch (e: Exception) {
                // ignore
                false
            }
        }
    }
}
