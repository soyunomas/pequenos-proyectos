import { FILTERS, MAX_CONTROLS, normalizeEffects } from './geometry.js?v=realistic-ai-2';
import { SPIDER_LIMITS } from './spider.js';
import { MAX_CREATURES } from './creatures.js';
const KEY = 'facechanger-pages:filters:v1';
const finite = n => typeof n === 'number' && Number.isFinite(n);
export const validStroke = s => s && Number.isInteger(s.landmark) && s.landmark >= 0 && s.landmark < 478 &&
  finite(s.radius) && s.radius >= .06 && s.radius <= .38 && finite(s.scale) && Math.abs(s.scale) <= 1 &&
  s.offset && finite(s.offset.x) && finite(s.offset.y) && Math.abs(s.offset.x) <= 1 && Math.abs(s.offset.y) <= 1 &&
  s.delta && finite(s.delta.x) && finite(s.delta.y) && Math.abs(s.delta.x) <= .48 && Math.abs(s.delta.y) <= .48;
export const validFilter = f => f && f.version === 1 && typeof f.name === 'string' && !!f.name.trim() && f.name.length <= 64 &&
  FILTERS.some(([id]) => id === f.preset) && finite(f.intensity) && f.intensity >= 0 && f.intensity <= 100 &&
  finite(f.radius) && f.radius >= .06 && f.radius <= .38 &&
  (f.creatures === undefined || (f.creatures && finite(f.creatures.speed) &&
    f.creatures.speed>=25 && f.creatures.speed<=250 &&
    finite(f.creatures.size) && f.creatures.size>=40 && f.creatures.size<=220 &&
    Number.isInteger(f.creatures.count) && f.creatures.count>=1 &&
    f.creatures.count<=MAX_CREATURES)) &&
  (f.spider === undefined || (f.spider && finite(f.spider.speed) &&
    f.spider.speed >= SPIDER_LIMITS.speed[0] && f.spider.speed <= SPIDER_LIMITS.speed[1] &&
    finite(f.spider.size) && f.spider.size >= SPIDER_LIMITS.size[0] &&
    f.spider.size <= SPIDER_LIMITS.size[1])) && (f.effects === undefined || (Array.isArray(f.effects) && f.effects.length <= 4 &&
    normalizeEffects(f.effects).length === f.effects.length)) && Array.isArray(f.strokes) &&
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
