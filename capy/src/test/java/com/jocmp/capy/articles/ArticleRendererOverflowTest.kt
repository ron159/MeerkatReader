package com.jocmp.capy.articles

import com.jocmp.capy.InMemoryPreference
import com.jocmp.capy.fixtures.ArticleFixture
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleRendererOverflowTest {
    @Test
    fun `horizontal pagination wraps code and constrains tables`() {
        val html = renderer(horizontalPagination = true).render(
            article = ArticleFixture().create(),
            byline = "",
            colors = emptyMap(),
            hideImages = false,
        )

        assertTrue(html.contains("--pre-white-space:pre-wrap"))
        assertTrue(html.contains("--code-overflow-x:visible"))
        assertTrue(html.contains("--code-overflow-wrap:anywhere"))
        assertTrue(html.contains("--table-overflow-x:visible"))
        assertTrue(html.contains("--table-layout:fixed"))
        assertTrue(html.contains("--table-width:100%"))
    }

    @Test
    fun `disabled horizontal pagination allows code and wide tables to scroll`() {
        val html = renderer(horizontalPagination = false).render(
            article = ArticleFixture().create(),
            byline = "",
            colors = emptyMap(),
            hideImages = false,
        )

        assertTrue(html.contains("--pre-white-space:pre"))
        assertTrue(html.contains("--code-overflow-x:auto"))
        assertTrue(html.contains("--code-overflow-wrap:normal"))
        assertTrue(html.contains("--table-overflow-x:auto"))
        assertTrue(html.contains("--table-layout:auto"))
        assertTrue(html.contains("--table-width:max-content"))
    }

    private fun renderer(horizontalPagination: Boolean): ArticleRenderer {
        return ArticleRenderer(
            template = """
                <style>
                  :root {
                    --pre-white-space:{{pre_white_space}};
                    --code-overflow-x:{{code_overflow_x}};
                    --code-overflow-wrap:{{code_overflow_wrap}};
                    --table-overflow-x:{{table_overflow_x}};
                    --table-layout:{{table_layout}};
                    --table-width:{{table_width}};
                  }
                </style>
                {{body}}
            """.trimIndent(),
            textSize = preference("text-size", 18),
            fontOption = preference("font", FontOption.SYSTEM_DEFAULT),
            titleFontSize = preference("title-size", 28),
            textAlignment = preference("alignment", TextAlignment.LEFT),
            titleFollowsBodyFont = preference("title-font", false),
            enableHorizontalPagination = preference("horizontal-pagination", horizontalPagination),
        )
    }

    private fun <T> preference(key: String, value: T): InMemoryPreference<T> {
        return InMemoryPreference(
            key = key,
            defaultValue = value,
            store = mutableMapOf(),
        )
    }
}
