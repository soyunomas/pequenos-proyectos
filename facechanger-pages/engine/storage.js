import { FILTERS, MAX_CONTROLS } from './geometry.js';
const KEY = 'facechanger-pages:filters:v1';
const finite = n => typeof n === 'number' && Number.isFinite(n);
export const validStroke = s => s && Number.isInteger(s.landmark) && s.landmark >= 0 && s.landmark < 478 &&
  finite(s.radius) && s.radius >= .06 && s.radius <= .38 && finite(s.scale) && Math.abs(s.scale) <= 1 &&
  s.offset && finite(s.offset.x) && finite(s.offset.y) && Math.abs(s.offset.x) <= 1 && Math.abs(s.offset.y) <= 1 &&
  s.delta && finite(s.delta.x) && finite(s.delta.y) && Math.abs(s.delta.x) <= .48 && Math.abs(s.delta.y) <= .48;
export const validFilter = f => f && f.version === 1 && typeof f.name === 'string' && !!f.name.trim() && f.name.length <= 64 &&
  FILTERS.some(([id]) => id === f.preset) && finite(f.intensity) && f.intensity >= 0 && f.intensity <= 100 &&
  finite(f.radius) && f.radius >= .06 && f.radius <= .38 && Array.isArray(f.strokes) &&
  f.strokes.length <= MAX_CONTROLS && f.strokes.every(validStroke);
export function listFilters(store = localStorage) {
  try { const filters = JSON.parse(store.getItem(KEY) ?? '[]');
    return Array.isArray(filters) ? filters.filter(validFilter).slice(-30) : [];
  } catch { return []; }
}
export function saveFilter(filter, store = localStorage) {
  if (!validFilter(filter)) throw Error('Configuración de filtro no válida');
  store.setItem(KEY, JSON.stringify([...listFilters(store).filter(f => f.name !== filter.name), filter].slice(-30)));
}
export function removeFilter(name, store = localStorage) {
  store.setItem(KEY, JSON.stringify(listFilters(store).filter(f => f.name !== name)));
}
