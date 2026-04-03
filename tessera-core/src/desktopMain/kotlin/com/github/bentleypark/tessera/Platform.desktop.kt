package com.github.bentleypark.tessera

import kotlinx.coroutines.Dispatchers

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    System.err.println("ERROR [$tag] $message")
    throwable?.let { System.err.println(it.stackTraceToString()) }
}

actual fun logWarning(tag: String, message: String, throwable: Throwable?) {
    System.err.println("WARN [$tag] $message")
    throwable?.let { System.err.println(it.stackTraceToString()) }
}

internal actual fun Throwable.simpleClassName(): String = this.javaClass.simpleName

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
internal actual val imageLoadDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO

internal actual fun isZoomModifierPressed(event: androidx.compose.ui.input.pointer.PointerEvent): Boolean {
    val awtEvent = event.nativeEvent as? java.awt.event.MouseEvent ?: return false
    val modifiers = awtEvent.modifiersEx
    val ctrl = (modifiers and java.awt.event.InputEvent.CTRL_DOWN_MASK) != 0
    val meta = (modifiers and java.awt.event.InputEvent.META_DOWN_MASK) != 0
    return ctrl || meta
}
