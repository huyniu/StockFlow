import { app as __stockflowApp } from './context.js';

let SESSION_KEY, wishlistOwner, wishlistIds, wishlistLoadVersion, checkoutDifferentAddress, administeredUsers, CART_KEY, CHECKOUT_ATTEMPT_KEY, checkoutAttempt, MAX_CART_ITEMS, ORDER_REFRESH_MS, EXPIRED_ORDER_REFRESH_MS, READ_TIMEOUT_MS, MAX_QUANTITY, MAX_GALLERY_IMAGES, CATALOG_PRICE_RANGES, LAPTOP_PRICE_RANGES, AUDIO_PRICE_RANGES, DEMO_ACCOUNTS, STATUS_LABELS, MOVEMENT_LABELS, ROLE_CLASSES, ROLE_LABELS, PORTAL_TITLES, PRODUCT_IMAGES, PRODUCT_IMAGE_RULES, CATEGORY_IMAGES, state, requests, channels, searchSuggestions, SEARCH_SUGGESTION_DELAY_MS, SEARCH_SUGGESTION_LIMIT, routedLocation, imagePreviewTimers, numberFormat, moneyFormat, $, $$, mobilePurchaseMedia, purchaseObserver, purchaseScrollFrame, cartBounceTimer, orderRefreshTimer, orderRefreshRun, orderRefreshVersion, orderRefreshFailures, lastOrderRefreshAttempt, storefrontRevealSelector, reducedStorefrontMotion, observedStorefrontReveals, storefrontRevealObserver, homeCategoryDesktop, escapeHtml, amount, integer, hasRole, icon, isOperator;

function syncMobilePurchase() {
        const bar = __stockflowApp.$('#mobile-purchase-bar');
        const main = __stockflowApp.$('#shop-product-add-form button[type="submit"]');
        const product = __stockflowApp.state.detailSku;
        if (!main) {
            __stockflowApp.purchaseObserver?.disconnect();
            __stockflowApp.purchaseObserver = null;
        }
        const eligible =
            __stockflowApp.mobilePurchaseMedia.matches &&
            __stockflowApp.state.view === 'shop' &&
            __stockflowApp.state.shopTab === 'product' &&
            Boolean(product && main) &&
            !__stockflowApp.isOperator() &&
            !document.querySelector('dialog[open]');
        bar.hidden = !eligible || main.getBoundingClientRect().bottom >= 0;
        document.body.classList.toggle('mobile-purchase-visible', !bar.hidden);
        if (!eligible) return;
        const image = __stockflowApp.$('#mobile-purchase-image');
        const source = __stockflowApp.productImage(product).src;
        if (image.getAttribute('src') !== source) {
            image.hidden = false;
            image.src = source;
        }
        __stockflowApp.$('#mobile-purchase-name').textContent = __stockflowApp.cartProductName(product);
        __stockflowApp.$('#mobile-purchase-name').title = __stockflowApp.cartProductName(product) + ' · ' + product.sku;
        __stockflowApp.$('#mobile-purchase-price').textContent = __stockflowApp.amount(product.unit_price);
        const button = __stockflowApp.$('[data-action="sticky-add-cart"]');
        button.disabled = main.disabled || main.getAttribute('aria-busy') === 'true';
        button.setAttribute('aria-label', 'Thêm ' + __stockflowApp.cartProductName(product) + ' vào giỏ');
    }

function observeMobilePurchase() {
        __stockflowApp.purchaseObserver?.disconnect();
        __stockflowApp.purchaseObserver = null;
        const button = __stockflowApp.$('#shop-product-add-form button[type="submit"]');
        if (button && 'IntersectionObserver' in window) {
            __stockflowApp.purchaseObserver = new IntersectionObserver(__stockflowApp.syncMobilePurchase, { threshold: 0 });
            __stockflowApp.purchaseObserver.observe(button);
        }
        __stockflowApp.syncMobilePurchase();
    }

function bounceCart() {
        const cart = __stockflowApp.$('.cart-trigger');
        window.clearTimeout(__stockflowApp.cartBounceTimer);
        cart.classList.remove('cart-added');
        void cart.offsetWidth;
        cart.classList.add('cart-added');
        __stockflowApp.cartBounceTimer = window.setTimeout(() => cart.classList.remove('cart-added'), 450);
    }

function revealStorefrontElement(element) {
        element.classList.add('is-revealed');
        // Cards keep observing after entry so scrolling back can fade them out.
        if (element.matches('.product-card') && !__stockflowApp.reducedStorefrontMotion.matches) return;
        __stockflowApp.storefrontRevealObserver?.unobserve(element);
        __stockflowApp.observedStorefrontReveals.delete(element);
    }

function prepareStorefrontReveals() {
        if (__stockflowApp.reducedStorefrontMotion.matches || !('IntersectionObserver' in window)) return;
        if (!__stockflowApp.storefrontRevealObserver) {
            __stockflowApp.storefrontRevealObserver = new IntersectionObserver(
                (entries) => {
                    entries.forEach((entry) => {
                        if (entry.target.matches('.product-card')) {
                            entry.target.classList.toggle('is-revealed',
                                entry.isIntersecting || entry.target.contains(document.activeElement));
                        } else if (entry.isIntersecting) {
                            __stockflowApp.revealStorefrontElement(entry.target);
                        }
                    });
                },
                { rootMargin: '0px 0px -32px 0px', threshold: 0 },
            );
        }
        // Khi phân trang hoặc đổi SKU, bỏ tham chiếu tới các thẻ cũ đã rời DOM.
        __stockflowApp.observedStorefrontReveals.forEach((element) => {
            if (!element.isConnected) {
                __stockflowApp.storefrontRevealObserver.unobserve(element);
                __stockflowApp.observedStorefrontReveals.delete(element);
            }
        });
        __stockflowApp.$$(__stockflowApp.storefrontRevealSelector, __stockflowApp.$('.shop-main')).forEach((element, index) => {
            if (!element.getClientRects().length) return;
            if (!element.classList.contains('scroll-reveal')) {
                element.style.setProperty('--reveal-delay', Math.min(index % 4, 3) * 55 + 'ms');
                if (element.getBoundingClientRect().top < window.innerHeight - 32) {
                    // Gắn hai trạng thái cùng lúc để nội dung trong màn hình không bị nháy ẩn khi tải.
                    element.classList.add('scroll-reveal', 'is-revealed');
                } else {
                    element.classList.add('scroll-reveal');
                }
            }
            if (element.matches('.product-card') || !element.classList.contains('is-revealed')) {
                __stockflowApp.observedStorefrontReveals.add(element);
                __stockflowApp.storefrontRevealObserver.observe(element);
            }
        });
    }

