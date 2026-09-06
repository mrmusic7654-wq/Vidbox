// ─── Scenes part 1: cold open → ch06 ────────────────────────────────────────
import { C, F, el, svgEl, setT, clamp, seg, EO, EIO, overshoot, LIN } from "./theme";
import { background, character, bank, moneyBag, coinStack, bill, phone, callout,
  revealPop, revealFade, hideAfter, stamp, slam, counter, chapterCard, newsCard,
  quoteCard, arrow, zigzagChart, shake } from "./components";

export type SceneCtx = { stage: HTMLElement; b: number[]; dur: number; meta?: any };
const at = (b:number[], i:number, dflt=99)=> (b[i]!==undefined? b[i] : dflt);

// ═══ COLD OPEN ═══
export function sceneCold(ctx: SceneCtx) {
  const { stage, b } = ctx;
  const night = el(stage,"",{position:"absolute",inset:"0",background:"linear-gradient(180deg,#132B44 0%,#274B6E 62%,#31577A 100%)",zIndex:"1"});
  const moon = el(stage,"",{position:"absolute",left:"1450px",top:"90px",width:"120px",height:"120px",borderRadius:"50%",background:"#F4EFE1",zIndex:"2"});
  const stars = el(stage,"",{position:"absolute",inset:"0",zIndex:"1"});
  for(let i=0;i<40;i++){ el(stars,"",{position:"absolute",left:`${(i*197)%1900}px`,top:`${(i*251)%400}px`,width:"4px",height:"4px",borderRadius:"50%",background:"#fff",opacity:String(0.25+((i*37)%60)/100)}); }
  // street
  el(stage,"",{position:"absolute",left:"0",top:"940px",width:"1920px",height:"140px",background:"#1B2F45",zIndex:"4"});
  for(let i=0;i<9;i++) el(stage,"",{position:"absolute",left:`${40+i*220}px`,top:"1006px",width:"120px",height:"12px",background:"#31506F",borderRadius:"3px",zIndex:"4"});
  // bank row sitting on the street (origin bottom-left: y+520 = ground line)
  const bk1 = bank(stage,{x:40,y:420,s:0.52,z:3});
  const bk2 = bank(stage,{x:660,y:420,s:0.52,z:3});
  const bk3 = bank(stage,{x:1280,y:420,s:0.52,z:3});
  const doorGlow = el(stage,"",{position:"absolute",left:"1408px",top:"824px",width:"62px",height:"116px",background:"#FFE9A8",zIndex:"4",borderRadius:"6px 6px 0 0",boxShadow:"0 0 44px 18px rgba(255,233,168,.45)"});
  // one billionaire sneaking toward the exit, bag in hand
  const bnk1 = character(stage,{x:1080,y:505,s:1.08,shirt:C.darkSuit,tie:C.red,walk:true,z:6});
  const bag1 = moneyBag(stage,{x:1145,y:838,s:0.95,z:7});
  // everyman at his phone under a lamppost, left side
  const lampPost = el(stage,"",{position:"absolute",left:"380px",top:"480px",width:"14px",height:"460px",background:"#22384E",zIndex:"5"});
  const lampHead = el(stage,"",{position:"absolute",left:"342px",top:"450px",width:"90px",height:"34px",background:"#22384E",borderRadius:"10px",zIndex:"5"});
  const lampGlow = el(stage,"",{position:"absolute",left:"270px",top:"450px",width:"230px",height:"230px",borderRadius:"50%",background:"radial-gradient(circle,#FFE9A8 0%,rgba(255,233,168,0) 66%)",zIndex:"5",opacity:"0.85"});
  const sleeper = character(stage,{x:130,y:505,s:1.02,hoodie:true,worry:true,z:6,pose:"phone"});
  const title = callout(stage,[
    {t:"WHAT \u201CALWAYS\u201D HAPPENS"},
    {t:"RIGHT BEFORE A", },
    {t:"BANK COLLAPSE",hl:true}],
    {x:960,y:46,size:116});
  const ex = stamp(stage,"EXIT",{x:1490,y:590,rot:-10,size:60});
  const refs = {night,moon,bk1,bk2,bk3,bnk1,bag1,sleeper,title,ex,doorGlow,lampGlow};
  return (t:number)=>{
    const fade = EO(seg(t,0,0.8));
    night.style.opacity = String(fade);
    for(const k of ["bk1","bk2","bk3"]) (refs as any)[k].root.style.opacity=String(fade);
    // billionaire sneaks toward the bank door; everyman stays put
    const sneak = seg(t, at(b,0)+0.5, at(b,3));
    const d1 = sneak*380;
    bnk1.seek(t,{x:d1});
    bag1.style.transform=`translateX(${d1}px) rotate(${Math.sin(t*6)*7}deg) scale(0.95)`;
    sleeper.seek(t);
    lampGlow.style.opacity = String((0.7+Math.sin(t*1.4)*0.15)*fade);
    // EXIT stamp at beat 8 ("The people at the top get out first.")
    slam(ex, t, at(b,8)+0.1);
    // title at final beat
    const ti = at(b,9)+0.3;
    revealPop(title, t, ti, 0.6);
    if(t>ti) { night.style.opacity = String(EO(seg(t,ti,ti+1.2))*0.92+0.08); }
    shake(stage, t, at(b,8)+0.1, 5);
  };
}

