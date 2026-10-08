import { app as __stockflowApp } from './context.js';

let QUICK_PRICE_RANGES, PRICE_SLIDER_STEP, PRICE_SLIDER_DEFAULT_MAX, MAX_CATALOG_PRICE, priceSliderDrag, priceSlider;

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
        const categories = [...__stockflowApp.state.categories].sort(
            (left, right) => Boolean(left.parent_id) - Boolean(right.parent_id),
        );
        __stockflowApp.$('#discovery-categories').innerHTML = groups
            .map((group) => {
                const category = categories.find(
                    (item) =>
                        !used.has(item.id) &&
                        group.keys.some((key) => {
                            const name = __stockflowApp.normalizeProductName(item.name);
                            return name === key || name.startsWith(key + ' ');
                        }),
                );
                if (!category) return '';
                used.add(category.id);
                return `<a class="discovery-category" href="/?categoryId=${category.id}#shop/catalog" data-catalog-link>
                <span class="discovery-category-art"><svg viewBox="0 0 24 24" aria-hidden="true">${symbols[group.symbol]}</svg></span>
                <span><strong>${__stockflowApp.escapeHtml(category.name)}</strong><small>${group.description}</small></span>
                ${__stockflowApp.icon('arrow')}
            </a>`;
            })
            .join('');
        __stockflowApp.$('.category-discovery').hidden = !used.size;
    }

async function loadBestsellers() {
        const grid = __stockflowApp.$('#bestseller-grid');
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
            const products = await __stockflowApp.api('/storefront/bestsellers', {
                query: { limit: 4 },
                anonymous: true,
                channel: 'storefront-bestsellers',
            });
            if (!Array.isArray(products)) throw new Error('Dữ liệu bán chạy chưa sẵn sàng.');
            products.forEach((product) => __stockflowApp.state.products.set(product.id, product));
            grid.innerHTML =
                __stockflowApp.productCards(products, { bestseller: true }) ||
                '<div class="discovery-empty"><strong>Những lựa chọn yêu thích sẽ xuất hiện ở đây.</strong>' +
                    '<p>Khi cửa hàng có đơn đã thanh toán, sản phẩm bán chạy được cập nhật từ doanh số thực tế.</p></div>';
            grid.setAttribute('aria-busy', 'false');
            __stockflowApp.state.discoveryDirty = false;
            __stockflowApp.prepareStorefrontReveals();
        } catch (error) {
            if (error.name === 'AbortError') return;
            grid.setAttribute('aria-busy', 'false');
            grid.innerHTML =
                '<div class="discovery-empty"><strong>Chưa tải được sản phẩm bán chạy.</strong>' +
                '<p>Bạn vẫn có thể duyệt và đặt sản phẩm trên kệ bên dưới.</p>' +
                '<button class="button secondary small" type="button" data-action="refresh-bestsellers">Thử lại</button></div>';
        }
    }

async function loadBrands() {
        __stockflowApp.state.brands = await __stockflowApp.api('/brands', { anonymous: true, channel: 'brands' });
        __stockflowApp.renderProductBrands();
        __stockflowApp.renderCatalogBrandOptions();
        __stockflowApp.renderShopCategoryMenuDetail();
        __stockflowApp.renderAdminBrands();
    }

function renderProductBrands() {
        const options = __stockflowApp.state.brands
            .map((brand) => '<option value="' + brand.id + '">' + __stockflowApp.escapeHtml(brand.name) + '</option>')
            .join('');
        ['product-brand', 'update-product-brand'].forEach((id) => {
            const select = __stockflowApp.$('#' + id);
            const previous = select.value;
            select.innerHTML = '<option value="">Chưa khai báo thương hiệu</option>' + options;
            select.value = previous;
        });
    }

function renderCatalogBrandOptions() {
        const categoryId = __stockflowApp.$('#catalog-category').value;
        const brands = __stockflowApp.state.brands.filter((brand) => !categoryId || brand.category_ids.includes(Number(categoryId)));
        if (!brands.some((brand) => String(brand.id) === __stockflowApp.state.catalogBrandId)) __stockflowApp.state.catalogBrandId = '';
        __stockflowApp.$('#catalog-brand').innerHTML =
            '<option value="">Tất cả thương hiệu</option>' +
            brands.map((brand) => '<option value="' + brand.id + '">' + __stockflowApp.escapeHtml(brand.name) + '</option>').join('');
        __stockflowApp.$('#catalog-brand').value = __stockflowApp.state.catalogBrandId;
        __stockflowApp.renderCatalogBrandChips(brands);
    }

