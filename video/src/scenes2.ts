// ─── Scenes part 2: ch07 → endcard ──────────────────────────────────────────
import { C, F, el, svgEl, setT, clamp, seg, EO, EIO, overshoot, LIN } from "./theme";
import { character, bank, moneyBag, coinStack, bill, phone, callout,
  revealPop, revealFade, hideAfter, stamp, slam, counter, chapterCard, newsCard,
  quoteCard, arrow, zigzagChart, shake } from "./components";

export type SceneCtx = { stage: HTMLElement; b: number[]; dur: number; meta?: any };
const at = (b:number[], i:number, dflt=99)=> (b[i]!==undefined? b[i] : dflt);

// ═══ CH07 — $42B in 24 hours ═══
export function sceneCh07(ctx: SceneCtx){
  const {stage,b}=ctx;
  const dusk = el(stage,"",{position:"absolute",inset:"0",background:"linear-gradient(180deg,#7FA6C4 0%,#A9C4D8 55%,#C9DCE8 100%)",zIndex:"1"});
  // city grid
  const grid = el(stage,"",{position:"absolute",inset:"0",zIndex:"2"});
  for(let i=0;i<7;i++) el(grid,"",{position:"absolute",left:"0",top:`${140+i*140}px`,width:"1920px",height:"16px",background:"#8FA9BC",opacity:"0.5"});
  for(let i=0;i<13;i++) el(grid,"",{position:"absolute",left:`${40+i*150}px`,top:"0",width:"16px",height:"1080px",background:"#8FA9BC",opacity:"0.5"});
  const vb = bank(stage,{x:640,y:220,s:1.0,label:"VALLEY BANK",z:5});
  const phones: ReturnType<typeof phone>[] = [];
  for(let i=0;i<16;i++){
    const col=i%8, rw=Math.floor(i/8);
    phones.push(phone(stage,{x:120+col*115,y:(rw===0?120:640)+((i*61)%90),s:0.9,z:6,screen:""}));
  }
  const bills: HTMLElement[] = [];
  for(let i=0;i<26;i++) bills.push(bill(stage,{x:300+((i*167)%1300),y:180+((i*97)%240),s:0.9+((i*13)%30)/100,rot:(i*47)%360}));
  const odo = counter(stage,{x:960,y:880,size:150,align:"center"});
  const per = callout(stage,[{t:"$486,000 EVERY SECOND",hl:true}],{x:960,y:1000,size:56});
  const lock = stamp(stage,"LOCKED",{x:1350,y:330,rot:-9,size:66});
  const wamu = newsCard(stage,"2008: WASHINGTON MUTUAL LOSES $16.7B OVER 10 DAYS",{x:120,y:120,w:520,src:"SEPIA FILES",rot:-3});
  const vs = callout(stage,[{t:"SVB: 2.5\u00D7 MORE",c:"#fff"},{t:"IN ONE AFTERNOON",hl:true}],{x:430,y:560,size:60,z:26});
  const cc = chapterCard(stage,7,"42 BILLION DOLLARS IN 24 HOURS");
  return (t:number)=>{
    cc.seek(t,ctx.dur);
    const run0 = at(b,1);
    // phones light up in accelerating waves
    phones.forEach((p,i)=>{
      const thr = LIN(seg(t, run0, at(b,3))) ;
      const on = ((i*37)%100)/100 < Math.pow(thr,0.7);
      (p.screen as HTMLElement).style.background = on? "#FFE08A" : "#BFE8FF";
      p.root.style.transform = `scale(${0.9 + (on?0.08:0)}) translateY(${on? -6:0}px)`;
    });
    // bills fly out after beat 2
    const fly = seg(t, at(b,2), at(b,2)+3);
    bills.forEach((bl,i)=>{
      const k = clamp(fly - ((i*13)%20)/30);
      bl.style.opacity = String(k>0&&fly<1.15? 1:0);
      bl.style.transform = `translate(${((i*53)%40)*3*k}px, ${-k*320 - ((i*29)%100)}px) rotate(${(i*47)%360 + t*60}deg)`;
    });
    // odometer to 42,000,000,000
    const o0=at(b,2)+0.3;
    if(t>=o0){
      odo.root.style.opacity="1";
      const k=EO(seg(t,o0,o0+2.6));
      odo.set("$"+Math.round(k*42e9).toLocaleString("en-US"));
    }
    revealFade(per,t,at(b,3)+0.2);
    slam(lock,t,at(b,5)+0.4);
    revealPop(wamu,t,at(b,7)); hideAfter(wamu,t,at(b,9)+3.2);
    revealFade(vs,t,at(b,9)); hideAfter(vs,t,at(b,9)+3.4);
    shake(stage,t,at(b,2)+0.4,8);
  };
}

