/** Sistema de coordenadas top-left, orientado como espejo. Sin dependencias. */
export const MAX_CONTROLS = 32;
export const clamp = (v, min, max) => Math.max(min, Math.min(max, v));
export const distance = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);

export function faceFromLandmarks(landmarks) {
  if (!landmarks || landmarks.length < 455) return null;
  const l = landmarks.map(p => ({ x: 1 - p.x, y: p.y }));
  const a = l[234], b = l[454], eyeL = l[263], eyeR = l[33];
  const width = distance(a, b);
  if (!Number.isFinite(width) || width < .04) return null;
  return { landmarks: l, frame: { width, angle: Math.atan2(eyeR.y - eyeL.y, eyeR.x - eyeL.x) } };
}
export function toLocal(v, face) {
  const { angle, width } = face.frame, c = Math.cos(angle), s = Math.sin(angle);
  return { x: (v.x * c + v.y * s) / width, y: (v.y * c - v.x * s) / width };
}
export function toWorld(v, face) {
  const { angle, width } = face.frame, c = Math.cos(angle), s = Math.sin(angle);
  return { x: (v.x * c - v.y * s) * width, y: (v.x * s + v.y * c) * width };
}
export function controlFromStroke(s, face) {
  const landmark = face.landmarks[s.landmark];
  if (!landmark) return null;
  const offset = toWorld(s.offset, face);
  return { x: landmark.x + offset.x, y: landmark.y + offset.y,
    dx: toWorld(s.delta, face).x, dy: toWorld(s.delta, face).y,
    radius: s.radius * face.frame.width, scale: s.scale || 0 };
}
const at = (face, i, radius, dx = 0, dy = 0, scale = 0) => {
  const p = face.landmarks[i]; const d = toWorld({ x: dx, y: dy }, face);
  return { x: p.x, y: p.y, dx: d.x, dy: d.y, radius: radius * face.frame.width, scale };
};
export const FILTERS = [
  ['normal', 'Normal'], ['nose-big', 'Nariz grande'], ['nose-twisted', 'Nariz torcida'],
  ['nose-long', 'Nariz larga'], ['eyes-big', 'Ojos grandes'], ['eye-droop', 'Ojo caído'],
  ['mouth-twisted', 'Boca torcida'], ['face-long', 'Cara alargada']
];
export function presetControls(face, name) {
  switch (name) {
    case 'nose-big': return [at(face, 1, .22, 0, 0, .58)];
    case 'nose-twisted': return [at(face, 1, .25, .19)];
    case 'nose-long': return [at(face, 4, .29, 0, .22)];
    case 'eyes-big': return [at(face, 468 in face.landmarks ? 468 : 33, .19, 0, 0, .46), at(face, 473 in face.landmarks ? 473 : 263, .19, 0, 0, .46)];
    case 'eye-droop': return [at(face, 33, .20, 0, .19)];
    case 'mouth-twisted': return [at(face, 61, .20, .18, .06)];
    case 'face-long': return [at(face, 152, .44, 0, .25), at(face, 10, .39, 0, -.10)];
    default: return [];
  }
}
export function compose(face, preset, strokes, intensity = 100) {
  if (!face || intensity <= 0) return [];
  const factor = clamp(intensity / 100, 0, 1);
  return [...presetControls(face, preset), ...strokes.map(s => controlFromStroke(s, face)).filter(Boolean)]
    .slice(-MAX_CONTROLS).map(c => ({ ...c, dx: c.dx * factor, dy: c.dy * factor, scale: c.scale * factor }));
}
/** Inversa aproximada de shader: hit-testing sobre la imagen YA deformada. */
export function inverseWarp(p, controls, aspect = 16 / 9) {
  let x = p.x, y = p.y;
  for (let i = controls.length - 1; i >= 0; i--) {
    const c = controls[i]; const r2 = Math.max(1e-8, (c.radius * aspect) ** 2);
    const dx = (x - c.x - c.dx) * aspect, dy = y - c.y - c.dy;
    const w = Math.exp(-2.7 * (dx * dx + dy * dy) / r2);
    x -= c.dx * w; y -= c.dy * w;
    const qx = (x - c.x) * aspect, qy = y - c.y;
    const swell = Math.exp(-2.7 * (qx * qx + qy * qy) / r2);
    const s = Math.max(.55, 1 + c.scale * swell);
    x = c.x + (x - c.x) / s; y = c.y + (y - c.y) / s;
  }
  return { x, y };
}
export function makeStroke(face, visiblePoint, radius, controls = [], aspect = 16 / 9) {
  if (!face) return null;
  const point = inverseWarp(visiblePoint, controls, aspect);
  let closest = -1, best = Infinity;
  face.landmarks.forEach((p, i) => {
    // Distancia euclídea expresada en píxeles cuadrados para evitar sesgo de relación de aspecto.
    const dist = Math.hypot((p.x - point.x) * aspect, p.y - point.y);
    if (dist < best) { best = dist; closest = i; }
  });
  if (closest < 0 || best > face.frame.width * aspect * .27) return null;
  const p = face.landmarks[closest];
  return { landmark: closest, offset: toLocal({ x: point.x - p.x, y: point.y - p.y }, face),
    delta: { x: 0, y: 0 }, radius: clamp(radius, .06, .38), scale: 0 };
}
