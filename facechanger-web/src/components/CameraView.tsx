import { useEffect, useRef, useState } from 'react';
import { FaceTracker } from '../engine/FaceTracker';
import { WebGLRenderer } from '../engine/WebGLRenderer';
import { TouchDeformer } from '../engine/TouchDeformer';
import { composeControls } from '../engine/FaceWarpEngine';
import { mapPointer } from '../engine/CoordinateMapper';
import type { Face, FilterId, Stroke } from '../types/face';

type Props = {
  strokes: Stroke[]; preset: FilterId; intensity: number; radius: number; editable: boolean;
  deviceId: string; onStroke: (stroke: Stroke) => void;
  onDevices: (devices: MediaDeviceInfo[]) => void;
  onStatus: (text: string) => void; onFace: (face: Face | null) => void;
};
export function CameraView(props: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null), videoRef = useRef<HTMLVideoElement>(null);
  const latest = useRef(props); latest.current = props;
  const currentFace = useRef<Face | null>(null);
  const gesture = useRef(new TouchDeformer());
  const [liveStroke, setLiveStroke] = useState<Stroke | null>(null);
  const [retry, setRetry] = useState(0);
  const [error, setError] = useState('');
  const frameId = useRef(0);
  const currentStroke = useRef<Stroke | null>(null);
  currentStroke.current = liveStroke;
  useEffect(() => {
    let disposed = false, tracker: FaceTracker | null = null, renderer: WebGLRenderer | null = null, stream: MediaStream | null = null;
    const canvas = canvasRef.current, video = videoRef.current;
    if (!canvas || !video) return;
    const contextLost = (event: Event) => { event.preventDefault(); latest.current.onStatus('Se ha perdido el contexto gráfico. Reiniciando…'); };
    const contextRestored = () => setRetry(i => i + 1);
    canvas.addEventListener('webglcontextlost', contextLost);
    canvas.addEventListener('webglcontextrestored', contextRestored);
    async function start() {
      try {
        latest.current.onStatus('Solicitando acceso a la cámara…');
        stream = await navigator.mediaDevices.getUserMedia({ audio: false, video: {
          width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 30 },
          ...(latest.current.deviceId ? { deviceId: { exact: latest.current.deviceId } } : { facingMode: 'user' }),
        } });
        if (disposed) { stream.getTracks().forEach(t => t.stop()); return; }
        video!.srcObject = stream;
        await video!.play();
        if (disposed) return;
        latest.current.onStatus('Cargando detector facial local…');
        renderer = new WebGLRenderer(canvas!);
        tracker = await FaceTracker.create();
        if (disposed) { tracker.close(); return; }
        setError('');
        latest.current.onStatus('Buscando rostro…');
        const devices = await navigator.mediaDevices.enumerateDevices().catch(() => [] as MediaDeviceInfo[]);
        if (!disposed) latest.current.onDevices(devices.filter(d => d.kind === 'videoinput'));
        let lastPresence: boolean | null = null;
        const render = (now: number) => {
          if (disposed) return;
          if (stream?.getVideoTracks().every(t => t.readyState === 'ended')) {
            const message = 'Cámara desconectada. Selecciona otra o reintenta.';
            latest.current.onStatus(message); latest.current.onFace(null); setError(message); return;
          }
          try {
            const face = tracker!.update(video!, now);
            currentFace.current = face;
            if (!!face !== lastPresence) {
              lastPresence = !!face;
              latest.current.onFace(face);
              latest.current.onStatus(face ? 'Rostro detectado · procesamiento local' : 'Acércate a la cámara para detectar el rostro');
            }
            const p = latest.current;
            renderer!.draw(video!, composeControls(face, p.preset, [...p.strokes, ...(currentStroke.current ? [currentStroke.current] : [])], p.intensity));
          } catch (err) {
            latest.current.onStatus(`Error de procesamiento: ${String(err)}`);
            return;
          }
          frameId.current = requestAnimationFrame(render);
        };
        frameId.current = requestAnimationFrame(render);
      } catch (err) {
        if (disposed) return;
        const message = err instanceof DOMException && err.name === 'NotAllowedError'
          ? 'Permiso de cámara denegado. Actívalo en la barra de direcciones y pulsa Reintentar.'
          : `No se ha podido iniciar: ${err instanceof Error ? err.message : String(err)}`;
        latest.current.onStatus(message); setError(message);
      }
    }
    void start();
    return () => {
      disposed = true; cancelAnimationFrame(frameId.current);
      gesture.current.cancel(); currentStroke.current = null; currentFace.current = null;
      tracker?.close(); renderer?.dispose();
      stream?.getTracks().forEach(t => t.stop()); video.srcObject = null;
      canvas.removeEventListener('webglcontextlost', contextLost);
      canvas.removeEventListener('webglcontextrestored', contextRestored);
    };
  }, [props.deviceId, retry]);

  function locate(e: React.PointerEvent<HTMLCanvasElement>) {
    const canvas = canvasRef.current!, video = videoRef.current!, rect = canvas.getBoundingClientRect();
    return mapPointer({ x: e.clientX, y: e.clientY }, rect, video.videoWidth, video.videoHeight);
  }
  function down(e: React.PointerEvent<HTMLCanvasElement>) {
    if (!latest.current.editable || !currentFace.current) return;
    const point = locate(e);
    if (point && gesture.current.begin(e.pointerId, point, currentFace.current, latest.current.radius)) {
      e.currentTarget.setPointerCapture(e.pointerId);
      e.preventDefault();
    }
  }
  function move(e: React.PointerEvent<HTMLCanvasElement>) {
    if (!gesture.current.active) return;
    const at = locate(e);
    if (at) {
      const stroke = gesture.current.move(e.pointerId, at);
      if (stroke) { currentStroke.current = stroke; setLiveStroke(stroke); }
    }
    e.preventDefault();
  }
  function end(e: React.PointerEvent<HTMLCanvasElement>, commit: boolean) {
    if (gesture.current.active?.pointerId !== e.pointerId) return;
    const stroke = gesture.current.end(e.pointerId, commit);
    if (e.currentTarget.hasPointerCapture(e.pointerId)) e.currentTarget.releasePointerCapture(e.pointerId);
    if (stroke) latest.current.onStroke(stroke);
    currentStroke.current = null; setLiveStroke(null);
    e.preventDefault();
  }
  return <div className="camera-surface">
    <video ref={videoRef} playsInline muted autoPlay aria-hidden="true" />
    <canvas ref={canvasRef} aria-label="Imagen de webcam deformable" data-testid="mirror-canvas"
      onPointerDown={down} onPointerMove={move} onPointerUp={e => end(e, true)}
      onPointerCancel={e => end(e, false)} onLostPointerCapture={() => { gesture.current.cancel(); currentStroke.current = null; setLiveStroke(null); }} />
    {error && <div className="camera-error"><p>{error}</p><button onClick={() => setRetry(n => n + 1)}>Reintentar cámara</button></div>}
  </div>;
}
