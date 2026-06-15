package com.capyreader.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.capyreader.app.notifications.NotificationHelper
import com.capyreader.app.preferences.AppPreferences
import com.capyreader.app.ui.App
import com.capyreader.app.ui.Route
import com.jocmp.capy.ArticleFilter
import org.koin.android.ext.android.get
import org.koin.android.ext.android.inject

class MainActivity : BaseActivity() {
    val appPreferences by inject<AppPreferences>()

    private var pendingArticleID by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initialRoute = startDestination()
        val openedFromNotification = intent.hasNotificationTarget()
        pendingArticleID = NotificationHelper.openFromIntent(intent, appPreferences = appPreferences)
        if (savedInstanceState == null && !openedFromNotification && initialRoute == Route.Articles) {
            appPreferences.openDefaultHomeTab()
        }

        setContent {
            App(
                startDestination = initialRoute,
                appPreferences = appPreferences,
                pendingArticleID = pendingArticleID,
                onPendingArticleSelected = { pendingArticleID = null },
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingArticleID = NotificationHelper.openFromIntent(intent, appPreferences = appPreferences)
    }

    private fun startDestination(): Route {
        val appPreferences = get<AppPreferences>()

        val accountID = appPreferences.accountID.get()

        return if (accountID.isBlank()) {
            Route.AddAccount
        } else {
            Route.Articles
        }
    }
}

private fun Intent.hasNotificationTarget(): Boolean {
    return hasExtra(NotificationHelper.UNREAD_ONLY_KEY) ||
        hasExtra(NotificationHelper.SHOW_ALL_KEY) ||
        hasExtra(NotificationHelper.ARTICLE_ID_KEY) ||
        hasExtra(NotificationHelper.FEED_ID_KEY)
}

private fun AppPreferences.openDefaultHomeTab() {
    val defaultHomeTab = articleListOptions.defaultHomeTab.get()
    filter.set(ArticleFilter.Articles(articleStatus = defaultHomeTab.articleStatus))
}
