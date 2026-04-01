# CLAUDE.md

Guidelines for Claude Code when working in the tessera repository.

## Project Overview
- **Project**: Tessera — Compose Multiplatform tile-based high-resolution image viewer
- **GitHub**: bentleypark/tessera
- **License**: Apache 2.0
- **Distribution**: JitPack (`com.github.bentleypark.tessera:tessera-core:<TAG>`)

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
├── tessera-core/          # KMP core library (Android + iOS)
│   ├── src/commonMain/    # Shared code (TileManager, TesseraState, models)
│   ├── src/commonTest/    # Shared tests
│   ├── src/androidMain/   # Android impl (BitmapRegionDecoder, NetworkImageLoader)
│   ├── src/androidUnitTest/ # Android tests (Robolectric)
│   └── src/iosMain/       # iOS impl (Skia decoder, NSData loader)
├── tessera-glide/         # Glide companion module (Android only)
├── sample/                # Android sample app
└── iosApp/                # iOS sample app (SwiftUI + Compose)
```

## Build & Test Commands

### Android
```bash
./gradlew :tessera-core:assembleDebug          # Build library
./gradlew :tessera-glide:assembleDebug         # Build Glide module
./gradlew :sample:assembleDebug                # Build sample app
./gradlew :tessera-core:testDebugUnitTest      # Run all tests
./gradlew :tessera-core:publishToMavenLocal    # Verify JitPack publishing
```

### iOS
```bash
./gradlew :tessera-core:linkDebugFrameworkIosSimulatorArm64  # Simulator
./gradlew :tessera-core:linkDebugFrameworkIosArm64           # Device
./gradlew :tessera-core:iosSimulatorArm64Test                # iOS tests
cd iosApp && xcodegen generate                                # Xcode project
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
- `ImageSource.kt` — Android: FileSource/ResourceSource, iOS: PathSource/DataSource
- `RegionDecoder` — interface with platform implementations (Android: BitmapRegionDecoder, iOS: Skia)

### No Platform Types in commonMain
- `android.graphics.Rect` → `TileRect`
- `android.graphics.Bitmap` → `ImageBitmap`
- `Dispatchers.IO` → `ioDispatcher` (expect/actual)
- `Timber` → `logError()` / `logWarning()`
- `System.currentTimeMillis()` → `currentTimeMillis()`

### Image Loading
```
RoutingImageLoader (routing by URI scheme)
├── NetworkImageLoader (http/https — java.net.URL download)
├── ResourceImageLoader (android.resource:// — ContentResolver)
└── [optional] GlideImageLoader (tessera-glide, file/content URI)
```

- tessera-core has zero external image library dependencies
- Glide is separated into the `tessera-glide` companion module
- iOS uses `IosImageLoader` (NSData.dataWithContentsOfURL)

### Tile Cache
- LRU-based (`maxCacheSize` default: 150)
- `loadTile()` calls `updateAccessOrder()` to maintain LRU order
- `evictLRUIfNeeded()` removes the oldest tile when cache is full

### Zoom Levels
| Level | Scale | Sample Size |
|-------|-------|-------------|
| 0 | 1.0x–1.5x | 2 (half resolution) |
| 1 | 1.5x–3.0x | 1 (full) |
| 2 | 3.0x–6.0x | 1 |
| 3 | 6.0x+ | 1 |

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
- **IosRegionDecoder**: Loads entire image into memory (no CGImageSource partial decoding yet)
- **Desktop**: Planned for Phase 4 (not implemented)
- **Configuration cache**: Disabled due to AGP 9 + KMP compatibility

## CI/CD
- GitHub Actions on push to main and PRs
- Android: ubuntu-latest, JDK 21
- iOS: macos-15, JDK 21
- JitPack: tag-based publishing
