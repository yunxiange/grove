import DOMPurify from 'dompurify';
import { createRenderer, normalizeLinks, sanitizeMarkdown, renderMath } from './render.js';
import {createEndObserver} from './scroll.js';
const md=createRenderer();
const article=document.querySelector('article');
let active=null;
let rendering=0;
let scrollTimer;
let contentReady=false;
const endObserver=createEndObserver(()=>({y:scrollY,height:innerHeight,total:document.documentElement.scrollHeight}),
  (id,page)=>window.Grove?.reachedEnd(id,page));
const checkEnd=()=>{if(contentReady)endObserver.check();};

window.renderDocument=async payload=>{
  const generation=++rendering;
  const isNew=active?.id!==payload.id;
  const previous=active;
  const oldY=scrollY;
  active=payload;
  endObserver.setPage(payload.id,payload.endPage);
  if(contentReady&&!isNew&&previous.content===payload.content&&previous.fontSize===payload.fontSize&&previous.warm===payload.warm&&previous.attachmentUrl===payload.attachmentUrl) {
    requestAnimationFrame(()=>{if(generation===rendering)checkEnd();});return;
  }
  contentReady=false;
  document.documentElement.style.setProperty('--font-size',payload.fontSize+'px');
  document.body.classList.toggle('warm',payload.warm);
  document.body.classList.toggle('attachment-preview',payload.kind==='image'||payload.kind==='spreadsheet');
  article.replaceChildren();
  if(payload.kind==='image') {
    const image=document.createElement('img');image.className='preview-image';image.alt='图片预览';image.src=payload.attachmentUrl;
    const hint=document.createElement('p');hint.className='image-hint';hint.textContent='双指缩放 · 双击原图 · 长按保存';
    image.ondblclick=()=>image.classList.toggle('original');
    image.onerror=()=>{hint.textContent='图片加载失败，可返回文件列表后重试。'};
    article.append(hint,image);window.Grove?.headings(payload.id,'[]');return;
  }
  if(payload.kind==='spreadsheet') {
    article.textContent='正在读取工作簿…';
    try {
      const {readWorkbook,showWorkbook}=await import('./spreadsheet.js');
      const response=await fetch(payload.attachmentUrl);if(!response.ok)throw Error();
      const book=readWorkbook(new Uint8Array(await response.arrayBuffer()));
      if(generation!==rendering)return;
      showWorkbook(article,book);
    }catch {article.textContent='无法读取工作簿，可能已加密、损坏或超过预览范围。'}
    window.Grove?.headings(payload.id,'[]');return;
  }
  article.innerHTML=sanitizeMarkdown(md.render(payload.content),DOMPurify);
  renderMath(article);
  const headings=normalizeLinks(article,payload.url);
  document.querySelectorAll('pre:not(.mermaid)').forEach(pre=>{
    const header=document.createElement('div');header.className='code-header';
    const language=pre.querySelector('code')?.className.replace('language-','')||'code';
    const label=document.createElement('span');label.textContent=language;
    const button=document.createElement('button');button.textContent='复制';button.setAttribute('aria-label','复制代码');
    button.onclick=()=>window.Grove?.copyCode(pre.textContent);
    header.append(label,button);pre.before(header);
  });
  if(article.querySelector('.mermaid')) {
    try {
      const {default:mermaid}=await import('mermaid');
      if(generation!==rendering)return;
      mermaid.initialize({startOnLoad:false,securityLevel:'strict',theme:'neutral',
        maxTextSize:50000,flowchart:{htmlLabels:false},themeVariables:{fontFamily:'serif'}});
      await mermaid.run({nodes:[...article.querySelectorAll('.mermaid')],suppressErrors:true});
    }catch { /* Keep the original fenced diagram readable if rendering is unavailable. */ }
  }
  if(generation!==rendering)return;
  window.Grove?.headings(payload.id,JSON.stringify(headings));
  requestAnimationFrame(()=>{
    if(generation!==rendering)return;
    window.scrollTo({left:0,top:isNew?(document.documentElement.scrollHeight-innerHeight)*payload.progress:oldY,behavior:'instant'});
    contentReady=true;checkEnd();
  });
};
window.scrollToHeading=id=>document.getElementById(id)?.scrollIntoView({behavior:'smooth',block:'start'});
document.addEventListener('click',event=>{
  const image=event.target.closest('article img:not(.preview-image)');
  if(image){event.preventDefault();window.Grove?.openImage(image.currentSrc||image.src);return;}
  const anchor=event.target.closest('a');if(!anchor)return;
  event.preventDefault();
  const href=anchor.getAttribute('href');if(!href)return;
  if(href.startsWith('#')){window.scrollToHeading(decodeURIComponent(href.slice(1)));return;}
  window.Grove?.openLink(href);
});
window.addEventListener('scroll',()=>{
  checkEnd();
  clearTimeout(scrollTimer);
  scrollTimer=setTimeout(()=>{
    if(!active)return;
    const max=document.documentElement.scrollHeight-innerHeight;
    window.Grove?.progress(active.id,max>0?Math.min(1,scrollY/max):1);
  },180);
},{passive:true});
window.addEventListener('resize',checkEnd);
