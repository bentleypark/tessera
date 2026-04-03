package com.github.bentleypark.tessera

import java.io.File

actual sealed class ImageSource {
    data class FileSource(val file: File) : ImageSource()
}
