import {MOBI} from '../vendor/foliate-js/mobi.js';

const MAX_TEXT=128*1024*1024;
const MAX_IMAGE=10_000_000;
const clean=value=>String(value||'').replace(/\s+/g,' ').trim().slice(0,160);
const pageNames=new Map([['cover','封面'],['titlepage','扉页'],['title-page','扉页'],['copyright','版权页'],['copyright-page','版权页'],
  ['toc','目录'],['contents','目录'],['table of contents','目录'],['preface','序言'],['foreword','前言'],['introduction','引言']]);
const pageTitle=value=>{
  const title=clean(value).replace(/^["'“”‘’]+|["'“”‘’]+$/g,'').trim();
  if(!title||/^(?:unknown(?: (?:content|title))?|untitled|undefined|null|none|no title|未知(?:内容|标题)?|未命名(?:页面)?|无标题)(?:[ _-]?\d+)?$/i.test(title))return null;
  if(/^(?:part|section|chapter|file|content|index|text|split)[\d_-]*\.x?html?$/i.test(title))return null;
  return pageNames.get(title.toLowerCase())||title;
};
const ascii=bytes=>new TextDecoder('ascii').decode(bytes);
const invalid=()=>new Error('电子书结构无效或文件已损坏。');

// Inspect both halves of combo books before the parser can fall back to MOBI6.
// This rejects encryption; it never attempts to remove or bypass DRM.
export async function validateMobi(file) {
  if(file.size<86)throw invalid();
  const pdb=new DataView(await file.slice(0,78).arrayBuffer());
  if(ascii(new Uint8Array(pdb.buffer,60,8))!=='BOOKMOBI')throw invalid();
  const count=pdb.getUint16(76),tableEnd=78+count*8;
  if(count<2||tableEnd>file.size)throw invalid();
  const table=new DataView(await file.slice(78,tableEnd).arrayBuffer());
  const offsets=Array.from({length:count},(_,i)=>table.getUint32(i*8));
  if(offsets.some((x,i)=>x<tableEnd||x>=file.size||(i&&x<=offsets[i-1])))throw invalid();
  const header=async index=>{
    if(index<0||index>=count)throw invalid();
    const end=offsets[index+1]??file.size;
    if(end-offsets[index]<248||end-offsets[index]>2_000_000)throw invalid();
    const bytes=await file.slice(offsets[index],end).arrayBuffer(),v=new DataView(bytes);
    if(ascii(new Uint8Array(bytes,16,4))!=='MOBI')throw invalid();
    if(v.getUint16(12)!==0)throw new Error('暂不支持加密或受 DRM 保护的 AZW3 / MOBI。');
    if(![1,2,17480].includes(v.getUint16(0)))throw new Error('此电子书使用了不支持的压缩格式。');
    if(!v.getUint16(8)||index+v.getUint16(8)>=count)throw invalid();
    if(v.getUint32(4)>MAX_TEXT)throw new Error('电子书解压后的正文过大，暂时无法排版。');
    const size=v.getUint32(20);
    if(size<232||size+16>bytes.byteLength)throw invalid();
    if(size>=176&&v.getUint32(188)>0&&v.getUint32(188)!==0xffffffff)
      throw new Error('暂不支持加密或受 DRM 保护的 AZW3 / MOBI。');
    if(!(v.getUint32(128)&64))return null;
    const start=size+16;
    if(start+12>bytes.byteLength||ascii(new Uint8Array(bytes,start,4))!=='EXTH')throw invalid();
    const stop=start+v.getUint32(start+4),entries=v.getUint32(start+8);
    if(stop>bytes.byteLength||entries>4096)throw invalid();
    let pos=start+12,boundary=null;
    for(let i=0;i<entries;i++) {
      if(pos+8>stop)throw invalid();
      const type=v.getUint32(pos),length=v.getUint32(pos+4);
      if(length<8||pos+length>stop)throw invalid();
      if(type===121&&length===12)boundary=v.getUint32(pos+8);
      pos+=length;
    }
    return boundary;
  };
  const boundary=await header(0);
  if(boundary!==null&&boundary!==0xffffffff)await header(boundary);
}

const unzlib=async bytes=>new Uint8Array(await new Response(
  new Blob([bytes]).stream().pipeThrough(new DecompressionStream('deflate'))).arrayBuffer());
const imageType=bytes=>{
  const prefix=ascii(bytes.subarray(0,12));
  if(bytes[0]===137&&prefix.slice(1,4)==='PNG')return 'png';
  if(bytes[0]===255&&bytes[1]===216&&bytes[2]===255)return 'jpeg';
  if(prefix.startsWith('GIF8'))return 'gif';
  if(prefix.startsWith('RIFF')&&prefix.slice(8,12)==='WEBP')return 'webp';
  if(prefix.startsWith('BM'))return 'bmp';
  if(prefix.slice(4,12)==='ftypavif'||prefix.slice(4,12)==='ftypavis')return 'avif';
  return null;
};
const base64=bytes=>{
  let text='';
  for(let i=0;i<bytes.length;i+=32768)text+=String.fromCharCode(...bytes.subarray(i,i+32768));
  return btoa(text);
};

/** The same small interface hides MOBI6, PalmDOC/HUFF compression and KF8. */
export async function openMobi(file) {
  await validateMobi(file);
  const parser=new MOBI({unzlib});
  const loadText=parser.loadText.bind(parser);
  const loaded=new Map();let expanded=0;
  const imageReferences=new Set();let textTail='';
  parser.loadText=async index=>{
    const bytes=await loadText(index);
    if(!loaded.has(index)){
      loaded.set(index,bytes.byteLength);expanded+=bytes.byteLength;
      if(parser.headers.mobi.version<8) {
        const source=textTail+ascii(new Uint8Array(bytes));
        for(const match of source.matchAll(/<img\b(?:[^"'<>]|"[^"]*"|'[^']*')*?\srecindex\s*=\s*(?:"(\d+)"|'(\d+)'|(\d+)(?=[\s/>]))/gi))
          imageReferences.add(Number(match[1]||match[2]||match[3])-1);
        textTail=source.slice(-4096);
      }
    }
    if(bytes.byteLength>2_000_000||expanded>MAX_TEXT)throw new Error('电子书解压后的正文过大，暂时无法排版。');
    return bytes;
  };
  const book=await parser.open(file);
  let alive=true;
  try {
    const sections=book.sections.map((s,original)=>({...s,original})).filter(s=>typeof s.load==='function');
    // Legacy MOBI often stores the cover only as EXTH metadata, outside the
    // text stream. KF8 normally already has its own cover section.
    if(parser.headers.mobi.version<8&&sections[0]) {
      const exth=parser.headers.exth;
      const coverIndex=exth?.coverOffset<0xffffffff?exth.coverOffset:exth?.thumbnailOffset;
      // Text records have already been decoded by MOBI6.init(). Inspect their
      // resource references, not only the first page: a cover can follow front matter.
      if(!imageReferences.has(coverIndex)) {
        const cover=await book.getCover?.();
        if(cover&&cover.size<=MAX_IMAGE) {
          const bytes=new Uint8Array(await cover.arrayBuffer()),type=imageType(bytes);
          if(type)sections.unshift({original:-1,html:`<img alt="封面" src="data:image/${type};base64,${base64(bytes)}">`,cover:true});
        }
      }
    }
    if(!sections.length||sections.length>500)throw new Error('电子书没有可阅读的章节，或章节数量超过限制。');
    const resolve=async href=>{
      if(!/^(filepos:\d+|kindle:pos:fid:[\da-v]+:off:[\da-v]+)$/i.test(href))return null;
      const result=await book.resolveHref(href);
      const index=sections.findIndex(s=>s.original===result?.index);
      return index<0?null:{index,anchor:result.anchor};
    };
    const bookTitle=pageTitle(book.metadata?.title)||'电子书';
    const contents=[],titles=sections.map(s=>s.cover?'封面':''),images=new Map(),unlabelled=new Set(),pageLabels=new Map(),landmarkTitles=new Map();
    if(sections[0]?.cover)contents.push({title:'封面',index:0,depth:0});
    const visit=async (items,depth=0)=>{
      if(depth>8)return;
      for(const item of items||[]) {
        if(contents.length>=2000)break;
        const target=await resolve(item.href||'').catch(()=>null);
        if(target) {
          const title=pageTitle(item.label);
          const entry={title:title||`页面 ${target.index+1}`,href:item.href,index:target.index,depth:Math.min(depth,3)};
          contents.push(entry);if(!title)unlabelled.add(entry);
          if(title&&!titles[target.index])titles[target.index]=title;
        }
        await visit(item.subitems,depth+1);
      }
    };
    await visit(book.toc);
    for(const item of book.landmarks||[]) {
      const target=await resolve(item.href||'').catch(()=>null);
      if(target) {
        const semantic=(item.type||[]).map(type=>pageNames.get(type.toLowerCase())).find(Boolean);
        if(semantic)pageLabels.set(target.index,semantic);
        else {const label=pageTitle(item.label);if(label)landmarkTitles.set(target.index,label);}
      }
    }
    const localImage=async url=>{
      // Only URLs manufactured by the local parser can be fetched. Never fetch
      // remote URLs, CSS imports or original book scripts from chapter markup.
      if(!url.startsWith('blob:'))return null;
      if(images.has(url))return images.get(url);
      const response=await fetch(url),blob=await response.blob();
      if(blob.size>MAX_IMAGE)return null;
      const bytes=new Uint8Array(await blob.arrayBuffer()),type=imageType(bytes);
      const data=type?`data:image/${type};base64,${base64(bytes)}`:null;
      images.set(url,data);return data;
    };
    const prepared=new Map();
    return {
      total:sections.length,
      metadata:{title:bookTitle,author:(book.metadata?.author||[]).map(clean).join('、')},
      contents,resolve,
      async chapter(index,href=null) {
        if(!alive)throw new Error('Book closed');
        if(!Number.isInteger(index)||!sections[index])throw invalid();
        let entry=prepared.get(index);
        if(entry){prepared.delete(index);prepared.set(index,entry);}
        else {
          entry={chars:0,promise:null};
          entry.promise=(async()=>{
        const source=sections[index].html??await (await fetch(await sections[index].load())).text();
        if(!alive){book.destroy();throw new Error('Book closed');}
        if(source.length>2_000_000)throw new Error('当前章节过大，暂时无法排版。');
        const doc=new DOMParser().parseFromString(source,'text/html');
        // Legacy <font size> is absolute and would ignore Grove's reading
        // preferences. Keep heading hierarchy, then inherit paper/ink/type.
        for(const font of doc.querySelectorAll('font')) {
          const parent=font.parentElement;
          if(Number(font.getAttribute('size'))>=5&&parent?.localName==='p'&&clean(parent.textContent)===clean(font.textContent)) {
            const heading=doc.createElement(Number(font.getAttribute('size'))>=7?'h1':'h2');
            heading.append(...parent.childNodes);parent.replaceWith(heading);
          }
          font.replaceWith(...font.childNodes);
        }
        // Use a private root accepted by the existing EPUB sanitizer. Resources
        // become local data images; no chapter URL is ever navigated in WebView.
        const root='https://appassets.androidplatform.net/epub/mobi/';
        for(const node of doc.querySelectorAll('img,image')) {
          const attr=node.localName==='image'?'href':'src';
          const original=node.getAttribute(attr)||node.getAttribute('xlink:href')||'';
          if(sections[index].cover&&original.startsWith('data:image/'))continue;
          const data=await localImage(original).catch(()=>null);
          node.removeAttribute('xlink:href');
          if(data)node.setAttribute(attr,data);else node.removeAttribute(attr);
        }
        for(const a of doc.querySelectorAll('a[href]')) {
          const href=a.getAttribute('href');
          if(/^(filepos:|kindle:pos:)/i.test(href))a.setAttribute('href',root+'link?href='+encodeURIComponent(href));
          else if(!/^#|^https?:\/\//i.test(href))a.removeAttribute('href');
        }
        const heading=[...doc.querySelectorAll('h1,h2,h3,h4')].map(h=>pageTitle(h.textContent)).find(Boolean);
        const title=pageLabels.get(index)||titles[index]||heading||pageTitle(doc.title)||landmarkTitles.get(index)||'';
        titles[index]=title;
        for(const entry of unlabelled)if(entry.index===index)entry.title=title||`页面 ${index+1}`;
            const html=doc.body.innerHTML;entry.chars=html.length;
            return {html,title,url:root+`section-${index}.html`,anchor:null};
          })();
          prepared.set(index,entry);
          entry.promise.catch(()=>{if(prepared.get(index)===entry)prepared.delete(index);});
        }
        const base=await entry.promise;
        // At most current/previous/next, bounded to 16 MiB of UTF-16 markup.
        while(prepared.size>3||[...prepared.values()].reduce((n,x)=>n+x.chars,0)>8_000_000)prepared.delete(prepared.keys().next().value);
        if(!href)return base;
        const doc=new DOMParser().parseFromString(base.html,'text/html');
        let anchor=null;
        if(href) {
          const target=await resolve(href);
          const node=target?.index===index?target.anchor(doc):null;
          if(node?.nodeType===1){node.id='grove-mobi-destination';anchor=node.id;}
        }
        return {...base,html:doc.body.innerHTML,anchor};
      },
      destroy(){alive=false;book.destroy();images.clear();loaded.clear();prepared.clear();},
    };
  }catch(error){book.destroy();throw error;}
}
