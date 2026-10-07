import { app } from './context.js';

let addresses = [], owner, editingId = null, locationVersion = 0, checkoutVersion = 0;
const $ = selector => document.querySelector(selector);
const toast = message => app.notify('success', message, '', '', { banner: false });
const addressText = row => [row.street_address, row.ward_name, row.district_name, row.province_name].filter(Boolean).join(', ');
const options = (select, rows, key, name, label) => {
    select.innerHTML = '<option value="">' + label + '</option>' + rows.map(row => '<option value="' + app.escapeHtml(row[key]) + '">' + app.escapeHtml(row[name]) + '</option>').join('');
    select.disabled = !rows.length;
};

export async function loadAddressBook() {
    if (!app.state.user) return;
    const currentOwner = app.state.user.id;
    addresses = await app.api('/users/me/addresses');
    owner = currentOwner;
    renderAddresses();
}
function renderAddresses() {
    $('#address-book-list').innerHTML = addresses.length ? addresses.map(row =>
        '<article class="saved-address"><div><strong>' + app.escapeHtml(row.label) + '</strong>' +
        (row.is_default ? '<span class="address-default">Mặc định</span>' : '') +
        '<p>' + app.escapeHtml(row.recipient_name) + ' · ' + app.escapeHtml(row.recipient_phone || '') + '</p><p>' + app.escapeHtml(addressText(row)) + '</p></div>' +
        '<div class="address-actions"><button type="button" class="button secondary small" data-action="edit-address" data-id="' + row.id + '">Sửa</button>' +
        (!row.is_default ? '<button type="button" class="button secondary small" data-action="default-address" data-id="' + row.id + '">Đặt mặc định</button>' : '') +
        '<button type="button" class="button danger small" data-action="delete-address" data-id="' + row.id + '">Xóa</button></div></article>').join('') : '<p class="meta-line">Chưa có địa chỉ đã lưu. Thêm địa chỉ để đặt hàng nhanh hơn.</p>';
}
export async function loadCheckoutAddresses(force = false) {
    if (!app.state.user) { addresses = []; owner = null; $('#checkoutSavedAddress').innerHTML = '<option value="">Đăng nhập để chọn địa chỉ đã lưu</option>'; return; }
    await loadAddressBook();
    const select = $('#checkoutSavedAddress'), previous = select.value;
    select.innerHTML = '<option value="">Nhập địa chỉ khác</option>' + addresses.map(row => '<option value="' + row.id + '">' + app.escapeHtml(row.label + (row.is_default ? ' · Mặc định' : '') + ' — ' + row.street_address) + '</option>').join('');
    select.value = addresses.some(row => String(row.id) === previous) ? previous : '';
    if (force || (!app.checkoutDifferentAddress && !$('#checkoutStreetAddress').value.trim())) {
        const initial = addresses.find(row => row.is_default);
        if (initial) { select.value = initial.id; await selectCheckoutAddress(initial); }
    }
}
async function selectCheckoutAddress(row) {
    const version = ++checkoutVersion;
    await app.loadCheckoutProvinces();
    if (version !== checkoutVersion) return;
    $('#checkoutProvince').value = row.province_id;
    if (!$('#checkoutProvince').value) throw new Error('Địa chỉ không còn trong danh sách GHN. Vui lòng chọn lại.');
    await app.changeCheckoutLocation('province');
    if (version !== checkoutVersion) return;
    $('#checkoutDistrict').value = row.district_id;
    if (!$('#checkoutDistrict').value) throw new Error('Quận/huyện không còn hợp lệ. Vui lòng chọn lại.');
    await app.changeCheckoutLocation('district');
    if (version !== checkoutVersion) return;
    $('#checkoutWard').value = row.ward_code;
    if (!$('#checkoutWard').value) throw new Error('Phường/xã không còn hợp lệ. Vui lòng chọn lại.');
    $('#checkoutStreetAddress').value = row.street_address;
    const form = $('#order-create');
    form.elements.recipient_name.value = row.recipient_name;
    form.elements.recipient_phone.value = row.recipient_phone || '';
    app.checkoutDifferentAddress = false;
    await app.refreshCheckoutFee();
}
async function editAddress(id) {
    if (owner !== app.state.user?.id) await loadAddressBook();
    const row = addresses.find(item => item.id === id);
    if (id && !row) throw new Error('Không tìm thấy địa chỉ. Vui lòng làm mới.');
    editingId = id || null;
    const form = $('#address-book-form'); form.reset();
    $('#address-book-error').textContent = '';
    form.elements.label.value = row?.label || 'Nhà riêng';
    form.elements.recipient_name.value = row?.recipient_name || app.state.user.full_name || '';
    form.elements.recipient_phone.value = row?.recipient_phone || app.state.user.phone || '';
    form.elements.street_address.value = row?.street_address || '';
    form.elements.is_default.checked = row?.is_default || !addresses.length;
    options($('#addressDistrict'), [], '', '', 'Chọn quận / huyện');
    options($('#addressWard'), [], '', '', 'Chọn phường / xã');
    app.openDialog('address-editor-dialog');
    const version = ++locationVersion;
    const provinces = await app.api('/locations/provinces');
    if (version !== locationVersion) return;
    options($('#addressProvince'), provinces, 'ProvinceID', 'ProvinceName', 'Chọn tỉnh / thành');
    if (row) {
        $('#addressProvince').value = row.province_id;
        await changeLocation('province');
        $('#addressDistrict').value = row.district_id;
        await changeLocation('district');
        $('#addressWard').value = row.ward_code;
    }
}
async function changeLocation(level) {
    const version = ++locationVersion;
    options($('#addressWard'), [], '', '', 'Chọn phường / xã');
    if (level === 'province') {
        options($('#addressDistrict'), [], '', '', 'Chọn quận / huyện');
        const id = $('#addressProvince').value; if (!id) return;
        const rows = await app.api('/locations/districts?province_id=' + encodeURIComponent(id));
        if (version === locationVersion) options($('#addressDistrict'), rows, 'DistrictID', 'DistrictName', 'Chọn quận / huyện');
    } else {
        const id = $('#addressDistrict').value; if (!id) return;
        const rows = await app.api('/locations/wards?district_id=' + encodeURIComponent(id));
        if (version === locationVersion) options($('#addressWard'), rows, 'WardCode', 'WardName', 'Chọn phường / xã');
    }
}
async function refreshProfileAndAddresses() {
    app.state.user = await app.api('/users/me');
    await loadAddressBook();
    app.renderProfile();
    await app.loadProfileAddress();
    app.renderIdentity();
}
function submit(form, errorId, handler) {
    form.addEventListener('submit', async event => {
        event.preventDefault(); event.stopPropagation();
        const button = form.querySelector('[type="submit"]');
        if (button.disabled) return;
        button.disabled = true; $(errorId).textContent = '';
        const epoch = app.state.epoch;
        try { await handler(form); }
        catch (error) { if (epoch === app.state.epoch && error.name !== 'AbortError') $(errorId).textContent = error.message || 'Chưa thực hiện được. Vui lòng thử lại.'; }
        finally { button.disabled = false; }
    });
}
export function initializeAccountServices() {
    submit($('#address-book-form'), '#address-book-error', async form => {
        const body = Object.fromEntries(new FormData(form));
        body.province_id = Number(body.province_id); body.district_id = Number(body.district_id);
        body.is_default = form.elements.is_default.checked;
        await app.api('/users/me/addresses' + (editingId ? '/' + editingId : ''), { method: editingId ? 'PUT' : 'POST', body });
        $('#address-editor-dialog').close(); await refreshProfileAndAddresses(); toast('Đã lưu địa chỉ nhận hàng.');
    });
    submit($('#change-password-form'), '#change-password-error', async form => {
        const current = form.elements.current_password.value, next = form.elements.new_password.value;
        if (next !== form.elements.confirmation.value) throw new Error('Mật khẩu nhập lại chưa khớp.');
        if (new TextEncoder().encode(next).length > 72) throw new Error('Mật khẩu tối đa 72 byte. Hãy dùng mật khẩu ngắn hơn.');
        const email = app.state.user?.email;
        await app.api('/users/me/password', { method: 'POST', body: { current_password: current, new_password: next } });
        form.reset(); $('#change-password-dialog').close();
        app.clearSession(); app.renderContext(); app.openAuth();
        $('#auth-email').value = email || ''; toast('Đã đổi mật khẩu. Vui lòng đăng nhập lại.');
    });
    $('#addressProvince').addEventListener('change', () => app.execute(() => changeLocation('province')));
    $('#addressDistrict').addEventListener('change', () => app.execute(() => changeLocation('district')));
    $('#checkoutSavedAddress').addEventListener('change', event => {
        const row = owner === app.state.user?.id && addresses.find(item => String(item.id) === event.target.value);
        if (row) app.execute(() => selectCheckoutAddress(row));
        else { ++checkoutVersion; app.checkoutDifferentAddress = true; }
    });
    ['checkoutProvince', 'checkoutDistrict', 'checkoutWard', 'checkoutStreetAddress'].forEach(id => $( '#' + id).addEventListener('change', () => { ++checkoutVersion; $('#checkoutSavedAddress').value = ''; }));
    document.addEventListener('click', event => {
        const button = event.target.closest('[data-action]'); if (!button) return;
        const action = button.dataset.action, id = Number(button.dataset.id);
        if (action === 'new-address' || action === 'edit-address') app.execute(() => editAddress(action === 'new-address' ? null : id));
        if (action === 'open-change-password') { $('#change-password-form').reset(); $('#change-password-error').textContent = ''; app.openDialog('change-password-dialog'); }
        if (action === 'default-address') app.execute(async () => { await app.api('/users/me/addresses/' + id + '/default', { method: 'POST' }); await refreshProfileAndAddresses(); toast('Đã đổi địa chỉ mặc định.'); });
        if (action === 'delete-address' && confirm('Xóa địa chỉ đã lưu này?')) app.execute(async () => { await app.api('/users/me/addresses/' + id, { method: 'DELETE' }); await refreshProfileAndAddresses(); toast('Đã xóa địa chỉ.'); });
        if (action === 'logout') { addresses = []; owner = null; ++checkoutVersion; ++locationVersion; $('#address-book-form').reset(); $('#change-password-form').reset(); }
    });
    $('#change-password-dialog').addEventListener('close', () => $('#change-password-form').reset());
    $('#address-editor-dialog').addEventListener('close', () => { ++locationVersion; $('#address-book-form').reset(); });
}
