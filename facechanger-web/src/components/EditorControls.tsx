type Props = { intensity: number; radius: number; editable: boolean;
  onIntensity: (n: number) => void; onRadius: (n: number) => void; onEditable: (v: boolean) => void };
export function EditorControls(p: Props) {
  return <div className="editor-controls">
    <label className="check"><input type="checkbox" checked={p.editable} onChange={e => p.onEditable(e.target.checked)} /> Deformación manual activada</label>
    <label>Intensidad <strong>{p.intensity}%</strong><input aria-label="Intensidad" type="range" min="0" max="100" value={p.intensity} onChange={e => p.onIntensity(Number(e.target.value))} /></label>
    <label>Radio de influencia <strong>{Math.round(p.radius * 100)}%</strong><input aria-label="Radio de influencia" type="range" min="6" max="38" value={Math.round(p.radius * 100)} onChange={e => p.onRadius(Number(e.target.value) / 100)} /></label>
    <p className="small">Arrastra nariz, ojos, mejillas o boca directamente sobre la imagen. Los cambios se conservan al soltar.</p>
  </div>;
}
