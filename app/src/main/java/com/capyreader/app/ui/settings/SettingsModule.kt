package com.capyreader.app.ui.settings

import com.capyreader.app.integrations.webdav.WebDavBackupWorker
import com.capyreader.app.transfers.AutomaticBackupWorker
import com.capyreader.app.transfers.OPMLImportWorker
import com.capyreader.app.ui.settings.panels.AccountSettingsViewModel
import com.capyreader.app.ui.settings.panels.AiSettingsViewModel
import com.capyreader.app.ui.settings.panels.DisplaySettingsViewModel
import com.capyreader.app.ui.settings.panels.GeneralSettingsViewModel
import com.capyreader.app.ui.settings.panels.GesturesSettingsViewModel
import com.capyreader.app.ui.settings.panels.IntegrationSettingsViewModel
import com.capyreader.app.ui.settings.panels.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.androidx.workmanager.dsl.worker
import org.koin.dsl.module

val settingsModule = module {
    viewModel {
        GeneralSettingsViewModel(
            refreshScheduler = get(),
            account = get(),
            appPreferences = get(),
            articleImageCacheCleaner = get(),
            articleOfflinePackageDownloader = get(),
            articleReadingProgressRecords = get(),
            articleTtsProgressRecords = get(),
            articleRuleMatchRecords = get(),
            appContext = get(),
        )
    }
    viewModel {
        AccountSettingsViewModel(
            account = get(),
            accountManager = get(),
            appPreferences = get(),
            automaticBackupScheduler = get(),
            backupFile = get(),
            webDavBackupScheduler = get(),
            application = get()
        )
    }
    viewModel {
        DisplaySettingsViewModel(
            account = get(),
            appPreferences = get(),
            articleImagePreloader = get(),
            articleImageCacheCleaner = get(),
            articleTtsEngine = get(),
        )
    }
    viewModel {
        GesturesSettingsViewModel(
            appPreferences = get(),
        )
    }
    viewModel {
        AiSettingsViewModel(
            appPreferences = get(),
            articleAiRepository = get(),
        )
    }
    viewModel {
        IntegrationSettingsViewModel(
            appPreferences = get(),
            webDavBackupClient = get(),
            webDavBackupScheduler = get(),
        )
    }
    viewModel {
       SettingsViewModel(
           account = get(),
           appPreferences = get(),
       )
    }
    worker { OPMLImportWorker(get(), get()) }
    worker { AutomaticBackupWorker(get(), get()) }
    worker { WebDavBackupWorker(get(), get()) }
}
