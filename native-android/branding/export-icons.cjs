const fs = require('fs');
const path = require('path');
const sharp = require('sharp');
(async () => {
  const roles = ['customer', 'merchant', 'rider'];
  const metrics = {};
  for (const role of roles) {
    const source = path.join(__dirname, `${role}-play-store.svg`);
    await sharp(source).resize(512,512).png().toFile(path.join(__dirname, `${role}-play-store-512.png`));
    for (const [density,size] of Object.entries({mdpi:48,hdpi:72,xhdpi:96,xxhdpi:144,xxxhdpi:192})) {
      const dir = path.join(__dirname, '..', role, 'src/main/res', `mipmap-${density}`);
      fs.mkdirSync(dir, {recursive:true});
      await sharp(source).resize(size,size).png().toFile(path.join(dir,'ic_queuego_launcher.png'));
    }
    const foreground = fs.readFileSync(path.join(__dirname,`${role}-foreground.svg`),'utf8');
    const {data,info} = await sharp(Buffer.from(foreground)).resize(1080,1080).ensureAlpha().raw().toBuffer({resolveWithObject:true});
    let maxRadius=0;
    for(let y=0;y<info.height;y++) for(let x=0;x<info.width;x++) {
      if(data[(y*info.width+x)*4+3]>127) maxRadius=Math.max(maxRadius,Math.hypot((x+.5)/10-54,(y+.5)/10-54));
    }
    const rolePaths=foreground.match(/<path[^>]*\/>/g).slice(2).join('');
    const raw=await sharp(Buffer.from(`<svg xmlns="http://www.w3.org/2000/svg" width="1080" height="1080" viewBox="0 0 108 108">${rolePaths}</svg>`)).ensureAlpha().raw().toBuffer();
    let coverage=0;for(let i=3;i<raw.length;i+=4)coverage+=raw[i]/255;
    metrics[role]={maxRadiusDp:Number(maxRadius.toFixed(2)),roleCoverage:Number((coverage/100).toFixed(2))};
    if(maxRadius>33) throw new Error(`${role}: outside adaptive safe circle`);
  }
  const coverage=Object.values(metrics).map(x=>x.roleCoverage);
  const spread=(Math.max(...coverage)-Math.min(...coverage))/Math.min(...coverage);
  if(spread>0.10) throw new Error('Role visual weight differs by more than 10%');
  await sharp(path.join(__dirname,'family-preview.svg')).png().toFile(path.join(__dirname,'family-preview.png'));
  fs.writeFileSync(path.join(__dirname,'geometry-check.json'),JSON.stringify({renderer:`sharp ${sharp.versions.sharp}`,safeRadiusDp:33,roleWeightSpreadPercent:Number((spread*100).toFixed(2)),metrics},null,2)+'\n');
})().catch(e=>{console.error(e.message);process.exitCode=1;});
