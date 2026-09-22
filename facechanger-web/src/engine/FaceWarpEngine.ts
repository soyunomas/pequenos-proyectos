import type { Face, FaceFrame, Landmark, Point, Stroke, WarpControl, FilterId } from '../types/face';
export const MAX_CONTROLS = 48;
export const clamp = (v: number, min: number, max: number) => Math.max(min, Math.min(max, v));
export const add = (a: Point, b: Point): Point => ({ x: a.x + b.x, y: a.y + b.y });
export const distance = (a: Point, b: Point) => Math.hypot(a.x - b.x, a.y - b.y);
export function frameFromLandmarks(points: Landmark[]): FaceFrame {
  const a = points[234], b = points[454], leftEye = points[33], rightEye = points[263];
  if (!a || !b || !leftEye || !rightEye) throw new Error('Malla facial incompleta');
  return { center: { x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 }, width: Math.max(distance(a, b), 0.0001),
    angle: Math.atan2(rightEye.y - leftEye.y, rightEye.x - leftEye.x) };
}
export function localVector(v: Point, frame: FaceFrame): Point {
  const c = Math.cos(frame.angle), s = Math.sin(frame.angle);
  return { x: (v.x * c + v.y * s) / frame.width, y: (-v.x * s + v.y * c) / frame.width };
}
export function worldVector(v: Point, frame: FaceFrame): Point {
  const c = Math.cos(frame.angle), s = Math.sin(frame.angle);
  return { x: (v.x * c - v.y * s) * frame.width, y: (v.x * s + v.y * c) * frame.width };
}
export function strokeToControl(stroke: Stroke, face: Face): WarpControl | null {
  const landmark = face.landmarks[stroke.landmark];
  if (!landmark) return null;
  return { center: add(landmark, worldVector(stroke.offset, face.frame)),
    delta: worldVector(stroke.delta, face.frame), radius: stroke.radius * face.frame.width, scale: stroke.scale };
}
export function createStroke(face: Face, at: Point, radius: number): Stroke | null {
  let closest = -1, best = Infinity;
  face.landmarks.forEach((p, i) => { const d = distance(p, at); if (d < best) { best = d; closest = i; } });
  if (closest < 0 || best > face.frame.width * .24) return null;
  const p = face.landmarks[closest];
  return { landmark: closest, offset: localVector({ x: at.x - p.x, y: at.y - p.y }, face.frame),
    delta: { x: 0, y: 0 }, radius, scale: 0 };
}
const item = (face: Face, landmark: number, radius: number, dx = 0, dy = 0, scale = 0): WarpControl => ({
  center: { x: face.landmarks[landmark].x, y: face.landmarks[landmark].y },
  delta: worldVector({ x: dx, y: dy }, face.frame), radius: radius * face.frame.width, scale
});
export function presetControls(face: Face, preset: FilterId): WarpControl[] {
  switch (preset) {
    case 'nose-big': return [item(face, 1, .23, 0, 0, .42)];
    case 'nose-twisted': return [item(face, 1, .23, .15)];
    case 'nose-long': return [item(face, 4, .26, 0, .2)];
    case 'eyes-big': return [item(face, 33, .17, 0, 0, .43), item(face, 263, .17, 0, 0, .43)];
    case 'eye-droop': return [item(face, 33, .2, 0, .18)];
    case 'mouth-twisted': return [item(face, 61, .19, .19, .06)];
    case 'face-long': return [item(face, 152, .46, 0, .24), item(face, 10, .39, 0, -.12)];
    default: return [];
  }
}
export function composeControls(face: Face | null, preset: FilterId, strokes: Stroke[], intensity: number): WarpControl[] {
  if (!face || intensity <= 0) return [];
  const factor = clamp(intensity, 0, 100) / 100;
  const all = [...presetControls(face, preset), ...strokes.map(s => strokeToControl(s, face)).filter((c): c is WarpControl => !!c)];
  return all.slice(-MAX_CONTROLS).map(c => ({ ...c, delta: { x: c.delta.x * factor, y: c.delta.y * factor }, scale: c.scale * factor }));
}
