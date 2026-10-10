/* Read-only browser checks: workspace UI + real catalog from the running local StockFlow API. */
const fs = require('node:fs'), http = require('node:http'), path = require('node:path');
const { spawn } = require('node:child_process'), assert = require('node:assert/strict');
const root = path.resolve(__dirname, '..'), assets = path.join(root, 'src/main/resources/static');
const upstream = process.env.STOCKFLOW_LOCAL_URL || 'http://localhost:8080';
const chrome = process.env.CHROME_PATH || 'C:/Program Files/Google/Chrome/Application/chrome.exe';
const before = process.argv.includes('--before'), stage = before ? 'before' : 'after';
const detailsOnly = process.argv.includes('--details-only');
const headerOnly = process.argv.includes('--header-only');
const evidence = path.join(root, 'target/mobile-support-verification'), baseline = path.join(evidence, 'before-assets');
fs.mkdirSync(evidence, { recursive: true });
const baselineRoutes = ['/styles.css', '/assets/fragments/storefront.html', '/assets/modules/navigation-events.js'];
if (before && !fs.existsSync(baseline)) {
    fs.mkdirSync(baseline);
    for (const route of baselineRoutes) fs.copyFileSync(path.join(assets, route), path.join(baseline, path.basename(route)));
}
const requests = [], failures = [], results = [];
const server = http.createServer(async (request, response) => {
    const url = new URL(request.url, 'http://localhost');
    try {
        if (url.pathname.startsWith('/api/')) {
            requests.push({ method: request.method, path: url.pathname });
            if (request.method !== 'GET') { response.writeHead(405); response.end(); return; }
            const actual = await fetch(upstream + url.pathname + url.search, { signal: AbortSignal.timeout(10000) });
            response.writeHead(actual.status, { 'Content-Type': actual.headers.get('content-type') || 'application/json' });
            response.end(Buffer.from(await actual.arrayBuffer())); return;
        }
        const route = /^\/san-pham\/\d+$/.test(url.pathname) || url.pathname === '/' ? '/index.html' : url.pathname;
        const file = path.resolve(assets, '.' + route);
        if (!file.startsWith(assets + path.sep) || !fs.existsSync(file)) { response.writeHead(404); response.end(); return; }
        response.writeHead(200, { 'Content-Type': ({ '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8',
            '.js': 'text/javascript; charset=utf-8', '.svg': 'image/svg+xml' })[path.extname(file)] || 'application/octet-stream' });
        response.end(fs.readFileSync(before && baselineRoutes.includes(route) ? path.join(baseline, path.basename(route)) : file));
    } catch (error) { response.writeHead(502); response.end(JSON.stringify({ message: error.message })); }
});
let browser, socket, session, sequence = 0;
const pending = new Map();
function send(method, params = {}, scoped = true) {
    return new Promise((resolve, reject) => {
        const id = ++sequence, timer = setTimeout(() => { pending.delete(id); reject(Error('CDP timeout: ' + method)); }, 20000);
        pending.set(id, { resolve: value => { clearTimeout(timer); resolve(value); }, reject: error => { clearTimeout(timer); reject(error); } });
        socket.send(JSON.stringify({ id, method, params, ...(scoped && session ? { sessionId: session } : {}) }));
    });
}
async function evaluate(expression) {
    const result = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise: true });
    if (result.exceptionDetails) throw Error(JSON.stringify(result.exceptionDetails));
    return result.result.value;
}
async function until(expression, timeout = 15000) {
    const end = Date.now() + timeout;
    while (Date.now() < end) { if (await evaluate(`document.body && (${expression})`)) return; await new Promise(resolve => setTimeout(resolve, 100)); }
    throw Error('UI timeout: ' + expression);
}
async function screenshot(name) {
    const result = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false });
    fs.writeFileSync(path.join(evidence, name + '.png'), Buffer.from(result.data, 'base64'));
}
async function click(selector) {
    await evaluate(`document.querySelector(${JSON.stringify(selector)}).scrollIntoView({block:'center',behavior:'instant'})`);
    await until(`(()=>{const e=document.querySelector(${JSON.stringify(selector)}),ancestor=e.closest('.scroll-reveal'),r=e.getBoundingClientRect();
        return (!ancestor||Number(getComputedStyle(ancestor).opacity)>.999)&&e.contains(document.elementFromPoint(r.left+r.width/2,r.top+r.height/2));})()`);
    const point = await evaluate(`(()=>{const r=document.querySelector(${JSON.stringify(selector)}).getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2};})()`);
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', ...point });
    await send('Input.dispatchMouseEvent', { type: 'mousePressed', button: 'left', clickCount: 1, ...point });
    await send('Input.dispatchMouseEvent', { type: 'mouseReleased', button: 'left', clickCount: 1, ...point });
    await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: 0, y: 0 });
}
async function key(key, code = key) {
    const windowsVirtualKeyCode = { Enter: 13, Escape: 27, ArrowDown: 40, Tab: 9 }[key];
    await send('Input.dispatchKeyEvent', { type: 'keyDown', key, code, windowsVirtualKeyCode,
        ...(key === 'Enter' ? { text: '\r', unmodifiedText: '\r' } : {}) });
    await send('Input.dispatchKeyEvent', { type: 'keyUp', key, code, windowsVirtualKeyCode });
}
async function theme(value) {
    await evaluate(`if(document.documentElement.dataset.theme!=='${value}')document.querySelector('#theme-toggle').click()`);
    await new Promise(resolve => setTimeout(resolve, 450));
}
async function overlaps() {
    return evaluate(`(()=>{const visible=e=>{const s=getComputedStyle(e),r=e.getBoundingClientRect();return s.display!=='none'&&s.visibility!=='hidden'&&Number(s.opacity)>0&&r.width>0&&r.height>0;};
        const controls=[...document.querySelectorAll('#contact-toggle,#back-to-top,#contact-panel')].filter(visible);
        const targets=[...document.querySelectorAll('#catalog-grid .product-price,#catalog-grid .add-button,#mobile-purchase-bar .button')].filter(visible);
        const out=[];for(const target of targets){const a=target.getBoundingClientRect();if(a.bottom<=0||a.top>=innerHeight)continue;
          for(const control of controls){const b=control.getBoundingClientRect();const x=Math.min(a.right,b.right)-Math.max(a.left,b.left),y=Math.min(a.bottom,b.bottom)-Math.max(a.top,b.top);
            if(x>1&&y>1){const top=document.elementFromPoint((Math.max(a.left,b.left)+Math.min(a.right,b.right))/2,(Math.max(0,a.top,b.top)+Math.min(innerHeight,a.bottom,b.bottom))/2);
              if(control.contains(top))out.push({control:control.id,target:target.textContent.trim(),product:target.closest('.product-card')?.querySelector('h3').textContent,x,y});}}}
        return out;})()`);
}
async function scanCatalog(width, value) {
    const catalog = await evaluate(`(async()=>{const response=await fetch('/api/v1/products?size=8&status=ACTIVE&grouped=true');const data=await response.json();return {total:data.total_elements,pages:data.total_pages};})()`);
    let totalCards = 0, firstCapture = false;
    const intersections = [], products = [], loadedPhotos = [];
    for (let page = 0; page < catalog.pages; page++) {
        {
            await evaluate(`(async()=>{const {app}=await import('/assets/modules/context.js');app.state.pages.catalog=${page};await app.loadCatalog();})()`);
            await until(`document.querySelector('#catalog-grid').dataset.catalogStatus==='ready'`);
        }
        const count = await evaluate(`document.querySelectorAll('#catalog-grid .product-card').length`);
        totalCards += count;
        products.push(...await evaluate(`[...document.querySelectorAll('#catalog-grid .product-card h3')].map(e=>e.textContent)`));
        for (let index = 0; index < count; index++) {
            for (const target of ['.product-price', '.add-button']) {
                for (const bottomOffset of [40, 95]) {
                    await evaluate(`(()=>{const e=document.querySelectorAll('#catalog-grid .product-card')[${index}].querySelector('${target}');const r=e.getBoundingClientRect();window.scrollTo({top:scrollY+r.top+r.height/2-innerHeight+${bottomOffset},behavior:'instant'});})()`);
                    await new Promise(resolve => setTimeout(resolve, 65));
                    const found = await overlaps(); intersections.push(...found);
                    if (before && found.length && !firstCapture) {
                        await new Promise(resolve => setTimeout(resolve, 1200));
                        await screenshot(`${stage}-${width}-${value}-overlap`); firstCapture = true;
                    }
                    if (!before) assert.deepEqual(found, [], `Support obscures product at ${width}px ${value}`);
                }
            }
            await evaluate(`document.querySelectorAll('#catalog-grid .product-card')[${index}].scrollIntoView({block:'center',behavior:'instant'})`);
            await new Promise(resolve => setTimeout(resolve, 300));
            loadedPhotos.push(await evaluate(`(()=>{const image=document.querySelectorAll('#catalog-grid .product-card')[${index}].querySelector('img');return {src:image.currentSrc,loaded:image.complete&&image.naturalWidth>0};})()`));
            if (index === 0 && page === 0) {
                await new Promise(resolve => setTimeout(resolve, 1500));
                await screenshot(`${stage}-${width}-${value}-catalog`);
            }
            if (index === 2 && page === 0) await screenshot(`${stage}-${width}-${value}-more-products`);
        }
        if (!before) assert.ok(await evaluate(`[...document.querySelectorAll('#catalog-grid .product-card')].every(card=>getComputedStyle(card).opacity==='1'&&getComputedStyle(card.querySelector('h3')).webkitLineClamp==='2'&&!card.querySelector('.product-sku'))`));
    }
    assert.equal(totalCards, catalog.total, 'Scan all catalog pages');
    const widget = await evaluate(`(()=>{const e=document.querySelector('#contact-widget'),r=e.getBoundingClientRect();return {position:getComputedStyle(e).position,parent:e.parentElement.id,width:r.width};})()`);
    results.push({ width, theme: value, catalog, totalCards, products, loadedPhotos, intersections, widget });
    if (!before && width < 1440) { assert.equal(widget.position, 'static'); assert.equal(widget.parent, 'mobile-support-actions'); }
    if (!before && width >= 1440) {
        const original = JSON.parse(fs.readFileSync(path.join(evidence, 'before-metrics.json'))).results.find(row=>row.width===width&&row.theme===value);
        assert.deepEqual(widget, original.widget, 'Wide desktop widget placement unchanged');
    }
    console.log(`${before?'CAPTURED':'PASS'} ${width}px ${value}: ${totalCards} real products, ${intersections.length} intersections`);
}
async function supportActions(width, value) {
    await evaluate(`document.querySelector('#contact-toggle').scrollIntoView({block:'center',behavior:'instant'});document.querySelector('#contact-toggle').focus()`);
    await key('Enter');
    await until(`document.querySelector('#contact-toggle').getAttribute('aria-expanded')==='true' && !document.querySelector('#contact-panel').inert`);
    assert.ok(await evaluate(`getComputedStyle(document.querySelector('#contact-toggle')).outlineStyle!=='none'`), 'Visible keyboard focus');
    assert.ok(await evaluate(`[...document.querySelectorAll('#contact-toggle,#back-to-top')].every(e=>e.scrollWidth<=e.clientWidth+1&&e.getBoundingClientRect().height>=44)`), 'Readable support buttons');
    if (width < 1440) assert.equal(await evaluate(`getComputedStyle(document.querySelector('#contact-panel')).position`), 'static', 'Contact panel also remains in document flow');
    await key('ArrowDown');
    await until(`document.activeElement.id==='zalo-contact'`);
    const links = await evaluate(`[...document.querySelectorAll('#contact-panel a:not([hidden])')].map(e=>({text:e.textContent.trim(),href:e.href}))`);
    assert.ok(links.some(link=>link.href==='https://zalo.me/0968935896'));
    assert.ok(links.some(link=>link.href.startsWith('https://web.facebook.com/')));
    assert.ok(links.some(link=>link.href==='tel:0968935896'));
    if (width < 1440) await screenshot(`${stage}-${width}-${value}-footer-open`);
    await key('Escape');
    assert.ok(await evaluate(`document.activeElement.id==='contact-toggle' && document.querySelector('#contact-panel').inert && document.querySelector('#contact-toggle').getAttribute('aria-expanded')==='false'`));
    if (width < 1440) {
        await key('Tab');
        assert.equal(await evaluate(`document.activeElement.id`), 'back-to-top', 'Footer keyboard order');
    } else await evaluate(`document.querySelector('#back-to-top').focus()`);
    await key('Enter');
    await until(`scrollY===0`);
    if (width < 1440) assert.equal(await evaluate(`document.activeElement.classList.contains('shop-brand')`), true, 'Return-to-top restores focus to header');
    assert.ok(await evaluate(`document.documentElement.scrollWidth<=innerWidth+1`), 'No page overflow');
    results.at(-1).supportLinks = links;
}
async function detailCheck(base, width, value, productId) {
    await send('Page.navigate', { url: base + '/san-pham/' + productId });
    await until(`document.querySelector('#shop-product-detail-body')?.dataset.productId==='${productId}' && document.querySelector('#shop-product-add-form button[type="submit"]')`);
    await theme(value);
    await evaluate(`(()=>{const r=document.querySelector('#shop-product-add-form button[type="submit"]').getBoundingClientRect();window.scrollTo({top:scrollY+r.bottom+160,behavior:'instant'});})()`);
    await until(`!document.querySelector('#mobile-purchase-bar').hidden`);
    assert.deepEqual(await overlaps(), [], 'Support never obscures sticky purchase action');
    const sticky = await evaluate(`(()=>{const e=document.querySelector('#mobile-purchase-bar [data-action="sticky-add-cart"]'),r=e.getBoundingClientRect(),top=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);return {inViewport:r.top>=0&&r.bottom<=innerHeight,receivesPointer:e.contains(top),widgetPosition:getComputedStyle(document.querySelector('#contact-widget')).position};})()`);
    assert.equal(sticky.inViewport, true); assert.equal(sticky.receivesPointer, true); assert.equal(sticky.widgetPosition, 'static');
    await new Promise(resolve => setTimeout(resolve, 750));
    await screenshot(`${stage}-${width}-${value}-detail-sticky`);
    await click('#contact-toggle');
    await until(`document.querySelector('#contact-toggle').getAttribute('aria-expanded')==='true'`);
    assert.deepEqual(await overlaps(), [], 'Expanded footer contact panel does not cover sticky action');
    await screenshot(`${stage}-${width}-${value}-detail-footer`);
    await key('Escape');
    const bar = await evaluate(`document.querySelector('#mobile-purchase-bar [data-action="sticky-add-cart"]').getBoundingClientRect().height`);
    assert.ok(bar >= 44);
    await key('Tab'); assert.equal(await evaluate(`document.activeElement.id`), 'back-to-top'); await key('Enter');
    await until(`scrollY===0 && document.activeElement.classList.contains('shop-brand')`);
    console.log(`PASS ${width}px ${value}: real product details + sticky purchase + footer contact`);
}
(async () => {
    const check = await fetch(upstream + '/api/v1/products?size=100&status=ACTIVE&grouped=true', { signal: AbortSignal.timeout(5000) });
    assert.equal(check.status, 200, 'Local StockFlow API is required for real-product screenshots');
    const actualCatalog = await check.json();
    assert.ok(actualCatalog.total_elements > 0);
    await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
    const base = 'http://127.0.0.1:' + server.address().port;
    browser = spawn(chrome, ['--headless=new', '--no-first-run', '--no-default-browser-check', '--disable-background-networking',
        '--disable-component-update', '--remote-debugging-port=0', '--user-data-dir=' + path.join(evidence, 'chrome-profile-' + process.pid), 'about:blank'],
        { windowsHide: true, stdio: ['ignore', 'ignore', 'pipe'] });
    const endpoint = await new Promise((resolve, reject) => {
        let output = ''; const timer = setTimeout(() => reject(Error('Chrome startup timeout')), 15000);
        browser.stderr.on('data', chunk => { output += chunk; const match = output.match(/DevTools listening on (ws:\/\/[^\s]+)/);if(match){clearTimeout(timer);resolve(match[1]);} });browser.once('error', reject);
    });
    socket = new WebSocket(endpoint);
    await new Promise((resolve, reject) => { socket.addEventListener('open', resolve, { once: true }); socket.addEventListener('error', reject, { once: true }); });
    socket.addEventListener('message', event => {
        const value = JSON.parse(event.data);
        if(value.id){const handler=pending.get(value.id);pending.delete(value.id);if(value.error)handler?.reject(Error(value.error.message));else handler?.resolve(value.result);}
        if(value.method==='Runtime.exceptionThrown')failures.push(value.params.exceptionDetails);
    });
    const target = await send('Target.createTarget', { url: 'about:blank' }, false);
    session = (await send('Target.attachToTarget', { targetId: target.targetId, flatten: true }, false)).sessionId;
    await send('Page.enable'); await send('Runtime.enable'); await send('Network.enable');
    await send('Network.setBlockedURLs', { urls: ['https://accounts.google.com/*', 'https://fonts.googleapis.com/*', 'https://fonts.gstatic.com/*'] });
    await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
    if (headerOnly) {
        for (const width of [320,375,414,768,1366]) {
            await send('Emulation.setDeviceMetricsOverride', { width, height: 950, deviceScaleFactor: 1, mobile: width < 768 });
            await send('Page.navigate', { url: base + '/#product-shelf' });
            await until(`document.querySelector('#catalog-grid')?.dataset.catalogStatus==='ready' && document.querySelectorAll('#catalog-grid .product-card').length>0`);
            for (const value of ['light','dark']) for (const reduced of [false,true]) {
                await theme(value);
                await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: reduced ? 'reduce' : 'no-preference' }] });
                await until(`Math.abs(parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--shop-header-height'))-document.querySelector('.shop-header').getBoundingClientRect().height)<1`);
                await evaluate(`(()=>{const r=document.querySelector('#catalog-grid .product-card').getBoundingClientRect();scrollTo({top:scrollY+r.top-document.querySelector('.shop-header').getBoundingClientRect().height-16,behavior:'instant'});})()`);
                await until(`Number(getComputedStyle(document.querySelector('#catalog-grid .product-card')).opacity)>.999 && [...document.querySelectorAll('#catalog-grid .product-card img')].every(img=>img.complete&&img.naturalWidth>0)`);
                assert.ok(await evaluate(`document.documentElement.scrollWidth<=innerWidth+1`),'Real catalog header overflow');
                assert.equal(await evaluate(`document.querySelector('.shop-header').getBoundingClientRect().top`),0,'Real catalog sticky offset');
                assert.equal(await evaluate(`document.querySelectorAll('.demo-bar,[data-demo-role]').length`),0);
                const photos=await evaluate(`[...document.querySelectorAll('#catalog-grid .product-card')].map(card=>({name:card.querySelector('h3').textContent,image:card.querySelector('img').currentSrc}))`);
                await screenshot(`header-real-${width}-${value}${reduced?'-reduced':''}`);
                results.push({width,theme:value,reduced,photos});
                console.log(`PASS real header ${width}px ${value} reduced=${reduced}: ${photos.length} real products, loaded images, sticky, no overflow`);
            }
        }
        assert.equal(failures.length,0,JSON.stringify(failures));
        assert.ok(requests.every(request=>request.method==='GET'),'Only GET requests reached the local API');
        fs.writeFileSync(path.join(evidence,'header-real-metrics.json'),JSON.stringify({upstream,results,requests},null,2));
        return;
    }
    for (const width of detailsOnly ? [] : [320, 375, 414, 1366, 1920]) {
        await send('Emulation.setDeviceMetricsOverride', { width, height: 950, deviceScaleFactor: 1, mobile: width < 768 });
        await send('Emulation.setTouchEmulationEnabled', { enabled: width < 768 });
        await send('Page.navigate', { url: base + '/#shop/catalog' });
        await until(`document.querySelector('#catalog-grid')?.dataset.catalogStatus==='ready' && document.querySelectorAll('#catalog-grid .product-card').length>0`);
        for (const value of ['light', 'dark']) {
            await theme(value);
            await scanCatalog(width, value);
            if (!before) await supportActions(width, value);
        }
    }
    if (!before) {
        const productId = actualCatalog.content[0].id;
        // Verify relocation and focus while resizing an already open contact menu.
        if (!detailsOnly) {
        await send('Emulation.setDeviceMetricsOverride', { width: 375, height: 950, deviceScaleFactor: 1, mobile: true });
        await until(`document.querySelector('#contact-widget').parentElement.id==='mobile-support-actions'`);
        await evaluate(`document.querySelector('#contact-toggle').focus()`); await key('ArrowDown');
        await send('Emulation.setDeviceMetricsOverride', { width: 1920, height: 950, deviceScaleFactor: 1, mobile: false });
        await until(`document.querySelector('#contact-widget').parentElement.id==='storefront-view'`);
        assert.equal(await evaluate(`document.activeElement.id`), 'contact-toggle');
        assert.ok(await evaluate(`document.querySelector('#contact-panel').inert && ['contact-widget','contact-toggle','contact-panel','back-to-top'].every(id=>document.querySelectorAll('#'+id).length===1)`));
        }
        // Details are checked with normal motion as well as the reduced-motion catalog checks above.
        await send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'no-preference' }] });
        for (const width of [320, 375, 414]) {
            await send('Emulation.setDeviceMetricsOverride', { width, height: 950, deviceScaleFactor: 1, mobile: true });
            await send('Emulation.setTouchEmulationEnabled', { enabled: true });
            for (const value of ['light', 'dark']) await detailCheck(base, width, value, productId);
        }
    }
    assert.equal(failures.length, 0, JSON.stringify(failures));
    assert.ok(requests.every(request => request.method === 'GET'), 'Only GET requests reached the local API');
    fs.writeFileSync(path.join(evidence, `${stage}${detailsOnly ? '-details' : ''}-metrics.json`), JSON.stringify({ upstream, results, requests }, null, 2));
    console.log('Evidence: ' + evidence);
})().catch(async error => {
    try { await screenshot('failed-' + stage); } catch {}
    console.error(error); process.exitCode=1;
}).finally(async()=>{
    try { if(socket?.readyState===1)await send('Browser.close',{},false); } catch {}
    socket?.close();browser?.kill();server.close();
});
