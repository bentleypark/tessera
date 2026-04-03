package com.github.bentleypark.tessera

import kotlinx.coroutines.Dispatchers

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    val errorMsg = if (throwable != null) "$message: ${throwable.message}" else message
    consoleError("ERROR [$tag] $errorMsg")
    throwable?.let { consoleError(it.stackTraceToString()) }
}

actual fun logWarning(tag: String, message: String, throwable: Throwable?) {
    val warnMsg = if (throwable != null) "$message: ${throwable.message}" else message
    consoleWarn("WARN [$tag] $warnMsg")
}

internal actual fun Throwable.simpleClassName(): String {
    return this::class.simpleName ?: "Unknown"
}

internal actual fun currentTimeMillis(): Long {
    return dateNow().toLong()
}

actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
internal actual val imageLoadDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default

internal actual fun isZoomModifierPressed(event: androidx.compose.ui.input.pointer.PointerEvent): Boolean = false

private fun consoleError(msg: String): JsAny = js("(console.error(msg), true)")
private fun consoleWarn(msg: String): JsAny = js("(console.warn(msg), true)")
private fun dateNow(): Double = js("Date.now()")
