(() => {
  'use strict';
  const SUPABASE_URL='https://pkypiqhlrmzocysgeqew.supabase.co';
  const PUBLISHABLE_KEY='sb_publishable_Vn2Il4jp-iBtzNsGRNY8Lg_Tpvk2Vre';
  const scriptUrl=document.currentScript?.src||new URL('queuego-ugc.js',document.baseURI).href;
  const root=new URL('.',scriptUrl);
  let getAccessToken=null;

  function configure(options={}){
    if(typeof options.getAccessToken==='function')getAccessToken=options.getAccessToken;
  }
  async function rpc(name,body={}){
    if(typeof getAccessToken!=='function')throw Error('กรุณาเข้าสู่ระบบใหม่');
    const token=await getAccessToken();
    if(!token)throw Error('กรุณาเข้าสู่ระบบใหม่');
    const response=await fetch(SUPABASE_URL+'/rest/v1/rpc/'+encodeURIComponent(name),{
      method:'POST',
      headers:{
        apikey:PUBLISHABLE_KEY,
        Authorization:'Bearer '+token,
        'Content-Type':'application/json'
      },
      body:JSON.stringify(body),
      signal:AbortSignal.timeout(15000)
    });
    const data=await response.json().catch(()=>null);
    if(!response.ok){
      const text=String(data?.message||data?.error||'จัดการความปลอดภัยแชตไม่สำเร็จ');
      throw Error(text);
    }
    return data;
  }
  const state=orderId=>rpc('qg_chat_moderation_state',{p_order_id:orderId});
  const accept=()=>rpc('qg_accept_ugc_terms',{p_version:1});
  const block=orderId=>rpc('qg_block_chat_counterpart',{p_order_id:orderId});
  const unblock=orderId=>rpc('qg_unblock_chat_counterpart',{p_order_id:orderId});
  function readEvidence(file){
    if(!file)return Promise.resolve(null);
    if(!['image/jpeg','image/png','image/webp'].includes(file.type))return Promise.reject(Error('หลักฐานต้องเป็นรูป JPG, PNG หรือ WebP'));
    if(file.size>8*1024*1024)return Promise.reject(Error('รูปหลักฐานต้องไม่เกิน 8 MB'));
    return new Promise((resolve,reject)=>{
      const url=URL.createObjectURL(file),img=new Image();
      img.onload=()=>{
        try{
          const render=(maxSide,quality)=>{
            const ratio=Math.min(1,maxSide/Math.max(img.naturalWidth||img.width,img.naturalHeight||img.height));
            const width=Math.max(1,Math.round((img.naturalWidth||img.width)*ratio));
            const height=Math.max(1,Math.round((img.naturalHeight||img.height)*ratio));
            const canvas=document.createElement('canvas');canvas.width=width;canvas.height=height;
            const ctx=canvas.getContext('2d',{alpha:false});ctx.fillStyle='#fff';ctx.fillRect(0,0,width,height);ctx.drawImage(img,0,0,width,height);
            return canvas.toDataURL('image/jpeg',quality);
          };
          let data=render(1600,.78);
          if(data.length>880000)data=render(1250,.68);
          if(data.length>880000)data=render(1000,.58);
          URL.revokeObjectURL(url);
          if(data.length>900000)return reject(Error('ภาพแคปหน้าจอมีขนาดใหญ่เกินไป กรุณาครอปภาพแล้วลองอีกครั้ง'));
          resolve(data);
        }catch(e){URL.revokeObjectURL(url);reject(Error('เตรียมภาพหลักฐานไม่สำเร็จ'))}
      };
      img.onerror=()=>{URL.revokeObjectURL(url);reject(Error('อ่านภาพหลักฐานไม่สำเร็จ'))};
      img.src=url;
    });
  }
  const report=async(orderId,messageId,reason,details,evidenceFile)=>{
    if(evidenceFile){
      const evidence=await readEvidence(evidenceFile);
      return rpc('qg_report_chat_with_evidence',{
        p_order_id:orderId,
        p_reason:reason||'other',
        p_details:details||null,
        p_evidence_data:evidence
      });
    }
    return rpc('qg_report_chat',{
      p_order_id:orderId,
      p_message_id:messageId||null,
      p_reason:reason||'other',
      p_details:details||null
    });
  };
  const guidelinesUrl=()=>new URL('docs/community-guidelines.html',root).href;

  window.QueueGoUGC=Object.freeze({configure,state,accept,block,unblock,report,guidelinesUrl});
})();