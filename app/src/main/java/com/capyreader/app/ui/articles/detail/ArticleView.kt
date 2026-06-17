package com.capyreader.app.ui.articles.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.paging.compose.LazyPagingItems
import com.capyreader.app.R
import com.capyreader.app.ai.ArticleAiAction
import com.capyreader.app.ai.ArticleAiDisplayState
import com.capyreader.app.ai.ArticleAiErrorMessages
import com.capyreader.app.ai.ArticleAiLabels
import com.capyreader.app.ai.ArticleAiRepository
import com.capyreader.app.ai.withAiDisplayContent
import com.capyreader.app.common.AudioEnclosure
import com.capyreader.app.common.Media
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.ArticleVerticalSwipe
import com.capyreader.app.preferences.ArticleVerticalSwipe.DISABLED
import com.capyreader.app.preferences.ArticleVerticalSwipe.LOAD_FULL_CONTENT
import com.capyreader.app.preferences.ArticleVerticalSwipe.NEXT_ARTICLE
import com.capyreader.app.preferences.ArticleVerticalSwipe.OPEN_ARTICLE_IN_BROWSER
import com.capyreader.app.preferences.ArticleVerticalSwipe.PREVIOUS_ARTICLE
import com.capyreader.app.ui.LocalLinkOpener
import com.capyreader.app.ui.articles.LocalFullContent
import com.capyreader.app.ui.collectChangesWithDefault
import com.capyreader.app.ui.components.LocalSnackbarHost
import com.capyreader.app.ui.components.pullrefresh.SwipeRefresh
import com.jocmp.capy.Article
import com.jocmp.capy.persistence.ArticleReadingProgressRecords
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import kotlin.math.abs

