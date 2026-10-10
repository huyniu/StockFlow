/* Isolated portal API fixtures; --real-images reads only the public local catalog for photos. */
const fs = require('node:fs'), http = require('node:http'), path = require('node:path');
const { spawn } = require('node:child_process'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), assets = path.join(root, 'src/main/resources/static');
const evidence = path.join(root, 'target/portal-product-images-verification');
const chrome = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const realImages = process.argv.includes('--real-images');
const upstream = process.env.STOCKFLOW_LOCAL_URL || 'http://localhost:8080';
fs.mkdirSync(evidence, { recursive: true });
const basic = { sku: 'PHONE-01', name: 'Điện thoại kiểm tra ảnh', category_id: 1, category_name: 'Điện thoại',
    unit_price: 100000, status: 'ACTIVE', versions: [], variants: [], specifications: [] };
let products = [1, 2, 3].map(id => ({ ...basic, id, name: basic.name + ' ' + id,
    sku: 'PHONE-0' + id, image_url: '/assets/verification-blue.svg' }));
let displayProducts, stockRows, movementRows, reportRows;
const page = content => ({ content, page: 0, size: 10, total_elements: content.length, total_pages: 1, first: true, last: true });
const user = { id: 1, email: 'portal-images@example.test', full_name: 'Quản lý kiểm tra giao diện', role: 'MANAGER', status: 'ACTIVE' };
const requests = [], exceptions = [], results = [];
function fixture(url) {
    const route = url.pathname.replace('/api/v1', '');
    if (route === '/users/me') return user;
    if (route === '/auth/google/config') return { enabled: false, client_id: '' };
    if (route === '/categories') return [{ id: 1, name: 'Điện thoại', slug: 'dien-thoai' }];
    if (route === '/products') return page(products);
    if (route === '/inventories/movements') return page(movementRows);
    if (route === '/inventories') return page(stockRows);
    if (route === '/reports/top-products') return page(reportRows);
    if (route === '/reports/low-stock') return page(stockRows);
    if (route === '/reports/revenue') return page([]);
    if (route === '/reports/order-summary') return [];
    if (route === '/warehouses/operating-options' || route === '/storefront/branches')
        return [{ id: 1, name: 'Kho Tổng Hà Nội', address: 'Hà Nội', status: 'ACTIVE' }];
    if (route.startsWith('/storefront/contact')) return { zalo_url: 'https://zalo.me/0968935896' };
    if (route.startsWith('/orders')) return page([]);
    return [];
}
const server = http.createServer((request, response) => {
    const url = new URL(request.url, 'http://localhost');
    if (url.pathname.startsWith('/api/')) {
        requests.push({ method: request.method, path: url.pathname });
        if (request.method !== 'GET') { response.writeHead(405); response.end(); return; }
        response.writeHead(200, { 'Content-Type': 'application/json' });
        response.end(JSON.stringify(fixture(url))); return;
    }
    if (/^\/assets\/verification-(blue|red)\.svg$/.test(url.pathname)) {
        response.writeHead(200, { 'Content-Type': 'image/svg+xml' });
        response.end('<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect x="25" y="10" width="50" height="80" rx="8" fill="' +
            (url.pathname.includes('red') ? '#dc2626' : '#2563eb') + '"/></svg>'); return;
    }
    const file = path.resolve(assets, '.' + (url.pathname === '/' ? '/index.html' : url.pathname));
    if (!file.startsWith(assets + path.sep) || !fs.existsSync(file)) { response.writeHead(404); response.end(); return; }
    response.writeHead(200, { 'Content-Type': ({ '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
        '.js': 'text/javascript; charset=utf-8', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream' });
    response.end(fs.readFileSync(file));
});
let browser, socket, sequence = 0, session;
const pending = new Map();
function send(method, params = {}, attached = true) {
    const id = ++sequence;
    return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => { pending.delete(id); reject(Error(method + ' timed out')); }, 20000);
        pending.set(id, { resolve: value => { clearTimeout(timeout); resolve(value); }, reject: error => { clearTimeout(timeout); reject(error); } });
        socket.send(JSON.stringify({ id, method, params, ...(attached ? { sessionId: session } : {}) }));
    });
}
async function evaluate(expression) {
    const result = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true });
    if (result.exceptionDetails) throw Error(result.exceptionDetails.exception?.description || result.exceptionDetails.text);
    return result.result.value;
}
async function until(expression) {
    for (let attempt = 0; attempt < 100; attempt++) {
        if (await evaluate(expression)) return;
        await new Promise(resolve => setTimeout(resolve, 100));
    }
    throw Error('Waiting for ' + expression);
}
async function screenshot(name) {
    const result = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    fs.writeFileSync(path.join(evidence, name + '.png'), Buffer.from(result.data, 'base64'));
}
(async () => {
    if (realImages) {
        const response = await fetch(upstream + '/api/v1/products?size=100', { signal: AbortSignal.timeout(10000) });
        assert.ok(response.ok, 'Public local catalog is available');
        const catalog = await response.json();
        products = catalog.content;
        // Use different actual photos; inventory/report numbers below remain isolated fixtures.
        displayProducts = [products.find(product => product.name.includes('Sony 1000X')),
            products.find(product => product.name.includes('HP Omnibook')), products.find(product => product.name.includes('IPHONE 17'))];
        assert.ok(displayProducts.every(product => product?.image_url), 'Real catalog photos are available');
    } else displayProducts = products;
    const noPhoto = { ...basic, id: 70001, sku: 'NO-PHOTO', name: 'Sản phẩm chưa có ảnh', image_url: null };
    const brokenPhoto = { ...basic, id: 70002, sku: 'BROKEN-PHOTO', name: 'Sản phẩm có link ảnh hỏng', image_url: '/assets/verification-missing.png' };
    const color = { ...basic, id: 70003, sku: 'COLOR-RED', image_url: '/assets/verification-blue.svg',
        variants: [{ sku_product_id: 70003, sku: 'COLOR-RED', version_name: '512GB', color_name: 'Đỏ',
            status: 'ACTIVE', unit_price: 100000, image_url: '/assets/verification-red.svg', image_urls: [] }] };
    products = [...products, noPhoto, brokenPhoto, color];
    const rows = [...displayProducts, noPhoto, brokenPhoto];
    stockRows = rows.map((product, index) => ({ id: 8000 + index, product_id: product.id, product_name: product.name,
        warehouse_id: 1, warehouse_name: 'Kho Tổng Hà Nội', available_quantity: 3, reserved_quantity: 0, physical_quantity: 3,
        updated_at: '2026-10-09T03:00:00Z' }));
    movementRows = rows.map((product, index) => ({ id: 9000 + index, inventory_id: 8000 + index, product_id: product.id,
        product_name: product.name, product_sku: product.sku, image_url: product.image_url, performed_by: 1,
        type: 'GOODS_RECEIPT', quantity: 3, balance_before: 0, balance_after: 3, note: 'Dữ liệu kiểm tra giao diện',
        created_at: '2026-10-09T03:00:00Z' }));
    reportRows = rows.map(product => ({ product_id: product.id, product_name: product.name, product_sku: product.sku,
        category_name: product.category_name, total_quantity_sold: 1, total_revenue: product.unit_price }));
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const base = 'http://127.0.0.1:' + server.address().port;
    browser = spawn(chrome, ['--headless=new', '--no-first-run', '--no-default-browser-check', '--disable-background-networking',
        '--remote-debugging-port=0', '--user-data-dir=' + path.join(evidence, 'chrome-profile'), 'about:blank'],
        { windowsHide: true, stdio: ['ignore', 'ignore', 'pipe'] });
    const endpoint = await new Promise((resolve, reject) => {
        let output = ''; const timeout = setTimeout(() => reject(Error('Chrome startup timed out')), 15000);
        browser.stderr.on('data', chunk => { output += chunk; const match = output.match(/DevTools listening on (ws:\/\/[^\s]+)/);
            if (match) { clearTimeout(timeout); resolve(match[1]); } });
        browser.once('error', reject);
    });
    socket = new WebSocket(endpoint);
    await new Promise(resolve => socket.addEventListener('open', resolve, { once: true }));
    socket.addEventListener('message', event => {
        const value = JSON.parse(event.data);
        if (value.id) { const task = pending.get(value.id); pending.delete(value.id);
            if (value.error) task?.reject(Error(value.error.message)); else task?.resolve(value.result); }
        if (value.method === 'Runtime.exceptionThrown') exceptions.push(value.params.exceptionDetails);
    });
    const target = await send('Target.createTarget', { url: 'about:blank' }, false);
    session = (await send('Target.attachToTarget', { targetId: target.targetId, flatten: true }, false)).sessionId;
    await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable');
    await send('Network.setBlockedURLs', { urls: realImages ? ['*accounts.google.com*', '*fonts.googleapis.com*'] : ['https://*'] });
    await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
    await send('Page.addScriptToEvaluateOnNewDocument', { source: "sessionStorage.setItem('stockflow.web.session',JSON.stringify({token:'portal-image-fixture'}));" });
    await send('Page.navigate', { url: base + '/#portal/ledger' });
    await until("document.querySelector('#ledger-rows [data-admin-thumbnail]')?.complete && document.querySelector('#portal-ledger').hidden===false");
    // A direct ledger visit must not depend on having loaded the inventory screen.
    assert.ok((await evaluate("document.querySelector('#ledger-rows .product-cell strong').textContent")).startsWith(displayProducts[0].name));
    const helper = await evaluate(`(async()=>{const {app}=await import('/assets/modules/context.js');
        const temporary=document.createElement('div');
        temporary.innerHTML=app.productCell({product_id:70003});
        const selected={src:temporary.querySelector('img').getAttribute('src'),text:temporary.textContent};
        temporary.innerHTML=app.productCell({id:70003,inventory_id:123});
        const legacy={images:temporary.querySelectorAll('img').length,text:temporary.textContent};
        temporary.innerHTML=app.productCell({product_id:987654,product_name:'SKU không còn trong catalog',image_url:'/assets/verification-blue.svg'});
        const detached=temporary.querySelector('img').getAttribute('src');
        temporary.innerHTML=app.productCell({product_id:987654,product_name:'<script>unsafe</script>',image_url:'javascript:alert(1)'});
        return {selected,legacy,detached,unsafeImages:temporary.querySelectorAll('img').length,unsafeScripts:temporary.querySelectorAll('script').length};})()`);
    assert.equal(helper.selected.src, '/assets/verification-red.svg'); assert.match(helper.selected.text, /512GB.*Đỏ/);
    assert.equal(helper.legacy.images, 0); assert.match(helper.legacy.text, /123/);
    assert.equal(helper.detached, '/assets/verification-blue.svg'); assert.equal(helper.unsafeImages, 0); assert.equal(helper.unsafeScripts, 0);
    for (const width of [320, 375, 768, 1366, 1920]) {
        await send('Emulation.setDeviceMetricsOverride', { width, height: width < 768 ? 900 : 1100, deviceScaleFactor: 1, mobile: width < 768 });
        for (const theme of ['light', 'dark']) {
            await evaluate(`document.documentElement.dataset.theme=${JSON.stringify(theme)}`);
            for (const tab of ['ledger', 'inventory', 'reports']) {
                await evaluate(`document.querySelector('[data-portal-tab="${tab}"]').click()`);
                const bodies = tab === 'reports' ? ['top', 'low'] : [tab];
                for (const body of bodies) {
                    await until(`document.querySelectorAll('#${body}-rows .product-cell').length===5`);
                    await evaluate(`document.querySelector('#${body}-rows').scrollIntoView({block:'start',behavior:'instant'})`);
                    await until(`Array.from(document.querySelectorAll('#${body}-rows [data-admin-thumbnail]')).every(image=>image.complete)`);
                    const measured = await evaluate(`Array.from(document.querySelectorAll('#${body}-rows .product-cell')).map(cell=>{
                        const photo=cell.querySelector('img'),box=cell.querySelector('.product-symbol').getBoundingClientRect();
                        return {name:cell.querySelector('strong').textContent,photo:photo?.getAttribute('src'),loaded:!!photo?.naturalWidth,
                            hidden:photo?.hidden,width:box.width,height:box.height,icon:!!cell.querySelector('use'),decorative:cell.querySelector('.product-symbol').getAttribute('aria-hidden')};})`);
                    assert.ok(measured.slice(0,3).every(row=>row.loaded&&!row.hidden), 'Visible product photos: '+body+' '+width+' '+theme);
                    assert.ok(!measured[3].photo && measured[3].icon, 'Missing-photo fallback');
                    await until(`document.querySelector('#${body}-rows tr:last-child img').hidden`);
                    assert.ok(measured.every(row=>row.width===52 && row.height===52 && row.decorative==='true'), 'Stable accessible thumbnail boxes');
                    assert.ok(await evaluate('document.documentElement.scrollWidth<=innerWidth+1'), 'No page overflow');
                    if (width===375 || width===1366) {
                        await evaluate(`(()=>{const panel=document.querySelector('#${body}-rows').closest('.panel');
                            scrollTo({top:Math.max(0,panel.getBoundingClientRect().top+scrollY-12),behavior:'instant'});})()`);
                        await screenshot(body+'-'+width+'-'+theme);
                    }
                    results.push({width,theme,body,photos:measured});
                }
            }
            console.log('PASS '+width+'px '+theme+': inventory, ledger, top products, low stock + image fallback');
        }
    }
    assert.equal(exceptions.length, 0, 'No runtime exceptions');
    assert.ok(requests.every(request=>request.method==='GET'), 'Read-only fixture requests');
    fs.writeFileSync(path.join(evidence,'metrics.json'),JSON.stringify({realImages,photosFrom:realImages?upstream:'local SVG fixtures',
        numbersFrom:'isolated portal API fixtures',results,requests},null,2));
    console.log('PASS: exact SKU color image, direct ledger visit, uncached product, unsafe URL, missing/broken photo');
    console.log('Evidence: '+evidence);
})().catch(async error => {
    console.error(error.stack); if(socket&&session)await screenshot('failure').catch(()=>{});process.exitCode=1;
}).finally(async()=>{
    if(socket){await send('Browser.close',{},false).catch(()=>{});socket.close();}browser?.kill();server.close();
});
