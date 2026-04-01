package com.naemomlab.tessera

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.window.ComposeUIViewController

private data class TestImage(
    val label: String,
    val description: String,
    val url: String
)

private val testImages = listOf(
    TestImage(
        label = "1280px",
        description = "Small (1280x1024)",
        url = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ea/Van_Gogh_-_Starry_Night_-_Google_Art_Project.jpg/1280px-Van_Gogh_-_Starry_Night_-_Google_Art_Project.jpg"
    ),
    TestImage(
        label = "4K",
        description = "4K (3840x2160)",
        url = "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a4/Pizigani_1367_Chart_1MB.jpg/3840px-Pizigani_1367_Chart_1MB.jpg"
    ),
    TestImage(
        label = "8K",
        description = "8K (7087x4724)",
        url = "https://upload.wikimedia.org/wikipedia/commons/e/ea/Van_Gogh_-_Starry_Night_-_Google_Art_Project.jpg"
    ),
    TestImage(
        label = "Large",
        description = "Large (9862x6622)",
        url = "https://upload.wikimedia.org/wikipedia/commons/2/2c/Pieter_Bruegel_the_Elder_-_The_Tower_of_Babel_%28Vienna%29_-_Google_Art_Project.jpg"
    )
)

fun MainViewController() = ComposeUIViewController {
    MaterialTheme {
        SampleContent()
    }
}

@Composable
private fun SampleContent() {
    var selectedImage by remember { mutableStateOf<TestImage?>(null) }

    if (selectedImage != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            TesseraImage(
                imageUrl = selectedImage!!.url,
                modifier = Modifier.fillMaxSize(),
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
            )
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
            .padding(24.dp),
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
