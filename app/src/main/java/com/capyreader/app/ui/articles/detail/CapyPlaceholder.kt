package com.capyreader.app.ui.articles.detail

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.capyreader.app.ui.components.MeerkatSilhouetteIcon

@Composable
fun CapyPlaceholder() {
    MeerkatSilhouetteIcon(
        contentDescription = null,
        alpha = 0.6f,
    )
}

@Preview
@Composable
private fun CapyPlaceholderPreview() {
    MaterialTheme {
        Surface {
            CapyPlaceholder()
        }
    }
}
