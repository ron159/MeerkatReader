package com.capyreader.app.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.jocmp.capy.preferences.Preference

@Composable
fun <T> Preference<T>.asState(): State<T> {
    val changes = remember(this) { changes() }
    val initialValue = remember(this) { get() }

    return changes.collectAsState(initial = initialValue)
}
