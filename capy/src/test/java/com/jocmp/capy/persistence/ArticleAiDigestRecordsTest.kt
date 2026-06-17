package com.jocmp.capy.persistence

import com.jocmp.capy.InMemoryDatabaseProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleAiDigestRecordsTest {
    @Test
    fun upsertStoresDigest() = runTest {
        val records = ArticleAiDigestRecords(InMemoryDatabaseProvider())
        val input = ArticleAiDigestInput(
            id = "digest-id",
            filterJson = """{"source":"visible_articles"}""",
            provider = "OPENAI_COMPATIBLE",
            model = "gpt-4.1-mini",
            language = "English",
            articleIdsJson = """["a","b"]""",
        )

        records.upsert(input, resultText = "Digest text")

        val record = records.find(input.id)!!

        assertEquals(expected = input.id, actual = record.id)
        assertEquals(expected = input.filterJson, actual = record.filterJson)
        assertEquals(expected = input.provider, actual = record.provider)
        assertEquals(expected = input.model, actual = record.model)
        assertEquals(expected = input.language, actual = record.language)
        assertEquals(expected = input.articleIdsJson, actual = record.articleIdsJson)
        assertEquals(expected = "Digest text", actual = record.resultText)
    }

    @Test
    fun upsertReplacesExistingDigest() = runTest {
        val records = ArticleAiDigestRecords(InMemoryDatabaseProvider())
        val input = ArticleAiDigestInput(
            id = "digest-id",
            filterJson = """{"source":"visible_articles"}""",
            provider = "OPENAI_COMPATIBLE",
            model = "gpt-4.1-mini",
            language = "English",
            articleIdsJson = """["a","b"]""",
        )

        records.upsert(input, resultText = "First")
        records.upsert(input, resultText = "Second")

        assertEquals(expected = "Second", actual = records.find(input.id)?.resultText)
    }

    @Test
    fun deleteAllClearsDigests() = runTest {
        val records = ArticleAiDigestRecords(InMemoryDatabaseProvider())
        val input = ArticleAiDigestInput(
            id = "digest-id",
            filterJson = """{"source":"visible_articles"}""",
            provider = "OPENAI_COMPATIBLE",
            model = "gpt-4.1-mini",
            language = "English",
            articleIdsJson = """["a","b"]""",
        )

        records.upsert(input, resultText = "Digest text")
        records.deleteAll()

        assertNull(records.find(input.id))
    }
}
