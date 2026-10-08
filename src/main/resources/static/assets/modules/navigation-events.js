import { app as __stockflowApp } from './context.js';

let pendingVNPayReturn;

function currentPagePath() {
        if (__stockflowApp.state.view === 'shop' && __stockflowApp.state.shopTab === 'product') return __stockflowApp.productPagePath(__stockflowApp.state.productId);
        if (__stockflowApp.state.view === 'shop' && __stockflowApp.state.shopTab === 'catalog') {
            const query = new URLSearchParams();
            if (__stockflowApp.$('#catalog-category').value) query.set('categoryId', __stockflowApp.$('#catalog-category').value);
            if (__stockflowApp.state.catalogBrandId) query.set('brandId', __stockflowApp.state.catalogBrandId);
            if (__stockflowApp.$('#catalog-query').value.trim()) query.set('q', __stockflowApp.$('#catalog-query').value.trim());
            if (__stockflowApp.state.catalogMinPrice !== '') query.set('minPrice', __stockflowApp.state.catalogMinPrice);
            if (__stockflowApp.state.catalogMaxPrice !== '') query.set('maxPrice', __stockflowApp.state.catalogMaxPrice);
            if (__stockflowApp.state.catalogSpecName) query.set('specificationName', __stockflowApp.state.catalogSpecName);
            if (__stockflowApp.state.catalogSpecValue) query.set('specificationValue', __stockflowApp.state.catalogSpecValue);
            if (__stockflowApp.state.catalogSort !== 'id,asc') query.set('sort', __stockflowApp.state.catalogSort);
            return '/' + (query.size ? '?' + query.toString() : '') + '#shop';
        }
        const hash =
            __stockflowApp.state.view === 'portal'
                ? '#portal/' + __stockflowApp.state.portalTab
                : __stockflowApp.state.shopTab === 'orders'
                  ? '#shop/orders'
                  : __stockflowApp.state.shopTab === 'account'
                    ? '#shop/account'
                    : __stockflowApp.state.shopTab === 'help'
                      ? '#shop/help'
                      : '#shop';
        return '/' + hash;
    }

function replaceHash() {
        __stockflowApp.setLoginPage(false);
        window.history.replaceState(null, '', __stockflowApp.currentPagePath());
        __stockflowApp.routedLocation = window.location.href;
    }

function pushPage() {
        const path = __stockflowApp.currentPagePath();
        if (window.location.pathname + window.location.search + window.location.hash !== path) {
            window.history.pushState(null, '', path);
        }
        __stockflowApp.routedLocation = window.location.href;
    }

async function activateView(view, tab, { productId = null, navigation = 'push' } = {}) {
        __stockflowApp.closeSearchSuggestions();
        if (view === 'portal') {
            if (!__stockflowApp.isOperator()) {
                __stockflowApp.openAuth('portal');
                return;
            }
            __stockflowApp.state.view = 'portal';
            __stockflowApp.state.portalTab = __stockflowApp.canPortalTab(tab) ? tab : 'queue';
            if (['products', 'categories', 'admin'].includes(__stockflowApp.state.portalTab)) __stockflowApp.state.catalogExpanded = true;
        } else {
            if (['orders', 'account'].includes(tab) && !__stockflowApp.hasRole('CUSTOMER')) {
                __stockflowApp.openAuth(tab);
                return;
            }
            __stockflowApp.state.view = 'shop';
            __stockflowApp.state.shopTab = ['orders', 'product', 'account', 'help'].includes(tab) ? tab : 'catalog';
        }
        __stockflowApp.invalidateOrderRefresh();
        // Rời trang chi tiết phải hủy GET chậm trước khi nó kịp cập nhật DOM hoặc giá trong giỏ.
        __stockflowApp.channels.get('shop-product-detail')?.abort();
        __stockflowApp.channels.delete('shop-product-detail');
        __stockflowApp.channels.get('shop-product-availability')?.abort();
        __stockflowApp.channels.delete('shop-product-availability');
        __stockflowApp.channels.get('profile-read')?.abort();
        __stockflowApp.channels.delete('profile-read');
        __stockflowApp.state.availabilityEpoch++;
        __stockflowApp.state.productId = __stockflowApp.state.view === 'shop' && __stockflowApp.state.shopTab === 'product' ? Number(productId) : null;
        if (__stockflowApp.state.shopTab !== 'product' || __stockflowApp.state.view !== 'shop') {
            __stockflowApp.$('#shop-product-detail-body').replaceChildren();
            delete __stockflowApp.$('#shop-product-detail-body').dataset.productId;
        }
        __stockflowApp.state.order = null;
        __stockflowApp.renderContext();
        __stockflowApp.renderOrder();
        if (navigation === 'replace') __stockflowApp.replaceHash();
        else if (navigation === 'push') __stockflowApp.pushPage();
        else __stockflowApp.routedLocation = window.location.href;
        if (__stockflowApp.state.view === 'shop' && __stockflowApp.state.shopTab === 'product') window.scrollTo({ top: 0, behavior: 'auto' });
        await __stockflowApp.refreshSection();
    }

async function refreshSection() {
        if (__stockflowApp.state.view === 'shop') {
            if (__stockflowApp.state.shopTab === 'orders') await __stockflowApp.loadOrders();
            else if (__stockflowApp.state.shopTab === 'account') await __stockflowApp.loadProfile();
            else if (__stockflowApp.state.shopTab === 'product') await __stockflowApp.loadShopProductDetail();
            else if (__stockflowApp.state.shopTab === 'help') __stockflowApp.$('#purchase-help-title').focus({ preventScroll: true });
            else await Promise.all([__stockflowApp.loadCatalog(), ...(__stockflowApp.state.discoveryDirty ? [__stockflowApp.loadBestsellers()] : [])]);
            return;
        }
        const loaders = {
            queue: __stockflowApp.loadQueue,
            inventory: __stockflowApp.loadInventory,
            ledger: __stockflowApp.loadLedger,
            reports: __stockflowApp.loadReports,
            products: __stockflowApp.loadManage,
            categories: __stockflowApp.loadCategoryList,
            admin: __stockflowApp.loadManage,
            users: __stockflowApp.loadAdminUsers,
        };
        if (__stockflowApp.canPortalTab(__stockflowApp.state.portalTab)) await loaders[__stockflowApp.state.portalTab]();
    }

async function changePage(name, direction) {
        if (!Object.hasOwn(__stockflowApp.state.pages, name)) return;
        __stockflowApp.state.pages[name] = Math.max(0, __stockflowApp.state.pages[name] + direction);
        const loaders = {
            catalog: __stockflowApp.loadCatalog,
            orders: __stockflowApp.loadOrders,
            queue: __stockflowApp.loadQueue,
            inventory: __stockflowApp.loadInventory,
            ledger: __stockflowApp.loadLedger,
            revenue: __stockflowApp.loadRevenue,
            top: __stockflowApp.loadTop,
            low: __stockflowApp.loadLow,
            manage: __stockflowApp.loadManage,
            products: __stockflowApp.loadManage,
            users: __stockflowApp.loadAdminUsers,
        };
        if (name !== 'catalog' && name !== 'orders' && !__stockflowApp.isOperator()) return;
        if (['ledger', 'revenue', 'top', 'low'].includes(name) && !__stockflowApp.hasRole('ADMIN', 'MANAGER')) return;
        await loaders[name]();
    }

