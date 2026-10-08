/* QueueGo Shopping — fixed-stock local retail categories using the existing shop/product/cart/order flow. */
(function(){
  const SHOPPING_CATEGORY='shopping';
  const FILTERS={
    mobile_accessories:{label:'มือถือและอุปกรณ์',icon:'mobile',banner:'mobile_accessories',subtitle:'มือถือ เคส ฟิล์ม สายชาร์จ และอุปกรณ์จากร้านใกล้คุณ'},
    computer_it:{label:'คอมพิวเตอร์และไอที',icon:'computer',banner:'computer_it',subtitle:'คอมพิวเตอร์ อุปกรณ์ไอที และเน็ตเวิร์กจากร้านใกล้คุณ'},
    automotive:{label:'อะไหล่ยานยนต์',icon:'gear',banner:'automotive',subtitle:'อะไหล่รถยนต์และมอเตอร์ไซค์จากร้านในพื้นที่'},
    automotive_car:{label:'อะไหล่รถยนต์',icon:'car',banner:'automotive',subtitle:'อะไหล่และของใช้รถยนต์จากร้านใกล้คุณ'},
    automotive_motorcycle:{label:'อะไหล่มอเตอร์ไซค์',icon:'motorcycle',banner:'automotive',subtitle:'อะไหล่และของใช้มอเตอร์ไซค์จากร้านใกล้คุณ'}
  };
  let bannerCache=null;

  /* Extend the existing icon/category dictionaries; no second renderer is introduced. */
  Object.assign(P,{
    mobile:'<rect x="7" y="2.5" width="10" height="19" rx="2"/><path d="M10 5h4M11 18.5h2"/>',
    computer:'<rect x="3" y="4" width="18" height="12" rx="2"/><path d="M8 21h8M12 16v5"/>',
    car:'<path d="M5 16h14l-1.5-6h-11L5 16Z"/><path d="M7 10l2-4h6l2 4M4 16v3h3v-3m10 0v3h3v-3"/><circle cx="8" cy="15" r="1"/><circle cx="16" cy="15" r="1"/>',
    motorcycle:'<circle cx="6" cy="17" r="3"/><circle cx="18" cy="17" r="3"/><path d="M9 17h4l3-6h-4l-2 3H7M14 8h3M12 11 10 8H8"/>',
    gear:'<circle cx="12" cy="12" r="3"/><path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1m8.6 8.6 2.1 2.1m0-12.8-2.1 2.1m-8.6 8.6-2.1 2.1"/>'
  });
  CATS.shopping='ช้อปปิ้ง';
  CICON.shopping='bag';

  async function serviceBanners(force=false){
    if(bannerCache&&!force)return bannerCache;
    try{
      const rows=await db('system_settings?select=value&key=eq.service_banners&limit=1');
      const value=Array.isArray(rows)&&rows[0]?.value;
      bannerCache=value&&typeof value==='object'?value:{};
    }catch(e){console.warn('service banners unavailable',e);bannerCache={}}
    return bannerCache;
  }
  function entry(config,key){
    const item=config?.[key]&&typeof config[key]==='object'?config[key]:{};
    return {image:String(item.image_url||item.image_data||''),title:String(item.title||''),subtitle:String(item.subtitle||''),active:item.active!==false};
  }
  function hero(config,key,fallbackTitle,fallbackSubtitle,icon){
    const item=entry(config,key),title=item.title||fallbackTitle,subtitle=item.subtitle||fallbackSubtitle;
    if(item.active&&item.image){
      return '<div class="qg-shopping-hero has-image"><img src="'+esc(item.image)+'" alt="'+esc(title)+'"><div class="qg-shopping-hero-copy"><b>'+esc(title)+'</b><span>'+esc(subtitle)+'</span></div></div>';
    }
    return '<div class="qg-shopping-hero">'+ico(icon,58)+'<b>'+esc(title)+'</b><span>'+esc(subtitle)+'</span></div>';
  }
  function hasSub(shop,key){
    const values=Array.isArray(shop?.public_subcategories)?shop.public_subcategories:[];
    if(key==='automotive')return values.includes('automotive_car')||values.includes('automotive_motorcycle');
    return values.includes(key);
  }
  function sortNearby(rows){
    if(!currentPos||!qgIsValidCoordinate(currentPos.lat,currentPos.lng,true))return rows;
    return rows.sort((a,b)=>{
      const da=calculateDistanceKm(currentPos.lat,currentPos.lng,a.latitude,a.longitude);
      const dbb=calculateDistanceKm(currentPos.lat,currentPos.lng,b.latitude,b.longitude);
      return (Number.isFinite(da)?da:Infinity)-(Number.isFinite(dbb)?dbb:Infinity);
    });
  }
  function categoryTiles(){
    return '<section class="sec"><div class="st"><h2>เลือกหมวดช้อปปิ้ง</h2></div><div class="qg-shopping-grid">'+
      '<button class="qg-shopping-tile" onclick="go(\'shopping/mobile_accessories\')"><i>'+ico('mobile',25)+'</i><b>มือถือและอุปกรณ์</b></button>'+
      '<button class="qg-shopping-tile" onclick="go(\'shopping/computer_it\')"><i>'+ico('computer',25)+'</i><b>คอมพิวเตอร์และไอที</b></button>'+
      '<button class="qg-shopping-tile" onclick="go(\'shopping/automotive\')"><i>'+ico('gear',25)+'</i><b>อะไหล่ยานยนต์</b></button>'+
    '</div></section>';
  }
  function autoFilters(key){
    if(!['automotive','automotive_car','automotive_motorcycle'].includes(key))return '';
    return '<section class="sec"><div class="qg-shopping-subfilters">'+
      '<button class="'+(key==='automotive'?'on':'')+'" onclick="go(\'shopping/automotive\')">ทั้งหมด</button>'+
      '<button class="'+(key==='automotive_car'?'on':'')+'" onclick="go(\'shopping/automotive_car\')">รถยนต์</button>'+
      '<button class="'+(key==='automotive_motorcycle'?'on':'')+'" onclick="go(\'shopping/automotive_motorcycle\')">มอเตอร์ไซค์</button>'+
    '</div></section>';
  }

  V.shopping=async function(filter,ticket=routeVersion){
    const [all,config]=await Promise.all([loadShops(),serviceBanners()]);
    const key=FILTERS[filter]?filter:'';
    let rows=all.filter(shop=>String(shop.public_category||'').toLowerCase()===SHOPPING_CATEGORY);
    if(key)rows=rows.filter(shop=>hasSub(shop,key));
    sortNearby(rows);
    if(ticket!==routeVersion)return;
    const current=FILTERS[key],title=current?.label||'ช้อปปิ้ง',subtitle=current?.subtitle||'สินค้าจากร้านใกล้คุณ ส่งได้ทันที',bannerKey=current?.banner||'shopping',icon=current?.icon||'bag';
    layout('<div class="pt"><button class="back" onclick="history.back()">'+ico('back')+'</button><h1>'+esc(title)+'</h1></div>'+
      hero(config,bannerKey,title,subtitle,icon)+
      (!key?categoryTiles():'')+autoFilters(key)+
      '<section class="sec"><div class="st"><div><h2>'+(key?'ร้านในหมวดนี้':'ร้านช้อปปิ้งใกล้คุณ')+'</h2><p class="sm" style="margin:4px 0 0">สินค้าเป็นชิ้น มีราคาและสต๊อก ใช้ระบบจัดส่ง QueueGo เดิม</p></div><span class="sm">'+rows.length+' ร้าน</span></div>'+
      '<div class="list">'+(rows.length?rows.map(shopCard).join(''):'<p class="empty">ยังไม่มีร้านในหมวดนี้</p>')+'</div></section>','home');
  };

  /* Keep the original Home renderer and only append one Shopping category entry. */
  const baseHome=V.home;
  V.home=async function(...args){
    await baseHome.apply(this,args);
    const ticket=args[1];
    if(ticket!==undefined&&ticket!==routeVersion)return;
    const cats=document.querySelector('.qg-home-original-main .cats')||document.querySelector('.cats');
    if(cats&&!cats.querySelector('[data-qg-shopping-home]')){
      const button=document.createElement('button');
      button.className='cat';button.type='button';button.dataset.qgShoppingHome='1';
      button.innerHTML='<i>'+ico('bag',25)+'</i><b>ช้อปปิ้ง</b>';
      button.onclick=()=>go('shopping');
      cats.appendChild(button);
    }
  };

  /* Replace the visual asset of existing banner pages only when Admin supplied one.
     The original image remains the fallback and the page/order flow is untouched. */
  async function applyManagedBanner(selector,key,ticket){
    const config=await serviceBanners();
    if(ticket!==undefined&&ticket!==routeVersion)return;
    const item=entry(config,key),host=document.querySelector(selector);
    if(!host||!item.active||!item.image)return;
    const img=host.querySelector('img');if(img){img.src=item.image;img.alt=item.title||img.alt||'QueueGo'}
    host.querySelector('.qg-managed-banner-copy')?.remove();
    if(item.title||item.subtitle){
      const copy=document.createElement('div');copy.className='qg-managed-banner-copy';
      copy.innerHTML=(item.title?'<b>'+esc(item.title)+'</b>':'')+(item.subtitle?'<span>'+esc(item.subtitle)+'</span>':'');
      host.appendChild(copy);
    }
  }
  [['food','.foodBanner'],['cafe','.cafeBanner'],['grocery','.groceryBanner'],['market','.marketBanner']].forEach(([key,selector])=>{
    const base=V[key];if(typeof base!=='function')return;
    V[key]=async function(...args){await base.apply(this,args);await applyManagedBanner(selector,key,args[1])};
  });
})();
