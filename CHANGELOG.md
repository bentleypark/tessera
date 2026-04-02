# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- **CGImageSource-based region decoder for iOS** (#25)
  - Subsample decoding with `kCGImageSourceSubsampleFactor` (1/2, 1/4, 1/8)
  - Skia tile extraction at ~1ms per tile
  - Memory protection: 30MP+ → subsample=2, 80MP+ → subsample=4
  - 108MP images on iPhone 7 (2GB RAM) with ~21MB memory
- **tessera-coil companion module** (#26)
  - Coil 3.x KMP image loader (Android + iOS)
  - Android: Coil + OkHttp, iOS: Coil + Ktor Darwin
  - Disk cache integration with SHA-256 file naming
- **HorizontalPager integration** (#28)
  - `enablePagerIntegration` parameter for TesseraImage
  - Custom gesture handler with selective event consumption
  - Zoomed-out horizontal swipe passes to Pager
  - Zoomed-in edge detection passes to Pager
  - Built-in double-tap zoom (no separate detectTapGestures)
- **Performance benchmarks** on iPhone 7 (iOS 15.8.5)
  - 4K: init 369ms, tile avg 1ms
  - 6K: init 892ms, tile avg 1ms
  - 8K: init 2053ms, tile avg 2ms
  - 108MP: init 1271ms, tile avg 2ms
- Sample apps with HorizontalPager gallery and 8K/108MP test images
- Unit tests for TileManager, TesseraModels, TesseraState (#22, #23, #24)
- 12 new tests for initializeDecoder, applyInitResult, decodeTile, cacheTile
- CI/CD with GitHub Actions for Android and iOS builds (#9)
- JitPack publishing configuration (#10)

### Changed
- **Package rename**: `com.naemomlab.tessera` → `com.github.bentleypark.tessera` (#27)
- **iOS decoder**: IosRegionDecoder (Skia full-load) → CgImageSourceRegionDecoder (subsample + Skia hybrid)
- **Thread safety**: TesseraState split into initializeDecoder/applyInitResult, decodeTile/cacheTile
- **Logging**: logError/logWarning/ioDispatcher changed to public for companion module access
- **iOS logging**: NSLog → println (NSLog caused app freezing on background threads)
- **Tile loading**: parallel async → sequential with ensureActive/yield for cancellation
- **Gesture handler**: detectTransformGestures → custom awaitEachGesture for Pager compatibility
- Timber logging unified across tessera-coil and tessera-glide

### Fixed
- iOS device deployment: restored embed:true framework config, STRIP_BITCODE_FROM_COPIED_FILES
- Compose SnapshotStateMap writes from background threads causing crashes
- Dispatcher starvation from parallel tile async
- LRU cache: loadTile() now correctly tracks access order for cached tiles
- SHA-256 for iOS temp file names (was hashCode, collision risk)

## [0.1.0] - 2026-04-01

### Added
- **Kotlin Multiplatform** project structure with Android and iOS targets
- **commonMain**: TileManager, TesseraState, TesseraModels, TesseraImageContent
- **Android**: BitmapRegionDecoder-based tile decoding, Glide image loader
- **iOS**: Skia-based tile decoding, NSURLSession image loader
- Tile-based rendering with configurable tile size (256px default)
- Multi-level zoom (0-3) with automatic sample size selection
- LRU tile cache with configurable max size (150 tiles default)
- Pinch-to-zoom, pan, and double-tap zoom gestures
- Drag-to-dismiss gesture support
- Preview bitmap for instant initial display
- Android sample app
- iOS sample app with SwiftUI integration

[Unreleased]: https://github.com/bentleypark/tessera/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/bentleypark/tessera/releases/tag/v0.1.0