function restoreCatalogLocation() {
        const query = new URLSearchParams(window.location.search);
        const categoryId = query.get('categoryId') || '';
        const brandId = query.get('brandId') || '';
        __stockflowApp.$('#catalog-category').value = __stockflowApp.state.categories.some((value) => String(value.id) === categoryId)
            ? categoryId
            : '';
        __stockflowApp.state.catalogBrandId = __stockflowApp.state.brands.some((value) => String(value.id) === brandId) ? brandId : '';
        __stockflowApp.$('#catalog-query').value = query.get('q') || '';
        __stockflowApp.state.catalogMinPrice = query.get('minPrice') || '';
        __stockflowApp.state.catalogMaxPrice = query.get('maxPrice') || '';
        __stockflowApp.state.catalogSpecName = query.get('specificationName') || '';
        __stockflowApp.state.catalogSpecValue = query.get('specificationValue') || '';
        __stockflowApp.$('#catalog-spec-name').value = __stockflowApp.state.catalogSpecName;
        __stockflowApp.$('#catalog-spec-value').value = __stockflowApp.state.catalogSpecValue;
        __stockflowApp.renderSpecificationValueOptions();
        const sort = query.get('sort') || 'id,asc';
        const choices = [...__stockflowApp.$('#catalog-sort').options].map((option) => option.value);
        __stockflowApp.state.catalogSort = choices.includes(sort) ? sort : 'id,asc';
        __stockflowApp.$('#catalog-sort').value = __stockflowApp.state.catalogSort;
        __stockflowApp.state.pages.catalog = 0;
        __stockflowApp.renderCatalogBrandOptions();
        __stockflowApp.syncCatalogPriceFields();
    }

async function consumeVNPayReturn() {
        if (!__stockflowApp.pendingVNPayReturn || !__stockflowApp.hasRole('CUSTOMER')) return;
        const result = __stockflowApp.pendingVNPayReturn;
        __stockflowApp.pendingVNPayReturn = null;
        await __stockflowApp.lookupOrder(result.orderId);
        const paid = ['CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED'].includes(__stockflowApp.state.order?.status);
        if (result.status === 'success' && paid) {
            __stockflowApp.notify('success', 'Thanh toán qua VNPay thành công!');
        } else {
            __stockflowApp.notify('error', 'Thanh toán VNPay chưa thành công. Vui lòng kiểm tra trạng thái đơn hàng.');
        }
    }

async function routeFromLocation() {
        if (['/login','/register'].includes(window.location.pathname)) {
            if (!__stockflowApp.state.user) { __stockflowApp.showLoginPage(); return; }
            const path = __stockflowApp.safeLoginReturn(new URLSearchParams(window.location.search).get('pre_uri'));
            window.history.replaceState(null, '', path);
        }
        __stockflowApp.setLoginPage(false);
        if (window.location.hash.startsWith('#orders?')) {
            const query = new URLSearchParams(window.location.hash.slice('#orders?'.length));
            const orderId = Number(query.get('order_id'));
            const paymentStatus = query.get('payment_status');
            if (Number.isSafeInteger(orderId) && orderId > 0 && ['success', 'failed'].includes(paymentStatus)) {
                __stockflowApp.pendingVNPayReturn = { orderId, status: paymentStatus };
                if (!__stockflowApp.state.user) {
                    __stockflowApp.openAuth('orders');
                    return;
                }
                await __stockflowApp.activateView('shop', 'orders', { navigation: 'replace' });
                await __stockflowApp.consumeVNPayReturn();
                return;
            }
        }
        const productRoute = window.location.pathname.match(/^\/san-pham\/(\d+)$/);
        if (productRoute) {
            await __stockflowApp.activateView('shop', 'product', { productId: productRoute[1], navigation: 'none' });
            return;
        }
        const hash = window.location.hash.slice(1).split('/');
        if (hash[0] === 'product-shelf' && __stockflowApp.state.view === 'shop' && __stockflowApp.state.shopTab === 'catalog') return;
        if (hash[0] === 'portal' && __stockflowApp.isOperator()) {
            await __stockflowApp.activateView('portal', hash[1] === 'orders' ? 'queue' : hash[1] || 'queue', {
                navigation: 'replace',
            });
        } else if (hash[0] === 'shop') {
            if (!hash[1] || hash[1] === 'catalog') __stockflowApp.restoreCatalogLocation();
            await __stockflowApp.activateView('shop', hash[1] || 'catalog', { navigation: 'replace' });
        } else {
            if (!__stockflowApp.isOperator()) __stockflowApp.restoreCatalogLocation();
            await __stockflowApp.activateView(
                __stockflowApp.isOperator() ? 'portal' : 'shop',
                __stockflowApp.isOperator() ? (__stockflowApp.hasRole('MANAGER') ? 'reports' : 'queue') : 'catalog',
                { navigation: 'replace' },
            );
        }
        if (__stockflowApp.state.view === 'shop' && __stockflowApp.state.shopTab === 'catalog' && window.location.search) {
            __stockflowApp.scrollToCatalogResults();
        }
    }

function onLocationChange() {
        if (__stockflowApp.routedLocation === window.location.href) return;
        __stockflowApp.routedLocation = window.location.href;
        __stockflowApp.execute(__stockflowApp.routeFromLocation);
    }

function initializeContactWidget() {
        const widget = __stockflowApp.$('#contact-widget');
        const toggle = __stockflowApp.$('#contact-toggle');
        const panel = __stockflowApp.$('#contact-panel');
        const backToTop = __stockflowApp.$('#back-to-top');
        backToTop.hidden = false;
        function updateBackToTop() {
            const visible = window.scrollY >= 300;
            backToTop.classList.toggle('is-visible', visible);
            backToTop.inert = !visible;
            backToTop.setAttribute('aria-hidden', String(!visible));
        }
        window.addEventListener('scroll', updateBackToTop, { passive: true });
        updateBackToTop();
        backToTop.addEventListener('click', () => {
            setOpen(false);
            window.scrollTo({ top: 0, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'instant' : 'smooth' });
        });
        function setOpen(open) {
            widget.classList.toggle('is-open', open);
            toggle.setAttribute('aria-expanded', String(open));
            panel.setAttribute('aria-hidden', String(!open));
            panel.inert = !open;
        }
        toggle.addEventListener('click', () => setOpen(toggle.getAttribute('aria-expanded') !== 'true'));
        toggle.addEventListener('mouseenter', () => {
            if (window.matchMedia('(hover: hover) and (pointer: fine)').matches) setOpen(true);
        });
        widget.addEventListener('mouseleave', () => {
            if (!widget.contains(document.activeElement)) setOpen(false);
        });
        widget.addEventListener('focusout', (event) => {
            if (!widget.contains(event.relatedTarget)) setOpen(false);
        });
        widget.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') { setOpen(false); toggle.focus(); }
            if (event.key === 'ArrowDown' && event.target === toggle) {
                event.preventDefault(); setOpen(true); panel.querySelector('a:not([hidden])')?.focus();
            }
        });
        document.addEventListener('click', (event) => {
            if (!widget.contains(event.target)) setOpen(false);
        });
    }

