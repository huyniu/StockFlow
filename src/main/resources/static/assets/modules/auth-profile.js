import { app as __stockflowApp } from './context.js';
import { loadAddressBook, loadCheckoutAddresses } from './account-services.js';

let otpEmail, otpResendAt, otpTimer, otpResendBusy, googleSdkPromise, googleLoginVersion, loginReturnPath, resetAuthArtwork, profileLocationVersion;

function renderIdentity() {
        __stockflowApp.$('#identity-email').textContent = __stockflowApp.state.user?.email || 'Chưa đăng nhập';
        __stockflowApp.$('#identity-role').textContent = __stockflowApp.ROLE_LABELS[__stockflowApp.state.user?.role] || 'Khách';
        __stockflowApp.$('#identity-role').className = 'badge ' + (__stockflowApp.ROLE_CLASSES[__stockflowApp.state.user?.role] || 'neutral');
        __stockflowApp.$('#open-login').hidden = Boolean(__stockflowApp.state.user);
        const accountMenu = __stockflowApp.$('#header-account');
        accountMenu.hidden = !__stockflowApp.state.user;
        accountMenu.open = false;
        const name = __stockflowApp.state.user?.full_name || __stockflowApp.state.user?.email || '';
        __stockflowApp.$('#header-account-name').textContent = name;
        __stockflowApp.$('#header-account-name').title = name;
        __stockflowApp.$('#account-menu-name').textContent = name;
        __stockflowApp.$('#account-menu-email').textContent = __stockflowApp.state.user?.email || '';
        __stockflowApp.$('#account-menu-role').textContent = __stockflowApp.ROLE_LABELS[__stockflowApp.state.user?.role] || '';
        __stockflowApp.$('#account-portal-label').textContent = __stockflowApp.hasRole('WAREHOUSE_STAFF') ? 'Truy cập cổng vận hành kho' : 'Truy cập cổng quản trị';
        __stockflowApp.syncWishlist();
        __stockflowApp.$('#logout').hidden = !__stockflowApp.state.user;
        __stockflowApp.$('#portal-name').textContent = __stockflowApp.state.user?.full_name || __stockflowApp.state.user?.email || '';
        __stockflowApp.$('#portal-scope').textContent = __stockflowApp.hasRole('WAREHOUSE_STAFF')
            ? 'Chỉ các kho được phân công'
            : 'Vận hành tất cả kho';
        __stockflowApp.$$('[data-demo-role]').forEach((button) => {
            const active = __stockflowApp.state.user?.email === __stockflowApp.DEMO_ACCOUNTS[button.dataset.demoRole].email;
            button.classList.toggle('active', active);
            button.setAttribute('aria-pressed', String(active));
            button.disabled = __stockflowApp.state.authBusy;
        });
        __stockflowApp.$('#auth-submit').disabled = __stockflowApp.state.authBusy;
    }

function renderPermissions() {
        __stockflowApp.$$('[data-roles]').forEach((element) => {
            element.hidden = !__stockflowApp.hasRole(...element.dataset.roles.split(','));
        });
        // Không sửa hồ sơ khi dữ liệu chưa tải xong hoặc đang ghi; tránh ghi đè nội dung đang nhập.
        __stockflowApp.$$('#profile-form input, #profile-form select, #profile-save').forEach((element) => {
            element.disabled =
                !__stockflowApp.hasRole('CUSTOMER') ||
                !__stockflowApp.state.profileReady ||
                __stockflowApp.state.profileSaving ||
                __stockflowApp.state.authBusy ||
                element.getAttribute('aria-busy') === 'true';
        });
        __stockflowApp.$('#profileDistrict').disabled ||= !__stockflowApp.$('#profileProvince').value;
        __stockflowApp.$('#profileWard').disabled ||= !__stockflowApp.$('#profileDistrict').value;
        __stockflowApp.$$('.shop-header [data-action="my-orders"], [data-shop-tab="orders"]').forEach((button) => {
            button.hidden = __stockflowApp.isOperator();
        });
        const create = __stockflowApp.$('#create-order');
        create.disabled =
            __stockflowApp.selectedCartItems().length === 0 ||
            __stockflowApp.isOperator() ||
            __stockflowApp.state.authBusy ||
            __stockflowApp.state.cartLoading ||
            __stockflowApp.state.cartRestoreFailed ||
            create.getAttribute('aria-busy') === 'true';
        create.textContent = __stockflowApp.state.user ? 'Tiến hành đặt hàng' : 'Đăng nhập để đặt hàng';
        create.type = __stockflowApp.state.user ? 'submit' : 'button';
        if (__stockflowApp.state.user) delete create.dataset.action;
        else create.dataset.action = 'checkout-login';
        __stockflowApp.$('#cart-permission').textContent = __stockflowApp.isOperator()
            ? 'Dùng tài khoản khách hàng để mua sắm.'
            : __stockflowApp.state.cartLoading
              ? 'Đang khôi phục giỏ và cập nhật thông tin sản phẩm…'
              : 'Một đơn được chuẩn bị tại một chi nhánh. Giữ hàng trong 15 phút.';
        // Không cho sửa giỏ chưa đồng bộ vì lần thử lại có thể phục hồi số lượng cũ từ bản lưu.
        const cartLocked = __stockflowApp.state.cartLoading || __stockflowApp.state.cartRestoreFailed || __stockflowApp.state.authBusy || create.getAttribute('aria-busy') === 'true';
        __stockflowApp.$$('.checkout-address-actions button').forEach(button => { button.disabled = cartLocked; });
        __stockflowApp.$('[data-action="use-default-address"]').disabled ||= !__stockflowApp.state.user?.default_address;
        __stockflowApp.$$('[data-cart-quantity], [data-cart-selected], #cart-select-all, [data-action="remove-cart"]').forEach((element) => {
            element.disabled = cartLocked;
        });
        __stockflowApp.$('#cart-select-all').disabled = cartLocked || !__stockflowApp.state.cart.size;
        __stockflowApp.$$('[data-action="cart-plus"], [data-action="cart-minus"]').forEach((button) => {
            const quantity = __stockflowApp.state.cart.get(Number(button.dataset.id))?.quantity || 0;
            button.disabled =
                cartLocked || (button.dataset.action === 'cart-minus' ? quantity <= 1 : quantity >= __stockflowApp.MAX_QUANTITY);
        });
        __stockflowApp.$$('#stock-in button[type="submit"], #inventory-filter button[type="submit"]').forEach((button) => {
            if (button.getAttribute('aria-busy') !== 'true') {
                button.disabled = __stockflowApp.hasRole('WAREHOUSE_STAFF') && !__stockflowApp.state.operatingWarehouses.length;
            }
        });
    }

