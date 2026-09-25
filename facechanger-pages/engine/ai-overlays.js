import { forwardWarp } from './geometry.js';
const TILE=64;
const atlas=new Image();atlas.decoding='async';
let ready=false;
fetch('./assets/ai-realistic-atlas.b64?v=1').then(r=>{if(!r.ok)throw Error('Missing atlas');return r.text();})
  .then(b64=>{atlas.onload=()=>ready=true;atlas.src='data:image/webp;base64,'+b64.trim();})
  .catch(()=>{ready=false;});
export const aiAtlasReady=()=>ready;
export function drawAtlasTile(ctx,index,x,y,w,h){
  if(!ready)return;
  ctx.drawImage(atlas,(index%4)*TILE,Math.floor(index/4)*TILE,TILE,TILE,x,y,w,h);
}
const indices={'glasses-classic':0,'eyepatch-black':1,'nose-strip':2,'mask-lace':3,
  'mustache-handlebar':4,'beard-full':5,'grillz-gold':6};
export function activeAIOverlays(preset,effects=[],intensity=100){
  if(intensity<=0)return[];
  const out=new Map(),add=(id,v)=>{if(indices[id]===undefined||v<=0)return;
    out.set(id,Math.min(1,(out.get(id)||0)+v));};
  add(preset,intensity/100);
  for(const e of effects)add(e?.preset,intensity/100*(e?.intensity||0)/100);
  return [...out].map(([id,alpha])=>({id,alpha}));
}
export function drawAIOverlays(ctx,face,controls,width,height,active){
  if(!ready||!face||!active.length)return;
  const aspect=width/height;
  const p=i=>{const a=forwardWarp(face.landmarks[i],controls,aspect);return{x:a.x*width,y:a.y*height};};
  const m=(a,b)=>({x:(a.x+b.x)/2,y:(a.y+b.y)/2});
  const d=(a,b)=>Math.hypot(a.x-b.x,a.y-b.y);
  const ea=p(33),eb=p(263),eyes=m(ea,eb),eyed=d(ea,eb);
  const la=p(61),lb=p(291),mouth=m(la,lb),mw=d(la,lb);
  const nose=m(p(98),p(327)),tip=p(1),chin=p(152);
  const ang=Math.atan2(eb.y-ea.y,eb.x-ea.x);
  const lipY=p(0).y;
  for(const {id,alpha} of active){
    let center=eyes,w=eyed*1.9,h=eyed*.73,angle=ang;
    switch(id){
      case 'eyepatch-black':center=ea;w=eyed*.78;h=eyed*.53;break;
      case 'nose-strip':center=m(nose,tip);w=d(p(98),p(327))*1.75;h=w*.43;break;
      case 'mask-lace':w=eyed*2.22;h=eyed*1.15;break;
      case 'mustache-handlebar':center={x:mouth.x,y:lipY-(p(17).y-lipY)*.3};
        w=mw*1.65;h=mw*.54;break;
      case 'beard-full':center=m(mouth,chin);w=mw*1.96;h=d(mouth,chin)*1.9;break;
      case 'grillz-gold':center=mouth;w=mw*1.25;h=mw*.67;break;
    }
    ctx.save();ctx.translate(center.x,center.y);ctx.rotate(angle);ctx.globalAlpha=alpha;
    drawAtlasTile(ctx,indices[id],-w/2,-h/2,w,h);ctx.restore();
  }
}