function initializeAccountMenu() {
        const menu = __stockflowApp.$('#header-account');
        const toggle = menu.querySelector('summary');
        toggle.addEventListener('mouseenter', () => {
            if (__stockflowApp.state.user && window.matchMedia('(hover: hover) and (pointer: fine)').matches) menu.open = true;
        });
        menu.addEventListener('mouseleave', () => {
            if (!menu.contains(document.activeElement)) menu.open = false;
        });
        menu.addEventListener('focusout', (event) => {
            if (!menu.contains(event.relatedTarget)) menu.open = false;
        });
        menu.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') { menu.open = false; toggle.focus(); }
        });
        menu.addEventListener('click', (event) => {
            if (event.target.closest('button[data-action]')) menu.open = false;
        });
        document.addEventListener('click', (event) => {
            if (!menu.contains(event.target)) menu.open = false;
        });
    }

async function loadShopContact() {
        try {
            const contact = await __stockflowApp.api('/storefront/contact', { anonymous: true });
            if (!contact.zalo_url) return;
            const url = new URL(contact.zalo_url);
            if (url.protocol !== 'https:' || url.hostname !== 'zalo.me' || url.username || url.password) return;
            const link = __stockflowApp.$('#zalo-contact');
            link.href = url.href;
            link.hidden = false;
        } catch {
            // Contact availability must not prevent browsing or checkout.
        }
    }

async function loadProductReviews(productId, page = 0) {
        const target = __stockflowApp.$('#product-reviews-content');
        if (!target) return;
        try {
            const result = await __stockflowApp.api('/products/' + productId + '/reviews', { anonymous: true, query: { page }, channel: 'product-reviews' });
            if (!target.isConnected || !__stockflowApp.state.detailRoot || __stockflowApp.state.detailRoot.id !== productId) return;
            target.innerHTML = result.total_elements
                ? '<p><strong>' + Number(result.average_rating).toFixed(1) + '/5</strong> · ' + __stockflowApp.integer(result.total_elements)
                  + ' đánh giá · Dịch vụ: ' + Number(result.average_service_rating).toFixed(1) + '/5</p>'
                  + result.content.map(review => '<article class="product-review"><header><strong>' + __stockflowApp.escapeHtml(review.customer_name)
                    + '</strong><span class="meta-line">Đã mua hàng · ' + __stockflowApp.escapeHtml(__stockflowApp.dateTime(review.created_at)) + '</span></header>'
                    + '<p class="review-stars">' + '★'.repeat(review.rating) + '☆'.repeat(5 - review.rating)
                    + ' <span>Sản phẩm · Dịch vụ: ' + review.service_rating + '/5</span></p>'
                    + '<p class="review-comment">' + __stockflowApp.escapeHtml(review.comment) + '</p></article>').join('')
                : '<p class="meta-line">Chưa có đánh giá. Bạn có thể đánh giá trong chi tiết đơn hàng sau khi nhận hàng thành công.</p>';
            if (result.total_elements > result.size) {
                target.innerHTML += '<div class="review-pagination"><button class="button secondary small" type="button" data-action="review-page" data-product-id="'
                    + productId + '" data-page="' + (page - 1) + '" ' + (page === 0 ? 'disabled' : '') + '>Trước</button><span>Trang '
                    + (page + 1) + '</span><button class="button secondary small" type="button" data-action="review-page" data-product-id="'
                    + productId + '" data-page="' + (page + 1) + '" ' + ((page + 1) * result.size >= result.total_elements ? 'disabled' : '') + '>Sau</button></div>';
            }
        } catch (error) {
            if (!target.isConnected || error.name === 'AbortError') return;
            target.innerHTML = '<p class="meta-line">Chưa tải được đánh giá.</p><button class="button secondary small" type="button" data-action="review-page" data-product-id="'
                + productId + '" data-page="' + page + '">Thử lại</button>';
        }
    }

async function openProductReview(orderId, productId) {
        const epoch = __stockflowApp.state.epoch;
        const reviews = await __stockflowApp.api('/orders/' + orderId + '/reviews');
        if (epoch !== __stockflowApp.state.epoch || !__stockflowApp.hasRole('CUSTOMER')) return;
        const existing = reviews.find(review => review.product_id === productId);
        const form = __stockflowApp.$('#review-form');
        form.reset();
        form.dataset.orderId = String(orderId);
        form.dataset.productId = String(productId);
        form.dataset.epoch = String(epoch);
        __stockflowApp.$('#review-title').textContent = existing ? 'Đánh giá của bạn' : 'Đánh giá sau khi nhận hàng';
        __stockflowApp.$('#review-rating').value = String(existing?.rating || 5);
        __stockflowApp.$('#review-service-rating').value = String(existing?.service_rating || 5);
        __stockflowApp.$('#review-comment').value = existing?.comment || '';
        __stockflowApp.$('#review-rating').disabled = Boolean(existing);
        __stockflowApp.$('#review-service-rating').disabled = Boolean(existing);
        __stockflowApp.$('#review-comment').readOnly = Boolean(existing);
        __stockflowApp.$('#review-submit').hidden = Boolean(existing);
        __stockflowApp.openDialog('review-dialog');
    }

async function submitProductReview() {
        const form = __stockflowApp.$('#review-form');
        if (Number(form.dataset.epoch) !== __stockflowApp.state.epoch || !__stockflowApp.hasRole('CUSTOMER')) return;
        const comment = __stockflowApp.$('#review-comment').value.trim();
        if (!comment) throw new Error('Vui lòng nhập nhận xét.');
        const epoch = __stockflowApp.state.epoch;
        await __stockflowApp.api('/orders/' + form.dataset.orderId + '/reviews', { method: 'POST', body: {
            product_id: Number(form.dataset.productId), rating: Number(__stockflowApp.$('#review-rating').value),
            service_rating: Number(__stockflowApp.$('#review-service-rating').value), comment,
        } });
        if (epoch !== __stockflowApp.state.epoch) return;
        __stockflowApp.$('#review-dialog').close();
        __stockflowApp.notify('success', 'Cảm ơn bạn đã chia sẻ đánh giá!');
        if (__stockflowApp.state.detailRoot && __stockflowApp.state.shopTab === 'product') await __stockflowApp.loadProductReviews(__stockflowApp.state.detailRoot.id);
    }

