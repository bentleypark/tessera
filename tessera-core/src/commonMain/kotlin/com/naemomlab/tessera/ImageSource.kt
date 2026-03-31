package com.naemomlab.tessera

/**
 * Platform-agnostic image source.
 *
 * Each platform defines concrete subtypes:
 * - Android: FileSource (java.io.File), ResourceSource (InputStream lambda)
 * - iOS: PathSource (file path), DataSource (NSData)
 */
expect sealed class ImageSource
