// A small, independently assembled MOBI6 container for image, hostile markup,
// UTF-8, PalmDOC literal compression and byte-offset link regression coverage.
export function makeMobi({compression=1,encryption=0,source=null,coverMeta=false,anchorMarker='<h1>第二章'}={}) {
  const encoder=new TextEncoder();
  let html='<html><body><p><font size="7" color="red" face="Comic Sans MS">中文书名</font></p><img recindex="1"><img src="https://example.com/tracker"><script>window.pwned=true</script><style>body{display:none}</style><a filepos="0000000000">下一章</a><mbp:pagebreak/><h1>第二章</h1><p id="inside">中文正文</p></body></html>';
  html=source??html;
  const target=encoder.encode(html.slice(0,html.indexOf(anchorMarker))).length;
  html=html.replace('filepos="0000000000"',`filepos="${String(target).padStart(10,'0')}"`);
  const raw=encoder.encode(html);
  const packed=compression===2?Uint8Array.from(Array.from({length:Math.ceil(raw.length/8)},(_,i)=>[Math.min(8,raw.length-i*8),...raw.subarray(i*8,i*8+8)]).flat()):raw;
  const title=encoder.encode('中文书名');
  const titleOffset=coverMeta?272:248;
  const header=new Uint8Array(titleOffset+title.length),h=new DataView(header.buffer);
  h.setUint16(0,compression);h.setUint32(4,raw.length);h.setUint16(8,1);h.setUint16(10,4096);h.setUint16(12,encryption);
  header.set(encoder.encode('MOBI'),16);h.setUint32(20,232);h.setUint32(24,2);h.setUint32(28,65001);h.setUint32(32,1);h.setUint32(36,6);
  h.setUint32(84,titleOffset);h.setUint32(88,title.length);h.setUint32(108,2);h.setUint32(244,0xffffffff);header.set(title,titleOffset);
  if(coverMeta) {
    h.setUint32(128,64);header.set(encoder.encode('EXTH'),248);h.setUint32(252,24);h.setUint32(256,1);
    h.setUint32(260,201);h.setUint32(264,12);h.setUint32(268,0);
  }
  const image=Uint8Array.from(atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVQIHWP4z8DwHwAFgAI/ScLbtAAAAABJRU5ErkJggg=='),c=>c.charCodeAt(0));
  const pdb=new Uint8Array(104),v=new DataView(pdb.buffer);pdb.set(encoder.encode('BOOKMOBI'),60);v.setUint16(76,3);
  v.setUint32(78,pdb.length);v.setUint32(86,pdb.length+header.length);v.setUint32(94,pdb.length+header.length+packed.length);
  return new Blob([pdb,header,packed,image]);
}

export async function makeEncryptedCombo() {
  const bytes=new Uint8Array(await makeMobi().arrayBuffer()),v=new DataView(bytes.buffer);
  const count=v.getUint16(76),offsets=Array.from({length:count},(_,i)=>v.getUint32(78+i*8));
  const records=offsets.map((x,i)=>bytes.slice(x,offsets[i+1]??bytes.length));
  const title=records[0].subarray(248),header=new Uint8Array(records[0].length+24),h=new DataView(header.buffer);
  header.set(records[0].subarray(0,248));header.set(title,272);h.setUint32(84,272);h.setUint32(128,64);
  header.set(new TextEncoder().encode('EXTH'),248);h.setUint32(252,24);h.setUint32(256,1);
  h.setUint32(260,121);h.setUint32(264,12);h.setUint32(268,count);
  const encrypted=records[0].slice(),e=new DataView(encrypted.buffer);e.setUint16(12,2);e.setUint32(36,8);
  const all=[header,...records.slice(1),encrypted,...records.slice(1)];
  const pdb=new Uint8Array(78+all.length*8+2),p=new DataView(pdb.buffer);pdb.set(bytes.subarray(0,78));p.setUint16(76,all.length);
  let offset=pdb.length;all.forEach((record,i)=>{p.setUint32(78+i*8,offset);offset+=record.length;});
  return new Blob([pdb,...all]);
}