function renderContext() {
        __stockflowApp.setShopCategoryMenu(false);
        __stockflowApp.closeHomeCategoryMenu();
        if (__stockflowApp.state.portalTab === 'admin') __stockflowApp.state.portalTab = 'products';
        if (__stockflowApp.state.view === 'portal' && !__stockflowApp.isOperator()) __stockflowApp.state.view = 'shop';
        if (!__stockflowApp.canPortalTab(__stockflowApp.state.portalTab)) __stockflowApp.state.portalTab = 'queue';
        if (['orders', 'account'].includes(__stockflowApp.state.shopTab) && !__stockflowApp.hasRole('CUSTOMER')) __stockflowApp.state.shopTab = 'catalog';
        __stockflowApp.$('#storefront-view').hidden = __stockflowApp.state.view !== 'shop';
        __stockflowApp.$('#dashboard-view').hidden = __stockflowApp.state.view !== 'portal';
        document.body.classList.toggle('portal-open', __stockflowApp.state.view === 'portal');
        __stockflowApp.$$('.shop-panel').forEach((panel) => {
            panel.hidden = panel.id !== 'shop-' + __stockflowApp.state.shopTab;
        });
        __stockflowApp.$$('.portal-panel').forEach((panel) => {
            panel.hidden = panel.id !== 'portal-' + __stockflowApp.state.portalTab;
        });
        __stockflowApp.$$('[data-shop-tab]').forEach((button) =>
            __stockflowApp.setActive(
                button,
                button.dataset.shopTab === __stockflowApp.state.shopTab ||
                    (button.dataset.shopTab === 'catalog' && __stockflowApp.state.shopTab === 'product'),
            ),
        );
        // Menu có nhiều nút: dùng danh sách DOM để cập nhật trạng thái mà không làm gián đoạn đăng nhập.
        __stockflowApp.$$('[data-portal-tab]').forEach((button) => __stockflowApp.setActive(button, button.dataset.portalTab === __stockflowApp.state.portalTab));
        __stockflowApp.renderCatalogNavigation();
        __stockflowApp.$('#portal-title').textContent = __stockflowApp.PORTAL_TITLES[__stockflowApp.state.portalTab];
        document.title =
            __stockflowApp.state.view === 'shop'
                ? 'StockFlow Tech — ' +
                  (__stockflowApp.state.shopTab === 'orders'
                      ? 'Đơn hàng của tôi'
                      : __stockflowApp.state.shopTab === 'account'
                        ? 'Tài khoản của tôi'
                        : __stockflowApp.state.shopTab === 'product'
                          ? 'Chi tiết sản phẩm'
                          : __stockflowApp.state.shopTab === 'help'
                            ? 'Hướng dẫn mua hàng'
                            : 'Cửa hàng công nghệ')
                : 'StockFlow — ' + __stockflowApp.PORTAL_TITLES[__stockflowApp.state.portalTab];
        __stockflowApp.renderIdentity();
        __stockflowApp.renderPermissions();
        __stockflowApp.prepareStorefrontReveals();
        __stockflowApp.syncMobilePurchase();
    }

function renderCatalogNavigation() {
        __stockflowApp.$('#nav-catalog-group').classList.toggle('expanded', __stockflowApp.state.catalogExpanded);
        const toggle = __stockflowApp.$('#catalog-group-toggle');
        toggle.classList.toggle(
            'active',
            __stockflowApp.state.view === 'portal' && ['products', 'categories'].includes(__stockflowApp.state.portalTab),
        );
        toggle.setAttribute('aria-expanded', String(__stockflowApp.state.catalogExpanded));
        __stockflowApp.$('#catalog-subitems').hidden = !__stockflowApp.state.catalogExpanded;
    }

