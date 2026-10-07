import { app } from './context.js';

let orderId, createdRequestId, reviewId, generation = 0;
const imageUrls = new Set();
const $ = selector => document.querySelector(selector);
const labels = { PENDING: 'Chờ duyệt', APPROVED: 'Đã chấp thuận', REJECTED: 'Đã từ chối', RECEIVED: 'Đã nhận lại hàng' };
const toast = message => app.notify('success', message, '', '', { banner: false });
function releaseImages() { ++generation; imageUrls.forEach(URL.revokeObjectURL); imageUrls.clear(); }
async function privateFetch(path, options = {}) {
    const epoch = app.state.epoch;
    const response = await fetch('/api/v1/returns' + path, { ...options, credentials: 'omit', headers: { Authorization: 'Bearer ' + app.state.token }, signal: AbortSignal.timeout(20000) });
    if (epoch !== app.state.epoch) throw new DOMException('Phiên đã thay đổi', 'AbortError');
    if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        throw new Error(body.message || 'Chưa tải được ảnh. Vui lòng thử lại.');
    }
    return response;
}
async function uploadImages(id, files) {
    if (files.length > 5 || files.some(file => file.size > 2 * 1024 * 1024 || !['image/png', 'image/jpeg'].includes(file.type))) throw new Error('Tối đa 5 ảnh PNG/JPEG, mỗi ảnh tối đa 2 MB.');
    for (const file of files) {
        const body = new FormData(); body.append('file', file);
        await privateFetch('/' + id + '/images', { method: 'POST', body });
    }
}
export async function loadReturns() {
    releaseImages(); const current = generation;
    $('#returns-list').textContent = 'Đang tải yêu cầu…';
    try {
        const rows = await app.api('/returns');
        if (current !== generation) return;
        const manager = app.hasRole('ADMIN', 'MANAGER');
        $('#returns-list').innerHTML = rows.length ? rows.map(row => '<article class="return-card"><div class="return-card-heading"><strong>' + app.escapeHtml(row.order_code || 'Đơn #' + row.order_id) + '</strong><span class="address-default">' + labels[row.status] + '</span></div>' +
            '<p><strong>' + (row.kind === 'EXCHANGE' ? 'Đổi sản phẩm' : 'Trả hàng') + '</strong> · ' + app.escapeHtml(app.dateTime(row.created_at)) + '</p><p class="return-reason">' + app.escapeHtml(row.reason) + '</p>' +
            (row.resolution_note ? '<p class="return-reason"><strong>Phản hồi từ cửa hàng:</strong> ' + app.escapeHtml(row.resolution_note) + '</p>' : '') +
            '<div class="return-images">' + row.images.map(image => '<img alt="Ảnh minh chứng" data-return-image="' + image.id + '" data-request-id="' + row.id + '" hidden>').join('') + '</div>' +
            '<div class="address-actions">' + (manager && row.status === 'PENDING' ? '<button class="button primary small" data-action="review-return" data-id="' + row.id + '">Xem xét yêu cầu</button>' : '') +
            (manager && row.status === 'APPROVED' ? '<button class="button primary small" data-action="receive-return" data-id="' + row.id + '">Xác nhận đã nhận lại hàng</button>' : '') +
            (!manager && row.status === 'PENDING' && row.images.length < 5 ? '<label class="button secondary small">Bổ sung ảnh<input type="file" accept="image/png,image/jpeg" data-return-upload="' + row.id + '" hidden></label>' : '') + '</div></article>').join('') : '<p class="meta-line">Chưa có yêu cầu đổi / trả hàng.</p>';
        const results = await Promise.allSettled([...document.querySelectorAll('[data-return-image]')].map(async image => {
            const response = await privateFetch('/' + image.dataset.requestId + '/images/' + image.dataset.returnImage);
            const blob = await response.blob(); if (current !== generation || !image.isConnected) return;
            const url = URL.createObjectURL(blob); imageUrls.add(url); image.src = url; image.hidden = false;
        }));
        if (current === generation && results.some(result => result.status === 'rejected')) $('#returns-list').insertAdjacentHTML('beforeend', '<p class="form-error">Có ảnh chưa tải được. Bấm Làm mới để thử lại.</p>');
    } catch (error) {
        if (current === generation && error.name !== 'AbortError') $('#returns-list').textContent = error.message;
    }
}
function submit(form, errorId, handler) {
    form.addEventListener('submit', async event => {
        event.preventDefault(); event.stopPropagation();
        const button = form.querySelector('[type="submit"]'); if (button.disabled) return;
        button.disabled = true; $(errorId).textContent = ''; const epoch = app.state.epoch;
        try { await handler(form); }
        catch (error) { if (epoch === app.state.epoch && error.name !== 'AbortError') $(errorId).textContent = error.message; }
        finally { button.disabled = false; }
    });
}
export function initializeAfterSales() {
    submit($('#return-create-form'), '#return-create-error', async form => {
        const files = [...form.elements.images.files];
        if (files.length > 5 || files.some(file => file.size > 2 * 1024 * 1024 || !['image/png', 'image/jpeg'].includes(file.type))) throw new Error('Tối đa 5 ảnh PNG/JPEG, mỗi ảnh tối đa 2 MB.');
        if (!createdRequestId) {
            const result = await app.api('/returns', { method: 'POST', body: { order_id: orderId, kind: form.elements.kind.value, reason: form.elements.reason.value.trim() } });
            createdRequestId = result.id;
        }
        try { await uploadImages(createdRequestId, files); }
        catch (error) {
            form.elements.images.value = '';
            throw new Error('Yêu cầu đã được lưu, nhưng có ảnh chưa gửi được. Bạn có thể đóng cửa sổ và bổ sung ảnh trong mục Đổi / trả hàng. ' + error.message);
        }
        $('#return-create-dialog').close(); app.openDialog('returns-dialog'); await loadReturns(); toast('Đã gửi yêu cầu. Cửa hàng sẽ phản hồi trong mục Đổi / trả hàng.');
    });
    submit($('#return-review-form'), '#return-review-error', async form => {
        await app.api('/returns/' + reviewId + '/review', { method: 'POST', body: { decision: form.elements.decision.value, note: form.elements.note.value.trim() } });
        $('#return-review-dialog').close(); await loadReturns(); toast('Đã lưu kết quả xem xét.');
    });
    document.addEventListener('change', event => {
        const input = event.target.closest('[data-return-upload]'); if (!input || !input.files.length) return;
        const files = [...input.files]; input.disabled = true;
        app.execute(async () => { try { await uploadImages(Number(input.dataset.returnUpload), files); await loadReturns(); toast('Đã bổ sung ảnh.'); } finally { input.disabled = false; input.value = ''; } });
    });
    document.addEventListener('click', event => {
        const button = event.target.closest('[data-action]'); if (!button) return;
        const action = button.dataset.action, id = Number(button.dataset.id);
        if (action === 'request-return') {
            orderId = id; createdRequestId = null; $('#return-create-form').reset(); $('#return-create-error').textContent = ''; app.openDialog('return-create-dialog');
        }
        if (action === 'open-returns') { app.openDialog('returns-dialog'); app.execute(loadReturns); }
        if (action === 'refresh-returns') app.execute(loadReturns);
        if (action === 'review-return') { reviewId = id; $('#return-review-form').reset(); $('#return-review-error').textContent = ''; app.openDialog('return-review-dialog'); }
        if (action === 'receive-return' && confirm('Bạn đã kiểm tra và nhận lại đầy đủ hàng của đơn này? Xác nhận sẽ hoàn số lượng vào kho. Việc hoàn tiền VNPay hoặc gửi hàng đổi cần cửa hàng xử lý riêng.')) {
            button.disabled = true;
            app.execute(async () => {
                try {
                    await app.api('/returns/' + id + '/receive', { method: 'POST' }); await loadReturns();
                    if (app.state.order) { app.state.order = await app.api('/orders/' + app.state.order.id); app.renderOrder(); }
                    toast('Đã ghi nhận hàng trả về và hoàn tồn kho.');
                } finally { button.disabled = false; }
            });
        }
        if (action === 'logout') { releaseImages(); $('#return-create-form').reset(); $('#return-review-form').reset(); $('#returns-list').replaceChildren(); }
    });
    $('#returns-dialog').addEventListener('close', releaseImages);
    $('#return-create-dialog').addEventListener('close', () => $('#return-create-form').reset());
}
