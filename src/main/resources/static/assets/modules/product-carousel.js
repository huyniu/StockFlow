export function initializeProductCarousels() {
    for (const selector of ['#recently-viewed-products', '#bestseller-grid']) {
        const track = document.querySelector(selector);
        let paused = false;
        let visible = false;
        let interactedAt = 0;
        track.addEventListener('pointerenter', () => { paused = true; });
        track.addEventListener('pointerleave', () => { paused = false; });
        track.addEventListener('focusin', () => { paused = true; });
        track.addEventListener('focusout', () => { paused = track.matches(':hover'); });
        for (const event of ['pointerdown', 'keydown']) {
            track.addEventListener(event, () => { interactedAt = Date.now(); }, { passive: true });
        }
        track.addEventListener('wheel', event => {
            interactedAt = Date.now();
            // Leave zoom gestures and native horizontal trackpad scrolling to the browser.
            if (event.ctrlKey || Math.abs(event.deltaX) >= Math.abs(event.deltaY)) return;
            const max = track.scrollWidth - track.clientWidth;
            if (max < 2) return;
            const unit = event.deltaMode === 1 ? 24 : event.deltaMode === 2 ? track.clientWidth : 1;
            const delta = event.deltaY * unit;
            if ((delta < 0 && track.scrollLeft <= 1) || (delta > 0 && track.scrollLeft >= max - 1)) return;
            event.preventDefault();
            track.style.scrollSnapType = 'none';
            track.scrollBy({ left: delta, behavior: 'instant' });
        }, { passive: false });
        new IntersectionObserver(entries => { visible = entries[0].isIntersecting; }).observe(track);
        setInterval(() => {
            if (!visible || paused || document.hidden || Date.now() - interactedAt < 4000 ||
                matchMedia('(prefers-reduced-motion: reduce)').matches) return;
            const cards = [...track.children].filter(child => child.matches('.recent-product, .product-card'));
            if (cards.length < 2) return;
            const distance = cards[1].offsetLeft - cards[0].offsetLeft;
            const max = track.scrollWidth - track.clientWidth;
            if (max < 2) return;
            track.scrollTo({ left: track.scrollLeft >= max - 2 ? 0 : Math.min(max, track.scrollLeft + distance), behavior: 'smooth' });
        }, 4000);
    }
}
