import { useCallback, useEffect, useRef, useState } from 'react';
import { CameraView } from './components/CameraView';
import { EditorControls } from './components/EditorControls';
import { FilterSelector } from './components/FilterSelector';
import { Toolbar } from './components/Toolbar';
import { deleteFilter, readFilters, saveFilter } from './filters/storage';
import type { Face, FilterId, SavedFilter, Stroke } from './types/face';

export default function App() {
  const stageRef = useRef<HTMLDivElement>(null), fadeRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [strokes, setStrokes] = useState<Stroke[]>([]), [redo, setRedo] = useState<Stroke[]>([]);
  const [preset, setPreset] = useState<FilterId>('normal');
  const [intensity, setIntensity] = useState(100), [radius, setRadius] = useState(.19);
  const [editable, setEditable] = useState(true), [mirror, setMirror] = useState(false);
  const [status, setStatus] = useState('Iniciando…'), [detected, setDetected] = useState(false);
  const [saved, setSaved] = useState<SavedFilter[]>(() => readFilters());
  const [devices, setDevices] = useState<MediaDeviceInfo[]>([]), [device, setDevice] = useState('');
  const [showExit, setShowExit] = useState(false);
  const lastFace = useRef<Face | null>(null);
  const onStroke = useCallback((s: Stroke) => { setStrokes(old => [...old, s].slice(-48)); setRedo([]); }, []);
  const onFace = useCallback((face: Face | null) => { lastFace.current = face; setDetected(!!face); }, []);
  const undo = () => { if (strokes.length) { setRedo(old => [...old, strokes[strokes.length - 1]]); setStrokes(old => old.slice(0, -1)); } };
  const redoLast = () => { if (redo.length) { setStrokes(old => [...old, redo[redo.length - 1]]); setRedo(old => old.slice(0, -1)); } };
  const reset = () => { setStrokes([]); setRedo([]); setPreset('normal'); setIntensity(100); };
  function storeCurrent() {
    const name = window.prompt('Nombre del filtro personalizado:')?.trim();
    if (!name) return;
    try {
      saveFilter({ version: 1, name, preset, intensity, radius, strokes });
      setSaved(readFilters());
    } catch (error) { window.alert(error instanceof Error ? error.message : String(error)); }
  }
  function openMirror() {
    setMirror(true); revealExit();
    void stageRef.current?.requestFullscreen?.().catch(() => { /* modo espejo sigue disponible sin fullscreen */ });
  }
  function exitMirror() {
    setMirror(false);
    if (document.fullscreenElement) void document.exitFullscreen().catch(() => {});
  }
  function revealExit() {
    setShowExit(true);
    if (fadeRef.current) clearTimeout(fadeRef.current);
    fadeRef.current = setTimeout(() => setShowExit(false), 2500);
  }
  useEffect(() => {
    const key = (e: KeyboardEvent) => { if (e.key === 'Escape') setMirror(false); };
    const fullscreen = () => { if (!document.fullscreenElement) setMirror(false); };
    window.addEventListener('keydown', key); document.addEventListener('fullscreenchange', fullscreen);
    return () => { window.removeEventListener('keydown', key); document.removeEventListener('fullscreenchange', fullscreen); if (fadeRef.current) clearTimeout(fadeRef.current); };
  }, []);
  return <div className={mirror ? 'app is-mirror' : 'app'}>
    {!mirror && <header className="topbar"><div><span className="brand-dot" /> <strong>FaceChanger</strong><span className="topbar-muted"> / Web</span></div><span className="privacy">Cámara procesada en tu dispositivo</span></header>}
    <main className="workspace">
      <div ref={stageRef} className="stage" onPointerMove={mirror ? revealExit : undefined} onDoubleClick={mirror ? exitMirror : undefined}>
        <CameraView strokes={strokes} preset={preset} intensity={intensity} radius={radius} editable={editable && !mirror}
          deviceId={device} onStroke={onStroke} onStatus={setStatus} onFace={onFace} onDevices={setDevices} />
        {!mirror && <div className="stage-status"><span className={detected ? 'led ok' : 'led'} />{status}</div>}
        {mirror && <button className={showExit ? 'exit-mirror visible' : 'exit-mirror'} onClick={exitMirror}
          aria-label="Salir del modo espejo">Salir del espejo ×</button>}
      </div>
      {!mirror && <aside className="panel">
        <div className="panel-heading"><div className="eyebrow">ESTUDIO FACIAL</div><h1>Deforma tu reflejo</h1>
          <p>Un espejo interactivo. Sin subir tu vídeo a ningún servidor.</p></div>
        <FilterSelector preset={preset} onChange={setPreset} />
        <section><h2>Editor</h2><EditorControls intensity={intensity} onIntensity={setIntensity} radius={radius} onRadius={setRadius}
          editable={editable} onEditable={setEditable} /></section>
        <section><h2>Cámara</h2><select value={device} aria-label="Seleccionar cámara" onChange={e => setDevice(e.target.value)}>
          <option value="">Cámara predeterminada</option>{devices.map((d, i) => <option key={d.deviceId} value={d.deviceId}>{d.label || `Cámara ${i + 1}`}</option>)}
        </select></section>
        <section><h2>Mis filtros</h2>{saved.length === 0 ? <p className="small">Todavía no hay filtros guardados.</p> :
          <div className="saved-list">{saved.map(f => <div key={f.name} className="saved-row"><button onClick={() => {
            setStrokes(f.strokes); setRedo([]); setPreset(f.preset); setIntensity(f.intensity); setRadius(f.radius);
          }}>{f.name}</button><button aria-label={`Eliminar ${f.name}`} title="Eliminar" onClick={() => { deleteFilter(f.name); setSaved(readFilters()); }}>×</button></div>)}</div>}</section>
        <Toolbar onUndo={undo} onRedo={redoLast} onReset={reset} onSave={storeCurrent} onMirror={openMirror}
          onFullscreen={() => void stageRef.current?.requestFullscreen?.().catch(() => setStatus('Pantalla completa no permitida por el navegador'))}
          canUndo={strokes.length > 0} canRedo={redo.length > 0} />
        <p className="small footer-help">Espejo: pulsa Esc o «Salir del espejo». Doble clic también permite salir.</p>
      </aside>}
    </main>
  </div>;
}
