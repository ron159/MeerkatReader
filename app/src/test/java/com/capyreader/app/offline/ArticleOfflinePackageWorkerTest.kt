package com.capyreader.app.offline

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleOfflinePackageWorkerTest {
    @Test
    fun `constraints require unmetered network and charging when enabled`() {
        val constraints = offlinePackageConstraints(
            wiFiOnly = true,
            requiresCharging = true,
        )

        assertEquals(NetworkType.UNMETERED, constraints.requiredNetworkType)
        assertTrue(constraints.requiresCharging())
    }

    @Test
    fun `constraints allow connected network and battery by default`() {
        val constraints = offlinePackageConstraints(
            wiFiOnly = false,
            requiresCharging = false,
        )

        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
        assertFalse(constraints.requiresCharging())
    }
}
