(function(root,factory){
  'use strict';
  const api=factory();
  if(typeof module==='object'&&module.exports)module.exports=api;
  if(root)root.QueueGoPasswordPolicy=api;
})(typeof globalThis!=='undefined'?globalThis:this,function(){
  'use strict';
  const MIN_LENGTH=12;
  const MESSAGE='รหัสผ่านต้องมีอย่างน้อย 12 ตัว และมี A-Z, a-z, ตัวเลข และสัญลักษณ์';
  function evaluate(value){
    const password=String(value||'');
    const missing=[];
    if(password.length<MIN_LENGTH)missing.push('length');
    if(!/[a-z]/.test(password))missing.push('lowercase');
    if(!/[A-Z]/.test(password))missing.push('uppercase');
    if(!/[0-9]/.test(password))missing.push('number');
    if(!/[^A-Za-z0-9]/.test(password))missing.push('symbol');
    return {ok:missing.length===0,missing,message:missing.length?MESSAGE:''};
  }
  function validate(value){return evaluate(value).ok}
  function requireStrong(value){
    const result=evaluate(value);
    if(!result.ok){const error=new Error(MESSAGE);error.code='WEAK_PASSWORD';error.missing=result.missing;throw error}
    return String(value);
  }
  return Object.freeze({MIN_LENGTH,MESSAGE,evaluate,validate,requireStrong});
});
