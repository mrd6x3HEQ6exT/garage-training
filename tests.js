#!/usr/bin/env node
/* Rack & Roll — permanent regression suite.
   Run: node tests.js   (from the folder containing index.html)
   Every build must pass this before upload. Add checks; never delete them. */
const fs=require("fs");
const html=fs.readFileSync(__dirname+"/index.html","utf8");
const m=[...html.matchAll(/<script>([\s\S]*?)<\/script>/g)].map(x=>x[1]).sort((a,b)=>b.length-a.length)[0];
let viewHTML="";
function realCL(){const s=new Set();return{add:(...x)=>x.forEach(v=>s.add(v)),remove:(...x)=>x.forEach(v=>s.delete(v)),toggle:()=>{},contains:x=>s.has(x)};}
function mkEl(){return new Proxy(function(){},{apply:()=>mkEl(),get:(t,p)=>{if(p==='innerHTML')return viewHTML;if(p==='value')return '';if(p==='classList')return realCL();if(p==='dataset')return{};if(p==='style')return{};if(p==='querySelector')return()=>mkEl();return(typeof p==='symbol')?undefined:mkEl();},set:(t,p,v)=>{if(p==='innerHTML'){viewHTML=v;}return true;}});}
const rt={classList:realCL(),addEventListener:()=>{}};const brQ={textContent:""};const br={classList:realCL(),addEventListener:()=>{},querySelector:()=>brQ};
const store={};const LS={getItem:k=>k in store?store[k]:null,setItem:(k,v)=>{store[k]=String(v);},removeItem:k=>{delete store[k];},clear:()=>{for(const k in store)delete store[k];}};
global.document={getElementById:id=>id==='rtimer'?rt:id==='bigrest'?br:mkEl(),querySelector:()=>mkEl(),querySelectorAll:()=>({forEach:()=>{}}),createElement:()=>mkEl(),addEventListener:()=>{},body:{appendChild:()=>{}},visibilityState:'visible'};
global.window={scrollTo:()=>{},localStorage:LS,addEventListener:()=>{},Audio:function(){this.play=()=>Promise.resolve()},AudioContext:function(){this.currentTime=0;this.resume=()=>{};this.createOscillator=()=>({frequency:{value:0,setValueAtTime:()=>{},exponentialRampToValueAtTime:()=>{}},detune:{value:0},type:'',connect:()=>{},start:()=>{},stop:()=>{}});this.createGain=()=>({connect:()=>{},gain:{setValueAtTime:()=>{},exponentialRampToValueAtTime:()=>{}}});this.createBiquadFilter=()=>({frequency:{value:0},Q:{value:0},type:'',connect:()=>{}});this.destination={};}};
global.localStorage=LS;global.indexedDB={open:()=>({})};global.prompt=()=>"x";global.confirm=()=>true;
Object.defineProperty(globalThis,'navigator',{value:{vibrate:()=>{},serviceWorker:{register:()=>({addEventListener:()=>{}}),addEventListener:()=>{}}},configurable:true});

