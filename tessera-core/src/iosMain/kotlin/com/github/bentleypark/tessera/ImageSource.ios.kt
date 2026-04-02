package com.github.bentleypark.tessera

actual sealed class ImageSource {
    /**
     * File path-based image source.
     * @param path Absolute file path on iOS filesystem
     */
    data class PathSource(val path: String) : ImageSource()

    /**
     * Data-based image source (e.g., bundled resources).
     * @param getData Lambda that returns image data as ByteArray.
     *                May be called multiple times, must return fresh data each time.
     * @param description Debug/caching description string
     */
    data class DataSource(
        val getData: () -> ByteArray,
        val description: String
    ) : ImageSource()
}
