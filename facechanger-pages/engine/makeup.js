import { forwardWarp } from './geometry.js';
import { aiAtlasReady, drawAtlasTile } from './ai-overlays.js?v=realistic-ai-1';
export function makeupStrength(preset,effects=[],intensity=100){
  if(intensity<=0)return 0;
  if(preset==='makeup-green')return Math.min(1,intensity/100);
  const extra=effects.find(e=>e?.preset==='makeup-green');
  return Math.min(1,(intensity/100)*(extra?.intensity||0)/100);
}
export function makeupPoint(face,index,controls,aspect){
  return forwardWarp(face.landmarks[index],controls,aspect);
}
export function drawMakeup(ctx,face,controls,strength,width,height){
  if(!face||strength<=0||!aiAtlasReady())return;
  const aspect=width/height;
  const p=i=>{const t=makeupPoint(face,i,controls,aspect);return{x:t.x*width,y:t.y*height};};
  const a=p(33),b=p(263),eyes={x:(a.x+b.x)/2,y:(a.y+b.y)/2};
  const c=p(61),d=p(291),mouth={x:(c.x+d.x)/2,y:(p(0).y+p(17).y)/2};
  const eyeSpan=Math.hypot(b.x-a.x,b.y-a.y);
  const heightPx=Math.max(20,(mouth.y-eyes.y)/.52);
  const widthPx=eyeSpan*1.77;
  const cx=(eyes.x+mouth.x)/2,cy=eyes.y+heightPx*.22;
  ctx.save();ctx.translate(cx,cy);ctx.rotate(Math.atan2(b.y-a.y,b.x-a.x));
  ctx.globalAlpha=Math.min(1,strength)*.78;
  drawAtlasTile(ctx,7,-widthPx/2,-heightPx/2,widthPx,heightPx);
  ctx.restore();
}
