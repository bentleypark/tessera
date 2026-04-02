package com.naemomlab.tessera

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    val errorMsg = if (throwable != null) "$message: ${throwable.message}" else message
    println("ERROR [$tag] $errorMsg")
}

actual fun logWarning(tag: String, message: String, throwable: Throwable?) {
    val warnMsg = if (throwable != null) "$message: ${throwable.message}" else message
    println("WARN [$tag] $warnMsg")
}

internal actual fun Throwable.simpleClassName(): String {
    return this::class.simpleName ?: "Unknown"
}

internal actual fun currentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default

internal actual val imageLoadDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