function initializeAuthArtwork() {
        const scene = __stockflowApp.$('#auth-art-scene');
        const finePointer = window.matchMedia('(pointer: fine)');
        let frame = null;
        let x = 0, y = 0, targetX = 0, targetY = 0;
        function allowed() {
            return document.body.classList.contains('auth-page-open') && !document.hidden && finePointer.matches && !__stockflowApp.reducedStorefrontMotion.matches && scene.getBoundingClientRect().width > 0;
        }
        function paint() {
            scene.style.setProperty('--auth-rotate-x', x.toFixed(2) + 'deg');
            scene.style.setProperty('--auth-rotate-y', y.toFixed(2) + 'deg');
        }
        function tick() {
            frame = null;
            if (!allowed()) { reset(); return; }
            x += (targetX - x) * .13;
            y += (targetY - y) * .13;
            paint();
            if (Math.abs(targetX - x) + Math.abs(targetY - y) > .04) frame = window.requestAnimationFrame(tick);
        }
        function reset() {
            if (frame !== null) window.cancelAnimationFrame(frame);
            frame = null;
            x = y = targetX = targetY = 0;
            paint();
        }
        __stockflowApp.resetAuthArtwork = reset;
        document.addEventListener('pointermove', event => {
            if (event.pointerType === 'touch' || !allowed()) return;
            const rect = scene.getBoundingClientRect();
            const horizontal = Math.max(-1, Math.min(1, (event.clientX - rect.left - rect.width / 2) / (window.innerWidth / 2)));
            const vertical = Math.max(-1, Math.min(1, (event.clientY - rect.top - rect.height / 2) / (window.innerHeight / 2)));
            targetX = -vertical * 30;
            targetY = horizontal * 42;
            if (frame === null) frame = window.requestAnimationFrame(tick);
        }, { passive: true });
        document.documentElement.addEventListener('pointerleave', reset);
        document.addEventListener('visibilitychange', () => { if (document.hidden) reset(); });
        window.addEventListener('blur', reset);
        window.addEventListener('resize', reset, { passive: true });
        __stockflowApp.reducedStorefrontMotion.addEventListener('change', reset);
        finePointer.addEventListener('change', reset);
    }

async function initialize() {
        window.StockFlowPasswordReset.initialize({ api: __stockflowApp.api, notify: __stockflowApp.notify, setAuthMode: __stockflowApp.setAuthMode });
        __stockflowApp.initializeAuthArtwork();
        __stockflowApp.initializeAccountMenu();
        __stockflowApp.initializeContactWidget();
        void __stockflowApp.loadSpecificationOptions();
        void __stockflowApp.loadShopContact();
        const epoch = __stockflowApp.state.epoch;
        __stockflowApp.renderContext();
        __stockflowApp.renderCart();
        __stockflowApp.renderOrder();
        __stockflowApp.renderCatalogLoading();
        __stockflowApp.setAuthMode('login');
        let saved;
        try {
            saved = JSON.parse(sessionStorage.getItem(__stockflowApp.SESSION_KEY) || 'null');
        } catch {
            /* Storage lỗi không ngăn xem cửa hàng công khai. */
        }
        if (saved?.token) {
            __stockflowApp.state.token = saved.token;
            try {
                __stockflowApp.state.user = await __stockflowApp.api('/users/me', { channel: 'identity' });
            } catch (error) {
                if (error.name !== 'AbortError') __stockflowApp.handleError(error);
            }
            if (epoch !== __stockflowApp.state.epoch) return;
        }
        __stockflowApp.renderContext();
        await __stockflowApp.restoreCart();
        if (epoch !== __stockflowApp.state.epoch) return;
        if (['/login','/register'].includes(window.location.pathname) && !__stockflowApp.state.user) {
            await __stockflowApp.routeFromLocation();
            return;
        }
        await __stockflowApp.loadReferences();
        if (epoch !== __stockflowApp.state.epoch) return;
        await __stockflowApp.routeFromLocation();
    }

function renderSpecificationValueOptions() {
        const name = __stockflowApp.$('#catalog-spec-name').value.trim().toLowerCase();
        const values = [...new Set(__stockflowApp.state.specificationOptions.filter(option => option.name.trim().toLowerCase() === name).map(option => option.value))];
        __stockflowApp.$('#catalog-spec-values').innerHTML = values.map(value => '<option value="' + __stockflowApp.escapeHtml(value) + '"></option>').join('');
    }

async function loadSpecificationOptions() {
        try {
            __stockflowApp.state.specificationOptions = await __stockflowApp.api('/products/specification-options', { anonymous: true });
            const names = [...new Set(__stockflowApp.state.specificationOptions.map(option => option.name))];
            __stockflowApp.$('#catalog-spec-names').innerHTML = names.map(name => '<option value="' + __stockflowApp.escapeHtml(name) + '"></option>').join('');
            __stockflowApp.renderSpecificationValueOptions();
        } catch { /* Manual entry remains available when suggestions fail. */ }
    }

export function register() {
Object.defineProperties(__stockflowApp, {
"currentPagePath": { get: () => currentPagePath },
"replaceHash": { get: () => replaceHash },
"pushPage": { get: () => pushPage },
"activateView": { get: () => activateView },
"refreshSection": { get: () => refreshSection },
"changePage": { get: () => changePage },
"restoreCatalogLocation": { get: () => restoreCatalogLocation },
"pendingVNPayReturn": { get: () => pendingVNPayReturn, set: value => { pendingVNPayReturn = value; } },
"consumeVNPayReturn": { get: () => consumeVNPayReturn },
"routeFromLocation": { get: () => routeFromLocation },
"onLocationChange": { get: () => onLocationChange },
"initializeContactWidget": { get: () => initializeContactWidget },
"initializeAccountMenu": { get: () => initializeAccountMenu },
"loadShopContact": { get: () => loadShopContact },
"loadProductReviews": { get: () => loadProductReviews },
"openProductReview": { get: () => openProductReview },
"submitProductReview": { get: () => submitProductReview },
"initializeAuthArtwork": { get: () => initializeAuthArtwork },
"initialize": { get: () => initialize },
"renderSpecificationValueOptions": { get: () => renderSpecificationValueOptions },
"loadSpecificationOptions": { get: () => loadSpecificationOptions }
});
}