function setActive(button, active) {
        button.classList.toggle('active', active);
        if (active) button.setAttribute('aria-current', 'page');
        else button.removeAttribute('aria-current');
    }

async function authenticate(credentials, register = false, demo = false, verification = false, googleLogin = false) {
        if (__stockflowApp.state.authBusy) return;
        const intent = demo ? 'login' : __stockflowApp.state.authIntent;
        const returnPath = __stockflowApp.loginReturnPath;
        const guestCart = !__stockflowApp.state.user ? __stockflowApp.readSavedCart() : null;
        if (!verification) __stockflowApp.clearSession({ preserveCart: !__stockflowApp.state.user });
        if (document.body.classList.contains('auth-page-open') && !__stockflowApp.$('#auth-dialog').open) __stockflowApp.$('#auth-dialog').show();
        __stockflowApp.state.authBusy = true;
        if (verification) __stockflowApp.updateOtpCountdown();
        __stockflowApp.renderIdentity();
        __stockflowApp.renderPermissions();
        const epoch = __stockflowApp.state.epoch;
        __stockflowApp.$('#auth-error').hidden = true;
        try {
            const result = await __stockflowApp.api(googleLogin ? '/auth/google' : verification ? '/auth/verify-email' : register ? '/auth/register' : '/auth/login', {
                method: 'POST',
                body: credentials,
                anonymous: true,
            });
            if (result.requires_verification) {
                __stockflowApp.$('#auth-password').value = '';
                __stockflowApp.openOtp(result.email, true);
                return;
            }
            __stockflowApp.$('#otp-dialog').close();
            __stockflowApp.state.token = result.access_token;
            __stockflowApp.state.user = result.user;
            __stockflowApp.saveSession();
            __stockflowApp.$('#auth-password').value = '';
            if (__stockflowApp.isOperator()) {
                __stockflowApp.state.cart.clear();
                __stockflowApp.forgetCart();
            } else {
                // Chỉ giỏ khách vãng lai được chuyển vào tài khoản vừa đăng nhập, không lấy giỏ actor cũ.
                if (guestCart) __stockflowApp.writeCartSnapshot(guestCart.items, guestCart.warehouse_id);
                await __stockflowApp.restoreCart();
                if (epoch !== __stockflowApp.state.epoch) return;
            }
            __stockflowApp.state.view = __stockflowApp.isOperator() ? 'portal' : 'shop';
            __stockflowApp.state.portalTab = __stockflowApp.hasRole('MANAGER') ? 'reports' : 'queue';
            __stockflowApp.state.shopTab = ['orders', 'account'].includes(intent) && __stockflowApp.hasRole('CUSTOMER') ? intent : 'catalog';
            __stockflowApp.renderContext();
            __stockflowApp.renderCart();
            __stockflowApp.replaceHash();
            __stockflowApp.notify(
                'success',
                'Xin chào ' + (result.user.full_name || result.user.email) + '.',
                register ? 'HTTP 201 Created' : 'Đăng nhập thành công',
                '',
                { banner: false },
            );
            await __stockflowApp.loadReferences();
            await __stockflowApp.refreshSection();
            if (returnPath && intent === 'login' && !__stockflowApp.isOperator() && !__stockflowApp.pendingVNPayReturn) {
                window.history.replaceState(null, '', returnPath);
                await __stockflowApp.routeFromLocation();
            }
            __stockflowApp.loginReturnPath = null;
            if (__stockflowApp.pendingVNPayReturn) await __stockflowApp.consumeVNPayReturn();
            if (intent === 'checkout' && __stockflowApp.hasRole('CUSTOMER') && __stockflowApp.state.cart.size) __stockflowApp.openDialog('cart-dialog');
            if (intent === 'portal' && __stockflowApp.hasRole('CUSTOMER')) {
                __stockflowApp.notify(
                    'error',
                    'Tài khoản khách hàng sử dụng cửa hàng. Đăng nhập nhân viên, quản lý hoặc admin để vào cổng quản trị.',
                );
            }
        } catch (error) {
            if (epoch === __stockflowApp.state.epoch && error.payload?.error === 'EMAIL_NOT_VERIFIED') {
                __stockflowApp.$('#auth-password').value = '';
                __stockflowApp.openOtp(error.payload.email, false);
                return;
            }
            if (verification && epoch === __stockflowApp.state.epoch && !__stockflowApp.state.token) {
                __stockflowApp.$('#otp-error').textContent = error.message;
                __stockflowApp.$('#otp-error').hidden = false;
                return;
            }
            if (epoch === __stockflowApp.state.epoch && error.name !== 'AbortError' && !__stockflowApp.state.token && !demo) {
                __stockflowApp.openDialog('auth-dialog');
                __stockflowApp.$('#auth-error').textContent = error.message;
                __stockflowApp.$('#auth-error').hidden = false;
            }
            throw error;
        } finally {
            if (epoch === __stockflowApp.state.epoch) {
                __stockflowApp.state.authBusy = false;
                __stockflowApp.renderIdentity();
                __stockflowApp.renderPermissions();
                if (__stockflowApp.$('#otp-dialog').open) __stockflowApp.updateOtpCountdown();
                if (googleLogin && __stockflowApp.$('#auth-dialog').open) __stockflowApp.execute(() => __stockflowApp.initializeGoogleLogin());
            }
        }
    }

