'use strict';
/* Customer capabilities over the current router and existing Supabase records. */
const QGCustomer=(()=>{
  let orderSnapshot=null,cleanups=[],notificationTimer=null,notificationUser='',notificationBusy=false,knownNotifications=null,unread=0;
  const actor=()=>{const u=S.get();return u?u.userId+':'+u.authUserId:''};
  const owned=(key,ticket)=>actor()===key&&ticket===routeVersion;
  const safeImage=value=>{const s=String(value||'');return /^https:\/\//i.test(s)||/^data:image\/(jpeg|png|webp);base64,[a-z0-9+/=]+$/i.test(s)?s:''};
  const phone=value=>String(value||'').replace(/[^+\d]/g,'');
  const linkPhone=(value,label)=>phone(value)?`<a class="lk" href="tel:${esc(phone(value))}">${esc(label)}</a>`:'';
  const date=value=>value?new Date(value).toLocaleString('th-TH'):'';
  const CHAT_AFTER_COMPLETE_MS=30*60*1000;
  function chatDeadline(order){if(!order||order.status!=='completed')return null;const at=Date.parse(order.completed_at||order.updated_at||'');return Number.isFinite(at)?at+CHAT_AFTER_COMPLETE_MS:0}
  function chatAvailable(order){if(!order||order.status==='cancelled')return false;const deadline=chatDeadline(order);return order.status!=='completed'||(Number.isFinite(deadline)&&deadline>Date.now())}
  function chatMinutesLeft(order){const deadline=chatDeadline(order);return deadline?Math.max(0,Math.ceil((deadline-Date.now())/60000)):0}
  const note=value=>String(value||'').replace(/^__QT_ORDER_STATUS__=[^\n\\]*(?:\\n|\n|$)/,'');
  function leave(){for(const f of cleanups.splice(0))try{f()}catch(e){}}
  function poll(task,delay=5000){let stopped=false,timer;const run=async()=>{if(stopped)return;try{if(!document.hidden&&navigator.onLine!==false)await task()}catch(e){}finally{if(!stopped)timer=setTimeout(run,delay)}};timer=setTimeout(run,delay);cleanups.push(()=>{stopped=true;clearTimeout(timer)});}
  async function context(id){return db('rpc/qg_customer_order_context',{method:'POST',body:{p_order_id:id}})}
  async function cancel(id,market=false){if(!confirm('ยกเลิกคำสั่งซื้อนี้?'))return;const key=actor();try{await db(market?'rpc/qg_customer_cancel_market_order':'rpc/qg_customer_cancel_order',{method:'POST',body:market?{p_market_order_id:id}:{p_order_id:id}})}catch(e){if(actor()===key)toast(e.message);return}if(actor()===key){toast('ยกเลิกออเดอร์แล้ว');try{route()}catch(e){console.warn('customer cancel post-commit render failed',e)}}}
  async function favorite(kind,id,button){if(!S.get())return go('login');if(!['shop','product'].includes(kind))return;const key=actor();button.disabled=true;try{const rows=await db('qg_customer_favorites?select=id&user_id=eq.'+encodeURIComponent(S.get().userId)+'&'+kind+'_id=eq.'+encodeURIComponent(id));if(actor()!==key)return;const yes=rows.length>0;await db(yes?'qg_customer_favorites?id=eq.'+encodeURIComponent(rows[0].id):'qg_customer_favorites',{method:yes?'DELETE':'POST',body:yes?undefined:{user_id:S.get().userId,[kind+'_id']:id}});if(actor()===key&&button.isConnected){button.setAttribute('aria-pressed',String(!yes));button.textContent=yes?'♡':'♥'}}catch(e){if(actor()===key)toast('บันทึกรายการโปรดไม่สำเร็จ')}finally{if(actor()===key&&button.isConnected)button.disabled=false}}
  function favoriteButton(kind,id,yes=false){return `<button class="lk fav" aria-label="บันทึกรายการโปรด" aria-pressed="${yes}" onclick="QGCustomer.favorite('${kind}','${esc(id)}',this)">${yes?'♥':'♡'}</button>`}
  async function loadFavorites(){const u=S.get();return u?db('qg_customer_favorites?select=id,shop_id,product_id&user_id=eq.'+encodeURIComponent(u.userId)+'&order=created_at.desc'):[]}
  async function reviews(id){return db('rpc/qg_public_shop_reviews',{method:'POST',body:{p_shop_id:id}})}
  function reviewList(data){return `<p>★ ${Number(data.average||0).toFixed(1)} · ${Number(data.count||0)} รีวิว</p>${(data.items||[]).map(r=>`<article class="review"><b>${esc(r.customer_name)} · ${Number(r.rating)} ★</b><p>${esc(r.comment||'')}</p><small>${esc(date(r.updated_at))}</small></article>`).join('')}`}
  async function renderFavorites(_,ticket=routeVersion){const key=actor(),rows=await loadFavorites(),shops=await loadShops();if(!owned(key,ticket))return;const pids=rows.filter(r=>r.product_id).map(r=>r.product_id);const products=pids.length?await db('products?select=id,shop_id,name,price,delivery_price,image&available=eq.true&id=in.('+pids.map(encodeURIComponent).join(',')+')'):[];if(!owned(key,ticket))return;layout(`<div class="pt"><button class="back" onclick="go('profile')">${ico('back')}</button><h1>รายการโปรด</h1></div>${rows.length?rows.map(r=>{const s=shops.find(s=>s.id===r.shop_id),p=products.find(p=>p.id===r.product_id);const label=s?.shop_name||p?.name||'รายการนี้ยังไม่พร้อมให้บริการ',shop=s?.id||p?.shop_id;return `<section class="card"><div class="sr"><button class="lk" ${shop?'':'disabled'} onclick="go('shop/${esc(shop||'')}')">${esc(label)}</button>${favoriteButton(r.shop_id?'shop':'product',r.shop_id||r.product_id,true)}</div>${p?baht(p.delivery_price??p.price):''}</section>`}).join(''):'<p class="empty">ยังไม่มีรายการโปรด</p>'}`,'');}
  async function renderPromotions(_,ticket=routeVersion){const data=await db('rpc/qg_public_promotions',{method:'POST',body:{}});if(ticket!==routeVersion)return;layout(`<div class="pt"><button class="back" onclick="history.back()">${ico('back')}</button><h1>โปรโมชั่นจากร้าน</h1></div>${data.length?data.map(p=>`<section class="card"><button class="lk" onclick="go('shop/${esc(p.shop_id)}')">${esc(p.shop_name)}</button><h2>${esc(p.title)}</h2><p>${esc(p.description)}</p></section>`).join(''):'<p class="empty">ยังไม่มีโปรโมชั่นที่เปิดใช้งาน</p>'}`,'home')}
  function badge(){const b=$('customer-notification-count');if(b){b.textContent=unread>99?'99+':unread;b.hidden=!unread}}
  async function notificationsTick(){const key=actor(),u=S.get();if(!u||notificationBusy||document.hidden||navigator.onLine===false)return;if(notificationUser!==key){notificationUser=key;knownNotifications=null;unread=0}notificationBusy=true;try{const rows=await db('notifications?select=id,title,message,type,reference_id,is_read,created_at&user_id=eq.'+encodeURIComponent(u.userId)+'&order=created_at.desc&limit=100');if(actor()!==key)return;unread=rows.filter(n=>!n.is_read).length;badge();if(knownNotifications){for(const n of rows.filter(n=>!n.is_read&&!knownNotifications.has(n.id)).slice(0,3)){toast(QueueGoOrderNumber.replaceInText((n.title||'แจ้งเตือน')+' · '+(n.message||'')));if(/มาถึง|ถึงจุดส่ง/.test(n.title+' '+n.message)){if(navigator.vibrate)navigator.vibrate([100,80,100]);if(localStorage.getItem('qg_customer_sound_'+u.userId)==='on'&&'speechSynthesis' in window){const speech=new SpeechSynthesisUtterance(n.title||'Rider มาถึงแล้ว');speech.lang='th-TH';speechSynthesis.speak(speech);}}}}const changed=knownNotifications&&rows.some(n=>!knownNotifications.has(n.id));knownNotifications=new Set(rows.map(n=>n.id));if(changed&&location.hash==='#notifications')await renderNotifications(null,routeVersion);}catch(e){}finally{notificationBusy=false}}
  function syncNotifications(){if(!S.get()){notificationUser='';knownNotifications=null;unread=0;clearTimeout(notificationTimer);notificationTimer=null;badge();return}badge();if(notificationTimer)return;const run=async()=>{notificationTimer=null;await notificationsTick();if(S.get())notificationTimer=setTimeout(run,8000)};notificationTimer=setTimeout(run,0)}
  async function openNotification(id,reference,wasUnread=false){const key=actor();try{await db('notifications?id=eq.'+encodeURIComponent(id)+'&user_id=eq.'+encodeURIComponent(S.get().userId),{method:'PATCH',body:{is_read:true}})}catch(e){if(actor()===key)toast('เปิดแจ้งเตือนไม่สำเร็จ');return}if(actor()!==key)return;if(wasUnread){unread=Math.max(0,unread-1);badge()}try{await notificationsTick()}catch(e){console.warn('customer notification post-read refresh failed',e)}try{if(reference){const rows=await db('orders?select=id&customer_id=eq.'+encodeURIComponent(S.get().userId)+'&id=eq.'+encodeURIComponent(reference));if(actor()!==key)return;if(rows[0])return go('order/'+reference)}route()}catch(e){console.warn('customer notification post-read navigation failed',e);try{route()}catch(_){}}}
  async function renderNotifications(_,ticket=routeVersion){const key=actor(),u=S.get(),rows=await db('notifications?select=id,title,message,type,reference_id,is_read,created_at&user_id=eq.'+encodeURIComponent(u.userId)+'&order=created_at.desc&limit=100');if(!owned(key,ticket))return;const pending=rows.filter(n=>!n.is_read);if(pending.length){try{await db('notifications?user_id=eq.'+encodeURIComponent(u.userId)+'&is_read=eq.false',{method:'PATCH',body:{is_read:true}});if(!owned(key,ticket))return;rows.forEach(n=>n.is_read=true);unread=0;badge()}catch(e){unread=pending.length;badge()}}layout(`<div class="pt"><button class="back" onclick="go('home')">${ico('back')}</button><h1>แจ้งเตือน</h1></div><button class="lk" onclick="QGCustomer.markRead(this)">อ่านทั้งหมด</button>${rows.length?rows.map(n=>`<button class="notification card ${n.is_read?'':'unread'}" onclick="QGCustomer.openNotification('${esc(n.id)}','${esc(n.reference_id||'')}',${n.is_read?'false':'true'})"><b>${esc(QueueGoOrderNumber.replaceInText(n.title))}</b><p>${esc(QueueGoOrderNumber.replaceInText(n.message))}</p><small>${esc(date(n.created_at))}</small></button>`).join(''):'<p class="empty">ยังไม่มีการแจ้งเตือน</p>'}`,'');badge();}
  async function markRead(button){button.disabled=true;const key=actor();try{await db('notifications?user_id=eq.'+encodeURIComponent(S.get().userId)+'&is_read=eq.false',{method:'PATCH',body:{is_read:true}})}catch(e){if(actor()===key)toast('บันทึกไม่สำเร็จ');if(actor()===key&&button.isConnected)button.disabled=false;return}if(actor()===key){unread=0;badge();knownNotifications=null;try{route()}catch(e){console.warn('customer notifications post-read refresh failed',e)}}if(actor()===key&&button.isConnected)button.disabled=false}
  async function deleteAccount(button){
    const u=S.get();if(!u)return go('login');
    if(!confirm('ต้องการลบบัญชี QueueGo และข้อมูลส่วนบุคคลถาวรใช่หรือไม่?'))return;
    if(!confirm('ยืนยันครั้งสุดท้าย: การลบบัญชีไม่สามารถย้อนกลับได้'))return;
    if(button)button.disabled=true;
    try{
      const accessToken=await token(true);
      await QueueGoAccountDeletion.request(accessToken);
      await QueueGoPush.unsubscribeLocal().catch(()=>{});
      const keys=[
        'qt_cart_'+u.userId,
        'qg_market_cart_'+u.userId,
        'qg_pending_checkout_'+u.userId,
        'qg_pending_market_checkout_'+u.userId,
        'qg_customer_support_pending_'+u.userId,
        'qg_customer_sound_'+u.userId
      ];
      for(const key of keys)try{localStorage.removeItem(key)}catch(e){}
      S.clear();cart=readCart();marketCart=readMarketCart();syncNotifications();
      location.hash='#login';location.reload();
    }catch(e){
      toast(e?.message||'ลบบัญชีไม่สำเร็จ');
      if(button?.isConnected)button.disabled=false;
    }
  }
  function renderProfile(){const u=S.get()||{};layout(`<div class="pf"><div class="av">${safeImage(u.photo)?`<img src="${esc(safeImage(u.photo))}" alt="รูปโปรไฟล์">`:ini(u.name)}</div><div><h1>${esc(u.name||'ลูกค้า')}</h1><p>${esc(u.phone||u.email||'')}</p><span class="acc">บัญชีลูกค้า</span></div></div><div class="pl">${[['map','ที่อยู่จัดส่ง'],['orders','ออเดอร์ของฉัน'],['notifications','การแจ้งเตือน'],['favorites','รายการโปรด'],['promotion','โปรโมชั่นจากร้าน'],['support','ติดต่อฝ่ายช่วยเหลือ']].map(([path,label])=>`<button class="pw" onclick="go('${path}')"><b>${label}</b></button>`).join('')}<button class="pw" onclick="QGCustomer.toggleSound(this)"><b>เสียงแจ้งเตือน Rider ถึง</b><span>${localStorage.getItem('qg_customer_sound_'+u.userId)==='on'?'เปิด':'ปิด'}</span></button><p class="sm">ชำระเงินสดเมื่อรับสินค้า</p><button class="pw" onclick="QueueGoPush.toggle(this).then(on=>toast(on?'เปิดการแจ้งเตือนเบื้องหลังแล้ว':'ปิดการแจ้งเตือนเบื้องหลังแล้ว')).catch(e=>toast(e.message||'เปิดการแจ้งเตือนไม่สำเร็จ'))"><b>การแจ้งเตือนเบื้องหลัง</b><span>รับสถานะออเดอร์แม้ไม่ได้เปิดหน้านี้</span></button><button class="pw" onclick="this.disabled=true;toast('ระบบจะส่งแจ้งเตือนใน 7 วินาที กด Home เพื่อทดสอบได้เลย');QueueGoPush.test().catch(e=>toast(e.message||'ทดสอบแจ้งเตือนไม่สำเร็จ')).finally(()=>this.disabled=false)"><b>ทดสอบการแจ้งเตือน</b><span>ตรวจว่าแจ้งเตือนเข้าเมื่อแอปอยู่เบื้องหลัง</span></button><button class="pw" onclick="QueueGoAccountDeletion.openPrivacy()"><b>นโยบายความเป็นส่วนตัว</b></button><button class="pw" onclick="QGCustomer.deleteAccount(this)"><b>ลบบัญชีถาวร</b><span>ลบ Auth และข้อมูลส่วนบุคคล</span></button><button class="pw" onclick="QueueGoPush.disable().finally(()=>{S.clear();cart=readCart();QGCustomer.syncNotifications();go('login')})"><b>ออกจากระบบ</b></button></div>`,'')}
  async function renderOrder(id,ticket=routeVersion){
    const key=actor(),u=S.get();
    const rows=await db('orders?select=*&id=eq.'+encodeURIComponent(id)+'&customer_id=eq.'+encodeURIComponent(u.userId)+'&limit=1'),o=rows[0];
    if(!owned(key,ticket))return;
    if(!o)return layout('<p class="empty">ไม่พบออเดอร์</p>','orders');

    const [items,ctx]=await Promise.all([loadOrderItems(id),context(id)]);
    if(!owned(key,ticket))return;

    const flow=['pending','searching_rider','rider_assigned','preparing','ready','picked_up','in_progress','completed'],
      idx=flow.indexOf(o.status),
      closed=['completed','cancelled'].includes(o.status),
      r=ctx.rider,
      chatOpen=chatAvailable(o),
      stage=o.status==='completed'?4:o.status==='in_progress'||o.status==='picked_up'?3:['rider_assigned','preparing','ready'].includes(o.status)?2:o.status==='searching_rider'?1:0,
      stageLabels=['สั่งซื้อ','หารายเดอร์','รับสินค้า','กำลังส่ง','สำเร็จ'],
      orderNote=note(o.note).trim();

    layout(`<div class="qg-order-page">
      <div class="pt"><button class="back" onclick="go('orders')">${ico('back')}</button><h1>${esc(customerOrderNumber(o))}</h1></div>

      <section class="qg-order-hero">
        <div class="qg-order-hero-top">
          <div><span class="qg-order-kicker">สถานะล่าสุด</span><strong class="qg-order-status">${esc(STATUS_LABEL[o.status]||o.status)}</strong></div>
          <span class="qg-order-time">${esc(date(o.created_at))}</span>
        </div>
        ${o.status!=='cancelled'?`<div class="qg-order-progress">${stageLabels.map((label,i)=>`<span class="qg-order-step ${i<=stage?'on':''}">${label}</span>`).join('')}</div>`:''}
        <div class="qg-order-place">
          <small>ส่งไปที่</small>
          <b>${esc(o.delivery_address||'ยังไม่มีที่อยู่จัดส่ง')}</b>
          <span>ร้าน ${esc(ctx.shop?.name||'ร้านค้า')}${orderNote?` · ${esc(orderNote)}`:''}</span>
        </div>
        <div class="qg-order-hero-actions">
          ${linkPhone(ctx.shop?.phone,'ติดต่อร้าน')}
          ${!o.rider_id&&['pending','searching_rider'].includes(o.status)?`<button class="lk qg-danger" onclick="QGCustomer.cancel('${esc(id)}')">ยกเลิกคำสั่งซื้อ</button>`:''}
          <button class="lk" onclick="go('support/${esc(id)}')">แจ้งปัญหา</button>
        </div>
      </section>

      ${r?`<section class="qg-order-card">
        <div class="qg-order-section-head"><b>Rider ของคุณ</b><small>${closed?'งานสิ้นสุดแล้ว':'กำลังดูแลออเดอร์นี้'}</small></div>
        <div class="qg-order-rider">
          ${safeImage(r.photo)?`<img class="qg-order-rider-photo" src="${esc(safeImage(r.photo))}" alt="รูป Rider">`:`<div class="qg-order-rider-fallback">${esc(String(r.name||'R').slice(0,1).toUpperCase())}</div>`}
          <div class="qg-order-rider-copy"><b>${esc(r.name||'Rider')}</b><span>${esc([r.vehicle_type,r.vehicle_plate].filter(Boolean).join(' ')||'ข้อมูลรถกำลังอัปเดต')}</span></div>
        </div>
        <div class="qg-order-rider-actions">
          ${!closed?linkPhone(r.phone,'โทรหา Rider'):''}
          ${chatOpen?`<button class="lk qg-chat" onclick="go('order-chat/${esc(id)}')">${o.status==='completed'?`แชทต่อได้อีก ${chatMinutesLeft(o)} นาที`:'แชทกับ Rider'}</button>`:o.status==='completed'?'<span class="sm">แชทปิดแล้วหลังจบงาน 30 นาที</span>':''}
        </div>
      </section>`:''}

      <section class="qg-order-card qg-order-map-card">
        <div class="qg-order-map-head"><b>ติดตามการจัดส่ง</b><span class="qg-live-dot">อัปเดตตำแหน่ง</span></div>
        <div id="customer-tracking-map" class="mapbox qg-order-map"></div>
        <p class="sm qg-order-location-status" id="tracking-location-status">กำลังโหลดตำแหน่งล่าสุด</p>
      </section>

      <section class="qg-order-card">
        <div class="qg-order-section-head"><b>รายการสินค้า</b><small>${items.reduce((sum,i)=>sum+Number(i.quantity||0),0)} ชิ้น</small></div>
        <div class="qg-order-items">
          ${items.map(i=>`<div class="qg-order-item-row"><div class="qg-order-item-copy"><b>${esc(i.item_name)}</b><small>จำนวน ${Number(i.quantity||0).toLocaleString('th-TH')}</small></div><span class="qg-order-item-price">${baht(i.total_price)}</span></div>`).join('')}
        </div>
        <div class="qg-order-summary">
          <div class="qg-order-summary-row"><span>ค่าสินค้า</span><span>${baht(o.subtotal)}</span></div>
          <div class="qg-order-summary-row"><span>ค่าจัดส่ง</span><span>${baht(o.delivery_fee)}</span></div>
          ${Number(o.bundle_customer_savings)>0?`<p class="qg-order-saving">ประหยัดจากงานพ่วง ${baht(o.bundle_customer_savings)}</p>`:''}
          <div class="qg-order-total"><span>ยอดรวม</span><strong>${baht(o.total_amount)}</strong></div>
        </div>
      </section>

      ${o.status==='completed'?'<section class="qg-order-card" id="customer-review">กำลังโหลดรีวิว…</section>':''}
    </div>`,'orders');

    orderSnapshot={id,actor:key,version:orderVersion(o)};
    track(o,ctx,ticket,key);
    if(o.status==='completed')await reviewForm(o,ticket,key);
  }
  function track(o,ctx,ticket,key){const host=$('customer-tracking-map');if(!host)return;if(typeof longdo==='undefined'){const status=$('tracking-location-status');if(status)status.textContent='ยังโหลดแผนที่ไม่ได้ กรุณาลองเปิดออเดอร์ใหม่';return;}const map=new longdo.Map({placeholder:host,language:'th',zoom:14});map.Ui.LayerSelector.visible(false);let marker=null;const points=[];function point(lat,lon,title,color,record=true){if(!qgIsValidCoordinate(lat,lon,true))return null;const loc={lat:Number(lat),lon:Number(lon)};const m=new longdo.Marker(loc,{title,icon:{html:`<div style="width:20px;height:20px;border-radius:50%;background:${color};border:3px solid white"></div>`}});map.Overlays.add(m);if(record)points.push(loc);return m}point(o.pickup_latitude,o.pickup_longitude,'ร้านค้า','#087556');point(o.delivery_latitude,o.delivery_longitude,'จุดส่ง','#e6002d');if(points[0])map.location(points[0],true);
    const draw=r=>{if(!owned(key,ticket)||!host.isConnected)return;const live=r&&qgIsValidCoordinate(r.latitude,r.longitude,true)&&!['completed','cancelled'].includes(o.status);if(marker){map.Overlays.remove(marker);marker=null}if(live){marker=point(r.latitude,r.longitude,'Rider · ตำแหน่งล่าสุด','#1677ff',false);$('tracking-location-status').textContent='ตำแหน่งล่าสุด · '+date(r.updated_at)+(Date.now()-new Date(r.updated_at).getTime()>120000?' · ยังไม่ได้อัปเดตใหม่':'')}else $('tracking-location-status').textContent=['completed','cancelled'].includes(o.status)?'ออเดอร์จบแล้ว':'รอข้อมูลตำแหน่งจริงจาก Rider'};draw(ctx.rider);cleanups.push(()=>{map.Overlays.clear();map.pause(true);host.replaceChildren()});if(!['completed','cancelled'].includes(o.status))poll(async()=>{const next=await context(o.id);draw(next.rider)},7000);
  }
  async function reviewForm(o,ticket,key){
    const mine=await db('reviews?select=*&order_id=eq.'+encodeURIComponent(o.id)+'&customer_id=eq.'+encodeURIComponent(S.get().userId)+'&limit=1');
    if(!owned(key,ticket)||!$('customer-review'))return;
    const r=mine[0];
    let shopCategory='';
    try{
      const shopRows=typeof loadShops==='function'?await loadShops():[];
      shopCategory=String((shopRows||[]).find(s=>String(s.id)===String(o.shop_id))?.public_category||'').toLowerCase();
    }catch(_){}
    if(!owned(key,ticket)||!$('customer-review'))return;
    const showFoodRating=['food','cafe'].includes(shopCategory);
    const ratingText=value=>['','แย่มาก','พอใช้','ปานกลาง','ดี','ดีมาก'][Number(value)||0]||'';
    const stars=(name,label,value,optional=false)=>{
      const selected=Number(value)||0;
      return `<div class="qg-review-rating">
        <span class="qg-review-rating-label">${esc(label)}</span>
        <div class="qg-star-rating" data-rating-name="${esc(name)}" role="radiogroup" aria-label="${esc(label)}">
          ${[1,2,3,4,5].map(n=>`<button type="button" class="${n<=selected?'on':''}" data-value="${n}" role="radio" aria-checked="${n===selected}" aria-label="${n} ดาว">★</button>`).join('')}
        </div>
        <input type="hidden" name="${esc(name)}" value="${selected||''}">
        <small class="qg-review-rating-note" data-rating-note="${esc(name)}">${selected?ratingText(selected):(optional?'เลือกได้ถ้าต้องการ':'กรุณาเลือกคะแนน')}</small>
      </div>`;
    };
    $('customer-review').innerHTML=`<h2>${r?'รีวิวของคุณ':'ให้คะแนนออเดอร์นี้'}</h2>
      <form id="review-form" class="qg-review-form">
        ${stars('rating','คะแนนร้าน',r?.rating,false)}
        ${showFoodRating?stars('food','คะแนนอาหาร',r?.food_rating,true):''}
        ${stars('rider','คะแนน Rider',r?.rider_rating,true)}
        <textarea class="ta" name="comment" maxlength="1000" placeholder="ความคิดเห็น">${esc(r?.comment||'')}</textarea>
        <button class="prim" type="submit">บันทึกรีวิว</button>
      </form>
      ${r?'<button class="lk qg-review-delete" id="review-delete" type="button">ลบรีวิว</button>':''}`;

    const form=$('review-form');
    form.querySelectorAll('.qg-star-rating').forEach(group=>{
      const name=group.dataset.ratingName,input=form.elements[name],note=group.parentElement.querySelector('[data-rating-note="'+name+'"]');
      const buttons=[...group.querySelectorAll('button[data-value]')];
      const paint=value=>{
        const n=Number(value)||0;
        input.value=n?String(n):'';
        buttons.forEach(button=>{
          const v=Number(button.dataset.value),on=v<=n;
          button.classList.toggle('on',on);
          button.setAttribute('aria-checked',String(v===n));
        });
        if(note)note.textContent=n?ratingText(n):(name==='rating'?'กรุณาเลือกคะแนน':'เลือกได้ถ้าต้องการ');
      };
      buttons.forEach(button=>button.onclick=()=>paint(button.dataset.value));
    });

    form.onsubmit=async e=>{
      e.preventDefault();
      const f=e.currentTarget,b=f.querySelector('button[type="submit"]');
      const shopRating=Number(f.elements.rating?.value||0);
      if(shopRating<1||shopRating>5){toast('กรุณาเลือกคะแนนร้าน 1–5 ดาว');return}
      b.disabled=true;
      try{
        await db('rpc/qg_customer_save_review',{method:'POST',body:{
          p_order_id:o.id,
          p_rating:shopRating,
          p_food_rating:f.elements.food?.value?Number(f.elements.food.value):null,
          p_rider_rating:f.elements.rider?.value?Number(f.elements.rider.value):null,
          p_comment:f.elements.comment.value.trim()
        }});
      }catch(e){
        if(owned(key,ticket))toast(e.message);
        if(owned(key,ticket)&&b.isConnected)b.disabled=false;
        return;
      }
      if(owned(key,ticket)){
        toast('บันทึกรีวิวแล้ว');
        try{await reviewForm(o,ticket,key)}catch(refreshErr){console.warn('customer review post-save refresh failed',refreshErr)}
      }
      if(owned(key,ticket)&&b.isConnected)b.disabled=false;
    };
    if(r)$('review-delete').onclick=async()=>{
      if(!confirm('ลบรีวิวนี้?'))return;
      try{await db('reviews?id=eq.'+encodeURIComponent(r.id)+'&customer_id=eq.'+encodeURIComponent(S.get().userId),{method:'DELETE'})}
      catch(e){if(owned(key,ticket))toast('ลบรีวิวไม่สำเร็จ');return}
      if(owned(key,ticket))try{await reviewForm(o,ticket,key)}catch(refreshErr){console.warn('customer review post-delete refresh failed',refreshErr)}
    };
  }
  const orderVersion=o=>JSON.stringify([o.status,o.rider_id,o.total_amount,o.delivery_fee,o.bundle_customer_savings,o.note]);
  async function needsOrderRefresh(id){if(!orderSnapshot||orderSnapshot.id!==id||orderSnapshot.actor!==actor()||!$('customer-tracking-map'))return true;const key=actor(),hash=location.hash;try{const rows=await db('orders?select=status,rider_id,total_amount,delivery_fee,bundle_customer_savings,note&id=eq.'+encodeURIComponent(id)+'&customer_id=eq.'+encodeURIComponent(S.get().userId)+'&limit=1');if(actor()!==key||location.hash!==hash)return false;return !rows[0]||orderVersion(rows[0])!==orderSnapshot.version}catch(e){return false}}
  const chatKey=id=>'qg_customer_chat_pending_'+S.get()?.userId+'_'+id;
  function chatBody(message){const text=String(message||'');if(text.startsWith('__IMG__')){const image=safeImage(text.slice(7));return image?`<img class="chat-image" src="${esc(image)}" alt="รูปในแชท">`:'รูปภาพไม่พร้อมแสดง'}return esc(text)}
  async function customerChatGate(id,ticket,key){
    let state;
    try{state=await QueueGoUGC.state(id)}catch(e){if(owned(key,ticket))layout('<p class="empty">ตรวจสอบความปลอดภัยแชตไม่สำเร็จ กรุณาลองใหม่</p>','orders');return null}
    if(!owned(key,ticket))return null;
    if(!state?.accepted){
      layout(`<div class="pt"><button class="back" onclick="go('order/${esc(id)}')">${ico('back')}</button><h1>กติกาการแชต</h1></div><section class="card"><h2>ก่อนเริ่มแชต</h2><p>ใช้แชตเพื่อประสานงานออเดอร์เท่านั้น ห้ามคุกคาม สแปม หลอกลวง ส่งเนื้อหาไม่เหมาะสม หรือเผยแพร่ข้อมูลส่วนบุคคลที่ไม่จำเป็น</p><p><a href="${esc(QueueGoUGC.guidelinesUrl())}" target="_blank" rel="noopener">อ่านกติกาชุมชน QueueGo</a></p><button class="prim" id="customer-chat-accept">ยอมรับกติกาและเปิดแชต</button></section>`,'orders');
      $('customer-chat-accept').onclick=async e=>{e.currentTarget.disabled=true;try{await QueueGoUGC.accept();await renderChat(id,ticket)}catch(err){toast(err.message||'ยอมรับกติกาไม่สำเร็จ');e.currentTarget.disabled=false}};
      return null;
    }
    if(state.blockedByMe||state.blockedMe){
      layout(`<div class="pt"><button class="back" onclick="go('order/${esc(id)}')">${ico('back')}</button><h1>แชทถูกจำกัด</h1></div><section class="card"><p>${state.blockedByMe?'คุณบล็อก Rider คนนี้ไว้':'คู่สนทนาได้บล็อกการแชตไว้'} การบล็อกแชตไม่ยกเลิกการส่งออเดอร์</p>${state.blockedByMe?'<button class="prim" id="customer-chat-unblock">ปลดบล็อกและเปิดแชต</button>':''}</section>`,'orders');
      if(state.blockedByMe)$('customer-chat-unblock').onclick=async e=>{e.currentTarget.disabled=true;try{await QueueGoUGC.unblock(id);await renderChat(id,ticket)}catch(err){toast(err.message||'ปลดบล็อกไม่สำเร็จ');e.currentTarget.disabled=false}};
      return null;
    }
    return state;
  }
  function customerChatReport(id,messageId){
    document.getElementById('customer-chat-report-dialog')?.remove();
    const d=document.createElement('dialog');d.id='customer-chat-report-dialog';
    d.innerHTML=`<form method="dialog" style="min-width:min(86vw,420px);display:grid;gap:12px"><h3 style="margin:0">รายงานเนื้อหาแชต</h3><label>เหตุผล<select class="in" name="reason"><option value="harassment">คุกคาม / กลั่นแกล้ง</option><option value="inappropriate">เนื้อหาไม่เหมาะสม</option><option value="spam">สแปม</option><option value="fraud">หลอกลวง / ฉ้อโกง</option><option value="safety">ความปลอดภัย</option><option value="other">อื่น ๆ</option></select></label><label>รายละเอียด<textarea class="in" name="details" maxlength="1000" rows="4" placeholder="อธิบายเพิ่มเติม (ไม่บังคับ)"></textarea></label><div style="display:flex;gap:8px"><button value="cancel">ยกเลิก</button><button class="prim" type="button" id="customer-chat-report-send">ส่งรายงาน</button></div></form>`;
    document.body.appendChild(d);d.showModal();
    d.querySelector('#customer-chat-report-send').onclick=async e=>{e.currentTarget.disabled=true;try{const form=d.querySelector('form');await QueueGoUGC.report(id,messageId||null,form.elements.reason.value,form.elements.details.value.trim());d.close();d.remove();toast('ส่งรายงานให้ QueueGo ตรวจสอบแล้ว')}catch(err){toast(err.message||'ส่งรายงานไม่สำเร็จ');e.currentTarget.disabled=false}};
    d.addEventListener('close',()=>d.remove(),{once:true});
  }
  async function customerChatBlock(id,ticket){
    if(!confirm('บล็อก Rider คนนี้จากการส่งข้อความใหม่? การบล็อกไม่ยกเลิกออเดอร์'))return;
    try{await QueueGoUGC.block(id);toast('บล็อกคู่สนทนาแล้ว');await renderChat(id,ticket)}catch(e){toast(e.message||'บล็อกไม่สำเร็จ')}
  }
  async function renderChat(id,ticket=routeVersion){const key=actor(),u=S.get(),rows=await db('orders?select=id,status,rider_id,completed_at,updated_at&customer_id=eq.'+encodeURIComponent(u.userId)+'&id=eq.'+encodeURIComponent(id)+'&limit=1');if(!owned(key,ticket))return;const o=rows[0];if(!o||!o.rider_id)return layout('<p class="empty">แชทเปิดเมื่อ Rider รับงานแล้ว</p>','orders');if(!chatAvailable(o)){try{localStorage.removeItem(chatKey(id))}catch(e){}return layout(`<div class="pt"><button class="back" onclick="go('order/${esc(id)}')">${ico('back')}</button><h1>แชทกับ Rider</h1></div><p class="empty">แชทปิดแล้ว ระบบให้ติดต่อกันได้ 30 นาทีหลังจบงาน</p>`,'orders')}const moderation=await customerChatGate(id,ticket,key);if(!moderation)return;const deadline=chatDeadline(o),completed=o.status==='completed';layout(`<div class="pt"><button class="back" onclick="go('order/${esc(id)}')">${ico('back')}</button><h1>แชทกับ Rider</h1></div><div style="display:flex;gap:8px;flex-wrap:wrap;margin:0 0 10px"><button type="button" id="customer-chat-report-user">รายงานคู่สนทนา</button><button type="button" id="customer-chat-block-user">บล็อก Rider</button><a href="${esc(QueueGoUGC.guidelinesUrl())}" target="_blank" rel="noopener">กติกาแชต</a></div>${completed?`<p class="sm">หลังจบงานแชทจะปิดและถูกลบอัตโนมัติ · เหลือประมาณ ${chatMinutesLeft(o)} นาที</p>`:''}<div id="customer-chat-messages" class="chat-messages"></div><form id="customer-chat-form"><input class="in" name="message" maxlength="500" placeholder="พิมพ์ข้อความ"><input type="file" name="photo" accept="image/jpeg,image/png,image/webp"><button class="prim">ส่งข้อความ / ตรวจผลข้อความเดิม</button><p class="sm" id="customer-chat-error" role="status"></p></form>`,'orders');$('customer-chat-report-user').onclick=()=>customerChatReport(id,null);$('customer-chat-block-user').onclick=()=>customerChatBlock(id,ticket);if(deadline){const timer=setTimeout(()=>{if(owned(key,ticket)&&location.hash==='#order-chat/'+id)renderChat(id,ticket)},Math.max(250,Math.min(2147483000,deadline-Date.now()+250)));cleanups.push(()=>clearTimeout(timer))}let last='';const refresh=async()=>{const messages=await db('order_chat_messages?select=id,sender_id,message,created_at&order_id=eq.'+encodeURIComponent(id)+'&order=created_at.desc&limit=100');if(!owned(key,ticket)||!$('customer-chat-messages'))return;messages.reverse();const version=JSON.stringify(messages);if(version===last)return;last=version;const box=$('customer-chat-messages'),bottom=box.scrollHeight-box.scrollTop-box.clientHeight<80;box.innerHTML=messages.length?messages.map(m=>`<div class="chat-bubble ${m.sender_id===u.userId?'mine':''}">${chatBody(m.message)}<small>${esc(date(m.created_at))}</small>${m.sender_id!==u.userId?`<button type="button" data-chat-report="${esc(m.id)}" style="display:block;margin-top:4px">รายงาน</button>`:''}</div>`).join(''):'<p class="empty">ยังไม่มีข้อความ</p>';box.querySelectorAll('[data-chat-report]').forEach(btn=>btn.onclick=()=>customerChatReport(id,btn.dataset.chatReport));if(bottom)box.scrollTop=box.scrollHeight};await refresh();if(!owned(key,ticket))return;poll(refresh,4000);
    $('customer-chat-form').onsubmit=async e=>{e.preventDefault();const f=e.currentTarget,b=f.querySelector('button'),storageKey=chatKey(id);if(b.disabled)return;b.disabled=true;try{let payload;try{payload=JSON.parse(localStorage.getItem(storageKey)||'null');if(payload&&(payload.order_id!==id||payload.sender_id!==u.userId||typeof payload.id!=='string'||typeof payload.message!=='string'))throw Error('invalid payload')}catch(e){localStorage.removeItem(storageKey);payload=null}if(!payload){let text=f.elements.message.value.trim();const file=f.elements.photo.files[0];if(file)text='__IMG__'+await compressImage(file);if(!owned(key,ticket))return;if(!text)return;payload={id:uuid(),order_id:id,sender_id:u.userId,message:text};localStorage.setItem(storageKey,JSON.stringify(payload));}if(payload.order_id!==id||payload.sender_id!==u.userId)throw Error('ข้อความเดิมไม่ตรงบัญชี');const accessToken=await token();if(!owned(key,ticket))return;const sent=await db('order_chat_messages?select=id&id=eq.'+encodeURIComponent(payload.id)+'&order_id=eq.'+encodeURIComponent(id),{token:accessToken});if(!owned(key,ticket))return;if(!sent.length)await db('order_chat_messages',{method:'POST',token:accessToken,body:payload});localStorage.removeItem(storageKey);if(!owned(key,ticket))return;f.reset();$('customer-chat-error').textContent='';try{await refresh()}catch(refreshErr){console.warn('customer chat post-send refresh failed',refreshErr)}}catch(e){if(e.status>=400&&e.status<500&&![408,409,429].includes(e.status))localStorage.removeItem(storageKey);if(owned(key,ticket))$('customer-chat-error').textContent=e.status?'ส่งข้อความไม่สำเร็จ กรุณาตรวจสถานะออเดอร์':'ยังไม่ได้รับผล กดตรวจผลข้อความเดิมอีกครั้ง';}finally{if(owned(key,ticket)&&b.isConnected)b.disabled=false}};
  }
  async function compressImage(file){if(!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size>5*1024*1024)throw Error('รูปต้องเป็น JPG, PNG หรือ WebP ไม่เกิน 5 MB');const url=URL.createObjectURL(file);try{const img=await new Promise((resolve,reject)=>{const i=new Image();i.onload=()=>resolve(i);i.onerror=reject;i.src=url});const scale=Math.min(1,1000/Math.max(img.width,img.height)),canvas=document.createElement('canvas');canvas.width=Math.round(img.width*scale);canvas.height=Math.round(img.height*scale);canvas.getContext('2d').drawImage(img,0,0,canvas.width,canvas.height);return canvas.toDataURL('image/jpeg',.7)}finally{URL.revokeObjectURL(url)}}
  async function uploadEvidence(file){if(!file)return null;if(!['image/jpeg','image/png','image/webp'].includes(file.type)||file.size>5*1024*1024)throw Error('หลักฐานต้องเป็นรูปไม่เกิน 5 MB');const key=actor(),u=S.get(),t=await token();if(actor()!==key)throw Error('บัญชีเปลี่ยนแล้ว');const path=u.authUserId+'/'+uuid()+'.'+({'image/jpeg':'jpg','image/png':'png','image/webp':'webp'}[file.type]);const response=await fetch(CFG.URL+'/storage/v1/object/qg-evidence/'+path,{method:'POST',headers:{apikey:CFG.KEY,Authorization:'Bearer '+t,'Content-Type':file.type,'x-upsert':'false'},body:file,signal:AbortSignal.timeout(20000)});if(!response.ok)throw Error('อัปโหลดหลักฐานไม่สำเร็จ');return path;}
  async function openEvidence(path){const key=actor(),t=await token();if(actor()!==key)return;try{const r=await fetch(CFG.URL+'/storage/v1/object/sign/qg-evidence/'+path,{method:'POST',headers:hdr(t),body:JSON.stringify({expiresIn:300}),signal:AbortSignal.timeout(15000)}),d=await r.json();if(!r.ok)throw Error('เปิดหลักฐานไม่สำเร็จ');if(actor()===key){if(!String(d.signedURL||'').startsWith('/object/sign/qg-evidence/'))throw Error('ลิงก์หลักฐานไม่ถูกต้อง');const url=new URL(CFG.URL+'/storage/v1'+d.signedURL);window.open(url.href,'_blank','noopener')}}catch(e){if(actor()===key)toast(e.message)}}
  addEventListener('pageshow',syncNotifications);addEventListener('online',syncNotifications);document.addEventListener('visibilitychange',()=>{if(!document.hidden)notificationsTick()});
  function toggleSound(button){const key='qg_customer_sound_'+S.get().userId,enabled=localStorage.getItem(key)!=='on';localStorage.setItem(key,enabled?'on':'off');button.querySelector('span').textContent=enabled?'เปิด':'ปิด';if(enabled&&'speechSynthesis' in window){const speech=new SpeechSynthesisUtterance('เปิดเสียงแจ้งเตือนแล้ว');speech.lang='th-TH';speechSynthesis.speak(speech)}}
  function filterMenu(category){document.querySelectorAll('[data-menu-category]').forEach(row=>{row.hidden=!!category&&row.dataset.menuCategory!==category})}
  return {needsOrderRefresh,toggleSound,deleteAccount,filterMenu,leave,poll,actor,owned,safeImage,note,linkPhone,date,context,cancel,favorite,favoriteButton,loadFavorites,reviews,reviewList,renderFavorites,renderPromotions,renderNotifications,renderProfile,renderOrder,renderChat,syncNotifications,openNotification,markRead,uploadEvidence,openEvidence};
})();
V.order=QGCustomer.renderOrder;
V['order-chat']=QGCustomer.renderChat;
V.favorites=QGCustomer.renderFavorites;
V.notifications=QGCustomer.renderNotifications;
V.promotion=QGCustomer.renderPromotions;
V.profile=QGCustomer.renderProfile;
