# CLAUDE.md

Guidelines for Claude Code when working in the tessera repository.

## Project Overview
- **Project**: Tessera — Compose Multiplatform tile-based high-resolution image viewer
- **GitHub**: bentleypark/tessera
- **License**: Apache 2.0
- **Distribution**: Maven Central (`io.github.bentleypark:tessera-core:<TAG>`)

## Work Rules
- **Always respond in Korean** unless explicitly asked otherwise
- **Commit messages in English**
- Do not git commit/push without explicit approval
- Do not run builds automatically — only when the user explicitly requests
- Analysis/planning/review requests should produce text output only, no code changes
- Check `gh issue list` at the start of work to understand current tasks
- Update progress via `gh issue comment`

## Development Workflow
Follow this order for every code change:
1. **Issue check** — Verify issue acceptance criteria and checklist before starting
2. **Code** — Implement the change
3. **Test** — Write or update unit tests
4. **Review** — Run code review (`/pr-review-toolkit:review-pr`)
5. **Local verification** — User confirms build/functionality locally
6. **Commit** — Only after user approval
7. **Issue update** — Close issue or update progress via `gh issue comment`

## Module Structure

```
tessera/
├── tessera-core/          # KMP core library (Android + iOS + Desktop + Web)
│   ├── src/commonMain/    # Shared code (TileManager, TesseraState, models)
│   ├── src/commonTest/    # Shared tests
│   ├── src/androidMain/   # Android impl (BitmapRegionDecoder, NetworkImageLoader)
│   ├── src/androidUnitTest/ # Android tests (Robolectric)
│   ├── src/iosMain/       # iOS impl (CgImageSource decoder, NSData loader)
│   ├── src/desktopMain/   # Desktop/JVM impl (ImageIO decoder, HTTP/file loader)
│   ├── src/desktopTest/   # Desktop tests
│   └── src/wasmJsMain/    # Web/Wasm impl (Skia decoder, HTTP loader)
├── tessera-glide/         # Glide companion module (Android only)
├── tessera-coil/          # Coil 3.x companion module (Android + iOS)
├── sample/                # Android sample app
├── sample-desktop/        # Desktop sample app (Compose Desktop)
├── sample-web/            # Web sample app (Wasm/JS)
└── iosApp/                # iOS sample app (SwiftUI + Compose)
```

## Build & Test Commands

### Android
```bash
./gradlew :tessera-core:assembleDebug          # Build library
./gradlew :tessera-glide:assembleDebug         # Build Glide module
./gradlew :sample:assembleDebug                # Build sample app
./gradlew :tessera-core:testDebugUnitTest      # Run all tests
./gradlew :tessera-core:publishToMavenLocal    # Verify Maven Central publishing
```

### iOS
```bash
./gradlew :tessera-core:linkDebugFrameworkIosSimulatorArm64  # Simulator
./gradlew :tessera-core:linkDebugFrameworkIosArm64           # Device
./gradlew :tessera-core:iosSimulatorArm64Test                # iOS tests
cd iosApp && xcodegen generate                                # Xcode project
```

### Desktop
```bash
./gradlew :sample-desktop:run                        # Run desktop sample
./gradlew :sample-desktop:packageUberJarForCurrentOS # Fat JAR
./gradlew :sample-desktop:packageDmg                 # macOS DMG
```

### Web
```bash
./gradlew :sample-web:wasmJsBrowserDevelopmentRun    # Dev server
./gradlew :sample-web:wasmJsBrowserProductionWebpack # Production build
```

### All Platforms
```bash
./gradlew :tessera-core:allTests
```

## Tech Stack

| Item | Version |
|------|---------|
| Kotlin | 2.3.0 |
| Compose Multiplatform | 1.8.0 |
| AGP | 9.1.0 |
| JDK | 21+ |
| Android minSdk | 28 |
| Android compileSdk | 36 |
| iOS deployment target | 15.0+ |

## Architecture

### expect/actual Pattern
Platform-specific implementations are separated via `expect`/`actual`:
- `Platform.kt` — logging (`logError`, `logWarning`), `currentTimeMillis()`, `ioDispatcher`
- `ImageSource.kt` — Android: FileSource/ResourceSource, iOS: PathSource/DataSource, Desktop: FileSource, Web: DataSource
- `RegionDecoder` — interface with platform implementations:
  - Android: `BitmapRegionDecoder` (partial decoding)
  - iOS: `CgImageSourceRegionDecoder` (CGImageSource + Skia, subsample cache)
  - Desktop: `DesktopRegionDecoder` (ImageIO, subsample cache)
  - Web: `WasmRegionDecoder` (Skia, full image decode)

