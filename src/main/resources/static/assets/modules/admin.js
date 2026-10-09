import { app as __stockflowApp } from './context.js';

function loadingRows(name, columns, message) {
        const count = __stockflowApp.$('#' + name + '-count');
        if (count) count.textContent = '—';
        __stockflowApp.$('#' + name + '-pagination')?.replaceChildren();
        __stockflowApp.$('#' + name + '-rows').innerHTML =
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
            `<tr class="skeleton-status"><td colspan="${columns}"><span class="sr-only" role="status">${__stockflowApp.escapeHtml(message)}</span></td></tr>`;
    }

function emptyRows(name, columns, message) {
        __stockflowApp.$('#' + name + '-rows').innerHTML =
            '<tr><td colspan="' + columns + '" class="empty-cell">' + __stockflowApp.escapeHtml(message) + '</td></tr>';
    }

function renderPager(name, result) {
        const start = result.total_elements ? result.page * result.size + 1 : 0;
        const end = Math.min((result.page + 1) * result.size, result.total_elements);
        __stockflowApp.$('#' + name + '-pagination').innerHTML =
            '<span>' +
            __stockflowApp.integer(start) +
            '–' +
            __stockflowApp.integer(end) +
            ' trên ' +
            __stockflowApp.integer(result.total_elements) +
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

function productCell(product, metadata = '') {
        const productId = product.product_id ?? (product.inventory_id == null ? product.id : null);
        const cached = __stockflowApp.state.products.get(Number(productId));
        const sku = cached ? __stockflowApp.saleSku(cached) : product;
        const name = cached
            ? __stockflowApp.cartProductName(sku)
            : product.product_name || product.name || 'Sản phẩm của tồn kho #' + product.inventory_id;
        const code = product.product_sku || product.sku || sku.sku || (productId ? '#' + productId : '');
        const image = __stockflowApp.safeProductImageUrl(sku.image_url) ||
            (sku.image_urls || []).map(__stockflowApp.safeProductImageUrl).find(Boolean) ||
            __stockflowApp.safeProductImageUrl(product.image_url);
        return `<div class="product-cell">
            <span class="product-symbol" aria-hidden="true">
                ${__stockflowApp.icon('box')}
                ${image ? `<img src="${__stockflowApp.escapeHtml(image)}" alt="" width="52" height="52"
                    loading="lazy" decoding="async" data-admin-thumbnail />` : ''}
            </span>
            <div class="product-cell-copy"><strong>${__stockflowApp.escapeHtml(name)}</strong>
                <span class="meta-line mono">${__stockflowApp.escapeHtml([code, metadata].filter(Boolean).join(' · '))}</span>
            </div>
        </div>`;
    }

async function loadInventory() {
        if (!__stockflowApp.hasRole('ADMIN', 'MANAGER', 'WAREHOUSE_STAFF')) return;
        __stockflowApp.loadingRows('inventory', 6, 'Đang tải tồn kho…');
        ['stock-available', 'stock-reserved', 'stock-physical'].forEach((id) => {
            __stockflowApp.$('#' + id).textContent = '—';
        });
        try {
            const result = await __stockflowApp.api('/inventories', {
                channel: 'inventory',
                query: {
                    warehouseId: __stockflowApp.$('#inventory-warehouse').value,
                    productId: __stockflowApp.$('#inventory-product').value,
                    page: __stockflowApp.state.pages.inventory,
                    size: 10,
                },
            });
            __stockflowApp.$('#inventory-rows').innerHTML = result.content
                .map(
                    (stock) =>
                        '<tr class="' +
                        (stock.available_quantity <= 5 ? 'inventory-warning' : '') +
                        '"><td>' +
                        __stockflowApp.productCell(stock, 'Tồn kho #' + stock.id) +
                        (stock.available_quantity <= 5
                            ? '<span class="stock-alert-badge">' +
                              __stockflowApp.icon('alert') +
                              (stock.available_quantity === 0 ? 'Hết hàng' : 'Sắp hết') +
                              '</span>'
                            : '') +
                        '</td><td>' +
                        __stockflowApp.escapeHtml(stock.warehouse_name) +
                        '</td><td class="align-right"><span class="' +
                        (stock.available_quantity <= 10 ? 'low-number' : 'price') +
                        '">' +
                        __stockflowApp.integer(stock.available_quantity) +
                        '</span></td><td class="align-right">' +
                        __stockflowApp.integer(stock.reserved_quantity) +
                        '</td><td class="align-right price">' +
                        __stockflowApp.integer(stock.physical_quantity) +
                        '</td><td><span class="subtle">' +
                        __stockflowApp.escapeHtml(__stockflowApp.dateTime(stock.updated_at)) +
                        '</span></td></tr>',
                )
                .join('');
            if (!result.content.length)
                __stockflowApp.emptyRows('inventory', 6, 'Chưa có tồn kho phù hợp. Nhập kho để tạo số tồn ban đầu.');
            __stockflowApp.$('#stock-available').textContent = __stockflowApp.integer(
                result.content.reduce((sum, row) => sum + row.available_quantity, 0),
            );
            __stockflowApp.$('#stock-reserved').textContent = __stockflowApp.integer(
                result.content.reduce((sum, row) => sum + row.reserved_quantity, 0),
            );
            __stockflowApp.$('#stock-physical').textContent = __stockflowApp.integer(
                result.content.reduce((sum, row) => sum + row.physical_quantity, 0),
            );
            __stockflowApp.$('#inventory-count').textContent = __stockflowApp.integer(result.total_elements) + ' dòng tồn kho';
            __stockflowApp.renderPager('inventory', result);
        } catch (error) {
            if (error.name !== 'AbortError') {
                __stockflowApp.emptyRows('inventory', 6, 'Không tải được tồn kho. Kiểm tra kho được phân công hoặc quyền truy cập.');
                __stockflowApp.$('#inventory-pagination').innerHTML = '';
                __stockflowApp.$('#inventory-count').textContent = '—';
            }
            throw error;
        }
    }

async function loadLedger() {
        if (!__stockflowApp.hasRole('ADMIN', 'MANAGER')) return;
        __stockflowApp.loadingRows('ledger', 5, 'Đang tải lịch sử kiểm toán…');
        try {
            const result = await __stockflowApp.api('/inventories/movements', {
                channel: 'ledger',
                query: { inventoryId: __stockflowApp.$('#ledger-inventory').value, page: __stockflowApp.state.pages.ledger, size: 10 },
            });
            __stockflowApp.$('#ledger-rows').innerHTML = result.content
                .map(
                    (movement) =>
                        '<tr><td>' + __stockflowApp.productCell(movement) +
                        '<div class="ledger-movement-details"><strong>' +
                        __stockflowApp.escapeHtml(__stockflowApp.MOVEMENT_LABELS[movement.type] || movement.type) +
                        '</strong><span class="meta-line mono">' +
                        __stockflowApp.escapeHtml(movement.type) +
                        '</span><span class="meta-line">Tồn kho #' +
                        movement.inventory_id +
                        ' · Biến động #' +
                        movement.id +
                        '</span></div></td><td class="align-right price">' +
                        __stockflowApp.integer(movement.quantity) +
                        '</td><td><div class="balance-change"><span>' +
                        __stockflowApp.integer(movement.balance_before) +
                        '</span>' +
                        __stockflowApp.icon('arrow') +
                        '<span class="after">' +
                        __stockflowApp.integer(movement.balance_after) +
                        '</span></div></td><td><strong>' +
                        __stockflowApp.escapeHtml(movement.reference_type || 'NHẬP HÀNG THỦ CÔNG') +
                        (movement.reference_id ? ' #' + movement.reference_id : '') +
                        '</strong><span class="meta-line">Người thực hiện #' +
                        movement.performed_by +
                        '</span></td><td><span class="subtle">' +
                        __stockflowApp.escapeHtml(__stockflowApp.dateTime(movement.created_at)) +
                        '</span><span class="meta-line ledger-note">' +
                        __stockflowApp.escapeHtml(movement.note || '—') +
                        '</span></td></tr>',
                )
                .join('');
            if (!result.content.length) __stockflowApp.emptyRows('ledger', 5, 'Chưa có biến động phù hợp với bộ lọc.');
            __stockflowApp.$('#ledger-count').textContent = __stockflowApp.integer(result.total_elements) + ' biến động';
            __stockflowApp.renderPager('ledger', result);
        } catch (error) {
            if (error.name !== 'AbortError') __stockflowApp.emptyRows('ledger', 5, 'Chưa tải được sổ cái. Vui lòng làm mới.');
            throw error;
        }
    }

async function stockIn(form) {
        const data = Object.fromEntries(new FormData(form));
        const body = {
            product_id: Number(data.product_id),
            warehouse_id: Number(data.warehouse_id),
            quantity: Number(data.quantity),
            note: data.note.trim() || null,
        };
        const stock = await __stockflowApp.api('/inventories/stock-in', { method: 'POST', body });
        __stockflowApp.$('#inventory-warehouse').value = String(stock.warehouse_id);
        __stockflowApp.$('#inventory-product').value = String(stock.product_id);
        __stockflowApp.state.pages.inventory = 0;
        __stockflowApp.notify(
            'success',
            'Đã nhập ' +
                __stockflowApp.integer(body.quantity) +
                ' sản phẩm. Tồn khả dụng mới: ' +
                __stockflowApp.integer(stock.available_quantity) +
                '.',
            'HTTP 201 Created',
            '/api/v1/inventories/stock-in',
        );
        const operations = [__stockflowApp.loadInventory()];
        if (__stockflowApp.hasRole('ADMIN', 'MANAGER')) operations.push(__stockflowApp.loadLedger());
        await Promise.all(operations);
    }

async function loadSummary() {
        __stockflowApp.$('#order-summary').innerHTML =
            Array.from(
                { length: 4 },
                () => `
            <div class="report-metric" aria-hidden="true">
                <span class="skeleton skeleton-line"></span>
                <span class="skeleton skeleton-card-price"></span>
            </div>
        `,
            ).join('') + '<span class="sr-only" role="status">Đang tổng hợp đơn hàng…</span>';
        __stockflowApp.$('#summary-statuses').replaceChildren();
        const result = await __stockflowApp.api('/reports/order-summary', { channel: 'summary' });
        const groups = new Map(result.map((row) => [row.order_status, row]));
        const total = result.reduce((sum, row) => sum + row.total_count, 0);
        const confirmed = result
            .filter((row) => ['CONFIRMED', 'PACKED', 'SHIPPED', 'DELIVERED'].includes(row.order_status))
            .reduce((sum, row) => sum + row.total_count, 0);
        const closed = (groups.get('CANCELLED')?.total_count || 0) + (groups.get('EXPIRED')?.total_count || 0);
        __stockflowApp.$('#order-summary').innerHTML = [
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
                    __stockflowApp.integer(value) +
                    '</strong><p>Tổng hợp mọi thời gian</p></div>',
            )
            .join('');
        __stockflowApp.$('#summary-statuses').innerHTML = Object.keys(__stockflowApp.STATUS_LABELS)
            .map((status) => {
                const row = groups.get(status);
                return (
                    '<div class="summary-chip">' +
                    __stockflowApp.statusBadge(status) +
                    '<strong>' +
                    __stockflowApp.integer(row?.total_count || 0) +
                    '</strong><span class="subtle">' +
                    __stockflowApp.amount(row?.total_amount || 0) +
                    '</span></div>'
                );
            })
            .join('');
    }

function reportQuery() {
        return { fromDate: __stockflowApp.$('#report-from').value, toDate: __stockflowApp.$('#report-to').value };
    }

function reportChartState(name, loading, message) {
        const chart = __stockflowApp.$('#' + name + '-chart');
        chart.innerHTML = loading
            ? `<div class="chart-loading" aria-hidden="true">
                   <span class="skeleton skeleton-line short"></span>
                   <div class="skeleton skeleton-chart"></div>
               </div>
               <span class="sr-only" role="status">${__stockflowApp.escapeHtml(message)}</span>`
            : `<p class="chart-empty">${__stockflowApp.escapeHtml(message)}</p>`;
    }

function renderRevenueChart(rows, groupBy) {
        if (!rows.length) {
            __stockflowApp.reportChartState('revenue', false, 'Chưa có doanh thu để biểu diễn trong kỳ đã chọn.');
            return;
        }
        const maximum = Math.max(...rows.map((row) => Number(row.total_revenue)), 0);
        __stockflowApp.$('#revenue-chart').innerHTML = `
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
                                <span class="chart-value" title="${__stockflowApp.escapeHtml(__stockflowApp.amount(row.total_revenue))}">${__stockflowApp.amount(row.total_revenue)}</span>
                                <div class="revenue-bar-stage" aria-hidden="true">
                                    <span class="revenue-bar" style="--bar-height: ${height}%"></span>
                                </div>
                                <strong class="chart-period">${__stockflowApp.escapeHtml(period)}</strong>
                                <span class="chart-warehouse" title="${__stockflowApp.escapeHtml(row.warehouse_name)}">${__stockflowApp.escapeHtml(row.warehouse_name)}</span>
                            </li>`;
                            })
                            .join('')}
                    </ol>
                </div>
            </figure>`;
    }

function renderTopChart(result) {
        const rows = result.content;
        if (!rows.length) {
            __stockflowApp.reportChartState('top', false, 'Chưa có sản phẩm bán ra để so sánh trong kỳ đã chọn.');
            return;
        }
        const total = rows.reduce((sum, row) => sum + Number(row.total_revenue), 0);
        __stockflowApp.$('#top-chart').innerHTML = `
            <div class="chart-heading">
                <strong>Tỷ trọng doanh thu sản phẩm</strong>
                <span>So với tổng doanh thu của các sản phẩm trong trang hiện tại</span>
            </div>
            <ol class="top-chart-list">
                ${rows
                    .map((row, index) => {
                        const share =
                            total > 0 ? Math.max(0, Math.min(100, (Number(row.total_revenue) / total) * 100)) : 0;
                        const percent = __stockflowApp.numberFormat.format(Number(share.toFixed(1))) + '%';
                        return `<li class="top-chart-item">
                        <span class="top-chart-rank">${result.page * result.size + index + 1}</span>
                        <div class="top-chart-product">
                            <div class="top-chart-label">
                                <strong>${__stockflowApp.escapeHtml(row.product_name)}</strong>
                                <span>${percent}</span>
                            </div>
                            <div class="top-chart-track" role="progressbar"
                                aria-label="Tỷ trọng doanh thu của ${__stockflowApp.escapeHtml(row.product_name)} trong trang hiện tại"
                                aria-valuemin="0" aria-valuemax="100"
                                aria-valuenow="${share.toFixed(1)}" aria-valuetext="${percent}">
                                <span style="--bar-width: ${share}%"></span>
                            </div>
                            <div class="top-chart-detail">
                                <span>${__stockflowApp.escapeHtml(row.product_sku)} · ${__stockflowApp.integer(row.total_quantity_sold)} đã bán</span>
                                <strong>${__stockflowApp.amount(row.total_revenue)}</strong>
                            </div>
                        </div>
                    </li>`;
                    })
                    .join('')}
            </ol>`;
    }

async function loadRevenue() {
        __stockflowApp.loadingRows('revenue', 4, 'Đang tổng hợp doanh thu…');
        __stockflowApp.reportChartState('revenue', true, 'Đang tải biểu đồ doanh thu…');
        __stockflowApp.$('#revenue-pagination').replaceChildren();
        __stockflowApp.$('#revenue-count').textContent = '—';
        const groupBy = __stockflowApp.$('#report-group').value;
        try {
            const result = await __stockflowApp.api('/reports/revenue', {
                channel: 'revenue',
                query: {
                    ...__stockflowApp.reportQuery(),
                    warehouseId: __stockflowApp.$('#report-warehouse').value,
                    groupBy,
                    page: __stockflowApp.state.pages.revenue,
                    size: 8,
                },
            });
            __stockflowApp.$('#revenue-rows').innerHTML = result.content
                .map((row) => {
                    const period = groupBy === 'MONTH' ? row.period.slice(0, 7) : row.period;
                    return (
                        '<tr><td class="mono">' +
                        __stockflowApp.escapeHtml(period) +
                        '</td><td>' +
                        __stockflowApp.escapeHtml(row.warehouse_name) +
                        '</td><td class="align-right">' +
                        __stockflowApp.integer(row.total_orders) +
                        '</td><td class="align-right price">' +
                        __stockflowApp.amount(row.total_revenue) +
                        '</td></tr>'
                    );
                })
                .join('');
            if (!result.content.length)
                __stockflowApp.emptyRows('revenue', 4, 'Chưa có doanh thu trong kỳ. Thanh toán một đơn để xem báo cáo.');
            __stockflowApp.$('#revenue-count').textContent = __stockflowApp.integer(result.total_elements) + ' nhóm kỳ / kho';
            __stockflowApp.renderPager('revenue', result);
            __stockflowApp.renderRevenueChart(result.content, groupBy);
        } catch (error) {
            if (error.name !== 'AbortError') {
                __stockflowApp.emptyRows('revenue', 4, 'Chưa tải được doanh thu. Kiểm tra bộ lọc rồi thử lại.');
                __stockflowApp.reportChartState('revenue', false, 'Chưa tải được biểu đồ doanh thu. Vui lòng thử lại.');
            }
            throw error;
        }
    }

async function loadTop() {
        __stockflowApp.loadingRows('top', 4, 'Đang tải sản phẩm bán chạy…');
        __stockflowApp.reportChartState('top', true, 'Đang tải biểu đồ sản phẩm bán chạy…');
        __stockflowApp.$('#top-pagination').replaceChildren();
        __stockflowApp.$('#top-count').textContent = '—';
        try {
            const result = await __stockflowApp.api('/reports/top-products', {
                channel: 'top',
                query: { ...__stockflowApp.reportQuery(), page: __stockflowApp.state.pages.top, size: 8 },
            });
            __stockflowApp.$('#top-rows').innerHTML = result.content
                .map(
                    (row) =>
                        '<tr><td>' +
                        __stockflowApp.productCell(row) +
                        '</td><td>' +
                        __stockflowApp.escapeHtml(row.category_name) +
                        '</td><td class="align-right">' +
                        __stockflowApp.integer(row.total_quantity_sold) +
                        '</td><td class="align-right price">' +
                        __stockflowApp.amount(row.total_revenue) +
                        '</td></tr>',
                )
                .join('');
            if (!result.content.length) __stockflowApp.emptyRows('top', 4, 'Chưa có sản phẩm bán ra trong khoảng ngày đã chọn.');
            __stockflowApp.$('#top-count').textContent = __stockflowApp.integer(result.total_elements) + ' sản phẩm';
            __stockflowApp.renderPager('top', result);
            __stockflowApp.renderTopChart(result);
        } catch (error) {
            if (error.name !== 'AbortError') {
                __stockflowApp.emptyRows('top', 4, 'Chưa tải được sản phẩm bán chạy. Kiểm tra bộ lọc rồi thử lại.');
                __stockflowApp.reportChartState('top', false, 'Chưa tải được biểu đồ sản phẩm bán chạy. Vui lòng thử lại.');
            }
            throw error;
        }
    }

async function loadLow() {
        __stockflowApp.loadingRows('low', 5, 'Đang kiểm tra hàng sắp hết…');
        const result = await __stockflowApp.api('/reports/low-stock', {
            channel: 'low',
            query: {
                warehouseId: __stockflowApp.$('#report-warehouse').value,
                threshold: __stockflowApp.$('#report-threshold').value,
                page: __stockflowApp.state.pages.low,
                size: 8,
            },
        });
        __stockflowApp.$('#low-rows').innerHTML = result.content
            .map(
                (row) =>
                    '<tr><td>' +
                    __stockflowApp.productCell(row) +
                    '</td><td>' +
                    __stockflowApp.escapeHtml(row.warehouse_name) +
                    '</td><td class="align-right"><span class="low-number">' +
                    __stockflowApp.integer(row.available_quantity) +
                    '</span></td><td class="align-right">' +
                    __stockflowApp.integer(row.reserved_quantity) +
                    '</td><td class="align-right price">' +
                    __stockflowApp.integer(row.physical_quantity) +
                    '</td></tr>',
            )
            .join('');
        if (!result.content.length) __stockflowApp.emptyRows('low', 5, 'Không có dòng tồn kho dưới ngưỡng cảnh báo đã chọn.');
        __stockflowApp.$('#low-count').textContent = __stockflowApp.integer(result.total_elements) + ' cảnh báo';
        __stockflowApp.renderPager('low', result);
    }

async function loadReports() {
        if (!__stockflowApp.hasRole('ADMIN', 'MANAGER')) return;
        const names = ['summary', 'revenue', 'top', 'low'];
        const results = await Promise.allSettled([__stockflowApp.loadSummary(), __stockflowApp.loadRevenue(), __stockflowApp.loadTop(), __stockflowApp.loadLow()]);
        results.forEach((result, index) => {
            if (result.status === 'rejected' && result.reason.name !== 'AbortError') {
                const name = names[index];
                if (name === 'summary') {
                    __stockflowApp.$('#order-summary').innerHTML = '<div class="inline-info">Chưa tải được tổng hợp đơn hàng.</div>';
                    __stockflowApp.$('#summary-statuses').innerHTML = '';
                } else {
                    __stockflowApp.emptyRows(name, name === 'low' ? 5 : 4, 'Không tải được báo cáo. Kiểm tra bộ lọc rồi thử lại.');
                    __stockflowApp.$('#' + name + '-pagination').innerHTML = '';
                }
            }
        });
        const failure = results.find((result) => result.status === 'rejected' && result.reason.name !== 'AbortError');
        if (failure) throw failure.reason;
    }

function catalogStatus(product) {
        const active = product.status === 'ACTIVE';
        return `<span class="catalog-status ${active ? 'is-active' : 'is-inactive'}">
            <span aria-hidden="true"></span>${active ? 'Đang bán' : 'Đã ẩn'}
        </span>`;
    }

function catalogMedia(product, detail = false) {
        const image = __stockflowApp.safeProductImageUrl(product.image_url);
        return `<span class="catalog-product-media ${detail ? 'catalog-detail-media' : ''}">
            ${__stockflowApp.icon('box')}
            ${image ? `<img src="${__stockflowApp.escapeHtml(image)}" alt="${__stockflowApp.escapeHtml(product.name)}" loading="lazy" data-catalog-image />` : ''}
        </span>`;
    }

function catalogProductRow(product) {
        const active = product.status === 'ACTIVE';
        return `<tr class="${active ? '' : 'catalog-row-inactive'}">
            <td><div class="catalog-product-cell">
                ${__stockflowApp.catalogMedia(product)}
                <div><strong>${__stockflowApp.escapeHtml(product.name)}</strong><span class="catalog-sku">${__stockflowApp.escapeHtml(product.sku)}</span></div>
            </div></td>
            <td><span class="catalog-category-name">${__stockflowApp.escapeHtml(product.category_name || 'Chưa phân loại')}</span>
                ${product.brand_name ? '<small class="catalog-product-brand">' + __stockflowApp.escapeHtml(product.brand_name) + '</small>' : ''}</td>
            <td class="align-right catalog-price">${__stockflowApp.amount(product.unit_price)}</td>
            <td>${__stockflowApp.catalogStatus(product)}</td>
            <td><div class="catalog-row-actions">
                <button class="catalog-action" type="button" data-action="manage-product-variants" data-id="${product.id}" aria-label="Quản lý phiên bản và màu của ${__stockflowApp.escapeHtml(product.name)}">
                    ${__stockflowApp.icon('box')}<span>Phiên bản</span>
                </button>
                <button class="catalog-action" type="button" data-action="manage-product-colors" data-id="${product.id}" aria-label="Quản lý màu sắc của ${__stockflowApp.escapeHtml(product.name)}">
                    <span class="configuration-color-icon" aria-hidden="true"></span><span>Màu sắc</span>
                </button>
                <button class="catalog-action" type="button" data-action="view-product" data-id="${product.id}" aria-label="Xem chi tiết ${__stockflowApp.escapeHtml(product.name)}">
                    ${__stockflowApp.icon('eye')}<span>Chi tiết</span>
                </button>
                <button class="catalog-action" type="button" data-action="edit-product" data-id="${product.id}" aria-label="Sửa ${__stockflowApp.escapeHtml(product.name)}">
                    ${__stockflowApp.icon('edit')}<span>Sửa</span>
                </button>
                <button class="catalog-action ${active ? 'catalog-action-danger' : 'catalog-action-restore'}" type="button" data-action="toggle-status" data-id="${product.id}" aria-label="${active ? 'Xóa khỏi cửa hàng' : 'Khôi phục'} ${__stockflowApp.escapeHtml(product.name)}">
                    ${__stockflowApp.icon(active ? 'trash' : 'refresh')}<span>${active ? 'Xóa' : 'Khôi phục'}</span>
                </button>
            </div></td>
        </tr>`;
    }

async function loadManage() {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        __stockflowApp.loadingRows('manage', 5, 'Đang tải sản phẩm…');
        __stockflowApp.$('#manage-count').textContent = '—';
        __stockflowApp.$('#manage-pagination').replaceChildren();
        const query = {
            page: __stockflowApp.state.pages.manage,
            size: 10,
            sort: 'id,asc',
            q: __stockflowApp.$('#manage-query').value.trim(),
            categoryId: __stockflowApp.$('#manage-category').value,
            status: __stockflowApp.$('#manage-status').value,
            grouped: true,
        };
        const result = await __stockflowApp.api('/products', { anonymous: true, channel: 'manage', query });
        result.content.forEach((product) => __stockflowApp.state.products.set(product.id, product));
        __stockflowApp.$('#manage-count').textContent = __stockflowApp.integer(result.total_elements) + ' sản phẩm';
        __stockflowApp.$('#manage-rows').innerHTML = result.content.map(__stockflowApp.catalogProductRow).join('');
        if (!result.content.length) {
            const filtered = Boolean(query.q || query.categoryId || query.status);
            __stockflowApp.$('#manage-rows').innerHTML = `<tr><td colspan="5"><div class="catalog-empty">
                <span class="catalog-empty-icon">${__stockflowApp.icon(filtered ? 'search' : 'box')}</span>
                <h3>${filtered ? 'Không tìm thấy sản phẩm' : 'Bắt đầu với sản phẩm đầu tiên'}</h3>
                <p>${filtered ? 'Thử từ khóa khác hoặc bỏ bộ lọc để xem lại danh sách.' : 'Thêm tên, giá và hình ảnh để xây dựng catalog của cửa hàng.'}</p>
                <button class="button ${filtered ? 'secondary' : 'primary'}" type="button" data-action="${filtered ? 'reset-manage-filter' : 'open-product-create'}">
                    ${__stockflowApp.icon(filtered ? 'refresh' : 'plus')}<span>${filtered ? 'Bỏ bộ lọc' : 'Thêm sản phẩm'}</span>
                </button>
            </div></td></tr>`;
        }
        __stockflowApp.renderProductOptions();
        __stockflowApp.renderPager('manage', result);
    }

async function loadAdminUsers() {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        __stockflowApp.administeredUsers.clear();
        __stockflowApp.$('#users-rows').innerHTML = '<tr><td colspan="5">Đang tải tài khoản…</td></tr>';
        __stockflowApp.$('#admin-users-count').textContent = '—';
        __stockflowApp.$('#users-pagination').replaceChildren();
        try {
            const result = await __stockflowApp.api('/admin/users', { channel: 'admin-users', query: {
                page: __stockflowApp.state.pages.users, size: 20, q: __stockflowApp.$('#admin-users-query').value.trim(),
                role: __stockflowApp.$('#admin-users-role').value, status: __stockflowApp.$('#admin-users-status').value,
            } });
            result.content.forEach(user => __stockflowApp.administeredUsers.set(user.id,user));
            __stockflowApp.$('#admin-users-count').textContent = __stockflowApp.integer(result.total_elements) + ' tài khoản';
            __stockflowApp.$('#users-rows').innerHTML = result.content.map(user => {
                const protectedAccount = user.role === 'ADMIN' || user.email.endsWith('@stockflow.invalid') || user.id === __stockflowApp.state.user.id;
                return '<tr><td><strong>' + __stockflowApp.escapeHtml(user.full_name) + '</strong><span class="meta-line">#' + user.id + '</span></td><td>' + __stockflowApp.escapeHtml(user.email)
                    + '<span class="meta-line">' + __stockflowApp.escapeHtml(user.phone || 'Chưa có số điện thoại') + '</span></td><td>' + __stockflowApp.escapeHtml(__stockflowApp.ROLE_LABELS[user.role] || user.role)
                    + '</td><td><span class="badge ' + (user.status === 'ACTIVE' ? 'success' : 'neutral') + '">' + (user.status === 'ACTIVE' ? 'Đang hoạt động' : 'Đã khóa')
                    + '</span></td><td><div class="account-admin-actions"><button type="button" class="button secondary small" data-action="view-admin-user" data-id="' + user.id + '">' + (protectedAccount ? 'Chi tiết' : 'Chi tiết / Cấp quyền') + '</button>'
                    + (protectedAccount ? '<span class="meta-line">Tài khoản được bảo vệ</span>' : '<button type="button" class="button ' + (user.status === 'ACTIVE' ? 'danger' : 'secondary')
                        + ' small" data-action="toggle-admin-user" data-id="' + user.id + '">' + (user.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa') + '</button>') + '</div></td></tr>';
            }).join('') || '<tr><td colspan="5">Không có tài khoản phù hợp với bộ lọc.</td></tr>';
            __stockflowApp.renderPager('users', result);
        } catch (error) {
            if (error.name !== 'AbortError' && __stockflowApp.hasRole('ADMIN')) __stockflowApp.$('#users-rows').innerHTML = '<tr><td colspan="5">Chưa tải được tài khoản. Bấm Làm mới để thử lại.</td></tr>';
            throw error;
        }
    }

async function viewAdminUser(id) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        __stockflowApp.$('#admin-user-detail').textContent = 'Đang tải thông tin…';
        const form = __stockflowApp.$('#admin-user-role-form');
        form.hidden = true;
        form.dataset.userId = String(id);
        __stockflowApp.$('#admin-user-role-history').replaceChildren();
        __stockflowApp.openDialog('admin-user-dialog');
        const [user, permissions, history, warehouses] = await Promise.all([
            __stockflowApp.api('/admin/users/' + id, { channel: 'admin-user-detail' }),
            __stockflowApp.api('/admin/users/' + id + '/permissions', { channel: 'admin-user-permissions' }),
            __stockflowApp.api('/admin/users/' + id + '/role-history', { channel: 'admin-user-history' }),
            __stockflowApp.api('/warehouses', { channel: 'admin-user-warehouses' }),
        ]);
        if (!__stockflowApp.$('#admin-user-dialog').open || form.dataset.userId !== String(id) || !__stockflowApp.hasRole('ADMIN')) return;
        const address = user.default_address;
        const entries = [['Họ tên',user.full_name], ['Email',user.email], ['Điện thoại',user.phone || 'Chưa bổ sung'],
            ['Vai trò',__stockflowApp.ROLE_LABELS[user.role] || user.role], ['Trạng thái',user.status === 'ACTIVE' ? 'Đang hoạt động' : 'Đã khóa'],
            ['Ngày tạo',__stockflowApp.dateTime(user.created_at)], ['Địa chỉ mặc định',address ? [address.street_address,address.ward_name,address.district_name,address.province_name].join(', ') : 'Chưa bổ sung']];
        __stockflowApp.$('#admin-user-detail').innerHTML = '<dl class="account-admin-details">' + entries.map(([label,value]) => '<div><dt>' + __stockflowApp.escapeHtml(label) + '</dt><dd>' + __stockflowApp.escapeHtml(value) + '</dd></div>').join('') + '</dl>';
        __stockflowApp.$('#admin-user-role').value = permissions.role;
        __stockflowApp.$('#admin-user-warehouse-options').innerHTML = warehouses.filter(w => w.status === 'ACTIVE').map(w => '<label><input type="checkbox" value="' + Number(w.id) + '"' + (permissions.warehouse_ids.includes(w.id) ? ' checked' : '') + '> ' + __stockflowApp.escapeHtml(w.name) + '</label>').join('') || 'Chưa có kho đang hoạt động.';
        form.hidden = user.role === 'ADMIN' || user.id === __stockflowApp.state.user.id || user.email.endsWith('@stockflow.invalid');
        __stockflowApp.updateAdminRoleFields();
        __stockflowApp.$('#admin-user-role-history').innerHTML = history.length ? '<ul>' + history.map(h => '<li>' + __stockflowApp.escapeHtml(__stockflowApp.dateTime(h.created_at)) + ' · Admin #' + Number(h.actor_id) + ': ' + __stockflowApp.escapeHtml(__stockflowApp.ROLE_LABELS[h.old_role] || h.old_role) + ' → ' + __stockflowApp.escapeHtml(__stockflowApp.ROLE_LABELS[h.new_role] || h.new_role) + ' · Kho: ' + __stockflowApp.escapeHtml(h.old_warehouse_ids.join(', ') || 'Không') + ' → ' + __stockflowApp.escapeHtml(h.new_warehouse_ids.join(', ') || 'Không') + '</li>').join('') + '</ul>' : '<p class="subtle">Chưa có thay đổi quyền.</p>';
    }

function updateAdminRoleFields() {
        __stockflowApp.$('#admin-user-warehouse-field').hidden = __stockflowApp.$('#admin-user-role').value !== 'WAREHOUSE_STAFF';
    }

async function saveAdminUserRole(form) {
        if (!__stockflowApp.hasRole('ADMIN') || form.hidden) return;
        const id = Number(form.dataset.userId);
        const role = __stockflowApp.$('#admin-user-role').value;
        const warehouseIds = role === 'WAREHOUSE_STAFF' ? Array.from(form.querySelectorAll('input[type="checkbox"]:checked'), input => Number(input.value)) : [];
        if (role === 'WAREHOUSE_STAFF' && !warehouseIds.length) throw new Error('Vui lòng chọn ít nhất một kho phụ trách.');
        await __stockflowApp.api('/admin/users/' + id + '/role', { method: 'PATCH', body: { role, warehouse_ids: warehouseIds } });
        __stockflowApp.notify('success', 'Đã cập nhật quyền và lưu lịch sử thay đổi.');
        await Promise.all([__stockflowApp.loadAdminUsers(), __stockflowApp.viewAdminUser(id)]);
    }

async function toggleAdminUser(id) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const user = __stockflowApp.administeredUsers.get(id);
        if (!user) return;
        const status = user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
        if (!window.confirm((status === 'INACTIVE' ? 'Khóa tài khoản ' : 'Mở khóa tài khoản ') + user.full_name + '?')) return;
        await __stockflowApp.api('/admin/users/' + id + '/status', { method: 'PATCH', body: { status } });
        __stockflowApp.notify('success',status === 'INACTIVE' ? 'Đã khóa tài khoản.' : 'Đã mở khóa tài khoản.');
        await __stockflowApp.loadAdminUsers();
    }

async function createProduct(form) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const gallery = __stockflowApp.$('#create-product-gallery');
        if (!__stockflowApp.validateProductGalleryInput(gallery)) throw new Error(gallery.validationMessage);
        const data = Object.fromEntries(new FormData(form));
        const product = await __stockflowApp.api('/products', {
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
                image_urls: __stockflowApp.readGalleryInput(gallery),
                specifications: __stockflowApp.readSpecifications(form),
            },
        });
        form.reset();
        __stockflowApp.$('#product-create-dialog')?.close();
        __stockflowApp.resetProductImagePreviews();
        __stockflowApp.state.pages.manage = 0;
        __stockflowApp.notify('success', 'Đã tạo sản phẩm ' + product.sku + '.', 'HTTP 201 Created');
        await __stockflowApp.loadProductOptions();
        await __stockflowApp.loadBrands();
        await __stockflowApp.loadManage();
    }

async function updateProduct(form) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const gallery = __stockflowApp.$('#update-product-gallery');
        if (!__stockflowApp.validateProductGalleryInput(gallery)) throw new Error(gallery.validationMessage);
        const data = Object.fromEntries(new FormData(form));
        const body = {};
        const storedBrandId = __stockflowApp.state.products.get(Number(data.product_id))?.brand_id;
        if (String(storedBrandId || '') !== data.brand_id) {
            if (data.brand_id) body.brand_id = Number(data.brand_id);
            else body.clear_brand = true;
        }
        if (data.name.trim()) body.name = data.name.trim();
        if (data.unit_price !== '') body.unit_price = Number(data.unit_price);
        if (data.status) body.status = data.status;
        // Form nạp mô tả hiện có; xóa trắng rồi lưu là yêu cầu xóa nội dung rõ ràng.
        body.description = data.description.trim();
        body.specifications = __stockflowApp.readSpecifications(form);
        const imageUrls = __stockflowApp.readGalleryInput(gallery);
        const storedImages = __stockflowApp.state.products.get(Number(data.product_id))?.image_urls || [];
        if (JSON.stringify(imageUrls) !== JSON.stringify(storedImages)) body.image_urls = imageUrls;
        if (__stockflowApp.$('#remove-product-image').checked) body.image_url = '';
        else if (data.image_url?.trim()) body.image_url = data.image_url.trim();
        if (!Object.keys(body).length) throw new Error('Nhập tên, giá, trạng thái, ảnh hoặc mô tả cần cập nhật.');
        const product = await __stockflowApp.api('/products/' + data.product_id, { method: 'PATCH', body });
        __stockflowApp.notify(
            'success',
            'Đã cập nhật ' + product.sku + '. Giá hiện tại: ' + __stockflowApp.amount(product.unit_price),
            'HTTP 200 OK',
        );
        form.reset();
        __stockflowApp.$('#product-edit-dialog')?.close();
        __stockflowApp.resetProductImagePreviews();
        await __stockflowApp.loadProductOptions();
        await __stockflowApp.loadBrands();
        await __stockflowApp.loadManage();
    }

function prepareProductImageUpdate() {
        __stockflowApp.$('#update-product-image').value = '';
        __stockflowApp.$('#remove-product-image').checked = false;
        __stockflowApp.renderProductImagePreview(__stockflowApp.$('#update-product-image'));
        const product = __stockflowApp.state.products.get(Number(__stockflowApp.$('#update-product').value));
        __stockflowApp.$('#update-product-gallery').value = (product?.image_urls || []).join('\n');
        __stockflowApp.renderGalleryInputPreview(__stockflowApp.$('#update-product-gallery'));
    }

function readGalleryInput(input) {
        return input.value
            .split(/\r?\n/)
            .map((line) => line.trim())
            .filter(Boolean);
    }

function validateProductGalleryInput(input) {
        const urls = __stockflowApp.readGalleryInput(input);
        let message = '';
        if (urls.length > __stockflowApp.MAX_GALLERY_IMAGES) message = 'Chỉ được nhập tối đa 8 ảnh bổ sung.';
        else if (urls.some((url) => url.length > 2048 || !__stockflowApp.safeProductImageUrl(url))) {
            message = 'Mỗi ảnh cần link HTTP/HTTPS hợp lệ hoặc /assets/, tối đa 2048 ký tự.';
        } else if (new Set(urls).size !== urls.length) message = 'Các link ảnh bổ sung không được trùng nhau.';
        input.setCustomValidity(message);
        return !message;
    }

function galleryPreviewContent(urls) {
        return `
            <p class="gallery-preview-heading">${urls.length} ảnh bổ sung · theo thứ tự nhập</p>
            <div class="gallery-preview-grid">
                ${urls
                    .map(
                        (url, index) => `
                    <figure class="gallery-preview-item">
                        <img src="${__stockflowApp.escapeHtml(url)}" alt="Ảnh bổ sung ${index + 1}" loading="lazy" decoding="async" data-gallery-preview-image />
                        <span class="gallery-preview-error" hidden>Không tải được ảnh</span>
                        <figcaption>Ảnh ${index + 1}</figcaption>
                    </figure>
                `,
                    )
                    .join('')}
            </div>
        `;
    }

function renderGalleryInputPreview(input) {
        window.clearTimeout(__stockflowApp.imagePreviewTimers.get(input));
        __stockflowApp.imagePreviewTimers.delete(input);
        const preview = __stockflowApp.$('[data-gallery-preview="' + input.dataset.galleryInput + '"]');
        const valid = __stockflowApp.validateProductGalleryInput(input);
        const urls = __stockflowApp.readGalleryInput(input);
        preview.hidden = !__stockflowApp.hasRole('ADMIN') || (valid && !urls.length);
        preview.innerHTML = valid
            ? __stockflowApp.galleryPreviewContent(urls)
            : '<p class="gallery-input-error">' + __stockflowApp.escapeHtml(input.validationMessage) + '</p>';
    }

function validateProductImageInput(input) {
        const invalid = input.value.trim() && !__stockflowApp.safeProductImageUrl(input.value);
        input.setCustomValidity(invalid ? 'Nhập link ảnh HTTP/HTTPS hợp lệ hoặc đường dẫn /assets/.' : '');
        return !invalid;
    }

function renderProductImagePreview(input) {
        window.clearTimeout(__stockflowApp.imagePreviewTimers.get(input));
        __stockflowApp.imagePreviewTimers.delete(input);
        const isUpdate = input.dataset.imageInput === 'update';
        const removing = isUpdate && __stockflowApp.$('#remove-product-image').checked;
        input.disabled = removing;
        const preview = __stockflowApp.$('[data-image-preview="' + input.dataset.imageInput + '"]');
        const image = __stockflowApp.$('.product-image-preview-img', preview);
        const status = __stockflowApp.$('[data-image-preview-status]', preview);
        const product = isUpdate ? __stockflowApp.state.products.get(Number(__stockflowApp.$('#update-product').value)) : null;
        const source = __stockflowApp.safeProductImageUrl(input.value.trim() || product?.image_url);
        const valid = removing || __stockflowApp.validateProductImageInput(input);
        if (removing) input.setCustomValidity('');
        preview.classList.remove('image-preview-failed');
        if (!__stockflowApp.hasRole('ADMIN') || removing || (!source && valid)) {
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
        const epoch = __stockflowApp.state.epoch;
        image.hidden = false;
        status.textContent = 'Đang tải ảnh xem trước…';
        image.onload = () => {
            if (epoch !== __stockflowApp.state.epoch || image.getAttribute('src') !== source) return;
            status.textContent = caption;
        };
        image.onerror = () => {
            if (epoch !== __stockflowApp.state.epoch || image.getAttribute('src') !== source) return;
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

function resetProductImagePreviews() {
        __stockflowApp.$$('[data-image-input]').forEach((input) => {
            input.setCustomValidity('');
            __stockflowApp.renderProductImagePreview(input);
        });
        __stockflowApp.$$('[data-gallery-input]').forEach((input) => {
            input.setCustomValidity('');
            __stockflowApp.renderGalleryInputPreview(input);
        });
    }

function renderBrandLogoPreview(input) {
        window.clearTimeout(__stockflowApp.imagePreviewTimers.get(input));
        __stockflowApp.imagePreviewTimers.delete(input);
        const preview = __stockflowApp.$('[data-brand-logo-preview="' + input.dataset.brandLogoInput + '"]');
        const valid = __stockflowApp.validateProductImageInput(input);
        const source = __stockflowApp.safeProductImageUrl(input.value);
        preview.replaceChildren();
        preview.classList.remove('brand-logo-preview-error');
        preview.hidden = !__stockflowApp.hasRole('ADMIN') || (valid && !source);
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
        const epoch = __stockflowApp.state.epoch;
        status.textContent = 'Đang tải logo…';
        image.onload = () => {
            if (!image.isConnected || epoch !== __stockflowApp.state.epoch) return;
            status.textContent = 'Logo sẽ hiển thị trong menu hãng và danh sách quản trị.';
        };
        image.onerror = () => {
            if (!image.isConnected || epoch !== __stockflowApp.state.epoch) return;
            image.hidden = true;
            preview.classList.add('brand-logo-preview-error');
            status.textContent = 'Không tải được logo. Kiểm tra link ảnh; tên hãng vẫn hiển thị nếu ảnh lỗi.';
        };
        preview.append(image, status);
        image.src = source;
    }

function openBrandLogoEdit(id) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const brand = __stockflowApp.state.brands.find((value) => value.id === id);
        if (!brand) return;
        __stockflowApp.$('#brand-logo-edit').dataset.brandId = String(id);
        __stockflowApp.$('#brand-logo-name').textContent = brand.name;
        __stockflowApp.$('#edit-brand-logo').value = brand.logo_url || '';
        __stockflowApp.renderBrandLogoPreview(__stockflowApp.$('#edit-brand-logo'));
        __stockflowApp.openDialog('brand-logo-dialog');
    }

async function saveBrandLogo(form) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const input = __stockflowApp.$('#edit-brand-logo');
        if (!__stockflowApp.validateProductImageInput(input)) throw new Error(input.validationMessage);
        await __stockflowApp.api('/brands/' + Number(form.dataset.brandId) + '/logo', {
            method: 'PATCH',
            body: { logo_url: input.value.trim() },
        });
        __stockflowApp.$('#brand-logo-dialog').close();
        form.reset();
        __stockflowApp.renderBrandLogoPreview(input);
        __stockflowApp.notify('success', 'Đã cập nhật logo thương hiệu.', 'HTTP 200 OK');
        await __stockflowApp.loadBrands();
    }

async function createCategory(form) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const data = Object.fromEntries(new FormData(form));
        await __stockflowApp.api('/categories', {
            method: 'POST',
            body: {
                name: data.name.trim(),
                slug: data.slug.trim(),
                parent_id: data.parent_id ? Number(data.parent_id) : null,
            },
        });
        form.reset();
        __stockflowApp.$('#category-create-dialog')?.close();
        __stockflowApp.notify('success', 'Đã thêm danh mục ' + data.name.trim() + '.', 'HTTP 201 Created');
        await __stockflowApp.loadCategoryList();
    }

function openCategoryEdit(id) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const category = __stockflowApp.state.categories.find((item) => item.id === id);
        if (!category) return;
        __stockflowApp.$('#category-edit').dataset.categoryId = String(id);
        __stockflowApp.$('#edit-category-name').value = category.name;
        __stockflowApp.$('#edit-category-slug').value = category.slug;
        __stockflowApp.$('#edit-category-path').textContent = __stockflowApp.categoryPath(category);
        __stockflowApp.openDialog('category-edit-dialog');
    }

async function updateCategory(form) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const data = Object.fromEntries(new FormData(form));
        await __stockflowApp.api('/categories/' + Number(form.dataset.categoryId), {
            method: 'PATCH',
            body: { name: data.name.trim(), slug: data.slug.trim() },
        });
        __stockflowApp.$('#category-edit-dialog').close();
        __stockflowApp.notify('success', 'Đã cập nhật danh mục ' + data.name.trim() + '.', 'HTTP 200 OK');
        await __stockflowApp.loadCategoryList();
    }

function confirmCategoryDelete(id) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const category = __stockflowApp.state.categories.find((item) => item.id === id);
        if (!category) return;
        __stockflowApp.$('#confirm-title').textContent = 'Xóa danh mục “' + category.name + '”?';
        __stockflowApp.$('#confirm-message').textContent =
            'Chỉ xóa khi danh mục không còn sản phẩm hoặc danh mục con. Gợi ý hãng của nhóm này sẽ được gỡ; ' +
            'các hãng và dữ liệu bán hàng vẫn được giữ nguyên.';
        __stockflowApp.$('#confirm-submit').textContent = 'Xóa danh mục';
        __stockflowApp.$('#confirm-submit').className = 'button danger';
        __stockflowApp.state.pendingMutation = {
            epoch: __stockflowApp.state.epoch,
            handler: async () => {
                await __stockflowApp.api('/categories/' + category.id, { method: 'DELETE' });
                __stockflowApp.notify('success', 'Đã xóa danh mục ' + category.name + '.', 'HTTP 204 No Content');
                await __stockflowApp.loadCategoryList();
            },
        };
        __stockflowApp.openDialog('confirm-dialog');
    }

async function createBrand(form) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        if (!__stockflowApp.validateProductImageInput(__stockflowApp.$('#create-brand-logo')))
            throw new Error(__stockflowApp.$('#create-brand-logo').validationMessage);
        const data = Object.fromEntries(new FormData(form));
        await __stockflowApp.api('/brands', {
            method: 'POST',
            body: {
                name: data.name.trim(),
                slug: data.slug.trim(),
                category_ids: data.category_id ? [Number(data.category_id)] : [],
                logo_url: data.logo_url.trim(),
            },
        });
        form.reset();
        __stockflowApp.renderBrandLogoPreview(__stockflowApp.$('#create-brand-logo'));
        __stockflowApp.$('#brand-create-dialog').close();
        __stockflowApp.notify('success', 'Đã thêm thương hiệu ' + data.name.trim() + '.', 'HTTP 201 Created');
        await __stockflowApp.loadBrands();
    }

function openProductCreate() {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const form = __stockflowApp.$('#product-create');
        if (form) form.reset();
        __stockflowApp.renderSpecEditor(__stockflowApp.$('[data-spec-editor="create"]'), []);
        __stockflowApp.renderProductImagePreview(__stockflowApp.$('#create-product-image'));
        __stockflowApp.renderGalleryInputPreview(__stockflowApp.$('#create-product-gallery'));
        __stockflowApp.openDialog('product-create-dialog');
    }

function openProductEdit(id) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const product = __stockflowApp.state.products.get(Number(id));
        if (!product) return;
        __stockflowApp.$('#update-product').value = String(product.id);
        __stockflowApp.$('#update-product-brand').value = product.brand_id ? String(product.brand_id) : '';
        const skuBadge = __stockflowApp.$('#update-product-sku-badge');
        if (skuBadge) skuBadge.textContent = 'SKU: ' + product.sku;
        const catText = __stockflowApp.$('#update-product-category-text');
        if (catText) catText.textContent = 'Danh mục: ' + (product.category_name || 'Chưa phân loại');
        __stockflowApp.$('#update-product-name').value = product.name;
        __stockflowApp.$('#update-product-description').value = product.description || '';
        __stockflowApp.renderSpecEditor(__stockflowApp.$('[data-spec-editor="update"]'), product.specifications || []);
        __stockflowApp.$('#update-product-price').value = product.unit_price;
        __stockflowApp.$('#update-product-status').value = product.status;
        __stockflowApp.$('#update-product-image').value = product.image_url || '';
        __stockflowApp.$('#remove-product-image').checked = false;
        __stockflowApp.renderProductImagePreview(__stockflowApp.$('#update-product-image'));
        __stockflowApp.$('#update-product-gallery').value = (product.image_urls || []).join('\n');
        __stockflowApp.renderGalleryInputPreview(__stockflowApp.$('#update-product-gallery'));
        __stockflowApp.openDialog('product-edit-dialog');
    }

function appendSpecRow(editor, specification = {}) {
        const rows = __stockflowApp.$('.spec-editor-rows', editor);
        if (rows.children.length >= 60) throw new Error('Một sản phẩm tối đa 60 thông số.');
        const row = document.createElement('div');
        row.className = 'spec-editor-row';
        row.innerHTML = `<label><span>Tên thông số</span><input data-spec-name maxlength="100" required placeholder="Ví dụ: Chipset" /></label>
            <label><span>Giá trị</span><textarea data-spec-value maxlength="1000" rows="2" required placeholder="Nhập thông số đã kiểm chứng"></textarea></label>
            <button class="icon-button" type="button" data-action="remove-spec-row" aria-label="Xóa dòng thông số">${__stockflowApp.icon('trash')}</button>`;
        __stockflowApp.$('[data-spec-name]', row).value = specification.name || '';
        __stockflowApp.$('[data-spec-value]', row).value = specification.value || '';
        rows.append(row);
        __stockflowApp.$('[data-action="add-spec-row"]', editor).disabled = rows.children.length >= 60;
    }

function renderSpecEditor(editor, specifications) {
        __stockflowApp.$('.spec-editor-rows', editor).replaceChildren();
        __stockflowApp.$('[data-action="add-spec-row"]', editor).disabled = false;
        specifications.forEach((specification) => __stockflowApp.appendSpecRow(editor, specification));
    }

function readSpecifications(form) {
        const rows = __stockflowApp.$$('.spec-editor-row', form).map((row) => ({
            name: __stockflowApp.$('[data-spec-name]', row).value.trim(),
            value: __stockflowApp.$('[data-spec-value]', row).value.trim(),
        }));
        if (rows.some((row) => !row.name || !row.value)) throw new Error('Mỗi thông số phải có tên và giá trị.');
        if (new Set(rows.map((row) => row.name.toLocaleLowerCase('vi-VN'))).size !== rows.length)
            throw new Error('Tên thông số không được trùng nhau.');
        return rows;
    }

function setConfigurationPanel(panel) {
        const dialog = __stockflowApp.$('#product-variants-dialog');
        const selected = panel === 'colors' ? 'colors' : 'versions';
        dialog.dataset.panel = selected;
        __stockflowApp.$('#variants-dialog-title').textContent = selected === 'colors' ? 'Màu sắc sản phẩm' : 'Phiên bản sản phẩm';
        __stockflowApp.$$('[data-configuration-panel]', dialog).forEach((section) => {
            section.hidden = section.dataset.configurationPanel !== selected;
        });
        __stockflowApp.$$('.configuration-tabs [role="tab"]', dialog).forEach((tab) => {
            const active = tab.dataset.panel === selected;
            tab.classList.toggle('is-selected', active);
            tab.setAttribute('aria-selected', String(active));
            tab.tabIndex = active ? 0 : -1;
        });
    }

async function manageProductVariants(id, selectedVersionId = null, panel = 'versions') {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const product = await __stockflowApp.api('/products/' + id, { anonymous: true, channel: 'variant-admin' });
        __stockflowApp.state.products.set(product.id, product);
        const dialog = __stockflowApp.$('#product-variants-dialog');
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
        __stockflowApp.$('#variants-product-name').textContent = product.name;
        __stockflowApp.$('#product-variants-body').innerHTML = /* HTML */ ` <div
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
                    ${__stockflowApp.icon('box')}Phiên bản<span>${versions.filter((version) => !version.archived).length}</span>
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
                            <strong>${__stockflowApp.escapeHtml(version.name)}</strong><small>${version.archived ? 'Đã xóa' : variants.filter((color) => color.version_id === version.id && !color.archived).length + ' màu / SKU'}</small>
                        </button>`,
                            )
                            .join('')}
                    </aside>
                    <form class="version-admin-edit" data-version-id="${selected.id}">
                        <div class="configuration-section-heading"><h3>Thông tin phiên bản</h3><span class="configuration-state ${selected.archived ? 'is-archived' : ''}">${selected.archived ? 'Đã xóa khỏi cửa hàng' : 'Đang sử dụng'}</span></div>
                        <label>Tên phiên bản<input name="name" maxlength="160" value="${__stockflowApp.escapeHtml(selected.name)}" placeholder="Ví dụ: 256 GB hoặc 40mm GPS" required /></label>
                        <details class="version-spec-details" open><summary>Thông số của phiên bản <span>${effectiveVersionSpecifications(product, selected).length} dòng</span></summary>
                            <fieldset class="spec-editor" data-spec-editor="version-edit"><legend>Bảng thông số đầy đủ</legend><p class="subtle">Sửa trực tiếp giá trị cần thay đổi. Các dòng không đổi tiếp tục dùng thông số chung. Tên và việc xóa thông số chung được chỉnh tại mục Sửa sản phẩm.</p><div class="spec-editor-rows"></div><button class="button secondary small" type="button" data-action="add-spec-row">+ Thêm thông số</button></fieldset>
                        </details>
                        <div class="configuration-card-actions">
                            <button class="button primary small" type="submit">Lưu phiên bản</button>
                            ${selected.archived ? `<button class="button secondary small" type="button" data-action="restore-product-version" data-id="${selected.id}">Khôi phục phiên bản</button>` : `<button class="button secondary small" type="button" data-action="configuration-panel" data-panel="colors">Xem màu sắc</button><button class="button danger small" type="button" data-action="delete-product-version" data-id="${selected.id}">${__stockflowApp.icon('trash')}Xóa phiên bản</button>`}
                        </div>
                    </form>
                </div>`
                        : '<p class="configuration-empty">Chưa có phiên bản đang sử dụng. Thêm phiên bản hoặc bật “Hiện phiên bản và màu đã xóa” để khôi phục.</p>'
                }
                ${__stockflowApp.renderVersionCreateForm(product)}
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
                    <label class="configuration-version-select">Chọn phiên bản để quản lý màu<select id="configuration-version-select">${visibleVersions.map((version) => `<option value="${version.id}" ${version.id === selected.id ? 'selected' : ''}>${__stockflowApp.escapeHtml(version.name)}${version.archived ? ' · Đã xóa' : ''}</option>`).join('')}</select></label>
                    ${selected.archived ? '<p class="configuration-empty">Phiên bản này đã xóa khỏi cửa hàng. Khôi phục phiên bản ở tab Phiên bản trước khi thêm hoặc mở bán màu.</p>' : ''}
                    <div class="variant-admin-list">${colors.map((color) => __stockflowApp.renderAdminColor(product, selected, color)).join('')}</div>
                    ${!colors.length ? '<p class="configuration-empty">Phiên bản chưa có màu đang sử dụng. Thêm màu mới bên dưới hoặc bật danh sách đã xóa để khôi phục.</p>' : ''}
                    ${!selected.archived ? __stockflowApp.renderColorCreateForm(product, selected) : ''}
                `
                        : '<p class="configuration-empty">Tạo phiên bản ở tab Phiên bản trước, sau đó thêm các màu vào cấu hình đó.</p><button class="button secondary" type="button" data-action="configuration-panel" data-panel="versions">Đi tới Phiên bản</button>'
                }
            </section>`;
        if (selected) {
            const editor = __stockflowApp.$('[data-spec-editor="version-edit"]', dialog);
            __stockflowApp.renderSpecEditor(editor, effectiveVersionSpecifications(product, selected));
            const sharedNames = new Set((product.specifications || []).map(row => row.name.toLowerCase()));
            editor.querySelectorAll('.spec-editor-row').forEach(row => {
                const name = row.querySelector('[data-spec-name]');
                if (sharedNames.has(name.value.toLowerCase())) {
                    name.readOnly = true;
                    row.querySelector('[data-action="remove-spec-row"]').hidden = true;
                }
            });
        }
        __stockflowApp.renderSpecEditor(__stockflowApp.$('[data-spec-editor="version-create"]', dialog), []);
        __stockflowApp.setConfigurationPanel(panel);
        __stockflowApp.openDialog('product-variants-dialog');
    }

