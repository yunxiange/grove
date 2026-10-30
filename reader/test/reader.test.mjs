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
test('double asterisks render Chinese emphasis next to quotes colons and brackets',()=>{
 for(const [source,text] of [
  ['**xxxx**','xxxx'],['这是**加粗**文字','加粗'],['这是**“重点”**内容','“重点”'],
  ['这是**重点：**内容','重点：'],['**重点：**后续','重点：'],['这是**【提示】**内容','【提示】'],
  ['启用**Starring → Read and write**权限','Starring → Read and write']
 ]){
  const root=render(source);
  assert.equal(root.querySelector('strong')?.textContent,text,source);
  assert.equal(root.textContent.includes('**'),false,source);
 }
});
test('literal double asterisks in code escapes and unclosed markup stay literal',()=>{
 const root=render('`**inline**`\n\n```text\n**block**\n```\n\n\\*\\*原样\\*\\*\n\n**未闭合');
 assert.equal(root.querySelector('p code').textContent,'**inline**');
 assert.equal(root.querySelector('pre code').textContent.trim(),'**block**');
 assert.match(root.textContent,/\*\*原样\*\*/);assert.match(root.textContent,/\*\*未闭合/);
 assert.equal(root.querySelector('strong'),null);
});
test('Chinese bold emphasis preserves nested links inline code and emphasis',()=>{
 const root=render('查看**[文档](./guide.md)：**了解更多。\n\n使用**`npm test`：**执行检查。\n\n这是***“重点”***内容。');
 assert.equal(root.querySelector('strong a')?.getAttribute('href'),'./guide.md');
 assert.equal(root.querySelector('strong code')?.textContent,'npm test');
 assert.ok(root.querySelector('em strong, strong em'));
 assert.equal(root.textContent.includes('**'),false);
});