export function initializeFeature() {
document.addEventListener(
        'error',
        (event) => {
            const image = event.target;
            if (image instanceof HTMLImageElement && image.hasAttribute('data-search-image')) {
                image.hidden = true;
                return;
            }
            if (image instanceof HTMLImageElement && image.hasAttribute('data-sticky-image')) {
                image.hidden = true;
                return;
            }
            if (image instanceof HTMLImageElement && image.hasAttribute('data-brand-logo')) {
                image.hidden = true;
                const media = image.closest('.brand-logo-media');
                if (media) {
                    media.classList.add('without-logo');
                    __stockflowApp.$('.brand-monogram', media).hidden = false;
                }
                return;
            }
            if (image instanceof HTMLImageElement && image.hasAttribute('data-cart-thumbnail')) {
                image.hidden = true;
                __stockflowApp.$('.cart-thumbnail-empty', image.closest('.cart-thumbnail')).hidden = false;
                return;
            }
            if (image instanceof HTMLImageElement && image.hasAttribute('data-color-thumbnail')) {
                image.hidden = true;
                return;
            }
            if (image instanceof HTMLImageElement && image.hasAttribute('data-gallery-thumbnail')) {
                image.hidden = true;
                __stockflowApp.$('.gallery-thumb-error', image.closest('.gallery-thumbnail')).hidden = false;
                return;
            }
            if (image instanceof HTMLImageElement && image.hasAttribute('data-gallery-preview-image')) {
                image.hidden = true;
                __stockflowApp.$('.gallery-preview-error', image.closest('.gallery-preview-item')).hidden = false;
                return;
            }
            if (image instanceof HTMLImageElement && image.hasAttribute('data-catalog-image')) {
                image.hidden = true;
                return;
            }
            // Banner giữ tên/giá/liên kết khi CDN lỗi; không thay bằng ảnh của sản phẩm khác.
            if (image instanceof HTMLImageElement && image.hasAttribute('data-hero-image')) {
                image.hidden = true;
                __stockflowApp.$('.hero-image-error', image.closest('.hero-device-media')).hidden = false;
                return;
            }
            if (!(image instanceof HTMLImageElement) || !image.classList.contains('product-card-img')) return;
            // Ảnh thay thế sẽ chạy hiệu ứng khi tải xong; lỗi tải không được giữ trạng thái ảnh mờ.
            image.classList.remove('gallery-image-enter');
            const fallback = image.dataset.imageFallback;
            if (!image.dataset.fallbackAttempted && fallback && image.getAttribute('src') !== fallback) {
                image.dataset.fallbackAttempted = 'true';
                image.src = fallback;
                const caption = __stockflowApp.$('.product-photo-caption', image.closest('.product-card-img-wrap'));
                caption.textContent = 'Ảnh minh họa';
                caption.hidden = false;
                return;
            }
            image.hidden = true;
            const wrapper = image.closest('.product-card-img-wrap');
            wrapper.classList.add('product-image-unavailable');
            __stockflowApp.$('.product-image-error', wrapper).hidden = false;
        },
        true,
    );
document.addEventListener('click', (event) => {
        if (__stockflowApp.state.categoryMenuOpen && !__stockflowApp.isShopCategoryTarget(event.target)) __stockflowApp.setShopCategoryMenu(false);
        if (!__stockflowApp.state.categoryMenuInline && __stockflowApp.state.homeCategoryId !== null && !event.target.closest('#home-categories'))
            __stockflowApp.closeHomeCategoryMenu();
        const catalogLink = event.target.closest('[data-catalog-link]');
        if (catalogLink) {
            if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            event.preventDefault();
            window.history.pushState(null, '', catalogLink.href);
            __stockflowApp.execute(async () => {
                await __stockflowApp.routeFromLocation();
                __stockflowApp.scrollToCatalogResults();
            });
            return;
        }
        const productLink = event.target.closest('[data-product-link]');
        if (productLink) {
            // Giữ hành vi liên kết native khi mở tab mới bằng Ctrl/Cmd, Shift hoặc nút chuột giữa.
            if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            __stockflowApp.closeSearchSuggestions();
            event.preventDefault();
            if (__stockflowApp.$('#wishlist-dialog').open) __stockflowApp.$('#wishlist-dialog').close();
            __stockflowApp.execute(() => __stockflowApp.activateView('shop', 'product', { productId: productLink.dataset.productId }));
            return;
        }
        const shopLink = event.target.closest('[data-shop-link]');
        if (shopLink) {
            if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            event.preventDefault();
            __stockflowApp.execute(() => __stockflowApp.activateView('shop', 'catalog'));
            return;
        }
        const button = event.target.closest('button');
        if (!button || button.disabled) return;
        if (button.dataset.demoRole) {
            __stockflowApp.execute(() => __stockflowApp.authenticate(__stockflowApp.DEMO_ACCOUNTS[button.dataset.demoRole], false, true));
            return;
        }
        if (button.dataset.shopTab) {
            __stockflowApp.execute(() => __stockflowApp.activateView('shop', button.dataset.shopTab));
            return;
        }
        if (button.dataset.action === 'toggle-catalog-menu') {
            if (__stockflowApp.hasRole('ADMIN')) {
                __stockflowApp.state.catalogExpanded = !__stockflowApp.state.catalogExpanded;
                __stockflowApp.renderCatalogNavigation();
            }
            return;
        }
        if (button.dataset.portalTab) {
            __stockflowApp.execute(() => __stockflowApp.activateView('portal', button.dataset.portalTab));
            return;
        }
        if (button.dataset.authMode) {
            __stockflowApp.setAuthMode(button.dataset.authMode);
            return;
        }
        if (button.hasAttribute('data-category')) {
            __stockflowApp.$('#catalog-category').value = button.dataset.category;
            __stockflowApp.state.catalogBrandId = '';
            __stockflowApp.renderCatalogBrandOptions();
            __stockflowApp.execute(__stockflowApp.reloadCatalogFilters);
            return;
        }
        if (button.dataset.page) {
            __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.changePage(button.dataset.page, Number(button.dataset.direction))));
            return;
        }
        const id = Number(button.dataset.id);
        const action = button.dataset.action;
        if (action === 'review-product') {
            __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.openProductReview(id, Number(button.dataset.productId))));
        } else if (action === 'review-page') {
            __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.loadProductReviews(Number(button.dataset.productId), Number(button.dataset.page))));
        } else if (action === 'retry-search-suggestions') {
            // Nút thử lại sẽ bị ẩn khi dựng skeleton; chuyển focus về input trước để không mất phiên gợi ý.
            __stockflowApp.$('#catalog-query').focus({ preventScroll: true });
            __stockflowApp.queueSearchSuggestions({ immediate: true });
        } else if (action === 'quick-price')
            __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.applyQuickPrice(button.dataset.priceChip)));
        // Đọc catalog có cơ chế hủy request cũ; giữ nút hãng có focus và cho phép đổi lựa chọn ngay.
        else if (action === 'quick-brand') __stockflowApp.execute(() => __stockflowApp.applyCatalogBrand(button.dataset.brandChip));
        else if (action === 'refresh-bestsellers') __stockflowApp.execute(() => __stockflowApp.busy(button, __stockflowApp.loadBestsellers));
        else if (action === 'queue-status')
            __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.applyQueueStatus(button.dataset.queueStatus)));
        else if (action === 'sticky-add-cart') {
            const form = __stockflowApp.$('#shop-product-add-form');
            const main = form && __stockflowApp.$('button[type="submit"]', form);
            if (main && !main.disabled) form.requestSubmit(main);
        } else if (action === 'explore-products') {
            __stockflowApp.$('#cart-dialog').close();
            __stockflowApp.execute(async () => {
                if (__stockflowApp.state.view !== 'shop' || __stockflowApp.state.shopTab !== 'catalog') await __stockflowApp.activateView('shop', 'catalog');
                __stockflowApp.scrollToCatalogResults({
                    behavior: __stockflowApp.reducedStorefrontMotion.matches ? 'auto' : 'smooth',
                });
            });
        } else if (action === 'open-auth') __stockflowApp.openAuth();
        else if (action === 'close-auth') {
            const path = __stockflowApp.loginReturnPath || '/#shop';
            __stockflowApp.setLoginPage(false);
            window.history.replaceState(null, '', path);
            __stockflowApp.execute(__stockflowApp.routeFromLocation);
        }
        else if (action === 'toggle-auth-password') {
            const input = __stockflowApp.$('#auth-password');
            const visible = input.type === 'password';
            input.type = visible ? 'text' : 'password';
            button.textContent = visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu';
            button.setAttribute('aria-pressed', String(visible));
        }
        else if (action === 'open-cart') {
            __stockflowApp.renderCart();
            __stockflowApp.openDialog('cart-dialog');
        } else if (action === 'close-cart') __stockflowApp.$('#cart-dialog').close();
        else if (action === 'open-portal') __stockflowApp.execute(() => __stockflowApp.activateView('portal', __stockflowApp.state.portalTab));
        else if (action === 'open-shop') __stockflowApp.execute(() => __stockflowApp.activateView('shop', 'catalog'));
        else if (action === 'my-orders') __stockflowApp.execute(() => __stockflowApp.activateView('shop', 'orders'));
        else if (action === 'my-account') __stockflowApp.execute(() => __stockflowApp.activateView('shop', 'account'));
        else if (action === 'open-wishlist') __stockflowApp.execute(async () => {
            __stockflowApp.syncWishlist(); __stockflowApp.openDialog('wishlist-dialog'); await __stockflowApp.loadWishlist();
        });
        else if (action === 'close-wishlist') __stockflowApp.$('#wishlist-dialog').close();
        else if (action === 'view-admin-user') __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.viewAdminUser(id)));
        else if (action === 'toggle-admin-user') __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.toggleAdminUser(id)));
        else if (action === 'use-default-address') __stockflowApp.execute(() => __stockflowApp.prepareDefaultCheckoutAddress(true));
        else if (action === 'different-address') {
            if (__stockflowApp.state.authBusy || __stockflowApp.$('#create-order').getAttribute('aria-busy') === 'true') return;
            __stockflowApp.checkoutDifferentAddress = true;
            __stockflowApp.shippingLocationVersion++; __stockflowApp.shippingFeeVersion++; __stockflowApp.checkoutShippingQuote = null;
            __stockflowApp.$('#checkoutProvince').value = ''; __stockflowApp.$('#checkoutStreetAddress').value = '';
            __stockflowApp.resetLocationSelect('checkoutDistrict', 'Chọn quận / huyện'); __stockflowApp.resetLocationSelect('checkoutWard', 'Chọn phường / xã');
            __stockflowApp.renderShippingTotal(); __stockflowApp.$('#checkoutProvince').focus();
        }
        else if (action === 'toggle-wishlist') __stockflowApp.execute(() => __stockflowApp.toggleWishlist(id));
        else if (action === 'dismiss-notice') __stockflowApp.$('#api-notice').hidden = true;
        else if (action === 'dismiss-toast') button.closest('.toast').remove();
        else if (action === 'logout') {
            __stockflowApp.clearSession();
            __stockflowApp.renderIdentity();
            __stockflowApp.replaceHash();
            __stockflowApp.notify('success', 'Đã đăng xuất. Bạn có thể tiếp tục mua sắm.');
            __stockflowApp.execute(() => {
                return __stockflowApp.loadReferences().then(__stockflowApp.loadCatalog);
            });
        } else if (action === 'toggle-category-menu') __stockflowApp.setShopCategoryMenu(!__stockflowApp.state.categoryMenuOpen);
        else if (action === 'close-category-menu') __stockflowApp.setShopCategoryMenu(false, { restoreFocus: true });
        else if (action === 'preview-category') __stockflowApp.openCategoryPreview(button.dataset.menuCategory);
        else if (action === 'browse-category') __stockflowApp.execute(() => __stockflowApp.browseShopCategory(button.dataset.menuCategory));
        else if (action === 'browse-price-range') {
            // Giữ hãng khi đổi giá trong cùng nhóm, dù dùng menu header hay menu bên trái.
            const categoryId = button.dataset.menuCategory ?? __stockflowApp.state.menuCategoryId;
            __stockflowApp.execute(() =>
                __stockflowApp.browseShopCategory(
                    categoryId,
                    button.dataset.priceRange,
                    __stockflowApp.$('#catalog-category').value === categoryId ? __stockflowApp.state.catalogBrandId : '',
                ),
            );
        } else if (action === 'browse-brand')
            __stockflowApp.execute(() => __stockflowApp.browseShopCategory(button.dataset.menuCategory, 'all', button.dataset.brandId));
        else if (action === 'clear-catalog-filter') __stockflowApp.execute(() => __stockflowApp.clearCatalogFilter(button.dataset.filter));
        else if (action === 'scroll-product-info') {
            document.getElementById(button.dataset.target)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
        } else if (action === 'refresh-product-availability') __stockflowApp.execute(__stockflowApp.loadProductAvailability);
        else if (action === 'select-product-branch') {
            __stockflowApp.state.branchId = String(id);
            __stockflowApp.saveCart();
            __stockflowApp.renderWarehouses();
            __stockflowApp.execute(__stockflowApp.loadProductAvailability);
        } else if (action === 'gallery-select') __stockflowApp.selectGalleryImage(Number(button.dataset.photoIndex));
        else if (action === 'gallery-step') {
            const gallery = __stockflowApp.$('.shop-product-gallery');
            if (gallery) __stockflowApp.selectGalleryImage(Number(gallery.dataset.selectedIndex) + Number(button.dataset.direction));
        } else if (action === 'select-product-version') {
            __stockflowApp.selectProductVersion(id);
        } else if (action === 'manage-product-version') {
            __stockflowApp.execute(() =>
                __stockflowApp.manageProductVariants(
                    Number(__stockflowApp.$('#product-variants-dialog').dataset.productId),
                    id,
                    __stockflowApp.$('#product-variants-dialog').dataset.panel,
                ),
            );
        } else if (action === 'configuration-panel' && __stockflowApp.hasRole('ADMIN')) {
            __stockflowApp.setConfigurationPanel(button.dataset.panel);
        } else if (action === 'delete-product-version' && __stockflowApp.hasRole('ADMIN')) {
            __stockflowApp.confirmConfigurationArchive('version', id);
        } else if (action === 'delete-product-color' && __stockflowApp.hasRole('ADMIN')) {
            __stockflowApp.confirmConfigurationArchive('color', id);
        } else if (action === 'restore-product-version' && __stockflowApp.hasRole('ADMIN')) {
            __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.restoreProductConfiguration('version', id)));
        } else if (action === 'restore-product-color' && __stockflowApp.hasRole('ADMIN')) {
            __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.restoreProductConfiguration('color', id)));
        } else if (action === 'select-product-color') {
            __stockflowApp.selectProductSku(id);
            __stockflowApp.$('[data-action="select-product-color"][data-id="' + id + '"]')?.focus({ preventScroll: true });
        } else if (action === 'add-cart') {
            if (__stockflowApp.state.products.get(id)?.variants?.length && __stockflowApp.$('#wishlist-dialog').open) __stockflowApp.$('#wishlist-dialog').close();
            __stockflowApp.execute(() =>
                __stockflowApp.state.products.get(id)?.variants?.length
                    ? __stockflowApp.activateView('shop', 'product', { productId: id })
                    : __stockflowApp.addCart(id),
            );
        } else if (action === 'remove-cart') {
            if (__stockflowApp.state.cartLoading || __stockflowApp.state.cartRestoreFailed || __stockflowApp.$('#create-order').getAttribute('aria-busy') === 'true') return;
            __stockflowApp.state.cart.delete(id);
            __stockflowApp.saveCart();
            __stockflowApp.renderCart();
        } else if (action === 'retry-cart') {
            __stockflowApp.execute(() => __stockflowApp.busy(button, __stockflowApp.restoreCart));
        } else if (action === 'cart-plus' || action === 'cart-minus') {
            const item = __stockflowApp.state.cart.get(id);
            if (item) __stockflowApp.execute(() => __stockflowApp.setCartQuantity(id, item.quantity + (action === 'cart-plus' ? 1 : -1)));
        } else if (action === 'refresh') __stockflowApp.execute(() => __stockflowApp.busy(button, __stockflowApp.refreshSection));
        else if (action === 'view-order') __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.lookupOrder(id)));
        else if (action === 'pay-order') __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.mutateOrder(id, 'pay')));
        else if (action === 'cod-order') __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.mutateOrder(id, 'cod')));
        else if (action === 'vnpay-order') __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.payWithVNPay(id)));
        else if (action === 'cancel-order') __stockflowApp.confirmOrderAction(id, 'cancel');
        else if (action === 'operate-order') {
            if (button.dataset.operation === 'return') __stockflowApp.confirmOrderAction(id, 'return');
            else __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.mutateOrder(id, button.dataset.operation)));
        } else if (action === 'dismiss-confirm') {
            __stockflowApp.state.pendingMutation = null;
            __stockflowApp.$('#confirm-dialog').close();
        } else if (action === 'confirm-mutation') {
            const pending = __stockflowApp.state.pendingMutation;
            if (!pending || pending.epoch !== __stockflowApp.state.epoch) return;
            __stockflowApp.state.pendingMutation = null;
            __stockflowApp.$('#confirm-dialog').close();
            if (pending.handler) {
                __stockflowApp.execute(() => __stockflowApp.busy(button, pending.handler));
            } else {
                __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.mutateOrder(pending.id, pending.action)));
            }
        } else if (action === 'open-product-create' && __stockflowApp.hasRole('ADMIN')) {
            __stockflowApp.openProductCreate();
        } else if (action === 'open-category-create' && __stockflowApp.hasRole('ADMIN')) {
            const form = __stockflowApp.$('#category-create');
            if (form) form.reset();
            __stockflowApp.openDialog('category-create-dialog');
        } else if (action === 'edit-category') {
            __stockflowApp.openCategoryEdit(id);
        } else if (action === 'delete-category') {
            __stockflowApp.confirmCategoryDelete(id);
        } else if (action === 'open-brand-create' && __stockflowApp.hasRole('ADMIN')) {
            __stockflowApp.$('#brand-create').reset();
            __stockflowApp.renderBrandLogoPreview(__stockflowApp.$('#create-brand-logo'));
            __stockflowApp.openDialog('brand-create-dialog');
        } else if (action === 'edit-brand-logo') {
            __stockflowApp.openBrandLogoEdit(id);
        } else if (action === 'remove-brand-logo' && __stockflowApp.hasRole('ADMIN')) {
            __stockflowApp.$('#edit-brand-logo').value = '';
            __stockflowApp.renderBrandLogoPreview(__stockflowApp.$('#edit-brand-logo'));
        } else if (action === 'view-product') {
            __stockflowApp.execute(() => __stockflowApp.viewProductDetail(id));
        } else if (action === 'edit-product' && __stockflowApp.hasRole('ADMIN')) {
            __stockflowApp.openProductEdit(id);
        } else if (action === 'manage-product-variants' && __stockflowApp.hasRole('ADMIN')) {
            __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.manageProductVariants(id)));
        } else if (action === 'manage-product-colors' && __stockflowApp.hasRole('ADMIN')) {
            __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.manageProductVariants(id, null, 'colors')));
        } else if (action === 'copy-product-color' && __stockflowApp.hasRole('ADMIN')) {
            __stockflowApp.copyProductColor(button.closest('form'));
        } else if (action === 'add-spec-row' && __stockflowApp.hasRole('ADMIN')) {
            const editor = button.closest('[data-spec-editor]');
            __stockflowApp.execute(() => __stockflowApp.appendSpecRow(editor));
        } else if (action === 'remove-spec-row' && __stockflowApp.hasRole('ADMIN')) {
            const editor = button.closest('[data-spec-editor]');
            button.closest('.spec-editor-row').remove();
            __stockflowApp.$('[data-action="add-spec-row"]', editor).disabled = false;
        } else if (action === 'toggle-status' && __stockflowApp.hasRole('ADMIN')) {
            __stockflowApp.toggleProductStatus(id);
        } else if (action === 'reset-manage-filter') {
            __stockflowApp.$('#manage-query').value = '';
            __stockflowApp.$('#manage-category').value = '';
            __stockflowApp.$('#manage-status').value = '';
            __stockflowApp.state.pages.manage = 0;
            __stockflowApp.execute(__stockflowApp.loadManage);
        } else if (action === 'close-dialog') {
            button.closest('dialog')?.close();
        }
    });
