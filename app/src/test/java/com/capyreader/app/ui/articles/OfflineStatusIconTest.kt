package com.capyreader.app.ui.articles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.capyreader.app.R
import com.capyreader.app.ui.theme.CapyTheme
import com.jocmp.capy.ArticleOfflinePackageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(
    application = android.app.Application::class,
    qualifiers = "w320dp-h800dp",
)
class OfflineStatusIconTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `every offline state maps to the intended icon description and announcement mode`() {
        offlineStatusCases.forEach { expected ->
            val actual = offlineStatusVisual(expected.state)

            assertSame(expected.icon, actual.icon)
            assertEquals(expected.descriptionRes, actual.descriptionRes)
            assertEquals(expected.announcesChanges, actual.announcesChanges)
        }
    }

    @Test
    fun `all offline states expose exact informational localized semantics`() {
        composeRule.setContent {
            CapyTheme {
                Column {
                    offlineStatusCases.forEach { expected ->
                        OfflineStatusIcon(
                            state = expected.state,
                            tint = Color.Black,
                            fontScale = ArticleListFontScale.MEDIUM,
                        )
                    }
                }
            }
        }

        offlineStatusCases.forEach { expected ->
            composeRule
                .onNodeWithContentDescription(expected.description)
                .assertExists()
                .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.OnClick))
                .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
                .assert(
                    if (expected.announcesChanges) {
                        SemanticsMatcher.expectValue(
                            SemanticsProperties.LiveRegion,
                            LiveRegionMode.Polite,
                        )
                    } else {
                        SemanticsMatcher.keyNotDefined(SemanticsProperties.LiveRegion)
                    }
                )
        }
    }

    @Test
    fun `article metadata keeps one offline announcement before time at every font scale`() {
        val scales = ArticleListFontScale.entries
        val timeLabels = scales.mapIndexed { index, _ -> "${index + 1}:00" }

        composeRule.setContent {
            CapyTheme {
                Column {
                    scales.forEachIndexed { index, scale ->
                        StyleProviders(ArticleRowOptions(fontScale = scale)) {
                            ArticleRowStatusMetadata(
                                starred = true,
                                hasAudio = true,
                                offlineState = ArticleOfflinePackageState.READY,
                                relativeTimeText = timeLabels[index],
                                tint = Color.Black,
                                fontScale = scale,
                                modifier = Modifier
                                    .width(METADATA_WIDTH)
                                    .testTag(metadataTag(scale)),
                            )
                        }
                    }
                }
            }
        }

        val describedNodes = composeRule
            .onAllNodes(
                SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription),
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()
        val offlineNodes = composeRule
            .onAllNodesWithContentDescription(
                "Available offline",
                useUnmergedTree = true,
            )
            .fetchSemanticsNodes()
            .sortedBy { it.boundsInRoot.top }

        assertEquals(scales.size, describedNodes.size)
        assertEquals(scales.size, offlineNodes.size)

        val statusWidths = offlineNodes.map { it.boundsInRoot.width }

        scales.forEachIndexed { index, scale ->
            val rowBounds = composeRule
                .onNodeWithTag(metadataTag(scale))
                .fetchSemanticsNode()
                .boundsInRoot
            val statusBounds = offlineNodes[index].boundsInRoot
            val timeBounds = composeRule
                .onNodeWithText(timeLabels[index])
                .fetchSemanticsNode()
                .boundsInRoot

            assertTrue(statusBounds.width > 0f)
            assertTrue(statusBounds.height > 0f)
            assertTrue(statusBounds.right <= timeBounds.left)
            assertTrue(timeBounds.right <= rowBounds.right)
            assertTrue(statusBounds.width <= rowBounds.width * MAX_STATUS_WIDTH_FRACTION)
        }
        assertEquals(statusWidths.sorted(), statusWidths)
    }

    private fun metadataTag(scale: ArticleListFontScale) = "metadata-${scale.name}"

    private data class OfflineStatusCase(
        val state: ArticleOfflinePackageState,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val descriptionRes: Int,
        val description: String,
        val announcesChanges: Boolean,
    )

    private companion object {
        val METADATA_WIDTH = 180.dp
        const val MAX_STATUS_WIDTH_FRACTION = 0.2f

        val offlineStatusCases = listOf(
            OfflineStatusCase(
                state = ArticleOfflinePackageState.NOT_DOWNLOADED,
                icon = Icons.Rounded.Download,
                descriptionRes = R.string.article_offline_status_not_downloaded,
                description = "Not downloaded for offline reading",
                announcesChanges = false,
            ),
            OfflineStatusCase(
                state = ArticleOfflinePackageState.QUEUED,
                icon = Icons.Rounded.Download,
                descriptionRes = R.string.article_offline_status_queued,
                description = "Offline download queued",
                announcesChanges = true,
            ),
            OfflineStatusCase(
                state = ArticleOfflinePackageState.DOWNLOADING,
                icon = Icons.Rounded.Download,
                descriptionRes = R.string.article_offline_status_downloading,
                description = "Offline download in progress",
                announcesChanges = true,
            ),
            OfflineStatusCase(
                state = ArticleOfflinePackageState.READY,
                icon = Icons.Rounded.DownloadDone,
                descriptionRes = R.string.article_offline_status_ready,
                description = "Available offline",
                announcesChanges = true,
            ),
            OfflineStatusCase(
                state = ArticleOfflinePackageState.STALE,
                icon = Icons.Rounded.Download,
                descriptionRes = R.string.article_offline_status_stale,
                description = "Offline copy will be refreshed",
                announcesChanges = true,
            ),
            OfflineStatusCase(
                state = ArticleOfflinePackageState.FAILED,
                icon = Icons.Rounded.ErrorOutline,
                descriptionRes = R.string.article_offline_status_failed,
                description = "Offline download failed",
                announcesChanges = true,
            ),
        )
    }
}
