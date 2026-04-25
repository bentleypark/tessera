package com.github.bentleypark.tessera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification

@Composable
internal actual fun AppLifecycleEffect(
    onStop: () -> Unit,
    onStart: () -> Unit,
) {
    val currentOnStop by rememberUpdatedState(onStop)
    val currentOnStart by rememberUpdatedState(onStart)
    DisposableEffect(Unit) {
        val center = NSNotificationCenter.defaultCenter
        val mainQueue = NSOperationQueue.mainQueue
        val stopToken = center.addObserverForName(
            name = UIApplicationDidEnterBackgroundNotification,
            `object` = null,
            queue = mainQueue,
        ) { _ -> currentOnStop() }
        val startToken = center.addObserverForName(
            name = UIApplicationWillEnterForegroundNotification,
            `object` = null,
            queue = mainQueue,
        ) { _ -> currentOnStart() }
        onDispose {
            center.removeObserver(stopToken)
            center.removeObserver(startToken)
        }
    }
}
