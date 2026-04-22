import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.CanvasBasedWindow
import com.github.bentleypark.tessera.ContentScale
import com.github.bentleypark.tessera.ImageRotation
import com.github.bentleypark.tessera.TesseraImage
import com.github.bentleypark.tessera.TesseraViewerState
import com.github.bentleypark.tessera.rememberTesseraState

data class TestImage(
    val name: String,
    val url: String,
    val contentScale: ContentScale = ContentScale.Fit
)

val testImages = listOf(
    TestImage("4K Landscape", "https://picsum.photos/3840/2160"),
    TestImage("Tall (FitWidth)", "https://picsum.photos/800/2400", ContentScale.FitWidth),
    TestImage("Wide (FitHeight)", "https://picsum.photos/3000/800", ContentScale.FitHeight),
    TestImage("Square", "https://picsum.photos/2000/2000"),
)

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(title = "Tessera Web Sample", canvasElementId = "ComposeTarget") {
        MaterialTheme {
            var selectedIndex by remember { mutableStateOf(0) }
            var currentRotation by remember { mutableStateOf(ImageRotation.None) }
            val image = testImages[selectedIndex]
            val viewerState = rememberTesseraState()

            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color.Black)
                    ) {
                        TesseraImage(
                            imageUrl = image.url,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = image.contentScale,
                            showScrollIndicators = true,
                            rotation = currentRotation,
                            state = viewerState,
                            contentDescription = image.name
                        )

                        // State info overlay
                        StateInfoOverlay(
                            viewerState = viewerState,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = 8.dp)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        testImages.forEachIndexed { index, img ->
                            Button(
                                onClick = { selectedIndex = index },
                                enabled = index != selectedIndex
                            ) {
                                Text(img.name, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        Button(onClick = { currentRotation = currentRotation.next() }) {
                            Text("${currentRotation.degrees}°", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateInfoOverlay(
    viewerState: TesseraViewerState,
    modifier: Modifier = Modifier
) {
    val info = viewerState.imageInfo
    val statusText = when {
        viewerState.isLoading -> "Loading..."
        viewerState.error != null -> "Error"
        viewerState.isReady -> "Ready"
        else -> "-"
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = statusText,
                color = if (viewerState.isReady) Color.Green else Color.Yellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            if (info != null) {
                Text(
                    text = "${info.width}x${info.height}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
            }
            Text(
                text = "Zoom: ${((viewerState.scale * 10).toInt() / 10f)}x  Level: ${viewerState.zoomLevel}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp
            )
            Text(
                text = "Tiles: ${viewerState.cachedTileCount}",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 10.sp
            )
        }
    }
}
