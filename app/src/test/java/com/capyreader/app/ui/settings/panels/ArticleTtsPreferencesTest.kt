package com.capyreader.app.ui.settings.panels

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.capyreader.app.tts.ArticleTtsVoice
import com.capyreader.app.ui.theme.CapyTheme
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    application = android.app.Application::class,
    qualifiers = "en-rUS-w320dp-h900dp",
)
class ArticleTtsPreferencesTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `system defaults local network voice and speech rate dispatch exact values`() {
        val updates = mutableListOf<PreferenceUpdate>()
        setPreferences(
            onSelectLanguage = { updates += PreferenceUpdate.Language(it) },
            onSelectVoice = { updates += PreferenceUpdate.Voice(it) },
            onSelectSpeechRate = { updates += PreferenceUpdate.SpeechRate(it) },
        )

        composeRule.onNodeWithText("System language").assertExists()
        composeRule.onNodeWithText("Engine default").assertExists()
        composeRule.onNodeWithText("1×").assertExists()

        composeRule.onNodeWithText("Reading language").performClick()
        composeRule
            .onNodeWithText(
                Locale.forLanguageTag("zh-CN").getDisplayName(Locale.US)
            )
            .performClick()

        composeRule.onNodeWithText("Voice").performClick()
        composeRule
            .onNodeWithText("cloud-voice (network)")
            .performClick()

        composeRule.onNodeWithText("Reading speed").performClick()
        composeRule.onNodeWithText("1.5×").performClick()

        composeRule.runOnIdle {
            assertEquals(
                listOf(
                    PreferenceUpdate.Language("zh-CN"),
                    PreferenceUpdate.Voice("cloud-voice"),
                    PreferenceUpdate.SpeechRate(1.5f),
                ),
                updates,
            )
        }
    }

    @Test
    fun `preference focus order follows language voice then speech rate`() {
        setPreferences()
        val selectors = composeRule.onAllNodes(
            hasClickAction(),
            useUnmergedTree = true,
        )
        val language = selectors[0]
        val voice = selectors[1]
        val rate = selectors[2]

        language
            .requestFocus()
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        voice
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        rate.assertIsFocused()
    }

    private fun setPreferences(
        onSelectLanguage: (String) -> Unit = {},
        onSelectVoice: (String) -> Unit = {},
        onSelectSpeechRate: (Float) -> Unit = {},
    ) {
        composeRule.setContent {
            CapyTheme {
                ArticleTtsPreferences(
                    languageTag = "",
                    languageTags = listOf("", "en-US", "zh-CN"),
                    onSelectLanguage = onSelectLanguage,
                    voiceID = "",
                    voices = listOf(
                        ArticleTtsVoice(
                            id = "local-voice",
                            languageTag = "en-US",
                            requiresNetwork = false,
                        ),
                        ArticleTtsVoice(
                            id = "cloud-voice",
                            languageTag = "en-US",
                            requiresNetwork = true,
                        ),
                    ),
                    onSelectVoice = onSelectVoice,
                    speechRate = 1f,
                    onSelectSpeechRate = onSelectSpeechRate,
                )
            }
        }
    }

    private sealed interface PreferenceUpdate {
        data class Language(val value: String) : PreferenceUpdate
        data class Voice(val value: String) : PreferenceUpdate
        data class SpeechRate(val value: Float) : PreferenceUpdate
    }
}
