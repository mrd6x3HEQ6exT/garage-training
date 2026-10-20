#!/usr/bin/env node
/* Rack & Roll — coaching audit. Simulates the user's real weekly plan through the app's own
   generator, logs each session the way a person would, and checks every session against the
   coaching rules. Complements tests.js (unit checks) with "does the PROGRAM make sense".

   Run (from the folder containing index.html):
     node audit.js <backup.json>            report to stdout
     node audit.js <backup.json> --save     also write audit/<BUILD>.txt + audit/<BUILD>.json
     node audit.js <backup.json> --sample   also print one simulated week WITH weights (stdout only)
   Options: --weeks 52  --runs 3  --seed 1  --skip 0.1  --swap 0.1
   <backup.json> = the app's own backup export. It is read, never copied into the repo; saved
   reports hold rates and exercise ids only (no weights, dates or notes) because the repo is public.

   Simulated user (seeded, reproducible): follows the weekly planner exactly as the app resolves
   it (a slot whose focus no longer exists falls back to recommendFocus(), like the Today screen),
   skips --skip of sessions, swaps one exercise in --swap of strength sessions to the app's top
   swap suggestion, follows the "try N lb today" overload hint, and logs reps around the plan
   (-2..+3). Each run is a separate child process with its own seeded Math.random and clock.

   Check kinds:
     RULE  — confirmed by the user or enforced by design; any hit on the user's plan = exit 1.
     COACH — draft coaching checks; reported, never fail the run, until the coaching standard
             (queue.md) is agreed. Weekly volume is report-only for the same reason.
   Every check must fire on its own known-bad fixture (self-test) before its 0% is trusted —
   see mistake.md: zero-from-unproven-check. */
const ARGV=process.argv.slice(2);
const IS_CHILD=ARGV[0]==="--child";
const fs=require("fs");
const html=fs.readFileSync(__dirname+"/index.html","utf8"); global.__html=html;
// `i` flag: silences CodeQL js/bad-tag-filter. This is a local build tool extracting the
// app's own <script> block for testing, not a sanitizer on untrusted input — but the `i`
// flag is free (index.html only uses lowercase tags) and removes the false positive.
const m=[...html.matchAll(/<script>([\s\S]*?)<\/script>/gi)].map(x=>x[1]).sort((a,b)=>b.length-a.length)[0];
global.viewHTML="";
function realCL(){const s=new Set();return{add:(...x)=>x.forEach(v=>s.add(v)),remove:(...x)=>x.forEach(v=>s.delete(v)),toggle:()=>{},contains:x=>s.has(x)};}
function mkEl(){return new Proxy(function(){},{apply:()=>mkEl(),get:(t,p)=>{if(p==='innerHTML')return global.viewHTML;if(p==='value')return '';if(p==='classList')return realCL();if(p==='dataset')return{};if(p==='style')return{};if(p==='querySelector')return()=>mkEl();return(typeof p==='symbol')?undefined:mkEl();},set:(t,p,v)=>{if(p==='innerHTML'){global.viewHTML=v;}return true;}});}
const rt={classList:realCL(),addEventListener:()=>{}};const brQ={textContent:""};const br={classList:realCL(),addEventListener:()=>{},querySelector:()=>brQ};
const store={};const LS={getItem:k=>k in store?store[k]:null,setItem:(k,v)=>{store[k]=String(v);},removeItem:k=>{delete store[k];},clear:()=>{for(const k in store)delete store[k];}};
global.document={getElementById:id=>id==='rtimer'?rt:id==='bigrest'?br:mkEl(),querySelector:()=>mkEl(),querySelectorAll:()=>({forEach:()=>{}}),createElement:()=>mkEl(),addEventListener:()=>{},body:{appendChild:()=>{}},visibilityState:'visible'};
global.window={scrollTo:()=>{},localStorage:LS,addEventListener:()=>{},Audio:function(){this.play=()=>Promise.resolve()},AudioContext:function(){this.currentTime=0;this.resume=()=>{};this.createOscillator=()=>({frequency:{value:0,setValueAtTime:()=>{},exponentialRampToValueAtTime:()=>{}},detune:{value:0},type:'',connect:()=>{},start:()=>{},stop:()=>{}});this.createGain=()=>({connect:()=>{},gain:{setValueAtTime:()=>{},exponentialRampToValueAtTime:()=>{}}});this.createBiquadFilter=()=>({frequency:{value:0},Q:{value:0},type:'',connect:()=>{}});this.destination={};}};
global.localStorage=LS;global.indexedDB={open:()=>({})};global.prompt=()=>"x";global.confirm=()=>true;
Object.defineProperty(globalThis,'navigator',{value:{vibrate:()=>{},serviceWorker:{register:()=>({addEventListener:()=>{}}),addEventListener:()=>{}}},configurable:true});

