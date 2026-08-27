package com.capyreader.app.ui.articles

import com.capyreader.app.ui.articles.list.ArticleHomeDestination
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleHomeActionsTest {
    @Test
    fun `add feed action is shown only on the feeds home page`() {
        assertTrue(
            shouldShowAddFeedButton(
                destination = ArticleHomeDestination.FEEDS,
                isHomeFilter = true,
            )
        )
        assertFalse(
            shouldShowAddFeedButton(
                destination = ArticleHomeDestination.UNREAD,
                isHomeFilter = true,
            )
        )
        assertFalse(
            shouldShowAddFeedButton(
                destination = ArticleHomeDestination.STARRED,
                isHomeFilter = true,
            )
        )
        assertFalse(
            shouldShowAddFeedButton(
                destination = ArticleHomeDestination.FEEDS,
                isHomeFilter = false,
            )
        )
    }
}