function renderVersionCreateForm(product) {
        const first = !product.versions?.length;
        return /* HTML */ `<details class="configuration-create-disclosure" ${first ? 'open' : ''}>
            <summary>${__stockflowApp.icon('plus')}${first ? 'Khởi tạo phiên bản và màu hiện tại' : 'Thêm phiên bản mới'}</summary>
            <form id="product-version-create" class="stacked-form catalog-form version-create-form">
                <label
                    >Tên phiên bản<input
                        name="name"
                        maxlength="160"
                        placeholder="Ví dụ: 256 GB hoặc 40mm GPS · Dây S/M"
                        required
                /></label>
                ${first ? `<fieldset class="original-color-fields"><legend>SKU hiện tại · ${__stockflowApp.escapeHtml(product.sku)}</legend><p class="subtle">Giữ nguyên giá, ảnh, tồn và lịch sử; nhập đúng màu của SKU đang có.</p><div class="form-grid"><label>Tên màu hiện tại<input name="default_color_name" maxlength="80" placeholder="Ví dụ: Đen" required /></label><label>Mã màu hiển thị<input name="default_color_hex" type="color" value="#202020" /></label></div></fieldset>` : ''}
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
                    ${__stockflowApp.icon('plus')}${first ? 'Lưu phiên bản và màu gốc' : 'Thêm phiên bản'}
                </button>
            </form>
        </details>`;
    }

