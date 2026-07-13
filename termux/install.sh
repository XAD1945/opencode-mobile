#!/bin/bash
# OpenCode Mobile - Termux Installer
# Usage: curl -fsSL https://raw.githubusercontent.com/YOUR_USER/opencode-mobile/main/termux/install.sh | bash

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

print_banner() {
    echo -e "${BLUE}"
    echo "  ╔══════════════════════════════════════╗"
    echo "  ║       OpenCode Mobile - Installer    ║"
    echo "  ║       AI Coding Agent for Android    ║"
    echo "  ╚══════════════════════════════════════╝"
    echo -e "${NC}"
}

log_info()    { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[OK]${NC} $1"; }
log_warn()    { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error()   { echo -e "${RED}[ERROR]${NC} $1"; }

check_termux() {
    if [ -z "$TERMUX_VERSION" ]; then
        log_error "This script must be run inside Termux!"
        log_info "Install Termux from F-Droid: https://f-droid.org/packages/com.termux/"
        exit 1
    fi
    log_success "Termux detected (v${TERMUX_VERSION})"
}

update_packages() {
    log_info "Updating package lists..."
    pkg update -y 2>/dev/null || apt update -y
    log_success "Packages updated"
}

install_nodejs() {
    if command -v node &>/dev/null; then
        NODE_VERSION=$(node --version)
        log_success "Node.js already installed: ${NODE_VERSION}"
    else
        log_info "Installing Node.js v22..."
        pkg install -y nodejs 2>/dev/null || apt install -y nodejs
        log_success "Node.js installed: $(node --version)"
    fi
}

install_opencode() {
    log_info "Installing OpenCode..."
    
    if command -v opencode &>/dev/null; then
        log_info "OpenCode found, updating..."
        npm update -g opencode-ai 2>/dev/null || true
    else
        npm install -g opencode-ai
    fi
    
    log_success "OpenCode installed: $(opencode --version 2>/dev/null || echo 'installed')"
}

install_optional_deps() {
    log_info "Installing optional dependencies..."
    pkg install -y git curl wget 2>/dev/null || apt install -y git curl wget
    log_success "Optional dependencies installed"
}

setup_config() {
    local CONFIG_DIR="$HOME/.config/opencode"
    local CONFIG_FILE="$CONFIG_DIR/opencode.json"
    
    mkdir -p "$CONFIG_DIR"
    
    if [ ! -f "$CONFIG_FILE" ]; then
        log_info "Creating default configuration..."
        cat > "$CONFIG_FILE" << 'CONFIGEOF'
{
  "$schema": "https://opencode.ai/config.json",
  "model": "anthropic/claude-sonnet-4-20250514",
  "default_agent": "build",
  "shell": "/data/data/com.termux/files/usr/bin/bash",
  "autoupdate": true,
  "share": "manual",
  "permissions": {
    "bash": "grant-permanent",
    "read": "grant-permanent",
    "write": "grant-permanent",
    "edit": "grant-permanent"
  }
}
CONFIGEOF
        log_success "Default config created at $CONFIG_FILE"
    else
        log_info "Config already exists at $CONFIG_FILE"
    fi
}

setup_storage() {
    log_info "Setting up storage access..."
    termux-setup-storage 2>/dev/null || true
    log_success "Storage access configured"
}

create_shortcuts() {
    log_info "Creating shortcut scripts..."
    
    cat > "$PREFIX/bin/oc" << 'SHEOF'
#!/bin/bash
opencode "$@"
SHEOF
    chmod +x "$PREFIX/bin/oc"
    
    cat > "$PREFIX/bin/oc-server" << 'SHEOF'
#!/bin/bash
PORT=${1:-3000}
echo "Starting OpenCode server on port $PORT..."
echo "Connect from the app: http://127.0.0.1:$PORT"
opencode serve --port "$PORT" --hostname 127.0.0.1
SHEOF
    chmod +x "$PREFIX/bin/oc-server"
    
    cat > "$PREFIX/bin/oc-web" << 'SHEOF'
#!/bin/bash
PORT=${1:-3000}
echo "Starting OpenCode web interface on port $PORT..."
echo "Open in browser: http://127.0.0.1:$PORT"
opencode web --port "$PORT" --hostname 127.0.0.1
SHEOF
    chmod +x "$PREFIX/bin/oc-web"
    
    log_success "Shortcuts created: oc, oc-server, oc-web"
}

print_usage() {
    echo ""
    echo -e "${GREEN}Installation complete!${NC}"
    echo ""
    echo "Quick commands:"
    echo "  oc              Start OpenCode TUI"
    echo "  oc-server       Start API server (for the Android app)"
    echo "  oc-web          Start web interface"
    echo ""
    echo "To start the server for the Android app:"
    echo "  oc-server 3000"
    echo ""
    echo "Then open OpenCode Mobile app and connect to:"
    echo "  http://127.0.0.1:3000"
    echo ""
    echo "Configuration: ~/.config/opencode/opencode.json"
    echo ""
}

main() {
    print_banner
    check_termux
    update_packages
    install_optional_deps
    install_nodejs
    install_opencode
    setup_config
    setup_storage
    create_shortcuts
    print_usage
}

main "$@"
