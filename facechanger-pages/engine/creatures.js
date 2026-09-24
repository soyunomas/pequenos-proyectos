import { spiderRoute, spiderPosition, spiderHeading, spiderGaitFrame } from './spider.js';

export const CREATURE_IDS=Object.freeze(['spider','cockroach','wasp']);
export const MAX_CREATURES=5;
export const CREATURE_DEFAULTS=Object.freeze({speed:100,size:175,count:1});
const finite=(v,a,b)=>Number.isFinite(v)&&v>=a&&v<=b;
export function creatureConfig(value={},legacy={}) {
  const speed=value.speed??legacy.speed;
  const size=value.size??legacy.size;
  return {
    speed:finite(speed,25,250)?speed:CREATURE_DEFAULTS.speed,
    size:finite(size,40,220)?size:CREATURE_DEFAULTS.size,
    count:finite(value.count,1,MAX_CREATURES)&&Number.isInteger(value.count)?
      value.count:CREATURE_DEFAULTS.count
  };
}
/** Species share the requested total; supplementary presets retain their own intensity. */
export function activeCreatures(preset,effects=[],intensity=100,count=1) {
  if(intensity<=0)return [];
  const active=CREATURE_IDS.filter(id=>id===preset || effects.some(e=>e?.preset===id && e.intensity>0));
  if(!active.length)return [];
  active.sort((a,b)=>Number(b===preset)-Number(a===preset));
  const total=Math.max(1,Math.min(MAX_CREATURES,Math.floor(count)||1));
  return Array.from({length:total},(_,i)=>{
    const id=active[i%active.length];
    const effect=effects.find(e=>e?.preset===id);
    return {id,ordinal:i,intensity:intensity/100*(id===preset?1:(effect?.intensity??0)/100),
      phase:(i/total+.071*(i%2))%1,speed:1+(i%3-1)*.07,scale:1+(i%3-1)*.06};
  });
}
export function creaturePosition(face,progress,aspect=16/9) {
  return spiderPosition(spiderRoute(face),progress,aspect);
}
export function creatureHeading(id,dx,dy) {
  if(id==='spider')return spiderHeading(dx,dy);
  // The generated cockroach / wasp photographs point toward top-right.
  return Math.atan2(dy,dx)-Math.atan2(-.91,.42);
}
export function localLighting(color,id='spider') {
  if(!color || color.length<3)return {brightness:id==='spider'?.69:.85,shadow:.30,tint:null};
  const [r,g,b]=color;
  const luma=(.2126*r+.7152*g+.0722*b)/255;
  const base=id==='spider'?.70:id==='cockroach'?.84:.92;
  return {
    brightness:Math.max(.48,Math.min(1.52,base+1.18*(luma-.46))),
    shadow:Math.max(.10,Math.min(.37,.34-.19*(luma-.45))),
    tint:[r,g,b],
    luma
  };
}
/** Raster-only gait: moving sectors plus fixed central body, no vector insects. */
export function creatureGaitFrame(image,canvas,phase,id) {
  if(id==='spider')return spiderGaitFrame(image,canvas,phase);
  if(!image?.naturalWidth||!image?.naturalHeight)return false;
  const ctx=canvas.getContext('2d'),d=176;
  if(canvas.width!==d||canvas.height!==d){canvas.width=d;canvas.height=d;}
  ctx.clearRect(0,0,d,d);
  const w=image.naturalWidth,h=image.naturalHeight,fit=d/Math.max(w,h);
  const x=d/2,y=d/2,legs=6;
  for(let k=0;k<legs;k++){
    const angle=(k+.5)*Math.PI*2/legs-Math.PI/2;
    ctx.save();ctx.beginPath();ctx.moveTo(x,y);
    ctx.arc(x,y,d*1.5,angle-Math.PI/legs-.01,angle+Math.PI/legs+.01);
    ctx.closePath();ctx.clip();ctx.translate(x,y);
    const step=Math.sin(phase+(k%2)*Math.PI+Math.floor(k/2)*.6);
    ctx.rotate(step*(id==='wasp'?.11:.064));
    ctx.drawImage(image,-w*fit/2,-h*fit/2,w*fit,h*fit);
    ctx.restore();
  }
  ctx.save();ctx.beginPath();
  ctx.ellipse(x,y,d*.17,d*.28,-.28,0,Math.PI*2);
  ctx.clip();ctx.drawImage(image,(d-w*fit)/2,(d-h*fit)/2,w*fit,h*fit);
  ctx.restore();
  return true;
}