function renderAdminColor(product, version, color) {
        const blocked = color.archived || version.archived;
        return /* HTML */ `<form
            class="variant-admin-edit ${color.archived ? 'is-archived' : ''}"
            data-variant-id="${color.id}"
        >
            <div class="variant-admin-title">
                <span class="color-dot" style="--color: ${__stockflowApp.colorHex(color.color_hex)}" aria-hidden="true"></span>
                <div>
                    <strong>${__stockflowApp.escapeHtml(color.color_name)}</strong
                    ><small class="mono"
                        >${__stockflowApp.escapeHtml(color.sku)}${color.sku_product_id === product.id ? ' · SKU gốc' : ''}</small
                    >
                </div>
                <span class="configuration-state ${blocked ? 'is-archived' : ''}"
                    >${color.archived ? 'Đã xóa' : color.status === 'ACTIVE' ? 'Đang bán' : 'Ngừng bán'}</span
                >
            </div>
            <fieldset class="configuration-color-fields" ${blocked ? 'disabled' : ''}>
                <div class="form-grid">
                    <label>Tên màu<input name="color_name" required maxlength="80" value="${__stockflowApp.escapeHtml(color.color_name)}" /></label>
                    <label>Màu hiển thị<input name="color_hex" type="color" value="${__stockflowApp.colorHex(color.color_hex)}" /></label>
                </div>
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
                        value="${__stockflowApp.escapeHtml(color.image_url || '')}"
                        placeholder="https://... hoặc /assets/..."
                /></label>
                <label
                    >Ảnh bổ sung (mỗi dòng một link, tối đa 8)<textarea name="image_urls" rows="2">
${__stockflowApp.escapeHtml((color.image_urls || []).join('\n'))}</textarea>
                </label>
            </fieldset>
            <div class="configuration-card-actions">
                ${color.archived ? `<button class="button secondary small" type="button" data-action="restore-product-color" data-id="${color.id}" ${version.archived ? 'disabled' : ''}>Khôi phục màu</button>` : `<button class="button primary small" type="submit" ${version.archived ? 'disabled' : ''}>Lưu màu</button><button class="button danger small" type="button" data-action="delete-product-color" data-id="${color.id}">${__stockflowApp.icon('trash')}Xóa màu</button>`}
            </div>
        </form>`;
    }

