package com.naemomlab.tessera

import java.io.File
import java.io.InputStream

actual sealed class ImageSource {
    data class FileSource(val file: File) : ImageSource()

    /**
     * Android resource-based image source.
     *
     * @param openStream Lambda that opens a stream. Called multiple times by ImageDecoder
     *                   (bounds check, region decoder, preview), must return fresh InputStream each time.
     * @param description Debug/caching description string
     */
    data class ResourceSource(
        val openStream: () -> InputStream,
        val description: String
    ) : ImageSource()
}
