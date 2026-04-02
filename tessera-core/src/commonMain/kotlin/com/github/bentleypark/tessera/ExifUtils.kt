package com.github.bentleypark.tessera

/**
 * Remap display-space tile rect to raw pixel coordinates based on EXIF orientation.
 *
 * @param rect Display-space tile rectangle
 * @param rawWidth Raw image width (before EXIF rotation)
 * @param rawHeight Raw image height (before EXIF rotation)
 * @param rotationDegrees EXIF rotation in degrees (0, 90, 180, 270)
 * @param isMirrored Whether EXIF orientation includes horizontal flip
 * @return Raw pixel-space rectangle
 */
internal fun remapRectForOrientation(
    rect: TileRect,
    rawWidth: Int,
    rawHeight: Int,
    rotationDegrees: Int,
    isMirrored: Boolean = false
): TileRect {
    val rotated = when (rotationDegrees) {
        90 -> TileRect(
            left = rect.top,
            top = rawWidth - rect.right,
            right = rect.bottom,
            bottom = rawWidth - rect.left
        )
        180 -> TileRect(
            left = rawWidth - rect.right,
            top = rawHeight - rect.bottom,
            right = rawWidth - rect.left,
            bottom = rawHeight - rect.top
        )
        270 -> TileRect(
            left = rawHeight - rect.bottom,
            top = rect.left,
            right = rawHeight - rect.top,
            bottom = rect.right
        )
        else -> rect
    }

    if (!isMirrored) return rotated

    // Mirror horizontally in raw coordinate space
    val w = if (rotationDegrees == 90 || rotationDegrees == 270) rawHeight else rawWidth
    return TileRect(w - rotated.right, rotated.top, w - rotated.left, rotated.bottom)
}
