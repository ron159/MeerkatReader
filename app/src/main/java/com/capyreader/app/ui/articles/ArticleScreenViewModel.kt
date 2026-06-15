package com.capyreader.app.ui.articles

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import com.capyreader.app.R
import com.capyreader.app.articleimages.ArticleImageCacheCleaner
import com.capyreader.app.articleimages.ArticleImageDownloader
import com.capyreader.app.articleimages.ArticleImagePreloader
import com.capyreader.app.articleimages.ArticleImageStore
import com.capyreader.app.common.isOnWifi
import com.capyreader.app.common.toast
import com.capyreader.app.notifications.NotificationHelper
import com.capyreader.app.preferences.AfterReadAllBehavior
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.ArticleListVerticalSwipe
import com.capyreader.app.refresher.RefreshInterval
import com.capyreader.app.ui.articles.feeds.AngleRefreshState
import com.capyreader.app.ui.components.SearchState
import com.capyreader.app.ui.widget.WidgetUpdater
import com.jocmp.capy.Account
import com.jocmp.capy.Article
import com.jocmp.capy.ArticleFilter
import com.jocmp.capy.ArticleStatus
import com.jocmp.capy.ArticleStatus.UNREAD
import com.jocmp.capy.Feed
import com.jocmp.capy.Folder
import com.jocmp.capy.MarkRead
import com.jocmp.capy.SavedSearch
import com.jocmp.capy.articles.ArticleContent
import com.jocmp.capy.articles.CachedArticleImage
import com.jocmp.capy.articles.SidebarItem
import com.jocmp.capy.common.UnauthorizedError
import com.jocmp.capy.common.launchIO
import com.jocmp.capy.common.launchUI
import com.jocmp.capy.common.withIOContext
import com.jocmp.capy.common.withUIContext
import com.jocmp.capy.countToday
import com.jocmp.capy.logging.CapyLog
import com.jocmp.capy.persistence.ArticleFullContentRecords
import com.jocmp.capy.persistence.ArticleImageRecords
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

