package com.capyreader.app.ui.settings.panels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capyreader.app.articleimages.ArticleImageCacheCleaner
import com.capyreader.app.articleimages.ArticleImagePreloader
import com.capyreader.app.common.ImagePreview
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.preferences.ArticleImageCacheCleanupInterval
import com.capyreader.app.preferences.ArticleImageCacheSize
import com.capyreader.app.preferences.ArticleImageDownloadMode
import com.capyreader.app.preferences.ReaderImageVisibility
import com.capyreader.app.preferences.ThemeMode
import com.capyreader.app.ui.articles.ArticleListFontScale
import com.capyreader.app.ui.articles.MarkReadPosition
import com.jocmp.capy.Account
import kotlinx.coroutines.launch

class DisplaySettingsViewModel(
    val account: Account,
    val appPreferences: AppPreferences,
    private val articleImagePreloader: ArticleImagePreloader,
    private val articleImageCacheCleaner: ArticleImageCacheCleaner,
) : ViewModel() {
    var themeMode by mutableStateOf(appPreferences.themeMode.get())
        private set
    
    var appTheme by mutableStateOf(appPreferences.appTheme.get())
        private set
    
    var pureBlackDarkMode by mutableStateOf(appPreferences.pureBlackDarkMode.get())
        private set

    var accentColors by mutableStateOf(appPreferences.accentColors.get())
        private set

    private val _imagePreview = mutableStateOf(appPreferences.articleListOptions.imagePreview.get())

    private val _showSummary = mutableStateOf(appPreferences.articleListOptions.showSummary.get())

    private val _showFeedName = mutableStateOf(appPreferences.articleListOptions.showFeedName.get())

    private val _showFeedIcons =
        mutableStateOf(appPreferences.articleListOptions.showFeedIcons.get())

    private val _shortenTitles = mutableStateOf(appPreferences.articleListOptions.shortenTitles.get())

    var fontScale by mutableStateOf(appPreferences.articleListOptions.fontScale.get())
        private set

    val imagePreview: ImagePreview
        get() = _imagePreview.value

    val showSummary: Boolean
        get() = _showSummary.value

    val showFeedName: Boolean
        get() = _showFeedName.value

    val showFeedIcons: Boolean
        get() = _showFeedIcons.value

    val shortenTitles: Boolean
        get() = _shortenTitles.value

    val pinArticleBars = appPreferences.readerOptions.pinToolbars

    var imageVisibility by mutableStateOf(appPreferences.readerOptions.imageVisibility.get())
        private set

    var articleImageDownloadMode by mutableStateOf(appPreferences.articleImageDownloadMode.get())
        private set

    var articleImageCacheSize by mutableStateOf(appPreferences.articleImageCacheSize.get())
        private set

    var articleImageCacheCleanupInterval by mutableStateOf(appPreferences.articleImageCacheCleanupInterval.get())
        private set

    val improveTalkback = appPreferences.readerOptions.improveTalkback

    val markReadButtonPosition = appPreferences.articleListOptions.markReadButtonPosition

    fun updateThemeMode(themeMode: ThemeMode) {
        appPreferences.themeMode.set(themeMode)
        this.themeMode = themeMode
    }

    fun updatePureBlackDarkMode(enable: Boolean) {
        appPreferences.pureBlackDarkMode.set(enable)
        this.pureBlackDarkMode = enable
    }

    fun updateAccentColors(enable: Boolean) {
        appPreferences.accentColors.set(enable)
        this.accentColors = enable
    }

    fun updatePinArticleBars(pinBars: Boolean) {
        appPreferences.readerOptions.pinToolbars.set(pinBars)
    }

    fun updateFontScale(fontScale: ArticleListFontScale) {
        appPreferences.articleListOptions.fontScale.set(fontScale)

        this.fontScale = fontScale
    }

    fun updateImagePreview(imagePreview: ImagePreview) {
        appPreferences.articleListOptions.imagePreview.set(imagePreview)

        _imagePreview.value = imagePreview
    }

    fun updateSummary(show: Boolean) {
        appPreferences.articleListOptions.showSummary.set(show)

        _showSummary.value = show
    }

    fun updateImageVisibility(option: ReaderImageVisibility) {
        appPreferences.readerOptions.imageVisibility.set(option)

        this.imageVisibility = option
    }

    fun updateArticleImageDownloadMode(mode: ArticleImageDownloadMode) {
        appPreferences.articleImageDownloadMode.set(mode)

        this.articleImageDownloadMode = mode

        if (mode == ArticleImageDownloadMode.OFF) {
            articleImagePreloader.cancel()
            viewModelScope.launch {
                articleImageCacheCleaner.resetInFlightDownloads()
            }
        } else {
            articleImagePreloader.enqueue(replaceExisting = true)
        }
    }

    fun updateArticleImageCacheSize(size: ArticleImageCacheSize) {
        appPreferences.articleImageCacheSize.set(size)
        this.articleImageCacheSize = size

        viewModelScope.launch {
            articleImageCacheCleaner.cleanup(force = true)
            articleImagePreloader.enqueue(replaceExisting = true)
        }
    }

    fun updateArticleImageCacheCleanupInterval(interval: ArticleImageCacheCleanupInterval) {
        appPreferences.articleImageCacheCleanupInterval.set(interval)
        this.articleImageCacheCleanupInterval = interval

        viewModelScope.launch {
            articleImageCacheCleaner.cleanup(force = interval == ArticleImageCacheCleanupInterval.ALWAYS)
        }
    }

    fun clearArticleImageCache() {
        viewModelScope.launch {
            articleImagePreloader.cancel()
            articleImageCacheCleaner.clear()
        }
    }

    fun updateMarkReadButtonPosition(position: MarkReadPosition) {
        appPreferences.articleListOptions.markReadButtonPosition.set(position)
    }

    fun updateFeedIcons(show: Boolean) {
        appPreferences.articleListOptions.showFeedIcons.set(show)

        _showFeedIcons.value = show
    }

    fun updateFeedName(show: Boolean) {
        appPreferences.articleListOptions.showFeedName.set(show)

        _showFeedName.value = show
    }

    fun updateShortenTitles(shortenTitles: Boolean) {
        appPreferences.articleListOptions.shortenTitles.set(shortenTitles)

        _shortenTitles.value = shortenTitles
    }
}
