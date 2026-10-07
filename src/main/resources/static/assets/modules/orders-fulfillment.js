import { app as __stockflowApp } from './context.js';

function renderMyOrders(result) {
        __stockflowApp.state.myOrdersPage = result;
        __stockflowApp.$('#orders-rows').innerHTML = result.content
            .map(
                (order) =>
                    '<tr><td><strong class="mono">' +
                    __stockflowApp.escapeHtml(order.order_code) +
                    '</strong><span class="meta-line">' +
                    __stockflowApp.escapeHtml(__stockflowApp.warehouseName(order.warehouse_id)) +
                    ' · #' +
                    order.id +
                    '</span></td><td>' +
                    __stockflowApp.statusBadge(order.status) +
                    '<span class="meta-line mono">' +
                    __stockflowApp.escapeHtml(order.shipment?.tracking_code || 'Chưa có vận đơn') +
                    '</span>' +
                    (order.shipment ? '<span class="meta-line shipment-mode">' + __stockflowApp.escapeHtml(window.StockFlowShipment.describe(order.shipment)) + '</span>' : '') +
                    '</td>' +
                    '<td class="align-right price">' +
                    __stockflowApp.amount(order.total_amount) +
                    '</td><td>' +
                    '<button class="button secondary small" type="button" data-action="view-order" data-id="' +
                    order.id +
                    '">Xem</button></td></tr>',
            )
            .join('');
        if (!result.content.length)
            __stockflowApp.emptyRows('orders', 4, 'Bạn chưa có đơn hàng. Khám phá cửa hàng để đặt đơn đầu tiên.');
        __stockflowApp.$('#orders-count').textContent = __stockflowApp.integer(result.total_elements) + ' đơn';
        __stockflowApp.renderPager('orders', result);
    }

async function loadOrders() {
        if (!__stockflowApp.hasRole('CUSTOMER')) return;
        __stockflowApp.invalidateOrderRefresh();
        __stockflowApp.loadingRows('orders', 4, 'Đang tải đơn hàng của bạn…');
        __stockflowApp.$('#orders-pagination').replaceChildren();
        try {
            const result = await __stockflowApp.api('/orders/my', {
                channel: 'my-orders',
                query: { page: __stockflowApp.state.pages.orders, size: 8 },
            });
            __stockflowApp.renderMyOrders(result);
            if (!__stockflowApp.state.order && result.content.length) __stockflowApp.state.order = result.content[0];
            else if (__stockflowApp.state.order) {
                const updated = result.content.find((order) => order.id === __stockflowApp.state.order.id);
                if (updated) __stockflowApp.state.order = updated;
            }
            await __stockflowApp.hydrateOrderProducts();
            __stockflowApp.renderOrder();
            __stockflowApp.orderRefreshFailures = 0;
        } catch (error) {
            if (error.name !== 'AbortError') __stockflowApp.emptyRows('orders', 4, 'Chưa tải được đơn hàng. Vui lòng làm mới.');
            throw error;
        } finally {
            __stockflowApp.scheduleOrderRefresh();
        }
    }

function syncQueueStatusTabs() {
        __stockflowApp.$$('[data-queue-status]').forEach((button) =>
            button.setAttribute('aria-pressed', String(button.dataset.queueStatus === __stockflowApp.$('#queue-status').value)),
        );
    }

async function applyQueueStatus(status) {
        if (!__stockflowApp.isOperator() || ![...__stockflowApp.$('#queue-status').options].some((option) => option.value === status)) return;
        __stockflowApp.$('#queue-status').value = status;
        __stockflowApp.syncQueueStatusTabs();
        __stockflowApp.state.pages.queue = 0;
        __stockflowApp.state.order = null;
        __stockflowApp.renderOrder();
        await __stockflowApp.loadQueue();
    }

