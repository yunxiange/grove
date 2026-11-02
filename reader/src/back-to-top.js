// The control belongs to the reader shell, outside sanitized document content.
export function installBackToTop(document,{getDocument}) {
  const view=document.defaultView;
  const button=document.createElement('button');
  button.type='button';button.className='reader-back-to-top';button.hidden=true;
  button.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 3h16v2H4zm4 8h3v10h2V11h3l-4-4z"/></svg>';
  document.body.append(button);
  const update=()=>{
    const current=getDocument();
    const text=current&&(!current.kind||current.kind==='markdown');
    button.hidden=!(text&&view.scrollY>Math.max(120,view.innerHeight/2));
    const label='回到顶部';
    button.setAttribute('aria-label',label);button.title=label;
  };
  button.onclick=()=>{
    if(button.hidden)return;
    const reduce=view.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    view.scrollTo({left:0,top:0,behavior:reduce?'instant':'smooth'});
  };
  view.addEventListener('scroll',update,{passive:true});
  view.addEventListener('resize',update);
  update();
  return {update,dispose(){
    view.removeEventListener('scroll',update);view.removeEventListener('resize',update);
    button.onclick=null;button.hidden=true;button.remove();
  }};
}
