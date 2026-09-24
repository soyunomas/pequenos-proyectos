import { creatureConfig,activeCreatures,localLighting,creatureHeading,CREATURE_DEFAULTS } from '../engine/creatures.js';
import { spiderRoute,spiderPosition,spiderConfig,spiderHeading } from '../engine/spider.js';
import test from 'node:test';
import assert from 'node:assert/strict';
import { faceFromLandmarks, makeStroke, compose, toLocal, toWorld, inverseWarp, MAX_CONTROLS, LANDMARK, FILTERS, influenceAt, presetControls, normalizeEffects, forwardOne, forwardWarp } from '../engine/geometry.js';
import { PointerDeformer, mapPointer } from '../engine/pointer.js';
import { listFilters, saveFilter, removeFilter, validFilter } from '../engine/storage.js';
const raw = Array.from({length:478},()=>({x:.5,y:.5}));
raw[234]={x:.7,y:.52};raw[454]={x:.3,y:.52};raw[33]={x:.35,y:.4};raw[263]={x:.65,y:.4};raw[1]={x:.5,y:.53};raw[4]={x:.5,y:.6};
const face=()=>faceFromLandmarks(raw);
test('landmarks especulares',()=> assert.ok(Math.abs(face().landmarks[234].x-.3)<1e-9));
test('object-fit cover',()=>{
 const p=mapPointer({clientX:200,clientY:350},{left:20,top:30,width:360,height:640},1280,720);
 assert.ok(Math.abs(p.x-.5)<1e-9&&Math.abs(p.y-.5)<1e-9);
 assert.ok(mapPointer({clientX:20,clientY:30},{left:20,top:30,width:360,height:640},1280,720).x>.3);
 assert.equal(mapPointer({clientX:200,clientY:350},{left:20,top:30,width:360,height:640},0,720),null);
});
test('base rotada reversible',()=>{
 const f=face();f.frame.angle=.4;const v={x:.11,y:-.05},p=toWorld(toLocal(v,f),f);
 assert.ok(Math.abs(v.x-p.x)<1e-9&&Math.abs(v.y-p.y)<1e-9);
});
test('gesto activo y persistente',()=>{
 const f=face(),g=new PointerDeformer();assert.ok(g.begin(4,{x:.5,y:.53},f,.19,[],16/9));
 assert.equal(g.move(6,{x:.6,y:.53}),null);assert.ok(g.move(4,{x:.54,y:.53}).delta.x>0);
 const s=g.end(4);assert.ok(s&&s.landmark>=0);
 const f2=face();f2.landmarks=f2.landmarks.map(p=>({x:p.x+.05,y:p.y+.05}));
 const a=compose(f,'normal',[s],100)[0],b=compose(f2,'normal',[s],100)[0];
 assert.ok(Math.abs(b.x-a.x-.05)<1e-9&&Math.abs(b.y-a.y-.05)<1e-9);
});
test('cancelación descarta gesto',()=>{
 const g=new PointerDeformer();assert.ok(g.begin(7,{x:.5,y:.53},face(),.19,[],16/9));
 g.move(7,{x:.55,y:.53});assert.equal(g.end(7,false),null);
});
test('toque sobre nariz previamente deformada',()=>{
 const f=face(),c=compose(f,'nose-twisted',[],100);
 const visible={x:f.landmarks[1].x+c[0].dx,y:f.landmarks[1].y};
 assert.ok(Math.abs(inverseWarp(visible,c,16/9).x-f.landmarks[1].x)<.025);
 assert.ok(makeStroke(f,visible,.19,c,16/9));
});
test('filtros combinables e intensidad',()=>{
 const f=face(),s=makeStroke(f,{x:.5,y:.53},.19);
 s.delta={x:.1,y:.05};
 assert.equal(compose(f,'nose-big',[s],100).length,4);
 assert.equal(compose(f,'nose-big',[s],0).length,0);
 assert.ok(compose(f,'nose-big',[s],50).at(-1).dx>0);
 assert.ok(compose(f,'nose-big',[s],50).at(-1).dx<compose(f,'nose-big',[s],100).at(-1).dx);
 assert.ok(MAX_CONTROLS>=8);
});
test('validación guardado y eliminación JSON',()=>{
 const data=new Map();const st={getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v)};
 const f={version:1,name:'Prueba',preset:'normal',intensity:100,radius:.19,strokes:[]};
 saveFilter(f,st);assert.equal(listFilters(st).length,1);
 saveFilter({...f,intensity:80},st);assert.equal(listFilters(st).length,1);
 removeFilter('Prueba',st);assert.equal(listFilters(st).length,0);
 assert.throws(()=>saveFilter({...f,intensity:Infinity},st));
});

