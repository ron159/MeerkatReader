package com.capyreader.app.ui.settings.panels

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPanelTest {
    @Test
    fun `top level settings are grouped exactly once`() {
        val groupedPanels = SettingsPanel.groups.flatMap { it.panels }

        assertEquals(groupedPanels.distinct(), groupedPanels)
        assertEquals(SettingsPanel.items, groupedPanels)
        assertFalse(SettingsPanel.UnreadBadges in groupedPanels)
        assertTrue(SettingsPanel.UnreadBadges.isNested())
    }
}
