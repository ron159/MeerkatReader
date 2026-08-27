package com.capyreader.app.ui.settings.panels

import android.app.Application
import androidx.preference.PreferenceManager
import com.capyreader.app.articleimages.ArticleImageCacheCleaner
import com.capyreader.app.articleimages.ArticleImagePreloader
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import com.capyreader.app.tts.ArticleTtsCapabilities
import com.capyreader.app.tts.ArticleTtsEngine
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DisplaySettingsArticleListImagePositionTest {
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
        coEvery { ttsEngine.initialize(any()) } returns Result.success(
            ArticleTtsCapabilities(voices = emptyList(), selectedVoiceID = null)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `list images stay on the right by default and left preference persists`() = runTest {
        assertFalse(appPreferences.articleListOptions.imagePreviewOnLeft.get())
        val viewModel = buildViewModel()

        viewModel.updateImagePreviewOnLeft(true)

        assertTrue(viewModel.imagePreviewOnLeft)
        assertTrue(appPreferences.articleListOptions.imagePreviewOnLeft.get())
    }

    private fun buildViewModel() = DisplaySettingsViewModel(
        account = mockk<Account>(relaxed = true),
        appPreferences = appPreferences,
        articleImagePreloader = mockk<ArticleImagePreloader>(relaxed = true),
        articleImageCacheCleaner = mockk<ArticleImageCacheCleaner>(relaxed = true),
        articleTtsEngine = ttsEngine,
    )
}
