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
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.paging.compose.LazyPagingItems
import com.capyreader.app.common.AudioEnclosure
import com.capyreader.app.common.Media
import com.capyreader.app.ai.ArticleAiAction
import com.capyreader.app.ai.ArticleAiDisplayState
import com.capyreader.app.ai.ArticleAiRepository
import com.capyreader.app.ai.withAiDisplayContent
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
import com.capyreader.app.ui.components.pullrefresh.SwipeRefresh
import com.capyreader.app.ui.components.LocalSnackbarHost
import com.jocmp.capy.Article
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

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
    val aiEnabled by appPreferences.aiOptions.enabled.collectChangesWithDefault()
    val aiTranslationMode by appPreferences.aiOptions.translationMode.collectChangesWithDefault()
    val scrollState = rememberArticleScrollState()
    val showToolBar = pinToolbars || !scrollState.isScrollingDown
    var isAiSheetOpen by rememberSaveable(article.id) { mutableStateOf(false) }
    var topAiState by remember(article.id) { mutableStateOf<ArticleAiDisplayState?>(null) }
    var translationAiState by remember(article.id) { mutableStateOf<ArticleAiDisplayState?>(null) }

    fun runAiAction(action: ArticleAiAction, forceRefresh: Boolean) {
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
            articleAiRepository.run(action, article, forceRefresh)
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
                            error = it.message ?: it::class.simpleName ?: "AI request failed",
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

    val displayArticle = remember(article, topAiState, translationAiState, aiTranslationMode, aiEnabled) {
        article.withAiDisplayContent(
            topState = topAiState.takeIf { aiEnabled },
            translationState = translationAiState.takeIf { aiEnabled },
            translationMode = aiTranslationMode,
        )
    }

    LaunchedEffect(article.id) {
        scrollState.reset()
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
                    hasPreviousArticle = hasPrevious,
                    pinToolbars = pinToolbars,
                    hasNextArticle = hasNext,
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
            )
        }
    }
}

@Composable
fun ArticlePullRefresh(
    hasNextArticle: Boolean,
    hasPreviousArticle: Boolean,
    pinToolbars: Boolean,
    onSwipe: (swipe: ArticleVerticalSwipe) -> Unit,
    content: @Composable () -> Unit,
) {
    val (topSwipe, bottomSwipe) = rememberSwipePreferences()
    val haptics = LocalHapticFeedback.current

    val triggerThreshold = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val enableTopSwipe = topSwipe.enabled &&
            (topSwipe != PREVIOUS_ARTICLE || (topSwipe.openArticle && hasPreviousArticle))

    val enableBottomSwipe = bottomSwipe.enabled &&
            (bottomSwipe != NEXT_ARTICLE || (bottomSwipe.openArticle && hasNextArticle))

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
