package com.capyreader.app.ui.settings.panels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.capyreader.app.ui.theme.CapyTheme
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
    qualifiers = "w411dp-h891dp",
)
class IntegrationSettingsPanelViewTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `Wallabag fields privacy error and callbacks are wired`() {
        var enabled = false
        var serverUrl = ""
        var accessToken = ""
        setPanel(
            wallabagLastError = "Token expired",
            updateWallabagEnabled = { enabled = it },
            updateWallabagServerUrl = { serverUrl = it },
            updateWallabagAccessToken = { accessToken = it },
        )

        composeRule
            .onNodeWithText("Enable Wallabag")
            .performScrollTo()
            .performClick()
        textField("Server URL")
            .performScrollTo()
            .performTextReplacement("https://wallabag.example")
        val tokenField = textField("Access token")
        tokenField.assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
        )
        tokenField
            .performScrollTo()
            .performTextReplacement("wallabag-token")

        composeRule
            .onNodeWithText(
                "The token stays on this device and is excluded from backups"
            )
            .performScrollTo()
            .assertExists()
        composeRule
            .onNodeWithText("Last error: Token expired")
            .performScrollTo()
            .assertExists()
        composeRule.runOnIdle {
            assertTrue(enabled)
            assertEquals("https://wallabag.example", serverUrl)
            assertEquals("wallabag-token", accessToken)
        }
    }

    @Test
    fun `WebDAV readiness testing and manual backup actions are wired once`() {
        var enabled = false
        var testCount = 0
        var backupCount = 0
        var directoryUrl = ""
        var username = ""
        var password = ""
        setPanel(
            updateWebDavBackupEnabled = { enabled = it },
            updateWebDavDirectoryUrl = { directoryUrl = it },
            updateWebDavUsername = { username = it },
            updateWebDavPassword = { password = it },
            testWebDavConnection = { testCount += 1 },
            backUpToWebDavNow = { backupCount += 1 },
        )

        composeRule
            .onNodeWithText("Test Connection")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule
            .onNodeWithText("Back Up to WebDAV Now")
            .performScrollTo()
            .assertIsNotEnabled()

        textField("WebDAV directory URL")
            .performScrollTo()
            .performTextReplacement("https://dav.example/backups/")
        textField("Username")
            .performScrollTo()
            .performTextReplacement("reader")
        val passwordField = textField("App password")
        passwordField.assert(
            SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)
        )
        passwordField
            .performScrollTo()
            .performTextReplacement("app-password")

        composeRule
            .onNodeWithText("Test Connection")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule
            .onNodeWithText("Testing…")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule
            .onNodeWithText("Back Up to WebDAV Now")
            .performScrollTo()
            .assertIsNotEnabled()

        composeRule
            .onNodeWithText("Enable WebDAV backup")
            .performScrollTo()
            .performClick()
        composeRule
            .onNodeWithText("Back Up to WebDAV Now")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle {
            assertTrue(enabled)
            assertEquals("https://dav.example/backups/", directoryUrl)
            assertEquals("reader", username)
            assertEquals("app-password", password)
            assertEquals(1, testCount)
            assertEquals(1, backupCount)
        }
    }

    @Test
    fun `WebDAV success and completed backup states are distinct`() {
        setPanel(
            webDavConnectionTestState = WebDavConnectionTestState.SUCCESS,
            webDavLastBackupAt = 1_700_000_000L,
        )

        composeRule
            .onNodeWithText("Connection successful")
            .performScrollTo()
            .assertExists()
        composeRule
            .onNodeWithText("Last WebDAV backup:", substring = true)
            .performScrollTo()
            .assertExists()
        composeRule
            .onNodeWithText("No WebDAV backup has completed yet")
            .assertDoesNotExist()
    }

    @Test
    fun `WebDAV failures remain visible and credential free`() {
        setPanel(
            webDavConnectionTestState = WebDavConnectionTestState.FAILED,
            webDavConnectionTestError = "WebDAV authentication failed",
            webDavLastError = "WebDAV connection failed",
        )

        composeRule
            .onNodeWithText(
                "Connection failed: WebDAV authentication failed"
            )
            .performScrollTo()
            .assertExists()
        composeRule
            .onNodeWithText("No WebDAV backup has completed yet")
            .performScrollTo()
            .assertExists()
        composeRule
            .onNodeWithText(
                "Last WebDAV backup failed: WebDAV connection failed"
            )
            .performScrollTo()
            .assertExists()
        composeRule
            .onNodeWithText("server-secret", substring = true)
            .assertDoesNotExist()
    }

    private fun setPanel(
        wallabagLastError: String = "",
        updateWallabagEnabled: (Boolean) -> Unit = {},
        updateWallabagServerUrl: (String) -> Unit = {},
        updateWallabagAccessToken: (String) -> Unit = {},
        updateWebDavBackupEnabled: (Boolean) -> Unit = {},
        updateWebDavDirectoryUrl: (String) -> Unit = {},
        updateWebDavUsername: (String) -> Unit = {},
        updateWebDavPassword: (String) -> Unit = {},
        webDavConnectionTestState: WebDavConnectionTestState =
            WebDavConnectionTestState.IDLE,
        webDavConnectionTestError: String = "",
        testWebDavConnection: () -> Unit = {},
        backUpToWebDavNow: () -> Unit = {},
        webDavLastBackupAt: Long = 0L,
        webDavLastError: String = "",
    ) {
        composeRule.setContent {
            var currentWallabagEnabled by remember { mutableStateOf(false) }
            var currentWallabagServerUrl by remember { mutableStateOf("") }
            var currentWallabagAccessToken by remember { mutableStateOf("") }
            var currentWebDavEnabled by remember { mutableStateOf(false) }
            var currentWebDavDirectoryUrl by remember { mutableStateOf("") }
            var currentWebDavUsername by remember { mutableStateOf("") }
            var currentWebDavPassword by remember { mutableStateOf("") }
            var currentConnectionTestState by remember {
                mutableStateOf(webDavConnectionTestState)
            }

            CapyTheme {
                IntegrationSettingsPanelView(
                    wallabagEnabled = currentWallabagEnabled,
                    updateWallabagEnabled = {
                        currentWallabagEnabled = it
                        updateWallabagEnabled(it)
                    },
                    wallabagServerUrl = currentWallabagServerUrl,
                    updateWallabagServerUrl = {
                        currentWallabagServerUrl = it
                        updateWallabagServerUrl(it)
                    },
                    wallabagAccessToken = currentWallabagAccessToken,
                    updateWallabagAccessToken = {
                        currentWallabagAccessToken = it
                        updateWallabagAccessToken(it)
                    },
                    wallabagLastError = wallabagLastError,
                    webDavBackupEnabled = currentWebDavEnabled,
                    updateWebDavBackupEnabled = {
                        currentWebDavEnabled = it
                        updateWebDavBackupEnabled(it)
                    },
                    webDavDirectoryUrl = currentWebDavDirectoryUrl,
                    updateWebDavDirectoryUrl = {
                        currentWebDavDirectoryUrl = it
                        updateWebDavDirectoryUrl(it)
                    },
                    webDavUsername = currentWebDavUsername,
                    updateWebDavUsername = {
                        currentWebDavUsername = it
                        updateWebDavUsername(it)
                    },
                    webDavPassword = currentWebDavPassword,
                    updateWebDavPassword = {
                        currentWebDavPassword = it
                        updateWebDavPassword(it)
                    },
                    webDavConnectionTestState = currentConnectionTestState,
                    webDavConnectionTestError = webDavConnectionTestError,
                    testWebDavConnection = {
                        testWebDavConnection()
                        currentConnectionTestState =
                            WebDavConnectionTestState.TESTING
                    },
                    backUpToWebDavNow = backUpToWebDavNow,
                    webDavLastBackupAt = webDavLastBackupAt,
                    webDavLastError = webDavLastError,
                )
            }
        }
    }

    private fun textField(label: String) = composeRule.onNode(
        hasSetTextAction() and hasText(label)
    )
}
