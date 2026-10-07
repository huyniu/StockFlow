/* Headless Chrome checks with local fixtures. Never creates live orders or sends mail. */
const fs=require('node:fs'), http=require('node:http'), path=require('node:path'), {spawn}=require('node:child_process'), assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'), assets=path.join(root,'src/main/resources/static');
const chrome=process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe';
const evidence=path.join(root,'target/ui-verification');fs.mkdirSync(evidence,{recursive:true});
const user={id:1,email:'layout@example.test',full_name:'Khách hàng có tên rất dài để kiểm tra giao diện',role:'CUSTOMER',status:'ACTIVE'};
const product={id:1,sku:'TEST-01',name:'Sản phẩm có tên rất dài dùng để kiểm tra bố cục giỏ hàng trên điện thoại',category_id:1,category_name:'Điện thoại',unit_price:200000,status:'ACTIVE',available_quantity:25,image_url:'/assets/stockflow.svg',versions:[],variants:[],specifications:[]};
const page=content=>({content,total_elements:content.length,total_pages:1,number:0,size:20,first:true,last:true});
let resetRequests=0;
let savedAddresses=[], returnRequests=[], deliveredVisible=false;
const deliveredOrder={id:1,order_code:'ORDER-TEST-1',customer_id:1,warehouse_id:1,status:'DELIVERED',total_amount:430000,shipping_fee:30000,created_at:new Date().toISOString(),updated_at:new Date().toISOString(),items:[{product_id:1,quantity:2,unit_price:200000,line_total:400000}],delivery_details:{recipient_name:'Khách',recipient_phone:'0901234567',address:'12 Mễ Trì'},shipment:{status:'DELIVERED',tracking_code:'GHN_HAN_1_1234',carrier_mode:'SIMULATED'}};
function fixture(url,method='GET',body={}) {
 const p=url.pathname;
 if(p.endsWith('/users/me/password'))return {message:'Đã đổi mật khẩu.'};
 if(p.endsWith('/users/me/addresses')){
  if(method==='POST'){
   const row={...body,id:savedAddresses.length+1,province_name:'Hà Nội',district_name:'Nam Từ Liêm',ward_name:'Mễ Trì',is_default:savedAddresses.length===0||body.is_default};
   savedAddresses.push(row);if(row.is_default)user.default_address=row;return row;
  }return savedAddresses;
 }
 if(p.endsWith('/returns')){
  if(method==='POST'){const row={...body,id:1,order_code:deliveredOrder.order_code,status:'PENDING',created_at:new Date().toISOString(),images:[]};returnRequests.push(row);return row;}return returnRequests;
 }
 if(p.endsWith('/returns/1/review')){Object.assign(returnRequests[0],{status:body.decision,resolution_note:body.note});return returnRequests[0];}
 if(p.endsWith('/returns/1/receive')){returnRequests[0].status='RECEIVED';deliveredOrder.status='RETURNED';return returnRequests[0];}
 if(p.endsWith('/orders/1/payment'))return {method:'COD',status:'PAID'};
 if(p.endsWith('/orders/1'))return deliveredOrder;
 if(p.endsWith('/auth/google/config'))return {enabled:false,client_id:''};
 if(p.endsWith('/users/me'))return user;
 if(p.endsWith('/admin/users'))return page([user]);
 if(p.endsWith('/auth/forgot-password')){resetRequests++;return {message:'Nếu email hợp lệ, mã đã được gửi.'};}
 if(p.endsWith('/auth/reset-password'))return {message:'Đã đổi mật khẩu. Vui lòng đăng nhập lại.'};
 if(p.endsWith('/locations/provinces'))return [{ProvinceID:201,ProvinceName:'Hà Nội'}];
 if(p.endsWith('/locations/districts'))return [{ProvinceID:201,DistrictID:1450,DistrictName:'Nam Từ Liêm'}];
 if(p.endsWith('/locations/wards'))return [{DistrictID:1450,WardCode:'20907',WardName:'Mễ Trì'}];
 if(p.endsWith('/locations/mode'))return {test_mode:true};
 if(p.endsWith('/locations/calculate-fee'))return {shipping_fee:30000,service_type_id:2};
 if(p.endsWith('/categories'))return [{id:1,name:'Điện thoại',slug:'dien-thoai',parent_id:null}];
 if(p.endsWith('/brands')||p.endsWith('/products/specification-options')||p.endsWith('/storefront/bestsellers')||p.endsWith('/wishlist'))return [];
 if(p.includes('/storefront/contact'))return {zalo_url:'https://zalo.me/0968935896'};
 if(p.endsWith('/storefront/branches')||p.endsWith('/warehouses/operating-options'))return [{id:1,name:'Kho Hà Nội',address:'Hà Nội',status:'ACTIVE'}];
 if(p.endsWith('/products/1'))return product;
 if(p.endsWith('/products'))return page([product]);
 if(p.endsWith('/orders/my')||p.endsWith('/orders'))return page(deliveredVisible?[deliveredOrder]:[]);
 if(p.includes('/inventories'))return page([]);
 return [];
}
const server=http.createServer(async(req,res)=>{
 const url=new URL(req.url,'http://localhost');
 if(url.pathname==='/api/v1/diagnostics/rejected'){res.writeHead(409,{'Content-Type':'application/json'});res.end(JSON.stringify({error:'CONFLICT',message:'SKU đã tồn tại.'}));return;}
 if(url.pathname.startsWith('/api/')){let text='';for await(const chunk of req)text+=chunk;const body=text?JSON.parse(text):{};res.writeHead(200,{'Content-Type':'application/json'});res.end(JSON.stringify(fixture(url,req.method,body)));return;}
 const relative=(['/','/login','/register'].includes(url.pathname)||/^\/san-pham\/\d+$/.test(url.pathname))?'/index.html':url.pathname;
 const file=path.resolve(assets,'.'+relative);
 if(!file.startsWith(assets+path.sep)||!fs.existsSync(file)){res.writeHead(404);res.end();return;}
 const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml'};
 res.writeHead(200,{'Content-Type':types[path.extname(file)]||'application/octet-stream'});res.end(fs.readFileSync(file));
});
let browser,socket,id=0,session;const pending=new Map(),exceptions=[];
function send(method,params={},scoped=true){return new Promise((resolve,reject)=>{const request=++id;const timeout=setTimeout(()=>{pending.delete(request);reject(Error('CDP timeout: '+method));},15000);pending.set(request,{resolve:value=>{clearTimeout(timeout);resolve(value);},reject:error=>{clearTimeout(timeout);reject(error);}});socket.send(JSON.stringify({id:request,method,params,...(scoped&&session?{sessionId:session}:{})}));});}
async function evaluate(expression){const result=await send('Runtime.evaluate',{expression,returnByValue:true,awaitPromise:true});if(result.exceptionDetails)throw Error(JSON.stringify(result.exceptionDetails));return result.result.value;}
async function until(expression){const end=Date.now()+10000;while(Date.now()<end){if(await evaluate(`document.readyState !== 'loading' && document.body && (${expression})`))return;await new Promise(r=>setTimeout(r,80));}throw Error('UI timeout: '+expression+' '+JSON.stringify(await evaluate(`({ready:document.readyState,text:document.body?.innerText.slice(-500)})`))+' '+JSON.stringify(exceptions));}
async function screenshot(name){const value=await send('Page.captureScreenshot',{format:'png',captureBeyondViewport:false});fs.writeFileSync(path.join(evidence,name+'.png'),Buffer.from(value.data,'base64'));}
async function viewport(width,height=850){await send('Emulation.setDeviceMetricsOverride',{width,height,deviceScaleFactor:1,mobile:width<768});}
async function noOverflow(label){const dimensions=await evaluate(`({viewport:innerWidth,width:document.documentElement.scrollWidth,body:document.body.scrollWidth})`);assert.ok(dimensions.width<=dimensions.viewport+1,label+' '+JSON.stringify(dimensions));}
(async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const base='http://127.0.0.1:'+server.address().port;
 browser=spawn(chrome,['--headless=new','--no-first-run','--no-default-browser-check','--disable-background-networking','--disable-component-update','--remote-debugging-port=0','--user-data-dir='+path.join(evidence,'chrome-profile'),'about:blank'],{windowsHide:true,stdio:['ignore','ignore','pipe']});
 const endpoint=await new Promise((resolve,reject)=>{let output='';const timeout=setTimeout(()=>reject(Error('Chrome startup timed out')),15000);browser.stderr.on('data',chunk=>{output+=chunk;const found=output.match(/DevTools listening on (ws:\/\/[^\s]+)/);if(found){clearTimeout(timeout);resolve(found[1]);}});browser.once('error',reject);});
 console.log('Chrome started');socket=new WebSocket(endpoint);await new Promise((resolve,reject)=>{const timeout=setTimeout(()=>reject(Error('CDP WebSocket open timeout')),10000);socket.addEventListener('open',()=>{clearTimeout(timeout);resolve();},{once:true});socket.addEventListener('error',reject,{once:true});});
 socket.addEventListener('message',event=>{const value=JSON.parse(event.data);if(value.id){const handlers=pending.get(value.id);pending.delete(value.id);if(value.error)handlers?.reject(Error(value.error.message));else handlers?.resolve(value.result);}if(value.method==='Runtime.exceptionThrown')exceptions.push(value.params.exceptionDetails);if(value.method==='Runtime.consoleAPICalled'&&value.params.type==='error')console.error('Browser:',value.params.args.map(arg=>arg.description||arg.value).join(' '));});
 const target=await send('Target.createTarget',{url:'about:blank'},false);session=(await send('Target.attachToTarget',{targetId:target.targetId,flatten:true},false)).sessionId;
 await send('Page.enable');await send('Runtime.enable');await send('Network.enable');await send('Network.setBlockedURLs',{urls:['https://*']});
 console.log('Browser connected');for(const width of [320,375,768,1366]) {
  await viewport(width);await send('Page.navigate',{url:base+'/login'});await until(`document.body.classList.contains('auth-page-open') && document.querySelector('#auth-dialog').open`);console.log('Login viewport '+width);
  for(const theme of ['light','dark']) {
   await evaluate(`document.documentElement.dataset.theme='${theme}'`);await noOverflow('Login '+width+' '+theme);
   assert.equal(await evaluate(`getComputedStyle(document.querySelector('.auth-page-form-side')).backgroundColor`),'rgba(0, 0, 0, 0)');
   assert.equal(await evaluate(`getComputedStyle(document.body).backgroundColor`),'rgb(250, 248, 242)');
   if(width<801)assert.equal(await evaluate(`getComputedStyle(document.querySelector('.auth-page-art')).display`),'none');
   await screenshot('login-'+width+'-'+theme);
  }
 }
 await viewport(375);await evaluate(`document.querySelector('#auth-forgot-password').click()`);await until(`document.querySelector('#password-reset-dialog').open`);
 await evaluate(`document.querySelector('#password-reset-form').elements.email.value='layout@example.test';document.querySelector('#password-reset-send').click()`);
 await until(`!document.querySelector('#password-reset-details').hidden`);assert.equal(resetRequests,1);assert.equal(await evaluate(`document.querySelector('#password-reset-send').disabled`),true);
 await evaluate(`document.querySelector('#password-reset-send').click()`);assert.equal(resetRequests,1);
 await screenshot('forgot-password-375');
 await evaluate(`(()=>{const f=document.querySelector('#password-reset-form');f.elements.otp.value='123456';f.elements.new_password.value='Secret@123';f.elements.confirm_password.value='Different@123';f.requestSubmit();})()`);
 await until(`!document.querySelector('#password-reset-error').hidden`);assert.match(await evaluate(`document.querySelector('#password-reset-error').textContent`),/chưa giống/);
 await evaluate(`document.querySelector('#password-reset-form').elements.confirm_password.value='Secret@123';document.querySelector('#password-reset-form').requestSubmit()`);
 await until(`!document.querySelector('#password-reset-dialog').open`);assert.equal(await evaluate(`document.querySelector('#password-reset-form').elements.new_password.value`),'');
 const modes=await evaluate(`['SIMULATED','GHN_SANDBOX','MANUAL','GHN_PRODUCTION'].map(carrier_mode=>StockFlowShipment.trackingUrl({carrier_mode,tracking_code:'ABC123'},'SHIPPED'))`);
 assert.deepEqual(modes,[null,null,null,'https://donhang.ghn.vn/?order_code=ABC123']);
 // Seed only browser fixtures; no live server, accounts, mail or inventory are touched.
 await evaluate(`sessionStorage.setItem('stockflow.web.session',JSON.stringify({token:'layout-token'}));sessionStorage.setItem('stockflow.web.cart.v1',JSON.stringify({version:1,owner:'customer:1',warehouse_id:1,items:[{product_id:1,quantity:2,selected:true}]}))`);
 await send('Page.navigate',{url:base+'/#shop'});await until(`document.querySelector('#catalog-grid')?.dataset.catalogStatus==='loaded' || document.querySelector('#catalog-grid')?.dataset.catalogStatus==='ready'`);
 const apiError=await evaluate(`(async()=>{const {app}=await import('/assets/modules/context.js');try{await app.api('/diagnostics/rejected')}catch(error){return {message:error.message,status:error.status,typed:error instanceof app.ApiError}}})()`);
 assert.deepEqual(apiError,{message:'SKU đã tồn tại.',status:409,typed:true});
 await send('Network.setBlockedURLs',{urls:['https://*','*diagnostics/offline*']});
 const offlineError=await evaluate(`(async()=>{const {app}=await import('/assets/modules/context.js');try{await app.api('/diagnostics/offline')}catch(error){return {status:error.status,typed:error instanceof app.ApiError}}})()`);
 assert.deepEqual(offlineError,{status:0,typed:true});
 for(const width of [320,375,768,1366]) {await viewport(width);await noOverflow('Storefront '+width);await screenshot('storefront-'+width);}
 await evaluate(`document.querySelector('[data-action="open-cart"]').click()`);await until(`document.querySelector('#cart-dialog').open`);
 for(const width of [320,375,768,1366]) {await viewport(width);await noOverflow('Checkout '+width);assert.ok(await evaluate(`document.querySelector('#cart-items img')!==null`));assert.ok(await evaluate(`(()=>{const d=document.querySelector('#cart-dialog');return d.scrollWidth<=d.clientWidth+1;})()`),'Cart content overflows at '+width);await screenshot('checkout-'+width);}
 await viewport(375);await evaluate(`document.querySelector('#checkoutProvince').value='201';document.querySelector('#checkoutProvince').dispatchEvent(new Event('change',{bubbles:true}))`);await until(`!document.querySelector('#checkoutDistrict').disabled`);
 await evaluate(`document.querySelector('#checkoutDistrict').value='1450';document.querySelector('#checkoutDistrict').dispatchEvent(new Event('change',{bubbles:true}))`);await until(`!document.querySelector('#checkoutWard').disabled`);
 await evaluate(`document.querySelector('#checkoutWard').value='20907';document.querySelector('#checkoutWard').dispatchEvent(new Event('change',{bubbles:true}))`);
 await until(`document.querySelector('#checkoutShippingFee').textContent.includes('30')`);
 assert.match(await evaluate(`document.querySelector('#checkoutTotal').textContent`),/430/);
 await screenshot('checkout-address-375');
 await evaluate(`document.querySelector('#cart-dialog').scrollTop=document.querySelector('#cart-dialog').scrollHeight`);
 await screenshot('checkout-total-375');
 await evaluate(`document.querySelector('#cart-dialog').close();document.querySelector('[data-action="my-account"]').click()`);
 await until(`!document.querySelector('#shop-account').hidden && document.querySelector('#profile-status').textContent===''`);
 await evaluate(`document.querySelector('[data-action="new-address"]').click()`);
 await until(`document.querySelector('#address-editor-dialog').open && document.querySelector('#addressProvince').options.length>1`);
 await evaluate(`(()=>{const f=document.querySelector('#address-book-form');f.elements.recipient_name.value='Người nhận ở công ty';f.elements.recipient_phone.value='0901234567';f.elements.label.value='Công ty';f.elements.street_address.value='12 đường thử nghiệm';document.querySelector('#addressProvince').value='201';document.querySelector('#addressProvince').dispatchEvent(new Event('change',{bubbles:true}));})()`);
 await until(`!document.querySelector('#addressDistrict').disabled`);
 await evaluate(`document.querySelector('#addressDistrict').value='1450';document.querySelector('#addressDistrict').dispatchEvent(new Event('change',{bubbles:true}))`);
 await until(`!document.querySelector('#addressWard').disabled`);
 await evaluate(`document.querySelector('#addressWard').value='20907';document.querySelector('#address-book-form').requestSubmit()`);
 await until(`!document.querySelector('#address-editor-dialog').open && document.querySelector('#address-book-list').textContent.includes('Công ty')`);
 assert.equal(savedAddresses.length,1);assert.equal(savedAddresses[0].is_default,true);
 await screenshot('address-book-375');
 await evaluate(`document.querySelector('[data-action="open-cart"]').click()`);
 await until(`document.querySelector('#checkoutSavedAddress').options.length===2 && document.querySelector('#delivery-name').value==='Người nhận ở công ty' && document.querySelector('#checkoutWard').value==='20907'`);
 assert.equal(await evaluate(`document.querySelector('#checkoutStreetAddress').value`),'12 đường thử nghiệm');
 await screenshot('saved-address-checkout-375');
 await evaluate(`document.querySelector('#cart-dialog').close()`);
 deliveredVisible=true;
 await evaluate(`document.querySelector('[data-action="my-orders"]').click()`);
 await until(`document.querySelector('[data-action="view-order"][data-id="1"]')!==null`);
 await evaluate(`document.querySelector('[data-action="view-order"][data-id="1"]').click()`);
 await until(`document.querySelector('[data-action="request-return"]')!==null`);
 await evaluate(`document.querySelector('[data-action="request-return"]').click();document.querySelector('#return-create-form').elements.reason.value='Sản phẩm bị lỗi, cần hỗ trợ đổi trả';document.querySelector('#return-create-form').requestSubmit()`);
 await until(`document.querySelector('#returns-dialog').open && document.querySelector('#returns-list').textContent.includes('Chờ duyệt')`);
 assert.equal(returnRequests.length,1);await screenshot('returns-customer-375');
 await evaluate(`document.querySelector('#returns-dialog').close();document.querySelector('[data-action="open-change-password"]').click()`);
 await until(`document.querySelector('#change-password-dialog').open`);
 await evaluate(`(()=>{const f=document.querySelector('#change-password-form');f.elements.current_password.value='Before@123';f.elements.new_password.value='After@123';f.elements.confirmation.value='Different';f.requestSubmit();})()`);
 await until(`document.querySelector('#change-password-error').textContent.includes('chưa khớp')`);
 await evaluate(`document.querySelector('#change-password-form').elements.confirmation.value='After@123';document.querySelector('#change-password-form').requestSubmit()`);
 await until(`!document.querySelector('#change-password-dialog').open && document.querySelector('#auth-dialog').open`);
 assert.equal(await evaluate(`document.querySelector('#change-password-form').elements.new_password.value`),'');
 assert.equal(await evaluate(`sessionStorage.getItem('stockflow.web.session')`),null);
 await evaluate(`sessionStorage.setItem('stockflow.web.session',JSON.stringify({token:'layout-token'}))`);
 await evaluate(`localStorage.removeItem('stockflow.recently-viewed')`);
 await send('Page.navigate',{url:base+'/san-pham/1'});
 await until(`(document.querySelector('#recently-viewed-products')?.querySelector('[data-product-id="1"]') ?? null)!==null && !document.querySelector('#recently-viewed').hidden`);
 assert.deepEqual(await evaluate(`JSON.parse(localStorage.getItem('stockflow.recently-viewed'))`),[1]);
 await send('Page.reload',{});
 await until(`document.querySelector('#recently-viewed-products')?.children.length===1`);
 await evaluate(`document.querySelector('.shop-footer [data-shop-tab="catalog"]').click()`); await until(`!document.querySelector('#shop-catalog').hidden`);
 for(const width of [375,768,1366]) { await viewport(width);await noOverflow('Recent products/footer '+width);await evaluate(`document.querySelector('.shop-footer').scrollIntoView()`);await evaluate(`new Promise(resolve=>setTimeout(resolve,400))`);await screenshot('recent-footer-'+width); }
 await viewport(1366);
 await evaluate(`(()=>{const track=document.querySelector('#recently-viewed-products');const card=track.firstElementChild;for(let i=0;i<7;i++)track.append(card.cloneNode(true));track.scrollLeft=0;track.scrollIntoView({block:'center'});track.dispatchEvent(new Event('pointerleave'));})()`);
 await until(`document.querySelector('#recently-viewed-products').scrollLeft>10`);
 await evaluate(`document.querySelector('#recently-viewed-products').dispatchEvent(new Event('pointerenter'));new Promise(resolve=>setTimeout(resolve,600))`);
 const pausedScroll=await evaluate(`document.querySelector('#recently-viewed-products').scrollLeft`);
 await evaluate(`new Promise(resolve=>setTimeout(resolve,4200))`);
 assert.equal(await evaluate(`document.querySelector('#recently-viewed-products').scrollLeft`),pausedScroll);
 for(const selector of ['#recently-viewed-products','#bestseller-grid']) {
  const wheel=await evaluate(`(()=>{const track=document.querySelector('${selector}');if('${selector}'==='#bestseller-grid'){track.closest('section').hidden=false;track.replaceChildren(document.querySelector('#catalog-grid .product-card').cloneNode(true));}if(track.children.length<8){const card=track.firstElementChild;for(let i=0;i<8;i++)track.append(card.cloneNode(true));}track.dispatchEvent(new Event('pointerenter'));track.scrollLeft=0;const event=new WheelEvent('wheel',{deltaY:120,bubbles:true,cancelable:true});track.dispatchEvent(event);return {left:track.scrollLeft,blocked:event.defaultPrevented};})()`);
  assert.ok(wheel.left>0,selector+JSON.stringify(wheel));assert.equal(wheel.blocked,true);
  const edge=await evaluate(`(()=>{const track=document.querySelector('${selector}');track.scrollLeft=track.scrollWidth;const event=new WheelEvent('wheel',{deltaY:120,bubbles:true,cancelable:true});track.dispatchEvent(event);return event.defaultPrevented;})()`);
  assert.equal(edge,false);
  const back=await evaluate(`(()=>{const track=document.querySelector('${selector}');const before=track.scrollLeft;track.dispatchEvent(new WheelEvent('wheel',{deltaY:-120,bubbles:true,cancelable:true}));return track.scrollLeft<before;})()`);
  assert.equal(back,true);
 }
 await evaluate(`document.querySelector('#clear-recently-viewed').click()`);
 assert.equal(await evaluate(`document.querySelector('#recently-viewed').hidden`),true);
 assert.deepEqual(await evaluate(`JSON.parse(localStorage.getItem('stockflow.recently-viewed'))`),[]);
 user.role='ADMIN';await send('Page.navigate',{url:base+'/index.html#portal/users'});await until(`document.querySelector('#users-rows')?.textContent.includes('layout@example.test')`);
 for(const width of [320,375,768,1366]) {await viewport(width);await noOverflow('Admin '+width);await screenshot('admin-'+width);}
 await evaluate(`document.querySelector('.aftercare-sidebar [data-action="open-returns"]').click()`);
 await until(`document.querySelector('[data-action="review-return"]')!==null`);
 await evaluate(`document.querySelector('[data-action="review-return"]').click();document.querySelector('#return-review-form').elements.note.value='Đồng ý nhận hàng về để kiểm tra';document.querySelector('#return-review-form').requestSubmit()`);
 await until(`document.querySelector('#returns-list').textContent.includes('Đã chấp thuận')`);
 await screenshot('returns-admin-375');assert.equal(returnRequests[0].status,'APPROVED');
 assert.equal(exceptions.length,0,JSON.stringify(exceptions));console.log('PASS: Chrome layouts 320/375/768/1366, light/dark login, recovery, cart/GHN quote, address book/default checkout, password change, customer returns/admin approval');
})().catch(error=>{console.error(error);process.exitCode=1;}).finally(async()=>{try{if(socket?.readyState===1)await send('Browser.close',{},false);}catch{}socket?.close();browser?.kill();server.close();});
