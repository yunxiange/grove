import DOMPurify from 'dompurify';
import { createRenderer, normalizeLinks, sanitizeMarkdown, renderMath, renderEpub, layoutTables } from './render.js';
import {createEndObserver} from './scroll.js';
import {installChapterSwipe} from './swipe.js';
import {installBackToTop} from './back-to-top.js';
const md=createRenderer();
const article=document.querySelector('article');
let active=null;
let rendering=0;
let scrollTimer;
let contentReady=false;
let disposeTableLayout=()=>{};
let mobiSession=null;
let prefetchTimer=0;
const cancelPrefetch=()=>{clearTimeout(prefetchTimer);prefetchTimer=0;};
const disposeMobi=()=>{cancelPrefetch();const old=mobiSession;mobiSession=null;old?.promise.then(book=>book.destroy()).catch(()=>{});};
const prefetchMobi=(book,index)=>{
  cancelPrefetch();
  const id=active?.id,session=mobiSession;
  const nearby=[index+1,index-1].filter(x=>x>=0&&x<book.total);
  const next=()=>{
    if(active?.id!==id||mobiSession!==session||!contentReady||!nearby.length)return;
    const section=nearby[0];
    prefetchTimer=window.setTimeout(()=>{
      if(active?.id!==id||mobiSession!==session||!contentReady)return;
      if(chapterSwipe.dragging?.()){next();return;}
      nearby.shift();
      book.chapter(section).catch(()=>{}).finally(next);
    },250);
  };
  next();
};
window.addEventListener('pagehide',disposeMobi);
const loadMobi=payload=>{
  // Parser-created inert documents must not request tracking images from book
  // markup. This reader instance belongs to the native MOBI view; its images
  // are decoded locally and rewritten to data URLs before display.
  if(!document.head.querySelector('meta[data-grove-mobi-policy]')) {
    const policy=document.createElement('meta');policy.httpEquiv='Content-Security-Policy';
    policy.content='img-src data: blob:';policy.dataset.groveMobiPolicy='true';document.head.append(policy);
  }
  if(mobiSession?.url!==payload.attachmentUrl) {
    disposeMobi();
    mobiSession={url:payload.attachmentUrl,promise:(async()=>{
      const {openMobi}=await import('./mobi-book.js');
      const response=await fetch(payload.attachmentUrl);
      if(!response.ok)throw Error('电子书缓存无法读取，请重新打开文件。');
      return openMobi(await response.blob());
    })()};
  }
  return mobiSession.promise;
};
const backToTop=installBackToTop(document,{getDocument:()=>contentReady?active:null});
const chapterSwipe=installChapterSwipe(document,{
  getDocument:()=>contentReady&&['epub','mobi'].includes(active?.kind)&&active.swipePages?active:null,
  onSwipe:(id,direction)=>window.Grove?.swipePage?.(id,direction)
});
const endObserver=createEndObserver(()=>({y:scrollY,height:innerHeight,total:document.documentElement.scrollHeight}),
  (id,page)=>window.Grove?.reachedEnd(id,page));
const checkEnd=()=>{if(contentReady)endObserver.check();};
const layoutPreviewImage=image=>{
  if(!image.naturalWidth||!image.naturalHeight)return;
  const vector=image.classList.contains('vector-preview');
  const width=vector?(article.clientWidth||innerWidth)*(image.classList.contains('original')?2:1):image.naturalWidth;
  image.style.width=width+'px';
  image.style.height=(width*image.naturalHeight/image.naturalWidth)+'px';
  if(vector)image.style.maxHeight='none';
};
const updatePreviewViewport=()=>{
  if(innerHeight>0)document.documentElement.style.setProperty('--preview-height',innerHeight+'px');
  const image=article.querySelector('.preview-image');
  if(image)layoutPreviewImage(image);
};
const finishRender=(payload,generation,top,describe,onReady)=>{
  requestAnimationFrame(()=>{
    if(generation!==rendering)return;
    window.scrollTo({left:0,top:typeof top==='function'?top():top,behavior:'instant'});
    contentReady=true;backToTop.update();checkEnd();
    if(['epub','mobi'].includes(payload.kind))chapterSwipe.present?.(payload.id);
    onReady?.();
    requestAnimationFrame(()=>{
      if(generation!==rendering)return;
      if(describe)window.Grove?.previewStatus?.(payload.id,describe());
      window.Grove?.previewPresented?.(payload.id);
    });
  });
};
const imageStatus=image=>{
  const r=image.getBoundingClientRect(),style=getComputedStyle(image);
  return `图片已解码 ${image.naturalWidth} × ${image.naturalHeight}；元素 ${Math.round(r.width)} × ${Math.round(r.height)} @ ${Math.round(r.left)},${Math.round(r.top)}；样式高度 ${style.height} / 上限 ${style.maxHeight}；视口 ${innerWidth} × ${innerHeight}`;
};

