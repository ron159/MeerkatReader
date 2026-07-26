package com.capyreader.app

import com.capyreader.app.articleimages.ArticleImageCacheCleaner
import com.capyreader.app.articleimages.ArticleImageDownloader
import com.capyreader.app.articleimages.ArticleImagePreloader
import com.capyreader.app.articleimages.ArticleImageStore
import com.capyreader.app.integrations.wallabag.WallabagArticleExporter
import com.capyreader.app.integrations.wallabag.WallabagClient
import com.capyreader.app.integrations.wallabag.WallabagIntegration
import com.capyreader.app.integrations.webdav.WebDavBackupUploader
import com.capyreader.app.offline.ArticleOfflineAudioDownloader
import com.capyreader.app.offline.ArticleOfflineAudioStore
import com.capyreader.app.offline.ArticleOfflinePackageDownloader
import com.capyreader.app.notifications.NotificationHelper
import com.capyreader.app.preferences.AppPreferences
import com.jocmp.capy.Account
import com.jocmp.capy.AccountManager
import com.jocmp.capy.DatabaseProvider
import com.jocmp.capy.db.Database
import com.jocmp.capy.persistence.ArticleAiDigestRecords
import com.jocmp.capy.persistence.ArticleAiResultRecords
import com.jocmp.capy.persistence.ArticleFullContentRecords
import com.jocmp.capy.persistence.ArticleImageRecords
import com.jocmp.capy.persistence.ArticleIntegrationExportRecords
import com.jocmp.capy.persistence.ArticleOfflinePackageRecords
import com.jocmp.capy.persistence.ArticleReadingProgressRecords
import com.jocmp.capy.persistence.ArticleRuleMatchRecords
import com.jocmp.capy.persistence.ArticleTtsProgressRecords
import org.koin.dsl.module
import java.io.File

val accountModule = module {
    single<Database> {
        get<DatabaseProvider>().build(accountID = get<AppPreferences>().accountID.get())
    }
    single<Account> {
        get<AccountManager>().findByID(
            id = get<AppPreferences>().accountID.get(),
            database = get<Database>()
        )!!
    }
    single<NotificationHelper> { NotificationHelper(account = get(), applicationContext = get()) }
    single { ArticleAiDigestRecords(database = get()) }
    single { ArticleAiResultRecords(database = get()) }
    single { ArticleFullContentRecords(database = get()) }
    single { ArticleImageRecords(database = get()) }
    single { ArticleIntegrationExportRecords(database = get()) }
    single { ArticleOfflinePackageRecords(database = get()) }
    single { ArticleReadingProgressRecords(database = get()) }
    single { ArticleRuleMatchRecords(database = get()) }
    single { ArticleTtsProgressRecords(database = get()) }
    single { WallabagClient(httpClient = get(), appPreferences = get()) }
    single { WallabagIntegration(client = get()) }
    single {
        WallabagArticleExporter(
            account = get(),
            records = get(),
            integration = get(),
            appPreferences = get(),
        )
    }
    single {
        WebDavBackupUploader(
            account = get(),
            appPreferences = get(),
            backupFile = get(),
            client = get(),
        )
    }
    single { ArticleImageStore(context = get()) }
    single { ArticleOfflineAudioStore(accountDirectory = File(get<Account>().path)) }
    single { ArticleOfflineAudioDownloader(httpClient = get(), store = get()) }
    single {
        ArticleImageCacheCleaner(
            records = get(),
            store = get(),
            appPreferences = get(),
            fullContentRecords = get(),
        )
    }
    single { ArticleImageDownloader(records = get(), httpClient = get(), store = get(), appPreferences = get()) }
    single { ArticleImagePreloader(context = get(), appPreferences = get()) }
    single {
        ArticleOfflinePackageDownloader(
            account = get(),
            packageRecords = get(),
            fullContentRecords = get(),
            imageRecords = get(),
            readingProgressRecords = get(),
            imageDownloader = get(),
            audioDownloader = get(),
            audioStore = get(),
            appPreferences = get(),
        )
    }
}