async function loadQueue() {
        if (!__stockflowApp.isOperator()) return;
        __stockflowApp.syncQueueStatusTabs();
        __stockflowApp.loadingRows('queue', 4, 'Đang tải đơn trong phạm vi được phép…');
        __stockflowApp.$('#queue-count').textContent = '—';
        __stockflowApp.$('#queue-pagination').replaceChildren();
        try {
            const result = await __stockflowApp.api('/orders', {
                channel: 'queue',
                query: {
                    page: __stockflowApp.state.pages.queue,
                    size: 8,
                    warehouseId: __stockflowApp.$('#queue-warehouse').value,
                    status: __stockflowApp.$('#queue-status').value,
                },
            });
            __stockflowApp.$('#queue-rows').innerHTML = result.content
                .map(
                    (order) =>
                        '<tr><td><strong class="mono">' +
                        __stockflowApp.escapeHtml(order.order_code) +
                        '</strong><span class="meta-line">#' +
                        order.id +
                        ' · Khách #' +
                        order.customer_id +
                        '</span></td><td><strong>' +
                        __stockflowApp.escapeHtml(order.warehouse_name) +
                        '</strong><span class="meta-line">' +
                        __stockflowApp.statusBadge(order.status) +
                        '</span></td><td class="align-right price">' +
                        __stockflowApp.amount(order.total_amount) +
                        '</td><td><div class="order-actions"><button class="button secondary small" type="button" data-action="view-order" data-id="' +
                        order.id +
                        '">Chi tiết</button>' +
                        __stockflowApp.fulfillmentButton(order) +
                        '</div></td></tr>',
                )
                .join('');
            if (!result.content.length) __stockflowApp.emptyRows('queue', 4, 'Chưa có đơn phù hợp trong phạm vi kho của bạn.');
            __stockflowApp.$('#queue-count').textContent = __stockflowApp.integer(result.total_elements) + ' đơn';
            __stockflowApp.renderPager('queue', result);
            if (!__stockflowApp.state.order && result.content.length) await __stockflowApp.lookupOrder(result.content[0].id);
        } catch (error) {
            if (error.name !== 'AbortError') __stockflowApp.emptyRows('queue', 4, 'Chưa tải được danh sách xử lý. Vui lòng làm mới.');
            throw error;
        }
    }

function fulfillmentButton(order) {
        if (!__stockflowApp.isOperator()) return '';
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

async function hydrateOrderProducts(order = __stockflowApp.state.order) {
        const ids = [...new Set((order?.items || []).map((item) => item.product_id))];
        await Promise.all(
            ids
                .filter((id) => !__stockflowApp.state.products.has(id))
                .map(async (id) => {
                    const product = await __stockflowApp.api('/products/' + id, { anonymous: true, channel: 'order-product-' + id });
                    __stockflowApp.state.products.set(product.id, product);
                }),
        );
    }

async function lookupOrder(id) {
        __stockflowApp.invalidateOrderRefresh();
        __stockflowApp.state.order = null;
        __stockflowApp.renderOrder();
        try {
            __stockflowApp.state.order = await __stockflowApp.api('/orders/' + id, { channel: 'order-detail' });
            __stockflowApp.$('#order-id').value = String(__stockflowApp.state.order.id);
            await __stockflowApp.hydrateOrderProducts();
            __stockflowApp.renderOrder();
        } finally {
            __stockflowApp.scheduleOrderRefresh();
        }
    }

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
                        __stockflowApp.escapeHtml(__stockflowApp.STATUS_LABELS[status]) +
                        '</span>',
                )
                .join('') +
            '</div>'
        );
    }

function renderDeliveryDetails(order) {
        const delivery = order.delivery;
        if (!delivery) {
            return '<p class="order-delivery-legacy">Đơn lịch sử chưa có thông tin nhận hàng.</p>';
        }
        return (
            '<section class="order-delivery" aria-label="Thông tin nhận hàng"><h4>Thông tin nhận hàng</h4>' +
            '<div class="delivery-contact"><strong>' +
            __stockflowApp.escapeHtml(delivery.recipient_name) +
            '</strong><span>' +
            __stockflowApp.escapeHtml(delivery.recipient_phone) +
            '</span></div><p class="delivery-address">' +
            __stockflowApp.escapeHtml(delivery.address) +
            '</p>' +
            (delivery.note
                ? '<p class="delivery-note"><span>Ghi chú: </span>' + __stockflowApp.escapeHtml(delivery.note) + '</p>'
                : '') +
            '<span class="delivery-shipping">Phí giao hàng GHN: ' + __stockflowApp.amount(order.shipping_fee || 0) + '</span></section>'
        );
    }

