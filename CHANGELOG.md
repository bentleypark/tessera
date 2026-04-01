# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Unit tests for TileManager, TesseraModels, TesseraState (#22, #23, #24)
- CI/CD with GitHub Actions for Android and iOS builds (#9)
- JitPack publishing configuration (#10)

### Fixed
- LRU cache: `loadTile()` now correctly tracks access order for newly cached tiles

## [0.1.0] - 2026-04-01

### Added
- **Kotlin Multiplatform** project structure with Android and iOS targets
- **commonMain**: TileManager, TesseraState, TesseraModels, TesseraImageContent
- **Android**: BitmapRegionDecoder-based tile decoding, Glide/Coil image loaders
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
