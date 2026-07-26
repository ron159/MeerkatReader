package com.capyreader.app.ui.settings.panels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import com.capyreader.app.preferences.AiProvider
import com.capyreader.app.preferences.AiTranslationMode
import com.capyreader.app.ui.theme.CapyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class AiSettingsPanelViewTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `global opt in fields password semantics and cache action are wired`() {
        var enabled = false
        var baseUrl = ""
        var apiKey = ""
        var model = ""
        var clearCount = 0
        setPanel(
            updateEnabled = { enabled = it },
            updateBaseUrl = { baseUrl = it },
            updateApiKey = { apiKey = it },
            updateModel = { model = it },
            clearAiCache = { clearCount += 1 },
        )

        composeRule
            .onNodeWithText("Enable AI actions")
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithText(
                "Article content is sent to your configured third-party AI provider."
            )
            .assertExists()

        textField("API base URL")
            .performScrollTo()
            .performTextReplacement("https://ai.example/v1")
        val apiKeyField = textField("API key")
        apiKeyField.assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
        )
        apiKeyField
            .performScrollTo()
            .performTextReplacement("fixture-key")
        textField("Model")
            .performScrollTo()
            .performTextReplacement("fixture-model")
        composeRule
            .onNodeWithText("Clear AI cache")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(enabled)
            assertEquals("https://ai.example/v1", baseUrl)
            assertEquals("fixture-key", apiKey)
            assertEquals("fixture-model", model)
            assertEquals(1, clearCount)
        }
    }

    @Test
    fun `provider background constraints daily limit and translation callbacks are wired`() {
        var provider: AiProvider? = null
        var previewsEnabled = false
        var wifiOnly = false
        var chargingOnly = false
        var dailyLimit = 0
        var translationMode: AiTranslationMode? = null
        setPanel(
            updateProvider = { provider = it },
            updateBackgroundPreviewsEnabled = { previewsEnabled = it },
            updateBackgroundPreviewsOnWiFiOnly = { wifiOnly = it },
            updateBackgroundPreviewsRequireCharging = { chargingOnly = it },
            updateBackgroundPreviewsDailyLimit = { dailyLimit = it },
            updateTranslationMode = { translationMode = it },
        )

        composeRule
            .onNodeWithText("Provider")
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithText("DeepSeek")
            .performClick()

        composeRule
            .onNodeWithText("Generate list previews in the background")
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithText("Run background previews on Wi-Fi only")
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithText("Require charging for background previews")
            .performScrollTo()
            .performClick()

        composeRule
            .onNodeWithText("Daily background preview limit")
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithText("50 articles per day")
            .performClick()

        composeRule
            .onNodeWithText("Translation display")
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithText("Side-by-side")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(AiProvider.DEEPSEEK, provider)
            assertTrue(previewsEnabled)
            assertTrue(wifiOnly)
            assertTrue(chargingOnly)
            assertEquals(50, dailyLimit)
            assertEquals(AiTranslationMode.PARALLEL, translationMode)
        }
    }

    @Test
    fun `busy cache action is visibly disabled`() {
        var clearCount = 0
        setPanel(
            isClearingAiCache = true,
            clearAiCache = { clearCount += 1 },
        )

        composeRule
            .onNodeWithText("Clearing AI cache…")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.runOnIdle {
            assertEquals(0, clearCount)
        }
    }

    @Test
    fun `provider configuration fields keep keyboard focus order`() {
        setPanel()
        val baseUrlField = textField("API base URL")
        val apiKeyField = textField("API key")
        val modelField = textField("Model")

        baseUrlField
            .performScrollTo()
            .performClick()
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        apiKeyField
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        modelField.assertIsFocused()
    }

    private fun setPanel(
        updateEnabled: (Boolean) -> Unit = {},
        updateProvider: (AiProvider) -> Unit = {},
        updateBaseUrl: (String) -> Unit = {},
        updateApiKey: (String) -> Unit = {},
        updateModel: (String) -> Unit = {},
        updateBackgroundPreviewsEnabled: (Boolean) -> Unit = {},
        updateBackgroundPreviewsOnWiFiOnly: (Boolean) -> Unit = {},
        updateBackgroundPreviewsRequireCharging: (Boolean) -> Unit = {},
        updateBackgroundPreviewsDailyLimit: (Int) -> Unit = {},
        updateTranslationMode: (AiTranslationMode) -> Unit = {},
        isClearingAiCache: Boolean = false,
        clearAiCache: () -> Unit = {},
    ) {
        composeRule.setContent {
            var currentBaseUrl by remember { mutableStateOf("") }
            var currentApiKey by remember { mutableStateOf("") }
            var currentModel by remember { mutableStateOf("") }

            CapyTheme {
                AiSettingsPanelView(
                    enabled = false,
                    updateEnabled = updateEnabled,
                    provider = AiProvider.OPENAI_COMPATIBLE,
                    updateProvider = updateProvider,
                    baseUrl = currentBaseUrl,
                    updateBaseUrl = {
                        currentBaseUrl = it
                        updateBaseUrl(it)
                    },
                    apiKey = currentApiKey,
                    updateApiKey = {
                        currentApiKey = it
                        updateApiKey(it)
                    },
                    model = currentModel,
                    updateModel = {
                        currentModel = it
                        updateModel(it)
                    },
                    language = "",
                    updateLanguage = {},
                    maxInputCharacters = "12000",
                    updateMaxInputCharacters = {},
                    backgroundPreviewsEnabled = false,
                    updateBackgroundPreviewsEnabled =
                        updateBackgroundPreviewsEnabled,
                    backgroundPreviewsOnWiFiOnly = false,
                    updateBackgroundPreviewsOnWiFiOnly =
                        updateBackgroundPreviewsOnWiFiOnly,
                    backgroundPreviewsRequireCharging = false,
                    updateBackgroundPreviewsRequireCharging =
                        updateBackgroundPreviewsRequireCharging,
                    backgroundPreviewsDailyLimit = 20,
                    updateBackgroundPreviewsDailyLimit =
                        updateBackgroundPreviewsDailyLimit,
                    translationMode = AiTranslationMode.REPLACE_ORIGINAL,
                    updateTranslationMode = updateTranslationMode,
                    translatePrompt = "",
                    updateTranslatePrompt = {},
                    summarizePrompt = "",
                    updateSummarizePrompt = {},
                    previewSummaryPrompt = "",
                    updatePreviewSummaryPrompt = {},
                    keyPointsPrompt = "",
                    updateKeyPointsPrompt = {},
                    isClearingAiCache = isClearingAiCache,
                    clearAiCache = clearAiCache,
                )
            }
        }
    }

    private fun textField(label: String) = composeRule.onNode(
        hasSetTextAction() and hasText(label)
    )
}