// ═══ CH08 — 167-year-old bank ═══
export function sceneCh08(ctx: SceneCtx){
  const {stage,b}=ctx;
  const cs = bank(stage,{x:180,y:440,s:1.05,label:"CREDIT SUISSE"});
  const plaque = el(stage,"",{position:"absolute",left:"330px",top:"770px",width:"240px",height:"80px",background:"#D9CFA8",borderRadius:"8px",display:"flex",alignItems:"center",justifyContent:"center",font:`400 44px ${F.display}`,color:"#6b5d33",zIndex:"6",boxShadow:"0 8px 0 rgba(0,0,0,.15)"});
  plaque.textContent="EST. 1856";
  const clock = svgEl(stage,`<svg width="420" height="420" viewBox="0 0 420 420">
    <circle cx="210" cy="210" r="180" fill="${C.white}" stroke="${C.navy}" stroke-width="18"/>
    <path class="ringArrow" d="M210 60 A 150 150 0 1 1 120 90" stroke="${C.red}" stroke-width="26" fill="none" stroke-linecap="round"/>
    <path d="M120 90 l 66 -14 l -30 62 z" fill="${C.red}"/>
    <text x="140" y="235" font-family="Anton" font-size="64" fill="${C.navy}">7 DAYS</text></svg>`,
    {left:"1240px",top:"90px",zIndex:"9",opacity:"0"});
  const ubs = bank(stage,{x:1290,y:480,s:0.85,label:"UBS",z:5});
  const caseE = el(stage,"",{position:"absolute",left:"1210px",top:"700px",width:"120px",height:"86px",background:"#8a6d3b",borderRadius:"8px",zIndex:"7",opacity:"0"});
  el(caseE,"",{position:"absolute",left:"0",top:"0",width:"120px",height:"30px",background:"#6b5427",borderRadius:"8px 8px 0 0"});
  el(caseE,"",{position:"absolute",left:"52px",top:"26px",width:"16px",height:"16px",background:C.gold,borderRadius:"50%"});
  const wiped = counter(stage,{x:560,y:880,size:120,align:"center"});
  const wipedL = callout(stage,[{t:"OF AT1 BONDS WIPED OUT",hl:true}],{x:560,y:1000,size:44});
  const gone = callout(stage,[{t:"FROM \u201cWE\u2019RE FINE\u201d",c:"#fff"},{t:"TO GONE IN 7 DAYS",hl:true}],{x:1440,y:880,size:56,z:26});
  const cc = chapterCard(stage,8,"THE 167-YEAR-OLD BANK THAT DIED IN A WEEK");
  return (t:number)=>{
    cc.seek(t,ctx.dur);
    // crack the facade
    const ck = EO(seg(t, at(b,4), at(b,6)));
    const path = (cs.crackPath as SVGPathElement);
    const len = 500; path.style.strokeDasharray=String(len); path.style.strokeDashoffset=String(len*(1-ck));
    cs.root.style.transform = `scale(${1.05 - ck*0.05}) rotate(${ck*1.2}deg)`;
    plaque.style.transform = `rotate(${ck*14}deg) translateY(${ck*10}px)`;
    revealPop(clock as unknown as HTMLElement, t, at(b,6));
    const spin = seg(t, at(b,6), at(b,8));
    (clock.querySelector(".ringArrow") as SVGPathElement).style.filter = spin<1? "":"none";
    // UBS briefcase handoff
    revealFade(caseE, t, at(b,9));
    caseE.style.transform = `translateX(${EO(seg(t,at(b,9),at(b,9)+1.6))*-140}px) rotate(${Math.sin(t*3)*4}deg)`;
    // $17B counter
    const w0=at(b,10);
    if(t>=w0){ wiped.root.style.opacity="1"; const k=EO(seg(t,w0,w0+2)); wiped.set("$"+Math.round(k*17)+"B"); }
    revealFade(wipedL,t,w0+0.3);
    revealFade(gone,t,at(b,8)+0.4); hideAfter(gone,t,at(b,10)+3);
    shake(stage,t,at(b,4)+0.6,6);
  };
}

