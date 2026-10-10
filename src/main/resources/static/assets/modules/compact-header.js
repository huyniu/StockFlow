export function initializeCompactHeader() {
    const header = document.querySelector('.shop-header');
    const syncHeight = () => {
        const height = header.getBoundingClientRect().height;
        if (height > 0) document.documentElement.style.setProperty('--shop-header-height', height + 'px');
    };
    if ('ResizeObserver' in window) new ResizeObserver(syncHeight).observe(header);
    else window.addEventListener('resize', syncHeight, { passive: true });
    syncHeight();
}
