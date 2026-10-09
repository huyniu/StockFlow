/* Product-card browser checks against isolated API fixtures; never writes live data. */
const fs = require('node:fs');
const http = require('node:http');
const path = require('node:path');
const { spawn, execFileSync } = require('node:child_process');
const assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..');
const assets = path.join(root, 'src/main/resources/static');
const chrome = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const before = process.argv.includes('--before');
const stage = before ? 'before' : 'after';
const evidence = path.join(root, 'target/product-card-verification');
fs.mkdirSync(evidence, { recursive: true });
const image = '/assets/stockflow-icon.svg';
const common = { category_id: 1, category_name: 'Điện thoại', brand_id: 1, brand_name: 'Apple',
    status: 'ACTIVE', warehouse_id: 1, available_quantity: 25, image_url: image, image_urls: [],
    specifications: [], variants: [], versions: [] };
const products = [
    { ...common, id: 1, sku: 'IPHONE-ROOT-01', name: 'iPhone 17 Pro Max với tên sản phẩm dài để kiểm tra đủ hai dòng trên điện thoại',
        unit_price: 29990000, min_price: 29990000, max_price: 49990000,
        versions: [{ id: 11, name: '256GB' }, { id: 12, name: '512GB' }],
        variants: [
            { id: 21, sku_product_id: 1, sku: 'IPHONE-256-BLUE', version_id: 11, version_name: '256GB', color_name: 'Xanh', color_hex: '#2563eb', unit_price: 29990000 },
            { id: 22, sku_product_id: 101, sku: 'IPHONE-256-BLACK', version_id: 11, version_name: '256GB', color_name: 'Đen', color_hex: '#242424', unit_price: 30990000 },
            { id: 23, sku_product_id: 102, sku: 'IPHONE-512-BLUE', version_id: 12, version_name: '512GB', color_name: 'Xanh', color_hex: '#2563eb', unit_price: 48990000 },
            { id: 24, sku_product_id: 103, sku: 'IPHONE-512-BLACK', version_id: 12, version_name: '512GB', color_name: 'Đen', color_hex: '#242424', unit_price: 49990000 },
        ].map(row => ({ ...row, status: 'ACTIVE', image_url: image, image_urls: [] })) },
    { ...common, id: 2, sku: 'SHORT-02', name: 'Tai nghe Bluetooth', unit_price: 990000 },
    { ...common, id: 3, sku: 'LONG-03', name: 'Điện thoại thông minh với màn hình lớn và dung lượng cao dành cho mọi nhu cầu sử dụng', unit_price: 19990000 },
    { ...common, id: 4, sku: 'DENSE-04', name: 'TênSảnPhẩmLiềnKhôngCóKhoảngTrắngDùngĐểKiểmTraKhôngTrànKhungTrênMànHìnhNhỏ', unit_price: 99999999 },
];
for (const product of products) {
    product.min_price ??= product.unit_price;
    product.max_price ??= product.unit_price;
}
// These files were clean at task start. Serve their original revision for repeatable before screenshots.
const originalAssets = before ? new Map(['/styles.css', '/assets/modules/catalog-navigation.js', '/assets/fragments/storefront.html',
    '/assets/modules/core.js', '/assets/modules/product-detail.js', '/assets/modules/cart-checkout.js']
    .map(route => [route, execFileSync('git', ['show', 'HEAD:src/main/resources/static' + route], { cwd: root })])) : new Map();
