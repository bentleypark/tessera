# Tessera

> **Tessera** (Latin: "small tile piece") — A Compose Multiplatform tile-based high-resolution image viewer

[![CI](https://github.com/bentleypark/tessera/actions/workflows/ci.yml/badge.svg)](https://github.com/bentleypark/tessera/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.bentleypark/tessera-core.svg)](https://central.sonatype.com/artifact/io.github.bentleypark/tessera-core)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## Overview

Tessera is a memory-efficient image viewer for Compose Multiplatform that uses tile-based rendering to display large, high-resolution images (up to 108MP+) without loading the entire image into memory.

### Supported Platforms

| Platform | Status | Decoder Strategy |
|----------|--------|-----------------|
| Android  | Stable | BitmapRegionDecoder (true region decoding) |
| iOS      | Stable | CGImageSource subsample + Skia tile extraction |
| Desktop  | Stable | ImageIO subsample cache + tile extraction |
| Web      | Experimental | Skia Surface + drawImageRect (Kotlin/Wasm) |

### Key Features

- **108MP+ Support** — Handles images up to 12000px+ on mobile devices with limited RAM
- **Memory Efficient** — Subsample decoding + tile-based rendering, only visible tiles in memory
- **1ms Tile Rendering** — Skia-optimized tile extraction averages 1-2ms per tile
- **Smooth Gestures** — 60fps pinch-to-zoom, pan, and double-tap zoom
- **Pager Ready** — HorizontalPager integration with smart gesture routing (swipe vs pan)
- **Compose Native** — Built for Compose Multiplatform (not a View wrapper)
- **LRU Tile Cache** — Automatic eviction with configurable cache size (default: 150 tiles)
- **Multiple Image Sources** — Network URLs (http/https), local files (file://), Android content URIs, Android resources
- **EXIF Orientation** — Automatic rotation/mirror correction for all 8 EXIF orientations
- **ReadMode** — Auto-detect tall/wide images with FitWidth/FitHeight content scaling
- **Scroll Indicators** — Minimap + scroll bars for zoomed-in navigation (auto-hide)
- **Zero Core Dependencies** — `tessera-core` has no external image library dependencies
- **Optional Loaders** — `tessera-coil` (KMP) and `tessera-glide` (Android) companion modules

## Quick Start

### Installation

Add dependencies to your `build.gradle.kts`:

```kotlin
// Core library (required)
implementation("io.github.bentleypark:tessera-core:<version>")

// Optional: Coil image loader (Android + iOS KMP)
implementation("io.github.bentleypark:tessera-coil:<version>")

// Optional: Glide image loader (Android only)
implementation("io.github.bentleypark:tessera-glide:<version>")
```

### Android Usage

```kotlin
@Composable
fun MyScreen() {
    // Basic usage (built-in NetworkImageLoader)
    TesseraImage(
        imageUrl = "https://example.com/large-image.jpg",
        modifier = Modifier.fillMaxSize()
    )

    // With Coil loader
    val coilLoader = remember { CoilImageLoader(context) }
    TesseraImage(
        imageUrl = "https://example.com/large-image.jpg",
        modifier = Modifier.fillMaxSize(),
        imageLoader = coilLoader
    )

    // Android resource
    TesseraImage(
        imageResId = R.drawable.large_image,
        modifier = Modifier.fillMaxSize()
    )
}
```

### iOS Usage

```kotlin
// Kotlin — Basic (built-in IosImageLoader with NSURLSession)
fun MainViewController() = ComposeUIViewController {
    TesseraImage(
        imageUrl = "https://example.com/large-image.jpg",
        modifier = Modifier.fillMaxSize()
    )
}

// Kotlin — With Coil loader
fun MainViewController() = ComposeUIViewController {
    val coilLoader = remember { CoilImageLoader() }
    TesseraImage(
        imageUrl = "https://example.com/large-image.jpg",
        modifier = Modifier.fillMaxSize(),
        imageLoader = coilLoader
    )
}
```

```swift
// Swift — With Coil loader (via TesseraCoil framework)
import TesseraCoil

let coilLoader = CoilImageLoader.companion.create()
MainViewControllerKt.MainViewController(imageLoader: coilLoader)
```

### Desktop Usage

```kotlin
fun main() = application {
    Window(title = "Tessera Desktop") {
        TesseraImage(
            imageUrl = "https://example.com/large-image.jpg",
            modifier = Modifier.fillMaxSize(),
            showScrollIndicators = true
        )
    }
}
```

**Desktop Gestures:**

| Gesture | Action |
|---------|--------|
| Scroll (trackpad/wheel) | Pan image |
| Ctrl/Cmd + Scroll | Zoom in/out (cursor-based) |
| Click + Drag | Pan image |
| Double-click | Zoom toggle (1x ↔ 3x) |

### Web (Wasm) Usage

```kotlin
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    CanvasBasedWindow(title = "Tessera Web", canvasElementId = "ComposeTarget") {
        TesseraImage(
            imageUrl = "https://example.com/large-image.jpg",
            modifier = Modifier.fillMaxSize()
        )
    }
}
```

> **Note**: Web support is experimental (Kotlin/Wasm). Full image is loaded into memory — suitable for images up to ~30MP in typical browser environments.

## Performance

Benchmarked on iPhone 7 (A10 Fusion, 2GB RAM, iOS 15.8.5) — 5 runs each:

| Image Size | Init | Tile Count | Tile Avg | Subsample | Est. Memory |
|------------|------|------------|----------|-----------|-------------|
| **4K** (10MP) | 369ms | 48 | **1ms** | 1920x1282 | ~10MB |
| **6K** (24MP) | 892ms | 96 | **1ms** | 3000x2002 | ~24MB |
| **8K** (39MP) | 2053ms | 165 | **2ms** | 3840x2563 | ~37MB |
| **108MP** (86MP) | 1271ms | 336 | **2ms** | 3000x1787 | ~21MB |

### vs Full Image Loading

| Image | Full Load Memory | Tessera Memory | Reduction |
|-------|-----------------|----------------|-----------|
| 4K | ~37MB | ~10MB | **73%** |
| 8K | ~134MB | ~37MB | **72%** |
| 108MP | ~432MB | ~21MB | **95%** |

> **Note**: No other tile-based image viewer library publishes quantitative benchmarks. Tessera is the first to provide reproducible, multi-run performance data.

## Modules

```
tessera/
├── tessera-core/      # Core library (Android + iOS + Desktop + Web, zero external deps)
├── tessera-coil/      # Coil 3.x image loader (Android + iOS KMP)
├── tessera-glide/     # Glide 5.x image loader (Android only)
├── sample/            # Android sample app
├── sample-desktop/    # Desktop (JVM) sample app
├── sample-web/        # Web (Wasm) sample app
└── iosApp/            # iOS sample app (SwiftUI + Compose)
```

| Module | Platforms | Dependencies |
|--------|-----------|-------------|
| `tessera-core` | Android, iOS, Desktop, Web | Compose Multiplatform, Coroutines |
| `tessera-coil` | Android, iOS | Coil 3.x, Ktor |
| `tessera-glide` | Android | Glide 5.x |

### Image Loading Strategy

| Module | Android | iOS | Desktop | Web |
|--------|---------|-----|---------|-----|
| `tessera-core` | `NetworkImageLoader` (java.net.URL) | `IosImageLoader` (NSURLSession) | `DesktopImageLoader` (HttpURLConnection) | `WasmImageLoader` (fetch API) |
| `tessera-coil` | Coil + OkHttp | Coil + Ktor Darwin | — | — |
| `tessera-glide` | Glide (file/content URI) | N/A | — | — |

## Architecture

```
TesseraImage (Composable)           <- User-facing API
  |-- Gesture detection
  |-- Canvas tile rendering
  v
TesseraState                        <- State + LRU Cache
  |-- initializeDecoder() on background thread
  |-- applyInitResult() on main thread
  |-- decodeTile() / cacheTile() split for thread safety
  v
TileManager                         <- Grid Calculation
  |-- Zoom level (0-3)
  |-- Visible tile selection
  v
RegionDecoder (expect/actual)       <- Platform Decoding
  |-- Android: BitmapRegionDecoder (true region decoding)
  |-- iOS: CGImageSource subsample + Skia tile extraction
  |-- Desktop: ImageIO subsample cache + getSubimage extraction
  |-- Web: Skia Image.makeFromEncoded + Surface.drawImageRect
```

### iOS Decoder: CGImageSource + Skia Hybrid

Unlike loading the entire image into memory, the iOS decoder uses a two-phase approach:

1. **CGImageSource** with `kCGImageSourceSubsampleFactor` decodes at reduced resolution (1/2, 1/4, 1/8) using JPEG's DCT block structure
2. **Skia `Canvas.drawImageRect`** extracts tiles from the subsampled image at ~1ms per tile

This enables 108MP images on iPhone 7 (2GB RAM) by keeping only ~21MB in memory at zoom level 0.

### Memory Protection

| Image Size | Max Subsample | Decoded Resolution | Memory |
|------------|--------------|-------------------|--------|
| < 30MP | 1 (full) | Original | No limit |
| 30-80MP | 2 (half) | 1/2 | ~1/4 |
| 80MP+ | 4 (quarter) | 1/4 | ~1/16 |

### Zoom Levels

| Level | Scale Range | Sample Size | Resolution |
|-------|-------------|-------------|------------|
| 0     | 1.0x-1.5x  | 2           | Half       |
| 1     | 1.5x-3.0x  | 1           | Full       |
| 2     | 3.0x-6.0x  | 1           | Full       |
| 3     | 6.0x+      | 1           | Full       |

## API Reference

### TesseraImage Parameters

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `imageUrl` | String | required | Image URL (http, file, content) |
| `modifier` | Modifier | `Modifier` | Layout modifier |
| `minScale` | Float | `1.0f` | Minimum zoom scale |
| `maxScale` | Float | `10.0f` | Maximum zoom scale |
| `contentScale` | ContentScale | `Fit` | Content scaling: Fit, FitWidth, FitHeight, Auto |
| `imageLoader` | ImageLoaderStrategy? | `null` | Custom image loader (Coil, Glide, etc.) |
| `contentDescription` | String? | `null` | Accessibility description |
| `enableDismissGesture` | Boolean | `false` | Vertical drag-to-dismiss |
| `enablePagerIntegration` | Boolean | `false` | Pass horizontal swipes to parent Pager |
| `showScrollIndicators` | Boolean | `false` | Show scroll position indicators when zoomed |
| `onDismiss` | () -> Unit | `{}` | Dismiss callback |

### HorizontalPager Integration

Tessera supports `HorizontalPager` for image gallery browsing:

```kotlin
val pagerState = rememberPagerState { images.size }

HorizontalPager(state = pagerState) { page ->
    TesseraImage(
        imageUrl = images[page],
        modifier = Modifier.fillMaxSize(),
        enablePagerIntegration = true,  // Enable swipe between pages
        enableDismissGesture = true
    )
}
```

**Gesture behavior with `enablePagerIntegration = true`:**

| State | Horizontal Swipe | Pinch | Double-Tap | Vertical Swipe |
|-------|-----------------|-------|------------|----------------|
| Zoomed out (1.0x) | Next/prev page | Zoom in | Zoom to 3x | Dismiss (if enabled) |
| Zoomed in (middle) | Pan image | Zoom | Zoom out | Pan image |
| Zoomed in (at edge) | Next/prev page | Zoom | Zoom out | Pan image |

### ReadMode (ContentScale)

For long images (webtoons, documents) or wide images (panoramas), use `ContentScale` to auto-fit:

```kotlin
// Auto-detect: tall → FitWidth, wide → FitHeight, normal → Fit
TesseraImage(
    imageUrl = "https://example.com/webtoon.jpg",
    contentScale = ContentScale.Auto
)

// Force fit to width (vertical scroll)
TesseraImage(
    imageUrl = "https://example.com/document.jpg",
    contentScale = ContentScale.FitWidth
)
```

| Mode | Behavior |
|------|----------|
| `Fit` | Fit entire image in viewport (default) |
| `FitWidth` | Fit to width, vertical scroll |
| `FitHeight` | Fit to height, horizontal scroll |
| `Auto` | Detect based on aspect ratio |

## Building

```bash
# Android build + tests
./gradlew :tessera-core:testDebugUnitTest

# Desktop build + tests
./gradlew :tessera-core:compileKotlinDesktop
./gradlew :tessera-core:desktopTest

# Web (Wasm) build
./gradlew :tessera-core:compileKotlinWasmJs

# Build all modules
./gradlew :tessera-core:assembleDebug :tessera-coil:assembleDebug :tessera-glide:assembleDebug

# iOS framework
./gradlew :tessera-core:linkDebugFrameworkIosSimulatorArm64

# iOS framework with Coil
./gradlew :tessera-coil:linkDebugFrameworkIosSimulatorArm64

# Run sample apps
./gradlew :sample-desktop:run                          # Desktop
./gradlew :sample-web:wasmJsBrowserDevelopmentRun      # Web (localhost:8080)

# All platform tests
./gradlew :tessera-core:allTests
```

### Test Infrastructure

| Source Set | Framework | Tests | Description |
|-----------|-----------|-------|-------------|
| `commonTest` | kotlin.test | 19+ | ContentScale, ExifUtils, TileManager, Models |
| `androidUnitTest` | Robolectric + Compose UI Test | 17+ | Gesture integration, TesseraState lifecycle |
| `desktopTest` | JUnit + Skia runtime | 31+ | DesktopRegionDecoder, DesktopImageLoader, Platform |

```bash
# Run all tests
./gradlew :tessera-core:testDebugUnitTest   # Android (Robolectric)
./gradlew :tessera-core:desktopTest          # Desktop (JVM)
```

## Requirements

- **Android**: API 28+ (Android 9)
- **iOS**: iOS 15+
- **Desktop**: JDK 21+ (macOS, Windows, Linux)
- **Web**: Modern browser with WebGL + WebAssembly support
- **Kotlin**: 2.3.0+
- **Compose Multiplatform**: 1.8.0+
- **JDK**: 21+

## License

```
Copyright 2026 bentleypark

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
