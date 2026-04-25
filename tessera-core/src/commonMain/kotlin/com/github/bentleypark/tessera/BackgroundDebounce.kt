package com.github.bentleypark.tessera

internal const val BACKGROUND_DEBOUNCE_MS: Long = 5_000L

/** `backgroundAt == 0L` means no ON_STOP has been observed yet. */
internal fun shouldClearAfterBackground(
    backgroundAt: Long,
    now: Long,
    thresholdMs: Long = BACKGROUND_DEBOUNCE_MS,
): Boolean {
    if (backgroundAt == 0L) return false
    return now - backgroundAt >= thresholdMs
}