function updateOtpCountdown() {
        const seconds = Math.max(0, Math.ceil((__stockflowApp.otpResendAt - Date.now()) / 1000));
        __stockflowApp.$('#otp-resend').disabled = seconds > 0 || __stockflowApp.otpResendBusy || __stockflowApp.state.authBusy;
        __stockflowApp.$('#otp-resend').textContent = seconds ? `Gửi lại mã (${seconds}s)` : 'Gửi lại mã';
        __stockflowApp.$('#otp-submit').disabled = __stockflowApp.state.authBusy || __stockflowApp.otpResendBusy;
    }

function openOtp(email, justSent) {
        if (__stockflowApp.otpEmail !== email) __stockflowApp.otpResendAt = 0;
        __stockflowApp.otpEmail = email;
        if (justSent) __stockflowApp.otpResendAt = Date.now() + 60000;
        __stockflowApp.$('#otp-email').textContent = email;
        __stockflowApp.$('#otp-code').value = '';
        __stockflowApp.$('#otp-error').hidden = true;
        __stockflowApp.openDialog('otp-dialog');
        clearInterval(__stockflowApp.otpTimer);
        __stockflowApp.otpTimer = setInterval(__stockflowApp.updateOtpCountdown, 1000);
        __stockflowApp.updateOtpCountdown();
        __stockflowApp.$('#otp-code').focus();
    }

async function resendOtp() {
        if (__stockflowApp.otpResendBusy || __stockflowApp.state.authBusy || Date.now() < __stockflowApp.otpResendAt) return;
        __stockflowApp.otpResendBusy = true;
        __stockflowApp.updateOtpCountdown();
        __stockflowApp.$('#otp-error').hidden = true;
        try {
            await __stockflowApp.api('/auth/resend-otp', { method: 'POST', body: { email: __stockflowApp.otpEmail }, anonymous: true });
            __stockflowApp.otpResendAt = Date.now() + 60000;
            __stockflowApp.notify('success', 'Đã gửi mã OTP mới. Mã cũ không còn hiệu lực.');
        } catch (error) {
            if (error.status === 429) __stockflowApp.otpResendAt = Date.now() + 60000;
            __stockflowApp.$('#otp-error').textContent = error.message;
            __stockflowApp.$('#otp-error').hidden = false;
        } finally {
            __stockflowApp.otpResendBusy = false;
            __stockflowApp.updateOtpCountdown();
        }
    }

function openDialog(id) {
        __stockflowApp.closeSearchSuggestions();
        __stockflowApp.$$('dialog[open]').forEach((dialog) => dialog.close());
        const dialog = __stockflowApp.$('#' + id);
        __stockflowApp.$$('[data-dialog-notice]', dialog).forEach((element) => element.remove());
        if (id === 'cart-dialog') {
            __stockflowApp.prefillCheckoutFromProfile();
            __stockflowApp.execute(__stockflowApp.prepareDefaultCheckoutAddress);
        }
        if (id === 'auth-dialog' && document.body.classList.contains('auth-page-open')) dialog.show();
        else dialog.showModal();
        __stockflowApp.syncMobilePurchase();
    }

function setAuthMode(mode) {
        __stockflowApp.state.authMode = mode;
        const register = mode === 'register';
        const forgot = __stockflowApp.$('#auth-forgot-password');
        if (forgot) forgot.hidden = register;
        __stockflowApp.$('#auth-title').textContent = register ? 'Tạo tài khoản StockFlow' : 'Đăng nhập StockFlow';
        __stockflowApp.$('#auth-page-subtitle').textContent = register ? 'Tạo tài khoản để lưu sản phẩm yêu thích, đặt hàng và theo dõi giao hàng.' : 'Chào mừng trở lại! Đăng nhập để tiếp tục mua sắm và theo dõi đơn hàng của bạn.';
        __stockflowApp.$('#auth-password').type = 'password';
        __stockflowApp.$('#auth-password-toggle').textContent = 'Hiện mật khẩu';
        __stockflowApp.$('#auth-password-toggle').setAttribute('aria-pressed', 'false');
        if (document.body.classList.contains('auth-page-open')) {
            const url = new URL(window.location.href);
            url.pathname = register ? '/register' : '/login';
            window.history.replaceState(null, '', url.pathname + url.search);
            __stockflowApp.routedLocation = window.location.href;
        }
        __stockflowApp.$('#auth-name-field').hidden = !register;
        __stockflowApp.$('#auth-name').required = register;
        __stockflowApp.$('#auth-password').autocomplete = register ? 'new-password' : 'current-password';
        // Đăng ký theo DTO tối thiểu 6 ký tự; đăng nhập không chặn mật khẩu hợp lệ của tài khoản cũ.
        __stockflowApp.$('#auth-password').minLength = register ? 6 : 1;
        __stockflowApp.$('#auth-submit').textContent = register ? 'Tạo tài khoản mua hàng' : 'Đăng nhập';
        __stockflowApp.$('#auth-description').textContent = register
            ? 'Tài khoản mới dành cho khách mua sắm và theo dõi đơn hàng của mình.'
            : __stockflowApp.state.authIntent === 'portal'
              ? 'Dùng tài khoản nhân viên, quản lý hoặc admin để vào cổng vận hành.'
              : 'Đăng nhập để đặt hàng và theo dõi đơn của bạn.';
        __stockflowApp.$$('[data-auth-mode]').forEach((button) => __stockflowApp.setActive(button, button.dataset.authMode === mode));
        __stockflowApp.$('#auth-error').hidden = true;
    }