function dateTime(value) {
        const date = new Date(value);
        return value && !Number.isNaN(date.getTime()) ? date.toLocaleString('vi-VN') : '—';
    }

function statusBadge(status) {
        const known = Object.hasOwn(__stockflowApp.STATUS_LABELS, status);
        return (
            '<span class="badge ' +
            (known ? 'status-' + status.toLowerCase() : 'neutral') +
            '" title="' +
            __stockflowApp.escapeHtml(status) +
            '">' +
            __stockflowApp.escapeHtml(__stockflowApp.STATUS_LABELS[status] || status) +
            '<small class="status-code">' +
            __stockflowApp.escapeHtml(status) +
            '</small></span>'
        );
    }

function cancelRequests() {
        __stockflowApp.closeSearchSuggestions();
        __stockflowApp.state.epoch++;
        __stockflowApp.requests.forEach((controller) => controller.abort());
        __stockflowApp.requests.clear();
        __stockflowApp.channels.clear();
    }

function invalidateOrderRefresh() {
        __stockflowApp.orderRefreshVersion++;
        window.clearTimeout(__stockflowApp.orderRefreshTimer);
        __stockflowApp.orderRefreshTimer = null;
        if (__stockflowApp.orderRefreshRun) window.clearTimeout(__stockflowApp.orderRefreshRun.timeout);
        __stockflowApp.orderRefreshRun = null;
        ['auto-order-list', 'auto-order-detail'].forEach((name) => {
            __stockflowApp.channels.get(name)?.abort();
            __stockflowApp.channels.delete(name);
        });
    }

function canAutoRefreshOrders() {
        return (
            __stockflowApp.hasRole('CUSTOMER') &&
            __stockflowApp.state.view === 'shop' &&
            __stockflowApp.state.shopTab === 'orders' &&
            document.visibilityState === 'visible' &&
            !__stockflowApp.state.authBusy &&
            !document.querySelector('dialog[open]')
        );
    }

function setOrderSyncMessage(message) {
        __stockflowApp.$$('[data-order-sync]').forEach((element) => {
            element.textContent = message;
        });
    }

function scheduleOrderRefresh(delay) {
        window.clearTimeout(__stockflowApp.orderRefreshTimer);
        __stockflowApp.orderRefreshTimer = null;
        if (!__stockflowApp.canAutoRefreshOrders() || __stockflowApp.orderRefreshRun) return;
        const expired =
            __stockflowApp.state.order?.status === 'PENDING' && new Date(__stockflowApp.state.order.reservation_expires_at).getTime() <= Date.now();
        const retryDelay = __stockflowApp.orderRefreshFailures
            ? Math.min(60000, __stockflowApp.ORDER_REFRESH_MS * 2 ** __stockflowApp.orderRefreshFailures)
            : expired
              ? __stockflowApp.EXPIRED_ORDER_REFRESH_MS
              : __stockflowApp.ORDER_REFRESH_MS;
        // Focus và visibility có thể tới cùng lúc; không tạo thêm request trong cùng một giây.
        const wait = Math.max(delay ?? retryDelay, 1000 - (Date.now() - __stockflowApp.lastOrderRefreshAttempt));
        __stockflowApp.orderRefreshTimer = window.setTimeout(() => __stockflowApp.execute(__stockflowApp.refreshOrdersQuietly), wait);
    }

async function refreshOrdersQuietly() {
        if (!__stockflowApp.canAutoRefreshOrders() || __stockflowApp.orderRefreshRun) return;
        if (__stockflowApp.channels.has('my-orders') || __stockflowApp.channels.has('order-detail') || __stockflowApp.$('#order-detail [aria-busy="true"]')) {
            __stockflowApp.scheduleOrderRefresh();
            return;
        }
        const run = { version: __stockflowApp.orderRefreshVersion, timedOut: false, timeout: null };
        __stockflowApp.orderRefreshRun = run;
        __stockflowApp.lastOrderRefreshAttempt = Date.now();
        const current = () => run.version === __stockflowApp.orderRefreshVersion && __stockflowApp.canAutoRefreshOrders();
        run.timeout = window.setTimeout(() => {
            run.timedOut = true;
            ['auto-order-list', 'auto-order-detail'].forEach((name) => __stockflowApp.channels.get(name)?.abort());
        }, __stockflowApp.READ_TIMEOUT_MS);
        try {
            const result = await __stockflowApp.api('/orders/my', {
                channel: 'auto-order-list',
                query: { page: __stockflowApp.state.pages.orders, size: 8 },
            });
            if (!current()) return;
            const selectedId = __stockflowApp.state.order?.id;
            let order = selectedId
                ? result.content.find((value) => value.id === selectedId)
                : result.content[0] || null;
            if (selectedId && !order) {
                order = await __stockflowApp.api('/orders/' + selectedId, { channel: 'auto-order-detail' });
            }
            if (!current()) return;
            const detailChanged = JSON.stringify(order) !== JSON.stringify(__stockflowApp.state.order);
            if (detailChanged) await __stockflowApp.hydrateOrderProducts(order);
            if (!current()) return;
            if (JSON.stringify(result) !== JSON.stringify(__stockflowApp.state.myOrdersPage)) __stockflowApp.renderMyOrders(result);
            if (detailChanged) {
                __stockflowApp.state.order = order;
                __stockflowApp.renderOrder();
            }
            __stockflowApp.orderRefreshFailures = 0;
            __stockflowApp.setOrderSyncMessage(
                'Đã đồng bộ lúc ' + new Date().toLocaleTimeString('vi-VN') + ' · Tự cập nhật khi đang xem.',
            );
        } catch (error) {
            if (!current() || (error.name === 'AbortError' && !run.timedOut)) return;
            __stockflowApp.orderRefreshFailures = Math.min(__stockflowApp.orderRefreshFailures + 1, 3);
            __stockflowApp.setOrderSyncMessage('Chưa đồng bộ được. Hệ thống sẽ thử lại; bạn vẫn có thể bấm Tải lại.');
        } finally {
            window.clearTimeout(run.timeout);
            if (__stockflowApp.orderRefreshRun === run) {
                __stockflowApp.orderRefreshRun = null;
                __stockflowApp.scheduleOrderRefresh();
            }
        }
    }