// ═══ CH09 — secret list ═══
export function sceneCh09(ctx: SceneCtx){
  const {stage,b}=ctx;
  const dark = el(stage,"",{position:"absolute",inset:"0",background:"radial-gradient(circle at 50% 40%, #33506E 0%, #1A2C40 70%)",zIndex:"1"});
  const skyline = el(stage,"",{position:"absolute",left:"0",top:"600px",width:"1920px",height:"0",zIndex:"2",opacity:"0.8"});
  const shadowBanks: HTMLElement[]=[];
  for(let i=0;i<12;i++){
    const bn = el(skyline,"",{position:"absolute",left:`${40+i*158}px`,top:"0",width:"130px",height:"260px",background:"#22364C",borderRadius:"6px 6px 0 0"});
    el(bn,"",{position:"absolute",left:"-6px",top:"-24px",width:"142px",height:"28px",background:"#2C4258",clipPath:"polygon(50% 0,100% 100%,0 100%)"});
    shadowBanks.push(bn);
  }
  const vault = el(stage,"",{position:"absolute",left:"560px",top:"140px",width:"800px",height:"640px",background:"#24384E",borderRadius:"24px",zIndex:"6",boxShadow:`inset 0 0 0 14px #1B2B3D, 0 18px 0 rgba(0,0,0,.25)`});
  const door = el(vault,"",{position:"absolute",left:"60px",top:"60px",width:"680px",height:"520px",background:"#2E465E",borderRadius:"18px",transformOrigin:"0% 50%",boxShadow:"inset 0 0 0 10px #22364A"});
  const handle = el(door,"",{position:"absolute",left:"520px",top:"220px",width:"110px",height:"110px",borderRadius:"50%",border:"16px solid #6E869C",zIndex:"2"});
  el(handle,"",{position:"absolute",left:"38px",top:"38px",width:"34px",height:"34px",background:"#6E869C",borderRadius:"6px"});
  const folder = el(vault,"",{position:"absolute",left:"250px",top:"200px",width:"300px",height:"220px",background:C.white,borderRadius:"10px",zIndex:"1",opacity:"0",display:"flex",alignItems:"center",justifyContent:"center",textAlign:"center",font:`400 34px ${F.display}`,color:C.red,padding:"16px",lineHeight:"1.2"});
  folder.textContent="PROBLEM BANK LIST \u2014 CONFIDENTIAL";
  const c60 = counter(stage,{x:960,y:830,size:130,align:"center"});
  const c60l = callout(stage,[{t:"BANKS ON THE SECRET LIST",hl:true}],{x:960,y:950,size:44});
  const sealed = stamp(stage,"SEALED BY LAW",{x:1050,y:560,rot:-10,size:56});
  const hist = svgEl(stage,`<svg width="820" height="360" viewBox="0 0 820 360">
    ${[80,150,220,320,420,560,700,820].map((h,i)=>`<rect x="${60+i*94}" y="${330-h}" width="64" height="${h}" fill="${i<3? '#55708C':'#3E5C7A'}" rx="8"/>`).join("")}
    <text x="60" y="352" font-family="Oswald" font-weight="600" font-size="26" fill="#9FB6C8">2008 CRISIS: LIST PEAKED NEAR 900 BANKS</text></svg>`,
    {left:"120px",top:"640px",zIndex:"8",opacity:"0"});
  const failed = callout(stage,[{t:"570 BANKS FAILED SINCE 2001",hl:true}],{x:1380,y:720,size:46});
  const cc = chapterCard(stage,9,"THE SECRET LIST OF DYING BANKS");
  return (t:number)=>{
    cc.seek(t,ctx.dur);
    // vault opens
    const ok = EIO(seg(t, at(b,3), at(b,4)+0.8));
    door.style.transform=`perspective(1200px) rotateY(${-ok*105}deg)`;
    folder.style.opacity=String(EO(seg(t,at(b,4)+0.6,at(b,4)+1.1)));
    shadowBanks.forEach((sb,i)=>{ sb.style.opacity=String(0.5+0.5*Math.abs(Math.sin(i*1.7+t*0.5))); });
    const h0=at(b,6);
    if(t>=h0){ c60.root.style.opacity="1"; const k=EO(seg(t,h0,h0+1.6)); c60.set(String(Math.round(k*60))); }
    revealFade(c60l,t,h0+0.3);
    slam(sealed,t,at(b,7)+0.4);
    revealPop(hist as unknown as HTMLElement,t,at(b,9));
    revealFade(failed,t,at(b,11));
    shake(stage,t,at(b,4)+0.7,7);
  };
}

