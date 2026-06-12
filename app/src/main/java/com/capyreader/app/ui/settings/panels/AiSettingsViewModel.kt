package com.capyreader.app.ui.settings.panels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.capyreader.app.preferences.AiProvider
import com.capyreader.app.preferences.AiTranslationMode
import com.capyreader.app.preferences.AppPreferences

class AiSettingsViewModel(
    private val appPreferences: AppPreferences,
) : ViewModel() {
    var enabled by mutableStateOf(appPreferences.aiOptions.enabled.get())
        private set

    var provider by mutableStateOf(appPreferences.aiOptions.provider.get())
        private set

    var baseUrl by mutableStateOf(appPreferences.aiOptions.baseUrl.get())
        private set

    var apiKey by mutableStateOf(appPreferences.aiOptions.apiKey.get())
        private set

    var model by mutableStateOf(appPreferences.aiOptions.model.get())
        private set

    var language by mutableStateOf(appPreferences.aiOptions.language.get())
        private set

    var translationMode by mutableStateOf(appPreferences.aiOptions.translationMode.get())
        private set

    var translatePrompt by mutableStateOf(appPreferences.aiOptions.translatePrompt.get())
        private set

    var summarizePrompt by mutableStateOf(appPreferences.aiOptions.summarizePrompt.get())
        private set

    var keyPointsPrompt by mutableStateOf(appPreferences.aiOptions.keyPointsPrompt.get())
        private set

    fun updateEnabled(enabled: Boolean) {
        appPreferences.aiOptions.enabled.set(enabled)
        this.enabled = enabled
    }

    fun updateProvider(provider: AiProvider) {
        val previousProvider = this.provider

        appPreferences.aiOptions.provider.set(provider)
        this.provider = provider

        if (baseUrl.isBlank() || baseUrl == previousProvider.defaultBaseUrl) {
            updateBaseUrl(provider.defaultBaseUrl)
        }

        if (model.isBlank() || model == previousProvider.defaultModel) {
            updateModel(provider.defaultModel)
        }
    }

    fun updateBaseUrl(baseUrl: String) {
        appPreferences.aiOptions.baseUrl.set(baseUrl)
        this.baseUrl = baseUrl
    }

    fun updateApiKey(apiKey: String) {
        appPreferences.aiOptions.apiKey.set(apiKey)
        this.apiKey = apiKey
    }

    fun updateModel(model: String) {
        appPreferences.aiOptions.model.set(model)
        this.model = model
    }

    fun updateLanguage(language: String) {
        appPreferences.aiOptions.language.set(language)
        this.language = language
    }

    fun updateTranslationMode(mode: AiTranslationMode) {
        appPreferences.aiOptions.translationMode.set(mode)
        translationMode = mode
    }

    fun updateTranslatePrompt(prompt: String) {
        appPreferences.aiOptions.translatePrompt.set(prompt)
        translatePrompt = prompt
    }

    fun updateSummarizePrompt(prompt: String) {
        appPreferences.aiOptions.summarizePrompt.set(prompt)
        summarizePrompt = prompt
    }

    fun updateKeyPointsPrompt(prompt: String) {
        appPreferences.aiOptions.keyPointsPrompt.set(prompt)
        keyPointsPrompt = prompt
    }
}
