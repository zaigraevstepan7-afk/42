package com.antigravity.android.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CancellationException

@Composable
fun PredictiveBackSlide(
    enabled: Boolean = true,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val back by rememberUpdatedState(onBack)
    PredictiveBackHandler(enabled) { progress ->
        try {
            progress.collect { }
            back()
        } catch (_: CancellationException) {
        }
    }
    Box(Modifier.fillMaxSize()) {
        content()
    }
}
