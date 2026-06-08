package services

import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import models.ChecklistProgressWithRequirement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ChecklistService {
    suspend fun getChecklistForUser(userId: String): List<ChecklistProgressWithRequirement> {
        return withContext(Dispatchers.IO) {
            try {
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
