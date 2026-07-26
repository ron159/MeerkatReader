package com.capyreader.app.tts

import com.jocmp.capy.Article
import com.jocmp.capy.persistence.ArticleTtsProgressRecord
import com.jocmp.capy.persistence.ArticleTtsProgressRecords
import com.jocmp.capy.persistence.ArticleTtsSource
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URL
import java.time.ZonedDateTime
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleTtsControllerTest {
    private val records = mockk<ArticleTtsProgressRecords>(relaxed = true)

    @Test
    fun `play prepares article and advances when sentence completes`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = FakeArticleTtsEngine()
        val controller = controller(engine, dispatcher)
        coEvery {
            records.find("article-1", ArticleTtsSource.ORIGINAL)
        } returns null

        controller.play(article(), Locale.ENGLISH)
        advanceUntilIdle()

        assertEquals(ArticleTtsStatus.PLAYING, controller.state.value.status)
        assertEquals(3, controller.state.value.sentenceCount)
        assertEquals("Example article.", engine.spoken.single().first)

        engine.completeCurrent()
        advanceUntilIdle()

        assertEquals(1, controller.state.value.sentenceIndex)
        assertEquals(2, engine.spoken.size)
        assertEquals("First sentence.", engine.spoken.last().first)
        controller.close()
    }

    @Test
    fun `pause and resume repeat the current sentence`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = FakeArticleTtsEngine()
        val controller = controller(engine, dispatcher)
        coEvery { records.find(any(), any()) } returns null
        controller.play(article(), Locale.ENGLISH)
        advanceUntilIdle()

        controller.pause()
        controller.resume()
        advanceUntilIdle()

        assertEquals(ArticleTtsStatus.PLAYING, controller.state.value.status)
        assertEquals(2, engine.spoken.size)
        assertEquals(engine.spoken.first().first, engine.spoken.last().first)
        assertTrue(engine.stopCount > 0)
        controller.close()
    }

    @Test
    fun `play resumes saved original sentence`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = FakeArticleTtsEngine()
        val controller = controller(engine, dispatcher)
        coEvery {
            records.find("article-1", ArticleTtsSource.ORIGINAL)
        } returns ArticleTtsProgressRecord(
            articleID = "article-1",
            source = ArticleTtsSource.ORIGINAL,
            contentKey = ArticleTtsContent.original(article()).contentKey,
            sentenceIndex = 2,
            updatedAt = 0,
        )

        controller.play(article(), Locale.ENGLISH)
        advanceUntilIdle()

        assertEquals(2, controller.state.value.sentenceIndex)
        assertEquals("Second sentence.", engine.spoken.single().first)
        controller.close()
    }

    @Test
    fun `switching source resumes its independent progress`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = FakeArticleTtsEngine()
        val controller = controller(engine, dispatcher)
        val translation = ArticleTtsContent.generated(
            article = article(),
            source = ArticleTtsSource.TRANSLATION,
            text = "Translated first. Translated second.",
        )
        coEvery {
            records.find("article-1", ArticleTtsSource.TRANSLATION)
        } returns ArticleTtsProgressRecord(
            articleID = "article-1",
            source = ArticleTtsSource.TRANSLATION,
            contentKey = translation.contentKey,
            sentenceIndex = 1,
            updatedAt = 0,
        )

        controller.play(translation, ArticleTtsConfiguration(languageTag = "en"))
        advanceUntilIdle()

        assertEquals(ArticleTtsSource.TRANSLATION, controller.state.value.source)
        assertEquals(1, controller.state.value.sentenceIndex)
        assertEquals("Translated second.", engine.spoken.single().first)
        controller.close()
    }

    @Test
    fun `replaced generated content resets saved progress`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = FakeArticleTtsEngine()
        val controller = controller(engine, dispatcher)
        val replacement = ArticleTtsContent.generated(
            article = article(),
            source = ArticleTtsSource.AI_SUMMARY,
            text = "New first. New second.",
        )
        coEvery {
            records.find("article-1", ArticleTtsSource.AI_SUMMARY)
        } returns ArticleTtsProgressRecord(
            articleID = "article-1",
            source = ArticleTtsSource.AI_SUMMARY,
            contentKey = "old-content",
            sentenceIndex = 1,
            updatedAt = 0,
        )

        controller.play(replacement, ArticleTtsConfiguration(languageTag = "en"))
        advanceUntilIdle()

        assertEquals(0, controller.state.value.sentenceIndex)
        assertEquals("New first.", engine.spoken.single().first)
        controller.close()
    }

    @Test
    fun `unavailable engine exposes explicit state`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = FakeArticleTtsEngine(
            initialization = Result.failure(
                ArticleTtsUnavailableException("Missing voice data")
            )
        )
        val controller = controller(engine, dispatcher)

        controller.play(article(), Locale.ENGLISH)
        advanceUntilIdle()

        assertEquals(ArticleTtsStatus.UNAVAILABLE, controller.state.value.status)
        assertEquals("Missing voice data", controller.state.value.errorMessage)
        assertTrue(engine.spoken.isEmpty())
        controller.close()
    }

    @Test
    fun `dismiss ignores callbacks from the stopped utterance`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = FakeArticleTtsEngine()
        val controller = controller(engine, dispatcher)
        coEvery { records.find(any(), any()) } returns null
        controller.play(article(), Locale.ENGLISH)
        advanceUntilIdle()
        val stoppedUtteranceID = engine.currentUtteranceID

        controller.dismiss()
        engine.complete(stoppedUtteranceID)
        engine.fail(stoppedUtteranceID, "late engine failure")
        advanceUntilIdle()

        assertEquals(ArticleTtsState(), controller.state.value)
        assertEquals(1, engine.spoken.size)
        controller.close()
    }

    @Test
    fun `switching articles ignores completion from the previous utterance`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = FakeArticleTtsEngine()
        val controller = controller(engine, dispatcher)
        coEvery { records.find(any(), any()) } returns null
        controller.play(article(), Locale.ENGLISH)
        advanceUntilIdle()
        val previousUtteranceID = engine.currentUtteranceID

        val nextArticle = article().copy(
            id = "article-2",
            title = "Next article",
            contentHTML = "<p>Next first. Next second.</p>",
        )
        controller.play(nextArticle, Locale.ENGLISH)
        advanceUntilIdle()
        engine.complete(previousUtteranceID)
        advanceUntilIdle()

        assertEquals("article-2", controller.state.value.articleID)
        assertEquals(0, controller.state.value.sentenceIndex)
        assertEquals(2, engine.spoken.size)
        assertEquals("Next article.", engine.spoken.last().first)
        controller.close()
    }

    @Test
    fun `close detaches listener closes engine and ignores captured callback`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val engine = FakeArticleTtsEngine()
        val controller = controller(engine, dispatcher)
        coEvery { records.find(any(), any()) } returns null
        controller.play(article(), Locale.ENGLISH)
        advanceUntilIdle()
        val lateListener = engine.listener
        val stoppedUtteranceID = engine.currentUtteranceID

        controller.close()
        lateListener?.onDone(stoppedUtteranceID)
        lateListener?.onError(stoppedUtteranceID, "late engine failure")
        advanceUntilIdle()

        assertEquals(ArticleTtsState(), controller.state.value)
        assertEquals(null, engine.listener)
        assertEquals(1, engine.closeCount)
    }

    private fun controller(
        engine: ArticleTtsEngine,
        dispatcher: CoroutineDispatcher,
    ): ArticleTtsController {
        return ArticleTtsController(
            engine = engine,
            progressRecords = records,
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher,
        )
    }

    private fun article() = Article(
        id = "article-1",
        feedID = "feed-1",
        title = "Example article",
        author = null,
        contentHTML = "<p>First sentence. Second sentence.</p>",
        url = URL("https://example.com/article"),
        summary = "",
        imageURL = null,
        updatedAt = ZonedDateTime.now(),
        publishedAt = ZonedDateTime.now(),
        read = false,
        starred = false,
    )
}

private class FakeArticleTtsEngine(
    private val initialization: Result<ArticleTtsCapabilities> = Result.success(
        ArticleTtsCapabilities(voices = emptyList(), selectedVoiceID = null)
    ),
) : ArticleTtsEngine {
    override var listener: ArticleTtsEngine.Listener? = null
    val spoken = mutableListOf<Pair<String, String>>()
    var stopCount = 0
        private set
    var closeCount = 0
        private set
    val currentUtteranceID: String
        get() = spoken.last().second

    override suspend fun initialize(
        configuration: ArticleTtsConfiguration,
    ): Result<ArticleTtsCapabilities> = initialization

    override fun speak(text: String, utteranceID: String): Result<Unit> {
        spoken += text to utteranceID
        listener?.onStart(utteranceID)
        return Result.success(Unit)
    }

    override fun stop() {
        stopCount += 1
    }

    override fun close() {
        closeCount += 1
    }

    fun completeCurrent() {
        listener?.onDone(spoken.last().second)
    }

    fun complete(utteranceID: String) {
        listener?.onDone(utteranceID)
    }

    fun fail(utteranceID: String, message: String) {
        listener?.onError(utteranceID, message)
    }
}
