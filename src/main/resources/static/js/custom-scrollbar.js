(function () {
    function initCustomScrollbar() {
        const scrollbar = document.getElementById('customScrollbar');
        const track = document.getElementById('customScrollbarTrack');
        const thumb = document.getElementById('customScrollbarThumb');

        if (!scrollbar || !track || !thumb) {
            return;
        }

        let isDragging = false;
        let dragOffsetY = 0;
        let rafId = null;
        let fadeTimer = null;

        const showScrollbar = () => {
            if (scrollbar.style.display === 'none') {
                return;
            }

            scrollbar.classList.add('is-visible');
            if (fadeTimer) {
                clearTimeout(fadeTimer);
            }

            fadeTimer = setTimeout(() => {
                if (!isDragging) {
                    scrollbar.classList.remove('is-visible');
                }
            }, 900);
        };

        const updateThumb = () => {
            const doc = document.documentElement;
            const scrollTop = window.scrollY || doc.scrollTop;
            const viewportHeight = window.innerHeight;
            const documentHeight = doc.scrollHeight;

            const maxScroll = Math.max(documentHeight - viewportHeight, 1);
            const trackHeight = track.clientHeight;
            const thumbHeight = Math.max((viewportHeight / documentHeight) * trackHeight, 42);
            const maxThumbTop = Math.max(trackHeight - thumbHeight, 0);
            const thumbTop = (scrollTop / maxScroll) * maxThumbTop;

            thumb.style.height = `${thumbHeight}px`;
            thumb.style.top = `${Math.max(0, Math.min(maxThumbTop, thumbTop))}px`;

            const shouldHide = documentHeight <= viewportHeight + 1;
            scrollbar.style.display = shouldHide ? 'none' : 'block';

            if (shouldHide) {
                scrollbar.classList.remove('is-visible');
                if (fadeTimer) {
                    clearTimeout(fadeTimer);
                    fadeTimer = null;
                }
            }
        };

        const requestThumbUpdate = () => {
            if (rafId) {
                return;
            }

            rafId = requestAnimationFrame(() => {
                updateThumb();
                rafId = null;
            });
        };

        const scrollToTrackPosition = (clientY, centerThumb) => {
            const doc = document.documentElement;
            const trackRect = track.getBoundingClientRect();
            const trackHeight = track.clientHeight;
            const thumbHeight = thumb.offsetHeight;
            const maxThumbTop = Math.max(trackHeight - thumbHeight, 0);
            const maxScroll = Math.max(doc.scrollHeight - window.innerHeight, 0);

            let relativeY = clientY - trackRect.top;
            if (centerThumb) {
                relativeY -= thumbHeight / 2;
            }

            const thumbTop = Math.max(0, Math.min(maxThumbTop, relativeY));
            const progress = maxThumbTop > 0 ? thumbTop / maxThumbTop : 0;
            window.scrollTo({ top: progress * maxScroll, behavior: 'auto' });
        };

        thumb.addEventListener('mousedown', (event) => {
            isDragging = true;
            const thumbRect = thumb.getBoundingClientRect();
            dragOffsetY = event.clientY - thumbRect.top;
            document.body.style.userSelect = 'none';
            showScrollbar();
            event.preventDefault();
        });

        document.addEventListener('mousemove', (event) => {
            if (!isDragging) {
                return;
            }

            scrollToTrackPosition(event.clientY - dragOffsetY + thumb.offsetHeight / 2, true);
            showScrollbar();
        });

        document.addEventListener('mouseup', () => {
            isDragging = false;
            document.body.style.userSelect = '';
            showScrollbar();
        });

        track.addEventListener('click', (event) => {
            if (event.target === thumb) {
                return;
            }

            scrollToTrackPosition(event.clientY, true);
            showScrollbar();
        });

        window.addEventListener('scroll', () => {
            requestThumbUpdate();
            showScrollbar();
        }, { passive: true });
        window.addEventListener('wheel', showScrollbar, { passive: true });
        window.addEventListener('touchmove', showScrollbar, { passive: true });
        window.addEventListener('resize', requestThumbUpdate);
        updateThumb();
        showScrollbar();
    }

    window.initCustomScrollbar = initCustomScrollbar;
})();
