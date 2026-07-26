package com.capyreader.app.ui.settings.panels

import androidx.activity.ComponentDialog
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.capyreader.app.transfers.BackupRestoreMode
import com.capyreader.app.transfers.BackupRestorePreview
import com.capyreader.app.ui.theme.CapyTheme
import com.jocmp.capy.accounts.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(
    application = android.app.Application::class,
    qualifiers = "w411dp-h891dp",
)
class BackupRestorePreviewDialogTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `preview exposes bounded summary and defaults to replace`() {
        setDialog()

        val summary = listOf(
            "Restore backup?",
            "Choose how the selected backup should update current settings.",
            "Version: 2",
            "Account type: reader",
            "Subscriptions: Yes",
            "Saved searches: 3",
            "Read later items: 4",
            "Starred articles: 5",
            "Rules: Yes",
            "AI settings: No",
        )
        summary.forEach { text ->
            composeRule.onNodeWithText(text).assertExists()
        }
        val summaryTops = summary.map { text ->
            composeRule.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.top
        }
        assertEquals(summaryTops.sorted(), summaryTops)
        listOf(
            "account-123",
            "super-secret-password",
            """{"raw":"backup-payload"}""",
        ).forEach { sensitiveValue ->
            composeRule.onNodeWithText(sensitiveValue, substring = true).assertDoesNotExist()
        }
        composeRule
            .onNodeWithText("Replace")
            .assertIsSelected()
        composeRule
            .onNodeWithText(
                "Replace current settings with backup values. " +
                    "Credentials and the current account ID are preserved."
            )
            .assertExists()
        composeRule
            .onNodeWithText(
                "Apply backup values while keeping current settings that are " +
                    "not present in the backup."
            )
            .assertDoesNotExist()
    }

    @Test
    fun `merge selection confirms exact mode once`() {
        var confirmedMode: BackupRestoreMode? = null
        var confirmCount = 0
        setDialog(
            onConfirm = { mode ->
                confirmedMode = mode
                confirmCount += 1
            }
        )

        composeRule
            .onNodeWithText("Merge")
            .performClick()
            .assertIsSelected()
        composeRule
            .onNodeWithText(
                "Apply backup values while keeping current settings that are " +
                    "not present in the backup."
            )
            .assertExists()
        composeRule
            .onNodeWithText(
                "Replace current settings with backup values. " +
                    "Credentials and the current account ID are preserved."
            )
            .assertDoesNotExist()
        composeRule
            .onNodeWithText("Restore")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(BackupRestoreMode.MERGE, confirmedMode)
            assertEquals(1, confirmCount)
        }
    }

    @Test
    fun `cancel never confirms restore`() {
        var dismissCount = 0
        var confirmedMode: BackupRestoreMode? = null
        setDialog(
            onDismiss = { dismissCount += 1 },
            onConfirm = { confirmedMode = it },
        )

        composeRule
            .onNodeWithText("Cancel")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, dismissCount)
            assertNull(confirmedMode)
        }
    }

    @Test
    fun `system back dismissal never confirms restore`() {
        var dismissCount = 0
        var confirmedMode: BackupRestoreMode? = null
        setDialog(
            onDismiss = { dismissCount += 1 },
            onConfirm = { confirmedMode = it },
        )

        val dialog = ShadowDialog.getLatestDialog() as ComponentDialog
        dialog.onBackPressedDispatcher.onBackPressed()

        composeRule.runOnIdle {
            assertEquals(1, dismissCount)
            assertNull(confirmedMode)
        }
    }

    private fun setDialog(
        onDismiss: () -> Unit = {},
        onConfirm: (BackupRestoreMode) -> Unit = {},
    ) {
        composeRule.setContent {
            CapyTheme {
                BackupRestorePreviewDialog(
                    preview = BackupRestorePreview(
                        version = 2,
                        source = Source.READER,
                        hasSubscriptions = true,
                        savedSearchCount = 3,
                        readLaterCount = 4,
                        starredCount = 5,
                        hasRules = true,
                        hasAiSettings = false,
                    ),
                    onDismiss = onDismiss,
                    onConfirm = onConfirm,
                )
            }
        }
    }
}
