package com.naemomlab.tessera.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.naemomlab.tessera.RoutingImageLoader
import com.naemomlab.tessera.TesseraImage
import com.naemomlab.tessera.glide.GlideImageLoader

class SampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val context = LocalContext.current
                val imageLoader = remember {
                    RoutingImageLoader(context, local = GlideImageLoader(context))
                }
                TesseraImage(
                    imageUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/e/ea/Van_Gogh_-_Starry_Night_-_Google_Art_Project.jpg/1280px-Van_Gogh_-_Starry_Night_-_Google_Art_Project.jpg",
                    modifier = Modifier.fillMaxSize(),
                    imageLoader = imageLoader,
                    contentDescription = "Sample Image"
                )
            }
        }
    }
}