// ═══ CH10 — FDIC promise ═══
export function sceneCh10(ctx: SceneCtx){
  const {stage,b}=ctx;
  // umbrella
  const um = svgEl(stage,`<svg width="900" height="420" viewBox="0 0 900 420">
    <path d="M20 300 A 430 430 0 0 1 880 300 L 20 300 Z" fill="${C.white}" stroke="#D7E3EA" stroke-width="8"/>
    <path d="M20 300 A 430 430 0 0 1 880 300" fill="none" stroke="${C.red}" stroke-width="12" stroke-dasharray="60 46"/>
    <rect x="430" y="300" width="24" height="120" rx="10" fill="#B9C7D1"/>
    <path d="M454 420 q 40 26 0 52" stroke="#B9C7D1" stroke-width="16" fill="none"/>
    <rect x="330" y="150" width="240" height="70" rx="12" fill="${C.navy}" transform="rotate(-4 450 185)"/>
    <text x="360" y="196" font-family="Anton" font-size="38" fill="${C.white}" transform="rotate(-4 450 185)">FDIC $250,000</text>
    </svg>`, {left:"480px",top:"40px",zIndex:"9"});
  // piggy houses
  const hood = el(stage,"",{position:"absolute",left:"360px",top:"620px",width:"1200px",height:"300px",zIndex:"5"});
  for(let i=0;i<6;i++){
    const h = el(hood,"",{position:"absolute",left:`${i*200}px`,top:`${(i%2)*40}px`,width:"160px",height:"180px"});
    el(h,"",{position:"absolute",left:"0",top:"40px",width:"160px",height:"140px",background:C.green,borderRadius:"14px",boxShadow:`inset -14px -10px 0 ${C.greenDark}`});
    el(h,"",{position:"absolute",left:"-12px",top:"0",width:"184px",height:"56px",background:"#2E7D3A",clipPath:"polygon(50% 0,100% 100%,0 100%)"});
    el(h,"",{position:"absolute",left:"56px",top:"70px",width:"10px",height:"10px",borderRadius:"50%",background:C.ink,opacity:"0.7"});
  }
  // red rain through holes
  const rain: HTMLElement[] = [];
  for(let i=0;i<22;i++){ const r = bill(stage,{x:520+((i*149)%860),y:430,s:0.7,rot:(i*61)%360}); r.style.opacity="0"; rain.push(r); }
  const bars = svgEl(stage,`<svg width="760" height="420" viewBox="0 0 760 420">
    <rect x="40" y="${330-60}" width="240" height="60" rx="10" fill="${C.gold}"/>
    <rect x="380" y="${330-330}" width="240" height="330" rx="10" fill="${C.navy}"/>
    <text x="40" y="370" font-family="Oswald" font-weight="600" font-size="28" fill="${C.navy}">FUND: $120B</text>
    <text x="380" y="370" font-family="Oswald" font-weight="600" font-size="28" fill="${C.navy}">DEPOSITS: $10T+</text></svg>`,
    {left:"1060px",top:"560px",zIndex:"9",opacity:"0"});
  const penny = callout(stage,[{t:"A PENNY ON THE DOLLAR",hl:true}],{x:1330,y:500,size:66});
  const poll = callout(stage,[{t:"HALF OF AMERICANS DON\u2019T KNOW THE LIMIT",hl:true}],{x:960,y:1000,size:44});
  const cc = chapterCard(stage,10,"YOUR $250,000 PROMISE");
  return (t:number)=>{
    cc.seek(t,ctx.dur);
    revealFade(um as unknown as HTMLElement,t,0.5);
    const r0=at(b,4);
    rain.forEach((r,i)=>{ const cyc=((t*0.9 + i*0.37)%1.6);
      r.style.opacity=String(clamp(1-Math.abs(cyc-0.8)*2)*EO(seg(t,r0,r0+1)));
      r.style.transform=`translateY(${cyc*330}px) rotate(${r.getStringAngle??0}deg)`;
    });
    revealFade(bars as unknown as HTMLElement,t,at(b,7));
    revealFade(penny,t,at(b,8)); hideAfter(penny,t,at(b,9)+2.6);
    revealFade(poll,t,at(b,10));
  };
}