const page = content => ({ content, total_elements: content.length, total_pages: 1, page: 0, size: 20, last: true });
const apiRequests = [];
function fixture(url) {
    const route = url.pathname;
    if (route.endsWith('/auth/google/config')) return { enabled: false, client_id: '' };
    if (route.endsWith('/categories')) return [{ id: 1, name: 'Điện thoại', slug: 'dien-thoai', parent_id: null }];
    if (route.endsWith('/brands')) return [{ id: 1, name: 'Apple', slug: 'apple', category_ids: [1] }];
    if (route.endsWith('/storefront/branches')) return [{ id: 1, name: 'Kho Hà Nội', address: 'Hà Nội', status: 'ACTIVE' }];
    if (route.endsWith('/storefront/contact')) return { zalo_url: 'https://zalo.me/0968935896' };
    if (route.endsWith('/products/specification-options')) return [];
    if (route.endsWith('/storefront/bestsellers')) return [];
    if (route.endsWith('/products')) return page(products);
    if (route.endsWith('/availability')) return [1, 101, 102, 103].map(product_id => ({ product_id, warehouse_id: 1, in_stock: true, available_quantity: 25 }));
    const match = route.match(/\/products\/(\d+)$/);
    if (match) {
        const id = Number(match[1]);
        const product = products.find(product => product.id === id);
        if (product) return product;
        const variant = products[0].variants.find(row => row.sku_product_id === id);
        if (variant) return { ...products[0], ...variant, id, parent_product_id: 1,
            variants: products[0].variants, versions: products[0].versions };
        return [];
    }
    if (route.endsWith('/orders/my')) return page([]);
    return [];
}
const server = http.createServer((request, response) => {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname.startsWith('/api/')) {
        apiRequests.push({ method: request.method, path: url.pathname });
        response.writeHead(200, { 'Content-Type': 'application/json' });
        response.end(JSON.stringify(fixture(url))); return;
    }
    const route = /^\/san-pham\/\d+$/.test(url.pathname) || url.pathname === '/' ? '/index.html' : url.pathname;
    const file = path.resolve(assets, '.' + route);
    if (!file.startsWith(assets + path.sep) || !fs.existsSync(file)) { response.writeHead(404); response.end(); return; }
    response.writeHead(200, { 'Content-Type': ({ '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
        '.js': 'text/javascript; charset=utf-8', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream' });
    response.end(originalAssets.get(route) || fs.readFileSync(file));
});
let browser, socket, session, sequence = 0;
const pending = new Map(), exceptions = [], metrics = [];
function send(method, params = {}, scoped = true) {
    return new Promise((resolve, reject) => {
        const id = ++sequence;
        const timer = setTimeout(() => { pending.delete(id); reject(Error('CDP timeout: ' + method)); }, 15000);
        pending.set(id, { resolve: value => { clearTimeout(timer); resolve(value); }, reject: error => { clearTimeout(timer); reject(error); } });
        socket.send(JSON.stringify({ id, method, params, ...(scoped && session ? { sessionId: session } : {}) }));
    });
}
async function evaluate(expression) {
    const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    if (result.exceptionDetails) throw Error(JSON.stringify(result.exceptionDetails));
    return result.result.value;
}
async function until(expression) {
    const end = Date.now() + 12000;
    while (Date.now() < end) {
        if (await evaluate(`document.body && (${expression})`)) return;
        await new Promise(resolve => setTimeout(resolve, 80));
    }
    throw Error('UI timeout: ' + expression + ' ' + JSON.stringify(exceptions));
}
async function click(selector) {
    await evaluate(`document.querySelector(${JSON.stringify(selector)}).scrollIntoView({block:'center',behavior:'instant'})`);
    const point = await evaluate(`(()=>{const r=document.querySelector(${JSON.stringify(selector)}).getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2};})()`);
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', ...point });
    await send('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...point });
    await send('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...point });
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: 0, y: 0 });
}
async function screenshot(name) {
    const result = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    fs.writeFileSync(path.join(evidence, name + '.png'), Buffer.from(result.data, 'base64'));
}
async function cardMetrics() {
    return evaluate(`(()=>{const rect=e=>{const r=e.getBoundingClientRect();return {x:r.x,y:r.y,width:r.width,height:r.height,bottom:r.bottom,right:r.right};};
      return {viewport:innerWidth,pageWidth:document.documentElement.scrollWidth,loginName:document.querySelector('#open-login').getAttribute('aria-label'),
        columns:getComputedStyle(document.querySelector('#catalog-grid')).gridTemplateColumns,
        cards:[...document.querySelectorAll('#catalog-grid .product-card')].map(card=>{
          const name=card.querySelector('h3'),price=card.querySelector('.product-price'),button=card.querySelector('.add-button'),range=document.createRange();
          range.selectNodeContents(button);
          return {card:rect(card),name:rect(name),nameText:name.textContent,clamp:getComputedStyle(name).webkitLineClamp,lineHeight:parseFloat(getComputedStyle(name).lineHeight),
            sku:!!card.querySelector('.product-sku'),image:rect(card.querySelector('.product-card-img-wrap')),preview:card.querySelector('.product-color-preview')?rect(card.querySelector('.product-color-preview')):null,
            price:rect(price),priceText:price.textContent,priceSize:parseFloat(getComputedStyle(price).fontSize),priceClient:price.clientWidth,priceScroll:price.scrollWidth,
            button:rect(button),buttonText:button.textContent.trim(),buttonTextRect:rect({getBoundingClientRect:()=>range.getBoundingClientRect()}),buttonSize:parseFloat(getComputedStyle(button).fontSize),
            buttonClient:button.clientWidth,buttonScroll:button.scrollWidth};})};})()`);
}
async function surfaceMetrics() {
    // Check the CSS scope explicitly; the broader UI suite navigates the real auth/admin screens.
    return evaluate(`(()=>{const classes=document.body.className,read=()=>({background:getComputedStyle(document.body).backgroundColor,
        aura:getComputedStyle(document.querySelector('.aura-bg')).display,
        layerBackground:getComputedStyle(document.querySelector('.aura-layer-1')).backgroundImage,
        layerBlend:getComputedStyle(document.querySelector('.aura-layer-1')).mixBlendMode,
        layerBlur:getComputedStyle(document.querySelector('.aura-layer-1')).filter});
        document.body.classList.remove('portal-open','auth-page-open');const storefront=read();
        document.body.classList.add('auth-page-open');const auth=read();
        document.body.classList.remove('auth-page-open');document.body.classList.add('portal-open');const portal=read();
        document.body.className=classes;return {storefront,auth,portal};})()`);
}
(async () => {
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const base = 'http://127.0.0.1:' + server.address().port;
    browser = spawn(chrome, ['--headless=new', '--no-first-run', '--no-default-browser-check', '--disable-background-networking',
        '--disable-component-update', '--remote-debugging-port=0', '--user-data-dir=' + path.join(evidence, 'chrome-profile-' + process.pid), 'about:blank'],
        { windowsHide: true, stdio: ['ignore', 'ignore', 'pipe'] });
    const endpoint = await new Promise((resolve, reject) => {
        let output = ''; const timer = setTimeout(() => reject(Error('Chrome startup timeout')), 15000);
        browser.stderr.on('data', chunk => { output += chunk; const match = output.match(/DevTools listening on (ws:\/\/[^\s]+)/);
            if (match) { clearTimeout(timer); resolve(match[1]); } });
        browser.once('error', reject);
    });
    socket = new WebSocket(endpoint);
    await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
    socket.addEventListener('message', event => {
        const value = JSON.parse(event.data);
        if (value.id) { const handler = pending.get(value.id); pending.delete(value.id); if (value.error) handler?.reject(Error(value.error.message)); else handler?.resolve(value.result); }
        if (value.method === 'Runtime.exceptionThrown') exceptions.push(value.params.exceptionDetails);
    });
    const target = await send('Target.createTarget', { url: 'about:blank' }, false);
    session = (await send('Target.attachToTarget', { targetId: target.targetId, flatten: true }, false)).sessionId;
    await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable');
    await send('Network.setBlockedURLs', { urls: ['https://*'] });
    // Measure layout without motion, then explicitly test ordinary scrolling with motion enabled.
    await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
    for (const width of [320, 375, 414, 768, 1366]) {
        await send('Emulation.setDeviceMetricsOverride', { width, height: 950, deviceScaleFactor: 1, mobile: width < 768 });
        if (width !== 320) await evaluate(`sessionStorage.removeItem('stockflow.web.cart.v1')`);
        await send('Page.navigate', { url: base + '/?categoryId=1#shop' });
        await until(`document.querySelectorAll('#catalog-grid .product-card').length===4 && document.querySelector('#catalog-grid').dataset.catalogStatus==='ready'`);
        for (const theme of ['light', 'dark']) {
            await evaluate(`if(document.documentElement.dataset.theme!=='${theme}')document.querySelector('#theme-toggle').click();document.documentElement.style.scrollBehavior='auto';
                window.scrollTo({top:scrollY+document.querySelector('#catalog-grid').getBoundingClientRect().top-document.querySelector('.shop-header').getBoundingClientRect().bottom-16,behavior:'instant'})`);
            await new Promise(resolve => setTimeout(resolve, 150));
            const result = await cardMetrics();
            result.surfaces = await surfaceMetrics();
            metrics.push({ width, theme, ...result });
            await screenshot(`${stage}-${width}-${theme}`);
            if (!before) {
                assert.ok(result.pageWidth <= width + 1, 'Page overflow at ' + width);
                assert.equal(result.loginName, 'Đăng nhập hoặc đăng ký');
                const document = await send('DOM.getDocument', { depth: 0 });
                const login = await send('DOM.querySelector', { nodeId: document.root.nodeId, selector: '#open-login' });
                const accessibility = await send('Accessibility.getPartialAXTree', { nodeId: login.nodeId, fetchRelatives: false });
                assert.equal(accessibility.nodes.find(node => node.role?.value === 'button')?.name?.value, 'Đăng nhập hoặc đăng ký');
                for (const card of result.cards) {
                    assert.equal(card.sku, false, 'SKU hidden on customer cards');
                    assert.equal(card.clamp, '2');
                    assert.ok(card.name.height <= 2 * card.lineHeight + 1, 'Name exceeds two lines');
                    assert.ok(card.image.bottom <= card.name.y && card.name.bottom <= card.price.y, 'Image/name/price order');
                    if (card.preview) assert.ok(card.name.bottom <= card.preview.y && card.preview.bottom <= card.price.y, 'Preview before price');
                    assert.ok(card.price.bottom <= card.button.y, 'Price before action');
                    assert.ok(card.priceSize >= (width <= 760 ? 17 : 19), 'Price remains readable');
                    assert.ok(card.priceScroll <= card.priceClient + 1, 'Price clipping');
                    assert.ok(card.buttonScroll <= card.buttonClient + 1, 'Button overflow');
                    assert.ok(card.buttonTextRect.right <= card.button.right - 3, 'Button label clipped');
                    if (width <= 760) {
                        assert.ok(card.buttonSize >= 13 && card.buttonSize <= 14, 'Mobile button font 13–14px');
                        assert.ok(card.button.height >= 44, 'Mobile button tap target');
                    }
                }
                assert.equal(result.cards[0].buttonText, 'Chọn phiên bản');
                assert.equal(result.surfaces.storefront.aura, 'none', 'No decorative aura behind the storefront');
                assert.equal(result.surfaces.storefront.background, theme === 'dark' ? 'rgb(11, 18, 32)' : 'rgb(248, 250, 252)');
                const surfaceBaselineFile = path.join(evidence, 'before-metrics.json');
                if (fs.existsSync(surfaceBaselineFile)) {
                    const baseline = JSON.parse(fs.readFileSync(surfaceBaselineFile)).find(row => row.width === width && row.theme === theme);
                    assert.deepEqual(result.surfaces.auth, baseline.surfaces.auth, 'Authentication background unchanged');
                    assert.deepEqual(result.surfaces.portal, baseline.surfaces.portal, 'Admin background unchanged');
                }
                for (const card of result.cards.slice(1)) assert.equal(card.buttonText, 'Thêm vào giỏ');
                const rows = new Map();
                for (const card of result.cards) { const key = Math.round(card.card.y); if (!rows.has(key)) rows.set(key, []); rows.get(key).push(card); }
                for (const row of rows.values()) {
                    assert.ok(Math.max(...row.map(card => card.button.height)) - Math.min(...row.map(card => card.button.height)) < 1, 'Uniform button height');
                    assert.ok(Math.max(...row.map(card => card.button.y)) - Math.min(...row.map(card => card.button.y)) < 1, 'Aligned row actions');
                }
                const baselineFile = path.join(evidence, 'before-metrics.json');
                if (width === 1366 && fs.existsSync(baselineFile)) {
                    const baseline = JSON.parse(fs.readFileSync(baselineFile)).find(row => row.width === width && row.theme === theme);
                    assert.equal(result.columns, baseline.columns, 'Desktop column widths preserved');
                    result.cards.forEach((card, index) => {
                        assert.equal(card.image.height, baseline.cards[index].image.height, 'Desktop image height preserved');
                        assert.equal(card.buttonSize, baseline.cards[index].buttonSize, 'Desktop button size preserved');
                        assert.equal(card.priceSize, baseline.cards[index].priceSize, 'Desktop price size preserved');
                    });
                }
            }
        }
        if (!before) {
            await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'no-preference' }] });
            await send('Page.navigate', { url: base + '/?categoryId=1#shop' });
            await until(`document.querySelectorAll('#catalog-grid .product-card').length===4`);
            for (const theme of ['light', 'dark']) {
                await evaluate(`if(document.documentElement.dataset.theme!=='${theme}')document.querySelector('#theme-toggle').click()`);
                for (const position of ['0', 'document.documentElement.scrollHeight', '0']) {
                    await evaluate(`window.scrollTo({top:${position},behavior:'instant'})`);
                    await new Promise(resolve => setTimeout(resolve, 600));
                    const visibility = await evaluate(`[...document.querySelectorAll('.product-card')].map(card=>({
                        opacity:getComputedStyle(card).opacity,pointerEvents:getComputedStyle(card).pointerEvents,reveal:card.classList.contains('scroll-reveal')}))`);
                    assert.ok(visibility.length >= 4);
                    for (const card of visibility) {
                        assert.equal(card.opacity, '1', 'Loaded cards always visible with normal motion at ' + width);
                        assert.equal(card.pointerEvents, 'auto', 'Loaded cards remain interactive');
                        assert.equal(card.reveal, false, 'Cards excluded from the reveal observer');
                    }
                }
            }
            await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
            const badge = Number(await evaluate(`document.querySelector('#cart-badge').textContent`));
            await evaluate(`document.querySelector('#catalog-grid [data-product-link][data-product-id="2"]').focus()`);
            assert.ok(await evaluate(`getComputedStyle(document.activeElement,'::after').outlineStyle!=='none'`), 'Keyboard focus stays visible');
            assert.equal(await evaluate(`getComputedStyle(document.activeElement.closest('.product-card')).transform`), 'none', 'Reduced motion disables card lift');
            await send('Input.dispatchKeyEvent', { type: 'keyDown', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 });
            await send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Enter', code: 'Enter', windowsVirtualKeyCode: 13 });
            await until(`document.querySelector('#shop-product-detail-body')?.dataset.productId==='2' && !document.querySelector('#shop-product').hidden`);
            assert.ok((await evaluate(`document.querySelector('.shop-product-sku').textContent`)).includes('SHORT-02'));
            await send('Page.navigate', { url: base + '/?categoryId=1#shop' });
            await until(`document.querySelectorAll('#catalog-grid .product-card').length===4`);
            await click('#catalog-grid [data-action="add-cart"][data-id="2"]');
            await until(`Number(document.querySelector('#cart-badge').textContent)===${badge + 1}`);
            assert.equal(await evaluate(`document.querySelector('#api-notice').hidden`), true, 'Cart success has no duplicate page banner');
            assert.equal(await evaluate(`document.querySelectorAll('#toasts .toast').length`), 1, 'One cart success toast');
            assert.ok((await evaluate(`document.querySelector('#toasts .toast').textContent`)).includes(products[1].name));
            assert.equal(await evaluate(`document.querySelector('#toasts').getAttribute('aria-live')`), 'polite', 'Cart feedback is announced');
            await screenshot(`cart-feedback-${width}`);
            await click('#catalog-grid [data-action="add-cart"][data-id="1"]');
            await until(`!document.querySelector('#shop-product').hidden && document.querySelector('[data-action="select-product-version"][data-id="12"]')`);
            assert.equal(Number(await evaluate(`document.querySelector('#cart-badge').textContent`)), badge + 1, 'Variant card opens details without adding to cart');
            assert.ok((await evaluate(`document.querySelector('#shop-product-name').textContent`)).includes(products[0].name), 'Full name on detail page');
            await click('[data-action="select-product-version"][data-id="12"]');
            await click('[data-action="select-product-color"][data-id="103"]');
            await until(`document.querySelector('#shop-product-detail-body').dataset.skuId==='103'`);
            assert.ok((await evaluate(`document.querySelector('.shop-product-sku').textContent`)).includes('IPHONE-512-BLACK'));
            await click('#shop-product-add-form button[type="submit"]');
            await until(`Number(document.querySelector('#cart-badge').textContent)===${badge + 2}`);
            assert.equal(await evaluate(`document.querySelector('#api-notice').hidden`), true, 'Variant cart success has no duplicate banner');
            const selected = await evaluate(`(async()=>{const {app}=await import('/assets/modules/context.js');const item=app.state.cart.get(103);return {version:item.product.version_name,color:item.product.color_name,sku:item.product.sku,price:item.product.unit_price};})()`);
            assert.deepEqual(selected, { version: '512GB', color: 'Đen', sku: 'IPHONE-512-BLACK', price: 49990000 });
            assert.ok(await evaluate(`document.documentElement.scrollWidth<=innerWidth+1`), 'Details no overflow at ' + width);
            await screenshot(`detail-${width}`);
        }
        console.log(`${before ? 'CAPTURED' : 'PASS'}: ${width}px, light/dark${before ? '' : ', layout + scroll visibility + keyboard + details + version/color + cart'}`);
    }
    await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'no-preference' }] });
    for (const width of [375, 1366]) {
        await send('Emulation.setDeviceMetricsOverride', { width, height: 950, deviceScaleFactor: 1, mobile: width < 768 });
        await evaluate(`sessionStorage.removeItem('stockflow.web.cart.v1')`);
        await send('Page.navigate', { url: base + '/' });
        await until(`document.querySelectorAll('#catalog-grid .product-card').length===4 && document.querySelector('a.hero-device')`);
        await new Promise(resolve => setTimeout(resolve, 750));
        for (const theme of ['light', 'dark']) {
            await evaluate(`if(document.documentElement.dataset.theme!=='${theme}')document.querySelector('#theme-toggle').click();window.scrollTo({top:0,behavior:'instant'})`);
            await new Promise(resolve => setTimeout(resolve, 400));
            if (!before) {
                assert.equal(await evaluate(`document.documentElement.dataset.theme`), theme, 'Theme toggle stays applied');
                assert.equal(await evaluate(`getComputedStyle(document.querySelector('.hero-shop-button')).backgroundColor`), theme === 'dark' ? 'rgb(96, 165, 250)' : 'rgb(37, 99, 235)', 'Hero CTA retains brand blue');
                assert.equal(await evaluate(`getComputedStyle(document.querySelector('.tech-hero')).backgroundImage`), 'none');
                assert.equal(await evaluate(`getComputedStyle(document.querySelector('.hero-showcase'),'::before').content`), 'none');
                assert.ok(await evaluate(`[...document.querySelectorAll('a.hero-device')].every(card=>getComputedStyle(card).animationName==='none')`));
                assert.equal(await evaluate(`document.querySelectorAll('.hero-atmosphere,.hero-label,#storefront-view .eyebrow,.catalog-demo-note').length`), 0);
                assert.ok(await evaluate(`document.documentElement.scrollWidth<=innerWidth+1`));
            }
            await screenshot(`${stage}-home-${width}-${theme}`);
        }
    }
    assert.equal(exceptions.length, 0, JSON.stringify(exceptions));
    assert.ok(apiRequests.every(request => request.method === 'GET'), 'No order/inventory/API writes');
    fs.writeFileSync(path.join(evidence, `${stage}-metrics.json`), JSON.stringify(metrics, null, 2));
    console.log('Evidence: ' + evidence);
})().catch(async error => {
    try { await screenshot('failed-' + stage); } catch {}
    console.error(error); process.exitCode = 1;
}).finally(async () => {
    try { if (socket?.readyState === 1) await send('Browser.close', {}, false); } catch {}
    socket?.close(); browser?.kill(); server.close();
});
