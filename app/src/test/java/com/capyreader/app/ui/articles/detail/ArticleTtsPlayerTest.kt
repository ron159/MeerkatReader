package com.capyreader.app.ui.articles.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import com.capyreader.app.common.AudioEnclosure
import com.capyreader.app.tts.ArticleTtsState
import com.capyreader.app.tts.ArticleTtsStatus
import com.capyreader.app.ui.theme.CapyTheme
import com.jocmp.capy.persistence.ArticleTtsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    application = android.app.Application::class,
    qualifiers = "w320dp-h900dp",
)
class ArticleTtsPlayerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `idle state does not render the floating player`() {
        setPlayer(
            state = state(status = ArticleTtsStatus.IDLE),
            modifier = Modifier.testTag(PLAYER_TAG),
        )

        composeRule.onNodeWithTag(PLAYER_TAG).assertDoesNotExist()
    }

    @Test
    fun `all visible statuses use stable localized text without engine details`() {
        composeRule.setContent {
            CapyTheme {
                Column {
                    androidx.compose.material3.Text(
                        ttsStatusText(state(status = ArticleTtsStatus.INITIALIZING))
                    )
                    androidx.compose.material3.Text(
                        ttsStatusText(state(status = ArticleTtsStatus.PLAYING))
                    )
                    androidx.compose.material3.Text(
                        ttsStatusText(state(status = ArticleTtsStatus.PAUSED))
                    )
                    androidx.compose.material3.Text(
                        ttsStatusText(state(status = ArticleTtsStatus.COMPLETED))
                    )
                    androidx.compose.material3.Text(
                        ttsStatusText(state(status = ArticleTtsStatus.UNAVAILABLE))
                    )
                    androidx.compose.material3.Text(
                        ttsStatusText(
                            state(
                                status = ArticleTtsStatus.ERROR,
                                errorMessage = "engine-secret diagnostic",
                            )
                        )
                    )
                }
            }
        }

        composeRule.onNodeWithText("Starting text-to-speech…").assertExists()
        composeRule
            .onAllNodesWithText("Sentence 2 of 4")
            .assertCountEquals(2)
        composeRule.onNodeWithText("Finished").assertExists()
        composeRule
            .onAllNodesWithText("Text-to-speech is unavailable")
            .assertCountEquals(2)
        composeRule.onNodeWithText("engine-secret diagnostic").assertDoesNotExist()
    }

    @Test
    fun `first sentence enables play next and close exactly once`() {
        val callbacks = PlayerCallbacks()
        setPlayer(
            state = state(
                status = ArticleTtsStatus.PAUSED,
                sentenceIndex = 0,
            ),
            callbacks = callbacks,
        )

        composeRule
            .onNodeWithContentDescription("Previous sentence")
            .assertIsNotEnabled()
        composeRule
            .onNodeWithContentDescription("Play")
            .assertIsEnabled()
            .performClick()
        composeRule
            .onNodeWithContentDescription("Next sentence")
            .assertIsEnabled()
            .performClick()
        composeRule
            .onNodeWithContentDescription("Close text-to-speech")
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(PlayerCallbacks(playPause = 1, next = 1, close = 1), callbacks)
        }
    }

    @Test
    fun `middle playing sentence exposes pause and both skip callbacks`() {
        val callbacks = PlayerCallbacks()
        setPlayer(
            state = state(status = ArticleTtsStatus.PLAYING),
            callbacks = callbacks,
        )

        composeRule
            .onNodeWithContentDescription("Previous sentence")
            .assertIsEnabled()
            .performClick()
        composeRule
            .onNodeWithContentDescription("Pause")
            .assertIsEnabled()
            .performClick()
        composeRule
            .onNodeWithContentDescription("Next sentence")
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(
                PlayerCallbacks(previous = 1, playPause = 1, next = 1),
                callbacks,
            )
        }
    }

    @Test
    fun `last completed sentence disables next and permits restart`() {
        val callbacks = PlayerCallbacks()
        setPlayer(
            state = state(
                status = ArticleTtsStatus.COMPLETED,
                sentenceIndex = 3,
            ),
            callbacks = callbacks,
        )

        composeRule
            .onNodeWithContentDescription("Previous sentence")
            .assertIsEnabled()
            .performClick()
        composeRule
            .onNodeWithContentDescription("Next sentence")
            .assertIsNotEnabled()
        composeRule
            .onNodeWithContentDescription("Play")
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertEquals(PlayerCallbacks(previous = 1, playPause = 1), callbacks)
        }
    }

    @Test
    fun `initializing disables playback controls`() {
        setPlayer(
            state = state(
                status = ArticleTtsStatus.INITIALIZING,
                sentenceIndex = 0,
                sentenceCount = 0,
            )
        )

        composeRule.onNodeWithContentDescription("Previous sentence").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Play").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Next sentence").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Close text-to-speech").assertIsEnabled()
    }

    @Test
    fun `single source is informational without an action role`() {
        setPlayer(
            state = state(status = ArticleTtsStatus.PAUSED),
            availableSources = listOf(ArticleTtsSource.ORIGINAL),
        )

        composeRule
            .onNodeWithText("Original")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }

    @Test
    fun `multiple sources use deterministic order and update active source once`() {
        val selectedSources = mutableListOf<ArticleTtsSource>()
        val unorderedSources = listOf(
            ArticleTtsSource.TRANSLATION,
            ArticleTtsSource.ORIGINAL,
            ArticleTtsSource.AI_SUMMARY,
        )
        assertEquals(
            ArticleTtsSource.entries,
            orderedTtsSources(unorderedSources, ArticleTtsSource.ORIGINAL),
        )

        composeRule.setContent {
            var currentSource by remember { mutableStateOf(ArticleTtsSource.ORIGINAL) }
            CapyTheme {
                ArticleTtsPlayer(
                    state = state(
                        status = ArticleTtsStatus.PAUSED,
                        source = currentSource,
                    ),
                    onPlayPause = {},
                    onSkipPrevious = {},
                    onSkipNext = {},
                    onDismiss = {},
                    availableSources = unorderedSources,
                    onSelectSource = { source ->
                        selectedSources += source
                        currentSource = source
                    },
                )
            }
        }

        composeRule
            .onNodeWithText("Original")
            .assertHasClickAction()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    Role.Button,
                )
            )
            .performClick()
        val aiSummaryTop = composeRule
            .onNodeWithText("AI summary")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        val translationTop = composeRule
            .onNodeWithText("Translation")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertTrue(aiSummaryTop < translationTop)

        composeRule.onNodeWithText("AI summary").performClick()
        composeRule
            .onNodeWithText("AI summary")
            .assertHasClickAction()
            .performClick()
        composeRule
            .onAllNodesWithText("AI summary")
            .assertCountEquals(2)

        composeRule.runOnIdle {
            assertEquals(listOf(ArticleTtsSource.AI_SUMMARY), selectedSources)
        }
    }

    @Test
    fun `compact player stays bounded and follows source then control focus order`() {
        val longTitle = "A very long article title that must remain inside the compact player"
        setPlayer(
            state = state(
                status = ArticleTtsStatus.PLAYING,
                articleTitle = longTitle,
            ),
            availableSources = ArticleTtsSource.entries,
            modifier = Modifier.testTag(PLAYER_TAG),
        )
        val playerBounds = composeRule
            .onNodeWithTag(PLAYER_TAG)
            .fetchSemanticsNode()
            .boundsInRoot
        val source = composeRule.onNodeWithText("Original")
        val previous = composeRule.onNodeWithContentDescription("Previous sentence")
        val pause = composeRule.onNodeWithContentDescription("Pause")
        val next = composeRule.onNodeWithContentDescription("Next sentence")
        val close = composeRule.onNodeWithContentDescription("Close text-to-speech")

        listOf(
            composeRule.onNodeWithText(longTitle),
            composeRule.onNodeWithText("Sentence 2 of 4"),
            source,
            previous,
            pause,
            next,
            close,
        ).forEach { interaction ->
            val bounds = interaction.fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.left >= playerBounds.left)
            assertTrue(bounds.right <= playerBounds.right)
            assertTrue(bounds.top >= playerBounds.top)
            assertTrue(bounds.bottom <= playerBounds.bottom)
        }

        val controlBounds = listOf(previous, pause, next, close)
            .map { it.fetchSemanticsNode().boundsInRoot }
        controlBounds.zipWithNext().forEach { (left, right) ->
            assertTrue(left.right <= right.left)
        }

        source
            .requestFocus()
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        previous
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        pause
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        next
            .assertIsFocused()
            .performKeyInput { pressKey(Key.Tab) }
        close.assertIsFocused()
    }

    @Test
    fun `toolbar button exposes listen or pause and dispatches once`() {
        var listenCount = 0
        var pauseCount = 0
        composeRule.setContent {
            CapyTheme {
                Column {
                    ArticleTtsToolbarButton(
                        isPlaying = false,
                        onToggle = { listenCount += 1 },
                    )
                    ArticleTtsToolbarButton(
                        isPlaying = true,
                        onToggle = { pauseCount += 1 },
                    )
                }
            }
        }

        composeRule
            .onNodeWithContentDescription("Listen to article")
            .performClick()
        composeRule
            .onNodeWithContentDescription("Pause")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, listenCount)
            assertEquals(1, pauseCount)
        }
    }

    @Test
    fun `toolbar start pauses enclosure audio before playing tts`() {
        val events = mutableListOf<String>()

        toggleArticleTtsFromToolbar(
            state = state(status = ArticleTtsStatus.IDLE),
            articleID = "article",
            onPauseAudio = { events += "pause-audio" },
            onPauseTts = { events += "pause-tts" },
            onPlayTts = { events += "play-tts" },
        )

        assertEquals(listOf("pause-audio", "play-tts"), events)
    }

    @Test
    fun `toolbar pause leaves enclosure audio untouched`() {
        val events = mutableListOf<String>()

        toggleArticleTtsFromToolbar(
            state = state(status = ArticleTtsStatus.PLAYING),
            articleID = "article",
            onPauseAudio = { events += "pause-audio" },
            onPauseTts = { events += "pause-tts" },
            onPlayTts = { events += "play-tts" },
        )

        assertEquals(listOf("pause-tts"), events)
    }

    @Test
    fun `toolbar resume keeps enclosure audio paused`() {
        val events = mutableListOf<String>()

        toggleArticleTtsFromToolbar(
            state = state(status = ArticleTtsStatus.PAUSED),
            articleID = "article",
            onPauseAudio = { events += "pause-audio" },
            onPauseTts = { events += "pause-tts" },
            onPlayTts = { events += "play-tts" },
        )

        assertEquals(listOf("pause-audio", "play-tts"), events)
    }

    @Test
    fun `late tts state from another article is dismissed exactly once`() {
        var activeTtsArticleID by mutableStateOf<String?>(null)
        var dismissCount = 0
        composeRule.setContent {
            DismissTtsForOtherArticleEffect(
                articleID = "visible-article",
                ttsArticleID = activeTtsArticleID,
                onDismiss = { dismissCount += 1 },
            )
        }

        composeRule.runOnIdle {
            activeTtsArticleID = "previous-article"
        }
        composeRule.waitForIdle()

        assertEquals(1, dismissCount)

        composeRule.runOnIdle {
            activeTtsArticleID = "visible-article"
        }
        composeRule.waitForIdle()

        assertEquals(1, dismissCount)
    }

    @Test
    fun `selecting enclosure audio dismisses tts before forwarding selection`() {
        val audio = AudioEnclosure(
            url = "https://example.com/audio.mp3",
            title = "Episode",
            feedName = "Feed",
            durationSeconds = 60,
            artworkUrl = null,
        )
        val events = mutableListOf<String>()
        var selectedAudio: AudioEnclosure? = null

        selectArticleAudio(
            audio = audio,
            onDismissTts = { events += "dismiss-tts" },
            onSelectAudio = {
                events += "select-audio"
                selectedAudio = it
            },
        )

        assertEquals(listOf("dismiss-tts", "select-audio"), events)
        assertEquals(audio, selectedAudio)
    }

    private fun setPlayer(
        state: ArticleTtsState,
        availableSources: List<ArticleTtsSource> = listOf(ArticleTtsSource.ORIGINAL),
        callbacks: PlayerCallbacks = PlayerCallbacks(),
        modifier: Modifier = Modifier,
    ) {
        composeRule.setContent {
            CapyTheme {
                ArticleTtsPlayer(
                    state = state,
                    onPlayPause = { callbacks.playPause += 1 },
                    onSkipPrevious = { callbacks.previous += 1 },
                    onSkipNext = { callbacks.next += 1 },
                    onDismiss = { callbacks.close += 1 },
                    availableSources = availableSources,
                    modifier = modifier,
                )
            }
        }
    }

    private fun state(
        status: ArticleTtsStatus,
        articleTitle: String = "Article title",
        source: ArticleTtsSource = ArticleTtsSource.ORIGINAL,
        sentenceIndex: Int = 1,
        sentenceCount: Int = 4,
        errorMessage: String? = null,
    ) = ArticleTtsState(
        articleID = "article",
        articleTitle = articleTitle,
        source = source,
        status = status,
        sentenceIndex = sentenceIndex,
        sentenceCount = sentenceCount,
        errorMessage = errorMessage,
    )

    private data class PlayerCallbacks(
        var previous: Int = 0,
        var playPause: Int = 0,
        var next: Int = 0,
        var close: Int = 0,
    )

    private companion object {
        const val PLAYER_TAG = "article-tts-player"
    }
}
