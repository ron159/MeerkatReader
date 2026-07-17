package com.capyreader.app.transfers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticBackupTest {
    @Test
    fun `retention removes oldest automatic backups`() {
        val newest = document("newest", lastModified = 300)
        val middle = document("middle", lastModified = 200)
        val oldest = document("oldest", lastModified = 100)

        val result = automaticBackupsToDelete(
            documents = listOf(oldest, newest, middle),
            retention = 2,
        )

        assertEquals(listOf(oldest), result)
    }

    @Test
    fun `retention uses name as stable tie breaker`() {
        val newerName = document("meerkat-backup-20260628-120001.json", lastModified = 0)
        val olderName = document("meerkat-backup-20260628-120000.json", lastModified = 0)

        val result = automaticBackupsToDelete(
            documents = listOf(olderName, newerName),
            retention = 1,
        )

        assertEquals(listOf(olderName), result)
    }

    @Test
    fun `automatic backup requires available storage`() {
        val constraints = automaticBackupConstraints()

        assertTrue(constraints.requiresStorageNotLow())
        assertFalse(constraints.requiresCharging())
    }

    private fun document(name: String, lastModified: Long) =
        AutomaticBackupDocument(
            documentID = name,
            name = name,
            lastModified = lastModified,
        )
}
