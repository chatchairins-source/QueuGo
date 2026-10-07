(function(root){
  'use strict';
  function rawValue(input){
    if(input&&typeof input==='object'){
      return String(input.order_number??input.orderNumber??input.orderNo??'').trim();
    }
    return String(input??'').trim();
  }
  function format(input){
    const raw=rawValue(input).toUpperCase();
    let match=raw.match(/^QT-\d{8}-(\d{1,4})$/);
    if(!match)match=raw.match(/^QT-(\d{1,4})$/);
    if(!match)match=raw.match(/^(\d{1,4})$/);
    return match?'QT-'+match[1].padStart(4,'0'):'QT-----';
  }
  root.QueueGoOrderNumber=Object.freeze({format});
})(window);
