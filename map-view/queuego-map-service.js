/* Shared QueueGo map API. Same origin by default; no public routing fallback. */
(function(root){
  "use strict";
  const configuredBase=String(root.QG_MAP_SERVICE_BASE_URL||root.document?.querySelector?.('meta[name="queuego-map-service-base"]')?.content||'').trim().replace(/\/+$/,'');
  const endpoint=(configuredBase||'')+"/map-api/";
  const staticPagesHost=!configuredBase&&/\.github\.io$/i.test(String(root.location?.hostname||''));
  async function call(action,body={},options={}){
    if(staticPagesHost)throw Error("QueueGo Map runtime is not configured for GitHub Pages");
    if(!/^[a-z-]+$/.test(action))throw Error("Invalid map action");
    const abort=new AbortController();
    const upstream=options.signal||null;
    const relay=()=>abort.abort();
    if(upstream?.aborted)abort.abort();
    else upstream?.addEventListener?.("abort",relay,{once:true});
    const timer=setTimeout(()=>abort.abort(),options.timeout||25000);
    try{
      const response=await fetch(endpoint+action,{
        method:"POST",
        headers:{"Content-Type":"application/json"},
        body:JSON.stringify(body),
        signal:abort.signal
      });
      const value=await response.json();
      if(!response.ok)throw Error(value.message||value.error||"QueueGo Map unavailable");
      return value;
    }finally{
      clearTimeout(timer);
      upstream?.removeEventListener?.("abort",relay);
    }
  }
  root.QueueGoMapService=Object.freeze({call,endpoint});
})(globalThis);
