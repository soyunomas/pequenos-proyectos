import { clamp, makeStroke, toLocal } from './geometry.js';
/** Rect es el área CSS completa del canvas, object-fit: cover recorta su contenido. */
export function mapPointer({ clientX, clientY }, rect, width, height) {
  if (width <= 0 || height <= 0 || rect.width <= 0 || rect.height <= 0) return null;
  const scale = Math.max(rect.width / width, rect.height / height);
  const shownW = width * scale, shownH = height * scale;
  const x = (clientX - rect.left - (rect.width - shownW) / 2) / shownW;
  const y = (clientY - rect.top - (rect.height - shownH) / 2) / shownH;
  return x >= 0 && x <= 1 && y >= 0 && y <= 1 ? { x, y } : null;
}
export class PointerDeformer {
  active = null;
  begin(id, position, face, radius, controls, aspect) {
    if (this.active || !face || !position) return false;
    const stroke = makeStroke(face, position, radius, controls, aspect);
    if (!stroke) return false;
    this.active = { id, start: position, face, stroke };
    return true;
  }
  move(id, position) {
    const a = this.active;
    if (!a || a.id !== id || !position) return null;
    const d = toLocal({ x: position.x - a.start.x, y: position.y - a.start.y }, a.face);
    const length = Math.hypot(d.x, d.y);
    const ratio = Math.min(1, .48 / Math.max(1e-6, length));
    a.stroke = { ...a.stroke, delta: { x: clamp(d.x * ratio, -.48, .48), y: clamp(d.y * ratio, -.48, .48) } };
    return a.stroke;
  }
  end(id, commit = true) {
    if (!this.active || id !== this.active.id) return null;
    const stroke = this.active.stroke;
    this.active = null;
    return commit && Math.hypot(stroke.delta.x, stroke.delta.y) > .003 ? stroke : null;
  }
  cancel() { this.active = null; }
}
