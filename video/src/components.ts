// ─── Reusable flat-vector visual vocabulary (DOM/CSS) ───────────────────────
import { C, F, el, svgEl, setT, T, clamp, seg, EO, overshoot } from "./theme";

export type R = Record<string, any>;

// ── background: pale blue + halftone + slow drifting blobs ──────────────────
export function background(stage: HTMLElement) {
  const b = el(stage, "bg", { position: "absolute", inset: "0" });
  b.innerHTML = `
    <div style="position:absolute;inset:0;background:${C.bg}"></div>
    <div class="halftone" style="position:absolute;inset:0;background-image:radial-gradient(${C.bgDot} 1.6px, transparent 1.7px);background-size:26px 26px;opacity:.55"></div>
    <div class="blobA" style="position:absolute;width:900px;height:900px;border-radius:50%;background:${C.grayLight};opacity:.35;filter:blur(2px);left:-250px;top:-300px"></div>
    <div class="blobB" style="position:absolute;width:1100px;height:1100px;border-radius:50%;background:#dcebf2;opacity:.5;left:55%;top:45%"></div>`;
  return {
    seek(t: number) {
      (b.querySelector(".blobA") as HTMLElement).style.transform = `translate(${Math.sin(t*.11)*40}px,${Math.cos(t*.13)*30}px)`;
      (b.querySelector(".blobB") as HTMLElement).style.transform = `translate(${Math.cos(t*.07)*55}px,${Math.sin(t*.09)*45}px)`;
    }
  };
}

// ── flat person character ───────────────────────────────────────────────────
export interface CharOpts { x:number;y:number;s:number;shirt?:string;skin?:string;hair?:string;
  hoodie?:boolean;tie?:string;walk?:boolean;worry?:boolean;smug?:boolean;flip?:boolean;z?:number;pose?:string }
