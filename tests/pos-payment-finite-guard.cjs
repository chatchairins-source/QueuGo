const assert=require('assert/strict');
const fs=require('fs'),path=require('path'),vm=require('vm');
const source=fs.readFileSync(path.resolve(__dirname,'../merchant/pos-table-v2.js'),'utf8');
const match=source.match(/^\s*window\.qgPosConfirmPay=async\(\)=>\{.+\};\s*$/m);
assert.ok(match,'the actual POS cash-confirmation handler must remain covered');

async function attempt(rawCash,totalAmount,method='cash'){
  const calls=[],messages=[];
  const bill={id:'test-order',total_amount:totalAmount,payment_status:'UNPAID'};
  const button={disabled:false};
  const context={
    window:{qgPosReceipt:()=>{}},
    selected:()=>bill,
    state:{loading:false,orders:[bill]},
    document:{
      getElementById:id=>id==='qg-pos-method'?{value:method}:id==='qg-pos-cash'?{value:rawCash}:null,
      querySelector:selector=>selector==='.qg-pos-pay-dialog button.confirm'?button:null
    },
    navigator:{onLine:true},
    busy:()=>{},
    message:text=>messages.push(String(text?.message||text)),
    rpc:async(name,body)=>{calls.push({name,body});return {id:'test-order'}},
    load:async()=>{}
  };
  vm.runInNewContext(match[0],context,{timeout:1000});
  await context.window.qgPosConfirmPay();
  return {calls,messages,button};
}

(async()=>{
  for(const value of ['NaN','1e309','-20','19.99','','  ']){
    const result=await attempt(value,20);
    assert.equal(result.calls.length,0,'invalid cash '+JSON.stringify(value)+' must not call pos_take_payment');
    assert.ok(result.messages.length,'invalid cash must display an error');
  }
  for(const amount of [null,'','Infinity','NaN',-1]){
    const result=await attempt('20',amount);
    assert.equal(result.calls.length,0,'invalid bill amount '+JSON.stringify(amount)+' must block payment');
    assert.ok(result.messages.length,'invalid bill amount must display an error');
  }
  const valid=await attempt('20.00',20);
  assert.equal(valid.calls.length,1,'exact tender is accepted');
  assert.equal(valid.calls[0].name,'pos_take_payment');
  assert.equal(valid.calls[0].body.p_cash_received,20);
  const change=await attempt('30',20);
  assert.equal(change.calls.length,1,'larger finite tender is accepted');
  assert.equal(change.calls[0].body.p_cash_received,30);
  const transfer=await attempt('',20,'bank_transfer');
  assert.equal(transfer.calls.length,1,'non-cash confirmation remains unchanged');
  assert.equal(transfer.calls[0].body.p_cash_received,null);
  console.log('POS finite cash guard regression passed');
})().catch(error=>{console.error(error);process.exitCode=1});