function renderCatalogBrandChips(brands) {
        const container = __stockflowApp.$('#catalog-brand-chips');
        const signature = JSON.stringify(brands.map((brand) => [brand.id, brand.name]));
        if (container.dataset.brands !== signature) {
            const choices = [{ id: '', name: 'Tất cả' }, ...brands];
            container.innerHTML = choices
                .map(
                    (
                        brand,
                    ) => `<button type="button" data-action="quick-brand" data-brand-chip="${__stockflowApp.escapeHtml(brand.id)}"
                        aria-pressed="false">${__stockflowApp.escapeHtml(brand.name)}</button>`,
                )
                .join('');
            container.dataset.brands = signature;
        }
        __stockflowApp.$$('[data-brand-chip]', container).forEach((button) => {
            const selected = button.dataset.brandChip === __stockflowApp.state.catalogBrandId;
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

async function applyCatalogBrand(id) {
        if (![...__stockflowApp.$('#catalog-brand').options].some((option) => option.value === id)) return;
        __stockflowApp.state.catalogBrandId = id;
        __stockflowApp.renderCatalogBrandOptions();
        await __stockflowApp.reloadCatalogFilters();
    }

function brandLogoMarkup(brand) {
        const source = __stockflowApp.safeProductImageUrl(brand.logo_url);
        const initials = String(brand.name).trim().slice(0, 2).toLocaleUpperCase('vi-VN');
        return `<span class="brand-logo-media ${source ? '' : 'without-logo'}" aria-hidden="true">
            ${source ? `<img src="${__stockflowApp.escapeHtml(source)}" alt="" loading="lazy" decoding="async" data-brand-logo />` : ''}
            <span class="brand-monogram" ${source ? 'hidden' : ''}>${__stockflowApp.escapeHtml(initials)}</span>
        </span>`;
    }

function renderAdminBrands() {
        const query = __stockflowApp.slugify(__stockflowApp.$('#brand-admin-query').value.trim());
        const visible = __stockflowApp.state.brands.filter((brand) => __stockflowApp.slugify(brand.name + ' ' + brand.slug).includes(query));
        __stockflowApp.$('#brand-count').textContent = __stockflowApp.integer(visible.length) + ' / ' + __stockflowApp.integer(__stockflowApp.state.brands.length) + ' thương hiệu';
        __stockflowApp.$('#brand-admin-list').innerHTML = visible.length
            ? visible
                  .map((brand) => {
                      const names = __stockflowApp.rootCategories()
                          .filter((category) => brand.category_ids.includes(category.id))
                          .map((category) => category.name);
                      return `<article class="brand-admin-item" data-admin-brand="${brand.id}">
                    ${__stockflowApp.brandLogoMarkup(brand)}
                    <strong>${__stockflowApp.escapeHtml(brand.name)}</strong>
                    <span class="brand-admin-slug">${__stockflowApp.escapeHtml(brand.slug)}</span>
                    <span class="brand-admin-groups">${__stockflowApp.escapeHtml(names.join(' · ') || 'Chưa có danh mục gợi ý')}</span>
                    ${
                        __stockflowApp.hasRole('ADMIN')
                            ? `<button class="button secondary" type="button" data-action="edit-brand-logo"
                        data-id="${brand.id}" aria-label="Sửa logo của ${__stockflowApp.escapeHtml(brand.name)}">${__stockflowApp.icon('edit')}Sửa logo</button>`
                            : ''
                    }
                </article>`;
                  })
                  .join('')
            : '<p class="category-menu-message">Không tìm thấy thương hiệu phù hợp.</p>';
    }

function renderCategoryChips() {
        const selected = __stockflowApp.$('#catalog-category').value;
        const selectedCategory = __stockflowApp.state.categories.find((category) => String(category.id) === selected);
        const activeRoot = selectedCategory ? __stockflowApp.categoryAncestors(selectedCategory).at(-1) : null;
        __stockflowApp.$('#category-chips').innerHTML = [{ id: '', name: 'Tất cả' }, ...__stockflowApp.rootCategories()]
            .map(
                (category) =>
                    '<button type="button" data-category="' +
                    category.id +
                    '" class="' +
                    (String(category.id) === selected || category.id === activeRoot?.id ? 'active' : '') +
                    '" aria-pressed="' +
                    String(String(category.id) === selected || category.id === activeRoot?.id) +
                    '">' +
                    __stockflowApp.escapeHtml(category.name) +
                    '</button>',
            )
            .join('');
        __stockflowApp.renderHomeCategoryState();
    }

function categoryAncestors(category) {
        const result = [];
        const visited = new Set();
        for (let current = category; current && !visited.has(current.id);) {
            result.push(current);
            visited.add(current.id);
            current = __stockflowApp.state.categories.find((item) => item.id === current.parent_id);
        }
        return result;
    }

function categoryPath(category) {
        return __stockflowApp.categoryAncestors(category)
            .reverse()
            .map((item) => item.name)
            .join(' › ');
    }

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
        return __stockflowApp.state.categories
            .filter((category) => !category.parent_id)
            .sort((first, second) => rank(first) - rank(second) || first.id - second.id);
    }

function orderedCategories() {
        const ordered = [];
        const seen = new Set();
        function visit(category) {
            if (seen.has(category.id)) return;
            seen.add(category.id);
            ordered.push(category);
            __stockflowApp.state.categories.filter((item) => item.parent_id === category.id).forEach(visit);
        }
        __stockflowApp.rootCategories().forEach(visit);
        __stockflowApp.state.categories.forEach(visit);
        return ordered;
    }

function categoryBelongsTo(categoryId, parentId) {
        const category = __stockflowApp.state.categories.find((item) => item.id === categoryId);
        return Boolean(category && __stockflowApp.categoryAncestors(category).some((item) => item.id === parentId));
    }

function categoryPriceRanges(category) {
        const root = category ? __stockflowApp.categoryAncestors(category).at(-1) : null;
        if (root?.slug === 'laptop') return __stockflowApp.LAPTOP_PRICE_RANGES;
        if (root?.slug === 'am-thanh-mic-thu-am') return __stockflowApp.AUDIO_PRICE_RANGES;
        return __stockflowApp.CATALOG_PRICE_RANGES;
    }

function shopCategoryIcon(category) {
        const name = __stockflowApp.slugify(category?.name || '');
        if (/dien-thoai|tablet|iphone/.test(name)) return 'device';
        if (/tai-nghe|loa|am-thanh|micro/.test(name)) return 'headphones';
        if (/laptop|may-tinh/.test(name)) return 'laptop';
        if (/man-hinh|webcam/.test(name)) return 'monitor';
        if (/ban-phim|chuot/.test(name)) return 'keyboard';
        if (/cap|sac|hub|phu-kien/.test(name)) return 'plug';
        return 'grid';
    }

function renderShopCategoryMenu() {
        // Cột trái chỉ chứa nhóm gốc; các cấp con nằm trong panel nên không dàn hàng chục nút cùng cấp.
        const groups = __stockflowApp.rootCategories();
        const categories = [{ id: '', name: 'Tất cả sản phẩm' }, ...groups];
        if (!categories.some((category) => String(category.id) === __stockflowApp.state.menuCategoryId)) __stockflowApp.state.menuCategoryId = '';
        __stockflowApp.$('#category-menu-list').innerHTML = categories
            .map(
                (category) => `
            <button type="button" class="category-menu-item" data-menu-category="${category.id}" data-action="preview-category"
                aria-controls="category-menu-detail" aria-expanded="false">
                ${__stockflowApp.icon(__stockflowApp.shopCategoryIcon(category))}
                <span>${__stockflowApp.escapeHtml(category.name)}</span>
                ${__stockflowApp.icon('arrow')}
            </button>
        `,
            )
            .join('');
        __stockflowApp.$('#home-category-list').innerHTML = categories
            .map(
                (category) => `
            <button type="button" class="category-menu-item" data-menu-category="${category.id}"
                data-action="preview-category" aria-controls="home-category-detail" aria-expanded="false">
                ${__stockflowApp.icon(__stockflowApp.shopCategoryIcon(category))}
                <span>${__stockflowApp.escapeHtml(category.name)}</span>
                ${__stockflowApp.icon('arrow')}
            </button>`,
            )
            .join('');
        __stockflowApp.renderHomeCategoryState();
        __stockflowApp.renderShopCategoryMenuDetail();
    }

function openCategoryPreview(id) {
        if (id && !__stockflowApp.state.categories.some((category) => String(category.id) === id)) return;
        if (__stockflowApp.canUseHomeCategoryMenu()) {
            __stockflowApp.previewHomeCategory(id);
            return;
        }
        if (!__stockflowApp.state.categoryMenuOpen) __stockflowApp.setShopCategoryMenu(true);
        __stockflowApp.previewShopCategory(id);
    }

function previewShopCategory(id) {
        if (__stockflowApp.state.menuCategoryId === id) return;
        __stockflowApp.state.menuCategoryId = id;
        __stockflowApp.renderShopCategoryMenuDetail();
        __stockflowApp.$('#category-menu-panel').scrollTop = 0;
        __stockflowApp.$('#category-menu-detail').scrollTop = 0;
    }

function renderShopCategoryMenuDetail() {
        __stockflowApp.$$('#category-menu-list [data-menu-category]').forEach((button) => {
            const active = button.dataset.menuCategory === __stockflowApp.state.menuCategoryId;
            button.classList.toggle('active', active);
            button.setAttribute('aria-pressed', String(active));
            button.setAttribute('aria-expanded', String(active));
        });
        __stockflowApp.$('#category-menu-detail').innerHTML = __stockflowApp.categoryMenuContent(__stockflowApp.state.menuCategoryId);
        if (__stockflowApp.state.homeCategoryId !== null) {
            __stockflowApp.$('#home-category-detail').innerHTML = __stockflowApp.categoryMenuContent(__stockflowApp.state.homeCategoryId);
        }
    }

function categoryMenuContent(categoryId) {
        const category = __stockflowApp.state.categories.find((item) => String(item.id) === categoryId);
        const childGroups = __stockflowApp.state.categories.filter((item) => item.parent_id === category?.id);
        return `
            <div class="menu-category-feature">
                <span class="menu-category-symbol" aria-hidden="true">${__stockflowApp.icon(__stockflowApp.shopCategoryIcon(category))}</span>
                <div>
                    <span class="eyebrow">KHÁM PHÁ CỬA HÀNG</span>
                    <h2>${__stockflowApp.escapeHtml(category?.name || 'Tất cả sản phẩm')}</h2>
                    <p>Chọn loại sản phẩm, thương hiệu và khoảng giá phù hợp.</p>
                </div>
            </div>
            ${childGroups.length ? __stockflowApp.renderMenuChildGroups(childGroups) : __stockflowApp.renderMenuBrands(category)}
            ${childGroups.length && category?.slug === 'do-gia-dung-lam-dep' ? __stockflowApp.renderMenuBrands(category) : ''}
            <div class="menu-price-heading">${__stockflowApp.icon('tag')}<h3>Chọn theo khoảng giá</h3></div>
            <div class="menu-price-grid">
                ${__stockflowApp.categoryPriceRanges(category)
                    .map(
                        (range) => `
                    <button type="button" data-action="browse-price-range" data-price-range="${range.key}"
                        data-menu-category="${categoryId}">
                        ${__stockflowApp.escapeHtml(range.label)}${__stockflowApp.icon('arrow')}
                    </button>
                `,
                    )
                    .join('')}
            </div>
            <button class="button primary menu-browse-button" type="button" data-action="browse-category" data-menu-category="${categoryId}">
                Xem sản phẩm${__stockflowApp.icon('arrow')}
            </button>
            <p class="menu-discovery-note">Nhóm lớn bao gồm các nhóm con. Danh mục chưa có sản phẩm sẽ hiển thị kết quả trống.</p>
        `;
    }

function renderHomeCategoryState() {
        const selected = __stockflowApp.state.categories.find((category) => String(category.id) === __stockflowApp.$('#catalog-category').value);
        const selectedId = selected ? String(__stockflowApp.categoryAncestors(selected).at(-1).id) : '';
        __stockflowApp.$$('#home-category-list [data-menu-category]').forEach((button) => {
            const previewed = button.dataset.menuCategory === __stockflowApp.state.homeCategoryId;
            const chosen = button.dataset.menuCategory === selectedId;
            button.classList.toggle('active', __stockflowApp.state.homeCategoryId === null ? chosen : previewed);
            button.setAttribute('aria-pressed', String(chosen));
            button.setAttribute('aria-expanded', String(previewed));
        });
    }

function previewHomeCategory(id) {
        if (!__stockflowApp.homeCategoryDesktop.matches || __stockflowApp.state.view !== 'shop' || __stockflowApp.state.shopTab !== 'catalog') return;
        if (__stockflowApp.state.homeCategoryId === id) return;
        __stockflowApp.state.homeCategoryId = id;
        __stockflowApp.$('#home-category-detail').innerHTML = __stockflowApp.categoryMenuContent(id);
        __stockflowApp.$('#home-category-detail').hidden = false;
        __stockflowApp.$('#home-categories').classList.add('expanded');
        __stockflowApp.renderHomeCategoryState();
    }

function closeHomeCategoryMenu({ restoreFocus = false } = {}) {
        __stockflowApp.state.homeCategoryId = null;
        __stockflowApp.$('#home-category-detail').hidden = true;
        __stockflowApp.$('#home-categories').classList.remove('expanded');
        __stockflowApp.renderHomeCategoryState();
        if (restoreFocus) __stockflowApp.$('#home-categories-title').focus({ preventScroll: true });
    }

function renderMenuChildGroups(groups) {
        return `<div class="menu-catalog-columns">${groups
            .map((group) => {
                const children = __stockflowApp.state.categories.filter((item) => item.parent_id === group.id);
                return `<section class="menu-catalog-column" data-menu-group="${__stockflowApp.escapeHtml(group.slug)}">
                <button type="button" class="menu-column-title" data-action="browse-category" data-menu-category="${group.id}">
                    ${__stockflowApp.escapeHtml(group.name)}${__stockflowApp.icon('arrow')}
                </button>
                <div class="menu-subcategory-list">${children
                    .map(
                        (child) => `
                    <button type="button" class="menu-subcategory-button" data-action="browse-category" data-menu-category="${child.id}" data-category-slug="${__stockflowApp.escapeHtml(child.slug)}">
                        ${__stockflowApp.escapeHtml(child.name)}
                    </button>`,
                    )
                    .join('')}</div>
                ${__stockflowApp.categoryAncestors(group).at(-1)?.slug === 'do-gia-dung-lam-dep' ? '' : __stockflowApp.renderMenuBrands(group, true)}
            </section>`;
            })
            .join('')}</div>`;
    }

function renderMenuBrands(category, compact = false) {
        const brands = __stockflowApp.state.brands.filter((brand) => category && brand.category_ids.includes(category.id));
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
            <div class="menu-price-heading">${__stockflowApp.icon('device')}<h3>${__stockflowApp.escapeHtml(title)}</h3></div>
            <div class="menu-brand-grid">${brands
                .map(
                    (brand) => `
                <button class="menu-brand-button" type="button" data-action="browse-brand" data-menu-category="${category.id}" data-brand-id="${brand.id}" data-brand-slug="${__stockflowApp.escapeHtml(brand.slug)}">
                    ${brand.logo_url ? __stockflowApp.brandLogoMarkup(brand) : ''}
                    <span>${__stockflowApp.escapeHtml(brand.name)}</span>${brand.slug === 'apple' && appleLabel ? '<small>' + appleLabel + '</small>' : ''}
                </button>`,
                )
                .join('')}</div>
        </div>`;
    }

function canUseHomeCategoryMenu() {
        return __stockflowApp.homeCategoryDesktop.matches && __stockflowApp.state.view === 'shop' && __stockflowApp.state.shopTab === 'catalog';
    }

function isShopCategoryTarget(target) {
        return Boolean(
            target?.closest?.('.category-menu-anchor') ||
            (__stockflowApp.state.categoryMenuInline && target?.closest?.('#home-categories')),
        );
    }

function fitHomeCategoryMenu() {
        const discovery = __stockflowApp.$('.shop-discovery');
        const sidebar = __stockflowApp.$('#home-categories');
        discovery.style.minHeight = discovery.getBoundingClientRect().height + 'px';
        discovery.style.setProperty(
            '--home-menu-max-height',
            Math.max(180, window.innerHeight - sidebar.getBoundingClientRect().top - 16) + 'px',
        );
    }

function setShopCategoryMenu(open, { restoreFocus = false } = {}) {
        const wasInline = __stockflowApp.state.categoryMenuInline;
        const inlineEligible = __stockflowApp.canUseHomeCategoryMenu();
        const inline = open && inlineEligible;
        if (inline && !wasInline) __stockflowApp.fitHomeCategoryMenu();
        __stockflowApp.state.categoryMenuOpen = open;
        __stockflowApp.state.categoryMenuInline = inline;
        __stockflowApp.$('#category-menu-panel').hidden = !open || inline;
        __stockflowApp.$('#category-menu-backdrop').hidden = !open;
        document.body.classList.toggle('category-menu-open', open);
        document.body.classList.toggle('category-menu-inline', inline);
        __stockflowApp.$('#home-categories').classList.toggle('pinned', inline);
        __stockflowApp.$('.shop-main').inert = open && !inline;
        // Không đặt inert trên main ở chế độ ngoài vì chính menu cần mở cũng nằm trong main.
        __stockflowApp.$$('#shop-catalog > :not(.shop-discovery), .shop-discovery > .shop-hero, .shop-footer').forEach((element) => {
            element.inert = inline;
        });
        __stockflowApp.$('#category-menu-toggle').setAttribute('aria-expanded', String(open));
        __stockflowApp.$('#category-menu-toggle').setAttribute(
            'aria-controls',
            inlineEligible ? 'home-category-detail' : 'category-menu-panel',
        );
        if (!inline) {
            __stockflowApp.$('.shop-discovery').style.removeProperty('min-height');
            __stockflowApp.$('.shop-discovery').style.removeProperty('--home-menu-max-height');
            if (wasInline || open) __stockflowApp.closeHomeCategoryMenu();
        }
        if (open) {
            const selected = __stockflowApp.state.categories.find((category) => String(category.id) === __stockflowApp.$('#catalog-category').value);
            __stockflowApp.state.menuCategoryId = selected ? String(__stockflowApp.categoryAncestors(selected).at(-1).id) : '';
            if (inline) __stockflowApp.previewHomeCategory(__stockflowApp.state.menuCategoryId);
            else __stockflowApp.renderShopCategoryMenuDetail();
        } else if (restoreFocus) __stockflowApp.$('#category-menu-toggle').focus({ preventScroll: true });
    }

async function browseShopCategory(id, rangeKey = 'all', brandId = '') {
        const range = [...__stockflowApp.CATALOG_PRICE_RANGES, ...__stockflowApp.LAPTOP_PRICE_RANGES, ...__stockflowApp.AUDIO_PRICE_RANGES].find(
            (item) => item.key === rangeKey,
        );
        if (!range || (id && !__stockflowApp.state.categories.some((category) => String(category.id) === id))) return;
        __stockflowApp.$('#catalog-category').value = id;
        __stockflowApp.state.catalogBrandId = brandId;
        __stockflowApp.renderCatalogBrandOptions();
        __stockflowApp.$('#catalog-query').value = '';
        __stockflowApp.state.catalogMinPrice = range.min;
        __stockflowApp.state.catalogMaxPrice = range.max;
        __stockflowApp.syncCatalogPriceFields();
        __stockflowApp.setShopCategoryMenu(false);
        __stockflowApp.closeHomeCategoryMenu();
        await __stockflowApp.reloadCatalogFilters({ scroll: true });
    }

function sameCatalogPrice(current, expected) {
        return current === '' || expected === '' ? current === expected : Number(current) === Number(expected);
    }

function syncQuickPriceChips() {
        __stockflowApp.$$('[data-price-chip]').forEach((button) => {
            const range = __stockflowApp.QUICK_PRICE_RANGES[button.dataset.priceChip];
            button.setAttribute(
                'aria-pressed',
                String(
                    __stockflowApp.sameCatalogPrice(__stockflowApp.state.catalogMinPrice, range.min) &&
                        __stockflowApp.sameCatalogPrice(__stockflowApp.state.catalogMaxPrice, range.max),
                ),
            );
        });
    }

async function applyQuickPrice(key) {
        const range = __stockflowApp.QUICK_PRICE_RANGES[key];
        if (!range) return;
        __stockflowApp.state.catalogMinPrice = range.min;
        __stockflowApp.state.catalogMaxPrice = range.max;
        __stockflowApp.syncCatalogPriceFields();
        await __stockflowApp.reloadCatalogFilters();
    }

function syncCatalogPriceFields() {
        __stockflowApp.$('#catalog-min-price').value = __stockflowApp.state.catalogMinPrice;
        __stockflowApp.$('#catalog-max-price').value = __stockflowApp.state.catalogMaxPrice;
        __stockflowApp.validateCatalogPriceFields();
        __stockflowApp.syncQuickPriceChips();
        __stockflowApp.syncCatalogPriceSlider();
    }

function syncCatalogPriceSlider({ keepDomain = false, dragging = false } = {}) {
        const minimum = __stockflowApp.$('#catalog-min-price');
        const maximum = __stockflowApp.$('#catalog-max-price');
        const low = __stockflowApp.$('#catalog-price-low');
        const high = __stockflowApp.$('#catalog-price-high');
        const minimumValue = Number.isFinite(minimum.valueAsNumber) ? Math.max(0, minimum.valueAsNumber) : 0;
        const maximumValue = Number.isFinite(maximum.valueAsNumber) ? Math.max(0, maximum.valueAsNumber) : null;
        const required = Math.min(__stockflowApp.MAX_CATALOG_PRICE, Math.max(minimumValue + __stockflowApp.PRICE_SLIDER_STEP, maximumValue || 0));
        const ceiling = keepDomain
            ? Number(high.max)
            : Math.max(__stockflowApp.PRICE_SLIDER_DEFAULT_MAX, Math.ceil(required / 5_000_000) * 5_000_000);
        const start = Math.min(minimumValue, ceiling);
        const end = maximumValue === null ? ceiling : Math.min(maximumValue, ceiling);
        low.max = high.max = String(ceiling);
        low.value = String(start);
        high.value = String(end);
        const container = __stockflowApp.$('#catalog-price-slider');
        container.style.setProperty('--price-start', (Math.min(start, end) / ceiling) * 100 + '%');
        container.style.setProperty('--price-end', (Math.max(start, end) / ceiling) * 100 + '%');
        __stockflowApp.$('#catalog-price-low-label').textContent = __stockflowApp.amount(minimumValue);
        __stockflowApp.$('#catalog-price-high-label').textContent = maximumValue === null ? 'Không giới hạn' : __stockflowApp.amount(maximumValue);
        low.setAttribute('aria-valuetext', 'Từ ' + __stockflowApp.amount(minimumValue));
        high.setAttribute('aria-valuetext', maximumValue === null ? 'Không giới hạn' : 'Đến ' + __stockflowApp.amount(maximumValue));
        const draft =
            !__stockflowApp.sameCatalogPrice(minimum.value, __stockflowApp.state.catalogMinPrice) ||
            !__stockflowApp.sameCatalogPrice(maximum.value, __stockflowApp.state.catalogMaxPrice);
        __stockflowApp.$('#catalog-price-hint').textContent = dragging
            ? 'Thả tay để áp dụng khoảng giá đã chọn.'
            : draft
              ? 'Bấm Áp dụng để lọc theo giá đã nhập.'
              : 'Kéo và thả để lọc. Mở Nhập giá chính xác để chọn giá tùy ý.';
    }

function updatePriceDraftFromSlider(id) {
        const minimum = __stockflowApp.$('#catalog-min-price');
        const maximum = __stockflowApp.$('#catalog-max-price');
        const ceiling = Number(__stockflowApp.$('#catalog-price-high').max);
        if (id === 'catalog-price-low') {
            const upper = Number.isFinite(maximum.valueAsNumber) ? Math.max(0, maximum.valueAsNumber) : ceiling;
            const value = Math.min(Number(__stockflowApp.$('#catalog-price-low').value), upper, __stockflowApp.MAX_CATALOG_PRICE);
            minimum.value = value === 0 ? '' : String(value);
        } else {
            const value = Number(__stockflowApp.$('#catalog-price-high').value);
            const lower = Number.isFinite(minimum.valueAsNumber) ? Math.max(0, minimum.valueAsNumber) : 0;
            maximum.value = value >= ceiling ? '' : String(Math.min(__stockflowApp.MAX_CATALOG_PRICE, Math.max(value, lower)));
        }
        __stockflowApp.$('#catalog-price-slider').dataset.activeHandle = id;
        __stockflowApp.validateCatalogPriceFields();
        __stockflowApp.syncCatalogPriceSlider({ keepDomain: true, dragging: true });
    }

async function applyCatalogPriceDraft() {
        if (!__stockflowApp.validateCatalogPriceFields()) return;
        const minimum = __stockflowApp.$('#catalog-min-price').value;
        const maximum = __stockflowApp.$('#catalog-max-price').value;
        const unchanged =
            __stockflowApp.sameCatalogPrice(minimum, __stockflowApp.state.catalogMinPrice) && __stockflowApp.sameCatalogPrice(maximum, __stockflowApp.state.catalogMaxPrice);
        if (unchanged && __stockflowApp.$('#catalog-grid').dataset.catalogStatus !== 'error') {
            __stockflowApp.syncCatalogPriceFields();
            return;
        }
        __stockflowApp.state.catalogMinPrice = minimum;
        __stockflowApp.state.catalogMaxPrice = maximum;
        __stockflowApp.syncCatalogPriceFields();
        await __stockflowApp.reloadCatalogFilters();
    }

function validateCatalogPriceFields() {
        const minimum = __stockflowApp.$('#catalog-min-price');
        const maximum = __stockflowApp.$('#catalog-max-price');
        const invalid = minimum.value !== '' && maximum.value !== '' && Number(minimum.value) > Number(maximum.value);
        maximum.setCustomValidity(invalid ? 'Giá đến phải lớn hơn hoặc bằng giá từ.' : '');
        const fields = [minimum, maximum];
        const broken = fields.find((field) => !field.validity.valid);
        let message = invalid ? 'Giá đến phải lớn hơn hoặc bằng giá từ.' : '';
        if (broken?.validity.badInput) message = 'Vui lòng nhập giá hợp lệ.';
        else if (broken?.validity.rangeUnderflow) message = 'Giá không được âm.';
        else if (broken?.validity.rangeOverflow) message = 'Giá không vượt quá ' + __stockflowApp.amount(__stockflowApp.MAX_CATALOG_PRICE) + '.';
        else if (broken?.validity.stepMismatch) message = 'Giá chỉ có tối đa hai chữ số thập phân.';
        const error = __stockflowApp.$('#catalog-price-error');
        error.hidden = !message;
        error.textContent = message;
        // Mở ô nhập khi có lỗi để thông báo HTML có thể đưa focus đến trường cần sửa.
        if (broken) __stockflowApp.$('#catalog-price-details').open = true;
        fields.forEach((field) => field.setAttribute('aria-invalid', String(!field.validity.valid)));
        return __stockflowApp.$('#catalog-price-filter').checkValidity();
    }

function movePriceTrackPointer(event) {
        if (!__stockflowApp.priceSliderDrag || __stockflowApp.priceSliderDrag.pointerId !== event.pointerId) return;
        const box = __stockflowApp.$('#catalog-price-slider').getBoundingClientRect();
        const ratio = Math.max(0, Math.min(1, (event.clientX - box.left - 10) / (box.width - 20)));
        const value = Math.round((ratio * __stockflowApp.priceSliderDrag.ceiling) / __stockflowApp.PRICE_SLIDER_STEP) * __stockflowApp.PRICE_SLIDER_STEP;
        __stockflowApp.$('#' + __stockflowApp.priceSliderDrag.id).value = String(value);
        __stockflowApp.updatePriceDraftFromSlider(__stockflowApp.priceSliderDrag.id);
    }

function cancelPriceTrackPointer(event) {
        if (!__stockflowApp.priceSliderDrag || __stockflowApp.priceSliderDrag.pointerId !== event.pointerId) return;
        __stockflowApp.$('#catalog-min-price').value = __stockflowApp.priceSliderDrag.minimum;
        __stockflowApp.$('#catalog-max-price').value = __stockflowApp.priceSliderDrag.maximum;
        __stockflowApp.priceSliderDrag = null;
        __stockflowApp.validateCatalogPriceFields();
        __stockflowApp.syncCatalogPriceSlider();
    }

async function reloadCatalogFilters({ scroll = false } = {}) {
        __stockflowApp.closeSearchSuggestions();
        __stockflowApp.state.pages.catalog = 0;
        if (__stockflowApp.state.view !== 'shop' || __stockflowApp.state.shopTab !== 'catalog') await __stockflowApp.activateView('shop', 'catalog');
        else await __stockflowApp.loadCatalog();
        if (scroll) __stockflowApp.scrollToCatalogResults();
    }

function scrollToCatalogResults({ behavior = 'auto' } = {}) {
        // Chọn danh mục phải thấy kết quả; bộ lọc dài chỉ mở khi khách cần trên màn hình nhỏ.
        if (window.matchMedia('(max-width: 900px)').matches) __stockflowApp.$('#catalog-filters-panel').open = false;
        __stockflowApp.$('#product-shelf').scrollIntoView({ block: 'start', behavior });
        __stockflowApp.$('#catalog-title').tabIndex = -1;
        __stockflowApp.$('#catalog-title').focus({ preventScroll: true });
    }

async function clearCatalogFilter(filter) {
        if (filter === 'all' || filter === 'specification') {
            __stockflowApp.state.catalogSpecName = '';
            __stockflowApp.state.catalogSpecValue = '';
            __stockflowApp.$('#catalog-spec-filter').reset();
            __stockflowApp.renderSpecificationValueOptions();
        }
        if (filter === 'all' || filter === 'category') __stockflowApp.$('#catalog-category').value = '';
        if (filter === 'all' || filter === 'category' || filter === 'brand') __stockflowApp.state.catalogBrandId = '';
        __stockflowApp.renderCatalogBrandOptions();
        if (filter === 'all' || filter === 'search') __stockflowApp.$('#catalog-query').value = '';
        if (filter === 'all' || filter === 'price') {
            __stockflowApp.state.catalogMinPrice = '';
            __stockflowApp.state.catalogMaxPrice = '';
            __stockflowApp.syncCatalogPriceFields();
        }
        if (filter === 'all' || filter === 'sort') {
            __stockflowApp.state.catalogSort = 'id,asc';
            __stockflowApp.$('#catalog-sort').value = __stockflowApp.state.catalogSort;
        }
        await __stockflowApp.reloadCatalogFilters();
    }

function renderCatalogActiveFilters() {
        const category = __stockflowApp.state.categories.find((item) => String(item.id) === __stockflowApp.$('#catalog-category').value);
        const filters = [];
        if (__stockflowApp.state.catalogSpecName && __stockflowApp.state.catalogSpecValue) filters.push({ key: 'specification', label: __stockflowApp.state.catalogSpecName + ': ' + __stockflowApp.state.catalogSpecValue });
        if (category) filters.push({ key: 'category', label: category.name });
        const brand = __stockflowApp.state.brands.find((item) => String(item.id) === __stockflowApp.state.catalogBrandId);
        if (brand) filters.push({ key: 'brand', label: 'Hãng: ' + brand.name });
        if (__stockflowApp.$('#catalog-query').value.trim())
            filters.push({ key: 'search', label: 'Tìm: ' + __stockflowApp.$('#catalog-query').value.trim() });
        if (__stockflowApp.state.catalogMinPrice !== '' || __stockflowApp.state.catalogMaxPrice !== '') {
            const label =
                __stockflowApp.state.catalogMinPrice !== '' && __stockflowApp.state.catalogMaxPrice !== ''
                    ? __stockflowApp.amount(__stockflowApp.state.catalogMinPrice) + ' – ' + __stockflowApp.amount(__stockflowApp.state.catalogMaxPrice)
                    : __stockflowApp.state.catalogMinPrice !== ''
                      ? 'Từ ' + __stockflowApp.amount(__stockflowApp.state.catalogMinPrice)
                      : 'Đến ' + __stockflowApp.amount(__stockflowApp.state.catalogMaxPrice);
            filters.push({ key: 'price', label });
        }
        if (__stockflowApp.state.catalogSort !== 'id,asc')
            filters.push({ key: 'sort', label: __stockflowApp.$('#catalog-sort').selectedOptions[0].textContent });
        const container = __stockflowApp.$('#catalog-active-filters');
        container.hidden = !filters.length;
        container.innerHTML =
            filters
                .map(
                    (filter) => `
            <button class="active-filter" type="button" data-action="clear-catalog-filter" data-filter="${filter.key}" aria-label="Bỏ bộ lọc ${__stockflowApp.escapeHtml(filter.label)}">
                ${__stockflowApp.escapeHtml(filter.label)}${__stockflowApp.icon('close')}
            </button>
        `,
                )
                .join('') +
            '<button class="text-button" type="button" data-action="clear-catalog-filter" data-filter="all">Xóa bộ lọc</button>';
        __stockflowApp.$('#catalog-title').textContent = category?.name || 'Sản phẩm & đặt hàng';
    }

async function loadBranches() {
        __stockflowApp.state.warehouses = await __stockflowApp.api('/storefront/branches', { anonymous: true, channel: 'branches' });
        if (!__stockflowApp.state.warehouses.some((warehouse) => String(warehouse.id) === __stockflowApp.state.branchId)) {
            __stockflowApp.state.branchId = __stockflowApp.state.warehouses.length ? String(__stockflowApp.state.warehouses[0].id) : '';
        }
        __stockflowApp.renderWarehouses();
        __stockflowApp.saveCart();
    }

async function loadOperatingWarehouses() {
        __stockflowApp.state.operatingWarehouses = await __stockflowApp.api('/warehouses/operating-options', { channel: 'operating-warehouses' });
        __stockflowApp.renderWarehouses();
        __stockflowApp.renderPermissions();
    }

function renderWarehouses() {
        const branchOptions = __stockflowApp.state.warehouses
            .map((warehouse) => '<option value="' + warehouse.id + '">' + __stockflowApp.escapeHtml(warehouse.name) + '</option>')
            .join('');
        __stockflowApp.$$('[data-store-warehouse]').forEach((select) => {
            select.innerHTML = branchOptions || '<option value="">Chưa có chi nhánh đang phục vụ</option>';
            select.value = __stockflowApp.state.branchId;
            select.disabled = !__stockflowApp.state.warehouses.length;
        });
        const options = __stockflowApp.state.operatingWarehouses
            .map(
                (warehouse) =>
                    '<option value="' +
                    warehouse.id +
                    '">' +
                    __stockflowApp.escapeHtml(warehouse.name + ' · ' + warehouse.code) +
                    '</option>',
            )
            .join('');
        __stockflowApp.$$('[data-warehouse]').forEach((select) => {
            const previous = select.value;
            const staffInventory = __stockflowApp.hasRole('WAREHOUSE_STAFF') && select.id === 'inventory-warehouse';
            const all = select.dataset.warehouse === 'all' && !staffInventory;
            const caption = __stockflowApp.state.operatingWarehouses.length
                ? all
                    ? __stockflowApp.hasRole('WAREHOUSE_STAFF')
                        ? 'Tất cả kho được phân công'
                        : 'Tất cả kho'
                    : 'Chọn kho'
                : 'Chưa có kho được phép';
            select.innerHTML = (staffInventory && options ? '' : '<option value="">' + caption + '</option>') + options;
            if (__stockflowApp.state.operatingWarehouses.some((warehouse) => String(warehouse.id) === previous))
                select.value = previous;
            else select.value = !all && __stockflowApp.state.operatingWarehouses.length ? String(__stockflowApp.state.operatingWarehouses[0].id) : '';
            select.required = staffInventory || select.dataset.warehouse === 'required';
        });
    }

async function loadProductOptions() {
        let page = 0;
        let result;
        const products = new Map();
        do {
            result = await __stockflowApp.api('/products', {
                anonymous: true,
                channel: 'product-options',
                query: { page, size: 100, sort: 'id,asc' },
            });
            result.content.forEach((product) => products.set(product.id, product));
            page++;
        } while (!result.last);
        __stockflowApp.state.products = products;
        __stockflowApp.renderProductOptions();
        __stockflowApp.renderCart();
    }

function renderProductOptions() {
        const options = [...__stockflowApp.state.products.values()]
            .map(
                (product) =>
                    '<option value="' +
                    product.id +
                    '">' +
                    __stockflowApp.escapeHtml(product.sku + ' · ' + __stockflowApp.cartProductName(__stockflowApp.saleSku(product))) +
                    '</option>',
            )
            .join('');
        __stockflowApp.$$('[data-product]').forEach((select) => {
            const previous = select.value;
            select.innerHTML =
                '<option value="">' +
                (select.dataset.product === 'all' ? 'Tất cả sản phẩm' : 'Chọn sản phẩm') +
                '</option>' +
                options;
            if (__stockflowApp.state.products.has(Number(previous))) select.value = previous;
        });
    }

function warehouseName(id) {
        return (
            [...__stockflowApp.state.operatingWarehouses, ...__stockflowApp.state.warehouses].find((warehouse) => warehouse.id === id)?.name ||
            'Kho #' + id
        );
    }

function normalizeProductName(value) {
        return String(value ?? '')
            .toLowerCase()
            .normalize('NFD')
            .replace(/[\u0300-\u036f]/g, '')
            .replace(/đ/g, 'd')
            .replace(/[^a-z0-9]+/g, ' ')
            .trim();
    }

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

function productImage(product) {
        const name = __stockflowApp.normalizeProductName(product.name);
        const rule = __stockflowApp.PRODUCT_IMAGE_RULES.find((entry) => entry.names.some((alias) => name.includes(alias)));
        const savedImage =
            __stockflowApp.safeProductImageUrl(product.image_url) ||
            (Array.isArray(product.image_urls) ? product.image_urls.map(__stockflowApp.safeProductImageUrl).find(Boolean) : null);
        return {
            src:
                savedImage ||
                (rule ? __stockflowApp.PRODUCT_IMAGES[rule.image] : __stockflowApp.CATEGORY_IMAGES[product.category_name] || __stockflowApp.PRODUCT_IMAGES.macbook),
            fallback: __stockflowApp.CATEGORY_IMAGES[product.category_name] || __stockflowApp.PRODUCT_IMAGES.macbook,
            custom: Boolean(savedImage),
        };
    }

function closeSearchSuggestions() {
        __stockflowApp.searchSuggestions.version++;
        window.clearTimeout(__stockflowApp.searchSuggestions.timer);
        window.clearTimeout(__stockflowApp.searchSuggestions.timeout);
        __stockflowApp.searchSuggestions.timer = null;
        __stockflowApp.searchSuggestions.timeout = null;
        __stockflowApp.searchSuggestions.items = [];
        __stockflowApp.searchSuggestions.active = -1;
        __stockflowApp.channels.get('search-suggestions')?.abort();
        __stockflowApp.channels.delete('search-suggestions');
        __stockflowApp.$('#search-suggestions').hidden = true;
        __stockflowApp.$('#search-suggestions-list').replaceChildren();
        __stockflowApp.$('#search-suggestions-list').removeAttribute('aria-busy');
        __stockflowApp.$('#catalog-query').setAttribute('aria-expanded', 'false');
        __stockflowApp.$('#catalog-query').removeAttribute('aria-activedescendant');
        __stockflowApp.$('#search-suggestions-status').textContent = '';
    }

function canSuggestProducts() {
        return (
            __stockflowApp.state.view === 'shop' &&
            !__stockflowApp.state.authBusy &&
            !__stockflowApp.searchSuggestions.composing &&
            __stockflowApp.$('#catalog-filter').contains(document.activeElement) &&
            !document.querySelector('dialog[open]')
        );
    }

function updateSearchSuggestionsLayout() {
        const popup = __stockflowApp.$('#search-suggestions');
        if (popup.hidden) return;
        const viewport = window.visualViewport;
        const viewportTop = viewport?.offsetTop || 0;
        const viewportBottom = viewportTop + (viewport?.height || window.innerHeight);
        const inputBounds = __stockflowApp.$('#catalog-query').getBoundingClientRect();
        if (inputBounds.bottom < viewportTop || inputBounds.top > viewportBottom) {
            __stockflowApp.closeSearchSuggestions();
            return;
        }
        const controlsHeight =
            __stockflowApp.$('.search-suggestions-heading', popup).offsetHeight + __stockflowApp.$('.search-suggestions-all', popup).offsetHeight;
        const availableHeight = Math.max(0, viewportBottom - popup.getBoundingClientRect().top - controlsHeight - 14);
        popup.style.setProperty('--search-list-height', Math.min(480, availableHeight) + 'px');
    }

function renderSearchSuggestionsLoading(query) {
        __stockflowApp.$('#search-suggestions').hidden = false;
        __stockflowApp.$('#catalog-query').setAttribute('aria-expanded', 'true');
        __stockflowApp.$('#search-suggestions-feedback').hidden = true;
        __stockflowApp.$('#search-suggestions-all-label').textContent = 'Tìm tất cả kết quả cho “' + query + '”';
        const list = __stockflowApp.$('#search-suggestions-list');
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
        __stockflowApp.$('#search-suggestions-status').textContent = 'Đang tìm sản phẩm…';
        __stockflowApp.updateSearchSuggestionsLayout();
    }

function renderSearchSuggestions(products) {
        __stockflowApp.searchSuggestions.items = products;
        const list = __stockflowApp.$('#search-suggestions-list');
        list.removeAttribute('aria-busy');
        list.innerHTML = products
            .map((product, index) => {
                const photo = __stockflowApp.productImage(product);
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
                            ${__stockflowApp.icon('box')}
                            <img src="${__stockflowApp.escapeHtml(photo.src)}" alt="" decoding="async" data-search-image />
                        </span>
                        <span class="search-suggestion-content">
                            <strong class="search-suggestion-name">${__stockflowApp.escapeHtml(product.name)}</strong>
                            <span class="search-suggestion-meta">
                                ${__stockflowApp.escapeHtml(product.brand_name || product.category_name || '')} · ${__stockflowApp.escapeHtml(product.sku)}
                            </span>
                            <span class="search-suggestion-price">${fromPrice}${__stockflowApp.amount(price)}</span>
                        </span>
                        ${__stockflowApp.icon('arrow')}
                    </a>
                `;
            })
            .join('');
        const feedback = __stockflowApp.$('#search-suggestions-feedback');
        feedback.hidden = products.length > 0;
        feedback.textContent = 'Chưa tìm thấy sản phẩm phù hợp. Thử tên hoặc mã SKU khác.';
        __stockflowApp.$('#search-suggestions-status').textContent = products.length
            ? 'Có ' + products.length + ' gợi ý. Dùng phím lên xuống để chọn, Enter để xem chi tiết.'
            : feedback.textContent;
        __stockflowApp.updateSearchSuggestionsLayout();
    }

