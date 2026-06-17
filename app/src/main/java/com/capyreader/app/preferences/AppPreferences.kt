package com.capyreader.app.preferences

import android.content.Context
import androidx.preference.PreferenceManager
import com.capyreader.app.common.FeedGroup
import com.capyreader.app.common.ImagePreview
import com.capyreader.app.refresher.RefreshInterval
import com.capyreader.app.ui.articles.ArticleListFontScale
import com.capyreader.app.ui.articles.DefaultPaneExpansionIndex
import com.capyreader.app.ui.articles.MarkReadPosition
import com.jocmp.capy.ArticleFilter
import com.jocmp.capy.articles.FontOption
import com.jocmp.capy.articles.FontSize
import com.jocmp.capy.articles.SortOrder
import com.jocmp.capy.articles.TextAlignment
import com.jocmp.capy.preferences.Preference
import com.jocmp.capy.preferences.PreferenceStore
import com.jocmp.capy.preferences.getEnum
import kotlinx.serialization.json.Json
import java.util.Locale

class AppPreferences(context: Context) {
    private val preferenceStore: PreferenceStore = AndroidPreferenceStore(
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    )

    val readerOptions = ReaderOptions(preferenceStore)

    val articleListOptions = ArticleListOptions(preferenceStore)

    val aiOptions = AiOptions(preferenceStore)

    val offlineOptions = OfflineOptions(preferenceStore)

    val isLoggedIn
        get() = accountID.get().isNotBlank()

    val accountID: Preference<String>
        get() = preferenceStore.getString("account_id")

    val filter: Preference<ArticleFilter>
        get() = preferenceStore.getObject(
            key = "article_filter",
            defaultValue = ArticleFilter.default(),
            serializer = { Json.encodeToString(it) },
            deserializer = {
                try {
                    Json.decodeFromString(it)
                } catch (e: Throwable) {
                    ArticleFilter.default()
                }
            }
        )

    val refreshInterval: Preference<RefreshInterval>
        get() = preferenceStore.getEnum("refresh_interval", RefreshInterval.default)

    val crashReporting: Preference<Boolean>
        get() = preferenceStore.getBoolean("enable_crash_reporting", false)

    val themeMode: Preference<ThemeMode>
        get() = preferenceStore.getEnum("theme_mode", ThemeMode.default)
    
    val appTheme: Preference<AppTheme>
        get() = preferenceStore.getEnum("app_theme", AppTheme.default)
    
    val pureBlackDarkMode: Preference<Boolean>
        get() = preferenceStore.getBoolean("pure_black_dark_mode", false)

    val accentColors: Preference<Boolean>
        get() = preferenceStore.getBoolean("accent_colors", false)

    val openLinksInternally: Preference<Boolean>
        get() = preferenceStore.getBoolean("open_links_internally", true)

    val enableStickyFullContent: Preference<Boolean>
        get() = preferenceStore.getBoolean("enable_sticky_full_content", false)

    val refreshOnWiFiOnly: Preference<Boolean>
        get() = preferenceStore.getBoolean("refresh_on_wifi_only", false)

    val articleImageDownloadMode: Preference<ArticleImageDownloadMode>
        get() = preferenceStore.getEnum("article_image_download_mode", ArticleImageDownloadMode.default)

    val articleImageCacheSize: Preference<ArticleImageCacheSize>
        get() = preferenceStore.getEnum("article_image_cache_size", ArticleImageCacheSize.default)

    val articleImageCacheCleanupInterval: Preference<ArticleImageCacheCleanupInterval>
        get() = preferenceStore.getEnum(
            "article_image_cache_cleanup_interval",
            ArticleImageCacheCleanupInterval.default,
        )

    val lastArticleImageCacheCleanupAt: Preference<Long>
        get() = preferenceStore.getLong("last_article_image_cache_cleanup_at", 0L)

    val paneExpansionIndex: Preference<Int>
        get() = preferenceStore.getInt("pane_expansion_index", DefaultPaneExpansionIndex)

    fun pinFeedGroup(type: FeedGroup): Preference<Boolean> {
        return preferenceStore.getBoolean("feed_group_${type.toString().lowercase()}", true)
    }

    val badgeStyle: Preference<BadgeStyle>
        get() = preferenceStore.getEnum("badge_style", BadgeStyle.default)

