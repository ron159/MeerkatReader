package com.capyreader.app.ui.articles.feeds.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jocmp.capy.Account
import com.jocmp.capy.EditFeedFormEntry
import com.jocmp.capy.Feed
import com.jocmp.capy.Folder
import com.jocmp.capy.common.launchIO
import com.jocmp.capy.common.sortedByTitle
import com.jocmp.capy.common.withUIContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EditFeedViewModel(
    private val account: Account,
) : ViewModel() {
    val folders: Flow<List<Folder>> = account.folders.map { it.sortedByTitle() }
    val showMultiselect = account.supportsMultiFolderFeeds

    var submitting by mutableStateOf(false)
        private set

    fun submit(
        form: EditFeedFormEntry,
        completion: (Result<Feed>) -> Unit,
    ) {
        if (submitting) {
            return
        }

        submitting = true
        viewModelScope.launchIO {
            val result = account.editFeed(form = form)
            withUIContext {
                submitting = false
                completion(result)
            }
        }
    }
}