function effectiveVersionSpecifications(product, version) {
        if (version.effective_specifications) return version.effective_specifications;
        const merged = new Map((product.specifications || []).map(row => [row.name.toLowerCase(), row]));
        (version.specifications || []).forEach(row => merged.set(row.name.toLowerCase(), row));
        return [...merged.values()];
    }

function renderColorCreateForm(product, version) {
        const variants = product.variants || [];
        const total = variants.filter((color) => color.version_id === version.id).length;
        const sources = variants.filter(color => color.version_id !== version.id && !color.archived &&
            product.versions.some(sourceVersion => sourceVersion.id === color.version_id && !sourceVersion.archived));
        return /* HTML */ `<details class="configuration-create-disclosure" ${!total ? 'open' : ''}>
            <summary>${__stockflowApp.icon('plus')}Thêm màu cho ${__stockflowApp.escapeHtml(version.name)}</summary>
            <form id="product-variant-create" class="stacked-form catalog-form variant-create-form">
                <input name="version_id" type="hidden" value="${version.id}" />
                ${sources.length ? `<div class="color-copy-tools">
                    <label>Lấy màu và ảnh từ phiên bản khác<select name="copy_source"><option value="">Chọn màu có sẵn</option>${sources.map(color =>
                        `<option value="${color.id}">${__stockflowApp.escapeHtml(color.version_name + ' — ' + color.color_name)}</option>`).join('')}</select></label>
                    <button class="button secondary" type="button" data-action="copy-product-color">Sao chép màu và ảnh</button>
                    <p class="subtle" data-copy-color-notice aria-live="polite">Chỉ sao chép tên màu, màu hiển thị và ảnh. Nhập SKU mới và kiểm tra giá của phiên bản này trước khi lưu.</p>
                </div>` : ''}
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
                    ${__stockflowApp.icon('plus')}Thêm màu
                </button>
            </form>
        </details>`;
    }

