package com.capyreader.app.ui.articles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.capyreader.app.R
import com.capyreader.app.ai.ArticleAiErrorMessages
import com.capyreader.app.common.Media
import com.capyreader.app.common.Saver
import com.capyreader.app.common.asState
import com.capyreader.app.preferences.AfterReadAllBehavior
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.ArticleListVerticalSwipe
import com.capyreader.app.preferences.ArticleStatusListDisplay
import com.capyreader.app.ui.LocalBadgeStyle
import com.capyreader.app.ui.LocalConnectivity
import com.capyreader.app.ui.LocalLinkOpener
import com.capyreader.app.ui.LocalMarkAllReadButtonPosition
import com.capyreader.app.ui.LocalTimeFormats
import com.capyreader.app.ui.LocalUnreadCount
import com.capyreader.app.ui.articles.audio.AudioPlayerController
import com.capyreader.app.ui.articles.audio.FloatingAudioPlayer
import com.capyreader.app.ui.articles.detail.ArticleView
import com.capyreader.app.ui.articles.detail.CapyPlaceholder
import com.capyreader.app.ui.articles.feeds.AngleRefreshState
import com.capyreader.app.ui.articles.feeds.FeedActions
import com.capyreader.app.ui.articles.feeds.FeedList
import com.capyreader.app.ui.articles.feeds.FolderActions
import com.capyreader.app.ui.articles.feeds.LocalFeedActions
import com.capyreader.app.ui.articles.feeds.LocalFolderActions
import com.capyreader.app.ui.articles.feeds.LocalSavedSearchActions
import com.capyreader.app.ui.articles.feeds.SavedSearchActions
import com.capyreader.app.ui.articles.list.ArticleHomeDestination
import com.capyreader.app.ui.articles.list.ArticleHomeNavigationBar
import com.capyreader.app.ui.articles.list.ArticleAiPreviewButton
import com.capyreader.app.ui.articles.list.ArticleListTopBar
import com.capyreader.app.ui.articles.list.EmptyOnboardingView
import com.capyreader.app.ui.articles.list.LabelBottomSheet
import com.capyreader.app.ui.articles.list.LocalMarkAllRead
import com.capyreader.app.ui.articles.list.MarkAllReadButton
import com.capyreader.app.ui.articles.list.MarkAllReadDialog
import com.capyreader.app.ui.articles.list.SwipeUpActionBox
import com.capyreader.app.ui.articles.list.resetScrollBehaviorListener
import com.capyreader.app.ui.articles.media.ArticleMediaView
import com.capyreader.app.ui.collectChangesWithCurrent
import com.capyreader.app.ui.collectChangesWithDefault
import com.capyreader.app.ui.components.ArticleSearch
import com.capyreader.app.ui.components.LocalSnackbarHost
import com.capyreader.app.ui.components.SearchState
import com.capyreader.app.ui.provideLinkOpener
import com.capyreader.app.ui.rememberDisplayTimeFormats
import com.capyreader.app.ui.rememberLazyListState
import com.capyreader.app.ui.rememberLocalConnectivity
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleFilter
import com.jocmp.capy.ArticleStatus
import com.jocmp.capy.Feed
import com.jocmp.capy.Folder
import com.jocmp.capy.MarkRead
import com.jocmp.capy.SavedSearch
import com.jocmp.capy.common.launchUI
import com.jocmp.capy.logging.CapyLog
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun ArticleScreen(
    viewModel: ArticleScreenViewModel = koinViewModel(),
    appPreferences: AppPreferences = koinInject(),
    pendingArticleID: String? = null,
    onPendingArticleSelected: () -> Unit = {},
    onNavigateToSettings: () -> Unit,
) {
    val currentFeed by viewModel.currentFeed.collectAsStateWithLifecycle(initialValue = null)
    val feeds by viewModel.topLevelFeeds.collectAsStateWithLifecycle(initialValue = emptyList())
    val readLaterFeed by viewModel.readLaterFeed.collectAsStateWithLifecycle(initialValue = null)
    val allFeeds by viewModel.allFeeds.collectAsStateWithLifecycle(initialValue = emptyList())
    val allFolders by viewModel.allFolders.collectAsStateWithLifecycle(initialValue = emptyList())
    val folders by viewModel.folders.collectAsStateWithLifecycle(initialValue = emptyList())
    val savedSearches by viewModel.savedSearches.collectAsStateWithLifecycle(initialValue = emptyList())
    val allSavedSearches by viewModel.allSavedSearches.collectAsStateWithLifecycle(initialValue = emptyList())
    val statusCount by viewModel.statusCount.collectAsStateWithLifecycle(initialValue = 0)
    val todayCount by viewModel.todayCount.collectAsStateWithLifecycle(initialValue = 0)
    val unreadCount by viewModel.unreadCount.collectAsStateWithLifecycle(initialValue = 0L)
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle("")
    val searchState by viewModel.searchState.collectAsStateWithLifecycle(SearchState.INACTIVE)
    val nextFilter by viewModel.nextFilter.collectAsStateWithLifecycle(initialValue = null)
    val swipeBottom by viewModel.listSwipeBottom.collectAsStateWithLifecycle()
    val afterReadAll by viewModel.afterReadAll.collectAsStateWithLifecycle()

    val canSwipeBottom = when (swipeBottom) {
        ArticleListVerticalSwipe.DISABLED -> false
        ArticleListVerticalSwipe.NEXT_FEED -> nextFilter != null
        ArticleListVerticalSwipe.MARK_ALL_READ -> true
    }
    val context = LocalContext.current

    val canSaveExternally by viewModel.canSaveArticleExternally.collectAsStateWithLifecycle()

    val fullContent = rememberFullContent(viewModel)
    val articleActions = rememberArticleActions(viewModel)
    val folderActions = rememberFolderActions(viewModel)
    val feedActions = rememberFeedActions(viewModel)
    val savedSearchActions = rememberSavedSearchActions(viewModel)
    val labelsActions = rememberLabelsActions(viewModel, allSavedSearches)
    val connectivity = rememberLocalConnectivity()
    val showOnboarding by viewModel.showOnboarding.collectAsState(false)
    val markAllReadButtonPosition by appPreferences
        .articleListOptions
        .markReadButtonPosition
        .collectChangesWithCurrent()
    val unreadDisplay by appPreferences
        .articleListOptions
        .unreadDisplay
        .collectChangesWithCurrent()
    val starredDisplay by appPreferences
        .articleListOptions
        .starredDisplay
        .collectChangesWithCurrent()
    val badgeStyle by appPreferences.badgeStyle.collectChangesWithDefault()
    val aiEnabled by appPreferences.aiOptions.enabled.collectChangesWithDefault()
    var selectedHomeDestination by rememberSaveable {
        mutableStateOf(filter.homeDestination())
    }

    val articles = key(filter) {
        viewModel.articles.collectAsLazyPagingItems()
    }

    val onMarkAllRead = { range: MarkRead ->
        viewModel.markAllRead(
            onArticlesCleared = {
                selectedHomeDestination = ArticleHomeDestination.FEEDS
                viewModel.selectArticleFilter(ArticleStatus.ALL)
            },
            range = range,
        )
    }

    val article = viewModel.article

    val search = ArticleSearch(
        query = searchQuery,
        start = { viewModel.startSearch() },
        clear = { viewModel.clearSearch() },
        update = viewModel::updateSearch,
        state = searchState,
    )

    val snackbarHostState = remember { SnackbarHostState() }

    val confirmMarkAllReadEnabled by appPreferences.articleListOptions.confirmMarkAllRead.asState()
    var isMarkAllReadDialogOpen by remember { mutableStateOf(false) }

    CompositionLocalProvider(
        LocalFullContent provides fullContent,
        LocalArticleActions provides articleActions,
        LocalFolderActions provides folderActions,
        LocalFeedActions provides feedActions,
        LocalSavedSearchActions provides savedSearchActions,
        LocalLabelsActions provides labelsActions,
        LocalConnectivity provides connectivity,
        LocalLinkOpener provides provideLinkOpener(context),
        LocalMarkAllReadButtonPosition provides markAllReadButtonPosition,
        LocalBadgeStyle provides badgeStyle,
        LocalUnreadCount provides unreadCount,
        LocalSnackbarHost provides snackbarHostState,
        LocalTimeFormats provides rememberDisplayTimeFormats(),
    ) {
        val openNextFeedOnReadAll = afterReadAll == AfterReadAllBehavior.OPEN_NEXT_FEED

        val refreshAllState by viewModel.refreshAllState.collectAsStateWithLifecycle(
            initialValue = AngleRefreshState.STOPPED
        )
        val isRefreshInitialized = viewModel.refreshInitialized

        val (isUpdatePasswordDialogOpen, setUpdatePasswordDialogOpen) = rememberSaveable {
            mutableStateOf(false)
        }
        val coroutineScope = rememberCoroutineScope()
        val scaffoldNavigator = rememberListDetailPaneScaffoldNavigator()
        val showMultipleColumns = scaffoldNavigator.scaffoldDirective.maxHorizontalPartitions > 1
        val paneExpansion = rememberArticlePaneExpansion()
        val isPullToRefreshing = viewModel.isPullToRefreshing
        val addFeedSuccessMessage = stringResource(R.string.add_feed_success)
        val scrollBehavior = pinnedScrollBehavior()
        var media by rememberSaveable(saver = Media.Saver) { mutableStateOf(null) }
        val audioController: AudioPlayerController = koinInject()
        val audioEnclosure by audioController.currentAudio.collectAsState()
        val focusManager = LocalFocusManager.current
        val openUpdatePasswordDialog = {
            viewModel.dismissUnauthorizedMessage()
            setUpdatePasswordDialogOpen(true)
        }
        suspend fun navigateToDetail() {
            scaffoldNavigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
            if (showMultipleColumns) {
                paneExpansion.restore()
            }
        }

        val listState = articles.rememberLazyListState()

        fun visibleArticles(): List<Article> {
            return listState.layoutInfo.visibleItemsInfo
                .mapNotNull { itemInfo ->
                    if (itemInfo.index < articles.itemCount) {
                        articles[itemInfo.index]
                    } else {
                        null
                    }
                }
                .distinctBy { it.id }
        }

        fun summarizeVisibleArticlePreviews() {
            viewModel.summarizeArticlePreviews(visibleArticles())
        }

        LaunchedEffect(listState, articles) {
            snapshotFlow { visibleArticles().map { it.id } }
                .distinctUntilChanged()
                .debounce(250)
                .collect {
                    viewModel.loadOfflinePackageStates(visibleArticles())
                }
        }

        fun generateArticleDigest() {
            viewModel.generateArticleDigest()
        }

        fun scrollToArticle(index: Int) {
            coroutineScope.launch {
                if (index > -1) {
                    val visibleItemsInfo = listState.layoutInfo.visibleItemsInfo
                    val isItemVisible = visibleItemsInfo.any { it.index == index }

                    if (!isItemVisible) {
                        listState.animateScrollToItem(index)
                    }
                }
            }
        }

        val resetScrollBehaviorOffset = resetScrollBehaviorListener(
            listState = listState,
            scrollBehavior = scrollBehavior
        )

        val scrollToTop = {
            coroutineScope.launch {
                listState.scrollToItem(0)
                resetScrollBehaviorOffset()
            }
        }

        val (scrolledFilter, setScrolledFilter) = rememberSaveable(
            saver = ArticleFilter.Saver
        ) { mutableStateOf(null) }

        LaunchedEffect(viewModel.refreshSkipReason) {
            val reason = viewModel.refreshSkipReason ?: return@LaunchedEffect
            val message = when (reason) {
                RefreshSkipReason.Mobile -> context.getString(R.string.refresh_skipped_mobile)
            }
            snackbarHostState.showSnackbar(
                message = message,
                withDismissAction = true,
                duration = SnackbarDuration.Short,
            )
            viewModel.clearRefreshSkipReason()
        }

        LaunchedEffect(filter, articles.loadState.refresh) {
            val refreshComplete = articles.loadState.refresh is LoadState.NotLoading
            if (refreshComplete && filter != scrolledFilter) {
                listState.scrollToItem(0)
                resetScrollBehaviorOffset()
                setScrolledFilter(filter)
            }
        }

        MarkReadOnScroll(
            listState = listState,
            articles = articles,
            scrollHighWaterMark = viewModel.scrollHighWaterMark,
            updateScrollHighWaterMark = viewModel::updateScrollHighWaterMark,
            markReadOnScroll = viewModel::markReadOnScroll,
            resetScrollBehaviorOffset = resetScrollBehaviorOffset,
        )

        suspend fun openNextStatus(action: suspend () -> Unit) {
            action()
            scaffoldNavigator.navigateTo(ListDetailPaneScaffoldRole.List)
        }

        fun markAllRead(range: MarkRead) {
            if (range == MarkRead.All && confirmMarkAllReadEnabled) {
                isMarkAllReadDialogOpen = true
                return
            }

            val animateMarkRead = openNextFeedOnReadAll &&
                    nextFilter != null &&
                    canOpenNextFeed(filter, range)

            if (animateMarkRead) {
                coroutineScope.launchUI {
                    openNextStatus {
                        onMarkAllRead(range)
                    }
                }
            } else {
                onMarkAllRead(range)
            }
        }

        fun onSwipeUp() {
            when (swipeBottom) {
                ArticleListVerticalSwipe.NEXT_FEED -> {
                    coroutineScope.launchUI {
                        openNextStatus {
                            viewModel.requestNextFeed()
                        }
                    }
                }

                ArticleListVerticalSwipe.MARK_ALL_READ -> {
                    markAllRead(MarkRead.All)
                }

                ArticleListVerticalSwipe.DISABLED -> {}
            }
        }

        fun refreshAll() {
            viewModel.refreshAll()
        }

        fun refreshFeeds() {
            viewModel.refresh(filter)
        }

        fun openNextList(action: suspend () -> Unit) {
            coroutineScope.launchUI {
                openNextStatus(action)
            }
        }

        fun clearArticle() {
            viewModel.clearArticle()

            coroutineScope.launchUI {
                scaffoldNavigator.navigateTo(ListDetailPaneScaffoldRole.List)
            }
        }

        fun dismissArticle() {
            viewModel.clearArticle()
        }

        val showSnackbar = { message: String ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message,
                    withDismissAction = true,
                    duration = SnackbarDuration.Short
                )
            }
        }

        val onFeedAdded = { feedID: String ->
            coroutineScope.launch {
                openNextList { viewModel.selectFeed(feedID) }

                showSnackbar(addFeedSuccessMessage)
            }
        }

        fun setArticle(articleID: String, onComplete: (article: Article) -> Unit = {}) {
            viewModel.selectArticle(articleID, onComplete)
        }

        val linkOpener = LocalLinkOpener.current

        fun selectArticle(articleID: String) {
            setArticle(articleID) { nextArticle ->
                if (search.isActive) {
                    focusManager.clearFocus()
                }

                val url = nextArticle.url
                if (nextArticle.openInBrowser && url != null) {
                    dismissArticle()
                    linkOpener.open(url.toString().toUri())
                } else {
                    coroutineScope.launch {
                        navigateToDetail()
                    }
                }
            }
        }

        val selectFilter = {
            if (!filter.hasArticlesSelected()) {
                openNextList { viewModel.selectArticleFilter() }
            }
        }

        val selectStatus = { status: ArticleStatus -> viewModel.selectStatus(status) }

        val selectFeed = { feed: Feed, folderTitle: String? ->
            if (!filter.isFeedSelected(feed)) {
                openNextList { viewModel.selectFeed(feed.id, folderTitle) }
            }
        }

        val selectFolder = { folder: Folder ->
            if (!filter.isFolderSelected(folder)) {
                openNextList { viewModel.selectFolder(folder.title) }
            }
        }

        val selectSavedSearch = { savedSearch: SavedSearch ->
            if (!filter.isSavedSearchSelected(savedSearch)) {
                openNextList { viewModel.selectSavedSearch(savedSearch.id) }
            }
        }

        val selectToday = {
            if (!filter.hasTodaySelected()) {
                openNextList { viewModel.selectToday() }
            }
        }

        val feedNavigationContent: @Composable () -> Unit = {
            FeedList(
                source = viewModel.source,
                folders = folders,
                feeds = feeds,
                readLaterFeed = readLaterFeed,
                onSelectFolder = selectFolder,
                onSelectFeed = selectFeed,
                onMarkAllRead = { viewModel.markAllRead(filter = it) },
                onFeedAdded = { onFeedAdded(it) },
                savedSearches = savedSearches,
                onSelectSavedSearch = selectSavedSearch,
                onNavigateToSettings = onNavigateToSettings,
                onFilterSelect = selectFilter,
                onSelectToday = { selectToday() },
                onSelectStatus = { selectStatus(it) },
                refreshState = refreshAllState,
                onRefresh = {
                    refreshAll()
                },
                filter = filter,
                statusCount = statusCount,
                todayCount = todayCount,
                showHeader = false,
                showArticleShortcuts = false,
                showStatusBar = false,
            )
        }

        fun selectHomeDestination(destination: ArticleHomeDestination, status: ArticleStatus) {
            selectedHomeDestination = destination
            viewModel.selectArticleFilter(status)
        }

        LaunchedEffect(filter) {
            selectedHomeDestination = filter.homeDestination()
        }

        val isHomeFilter = filter is ArticleFilter.Articles && when (selectedHomeDestination) {
            ArticleHomeDestination.FEEDS -> filter.status == ArticleStatus.ALL
            ArticleHomeDestination.UNREAD -> filter.status == ArticleStatus.UNREAD
            ArticleHomeDestination.STARRED -> filter.status == ArticleStatus.STARRED
        }

        val usesGroupedStatusHome = when (selectedHomeDestination) {
            ArticleHomeDestination.UNREAD -> unreadDisplay == ArticleStatusListDisplay.GROUPED_BY_FEED
            ArticleHomeDestination.STARRED -> starredDisplay == ArticleStatusListDisplay.GROUPED_BY_FEED
            ArticleHomeDestination.FEEDS -> false
        }
        val showGroupedStatusHome = isHomeFilter && usesGroupedStatusHome

        val showFeedNavigation = selectedHomeDestination == ArticleHomeDestination.FEEDS && isHomeFilter ||
            showGroupedStatusHome

        LaunchedEffect(pendingArticleID) {
            val id = pendingArticleID ?: return@LaunchedEffect
            onPendingArticleSelected()
            selectArticle(id)
        }

        ArticleScaffold(
            scaffoldNavigator = scaffoldNavigator,
            paneExpansion = paneExpansion,
            listPane = {
                val keyboardManager = LocalSoftwareKeyboardController.current
                val markReadPosition = LocalMarkAllReadButtonPosition.current
                val showAiSummaryPreviewButton = aiEnabled &&
                        !showFeedNavigation &&
                        (articles.itemCount > 0 || viewModel.isAiSummaryPreviewLoading)
                val canSummarizeArticlePreviews = showAiSummaryPreviewButton &&
                        articles.itemCount > 0 &&
                        !viewModel.isAiSummaryPreviewLoading
                val showAiDigestButton = aiEnabled &&
                        !showFeedNavigation &&
                        (articles.itemCount > 0 || viewModel.aiDigestState.isLoading)
                val canGenerateArticleDigest = showAiDigestButton &&
                        articles.itemCount > 0 &&
                        !viewModel.aiDigestState.isLoading

                CompositionLocalProvider(
                    LocalMarkAllRead provides { markAllRead(MarkRead.All) },
                ) {
                    Scaffold(
                        modifier = Modifier
                            .nestedScroll(scrollBehavior.nestedScrollConnection)
                            .nestedScroll(object : NestedScrollConnection {
                                override fun onPostScroll(
                                    consumed: Offset,
                                    available: Offset,
                                    source: NestedScrollSource
                                ): Offset {
                                    if (search.isActive) {
                                        keyboardManager?.hide()
                                    }

                                    return Offset.Zero
                                }
                            }),
                        topBar = {
                            ArticleListTopBar(
                                onRequestJumpToTop = { scrollToTop() },
                                onNavigateToSettings = onNavigateToSettings,
                                onSummarizeArticlePreviews = { summarizeVisibleArticlePreviews() },
                                onGenerateArticleDigest = { generateArticleDigest() },
                                onMarkSearchResultsRead = {
                                    viewModel.markSearchResultsReadAsync { count ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.search_batch_mark_read_done, count)
                                            )
                                        }
                                    }
                                },
                                onStarSearchResults = {
                                    viewModel.starSearchResultsAsync { count ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.search_batch_star_done, count)
                                            )
                                        }
                                    }
                                },
                                onSaveSearchResultsExternally = {
                                    viewModel.saveSearchResultsExternallyAsync { result ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar(
                                                result.fold(
                                                    onSuccess = {
                                                        context.getString(R.string.search_batch_save_done, it)
                                                    },
                                                    onFailure = {
                                                        context.getString(R.string.search_batch_save_failed)
                                                    },
                                                )
                                            )
                                        }
                                    }
                                },
                                showAiSummaryPreviewButton = showAiSummaryPreviewButton,
                                canSummarizeArticlePreviews = canSummarizeArticlePreviews,
                                isAiSummaryPreviewLoading = viewModel.isAiSummaryPreviewLoading,
                                showAiDigestButton = showAiDigestButton,
                                canGenerateArticleDigest = canGenerateArticleDigest,
                                canSaveSearchResultsExternally = canSaveExternally,
                                onRemoveFolder = { folderTitle, completion ->
                                    viewModel.removeFolder(
                                        folderTitle,
                                        completion
                                    )
                                },
                                scrollBehavior = scrollBehavior,
                                search = search,
                                filter = filter,
                                currentFeed = currentFeed,
                                feeds = allFeeds,
                                savedSearches = savedSearches,
                                folders = allFolders,
                                source = viewModel.source,
                            )
                        },
                        snackbarHost = {
                            SnackbarHost(
                                hostState = snackbarHostState,
                                modifier = Modifier.padding(
                                    bottom = if (article == null) 96.dp else 56.dp,
                                ),
                            )
                        },
                        floatingActionButton = {
                            if (markReadPosition == MarkReadPosition.FLOATING_ACTION_BUTTON) {
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    ArticleAiPreviewButton(
                                        position = MarkReadPosition.FLOATING_ACTION_BUTTON,
                                        visible = showAiSummaryPreviewButton,
                                        enabled = canSummarizeArticlePreviews,
                                        isLoading = viewModel.isAiSummaryPreviewLoading,
                                        onClick = { summarizeVisibleArticlePreviews() },
                                    )
                                    MarkAllReadButton(
                                        position = MarkReadPosition.FLOATING_ACTION_BUTTON,
                                    )
                                }
                            }
                        },
                        bottomBar = {
                            Column {
                                audioEnclosure?.let { audio ->
                                    FloatingAudioPlayer(
                                        audio = audio,
                                        controller = audioController,
                                        onDismiss = {
                                            audioController.dismiss()
                                        },
                                    )
                                }
                                if (article == null) {
                                    ArticleHomeNavigationBar(
                                        selectedDestination = selectedHomeDestination,
                                        unreadCount = unreadCount,
                                        onSelectFeeds = {
                                            selectHomeDestination(
                                                ArticleHomeDestination.FEEDS,
                                                ArticleStatus.ALL
                                            )
                                        },
                                        onSelectUnread = {
                                            selectHomeDestination(
                                                ArticleHomeDestination.UNREAD,
                                                ArticleStatus.UNREAD
                                            )
                                        },
                                        onSelectStarred = {
                                            selectHomeDestination(
                                                ArticleHomeDestination.STARRED,
                                                ArticleStatus.STARRED
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        ArticleListScaffold(
                            padding = innerPadding,
                            showOnboarding = showOnboarding && !showFeedNavigation,
                            onboarding = {
                                EmptyOnboardingView {
                                    AddFeedButton(
                                        onComplete = {
                                            onFeedAdded(it)
                                        },
                                    )
                                }
                            },
                        ) {
                            if (showFeedNavigation) {
                                PullToRefreshBox(
                                    isRefreshing = isPullToRefreshing,
                                    onRefresh = {
                                        refreshFeeds()
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    feedNavigationContent()
                                }
                            } else {
                                PullToRefreshBox(
                                    isRefreshing = isPullToRefreshing,
                                    onRefresh = {
                                        refreshFeeds()
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    SwipeUpActionBox(
                                        modifier = Modifier.fillMaxSize(),
                                        enabled = canSwipeBottom,
                                        onRequestNext = {
                                            onSwipeUp()
                                        },
                                    ) {
                                        if (isRefreshInitialized && articles.itemCount == 0) {
                                            ArticleListEmptyView()
                                        } else {
                                            ArticleList(
                                                articles = articles,
                                                selectedArticleKey = article?.id,
                                                aiSummaryPreviews = viewModel.aiSummaryPreviews,
                                                offlinePackageStates = viewModel.offlinePackageStates,
                                                listState = listState,
                                                enableMarkReadOnScroll = viewModel.markReadOnScrollEnabled,
                                                dimReadArticles = filter.status != ArticleStatus.STARRED,
                                                scrollToTop = { scrollToTop() },
                                                onMarkAllRead = { range ->
                                                    onMarkAllRead(range)
                                                },
                                                onSelect = { articleID ->
                                                    selectArticle(articleID)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            detailPane = {
                if (article == null && showMultipleColumns) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
                        CapyPlaceholder()
                    }
                } else if (article != null) {
                    val isAudioPlaying by audioController.isPlaying.collectAsState()
                    val currentAudio by audioController.currentAudio.collectAsState()

                    ArticleView(
                        article = article,
                        articles = articles,
                        onBackPressed = {
                            clearArticle()
                        },
                        onToggleRead = viewModel::toggleArticleRead,
                        onToggleStar = viewModel::toggleArticleStar,
                        canSaveExternally = canSaveExternally,
                        onDeletePage = {
                            clearArticle()
                            viewModel.deletePage(article.id)
                        },
                        onSelectMedia = { media = it },
                        onSelectAudio = { audio ->
                            audioController.play(audio)
                        },
                        onPauseAudio = {
                            audioController.pause()
                        },
                        onSelectArticle = { articleID ->
                            setArticle(articleID)
                        },
                        preloadedArticles = viewModel.preloadedArticles,
                        onPreloadAdjacentArticles = viewModel::preloadAdjacentArticles,
                        onScrollToArticle = { index ->
                            scrollToArticle(index)
                        },
                        currentAudioUrl = currentAudio?.url,
                        isAudioPlaying = isAudioPlaying,
                        isFullscreen = paneExpansion.isFullscreen,
                        onToggleFullscreen = { paneExpansion.toggleFullscreen() },
                    )
                }
            }
        )

        LaunchedEffect(scaffoldNavigator.currentDestination) {
            val isOnList =
                scaffoldNavigator.currentDestination?.pane != ListDetailPaneScaffoldRole.Detail
            if (isOnList && article != null) {
                clearArticle()
            }
        }

        AnimatedVisibility(
            enter = fadeIn(),
            exit = fadeOut(),
            visible = media != null
        ) {
            ArticleMediaView(
                onDismissRequest = {
                    media = null
                },
                media = media
            )
        }


        if (isMarkAllReadDialogOpen) {
            MarkAllReadDialog(
                onConfirm = {
                    isMarkAllReadDialogOpen = false
                    onMarkAllRead(MarkRead.All)
                },
                onDismissRequest = {
                    isMarkAllReadDialogOpen = false
                },
            )
        }

        if (viewModel.showUnauthorizedMessage) {
            UnauthorizedAlertDialog(
                onConfirm = openUpdatePasswordDialog,
                onDismissRequest = viewModel::dismissUnauthorizedMessage,
            )
        }

        val aiDigestState = viewModel.aiDigestState
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
        if (aiDigestState.isLoading || aiDigestState.result != null || aiDigestState.error != null) {
            AlertDialog(
                onDismissRequest = viewModel::clearArticleDigest,
                title = {
                    Text(stringResource(R.string.article_ai_digest_title))
                },
                text = {
                    Text(
                        text = when {
                            aiDigestState.isLoading -> stringResource(R.string.article_ai_digest_loading)
                            aiDigestState.error != null -> {
                                stringResource(
                                    R.string.article_ai_digest_failed,
                                    aiErrorMessages.messageFor(aiDigestState.error),
                                )
                            }
                            else -> aiDigestState.result.orEmpty()
                        },
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    TextButton(onClick = viewModel::clearArticleDigest) {
                        Text(stringResource(R.string.article_ai_digest_close))
                    }
                },
            )
        }

        if (isUpdatePasswordDialogOpen) {
            UpdateAuthDialog(
                onSuccess = { message ->
                    setUpdatePasswordDialogOpen(false)
                    showSnackbar(message)
                },
                onDismissRequest = {
                    setUpdatePasswordDialogOpen(false)
                }
            )
        }

        labelsActions.selectedArticleID?.let { articleID ->
            LabelBottomSheet(
                articleID = articleID,
                savedSearches = labelsActions.savedSearches,
                articleLabels = labelsActions.articleLabels,
                onAddLabel = { savedSearchID ->
                    labelsActions.addLabel(articleID, savedSearchID)
                },
                onRemoveLabel = { savedSearchID ->
                    labelsActions.removeLabel(articleID, savedSearchID)
                },
                onCreateLabel = labelsActions.createLabel,
                onDismissRequest = labelsActions.closeSheet
            )
        }

        BackHandler(media != null) {
            media = null
        }

        BackHandler(media == null && article != null) {
            paneExpansion.reset()
            clearArticle()
        }

        BackHandler(media == null && search.isActive && article == null) {
            search.clear()
        }

        ArticleListBackHandler(
            filter,
            returnFeedsToStatusHome = usesGroupedStatusHome,
            onRequestFilter = selectFilter,
            onRequestFolder = selectFolder,
            onRequestFeeds = {
                selectHomeDestination(
                    ArticleHomeDestination.FEEDS,
                    ArticleStatus.ALL
                )
            },
            enabled = isFeedActive(media, article, search),
        )

        LayoutNavigationHandler(
            enabled = article == null
        ) {
            scaffoldNavigator.navigateTo(ListDetailPaneScaffoldRole.List)
        }
    }
}

@Composable
fun rememberArticleActions(viewModel: ArticleScreenViewModel): ArticleActions {
    return remember {
        ArticleActions(
            markRead = viewModel::markReadAsync,
            markUnread = viewModel::markUnreadAsync,
            star = viewModel::addStarAsync,
            unstar = viewModel::removeStarAsync,
            saveExternally = viewModel::saveArticleExternallyAsync,
            saveForLater = viewModel::saveForLater,
            muteFeed = viewModel::createMuteFeedRule,
            muteSimilar = viewModel::createMuteSimilarRule,
            notifyAuthor = viewModel::createNotifyAuthorRule,
            downloadOffline = viewModel::downloadOfflineAsync,
            removeOffline = viewModel::removeOfflineAsync,

            showSaveForLater = viewModel.source.supportsReadLater,
        )
    }
}

@Composable
fun rememberFolderActions(viewModel: ArticleScreenViewModel): FolderActions {
    return remember {
        FolderActions(
            updateExpanded = viewModel::expandFolder,
            removeFolder = { folderTitle, completion ->
                viewModel.removeFolder(folderTitle, completion)
            },
        )
    }
}

@Composable
fun rememberFeedActions(viewModel: ArticleScreenViewModel): FeedActions {
    return remember {
        FeedActions(
            updateOpenInBrowser = { feedID, openInBrowser ->
                viewModel.updateOpenInBrowser(feedID, openInBrowser)
            },
            removeFeed = { feedID ->
                viewModel.removeFeed(feedID)
            },
            toggleUnreadBadge = { feedID, show ->
                viewModel.toggleFeedUnreadBadge(feedID, show)
            },
            reloadIcon = { feedID ->
                viewModel.reloadFavicon(feedID)
            }
        )
    }
}

@Composable
fun rememberLabelsActions(
    viewModel: ArticleScreenViewModel,
    savedSearches: List<SavedSearch>,
): LabelsActions {
    var selectedArticleID by remember { mutableStateOf<String?>(null) }

    val articleLabels by viewModel.getArticleLabels(selectedArticleID)
        .collectAsState(initial = emptyList())

    return remember(savedSearches, selectedArticleID, articleLabels) {
        LabelsActions(
            source = viewModel.source,
            showLabels = viewModel.source.supportsLabels,
            savedSearches = savedSearches,
            selectedArticleID = selectedArticleID,
            articleLabels = articleLabels,
            openSheet = { selectedArticleID = it },
            closeSheet = { selectedArticleID = null },
            addLabel = viewModel::addLabelAsync,
            removeLabel = viewModel::removeLabelAsync,
            createLabel = viewModel::createLabel,
        )
    }
}

@Composable
fun rememberSavedSearchActions(viewModel: ArticleScreenViewModel): SavedSearchActions {
    return remember {
        SavedSearchActions(
            toggleUnreadBadge = { id, show ->
                viewModel.toggleSavedSearchUnreadBadge(id, show)
            },
        )
    }
}

@Composable
fun rememberFullContent(viewModel: ArticleScreenViewModel): FullContentFetcher {
    return remember {
        FullContentFetcher(
            fetch = viewModel::fetchFullContentAsync,
            reset = viewModel::resetFullContent,
        )
    }
}

fun canOpenNextFeed(
    filter: ArticleFilter,
    range: MarkRead,
): Boolean {
    return range is MarkRead.All && filter !is ArticleFilter.Articles
}

fun isFeedActive(
    media: Media?,
    article: Article?,
    search: ArticleSearch
): Boolean {
    return media == null &&
            article == null &&
            !search.isActive
}

private fun ArticleFilter.homeDestination(): ArticleHomeDestination {
    return when (status) {
        ArticleStatus.ALL -> ArticleHomeDestination.FEEDS
        ArticleStatus.UNREAD -> ArticleHomeDestination.UNREAD
        ArticleStatus.STARRED -> ArticleHomeDestination.STARRED
    }
}

@OptIn(FlowPreview::class)
@Composable
private fun MarkReadOnScroll(
    listState: LazyListState,
    articles: LazyPagingItems<Article>,
    scrollHighWaterMark: Int,
    updateScrollHighWaterMark: (Int) -> Unit,
    markReadOnScroll: (String) -> Unit,
    resetScrollBehaviorOffset: () -> Unit,
) {
    val appPreferences = koinInject<AppPreferences>()

    val enabled by appPreferences
        .articleListOptions
        .markReadOnScroll
        .collectChangesWithCurrent()

    if (enabled) {
        LaunchedEffect(listState) {
            snapshotFlow { listState.layoutInfo.totalItemsCount }
                .distinctUntilChanged()
                .collect {
                    listState.scrollToItem(0)
                    resetScrollBehaviorOffset()
                }
        }

        LaunchedEffect(listState) {
            snapshotFlow {
                listState.firstVisibleItemIndex - 1
            }
                .distinctUntilChanged()
                .debounce(500)
                .collect { scrolledPastIndex ->
                    CapyLog.debug(
                        "mark_read_on_scroll:collect", mapOf(
                            "scrolledPastIndex" to scrolledPastIndex,
                            "highWaterMark" to scrollHighWaterMark,
                            "itemCount" to articles.itemCount,
                        )
                    )
                    if (scrolledPastIndex > scrollHighWaterMark && scrolledPastIndex < articles.itemCount) {
                        updateScrollHighWaterMark(scrolledPastIndex)
                        val boundaryArticle = articles[scrolledPastIndex]
                        if (boundaryArticle != null) {
                            CapyLog.debug(
                                "mark_read_on_scroll:boundary", mapOf(
                                    "articleID" to boundaryArticle.id,
                                    "index" to scrolledPastIndex,
                                )
                            )
                            markReadOnScroll(boundaryArticle.id)
                        }
                    }
                }
        }
    }
}
