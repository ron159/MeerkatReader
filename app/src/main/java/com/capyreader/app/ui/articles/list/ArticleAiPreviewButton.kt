package com.capyreader.app.ui.articles.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import com.capyreader.app.R
import com.capyreader.app.ui.articles.MarkReadPosition

@Composable
fun ArticleAiPreviewButton(
    position: MarkReadPosition = MarkReadPosition.TOOLBAR,
    visible: Boolean = true,
    enabled: Boolean,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    if (position == MarkReadPosition.FLOATING_ACTION_BUTTON) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(120)) +
                    scaleIn(initialScale = 0.82f, animationSpec = spring()) +
                    expandVertically(expandFrom = Alignment.Bottom, animationSpec = spring()),
            exit = fadeOut(animationSpec = tween(90)) +
                    scaleOut(targetScale = 0.82f, animationSpec = tween(120)) +
                    shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = tween(140)),
        ) {
            FloatingActionButton(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = CircleShape,
                onClick = {
                    if (enabled && !isLoading) {
                        onClick()
                    }
                },
            ) {
                AiPreviewIcon(isLoading = isLoading)
            }
        }
    } else {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(120)) +
                    expandHorizontally(expandFrom = Alignment.End, animationSpec = spring()),
            exit = fadeOut(animationSpec = tween(90)) +
                    shrinkHorizontally(shrinkTowards = Alignment.End, animationSpec = tween(120)),
        ) {
            IconButton(
                enabled = enabled && !isLoading,
                onClick = onClick,
            ) {
                AiPreviewIcon(isLoading = isLoading)
            }
        }
    }
}

@Composable
private fun AiPreviewIcon(isLoading: Boolean) {
    Icon(
        imageVector = Icons.Rounded.AutoAwesome,
        contentDescription = stringResource(R.string.article_ai_preview_summaries),
        modifier = aiPreviewIconModifier(isLoading),
    )
}

@Composable
private fun aiPreviewIconModifier(isLoading: Boolean): Modifier {
    if (!isLoading) {
        return Modifier
    }

    val transition = rememberInfiniteTransition(label = "AiPreviewLoading")
    val pulse = transition.animateFloat(
        initialValue = 0.86f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "AiPreviewPulse",
    )
    val rotation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
        ),
        label = "AiPreviewRotation",
    )

    return Modifier
        .graphicsLayer {
            scaleX = pulse.value
            scaleY = pulse.value
            rotationZ = rotation.value
        }
}
