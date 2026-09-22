import { describe, expect, it } from 'vitest';
import { composeControls, createStroke, frameFromLandmarks, localVector, worldVector } from '../src/engine/FaceWarpEngine';
import { mapPointer, mirrorLandmarks } from '../src/engine/CoordinateMapper';
import { TouchDeformer } from '../src/engine/TouchDeformer';
import type { Face } from '../src/types/face';
const points = Array.from({ length: 478 }, () => ({ x: .5, y: .5 }));
points[234] = { x: .3, y: .52 }; points[454] = { x: .7, y: .52 };
points[33] = { x: .42, y: .4 }; points[263] = { x: .58, y: .4 };
points[1] = { x: .5, y: .53 }; points[4] = { x: .5, y: .6 };
const face = (): Face => ({ landmarks: structuredClone(points), frame: frameFromLandmarks(points) });
describe('mapeo espejo y recorte cover', () => {
  it('invierte landmarks respecto al fotograma de vídeo', () => {
    expect(mirrorLandmarks([{ x: .15, y: .7 }])).toEqual([{ x: .85, y: .7 }]);
  });
  it('aplica object-fit cover en retrato sin offset táctil', () => {
    const rect = { left: 20, top: 30, width: 360, height: 640 };
    expect(mapPointer({ x: 200, y: 350 }, rect, 1280, 720)).toEqual({ x: .5, y: .5 });
    expect(mapPointer({ x: 20, y: 30 }, rect, 1280, 720)!.x).toBeCloseTo(.341796875);
    expect(mapPointer({ x: 200, y: 350 }, rect, 0, 720)).toBeNull();
  });
  it('corrige coordenadas de la caja CSS trasladada y escalada', () => {
    expect(mapPointer({ x: 150, y: 100 }, { left: 50, top: 50, width: 200, height: 100 }, 1600, 800))
      .toEqual({ x: .5, y: .5 });
  });
});
describe('deformaciones ancladas', () => {
  it('convierte desplazamientos entre sistema local y mundo incluso con giro', () => {
    const f = { center: { x: .5, y: .5 }, width: .4, angle: Math.PI / 6 };
    const v = { x: .08, y: -.02 };
    const l = localVector(v, f), w = worldVector(l, f);
    expect(w.x).toBeCloseTo(v.x); expect(w.y).toBeCloseTo(v.y);
  });
  it('un gesto se asocia a landmark y sigue tras mover y girar la cara', () => {
    const original = face();
    const stroke = createStroke(original, { x: .5, y: .53 }, .2)!;
    stroke.delta = { x: .1, y: 0 };
    const first = composeControls(original, 'normal', [stroke], 100)[0];
    const moved = face(); moved.landmarks = moved.landmarks.map(p => ({ x: p.x + .1, y: p.y + .08 }));
    moved.frame = frameFromLandmarks(moved.landmarks);
    const second = composeControls(moved, 'normal', [stroke], 100)[0];
    expect(second.center.x - first.center.x).toBeCloseTo(.1);
    expect(second.center.y - first.center.y).toBeCloseTo(.08);
    expect(second.delta.x).toBeCloseTo(first.delta.x);
  });
  it('sin rostro o a intensidad cero deja imagen sin deformar', () => {
    expect(composeControls(null, 'nose-long', [], 100)).toEqual([]);
    expect(composeControls(face(), 'nose-long', [], 0)).toEqual([]);
  });
});
describe('gestos Pointer Events', () => {
  it('permanece durante arrastre, conserva al soltar y permite una segunda edición', () => {
    const g = new TouchDeformer(), f = face();
    expect(g.begin(7, { x: .5, y: .53 }, f, .2)).toBe(true);
    expect(g.move(8, { x: .7, y: .53 })).toBeNull();
    expect(g.move(7, { x: .54, y: .53 })!.delta.x).toBeCloseTo(.1);
    const first = g.end(7)!;
    expect(first.delta.x).toBeCloseTo(.1);
    expect(g.begin(9, { x: .5, y: .6 }, f, .2)).toBe(true);
    expect(g.move(9, { x: .5, y: .64 })!.delta.y).toBeCloseTo(.1);
    expect(g.end(9)).not.toBeNull();
  });
  it('cancelación no guarda edición y tocar fuera de la cara no inicia', () => {
    const g = new TouchDeformer();
    expect(g.begin(1, { x: .02, y: .02 }, face(), .2)).toBe(false);
    expect(g.begin(2, { x: .5, y: .53 }, face(), .2)).toBe(true);
    g.move(2, { x: .55, y: .53 });
    expect(g.end(2, false)).toBeNull();
  });
});