async function api(path, { method = 'GET', body, query, anonymous = false, channel, idempotencyKey } = {}) {
        const epoch = __stockflowApp.state.epoch;
        const controller = new AbortController();
        if (channel) {
            __stockflowApp.channels.get(channel)?.abort();
            __stockflowApp.channels.set(channel, controller);
        }
        __stockflowApp.requests.add(controller);
        const url = new URL('/api/v1' + path, window.location.origin);
        Object.entries(query || {}).forEach(([key, value]) => {
            if (value !== '' && value !== null && value !== undefined) url.searchParams.set(key, value);
        });
        const headers = { Accept: 'application/json' };
        if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
        if (__stockflowApp.state.token && !anonymous) headers.Authorization = 'Bearer ' + __stockflowApp.state.token;
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        try {
            const response = await fetch(url, {
                method,
                headers,
                signal: controller.signal,
                credentials: path === '/auth/google' || path === '/auth/google/config' ? 'same-origin' : 'omit',
                body: body !== undefined ? JSON.stringify(body) : undefined,
            });
            const raw = await response.text();
            if (epoch !== __stockflowApp.state.epoch || (channel && __stockflowApp.channels.get(channel) !== controller)) {
                throw new DOMException('Phiên giao diện đã thay đổi.', 'AbortError');
            }
            let payload;
            try {
                payload = raw ? JSON.parse(raw) : null;
            } catch {
                payload = { message: 'Hệ thống trả dữ liệu không hợp lệ. Vui lòng thử lại.' };
            }
            if (!response.ok) {
                const error = new __stockflowApp.ApiError(
                    response.status,
                    payload?.error || response.statusText,
                    payload,
                    url.pathname,
                );
                if (response.status === 401 && __stockflowApp.state.token && !anonymous) {
                    const authRoute = ['/login','/register'].includes(window.location.pathname);
                    __stockflowApp.clearSession();
                    __stockflowApp.renderIdentity();
                    __stockflowApp.renderPermissions();
                    // Phiên hết hạn vẫn tải lại cửa hàng công khai, không để người xem gặp trang trắng.
                    if (authRoute) __stockflowApp.execute(__stockflowApp.routeFromLocation);
                    else {
                        __stockflowApp.replaceHash();
                        __stockflowApp.execute(() => __stockflowApp.loadReferences().then(__stockflowApp.loadCatalog));
                    }
                }
                throw error;
            }
            if (method !== 'GET' && /^\/(orders|products)(\/|$)/.test(path)) __stockflowApp.state.discoveryDirty = true;
            __stockflowApp.$('#last-sync').textContent = 'Cập nhật lúc ' + new Date().toLocaleTimeString('vi-VN');
            return payload;
        } catch (error) {
            if (error.name === 'AbortError' || error instanceof __stockflowApp.ApiError) throw error;
            throw new __stockflowApp.ApiError(
                0,
                'Không thể kết nối',
                {
                    message: 'Không kết nối được hệ thống. Kiểm tra ứng dụng Spring Boot đang chạy rồi thử lại.',
                },
                url.pathname,
            );
        } finally {
            __stockflowApp.requests.delete(controller);
            if (channel && __stockflowApp.channels.get(channel) === controller) __stockflowApp.channels.delete(channel);
        }
    }

function notify(kind, message, title = '', path = '', { banner = true } = {}) {
        const error = kind === 'error';
        const notice = __stockflowApp.$('#api-notice');
        notice.className = 'notice' + (error ? ' error' : '');
        notice.hidden = !banner;
        notice.innerHTML =
            __stockflowApp.icon(error ? 'alert' : 'check') +
            '<div class="notice-content"><strong>' +
            __stockflowApp.escapeHtml(title || (error ? 'Không thể thực hiện' : 'Thành công')) +
            '</strong><span>' +
            __stockflowApp.escapeHtml(message) +
            '</span>' +
            (path ? '<small class="mono">' + __stockflowApp.escapeHtml(path) + '</small>' : '') +
            '</div>' +
            '<button class="icon-button" type="button" data-action="dismiss-notice" aria-label="Đóng thông báo">' +
            __stockflowApp.icon('close') +
            '</button>';
        const openDialog = __stockflowApp.$('dialog[open]');
        if (openDialog && error) {
            let feedback = __stockflowApp.$('[data-dialog-notice]', openDialog);
            if (!feedback) {
                feedback = document.createElement('div');
                feedback.dataset.dialogNotice = '';
                feedback.className = 'form-error';
                feedback.setAttribute('role', 'alert');
                openDialog.append(feedback);
            }
            feedback.textContent = (title ? title + ' · ' : '') + message;
        }
        const toast = document.createElement('div');
        toast.className = 'toast' + (error ? ' error' : '');
        toast.innerHTML =
            __stockflowApp.icon(error ? 'alert' : 'check') +
            '<div><strong>' +
            __stockflowApp.escapeHtml(title || (error ? 'Thông báo lỗi' : 'Thành công')) +
            '</strong><p>' +
            __stockflowApp.escapeHtml(message) +
            '</p></div>' +
            '<button class="icon-button" type="button" data-action="dismiss-toast" aria-label="Đóng toast">' +
            __stockflowApp.icon('close') +
            '</button>';
        __stockflowApp.$('#toasts').append(toast);
        while (__stockflowApp.$('#toasts').children.length > 3) __stockflowApp.$('#toasts').firstElementChild.remove();
        window.setTimeout(() => toast.remove(), error ? 10000 : 6500);
    }

function handleError(error) {
        if (error.name === 'AbortError') return;
        const title = error.status ? 'HTTP ' + error.status + ' ' + error.reason : 'Thông báo';
        __stockflowApp.notify('error', error.message || 'Không thể thực hiện thao tác.', title, error.path);
    }

function execute(operation) {
        Promise.resolve().then(operation).catch(__stockflowApp.handleError);
    }

async function busy(button, operation) {
        if (button.disabled || button.getAttribute('aria-busy') === 'true') return;
        const epoch = __stockflowApp.state.epoch;
        button.disabled = true;
        button.setAttribute('aria-busy', 'true');
        __stockflowApp.syncMobilePurchase();
        try {
            await operation();
        } finally {
            if (epoch === __stockflowApp.state.epoch && button.isConnected) {
                button.disabled = false;
                button.removeAttribute('aria-busy');
                __stockflowApp.renderPermissions();
                __stockflowApp.syncMobilePurchase();
                __stockflowApp.scheduleOrderRefresh();
            }
        }
    }

function saveSession() {
        try {
            sessionStorage.setItem(__stockflowApp.SESSION_KEY, JSON.stringify({ token: __stockflowApp.state.token }));
        } catch {
            /* Storage bị chặn thì phiên vẫn chạy trong bộ nhớ. */
        }
    }

function cartOwner() {
        if (!__stockflowApp.state.user) return 'guest';
        return __stockflowApp.hasRole('CUSTOMER') ? 'customer:' + __stockflowApp.state.user.id : null;
    }

function writeCartSnapshot(items, warehouseId = __stockflowApp.state.branchId) {
        const owner = __stockflowApp.cartOwner();
        if (!owner) return;
        try {
            sessionStorage.setItem(
                __stockflowApp.CART_KEY,
                JSON.stringify({
                    version: 1,
                    owner,
                    warehouse_id: warehouseId ? Number(warehouseId) : null,
                    items,
                }),
            );
        } catch {
            /* Trình duyệt chặn storage thì giỏ hiện tại vẫn sử dụng được trong bộ nhớ. */
        }
    }

