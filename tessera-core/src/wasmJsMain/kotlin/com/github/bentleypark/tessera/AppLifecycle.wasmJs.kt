package com.github.bentleypark.tessera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import org.w3c.dom.events.EventListener

@Composable
internal actual fun AppLifecycleEffect(
    onStop: () -> Unit,
    onStart: () -> Unit,
) {
    val currentOnStop by rememberUpdatedState(onStop)
    val currentOnStart by rememberUpdatedState(onStart)
    DisposableEffect(Unit) {
        val listener = EventListener {
            if (document.hidden) currentOnStop() else currentOnStart()
        }
        document.addEventListener("visibilitychange", listener)
        onDispose { document.removeEventListener("visibilitychange", listener) }
    }
}
