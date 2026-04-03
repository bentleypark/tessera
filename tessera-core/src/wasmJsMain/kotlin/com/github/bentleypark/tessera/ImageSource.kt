package com.github.bentleypark.tessera

actual sealed class ImageSource {
    data class DataSource(
        val data: ByteArray,
        val description: String = ""
    ) : ImageSource()
}