function anatomy() {
  const raw = Array.from({length:478},()=>({x:.5,y:.5}));
  const set=(i,x,y)=>{raw[i]={x:1-x,y};};
  const L=LANDMARK;
  for(const [i,x,y] of [
    [L.cheekA,.3,.53],[L.cheekB,.7,.53],[L.noseTip,.5,.57],[L.noseBase,.5,.61],
    [L.noseLowerBridge,.5,.51],[L.nostrilA,.47,.60],[L.nostrilB,.53,.60],
    [L.eyeAOuter,.35,.4],[L.eyeAInner,.46,.4],[L.eyeATop,.405,.38],[L.eyeABottom,.405,.42],
    [L.eyeBOuter,.65,.4],[L.eyeBInner,.54,.4],[L.eyeBTop,.595,.38],[L.eyeBBottom,.595,.42],
    [L.mouthA,.43,.74],[L.mouthB,.57,.74],[L.mouthTopOuter,.5,.72],[L.mouthBottomOuter,.5,.76],[L.mouthTopInner,.5,.73],[L.mouthBottomInner,.5,.75],[L.chin,.5,.86],[L.forehead,.5,.16]
  ])set(i,x,y);
  return faceFromLandmarks(raw);
}
test('puntos anatómicos de nariz, ojos y boca separados',()=>{
  const f=anatomy(),L=LANDMARK;
  assert.ok(f.landmarks[L.noseTip].y>f.landmarks[L.eyeATop].y);
  assert.ok(f.landmarks[L.noseTip].y<f.landmarks[L.mouthA].y);
  assert.ok(Math.abs(f.landmarks[L.nostrilA].x-f.landmarks[L.nostrilB].x)>.05);
});
test('nariz grande NO afecta a ningún párpado ni comisura',()=>{
  const f=anatomy(),L=LANDMARK,c=presetControls(f,'nose-big');
  assert.equal(c.length,3);
  for(const i of [L.eyeAOuter,L.eyeAInner,L.eyeATop,L.eyeABottom,L.eyeBOuter,L.eyeBInner,L.eyeBTop,L.eyeBBottom,L.mouthA,L.mouthB])
    assert.equal(c.reduce((sum,ctl)=>sum+influenceAt(f.landmarks[i],ctl),0),0,'punto contaminado '+i);
  assert.ok(c.some(ctl=>influenceAt(f.landmarks[L.noseTip],ctl)>.01));
});
test('ojos grandes centrados en párpados estables y no en iris',()=>{
  const f=anatomy(),cs=presetControls(f,'eyes-big');
  const L=LANDMARK;
  const average=indices=>indices.reduce((sum,i)=>sum+f.landmarks[i].x,0)/indices.length;
  assert.ok(Math.abs(cs[0].x-average([L.eyeAOuter,L.eyeAInner,L.eyeATop,L.eyeABottom]))<1e-8);
  assert.ok(Math.abs(cs[1].x-average([L.eyeBOuter,L.eyeBInner,L.eyeBTop,L.eyeBBottom]))<1e-8);
  assert.deepEqual(presetControls({...f,landmarks:f.landmarks.slice(0,468)},'eyes-big'),cs);
});
test('todos los presets producen controles finitos dentro de la malla',()=>{
  for(const [name] of [...FILTERS.map(([id])=>[id])])
    for(const c of presetControls(anatomy(),name)) {
      for(const key of ['x','y','dx','dy','radius','shapeY','scale'])assert.ok(Number.isFinite(c[key]),name+' '+key);
      assert.ok(c.radius>0&&c.shapeY>0);
    }
});
test('caída compacta: píxeles lejanos intactos',()=>{
  const c={x:.5,y:.5,dx:0,dy:0,radius:.05,shapeY:.7,scale:.3};
  assert.equal(influenceAt({x:.8,y:.5},c),0);
  assert.equal(influenceAt({x:.5,y:.7},c),0);
  assert.deepEqual(inverseWarp({x:.8,y:.5},[c]),{x:.8,y:.5});
});

