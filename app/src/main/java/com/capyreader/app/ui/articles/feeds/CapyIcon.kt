package com.capyreader.app.ui.articles.feeds

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.capyreader.app.ui.components.MeerkatSilhouetteIcon
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CapyIcon() {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val (_, setSurprised) = remember { mutableStateOf(false) }

    Box(
        Modifier
            .padding(
                vertical = 18.dp,
                horizontal = 16.dp
            ),
    ) {
        MeerkatSilhouetteIcon(
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    scope.launch {
                        delay(200)
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        setSurprised(true)
                        delay(500)
                        setSurprised(false)
                    }
                }
        )
    }
}

@Preview
@Composable
private fun CapyIconPreview() {
    CapyIcon()
}