    fun clearAll() {
        preferenceStore.clearAll()
    }

    class ReaderOptions(private val preferenceStore: PreferenceStore) {
        val pinToolbars: Preference<Boolean>
            get() = preferenceStore.getBoolean("article_pin_top_bar", false)

        val fontSize: Preference<Int>
            get() = preferenceStore.getInt("article_font_size", FontSize.DEFAULT)

        val fontFamily: Preference<FontOption>
            get() = preferenceStore.getEnum("article_font_family", FontOption.default)

        val topSwipeGesture: Preference<ArticleVerticalSwipe>
            get() = preferenceStore.getEnum(
                "article_top_swipe_gesture",
                ArticleVerticalSwipe.topSwipeDefault
            )

        val bottomSwipeGesture: Preference<ArticleVerticalSwipe>
            get() = preferenceStore.getEnum(
                "article_bottom_swipe_gesture",
                ArticleVerticalSwipe.bottomSwipeDefault
            )

        val imageVisibility: Preference<ReaderImageVisibility>
            get() = preferenceStore.getEnum(
                "article_image_visibility",
                ReaderImageVisibility.ALWAYS_SHOW
            )

        val enablePagingTapGesture: Preference<Boolean>
            get() = preferenceStore.getBoolean("article_enable_paging_tap_gesture", false)

        val enableHorizontaPagination: Preference<Boolean>
            get() = preferenceStore.getBoolean("article_enable_horizontal_pagination", false)

        val improveTalkback: Preference<Boolean>
            get() = preferenceStore.getBoolean("article_improve_talkback", false)

        val titleTextAlignment: Preference<TextAlignment>
            get() = preferenceStore.getEnum("article_title_text_alignment", TextAlignment.default)

        val titleFontSize: Preference<Int>
            get() = preferenceStore.getInt("article_title_font_size", FontSize.TITLE_DEFAULT)

        val titleFollowsBodyFont: Preference<Boolean>
            get() = preferenceStore.getBoolean("article_title_follows_body_font", false)
    }

    class ArticleListOptions(private val preferenceStore: PreferenceStore) {
        val backAction: Preference<BackAction>
            get() = preferenceStore.getEnum("article_list_back_action", BackAction.default)

        val sortOrder: Preference<SortOrder>
            get() = preferenceStore.getEnum(
                "article_list_sort_order",
                SortOrder.default
            )

        val defaultHomeTab: Preference<DefaultHomeTab>
            get() = preferenceStore.getEnum(
                "article_list_default_home_tab",
                DefaultHomeTab.default
            )

        val showFeedName: Preference<Boolean>
            get() = preferenceStore.getBoolean("article_display_feed_name", true)

        val showFeedIcons: Preference<Boolean>
            get() = preferenceStore.getBoolean("article_display_feed_icons", true)

        val showSummary: Preference<Boolean>
            get() = preferenceStore.getBoolean("article_display_show_summary", true)

        val imagePreview: Preference<ImagePreview>
            get() = preferenceStore.getEnum("article_display_image_preview", ImagePreview.default)

        val shortenTitles: Preference<Boolean>
            get() = preferenceStore.getBoolean("article_display_shorten_titles", true)

        val fontScale: Preference<ArticleListFontScale>
            get() = preferenceStore.getEnum(
                "article_display_font_scale",
                ArticleListFontScale.default
            )

        val markReadButtonPosition: Preference<MarkReadPosition>
            get() = preferenceStore.getEnum(
                "article_list_mark_read_position",
                MarkReadPosition.default
            )

        val swipeStart: Preference<RowSwipeOption>
            get() = preferenceStore.getEnum("article_list_swipe_start", RowSwipeOption.default)

        val swipeEnd: Preference<RowSwipeOption>
            get() = preferenceStore.getEnum("article_list_swipe_end", RowSwipeOption.default)

        val swipeBottom: Preference<ArticleListVerticalSwipe>
            get() = preferenceStore.getEnum(
                "article_list_swipe_bottom",
                ArticleListVerticalSwipe.default
            )

        val confirmMarkAllRead: Preference<Boolean>
            get() = preferenceStore.getBoolean("article_list_confirm_mark_all_read", true)

        val afterReadAllBehavior: Preference<AfterReadAllBehavior>
            get() = preferenceStore.getEnum(
                "after_read_all_behavior",
                AfterReadAllBehavior.default
            )

