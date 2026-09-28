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
  // Corrected raster crops both face image-up; align their heads with forward motion.
  return Math.atan2(dy,dx)+Math.PI/2;
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
/** Motion is derived from the supplied top-down raster: wings flutter faster than the walking cycle. */
export function waspMotionAngles(phase=0) {
  const wing=Math.sin(phase)*.12;
  const stride=Math.sin(phase*.5)*.06;
  return {
    wings:[wing,-wing],
    legs:[stride,-stride,-stride*.82,stride*.82,stride*.68,-stride*.68]
  };
}
const WASP_PARTS=Object.freeze({
  wings:[
    {pivot:[.43,.405],points:[[.005,.255],[.11,.245],[.455,.345],[.485,.465],[.40,.545],[.10,.53],[.01,.445]]},
    {pivot:[.57,.405],points:[[.995,.255],[.89,.245],[.545,.345],[.515,.465],[.60,.545],[.90,.53],[.99,.445]]}
  ],
  legs:[
    {pivot:[.405,.35],points:[[.21,.105],[.29,.11],[.46,.31],[.46,.43],[.37,.41],[.23,.205]]},
    {pivot:[.595,.35],points:[[.79,.105],[.71,.11],[.54,.31],[.54,.43],[.63,.41],[.77,.205]]},
    {pivot:[.425,.49],points:[[.115,.68],[.145,.60],[.39,.44],[.50,.445],[.49,.555],[.19,.735]]},
    {pivot:[.575,.49],points:[[.885,.68],[.855,.60],[.61,.44],[.50,.445],[.51,.555],[.81,.735]]},
    {pivot:[.445,.57],points:[[.225,.96],[.255,.82],[.335,.62],[.405,.54],[.50,.53],[.495,.655],[.38,.77],[.31,.975]]},
    {pivot:[.555,.57],points:[[.775,.96],[.745,.82],[.665,.62],[.595,.54],[.50,.53],[.505,.655],[.62,.77],[.69,.975]]}
  ],
  body:[
    [[.425,.17],[.575,.17],[.65,.31],[.62,.52],[.61,.83],[.53,.975],[.47,.975],[.39,.83],[.38,.52],[.35,.31]],
    [[.48,.225],[.42,.16],[.32,.09],[.20,.07],[.20,.02],[.31,.025],[.43,.09],[.49,.19]],
    [[.52,.225],[.58,.16],[.68,.09],[.80,.07],[.80,.02],[.69,.025],[.57,.09],[.51,.19]]
  ]
});
function pathPolygon(ctx,points,d) {
  ctx.beginPath();
  ctx.moveTo(points[0][0]*d,points[0][1]*d);
  for(let i=1;i<points.length;i++)ctx.lineTo(points[i][0]*d,points[i][1]*d);
  ctx.closePath();
}
function drawWaspPart(ctx,image,d,part,angle) {
  ctx.save();
  ctx.translate(part.pivot[0]*d,part.pivot[1]*d);
  ctx.rotate(angle);
  ctx.translate(-part.pivot[0]*d,-part.pivot[1]*d);
  pathPolygon(ctx,part.points,d);
  ctx.clip();
  ctx.drawImage(image,0,0,d,d);
  ctx.restore();
}
function waspGaitFrame(image,canvas,phase) {
  const ctx=canvas.getContext('2d'),d=192;
  if(canvas.width!==d||canvas.height!==d){canvas.width=d;canvas.height=d;}
  ctx.clearRect(0,0,d,d);
  const motion=waspMotionAngles(phase);
  WASP_PARTS.wings.forEach((part,i)=>drawWaspPart(ctx,image,d,part,motion.wings[i]));
  WASP_PARTS.legs.forEach((part,i)=>drawWaspPart(ctx,image,d,part,motion.legs[i]));
  // Rigid body and antennae go last so moving roots remain visually attached.
  for(const points of WASP_PARTS.body){
    ctx.save();
    pathPolygon(ctx,points,d);
    ctx.clip();
    ctx.drawImage(image,0,0,d,d);
    ctx.restore();
  }
  return true;
}
/** Raster-only gait: animated image sectors, with a dedicated articulated wasp. */
export function creatureGaitFrame(image,canvas,phase,id) {
  if(id==='spider')return spiderGaitFrame(image,canvas,phase);
  if(!image?.naturalWidth||!image?.naturalHeight)return false;
  if(id==='wasp')return waspGaitFrame(image,canvas,phase);
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
    ctx.rotate(step*.064);
    ctx.drawImage(image,-w*fit/2,-h*fit/2,w*fit,h*fit);
    ctx.restore();
  }
  ctx.save();ctx.beginPath();
  ctx.ellipse(x,y,d*.17,d*.28,0,0,Math.PI*2);
  ctx.clip();ctx.drawImage(image,(d-w*fit)/2,(d-h*fit)/2,w*fit,h*fit);
  ctx.restore();
  return true;
}