// ═══ CH01 — $500B hole / bond losses ═══
export function sceneCh01(ctx: SceneCtx) {
  const { stage, b } = ctx;
  const bk = bank(stage,{x:120,y:440,s:1.05,label:"YOUR BANK"});
  // cutaway vault panel
  const vault = el(stage,"",{position:"absolute",left:"880px",top:"120px",width:"820px",height:"620px",background:"#F7FBFD",borderRadius:"22px",boxShadow:"0 16px 0 rgba(0,0,0,.10)",zIndex:"8",opacity:"0"});
  el(vault,"",{position:"absolute",left:"0",top:"0",width:"100%",height:"64px",background:C.navy,borderRadius:"22px 22px 0 0"});
  const vt = el(vault,"",{position:"absolute",left:"36px",top:"14px",font:`400 34px ${F.display}`,color:C.white,letterSpacing:"2px"}); vt.textContent="INSIDE THE VAULT";
  const stacks: HTMLElement[] = [];
  for(let i=0;i<4;i++) for(let j=0;j<3;j++){
    const s = el(vault,"",{position:"absolute",left:`${70+i*190}px`,top:`${300-j*52}px`,width:"150px",height:"42px",background:C.green,borderRadius:"6px",
      boxShadow:`inset 0 -8px 0 ${C.greenDark}`,font:`400 22px ${F.label}`,color:C.white,display:"flex",alignItems:"center",justifyContent:"center"});
    s.textContent="1.5% BOND"; stacks.push(s);
  }
  const mag = svgEl(vault,`<svg width="240" height="240" viewBox="0 0 240 240"><circle cx="100" cy="100" r="70" fill="rgba(255,255,255,.25)" stroke="${C.navy}" stroke-width="16"/><rect x="150" y="150" width="26" height="80" rx="12" transform="rotate(45 163 163)" fill="${C.navy}"/></svg>`,
    {right:"40px",top:"70px"});
  const negTags: HTMLElement[] = stacks.slice(0,4).map((s,i)=>{
    const tag = el(vault,"",{position:"absolute",left:`${86+i*190}px`,top:`${236}px`,font:`400 26px ${F.display}`,color:C.red,zIndex:"3",opacity:"0"});
    tag.textContent = `-${18+i*4}%`;
    return tag;
  });
  const c700 = counter(stage,{x:960,y:760,size:120,align:"center"});
  c700.set("$0");
  const lbl700 = callout(stage,[{t:"IN PAPER LOSSES",hl:true}],{x:960,y:880,size:56});
  const idea = callout(stage,[{t:"SAFE STUFF."},{t:"AT LEAST, THAT\u2019S THE IDEA.",hl:true}],{x:960,y:180,size:60});
  const rot = stamp(stage,"-30%",{x:1460,y:560,rot:8,size:60,color:C.red});
  const fed = newsCard(stage,"FEDERAL RESERVE RAISES RATES TO FIGHT INFLATION",{x:120,y:860,src:"FEDERAL RESERVE",rot:-2});
  const cc = chapterCard(stage,1,"THE $500 BILLION HOLE HIDING IN EVERY BANK");
  return (t:number)=>{
    cc.seek(t, ctx.dur);
    revealPop(vault, t, at(b,3)); // "banks don't keep cash in vault"
    for(let i=0;i<stacks.length;i++){
      const k = EO(seg(t, at(b,4)+i*0.08, at(b,4)+0.4+i*0.08));
      stacks[i].style.opacity=String(k); stacks[i].style.transform=`translateY(${(1-k)*20}px)`;
    }
    revealFade(idea, t, at(b,7)); hideAfter(idea, t, at(b,9));
    revealPop(fed, t, at(b,8)); hideAfter(fed, t, at(b,10)+2.5);
    // bond prices fall: stacks tilt & drop + neg tags
    const fall = EO(seg(t, at(b,10), at(b,12)));
    stacks.forEach((s,i)=>{ s.style.transform=`translateY(${fall*26}px) rotate(${fall*(i%2? -4:4)}deg)`; s.style.background = fall>0.5? "#c9564b":C.green; s.style.boxShadow=`inset 0 -8px 0 ${fall>0.5? "#9c4038":C.greenDark}`; });
    negTags.forEach((tg,i)=>{ tg.style.opacity=String(EO(seg(t,at(b,11)+i*0.15,at(b,11)+0.4+i*0.15))); });
    slam(rot, t, at(b,12));
    // $700B counter
    const c0=at(b,13), c1=c0+2.2;
    if(t>=c0){ c700.root.style.opacity="1";
      const k=EO(seg(t,c0,c1)); c700.set("$"+Math.round(k*700).toString()+"B");
      const grow = EO(seg(t,c0,c1));
      c700.root.style.transform=`scale(${(0.8+0.25*overshoot(seg(t,c0,c1))).toFixed(3)})`;
    }
    revealFade(lbl700, t, at(b,14));
    shake(stage,t,at(b,13),7);
  };
}

