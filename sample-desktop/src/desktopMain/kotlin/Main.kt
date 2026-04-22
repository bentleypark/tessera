import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.github.bentleypark.tessera.ContentScale
import com.github.bentleypark.tessera.ImageRotation
import com.github.bentleypark.tessera.TesseraImage
import com.github.bentleypark.tessera.TesseraViewerState
import com.github.bentleypark.tessera.rememberTesseraState

data class TestImage(
    val name: String,
    val url: String,
    val contentScale: ContentScale = ContentScale.Fit,
    val description: String = ""
)

val testImages = listOf(
    TestImage(
        "4K JPEG", "https://picsum.photos/3840/2160",
        description = "3840x2160 JPEG Fit"
    ),
    TestImage(
        "Tall (FitWidth)", "https://picsum.photos/800/2400",
        ContentScale.FitWidth,
        description = "800x2400 JPEG FitWidth — vertical scroll"
    ),
    TestImage(
        "Wide (FitHeight)", "https://picsum.photos/3000/800",
        ContentScale.FitHeight,
        description = "3000x800 JPEG FitHeight — horizontal scroll"
    ),
    TestImage(
        "PNG", "https://placehold.co/2000x1500.png",
        description = "2000x1500 PNG format test"
    ),
    TestImage(
        "Large JPEG", "https://picsum.photos/4000/3000",
        description = "4000x3000 JPEG Fit"
    ),
)

fun main() = application {
    val windowState = rememberWindowState(width = 1200.dp, height = 800.dp)

    Window(
        onCloseRequest = ::exitApplication,
        title = "Tessera Desktop Sample",
        state = windowState
    ) {
        MaterialTheme {
            var selectedIndex by remember { mutableStateOf(0) }
            var currentRotation by remember { mutableStateOf(ImageRotation.None) }
            val image = testImages[selectedIndex]
            val viewerState = rememberTesseraState()

            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Image viewer
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

                    // Bottom bar
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        // Image selector buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            testImages.forEachIndexed { index, img ->
                                Button(
                                    onClick = { selectedIndex = index },
                                    enabled = index != selectedIndex
                                ) {
                                    Text(img.name, style = MaterialTheme.typography.labelSmall)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Rotation button
                            Button(onClick = { currentRotation = currentRotation.next() }) {
                                Text("${currentRotation.degrees}°", style = MaterialTheme.typography.labelSmall)
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            // Gesture guide
                            Text(
                                text = "Scroll: pan | Ctrl/Cmd+Scroll: zoom | Drag: pan | Double-click: zoom toggle",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.End
                            )
                        }

                        // Image info
                        Text(
                            text = image.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
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
                style = MaterialTheme.typography.labelSmall
            )
            if (info != null) {
                Text(
                    text = "${info.width}x${info.height}",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Text(
                text = "Zoom: ${((viewerState.scale * 10).toInt() / 10f)}x  Level: ${viewerState.zoomLevel}",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = "Tiles: ${viewerState.cachedTileCount}",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