        val markReadOnScroll: Preference<Boolean>
            get() = preferenceStore.getBoolean("article_list_mark_read_on_scroll", false)

        val unreadDisplay: Preference<ArticleStatusListDisplay>
            get() = preferenceStore.getEnum(
                "article_list_unread_display",
                ArticleStatusListDisplay.default
            )

        val starredDisplay: Preference<ArticleStatusListDisplay>
            get() = preferenceStore.getEnum(
                "article_list_starred_display",
                ArticleStatusListDisplay.default
            )

    }

    class OfflineOptions(private val preferenceStore: PreferenceStore) {
        val enabled: Preference<Boolean>
            get() = preferenceStore.getBoolean("offline_reading_enabled", true)

        val downloadOnWiFiOnly: Preference<Boolean>
            get() = preferenceStore.getBoolean("offline_download_on_wifi_only", true)

        val includeFullContent: Preference<Boolean>
            get() = preferenceStore.getBoolean("offline_include_full_content", true)

        val includeImages: Preference<Boolean>
            get() = preferenceStore.getBoolean("offline_include_images", true)

        val includeAudio: Preference<Boolean>
            get() = preferenceStore.getBoolean("offline_include_audio", false)

        val storageLimitMegabytes: Preference<Int>
            get() = preferenceStore.getInt("offline_storage_limit_megabytes", 512)

        val preserveStarred: Preference<Boolean>
            get() = preferenceStore.getBoolean("offline_cleanup_preserve_starred", true)

        val preserveSavedForLater: Preference<Boolean>
            get() = preferenceStore.getBoolean("offline_cleanup_preserve_saved_for_later", true)

        val preserveRecentlyOpened: Preference<Boolean>
            get() = preferenceStore.getBoolean("offline_cleanup_preserve_recently_opened", true)

        val preserveFeedOffline: Preference<Boolean>
            get() = preferenceStore.getBoolean("offline_cleanup_preserve_feed_offline", true)
    }

    class AiOptions(private val preferenceStore: PreferenceStore) {
        val enabled: Preference<Boolean>
            get() = preferenceStore.getBoolean("ai_enabled", false)

        val provider: Preference<AiProvider>
            get() = preferenceStore.getEnum("ai_provider", AiProvider.default)

        val baseUrl: Preference<String>
            get() = preferenceStore.getString("ai_base_url", AiProvider.default.defaultBaseUrl)

        val apiKey: Preference<String>
            get() = preferenceStore.getString("ai_api_key")

        val model: Preference<String>
            get() = preferenceStore.getString("ai_model", AiProvider.default.defaultModel)

        val language: Preference<String>
            get() = preferenceStore.getString(
                "ai_language",
                Locale.getDefault().displayLanguage.ifBlank { "English" },
            )

        val maxInputCharacters: Preference<Int>
            get() = preferenceStore.getInt("ai_max_input_characters", 12000)

        val backgroundPreviewsEnabled: Preference<Boolean>
            get() = preferenceStore.getBoolean("ai_background_preview_summaries_enabled", false)

        val backgroundPreviewsOnWiFiOnly: Preference<Boolean>
            get() = preferenceStore.getBoolean("ai_background_preview_summaries_wifi_only", true)

        val translationMode: Preference<AiTranslationMode>
            get() = preferenceStore.getEnum("ai_translation_mode", AiTranslationMode.default)

        val translatePrompt: Preference<String>
            get() = preferenceStore.getString(
                "ai_translate_prompt",
                com.capyreader.app.ai.ArticleAiPrompts.TRANSLATE,
            )

        val summarizePrompt: Preference<String>
            get() = preferenceStore.getString(
                "ai_summarize_prompt",
                com.capyreader.app.ai.ArticleAiPrompts.SUMMARIZE,
            )

        val previewSummaryPrompt: Preference<String>
            get() = preferenceStore.getString(
                "ai_preview_summary_prompt",
                com.capyreader.app.ai.ArticleAiPrompts.PREVIEW_SUMMARY,
            )

        val keyPointsPrompt: Preference<String>
            get() = preferenceStore.getString(
                "ai_key_points_prompt",
                com.capyreader.app.ai.ArticleAiPrompts.KEY_POINTS,
            )
    }
}
