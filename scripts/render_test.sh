#!/bin/bash
# Terra render test: proves whether THIS server can render the Earth scene
# on its CPU (no GPU). Prints VERDICT GOOD or VERDICT BLACK at the end.
# Safe to re-run. First run downloads ~300 MB (Chrome), takes ~5 min.
set -u
export DEBIAN_FRONTEND=noninteractive
D=/opt/terra/rtest
mkdir -p "$D"; cd "$D"
apt-get install -y -qq nodejs npm fonts-liberation libasound2t64 libatk-bridge2.0-0 libatk1.0-0 libcups2 libdrm2 libgbm1 libgtk-3-0 libnss3 libxcomposite1 libxdamage1 libxfixes3 libxkbcommon0 libxrandr2 >/dev/null 2>&1
[ -d node_modules/puppeteer ] || { npm init -y >/dev/null 2>&1; npm i --silent puppeteer pngjs >/dev/null 2>&1; }
cat > rt.js <<'EOF'
const puppeteer = require('puppeteer');
const fs = require('fs');
const { PNG } = require('pngjs');
(async () => {
  const br = await puppeteer.launch({headless:'new', args:['--no-sandbox','--disable-dev-shm-usage','--enable-unsafe-swiftshader']});
  const pg = await br.newPage();
  await pg.setViewport({width:1280,height:720});
  await pg.goto('https://i-marx.github.io/TerraLive/demo.html?full=1&rot=1&intro=0&rotspd=0.034907&cb='+Date.now(), {waitUntil:'networkidle2', timeout:90000});
  await new Promise(r=>setTimeout(r,15000));
  let best=0;
  for(let i=0;i<5;i++){
    await pg.screenshot({path:'f'+i+'.png'});
    const png = PNG.sync.read(fs.readFileSync('f'+i+'.png'));
    let s=0,n=png.width*png.height;
    for(let p=0;p<png.data.length;p+=4) s+=0.299*png.data[p]+0.587*png.data[p+1]+0.114*png.data[p+2];
    const avg=s/n;
    console.log('frame '+i+': avg luma '+avg.toFixed(2));
    if(avg>best)best=avg;
    await new Promise(r=>setTimeout(r,2000));
  }
  await br.close();
  console.log(best>6 ? 'VERDICT GOOD (planet visible, luma '+best.toFixed(1)+')' : 'VERDICT BLACK (max luma '+best.toFixed(1)+')');
})().catch(e=>{console.error('VERDICT ERROR '+e.message);process.exit(1)});
EOF
node rt.js
