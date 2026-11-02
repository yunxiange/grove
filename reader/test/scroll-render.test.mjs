import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';
import createDOMPurify from 'dompurify';
import {createRenderer,normalizeLinks,sanitizeMarkdown,renderMath,renderEpub,layoutTables} from '../src/render.js';
import {createEndObserver} from '../src/scroll.js';
import {installChapterSwipe} from '../src/swipe.js';

test('table layout is content driven across document paths and preserves Markdown links and EPUB content',async()=>{
  const dom=new JSDOM('<article></article>',{runScripts:'outside-only',pretendToBeVisual:true});
  const {window}=dom,document=window.document;
  try {
    const style=document.createElement('style');
    style.textContent=await readFile(new URL('../src/reader.css',import.meta.url),'utf8');document.head.append(style);
    window.scrollTo=()=>{};
    Object.assign(window,{createRenderer,normalizeLinks,sanitizeMarkdown,renderMath,layoutTables,renderEpub,createEndObserver,installChapterSwipe,DOMPurify:createDOMPurify(window)});
    window.Grove={headings(){},progress(){}};
    window.eval((await readFile(new URL('../src/main.js',import.meta.url),'utf8')).replace(/^import .*;$/gm,''));
    const content='| 书名 | 作者 |\n| --- | --- |\n| [示例书](./book.md) | 示例作者 |';
    for(const path of ['README.md','books/README.md','docs/README.md','books/README.md','books/other.md']) {
      await window.renderDocument({id:'reader:'+path,path,content,kind:'markdown',url:'https://raw.githubusercontent.com/owner/repo/main/'+path,fontSize:17,warm:false,progress:0});
      await new Promise(resolve=>window.requestAnimationFrame(resolve));
      const table=document.querySelector('table');
      assert.equal(window.getComputedStyle(table).display,'block',path);
      assert.equal(window.getComputedStyle(table).maxWidth,'100%',path);
      assert.equal(document.querySelectorAll('.table-scroll').length,0,path);
      assert.equal(table.rows.length,2);
      assert.equal(table.querySelector('a').textContent,'示例书');
      assert.equal(table.querySelector('a').href,'https://raw.githubusercontent.com/owner/repo/main/'+(path.includes('/')?path.slice(0,path.lastIndexOf('/')+1):'')+'book.md');
    }
    await window.renderDocument({id:'chapter',path:'books/README.md',content:'<table><tr><td>电子书表格</td></tr></table>',kind:'epub',url:'https://appassets.androidplatform.net/epub/test/chapter.xhtml',fontSize:17,warm:false,progress:0});
    assert.equal(window.getComputedStyle(document.querySelector('table')).display,'block');
    assert.equal(document.querySelectorAll('.table-scroll').length,0);
    assert.equal(document.body.classList.contains('booklist-document'),false);
  }finally{window.close();}
});

test('appending comments preserves absolute reading position and loading updates keep existing DOM',async()=>{
  const dom=new JSDOM('<article></article>',{runScripts:'outside-only',pretendToBeVisual:true});
  const {window}=dom;const article=window.document.querySelector('article');let y=0;
  Object.defineProperty(window,'scrollY',{get:()=>y});
  Object.defineProperty(window,'innerHeight',{value:700});
  Object.defineProperty(window.document.documentElement,'scrollHeight',{get:()=>700+article.textContent.length*10});
  window.scrollTo=(first,next)=>{y=Math.max(0,Math.min(typeof first==='object'?first.top:next,window.document.documentElement.scrollHeight-700));};
  Object.assign(window,{createRenderer,normalizeLinks,sanitizeMarkdown,renderMath,layoutTables,createEndObserver,installChapterSwipe,DOMPurify:createDOMPurify(window)});
  const calls=[];window.Grove={headings(){},reachedEnd:(...args)=>calls.push(args)};
  const script=(await readFile(new URL('../src/main.js',import.meta.url),'utf8')).replace(/^import .*;$/gm,'');
  window.eval(script);
  const frame=()=>new Promise(resolve=>window.requestAnimationFrame(resolve));
  const payload={id:'issue',content:'# Discussion\n\n'+('Comment body.\n\n'.repeat(100)),fontSize:17,warm:false,progress:0,kind:'markdown',endPage:1};
  await window.renderDocument(payload);await frame();
  window.scrollTo(0,3000);
  const original=article.firstChild;
  await window.renderDocument({...payload,endPage:null});await frame();
  assert.equal(article.firstChild,original);assert.equal(window.scrollY,3000);assert.equal(calls.length,0);
  await window.renderDocument({...payload,content:payload.content+'More comments.\n\n'.repeat(100),endPage:2});await frame();
  assert.equal(window.scrollY,3000);assert.equal(calls.length,0);
  window.scrollTo(0,window.document.documentElement.scrollHeight);window.dispatchEvent(new window.Event('scroll'));
  assert.deepEqual(calls,[['issue',2]]);
  dom.window.close();
});
