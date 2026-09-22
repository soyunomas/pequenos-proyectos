type Props = { onUndo: () => void; onRedo: () => void; onReset: () => void; onSave: () => void;
  onMirror: () => void; onFullscreen: () => void; canUndo: boolean; canRedo: boolean };
export function Toolbar(p: Props) {
  return <div className="toolbar">
    <button disabled={!p.canUndo} onClick={p.onUndo} title="Deshacer último arrastre">↶ Deshacer</button>
    <button disabled={!p.canRedo} onClick={p.onRedo} title="Rehacer último arrastre">↷ Rehacer</button>
    <button onClick={p.onReset}>Restablecer</button>
    <button onClick={p.onSave}>Guardar filtro</button>
    <button onClick={p.onFullscreen}>Pantalla completa</button>
    <button className="primary" onClick={p.onMirror}>Modo espejo ↗</button>
  </div>;
}
