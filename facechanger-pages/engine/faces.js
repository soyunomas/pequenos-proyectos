import { faceFromLandmarks, distance } from './geometry.js';

export const MAX_FACES=5;
/** Match by nose proximity rather than MediaPipe array order to avoid mixing face tracks. */
export function trackFaces(previous=[],rawFaces=[]) {
  const available=new Set(previous.map((_,i)=>i));
  return rawFaces.slice(0,MAX_FACES).map(raw=>{
    const face=faceFromLandmarks(raw);
    if(!face)return null;
    const center=f=>f.landmarks[1];
    let match=-1,closest=Infinity;
    for(const i of available){
      const old=previous[i],d=distance(center(face),center(old));
      if(d<closest && d<Math.max(face.frame.width,old.frame.width)*.48){
        closest=d;match=i;
      }
    }
    if(match!==-1){
      available.delete(match);
      const old=previous[match];
      if(old.landmarks.length===face.landmarks.length){
        face.landmarks=face.landmarks.map((p,i)=>({
          x:old.landmarks[i].x*.56+p.x*.44,
          y:old.landmarks[i].y*.56+p.y*.44
        }));
        const aligned=faceFromLandmarks(face.landmarks.map(p=>({x:1-p.x,y:p.y})));
        if(aligned)face.frame=aligned.frame;
      }
    }
    return face;
  }).filter(Boolean);
}
export function nearestFace(faces=[],point=null,aspect=16/9){
  if(!point)return null;
  let best=null,score=Infinity;
  for(const face of faces){
    const center=face.landmarks[1];
    const d=Math.hypot((point.x-center.x)*aspect,point.y-center.y);
    const limit=face.frame.width*aspect*.76;
    if(d<limit&&d<score){best=face;score=d;}
  }
  return best;
}
/** A separate WebGL scissor per face retains the 32-control mobile GPU budget. */
export function controlBounds(controls,aspect=16/9){
  if(!controls?.length)return null;
  let left=1,top=1,right=0,bottom=0;
  for(const c of controls){
    // Expansion includes the destination displacement and scaling near the
    // falloff; the extra margin avoids visible scissor seams.
    const rx=c.radius*(1+Math.max(0,c.scale||0)*.5)+Math.abs(c.dx)+.003;
    const ry=c.radius*aspect*(c.shapeY||1)*
      (1+Math.max(0,c.scaleY??c.scale??0)*.5)+Math.abs(c.dy)+.003;
    left=Math.min(left,c.x-rx);right=Math.max(right,c.x+rx);
    top=Math.min(top,c.y-ry);bottom=Math.max(bottom,c.y+ry);
  }
  return {left:Math.max(0,left),top:Math.max(0,top),
    right:Math.min(1,right),bottom:Math.min(1,bottom)};
}
