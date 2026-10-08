export function initializeCompactHeader() {
    const switcher = document.querySelector('#demo-account-switcher');
    const header = document.querySelector('.shop-header');
    const mobile = window.matchMedia('(max-width: 760px)');
    const syncSwitcher = () => { switcher.open = !mobile.matches; };
    syncSwitcher();
    mobile.addEventListener('change', syncSwitcher);

    document.addEventListener('click', event => {
        if (!mobile.matches || !switcher.open) return;
        if (!switcher.contains(event.target) || event.target.closest('[data-demo-role]')) switcher.open = false;
    });
    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && mobile.matches && switcher.open) {
            switcher.open = false;
            switcher.querySelector('summary').focus();
        }
    });

    const syncHeight = () => {
        const height = header.getBoundingClientRect().height;
        if (height > 0) document.documentElement.style.setProperty('--shop-header-height', height + 'px');
    };
    if ('ResizeObserver' in window) new ResizeObserver(syncHeight).observe(header);
    else window.addEventListener('resize', syncHeight, { passive: true });
    syncHeight();
}
