package com.capyreader.app.ui.settings.panels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capyreader.app.ai.ArticleAiRepository
import com.capyreader.app.preferences.AiProvider
import com.capyreader.app.preferences.AiTranslationMode
import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.common.launchIO
import com.jocmp.capy.common.withUIContext

class AiSettingsViewModel(
    private val appPreferences: AppPreferences,
    private val articleAiRepository: ArticleAiRepository,
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

    var maxInputCharacters by mutableStateOf(appPreferences.aiOptions.maxInputCharacters.get().toString())
        private set

    var backgroundPreviewsEnabled by mutableStateOf(appPreferences.aiOptions.backgroundPreviewsEnabled.get())
        private set

    var backgroundPreviewsOnWiFiOnly by mutableStateOf(appPreferences.aiOptions.backgroundPreviewsOnWiFiOnly.get())
        private set

    var translationMode by mutableStateOf(appPreferences.aiOptions.translationMode.get())
        private set

    var translatePrompt by mutableStateOf(appPreferences.aiOptions.translatePrompt.get())
        private set

    var summarizePrompt by mutableStateOf(appPreferences.aiOptions.summarizePrompt.get())
        private set

    var previewSummaryPrompt by mutableStateOf(appPreferences.aiOptions.previewSummaryPrompt.get())
        private set

    var keyPointsPrompt by mutableStateOf(appPreferences.aiOptions.keyPointsPrompt.get())
        private set

    var isClearingAiCache by mutableStateOf(false)
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

    fun updateMaxInputCharacters(value: String) {
        val sanitized = value.filter(Char::isDigit).take(6)
        maxInputCharacters = sanitized

        val parsed = sanitized.toIntOrNull() ?: return
        if (parsed > 0) {
            appPreferences.aiOptions.maxInputCharacters.set(parsed)
        }
    }

    fun updateBackgroundPreviewsEnabled(enabled: Boolean) {
        appPreferences.aiOptions.backgroundPreviewsEnabled.set(enabled)
        backgroundPreviewsEnabled = enabled
    }

    fun updateBackgroundPreviewsOnWiFiOnly(enabled: Boolean) {
        appPreferences.aiOptions.backgroundPreviewsOnWiFiOnly.set(enabled)
        backgroundPreviewsOnWiFiOnly = enabled
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

    fun updatePreviewSummaryPrompt(prompt: String) {
        appPreferences.aiOptions.previewSummaryPrompt.set(prompt)
        previewSummaryPrompt = prompt
    }

    fun updateKeyPointsPrompt(prompt: String) {
        appPreferences.aiOptions.keyPointsPrompt.set(prompt)
        keyPointsPrompt = prompt
    }

    fun clearAiCache() {
        if (isClearingAiCache) {
            return
        }

        isClearingAiCache = true
        viewModelScope.launchIO {
            articleAiRepository.clearCache()
            withUIContext {
                isClearingAiCache = false
            }
        }
    }
}
