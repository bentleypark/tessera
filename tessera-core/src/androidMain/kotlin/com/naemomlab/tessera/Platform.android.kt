package com.naemomlab.tessera

import timber.log.Timber

internal actual fun logError(tag: String, message: String, throwable: Throwable?) {
    if (throwable != null) {
        Timber.tag(tag).e(throwable, message)
    } else {
        Timber.tag(tag).e(message)
    }
}

internal actual fun logWarning(tag: String, message: String, throwable: Throwable?) {
    if (throwable != null) {
        Timber.tag(tag).w(throwable, message)
    } else {
        Timber.tag(tag).w(message)
    }
}

internal actual fun Throwable.simpleClassName(): String = this.javaClass.simpleName

internal actual fun currentTimeMillis(): Long = System.currentTimeMillis()

internal actual val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
internal actual val imageLoadDispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
