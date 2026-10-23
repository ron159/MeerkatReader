package com.capyreader.app.tts

import com.jocmp.capy.Article
import com.jocmp.capy.persistence.ArticleTtsProgressRecords
import com.jocmp.capy.persistence.ArticleTtsSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ArticleTtsController(
    private val engine: ArticleTtsEngine,
    private val progressRecords: ArticleTtsProgressRecords,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private var prepareJob: Job? = null
    private var sentences: List<String> = emptyList()
    private var currentUtteranceID: String? = null
    private var currentContentKey: String? = null
    private var generation = 0L
    private var playbackGeneration = 0L

    private val _state = MutableStateFlow(ArticleTtsState())
    val state: StateFlow<ArticleTtsState> = _state.asStateFlow()

    init {
        engine.listener = object : ArticleTtsEngine.Listener {
            override fun onStart(utteranceID: String) {
                scope.launch {
                    if (utteranceID == currentUtteranceID) {
                        _state.value = _state.value.copy(status = ArticleTtsStatus.PLAYING)
                    }
                }
            }

            override fun onDone(utteranceID: String) {
                scope.launch {
                    if (utteranceID == currentUtteranceID) {
                        advanceAfterCompletion(playbackGeneration)
                    }
                }
            }

            override fun onError(utteranceID: String, message: String?) {
                scope.launch {
                    if (utteranceID == currentUtteranceID) {
                        _state.value = _state.value.copy(
                            status = ArticleTtsStatus.ERROR,
                            errorMessage = message ?: "Text-to-speech playback failed",
                        )
                    }
                }
            }
        }
    }

    fun play(
        article: Article,
        locale: Locale = Locale.getDefault(),
    ) = play(
        content = ArticleTtsContent.original(article),
        configuration = ArticleTtsConfiguration(languageTag = locale.toLanguageTag()),
    )

    fun play(
        content: ArticleTtsContent,
        configuration: ArticleTtsConfiguration = ArticleTtsConfiguration(),
    ) {
        val current = _state.value
        val isCurrentContent = current.articleID == content.articleID &&
            current.source == content.source &&
            currentContentKey == content.contentKey
        if (isCurrentContent && current.status == ArticleTtsStatus.PAUSED) {
            resume()
            return
        }
        if (isCurrentContent && current.status == ArticleTtsStatus.PLAYING) {
            return
        }
        if (isCurrentContent && current.status == ArticleTtsStatus.INITIALIZING) {
            return
        }

        prepareJob?.cancel()
        val playback = ++playbackGeneration
        currentUtteranceID = null
        engine.stop()
        currentContentKey = content.contentKey
        sentences = emptyList()
        prepareJob = scope.launch {
            if (playback != playbackGeneration) return@launch
            _state.value = ArticleTtsState(
                articleID = content.articleID,
                articleTitle = content.articleTitle,
                source = content.source,
                status = ArticleTtsStatus.INITIALIZING,
            )

            try {
                val locale = configuration.languageTag
                    .takeIf(String::isNotBlank)
                    ?.let(Locale::forLanguageTag)
                    ?: Locale.getDefault()
                val preparedSentences = withContext(ioDispatcher) {
                    ArticleTtsText.sentences(content.text, locale)
                }
                if (playback != playbackGeneration) return@launch
                if (preparedSentences.isEmpty()) {
                    _state.value = _state.value.copy(
                        status = ArticleTtsStatus.ERROR,
                        errorMessage = "Article text is empty",
                    )
                    return@launch
                }

                engine.initialize(configuration).getOrElse { error ->
                    if (playback == playbackGeneration) {
                        _state.value = _state.value.copy(
                            status = ArticleTtsStatus.UNAVAILABLE,
                            errorMessage = error.message,
                        )
                    }
                    return@launch
                }
                if (playback != playbackGeneration) return@launch

                sentences = preparedSentences
                val saved = withContext(ioDispatcher) {
                    progressRecords.find(content.articleID, content.source)
                }
                if (playback != playbackGeneration) return@launch
                val savedIndex = saved
                    ?.takeIf { it.contentKey == content.contentKey }
                    ?.sentenceIndex
                    ?: 0
                val startIndex = if (
                    isCurrentContent &&
                    current.status == ArticleTtsStatus.COMPLETED
                ) {
                    0
                } else {
                    savedIndex.coerceIn(0, sentences.lastIndex)
                }
                _state.value = _state.value.copy(
                    sentenceIndex = startIndex,
                    sentenceCount = sentences.size,
                    errorMessage = null,
                )
                speakCurrent(playback)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (playback == playbackGeneration) {
                    _state.value = _state.value.copy(
                        status = ArticleTtsStatus.ERROR,
                        errorMessage = e.message ?: "Text-to-speech playback failed",
                    )
                }
            }
        }
    }

    fun pause() {
        val state = _state.value
        if (state.status != ArticleTtsStatus.PLAYING &&
            !(state.status == ArticleTtsStatus.INITIALIZING && state.sentenceCount > 0)
        ) {
            return
        }
        playbackGeneration += 1
        currentUtteranceID = null
        engine.stop()
        _state.value = _state.value.copy(status = ArticleTtsStatus.PAUSED)
    }

    fun resume() {
        if (_state.value.status != ArticleTtsStatus.PAUSED) {
            return
        }
        val playback = ++playbackGeneration
        scope.launch { speakCurrent(playback) }
    }

    fun skipNext() {
        moveTo(_state.value.sentenceIndex + 1)
    }

    fun skipPrevious() {
        moveTo(_state.value.sentenceIndex - 1)
    }

    fun dismiss() {
        prepareJob?.cancel()
        playbackGeneration += 1
        currentUtteranceID = null
        engine.stop()
        currentContentKey = null
        sentences = emptyList()
        _state.value = ArticleTtsState()
    }

    override fun close() {
        dismiss()
        engine.listener = null
        engine.close()
        scope.cancel()
    }

    private fun moveTo(index: Int) {
        if (sentences.isEmpty()) {
            return
        }

        val wasPlaying = _state.value.status == ArticleTtsStatus.PLAYING
        val target = index.coerceIn(0, sentences.lastIndex)
        val playback = ++playbackGeneration
        currentUtteranceID = null
        engine.stop()
        _state.value = _state.value.copy(
            sentenceIndex = target,
            status = if (wasPlaying) ArticleTtsStatus.INITIALIZING else ArticleTtsStatus.PAUSED,
            errorMessage = null,
        )
        scope.launch {
            if (playback != playbackGeneration) return@launch
            persistProgress(target)
            if (wasPlaying && playback == playbackGeneration) {
                speakCurrent(playback)
            }
        }
    }

    private suspend fun speakCurrent(playback: Long) {
        if (playback != playbackGeneration) return
        val state = _state.value
        val sentence = sentences.getOrNull(state.sentenceIndex) ?: return
        generation += 1
        val utteranceID = "${state.articleID}:${state.sentenceIndex}:$generation"
        currentUtteranceID = utteranceID
        persistProgress(state.sentenceIndex)
        if (playback != playbackGeneration || currentUtteranceID != utteranceID) return
        engine.speak(sentence, utteranceID).fold(
            onSuccess = {
                _state.value = _state.value.copy(
                    status = ArticleTtsStatus.PLAYING,
                    errorMessage = null,
                )
            },
            onFailure = { error ->
                currentUtteranceID = null
                _state.value = _state.value.copy(
                    status = ArticleTtsStatus.ERROR,
                    errorMessage = error.message ?: "Text-to-speech playback failed",
                )
            },
        )
    }

    private suspend fun advanceAfterCompletion(playback: Long) {
        val nextIndex = _state.value.sentenceIndex + 1
        if (nextIndex > sentences.lastIndex) {
            currentUtteranceID = null
            _state.value = _state.value.copy(status = ArticleTtsStatus.COMPLETED)
            persistProgress(0)
            return
        }

        _state.value = _state.value.copy(sentenceIndex = nextIndex)
        speakCurrent(playback)
    }

    private suspend fun persistProgress(sentenceIndex: Int) {
        val state = _state.value
        val articleID = state.articleID ?: return
        val contentKey = currentContentKey ?: return
        withContext(ioDispatcher) {
            progressRecords.upsert(
                articleID = articleID,
                source = state.source,
                contentKey = contentKey,
                sentenceIndex = sentenceIndex,
            )
        }
    }
}

enum class ArticleTtsStatus {
    IDLE,
    INITIALIZING,
    PLAYING,
    PAUSED,
    COMPLETED,
    UNAVAILABLE,
    ERROR,
}

data class ArticleTtsState(
    val articleID: String? = null,
    val articleTitle: String = "",
    val source: ArticleTtsSource = ArticleTtsSource.ORIGINAL,
    val status: ArticleTtsStatus = ArticleTtsStatus.IDLE,
    val sentenceIndex: Int = 0,
    val sentenceCount: Int = 0,
    val errorMessage: String? = null,
) {
    val isVisible: Boolean
        get() = status != ArticleTtsStatus.IDLE

    val isPlaying: Boolean
        get() = status == ArticleTtsStatus.PLAYING
}
