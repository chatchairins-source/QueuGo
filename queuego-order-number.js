(function(root){
  'use strict';
  function rawValue(input){
    if(input&&typeof input==='object'){
      return String(input.order_number??input.orderNumber??input.orderNo??input.order_id??input.orderId??input.id??'').trim();
    }
    return String(input??'').trim();
  }
  function fourDigits(input){
    const raw=rawValue(input).toUpperCase();
    if(!raw)return '----';
    let match=raw.match(/^(?:QO|QT)-\d{8}-(\d{1,4})$/);
    if(!match)match=raw.match(/^(?:QO|QT)-(\d{1,4})$/);
    if(!match)match=raw.match(/^(\d{1,4})$/);
    if(!match)match=raw.match(/(\d{4})$/);
    if(match)return match[1].padStart(4,'0').slice(-4);
    let hash=0;
    for(let i=0;i<raw.length;i++)hash=(hash*31+raw.charCodeAt(i))%10000;
    return String(hash).padStart(4,'0');
  }
  function format(input){return 'QO-'+fourDigits(input)}
  root.QueueGoOrderNumber=Object.freeze({format,fourDigits});
})(window);
