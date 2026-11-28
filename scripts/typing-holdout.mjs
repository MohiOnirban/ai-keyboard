import fs from 'node:fs';
const cases=JSON.parse(fs.readFileSync(new URL('../intelligence/scenarios.json', import.meta.url),'utf8'));
const rows=['kind\tlanguage\tcontext\tinput\texpected'];
for(const {request,expectedLanguage} of cases){
 const lang=request.language==='auto'?expectedLanguage:request.language;
 if(!['en','bn','banglish'].includes(lang))continue;
 for(const clause of request.text.normalize('NFC').toLowerCase().split(/[।.!?]+/)){
  const words=clause.match(/[\p{L}][\p{L}\p{M}]*(?:['’][\p{L}]+)*/gu)??[];
  for(let i=1;i<words.length;i++){
   const word=words[i].replaceAll('’',"'");
   const ctx=words.slice(Math.max(0,i-2),i).join(' ');
   rows.push(['next',lang,ctx,'',word].join('\t'));
   if([...word].length>3){
     rows.push(['completion',lang,ctx,[...word].slice(0,3).join(''),word].join('\t'));
   }
  }
 }
}
process.stdout.write(rows.join('\n')+'\n');
