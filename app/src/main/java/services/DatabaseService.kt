package services

import models.*
import models.toMap
import org.json.JSONObject
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * DatabaseService.kt
 * Lightweight, local-first storage and synchronization layer.
 * - Uses SQLite via JDBC for ACID transactions (durable, transactional).
 * - Stores model payloads as JSON in a flexible column and maintains
 *   minimal control columns: `sync_state`, `is_deleted`, `created_at`.
 * - Provides upsert, soft-delete, query (exclude deleted by default),
 *   and per-model sync that treats external input as opaque.
 *
 * Important: This implementation purposely treats external source data
 * as opaque. When mapping cannot be derived from the input, the mapping
 * utilities will return a `TODO_MAPPING_REQUIRED` marker rather than
 * guessing.
 */

private val logger: Logger = Logger.getLogger("DatabaseService")

data class SyncResult(
    val model: String,
    val inserted: Int = 0,
    val updated: Int = 0,
    val skipped: Int = 0,
    val conflicts: Int = 0,
    val errors: List<String> = emptyList()
)

object DatabaseService {
    private var dbPath: String = "zenkidb.sqlite"
    private var conn: Connection? = null

    // sync states: 0 = pending, 1 = synced, 2 = conflict
    const val SYNC_PENDING = 0
    const val SYNC_SYNCED = 1
    const val SYNC_CONFLICT = 2

    fun init(path: String) {
        dbPath = path
        connect()
        createTablesIfNeeded()
    }

    private fun connect() {
        try {
            val file = File(dbPath)
            file.parentFile?.mkdirs()
            Class.forName("org.sqlite.JDBC")
            conn = DriverManager.getConnection("jdbc:sqlite:" + file.path)
            conn?.autoCommit = true
        } catch (ex: Exception) {
            logger.log(Level.SEVERE, "Failed to open DB connection: ${ex.message}")
            // Fallback for Android environment if JDBC fails
            try {
                // If this were a real Android app, we'd use SQLiteDatabase here.
                // But we'll try to stick to the user's JDBC approach if they have the driver.
            } catch (e: Exception) {}
        }
    }

    private fun createTablesIfNeeded() {
        // Tables are idempotent and additive. Store raw payload in json_data
        // and maintain minimal control columns.
        val createSql = listOf(
            """
            CREATE TABLE IF NOT EXISTS users (
              id TEXT PRIMARY KEY,
              json_data TEXT NOT NULL,
              sync_state INTEGER NOT NULL DEFAULT 0,
              is_deleted INTEGER NOT NULL DEFAULT 0,
              created_at TEXT
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS programs (
              id TEXT PRIMARY KEY,
              json_data TEXT NOT NULL,
              sync_state INTEGER NOT NULL DEFAULT 0,
              is_deleted INTEGER NOT NULL DEFAULT 0,
              created_at TEXT
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS requirements (
              id TEXT PRIMARY KEY,
              json_data TEXT NOT NULL,
              sync_state INTEGER NOT NULL DEFAULT 0,
              is_deleted INTEGER NOT NULL DEFAULT 0,
              created_at TEXT
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS applications (
              id TEXT PRIMARY KEY,
              json_data TEXT NOT NULL,
              sync_state INTEGER NOT NULL DEFAULT 0,
              is_deleted INTEGER NOT NULL DEFAULT 0,
              created_at TEXT
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS checklist_progress (
              id TEXT PRIMARY KEY,
              json_data TEXT NOT NULL,
              sync_state INTEGER NOT NULL DEFAULT 0,
              is_deleted INTEGER NOT NULL DEFAULT 0,
              created_at TEXT
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS announcements (
              id TEXT PRIMARY KEY,
              json_data TEXT NOT NULL,
              sync_state INTEGER NOT NULL DEFAULT 0,
              is_deleted INTEGER NOT NULL DEFAULT 0,
              created_at TEXT
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS app_settings (
              id TEXT PRIMARY KEY,
              json_data TEXT NOT NULL,
              sync_state INTEGER NOT NULL DEFAULT 0,
              is_deleted INTEGER NOT NULL DEFAULT 0,
              created_at TEXT
            );
            """,
            """
            CREATE TABLE IF NOT EXISTS appointment (
              id TEXT PRIMARY KEY,
              application_id TEXT,
              json_data TEXT NOT NULL,
              sync_state INTEGER NOT NULL DEFAULT 0,
              is_deleted INTEGER NOT NULL DEFAULT 0,
              created_at TEXT
            );
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_appointment_application_id ON appointment(application_id);
            """,
            """
            CREATE INDEX IF NOT EXISTS idx_appointment_scheduled_date ON appointment((json_extract(json_data, '$.scheduled_date')));
            """
        )

        conn?.createStatement().use { stmt ->
            if (stmt != null) {
                for (sql in createSql) {
                    stmt.execute(sql)
                }
            }
        }
    }