/* ================= child: one seeded simulation run ================= */
async function __audit(){
  const A=global.__AUDIT_ARGS; const rnd=Math.random;
  const D=id=>EXERCISES.find(x=>x.id===id);
  const DROP_LOADS=["dumbbells","barbell","plates","kettlebell","cable_low","cable_high","ezbar","landmine","trapbar"];
  const LOADED=DROP_LOADS.concat(["resistancebands","hipbands"]);
  const musc=d=>new Set([...(d.primary||[]),...(d.secondary||[])]);
  const flat=w=>{ const o=[]; (w.groups||[]).forEach(g=>g.items.forEach(id=>o.push(id))); w.exercises.forEach(e=>{ if(!o.includes(e.id)) o.push(e.id); }); return o; };
  const CHECKS=[
    {id:"job_cap",kind:"RULE",label:"more than "+JOB_CAP+" of one job (rows / triceps iso / rear-delt+upper-back / glute iso)",
      fn:w=>{ const c={}; w.exercises.forEach(e=>{ const j=jobOf(e.id); if(j) c[j]=(c[j]||0)+1; }); const b=Object.keys(c).filter(j=>c[j]>JOB_CAP); return b.length?b.map(j=>j+"×"+c[j]).join(","):null; }},
    {id:"family_dup",kind:"RULE",label:"two exercises from one family",
      fn:w=>{ const ids=w.exercises.map(e=>e.id); for(let i=0;i<ids.length;i++) for(let j=i+1;j<ids.length;j++) if(simFamilyOf(ids[i]).includes(ids[j])) return ids[i]+"+"+ids[j]; return null; }},
    {id:"muscle_cap",kind:"RULE",label:"more than 4 exercises on one primary muscle",
      fn:(w,c)=>{ if(c.focus==="core") return null; const m={}; w.exercises.forEach(e=>{ const p=((D(e.id)||{}).primary||[])[0]; if(p&&p!=="core") m[p]=(m[p]||0)+1; }); const b=Object.keys(m).filter(k=>m[k]>4); return b.length?b.map(k=>k+"×"+m[k]).join(","):null; }},
    {id:"compound_cap",kind:"RULE",label:"more compound lifts than the session allows",
      fn:(w,c)=>{ if(c.focus==="core") return null; const n=w.exercises.filter(e=>(D(e.id)||{}).role==="compound").length; const cap=c.dur>=45?2:1; return n>cap?"compounds×"+n:null; }},
    {id:"core_fx",kind:"RULE",label:"too many core exercises doing the same job",
      fn:w=>{ const lim=w.exercises.length>=8?3:2; const c={}; w.exercises.forEach(e=>{ const fx=(D(e.id)||{}).cfx; if(fx) c[fx]=(c[fx]||0)+1; }); const b=Object.keys(c).filter(k=>c[k]>lim); return b.length?b.map(k=>k+"×"+c[k]).join(","):null; }},
    {id:"two_comp_group",kind:"RULE",label:"two compound lifts in one circuit",
      fn:w=>{ for(const g of (w.groups||[])) if(g.items.filter(id=>(D(id)||{}).role==="compound").length>1) return g.items.join("+"); return null; }},
    {id:"prefatigue",kind:"RULE",label:"exercise before a compound that uses its main muscle",
      fn:w=>{ const o=flat(w).map(D).filter(Boolean); for(let i=0;i<o.length;i++){ const x=o[i]; if(x.role!=="compound") continue; const m=musc(x); const y=o.slice(0,i).find(y=>y.role!=="compound"&&(y.primary||[]).some(p=>m.has(p))); if(y) return y.id+" before "+x.id; } return null; }},
    {id:"drop_nonload",kind:"RULE",label:"drop set on a bodyweight/timed exercise",
      fn:w=>{ const e=w.exercises.find(e=>{ const d=D(e.id)||{}; return e.sets.some(s=>s.drop)&&!((d.metric||"reps")==="reps"&&(d.requires||[]).some(r=>DROP_LOADS.includes(r))); }); return e?e.id:null; }},
    {id:"reps_range",kind:"RULE",label:"planned reps outside the scheme",
      fn:w=>{ for(const e of w.exercises){ const m=String(e.scheme||"").match(/(\d+)\s*[–-]\s*(\d+)(s?)/); if(!m||m[3]||e.metric==="time") continue; const s=e.sets.find(s=>!s.drop&&+s.reps>0&&(+s.reps<+m[1]||+s.reps>+m[2])); if(s) return e.id+" "+s.reps+" vs "+m[1]+"–"+m[2]; } return null; }},
    {id:"hinge_on_pull",kind:"RULE",label:"hinge on a Pull day",
      fn:(w,c)=>{ if(c.focus!=="pull") return null; const d=w.exercises.map(e=>D(e.id)).find(d=>d&&d.pattern==="hinge"); return d?d.id:null; }},
    {id:"lower_quads",kind:"RULE",label:"Lower day with fewer than 2 quad exercises",
      fn:(w,c)=>{ if(c.focus!=="lower") return null; const q=w.exercises.filter(e=>((D(e.id)||{}).primary||[]).includes("quads")).length; return q<2?"quads×"+q:null; }},
    {id:"banned",kind:"RULE",label:"banned exercise in the session",
      fn:(w,c)=>{ const e=w.exercises.find(e=>(c.blocked||[]).includes(e.id)); return e?e.id:null; }},
    {id:"injury_flag",kind:"RULE",label:"exercise flagged by your injury settings",
      fn:w=>{ const e=w.exercises.find(e=>{ const d=D(e.id); return d&&exInjuryHits(d).length; }); return e?e.id:null; }},
    {id:"repeat",kind:"RULE",label:"repeats a non-(+) exercise from the last session of this type",
      fn:(w,c)=>{ if(!c.prevIds) return null; const e=w.exercises.find(e=>c.prevIds.includes(e.id)&&(c.plus||{})[e.id]!==1); return e?e.id:null; }},
    {id:"musthave",kind:"COACH",label:"day missing its main movement (chest press / vertical pull + row / squat + hamstrings)",
      fn:(w,c)=>{ const d=w.exercises.map(e=>D(e.id)).filter(Boolean); const has=f=>d.some(f);
        if(c.focus==="push"&&!has(x=>x.pattern==="horiz_push"&&x.role==="compound")) return "no chest press";
        if(c.focus==="pull"&&!has(x=>x.pattern==="vert_pull")) return "no vertical pull";
        if(c.focus==="pull"&&!has(x=>jobOf(x.id)==="rows")) return "no row";
        if(c.focus==="lower"&&!has(x=>x.pattern==="squat")) return "no squat";
        if(c.focus==="lower"&&!has(x=>(x.primary||[]).includes("hams"))) return "no hamstring work";
        return null; }},
    {id:"same_muscle_circuit",kind:"COACH",label:"circuit (no compound) pairs two exercises on the same main muscle",
      fn:(w,c)=>{ if(c.focus==="core") return null; for(const g of (w.groups||[])){ const ds=g.items.map(D).filter(Boolean); if(ds.some(x=>x.role==="compound")) continue; const p={}; for(const x of ds){ const k=(x.primary||[])[0]; if(p[k]) return g.items.join("+"); p[k]=1; } } return null; }},
  ];

  // ---- self-test: each check must fire on its known-bad fixture, none on the known-good one ----
  const mk=(focus,dur,groups,over)=>{ const ids=[].concat(...groups); const w={focus,groups:groups.map(g=>({type:g.length>1?"circuit":"straight",items:g})),
      exercises:ids.map(id=>({id,scheme:"3 × 10–12",metric:"reps",sets:[{w:"20",reps:"10"},{w:"20",reps:"10"},{w:"20",reps:"10"}]}))};
    (over||[]).forEach(f=>f(w)); return w; };
  const ctx0=(focus,dur,more)=>Object.assign({focus,dur,prevIds:null,plus:{},blocked:[]},more||{});
  const antiext=EXERCISES.filter(e=>e.cfx==="antiext").slice(0,3).map(e=>e.id);
  const FIX={
    job_cap:[mk("pull",45,[["pulldown"],["bb_row"],["db_row"],["lm_row"]]),ctx0("pull",45)],
    family_dup:[mk("push",45,[["bb_bench"],["db_bench"]]),ctx0("push",45)],
    muscle_cap:[mk("pull",45,[["pulldown"],["bb_row"],["db_row"],["low_row"],["sa_pulldown"]]),ctx0("pull",45)],
    compound_cap:[mk("push",45,[["bb_bench"],["bb_ohp"],["dips"]]),ctx0("push",45)],
    core_fx:[mk("core",45,[antiext]),ctx0("core",45)],
    two_comp_group:[mk("push",45,[["bb_bench","bb_ohp"]]),ctx0("push",45)],
    prefatigue:[mk("push",45,[["skull"],["bb_bench"]]),ctx0("push",45)],
    drop_nonload:[mk("lower",60,[["superman"]],[w=>w.exercises[0].sets.push({w:"",reps:"",drop:true})]),ctx0("lower",60)],
    reps_range:[mk("pull",45,[["iyt_raise"]],[w=>{ w.exercises[0].scheme="3 × 15–20"; }]),ctx0("pull",45)],
    hinge_on_pull:[mk("pull",45,[["pulldown"],["deadlift"]]),ctx0("pull",45)],
    lower_quads:[mk("lower",60,[["bb_squat"],["bb_rdl"]]),ctx0("lower",60)],
    banned:[mk("push",45,[["bb_ohp"]]),ctx0("push",45,{blocked:["bb_ohp"]})],
    injury_flag:[mk("push",45,[["bb_ohp"]]),ctx0("push",45),"shoulder"],
    repeat:[mk("push",45,[["db_ohp"]]),ctx0("push",45,{prevIds:["db_ohp"]})],
    musthave:[mk("push",45,[["lat_raise"],["pushdown"]]),ctx0("push",45)],
    same_muscle_circuit:[mk("push",45,[["pushdown","skull"]]),ctx0("push",45)],
  };
  const selftest=[];
  const savedInj=STATE.settings.injuries;
  for(const c of CHECKS){ const f=FIX[c.id]; if(!f){ selftest.push(c.id+": no fixture"); continue; }
    if(f[2]) STATE.settings.injuries=[f[2]];
    let hit=null; try{ hit=c.fn(f[0],f[1]); }catch(e){ hit=null; selftest.push(c.id+": threw "+e.message); }
    STATE.settings.injuries=savedInj;
    if(!hit) selftest.push(c.id+": did NOT fire on its known-bad fixture"); }
  const good=mk("push",45,[["bb_bench"],["db_ohp"],["pushdown","lat_raise"]]); const gctx=ctx0("push",45);
  STATE.settings.injuries=[];
  for(const c of CHECKS){ let hit=null; try{ hit=c.fn(good,gctx); }catch(e){} if(hit) selftest.push(c.id+": fired on the known-good session ("+hit+")"); }
  STATE.settings.injuries=savedInj;

  // ---- simulate ----
  const plan=STATE.settings.plan||{}; const plus=Object.assign({},STATE.settings.recWeights||{});
  const blocked=[...(STATE.settings.blocked||[])];
  const DAY=864e5; const last=Math.max(...STATE.workouts.map(w=>w.date||0),Date.now());
  const day0=Math.floor(last/DAY+1)*DAY;
  const R={selftest,checks:CHECKS.map(c=>({id:c.id,kind:c.kind,label:c.label})),foci:{},planUse:{},weekly:{},weeks:A.weeks,progress:{},appear:{},sample:[],build:BUILD,skipped:0,firstUseBlank:0};
  const F=f=>R.foci[f]=R.foci[f]||{n:0,size:0,est:0,dur:{},swaps:0,hits:{},swapHits:{},ex:{},swapEx:{},distinct:{}};
  const firstSeen=new Set(STATE.workouts.flatMap(w=>(w.exercises||[]).map(e=>e.id)));
  for(let d=0; d<A.weeks*7; d++){
    const dow=new Date(day0+d*DAY).getDay(); const p=plan[dow]||{}; const wk=Math.floor(d/7);
    for(const slot of ["am","pm"]){ const pf=p[slot]; if(!pf) continue;
      global.__SIM_NOW=day0+d*DAY+(slot==="am"?6:18)*36e5;
      const f=FOCI[pf]?pf:recommendFocus();
      const pk=["Sun","Mon","Tue","Wed","Thu","Fri","Sat"][dow]+" "+slot+" "+pf; R.planUse[pk]=R.planUse[pk]||{}; R.planUse[pk][f]=(R.planUse[pk][f]||0)+1;
      if(rnd()<A.skip){ R.skipped++; continue; }
      const dur=resolvedDuration(f); const w=generate(f,dur,STATE.settings.mode||"strength");
      w.date=global.__SIM_NOW; w.id="sim"+w.date;
      if((w.exercises||[]).length){
        const r=F(f); r.n++; r.dur[dur]=(r.dur[dur]||0)+1; r.size+=w.exercises.length; r.est+=estimateSessionSec(w)/60;
        const prev=STATE.workouts.find(x=>!x.ref&&x.focus===f&&(x.exercises||[]).length);
        const ctx={focus:f,dur,prevIds:prev?prev.exercises.map(e=>e.id):null,plus,blocked};
        CHECKS.forEach(c=>{ const h=c.fn(w,ctx); if(h){ r.hits[c.id]=(r.hits[c.id]||0)+1; if(!r.ex[c.id]) r.ex[c.id]=h+"   ["+flat(w).join(",")+"]"; } });
        if(rnd()<A.swap){ const idx=Math.floor(rnd()*w.exercises.length); const old=w.exercises[idx]; const cands=swapCandidates({id:old.id},w);
          if(cands.length){ const ne=cands[0].ex; r.swaps++;
            w.groups.forEach(g=>{ const gi=g.items.indexOf(old.id); if(gi>=0) g.items[gi]=ne.id; });
            const nl=buildLoggable(ne); const ow=old.sets.filter(s=>!s.drop); nl.sets.forEach((s,i)=>{ const o=ow[i]||ow[ow.length-1]; if(o&&(s.reps===""||s.reps==null)) s.reps=o.reps; });
            w.exercises[idx]=nl; normalizeGroupSets(w);
            CHECKS.forEach(c=>{ const h=c.fn(w,ctx); if(h){ r.swapHits[c.id]=(r.swapHits[c.id]||0)+1; if(!r.swapEx[c.id]) r.swapEx[c.id]=old.id+"→"+ne.id+": "+h; } }); } }
        // the simulated user follows the overload hint, then logs the session
        w.exercises.forEach(e=>{ const hint=overloadHint(e); const m=hint&&/try (\d+(?:\.\d+)?) lb/.exec(hint.text); if(m) e.sets.forEach(s=>{ if(!s.drop) s.w=m[1]; });
          const d0=D(e.id)||{}; const loaded=(d0.requires||[]).some(x=>LOADED.includes(x));
          if(loaded&&!firstSeen.has(e.id)&&e.sets.every(s=>s.w===""||s.w==null)) R.firstUseBlank++;
          firstSeen.add(e.id);
          const top=parseTopReps(e.scheme)||10, lo=+((String(e.scheme).match(/(\d+)\s*[–-]/)||[])[1]||top);
          e.sets.forEach(s=>{ const planned=+s.reps>0?+s.reps:lo; s.reps=String(Math.max(1,planned+[-2,-1,0,0,1,1,2,3][Math.floor(rnd()*8)])); s.done=true; });
          const ws=e.sets.filter(s=>!s.drop).map(s=>+s.w).filter(x=>x>0); if(ws.length){ const pr=R.progress[e.id]=R.progress[e.id]||[]; pr.push(Math.max(...ws)); }
          R.appear[e.id]=(R.appear[e.id]||0)+1; r.distinct[e.id]=1;
          const W=R.weekly[wk]=R.weekly[wk]||{}; const sets=e.sets.filter(s=>!s.drop).length;
          (d0.primary||[]).forEach(m=>W[m]=(W[m]||0)+sets); (d0.secondary||[]).forEach(m=>W[m]=(W[m]||0)+0.5*sets); });
        if(A.sample&&wk===A.weeks-1) R.sample.push(exportSessionTxt(w,{}).split("\nSETTINGS CONTEXT")[0]);
      }
      STATE.workouts.unshift(w);
    }
  }
  const owned=ownedCaps(); R.never=EXERCISES.filter(e=>e.type==="strength"&&exAvailable(e,owned)&&!blocked.includes(e.id)&&!R.appear[e.id]).map(e=>e.id);
  Object.values(R.foci).forEach(r=>{ r.distinct=Object.keys(r.distinct).length; });
  const pg={up:0,flat:0,down:0,n:0}; for(const id in R.progress){ const a=R.progress[id]; if(a.length<4) continue; pg.n++; const q=Math.max(1,Math.floor(a.length/4)); const f0=a.slice(0,q).reduce((x,y)=>x+y,0)/q, f1=a.slice(-q).reduce((x,y)=>x+y,0)/q; if(f1>f0*1.02) pg.up++; else if(f1<f0*0.98) pg.down++; else pg.flat++; }
  R.progress=pg;
  console.log("__AUDIT_JSON__"+JSON.stringify(R));
}

