import { faceFromLandmarks, makeStroke, compose, toLocal, toWorld, inverseWarp } from '../engine/geometry.js';
import { PointerDeformer, mapPointer } from '../engine/pointer.js';
import { listFilters, saveFilter } from '../engine/storage.js';
import { Renderer } from '../engine/renderer.js';
const host = document.getElementById('tests'); let pass = 0, fail = 0;
const close = (a, b) => Math.abs(a - b) < 1e-5;
async function test(label, fn) {
  const li = document.createElement('li');
  try { await fn(); li.className = 'ok'; li.textContent = '✓ ' + label; pass++; }
  catch (err) { li.className = 'fail'; li.textContent = '✕ ' + label + ': ' + err.message; fail++; }
  host.append(li);
}
const assert = (condition, reason = 'Resultado inesperado') => { if (!condition) throw Error(reason); };
const raw = Array.from({ length: 478 }, () => ({ x: .5, y: .5 }));
raw[234] = { x: .7, y: .52 }; raw[454] = { x: .3, y: .52 };
raw[33] = { x: .35, y: .4 }; raw[263] = { x: .65, y: .4 };
raw[1] = { x: .5, y: .53 }; raw[4] = { x: .5, y: .6 };
const face = () => faceFromLandmarks(raw);
await test('Espejo: invierte los landmarks', () => assert(close(face().landmarks[234].x, .3)));
await test('Mapea object-fit cover con CSS desplazado', () => {
  const p = mapPointer({ clientX: 200, clientY: 350 }, { left: 20, top: 30, width: 360, height: 640 }, 1280, 720);
  assert(close(p.x, .5) && close(p.y, .5));
  assert(mapPointer({ clientX: 20, clientY: 30 }, { left: 20, top: 30, width: 360, height: 640 }, 1280, 720).x > .3);
});
await test('Desplazamiento facial reversible bajo giro', () => {
  const f = face(); f.frame.angle = .4;
  const v = { x: .11, y: -.05 }, back = toWorld(toLocal(v, f), f);
  assert(close(back.x, v.x) && close(back.y, v.y));
});
await test('El gesto se actualiza en movimiento y persiste al soltar', () => {
  const g = new PointerDeformer(), f = face();
  assert(g.begin(4, { x: .5, y: .53 }, f, .19, [], 16 / 9));
  assert(g.move(4, { x: .54, y: .53 }).delta.x > 0);
  const s = g.end(4); assert(s && s.landmark >= 0);
  const f2 = face(); f2.landmarks = f2.landmarks.map(p => ({ x: p.x + .05, y: p.y + .05 }));
  const a = compose(f, 'normal', [s], 100)[0], b = compose(f2, 'normal', [s], 100)[0];
  assert(close(b.x - a.x, .05) && close(b.y - a.y, .05));
});
await test('Gesto cancelado no produce filtros guardados', () => {
  const g = new PointerDeformer(); assert(g.begin(7, { x: .5, y: .53 }, face(), .19, [], 16 / 9));
  g.move(7, { x: .56, y: .53 }); assert(g.end(7, false) === null);
});
await test('Hit-testing tolera píxeles ya deformados', () => {
  const f = face(), c = compose(f, 'nose-twisted', [], 100);
  const visible = { x: f.landmarks[1].x + c[0].dx, y: f.landmarks[1].y };
  const hit = inverseWarp(visible, c, 16 / 9);
  assert(Math.abs(hit.x - f.landmarks[1].x) < .025);
  assert(makeStroke(f, visible, .19, c, 16 / 9));
});
await test('Almacena y recupera JSON versionado sin servidor', () => {
  const data = new Map(); const store = { getItem: k => data.get(k) ?? null, setItem: (k, v) => data.set(k, v) };
  const f = { version: 1, name: 'Mi filtro', preset: 'normal', intensity: 100, radius: .19, strokes: [] };
  saveFilter(f, store); assert(listFilters(store).length === 1);
});
await test('El shader WebGL2 compila y enlaza', () => {
  const r = new Renderer(document.createElement('canvas')); r.dispose();
});
document.getElementById('summary').textContent = `${pass} correctas · ${fail} fallidas`;
document.title = fail ? 'Error en pruebas · FaceChanger' : 'Pruebas correctas · FaceChanger';
