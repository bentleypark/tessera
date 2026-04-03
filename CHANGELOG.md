# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- **Desktop (JVM) platform support** (#16, #17)
  - `DesktopRegionDecoder` using `javax.imageio` with subsampled image cache + tile extraction
  - `DesktopImageLoader` with HTTP validation, atomic staging file, `Files.move()`
  - Mouse/trackpad gestures: Ctrl/Cmd+Scroll → zoom, plain scroll → pan, drag, double-click
  - EXIF orientation support via ImageIO metadata XML parsing
  - Desktop sample app (`sample-desktop`) with image selector and gesture guide
  - 31 Desktop unit tests (DesktopRegionDecoder, DesktopImageLoader, Platform)
- **Web (Wasm) platform support** (#34)
  - `WasmRegionDecoder` using Skia `Surface.makeRasterN32Premul` + `drawImageRect` tile extraction
  - `WasmImageLoader` using `fetch()` API via `kotlin.js.Promise` + `kotlinx.coroutines.await()`
  - Platform actuals: `console.error/warn`, `Date.now()`, `Dispatchers.Default`
  - Web sample app (`sample-web`) with `CanvasBasedWindow`
  - Kotlin/Wasm `js()` interop: comma operator pattern to avoid NPE from undefined returns
- **Maven Central publishing** (#31)
  - vanniktech maven-publish plugin with SonatypeHost.CENTRAL_PORTAL
  - In-memory GPG signing with Base64-encoded keys
  - Tag-based GitHub Actions release workflow
- **ReadMode with ContentScale** (#33)
  - `ContentScale` enum: Fit, FitWidth, FitHeight, Auto
  - Auto detection using 1.5x aspect ratio threshold
  - FitWidth for tall images (webtoons), FitHeight for wide images (panoramas)
- **Scroll indicators and minimap** (#32)
  - Thin scroll bars on right/bottom edges when zoomed
  - Minimap preview thumbnail with viewport rectangle in bottom-left
  - Fade animation: 1.5s hold → 0.5s fade-out
- **User-controlled rotation** (#36)
  - `rotation` parameter (0°/90°/180°/270°) on TesseraImage across all 4 platforms
  - `graphicsLayer { rotationZ }` with `clipToBounds()` for overflow prevention
  - Rotation buttons in all sample apps (Android, iOS, Desktop, Web)
  - 12 rotation UI tests (4 angles, gesture combos, normalization)
- **Format-based large image warning** (#35)
  - `ImageFormat` enum with `fromMimeType()` detection (JPEG, PNG, WEBP, GIF, UNKNOWN)
  - 30MP+ non-JPEG images trigger `logWarning` about potential OOM
  - Replaced ineffective PNG subsample fallback (PNG/TIFF decode full image internally)
  - 10 ImageFormat unit tests
- **Compose UI test infrastructure** (#29)
  - 17 gesture integration tests using Robolectric + `createComposeRule()`
  - FakeImageLoader, fakeDecoderFactory for isolated testing
  - Tests: double-tap zoom, dismiss gesture, pager swipes, ContentScale modes, error states
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
- CI/CD with GitHub Actions for Android and iOS builds (#9)
- JitPack publishing configuration (#10)

### Changed
- **Group ID**: `com.github.bentleypark.tessera` → `io.github.bentleypark` for Maven Central
- **Build targets**: Added `jvm("desktop")` and `wasmJs { browser() }` to tessera-core
- **.gitignore**: Individual `/module/build` patterns → global `**/build/`
- **`repositoriesMode`**: `FAIL_ON_PROJECT_REPOS` → `PREFER_PROJECT` (required by Kotlin/Wasm Node.js/yarn repos)
- **Sample apps**: Streamlined from 14 to 7 test images (2K, 4K, 108MP, EXIF 90°, PNG, FitWidth, Auto)
- **Scroll gesture**: Added `isZoomModifierPressed` expect/actual for Desktop Ctrl/Cmd+Scroll zoom
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

[Unreleased]: https://github.com/bentleypark/tessera/commits/main