@Composable
fun ArticleView(
    article: Article,
    articles: LazyPagingItems<Article>,
    onBackPressed: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleStar: () -> Unit,
    canSaveExternally: Boolean = false,
    onDeletePage: () -> Unit = {},
    onScrollToArticle: (index: Int) -> Unit,
    onSelectArticle: (id: String) -> Unit,
    preloadedArticles: Map<String, Article> = emptyMap(),
    onPreloadAdjacentArticles: (articleIDs: List<String?>) -> Unit = {},
    onSelectMedia: (media: Media) -> Unit,
    onSelectAudio: (audio: AudioEnclosure) -> Unit = {},
    onPauseAudio: () -> Unit = {},
    currentAudioUrl: String? = null,
    isAudioPlaying: Boolean = false,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    appPreferences: AppPreferences = koinInject(),
    articleAiRepository: ArticleAiRepository = koinInject(),
    articleReadingProgressRecords: ArticleReadingProgressRecords = koinInject(),
) {
    val coroutineScope = rememberCoroutineScope()
    val enableHorizontalPager by appPreferences.readerOptions.enableHorizontaPagination.collectChangesWithDefault()
    val fullContent = LocalFullContent.current
    val openLink = articleOpenLink(article)

    val onToggleFullContent = {
        if (article.fullContent == Article.FullContentState.LOADED) {
            fullContent.reset()
        } else if (article.fullContent != Article.FullContentState.LOADING) {
            fullContent.fetch()
        }
    }

    val index = remember(
        article.id,
        articles.itemCount,
    ) {
        articles.itemSnapshotList.indexOfFirst { it?.id == article.id }
    }

    val previousIndex = index - 1
    val nextIndex = index + 1

    val hasPrevious = previousIndex > -1 && articles[index - 1] != null
    val hasNext = nextIndex < articles.itemCount && articles[index + 1] != null

    val previousArticleId = if (hasPrevious) articles[previousIndex]?.id else null
    val nextArticleId = if (hasNext) articles[nextIndex]?.id else null

    LaunchedEffect(previousArticleId, nextArticleId) {
        onPreloadAdjacentArticles(listOf(previousArticleId, nextArticleId))
    }

    fun selectPrevious() {
        if (previousIndex < 0) return

        articles[previousIndex]?.let {
            onSelectArticle(it.id)
        }
    }

    fun selectNext() {
        if (nextIndex >= articles.itemCount) return

        articles[nextIndex]?.let {
            onSelectArticle(it.id)
        }
    }

    val onSwipe = { swipe: ArticleVerticalSwipe ->
        when (swipe) {
            LOAD_FULL_CONTENT -> onToggleFullContent()
            OPEN_ARTICLE_IN_BROWSER -> openLink()
            PREVIOUS_ARTICLE -> selectPrevious()
            NEXT_ARTICLE -> selectNext()
            DISABLED -> {}
        }
    }

    val pinToolbars by appPreferences.readerOptions.pinToolbars.collectChangesWithDefault()
    val (topSwipe, bottomSwipe) = rememberSwipePreferences()
    val enableTopSwipe = topSwipe.enabled &&
            (topSwipe != PREVIOUS_ARTICLE || (topSwipe.openArticle && hasPrevious))

    val enableBottomSwipe = bottomSwipe.enabled &&
            (bottomSwipe != NEXT_ARTICLE || (bottomSwipe.openArticle && hasNext))

    val aiEnabled by appPreferences.aiOptions.enabled.collectChangesWithDefault()
    val aiTranslationMode by appPreferences.aiOptions.translationMode.collectChangesWithDefault()
    val scrollState = rememberArticleScrollState()
    val showToolBar = pinToolbars || !scrollState.isScrollingDown
    var isAiSheetOpen by rememberSaveable(article.id) { mutableStateOf(false) }
    var topAiState by remember(article.id) { mutableStateOf<ArticleAiDisplayState?>(null) }
    var translationAiState by remember(article.id) { mutableStateOf<ArticleAiDisplayState?>(null) }
    var initialScrollPercent by remember(article.id) { mutableStateOf(0.0) }
    var lastSavedScrollPercent by remember(article.id) { mutableStateOf(0.0) }
    val aiLabels = ArticleAiLabels(
        translation = stringResource(R.string.article_ai_label_translation),
        summary = stringResource(R.string.article_ai_label_summary),
        previewSummary = stringResource(R.string.article_ai_label_preview_summary),
        keyPoints = stringResource(R.string.article_ai_label_key_points),
        answer = stringResource(R.string.article_ai_label_answer),
        digest = stringResource(R.string.article_ai_label_digest),
        workingOnIt = stringResource(R.string.article_ai_working_on_it),
    )
    val aiErrorMessages = ArticleAiErrorMessages(
        requestFailed = stringResource(R.string.article_ai_error_request_failed),
        disabled = stringResource(R.string.article_ai_error_disabled),
        disabledForFeed = stringResource(R.string.article_ai_error_disabled_for_feed),
        apiKeyRequired = stringResource(R.string.article_ai_error_api_key_required),
        modelRequired = stringResource(R.string.article_ai_error_model_required),
        contentEmpty = stringResource(R.string.article_ai_error_content_empty),
        questionRequired = stringResource(R.string.article_ai_error_question_required),
        noDigestArticles = stringResource(R.string.article_ai_error_no_digest_articles),
    )

    fun runAiAction(action: ArticleAiAction, forceRefresh: Boolean, question: String? = null) {
        isAiSheetOpen = false

        val loadingState = ArticleAiDisplayState(
            action = action,
            isLoading = true,
        )

        if (action == ArticleAiAction.TRANSLATE) {
            translationAiState = loadingState
        } else {
            topAiState = loadingState
        }

        coroutineScope.launch {
            articleAiRepository.run(action, article, forceRefresh, question)
                .fold(
                    onSuccess = {
                        val resultState = ArticleAiDisplayState(action = action, result = it)
                        if (action == ArticleAiAction.TRANSLATE) {
                            translationAiState = resultState
                        } else {
                            topAiState = resultState
                        }
                    },
                    onFailure = {
                        val errorState = ArticleAiDisplayState(
                            action = action,
                            error = aiErrorMessages.messageFor(it),
                        )
                        if (action == ArticleAiAction.TRANSLATE) {
                            translationAiState = errorState
                        } else {
                            topAiState = errorState
                        }
                    }
                )
        }
    }

    val displayArticle = remember(article, topAiState, translationAiState, aiTranslationMode, aiEnabled, aiLabels) {
        article.withAiDisplayContent(
            topState = topAiState.takeIf { aiEnabled },
            translationState = translationAiState.takeIf { aiEnabled },
            translationMode = aiTranslationMode,
            labels = aiLabels,
        )
    }

    LaunchedEffect(article.id) {
        scrollState.reset()
        val savedScrollPercent = articleReadingProgressRecords.find(article.id)?.scrollPercent ?: 0.0
        initialScrollPercent = savedScrollPercent
        lastSavedScrollPercent = savedScrollPercent
    }

    fun saveReadingProgress(scrollPercent: Double) {
        if (abs(scrollPercent - lastSavedScrollPercent) < PROGRESS_SAVE_DELTA && scrollPercent < NEAR_END_PROGRESS) {
            return
        }

        lastSavedScrollPercent = scrollPercent
        coroutineScope.launch {
            articleReadingProgressRecords.upsert(
                articleID = article.id,
                scrollPercent = scrollPercent,
            )
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    val contentPadding = rememberContentPadding(pinToolbars)

    CompositionLocalProvider(
        LocalSnackbarHost provides snackbarHostState,
    ) {
        BoxWithConstraints(Modifier
            .fillMaxSize()
            .nestedScroll(scrollState.connection)) {
          if (maxWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .padding(contentPadding)
                    .fillMaxSize()
            ) {
                ArticlePullRefresh(
                    onSwipe = onSwipe,
                    pinToolbars = pinToolbars,
                    topSwipe = topSwipe,
                    bottomSwipe = bottomSwipe,
                    enableTopSwipe = enableTopSwipe,
                    enableBottomSwipe = enableBottomSwipe,
                ) {
                    HorizontalReaderPager(
                        enabled = enableHorizontalPager,
                        enablePrevious = hasPrevious,
                        enableNext = hasNext,
                        onSelectPrevious = { selectPrevious() },
                        onSelectNext = { selectNext() },
                    ) {
                        ArticleReaderPool(
                            article = displayArticle,
                            pinToolbars = pinToolbars,
                            onSelectMedia = onSelectMedia,
                            onSelectAudio = onSelectAudio,
                            onPauseAudio = onPauseAudio,
                            currentAudioUrl = currentAudioUrl,
                            isAudioPlaying = isAudioPlaying,
                            onScrollChanged = scrollState::updateFromScroll,
                            onScrollProgressChanged = ::saveReadingProgress,
                            initialScrollPercent = initialScrollPercent,
                            enableTopSwipe = enableTopSwipe,
                            enableBottomSwipe = enableBottomSwipe,
                            onSwipeDownFromTop = { onSwipe(topSwipe) },
                            onSwipeUpFromBottom = { onSwipe(bottomSwipe) },
                        )
                    }
                }
            }

            ArticleTopBar(
                show = showToolBar,
                isScrolled = scrollState.showTopDivider,
                articleId = article.id,
                canDeletePage = article.isReadLater,
                canSaveExternally = canSaveExternally,
                onDeletePage = onDeletePage,
                isFullscreen = isFullscreen,
                onToggleFullscreen = onToggleFullscreen,
                onClose = onBackPressed,
            )

            ArticleBottomBar(
                show = showToolBar,
                article = article,
                hasNextArticle = hasNext,
                onToggleExtractContent = onToggleFullContent,
                onToggleRead = onToggleRead,
                onToggleStar = onToggleStar,
                onSelectNext = { selectNext() },
                showAiAction = aiEnabled,
                isAiLoading = topAiState?.isLoading == true || translationAiState?.isLoading == true,
                onOpenAi = { isAiSheetOpen = true },
            )

            if (isAiSheetOpen) {
                ArticleAiSheet(
                    topState = topAiState,
                    translationState = translationAiState,
                    onRunAction = ::runAiAction,
                    onDismiss = { isAiSheetOpen = false },
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
          }
        }
    }

    LaunchedEffect(index) {
        if (index > -1) {
            onScrollToArticle(index)
        }
    }

}

@Composable
private fun ArticleReaderPool(
    article: Article,
    pinToolbars: Boolean,
    onSelectMedia: (media: Media) -> Unit,
    onSelectAudio: (audio: AudioEnclosure) -> Unit = {},
    onPauseAudio: () -> Unit = {},
    currentAudioUrl: String? = null,
    isAudioPlaying: Boolean = false,
    onScrollChanged: (scrollY: Int, oldScrollY: Int) -> Unit = { _, _ -> },
    onScrollProgressChanged: (scrollPercent: Double) -> Unit = {},
    initialScrollPercent: Double = 0.0,
    enableTopSwipe: Boolean = false,
    enableBottomSwipe: Boolean = false,
    onSwipeDownFromTop: () -> Unit = {},
    onSwipeUpFromBottom: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        key(article.id) {
            ArticleReader(
                article = article,
                pinToolbars = pinToolbars,
                modifier = Modifier.fillMaxSize(),
                onSelectMedia = onSelectMedia,
                onSelectAudio = onSelectAudio,
                onPauseAudio = onPauseAudio,
                currentAudioUrl = currentAudioUrl,
                isAudioPlaying = isAudioPlaying,
                onScrollChanged = onScrollChanged,
                onScrollProgressChanged = onScrollProgressChanged,
                initialScrollPercent = initialScrollPercent,
                enableTopSwipe = enableTopSwipe,
                enableBottomSwipe = enableBottomSwipe,
                onSwipeDownFromTop = onSwipeDownFromTop,
                onSwipeUpFromBottom = onSwipeUpFromBottom,
            )
        }
    }
}

@Composable
fun ArticlePullRefresh(
    pinToolbars: Boolean,
    topSwipe: ArticleVerticalSwipe,
    bottomSwipe: ArticleVerticalSwipe,
    enableTopSwipe: Boolean,
    enableBottomSwipe: Boolean,
    onSwipe: (swipe: ArticleVerticalSwipe) -> Unit,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    val triggerThreshold = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    SwipeRefresh(
        onRefresh = { onSwipe(topSwipe) },
        swipeEnabled = enableTopSwipe,
        indicatorPadding = if (!pinToolbars) PaddingValues(top = 100.dp) else PaddingValues(),
        icon = swipeIcon(
            topSwipe,
            relatedArticleIcon = Icons.Rounded.KeyboardArrowUp
        ),
        onTriggerThreshold = { triggerThreshold() }
    ) {
        SwipeRefresh(
            onRefresh = { onSwipe(bottomSwipe) },
            swipeEnabled = enableBottomSwipe,
            icon = swipeIcon(
                bottomSwipe,
                relatedArticleIcon = Icons.Rounded.KeyboardArrowDown
            ),
            onTriggerThreshold = { triggerThreshold() },
            indicatorAlignment = Alignment.BottomCenter,
        ) {
            content()
        }
    }
}

fun swipeIcon(
    swipe: ArticleVerticalSwipe,
    relatedArticleIcon: ImageVector
): ImageVector {
    return when (swipe) {
        LOAD_FULL_CONTENT -> Icons.AutoMirrored.Rounded.Article
        OPEN_ARTICLE_IN_BROWSER -> Icons.AutoMirrored.Rounded.OpenInNew
        else -> relatedArticleIcon
    }
}

@Composable
fun articleOpenLink(
    article: Article,
): () -> Unit {
    val linkOpener = LocalLinkOpener.current

    fun open() {
        val link = article.url?.toString() ?: return

        linkOpener.open(link.toUri())
    }

    return ::open
}

@Composable
private fun rememberSwipePreferences(appPreferences: AppPreferences = koinInject()): SwipePreferences {
    val topSwipe by appPreferences.readerOptions.topSwipeGesture.collectChangesWithDefault()
    val bottomSwipe by appPreferences.readerOptions.bottomSwipeGesture.collectChangesWithDefault()

    return SwipePreferences(topSwipe, bottomSwipe)
}

@Stable
private data class SwipePreferences(
    val topSwipe: ArticleVerticalSwipe,
    val bottomSwipe: ArticleVerticalSwipe,
)

@Composable
private fun rememberContentPadding(pinToolbars: Boolean): PaddingValues {
    return if (pinToolbars) {
        PaddingValues(
            top = ArticleBarDefaults.topBarOffset,
            bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
        )
    } else {
        PaddingValues()
    }
}

private const val PROGRESS_SAVE_DELTA = 0.05
private const val NEAR_END_PROGRESS = 0.98