test('ojos grandes: intensidad visible también en el contorno, sin modificar nariz',()=>{
  const f=anatomy(),L=LANDMARK,eyes=presetControls(f,'eyes-big');
  assert.equal(eyes.length,2);
  const edge=f.landmarks[L.eyeAOuter],nose=f.landmarks[L.noseTip];
  const expansion=1+eyes[0].scale*influenceAt(edge,eyes[0]);
  assert.ok(expansion>1.35,'expansión insuficiente en comisura ocular: '+expansion);
  assert.ok(eyes.every(c=>influenceAt(nose,c)===0));
  assert.ok(compose(f,'eyes-big',[],50)[0].scale<compose(f,'eyes-big',[],100)[0].scale);
});
test('boca grande agranda labios y separa comisuras sin alterar la nariz',()=>{
  const f=anatomy(),L=LANDMARK,cs=presetControls(f,'mouth-big');
  assert.equal(cs.length,3);
  const lip=f.landmarks[L.mouthTopOuter];
  assert.ok(1+cs[0].scale*influenceAt(lip,cs[0])>1.5);
  assert.ok(cs[1].dx*cs[2].dx<0);
  for(const c of cs)assert.equal(influenceAt(f.landmarks[L.noseTip],c),0);
  assert.equal(compose(f,'mouth-big',[],0).length,0);
  assert.equal(compose(f,'mouth-big',[],100).length,3);
});
test('boca grande se admite en JSON guardado sin modificar filtros previos',()=>{
  const data=new Map(),storage={getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v)};
  const filter={version:1,name:'Labios',preset:'mouth-big',intensity:80,radius:.19,strokes:[]};
  saveFilter(filter,storage);assert.deepEqual(listFilters(storage),[filter]);
});

test('catálogo con 58 opciones y filtros de tendencia únicos',()=>{
  const ids=FILTERS.map(([id])=>id);
  assert.equal(ids.length,58);
  assert.equal(new Set(ids).size,ids.length);
  assert.equal(ids[0],'normal');
  for(const id of ids.slice(1).filter(id=>!['spider','cockroach','wasp'].includes(id)))assert.ok(presetControls(anatomy(),id).length>0,id);
});
test('ojo caído: el ojo fuente solo ocupa su destino, no queda duplicado',()=>{
  const f=anatomy(),c=presetControls(f,'eye-droop')[0];
  const origin={x:c.x,y:c.y},destination=forwardOne(origin,c);
  assert.ok(Math.hypot(destination.x-origin.x,destination.y-origin.y)>.025*f.frame.width);
  const sourceAtDestination=inverseWarp(destination,[c]);
  const sourceAtOrigin=inverseWarp(origin,[c]);
  assert.ok(Math.hypot(sourceAtDestination.x-origin.x,sourceAtDestination.y-origin.y)<.0006,
    'el ojo desplazado no muestra el ojo fuente');
  assert.ok(Math.hypot(sourceAtOrigin.x-origin.x,sourceAtOrigin.y-origin.y)>.012,
    'el ojo original sigue visible sin rellenarse');
  assert.ok(Math.abs(c.dy)<c.radius*.4,'el desplazamiento rompe la máscara');
});
test('mapeo del dedo conserva coordenadas cuando el canvas tiene zoom 2x',()=>{
  const normal=mapPointer({clientX:200,clientY:300},
    {left:0,top:100,width:400,height:400},1280,720);
  const zoom=mapPointer({clientX:200,clientY:300},
    {left:-200,top:-100,width:800,height:800},1280,720);
  assert.ok(Math.abs(normal.x-.5)<1e-8&&Math.abs(zoom.x-.5)<1e-8);
  assert.ok(Math.abs(normal.y-.5)<1e-8&&Math.abs(zoom.y-.5)<1e-8);
});
test('mezclas limitadas a cuatro, con intensidades independientes',()=>{
  const f=anatomy();
  const extra=[{preset:'nose-small',intensity:20},{preset:'eyes-big',intensity:80}];
  assert.equal(normalizeEffects([...extra,...extra]).length,2);
  const a=compose(f,'normal',[],100,extra);
  const b=compose(f,'normal',[],50,extra);
  assert.ok(a.length===3);
  assert.ok(a[0].scale<0 && a[1].scale>0);
  assert.ok(Math.abs(b[0].scale-a[0].scale*.5)<1e-8);
});
test('guardar y recuperar mezcla; compatibilidad con filtros antiguos',()=>{
  const data=new Map(),store={getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v)};
  const old={version:1,name:'Anterior',preset:'normal',intensity:60,radius:.19,strokes:[]};
  const newer={...old,name:'Combinado',effects:[{preset:'eyes-small',intensity:75}]};
  saveFilter(old,store);saveFilter(newer,store);
  assert.equal(listFilters(store).length,2);
  assert.deepEqual(listFilters(store)[1].effects,newer.effects);
});

