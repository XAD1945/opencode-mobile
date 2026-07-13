# Contributing to OpenCode Mobile

Thank you for your interest in contributing!

## Development Setup

1. Install Android Studio (latest stable)
2. Clone the repo
3. Open `android/` in Android Studio
4. Sync Gradle and run on emulator/device

## Building

```bash
cd android
./gradlew assembleDebug    # Debug build
./gradlew assembleRelease  # Release build
```

## Project Structure

- `android/` - Android app source code (Kotlin)
- `termux/` - Termux installer scripts (Bash)
- `web-overlay/` - WebView overlay scripts (JavaScript/CSS)
- `.github/workflows/` - CI/CD pipelines

## Code Style

- Follow Kotlin coding conventions
- Use Material Design 3 components
- Keep functions small and focused
- Add KDoc comments for public APIs

## Pull Requests

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Issues

Use GitHub Issues for bug reports and feature requests.

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
