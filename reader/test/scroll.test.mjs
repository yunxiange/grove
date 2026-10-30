import test from 'node:test';
import assert from 'node:assert/strict';
import {createEndObserver} from '../src/scroll.js';

test('comment pagination only requests once near bottom and re-arms for a new page',()=>{
  let position={y:0,height:700,total:3000};const calls=[];
  const observer=createEndObserver(()=>position,(...args)=>calls.push(args));
  observer.setPage('issue',1);observer.check();assert.equal(calls.length,0);
  position.y=2200;observer.check();observer.check();assert.deepEqual(calls,[['issue',1]]);
  observer.setPage('issue',null);observer.check();
  observer.setPage('issue',1);observer.check();assert.equal(calls.length,1);
  observer.setPage('issue',2);position.total=6000;observer.check();assert.equal(calls.length,1);
  position.y=5200;observer.check();assert.deepEqual(calls,[['issue',1],['issue',2]]);
});
test('short pages auto-fill the viewport; disabled and new documents are isolated',()=>{
  const calls=[];const observer=createEndObserver(()=>({y:0,height:700,total:400}),(...args)=>calls.push(args));
  observer.setPage('a',null);observer.check();assert.equal(calls.length,0);
  observer.setPage('a',1);observer.check();observer.setPage('a',2);observer.check();
  observer.setPage('b',1);observer.check();assert.deepEqual(calls,[['a',1],['a',2],['b',1]]);
});
