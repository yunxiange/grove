import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import createDOMPurify from 'dompurify';
import {readFile} from 'node:fs/promises';
import {installBackToTop} from '../src/back-to-top.js';
import {createRenderer,normalizeLinks,sanitizeMarkdown,renderMath,renderEpub,layoutTables} from '../src/render.js';
import {createEndObserver} from '../src/scroll.js';
import {installChapterSwipe} from '../src/swipe.js';

function fixture() {
  const dom=new JSDOM('<article></article>',{pretendToBeVisual:true});
  const {window}=dom;let current={id:'readme',kind:'markdown'},y=0;
  const requests=[];
  Object.defineProperty(window,'scrollY',{get:()=>y});
  Object.defineProperty(window,'innerHeight',{value:600});
  window.scrollTo=options=>{requests.push(options);y=options.top;window.dispatchEvent(new window.Event('scroll'));};
  const control=installBackToTop(window.document,{getDocument:()=>current});
  return {window,control,requests,button:window.document.querySelector('button'),
    scrollTo:value=>{y=value;window.dispatchEvent(new window.Event('scroll'));},
    setDocument:value=>{current=value;control.update();},close:()=>{control.dispose();window.close();}};
}
test('back to top appears only after scrolling and clicking returns to the same document start',()=>{
  const f=fixture();try {
    assert.equal(f.button.hidden,true);
    assert.equal(f.window.document.querySelector('article button'),null);
    f.scrollTo(100);assert.equal(f.button.hidden,true);
    f.scrollTo(500);assert.equal(f.button.hidden,false);
    assert.equal(f.button.getAttribute('aria-label'),'回到顶部');
    f.button.click();assert.equal(f.button.hidden,true);
    assert.deepEqual(f.requests,[{left:0,top:0,behavior:'smooth'}]);
  }finally{f.close();}
});
test('loading and every attachment preview hide the action even after scrolling',()=>{
  const f=fixture();try {
    f.scrollTo(500);assert.equal(f.button.hidden,false);
    for(const current of [null,{kind:'epub'},{kind:'pdf'},{kind:'image'},{kind:'spreadsheet'}]){
      f.setDocument(current);assert.equal(f.button.hidden,true);
      f.button.click();assert.equal(f.requests.length,0);
    }
  }finally{f.close();}
});
test('back to top honors reduced motion and disposing removes its handlers',()=>{
  const f=fixture();try {
    f.window.matchMedia=()=>({matches:true});f.scrollTo(500);f.button.click();
    assert.equal(f.requests[0].behavior,'instant');
    f.scrollTo(500);f.control.dispose();f.button.click();
    assert.equal(f.requests.length,1);assert.equal(f.window.document.querySelector('button'),null);
  }finally{f.close();}
});
test('the actual reader shows the action after position restoration and reports zero progress on return',async()=>{
  const dom=new JSDOM('<article></article>',{runScripts:'outside-only',pretendToBeVisual:true});
  const {window}=dom;let y=0;const progress=[];
  Object.defineProperty(window,'scrollY',{get:()=>y});
  Object.defineProperty(window,'innerHeight',{value:600});
  Object.defineProperty(window.document.documentElement,'scrollHeight',{value:6000});
  window.scrollTo=options=>{y=options.top;window.dispatchEvent(new window.Event('scroll'));};
  Object.assign(window,{createRenderer,normalizeLinks,sanitizeMarkdown,renderMath,renderEpub,layoutTables,createEndObserver,installChapterSwipe,installBackToTop,DOMPurify:createDOMPurify(window)});
  window.Grove={headings(){},progress:(...args)=>progress.push(args)};
  window.eval((await readFile(new URL('../src/main.js',import.meta.url),'utf8')).replace(/^import .*;$/gm,''));
  const frame=()=>new Promise(resolve=>window.requestAnimationFrame(resolve));
  try {
    await window.renderDocument({id:'readme',kind:'markdown',content:'# 长文档\n\n正文',url:'https://example.com/README.md',fontSize:17,warm:false,progress:.5});
    await frame();await frame();
    const button=window.document.querySelector('.reader-back-to-top');
    assert.equal(button.hidden,false);assert.equal(y,2700);
    button.click();assert.equal(y,0);assert.equal(button.hidden,true);
    await new Promise(resolve=>window.setTimeout(resolve,220));
    assert.deepEqual(progress.at(-1),['readme',0]);
    await window.renderDocument({id:'other',kind:'markdown',content:'# 下一篇',url:'https://example.com/other.md',fontSize:17,warm:false,progress:0});
    await frame();assert.equal(button.hidden,true);
  }finally{window.close();}
});
