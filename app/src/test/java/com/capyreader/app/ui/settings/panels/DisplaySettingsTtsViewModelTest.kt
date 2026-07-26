package com.capyreader.app.ui.settings.panels

import android.app.Application
import androidx.preference.PreferenceManager
import com.capyreader.app.articleimages.ArticleImageCacheCleaner
import com.capyreader.app.articleimages.ArticleImagePreloader
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import com.capyreader.app.tts.ArticleTtsCapabilities
import com.capyreader.app.tts.ArticleTtsConfiguration
import com.capyreader.app.tts.ArticleTtsEngine
import com.capyreader.app.tts.ArticleTtsVoice
import com.jocmp.capy.Account
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(
    application = Application::class,
    qualifiers = "en-rUS",
)
class DisplaySettingsTtsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val ttsEngine = mockk<ArticleTtsEngine>(relaxed = true)
    private lateinit var appPreferences: AppPreferences

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context: Application = RuntimeEnvironment.getApplication()
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        appPreferences = AppPreferences(
            context,
            InMemorySecretStore(),
        ).also(AppPreferences::clearAll)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `capabilities filter choices and updates persist exact values`() = runTest {
        coEvery {
            ttsEngine.initialize(any())
        } returns Result.success(
            ArticleTtsCapabilities(
                voices = voices,
                selectedVoiceID = "en-local",
            )
        )
        val viewModel = buildViewModel()

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("", "zh-CN", "en-US"), viewModel.ttsLanguageTags)
        assertEquals(
            listOf("en-local", "en-network"),
            viewModel.ttsVoices.map(ArticleTtsVoice::id),
        )

        viewModel.updateTtsLanguage("zh-CN")
        viewModel.updateTtsVoice("zh-local")
        viewModel.updateTtsSpeechRate(1.5f)

        assertEquals("zh-CN", viewModel.ttsLanguageTag)
        assertEquals("zh-local", viewModel.ttsVoiceID)
        assertEquals(1.5f, viewModel.ttsSpeechRate)
        assertEquals("zh-CN", appPreferences.readerOptions.ttsLanguageTag.get())
        assertEquals("zh-local", appPreferences.readerOptions.ttsVoiceID.get())
        assertEquals(1.5f, appPreferences.readerOptions.ttsSpeechRate.get())
    }

    @Test
    fun `missing or incompatible voices reset and speech rate is bounded`() = runTest {
        appPreferences.readerOptions.ttsVoiceID.set("removed-voice")
        coEvery {
            ttsEngine.initialize(any())
        } returns Result.success(
            ArticleTtsCapabilities(
                voices = voices,
                selectedVoiceID = "en-local",
            )
        )
        val viewModel = buildViewModel()

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.ttsVoiceID)
        viewModel.updateTtsVoice("en-local")
        viewModel.updateTtsLanguage("zh-CN")
        assertEquals("", viewModel.ttsVoiceID)
        assertEquals("", appPreferences.readerOptions.ttsVoiceID.get())

        viewModel.updateTtsSpeechRate(9f)
        assertEquals(ArticleTtsConfiguration.MAX_SPEECH_RATE, viewModel.ttsSpeechRate)
        assertEquals(
            ArticleTtsConfiguration.MAX_SPEECH_RATE,
            appPreferences.readerOptions.ttsSpeechRate.get(),
        )
    }

    private fun buildViewModel() = DisplaySettingsViewModel(
        account = mockk<Account>(relaxed = true),
        appPreferences = appPreferences,
        articleImagePreloader = mockk<ArticleImagePreloader>(relaxed = true),
        articleImageCacheCleaner = mockk<ArticleImageCacheCleaner>(relaxed = true),
        articleTtsEngine = ttsEngine,
    )

    private val voices = listOf(
        ArticleTtsVoice(
            id = "en-local",
            languageTag = "en-US",
            requiresNetwork = false,
        ),
        ArticleTtsVoice(
            id = "en-network",
            languageTag = "en-US",
            requiresNetwork = true,
        ),
        ArticleTtsVoice(
            id = "zh-local",
            languageTag = "zh-CN",
            requiresNetwork = false,
        ),
    )
}
