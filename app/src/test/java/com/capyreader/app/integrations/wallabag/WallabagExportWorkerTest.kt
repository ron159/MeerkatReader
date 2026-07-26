package com.capyreader.app.integrations.wallabag

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Test

class WallabagExportWorkerTest {
    @Test
    fun `exports require a connected network`() {
        assertEquals(
            NetworkType.CONNECTED,
            wallabagExportConstraints().requiredNetworkType,
        )
    }
}
