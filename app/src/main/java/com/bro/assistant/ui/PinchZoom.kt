package com.bro.assistant.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.bro.assistant.ui.theme.ThemeState

/** Two-finger pinch zooms the whole app. One-finger taps and scrolls are left alone. */
fun Modifier.pinchToZoomApp(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        var pinching = false
        awaitPointerEvent(PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val down = event.changes.count { it.pressed }
            if (down >= 2) {
                pinching = true
                val z = event.calculateZoom()
                if (z != 1f) ThemeState.zoomTo(ThemeState.zoom * z, save = false)
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (pinching) ThemeState.zoomTo(ThemeState.zoom, save = true)
    }
}
