package com.naemomlab.tessera

/**
 * Platform-agnostic logging.
 */
internal expect fun logError(tag: String, message: String, throwable: Throwable? = null)
internal expect fun logWarning(tag: String, message: String, throwable: Throwable? = null)

/**
 * Returns the simple class name of the throwable in a platform-agnostic way.
 */
internal expect fun Throwable.simpleClassName(): String

/**
 * Returns current time in milliseconds since epoch.
 */
internal expect fun currentTimeMillis(): Long

/**
 * IO dispatcher for background work.
 */
internal expect val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher
