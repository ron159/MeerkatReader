package com.capyreader.app.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.capyreader.app.ui.theme.CapyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class TextSwitchTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `title subtitle and switch are one labelled full-row toggle`() {
        var updates = 0
        composeRule.setContent {
            var checked by remember { mutableStateOf(false) }

            CapyTheme {
                TextSwitch(
                    checked = checked,
                    onCheckedChange = {
                        checked = it
                        updates += 1
                    },
                    title = "Enable feature",
                    subtitle = "Feature context",
                )
            }
        }

        composeRule
            .onAllNodes(roleMatcher(Role.Switch))
            .assertCountEquals(1)
        composeRule
            .onNodeWithText("Enable feature")
            .assertIsEnabled()
            .assertIsOff()
            .assertHasClickAction()
            .performClick()
            .assertIsOn()
        composeRule
            .onNodeWithText("Feature context")
            .assertIsOn()
        composeRule.runOnIdle {
            assertEquals(1, updates)
        }
    }

    @Test
    fun `disabled row keeps one labelled non-actionable switch`() {
        var updates = 0
        composeRule.setContent {
            CapyTheme {
                TextSwitch(
                    checked = true,
                    onCheckedChange = { updates += 1 },
                    title = "Unavailable feature",
                    enabled = false,
                )
            }
        }

        composeRule
            .onAllNodes(roleMatcher(Role.Switch))
            .assertCountEquals(1)
        composeRule
            .onNodeWithText("Unavailable feature")
            .assertIsNotEnabled()
            .assertIsOn()
        composeRule.runOnIdle {
            assertEquals(0, updates)
        }
    }

    private fun roleMatcher(role: Role) = SemanticsMatcher.expectValue(
        SemanticsProperties.Role,
        role,
    )
}
