package com.naemomlab.tessera

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController

fun MainViewController() = ComposeUIViewController {
    MaterialTheme {
        SampleContent()
    }
}

@Composable
private fun SampleContent() {
    TesseraImage(
        imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ea/Van_Gogh_-_Starry_Night_-_Google_Art_Project.jpg/1280px-Van_Gogh_-_Starry_Night_-_Google_Art_Project.jpg",
        modifier = Modifier.fillMaxSize(),
        enableDismissGesture = false,
        contentDescription = "Sample Image"
    )
}