// ═══ CH02 — office time bomb ═══
export function sceneCh02(ctx: SceneCtx) {
  const {stage,b}=ctx;
  // office tower
  const tower = el(stage,"",{position:"absolute",left:"150px",top:"170px",width:"480px",height:"760px",background:"#B9C7D1",borderRadius:"14px",zIndex:"4",boxShadow:"0 16px 0 rgba(0,0,0,.10)"});
  const wins: HTMLElement[]=[];
  for(let r=0;r<11;r++) for(let c=0;c<4;c++){
    wins.push(el(tower,"",{position:"absolute",left:`${36+c*108}px`,top:`${40+r*64}px`,width:"82px",height:"44px",
      borderRadius:"6px",background:"#DfEdF5", boxShadow:"inset 0 0 0 4px rgba(0,0,0,.06)"}));
  }
  // round bomb on the roof with lit fuse
  const bomb = el(stage,"",{position:"absolute",left:"330px",top:"84px",width:"120px",height:"120px",background:"#37424E",borderRadius:"50%",zIndex:"6",boxShadow:"inset -18px -14px 0 #2A333D"});
  el(bomb,"",{position:"absolute",left:"24px",top:"14px",width:"34px",height:"20px",background:"#4A5764",borderRadius:"8px",transform:"rotate(-24deg)"});
  const fuse = svgEl(stage,`<svg width="220" height="120" viewBox="0 0 220 120"><path d="M150 110 C 150 50, 190 60, 196 28" stroke="#8a6d3b" stroke-width="12" fill="none" stroke-linecap="round"/></svg>`,
    {left:"330px",top:"-24px",zIndex:"6"});
  const spark = el(stage,"",{position:"absolute",left:"516px",top:"0px",width:"24px",height:"24px",borderRadius:"50%",background:C.gold,boxShadow:`0 0 34px 16px ${C.yellow}`,zIndex:"7"});
  const cal = el(stage,"",{position:"absolute",left:"820px",top:"120px",width:"420px",height:"300px",background:C.white,borderRadius:"18px",boxShadow:"0 14px 0 rgba(0,0,0,.12)",zIndex:"9",opacity:"0"});
  el(cal,"",{position:"absolute",left:"0",top:"0",width:"100%",height:"84px",background:C.red,borderRadius:"18px 18px 0 0"});
  const calTop = el(cal,"",{position:"absolute",left:"0",top:"18px",width:"100%",textAlign:"center",font:`400 40px ${F.display}`,color:C.white});
  const calYear = el(cal,"",{position:"absolute",left:"0",top:"120px",width:"100%",textAlign:"center",font:`400 130px ${F.display}`,color:C.navy});
  const vac = counter(stage,{x:1360,y:420,size:120,align:"center"}); vac.set("20%");
  const vacLbl = callout(stage,[{t:"OFFICE VACANCY",hl:true},{t:"HIGHEST IN 40 YEARS"}],{x:1360,y:550,size:42});
  const tril = callout(stage,[{t:"$1.5 TRILLION DUE",hl:true},{t:"2024 \u2013 2026"}],{x:1330,y:180,size:74});
  const under = el(stage,"",{position:"absolute",left:"820px",top:"700px",width:"900px",height:"140px",zIndex:"9"});
  const barBg = el(under,"",{position:"absolute",inset:"0",background:C.white,borderRadius:"16px",boxShadow:"0 12px 0 rgba(0,0,0,.10)",overflow:"hidden"});
  const water = el(barBg,"",{position:"absolute",left:"0",top:"0",width:"100%",height:"100%",background:"#9CCFE8",opacity:"0"});
  const barFill = el(barBg,"",{position:"absolute",left:"0",top:"0",height:"100%",width:"0%",background:C.red});
  const barTxt = el(under,"",{position:"absolute",left:"30px",top:"50px",font:`400 44px ${F.display}`,color:C.white,zIndex:"2"}); barTxt.textContent="0%";
  const cap = el(under,"",{position:"absolute",left:"30px",top:"8px",font:`600 24px ${F.label}`,color:C.navy,letterSpacing:"2px"}); cap.textContent="OFFICE LOANS ALREADY UNDERWATER";
  const cc = chapterCard(stage,2,"THE EMPTY OFFICE TIME BOMB");
  const boom = stamp(stage,"BOOM",{x:1500,y:880,rot:-8,size:56,color:C.gold});
  return (t:number)=>{
    cc.seek(t,ctx.dur);
    // windows go dark progressively during WFH sentence
    const wfh = seg(t, at(b,3), at(b,5));
    wins.forEach((w,i)=>{ const thr = 0.25+((i*53)%100)/220; w.style.background = wfh>thr ? "#2A3B4C" : (i%3?"#DfEdF5":"#c4dbe8"); });
    // fuse burn: spark travels from tip into the bomb
    const fb = LIN(seg(t, at(b,6), at(b,9)));
    spark.style.left = `${516 - fb*72 + Math.sin(t*11)*4}px`;
    spark.style.top = `${4 + fb*82}px`;
    spark.style.opacity = fb<1? "1":"0";
    revealPop(cal, t, at(b,7));
    const yr = t<at(b,8)?"2024": t<at(b,8.8)?"2025":"2026";
    if(calYear.textContent!==yr) calYear.textContent=yr;
    calTop.textContent="MATURITY WALL";
    revealPop(tril, t, at(b,9));
    // vacancy counter
    if(t>=at(b,2)){ vac.root.style.opacity="1"; const k=EO(seg(t,at(b,2),at(b,2)+1.6)); vac.set(Math.round(k*20)+"%"); }
    revealFade(vacLbl,t,at(b,2)+0.5);
    // underwater bar 44%
    const u0=at(b,11);
    revealFade(under,t,u0);
    water.style.opacity=String(EO(seg(t,u0,u0+0.8))*0.5);
    const k44=EO(seg(t,u0+0.3,u0+1.8));
    barFill.style.width=`${44*k44}%`;
    barTxt.textContent=Math.round(44*k44)+"%";
    slam(boom,t,at(b,9)+0.4);
  };
}