function saveCart() {
        if (!__stockflowApp.state.cartReady || __stockflowApp.state.cartLoading || __stockflowApp.state.cartRestoreFailed) return;
        __stockflowApp.writeCartSnapshot(
            [...__stockflowApp.state.cart.values()].map((item) => ({ product_id: item.product.id, quantity: item.quantity, selected: item.selected !== false })),
        );
    }

function forgetCart() {
        __stockflowApp.forgetCheckoutAttempt();
        try {
            sessionStorage.removeItem(__stockflowApp.CART_KEY);
        } catch {
            /* Dữ liệu trong bộ nhớ vẫn được xóa nếu storage không truy cập được. */
        }
    }

function readSavedCart() {
        try {
            const raw = sessionStorage.getItem(__stockflowApp.CART_KEY);
            if (!raw) return null;
            if (raw.length > 20000) throw new Error('Bản lưu giỏ vượt giới hạn.');
            const saved = JSON.parse(raw);
            if (
                saved?.version !== 1 ||
                saved.owner !== __stockflowApp.cartOwner() ||
                !Array.isArray(saved.items) ||
                saved.items.length > __stockflowApp.MAX_CART_ITEMS
            ) {
                throw new Error('Bản lưu giỏ không phù hợp với phiên hiện tại.');
            }
            const ids = new Set();
            const items = saved.items.filter((item) => {
                if (
                    !Number.isSafeInteger(item?.product_id) ||
                    item.product_id < 1 ||
                    !Number.isInteger(item.quantity) ||
                    item.quantity < 1 ||
                    item.quantity > __stockflowApp.MAX_QUANTITY ||
                    ids.has(item.product_id)
                ) {
                    return false;
                }
                ids.add(item.product_id);
                return true;
            });
            return {
                items: items.map((item) => ({ product_id: item.product_id, quantity: item.quantity, selected: item.selected !== false })),
                warehouse_id:
                    Number.isSafeInteger(saved.warehouse_id) && saved.warehouse_id > 0 ? saved.warehouse_id : null,
            };
        } catch {
            __stockflowApp.forgetCart();
            return null;
        }
    }

async function loadSavedCartProduct(id, cache) {
        const getProduct = (productId) => {
            if (!cache.has(productId)) {
                cache.set(
                    productId,
                    __stockflowApp.api('/products/' + productId, { anonymous: true, channel: 'cart-product-' + productId }),
                );
            }
            return cache.get(productId);
        };
        const product = await getProduct(id);
        if (product.parent_product_id) {
            const root = await getProduct(product.parent_product_id);
            const variant = root.variants?.find((value) => value.sku_product_id === id);
            return root.status === 'ACTIVE' && variant ? __stockflowApp.colorSku(root, variant) : null;
        }
        return product.status === 'ACTIVE' ? __stockflowApp.saleSku(product) : null;
    }

async function restoreCart() {
        if (__stockflowApp.isOperator() || __stockflowApp.state.cartLoading) return;
        const epoch = __stockflowApp.state.epoch;
        const saved = __stockflowApp.readSavedCart();
        __stockflowApp.state.cartReady = false;
        __stockflowApp.state.cartLoading = true;
        __stockflowApp.state.cartRestoreFailed = false;
        if (saved?.warehouse_id) __stockflowApp.state.branchId = String(saved.warehouse_id);
        __stockflowApp.renderCart();
        const cache = new Map();
        // API treo không giữ toàn bộ cửa hàng ở trạng thái tải mãi; bản lưu được giữ để khách thử lại.
        const timeout = window.setTimeout(() => {
            if (epoch !== __stockflowApp.state.epoch) return;
            cache.forEach((_, id) => __stockflowApp.channels.get('cart-product-' + id)?.abort());
        }, __stockflowApp.READ_TIMEOUT_MS);
        const restored = new Map(__stockflowApp.state.cart);
        let removed = 0;
        try {
            const items = saved?.items || [];
            for (let offset = 0; offset < items.length; offset += 4) {
                const results = await Promise.allSettled(
                    items.slice(offset, offset + 4).map(async (item) => {
                        const product = await __stockflowApp.loadSavedCartProduct(item.product_id, cache);
                        return { product, item };
                    }),
                );
                if (epoch !== __stockflowApp.state.epoch) return;
                for (const [index, result] of results.entries()) {
                    const id = items[offset + index].product_id;
                    if (result.status === 'rejected') {
                        if (result.reason.status !== 404) throw result.reason;
                        restored.delete(id);
                        removed++;
                    } else if (!result.value.product || result.value.product.status !== 'ACTIVE') {
                        restored.delete(id);
                        removed++;
                    } else {
                        const { product, item } = result.value;
                        restored.set(id, { product, quantity: restored.get(id)?.quantity || item.quantity,
                            selected: restored.get(id)?.selected ?? item.selected });
                    }
                }
            }
            __stockflowApp.state.cart = restored;
            restored.forEach((item) => __stockflowApp.state.products.set(item.product.id, item.product));
            __stockflowApp.state.cartReady = true;
            if (removed) {
                __stockflowApp.notify(
                    'error',
                    'Đã bỏ ' + removed + ' mặt hàng không còn bán khỏi giỏ. Vui lòng kiểm tra lại giỏ hàng.',
                );
            }
        } catch (error) {
            if (epoch !== __stockflowApp.state.epoch) return;
            __stockflowApp.state.cartRestoreFailed = true;
        } finally {
            window.clearTimeout(timeout);
            if (epoch === __stockflowApp.state.epoch) {
                __stockflowApp.state.cartLoading = false;
                __stockflowApp.renderCart();
                __stockflowApp.saveCart();
            }
        }
    }

