const fs=require('fs'),vm=require('vm'),assert=require('assert/strict');
const source=fs.readFileSync('admin/index.html','utf8'),a=source.indexOf('function adminApproveRefund(orderId){'),b=source.indexOf('\nasync function adminDeleteUser',a);let admin=true,writes=0,messages=[],checks=0;
const ctx={qtAdminOnly:()=>admin?{id:'admin'}:null,toast:m=>messages.push(m),dbSet:()=>writes++,qtFinanceEntry:()=>writes++,qtAudit:()=>writes++,pushNotification:()=>writes++};vm.createContext(ctx);vm.runInContext(source.slice(a,b),ctx);
ctx.adminApproveRefund('order');assert.equal(writes,0);assert.equal(messages.length,1);assert(messages[0].includes('ยังดำเนินการคืนเงิน'));checks+=3;
ctx.adminApproveRefund('order');assert.equal(writes,0);checks++;admin=false;messages=[];ctx.adminApproveRefund('order');assert.equal(writes,0);assert.equal(messages.length,0);checks+=2;
assert(!source.includes('onclick="adminApproveRefund('));assert(!source.slice(a,b).includes("refundStatus='approved'"));checks+=2;
console.log(JSON.stringify({checks,failures:0,scope:'legacy refund action fails closed without authoritative backend; no cache/ledger/audit/push writes or success acknowledgement, repeat and non-admin denial'}));
