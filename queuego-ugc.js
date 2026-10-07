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
  const report=(orderId,messageId,reason,details)=>rpc('qg_report_chat',{
    p_order_id:orderId,
    p_message_id:messageId||null,
    p_reason:reason||'other',
    p_details:details||null
  });
  const guidelinesUrl=()=>new URL('docs/community-guidelines.html',root).href;

  window.QueueGoUGC=Object.freeze({configure,state,accept,block,unblock,report,guidelinesUrl});
})();