function clearSession({ preserveCart = false } = {}) {
        __stockflowApp.invalidateOrderRefresh();
        __stockflowApp.cancelRequests();
        if (!preserveCart) __stockflowApp.forgetCart();
        __stockflowApp.state.token = null;
        __stockflowApp.state.user = null;
        __stockflowApp.administeredUsers.clear();
        __stockflowApp.$('#users-rows').replaceChildren();
        __stockflowApp.$('#admin-user-detail').replaceChildren();
        __stockflowApp.$('#admin-user-role-form').hidden = true;
        __stockflowApp.$('#admin-user-role-history').replaceChildren();
        __stockflowApp.state.profileReady = false;
        __stockflowApp.state.profileSaving = false;
        __stockflowApp.state.order = null;
        __stockflowApp.state.myOrdersPage = null;
        __stockflowApp.state.cartLoading = false;
        __stockflowApp.state.cartRestoreFailed = false;
        __stockflowApp.orderRefreshFailures = 0;
        __stockflowApp.state.pendingMutation = null;
        __stockflowApp.state.operatingWarehouses = [];
        __stockflowApp.state.authBusy = false;
        __stockflowApp.state.view = 'shop';
        __stockflowApp.state.shopTab = 'catalog';
        __stockflowApp.state.productId = null;
        __stockflowApp.state.detailRoot = null;
        __stockflowApp.state.detailSku = null;
        __stockflowApp.state.portalTab = 'queue';
        __stockflowApp.state.catalogExpanded = false;
        if (!preserveCart) {
            __stockflowApp.state.cart.clear();
            __stockflowApp.state.cartReady = true;
            __stockflowApp.resetCheckoutDetails();
        }
        Object.keys(__stockflowApp.state.pages).forEach((key) => {
            __stockflowApp.state.pages[key] = 0;
        });
        try {
            sessionStorage.removeItem(__stockflowApp.SESSION_KEY);
        } catch {
            /* Không có storage thì state hiện tại đã được xóa. */
        }
        __stockflowApp.$$('dialog[open]').forEach((dialog) => dialog.close());
        __stockflowApp.$$('[data-dialog-notice]').forEach((element) => element.remove());
        __stockflowApp.$$('[aria-busy="true"]').forEach((button) => {
            button.disabled = false;
            button.removeAttribute('aria-busy');
        });
        ['orders', 'queue', 'inventory', 'ledger', 'revenue', 'top', 'low', 'manage'].forEach((name) => {
            __stockflowApp.$('#' + name + '-rows').replaceChildren();
            __stockflowApp.$('#' + name + '-pagination').replaceChildren();
            const count = __stockflowApp.$('#' + name + '-count');
            if (count) count.textContent = '—';
        });
        ['stock-available', 'stock-reserved', 'stock-physical'].forEach((id) => {
            __stockflowApp.$('#' + id).textContent = '—';
        });
        __stockflowApp.$('#order-summary').replaceChildren();
        __stockflowApp.$('#summary-statuses').replaceChildren();
        __stockflowApp.$('#revenue-chart').replaceChildren();
        __stockflowApp.$('#top-chart').replaceChildren();
        __stockflowApp.purchaseObserver?.disconnect();
        __stockflowApp.purchaseObserver = null;
        window.cancelAnimationFrame(__stockflowApp.purchaseScrollFrame);
        __stockflowApp.purchaseScrollFrame = null;
        window.clearTimeout(__stockflowApp.cartBounceTimer);
        __stockflowApp.$('.cart-trigger').classList.remove('cart-added');
        __stockflowApp.$('#shop-product-detail-body').replaceChildren();
        delete __stockflowApp.$('#shop-product-detail-body').dataset.productId;
        [
            'queue-filter',
            'order-lookup',
            'stock-in',
            'inventory-filter',
            'ledger-filter',
            'report-filter',
            'manage-filter',
            'product-create',
            'product-update',
            'category-create',
            'category-edit',
            'brand-create',
            'brand-logo-edit',
            'profile-form',
            'review-form',
        ].forEach((id) => {
            __stockflowApp.$('#' + id).reset();
        });
        __stockflowApp.resetProductImagePreviews();
        delete __stockflowApp.$('#brand-logo-edit').dataset.brandId;
        delete __stockflowApp.$('#category-edit').dataset.categoryId;
        __stockflowApp.state.categoryProductCounts.clear();
        __stockflowApp.$('#category-admin-query').value = '';
        __stockflowApp.$('#brand-admin-query').value = '';
        ['profile-display-name', 'profile-email', 'profile-created'].forEach((id) => {
            __stockflowApp.$('#' + id).textContent = '—';
        });
        __stockflowApp.$('#profile-status').textContent = '';
        __stockflowApp.$$('[data-brand-logo-input]').forEach(__stockflowApp.renderBrandLogoPreview);
        __stockflowApp.$('#api-notice').hidden = true;
        __stockflowApp.$('#toasts').replaceChildren();
        __stockflowApp.renderWarehouses();
        __stockflowApp.renderCart();
        __stockflowApp.renderOrder();
        __stockflowApp.renderContext();
        __stockflowApp.syncQueueStatusTabs();
    }

function canPortalTab(tab) {
        return (
            __stockflowApp.isOperator() &&
            (['queue', 'inventory'].includes(tab) ||
                (['ledger', 'reports'].includes(tab) && __stockflowApp.hasRole('ADMIN', 'MANAGER')) ||
                (['products', 'categories', 'admin', 'users'].includes(tab) && __stockflowApp.hasRole('ADMIN')))
        );
    }

class ApiError extends Error {
        constructor(status, reason, payload, path) {
            const details = (payload?.errors || []).map((error) => error.message).filter(Boolean);
            super([payload?.message || reason || 'Không thể thực hiện yêu cầu.', ...details].join(' '));
            this.status = status;
            this.reason = reason;
            this.path = path;
            this.payload = payload;
        }
    }