function copyProductColor(form) {
        if (!__stockflowApp.hasRole('ADMIN') || !form) return;
        const productId = Number(__stockflowApp.$('#product-variants-dialog').dataset.productId);
        const product = __stockflowApp.state.products.get(productId);
        const targetVersion = Number(form.elements.version_id.value);
        const source = product?.variants.find(color => color.id === Number(form.elements.copy_source.value) &&
            color.version_id !== targetVersion && !color.archived &&
            product.versions.some(version => version.id === color.version_id && !version.archived));
        const notice = form.querySelector('[data-copy-color-notice]');
        if (!source) { notice.textContent = 'Hãy chọn một màu có sẵn để sao chép.'; return; }
        form.elements.color_name.value = source.color_name;
        form.elements.color_hex.value = __stockflowApp.colorHex(source.color_hex);
        form.elements.image_url.value = source.image_url || '';
        form.elements.image_urls.value = (source.image_urls || []).join('\n');
        __stockflowApp.validateProductGalleryInput(form.elements.image_urls);
        notice.textContent = 'Đã sao chép màu và bộ ảnh. SKU và giá vẫn giữ giá trị bạn đang nhập; hãy kiểm tra trước khi bấm Thêm màu.';
        form.elements.sku.focus();
    }

function confirmConfigurationArchive(kind, id) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const dialog = __stockflowApp.$('#product-variants-dialog');
        const product = __stockflowApp.state.products.get(Number(dialog.dataset.productId));
        const version = kind === 'version';
        const item = (version ? product?.versions : product?.variants)?.find((value) => value.id === id);
        if (!item) return;
        const name = version ? item.name : item.color_name + ' · ' + item.version_name;
        __stockflowApp.$('#confirm-title').textContent = version ? 'Xóa phiên bản?' : 'Xóa màu?';
        __stockflowApp.$('#confirm-message').textContent =
            'Xóa “' +
            name +
            '” khỏi các lựa chọn mua hàng? ' +
            (version ? 'Các màu thuộc phiên bản này sẽ được ẩn cùng. ' : '') +
            'SKU, tồn kho và đơn hàng đã có vẫn được lưu giữ. Bạn có thể khôi phục trong danh sách đã xóa.';
        __stockflowApp.$('#confirm-submit').textContent = version ? 'Xóa phiên bản' : 'Xóa màu';
        __stockflowApp.$('#confirm-submit').className = 'button danger';
        __stockflowApp.state.pendingMutation = {
            epoch: __stockflowApp.state.epoch,
            handler: async () => {
                await __stockflowApp.api('/products/' + product.id + '/' + (version ? 'versions' : 'variants') + '/' + id, {
                    method: 'DELETE',
                });
                __stockflowApp.notify(
                    'success',
                    'Đã xóa ' + (version ? 'phiên bản ' : 'màu ') + name + ' khỏi cửa hàng.',
                    'HTTP 200 OK',
                );
                await __stockflowApp.refreshConfiguration(
                    product.id,
                    Number(dialog.dataset.versionId),
                    version ? 'versions' : 'colors',
                );
            },
        };
        __stockflowApp.$('#confirm-dialog').showModal();
    }