function queueSearchSuggestions({ immediate = false } = {}) {
        __stockflowApp.closeSearchSuggestions();
        const query = __stockflowApp.$('#catalog-query').value.trim();
        if (!query || !__stockflowApp.canSuggestProducts()) return;
        if (__stockflowApp.state.categoryMenuOpen) __stockflowApp.setShopCategoryMenu(false);
        __stockflowApp.closeHomeCategoryMenu();
        const version = __stockflowApp.searchSuggestions.version;
        __stockflowApp.renderSearchSuggestionsLoading(query);
        __stockflowApp.searchSuggestions.timer = window.setTimeout(
            () => __stockflowApp.loadSearchSuggestions(query, version),
            immediate ? 0 : __stockflowApp.SEARCH_SUGGESTION_DELAY_MS,
        );
    }

async function loadSearchSuggestions(query, version) {
        __stockflowApp.searchSuggestions.timer = null;
        const current = () =>
            version === __stockflowApp.searchSuggestions.version &&
            query === __stockflowApp.$('#catalog-query').value.trim() &&
            __stockflowApp.canSuggestProducts() &&
            !__stockflowApp.$('#search-suggestions').hidden;
        if (!current()) return;
        let timedOut = false;
        const timeout = window.setTimeout(() => {
            if (!current()) return;
            timedOut = true;
            __stockflowApp.channels.get('search-suggestions')?.abort();
        }, __stockflowApp.READ_TIMEOUT_MS);
        __stockflowApp.searchSuggestions.timeout = timeout;
        try {
            const result = await __stockflowApp.api('/products', {
                anonymous: true,
                channel: 'search-suggestions',
                query: {
                    q: query,
                    status: 'ACTIVE',
                    grouped: true,
                    page: 0,
                    size: __stockflowApp.SEARCH_SUGGESTION_LIMIT,
                    sort: 'id,asc',
                },
            });
            if (!current()) return;
            __stockflowApp.renderSearchSuggestions(
                result.content
                    .filter(
                        (product) => Number.isSafeInteger(product.id) && product.id > 0 && product.status === 'ACTIVE',
                    )
                    .slice(0, __stockflowApp.SEARCH_SUGGESTION_LIMIT),
            );
        } catch (error) {
            if (!current() || (error.name === 'AbortError' && !timedOut)) return;
            // Lỗi gợi ý chỉ ở dropdown; không tạo toast hoặc thay kết quả catalog đang xem.
            __stockflowApp.$('#search-suggestions-list').replaceChildren();
            __stockflowApp.$('#search-suggestions-list').removeAttribute('aria-busy');
            const feedback = __stockflowApp.$('#search-suggestions-feedback');
            feedback.hidden = false;
            feedback.innerHTML = `
                <p>Chưa tải được gợi ý. Bạn vẫn có thể nhấn Enter để tìm trên kệ hàng.</p>
                <button type="button" class="button secondary small" data-action="retry-search-suggestions">Thử lại</button>
            `;
            __stockflowApp.$('#search-suggestions-status').textContent = 'Chưa tải được gợi ý. Thử lại hoặc nhấn Enter để tìm.';
            __stockflowApp.updateSearchSuggestionsLayout();
        } finally {
            window.clearTimeout(timeout);
            if (__stockflowApp.searchSuggestions.timeout === timeout) __stockflowApp.searchSuggestions.timeout = null;
        }
    }

function selectSearchSuggestion(index) {
        const options = __stockflowApp.$$('#search-suggestions-list [role="option"]');
        if (!options.length) return;
        __stockflowApp.searchSuggestions.active = (index + options.length) % options.length;
        options.forEach((option, position) => {
            option.setAttribute('aria-selected', String(position === __stockflowApp.searchSuggestions.active));
        });
        const option = options[__stockflowApp.searchSuggestions.active];
        __stockflowApp.$('#catalog-query').setAttribute('aria-activedescendant', option.id);
        // Chỉ cuộn trong danh sách gợi ý, không kéo cả trang và làm ô tìm kiếm rời màn hình.
        const list = __stockflowApp.$('#search-suggestions-list');
        const rowBounds = option.getBoundingClientRect();
        const listBounds = list.getBoundingClientRect();
        if (rowBounds.top < listBounds.top) list.scrollTop += rowBounds.top - listBounds.top;
        else if (rowBounds.bottom > listBounds.bottom) list.scrollTop += rowBounds.bottom - listBounds.bottom;
    }

function productArt(product) {
        const photo = __stockflowApp.productImage(product);
        return `
            <div class="product-card-img-wrap">
                <img
                    src="${__stockflowApp.escapeHtml(photo.src)}"
                    alt="${__stockflowApp.escapeHtml(product.name)} — ${photo.custom ? 'ảnh sản phẩm' : 'ảnh minh họa'}"
                    loading="lazy"
                    decoding="async"
                    class="product-card-img"
                    data-image-fallback="${__stockflowApp.escapeHtml(photo.fallback)}"
                />
                ${product.brand_name ? '<span class="product-tag">' + __stockflowApp.escapeHtml(product.brand_name) + '</span>' : ''}
                <span class="product-photo-caption" ${photo.custom ? 'hidden' : ''}>Ảnh minh họa</span>
                <span class="product-image-error" hidden>Chưa tải được ảnh. Vui lòng kiểm tra kết nối.</span>
            </div>
        `;
    }

function renderHeroShowcase(products, { failed = false } = {}) {
        const container = __stockflowApp.$('#hero-showcase');
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
                    __stockflowApp.safeProductImageUrl(product.image_url) ||
                    (product.image_urls || []).map(__stockflowApp.safeProductImageUrl).find(Boolean);
                const price = product.min_price ?? product.unit_price;
                const from = product.min_price != null && Number(product.min_price) !== Number(product.max_price);
                return `
                    <a class="hero-device hero-device-${positions[index]}" href="${__stockflowApp.productPagePath(product.id)}"
                        data-product-link data-product-id="${product.id}" aria-label="Xem sản phẩm ${__stockflowApp.escapeHtml(product.name)}">
                        <div class="hero-device-media">
                            ${
                                source
                                    ? `<img src="${__stockflowApp.escapeHtml(source)}" alt="${__stockflowApp.escapeHtml(product.name)}"
                                width="320" height="320" decoding="async" data-hero-image />`
                                    : ''
                            }
                            <span class="hero-image-error" ${source ? 'hidden' : ''}>${source ? 'Ảnh chưa tải được.' : 'Xem thông tin sản phẩm'}</span>
                        </div>
                        <div class="hero-device-information">
                            <span class="hero-device-category">${__stockflowApp.escapeHtml(product.brand_name || product.category_name)}</span>
                            <span class="hero-device-name">${__stockflowApp.escapeHtml(product.name)}</span>
                            <strong class="hero-device-price">${from ? 'Từ ' : ''}${__stockflowApp.amount(price)}</strong>
                        </div>
                    </a>
                `;
            })
            .join('');
    }