async function __suite(){ const ob=id=>EXERCISES.find(e=>e.id===id);
let pass=0,fail=0; const T=(n,ok,x)=>{ if(ok){pass++;} else {fail++; console.log("FAIL  "+n+(x!==undefined?"  → "+x:""));} };
await boot();
// math
T("e1RM single exact", est1RM(200,1)===200);
T("e1RM Epley", Math.round(est1RM(225,5))===263);
T("e1RM NaN-safe", est1RM(NaN,5)===0&&est1RM("",5)===0);
// prefill scaling
STATE.workouts=[{id:"w1",date:Date.now()-86400000,focus:"push",focusName:"Push",exercises:[{id:"bb_bench",name:"Bench",sets:[{w:"185",reps:"6",done:true}]}],conditioning:[],groups:[]}];
STATE.settings.goal="lean"; let b=buildLoggable(ob("bb_bench"));
T("prefill scales down for higher target reps", +b.sets[0].w<185&&+b.sets[0].w>=155, b.sets[0].w);
STATE.settings.goal="strong"; b=buildLoggable(ob("bb_bench"));
T("prefill scales up for lower target reps", +b.sets[0].w>185, b.sets[0].w);
STATE.workouts=[];
// drops
STATE.settings.goal="strong"; let d=0; for(let i=0;i<15;i++) generate("push",45,"strength").exercises.forEach(e=>{if(e.dropPct)d++;});
T("no drops in strong", d===0, d);
STATE.settings.goal="lean"; let mx=0; for(let i=0;i<15;i++){ const n=generate("push",45,"strength").exercises.filter(e=>e.dropPct).length; mx=Math.max(mx,n); }
T("max 1 drop elsewhere", mx<=1, mx);
// swap validity
const isBR=e=>{const r=e.requires||[];return r.includes("rack")&&r.includes("bench");};
const isCable=e=>{const s=e.stations||[];return s.includes("cable_high")||s.includes("cable_low");};
let bad=0,chk=0;
for(let i=0;i<8;i++){ for(const f of ["push","pull","legs_quad"]){ const w=generate(f,60,"circuit"); STATE.current=w;
  w.groups.filter(g=>g.items.length>1).forEach(g=>g.items.forEach(v=>{ const others=g.items.filter(x=>x!==v).map(ob);
    swapCandidates({id:v},w).forEach(c=>{ chk++; const stn=[]; others.forEach(o=>exStations(o).forEach(s=>stn.push(s)));
      if(stationConflict(stn,c.ex)) bad++;
      const grp=[...others,c.ex];
      if(grp.filter(e=>(e.requires||[]).includes("dumbbells")).length>1) bad++;
      if(others.some(isBR)&&isCable(c.ex)) bad++; }); })); } }
T("swap candidates valid in group context ("+chk+")", bad===0, bad);
// machine sessions parse
STATE.settings.hrMax=144; STATE.settings.hrRest=43;
let mism=0; for(const k of ["bike","ski","sledS","ruck"]) for(const dd of [30,45,60]) for(const hard of [false,true])
  buildMachineSession(k,dd,hard).forEach(c=>{ if(condRounds(c.proto)!==c.sets.length) mism++; });
T("machine protos = line counts", mism===0, mism);
// restore roundtrip
STATE.workouts=[{id:"rw",date:1,focus:"push",focusName:"P",exercises:[],conditioning:[],groups:[]}];
STATE.templates=[{id:"rt",name:"T",items:[]}]; STATE.bw=[{t:9,w:200}];
STATE.settings.exNotes={a:"n"}; STATE.settings.hrMax=144;
const bk=backupJSON();
STATE.workouts=[];STATE.templates=[];STATE.bw=[];STATE.settings.exNotes={};STATE.settings.hrMax=null;
importData(bk);
T("restore: workouts/templates/bw/notes/hr", STATE.workouts.length===1&&STATE.templates.length===1&&STATE.bw.length===1&&!!STATE.settings.exNotes.a&&STATE.settings.hrMax===144);
importData(bk); T("restore idempotent", STATE.workouts.length===1&&STATE.templates.length===1&&STATE.bw.length===1);
// XSS
STATE.settings.exNotes["bb_bench"]='<img src=x onerror=1>';
STATE.current=generate("push",45,"strength");
if(!STATE.current.exercises.some(e=>e.id==="bb_bench")){ STATE.current.exercises.unshift(buildLoggable(ob("bb_bench"))); STATE.current.groups.unshift({type:"straight",items:["bb_bench"]}); }
T("note escaped", !renderWorkout(STATE.current,true).includes('<img src=x')); delete STATE.settings.exNotes["bb_bench"];
// session length honesty (build → measure → trim/grow)
{ let worst=0,what="";
  for(const g of Object.keys(GOALS)){ STATE.settings.goal=g;
    for(const f of ["push","pull","legs_quad","legs_post","core"]) for(const dd of [30,45,60]) for(const md of ["strength","circuit"]){
      const w=generate(f,dd,md); const mins=estimateSessionSec(w)/60; const err=Math.abs(mins-dd)/dd;
      if(err>worst){ worst=err; what=g+"/"+f+"/"+dd+"/"+md+"="+Math.round(mins)+"min"; } } }
  STATE.settings.goal="general";
  T("no session overshoots its time budget", worst<=0.30, what+" ("+Math.round(worst*100)+"% off)"); }
// print sheet integrity
{ STATE.settings.goal="lean"; const w=generate("push",45,"circuit"); const p=buildPrint(w);
  const labels=[...p.matchAll(/<div class="gh">([^<]+)<\/div>/g)].map(m=>m[1]);
  const circ=labels.filter(l=>/^(Circuit|Superset) /.test(l)).map(l=>l.split(" ")[1]);
  T("print: group letters sequential", circ.every((c,i)=>c===String.fromCharCode(65+i)), circ.join(","));
  T("print: no unlabeled tables", !p.includes('<div class="p-g"><table'));
  T("print: footer has totals+estimate", /working sets · about \d+ min/.test(p)); }
// big regression
STATE.workouts=[];
const EXCL=new Set(["bench","barbell","rack","trapbar","landmine","cable_high","cable_low","dipstation","pullupbar","hangbar","ezbar","ghr","echobike","skierg","sled","battlerope"]);
let conflicts=0,runs=0,rerr=0,uneven=0,overComp=0;
for(const g of Object.keys(GOALS)){STATE.settings.goal=g; for(const f of Object.keys(FOCI)){for(const dd of [30,45,60]){for(const md of ["strength","circuit"]){
  const wk=generate(f,dd,md);runs++; try{renderWorkout(wk,true);}catch(e){rerr++;}
  const cap=dd>=45?2:1; if(wk.exercises.filter(e=>ob(e.id).role==="compound").length>cap) overComp++;
  wk.groups.filter(x=>x.items.length>1).forEach(x=>{const c=x.items.map(id=>{const e=wk.exercises.find(y=>y.id===id);return e?e.sets.filter(s=>!s.drop).length:0;});if(new Set(c).size>1)uneven++;});
  for(const x of wk.groups){const seen=new Set();for(const id of x.items){for(const s of exStations(ob(id))){if(EXCL.has(s)||s==="benchrack"){if(seen.has(s))conflicts++;seen.add(s);}}}}
}}}}
T("regression "+runs+" runs: conflicts", conflicts===0, conflicts);
T("regression: uneven groups", uneven===0, uneven);
T("regression: compound cap", overComp===0, overComp);
T("regression: render errors", rerr===0, rerr);
STATE.settings.goal="general";
["today","log","prog","gear","set"].forEach(t=>{STATE.tab=t;try{render();}catch(e){T("tab "+t,false,e.message);}});
console.log("\n"+pass+" passed · "+fail+" failed"+(fail?"  ← DO NOT SHIP":"  — clear to ship"));
process.exit(fail?1:0);
}
const combined=m.replace("\nboot();","\n//boot")+"\n("+__suite.toString()+")();";
require("vm").runInThisContext(combined,{filename:"app+suite.js"});
