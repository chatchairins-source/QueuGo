'use strict';
const fs=require('fs'),assert=require('assert');
const sql=fs.readFileSync('ops/grocery/grocery_cash_checkout_lock_order.sql','utf8');
let checks=0;
const ok=(value,message)=>{assert.ok(value,message);checks++;};

ok(sql.trimEnd().endsWith(';'),'candidate SQL must be executable as a standalone migration');

ok(
  /for v_item in select value from jsonb_array_elements\(p_items\) order by \(value->>'product_id'\)::uuid loop/i.test(sql),
  'cash checkout must lock cart products in deterministic UUID order'
);
const productRead=/select \* into v_product\s+from public\.products\s+where id=v_product_id and shop_id=p_shop_id and available=true and delivery_available=true\s+for update;/i;
ok(productRead.test(sql),'cash checkout product validation must take FOR UPDATE before order_items triggers run');
ok(
  !/select \* into v_product\s+from public\.products\s+where id=v_product_id and shop_id=p_shop_id and available=true and delivery_available=true\s+for share;/i.test(sql),
  'cash checkout must not retain the Grocery lock-upgrade hazard'
);
ok(/SECURITY DEFINER/i.test(sql) && /SET search_path TO 'public', 'pg_temp'/i.test(sql),'existing function security contract must be preserved');
console.log(JSON.stringify({checks,failures:0,scope:'Grocery cash checkout deterministic product-lock contract'}));