document.addEventListener('change', (event) => {
        const input = event.target;
        if (input.id === 'admin-user-role') __stockflowApp.updateAdminRoleFields();
        if (input.id === 'cart-select-all' || input.hasAttribute('data-cart-selected')) {
            if (__stockflowApp.state.cartLoading || __stockflowApp.state.cartRestoreFailed || __stockflowApp.state.authBusy || __stockflowApp.$('#create-order').getAttribute('aria-busy') === 'true') return;
            if (input.id === 'cart-select-all') __stockflowApp.state.cart.forEach(item => { item.selected = input.checked; });
            else {
                const item = __stockflowApp.state.cart.get(Number(input.dataset.cartSelected));
                if (item) item.selected = input.checked;
            }
            const id = input.dataset.cartSelected;
            __stockflowApp.saveCart();
            __stockflowApp.renderCart();
            if (id) __stockflowApp.$('[data-cart-selected="' + id + '"]')?.focus({ preventScroll: true });
            return;
        }
        if (
            (input.id === 'configuration-version-select' || input.id === 'configuration-show-archived') &&
            __stockflowApp.hasRole('ADMIN')
        ) {
            const dialog = __stockflowApp.$('#product-variants-dialog');
            if (input.id === 'configuration-show-archived') dialog.dataset.showArchived = String(input.checked);
            __stockflowApp.execute(() =>
                __stockflowApp.manageProductVariants(
                    Number(dialog.dataset.productId),
                    input.id === 'configuration-version-select'
                        ? Number(input.value)
                        : Number(dialog.dataset.versionId),
                    dialog.dataset.panel,
                ),
            );
        } else if (input.hasAttribute('data-store-warehouse')) {
            __stockflowApp.state.branchId = input.value;
            __stockflowApp.execute(__stockflowApp.refreshCheckoutFee);
            __stockflowApp.saveCart();
            __stockflowApp.renderWarehouses();
            __stockflowApp.refreshProductStock();
            __stockflowApp.execute(__stockflowApp.loadProductAvailability);
        } else if (input.dataset.cartQuantity) {
            __stockflowApp.execute(() => __stockflowApp.setCartQuantity(Number(input.dataset.cartQuantity), Number(input.value)));
        } else if (input.id === 'catalog-category') {
            __stockflowApp.state.catalogBrandId = '';
            __stockflowApp.renderCatalogBrandOptions();
            __stockflowApp.execute(__stockflowApp.reloadCatalogFilters);
        } else if (input.id === 'catalog-brand') {
            __stockflowApp.execute(() => __stockflowApp.applyCatalogBrand(input.value));
        } else if (input.id === 'catalog-sort') {
            __stockflowApp.state.catalogSort = input.value;
            __stockflowApp.execute(__stockflowApp.reloadCatalogFilters);
        } else if (input.id === 'update-product') {
            __stockflowApp.prepareProductImageUpdate();
        } else if (input.id === 'remove-product-image') {
            __stockflowApp.renderProductImagePreview(__stockflowApp.$('#update-product-image'));
        } else if (input.dataset.imageInput) {
            __stockflowApp.renderProductImagePreview(input);
        } else if (input.dataset.galleryInput) {
            __stockflowApp.renderGalleryInputPreview(input);
        }
    });