async function restoreProductConfiguration(kind, id) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const dialog = __stockflowApp.$('#product-variants-dialog');
        const productId = Number(dialog.dataset.productId);
        const version = kind === 'version';
        await __stockflowApp.api(
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
        __stockflowApp.notify('success', version ? 'Đã khôi phục phiên bản.' : 'Đã khôi phục màu.', 'HTTP 200 OK');
        await __stockflowApp.refreshConfiguration(
            productId,
            version ? id : Number(dialog.dataset.versionId),
            version ? 'versions' : 'colors',
        );
    }

async function refreshConfiguration(productId, versionId, panel) {
        await __stockflowApp.loadProductOptions();
        await __stockflowApp.loadManage();
        await __stockflowApp.manageProductVariants(productId, versionId, panel);
    }

async function saveProductVersion(form, create = false) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const productId = Number(__stockflowApp.$('#product-variants-dialog').dataset.productId);
        const data = Object.fromEntries(new FormData(form));
        const body = { name: data.name.trim(), specifications: __stockflowApp.readSpecifications(form) };
        if (!create) {
            const common = new Map((__stockflowApp.state.products.get(productId)?.specifications || []).map(row => [row.name.toLowerCase(), row.value]));
            body.specifications = body.specifications.filter(row => !common.has(row.name.toLowerCase()) || common.get(row.name.toLowerCase()) !== row.value);
        }
        if (data.default_color_name) {
            body.default_color_name = data.default_color_name.trim();
            body.default_color_hex = data.default_color_hex;
        }
        const result = await __stockflowApp.api(
            '/products/' + productId + '/versions' + (create ? '' : '/' + form.dataset.versionId),
            {
                method: create ? 'POST' : 'PATCH',
                body,
            },
        );
        const selected = create ? result.versions.at(-1).id : Number(form.dataset.versionId);
        __stockflowApp.notify(
            'success',
            create ? 'Đã tạo phiên bản. Thêm màu và nhập hàng theo SKU để bán.' : 'Đã lưu phiên bản và thông số.',
            create ? 'HTTP 201 Created' : 'HTTP 200 OK',
        );
        await __stockflowApp.loadProductOptions();
        await __stockflowApp.loadManage();
        await __stockflowApp.manageProductVariants(productId, selected, create ? 'colors' : 'versions');
    }

async function saveProductVariant(form, create = false) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const dialog = __stockflowApp.$('#product-variants-dialog');
        const productId = Number(dialog.dataset.productId);
        const versionId = Number(dialog.dataset.versionId);
        const data = Object.fromEntries(new FormData(form));
        if (!__stockflowApp.validateProductGalleryInput(form.elements.image_urls))
            throw new Error(form.elements.image_urls.validationMessage);
        const body = {
            unit_price: data.unit_price ? Number(data.unit_price) : null,
            image_url: data.image_url.trim(),
            image_urls: __stockflowApp.readGalleryInput(form.elements.image_urls),
        };
        if (create) {
            body.sku = data.sku.trim();
            body.color_name = data.color_name.trim();
            body.color_hex = data.color_hex;
            body.version_id = Number(data.version_id);
        } else {
            body.status = data.status;
            body.color_name = data.color_name.trim();
            body.color_hex = data.color_hex;
        }
        await __stockflowApp.api('/products/' + productId + '/variants' + (create ? '' : '/' + form.dataset.variantId), {
            method: create ? 'POST' : 'PATCH',
            body,
        });
        __stockflowApp.notify(
            'success',
            create ? 'Đã thêm SKU mới. Nhập hàng theo SKU để bắt đầu bán.' : 'Đã lưu cấu hình màu.',
            create ? 'HTTP 201 Created' : 'HTTP 200 OK',
        );
        await __stockflowApp.loadProductOptions();
        await __stockflowApp.loadManage();
        await __stockflowApp.manageProductVariants(productId, versionId, 'colors');
    }

async function viewProductDetail(id) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const product = await __stockflowApp.api('/products/' + Number(id), { anonymous: true, channel: 'product-detail' });
        __stockflowApp.state.products.set(product.id, product);
        const dialog = __stockflowApp.$('#product-detail-dialog');
        dialog.dataset.productId = String(product.id);
        __stockflowApp.$('#product-detail-body').innerHTML = `<div class="catalog-detail-heading">
            ${__stockflowApp.catalogMedia(product, true)}
            <div class="catalog-detail-summary">
                <span class="catalog-sku">${__stockflowApp.escapeHtml(product.sku)}</span>
                <h3>${__stockflowApp.escapeHtml(product.name)}</h3>
                <p>${__stockflowApp.escapeHtml(product.category_name || 'Chưa phân loại')}</p>
                <strong class="catalog-detail-price">${__stockflowApp.amount(product.unit_price)}</strong>
                ${__stockflowApp.catalogStatus(product)}
            </div>
        </div>
        ${product.image_urls?.length ? '<section class="gallery-input-preview">' + __stockflowApp.galleryPreviewContent(product.image_urls.map(__stockflowApp.safeProductImageUrl).filter(Boolean)) + '</section>' : ''}
        ${__stockflowApp.productDescription(product)}
        ${__stockflowApp.productSpecifications(product)}
        <dl class="catalog-detail-facts">
            <div><dt>Mã sản phẩm</dt><dd>#${product.id}</dd></div>
            <div><dt>Ngày tạo</dt><dd>${__stockflowApp.dateTime(product.created_at)}</dd></div>
        </dl>
        <section class="catalog-detail-stock" aria-labelledby="detail-stock-title">
            <div><h3 id="detail-stock-title">Tồn kho theo chi nhánh</h3><p>Khả dụng, đang giữ và tổng số hàng thực tế.</p></div>
            <div id="detail-stock-container" aria-live="polite"><p class="catalog-detail-message">Đang tải tồn kho…</p></div>
        </section>`;
        __stockflowApp.$('#detail-edit-shortcut').onclick = () => {
            dialog.close();
            __stockflowApp.openProductEdit(product.id);
        };
        __stockflowApp.openDialog('product-detail-dialog');
        try {
            const stocks = await __stockflowApp.api('/inventories', {
                channel: 'detail-stock',
                query: { productId: product.id, page: 0, size: 100 },
            });
            // Đóng dialog hoặc mở sản phẩm khác thì response cũ không được ghi vào bản đang xem.
            if (!dialog.open || dialog.dataset.productId !== String(product.id)) return;
            __stockflowApp.$('#detail-stock-container').innerHTML = stocks.content.length
                ? `<div class="table-scroll"><table class="catalog-stock-table">
                    <thead><tr><th>Chi nhánh</th><th>Khả dụng</th><th>Đang giữ</th><th>Thực tế</th></tr></thead>
                    <tbody>${stocks.content
                        .map(
                            (stock) => `<tr>
                        <td>${__stockflowApp.escapeHtml(stock.warehouse_name)}</td>
                        <td class="catalog-stock-available">${__stockflowApp.integer(stock.available_quantity)}</td>
                        <td>${__stockflowApp.integer(stock.reserved_quantity)}</td>
                        <td>${__stockflowApp.integer(stock.physical_quantity)}</td>
                    </tr>`,
                        )
                        .join('')}</tbody>
                </table></div>`
                : '<p class="catalog-detail-message">Sản phẩm chưa được nhập kho. Nhập hàng để bắt đầu bán.</p>';
        } catch (error) {
            if (error.name === 'AbortError' || !dialog.open || dialog.dataset.productId !== String(product.id)) return;
            __stockflowApp.$('#detail-stock-container').innerHTML =
                '<p class="catalog-detail-message">Chưa tải được tồn kho. Vui lòng đóng và mở lại chi tiết.</p>';
            __stockflowApp.handleError(error);
        }
    }

function toggleProductStatus(id) {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const product = __stockflowApp.state.products.get(Number(id));
        if (!product) return;
        const active = product.status === 'ACTIVE';
        const nextStatus = active ? 'INACTIVE' : 'ACTIVE';
        __stockflowApp.$('#confirm-title').textContent = active ? 'Xóa sản phẩm khỏi cửa hàng?' : 'Khôi phục sản phẩm?';
        __stockflowApp.$('#confirm-message').textContent = active
            ? 'Sản phẩm “' +
              product.name +
              '” sẽ ngừng kinh doanh và được ẩn khỏi cửa hàng. Tồn kho, đơn hàng và lịch sử vẫn được giữ nguyên. Bạn có thể khôi phục sản phẩm trong danh sách đã ẩn.'
            : 'Sản phẩm “' + product.name + '” sẽ được hiển thị lại trên cửa hàng với giá và tồn kho hiện tại.';
        __stockflowApp.$('#confirm-submit').textContent = active ? 'Xóa khỏi cửa hàng' : 'Khôi phục sản phẩm';
        __stockflowApp.$('#confirm-submit').className = 'button ' + (active ? 'danger' : 'primary');
        __stockflowApp.state.pendingMutation = {
            epoch: __stockflowApp.state.epoch,
            handler: async () => {
                const updated = await __stockflowApp.api('/products/' + product.id, { method: 'PATCH', body: { status: nextStatus } });
                __stockflowApp.state.products.set(updated.id, updated);
                __stockflowApp.notify('success', (active ? 'Đã ẩn ' : 'Đã khôi phục ') + updated.name + '.', 'Cập nhật thành công');
                await __stockflowApp.loadProductOptions();
                await __stockflowApp.loadManage();
            },
        };
        __stockflowApp.openDialog('confirm-dialog');
    }