const renderDocument=async payload=>{
  const generation=++rendering;
  const isNew=active?.id!==payload.id;
  const previous=active;
  const oldY=scrollY;
  if(!['epub','mobi'].includes(payload.kind)||previous?.kind!==payload.kind||previous?.attachmentUrl!==payload.attachmentUrl)chapterSwipe.cancel?.();
  if(payload.kind==='mobi'&&previous?.id===payload.id) {
    payload.canSwipePrevious=previous.canSwipePrevious;payload.canSwipeNext=previous.canSwipeNext;
  }
  active=payload;
  if(payload.kind!=='mobi')disposeMobi();
  endObserver.setPage(payload.id,payload.endPage);
  if(contentReady&&!isNew&&previous.content===payload.content&&previous.fontSize===payload.fontSize&&previous.warm===payload.warm&&previous.attachmentUrl===payload.attachmentUrl&&previous.attachmentMimeType===payload.attachmentMimeType) {
    requestAnimationFrame(()=>{if(generation===rendering)checkEnd();});return;
  }
  cancelPrefetch();
  contentReady=false;
  backToTop.update();
  updatePreviewViewport();
  document.documentElement.style.setProperty('--font-size',payload.fontSize+'px');
  document.body.classList.toggle('warm',payload.warm);
  document.body.classList.toggle('attachment-preview',payload.kind==='image'||payload.kind==='spreadsheet');
  disposeTableLayout();disposeTableLayout=()=>{};
  const keepPage=payload.kind==='mobi'&&previous?.kind==='mobi'&&previous.attachmentUrl===payload.attachmentUrl&&article.childNodes.length>0;
  if(!keepPage&&payload.kind!=='epub')article.replaceChildren();
  let mobi=null;
  if(payload.kind==='mobi') {
    if(!keepPage)article.textContent='正在解析电子书…';
    try {
      const book=await loadMobi(payload);
      if(generation!==rendering)return;
      const restored=Math.min(book.total-Number.EPSILON,Math.max(0,payload.progress*book.total));
      let index=payload.bookSection>=0?payload.bookSection:Math.min(book.total-1,Math.floor(restored));
      if(payload.bookHref)index=(await book.resolve(payload.bookHref))?.index??index;
      const chapter=await book.chapter(index,payload.bookHref);
      if(generation!==rendering)return;
      payload.canSwipePrevious=index>0;payload.canSwipeNext=index+1<book.total;
      renderEpub(article,chapter.html,chapter.url,DOMPurify);
      mobi={book,bookTitle:book.metadata.title,author:book.metadata.author,total:book.total,section:index,title:chapter.title,contents:book.contents,
        progress:payload.bookSection>=0?(payload.bookProgress||0):Math.min(1,restored-index),anchor:chapter.anchor};
    }catch(error) {
      if(generation!==rendering)return;
      disposeMobi();
      const message=/[\u3400-\u9fff]/.test(error.message)?error.message:'电子书无法读取，文件可能已损坏或格式不受支持。';
      article.textContent=message;
      finishRender(payload,generation,0,null,()=>window.Grove?.bookState?.(payload.id,JSON.stringify({error:message})));
      return;
    }
  }
  if(payload.kind==='image') {
    const image=document.createElement('img');image.className='preview-image';image.alt='图片预览';image.src=payload.attachmentUrl;
    const vector=payload.attachmentMimeType==='image/svg+xml';
    image.classList.toggle('vector-preview',vector);
    const hint=document.createElement('p');hint.className='image-hint';hint.textContent=vector?'双指缩放 · 双击放大 · 长按保存':'双指缩放 · 双击原图 · 长按保存';
    image.ondblclick=()=>{image.classList.toggle('original');layoutPreviewImage(image);};
    image.onload=()=>{
      // SVGs with only a viewBox can retain a zero auto size in Android WebView.
      // Supply decoded dimensions while CSS still constrains the image to the viewport.
      image.width=image.naturalWidth;image.height=image.naturalHeight;
      updatePreviewViewport();
      finishRender(payload,generation,0,()=>imageStatus(image));
    };
    image.onerror=()=>{hint.textContent='图片加载失败，可返回文件列表后重试。';window.Grove?.renderFailed?.(payload.id);};
    article.append(hint,image);window.Grove?.previewStatus?.(payload.id,`图片节点已创建；视口 ${innerWidth} × ${innerHeight}`);window.Grove?.headings(payload.id,'[]');return;
  }
  if(payload.kind==='spreadsheet') {
    article.textContent='正在读取工作簿…';
    window.Grove?.previewStatus?.(payload.id,'正在读取本地表格');
    try {
      const {readWorkbook,showWorkbook}=await import('./spreadsheet.js');
      const response=await fetch(payload.attachmentUrl);if(!response.ok)throw Error();
      const book=readWorkbook(new Uint8Array(await response.arrayBuffer()));
      if(generation!==rendering)return;
      showWorkbook(article,book);
      finishRender(payload,generation,0,()=>`表格已渲染 ${article.querySelectorAll('tbody tr').length} 行；滚动 ${Math.round(scrollY)}；视口 ${innerWidth} × ${innerHeight}`);
    }catch {if(generation!==rendering)return;article.textContent='无法读取工作簿，可能已加密、损坏或超过预览范围。';window.Grove?.renderFailed?.(payload.id);}
    window.Grove?.headings(payload.id,'[]');return;
  }
  if(payload.kind==='mobi') { /* The local parser has supplied a sanitized chapter. */ }
  else if(payload.kind==='epub')renderEpub(article,payload.content,payload.url,DOMPurify);
  else article.innerHTML=sanitizeMarkdown(md.render(payload.content),DOMPurify);
  renderMath(article);
  const headings=['epub','mobi'].includes(payload.kind)?[]:normalizeLinks(article,payload.url);
  disposeTableLayout=layoutTables(article);
  article.querySelectorAll('pre:not(.mermaid)').forEach(pre=>{
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
  finishRender(payload,generation,()=>isNew?(document.documentElement.scrollHeight-innerHeight)*(mobi?.progress??payload.progress):oldY,null,
    mobi?()=>{
      const {book,...info}=mobi;
      window.Grove?.bookState?.(payload.id,JSON.stringify(info));
      prefetchMobi(book,mobi.section);
    }:null);
  if(mobi?.anchor&&isNew)requestAnimationFrame(()=>{
    if(generation===rendering)document.getElementById(mobi.anchor)?.scrollIntoView();
  });
  if(payload.kind==='epub'&&isNew&&new URL(payload.url).hash)requestAnimationFrame(()=>{
    document.getElementById(decodeURIComponent(new URL(payload.url).hash.slice(1)))?.scrollIntoView();
  });
};
window.scrollToHeading=id=>document.getElementById(id)?.scrollIntoView({behavior:'smooth',block:'start'});
document.addEventListener('click',event=>{
  const image=event.target.closest('article img:not(.preview-image)');
  if(image){event.preventDefault();if(!['epub','mobi'].includes(active?.kind))window.Grove?.openImage(image.currentSrc||image.src);return;}
  const anchor=event.target.closest('a');if(!anchor)return;
  event.preventDefault();
  const href=anchor.getAttribute('href');if(!href)return;
  if(active?.kind==='mobi') {
    const url=new URL(href,active.url);
    if(url.origin==='https://appassets.androidplatform.net'&&url.pathname==='/epub/mobi/link') {
      window.Grove?.bookLink?.(active.id,url.searchParams.get('href'));return;
    }
  }
  if(href.startsWith('#')){window.scrollToHeading(decodeURIComponent(href.slice(1)));return;}
  window.Grove?.openLink(href);
});
window.addEventListener('scroll',()=>{
  checkEnd();
  clearTimeout(scrollTimer);
  scrollTimer=setTimeout(()=>{
    if(!active||!contentReady)return;
    const max=document.documentElement.scrollHeight-innerHeight;
    window.Grove?.progress(active.id,max>0?Math.min(1,scrollY/max):1);
  },180);
},{passive:true});
window.addEventListener('resize',()=>{updatePreviewViewport();checkEnd();});
window.renderDocument=payload=>renderDocument(payload).catch(()=>{
  if(active?.id!==payload.id)return;
  contentReady=false;backToTop.update();
  disposeTableLayout();disposeTableLayout=()=>{};
  article.textContent='预览加载失败，请重新加载。';
  window.Grove?.renderFailed?.(payload.id);
});
window.Grove?.readerReady?.();
