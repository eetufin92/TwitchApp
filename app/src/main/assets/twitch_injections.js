(function() {
    if (window._twitch_injections_loaded) {
        console.log("Twitch injections already loaded");
        return;
    }
    window._twitch_injections_loaded = true;

    console.log("Twitch injections initializing...");

    // ==========================================
    // 1. POPULAR GLOBAL EMOTES CACHE (Verified CDN URLs)
    // ==========================================
    const DEFAULT_EMOTES = {
        "KEKW": "https://cdn.frankerfacez.com/emote/381875/2",
        "OMEGALUL": "https://cdn.frankerfacez.com/emote/128054/2",
        "catJAM": "https://cdn.frankerfacez.com/emote/564062/2",
        "monkaW": "https://cdn.frankerfacez.com/emote/214681/2",
        "monkaS": "https://cdn.frankerfacez.com/emote/130762/2",
        "Pog": "https://cdn.frankerfacez.com/emote/210748/2",
        "PogBlink": "https://cdn.frankerfacez.com/emote/381875/2",
        "Pepega": "https://cdn.frankerfacez.com/emote/243789/2",
        "Sadge": "https://cdn.frankerfacez.com/emote/425196/2",
        "COPIUM": "https://cdn.frankerfacez.com/emote/563443/2",
        "Clap": "https://cdn.frankerfacez.com/emote/298847/2",
        "AYAYA": "https://cdn.frankerfacez.com/emote/162146/2",
        "NODDERS": "https://cdn.frankerfacez.com/emote/720817/2",
        "NOPERS": "https://cdn.frankerfacez.com/emote/725721/2",
        "GIGACHAD": "https://cdn.frankerfacez.com/emote/354434/2",
        "EZ": "https://cdn.frankerfacez.com/emote/185890/2",
        "LULW": "https://cdn.frankerfacez.com/emote/139407/2",
        "5Head": "https://cdn.frankerfacez.com/emote/239504/2",
        "3Head": "https://cdn.frankerfacez.com/emote/274406/2",
        "FeelsStrongMan": "https://cdn.frankerfacez.com/emote/64210/2",
        "widepeppoHappy": "https://cdn.betterttv.net/emote/5e18237910389326f6e52233/2x",
        "pepeJAM": "https://cdn.betterttv.net/emote/5b77ac3af7ab497359922e37/2x",
        "blobDance": "https://cdn.betterttv.net/emote/5ada077451d4120ea3918426/2x",
        "DOGE": "https://cdn.betterttv.net/emote/56e9f494fff3cc5c35e5287e/2x"
    };

    window._twitchEmoteMap = Object.assign({}, DEFAULT_EMOTES);

    window.addEmotes = function(newEmotes) {
        if (!newEmotes || typeof newEmotes !== 'object') return;
        Object.assign(window._twitchEmoteMap, newEmotes);
        console.log("TwitchApp: Added emotes. Total now:", Object.keys(window._twitchEmoteMap).length);
        scanAndReplaceAllChatEmotes();
    };

    // ==========================================
    // 2. EMOTE REPLACEMENT IN CHAT
    // ==========================================
    function createEmoteElement(name, url) {
        const img = document.createElement('img');
        img.src = url;
        img.alt = name;
        img.title = name;
        img.className = 'twitch-custom-emote';
        img.loading = 'lazy';
        img.crossOrigin = 'anonymous';
        img.style.cssText = 'height: 1.8em; min-width: 1.4em; vertical-align: middle; margin: 0 2px; display: inline-block; object-fit: contain;';
        img.onerror = function() {
            // If image fails to load for any reason, gracefully fall back to plain text
            // so a broken image icon is NEVER rendered in chat!
            if (this.parentNode) {
                const textNode = document.createTextNode(name);
                this.parentNode.replaceChild(textNode, this);
            }
        };
        return img;
    }

    function processTextNode(textNode) {
        const text = textNode.nodeValue;
        if (!text || !text.trim()) return null;

        const words = text.split(/(\s+)/);
        let hasEmote = false;
        for (let i = 0; i < words.length; i += 2) {
            if (window._twitchEmoteMap[words[i]]) {
                hasEmote = true;
                break;
            }
        }
        if (!hasEmote) return null;

        const fragment = document.createDocumentFragment();
        for (let i = 0; i < words.length; i++) {
            const word = words[i];
            const emoteUrl = window._twitchEmoteMap[word];
            if (emoteUrl) {
                fragment.appendChild(createEmoteElement(word, emoteUrl));
            } else {
                fragment.appendChild(document.createTextNode(word));
            }
        }
        return fragment;
    }

    const processedNodes = new WeakSet();

    function processMessageElement(el) {
        if (!el || processedNodes.has(el)) return;
        processedNodes.add(el);

        // Find text fragments inside message (works for mobile and desktop web)
        const textElements = el.querySelectorAll('.text-fragment, [data-a-target="chat-message-text"], span[data-test-selector="chat-line-message-body"], .chat-author__message, .chat-message__message');
        const targets = textElements.length > 0 ? textElements : [el];

        targets.forEach(target => {
            const walker = document.createTreeWalker(target, NodeFilter.SHOW_TEXT, null, false);
            const textNodes = [];
            let node;
            while ((node = walker.nextNode())) {
                textNodes.push(node);
            }

            textNodes.forEach(tNode => {
                const replacement = processTextNode(tNode);
                if (replacement && tNode.parentNode) {
                    tNode.parentNode.replaceChild(replacement, tNode);
                }
            });
        });
    }

    function scanAndReplaceAllChatEmotes() {
        const messages = document.querySelectorAll('.chat-line__message, .chat-line, [data-test-selector="chat-line-message-body"], .stream-chat-line, .chat-message, [data-a-target="chat-line-message"]');
        messages.forEach(processMessageElement);
    }

    // Observe chat container mutations
    function initChatObserver() {
        const chatObserver = new MutationObserver(mutations => {
            for (let i = 0; i < mutations.length; i++) {
                const addedNodes = mutations[i].addedNodes;
                for (let j = 0; j < addedNodes.length; j++) {
                    const node = addedNodes[j];
                    if (node.nodeType === Node.ELEMENT_NODE) {
                        const selector = '.chat-line__message, .chat-line, [data-test-selector="chat-line-message-body"], .chat-message, [data-a-target="chat-line-message"]';
                        if (node.matches && node.matches(selector)) {
                            processMessageElement(node);
                        } else {
                            const subMessages = node.querySelectorAll ? node.querySelectorAll(selector) : [];
                            subMessages.forEach(processMessageElement);
                        }
                    }
                }
            }
        });

        chatObserver.observe(document.body, { childList: true, subtree: true });
        console.log("Chat observer registered");
    }

    // ==========================================
    // 3. SSAI AD DETECTION & AUTO-MUTING
    // ==========================================
    let isAdActive = false;
    let savedMutedState = false;
    let savedVolume = 1.0;
    let userUnmutedManually = false;

    function detectAdElements() {
        // Look for ad labels and countdowns
        const adLabel = document.querySelector('[data-a-target="video-ad-label"], .video-ad-label, [data-test-selector="ad-banner-default-text"], .ad-interrupting');
        const adCountdown = document.querySelector('[data-a-target="video-ad-countdown"], .video-ad-countdown');
        const playerAd = document.querySelector('[data-a-target="player-overlay-ad"], .player-ad-overlay');

        if (adLabel || adCountdown || playerAd) {
            const countdownText = adCountdown ? adCountdown.textContent.trim() : (adLabel ? adLabel.textContent.trim() : "Ad in progress");
            return { isAd: true, countdownText: countdownText };
        }

        // Also check player overlay text for "Ad 1 of" or "Commercial"
        const notices = document.querySelectorAll('.tw-c-text-overlay, [class*="ad-notice"], [class*="ad-banner"]');
        for (let i = 0; i < notices.length; i++) {
            const txt = notices[i].textContent || "";
            if (/ad \d+ of \d+|commercial break/i.test(txt)) {
                return { isAd: true, countdownText: txt.trim() };
            }
        }

        return { isAd: false, countdownText: "" };
    }

    function createAdShield(countdownText) {
        let shield = document.getElementById('twitch-app-ad-shield');
        if (!shield) {
            shield = document.createElement('div');
            shield.id = 'twitch-app-ad-shield';
            shield.style.cssText = `
                position: absolute;
                top: 0;
                left: 0;
                width: 100%;
                height: 100%;
                background: linear-gradient(135deg, rgba(14, 14, 16, 0.96) 0%, rgba(24, 24, 27, 0.94) 100%);
                z-index: 2147483645;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                color: #FFFFFF;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                text-align: center;
                padding: 20px;
                box-sizing: border-box;
                backdrop-filter: blur(8px);
                transition: opacity 0.3s ease;
            `;

            shield.innerHTML = `
                <div style="background: rgba(145, 70, 255, 0.2); border: 2px solid #9146FF; border-radius: 50%; width: 72px; height: 72px; display: flex; align-items: center; justify-content: center; margin-bottom: 16px; animation: pulse 2s infinite;">
                    <svg width="36" height="36" viewBox="0 0 24 24" fill="#9146FF">
                        <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 14h-2v-2h2v2zm0-4h-2V7h2v5z"/>
                    </svg>
                </div>
                <div style="font-size: 20px; font-weight: 700; margin-bottom: 6px; color: #FFFFFF;">Commercial Break in Progress</div>
                <div id="twitch-ad-countdown-text" style="font-size: 15px; color: #BB86FC; margin-bottom: 12px; font-weight: 600;">${countdownText}</div>
                <div style="font-size: 13px; color: #ADADB8; margin-bottom: 20px; max-width: 280px;">Stream audio is automatically muted to save your ears 🎧</div>
                <button id="twitch-ad-unmute-btn" style="background: #9146FF; color: white; border: none; padding: 8px 20px; border-radius: 20px; font-size: 14px; font-weight: 600; cursor: pointer; box-shadow: 0 4px 12px rgba(145, 70, 255, 0.4);">
                    Unmute Audio
                </button>
            `;

            // Insert into player container if possible
            const playerContainer = document.querySelector('.video-player__container, .player-container, [data-a-target="video-player"], .highwind-video-player') || document.body;
            if (playerContainer.style.position === '' || playerContainer.style.position === 'static') {
                playerContainer.style.position = 'relative';
            }
            playerContainer.appendChild(shield);

            const unmuteBtn = shield.querySelector('#twitch-ad-unmute-btn');
            if (unmuteBtn) {
                unmuteBtn.addEventListener('click', () => {
                    const video = document.querySelector('video');
                    if (video) {
                        video.muted = false;
                        video.volume = 1.0;
                    }
                    userUnmutedManually = true;
                    unmuteBtn.textContent = "Audio Unmuted";
                    unmuteBtn.style.background = "#00F5D4";
                    unmuteBtn.style.color = "#000000";
                });
            }
        } else {
            const countEl = shield.querySelector('#twitch-ad-countdown-text');
            if (countEl && countdownText) {
                countEl.textContent = countdownText;
            }
        }
    }

    function removeAdShield() {
        const shield = document.getElementById('twitch-app-ad-shield');
        if (shield) {
            shield.style.opacity = '0';
            setTimeout(() => {
                if (shield.parentNode) shield.parentNode.removeChild(shield);
            }, 300);
        }
    }

    function checkAdState() {
        const { isAd, countdownText } = detectAdElements();
        const video = document.querySelector('video');

        if (isAd) {
            if (!isAdActive) {
                isAdActive = true;
                userUnmutedManually = false;
                console.log("TwitchApp: SSAI Ad started:", countdownText);
                if (video) {
                    savedMutedState = video.muted;
                    savedVolume = video.volume;
                    video.muted = true;
                    video.volume = 0;
                }
                createAdShield(countdownText);
                if (window.TwitchAndroidBridge && window.TwitchAndroidBridge.onAdStatusChanged) {
                    window.TwitchAndroidBridge.onAdStatusChanged(true, countdownText);
                }
            } else {
                createAdShield(countdownText);
            }
        } else {
            if (isAdActive) {
                isAdActive = false;
                console.log("TwitchApp: SSAI Ad ended, restoring audio");
                if (video && !userUnmutedManually) {
                    video.muted = savedMutedState;
                    video.volume = savedVolume;
                }
                removeAdShield();
                if (window.TwitchAndroidBridge && window.TwitchAndroidBridge.onAdStatusChanged) {
                    window.TwitchAndroidBridge.onAdStatusChanged(false, "");
                }
            }
        }
    }

    setInterval(checkAdState, 250);

    // ==========================================
    // 4. COSMETIC CLEANUPS (Strip mobile app nags)
    // ==========================================
    function injectCosmeticStyles() {
        const style = document.createElement('style');
        style.id = 'twitch-app-cosmetics';
        style.textContent = `
            /* Hide app install banners, nag modals, smart banners */
            .tw-smart-banner,
            .app-promo,
            .upsell-banner,
            [data-a-target="tw-core-button-open-in-app"],
            a[href*="app.link"],
            button[aria-label*="Open in App"],
            button[aria-label*="Get App"],
            .tw-button--open-in-app,
            .mobile-web-download-banner,
            .cookie-banner,
            .consent-banner {
                display: none !important;
                visibility: hidden !important;
                height: 0 !important;
                overflow: hidden !important;
            }

            @keyframes pulse {
                0% { transform: scale(1); opacity: 0.9; }
                50% { transform: scale(1.08); opacity: 1; }
                100% { transform: scale(1); opacity: 0.9; }
            }
        `;
        document.head.appendChild(style);
    }

    // ==========================================
    // 5. METADATA & PLAYER TRACKING
    // ==========================================
    let lastChannel = "";
    let lastTitle = "";
    let lastIsPlaying = false;

    function checkMetadata() {
        const video = document.querySelector('video');
        if (video && !video._dimensions_listener) {
            video._dimensions_listener = true;
            const reportDims = () => {
                if (video.videoWidth > 0 && video.videoHeight > 0 && window.TwitchAndroidBridge) {
                    window.TwitchAndroidBridge.onVideoDimensionsChanged(video.videoWidth, video.videoHeight);
                }
            };
            video.addEventListener('loadedmetadata', reportDims);
            video.addEventListener('resize', reportDims);
            reportDims();
        }

        // Channel detection from URL
        const pathParts = window.location.pathname.split('/').filter(Boolean);
        const channel = pathParts.length > 0 ? pathParts[0] : "";
        if (channel && channel !== lastChannel && channel !== 'directory' && channel !== 'videos') {
            lastChannel = channel;
            console.log("TwitchApp: Channel detected:", channel);
            if (window.TwitchAndroidBridge && window.TwitchAndroidBridge.onChannelDetected) {
                window.TwitchAndroidBridge.onChannelDetected(channel);
            }
        }

        // Title detection
        let title = document.title;
        if (title.endsWith(' - Twitch')) title = title.substring(0, title.length - 9);
        if (title !== lastTitle) {
            lastTitle = title;
            if (window.TwitchAndroidBridge && window.TwitchAndroidBridge.updateMetadata) {
                window.TwitchAndroidBridge.updateMetadata(title, channel);
            }
        }

        // Playback state
        if (video) {
            const isPlaying = !video.paused && !video.ended;
            if (isPlaying !== lastIsPlaying) {
                lastIsPlaying = isPlaying;
                if (window.TwitchAndroidBridge && window.TwitchAndroidBridge.updatePlaybackState) {
                    window.TwitchAndroidBridge.updatePlaybackState(isPlaying);
                }
            }
        }
    }

    setInterval(checkMetadata, 1000);

    // Run initialization
    injectCosmeticStyles();
    initChatObserver();
    scanAndReplaceAllChatEmotes();

    console.log("TwitchApp: Injections successfully loaded!");
})();