function loadGoogleSdk() {
        if (window.google?.accounts?.id) return Promise.resolve();
        if (!__stockflowApp.googleSdkPromise) __stockflowApp.googleSdkPromise = new Promise((resolve, reject) => {
            const script = document.createElement('script');
            const timer = setTimeout(() => { script.remove(); __stockflowApp.googleSdkPromise = null; reject(new Error('Không tải được Google. Kiểm tra kết nối rồi mở lại đăng nhập.')); }, 10000);
            script.src = 'https://accounts.google.com/gsi/client';
            script.referrerPolicy = 'no-referrer-when-downgrade';
            script.async = true;
            script.onload = () => { clearTimeout(timer); resolve(); };
            script.onerror = () => { clearTimeout(timer); script.remove(); __stockflowApp.googleSdkPromise = null; reject(new Error('Không tải được đăng nhập Google.')); };
            document.head.append(script);
        });
        return __stockflowApp.googleSdkPromise;
    }

async function initializeGoogleLogin() {
        const version = ++__stockflowApp.googleLoginVersion;
        const host = __stockflowApp.$('#google-signin-button');
        host.replaceChildren();
        __stockflowApp.$('#google-signin-placeholder').hidden = false;
        __stockflowApp.$('#google-signin-note').textContent = 'Đang chuẩn bị đăng nhập Google…';
        try {
            const config = await __stockflowApp.api('/auth/google/config', { anonymous: true, channel: 'google-login-config' });
            if (version !== __stockflowApp.googleLoginVersion || !__stockflowApp.$('#auth-dialog').open) return;
            if (!config.enabled) {
                __stockflowApp.$('#google-signin-note').textContent = 'Đăng nhập Google chưa được bật. Cần cấu hình Google Client ID.';
                return;
            }
            await __stockflowApp.loadGoogleSdk();
            if (version !== __stockflowApp.googleLoginVersion || !__stockflowApp.$('#auth-dialog').open) return;
            window.google.accounts.id.initialize({
                client_id: config.client_id, nonce: config.nonce, auto_select: false,
                callback: response => {
                    if (version !== __stockflowApp.googleLoginVersion || !__stockflowApp.$('#auth-dialog').open || __stockflowApp.state.authBusy) return;
                    __stockflowApp.execute(() => __stockflowApp.authenticate({ credential: response.credential }, false, false, false, true));
                },
            });
            window.google.accounts.id.renderButton(host, { type: 'standard', theme: 'outline', size: 'large', text: 'continue_with', shape: 'pill', locale: 'vi', width: Math.max(200, Math.min(320, Math.floor(host.parentElement.clientWidth))) });
            __stockflowApp.$('#google-signin-placeholder').hidden = true;
            __stockflowApp.$('#google-signin-note').textContent = 'Dùng tài khoản Google để đăng nhập hoặc tạo tài khoản.';
        } catch (error) {
            if (version === __stockflowApp.googleLoginVersion && error.name !== 'AbortError') __stockflowApp.$('#google-signin-note').textContent = error.message;
        }
    }

function safeLoginReturn(value) {
        if (!value || !value.startsWith('/') || value.startsWith('//')) return '/#shop';
        try {
            const url = new URL(value, window.location.origin);
            if (url.origin !== window.location.origin || !(/^\/$/.test(url.pathname) || /^\/san-pham\/\d+$/.test(url.pathname))) return '/#shop';
            return url.pathname + url.search + url.hash;
        } catch { return '/#shop'; }
    }

function setLoginPage(visible) {
        if (!visible) __stockflowApp.resetAuthArtwork();
        const wasVisible = document.body.classList.contains('auth-page-open') || Boolean(document.documentElement.dataset.authRoute);
        document.body.classList.toggle('auth-page-open', visible);
        __stockflowApp.$('#auth-page').hidden = !visible;
        window.StockFlowAuthArtwork?.setActive(visible);
        delete document.documentElement.dataset.authRoute;
        if (visible) __stockflowApp.$('#auth-page-form-slot').append(__stockflowApp.$('#auth-dialog'));
        else if (__stockflowApp.$('#auth-page-form-slot').contains(__stockflowApp.$('#auth-dialog'))) {
            __stockflowApp.$('#auth-dialog').close();
            document.body.append(__stockflowApp.$('#auth-dialog'));
        }
        if (visible || wasVisible) document.title = visible ? 'Đăng nhập / Đăng ký — StockFlow Tech' : 'StockFlow Tech — Cửa hàng công nghệ & Cổng quản trị';
    }

