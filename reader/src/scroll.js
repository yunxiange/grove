// One notification per loaded comment page, including pages shorter than the viewport.
export function createEndObserver(position,onEnd) {
  let id=null,page=null,delivered=null;
  return {
    setPage(nextId,nextPage) {
      if(nextId!==id)delivered=null;
      id=nextId;page=nextPage;
    },
    check() {
      if(page==null)return;
      const key=JSON.stringify([id,page]);
      const {y,height,total}=position();
      if(delivered!==key&&height>0&&y+height>=total-160) {
        delivered=key;onEnd(id,page);
      }
    }
  };
}
