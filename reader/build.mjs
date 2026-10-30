import { build } from 'esbuild';
import { cp, mkdir, writeFile, readdir, readFile, rm } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
const out = fileURLToPath(new URL('../app/src/main/assets/reader/', import.meta.url));
await rm(out, {recursive:true,force:true});
await mkdir(out, { recursive: true });
await build({ entryPoints: ['src/main.js'], bundle: true, splitting: true, format: 'esm',
  outdir: out + '/js', minify: true, target: ['chrome90'], legalComments: 'linked',
  loader: { '.woff2': 'file', '.woff': 'file', '.ttf': 'file' }, assetNames: 'fonts/[name]-[hash]' });
await cp('src/index.html', out + '/index.html');
await cp('src/reader.css', out + '/reader.css');
await cp('node_modules/katex/dist/katex.min.css', out + '/katex.min.css');
await cp('node_modules/katex/dist/fonts', out + '/fonts', { recursive: true });
await writeFile(out + '/THIRD_PARTY.txt', [
  'Grove reader bundles the following open-source libraries:',
  'markdown-it (MIT), DOMPurify (Apache-2.0 OR MPL-2.0), highlight.js (BSD-3-Clause),',
  'Mermaid (MIT), KaTeX (MIT), markdown-it-texmath (MIT), markdown-it-task-lists (ISC).',
  'Exact versions are recorded in reader/package-lock.json. Bundled JS license notices are retained.'
].join('\n'));
await mkdir(out+'/licenses',{recursive:true});
const licenses=[];
async function collect(dir) {
  for(const entry of await readdir(dir,{withFileTypes:true})) {
    if(!entry.isDirectory() || entry.name.startsWith('.'))continue;
    const path=dir+'/'+entry.name;
    if(entry.name.startsWith('@')){await collect(path);continue;}
    try {
      const meta=JSON.parse(await readFile(path+'/package.json','utf8'));
      licenses.push({name:meta.name,version:meta.version,license:meta.license});
      for(const name of await readdir(path)) {
        if(/^(licen[cs]e|copying|notice)(\.|$)/i.test(name)) {
          const target=out+'/licenses/'+meta.name.replaceAll('/','_')+'-'+meta.version+'-'+name;
          await cp(path+'/'+name,target,{recursive:true});
        }
      }
      try{await collect(path+'/node_modules');}catch{}
    }catch{}
  }
}
await collect('node_modules');
await writeFile(out+'/THIRD_PARTY.json',JSON.stringify(licenses,null,2));
console.log('Bundled offline reader assets:', out);
