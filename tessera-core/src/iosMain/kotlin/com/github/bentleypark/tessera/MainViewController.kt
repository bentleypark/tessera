package com.github.bentleypark.tessera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.ComposeUIViewController

private data class TestImage(
    val label: String,
    val description: String,
    val url: String
)

private val testImages = listOf(
    TestImage(
        label = "Small",
        description = "1280px (~600KB, ~5MB decoded)",
        url = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1280&q=80"
    ),
    TestImage(
        label = "2K",
        description = "2048px (~1.3MB, ~13MB decoded)",
        url = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=2048&q=80"
    ),
    TestImage(
        label = "4K",
        description = "3840px (~1.5MB, ~37MB decoded)",
        url = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=3840&q=80"
    ),
    TestImage(
        label = "6K",
        description = "6000px (~4MB, ~96MB decoded)",
        url = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=6000&q=90"
    ),
    TestImage(
        label = "8K",
        description = "7680px (~5.6MB, ~134MB decoded)",
        url = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=7680&q=80"
    ),
    TestImage(
        label = "108MP",
        description = "12000px (~7.5MB, ~432MB decoded)",
        url = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=12000&q=80"
    )
)

fun MainViewController(
    imageLoader: ImageLoaderStrategy? = null
) = ComposeUIViewController {
    MaterialTheme {
        SampleContent(imageLoader = imageLoader)
    }
}

@Composable
private fun SampleContent(imageLoader: ImageLoaderStrategy? = null) {
    var selectedImage by remember { mutableStateOf<TestImage?>(null) }

    if (selectedImage != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            TesseraImage(
                imageUrl = selectedImage!!.url,
                modifier = Modifier.fillMaxSize(),
                imageLoader = imageLoader,
                enableDismissGesture = true,
                onDismiss = { selectedImage = null },
                contentDescription = selectedImage!!.description
            )
            Text(
                text = selectedImage!!.description,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp)
                    .zIndex(1f)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 4.dp)
                    .zIndex(2f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { selectedImage = null }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "< Back",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else {
        ImageSelectionScreen(onSelect = { selectedImage = it })
    }
}

@Composable
private fun ImageSelectionScreen(onSelect: (TestImage) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Tessera Sample",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select image size to test performance",
            color = Color.Gray,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(32.dp))

        testImages.forEach { image ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1E1E))
                    .clickable { onSelect(image) }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = image.label,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = image.description,
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = ">",
                    color = Color.Gray,
                    fontSize = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Swipe down to dismiss and return",
            color = Color.Gray,
            fontSize = 12.sp
        )
    }
}
