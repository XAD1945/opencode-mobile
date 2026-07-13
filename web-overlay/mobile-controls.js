/**
 * OpenCode Mobile - Virtual Keyboard Bar
 * Adds coding-specific keyboard shortcuts for mobile devices
 */

(function() {
    'use strict';

    const STYLE_ID = 'oc-mobile-kb-style';
    const BAR_ID = 'oc-mobile-keyboard-bar';

    const KEYS = [
        { id: 'ctrl', label: 'Ctrl', toggle: true },
        { id: 'alt', label: 'Alt', toggle: true },
        { id: 'shift', label: 'Shift', toggle: true },
        { id: 'tab', label: 'Tab', keys: ['Tab'] },
        { id: 'esc', label: 'Esc', keys: ['Escape'] },
        { id: 'up', label: '\u2191', keys: ['ArrowUp'] },
        { id: 'down', label: '\u2193', keys: ['ArrowDown'] },
        { id: 'left', label: '\u2190', keys: ['ArrowLeft'] },
        { id: 'right', label: '\u2192', keys: ['ArrowRight'] },
        { id: 'pipe', label: '|', keys: ['|'] },
        { id: 'slash', label: '/', keys: ['/'] }
    ];

    let activeModifiers = { ctrl: false, alt: false, shift: false };

    function injectStyles() {
        if (document.getElementById(STYLE_ID)) return;
        const style = document.createElement('style');
        style.id = STYLE_ID;
        style.textContent = `
            #${BAR_ID} {
                position: fixed;
                bottom: 0;
                left: 0;
                right: 0;
                height: 48px;
                background: #1a1a2e;
                border-top: 1px solid rgba(255,255,255,0.1);
                display: flex;
                align-items: center;
                justify-content: center;
                gap: 4px;
                padding: 0 8px;
                z-index: 99999;
                overflow-x: auto;
                -webkit-overflow-scrolling: touch;
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
            }
            #${BAR_ID} .kb-btn {
                flex-shrink: 0;
                height: 36px;
                min-width: 40px;
                padding: 0 10px;
                border: 1px solid rgba(255,255,255,0.2);
                border-radius: 6px;
                background: rgba(255,255,255,0.05);
                color: #e0e0e0;
                font-size: 13px;
                font-weight: 500;
                cursor: pointer;
                transition: all 0.15s ease;
                user-select: none;
                -webkit-user-select: none;
                display: flex;
                align-items: center;
                justify-content: center;
            }
            #${BAR_ID} .kb-btn:active {
                background: rgba(255,255,255,0.15);
                transform: scale(0.95);
            }
            #${BAR_ID} .kb-btn.active {
                background: #7C4DFF;
                border-color: #7C4DFF;
                color: #fff;
                box-shadow: 0 0 8px rgba(124,77,255,0.4);
            }
            #${BAR_ID} .kb-divider {
                width: 1px;
                height: 24px;
                background: rgba(255,255,255,0.15);
                flex-shrink: 0;
                margin: 0 4px;
            }
            @media (prefers-color-scheme: light) {
                #${BAR_ID} {
                    background: #f5f5f5;
                    border-top-color: rgba(0,0,0,0.1);
                }
                #${BAR_ID} .kb-btn {
                    color: #333;
                    border-color: rgba(0,0,0,0.15);
                    background: rgba(0,0,0,0.03);
                }
                #${BAR_ID} .kb-btn.active {
                    background: #6200EA;
                    border-color: #6200EA;
                    color: #fff;
                }
                #${BAR_ID} .kb-divider {
                    background: rgba(0,0,0,0.12);
                }
            }
        `;
        document.head.appendChild(style);
    }

    function createKeyButton(key) {
        const btn = document.createElement('button');
        btn.className = 'kb-btn';
        btn.id = `kb-${key.id}`;
        btn.textContent = key.label;
        btn.setAttribute('aria-label', key.label);
        btn.setAttribute('role', 'button');

        btn.addEventListener('touchstart', function(e) {
            e.preventDefault();
            handleKeyPress(key);
        }, { passive: false });

        btn.addEventListener('mousedown', function(e) {
            e.preventDefault();
            handleKeyPress(key);
        });

        return btn;
    }

    function handleKeyPress(key) {
        const activeEl = document.activeElement;
        if (!activeEl) return;

        if (key.toggle) {
            activeModifiers[key.id] = !activeModifiers[key.id];
            const btn = document.getElementById(`kb-${key.id}`);
            btn.classList.toggle('active', activeModifiers[key.id]);
            return;
        }

        const keyEvent = {
            key: key.keys[0],
            code: key.keys[0],
            ctrlKey: activeModifiers.ctrl,
            altKey: activeModifiers.alt,
            shiftKey: activeModifiers.shift,
            bubbles: true,
            cancelable: true
        };

        if (key.keys[0] === 'Tab') {
            keyEvent.key = 'Tab';
            keyEvent.code = 'Tab';
        }

        activeEl.dispatchEvent(new KeyboardEvent('keydown', keyEvent));
        activeEl.dispatchEvent(new KeyboardEvent('keyup', keyEvent));

        if (activeModifiers.ctrl || activeModifiers.alt) {
            if (key.id !== 'ctrl' && key.id !== 'alt' && key.id !== 'shift') {
                activeModifiers.ctrl = false;
                activeModifiers.alt = false;
                document.getElementById('kb-ctrl')?.classList.remove('active');
                document.getElementById('kb-alt')?.classList.remove('active');
            }
        }

        if (navigator.vibrate) {
            navigator.vibrate(10);
        }
    }

    function createBar() {
        if (document.getElementById(BAR_ID)) return;
        injectStyles();

        const bar = document.createElement('div');
        bar.id = BAR_ID;
        bar.setAttribute('role', 'toolbar');
        bar.setAttribute('aria-label', 'Keyboard shortcuts');

        KEYS.forEach((key, index) => {
            if (index > 0 && (key.id === 'tab' || key.id === 'pipe')) {
                const divider = document.createElement('div');
                divider.className = 'kb-divider';
                bar.appendChild(divider);
            }
            bar.appendChild(createKeyButton(key));
        });

        document.body.appendChild(bar);
    }

    function init() {
        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', createBar);
        } else {
            createBar();
        }
    }

    init();
})();
