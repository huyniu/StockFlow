import { app as __stockflowApp } from './context.js';

let checkoutShippingQuote, shippingLocationVersion, shippingFeeVersion;

function renderCart() {
        let total = 0;
        let quantity = 0;
        __stockflowApp.$('#cart-items').innerHTML = [...__stockflowApp.state.cart.values()]
            .map((item) => {
                const product = __stockflowApp.saleSku(__stockflowApp.state.products.get(item.product.id) || item.product);
                if (item.selected !== false) total += Number(product.unit_price) * item.quantity;
                quantity += item.quantity;
                return (
                    '<div class="cart-line' + (item.selected === false ? ' cart-line-unselected' : '') + '">'
                    + '<input class="cart-line-select" type="checkbox" data-cart-selected="' + product.id + '" '
                    + (item.selected !== false ? 'checked ' : '') + 'aria-label="Chọn thanh toán ' + __stockflowApp.escapeHtml(__stockflowApp.cartProductName(product)) + '" />'
                    + __stockflowApp.cartThumbnail(product) + '<div class="cart-line-head"><div><strong>' +
                    __stockflowApp.escapeHtml(__stockflowApp.cartProductName(product)) +
                    '</strong><span class="meta-line mono">' +
                    __stockflowApp.escapeHtml(product.sku) +
                    '</span></div>' +
                    '<button class="icon-button" type="button" data-action="remove-cart" data-id="' +
                    product.id +
                    '" aria-label="Xóa ' +
                    __stockflowApp.escapeHtml(product.name) +
                    '">' +
                    __stockflowApp.icon('close') +
                    '</button></div>' +
                    '<div class="cart-line-foot"><div class="cart-quantity">' +
                    '<button class="icon-button" type="button" data-action="cart-minus" data-id="' +
                    product.id +
                    '" aria-label="Giảm số lượng" ' +
                    (item.quantity === 1 ? 'disabled' : '') +
                    '>' +
                    __stockflowApp.icon('minus') +
                    '</button>' +
                    '<input type="number" min="1" max="' +
                    __stockflowApp.MAX_QUANTITY +
                    '" step="1" value="' +
                    item.quantity +
                    '" data-cart-quantity="' +
                    product.id +
                    '" aria-label="Số lượng ' +
                    __stockflowApp.escapeHtml(product.name) +
                    '">' +
                    '<button class="icon-button" type="button" data-action="cart-plus" data-id="' +
                    product.id +
                    '" aria-label="Tăng số lượng" ' +
                    (item.quantity >= __stockflowApp.MAX_QUANTITY ? 'disabled' : '') +
                    '>' +
                    __stockflowApp.icon('plus') +
                    '</button>' +
                    '</div><strong class="price">' +
                    __stockflowApp.amount(Number(product.unit_price) * item.quantity) +
                    '</strong></div></div>'
                );
            })
            .join('');
        if (!__stockflowApp.state.cart.size) {
            const waiting = __stockflowApp.state.cartLoading || __stockflowApp.state.cartRestoreFailed;
            __stockflowApp.$('#cart-items').innerHTML =
                '<div class="empty-state">' +
                __stockflowApp.icon('cart') +
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
                      __stockflowApp.icon('arrow') +
                      '</button>') +
                '</div>';
        }
        if (__stockflowApp.state.cartRestoreFailed) {
            __stockflowApp.$('#cart-items').insertAdjacentHTML(
                'beforeend',
                `<div class="notice error" role="status">
                    ${__stockflowApp.icon('alert')}
                    <div class="notice-content">
                        <strong>Chưa khôi phục được giỏ hàng</strong>
                        <div>Bản lưu vẫn được giữ. Kiểm tra kết nối và thử lại để cập nhật giá trước khi đặt hàng.</div>
                        <button class="button secondary small" type="button" data-action="retry-cart">Thử lại</button>
                    </div>
                </div>`,
            );
        }
        __stockflowApp.$('#cart-total').textContent = __stockflowApp.amount(total);
        __stockflowApp.renderShippingTotal();
        __stockflowApp.$('#cart-badge').textContent = __stockflowApp.integer(quantity);
        __stockflowApp.$('#cart-count').textContent = __stockflowApp.integer(__stockflowApp.state.cart.size);
        const selectedCount = __stockflowApp.selectedCartItems().length;
        const all = __stockflowApp.$('#cart-select-all');
        all.checked = __stockflowApp.state.cart.size > 0 && selectedCount === __stockflowApp.state.cart.size;
        all.indeterminate = selectedCount > 0 && selectedCount < __stockflowApp.state.cart.size;
        __stockflowApp.$('#cart-selected-count').textContent = 'Đã chọn ' + selectedCount + '/' + __stockflowApp.state.cart.size + ' sản phẩm';
        __stockflowApp.renderPermissions();
    }

