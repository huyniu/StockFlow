import { loadFragments } from '/assets/modules/fragments.js';
import { initializeLoginArtwork } from '/assets/login-artwork.js';
import { initializeCompactHeader } from '/assets/modules/compact-header.js';
import { initializeAccountServices } from '/assets/modules/account-services.js';
import { initializeAfterSales } from '/assets/modules/after-sales.js';
import { initializeRecentlyViewed } from '/assets/modules/recently-viewed.js';
import { initializeProductCarousels } from '/assets/modules/product-carousel.js';
import { initializeProductImageViewer } from '/assets/modules/product-image-viewer.js';

try {
    await loadFragments();
    initializeLoginArtwork();
    initializeCompactHeader();
    const features = await Promise.all([
        import('/assets/modules/core.js'),
        import('/assets/modules/auth-profile.js'),
        import('/assets/modules/catalog-navigation.js'),
        import('/assets/modules/product-detail.js'),
        import('/assets/modules/cart-checkout.js'),
        import('/assets/modules/orders-fulfillment.js'),
        import('/assets/modules/admin.js'),
        import('/assets/modules/navigation-events.js'),
    ]);
    features.forEach(feature => feature.register());
    features.slice(0, -1).forEach(feature => feature.initializeFeature());
    initializeAccountServices();
    initializeAfterSales();
    initializeRecentlyViewed();
    initializeProductCarousels();
    initializeProductImageViewer();
    features.at(-1).initializeFeature();
} catch (error) {
    const status = document.querySelector('#boot-status') || document.body.appendChild(document.createElement('p'));
    status.textContent = 'Chưa mở được StockFlow. Vui lòng tải lại trang.';
    status.setAttribute('role', 'alert');
    const retry = document.createElement('button');
    retry.textContent = 'Tải lại';
    retry.addEventListener('click', () => location.reload());
    status.append(' ', retry);
    console.error('StockFlow startup failed', error);
}