document.addEventListener('submit', (event) => {
        event.preventDefault();
        const form = event.target;
        if (!form.reportValidity()) return;
        const button = __stockflowApp.$('button[type="submit"]', form);
        const handlers = {
            'catalog-spec-filter': async () => {
                const name = __stockflowApp.$('#catalog-spec-name').value.trim();
                const value = __stockflowApp.$('#catalog-spec-value').value.trim();
                if (!name || !value) throw new Error('Vui lòng nhập cả tên thông số và giá trị.');
                __stockflowApp.state.catalogSpecName = name;
                __stockflowApp.state.catalogSpecValue = value;
                await __stockflowApp.reloadCatalogFilters();
            },
            'review-form': __stockflowApp.submitProductReview,
            'catalog-filter': () => __stockflowApp.reloadCatalogFilters(),
            'catalog-price-filter': __stockflowApp.applyCatalogPriceDraft,
            'queue-filter': () => {
                __stockflowApp.state.order = null;
                __stockflowApp.renderOrder();
                __stockflowApp.state.pages.queue = 0;
                return __stockflowApp.loadQueue();
            },
            'order-create': __stockflowApp.createOrder,
            'profile-form': __stockflowApp.saveProfile,
            'order-lookup': () => __stockflowApp.lookupOrder(Number(__stockflowApp.$('#order-id').value)),
            'product-create': () => __stockflowApp.createProduct(form),
            'product-update': () => __stockflowApp.updateProduct(form),
            'product-variant-create': () => __stockflowApp.saveProductVariant(form, true),
            'product-version-create': () => __stockflowApp.saveProductVersion(form, true),
            'shop-product-add-form': () => __stockflowApp.addProductFromDetail(form),
            'category-create': () => __stockflowApp.createCategory(form),
            'category-edit': () => __stockflowApp.updateCategory(form),
            'brand-create': () => __stockflowApp.createBrand(form),
            'brand-logo-edit': () => __stockflowApp.saveBrandLogo(form),
            'manage-filter': () => {
                __stockflowApp.state.pages.manage = 0;
                return __stockflowApp.loadManage();
            },
            'inventory-filter': () => {
                __stockflowApp.state.pages.inventory = 0;
                return __stockflowApp.loadInventory();
            },
            'admin-users-filter': () => { __stockflowApp.state.pages.users = 0; return __stockflowApp.loadAdminUsers(); },
            'admin-user-role-form': () => __stockflowApp.saveAdminUserRole(form),
            'stock-in': () => __stockflowApp.stockIn(form),
            'ledger-filter': () => {
                __stockflowApp.state.pages.ledger = 0;
                return __stockflowApp.loadLedger();
            },
            'report-filter': () => {
                if (
                    __stockflowApp.$('#report-from').value &&
                    __stockflowApp.$('#report-to').value &&
                    __stockflowApp.$('#report-from').value > __stockflowApp.$('#report-to').value
                ) {
                    throw new Error('Từ ngày phải trước hoặc bằng Đến ngày.');
                }
                ['revenue', 'top', 'low'].forEach((name) => {
                    __stockflowApp.state.pages[name] = 0;
                });
                return __stockflowApp.loadReports();
            },
        };
        if (form.matches('.version-admin-edit')) {
            event.preventDefault();
            __stockflowApp.execute(() => __stockflowApp.busy(form.querySelector('button[type="submit"]'), () => __stockflowApp.saveProductVersion(form)));
            return;
        }
        if (form.matches('.variant-admin-edit')) {
            __stockflowApp.execute(() => __stockflowApp.busy(button, () => __stockflowApp.saveProductVariant(form)));
            return;
        }
        if (form.id === 'otp-form') {
            __stockflowApp.execute(() => __stockflowApp.authenticate({ email: __stockflowApp.otpEmail, otp: __stockflowApp.$('#otp-code').value.trim() }, false, false, true));
        } else if (form.id === 'auth-form') {
            const body = { email: __stockflowApp.$('#auth-email').value.trim(), password: __stockflowApp.$('#auth-password').value };
            if (__stockflowApp.state.authMode === 'register') body.full_name = __stockflowApp.$('#auth-name').value.trim();
            __stockflowApp.execute(() => __stockflowApp.authenticate(body, __stockflowApp.state.authMode === 'register'));
        } else if (handlers[form.id]) __stockflowApp.execute(() => __stockflowApp.busy(button, handlers[form.id]));
    });
(pendingVNPayReturn = null);
window.addEventListener('popstate', __stockflowApp.onLocationChange);
window.addEventListener('hashchange', __stockflowApp.onLocationChange);
window.setInterval(__stockflowApp.updateCountdown, 1000);
__stockflowApp.$('#catalog-spec-name').addEventListener('input', __stockflowApp.renderSpecificationValueOptions);
__stockflowApp.execute(__stockflowApp.initialize);
}