test('ojo caído siempre desciende, también con orden especular de landmarks',()=>{
  const f=anatomy(),c=presetControls(f,'eye-droop')[0];
  assert.ok(c.dy>0,'el desplazamiento debe ir hacia abajo');
  const raw=Array.from({length:478},()=>({x:.5,y:.5}));
  raw[234]={x:.7,y:.5};raw[454]={x:.3,y:.5};
  raw[33]={x:.35,y:.4};raw[263]={x:.65,y:.4};
  const mirrored=faceFromLandmarks(raw);
  assert.ok(presetControls(mirrored,'eye-droop')[0].dy>0);
});

test('presets inquietantes combinan caída ocular y boca sin exceso a 40 %',()=>{
 const f=anatomy(), controls=compose(f,'uncanny-droop',[],39);
 assert.equal(controls.length,4);
 assert.ok(controls[0].dy>0 && controls[1].dy>0);
 assert.ok(controls.every(c=>c.dy < .06*f.frame.width));
 assert.equal(compose(f,'spider',[],100).length,0);
 assert.ok(FILTERS.some(([id])=>id==='spider'));
});

test('los nuevos filtros discretos y combinados conservan controles finitos',()=>{
 const f=anatomy();
 for (const id of ['eyes-squint','brows-uneven','brows-down','cheeks-hollow','chin-small','uncanny-tired','uncanny-skeptic']) {
   const controls=compose(f,id,[],40);
   assert.ok(controls.length>0,id);
   assert.ok(controls.every(c=>[c.x,c.y,c.dx,c.dy,c.scale,c.scaleY].every(Number.isFinite)),id);
 }
 const controls=compose(f,'uncanny-droop',[],40);
 const point=f.landmarks[205], expected=controls.reduce((p,c)=>forwardOne(p,c),point);
 assert.deepEqual(forwardWarp(point,controls),expected,'la araña sigue la misma deformación que la textura');
});

test('araña recorre y cierra el rostro con orientación variable',()=>{
 const route=spiderRoute(anatomy());
 assert.ok(route.length>=12);
 const a=spiderPosition(route,0),b=spiderPosition(route,1),near=spiderPosition(route,.9999);
 assert.ok(Math.hypot(a.x-b.x,a.y-b.y)<1e-9);
 assert.ok(Math.hypot(a.x-near.x,a.y-near.y)<.01);
 const direction=t=>{
  const p=spiderPosition(route,t),q=spiderPosition(route,t+.001);
  return Math.atan2(q.y-p.y,q.x-p.x);
 };
 assert.ok(new Set([.05,.28,.53,.77].map(t=>Math.round(direction(t)*10))).size>2);
});
test('araña guarda reguladores y respeta filtros antiguos',()=>{
 assert.deepEqual(spiderConfig(),{speed:100,size:175});
 assert.deepEqual(spiderConfig({speed:175,size:140}),{speed:175,size:140});
 assert.deepEqual(spiderConfig({speed:999,size:-1}),{speed:100,size:175});
 const base={version:1,name:'Araña',preset:'spider',intensity:100,radius:.19,strokes:[]};
 assert.ok(validFilter(base));
 const saved={...base,spider:{speed:175,size:140}};
 assert.ok(validFilter(saved));
 assert.ok(!validFilter({...base,spider:{speed:251,size:140}}));
 const data=new Map(),store={getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v)};
 saveFilter(saved,store);assert.deepEqual(listFilters(store),[saved]);
});

test('araña gira para avanzar con la cabeza por delante',()=>{
 assert.ok(Math.abs(spiderHeading(0,-1)+Math.PI)<1e-9,'hacia arriba');
 assert.ok(Math.abs(spiderHeading(0,1))<1e-9,'hacia abajo');
 assert.ok(Math.abs(spiderHeading(1,0)+Math.PI/2)<1e-9,'hacia derecha');
});