/* ================= parent: run children, aggregate, report ================= */
function opt(name,def){ const i=ARGV.indexOf("--"+name); return i>=0&&ARGV[i+1]!=null&&!ARGV[i+1].startsWith("--")?ARGV[i+1]:def; }
function pct(a,b){ return b?Math.round(100*a/b):0; }
async function parentMain(){
  const backup=ARGV.find(a=>!a.startsWith("--")&&ARGV[ARGV.indexOf(a)-1]!=="--weeks"&&ARGV[ARGV.indexOf(a)-1]!=="--runs"&&ARGV[ARGV.indexOf(a)-1]!=="--seed"&&ARGV[ARGV.indexOf(a)-1]!=="--skip"&&ARGV[ARGV.indexOf(a)-1]!=="--swap");
  if(!backup||!fs.existsSync(backup)){ console.error("usage: node audit.js <backup.json> [--save] [--sample] [--weeks 52] [--runs 3] [--seed 1] [--skip 0.1] [--swap 0.1]"); process.exit(2); }
  const args={backup,weeks:+opt("weeks",52),runs:+opt("runs",3),seed:+opt("seed",1),skip:+opt("skip",0.1),swap:+opt("swap",0.1),sample:ARGV.includes("--sample")};
  const {spawn}=require("child_process");
  const one=i=>new Promise((res,rej)=>{ const c=spawn(process.execPath,[__filename,"--child",JSON.stringify({...args,seed:args.seed+i,sample:args.sample&&i===0})],{cwd:__dirname});
    let out="",err=""; c.stdout.on("data",d=>out+=d); c.stderr.on("data",d=>err+=d);
    c.on("close",code=>{ const line=out.split("\n").find(l=>l.startsWith("__AUDIT_JSON__")); if(!line) return rej(new Error("run "+i+" failed (exit "+code+"):\n"+err.slice(-2000)+out.slice(-1000))); res(JSON.parse(line.slice(14))); }); });
  const runs=await Promise.all([...Array(args.runs).keys()].map(one));
  const bk=JSON.parse(fs.readFileSync(backup,"utf8"));
  // aggregate
  const R0=runs[0]; const checks=R0.checks; const foci={};
  runs.forEach(R=>{ for(const f in R.foci){ const a=foci[f]=foci[f]||{n:0,size:0,est:0,dur:{},swaps:0,hits:{},swapHits:{},ex:{},swapEx:{},distinct:0};
    const r=R.foci[f]; a.n+=r.n; a.size+=r.size; a.est+=r.est; a.swaps+=r.swaps; a.distinct+=r.distinct/args.runs;
    for(const k in r.dur) a.dur[k]=(a.dur[k]||0)+r.dur[k];
    for(const k in r.hits){ a.hits[k]=(a.hits[k]||0)+r.hits[k]; a.ex[k]=a.ex[k]||r.ex[k]; }
    for(const k in r.swapHits){ a.swapHits[k]=(a.swapHits[k]||0)+r.swapHits[k]; a.swapEx[k]=a.swapEx[k]||r.swapEx[k]; } } });
  const planUse={}; runs.forEach(R=>{ for(const k in R.planUse) for(const f in R.planUse[k]){ planUse[k]=planUse[k]||{}; planUse[k][f]=(planUse[k][f]||0)+R.planUse[k][f]; } });
  const weekly={}; let nW=0; runs.forEach(R=>{ for(const w in R.weekly){ nW++; for(const m in R.weekly[w]) weekly[m]=(weekly[m]||[]).concat([R.weekly[w][m]]); } });
  const never=R0.never.filter(id=>runs.every(R=>R.never.includes(id)));
  const prog=runs.reduce((a,R)=>({up:a.up+R.progress.up,flat:a.flat+R.progress.flat,down:a.down+R.progress.down,n:a.n+R.progress.n}),{up:0,flat:0,down:0,n:0});
  const selftest=[...new Set([].concat(...runs.map(R=>R.selftest)))];
  // report
  const L=[]; const P=s=>L.push(s);
  P("RACK & ROLL COACHING AUDIT — build "+R0.build);
  P("data: your backup (app build "+(bk.build||"?")+", "+(bk.workouts||[]).length+" logged sessions) | "+args.runs+" runs × "+args.weeks+" weeks | seed "+args.seed+" | skip "+args.skip+" | swap "+args.swap);
  P("assumes: you train the planner's slots and accept the app's suggested focus; skipped/swapped sessions as above");
  P("self-test: "+(selftest.length?"FAILED\n  - "+selftest.join("\n  - "):"all "+checks.length+" checks fire on their known-bad fixture and stay quiet on the known-good one"));
  P("\nWEEKLY PLAN AS THE APP RESOLVES IT");
  Object.keys(planUse).sort().forEach(k=>{ const t=Object.values(planUse[k]).reduce((x,y)=>x+y,0); const s=Object.entries(planUse[k]).sort((a,b)=>b[1]-a[1]).map(([f,n])=>f+" "+pct(n,t)+"%").join(", "); const pf=k.split(" ")[2];
    P("  "+k.padEnd(18)+"→ "+s+(pf&&!s.startsWith(pf+" 100%")?"   ← planner value '"+pf+"' is not a current focus; app falls back to its own pick":"")); });
  let ruleFail=0;
  P("\nSESSION CHECKS  (% of sessions; RULE = confirmed, COACH = draft, report-only)");
  Object.keys(foci).sort().forEach(f=>{ const a=foci[f]; const durs=Object.keys(a.dur).map(k=>k+" min").join("/");
    P("\n  "+f.toUpperCase()+"  sessions="+a.n+"  duration "+durs+"  avg size "+(a.size/a.n).toFixed(1)+"  raw estimate "+(a.est/a.n).toFixed(0)+" min  distinct exercises/run "+Math.round(a.distinct));
    let any=false; checks.forEach(c=>{ const h=a.hits[c.id]||0; if(!h) return; any=true; if(c.kind==="RULE") ruleFail+=h;
      P("    "+String(pct(h,a.n)).padStart(3)+"%  "+c.kind.padEnd(5)+" "+c.label+"\n           e.g. "+a.ex[c.id]); });
    if(!any) P("      0%  no check fired");
    const sw=Object.keys(a.swapHits); if(a.swaps) P("    after "+a.swaps+" simulated swaps: "+(sw.length?sw.map(k=>k+" "+pct(a.swapHits[k],a.swaps)+"% (e.g. "+a.swapEx[k]+")").join("; "):"no check fired"));
  });
  P("\nWEEKLY VOLUME  (hard sets/week: primary = 1, secondary = 0.5; REPORT-ONLY until the coaching standard sets targets)");
  const nWeeks=args.weeks*args.runs;
  Object.keys(weekly).sort((a,b)=>weekly[b].reduce((x,y)=>x+y,0)-weekly[a].reduce((x,y)=>x+y,0)).forEach(m=>{ const arr=weekly[m]; const avg=arr.reduce((x,y)=>x+y,0)/nWeeks; const s=[...arr].sort((x,y)=>x-y);
    P("  "+m.padEnd(10)+avg.toFixed(1).padStart(5)+"   (busiest weeks ~"+(s[Math.floor(s.length*0.9)]||0).toFixed(0)+", weeks with none: "+pct(nWeeks-arr.length,nWeeks)+"%)"); });
  const sum=ms=>ms.reduce((t,m)=>t+(weekly[m]||[]).reduce((x,y)=>x+y,0),0)/nWeeks;
  P("  push:pull  "+(sum(["chest","shoulders","triceps"])/Math.max(1,sum(["back","biceps"]))).toFixed(2)+"   quads:hams  "+(sum(["quads"])/Math.max(1,sum(["hams"]))).toFixed(2));
  P("\nOTHER");
  P("  never generated (available, not banned): "+(never.length?never.join(", "):"none"));
  P("  load progression (exercises done 4+ times, first vs last quarter): up "+pct(prog.up,prog.n)+"%, flat "+pct(prog.flat,prog.n)+"%, down "+pct(prog.down,prog.n)+"%");
  P("  first-time loaded exercises with no planned weight (you type it once): "+Math.round(runs.reduce((a,R)=>a+R.firstUseBlank,0)/args.runs)+" per run");
  P("  skipped sessions (simulated): "+Math.round(runs.reduce((a,R)=>a+R.skipped,0)/args.runs)+" per run");
  // compare with the previous saved report
  const dir=__dirname+"/audit"; let prevJ=null;
  if(fs.existsSync(dir)){ const fsx=fs.readdirSync(dir).filter(x=>/^v\d+\.json$/.test(x)&&x!==R0.build+".json").sort((a,b)=>+b.slice(1,-5)-+a.slice(1,-5)); if(fsx.length) prevJ=JSON.parse(fs.readFileSync(dir+"/"+fsx[0],"utf8")); }
  const rates={}; for(const f in foci){ rates[f]={}; checks.forEach(c=>{ rates[f][c.id]=pct(foci[f].hits[c.id]||0,foci[f].n); }); }
  if(prevJ){ P("\nCHANGE vs "+prevJ.build+" (percentage points)"); let any=false;
    for(const f in rates) for(const c in rates[f]){ const a=(prevJ.rates[f]||{})[c]; if(a==null) continue; if(a!==rates[f][c]){ any=true; P("  "+f.padEnd(6)+c.padEnd(20)+a+"% → "+rates[f][c]+"%"); } }
    if(!any) P("  no check rate changed"); }
  P("\nRESULT: "+(selftest.length?"FAIL (self-test)":ruleFail?"FAIL — a RULE check fired on your plan":"PASS — no RULE check fired on your plan"));
  const text=L.join("\n"); console.log(text);
  if(args.sample&&R0.sample.length) console.log("\n================ SAMPLE WEEK (last simulated week, run 1 — not saved) ================\n"+R0.sample.join("\n"));
  if(ARGV.includes("--save")){ if(!fs.existsSync(dir)) fs.mkdirSync(dir); fs.writeFileSync(dir+"/"+R0.build+".txt",text+"\n");
    fs.writeFileSync(dir+"/"+R0.build+".json",JSON.stringify({build:R0.build,backupBuild:bk.build||null,args:{weeks:args.weeks,runs:args.runs,seed:args.seed,skip:args.skip,swap:args.swap},rates},null,1)+"\n");
    console.log("\nsaved audit/"+R0.build+".txt and audit/"+R0.build+".json"); }
  process.exit(selftest.length||ruleFail?1:0);
}

if(IS_CHILD){
  const a=JSON.parse(ARGV[1]); global.__AUDIT_ARGS=a;
  let s=a.seed>>>0||1; Math.random=function(){ s|=0; s=s+0x6D2B79F5|0; let t=Math.imul(s^s>>>15,1|s); t=t+Math.imul(t^t>>>7,61|t)^t; return ((t^t>>>14)>>>0)/4294967296; };
  const bk=JSON.parse(fs.readFileSync(a.backup,"utf8"));
  LS.setItem("gym_settings",JSON.stringify(bk.settings||{})); LS.setItem("gym_equipment",JSON.stringify(bk.equipment||[])); LS.setItem("gym_workouts",JSON.stringify(bk.workouts||[]));
  const realNow=Date.now; Date.now=()=>global.__SIM_NOW||realNow();
  const combined=m.replace("\nboot();","\n//boot")+"\n(async()=>{ await boot(); await ("+__audit.toString()+")(); })().catch(e=>{ console.error(e&&e.stack||e); process.exit(3); });";
  require("vm").runInThisContext(combined,{filename:"app+audit.js"});
} else {
  parentMain().catch(e=>{ console.error(e.stack||e); process.exit(2); });
}
