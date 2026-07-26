package com.capyreader.app.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

class AndroidArticleTtsEngine(
    context: Context,
) : ArticleTtsEngine {
    private val appContext = context.applicationContext
    private val initializationMutex = Mutex()
    private var textToSpeech: TextToSpeech? = null
    private var initialized = false

    override var listener: ArticleTtsEngine.Listener? = null

    override suspend fun initialize(
        configuration: ArticleTtsConfiguration,
    ): Result<ArticleTtsCapabilities> {
        return initializationMutex.withLock {
            if (!initialized) {
                initializeEngine().getOrElse { return@withLock Result.failure(it) }
            }
            configure(configuration)
        }
    }

    override fun speak(text: String, utteranceID: String): Result<Unit> {
        val engine = textToSpeech
            ?: return Result.failure(ArticleTtsUnavailableException("Text-to-speech is unavailable"))
        val result = engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceID,
        )

        return if (result == TextToSpeech.SUCCESS) {
            Result.success(Unit)
        } else {
            Result.failure(ArticleTtsUnavailableException("Text-to-speech could not start"))
        }
    }

    override fun stop() {
        textToSpeech?.stop()
    }

    override fun close() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        initialized = false
    }

    private suspend fun initializeEngine(): Result<Unit> = suspendCancellableCoroutine { continuation ->
        textToSpeech = TextToSpeech(appContext) { status ->
            if (!continuation.isActive) {
                return@TextToSpeech
            }

            val engine = textToSpeech
            if (status == TextToSpeech.SUCCESS && engine != null) {
                initialized = true
                engine.setOnUtteranceProgressListener(progressListener)
                continuation.resume(Result.success(Unit))
            } else {
                close()
                continuation.resume(
                    Result.failure(
                        ArticleTtsUnavailableException("Text-to-speech engine initialization failed")
                    )
                )
            }
        }
        continuation.invokeOnCancellation { close() }
    }

    private fun configure(
        configuration: ArticleTtsConfiguration,
    ): Result<ArticleTtsCapabilities> {
        val engine = textToSpeech
            ?: return Result.failure(ArticleTtsUnavailableException("Text-to-speech is unavailable"))
        val platformVoices = engine.voices.orEmpty()
            .sortedWith(compareBy({ it.locale.toLanguageTag() }, { it.name }))
        val voices = platformVoices.map {
            ArticleTtsVoice(
                id = it.name,
                languageTag = it.locale.toLanguageTag(),
                requiresNetwork = it.isNetworkConnectionRequired,
            )
        }
        val locale = configuration.languageTag
            .takeIf(String::isNotBlank)
            ?.let(Locale::forLanguageTag)
            ?: Locale.getDefault()
        val selectedVoice = ArticleTtsVoiceSelector.select(
            voices = voices,
            configuration = configuration,
            currentVoiceID = engine.voice?.name,
            deviceLanguageTag = Locale.getDefault().toLanguageTag(),
        )
        val languageResult = selectedVoice
            ?.let { voice -> platformVoices.find { it.name == voice.id } }
            ?.let(engine::setVoice)
            ?: engine.setLanguage(locale)

        return if (
            languageResult == TextToSpeech.LANG_MISSING_DATA ||
            languageResult == TextToSpeech.LANG_NOT_SUPPORTED ||
            languageResult == TextToSpeech.ERROR
        ) {
            Result.failure(
                ArticleTtsUnavailableException(
                    "Text-to-speech does not support ${locale.toLanguageTag()}"
                )
            )
        } else if (engine.setSpeechRate(configuration.normalizedSpeechRate) != TextToSpeech.SUCCESS) {
            Result.failure(ArticleTtsUnavailableException("Text-to-speech rate is unavailable"))
        } else {
            Result.success(
                ArticleTtsCapabilities(
                    voices = voices,
                    selectedVoiceID = engine.voice?.name,
                )
            )
        }
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String) {
            listener?.onStart(utteranceId)
        }

        override fun onDone(utteranceId: String) {
            listener?.onDone(utteranceId)
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String) {
            listener?.onError(utteranceId, null)
        }

        override fun onError(utteranceId: String, errorCode: Int) {
            listener?.onError(utteranceId, "Text-to-speech error $errorCode")
        }
    }
}