test('tendencias: controles finitos, intensidad proporcional y mezclas compatibles',()=>{
 const f=anatomy();
 const ids=FILTERS.map(([id])=>id).filter(id=>id.startsWith('trend-'));
 assert.equal(ids.length,10);
 for(const id of ids){
   const full=presetControls(f,id),lower=compose(f,id,[],35);
   assert.ok(full.length>0&&full.length<=MAX_CONTROLS,id);
   assert.equal(lower.length,full.length,id);
   for(let k=0;k<full.length;k++){
     assert.ok(Object.values(full[k]).every(Number.isFinite),id+' finitud');
     for(const key of ['dx','dy','scale','scaleY'])
       assert.ok(Math.abs(lower[k][key]-full[k][key]*.35)<1e-8,id+' '+key);
   }
 }
 const mixed=compose(f,'trend-baby',[],75,[{preset:'trend-pout',intensity:40}]);
 assert.equal(mixed.length,presetControls(f,'trend-baby').length+presetControls(f,'trend-pout').length);
 const storeData=new Map(),store={getItem:k=>storeData.get(k)??null,setItem:(k,v)=>storeData.set(k,v)};
 const preset={version:1,name:'Tendencia',preset:'trend-surprise',intensity:55,radius:.19,strokes:[],
   effects:[{preset:'trend-hero',intensity:30}]};
 saveFilter(preset,store);
 assert.deepEqual(listFilters(store),[preset]);
});

test('tres especies y cinco bichos máximo incluso en mezcla',()=>{
 assert.deepEqual(CREATURE_DEFAULTS,{speed:100,size:175,count:1});
 const effects=[{preset:'cockroach',intensity:60},{preset:'wasp',intensity:80}];
 const mixed=activeCreatures('spider',effects,75,5);
 assert.equal(mixed.length,5);
 assert.deepEqual(mixed.map(c=>c.id),['spider','cockroach','wasp','spider','cockroach']);
 assert.equal(mixed[0].intensity,.75);
 assert.ok(Math.abs(mixed[1].intensity-.45)<1e-9);
 assert.equal(activeCreatures('normal',effects,100,5).length,5);
 assert.equal(activeCreatures('normal',[],100,5).length,0);
 assert.equal(activeCreatures('spider',effects,0,5).length,0);
 assert.equal(activeCreatures('cockroach',[],100,100).length,5);
 assert.equal(activeCreatures('wasp',[],100,1)[0].id,'wasp');
 assert.equal(presetControls(anatomy(),'cockroach').length,0);
 assert.equal(presetControls(anatomy(),'wasp').length,0);
});
test('luz de piel clara aclara los bichos y reduce sombras',()=>{
 for(const id of ['spider','cockroach','wasp']){
  const dark=localLighting([34,31,29],id);
  const bright=localLighting([240,226,221],id);
  assert.ok(bright.brightness>dark.brightness,id);
  assert.ok(bright.shadow<dark.shadow,id);
  assert.ok(bright.brightness<=1.52 && dark.brightness>=.48,id);
  assert.ok(Number.isFinite(creatureHeading(id,1,1)));
 }
});
test('nuevo guardado de criaturas y compatibilidad con spider guardado',()=>{
 assert.deepEqual(creatureConfig(),{speed:100,size:175,count:1});
 assert.deepEqual(creatureConfig(undefined,{speed:80,size:125}),{speed:80,size:125,count:1});
 assert.deepEqual(creatureConfig({speed:120,size:175,count:5}),{speed:120,size:175,count:5});
 const base={version:1,name:'Bichos',preset:'cockroach',intensity:100,radius:.19,strokes:[]};
 const settings={speed:120,size:175,count:5};
 assert.ok(validFilter({...base,creatures:settings}));
 assert.ok(!validFilter({...base,creatures:{...settings,count:6}}));
 assert.ok(!validFilter({...base,creatures:{...settings,count:0}}));
 assert.ok(!validFilter({...base,creatures:{...settings,count:1.3}}));
 const data=new Map(),store={getItem:k=>data.get(k)??null,setItem:(k,v)=>data.set(k,v)};
 const saved={...base,creatures:settings,effects:[{preset:'wasp',intensity:45}]};
 saveFilter(saved,store);
 assert.deepEqual(listFilters(store),[saved]);
});

test('cucaracha y avispa verticales avanzan cabeza por delante',()=>{
 for(const id of ['cockroach','wasp']){
   assert.ok(Math.abs(creatureHeading(id,0,-1))<1e-9,id+' al subir');
   assert.ok(Math.abs(creatureHeading(id,1,0)-Math.PI/2)<1e-9,id+' al ir a la derecha');
   assert.ok(Math.abs(creatureHeading(id,0,1)-Math.PI)<1e-9,id+' al bajar');
 }
 assert.ok(Math.abs(creatureHeading('spider',0,1))<1e-9,'araña sin regresión');
});
