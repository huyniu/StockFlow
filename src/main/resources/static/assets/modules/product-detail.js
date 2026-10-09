import { app as __stockflowApp } from './context.js';
import { rememberProduct } from './recently-viewed.js';

function renderProducts(products) {
        const cards = __stockflowApp.productCards(products);
        __stockflowApp.$('#catalog-grid').innerHTML = cards
            ? cards
            : '<div class="grid-message">Chưa có sản phẩm phù hợp. Thử đổi danh mục, từ khóa hoặc khoảng giá.' +
              '<br /><button class="button secondary small" type="button" data-action="clear-catalog-filter" data-filter="all">Xóa bộ lọc</button></div>';
        __stockflowApp.prepareStorefrontReveals();
    }

function productDescription(product) {
        const description = String(product.description ?? '').trim();
        return `<section id="product-description" class="product-description">
            <h3>Mô tả sản phẩm</h3>
            <p class="product-description-text ${description ? '' : 'subtle'}">${__stockflowApp.escapeHtml(description || 'Cửa hàng chưa bổ sung mô tả cho sản phẩm này.')}</p>
        </section>`;
    }

function productSpecifications(product) {
        const rows = Array.isArray(product.specifications) ? product.specifications : [];
        return `<section id="product-specifications" class="product-specifications">
            <div class="specifications-heading"><h3>Thông số kỹ thuật</h3></div>
            ${rows.length ? `<dl class="specifications-table">${rows.map((row) => `<div><dt>${__stockflowApp.escapeHtml(row.name)}</dt><dd>${__stockflowApp.escapeHtml(row.value)}</dd></div>`).join('')}</dl>` : '<p class="subtle">Cửa hàng chưa bổ sung thông số kỹ thuật.</p>'}
        </section>`;
    }

function colorHex(value) {
        return /^#[0-9a-fA-F]{6}$/.test(value || '') ? value : '#94a3b8';
    }

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
        return palette.find(([pattern]) => pattern.test(name))?.[1] || __stockflowApp.colorHex(null);
    }

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

function saleSku(product) {
        const own = product?.variants?.find((variant) => variant.sku_product_id === product.id);
        return own ? __stockflowApp.colorSku(product, own) : product;
    }

function versionLabel(product) {
        return product.version_name === 'Phiên bản hiện tại' ? '' : product.version_name || '';
    }

function cartProductName(product) {
        return [product.name, __stockflowApp.versionLabel(product), product.color_name].filter(Boolean).join(' · ');
    }

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
                        `<span class="color-dot" style="--color: ${__stockflowApp.colorHex(color.color_hex)}" title="${__stockflowApp.escapeHtml(color.color_name)}"></span>`,
                )
                .join('')}
            <span>${versionCount} phiên bản · ${colors.length} màu sắc</span>
        </div>`;
    }

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
            ? __stockflowApp.colorSku(root, selected)
            : root.variants?.length
              ? { ...root, status: 'INACTIVE' }
              : root;
        __stockflowApp.state.detailRoot = root;
        __stockflowApp.state.detailSku = product;
        __stockflowApp.state.products.set(root.id, root);
        variants.forEach((variant) => {
            if (variant.sku_product_id !== root.id) __stockflowApp.state.products.set(variant.sku_product_id, __stockflowApp.colorSku(root, variant));
        });
        const stock = __stockflowApp.productStock(product);
        const active = product.status === 'ACTIVE';
        const body = __stockflowApp.$('#shop-product-detail-body');
        body.dataset.productId = String(__stockflowApp.state.productId);
        body.dataset.skuId = String(product.id);
        body.dataset.versionId = String(selected?.version_id || '');
        __stockflowApp.renderProductBreadcrumb(root);
        const title = [root.name, __stockflowApp.versionLabel(product)].filter(Boolean).join(' · ');
        document.title = title + ' | StockFlow Tech';
        body.innerHTML = `
            <header class="product-detail-heading">
                <h1 id="shop-product-name" tabindex="-1">${__stockflowApp.escapeHtml(title)}</h1>
                <div class="wishlist-detail-action">${__stockflowApp.wishlistButton(root.id, root.name)}<span>Lưu sản phẩm yêu thích</span></div>
                <div class="product-detail-meta">
                    ${root.brand_name ? '<span>Thương hiệu <strong>' + __stockflowApp.escapeHtml(root.brand_name) + '</strong></span>' : ''}
                    <span class="shop-product-sku">Mã sản phẩm <span class="mono">${__stockflowApp.escapeHtml(product.sku)}</span></span>
                    <button type="button" data-action="scroll-product-info" data-target="product-specifications">Thông số kỹ thuật</button>
                </div>
            </header>
            <div class="shop-product-layout">
                <div class="product-visual-column">
                    ${__stockflowApp.renderShopProductGallery(product)}
                    <div class="product-service-strip">
                        <span>${__stockflowApp.icon('box')}Chọn đúng phiên bản và màu</span>
                        <span>${__stockflowApp.icon('warehouse')}Phục vụ tại nhiều chi nhánh</span>
                        <span>${__stockflowApp.icon('truck')}Theo dõi tiến trình giao hàng</span>
                    </div>
                </div>
                <div class="shop-product-summary">
                    <div class="product-price-panel">
                    <span class="shop-product-price-label">Giá bán${selected ? ' · ' + __stockflowApp.escapeHtml(selected.color_name) : ''}</span>
                    <strong class="shop-product-price">${__stockflowApp.amount(product.unit_price)}</strong>
                    <span class="price-selection-note">${__stockflowApp.escapeHtml(version?.name || 'Sản phẩm đang chọn')}</span>
                    </div>
                    ${
                        versions.length
                            ? `
                        <fieldset class="version-picker">
                            <legend>Phiên bản <strong>${__stockflowApp.escapeHtml(version?.name || '')}</strong></legend>
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
                                        <strong>${__stockflowApp.escapeHtml(value.name)}</strong>
                                        <small>${minimum == null ? 'Chưa mở bán' : 'Từ ' + __stockflowApp.amount(minimum)}</small>
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
                            <legend>Màu sắc <strong>${__stockflowApp.escapeHtml(selected.color_name)}</strong></legend>
                            <div class="color-options" role="radiogroup" aria-label="Chọn màu sắc">
                                ${colors
                                    .map(
                                        (variant) => `
                                    <button type="button" class="color-option ${variant.sku_product_id === product.id ? 'is-selected' : ''}"
                                        data-action="select-product-color" data-id="${variant.sku_product_id}"
                                        role="radio" aria-checked="${variant.sku_product_id === product.id}" ${variant.status !== 'ACTIVE' ? 'disabled' : ''}>
                                        ${__stockflowApp.colorOptionThumbnail(variant)}
                                        <span class="color-option-copy">
                                            <strong class="color-option-label">
                                                <span class="color-swatch-dot"
                                                    style="--swatch-color: ${__stockflowApp.swatchColor(variant)}"
                                                    aria-hidden="true"></span>
                                                ${__stockflowApp.escapeHtml(variant.color_name)}
                                            </strong>
                                            <small>${variant.status === 'ACTIVE' ? __stockflowApp.amount(variant.unit_price) : 'Đã ngừng bán'}</small>
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
                            <label>Số lượng<input name="quantity" id="shop-product-quantity" type="number" value="${quantity}" min="1" max="${__stockflowApp.MAX_QUANTITY}" step="1" required /></label>
                            <button class="button primary" type="submit" ${!active || __stockflowApp.isOperator() || stock.tone === 'empty' ? 'disabled' : ''}>${__stockflowApp.icon('plus')}Thêm vào giỏ</button>
                        </div>
                        <p class="subtle">${__stockflowApp.isOperator() ? 'Dùng tài khoản khách hàng để mua sắm.' : 'Hàng được giữ trong 15 phút sau khi tạo đơn. Tình trạng hàng có thể thay đổi trước khi đặt.'}</p>
                    </form>
                    <details class="product-branch-panel" open>
                        <summary>${__stockflowApp.icon('warehouse')}Xem chi nhánh có hàng</summary>
                        <div id="product-branch-availability" class="product-branch-list"></div>
                        <button type="button" class="branch-stock-refresh" data-action="refresh-product-availability">Kiểm tra lại tình trạng hàng</button>
                        <p id="product-availability-error" class="availability-error" hidden>
                            Chưa kiểm tra được tình trạng hàng.
                            Vui lòng nhấn kiểm tra lại.
                        </p>
                    </details>
                </div>
            </div>
            <div class="product-information-grid">${__stockflowApp.productDescription(root)}${__stockflowApp.productSpecifications(product)}</div>
            <section class="product-reviews" aria-labelledby="product-reviews-title">
                <h2 id="product-reviews-title">Đánh giá từ khách đã mua</h2>
                <div id="product-reviews-content" aria-live="polite">Đang tải đánh giá…</div>
            </section>
        `;
        void __stockflowApp.loadProductReviews(root.id);
        __stockflowApp.renderWarehouses();
        __stockflowApp.refreshProductStock();
        __stockflowApp.observeMobilePurchase();
        __stockflowApp.prepareStorefrontReveals();
    }

function colorOptionThumbnail(variant) {
        const image =
            __stockflowApp.safeProductImageUrl(variant.image_url) || (variant.image_urls || []).map(__stockflowApp.safeProductImageUrl).find(Boolean);
        return `<span class="color-option-photo">
            <span class="color-dot" style="--color: ${__stockflowApp.colorHex(variant.color_hex)}" aria-hidden="true"></span>
            ${image ? `<img src="${__stockflowApp.escapeHtml(image)}" alt="${__stockflowApp.escapeHtml(variant.color_name)}" loading="lazy" data-color-thumbnail />` : ''}
        </span>`;
    }

function selectProductSku(skuId) {
        if (!__stockflowApp.state.detailRoot || !__stockflowApp.isCurrentProductPage(__stockflowApp.state.productId)) return;
        const variant = __stockflowApp.state.detailRoot.variants.find((value) => value.sku_product_id === skuId);
        if (!variant || variant.status !== 'ACTIVE') return;
        const quantity = Number(__stockflowApp.$('#shop-product-quantity')?.value) || 1;
        if (__stockflowApp.state.productId !== skuId) {
            __stockflowApp.state.productId = skuId;
            __stockflowApp.pushPage();
        }
        __stockflowApp.renderShopProductDetail(__stockflowApp.state.detailRoot, skuId, quantity);
    }

function selectProductVersion(versionId) {
        if (!__stockflowApp.state.detailRoot || !__stockflowApp.isCurrentProductPage(__stockflowApp.state.productId)) return;
        const choices = __stockflowApp.state.detailRoot.variants.filter(
            (value) => value.version_id === versionId && value.status === 'ACTIVE',
        );
        const selected = choices.find((value) => value.color_name === __stockflowApp.state.detailSku?.color_name) || choices[0];
        if (!selected) return;
        __stockflowApp.selectProductSku(selected.sku_product_id);
        __stockflowApp.$('[data-action="select-product-version"][data-id="' + versionId + '"]')?.focus({ preventScroll: true });
    }

function productGallery(product) {
        const fallback = __stockflowApp.productImage(product).fallback;
        const cover = __stockflowApp.safeProductImageUrl(product.image_url);
        const additional = Array.isArray(product.image_urls) ? product.image_urls.map(__stockflowApp.safeProductImageUrl) : [];
        const sources = [...new Set([cover, ...additional].filter(Boolean))];
        if (!sources.length) return [{ ...__stockflowApp.productImage(product), label: 'Ảnh minh họa' }];
        return sources.map((src, index) => ({
            src,
            fallback,
            custom: true,
            label: src === cover ? 'Ảnh bìa' : 'Ảnh sản phẩm ' + (index + 1),
        }));
    }

function renderShopProductGallery(product) {
        const photos = __stockflowApp.productGallery(product);
        const photo = photos[0];
        return `
            <section class="shop-product-gallery" data-selected-index="0" aria-label="Bộ ảnh sản phẩm">
                <div class="product-card-img-wrap shop-product-image">
                    <img
                        id="shop-product-main-image"
                        class="product-card-img"
                        src="${__stockflowApp.escapeHtml(photo.src)}"
                        alt="${__stockflowApp.escapeHtml(product.name)} — ${__stockflowApp.escapeHtml(photo.label)}"
                        decoding="async"
                        data-image-fallback="${__stockflowApp.escapeHtml(photo.fallback)}"
                    />
                    <button class="product-image-zoom-trigger" type="button" data-open-product-image aria-label="Mở ảnh sản phẩm và phóng to"><span>⤢ Xem ảnh lớn</span></button>
                    <span class="product-photo-caption">${__stockflowApp.escapeHtml(photo.label)}</span>
                    <span class="product-image-error" hidden>Chưa tải được ảnh. Vui lòng kiểm tra kết nối.</span>
                    <span id="shop-product-image-count" class="gallery-counter" aria-live="polite">1 / ${photos.length}</span>
                    ${
                        photos.length > 1
                            ? `
                        <button class="gallery-arrow gallery-arrow-prev" type="button" data-action="gallery-step" data-direction="-1" aria-label="Xem ảnh trước">
                            ${__stockflowApp.icon('arrow')}
                        </button>
                        <button class="gallery-arrow" type="button" data-action="gallery-step" data-direction="1" aria-label="Xem ảnh sau">
                            ${__stockflowApp.icon('arrow')}
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
                                aria-label="Xem ${__stockflowApp.escapeHtml(entry.label)} của ${__stockflowApp.escapeHtml(product.name)}"
                            >
                                <img src="${__stockflowApp.escapeHtml(entry.src)}" alt="" loading="lazy" decoding="async" data-gallery-thumbnail />
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

function selectGalleryImage(index) {
        if (!__stockflowApp.isCurrentProductPage(__stockflowApp.state.productId) || !Number.isInteger(index)) return;
        const product = __stockflowApp.state.detailSku;
        const gallery = __stockflowApp.$('.shop-product-gallery');
        if (!product || !gallery) return;
        const photos = __stockflowApp.productGallery(product);
        const selected = ((index % photos.length) + photos.length) % photos.length;
        const previous = Number(gallery.dataset.selectedIndex);
        if (selected === previous) return;
        const photo = photos[selected];
        const wrapper = __stockflowApp.$('.shop-product-image', gallery);
        const image = document.createElement('img');
        image.id = 'shop-product-main-image';
        image.className = 'product-card-img';
        image.alt = product.name + ' — ' + photo.label;
        image.decoding = 'async';
        image.dataset.imageFallback = photo.fallback;
        image.style.setProperty('--gallery-shift', index > previous ? '14px' : '-14px');
        image.src = photo.src;
        // Thay phần tử ảnh để lỗi tải của ảnh trước không đánh dấu nhầm ảnh vừa chọn.
        __stockflowApp.$('#shop-product-main-image').replaceWith(image);
        wrapper.classList.remove('product-image-unavailable');
        __stockflowApp.$('.product-image-error', wrapper).hidden = true;
        __stockflowApp.$('.product-photo-caption', wrapper).textContent = photo.label;
        gallery.dataset.selectedIndex = String(selected);
        __stockflowApp.$('#shop-product-image-count').textContent = selected + 1 + ' / ' + photos.length;
        __stockflowApp.$$('.gallery-thumbnail', gallery).forEach((button) => {
            const active = Number(button.dataset.photoIndex) === selected;
            button.classList.toggle('active', active);
            button.setAttribute('aria-pressed', String(active));
            if (active) button.scrollIntoView({ block: 'nearest', inline: 'nearest' });
        });
    }

function productPagePath(id) {
        return '/san-pham/' + id;
    }

function isCurrentProductPage(id) {
        return __stockflowApp.state.view === 'shop' && __stockflowApp.state.shopTab === 'product' && __stockflowApp.state.productId === id;
    }

async function loadShopProductDetail() {
        const id = __stockflowApp.state.productId;
        const body = __stockflowApp.$('#shop-product-detail-body');
        delete body.dataset.productId;
        __stockflowApp.state.detailRoot = null;
        __stockflowApp.state.detailSku = null;
        __stockflowApp.state.detailAvailability = null;
        __stockflowApp.observeMobilePurchase();
        __stockflowApp.$('#product-breadcrumb-category').textContent = 'Sản phẩm';
        __stockflowApp.$('#product-breadcrumb-name').textContent = 'Chi tiết sản phẩm';
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
            let product = await __stockflowApp.api('/products/' + id, { anonymous: true, channel: 'shop-product-detail' });
            const selectedId = product.parent_product_id ? id : null;
            if (product.parent_product_id) {
                product = await __stockflowApp.api('/products/' + product.parent_product_id, {
                    anonymous: true,
                    channel: 'shop-product-detail',
                });
            }
            if (!__stockflowApp.isCurrentProductPage(id)) return;
            __stockflowApp.renderShopProductDetail(product, selectedId);
            rememberProduct(product);
            __stockflowApp.renderCart();
            __stockflowApp.$('#shop-product-name').focus({ preventScroll: true });
            await __stockflowApp.loadProductAvailability();
        } catch (error) {
            if (error.name === 'AbortError' || !__stockflowApp.isCurrentProductPage(id)) return;
            const title = error.status === 404 ? 'Không tìm thấy sản phẩm' : 'Chưa tải được sản phẩm';
            document.title = title + ' | StockFlow Tech';
            __stockflowApp.$('#product-breadcrumb-name').textContent = title;
            body.innerHTML = `
                <section class="shop-product-message product-page-error" role="alert">
                    ${__stockflowApp.icon('alert')}
                    <h1>${title}</h1>
                    <p>${__stockflowApp.escapeHtml(error.message)}</p>
                    <a class="button primary" href="/#shop" data-shop-link>Quay về cửa hàng</a>
                    ${error.status !== 404 && Number.isSafeInteger(id) && id > 0 ? '<button class="button secondary" type="button" data-action="refresh">Thử lại</button>' : ''}
                </section>
            `;
            __stockflowApp.handleError(error);
        }
    }

function addProductFromDetail(form) {
        if (__stockflowApp.isOperator()) throw new Error('Dùng tài khoản khách hàng để mua sắm.');
        const id = Number(__stockflowApp.$('#shop-product-detail-body').dataset.productId);
        if (!__stockflowApp.isCurrentProductPage(id)) throw new Error('Vui lòng tải lại thông tin sản phẩm trước khi thêm giỏ.');
        const product = __stockflowApp.state.detailSku;
        if (!product || product.status !== 'ACTIVE') throw new Error('Sản phẩm đã ngừng kinh doanh.');
        if (__stockflowApp.productStock(product).tone === 'empty') {
            throw new Error('Lựa chọn này tạm hết hàng tại chi nhánh. Vui lòng chọn màu hoặc chi nhánh khác.');
        }
        __stockflowApp.addCart(product.id, Number(form.elements.quantity.value));
        __stockflowApp.openDialog('cart-dialog');
    }

function renderCatalogLoading() {
        __stockflowApp.$('#catalog-grid').innerHTML =
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

let catalogRequest;

function loadCatalog() {
        const key = JSON.stringify([__stockflowApp.state.epoch, __stockflowApp.state.pages.catalog,
            __stockflowApp.state.catalogSort, __stockflowApp.$('#catalog-category').value,
            __stockflowApp.state.catalogBrandId, __stockflowApp.$('#catalog-query').value.trim(),
            __stockflowApp.state.catalogMinPrice, __stockflowApp.state.catalogMaxPrice,
            __stockflowApp.state.catalogSpecName, __stockflowApp.state.catalogSpecValue]);
        if (catalogRequest?.key === key) return catalogRequest.promise;
        const request = { key };
        catalogRequest = request;
        request.promise = loadCatalogPage().finally(() => {
            if (catalogRequest === request) catalogRequest = null;
        });
        return request.promise;
    }

async function loadCatalogPage() {
        let timeout, timedOut = false;
        __stockflowApp.$('#catalog-grid').dataset.catalogStatus = 'loading';
        __stockflowApp.renderCatalogLoading();
        __stockflowApp.$('#catalog-pagination').replaceChildren();
        __stockflowApp.$('#catalog-count').textContent = '—';
        __stockflowApp.renderCategoryChips();
        __stockflowApp.renderCatalogActiveFilters();
        __stockflowApp.syncQuickPriceChips();
        try {
            const response = __stockflowApp.api('/products', {
                anonymous: true,
                channel: 'catalog',
                query: {
                    page: __stockflowApp.state.pages.catalog,
                    size: 8,
                    sort: __stockflowApp.state.catalogSort,
                    status: 'ACTIVE',
                    grouped: true,
                    categoryId: __stockflowApp.$('#catalog-category').value,
                    brandId: __stockflowApp.state.catalogBrandId,
                    q: __stockflowApp.$('#catalog-query').value.trim(),
                    minPrice: __stockflowApp.state.catalogMinPrice,
                    maxPrice: __stockflowApp.state.catalogMaxPrice,
                    specificationName: __stockflowApp.state.catalogSpecName,
                    specificationValue: __stockflowApp.state.catalogSpecValue,
                },
            });
            const controller = __stockflowApp.channels.get('catalog');
            timeout = window.setTimeout(() => { timedOut = true; controller?.abort(); }, __stockflowApp.READ_TIMEOUT_MS);
            const result = await response;
            result.content.forEach((product) => __stockflowApp.state.products.set(product.id, product));
            __stockflowApp.renderProducts(result.content);
            __stockflowApp.renderHeroShowcase(result.content);
            __stockflowApp.$('#catalog-grid').dataset.catalogStatus = 'ready';
            __stockflowApp.$('#catalog-count').textContent =
                'Hiển thị ' + __stockflowApp.integer(result.content.length) + ' / ' + __stockflowApp.integer(result.total_elements) + ' sản phẩm';
            __stockflowApp.renderPager('catalog', result);
            __stockflowApp.renderCart();
            if (__stockflowApp.state.view === 'shop' && __stockflowApp.state.shopTab === 'catalog' &&
                window.location.hash !== '#product-shelf') __stockflowApp.replaceHash();
        } catch (error) {
            if (timedOut) error = new __stockflowApp.ApiError(0, 'TIMEOUT',
                { message: 'Tải sản phẩm quá lâu. Vui lòng thử lại.' }, '/api/v1/products');
            if (error.name !== 'AbortError') {
                __stockflowApp.$('#catalog-grid').dataset.catalogStatus = 'error';
                __stockflowApp.renderHeroShowcase([], { failed: true });
                __stockflowApp.$('#catalog-grid').innerHTML =
                    '<div class="grid-message"><p role="alert">Chưa tải được sản phẩm. Vui lòng thử lại.</p>' +
                    '<button class="button secondary" type="button" data-action="retry-catalog">Thử lại</button></div>';
                __stockflowApp.$('#catalog-count').textContent = 'Chưa tải được sản phẩm';
            }
            throw error;
        } finally {
            window.clearTimeout(timeout);
        }
    }

function selectedCartItems() {
        return [...__stockflowApp.state.cart.values()].filter(item => item.selected !== false);
    }

function consumePurchasedCartItems(items) {
        for (const purchased of items) {
            const current = __stockflowApp.state.cart.get(purchased.product_id);
            if (!current) continue;
            if (current.quantity > purchased.quantity) current.quantity -= purchased.quantity;
            else __stockflowApp.state.cart.delete(purchased.product_id);
        }
    }

export function register() {
Object.defineProperties(__stockflowApp, {
"renderProducts": { get: () => renderProducts },
"productDescription": { get: () => productDescription },
"productSpecifications": { get: () => productSpecifications },
"colorHex": { get: () => colorHex },
"swatchColor": { get: () => swatchColor },
"colorSku": { get: () => colorSku },
"saleSku": { get: () => saleSku },
"versionLabel": { get: () => versionLabel },
"cartProductName": { get: () => cartProductName },
"configurationPreview": { get: () => configurationPreview },
"renderShopProductDetail": { get: () => renderShopProductDetail },
"colorOptionThumbnail": { get: () => colorOptionThumbnail },
"selectProductSku": { get: () => selectProductSku },
"selectProductVersion": { get: () => selectProductVersion },
"productGallery": { get: () => productGallery },
"renderShopProductGallery": { get: () => renderShopProductGallery },
"selectGalleryImage": { get: () => selectGalleryImage },
"productPagePath": { get: () => productPagePath },
"isCurrentProductPage": { get: () => isCurrentProductPage },
"loadShopProductDetail": { get: () => loadShopProductDetail },
"addProductFromDetail": { get: () => addProductFromDetail },
"renderCatalogLoading": { get: () => renderCatalogLoading },
"loadCatalog": { get: () => loadCatalog },
"selectedCartItems": { get: () => selectedCartItems },
"consumePurchasedCartItems": { get: () => consumePurchasedCartItems }
});
}

export function initializeFeature() {
document.addEventListener(
        'load',
        (event) => {
            const image = event.target;
            if (!(image instanceof HTMLImageElement) || image !== __stockflowApp.$('#shop-product-main-image')) return;
            if (!image.isConnected || image.hidden || __stockflowApp.reducedStorefrontMotion.matches) return;
            image.classList.add('gallery-image-enter');
        },
        true,
    );
document.addEventListener('keydown', (event) => {
        if (event.altKey || event.ctrlKey || event.metaKey || !event.target.closest('.shop-product-gallery')) return;
        if (!['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
        event.preventDefault();
        const gallery = __stockflowApp.$('.shop-product-gallery');
        __stockflowApp.selectGalleryImage(Number(gallery.dataset.selectedIndex) + (event.key === 'ArrowLeft' ? -1 : 1));
    });
}