// ═══ CH03 — 1,788 banks over the line ═══
export function sceneCh03(ctx: SceneCtx){
  const {stage,b}=ctx;
  const gauge = svgEl(stage,`<svg width="760" height="420" viewBox="0 0 760 420">
    <path d="M60 380 A 320 320 0 0 1 700 380" fill="none" stroke="${C.grayLight}" stroke-width="70" stroke-linecap="round"/>
    <path d="M60 380 A 320 320 0 0 1 700 380" fill="none" stroke="${C.red}" stroke-width="70" stroke-linecap="round" stroke-dasharray="580 1200" />
    <text x="96" y="330" font-family="Oswald" font-weight="600" font-size="30" fill="${C.navy}">SAFE</text>
    <text x="580" y="330" font-family="Oswald" font-weight="600" font-size="30" fill="${C.red}">DANGER</text>
    <text x="240" y="410" font-family="Anton" font-size="40" fill="${C.navy}">CRE LOANS / CAPITAL</text>
    <line class="needle" x1="380" y1="380" x2="180" y2="180" stroke="${C.ink}" stroke-width="16" stroke-linecap="round"/>
    <circle cx="380" cy="380" r="34" fill="${C.ink}"/></svg>`,
    {left:"80px",top:"100px",zIndex:"9"});
  const needle = gauge.querySelector(".needle") as SVGLineElement;
  needle.style.transformOrigin="380px 380px";
  const rows = el(stage,"",{position:"absolute",left:"900px",top:"90px",width:"900px",height:"520px",zIndex:"8"});
  const banks: HTMLElement[]=[];
  for(let i=0;i<15;i++){
    const col=i%5, rw=Math.floor(i/5);
    const bn = el(rows,"",{position:"absolute",left:`${col*170}px`,top:`${rw*170}px`,width:"140px",height:"150px",zIndex:"8",opacity:"0"});
    el(bn,"",{position:"absolute",left:"0",top:"30px",width:"140px",height:"110px",background:C.navy,borderRadius:"8px"});
    el(bn,"",{position:"absolute",left:"-8px",top:"0",width:"156px",height:"34px",background:"#2C4A73",clipPath:"polygon(50% 0,100% 100%,0 100%)"});
    el(bn,"",{position:"absolute",left:"52px",top:"66px",width:"36px",height:"74px",background:C.navyDark,borderRadius:"6px 6px 0 0"});
    banks.push(bn);
  }
  const c1788 = counter(stage,{x:1350,y:640,size:130,align:"center"});
  const flag = newsCard(stage,"FLAGSTAR: CRE EXPOSURE >500% OF EQUITY",{x:120,y:760,src:"FDIC FILINGS",rot:-2});
  const cc = chapterCard(stage,3,"1,788 BANKS ARE ALREADY OVER THE LINE");
  const red300 = callout(stage,[{t:"300% OF CAPITAL",hl:true}],{x:460,y:520,size:56});
  const five2 = el(stage,"",{position:"absolute",left:"900px",top:"640px",display:"flex",gap:"18px",zIndex:"9",opacity:"0"});
  for(let i=0;i<5;i++){ const p=el(five2,"",{width:"56px",height:"56px",borderRadius:"50%",background:C.grayLight,border:`5px solid ${C.navy}`});
    if(i<2) p.style.background=C.red; }
  return (t:number)=>{
    cc.seek(t,ctx.dur);
    // needle swing
    const nk = EIO(seg(t, at(b,1)+0.2, at(b,2)+0.5));
    needle.style.transform=`rotate(${nk*115}deg)`;
    revealFade(red300,t,at(b,1)+0.2); hideAfter(red300,t,at(b,5));
    // banks pop in
    for(let i=0;i<15;i++){ const k=EO(seg(t, at(b,4)+i*0.05, at(b,4)+0.35+i*0.05)); banks[i].style.opacity=String(k); banks[i].style.transform=`translateY(${(1-k)*30}px)`; if(i<6){ const red=EO(seg(t,at(b,5)+i*0.04,at(b,5)+0.3+i*0.04)); banks[i].style.filter = red>0? "":"";
      if(i<6) banks[i].querySelector("div")!.style.background = red>0.5? C.red : C.navy; } }
    // 1,788 counter
    const c0=at(b,5)+0.4;
    if(t>=c0){ c1788.root.style.opacity="1"; const k=EO(seg(t,c0,c0+2.4)); c1788.set(Math.round(k*1788).toLocaleString()); }
    revealFade(five2,t,at(b,7)); // 2 out of 5
    revealPop(flag,t,at(b,9)); hideAfter(flag,t,at(b,11)+3);
    shake(stage,t,at(b,5),6);
  };
}