// ═══ CH11 — too big to fail ═══
export function sceneCh11(ctx: SceneCtx){
  const {stage,b}=ctx;
  const big = bank(stage,{x:660,y:320,s:1.35,label:"MEGA BANK"});
  const smalls: {root:HTMLElement; x:number; eaten:boolean}[] = [];
  const labels=["FIRST REPUBLIC","SIGNATURE","COMMUNITY BANK"];
  for(let i=0;i<3;i++){
    const bx = i===0? 120 : i===1? 1480 : 1550;
    const root = bank(stage,{x:bx,y:i===2? 740: 700,s:0.5,label:labels[i],z:6-i}).root;
    smalls.push({root,x:bx,eaten:false});
  }
  const gulp = (e:HTMLElement,k:number,tx:number,ty:number)=>{ e.style.transform=`translate(${k*tx}px,${k*ty}px) scale(${1-0.55*k}) rotate(${k*8}deg)`; e.style.opacity=String(1-k*0.9); };
  const ccard = newsCard(stage,"MIDNIGHT DEAL: FIRST REPUBLIC SOLD TO JPMORGAN",{x:120,y:120,w:560,src:"MARCH 2023",rot:-2});
  const weak = callout(stage,[{t:"THE WEAK FAIL.",c:"#fff"},{t:"THE STRONG EAT.",hl:true}],{x:960,y:920,size:66});
  const chat = el(stage,"",{position:"absolute",left:"1560px",top:"840px",width:"260px",height:"120px",background:C.white,borderRadius:"22px",zIndex:"8",opacity:"0",display:"flex",alignItems:"center",justifyContent:"center",font:`600 22px ${F.label}`,color:C.navy});
  chat.textContent="\u201CHow can I help? \u2014 BOT\u201D";
  const cc = chapterCard(stage,11,"TOO BIG TO FAIL, TOO SMALL TO SAVE");
  return (t:number)=>{
    cc.seek(t,ctx.dur);
    // three gulps
    const g1=EO(seg(t,at(b,3),at(b,3)+1.1)), g2=EO(seg(t,at(b,6),at(b,6)+1.1)), g3=EO(seg(t,at(b,8),at(b,8)+1.1));
    gulp(smalls[0].root,g1,780,-260); gulp(smalls[1].root,g2,-640,-260); gulp(smalls[2].root,g3,-700,-320);
    if(g1>0.2||g2>0.2||g3>0.2) big.root.style.transform=`scale(${1.35+0.03*(g1+g2+g3)}) translateY(${-6*(g1+g2+g3)}px)`;
    revealPop(ccard,t,at(b,4)); hideAfter(ccard,t,at(b,5)+3);
    revealFade(weak,t,at(b,7)); hideAfter(weak,t,at(b,9)+2.4);
    revealFade(chat,t,at(b,10));
    shake(stage,t,at(b,3)+0.7,9); shake(stage,t,at(b,6)+0.7,9); shake(stage,t,at(b,8)+0.7,9);
  };
}