function cartThumbnail(product) {
        const source = __stockflowApp.safeProductImageUrl(product.image_url)
            || (Array.isArray(product.image_urls) ? product.image_urls.map(__stockflowApp.safeProductImageUrl).find(Boolean) : null);
        return '<div class="cart-thumbnail">'
            + (source ? '<img data-cart-thumbnail src="' + __stockflowApp.escapeHtml(source) + '" alt="' + __stockflowApp.escapeHtml(__stockflowApp.cartProductName(product))
                + '" width="72" height="72" loading="lazy" decoding="async" />' : '')
            + '<span class="cart-thumbnail-empty" ' + (source ? 'hidden' : '') + '>Chưa có ảnh</span></div>';
    }

function addCart(id, quantity = 1) {
        if (__stockflowApp.isOperator()) return;
        if (__stockflowApp.$('#create-order').getAttribute('aria-busy') === 'true') throw new Error('Vui lòng chờ đặt hàng hoàn tất trước khi sửa giỏ.');
        if (__stockflowApp.state.cartLoading || __stockflowApp.state.cartRestoreFailed || __stockflowApp.state.authBusy) {
            throw new Error('Vui lòng khôi phục giỏ và chờ phiên đăng nhập cập nhật xong trước khi thêm sản phẩm.');
        }
        const product = __stockflowApp.saleSku(__stockflowApp.state.products.get(id));
        if (!product || product.status !== 'ACTIVE') return;
        const item = __stockflowApp.state.cart.get(id);
        if (!item && __stockflowApp.state.cart.size >= __stockflowApp.MAX_CART_ITEMS) throw new Error('Một đơn tối đa 100 mặt hàng.');
        if (!Number.isInteger(quantity) || quantity < 1) throw new Error('Số lượng phải là số nguyên dương.');
        const totalQuantity = (item ? item.quantity : 0) + quantity;
        if (totalQuantity > __stockflowApp.MAX_QUANTITY) throw new Error('Số lượng đã đạt giới hạn.');
        __stockflowApp.state.cart.set(id, { product, quantity: totalQuantity, selected: true });
        __stockflowApp.saveCart();
        __stockflowApp.renderCart();
        __stockflowApp.bounceCart();
        __stockflowApp.notify('success', 'Đã thêm ' + product.name + ' vào giỏ hàng.');
    }

function setCartQuantity(id, quantity) {
        if (__stockflowApp.$('#create-order').getAttribute('aria-busy') === 'true') return;
        if (__stockflowApp.state.cartLoading || __stockflowApp.state.cartRestoreFailed) return;
        const item = __stockflowApp.state.cart.get(id);
        if (!item) return;
        if (!Number.isInteger(quantity) || quantity < 1 || quantity > __stockflowApp.MAX_QUANTITY) {
            __stockflowApp.renderCart();
            throw new Error('Số lượng phải là số nguyên từ 1 đến ' + __stockflowApp.integer(__stockflowApp.MAX_QUANTITY) + '.');
        }
        item.quantity = quantity;
        __stockflowApp.saveCart();
        __stockflowApp.renderCart();
    }

function resetLocationSelect(id, placeholder) {
        const select = __stockflowApp.$('#' + id);
        select.innerHTML = '<option value="">' + placeholder + '</option>';
        select.disabled = true;
    }

function renderShippingTotal() {
        const selected = __stockflowApp.selectedCartItems();
        const subtotal = selected.reduce((sum, item) => sum + Number(__stockflowApp.saleSku(__stockflowApp.state.products.get(item.product.id) || item.product).unit_price) * item.quantity, 0);
        __stockflowApp.$('#checkoutShippingFee').textContent = !selected.length ? __stockflowApp.amount(0) : __stockflowApp.checkoutShippingQuote ? __stockflowApp.amount(__stockflowApp.checkoutShippingQuote.fee)
            + (__stockflowApp.checkoutShippingQuote.testMode ? ' (phí thử nghiệm)' : '') : 'Chọn địa chỉ / chờ tính phí';
        __stockflowApp.$('#checkoutTotal').textContent = __stockflowApp.amount(subtotal + (selected.length ? __stockflowApp.checkoutShippingQuote?.fee || 0 : 0));
    }

