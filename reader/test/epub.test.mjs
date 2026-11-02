import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import createDOMPurify from 'dompurify';
import {renderEpub} from '../src/render.js';
test('EPUB keeps chapter IDs and local images and links while removing active or remote content',()=>{
 const dom=new JSDOM('<article></article>');const root=dom.window.document.querySelector('article');
 renderEpub(root,'<html><head><style>body{display:none}</style></head><body><h1 id="detail">章节</h1><img src="../images/cover.svg"><img src="https://example.com/tracker"><script>alert(1)</script><iframe src="https://example.com"></iframe><form><input></form><a href="two.xhtml#part">下一章</a><a href="#detail">本章</a><a href="javascript:alert(1)">危险</a></body></html>',
  'https://appassets.androidplatform.net/epub/book/OPS/text/one.xhtml',createDOMPurify(dom.window));
 assert.equal(root.querySelector('h1').id,'detail');
 assert.equal(root.querySelector('img').getAttribute('src'),'https://appassets.androidplatform.net/epub/book/OPS/images/cover.svg');
 assert.equal(root.querySelectorAll('img')[1].hasAttribute('src'),false);
 assert.equal(root.querySelector('a').getAttribute('href'),'https://appassets.androidplatform.net/epub/book/OPS/text/two.xhtml#part');
 assert.equal(root.querySelectorAll('a')[1].getAttribute('href'),'#detail');
 assert.equal(root.querySelector('script,style,iframe,form,input'),null);
 assert.equal(root.querySelectorAll('a')[2].hasAttribute('href'),false);
 dom.window.close();
});
