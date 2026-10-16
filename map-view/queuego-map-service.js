/* Shared QueueGo map API. Same origin by default; no public routing fallback. */
(function(root){
  "use strict";
  const endpoint="/map-api/";
  async function call(action,body={}){
    if(!/^[a-z-]+$/.test(action))throw Error("Invalid map action");
    const abort=new AbortController(),timer=setTimeout(()=>abort.abort(),25000);
    try{
      const response=await fetch(endpoint+action,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body),signal:abort.signal});
      const value=await response.json();
      if(!response.ok)throw Error(value.message||value.error||"QueueGo Map unavailable");
      return value;
    }finally{clearTimeout(timer)}
  }
  root.QueueGoMapService=Object.freeze({call,endpoint});
})(globalThis);
