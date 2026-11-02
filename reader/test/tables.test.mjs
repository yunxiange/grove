import test from 'node:test';
import assert from 'node:assert/strict';
import {JSDOM} from 'jsdom';
import createDOMPurify from 'dompurify';
import {sanitizeMarkdown,normalizeLinks,layoutTables} from '../src/render.js';

const item=n=>`<td width="25%" align="center" valign="top"><a href="notes/${n}.md"><img src="covers/${n}.jpg" width="152" height="190" alt="封面 ${n}"></a><br><a href="notes/${n}.md">书名 ${n}</a></td>`;
function fixture(html) {
  const dom=new JSDOM('<article></article>');
  const root=dom.window.document.querySelector('article');
  root.innerHTML=sanitizeMarkdown(html,createDOMPurify(dom.window));
  normalizeLinks(root,'https://raw.githubusercontent.com/example/library/main/README.md');
  layoutTables(root);
  return {dom,root};
}
test('a headerless linked-image collection can wrap without losing order links or image dimensions',()=>{
  const {dom,root}=fixture(`<table><tr>${item(1)}${item(2)}${item(3)}${item(4)}</tr><tr>${item(5)}</tr></table>`);
  try {
    const table=root.querySelector('table');
    assert.equal(table.classList.contains('grove-media-grid'),true);
    assert.deepEqual([...table.querySelectorAll('td')].map(c=>c.textContent),['书名 1','书名 2','书名 3','书名 4','书名 5']);
    assert.equal(table.querySelectorAll('a').length,10);
    assert.equal(table.querySelector('img').getAttribute('width'),'152');
    assert.equal(table.querySelector('img').getAttribute('height'),'190');
    assert.equal(table.querySelector('a').href,'https://raw.githubusercontent.com/example/library/main/notes/1.md');
    layoutTables(root);
    assert.equal(root.querySelectorAll('table').length,1);
    assert.equal(table.querySelectorAll('td').length,5);
  }finally{dom.window.close();}
});
test('headerless data tables headers spans and mixed link targets retain their row and column relationships',()=>{
  for(const html of [
    '<table><tr><td>项目</td><td>10</td></tr><tr><td>合计</td><td>20</td></tr></table>',
    `<table><thead><tr><th>书名</th><th>作者</th></tr></thead><tbody><tr>${item(1)}${item(2)}</tr></tbody></table>`,
    `<table><tr>${item(1).replace('width="25%"','colspan="2"')}${item(2)}</tr></table>`,
    `<table><tr>${item(1)}${item(2).replace('书名 2</a>','书名 2</a><a href="other.md">另一项</a>')}</tr></table>`,
    `<table><tr>${item(1)}<td>长文字说明</td></tr></table>`,
  ]) {
    const {dom,root}=fixture(html);
    try{assert.equal(root.querySelector('.grove-media-grid'),null,html);assert.ok(root.querySelector('tr>td'));}
    finally{dom.window.close();}
  }
});
test('all data cells use intrinsic content sizing without losing values or affecting gallery captions',()=>{
  const description='这是一段比较长的内容，需要留出足够的列宽才能清楚阅读，而不是每行只显示几个字。';
  const {dom,root}=fixture(`<table><tr><th>日期</th><th>说明</th></tr><tr><td>2026-11-02</td><td><a href="note.md">${description}</a></td></tr></table><table><tr>${item(1).replace('书名 1',description)}${item(2)}</tr></table>`);
  try {
    const tables=root.querySelectorAll('table');
    assert.equal(tables[0].querySelectorAll('.grove-cell-content').length,4);
    assert.equal(tables[0].querySelectorAll('td .grove-cell-content')[1].textContent,description);
    assert.equal(tables[0].querySelectorAll('tr').length,2);
    assert.equal(tables[0].querySelector('td .grove-cell-content').textContent,'2026-11-02');
    assert.equal(tables[1].querySelectorAll('.grove-cell-content').length,0);
    assert.equal(tables[1].classList.contains('grove-media-grid'),true);
    layoutTables(root);
    assert.equal(tables[0].querySelectorAll('.grove-cell-content').length,4);
    assert.equal(tables[0].querySelector('.grove-cell-content .grove-cell-content'),null);
  }finally{dom.window.close();}
});
