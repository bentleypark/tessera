package com.github.bentleypark.tessera

import androidx.compose.runtime.Composable

/** Main-thread callback when the host app crosses the foreground/background boundary. */
@Composable
internal expect fun AppLifecycleEffect(
    onStop: () -> Unit,
    onStart: () -> Unit,
)
