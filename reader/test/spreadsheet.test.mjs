import test from 'node:test';
import assert from 'node:assert/strict';
import * as XLSX from 'xlsx';
import {readWorkbook,sheetWindow,showWorkbook} from '../src/spreadsheet.js';
import {JSDOM} from 'jsdom';

for(const bookType of ['xlsx','xls'])test(`reads ${bookType} multiple sheets and saved cell values`,()=>{
 const book=XLSX.utils.book_new();
 XLSX.utils.book_append_sheet(book,XLSX.utils.aoa_to_sheet([['项目','金额'],['示例',123.45]]),'预算');
 XLSX.utils.book_append_sheet(book,XLSX.utils.aoa_to_sheet([['<img src=x onerror=alert(1)>']]),'备注');
 const parsed=readWorkbook(XLSX.write(book,{type:'buffer',bookType}));
 assert.deepEqual(parsed.SheetNames,['预算','备注']);
 assert.deepEqual(sheetWindow(parsed,'预算').rows,[['项目','金额'],['示例','123.45']]);
 assert.equal(sheetWindow(parsed,'备注').rows[0][0],'<img src=x onerror=alert(1)>');
});
test('limits the rendered window before iterating sparse extreme columns',()=>{
 const book={SheetNames:['x'],Sheets:{x:{'!ref':'A1:XFD5000',A1:{t:'s',v:'ok'}}}};
 const view=sheetWindow(book,'x',10);
 assert.equal(view.rows.length,10);assert.equal(view.columns.length,50);assert.equal(view.truncated,true);
});
test('scrolling a workbook appends rows without replacing table or losing horizontal position',()=>{
 const dom=new JSDOM('<article></article>');globalThis.document=dom.window.document;
 try {
  const book=XLSX.utils.book_new();XLSX.utils.book_append_sheet(book,XLSX.utils.aoa_to_sheet(Array.from({length:401},(_,i)=>['row-'+i])),'Data');
  const root=document.querySelector('article');showWorkbook(root,book);
  const wrap=root.querySelector('.workbook-grid'),table=wrap.querySelector('table');
  Object.defineProperty(wrap,'clientHeight',{value:500});
  Object.defineProperty(wrap,'scrollHeight',{get:()=>table.tBodies[0].rows.length*30});
  wrap.scrollLeft=100;wrap.scrollTop=5500;wrap.dispatchEvent(new dom.window.Event('scroll'));
  assert.equal(table.tBodies[0].rows.length,400);assert.equal(wrap.querySelector('table'),table);assert.equal(wrap.scrollLeft,100);
  wrap.dispatchEvent(new dom.window.Event('scroll'));assert.equal(table.tBodies[0].rows.length,400);
  wrap.scrollTop=11500;wrap.dispatchEvent(new dom.window.Event('scroll'));
  assert.equal(table.tBodies[0].rows.length,401);assert.equal(wrap.querySelector('p'),null);
 } finally {delete globalThis.document;dom.window.close();}
});
