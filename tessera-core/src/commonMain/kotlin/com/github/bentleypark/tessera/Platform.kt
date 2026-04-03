package com.github.bentleypark.tessera

/**
 * Platform-agnostic logging. Public for companion module access (tessera-coil, tessera-glide).
 */
expect fun logError(tag: String, message: String, throwable: Throwable? = null)
expect fun logWarning(tag: String, message: String, throwable: Throwable? = null)

/**
 * Returns the simple class name of the throwable in a platform-agnostic way.
 */
internal expect fun Throwable.simpleClassName(): String

/**
 * Returns current time in milliseconds since epoch.
 */
internal expect fun currentTimeMillis(): Long

/**
 * IO dispatcher for background work. Public for companion module access.
 */
expect val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatcher for image download and decode (separate from tile loading to prevent starvation).
 */
internal expect val imageLoadDispatcher: kotlinx.coroutines.CoroutineDispatcher

/**
 * Check if zoom modifier key (Ctrl on Windows/Linux, Cmd on macOS) is pressed.
 * Used for Ctrl/Cmd+Scroll → zoom gesture on Desktop.
 * Mobile platforms always return false (no keyboard modifiers on scroll events).
 */
internal expect fun isZoomModifierPressed(event: androidx.compose.ui.input.pointer.PointerEvent): Boolean