function showLoginPage() {
        const query = new URLSearchParams(window.location.search);
        const mode = window.location.pathname === '/register' ? 'register' : 'login';
        __stockflowApp.loginReturnPath = __stockflowApp.safeLoginReturn(query.get('pre_uri'));
        __stockflowApp.state.authIntent = ['checkout','orders','account','portal'].includes(query.get('intent')) ? query.get('intent') : 'login';
        __stockflowApp.setLoginPage(true);
        __stockflowApp.setAuthMode(mode);
        __stockflowApp.openDialog('auth-dialog');
        __stockflowApp.execute(() => __stockflowApp.initializeGoogleLogin());
        window.scrollTo({ top: 0, behavior: 'auto' });
    }

function openAuth(intent = 'login') {
        const returnPath = __stockflowApp.safeLoginReturn(window.location.pathname + window.location.search + window.location.hash);
        const query = new URLSearchParams({ pre_uri: returnPath });
        if (intent !== 'login') query.set('intent', intent);
        window.history.pushState(null, '', '/login?' + query.toString());
        __stockflowApp.routedLocation = window.location.href;
        __stockflowApp.showLoginPage();
    }

function renderProfile() {
        __stockflowApp.$('#profile-display-name').textContent = __stockflowApp.state.user?.full_name || 'Khách mua hàng';
        __stockflowApp.$('#profile-email').textContent = __stockflowApp.state.user?.email || '—';
        __stockflowApp.$('#profile-created').textContent = __stockflowApp.state.user?.created_at
            ? new Date(__stockflowApp.state.user.created_at).toLocaleDateString('vi-VN')
            : '—';
        __stockflowApp.$('#profile-name').value = __stockflowApp.state.user?.full_name || '';
        __stockflowApp.$('#profile-phone').value = __stockflowApp.state.user?.phone || '';
        __stockflowApp.$('#profileStreetAddress').value = __stockflowApp.state.user?.default_address?.street_address || '';
    }

async function loadProfile() {
        if (!__stockflowApp.hasRole('CUSTOMER') || __stockflowApp.state.profileSaving) return;
        const epoch = __stockflowApp.state.epoch;
        __stockflowApp.state.profileReady = false;
        __stockflowApp.renderProfile();
        __stockflowApp.$('#profile-status').textContent = 'Đang tải thông tin…';
        __stockflowApp.renderPermissions();
        try {
            __stockflowApp.state.user = await __stockflowApp.api('/users/me', { channel: 'profile-read' });
            await __stockflowApp.loadProfileAddress();
            await loadAddressBook();
            __stockflowApp.state.profileReady = true;
            __stockflowApp.renderProfile();
            __stockflowApp.renderIdentity();
            __stockflowApp.$('#profile-status').textContent = '';
        } catch (error) {
            if (epoch === __stockflowApp.state.epoch && error.name !== 'AbortError') {
                __stockflowApp.$('#profile-status').textContent = 'Chưa tải được hồ sơ. Bấm Làm mới để thử lại.';
            }
            throw error;
        } finally {
            if (epoch === __stockflowApp.state.epoch) __stockflowApp.renderPermissions();
        }
    }

async function saveProfile() {
        if (!__stockflowApp.hasRole('CUSTOMER') || !__stockflowApp.state.profileReady || __stockflowApp.state.profileSaving) return;
        const province = __stockflowApp.$('#profileProvince').value, district = __stockflowApp.$('#profileDistrict').value;
        const ward = __stockflowApp.$('#profileWard').value, street = __stockflowApp.$('#profileStreetAddress').value.trim();
        const anyAddress = Boolean(province || district || ward || street);
        if (anyAddress && !(province && district && ward && street)) throw new Error('Vui lòng chọn đủ tỉnh, quận, phường và số nhà/tên đường.');
        const address = anyAddress ? { province_id: Number(province), district_id: Number(district), ward_code: ward, street_address: street } : null;
        const epoch = __stockflowApp.state.epoch;
        __stockflowApp.state.profileSaving = true;
        __stockflowApp.$('#profile-status').textContent = 'Đang lưu thông tin…';
        __stockflowApp.renderPermissions();
        try {
            __stockflowApp.state.user = await __stockflowApp.api('/users/me', {
                method: 'PATCH',
                body: { full_name: __stockflowApp.$('#profile-name').value, phone: __stockflowApp.$('#profile-phone').value, default_address: address, clear_default_address: !anyAddress },
                channel: 'profile-save',
            });
            __stockflowApp.renderProfile();
            __stockflowApp.renderIdentity();
            await loadAddressBook();
            __stockflowApp.$('#profile-status').textContent = 'Đã lưu thông tin.';
            __stockflowApp.notify('success', 'Thông tin tài khoản đã được cập nhật.', 'HTTP 200 OK');
        } catch (error) {
            if (epoch === __stockflowApp.state.epoch && error.name !== 'AbortError') {
                __stockflowApp.$('#profile-status').textContent = 'Chưa lưu được. Kiểm tra thông tin và thử lại.';
            }
            throw error;
        } finally {
            if (epoch === __stockflowApp.state.epoch) {
                __stockflowApp.state.profileSaving = false;
                __stockflowApp.renderPermissions();
            }
        }
    }

