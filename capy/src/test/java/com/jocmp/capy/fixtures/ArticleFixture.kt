package com.jocmp.capy.fixtures

import com.jocmp.capy.Article
import com.jocmp.capy.Feed
import com.jocmp.capy.InMemoryDatabaseProvider
import com.jocmp.capy.RandomUUID
import com.jocmp.capy.common.TimeHelpers
import com.jocmp.capy.db.Database
import com.jocmp.capy.persistence.articleMapper

class ArticleFixture(private val database: Database = InMemoryDatabaseProvider()) {
    private val feedFixture = FeedFixture(database)

    fun create(
        id: String = RandomUUID.generate(),
        title: String = "Test Title",
        summary: String = "Test article here",
        feed: Feed = feedFixture.create(feedURL = "https://example.com/${RandomUUID.generate()}"),
        author: String? = "John Writer",
        imageURL: String? = null,
        enclosureType: String? = null,
        read: Boolean = true,
        starred: Boolean = false,
        url: String = "https://example.com/test-article",
        publishedAt: Long = TimeHelpers.nowUTC().toEpochSecond(),
    ): Article {
        database.transaction {
            database.articlesQueries.create(
                id = id,
                feed_id = feed.id,
                title = title,
                author = author,
                content_html = "<div>Test</div>",
                extracted_content_url = null,
                image_url = imageURL,
                published_at = publishedAt,
                summary = summary,
                url = url,
                enclosure_type = enclosureType
            )
            database.articlesQueries.createStatus(
                article_id = id,
                updated_at = publishedAt,
                read = read,
            )
            if (starred) {
                database.articlesQueries.markStarred(
                    articleID = id,
                    starred = true,
                    lastUnstarredAt = null,
                )
            }
        }

        return database.articlesQueries.findBy(
            articleID = id,
            mapper = ::articleMapper
        ).executeAsOne()
    }
}
