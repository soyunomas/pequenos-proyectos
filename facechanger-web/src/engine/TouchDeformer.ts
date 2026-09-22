import { clamp, createStroke, localVector } from './FaceWarpEngine';
import type { Face, Point, Stroke } from '../types/face';
export type GestureState = { pointerId: number; start: Point; face: Face; stroke: Stroke };
/** State machine is framework-agnostic and can be tested without a browser. */
export class TouchDeformer {
  active: GestureState | null = null;
  begin(pointerId: number, at: Point, face: Face | null, radius: number): boolean {
    if (!face || this.active) return false;
    const stroke = createStroke(face, at, radius);
    if (!stroke) return false;
    this.active = { pointerId, start: at, face, stroke };
    return true;
  }
  move(pointerId: number, at: Point): Stroke | null {
    const a = this.active;
    if (!a || a.pointerId !== pointerId) return null;
    const delta = localVector({ x: at.x - a.start.x, y: at.y - a.start.y }, a.face.frame);
    const d = Math.hypot(delta.x, delta.y), max = .55;
    a.stroke = { ...a.stroke, delta: { x: clamp(delta.x, -max, max) * Math.min(1, max / (d || 1)), y: clamp(delta.y, -max, max) * Math.min(1, max / (d || 1)) } };
    return a.stroke;
  }
  end(pointerId: number, commit = true): Stroke | null {
    if (!this.active || this.active.pointerId !== pointerId) return null;
    const stroke = this.active.stroke;
    this.active = null;
    return commit && Math.hypot(stroke.delta.x, stroke.delta.y) > .003 ? stroke : null;
  }
  cancel(): void { this.active = null; }
}