    // --- Generic helpers ---
    private fun beginTransaction() {
        conn?.autoCommit = false
    }

    private fun commit() {
        conn?.commit()
        conn?.autoCommit = true
    }

    private fun rollback() {
        try {
            conn?.rollback()
        } catch (_: SQLException) {
        } finally {
            conn?.autoCommit = true
        }
    }

    private fun upsertRow(table: String, id: String, jsonData: String, createdAt: String?, isDeleted: Boolean = false): Boolean {
        val sql = "INSERT INTO $table (id, json_data, sync_state, is_deleted, created_at) VALUES (?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET json_data = excluded.json_data, sync_state = excluded.sync_state, is_deleted = excluded.is_deleted, created_at = COALESCE(excluded.created_at, $table.created_at)"
        conn?.prepareStatement(sql).use { ps ->
            if (ps == null) return false
            ps.setString(1, id)
            ps.setString(2, jsonData)
            ps.setInt(3, SYNC_PENDING)
            ps.setInt(4, if (isDeleted) 1 else 0)
            if (createdAt != null) ps.setString(5, createdAt) else ps.setNull(5, java.sql.Types.VARCHAR)
            ps.executeUpdate()
            return true
        }
    }

    private fun markRowSyncState(table: String, id: String, state: Int) {
        val sql = "UPDATE $table SET sync_state = ? WHERE id = ?"
        conn?.prepareStatement(sql).use { ps ->
            ps?.setInt(1, state)
            ps?.setString(2, id)
            ps?.executeUpdate()
        }
    }

    private fun softDeleteRow(table: String, id: String) {
        val sql = "UPDATE $table SET is_deleted = 1, sync_state = ? WHERE id = ?"
        conn?.prepareStatement(sql).use { ps ->
            ps?.setInt(1, SYNC_PENDING)
            ps?.setString(2, id)
            ps?.executeUpdate()
        }
    }