async function loadCheckoutProvinces() {
        const mode = await __stockflowApp.api('/locations/mode');
        __stockflowApp.$('#checkoutShippingNotice').textContent = mode.test_mode
            ? 'Đang thử nghiệm GHN: địa chỉ có thể là dữ liệu mẫu, phí chưa dùng cho giao hàng thật.'
            : 'Chọn địa chỉ để lấy cước vận chuyển từ GHN.';
        const select = __stockflowApp.$('#checkoutProvince');
        if (select.options.length > 1) return;
        const values = await __stockflowApp.api('/locations/provinces');
        select.innerHTML = '<option value="">Chọn tỉnh / thành phố</option>' + values.map(p =>
            '<option value="' + p.ProvinceID + '">' + __stockflowApp.escapeHtml(p.ProvinceName) + '</option>').join('');
    }

async function changeCheckoutLocation(level) {
        const version = ++__stockflowApp.shippingLocationVersion;
        ++__stockflowApp.shippingFeeVersion;
        __stockflowApp.checkoutShippingQuote = null;
        __stockflowApp.renderShippingTotal();
        if (level === 'province') {
            __stockflowApp.resetLocationSelect('checkoutDistrict', 'Chọn quận / huyện');
            __stockflowApp.resetLocationSelect('checkoutWard', 'Chọn phường / xã');
            const id = __stockflowApp.$('#checkoutProvince').value;
            if (!id) return;
            const values = await __stockflowApp.api('/locations/districts', { query: { province_id: id } });
            if (version !== __stockflowApp.shippingLocationVersion || id !== __stockflowApp.$('#checkoutProvince').value) return;
            const select = __stockflowApp.$('#checkoutDistrict');
            select.innerHTML += values.map(d => '<option value="' + d.DistrictID + '">' + __stockflowApp.escapeHtml(d.DistrictName) + '</option>').join('');
            select.disabled = false;
        } else {
            __stockflowApp.resetLocationSelect('checkoutWard', 'Chọn phường / xã');
            const id = __stockflowApp.$('#checkoutDistrict').value;
            if (!id) return;
            const values = await __stockflowApp.api('/locations/wards', { query: { district_id: id } });
            if (version !== __stockflowApp.shippingLocationVersion || id !== __stockflowApp.$('#checkoutDistrict').value) return;
            const select = __stockflowApp.$('#checkoutWard');
            select.innerHTML += values.map(w => '<option value="' + __stockflowApp.escapeHtml(w.WardCode) + '">' + __stockflowApp.escapeHtml(w.WardName) + '</option>').join('');
            select.disabled = false;
        }
    }

async function refreshCheckoutFee() {
        const version = ++__stockflowApp.shippingFeeVersion;
        __stockflowApp.checkoutShippingQuote = null;
        __stockflowApp.renderShippingTotal();
        const warehouse = String(__stockflowApp.state.branchId || '');
        const district = __stockflowApp.$('#checkoutDistrict').value;
        const ward = __stockflowApp.$('#checkoutWard').value;
        if (!warehouse || !district || !ward) return;
        const result = await __stockflowApp.api('/locations/calculate-fee', { method: 'POST', body: {
            warehouse_id: Number(warehouse), to_district_id: Number(district), to_ward_code: ward, weight: 500,
        } });
        if (version !== __stockflowApp.shippingFeeVersion || warehouse !== String(__stockflowApp.state.branchId) || district !== __stockflowApp.$('#checkoutDistrict').value
            || ward !== __stockflowApp.$('#checkoutWard').value) return;
        const fee = Number(result.shipping_fee);
        if (!Number.isFinite(fee) || fee < 0) throw new Error('Phí vận chuyển không hợp lệ.');
        __stockflowApp.checkoutShippingQuote = { warehouse, district, ward, fee, testMode: result.test_mode === true };
        __stockflowApp.renderShippingTotal();
    }

function resetCheckoutDetails() {
        __stockflowApp.checkoutDifferentAddress = false;
        __stockflowApp.shippingLocationVersion++;
        __stockflowApp.shippingFeeVersion++;
        __stockflowApp.checkoutShippingQuote = null;
        __stockflowApp.$('#checkoutProvince').value = '';
        __stockflowApp.resetLocationSelect('checkoutDistrict', 'Chọn quận / huyện');
        __stockflowApp.resetLocationSelect('checkoutWard', 'Chọn phường / xã');
        __stockflowApp.renderShippingTotal();
        __stockflowApp.$$('[data-delivery-field]').forEach((field) => {
            field.value = '';
        });
    }