function prefillCheckoutFromProfile() {
        if (!__stockflowApp.hasRole('CUSTOMER')) return;
        if (!__stockflowApp.$('#delivery-name').value.trim()) __stockflowApp.$('#delivery-name').value = __stockflowApp.state.user.full_name || '';
        if (!__stockflowApp.$('#delivery-phone').value.trim()) __stockflowApp.$('#delivery-phone').value = __stockflowApp.state.user.phone || '';
    }

async function loadProfileAddress() {
        const address = __stockflowApp.state.user?.default_address;
        const values = await __stockflowApp.api('/locations/provinces');
        __stockflowApp.$('#profileProvince').innerHTML = '<option value="">Chọn tỉnh / thành phố</option>' + values.map(p => '<option value="' + p.ProvinceID + '">' + __stockflowApp.escapeHtml(p.ProvinceName) + '</option>').join('');
        __stockflowApp.resetLocationSelect('profileDistrict', 'Chọn quận / huyện');
        __stockflowApp.resetLocationSelect('profileWard', 'Chọn phường / xã');
        __stockflowApp.$('#profileProvince').value = address?.province_id || '';
        if (!address) return;
        await __stockflowApp.changeProfileLocation('province');
        __stockflowApp.$('#profileDistrict').value = address.district_id;
        await __stockflowApp.changeProfileLocation('district');
        __stockflowApp.$('#profileWard').value = address.ward_code;
    }

async function changeProfileLocation(level) {
        const version = ++__stockflowApp.profileLocationVersion;
        const province = level === 'province';
        if (province) __stockflowApp.resetLocationSelect('profileDistrict', 'Chọn quận / huyện');
        __stockflowApp.resetLocationSelect('profileWard', 'Chọn phường / xã');
        const source = province ? __stockflowApp.$('#profileProvince') : __stockflowApp.$('#profileDistrict');
        const id = source.value;
        if (!id) return;
        const values = await __stockflowApp.api(province ? '/locations/districts' : '/locations/wards', { query: province ? { province_id: id } : { district_id: id } });
        if (version !== __stockflowApp.profileLocationVersion || id !== source.value) return;
        const target = province ? __stockflowApp.$('#profileDistrict') : __stockflowApp.$('#profileWard');
        target.innerHTML += values.map(value => '<option value="' + __stockflowApp.escapeHtml(province ? value.DistrictID : value.WardCode) + '">' + __stockflowApp.escapeHtml(province ? value.DistrictName : value.WardName) + '</option>').join('');
        target.disabled = false;
    }

async function prepareDefaultCheckoutAddress(force = false) {
        await loadCheckoutAddresses(force);
        if (__stockflowApp.state.authBusy || __stockflowApp.$('#create-order').getAttribute('aria-busy') === 'true') return;
        if (!force && __stockflowApp.checkoutDifferentAddress) { await __stockflowApp.loadCheckoutProvinces(); return; }
        if (force) __stockflowApp.checkoutDifferentAddress = false;
        await __stockflowApp.loadCheckoutProvinces();
        const address = __stockflowApp.state.user?.default_address;
        if (!address || (!force && (__stockflowApp.$('#checkoutProvince').value || __stockflowApp.$('#checkoutStreetAddress').value.trim()))) return;
        __stockflowApp.$('#checkoutProvince').value = address.province_id;
        if (!__stockflowApp.$('#checkoutProvince').value) throw new Error('Địa chỉ mặc định không còn trong danh sách GHN. Hãy chọn lại địa chỉ.');
        let version = __stockflowApp.shippingLocationVersion + 1;
        await __stockflowApp.changeCheckoutLocation('province');
        if (version !== __stockflowApp.shippingLocationVersion) return;
        __stockflowApp.$('#checkoutDistrict').value = address.district_id;
        version = __stockflowApp.shippingLocationVersion + 1;
        await __stockflowApp.changeCheckoutLocation('district');
        if (version !== __stockflowApp.shippingLocationVersion) return;
        __stockflowApp.$('#checkoutWard').value = address.ward_code;
        if (!__stockflowApp.$('#checkoutDistrict').value || !__stockflowApp.$('#checkoutWard').value) throw new Error('Địa chỉ mặc định cần được cập nhật. Hãy chọn lại quận/phường.');
        if (force || !__stockflowApp.$('#checkoutStreetAddress').value.trim()) __stockflowApp.$('#checkoutStreetAddress').value = address.street_address;
        await __stockflowApp.refreshCheckoutFee();
    }

async function loadReferences() {
        const operations = [__stockflowApp.loadCategories(), __stockflowApp.loadBrands(), __stockflowApp.loadBranches(), __stockflowApp.loadBestsellers()];
        if (__stockflowApp.isOperator()) operations.push(__stockflowApp.loadOperatingWarehouses(), __stockflowApp.loadProductOptions());
        const results = await Promise.allSettled(operations);
        const failure = results.find((result) => result.status === 'rejected' && result.reason.name !== 'AbortError');
        if (failure) __stockflowApp.handleError(failure.reason);
    }

