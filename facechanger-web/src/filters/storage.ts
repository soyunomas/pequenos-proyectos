import type { FilterId, SavedFilter, Stroke } from '../types/face';
import { FILTERS } from './presets';
const KEY = 'facechanger-web:filters:v1';
const finite = (n: unknown): n is number => typeof n === 'number' && Number.isFinite(n);
export function validStroke(s: unknown): s is Stroke {
  if (!s || typeof s !== 'object') return false;
  const v = s as Partial<Stroke>;
  return Number.isInteger(v.landmark) && (v.landmark as number) >= 0 && (v.landmark as number) < 478 &&
    finite(v.radius) && v.radius >= .03 && v.radius <= .6 && finite(v.scale) && Math.abs(v.scale) <= 1 &&
    !!v.offset && finite(v.offset.x) && finite(v.offset.y) && Math.abs(v.offset.x) <= 1 && Math.abs(v.offset.y) <= 1 &&
    !!v.delta && finite(v.delta.x) && finite(v.delta.y) && Math.abs(v.delta.x) <= .56 && Math.abs(v.delta.y) <= .56;
}
export function validFilter(v: unknown): v is SavedFilter {
  if (!v || typeof v !== 'object') return false;
  const f = v as Partial<SavedFilter>;
  return f.version === 1 && typeof f.name === 'string' && f.name.trim().length > 0 && f.name.length <= 64 &&
    FILTERS.some(p => p.id === f.preset) && finite(f.intensity) && f.intensity >= 0 && f.intensity <= 100 &&
    finite(f.radius) && f.radius >= .03 && f.radius <= .6 && Array.isArray(f.strokes) && f.strokes.length <= 48 && f.strokes.every(validStroke);
}
export function readFilters(store: Pick<Storage, 'getItem'> = localStorage): SavedFilter[] {
  try {
    const data: unknown = JSON.parse(store.getItem(KEY) ?? '[]');
    return Array.isArray(data) ? data.filter(validFilter).slice(0, 40) : [];
  } catch { return []; }
}
export function saveFilter(value: SavedFilter, store: Pick<Storage, 'getItem' | 'setItem'> = localStorage): void {
  if (!validFilter(value)) throw new Error('El filtro no tiene un formato válido');
  const others = readFilters(store).filter(f => f.name !== value.name);
  store.setItem(KEY, JSON.stringify([...others, value].slice(-40)));
}
export function deleteFilter(name: string, store: Pick<Storage, 'getItem' | 'setItem'> = localStorage): void {
  store.setItem(KEY, JSON.stringify(readFilters(store).filter(f => f.name !== name)));
}
export type { FilterId };