function renderOrder() {
        const order = __stockflowApp.state.order;
        const detail = __stockflowApp.state.view === 'portal' ? __stockflowApp.$('#queue-detail') : __stockflowApp.$('#order-detail');
        const other = __stockflowApp.state.view === 'portal' ? __stockflowApp.$('#order-detail') : __stockflowApp.$('#queue-detail');
        other.replaceChildren();
        if (!order) {
            detail.innerHTML =
                '<div class="empty-state">' +
                __stockflowApp.icon('order') +
                '<h3>Mỗi đơn hàng, một hành trình.</h3><p>Chọn một đơn để xem mặt hàng và tiến trình giao hàng.</p></div>';
            return;
        }
        const canCancel =
            (__stockflowApp.hasRole('CUSTOMER') && order.status === 'PENDING') ||
            (__stockflowApp.hasRole('ADMIN', 'MANAGER') && ['PENDING', 'CONFIRMED', 'PACKED'].includes(order.status));
        const items = order.items
            .map((item) => {
                const product = __stockflowApp.state.products.get(item.product_id);
                return (
                    '<div class="order-item"><div class="order-item-product">' +
                    __stockflowApp.cartThumbnail(product ? __stockflowApp.saleSku(product) : { name: 'Sản phẩm #' + item.product_id }) +
                    '<div class="order-item-description"><strong>' +
                    __stockflowApp.escapeHtml(product ? __stockflowApp.cartProductName(__stockflowApp.saleSku(product)) : 'Sản phẩm #' + item.product_id) +
                    '</strong><span class="meta-line">' +
                    __stockflowApp.integer(item.quantity) +
                    ' × ' +
                    __stockflowApp.amount(item.unit_price) +
                    '</span></div></div><strong class="price">' +
                    __stockflowApp.amount(item.line_total) +
                    '</strong>' +
                    (__stockflowApp.hasRole('CUSTOMER') && order.status === 'DELIVERED'
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
            __stockflowApp.escapeHtml(order.order_code) +
            '</h3></div>' +
            __stockflowApp.statusBadge(order.status) +
            '</div>' +
            __stockflowApp.orderTimeline(order) +
            '<div class="order-info"><div><span>Chi nhánh chuẩn bị</span><strong>' +
            __stockflowApp.escapeHtml(__stockflowApp.warehouseName(order.warehouse_id)) +
            '</strong></div><div><span>Đặt lúc</span>' +
            __stockflowApp.escapeHtml(__stockflowApp.dateTime(order.created_at)) +
            '</div><div><span>Thời hạn giữ hàng</span>' +
            __stockflowApp.escapeHtml(__stockflowApp.dateTime(order.reservation_expires_at)) +
            '</div><div><span>Cập nhật gần nhất</span>' +
            __stockflowApp.escapeHtml(__stockflowApp.dateTime(order.updated_at)) +
            '</div></div>' +
            __stockflowApp.renderDeliveryDetails(order) +
            '<p class="meta-line" data-order-payment></p>' +
            (order.status === 'PENDING'
                ? '<div class="countdown">' +
                  __stockflowApp.icon('clock') +
                  '<span data-expires="' +
                  __stockflowApp.escapeHtml(order.reservation_expires_at) +
                  '"></span></div>'
                : '') +
            (shipment
                ? '<div class="shipment-info">Vận đơn · ' +
                  __stockflowApp.escapeHtml(__stockflowApp.STATUS_LABELS[shipment.status] || 'Chuẩn bị') +
                  '<strong class="mono">' +
                  __stockflowApp.escapeHtml(shipment.tracking_code) +
                  '</strong>' +
                  '<p class="shipment-mode">' + __stockflowApp.escapeHtml(window.StockFlowShipment.describe(shipment)) + '</p>' +
                  (window.StockFlowShipment.trackingUrl(shipment, order.status)
                    ? '<a class="button secondary" target="_blank" rel="noopener noreferrer" href="' +
                      __stockflowApp.escapeHtml(window.StockFlowShipment.trackingUrl(shipment, order.status)) + '">Tra cứu hành trình GHN</a>' : '') +
                  '<span>Xuất giao: ' +
                  __stockflowApp.escapeHtml(__stockflowApp.dateTime(shipment.shipped_at)) +
                  '</span><br>' +
                  '<span>Giao thành công: ' +
                  __stockflowApp.escapeHtml(__stockflowApp.dateTime(shipment.delivered_at)) +
                  '</span></div>'
                : '<p class="meta-line">Mã vận đơn sẽ hiển thị khi chi nhánh chuẩn bị hàng.</p>') +
            '<div class="order-items">' +
            items +
            '</div><div class="order-total"><span>Tổng tiền</span><strong>' +
            __stockflowApp.amount(order.total_amount) +
            '</strong></div><div class="order-actions">' +
            (__stockflowApp.hasRole('CUSTOMER') && order.status === 'DELIVERED' ? '<button type="button" class="button secondary" data-action="request-return" data-id="' + order.id + '">Yêu cầu đổi / trả hàng</button>' : '') +
            (__stockflowApp.isOperator() && order.status === 'PACKED'
                ? '<label class="tracking-field">Mã vận đơn (tùy chọn)<input data-tracking-code maxlength="100" ' +
                  'pattern="[A-Za-z0-9._-]+" placeholder="Để trống để dùng mã tự sinh"></label>'
                : '') +
            __stockflowApp.fulfillmentButton(order) +
            (__stockflowApp.isOperator() && order.status === 'PACKED'
                ? '<button type="button" class="button primary" data-action="operate-order" data-operation="ghn-ship" data-id="' +
                  order.id + '">🚚 Bắn đơn sang GHN (Tự động lấy mã vận đơn)</button>' : '') +
            (__stockflowApp.hasRole('CUSTOMER') && order.status === 'PENDING'
                ? '<button type="button" class="button primary" data-action="cod-order" data-id="' +
                  order.id +
                  '">' +
                  __stockflowApp.icon('card') +
                  'Thanh toán khi nhận hàng (COD)</button>'
                : '') +
            (__stockflowApp.hasRole('CUSTOMER') && order.status === 'PENDING'
                ? '<button type="button" class="button secondary" data-action="vnpay-order" data-id="' +
                  order.id + '"><img class="payment-method-logo" src="/assets/vnpay-logo.svg" alt="VNPay" width="84" height="26">Thanh toán qua VNPay</button>'
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
            __stockflowApp.icon('refresh') +
            'Tải lại</button></div>' +
            (__stockflowApp.hasRole('CUSTOMER')
                ? '<p class="meta-line" data-order-sync>Tự cập nhật trạng thái khi bạn đang xem đơn.</p>'
                : '');
        __stockflowApp.updateCountdown();
        const paymentLabel = detail.querySelector('[data-order-payment]');
        __stockflowApp.api('/orders/' + order.id + '/payment').then((payment) => {
            if (!paymentLabel?.isConnected || __stockflowApp.state.order?.id !== order.id || !payment) return;
            const method = payment.method === 'COD' ? 'Thanh toán khi nhận hàng (COD)'
                : payment.method === 'VNPAY' ? 'VNPay' : 'Thanh toán mô phỏng';
            const status = payment.status === 'PENDING' ? 'Chưa thu tiền — thanh toán khi nhận hàng'
                : payment.status === 'PAID' ? 'Đã thanh toán'
                : payment.status === 'REFUNDED' ? 'Đã hoàn tiền' : 'Đã hủy';
            paymentLabel.textContent = method + ' · ' + status;
        }).catch(() => {});
    }

function updateCountdown() {
        __stockflowApp.$$('[data-expires]').forEach((element) => {
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
                __stockflowApp.canAutoRefreshOrders() &&
                !__stockflowApp.orderRefreshFailures &&
                Date.now() - __stockflowApp.lastOrderRefreshAttempt >= __stockflowApp.EXPIRED_ORDER_REFRESH_MS
            ) {
                __stockflowApp.scheduleOrderRefresh(0);
            }
        });
    }

function confirmOrderAction(id, action) {
        __stockflowApp.state.pendingMutation = { id, action, epoch: __stockflowApp.state.epoch };
        // Dialog dùng chung với catalog; đặt lại nhãn để không giữ chữ Xóa danh mục từ lần trước.
        __stockflowApp.$('#confirm-submit').textContent = 'Xác nhận';
        __stockflowApp.$('#confirm-submit').className = 'button danger';
        __stockflowApp.$('#confirm-title').textContent = action === 'return' ? 'Nhận trả toàn bộ đơn #' + id : 'Hủy đơn #' + id;
        __stockflowApp.$('#confirm-message').textContent =
            action === 'return'
                ? 'Xác nhận đã nhận lại toàn bộ hàng của đơn. Hệ thống sẽ hoàn kho và hoàn tiền mô phỏng. Chỉ thực hiện sau khi kiểm tra hàng.'
                : 'Đơn sẽ được hủy. Hệ thống giải phóng hàng đang giữ hoặc hoàn kho. Chỉ hoàn tiền nếu đơn đã thanh toán.';
        __stockflowApp.openDialog('confirm-dialog');
    }

async function payWithVNPay(id) {
        const result = await __stockflowApp.api('/payments/vnpay/create', { method: 'POST', body: { order_id: id } });
        const paymentUrl = new URL(result.payment_url);
        if (paymentUrl.protocol !== 'https:' || paymentUrl.hostname !== 'sandbox.vnpayment.vn') {
            throw new Error('Đường dẫn VNPay Sandbox không hợp lệ.');
        }
        window.location.assign(paymentUrl.href);
    }

async function mutateOrder(id, action) {
        __stockflowApp.invalidateOrderRefresh();
        const segment = action === 'pay' ? 'payment-simulations/confirm' : action === 'cod' ? 'cod/confirm' : action;
        const path = '/orders/' + id + '/' + segment;
        let body;
        if (action === 'ship') {
            const input = __stockflowApp.state.order?.id === id ? __stockflowApp.$('[data-tracking-code]') : null;
            if (input && !input.reportValidity()) return;
            body = input?.value.trim() ? { tracking_code: input.value.trim() } : {};
        }
        const order = await __stockflowApp.api(path, { method: 'POST', body });
        __stockflowApp.state.order = order;
        await __stockflowApp.hydrateOrderProducts();
        __stockflowApp.renderOrder();
        const messages = {
            pay: 'Thanh toán mô phỏng đã được xác nhận.',
            cod: 'Đã xác nhận đơn COD. Bạn thanh toán khi nhận hàng.',
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
        if (action === 'ghn-ship') messages[action] += ' ' + window.StockFlowShipment.describe(order.shipment);
        __stockflowApp.notify(
            'success',
            order.status === 'EXPIRED' ? 'Đơn đã hết hạn; hàng được giải phóng và không thu tiền.' : messages[action],
            'HTTP 200 OK',
        );
        if (__stockflowApp.hasRole('CUSTOMER')) await __stockflowApp.loadOrders();
        else if (__stockflowApp.isOperator()) {
            if (action === 'ghn-ship') {
                __stockflowApp.$('#queue-status').value = 'SHIPPED';
                __stockflowApp.state.pages.queue = 0;
                __stockflowApp.syncQueueStatusTabs();
            }
            await __stockflowApp.loadQueue();
        }
    }

export function register() {
Object.defineProperties(__stockflowApp, {
"renderMyOrders": { get: () => renderMyOrders },
"loadOrders": { get: () => loadOrders },
"syncQueueStatusTabs": { get: () => syncQueueStatusTabs },
"applyQueueStatus": { get: () => applyQueueStatus },
"loadQueue": { get: () => loadQueue },
"fulfillmentButton": { get: () => fulfillmentButton },
"hydrateOrderProducts": { get: () => hydrateOrderProducts },
"lookupOrder": { get: () => lookupOrder },
"orderTimeline": { get: () => orderTimeline },
"renderDeliveryDetails": { get: () => renderDeliveryDetails },
"renderOrder": { get: () => renderOrder },
"updateCountdown": { get: () => updateCountdown },
"confirmOrderAction": { get: () => confirmOrderAction },
"payWithVNPay": { get: () => payWithVNPay },
"mutateOrder": { get: () => mutateOrder }
});
}

export function initializeFeature() {

}
