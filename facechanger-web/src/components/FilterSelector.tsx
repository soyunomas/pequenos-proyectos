import { FILTERS } from '../filters/presets';
import type { FilterId } from '../types/face';
export function FilterSelector({ preset, onChange }: { preset: FilterId; onChange: (id: FilterId) => void }) {
  return <section><h2>Filtros</h2><div className="filter-grid">{FILTERS.map(f =>
    <button key={f.id} className={preset === f.id ? 'filter selected' : 'filter'} aria-pressed={preset === f.id}
      onClick={() => onChange(f.id)} title={f.detail}>{f.title}</button>
  )}</div></section>;
}
