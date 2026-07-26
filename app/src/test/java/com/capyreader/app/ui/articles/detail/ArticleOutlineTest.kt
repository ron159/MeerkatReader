package com.capyreader.app.ui.articles.detail

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleOutlineTest {
    @Test
    fun `decode and build normalizes extracted headings`() {
        val candidates = ArticleOutline.decodeCandidates(
            """
            [
              {"domIndex":0,"level":2,"title":"  First\n heading  ","id":"existing"},
              {"domIndex":1,"level":3,"title":"Second heading","id":null},
              {"domIndex":2,"level":4,"title":"   ","id":null}
            ]
            """.trimIndent()
        )

        val items = ArticleOutline.build(candidates)

        assertEquals(2, items.size)
        assertEquals("First heading", items[0].title)
        assertEquals("existing", items[0].targetID)
        assertEquals("capy-heading-2", items[1].targetID)
    }

    @Test
    fun `generated heading ids are deterministic`() {
        val candidates = listOf(
            ArticleHeadingCandidate(0, 2, "Introduction"),
            ArticleHeadingCandidate(1, 2, "Details"),
        )

        assertEquals(
            ArticleOutline.build(candidates),
            ArticleOutline.build(candidates),
        )
    }

    @Test
    fun `duplicate heading text receives distinct targets`() {
        val items = ArticleOutline.build(
            listOf(
                ArticleHeadingCandidate(0, 2, "Overview"),
                ArticleHeadingCandidate(1, 2, "Overview"),
            )
        )

        assertNotEquals(items[0].targetID, items[1].targetID)
    }

    @Test
    fun `duplicate existing ids preserve the first and replace later duplicates`() {
        val items = ArticleOutline.build(
            listOf(
                ArticleHeadingCandidate(0, 2, "First", id = "overview"),
                ArticleHeadingCandidate(1, 2, "Second", id = "overview"),
            )
        )

        assertEquals("overview", items[0].targetID)
        assertEquals("capy-heading-2", items[1].targetID)
    }

    @Test
    fun `generated ids do not replace a later existing id`() {
        val items = ArticleOutline.build(
            listOf(
                ArticleHeadingCandidate(0, 2, "First"),
                ArticleHeadingCandidate(1, 2, "Second", id = "capy-heading-1"),
            )
        )

        assertEquals("capy-heading-1-2", items[0].targetID)
        assertEquals("capy-heading-1", items[1].targetID)
    }

    @Test
    fun `outline is only visible at the heading threshold`() {
        val twoItems = ArticleOutline.build(
            List(2) { ArticleHeadingCandidate(it, 2, "Heading $it") }
        )
        val threeItems = ArticleOutline.build(
            List(3) { ArticleHeadingCandidate(it, 2, "Heading $it") }
        )

        assertFalse(ArticleOutline.shouldShow(twoItems))
        assertTrue(ArticleOutline.shouldShow(threeItems))
    }

    @Test
    fun `outline caps excessive heading counts`() {
        val items = ArticleOutline.build(
            List(ArticleOutline.MAX_HEADING_COUNT + 5) {
                ArticleHeadingCandidate(it, 2, "Heading $it")
            }
        )

        assertEquals(ArticleOutline.MAX_HEADING_COUNT, items.size)
    }

    @Test
    fun `jump target is JSON encoded before JavaScript evaluation`() {
        val script = ArticleOutline.jumpScript("heading'\"\\\n")

        assertEquals("""scrollToArticleHeading("heading'\"\\\n")""", script)
    }
}
