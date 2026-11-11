// Before-submit session identity must be checked before any DOM action.
const fs=require('fs'),vm=require('vm'),assert=require('assert');
let queries=0,password=false, field=null;
const document={title:'DeepSeek', querySelector:()=>password?{}:null,
  querySelectorAll: s => { queries++; return field && s.includes('textarea') ? [field] : []; }};
const window={getComputedStyle:()=>({display:'block',visibility:'visible',opacity:'1'})};
const location={href:'https://chat.deepseek.com/a/chat/s/active123'};
vm.runInNewContext(fs.readFileSync('nemapi/app/src/main/assets/js/adapter-deepseek.js','utf8'),{window,document,location,console});
const ds=window.__napDS;
let r=JSON.parse(ds.submit('https://chat.deepseek.com/a/chat/s/different123'));
assert.strictEqual(r.reason,'conversation_changed'); assert.strictEqual(r.not_submitted,true); assert.strictEqual(queries,0);
r=JSON.parse(ds.submit(location.href)); assert.strictEqual(r.reason,'input_missing'); assert.strictEqual(r.not_submitted,true);
field={tagName:'TEXTAREA',disabled:false,readOnly:false,isContentEditable:false,getBoundingClientRect:()=>({width:300,height:40})};
assert.strictEqual(JSON.parse(ds.ready()).ok,true);
field.readOnly=true; assert.strictEqual(JSON.parse(ds.ready()).ok,false);
field.readOnly=false; field.getBoundingClientRect=()=>({width:0,height:40}); assert.strictEqual(JSON.parse(ds.ready()).ok,false);
field=null; password=true; assert.strictEqual(JSON.parse(ds.ready()).login_required,true);
console.log('Provider readiness and atomic session guard: OK');
