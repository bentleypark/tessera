package com.github.bentleypark.tessera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import java.awt.Frame
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent

// Multi-window apps: attach to every visible top-level frame at registration.
@Composable
internal actual fun AppLifecycleEffect(
    onStop: () -> Unit,
    onStart: () -> Unit,
) {
    val currentOnStop by rememberUpdatedState(onStop)
    val currentOnStart by rememberUpdatedState(onStart)
    DisposableEffect(Unit) {
        val listener = object : WindowAdapter() {
            override fun windowIconified(e: WindowEvent?) {
                currentOnStop()
            }
            override fun windowDeiconified(e: WindowEvent?) {
                currentOnStart()
            }
        }
        val frames = Frame.getFrames().filter { it.isVisible }
        frames.forEach { it.addWindowListener(listener) }
        onDispose {
            frames.forEach { it.removeWindowListener(listener) }
        }
    }
}
