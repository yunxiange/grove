import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import createDOMPurify from 'dompurify';
import {createRenderer,normalizeLinks,sanitizeMarkdown,renderMath} from '../src/render.js';
const md=createRenderer();
function render(source){const {window}=new JSDOM('<article></article>');const root=window.document.querySelector('article');root.innerHTML=sanitizeMarkdown(md.render(source),createDOMPurify(window));renderMath(root);return root;}

test('untrusted README HTML cannot execute scripts or create forms',()=>{
 const root=render('<script>Grove.openLink("bad")</script><img src="x" onerror="alert(1)"><a href="javascript:alert(1)">x</a><iframe src="https://example.com"></iframe><form><input autofocus></form>');
 assert.equal(root.querySelector('script,iframe,form,input'),null);
 assert.equal(root.querySelector('img').hasAttribute('onerror'),false);
 assert.equal(root.querySelector('a').hasAttribute('href'),false);
});
test('relative docs and images resolve against the current document directory',()=>{
 const root=render('[guide](../guide.md) ![image](../../assets/diagram.png)');
 normalizeLinks(root,'https://raw.githubusercontent.com/owner/repo/main/docs/nested/readme.md');
 assert.equal(root.querySelector('a').getAttribute('href'),'https://raw.githubusercontent.com/owner/repo/main/docs/guide.md');
 assert.equal(root.querySelector('img').getAttribute('src'),'https://raw.githubusercontent.com/owner/repo/main/assets/diagram.png');
});
test('duplicate headings produce distinct stable anchors, including Chinese headings',()=>{
 const root=render('# 快速开始\n\n## 快速开始\n\n## 快速开始');
 const headings=normalizeLinks(root,'https://example.com/README.md');
 assert.deepEqual(headings.map(h=>h.id),['快速开始','快速开始-1','快速开始-2']);
});
test('GFM tables, tasks, math, and mermaid remain readable after sanitizing',()=>{
 const root=render('| 格式 | 支持 |\n|---|---|\n|表格|是|\n\n- [x] 完成\n- [ ] 待办\n\n$$E=mc^2$$\n\n```mermaid\nflowchart LR\n A-->B\n```');
 assert.ok(root.querySelector('table'));
 assert.match(root.textContent,/☑.*完成/s);assert.match(root.textContent,/☐.*待办/s);
 assert.ok(root.querySelector('.katex'));
 assert.ok(root.querySelector('.katex [style]'),'KaTeX positioning styles must survive to keep exponents aligned');
 assert.equal(root.querySelector('.mermaid').textContent.trim(),'flowchart LR\n A-->B');
});
