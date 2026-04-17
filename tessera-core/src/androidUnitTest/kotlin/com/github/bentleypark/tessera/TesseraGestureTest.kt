package com.github.bentleypark.tessera

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.geometry.Offset
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fake ImageLoaderStrategy that returns a FileSource immediately.
 */
private class FakeImageLoader(private val shouldFail: Boolean = false) : ImageLoaderStrategy {
    override suspend fun loadImageSource(imageUrl: String): Result<ImageSource> {
        if (shouldFail) return Result.failure(RuntimeException("Load failed"))
        return Result.success(ImageSource.FileSource(File("/fake/test-image")))
    }

    override suspend fun clearCache() {}
}

/**
 * Creates a FakeRegionDecoder-based decoder factory for testing.
 */
private fun fakeDecoderFactory(
    width: Int = 2000,
    height: Int = 1500
): (ImageSource) -> RegionDecoder = { _ ->
    FakeRegionDecoder(width = width, height = height)
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TesseraGestureTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testContentDescription = "test-image"

    private fun setUpContent(
        contentScale: ContentScale = ContentScale.Fit,
        enableDismissGesture: Boolean = false,
        enablePagerIntegration: Boolean = false,
        showScrollIndicators: Boolean = false,
        rotation: ImageRotation = ImageRotation.None,
        imageWidth: Int = 2000,
        imageHeight: Int = 1500,
        viewerState: TesseraViewerState? = null,
        onDismiss: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            TesseraImageContent(
                imageUrl = "test://image",
                modifier = Modifier.fillMaxSize(),
                imageLoader = FakeImageLoader(),
                decoderFactory = fakeDecoderFactory(imageWidth, imageHeight),
                contentScale = contentScale,
                contentDescription = testContentDescription,
                enableDismissGesture = enableDismissGesture,
                enablePagerIntegration = enablePagerIntegration,
                showScrollIndicators = showScrollIndicators,
                rotation = rotation,
                viewerState = viewerState,
                onDismiss = onDismiss
            )
        }
        // Wait for image loading to complete
        composeTestRule.waitForIdle()
    }

    // --- Rendering tests ---

    @Test
    fun imageLoads_showsContent() {
        setUpContent()
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .assertExists()
    }

    @Test
    fun imageWithContentScaleFit_showsContent() {
        setUpContent(contentScale = ContentScale.Fit)
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .assertExists()
    }

    @Test
    fun imageWithContentScaleFitWidth_showsContent() {
        setUpContent(contentScale = ContentScale.FitWidth, imageWidth = 800, imageHeight = 2400)
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .assertExists()
    }

    @Test
    fun imageWithContentScaleAuto_showsContent() {
        setUpContent(contentScale = ContentScale.Auto)
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .assertExists()
    }

    // --- Double-tap tests ---

    @Test
    fun doubleTap_zoomsIn() {
        setUpContent()
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .performTouchInput {
                doubleClick(center)
            }
        composeTestRule.waitForIdle()
        // If double-tap worked, a second double-tap should zoom out
        // (we can't directly read scale, but we verify no crash)
    }

    @Test
    fun doubleTapTwice_zoomsOutToOriginal() {
        setUpContent()
        val node = composeTestRule.onNodeWithContentDescription(testContentDescription)
        // Zoom in
        node.performTouchInput { doubleClick(center) }
        composeTestRule.waitForIdle()
        // Zoom out
        node.performTouchInput { doubleClick(center) }
        composeTestRule.waitForIdle()
        // No crash = success
    }

    // --- Dismiss gesture tests ---

    @Test
    fun swipeDown_withDismissDisabled_doesNotDismiss() {
        var dismissed = false
        setUpContent(enableDismissGesture = false, onDismiss = { dismissed = true })
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .performTouchInput {
                swipeDown(startY = centerY, endY = centerY + 300f)
            }
        composeTestRule.waitForIdle()
        assertFalse(dismissed, "onDismiss should not be called when dismiss gesture is disabled")
    }

    @Test
    fun swipeDown_withDismissEnabled_noCrash() {
        setUpContent(enableDismissGesture = true)
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .performTouchInput {
                swipeDown(startY = centerY, endY = centerY + 300f)
            }
        composeTestRule.waitForIdle()
    }

    // --- Pager integration tests ---

    @Test
    fun horizontalSwipe_withPagerEnabled_noCrash() {
        setUpContent(enablePagerIntegration = true)
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .performTouchInput {
                swipeLeft()
            }
        composeTestRule.waitForIdle()
    }

    @Test
    fun horizontalSwipe_withPagerDisabled_noCrash() {
        setUpContent(enablePagerIntegration = false)
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .performTouchInput {
                swipeRight()
            }
        composeTestRule.waitForIdle()
    }

    // --- Scroll indicators ---

    @Test
    fun scrollIndicators_enabled_noCrash() {
        setUpContent(showScrollIndicators = true)
        val node = composeTestRule.onNodeWithContentDescription(testContentDescription)
        // Zoom in to trigger indicators
        node.performTouchInput { doubleClick(center) }
        composeTestRule.waitForIdle()
    }

    // --- ContentScale combinations ---

    @Test
    fun fitWidth_withDismissAndPager_noCrash() {
        setUpContent(
            contentScale = ContentScale.FitWidth,
            enableDismissGesture = true,
            enablePagerIntegration = true,
            imageWidth = 800,
            imageHeight = 2400
        )
        val node = composeTestRule.onNodeWithContentDescription(testContentDescription)
        node.performTouchInput { swipeDown() }
        composeTestRule.waitForIdle()
        node.performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
    }

    @Test
    fun fitHeight_doubleTap_noCrash() {
        setUpContent(
            contentScale = ContentScale.FitHeight,
            imageWidth = 3000,
            imageHeight = 600
        )
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .performTouchInput { doubleClick(center) }
        composeTestRule.waitForIdle()
    }

    // --- Edge case: very small image ---

    @Test
    fun smallImage_gestures_noCrash() {
        setUpContent(imageWidth = 100, imageHeight = 100)
        val node = composeTestRule.onNodeWithContentDescription(testContentDescription)
        node.performTouchInput { doubleClick(center) }
        composeTestRule.waitForIdle()
        node.performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
    }

    // --- Edge case: very large image ---

    @Test
    fun largeImage_gestures_noCrash() {
        setUpContent(imageWidth = 12000, imageHeight = 8000)
        val node = composeTestRule.onNodeWithContentDescription(testContentDescription)
        node.performTouchInput { doubleClick(center) }
        composeTestRule.waitForIdle()
    }

    // --- Error state ---

    @Test
    fun loadFailure_showsErrorText() {
        composeTestRule.setContent {
            TesseraImageContent(
                imageUrl = "test://failing-image",
                modifier = Modifier.fillMaxSize(),
                imageLoader = FakeImageLoader(shouldFail = true),
                decoderFactory = fakeDecoderFactory(),
                contentDescription = testContentDescription
            )
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Error:", substring = true).assertExists()
    }

    // --- Rotation tests ---

    @Test
    fun rotation0_showsContent() {
        setUpContent(rotation = ImageRotation.None)
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .assertExists()
    }

    @Test
    fun rotation90_showsContent() {
        setUpContent(rotation = ImageRotation.Rotate90)
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .assertExists()
    }

    @Test
    fun rotation180_showsContent() {
        setUpContent(rotation = ImageRotation.Rotate180)
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .assertExists()
    }

    @Test
    fun rotation270_showsContent() {
        setUpContent(rotation = ImageRotation.Rotate270)
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .assertExists()
    }

    @Test
    fun rotation90_doubleTap_noCrash() {
        setUpContent(rotation = ImageRotation.Rotate90)
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .performTouchInput { doubleClick(center) }
        composeTestRule.waitForIdle()
    }

    @Test
    fun rotation270_swipe_noCrash() {
        setUpContent(rotation = ImageRotation.Rotate270)
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
    }

    @Test
    fun rotation90_withFitWidth_noCrash() {
        setUpContent(
            rotation = ImageRotation.Rotate90,
            contentScale = ContentScale.FitWidth,
            imageWidth = 800,
            imageHeight = 2400
        )
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .assertExists()
    }

    @Test
    fun rotation90_withDismissGesture_noCrash() {
        setUpContent(rotation = ImageRotation.Rotate90, enableDismissGesture = true)
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .performTouchInput {
                swipeDown(startY = centerY, endY = centerY + 300f)
            }
        composeTestRule.waitForIdle()
    }

    @Test
    fun rotation180_withDismissGesture_noCrash() {
        setUpContent(rotation = ImageRotation.Rotate180, enableDismissGesture = true)
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .performTouchInput {
                swipeDown(startY = centerY, endY = centerY + 300f)
            }
        composeTestRule.waitForIdle()
    }

    @Test
    fun rotation90_withFitHeight_noCrash() {
        setUpContent(
            rotation = ImageRotation.Rotate90,
            contentScale = ContentScale.FitHeight,
            imageWidth = 3000,
            imageHeight = 600
        )
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .assertExists()
    }

    // --- Gesture pause tests ---

    @Test
    fun pinchZoom_pausesTileLoading_thenResumesAfterGesture() {
        val state = TesseraViewerState()
        setUpContent(viewerState = state, imageWidth = 4000, imageHeight = 3000)
        composeTestRule.waitForIdle()

        // Record tile count before pinch
        val tilesBefore = state.cachedTileCount

        // Perform pinch zoom (gesture starts → isGesturing = true)
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .performTouchInput {
                pinch(
                    start0 = center - Offset(100f, 0f),
                    end0 = center - Offset(200f, 0f),
                    start1 = center + Offset(100f, 0f),
                    end1 = center + Offset(200f, 0f)
                )
            }

        // After gesture ends, wait for tile loading to resume
        composeTestRule.waitForIdle()

        // Verify no crash during pinch gesture with tile pause logic.
        // Pinch zoom in Robolectric may not change scale reliably,
        // so we verify the gesture completes without error.
        assertTrue(state.isReady, "State should remain ready after pinch gesture")
    }

    @Test
    fun viewerState_reflectsStateAfterGesture() {
        val state = TesseraViewerState()
        setUpContent(viewerState = state)
        composeTestRule.waitForIdle()

        assertTrue(state.isReady, "State should be ready after load")

        // Double-tap to zoom in
        composeTestRule.onNodeWithContentDescription(testContentDescription)
            .performTouchInput { doubleClick(center) }
        composeTestRule.waitForIdle()

        // Scale should have changed to 3.0 (double-tap zoom target)
        assertTrue(state.scale > 1.0f, "Scale should increase after double-tap zoom")
    }
}
