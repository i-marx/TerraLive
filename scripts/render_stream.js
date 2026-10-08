const puppeteer=require('puppeteer');
const fs=require('fs');
(async()=>{
const SECONDS=parseInt(process.env.RENDER_SECONDS||'600',10);
const browser=await puppeteer.launch({headless:'new',args:['--no-sandbox','--disable-dev-shm-usage','--enable-unsafe-swiftshader','--use-angle=swiftshader','--window-size=1920,1080','--autoplay-policy=no-user-gesture-required']});
const page=await browser.newPage();
await page.setViewport({width:1920,height:1080,deviceScaleFactor:1});
const ws=fs.createWriteStream('cap.webm');
await page.exposeFunction('nodeChunk',b64=>{ws.write(Buffer.from(b64,'base64'));});
await page.goto('https://i-marx.github.io/TerraLive/demo.html?full=1&rot=1&intro=0&cb=ci'+Date.now(),{waitUntil:'networkidle2',timeout:180000});
console.log('page loaded, warming textures 90s');
await new Promise(r=>setTimeout(r,90000));
const gl=await page.evaluate(()=>{const c=document.querySelector('canvas');const g=c.getContext('webgl')||c.getContext('webgl2');return {w:c.width,h:c.height,maxTex:g?g.getParameter(g.MAX_TEXTURE_SIZE):0};});
console.log('canvas '+JSON.stringify(gl));
await page.evaluate(()=>{
  const c=document.querySelector('canvas');
  const st=c.captureStream(30);
  window.__mr=new MediaRecorder(st,{mimeType:'video/webm;codecs=vp9',videoBitsPerSecond:6000000});
  window.__mr.ondataavailable=async e=>{
    if(!e.data||!e.data.size)return;
    const u8=new Uint8Array(await e.data.arrayBuffer());
    let s='';
    for(let i=0;i<u8.length;i+=8192)s+=String.fromCharCode.apply(null,u8.subarray(i,Math.min(i+8192,u8.length)));
    window.nodeChunk(btoa(s));
  };
  window.__mr.start(2000);
});
console.log('recording '+SECONDS+'s');
await new Promise(r=>setTimeout(r,SECONDS*1000));
await page.evaluate(()=>{try{window.__mr.stop()}catch(e){}});
await new Promise(r=>setTimeout(r,5000));
ws.end();
await new Promise(r=>ws.on('close',r));
await browser.close();
const sz=fs.statSync('cap.webm').size;
console.log('cap.webm bytes='+sz);
if(sz<2000000){console.error('capture too small');process.exit(1);}
})().catch(e=>{console.error(e);process.exit(1);});