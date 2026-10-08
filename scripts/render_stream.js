const puppeteer=require('puppeteer');
const fs=require('fs');
(async()=>{
const FRAMES=parseInt(process.env.FRAMES||'3000',10);
const STEP_MS=1000/12;
fs.mkdirSync('frames',{recursive:true});
const browser=await puppeteer.launch({headless:'new',protocolTimeout:600000,args:['--no-sandbox','--disable-dev-shm-usage','--enable-unsafe-swiftshader','--use-angle=swiftshader','--window-size=1280,720']});
const page=await browser.newPage();
await page.setViewport({width:1280,height:720,deviceScaleFactor:1});
await page.evaluateOnNewDocument(()=>{
  const T0=Date.now();
  window.__vt=T0;
  const RD=Date; const Rnow=Date.now.bind(Date); const Pnow=performance.now.bind(performance);
  Date.now=()=>window.__vt;
  const OrigDate=Date;
  window.Date=new Proxy(OrigDate,{construct(t,a){ if(a.length===0) return new t(window.__vt); return new t(...a); }, get(t,p){ return p==='now'? (()=>window.__vt) : t[p]; }});
  const p0=Pnow();
  performance.now=()=>(window.__vt-T0)+p0;
  window.__rafQ=[];
  window.requestAnimationFrame=cb=>{window.__rafQ.push(cb);return window.__rafQ.length;};
  window.cancelAnimationFrame=()=>{};
  window.__step=(stepMs)=>{
    window.__vt+=stepMs;
    const q=window.__rafQ; window.__rafQ=[];
    for(const cb of q){ try{ cb(performance.now()); }catch(e){} }
    return window.__rafQ.length;
  };
  window.__grab=(q)=>{ const c=document.querySelector('canvas'); return c? c.toDataURL('image/jpeg',q) : null; };
});
await page.goto('https://i-marx.github.io/TerraLive/demo.html?full=1&rot=1&intro=0&rotspd=0.025133&cb=ci'+Math.floor(Math.random()*1e9),{waitUntil:'domcontentloaded',timeout:180000});
console.log('page loaded, pumping warmup frames for texture load');
for(let w=0;w<240;w++){ await page.evaluate(s=>window.__step(s),STEP_MS); await new Promise(r=>setTimeout(r,250)); if(w%40===0)console.log('warmup',w); }
console.log('warmup done, rendering '+FRAMES+' frames');
const t0=Date.now();
for(let n=0;n<FRAMES;n++){
  await page.evaluate(s=>window.__step(s),STEP_MS);
  const d=await page.evaluate(q=>window.__grab(q),0.82);
  if(!d) throw new Error('no canvas at frame '+n);
  fs.writeFileSync('frames/f'+String(n).padStart(5,'0')+'.jpg',Buffer.from(d.slice(23),'base64'));
  if(n%300===0){ const el=(Date.now()-t0)/1000; console.log('frame '+n+' elapsed '+el.toFixed(0)+'s rate '+(n/Math.max(el,1)).toFixed(2)+'fps'); }
}
await browser.close();
const n=fs.readdirSync('frames').length;
console.log('frames written: '+n);
if(n<FRAMES) process.exit(1);
})().catch(e=>{console.error(e);process.exit(1);});