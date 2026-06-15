package com.capyreader.app.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.capyreader.app.R
import com.capyreader.app.ui.theme.LocalAppTheme

@Composable
fun MeerkatSilhouetteIcon(
    contentDescription: String?,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    Icon(
        painter = painterResource(R.drawable.meerkat_silhouette),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = meerkatSilhouetteTint(alpha),
    )
}

@Composable
fun meerkatSilhouetteTint(alpha: Float = 1f): Color {
    val base = MaterialTheme.colorScheme.onSurface
    val resolvedAlpha = if (LocalAppTheme.current.isDark && alpha < 0.9f) 0.9f else alpha

    return base.copy(alpha = resolvedAlpha)
}
