package com.capyreader.app.tts

interface ArticleTtsEngine : AutoCloseable {
    var listener: Listener?

    suspend fun initialize(
        configuration: ArticleTtsConfiguration = ArticleTtsConfiguration(),
    ): Result<ArticleTtsCapabilities>

    fun speak(text: String, utteranceID: String): Result<Unit>

    fun stop()

    interface Listener {
        fun onStart(utteranceID: String)

        fun onDone(utteranceID: String)

        fun onError(utteranceID: String, message: String?)
    }
}

data class ArticleTtsConfiguration(
    val languageTag: String = "",
    val voiceID: String = "",
    val speechRate: Float = DEFAULT_SPEECH_RATE,
) {
    val normalizedSpeechRate: Float
        get() = speechRate.coerceIn(MIN_SPEECH_RATE, MAX_SPEECH_RATE)

    companion object {
        const val MIN_SPEECH_RATE = 0.5f
        const val DEFAULT_SPEECH_RATE = 1.0f
        const val MAX_SPEECH_RATE = 2.0f
    }
}

data class ArticleTtsCapabilities(
    val voices: List<ArticleTtsVoice>,
    val selectedVoiceID: String?,
)

data class ArticleTtsVoice(
    val id: String,
    val languageTag: String,
    val requiresNetwork: Boolean,
)

object ArticleTtsVoiceSelector {
    fun select(
        voices: List<ArticleTtsVoice>,
        configuration: ArticleTtsConfiguration,
        currentVoiceID: String? = null,
        deviceLanguageTag: String,
    ): ArticleTtsVoice? {
        voices.find { it.id == configuration.voiceID }
            ?.let { return it }

        val languageTag = configuration.languageTag.ifBlank { deviceLanguageTag }
        val language = languageTag.substringBefore('-')
        val candidates = voices.filter {
            it.languageTag == languageTag ||
                it.languageTag.substringBefore('-') == language
        }

        return candidates.find { it.id == currentVoiceID && !it.requiresNetwork }
            ?: candidates.find { !it.requiresNetwork }
            ?: candidates.find { it.id == currentVoiceID }
            ?: candidates.firstOrNull()
    }
}

class ArticleTtsUnavailableException(message: String) : IllegalStateException(message)
