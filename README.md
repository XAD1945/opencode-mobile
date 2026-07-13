# OpenCode Mobile

AI coding agent for Android. Run OpenCode directly from your phone or tablet with touch-optimized controls.

This is an unofficial mobile client for [OpenCode](https://github.com/anomalyco/opencode).

## Features

- Full OpenCode web UI in a native Android WebView
- Virtual keyboard bar with coding shortcuts (Ctrl, Alt, Tab, Esc, arrows)
- Gesture navigation (swipe for sessions and files)
- Split view for chat + code editing in landscape
- Termux integration for full terminal access
- Material Design 3 UI
- Dark/light theme support
- Share code from other apps directly to OpenCode

## Installation

### Option 1: Download APK

1. Go to [Releases](https://github.com/YOUR_USER/opencode-mobile/releases)
2. Download the latest `opencode-mobile-release.apk`
3. Install (enable "Unknown sources" if prompted)

### Option 2: Build from Source

```bash
git clone https://github.com/YOUR_USER/opencode-mobile.git
cd opencode-mobile
cd android
./gradlew assembleRelease
```

APK will be at `app/build/outputs/apk/release/`

### Option 3: Termux (Full Terminal Access)

Install Termux from [F-Droid](https://f-droid.org/packages/com.termux/), then:

```bash
curl -fsSL https://raw.githubusercontent.com/YOUR_USER/opencode-mobile/main/termux/install.sh | bash
```

This installs Node.js and OpenCode in Termux. Start the server:

```bash
oc-server 3000
```

Then open the Android app and connect to `http://127.0.0.1:3000`.

## Mobile Controls

### Virtual Keyboard Bar

The keyboard bar appears above your Android keyboard with:

| Key | Action |
|-----|--------|
| `Ctrl` | Toggle Ctrl modifier (stays active until tapped again) |
| `Alt` | Toggle Alt modifier |
| `Shift` | Toggle Shift modifier |
| `Tab` | Insert tab / trigger autocomplete |
| `Esc` | Send Escape key |
| `↑↓←→` | Arrow keys |
| `\|` | Open command palette |
| `/` | Open slash commands |

### Gestures

- **Swipe right**: Go back to session list
- **Swipe left**: Open file browser
- **Double tap**: Select word
- **Long press**: Context menu
- **Pinch**: Zoom code text

### Split View (Landscape)

In landscape mode, a toggle button appears in the top-right corner to split the screen between chat (top/left) and code editor (bottom/right). Drag the divider to resize.

## Configuration

### API Keys

Configure your API key in the app's setup wizard or Settings screen.

Supported providers:
- OpenAI
- Anthropic (Claude)
- Google Gemini
- Ollama (local)
- Custom (any OpenAI-compatible API)

### Configuration File (Termux)

Edit `~/.config/opencode/opencode.json`:

```json
{
  "$schema": "https://opencode.ai/config.json",
  "model": "anthropic/claude-sonnet-4-20250514",
  "default_agent": "build",
  "permissions": {
    "bash": "grant-permanent",
    "read": "grant-permanent",
    "write": "grant-permanent"
  }
}
```

## Architecture

```
opencode-mobile/
├── android/                  # Android app (Kotlin + Jetpack Compose)
│   ├── app/src/main/
│   │   ├── java/com/opencode/mobile/
│   │   │   ├── MainActivity.kt         # Setup wizard
│   │   │   ├── WebViewActivity.kt      # WebView + mobile UI
│   │   │   ├── TermuxBridge.kt         # Termux RUN_COMMAND integration
│   │   │   ├── OpenCodeService.kt      # Background server service
│   │   │   ├── SettingsActivity.kt     # Settings screen
│   │   │   └── FileShareReceiver.kt    # Share intent handler
│   │   └── res/                        # Android resources
│   └── build.gradle.kts
├── termux/                   # Termux integration scripts
│   ├── install.sh            # One-click Termux installer
│   ├── opencode-start.sh     # Server management script
│   └── config/opencode.json  # Default config for mobile
├── web-overlay/              # Mobile UI overlay (injected into WebView)
│   ├── mobile-controls.js    # Virtual keyboard bar
│   ├── mobile-theme.css      # Mobile-optimized styling
│   └── split-view.js         # Split panel layout
└── .github/workflows/        # CI/CD
    ├── build-apk.yml         # Build APK on push
    └── release.yml           # Create GitHub Release with APK
```

## Termux Commands

| Command | Description |
|---------|-------------|
| `oc` | Start OpenCode TUI |
| `oc-server [port]` | Start API server for the Android app |
| `oc-web [port]` | Start web interface |
| `oc-server start` | Start server in background |
| `oc-server stop` | Stop background server |
| `oc-server status` | Check if server is running |
| `oc-server logs` | View server logs |

## Permissions

| Permission | Purpose |
|------------|---------|
| INTERNET | Connect to OpenCode server |
| STORAGE | Access project files |
| NOTIFICATIONS | Server status alerts |
| Termux RUN_COMMAND | Send commands to Termux |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Credits

- [OpenCode](https://github.com/anomalyco/opencode) - The open source AI coding agent
- Built with Kotlin, Jetpack Compose, and Material Design 3

## License

MIT License - Same as OpenCode
