package com.capyreader.app.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ArticleTtsTextTest {
    @Test
    fun `extract text removes non-content elements and normalizes whitespace`() {
        val html = """
            <article>
              <style>.hidden { display: none; }</style>
              <h1>Readable title</h1>
              <p>First   paragraph.</p>
              <script>alert("no")</script>
            </article>
        """.trimIndent()

        val text = ArticleTtsText.extractText(html)

        assertEquals("Readable title First paragraph.", text)
        assertFalse(text.contains("alert"))
        assertFalse(text.contains("display"))
    }

    @Test
    fun `sentences follow locale boundaries`() {
        val sentences = ArticleTtsText.sentences(
            text = "第一句。第二句！第三句？",
            locale = Locale.CHINESE,
        )

        assertEquals(listOf("第一句。", "第二句！", "第三句？"), sentences)
    }

    @Test
    fun `long sentences are split below engine input limit`() {
        val text = List(1_000) { "word" }.joinToString(" ")

        val sentences = ArticleTtsText.sentences(text, Locale.ENGLISH)

        assertTrue(sentences.size > 1)
        assertTrue(sentences.all { it.length <= 3_000 })
        assertEquals(text, sentences.joinToString(" "))
    }
}
