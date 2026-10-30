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
