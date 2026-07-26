package com.capyreader.app.ui.settings.panels

import android.content.Context
import androidx.preference.PreferenceManager
import com.capyreader.app.ai.ArticleAiRuleDailyBudget
import com.capyreader.app.ai.ArticleAiRuleRunAvailability
import com.capyreader.app.ai.ArticleAiRepository
import com.capyreader.app.ai.articleAiRuleRunAvailability
import com.capyreader.app.preferences.AiProvider
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.InMemorySecretStore
import com.jocmp.capy.ArticleAutomationRule
import com.jocmp.capy.ArticleRuleAction
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AiBackgroundPreviewSettingsTest {
    private val context: Context
        get() = RuntimeEnvironment.getApplication()
    private lateinit var preferences: AppPreferences
    private lateinit var viewModel: AiSettingsViewModel

    @Before
    fun setUp() {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .clear()
            .commit()
        preferences = AppPreferences(context, InMemorySecretStore())
        preferences.clearAll()
        viewModel = AiSettingsViewModel(
            appPreferences = preferences,
            articleAiRepository = mockk<ArticleAiRepository>(relaxed = true),
        )
    }

    @Test
    fun `background budget settings have conservative defaults`() {
        assertFalse(viewModel.backgroundPreviewsRequireCharging)
        assertEquals(20, viewModel.backgroundPreviewsDailyLimit)
    }

    @Test
    fun `background budget settings update and persist`() {
        viewModel.updateBackgroundPreviewsRequireCharging(true)
        viewModel.updateBackgroundPreviewsDailyLimit(50)

        assertTrue(viewModel.backgroundPreviewsRequireCharging)
        assertEquals(50, viewModel.backgroundPreviewsDailyLimit)

        val restored = AppPreferences(context, InMemorySecretStore())
        assertTrue(restored.aiOptions.backgroundPreviewsRequireCharging.get())
        assertEquals(50, restored.aiOptions.backgroundPreviewsDailyLimit.get())
    }

    @Test
    fun `AI settings drive rule readiness through all provider checks`() {
        val rules = listOf(
            ArticleAutomationRule(
                enabled = true,
                actions = setOf(ArticleRuleAction.STAR),
                aiEnabled = true,
                aiCriterion = "Important releases",
            )
        )

        assertEquals(
            ArticleAiRuleRunAvailability.AI_DISABLED,
            articleAiRuleRunAvailability(preferences.aiOptions, rules),
        )

        viewModel.updateEnabled(true)
        assertEquals(
            ArticleAiRuleRunAvailability.API_KEY_REQUIRED,
            articleAiRuleRunAvailability(preferences.aiOptions, rules),
        )

        viewModel.updateApiKey("fixture-key")
        assertEquals(
            ArticleAiRuleRunAvailability.READY,
            articleAiRuleRunAvailability(preferences.aiOptions, rules),
        )

        viewModel.updateModel("")
        assertEquals(
            ArticleAiRuleRunAvailability.MODEL_REQUIRED,
            articleAiRuleRunAvailability(preferences.aiOptions, rules),
        )

        viewModel.updateProvider(AiProvider.DEEPSEEK)
        assertEquals(AiProvider.DEEPSEEK.defaultBaseUrl, viewModel.baseUrl)
        assertEquals(AiProvider.DEEPSEEK.defaultModel, viewModel.model)
        assertEquals(
            ArticleAiRuleRunAvailability.READY,
            articleAiRuleRunAvailability(preferences.aiOptions, rules),
        )

        preferences.aiOptions.ruleEvaluationsDailyLimit.set(1)
        assertTrue(
            ArticleAiRuleDailyBudget(preferences.aiOptions).recordAttempt()
        )
        assertEquals(
            ArticleAiRuleRunAvailability.DAILY_LIMIT_REACHED,
            articleAiRuleRunAvailability(preferences.aiOptions, rules),
        )
    }
}
