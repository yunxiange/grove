import * as XLSX from 'xlsx';

export function readWorkbook(bytes) {
  return XLSX.read(bytes,{type:'array',dense:true,sheetRows:5000,cellHTML:false,cellFormula:false,
    bookVBA:false,cellStyles:false,raw:true});
}
export function sheetWindow(book,name,limit=200) {
  const sheet=book.Sheets[name];if(!sheet||!sheet['!ref'])return {rows:[],columns:[],startRow:0,totalRows:0,truncated:false};
  const range=XLSX.utils.decode_range(sheet['!ref']);
  const endRow=Math.min(range.e.r,range.s.r+4999);const endCol=Math.min(range.e.c,range.s.c+49);
  const rows=[];
  for(let r=range.s.r;r<=Math.min(endRow,range.s.r+limit-1);r++) {
    const row=[];
    for(let c=range.s.c;c<=endCol;c++) {
      const cell=sheet['!data']?.[r]?.[c]||sheet[XLSX.utils.encode_cell({r,c})];
      row.push(cell?XLSX.utils.format_cell(cell):'');
    }
    rows.push(row);
  }
  return {rows,columns:Array.from({length:endCol-range.s.c+1},(_,i)=>XLSX.utils.encode_col(range.s.c+i)),
    startRow:range.s.r,totalRows:endRow-range.s.r+1,truncated:range.e.c>endCol||!!sheet['!fullref']};
}

export function showWorkbook(root,book) {
  let selected=book.SheetNames[0];let limit=200;
  function draw() {
    root.replaceChildren();
    const tabs=document.createElement('nav');tabs.className='workbook-tabs';tabs.setAttribute('aria-label','工作表');
    for(const name of book.SheetNames) {
      const button=document.createElement('button');button.textContent=name;button.className=name===selected?'active':'';
      button.onclick=()=>{selected=name;limit=200;draw()};tabs.append(button);
    }
    let view=sheetWindow(book,selected,limit);
    const note=document.createElement('p');note.className='workbook-note';
    function updateNote(){note.textContent=`${selected||'空工作簿'} · 显示 ${view.rows.length}/${view.totalRows} 行${view.truncated?' · 已限制预览范围':''}。只读，不执行公式或宏。`;}
    updateNote();
    const wrap=document.createElement('div');wrap.className='workbook-grid';
    const table=document.createElement('table');const head=document.createElement('thead');const tr=document.createElement('tr');
    const corner=document.createElement('th');corner.textContent='#';tr.append(corner);
    view.columns.forEach(name=>{const th=document.createElement('th');th.textContent=name;tr.append(th)});head.append(tr);table.append(head);
    const body=document.createElement('tbody');
    function appendRows(start){view.rows.slice(start).forEach((row,index)=>{
      const tr=document.createElement('tr');const number=document.createElement('th');number.textContent=view.startRow+start+index+1;tr.append(number);
      row.forEach(value=>{const cell=document.createElement('td');cell.textContent=value;tr.append(cell)});body.append(tr);
    });}
    appendRows(0);table.append(body);wrap.append(table);root.append(tabs,note,wrap);
    if(view.rows.length<view.totalRows){
      const more=document.createElement('p');more.className='workbook-note';more.textContent='上拉继续加载';wrap.append(more);
      wrap.addEventListener('scroll',()=>{
        if(wrap.clientHeight<=0||wrap.scrollTop+wrap.clientHeight<wrap.scrollHeight-160||view.rows.length>=view.totalRows)return;
        const start=view.rows.length;limit+=200;view=sheetWindow(book,selected,limit);appendRows(start);updateNote();
        if(view.rows.length>=view.totalRows)more.remove();
      },{passive:true});
    }
  }
  draw();
}
