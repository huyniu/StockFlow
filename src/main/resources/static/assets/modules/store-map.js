import { app } from './context.js';

export function initializeStoreMap() {
    const dialog = document.querySelector('#store-map');
    const list = document.querySelector('#store-map-list');
    const status = document.querySelector('#store-map-status');
    const content = document.querySelector('#store-map-content');
    const frame = document.querySelector('#store-map-frame');
    const title = document.querySelector('#store-map-selected');
    const address = document.querySelector('#store-map-address');
    const notice = document.querySelector('#store-map-demo');
    const unavailable = document.querySelector('#store-map-unavailable');
    const links = document.querySelector('#store-map-links');
    const mapHelp = dialog.querySelector('.store-map-help');
    const largeMap = document.querySelector('#store-map-external');
    const directions = document.querySelector('#store-map-directions');
    const reload = document.querySelector('#store-map-reload');
    const retry = document.querySelector('#store-map-retry');
    const confirm = document.querySelector('#store-map-confirm');
    const choice = document.querySelector('#store-map-choice');
    const header = document.querySelector('#header-branch-picker');
    let locations = [];
    let loading = false;
    let previewWarehouseId = '';
    let opener;

    const branches = () => app.state.warehouses;

    function selectBranch(branch) {
        previewWarehouseId = String(branch.id);
        const location = locations.find(row => row.warehouse_id === branch.id);
        for (const button of list.querySelectorAll('button')) {
            button.setAttribute('aria-pressed', String(button.dataset.warehouseId === previewWarehouseId));
        }
        title.textContent = location?.name || branch.name;
        address.textContent = location?.address || '';
        notice.hidden = !location?.demo;
        unavailable.hidden = Boolean(location);
        frame.hidden = !location;
        links.hidden = !location;
        mapHelp.hidden = !location;
        reload.hidden = !location;
        confirm.disabled = false;
        choice.textContent = 'Đang xem: ' + branch.name;
        if (!location) {
            frame.removeAttribute('src');
            return;
        }
        frame.title = 'Google Maps ' + branch.name + (location.demo ? ' — vị trí demo' : '');
        const { latitude, longitude } = location;
        const coordinates = latitude + ',' + longitude;
        const map = new URL('https://www.google.com/maps');
        map.searchParams.set('q', coordinates);
        map.searchParams.set('z', '15');
        map.searchParams.set('hl', 'vi');
        map.searchParams.set('output', 'embed');
        if (frame.getAttribute('src') !== map.href) frame.src = map.href;
        const external = new URL('https://www.google.com/maps/search/');
        external.searchParams.set('api', '1');
        external.searchParams.set('query', coordinates);
        largeMap.href = external.href;
        const route = new URL('https://www.google.com/maps/dir/');
        route.searchParams.set('api', '1');
        route.searchParams.set('destination', coordinates);
        directions.href = route.href;
    }

    function renderBranches() {
        list.replaceChildren();
        const choices = branches();
        content.hidden = choices.length === 0;
        confirm.disabled = choices.length === 0;
        for (const branch of choices) {
            const location = locations.find(row => row.warehouse_id === branch.id);
            const button = document.createElement('button');
            button.type = 'button';
            button.dataset.warehouseId = String(branch.id);
            button.setAttribute('aria-pressed', 'false');
            const name = document.createElement('strong');
            name.textContent = branch.name;
            const detail = document.createElement('span');
            detail.textContent = location?.address || 'Chưa có vị trí bản đồ';
            button.append(name, detail);
            if (location?.demo) {
                const badge = document.createElement('small');
                badge.textContent = 'Địa điểm demo';
                button.append(badge);
            }
            button.addEventListener('click', () => selectBranch(branch));
            list.append(button);
        }
        const selected = choices.find(branch => String(branch.id) === previewWarehouseId) || choices[0];
        if (selected) selectBranch(selected);
        else {
            previewWarehouseId = '';
            frame.removeAttribute('src');
            status.hidden = false;
            status.textContent = 'Hiện chưa có chi nhánh đang phục vụ. Bạn có thể liên hệ qua điện thoại hoặc Zalo.';
            choice.textContent = 'Chưa có chi nhánh để chọn.';
        }
    }

    async function loadLocations() {
        if (loading) return;
        loading = true;
        retry.hidden = true;
        status.hidden = false;
        status.textContent = 'Đang tải vị trí bản đồ…';
        dialog.setAttribute('aria-busy', 'true');
        try {
            const response = await app.api('/storefront/locations', { anonymous: true, channel: 'store-map' });
            if (!Array.isArray(response)) throw new Error('Invalid locations');
            locations = response.filter(location =>
                typeof location.name === 'string' && typeof location.address === 'string' &&
                typeof location.demo === 'boolean' && Number.isSafeInteger(location.warehouse_id) &&
                Number.isFinite(location.latitude) && Math.abs(location.latitude) <= 85 &&
                Number.isFinite(location.longitude) && Math.abs(location.longitude) <= 180,
            );
            if (!dialog.open) return;
            status.hidden = true;
            renderBranches();
        } catch {
            if (!dialog.open) return;
            locations = [];
            renderBranches();
            status.hidden = false;
            status.textContent = 'Chưa tải được bản đồ. Bạn vẫn có thể chọn chi nhánh hoặc thử lại.';
            retry.hidden = false;
        } finally {
            loading = false;
            dialog.setAttribute('aria-busy', 'false');
        }
    }

    function openPicker(button) {
        if (dialog.open) return;
        opener = button.closest('#contact-panel') ? document.querySelector('#contact-toggle') : button;
        previewWarehouseId = app.state.branchId;
        renderBranches();
        dialog.showModal();
        document.body.classList.add('store-map-open');
        header.setAttribute('aria-expanded', 'true');
        document.querySelector('#store-map-title').focus({ preventScroll: true });
        void loadLocations();
    }

    confirm.addEventListener('click', () => {
        const branch = branches().find(row => String(row.id) === previewWarehouseId);
        const select = document.querySelector('#shop-warehouse');
        if (!branch || ![...select.options].some(option => option.value === previewWarehouseId)) {
            confirm.disabled = true;
            choice.textContent = 'Chi nhánh này không còn khả dụng. Vui lòng mở lại để chọn chi nhánh khác.';
            return;
        }
        if (select.value !== previewWarehouseId) {
            // Reuse the existing branch-change flow: fee refresh, saved cart and availability.
            select.value = previewWarehouseId;
            select.dispatchEvent(new Event('change', { bubbles: true }));
        }
        dialog.close();
    });
    retry.addEventListener('click', loadLocations);
    reload.addEventListener('click', () => {
        if (frame.hasAttribute('src')) frame.src = frame.src;
    });
    dialog.querySelector('[data-store-map-close]').addEventListener('click', () => dialog.close());
    dialog.addEventListener('click', event => {
        if (event.target !== dialog) return;
        const bounds = dialog.getBoundingClientRect();
        if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) dialog.close();
    });
    dialog.addEventListener('close', () => {
        document.body.classList.remove('store-map-open');
        header.setAttribute('aria-expanded', 'false');
        frame.removeAttribute('src');
        (opener?.isConnected && opener.getClientRects().length ? opener : header).focus({ preventScroll: true });
    });
    for (const event of ['popstate', 'hashchange']) window.addEventListener(event, () => { if (dialog.open) dialog.close(); });
    document.addEventListener('click', event => {
        const button = event.target.closest('[data-store-map-open]');
        if (button) openPicker(button);
    });
}
