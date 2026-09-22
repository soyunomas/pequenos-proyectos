import { describe, expect, it } from 'vitest';
import { deleteFilter, readFilters, saveFilter, validFilter } from '../src/filters/storage';
import type { SavedFilter } from '../src/types/face';
function memory() {
  const data = new Map<string, string>();
  return { getItem: (key: string) => data.get(key) ?? null, setItem: (key: string, value: string) => { data.set(key, value); } };
}
const entry: SavedFilter = { version: 1, name: 'Mi nariz', preset: 'nose-long', intensity: 75, radius: .2,
  strokes: [{ landmark: 1, offset: { x: .01, y: -.03 }, delta: { x: .12, y: .02 }, radius: .2, scale: 0 }] };
describe('guardado local JSON versionado', () => {
  it('guarda, reemplaza por nombre, recupera y elimina', () => {
    const store = memory();
    saveFilter(entry, store);
    expect(readFilters(store)).toEqual([entry]);
    saveFilter({ ...entry, intensity: 90 }, store);
    expect(readFilters(store)).toHaveLength(1);
    expect(readFilters(store)[0].intensity).toBe(90);
    deleteFilter(entry.name, store);
    expect(readFilters(store)).toEqual([]);
  });
  it('ignora JSON corrupto o datos inválidos', () => {
    const store = memory(); store.setItem('facechanger-web:filters:v1', 'no-json');
    expect(readFilters(store)).toEqual([]);
    expect(validFilter({ ...entry, strokes: [{ ...entry.strokes[0], landmark: -1 }] })).toBe(false);
    expect(() => saveFilter({ ...entry, intensity: Infinity }, store)).toThrow();
  });
});