// ═══ CH04 — extend & pretend ═══
export function sceneCh04(ctx: SceneCtx){
  const {stage,b}=ctx;
  const contract = el(stage,"",{position:"absolute",left:"140px",top:"170px",width:"640px",height:"760px",background:C.white,borderRadius:"16px",boxShadow:"0 16px 0 rgba(0,0,0,.12)",zIndex:"8",transform:"rotate(-2deg)"});
  el(contract,"",{position:"absolute",left:"40px",top:"50px",width:"560px",height:"18px",background:C.grayLight,borderRadius:"9px"});
  el(contract,"",{position:"absolute",left:"40px",top:"100px",width:"480px",height:"18px",background:C.grayLight,borderRadius:"9px"});
  el(contract,"",{position:"absolute",left:"40px",top:"150px",width:"540px",height:"18px",background:C.grayLight,borderRadius:"9px"});
  el(contract,"",{position:"absolute",left:"40px",top:"200px",width:"380px",height:"18px",background:C.grayLight,borderRadius:"9px"});
  const due = stamp(stage,"DUE",{x:240,y:300,rot:-8,size:84});
  const ext = stamp(stage,"EXTENDED",{x:210,y:560,rot:-6,size:84,color:"#2E7D3A"});
  const zombie = el(stage,"",{position:"absolute",left:"860px",top:"120px",width:"560px",height:"800px",zIndex:"7"});
  const ztower = el(zombie,"",{position:"absolute",left:"40px",top:"60px",width:"460px",height:"720px",background:"#A9BDA6",borderRadius:"14px",transformOrigin:"50% 100%",boxShadow:"0 16px 0 rgba(0,0,0,.10)"});
  for(let r=0;r<10;r++) for(let c=0;c<3;c++) el(ztower,"",{position:"absolute",left:`${44+c*136}px`,top:`${50+r*64}px`,width:"100px",height:"42px",borderRadius:"6px",background:"#42503F"});
  const zarm = el(zombie,"",{position:"absolute",left:"330px",top:"300px",width:"190px",height:"46px",background:"#8FA48C",borderRadius:"23px",transformOrigin:"10% 50%",transform:"rotate(20deg)",zIndex:"2"});
  const zchain = el(zombie,"",{position:"absolute",left:"240px",top:"760px",font:`400 34px ${F.display}`,color:C.white,background:C.red,padding:"10px 22px",borderRadius:"10px",zIndex:"3"});
  zchain.textContent="LOAN";
  const c9 = counter(stage,{x:1360,y:840,size:110,align:"center"});
  const c9l = callout(stage,[{t:"DELINQUENT AT BIG 6 BANKS",hl:true}],{x:1360,y:950,size:40});
  const reserve = el(stage,"",{position:"absolute",left:"860px",top:"560px",width:"560px",height:"0px",zIndex:"9"});
  const rlbl = callout(stage,[{t:"90\u00A2 IN RESERVE PER $1 LATE CRE DEBT",hl:true}],{x:1150,y:600,size:44});
  const cc = chapterCard(stage,4,"\u201cEXTEND AND PRETEND\u201d: THE ZOMBIE LOANS");
  return (t:number)=>{
    cc.seek(t,ctx.dur);
    slam(due,t,at(b,7)+0.3);
    const zk=EO(seg(t,at(b,8),at(b,10)));
    ext.style.opacity = String(EO(seg(t,at(b,8)+0.5,at(b,8)+0.9)));
    // zombie tower tilts
    const tilt = Math.sin(t*1.1)*2.2 - zk*3;
    ztower.style.transform=`rotate(${tilt}deg)`;
    ztower.style.filter=`saturate(${0.5+0.2*Math.sin(t*2)})`;
    zarm.style.transform=`rotate(${20+Math.sin(t*2.2)*14}deg)`;
    zchain.style.transform=`translateY(${Math.sin(t*2.2)*8}px)`;
    // delinquent counter to 9.3B
    const c0=at(b,12);
    if(t>=c0){ c9.root.style.opacity="1"; const k=EO(seg(t,c0,c0+1.8)); c9.set("$"+(k*9.3).toFixed(1)+"B"); }
    revealFade(c9l,t,c0+0.3);
    revealFade(rlbl,t,at(b,2));
    shake(stage,t,at(b,8)+0.5,4);
  };
}