### Public State API
- `TesseraViewerState` — public read-only state holder (`@Stable`, `internal set`)
- `rememberTesseraState()` — Composable factory (follows `rememberLazyListState()` convention)
- `TesseraViewerState.sync()` — internal bulk update (called from SideEffect on main thread)
- `syncViewerState()` — extracted testable function for state synchronization
- State sync runs in `SideEffect { }` block (not during composition)
- Optional `state` parameter on all platform `TesseraImage` wrappers

### No Platform Types in commonMain
- `android.graphics.Rect` → `TileRect`
- `android.graphics.Bitmap` → `ImageBitmap`
- `Dispatchers.IO` → `ioDispatcher` (expect/actual)
- `Timber` → `logError()` / `logWarning()`
- `System.currentTimeMillis()` → `currentTimeMillis()`

### Image Loading
Platform-specific `ImageLoaderStrategy` implementations:
- **Android**: `NetworkImageLoader` (http/https), `ResourceImageLoader` (android.resource://)
- **iOS**: `IosImageLoader` (NSData.dataWithContentsOfURL)
- **Desktop**: `DesktopImageLoader` (http/https + file://)
- **Web**: `WasmImageLoader` (http/https)

Companion modules (optional):
- `tessera-glide` — GlideImageLoader (Android only, file/content URI)
- `tessera-coil` — CoilImageLoader (Android + iOS KMP, Coil 3.x)

tessera-core has zero external image library dependencies.

### Tile Cache
- LRU-based, `maxCacheSize` auto-scales inversely with tile size: `(150 * 256² / tileSize²).coerceAtLeast(50)`
- Tile size is dynamic: `(256 * displayDensity).coerceIn(256, 512)` via `LocalDensity`
- `loadTile()` calls `updateAccessOrder()` to maintain LRU order
- `evictLRUIfNeeded()` removes the oldest tile when cache is full
- Android JPEG tiles use `RGB_565` (2 bytes/pixel); PNG/alpha formats use `ARGB_8888`
- `cachedTileCount` tracked via separate `mutableIntStateOf` (avoids SnapshotStateMap over-subscription)

### Android Decoder Pool
- `BitmapRegionDecoder` pool (default 2 instances) for parallel tile decoding
- Each instance retains full encoded image data — pool sizes above 2-3 risk OOM on low-RAM devices
- `ArrayBlockingQueue` with `poll(5s)` timeout prevents deadlock on `close()`
- `isClosed` flag ensures in-flight decoders are recycled on return

### Zoom Levels
| Level | Scale | Sample Size | Note |
|-------|-------|-------------|------|
| 0 | 1.0x–1.5x | 2 (half resolution) | Tiles skipped when viewport covers full image (preview sufficient) |
| 1 | 1.5x–3.0x | 1 (full) | First level where tiles actually load |
| 2 | 3.0x–6.0x | 1 | |
| 3 | 6.0x+ | 1 | |

## Code Style
- Follow Kotlin official coding conventions
- Use `@Volatile` (`kotlin.concurrent.Volatile`) for thread safety
- Always rethrow `CancellationException`
- No `printStackTrace()` — use `logError()` instead
- SHA-256 hash for cache file names (avoid hashCode collisions)
- Atomic temp file pattern for network downloads (staging file → rename)

## Performance Profiling
- `TesseraPerf` log tag for init/tile loading timing
- Slow tile warning logged when >50ms
- iOS: Use Xcode Instruments (Allocations, Core Animation)

## Known Limitations
- **WasmRegionDecoder**: Loads entire image into memory (no partial decoding in Wasm/Skia)
- **Web tests**: No wasmJsTest source set yet (Desktop/Android/iOS have tests)
- **CI/CD coverage**: Desktop and Web builds are not tested in GitHub Actions
- **Configuration cache**: Disabled due to AGP 9 + KMP compatibility

## CI/CD
- GitHub Actions on push to main and PRs
- Android: ubuntu-latest, JDK 21 (build + unit tests)
- iOS: macos-15, JDK 21 (framework link + tests)
- Desktop/Web: not yet included in CI
- Maven Central: tag-based publishing via release.yml (vanniktech maven-publish plugin)
