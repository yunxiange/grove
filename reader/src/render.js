import MarkdownIt from 'markdown-it';
import cjkFriendly from 'markdown-it-cjk-friendly';
import texmath from 'markdown-it-texmath';
import taskLists from 'markdown-it-task-lists';
import katex from 'katex';
import hljs from 'highlight.js/lib/common';

export function createRenderer() {
  const md = new MarkdownIt({html:true,linkify:true,breaks:false,highlight(code,language) {
    if(language && hljs.getLanguage(language)) {
      try {return hljs.highlight(code,{language,ignoreIllegals:true}).value;} catch {}
    }
    return '';
  }}).use(cjkFriendly).use(texmath,{engine:{renderToString(tex,options){
    return '<span class="grove-math" data-display="'+(options.displayMode?'block':'inline')+'">'+md.utils.escapeHtml(tex)+'</span>';
  }},delimiters:'dollars',katexOptions:{throwOnError:false,trust:false,maxExpand:1000}})
    .use(taskLists,{enabled:false,label:false});
  const fence=md.renderer.rules.fence;
  md.renderer.rules.fence=(tokens,index,options,env,self)=>{
    if(tokens[index].info.trim().toLowerCase()==='mermaid') {
      return '<pre class="mermaid">'+md.utils.escapeHtml(tokens[index].content)+'</pre>';
    }
    return fence(tokens,index,options,env,self);
  };
  return md;
}

export function normalizeLinks(root, baseUrl) {
  for(const element of root.querySelectorAll('a[href],img[src],source[srcset]')) {
    if(element.tagName==='SOURCE') {element.remove();continue;}
    const attr=element.tagName==='A'?'href':'src';
    const value=element.getAttribute(attr);
    if(!value || value.startsWith('#'))continue;
    try {
      const url=new URL(value,baseUrl);
      const protocols=attr==='src'?['https:','data:']:['https:','http:','mailto:'];
      if(protocols.includes(url.protocol))element.setAttribute(attr,url.href);
      else element.removeAttribute(attr);
    }catch {element.removeAttribute(attr);}
    if(element.tagName==='IMG')element.setAttribute('loading','lazy');
  }
  const ids=new Map();
  return [...root.querySelectorAll('h1,h2,h3,h4')].map(h=>{
    const base=h.textContent.trim().toLowerCase().replace(/[^\p{L}\p{N}\s_-]/gu,'').replace(/\s+/g,'-')||'section';
    const count=ids.get(base)||0;ids.set(base,count+1);
    h.id=base+(count?'-'+count:'');
    return {id:h.id,text:h.textContent,level:Number(h.tagName[1])};
  });
}

export function sanitizeMarkdown(html, purifier) {
  const withTasks=html.replace(/<input class="task-list-item-checkbox"([^>]*)>/g,(_,attrs)=>
    '<span class="task-checkbox">'+(/\bchecked\b/.test(attrs)?'☑':'☐')+'</span>');
  return purifier.sanitize(withTasks,{
    FORBID_TAGS:['script','iframe','object','embed','form','input','button','style'],
    FORBID_ATTR:['style','srcset'],ALLOW_DATA_ATTR:false,ADD_ATTR:['data-display'],
  });
}

