#!/bin/bash
# OpenCode Mobile - Server Start Script
# Starts the OpenCode server in the background for the Android app

set -e

PORT=${1:-3000}
HOST="127.0.0.1"
LOG_FILE="/tmp/opencode-server.log"
PID_FILE="/tmp/opencode-server.pid"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

stop_server() {
    if [ -f "$PID_FILE" ]; then
        local PID=$(cat "$PID_FILE")
        if kill -0 "$PID" 2>/dev/null; then
            echo -e "${YELLOW}[STOP]${NC} Stopping existing server (PID: $PID)..."
            kill "$PID" 2>/dev/null || true
            sleep 1
        fi
        rm -f "$PID_FILE"
    fi
    
    pkill -f "opencode serve" 2>/dev/null || true
}

start_server() {
    stop_server
    
    echo -e "${BLUE}[START]${NC} Starting OpenCode server..."
    echo -e "  Port: ${PORT}"
    echo -e "  Host: ${HOST}"
    echo -e "  Log:  ${LOG_FILE}"
    
    nohup opencode serve --port "$PORT" --hostname "$HOST" > "$LOG_FILE" 2>&1 &
    echo $! > "$PID_FILE"
    
    echo -e "${GREEN}[OK]${NC} Server starting (PID: $(cat $PID_FILE))"
    
    echo -n "Waiting for server"
    for i in $(seq 1 15); do
        sleep 1
        echo -n "."
        if curl -s "http://${HOST}:${PORT}/health" >/dev/null 2>&1; then
            echo ""
            echo -e "${GREEN}[OK]${NC} Server is ready!"
            echo ""
            echo -e "Connect from OpenCode Mobile app:"
            echo -e "  ${BLUE}http://127.0.0.1:${PORT}${NC}"
            return 0
        fi
    done
    
    echo ""
    echo -e "${YELLOW}[WARN]${NC} Server may still be starting..."
    echo -e "Check logs: tail -f ${LOG_FILE}"
    return 0
}

status_server() {
    if [ -f "$PID_FILE" ]; then
        local PID=$(cat "$PID_FILE")
        if kill -0 "$PID" 2>/dev/null; then
            echo -e "${GREEN}[STATUS]${NC} Server running (PID: $PID) on port ${PORT}"
            return 0
        fi
    fi
    echo -e "${RED}[STATUS]${NC} Server not running"
    return 1
}

case "${1:-start}" in
    start)
        start_server
        ;;
    stop)
        stop_server
        echo -e "${GREEN}[OK]${NC} Server stopped"
        ;;
    restart)
        stop_server
        start_server
        ;;
    status)
        status_server
        ;;
    logs)
        tail -f "$LOG_FILE"
        ;;
    *)
        echo "Usage: $0 {start|stop|restart|status|logs} [port]"
        exit 1
        ;;
esac
