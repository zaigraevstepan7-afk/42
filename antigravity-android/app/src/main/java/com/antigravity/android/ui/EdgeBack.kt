package com.antigravity.android.ui

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

@Composable
fun PredictiveBackSlide(
    enabled: Boolean = true,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val back by rememberUpdatedState(onBack)
    val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    var shift by remember { mutableFloatStateOf(0f) }
    PredictiveBackHandler(enabled) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { event ->
                val direction = if (event.swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                shift = direction * event.progress.coerceIn(0f, 1f) * widthPx
            }
            shift = 0f
            back()
        } catch (_: CancellationException) {
            shift = 0f
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val edge = 48.dp.toPx()
                val threshold = 96.dp.toPx()
                var fromLeft = false
                var tracking = false
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        fromLeft = start.x <= edge
                        val fromRight = start.x >= size.width - edge
                        tracking = fromLeft || fromRight
                        if (fromRight) fromLeft = false
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        if (!tracking) return@detectHorizontalDragGestures
                        val inward = if (fromLeft) dragAmount > 0f || shift > 0f else dragAmount < 0f || shift < 0f
                        if (!inward && shift == 0f) return@detectHorizontalDragGestures
                        shift = if (fromLeft) (shift + dragAmount).coerceAtLeast(0f) else (shift + dragAmount).coerceAtMost(0f)
                        change.consume()
                    },
                    onDragEnd = {
                        if (tracking && abs(shift) >= threshold) {
                            shift = 0f
                            back()
                        } else {
                            shift = 0f
                        }
                        tracking = false
                    },
                    onDragCancel = {
                        shift = 0f
                        tracking = false
                    },
                )
            }
            .graphicsLayer { translationX = shift },
    ) {
        content()
    }
}
