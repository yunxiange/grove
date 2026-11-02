import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import {installChapterSwipe} from '../src/swipe.js';
import {installBackToTop} from '../src/back-to-top.js';
import {readFile} from 'node:fs/promises';
import createDOMPurify from 'dompurify';
import {createRenderer,normalizeLinks,sanitizeMarkdown,renderMath,renderEpub,layoutTables} from '../src/render.js';
import {createEndObserver} from '../src/scroll.js';

function fixture() {
  const dom=new JSDOM('<article><p>Chapter text</p><div style="overflow-x:auto"><table><tr><td>Wide table</td></tr></table></div></article>');
  const {window}=dom,document=window.document;Object.defineProperty(window,'innerWidth',{value:400});
  let current={id:'chapter-1'},selected=false,scale=1;
  window.getSelection=()=>({isCollapsed:!selected});
  Object.defineProperty(window,'visualViewport',{value:{get scale(){return scale;}}});
  const calls=[];
  const stop=installChapterSwipe(document,{getDocument:()=>current,onSwipe:(...args)=>calls.push(args)});
  const point=(x,y,id=1)=>({identifier:id,clientX:x,clientY:y});
  const emit=(type,touches,changedTouches=touches,target=document.querySelector('p'))=>{
    const e=new window.Event(type,{bubbles:true,cancelable:true});
    Object.defineProperties(e,{touches:{value:touches},changedTouches:{value:changedTouches}});
    target.dispatchEvent(e);return e;
  };
  const swipe=(dx,dy=0,target)=>{
    const start=point(200,200),end=point(200+dx,200+dy);
    emit('touchstart',[start],[start],target);
    const move=emit('touchmove',[end],[end],target);
    emit('touchend',[],[end],target);return move;
  };
  return {window,document,calls,point,emit,swipe,setCurrent:v=>current=v,setSelected:v=>selected=v,setScale:v=>scale=v,
    close:()=>{stop();window.close();}};
}
test('EPUB single-finger swipes request exactly one next or previous chapter',()=>{
  const f=fixture();try {
    assert.equal(f.swipe(-160).defaultPrevented,true);f.swipe(160);
    assert.deepEqual(f.calls,[['chapter-1',1],['chapter-1',-1]]);
  }finally{f.close();}
});
test('vertical scrolls, short drags, selections and pinch zoom do not turn chapters',()=>{
  const f=fixture();try {
    assert.equal(f.swipe(-30).defaultPrevented,true);
    assert.equal(f.swipe(-100,200).defaultPrevented,false);
    f.setSelected(true);f.swipe(-160);f.setSelected(false);
    f.setScale(2);f.swipe(-160);f.setScale(1);
    f.emit('touchstart',[f.point(220,200)]);
    f.emit('touchmove',[f.point(100,200),f.point(150,200,2)]);
    f.emit('touchend',[],[f.point(60,200)]);
    assert.deepEqual(f.calls,[]);
  }finally{f.close();}
});
test('wide tables pan until the edge and only a new outward gesture turns a chapter',()=>{
  const f=fixture();try {
    const table=f.document.querySelector('div'),target=table.querySelector('td');
    Object.defineProperties(table,{clientWidth:{value:200},scrollWidth:{value:600}});
    table.scrollLeft=100;
    assert.equal(f.swipe(-160,0,target).defaultPrevented,false);assert.deepEqual(f.calls,[]);
    table.scrollLeft=400;f.swipe(-160,0,target);
    assert.deepEqual(f.calls,[['chapter-1',1]]);
    table.scrollLeft=0;f.swipe(160,0,target);
    assert.deepEqual(f.calls,[['chapter-1',1],['chapter-1',-1]]);
  }finally{f.close();}
});
test('disabled readers, stale gestures and cancellation never navigate',()=>{
  const f=fixture();try {
    f.setCurrent(null);f.swipe(-160);
    f.setCurrent({id:'chapter-1'});f.emit('touchstart',[f.point(250,200)]);
    f.emit('touchmove',[f.point(100,200)]);f.setCurrent({id:'chapter-2'});
    f.emit('touchend',[],[f.point(100,200)]);
    f.emit('touchstart',[f.point(250,200)]);f.emit('touchmove',[f.point(100,200)]);
    f.emit('touchcancel',[]);f.emit('touchend',[],[f.point(100,200)]);
    assert.deepEqual(f.calls,[]);
  }finally{f.close();}
});
test('the actual reader enables the gesture only for ready EPUB pages with native navigation',async()=>{
  const dom=new JSDOM('<article></article>',{runScripts:'outside-only',pretendToBeVisual:true});
  const {window}=dom,calls=[];window.scrollTo=()=>{};
  Object.assign(window,{createRenderer,normalizeLinks,sanitizeMarkdown,renderMath,layoutTables,renderEpub,createEndObserver,installChapterSwipe,installBackToTop,DOMPurify:createDOMPurify(window)});
  window.Grove={headings(){},swipePage:(...args)=>calls.push(args)};
  const script=(await readFile(new URL('../src/main.js',import.meta.url),'utf8')).replace(/^import .*;$/gm,'');
  const swipe=()=>{
    const target=window.document.querySelector('article');
    for(const [type,x,pressed] of [['touchstart',300,true],['touchmove',50,true],['touchend',50,false]]) {
      const e=new window.Event(type,{bubbles:true,cancelable:true});const point={identifier:1,clientX:x,clientY:200};
      Object.defineProperties(e,{touches:{value:pressed?[point]:[]},changedTouches:{value:[point]}});target.dispatchEvent(e);
    }
  };
  try {
    window.eval(script);
    const payload={id:'book/one',kind:'epub',content:'<html><body><h1>Chapter</h1></body></html>',url:'https://appassets.androidplatform.net/epub/book/one.xhtml',swipePages:true,fontSize:17,warm:false,progress:0};
    await window.renderDocument(payload);
    await new Promise(resolve=>window.requestAnimationFrame(()=>window.requestAnimationFrame(resolve)));
    swipe();assert.deepEqual(calls,[['book/one',1]]);
    await window.renderDocument({...payload,swipePages:false});swipe();assert.equal(calls.length,1);
    await window.renderDocument({...payload,id:'readme',kind:'markdown'});
    await new Promise(resolve=>window.requestAnimationFrame(()=>window.requestAnimationFrame(resolve)));
    swipe();assert.equal(calls.length,1);
  }finally{window.close();}
});