function forgetCheckoutAttempt() {
        __stockflowApp.checkoutAttempt = null;
        try {
            sessionStorage.removeItem(__stockflowApp.CHECKOUT_ATTEMPT_KEY);
        } catch {
            /* Trình duyệt chặn storage vẫn xóa khóa trong bộ nhớ. */
        }
    }

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
        if (epoch !== __stockflowApp.state.epoch || owner !== __stockflowApp.cartOwner()) {
            throw new DOMException('Tài khoản đặt hàng đã thay đổi.', 'AbortError');
        }
        if (!__stockflowApp.checkoutAttempt && persistent) {
            try {
                const saved = JSON.parse(sessionStorage.getItem(__stockflowApp.CHECKOUT_ATTEMPT_KEY));
                if (
                    saved?.owner === owner &&
                    /^[a-f0-9]{64}$/.test(saved.hash) &&
                    /^SF-WEB-[a-f0-9]{32}$/.test(saved.key)
                )
                    __stockflowApp.checkoutAttempt = saved;
            } catch {
                /* Metadata hỏng hoặc storage bị chặn thì tạo khóa ngẫu nhiên mới. */
            }
        }
        if (__stockflowApp.checkoutAttempt?.owner !== owner || __stockflowApp.checkoutAttempt.hash !== hash) {
            const random = crypto.getRandomValues(new Uint8Array(16));
            const key = 'SF-WEB-' + [...random].map((byte) => byte.toString(16).padStart(2, '0')).join('');
            __stockflowApp.checkoutAttempt = { owner, key, hash };
        }
        if (persistent) {
            try {
                sessionStorage.setItem(__stockflowApp.CHECKOUT_ATTEMPT_KEY, JSON.stringify(__stockflowApp.checkoutAttempt));
            } catch {
                /* HTTPS/localhost có SHA-256; nếu storage bị chặn thì vẫn bảo vệ retry trong phiên hiện tại. */
            }
        }
        return __stockflowApp.checkoutAttempt.key;
    }

