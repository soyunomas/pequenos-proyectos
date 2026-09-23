import test from 'node:test';
import assert from 'node:assert/strict';
import { faceFromLandmarks, makeStroke, compose, toLocal, toWorld, inverseWarp, MAX_CONTROLS, LANDMARK, influenceAt, presetControls } from '../engine/geometry.js';
import { PointerDeformer, mapPointer } from '../engine/pointer.js';
import { listFilters, saveFilter, removeFilter } from '../engine/storage.js';
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
    [L.mouthA,.43,.73],[L.mouthB,.57,.73],[L.chin,.5,.86],[L.forehead,.5,.16]
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
test('ojos grandes centrados en párpados y no en iris móvil',()=>{
  const f=anatomy(),cs=presetControls(f,'eyes-big').map(c=>c.x).sort((a,b)=>a-b);
  assert.ok(Math.abs(cs[0]-.405)<.01&&Math.abs(cs[1]-.595)<.01);
  assert.deepEqual(presetControls({...f,landmarks:f.landmarks.slice(0,468)},'eyes-big'),presetControls(f,'eyes-big'));
});
test('todos los presets producen controles finitos dentro de la malla',()=>{
  for(const [name] of [['normal'],['nose-big'],['nose-twisted'],['nose-long'],['eyes-big'],['eye-droop'],['mouth-twisted'],['face-long']])
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
