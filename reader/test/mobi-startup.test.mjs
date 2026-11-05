import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';
import createDOMPurify from 'dompurify';
import {createRenderer,normalizeLinks,sanitizeMarkdown,renderMath,renderEpub,layoutTables} from '../src/render.js';
import {createEndObserver} from '../src/scroll.js';
import {installChapterSwipe} from '../src/swipe.js';
import {installBackToTop} from '../src/back-to-top.js';

test('MOBI restores the chapter scroll before native metadata feedback re-renders the same request',async()=>{
  const dom=new JSDOM('<article></article>',{runScripts:'outside-only',pretendToBeVisual:true});
  const {window}=dom;let y=0,metadata=0,closed=0;
  Object.defineProperty(window,'scrollY',{get:()=>y});
  Object.defineProperty(window,'innerHeight',{value:600});
  Object.defineProperty(window.document.documentElement,'scrollHeight',{value:6000});
  window.scrollTo=options=>{y=options.top;window.dispatchEvent(new window.Event('scroll'));};
  window.fetch=async()=>({ok:true,blob:async()=>new Blob(['book'])});
  window.testOpenMobi=async()=>({total:2,metadata:{title:'中文书籍',author:'作者'},contents:[],
    chapter:async()=>({html:'<h1>第一章</h1><p>正文</p>',title:'第一章',url:'https://appassets.androidplatform.net/epub/mobi/section-0.html'}),
    destroy:()=>{closed++;}});
  Object.assign(window,{createRenderer,normalizeLinks,sanitizeMarkdown,renderMath,renderEpub,layoutTables,createEndObserver,installChapterSwipe,installBackToTop,DOMPurify:createDOMPurify(window)});
  const payload={id:'book:mobi:0',kind:'mobi',content:'',url:'https://example.com/book.mobi',attachmentUrl:'https://appassets.androidplatform.net/attachment/book',
    progress:.25,bookSection:-1,bookProgress:0,fontSize:17,warm:false};
  window.Grove={headings(){},bookState:(id,json)=>{
    metadata++;const state=JSON.parse(json);
    assert.equal(id,payload.id);assert.equal(state.section,0);assert.equal(state.progress,.5);
    // Compose reports the displayed section/progress back into the payload.
    void window.renderDocument({...payload,bookProgress:state.progress});
  }};
  const source=(await readFile(new URL('../src/main.js',import.meta.url),'utf8')).replace(/^import .*;$/gm,'')
    .replace("import('./mobi-book.js')","Promise.resolve({openMobi:window.testOpenMobi})");
  window.eval(source);
  const frame=()=>new Promise(resolve=>window.requestAnimationFrame(resolve));
  try {
    await window.renderDocument(payload);await frame();await frame();await frame();
    assert.equal(y,2700);assert.equal(metadata,1);
    assert.equal(window.document.querySelector('.reader-back-to-top').hidden,true);
    assert.equal(window.document.querySelector('meta[data-grove-mobi-policy]').content,'img-src data: blob:');
    window.dispatchEvent(new window.Event('pagehide'));await Promise.resolve();
    assert.equal(closed,1);
  }finally{window.close();}
});

test('turning a MOBI chapter keeps the displayed page until the next chapter is ready',async()=>{
  const dom=new JSDOM('<article></article>',{runScripts:'outside-only',pretendToBeVisual:true});
  const {window}=dom;window.scrollTo=()=>{};
  let release;const waiting=new Promise(resolve=>{release=resolve;});
  window.fetch=async()=>({ok:true,blob:async()=>new Blob(['book'])});
  window.testOpenMobi=async()=>({total:2,metadata:{title:'Book',author:''},contents:[],
    chapter:async index=>{if(index===1)await waiting;return {html:`<h1>Chapter ${index+1}</h1>`,title:`Chapter ${index+1}`,url:'https://appassets.androidplatform.net/epub/mobi/chapter.html'};},destroy(){}});
  Object.assign(window,{createRenderer,normalizeLinks,sanitizeMarkdown,renderMath,renderEpub,layoutTables,createEndObserver,installChapterSwipe,installBackToTop,DOMPurify:createDOMPurify(window)});
  window.Grove={headings(){},bookState(){}};
  window.eval((await readFile(new URL('../src/main.js',import.meta.url),'utf8')).replace(/^import .*;$/gm,'')
    .replace("import('./mobi-book.js')","Promise.resolve({openMobi:window.testOpenMobi})"));
  const frame=()=>new Promise(resolve=>window.requestAnimationFrame(resolve));
  const payload={id:'book:mobi:0',kind:'mobi',content:'',url:'https://example.com/book.mobi',attachmentUrl:'https://appassets.androidplatform.net/attachment/book',
    progress:0,bookSection:0,bookProgress:0,fontSize:17,warm:false};
  try {
    await window.renderDocument(payload);await frame();
    const pending=window.renderDocument({...payload,id:'book:mobi:1',bookSection:1});
    await Promise.resolve();
    assert.match(window.document.querySelector('article').textContent,/Chapter 1/,'keep the current page while decoding');
    release();await pending;await frame();assert.match(window.document.querySelector('article').textContent,/Chapter 2/);
  }finally{release();window.close();}
});
