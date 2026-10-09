/* Headless Chrome checks with local fixtures. Never creates live orders or sends mail. */
const fs=require('node:fs'), http=require('node:http'), path=require('node:path'), {spawn}=require('node:child_process'), assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'), assets=path.join(root,'src/main/resources/static');
const chrome=process.env.CHROME_PATH||'C:/Program Files/Google/Chrome/Application/chrome.exe';
const evidence=path.join(root,'target/ui-verification');fs.mkdirSync(evidence,{recursive:true});
const user={id:1,email:'layout@example.test',full_name:'Khách hàng có tên rất dài để kiểm tra giao diện',role:'CUSTOMER',status:'ACTIVE'};
const product={id:1,sku:'TEST-01',name:'Sản phẩm có tên rất dài dùng để kiểm tra bố cục giỏ hàng trên điện thoại',category_id:1,category_name:'Điện thoại',unit_price:200000,status:'ACTIVE',available_quantity:25,image_url:'/assets/stockflow.svg',versions:[],variants:[],specifications:[]};
product.brand_id=1;product.brand_name='Apple';
const tablet={...product,id:2,sku:'TABLET-01',name:'Máy tính bảng kiểm tra danh mục',category_id:2,category_name:'Máy tính bảng'};
const samsungPhone={...product,id:3,sku:'SAMSUNG-01',name:'Điện thoại Samsung kiểm tra hãng',brand_id:2,brand_name:'Samsung'};
const page=content=>({content,total_elements:content.length,total_pages:1,number:0,size:20,first:true,last:true});
let resetRequests=0;
let savedAddresses=[], returnRequests=[], deliveredVisible=false;
const variantRequests=[];
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
 if(p.endsWith('/categories'))return [{id:1,name:'Điện thoại',slug:'dien-thoai',parent_id:null},{id:2,name:'Máy tính bảng',slug:'may-tinh-bang',parent_id:null},{id:3,name:'Laptop',slug:'laptop',parent_id:null}];
 if(p.endsWith('/brands'))return [{id:1,name:'Apple',slug:'apple',category_ids:[1,2]},{id:2,name:'Samsung',slug:'samsung',category_ids:[1]}];
 if(method==='POST' && p.endsWith('/products/1/variants')){
  variantRequests.push({method,body});product.variants.push({...body,id:22,version_name:'256GB',status:'ACTIVE'});return product;
 }
 if(method==='PATCH' && p.endsWith('/products/1/variants/21')){
  variantRequests.push({method,body});Object.assign(product.variants.find(row=>row.id===21),body);return product;
 }
 if(p.endsWith('/products/specification-options')||p.endsWith('/storefront/bestsellers')||p.endsWith('/wishlist'))return [];
 if(p.includes('/storefront/contact'))return {zalo_url:'https://zalo.me/0968935896'};
 if(p.endsWith('/storefront/branches')||p.endsWith('/warehouses/operating-options'))return [{id:1,name:'Kho Hà Nội',address:'Hà Nội',status:'ACTIVE'}];
 if(p.endsWith('/products/1'))return product;
 if(p.endsWith('/products/2'))return tablet;
 if(p.endsWith('/products/3'))return samsungPhone;
 if(p.endsWith('/products')){
  const category=url.searchParams.get('categoryId'),brand=url.searchParams.get('brandId');
  const products=category==='2'?[tablet]:category==='3'?[]:brand==='2'?[samsungPhone]:[product];
  return page(brand?products.filter(row=>String(row.brand_id)===brand):products);
 }
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
 // Preview the group first; browsing its products is a separate, explicit action.
 const assertCategoryResults=async(id,name,productName)=>{
  await until(`document.querySelector('#catalog-grid')?.dataset.catalogStatus==='ready' && document.querySelector('#catalog-title')?.textContent===${JSON.stringify(name)}`);
  assert.equal(await evaluate(`new URLSearchParams(location.search).get('categoryId')`),String(id));
  assert.ok((await evaluate(`document.querySelector('#catalog-grid').textContent`)).includes(productName));
  await until(`(()=>{const title=document.querySelector('#catalog-title').getBoundingClientRect();const header=document.querySelector('.shop-header').getBoundingClientRect();const products=document.querySelector('#catalog-grid').getBoundingClientRect();return title.top>=header.bottom && (!matchMedia('(max-width:900px)').matches || products.top<innerHeight-64);})()`);
  if(await evaluate(`matchMedia('(max-width:900px)').matches`))assert.equal(await evaluate(`document.querySelector('#catalog-filters-panel').open`),false);
  await noOverflow('Category results');
 };
 const tap=async selector=>{
  const point=await evaluate(`(()=>{const r=document.querySelector(${JSON.stringify(selector)}).getBoundingClientRect();return {x:r.left+r.width/2,y:r.top+r.height/2};})()`);
  assert.ok(point.y>0 && point.y<850,'Tap target is visible: '+selector+' '+JSON.stringify(point));
  await send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[point]});
  await send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
 };
 const previewHeaderCategory=async id=>{
  const touch=await evaluate(`matchMedia('(max-width:900px)').matches`);
  await send('Emulation.setTouchEmulationEnabled',{enabled:touch,maxTouchPoints:1});
  const before=await evaluate(`({url:location.href,category:document.querySelector('#catalog-category').value,scroll:scrollY,visible:document.querySelector('.shop-panel:not([hidden])').id})`);
  if(touch)await tap('#category-menu-toggle');else await evaluate(`document.querySelector('#category-menu-toggle').click()`);
  await until(`document.querySelector('#category-menu-toggle').getAttribute('aria-expanded')==='true'`);
  const inline=await evaluate(`(async()=>{const {app}=await import('/assets/modules/context.js');return app.state.categoryMenuInline;})()`);
  if(!inline)await until(`Number(getComputedStyle(document.querySelector('#category-menu-panel')).opacity)>.99`);
  const list=inline?'#home-category-list':'#category-menu-list',detail=inline?'#home-category-detail':'#category-menu-detail';
  if(touch)await tap(list+' [data-menu-category="'+id+'"]');else await evaluate(`document.querySelector('${list} [data-menu-category="${id}"]').click()`);
  await until(`document.querySelector('${list} [data-menu-category="${id}"]').getAttribute('aria-expanded')==='true' && document.querySelector('${detail} h2').textContent===${JSON.stringify({1:'Điện thoại',2:'Máy tính bảng',3:'Laptop'}[id])}`);
  assert.equal(await evaluate(`document.querySelector('#category-menu-toggle').getAttribute('aria-expanded')`),'true');
  assert.deepEqual(await evaluate(`({url:location.href,category:document.querySelector('#catalog-category').value,scroll:scrollY,visible:document.querySelector('.shop-panel:not([hidden])').id})`),before,'Preview keeps the current page and product filters');
  return {touch,detail};
 };
 const selectHeaderCategory=async id=>{
  const {touch,detail}=await previewHeaderCategory(id);
  if(touch){await evaluate(`document.querySelector('#category-menu-panel').scrollTop=document.querySelector('#category-menu-panel').scrollHeight;document.querySelector('${detail}').scrollTop=document.querySelector('${detail}').scrollHeight`);await tap(detail+' .menu-browse-button');}
  else await evaluate(`document.querySelector('${detail} .menu-browse-button').click()`);
 };
 for(const width of [320,375,768,1366]) {
  await viewport(width);
  for(const theme of ['light','dark']) {
   await evaluate(`document.documentElement.dataset.theme='${theme}'`);
   await selectHeaderCategory(2);await assertCategoryResults(2,'Máy tính bảng',tablet.name);
   assert.ok(!(await evaluate(`document.querySelector('#catalog-grid').textContent`)).includes(product.name));
   await screenshot('category-'+width+'-'+theme);
  }
 }
 await viewport(375);
 await previewHeaderCategory(1);
 assert.ok((await evaluate(`document.querySelector('#category-menu-detail').textContent`)).includes('Apple'));
 assert.ok((await evaluate(`document.querySelector('#category-menu-detail').textContent`)).includes('Samsung'));
 await screenshot('mobile-phone-brands');
 await tap('#category-menu-detail [data-action="browse-brand"][data-brand-id="2"]');
 await assertCategoryResults(1,'Điện thoại',samsungPhone.name);
 assert.equal(await evaluate(`new URLSearchParams(location.search).get('brandId')`),'2');
 assert.ok(!(await evaluate(`document.querySelector('#catalog-grid').textContent`)).includes(product.name));
 await previewHeaderCategory(1);
 await evaluate(`document.querySelector('#category-menu-panel').scrollTop=document.querySelector('#category-menu-panel').scrollHeight`);
 await tap('#category-menu-detail [data-price-range="budget"]');
 await assertCategoryResults(1,'Điện thoại',samsungPhone.name);
 assert.equal(await evaluate(`new URLSearchParams(location.search).get('maxPrice')`),'1000000');
 assert.equal(await evaluate(`new URLSearchParams(location.search).get('brandId')`),'2');
 await selectHeaderCategory(2);await assertCategoryResults(2,'Máy tính bảng',tablet.name);
 await evaluate(`document.querySelector('#catalog-filters-toggle').click()`);
 assert.equal(await evaluate(`document.querySelector('#catalog-filters-panel').open`),true);
 await evaluate(`document.querySelector('[data-price-chip="under-500"]').click()`);
 await until(`document.querySelector('#catalog-grid').dataset.catalogStatus==='ready' && new URLSearchParams(location.search).get('maxPrice')==='499999.99'`);
 assert.equal(await evaluate(`document.querySelector('#catalog-category').value`),'2');
 assert.equal(await evaluate(`document.querySelector('#catalog-filters-panel').open`),true);
 await selectHeaderCategory(3);
 await assertCategoryResults(3,'Laptop','Chưa có sản phẩm phù hợp');
 assert.equal(await evaluate(`document.querySelectorAll('#catalog-grid .product-card').length`),0);
 await selectHeaderCategory(1);await assertCategoryResults(1,'Điện thoại',product.name);
 await evaluate(`document.querySelector('#catalog-grid [data-product-link]').click()`);
 await until(`!document.querySelector('#shop-product').hidden && document.querySelector('#shop-product-detail-body').dataset.productId==='1'`);
 await selectHeaderCategory(2);await assertCategoryResults(2,'Máy tính bảng',tablet.name);
 await evaluate(`document.querySelector('[data-action="my-account"]').click()`);
 await until(`!document.querySelector('#shop-account').hidden`);
 await selectHeaderCategory(1);await assertCategoryResults(1,'Điện thoại',product.name);
 await selectHeaderCategory(2);await assertCategoryResults(2,'Máy tính bảng',tablet.name);
 await evaluate(`document.querySelector('#discovery-categories [href*="categoryId=1"]').click()`);
 await assertCategoryResults(1,'Điện thoại',product.name);
 await send('Page.reload',{ignoreCache:true});
 await assertCategoryResults(1,'Điện thoại',product.name);
 await selectHeaderCategory(1);await assertCategoryResults(1,'Điện thoại',product.name);
 console.log('PASS: touch category preview without navigation, brand/price selection, catalog/product/profile entry, reload, empty category and mobile filters');
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
 await evaluate(`document.querySelector('[data-open-product-image]').click()`);
 await until(`document.querySelector('#product-image-viewer').open`);
 assert.equal(await evaluate(`document.querySelector('#product-image-viewer img').src===document.querySelector('#shop-product-main-image').src`),true);
 await evaluate(`document.querySelector('[data-image-zoom="in"]').click()`);
 assert.equal(await evaluate(`document.querySelector('#image-viewer-scale').textContent`),'150%');
 await evaluate(`document.querySelector('.image-viewer-stage').dispatchEvent(new WheelEvent('wheel',{deltaY:-100,cancelable:true}))`);
 assert.equal(await evaluate(`document.querySelector('#image-viewer-scale').textContent`),'175%');

 await evaluate(`document.querySelector('[data-image-zoom="reset"]').click()`);
 const anchor=await evaluate(`(()=>{const stage=document.querySelector('.image-viewer-stage');const r=stage.getBoundingClientRect();const x=stage.clientWidth*.7,y=stage.clientHeight*.6;stage.dispatchEvent(new WheelEvent('wheel',{deltaY:-100,clientX:r.left+x,clientY:r.top+y,cancelable:true}));return {left:stage.scrollLeft,top:stage.scrollTop,expectedLeft:x*.25,expectedTop:y*.25,x:r.left+x,y:r.top+y};})()`);
 assert.ok(Math.abs(anchor.left-anchor.expectedLeft)<2);assert.ok(Math.abs(anchor.top-anchor.expectedTop)<2);
 await send('Input.dispatchMouseEvent',{type:'mousePressed',x:anchor.x,y:anchor.y,button:'left',clickCount:1});
 await send('Input.dispatchMouseEvent',{type:'mouseMoved',x:anchor.x-10,y:anchor.y-10,button:'left',buttons:1});
 await send('Input.dispatchMouseEvent',{type:'mouseReleased',x:anchor.x-10,y:anchor.y-10,button:'left',clickCount:1});
 const pan=await evaluate(`({left:document.querySelector('.image-viewer-stage').scrollLeft,top:document.querySelector('.image-viewer-stage').scrollTop,dragging:document.querySelector('.image-viewer-stage').classList.contains('is-dragging')})`);
 assert.ok(Math.abs(pan.left-anchor.left-10)<2);assert.ok(Math.abs(pan.top-anchor.top-10)<2);assert.equal(pan.dragging,false);
 await evaluate(`document.querySelector('[data-image-zoom="reset"]').click()`);
 assert.equal(await evaluate(`document.querySelector('#image-viewer-scale').textContent`),'100%');
 for(const width of [375,1366]) { await viewport(width);await noOverflow('Image viewer '+width);await screenshot('image-viewer-'+width); }
 await send('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});
 await send('Input.dispatchKeyEvent',{type:'keyUp',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});
 await until(`!document.querySelector('#product-image-viewer').open`);

 await send('Page.reload',{});
 await until(`document.querySelector('#recently-viewed-products')?.children.length===1`);
 await evaluate(`document.querySelector('.shop-footer [data-shop-tab="catalog"]').click()`); await until(`!document.querySelector('#shop-catalog').hidden`);
 for(const width of [375,768,1366]) { await viewport(width);await noOverflow('Recent products/footer '+width);await evaluate(`document.querySelector('.shop-footer').scrollIntoView()`);await evaluate(`new Promise(resolve=>setTimeout(resolve,400))`);await screenshot('recent-footer-'+width); }
 await viewport(1366);
 // Autoplay intentionally stops for reduced motion; test it under an explicit normal-motion preference.
 await send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'no-preference'}]});
 // The image-viewer drag leaves the pointer on the page; move it away so hover does not pause autoplay.
 await send('Input.dispatchMouseEvent',{type:'mouseMoved',x:0,y:0});
 await evaluate(`(()=>{const track=document.querySelector('#recently-viewed-products');const card=track.firstElementChild;for(let i=0;i<7;i++)track.append(card.cloneNode(true));track.scrollLeft=0;track.scrollIntoView({block:'center',behavior:'instant'});track.dispatchEvent(new Event('pointerleave'));})()`);
 try {await until(`document.querySelector('#recently-viewed-products').scrollLeft>10`);} catch(error) {
  console.error('Carousel diagnostics',await evaluate(`(()=>{const e=document.querySelector('#recently-viewed-products'),r=e.getBoundingClientRect();return {top:r.top,bottom:r.bottom,width:e.clientWidth,scrollWidth:e.scrollWidth,children:e.children.length,hover:e.matches(':hover'),focus:e.contains(document.activeElement),hidden:document.hidden,reduced:matchMedia('(prefers-reduced-motion: reduce)').matches,display:getComputedStyle(e).display,active:document.activeElement.id};})()`));throw error;
 }
 await evaluate(`document.querySelector('#recently-viewed-products').dispatchEvent(new Event('pointerenter'));new Promise(resolve=>setTimeout(resolve,600))`);
 const pausedScroll=await evaluate(`document.querySelector('#recently-viewed-products').scrollLeft`);
 await evaluate(`new Promise(resolve=>setTimeout(resolve,4200))`);
 assert.equal(await evaluate(`document.querySelector('#recently-viewed-products').scrollLeft`),pausedScroll);
 await send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
 await evaluate(`document.querySelector('#recently-viewed-products').dispatchEvent(new Event('pointerleave'))`);
 const reducedScroll=await evaluate(`document.querySelector('#recently-viewed-products').scrollLeft`);
 await evaluate(`new Promise(resolve=>setTimeout(resolve,4200))`);
 assert.equal(await evaluate(`document.querySelector('#recently-viewed-products').scrollLeft`),reducedScroll);
 await send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'no-preference'}]});
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
 // Custom validation must recover after editing; a stale error otherwise blocks native submit.
 product.versions=[{id:11,name:'256GB',specifications:[]},{id:12,name:'512GB',specifications:[]}];
 product.variants=[{id:21,sku:'TEST-OLD',version_id:11,version_name:'256GB',color_name:'Blue',color_hex:'#2563eb',unit_price:200000,status:'ACTIVE',image_urls:[]},
  {id:23,sku:'TEST-COPY',version_id:12,version_name:'512GB',color_name:'White',color_hex:'#ffffff',unit_price:200000,status:'ACTIVE',image_urls:['/assets/stockflow.svg']}];
 const galleryUrls=[
  'https://cdn2.cellphones.com.vn/x/media/catalog/product/i/p/iphone-17-pro-max_3.jpg',
  'https://cdn2.cellphones.com.vn/insecure/rs:fill:0:358/q:90/plain/https://cellphones.com.vn/media/catalog/product/i/p/iphone-17-pro-max_1_3.jpg',
  'https://cdn2.cellphones.com.vn/insecure/rs:fill:0:358/q:90/plain/https://cellphones.com.vn/media/catalog/product/i/p/iphone-17-pro-max-1_4.jpg',
  'https://cdn2.cellphones.com.vn/insecure/rs:fill:0:358/q:90/plain/https://cellphones.com.vn/media/catalog/product/i/p/iphone-17-pro-max-2_1_1.jpg',
  'https://cdn2.cellphones.com.vn/insecure/rs:fill:0:358/q:90/plain/https://cellphones.com.vn/media/catalog/product/i/p/iphone-17-pro-max-3.jpg'];
 const setGallery=async(selector,urls)=>{
  const edit=async function(selector,urls){const {app}=await import('/assets/modules/context.js');const input=document.querySelector(selector);input.value=urls.join('\n');input.dispatchEvent(new Event('input',{bubbles:true}));return {count:app.readGalleryInput(input).length,valid:input.validity.valid,message:input.validationMessage};};
  return evaluate('('+edit.toString()+')('+JSON.stringify(selector)+','+JSON.stringify(urls)+')');
 };
 await evaluate(`(async()=>{const {app}=await import('/assets/modules/context.js');await app.manageProductVariants(1,11,'colors');const f=document.querySelector('#product-variant-create');f.closest('details').open=true;f.elements.sku.value='TEST-NEW';f.elements.color_name.value='Blue';})()`);
 const createGallery='#product-variant-create textarea[name="image_urls"]',editGallery='.variant-admin-edit[data-variant-id="21"] textarea[name="image_urls"]';
 const ninePhotos=Array.from({length:9},(_,i)=>'https://example.test/image-'+i+'.jpg');
 assert.equal((await setGallery(createGallery,ninePhotos)).valid,false);
 await evaluate(`document.querySelector('#product-variant-create').requestSubmit()`);assert.equal(variantRequests.length,0);
 assert.deepEqual(await setGallery(createGallery,[...galleryUrls,'','']),{count:5,valid:true,message:''});
 await evaluate(`document.querySelector('#product-variant-create').requestSubmit()`);
 await until(`document.querySelector('.variant-admin-edit[data-variant-id="22"]')!==null`);
 assert.equal(variantRequests.length,1);assert.equal(variantRequests[0].method,'POST');assert.deepEqual(variantRequests[0].body.image_urls,galleryUrls);
 assert.equal((await setGallery(editGallery,[galleryUrls[0],galleryUrls[0]])).valid,false);
 assert.equal((await setGallery(editGallery,['javascript:alert(1)'])).valid,false);
 assert.deepEqual(await setGallery(editGallery,galleryUrls),{count:5,valid:true,message:''});
 await evaluate(`document.querySelector('.variant-admin-edit[data-variant-id="21"]').requestSubmit()`);
 await until(`(async()=>{const {app}=await import('/assets/modules/context.js');return app.state.products.get(1)?.variants.find(row=>row.id===21)?.image_urls.length===5 && !document.querySelector('.variant-admin-edit[data-variant-id="21"] button[type="submit"]').disabled;})()`);
 assert.equal(variantRequests.length,2);assert.equal(variantRequests[1].method,'PATCH');assert.deepEqual(variantRequests[1].body.image_urls,galleryUrls);
 assert.equal((await setGallery(createGallery,ninePhotos.slice(0,8))).valid,true);
 assert.equal((await setGallery(createGallery,ninePhotos)).valid,false);
 await evaluate(`(()=>{const f=document.querySelector('#product-variant-create');f.elements.copy_source.value='23';f.querySelector('[data-action="copy-product-color"]').click();})()`);
 assert.deepEqual(await evaluate(`(()=>{const input=document.querySelector('${createGallery}');return {value:input.value,valid:input.validity.valid,message:input.validationMessage};})()`),{value:'/assets/stockflow.svg',valid:true,message:''});
 await evaluate(`document.querySelector('#product-variants-dialog').close()`);
 console.log('PASS: gallery validation recovers after excess/duplicate/unsafe URLs, exact five nested CDN URLs submitted on create/update, eight-image boundary and copied gallery');
 await evaluate(`document.querySelector('.aftercare-sidebar [data-action="open-returns"]').click()`);
 await until(`document.querySelector('[data-action="review-return"]')!==null`);
 await evaluate(`document.querySelector('[data-action="review-return"]').click();document.querySelector('#return-review-form').elements.note.value='Đồng ý nhận hàng về để kiểm tra';document.querySelector('#return-review-form').requestSubmit()`);
 await until(`document.querySelector('#returns-list').textContent.includes('Đã chấp thuận')`);
 await screenshot('returns-admin-375');assert.equal(returnRequests[0].status,'APPROVED');
 assert.equal(exceptions.length,0,JSON.stringify(exceptions));console.log('PASS: Chrome layouts 320/375/768/1366, light/dark login, recovery, cart/GHN quote, address book/default checkout, password change, customer returns/admin approval');
})().catch(error=>{console.error(error);process.exitCode=1;}).finally(async()=>{try{if(socket?.readyState===1)await send('Browser.close',{},false);}catch{}socket?.close();browser?.kill();server.close();});