// ═══ CH12 — treadmill trap ═══
export function sceneCh12(ctx: SceneCtx){
  const {stage,b}=ctx;
  const tb = bank(stage,{x:700,y:220,s:1.0});
  // conveyor belt base
  const belt = el(stage,"",{position:"absolute",left:"600px",top:"780px",width:"820px",height:"56px",background:C.ink,borderRadius:"28px",zIndex:"6",overflow:"hidden"});
  const beltStrip = el(belt,"",{position:"absolute",left:"0",top:"14px",width:"200%",height:"28px",
    background:`repeating-linear-gradient(90deg,#39434e 0 60px,#242c35 60px 120px)`});
  const runner = character(stage,{x:380,y:420,s:1.25,hoodie:true,worry:true,z:8});
  // autopay chains
  const chain: string[] = ["\u2191 SALARY","\u2261 MORTGAGE","\u25CF GYM","\u2615 COFFEE","\u25A0 TUITION"];
  const links: HTMLElement[] = chain.map((txt,i)=>{
    const L = el(stage,"",{position:"absolute",left:`${240+i*36}px`,top:`${640+i*54}px`,background:C.white,border:`5px solid ${C.navy}`,borderRadius:"14px",
      padding:"8px 16px",font:`600 24px ${F.label}`,color:C.navy,zIndex:"9"});
    L.textContent=txt; return L;
  });
  // billionaire floats away on money bag
  const rich = character(stage,{x:1450,y:300,s:1.05,shirt:C.darkSuit,tie:C.red,smug:true,z:8});
  const balloon = el(stage,"",{position:"absolute",left:"1520px",top:"60px",width:"180px",height:"220px",background:C.red,borderRadius:"50% 50% 46% 46%",zIndex:"7",boxShadow:`inset -20px -16px 0 ${C.redDark}`});
  const rope = el(stage,"",{position:"absolute",left:"1602px",top:"270px",width:"8px",height:"130px",background:"#8a6d3b",zIndex:"7"});
  const bagF = moneyBag(stage,{x:1530,y:400,s:1.4,z:8});
  const days = callout(stage,[{t:"YOU: DAYS",c:C.red},{t:"THEM: MILLISECONDS",hl:true}],{x:300,y:130,size:60,z:25});
  const three = ["THE RUN","THE FREEZE","THE RESCUE"].map((txt,i)=>{
    const c=el(stage,"",{position:"absolute",left:`${180+i*560}px`,top:"900px",background:C.navy,color:C.white,borderRadius:"14px",
      padding:"18px 40px",font:`400 44px ${F.display}`,letterSpacing:"2px",zIndex:"12",opacity:"0"});
    c.textContent=`${i+1}. ${txt}`; return c;
  });
  const cc = chapterCard(stage,12,"HOW YOUR MONEY GETS TRAPPED");
  return (t:number)=>{
    cc.seek(t,ctx.dur);
    // running in place + belt moving
    beltStrip.style.transform=`translateX(${-(t*260)%120}px)`;
    runner.seek(t,{y:-Math.abs(Math.sin(t*8))*14});
    runner.set({armL:-70+Math.sin(t*8)*25,armR:70-Math.sin(t*8)*25});
    links.forEach((L,i)=>{ L.style.transform=`rotate(${Math.sin(t*2+i)*4}deg)`; });
    // balloon drifts up-right slowly
    const drift = seg(t, at(b,4), ctx.dur);
    const dx=drift*260, dy=-drift*130;
    rich.seek(t,{x:dx,y:dy}); balloon.style.transform=`translate(${dx}px,${dy}px) rotate(${Math.sin(t*1.5)*4}deg)`;
    rope.style.transform=`translate(${dx}px,${dy}px)`;
    bagF.style.transform=`translate(${dx}px,${dy}px) rotate(${Math.sin(t*1.5)*-5}deg) scale(1.4)`;
    revealFade(days,t,at(b,8)); hideAfter(days,t,at(b,9)+2.6);
    three.forEach((c,i)=>{ revealPop(c,t,at(b,10)+i*1.1,0.5); });
    shake(stage,t,at(b,10),3);
  };
}

