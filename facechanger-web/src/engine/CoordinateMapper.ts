import type { Point } from '../types/face';
export type Viewport = { left: number; top: number; width: number; height: number };
/** Maps CSS pointer coordinates to mirrored, top-left-origin normalized video pixels. */
export function mapPointer(client: Point, rect: Viewport, videoWidth: number, videoHeight: number): Point | null {
  if (rect.width <= 0 || rect.height <= 0 || videoWidth <= 0 || videoHeight <= 0) return null;
  const scale = Math.max(rect.width / videoWidth, rect.height / videoHeight);
  const w = videoWidth * scale, h = videoHeight * scale;
  const x = (client.x - rect.left - (rect.width - w) / 2) / w;
  const y = (client.y - rect.top - (rect.height - h) / 2) / h;
  return x >= 0 && x <= 1 && y >= 0 && y <= 1 ? { x, y } : null;
}
export const mirrorLandmarks = <T extends Point>(landmarks: T[]): T[] => landmarks.map(p => ({ ...p, x: 1 - p.x }));