export function character(parent:HTMLElement, o:CharOpts) {
  const root = el(parent,"",{position:"absolute",left:`${o.x}px`,top:`${o.y}px`,
    width:"300px",height:"420px",transformOrigin:"50% 100%",
    transform:`scale(${o.s}) ${o.flip?"scaleX(-1)":""}`, zIndex:String(o.z??5)});
  const shirt = o.shirt ?? C.darkSuit, skin = o.skin ?? C.skin, hair = o.hair ?? "#3A2E25";
  // legs
  const legL = el(root,"",{position:"absolute",left:"128px",top:"300px",width:"20px",height:"110px",background:C.navyDark,borderRadius:"8px"});
  const legR = el(root,"",{position:"absolute",left:"158px",top:"300px",width:"20px",height:"110px",background:C.navyDark,borderRadius:"8px"});
  // body
  const body = el(root,"",{position:"absolute",left:"96px",top:"172px",width:"114px",height:"150px",
    background: o.hoodie?C.hoodie:shirt, borderRadius:"26px 26px 18px 18px"});
  if (o.tie) el(body,"",{position:"absolute",left:"49px",top:"4px",width:"18px",height:"64px",background:o.tie,
    clipPath:"polygon(35% 0,65% 0,100% 78%,50% 100%,0 78%)"});
  if (o.hoodie) { el(body,"",{position:"absolute",left:"30px",top:"-12px",width:"54px",height:"26px",background:"#68737f",borderRadius:"12px"});
    el(body,"",{position:"absolute",left:"40px",top:"16px",width:"34px",height:"60px",background:"#8d99a5",borderRadius:"8px"}); }
  // arms
  const armL = el(root,"",{position:"absolute",left:"78px",top:"186px",width:"22px",height:"104px",background:o.hoodie?C.hoodie:shirt,borderRadius:"11px",transformOrigin:"50% 8px",transform:"rotate(8deg)"});
  const armR = el(root,"",{position:"absolute",left:"206px",top:"186px",width:"22px",height:"104px",background:o.hoodie?C.hoodie:shirt,borderRadius:"11px",transformOrigin:"50% 8px",transform:"rotate(-8deg)"});
  // head
  const head = el(root,"",{position:"absolute",left:"110px",top:"66px",width:"86px",height:"96px",background:skin,borderRadius:"42px"});
  el(head,"",{position:"absolute",left:"-4px",top:"-8px",width:"94px",height:"52px",background:hair,borderRadius:"46px 46px 12px 12px"});
  const eyeL = el(head,"",{position:"absolute",left:"22px",top:"48px",width:"7px",height:"7px",borderRadius:"50%",background:C.ink});
  const eyeR = el(head,"",{position:"absolute",left:"56px",top:"48px",width:"7px",height:"7px",borderRadius:"50%",background:C.ink});
  const browL = el(head,"",{position:"absolute",left:"18px",top:"36px",width:"16px",height:"4px",borderRadius:"2px",background:C.ink,transform:"rotate(0deg)"});
  const browR = el(head,"",{position:"absolute",left:"52px",top:"36px",width:"16px",height:"4px",borderRadius:"2px",background:C.ink,transform:"rotate(0deg)"});
  if (o.smug){ browL.style.transform="rotate(-14deg)"; browR.style.transform="rotate(-14deg)"; }
  if (o.worry){ browL.style.transform="rotate(18deg)"; browR.style.transform="rotate(-18deg)"; browL.style.left="16px"; browR.style.left="54px"; }
  let handPhone: HTMLElement|null = null;
  if (o.pose === "phone") {
    armL.style.transform = "rotate(34deg)"; armR.style.transform = "rotate(-34deg)";
    handPhone = el(root, "", { position:"absolute", left:"128px", top:"268px", width:"46px", height:"76px",
      background:C.ink, borderRadius:"9px", zIndex:"3" });
    el(handPhone, "", { position:"absolute", left:"4px", top:"7px", width:"38px", height:"58px", borderRadius:"5px", background:"#BFE8FF" });
  }
  return {
    root, armL, armR, browL, browR,
    set(p:R){ // pose updates: {armL,armR (deg), headTilt, jump}
      if(p.armL!=null) armL.style.transform=`rotate(${p.armL}deg)`;
      if(p.armR!=null) armR.style.transform=`rotate(${p.armR}deg)`;
      if(p.headTilt!=null) head.style.transform=`rotate(${p.headTilt}deg)`;
    },
    seek(t:number, base={x:0,y:0}){
      if(o.walk){ const b=Math.sin(t*7.2)*10; legL.style.transform=`rotate(${-b*0.8}deg)`; legR.style.transform=`rotate(${b*0.8}deg)`;
        root.style.left = `${o.x + base.x + t*0}px`; root.style.transform=`translateY(${-Math.abs(Math.sin(t*7.2))*8}px) scale(${o.s}) ${o.flip?"scaleX(-1)":""}`; }
      else root.style.transform=`translateY(${base.y||0}px) scale(${o.s}) ${o.flip?"scaleX(-1)":""}`;
    }
  };
}

// ── bank building ───────────────────────────────────────────────────────────
export function bank(parent:HTMLElement, o:{x:number;y:number;s:number;label?:string;z?:number}) {
  const root = el(parent,"",{position:"absolute",left:`${o.x}px`,top:`${o.y}px`,width:"620px",height:"520px",
    transformOrigin:"0% 100%",transform:`scale(${o.s})`,zIndex:String(o.z??4)});
  el(root,"",{position:"absolute",left:"0",top:"470px",width:"620px",height:"50px",background:C.navyDark,borderRadius:"6px"});
  el(root,"",{position:"absolute",left:"10px",top:"86px",width:"600px",height:"390px",background:C.navy,borderRadius:"8px"});
  el(root,"",{position:"absolute",left:"-12px",top:"26px",width:"644px",height:"66px",background:"#2C4A73",clipPath:"polygon(50% 0,100% 100%,0 100%)",borderRadius:"4px"});
  const cols = [40,152,264,376,488].map(cx=> el(root,"",{position:"absolute",left:`${cx}px`,top:"112px",width:"92px",height:"348px",background:C.white,borderRadius:"10px",boxShadow:"inset -14px 0 0 rgba(0,0,0,.08)"}));
  el(root,"",{position:"absolute",left:"248px",top:"240px",width:"124px",height:"220px",background:C.navyDark,borderRadius:"10px 10px 0 0"});
  let lbl=null;
  if(o.label){ lbl = el(root,"",{position:"absolute",left:"50%",top:"140px",transform:"translateX(-50%)",
    font:`400 34px ${F.display}`,color:C.white,letterSpacing:"2px",whiteSpace:"nowrap",textShadow:"0 3px 0 rgba(0,0,0,.25)"}); lbl.textContent=o.label; }
  const crack = svgEl(root, `<svg width="620" height="520" viewBox="0 0 620 520"><path d="M310 100 L330 170 L295 240 L345 310 L305 400 L330 480" stroke="${C.navyDark}" stroke-width="10" fill="none" stroke-linejoin="round"/></svg>`,
    {left:"0",top:"0"});
  const cp = crack.querySelector("path") as SVGPathElement;
  cp.style.strokeDasharray = "520"; cp.style.strokeDashoffset = "520"; // hidden by default
  return { root, cols, crackPath: cp, label: lbl };
}

// ── money bag ───────────────────────────────────────────────────────────────
export function moneyBag(parent:HTMLElement,o:{x:number;y:number;s:number;z?:number}) {
  const root = el(parent,"",{position:"absolute",left:`${o.x}px`,top:`${o.y}px`,transformOrigin:"50% 90%",transform:`scale(${o.s})`,zIndex:String(o.z??6)});
  el(root,"",{position:"absolute",left:"30px",top:"0",width:"20px",height:"34px",background:"#C9A227",borderRadius:"4px"});
  el(root,"",{position:"absolute",left:"0",top:"26px",width:"80px",height:"86px",background:C.green,borderRadius:"34px 34px 26px 26px",boxShadow:`inset -10px -8px 0 ${C.greenDark}`});
  const d = el(root,"",{position:"absolute",left:"0",top:"44px",width:"80px",textAlign:"center",font:`400 44px ${F.display}`,color:C.white});
  d.textContent="$";
  return root;
}
export function coinStack(parent:HTMLElement,o:{x:number;y:number;n:number;s:number;color?:string}) {
  const root = el(parent,"",{position:"absolute",left:`${o.x}px`,top:`${o.y}px`,transform:`scale(${o.s})`,transformOrigin:"50% 100%",zIndex:"5"});
  for(let i=0;i<o.n;i++) el(root,"",{position:"absolute",left:"0",top:`${-i*14}px`,width:"84px",height:"16px",borderRadius:"50%",
    background: o.color??C.gold, boxShadow:`inset 0 -5px 0 ${C.gold===(o.color??C.gold)?"#C98F1B":"#555"}`});
  return root;
}
export function bill(parent:HTMLElement,o:{x:number;y:number;s:number;rot:number}) {
  const b = el(parent,"",{position:"absolute",left:`${o.x}px`,top:`${o.y}px`,width:"70px",height:"34px",
    background:C.green,borderRadius:"4px",border:"2px solid #2E7D3A",display:"flex",alignItems:"center",justifyContent:"center",
    font:`400 22px ${F.display}`,color:C.white, zIndex:"7"});
  b.textContent="$"; b.style.transform=`rotate(${o.rot}deg) scale(${o.s})`;
  return b;
}
// ── phone ───────────────────────────────────────────────────────────────────
export function phone(parent:HTMLElement,o:{x:number;y:number;s:number;screen?:string;z?:number}) {
  const root = el(parent,"",{position:"absolute",left:`${o.x}px`,top:`${o.y}px`,width:"64px",height:"110px",
    background:C.ink,borderRadius:"12px",transform:`scale(${o.s})`,zIndex:String(o.z??6)});
  const sc = el(root,"",{position:"absolute",left:"5px",top:"10px",width:"54px",height:"84px",borderRadius:"6px",
    background:o.screen??"#BFE8FF", display:"flex",alignItems:"center",justifyContent:"center",font:`400 16px ${F.display}`,color:C.red});
  if(o.screen) sc.textContent=o.screen;
  return {root, screen:sc};
}
// ── ALL-CAPS callout with black stroke ──────────────────────────────────────
export function callout(parent:HTMLElement, lines:{t:string;c?:string;hl?:boolean}[], o:{x:number;y:number;size?:number;align?:string;z?:number}) {
  const root = el(parent,"",{position:"absolute",left:`${o.x}px`,top:`${o.y}px`,textAlign:(o.align??"center") as any,zIndex:String(o.z??20),opacity:"0"});
  const size=o.size??92;
  for(const ln of lines){
    const d = el(root,"",{font:`400 ${size}px ${F.display}`,lineHeight:"1.04",letterSpacing:"1px",color:ln.c??C.white,whiteSpace:"nowrap",
      textShadow:`${stroke(size/14)}`});
    d.innerHTML = ln.hl ? `<span style="color:${C.yellow}">${ln.t}</span>` : ln.t;
  }
  return root;
}
const stroke=(w:number)=>Array.from({length:12},(_,i)=>{const a=(i/12)*Math.PI*2;return `${(Math.cos(a)*w).toFixed(1)}px ${(Math.sin(a)*w).toFixed(1)}px 0 ${C.ink}`;}).join(",");
export function revealPop(e:HTMLElement,t:number,t0:number,dur=0.5){ const k=EO(seg(t,t0,t0+dur));
  e.style.opacity=String(k); e.style.transform=`scale(${(0.4+0.6*overshoot(seg(t,t0,t0+dur))).toFixed(3)})`; }
export function revealFade(e:HTMLElement,t:number,t0:number,dur=0.45){ const k=EO(seg(t,t0,t0+dur));
  e.style.opacity=String(k); e.style.transform=`translateY(${(1-k)*34}px)`; }
export function hideAfter(e:HTMLElement,t:number,t1:number,dur=0.35){ const k=1-EO(seg(t,t1,t1+dur)); e.style.opacity=String(k); }

// ── red rubber stamp ────────────────────────────────────────────────────────
export function stamp(parent:HTMLElement,text:string,o:{x:number;y:number;rot?:number;size?:number;color?:string;z?:number}) {
  const root = el(parent,"",{position:"absolute",left:`${o.x}px`,top:`${o.y}px`,zIndex:String(o.z??30),opacity:"0",
    border:`10px solid ${o.color??C.red}`,borderRadius:"14px",padding:`${(o.size??64)/5}px ${(o.size??64)/3.4}px`,
    font:`400 ${(o.size??64)}px ${F.display}`,color:o.color??C.red,letterSpacing:"4px",whiteSpace:"nowrap",
    transform:T(0,0,1,o.rot??-12), background:"rgba(255,255,255,.14)"});
  root.textContent=text;
  return root;
}
export function slam(e:HTMLElement,t:number,t0:number){
  const k=seg(t,t0,t0+0.22);
  if(k<=0){e.style.opacity="0";return;}
  e.style.opacity="1";
  const s=1+2.4*Math.pow(1-k,2);
  e.style.transform=T(0,0,s,(parseFloat(e.dataset.rot??String(-12))));
  e.style.transform=T(0,0,1+2.2*Math.pow(1-k,2.4),-12);
}
// ── big number counter ──────────────────────────────────────────────────────
export function counter(parent:HTMLElement,o:{x:number;y:number;size?:number;prefix?:string;digits?:string;align?:string;z?:number}) {
  const root = el(parent,"",{position:"absolute",left:`${o.x}px`,top:`${o.y}px`,font:`400 ${(o.size??110)}px ${F.display}`,
    color:C.white,letterSpacing:"2px",whiteSpace:"nowrap",textShadow:stroke((o.size??110)/15),zIndex:String(o.z??20),
    textAlign:(o.align??"center") as any,opacity:"0"});
  const span = el(root,"",{});
  return { root, set(v:string){span.textContent=v;}, el:root };
}
// ── chapter lower-third card ────────────────────────────────────────────────
export function chapterCard(stage:HTMLElement,n:number,title:string){
  const root = el(stage,"",{position:"absolute",left:"90px",bottom:"70px",zIndex:"40",opacity:"0",display:"flex",alignItems:"center",gap:"26px"});
  const badge = el(root,"",{width:"108px",height:"108px",borderRadius:"18px",background:C.red,display:"flex",alignItems:"center",justifyContent:"center",
    font:`400 62px ${F.display}`,color:C.white,boxShadow:"0 10px 0 rgba(0,0,0,.15)"});
  badge.textContent=String(n).padStart(2,"0");
  const bar = el(root,"",{background:C.navy,borderRadius:"12px",padding:"16px 34px",font:`400 40px ${F.display}`,color:C.white,letterSpacing:"1px",boxShadow:"0 10px 0 rgba(0,0,0,.12)",whiteSpace:"nowrap"});
  bar.textContent=title;
  return {root,
    seek(t:number,tEnd:number){
      const i=EO(seg(t,0.35,1.05)), o=EO(seg(t,tEnd-1.1,tEnd-0.45));
      root.style.opacity=String(i*(1-o));
      root.style.transform=`translateX(${(i-1)*-160 + o*120}px)`;
    }};
}
// ── headline card (news style) ──────────────────────────────────────────────
export function newsCard(parent:HTMLElement,text:string,o:{x:number;y:number;rot?:number;w?:number;src?:string}){
  const root = el(parent,"",{position:"absolute",left:`${o.x}px`,top:`${o.y}px`,width:`${o.w??430}px`,background:C.white,borderRadius:"10px",
    boxShadow:"0 12px 0 rgba(0,0,0,.12)",padding:"18px 22px",zIndex:"12",transform:T(0,0,1,o.rot??-3)});
  const src = el(root,"",{font:`600 15px ${F.label}`,color:C.red,letterSpacing:"3px",textTransform:"uppercase"});
  src.textContent=o.src??"WALL STREET JOURNAL";
  const tx = el(root,"",{font:`600 24px ${F.label}`,color:C.ink,lineHeight:"1.25",marginTop:"6px"});
  tx.textContent=text;
  return root;
}
// ── quote card ──────────────────────────────────────────────────────────────
export function quoteCard(parent:HTMLElement,text:string,o:{x:number;y:number;w?:number;rot?:number}){
  const root = el(parent,"",{position:"absolute",left:`${o.x}px`,top:`${o.y}px`,width:`${o.w??560}px`,background:C.white,borderRadius:"16px",
    padding:"30px 36px",boxShadow:"0 14px 0 rgba(0,0,0,.12)",transform:T(0,0,1,o.rot??-2),zIndex:"14"});
  el(root,"",{font:`400 64px ${F.display}`,color:C.gold,height:"44px"}).textContent="\u201C";
  const tx = el(root,"",{font:`600 34px ${F.label}`,color:C.navy,lineHeight:"1.3",marginTop:"-14px"});
  tx.textContent=text;
  return root;
}
// ── red arrow (up/down) ─────────────────────────────────────────────────────
export function arrow(parent:HTMLElement,o:{x:number;y:number;s:number;dir?: "down"|"up";z?:number}){
  const root = svgEl(parent, `<svg width="300" height="480" viewBox="0 0 300 480">
    <g fill="${C.red}"><rect x="105" y="0" width="90" height="330" rx="10"/>
    <path d="M150 480 L45 320 L255 320 Z"/></g></svg>`,
    {left:`${o.x}px`,top:`${o.y}px`,transform:`scale(${o.s}) ${o.dir==="up"?"scaleY(-1)":""}`,transformOrigin:"50% 50%",zIndex:String(o.z??15)});
  return root;
}
export function zigzagChart(parent:HTMLElement,o:{x:number;y:number;w:number;h:number;color?:string;z?:number}){
  const root = svgEl(parent,`<svg width="${o.w}" height="${o.h}" viewBox="0 0 ${o.w} ${o.h}">
    <path class="zz" d="M0 ${o.h*0.25} L${o.w*0.14} ${o.h*0.18} L${o.w*0.26} ${o.h*0.42} L${o.w*0.4} ${o.h*0.3} L${o.w*0.52} ${o.h*0.6} L${o.w*0.66} ${o.h*0.5} L${o.w*0.82} ${o.h*0.86} L${o.w} ${o.h*0.72}"
      stroke="${o.color??C.red}" stroke-width="14" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>`,
    {left:`${o.x}px`,top:`${o.y}px`,zIndex:String(o.z??15)});
  const p = root.querySelector("path") as SVGPathElement;
  const len = p.getTotalLength(); p.style.strokeDasharray=String(len); p.style.strokeDashoffset=String(len);
  return {root, draw(t:number,t0:number,dur:number){ const k=EO(seg(t,t0,t0+dur)); p.style.strokeDashoffset=String(len*(1-k)); }};
}
// screen shake
export function shake(stage:HTMLElement,t:number,t0:number,amp=9){
  const k=clamp(1-seg(t,t0,t0+0.4));
  const dx=k*Math.sin(t*90)*amp, dy=k*Math.cos(t*70)*amp*0.6;
  (stage as any).style.setProperty("--shakeX",`${dx.toFixed(1)}px`);
  (stage as any).style.setProperty("--shakeY",`${dy.toFixed(1)}px`);
}
