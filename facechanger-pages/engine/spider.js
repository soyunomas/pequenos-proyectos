/** Recorrido facial y patas rasterizadas: la fuente es la fotografía WebP, no SVG. */
export const SPIDER_DEFAULTS=Object.freeze({speed:100,size:175});
export const SPIDER_LIMITS=Object.freeze({speed:[25,250],size:[40,220]});
export function spiderConfig(value) {
  return {
    speed: Number.isFinite(value?.speed) && value.speed>=25 && value.speed<=250 ? value.speed : 100,
    size: Number.isFinite(value?.size) && value.size>=40 && value.size<=220 ? value.size : SPIDER_DEFAULTS.size
  };
}
const ROUTE=[205,234,127,109,10,338,356,454,425,291,152,61,205,1];
export function spiderRoute(face) {
  if (!face?.landmarks || ROUTE.some(id=>!face.landmarks[id]))return [];
  return ROUTE.map(id=>face.landmarks[id]);
}
function spline(path,index,t) {
  const n=path.length;
  const a=path[(index+n-1)%n],b=path[index],c=path[(index+1)%n],d=path[(index+2)%n];
  const axis=k=>.5*((2*b[k])+(-a[k]+c[k])*t+
    (2*a[k]-5*b[k]+4*c[k]-d[k])*t*t+
    (-a[k]+3*b[k]-3*c[k]+d[k])*t*t*t);
  return {x:axis('x'),y:axis('y')};
}
export function spiderPosition(route,progress,aspect=16/9) {
  if(route.length<3)return null;
  const distances=route.map((p,i)=>{
    const q=route[(i+1)%route.length];
    return Math.hypot((q.x-p.x)*aspect,q.y-p.y);
  });
  const total=distances.reduce((a,b)=>a+b,0);
  if(total<1e-6)return null;
  let remaining=(((progress%1)+1)%1)*total;
  for(let i=0;i<route.length;i++){
    if(remaining<=distances[i] || i===route.length-1)
      return spline(route,i,Math.min(1,remaining/Math.max(1e-8,distances[i])));
    remaining-=distances[i];
  }
  return route[0];
}
/** La fotografía fuente mira hacia abajo: su frente es el vector local (0,1). */
export function spiderHeading(dx,dy) {
  return Math.atan2(dy,dx)-Math.PI/2;
}
/** Articula ocho sectores periféricos del propio recorte fotográfico. */
export function spiderGaitFrame(image,canvas,phase) {
  if(!image?.naturalWidth || !image?.naturalHeight)return false;
  const ctx=canvas.getContext('2d');
  const width=image.naturalWidth,height=image.naturalHeight,dimension=176;
  if(canvas.width!==dimension || canvas.height!==dimension){
    canvas.width=dimension;canvas.height=dimension;
  }
  ctx.clearRect(0,0,dimension,dimension);
  const fit=dimension/Math.max(width,height);
  const left=(dimension-width*fit)/2,top=(dimension-height*fit)/2;
  const x=left+width*fit*.5,y=top+height*fit*.5;
  for(let leg=0;leg<8;leg++){
    const angle=(leg+.5)*Math.PI/4-Math.PI/2;
    ctx.save();ctx.beginPath();ctx.moveTo(x,y);
    ctx.arc(x,y,dimension*1.6,angle-Math.PI/8-.012,angle+Math.PI/8+.012);
    ctx.closePath();ctx.clip();ctx.translate(x,y);
    ctx.rotate(Math.sin(phase+(leg%2)*Math.PI+Math.floor(leg/2)*.6)*.085);
    ctx.drawImage(image,-width*fit/2,-height*fit/2,width*fit,height*fit);
    ctx.restore();
  }
  ctx.save();ctx.beginPath();
  ctx.ellipse(x,y,width*fit*.20,height*fit*.30,0,0,Math.PI*2);ctx.clip();
  ctx.drawImage(image,left,top,width*fit,height*fit);
  ctx.restore();
  return true;
}
