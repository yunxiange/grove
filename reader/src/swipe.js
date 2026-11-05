// Observe only a deliberate one-finger horizontal gesture. Scrolling, text
// selection and pinch zoom keep ownership of their own touches.
export function installChapterSwipe(document,{getDocument,onSwipe}) {
  const window=document.defaultView,paper=document.querySelector('article');
  let gesture=null,snapshot=null,pending=null,pendingTimer=0,animations=[],revision=0;
  const reduced=()=>window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  const active=()=>getDocument();
  const width=()=>window.innerWidth;
  const reset=()=>{
    revision++;
    for(const animation of animations)animation.cancel();
    animations=[];snapshot?.remove();snapshot=null;
    if(paper){paper.style.transform='';paper.style.willChange='';paper.style.visibility='';}
  };
  const clearPending=()=>{pending=null;window.clearTimeout(pendingTimer);pendingTimer=0;};
  const complete=motions=>{
    animations=motions;
    const current=revision;
    Promise.all(motions.map(animation=>animation.finished)).then(()=>{
      if(current===revision)reset();
    },()=>{if(current===revision)reset();});
  };
  const freeze=()=>{
    if(snapshot||!paper)return;
    // Freeze the old viewport before native navigation replaces the article.
    // The fixed, clipped sheet keeps its scroll position when the new one resets.
    const rect=paper.getBoundingClientRect(),copy=paper.cloneNode(true);
    snapshot=document.createElement('div');
    snapshot.dataset.readerTurnSnapshot='';snapshot.setAttribute('aria-hidden','true');snapshot.inert=true;
    Object.assign(snapshot.style,{position:'fixed',left:'0',top:'0',width:width()+'px',height:window.innerHeight+'px',
      overflow:'hidden',pointerEvents:'none',zIndex:'30',background:'var(--paper, #f9f7f1)',
      boxShadow:'0 0 18px #302e282b',willChange:'transform'});
    Object.assign(copy.style,{position:'absolute',left:rect.left+'px',top:rect.top+'px',
      width:(rect.width||width())+'px',margin:'0',transform:'',visibility:'visible',willChange:''});
    snapshot.append(copy);document.body.append(snapshot);
    paper.style.visibility='hidden';
  };
  const drag=dx=>{
    if(!paper||reduced())return;
    freeze();
    snapshot.style.transform=`translate3d(${Math.max(-width(),Math.min(width(),dx))}px,0,0)`;
  };
  const settle=()=>{
    clearPending();
    if(!snapshot?.animate||reduced()){reset();return;}
    complete([snapshot.animate([{transform:snapshot.style.transform},{transform:'translate3d(0,0,0)'}],
      {duration:150,easing:'cubic-bezier(.2,.8,.2,1)',fill:'forwards'})]);
  };
  const selected=()=>window.getSelection()?.isCollapsed===false;
  const zoomed=()=>window.visualViewport?.scale>1.01;
  const canTurn=dx=>dx<0?active()?.canSwipeNext!==false:active()?.canSwipePrevious!==false;
  const parents=target=>{
    const result=[];
    const scrollRoot=document.scrollingElement||document.documentElement;
    for(let element=target; element?.nodeType===1;element=element.parentElement) {
      const root=element===scrollRoot;
      const viewport=element.clientWidth||(root?window.innerWidth:0);
      const max=element.scrollWidth-viewport;
      if(max>2&&(root||/auto|scroll/.test(window.getComputedStyle(element).overflowX)))
        result.push({element,left:element.scrollLeft,max});
    }
    return result;
  };
  const start=event=>{
    if(pending&&!active())return;
    gesture=null;clearPending();reset();
    const current=active();
    if(!current||event.touches.length!==1||selected()||zoomed())return;
    if(event.target.closest?.('a,button,input,select,textarea,[contenteditable="true"]'))return;
    const point=event.touches[0];
    gesture={id:current.id,touch:point.identifier,x:point.clientX,y:point.clientY,time:event.timeStamp,
      scrolls:parents(event.target),claimed:false,moving:false};
  };
  const allowed=(g,dx)=>canTurn(dx)&&g.scrolls.every(({element,left,max})=>
    Math.abs(element.scrollLeft-left)<2&&(dx<0?left>=max-2:left<=2));
  const cancel=()=>{gesture=null;clearPending();reset();};
  const move=event=>{
    const g=gesture;
    if(!g)return;
    if(event.touches.length!==1||active()?.id!==g.id||selected()||zoomed()){cancel();return;}
    const point=[...event.touches].find(t=>t.identifier===g.touch);
    if(!point){cancel();return;}
    const dx=point.clientX-g.x,dy=point.clientY-g.y;
    if(!g.moving&&event.timeStamp-g.time>500){cancel();return;}
    if(Math.max(Math.abs(dx),Math.abs(dy))>8)g.moving=true;
    if(Math.abs(dy)>8&&Math.abs(dy)>=Math.abs(dx)){cancel();return;}
    if(Math.abs(dx)>8&&Math.abs(dx)>Math.abs(dy)*1.5&&allowed(g,dx)) {
      g.claimed=true;drag(dx);
      if(event.cancelable)event.preventDefault();
    }else if(g.claimed) {g.claimed=false;reset();}
  };
  const end=event=>{
    const g=gesture;gesture=null;
    if(!g||!g.claimed||event.touches.length||active()?.id!==g.id||selected()||zoomed()){settle();return;}
    const point=[...event.changedTouches].find(t=>t.identifier===g.touch);
    if(!point){settle();return;}
    const dx=point.clientX-g.x,dy=point.clientY-g.y;
    const threshold=Math.min(120,Math.max(64,width()*.18));
    if(Math.abs(dx)>=threshold&&Math.abs(dx)>Math.abs(dy)*1.5&&allowed(g,dx)) {
      drag(dx);
      pending={id:g.id,direction:dx<0?1:-1,offset:Math.max(-width(),Math.min(width(),dx))};
      // A failed/ignored native request must never leave the live page hidden.
      pendingTimer=window.setTimeout(settle,4000);
      onSwipe(g.id,pending.direction);
    }else settle();
  };
  document.addEventListener('touchstart',start,{passive:true});
  document.addEventListener('touchmove',move,{passive:false});
  document.addEventListener('touchend',end,{passive:true});
  document.addEventListener('touchcancel',cancel,{passive:true});
  window.addEventListener('pagehide',cancel);
  window.addEventListener('resize',cancel);
  const dispose=()=>{
    cancel();window.removeEventListener('pagehide',cancel);window.removeEventListener('resize',cancel);
    document.removeEventListener('touchstart',start);document.removeEventListener('touchmove',move);
    document.removeEventListener('touchend',end);document.removeEventListener('touchcancel',cancel);
  };
  dispose.present=id=>{
    const turn=pending;
    if(!turn||id===turn.id)return;
    clearPending();
    if(!snapshot?.animate||!paper?.animate||reduced()){reset();return;}
    paper.style.visibility='';paper.style.willChange='transform';
    const options={duration:240,easing:'cubic-bezier(.2,.7,.2,1)',fill:'both'};
    complete([
      snapshot.animate([{transform:snapshot.style.transform},{transform:`translate3d(${-turn.direction*width()}px,0,0)`}],options),
      paper.animate([{transform:`translate3d(${turn.direction*width()+turn.offset}px,0,0)`},{transform:'translate3d(0,0,0)'}],options)
    ]);
  };
  dispose.cancel=cancel;
  dispose.dragging=()=>!!gesture?.claimed||!!pending||animations.length>0;
  return dispose;
}
