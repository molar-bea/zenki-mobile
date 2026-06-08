package models

import org.json.JSONObject

data class AppSettingsModel(
    val id: String = "default",
    val useLargeTexts: Boolean = false,
    val keepScreenOn: Boolean = false,
    val isUserLoggedIn: Boolean = false,
    val currentUserId: String? = null,
    val currentUserFullName: String? = null,
    val currentUserEmail: String? = null,
) {
    fun toMap() = mapOf(
        "id" to id,
        "use_large_texts" to useLargeTexts,
        "keep_screen_on" to keepScreenOn,
        "is_user_logged_in" to isUserLoggedIn,
        "current_user_id" to currentUserId,
        "current_user_full_name" to currentUserFullName
    )

    companion object {
        fun fromMap(map: Map<String, Any?>) = AppSettingsModel(
            id = map["id"]?.toString() ?: "default",
            useLargeTexts = map["use_large_texts"]?.toString()?.toBoolean() ?: false,
            keepScreenOn = map["keep_screen_on"]?.toString()?.toBoolean() ?: false,
            isUserLoggedIn = map["is_user_logged_in"]?.toString()?.toBoolean() ?: false,
            currentUserId = map["current_user_id"]?.toString(),
            currentUserFullName = map["current_user_full_name"]?.toString()
        )
    }
}