// An image/link collection uses cells as independent items, unlike a data table.
// Require a single linked image per cell and no headers or spanning cells before
// allowing items to flow onto another row. Repository paths play no role here.
const tableCleanups=new WeakMap();
export function layoutTables(root) {
  tableCleanups.get(root)?.();
  const dataTables=[];
  for(const table of root.querySelectorAll('table')) {
    const cells=[...table.rows].flatMap(row=>[...row.cells]);
    const gallery=!table.querySelector('th,thead,tfoot,caption,colgroup,table')&&cells.length>1&&cells.every(cell=>{
      if(cell.colSpan!==1||cell.rowSpan!==1||cell.querySelectorAll('img').length!==1)return false;
      const image=cell.querySelector('img'),links=[...cell.querySelectorAll('a[href]')];
      return image.closest('a[href]')&&links.length>0&&new Set(links.map(a=>a.getAttribute('href'))).size===1;
    });
    table.classList.toggle('grove-media-grid',Boolean(gallery));
    for(const cell of cells) {
      const existing=cell.children.length===1&&cell.firstElementChild.classList.contains('grove-cell-content')?cell.firstElementChild:null;
      if(gallery) {
        if(existing)existing.replaceWith(...existing.childNodes);
      }else if(!existing) {
        // Let CSS intrinsic sizing use the actual font and content, rather than
        // estimating character widths or recognizing particular column names.
        const content=root.ownerDocument.createElement('div');
        content.className='grove-cell-content';content.append(...cell.childNodes);cell.append(content);
      }
    }
    if(!gallery)dataTables.push(table);
  }
  const view=root.ownerDocument.defaultView;
  let active=true,frame=0,width=root.clientWidth;
  const fit=()=>{
    // Work from nested tables outwards, with fonts and content measured by the
    // browser. Short values retain their full width; longer text may wrap down
    // to a six-em reading width when that lets the whole table fit.
    for(const table of [...dataTables].reverse()) {
      const scroll=table.scrollLeft;
      table.classList.remove('grove-fit-table');
      if(!table.clientWidth||table.scrollWidth<=table.clientWidth)continue;
      for(const cell of [...table.rows].flatMap(row=>[...row.cells])) {
        const content=cell.querySelector(':scope > .grove-cell-content');
        if(!content)continue;
        const minimum=Math.min(content.getBoundingClientRect().width,parseFloat(view.getComputedStyle(content).fontSize)*6);
        content.style.setProperty('--grove-cell-min',minimum+'px');
      }
      table.classList.add('grove-fit-table');
      // A wide data table should stay readable instead of being squeezed into
      // the screen. Only keep the wrapping layout if it actually fits fully.
      if(table.scrollWidth>table.clientWidth)table.classList.remove('grove-fit-table');
      table.scrollLeft=scroll;
    }
  };
  const schedule=()=>{
    if(!active||frame)return;
    frame=view.requestAnimationFrame(()=>{frame=0;if(active)fit();});
  };
  fit();
  const observer=dataTables.length&&view.ResizeObserver?new view.ResizeObserver(()=>{
    if(root.clientWidth!==width){width=root.clientWidth;schedule();}
  }):null;
  if(dataTables.length) {
    observer?.observe(root);
    root.addEventListener('load',schedule,true);
    root.ownerDocument.fonts?.ready.then(schedule);
  }
  const dispose=()=>{
    active=false;observer?.disconnect();root.removeEventListener('load',schedule,true);
    if(frame)view.cancelAnimationFrame(frame);
    if(tableCleanups.get(root)===dispose)tableCleanups.delete(root);
  };
  tableCleanups.set(root,dispose);
  return dispose;
}

export function renderEpub(root,source,base,purifier) {
  const url=new URL(base),prefix=url.pathname.match(/^\/epub\/[^/]+\//)?.[0];
  if(!prefix)throw Error('Invalid EPUB resource root');
  const bookRoot=url.origin+prefix;
  // Rewrite resources in inert template content before attaching any image to the
  // live document, so an EPUB cannot trigger remote tracking requests on open.
  const template=root.ownerDocument.createElement('template');
  template.innerHTML=sanitizeMarkdown(source,purifier);
  const content=template.content;
  content.querySelectorAll('base,link,meta,audio,video,source,track').forEach(node=>node.remove());
  const localResource=value=>{
    if(!value)return null;
    try{const resource=new URL(value,base);return resource.href.startsWith(bookRoot)?resource.href:null;}catch{return null;}
  };
  const dataImage=value=>/^data:image\/(png|jpeg|gif|webp|bmp|avif);base64,/i.test(value);
  for(const img of content.querySelectorAll('img')){
    const src=img.getAttribute('src')||'';
    const local=localResource(src);
    if(local)img.setAttribute('src',local);
    else if(!dataImage(src))img.removeAttribute('src');
  }
  for(const node of content.querySelectorAll('image,use')){
    const href=node.getAttribute('href')||node.getAttribute('xlink:href')||'';
    if(!href.startsWith('#')){
      const local=localResource(href);
      if(local||dataImage(href)){node.removeAttributeNS('http://www.w3.org/1999/xlink','href');node.setAttribute('href',local||href);}
      else node.remove();
    }
  }
  for(const a of content.querySelectorAll('a[href]')){
    try{
      const href=new URL(a.getAttribute('href'),base);
      if(href.href.startsWith(bookRoot))a.setAttribute('href',href.pathname===url.pathname&&href.hash?href.hash:href.href);
      else if(href.protocol==='https:'||href.protocol==='http:')a.setAttribute('href',href.href);
      else a.removeAttribute('href');
    }catch{a.removeAttribute('href');}
  }
  root.replaceChildren(content);
}

// Typeset only after sanitizing author HTML, so required KaTeX positioning styles
// survive without admitting untrusted inline styles from repository content.
export function renderMath(root) {
  for(const element of root.querySelectorAll('.grove-math')) {
    const tex=element.textContent;
    if(tex.length>20000)continue;
    element.innerHTML=katex.renderToString(tex,{displayMode:element.dataset.display==='block',
      throwOnError:false,trust:false,maxExpand:1000});
  }
}
