import { forwardWarp } from './geometry.js?v=realistic-ai-3';
const TILE=64;
const atlas=new Image();atlas.decoding='async';
let ready=false;
fetch('./assets/ai-realistic-atlas.b64?v=2').then(r=>{if(!r.ok)throw Error('Missing atlas');return r.text();})
  .then(b64=>{atlas.onload=()=>ready=true;atlas.src='data:image/webp;base64,'+b64.trim();})
  .catch(()=>{ready=false;});
export const aiAtlasReady=()=>ready;
export function drawAtlasTile(ctx,index,x,y,w,h){
  if(!ready)return;
  ctx.drawImage(atlas,(index%4)*TILE,Math.floor(index/4)*TILE,TILE,TILE,x,y,w,h);
}
const indices={'glasses-classic':0,'eyepatch-black':1,'mask-lace':3,
  'mustache-handlebar':4};
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
  // MediaPipe IDs 33/263 are reversed on the mirrored canvas. The raw
  // atan2(263-33) rotated every accessory by 180 degrees.
  const leftEye=ea.x<=eb.x?ea:eb,rightEye=ea.x<=eb.x?eb:ea;
  const ang=Math.atan2(rightEye.y-leftEye.y,rightEye.x-leftEye.x);
  const leftMouth=la.x<=lb.x?la:lb,rightMouth=la.x<=lb.x?lb:la;
  const mouthAng=Math.atan2(rightMouth.y-leftMouth.y,rightMouth.x-leftMouth.x);
  const temples=[p(127),p(356)].sort((a,b)=>a.x-b.x);
  const lipY=p(0).y;
  for(const {id,alpha} of active){
    let center=eyes,w=eyed*1.9,h=eyed*.73,angle=ang;
    switch(id){
      case 'eyepatch-black':center=ea;w=eyed*.78;h=eyed*.53;break;
      case 'mask-lace':w=eyed*2.22;h=eyed*1.15;break;
      case 'mustache-handlebar':center={x:mouth.x,y:lipY-(p(17).y-lipY)*.3};
        w=mw*1.65;h=mw*.54;break;
    }
    ctx.save();ctx.translate(center.x,center.y);
    const rotation=id==='mustache-handlebar'?mouthAng:angle;
    ctx.rotate(rotation);
    ctx.globalAlpha=alpha;
    if(id==='glasses-classic'||id==='eyepatch-black'||id==='mask-lace'){
      // Texture-sampled front-facing arms / ties; stop at the temples.
      const c=Math.cos(rotation),s=Math.sin(rotation);
      const local=p=>({x:(p.x-center.x)*c+(p.y-center.y)*s,
        y:-(p.x-center.x)*s+(p.y-center.y)*c});
      const l=local(temples[0]),r=local(temples[1]);
      const stripHeight=Math.max(2,eyed*(id==='glasses-classic'?.028:.022));
      const bandY=id==='eyepatch-black'?-h*.30:id==='mask-lace'?-h*.07:-h*.31;
      for(const [side,target] of [[-1,l],[1,r]]){
        const start=side*w*.48,end=target.x;
        if((end-start)*side<=1)continue;
        const srcX=(indices[id]%4)*TILE+20;
        const srcY=Math.floor(indices[id]/4)*TILE+19;
        ctx.drawImage(atlas,srcX,srcY,24,6,Math.min(start,end),bandY,
          Math.abs(end-start),stripHeight);
      }
    }
    drawAtlasTile(ctx,indices[id],-w/2,-h/2,w,h);ctx.restore();
  }
}
