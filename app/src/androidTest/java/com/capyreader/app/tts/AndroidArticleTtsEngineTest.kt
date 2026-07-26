package com.capyreader.app.tts

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidArticleTtsEngineTest {
    private lateinit var engine: AndroidArticleTtsEngine

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            engine = AndroidArticleTtsEngine(context)
        }
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            engine.listener = null
            engine.close()
        }
    }

    @Test
    fun initializeReturnsDeterministicCapabilitiesOrTypedUnavailableFailure() = runBlocking {
        val result = initializeEngine()

        result.fold(
            onSuccess = { capabilities ->
                val expectedVoices = capabilities.voices.sortedWith(
                    compareBy(
                        ArticleTtsVoice::languageTag,
                        ArticleTtsVoice::id,
                    )
                )
                assertEquals(expectedVoices, capabilities.voices)
                assertEquals(
                    capabilities.voices.size,
                    capabilities.voices.map(ArticleTtsVoice::id).distinct().size,
                )
                assertTrue(
                    capabilities.voices.all {
                        it.id.isNotBlank() && it.languageTag.isNotBlank()
                    }
                )
            },
            onFailure = { error ->
                assertTrue(error is ArticleTtsUnavailableException)
            },
        )
    }

    @Test
    fun availableEngineStartsUtteranceAndCloseRejectsFurtherSpeech() = runBlocking {
        val initialization = initializeEngine()
        assumeTrue(
            "This device has no usable text-to-speech engine: " +
                initialization.exceptionOrNull()?.message,
            initialization.isSuccess,
        )

        val utteranceID = "instrumented-tts-start"
        val started = CompletableDeferred<String>()
        withContext(Dispatchers.Main) {
            engine.listener = object : ArticleTtsEngine.Listener {
                override fun onStart(utteranceID: String) {
                    started.complete(utteranceID)
                }

                override fun onDone(utteranceID: String) = Unit

                override fun onError(utteranceID: String, message: String?) {
                    started.completeExceptionally(
                        AssertionError(message ?: "Text-to-speech playback failed")
                    )
                }
            }
        }

        val speakResult = withContext(Dispatchers.Main) {
            engine.speak(
                text = "Meerkat Reader text-to-speech platform test.",
                utteranceID = utteranceID,
            )
        }
        assertTrue(speakResult.isSuccess)
        assertEquals(
            utteranceID,
            withTimeout(TTS_TIMEOUT_MILLIS) { started.await() },
        )

        withContext(Dispatchers.Main) {
            engine.close()
        }
        val afterClose = withContext(Dispatchers.Main) {
            engine.speak("This must not play.", "after-close")
        }
        assertTrue(afterClose.exceptionOrNull() is ArticleTtsUnavailableException)
    }

    private suspend fun initializeEngine() = withTimeout(TTS_TIMEOUT_MILLIS) {
        withContext(Dispatchers.Main) {
            engine.initialize(
                ArticleTtsConfiguration(
                    languageTag = "en-US",
                    speechRate = 1.25f,
                )
            )
        }
    }

    private companion object {
        const val TTS_TIMEOUT_MILLIS = 15_000L
    }
}