async function loadCategories() {
        __stockflowApp.state.categories = await __stockflowApp.api('/categories', { anonymous: true, channel: 'categories' });
        const options = __stockflowApp.orderedCategories()
            .map(
                (category) => '<option value="' + category.id + '">' + __stockflowApp.escapeHtml(__stockflowApp.categoryPath(category)) + '</option>',
            )
            .join('');
        ['catalog-category', 'manage-category', 'product-category'].forEach((id) => {
            const select = __stockflowApp.$('#' + id);
            const previous = select.value;
            select.innerHTML =
                '<option value="">' +
                (id === 'product-category' ? 'Chọn danh mục' : 'Tất cả danh mục') +
                '</option>' +
                options;
            select.value = previous;
        });
        __stockflowApp.renderCategoryChips();
        const brandCategory = __stockflowApp.$('#brand-category');
        const previousBrandCategory = brandCategory.value;
        brandCategory.innerHTML = '<option value="">Chưa chọn</option>' + options;
        brandCategory.value = previousBrandCategory;
        const parentSelect = __stockflowApp.$('#category-parent');
        const previousParent = parentSelect.value;
        parentSelect.innerHTML =
            '<option value="">Không có — danh mục cấp đầu</option>' +
            __stockflowApp.orderedCategories()
                .filter((category) => __stockflowApp.categoryAncestors(category).length < 3)
                .map(
                    (category) =>
                        '<option value="' + category.id + '">' + __stockflowApp.escapeHtml(__stockflowApp.categoryPath(category)) + '</option>',
                )
                .join('');
        parentSelect.value = previousParent;
        __stockflowApp.renderCatalogBrandOptions();
        __stockflowApp.renderShopCategoryMenu();
        __stockflowApp.renderAdminBrands();
        __stockflowApp.renderDiscoveryCategories();
    }

export function register() {
Object.defineProperties(__stockflowApp, {
"renderIdentity": { get: () => renderIdentity },
"renderPermissions": { get: () => renderPermissions },
"renderContext": { get: () => renderContext },
"renderCatalogNavigation": { get: () => renderCatalogNavigation },
"setActive": { get: () => setActive },
"authenticate": { get: () => authenticate },
"otpEmail": { get: () => otpEmail, set: value => { otpEmail = value; } },
"otpResendAt": { get: () => otpResendAt, set: value => { otpResendAt = value; } },
"otpTimer": { get: () => otpTimer, set: value => { otpTimer = value; } },
"otpResendBusy": { get: () => otpResendBusy, set: value => { otpResendBusy = value; } },
"updateOtpCountdown": { get: () => updateOtpCountdown },
"openOtp": { get: () => openOtp },
"resendOtp": { get: () => resendOtp },
"openDialog": { get: () => openDialog },
"setAuthMode": { get: () => setAuthMode },
"googleSdkPromise": { get: () => googleSdkPromise, set: value => { googleSdkPromise = value; } },
"googleLoginVersion": { get: () => googleLoginVersion, set: value => { googleLoginVersion = value; } },
"loadGoogleSdk": { get: () => loadGoogleSdk },
"initializeGoogleLogin": { get: () => initializeGoogleLogin },
"loginReturnPath": { get: () => loginReturnPath, set: value => { loginReturnPath = value; } },
"resetAuthArtwork": { get: () => resetAuthArtwork, set: value => { resetAuthArtwork = value; } },
"safeLoginReturn": { get: () => safeLoginReturn },
"setLoginPage": { get: () => setLoginPage },
"showLoginPage": { get: () => showLoginPage },
"openAuth": { get: () => openAuth },
"renderProfile": { get: () => renderProfile },
"loadProfile": { get: () => loadProfile },
"saveProfile": { get: () => saveProfile },
"prefillCheckoutFromProfile": { get: () => prefillCheckoutFromProfile },
"profileLocationVersion": { get: () => profileLocationVersion, set: value => { profileLocationVersion = value; } },
"loadProfileAddress": { get: () => loadProfileAddress },
"changeProfileLocation": { get: () => changeProfileLocation },
"prepareDefaultCheckoutAddress": { get: () => prepareDefaultCheckoutAddress },
"loadReferences": { get: () => loadReferences },
"loadCategories": { get: () => loadCategories }
});
}

export function initializeFeature() {
(otpEmail = '');
(otpResendAt = 0);
(otpResendBusy = false);
__stockflowApp.$('#otp-dialog').addEventListener('close', () => clearInterval(__stockflowApp.otpTimer));
__stockflowApp.$('#otp-resend').addEventListener('click', () => __stockflowApp.execute(__stockflowApp.resendOtp));
__stockflowApp.$('#otp-close').addEventListener('click', () => __stockflowApp.$('#otp-dialog').close());
(googleLoginVersion = 0);
(loginReturnPath = null);
(resetAuthArtwork = () => {});
(profileLocationVersion = 0);
}
