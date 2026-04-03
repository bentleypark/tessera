package com.github.bentleypark.tessera

import kotlinx.coroutines.await
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.Promise

/**
 * Web image loader using fetch() API via Promise.
 * Returns image data as ByteArray for use with WasmRegionDecoder.
 */
class WasmImageLoader : ImageLoaderStrategy {

    override suspend fun loadImageSource(
        imageUrl: String
    ): Result<ImageSource> {
        return try {
            val jsArray = fetchAsUint8Array(imageUrl).await<JsAny>()
            val length = getJsArrayLength(jsArray)

            if (length <= 0) {
                return Result.failure(IllegalStateException("Empty response for $imageUrl"))
            }

            val bytes = ByteArray(length)
            for (i in 0 until length) {
                bytes[i] = getJsArrayByte(jsArray, i)
            }
            Result.success(ImageSource.DataSource(bytes, imageUrl))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("WasmImageLoader", "Failed to load: $imageUrl", e)
            Result.failure(e)
        }
    }

    override suspend fun clearCache() {
        // No local cache in web — browser handles HTTP caching
    }
}

private fun fetchAsUint8Array(url: String): Promise<JsAny> = js("""
    fetch(url).then(function(response) {
        if (!response.ok) throw new Error('HTTP ' + response.status + ' ' + response.statusText + ' for ' + url);
        return response.arrayBuffer();
    }).then(function(buffer) {
        return new Uint8Array(buffer);
    })
""")

private fun getJsArrayLength(arr: JsAny): Int = js("arr.length")
private fun getJsArrayByte(arr: JsAny, index: Int): Byte = js("arr[index]")
