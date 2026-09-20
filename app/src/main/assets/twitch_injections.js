(function() {
    if (window._twitch_injections_loaded) {
        console.log("Twitch injections already loaded");
        return;
    }
    window._twitch_injections_loaded = true;

    console.log("Twitch injections initializing...");

    // ==========================================
    // 1. POPULAR GLOBAL EMOTES CACHE (Instant offline rendering)
    // ==========================================
    const DEFAULT_EMOTES = {
        "KEKW": "https://cdn.7tv.app/emote/60afb5d8e09f5db760920ef0/2x.webp",
        "Pog": "https://cdn.7tv.app/emote/60ae3e620583b28b704cbf9b/2x.webp",
        "PogBlink": "https://cdn.7tv.app/emote/60ae3f0c0583b28b704cbfe5/2x.webp",
        "monkaS": "https://cdn.7tv.app/emote/60ae3ec60583b28b704cbfc4/2x.webp",
        "monkaW": "https://cdn.7tv.app/emote/60ae3eb60583b28b704cbfbf/2x.webp",
        "OMEGALUL": "https://cdn.7tv.app/emote/60ae3eed0583b28b704cbfd6/2x.webp",
        "catJAM": "https://cdn.7tv.app/emote/60ae3fd40583b28b704cc03b/2x.webp",
        "pepeJAM": "https://cdn.7tv.app/emote/60ae40340583b28b704cc061/2x.webp",
        "Pepega": "https://cdn.7tv.app/emote/60ae3f3a0583b28b704cbff9/2x.webp",
        "widepeppoHappy": "https://cdn.7tv.app/emote/60ae3f860583b28b704cc01b/2x.webp",
        "Sadge": "https://cdn.7tv.app/emote/60ae3f700583b28b704cc012/2x.webp",
        "COPIUM": "https://cdn.7tv.app/emote/60ae40a40583b28b704cc090/2x.webp",
        "EZ": "https://cdn.7tv.app/emote/60ae3f490583b28b704cc000/2x.webp",
        "Clap": "https://cdn.7tv.app/emote/60ae3e970583b28b704cbfb2/2x.webp",
        "AYAYA": "https://cdn.7tv.app/emote/60ae3f1c0583b28b704cbfee/2x.webp",
        "NODDERS": "https://cdn.7tv.app/emote/60ae40e20583b28b704cc0aa/2x.webp",
        "NOPERS": "https://cdn.7tv.app/emote/60ae40ec0583b28b704cc0ae/2x.webp",
        "PepeSpit": "https://cdn.7tv.app/emote/60ae41080583b28b704cc0ba/2x.webp",
        "modCheck": "https://cdn.7tv.app/emote/60ae40720583b28b704cc07a/2x.webp",
        "PauseChamp": "https://cdn.7tv.app/emote/60ae3f5d0583b28b704cc009/2x.webp",
        "FeelsStrongMan": "https://cdn.7tv.app/emote/60ae3ea80583b28b704cbfb8/2x.webp",
        "HYPERCLAP": "https://cdn.7tv.app/emote/60ae414c0583b28b704cc0d6/2x.webp",
        "LULW": "https://cdn.7tv.app/emote/60ae3ed70583b28b704cbfcc/2x.webp",
        "GIGACHAD": "https://cdn.7tv.app/emote/60ae43ec0583b28b704cc1db/2x.webp",
        "blobDance": "https://cdn.7tv.app/emote/60ae41660583b28b704cc0e2/2x.webp",
        "RatJAM": "https://cdn.7tv.app/emote/60ae41880583b28b704cc0f0/2x.webp",
        "DOGE": "https://cdn.7tv.app/emote/60ae419c0583b28b704cc0f8/2x.webp",
        "5Head": "https://cdn.7tv.app/emote/60ae3e7b0583b28b704cbfa6/2x.webp",
        "3Head": "https://cdn.7tv.app/emote/60ae3e890583b28b704cbfac/2x.webp",
        "pepegaAim": "https://cdn.7tv.app/emote/60ae41c20583b28b704cc108/2x.webp",
        "pepeL": "https://cdn.7tv.app/emote/60ae41e00583b28b704cc115/2x.webp",
        "pepeW": "https://cdn.7tv.app/emote/60ae41ee0583b28b704cc11b/2x.webp"
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
        img.style.cssText = 'height: 28px; min-width: 20px; vertical-align: middle; margin: -2px 3px; display: inline-block; object-fit: contain;';
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

        // Find text fragments inside message
        const textElements = el.querySelectorAll('.text-fragment, [data-a-target="chat-message-text"], span[data-test-selector="chat-line-message-body"]');
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
        const messages = document.querySelectorAll('.chat-line__message, .chat-line, [data-test-selector="chat-line-message-body"], .stream-chat-line');
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
                        if (node.matches && (node.matches('.chat-line__message') || node.matches('.chat-line') || node.matches('[data-test-selector="chat-line-message-body"]'))) {
                            processMessageElement(node);
                        } else {
                            const subMessages = node.querySelectorAll ? node.querySelectorAll('.chat-line__message, .chat-line, [data-test-selector="chat-line-message-body"]') : [];
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