// ═══ CH05 — Buffett signal ═══
export function sceneCh05(ctx: SceneCtx){
  const {stage,b}=ctx;
  const bankRow = el(stage,"",{position:"absolute",left:"0",top:"620px",width:"1920px",height:"0",zIndex:"3"});
  const bks:[ReturnType<typeof bank>,number][] = [];
  for(let i=0;i<5;i++){ bks.push([bank(bankRow,{x:60+i*370,y:0,s:0.5,z:3}), i]); }
  const buff = character(stage,{x:240,y:300,s:1.5,shirt:C.darkSuit,tie:C.red,hair:"#9aa3ab",walk:true,z:8});
  el((buff as any).root,"",{position:"absolute",left:"24px",top:"30px",width:"120px",height:"12px",background:"#C8CDD2",borderRadius:"6px"});
  const mountain = el(stage,"",{position:"absolute",left:"700px",top:"420px",width:"900px",height:"560px",zIndex:"4"});
  const coins: HTMLElement[]=[];
  for(let i=0;i<26;i++){
    const col=i%8, rw=Math.floor(i/8);
    const cS = coinStack(mountain,{x:40+col*108,y:520-rw*60,n:3+((i*7)%3),s:1.05});
    coins.push(cS);
  }
  const q = quoteCard(stage,"BE FEARFUL WHEN OTHERS ARE GREEDY, AND GREEDY WHEN OTHERS ARE FEARFUL.",{x:1080,y:110,w:700,rot:-2});
  const sold = counter(stage,{x:430,y:120,size:120,align:"center"});
  const soldL = callout(stage,[{t:"SOLD BY BERKSHIRE IN 2024",hl:true}],{x:430,y:240,size:44});
  const gold = el(stage,"",{position:"absolute",left:"900px",top:"840px",display:"flex",gap:"10px",zIndex:"9",opacity:"0"});
  for(let i=0;i<6;i++) el(gold,"",{width:"110px",height:"56px",background:C.gold,borderRadius:"8px",boxShadow:"inset 0 -10px 0 #C98F1B"});
  const mmf = callout(stage,[{t:"MONEY MARKET FUNDS: $7 TRILLION",hl:true}],{x:1330,y:900,size:40});
  const cc = chapterCard(stage,5,"THE BUFFETT SIGNAL");
  return (t:number)=>{
    cc.seek(t,ctx.dur);
    // walking away from banks; banks dim one by one
    const walkK = seg(t, 0.4, at(b,4));
    buff.seek(t,{x:0});
    (buff as any).root.style.left = `${240+walkK*430}px`;
    bks.forEach(([bk,i])=>{ const d=EO(seg(t,at(b,2)+i*0.5,at(b,2)+0.8+i*0.5)); bk.root.style.opacity=String(1-d*0.8); bk.root.style.transform=`scale(${0.5*(1-d*0.12)}) translateY(${d*14}px)`; });
    // coin mountain builds
    coins.forEach((c,i)=>{ const k=EO(seg(t, at(b,7)+i*0.06, at(b,7)+0.4+i*0.06)); c.style.opacity=String(k); c.style.transform=`scale(${0.8+0.25*k})`; });
    revealFade(q,t,at(b,12)); hideAfter(q,t,at(b,13)+2.4);
    const s0=at(b,8);
    if(t>=s0){ sold.root.style.opacity="1"; const k=EO(seg(t,s0,s0+2)); sold.set("$"+Math.round(k*143)+"B"); }
    revealFade(soldL,t,s0+0.4); hideAfter(sold.root,t,at(b,10)); hideAfter(soldL,t,at(b,10));
    revealFade(gold,t,at(b,13));
    revealFade(mmf,t,at(b,13)+0.6);
    shake(stage,t,at(b,7),5);
  };
}

