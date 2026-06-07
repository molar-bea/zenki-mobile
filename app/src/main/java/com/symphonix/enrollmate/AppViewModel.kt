package com.symphonix.enrollmate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import models.AppSettingsModel
import services.DatabaseService

class AppViewModel : ViewModel() {
    private val _settings = MutableStateFlow(DatabaseService.getAppSettings())
    val settings: StateFlow<AppSettingsModel> = _settings.asStateFlow()

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
