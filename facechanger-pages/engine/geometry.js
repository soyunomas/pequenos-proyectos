/** Mirror-space, top-left coordinates. IDs follow MediaPipe Face Landmarker topology. */
export const MAX_CONTROLS = 32;
export const LANDMARK = Object.freeze({
  noseTip:1, noseBridge:4, noseLowerBridge:4, noseBase:2, noseA:98, noseB:327, nostrilA:98, nostrilB:327,
  eyeAOuter:33, eyeAInner:133, eyeATop:159, eyeABottom:145,
  eyeBOuter:263, eyeBInner:362, eyeBTop:386, eyeBBottom:374,
  browAOuter:70, browAInner:107, browAMiddle:105,
  browBOuter:300, browBInner:336, browBMiddle:334,
  mouthA:61, mouthB:291, mouthTop:0, mouthBottom:17, mouthTopOuter:0, mouthBottomOuter:17,
  mouthTopInner:13, mouthBottomInner:14,
  cheekA:234, cheekB:454, cheekAInner:205, cheekBInner:425,
  jawA:172, jawB:397, jawAHigh:136, jawBHigh:365,
  templeA:127, templeB:356, forehead:10, foreheadA:109, foreheadB:338,
  chin:152
});
export const clamp=(v,min,max)=>Math.max(min,Math.min(max,v));
export const distance=(a,b)=>Math.hypot(a.x-b.x,a.y-b.y);
export function faceFromLandmarks(raw) {
  if (!raw || raw.length < 455) return null;
  const landmarks=raw.map(p=>({x:1-p.x,y:p.y}));
  const {cheekA,cheekB,eyeAOuter,eyeBOuter}=LANDMARK;
  const width=distance(landmarks[cheekA],landmarks[cheekB]);
  if(!Number.isFinite(width)||width<.04)return null;
  const a=landmarks[eyeAOuter],b=landmarks[eyeBOuter];
  const left=a.x<=b.x?a:b,right=a.x<=b.x?b:a;
  // Always +X = screen-right: a mirrored camera must never turn local DOWN into UP.
  return {landmarks,frame:{width,angle:Math.atan2(right.y-left.y,right.x-left.x)}};
}
export function toLocal(v,face) {
  const {angle,width}=face.frame,c=Math.cos(angle),s=Math.sin(angle);
  return {x:(v.x*c+v.y*s)/width,y:(v.y*c-v.x*s)/width};
}
export function toWorld(v,face) {
  const {angle,width}=face.frame,c=Math.cos(angle),s=Math.sin(angle);
  return {x:(v.x*c-v.y*s)*width,y:(v.x*s+v.y*c)*width};
}
const midpoint=(face,indices)=>{
  const pts=(Array.isArray(indices)?indices:[indices]).map(i=>face.landmarks[i]);
  return {x:pts.reduce((sum,p)=>sum+p.x,0)/pts.length,y:pts.reduce((sum,p)=>sum+p.y,0)/pts.length};
};
const l=(face,idx,dx,dy)=>({center:midpoint(face,idx),offset:toWorld({x:dx,y:dy},face)});
const ctl=(face,idx,radius,dx=0,dy=0,scale=0,shapeY=1,scaleY=scale)=>{
  const p=l(face,idx,dx,dy);
  return {x:p.center.x,y:p.center.y,dx:p.offset.x,dy:p.offset.y,
    radius:radius*face.frame.width,shapeY,scale,scaleY};
};
const eyeA=[33,133,159,145],eyeB=[263,362,386,374];
const browsA=[70,105,107],browsB=[300,334,336];
const lips=[0,17,13,14];
const midX=(face)=>midpoint(face,[LANDMARK.cheekA,LANDMARK.cheekB]).x;
const signX=(face,id)=>Math.sign(midpoint(face,id).x-midX(face))||1;
const symmetric=(face,ids,r,dx,dy=0,scale=0,shapeY=1,scaleY=scale)=>
  ids.map(id=>ctl(face,id,r,dx*signX(face,id),dy,scale,shapeY,scaleY));