// ═══ CH06 — the rich don't stand in line ═══
export function sceneCh06(ctx: SceneCtx){
  const {stage,b}=ctx;
  const split = el(stage,"",{position:"absolute",left:"0",top:"0",width:"1920px",height:"1080px",zIndex:"2"});
  const topH = el(split,"",{position:"absolute",left:"0",top:"0",width:"1920px",height:"540px",background:"#DCE9F0",boxShadow:"inset 0 -8px 0 rgba(0,0,0,.06)"});
  const botH = el(split,"",{position:"absolute",left:"0",top:"540px",width:"1920px",height:"540px",background:"#E7DFC9"});
  const lblTop = callout(stage,[{t:"YOU: INSURED",c:C.navy}],{x:280,y:40,size:54});
  const lblBot = callout(stage,[{t:"THEM: UNINSURED",c:C.red}],{x:300,y:580,size:54});
  // queue
  const q: ReturnType<typeof character>[]=[];
  for(let i=0;i<6;i++) q.push(character(stage,{x:120+i*180,y:300,s:0.72,hoodie:i%2===0,worry:true,z:6,shirt:["#5b6b7a","#6e5b7a","#5b7a6e"][i%3]}));
  const qBank = bank(stage,{x:1420,y:180,s:0.55,z:5});
  const clock = svgEl(stage,`<svg width="120" height="120" viewBox="0 0 120 120"><circle cx="60" cy="60" r="52" fill="${C.white}" stroke="${C.navy}" stroke-width="10"/><line class="hh" x1="60" y1="60" x2="60" y2="30" stroke="${C.ink}" stroke-width="9" stroke-linecap="round"/><line class="mh" x1="60" y1="60" x2="88" y2="60" stroke="${C.red}" stroke-width="7" stroke-linecap="round"/></svg>`,
    {left:"1660px",top:"40px",zIndex:"9"});
  const mh = clock.querySelector(".mh") as SVGLineElement; mh.style.transformOrigin="60px 60px";
  // bottom: billionaire + button + wires to capitol
  const rich = character(stage,{x:220,y:800,s:1.15,shirt:C.darkSuit,tie:C.red,smug:true,z:7});
  const button = el(stage,"",{position:"absolute",left:"640px",top:"820px",width:"170px",height:"110px",background:C.red,borderRadius:"16px",boxShadow:`0 10px 0 ${C.redDark}, inset 0 8px 0 rgba(255,255,255,.25)`,zIndex:"8"});
  const cap = svgEl(stage,`<svg width="360" height="240" viewBox="0 0 360 240"><rect x="30" y="120" width="300" height="100" fill="${C.white}"/><rect x="150" y="40" width="60" height="90" fill="${C.white}"/><path d="M150 40 L180 8 L210 40 Z" fill="${C.white}"/><rect x="40" y="150" width="16" height="70" fill="#d9e2e8"/><rect x="90" y="150" width="16" height="70" fill="#d9e2e8"/><rect x="140" y="150" width="16" height="70" fill="#d9e2e8"/><rect x="190" y="150" width="16" height="70" fill="#d9e2e8"/><rect x="240" y="150" width="16" height="70" fill="#d9e2e8"/><rect x="290" y="150" width="16" height="70" fill="#d9e2e8"/></svg>`,
    {left:"1420px",top:"700px",zIndex:"5"});
  const wire = svgEl(stage,`<svg width="1920" height="1080" viewBox="0 0 1920 1080"><path class="wp" d="M760 900 C 1000 860, 1100 800, 1400 820" stroke="${C.green}" stroke-width="10" fill="none" stroke-dasharray="14 10"/></svg>`,
    {left:"0",top:"0",zIndex:"6"});
  const wp = wire.querySelector(".wp") as SVGPathElement;
  const pct = callout(stage,[{t:"4 OF EVERY 10 DOLLARS",hl:true},{t:"IN BANKS ARE UNINSURED"}],{x:960,y:470,size:56,z:25});
  const speed = callout(stage,[{t:"THEM: SPEED OF LIGHT",c:"#fff"},{t:"YOU: SPEED OF A LUNCH BREAK",c:C.yellow}],{x:960,y:860,size:52,z:25});
  const cc = chapterCard(stage,6,"THE RICH DON\u2019T STAND IN LINE");
  return (t:number)=>{
    cc.seek(t,ctx.dur);
    q.forEach((c,i)=>{ c.seek(t); c.set({armL:-8-Math.sin(t*2+i)*4, armR:8+Math.sin(t*2+i)*4}); });
    mh.style.transform=`rotate(${t*160}deg)`;
    rich.seek(t); rich.set({armR:-30});
    const press = seg(t, at(b,6), at(b,6)+0.3);
    button.style.transform=`translateY(${press>0? 8:0}px) scale(${1-0.06*press})`;
    const lk=EO(seg(t,at(b,6)+0.2,at(b,6)+1.4));
    wp.style.strokeDashoffset=String(2600*(1-lk));
    revealFade(pct,t,at(b,2)); hideAfter(pct,t,at(b,5)+2);
    revealFade(speed,t,at(b,9)); hideAfter(speed,t,at(b,11)+2.5);
  };
}
