package com.naemomlab.tessera

import platform.Foundation.NSDate
import platform.Foundation.NSLog
import platform.Foundation.timeIntervalSince1970

internal actual fun logError(tag: String, message: String, throwable: Throwable?) {
    val errorMsg = if (throwable != null) "$message: ${throwable.message}" else message
    NSLog("ERROR [$tag] %@", errorMsg)
}

internal actual fun logWarning(tag: String, message: String, throwable: Throwable?) {
    val warnMsg = if (throwable != null) "$message: ${throwable.message}" else message
    NSLog("WARN [$tag] %@", warnMsg)
}

internal actual fun Throwable.simpleClassName(): String {
    return this::class.simpleName ?: "Unknown"
}

internal actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

internal actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