@OptIn(ExperimentalCoroutinesApi::class)
class ArticleScreenViewModel(
    private val account: Account,
    private val appPreferences: AppPreferences,
    private val application: Application,
    private val notificationHelper: NotificationHelper,
    private val articleImageRecords: ArticleImageRecords,
    private val articleImagePreloader: ArticleImagePreloader,
    private val articleImageDownloader: ArticleImageDownloader,
    private val articleImageStore: ArticleImageStore,
    private val articleImageCacheCleaner: ArticleImageCacheCleaner,
    private val articleFullContentRecords: ArticleFullContentRecords,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val syncFlushInterval: Duration? = SYNC_FLUSH_INTERVAL,
) : AndroidViewModel(application) {
    private var refreshJob: Job? = null

    private var fullContentJob: Job? = null

    val preloadedArticles = mutableStateMapOf<String, Article>()

    var refreshSkipReason by mutableStateOf<RefreshSkipReason?>(null)
        private set

    fun clearRefreshSkipReason() {
        refreshSkipReason = null
    }

    private val _filter = MutableStateFlow(appPreferences.filter.get())
    val filter = _filter.asStateFlow()

    val listSwipeBottom =
        appPreferences.articleListOptions.swipeBottom.stateIn(viewModelScope)

    private val _searchQuery = MutableStateFlow("")

    private val _searchState = MutableStateFlow(SearchState.INACTIVE)

    private var _article by mutableStateOf<Article?>(null)

    private val _refreshAllState = MutableStateFlow(AngleRefreshState.STOPPED)

    val refreshAllState: Flow<AngleRefreshState>
        get() = _refreshAllState

    var refreshingAll by mutableStateOf(false)
        private set

    var isPullToRefreshing by mutableStateOf(false)
        private set

    var refreshInitialized by mutableStateOf(false)
        private set

    private var _scrollHighWaterMark by mutableIntStateOf(-1)

    val scrollHighWaterMark: Int
        get() = _scrollHighWaterMark

    val markReadOnScrollEnabled: Boolean
        get() = appPreferences.articleListOptions.markReadOnScroll.get()

    val articlesSince = MutableStateFlow<OffsetDateTime>(OffsetDateTime.now())

    private var _showUnauthorizedMessage by mutableStateOf(UnauthorizedMessageState.HIDE)

    val sortOrder = appPreferences.articleListOptions.sortOrder.stateIn(viewModelScope)

    val afterReadAll =
        appPreferences.articleListOptions.afterReadAllBehavior.stateIn(viewModelScope)

    private val _counts = filter.flatMapLatest { latestFilter ->
        account.countAll(latestFilter.status)
    }

    private val _savedSearchCounts = filter.flatMapLatest { latestFilter ->
        account.countAllBySavedSearch(latestFilter.status)
    }

    val articles: Flow<PagingData<Article>> =
        combine(
            filter,
            _searchQuery,
            articlesSince,
            sortOrder
        ) { filter, query, since, sort ->
            account.buildArticlePager(
                filter = filter,
                query = query,
                sortOrder = sort,
                since = since
            ).flow
        }.flatMapLatest { it }

    val folders: Flow<List<Folder>> = combine(
        account.folders,
        _counts,
        filter,
    ) { folders, latestCounts, filter ->
        folders.map { copyFolderCounts(it, latestCounts, filter) }
            .withPositiveCount(filter.status)
    }

    val savedSearches: Flow<List<SavedSearch>> = combine(
        account.savedSearches,
        _savedSearchCounts,
        filter,
    ) { searches, latestCounts, filter ->
        searches.map { copySavedSearchCounts(it, latestCounts) }
            .withPositiveCount(filter.status)
    }

    val allFeeds = account.taggedFeeds

    val showOnboarding = allFeeds.map { it.isEmpty() }

    val allSavedSearches = account.savedSearches

    val allFolders = account.folders

    val topLevelFeeds = combine(
        account.feeds,
        _counts,
        filter,
    ) { feeds, latestCounts, filter ->
        feeds.filter { !it.isReadLater }
            .map { copyFeedCounts(it, latestCounts) }
            .withPositiveCount(filter.status)
    }

    val readLaterFeed: Flow<Feed?> = combine(
        account.feeds,
        _counts,
    ) { feeds, latestCounts ->
        feeds.find { it.isReadLater }
            ?.let { copyFeedCounts(it, latestCounts) }
    }

    val currentFeed: Flow<Feed?> = combine(allFeeds, filter) { feeds, filter ->
        if (filter is ArticleFilter.Feeds) {
            feeds.find { it.id == filter.feedID }
        } else {
            null
        }
    }

    private val sidebar: Flow<List<SidebarItem>> = combine(
        readLaterFeed,
        savedSearches,
        folders,
        topLevelFeeds,
        filter,
    ) { readLater, searches, folders, feeds, filter ->
        SidebarItem.buildList(
            status = filter.status,
            readLaterFeed = readLater,
            savedSearches = searches,
            folders = folders,
            feeds = feeds,
        )
    }

    private val _sidebarItem = MutableStateFlow<SidebarItem?>(null)

    private val sidebarListener: Flow<SidebarItem?> =
        combine(
            listSwipeBottom,
            sidebar,
            filter,
        ) { swipeBottom, sidebar, filter ->
            if (swipeBottom == ArticleListVerticalSwipe.DISABLED) {
                return@combine null
            }

            val found = sidebar.find { it.isSelected(filter) }
            if (found != null) return@combine found

            val stale = _sidebarItem.value?.takeIf { it.isSelected(filter) }
                ?: return@combine null

            SidebarItem(
                toFilter = stale.toFilter,
                isSelected = stale.isSelected,
                next = findFreshItem(stale, sidebar, filter.status),
            )
        }

    private fun findFreshItem(
        stale: SidebarItem,
        sidebar: List<SidebarItem>,
        status: ArticleStatus,
    ): SidebarItem? {
        var search: SidebarItem? = stale.next

        while (search != null) {
            val searchFilter = search.toFilter(status)
            val match = sidebar.find { it.isSelected(searchFilter) }
            if (match != null) {
                return match
            }
            search = search.next
        }

        return null
    }

    val statusCount: Flow<Long> = filter.flatMapLatest { latestFilter ->
        account.countAllByStatus(countableStatus(latestFilter))
    }

    val todayCount: Flow<Long> = _counts.combine(filter) { _, filter ->
        account.countToday(countableStatus(filter))
    }

    val unreadCount: Flow<Long> = combine(
        filter,
        _searchQuery,
    ) { filter, query ->
        account.countUnread(filter = filter, query = query)
    }.flatMapLatest {
        it
    }


    val showUnauthorizedMessage: Boolean
        get() = _showUnauthorizedMessage == UnauthorizedMessageState.SHOW

    val article: Article?
        get() = _article

    val searchQuery: Flow<String>
        get() = _searchQuery

    val searchState: Flow<SearchState>
        get() = _searchState

    val nextFilter: Flow<SidebarItem?> = _sidebarItem.map { it?.next }

    init {
        viewModelScope.launch {
            sidebarListener.collect {
                _sidebarItem.value = it
            }
        }

        if (syncFlushInterval != null) {
            viewModelScope.launch {
                while (true) {
                    delay(syncFlushInterval)
                    withIOContext { account.sendArticleStatus() }
                }
            }
        }

        val skipInitialRefresh =
            appPreferences.refreshInterval.get() == RefreshInterval.MANUALLY_ONLY

        if (skipInitialRefresh) {
            refreshInitialized = true
        } else {
            refreshAll()
        }
    }

    fun selectArticleFilter(status: ArticleStatus = currentStatus) {
        updateFilter(ArticleFilter.Articles(articleStatus = status))
    }

    fun selectToday() {
        updateFilter(ArticleFilter.Today(todayStatus = currentStatus))
    }

    fun selectStatus(status: ArticleStatus) {
        updateFilter(latestFilter.withStatus(status))
    }

    fun selectFeed(feedID: String, folderTitle: String? = null) {
        updateFilter(
            ArticleFilter.Feeds(
                feedID = feedID,
                folderTitle = folderTitle,
                feedStatus = currentStatus
            )
        )
    }

    fun selectSavedSearch(savedSearchID: String) {
        updateFilter(
            ArticleFilter.SavedSearches(savedSearchID, savedSearchStatus = currentStatus)
        )
    }

    fun selectFolder(title: String) {
        updateFilter(
            ArticleFilter.Folders(folderTitle = title, folderStatus = currentStatus)
        )
    }

    fun markAllRead(
        onArticlesCleared: () -> Unit = {},
        range: MarkRead = MarkRead.All,
        filter: ArticleFilter = latestFilter,
        query: String? = _searchQuery.value,
    ) {
        viewModelScope.launchIO {
            val articleIDs = account.unreadArticleIDs(
                filter = filter,
                range = range,
                sortOrder = sortOrder.value,
                query = query,
            )

            account.markAllRead(articleIDs)

            launchIO {
                notificationHelper.dismissNotifications(articleIDs)
            }

            if (range != MarkRead.All) {
                return@launchIO
            }

            if (afterReadAll.value == AfterReadAllBehavior.OPEN_DRAWER) {
                onArticlesCleared()
            } else if (afterReadAll.value == AfterReadAllBehavior.OPEN_NEXT_FEED) {
                openNextFeedOnAllRead(onArticlesCleared)
            }
        }
    }

    val canSaveArticleExternally = account.canSaveArticleExternally.stateIn(viewModelScope)

    fun saveArticleExternallyAsync(articleID: String, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launchIO {
            val result = account.saveArticleExternally(articleID)
            withUIContext { onComplete(result) }
        }
    }

    fun deletePage(articleID: String) {
        viewModelScope.launchIO {
            account.deletePage(articleID)
            articleImageCacheCleaner.cleanup()
        }
    }

    fun saveForLater(url: String, onComplete: (Result<Unit>) -> Unit) {
        viewModelScope.launchIO {
            val result = account.createPage(url)
            withUIContext { onComplete(result) }
        }
    }

    fun removeFeed(
        feedID: String,
    ) {
        viewModelScope.launchIO {
            account.removeFeed(feedID = feedID)
                .onSuccess {
                    articleImageCacheCleaner.cleanup()
                    resetToDefaultFilter()
                }
        }
    }

    fun removeFolder(
        folderTitle: String,
        completion: (result: Result<Unit>) -> Unit
    ) {
        viewModelScope.launchIO {
            account.removeFolder(folderTitle).fold(
                onSuccess = {
                    articleImageCacheCleaner.cleanup()
                    resetToDefaultFilter()
                    completion(Result.success(Unit))
                },
                onFailure = {
                    completion(Result.failure(it))
                }
            )
        }
    }

    private fun refreshFilter(
        filter: ArticleFilter,
        onComplete: () -> Unit,
    ) {
        refreshJob?.cancel()

        refreshJob = viewModelScope.launch(ioDispatcher) {
            if (appPreferences.refreshOnWiFiOnly.get() && !application.isOnWifi()) {
                CapyLog.info("refresh_skipped_mobile", mapOf("type" to "foreground"))
                refreshSkipReason = RefreshSkipReason.Mobile
                onComplete()
                return@launch
            }

            account.refresh(filter).onFailure { throwable ->
                if (throwable is UnauthorizedError && _showUnauthorizedMessage == UnauthorizedMessageState.HIDE) {
                    _showUnauthorizedMessage = UnauthorizedMessageState.SHOW
                }
            }
            articleImageCacheCleaner.cleanup()
            articleImagePreloader.enqueue()

            launchIO {
                WidgetUpdater.update(context)
            }

            onComplete()
        }
    }

    fun refresh(filter: ArticleFilter, onComplete: () -> Unit = {}) {
        isPullToRefreshing = true
        updateArticlesSince()

        refreshFilter(filter) {
            updateArticlesSince()
            isPullToRefreshing = false
            resetScrollHighWaterMark()
            onComplete()
        }
    }

    fun refreshAll(onComplete: () -> Unit = {}) {
        if (_refreshAllState.value == AngleRefreshState.RUNNING) {
            return
        }

        _refreshAllState.value = AngleRefreshState.RUNNING
        refreshingAll = true

        refresh(ArticleFilter.default()) {
            _refreshAllState.value = AngleRefreshState.SETTLING
            refreshInitialized = true
            resetScrollHighWaterMark()
            onComplete()

            refreshJob?.invokeOnCompletion {
                refreshingAll = false
            }
        }
    }

    fun updateScrollHighWaterMark(index: Int) {
        if (index > _scrollHighWaterMark) {
            CapyLog.debug(
                "scroll_high_water_mark:update",
                mapOf("previous" to _scrollHighWaterMark, "new" to index)
            )
            _scrollHighWaterMark = index
        }
    }

    fun resetScrollHighWaterMark() {
        CapyLog.debug(
            "scroll_high_water_mark:reset",
            mapOf("previous" to _scrollHighWaterMark)
        )
        _scrollHighWaterMark = -1
    }

    fun markReadOnScroll(articleID: String) {
        if (isPullToRefreshing) {
            CapyLog.debug("mark_read_on_scroll:skip", mapOf("reason" to "pull_to_refresh"))
            return
        }

        if (_refreshAllState.value == AngleRefreshState.RUNNING) {
            CapyLog.debug("mark_read_on_scroll:skip", mapOf("reason" to "refresh_all_running"))
            return
        }

        val range = MarkRead.After(articleID)

        CapyLog.debug(
            "mark_read_on_scroll",
            mapOf(
                "articleID" to articleID,
                "range" to range.toString(),
                "sortOrder" to sortOrder.value.toString(),
                "highWaterMark" to _scrollHighWaterMark,
            )
        )

        viewModelScope.launchIO {
            val articleIDs = account.unreadArticleIDs(
                filter = latestFilter,
                range = range,
                sortOrder = sortOrder.value,
                query = _searchQuery.value,
            )

            CapyLog.debug(
                "mark_read_on_scroll:marking",
                mapOf("count" to articleIDs.size)
            )

            account.markAllRead(articleIDs)

            launchIO {
                notificationHelper.dismissNotifications(articleIDs)
            }
        }
    }

    fun selectArticle(articleID: String, onComplete: (article: Article) -> Unit = {}) {
        if (_article?.id == articleID) {
            return
        }

        viewModelScope.launchIO {
            val article = preloadedArticles.remove(articleID) ?: buildArticle(articleID) ?: return@launchIO
            _article = article

            launchIO {
                markRead(articleID)
            }

            launchUI {
                onComplete(article)
            }

            if (article.fullContent == Article.FullContentState.LOADING) {
                fullContentJob?.cancel()

                fullContentJob = viewModelScope.launchIO { fetchFullContent(article) }
            }

            articleImagePreloader.enqueue()
        }
    }

    fun preloadAdjacentArticles(articleIDs: List<String?>) {
        val ids = articleIDs
            .filterNotNull()
            .distinct()
            .filterNot { it == _article?.id }

        if (ids.isEmpty()) {
            return
        }

        viewModelScope.launchIO {
            preloadedArticles.keys
                .toList()
                .filterNot { it in ids }
                .forEach { preloadedArticles.remove(it) }

            ids.forEach { articleID ->
                if (preloadedArticles.containsKey(articleID)) {
                    return@forEach
                }

                val article = buildArticle(articleID, downloadImages = true) ?: return@forEach
                preloadedArticles[articleID] = article
            }
        }
    }

    fun toggleArticleRead() {
        _article?.let { article ->
            viewModelScope.launch {
                if (article.read) {
                    markUnread(article.id)
                } else {
                    markRead(article.id)
                }
            }

            _article = article.copy(read = !article.read)
        }
    }

    fun toggleArticleStar() {
        _article?.let { article ->
            viewModelScope.launch {
                if (article.starred) {
                    removeStar(article.id)
                } else {
                    addStar(article.id)
                }

                _article = article.copy(starred = !article.starred)
            }
        }
    }

    fun dismissUnauthorizedMessage() {
        _showUnauthorizedMessage = UnauthorizedMessageState.LATER
    }

    fun clearArticle() {
        _article = null
    }

    fun startSearch() {
        _searchState.value = SearchState.ACTIVE
    }

    fun clearSearch() {
        if (_searchQuery.value.isNotBlank()) {
            clearArticle()
        }
        _searchQuery.value = ""
        _searchState.value = SearchState.INACTIVE
        resetScrollHighWaterMark()
    }

    fun updateSearch(query: String) {
        clearArticle()
        _searchQuery.value = query
        resetScrollHighWaterMark()
    }

    fun addStarAsync(articleID: String) {
        toggleCurrentStarred(articleID)
        addStar(articleID)
    }

    fun removeStarAsync(articleID: String) = viewModelScope.launchIO {
        toggleCurrentStarred(articleID)
        removeStar(articleID)
    }

    fun markReadAsync(articleID: String) = viewModelScope.launchIO {
        toggleCurrentRead(articleID)
        markRead(articleID)
    }

    fun markUnreadAsync(articleID: String) = viewModelScope.launchIO {
        toggleCurrentRead(articleID)
        markUnread(articleID)
    }

    fun requestNextFeed() {
        _sidebarItem.value?.next?.let(::selectSidebarItem)
    }

    private fun selectSidebarItem(item: SidebarItem) {
        val filter = item.toFilter(currentStatus)
        updateFilter(filter)
    }

    private fun addStar(articleID: String) {
        viewModelScope.launchIO {
            account.addStar(articleID)
        }
    }

    private suspend fun removeStar(articleID: String) {
        account.removeStar(articleID)
    }

    private suspend fun markRead(articleID: String) {
        account.markRead(articleID)
        notificationHelper.dismissNotifications(listOf(articleID))
    }

    private suspend fun markUnread(articleID: String) {
        account.markUnread(articleID)
    }

    private fun resetToDefaultFilter() {
        updateFilter(ArticleFilter.default())
    }

    private fun toggleCurrentStarred(articleID: String) {
        _article?.let { article ->
            if (articleID == article.id) {
                _article = article.copy(starred = !article.starred)
            }
        }
    }

    private fun toggleCurrentRead(articleID: String) {
        _article?.let { article ->
            if (articleID == article.id) {
                _article = article.copy(read = !article.read)
            }
        }
    }

    private fun updateFilter(filter: ArticleFilter) {
        _filter.value = filter
        appPreferences.filter.set(filter)

        clearArticle()
        resetScrollHighWaterMark()

        updateArticlesSince()
    }

    private fun updateArticlesSince() {
        articlesSince.value = OffsetDateTime.now().plusSeconds(1)
    }

    private fun copyFolderCounts(
        folder: Folder,
        counts: Map<String, Long>,
        filter: ArticleFilter
    ): Folder {
        val folderFeeds = folder.feeds.map { copyFeedCounts(it, counts) }

        return folder.copy(
            feeds = folderFeeds.withPositiveCount(filter.status).toMutableList(),
            count = folderFeeds.sumOf { it.count }
        )
    }

    private fun copyFeedCounts(feed: Feed, counts: Map<String, Long>): Feed {
        return feed.copy(count = counts.getOrDefault(feed.id, 0))
    }

    private fun copySavedSearchCounts(
        savedSearch: SavedSearch,
        counts: Map<String, Long>
    ): SavedSearch {
        return savedSearch.copy(count = counts.getOrDefault(savedSearch.id, 0))
    }

    private suspend fun buildArticle(
        articleID: String,
        downloadImages: Boolean = false,
    ): Article? {
        val article = account.findArticle(articleID = articleID) ?: return null
        val cachedFullContent = articleFullContentRecords.find(articleID)
        val hasCachedFullContent = cachedFullContent != null && article.enableStickyFullContent

        val fullContent = if (hasCachedFullContent) {
            Article.FullContentState.LOADED
        } else if (enableStickyFullContent && article.enableStickyFullContent) {
            Article.FullContentState.LOADING
        } else {
            Article.FullContentState.NONE
        }

        val content = when (fullContent) {
            Article.FullContentState.LOADED -> cachedFullContent?.contentHTML.orEmpty()
            Article.FullContentState.LOADING -> ""
            else -> article.defaultContent
        }

        articleImageRecords.replaceArticleRefs(
            articleID = article.id,
            contentHTML = content.ifBlank { article.defaultContent },
            articleURL = article.url?.toString(),
            siteURL = article.siteURL,
        )

        if (downloadImages) {
            articleImageDownloader.downloadPendingForArticle(articleID)
        }

        val cachedImages = cachedImagesForArticle(articleID)

        return article.copy(
            read = true,
            content = content,
            fullContent = fullContent,
            cachedImages = cachedImages,
        )
    }

    private suspend fun cachedImagesForArticle(articleID: String): List<CachedArticleImage> {
        return articleImageRecords.findCachedImages(articleID).filter { image ->
            val file = articleImageStore.fileForRelativePath(image.relativePath)
            val exists = file != null && file.exists() && file.isFile

            if (exists) {
                articleImageRecords.touch(image.assetID)
            } else {
                articleImageRecords.markPending(image.assetID, "Cached file missing")
            }

            exists
        }
    }

    fun fetchFullContentAsync(article: Article? = _article) {
        article ?: return

        viewModelScope.launchIO {
            if (enableStickyFullContent && !account.isFullContentEnabled(feedID = article.feedID)) {
                account.enableStickyContent(article.feedID)
            }

            _article = article.copy(fullContent = Article.FullContentState.LOADING)

            _article?.let { fetchFullContent(it) }
        }
    }

    fun resetFullContent() {
        val article = _article ?: return
        viewModelScope.launchIO {
            articleImageRecords.replaceArticleRefs(
                articleID = article.id,
                contentHTML = article.defaultContent,
                articleURL = article.url?.toString(),
                siteURL = article.siteURL,
            )
        }

        _article = article.copy(
            content = article.defaultContent,
            fullContent = Article.FullContentState.NONE
        )

        if (enableStickyFullContent) {
            viewModelScope.launch {
                account.disableStickyContent(article.feedID)
            }
        }
    }

    private suspend fun fetchFullContent(article: Article) {
        account.fetchFullContent(article)
            .fold(
                onSuccess = { value ->
                    if (_article?.id == article.id) {
                        articleFullContentRecords.upsert(
                            articleID = article.id,
                            contentHTML = value,
                        )
                        articleImageRecords.replaceArticleRefs(
                            articleID = article.id,
                            contentHTML = value,
                            articleURL = article.url?.toString(),
                            siteURL = article.siteURL,
                        )
                        articleImageDownloader.downloadPendingForArticle(article.id)
                        val cachedImages = cachedImagesForArticle(article.id)

                        _article = article.copy(
                            content = value,
                            fullContent = Article.FullContentState.LOADED,
                            cachedImages = cachedImages,
                        )
                    }
                },
                onFailure = {
                    if (_article?.id != article.id) {
                        return
                    }
                    _article = article.copy(
                        content = article.defaultContent,
                        fullContent = Article.FullContentState.ERROR
                    )

                    CapyLog.warn(
                        "full_content",
                        mapOf(
                            "error_type" to it::class.simpleName,
                            "error_message" to it.message
                        )
                    )

                    viewModelScope.launchUI {
                        context.showFullContentErrorToast(it)
                    }
                }
            )
    }

    private suspend fun openNextFeedOnAllRead(
        onArticlesCleared: () -> Unit,
    ) {
        val current = _sidebarItem.value
        val next = sidebar.first()

        val nextItem = current?.let { stale ->
            val matched = next.find { it.isSelected(stale.toFilter(latestFilter.status)) }
            matched?.next ?: findFreshItem(stale, next, latestFilter.status)
        }

        if (nextItem != null) {
            selectSidebarItem(nextItem)
        } else {
            if (latestFilter.status == UNREAD) {
                selectArticleFilter()
            }
            onArticlesCleared()
        }
    }

    fun expandFolder(folderName: String, expanded: Boolean) {
        viewModelScope.launchIO {
            account.expandFolder(folderName, expanded = expanded)
        }
    }

    fun updateOpenInBrowser(feedID: String, enabled: Boolean) {
        viewModelScope.launchIO {
            account.updateOpenInBrowser(feedID, enabled = enabled)
            WidgetUpdater.update(context = application.applicationContext)
        }
    }

    fun toggleFeedUnreadBadge(feedID: String, enabled: Boolean) {
        viewModelScope.launchIO {
            account.toggleFeedUnreadBadge(feedID, enabled)
        }
    }

    fun toggleSavedSearchUnreadBadge(id: String, enabled: Boolean) {
        viewModelScope.launchIO {
            account.toggleSavedSearchUnreadBadge(id, enabled)
        }
    }

    fun reloadFavicon(feedID: String) {
        viewModelScope.launchIO {
            account.reloadFavicon(feedID)
        }
    }

    private val latestFilter: ArticleFilter get() = filter.value
    private val currentStatus: ArticleStatus
        get() = latestFilter.status

    private val enableStickyFullContent: Boolean
        get() = appPreferences.enableStickyFullContent.get()
    private val context: Context
        get() = application.applicationContext

    val source = account.source

    fun getArticleLabels(articleID: String?): Flow<List<String>> {
        articleID ?: return emptyFlow()

        return account.getArticleSavedSearches(articleID)
    }

    fun addLabelAsync(articleID: String, savedSearchID: String) {
        viewModelScope.launchIO {
            account.addSavedSearch(articleID, savedSearchID)
        }
    }

    fun removeLabelAsync(articleID: String, savedSearchID: String) {
        viewModelScope.launchIO {
            account.removeSavedSearch(articleID, savedSearchID)
        }
    }

    suspend fun createLabel(articleID: String, name: String): Result<String> {
        return account.createSavedSearch(name).fold(
            onSuccess = { labelID ->
                account.addSavedSearch(articleID, labelID).fold(
                    onSuccess = { Result.success(labelID) },
                    onFailure = { Result.failure(it) }
                )
            },
            onFailure = { Result.failure(it) }
        )
    }

    enum class UnauthorizedMessageState {
        HIDE,
        SHOW,
        LATER,
    }

    companion object {
        val SYNC_FLUSH_INTERVAL = 2.minutes
    }
}

fun Context.showFullContentErrorToast(throwable: Throwable) {
    val message = when (throwable) {
        is ArticleContent.HttpError if throwable.code == 403 ->
            R.string.full_content_error_forbidden

        is ArticleContent.MissingBodyError -> R.string.full_content_error_missing_response
        else -> R.string.full_content_error_generic
    }

    toast(message)
}

fun countableStatus(filter: ArticleFilter): ArticleStatus {
    return when (filter.status) {
        ArticleStatus.STARRED -> ArticleStatus.STARRED
        else -> UNREAD
    }
}
