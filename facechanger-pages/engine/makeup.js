import { forwardWarp, toWorld } from './geometry.js';

/** Raster cosmetics drawn over the already warped video; no stickers or SVG. */
export function makeupStrength(preset,effects=[],intensity=100) {
  if(intensity<=0)return 0;
  if(preset==='makeup-green')return Math.min(1,intensity/100);
  const extra=effects.find(e=>e.preset==='makeup-green');
  return Math.min(1,(intensity/100)*(extra?.intensity||0)/100);
}
const LIPS=[61,40,37,0,267,270,291,321,314,17,84,91];
const INNER=[78,191,80,81,82,13,312,311,310,415,308,324,318,402,317,14,87,178,88,95];
const EYES=[
  {upper:[33,160,159,158,133],brow:[70,63,105,66,107]},
  {upper:[263,387,386,385,362],brow:[300,293,334,296,336]}
];
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v));
export function makeupPoint(face,index,controls,aspect) {
  return forwardWarp(face.landmarks[index],controls,aspect);
}
export function drawMakeup(ctx,face,controls,strength,width,height) {
  if(!face||strength<=0)return;
  const aspect=width/height;
  const point=i=>{const p=makeupPoint(face,i,controls,aspect);return {x:p.x*width,y:p.y*height};};
  const fw=face.frame.width*width;
  const intensity=clamp(strength,0,1);
  const trace=(indices)=>{const p=point(indices[0]);ctx.moveTo(p.x,p.y);
    for(const i of indices.slice(1)){const t=point(i);ctx.lineTo(t.x,t.y);}
    ctx.closePath();};
  ctx.save();
  // Feathered cheek tint: skin texture remains visible through translucent pigment.
  for(const i of [205,425]){
    const p=point(i);
    const radius=fw*.205;
    ctx.save();ctx.translate(p.x,p.y);ctx.rotate(face.frame.angle);
    ctx.scale(1,.64);
    const g=ctx.createRadialGradient(0,0,radius*.08,0,0,radius);
    g.addColorStop(0,'rgba(205,49,98,'+(.26*intensity)+')');
    g.addColorStop(.56,'rgba(224,80,121,'+(.15*intensity)+')');
    g.addColorStop(1,'rgba(230,90,120,0)');
    ctx.fillStyle=g;ctx.beginPath();ctx.arc(0,0,radius,0,Math.PI*2);ctx.fill();ctx.restore();
  }
  for(const eye of EYES){
    const upper=eye.upper.map(point),brow=eye.brow.map(point);
    const eyeCenter=point(eye.upper[2]);
    const shadow=ctx.createRadialGradient(eyeCenter.x,eyeCenter.y-fw*.055,fw*.012,
      eyeCenter.x,eyeCenter.y-fw*.055,fw*.22);
    shadow.addColorStop(0,'rgba(21,117,68,'+(.61*intensity)+')');
    shadow.addColorStop(.57,'rgba(35,153,80,'+(.43*intensity)+')');
    shadow.addColorStop(1,'rgba(60,160,96,0)');
    ctx.save();ctx.filter='blur('+Math.max(.7,fw*.009)+'px)';
    ctx.beginPath();ctx.moveTo(upper[0].x,upper[0].y);
    for(const p of upper.slice(1))ctx.lineTo(p.x,p.y);
    for(const p of brow.slice().reverse())ctx.lineTo(p.x,p.y);
    ctx.closePath();ctx.fillStyle=shadow;ctx.fill();ctx.restore();
    // Fine dark eyeliner follows the actual curved upper lid.
    ctx.save();ctx.lineCap='round';ctx.lineJoin='round';
    ctx.strokeStyle='rgba(30,24,24,'+(.7*intensity)+')';
    ctx.lineWidth=Math.max(.8,fw*.008);ctx.beginPath();ctx.moveTo(upper[0].x,upper[0].y);
    for(const p of upper.slice(1))ctx.lineTo(p.x,p.y);
    ctx.stroke();
    // Tapered individually spaced lashes, including the projected eye tilt.
    const upward=toWorld({x:0,y:-fw/width*.048},face);
    for(let k=0;k<8;k++){
      const pos=(k+.5)/8*4,seg=Math.min(3,Math.floor(pos)),t=pos-seg;
      const a=upper[seg],b=upper[seg+1],x=a.x+(b.x-a.x)*t,y=a.y+(b.y-a.y)*t;
      const length=(.62+.38*Math.sin(Math.PI*(k+.5)/8))*intensity;
      const dx=upward.x*width*length,dy=upward.y*height*length;
      ctx.beginPath();ctx.moveTo(x,y);
      ctx.quadraticCurveTo(x+dx*.4-fw*.008,y+dy*.55,x+dx,y+dy);
      ctx.strokeStyle='rgba(28,22,23,'+(.85*intensity)+')';
      ctx.lineWidth=Math.max(.65,fw*.006*(.75+.25*length));
      ctx.stroke();
    }
    ctx.restore();
  }
  ctx.save();ctx.beginPath();trace(LIPS);trace(INNER);
  ctx.fillStyle='rgba(163,19,58,'+(.57*intensity)+')';ctx.fill('evenodd');
  ctx.strokeStyle='rgba(110,17,45,'+(.19*intensity)+')';
  ctx.lineWidth=Math.max(.6,fw*.003);ctx.stroke();
  // Subtle lip shine, without repainting exposed teeth or mouth interior.
  const a=point(40),b=point(270);
  ctx.beginPath();ctx.moveTo(a.x,a.y);ctx.quadraticCurveTo(point(0).x,point(0).y-fw*.014,b.x,b.y);
  ctx.strokeStyle='rgba(255,205,208,'+(.22*intensity)+')';
  ctx.lineWidth=Math.max(1,fw*.012);ctx.stroke();ctx.restore();
  ctx.restore();
}
