package com.capyreader.app.ui.settings.panels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capyreader.app.articleimages.ArticleImageCacheCleaner
import com.capyreader.app.preferences.AfterReadAllBehavior
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.DefaultHomeTab
import com.capyreader.app.refresher.RefreshInterval
import com.capyreader.app.refresher.RefreshScheduler
import com.jocmp.capy.Account
import com.jocmp.capy.ArticleAutomationRule
import com.jocmp.capy.accounts.AutoDelete
import com.jocmp.capy.articles.SortOrder
import com.jocmp.capy.persistence.ArticleOfflinePackageRecords
import com.jocmp.capy.persistence.ArticleReadingProgressRecords
import com.jocmp.capy.persistence.ArticleRuleMatchRecords
import com.jocmp.capy.preferences.getAndSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class GeneralSettingsViewModel(
    private val refreshScheduler: RefreshScheduler,
    val account: Account,
    private val appPreferences: AppPreferences,
    private val articleImageCacheCleaner: ArticleImageCacheCleaner,
    private val articleOfflinePackageRecords: ArticleOfflinePackageRecords,
    private val articleReadingProgressRecords: ArticleReadingProgressRecords,
    private val articleRuleMatchRecords: ArticleRuleMatchRecords,
) : ViewModel() {
    val source = account.source

    var refreshInterval by mutableStateOf(refreshScheduler.refreshInterval)
        private set

    var autoDelete by mutableStateOf(account.preferences.autoDelete.get())
        private set

    var canOpenLinksInternally by mutableStateOf(appPreferences.openLinksInternally.get())
        private set

    var sortOrder by mutableStateOf(appPreferences.articleListOptions.sortOrder.get())
        private set

    var defaultHomeTab by mutableStateOf(appPreferences.articleListOptions.defaultHomeTab.get())
        private set

    var confirmMarkAllRead by mutableStateOf(appPreferences.articleListOptions.confirmMarkAllRead.get())
        private set

    var afterReadAll by mutableStateOf(appPreferences.articleListOptions.afterReadAllBehavior.get())
        private set

    var enableStickyFullContent by mutableStateOf(appPreferences.enableStickyFullContent.get())
        private set

    var markReadOnScroll by mutableStateOf(appPreferences.articleListOptions.markReadOnScroll.get())
        private set

    var refreshOnWiFiOnly by mutableStateOf(appPreferences.refreshOnWiFiOnly.get())
        private set

    var offlineReadingEnabled by mutableStateOf(appPreferences.offlineOptions.enabled.get())
        private set

    var offlineDownloadOnWiFiOnly by mutableStateOf(appPreferences.offlineOptions.downloadOnWiFiOnly.get())
        private set

    var offlineIncludeFullContent by mutableStateOf(appPreferences.offlineOptions.includeFullContent.get())
        private set

    var offlineIncludeImages by mutableStateOf(appPreferences.offlineOptions.includeImages.get())
        private set

    var offlineIncludeAudio by mutableStateOf(appPreferences.offlineOptions.includeAudio.get())
        private set

    var offlineStorageLimitMegabytes by mutableStateOf(appPreferences.offlineOptions.storageLimitMegabytes.get().toString())
        private set

    var offlinePreserveStarred by mutableStateOf(appPreferences.offlineOptions.preserveStarred.get())
        private set

    var offlinePreserveSavedForLater by mutableStateOf(appPreferences.offlineOptions.preserveSavedForLater.get())
        private set

    var offlinePreserveRecentlyOpened by mutableStateOf(appPreferences.offlineOptions.preserveRecentlyOpened.get())
        private set

    var offlinePreserveFeedOffline by mutableStateOf(appPreferences.offlineOptions.preserveFeedOffline.get())
        private set

    fun updateRefreshOnWiFiOnly(enabled: Boolean) {
        appPreferences.refreshOnWiFiOnly.set(enabled)
        refreshOnWiFiOnly = enabled
    }

    fun updateOfflineReadingEnabled(enabled: Boolean) {
        appPreferences.offlineOptions.enabled.set(enabled)
        offlineReadingEnabled = enabled
    }

    fun updateOfflineDownloadOnWiFiOnly(enabled: Boolean) {
        appPreferences.offlineOptions.downloadOnWiFiOnly.set(enabled)
        offlineDownloadOnWiFiOnly = enabled
    }

    fun updateOfflineIncludeFullContent(enabled: Boolean) {
        appPreferences.offlineOptions.includeFullContent.set(enabled)
        offlineIncludeFullContent = enabled
    }

    fun updateOfflineIncludeImages(enabled: Boolean) {
        appPreferences.offlineOptions.includeImages.set(enabled)
        offlineIncludeImages = enabled
    }

    fun updateOfflineIncludeAudio(enabled: Boolean) {
        appPreferences.offlineOptions.includeAudio.set(enabled)
        offlineIncludeAudio = enabled
    }

    fun updateOfflineStorageLimitMegabytes(value: String) {
        val sanitized = value.filter(Char::isDigit).take(6)
        offlineStorageLimitMegabytes = sanitized

        val parsed = sanitized.toIntOrNull() ?: return
        if (parsed > 0) {
            appPreferences.offlineOptions.storageLimitMegabytes.set(parsed)
        }
    }

    fun updateOfflinePreserveStarred(enabled: Boolean) {
        appPreferences.offlineOptions.preserveStarred.set(enabled)
        offlinePreserveStarred = enabled
    }

    fun updateOfflinePreserveSavedForLater(enabled: Boolean) {
        appPreferences.offlineOptions.preserveSavedForLater.set(enabled)
        offlinePreserveSavedForLater = enabled
    }

    fun updateOfflinePreserveRecentlyOpened(enabled: Boolean) {
        appPreferences.offlineOptions.preserveRecentlyOpened.set(enabled)
        offlinePreserveRecentlyOpened = enabled
    }

    fun updateOfflinePreserveFeedOffline(enabled: Boolean) {
        appPreferences.offlineOptions.preserveFeedOffline.set(enabled)
        offlinePreserveFeedOffline = enabled
    }

    val filterKeywords = account
        .preferences
        .filterKeywords
        .stateIn(viewModelScope)

    val automationRules = account
        .preferences
        .automationRules
        .stateIn(viewModelScope)

    fun updateRefreshInterval(interval: RefreshInterval) {
        refreshScheduler.update(interval)

        this.refreshInterval = interval
    }

    fun updateSortOrder(sort: SortOrder) {
        appPreferences.articleListOptions.sortOrder.set(sort)

        this.sortOrder = sort
    }

    fun updateDefaultHomeTab(tab: DefaultHomeTab) {
        appPreferences.articleListOptions.defaultHomeTab.set(tab)

        defaultHomeTab = tab
    }

    fun updateAutoDelete(autoDelete: AutoDelete) {
        account.preferences.autoDelete.set(autoDelete)

        this.autoDelete = autoDelete
    }

    fun updateOpenLinksInternally(openLinksInternally: Boolean) {
        appPreferences.openLinksInternally.set(openLinksInternally)

        this.canOpenLinksInternally = openLinksInternally
    }

    fun updateConfirmMarkAllRead(confirm: Boolean) {
        appPreferences.articleListOptions.confirmMarkAllRead.set(confirm)

        confirmMarkAllRead = confirm
    }

    fun updateAfterReadAll(behavior: AfterReadAllBehavior) {
        appPreferences.articleListOptions.afterReadAllBehavior.set(behavior)

        afterReadAll = behavior
    }

    fun updateMarkReadOnScroll(enabled: Boolean) {
        appPreferences.articleListOptions.markReadOnScroll.set(enabled)

        markReadOnScroll = enabled
    }

    fun updateStickyFullContent(enable: Boolean) {
        appPreferences.enableStickyFullContent.set(enable)

        enableStickyFullContent = enable

        if (!enable) {
            viewModelScope.launch(Dispatchers.IO) {
                account.clearStickyFullContent()
            }
        }
    }

    fun clearAllArticles() {
        viewModelScope.launch(Dispatchers.IO) {
            account.clearAllArticles()
            articleImageCacheCleaner.cleanup()
        }
    }

    fun clearOfflinePackages() {
        viewModelScope.launch(Dispatchers.IO) {
            articleOfflinePackageRecords.deleteAll()
        }
    }

    fun clearReadingProgress() {
        viewModelScope.launch(Dispatchers.IO) {
            articleReadingProgressRecords.deleteAll()
        }
    }

    fun clearRuleMatchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            articleRuleMatchRecords.deleteAll()
        }
    }

    fun addFilterKeyword(keyword: String) {
        account.preferences.filterKeywords.getAndSet { list ->
            list.toMutableSet().apply { add(keyword) }
        }
    }

    fun removeFilterKeyword(keyword: String) {
        account.preferences.filterKeywords.getAndSet { list ->
            list.toMutableSet().apply { remove(keyword) }
        }
    }

    fun addAutomationRule(rule: ArticleAutomationRule) {
        account.preferences.automationRules.getAndSet { rules ->
            rules + rule
        }
    }

    fun updateAutomationRule(rule: ArticleAutomationRule) {
        account.preferences.automationRules.getAndSet { rules ->
            rules.map { existingRule ->
                if (existingRule.id == rule.id) rule else existingRule
            }
        }
    }

    fun removeAutomationRule(rule: ArticleAutomationRule) {
        account.preferences.automationRules.getAndSet { rules ->
            rules.filterNot { it.id == rule.id }
        }
    }

    fun moveAutomationRule(rule: ArticleAutomationRule, direction: Int) {
        account.preferences.automationRules.getAndSet { rules ->
            val currentIndex = rules.indexOfFirst { it.id == rule.id }
            if (currentIndex == -1) {
                return@getAndSet rules
            }

            val targetIndex = (currentIndex + direction).coerceIn(rules.indices)
            if (targetIndex == currentIndex) {
                return@getAndSet rules
            }

            rules.toMutableList().apply {
                add(targetIndex, removeAt(currentIndex))
            }
        }
    }

}