function productStock(product) {
        if (product.status !== 'ACTIVE') return { tone: 'empty', label: 'Đã ngừng kinh doanh' };
        const rows = __stockflowApp.state.detailAvailability;
        if (rows && __stockflowApp.state.branchId) {
            const ids = product.variants?.length
                ? product.variants
                      .filter((variant) => variant.status === 'ACTIVE')
                      .map((variant) => variant.sku_product_id)
                : [product.id];
            const matches = rows.filter(
                (row) => ids.includes(row.product_id) && String(row.warehouse_id) === __stockflowApp.state.branchId,
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
            !__stockflowApp.state.branchId ||
            warehouseId == null ||
            String(warehouseId) !== __stockflowApp.state.branchId ||
            !Number.isInteger(available) ||
            available < 0
        ) {
            return { tone: 'unknown', label: 'Xem tình trạng hàng tại chi nhánh' };
        }
        return available > 0
            ? { tone: 'available', label: '🟢 Sẵn hàng tại kho' }
            : { tone: 'empty', label: '🔴 Tạm hết hàng' };
    }

function refreshProductStock() {
        __stockflowApp.$$(
            '#catalog-grid [data-product-stock], #bestseller-grid [data-product-stock], #shop-product [data-product-stock]',
        ).forEach((element) => {
            const product = element.closest('#shop-product')
                ? __stockflowApp.state.detailSku
                : __stockflowApp.state.products.get(Number(element.dataset.productStock));
            if (!product) return;
            const stock = __stockflowApp.productStock(product);
            element.className = 'product-stock stock-' + stock.tone;
            element.textContent = stock.label;
        });
        __stockflowApp.renderProductBranchAvailability();
        const button = __stockflowApp.$('#shop-product-add-form button[type="submit"]');
        if (button && button.getAttribute('aria-busy') !== 'true' && __stockflowApp.state.detailSku) {
            button.disabled =
                __stockflowApp.isOperator() || __stockflowApp.state.detailSku.status !== 'ACTIVE' || __stockflowApp.productStock(__stockflowApp.state.detailSku).tone === 'empty';
        }
        __stockflowApp.syncMobilePurchase();
    }

async function loadProductAvailability() {
        const root = __stockflowApp.state.detailRoot;
        if (!root || __stockflowApp.state.view !== 'shop' || __stockflowApp.state.shopTab !== 'product') return;
        const epoch = ++__stockflowApp.state.availabilityEpoch;
        __stockflowApp.state.detailAvailability = null;
        __stockflowApp.state.availabilityLoading = true;
        __stockflowApp.state.availabilityError = false;
        __stockflowApp.refreshProductStock();
        try {
            const rows = await __stockflowApp.api('/products/' + root.id + '/availability', {
                anonymous: true,
                channel: 'shop-product-availability',
            });
            if (
                epoch !== __stockflowApp.state.availabilityEpoch ||
                __stockflowApp.state.detailRoot?.id !== root.id ||
                __stockflowApp.state.view !== 'shop' ||
                __stockflowApp.state.shopTab !== 'product'
            )
                return;
            __stockflowApp.state.detailAvailability = rows;
        } catch (error) {
            if (error.name === 'AbortError' || epoch !== __stockflowApp.state.availabilityEpoch) return;
            __stockflowApp.state.availabilityError = true;
        } finally {
            if (epoch === __stockflowApp.state.availabilityEpoch) {
                __stockflowApp.state.availabilityLoading = false;
                __stockflowApp.refreshProductStock();
            }
        }
    }

function renderProductBranchAvailability() {
        const container = __stockflowApp.$('#product-branch-availability');
        if (!container || !__stockflowApp.state.detailSku) return;
        container.innerHTML =
            __stockflowApp.state.warehouses
                .map((branch) => {
                    const row = __stockflowApp.state.detailAvailability?.find(
                        (value) => value.product_id === __stockflowApp.state.detailSku.id && value.warehouse_id === branch.id,
                    );
                    const tone = row ? (row.in_stock ? 'available' : 'empty') : 'unknown';
                    const text = __stockflowApp.state.availabilityLoading
                        ? 'Đang kiểm tra…'
                        : row
                          ? row.in_stock
                              ? 'Còn hàng'
                              : 'Tạm hết hàng'
                          : 'Chưa xác nhận';
                    return `<button type="button" class="branch-stock-option ${String(branch.id) === __stockflowApp.state.branchId ? 'is-selected' : ''}"
                data-action="select-product-branch" data-id="${branch.id}" aria-pressed="${String(branch.id) === __stockflowApp.state.branchId}">
                ${__stockflowApp.icon('warehouse')}<span>${__stockflowApp.escapeHtml(branch.name)}</span><small class="stock-${tone}">${text}</small>
            </button>`;
                })
                .join('') || '<p class="subtle">Chưa có chi nhánh đang phục vụ.</p>';
        __stockflowApp.$('#product-availability-error').hidden = !__stockflowApp.state.availabilityError;
        const stock = __stockflowApp.$('#shop-product [data-product-stock]');
        if (stock && __stockflowApp.state.availabilityLoading) stock.textContent = 'Đang kiểm tra hàng tại chi nhánh…';
    }

function renderProductBreadcrumb(root) {
        const category = __stockflowApp.state.categories.find((value) => value.id === root.category_id);
        const ancestors = category ? __stockflowApp.categoryAncestors(category).reverse() : [];
        const links = ancestors.map(
            (value) => `<a href="/?categoryId=${value.id}#shop" data-catalog-link>${__stockflowApp.escapeHtml(value.name)}</a>`,
        );
        if (root.brand_id)
            links.push(`<a href="/?categoryId=${root.category_id}&amp;brandId=${root.brand_id}#shop"
            data-catalog-link>${__stockflowApp.escapeHtml(root.brand_name)}</a>`);
        __stockflowApp.$('#product-breadcrumb-category').innerHTML =
            links.join('<span aria-hidden="true">›</span>') || __stockflowApp.escapeHtml(root.category_name);
        __stockflowApp.$('#product-breadcrumb-name').textContent = root.name;
    }

function productCards(products, { bestseller = false } = {}) {
        return products
            .map((product) => {
                const stock = __stockflowApp.productStock(product);
                return `
                    <article class="product-card">
                        ${__stockflowApp.wishlistButton(product.id, product.name)}
                        <a class="product-card-link" href="${__stockflowApp.productPagePath(product.id)}" data-product-link data-product-id="${product.id}" aria-label="Xem sản phẩm ${__stockflowApp.escapeHtml(product.name)}">
                            ${__stockflowApp.productArt(product)}
                            <div class="product-card-body">
                                <span class="product-category">${__stockflowApp.escapeHtml(product.category_name)}</span>
                                <h3>${__stockflowApp.escapeHtml(product.name)}</h3>
                                <span class="product-sku mono" title="${__stockflowApp.escapeHtml(product.sku)}">${__stockflowApp.escapeHtml(product.sku)}</span>
                                ${bestseller ? '<span class="bestseller-label">Bán chạy</span>' : ''}
                                ${__stockflowApp.configurationPreview(product)}
                                <div class="product-card-footer">
                                    <strong class="product-price">${Number(product.min_price) !== Number(product.max_price) ? 'Từ ' : ''}${__stockflowApp.amount(product.min_price ?? product.unit_price)}</strong>
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
                                aria-label="Thêm ${__stockflowApp.escapeHtml(product.name)} vào giỏ"
                                ${__stockflowApp.isOperator() ? 'disabled title="Dùng tài khoản khách hàng để mua sắm"' : ''}
                            >
                                ${__stockflowApp.icon('plus')}${product.variants?.length ? 'Chọn phiên bản và màu' : 'Thêm vào giỏ'}
                            </button>
                        </div>
                    </article>
                `;
            })
            .join('');
    }

function wishlistButton(id, name) {
        const selected = __stockflowApp.wishlistIds.has(Number(id));
        return '<button type="button" class="wishlist-product-button' + (selected ? ' is-saved' : '')
            + '" data-action="toggle-wishlist" data-id="' + id + '" aria-pressed="' + selected
            + '" aria-label="' + __stockflowApp.escapeHtml((selected ? 'Bỏ yêu thích ' : 'Yêu thích ') + name)
            + '"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z" /></svg></button>';
    }

function syncWishlist() {
        const owner = __stockflowApp.state.user ? 'user:' + __stockflowApp.state.user.id : 'guest';
        if (owner !== __stockflowApp.wishlistOwner) {
            __stockflowApp.wishlistOwner = owner;
            __stockflowApp.wishlistLoadVersion++;
            try {
                const saved = JSON.parse(localStorage.getItem('stockflow.wishlist.' + owner) || '[]');
                __stockflowApp.wishlistIds = new Set(Array.isArray(saved) ? saved.filter(id => Number.isSafeInteger(id) && id > 0).slice(0, 100) : []);
            } catch { __stockflowApp.wishlistIds = new Set(); }
            __stockflowApp.$('#wishlist-dialog').close();
            __stockflowApp.$('#wishlist-content').replaceChildren();
        }
        __stockflowApp.$('#wishlist-count').textContent = __stockflowApp.wishlistIds.size;
        __stockflowApp.$$('[data-action="toggle-wishlist"]').forEach(button => {
            const selected = __stockflowApp.wishlistIds.has(Number(button.dataset.id));
            button.classList.toggle('is-saved', selected);
            button.setAttribute('aria-pressed', String(selected));
            const product = __stockflowApp.state.products.get(Number(button.dataset.id));
            button.setAttribute('aria-label', (selected ? 'Bỏ yêu thích ' : 'Yêu thích ') + (product?.name || 'sản phẩm'));
        });
    }

async function toggleWishlist(id) {
        __stockflowApp.syncWishlist();
        if (!Number.isSafeInteger(id) || id <= 0 || __stockflowApp.state.authBusy) return;
        const selected = __stockflowApp.wishlistIds.has(id);
        if (!selected && __stockflowApp.wishlistIds.size >= 100) throw new Error('Bạn có thể lưu tối đa 100 sản phẩm yêu thích.');
        const next = new Set(__stockflowApp.wishlistIds);
        if (selected) next.delete(id); else next.add(id);
        try { localStorage.setItem('stockflow.wishlist.' + __stockflowApp.wishlistOwner, JSON.stringify([...next])); }
        catch { throw new Error('Không lưu được yêu thích. Hãy cho phép trình duyệt lưu dữ liệu.'); }
        __stockflowApp.wishlistIds = next;
        __stockflowApp.syncWishlist();
        __stockflowApp.notify('success', selected ? 'Đã bỏ sản phẩm khỏi yêu thích.' : 'Đã thêm sản phẩm vào yêu thích.');
        if (__stockflowApp.$('#wishlist-dialog').open) await __stockflowApp.loadWishlist();
    }

async function loadWishlist() {
        const version = ++__stockflowApp.wishlistLoadVersion;
        const owner = __stockflowApp.wishlistOwner;
        const ids = [...__stockflowApp.wishlistIds];
        const target = __stockflowApp.$('#wishlist-content');
        if (!ids.length) {
            target.innerHTML = '<div class="empty-state"><h3>Chưa có sản phẩm yêu thích</h3><p>Bấm trái tim trên sản phẩm để lưu vào đây.</p></div>';
            return;
        }
        target.innerHTML = '<p class="subtle">Đang tải sản phẩm yêu thích…</p>';
        const results = await Promise.allSettled(ids.map(id => __stockflowApp.api('/products/' + id, { anonymous: true })));
        if (version !== __stockflowApp.wishlistLoadVersion || owner !== __stockflowApp.wishlistOwner || !__stockflowApp.$('#wishlist-dialog').open) return;
        const products = [];
        const unavailable = [];
        results.forEach((result, index) => {
            if (result.status === 'fulfilled' && result.value?.status === 'ACTIVE') {
                __stockflowApp.state.products.set(result.value.id, result.value);
                products.push(result.value);
            } else unavailable.push(ids[index]);
        });
        target.innerHTML = '<div class="wishlist-grid">' + __stockflowApp.productCards(products) + '</div>'
            + unavailable.map(id => '<div class="wishlist-unavailable">Không tải được sản phẩm #' + id
                + '<button type="button" class="button secondary small" data-action="toggle-wishlist" data-id="' + id + '">Bỏ khỏi yêu thích</button></div>').join('');
        __stockflowApp.syncWishlist();
    }

export function register() {
Object.defineProperties(__stockflowApp, {
"renderDiscoveryCategories": { get: () => renderDiscoveryCategories },
"loadBestsellers": { get: () => loadBestsellers },
"loadBrands": { get: () => loadBrands },
"renderProductBrands": { get: () => renderProductBrands },
"renderCatalogBrandOptions": { get: () => renderCatalogBrandOptions },
"renderCatalogBrandChips": { get: () => renderCatalogBrandChips },
"applyCatalogBrand": { get: () => applyCatalogBrand },
"brandLogoMarkup": { get: () => brandLogoMarkup },
"renderAdminBrands": { get: () => renderAdminBrands },
"renderCategoryChips": { get: () => renderCategoryChips },
"categoryAncestors": { get: () => categoryAncestors },
"categoryPath": { get: () => categoryPath },
"rootCategories": { get: () => rootCategories },
"orderedCategories": { get: () => orderedCategories },
"categoryBelongsTo": { get: () => categoryBelongsTo },
"categoryPriceRanges": { get: () => categoryPriceRanges },
"shopCategoryIcon": { get: () => shopCategoryIcon },
"renderShopCategoryMenu": { get: () => renderShopCategoryMenu },
"openCategoryPreview": { get: () => openCategoryPreview },
"previewShopCategory": { get: () => previewShopCategory },
"renderShopCategoryMenuDetail": { get: () => renderShopCategoryMenuDetail },
"categoryMenuContent": { get: () => categoryMenuContent },
"renderHomeCategoryState": { get: () => renderHomeCategoryState },
"previewHomeCategory": { get: () => previewHomeCategory },
"closeHomeCategoryMenu": { get: () => closeHomeCategoryMenu },
"renderMenuChildGroups": { get: () => renderMenuChildGroups },
"renderMenuBrands": { get: () => renderMenuBrands },
"canUseHomeCategoryMenu": { get: () => canUseHomeCategoryMenu },
"isShopCategoryTarget": { get: () => isShopCategoryTarget },
"fitHomeCategoryMenu": { get: () => fitHomeCategoryMenu },
"setShopCategoryMenu": { get: () => setShopCategoryMenu },
"browseShopCategory": { get: () => browseShopCategory },
"QUICK_PRICE_RANGES": { get: () => QUICK_PRICE_RANGES },
"PRICE_SLIDER_STEP": { get: () => PRICE_SLIDER_STEP },
"PRICE_SLIDER_DEFAULT_MAX": { get: () => PRICE_SLIDER_DEFAULT_MAX },
"MAX_CATALOG_PRICE": { get: () => MAX_CATALOG_PRICE },
"priceSliderDrag": { get: () => priceSliderDrag, set: value => { priceSliderDrag = value; } },
"sameCatalogPrice": { get: () => sameCatalogPrice },
"syncQuickPriceChips": { get: () => syncQuickPriceChips },
"applyQuickPrice": { get: () => applyQuickPrice },
"syncCatalogPriceFields": { get: () => syncCatalogPriceFields },
"syncCatalogPriceSlider": { get: () => syncCatalogPriceSlider },
"updatePriceDraftFromSlider": { get: () => updatePriceDraftFromSlider },
"applyCatalogPriceDraft": { get: () => applyCatalogPriceDraft },
"validateCatalogPriceFields": { get: () => validateCatalogPriceFields },
"movePriceTrackPointer": { get: () => movePriceTrackPointer },
"priceSlider": { get: () => priceSlider },
"cancelPriceTrackPointer": { get: () => cancelPriceTrackPointer },
"reloadCatalogFilters": { get: () => reloadCatalogFilters },
"scrollToCatalogResults": { get: () => scrollToCatalogResults },
"clearCatalogFilter": { get: () => clearCatalogFilter },
"renderCatalogActiveFilters": { get: () => renderCatalogActiveFilters },
"loadBranches": { get: () => loadBranches },
"loadOperatingWarehouses": { get: () => loadOperatingWarehouses },
"renderWarehouses": { get: () => renderWarehouses },
"loadProductOptions": { get: () => loadProductOptions },
"renderProductOptions": { get: () => renderProductOptions },
"warehouseName": { get: () => warehouseName },
"normalizeProductName": { get: () => normalizeProductName },
"safeProductImageUrl": { get: () => safeProductImageUrl },
"productImage": { get: () => productImage },
"closeSearchSuggestions": { get: () => closeSearchSuggestions },
"canSuggestProducts": { get: () => canSuggestProducts },
"updateSearchSuggestionsLayout": { get: () => updateSearchSuggestionsLayout },
"renderSearchSuggestionsLoading": { get: () => renderSearchSuggestionsLoading },
"renderSearchSuggestions": { get: () => renderSearchSuggestions },
"queueSearchSuggestions": { get: () => queueSearchSuggestions },
"loadSearchSuggestions": { get: () => loadSearchSuggestions },
"selectSearchSuggestion": { get: () => selectSearchSuggestion },
"productArt": { get: () => productArt },
"renderHeroShowcase": { get: () => renderHeroShowcase },
"productStock": { get: () => productStock },
"refreshProductStock": { get: () => refreshProductStock },
"loadProductAvailability": { get: () => loadProductAvailability },
"renderProductBranchAvailability": { get: () => renderProductBranchAvailability },
"renderProductBreadcrumb": { get: () => renderProductBreadcrumb },
"productCards": { get: () => productCards },
"wishlistButton": { get: () => wishlistButton },
"syncWishlist": { get: () => syncWishlist },
"toggleWishlist": { get: () => toggleWishlist },
"loadWishlist": { get: () => loadWishlist }
});
}

export function initializeFeature() {
const filtersPanel = __stockflowApp.$('#catalog-filters-panel');
const compactFilters = window.matchMedia('(max-width: 900px)');
const syncFiltersPanel = () => { filtersPanel.open = !compactFilters.matches; };
syncFiltersPanel();
compactFilters.addEventListener('change', syncFiltersPanel);
(QUICK_PRICE_RANGES = {
        all: { min: '', max: '' },
        'under-500': { min: '', max: '499999.99' },
        '500-2000': { min: '500000', max: '2000000' },
        '2000-5000': { min: '2000000', max: '5000000' },
        'over-5000': { min: '5000000.01', max: '' },
    });
(PRICE_SLIDER_STEP = 50_000);
(PRICE_SLIDER_DEFAULT_MAX = 50_000_000);
(MAX_CATALOG_PRICE = 9_999_999_999.99);
(priceSliderDrag = null);
document.addEventListener('input', (event) => {
        if (['catalog-min-price', 'catalog-max-price'].includes(event.target.id)) {
            __stockflowApp.validateCatalogPriceFields();
            __stockflowApp.syncCatalogPriceSlider();
        } else if (['catalog-price-low', 'catalog-price-high'].includes(event.target.id)) {
            __stockflowApp.updatePriceDraftFromSlider(event.target.id);
        }
    });
document.addEventListener('change', (event) => {
        if (['catalog-price-low', 'catalog-price-high'].includes(event.target.id)) __stockflowApp.execute(__stockflowApp.applyCatalogPriceDraft);
    });
(priceSlider = __stockflowApp.$('#catalog-price-slider'));
__stockflowApp.priceSlider.addEventListener('pointerdown', (event) => {
        if (event.button !== 0 || event.isPrimary === false || event.target.matches('input')) return;
        const box = __stockflowApp.priceSlider.getBoundingClientRect();
        const ceiling = Number(__stockflowApp.$('#catalog-price-high').max);
        const value = Math.max(0, Math.min(1, (event.clientX - box.left - 10) / (box.width - 20))) * ceiling;
        const low = Number(__stockflowApp.$('#catalog-price-low').value);
        const high = Number(__stockflowApp.$('#catalog-price-high').value);
        const useLow = low === high ? value < low : Math.abs(value - low) < Math.abs(value - high);
        __stockflowApp.priceSliderDrag = {
            id: useLow ? 'catalog-price-low' : 'catalog-price-high',
            pointerId: event.pointerId,
            ceiling,
            minimum: __stockflowApp.$('#catalog-min-price').value,
            maximum: __stockflowApp.$('#catalog-max-price').value,
        };
        event.preventDefault();
        __stockflowApp.priceSlider.setPointerCapture(event.pointerId);
        __stockflowApp.$('#' + __stockflowApp.priceSliderDrag.id).focus({ preventScroll: true });
        __stockflowApp.movePriceTrackPointer(event);
    });
__stockflowApp.priceSlider.addEventListener('pointermove', __stockflowApp.movePriceTrackPointer);
__stockflowApp.priceSlider.addEventListener('pointerup', (event) => {
        if (!__stockflowApp.priceSliderDrag || __stockflowApp.priceSliderDrag.pointerId !== event.pointerId) return;
        __stockflowApp.movePriceTrackPointer(event);
        __stockflowApp.priceSliderDrag = null;
        if (__stockflowApp.priceSlider.hasPointerCapture(event.pointerId)) __stockflowApp.priceSlider.releasePointerCapture(event.pointerId);
        __stockflowApp.execute(__stockflowApp.applyCatalogPriceDraft);
    });
__stockflowApp.priceSlider.addEventListener('pointercancel', __stockflowApp.cancelPriceTrackPointer);
__stockflowApp.priceSlider.addEventListener('lostpointercapture', __stockflowApp.cancelPriceTrackPointer);
document.addEventListener('pointerover', (event) => {
        const button = event.target.closest('#category-menu-list [data-menu-category]');
        if (button && __stockflowApp.state.categoryMenuOpen && event.pointerType !== 'touch')
            __stockflowApp.previewShopCategory(button.dataset.menuCategory);
        const homeButton = event.target.closest('#home-category-list [data-menu-category]');
        if (homeButton && event.pointerType !== 'touch') __stockflowApp.previewHomeCategory(homeButton.dataset.menuCategory);
    });
document.addEventListener('pointerout', (event) => {
        if (
            !__stockflowApp.state.categoryMenuInline &&
            __stockflowApp.state.homeCategoryId !== null &&
            event.target.closest('#home-categories') &&
            !event.relatedTarget?.closest('#home-categories')
        )
            __stockflowApp.closeHomeCategoryMenu();
    });
document.addEventListener('focusin', (event) => {
        const button = event.target.closest('#category-menu-list [data-menu-category]');
        if (button && __stockflowApp.state.categoryMenuOpen) __stockflowApp.previewShopCategory(button.dataset.menuCategory);
        const homeButton = event.target.closest('#home-category-list [data-menu-category]');
        if (homeButton) __stockflowApp.previewHomeCategory(homeButton.dataset.menuCategory);
    });
document.addEventListener('focusout', (event) => {
        if (
            __stockflowApp.state.categoryMenuOpen &&
            __stockflowApp.isShopCategoryTarget(event.target) &&
            event.relatedTarget &&
            !__stockflowApp.isShopCategoryTarget(event.relatedTarget)
        ) {
            __stockflowApp.setShopCategoryMenu(false);
        }
        if (
            !__stockflowApp.state.categoryMenuInline &&
            __stockflowApp.state.homeCategoryId !== null &&
            event.target.closest('#home-categories') &&
            event.relatedTarget &&
            !event.relatedTarget.closest('#home-categories')
        )
            __stockflowApp.closeHomeCategoryMenu();
    });
document.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && __stockflowApp.state.categoryMenuOpen) {
            event.preventDefault();
            __stockflowApp.setShopCategoryMenu(false, { restoreFocus: true });
        } else if (event.key === 'Escape' && __stockflowApp.state.homeCategoryId !== null) {
            event.preventDefault();
            __stockflowApp.closeHomeCategoryMenu({ restoreFocus: true });
        } else if (event.key === 'ArrowDown' && event.target.id === 'category-menu-toggle') {
            event.preventDefault();
            __stockflowApp.setShopCategoryMenu(true);
            __stockflowApp.$(__stockflowApp.state.categoryMenuInline ? '#home-category-list .active' : '#category-menu-list .active')?.focus();
        } else if (
            ['ArrowDown', 'ArrowUp'].includes(event.key) &&
            event.target.matches('#category-menu-list button, #home-category-list button')
        ) {
            event.preventDefault();
            const buttons = __stockflowApp.$$('button', event.target.closest('nav'));
            buttons[
                (buttons.indexOf(event.target) + (event.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length
            ].focus();
        }
    });
__stockflowApp.$('#catalog-query').addEventListener('compositionstart', () => {
        __stockflowApp.searchSuggestions.composing = true;
        __stockflowApp.closeSearchSuggestions();
    });
__stockflowApp.$('#catalog-query').addEventListener('compositionend', () => {
        __stockflowApp.searchSuggestions.composing = false;
        __stockflowApp.queueSearchSuggestions();
    });
__stockflowApp.$('#catalog-query').addEventListener('input', () => __stockflowApp.queueSearchSuggestions());
__stockflowApp.$('#catalog-query').addEventListener('focus', () => {
        // Trở lại cửa sổ không tải lại một danh sách đang mở hoặc làm mất lựa chọn bằng bàn phím.
        if (__stockflowApp.$('#search-suggestions').hidden) __stockflowApp.queueSearchSuggestions();
    });
__stockflowApp.$('#catalog-query').addEventListener('click', () => {
        if (__stockflowApp.$('#search-suggestions').hidden) __stockflowApp.queueSearchSuggestions();
    });
__stockflowApp.$('#catalog-query').addEventListener('search', () => {
        if (!__stockflowApp.$('#catalog-query').value.trim()) __stockflowApp.closeSearchSuggestions();
    });
__stockflowApp.$('#catalog-query').addEventListener('keydown', (event) => {
        if (event.isComposing || __stockflowApp.searchSuggestions.composing || event.keyCode === 229) return;
        if (event.key === 'Escape' && !__stockflowApp.$('#search-suggestions').hidden) {
            event.preventDefault();
            event.stopPropagation();
            __stockflowApp.closeSearchSuggestions();
        } else if (['ArrowDown', 'ArrowUp'].includes(event.key)) {
            if (!__stockflowApp.$('#catalog-query').value.trim()) return;
            event.preventDefault();
            if (__stockflowApp.$('#search-suggestions').hidden) __stockflowApp.queueSearchSuggestions({ immediate: true });
            else
                __stockflowApp.selectSearchSuggestion(
                    event.key === 'ArrowUp' && __stockflowApp.searchSuggestions.active < 0
                        ? __stockflowApp.searchSuggestions.items.length - 1
                        : __stockflowApp.searchSuggestions.active + (event.key === 'ArrowDown' ? 1 : -1),
                );
        } else if (event.key === 'Enter') {
            const option = __stockflowApp.$('#search-suggestion-' + __stockflowApp.searchSuggestions.active);
            if (option) {
                event.preventDefault();
                option.click();
            } else __stockflowApp.closeSearchSuggestions();
        }
    });
document.addEventListener('pointerdown', (event) => {
        if (!event.target.closest('#catalog-filter')) __stockflowApp.closeSearchSuggestions();
        else if (event.target.closest('.search-suggestion') && event.button === 0) event.preventDefault();
    });
document.addEventListener('focusin', (event) => {
        if (!event.target.closest('#catalog-filter')) __stockflowApp.closeSearchSuggestions();
    });
window.addEventListener('resize', __stockflowApp.updateSearchSuggestionsLayout, { passive: true });
window.addEventListener('scroll', __stockflowApp.updateSearchSuggestionsLayout, { passive: true });
window.visualViewport?.addEventListener('resize', __stockflowApp.updateSearchSuggestionsLayout, { passive: true });
window.visualViewport?.addEventListener('scroll', __stockflowApp.updateSearchSuggestionsLayout, { passive: true });
}
