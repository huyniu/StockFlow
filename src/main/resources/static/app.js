/* Dashboard mỏng gọi API thật cùng origin; mọi quyền, tồn kho và trạng thái đơn do backend quyết định. */
(() => {
    'use strict';

    const SESSION_KEY = 'stockflow.web.session';
    const MAX_QUANTITY = 2147483647;
    const DEMO_ACCOUNTS = {
        ADMIN: { email: 'admin@stockflow.com', password: 'Admin@123' },
        MANAGER: { email: 'manager@stockflow.com', password: 'Manager@123' },
        WAREHOUSE_STAFF: { email: 'staff.hn@stockflow.com', password: 'Staff@123' },
        CUSTOMER: { email: 'customer@stockflow.com', password: 'Customer@123' }
    };
    const STATUS_LABELS = {
        PENDING: 'Chờ thanh toán', CONFIRMED: 'Đã xác nhận', PACKED: 'Đã đóng gói',
        SHIPPED: 'Đang giao', DELIVERED: 'Đã giao', CANCELLED: 'Đã hủy',
        EXPIRED: 'Hết hạn', RETURNED: 'Đã trả hàng'
    };
    const MOVEMENT_LABELS = {
        GOODS_RECEIPT: 'Nhập hàng', RESERVATION_HOLD: 'Giữ hàng',
        RESERVATION_RELEASE: 'Nhả hàng', DISPATCH: 'Xuất hàng',
        RETURN_RESTOCK: 'Hoàn kho', STOCK_ADJUSTMENT: 'Điều chỉnh'
    };
    const ROLE_CLASSES = {
        ADMIN: 'role-admin', MANAGER: 'role-manager',
        WAREHOUSE_STAFF: 'role-warehouse_staff', CUSTOMER: 'role-customer'
    };
    const state = {
        token: null, user: null, epoch: 0, authBusy: false, authMode: 'login',
        tab: 'catalog', categories: [], warehouses: [], products: new Map(), cart: new Map(),
        order: null, catalog: null, pages: {
            catalog: 0, orders: 0, inventory: 0, ledger: 0, revenue: 0, top: 0, low: 0
        }
    };
    const requests = new Set();
    const channels = new Map();
    const numberFormat = new Intl.NumberFormat('vi-VN');
    const moneyFormat = new Intl.NumberFormat('vi-VN', {
        style: 'currency', currency: 'VND', minimumFractionDigits: 0, maximumFractionDigits: 2
    });

    /** Truy vấn DOM tập trung để không phụ thuộc thư viện frontend. */
    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
    /** Escape dữ liệu backend trước khi ghép HTML, kể cả SKU, tên, email và ghi chú ledger. */
    const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, character => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[character]));
    /** Các bộ định dạng chỉ nhận số hữu hạn, không hiển thị NaN khi response lỗi. */
    const amount = value => moneyFormat.format(Number.isFinite(Number(value)) ? Number(value) : 0);
    const integer = value => numberFormat.format(Number.isFinite(Number(value)) ? Number(value) : 0);
    /** Kiểm tra role ở giao diện chỉ phục vụ hiển thị; backend vẫn kiểm tra tất cả request. */
    const hasRole = (...roles) => Boolean(state.user && roles.includes(state.user.role));
    const icon = name => '<svg class="icon" aria-hidden="true"><use href="#i-' + name + '"/></svg>';
    /** Ngày giờ Việt Nam để đọc dễ; khoảng ngày báo cáo được gửi theo contract UTC. */
    function dateTime(value) {
        const date = new Date(value);
        return value && !Number.isNaN(date.getTime()) ? date.toLocaleString('vi-VN') : '—';
    }
    /** Badge trạng thái chỉ dùng class từ danh sách enum đã biết, không nhận class tùy ý từ API. */
    function statusBadge(status) {
        const known = Object.hasOwn(STATUS_LABELS, status);
        return '<span class="badge ' + (known ? 'status-' + status.toLowerCase() : 'neutral')
            + '" title="' + escapeHtml(STATUS_LABELS[status] || status) + '">' + escapeHtml(status) + '</span>';
    }
    /** Lỗi HTTP giữ mã thật và thông điệp tiếng Việt, không đưa Authorization/token ra DOM. */
    class ApiError extends Error {
        constructor(status, reason, payload, path) {
            const details = (payload?.errors || []).map(error => error.message).filter(Boolean);
            super([payload?.message || reason || 'Không thể thực hiện yêu cầu.', ...details].join(' '));
            this.status = status;
            this.reason = reason;
            this.path = path;
        }
    }

    /** Hủy request cũ khi đổi tài khoản; epoch ngăn response chậm ghi dữ liệu sang vai trò mới. */
    function cancelRequests() {
        state.epoch++;
        requests.forEach(controller => controller.abort());
        requests.clear();
        channels.clear();
    }
    /** API cùng origin với JWT; mỗi kênh GET chỉ nhận response mới nhất khi đổi filter/phân trang. */
    async function api(path, { method = 'GET', body, query, anonymous = false, channel } = {}) {
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
        if (state.token && !anonymous) headers.Authorization = 'Bearer ' + state.token;
        if (body !== undefined) headers['Content-Type'] = 'application/json';
        try {
            const response = await fetch(url, {
                method, headers, signal: controller.signal, credentials: 'omit',
                body: body !== undefined ? JSON.stringify(body) : undefined
            });
            const raw = await response.text();
            if (epoch !== state.epoch || (channel && channels.get(channel) !== controller)) {
                throw new DOMException('Phiên giao diện đã thay đổi.', 'AbortError');
            }
            let payload;
            try { payload = raw ? JSON.parse(raw) : null; }
            catch { payload = { message: 'Hệ thống trả dữ liệu không hợp lệ. Vui lòng thử lại.' }; }
            if (!response.ok) {
                const error = new ApiError(response.status, payload?.error || response.statusText, payload, url.pathname);
                if (response.status === 401 && state.token && !anonymous) {
                    clearSession();
                    renderIdentity();
                    renderPermissions();
                }
                throw error;
            }
            $('#last-sync').textContent = 'Cập nhật lúc ' + new Date().toLocaleTimeString('vi-VN');
            return payload;
        } catch (error) {
            if (error.name === 'AbortError' || error instanceof ApiError) throw error;
            throw new ApiError(0, 'Không thể kết nối', {
                message: 'Không kết nối được hệ thống. Kiểm tra ứng dụng Spring Boot đang chạy rồi thử lại.'
            }, url.pathname);
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
        notice.innerHTML = icon(error ? 'alert' : 'check')
            + '<div class="notice-content"><strong>' + escapeHtml(title || (error ? 'Không thể thực hiện' : 'Thành công'))
            + '</strong><span>' + escapeHtml(message) + '</span>'
            + (path ? '<small class="mono">' + escapeHtml(path) + '</small>' : '') + '</div>'
            + '<button class="icon-button" type="button" data-action="dismiss-notice" aria-label="Đóng thông báo">' + icon('close') + '</button>';
        const toast = document.createElement('div');
        toast.className = 'toast' + (error ? ' error' : '');
        toast.innerHTML = icon(error ? 'alert' : 'check')
            + '<div><strong>' + escapeHtml(title || (error ? 'Thông báo lỗi' : 'Thành công'))
            + '</strong><p>' + escapeHtml(message) + '</p></div>'
            + '<button class="icon-button" type="button" data-action="dismiss-toast" aria-label="Đóng toast">' + icon('close') + '</button>';
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
        try { await operation(); }
        finally {
            if (epoch === state.epoch && button.isConnected) {
                button.disabled = false;
                button.removeAttribute('aria-busy');
                renderPermissions();
            }
        }
    }

    /** JWT chỉ tồn tại trong sessionStorage của tab; không lưu mật khẩu người dùng. */
    function saveSession() {
        try { sessionStorage.setItem(SESSION_KEY, JSON.stringify({ token: state.token })); }
        catch { /* Trình duyệt chặn storage vẫn dùng được phiên trong bộ nhớ. */ }
    }
    /** Xóa dữ liệu nội bộ ngay trước khi đổi actor, kể cả form và kết quả báo cáo đã tải. */
    function clearSession() {
        cancelRequests();
        state.token = null;
        state.user = null;
        state.order = null;
        state.authBusy = false;
        state.warehouses = [];
        state.cart.clear();
        // Nút đang chờ của phiên cũ phải được mở lại trước khi áp dụng quyền của actor mới.
        $$('[aria-busy="true"]').forEach(button => {
            button.removeAttribute('aria-busy');
            button.disabled = false;
        });
        Object.keys(state.pages).forEach(key => { state.pages[key] = 0; });
        try { sessionStorage.removeItem(SESSION_KEY); }
        catch { /* Không có storage thì chỉ cần xóa state hiện tại. */ }
        ['orders', 'inventory', 'ledger', 'revenue', 'top', 'low'].forEach(name => {
            $('#' + name + '-rows').innerHTML = '';
            $('#' + name + '-pagination').innerHTML = '';
            const count = $('#' + name + '-count');
            if (count) count.textContent = '—';
        });
        ['stock-available', 'stock-reserved', 'stock-physical'].forEach(id => { $('#' + id).textContent = '—'; });
        $('#order-summary').innerHTML = '';
        $('#summary-statuses').innerHTML = '';
        ['order-lookup', 'stock-in', 'inventory-filter', 'ledger-filter', 'report-filter', 'product-create', 'product-update']
            .forEach(id => { $('#' + id).reset(); });
        $('#api-notice').hidden = true;
        $('#toasts').replaceChildren();
        renderWarehouses();
        renderCart();
        renderOrder();
    }
    /** Danh tính lấy từ login/users-me; badge màu phân biệt vai trò và nút demo đang dùng. */
    function renderIdentity() {
        $('#identity-email').textContent = state.user?.email || 'Chưa đăng nhập';
        $('#identity-role').textContent = state.user?.role || 'KHÁCH';
        $('#identity-role').className = 'badge ' + (ROLE_CLASSES[state.user?.role] || 'neutral');
        $('#open-login').hidden = Boolean(state.user);
        $('#logout').hidden = !state.user;
        $$('[data-demo-role]').forEach(button => {
            const active = state.user?.email === DEMO_ACCOUNTS[button.dataset.demoRole].email;
            button.classList.toggle('active', active);
            button.setAttribute('aria-pressed', String(active));
            button.disabled = state.authBusy;
        });
        $('#auth-submit').disabled = state.authBusy;
    }
    /** Chỉ mở form phù hợp; thẻ kiểm tra quyền gọi API thật để trình diễn response 401/403. */
    function renderPermissions() {
        $$('[data-roles]').forEach(element => {
            element.hidden = !hasRole(...element.dataset.roles.split(','));
        });
        const operational = hasRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF');
        $('#inventory-denied').hidden = operational;
        $('#inventory-content').hidden = !operational;
        const reports = hasRole('ADMIN', 'MANAGER');
        $('#reports-denied').hidden = reports;
        $('#reports-content').hidden = !reports;
        $('#customer-order-list').hidden = !hasRole('CUSTOMER');
        $('#orders-role-note').hidden = hasRole('CUSTOMER');
        $('#orders-list-title').textContent = hasRole('CUSTOMER') ? 'Đơn hàng của tôi' : 'Tra cứu theo quyền';
        $('#cart-permission').textContent = hasRole('CUSTOMER')
            ? 'Giữ hàng đồng thời; không bán vượt tồn kho.'
            : 'Chuyển sang Customer hoặc đăng ký tài khoản để đặt hàng.';
        const create = $('#create-order');
        create.disabled = !hasRole('CUSTOMER') || state.cart.size === 0 || state.authBusy
            || create.getAttribute('aria-busy') === 'true';
    }
    /** Mỗi lần đăng nhập đổi JWT thật và tải lại lựa chọn kho theo database hiện tại. */
    async function authenticate(credentials, register = false) {
        if (state.authBusy) return;
        clearSession();
        state.authBusy = true;
        renderIdentity();
        renderPermissions();
        const epoch = state.epoch;
        $('#auth-error').hidden = true;
        try {
            const result = await api(register ? '/auth/register' : '/auth/login', {
                method: 'POST', body: credentials, anonymous: true
            });
            state.token = result.access_token;
            state.user = result.user;
            saveSession();
            renderIdentity();
            renderPermissions();
            if ($('#auth-dialog').open) $('#auth-dialog').close();
            $('#auth-password').value = '';
            notify('success', register ? 'Tài khoản Customer đã được tạo. Chào mừng bạn!'
                : 'Đã đăng nhập ' + result.user.email + ' · ' + result.user.role,
                register ? 'HTTP 201 Created' : 'HTTP 200 OK');
            await loadReferences();
            await refreshTab();
        } catch (error) {
            if (epoch === state.epoch && $('#auth-dialog').open && error.name !== 'AbortError') {
                $('#auth-error').textContent = error.message;
                $('#auth-error').hidden = false;
            }
            throw error;
        } finally {
            if (epoch === state.epoch) {
                state.authBusy = false;
                renderIdentity();
                renderPermissions();
            }
        }
    }
    /** Modal thủ công có login/register; đăng ký không cho người dùng tự chọn role đặc quyền. */
    function setAuthMode(mode) {
        state.authMode = mode;
        const register = mode === 'register';
        $('#auth-name-field').hidden = !register;
        $('#auth-name').required = register;
        $('#auth-password').autocomplete = register ? 'new-password' : 'current-password';
        $('#auth-submit').textContent = register ? 'Tạo tài khoản Customer' : 'Đăng nhập';
        $('#auth-description').textContent = register
            ? 'Tài khoản mới có quyền Customer để đặt hàng và theo dõi đơn của mình.'
            : 'Tài khoản demo có sẵn khi ứng dụng chạy với profile demo.';
        $$('[data-auth-mode]').forEach(button => button.classList.toggle('active', button.dataset.authMode === mode));
        $('#auth-error').hidden = true;
    }

    /** Nạp danh mục và lựa chọn sản phẩm; tất cả tên/ID đều lấy từ backend thay vì mặc định 1/2/3. */
    async function loadReferences() {
        const results = await Promise.allSettled([
            loadCategories(), loadProductOptions(), loadWarehouses()
        ]);
        const failed = results.find(result => result.status === 'rejected' && result.reason.name !== 'AbortError');
        if (failed) handleError(failed.reason);
    }
    /** Danh mục công khai được tái sử dụng cho bộ lọc và form ADMIN. */
    async function loadCategories() {
        state.categories = await api('/categories', { anonymous: true, channel: 'categories' });
        const options = state.categories.map(category =>
            '<option value="' + category.id + '">' + escapeHtml(category.name) + '</option>').join('');
        const current = $('#catalog-category').value;
        $('#catalog-category').innerHTML = '<option value="">Tất cả danh mục</option>' + options;
        $('#catalog-category').value = current;
        $('#product-category').innerHTML = '<option value="">Chọn danh mục</option>' + options;
        $('#overview-categories').textContent = integer(state.categories.length);
    }
    /** Danh sách lựa chọn cho form nhỏ được phân trang 100/lần để không bỏ sót sản phẩm sau trang đầu. */
    async function loadProductOptions() {
        let page = 0;
        let result;
        const products = new Map();
        do {
            // Thứ tự ID cố định giữ các trang/dropdown ổn định khi giá hoặc trạng thái được cập nhật.
            result = await api('/products', { anonymous: true, query: { page, size: 100, sort: 'id,asc' }, channel: 'product-options' });
            result.content.forEach(product => products.set(product.id, product));
            page++;
        } while (!result.last);
        state.products = products;
        renderProductOptions();
        renderCart();
    }
    /** Endpoint DTO tối thiểu có JWT cung cấp kho hoạt động cho cả CUSTOMER, không mở /warehouses nội bộ. */
    async function loadWarehouses() {
        if (!state.token) { renderWarehouses(); return; }
        state.warehouses = await api('/warehouses/order-options', { channel: 'warehouses' });
        renderWarehouses();
    }
    /** Giữ lựa chọn hợp lệ; nhân viên demo ưu tiên kho Hà Nội theo code ổn định, không theo ID. */
    function renderWarehouses() {
        const options = state.warehouses.map(warehouse => '<option value="' + warehouse.id + '">'
            + escapeHtml(warehouse.name) + ' · ' + escapeHtml(warehouse.code) + '</option>').join('');
        $$('[data-warehouse]').forEach(select => {
            const previous = select.value;
            const all = select.dataset.warehouse === 'all';
            select.innerHTML = '<option value="">' + (all ? 'Tất cả kho' : state.user ? 'Chọn kho hàng' : 'Đăng nhập để chọn kho')
                + '</option>' + options;
            if (state.warehouses.some(warehouse => String(warehouse.id) === previous)) select.value = previous;
            else if (state.user) {
                const first = hasRole('WAREHOUSE_STAFF')
                    ? state.warehouses.find(warehouse => warehouse.code === 'WH-HAN-01') || state.warehouses[0]
                    : state.warehouses[0];
                if (first && (!all || (hasRole('WAREHOUSE_STAFF') && select.id === 'inventory-warehouse'))) {
                    // Giá trị select luôn là chuỗi để thống nhất giữa trình duyệt và môi trường kiểm chứng DOM.
                    select.value = String(first.id);
                }
            }
        });
        $('#overview-warehouses').textContent = state.user ? integer(state.warehouses.length) : '—';
    }
    /** Dropdown dùng SKU/tên để chọn chính xác, giữ nguyên filter khi làm mới. */
    function renderProductOptions() {
        const options = [...state.products.values()].map(product => '<option value="' + product.id + '">'
            + escapeHtml(product.sku + ' · ' + product.name) + '</option>').join('');
        $$('[data-product]').forEach(select => {
            const previous = select.value;
            select.innerHTML = '<option value="">' + (select.dataset.product === 'all' ? 'Tất cả sản phẩm' : 'Chọn sản phẩm')
                + '</option>' + options;
            if (state.products.has(Number(previous))) select.value = previous;
        });
    }
    /** Ô sản phẩm dùng tên thật, SKU và biểu tượng trung tính, không gán ảnh giả cho catalog. */
    function productCell(product) {
        return '<div class="product-cell"><span class="product-symbol">' + icon('box') + '</span><div><strong>'
            + escapeHtml(product.name || product.product_name) + '</strong><span class="meta-line mono">'
            + escapeHtml(product.sku || product.product_sku || ('#' + product.product_id)) + '</span></div></div>';
    }
    /** Placeholder của bảng thống nhất để phân biệt trạng thái đang tải, trống và lỗi. */
    function emptyRows(id, columns, message) {
        $('#' + id + '-rows').innerHTML = '<tr><td colspan="' + columns + '" class="empty-cell">'
            + escapeHtml(message) + '</td></tr>';
    }
    /** Mỗi bảng dùng metadata phân trang từ API, không tự suy đoán số trang. */
    function renderPager(name, result) {
        const start = result.total_elements ? result.page * result.size + 1 : 0;
        const end = Math.min((result.page + 1) * result.size, result.total_elements);
        $('#' + name + '-pagination').innerHTML = '<span>' + integer(start) + '–' + integer(end)
            + ' trên ' + integer(result.total_elements) + ' kết quả</span><div class="pagination-actions">'
            + '<button class="button secondary" type="button" data-page="' + name + '" data-direction="-1" '
            + (result.page === 0 ? 'disabled' : '') + '>Trước</button><span>'
            + (result.total_pages ? result.page + 1 : 0) + ' / ' + result.total_pages + '</span>'
            + '<button class="button secondary" type="button" data-page="' + name + '" data-direction="1" '
            + (result.last ? 'disabled' : '') + '>Sau</button></div>';
    }
    /** Catalog public lọc danh mục/trạng thái; button Thêm chỉ xuất hiện cho sản phẩm ACTIVE. */
    async function loadCatalog() {
        emptyRows('catalog', 4, 'Đang tải sản phẩm…');
        try {
            const result = await api('/products', {
                anonymous: true, channel: 'catalog',
                query: {
                    page: state.pages.catalog, size: 8, sort: 'id,asc',
                    categoryId: $('#catalog-category').value, status: $('#catalog-status').value
                }
            });
            state.catalog = result;
            result.content.forEach(product => state.products.set(product.id, product));
            $('#catalog-rows').innerHTML = result.content.map(product =>
                '<tr><td>' + productCell(product) + (product.status === 'INACTIVE' ? '<span class="meta-line">Ngừng kinh doanh</span>' : '')
                + '</td><td><span class="badge neutral">' + escapeHtml(product.category_name)
                + '</span></td><td class="align-right price">' + amount(product.unit_price)
                + '</td><td class="align-right"><button type="button" class="button add-button small" data-action="add-cart" data-id="'
                + product.id + '" aria-label="Thêm ' + escapeHtml(product.name) + ' vào giỏ" '
                + (product.status !== 'ACTIVE' ? 'disabled' : '') + '>' + icon('plus') + 'Thêm</button></td></tr>').join('');
            if (!result.content.length) emptyRows('catalog', 4, 'Chưa có sản phẩm phù hợp với bộ lọc.');
            $('#overview-products').textContent = integer(result.total_elements);
            $('#catalog-count').textContent = integer(result.total_elements) + ' sản phẩm';
            renderPager('catalog', result);
            renderCart();
        } catch (error) {
            if (error.name !== 'AbortError') emptyRows('catalog', 4, 'Chưa tải được catalog. Vui lòng làm mới.');
            throw error;
        }
    }
    /** Giỏ có nhiều mặt hàng, tổng tiền chỉ là ước tính; server tự lấy giá hiện tại khi tạo đơn. */
    function renderCart() {
        let total = 0;
        let quantity = 0;
        $('#cart-items').innerHTML = [...state.cart.values()].map(item => {
            const product = state.products.get(item.product.id) || item.product;
            total += Number(product.unit_price) * item.quantity;
            quantity += item.quantity;
            return '<div class="cart-line"><div class="cart-line-head"><div><strong>' + escapeHtml(product.name)
                + '</strong><span class="meta-line mono">' + escapeHtml(product.sku) + '</span></div>'
                + '<button class="icon-button" type="button" data-action="remove-cart" data-id="' + product.id
                + '" aria-label="Xóa ' + escapeHtml(product.name) + ' khỏi giỏ">' + icon('close') + '</button></div>'
                + '<div class="cart-line-foot"><label class="cart-quantity">SL<input type="number" min="1" max="' + MAX_QUANTITY
                + '" step="1" value="' + item.quantity + '" data-cart-quantity="' + product.id
                + '" aria-label="Số lượng ' + escapeHtml(product.name) + '"></label><span>' + amount(product.unit_price) + '</span></div></div>';
        }).join('');
        if (!state.cart.size) {
            $('#cart-items').innerHTML = '<div class="empty-state">' + icon('cart')
                + '<h3>Giỏ hàng đang trống</h3><p>Thêm sản phẩm từ danh mục để tạo một đơn hàng.</p></div>';
        }
        $('#cart-total').textContent = amount(total);
        $('#cart-count').textContent = integer(state.cart.size);
        $('#overview-cart').textContent = integer(quantity);
        renderPermissions();
    }
    /** Tăng lượng của dòng đã chọn, không tạo hai item trùng productId trong cùng đơn. */
    function addCart(id) {
        const product = state.products.get(id);
        if (!product || product.status !== 'ACTIVE') return;
        const existing = state.cart.get(id);
        if (existing && existing.quantity >= MAX_QUANTITY) throw new Error('Số lượng đã đạt giới hạn.');
        state.cart.set(id, { product, quantity: existing ? existing.quantity + 1 : 1 });
        renderCart();
    }
    /** CUSTOMER tạo đơn thật; giữ nguyên giỏ khi backend trả 409 để người dùng điều chỉnh. */
    async function createOrder() {
        if (!hasRole('CUSTOMER')) throw new Error('Đăng nhập Customer để đặt hàng.');
        if (!state.cart.size) throw new Error('Thêm ít nhất một sản phẩm vào giỏ.');
        const result = await api('/orders', { method: 'POST', body: {
            warehouse_id: Number($('#order-warehouse').value),
            items: [...state.cart.values()].map(item => ({ product_id: item.product.id, quantity: item.quantity }))
        } });
        state.cart.clear();
        state.order = result;
        state.pages.orders = 0;
        $('#order-id').value = result.id;
        renderCart();
        renderOrder();
        notify('success', 'Đơn ' + result.order_code + ' đã giữ hàng trong 15 phút. ID tra cứu: ' + result.id,
            'HTTP 201 Created', '/api/v1/orders');
        await activateTab('orders');
    }


    /** Tên kho được tra từ endpoint lựa chọn, các đơn vẫn dùng warehouse_id thực của server. */
    function warehouseName(id) {
        return state.warehouses.find(warehouse => warehouse.id === id)?.name || 'Kho #' + id;
    }
    /** Danh sách /orders/my chỉ được gọi cho CUSTOMER; người vận hành sử dụng ô tra cứu ID. */
    async function loadOrders() {
        if (!hasRole('CUSTOMER')) {
            if (state.order) await lookupOrder(state.order.id);
            return;
        }
        emptyRows('orders', 4, 'Đang tải đơn hàng…');
        try {
            const result = await api('/orders/my', { channel: 'my-orders', query: { page: state.pages.orders, size: 8 } });
            $('#orders-rows').innerHTML = result.content.map(order =>
                '<tr><td><strong class="mono">' + escapeHtml(order.order_code) + '</strong><span class="meta-line">#'
                + order.id + ' · ' + escapeHtml(warehouseName(order.warehouse_id)) + '</span></td><td>'
                + statusBadge(order.status) + '</td><td class="align-right price">' + amount(order.total_amount)
                + '</td><td><button class="button secondary small" type="button" data-action="view-order" data-id="'
                + order.id + '">Xem</button></td></tr>').join('');
            if (!result.content.length) emptyRows('orders', 4, 'Chưa có đơn hàng. Chọn sản phẩm để tạo đơn đầu tiên.');
            $('#orders-count').textContent = integer(result.total_elements) + ' đơn';
            renderPager('orders', result);
            if (!state.order && result.content.length) {
                state.order = result.content[0];
                $('#order-id').value = state.order.id;
            } else if (state.order) {
                const updated = result.content.find(order => order.id === state.order.id);
                if (updated) state.order = updated;
            }
            await hydrateOrderProducts();
            renderOrder();
        } catch (error) {
            if (error.name !== 'AbortError') emptyRows('orders', 4, 'Chưa tải được đơn hàng. Vui lòng làm mới.');
            throw error;
        }
    }
    /** Bổ sung tên sản phẩm cho snapshot order-item vì DTO chỉ có product_id, không tự bịa tên. */
    async function hydrateOrderProducts() {
        const ids = [...new Set((state.order?.items || []).map(item => item.product_id))];
        await Promise.all(ids.filter(id => !state.products.has(id)).map(async id => {
            const product = await api('/products/' + id, { anonymous: true, channel: 'order-product-' + id });
            state.products.set(product.id, product);
        }));
    }
    /** Chi tiết đơn trả 403 nếu sai chủ/kho; xóa chi tiết cũ trước mỗi lần tra cứu. */
    async function lookupOrder(id) {
        state.order = null;
        renderOrder();
        const order = await api('/orders/' + id, { channel: 'order-detail' });
        state.order = order;
        $('#order-id').value = order.id;
        await hydrateOrderProducts();
        renderOrder();
    }
    /** Nút thanh toán/hủy dựa trên role và status; server kiểm tra thêm ownership và trạng thái shipment. */
    function renderOrder() {
        const order = state.order;
        if (!order) {
            $('#order-detail').innerHTML = '<div class="empty-state">' + icon('order')
                + '<h3>Mỗi đơn hàng, một hành trình.</h3><p>Chọn một đơn hoặc nhập ID để xem mặt hàng và các thao tác phù hợp.</p></div>';
            return;
        }
        const canPay = hasRole('CUSTOMER') && ['PENDING', 'CONFIRMED'].includes(order.status);
        const canCancel = (hasRole('CUSTOMER') && order.status === 'PENDING')
            || (hasRole('ADMIN', 'MANAGER') && ['PENDING', 'CONFIRMED', 'PACKED'].includes(order.status));
        const items = order.items.map(item => {
            const product = state.products.get(item.product_id);
            return '<div class="order-item"><div><strong>' + escapeHtml(product?.name || 'Sản phẩm #' + item.product_id)
                + '</strong><span class="meta-line">' + integer(item.quantity) + ' × ' + amount(item.unit_price)
                + '</span></div><span class="price">' + amount(item.line_total) + '</span></div>';
        }).join('');
        $('#order-detail').innerHTML = '<div class="order-detail-head"><div><span class="eyebrow">CHI TIẾT ĐƠN #' + order.id
            + '</span><h3 class="mono">' + escapeHtml(order.order_code) + '</h3></div>' + statusBadge(order.status) + '</div>'
            + '<div class="order-info"><div><span>Kho lấy hàng</span><strong>' + escapeHtml(warehouseName(order.warehouse_id))
            + '</strong></div><div><span>Trạng thái</span><strong>' + escapeHtml(STATUS_LABELS[order.status] || order.status)
            + '</strong></div><div><span>Tạo lúc</span>' + escapeHtml(dateTime(order.created_at))
            + '</div><div><span>Hạn giữ hàng</span>' + escapeHtml(dateTime(order.reservation_expires_at)) + '</div></div>'
            + (order.status === 'PENDING' ? '<div class="countdown">' + icon('clock') + '<span data-expires="'
                + escapeHtml(order.reservation_expires_at) + '"></span></div>' : '')
            + '<div class="order-items">' + items + '</div><div class="order-total"><span>Tổng tiền đơn hàng</span><strong>'
            + amount(order.total_amount) + '</strong></div><div class="order-actions">'
            + (canPay ? '<button type="button" class="button primary" data-action="pay-order" data-id="' + order.id
                + '" data-mutation>' + icon('card') + (order.status === 'CONFIRMED' ? 'Xác nhận thanh toán lặp' : 'Thanh toán mô phỏng') + '</button>' : '')
            + (canCancel ? '<button type="button" class="button danger" data-action="cancel-order" data-id="' + order.id
                + '" data-mutation>' + icon('close') + (order.status === 'PENDING' ? 'Hủy đơn & nhả hàng' : 'Hủy đơn & hoàn kho') + '</button>' : '')
            + '<button type="button" class="button secondary" data-action="view-order" data-id="' + order.id
            + '">' + icon('refresh') + 'Tải lại</button></div>';
        updateCountdown();
    }
    /** Countdown chỉ hiển thị thời gian; không tự chuyển status EXPIRED ở client. */
    function updateCountdown() {
        $$('[data-expires]').forEach(element => {
            const remaining = Math.max(0, Math.ceil((new Date(element.dataset.expires).getTime() - Date.now()) / 1000));
            element.textContent = remaining > 0
                ? 'Còn ' + Math.floor(remaining / 60) + ' phút ' + String(remaining % 60).padStart(2, '0') + ' giây giữ hàng'
                : 'Đã đến hạn giữ hàng. Tải lại đơn để xem trạng thái từ hệ thống.';
        });
    }
    /** Thanh toán idempotent hoặc hủy trả tồn; response là trạng thái thực kể cả khi đơn vừa hết hạn. */
    async function mutateOrder(id, payment) {
        const path = '/orders/' + id + (payment ? '/payment-simulations/confirm' : '/cancel');
        const order = await api(path, { method: 'POST' });
        state.order = order;
        renderOrder();
        const message = payment
            ? order.status === 'EXPIRED' ? 'Đơn đã hết hạn; hệ thống nhả tồn kho và không thu tiền.'
                : 'Thanh toán đã xác nhận. Gọi lặp không tạo thêm payment hoặc xuất hàng.'
            : order.status === 'EXPIRED' ? 'Đơn đã hết hạn và tồn kho đã được giải phóng.'
                : 'Đã hủy đơn và hoàn lại đúng số lượng tồn kho.';
        notify('success', message, 'HTTP 200 OK · ' + order.status, '/api/v1' + path);
        if (hasRole('CUSTOMER')) await loadOrders();
    }

    /** Tra tồn kho theo bộ lọc thật, riêng staff phải chọn kho; các thẻ chỉ cộng trang hiện tại. */
    async function loadInventory() {
        if (!hasRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF')) return;
        emptyRows('inventory', 6, 'Đang tải tồn kho…');
        ['stock-available', 'stock-reserved', 'stock-physical'].forEach(id => { $('#' + id).textContent = '—'; });
        try {
            const result = await api('/inventories', {
                channel: 'inventory', query: {
                    warehouseId: $('#inventory-warehouse').value, productId: $('#inventory-product').value,
                    page: state.pages.inventory, size: 10
                }
            });
            $('#inventory-rows').innerHTML = result.content.map(stock =>
                '<tr><td><strong>' + escapeHtml(stock.product_name) + '</strong><span class="meta-line mono">#'
                + stock.product_id + ' · Tồn kho #' + stock.id + '</span></td><td>' + escapeHtml(stock.warehouse_name)
                + '</td><td class="align-right"><span class="' + (stock.available_quantity <= 10 ? 'low-number' : 'price')
                + '">' + integer(stock.available_quantity) + '</span></td><td class="align-right">'
                + integer(stock.reserved_quantity) + '</td><td class="align-right price">' + integer(stock.physical_quantity)
                + '</td><td><span class="subtle">' + escapeHtml(dateTime(stock.updated_at)) + '</span></td></tr>').join('');
            if (!result.content.length) emptyRows('inventory', 6, 'Chưa có tồn kho phù hợp. Nhập kho để tạo số tồn ban đầu.');
            $('#stock-available').textContent = integer(result.content.reduce((sum, row) => sum + row.available_quantity, 0));
            $('#stock-reserved').textContent = integer(result.content.reduce((sum, row) => sum + row.reserved_quantity, 0));
            $('#stock-physical').textContent = integer(result.content.reduce((sum, row) => sum + row.physical_quantity, 0));
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
        emptyRows('ledger', 5, 'Đang tải lịch sử kiểm toán…');
        try {
            const result = await api('/inventories/movements', {
                channel: 'ledger', query: { inventoryId: $('#ledger-inventory').value, page: state.pages.ledger, size: 10 }
            });
            $('#ledger-rows').innerHTML = result.content.map(movement =>
                '<tr><td><strong>' + escapeHtml(MOVEMENT_LABELS[movement.type] || movement.type)
                + '</strong><span class="meta-line mono">' + escapeHtml(movement.type)
                + '</span><span class="meta-line">Tồn kho #' + movement.inventory_id + ' · Biến động #' + movement.id
                + '</span></td><td class="align-right price">' + integer(movement.quantity)
                + '</td><td><div class="balance-change"><span>' + integer(movement.balance_before) + '</span>'
                + icon('arrow') + '<span class="after">' + integer(movement.balance_after) + '</span></div></td><td><strong>'
                + escapeHtml(movement.reference_type || 'NHẬP HÀNG THỦ CÔNG')
                + (movement.reference_id ? ' #' + movement.reference_id : '') + '</strong><span class="meta-line">Người thực hiện #'
                + movement.performed_by + '</span></td><td><span class="subtle">' + escapeHtml(dateTime(movement.created_at))
                + '</span><span class="meta-line ledger-note">' + escapeHtml(movement.note || '—') + '</span></td></tr>').join('');
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
            product_id: Number(data.product_id), warehouse_id: Number(data.warehouse_id),
            quantity: Number(data.quantity), note: data.note.trim() || null
        };
        const stock = await api('/inventories/stock-in', { method: 'POST', body });
        $('#inventory-warehouse').value = String(stock.warehouse_id);
        $('#inventory-product').value = String(stock.product_id);
        state.pages.inventory = 0;
        notify('success', 'Đã nhập ' + integer(body.quantity) + ' sản phẩm. Tồn khả dụng mới: '
            + integer(stock.available_quantity) + '.', 'HTTP 201 Created', '/api/v1/inventories/stock-in');
        const operations = [loadInventory()];
        if (hasRole('ADMIN', 'MANAGER')) operations.push(loadLedger());
        await Promise.all(operations);
    }

    /** Order-summary không có bộ lọc ngày; ghi rõ thống kê mọi thời gian để không nhầm với revenue. */
    async function loadSummary() {
        const result = await api('/reports/order-summary', { channel: 'summary' });
        const groups = new Map(result.map(row => [row.order_status, row]));
        const total = result.reduce((sum, row) => sum + row.total_count, 0);
        const confirmed = result.filter(row => ['CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED'].includes(row.order_status))
            .reduce((sum, row) => sum + row.total_count, 0);
        const closed = (groups.get('CANCELLED')?.total_count || 0) + (groups.get('EXPIRED')?.total_count || 0);
        $('#order-summary').innerHTML = [
            ['Tổng đơn hàng', total], ['Đã xác nhận / thực hiện', confirmed],
            ['Đang chờ thanh toán', groups.get('PENDING')?.total_count || 0], ['Đã hủy / hết hạn', closed]
        ].map(([label, value]) => '<div class="report-metric"><span>' + label + '</span><strong>'
            + integer(value) + '</strong><p>Tổng hợp mọi thời gian</p></div>').join('');
        $('#summary-statuses').innerHTML = Object.keys(STATUS_LABELS).map(status => {
            const row = groups.get(status);
            return '<div class="summary-chip">' + statusBadge(status) + '<strong>' + integer(row?.total_count || 0)
                + '</strong><span class="subtle">' + amount(row?.total_amount || 0) + '</span></div>';
        }).join('');
    }
    /** Kho/ngưỡng chỉ áp dụng cho báo cáo có tham số đó; Top Products tổng hợp toàn bộ kho. */
    function reportQuery() {
        return { fromDate: $('#report-from').value, toDate: $('#report-to').value };
    }
    /** Revenue nhóm theo DAY/MONTH và kho, dùng tiền snapshot backend và metadata phân trang thật. */
    async function loadRevenue() {
        emptyRows('revenue', 4, 'Đang tổng hợp doanh thu…');
        const result = await api('/reports/revenue', {
            channel: 'revenue', query: {
                ...reportQuery(), warehouseId: $('#report-warehouse').value, groupBy: $('#report-group').value,
                page: state.pages.revenue, size: 8
            }
        });
        $('#revenue-rows').innerHTML = result.content.map(row => {
            const period = $('#report-group').value === 'MONTH' ? row.period.slice(0, 7) : row.period;
            return '<tr><td class="mono">' + escapeHtml(period) + '</td><td>' + escapeHtml(row.warehouse_name)
                + '</td><td class="align-right">' + integer(row.total_orders)
                + '</td><td class="align-right price">' + amount(row.total_revenue) + '</td></tr>';
        }).join('');
        if (!result.content.length) emptyRows('revenue', 4, 'Chưa có doanh thu trong kỳ. Thanh toán một đơn để xem báo cáo.');
        $('#revenue-count').textContent = integer(result.total_elements) + ' nhóm kỳ / kho';
        renderPager('revenue', result);
    }
    /** Báo cáo sản phẩm bán chạy theo doanh thu giảm dần, không dùng giá catalog mới để tính lại. */
    async function loadTop() {
        emptyRows('top', 4, 'Đang tải sản phẩm bán chạy…');
        const result = await api('/reports/top-products', {
            channel: 'top', query: { ...reportQuery(), page: state.pages.top, size: 8 }
        });
        $('#top-rows').innerHTML = result.content.map(row =>
            '<tr><td>' + productCell(row) + '</td><td>' + escapeHtml(row.category_name)
            + '</td><td class="align-right">' + integer(row.total_quantity_sold)
            + '</td><td class="align-right price">' + amount(row.total_revenue) + '</td></tr>').join('');
        if (!result.content.length) emptyRows('top', 4, 'Chưa có sản phẩm bán ra trong khoảng ngày đã chọn.');
        $('#top-count').textContent = integer(result.total_elements) + ' sản phẩm';
        renderPager('top', result);
    }
    /** Low stock dựa vào available_quantity, hiển thị thêm reserved và physical để ra quyết định nhập hàng. */
    async function loadLow() {
        emptyRows('low', 5, 'Đang kiểm tra hàng sắp hết…');
        const result = await api('/reports/low-stock', {
            channel: 'low', query: {
                warehouseId: $('#report-warehouse').value, threshold: $('#report-threshold').value,
                page: state.pages.low, size: 8
            }
        });
        $('#low-rows').innerHTML = result.content.map(row =>
            '<tr><td>' + productCell(row) + '</td><td>' + escapeHtml(row.warehouse_name)
            + '</td><td class="align-right"><span class="low-number">' + integer(row.available_quantity)
            + '</span></td><td class="align-right">' + integer(row.reserved_quantity)
            + '</td><td class="align-right price">' + integer(row.physical_quantity) + '</td></tr>').join('');
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
        const failure = results.find(result => result.status === 'rejected' && result.reason.name !== 'AbortError');
        if (failure) throw failure.reason;
    }

    /** Form ADMIN tạo sản phẩm bằng DTO backend, giá dương và danh mục bắt buộc. */
    async function createProduct(form) {
        const data = Object.fromEntries(new FormData(form));
        const product = await api('/products', { method: 'POST', body: {
            sku: data.sku.trim(), name: data.name.trim(), category_id: Number(data.category_id),
            unit_price: Number(data.unit_price), status: data.status
        } });
        form.reset();
        state.pages.catalog = 0;
        await Promise.all([loadProductOptions(), loadCatalog()]);
        notify('success', 'Đã tạo sản phẩm ' + product.sku + '.', 'HTTP 201 Created', '/api/v1/products');
    }
    /** PATCH chỉ gửi trường đã nhập; không ghi đè tên/trạng thái khi người dùng chỉ đổi giá. */
    async function updateProduct(form) {
        const data = Object.fromEntries(new FormData(form));
        const body = {};
        if (data.name.trim()) body.name = data.name.trim();
        if (data.unit_price !== '') body.unit_price = Number(data.unit_price);
        if (data.status) body.status = data.status;
        if (!Object.keys(body).length) throw new Error('Nhập tên, giá hoặc trạng thái cần cập nhật.');
        const product = await api('/products/' + data.product_id, { method: 'PATCH', body });
        await Promise.all([loadProductOptions(), loadCatalog()]);
        notify('success', 'Đã cập nhật ' + product.sku + '. Giá hiện tại: ' + amount(product.unit_price),
            'HTTP 200 OK', '/api/v1/products/' + product.id);
    }

    /** Đổi khu vực chỉ tải API phù hợp với role, thẻ permission vẫn cho phép thử response 403 thật. */
    async function activateTab(tab) {
        if (!['catalog', 'orders', 'inventory', 'reports'].includes(tab)) return;
        state.tab = tab;
        $$('.tab-panel').forEach(panel => { panel.hidden = panel.id !== 'tab-' + tab; });
        $$('[data-tab]').forEach(button => {
            const active = button.dataset.tab === tab;
            button.classList.toggle('active', active);
            if (active) button.setAttribute('aria-current', 'page');
            else button.removeAttribute('aria-current');
        });
        window.history.replaceState(null, '', '#' + tab);
        renderPermissions();
        await refreshTab();
    }
    /** Chỉ tải lại khu vực đang xem; data stock/order/report luôn lấy lại sau khi chuyển tab. */
    async function refreshTab() {
        if (state.tab === 'catalog') await loadCatalog();
        else if (state.tab === 'orders') await loadOrders();
        else if (state.tab === 'inventory') {
            const results = await Promise.allSettled([loadInventory(), loadLedger()]);
            const failure = results.find(result => result.status === 'rejected' && result.reason.name !== 'AbortError');
            if (failure) throw failure.reason;
        } else await loadReports();
    }
    /** Metadata của pager giữ state riêng cho từng bảng, không làm nhảy trang của báo cáo khác. */
    async function changePage(name, direction) {
        if (!Object.hasOwn(state.pages, name)) return;
        state.pages[name] = Math.max(0, state.pages[name] + direction);
        const loaders = {
            catalog: loadCatalog, orders: loadOrders, inventory: loadInventory,
            ledger: loadLedger, revenue: loadRevenue, top: loadTop, low: loadLow
        };
        await loaders[name]();
    }
    /** Kiểm tra quyền dùng JWT hiện tại và endpoint thật, không giả lập lỗi 403 bằng JavaScript. */
    async function probe(path, query = {}) {
        await api(path, { query });
        notify('success', 'Tài khoản hiện tại được phép truy cập khu vực này.', 'HTTP 200 OK', '/api/v1' + path);
    }

    /** Sự kiện ủy quyền giúp các hàng phân trang và chi tiết được dựng lại mà không mất handler. */
    document.addEventListener('click', event => {
        const button = event.target.closest('button');
        if (!button || button.disabled) return;
        if (button.dataset.tab) { execute(() => activateTab(button.dataset.tab)); return; }
        if (button.dataset.demoRole) { execute(() => authenticate(DEMO_ACCOUNTS[button.dataset.demoRole])); return; }
        if (button.dataset.authMode) { setAuthMode(button.dataset.authMode); return; }
        if (button.dataset.page) {
            execute(() => changePage(button.dataset.page, Number(button.dataset.direction)));
            return;
        }
        const id = Number(button.dataset.id);
        const action = button.dataset.action;
        if (action === 'open-auth') { setAuthMode('login'); $('#auth-dialog').showModal(); }
        else if (action === 'close-auth') $('#auth-dialog').close();
        else if (action === 'dismiss-notice') $('#api-notice').hidden = true;
        else if (action === 'dismiss-toast') button.closest('.toast').remove();
        else if (action === 'logout') {
            clearSession(); renderIdentity(); renderPermissions();
            notify('success', 'Đã đăng xuất. Bạn có thể tiếp tục xem danh mục công khai.');
            execute(() => activateTab('catalog'));
        } else if (action === 'add-cart') execute(() => addCart(id));
        else if (action === 'remove-cart') { state.cart.delete(id); renderCart(); }
        else if (action === 'refresh') execute(() => busy(button, refreshTab));
        else if (action === 'view-order') execute(() => busy(button, () => lookupOrder(id)));
        else if (action === 'pay-order') execute(() => busy(button, () => mutateOrder(id, true)));
        else if (action === 'cancel-order') execute(() => busy(button, () => mutateOrder(id, false)));
        else if (action === 'probe-inventory') execute(() => busy(button, () => probe('/inventories')));
        else if (action === 'probe-ledger') execute(() => busy(button, () => probe('/inventories/movements')));
        else if (action === 'probe-reports') execute(() => busy(button, () => probe('/reports/order-summary')));
    });

    /** Số lượng giỏ chỉ nhận số nguyên dương; lỗi không làm mất quantity hợp lệ trước đó. */
    document.addEventListener('change', event => {
        const input = event.target;
        if (!input.dataset.cartQuantity) return;
        const item = state.cart.get(Number(input.dataset.cartQuantity));
        const quantity = Number(input.value);
        if (!item) return;
        if (!Number.isInteger(quantity) || quantity < 1 || quantity > MAX_QUANTITY) {
            input.value = item.quantity;
            notify('error', 'Số lượng phải là số nguyên dương và không vượt ' + integer(MAX_QUANTITY) + '.');
            return;
        }
        item.quantity = quantity;
        renderCart();
    });

    /** Submit form dùng validation HTML trước khi gọi API; server vẫn kiểm tra DTO đầy đủ. */
    document.addEventListener('submit', event => {
        event.preventDefault();
        const form = event.target;
        if (!form.reportValidity()) return;
        const button = $('button[type="submit"]', form);
        if (form.id === 'auth-form') {
            const body = { email: $('#auth-email').value.trim(), password: $('#auth-password').value };
            if (state.authMode === 'register') body.full_name = $('#auth-name').value.trim();
            execute(() => authenticate(body, state.authMode === 'register'));
            return;
        }
        if (form.id === 'catalog-filter') {
            state.pages.catalog = 0; execute(() => busy(button, loadCatalog));
        } else if (form.id === 'order-create') execute(() => busy(button, createOrder));
        else if (form.id === 'order-lookup') execute(() => busy(button, () => lookupOrder(Number($('#order-id').value))));
        else if (form.id === 'product-create') execute(() => busy(button, () => createProduct(form)));
        else if (form.id === 'product-update') execute(() => busy(button, () => updateProduct(form)));
        else if (form.id === 'inventory-filter') {
            state.pages.inventory = 0; execute(() => busy(button, loadInventory));
        } else if (form.id === 'stock-in') execute(() => busy(button, () => stockIn(form)));
        else if (form.id === 'ledger-filter') {
            state.pages.ledger = 0; execute(() => busy(button, loadLedger));
        } else if (form.id === 'report-filter') {
            if ($('#report-from').value && $('#report-to').value && $('#report-from').value > $('#report-to').value) {
                notify('error', 'Từ ngày phải trước hoặc bằng Đến ngày.'); return;
            }
            ['revenue', 'top', 'low'].forEach(name => { state.pages[name] = 0; });
            execute(() => busy(button, loadReports));
        }
    });

    /** Khôi phục JWT bằng users/me để role luôn khớp backend, không tin role do storage tự khai báo. */
    async function initialize() {
        renderIdentity();
        renderPermissions();
        renderCart();
        setAuthMode('login');
        try {
            const health = await fetch('/api/v1/health', { credentials: 'omit' });
            if (!health.ok) throw new Error('Health chưa sẵn sàng.');
            $('#connection-dot').className = 'connection-dot online';
            $('#connection-label').textContent = 'Hệ thống đang hoạt động';
        } catch {
            $('#connection-dot').className = 'connection-dot offline';
            $('#connection-label').textContent = 'Chưa kết nối được hệ thống';
        }
        let saved;
        try { saved = JSON.parse(sessionStorage.getItem(SESSION_KEY) || 'null'); }
        catch { /* Storage lỗi không ngăn xem catalog công khai. */ }
        if (saved?.token) {
            state.token = saved.token;
            try { state.user = await api('/users/me', { channel: 'identity' }); }
            catch (error) {
                if (error.name !== 'AbortError') handleError(error);
            }
        }
        renderIdentity();
        renderPermissions();
        await loadReferences();
        const tab = window.location.hash.slice(1);
        await activateTab(['catalog', 'orders', 'inventory', 'reports'].includes(tab) ? tab : 'catalog');
        window.setInterval(updateCountdown, 1000);
    }
    execute(initialize);
})();
