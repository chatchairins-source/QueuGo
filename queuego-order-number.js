var QueueGoOrderNumber=(function(){
  'use strict';
  function rawValue(input){
    if(input&&typeof input==='object') return String(input.order_number??input.orderNumber??input.orderNo??'').trim();
    return String(input??'').trim();
  }
  function fourChars(input){
    const raw=rawValue(input).toUpperCase();
    if(!raw)return '----';
    const legacyPosQr=raw.match(/^(?:POS|QR)-([0-9A-F]{4,})$/);
    if(legacyPosQr)return String(parseInt(legacyPosQr[1].slice(-4),16)%10000).padStart(4,'0');
    let match=raw.match(/^(?:QT|QO)-\d{8}-([A-Z0-9]{1,4})$/);
    if(!match)match=raw.match(/^(?:QT|QO)-([A-Z0-9]{1,4})$/);
    if(!match)match=raw.match(/^([0-9]{1,4})$/);
    if(!match)match=raw.match(/^LW-\d{8}-\d{6}-([A-Z0-9]{4})$/);
    if(!match)return '----';
    const code=match[1].toUpperCase();
    if(/^LW-/.test(raw)&&/^[0-9A-F]{4}$/.test(code))return String(parseInt(code,16)%10000).padStart(4,'0');
    if(/^\d+$/.test(code))return code.padStart(4,'0');
    return String(parseInt(code,36)%10000).padStart(4,'0');
  }
  function format(input){return 'QT-'+fourChars(input)}
  function replaceInText(value){
    return String(value??'').replace(
      /\b(?:QT|QO)-\d{8}-[A-Z0-9]{1,4}\b|\b(?:QT|QO)-[A-Z0-9]{1,4}\b|\bLW-\d{8}-\d{6}-[A-Z0-9]{4}\b|\b(?:POS|QR)-[0-9A-F]{4,}\b/gi,
      token=>format(token)
    );
  }
  return Object.freeze({format,fourChars,replaceInText});
})();
if(typeof window!=='undefined')window.QueueGoOrderNumber=QueueGoOrderNumber;
if(typeof module==='object'&&module.exports)module.exports=QueueGoOrderNumber;
