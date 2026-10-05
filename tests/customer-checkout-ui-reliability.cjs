const fs=require('fs'),path=require('path'),assert=require('assert');
const root=path.resolve(__dirname,'..');
const customer=fs.readFileSync(path.join(root,'index.html'),'utf8');
let checks=0;const ok=(v,m)=>{assert.ok(v,m);checks++};

ok(customer.includes("customer checkout post-commit local cleanup failed"),'post-commit local cleanup must be non-fatal');
ok(customer.includes("customer checkout post-commit cart refresh failed"),'post-commit cart refresh must be non-fatal');
ok(customer.includes("customer checkout post-commit navigation failed"),'post-commit navigation must be non-fatal');
ok(customer.includes("customer checkout recovery local cleanup failed"),'recovered server-confirmed order cleanup must be non-fatal');
ok(customer.includes("customer checkout recovery navigation failed"),'recovered order navigation must be non-fatal');
ok(customer.includes("if(await reconcilePendingCheckout())return;"),'ambiguous checkout must reconcile before showing failure');
ok(customer.includes("if(!result){checkoutBusy=false;"),'checkout failure cleanup must run only before a confirmed server result');
ok(!customer.includes("toast('สั่งซื้อสำเร็จ · '+customerOrderNumber({order_number:result.order_number,id:result.id}));go('order/'+result.id);\n  }catch(e){"),'success UI work must not remain inside the mutation failure catch scope');
console.log(JSON.stringify({checks,failures:0,scope:'customer checkout post-commit UI error isolation and ambiguous-result recovery'}));