export const FILTER_GROUPS=[
  ['Bichos en la cara', [
    ['spider','Arañas por la cara'],['cockroach','Cucarachas por la cara'],
    ['wasp','Avispas por la cara']
  ]],
  ['Básico', [['normal','Normal']]],
  ['Accesorios realistas IA', [
    ['glasses-classic','Gafas clásicas'],['eyepatch-black','Parche ocular de cuero'],
    ['mask-lace','Máscara veneciana'],
    ['mustache-handlebar','Bigote realista'],['beard-full','Barba completa'],
    ['grillz-gold','Grillz dorados']
  ]],
  ['Ojos y cejas', [
    ['eyes-big','Ojos grandes'],['eyes-small','Ojos pequeños'],
    ['eyes-apart','Ojos separados'],['eyes-together','Ojos juntos'],
    ['eyes-alien','Ojos de alienígena'],['eye-droop','Ojo caído'],
    ['brows-up','Cejas levantadas'],['brows-angry','Cejas enfadadas'],
    ['eyes-droop','Ambos ojos caídos'],['eyes-uneven','Mirada asimétrica'],
    ['eyes-squint','Mirada entrecerrada'],['brows-uneven','Cejas asimétricas'],
    ['brows-down','Cejas bajas']
  ]],
  ['Nariz', [
    ['nose-big','Nariz grande'],['nose-small','Nariz pequeña'],
    ['nose-twisted','Nariz torcida'],['nose-long','Nariz larga'],
    ['nose-pinocchio','Nariz de Pinocho'],['nose-pig','Nariz de cerdito'],
    ['nose-thin','Nariz fina']
  ]],
  ['Boca', [
    ['mouth-big','Boca grande'],['mouth-small','Boca pequeña'],
    ['mouth-twisted','Boca torcida'],['mouth-smile','Sonrisa gigante'],
    ['mouth-sad','Boca triste'],['lips-big','Labios grandes'],
    ['mouth-fish','Boca de pez'],['mouth-droop','Boca caída'],['mouth-uneven','Media sonrisa']
  ]],
  ['Rostro', [
    ['face-long','Cara alargada'],['face-round','Cara redonda'],
    ['face-thin','Cara estrecha'],['face-egg','Cabeza de huevo'],
    ['chin-big','Barbilla gigante'],['forehead-big','Frente gigante'],
    ['face-square','Cara cuadrada'],['cheeks-hamster','Mejillas de hámster'],
    ['face-alien','Cara de alienígena'],['cheeks-hollow','Mejillas hundidas'],
    ['chin-small','Barbilla pequeña']
  ]],
  ['Inspirados en tendencias', [
    ['trend-baby','Carita de bebé'],['trend-cry','Llanto dramático'],
    ['trend-surprise','Sorpresa total'],['trend-doll','Mirada de muñeca'],
    ['trend-soft','Rasgos suaves'],['trend-cartoon','Caricatura disparatada'],
    ['trend-hero','Mandíbula de cómic'],['trend-pout','Puchero exagerado'],
    ['trend-bighead','Cabezón de caricatura'],['trend-squash','Cara aplastada']
  ]],
  ['Efectos combinados', [
    ['uncanny-droop','Extrañeza sutil · ojo y boca caídos'],
    ['uncanny-uneven','Extrañeza asimétrica · mirada y sonrisa'],
    ['uncanny-tired','Cansancio inquietante · ojos y expresión'],
    ['uncanny-skeptic','Escepticismo sutil · cejas y boca']
  ]]
];
export const FILTERS=FILTER_GROUPS.flatMap(([,items])=>items);
export const FILTER_IDS=new Set(FILTERS.map(([id])=>id));
export function presetControls(face,name) {
  if (!face || !FILTER_IDS.has(name)) return [];
  const L=LANDMARK;
  const eyes=(r,scale,shapeY=.78,scaleY=scale)=>[
    ctl(face,eyeA,r,0,0,scale,shapeY,scaleY),
    ctl(face,eyeB,r,0,0,scale,shapeY,scaleY)
  ];
  const mouth=()=>midpoint(face,lips);
  const corners=(r,dx,dy=0,s=0,y=.84)=>
    symmetric(face,[L.mouthA,L.mouthB],r,dx,dy,s,y);
  switch(name) {
    case 'normal':return [];
    case 'eyes-big':return eyes(.29,.95,.72);
    case 'eyes-small':return eyes(.32,-.5,.77);
    case 'eyes-apart':return symmetric(face,[eyeA,eyeB],.32,.095,0,0,.86);
    case 'eyes-together':return symmetric(face,[eyeA,eyeB],.32,-.09,0,0,.86);
    case 'eyes-alien':return eyes(.34,.52,.92,1.13);
    // Eye DROP moves the whole source-eye patch with an invertible field:
    // gentle displacement / broad region, NOT a duplicate of the original eye.
    case 'eye-droop':return [ctl(face,eyeA,.32,0,.105,0,.83)];
    case 'eyes-droop':return [ctl(face,eyeA,.32,0,.078,0,.83),ctl(face,eyeB,.32,0,.078,0,.83)];
    case 'eyes-uneven':return [ctl(face,eyeA,.29,0,.05,0,.83),ctl(face,eyeB,.29,0,-.025,0,.83)];
    case 'eyes-squint':return eyes(.31,-.055,.69,-.32);
    case 'brows-uneven':return [ctl(face,browsA,.20,0,-.055,0,.7),ctl(face,browsB,.20,0,.025,0,.7)];
    case 'brows-down':return [ctl(face,browsA,.20,0,.045,0,.72),ctl(face,browsB,.20,0,.045,0,.72)];
    case 'brows-up':return [ctl(face,browsA,.19,0,-.065,0,.69),
      ctl(face,browsB,.19,0,-.065,0,.69)];
    case 'brows-angry':return [
      ctl(face,L.browAInner,.155,0,.065),ctl(face,L.browBInner,.155,0,.065),
      ctl(face,L.browAOuter,.15,0,-.025),ctl(face,L.browBOuter,.15,0,-.025)
    ];
    case 'nose-big':return [
      ctl(face,[L.noseTip,L.noseBase],.135,0,0,.34,.72),
      ctl(face,L.noseA,.105,0,0,.22,.68),ctl(face,L.noseB,.105,0,0,.22,.68)
    ];
    case 'nose-small':return [ctl(face,[L.noseTip,L.noseBase,L.noseA,L.noseB],.22,0,0,-.52,.85)];
    case 'nose-twisted':return [ctl(face,[L.noseTip,L.noseBridge],.29,.11,0,0,.82)];
    case 'nose-long':return [ctl(face,[L.noseTip,L.noseBase],.28,0,.105,0,.82)];
    case 'nose-pinocchio':return [ctl(face,[L.noseTip,L.noseBase],.36,0,.165,0,.85)];
    case 'nose-pig':return [
      ctl(face,L.noseTip,.21,0,-.055,0,.8),
      ...symmetric(face,[L.noseA,L.noseB],.15,.047,-.035,.12,.85)
    ];
    case 'nose-thin':return symmetric(face,[L.noseA,L.noseB],.18,-.045,0,-.12,.85);
    case 'mouth-big':return [
      ctl(face,lips,.35,0,0,.86,.64),...corners(.16,.065)
    ];
    case 'mouth-small':return [ctl(face,lips.concat([L.mouthA,L.mouthB]),.34,0,0,-.55,.71)];
    case 'mouth-twisted':return [ctl(face,L.mouthA,.28,.095,.028,0,.82)];
    case 'mouth-smile':return [...corners(.28,.11,-.065),ctl(face,L.mouthTop,.20,0,-.02,0,.85)];
    case 'mouth-sad':return corners(.25,0,.082);
    case 'mouth-droop':return [...corners(.25,0,.055),ctl(face,lips,.26,0,.042,0,.79)];
    case 'mouth-uneven':return [ctl(face,L.mouthA,.23,0,-.05,0,.8),ctl(face,L.mouthB,.23,0,.018,0,.8)];
    case 'lips-big':return [
      ctl(face,lips,.23,0,0,.48,.74,.95),
      ctl(face,L.mouthTop,.12,0,-.015,.18,.76),
      ctl(face,L.mouthBottom,.12,0,.015,.18,.76)
    ];
    case 'mouth-fish':return [
      ...corners(.27,-.075,0),
      ctl(face,lips,.23,0,0,.42,.76,.75)
    ];
    case 'face-long':return [ctl(face,L.chin,.43,0,.16),ctl(face,L.forehead,.38,0,-.07)];
    case 'face-round':return [
      ...symmetric(face,[L.cheekAInner,L.cheekBInner],.34,.085,0,.18),
      ctl(face,L.chin,.3,0,-.05)
    ];
    case 'face-thin':return symmetric(face,[L.cheekAInner,L.cheekBInner],.38,-.085,0,-.08);
    case 'face-egg':return [
      ...symmetric(face,[L.templeA,L.templeB],.38,.095,0,.12),
      ...symmetric(face,[L.jawA,L.jawB],.37,-.085,0,-.08)
    ];
    case 'chin-big':return [ctl(face,L.chin,.41,0,.15, .36)];
    case 'forehead-big':return [
      ctl(face,L.forehead,.38,0,-.115,.22),
      ...symmetric(face,[L.foreheadA,L.foreheadB],.28,.055,-.03,.12)
    ];
    case 'face-square':return [
      ...symmetric(face,[L.jawAHigh,L.jawBHigh],.34,.095,0,.15),
      ...symmetric(face,[L.jawA,L.jawB],.29,.07,0,.08)
    ];
    case 'cheeks-hamster':return [
      ...symmetric(face,[L.cheekAInner,L.cheekBInner],.29,.055,0,.62)
    ];
    case 'uncanny-droop':return [...presetControls(face,'eye-droop'),...presetControls(face,'mouth-droop')];
    case 'uncanny-uneven':return [...presetControls(face,'eyes-uneven'),...presetControls(face,'mouth-uneven')];
    // Presets originales basados en tendencias de deformación y expresiones faciales.
    // Todos usan píxeles de webcam, sin assets/modelos de TikTok.
    case 'trend-baby':return [
      ...eyes(.29,.36,.78,.44),
      ctl(face,[L.noseTip,L.noseBase,L.noseA,L.noseB],.20,0,0,-.28,.83),
      ...symmetric(face,[L.cheekAInner,L.cheekBInner],.30,.035,0,.13),
      ctl(face,L.chin,.32,0,-.045,-.12),
      ctl(face,lips.concat([L.mouthA,L.mouthB]),.26,0,0,-.16,.82)
    ];
    case 'trend-cry':return [
      ...eyes(.28,.12,.77,.20),
      ctl(face,L.browAInner,.16,0,-.055,0,.70),
      ctl(face,L.browBInner,.16,0,-.055,0,.70),
      ctl(face,L.browAOuter,.15,0,.022,0,.71),
      ctl(face,L.browBOuter,.15,0,.022,0,.71),
      ...corners(.27,0,.092),
      ctl(face,lips,.24,0,.037,0,.86)
    ];
    case 'trend-surprise':return [
      ...eyes(.30,.22,.78,.47),
      ...presetControls(face,'brows-up'),
      ctl(face,lips.concat([L.mouthA,L.mouthB]),.30,0,0,-.22,.85,.73),
      ctl(face,L.mouthBottom,.17,0,.034,0,.78)
    ];
    case 'trend-doll':return [
      ...eyes(.31,.44,.79,.51),
      ctl(face,[L.noseTip,L.noseBase,L.noseA,L.noseB],.20,0,0,-.32,.85),
      ctl(face,lips,.21,0,0,-.16,.78,.20),
      ctl(face,L.chin,.30,0,-.035,-.10)
    ];
    case 'trend-soft':return [
      ...eyes(.29,.16,.78,.18),
      ...symmetric(face,[L.cheekAInner,L.cheekBInner],.31,-.023,0,-.035),
      ctl(face,[L.noseTip,L.noseBase,L.noseA,L.noseB],.18,0,0,-.20,.85),
      ctl(face,lips,.20,0,0,.12,.77,.24),
      ctl(face,browsA,.19,0,-.022,0,.74),
      ctl(face,browsB,.19,0,-.022,0,.74)
    ];
    case 'trend-cartoon':return [
      ...eyes(.30,.68,.77,.82),
      ctl(face,[L.noseTip,L.noseBase,L.noseA,L.noseB],.22,0,0,-.46,.85),
      ctl(face,lips,.31,0,0,.63,.75,.58),
      ...corners(.20,.052,-.025),
      ctl(face,L.chin,.33,0,-.048,-.11)
    ];
    case 'trend-hero':return [
      ...symmetric(face,[L.jawAHigh,L.jawBHigh],.34,.073,0,.11),
      ...symmetric(face,[L.jawA,L.jawB],.32,.066,0,.08),
      ...symmetric(face,[L.cheekAInner,L.cheekBInner],.32,-.036,0,-.055),
      ctl(face,L.chin,.36,0,.089,.18),
      ...presetControls(face,'brows-down')
    ];
    case 'trend-pout':return [
      ctl(face,lips,.23,0,0,-.16,.79,.42),
      ...corners(.24,-.042,.043),
      ctl(face,L.mouthBottom,.17,0,.027,.08,.79),
      ctl(face,L.browAInner,.14,0,-.03,0,.74),
      ctl(face,L.browBInner,.14,0,-.03,0,.74)
    ];
    case 'trend-bighead':return [
      ctl(face,L.forehead,.39,0,-.102,.29),
      ...symmetric(face,[L.foreheadA,L.foreheadB],.31,.073,-.023,.13),
      ...symmetric(face,[L.jawA,L.jawB],.34,-.055,0,-.06),
      ...presetControls(face,'brows-angry')
    ];
    case 'trend-squash':return [
      ctl(face,L.forehead,.36,0,.074,0,.89),
      ctl(face,L.chin,.36,0,-.078,0,.88),
      ...symmetric(face,[L.cheekAInner,L.cheekBInner],.33,.035,0,.08),
      ...eyes(.28,.18,.76,.14),
      ctl(face,lips,.27,0,0,.17,.79,-.13)
    ];
    case 'spider':case 'cockroach':case 'wasp':return [];
    case 'cheeks-hollow':return symmetric(face,[L.cheekAInner,L.cheekBInner],.32,-.05,0,-.12);
    case 'chin-small':return [ctl(face,L.chin,.34,0,-.04,-.2)];
    case 'uncanny-tired':return [...presetControls(face,'eyes-squint'),...presetControls(face,'brows-down'),...presetControls(face,'mouth-droop')];
    case 'uncanny-skeptic':return [...presetControls(face,'brows-uneven'),...presetControls(face,'mouth-uneven')];
    case 'face-alien':return [
      ...eyes(.3,.52,.83,.8),
      ...symmetric(face,[L.templeA,L.templeB],.36,.075),
      ...symmetric(face,[L.jawA,L.jawB],.34,-.065),
      ctl(face,L.chin,.26,0,-.055)
    ];
    default:return [];
  }
}
/** Up to four independently adjustable supplementary presets. */
export function normalizeEffects(effects) {
  if (!Array.isArray(effects)) return [];
  const seen=new Set();
  return effects.filter(e=>{
    if(!e || !FILTER_IDS.has(e.preset) || e.preset==='normal' ||
       !Number.isFinite(e.intensity) || e.intensity<0 || e.intensity>100 ||
       seen.has(e.preset))return false;
    seen.add(e.preset);return true;
  }).slice(0,4).map(e=>({preset:e.preset,intensity:e.intensity}));
}
export function controlFromStroke(stroke,face) {
  const p=face.landmarks[stroke.landmark];
  if(!p)return null;
  const offset=toWorld(stroke.offset,face),delta=toWorld(stroke.delta,face);
  // A very long drag used to exceed the field support and leave a second eye.
  // Grow its support, then clamp translation to keep the warp one-to-one.
  const r=Math.min(.5,Math.max(stroke.radius,Math.hypot(stroke.delta.x,stroke.delta.y)*2.55));
  const radius=r*face.frame.width;
  const max=.38*radius;
  return {x:p.x+offset.x,y:p.y+offset.y,
    dx:clamp(delta.x,-max,max),
    dy:clamp(delta.y,-max,max*1.45),
    radius,shapeY:1,scale:stroke.scale||0,scaleY:stroke.scale||0};
}
export function compose(face,preset,strokes=[],intensity=100,effects=[]) {
  if(!face || intensity<=0)return [];
  const factor=clamp(intensity/100,0,1);
  const extra=normalizeEffects(effects).flatMap(e=>presetControls(face,e.preset)
    .map(c=>({...c,dx:c.dx*e.intensity/100,dy:c.dy*e.intensity/100,
      scale:c.scale*e.intensity/100,scaleY:c.scaleY*e.intensity/100})));
  return [...presetControls(face,preset),...extra,
    ...strokes.map(s=>controlFromStroke(s,face)).filter(Boolean)]
    .slice(-MAX_CONTROLS).map(c=>({...c,dx:c.dx*factor,dy:c.dy*factor,
      scale:c.scale*factor,scaleY:c.scaleY*factor}));
}
/** Finite support identical to GLSL, with corrected video aspect ratio. */
export function influenceAt(p,c,aspect=16/9) {
  const r=Math.max(1e-5,c.radius*aspect);
  const dx=(p.x-c.x)*aspect/r,dy=(p.y-c.y)/(r*(c.shapeY||1));
  const d2=dx*dx+dy*dy;
  return d2<1?(1-d2)**3:0;
}
export function forwardOne(p,c,aspect=16/9) {
  const w=influenceAt(p,c,aspect),sx=1+(c.scale||0)*w,sy=1+(c.scaleY??c.scale??0)*w;
  return {x:c.x+(p.x-c.x)*sx+c.dx*w,
    y:c.y+(p.y-c.y)*sy+c.dy*w};
}
/** Apply the same forward controls to face-anchored overlays as to video pixels. */
export function forwardWarp(point,controls=[],aspect=16/9) {
  return controls.reduce((p,c)=>forwardOne(p,c,aspect),point);
}
/** Inverts SOURCE-anchored forward field: the original feature is moved, not overlaid. */
export function inverseOne(destination,c,aspect=16/9) {
  const w=influenceAt(destination,c,aspect);
  let x=c.x+(destination.x-c.x)/(1+(c.scale||0)*w)-c.dx*w;
  let y=c.y+(destination.y-c.y)/(1+(c.scaleY??c.scale??0)*w)-c.dy*w;
  for(let j=0;j<7;j++){
    const f=forwardOne({x,y},c,aspect);
    x+=.72*(destination.x-f.x);
    y+=.72*(destination.y-f.y);
  }
  return {x,y};
}
export function inverseWarp(p,controls,aspect=16/9) {
  let out=p;
  for(let i=controls.length-1;i>=0;i--)out=inverseOne(out,controls[i],aspect);
  return out;
}
export function makeStroke(face,visiblePoint,radius,controls=[],aspect=16/9) {
  if(!face)return null;
  const p=inverseWarp(visiblePoint,controls,aspect);
  let closest=-1,best=Infinity;
  face.landmarks.forEach((candidate,i)=>{
    const d=Math.hypot((candidate.x-p.x)*aspect,candidate.y-p.y);
    if(d<best){closest=i;best=d;}
  });
  if(closest<0||best>face.frame.width*aspect*.27)return null;
  const landmark=face.landmarks[closest];
  return {landmark:closest,offset:toLocal({x:p.x-landmark.x,y:p.y-landmark.y},face),
    delta:{x:0,y:0},radius:clamp(radius,.06,.38),scale:0};
}