export function register() {
Object.defineProperties(__stockflowApp, {
"SESSION_KEY": { get: () => SESSION_KEY },
"wishlistOwner": { get: () => wishlistOwner, set: value => { wishlistOwner = value; } },
"wishlistIds": { get: () => wishlistIds, set: value => { wishlistIds = value; } },
"wishlistLoadVersion": { get: () => wishlistLoadVersion, set: value => { wishlistLoadVersion = value; } },
"checkoutDifferentAddress": { get: () => checkoutDifferentAddress, set: value => { checkoutDifferentAddress = value; } },
"administeredUsers": { get: () => administeredUsers },
"CART_KEY": { get: () => CART_KEY },
"CHECKOUT_ATTEMPT_KEY": { get: () => CHECKOUT_ATTEMPT_KEY },
"checkoutAttempt": { get: () => checkoutAttempt, set: value => { checkoutAttempt = value; } },
"MAX_CART_ITEMS": { get: () => MAX_CART_ITEMS },
"ORDER_REFRESH_MS": { get: () => ORDER_REFRESH_MS },
"EXPIRED_ORDER_REFRESH_MS": { get: () => EXPIRED_ORDER_REFRESH_MS },
"READ_TIMEOUT_MS": { get: () => READ_TIMEOUT_MS },
"MAX_QUANTITY": { get: () => MAX_QUANTITY },
"MAX_GALLERY_IMAGES": { get: () => MAX_GALLERY_IMAGES },
"CATALOG_PRICE_RANGES": { get: () => CATALOG_PRICE_RANGES },
"LAPTOP_PRICE_RANGES": { get: () => LAPTOP_PRICE_RANGES },
"AUDIO_PRICE_RANGES": { get: () => AUDIO_PRICE_RANGES },
"DEMO_ACCOUNTS": { get: () => DEMO_ACCOUNTS },
"STATUS_LABELS": { get: () => STATUS_LABELS },
"MOVEMENT_LABELS": { get: () => MOVEMENT_LABELS },
"ROLE_CLASSES": { get: () => ROLE_CLASSES },
"ROLE_LABELS": { get: () => ROLE_LABELS },
"PORTAL_TITLES": { get: () => PORTAL_TITLES },
"PRODUCT_IMAGES": { get: () => PRODUCT_IMAGES },
"PRODUCT_IMAGE_RULES": { get: () => PRODUCT_IMAGE_RULES },
"CATEGORY_IMAGES": { get: () => CATEGORY_IMAGES },
"state": { get: () => state },
"requests": { get: () => requests },
"channels": { get: () => channels },
"searchSuggestions": { get: () => searchSuggestions },
"SEARCH_SUGGESTION_DELAY_MS": { get: () => SEARCH_SUGGESTION_DELAY_MS },
"SEARCH_SUGGESTION_LIMIT": { get: () => SEARCH_SUGGESTION_LIMIT },
"routedLocation": { get: () => routedLocation, set: value => { routedLocation = value; } },
"imagePreviewTimers": { get: () => imagePreviewTimers },
"numberFormat": { get: () => numberFormat },
"moneyFormat": { get: () => moneyFormat },
"$": { get: () => $ },
"$$": { get: () => $$ },
"mobilePurchaseMedia": { get: () => mobilePurchaseMedia },
"purchaseObserver": { get: () => purchaseObserver, set: value => { purchaseObserver = value; } },
"purchaseScrollFrame": { get: () => purchaseScrollFrame, set: value => { purchaseScrollFrame = value; } },
"cartBounceTimer": { get: () => cartBounceTimer, set: value => { cartBounceTimer = value; } },
"orderRefreshTimer": { get: () => orderRefreshTimer, set: value => { orderRefreshTimer = value; } },
"orderRefreshRun": { get: () => orderRefreshRun, set: value => { orderRefreshRun = value; } },
"orderRefreshVersion": { get: () => orderRefreshVersion, set: value => { orderRefreshVersion = value; } },
"orderRefreshFailures": { get: () => orderRefreshFailures, set: value => { orderRefreshFailures = value; } },
"lastOrderRefreshAttempt": { get: () => lastOrderRefreshAttempt, set: value => { lastOrderRefreshAttempt = value; } },
"syncMobilePurchase": { get: () => syncMobilePurchase },
"observeMobilePurchase": { get: () => observeMobilePurchase },
"bounceCart": { get: () => bounceCart },
"storefrontRevealSelector": { get: () => storefrontRevealSelector },
"reducedStorefrontMotion": { get: () => reducedStorefrontMotion },
"observedStorefrontReveals": { get: () => observedStorefrontReveals },
"storefrontRevealObserver": { get: () => storefrontRevealObserver, set: value => { storefrontRevealObserver = value; } },
"homeCategoryDesktop": { get: () => homeCategoryDesktop },
"revealStorefrontElement": { get: () => revealStorefrontElement },
"prepareStorefrontReveals": { get: () => prepareStorefrontReveals },
"escapeHtml": { get: () => escapeHtml },
"amount": { get: () => amount },
"integer": { get: () => integer },
"hasRole": { get: () => hasRole },
"icon": { get: () => icon },
"dateTime": { get: () => dateTime },
"statusBadge": { get: () => statusBadge },
"ApiError": { get: () => ApiError },
"cancelRequests": { get: () => cancelRequests },
"invalidateOrderRefresh": { get: () => invalidateOrderRefresh },
"canAutoRefreshOrders": { get: () => canAutoRefreshOrders },
"setOrderSyncMessage": { get: () => setOrderSyncMessage },
"scheduleOrderRefresh": { get: () => scheduleOrderRefresh },
"refreshOrdersQuietly": { get: () => refreshOrdersQuietly },
"api": { get: () => api },
"notify": { get: () => notify },
"handleError": { get: () => handleError },
"execute": { get: () => execute },
"busy": { get: () => busy },
"saveSession": { get: () => saveSession },
"cartOwner": { get: () => cartOwner },
"writeCartSnapshot": { get: () => writeCartSnapshot },
"saveCart": { get: () => saveCart },
"forgetCart": { get: () => forgetCart },
"readSavedCart": { get: () => readSavedCart },
"loadSavedCartProduct": { get: () => loadSavedCartProduct },
"restoreCart": { get: () => restoreCart },
"clearSession": { get: () => clearSession },
"isOperator": { get: () => isOperator },
"canPortalTab": { get: () => canPortalTab }
});
}