async function loadCategoryList() {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        __stockflowApp.$('#category-count').textContent = '—';
        // Đếm theo category_id cùng hậu duệ để tổng của nhóm cha khớp quy tắc lọc sản phẩm tại API.
        await Promise.all([__stockflowApp.loadCategories(), __stockflowApp.loadBrands(), __stockflowApp.loadProductOptions()]);
        __stockflowApp.state.categoryProductCounts.clear();
        for (const category of __stockflowApp.state.categories)
            __stockflowApp.state.categoryProductCounts.set(
                category.id,
                [...__stockflowApp.state.products.values()].filter(
                    (product) => !product.parent_product_id && __stockflowApp.categoryBelongsTo(product.category_id, category.id),
                ).length,
            );
        __stockflowApp.renderCategoryList();
    }

function renderCategoryList() {
        if (!__stockflowApp.hasRole('ADMIN')) return;
        const query = __stockflowApp.slugify(__stockflowApp.$('#category-admin-query').value.trim());
        const visible = __stockflowApp.orderedCategories().filter((category) =>
            __stockflowApp.slugify(category.id + ' ' + category.name + ' ' + category.slug + ' ' + __stockflowApp.categoryPath(category)).includes(
                query,
            ),
        );
        __stockflowApp.$('#category-count').textContent =
            __stockflowApp.integer(visible.length) + (query ? ' / ' + __stockflowApp.integer(__stockflowApp.state.categories.length) : '') + ' danh mục';
        __stockflowApp.$('#category-rows').innerHTML = visible
            .map(
                (category) => `<tr>
            <td><span class="catalog-category-id mono">#${category.id}</span></td>
            <td><div class="catalog-category-cell"><span>${__stockflowApp.icon('tag')}</span><div><strong>${__stockflowApp.escapeHtml(category.name)}</strong>
                ${category.parent_id ? '<small class="category-parent-path">' + __stockflowApp.escapeHtml(__stockflowApp.categoryPath(category)) + '</small>' : '<small class="category-parent-path">Danh mục cấp đầu</small>'}
            </div></div></td>
            <td><code class="slug-tag">${__stockflowApp.escapeHtml(category.slug)}</code></td>
            <td class="align-right"><span class="catalog-category-total">${__stockflowApp.integer(__stockflowApp.state.categoryProductCounts.get(category.id) || 0)}</span><span class="catalog-category-unit"> sản phẩm</span></td>
            <td><div class="catalog-row-actions">
                <button type="button" class="catalog-action" data-action="edit-category" data-id="${category.id}"
                    aria-label="Sửa danh mục ${__stockflowApp.escapeHtml(category.name)}">${__stockflowApp.icon('edit')}Sửa</button>
                <button type="button" class="catalog-action catalog-action-danger" data-action="delete-category" data-id="${category.id}"
                    aria-label="Xóa danh mục ${__stockflowApp.escapeHtml(category.name)}">${__stockflowApp.icon('trash')}Xóa</button>
            </div></td>
        </tr>`,
            )
            .join('');
        if (!visible.length) {
            __stockflowApp.$('#category-rows').innerHTML =
                '<tr><td colspan="5"><div class="catalog-empty"><h3>' +
                (__stockflowApp.state.categories.length ? 'Không tìm thấy danh mục' : 'Chưa có danh mục') +
                '</h3><p>' +
                (__stockflowApp.state.categories.length
                    ? 'Thử từ khóa khác hoặc xóa nội dung tìm kiếm.'
                    : 'Tạo danh mục đầu tiên để sắp xếp sản phẩm của cửa hàng.') +
                '</p></div></td></tr>';
        }
    }

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

export function register() {
Object.defineProperties(__stockflowApp, {
"loadingRows": { get: () => loadingRows },
"emptyRows": { get: () => emptyRows },
"renderPager": { get: () => renderPager },
"productCell": { get: () => productCell },
"loadInventory": { get: () => loadInventory },
"loadLedger": { get: () => loadLedger },
"stockIn": { get: () => stockIn },
"loadSummary": { get: () => loadSummary },
"reportQuery": { get: () => reportQuery },
"reportChartState": { get: () => reportChartState },
"renderRevenueChart": { get: () => renderRevenueChart },
"renderTopChart": { get: () => renderTopChart },
"loadRevenue": { get: () => loadRevenue },
"loadTop": { get: () => loadTop },
"loadLow": { get: () => loadLow },
"loadReports": { get: () => loadReports },
"catalogStatus": { get: () => catalogStatus },
"catalogMedia": { get: () => catalogMedia },
"catalogProductRow": { get: () => catalogProductRow },
"loadManage": { get: () => loadManage },
"loadAdminUsers": { get: () => loadAdminUsers },
"viewAdminUser": { get: () => viewAdminUser },
"updateAdminRoleFields": { get: () => updateAdminRoleFields },
"saveAdminUserRole": { get: () => saveAdminUserRole },
"toggleAdminUser": { get: () => toggleAdminUser },
"createProduct": { get: () => createProduct },
"updateProduct": { get: () => updateProduct },
"prepareProductImageUpdate": { get: () => prepareProductImageUpdate },
"readGalleryInput": { get: () => readGalleryInput },
"validateProductGalleryInput": { get: () => validateProductGalleryInput },
"galleryPreviewContent": { get: () => galleryPreviewContent },
"renderGalleryInputPreview": { get: () => renderGalleryInputPreview },
"validateProductImageInput": { get: () => validateProductImageInput },
"renderProductImagePreview": { get: () => renderProductImagePreview },
"resetProductImagePreviews": { get: () => resetProductImagePreviews },
"renderBrandLogoPreview": { get: () => renderBrandLogoPreview },
"openBrandLogoEdit": { get: () => openBrandLogoEdit },
"saveBrandLogo": { get: () => saveBrandLogo },
"createCategory": { get: () => createCategory },
"openCategoryEdit": { get: () => openCategoryEdit },
"updateCategory": { get: () => updateCategory },
"confirmCategoryDelete": { get: () => confirmCategoryDelete },
"createBrand": { get: () => createBrand },
"openProductCreate": { get: () => openProductCreate },
"openProductEdit": { get: () => openProductEdit },
"appendSpecRow": { get: () => appendSpecRow },
"renderSpecEditor": { get: () => renderSpecEditor },
"readSpecifications": { get: () => readSpecifications },
"setConfigurationPanel": { get: () => setConfigurationPanel },
"manageProductVariants": { get: () => manageProductVariants },
"renderVersionCreateForm": { get: () => renderVersionCreateForm },
"renderAdminColor": { get: () => renderAdminColor },
"renderColorCreateForm": { get: () => renderColorCreateForm },
"confirmConfigurationArchive": { get: () => confirmConfigurationArchive },
"restoreProductConfiguration": { get: () => restoreProductConfiguration },
"refreshConfiguration": { get: () => refreshConfiguration },
"saveProductVersion": { get: () => saveProductVersion },
"saveProductVariant": { get: () => saveProductVariant },
"copyProductColor": { get: () => copyProductColor },
"viewProductDetail": { get: () => viewProductDetail },
"toggleProductStatus": { get: () => toggleProductStatus },
"loadCategoryList": { get: () => loadCategoryList },
"renderCategoryList": { get: () => renderCategoryList },
"slugify": { get: () => slugify }
});
}

export function initializeFeature() {
document.addEventListener('input', (event) => {
        const input = event.target;
        if (input.id === 'brand-admin-query') {
            __stockflowApp.renderAdminBrands();
            return;
        }
        if (input.id === 'category-admin-query') {
            __stockflowApp.renderCategoryList();
            return;
        }
        if (input.dataset.brandLogoInput) {
            __stockflowApp.validateProductImageInput(input);
            window.clearTimeout(__stockflowApp.imagePreviewTimers.get(input));
            __stockflowApp.imagePreviewTimers.set(
                input,
                window.setTimeout(() => __stockflowApp.renderBrandLogoPreview(input), 300),
            );
            return;
        }
        if (input.dataset.galleryInput) {
            __stockflowApp.validateProductGalleryInput(input);
            window.clearTimeout(__stockflowApp.imagePreviewTimers.get(input));
            __stockflowApp.imagePreviewTimers.set(
                input,
                window.setTimeout(() => __stockflowApp.renderGalleryInputPreview(input), 300),
            );
            return;
        }
        if (input.matches('#product-variant-create textarea[name="image_urls"], .variant-admin-edit textarea[name="image_urls"]')) {
            // Browser kiểm tra customValidity trước submit; phải xóa lỗi cũ ngay khi sửa danh sách.
            __stockflowApp.validateProductGalleryInput(input);
            return;
        }
        if (!input.dataset.imageInput) return;
        __stockflowApp.validateProductImageInput(input);
        window.clearTimeout(__stockflowApp.imagePreviewTimers.get(input));
        __stockflowApp.imagePreviewTimers.set(
            input,
            window.setTimeout(() => __stockflowApp.renderProductImagePreview(input), 300),
        );
    });
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
        __stockflowApp.setConfigurationPanel(panel);
        __stockflowApp.$('.configuration-tabs [data-panel="' + panel + '"]').focus();
    });
document.addEventListener('input', (event) => {
        const input = event.target;
        if (input.id === 'create-category-name') {
            const slugInput = __stockflowApp.$('#create-category-slug');
            if (slugInput && !slugInput.dataset.touched) {
                slugInput.value = __stockflowApp.slugify(input.value);
            }
        } else if (input.id === 'create-category-slug') {
            input.dataset.touched = 'true';
        }
    });
document.addEventListener('reset', (event) => {
        if (event.target.id === 'category-create') {
            delete __stockflowApp.$('#create-category-slug').dataset.touched;
        }
    });
document.addEventListener('input', (event) => {
        if (event.target.id === 'create-brand-slug') event.target.dataset.touched = 'true';
        if (event.target.id === 'create-brand-name' && !__stockflowApp.$('#create-brand-slug').dataset.touched) {
            __stockflowApp.$('#create-brand-slug').value = __stockflowApp.slugify(event.target.value);
        }
    });
document.addEventListener('reset', (event) => {
        if (event.target.id === 'brand-create') delete __stockflowApp.$('#create-brand-slug').dataset.touched;
    });
}
