package com.example.musicsmd.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val TRANSITION_MS = 600
private const val MAX_LOAD_WAIT_MS = 5_000L
private const val INCOMING_START_SCALE = 1.08f

/**
 * Cover-art change animation. The outgoing cover stays put until the incoming one has actually
 * loaded (or failed / timed out), then the new cover fades in on top while settling from a slight
 * zoom — so a slow network never turns the transition into a fade to an empty tile.
 *
 * [modifier] must give the transition a size; each layer fills it. [content] must call `onReady`
 * once its image is displayable.
 */
@Composable
fun ArtworkTransition(
    url: String?,
    modifier: Modifier = Modifier,
    content: @Composable (url: String?, onReady: () -> Unit) -> Unit,
) {
    var shown by remember { mutableStateOf(url) }
    var readyUrl by remember { mutableStateOf<String?>(null) }
    var readyNull by remember { mutableStateOf(false) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(url) {
        if (url == shown) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.snapTo(0f)
        withTimeoutOrNull(MAX_LOAD_WAIT_MS) {
            snapshotFlow { if (url == null) readyNull else readyUrl == url }.first { it }
        }
        progress.animateTo(1f, tween(TRANSITION_MS, easing = FastOutSlowInEasing))
        shown = url
    }

    Box(modifier) {
        // Keyed so the incoming layer's image is reused (not reloaded) once it becomes the base.
        val layers = if (url == shown) listOf(url) else listOf(shown, url)
        for (layerUrl in layers) {
            key(layerUrl) {
                val incoming = layerUrl == url && url != shown
                Box(
                    Modifier.matchParentSize().graphicsLayer {
                        if (incoming) {
                            val p = progress.value
                            alpha = p
                            val s = INCOMING_START_SCALE - (INCOMING_START_SCALE - 1f) * p
                            scaleX = s
                            scaleY = s
                        }
                    },
                ) {
                    content(layerUrl) {
                        if (layerUrl == null) readyNull = true else readyUrl = layerUrl
                    }
                }
            }
        }
    }
}