export function initializeFeature() {
(SESSION_KEY = 'stockflow.web.session');
(wishlistOwner = null);
(wishlistIds = new Set());
(wishlistLoadVersion = 0);
(checkoutDifferentAddress = false);
(administeredUsers = new Map());
(CART_KEY = 'stockflow.web.cart.v1');
(CHECKOUT_ATTEMPT_KEY = 'stockflow.web.checkout-attempt.v1');
(checkoutAttempt = null);
(MAX_CART_ITEMS = 100);
(ORDER_REFRESH_MS = 15000);
(EXPIRED_ORDER_REFRESH_MS = 5000);
(READ_TIMEOUT_MS = 15000);
(MAX_QUANTITY = 2147483647);
(MAX_GALLERY_IMAGES = 8);
(CATALOG_PRICE_RANGES = [
        { key: 'all', label: 'Tất cả mức giá', min: '', max: '' },
        { key: 'budget', label: 'Đến 1 triệu', min: '', max: '1000000' },
        { key: 'one-three', label: '1 – 3 triệu', min: '1000000', max: '3000000' },
        { key: 'three-five', label: '3 – 5 triệu', min: '3000000', max: '5000000' },
        { key: 'five-ten', label: '5 – 10 triệu', min: '5000000', max: '10000000' },
        { key: 'ten-twenty', label: '10 – 20 triệu', min: '10000000', max: '20000000' },
        { key: 'premium', label: 'Từ 20 triệu', min: '20000000', max: '' },
    ]);
(LAPTOP_PRICE_RANGES = [
        { key: 'all', label: 'Tất cả mức giá', min: '', max: '' },
        { key: 'laptop-under-ten', label: 'Đến 10 triệu', min: '', max: '10000000' },
        { key: 'laptop-ten-fifteen', label: '10 – 15 triệu', min: '10000000', max: '15000000' },
        { key: 'laptop-fifteen-twenty', label: '15 – 20 triệu', min: '15000000', max: '20000000' },
        { key: 'laptop-twenty-twentyfive', label: '20 – 25 triệu', min: '20000000', max: '25000000' },
        { key: 'laptop-twentyfive-thirty', label: '25 – 30 triệu', min: '25000000', max: '30000000' },
        { key: 'laptop-over-thirty', label: 'Từ 30 triệu', min: '30000000', max: '' },
    ]);
(AUDIO_PRICE_RANGES = [
        { key: 'all', label: 'Tất cả mức giá', min: '', max: '' },
        { key: 'audio-two-hundred', label: 'Đến 200 nghìn', min: '', max: '200000' },
        { key: 'audio-five-hundred', label: 'Đến 500 nghìn', min: '', max: '500000' },
        { key: 'budget', label: 'Đến 1 triệu', min: '', max: '1000000' },
        { key: 'audio-two-million', label: 'Đến 2 triệu', min: '', max: '2000000' },
        { key: 'audio-five-million', label: 'Đến 5 triệu', min: '', max: '5000000' },
    ]);
(DEMO_ACCOUNTS = {
        CUSTOMER: { email: 'customer@stockflow.com', password: 'Customer@123' },
    });
(STATUS_LABELS = {
        PENDING: 'Chờ thanh toán',
        CONFIRMED: 'Đã xác nhận',
        PACKED: 'Đã đóng gói',
        SHIPPED: 'Đang giao',
        DELIVERED: 'Đã giao',
        CANCELLED: 'Đã hủy',
        EXPIRED: 'Hết hạn',
        RETURNED: 'Đã trả hàng',
    });
(MOVEMENT_LABELS = {
        GOODS_RECEIPT: 'Nhập hàng',
        RESERVATION_HOLD: 'Giữ hàng',
        RESERVATION_RELEASE: 'Nhả hàng',
        DISPATCH: 'Xuất hàng',
        RETURN_RESTOCK: 'Hoàn kho',
        STOCK_ADJUSTMENT: 'Điều chỉnh',
    });
(ROLE_CLASSES = {
        ADMIN: 'role-admin',
        MANAGER: 'role-manager',
        WAREHOUSE_STAFF: 'role-warehouse_staff',
        CUSTOMER: 'role-customer',
    });
(ROLE_LABELS = {
        CUSTOMER: 'Khách hàng',
        WAREHOUSE_STAFF: 'Nhân viên kho',
        MANAGER: 'Quản lý',
        ADMIN: 'Quản trị viên',
    });
(PORTAL_TITLES = {
        queue: 'Vận hành đơn hàng',
        inventory: 'Tồn kho & nhập hàng',
        ledger: 'Sổ cái kiểm toán',
        reports: 'Báo cáo quản trị',
        products: 'Quản lý sản phẩm',
        categories: 'Quản lý danh mục',
        admin: 'Quản lý sản phẩm',
        users: 'Quản lý tài khoản',
    });
(PRODUCT_IMAGES = {
        iphone: 'https://images.unsplash.com/photo-1695048133142-1a20484d2569?w=600&auto=format&fit=crop&q=80',
        macbook: 'https://images.unsplash.com/photo-1517336714731-489689fd1ca8?w=600&auto=format&fit=crop&q=80',
        galaxy: 'https://images.unsplash.com/photo-1610945265064-0e34e5519bbf?w=600&auto=format&fit=crop&q=80',
        headphones: 'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600&auto=format&fit=crop&q=80',
        keyboard: 'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=600&auto=format&fit=crop&q=80',
        mouse: 'https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?w=600&auto=format&fit=crop&q=80',
        watch: 'https://images.unsplash.com/photo-1546868871-7041f2a55e12?w=600&auto=format&fit=crop&q=80',
        airFryer: 'https://images.unsplash.com/photo-1584269600464-37b1b58a9fe7?w=600&auto=format&fit=crop&q=80',
        vacuum: 'https://images.unsplash.com/photo-1518640467707-6811f4a6ab73?w=600&auto=format&fit=crop&q=80',
        airPurifier: 'https://images.unsplash.com/photo-1585771724684-38269d6639fd?w=600&auto=format&fit=crop&q=80',
        backpack: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&auto=format&fit=crop&q=80',
        sneakers: 'https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&auto=format&fit=crop&q=80',
        shirt: 'https://images.unsplash.com/photo-1521572267360-ee0c2909d518?w=600&auto=format&fit=crop&q=80',
        bottle: 'https://images.unsplash.com/photo-1602143407151-7111542de6e8?w=600&auto=format&fit=crop&q=80',
    });
(PRODUCT_IMAGE_RULES = [
        { names: ['iphone'], image: 'iphone' },
        { names: ['macbook', 'laptop'], image: 'macbook' },
        { names: ['samsung galaxy', 'galaxy s24', 'dien thoai android'], image: 'galaxy' },
        { names: ['sony wh', 'tai nghe'], image: 'headphones' },
        { names: ['keychron', 'ban phim'], image: 'keyboard' },
        { names: ['logitech mx', 'chuot'], image: 'mouse' },
        { names: ['apple watch', 'dong ho'], image: 'watch' },
        { names: ['noi chien'], image: 'airFryer' },
        { names: ['robot hut bui', 'may hut bui'], image: 'vacuum' },
        { names: ['may loc khong khi'], image: 'airPurifier' },
        { names: ['balo', 'ba lo'], image: 'backpack' },
        { names: ['sneaker', 'giay'], image: 'sneakers' },
        { names: ['ao thun'], image: 'shirt' },
        { names: ['binh giu nhiet'], image: 'bottle' },
    ]);
(CATEGORY_IMAGES = {
        'Bàn phím & Chuột': __stockflowApp.PRODUCT_IMAGES.keyboard,
        'Tai nghe & Loa': __stockflowApp.PRODUCT_IMAGES.headphones,
        'Webcam & Micro': __stockflowApp.PRODUCT_IMAGES.macbook,
        'Hub, Cáp & Bộ sạc': __stockflowApp.PRODUCT_IMAGES.macbook,
        'Màn hình & Phụ kiện bàn làm việc': __stockflowApp.PRODUCT_IMAGES.macbook,
        'Điện tử': __stockflowApp.PRODUCT_IMAGES.macbook,
        'Gia dụng': 'https://images.unsplash.com/photo-1555041469-a586c61ea9bc?w=600&auto=format&fit=crop&q=80',
        'Thời trang': 'https://images.unsplash.com/photo-1445205170230-053b83016050?w=600&auto=format&fit=crop&q=80',
        'Phụ kiện': __stockflowApp.PRODUCT_IMAGES.backpack,
    });
(state = {
        token: null,
        user: null,
        // Hồ sơ chỉ giữ trong bộ nhớ của phiên đăng nhập; không đưa số điện thoại vào sessionStorage.
        profileReady: false,
        profileSaving: false,
        epoch: 0,
        authBusy: false,
        authMode: 'login',
        authIntent: 'login',
        view: 'shop',
        shopTab: 'catalog',
        productId: null,
        portalTab: 'queue',
        catalogExpanded: false,
        categoryMenuOpen: false,
        categoryMenuInline: false,
        menuCategoryId: '',
        homeCategoryId: null,
        catalogMinPrice: '',
        catalogMaxPrice: '',
        catalogSort: 'id,asc',
        catalogBrandId: '',
        catalogSpecName: '',
        catalogSpecValue: '',
        specificationOptions: [],
        discoveryDirty: true,
        branchId: '',
        categories: [],
        categoryProductCounts: new Map(),
        brands: [],
        warehouses: [],
        operatingWarehouses: [],
        products: new Map(),
        detailRoot: null,
        detailSku: null,
        // Tín hiệu còn hàng tách khỏi số tồn nội bộ; không dùng nó thay atomic reserve khi đặt đơn.
        detailAvailability: null,
        availabilityEpoch: 0,
        availabilityLoading: false,
        availabilityError: false,
        cart: new Map(),
        cartReady: false,
        cartLoading: false,
        cartRestoreFailed: false,
        order: null,
        myOrdersPage: null,
        pendingMutation: null,
        pages: { catalog: 0, orders: 0, queue: 0, inventory: 0, ledger: 0, revenue: 0, top: 0, low: 0, manage: 0, users: 0 },
    });
(requests = new Set());
(channels = new Map());
(searchSuggestions = {
        timer: null,
        timeout: null,
        version: 0,
        active: -1,
        items: [],
        composing: false,
    });
(SEARCH_SUGGESTION_DELAY_MS = 250);
(SEARCH_SUGGESTION_LIMIT = 6);
(routedLocation = '');
(imagePreviewTimers = new Map());
(numberFormat = new Intl.NumberFormat('vi-VN'));
(moneyFormat = new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND',
        minimumFractionDigits: 0,
        maximumFractionDigits: 2,
    }));
