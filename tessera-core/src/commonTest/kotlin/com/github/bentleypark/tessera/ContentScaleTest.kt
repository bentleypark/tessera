package com.github.bentleypark.tessera

import kotlin.test.Test
import kotlin.test.assertEquals

class ContentScaleTest {

    // Viewport: 400x800 (portrait phone)
    private val viewWidth = 400f
    private val viewHeight = 800f

    // --- Auto detection ---

    @Test
    fun auto_tallImage_resolvesFitWidth() {
        // Image 600x3000 (aspect 0.2), viewport 400x800 (aspect 0.5)
        // imageAspect(0.2) < viewAspect/1.5(0.333) → FitWidth
        val result = resolveContentScale(
            ContentScale.Auto, 600f, 3000f, viewWidth, viewHeight
        )
        assertEquals(ContentScale.FitWidth, result)
    }

    @Test
    fun auto_wideImage_resolvesFitHeight() {
        // Image 3000x600 (aspect 5.0), viewport 400x800 (aspect 0.5)
        // imageAspect(5.0) > viewAspect*1.5(0.75) → FitHeight
        val result = resolveContentScale(
            ContentScale.Auto, 3000f, 600f, viewWidth, viewHeight
        )
        assertEquals(ContentScale.FitHeight, result)
    }

    @Test
    fun auto_normalImage_resolvesFit() {
        // Image 400x600 (aspect 0.667), viewport 400x800 (aspect 0.5)
        // viewAspect/1.5=0.333, viewAspect*1.5=0.75
        // 0.667 is between 0.333 and 0.75 → Fit
        val result = resolveContentScale(
            ContentScale.Auto, 400f, 600f, viewWidth, viewHeight
        )
        assertEquals(ContentScale.Fit, result)
    }

    @Test
    fun auto_squareImage_resolvesFitHeight() {
        // Square image 1000x1000 (aspect 1.0) on portrait viewport (aspect 0.5)
        // 1.0 > 0.75 → considered wide relative to viewport → FitHeight
        val result = resolveContentScale(
            ContentScale.Auto, 1000f, 1000f, viewWidth, viewHeight
        )
        assertEquals(ContentScale.FitHeight, result)
    }

    @Test
    fun auto_webtoonImage_resolvesFitWidth() {
        // Webtoon: 800x12000 (aspect 0.067)
        val result = resolveContentScale(
            ContentScale.Auto, 800f, 12000f, viewWidth, viewHeight
        )
        assertEquals(ContentScale.FitWidth, result)
    }

    @Test
    fun auto_panoramaImage_resolvesFitHeight() {
        // Panorama: 10000x800 (aspect 12.5)
        val result = resolveContentScale(
            ContentScale.Auto, 10000f, 800f, viewWidth, viewHeight
        )
        assertEquals(ContentScale.FitHeight, result)
    }

    // --- Explicit modes pass through ---

    @Test
    fun fit_passesThrough() {
        val result = resolveContentScale(
            ContentScale.Fit, 600f, 3000f, viewWidth, viewHeight
        )
        assertEquals(ContentScale.Fit, result)
    }

    @Test
    fun fitWidth_passesThrough() {
        val result = resolveContentScale(
            ContentScale.FitWidth, 800f, 600f, viewWidth, viewHeight
        )
        assertEquals(ContentScale.FitWidth, result)
    }

    @Test
    fun fitHeight_passesThrough() {
        val result = resolveContentScale(
            ContentScale.FitHeight, 800f, 600f, viewWidth, viewHeight
        )
        assertEquals(ContentScale.FitHeight, result)
    }

    // --- Landscape viewport ---

    @Test
    fun auto_tallImageLandscapeViewport_resolvesFitWidth() {
        // Landscape viewport: 800x400
        val result = resolveContentScale(
            ContentScale.Auto, 600f, 3000f, 800f, 400f
        )
        assertEquals(ContentScale.FitWidth, result)
    }

    @Test
    fun auto_wideImageLandscapeViewport_resolvesFitHeight() {
        // Image 3000x600 on landscape 800x400 (viewAspect 2.0)
        // imageAspect(5.0) > viewAspect*1.5(3.0) → FitHeight
        val result = resolveContentScale(
            ContentScale.Auto, 3000f, 600f, 800f, 400f
        )
        assertEquals(ContentScale.FitHeight, result)
    }

    // --- Boundary values at 1.5x threshold ---

    @Test
    fun auto_exactFitWidthBoundary_resolvesFit() {
        // viewAspect = 0.5, boundary = 0.5/1.5 = 0.333...
        // imageAspect = 0.333... (exactly at boundary) → not strictly less → Fit
        val result = resolveContentScale(
            ContentScale.Auto, 1f, 3f, viewWidth, viewHeight
        )
        assertEquals(ContentScale.Fit, result)
    }

    @Test
    fun auto_exactFitHeightBoundary_resolvesFit() {
        // viewAspect = 0.5, boundary = 0.5*1.5 = 0.75
        // imageAspect = 0.75 (exactly at boundary) → not strictly greater → Fit
        val result = resolveContentScale(
            ContentScale.Auto, 3f, 4f, viewWidth, viewHeight
        )
        assertEquals(ContentScale.Fit, result)
    }

    // --- Zero/degenerate dimensions ---

    @Test
    fun auto_zeroImageHeight_resolvesFit() {
        val result = resolveContentScale(
            ContentScale.Auto, 400f, 0f, viewWidth, viewHeight
        )
        assertEquals(ContentScale.Fit, result)
    }

    @Test
    fun auto_zeroViewport_resolvesFit() {
        val result = resolveContentScale(
            ContentScale.Auto, 400f, 800f, 0f, 0f
        )
        assertEquals(ContentScale.Fit, result)
    }

    // --- computeFitScale ---

    @Test
    fun computeFitScale_fitWidth_returnsScaleX() {
        // Image 800x2400 on viewport 400x800
        // scaleX = 400/800 = 0.5
        val result = computeFitScale(
            ContentScale.FitWidth, 800f, 2400f, viewWidth, viewHeight
        )
        assertEquals(0.5f, result)
    }

    @Test
    fun computeFitScale_fitHeight_returnsScaleY() {
        // Image 3000x600 on viewport 400x800
        // scaleY = 800/600 = 1.333...
        val result = computeFitScale(
            ContentScale.FitHeight, 3000f, 600f, viewWidth, viewHeight
        )
        assertEquals(800f / 600f, result)
    }

    @Test
    fun computeFitScale_fit_returnsMinScale() {
        // Image 1000x500 on viewport 400x800
        // scaleX = 0.4, scaleY = 1.6 → min = 0.4
        val result = computeFitScale(
            ContentScale.Fit, 1000f, 500f, viewWidth, viewHeight
        )
        assertEquals(0.4f, result)
    }

    @Test
    fun computeFitScale_zeroDimension_returns1() {
        val result = computeFitScale(
            ContentScale.Fit, 0f, 0f, viewWidth, viewHeight
        )
        assertEquals(1f, result)
    }
}
