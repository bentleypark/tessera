package com.github.bentleypark.tessera

import kotlinx.coroutines.Dispatchers

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    System.err.println("ERROR [$tag] $message")
    throwable?.printStackTrace(System.err)
}

actual fun logWarning(tag: String, message: String, throwable: Throwable?) {
    System.err.println("WARN [$tag] $message")
    throwable?.printStackTrace(System.err)
}

internal actual fun Throwable.simpleClassName(): String = this.javaClass.simpleName

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
internal actual val imageLoadDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO
