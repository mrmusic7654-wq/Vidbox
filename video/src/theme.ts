// ─── Vidbox theme: palette, easing, DOM micro-framework ─────────────────────
export const C = {
  bg: "#E9F2F6", bgDot: "#c9dde8", navy: "#1F3A5F", navyDark: "#16293F",
  red: "#D94A3D", redDark: "#A83830", green: "#3FA34D", greenDark: "#2E7D3A",
  gold: "#F2B134", yellow: "#FFD645", white: "#FFFFFF", ink: "#101820",
  gray: "#8FA3B0", grayLight: "#D5E2EA", hoodie: "#7A8794", skin: "#F0C29B",
  skin2: "#C68B59", darkSuit: "#26303B",
};
export const F = { display: "'Anton'", label: "'Oswald'", black: "'Archivo Black'" };

// easings
export const clamp = (x:number,a=0,b=1)=>Math.max(a,Math.min(b,x));
export const EO = (t:number)=>1-Math.pow(1-clamp(t),3);            // easeOutCubic
export const EI = (t:number)=>Math.pow(clamp(t),3);
export const EIO = (t:number)=>t<.5?4*t*t*t:1-Math.pow(-2*t+2,3)/2;
export const LIN = (t:number)=>clamp(t);
export const seg = (t:number,a:number,b:number)=>clamp((t-a)/(b-a));
// springy overshoot (0→>1→1)
export const spring = (t:number)=>{const x=clamp(t);return 1 - Math.pow(2,-9*x)*Math.cos(11*x)*1.0 - 0.0*(x);};
export const popScale = (t:number)=>{const x=clamp(t); if(x>=1)return 1; return 1+0.28*Math.sin(x*Math.PI)*Math.pow(1-x,0.6)* (x<1?1:0) * (EO(x)); };
// simple overshoot: 0 → 1.12 → 1
export const overshoot = (t:number)=>{const x=clamp(t); if(x<=0)return 0; if(x>=1)return 1; return 1+0.35*Math.sin(Math.PI*Math.min(1,x))*(1-x); };

// DOM helpers
export function el(parent:HTMLElement|null, cls:string, css:Record<string,any>={}, html=""):HTMLElement {
  const d = document.createElement("div");
  if (cls) d.className = cls;
  Object.assign(d.style, css);
  if (html) d.innerHTML = html;
  if (parent) parent.appendChild(d);
  return d;
}
export function svgEl(parent:HTMLElement, svg:string, css:Record<string,any>={}):HTMLElement{
  const w = el(parent, "", {position:"absolute", pointerEvents:"none", ...css});
  w.innerHTML = svg;
  return w;
}
export const T = (x=0,y=0,s=1,r=0)=>`translate(${x.toFixed(2)}px,${y.toFixed(2)}px) scale(${s.toFixed(4)}) rotate(${r.toFixed(2)}deg)`;
export const setT = (e:HTMLElement,x=0,y=0,s=1,r=0,o=1)=>{
  e.style.transform=T(x,y,s,r); e.style.opacity=String(o);
};
export const fmt = (n:number)=>"$"+Math.round(n).toLocaleString("en-US");
export const fmtPlain=(n:number)=>Math.round(n).toLocaleString("en-US");
