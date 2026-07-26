package com.capyreader.app.ai

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleAiPreviewWorkerTest {
    @Test
    fun `constraints combine unmetered network and charging`() {
        val constraints = articleAiPreviewConstraints(
            wiFiOnly = true,
            requiresCharging = true,
        )

        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresCharging())
    }

    @Test
    fun `constraints allow connected network and battery`() {
        val constraints = articleAiPreviewConstraints(
            wiFiOnly = false,
            requiresCharging = false,
        )

        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertFalse(constraints.requiresCharging())
    }
}
