export type Point = { x: number; y: number };
export type Landmark = Point & { z?: number };
export type FaceFrame = { center: Point; width: number; angle: number };
export type Face = { landmarks: Landmark[]; frame: FaceFrame };
/** Coordinates and offsets are normalized to face width in a rotating face basis. */
export type Stroke = {
  landmark: number;
  offset: Point;
  delta: Point;
  radius: number;
  scale: number;
};
export type FilterId = 'normal' | 'nose-big' | 'nose-twisted' | 'nose-long' | 'eyes-big' | 'eye-droop' | 'mouth-twisted' | 'face-long';
export type WarpControl = { center: Point; delta: Point; radius: number; scale: number };
export type SavedFilter = { version: 1; name: string; strokes: Stroke[]; preset: FilterId; intensity: number; radius: number };