($ = (selector, root = document) => root.querySelector(selector));
($$ = (selector, root = document) => [...root.querySelectorAll(selector)]);
(mobilePurchaseMedia = window.matchMedia('(max-width: 767px)'));
(purchaseObserver = null);
(purchaseScrollFrame = null);
(cartBounceTimer = null);
(orderRefreshTimer = null);
(orderRefreshRun = null);
(orderRefreshVersion = 0);
(orderRefreshFailures = 0);
(lastOrderRefreshAttempt = 0);
__stockflowApp.mobilePurchaseMedia.addEventListener('change', __stockflowApp.syncMobilePurchase);
window.addEventListener('resize', __stockflowApp.syncMobilePurchase);
window.addEventListener(
        'scroll',
        () => {
            if (
                !__stockflowApp.mobilePurchaseMedia.matches ||
                __stockflowApp.state.view !== 'shop' ||
                __stockflowApp.state.shopTab !== 'product' ||
                __stockflowApp.purchaseScrollFrame !== null
            )
                return;
            __stockflowApp.purchaseScrollFrame = window.requestAnimationFrame(() => {
                __stockflowApp.purchaseScrollFrame = null;
                __stockflowApp.syncMobilePurchase();
            });
        },
        { passive: true },
    );
document.addEventListener('close', __stockflowApp.syncMobilePurchase, true);
(storefrontRevealSelector = [
        '.shop-hero',
        '.shop-benefits > span',
        '.product-card',
        '.shelf-heading',
        '.catalog-discovery-filters',
        '.product-description',
        '.product-specifications',
        '.shop-footer',
    ].join(', '));
(reducedStorefrontMotion = window.matchMedia('(prefers-reduced-motion: reduce)'));
(observedStorefrontReveals = new Set());
(storefrontRevealObserver = null);
(homeCategoryDesktop = window.matchMedia('(min-width: 1001px)'));
__stockflowApp.homeCategoryDesktop.addEventListener('change', () => {
        __stockflowApp.setShopCategoryMenu(false);
        __stockflowApp.closeHomeCategoryMenu();
    });
window.addEventListener('resize', () => {
        if (__stockflowApp.state.categoryMenuOpen) __stockflowApp.setShopCategoryMenu(false);
    });
__stockflowApp.reducedStorefrontMotion.addEventListener('change', () => {
        if (__stockflowApp.reducedStorefrontMotion.matches) {
            __stockflowApp.observedStorefrontReveals.forEach(__stockflowApp.revealStorefrontElement);
            __stockflowApp.storefrontRevealObserver?.disconnect();
            __stockflowApp.observedStorefrontReveals.clear();
        } else {
            __stockflowApp.prepareStorefrontReveals();
        }
    });
document.addEventListener('focusin', (event) => {
        const element = event.target.closest('.scroll-reveal');
        if (element) __stockflowApp.revealStorefrontElement(element);
    });
document.addEventListener('focusout', (event) => {
        const element = event.target.closest('.product-card.scroll-reveal');
        if (!element || element.contains(event.relatedTarget) || __stockflowApp.reducedStorefrontMotion.matches) return;
        // A focused card is pinned visible. Re-observe on release so an offscreen
        // card resumes fading even when its intersection geometry did not change.
        __stockflowApp.storefrontRevealObserver?.unobserve(element);
        __stockflowApp.storefrontRevealObserver?.observe(element);
    });
(escapeHtml = (value) =>
        String(value ?? '').replace(
            /[&<>"']/g,
            (character) =>
                ({
                    '&': '&amp;',
                    '<': '&lt;',
                    '>': '&gt;',
                    '"': '&quot;',
                    "'": '&#39;',
                })[character],
        ));
(amount = (value) => __stockflowApp.moneyFormat.format(Number.isFinite(Number(value)) ? Number(value) : 0));
(integer = (value) => __stockflowApp.numberFormat.format(Number.isFinite(Number(value)) ? Number(value) : 0));
(hasRole = (...roles) => Boolean(__stockflowApp.state.user && roles.includes(__stockflowApp.state.user.role)));
(icon = (name) => '<svg class="icon" aria-hidden="true"><use href="#i-' + name + '"/></svg>');
document.addEventListener('visibilitychange', () => {
        if (document.visibilityState !== 'visible') __stockflowApp.invalidateOrderRefresh();
        else __stockflowApp.scheduleOrderRefresh(0);
    });
window.addEventListener('focus', () => __stockflowApp.scheduleOrderRefresh(0));
window.addEventListener('online', () => {
        __stockflowApp.orderRefreshFailures = 0;
        __stockflowApp.scheduleOrderRefresh(0);
    });
__stockflowApp.$$('dialog').forEach((dialog) => dialog.addEventListener('close', () => __stockflowApp.scheduleOrderRefresh(0)));
(isOperator = () => __stockflowApp.hasRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF'));
}
