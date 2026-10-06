/* StockFlow Tech bán điện thoại, laptop và phụ kiện; quyền, số tồn và vòng đời đơn do backend quyết định. */
(() => {
    'use strict';

    const SESSION_KEY = 'stockflow.web.session';
    // Giỏ chỉ tồn tại trong phiên của tab và gắn với danh tính đã được backend xác thực.
    const CART_KEY = 'stockflow.web.cart.v1';
    // Khóa retry chỉ lưu chủ phiên, mã ngẫu nhiên và hash; không lưu địa chỉ/điện thoại hoặc nội dung đơn.
    const CHECKOUT_ATTEMPT_KEY = 'stockflow.web.checkout-attempt.v1';
    let checkoutAttempt = null;
    const MAX_CART_ITEMS = 100;
    const ORDER_REFRESH_MS = 15000;
    const EXPIRED_ORDER_REFRESH_MS = 5000;
    const READ_TIMEOUT_MS = 15000;
    const MAX_QUANTITY = 2147483647;
    const MAX_GALLERY_IMAGES = 8;
    /** Khoảng giá dùng chung cho menu và API; hai đầu mút được tính cả trong kết quả. */
    const CATALOG_PRICE_RANGES = [
        { key: 'all', label: 'Tất cả mức giá', min: '', max: '' },
        { key: 'budget', label: 'Đến 1 triệu', min: '', max: '1000000' },
        { key: 'one-three', label: '1 – 3 triệu', min: '1000000', max: '3000000' },
        { key: 'three-five', label: '3 – 5 triệu', min: '3000000', max: '5000000' },
        { key: 'five-ten', label: '5 – 10 triệu', min: '5000000', max: '10000000' },
        { key: 'ten-twenty', label: '10 – 20 triệu', min: '10000000', max: '20000000' },
        { key: 'premium', label: 'Từ 20 triệu', min: '20000000', max: '' },
    ];
    /** Khoảng giá theo ngành hàng dùng điều kiện số thật, không tạo danh mục giá giả. */
    const LAPTOP_PRICE_RANGES = [
        { key: 'all', label: 'Tất cả mức giá', min: '', max: '' },
        { key: 'laptop-under-ten', label: 'Đến 10 triệu', min: '', max: '10000000' },
        { key: 'laptop-ten-fifteen', label: '10 – 15 triệu', min: '10000000', max: '15000000' },
        { key: 'laptop-fifteen-twenty', label: '15 – 20 triệu', min: '15000000', max: '20000000' },
        { key: 'laptop-twenty-twentyfive', label: '20 – 25 triệu', min: '20000000', max: '25000000' },
        { key: 'laptop-twentyfive-thirty', label: '25 – 30 triệu', min: '25000000', max: '30000000' },
        { key: 'laptop-over-thirty', label: 'Từ 30 triệu', min: '30000000', max: '' },
    ];
    const AUDIO_PRICE_RANGES = [
        { key: 'all', label: 'Tất cả mức giá', min: '', max: '' },
        { key: 'audio-two-hundred', label: 'Đến 200 nghìn', min: '', max: '200000' },
        { key: 'audio-five-hundred', label: 'Đến 500 nghìn', min: '', max: '500000' },
        { key: 'budget', label: 'Đến 1 triệu', min: '', max: '1000000' },
        { key: 'audio-two-million', label: 'Đến 2 triệu', min: '', max: '2000000' },
        { key: 'audio-five-million', label: 'Đến 5 triệu', min: '', max: '5000000' },
    ];
    const DEMO_ACCOUNTS = {
        ADMIN: { email: 'admin@stockflow.com', password: 'Admin@123' },
        MANAGER: { email: 'manager@stockflow.com', password: 'Manager@123' },
        WAREHOUSE_STAFF: { email: 'staff.hn@stockflow.com', password: 'Staff@123' },
        CUSTOMER: { email: 'customer@stockflow.com', password: 'Customer@123' },
    };
    const STATUS_LABELS = {
        PENDING: 'Chờ thanh toán',
        CONFIRMED: 'Đã xác nhận',
        PACKED: 'Đã đóng gói',
        SHIPPED: 'Đang giao',
        DELIVERED: 'Đã giao',
        CANCELLED: 'Đã hủy',
        EXPIRED: 'Hết hạn',
        RETURNED: 'Đã trả hàng',
    };
    const MOVEMENT_LABELS = {
        GOODS_RECEIPT: 'Nhập hàng',
        RESERVATION_HOLD: 'Giữ hàng',
        RESERVATION_RELEASE: 'Nhả hàng',
        DISPATCH: 'Xuất hàng',
        RETURN_RESTOCK: 'Hoàn kho',
        STOCK_ADJUSTMENT: 'Điều chỉnh',
    };
    const ROLE_CLASSES = {
        ADMIN: 'role-admin',
        MANAGER: 'role-manager',
        WAREHOUSE_STAFF: 'role-warehouse_staff',
        CUSTOMER: 'role-customer',
    };
    const ROLE_LABELS = {
        CUSTOMER: 'Khách hàng',
        WAREHOUSE_STAFF: 'Nhân viên kho',
        MANAGER: 'Quản lý',
        ADMIN: 'Quản trị viên',
    };
    const PORTAL_TITLES = {
        queue: 'Vận hành đơn hàng',
        inventory: 'Tồn kho & nhập hàng',
        ledger: 'Sổ cái kiểm toán',
        reports: 'Báo cáo quản trị',
        products: 'Quản lý sản phẩm',
        categories: 'Quản lý danh mục',
        admin: 'Quản lý sản phẩm',
    };
    /** Ảnh Unsplash minh họa cho bản demo; tên, SKU và giá thật vẫn lấy nguyên từ API sản phẩm. */
    const PRODUCT_IMAGES = {
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
    };
    /** Tên có dấu hoặc tên mẫu trong seed đều được ghép ảnh; ưu tiên loại hàng trước ảnh danh mục. */
    const PRODUCT_IMAGE_RULES = [
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
    ];
    /** Ảnh dự phòng cho năm nhóm công nghệ; giữ mapping cũ để dữ liệu chưa reset vẫn đọc được. */
    const CATEGORY_IMAGES = {
        'Bàn phím & Chuột': PRODUCT_IMAGES.keyboard,
        'Tai nghe & Loa': PRODUCT_IMAGES.headphones,
        'Webcam & Micro': PRODUCT_IMAGES.macbook,
        'Hub, Cáp & Bộ sạc': PRODUCT_IMAGES.macbook,
        'Màn hình & Phụ kiện bàn làm việc': PRODUCT_IMAGES.macbook,
        'Điện tử': PRODUCT_IMAGES.macbook,
        'Gia dụng': 'https://images.unsplash.com/photo-1555041469-a586c61ea9bc?w=600&auto=format&fit=crop&q=80',
        'Thời trang': 'https://images.unsplash.com/photo-1445205170230-053b83016050?w=600&auto=format&fit=crop&q=80',
        'Phụ kiện': PRODUCT_IMAGES.backpack,
    };
    const state = {
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
        pages: { catalog: 0, orders: 0, queue: 0, inventory: 0, ledger: 0, revenue: 0, top: 0, low: 0, manage: 0 },
    };
    const requests = new Set();
    const channels = new Map();
    /** Gợi ý chỉ giữ trong bộ nhớ; kênh GET riêng không ảnh hưởng kệ hàng, JWT hoặc giỏ. */
    const searchSuggestions = {
        timer: null,
        timeout: null,
        version: 0,
        active: -1,
        items: [],
        composing: false,
    };
    const SEARCH_SUGGESTION_DELAY_MS = 250;
    const SEARCH_SUGGESTION_LIMIT = 6;
    /* Ghi nhận URL đã xử lý để popstate/hashchange không tải cùng trang hai lần. */
    let routedLocation = '';
    /* Debounce xem trước ảnh để không tải CDN sau từng phím gõ; hủy khi chuyển tài khoản. */
    const imagePreviewTimers = new Map();
    const numberFormat = new Intl.NumberFormat('vi-VN');
    const moneyFormat = new Intl.NumberFormat('vi-VN', {
        style: 'currency',
        currency: 'VND',
        minimumFractionDigits: 0,
        maximumFractionDigits: 2,
    });

    /** Truy vấn DOM tập trung để không phụ thuộc thư viện frontend. */
    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

    /* Thanh mua nhỏ chỉ hỗ trợ thao tác; giá, SKU, kiểm tra số lượng và API vẫn dùng form mua chính. */
    const mobilePurchaseMedia = window.matchMedia('(max-width: 767px)');
    let purchaseObserver = null;
    let purchaseScrollFrame = null;
    let cartBounceTimer = null;
    // Một lần đồng bộ nền duy nhất; phiên bản chống phản hồi cũ ghi đè thao tác hoặc màn hình mới.
    let orderRefreshTimer = null;
    let orderRefreshRun = null;
    let orderRefreshVersion = 0;
    let orderRefreshFailures = 0;
    let lastOrderRefreshAttempt = 0;

    /** Đồng bộ nội dung và quyền bấm; chỉ ghim khi nút mua chính đã đi khỏi mép trên màn hình. */
    function syncMobilePurchase() {
        const bar = $('#mobile-purchase-bar');
        const main = $('#shop-product-add-form button[type="submit"]');
        const product = state.detailSku;
        if (!main) {
            purchaseObserver?.disconnect();
            purchaseObserver = null;
        }
        const eligible =
            mobilePurchaseMedia.matches &&
            state.view === 'shop' &&
            state.shopTab === 'product' &&
            Boolean(product && main) &&
            !isOperator() &&
            !document.querySelector('dialog[open]');
        bar.hidden = !eligible || main.getBoundingClientRect().bottom >= 0;
        document.body.classList.toggle('mobile-purchase-visible', !bar.hidden);
        if (!eligible) return;
        const image = $('#mobile-purchase-image');
        const source = productImage(product).src;
        if (image.getAttribute('src') !== source) {
            image.hidden = false;
            image.src = source;
        }
        $('#mobile-purchase-name').textContent = cartProductName(product);
        $('#mobile-purchase-name').title = cartProductName(product) + ' · ' + product.sku;
        $('#mobile-purchase-price').textContent = amount(product.unit_price);
        const button = $('[data-action="sticky-add-cart"]');
        button.disabled = main.disabled || main.getAttribute('aria-busy') === 'true';
        button.setAttribute('aria-label', 'Thêm ' + cartProductName(product) + ' vào giỏ');
    }

    /** Thay observer khi đổi SKU hoặc dựng lại form, tránh theo dõi nút đã bị gỡ khỏi trang. */
    function observeMobilePurchase() {
        purchaseObserver?.disconnect();
        purchaseObserver = null;
        const button = $('#shop-product-add-form button[type="submit"]');
        if (button && 'IntersectionObserver' in window) {
            purchaseObserver = new IntersectionObserver(syncMobilePurchase, { threshold: 0 });
            purchaseObserver.observe(button);
        }
        syncMobilePurchase();
    }
    mobilePurchaseMedia.addEventListener('change', syncMobilePurchase);
    window.addEventListener('resize', syncMobilePurchase);
    // Cuộn nhanh có thể nhảy từ dưới lên trên viewport mà observer không đổi giao cắt; kiểm tra tối đa một lần mỗi khung hình.
    window.addEventListener(
        'scroll',
        () => {
            if (
                !mobilePurchaseMedia.matches ||
                state.view !== 'shop' ||
                state.shopTab !== 'product' ||
                purchaseScrollFrame !== null
            )
                return;
            purchaseScrollFrame = window.requestAnimationFrame(() => {
                purchaseScrollFrame = null;
                syncMobilePurchase();
            });
        },
        { passive: true },
    );
    document.addEventListener('close', syncMobilePurchase, true);

    /** Phản hồi thêm giỏ thành công; người chọn giảm chuyển động được CSS loại bỏ hiệu ứng. */
    function bounceCart() {
        const cart = $('.cart-trigger');
        window.clearTimeout(cartBounceTimer);
        cart.classList.remove('cart-added');
        void cart.offsetWidth;
        cart.classList.add('cart-added');
        cartBounceTimer = window.setTimeout(() => cart.classList.remove('cart-added'), 450);
    }

    /** Chỉ kệ hàng và phần giới thiệu dùng hiệu ứng cuộn; form mua hàng và dashboard luôn hiện ngay. */
    const storefrontRevealSelector = [
        '.shop-hero',
        '.shop-benefits > span',
        '.shelf-heading',
        '.catalog-discovery-filters',
        '.product-card',
        '.product-description',
        '.product-specifications',
        '.shop-footer',
    ].join(', ');
    const reducedStorefrontMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
    const observedStorefrontReveals = new Set();
    const productRevealThreshold = 0.12;
    let storefrontRevealObserver = null;
    /** Menu cạnh banner chỉ mở bảng con trên desktop; điện thoại chọn nhóm từ dải cuộn ngang. */
    const homeCategoryDesktop = window.matchMedia('(min-width: 1001px)');
    homeCategoryDesktop.addEventListener('change', () => {
        setShopCategoryMenu(false);
        closeHomeCategoryMenu();
    });
    /** Đổi kích thước đóng lớp phủ để không giữ menu hoặc khóa cuộn ở bố cục cũ. */
    window.addEventListener('resize', () => {
        if (state.categoryMenuOpen) setShopCategoryMenu(false);
    });

    /** Giới thiệu hiện một lần; thẻ sản phẩm tiếp tục được theo dõi để có hiệu ứng cuộn hai chiều. */
    function revealStorefrontElement(element) {
        element.classList.add('is-revealed');
        if (!element.matches('.product-card')) {
            storefrontRevealObserver?.unobserve(element);
            observedStorefrontReveals.delete(element);
        }
    }

    /** Nội dung mặc định luôn hiện; chỉ phần dưới màn hình có observer mới chờ hiệu ứng cuộn. */
    function prepareStorefrontReveals() {
        if (reducedStorefrontMotion.matches || !('IntersectionObserver' in window)) return;
        if (!storefrontRevealObserver) {
            storefrontRevealObserver = new IntersectionObserver(
                (entries) => {
                    entries.forEach((entry) => {
                        const element = entry.target;
                        if (!element.matches('.product-card')) {
                            if (entry.isIntersecting) revealStorefrontElement(element);
                            return;
                        }
                        if (entry.isIntersecting && entry.intersectionRatio >= productRevealThreshold) {
                            revealStorefrontElement(element);
                            return;
                        }
                        // Cuộn lên làm thẻ rời mép dưới: thu nhẹ và mờ dần, không ẩn các thẻ đang xem.
                        const bottom = entry.rootBounds?.bottom ?? window.innerHeight;
                        const leavingBottom =
                            entry.boundingClientRect.top >=
                            bottom - entry.boundingClientRect.height * productRevealThreshold;
                        if (leavingBottom && !element.contains(document.activeElement)) {
                            element.classList.remove('is-revealed');
                        }
                    });
                },
                { rootMargin: '0px 0px -32px 0px', threshold: [0, productRevealThreshold] },
            );
        }
        // Khi phân trang hoặc đổi SKU, bỏ tham chiếu tới các thẻ cũ đã rời DOM.
        observedStorefrontReveals.forEach((element) => {
            if (!element.isConnected) {
                storefrontRevealObserver.unobserve(element);
                observedStorefrontReveals.delete(element);
            }
        });
        $$(storefrontRevealSelector, $('.shop-main')).forEach((element, index) => {
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
                observedStorefrontReveals.add(element);
                storefrontRevealObserver.observe(element);
            }
        });
    }

    /** Đổi thiết lập hệ điều hành giữa phiên cũng hiện ngay mọi nội dung đang chờ. */
    reducedStorefrontMotion.addEventListener('change', () => {
        if (reducedStorefrontMotion.matches) {
            observedStorefrontReveals.forEach(revealStorefrontElement);
            storefrontRevealObserver?.disconnect();
            observedStorefrontReveals.clear();
        } else {
            prepareStorefrontReveals();
        }
    });

    /** Tab tới sản phẩm dưới màn hình phải thấy ngay nội dung, không chờ hiệu ứng. */
    document.addEventListener('focusin', (event) => {
        const element = event.target.closest('.scroll-reveal');
        if (element) revealStorefrontElement(element);
    });

    /** Escape dữ liệu backend trước khi ghép HTML, kể cả SKU, tên, email và ghi chú ledger. */
    const escapeHtml = (value) =>
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
        );
    /** Các bộ định dạng chỉ nhận số hữu hạn, không hiển thị NaN khi response lỗi. */
    const amount = (value) => moneyFormat.format(Number.isFinite(Number(value)) ? Number(value) : 0);
    const integer = (value) => numberFormat.format(Number.isFinite(Number(value)) ? Number(value) : 0);
    /** Kiểm tra role ở giao diện chỉ phục vụ hiển thị; backend vẫn kiểm tra tất cả request. */
    const hasRole = (...roles) => Boolean(state.user && roles.includes(state.user.role));
    const icon = (name) => '<svg class="icon" aria-hidden="true"><use href="#i-' + name + '"/></svg>';
    /** Ngày giờ Việt Nam để đọc dễ; khoảng ngày báo cáo được gửi theo contract UTC. */
    function dateTime(value) {
        const date = new Date(value);
        return value && !Number.isNaN(date.getTime()) ? date.toLocaleString('vi-VN') : '—';
    }
    /** Badge trạng thái chỉ dùng class từ danh sách enum đã biết, không nhận class tùy ý từ API. */
    function statusBadge(status) {
        const known = Object.hasOwn(STATUS_LABELS, status);
        return (
            '<span class="badge ' +
            (known ? 'status-' + status.toLowerCase() : 'neutral') +
            '" title="' +
            escapeHtml(status) +
            '">' +
            escapeHtml(STATUS_LABELS[status] || status) +
            '<small class="status-code">' +
            escapeHtml(status) +
            '</small></span>'
        );
    }
    /** Lỗi HTTP giữ mã thật và thông điệp tiếng Việt, không đưa Authorization/token ra DOM. */
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

    /** Hủy request cũ khi đổi tài khoản; epoch ngăn response chậm ghi dữ liệu sang vai trò mới. */
    function cancelRequests() {
        closeSearchSuggestions();
        state.epoch++;
        requests.forEach((controller) => controller.abort());
        requests.clear();
        channels.clear();
    }

    /** Hủy đồng bộ nền trước thao tác tay, điều hướng hoặc đổi actor; request cũ không được ghi đè đơn mới. */
    function invalidateOrderRefresh() {
        orderRefreshVersion++;
        window.clearTimeout(orderRefreshTimer);
        orderRefreshTimer = null;
        if (orderRefreshRun) window.clearTimeout(orderRefreshRun.timeout);
        orderRefreshRun = null;
        ['auto-order-list', 'auto-order-detail'].forEach((name) => {
            channels.get(name)?.abort();
            channels.delete(name);
        });
    }

    /** Chỉ tự đồng bộ đơn của CUSTOMER ở màn hình đang xem; không gọi API vận hành hoặc chạy trong tab ẩn. */
    function canAutoRefreshOrders() {
        return (
            hasRole('CUSTOMER') &&
            state.view === 'shop' &&
            state.shopTab === 'orders' &&
            document.visibilityState === 'visible' &&
            !state.authBusy &&
            !document.querySelector('dialog[open]')
        );
    }

    /** Thông báo nhỏ giữ dữ liệu đã xem khi mạng lỗi, không thay bảng bằng skeleton mỗi lần đồng bộ. */
    function setOrderSyncMessage(message) {
        $$('[data-order-sync]').forEach((element) => {
            element.textContent = message;
        });
    }

    /** Lặp sau khi request kết thúc; tăng thời gian chờ khi lỗi mạng và quét nhanh hơn sau hạn giữ hàng. */
    function scheduleOrderRefresh(delay) {
        window.clearTimeout(orderRefreshTimer);
        orderRefreshTimer = null;
        if (!canAutoRefreshOrders() || orderRefreshRun) return;
        const expired =
            state.order?.status === 'PENDING' && new Date(state.order.reservation_expires_at).getTime() <= Date.now();
        const retryDelay = orderRefreshFailures
            ? Math.min(60000, ORDER_REFRESH_MS * 2 ** orderRefreshFailures)
            : expired
              ? EXPIRED_ORDER_REFRESH_MS
              : ORDER_REFRESH_MS;
        // Focus và visibility có thể tới cùng lúc; không tạo thêm request trong cùng một giây.
        const wait = Math.max(delay ?? retryDelay, 1000 - (Date.now() - lastOrderRefreshAttempt));
        orderRefreshTimer = window.setTimeout(() => execute(refreshOrdersQuietly), wait);
    }

    /** GET nền giữ trang/đơn đang chọn; chỉ render phần thay đổi và luôn lấy trạng thái từ backend. */
    async function refreshOrdersQuietly() {
        if (!canAutoRefreshOrders() || orderRefreshRun) return;
        if (channels.has('my-orders') || channels.has('order-detail') || $('#order-detail [aria-busy="true"]')) {
            scheduleOrderRefresh();
            return;
        }
        const run = { version: orderRefreshVersion, timedOut: false, timeout: null };
        orderRefreshRun = run;
        lastOrderRefreshAttempt = Date.now();
        const current = () => run.version === orderRefreshVersion && canAutoRefreshOrders();
        run.timeout = window.setTimeout(() => {
            run.timedOut = true;
            ['auto-order-list', 'auto-order-detail'].forEach((name) => channels.get(name)?.abort());
        }, READ_TIMEOUT_MS);
        try {
            const result = await api('/orders/my', {
                channel: 'auto-order-list',
                query: { page: state.pages.orders, size: 8 },
            });
            if (!current()) return;
            const selectedId = state.order?.id;
            let order = selectedId
                ? result.content.find((value) => value.id === selectedId)
                : result.content[0] || null;
            if (selectedId && !order) {
                order = await api('/orders/' + selectedId, { channel: 'auto-order-detail' });
            }
            if (!current()) return;
            const detailChanged = JSON.stringify(order) !== JSON.stringify(state.order);
            if (detailChanged) await hydrateOrderProducts(order);
            if (!current()) return;
            if (JSON.stringify(result) !== JSON.stringify(state.myOrdersPage)) renderMyOrders(result);
            if (detailChanged) {
                state.order = order;
                renderOrder();
            }
            orderRefreshFailures = 0;
            setOrderSyncMessage(
                'Đã đồng bộ lúc ' + new Date().toLocaleTimeString('vi-VN') + ' · Tự cập nhật khi đang xem.',
            );
        } catch (error) {
            if (!current() || (error.name === 'AbortError' && !run.timedOut)) return;
            orderRefreshFailures = Math.min(orderRefreshFailures + 1, 3);
            setOrderSyncMessage('Chưa đồng bộ được. Hệ thống sẽ thử lại; bạn vẫn có thể bấm Tải lại.');
        } finally {
            window.clearTimeout(run.timeout);
            if (orderRefreshRun === run) {
                orderRefreshRun = null;
                scheduleOrderRefresh();
            }
        }
    }

    /** Tab ẩn/rời trang dừng GET nền; quay lại và có mạng thì đọc lại đơn, không gửi thao tác thanh toán/hủy. */
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState !== 'visible') invalidateOrderRefresh();
        else scheduleOrderRefresh(0);
    });
    window.addEventListener('focus', () => scheduleOrderRefresh(0));
    window.addEventListener('online', () => {
        orderRefreshFailures = 0;
        scheduleOrderRefresh(0);
    });
    $$('dialog').forEach((dialog) => dialog.addEventListener('close', () => scheduleOrderRefresh(0)));

    /** API cùng origin với JWT; mỗi kênh GET chỉ nhận response mới nhất khi đổi filter/phân trang. */
    async function api(path, { method = 'GET', body, query, anonymous = false, channel, idempotencyKey } = {}) {
        const epoch = state.epoch;
        const controller = new AbortController();
        if (channel) {
            channels.get(channel)?.abort();
            channels.set(channel, controller);
        }
        requests.add(controller);
        const url = new URL('/api/v1' + path, window.location.origin);
        Object.entries(query || {}).forEach(([key, value]) => {
            if (value !== '' && value !== null && value !== undefined) url.searchParams.set(key, value);
        });
        const headers = { Accept: 'application/json' };
        if (idempotencyKey) headers['Idempotency-Key'] = idempotencyKey;
        if (state.token && !anonymous) headers.Authorization = 'Bearer ' + state.token;
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        try {
            const response = await fetch(url, {
                method,
                headers,
                signal: controller.signal,
                credentials: 'omit',
                body: body !== undefined ? JSON.stringify(body) : undefined,
            });
            const raw = await response.text();
            if (epoch !== state.epoch || (channel && channels.get(channel) !== controller)) {
                throw new DOMException('Phiên giao diện đã thay đổi.', 'AbortError');
            }
            let payload;
            try {
                payload = raw ? JSON.parse(raw) : null;
            } catch {
                payload = { message: 'Hệ thống trả dữ liệu không hợp lệ. Vui lòng thử lại.' };
            }
            if (!response.ok) {
                const error = new ApiError(
                    response.status,
                    payload?.error || response.statusText,
                    payload,
                    url.pathname,
                );
                if (response.status === 401 && state.token && !anonymous) {
                    clearSession();
                    renderIdentity();
                    renderPermissions();
                    // Phiên hết hạn vẫn tải lại cửa hàng công khai, không để người xem gặp trang trắng.
                    replaceHash();
                    execute(() => loadReferences().then(loadCatalog));
                }
                throw error;
            }
            if (method !== 'GET' && /^\/(orders|products)(\/|$)/.test(path)) state.discoveryDirty = true;
            $('#last-sync').textContent = 'Cập nhật lúc ' + new Date().toLocaleTimeString('vi-VN');
            return payload;
        } catch (error) {
            if (error.name === 'AbortError' || error instanceof ApiError) throw error;
            throw new ApiError(
                0,
                'Không thể kết nối',
                {
                    message: 'Không kết nối được hệ thống. Kiểm tra ứng dụng Spring Boot đang chạy rồi thử lại.',
                },
                url.pathname,
            );
        } finally {
            requests.delete(controller);
            if (channel && channels.get(channel) === controller) channels.delete(channel);
        }
    }

    /** Banner giữ kết quả và toast tự đóng; người xem thấy rõ 409 Conflict, 403 Forbidden và validation. */
    function notify(kind, message, title = '', path = '') {
        const error = kind === 'error';
        const notice = $('#api-notice');
        notice.className = 'notice' + (error ? ' error' : '');
        notice.hidden = false;
        notice.innerHTML =
            icon(error ? 'alert' : 'check') +
            '<div class="notice-content"><strong>' +
            escapeHtml(title || (error ? 'Không thể thực hiện' : 'Thành công')) +
            '</strong><span>' +
            escapeHtml(message) +
            '</span>' +
            (path ? '<small class="mono">' + escapeHtml(path) + '</small>' : '') +
            '</div>' +
            '<button class="icon-button" type="button" data-action="dismiss-notice" aria-label="Đóng thông báo">' +
            icon('close') +
            '</button>';
        const openDialog = $('dialog[open]');
        if (openDialog && error) {
            let feedback = $('[data-dialog-notice]', openDialog);
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
            icon(error ? 'alert' : 'check') +
            '<div><strong>' +
            escapeHtml(title || (error ? 'Thông báo lỗi' : 'Thành công')) +
            '</strong><p>' +
            escapeHtml(message) +
            '</p></div>' +
            '<button class="icon-button" type="button" data-action="dismiss-toast" aria-label="Đóng toast">' +
            icon('close') +
            '</button>';
        $('#toasts').append(toast);
        while ($('#toasts').children.length > 3) $('#toasts').firstElementChild.remove();
        window.setTimeout(() => toast.remove(), error ? 10000 : 6500);
    }
    /** Response bị hủy có chủ đích không tạo toast; lỗi backend luôn hiển thị mã HTTP nguyên gốc. */
    function handleError(error) {
        if (error.name === 'AbortError') return;
        const title = error.status ? 'HTTP ' + error.status + ' ' + error.reason : 'Thông báo';
        notify('error', error.message || 'Không thể thực hiện thao tác.', title, error.path);
    }
    /** Bọc event bất đồng bộ để không sinh promise rejection ngoài luồng phản hồi giao diện. */
    function execute(operation) {
        Promise.resolve().then(operation).catch(handleError);
    }
    /** Khóa nút đang gửi để hạn chế nhấp lặp; backend vẫn chịu trách nhiệm idempotency. */
    async function busy(button, operation) {
        if (button.disabled || button.getAttribute('aria-busy') === 'true') return;
        const epoch = state.epoch;
        button.disabled = true;
        button.setAttribute('aria-busy', 'true');
        syncMobilePurchase();
        try {
            await operation();
        } finally {
            if (epoch === state.epoch && button.isConnected) {
                button.disabled = false;
                button.removeAttribute('aria-busy');
                renderPermissions();
                syncMobilePurchase();
                scheduleOrderRefresh();
            }
        }
    }

    /** Chỉ lưu token trong phiên của tab; mật khẩu và vai trò không được ghi vào storage. */
    function saveSession() {
        try {
            sessionStorage.setItem(SESSION_KEY, JSON.stringify({ token: state.token }));
        } catch {
            /* Storage bị chặn thì phiên vẫn chạy trong bộ nhớ. */
        }
    }

    /** Không tin role từ storage; chủ giỏ được xác định sau khi login hoặc users/me trả thành công. */
    function cartOwner() {
        if (!state.user) return 'guest';
        return hasRole('CUSTOMER') ? 'customer:' + state.user.id : null;
    }

    /** Giỏ không chứa token, giá, ảnh hoặc thông tin người nhận; lỗi storage không chặn mua hàng. */
    function writeCartSnapshot(items, warehouseId = state.branchId) {
        const owner = cartOwner();
        if (!owner) return;
        try {
            sessionStorage.setItem(
                CART_KEY,
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

    /** Chỉ ghi giỏ đã khôi phục xong, tránh ghi rỗng đè bản lưu khi API đang tải hoặc tạm lỗi. */
    function saveCart() {
        if (!state.cartReady || state.cartLoading || state.cartRestoreFailed) return;
        writeCartSnapshot(
            [...state.cart.values()].map((item) => ({ product_id: item.product.id, quantity: item.quantity })),
        );
    }

    /** Đăng xuất, đổi actor hoặc dữ liệu lưu sai định dạng đều bỏ giỏ cũ khỏi phiên của tab. */
    function forgetCart() {
        forgetCheckoutAttempt();
        try {
            sessionStorage.removeItem(CART_KEY);
        } catch {
            /* Dữ liệu trong bộ nhớ vẫn được xóa nếu storage không truy cập được. */
        }
    }

    /** Kiểm tra phiên bản, chủ giỏ và giới hạn request; chỉ nhận ID/số lượng nguyên hợp lệ. */
    function readSavedCart() {
        try {
            const raw = sessionStorage.getItem(CART_KEY);
            if (!raw) return null;
            if (raw.length > 20000) throw new Error('Bản lưu giỏ vượt giới hạn.');
            const saved = JSON.parse(raw);
            if (
                saved?.version !== 1 ||
                saved.owner !== cartOwner() ||
                !Array.isArray(saved.items) ||
                saved.items.length > MAX_CART_ITEMS
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
                    item.quantity > MAX_QUANTITY ||
                    ids.has(item.product_id)
                ) {
                    return false;
                }
                ids.add(item.product_id);
                return true;
            });
            return {
                items: items.map((item) => ({ product_id: item.product_id, quantity: item.quantity })),
                warehouse_id:
                    Number.isSafeInteger(saved.warehouse_id) && saved.warehouse_id > 0 ? saved.warehouse_id : null,
            };
        } catch {
            forgetCart();
            return null;
        }
    }

    /** SKU con lấy lại mapping model/phiên bản/màu; không phục hồi lựa chọn đã lưu trữ hoặc ngừng bán. */
    async function loadSavedCartProduct(id, cache) {
        const getProduct = (productId) => {
            if (!cache.has(productId)) {
                cache.set(
                    productId,
                    api('/products/' + productId, { anonymous: true, channel: 'cart-product-' + productId }),
                );
            }
            return cache.get(productId);
        };
        const product = await getProduct(id);
        if (product.parent_product_id) {
            const root = await getProduct(product.parent_product_id);
            const variant = root.variants?.find((value) => value.sku_product_id === id);
            return root.status === 'ACTIVE' && variant ? colorSku(root, variant) : null;
        }
        return product.status === 'ACTIVE' ? saleSku(product) : null;
    }

    /** Đọc lại catalog theo lô nhỏ; lỗi mạng giữ bản lưu để thử lại, 404/ngừng bán bỏ mặt hàng tương ứng. */
    async function restoreCart() {
        if (isOperator() || state.cartLoading) return;
        const epoch = state.epoch;
        const saved = readSavedCart();
        state.cartReady = false;
        state.cartLoading = true;
        state.cartRestoreFailed = false;
        if (saved?.warehouse_id) state.branchId = String(saved.warehouse_id);
        renderCart();
        const cache = new Map();
        // API treo không giữ toàn bộ cửa hàng ở trạng thái tải mãi; bản lưu được giữ để khách thử lại.
        const timeout = window.setTimeout(() => {
            if (epoch !== state.epoch) return;
            cache.forEach((_, id) => channels.get('cart-product-' + id)?.abort());
        }, READ_TIMEOUT_MS);
        const restored = new Map(state.cart);
        let removed = 0;
        try {
            const items = saved?.items || [];
            for (let offset = 0; offset < items.length; offset += 4) {
                const results = await Promise.allSettled(
                    items.slice(offset, offset + 4).map(async (item) => {
                        const product = await loadSavedCartProduct(item.product_id, cache);
                        return { product, item };
                    }),
                );
                if (epoch !== state.epoch) return;
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
                        restored.set(id, { product, quantity: restored.get(id)?.quantity || item.quantity });
                    }
                }
            }
            state.cart = restored;
            restored.forEach((item) => state.products.set(item.product.id, item.product));
            state.cartReady = true;
            if (removed) {
                notify(
                    'error',
                    'Đã bỏ ' + removed + ' mặt hàng không còn bán khỏi giỏ. Vui lòng kiểm tra lại giỏ hàng.',
                );
            }
        } catch (error) {
            if (epoch !== state.epoch) return;
            state.cartRestoreFailed = true;
        } finally {
            window.clearTimeout(timeout);
            if (epoch === state.epoch) {
                state.cartLoading = false;
                renderCart();
                saveCart();
            }
        }
    }

    /** Xóa dữ liệu riêng và hủy request cũ; chỉ giữ giỏ khách vãng lai khi họ bắt đầu đăng nhập. */
    function clearSession({ preserveCart = false } = {}) {
        invalidateOrderRefresh();
        cancelRequests();
        if (!preserveCart) forgetCart();
        state.token = null;
        state.user = null;
        state.profileReady = false;
        state.profileSaving = false;
        state.order = null;
        state.myOrdersPage = null;
        state.cartLoading = false;
        state.cartRestoreFailed = false;
        orderRefreshFailures = 0;
        state.pendingMutation = null;
        state.operatingWarehouses = [];
        state.authBusy = false;
        state.view = 'shop';
        state.shopTab = 'catalog';
        state.productId = null;
        state.detailRoot = null;
        state.detailSku = null;
        state.portalTab = 'queue';
        state.catalogExpanded = false;
        if (!preserveCart) {
            state.cart.clear();
            state.cartReady = true;
            resetCheckoutDetails();
        }
        Object.keys(state.pages).forEach((key) => {
            state.pages[key] = 0;
        });
        try {
            sessionStorage.removeItem(SESSION_KEY);
        } catch {
            /* Không có storage thì state hiện tại đã được xóa. */
        }
        $$('dialog[open]').forEach((dialog) => dialog.close());
        $$('[data-dialog-notice]').forEach((element) => element.remove());
        $$('[aria-busy="true"]').forEach((button) => {
            button.disabled = false;
            button.removeAttribute('aria-busy');
        });
        ['orders', 'queue', 'inventory', 'ledger', 'revenue', 'top', 'low', 'manage'].forEach((name) => {
            $('#' + name + '-rows').replaceChildren();
            $('#' + name + '-pagination').replaceChildren();
            const count = $('#' + name + '-count');
            if (count) count.textContent = '—';
        });
        ['stock-available', 'stock-reserved', 'stock-physical'].forEach((id) => {
            $('#' + id).textContent = '—';
        });
        $('#order-summary').replaceChildren();
        $('#summary-statuses').replaceChildren();
        $('#revenue-chart').replaceChildren();
        $('#top-chart').replaceChildren();
        purchaseObserver?.disconnect();
        purchaseObserver = null;
        window.cancelAnimationFrame(purchaseScrollFrame);
        purchaseScrollFrame = null;
        window.clearTimeout(cartBounceTimer);
        $('.cart-trigger').classList.remove('cart-added');
        $('#shop-product-detail-body').replaceChildren();
        delete $('#shop-product-detail-body').dataset.productId;
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
            $('#' + id).reset();
        });
        resetProductImagePreviews();
        delete $('#brand-logo-edit').dataset.brandId;
        delete $('#category-edit').dataset.categoryId;
        state.categoryProductCounts.clear();
        $('#category-admin-query').value = '';
        $('#brand-admin-query').value = '';
        ['profile-display-name', 'profile-email', 'profile-created'].forEach((id) => {
            $('#' + id).textContent = '—';
        });
        $('#profile-status').textContent = '';
        $$('[data-brand-logo-input]').forEach(renderBrandLogoPreview);
        $('#api-notice').hidden = true;
        $('#toasts').replaceChildren();
        renderWarehouses();
        renderCart();
        renderOrder();
        renderContext();
        syncQueueStatusTabs();
    }

    /** Hai nhóm giao diện được mở theo danh tính backend; hash hay DOM không cấp quyền API. */
    const isOperator = () => hasRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF');
    /** Hai mục catalog mới và đường dẫn admin cũ đều chỉ mở cho ADMIN. */
    function canPortalTab(tab) {
        return (
            isOperator() &&
            (['queue', 'inventory'].includes(tab) ||
                (['ledger', 'reports'].includes(tab) && hasRole('ADMIN', 'MANAGER')) ||
                (['products', 'categories', 'admin'].includes(tab) && hasRole('ADMIN')))
        );
    }

    /** Badge và thanh demo dựa trên actor đã xác thực; nhân viên đăng nhập tự động vào cổng vận hành. */
    function renderIdentity() {
        $('#identity-email').textContent = state.user?.email || 'Chưa đăng nhập';
        $('#identity-role').textContent = ROLE_LABELS[state.user?.role] || 'Khách';
        $('#identity-role').className = 'badge ' + (ROLE_CLASSES[state.user?.role] || 'neutral');
        $('#open-login').hidden = Boolean(state.user);
        $('#logout').hidden = !state.user;
        $('#portal-name').textContent = state.user?.full_name || state.user?.email || '';
        $('#portal-scope').textContent = hasRole('WAREHOUSE_STAFF')
            ? 'Chỉ các kho được phân công'
            : 'Vận hành tất cả kho';
        $$('[data-demo-role]').forEach((button) => {
            const active = state.user?.email === DEMO_ACCOUNTS[button.dataset.demoRole].email;
            button.classList.toggle('active', active);
            button.setAttribute('aria-pressed', String(active));
            button.disabled = state.authBusy;
        });
        $('#auth-submit').disabled = state.authBusy;
    }

    /** Không cho CUSTOMER thấy menu hoặc form quản trị; staff cũng không thấy ledger/báo cáo. */
    function renderPermissions() {
        $$('[data-roles]').forEach((element) => {
            element.hidden = !hasRole(...element.dataset.roles.split(','));
        });
        // Không sửa hồ sơ khi dữ liệu chưa tải xong hoặc đang ghi; tránh ghi đè nội dung đang nhập.
        $$('#profile-form input, #profile-save').forEach((element) => {
            element.disabled =
                !hasRole('CUSTOMER') ||
                !state.profileReady ||
                state.profileSaving ||
                state.authBusy ||
                element.getAttribute('aria-busy') === 'true';
        });
        $$('.shop-header [data-action="my-orders"], [data-shop-tab="orders"]').forEach((button) => {
            button.hidden = isOperator();
        });
        const create = $('#create-order');
        create.disabled =
            state.cart.size === 0 ||
            isOperator() ||
            state.authBusy ||
            state.cartLoading ||
            state.cartRestoreFailed ||
            create.getAttribute('aria-busy') === 'true';
        create.textContent = state.user ? 'Tiến hành đặt hàng' : 'Đăng nhập để đặt hàng';
        $('#cart-permission').textContent = isOperator()
            ? 'Dùng tài khoản khách hàng để mua sắm.'
            : state.cartLoading
              ? 'Đang khôi phục giỏ và cập nhật thông tin sản phẩm…'
              : 'Một đơn được chuẩn bị tại một chi nhánh. Giữ hàng trong 15 phút.';
        // Không cho sửa giỏ chưa đồng bộ vì lần thử lại có thể phục hồi số lượng cũ từ bản lưu.
        const cartLocked = state.cartLoading || state.cartRestoreFailed || state.authBusy;
        $$('[data-cart-quantity], [data-action="remove-cart"]').forEach((element) => {
            element.disabled = cartLocked;
        });
        $$('[data-action="cart-plus"], [data-action="cart-minus"]').forEach((button) => {
            const quantity = state.cart.get(Number(button.dataset.id))?.quantity || 0;
            button.disabled =
                cartLocked || (button.dataset.action === 'cart-minus' ? quantity <= 1 : quantity >= MAX_QUANTITY);
        });
        $$('#stock-in button[type="submit"], #inventory-filter button[type="submit"]').forEach((button) => {
            if (button.getAttribute('aria-busy') !== 'true') {
                button.disabled = hasRole('WAREHOUSE_STAFF') && !state.operatingWarehouses.length;
            }
        });
    }

    /** Việc ẩn dashboard áp dụng ngay cả trước khi tải dữ liệu, tránh nháy menu nội bộ cho khách. */
    function renderContext() {
        setShopCategoryMenu(false);
        closeHomeCategoryMenu();
        if (state.portalTab === 'admin') state.portalTab = 'products';
        if (state.view === 'portal' && !isOperator()) state.view = 'shop';
        if (!canPortalTab(state.portalTab)) state.portalTab = 'queue';
        if (['orders', 'account'].includes(state.shopTab) && !hasRole('CUSTOMER')) state.shopTab = 'catalog';
        $('#storefront-view').hidden = state.view !== 'shop';
        $('#dashboard-view').hidden = state.view !== 'portal';
        document.body.classList.toggle('portal-open', state.view === 'portal');
        $$('.shop-panel').forEach((panel) => {
            panel.hidden = panel.id !== 'shop-' + state.shopTab;
        });
        $$('.portal-panel').forEach((panel) => {
            panel.hidden = panel.id !== 'portal-' + state.portalTab;
        });
        $$('[data-shop-tab]').forEach((button) =>
            setActive(
                button,
                button.dataset.shopTab === state.shopTab ||
                    (button.dataset.shopTab === 'catalog' && state.shopTab === 'product'),
            ),
        );
        // Menu có nhiều nút: dùng danh sách DOM để cập nhật trạng thái mà không làm gián đoạn đăng nhập.
        $$('[data-portal-tab]').forEach((button) => setActive(button, button.dataset.portalTab === state.portalTab));
        renderCatalogNavigation();
        $('#portal-title').textContent = PORTAL_TITLES[state.portalTab];
        document.title =
            state.view === 'shop'
                ? 'StockFlow Tech — ' +
                  (state.shopTab === 'orders'
                      ? 'Đơn hàng của tôi'
                      : state.shopTab === 'account'
                        ? 'Tài khoản của tôi'
                        : state.shopTab === 'product'
                          ? 'Chi tiết sản phẩm'
                          : state.shopTab === 'help'
                            ? 'Hướng dẫn mua hàng'
                            : 'Cửa hàng công nghệ')
                : 'StockFlow — ' + PORTAL_TITLES[state.portalTab];
        renderIdentity();
        renderPermissions();
        prepareStorefrontReveals();
        syncMobilePurchase();
    }

    /** Menu mở/đóng theo người dùng; ẩn mục con cũng loại chúng khỏi thứ tự focus. */
    function renderCatalogNavigation() {
        $('#nav-catalog-group').classList.toggle('expanded', state.catalogExpanded);
        const toggle = $('#catalog-group-toggle');
        toggle.classList.toggle(
            'active',
            state.view === 'portal' && ['products', 'categories'].includes(state.portalTab),
        );
        toggle.setAttribute('aria-expanded', String(state.catalogExpanded));
        $('#catalog-subitems').hidden = !state.catalogExpanded;
    }

    /** Gắn trạng thái điều hướng và aria-current để cả bàn phím/trình đọc màn hình hiểu vị trí. */
    function setActive(button, active) {
        button.classList.toggle('active', active);
        if (active) button.setAttribute('aria-current', 'page');
        else button.removeAttribute('aria-current');
    }

    /** Đăng nhập giữ giỏ vãng lai, xóa dữ liệu của actor cũ và chuyển không gian theo role thật. */
    async function authenticate(credentials, register = false, demo = false, verification = false) {
        if (state.authBusy) return;
        const intent = demo ? 'login' : state.authIntent;
        const guestCart = !state.user ? readSavedCart() : null;
        if (!verification) clearSession({ preserveCart: !state.user });
        state.authBusy = true;
        if (verification) updateOtpCountdown();
        renderIdentity();
        renderPermissions();
        const epoch = state.epoch;
        $('#auth-error').hidden = true;
        try {
            const result = await api(verification ? '/auth/verify-email' : register ? '/auth/register' : '/auth/login', {
                method: 'POST',
                body: credentials,
                anonymous: true,
            });
            if (result.requires_verification) {
                $('#auth-password').value = '';
                openOtp(result.email, true);
                return;
            }
            $('#otp-dialog').close();
            state.token = result.access_token;
            state.user = result.user;
            saveSession();
            $('#auth-password').value = '';
            if (isOperator()) {
                state.cart.clear();
                forgetCart();
            } else {
                // Chỉ giỏ khách vãng lai được chuyển vào tài khoản vừa đăng nhập, không lấy giỏ actor cũ.
                if (guestCart) writeCartSnapshot(guestCart.items, guestCart.warehouse_id);
                await restoreCart();
                if (epoch !== state.epoch) return;
            }
            state.view = isOperator() ? 'portal' : 'shop';
            state.portalTab = hasRole('MANAGER') ? 'reports' : 'queue';
            state.shopTab = ['orders', 'account'].includes(intent) && hasRole('CUSTOMER') ? intent : 'catalog';
            renderContext();
            renderCart();
            replaceHash();
            notify(
                'success',
                'Xin chào ' + (result.user.full_name || result.user.email) + '.',
                register ? 'HTTP 201 Created' : 'Đăng nhập thành công',
            );
            await loadReferences();
            await refreshSection();
            if (pendingVNPayReturn) await consumeVNPayReturn();
            if (intent === 'checkout' && hasRole('CUSTOMER') && state.cart.size) openDialog('cart-dialog');
            if (intent === 'portal' && hasRole('CUSTOMER')) {
                notify(
                    'error',
                    'Tài khoản khách hàng sử dụng cửa hàng. Đăng nhập nhân viên, quản lý hoặc admin để vào cổng quản trị.',
                );
            }
        } catch (error) {
            if (epoch === state.epoch && error.payload?.error === 'EMAIL_NOT_VERIFIED') {
                $('#auth-password').value = '';
                openOtp(error.payload.email, false);
                return;
            }
            if (verification && epoch === state.epoch && !state.token) {
                $('#otp-error').textContent = error.message;
                $('#otp-error').hidden = false;
                return;
            }
            if (epoch === state.epoch && error.name !== 'AbortError' && !state.token && !demo) {
                openDialog('auth-dialog');
                $('#auth-error').textContent = error.message;
                $('#auth-error').hidden = false;
            }
            throw error;
        } finally {
            if (epoch === state.epoch) {
                state.authBusy = false;
                renderIdentity();
                renderPermissions();
                if ($('#otp-dialog').open) updateOtpCountdown();
            }
        }
    }

    let otpEmail = '';
    let otpResendAt = 0;
    let otpTimer;
    let otpResendBusy = false;

    function updateOtpCountdown() {
        const seconds = Math.max(0, Math.ceil((otpResendAt - Date.now()) / 1000));
        $('#otp-resend').disabled = seconds > 0 || otpResendBusy || state.authBusy;
        $('#otp-resend').textContent = seconds ? `Gửi lại mã (${seconds}s)` : 'Gửi lại mã';
        $('#otp-submit').disabled = state.authBusy || otpResendBusy;
    }

    function openOtp(email, justSent) {
        if (otpEmail !== email) otpResendAt = 0;
        otpEmail = email;
        if (justSent) otpResendAt = Date.now() + 60000;
        $('#otp-email').textContent = email;
        $('#otp-code').value = '';
        $('#otp-error').hidden = true;
        openDialog('otp-dialog');
        clearInterval(otpTimer);
        otpTimer = setInterval(updateOtpCountdown, 1000);
        updateOtpCountdown();
        $('#otp-code').focus();
    }

    async function resendOtp() {
        if (otpResendBusy || state.authBusy || Date.now() < otpResendAt) return;
        otpResendBusy = true;
        updateOtpCountdown();
        $('#otp-error').hidden = true;
        try {
            await api('/auth/resend-otp', { method: 'POST', body: { email: otpEmail }, anonymous: true });
            otpResendAt = Date.now() + 60000;
            notify('success', 'Đã gửi mã OTP mới. Mã cũ không còn hiệu lực.');
        } catch (error) {
            if (error.status === 429) otpResendAt = Date.now() + 60000;
            $('#otp-error').textContent = error.message;
            $('#otp-error').hidden = false;
        } finally {
            otpResendBusy = false;
            updateOtpCountdown();
        }
    }

    $('#otp-dialog').addEventListener('close', () => clearInterval(otpTimer));
    $('#otp-resend').addEventListener('click', () => execute(resendOtp));
    $('#otp-close').addEventListener('click', () => $('#otp-dialog').close());

    /** Dialog native giữ focus và cho phép Escape; xóa phản hồi của lần mở trước. */
    function openDialog(id) {
        closeSearchSuggestions();
        $$('dialog[open]').forEach((dialog) => dialog.close());
        const dialog = $('#' + id);
        $$('[data-dialog-notice]', dialog).forEach((element) => element.remove());
        if (id === 'cart-dialog') {
            prefillCheckoutFromProfile();
            execute(loadCheckoutProvinces);
        }
        dialog.showModal();
        syncMobilePurchase();
    }

    /** Đăng ký chỉ tạo khách hàng; backend không nhận role từ form công khai. */
    function setAuthMode(mode) {
        state.authMode = mode;
        const register = mode === 'register';
        $('#auth-name-field').hidden = !register;
        $('#auth-name').required = register;
        $('#auth-password').autocomplete = register ? 'new-password' : 'current-password';
        // Đăng ký theo DTO tối thiểu 6 ký tự; đăng nhập không chặn mật khẩu hợp lệ của tài khoản cũ.
        $('#auth-password').minLength = register ? 6 : 1;
        $('#auth-submit').textContent = register ? 'Tạo tài khoản mua hàng' : 'Đăng nhập';
        $('#auth-description').textContent = register
            ? 'Tài khoản mới dành cho khách mua sắm và theo dõi đơn hàng của mình.'
            : state.authIntent === 'portal'
              ? 'Dùng tài khoản nhân viên, quản lý hoặc admin để vào cổng vận hành.'
              : 'Đăng nhập để đặt hàng và theo dõi đơn của bạn.';
        $$('[data-auth-mode]').forEach((button) => setActive(button, button.dataset.authMode === mode));
        $('#auth-error').hidden = true;
    }

    /** Ý định mua hàng được giữ đến khi đăng nhập xong; không tự đặt đơn sau đăng nhập. */
    function openAuth(intent = 'login') {
        state.authIntent = intent;
        setAuthMode('login');
        openDialog('auth-dialog');
    }

    /** Hiển thị liên hệ bằng textContent/value; tên do người dùng nhập không được coi là HTML. */
    function renderProfile() {
        $('#profile-display-name').textContent = state.user?.full_name || 'Khách mua hàng';
        $('#profile-email').textContent = state.user?.email || '—';
        $('#profile-created').textContent = state.user?.created_at
            ? new Date(state.user.created_at).toLocaleDateString('vi-VN')
            : '—';
        $('#profile-name').value = state.user?.full_name || '';
        $('#profile-phone').value = state.user?.phone || '';
    }

    /** Tải lại hồ sơ thật; epoch của API loại bỏ phản hồi thuộc tài khoản cũ sau khi đổi vai trò. */
    async function loadProfile() {
        if (!hasRole('CUSTOMER') || state.profileSaving) return;
        const epoch = state.epoch;
        state.profileReady = false;
        renderProfile();
        $('#profile-status').textContent = 'Đang tải thông tin…';
        renderPermissions();
        try {
            state.user = await api('/users/me', { channel: 'profile-read' });
            state.profileReady = true;
            renderProfile();
            renderIdentity();
            $('#profile-status').textContent = '';
        } catch (error) {
            if (epoch === state.epoch && error.name !== 'AbortError') {
                $('#profile-status').textContent = 'Chưa tải được hồ sơ. Bấm Làm mới để thử lại.';
            }
            throw error;
        } finally {
            if (epoch === state.epoch) renderPermissions();
        }
    }

    /** Chỉ gửi liên hệ được phép; không đổi token, email hoặc vai trò và không tự sửa giỏ/đơn cũ. */
    async function saveProfile() {
        if (!hasRole('CUSTOMER') || !state.profileReady || state.profileSaving) return;
        const epoch = state.epoch;
        state.profileSaving = true;
        $('#profile-status').textContent = 'Đang lưu thông tin…';
        renderPermissions();
        try {
            state.user = await api('/users/me', {
                method: 'PATCH',
                body: { full_name: $('#profile-name').value, phone: $('#profile-phone').value },
                channel: 'profile-save',
            });
            renderProfile();
            renderIdentity();
            $('#profile-status').textContent = 'Đã lưu thông tin.';
            notify('success', 'Thông tin tài khoản đã được cập nhật.', 'HTTP 200 OK');
        } catch (error) {
            if (epoch === state.epoch && error.name !== 'AbortError') {
                $('#profile-status').textContent = 'Chưa lưu được. Kiểm tra thông tin và thử lại.';
            }
            throw error;
        } finally {
            if (epoch === state.epoch) {
                state.profileSaving = false;
                renderPermissions();
            }
        }
    }

    /** Chỉ điền ô trống khi mở giỏ; giữ nguyên người nhận mà khách đã nhập, kể cả khác chủ tài khoản. */
    function prefillCheckoutFromProfile() {
        if (!hasRole('CUSTOMER')) return;
        if (!$('#delivery-name').value.trim()) $('#delivery-name').value = state.user.full_name || '';
        if (!$('#delivery-phone').value.trim()) $('#delivery-phone').value = state.user.phone || '';
    }

    /** Dữ liệu công khai tách khỏi lựa chọn kho vận hành; không gọi báo cáo hoặc kho nội bộ cho khách. */
    async function loadReferences() {
        const operations = [loadCategories(), loadBrands(), loadBranches(), loadBestsellers()];
        if (isOperator()) operations.push(loadOperatingWarehouses(), loadProductOptions());
        const results = await Promise.allSettled(operations);
        const failure = results.find((result) => result.status === 'rejected' && result.reason.name !== 'AbortError');
        if (failure) handleError(failure.reason);
    }

    /** Danh mục được tái sử dụng cho kệ sản phẩm và các form chỉ dành cho admin. */
    async function loadCategories() {
        state.categories = await api('/categories', { anonymous: true, channel: 'categories' });
        const options = orderedCategories()
            .map(
                (category) => '<option value="' + category.id + '">' + escapeHtml(categoryPath(category)) + '</option>',
            )
            .join('');
        ['catalog-category', 'manage-category', 'product-category'].forEach((id) => {
            const select = $('#' + id);
            const previous = select.value;
            select.innerHTML =
                '<option value="">' +
                (id === 'product-category' ? 'Chọn danh mục' : 'Tất cả danh mục') +
                '</option>' +
                options;
            select.value = previous;
        });
        renderCategoryChips();
        const brandCategory = $('#brand-category');
        const previousBrandCategory = brandCategory.value;
        brandCategory.innerHTML = '<option value="">Chưa chọn</option>' + options;
        brandCategory.value = previousBrandCategory;
        const parentSelect = $('#category-parent');
        const previousParent = parentSelect.value;
        parentSelect.innerHTML =
            '<option value="">Không có — danh mục cấp đầu</option>' +
            orderedCategories()
                .filter((category) => categoryAncestors(category).length < 3)
                .map(
                    (category) =>
                        '<option value="' + category.id + '">' + escapeHtml(categoryPath(category)) + '</option>',
                )
                .join('');
        parentSelect.value = previousParent;
        renderCatalogBrandOptions();
        renderShopCategoryMenu();
        renderAdminBrands();
        renderDiscoveryCategories();
    }

    /** Dùng ngành hàng thực tế trong catalog, không hardcode ID hoặc tạo đường dẫn tới danh mục chưa có. */
    function renderDiscoveryCategories() {
        const groups = [
            {
                keys: ['dien thoai', 'smartphone'],
                label: 'Điện thoại',
                symbol: 'phone',
                description: 'Kết nối mỗi ngày',
            },
            {
                keys: ['laptop', 'may tinh xach tay'],
                label: 'Laptop',
                symbol: 'laptop',
                description: 'Học tập và làm việc',
            },
            {
                keys: ['am thanh', 'tai nghe'],
                label: 'Âm thanh',
                symbol: 'headphones',
                description: 'Nghe theo cách bạn thích',
            },
            { keys: ['phu kien'], label: 'Phụ kiện', symbol: 'keyboard', description: 'Hoàn thiện bộ thiết bị' },
        ];
        const symbols = {
            phone: '<rect x="7" y="2" width="10" height="20" rx="2"/><path d="M10 5h4m-3 14h2"/>',
            laptop: '<rect x="4" y="4" width="16" height="12" rx="2"/><path d="m4 16-2 4h20l-2-4M10 19h4"/>',
            headphones:
                '<path d="M4 13v-2a8 8 0 0 1 16 0v2"/><rect x="3" y="11" width="4" height="9" rx="2"/><rect x="17" y="11" width="4" height="9" rx="2"/>',
            keyboard:
                '<rect x="2" y="5" width="20" height="14" rx="2"/><path d="M5 9h1m3 0h1m3 0h1m3 0h1M5 13h1m3 0h1m3 0h1m3 0h1M7 16h10"/>',
        };
        const used = new Set();
        // Ưu tiên nhóm cấp đầu (ví dụ Âm thanh, Mic thu âm) trước danh mục con Tai nghe.
        const categories = [...state.categories].sort(
            (left, right) => Boolean(left.parent_id) - Boolean(right.parent_id),
        );
        $('#discovery-categories').innerHTML = groups
            .map((group) => {
                const category = categories.find(
                    (item) =>
                        !used.has(item.id) &&
                        group.keys.some((key) => {
                            const name = normalizeProductName(item.name);
                            return name === key || name.startsWith(key + ' ');
                        }),
                );
                if (!category) return '';
                used.add(category.id);
                return `<a class="discovery-category" href="/?categoryId=${category.id}#shop/catalog" data-catalog-link>
                <span class="discovery-category-art"><svg viewBox="0 0 24 24" aria-hidden="true">${symbols[group.symbol]}</svg></span>
                <span><strong>${escapeHtml(category.name)}</strong><small>${group.description}</small></span>
                ${icon('arrow')}
            </a>`;
            })
            .join('');
        $('.category-discovery').hidden = !used.size;
    }

    /** Tải bảng xếp hạng công khai riêng; lỗi mục gợi ý không làm hỏng kệ sản phẩm hoặc giỏ hàng. */
    async function loadBestsellers() {
        const grid = $('#bestseller-grid');
        grid.setAttribute('aria-busy', 'true');
        grid.innerHTML =
            Array.from(
                { length: 4 },
                () => `<div class="product-skeleton" aria-hidden="true">
            <div class="skeleton skeleton-card-image"></div><div class="skeleton-card-body">
            <span class="skeleton skeleton-line"></span><span class="skeleton skeleton-card-price"></span>
            </div></div>`,
            ).join('') + '<span class="sr-only" role="status">Đang tải sản phẩm bán chạy…</span>';
        try {
            const products = await api('/storefront/bestsellers', {
                query: { limit: 4 },
                anonymous: true,
                channel: 'storefront-bestsellers',
            });
            if (!Array.isArray(products)) throw new Error('Dữ liệu bán chạy chưa sẵn sàng.');
            products.forEach((product) => state.products.set(product.id, product));
            grid.innerHTML =
                productCards(products, { bestseller: true }) ||
                '<div class="discovery-empty"><strong>Những lựa chọn yêu thích sẽ xuất hiện ở đây.</strong>' +
                    '<p>Khi cửa hàng có đơn đã thanh toán, sản phẩm bán chạy được cập nhật từ doanh số thực tế.</p></div>';
            grid.setAttribute('aria-busy', 'false');
            state.discoveryDirty = false;
            prepareStorefrontReveals();
        } catch (error) {
            if (error.name === 'AbortError') return;
            grid.setAttribute('aria-busy', 'false');
            grid.innerHTML =
                '<div class="discovery-empty"><strong>Chưa tải được sản phẩm bán chạy.</strong>' +
                '<p>Bạn vẫn có thể duyệt và đặt sản phẩm trên kệ bên dưới.</p>' +
                '<button class="button secondary small" type="button" data-action="refresh-bestsellers">Thử lại</button></div>';
        }
    }

    /** Hãng do backend trả; không gán hãng bằng cách dò tên sản phẩm hoặc hardcode bộ lọc ở client. */
    async function loadBrands() {
        state.brands = await api('/brands', { anonymous: true, channel: 'brands' });
        renderProductBrands();
        renderCatalogBrandOptions();
        renderShopCategoryMenuDetail();
        renderAdminBrands();
    }

    /** Form ADMIN dùng toàn bộ hãng; một hãng có thể được dùng cho điện thoại và laptop. */
    function renderProductBrands() {
        const options = state.brands
            .map((brand) => '<option value="' + brand.id + '">' + escapeHtml(brand.name) + '</option>')
            .join('');
        ['product-brand', 'update-product-brand'].forEach((id) => {
            const select = $('#' + id);
            const previous = select.value;
            select.innerHTML = '<option value="">Chưa khai báo thương hiệu</option>' + options;
            select.value = previous;
        });
    }

    /** Chỉ đưa hãng thuộc danh mục đang xem vào bộ lọc; không hiển thị lựa chọn giả. */
    function renderCatalogBrandOptions() {
        const categoryId = $('#catalog-category').value;
        const brands = state.brands.filter((brand) => !categoryId || brand.category_ids.includes(Number(categoryId)));
        if (!brands.some((brand) => String(brand.id) === state.catalogBrandId)) state.catalogBrandId = '';
        $('#catalog-brand').innerHTML =
            '<option value="">Tất cả thương hiệu</option>' +
            brands.map((brand) => '<option value="' + brand.id + '">' + escapeHtml(brand.name) + '</option>').join('');
        $('#catalog-brand').value = state.catalogBrandId;
        renderCatalogBrandChips(brands);
    }

    /** Chip dùng danh sách hãng hợp lệ của select; giữ nút để bàn phím không mất focus khi đổi hãng. */
    function renderCatalogBrandChips(brands) {
        const container = $('#catalog-brand-chips');
        const signature = JSON.stringify(brands.map((brand) => [brand.id, brand.name]));
        if (container.dataset.brands !== signature) {
            const choices = [{ id: '', name: 'Tất cả' }, ...brands];
            container.innerHTML = choices
                .map(
                    (
                        brand,
                    ) => `<button type="button" data-action="quick-brand" data-brand-chip="${escapeHtml(brand.id)}"
                        aria-pressed="false">${escapeHtml(brand.name)}</button>`,
                )
                .join('');
            container.dataset.brands = signature;
        }
        $$('[data-brand-chip]', container).forEach((button) => {
            const selected = button.dataset.brandChip === state.catalogBrandId;
            button.setAttribute('aria-pressed', String(selected));
            // Chỉ cuộn ngang dải hãng, không kéo trang về kệ khi tải lại tham chiếu.
            if (selected && container.clientWidth > 0) {
                const bounds = button.getBoundingClientRect();
                const viewport = container.getBoundingClientRect();
                if (bounds.left < viewport.left) container.scrollLeft -= viewport.left - bounds.left;
                else if (bounds.right > viewport.right) container.scrollLeft += bounds.right - viewport.right;
            }
        });
    }

    /** Chọn nhanh và dropdown dùng chung bộ lọc; giữ giá/từ khóa/sắp xếp và về trang đầu. */
    async function applyCatalogBrand(id) {
        if (![...$('#catalog-brand').options].some((option) => option.value === id)) return;
        state.catalogBrandId = id;
        renderCatalogBrandOptions();
        await reloadCatalogFilters();
    }

    /** Logo dùng URL đã lưu; nếu chưa có/tải lỗi thì tên hãng và chữ viết tắt vẫn giúp nhận diện. */
    function brandLogoMarkup(brand) {
        const source = safeProductImageUrl(brand.logo_url);
        const initials = String(brand.name).trim().slice(0, 2).toLocaleUpperCase('vi-VN');
        return `<span class="brand-logo-media ${source ? '' : 'without-logo'}" aria-hidden="true">
            ${source ? `<img src="${escapeHtml(source)}" alt="" loading="lazy" decoding="async" data-brand-logo />` : ''}
            <span class="brand-monogram" ${source ? 'hidden' : ''}>${escapeHtml(initials)}</span>
        </span>`;
    }

    /** ADMIN tìm hãng và sửa logo; chỉ tóm tắt các nhánh gốc để danh sách không kéo dài hàng trăm dòng. */
    function renderAdminBrands() {
        const query = slugify($('#brand-admin-query').value.trim());
        const visible = state.brands.filter((brand) => slugify(brand.name + ' ' + brand.slug).includes(query));
        $('#brand-count').textContent = integer(visible.length) + ' / ' + integer(state.brands.length) + ' thương hiệu';
        $('#brand-admin-list').innerHTML = visible.length
            ? visible
                  .map((brand) => {
                      const names = rootCategories()
                          .filter((category) => brand.category_ids.includes(category.id))
                          .map((category) => category.name);
                      return `<article class="brand-admin-item" data-admin-brand="${brand.id}">
                    ${brandLogoMarkup(brand)}
                    <strong>${escapeHtml(brand.name)}</strong>
                    <span class="brand-admin-slug">${escapeHtml(brand.slug)}</span>
                    <span class="brand-admin-groups">${escapeHtml(names.join(' · ') || 'Chưa có danh mục gợi ý')}</span>
                    ${
                        hasRole('ADMIN')
                            ? `<button class="button secondary" type="button" data-action="edit-brand-logo"
                        data-id="${brand.id}" aria-label="Sửa logo của ${escapeHtml(brand.name)}">${icon('edit')}Sửa logo</button>`
                            : ''
                    }
                </article>`;
                  })
                  .join('')
            : '<p class="category-menu-message">Không tìm thấy thương hiệu phù hợp.</p>';
    }

    /** Chip và select cùng một bộ lọc để không sinh hai trạng thái danh mục khác nhau. */
    function renderCategoryChips() {
        const selected = $('#catalog-category').value;
        const selectedCategory = state.categories.find((category) => String(category.id) === selected);
        const activeRoot = selectedCategory ? categoryAncestors(selectedCategory).at(-1) : null;
        $('#category-chips').innerHTML = [{ id: '', name: 'Tất cả' }, ...rootCategories()]
            .map(
                (category) =>
                    '<button type="button" data-category="' +
                    category.id +
                    '" class="' +
                    (String(category.id) === selected || category.id === activeRoot?.id ? 'active' : '') +
                    '" aria-pressed="' +
                    String(String(category.id) === selected || category.id === activeRoot?.id) +
                    '">' +
                    escapeHtml(category.name) +
                    '</button>',
            )
            .join('');
        renderHomeCategoryState();
    }

    /** Theo chuỗi cha có visited để dữ liệu ngoài API bị sai không khiến trình duyệt lặp vô hạn. */
    function categoryAncestors(category) {
        const result = [];
        const visited = new Set();
        for (let current = category; current && !visited.has(current.id);) {
            result.push(current);
            visited.add(current.id);
            current = state.categories.find((item) => item.id === current.parent_id);
        }
        return result;
    }

    /** Đường dẫn đầy đủ giúp ADMIN chọn đúng nhóm con và không nhầm hãng với danh mục. */
    function categoryPath(category) {
        return categoryAncestors(category)
            .reverse()
            .map((item) => item.name)
            .join(' › ');
    }

    /** Nhóm chính ưu tiên theo menu cửa hàng; danh mục nhập tay chưa có cha vẫn xuất hiện bình thường. */
    function rootCategories() {
        const order = [
            'dien-thoai',
            'may-tinh-bang',
            'laptop',
            'am-thanh-mic-thu-am',
            'dong-ho-camera',
            'do-gia-dung-lam-dep',
            'phu-kien',
            'pc-man-hinh-may-in',
            'tivi-dien-may',
            'hang-cu',
        ];
        const rank = (category) => (order.includes(category.slug) ? order.indexOf(category.slug) : order.length);
        return state.categories
            .filter((category) => !category.parent_id)
            .sort((first, second) => rank(first) - rank(second) || first.id - second.id);
    }

    /** Thứ tự DFS đặt nhóm con ngay sau cha; không tạo thêm danh mục hoặc sửa ID ở client. */
    function orderedCategories() {
        const ordered = [];
        const seen = new Set();
        function visit(category) {
            if (seen.has(category.id)) return;
            seen.add(category.id);
            ordered.push(category);
            state.categories.filter((item) => item.parent_id === category.id).forEach(visit);
        }
        rootCategories().forEach(visit);
        state.categories.forEach(visit);
        return ordered;
    }

    /** Kiểm tra cùng nhánh để đếm sản phẩm nhóm lớn theo cùng quy tắc backend. */
    function categoryBelongsTo(categoryId, parentId) {
        const category = state.categories.find((item) => item.id === categoryId);
        return Boolean(category && categoryAncestors(category).some((item) => item.id === parentId));
    }

    /** Laptop và âm thanh có phân khúc riêng; các nhóm khác dùng bộ giá tổng quát hiện có. */
    function categoryPriceRanges(category) {
        const root = category ? categoryAncestors(category).at(-1) : null;
        if (root?.slug === 'laptop') return LAPTOP_PRICE_RANGES;
        if (root?.slug === 'am-thanh-mic-thu-am') return AUDIO_PRICE_RANGES;
        return CATALOG_PRICE_RANGES;
    }

    /** Tên/slug chỉ chọn biểu tượng trình bày; danh mục và ID dùng để lọc vẫn do database quyết định. */
    function shopCategoryIcon(category) {
        const name = slugify(category?.name || '');
        if (/dien-thoai|tablet|iphone/.test(name)) return 'device';
        if (/tai-nghe|loa|am-thanh|micro/.test(name)) return 'headphones';
        if (/laptop|may-tinh/.test(name)) return 'laptop';
        if (/man-hinh|webcam/.test(name)) return 'monitor';
        if (/ban-phim|chuot/.test(name)) return 'keyboard';
        if (/cap|sac|hub|phu-kien/.test(name)) return 'plug';
        return 'grid';
    }

    /** Menu lấy toàn bộ danh mục đã lưu, kể cả danh mục mới tạo; không tự thêm nhóm hàng vào database. */
    function renderShopCategoryMenu() {
        // Cột trái chỉ chứa nhóm gốc; các cấp con nằm trong panel nên không dàn hàng chục nút cùng cấp.
        const groups = rootCategories();
        const categories = [{ id: '', name: 'Tất cả sản phẩm' }, ...groups];
        if (!categories.some((category) => String(category.id) === state.menuCategoryId)) state.menuCategoryId = '';
        $('#category-menu-list').innerHTML = categories
            .map(
                (category) => `
            <button type="button" class="category-menu-item" data-menu-category="${category.id}" data-action="browse-category">
                ${icon(shopCategoryIcon(category))}
                <span>${escapeHtml(category.name)}</span>
                ${icon('arrow')}
            </button>
        `,
            )
            .join('');
        $('#home-category-list').innerHTML = categories
            .map(
                (category) => `
            <button type="button" class="category-menu-item" data-menu-category="${category.id}"
                data-action="browse-category" aria-controls="home-category-detail" aria-expanded="false">
                ${icon(shopCategoryIcon(category))}
                <span>${escapeHtml(category.name)}</span>
                ${icon('arrow')}
            </button>`,
            )
            .join('');
        renderHomeCategoryState();
        renderShopCategoryMenuDetail();
    }

    /** Di chuột hoặc focus chỉ xem lựa chọn khoảng giá; chưa gửi request hay thay bộ lọc hiện hành. */
    function previewShopCategory(id) {
        if (state.menuCategoryId === id) return;
        state.menuCategoryId = id;
        renderShopCategoryMenuDetail();
    }

    /** Menu lấy nhóm con và hãng từ API; mỗi nút đều lọc theo khóa dữ liệu thật. */
    function renderShopCategoryMenuDetail() {
        $$('#category-menu-list [data-menu-category]').forEach((button) => {
            const active = button.dataset.menuCategory === state.menuCategoryId;
            button.classList.toggle('active', active);
            button.setAttribute('aria-pressed', String(active));
        });
        $('#category-menu-detail').innerHTML = categoryMenuContent(state.menuCategoryId);
        if (state.homeCategoryId !== null) {
            $('#home-category-detail').innerHTML = categoryMenuContent(state.homeCategoryId);
        }
    }

    /** Hai vị trí menu dùng cùng nhóm con, hãng và khoảng giá để không tạo hai cách lọc khác nhau. */
    function categoryMenuContent(categoryId) {
        const category = state.categories.find((item) => String(item.id) === categoryId);
        const childGroups = state.categories.filter((item) => item.parent_id === category?.id);
        return `
            <div class="menu-category-feature">
                <span class="menu-category-symbol" aria-hidden="true">${icon(shopCategoryIcon(category))}</span>
                <div>
                    <span class="eyebrow">KHÁM PHÁ CỬA HÀNG</span>
                    <h2>${escapeHtml(category?.name || 'Tất cả sản phẩm')}</h2>
                    <p>Chọn loại sản phẩm, thương hiệu và khoảng giá phù hợp.</p>
                </div>
            </div>
            ${childGroups.length ? renderMenuChildGroups(childGroups) : renderMenuBrands(category)}
            ${childGroups.length && category?.slug === 'do-gia-dung-lam-dep' ? renderMenuBrands(category) : ''}
            <div class="menu-price-heading">${icon('tag')}<h3>Chọn theo khoảng giá</h3></div>
            <div class="menu-price-grid">
                ${categoryPriceRanges(category)
                    .map(
                        (range) => `
                    <button type="button" data-action="browse-price-range" data-price-range="${range.key}"
                        data-menu-category="${categoryId}">
                        ${escapeHtml(range.label)}${icon('arrow')}
                    </button>
                `,
                    )
                    .join('')}
            </div>
            <button class="button primary menu-browse-button" type="button" data-action="browse-category" data-menu-category="${categoryId}">
                Xem sản phẩm${icon('arrow')}
            </button>
            <p class="menu-discovery-note">Nhóm lớn bao gồm các nhóm con. Danh mục chưa có sản phẩm sẽ hiển thị kết quả trống.</p>
        `;
    }

    /** Menu bên trái đánh dấu nhóm đang lọc; khi xem bảng con thì nhóm đang duyệt được nhấn mạnh. */
    function renderHomeCategoryState() {
        const selected = state.categories.find((category) => String(category.id) === $('#catalog-category').value);
        const selectedId = selected ? String(categoryAncestors(selected).at(-1).id) : '';
        $$('#home-category-list [data-menu-category]').forEach((button) => {
            const previewed = button.dataset.menuCategory === state.homeCategoryId;
            const chosen = button.dataset.menuCategory === selectedId;
            button.classList.toggle('active', state.homeCategoryId === null ? chosen : previewed);
            button.setAttribute('aria-pressed', String(chosen));
            button.setAttribute('aria-expanded', String(previewed));
        });
    }

    /** Rê chuột hoặc dùng bàn phím mở bảng bên phải; xem trước không thay giỏ hay gọi API sản phẩm. */
    function previewHomeCategory(id) {
        if (!homeCategoryDesktop.matches || state.view !== 'shop' || state.shopTab !== 'catalog') return;
        if (state.homeCategoryId === id) return;
        state.homeCategoryId = id;
        $('#home-category-detail').innerHTML = categoryMenuContent(id);
        $('#home-category-detail').hidden = false;
        $('#home-categories').classList.add('expanded');
        renderHomeCategoryState();
    }

    /** Đóng bảng con giữ menu chính hiện sẵn; Escape đưa focus về tiêu đề để không tự mở lại. */
    function closeHomeCategoryMenu({ restoreFocus = false } = {}) {
        state.homeCategoryId = null;
        $('#home-category-detail').hidden = true;
        $('#home-categories').classList.remove('expanded');
        renderHomeCategoryState();
        if (restoreFocus) $('#home-categories-title').focus({ preventScroll: true });
    }

    /** Cột loại hàng lọc đúng nhánh; không tìm gần đúng bằng chuỗi tên sản phẩm. */
    function renderMenuChildGroups(groups) {
        return `<div class="menu-catalog-columns">${groups
            .map((group) => {
                const children = state.categories.filter((item) => item.parent_id === group.id);
                return `<section class="menu-catalog-column" data-menu-group="${escapeHtml(group.slug)}">
                <button type="button" class="menu-column-title" data-action="browse-category" data-menu-category="${group.id}">
                    ${escapeHtml(group.name)}${icon('arrow')}
                </button>
                <div class="menu-subcategory-list">${children
                    .map(
                        (child) => `
                    <button type="button" class="menu-subcategory-button" data-action="browse-category" data-menu-category="${child.id}" data-category-slug="${escapeHtml(child.slug)}">
                        ${escapeHtml(child.name)}
                    </button>`,
                    )
                    .join('')}</div>
                ${categoryAncestors(group).at(-1)?.slug === 'do-gia-dung-lam-dep' ? '' : renderMenuBrands(group, true)}
            </section>`;
            })
            .join('')}</div>`;
    }

    /** Hãng theo nhánh; Apple là hãng chung, không tạo Mac/Watch thành các hãng trùng nhau. */
    function renderMenuBrands(category, compact = false) {
        const brands = state.brands.filter((brand) => category && brand.category_ids.includes(category.id));
        if (!brands.length) return '';
        const title =
            category.slug === 'dien-thoai'
                ? 'Hãng điện thoại'
                : category.slug === 'tai-nghe'
                  ? 'Hãng tai nghe'
                  : category.slug === 'loa'
                    ? 'Hãng loa'
                    : category.slug === 'dong-ho'
                      ? 'Hãng đồng hồ'
                      : category.slug === 'camera'
                        ? 'Hãng camera'
                        : category.slug === 'do-gia-dung-lam-dep'
                          ? 'Thương hiệu gia dụng'
                          : 'Thương hiệu';
        const appleLabel = ['dien-thoai', 'dien-thoai-cu'].includes(category.slug)
            ? 'iPhone'
            : ['laptop', 'laptop-cu', 'mac-cu'].includes(category.slug)
              ? 'MacBook'
              : ['dong-ho', 'dong-ho-thong-minh-cu'].includes(category.slug)
                ? 'Apple Watch'
                : category.slug === 'may-tinh-bang-cu'
                  ? 'iPad'
                  : category.slug === 'tai-nghe-cu'
                    ? 'AirPods'
                    : '';
        return `<div class="menu-brand-section ${compact ? 'is-compact' : ''}">
            <div class="menu-price-heading">${icon('device')}<h3>${escapeHtml(title)}</h3></div>
            <div class="menu-brand-grid">${brands
                .map(
                    (brand) => `
                <button class="menu-brand-button" type="button" data-action="browse-brand" data-menu-category="${category.id}" data-brand-id="${brand.id}" data-brand-slug="${escapeHtml(brand.slug)}">
                    ${brand.logo_url ? brandLogoMarkup(brand) : ''}
                    <span>${escapeHtml(brand.name)}</span>${brand.slug === 'apple' && appleLabel ? '<small>' + appleLabel + '</small>' : ''}
                </button>`,
                )
                .join('')}</div>
        </div>`;
    }

    /** Header dùng đúng menu cạnh banner; chỉ điện thoại và trang khác cần bảng xổ riêng. */
    function canUseHomeCategoryMenu() {
        return homeCategoryDesktop.matches && state.view === 'shop' && state.shopTab === 'catalog';
    }

    /** Khi ghim menu ngoài, cả nút header và ô danh mục đều thuộc cùng một vùng tương tác. */
    function isShopCategoryTarget(target) {
        return Boolean(
            target?.closest?.('.category-menu-anchor') ||
            (state.categoryMenuInline && target?.closest?.('#home-categories')),
        );
    }

    /** Giữ chiều cao banner và cho menu cuộn bên trong nếu màn hình laptop không đủ cao. */
    function fitHomeCategoryMenu() {
        const discovery = $('.shop-discovery');
        const sidebar = $('#home-categories');
        discovery.style.minHeight = discovery.getBoundingClientRect().height + 'px';
        discovery.style.setProperty(
            '--home-menu-max-height',
            Math.max(180, window.innerHeight - sidebar.getBoundingClientRect().top - 16) + 'px',
        );
    }

    /** Làm sáng đúng ô danh mục đang có; khóa phần nền nhưng vẫn cho thao tác menu trong main. */
    function setShopCategoryMenu(open, { restoreFocus = false } = {}) {
        const wasInline = state.categoryMenuInline;
        const inlineEligible = canUseHomeCategoryMenu();
        const inline = open && inlineEligible;
        if (inline && !wasInline) fitHomeCategoryMenu();
        state.categoryMenuOpen = open;
        state.categoryMenuInline = inline;
        $('#category-menu-panel').hidden = !open || inline;
        $('#category-menu-backdrop').hidden = !open;
        document.body.classList.toggle('category-menu-open', open);
        document.body.classList.toggle('category-menu-inline', inline);
        $('#home-categories').classList.toggle('pinned', inline);
        $('.shop-main').inert = open && !inline;
        $('.shop-nav').inert = open;
        // Không đặt inert trên main ở chế độ ngoài vì chính menu cần mở cũng nằm trong main.
        $$('#shop-catalog > :not(.shop-discovery), .shop-discovery > .shop-hero, .shop-footer').forEach((element) => {
            element.inert = inline;
        });
        $('#category-menu-toggle').setAttribute('aria-expanded', String(open));
        $('#category-menu-toggle').setAttribute(
            'aria-controls',
            inlineEligible ? 'home-category-detail' : 'category-menu-panel',
        );
        if (!inline) {
            $('.shop-discovery').style.removeProperty('min-height');
            $('.shop-discovery').style.removeProperty('--home-menu-max-height');
            if (wasInline || open) closeHomeCategoryMenu();
        }
        if (open) {
            const selected = state.categories.find((category) => String(category.id) === $('#catalog-category').value);
            state.menuCategoryId = selected ? String(categoryAncestors(selected).at(-1).id) : '';
            if (inline) previewHomeCategory(state.menuCategoryId);
            else renderShopCategoryMenuDetail();
        } else if (restoreFocus) $('#category-menu-toggle').focus({ preventScroll: true });
    }

    /** Menu bắt đầu lượt duyệt mới và bỏ từ khóa cũ; giỏ và chi nhánh hiện tại được giữ nguyên. */
    async function browseShopCategory(id, rangeKey = 'all', brandId = '') {
        const range = [...CATALOG_PRICE_RANGES, ...LAPTOP_PRICE_RANGES, ...AUDIO_PRICE_RANGES].find(
            (item) => item.key === rangeKey,
        );
        if (!range || (id && !state.categories.some((category) => String(category.id) === id))) return;
        $('#catalog-category').value = id;
        state.catalogBrandId = brandId;
        renderCatalogBrandOptions();
        $('#catalog-query').value = '';
        state.catalogMinPrice = range.min;
        state.catalogMaxPrice = range.max;
        syncCatalogPriceFields();
        setShopCategoryMenu(false);
        closeHomeCategoryMenu();
        await reloadCatalogFilters({ scroll: true });
    }

    /* Hai đầu mút giữ độ chính xác NUMERIC(12,2); “dưới/trên” không bao gồm giá đúng tại mốc. */
    const QUICK_PRICE_RANGES = {
        all: { min: '', max: '' },
        'under-500': { min: '', max: '499999.99' },
        '500-2000': { min: '500000', max: '2000000' },
        '2000-5000': { min: '2000000', max: '5000000' },
        'over-5000': { min: '5000000.01', max: '' },
    };
    /* Thanh kéo chọn theo bước 50.000đ; ô nhập vẫn giữ chính xác hai chữ số thập phân của API. */
    const PRICE_SLIDER_STEP = 50_000;
    const PRICE_SLIDER_DEFAULT_MAX = 50_000_000;
    const MAX_CATALOG_PRICE = 9_999_999_999.99;
    let priceSliderDrag = null;

    /** So sánh điều kiện giá theo giá trị số, nhưng phân biệt ô trống với một đầu mút cụ thể. */
    function sameCatalogPrice(current, expected) {
        return current === '' || expected === '' ? current === expected : Number(current) === Number(expected);
    }

    /** Trạng thái chip luôn phản ánh bộ lọc đã áp dụng, kể cả khi nhập giá tay hoặc quay lại URL cũ. */
    function syncQuickPriceChips() {
        $$('[data-price-chip]').forEach((button) => {
            const range = QUICK_PRICE_RANGES[button.dataset.priceChip];
            button.setAttribute(
                'aria-pressed',
                String(
                    sameCatalogPrice(state.catalogMinPrice, range.min) &&
                        sameCatalogPrice(state.catalogMaxPrice, range.max),
                ),
            );
        });
    }

    /** Lọc giá một chạm giữ nguyên danh mục, hãng và từ khóa; quay về trang đầu như form giá hiện có. */
    async function applyQuickPrice(key) {
        const range = QUICK_PRICE_RANGES[key];
        if (!range) return;
        state.catalogMinPrice = range.min;
        state.catalogMaxPrice = range.max;
        syncCatalogPriceFields();
        await reloadCatalogFilters();
    }

    /** Giá đang áp dụng tách khỏi bản nháp nhập tay để chuyển trang không vô tình áp dụng giá chưa gửi. */
    function syncCatalogPriceFields() {
        $('#catalog-min-price').value = state.catalogMinPrice;
        $('#catalog-max-price').value = state.catalogMaxPrice;
        validateCatalogPriceFields();
        syncQuickPriceChips();
        syncCatalogPriceSlider();
    }

    /**
     * Đồng bộ hình học và nhãn từ bản nháp; không làm tròn ô nhập hoặc tự áp giá đang gõ.
     * Thang kéo mở rộng khi nhập giá lớn; đầu trên trống luôn có nghĩa không giới hạn.
     */
    function syncCatalogPriceSlider({ keepDomain = false, dragging = false } = {}) {
        const minimum = $('#catalog-min-price');
        const maximum = $('#catalog-max-price');
        const low = $('#catalog-price-low');
        const high = $('#catalog-price-high');
        const minimumValue = Number.isFinite(minimum.valueAsNumber) ? Math.max(0, minimum.valueAsNumber) : 0;
        const maximumValue = Number.isFinite(maximum.valueAsNumber) ? Math.max(0, maximum.valueAsNumber) : null;
        const required = Math.min(MAX_CATALOG_PRICE, Math.max(minimumValue + PRICE_SLIDER_STEP, maximumValue || 0));
        const ceiling = keepDomain
            ? Number(high.max)
            : Math.max(PRICE_SLIDER_DEFAULT_MAX, Math.ceil(required / 5_000_000) * 5_000_000);
        const start = Math.min(minimumValue, ceiling);
        const end = maximumValue === null ? ceiling : Math.min(maximumValue, ceiling);
        low.max = high.max = String(ceiling);
        low.value = String(start);
        high.value = String(end);
        const container = $('#catalog-price-slider');
        container.style.setProperty('--price-start', (Math.min(start, end) / ceiling) * 100 + '%');
        container.style.setProperty('--price-end', (Math.max(start, end) / ceiling) * 100 + '%');
        $('#catalog-price-low-label').textContent = amount(minimumValue);
        $('#catalog-price-high-label').textContent = maximumValue === null ? 'Không giới hạn' : amount(maximumValue);
        low.setAttribute('aria-valuetext', 'Từ ' + amount(minimumValue));
        high.setAttribute('aria-valuetext', maximumValue === null ? 'Không giới hạn' : 'Đến ' + amount(maximumValue));
        const draft =
            !sameCatalogPrice(minimum.value, state.catalogMinPrice) ||
            !sameCatalogPrice(maximum.value, state.catalogMaxPrice);
        $('#catalog-price-hint').textContent = dragging
            ? 'Thả tay để áp dụng khoảng giá đã chọn.'
            : draft
              ? 'Bấm Áp dụng để lọc theo giá đã nhập.'
              : 'Kéo và thả để lọc. Mở Nhập giá chính xác để chọn giá tùy ý.';
    }

    /** Chỉ đổi đầu mút đang kéo; giữ nguyên phần thập phân hoặc đầu mút trống ở bên còn lại. */
    function updatePriceDraftFromSlider(id) {
        const minimum = $('#catalog-min-price');
        const maximum = $('#catalog-max-price');
        const ceiling = Number($('#catalog-price-high').max);
        if (id === 'catalog-price-low') {
            const upper = Number.isFinite(maximum.valueAsNumber) ? Math.max(0, maximum.valueAsNumber) : ceiling;
            const value = Math.min(Number($('#catalog-price-low').value), upper, MAX_CATALOG_PRICE);
            minimum.value = value === 0 ? '' : String(value);
        } else {
            const value = Number($('#catalog-price-high').value);
            const lower = Number.isFinite(minimum.valueAsNumber) ? Math.max(0, minimum.valueAsNumber) : 0;
            maximum.value = value >= ceiling ? '' : String(Math.min(MAX_CATALOG_PRICE, Math.max(value, lower)));
        }
        $('#catalog-price-slider').dataset.activeHandle = id;
        validateCatalogPriceFields();
        syncCatalogPriceSlider({ keepDomain: true, dragging: true });
    }

    /** Thả tay hoặc gửi form mới áp dụng; không gửi lặp cùng điều kiện đang tải/đã hiển thị, lỗi đọc vẫn thử lại được. */
    async function applyCatalogPriceDraft() {
        if (!validateCatalogPriceFields()) return;
        const minimum = $('#catalog-min-price').value;
        const maximum = $('#catalog-max-price').value;
        const unchanged =
            sameCatalogPrice(minimum, state.catalogMinPrice) && sameCatalogPrice(maximum, state.catalogMaxPrice);
        if (unchanged && $('#catalog-grid').dataset.catalogStatus !== 'error') {
            syncCatalogPriceFields();
            return;
        }
        state.catalogMinPrice = minimum;
        state.catalogMaxPrice = maximum;
        syncCatalogPriceFields();
        await reloadCatalogFilters();
    }

    /** HTML kiểm tra kiểu số/độ chính xác; điều kiện chéo báo rõ giá từ lớn hơn giá đến. */
    function validateCatalogPriceFields() {
        const minimum = $('#catalog-min-price');
        const maximum = $('#catalog-max-price');
        const invalid = minimum.value !== '' && maximum.value !== '' && Number(minimum.value) > Number(maximum.value);
        maximum.setCustomValidity(invalid ? 'Giá đến phải lớn hơn hoặc bằng giá từ.' : '');
        const fields = [minimum, maximum];
        const broken = fields.find((field) => !field.validity.valid);
        let message = invalid ? 'Giá đến phải lớn hơn hoặc bằng giá từ.' : '';
        if (broken?.validity.badInput) message = 'Vui lòng nhập giá hợp lệ.';
        else if (broken?.validity.rangeUnderflow) message = 'Giá không được âm.';
        else if (broken?.validity.rangeOverflow) message = 'Giá không vượt quá ' + amount(MAX_CATALOG_PRICE) + '.';
        else if (broken?.validity.stepMismatch) message = 'Giá chỉ có tối đa hai chữ số thập phân.';
        const error = $('#catalog-price-error');
        error.hidden = !message;
        error.textContent = message;
        // Mở ô nhập khi có lỗi để thông báo HTML có thể đưa focus đến trường cần sửa.
        if (broken) $('#catalog-price-details').open = true;
        fields.forEach((field) => field.setAttribute('aria-invalid', String(!field.validity.valid)));
        return $('#catalog-price-filter').checkValidity();
    }

    /** Bỏ lỗi chéo ngay khi người dùng sửa lại giá, tránh customValidity cũ chặn lần gửi hợp lệ. */
    document.addEventListener('input', (event) => {
        if (['catalog-min-price', 'catalog-max-price'].includes(event.target.id)) {
            validateCatalogPriceFields();
            syncCatalogPriceSlider();
        } else if (['catalog-price-low', 'catalog-price-high'].includes(event.target.id)) {
            updatePriceDraftFromSlider(event.target.id);
        }
    });

    /** Native range hỗ trợ Tab/phím mũi tên/Home/End; sự kiện change chỉ đọc catalog sau khi chọn xong. */
    document.addEventListener('change', (event) => {
        if (['catalog-price-low', 'catalog-price-high'].includes(event.target.id)) execute(applyCatalogPriceDraft);
    });

    /** Bấm/kéo trên nền thanh chọn đầu mút gần nhất; thumb vẫn dùng tương tác range gốc của trình duyệt. */
    function movePriceTrackPointer(event) {
        if (!priceSliderDrag || priceSliderDrag.pointerId !== event.pointerId) return;
        const box = $('#catalog-price-slider').getBoundingClientRect();
        const ratio = Math.max(0, Math.min(1, (event.clientX - box.left - 10) / (box.width - 20)));
        const value = Math.round((ratio * priceSliderDrag.ceiling) / PRICE_SLIDER_STEP) * PRICE_SLIDER_STEP;
        $('#' + priceSliderDrag.id).value = String(value);
        updatePriceDraftFromSlider(priceSliderDrag.id);
    }
    const priceSlider = $('#catalog-price-slider');
    priceSlider.addEventListener('pointerdown', (event) => {
        if (event.button !== 0 || event.isPrimary === false || event.target.matches('input')) return;
        const box = priceSlider.getBoundingClientRect();
        const ceiling = Number($('#catalog-price-high').max);
        const value = Math.max(0, Math.min(1, (event.clientX - box.left - 10) / (box.width - 20))) * ceiling;
        const low = Number($('#catalog-price-low').value);
        const high = Number($('#catalog-price-high').value);
        const useLow = low === high ? value < low : Math.abs(value - low) < Math.abs(value - high);
        priceSliderDrag = {
            id: useLow ? 'catalog-price-low' : 'catalog-price-high',
            pointerId: event.pointerId,
            ceiling,
            minimum: $('#catalog-min-price').value,
            maximum: $('#catalog-max-price').value,
        };
        event.preventDefault();
        priceSlider.setPointerCapture(event.pointerId);
        $('#' + priceSliderDrag.id).focus({ preventScroll: true });
        movePriceTrackPointer(event);
    });
    priceSlider.addEventListener('pointermove', movePriceTrackPointer);
    priceSlider.addEventListener('pointerup', (event) => {
        if (!priceSliderDrag || priceSliderDrag.pointerId !== event.pointerId) return;
        movePriceTrackPointer(event);
        priceSliderDrag = null;
        if (priceSlider.hasPointerCapture(event.pointerId)) priceSlider.releasePointerCapture(event.pointerId);
        execute(applyCatalogPriceDraft);
    });
    /** Hủy cử chỉ (ví dụ vuốt trang dọc) trả bản nháp về trước khi kéo và không gọi API. */
    function cancelPriceTrackPointer(event) {
        if (!priceSliderDrag || priceSliderDrag.pointerId !== event.pointerId) return;
        $('#catalog-min-price').value = priceSliderDrag.minimum;
        $('#catalog-max-price').value = priceSliderDrag.maximum;
        priceSliderDrag = null;
        validateCatalogPriceFields();
        syncCatalogPriceSlider();
    }
    priceSlider.addEventListener('pointercancel', cancelPriceTrackPointer);
    priceSlider.addEventListener('lostpointercapture', cancelPriceTrackPointer);

    /** Bất kỳ bộ lọc/thứ tự mới nào đều quay về trang đầu; điều hướng từ chi tiết vẫn dùng History API. */
    async function reloadCatalogFilters({ scroll = false } = {}) {
        closeSearchSuggestions();
        state.pages.catalog = 0;
        if (state.view !== 'shop' || state.shopTab !== 'catalog') await activateView('shop', 'catalog');
        else await loadCatalog();
        if (scroll) {
            $('#product-shelf').scrollIntoView({ block: 'start', behavior: 'auto' });
            $('#catalog-title').tabIndex = -1;
            $('#catalog-title').focus({ preventScroll: true });
        }
    }

    /** Xóa một điều kiện hoặc toàn bộ bộ lọc; không thay sản phẩm hay số lượng trong giỏ. */
    async function clearCatalogFilter(filter) {
        if (filter === 'all' || filter === 'category') $('#catalog-category').value = '';
        if (filter === 'all' || filter === 'category' || filter === 'brand') state.catalogBrandId = '';
        renderCatalogBrandOptions();
        if (filter === 'all' || filter === 'search') $('#catalog-query').value = '';
        if (filter === 'all' || filter === 'price') {
            state.catalogMinPrice = '';
            state.catalogMaxPrice = '';
            syncCatalogPriceFields();
        }
        if (filter === 'all' || filter === 'sort') {
            state.catalogSort = 'id,asc';
            $('#catalog-sort').value = state.catalogSort;
        }
        await reloadCatalogFilters();
    }

    /** Chip mô tả điều kiện thật gửi lên backend, giúp khách nhận biết vì sao kệ chỉ còn ít kết quả. */
    function renderCatalogActiveFilters() {
        const category = state.categories.find((item) => String(item.id) === $('#catalog-category').value);
        const filters = [];
        if (category) filters.push({ key: 'category', label: category.name });
        const brand = state.brands.find((item) => String(item.id) === state.catalogBrandId);
        if (brand) filters.push({ key: 'brand', label: 'Hãng: ' + brand.name });
        if ($('#catalog-query').value.trim())
            filters.push({ key: 'search', label: 'Tìm: ' + $('#catalog-query').value.trim() });
        if (state.catalogMinPrice !== '' || state.catalogMaxPrice !== '') {
            const label =
                state.catalogMinPrice !== '' && state.catalogMaxPrice !== ''
                    ? amount(state.catalogMinPrice) + ' – ' + amount(state.catalogMaxPrice)
                    : state.catalogMinPrice !== ''
                      ? 'Từ ' + amount(state.catalogMinPrice)
                      : 'Đến ' + amount(state.catalogMaxPrice);
            filters.push({ key: 'price', label });
        }
        if (state.catalogSort !== 'id,asc')
            filters.push({ key: 'sort', label: $('#catalog-sort').selectedOptions[0].textContent });
        const container = $('#catalog-active-filters');
        container.hidden = !filters.length;
        container.innerHTML =
            filters
                .map(
                    (filter) => `
            <button class="active-filter" type="button" data-action="clear-catalog-filter" data-filter="${filter.key}" aria-label="Bỏ bộ lọc ${escapeHtml(filter.label)}">
                ${escapeHtml(filter.label)}${icon('close')}
            </button>
        `,
                )
                .join('') +
            '<button class="text-button" type="button" data-action="clear-catalog-filter" data-filter="all">Xóa bộ lọc</button>';
        $('#catalog-title').textContent = category?.name || 'Sản phẩm & đặt hàng';
    }

    /** Bàn phím và chuột ngoài menu đều đóng được, không dùng hành vi hover bắt buộc trên điện thoại. */
    document.addEventListener('pointerover', (event) => {
        const button = event.target.closest('#category-menu-list [data-menu-category]');
        if (button && state.categoryMenuOpen && event.pointerType !== 'touch')
            previewShopCategory(button.dataset.menuCategory);
        const homeButton = event.target.closest('#home-category-list [data-menu-category]');
        if (homeButton && event.pointerType !== 'touch') previewHomeCategory(homeButton.dataset.menuCategory);
    });
    /** Cầu nối trong CSS giữ bảng con mở khi con trỏ đi qua khoảng cách giữa hai cột. */
    document.addEventListener('pointerout', (event) => {
        if (
            !state.categoryMenuInline &&
            state.homeCategoryId !== null &&
            event.target.closest('#home-categories') &&
            !event.relatedTarget?.closest('#home-categories')
        )
            closeHomeCategoryMenu();
    });
    document.addEventListener('focusin', (event) => {
        const button = event.target.closest('#category-menu-list [data-menu-category]');
        if (button && state.categoryMenuOpen) previewShopCategory(button.dataset.menuCategory);
        const homeButton = event.target.closest('#home-category-list [data-menu-category]');
        if (homeButton) previewHomeCategory(homeButton.dataset.menuCategory);
    });
    document.addEventListener('focusout', (event) => {
        if (
            state.categoryMenuOpen &&
            isShopCategoryTarget(event.target) &&
            event.relatedTarget &&
            !isShopCategoryTarget(event.relatedTarget)
        ) {
            setShopCategoryMenu(false);
        }
        if (
            !state.categoryMenuInline &&
            state.homeCategoryId !== null &&
            event.target.closest('#home-categories') &&
            event.relatedTarget &&
            !event.relatedTarget.closest('#home-categories')
        )
            closeHomeCategoryMenu();
    });
    document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && state.categoryMenuOpen) {
            event.preventDefault();
            setShopCategoryMenu(false, { restoreFocus: true });
        } else if (event.key === 'Escape' && state.homeCategoryId !== null) {
            event.preventDefault();
            closeHomeCategoryMenu({ restoreFocus: true });
        } else if (event.key === 'ArrowDown' && event.target.id === 'category-menu-toggle') {
            event.preventDefault();
            setShopCategoryMenu(true);
            $(state.categoryMenuInline ? '#home-category-list .active' : '#category-menu-list .active')?.focus();
        } else if (
            ['ArrowDown', 'ArrowUp'].includes(event.key) &&
            event.target.matches('#category-menu-list button, #home-category-list button')
        ) {
            event.preventDefault();
            const buttons = $$('button', event.target.closest('nav'));
            buttons[
                (buttons.indexOf(event.target) + (event.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length
            ].focus();
        }
    });

    /** Chi nhánh công khai dùng ID thực và chỉ ACTIVE; khách vãng lai chọn được trước khi đăng nhập. */
    async function loadBranches() {
        state.warehouses = await api('/storefront/branches', { anonymous: true, channel: 'branches' });
        if (!state.warehouses.some((warehouse) => String(warehouse.id) === state.branchId)) {
            state.branchId = state.warehouses.length ? String(state.warehouses[0].id) : '';
        }
        renderWarehouses();
        saveCart();
    }

    /** Phạm vi staff lấy từ phân công database, không suy đoán bằng email demo hay ID kho mặc định. */
    async function loadOperatingWarehouses() {
        state.operatingWarehouses = await api('/warehouses/operating-options', { channel: 'operating-warehouses' });
        renderWarehouses();
        renderPermissions();
    }

    /** Bộ chọn chi nhánh cửa hàng đồng bộ với giỏ; bộ chọn vận hành dùng danh sách theo quyền riêng. */
    function renderWarehouses() {
        const branchOptions = state.warehouses
            .map((warehouse) => '<option value="' + warehouse.id + '">' + escapeHtml(warehouse.name) + '</option>')
            .join('');
        $$('[data-store-warehouse]').forEach((select) => {
            select.innerHTML = branchOptions || '<option value="">Chưa có chi nhánh đang phục vụ</option>';
            select.value = state.branchId;
            select.disabled = !state.warehouses.length;
        });
        const options = state.operatingWarehouses
            .map(
                (warehouse) =>
                    '<option value="' +
                    warehouse.id +
                    '">' +
                    escapeHtml(warehouse.name + ' · ' + warehouse.code) +
                    '</option>',
            )
            .join('');
        $$('[data-warehouse]').forEach((select) => {
            const previous = select.value;
            const staffInventory = hasRole('WAREHOUSE_STAFF') && select.id === 'inventory-warehouse';
            const all = select.dataset.warehouse === 'all' && !staffInventory;
            const caption = state.operatingWarehouses.length
                ? all
                    ? hasRole('WAREHOUSE_STAFF')
                        ? 'Tất cả kho được phân công'
                        : 'Tất cả kho'
                    : 'Chọn kho'
                : 'Chưa có kho được phép';
            select.innerHTML = (staffInventory && options ? '' : '<option value="">' + caption + '</option>') + options;
            if (state.operatingWarehouses.some((warehouse) => String(warehouse.id) === previous))
                select.value = previous;
            else select.value = !all && state.operatingWarehouses.length ? String(state.operatingWarehouses[0].id) : '';
            select.required = staffInventory || select.dataset.warehouse === 'required';
        });
    }

    /** Form vận hành nạp lựa chọn theo trang ID ổn định; storefront chỉ nạp trang sản phẩm đang xem. */
    async function loadProductOptions() {
        let page = 0;
        let result;
        const products = new Map();
        do {
            result = await api('/products', {
                anonymous: true,
                channel: 'product-options',
                query: { page, size: 100, sort: 'id,asc' },
            });
            result.content.forEach((product) => products.set(product.id, product));
            page++;
        } while (!result.last);
        state.products = products;
        renderProductOptions();
        renderCart();
    }

    /** Tên/SKU backend đều được escape trước khi dựng lựa chọn sản phẩm. */
    function renderProductOptions() {
        const options = [...state.products.values()]
            .map(
                (product) =>
                    '<option value="' +
                    product.id +
                    '">' +
                    escapeHtml(product.sku + ' · ' + cartProductName(saleSku(product))) +
                    '</option>',
            )
            .join('');
        $$('[data-product]').forEach((select) => {
            const previous = select.value;
            select.innerHTML =
                '<option value="">' +
                (select.dataset.product === 'all' ? 'Tất cả sản phẩm' : 'Chọn sản phẩm') +
                '</option>' +
                options;
            if (state.products.has(Number(previous))) select.value = previous;
        });
    }

    /** Tên chi nhánh lịch sử ưu tiên dữ liệu vận hành để vẫn đọc được kho đã ngừng phục vụ. */
    function warehouseName(id) {
        return (
            [...state.operatingWarehouses, ...state.warehouses].find((warehouse) => warehouse.id === id)?.name ||
            'Kho #' + id
        );
    }

    /** Bỏ dấu để tên sản phẩm tiếng Việt và các tên thương mại dùng chung bảng tra cứu ảnh. */
    function normalizeProductName(value) {
        return String(value ?? '')
            .toLowerCase()
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .replace(/đ/g, 'd')
            .replace(/[^a-z0-9]+/g, ' ')
            .trim();
    }

    /** Chỉ nhận ảnh HTTP/HTTPS hoặc assets cùng ứng dụng; dữ liệu lạ từ API không được đưa vào src. */
    function safeProductImageUrl(value) {
        const source = String(value ?? '').trim();
        if (!source || /[\s\\\u0000-\u001f\u007f]/.test(source)) return null;
        try {
            if (source.startsWith('/assets/')) {
                const path = decodeURIComponent(source.split(/[?#]/)[0]);
                if (path.includes('\\') || path.split('/').some((segment) => segment === '.' || segment === '..')) {
                    return null;
                }
                return source;
            }
            const url = new URL(source);
            return ['http:', 'https:'].includes(url.protocol) && url.hostname && !url.username && !url.password
                ? source
                : null;
        } catch {
            return null;
        }
    }

    /** Ưu tiên ảnh đã lưu cùng sản phẩm; bảng tên/danh mục chỉ phục vụ sản phẩm cũ chưa có ảnh. */
    function productImage(product) {
        const name = normalizeProductName(product.name);
        const rule = PRODUCT_IMAGE_RULES.find((entry) => entry.names.some((alias) => name.includes(alias)));
        const savedImage =
            safeProductImageUrl(product.image_url) ||
            (Array.isArray(product.image_urls) ? product.image_urls.map(safeProductImageUrl).find(Boolean) : null);
        return {
            src:
                savedImage ||
                (rule ? PRODUCT_IMAGES[rule.image] : CATEGORY_IMAGES[product.category_name] || PRODUCT_IMAGES.macbook),
            fallback: CATEGORY_IMAGES[product.category_name] || PRODUCT_IMAGES.macbook,
            custom: Boolean(savedImage),
        };
    }

    /** Đóng và hủy cả debounce/request; phản hồi đến muộn không được tự mở lại danh sách. */
    function closeSearchSuggestions() {
        searchSuggestions.version++;
        window.clearTimeout(searchSuggestions.timer);
        window.clearTimeout(searchSuggestions.timeout);
        searchSuggestions.timer = null;
        searchSuggestions.timeout = null;
        searchSuggestions.items = [];
        searchSuggestions.active = -1;
        channels.get('search-suggestions')?.abort();
        channels.delete('search-suggestions');
        $('#search-suggestions').hidden = true;
        $('#search-suggestions-list').replaceChildren();
        $('#search-suggestions-list').removeAttribute('aria-busy');
        $('#catalog-query').setAttribute('aria-expanded', 'false');
        $('#catalog-query').removeAttribute('aria-activedescendant');
        $('#search-suggestions-status').textContent = '';
    }

    /** Chỉ tìm khi đang dùng ô tìm kiếm của cửa hàng; không chạy trong dialog hay lúc đổi tài khoản. */
    function canSuggestProducts() {
        return (
            state.view === 'shop' &&
            !state.authBusy &&
            !searchSuggestions.composing &&
            $('#catalog-filter').contains(document.activeElement) &&
            !document.querySelector('dialog[open]')
        );
    }

    /** Chừa chỗ cho footer khi màn hình thấp hoặc bàn phím mobile mở; chỉ danh sách bên trong được cuộn. */
    function updateSearchSuggestionsLayout() {
        const popup = $('#search-suggestions');
        if (popup.hidden) return;
        const viewport = window.visualViewport;
        const viewportTop = viewport?.offsetTop || 0;
        const viewportBottom = viewportTop + (viewport?.height || window.innerHeight);
        const inputBounds = $('#catalog-query').getBoundingClientRect();
        if (inputBounds.bottom < viewportTop || inputBounds.top > viewportBottom) {
            closeSearchSuggestions();
            return;
        }
        const controlsHeight =
            $('.search-suggestions-heading', popup).offsetHeight + $('.search-suggestions-all', popup).offsetHeight;
        const availableHeight = Math.max(0, viewportBottom - popup.getBoundingClientRect().top - controlsHeight - 14);
        popup.style.setProperty('--search-list-height', Math.min(480, availableHeight) + 'px');
    }

    /** Skeleton gọn cho danh sách; footer dùng textContent để từ khóa không thể chèn HTML. */
    function renderSearchSuggestionsLoading(query) {
        $('#search-suggestions').hidden = false;
        $('#catalog-query').setAttribute('aria-expanded', 'true');
        $('#search-suggestions-feedback').hidden = true;
        $('#search-suggestions-all-label').textContent = 'Tìm tất cả kết quả cho “' + query + '”';
        const list = $('#search-suggestions-list');
        list.setAttribute('aria-busy', 'true');
        list.innerHTML = Array.from(
            { length: 3 },
            () => `
                <div class="search-suggestion-skeleton" aria-hidden="true">
                    <span class="skeleton search-skeleton-image"></span>
                    <span class="search-skeleton-lines">
                        <span class="skeleton skeleton-line"></span>
                        <span class="skeleton skeleton-line short"></span>
                    </span>
                </div>
            `,
        ).join('');
        $('#search-suggestions-status').textContent = 'Đang tìm sản phẩm…';
        updateSearchSuggestionsLayout();
    }

    /** Tối đa sáu model ACTIVE, ảnh/giá từ dữ liệu thật; tên/SKU/hãng luôn được escape. */
    function renderSearchSuggestions(products) {
        searchSuggestions.items = products;
        const list = $('#search-suggestions-list');
        list.removeAttribute('aria-busy');
        list.innerHTML = products
            .map((product, index) => {
                const photo = productImage(product);
                const price = product.min_price ?? product.unit_price;
                const fromPrice = Number(product.max_price ?? price) > Number(price) ? 'Từ ' : '';
                return `
                    <a
                        id="search-suggestion-${index}"
                        class="search-suggestion"
                        href="/san-pham/${product.id}"
                        data-product-link
                        data-product-id="${product.id}"
                        role="option"
                        aria-selected="false"
                        tabindex="-1"
                    >
                        <span class="search-suggestion-photo">
                            ${icon('box')}
                            <img src="${escapeHtml(photo.src)}" alt="" decoding="async" data-search-image />
                        </span>
                        <span class="search-suggestion-content">
                            <strong class="search-suggestion-name">${escapeHtml(product.name)}</strong>
                            <span class="search-suggestion-meta">
                                ${escapeHtml(product.brand_name || product.category_name || '')} · ${escapeHtml(product.sku)}
                            </span>
                            <span class="search-suggestion-price">${fromPrice}${amount(price)}</span>
                        </span>
                        ${icon('arrow')}
                    </a>
                `;
            })
            .join('');
        const feedback = $('#search-suggestions-feedback');
        feedback.hidden = products.length > 0;
        feedback.textContent = 'Chưa tìm thấy sản phẩm phù hợp. Thử tên hoặc mã SKU khác.';
        $('#search-suggestions-status').textContent = products.length
            ? 'Có ' + products.length + ' gợi ý. Dùng phím lên xuống để chọn, Enter để xem chi tiết.'
            : feedback.textContent;
        updateSearchSuggestionsLayout();
    }

    /** Debounce tránh gọi DB sau từng phím; giữ nguyên toàn bộ bộ lọc cho lần tìm trên kệ. */
    function queueSearchSuggestions({ immediate = false } = {}) {
        closeSearchSuggestions();
        const query = $('#catalog-query').value.trim();
        if (!query || !canSuggestProducts()) return;
        if (state.categoryMenuOpen) setShopCategoryMenu(false);
        closeHomeCategoryMenu();
        const version = searchSuggestions.version;
        renderSearchSuggestionsLoading(query);
        searchSuggestions.timer = window.setTimeout(
            () => loadSearchSuggestions(query, version),
            immediate ? 0 : SEARCH_SUGGESTION_DELAY_MS,
        );
    }

    /** GET công khai gộp model, độc lập danh mục/giá đang lọc; hủy phản hồi cũ và có hạn chờ mạng. */
    async function loadSearchSuggestions(query, version) {
        searchSuggestions.timer = null;
        const current = () =>
            version === searchSuggestions.version &&
            query === $('#catalog-query').value.trim() &&
            canSuggestProducts() &&
            !$('#search-suggestions').hidden;
        if (!current()) return;
        let timedOut = false;
        const timeout = window.setTimeout(() => {
            if (!current()) return;
            timedOut = true;
            channels.get('search-suggestions')?.abort();
        }, READ_TIMEOUT_MS);
        searchSuggestions.timeout = timeout;
        try {
            const result = await api('/products', {
                anonymous: true,
                channel: 'search-suggestions',
                query: {
                    q: query,
                    status: 'ACTIVE',
                    grouped: true,
                    page: 0,
                    size: SEARCH_SUGGESTION_LIMIT,
                    sort: 'id,asc',
                },
            });
            if (!current()) return;
            renderSearchSuggestions(
                result.content
                    .filter(
                        (product) => Number.isSafeInteger(product.id) && product.id > 0 && product.status === 'ACTIVE',
                    )
                    .slice(0, SEARCH_SUGGESTION_LIMIT),
            );
        } catch (error) {
            if (!current() || (error.name === 'AbortError' && !timedOut)) return;
            // Lỗi gợi ý chỉ ở dropdown; không tạo toast hoặc thay kết quả catalog đang xem.
            $('#search-suggestions-list').replaceChildren();
            $('#search-suggestions-list').removeAttribute('aria-busy');
            const feedback = $('#search-suggestions-feedback');
            feedback.hidden = false;
            feedback.innerHTML = `
                <p>Chưa tải được gợi ý. Bạn vẫn có thể nhấn Enter để tìm trên kệ hàng.</p>
                <button type="button" class="button secondary small" data-action="retry-search-suggestions">Thử lại</button>
            `;
            $('#search-suggestions-status').textContent = 'Chưa tải được gợi ý. Thử lại hoặc nhấn Enter để tìm.';
            updateSearchSuggestionsLayout();
        } finally {
            window.clearTimeout(timeout);
            if (searchSuggestions.timeout === timeout) searchSuggestions.timeout = null;
        }
    }

    /** Focus vẫn ở combobox khi chọn bằng mũi tên; trình đọc màn hình nhận option đang chọn qua ID. */
    function selectSearchSuggestion(index) {
        const options = $$('#search-suggestions-list [role="option"]');
        if (!options.length) return;
        searchSuggestions.active = (index + options.length) % options.length;
        options.forEach((option, position) => {
            option.setAttribute('aria-selected', String(position === searchSuggestions.active));
        });
        const option = options[searchSuggestions.active];
        $('#catalog-query').setAttribute('aria-activedescendant', option.id);
        // Chỉ cuộn trong danh sách gợi ý, không kéo cả trang và làm ô tìm kiếm rời màn hình.
        const list = $('#search-suggestions-list');
        const rowBounds = option.getBoundingClientRect();
        const listBounds = list.getBoundingClientRect();
        if (rowBounds.top < listBounds.top) list.scrollTop += rowBounds.top - listBounds.top;
        else if (rowBounds.bottom > listBounds.bottom) list.scrollTop += rowBounds.bottom - listBounds.bottom;
    }

    /** Tôn trọng bộ gõ tiếng Việt/IME; không chọn nhầm hoặc gọi API giữa lúc đang ghép ký tự. */
    $('#catalog-query').addEventListener('compositionstart', () => {
        searchSuggestions.composing = true;
        closeSearchSuggestions();
    });
    $('#catalog-query').addEventListener('compositionend', () => {
        searchSuggestions.composing = false;
        queueSearchSuggestions();
    });
    $('#catalog-query').addEventListener('input', () => queueSearchSuggestions());
    $('#catalog-query').addEventListener('focus', () => {
        // Trở lại cửa sổ không tải lại một danh sách đang mở hoặc làm mất lựa chọn bằng bàn phím.
        if ($('#search-suggestions').hidden) queueSearchSuggestions();
    });
    $('#catalog-query').addEventListener('click', () => {
        if ($('#search-suggestions').hidden) queueSearchSuggestions();
    });
    $('#catalog-query').addEventListener('search', () => {
        if (!$('#catalog-query').value.trim()) closeSearchSuggestions();
    });
    $('#catalog-query').addEventListener('keydown', (event) => {
        if (event.isComposing || searchSuggestions.composing || event.keyCode === 229) return;
        if (event.key === 'Escape' && !$('#search-suggestions').hidden) {
            event.preventDefault();
            event.stopPropagation();
            closeSearchSuggestions();
        } else if (['ArrowDown', 'ArrowUp'].includes(event.key)) {
            if (!$('#catalog-query').value.trim()) return;
            event.preventDefault();
            if ($('#search-suggestions').hidden) queueSearchSuggestions({ immediate: true });
            else
                selectSearchSuggestion(
                    event.key === 'ArrowUp' && searchSuggestions.active < 0
                        ? searchSuggestions.items.length - 1
                        : searchSuggestions.active + (event.key === 'ArrowDown' ? 1 : -1),
                );
        } else if (event.key === 'Enter') {
            const option = $('#search-suggestion-' + searchSuggestions.active);
            if (option) {
                event.preventDefault();
                option.click();
            } else closeSearchSuggestions();
        }
    });

    /** Giữ focus khi bấm một option; click ngoài hoặc Tab ra ngoài đóng menu mà không chặn điều hướng native. */
    document.addEventListener('pointerdown', (event) => {
        if (!event.target.closest('#catalog-filter')) closeSearchSuggestions();
        else if (event.target.closest('.search-suggestion') && event.button === 0) event.preventDefault();
    });
    document.addEventListener('focusin', (event) => {
        if (!event.target.closest('#catalog-filter')) closeSearchSuggestions();
    });
    window.addEventListener('resize', updateSearchSuggestionsLayout, { passive: true });
    window.addEventListener('scroll', updateSearchSuggestionsLayout, { passive: true });
    window.visualViewport?.addEventListener('resize', updateSearchSuggestionsLayout, { passive: true });
    window.visualViewport?.addEventListener('scroll', updateSearchSuggestionsLayout, { passive: true });

    /** Ảnh bìa tải lười; phân biệt ảnh đã lưu với ảnh minh họa dự phòng, không thay đổi dữ liệu giỏ hàng. */
    function productArt(product) {
        const photo = productImage(product);
        return `
            <div class="product-card-img-wrap">
                <img
                    src="${escapeHtml(photo.src)}"
                    alt="${escapeHtml(product.name)} — ${photo.custom ? 'ảnh sản phẩm' : 'ảnh minh họa'}"
                    loading="lazy"
                    decoding="async"
                    class="product-card-img"
                    data-image-fallback="${escapeHtml(photo.fallback)}"
                />
                ${product.brand_name ? '<span class="product-tag">' + escapeHtml(product.brand_name) + '</span>' : ''}
                <span class="product-photo-caption" ${photo.custom ? 'hidden' : ''}>Ảnh minh họa</span>
                <span class="product-image-error" hidden>Chưa tải được ảnh. Vui lòng kiểm tra kết nối.</span>
            </div>
        `;
    }

    /**
     * Trưng bày tối đa ba model từ trang catalog đã tải, ưu tiên ngành hàng khác nhau.
     * Dùng ảnh đã lưu và giá thật; không gọi thêm API, không tự gán bán chạy hoặc ưu đãi.
     */
    function renderHeroShowcase(products, { failed = false } = {}) {
        const container = $('#hero-showcase');
        const candidates = products.filter(
            (product) => product.status === 'ACTIVE' && Number.isSafeInteger(Number(product.id)),
        );
        const selected = [];
        const categories = new Set();
        candidates.forEach((product) => {
            if (selected.length < 3 && !categories.has(product.category_id)) {
                selected.push(product);
                categories.add(product.category_id);
            }
        });
        candidates.forEach((product) => {
            if (selected.length < 3 && !selected.some((value) => value.id === product.id)) selected.push(product);
        });
        // Chỉ thay DOM khi dữ liệu công khai đổi, tránh phát lại hiệu ứng và làm mất focus không cần thiết.
        const signature = JSON.stringify({
            products: selected.map((product) => [
                product.id,
                product.name,
                product.category_name,
                product.brand_name,
                product.image_url,
                product.image_urls,
                product.min_price,
                product.max_price,
                product.unit_price,
            ]),
            failed,
        });
        if (container.dataset.signature === signature) return;
        container.dataset.signature = signature;
        container.dataset.count = String(selected.length);
        container.setAttribute('aria-busy', 'false');
        if (!selected.length) {
            container.innerHTML = `
                <div class="hero-showcase-empty">
                    <span>KHÁM PHÁ CÙNG STOCKFLOW</span>
                    <strong>Một lựa chọn mới.<br />Một trải nghiệm mới.</strong>
                    <p>${failed ? 'Chưa tải được sản phẩm. Bạn có thể thử làm mới kệ hàng bên dưới.' : 'Khám phá danh mục hoặc đổi bộ lọc để tìm thiết bị phù hợp với bạn.'}</p>
                </div>
            `;
            return;
        }
        const positions = ['main', 'secondary', 'tertiary'];
        container.innerHTML = selected
            .map((product, index) => {
                const source =
                    safeProductImageUrl(product.image_url) ||
                    (product.image_urls || []).map(safeProductImageUrl).find(Boolean);
                const price = product.min_price ?? product.unit_price;
                const from = product.min_price != null && Number(product.min_price) !== Number(product.max_price);
                return `
                    <a class="hero-device hero-device-${positions[index]}" href="${productPagePath(product.id)}"
                        data-product-link data-product-id="${product.id}" aria-label="Xem sản phẩm ${escapeHtml(product.name)}">
                        <div class="hero-device-media">
                            ${
                                source
                                    ? `<img src="${escapeHtml(source)}" alt="${escapeHtml(product.name)}"
                                width="320" height="320" decoding="async" data-hero-image />`
                                    : ''
                            }
                            <span class="hero-image-error" ${source ? 'hidden' : ''}>${source ? 'Ảnh chưa tải được.' : 'Xem thông tin sản phẩm'}</span>
                        </div>
                        <div class="hero-device-information">
                            <span class="hero-device-category">${escapeHtml(product.brand_name || product.category_name)}</span>
                            <span class="hero-device-name">${escapeHtml(product.name)}</span>
                            <strong class="hero-device-price">${from ? 'Từ ' : ''}${amount(price)}</strong>
                        </div>
                    </a>
                `;
            })
            .join('');
    }

    /** Tình trạng chi nhánh lấy từ API chỉ đọc; hàng đã giữ hoặc SKU ngừng bán không được coi là còn hàng. */
    function productStock(product) {
        if (product.status !== 'ACTIVE') return { tone: 'empty', label: 'Đã ngừng kinh doanh' };
        const rows = state.detailAvailability;
        if (rows && state.branchId) {
            const ids = product.variants?.length
                ? product.variants
                      .filter((variant) => variant.status === 'ACTIVE')
                      .map((variant) => variant.sku_product_id)
                : [product.id];
            const matches = rows.filter(
                (row) => ids.includes(row.product_id) && String(row.warehouse_id) === state.branchId,
            );
            if (matches.length) {
                return matches.some((row) => row.in_stock)
                    ? { tone: 'available', label: 'Còn hàng tại chi nhánh đã chọn' }
                    : { tone: 'empty', label: 'Tạm hết hàng tại chi nhánh này' };
            }
        }
        const quantity = product.available_quantity ?? product.availableQuantity;
        const warehouseId = product.warehouse_id ?? product.warehouseId;
        const available = typeof quantity === 'number' ? quantity : NaN;
        if (
            !state.branchId ||
            warehouseId == null ||
            String(warehouseId) !== state.branchId ||
            !Number.isInteger(available) ||
            available < 0
        ) {
            return { tone: 'unknown', label: 'Xem tình trạng hàng tại chi nhánh' };
        }
        return available > 0
            ? { tone: 'available', label: '🟢 Sẵn hàng tại kho' }
            : { tone: 'empty', label: '🔴 Tạm hết hàng' };
    }

    /** Cập nhật SKU đang chọn và danh sách chi nhánh; không nới quyền của API kho nội bộ. */
    function refreshProductStock() {
        $$(
            '#catalog-grid [data-product-stock], #bestseller-grid [data-product-stock], #shop-product [data-product-stock]',
        ).forEach((element) => {
            const product = element.closest('#shop-product')
                ? state.detailSku
                : state.products.get(Number(element.dataset.productStock));
            if (!product) return;
            const stock = productStock(product);
            element.className = 'product-stock stock-' + stock.tone;
            element.textContent = stock.label;
        });
        renderProductBranchAvailability();
        const button = $('#shop-product-add-form button[type="submit"]');
        if (button && button.getAttribute('aria-busy') !== 'true' && state.detailSku) {
            button.disabled =
                isOperator() || state.detailSku.status !== 'ACTIVE' || productStock(state.detailSku).tone === 'empty';
        }
        syncMobilePurchase();
    }

    /** Tải tín hiệu cho mọi phiên bản/màu bằng một query; response cũ không được ghi đè model mới. */
    async function loadProductAvailability() {
        const root = state.detailRoot;
        if (!root || state.view !== 'shop' || state.shopTab !== 'product') return;
        const epoch = ++state.availabilityEpoch;
        state.detailAvailability = null;
        state.availabilityLoading = true;
        state.availabilityError = false;
        refreshProductStock();
        try {
            const rows = await api('/products/' + root.id + '/availability', {
                anonymous: true,
                channel: 'shop-product-availability',
            });
            if (
                epoch !== state.availabilityEpoch ||
                state.detailRoot?.id !== root.id ||
                state.view !== 'shop' ||
                state.shopTab !== 'product'
            )
                return;
            state.detailAvailability = rows;
        } catch (error) {
            if (error.name === 'AbortError' || epoch !== state.availabilityEpoch) return;
            state.availabilityError = true;
        } finally {
            if (epoch === state.availabilityEpoch) {
                state.availabilityLoading = false;
                refreshProductStock();
            }
        }
    }

    /** Khách xem còn/hết hàng theo SKU ở chi nhánh, không thấy quantity, reserved hoặc dữ liệu kiểm toán. */
    function renderProductBranchAvailability() {
        const container = $('#product-branch-availability');
        if (!container || !state.detailSku) return;
        container.innerHTML =
            state.warehouses
                .map((branch) => {
                    const row = state.detailAvailability?.find(
                        (value) => value.product_id === state.detailSku.id && value.warehouse_id === branch.id,
                    );
                    const tone = row ? (row.in_stock ? 'available' : 'empty') : 'unknown';
                    const text = state.availabilityLoading
                        ? 'Đang kiểm tra…'
                        : row
                          ? row.in_stock
                              ? 'Còn hàng'
                              : 'Tạm hết hàng'
                          : 'Chưa xác nhận';
                    return `<button type="button" class="branch-stock-option ${String(branch.id) === state.branchId ? 'is-selected' : ''}"
                data-action="select-product-branch" data-id="${branch.id}" aria-pressed="${String(branch.id) === state.branchId}">
                ${icon('warehouse')}<span>${escapeHtml(branch.name)}</span><small class="stock-${tone}">${text}</small>
            </button>`;
                })
                .join('') || '<p class="subtle">Chưa có chi nhánh đang phục vụ.</p>';
        $('#product-availability-error').hidden = !state.availabilityError;
        const stock = $('#shop-product [data-product-stock]');
        if (stock && state.availabilityLoading) stock.textContent = 'Đang kiểm tra hàng tại chi nhánh…';
    }

    /** Breadcrumb nối các nhóm cha thật rồi đến hãng; model/phiên bản không bị biến thành danh mục giả. */
    function renderProductBreadcrumb(root) {
        const category = state.categories.find((value) => value.id === root.category_id);
        const ancestors = category ? categoryAncestors(category).reverse() : [];
        const links = ancestors.map(
            (value) => `<a href="/?categoryId=${value.id}#shop" data-catalog-link>${escapeHtml(value.name)}</a>`,
        );
        if (root.brand_id)
            links.push(`<a href="/?categoryId=${root.category_id}&amp;brandId=${root.brand_id}#shop"
            data-catalog-link>${escapeHtml(root.brand_name)}</a>`);
        $('#product-breadcrumb-category').innerHTML =
            links.join('<span aria-hidden="true">›</span>') || escapeHtml(root.category_name);
        $('#product-breadcrumb-name').textContent = root.name;
    }

    /** Một mẫu thẻ dùng cho kệ và bán chạy; giữ đúng ID model và nút chọn SKU của giỏ hiện có. */
    function productCards(products, { bestseller = false } = {}) {
        return products
            .map((product) => {
                const stock = productStock(product);
                return `
                    <article class="product-card">
                        <a class="product-card-link" href="${productPagePath(product.id)}" data-product-link data-product-id="${product.id}" aria-label="Xem sản phẩm ${escapeHtml(product.name)}">
                            ${productArt(product)}
                            <div class="product-card-body">
                                <span class="product-category">${escapeHtml(product.category_name)}</span>
                                <h3>${escapeHtml(product.name)}</h3>
                                <span class="product-sku mono" title="${escapeHtml(product.sku)}">${escapeHtml(product.sku)}</span>
                                ${bestseller ? '<span class="bestseller-label">Bán chạy</span>' : ''}
                                ${configurationPreview(product)}
                                <div class="product-card-footer">
                                    <strong class="product-price">${Number(product.min_price) !== Number(product.max_price) ? 'Từ ' : ''}${amount(product.min_price ?? product.unit_price)}</strong>
                                    <p class="product-stock stock-${stock.tone}" data-product-stock="${product.id}">
                                        ${stock.label}
                                    </p>
                                </div>
                            </div>
                        </a>
                        <div class="product-card-actions">
                            <button
                                type="button"
                                class="button add-button"
                                data-action="add-cart"
                                data-id="${product.id}"
                                aria-label="Thêm ${escapeHtml(product.name)} vào giỏ"
                                ${isOperator() ? 'disabled title="Dùng tài khoản khách hàng để mua sắm"' : ''}
                            >
                                ${icon('plus')}${product.variants?.length ? 'Chọn phiên bản và màu' : 'Thêm vào giỏ'}
                            </button>
                        </div>
                    </article>
                `;
            })
            .join('');
    }

    /** Cả thẻ là liên kết trang sản phẩm; nút thêm giỏ riêng không bị lồng vào liên kết. */
    function renderProducts(products) {
        const cards = productCards(products);
        $('#catalog-grid').innerHTML = cards
            ? cards +
              `
                <p class="catalog-demo-note">
                    Sản phẩm chưa có ảnh đã lưu dùng hình minh họa theo danh mục.
                    Giá từ là giá thấp nhất của các lựa chọn đang bán. Giá và tồn được xác nhận theo SKU khi đặt hàng.
                </p>
            `
            : '<div class="grid-message">Chưa có sản phẩm phù hợp. Thử đổi danh mục, từ khóa hoặc khoảng giá.' +
              '<br /><button class="button secondary small" type="button" data-action="clear-catalog-filter" data-filter="all">Xóa bộ lọc</button></div>';
        prepareStorefrontReveals();
    }

    /** Mô tả luôn là văn bản đã escape; giữ xuống dòng bằng CSS và không tự tạo thông số sản phẩm. */
    function productDescription(product) {
        const description = String(product.description ?? '').trim();
        return `<section id="product-description" class="product-description">
            <h3>Mô tả sản phẩm</h3>
            <p class="product-description-text ${description ? '' : 'subtle'}">${escapeHtml(description || 'Cửa hàng chưa bổ sung mô tả cho sản phẩm này.')}</p>
        </section>`;
    }

    /** Bảng thông số lấy nguyên thứ tự từ database; tất cả giá trị được escape như mô tả. */
    function productSpecifications(product) {
        const rows = Array.isArray(product.specifications) ? product.specifications : [];
        return `<section id="product-specifications" class="product-specifications">
            <div class="specifications-heading"><span class="eyebrow">THÔNG TIN SẢN PHẨM</span><h3>Thông số kỹ thuật</h3></div>
            ${rows.length ? `<dl class="specifications-table">${rows.map((row) => `<div><dt>${escapeHtml(row.name)}</dt><dd>${escapeHtml(row.value)}</dd></div>`).join('')}</dl>` : '<p class="subtle">Cửa hàng chưa bổ sung thông số kỹ thuật.</p>'}
        </section>`;
    }

    /** Chỉ mã HEX hợp lệ được đưa vào CSS; tên màu nhập tay không trở thành mã thực thi. */
    function colorHex(value) {
        return /^#[0-9a-fA-F]{6}$/.test(value || '') ? value : '#94a3b8';
    }

    /** Ưu tiên HEX đã lưu; tên màu phổ biến chỉ tạo minh họa gần đúng khi chưa có mã màu. */
    function swatchColor(variant) {
        if (/^#[0-9a-fA-F]{6}$/.test(variant.color_hex || '')) return variant.color_hex;
        const name = String(variant.color_name || '').toLocaleLowerCase('vi-VN');
        const palette = [
            [/midnight|nửa đêm/, '#1e293b'],
            [/space gr[ae]y|xám không gian/, '#4a5568'],
            [/silver|bạc/, '#e2e8f0'],
            [/starlight|ánh sao/, '#e7dfd0'],
            [/black|đen/, '#242424'],
            [/white|trắng/, '#f8fafc'],
            [/blue|xanh dương|xanh biển/, '#477eb3'],
            [/green|xanh lá/, '#527761'],
            [/purple|tím/, '#9681b4'],
            [/pink|hồng/, '#e4a4b3'],
            [/red|đỏ/, '#c94d58'],
            [/gold|vàng/, '#c9aa71'],
            [/gr[ae]y|xám/, '#81858d'],
        ];
        return palette.find(([pattern]) => pattern.test(name))?.[1] || colorHex(null);
    }

    /** Một SKU thuộc đúng phiên bản/màu; ID Product tiếp tục là khóa giỏ và đơn hàng. */
    function colorSku(root, variant) {
        const version = root.versions?.find((value) => value.id === variant.version_id);
        return {
            ...root,
            id: variant.sku_product_id,
            sku: variant.sku,
            unit_price: variant.unit_price,
            status: variant.status,
            image_url: variant.image_url,
            image_urls: variant.image_urls,
            color_name: variant.color_name,
            version_id: variant.version_id,
            version_name: variant.version_name,
            specifications: version?.effective_specifications || root.specifications || [],
            parent_product_id: root.id,
            variants: [],
            versions: [],
        };
    }

    /** Trạng thái SKU gốc không thay thế trạng thái của cả model khi một màu ngừng bán. */
    function saleSku(product) {
        const own = product?.variants?.find((variant) => variant.sku_product_id === product.id);
        return own ? colorSku(product, own) : product;
    }

    /** Nhãn trung tính của dữ liệu V11 không giả làm một cấu hình kỹ thuật do ADMIN nhập. */
    function versionLabel(product) {
        return product.version_name === 'Phiên bản hiện tại' ? '' : product.version_name || '';
    }

    /** Giỏ phân biệt cấu hình và màu; các SKU cùng model không bị gộp số lượng vào nhau. */
    function cartProductName(product) {
        return [product.name, versionLabel(product), product.color_name].filter(Boolean).join(' · ');
    }

    /** Một thẻ model giới thiệu các lựa chọn thật, không đếm cùng một màu hai lần qua các phiên bản. */
    function configurationPreview(product) {
        // Lựa chọn đã xóa không xuất hiện trên kệ; màu tạm ngừng bán vẫn giữ cách hiển thị cũ.
        const variants = (product.variants || []).filter(
            (color) =>
                !color.archived && !product.versions?.find((version) => version.id === color.version_id)?.archived,
        );
        if (!variants.length) return '';
        const colors = [
            ...new Map(variants.map((value) => [value.color_name.toLocaleLowerCase('vi-VN'), value])).values(),
        ];
        const versionCount = new Set(variants.map((value) => value.version_id)).size;
        return `<div class="product-color-preview">
            ${colors
                .slice(0, 5)
                .map(
                    (color) =>
                        `<span class="color-dot" style="--color: ${colorHex(color.color_hex)}" title="${escapeHtml(color.color_name)}"></span>`,
                )
                .join('')}
            <span>${versionCount} phiên bản · ${colors.length} màu sắc</span>
        </div>`;
    }

    /** Một trang model chọn phiên bản rồi màu; giá, ảnh và thông số thuộc đúng SKU đã chọn. */
    function renderShopProductDetail(root, selectedId = null, quantity = 1) {
        // Dữ liệu lưu trữ vẫn dùng ở ADMIN; cửa hàng chỉ cho chọn phiên bản/màu chưa xóa.
        const versions = (root.versions || []).filter((version) => !version.archived);
        const variants = (root.variants || []).filter(
            (color) => !color.archived && versions.some((version) => version.id === color.version_id),
        );
        const selected =
            variants.find((variant) => variant.sku_product_id === selectedId) ||
            variants.find((variant) => variant.sku_product_id === root.id && variant.status === 'ACTIVE') ||
            variants.find((variant) => variant.status === 'ACTIVE') ||
            variants[0];
        const version = versions.find((value) => value.id === selected?.version_id);
        const colors = selected ? variants.filter((value) => value.version_id === selected.version_id) : [];
        const product = selected
            ? colorSku(root, selected)
            : root.variants?.length
              ? { ...root, status: 'INACTIVE' }
              : root;
        state.detailRoot = root;
        state.detailSku = product;
        state.products.set(root.id, root);
        variants.forEach((variant) => {
            if (variant.sku_product_id !== root.id) state.products.set(variant.sku_product_id, colorSku(root, variant));
        });
        const stock = productStock(product);
        const active = product.status === 'ACTIVE';
        const body = $('#shop-product-detail-body');
        body.dataset.productId = String(state.productId);
        body.dataset.skuId = String(product.id);
        body.dataset.versionId = String(selected?.version_id || '');
        renderProductBreadcrumb(root);
        const title = [root.name, versionLabel(product)].filter(Boolean).join(' · ');
        document.title = title + ' | StockFlow Tech';
        body.innerHTML = `
            <header class="product-detail-heading">
                <h1 id="shop-product-name" tabindex="-1">${escapeHtml(title)}</h1>
                <div class="product-detail-meta">
                    ${root.brand_name ? '<span>Thương hiệu <strong>' + escapeHtml(root.brand_name) + '</strong></span>' : ''}
                    <span class="shop-product-sku">Mã sản phẩm <span class="mono">${escapeHtml(product.sku)}</span></span>
                    <button type="button" data-action="scroll-product-info" data-target="product-specifications">Thông số kỹ thuật</button>
                </div>
            </header>
            <div class="shop-product-layout">
                <div class="product-visual-column">
                    ${renderShopProductGallery(product)}
                    <div class="product-service-strip">
                        <span>${icon('box')}Chọn đúng phiên bản và màu</span>
                        <span>${icon('warehouse')}Phục vụ tại nhiều chi nhánh</span>
                        <span>${icon('truck')}Theo dõi tiến trình giao hàng</span>
                    </div>
                </div>
                <div class="shop-product-summary">
                    <div class="product-price-panel">
                    <span class="shop-product-price-label">Giá bán${selected ? ' · ' + escapeHtml(selected.color_name) : ''}</span>
                    <strong class="shop-product-price">${amount(product.unit_price)}</strong>
                    <span class="price-selection-note">${escapeHtml(version?.name || 'Sản phẩm đang chọn')}</span>
                    </div>
                    ${
                        versions.length
                            ? `
                        <fieldset class="version-picker">
                            <legend>Phiên bản <strong>${escapeHtml(version?.name || '')}</strong></legend>
                            <div class="version-options" role="radiogroup" aria-label="Chọn phiên bản">
                                ${versions
                                    .map((value) => {
                                        const skus = variants.filter(
                                            (variant) => variant.version_id === value.id && variant.status === 'ACTIVE',
                                        );
                                        const minimum = skus.length
                                            ? Math.min(...skus.map((sku) => Number(sku.unit_price)))
                                            : null;
                                        return `<button type="button" class="version-option ${value.id === version?.id ? 'is-selected' : ''}"
                                        data-action="select-product-version" data-id="${value.id}"
                                        role="radio" aria-checked="${value.id === version?.id}" ${!skus.length ? 'disabled' : ''}>
                                        <span class="version-check" aria-hidden="true">✓</span>
                                        <strong>${escapeHtml(value.name)}</strong>
                                        <small>${minimum == null ? 'Chưa mở bán' : 'Từ ' + amount(minimum)}</small>
                                    </button>`;
                                    })
                                    .join('')}
                            </div>
                        </fieldset>`
                            : ''
                    }
                    ${
                        colors.length
                            ? `
                        <fieldset class="color-picker">
                            <legend>Màu sắc <strong>${escapeHtml(selected.color_name)}</strong></legend>
                            <div class="color-options" role="radiogroup" aria-label="Chọn màu sắc">
                                ${colors
                                    .map(
                                        (variant) => `
                                    <button type="button" class="color-option ${variant.sku_product_id === product.id ? 'is-selected' : ''}"
                                        data-action="select-product-color" data-id="${variant.sku_product_id}"
                                        role="radio" aria-checked="${variant.sku_product_id === product.id}" ${variant.status !== 'ACTIVE' ? 'disabled' : ''}>
                                        ${colorOptionThumbnail(variant)}
                                        <span class="color-option-copy">
                                            <strong class="color-option-label">
                                                <span class="color-swatch-dot"
                                                    style="--swatch-color: ${swatchColor(variant)}"
                                                    aria-hidden="true"></span>
                                                ${escapeHtml(variant.color_name)}
                                            </strong>
                                            <small>${variant.status === 'ACTIVE' ? amount(variant.unit_price) : 'Đã ngừng bán'}</small>
                                        </span>
                                        <span class="color-check" aria-hidden="true">✓</span>
                                    </button>`,
                                    )
                                    .join('')}
                            </div>
                        </fieldset>`
                            : ''
                    }
                    <p class="product-stock stock-${stock.tone}" data-product-stock="${product.id}">${stock.label}</p>
                    <form id="shop-product-add-form" class="shop-product-purchase">
                        <label>Chi nhánh phục vụ<select id="detail-shop-warehouse" data-store-warehouse aria-label="Chọn chi nhánh mua sản phẩm"></select></label>
                        <div class="shop-product-buy-row">
                            <label>Số lượng<input name="quantity" id="shop-product-quantity" type="number" value="${quantity}" min="1" max="${MAX_QUANTITY}" step="1" required /></label>
                            <button class="button primary" type="submit" ${!active || isOperator() || stock.tone === 'empty' ? 'disabled' : ''}>${icon('plus')}Thêm vào giỏ</button>
                        </div>
                        <p class="subtle">${isOperator() ? 'Dùng tài khoản khách hàng để mua sắm.' : 'Hàng được giữ trong 15 phút sau khi tạo đơn. Tình trạng hàng có thể thay đổi trước khi đặt.'}</p>
                    </form>
                    <details class="product-branch-panel" open>
                        <summary>${icon('warehouse')}Xem chi nhánh có hàng</summary>
                        <div id="product-branch-availability" class="product-branch-list"></div>
                        <button type="button" class="branch-stock-refresh" data-action="refresh-product-availability">Kiểm tra lại tình trạng hàng</button>
                        <p id="product-availability-error" class="availability-error" hidden>
                            Chưa kiểm tra được tình trạng hàng.
                            Vui lòng nhấn kiểm tra lại.
                        </p>
                    </details>
                </div>
            </div>
            <div class="product-information-grid">${productDescription(root)}${productSpecifications(product)}</div>
            <section class="product-reviews" aria-labelledby="product-reviews-title">
                <h2 id="product-reviews-title">Đánh giá từ khách đã mua</h2>
                <div id="product-reviews-content" aria-live="polite">Đang tải đánh giá…</div>
            </section>
        `;
        void loadProductReviews(root.id);
        renderWarehouses();
        refreshProductStock();
        observeMobilePurchase();
        prepareStorefrontReveals();
    }

    /** Ảnh nhỏ dùng đúng ảnh bìa của SKU; thiếu ảnh chỉ hiển thị màu và không mượn ảnh khác cấu hình. */
    function colorOptionThumbnail(variant) {
        const image =
            safeProductImageUrl(variant.image_url) || (variant.image_urls || []).map(safeProductImageUrl).find(Boolean);
        return `<span class="color-option-photo">
            <span class="color-dot" style="--color: ${colorHex(variant.color_hex)}" aria-hidden="true"></span>
            ${image ? `<img src="${escapeHtml(image)}" alt="${escapeHtml(variant.color_name)}" loading="lazy" data-color-thumbnail />` : ''}
        </span>`;
    }

    /** Lưu SKU đã chọn vào URL thật; reload, chia sẻ và Back/Forward giữ đúng phiên bản/màu. */
    function selectProductSku(skuId) {
        if (!state.detailRoot || !isCurrentProductPage(state.productId)) return;
        const variant = state.detailRoot.variants.find((value) => value.sku_product_id === skuId);
        if (!variant || variant.status !== 'ACTIVE') return;
        const quantity = Number($('#shop-product-quantity')?.value) || 1;
        if (state.productId !== skuId) {
            state.productId = skuId;
            pushPage();
        }
        renderShopProductDetail(state.detailRoot, skuId, quantity);
    }

    /** Đổi cấu hình giữ số lượng/chi nhánh; giữ màu cũ nếu cấu hình mới có đúng màu còn bán. */
    function selectProductVersion(versionId) {
        if (!state.detailRoot || !isCurrentProductPage(state.productId)) return;
        const choices = state.detailRoot.variants.filter(
            (value) => value.version_id === versionId && value.status === 'ACTIVE',
        );
        const selected = choices.find((value) => value.color_name === state.detailSku?.color_name) || choices[0];
        if (!selected) return;
        selectProductSku(selected.sku_product_id);
        $('[data-action="select-product-version"][data-id="' + versionId + '"]')?.focus({ preventScroll: true });
    }

    /** Bộ ảnh chỉ gồm URL đã lưu, bỏ link trùng với ảnh bìa; không tự thêm góc chụp minh họa. */
    function productGallery(product) {
        const fallback = productImage(product).fallback;
        const cover = safeProductImageUrl(product.image_url);
        const additional = Array.isArray(product.image_urls) ? product.image_urls.map(safeProductImageUrl) : [];
        const sources = [...new Set([cover, ...additional].filter(Boolean))];
        if (!sources.length) return [{ ...productImage(product), label: 'Ảnh minh họa' }];
        return sources.map((src, index) => ({
            src,
            fallback,
            custom: true,
            label: src === cover ? 'Ảnh bìa' : 'Ảnh sản phẩm ' + (index + 1),
        }));
    }

    /** Thư viện ảnh có nút chuyển và ảnh nhỏ; một ảnh duy nhất không tạo thêm ảnh giả để lấp bố cục. */
    function renderShopProductGallery(product) {
        const photos = productGallery(product);
        const photo = photos[0];
        return `
            <section class="shop-product-gallery" data-selected-index="0" aria-label="Bộ ảnh sản phẩm">
                <div class="product-card-img-wrap shop-product-image">
                    <img
                        id="shop-product-main-image"
                        class="product-card-img"
                        src="${escapeHtml(photo.src)}"
                        alt="${escapeHtml(product.name)} — ${escapeHtml(photo.label)}"
                        decoding="async"
                        data-image-fallback="${escapeHtml(photo.fallback)}"
                    />
                    <span class="product-photo-caption">${escapeHtml(photo.label)}</span>
                    <span class="product-image-error" hidden>Chưa tải được ảnh. Vui lòng kiểm tra kết nối.</span>
                    <span id="shop-product-image-count" class="gallery-counter" aria-live="polite">1 / ${photos.length}</span>
                    ${
                        photos.length > 1
                            ? `
                        <button class="gallery-arrow gallery-arrow-prev" type="button" data-action="gallery-step" data-direction="-1" aria-label="Xem ảnh trước">
                            ${icon('arrow')}
                        </button>
                        <button class="gallery-arrow" type="button" data-action="gallery-step" data-direction="1" aria-label="Xem ảnh sau">
                            ${icon('arrow')}
                        </button>
                    `
                            : ''
                    }
                </div>
                ${
                    photos.length > 1
                        ? `
                    <div class="gallery-thumbnails" aria-label="Chọn ảnh sản phẩm">
                        ${photos
                            .map(
                                (entry, index) => `
                            <button
                                class="gallery-thumbnail ${index === 0 ? 'active' : ''}"
                                type="button"
                                data-action="gallery-select"
                                data-photo-index="${index}"
                                aria-pressed="${index === 0}"
                                aria-label="Xem ${escapeHtml(entry.label)} của ${escapeHtml(product.name)}"
                            >
                                <img src="${escapeHtml(entry.src)}" alt="" loading="lazy" decoding="async" data-gallery-thumbnail />
                                <span class="gallery-thumb-error" hidden>Ảnh lỗi</span>
                            </button>
                        `,
                            )
                            .join('')}
                    </div>
                    <p class="gallery-help">Bấm ảnh nhỏ hoặc dùng phím ← → để xem các góc chụp.</p>
                `
                        : ''
                }
            </section>
        `;
    }

    /** Đổi ảnh trong cùng trang; không gọi API, thay giá, số lượng, chi nhánh hoặc giỏ hàng. */
    function selectGalleryImage(index) {
        if (!isCurrentProductPage(state.productId) || !Number.isInteger(index)) return;
        const product = state.detailSku;
        const gallery = $('.shop-product-gallery');
        if (!product || !gallery) return;
        const photos = productGallery(product);
        const selected = ((index % photos.length) + photos.length) % photos.length;
        const previous = Number(gallery.dataset.selectedIndex);
        if (selected === previous) return;
        const photo = photos[selected];
        const wrapper = $('.shop-product-image', gallery);
        const image = document.createElement('img');
        image.id = 'shop-product-main-image';
        image.className = 'product-card-img';
        image.alt = product.name + ' — ' + photo.label;
        image.decoding = 'async';
        image.dataset.imageFallback = photo.fallback;
        image.style.setProperty('--gallery-shift', index > previous ? '14px' : '-14px');
        image.src = photo.src;
        // Thay phần tử ảnh để lỗi tải của ảnh trước không đánh dấu nhầm ảnh vừa chọn.
        $('#shop-product-main-image').replaceWith(image);
        wrapper.classList.remove('product-image-unavailable');
        $('.product-image-error', wrapper).hidden = true;
        $('.product-photo-caption', wrapper).textContent = photo.label;
        gallery.dataset.selectedIndex = String(selected);
        $('#shop-product-image-count').textContent = selected + 1 + ' / ' + photos.length;
        $$('.gallery-thumbnail', gallery).forEach((button) => {
            const active = Number(button.dataset.photoIndex) === selected;
            button.classList.toggle('active', active);
            button.setAttribute('aria-pressed', String(active));
            if (active) button.scrollIntoView({ block: 'nearest', inline: 'nearest' });
        });
    }

    /** Chỉ ảnh vừa tải xong và còn được chọn mới hiện dần; ảnh cũ không tác động tới SKU mới. */
    document.addEventListener(
        'load',
        (event) => {
            const image = event.target;
            if (!(image instanceof HTMLImageElement) || image !== $('#shop-product-main-image')) return;
            if (!image.isConnected || image.hidden || reducedStorefrontMotion.matches) return;
            image.classList.add('gallery-image-enter');
        },
        true,
    );

    /** Phím chuyển ảnh chỉ áp dụng khi focus nằm trong bộ ảnh, không cản phím ở form mua hàng. */
    document.addEventListener('keydown', (event) => {
        if (event.altKey || event.ctrlKey || event.metaKey || !event.target.closest('.shop-product-gallery')) return;
        if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
        event.preventDefault();
        const gallery = $('.shop-product-gallery');
        selectGalleryImage(Number(gallery.dataset.selectedIndex) + (event.key === 'ArrowLeft' ? -1 : 1));
    });

    /** Đường dẫn thật cho phép sao chép, mở tab mới và tải trực tiếp qua Spring Boot. */
    function productPagePath(id) {
        return '/san-pham/' + id;
    }

    /** Response chậm không được ghi đè trang khác hoặc mở lại sản phẩm khách đã rời. */
    function isCurrentProductPage(id) {
        return state.view === 'shop' && state.shopTab === 'product' && state.productId === id;
    }

    /** Khách đọc catalog công khai; trang riêng giữ nguyên giỏ và không gọi số liệu kho nội bộ. */
    async function loadShopProductDetail() {
        const id = state.productId;
        const body = $('#shop-product-detail-body');
        delete body.dataset.productId;
        state.detailRoot = null;
        state.detailSku = null;
        state.detailAvailability = null;
        observeMobilePurchase();
        $('#product-breadcrumb-category').textContent = 'Sản phẩm';
        $('#product-breadcrumb-name').textContent = 'Chi tiết sản phẩm';
        body.innerHTML = `<div class="product-detail-skeleton" aria-hidden="true">
            <div class="skeleton skeleton-detail-image"></div>
            <div class="skeleton-detail-copy">
                <span class="skeleton skeleton-line"></span>
                <span class="skeleton skeleton-line short"></span>
                <span class="skeleton skeleton-detail-price"></span>
                <span class="skeleton skeleton-detail-options"></span>
                <span class="skeleton skeleton-detail-options"></span>
            </div>
        </div><span class="sr-only" role="status">Đang tải thông tin sản phẩm…</span>`;
        try {
            if (!Number.isSafeInteger(id) || id < 1) {
                throw new Error('Đường dẫn sản phẩm không hợp lệ. Vui lòng chọn lại từ cửa hàng.');
            }
            let product = await api('/products/' + id, { anonymous: true, channel: 'shop-product-detail' });
            const selectedId = product.parent_product_id ? id : null;
            if (product.parent_product_id) {
                product = await api('/products/' + product.parent_product_id, {
                    anonymous: true,
                    channel: 'shop-product-detail',
                });
            }
            if (!isCurrentProductPage(id)) return;
            renderShopProductDetail(product, selectedId);
            renderCart();
            $('#shop-product-name').focus({ preventScroll: true });
            await loadProductAvailability();
        } catch (error) {
            if (error.name === 'AbortError' || !isCurrentProductPage(id)) return;
            const title = error.status === 404 ? 'Không tìm thấy sản phẩm' : 'Chưa tải được sản phẩm';
            document.title = title + ' | StockFlow Tech';
            $('#product-breadcrumb-name').textContent = title;
            body.innerHTML = `
                <section class="shop-product-message product-page-error" role="alert">
                    ${icon('alert')}
                    <h1>${title}</h1>
                    <p>${escapeHtml(error.message)}</p>
                    <a class="button primary" href="/#shop" data-shop-link>Quay về cửa hàng</a>
                    ${error.status !== 404 && Number.isSafeInteger(id) && id > 0 ? '<button class="button secondary" type="button" data-action="refresh">Thử lại</button>' : ''}
                </section>
            `;
            handleError(error);
        }
    }

    /** Thêm đúng số lượng khách chọn, dùng chung giỏ hiện có và không tạo đơn hoặc giữ kho tại đây. */
    function addProductFromDetail(form) {
        if (isOperator()) throw new Error('Dùng tài khoản khách hàng để mua sắm.');
        const id = Number($('#shop-product-detail-body').dataset.productId);
        if (!isCurrentProductPage(id)) throw new Error('Vui lòng tải lại thông tin sản phẩm trước khi thêm giỏ.');
        const product = state.detailSku;
        if (!product || product.status !== 'ACTIVE') throw new Error('Sản phẩm đã ngừng kinh doanh.');
        if (productStock(product).tone === 'empty') {
            throw new Error('Lựa chọn này tạm hết hàng tại chi nhánh. Vui lòng chọn màu hoặc chi nhánh khác.');
        }
        addCart(product.id, Number(form.elements.quantity.value));
        openDialog('cart-dialog');
    }

    /** Giữ bố cục tám thẻ khi tải; thông báo chỉ dành cho trình đọc màn hình, không giả dữ liệu bán hàng. */
    function renderCatalogLoading() {
        $('#catalog-grid').innerHTML =
            Array.from(
                { length: 8 },
                () => `
            <div class="product-skeleton" aria-hidden="true">
                <div class="skeleton skeleton-card-image"></div>
                <div class="skeleton-card-body">
                    <span class="skeleton skeleton-line short"></span>
                    <span class="skeleton skeleton-line"></span>
                    <span class="skeleton skeleton-line"></span>
                    <span class="skeleton skeleton-card-price"></span>
                    <span class="skeleton skeleton-card-button"></span>
                </div>
            </div>`,
            ).join('') + '<span class="sr-only" role="status">Đang tải sản phẩm…</span>';
    }

    /** Kệ chỉ hiển thị ACTIVE; tìm kiếm tên/SKU kết hợp danh mục và phân trang tại database. */
    async function loadCatalog() {
        $('#catalog-grid').dataset.catalogStatus = 'loading';
        renderCatalogLoading();
        $('#catalog-pagination').replaceChildren();
        $('#catalog-count').textContent = '—';
        renderCategoryChips();
        renderCatalogActiveFilters();
        syncQuickPriceChips();
        try {
            const result = await api('/products', {
                anonymous: true,
                channel: 'catalog',
                query: {
                    page: state.pages.catalog,
                    size: 8,
                    sort: state.catalogSort,
                    status: 'ACTIVE',
                    grouped: true,
                    categoryId: $('#catalog-category').value,
                    brandId: state.catalogBrandId,
                    q: $('#catalog-query').value.trim(),
                    minPrice: state.catalogMinPrice,
                    maxPrice: state.catalogMaxPrice,
                },
            });
            result.content.forEach((product) => state.products.set(product.id, product));
            renderProducts(result.content);
            renderHeroShowcase(result.content);
            $('#catalog-grid').dataset.catalogStatus = 'ready';
            $('#catalog-count').textContent =
                'Hiển thị ' + integer(result.content.length) + ' / ' + integer(result.total_elements) + ' sản phẩm';
            renderPager('catalog', result);
            renderCart();
            if (state.view === 'shop' && state.shopTab === 'catalog') replaceHash();
        } catch (error) {
            if (error.name !== 'AbortError') {
                $('#catalog-grid').dataset.catalogStatus = 'error';
                renderHeroShowcase([], { failed: true });
                $('#catalog-grid').innerHTML =
                    '<div class="grid-message">Chưa tải được sản phẩm. Vui lòng làm mới.</div>';
                $('#catalog-count').textContent = 'Chưa tải được sản phẩm';
            }
            throw error;
        }
    }

    /** Giỏ nhiều mặt hàng, đơn giá tạm tính từ catalog; backend chụp giá thật tại thời điểm đặt. */
    function renderCart() {
        let total = 0;
        let quantity = 0;
        $('#cart-items').innerHTML = [...state.cart.values()]
            .map((item) => {
                const product = saleSku(state.products.get(item.product.id) || item.product);
                total += Number(product.unit_price) * item.quantity;
                quantity += item.quantity;
                return (
                    '<div class="cart-line"><div class="cart-line-head"><div><strong>' +
                    escapeHtml(cartProductName(product)) +
                    '</strong><span class="meta-line mono">' +
                    escapeHtml(product.sku) +
                    '</span></div>' +
                    '<button class="icon-button" type="button" data-action="remove-cart" data-id="' +
                    product.id +
                    '" aria-label="Xóa ' +
                    escapeHtml(product.name) +
                    '">' +
                    icon('close') +
                    '</button></div>' +
                    '<div class="cart-line-foot"><div class="cart-quantity">' +
                    '<button class="icon-button" type="button" data-action="cart-minus" data-id="' +
                    product.id +
                    '" aria-label="Giảm số lượng" ' +
                    (item.quantity === 1 ? 'disabled' : '') +
                    '>' +
                    icon('minus') +
                    '</button>' +
                    '<input type="number" min="1" max="' +
                    MAX_QUANTITY +
                    '" step="1" value="' +
                    item.quantity +
                    '" data-cart-quantity="' +
                    product.id +
                    '" aria-label="Số lượng ' +
                    escapeHtml(product.name) +
                    '">' +
                    '<button class="icon-button" type="button" data-action="cart-plus" data-id="' +
                    product.id +
                    '" aria-label="Tăng số lượng" ' +
                    (item.quantity >= MAX_QUANTITY ? 'disabled' : '') +
                    '>' +
                    icon('plus') +
                    '</button>' +
                    '</div><strong class="price">' +
                    amount(Number(product.unit_price) * item.quantity) +
                    '</strong></div></div>'
                );
            })
            .join('');
        if (!state.cart.size) {
            const waiting = state.cartLoading || state.cartRestoreFailed;
            $('#cart-items').innerHTML =
                '<div class="empty-state">' +
                icon('cart') +
                '<h3>' +
                (waiting ? 'Đang khôi phục giỏ hàng' : 'Giỏ hàng đang trống') +
                '</h3><p>' +
                (waiting
                    ? 'Thông tin sản phẩm sẽ hiển thị sau khi kết nối thành công.'
                    : 'Thêm món đồ yêu thích từ cửa hàng để bắt đầu.') +
                '</p>' +
                (waiting
                    ? ''
                    : '<button class="button primary empty-cart-cta" type="button" data-action="explore-products">Khám phá sản phẩm ngay ' +
                      icon('arrow') +
                      '</button>') +
                '</div>';
        }
        if (state.cartRestoreFailed) {
            $('#cart-items').insertAdjacentHTML(
                'beforeend',
                `<div class="notice error" role="status">
                    ${icon('alert')}
                    <div class="notice-content">
                        <strong>Chưa khôi phục được giỏ hàng</strong>
                        <div>Bản lưu vẫn được giữ. Kiểm tra kết nối và thử lại để cập nhật giá trước khi đặt hàng.</div>
                        <button class="button secondary small" type="button" data-action="retry-cart">Thử lại</button>
                    </div>
                </div>`,
            );
        }
        $('#cart-total').textContent = amount(total);
        renderShippingTotal();
        $('#cart-badge').textContent = integer(quantity);
        $('#cart-count').textContent = integer(state.cart.size);
        renderPermissions();
    }

    /** Gộp theo productId để mỗi sản phẩm chỉ xuất hiện một lần trong request đặt đơn. */
    function addCart(id, quantity = 1) {
        if (isOperator()) return;
        if (state.cartLoading || state.cartRestoreFailed || state.authBusy) {
            throw new Error('Vui lòng khôi phục giỏ và chờ phiên đăng nhập cập nhật xong trước khi thêm sản phẩm.');
        }
        const product = saleSku(state.products.get(id));
        if (!product || product.status !== 'ACTIVE') return;
        const item = state.cart.get(id);
        if (!item && state.cart.size >= MAX_CART_ITEMS) throw new Error('Một đơn tối đa 100 mặt hàng.');
        if (!Number.isInteger(quantity) || quantity < 1) throw new Error('Số lượng phải là số nguyên dương.');
        const totalQuantity = (item ? item.quantity : 0) + quantity;
        if (totalQuantity > MAX_QUANTITY) throw new Error('Số lượng đã đạt giới hạn.');
        state.cart.set(id, { product, quantity: totalQuantity });
        saveCart();
        renderCart();
        bounceCart();
        notify('success', 'Đã thêm ' + product.name + ' vào giỏ hàng.');
    }

    /** Số lượng giỏ phải là số nguyên dương trong giới hạn INTEGER của API. */
    function setCartQuantity(id, quantity) {
        if (state.cartLoading || state.cartRestoreFailed) return;
        const item = state.cart.get(id);
        if (!item) return;
        if (!Number.isInteger(quantity) || quantity < 1 || quantity > MAX_QUANTITY) {
            renderCart();
            throw new Error('Số lượng phải là số nguyên từ 1 đến ' + integer(MAX_QUANTITY) + '.');
        }
        item.quantity = quantity;
        saveCart();
        renderCart();
    }

    let checkoutShippingQuote = null;
    let shippingLocationVersion = 0;
    let shippingFeeVersion = 0;

    function resetLocationSelect(id, placeholder) {
        const select = $('#' + id);
        select.innerHTML = '<option value="">' + placeholder + '</option>';
        select.disabled = true;
    }

    function renderShippingTotal() {
        const subtotal = [...state.cart.values()].reduce((sum, item) => sum + Number(saleSku(state.products.get(item.product.id) || item.product).unit_price) * item.quantity, 0);
        $('#checkoutShippingFee').textContent = checkoutShippingQuote ? amount(checkoutShippingQuote.fee)
            + (checkoutShippingQuote.testMode ? ' (phí thử nghiệm)' : '') : 'Chọn địa chỉ / chờ tính phí';
        $('#checkoutTotal').textContent = amount(subtotal + (checkoutShippingQuote?.fee || 0));
    }

    async function loadCheckoutProvinces() {
        const mode = await api('/locations/mode');
        $('#checkoutShippingNotice').textContent = mode.test_mode
            ? 'Đang thử nghiệm GHN: địa chỉ có thể là dữ liệu mẫu, phí chưa dùng cho giao hàng thật.'
            : 'Chọn địa chỉ để lấy cước vận chuyển từ GHN.';
        const select = $('#checkoutProvince');
        if (select.options.length > 1) return;
        const values = await api('/locations/provinces');
        select.innerHTML = '<option value="">Chọn tỉnh / thành phố</option>' + values.map(p =>
            '<option value="' + p.ProvinceID + '">' + escapeHtml(p.ProvinceName) + '</option>').join('');
    }

    async function changeCheckoutLocation(level) {
        const version = ++shippingLocationVersion;
        ++shippingFeeVersion;
        checkoutShippingQuote = null;
        renderShippingTotal();
        if (level === 'province') {
            resetLocationSelect('checkoutDistrict', 'Chọn quận / huyện');
            resetLocationSelect('checkoutWard', 'Chọn phường / xã');
            const id = $('#checkoutProvince').value;
            if (!id) return;
            const values = await api('/locations/districts', { query: { province_id: id } });
            if (version !== shippingLocationVersion || id !== $('#checkoutProvince').value) return;
            const select = $('#checkoutDistrict');
            select.innerHTML += values.map(d => '<option value="' + d.DistrictID + '">' + escapeHtml(d.DistrictName) + '</option>').join('');
            select.disabled = false;
        } else {
            resetLocationSelect('checkoutWard', 'Chọn phường / xã');
            const id = $('#checkoutDistrict').value;
            if (!id) return;
            const values = await api('/locations/wards', { query: { district_id: id } });
            if (version !== shippingLocationVersion || id !== $('#checkoutDistrict').value) return;
            const select = $('#checkoutWard');
            select.innerHTML += values.map(w => '<option value="' + escapeHtml(w.WardCode) + '">' + escapeHtml(w.WardName) + '</option>').join('');
            select.disabled = false;
        }
    }

    async function refreshCheckoutFee() {
        const version = ++shippingFeeVersion;
        checkoutShippingQuote = null;
        renderShippingTotal();
        const warehouse = String(state.branchId || '');
        const district = $('#checkoutDistrict').value;
        const ward = $('#checkoutWard').value;
        if (!warehouse || !district || !ward) return;
        const result = await api('/locations/calculate-fee', { method: 'POST', body: {
            warehouse_id: Number(warehouse), to_district_id: Number(district), to_ward_code: ward, weight: 500,
        } });
        if (version !== shippingFeeVersion || warehouse !== String(state.branchId) || district !== $('#checkoutDistrict').value
            || ward !== $('#checkoutWard').value) return;
        const fee = Number(result.shipping_fee);
        if (!Number.isFinite(fee) || fee < 0) throw new Error('Phí vận chuyển không hợp lệ.');
        checkoutShippingQuote = { warehouse, district, ward, fee, testMode: result.test_mode === true };
        renderShippingTotal();
    }

    document.addEventListener('change', event => {
        if (event.target.id === 'checkoutProvince') execute(() => changeCheckoutLocation('province'));
        else if (event.target.id === 'checkoutDistrict') execute(() => changeCheckoutLocation('district'));
        else if (event.target.id === 'checkoutWard') execute(refreshCheckoutFee);
    });

    /** Xóa người nhận khi đổi tài khoản hoặc đặt xong, tránh giữ dữ liệu cá nhân của phiên trước. */
    function resetCheckoutDetails() {
        shippingLocationVersion++;
        shippingFeeVersion++;
        checkoutShippingQuote = null;
        $('#checkoutProvince').value = '';
        resetLocationSelect('checkoutDistrict', 'Chọn quận / huyện');
        resetLocationSelect('checkoutWard', 'Chọn phường / xã');
        renderShippingTotal();
        $$('[data-delivery-field]').forEach((field) => {
            field.value = '';
        });
    }

    /** Đổi actor/đặt thành công thì bỏ khóa retry, tránh gắn một đơn mới với lần mua đã hoàn tất. */
    function forgetCheckoutAttempt() {
        checkoutAttempt = null;
        try {
            sessionStorage.removeItem(CHECKOUT_ATTEMPT_KEY);
        } catch {
            /* Trình duyệt chặn storage vẫn xóa khóa trong bộ nhớ. */
        }
    }

    /** Giữ cùng khóa cho cùng nội dung sau lỗi mạng; storage chỉ có hash SHA-256, không có người nhận. */
    async function checkoutKey(body, owner, epoch) {
        const canonical = JSON.stringify({
            ...body,
            items: [...body.items].sort((left, right) => left.product_id - right.product_id),
            delivery: {
                ...body.delivery,
                recipient_phone: body.delivery.recipient_phone.replace(/[\s()-]/g, ''),
            },
        });
        const persistent = Boolean(window.crypto?.subtle);
        const hash = persistent
            ? [...new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(canonical)))]
                  .map((byte) => byte.toString(16).padStart(2, '0'))
                  .join('')
            : canonical;
        if (epoch !== state.epoch || owner !== cartOwner()) {
            throw new DOMException('Tài khoản đặt hàng đã thay đổi.', 'AbortError');
        }
        if (!checkoutAttempt && persistent) {
            try {
                const saved = JSON.parse(sessionStorage.getItem(CHECKOUT_ATTEMPT_KEY));
                if (
                    saved?.owner === owner &&
                    /^[a-f0-9]{64}$/.test(saved.hash) &&
                    /^SF-WEB-[a-f0-9]{32}$/.test(saved.key)
                )
                    checkoutAttempt = saved;
            } catch {
                /* Metadata hỏng hoặc storage bị chặn thì tạo khóa ngẫu nhiên mới. */
            }
        }
        if (checkoutAttempt?.owner !== owner || checkoutAttempt.hash !== hash) {
            const random = crypto.getRandomValues(new Uint8Array(16));
            const key = 'SF-WEB-' + [...random].map((byte) => byte.toString(16).padStart(2, '0')).join('');
            checkoutAttempt = { owner, key, hash };
        }
        if (persistent) {
            try {
                sessionStorage.setItem(CHECKOUT_ATTEMPT_KEY, JSON.stringify(checkoutAttempt));
            } catch {
                /* HTTPS/localhost có SHA-256; nếu storage bị chặn thì vẫn bảo vệ retry trong phiên hiện tại. */
            }
        }
        return checkoutAttempt.key;
    }

    /** CUSTOMER tạo đơn có người nhận; lỗi 400/409 giữ giỏ, địa chỉ và chi nhánh để khách điều chỉnh. */
    async function createOrder() {
        if (!state.user) {
            openAuth('checkout');
            return;
        }
        if (!hasRole('CUSTOMER')) throw new Error('Dùng tài khoản khách hàng để đặt đơn.');
        if (state.cartLoading || state.cartRestoreFailed)
            throw new Error('Khôi phục giỏ và cập nhật giá trước khi đặt hàng.');
        if (!state.cart.size) throw new Error('Thêm ít nhất một sản phẩm vào giỏ.');
        if (!state.branchId) throw new Error('Chọn chi nhánh chuẩn bị đơn.');
        const district = $('#checkoutDistrict').value;
        const ward = $('#checkoutWard').value;
        if (!checkoutShippingQuote || checkoutShippingQuote.warehouse !== String(state.branchId)
            || checkoutShippingQuote.district !== district || checkoutShippingQuote.ward !== ward)
            throw new Error('Vui lòng chọn địa chỉ và chờ tính phí vận chuyển.');
        const streetAddress = $('#checkoutStreetAddress').value.trim();
        if (!streetAddress) throw new Error('Vui lòng nhập số nhà, tên đường.');
        const fullAddress = [streetAddress,
            $('#checkoutWard').selectedOptions[0].textContent,
            $('#checkoutDistrict').selectedOptions[0].textContent,
            $('#checkoutProvince').selectedOptions[0].textContent].join(', ');
        const body = {
            warehouse_id: Number(state.branchId),
            to_district_id: Number(district),
            to_ward_code: ward,
            shipping_fee: checkoutShippingQuote.fee,
            items: [...state.cart.values()].map((item) => ({
                product_id: item.product.id,
                quantity: item.quantity,
            })),
            delivery: {
                recipient_name: $('#delivery-name').value.trim(),
                recipient_phone: $('#delivery-phone').value.trim(),
                address: fullAddress,
                note: $('#delivery-note').value.trim() || null,
            },
        };
        const owner = cartOwner();
        const epoch = state.epoch;
        const idempotencyKey = await checkoutKey(body, owner, epoch);
        if (epoch !== state.epoch || owner !== cartOwner()) {
            throw new DOMException('Tài khoản đặt hàng đã thay đổi.', 'AbortError');
        }
        let result;
        try {
            result = await api('/orders', {
                method: 'POST',
                body,
                idempotencyKey,
            });
        } catch (error) {
            // Mất phản hồi không có nghĩa đặt thất bại; hướng dẫn thử lại cùng khóa thay vì tạo giỏ khác.
            if (error instanceof ApiError && error.status === 0) {
                error.message =
                    'Không kết nối được hệ thống; chưa rõ kết quả đặt hàng. ' +
                    'Hãy thử lại với cùng giỏ và thông tin, hoặc kiểm tra Đơn hàng của tôi trước khi đổi nội dung.';
            }
            throw error;
        }
        if (!Number.isSafeInteger(result?.id) || !result.order_code || !result.status) {
            throw new Error('Chưa nhận được thông tin đơn hợp lệ. Thử lại để tra lại cùng lần đặt hàng.');
        }
        forgetCheckoutAttempt();
        state.cart.clear();
        saveCart();
        resetCheckoutDetails();
        state.order = result;
        state.pages.orders = 0;
        $('#cart-dialog').close();
        renderCart();
        renderOrder();
        notify(
            'success',
            'Đơn ' +
                result.order_code +
                (result.status === 'PENDING'
                    ? ' đang chờ thanh toán. Kiểm tra thời hạn giữ hàng trong chi tiết đơn.'
                    : ' đã được ghi nhận: ' + (STATUS_LABELS[result.status] || result.status) + '.'),
            'HTTP 201 Created',
        );
        await activateView('shop', 'orders');
    }

    /** Dùng cùng một bảng cho tải tay và tải nền, giữ tổng số dòng/phân trang do backend trả. */
    function renderMyOrders(result) {
        state.myOrdersPage = result;
        $('#orders-rows').innerHTML = result.content
            .map(
                (order) =>
                    '<tr><td><strong class="mono">' +
                    escapeHtml(order.order_code) +
                    '</strong><span class="meta-line">' +
                    escapeHtml(warehouseName(order.warehouse_id)) +
                    ' · #' +
                    order.id +
                    '</span></td><td>' +
                    statusBadge(order.status) +
                    '<span class="meta-line mono">' +
                    escapeHtml(order.shipment?.tracking_code || 'Chưa có vận đơn') +
                    '</span></td>' +
                    '<td class="align-right price">' +
                    amount(order.total_amount) +
                    '</td><td>' +
                    '<button class="button secondary small" type="button" data-action="view-order" data-id="' +
                    order.id +
                    '">Xem</button></td></tr>',
            )
            .join('');
        if (!result.content.length)
            emptyRows('orders', 4, 'Bạn chưa có đơn hàng. Khám phá cửa hàng để đặt đơn đầu tiên.');
        $('#orders-count').textContent = integer(result.total_elements) + ' đơn';
        renderPager('orders', result);
    }

    /** Lịch sử chỉ gọi /my cho CUSTOMER; thao tác tay luôn ưu tiên hơn lần đồng bộ nền đang chạy. */
    async function loadOrders() {
        if (!hasRole('CUSTOMER')) return;
        invalidateOrderRefresh();
        loadingRows('orders', 4, 'Đang tải đơn hàng của bạn…');
        $('#orders-pagination').replaceChildren();
        try {
            const result = await api('/orders/my', {
                channel: 'my-orders',
                query: { page: state.pages.orders, size: 8 },
            });
            renderMyOrders(result);
            if (!state.order && result.content.length) state.order = result.content[0];
            else if (state.order) {
                const updated = result.content.find((order) => order.id === state.order.id);
                if (updated) state.order = updated;
            }
            await hydrateOrderProducts();
            renderOrder();
            orderRefreshFailures = 0;
        } catch (error) {
            if (error.name !== 'AbortError') emptyRows('orders', 4, 'Chưa tải được đơn hàng. Vui lòng làm mới.');
            throw error;
        } finally {
            scheduleOrderRefresh();
        }
    }

    /** Tab và dropdown dùng chung bộ lọc; mỗi lần bấm vẫn để backend áp dụng phạm vi kho. */
    function syncQueueStatusTabs() {
        $$('[data-queue-status]').forEach((button) =>
            button.setAttribute('aria-pressed', String(button.dataset.queueStatus === $('#queue-status').value)),
        );
    }

    /** Lọc trạng thái tức thì, giữ kho đang chọn và xóa chi tiết cũ để tránh nhầm đơn cần xử lý. */
    async function applyQueueStatus(status) {
        if (!isOperator() || ![...$('#queue-status').options].some((option) => option.value === status)) return;
        $('#queue-status').value = status;
        syncQueueStatusTabs();
        state.pages.queue = 0;
        state.order = null;
        renderOrder();
        await loadQueue();
    }

    /** Work queue lấy scope và tổng số dòng từ backend, staff không tự lọc sau phân trang. */
    async function loadQueue() {
        if (!isOperator()) return;
        syncQueueStatusTabs();
        loadingRows('queue', 4, 'Đang tải đơn trong phạm vi được phép…');
        $('#queue-count').textContent = '—';
        $('#queue-pagination').replaceChildren();
        try {
            const result = await api('/orders', {
                channel: 'queue',
                query: {
                    page: state.pages.queue,
                    size: 8,
                    warehouseId: $('#queue-warehouse').value,
                    status: $('#queue-status').value,
                },
            });
            $('#queue-rows').innerHTML = result.content
                .map(
                    (order) =>
                        '<tr><td><strong class="mono">' +
                        escapeHtml(order.order_code) +
                        '</strong><span class="meta-line">#' +
                        order.id +
                        ' · Khách #' +
                        order.customer_id +
                        '</span></td><td><strong>' +
                        escapeHtml(order.warehouse_name) +
                        '</strong><span class="meta-line">' +
                        statusBadge(order.status) +
                        '</span></td><td class="align-right price">' +
                        amount(order.total_amount) +
                        '</td><td><div class="order-actions"><button class="button secondary small" type="button" data-action="view-order" data-id="' +
                        order.id +
                        '">Chi tiết</button>' +
                        fulfillmentButton(order) +
                        '</div></td></tr>',
                )
                .join('');
            if (!result.content.length) emptyRows('queue', 4, 'Chưa có đơn phù hợp trong phạm vi kho của bạn.');
            $('#queue-count').textContent = integer(result.total_elements) + ' đơn';
            renderPager('queue', result);
            if (!state.order && result.content.length) await lookupOrder(result.content[0].id);
        } catch (error) {
            if (error.name !== 'AbortError') emptyRows('queue', 4, 'Chưa tải được danh sách xử lý. Vui lòng làm mới.');
            throw error;
        }
    }

    /** Hàng work queue và chi tiết dùng cùng ánh xạ bước tiếp theo; không cho bỏ qua trạng thái. */
    function fulfillmentButton(order) {
        if (!isOperator()) return '';
        const step = {
            CONFIRMED: ['pack', '📦 Đóng gói PACKED'],
            PACKED: ['ship', '🚚 Giao hàng SHIPPED'],
            SHIPPED: ['deliver', '✅ Hoàn tất DELIVERED'],
            DELIVERED: ['return', '↩️ Trả hàng & hoàn kho RETURNED'],
        }[order.status];
        return step
            ? '<button type="button" class="button ' +
                  (step[0] === 'return' ? 'danger' : 'primary') +
                  ' small" data-action="operate-order" data-operation="' +
                  step[0] +
                  '" data-id="' +
                  order.id +
                  '">' +
                  step[1] +
                  '</button>'
            : '';
    }

    /** DTO item chỉ có product_id; bổ sung tên từ catalog thật mà không thay giá snapshot của đơn. */
    async function hydrateOrderProducts(order = state.order) {
        const ids = [...new Set((order?.items || []).map((item) => item.product_id))];
        await Promise.all(
            ids
                .filter((id) => !state.products.has(id))
                .map(async (id) => {
                    const product = await api('/products/' + id, { anonymous: true, channel: 'order-product-' + id });
                    state.products.set(product.id, product);
                }),
        );
    }

    /** Xóa chi tiết cũ trước tra cứu để lỗi 403/404 không để lại thông tin của đơn trước đó. */
    async function lookupOrder(id) {
        invalidateOrderRefresh();
        state.order = null;
        renderOrder();
        try {
            state.order = await api('/orders/' + id, { channel: 'order-detail' });
            $('#order-id').value = String(state.order.id);
            await hydrateOrderProducts();
            renderOrder();
        } finally {
            scheduleOrderRefresh();
        }
    }

    /** Timeline biểu diễn trạng thái thật, không tự suy ra đã giao từ timestamp hoặc countdown. */
    function orderTimeline(order) {
        const steps = ['PENDING', 'CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED'];
        const active = steps.indexOf(order.status);
        if (active < 0) return '';
        return (
            '<div class="order-timeline">' +
            steps
                .map(
                    (status, index) =>
                        '<span class="timeline-step ' +
                        (index <= active ? 'done' : '') +
                        '">' +
                        escapeHtml(STATUS_LABELS[status]) +
                        '</span>',
                )
                .join('') +
            '</div>'
        );
    }

    /** Người nhận chỉ xuất hiện trong chi tiết đã được backend kiểm tra quyền; mọi chuỗi đều được escape. */
    function renderDeliveryDetails(order) {
        const delivery = order.delivery;
        if (!delivery) {
            return '<p class="order-delivery-legacy">Đơn lịch sử chưa có thông tin nhận hàng.</p>';
        }
        return (
            '<section class="order-delivery" aria-label="Thông tin nhận hàng"><h4>Thông tin nhận hàng</h4>' +
            '<div class="delivery-contact"><strong>' +
            escapeHtml(delivery.recipient_name) +
            '</strong><span>' +
            escapeHtml(delivery.recipient_phone) +
            '</span></div><p class="delivery-address">' +
            escapeHtml(delivery.address) +
            '</p>' +
            (delivery.note
                ? '<p class="delivery-note"><span>Ghi chú: </span>' + escapeHtml(delivery.note) + '</p>'
                : '') +
            '<span class="delivery-shipping">Phí giao hàng GHN: ' + amount(order.shipping_fee || 0) + '</span></section>'
        );
    }

    /** Chi tiết chung gồm người nhận và shipment; khách chỉ có thanh toán/hủy pending, vận hành có bước kế tiếp. */
    function renderOrder() {
        const order = state.order;
        const detail = state.view === 'portal' ? $('#queue-detail') : $('#order-detail');
        const other = state.view === 'portal' ? $('#order-detail') : $('#queue-detail');
        other.replaceChildren();
        if (!order) {
            detail.innerHTML =
                '<div class="empty-state">' +
                icon('order') +
                '<h3>Mỗi đơn hàng, một hành trình.</h3><p>Chọn một đơn để xem mặt hàng và tiến trình giao hàng.</p></div>';
            return;
        }
        const canCancel =
            (hasRole('CUSTOMER') && order.status === 'PENDING') ||
            (hasRole('ADMIN', 'MANAGER') && ['PENDING', 'CONFIRMED', 'PACKED'].includes(order.status));
        const items = order.items
            .map((item) => {
                const product = state.products.get(item.product_id);
                return (
                    '<div class="order-item"><div><strong>' +
                    escapeHtml(product ? cartProductName(saleSku(product)) : 'Sản phẩm #' + item.product_id) +
                    '</strong><span class="meta-line">' +
                    integer(item.quantity) +
                    ' × ' +
                    amount(item.unit_price) +
                    '</span></div><strong class="price">' +
                    amount(item.line_total) +
                    '</strong>' +
                    (hasRole('CUSTOMER') && order.status === 'DELIVERED'
                        ? '<button type="button" class="button secondary small" data-action="review-product" data-id="' +
                          order.id + '" data-product-id="' + item.product_id + '">Đánh giá sản phẩm / dịch vụ</button>' : '') +
                    '</div>'
                );
            })
            .join('');
        const shipment = order.shipment;
        detail.innerHTML =
            '<div class="order-detail-head"><div><span class="eyebrow">ĐƠN HÀNG #' +
            order.id +
            '</span><h3 class="mono">' +
            escapeHtml(order.order_code) +
            '</h3></div>' +
            statusBadge(order.status) +
            '</div>' +
            orderTimeline(order) +
            '<div class="order-info"><div><span>Chi nhánh chuẩn bị</span><strong>' +
            escapeHtml(warehouseName(order.warehouse_id)) +
            '</strong></div><div><span>Đặt lúc</span>' +
            escapeHtml(dateTime(order.created_at)) +
            '</div><div><span>Thời hạn giữ hàng</span>' +
            escapeHtml(dateTime(order.reservation_expires_at)) +
            '</div><div><span>Cập nhật gần nhất</span>' +
            escapeHtml(dateTime(order.updated_at)) +
            '</div></div>' +
            renderDeliveryDetails(order) +
            (order.status === 'PENDING'
                ? '<div class="countdown">' +
                  icon('clock') +
                  '<span data-expires="' +
                  escapeHtml(order.reservation_expires_at) +
                  '"></span></div>'
                : '') +
            (shipment
                ? '<div class="shipment-info">Vận đơn · ' +
                  escapeHtml(STATUS_LABELS[shipment.status] || 'Chuẩn bị') +
                  '<strong class="mono">' +
                  escapeHtml(shipment.tracking_code) +
                  '</strong>' +
                  (['SHIPPED', 'DELIVERED'].includes(order.status)
                    ? '<a class="button secondary" target="_blank" rel="noopener noreferrer" href="https://donhang.ghn.vn/?order_code=' +
                      encodeURIComponent(shipment.tracking_code) + '">Tra cứu hành trình GHN</a>' : '') +
                  '<span>Xuất giao: ' +
                  escapeHtml(dateTime(shipment.shipped_at)) +
                  '</span><br>' +
                  '<span>Giao thành công: ' +
                  escapeHtml(dateTime(shipment.delivered_at)) +
                  '</span></div>'
                : '<p class="meta-line">Mã vận đơn sẽ hiển thị khi chi nhánh chuẩn bị hàng.</p>') +
            '<div class="order-items">' +
            items +
            '</div><div class="order-total"><span>Tổng tiền</span><strong>' +
            amount(order.total_amount) +
            '</strong></div><div class="order-actions">' +
            (isOperator() && order.status === 'PACKED'
                ? '<label class="tracking-field">Mã vận đơn (tùy chọn)<input data-tracking-code maxlength="100" ' +
                  'pattern="[A-Za-z0-9._-]+" placeholder="Để trống để dùng mã tự sinh"></label>'
                : '') +
            fulfillmentButton(order) +
            (isOperator() && order.status === 'PACKED'
                ? '<button type="button" class="button primary" data-action="operate-order" data-operation="ghn-ship" data-id="' +
                  order.id + '">🚚 Bắn đơn sang GHN (Tự động lấy mã vận đơn)</button>' : '') +
            (hasRole('CUSTOMER') && order.status === 'PENDING'
                ? '<button type="button" class="button primary" data-action="pay-order" data-id="' +
                  order.id +
                  '">' +
                  icon('card') +
                  'Xác nhận thanh toán mô phỏng</button>'
                : '') +
            (hasRole('CUSTOMER') && order.status === 'PENDING'
                ? '<button type="button" class="button secondary" data-action="vnpay-order" data-id="' +
                  order.id + '">' + icon('card') + 'Thanh toán qua VNPay (Sandbox)</button>'
                : '') +
            (canCancel
                ? '<button type="button" class="button danger" data-action="cancel-order" data-id="' +
                  order.id +
                  '">Hủy đơn' +
                  (order.status === 'PENDING' ? ' & nhả hàng' : ' & hoàn kho') +
                  '</button>'
                : '') +
            '<button type="button" class="button secondary" data-action="view-order" data-id="' +
            order.id +
            '">' +
            icon('refresh') +
            'Tải lại</button></div>' +
            (hasRole('CUSTOMER')
                ? '<p class="meta-line" data-order-sync>Tự cập nhật trạng thái khi bạn đang xem đơn.</p>'
                : '');
        updateCountdown();
    }

    /** Countdown chỉ nhắc hạn và kích hoạt GET; EXPIRED cùng thao tác nhả hàng vẫn do backend quyết định. */
    function updateCountdown() {
        $$('[data-expires]').forEach((element) => {
            const remaining = Math.max(0, Math.ceil((new Date(element.dataset.expires).getTime() - Date.now()) / 1000));
            element.textContent =
                remaining > 0
                    ? 'Còn ' +
                      Math.floor(remaining / 60) +
                      ' phút ' +
                      String(remaining % 60).padStart(2, '0') +
                      ' giây giữ hàng'
                    : 'Đã đến hạn giữ hàng. Đang kiểm tra trạng thái từ hệ thống…';
            if (
                remaining === 0 &&
                canAutoRefreshOrders() &&
                !orderRefreshFailures &&
                Date.now() - lastOrderRefreshAttempt >= EXPIRED_ORDER_REFRESH_MS
            ) {
                scheduleOrderRefresh(0);
            }
        });
    }

    /** Hủy và nhận trả yêu cầu xác nhận rõ cả đơn và tác động hoàn kho/hoàn tiền. */
    function confirmOrderAction(id, action) {
        state.pendingMutation = { id, action, epoch: state.epoch };
        // Dialog dùng chung với catalog; đặt lại nhãn để không giữ chữ Xóa danh mục từ lần trước.
        $('#confirm-submit').textContent = 'Xác nhận';
        $('#confirm-submit').className = 'button danger';
        $('#confirm-title').textContent = action === 'return' ? 'Nhận trả toàn bộ đơn #' + id : 'Hủy đơn #' + id;
        $('#confirm-message').textContent =
            action === 'return'
                ? 'Xác nhận đã nhận lại toàn bộ hàng của đơn. Hệ thống sẽ hoàn kho và hoàn tiền mô phỏng. Chỉ thực hiện sau khi kiểm tra hàng.'
                : 'Đơn sẽ được hủy. Hệ thống giải phóng hàng đang giữ hoặc hoàn kho và hoàn tiền mô phỏng theo trạng thái hiện tại.';
        openDialog('confirm-dialog');
    }

    async function payWithVNPay(id) {
        const result = await api('/payments/vnpay/create', { method: 'POST', body: { order_id: id } });
        const paymentUrl = new URL(result.payment_url);
        if (paymentUrl.protocol !== 'https:' || paymentUrl.hostname !== 'sandbox.vnpayment.vn') {
            throw new Error('Đường dẫn VNPay Sandbox không hợp lệ.');
        }
        window.location.assign(paymentUrl.href);
    }

    /** Mọi hành động gửi POST thật; không trừ kho tại client hoặc ship lần hai. */
    async function mutateOrder(id, action) {
        invalidateOrderRefresh();
        const segment = action === 'pay' ? 'payment-simulations/confirm' : action;
        const path = '/orders/' + id + '/' + segment;
        let body;
        if (action === 'ship') {
            const input = state.order?.id === id ? $('[data-tracking-code]') : null;
            if (input && !input.reportValidity()) return;
            body = input?.value.trim() ? { tracking_code: input.value.trim() } : {};
        }
        const order = await api(path, { method: 'POST', body });
        state.order = order;
        await hydrateOrderProducts();
        renderOrder();
        const messages = {
            pay: 'Thanh toán mô phỏng đã được xác nhận.',
            cancel: 'Đã hủy đơn và trả lại tồn kho phù hợp.',
            pack: 'Đã đóng gói và chuẩn bị vận đơn.',
            ship: 'Đã xuất giao. Mã vận đơn: ' + (order.shipment?.tracking_code || ''),
            'ghn-ship': 'Đã xuất giao qua GHN. Mã vận đơn: ' + (order.shipment?.tracking_code || ''),
            deliver: 'Đã xác nhận giao hàng thành công.',
            return: 'Đã nhận trả toàn bộ đơn, hoàn kho và hoàn tiền mô phỏng.',
        };
        if (action === 'ghn-ship' && order.shipment?.tracking_code?.startsWith('GHN_HAN_')) {
            messages[action] = 'Đã xuất giao với mã GHN mô phỏng: ' + order.shipment.tracking_code;
        }
        notify(
            'success',
            order.status === 'EXPIRED' ? 'Đơn đã hết hạn; hàng được giải phóng và không thu tiền.' : messages[action],
            'HTTP 200 OK',
        );
        if (hasRole('CUSTOMER')) await loadOrders();
        else if (isOperator()) {
            if (action === 'ghn-ship') {
                $('#queue-status').value = 'SHIPPED';
                state.pages.queue = 0;
                syncQueueStatusTabs();
            }
            await loadQueue();
        }
    }

    /** Khung tải giữ số cột thật và loại dòng minh họa khỏi trình đọc màn hình. */
    function loadingRows(name, columns, message) {
        const count = $('#' + name + '-count');
        if (count) count.textContent = '—';
        $('#' + name + '-pagination')?.replaceChildren();
        $('#' + name + '-rows').innerHTML =
            Array.from(
                { length: 5 },
                () => `
            <tr class="table-skeleton-row" aria-hidden="true">
                ${Array.from(
                    { length: columns },
                    (_, index) => `
                    <td>
                        <span class="skeleton skeleton-table-line ${index === 0 ? 'wide' : ''}"></span>
                        ${index === 0 ? '<span class="skeleton skeleton-table-line short"></span>' : ''}
                    </td>`,
                ).join('')}
            </tr>`,
            ).join('') +
            `<tr class="skeleton-status"><td colspan="${columns}"><span class="sr-only" role="status">${escapeHtml(message)}</span></td></tr>`;
    }

    /** Bảng trống/lỗi có thông báo riêng, không để số liệu cũ trông như kết quả mới. */
    function emptyRows(name, columns, message) {
        $('#' + name + '-rows').innerHTML =
            '<tr><td colspan="' + columns + '" class="empty-cell">' + escapeHtml(message) + '</td></tr>';
    }

    /** Phân trang sử dụng metadata thật do API trả về, mỗi bảng có state độc lập. */
    function renderPager(name, result) {
        const start = result.total_elements ? result.page * result.size + 1 : 0;
        const end = Math.min((result.page + 1) * result.size, result.total_elements);
        $('#' + name + '-pagination').innerHTML =
            '<span>' +
            integer(start) +
            '–' +
            integer(end) +
            ' trên ' +
            integer(result.total_elements) +
            ' kết quả</span><div class="pagination-actions">' +
            '<button class="button secondary" type="button" data-page="' +
            name +
            '" data-direction="-1" ' +
            (result.page === 0 ? 'disabled' : '') +
            '>Trước</button><span>' +
            (result.page + 1) +
            ' / ' +
            Math.max(1, result.total_pages) +
            '</span>' +
            '<button class="button secondary" type="button" data-page="' +
            name +
            '" data-direction="1" ' +
            (result.last ? 'disabled' : '') +
            '>Sau</button></div>';
    }

    /** Ô sản phẩm lấy tên/SKU thật, escape trước khi dựng bảng vận hành. */
    function productCell(product) {
        return (
            '<div class="product-cell"><span class="product-symbol">' +
            icon('box') +
            '</span><div><strong>' +
            escapeHtml(product.name || product.product_name) +
            '</strong><span class="meta-line mono">' +
            escapeHtml(product.sku || product.product_sku || '#' + product.product_id) +
            '</span></div></div>'
        );
    }

    /** Tra tồn kho theo bộ lọc thật, riêng staff phải chọn kho; các thẻ chỉ cộng trang hiện tại. */
    async function loadInventory() {
        if (!hasRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF')) return;
        loadingRows('inventory', 6, 'Đang tải tồn kho…');
        ['stock-available', 'stock-reserved', 'stock-physical'].forEach((id) => {
            $('#' + id).textContent = '—';
        });
        try {
            const result = await api('/inventories', {
                channel: 'inventory',
                query: {
                    warehouseId: $('#inventory-warehouse').value,
                    productId: $('#inventory-product').value,
                    page: state.pages.inventory,
                    size: 10,
                },
            });
            $('#inventory-rows').innerHTML = result.content
                .map(
                    (stock) =>
                        '<tr class="' +
                        (stock.available_quantity <= 5 ? 'inventory-warning' : '') +
                        '"><td><strong>' +
                        escapeHtml(
                            state.products.has(stock.product_id)
                                ? cartProductName(saleSku(state.products.get(stock.product_id)))
                                : stock.product_name,
                        ) +
                        '</strong><span class="meta-line mono">#' +
                        stock.product_id +
                        ' · Tồn kho #' +
                        stock.id +
                        '</span>' +
                        (stock.available_quantity <= 5
                            ? '<span class="stock-alert-badge">' +
                              icon('alert') +
                              (stock.available_quantity === 0 ? 'Hết hàng' : 'Sắp hết') +
                              '</span>'
                            : '') +
                        '</td><td>' +
                        escapeHtml(stock.warehouse_name) +
                        '</td><td class="align-right"><span class="' +
                        (stock.available_quantity <= 10 ? 'low-number' : 'price') +
                        '">' +
                        integer(stock.available_quantity) +
                        '</span></td><td class="align-right">' +
                        integer(stock.reserved_quantity) +
                        '</td><td class="align-right price">' +
                        integer(stock.physical_quantity) +
                        '</td><td><span class="subtle">' +
                        escapeHtml(dateTime(stock.updated_at)) +
                        '</span></td></tr>',
                )
                .join('');
            if (!result.content.length)
                emptyRows('inventory', 6, 'Chưa có tồn kho phù hợp. Nhập kho để tạo số tồn ban đầu.');
            $('#stock-available').textContent = integer(
                result.content.reduce((sum, row) => sum + row.available_quantity, 0),
            );
            $('#stock-reserved').textContent = integer(
                result.content.reduce((sum, row) => sum + row.reserved_quantity, 0),
            );
            $('#stock-physical').textContent = integer(
                result.content.reduce((sum, row) => sum + row.physical_quantity, 0),
            );
            $('#inventory-count').textContent = integer(result.total_elements) + ' dòng tồn kho';
            renderPager('inventory', result);
        } catch (error) {
            if (error.name !== 'AbortError') {
                emptyRows('inventory', 6, 'Không tải được tồn kho. Kiểm tra kho được phân công hoặc quyền truy cập.');
                $('#inventory-pagination').innerHTML = '';
                $('#inventory-count').textContent = '—';
            }
            throw error;
        }
    }
    /** Ledger là bảng chỉ đọc, thể hiện nổi bật snapshot physical trước/sau, actor và tham chiếu. */
    async function loadLedger() {
        if (!hasRole('ADMIN', 'MANAGER')) return;
        loadingRows('ledger', 5, 'Đang tải lịch sử kiểm toán…');
        try {
            const result = await api('/inventories/movements', {
                channel: 'ledger',
                query: { inventoryId: $('#ledger-inventory').value, page: state.pages.ledger, size: 10 },
            });
            $('#ledger-rows').innerHTML = result.content
                .map(
                    (movement) =>
                        '<tr><td><strong>' +
                        escapeHtml(MOVEMENT_LABELS[movement.type] || movement.type) +
                        '</strong><span class="meta-line mono">' +
                        escapeHtml(movement.type) +
                        '</span><span class="meta-line">Tồn kho #' +
                        movement.inventory_id +
                        ' · Biến động #' +
                        movement.id +
                        '</span></td><td class="align-right price">' +
                        integer(movement.quantity) +
                        '</td><td><div class="balance-change"><span>' +
                        integer(movement.balance_before) +
                        '</span>' +
                        icon('arrow') +
                        '<span class="after">' +
                        integer(movement.balance_after) +
                        '</span></div></td><td><strong>' +
                        escapeHtml(movement.reference_type || 'NHẬP HÀNG THỦ CÔNG') +
                        (movement.reference_id ? ' #' + movement.reference_id : '') +
                        '</strong><span class="meta-line">Người thực hiện #' +
                        movement.performed_by +
                        '</span></td><td><span class="subtle">' +
                        escapeHtml(dateTime(movement.created_at)) +
                        '</span><span class="meta-line ledger-note">' +
                        escapeHtml(movement.note || '—') +
                        '</span></td></tr>',
                )
                .join('');
            if (!result.content.length) emptyRows('ledger', 5, 'Chưa có biến động phù hợp với bộ lọc.');
            $('#ledger-count').textContent = integer(result.total_elements) + ' biến động';
            renderPager('ledger', result);
        } catch (error) {
            if (error.name !== 'AbortError') emptyRows('ledger', 5, 'Chưa tải được sổ cái. Vui lòng làm mới.');
            throw error;
        }
    }
    /** Nhập kho gửi số lượng dương/ghi chú; sau thành công tra đúng cặp product/warehouse vừa nhập. */
    async function stockIn(form) {
        const data = Object.fromEntries(new FormData(form));
        const body = {
            product_id: Number(data.product_id),
            warehouse_id: Number(data.warehouse_id),
            quantity: Number(data.quantity),
            note: data.note.trim() || null,
        };
        const stock = await api('/inventories/stock-in', { method: 'POST', body });
        $('#inventory-warehouse').value = String(stock.warehouse_id);
        $('#inventory-product').value = String(stock.product_id);
        state.pages.inventory = 0;
        notify(
            'success',
            'Đã nhập ' +
                integer(body.quantity) +
                ' sản phẩm. Tồn khả dụng mới: ' +
                integer(stock.available_quantity) +
                '.',
            'HTTP 201 Created',
            '/api/v1/inventories/stock-in',
        );
        const operations = [loadInventory()];
        if (hasRole('ADMIN', 'MANAGER')) operations.push(loadLedger());
        await Promise.all(operations);
    }

    /** Order-summary không có bộ lọc ngày; ghi rõ thống kê mọi thời gian để không nhầm với revenue. */
    async function loadSummary() {
        $('#order-summary').innerHTML =
            Array.from(
                { length: 4 },
                () => `
            <div class="report-metric" aria-hidden="true">
                <span class="skeleton skeleton-line"></span>
                <span class="skeleton skeleton-card-price"></span>
            </div>
        `,
            ).join('') + '<span class="sr-only" role="status">Đang tổng hợp đơn hàng…</span>';
        $('#summary-statuses').replaceChildren();
        const result = await api('/reports/order-summary', { channel: 'summary' });
        const groups = new Map(result.map((row) => [row.order_status, row]));
        const total = result.reduce((sum, row) => sum + row.total_count, 0);
        const confirmed = result
            .filter((row) => ['CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED'].includes(row.order_status))
            .reduce((sum, row) => sum + row.total_count, 0);
        const closed = (groups.get('CANCELLED')?.total_count || 0) + (groups.get('EXPIRED')?.total_count || 0);
        $('#order-summary').innerHTML = [
            ['Tổng đơn hàng', total],
            ['Đã xác nhận / thực hiện', confirmed],
            ['Đang chờ thanh toán', groups.get('PENDING')?.total_count || 0],
            ['Đã hủy / hết hạn', closed],
        ]
            .map(
                ([label, value]) =>
                    '<div class="report-metric"><span>' +
                    label +
                    '</span><strong>' +
                    integer(value) +
                    '</strong><p>Tổng hợp mọi thời gian</p></div>',
            )
            .join('');
        $('#summary-statuses').innerHTML = Object.keys(STATUS_LABELS)
            .map((status) => {
                const row = groups.get(status);
                return (
                    '<div class="summary-chip">' +
                    statusBadge(status) +
                    '<strong>' +
                    integer(row?.total_count || 0) +
                    '</strong><span class="subtle">' +
                    amount(row?.total_amount || 0) +
                    '</span></div>'
                );
            })
            .join('');
    }
    /** Kho/ngưỡng chỉ áp dụng cho báo cáo có tham số đó; Top Products tổng hợp toàn bộ kho. */
    function reportQuery() {
        return { fromDate: $('#report-from').value, toDate: $('#report-to').value };
    }

    /** Không giữ cột/thanh cũ khi đang tải hoặc khi lỗi; không vẽ dữ liệu bằng không thay cho dữ liệu thiếu. */
    function reportChartState(name, loading, message) {
        const chart = $('#' + name + '-chart');
        chart.innerHTML = loading
            ? `<div class="chart-loading" aria-hidden="true">
                   <span class="skeleton skeleton-line short"></span>
                   <div class="skeleton skeleton-chart"></div>
               </div>
               <span class="sr-only" role="status">${escapeHtml(message)}</span>`
            : `<p class="chart-empty">${escapeHtml(message)}</p>`;
    }

    /** Doanh thu mỗi cột thuộc một kỳ/kho của trang đang xem; chiều cao chỉ so sánh trong trang này. */
    function renderRevenueChart(rows, groupBy) {
        if (!rows.length) {
            reportChartState('revenue', false, 'Chưa có doanh thu để biểu diễn trong kỳ đã chọn.');
            return;
        }
        const maximum = Math.max(...rows.map((row) => Number(row.total_revenue)), 0);
        $('#revenue-chart').innerHTML = `
            <figure class="revenue-figure">
                <figcaption class="chart-heading">
                    <strong>So sánh doanh thu</strong>
                    <span>Dữ liệu trang hiện tại · ${groupBy === 'MONTH' ? 'theo tháng' : 'theo ngày'} / kho</span>
                </figcaption>
                <div class="revenue-chart-scroll" tabindex="0" aria-label="Biểu đồ doanh thu; cuộn ngang để xem đầy đủ">
                    <ol class="revenue-bars" style="--chart-columns: ${rows.length}">
                        ${rows
                            .map((row) => {
                                const period = groupBy === 'MONTH' ? row.period.slice(0, 7) : row.period;
                                const height =
                                    maximum > 0
                                        ? Math.max(0, Math.min(100, (Number(row.total_revenue) / maximum) * 100))
                                        : 0;
                                return `<li class="revenue-column">
                                <span class="chart-value" title="${escapeHtml(amount(row.total_revenue))}">${amount(row.total_revenue)}</span>
                                <div class="revenue-bar-stage" aria-hidden="true">
                                    <span class="revenue-bar" style="--bar-height: ${height}%"></span>
                                </div>
                                <strong class="chart-period">${escapeHtml(period)}</strong>
                                <span class="chart-warehouse" title="${escapeHtml(row.warehouse_name)}">${escapeHtml(row.warehouse_name)}</span>
                            </li>`;
                            })
                            .join('')}
                    </ol>
                </div>
            </figure>`;
    }

    /** Thanh tỷ trọng dùng tổng doanh thu các dòng của trang API này; bảng phía dưới giữ số liệu gốc. */
    function renderTopChart(result) {
        const rows = result.content;
        if (!rows.length) {
            reportChartState('top', false, 'Chưa có sản phẩm bán ra để so sánh trong kỳ đã chọn.');
            return;
        }
        const total = rows.reduce((sum, row) => sum + Number(row.total_revenue), 0);
        $('#top-chart').innerHTML = `
            <div class="chart-heading">
                <strong>Tỷ trọng doanh thu sản phẩm</strong>
                <span>So với tổng doanh thu của các sản phẩm trong trang hiện tại</span>
            </div>
            <ol class="top-chart-list">
                ${rows
                    .map((row, index) => {
                        const share =
                            total > 0 ? Math.max(0, Math.min(100, (Number(row.total_revenue) / total) * 100)) : 0;
                        const percent = numberFormat.format(Number(share.toFixed(1))) + '%';
                        return `<li class="top-chart-item">
                        <span class="top-chart-rank">${result.page * result.size + index + 1}</span>
                        <div class="top-chart-product">
                            <div class="top-chart-label">
                                <strong>${escapeHtml(row.product_name)}</strong>
                                <span>${percent}</span>
                            </div>
                            <div class="top-chart-track" role="progressbar"
                                aria-label="Tỷ trọng doanh thu của ${escapeHtml(row.product_name)} trong trang hiện tại"
                                aria-valuemin="0" aria-valuemax="100"
                                aria-valuenow="${share.toFixed(1)}" aria-valuetext="${percent}">
                                <span style="--bar-width: ${share}%"></span>
                            </div>
                            <div class="top-chart-detail">
                                <span>${escapeHtml(row.product_sku)} · ${integer(row.total_quantity_sold)} đã bán</span>
                                <strong>${amount(row.total_revenue)}</strong>
                            </div>
                        </div>
                    </li>`;
                    })
                    .join('')}
            </ol>`;
    }
    /** Revenue nhóm theo DAY/MONTH và kho, dùng tiền snapshot backend và metadata phân trang thật. */
    async function loadRevenue() {
        loadingRows('revenue', 4, 'Đang tổng hợp doanh thu…');
        reportChartState('revenue', true, 'Đang tải biểu đồ doanh thu…');
        $('#revenue-pagination').replaceChildren();
        $('#revenue-count').textContent = '—';
        const groupBy = $('#report-group').value;
        try {
            const result = await api('/reports/revenue', {
                channel: 'revenue',
                query: {
                    ...reportQuery(),
                    warehouseId: $('#report-warehouse').value,
                    groupBy,
                    page: state.pages.revenue,
                    size: 8,
                },
            });
            $('#revenue-rows').innerHTML = result.content
                .map((row) => {
                    const period = groupBy === 'MONTH' ? row.period.slice(0, 7) : row.period;
                    return (
                        '<tr><td class="mono">' +
                        escapeHtml(period) +
                        '</td><td>' +
                        escapeHtml(row.warehouse_name) +
                        '</td><td class="align-right">' +
                        integer(row.total_orders) +
                        '</td><td class="align-right price">' +
                        amount(row.total_revenue) +
                        '</td></tr>'
                    );
                })
                .join('');
            if (!result.content.length)
                emptyRows('revenue', 4, 'Chưa có doanh thu trong kỳ. Thanh toán một đơn để xem báo cáo.');
            $('#revenue-count').textContent = integer(result.total_elements) + ' nhóm kỳ / kho';
            renderPager('revenue', result);
            renderRevenueChart(result.content, groupBy);
        } catch (error) {
            if (error.name !== 'AbortError') {
                emptyRows('revenue', 4, 'Chưa tải được doanh thu. Kiểm tra bộ lọc rồi thử lại.');
                reportChartState('revenue', false, 'Chưa tải được biểu đồ doanh thu. Vui lòng thử lại.');
            }
            throw error;
        }
    }
    /** Báo cáo sản phẩm bán chạy theo doanh thu giảm dần, không dùng giá catalog mới để tính lại. */
    async function loadTop() {
        loadingRows('top', 4, 'Đang tải sản phẩm bán chạy…');
        reportChartState('top', true, 'Đang tải biểu đồ sản phẩm bán chạy…');
        $('#top-pagination').replaceChildren();
        $('#top-count').textContent = '—';
        try {
            const result = await api('/reports/top-products', {
                channel: 'top',
                query: { ...reportQuery(), page: state.pages.top, size: 8 },
            });
            $('#top-rows').innerHTML = result.content
                .map(
                    (row) =>
                        '<tr><td>' +
                        productCell(row) +
                        '</td><td>' +
                        escapeHtml(row.category_name) +
                        '</td><td class="align-right">' +
                        integer(row.total_quantity_sold) +
                        '</td><td class="align-right price">' +
                        amount(row.total_revenue) +
                        '</td></tr>',
                )
                .join('');
            if (!result.content.length) emptyRows('top', 4, 'Chưa có sản phẩm bán ra trong khoảng ngày đã chọn.');
            $('#top-count').textContent = integer(result.total_elements) + ' sản phẩm';
            renderPager('top', result);
            renderTopChart(result);
        } catch (error) {
            if (error.name !== 'AbortError') {
                emptyRows('top', 4, 'Chưa tải được sản phẩm bán chạy. Kiểm tra bộ lọc rồi thử lại.');
                reportChartState('top', false, 'Chưa tải được biểu đồ sản phẩm bán chạy. Vui lòng thử lại.');
            }
            throw error;
        }
    }
    /** Low stock dựa vào available_quantity, hiển thị thêm reserved và physical để ra quyết định nhập hàng. */
    async function loadLow() {
        loadingRows('low', 5, 'Đang kiểm tra hàng sắp hết…');
        const result = await api('/reports/low-stock', {
            channel: 'low',
            query: {
                warehouseId: $('#report-warehouse').value,
                threshold: $('#report-threshold').value,
                page: state.pages.low,
                size: 8,
            },
        });
        $('#low-rows').innerHTML = result.content
            .map(
                (row) =>
                    '<tr><td>' +
                    productCell(row) +
                    '</td><td>' +
                    escapeHtml(row.warehouse_name) +
                    '</td><td class="align-right"><span class="low-number">' +
                    integer(row.available_quantity) +
                    '</span></td><td class="align-right">' +
                    integer(row.reserved_quantity) +
                    '</td><td class="align-right price">' +
                    integer(row.physical_quantity) +
                    '</td></tr>',
            )
            .join('');
        if (!result.content.length) emptyRows('low', 5, 'Không có dòng tồn kho dưới ngưỡng cảnh báo đã chọn.');
        $('#low-count').textContent = integer(result.total_elements) + ' cảnh báo';
        renderPager('low', result);
    }
    /** Tổng hợp chạy độc lập, một báo cáo lỗi không che mất các bảng đã thành công. */
    async function loadReports() {
        if (!hasRole('ADMIN', 'MANAGER')) return;
        const names = ['summary', 'revenue', 'top', 'low'];
        const results = await Promise.allSettled([loadSummary(), loadRevenue(), loadTop(), loadLow()]);
        results.forEach((result, index) => {
            if (result.status === 'rejected' && result.reason.name !== 'AbortError') {
                const name = names[index];
                if (name === 'summary') {
                    $('#order-summary').innerHTML = '<div class="inline-info">Chưa tải được tổng hợp đơn hàng.</div>';
                    $('#summary-statuses').innerHTML = '';
                } else {
                    emptyRows(name, name === 'low' ? 5 : 4, 'Không tải được báo cáo. Kiểm tra bộ lọc rồi thử lại.');
                    $('#' + name + '-pagination').innerHTML = '';
                }
            }
        });
        const failure = results.find((result) => result.status === 'rejected' && result.reason.name !== 'AbortError');
        if (failure) throw failure.reason;
    }

    /** Bảng quản trị cho ADMIN có cả sản phẩm ngừng bán, không ảnh hưởng kệ ACTIVE của khách. */
    /** Badge trạng thái có nhãn thật; INACTIVE là sản phẩm được ẩn khỏi cửa hàng. */
    function catalogStatus(product) {
        const active = product.status === 'ACTIVE';
        return `<span class="catalog-status ${active ? 'is-active' : 'is-inactive'}">
            <span aria-hidden="true"></span>${active ? 'Đang bán' : 'Đã ẩn'}
        </span>`;
    }

    /** Ảnh quản trị chỉ dùng URL đã lưu; thiếu/lỗi ảnh có biểu tượng nhỏ và không chặn thao tác. */
    function catalogMedia(product, detail = false) {
        const image = safeProductImageUrl(product.image_url);
        return `<span class="catalog-product-media ${detail ? 'catalog-detail-media' : ''}">
            ${icon('box')}
            ${image ? `<img src="${escapeHtml(image)}" alt="${escapeHtml(product.name)}" loading="lazy" data-catalog-image />` : ''}
        </span>`;
    }

    /** Mỗi dòng có tên thao tác rõ ràng và dữ liệu được escape trước khi ghép HTML. */
    function catalogProductRow(product) {
        const active = product.status === 'ACTIVE';
        return `<tr class="${active ? '' : 'catalog-row-inactive'}">
            <td><div class="catalog-product-cell">
                ${catalogMedia(product)}
                <div><strong>${escapeHtml(product.name)}</strong><span class="catalog-sku">${escapeHtml(product.sku)}</span></div>
            </div></td>
            <td><span class="catalog-category-name">${escapeHtml(product.category_name || 'Chưa phân loại')}</span>
                ${product.brand_name ? '<small class="catalog-product-brand">' + escapeHtml(product.brand_name) + '</small>' : ''}</td>
            <td class="align-right catalog-price">${amount(product.unit_price)}</td>
            <td>${catalogStatus(product)}</td>
            <td><div class="catalog-row-actions">
                <button class="catalog-action" type="button" data-action="manage-product-variants" data-id="${product.id}" aria-label="Quản lý phiên bản và màu của ${escapeHtml(product.name)}">
                    ${icon('box')}<span>Phiên bản</span>
                </button>
                <button class="catalog-action" type="button" data-action="manage-product-colors" data-id="${product.id}" aria-label="Quản lý màu sắc của ${escapeHtml(product.name)}">
                    <span class="configuration-color-icon" aria-hidden="true"></span><span>Màu sắc</span>
                </button>
                <button class="catalog-action" type="button" data-action="view-product" data-id="${product.id}" aria-label="Xem chi tiết ${escapeHtml(product.name)}">
                    ${icon('eye')}<span>Chi tiết</span>
                </button>
                <button class="catalog-action" type="button" data-action="edit-product" data-id="${product.id}" aria-label="Sửa ${escapeHtml(product.name)}">
                    ${icon('edit')}<span>Sửa</span>
                </button>
                <button class="catalog-action ${active ? 'catalog-action-danger' : 'catalog-action-restore'}" type="button" data-action="toggle-status" data-id="${product.id}" aria-label="${active ? 'Xóa khỏi cửa hàng' : 'Khôi phục'} ${escapeHtml(product.name)}">
                    ${icon(active ? 'trash' : 'refresh')}<span>${active ? 'Xóa' : 'Khôi phục'}</span>
                </button>
            </div></td>
        </tr>`;
    }

    /** Danh sách phân trang/lọc ở backend; ADMIN đọc cả sản phẩm đã ẩn để có thể khôi phục. */
    async function loadManage() {
        if (!hasRole('ADMIN')) return;
        loadingRows('manage', 5, 'Đang tải sản phẩm…');
        $('#manage-count').textContent = '—';
        $('#manage-pagination').replaceChildren();
        const query = {
            page: state.pages.manage,
            size: 10,
            sort: 'id,asc',
            q: $('#manage-query').value.trim(),
            categoryId: $('#manage-category').value,
            status: $('#manage-status').value,
            grouped: true,
        };
        const result = await api('/products', { anonymous: true, channel: 'manage', query });
        result.content.forEach((product) => state.products.set(product.id, product));
        $('#manage-count').textContent = integer(result.total_elements) + ' sản phẩm';
        $('#manage-rows').innerHTML = result.content.map(catalogProductRow).join('');
        if (!result.content.length) {
            const filtered = Boolean(query.q || query.categoryId || query.status);
            $('#manage-rows').innerHTML = `<tr><td colspan="5"><div class="catalog-empty">
                <span class="catalog-empty-icon">${icon(filtered ? 'search' : 'box')}</span>
                <h3>${filtered ? 'Không tìm thấy sản phẩm' : 'Bắt đầu với sản phẩm đầu tiên'}</h3>
                <p>${filtered ? 'Thử từ khóa khác hoặc bỏ bộ lọc để xem lại danh sách.' : 'Thêm tên, giá và hình ảnh để xây dựng catalog của cửa hàng.'}</p>
                <button class="button ${filtered ? 'secondary' : 'primary'}" type="button" data-action="${filtered ? 'reset-manage-filter' : 'open-product-create'}">
                    ${icon(filtered ? 'refresh' : 'plus')}<span>${filtered ? 'Bỏ bộ lọc' : 'Thêm sản phẩm'}</span>
                </button>
            </div></td></tr>`;
        }
        renderProductOptions();
        renderPager('manage', result);
    }

    /** Thông báo tạo thành công ngay sau POST; tải lại bảng không được che kết quả đã ghi vào database. */
    async function createProduct(form) {
        if (!hasRole('ADMIN')) return;
        const gallery = $('#create-product-gallery');
        if (!validateProductGalleryInput(gallery)) throw new Error(gallery.validationMessage);
        const data = Object.fromEntries(new FormData(form));
        const product = await api('/products', {
            method: 'POST',
            body: {
                sku: data.sku.trim(),
                name: data.name.trim(),
                category_id: Number(data.category_id),
                brand_id: data.brand_id ? Number(data.brand_id) : null,
                unit_price: Number(data.unit_price),
                status: data.status,
                image_url: data.image_url.trim() || null,
                description: data.description.trim() || null,
                image_urls: readGalleryInput(gallery),
                specifications: readSpecifications(form),
            },
        });
        form.reset();
        $('#product-create-dialog')?.close();
        resetProductImagePreviews();
        state.pages.manage = 0;
        notify('success', 'Đã tạo sản phẩm ' + product.sku + '.', 'HTTP 201 Created');
        await loadProductOptions();
        await loadBrands();
        await loadManage();
    }

    /** PATCH chỉ gửi trường được nhập; không đổi những thuộc tính mà admin muốn giữ nguyên. */
    async function updateProduct(form) {
        if (!hasRole('ADMIN')) return;
        const gallery = $('#update-product-gallery');
        if (!validateProductGalleryInput(gallery)) throw new Error(gallery.validationMessage);
        const data = Object.fromEntries(new FormData(form));
        const body = {};
        const storedBrandId = state.products.get(Number(data.product_id))?.brand_id;
        if (String(storedBrandId || '') !== data.brand_id) {
            if (data.brand_id) body.brand_id = Number(data.brand_id);
            else body.clear_brand = true;
        }
        if (data.name.trim()) body.name = data.name.trim();
        if (data.unit_price !== '') body.unit_price = Number(data.unit_price);
        if (data.status) body.status = data.status;
        // Form nạp mô tả hiện có; xóa trắng rồi lưu là yêu cầu xóa nội dung rõ ràng.
        body.description = data.description.trim();
        body.specifications = readSpecifications(form);
        const imageUrls = readGalleryInput(gallery);
        const storedImages = state.products.get(Number(data.product_id))?.image_urls || [];
        if (JSON.stringify(imageUrls) !== JSON.stringify(storedImages)) body.image_urls = imageUrls;
        if ($('#remove-product-image').checked) body.image_url = '';
        else if (data.image_url?.trim()) body.image_url = data.image_url.trim();
        if (!Object.keys(body).length) throw new Error('Nhập tên, giá, trạng thái, ảnh hoặc mô tả cần cập nhật.');
        const product = await api('/products/' + data.product_id, { method: 'PATCH', body });
        notify(
            'success',
            'Đã cập nhật ' + product.sku + '. Giá hiện tại: ' + amount(product.unit_price),
            'HTTP 200 OK',
        );
        form.reset();
        $('#product-edit-dialog')?.close();
        resetProductImagePreviews();
        await loadProductOptions();
        await loadBrands();
        await loadManage();
    }

    /** Chọn sản phẩm khác phải bỏ bản nháp ảnh cũ để không thay nhầm ảnh của hai mặt hàng. */
    function prepareProductImageUpdate() {
        $('#update-product-image').value = '';
        $('#remove-product-image').checked = false;
        renderProductImagePreview($('#update-product-image'));
        const product = state.products.get(Number($('#update-product').value));
        $('#update-product-gallery').value = (product?.image_urls || []).join('\n');
        renderGalleryInputPreview($('#update-product-gallery'));
    }

    /** Mỗi dòng là một link; dòng trống bỏ qua để xóa trắng thành mảng rỗng rõ ràng. */
    function readGalleryInput(input) {
        return input.value
            .split(/\r?\n/)
            .map((line) => line.trim())
            .filter(Boolean);
    }

    /** Kiểm tra số lượng, độ dài, cấu trúc và link trùng trước khi tải preview hoặc gửi JSON. */
    function validateProductGalleryInput(input) {
        const urls = readGalleryInput(input);
        let message = '';
        if (urls.length > MAX_GALLERY_IMAGES) message = 'Chỉ được nhập tối đa 8 ảnh bổ sung.';
        else if (urls.some((url) => url.length > 2048 || !safeProductImageUrl(url))) {
            message = 'Mỗi ảnh cần link HTTP/HTTPS hợp lệ hoặc /assets/, tối đa 2048 ký tự.';
        } else if (new Set(urls).size !== urls.length) message = 'Các link ảnh bổ sung không được trùng nhau.';
        input.setCustomValidity(message);
        return !message;
    }

    /** Xem trước bộ ảnh đã validate; lỗi CDN được hiển thị riêng và không thay đổi link sẽ lưu. */
    function galleryPreviewContent(urls) {
        return `
            <p class="gallery-preview-heading">${urls.length} ảnh bổ sung · theo thứ tự nhập</p>
            <div class="gallery-preview-grid">
                ${urls
                    .map(
                        (url, index) => `
                    <figure class="gallery-preview-item">
                        <img src="${escapeHtml(url)}" alt="Ảnh bổ sung ${index + 1}" loading="lazy" decoding="async" data-gallery-preview-image />
                        <span class="gallery-preview-error" hidden>Không tải được ảnh</span>
                        <figcaption>Ảnh ${index + 1}</figcaption>
                    </figure>
                `,
                    )
                    .join('')}
            </div>
        `;
    }

    /** Preview của hai form dùng chung validation; khi đổi actor không giữ ảnh bản nháp. */
    function renderGalleryInputPreview(input) {
        window.clearTimeout(imagePreviewTimers.get(input));
        imagePreviewTimers.delete(input);
        const preview = $('[data-gallery-preview="' + input.dataset.galleryInput + '"]');
        const valid = validateProductGalleryInput(input);
        const urls = readGalleryInput(input);
        preview.hidden = !hasRole('ADMIN') || (valid && !urls.length);
        preview.innerHTML = valid
            ? galleryPreviewContent(urls)
            : '<p class="gallery-input-error">' + escapeHtml(input.validationMessage) + '</p>';
    }

    /** Validation tại form hỗ trợ đường dẫn tương đối; backend vẫn kiểm tra độc lập mọi request. */
    function validateProductImageInput(input) {
        const invalid = input.value.trim() && !safeProductImageUrl(input.value);
        input.setCustomValidity(invalid ? 'Nhập link ảnh HTTP/HTTPS hợp lệ hoặc đường dẫn /assets/.' : '');
        return !invalid;
    }

    /** Xem trước ảnh mới hoặc ảnh đang lưu; URL lỗi tải chỉ cảnh báo, không gọi API lấy ảnh qua backend. */
    function renderProductImagePreview(input) {
        window.clearTimeout(imagePreviewTimers.get(input));
        imagePreviewTimers.delete(input);
        const isUpdate = input.dataset.imageInput === 'update';
        const removing = isUpdate && $('#remove-product-image').checked;
        input.disabled = removing;
        const preview = $('[data-image-preview="' + input.dataset.imageInput + '"]');
        const image = $('.product-image-preview-img', preview);
        const status = $('[data-image-preview-status]', preview);
        const product = isUpdate ? state.products.get(Number($('#update-product').value)) : null;
        const source = safeProductImageUrl(input.value.trim() || product?.image_url);
        const valid = removing || validateProductImageInput(input);
        if (removing) input.setCustomValidity('');
        preview.classList.remove('image-preview-failed');
        if (!hasRole('ADMIN') || removing || (!source && valid)) {
            preview.hidden = true;
            image.removeAttribute('src');
            status.textContent = '';
            return;
        }
        preview.hidden = false;
        if (!valid) {
            image.hidden = true;
            image.removeAttribute('src');
            preview.classList.add('image-preview-failed');
            status.textContent = input.validationMessage;
            return;
        }
        const caption = isUpdate && !input.value.trim() ? 'Ảnh hiện tại của sản phẩm' : 'Ảnh sẽ được lưu cùng sản phẩm';
        const epoch = state.epoch;
        image.hidden = false;
        status.textContent = 'Đang tải ảnh xem trước…';
        image.onload = () => {
            if (epoch !== state.epoch || image.getAttribute('src') !== source) return;
            status.textContent = caption;
        };
        image.onerror = () => {
            if (epoch !== state.epoch || image.getAttribute('src') !== source) return;
            image.hidden = true;
            preview.classList.add('image-preview-failed');
            status.textContent =
                'Không tải được ảnh từ link này. Bạn có thể đổi link; storefront sẽ dùng ảnh dự phòng nếu ảnh lỗi.';
        };
        if (image.getAttribute('src') === source && image.complete) {
            if (image.naturalWidth > 0) image.onload();
            else image.onerror();
        } else {
            image.src = source;
        }
    }

    /** Đặt lại preview và validity sau khi lưu hoặc đổi actor; không giữ URL ảnh bản nháp trong phiên mới. */
    function resetProductImagePreviews() {
        $$('[data-image-input]').forEach((input) => {
            input.setCustomValidity('');
            renderProductImagePreview(input);
        });
        $$('[data-gallery-input]').forEach((input) => {
            input.setCustomValidity('');
            renderGalleryInputPreview(input);
        });
    }

    /** Xem trước sau khi người dùng ngừng gõ; URL chưa hợp lệ không phát request ảnh. */
    document.addEventListener('input', (event) => {
        const input = event.target;
        if (input.id === 'brand-admin-query') {
            renderAdminBrands();
            return;
        }
        if (input.id === 'category-admin-query') {
            renderCategoryList();
            return;
        }
        if (input.dataset.brandLogoInput) {
            validateProductImageInput(input);
            window.clearTimeout(imagePreviewTimers.get(input));
            imagePreviewTimers.set(
                input,
                window.setTimeout(() => renderBrandLogoPreview(input), 300),
            );
            return;
        }
        if (input.dataset.galleryInput) {
            validateProductGalleryInput(input);
            window.clearTimeout(imagePreviewTimers.get(input));
            imagePreviewTimers.set(
                input,
                window.setTimeout(() => renderGalleryInputPreview(input), 300),
            );
            return;
        }
        if (!input.dataset.imageInput) return;
        validateProductImageInput(input);
        window.clearTimeout(imagePreviewTimers.get(input));
        imagePreviewTimers.set(
            input,
            window.setTimeout(() => renderProductImagePreview(input), 300),
        );
    });

    /** Preview logo chỉ tải tại trình duyệt; thay phần tử để phản hồi ảnh cũ không ghi đè link vừa nhập. */
    function renderBrandLogoPreview(input) {
        window.clearTimeout(imagePreviewTimers.get(input));
        imagePreviewTimers.delete(input);
        const preview = $('[data-brand-logo-preview="' + input.dataset.brandLogoInput + '"]');
        const valid = validateProductImageInput(input);
        const source = safeProductImageUrl(input.value);
        preview.replaceChildren();
        preview.classList.remove('brand-logo-preview-error');
        preview.hidden = !hasRole('ADMIN') || (valid && !source);
        if (preview.hidden) return;
        const status = document.createElement('p');
        status.setAttribute('role', 'status');
        if (!valid) {
            preview.classList.add('brand-logo-preview-error');
            status.textContent = input.validationMessage;
            preview.append(status);
            return;
        }
        const image = document.createElement('img');
        image.alt = 'Xem trước logo thương hiệu';
        const epoch = state.epoch;
        status.textContent = 'Đang tải logo…';
        image.onload = () => {
            if (!image.isConnected || epoch !== state.epoch) return;
            status.textContent = 'Logo sẽ hiển thị trong menu hãng và danh sách quản trị.';
        };
        image.onerror = () => {
            if (!image.isConnected || epoch !== state.epoch) return;
            image.hidden = true;
            preview.classList.add('brand-logo-preview-error');
            status.textContent = 'Không tải được logo. Kiểm tra link ảnh; tên hãng vẫn hiển thị nếu ảnh lỗi.';
        };
        preview.append(image, status);
        image.src = source;
    }

    /** Mở hãng đã có để sửa đúng logo; giữ nguyên tên, slug và danh mục đang gợi ý. */
    function openBrandLogoEdit(id) {
        if (!hasRole('ADMIN')) return;
        const brand = state.brands.find((value) => value.id === id);
        if (!brand) return;
        $('#brand-logo-edit').dataset.brandId = String(id);
        $('#brand-logo-name').textContent = brand.name;
        $('#edit-brand-logo').value = brand.logo_url || '';
        renderBrandLogoPreview($('#edit-brand-logo'));
        openDialog('brand-logo-dialog');
    }

    /** Chỉ lưu sau khi ADMIN bấm xác nhận; chuỗi trống xóa logo và API vẫn kiểm tra quyền độc lập. */
    async function saveBrandLogo(form) {
        if (!hasRole('ADMIN')) return;
        const input = $('#edit-brand-logo');
        if (!validateProductImageInput(input)) throw new Error(input.validationMessage);
        await api('/brands/' + Number(form.dataset.brandId) + '/logo', {
            method: 'PATCH',
            body: { logo_url: input.value.trim() },
        });
        $('#brand-logo-dialog').close();
        form.reset();
        renderBrandLogoPreview(input);
        notify('success', 'Đã cập nhật logo thương hiệu.', 'HTTP 200 OK');
        await loadBrands();
    }

    /** Danh mục chỉ ADMIN được tạo; slug do backend kiểm tra cùng ràng buộc duy nhất. */
    async function createCategory(form) {
        if (!hasRole('ADMIN')) return;
        const data = Object.fromEntries(new FormData(form));
        await api('/categories', {
            method: 'POST',
            body: {
                name: data.name.trim(),
                slug: data.slug.trim(),
                parent_id: data.parent_id ? Number(data.parent_id) : null,
            },
        });
        form.reset();
        $('#category-create-dialog')?.close();
        notify('success', 'Đã thêm danh mục ' + data.name.trim() + '.', 'HTTP 201 Created');
        await loadCategoryList();
    }

    /** ADMIN sửa đúng danh mục đã chọn; không tạo lại ID hoặc thay vị trí cha/con theo tên mới. */
    function openCategoryEdit(id) {
        if (!hasRole('ADMIN')) return;
        const category = state.categories.find((item) => item.id === id);
        if (!category) return;
        $('#category-edit').dataset.categoryId = String(id);
        $('#edit-category-name').value = category.name;
        $('#edit-category-slug').value = category.slug;
        $('#edit-category-path').textContent = categoryPath(category);
        openDialog('category-edit-dialog');
    }

    /** Lưu tên/slug qua API có phân quyền, rồi tải lại cây/hãng/sản phẩm để mọi lựa chọn dùng tên mới. */
    async function updateCategory(form) {
        if (!hasRole('ADMIN')) return;
        const data = Object.fromEntries(new FormData(form));
        await api('/categories/' + Number(form.dataset.categoryId), {
            method: 'PATCH',
            body: { name: data.name.trim(), slug: data.slug.trim() },
        });
        $('#category-edit-dialog').close();
        notify('success', 'Đã cập nhật danh mục ' + data.name.trim() + '.', 'HTTP 200 OK');
        await loadCategoryList();
    }

    /** Xác nhận tên trước khi xóa; backend chặn nhóm có con/SKU và không xóa dây chuyền sản phẩm. */
    function confirmCategoryDelete(id) {
        if (!hasRole('ADMIN')) return;
        const category = state.categories.find((item) => item.id === id);
        if (!category) return;
        $('#confirm-title').textContent = 'Xóa danh mục “' + category.name + '”?';
        $('#confirm-message').textContent =
            'Chỉ xóa khi danh mục không còn sản phẩm hoặc danh mục con. Gợi ý hãng của nhóm này sẽ được gỡ; ' +
            'các hãng và dữ liệu bán hàng vẫn được giữ nguyên.';
        $('#confirm-submit').textContent = 'Xóa danh mục';
        $('#confirm-submit').className = 'button danger';
        state.pendingMutation = {
            epoch: state.epoch,
            handler: async () => {
                await api('/categories/' + category.id, { method: 'DELETE' });
                notify('success', 'Đã xóa danh mục ' + category.name + '.', 'HTTP 204 No Content');
                await loadCategoryList();
            },
        };
        openDialog('confirm-dialog');
    }

    /** ADMIN tạo hãng thật và danh mục gợi ý tùy chọn, không suy đoán hãng từ chuỗi tên sản phẩm. */
    async function createBrand(form) {
        if (!hasRole('ADMIN')) return;
        if (!validateProductImageInput($('#create-brand-logo')))
            throw new Error($('#create-brand-logo').validationMessage);
        const data = Object.fromEntries(new FormData(form));
        await api('/brands', {
            method: 'POST',
            body: {
                name: data.name.trim(),
                slug: data.slug.trim(),
                category_ids: data.category_id ? [Number(data.category_id)] : [],
                logo_url: data.logo_url.trim(),
            },
        });
        form.reset();
        renderBrandLogoPreview($('#create-brand-logo'));
        $('#brand-create-dialog').close();
        notify('success', 'Đã thêm thương hiệu ' + data.name.trim() + '.', 'HTTP 201 Created');
        await loadBrands();
    }

    /** Mở hộp thoại thêm sản phẩm mới cho ADMIN. */
    function openProductCreate() {
        if (!hasRole('ADMIN')) return;
        const form = $('#product-create');
        if (form) form.reset();
        renderSpecEditor($('[data-spec-editor="create"]'), []);
        renderProductImagePreview($('#create-product-image'));
        renderGalleryInputPreview($('#create-product-gallery'));
        openDialog('product-create-dialog');
    }

    /** Mở hộp thoại sửa sản phẩm và tự động nạp thông tin sẵn có. */
    function openProductEdit(id) {
        if (!hasRole('ADMIN')) return;
        const product = state.products.get(Number(id));
        if (!product) return;
        $('#update-product').value = String(product.id);
        $('#update-product-brand').value = product.brand_id ? String(product.brand_id) : '';
        const skuBadge = $('#update-product-sku-badge');
        if (skuBadge) skuBadge.textContent = 'SKU: ' + product.sku;
        const catText = $('#update-product-category-text');
        if (catText) catText.textContent = 'Danh mục: ' + (product.category_name || 'Chưa phân loại');
        $('#update-product-name').value = product.name;
        $('#update-product-description').value = product.description || '';
        renderSpecEditor($('[data-spec-editor="update"]'), product.specifications || []);
        $('#update-product-price').value = product.unit_price;
        $('#update-product-status').value = product.status;
        $('#update-product-image').value = product.image_url || '';
        $('#remove-product-image').checked = false;
        renderProductImagePreview($('#update-product-image'));
        $('#update-product-gallery').value = (product.image_urls || []).join('\n');
        renderGalleryInputPreview($('#update-product-gallery'));
        openDialog('product-edit-dialog');
    }

    /** Tạo một dòng thông số bằng DOM; không ghép giá trị ADMIN nhập vào thuộc tính HTML. */
    function appendSpecRow(editor, specification = {}) {
        const rows = $('.spec-editor-rows', editor);
        if (rows.children.length >= 60) throw new Error('Một sản phẩm tối đa 60 thông số.');
        const row = document.createElement('div');
        row.className = 'spec-editor-row';
        row.innerHTML = `<label><span>Tên thông số</span><input data-spec-name maxlength="100" required placeholder="Ví dụ: Chipset" /></label>
            <label><span>Giá trị</span><textarea data-spec-value maxlength="1000" rows="2" required placeholder="Nhập thông số đã kiểm chứng"></textarea></label>
            <button class="icon-button" type="button" data-action="remove-spec-row" aria-label="Xóa dòng thông số">${icon('trash')}</button>`;
        $('[data-spec-name]', row).value = specification.name || '';
        $('[data-spec-value]', row).value = specification.value || '';
        rows.append(row);
        $('[data-action="add-spec-row"]', editor).disabled = rows.children.length >= 60;
    }

    /** Nạp bảng đã lưu khi sửa; form tạo mới bắt đầu rỗng và không tự điền cấu hình thiết bị. */
    function renderSpecEditor(editor, specifications) {
        $('.spec-editor-rows', editor).replaceChildren();
        $('[data-action="add-spec-row"]', editor).disabled = false;
        specifications.forEach((specification) => appendSpecRow(editor, specification));
    }

    /** Kiểm tra nhãn trùng tại client để phản hồi nhanh; backend vẫn validate độc lập trước khi lưu. */
    function readSpecifications(form) {
        const rows = $$('.spec-editor-row', form).map((row) => ({
            name: $('[data-spec-name]', row).value.trim(),
            value: $('[data-spec-value]', row).value.trim(),
        }));
        if (rows.some((row) => !row.name || !row.value)) throw new Error('Mỗi thông số phải có tên và giá trị.');
        if (new Set(rows.map((row) => row.name.toLocaleLowerCase('vi-VN'))).size !== rows.length)
            throw new Error('Tên thông số không được trùng nhau.');
        return rows;
    }

    /** Hai tab tách cấu hình và SKU màu; chuyển tab giữ dữ liệu đang nhập trong các form. */
    function setConfigurationPanel(panel) {
        const dialog = $('#product-variants-dialog');
        const selected = panel === 'colors' ? 'colors' : 'versions';
        dialog.dataset.panel = selected;
        $('#variants-dialog-title').textContent = selected === 'colors' ? 'Màu sắc sản phẩm' : 'Phiên bản sản phẩm';
        $$('[data-configuration-panel]', dialog).forEach((section) => {
            section.hidden = section.dataset.configurationPanel !== selected;
        });
        $$('.configuration-tabs [role="tab"]', dialog).forEach((tab) => {
            const active = tab.dataset.panel === selected;
            tab.classList.toggle('is-selected', active);
            tab.setAttribute('aria-selected', String(active));
            tab.tabIndex = active ? 0 : -1;
        });
    }

    /** Tab hỗ trợ phím mũi tên/Home/End, không buộc người dùng bàn phím phải dùng chuột. */
    document.addEventListener('keydown', (event) => {
        const tab = event.target.closest('.configuration-tabs [role="tab"]');
        if (!tab || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault();
        const panel =
            event.key === 'Home'
                ? 'versions'
                : event.key === 'End'
                  ? 'colors'
                  : tab.dataset.panel === 'versions'
                    ? 'colors'
                    : 'versions';
        setConfigurationPanel(panel);
        $('.configuration-tabs [data-panel="' + panel + '"]').focus();
    });

    /** ADMIN thấy phần đang quản lý và có thể bật danh sách đã xóa để khôi phục khi cần. */
    async function manageProductVariants(id, selectedVersionId = null, panel = 'versions') {
        if (!hasRole('ADMIN')) return;
        const product = await api('/products/' + id, { anonymous: true, channel: 'variant-admin' });
        state.products.set(product.id, product);
        const dialog = $('#product-variants-dialog');
        if (dialog.dataset.productId !== String(product.id)) delete dialog.dataset.showArchived;
        const showArchived = dialog.dataset.showArchived === 'true';
        const variants = product.variants || [];
        const versions = product.versions || [];
        const visibleVersions = versions.filter((version) => showArchived || !version.archived);
        const selected = visibleVersions.find((version) => version.id === selectedVersionId) || visibleVersions[0];
        const colors = selected
            ? variants.filter((color) => color.version_id === selected.id && (showArchived || !color.archived))
            : [];
        dialog.dataset.productId = String(product.id);
        dialog.dataset.versionId = String(selected?.id || '');
        $('#variants-product-name').textContent = product.name;
        $('#product-variants-body').innerHTML = /* HTML */ ` <div
                class="configuration-tabs"
                role="tablist"
                aria-label="Quản lý cấu hình sản phẩm"
            >
                <button
                    id="configuration-versions-tab"
                    type="button"
                    role="tab"
                    data-action="configuration-panel"
                    data-panel="versions"
                    aria-controls="configuration-versions"
                >
                    ${icon('box')}Phiên bản<span>${versions.filter((version) => !version.archived).length}</span>
                </button>
                <button
                    id="configuration-colors-tab"
                    type="button"
                    role="tab"
                    data-action="configuration-panel"
                    data-panel="colors"
                    aria-controls="configuration-colors"
                >
                    <span class="configuration-color-icon" aria-hidden="true"></span>Màu sắc<span
                        >${variants.filter((color) => !color.archived && !versions.find((version) => version.id === color.version_id)?.archived).length}</span
                    >
                </button>
            </div>
            <div class="configuration-guide">
                <p><strong>Phiên bản</strong> là cấu hình, ví dụ 256 GB / 512 GB hoặc 40mm GPS.</p>
                <p><strong>Màu sắc</strong> là các lựa chọn trong phiên bản, mỗi màu có SKU, giá, ảnh và tồn riêng.</p>
            </div>
            <label class="configuration-archive-filter"
                ><input id="configuration-show-archived" type="checkbox" ${showArchived ? 'checked' : ''} />Hiện phiên
                bản và màu đã xóa để khôi phục</label
            >
            <section
                id="configuration-versions"
                data-configuration-panel="versions"
                role="tabpanel"
                aria-labelledby="configuration-versions-tab"
            >
                <div class="configuration-section-heading">
                    <h3>Danh sách phiên bản</h3>
                    <span>${visibleVersions.length} phiên bản</span>
                </div>
                ${
                    selected
                        ? `
                <div class="configuration-admin-layout">
                    <aside class="version-admin-sidebar" aria-label="Các phiên bản của sản phẩm">
                        ${visibleVersions
                            .map(
                                (
                                    version,
                                ) => `<button type="button" class="version-admin-tab ${version.id === selected.id ? 'is-selected' : ''}" data-action="manage-product-version" data-id="${version.id}" aria-pressed="${version.id === selected.id}">
                            <strong>${escapeHtml(version.name)}</strong><small>${version.archived ? 'Đã xóa' : variants.filter((color) => color.version_id === version.id && !color.archived).length + ' màu / SKU'}</small>
                        </button>`,
                            )
                            .join('')}
                    </aside>
                    <form class="version-admin-edit" data-version-id="${selected.id}">
                        <div class="configuration-section-heading"><h3>Thông tin phiên bản</h3><span class="configuration-state ${selected.archived ? 'is-archived' : ''}">${selected.archived ? 'Đã xóa khỏi cửa hàng' : 'Đang sử dụng'}</span></div>
                        <label>Tên phiên bản<input name="name" maxlength="160" value="${escapeHtml(selected.name)}" placeholder="Ví dụ: 256 GB hoặc 40mm GPS" required /></label>
                        <details class="version-spec-details"><summary>Thông số riêng của phiên bản <span>${selected.specifications.length} dòng</span></summary>
                            <fieldset class="spec-editor" data-spec-editor="version-edit"><legend>Thông số khác với model</legend><p class="subtle">Cùng tên thông số sẽ ghi đè bảng chung. Xóa hết dòng để dùng bảng chung.</p><div class="spec-editor-rows"></div><button class="button secondary small" type="button" data-action="add-spec-row">+ Thêm thông số</button></fieldset>
                        </details>
                        <div class="configuration-card-actions">
                            <button class="button primary small" type="submit">Lưu phiên bản</button>
                            ${selected.archived ? `<button class="button secondary small" type="button" data-action="restore-product-version" data-id="${selected.id}">Khôi phục phiên bản</button>` : `<button class="button secondary small" type="button" data-action="configuration-panel" data-panel="colors">Xem màu sắc</button><button class="button danger small" type="button" data-action="delete-product-version" data-id="${selected.id}">${icon('trash')}Xóa phiên bản</button>`}
                        </div>
                    </form>
                </div>`
                        : '<p class="configuration-empty">Chưa có phiên bản đang sử dụng. Thêm phiên bản hoặc bật “Hiện phiên bản và màu đã xóa” để khôi phục.</p>'
                }
                ${renderVersionCreateForm(product)}
            </section>
            <section
                id="configuration-colors"
                data-configuration-panel="colors"
                role="tabpanel"
                aria-labelledby="configuration-colors-tab"
            >
                <div class="configuration-section-heading">
                    <h3>Màu sắc theo phiên bản</h3>
                    <span>${colors.length} màu</span>
                </div>
                ${
                    selected
                        ? `
                    <label class="configuration-version-select">Chọn phiên bản để quản lý màu<select id="configuration-version-select">${visibleVersions.map((version) => `<option value="${version.id}" ${version.id === selected.id ? 'selected' : ''}>${escapeHtml(version.name)}${version.archived ? ' · Đã xóa' : ''}</option>`).join('')}</select></label>
                    ${selected.archived ? '<p class="configuration-empty">Phiên bản này đã xóa khỏi cửa hàng. Khôi phục phiên bản ở tab Phiên bản trước khi thêm hoặc mở bán màu.</p>' : ''}
                    <div class="variant-admin-list">${colors.map((color) => renderAdminColor(product, selected, color)).join('')}</div>
                    ${!colors.length ? '<p class="configuration-empty">Phiên bản chưa có màu đang sử dụng. Thêm màu mới bên dưới hoặc bật danh sách đã xóa để khôi phục.</p>' : ''}
                    ${!selected.archived ? renderColorCreateForm(product, selected) : ''}
                `
                        : '<p class="configuration-empty">Tạo phiên bản ở tab Phiên bản trước, sau đó thêm các màu vào cấu hình đó.</p><button class="button secondary" type="button" data-action="configuration-panel" data-panel="versions">Đi tới Phiên bản</button>'
                }
            </section>`;
        if (selected) renderSpecEditor($('[data-spec-editor="version-edit"]', dialog), selected.specifications);
        renderSpecEditor($('[data-spec-editor="version-create"]', dialog), []);
        setConfigurationPanel(panel);
        openDialog('product-variants-dialog');
    }

    /** Form tạo cấu hình riêng; lần đầu chỉ khai báo màu cho SKU hiện tại, không tạo lại sản phẩm. */
    function renderVersionCreateForm(product) {
        const first = !product.versions?.length;
        return /* HTML */ `<details class="configuration-create-disclosure" ${first ? 'open' : ''}>
            <summary>${icon('plus')}${first ? 'Khởi tạo phiên bản và màu hiện tại' : 'Thêm phiên bản mới'}</summary>
            <form id="product-version-create" class="stacked-form catalog-form version-create-form">
                <label
                    >Tên phiên bản<input
                        name="name"
                        maxlength="160"
                        placeholder="Ví dụ: 256 GB hoặc 40mm GPS · Dây S/M"
                        required
                /></label>
                ${first ? `<fieldset class="original-color-fields"><legend>SKU hiện tại · ${escapeHtml(product.sku)}</legend><p class="subtle">Giữ nguyên giá, ảnh, tồn và lịch sử; nhập đúng màu của SKU đang có.</p><div class="form-grid"><label>Tên màu hiện tại<input name="default_color_name" maxlength="80" placeholder="Ví dụ: Đen" required /></label><label>Mã màu hiển thị<input name="default_color_hex" type="color" value="#202020" /></label></div></fieldset>` : ''}
                <details class="version-spec-details">
                    <summary>Thông số riêng <span>Tùy chọn</span></summary>
                    <fieldset class="spec-editor" data-spec-editor="version-create">
                        <legend>Thông số của cấu hình</legend>
                        <p class="subtle">Chỉ nhập thông số khác với bảng chung của sản phẩm.</p>
                        <div class="spec-editor-rows"></div>
                        <button class="button secondary small" type="button" data-action="add-spec-row">
                            + Thêm thông số
                        </button>
                    </fieldset>
                </details>
                <button class="button primary" type="submit" ${product.versions?.length >= 20 ? 'disabled' : ''}>
                    ${icon('plus')}${first ? 'Lưu phiên bản và màu gốc' : 'Thêm phiên bản'}
                </button>
            </form>
        </details>`;
    }

    /** Màu đã xóa có nút khôi phục rõ ràng; màu đang sử dụng có nút lưu và xóa riêng. */
    function renderAdminColor(product, version, color) {
        const blocked = color.archived || version.archived;
        return /* HTML */ `<form
            class="variant-admin-edit ${color.archived ? 'is-archived' : ''}"
            data-variant-id="${color.id}"
        >
            <div class="variant-admin-title">
                <span class="color-dot" style="--color: ${colorHex(color.color_hex)}" aria-hidden="true"></span>
                <div>
                    <strong>${escapeHtml(color.color_name)}</strong
                    ><small class="mono"
                        >${escapeHtml(color.sku)}${color.sku_product_id === product.id ? ' · SKU gốc' : ''}</small
                    >
                </div>
                <span class="configuration-state ${blocked ? 'is-archived' : ''}"
                    >${color.archived ? 'Đã xóa' : color.status === 'ACTIVE' ? 'Đang bán' : 'Ngừng bán'}</span
                >
            </div>
            <fieldset class="configuration-color-fields" ${blocked ? 'disabled' : ''}>
                <div class="form-grid">
                    <label
                        >Giá bán (VND)<input
                            name="unit_price"
                            type="number"
                            min="0.01"
                            max="9999999999.99"
                            step="0.01"
                            value="${color.unit_price}"
                            required /></label
                    ><label
                        >Trạng thái màu<select name="status">
                            <option value="ACTIVE" ${color.status === 'ACTIVE' ? 'selected' : ''}>Đang bán</option>
                            <option value="INACTIVE" ${color.status !== 'ACTIVE' ? 'selected' : ''}>Ngừng bán</option>
                        </select></label
                    >
                </div>
                <label
                    >Ảnh bìa của màu<input
                        name="image_url"
                        maxlength="2048"
                        value="${escapeHtml(color.image_url || '')}"
                        placeholder="https://... hoặc /assets/..."
                /></label>
                <label
                    >Ảnh bổ sung (mỗi dòng một link, tối đa 8)<textarea name="image_urls" rows="2">
${escapeHtml((color.image_urls || []).join('\n'))}</textarea>
                </label>
            </fieldset>
            <div class="configuration-card-actions">
                ${color.archived ? `<button class="button secondary small" type="button" data-action="restore-product-color" data-id="${color.id}" ${version.archived ? 'disabled' : ''}>Khôi phục màu</button>` : `<button class="button primary small" type="submit" ${version.archived ? 'disabled' : ''}>Lưu màu</button><button class="button danger small" type="button" data-action="delete-product-color" data-id="${color.id}">${icon('trash')}Xóa màu</button>`}
            </div>
        </form>`;
    }

    /** Thêm màu vào đúng phiên bản đã chọn; SKU mới chỉ có hàng sau nghiệp vụ nhập kho. */
    function renderColorCreateForm(product, version) {
        const variants = product.variants || [];
        const total = variants.filter((color) => color.version_id === version.id).length;
        return /* HTML */ `<details class="configuration-create-disclosure" ${!total ? 'open' : ''}>
            <summary>${icon('plus')}Thêm màu cho ${escapeHtml(version.name)}</summary>
            <form id="product-variant-create" class="stacked-form catalog-form variant-create-form">
                <input name="version_id" type="hidden" value="${version.id}" />
                <div class="form-grid">
                    <label>SKU mới<input name="sku" maxlength="80" placeholder="Ví dụ: IP17-256-DEN" required /></label
                    ><label>Tên màu<input name="color_name" maxlength="80" placeholder="Ví dụ: Đen" required /></label>
                </div>
                <div class="form-grid">
                    <label
                        >Giá bán (VND)<input
                            name="unit_price"
                            type="number"
                            min="0.01"
                            max="9999999999.99"
                            step="0.01"
                            value="${product.unit_price}" /></label
                    ><label>Mã màu hiển thị<input name="color_hex" type="color" value="#2563eb" /></label>
                </div>
                <label
                    >Ảnh bìa đúng màu<input
                        name="image_url"
                        maxlength="2048"
                        placeholder="https://... hoặc /assets/..." /></label
                ><label
                    >Ảnh bổ sung (mỗi dòng một link, tối đa 8)<textarea name="image_urls" rows="3"></textarea>
                </label>
                <p class="subtle">
                    SKU mới có tồn bằng 0. Cùng tên màu được phép ở các phiên bản khác nhau. Màu đã xóa có thể khôi phục
                    để dùng lại mã cũ.
                </p>
                <button class="button primary" type="submit" ${total >= 30 || variants.length >= 100 ? 'disabled' : ''}>
                    ${icon('plus')}Thêm màu
                </button>
            </form>
        </details>`;
    }

    /** Xác nhận tên/cấu hình trước khi xóa; giữ dialog gốc để nút Quay lại không làm mất dữ liệu nhập. */
    function confirmConfigurationArchive(kind, id) {
        if (!hasRole('ADMIN')) return;
        const dialog = $('#product-variants-dialog');
        const product = state.products.get(Number(dialog.dataset.productId));
        const version = kind === 'version';
        const item = (version ? product?.versions : product?.variants)?.find((value) => value.id === id);
        if (!item) return;
        const name = version ? item.name : item.color_name + ' · ' + item.version_name;
        $('#confirm-title').textContent = version ? 'Xóa phiên bản?' : 'Xóa màu?';
        $('#confirm-message').textContent =
            'Xóa “' +
            name +
            '” khỏi các lựa chọn mua hàng? ' +
            (version ? 'Các màu thuộc phiên bản này sẽ được ẩn cùng. ' : '') +
            'SKU, tồn kho và đơn hàng đã có vẫn được lưu giữ. Bạn có thể khôi phục trong danh sách đã xóa.';
        $('#confirm-submit').textContent = version ? 'Xóa phiên bản' : 'Xóa màu';
        $('#confirm-submit').className = 'button danger';
        state.pendingMutation = {
            epoch: state.epoch,
            handler: async () => {
                await api('/products/' + product.id + '/' + (version ? 'versions' : 'variants') + '/' + id, {
                    method: 'DELETE',
                });
                notify(
                    'success',
                    'Đã xóa ' + (version ? 'phiên bản ' : 'màu ') + name + ' khỏi cửa hàng.',
                    'HTTP 200 OK',
                );
                await refreshConfiguration(
                    product.id,
                    Number(dialog.dataset.versionId),
                    version ? 'versions' : 'colors',
                );
            },
        };
        $('#confirm-dialog').showModal();
    }

    /** Khôi phục cấu hình qua API ADMIN; tồn và movement không thay đổi khi mở lại lựa chọn. */
    async function restoreProductConfiguration(kind, id) {
        if (!hasRole('ADMIN')) return;
        const dialog = $('#product-variants-dialog');
        const productId = Number(dialog.dataset.productId);
        const version = kind === 'version';
        await api(
            '/products/' +
                productId +
                '/' +
                (version ? 'versions' : 'variants') +
                '/' +
                id +
                (version ? '/restore' : ''),
            {
                method: version ? 'POST' : 'PATCH',
                ...(version ? {} : { body: { status: 'ACTIVE' } }),
            },
        );
        notify('success', version ? 'Đã khôi phục phiên bản.' : 'Đã khôi phục màu.', 'HTTP 200 OK');
        await refreshConfiguration(
            productId,
            version ? id : Number(dialog.dataset.versionId),
            version ? 'versions' : 'colors',
        );
    }

    /** Nạp lại danh sách dùng chung sau ghi; giữ đúng tab/phiên bản đang thao tác. */
    async function refreshConfiguration(productId, versionId, panel) {
        await loadProductOptions();
        await loadManage();
        await manageProductVariants(productId, versionId, panel);
    }

    /** Lưu phiên bản trước khi khai báo màu; giữ selection sau PATCH và chọn bản vừa tạo sau POST. */
    async function saveProductVersion(form, create = false) {
        if (!hasRole('ADMIN')) return;
        const productId = Number($('#product-variants-dialog').dataset.productId);
        const data = Object.fromEntries(new FormData(form));
        const body = { name: data.name.trim(), specifications: readSpecifications(form) };
        if (data.default_color_name) {
            body.default_color_name = data.default_color_name.trim();
            body.default_color_hex = data.default_color_hex;
        }
        const result = await api(
            '/products/' + productId + '/versions' + (create ? '' : '/' + form.dataset.versionId),
            {
                method: create ? 'POST' : 'PATCH',
                body,
            },
        );
        const selected = create ? result.versions.at(-1).id : Number(form.dataset.versionId);
        notify(
            'success',
            create ? 'Đã tạo phiên bản. Thêm màu và nhập hàng theo SKU để bán.' : 'Đã lưu phiên bản và thông số.',
            create ? 'HTTP 201 Created' : 'HTTP 200 OK',
        );
        await loadProductOptions();
        await loadManage();
        await manageProductVariants(productId, selected, create ? 'colors' : 'versions');
    }

    /** Giá và ảnh được lưu theo SKU thuộc phiên bản đã chọn, không cộng chung hàng giữa các cấu hình. */
    async function saveProductVariant(form, create = false) {
        if (!hasRole('ADMIN')) return;
        const dialog = $('#product-variants-dialog');
        const productId = Number(dialog.dataset.productId);
        const versionId = Number(dialog.dataset.versionId);
        const data = Object.fromEntries(new FormData(form));
        if (!validateProductGalleryInput(form.elements.image_urls))
            throw new Error(form.elements.image_urls.validationMessage);
        const body = {
            unit_price: data.unit_price ? Number(data.unit_price) : null,
            image_url: data.image_url.trim(),
            image_urls: readGalleryInput(form.elements.image_urls),
        };
        if (create) {
            body.sku = data.sku.trim();
            body.color_name = data.color_name.trim();
            body.color_hex = data.color_hex;
            body.version_id = Number(data.version_id);
        } else body.status = data.status;
        await api('/products/' + productId + '/variants' + (create ? '' : '/' + form.dataset.variantId), {
            method: create ? 'POST' : 'PATCH',
            body,
        });
        notify(
            'success',
            create ? 'Đã thêm SKU mới. Nhập hàng theo SKU để bắt đầu bán.' : 'Đã lưu cấu hình màu.',
            create ? 'HTTP 201 Created' : 'HTTP 200 OK',
        );
        await loadProductOptions();
        await loadManage();
        await manageProductVariants(productId, versionId, 'colors');
    }

    /** Mở hộp thoại xem chi tiết sản phẩm và tải tồn kho chi nhánh. */
    async function viewProductDetail(id) {
        if (!hasRole('ADMIN')) return;
        const product = await api('/products/' + Number(id), { anonymous: true, channel: 'product-detail' });
        state.products.set(product.id, product);
        const dialog = $('#product-detail-dialog');
        dialog.dataset.productId = String(product.id);
        $('#product-detail-body').innerHTML = `<div class="catalog-detail-heading">
            ${catalogMedia(product, true)}
            <div class="catalog-detail-summary">
                <span class="catalog-sku">${escapeHtml(product.sku)}</span>
                <h3>${escapeHtml(product.name)}</h3>
                <p>${escapeHtml(product.category_name || 'Chưa phân loại')}</p>
                <strong class="catalog-detail-price">${amount(product.unit_price)}</strong>
                ${catalogStatus(product)}
            </div>
        </div>
        ${product.image_urls?.length ? '<section class="gallery-input-preview">' + galleryPreviewContent(product.image_urls.map(safeProductImageUrl).filter(Boolean)) + '</section>' : ''}
        ${productDescription(product)}
        ${productSpecifications(product)}
        <dl class="catalog-detail-facts">
            <div><dt>Mã sản phẩm</dt><dd>#${product.id}</dd></div>
            <div><dt>Ngày tạo</dt><dd>${dateTime(product.created_at)}</dd></div>
        </dl>
        <section class="catalog-detail-stock" aria-labelledby="detail-stock-title">
            <div><h3 id="detail-stock-title">Tồn kho theo chi nhánh</h3><p>Khả dụng, đang giữ và tổng số hàng thực tế.</p></div>
            <div id="detail-stock-container" aria-live="polite"><p class="catalog-detail-message">Đang tải tồn kho…</p></div>
        </section>`;
        $('#detail-edit-shortcut').onclick = () => {
            dialog.close();
            openProductEdit(product.id);
        };
        openDialog('product-detail-dialog');
        try {
            const stocks = await api('/inventories', {
                channel: 'detail-stock',
                query: { productId: product.id, page: 0, size: 100 },
            });
            // Đóng dialog hoặc mở sản phẩm khác thì response cũ không được ghi vào bản đang xem.
            if (!dialog.open || dialog.dataset.productId !== String(product.id)) return;
            $('#detail-stock-container').innerHTML = stocks.content.length
                ? `<div class="table-scroll"><table class="catalog-stock-table">
                    <thead><tr><th>Chi nhánh</th><th>Khả dụng</th><th>Đang giữ</th><th>Thực tế</th></tr></thead>
                    <tbody>${stocks.content
                        .map(
                            (stock) => `<tr>
                        <td>${escapeHtml(stock.warehouse_name)}</td>
                        <td class="catalog-stock-available">${integer(stock.available_quantity)}</td>
                        <td>${integer(stock.reserved_quantity)}</td>
                        <td>${integer(stock.physical_quantity)}</td>
                    </tr>`,
                        )
                        .join('')}</tbody>
                </table></div>`
                : '<p class="catalog-detail-message">Sản phẩm chưa được nhập kho. Nhập hàng để bắt đầu bán.</p>';
        } catch (error) {
            if (error.name === 'AbortError' || !dialog.open || dialog.dataset.productId !== String(product.id)) return;
            $('#detail-stock-container').innerHTML =
                '<p class="catalog-detail-message">Chưa tải được tồn kho. Vui lòng đóng và mở lại chi tiết.</p>';
            handleError(error);
        }
    }

    /** Chuyển đổi trạng thái kinh doanh an toàn (soft delete / toggle active). */
    function toggleProductStatus(id) {
        if (!hasRole('ADMIN')) return;
        const product = state.products.get(Number(id));
        if (!product) return;
        const active = product.status === 'ACTIVE';
        const nextStatus = active ? 'INACTIVE' : 'ACTIVE';
        $('#confirm-title').textContent = active ? 'Xóa sản phẩm khỏi cửa hàng?' : 'Khôi phục sản phẩm?';
        $('#confirm-message').textContent = active
            ? 'Sản phẩm “' +
              product.name +
              '” sẽ ngừng kinh doanh và được ẩn khỏi cửa hàng. Tồn kho, đơn hàng và lịch sử vẫn được giữ nguyên. Bạn có thể khôi phục sản phẩm trong danh sách đã ẩn.'
            : 'Sản phẩm “' + product.name + '” sẽ được hiển thị lại trên cửa hàng với giá và tồn kho hiện tại.';
        $('#confirm-submit').textContent = active ? 'Xóa khỏi cửa hàng' : 'Khôi phục sản phẩm';
        $('#confirm-submit').className = 'button ' + (active ? 'danger' : 'primary');
        state.pendingMutation = {
            epoch: state.epoch,
            handler: async () => {
                const updated = await api('/products/' + product.id, { method: 'PATCH', body: { status: nextStatus } });
                state.products.set(updated.id, updated);
                notify('success', (active ? 'Đã ẩn ' : 'Đã khôi phục ') + updated.name + '.', 'Cập nhật thành công');
                await loadProductOptions();
                await loadManage();
            },
        };
        openDialog('confirm-dialog');
    }

    /** Bảng quản lý danh mục sản phẩm. */
    async function loadCategoryList() {
        if (!hasRole('ADMIN')) return;
        $('#category-count').textContent = '—';
        // Đếm theo category_id cùng hậu duệ để tổng của nhóm cha khớp quy tắc lọc sản phẩm tại API.
        await Promise.all([loadCategories(), loadBrands(), loadProductOptions()]);
        state.categoryProductCounts.clear();
        for (const category of state.categories)
            state.categoryProductCounts.set(
                category.id,
                [...state.products.values()].filter(
                    (product) => !product.parent_product_id && categoryBelongsTo(product.category_id, category.id),
                ).length,
            );
        renderCategoryList();
    }

    /** Tìm tên, slug, ID và đường dẫn cha bằng tiếng Việt có/không dấu; không thay cây thật trong state. */
    function renderCategoryList() {
        if (!hasRole('ADMIN')) return;
        const query = slugify($('#category-admin-query').value.trim());
        const visible = orderedCategories().filter((category) =>
            slugify(category.id + ' ' + category.name + ' ' + category.slug + ' ' + categoryPath(category)).includes(
                query,
            ),
        );
        $('#category-count').textContent =
            integer(visible.length) + (query ? ' / ' + integer(state.categories.length) : '') + ' danh mục';
        $('#category-rows').innerHTML = visible
            .map(
                (category) => `<tr>
            <td><span class="catalog-category-id mono">#${category.id}</span></td>
            <td><div class="catalog-category-cell"><span>${icon('tag')}</span><div><strong>${escapeHtml(category.name)}</strong>
                ${category.parent_id ? '<small class="category-parent-path">' + escapeHtml(categoryPath(category)) + '</small>' : '<small class="category-parent-path">Danh mục cấp đầu</small>'}
            </div></div></td>
            <td><code class="slug-tag">${escapeHtml(category.slug)}</code></td>
            <td class="align-right"><span class="catalog-category-total">${integer(state.categoryProductCounts.get(category.id) || 0)}</span><span class="catalog-category-unit"> sản phẩm</span></td>
            <td><div class="catalog-row-actions">
                <button type="button" class="catalog-action" data-action="edit-category" data-id="${category.id}"
                    aria-label="Sửa danh mục ${escapeHtml(category.name)}">${icon('edit')}Sửa</button>
                <button type="button" class="catalog-action catalog-action-danger" data-action="delete-category" data-id="${category.id}"
                    aria-label="Xóa danh mục ${escapeHtml(category.name)}">${icon('trash')}Xóa</button>
            </div></td>
        </tr>`,
            )
            .join('');
        if (!visible.length) {
            $('#category-rows').innerHTML =
                '<tr><td colspan="5"><div class="catalog-empty"><h3>' +
                (state.categories.length ? 'Không tìm thấy danh mục' : 'Chưa có danh mục') +
                '</h3><p>' +
                (state.categories.length
                    ? 'Thử từ khóa khác hoặc xóa nội dung tìm kiếm.'
                    : 'Tạo danh mục đầu tiên để sắp xếp sản phẩm của cửa hàng.') +
                '</p></div></td></tr>';
        }
    }

    /** Tạo slug tiếng Việt không dấu tự động khi nhập danh mục mới. */
    function slugify(text) {
        return text
            .toString()
            .toLowerCase()
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .replace(/[đĐ]/g, 'd')
            .replace(/[^a-z0-9\s-]/g, '')
            .trim()
            .replace(/\s+/g, '-')
            .replace(/-+/g, '-');
    }

    /** Gợi ý slug trong cùng phạm vi với các hàm DOM; giữ slug khi ADMIN đã sửa thủ công. */
    document.addEventListener('input', (event) => {
        const input = event.target;
        if (input.id === 'create-category-name') {
            const slugInput = $('#create-category-slug');
            if (slugInput && !slugInput.dataset.touched) {
                slugInput.value = slugify(input.value);
            }
        } else if (input.id === 'create-category-slug') {
            input.dataset.touched = 'true';
        }
    });

    /** Mỗi lần mở lại hoặc đổi tài khoản, form danh mục được gợi ý slug cho bản nháp mới. */
    document.addEventListener('reset', (event) => {
        if (event.target.id === 'category-create') {
            delete $('#create-category-slug').dataset.touched;
        }
    });

    /** Gợi ý slug hãng mới; khi ADMIN tự sửa slug thì giữ nguyên bản nhập tay. */
    document.addEventListener('input', (event) => {
        if (event.target.id === 'create-brand-slug') event.target.dataset.touched = 'true';
        if (event.target.id === 'create-brand-name' && !$('#create-brand-slug').dataset.touched) {
            $('#create-brand-slug').value = slugify(event.target.value);
        }
    });
    document.addEventListener('reset', (event) => {
        if (event.target.id === 'brand-create') delete $('#create-brand-slug').dataset.touched;
    });

    /** Trang sản phẩm dùng pathname; dashboard và lịch sử đơn giữ đường dẫn hash tương thích. */
    function currentPagePath() {
        if (state.view === 'shop' && state.shopTab === 'product') return productPagePath(state.productId);
        if (state.view === 'shop' && state.shopTab === 'catalog') {
            const query = new URLSearchParams();
            if ($('#catalog-category').value) query.set('categoryId', $('#catalog-category').value);
            if (state.catalogBrandId) query.set('brandId', state.catalogBrandId);
            if ($('#catalog-query').value.trim()) query.set('q', $('#catalog-query').value.trim());
            if (state.catalogMinPrice !== '') query.set('minPrice', state.catalogMinPrice);
            if (state.catalogMaxPrice !== '') query.set('maxPrice', state.catalogMaxPrice);
            if (state.catalogSort !== 'id,asc') query.set('sort', state.catalogSort);
            return '/' + (query.size ? '?' + query.toString() : '') + '#shop';
        }
        const hash =
            state.view === 'portal'
                ? '#portal/' + state.portalTab
                : state.shopTab === 'orders'
                  ? '#shop/orders'
                  : state.shopTab === 'account'
                    ? '#shop/account'
                    : state.shopTab === 'help'
                      ? '#shop/help'
                      : '#shop';
        return '/' + hash;
    }

    /** Thay URL khi đổi phiên; không để đường dẫn sản phẩm đi kèm hash quản trị. */
    function replaceHash() {
        window.history.replaceState(null, '', currentPagePath());
        routedLocation = window.location.href;
    }

    /** Điều hướng trong cùng phiên giữ giỏ; thêm history để nút Quay lại/Tiến hoạt động tự nhiên. */
    function pushPage() {
        const path = currentPagePath();
        if (window.location.pathname + window.location.search + window.location.hash !== path) {
            window.history.pushState(null, '', path);
        }
        routedLocation = window.location.href;
    }

    /** Kiểm tra quyền trước khi mở không gian hoặc gọi API của tab nội bộ. */
    async function activateView(view, tab, { productId = null, navigation = 'push' } = {}) {
        closeSearchSuggestions();
        if (view === 'portal') {
            if (!isOperator()) {
                openAuth('portal');
                return;
            }
            state.view = 'portal';
            state.portalTab = canPortalTab(tab) ? tab : 'queue';
            if (['products', 'categories', 'admin'].includes(state.portalTab)) state.catalogExpanded = true;
        } else {
            if (['orders', 'account'].includes(tab) && !hasRole('CUSTOMER')) {
                openAuth(tab);
                return;
            }
            state.view = 'shop';
            state.shopTab = ['orders', 'product', 'account', 'help'].includes(tab) ? tab : 'catalog';
        }
        invalidateOrderRefresh();
        // Rời trang chi tiết phải hủy GET chậm trước khi nó kịp cập nhật DOM hoặc giá trong giỏ.
        channels.get('shop-product-detail')?.abort();
        channels.delete('shop-product-detail');
        channels.get('shop-product-availability')?.abort();
        channels.delete('shop-product-availability');
        channels.get('profile-read')?.abort();
        channels.delete('profile-read');
        state.availabilityEpoch++;
        state.productId = state.view === 'shop' && state.shopTab === 'product' ? Number(productId) : null;
        if (state.shopTab !== 'product' || state.view !== 'shop') {
            $('#shop-product-detail-body').replaceChildren();
            delete $('#shop-product-detail-body').dataset.productId;
        }
        state.order = null;
        renderContext();
        renderOrder();
        if (navigation === 'replace') replaceHash();
        else if (navigation === 'push') pushPage();
        else routedLocation = window.location.href;
        if (state.view === 'shop' && state.shopTab === 'product') window.scrollTo({ top: 0, behavior: 'auto' });
        await refreshSection();
    }

    /** Chỉ tải khu vực đang xem; staff không được gọi reports/ledger từ menu hoặc hash. */
    async function refreshSection() {
        if (state.view === 'shop') {
            if (state.shopTab === 'orders') await loadOrders();
            else if (state.shopTab === 'account') await loadProfile();
            else if (state.shopTab === 'product') await loadShopProductDetail();
            else if (state.shopTab === 'help') $('#purchase-help-title').focus({ preventScroll: true });
            else await Promise.all([loadCatalog(), ...(state.discoveryDirty ? [loadBestsellers()] : [])]);
            return;
        }
        const loaders = {
            queue: loadQueue,
            inventory: loadInventory,
            ledger: loadLedger,
            reports: loadReports,
            products: loadManage,
            categories: loadCategoryList,
            admin: loadManage,
        };
        if (canPortalTab(state.portalTab)) await loaders[state.portalTab]();
    }

    /** Pager độc lập cho giỏ/catalog/đơn và các báo cáo; chặn tên pager không hợp lệ. */
    async function changePage(name, direction) {
        if (!Object.hasOwn(state.pages, name)) return;
        state.pages[name] = Math.max(0, state.pages[name] + direction);
        const loaders = {
            catalog: loadCatalog,
            orders: loadOrders,
            queue: loadQueue,
            inventory: loadInventory,
            ledger: loadLedger,
            revenue: loadRevenue,
            top: loadTop,
            low: loadLow,
            manage: loadManage,
            products: loadManage,
        };
        if (name !== 'catalog' && name !== 'orders' && !isOperator()) return;
        if (['ledger', 'revenue', 'top', 'low'].includes(name) && !hasRole('ADMIN', 'MANAGER')) return;
        await loaders[name]();
    }

    /** CDN lỗi thì thử ảnh danh mục đúng một lần; mất mạng vẫn có thông báo và không lặp tải vô hạn. */
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
                    $('.brand-monogram', media).hidden = false;
                }
                return;
            }
            if (image instanceof HTMLImageElement && image.hasAttribute('data-color-thumbnail')) {
                image.hidden = true;
                return;
            }
            if (image instanceof HTMLImageElement && image.hasAttribute('data-gallery-thumbnail')) {
                image.hidden = true;
                $('.gallery-thumb-error', image.closest('.gallery-thumbnail')).hidden = false;
                return;
            }
            if (image instanceof HTMLImageElement && image.hasAttribute('data-gallery-preview-image')) {
                image.hidden = true;
                $('.gallery-preview-error', image.closest('.gallery-preview-item')).hidden = false;
                return;
            }
            if (image instanceof HTMLImageElement && image.hasAttribute('data-catalog-image')) {
                image.hidden = true;
                return;
            }
            // Banner giữ tên/giá/liên kết khi CDN lỗi; không thay bằng ảnh của sản phẩm khác.
            if (image instanceof HTMLImageElement && image.hasAttribute('data-hero-image')) {
                image.hidden = true;
                $('.hero-image-error', image.closest('.hero-device-media')).hidden = false;
                return;
            }
            if (!(image instanceof HTMLImageElement) || !image.classList.contains('product-card-img')) return;
            // Ảnh thay thế sẽ chạy hiệu ứng khi tải xong; lỗi tải không được giữ trạng thái ảnh mờ.
            image.classList.remove('gallery-image-enter');
            const fallback = image.dataset.imageFallback;
            if (!image.dataset.fallbackAttempted && fallback && image.getAttribute('src') !== fallback) {
                image.dataset.fallbackAttempted = 'true';
                image.src = fallback;
                const caption = $('.product-photo-caption', image.closest('.product-card-img-wrap'));
                caption.textContent = 'Ảnh minh họa';
                caption.hidden = false;
                return;
            }
            image.hidden = true;
            const wrapper = image.closest('.product-card-img-wrap');
            wrapper.classList.add('product-image-unavailable');
            $('.product-image-error', wrapper).hidden = false;
        },
        true,
    );

    /** Delegation giữ event khi card/hàng bảng được render lại; nút busy chống nhấp lặp tại client. */
    document.addEventListener('click', (event) => {
        if (state.categoryMenuOpen && !isShopCategoryTarget(event.target)) setShopCategoryMenu(false);
        if (!state.categoryMenuInline && state.homeCategoryId !== null && !event.target.closest('#home-categories'))
            closeHomeCategoryMenu();
        const catalogLink = event.target.closest('[data-catalog-link]');
        if (catalogLink) {
            if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            event.preventDefault();
            window.history.pushState(null, '', catalogLink.href);
            execute(routeFromLocation);
            return;
        }
        const productLink = event.target.closest('[data-product-link]');
        if (productLink) {
            // Giữ hành vi liên kết native khi mở tab mới bằng Ctrl/Cmd, Shift hoặc nút chuột giữa.
            if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            closeSearchSuggestions();
            event.preventDefault();
            execute(() => activateView('shop', 'product', { productId: productLink.dataset.productId }));
            return;
        }
        const shopLink = event.target.closest('[data-shop-link]');
        if (shopLink) {
            if (event.button !== 0 || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
            event.preventDefault();
            execute(() => activateView('shop', 'catalog'));
            return;
        }
        const button = event.target.closest('button');
        if (!button || button.disabled) return;
        if (button.dataset.demoRole) {
            execute(() => authenticate(DEMO_ACCOUNTS[button.dataset.demoRole], false, true));
            return;
        }
        if (button.dataset.shopTab) {
            execute(() => activateView('shop', button.dataset.shopTab));
            return;
        }
        if (button.dataset.action === 'toggle-catalog-menu') {
            if (hasRole('ADMIN')) {
                state.catalogExpanded = !state.catalogExpanded;
                renderCatalogNavigation();
            }
            return;
        }
        if (button.dataset.portalTab) {
            execute(() => activateView('portal', button.dataset.portalTab));
            return;
        }
        if (button.dataset.authMode) {
            setAuthMode(button.dataset.authMode);
            return;
        }
        if (button.hasAttribute('data-category')) {
            $('#catalog-category').value = button.dataset.category;
            state.catalogBrandId = '';
            renderCatalogBrandOptions();
            execute(reloadCatalogFilters);
            return;
        }
        if (button.dataset.page) {
            execute(() => busy(button, () => changePage(button.dataset.page, Number(button.dataset.direction))));
            return;
        }
        const id = Number(button.dataset.id);
        const action = button.dataset.action;
        if (action === 'review-product') {
            execute(() => busy(button, () => openProductReview(id, Number(button.dataset.productId))));
        } else if (action === 'review-page') {
            execute(() => busy(button, () => loadProductReviews(Number(button.dataset.productId), Number(button.dataset.page))));
        } else if (action === 'retry-search-suggestions') {
            // Nút thử lại sẽ bị ẩn khi dựng skeleton; chuyển focus về input trước để không mất phiên gợi ý.
            $('#catalog-query').focus({ preventScroll: true });
            queueSearchSuggestions({ immediate: true });
        } else if (action === 'quick-price')
            execute(() => busy(button, () => applyQuickPrice(button.dataset.priceChip)));
        // Đọc catalog có cơ chế hủy request cũ; giữ nút hãng có focus và cho phép đổi lựa chọn ngay.
        else if (action === 'quick-brand') execute(() => applyCatalogBrand(button.dataset.brandChip));
        else if (action === 'refresh-bestsellers') execute(() => busy(button, loadBestsellers));
        else if (action === 'queue-status')
            execute(() => busy(button, () => applyQueueStatus(button.dataset.queueStatus)));
        else if (action === 'sticky-add-cart') {
            const form = $('#shop-product-add-form');
            const main = form && $('button[type="submit"]', form);
            if (main && !main.disabled) form.requestSubmit(main);
        } else if (action === 'explore-products') {
            $('#cart-dialog').close();
            execute(async () => {
                if (state.view !== 'shop' || state.shopTab !== 'catalog') await activateView('shop', 'catalog');
                $('#product-shelf').scrollIntoView({
                    block: 'start',
                    behavior: reducedStorefrontMotion.matches ? 'auto' : 'smooth',
                });
                $('#catalog-title').tabIndex = -1;
                $('#catalog-title').focus({ preventScroll: true });
            });
        } else if (action === 'open-auth') openAuth();
        else if (action === 'close-auth') $('#auth-dialog').close();
        else if (action === 'open-cart') {
            renderCart();
            openDialog('cart-dialog');
        } else if (action === 'close-cart') $('#cart-dialog').close();
        else if (action === 'open-portal') execute(() => activateView('portal', state.portalTab));
        else if (action === 'open-shop') execute(() => activateView('shop', 'catalog'));
        else if (action === 'my-orders') execute(() => activateView('shop', 'orders'));
        else if (action === 'my-account') execute(() => activateView('shop', 'account'));
        else if (action === 'dismiss-notice') $('#api-notice').hidden = true;
        else if (action === 'dismiss-toast') button.closest('.toast').remove();
        else if (action === 'logout') {
            clearSession();
            renderIdentity();
            replaceHash();
            notify('success', 'Đã đăng xuất. Bạn có thể tiếp tục mua sắm.');
            execute(() => {
                return loadReferences().then(loadCatalog);
            });
        } else if (action === 'toggle-category-menu') setShopCategoryMenu(!state.categoryMenuOpen);
        else if (action === 'close-category-menu') setShopCategoryMenu(false, { restoreFocus: true });
        else if (action === 'browse-category') execute(() => browseShopCategory(button.dataset.menuCategory));
        else if (action === 'browse-price-range') {
            // Giữ hãng khi đổi giá trong cùng nhóm, dù dùng menu header hay menu bên trái.
            const categoryId = button.dataset.menuCategory ?? state.menuCategoryId;
            execute(() =>
                browseShopCategory(
                    categoryId,
                    button.dataset.priceRange,
                    $('#catalog-category').value === categoryId ? state.catalogBrandId : '',
                ),
            );
        } else if (action === 'browse-brand')
            execute(() => browseShopCategory(button.dataset.menuCategory, 'all', button.dataset.brandId));
        else if (action === 'clear-catalog-filter') execute(() => clearCatalogFilter(button.dataset.filter));
        else if (action === 'scroll-product-info') {
            document.getElementById(button.dataset.target)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
        } else if (action === 'refresh-product-availability') execute(loadProductAvailability);
        else if (action === 'select-product-branch') {
            state.branchId = String(id);
            saveCart();
            renderWarehouses();
            execute(loadProductAvailability);
        } else if (action === 'gallery-select') selectGalleryImage(Number(button.dataset.photoIndex));
        else if (action === 'gallery-step') {
            const gallery = $('.shop-product-gallery');
            if (gallery) selectGalleryImage(Number(gallery.dataset.selectedIndex) + Number(button.dataset.direction));
        } else if (action === 'select-product-version') {
            selectProductVersion(id);
        } else if (action === 'manage-product-version') {
            execute(() =>
                manageProductVariants(
                    Number($('#product-variants-dialog').dataset.productId),
                    id,
                    $('#product-variants-dialog').dataset.panel,
                ),
            );
        } else if (action === 'configuration-panel' && hasRole('ADMIN')) {
            setConfigurationPanel(button.dataset.panel);
        } else if (action === 'delete-product-version' && hasRole('ADMIN')) {
            confirmConfigurationArchive('version', id);
        } else if (action === 'delete-product-color' && hasRole('ADMIN')) {
            confirmConfigurationArchive('color', id);
        } else if (action === 'restore-product-version' && hasRole('ADMIN')) {
            execute(() => busy(button, () => restoreProductConfiguration('version', id)));
        } else if (action === 'restore-product-color' && hasRole('ADMIN')) {
            execute(() => busy(button, () => restoreProductConfiguration('color', id)));
        } else if (action === 'select-product-color') {
            selectProductSku(id);
            $('[data-action="select-product-color"][data-id="' + id + '"]')?.focus({ preventScroll: true });
        } else if (action === 'add-cart')
            execute(() =>
                state.products.get(id)?.variants?.length
                    ? activateView('shop', 'product', { productId: id })
                    : addCart(id),
            );
        else if (action === 'remove-cart') {
            if (state.cartLoading || state.cartRestoreFailed) return;
            state.cart.delete(id);
            saveCart();
            renderCart();
        } else if (action === 'retry-cart') {
            execute(() => busy(button, restoreCart));
        } else if (action === 'cart-plus' || action === 'cart-minus') {
            const item = state.cart.get(id);
            if (item) execute(() => setCartQuantity(id, item.quantity + (action === 'cart-plus' ? 1 : -1)));
        } else if (action === 'refresh') execute(() => busy(button, refreshSection));
        else if (action === 'view-order') execute(() => busy(button, () => lookupOrder(id)));
        else if (action === 'pay-order') execute(() => busy(button, () => mutateOrder(id, 'pay')));
        else if (action === 'vnpay-order') execute(() => busy(button, () => payWithVNPay(id)));
        else if (action === 'cancel-order') confirmOrderAction(id, 'cancel');
        else if (action === 'operate-order') {
            if (button.dataset.operation === 'return') confirmOrderAction(id, 'return');
            else execute(() => busy(button, () => mutateOrder(id, button.dataset.operation)));
        } else if (action === 'dismiss-confirm') {
            state.pendingMutation = null;
            $('#confirm-dialog').close();
        } else if (action === 'confirm-mutation') {
            const pending = state.pendingMutation;
            if (!pending || pending.epoch !== state.epoch) return;
            state.pendingMutation = null;
            $('#confirm-dialog').close();
            if (pending.handler) {
                execute(() => busy(button, pending.handler));
            } else {
                execute(() => busy(button, () => mutateOrder(pending.id, pending.action)));
            }
        } else if (action === 'open-product-create' && hasRole('ADMIN')) {
            openProductCreate();
        } else if (action === 'open-category-create' && hasRole('ADMIN')) {
            const form = $('#category-create');
            if (form) form.reset();
            openDialog('category-create-dialog');
        } else if (action === 'edit-category') {
            openCategoryEdit(id);
        } else if (action === 'delete-category') {
            confirmCategoryDelete(id);
        } else if (action === 'open-brand-create' && hasRole('ADMIN')) {
            $('#brand-create').reset();
            renderBrandLogoPreview($('#create-brand-logo'));
            openDialog('brand-create-dialog');
        } else if (action === 'edit-brand-logo') {
            openBrandLogoEdit(id);
        } else if (action === 'remove-brand-logo' && hasRole('ADMIN')) {
            $('#edit-brand-logo').value = '';
            renderBrandLogoPreview($('#edit-brand-logo'));
        } else if (action === 'view-product') {
            execute(() => viewProductDetail(id));
        } else if (action === 'edit-product' && hasRole('ADMIN')) {
            openProductEdit(id);
        } else if (action === 'manage-product-variants' && hasRole('ADMIN')) {
            execute(() => busy(button, () => manageProductVariants(id)));
        } else if (action === 'manage-product-colors' && hasRole('ADMIN')) {
            execute(() => busy(button, () => manageProductVariants(id, null, 'colors')));
        } else if (action === 'add-spec-row' && hasRole('ADMIN')) {
            const editor = button.closest('[data-spec-editor]');
            execute(() => appendSpecRow(editor));
        } else if (action === 'remove-spec-row' && hasRole('ADMIN')) {
            const editor = button.closest('[data-spec-editor]');
            button.closest('.spec-editor-row').remove();
            $('[data-action="add-spec-row"]', editor).disabled = false;
        } else if (action === 'toggle-status' && hasRole('ADMIN')) {
            toggleProductStatus(id);
        } else if (action === 'reset-manage-filter') {
            $('#manage-query').value = '';
            $('#manage-category').value = '';
            $('#manage-status').value = '';
            state.pages.manage = 0;
            execute(loadManage);
        } else if (action === 'close-dialog') {
            button.closest('dialog')?.close();
        }
    });

    /** Chọn chi nhánh từ header hoặc giỏ có cùng state; số lượng sai không thay thế giá trị hợp lệ. */
    document.addEventListener('change', (event) => {
        const input = event.target;
        if (
            (input.id === 'configuration-version-select' || input.id === 'configuration-show-archived') &&
            hasRole('ADMIN')
        ) {
            const dialog = $('#product-variants-dialog');
            if (input.id === 'configuration-show-archived') dialog.dataset.showArchived = String(input.checked);
            execute(() =>
                manageProductVariants(
                    Number(dialog.dataset.productId),
                    input.id === 'configuration-version-select'
                        ? Number(input.value)
                        : Number(dialog.dataset.versionId),
                    dialog.dataset.panel,
                ),
            );
        } else if (input.hasAttribute('data-store-warehouse')) {
            state.branchId = input.value;
            execute(refreshCheckoutFee);
            saveCart();
            renderWarehouses();
            refreshProductStock();
            execute(loadProductAvailability);
        } else if (input.dataset.cartQuantity) {
            execute(() => setCartQuantity(Number(input.dataset.cartQuantity), Number(input.value)));
        } else if (input.id === 'catalog-category') {
            state.catalogBrandId = '';
            renderCatalogBrandOptions();
            execute(reloadCatalogFilters);
        } else if (input.id === 'catalog-brand') {
            execute(() => applyCatalogBrand(input.value));
        } else if (input.id === 'catalog-sort') {
            state.catalogSort = input.value;
            execute(reloadCatalogFilters);
        } else if (input.id === 'update-product') {
            prepareProductImageUpdate();
        } else if (input.id === 'remove-product-image') {
            renderProductImagePreview($('#update-product-image'));
        } else if (input.dataset.imageInput) {
            renderProductImagePreview(input);
        } else if (input.dataset.galleryInput) {
            renderGalleryInputPreview(input);
        }
    });

    /** Validation HTML trước POST và DTO validation tại server cùng bảo vệ mọi form. */
    document.addEventListener('submit', (event) => {
        event.preventDefault();
        const form = event.target;
        if (!form.reportValidity()) return;
        const button = $('button[type="submit"]', form);
        const handlers = {
            'review-form': submitProductReview,
            'catalog-filter': () => reloadCatalogFilters(),
            'catalog-price-filter': applyCatalogPriceDraft,
            'queue-filter': () => {
                state.order = null;
                renderOrder();
                state.pages.queue = 0;
                return loadQueue();
            },
            'order-create': createOrder,
            'profile-form': saveProfile,
            'order-lookup': () => lookupOrder(Number($('#order-id').value)),
            'product-create': () => createProduct(form),
            'product-update': () => updateProduct(form),
            'product-variant-create': () => saveProductVariant(form, true),
            'product-version-create': () => saveProductVersion(form, true),
            'shop-product-add-form': () => addProductFromDetail(form),
            'category-create': () => createCategory(form),
            'category-edit': () => updateCategory(form),
            'brand-create': () => createBrand(form),
            'brand-logo-edit': () => saveBrandLogo(form),
            'manage-filter': () => {
                state.pages.manage = 0;
                return loadManage();
            },
            'inventory-filter': () => {
                state.pages.inventory = 0;
                return loadInventory();
            },
            'stock-in': () => stockIn(form),
            'ledger-filter': () => {
                state.pages.ledger = 0;
                return loadLedger();
            },
            'report-filter': () => {
                if (
                    $('#report-from').value &&
                    $('#report-to').value &&
                    $('#report-from').value > $('#report-to').value
                ) {
                    throw new Error('Từ ngày phải trước hoặc bằng Đến ngày.');
                }
                ['revenue', 'top', 'low'].forEach((name) => {
                    state.pages[name] = 0;
                });
                return loadReports();
            },
        };
        if (form.matches('.version-admin-edit')) {
            event.preventDefault();
            execute(() => busy(form.querySelector('button[type="submit"]'), () => saveProductVersion(form)));
            return;
        }
        if (form.matches('.variant-admin-edit')) {
            execute(() => busy(button, () => saveProductVariant(form)));
            return;
        }
        if (form.id === 'otp-form') {
            execute(() => authenticate({ email: otpEmail, otp: $('#otp-code').value.trim() }, false, false, true));
        } else if (form.id === 'auth-form') {
            const body = { email: $('#auth-email').value.trim(), password: $('#auth-password').value };
            if (state.authMode === 'register') body.full_name = $('#auth-name').value.trim();
            execute(() => authenticate(body, state.authMode === 'register'));
        } else if (handlers[form.id]) execute(() => busy(button, handlers[form.id]));
    });

    /** Khôi phục bộ lọc của breadcrumb/URL chia sẻ; chỉ nhận danh mục và hãng thực sự tồn tại. */
    function restoreCatalogLocation() {
        const query = new URLSearchParams(window.location.search);
        const categoryId = query.get('categoryId') || '';
        const brandId = query.get('brandId') || '';
        $('#catalog-category').value = state.categories.some((value) => String(value.id) === categoryId)
            ? categoryId
            : '';
        state.catalogBrandId = state.brands.some((value) => String(value.id) === brandId) ? brandId : '';
        $('#catalog-query').value = query.get('q') || '';
        state.catalogMinPrice = query.get('minPrice') || '';
        state.catalogMaxPrice = query.get('maxPrice') || '';
        const sort = query.get('sort') || 'id,asc';
        const choices = [...$('#catalog-sort').options].map((option) => option.value);
        state.catalogSort = choices.includes(sort) ? sort : 'id,asc';
        $('#catalog-sort').value = state.catalogSort;
        state.pages.catalog = 0;
        renderCatalogBrandOptions();
        syncCatalogPriceFields();
    }

    let pendingVNPayReturn = null;

    async function consumeVNPayReturn() {
        if (!pendingVNPayReturn || !hasRole('CUSTOMER')) return;
        const result = pendingVNPayReturn;
        pendingVNPayReturn = null;
        await lookupOrder(result.orderId);
        const paid = ['CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED'].includes(state.order?.status);
        if (result.status === 'success' && paid) {
            notify('success', 'Thanh toán qua VNPay thành công!');
        } else {
            notify('error', 'Thanh toán VNPay chưa thành công. Vui lòng kiểm tra trạng thái đơn hàng.');
        }
    }

    /** Mở trực tiếp hoặc quay lại URL sản phẩm; hash quản trị vẫn kiểm tra quyền trước khi gọi API. */
    async function routeFromLocation() {
        if (window.location.hash.startsWith('#orders?')) {
            const query = new URLSearchParams(window.location.hash.slice('#orders?'.length));
            const orderId = Number(query.get('order_id'));
            const paymentStatus = query.get('payment_status');
            if (Number.isSafeInteger(orderId) && orderId > 0 && ['success', 'failed'].includes(paymentStatus)) {
                pendingVNPayReturn = { orderId, status: paymentStatus };
                if (!state.user) {
                    openAuth('orders');
                    return;
                }
                await activateView('shop', 'orders', { navigation: 'replace' });
                await consumeVNPayReturn();
                return;
            }
        }
        const productRoute = window.location.pathname.match(/^\/san-pham\/(\d+)$/);
        if (productRoute) {
            await activateView('shop', 'product', { productId: productRoute[1], navigation: 'none' });
            return;
        }
        const hash = window.location.hash.slice(1).split('/');
        if (hash[0] === 'product-shelf' && state.view === 'shop' && state.shopTab === 'catalog') return;
        if (hash[0] === 'portal' && isOperator()) {
            await activateView('portal', hash[1] === 'orders' ? 'queue' : hash[1] || 'queue', {
                navigation: 'replace',
            });
        } else if (hash[0] === 'shop') {
            if (!hash[1] || hash[1] === 'catalog') restoreCatalogLocation();
            await activateView('shop', hash[1] || 'catalog', { navigation: 'replace' });
        } else {
            if (!isOperator()) restoreCatalogLocation();
            await activateView(
                isOperator() ? 'portal' : 'shop',
                isOperator() ? (hasRole('MANAGER') ? 'reports' : 'queue') : 'catalog',
                { navigation: 'replace' },
            );
        }
    }

    /** Một lần quay lại có thể phát cả hai sự kiện; URL đã nhận không được tạo thêm history hoặc GET. */
    function onLocationChange() {
        if (routedLocation === window.location.href) return;
        routedLocation = window.location.href;
        execute(routeFromLocation);
    }
    window.addEventListener('popstate', onLocationChange);
    window.addEventListener('hashchange', onLocationChange);

    /** Tải lại trang kiểm tra actor qua users/me; request khởi tạo cũ không ghi đè đăng nhập mới. */
    async function loadShopContact() {
        try {
            const contact = await api('/storefront/contact', { anonymous: true });
            if (!contact.zalo_url) return;
            const url = new URL(contact.zalo_url);
            if (url.protocol !== 'https:' || url.hostname !== 'zalo.me' || url.username || url.password) return;
            const link = $('#zalo-contact');
            link.href = url.href;
            link.hidden = false;
        } catch {
            // Contact availability must not prevent browsing or checkout.
        }
    }

    async function loadProductReviews(productId, page = 0) {
        const target = $('#product-reviews-content');
        if (!target) return;
        try {
            const result = await api('/products/' + productId + '/reviews', { anonymous: true, query: { page }, channel: 'product-reviews' });
            if (!target.isConnected || !state.detailRoot || state.detailRoot.id !== productId) return;
            target.innerHTML = result.total_elements
                ? '<p><strong>' + Number(result.average_rating).toFixed(1) + '/5</strong> · ' + integer(result.total_elements)
                  + ' đánh giá · Dịch vụ: ' + Number(result.average_service_rating).toFixed(1) + '/5</p>'
                  + result.content.map(review => '<article class="product-review"><header><strong>' + escapeHtml(review.customer_name)
                    + '</strong><span class="meta-line">Đã mua hàng · ' + escapeHtml(dateTime(review.created_at)) + '</span></header>'
                    + '<p class="review-stars">' + '★'.repeat(review.rating) + '☆'.repeat(5 - review.rating)
                    + ' <span>Sản phẩm · Dịch vụ: ' + review.service_rating + '/5</span></p>'
                    + '<p class="review-comment">' + escapeHtml(review.comment) + '</p></article>').join('')
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
        const epoch = state.epoch;
        const reviews = await api('/orders/' + orderId + '/reviews');
        if (epoch !== state.epoch || !hasRole('CUSTOMER')) return;
        const existing = reviews.find(review => review.product_id === productId);
        const form = $('#review-form');
        form.reset();
        form.dataset.orderId = String(orderId);
        form.dataset.productId = String(productId);
        form.dataset.epoch = String(epoch);
        $('#review-title').textContent = existing ? 'Đánh giá của bạn' : 'Đánh giá sau khi nhận hàng';
        $('#review-rating').value = String(existing?.rating || 5);
        $('#review-service-rating').value = String(existing?.service_rating || 5);
        $('#review-comment').value = existing?.comment || '';
        $('#review-rating').disabled = Boolean(existing);
        $('#review-service-rating').disabled = Boolean(existing);
        $('#review-comment').readOnly = Boolean(existing);
        $('#review-submit').hidden = Boolean(existing);
        openDialog('review-dialog');
    }

    async function submitProductReview() {
        const form = $('#review-form');
        if (Number(form.dataset.epoch) !== state.epoch || !hasRole('CUSTOMER')) return;
        const comment = $('#review-comment').value.trim();
        if (!comment) throw new Error('Vui lòng nhập nhận xét.');
        const epoch = state.epoch;
        await api('/orders/' + form.dataset.orderId + '/reviews', { method: 'POST', body: {
            product_id: Number(form.dataset.productId), rating: Number($('#review-rating').value),
            service_rating: Number($('#review-service-rating').value), comment,
        } });
        if (epoch !== state.epoch) return;
        $('#review-dialog').close();
        notify('success', 'Cảm ơn bạn đã chia sẻ đánh giá!');
        if (state.detailRoot && state.shopTab === 'product') await loadProductReviews(state.detailRoot.id);
    }

    async function initialize() {
        void loadShopContact();
        const epoch = state.epoch;
        renderContext();
        renderCart();
        renderOrder();
        renderCatalogLoading();
        setAuthMode('login');
        let saved;
        try {
            saved = JSON.parse(sessionStorage.getItem(SESSION_KEY) || 'null');
        } catch {
            /* Storage lỗi không ngăn xem cửa hàng công khai. */
        }
        if (saved?.token) {
            state.token = saved.token;
            try {
                state.user = await api('/users/me', { channel: 'identity' });
            } catch (error) {
                if (error.name !== 'AbortError') handleError(error);
            }
            if (epoch !== state.epoch) return;
        }
        renderContext();
        await restoreCart();
        if (epoch !== state.epoch) return;
        await loadReferences();
        if (epoch !== state.epoch) return;
        await routeFromLocation();
    }
    window.setInterval(updateCountdown, 1000);
    execute(initialize);
})();
