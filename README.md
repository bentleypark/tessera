# Tessera

> **Tessera** (Latin: "small tile piece") — A Compose Multiplatform tile-based high-resolution image viewer

[![CI](https://github.com/bentleypark/tessera/actions/workflows/ci.yml/badge.svg)](https://github.com/bentleypark/tessera/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/bentleypark/tessera.svg)](https://jitpack.io/#bentleypark/tessera)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## Overview

Tessera is a memory-efficient image viewer for Compose Multiplatform that uses tile-based rendering to display large, high-resolution images without loading the entire image into memory.

### Supported Platforms

| Platform | Status |
|----------|--------|
| Android  | Stable |
| iOS      | Beta   |
| Desktop  | Planned |

### Key Features

- **Memory Efficient** — Only loads visible tiles, using 90-98% less memory than full-image loading
- **Smooth Gestures** — 60fps pinch-to-zoom, pan, and double-tap zoom
- **Compose Native** — Built for Compose Multiplatform (not a View wrapper)
- **LRU Tile Cache** — Automatic eviction with configurable cache size
- **Multiple Image Sources** — Network URLs, local files, content URIs, resources

## Quick Start

### Installation

Add JitPack repository to your `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        maven("https://jitpack.io")
    }
}
```

Add dependency:

```kotlin
// Kotlin Multiplatform (commonMain)
implementation("com.github.bentleypark.tessera:tessera-core:<version>")

// Android only
implementation("com.github.bentleypark.tessera:tessera-core-android:<version>")
```

### Android Usage

```kotlin
@Composable
fun MyScreen() {
    // Network image
    TesseraImage(
        imageUrl = "https://example.com/large-image.jpg",
        modifier = Modifier.fillMaxSize()
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
@Composable
fun MyScreen() {
    TesseraImage(
        imageUrl = "https://example.com/large-image.jpg",
        modifier = Modifier.fillMaxSize()
    )
}
```

## Performance

| Image Size | Full Load | Tessera (Peak) | Tessera (Typical) |
|------------|-----------|----------------|-------------------|
| 4K (8MP)   | ~32MB     | ~12MB          | ~8-10MB           |
| 8K (33MP)  | ~127MB    | ~18MB          | ~10-14MB          |
| 108MP      | ~432MB    | ~42MB          | ~12-18MB          |

- **Tile Cache**: 150 tiles x ~256KB = Max ~38MB
- **Preview Image**: ~4MB (1024px, high quality)

## Architecture

```
TesseraImage (Composable)       <- User-facing API
  |-- Gesture detection
  |-- Canvas tile rendering
  v
TesseraState                    <- State + LRU Cache
  |-- Viewport tracking
  |-- Tile lifecycle
  v
TileManager                     <- Grid Calculation
  |-- Zoom level (0-3)
  |-- Visible tile selection
  v
RegionDecoder (expect/actual)   <- Platform Decoding
  |-- Android: BitmapRegionDecoder
  |-- iOS: Skia Image + Canvas
```

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
| `contentDescription` | String? | `null` | Accessibility description |
| `enableDismissGesture` | Boolean | `false` | Vertical drag-to-dismiss |
| `onDismiss` | () -> Unit | `{}` | Dismiss callback |

### Android-only: Image Loaders

Tessera uses `RoutingImageLoader` by default:

| URI Scheme | Loader | Use Case |
|------------|--------|----------|
| `http://`, `https://` | Coil | Network images |
| `file://`, `content://` | Glide | Local files, gallery |
| `android.resource://` | Resource | Bundled images |

> **Note**: Glide and Coil are `compileOnly` dependencies. Add them to your app module.

## Building

```bash
# Android build + tests
./gradlew :tessera-core:testDebugUnitTest

# iOS framework
./gradlew :tessera-core:linkDebugFrameworkIosSimulatorArm64

# iOS tests
./gradlew :tessera-core:iosSimulatorArm64Test

# All platforms
./gradlew :tessera-core:allTests
```

## Requirements

- **Android**: API 28+ (Android 9)
- **iOS**: iOS 15+
- **Kotlin**: 2.3.0+
- **Compose Multiplatform**: 1.8.0+

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