// ═══ CH13 — why nobody warns you ═══
export function sceneCh13(ctx: SceneCtx){
  const {stage,b}=ctx;
  // money loop diagram
  const loop = svgEl(stage,`<svg width="1100" height="900" viewBox="0 0 1100 900">
    <g class="ring">
      <circle cx="550" cy="450" r="330" fill="none" stroke="${C.green}" stroke-width="26" stroke-dasharray="40 26" stroke-linecap="round"/>
    </g>
    <polygon class="tri1" points="550,80 600,150 500,150" fill="${C.green}"/>
    <polygon class="tri2" points="880,620 820,660 900,700" fill="${C.green}"/>
    <polygon class="tri3" points="220,620 200,700 280,660" fill="${C.green}"/>
  </svg>`, {left:"120px",top:"90px",zIndex:"4"});
  const ring = loop.querySelector(".ring") as SVGGElement;
  // nodes on the loop
  const bankN = bank(stage,{x:420,y:180,s:0.62,z:6});
  const tv = svgEl(stage,`<svg width="300" height="220" viewBox="0 0 300 220"><rect x="10" y="10" width="280" height="180" rx="20" fill="${C.navy}"/><rect x="34" y="34" width="232" height="132" rx="10" fill="#7FD4FF"/><path d="M110 70 L200 100 L110 130 Z" fill="${C.red}"/></svg>`,
    {left:"1010px",top:"240px",zIndex:"6"});
  const crowd = svgEl(stage,`<svg width="340" height="220" viewBox="0 0 340 220">
    ${[0,1,2,3,4,5].map(i=>`<circle cx="${40+i*52}" cy="${90+(i%2)*24}" r="24" fill="${C.hoodie}"/><circle cx="${40+i*52}" cy="${52+(i%2)*24}" r="16" fill="${C.skin}"/>`).join("")}
  </svg>`,{left:"1000px",top:"600px",zIndex:"6"});
  const ftm = callout(stage,[{t:"FOLLOW THE MONEY",hl:true}],{x:960,y:60,size:96,z:25});
  const strong = newsCard(stage,"\u201CTHE BANKING SYSTEM REMAINS STRONG\u201D",{x:1240,y:420,w:520,src:"FINANCIAL TV",rot:2});
  const trap = callout(stage,[{t:"A STRUCTURAL TRAP",c:"#fff"},{t:"BAKED INTO THE MACHINE",hl:true}],{x:960,y:850,size:64,z:25});
  const final3 = [["THE WARNING SIGNS ARE PUBLIC","#fff"],["THE LIST IS SECRET",C.yellow],["THE EXITS ARE FILLING UP",C.red]].map(([txt,col],i)=>{
    const c=callout(stage,[{t:txt as string,c:col as string}],{x:960,y:250+i*120,size:64,z:26});
    return c;
  });
  const endman = character(stage,{x:840,y:460,s:1.5,hoodie:true,z:9});
  const endBtn = el(stage,"",{position:"absolute",left:"800px",top:"900px",background:C.red,color:C.white,borderRadius:"16px",padding:"20px 54px",font:`400 46px ${F.display}`,letterSpacing:"2px",zIndex:"30",opacity:"0",boxShadow:`0 10px 0 ${C.redDark}`});
  endBtn.textContent="ASK YOUR BANK A QUESTION OR TWO";
  const cc = chapterCard(stage,13,"WHY NOBODY WARNS YOU");
  return (t:number)=>{
    cc.seek(t,ctx.dur);
    ring.style.transformOrigin="550px 450px";
    ring.style.transform=`rotate(${t*24}deg)`;
    revealFade(ftm,t,at(b,0)+0.2); hideAfter(ftm,t,at(b,2));
    revealFade(strong,t,at(b,3)); hideAfter(strong,t,at(b,6));
    // loop snaps at beat 7
    const snap=EO(seg(t,at(b,7),at(b,7)+0.4));
    if(snap>0){ loop.style.transform=`rotate(${-snap*2}deg)`; ring.style.opacity=String(1-snap*0.25); }
    revealFade(trap,t,at(b,10)); hideAfter(trap,t,at(b,13)+2);
    // final three lines
    final3.forEach((f,i)=>{ revealPop(f,t,at(b,13)+0.4+i*1.0,0.5); });
    final3.forEach((f,i)=>{ hideAfter(f,t,at(b,16)+i*0.1); });
    // everyman looks at camera + button
    endman.seek(t,{y: seg(t,at(b,16),at(b,16)+1)*-60});
    endman.set({armL:-14,armR:14});
    revealPop(endBtn,t,at(b,17)+0.3,0.5);
    shake(stage,t,at(b,7),7);
  };
}

