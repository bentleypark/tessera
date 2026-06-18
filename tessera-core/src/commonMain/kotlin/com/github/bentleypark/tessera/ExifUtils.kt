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
            top = rawHeight - rect.right,
            right = rect.bottom,
            bottom = rawHeight - rect.left
        )
        180 -> TileRect(
            left = rawWidth - rect.right,
            top = rawHeight - rect.bottom,
            right = rawWidth - rect.left,
            bottom = rawHeight - rect.top
        )
        270 -> TileRect(
            left = rawWidth - rect.bottom,
            top = rect.left,
            right = rawWidth - rect.top,
            bottom = rect.right
        )
        else -> rect
    }

    if (!isMirrored) return rotated

    // Mirror horizontally in raw coordinate space. The remapped rect already lives
    // in raw pixel space, whose X axis always spans rawWidth regardless of rotation.
    return TileRect(rawWidth - rotated.right, rotated.top, rawWidth - rotated.left, rotated.bottom)
}
