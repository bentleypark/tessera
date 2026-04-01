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

# Run all tests
./gradlew :tessera-core:allTests
```

### Project Structure

```
tessera-core/src/
  commonMain/    # Shared code (TileManager, TesseraState, models)
  commonTest/    # Shared tests
  androidMain/   # Android implementation (BitmapRegionDecoder, Glide/Coil)
  androidUnitTest/ # Android-specific tests (Robolectric)
  iosMain/       # iOS implementation (Skia decoder, NSURLSession)
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

- Platform (Android/iOS) and OS version
- Image size and format
- Steps to reproduce
- Relevant logs

## License

By contributing, you agree that your contributions will be licensed under the Apache License 2.0.