    private fun queryAllRows(table: String, includeDeleted: Boolean = false): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()
        val sql = if (includeDeleted) "SELECT id, json_data, sync_state, is_deleted, created_at FROM $table" else "SELECT id, json_data, sync_state, is_deleted, created_at FROM $table WHERE is_deleted = 0"
        conn?.prepareStatement(sql).use { ps ->
            ps?.executeQuery().use { rs ->
                while (rs != null && rs.next()) {
                    val id = rs.getString("id")
                    val json = rs.getString("json_data")
                    val syncState = rs.getInt("sync_state")
                    val isDeleted = rs.getInt("is_deleted")
                    val createdAt = rs.getString("created_at")
                    val map = try {
                        JSONObject(json).toMap()
                    } catch (ex: Exception) {
                        mapOf("_raw" to json)
                    }
                    val full = map.toMutableMap()
                    full["id"] = id
                    full["_sync_state"] = syncState
                    full["is_deleted"] = isDeleted == 1
                    full["created_at"] = createdAt
                    rows.add(full)
                }
            }
        }
        return rows
    }

    // --- Bidirectional mapping utilities ---
    private fun modelMapToJson(map: Map<String, Any?>): String {
        return JSONObject(map).toString()
    }

    private fun safeExtractId(map: Map<String, Any?>): String? {
        val id = when {
            map.containsKey("id") -> map["id"]?.toString()
            map.containsKey("_id") -> map["_id"]?.toString()
            else -> null
        }
        return id?.takeIf { it.isNotEmpty() }
    }

    private fun parseExternalOpaque(raw: Any?): List<Map<String, Any?>> {
        // Try a few non-guessing parses. If structure is unclear, return entries
        // with a special marker so caller can decide how to map.
        val results = mutableListOf<Map<String, Any?>>()
        if (raw == null) return results

        when (raw) {
            is org.json.JSONArray -> {
                for (i in 0 until raw.length()) {
                    val item = raw.opt(i)
                    if (item is JSONObject) {
                        results.add(item.toMap())
                    } else {
                        results.add(mapOf("_value" to item, "_external_mapping" to "TODO_MAPPING_REQUIRED"))
                    }
                }
            }
            is String -> {
                // try JSON
                try {
                    val jo = JSONObject(raw)
                    val map = jo.toMap()
                    // If map contains an array root, expose its elements
                    val arrayRootKey = map.keys.firstOrNull { k -> map[k] is List<*> }
                    if (arrayRootKey != null) {
                        val list = map[arrayRootKey] as List<*>
                        for (item in list) {
                            if (item is Map<*, *>) {
                                results.add(item as Map<String, Any?>)
                            } else {
                                results.add(mapOf("_value" to item, "_external_mapping" to "TODO_MAPPING_REQUIRED"))
                            }
                        }
                    } else {
                        results.add(map + mapOf("_external_mapping" to "TODO_MAPPING_REQUIRED"))
                    }
                } catch (ex: Exception) {
                    results.add(mapOf("_value" to raw, "_external_mapping" to "TODO_MAPPING_REQUIRED"))
                }
            }
            is Map<*, *> -> {
                val m = raw as Map<String, Any?>
                // check for common array roots
                val arrayRootKey = m.keys.firstOrNull { k -> m[k] is List<*> }
                if (arrayRootKey != null) {
                    val list = m[arrayRootKey] as List<*>
                    for (item in list) {
                        if (item is Map<*, *>) {
                            results.add(item as Map<String, Any?>)
                        } else {
                            results.add(mapOf("_value" to item, "_external_mapping" to "TODO_MAPPING_REQUIRED"))
                        }
                    }
                } else {
                    results.add(m + mapOf("_external_mapping" to "TODO_MAPPING_REQUIRED"))
                }
            }
            is List<*> -> {
                for (item in raw) {
                    if (item is Map<*, *>) {
                        results.add(item as Map<String, Any?>)
                    } else {
                        results.add(mapOf("_value" to item, "_external_mapping" to "TODO_MAPPING_REQUIRED"))
                    }
                }
            }
            else -> {
                results.add(mapOf("_value" to raw.toString(), "_external_mapping" to "TODO_MAPPING_REQUIRED"))
            }
        }

        return results
    }

    // --- Generic CRUD operations exposed per-model ---
    fun upsertUser(user: UserModel): Boolean {
        val table = "users"
        val map = user.toMap()
        val json = modelMapToJson(map)
        return upsertRow(table, user.id, json, user.createdAt)
    }

    fun upsertProgram(program: ProgramModel): Boolean {
        val table = "programs"
        val map = program.toMap()
        val json = modelMapToJson(map)
        return upsertRow(table, program.id, json, program.createdAt)
    }

    fun upsertRequirement(req: RequirementModel): Boolean {
        val table = "requirements"
        val map = req.toMap()
        val json = modelMapToJson(map)
        return upsertRow(table, req.id, json, req.createdAt)
    }

    fun upsertApplication(app: ApplicationModel): Boolean {
        val table = "applications"
        val map = app.toMap()
        val json = modelMapToJson(map)
        return upsertRow(table, app.id, json, app.createdAt)
    }

    fun upsertChecklistProgress(doc: models.ChecklistProgressModel): Boolean {
        val table = "checklist_progress"
        val map = doc.toMap()
        val json = modelMapToJson(map)
        return upsertRow(table, doc.id, json, doc.createdAt)
    }

    fun upsertAppointment(app: models.AppointmentModel): Boolean {
        val table = "appointment"
        val map = app.toMap()
        val json = modelMapToJson(map)
        return upsertRow(table, app.id, json, app.createdAt)
    }

    fun upsertAnnouncement(a: AnnouncementModel): Boolean {
        val table = "announcements"
        val map = a.toMap()
        val json = modelMapToJson(map)
        return upsertRow(table, a.id, json, a.createdAt)
    }

    fun upsertAppSettings(settings: AppSettingsModel): Boolean {
        val table = "app_settings"
        val map = settings.toMap()
        val json = modelMapToJson(map)
        return upsertRow(table, settings.id, json, null)
    }

    fun getAppSettings(): AppSettingsModel {
        val rows = queryAllRows("app_settings")
        return if (rows.isNotEmpty()) {
            AppSettingsModel.fromMap(rows.first())
        } else {
            val default = AppSettingsModel()
            upsertAppSettings(default)
            default
        }
    }

    fun softDeleteModelById(model: String, id: String) {
        val table = when (model.lowercase()) {
            "user", "users" -> "users"
            "program", "programs" -> "programs"
            "requirement", "requirements" -> "requirements"
            "application", "applications" -> "applications"
            "applicationdocument", "application_documents", "applicationdocumentmodel", "checklistprogress", "checklist_progress", "checklistprogressmodel" -> "checklist_progress"
            "announcement", "announcements" -> "announcements"
            "appointment", "appointments" -> "appointment"
            "app_settings", "appsettings" -> "app_settings"
            else -> throw IllegalArgumentException("Unknown model: $model")
        }
        softDeleteRow(table, id)
    }

    fun queryAll(model: String, includeDeleted: Boolean = false): List<Any> {
        val table = when (model.lowercase()) {
            "user", "users" -> "users"
            "program", "programs" -> "programs"
            "requirement", "requirements" -> "requirements"
            "application", "applications" -> "applications"
            "applicationdocument", "application_documents", "applicationdocumentmodel", "checklistprogress", "checklist_progress", "checklistprogressmodel" -> "checklist_progress"
            "announcement", "announcements" -> "announcements"
            "appointment", "appointments" -> "appointment"
            "app_settings", "appsettings" -> "app_settings"
            else -> throw IllegalArgumentException("Unknown model: $model")
        }
        val rows = queryAllRows(table, includeDeleted)
        return rows.mapNotNull { row ->
            when (table) {
                "users" -> try { UserModel.fromMap(row) } catch (_: Exception) { null }
                "programs" -> try { ProgramModel.fromMap(row) } catch (_: Exception) { null }
                "requirements" -> try { RequirementModel.fromMap(row) } catch (_: Exception) { null }
                "applications" -> try { ApplicationModel.fromMap(row) } catch (_: Exception) { null }
                "checklist_progress" -> try { models.ChecklistProgressModel.fromMap(row) } catch (_: Exception) { null }
                "appointment" -> try { models.AppointmentModel.fromMap(row) } catch (_: Exception) { null }
                "announcements" -> try { AnnouncementModel.fromMap(row) } catch (_: Exception) { null }
                "app_settings" -> try { AppSettingsModel.fromMap(row) } catch (_: Exception) { null }
                else -> null
            }
        }
    }

    // --- Sync operations ---
    fun syncModelFromExternal(model: String, externalRaw: Any?, preserveLocalPending: Boolean = true): SyncResult {
        val table = when (model.lowercase()) {
            "user", "users" -> "users"
            "program", "programs" -> "programs"
            "requirement", "requirements" -> "requirements"
            "application", "applications" -> "applications"
            "applicationdocument", "application_documents", "applicationdocumentmodel", "checklistprogress", "checklist_progress", "checklistprogressmodel" -> "checklist_progress"
            "announcement", "announcements" -> "announcements"
            "appointment", "appointments" -> "appointment"
            "app_settings", "appsettings" -> "app_settings"
            else -> return SyncResult(model, errors = listOf("Unknown model: $model"))
        }

        val entries = parseExternalOpaque(externalRaw)
        if (entries.isEmpty()) return SyncResult(model, skipped = 0, errors = listOf("No parseable entries"))

        var inserted = 0
        var updated = 0
        var skipped = 0
        var conflicts = 0
        val errors = mutableListOf<String>()

        try {
            beginTransaction()
            for (entry in entries) {
                val id = safeExtractId(entry)
                if (id == null) {
                    skipped++
                    continue
                }

                // Load existing row if any
                val existingList = queryAllRows(table, includeDeleted = true).filter { it["id"] == id }
                val existing = existingList.firstOrNull()

                // If existing has pending local changes and preserveLocalPending==true -> skip updating
                val existingSyncState = existing?.get("_sync_state") as? Int ?: SYNC_PENDING
                val existingIsDeleted = existing?.get("is_deleted") as? Boolean ?: false

                 if (existing != null && existingSyncState == SYNC_PENDING && preserveLocalPending) {
                     skipped++
                     continue
                 }

                // Build target payload: we will only set fields present in external entry.
                // Use TODO_MAPPING_REQUIRED flag when mapping is ambiguous.
                val targetMap = mutableMapOf<String, Any?>()
                targetMap.putAll(entry)
                
                val isDeletedExternal = when(val del = entry["is_deleted"]) {
                    is Boolean -> del
                    is Int -> del == 1
                    is String -> del.lowercase() == "true" || del == "1"
                    else -> false
                }
                
                if (!targetMap.containsKey("is_deleted")) targetMap["is_deleted"] = isDeletedExternal
                if (!targetMap.containsKey("created_at")) targetMap["created_at"] = existing?.get("created_at")

                val json = modelMapToJson(targetMap)

                val ok = upsertRow(table, id, json, targetMap["created_at"]?.toString(), isDeletedExternal)
                if (!ok) {
                    errors.add("Failed upsert id=$id")
                } else {
                    if (existing == null) inserted++ else updated++
                    // mark as synced because external is authoritative unless local pending preserved
                    markRowSyncState(table, id, SYNC_SYNCED)
                }
            }
            commit()
        } catch (ex: Exception) {
            rollback()
            errors.add(ex.message ?: "unknown error")
        }

        return SyncResult(model, inserted = inserted, updated = updated, skipped = skipped, conflicts = conflicts, errors = errors)
    }

    // Mark a model row as conflict
    fun markConflict(model: String, id: String) {
        val table = when (model.lowercase()) {
            "user", "users" -> "users"
            "program", "programs" -> "programs"
            "requirement", "requirements" -> "requirements"
            "application", "applications" -> "applications"
            "applicationdocument", "application_documents", "applicationdocumentmodel" -> "application_documents"
            "announcement", "announcements" -> "announcements"
            else -> throw IllegalArgumentException("Unknown model: $model")
        }
        markRowSyncState(table, id, SYNC_CONFLICT)
    }

    // Utility: read single model by id (only non-deleted by default)
    fun getModelById(model: String, id: String, includeDeleted: Boolean = false): Any? {
        val list = queryAll(model, includeDeleted)
        return list.firstOrNull { item ->
            val m = when (item) {
                is UserModel -> item.id == id
                is ProgramModel -> item.id == id
                is RequirementModel -> item.id == id
                is ApplicationModel -> item.id == id
                is models.ChecklistProgressModel -> item.id == id
                is models.AppointmentModel -> item.id == id
                is AnnouncementModel -> item.id == id
                is AppSettingsModel -> item.id == id
                else -> false
            }
            m
        }
    }

}
