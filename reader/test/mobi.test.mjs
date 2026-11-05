import test from 'node:test';
import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {JSDOM} from 'jsdom';
import createDOMPurify from 'dompurify';
import {openMobi,validateMobi} from '../src/mobi-book.js';
import {renderEpub} from '../src/render.js';
import {makeMobi,makeEncryptedCombo} from './mobi-fixture.mjs';

const dom=new JSDOM('<article></article>');
for(const name of ['document','DOMParser','XMLSerializer','CustomEvent','EventTarget'])globalThis[name]=dom.window[name];
Object.defineProperty(dom.window.HTMLElement.prototype,'innerText',{get(){return this.textContent;}});
globalThis.CSS={escape:value=>String(value).replace(/[^a-zA-Z0-9_-]/g,char=>'\\'+char)};
const fixture=async name=>new Blob([await readFile(new URL(`../fixtures/mobi/${name}`,import.meta.url))]);

for(const name of ['alice.mobi','alice.azw3'])test(`${name} reads real text, cover images, TOC and chapter links offline`,async()=>{
  const book=await openMobi(await fixture(name));
  try {
    assert.match(book.metadata.title,/Alice/);
    assert.match(book.metadata.author,/Carroll/);
    assert.ok(book.total>10);
    assert.ok(book.contents.length>10);
    const first=await book.chapter(0);
    const root=dom.window.document.querySelector('article');
    renderEpub(root,first.html,first.url,createDOMPurify(dom.window));
    assert.ok(root.querySelector('img[src^="data:image/"],image[href^="data:image/"]'));
    const chapterOne=book.contents.find(x=>/rabbit.hole/i.test(x.title));
    assert.ok(chapterOne,'TOC must contain the actual first chapter');
    const target=await book.resolve(chapterOne.href);
    assert.equal(target.index,chapterOne.index);
    const chapter=await book.chapter(target.index,chapterOne.href);
    renderEpub(root,chapter.html,chapter.url,createDOMPurify(dom.window));
    assert.match(root.textContent,/rabbit-hole|rabbit hole/i);
    assert.ok(root.querySelector('#grove-mobi-destination'));
    assert.equal(root.querySelector('script,style,iframe,form,link'),null);
    assert.equal(root.querySelector('img[src^="http"]'),null);
    assert.equal(await book.resolve('https://example.com/tracker'),null);
  }finally{book.destroy();}
});

test('encrypted, truncated and malformed record tables fail before decoding',async()=>{
  const original=new Uint8Array(await (await fixture('alice.mobi')).arrayBuffer());
  const v=new DataView(original.buffer),start=v.getUint32(78);
  const encrypted=original.slice();new DataView(encrypted.buffer).setUint16(start+12,2);
  await assert.rejects(openMobi(new Blob([encrypted])),/DRM/);
  const broken=original.slice();new DataView(broken.buffer).setUint32(86,10);
  await assert.rejects(validateMobi(new Blob([broken])),/结构无效/);
  await assert.rejects(validateMobi(new Blob([original.slice(0,90)])),/结构无效/);
  await assert.rejects(validateMobi(new Blob(['this is not an ebook'])),/结构无效/);
});

test('a combo book with an encrypted KF8 half is rejected instead of falling back to MOBI6',async()=>{
  await assert.rejects(openMobi(await makeEncryptedCombo()),/DRM/);
});

test('a prepared chapter is reused without repeating blob reads on back-and-forth turns',async()=>{
  const book=await openMobi(await fixture('alice.azw3'));
  const originalFetch=globalThis.fetch;let reads=0;
  globalThis.fetch=(...args)=>{reads++;return originalFetch(...args);};
  try {
    const index=book.contents.find(x=>/rabbit.hole/i.test(x.title)).index;
    await book.chapter(index);const before=reads;
    await book.chapter(index);
    assert.equal(reads,before,'warm turns must reuse prepared chapter text and images');
  }finally{globalThis.fetch=originalFetch;book.destroy();}
});

test('a cover already present after front matter is not added again from EXTH metadata',async()=>{
  const source='<html><head><title>Unknown</title></head><body><guide><reference type="cover" title="Cover" filepos="0000000000"></guide><p>版权信息</p><mbp:pagebreak/><div id="book-cover"><img alt="封面 > 图片" recindex="1"></div><mbp:pagebreak/><h1>第一章</h1><p>正文</p></body></html>';
  const book=await openMobi(makeMobi({source,coverMeta:true,anchorMarker:'<div id="book-cover">'}));
  try {
    assert.equal(book.total,3,'keep the original three pages; do not insert a duplicate cover');
    const cover=await book.chapter(1);
    assert.equal(cover.title,'封面');assert.match(cover.html,/data:image/);
    const first=await book.chapter(0);assert.equal(first.title,'');
    const next=await book.chapter(2);assert.equal(next.title,'第一章');
  }finally{book.destroy();}
});

test('a book without any cover page can still use its metadata cover exactly once',async()=>{
  const book=await openMobi(makeMobi({coverMeta:true,source:'<html><body><h1>第一章</h1><p>正文</p></body></html>'}));
  try {
    assert.equal(book.total,2);assert.equal((await book.chapter(0)).title,'封面');
    assert.equal((await book.chapter(1)).title,'第一章');
  }finally{book.destroy();}
});

test('placeholder document titles do not appear in the chapter header',async()=>{
  for(const placeholder of ['Unknown','Untitled','undefined','未知内容','未命名页面']) {
    const book=await openMobi(makeMobi({source:`<html><head><title>${placeholder}</title></head><body><p>没有章节标题的扉页</p></body></html>`}));
    try{assert.equal((await book.chapter(0)).title,'');}finally{book.destroy();}
  }
});

test('image fetching never follows original remote URLs or executes original markup',async()=>{
  const book=await openMobi(makeMobi());
  const originalFetch=globalThis.fetch,requests=[];
  globalThis.fetch=(url,...args)=>{requests.push(String(url));return originalFetch(url,...args);};
  try {
    const chapter=await book.chapter(0);
    renderEpub(dom.window.document.querySelector('article'),chapter.html,chapter.url,createDOMPurify(dom.window));
    const root=dom.window.document.querySelector('article');
    assert.ok(root.querySelector('img[src^="data:image/png;base64,"]'));
    assert.equal(root.querySelector('script,style,iframe,form'),null);
    assert.equal(root.querySelector('img[src^="https:"]'),null);
    assert.ok(requests.length>0);
    assert.ok(requests.every(url=>url.startsWith('blob:')));
  }finally{globalThis.fetch=originalFetch;book.destroy();}
});

for(const compression of [1,2])test(`MOBI6 compression ${compression} keeps Chinese, page breaks, inline images and filepos targets`,async()=>{
  const book=await openMobi(makeMobi({compression}));
  try {
    assert.equal(book.metadata.title,'中文书名');assert.equal(book.total,2);
    const first=await book.chapter(0);
    const root=dom.window.document.querySelector('article');
    renderEpub(root,first.html,first.url,createDOMPurify(dom.window));
    assert.match(root.textContent,/中文书名/);
    assert.equal(root.querySelector('h1').textContent,'中文书名');
    assert.equal(root.querySelector('font'),null);
    const href=new URL(root.querySelector('a').href).searchParams.get('href');
    assert.equal((await book.resolve(href)).index,1);
    const second=await book.chapter(1,href);
    renderEpub(root,second.html,second.url,createDOMPurify(dom.window));
    assert.match(root.textContent,/第二章/);assert.match(root.textContent,/中文正文/);
    assert.ok(root.querySelector('#grove-mobi-destination'));
  }finally{book.destroy();}
});
