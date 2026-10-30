import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';
import createDOMPurify from 'dompurify';
import {createRenderer,normalizeLinks,sanitizeMarkdown,renderMath} from '../src/render.js';
import {createEndObserver} from '../src/scroll.js';

test('renderer signals readiness after registration and reports image failures to native UI',async()=>{
 const dom=new JSDOM('<article></article>',{runScripts:'outside-only',pretendToBeVisual:true});
 const {window}=dom;const failures=[];let ready=false;
 Object.assign(window,{createRenderer,normalizeLinks,sanitizeMarkdown,renderMath,createEndObserver,DOMPurify:createDOMPurify(window)});
 window.Grove={readerReady(){assert.equal(typeof window.renderDocument,'function');ready=true;},headings(){},renderFailed(id){failures.push(id);}};
 const source=(await readFile(new URL('../src/main.js',import.meta.url),'utf8')).replace(/^import .*;$/gm,'');
 try {
  window.eval(source);assert.equal(ready,true);
  await window.renderDocument({id:'svg',content:'',kind:'image',fontSize:17,warm:false,progress:0,attachmentUrl:'https://appassets.androidplatform.net/attachment/drawing'});
  window.document.querySelector('img').dispatchEvent(new window.Event('error'));
  assert.deepEqual(failures,['svg']);assert.match(window.document.querySelector('.image-hint').textContent,/加载失败/);
 }finally{window.close();}
});

test('standalone decoded image resets the old reading offset and requests a visible frame',async()=>{
 const dom=new JSDOM('<article></article>',{runScripts:'outside-only',pretendToBeVisual:true});
 const {window}=dom;let offset=1200;const presented=[];
 Object.defineProperty(window,'scrollY',{get:()=>offset});
 window.scrollTo=(first,next)=>{offset=typeof first==='object'?first.top:next;};
 Object.assign(window,{createRenderer,normalizeLinks,sanitizeMarkdown,renderMath,createEndObserver,DOMPurify:createDOMPurify(window)});
 window.Grove={readerReady(){},headings(){},previewStatus(){},previewPresented(id){presented.push(id);}};
 const source=(await readFile(new URL('../src/main.js',import.meta.url),'utf8')).replace(/^import .*;$/gm,'');
 try {
  window.eval(source);
  await window.renderDocument({id:'svg',content:'',kind:'image',fontSize:17,warm:false,progress:.9,attachmentUrl:'https://appassets.androidplatform.net/attachment/drawing'});
  const img=window.document.querySelector('img');
  Object.defineProperty(img,'naturalWidth',{value:191});Object.defineProperty(img,'naturalHeight',{value:150});
  img.dispatchEvent(new window.Event('load'));
  await new Promise(resolve=>window.requestAnimationFrame(()=>window.requestAnimationFrame(resolve)));
  assert.equal(img.width,191,'viewBox-only SVG needs an explicit decoded width in Android WebView');
  assert.equal(img.height,150,'decoded dimensions must preserve the SVG aspect ratio');
  assert.equal(img.style.height,'150px','CSS auto height must not collapse viewBox-only SVGs in Android WebView');
  assert.equal(window.document.documentElement.style.getPropertyValue('--preview-height'),window.innerHeight+'px');
  Object.defineProperty(window,'innerHeight',{value:500,configurable:true});
  window.dispatchEvent(new window.Event('resize'));
  assert.equal(window.document.documentElement.style.getPropertyValue('--preview-height'),'500px','height limits must follow the measured viewport after resizing');
  assert.equal(offset,0,'attachment preview must not retain a previous reading scroll offset');
  assert.deepEqual(presented,['svg'],'decoded image needs the same visible-frame completion path as Markdown');
 }finally{window.close();}
});

test('SVG preview fills available width with proportional height and follows resize and zoom',async()=>{
 const dom=new JSDOM('<article></article>',{runScripts:'outside-only',pretendToBeVisual:true});
 const {window}=dom;
 Object.defineProperty(window,'innerWidth',{value:423,configurable:true});
 window.scrollTo=()=>{};
 Object.assign(window,{createRenderer,normalizeLinks,sanitizeMarkdown,renderMath,createEndObserver,DOMPurify:createDOMPurify(window)});
 const source=(await readFile(new URL('../src/main.js',import.meta.url),'utf8')).replace(/^import .*;$/gm,'');
 try {
  window.eval(source);
  await window.renderDocument({id:'svg',content:'',kind:'image',attachmentMimeType:'image/svg+xml',fontSize:17,warm:false,progress:0,attachmentUrl:'https://appassets.androidplatform.net/attachment/drawing'});
  const img=window.document.querySelector('img');
  Object.defineProperty(img,'naturalWidth',{value:191});Object.defineProperty(img,'naturalHeight',{value:150});
  Object.defineProperty(img,'complete',{value:true});
  img.dispatchEvent(new window.Event('load'));
  assert.equal(parseFloat(img.style.width),423,'SVG should fill the viewport instead of using its small decoded fallback width');
  assert.equal(parseFloat(img.style.height),423*150/191);
  assert.equal(img.style.maxHeight,'none','tall SVG diagrams must scroll instead of shrinking below available width');
  Object.defineProperty(window,'innerWidth',{value:615,configurable:true});
  window.dispatchEvent(new window.Event('resize'));
  assert.equal(parseFloat(img.style.width),615);
  img.dispatchEvent(new window.Event('dblclick'));
  assert.equal(parseFloat(img.style.width),1230);
  img.dispatchEvent(new window.Event('dblclick'));
  assert.equal(parseFloat(img.style.width),615);
 }finally{window.close();}
});
