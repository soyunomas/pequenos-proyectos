/** MediaPipe Face Landmarker, coordenadas top-left invertidas como espejo.
 * Índices contrastados con FACE_LANDMARKS_NOSE/EYES/LIPS/FACE_OVAL oficiales.
 * Los puntos 468/473 son iris; no se usan como centro del ojo porque se mueven al mirar.
 */
export const MAX_CONTROLS = 32;
export const LANDMARK = Object.freeze({
  noseTip: 1, noseLowerBridge: 4, noseBase: 2, nostrilA: 98, nostrilB: 327,
  eyeAOuter: 33, eyeAInner: 133, eyeATop: 159, eyeABottom: 145,
  eyeBOuter: 263, eyeBInner: 362, eyeBTop: 386, eyeBBottom: 374,
  mouthA: 61, mouthB: 291, chin: 152, forehead: 10, cheekA: 234, cheekB: 454
});
export const clamp = (v, min, max) => Math.max(min, Math.min(max, v));
export const distance = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);
export function faceFromLandmarks(landmarks) {
  if (!landmarks || landmarks.length < 455) return null;
  const l = landmarks.map(p => ({ x: 1 - p.x, y: p.y }));
  const a = l[LANDMARK.cheekA], b = l[LANDMARK.cheekB];
  const width = distance(a, b);
  if (!Number.isFinite(width) || width < .04) return null;
  const eyeA = l[LANDMARK.eyeAOuter], eyeB = l[LANDMARK.eyeBOuter];
  return { landmarks: l, frame: { width,
    angle: Math.atan2(eyeA.y - eyeB.y, eyeA.x - eyeB.x) } };
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
  const offset = toWorld(s.offset, face), delta = toWorld(s.delta, face);
  return { x: landmark.x + offset.x, y: landmark.y + offset.y,
    dx: delta.x, dy: delta.y, radius: s.radius * face.frame.width,
    shapeY: 1, scale: s.scale || 0 };
}
const midpoint = (face, indices) => {
  const points = indices.map(i => face.landmarks[i]).filter(Boolean);
  return { x: points.reduce((sum, p) => sum + p.x, 0) / points.length,
    y: points.reduce((sum, p) => sum + p.y, 0) / points.length };
};
/** Radio en fracción de ancho facial; shapeY < 1 evita invadir ojos/boca. */
const at = (face, indices, radius, dx = 0, dy = 0, scale = 0, shapeY = 1) => {
  const p = midpoint(face, Array.isArray(indices) ? indices : [indices]);
  const d = toWorld({ x: dx, y: dy }, face);
  return { x: p.x, y: p.y, dx: d.x, dy: d.y,
    radius: radius * face.frame.width, shapeY, scale };
};
export const FILTERS = [
  ['normal', 'Normal'], ['nose-big', 'Nariz grande'], ['nose-twisted', 'Nariz torcida'],
  ['nose-long', 'Nariz larga'], ['eyes-big', 'Ojos grandes'], ['eye-droop', 'Ojo caído'],
  ['mouth-twisted', 'Boca torcida'], ['face-long', 'Cara alargada']
];
export function presetControls(face, name) {
  const L = LANDMARK;
  switch (name) {
    // Base + alas de nariz, soportes COMPACTOS separados del ojo y la boca.
    case 'nose-big': return [
      at(face, [L.noseTip, L.noseBase], .135, 0, 0, .34, .72),
      at(face, L.nostrilA, .105, 0, 0, .22, .68),
      at(face, L.nostrilB, .105, 0, 0, .22, .68)
    ];
    case 'nose-twisted': return [at(face, [L.noseTip, L.noseLowerBridge], .18, .14, 0, 0, .78)];
    case 'nose-long': return [at(face, [L.noseTip, L.noseBase], .17, 0, .15, 0, .82)];
    // Centro del párpado, no iris 468/473: estable aunque cambie la mirada.
    case 'eyes-big': return [
      at(face, [L.eyeAOuter, L.eyeAInner, L.eyeATop, L.eyeABottom], .175, 0, 0, .43, .75),
      at(face, [L.eyeBOuter, L.eyeBInner, L.eyeBTop, L.eyeBBottom], .175, 0, 0, .43, .75)
    ];
    case 'eye-droop': return [at(face, [L.eyeAOuter, L.eyeAInner, L.eyeATop, L.eyeABottom], .185, 0, .15, 0, .82)];
    case 'mouth-twisted': return [at(face, L.mouthA, .14, .15, .04, 0, .78)];
    case 'face-long': return [at(face, L.chin, .32, 0, .21), at(face, L.forehead, .29, 0, -.085)];
    default: return [];
  }
}
export function compose(face, preset, strokes, intensity = 100) {
  if (!face || intensity <= 0) return [];
  const factor = clamp(intensity / 100, 0, 1);
  return [...presetControls(face, preset), ...strokes.map(s => controlFromStroke(s, face)).filter(Boolean)]
    .slice(-MAX_CONTROLS).map(c => ({ ...c, dx: c.dx * factor, dy: c.dy * factor, scale: c.scale * factor }));
}
/** Misma función compacta que GLSL, con distancia corregida por relación de aspecto. */
export function influenceAt(point, c, aspect = 16 / 9, destination = false) {
  const cx = c.x + (destination ? c.dx : 0);
  const cy = c.y + (destination ? c.dy : 0);
  const radius = Math.max(1e-5, c.radius * aspect);
  const dx = (point.x - cx) * aspect / radius;
  const dy = (point.y - cy) / (radius * (c.shapeY || 1));
  const d2 = dx * dx + dy * dy;
  return d2 < 1 ? (1 - d2) ** 3 : 0;
}
/** Inversa aproximada de shader: hit-testing sobre la imagen YA deformada. */
export function inverseWarp(p, controls, aspect = 16 / 9) {
  let x = p.x, y = p.y;
  for (let i = controls.length - 1; i >= 0; i--) {
    const c = controls[i];
    const w = influenceAt({ x, y }, c, aspect, true);
    x -= c.dx * w; y -= c.dy * w;
    const swell = influenceAt({ x, y }, c, aspect);
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
    const dist = Math.hypot((p.x - point.x) * aspect, p.y - point.y);
    if (dist < best) { best = dist; closest = i; }
  });
  if (closest < 0 || best > face.frame.width * aspect * .27) return null;
  const p = face.landmarks[closest];
  return { landmark: closest, offset: toLocal({ x: point.x - p.x, y: point.y - p.y }, face),
    delta: { x: 0, y: 0 }, radius: clamp(radius, .06, .38), scale: 0 };
}
