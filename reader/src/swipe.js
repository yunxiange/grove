// Observe only a deliberate one-finger horizontal gesture. Scrolling, text
// selection and pinch zoom keep ownership of their own touches.
export function installChapterSwipe(document,{getDocument,onSwipe}) {
  const window=document.defaultView;
  let gesture=null;
  const selected=()=>window.getSelection()?.isCollapsed===false;
  const zoomed=()=>window.visualViewport?.scale>1.01;
  const active=()=>getDocument();
  const parents=target=>{
    const result=[];
    const scrollRoot=document.scrollingElement||document.documentElement;
    for(let element=target; element?.nodeType===1;element=element.parentElement) {
      const root=element===scrollRoot;
      const width=element.clientWidth||(root?window.innerWidth:0);
      const max=element.scrollWidth-width;
      if(max>2&&(root||/auto|scroll/.test(window.getComputedStyle(element).overflowX)))
        result.push({element,left:element.scrollLeft,max});
    }
    return result;
  };
  const start=event=>{
    gesture=null;
    const current=active();
    if(!current||event.touches.length!==1||selected()||zoomed())return;
    if(event.target.closest?.('a,button,input,select,textarea,[contenteditable="true"]'))return;
    const point=event.touches[0];
    gesture={id:current.id,touch:point.identifier,x:point.clientX,y:point.clientY,time:event.timeStamp,
      scrolls:parents(event.target),claimed:false,moving:false};
  };
  const allowed=(g,dx)=>g.scrolls.every(({element,left,max})=>
    Math.abs(element.scrollLeft-left)<2&&(dx<0?left>=max-2:left<=2));
  const move=event=>{
    const g=gesture;
    if(!g)return;
    if(event.touches.length!==1||active()?.id!==g.id||selected()||zoomed()){gesture=null;return;}
    const point=[...event.touches].find(t=>t.identifier===g.touch);
    if(!point){gesture=null;return;}
    const dx=point.clientX-g.x,dy=point.clientY-g.y;
    if(!g.moving&&event.timeStamp-g.time>500){gesture=null;return;}
    if(Math.max(Math.abs(dx),Math.abs(dy))>8)g.moving=true;
    if(Math.abs(dy)>8&&Math.abs(dy)>=Math.abs(dx)){gesture=null;return;}
    if(Math.abs(dx)>8&&Math.abs(dx)>Math.abs(dy)*1.5&&allowed(g,dx)) {
      g.claimed=true;
      if(event.cancelable)event.preventDefault();
    }
  };
  const end=event=>{
    const g=gesture;gesture=null;
    if(!g||!g.claimed||event.touches.length||active()?.id!==g.id||selected()||zoomed())return;
    const point=[...event.changedTouches].find(t=>t.identifier===g.touch);
    if(!point)return;
    const dx=point.clientX-g.x,dy=point.clientY-g.y;
    const threshold=Math.min(120,Math.max(64,window.innerWidth*.18));
    if(Math.abs(dx)>=threshold&&Math.abs(dx)>Math.abs(dy)*1.5&&allowed(g,dx))onSwipe(g.id,dx<0?1:-1);
  };
  const cancel=()=>{gesture=null;};
  document.addEventListener('touchstart',start,{passive:true});
  document.addEventListener('touchmove',move,{passive:false});
  document.addEventListener('touchend',end,{passive:true});
  document.addEventListener('touchcancel',cancel,{passive:true});
  return ()=>{
    document.removeEventListener('touchstart',start);document.removeEventListener('touchmove',move);
    document.removeEventListener('touchend',end);document.removeEventListener('touchcancel',cancel);
  };
}
