package com.symphonix.enrollmate

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import models.AnnouncementModel
import models.AppSettingsModel
import services.DatabaseService
import services.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 1. Upgrade to AndroidViewModel to access native device storage
class AppViewModel(application: Application) : AndroidViewModel(application) {

    // 2. Initialize Android's bulletproof SharedPreferences
    private val prefs = application.getSharedPreferences("enrollmate_prefs", Context.MODE_PRIVATE)

    // 3. Immediately read the permanently saved state the millisecond the app opens
    private val _settings = MutableStateFlow(
        AppSettingsModel(
            isUserLoggedIn = prefs.getBoolean("is_logged_in", false),
            currentUserId = prefs.getString("user_id", null),
            currentUserFullName = prefs.getString("user_name", null),
            currentUserEmail = prefs.getString("user_email", null)
        )
    )
    val settings: StateFlow<AppSettingsModel> = _settings.asStateFlow()

    private val _announcements = MutableStateFlow<List<AnnouncementModel>>(emptyList())
    val announcements: StateFlow<List<AnnouncementModel>> = _announcements.asStateFlow()

    private val _isLoadingAnnouncements = MutableStateFlow(false)
    val isLoadingAnnouncements: StateFlow<Boolean> = _isLoadingAnnouncements.asStateFlow()

    init {
        refreshAnnouncements()
        refreshUserProfile()
    }

    private fun refreshUserProfile() {
        val userId = settings.value.currentUserId
        if (userId != null) {
            viewModelScope.launch {
                try {
                    val userProfile = withContext(Dispatchers.IO) {
                        supabase.postgrest["user"]
                            .select { filter { eq("id", userId) } }
                            .decodeSingleOrNull<models.UserModel>()
                    }
                    if (userProfile != null) {
                        updateSettings(settings.value.copy(currentUserFullName = userProfile.fullName))
                    }
                } catch (e: Exception) {
                    // ignore network errors
                }
            }
        }
    }

    fun refreshAnnouncements() {
        viewModelScope.launch {
            _isLoadingAnnouncements.value = true
            try {
                // Fetch strictly from the Supabase "announcement" table
                val remoteData = withContext(Dispatchers.IO) {
                    supabase.postgrest["announcement"]
                        .select { filter { eq("is_deleted", false) } }
                        .decodeList<AnnouncementModel>()
                }

                if (remoteData.isNotEmpty()) {
                    // Push directly to memory so it shows on the Dashboard immediately
                    _announcements.value = remoteData.sortedWith(
                        compareByDescending<AnnouncementModel> { it.isPinned }
                            .thenByDescending { it.createdAt }
                    )
                } else {
                    _announcements.value = emptyList()
                }
            } catch (e: Exception) {
                android.util.Log.e("AppViewModel", "Failed to fetch announcements: ${e.message}")
            } finally {
                _isLoadingAnnouncements.value = false
            }
        }
    }

    fun updateSettings(newSettings: AppSettingsModel) {
        viewModelScope.launch {
            // 4. Save the login state natively to the Android device whenever it changes
            prefs.edit().apply {
                putBoolean("is_logged_in", newSettings.isUserLoggedIn)
                putString("user_id", newSettings.currentUserId)
                putString("user_name", newSettings.currentUserFullName)
                putString("user_email", newSettings.currentUserEmail)
                apply()
            }
            _settings.value = newSettings
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                // Destroy the secure Supabase token
                supabase.auth.signOut()
            } catch (e: Exception) {
                // Ignore network errors on sign out
            }

            // Wipe the user data and update the settings (which clears SharedPreferences)
            val newSettings = settings.value.copy(
                isUserLoggedIn = false,
                currentUserId = null,
                currentUserFullName = null,
                currentUserEmail = null
            )
            updateSettings(newSettings)
        }
    }
}