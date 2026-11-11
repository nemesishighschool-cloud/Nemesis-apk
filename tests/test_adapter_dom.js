// Regression: a user's message is not a new assistant reply.
const fs=require('fs'), vm=require('vm'), assert=require('assert');
let items=[];
const document={querySelectorAll: selector => selector.includes('.ds-message') ? items : [], querySelector:()=>null};
const window={};
vm.runInNewContext(fs.readFileSync('nemapi/app/src/main/assets/js/adapter-deepseek.js','utf8'), {window,document,console});
const assistant=text=>({querySelector:s=>s==='.ds-assistant-message-main-content'?{innerText:text}:null});
const user=text=>({innerText:text,querySelector:()=>null});
function answer(){return JSON.parse(window.__napDS.answer());}
items=[user('question')]; assert.strictEqual(answer().ok,false);
items=[assistant('old')]; assert.strictEqual(answer().text,'old'); assert.strictEqual(answer().blocks,1);
items=[assistant('old'),user('new question')]; assert.strictEqual(answer().text,'old'); assert.strictEqual(answer().blocks,1);
items=[assistant('old'),user('new question'),assistant('new answer')]; assert.strictEqual(answer().text,'new answer'); assert.strictEqual(answer().blocks,2);
console.log('DOM assistant freshness: OK');