async function createOrder() {
        __stockflowApp.renderPermissions();
        if (!__stockflowApp.state.user) {
            __stockflowApp.openAuth('checkout');
            return;
        }
        if (!__stockflowApp.hasRole('CUSTOMER')) throw new Error('Dùng tài khoản khách hàng để đặt đơn.');
        if (__stockflowApp.state.cartLoading || __stockflowApp.state.cartRestoreFailed)
            throw new Error('Khôi phục giỏ và cập nhật giá trước khi đặt hàng.');
        if (!__stockflowApp.state.cart.size) throw new Error('Thêm ít nhất một sản phẩm vào giỏ.');
        const selected = __stockflowApp.selectedCartItems();
        if (!selected.length) throw new Error('Chọn ít nhất một sản phẩm muốn thanh toán.');
        if (!__stockflowApp.state.branchId) throw new Error('Chọn chi nhánh chuẩn bị đơn.');
        const district = __stockflowApp.$('#checkoutDistrict').value;
        const ward = __stockflowApp.$('#checkoutWard').value;
        if (!__stockflowApp.checkoutShippingQuote || __stockflowApp.checkoutShippingQuote.warehouse !== String(__stockflowApp.state.branchId)
            || __stockflowApp.checkoutShippingQuote.district !== district || __stockflowApp.checkoutShippingQuote.ward !== ward)
            throw new Error('Vui lòng chọn địa chỉ và chờ tính phí vận chuyển.');
        const streetAddress = __stockflowApp.$('#checkoutStreetAddress').value.trim();
        if (!streetAddress) throw new Error('Vui lòng nhập số nhà, tên đường.');
        const fullAddress = [streetAddress,
            __stockflowApp.$('#checkoutWard').selectedOptions[0].textContent,
            __stockflowApp.$('#checkoutDistrict').selectedOptions[0].textContent,
            __stockflowApp.$('#checkoutProvince').selectedOptions[0].textContent].join(', ');
        const body = {
            warehouse_id: Number(__stockflowApp.state.branchId),
            to_district_id: Number(district),
            to_ward_code: ward,
            shipping_fee: __stockflowApp.checkoutShippingQuote.fee,
            items: selected.map((item) => ({
                product_id: item.product.id,
                quantity: item.quantity,
            })),
            delivery: {
                recipient_name: __stockflowApp.$('#delivery-name').value.trim(),
                recipient_phone: __stockflowApp.$('#delivery-phone').value.trim(),
                address: fullAddress,
                note: __stockflowApp.$('#delivery-note').value.trim() || null,
            },
        };
        const owner = __stockflowApp.cartOwner();
        const epoch = __stockflowApp.state.epoch;
        const idempotencyKey = await __stockflowApp.checkoutKey(body, owner, epoch);
        if (epoch !== __stockflowApp.state.epoch || owner !== __stockflowApp.cartOwner()) {
            throw new DOMException('Tài khoản đặt hàng đã thay đổi.', 'AbortError');
        }
        let result;
        try {
            result = await __stockflowApp.api('/orders', {
                method: 'POST',
                body,
                idempotencyKey,
            });
        } catch (error) {
            // Mất phản hồi không có nghĩa đặt thất bại; hướng dẫn thử lại cùng khóa thay vì tạo giỏ khác.
            if (error instanceof __stockflowApp.ApiError && error.status === 0) {
                error.message =
                    'Không kết nối được hệ thống; chưa rõ kết quả đặt hàng. ' +
                    'Hãy thử lại với cùng giỏ và thông tin, hoặc kiểm tra Đơn hàng của tôi trước khi đổi nội dung.';
            }
            throw error;
        }
        if (!Number.isSafeInteger(result?.id) || !result.order_code || !result.status) {
            throw new Error('Chưa nhận được thông tin đơn hợp lệ. Thử lại để tra lại cùng lần đặt hàng.');
        }
        __stockflowApp.forgetCheckoutAttempt();
        __stockflowApp.consumePurchasedCartItems(body.items);
        __stockflowApp.saveCart();
        __stockflowApp.resetCheckoutDetails();
        __stockflowApp.state.order = result;
        __stockflowApp.state.pages.orders = 0;
        __stockflowApp.$('#cart-dialog').close();
        __stockflowApp.renderCart();
        __stockflowApp.renderOrder();
        __stockflowApp.notify(
            'success',
            'Đơn ' +
                result.order_code +
                (result.status === 'PENDING'
                    ? ' đang chờ thanh toán. Kiểm tra thời hạn giữ hàng trong chi tiết đơn.'
                    : ' đã được ghi nhận: ' + (__stockflowApp.STATUS_LABELS[result.status] || result.status) + '.'),
            'HTTP 201 Created',
        );
        await __stockflowApp.activateView('shop', 'orders');
    }

export function register() {
Object.defineProperties(__stockflowApp, {
"renderCart": { get: () => renderCart },
"cartThumbnail": { get: () => cartThumbnail },
"addCart": { get: () => addCart },
"setCartQuantity": { get: () => setCartQuantity },
"checkoutShippingQuote": { get: () => checkoutShippingQuote, set: value => { checkoutShippingQuote = value; } },
"shippingLocationVersion": { get: () => shippingLocationVersion, set: value => { shippingLocationVersion = value; } },
"shippingFeeVersion": { get: () => shippingFeeVersion, set: value => { shippingFeeVersion = value; } },
"resetLocationSelect": { get: () => resetLocationSelect },
"renderShippingTotal": { get: () => renderShippingTotal },
"loadCheckoutProvinces": { get: () => loadCheckoutProvinces },
"changeCheckoutLocation": { get: () => changeCheckoutLocation },
"refreshCheckoutFee": { get: () => refreshCheckoutFee },
"resetCheckoutDetails": { get: () => resetCheckoutDetails },
"forgetCheckoutAttempt": { get: () => forgetCheckoutAttempt },
"checkoutKey": { get: () => checkoutKey },
"createOrder": { get: () => createOrder }
});
}

export function initializeFeature() {
(checkoutShippingQuote = null);
(shippingLocationVersion = 0);
(shippingFeeVersion = 0);
document.addEventListener('change', event => {
        if (event.target.id === 'profileProvince') __stockflowApp.execute(() => __stockflowApp.changeProfileLocation('province'));
        else if (event.target.id === 'profileDistrict') __stockflowApp.execute(() => __stockflowApp.changeProfileLocation('district'));
        if (event.target.id === 'checkoutProvince') __stockflowApp.execute(() => __stockflowApp.changeCheckoutLocation('province'));
        else if (event.target.id === 'checkoutDistrict') __stockflowApp.execute(() => __stockflowApp.changeCheckoutLocation('district'));
        else if (event.target.id === 'checkoutWard') __stockflowApp.execute(__stockflowApp.refreshCheckoutFee);
    });
}
