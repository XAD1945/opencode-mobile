/**
 * OpenCode Mobile - Split View
 * Enables split-screen layout for chat + code editing
 */

(function() {
    'use strict';

    const STYLE_ID = 'oc-split-view-style';
    const CONTAINER_ID = 'oc-split-container';
    const DIVIDER_ID = 'oc-split-divider';

    let isLandscape = false;
    let splitRatio = 0.5;
    let isDragging = false;

    function injectStyles() {
        if (document.getElementById(STYLE_ID)) return;
        const style = document.createElement('style');
        style.id = STYLE_ID;
        style.textContent = `
            .oc-split-active #${CONTAINER_ID} {
                display: flex;
                flex-direction: row;
                height: 100vh;
                overflow: hidden;
            }
            .oc-split-active .oc-pane-chat {
                flex: 0 0 ${splitRatio * 100}%;
                overflow-y: auto;
                border-right: 1px solid rgba(128,128,128,0.2);
            }
            .oc-split-active .oc-pane-code {
                flex: 1;
                overflow: auto;
                background: #1e1e1e;
            }
            #${DIVIDER_ID} {
                display: none;
                width: 6px;
                cursor: col-resize;
                background: rgba(124,77,255,0.3);
                position: relative;
                z-index: 10;
                touch-action: none;
            }
            #${DIVIDER_ID}:hover,
            #${DIVIDER_ID}.active {
                background: rgba(124,77,255,0.7);
            }
            #${DIVIDER_ID}::after {
                content: '';
                position: absolute;
                top: 50%;
                left: 50%;
                transform: translate(-50%, -50%);
                width: 2px;
                height: 40px;
                background: rgba(255,255,255,0.4);
                border-radius: 1px;
            }
            .oc-split-active #${DIVIDER_ID} {
                display: block;
            }
            .oc-split-toggle {
                position: fixed;
                top: 12px;
                right: 12px;
                width: 40px;
                height: 40px;
                border-radius: 8px;
                border: 1px solid rgba(128,128,128,0.3);
                background: rgba(30,30,30,0.9);
                color: #fff;
                font-size: 18px;
                cursor: pointer;
                z-index: 99998;
                display: flex;
                align-items: center;
                justify-content: center;
                backdrop-filter: blur(10px);
            }
            .oc-split-toggle.active {
                background: #7C4DFF;
            }
            @media (orientation: portrait) {
                .oc-split-toggle {
                    display: none !important;
                }
                .oc-split-active #${CONTAINER_ID} {
                    flex-direction: column !important;
                }
                .oc-split-active .oc-pane-chat {
                    flex: 0 0 50% !important;
                    border-right: none !important;
                    border-bottom: 1px solid rgba(128,128,128,0.2);
                }
                #${DIVIDER_ID} {
                    width: 100% !important;
                    height: 6px !important;
                    cursor: row-resize !important;
                }
            }
        `;
        document.head.appendChild(style);
    }

    function createToggleButton() {
        const btn = document.createElement('button');
        btn.className = 'oc-split-toggle';
        btn.innerHTML = '\u231C';
        btn.title = 'Toggle split view';
        btn.addEventListener('click', toggleSplit);
        document.body.appendChild(btn);
    }

    function createDivider() {
        const divider = document.createElement('div');
        divider.id = DIVIDER_ID;

        divider.addEventListener('mousedown', startDrag);
        divider.addEventListener('touchstart', startDrag, { passive: false });

        document.addEventListener('mousemove', onDrag);
        document.addEventListener('touchmove', onDrag, { passive: false });
        document.addEventListener('mouseup', endDrag);
        document.addEventListener('touchend', endDrag);

        return divider;
    }

    function toggleSplit() {
        const isLandscapeMatch = window.matchMedia('(orientation: landscape)').matches;
        
        if (!isLandscapeMatch && !document.body.classList.contains('oc-split-active')) {
            return;
        }

        document.body.classList.toggle('oc-split-active');
        const btn = document.querySelector('.oc-split-toggle');
        if (btn) {
            btn.classList.toggle('active');
        }

        if (document.body.classList.contains('oc-split-active')) {
            setupSplitPanes();
        }
    }

    function setupSplitPanes() {
        const container = document.getElementById(CONTAINER_ID);
        if (!container) return;

        const existingPanes = container.querySelectorAll('.oc-pane-chat, .oc-pane-code');
        if (existingPanes.length >= 2) return;

        const messages = container.querySelector('[class*="messages"], [class*="chat"]');
        const code = container.querySelector('[class*="editor"], [class*="code"], pre, code');

        if (messages) messages.classList.add('oc-pane-chat');
        if (code) code.classList.add('oc-pane-code');

        const divider = createDivider();
        if (messages && messages.nextSibling) {
            container.insertBefore(divider, messages.nextSibling);
        }
    }

    function startDrag(e) {
        e.preventDefault();
        isDragging = true;
        document.getElementById(DIVIDER_ID)?.classList.add('active');
    }

    function onDrag(e) {
        if (!isDragging) return;
        e.preventDefault();

        const clientX = e.touches ? e.touches[0].clientX : e.clientX;
        const clientY = e.touches ? e.touches[0].clientY : e.clientY;
        const isLandscape = window.matchMedia('(orientation: landscape)').matches;

        if (isLandscape) {
            const width = window.innerWidth;
            splitRatio = Math.max(0.2, Math.min(0.8, clientX / width));
        } else {
            const height = window.innerHeight;
            splitRatio = Math.max(0.2, Math.min(0.8, clientY / height));
        }

        const styleEl = document.getElementById(STYLE_ID);
        if (styleEl) {
            const isLand = window.matchMedia('(orientation: landscape)').matches;
            if (isLand) {
                styleEl.textContent = styleEl.textContent.replace(
                    /flex: 0 0 [\d.]+%/,
                    `flex: 0 0 ${splitRatio * 100}%`
                );
            }
        }
    }

    function endDrag() {
        isDragging = false;
        document.getElementById(DIVIDER_ID)?.classList.remove('active');
    }

    function checkOrientation() {
        const isLand = window.matchMedia('(orientation: landscape)').matches;
        if (!isLand && document.body.classList.contains('oc-split-active')) {
            document.body.classList.remove('oc-split-active');
            document.querySelector('.oc-split-toggle')?.classList.remove('active');
        }
    }

    function init() {
        const setup = () => {
            injectStyles();
            createToggleButton();
            window.addEventListener('orientationchange', checkOrientation);
            window.addEventListener('resize', checkOrientation);
        };

        if (document.readyState === 'loading') {
            document.addEventListener('DOMContentLoaded', setup);
        } else {
            setup();
        }
    }

    init();
})();
