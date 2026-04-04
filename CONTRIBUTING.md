# Contributing to Tessera

Thank you for your interest in contributing to Tessera!

## Getting Started

1. Fork the repository
2. Clone your fork
3. Create a feature branch from `main`

## Development Setup

### Prerequisites

- JDK 21+
- Android SDK (API 36)
- Xcode 16+ (for iOS development)

### Build

```bash
# Android
./gradlew :tessera-core:assembleDebug

# iOS framework
./gradlew :tessera-core:linkDebugFrameworkIosSimulatorArm64

# Desktop
./gradlew :tessera-core:desktopJar

# Run all tests (Android + iOS + Desktop + Web)
./gradlew :tessera-core:allTests
```

### Sample Apps

```bash
# Android
./gradlew :sample:installDebug

# Desktop
./gradlew :sample-desktop:run

# Web
./gradlew :sample-web:wasmJsBrowserDevelopmentRun
```

### Project Structure

```
tessera-core/src/
  commonMain/       # Shared code (TileManager, TesseraState, models)
  commonTest/       # Shared tests
  androidMain/      # Android (BitmapRegionDecoder, NetworkImageLoader)
  androidUnitTest/  # Android tests (Robolectric + Compose UI)
  iosMain/          # iOS (CGImageSource + Skia decoder)
  desktopMain/      # Desktop JVM (ImageIO decoder)
  desktopTest/      # Desktop tests
  wasmJsMain/       # Web/Wasm (Skia Surface decoder, fetch API)
tessera-coil/       # Coil 3.x companion (Android + iOS)
tessera-glide/      # Glide companion (Android only)
```

## Pull Request Process

1. Run all tests before submitting: `./gradlew :tessera-core:allTests`
2. Keep PRs focused — one feature or fix per PR
3. Write tests for new functionality
4. Update documentation if the public API changes
5. Target the `main` branch

## Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `expect`/`actual` for platform-specific code
- Keep `commonMain` free of platform types
- Use `logError()`/`logWarning()` from `Platform.kt` instead of platform-specific logging

## Reporting Issues

When reporting bugs, include:

- Platform (Android/iOS/Desktop/Web) and OS version
- Image size and format
- Steps to reproduce
- Relevant logs (filter by `TesseraPerf` tag for performance issues)

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
