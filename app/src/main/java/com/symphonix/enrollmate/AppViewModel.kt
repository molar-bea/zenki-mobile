package com.symphonix.enrollmate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import models.AnnouncementModel
import models.AppSettingsModel
import services.ApiService
import services.DatabaseService
import services.supabase
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppViewModel : ViewModel() {
    private val _settings = MutableStateFlow(DatabaseService.getAppSettings())
    val settings: StateFlow<AppSettingsModel> = _settings.asStateFlow()

    private val _announcements = MutableStateFlow<List<AnnouncementModel>>(emptyList())
    val announcements: StateFlow<List<AnnouncementModel>> = _announcements.asStateFlow()

    private val _isLoadingAnnouncements = MutableStateFlow(false)
    val isLoadingAnnouncements: StateFlow<Boolean> = _isLoadingAnnouncements.asStateFlow()

    init {
        loadLocalAnnouncements()
        refreshUserProfile()
    }

    private fun refreshUserProfile() {
        val userId = settings.value.currentUserId
        if (userId != null) {
            viewModelScope.launch {
                try {
                    val userProfile = withContext(Dispatchers.IO) {
                        supabase.postgrest.from("user")
                            .select { filter { eq("id", userId) } }
                            .decodeSingleOrNull<models.UserModel>()
                    }
                    if (userProfile != null) {
                        updateSettings(settings.value.copy(currentUserFullName = userProfile.fullName))
                        DatabaseService.upsertUser(userProfile)
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    private fun loadLocalAnnouncements() {
        val list = DatabaseService.queryAll("announcement").filterIsInstance<AnnouncementModel>()
        _announcements.value = list.sortedByDescending { it.createdAt }
    }

    fun refreshAnnouncements() {
        viewModelScope.launch {
            _isLoadingAnnouncements.value = true
            try {
                // 1. Try fetching directly from Supabase
                val remoteData = withContext(Dispatchers.IO) {
                    supabase.postgrest.from("announcement")
                        .select()
                        .decodeList<AnnouncementModel>()
                }
                
                if (remoteData.isNotEmpty()) {
                    // Convert models to maps for the sync service
                    val maps = remoteData.map { it.toMap() }
                    DatabaseService.syncModelFromExternal("announcement", maps)
                } else {
                    // 2. Fallback to ApiService
                    val response = ApiService.getAnnouncements()
                    if (response.optInt("success") == 1) {
                        val data = response.opt("data")
                        DatabaseService.syncModelFromExternal("announcement", data)
                    }
                }
                loadLocalAnnouncements()
            } catch (e: Exception) {
                // 3. Last fallback
                try {
                    val response = ApiService.getAnnouncements()
                    if (response.optInt("success") == 1) {
                        val data = response.opt("data")
                        DatabaseService.syncModelFromExternal("announcement", data)
                        loadLocalAnnouncements()
                    }
                } catch (ex: Exception) {
                    android.util.Log.e("AppViewModel", "Sync failed: ${ex.message}")
                }
            } finally {
                _isLoadingAnnouncements.value = false
            }
        }
    }

    fun updateSettings(newSettings: AppSettingsModel) {
        viewModelScope.launch {
            DatabaseService.upsertAppSettings(newSettings)
            _settings.value = newSettings
        }
    }

    fun logout() {
        val newSettings = settings.value.copy(
            isUserLoggedIn = false,
            currentUserId = null,
            currentUserFullName = null
        )
        updateSettings(newSettings)
    }
}
