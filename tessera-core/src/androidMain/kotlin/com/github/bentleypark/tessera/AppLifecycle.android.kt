package com.github.bentleypark.tessera

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

// ProcessLifecycleOwner's Startup init can silently fail under Compose MP hosts; use Application callbacks directly.
@Composable
internal actual fun AppLifecycleEffect(
    onStop: () -> Unit,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as? Application ?: return
    val hostActivity = context as? Activity
    val currentOnStop by rememberUpdatedState(onStop)
    val currentOnStart by rememberUpdatedState(onStart)
    DisposableEffect(app, hostActivity) {
        val activities = mutableSetOf<Activity>()
        hostActivity?.let { activities.add(it) }

        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) {
                val wasEmpty = activities.isEmpty()
                activities.add(activity)
                if (wasEmpty) currentOnStart()
            }
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) {
                activities.remove(activity)
                if (activities.isEmpty()) currentOnStop()
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                activities.remove(activity)
            }
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        onDispose {
            app.unregisterActivityLifecycleCallbacks(callbacks)
            activities.clear()
        }
    }
}
