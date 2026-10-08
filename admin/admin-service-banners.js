/* Admin-managed category banners. Uses the same public banner bucket and system_settings source of truth. */
(function(){
  const KEY='service_banners',BUCKET='queuego-banners';
  const SERVICES=[
    ['food','อาหาร'],
    ['cafe','เครื่องดื่ม'],
    ['grocery','ร้านขายของชำ'],
    ['market','ตลาดสด'],
    ['shopping','ช้อปปิ้ง'],
    ['mobile_accessories','มือถือและอุปกรณ์'],
    ['computer_it','คอมพิวเตอร์และไอที'],
    ['automotive','อะไหล่ยานยนต์']
  ];
  const DEFAULTS={
    food:['อาหาร','ร้านอาหารใกล้คุณ'],
    cafe:['เครื่องดื่ม','ร้านเครื่องดื่มใกล้คุณ'],
    grocery:['ร้านขายของชำ','ซื้อของใกล้บ้าน เงินหมุนเวียนในชุมชน'],
    market:['ตลาดสด','ตลาดสดใกล้คุณ สดใหม่ทุกวัน'],
    shopping:['ช้อปปิ้ง','สินค้าจากร้านใกล้คุณ ส่งได้ทันที'],
    mobile_accessories:['มือถือและอุปกรณ์','มือถือ เคส ฟิล์ม สายชาร์จ และอุปกรณ์ใกล้คุณ'],
    computer_it:['คอมพิวเตอร์และไอที','อุปกรณ์ไอทีจากร้านใกล้คุณ'],
    automotive:['อะไหล่ยานยนต์','อะไหล่รถยนต์และมอเตอร์ไซค์ใกล้คุณ']
  };
  const escBanner=v=>String(v??'').replace(/[&<>"']/g,ch=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
  const encodePath=path=>String(path||'').split('/').map(encodeURIComponent).join('/');
  const safeLink=value=>{const link=String(value||'').trim();if(!link)return '';if(link.startsWith('#')||/^https?:\/\//i.test(link))return link;throw Error('ลิงก์ต้องขึ้นต้นด้วย # หรือ https://')};
  function normalize(value){
    const cfg=value&&typeof value==='object'?JSON.parse(JSON.stringify(value)):{version:'1'};
    cfg.version='1';
    for(const [key] of SERVICES){
      const d=DEFAULTS[key],item=cfg[key]&&typeof cfg[key]==='object'?cfg[key]:{};
      cfg[key]=Object.assign({title:d[0],subtitle:d[1],active:true},item);
    }
    return cfg;
  }
  async function readConfig(){
    const token=await qtGetAccessToken();if(!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
    const rows=await qtSupabaseTable('system_settings?select=value&key=eq.'+KEY+'&limit=1',{accessToken:token});
    return normalize(Array.isArray(rows)&&rows[0]?.value||{});
  }
  async function writeConfig(cfg){
    const token=await qtGetAccessToken();if(!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
    cfg=normalize(cfg);
    await qtSupabaseTable('system_settings?on_conflict=key',{method:'POST',accessToken:token,body:{key:KEY,value:cfg,updated_at:new Date().toISOString()}});
    window.QG_ADMIN_SERVICE_BANNER_CONFIG=cfg;
    return cfg;
  }
  async function removeObject(path){
    if(!path)return;
    try{
      const token=await qtGetAccessToken();if(!token)return;
      await fetch(QT_SUPABASE_CONFIG.URL+'/storage/v1/object/'+BUCKET+'/'+encodePath(path),{method:'DELETE',headers:{apikey:QT_SUPABASE_CONFIG.KEY,Authorization:'Bearer '+token}});
    }catch(e){console.warn('service banner cleanup failed',e)}
  }
  function ensureHost(){
    let root=document.getElementById('qg-service-banner-manager');
    if(root)return root;
    const home=document.getElementById('qg-home-banner-manager');
    const anchor=home?.closest('.qg-banner-admin-card')||document.querySelector('#qt-sec-settings .card:last-of-type');
    if(!anchor)return null;
    const card=document.createElement('div');card.className='card qg-banner-admin-card';
    card.innerHTML='<div class="qg-banner-admin-head"><div><h3>แบนเนอร์หน้าหมวด</h3><p>Admin เปลี่ยนรูป หัวข้อ และคำโปรยได้ทุกหน้าที่มีแบนเนอร์ รูปเริ่มต้นยังคงเป็น fallback</p></div></div><div id="qg-service-banner-manager" class="qg-service-banner-manager">กำลังโหลดแบนเนอร์…</div>';
    anchor.after(card);root=card.querySelector('#qg-service-banner-manager');return root;
  }
  function render(cfg){
    const root=ensureHost();if(!root)return;
    root.innerHTML='<div class="qg-service-banner-slots">'+SERVICES.map(([key,label])=>{
      const item=cfg[key]||{},src=String(item.image_url||item.image_data||''),active=item.active!==false;
      return '<article class="qg-service-banner-slot" data-key="'+key+'">'+
        '<div class="qg-banner-slot-title"><b>'+escBanner(label)+'</b><span>'+(src?'มีรูปกำหนดเอง':'ใช้ภาพเริ่มต้น')+'</span></div>'+
        '<div class="qg-banner-preview">'+(src?'<img src="'+escBanner(src)+'" alt="'+escBanner(label)+'">':'<div>ภาพเริ่มต้น / พื้นหลังมาตรฐาน</div>')+'</div>'+
        '<label class="qg-banner-file">เลือกรูปใหม่<input type="file" accept="image/jpeg,image/png,image/webp" onchange="qgAdminUploadServiceBanner(\''+key+'\',this)"></label>'+
        '<label>หัวข้อ<input id="qg-service-title-'+key+'" type="text" maxlength="80" value="'+escBanner(item.title||DEFAULTS[key][0])+'"></label>'+
        '<label>คำโปรย<input id="qg-service-subtitle-'+key+'" type="text" maxlength="180" value="'+escBanner(item.subtitle||DEFAULTS[key][1])+'"></label>'+
        '<label>ลิงก์เมื่อกด (ไม่บังคับ)<input id="qg-service-link-'+key+'" type="text" maxlength="500" value="'+escBanner(item.link||item.link_target||'')+'" placeholder="#shopping หรือ https://..."></label>'+
        '<label class="qg-banner-active"><span>เปิดใช้งานแบนเนอร์</span><input id="qg-service-active-'+key+'" type="checkbox" '+(active?'checked':'')+'></label>'+
        '<div class="qg-banner-actions"><button type="button" class="btn-primary" onclick="qgAdminSaveServiceBanner(\''+key+'\')">บันทึก</button><button type="button" class="btn-secondary" onclick="qgAdminClearServiceBanner(\''+key+'\')">กลับภาพเริ่มต้น</button></div>'+
      '</article>';
    }).join('')+'</div>';
  }
  window.qgAdminLoadServiceBanners=async function(){
    const root=ensureHost();if(!root)return;
    root.innerHTML='กำลังโหลดแบนเนอร์…';
    try{const cfg=await readConfig();window.QG_ADMIN_SERVICE_BANNER_CONFIG=cfg;render(cfg)}
    catch(e){root.innerHTML='<p class="qg-banner-error">โหลดแบนเนอร์ไม่สำเร็จ: '+escBanner(e.message||e)+'</p>'}
  };
  window.qgAdminSaveServiceBanner=async function(key){
    try{
      const cfg=normalize(window.QG_ADMIN_SERVICE_BANNER_CONFIG||await readConfig());
      if(!cfg[key])throw Error('ไม่พบหมวดแบนเนอร์');
      cfg[key].title=String(document.getElementById('qg-service-title-'+key)?.value||'').trim().slice(0,80)||DEFAULTS[key][0];
      cfg[key].subtitle=String(document.getElementById('qg-service-subtitle-'+key)?.value||'').trim().slice(0,180)||DEFAULTS[key][1];
      cfg[key].link=safeLink(document.getElementById('qg-service-link-'+key)?.value||'').slice(0,500);
      cfg[key].active=!!document.getElementById('qg-service-active-'+key)?.checked;
      await writeConfig(cfg);render(cfg);toast('บันทึกแบนเนอร์ '+DEFAULTS[key][0]+' แล้ว');
    }catch(e){toast('บันทึกไม่สำเร็จ: '+String(e.message||e).slice(0,90))}
  };
  window.qgAdminUploadServiceBanner=async function(key,input){
    const file=input?.files?.[0];if(!file)return;
    if(!['image/jpeg','image/png','image/webp'].includes(file.type)){toast('รองรับเฉพาะ JPG, PNG และ WebP');input.value='';return}
    if(file.size>5*1024*1024){toast('รูปต้องมีขนาดไม่เกิน 5 MB');input.value='';return}
    input.disabled=true;let path='';
    try{
      const cfg=normalize(window.QG_ADMIN_SERVICE_BANNER_CONFIG||await readConfig()),item=cfg[key],oldPath=String(item.storage_path||'');
      const ext=file.type==='image/png'?'png':file.type==='image/webp'?'webp':'jpg';
      path='service/'+key+'/'+Date.now()+'-'+crypto.randomUUID().slice(0,8)+'.'+ext;
      const token=await qtGetAccessToken();if(!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
      const response=await fetch(QT_SUPABASE_CONFIG.URL+'/storage/v1/object/'+BUCKET+'/'+encodePath(path),{method:'POST',headers:{apikey:QT_SUPABASE_CONFIG.KEY,Authorization:'Bearer '+token,'Content-Type':file.type,'x-upsert':'false'},body:file});
      const data=await response.json().catch(()=>({}));if(!response.ok)throw Error(data.message||'อัปโหลดรูปไม่สำเร็จ');
      item.image_url=QT_SUPABASE_CONFIG.URL+'/storage/v1/object/public/'+BUCKET+'/'+encodePath(path);
      item.storage_path=path;item.active=true;delete item.image_data;
      cfg[key]=item;await writeConfig(cfg);if(oldPath&&oldPath!==path)removeObject(oldPath);
      render(cfg);toast('อัปโหลดแบนเนอร์ '+DEFAULTS[key][0]+' แล้ว');
    }catch(e){if(path)removeObject(path);toast('อัปโหลดไม่สำเร็จ: '+String(e.message||e).slice(0,90))}
    finally{input.disabled=false;input.value=''}
  };
  window.qgAdminClearServiceBanner=async function(key){
    if(!confirm('กลับไปใช้ภาพเริ่มต้นของ '+DEFAULTS[key][0]+'?'))return;
    try{
      const cfg=normalize(window.QG_ADMIN_SERVICE_BANNER_CONFIG||await readConfig()),item=cfg[key],oldPath=String(item.storage_path||'');
      delete item.image_url;delete item.image_data;delete item.storage_path;item.active=true;cfg[key]=item;
      await writeConfig(cfg);if(oldPath)removeObject(oldPath);render(cfg);toast('กลับไปใช้ภาพเริ่มต้นแล้ว');
    }catch(e){toast('อัปเดตไม่สำเร็จ: '+String(e.message||e).slice(0,90))}
  };
  if(document.getElementById('qt-sec-settings')?.classList.contains('active'))setTimeout(()=>{window.qgAdminLoadHomeBanners?.();window.qgAdminLoadServiceBanners?.()},0);
})();