// ═══ END CARD ═══
export function sceneEnd(ctx: SceneCtx){
  const {stage}=ctx;
  const bg2 = el(stage,"",{position:"absolute",inset:"0",background:C.navy,zIndex:"1",opacity:"0"});
  const title = callout(stage,[{t:"THE FIRST PAGE OF THE SCRIPT",c:"#fff"},{t:"IS ALREADY BEHIND US.",hl:true}],{x:960,y:140,size:84,z:20});
  const card1 = el(stage,"",{position:"absolute",left:"330px",top:"520px",width:"520px",height:"300px",background:C.red,borderRadius:"18px",zIndex:"12",opacity:"0",display:"flex",alignItems:"flex-end",padding:"20px",font:`400 40px ${F.display}`,color:C.white,lineHeight:"1.1",boxShadow:"0 14px 0 rgba(0,0,0,.25)"});
  card1.innerHTML="<span>THIS IS WHAT \u201CALWAYS\u201D HAPPENS BEFORE A MARKET CRASH</span>";
  const arr = svgEl(card1,`<svg width="240" height="240" viewBox="0 0 240 240"><g fill="${C.white}"><rect x="92" y="10" width="56" height="140" rx="8"/><path d="M120 230 L60 140 L180 140 Z"/></g></svg>`,{left:"250px",top:"10px"});
  const card2 = el(stage,"",{position:"absolute",left:"1070px",top:"520px",width:"520px",height:"300px",background:C.gold,borderRadius:"18px",zIndex:"12",opacity:"0",display:"flex",alignItems:"flex-end",padding:"20px",font:`400 40px ${F.display}`,color:C.navy,lineHeight:"1.1",boxShadow:"0 14px 0 rgba(0,0,0,.25)"});
  card2.innerHTML="<span>HOW WARREN BUFFETT MADE $85 BILLION</span>";
  const coin = svgEl(card2,`<svg width="200" height="200" viewBox="0 0 200 200"><circle cx="100" cy="90" r="70" fill="#C98F1B"/><circle cx="100" cy="90" r="52" fill="#F2B134"/><text x="100" y="112" text-anchor="middle" font-family="Anton" font-size="64" fill="#8a5f00">$</text></svg>`,{left:"280px",top:"6px"});
  const sub = el(stage,"",{position:"absolute",left:"760px",top:"900px",background:C.red,color:C.white,borderRadius:"16px",padding:"20px 60px",font:`400 52px ${F.display}`,letterSpacing:"2px",zIndex:"20",opacity:"0",boxShadow:`0 12px 0 ${C.redDark}`});
  sub.textContent="\u25B6 SUBSCRIBE";
  return (t:number)=>{
    bg2.style.opacity=String(EO(seg(t,0,0.8)));
    revealPop(title,t,0.5,0.6);
    revealPop(card1,t,1.2,0.5);
    revealPop(card2,t,1.5,0.5);
    revealPop(sub,t,2.0,0.5);
  };
}
