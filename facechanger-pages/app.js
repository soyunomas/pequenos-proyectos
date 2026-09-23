import { FILTERS, MAX_CONTROLS, compose, faceFromLandmarks } from './engine/geometry.js';
import { PointerDeformer, mapPointer } from './engine/pointer.js';
import { Renderer } from './engine/renderer.js';
import { listFilters, removeFilter, saveFilter } from './engine/storage.js';

// Sin Node ni bundle. El módulo, WASM y modelo se descargan; la imagen se procesa en el equipo.
const MEDIAPIPE = 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.35';
const MODEL = 'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task';
const $ = id => document.getElementById(id);
const video = $('webcam'), canvas = $('mirror'), stage = $('stage'), start = $('start');
const gesture = new PointerDeformer();
const state = {
  preset: 'normal', strokes: [], undone: [], intensity: 100, radius: .19, editable: true,
  face: null, live: null, stream: null, tracker: null, renderer: null,
  raf: 0, startupToken: 0, lastDetectAt: -Infinity, lastVideoTime: -1,
  mirror: false, settingsOpen: false, exitTimer: 0, running: false,
};

function status(message, good = false) {
  $('status-text').textContent = message;
  $('led').classList.toggle('good', good);
}
function controls() {
  return compose(state.face, state.preset,
    [...state.strokes, ...(state.live ? [state.live] : [])], state.intensity);
}
function refreshButtons() {
  $('undo').disabled = state.strokes.length === 0;
  $('redo').disabled = state.undone.length === 0;
}
function refreshFilters() {
  $('filters').replaceChildren(...FILTERS.map(([id, label]) => {
    const button = document.createElement('button');
    button.type = 'button'; button.textContent = label;
    button.classList.toggle('selected', state.preset === id);
    button.setAttribute('aria-pressed', String(state.preset === id));
    button.addEventListener('click', () => { state.preset = id; refreshFilters(); });
    return button;
  }));
}
function refreshSaved() {
  const filters = listFilters(); const host = $('saved'); host.replaceChildren();
  if (!filters.length) {
    const p = document.createElement('p'); p.className = 'help';
    p.textContent = 'Aún no tienes filtros guardados.'; host.append(p); return;
  }
  filters.forEach(filter => {
    const row = document.createElement('div'); row.className = 'saved-row';
    const load = document.createElement('button'); load.textContent = filter.name;
    load.addEventListener('click', () => {
      state.strokes = structuredClone(filter.strokes); state.undone = [];
      state.preset = filter.preset; state.intensity = filter.intensity; state.radius = filter.radius;
      $('intensity').value = state.intensity; $('radius').value = Math.round(state.radius * 100);
      refreshNumbers(); refreshButtons(); refreshFilters();
    });
    const del = document.createElement('button'); del.textContent = '×';
    del.setAttribute('aria-label', 'Eliminar ' + filter.name);
    del.addEventListener('click', () => { removeFilter(filter.name); refreshSaved(); });
    row.append(load, del); host.append(row);
  });
}
function refreshNumbers() {
  $('intensity-value').textContent = state.intensity + ' %';
  $('radius-value').textContent = Math.round(state.radius * 100) + ' %';
}
function errorText(err) {
  if (err?.name === 'NotAllowedError' || err?.name === 'PermissionDeniedError')
    return 'Acceso a la cámara denegado. Permite la cámara en la barra de direcciones y vuelve a pulsar Activar cámara.';
  if (err?.name === 'NotFoundError') return 'No se encuentra ninguna webcam conectada.';
  if (err?.name === 'NotReadableError') return 'La webcam está ocupada por otra aplicación o no responde.';
  return err?.message || String(err);
}
async function refreshDevices() {
  try {
    const selected = $('cameras').value;
    const devices = (await navigator.mediaDevices.enumerateDevices()).filter(d => d.kind === 'videoinput');
    const options = [new Option('Cámara predeterminada', '')];
    devices.forEach((d, i) => options.push(new Option(d.label || 'Cámara ' + (i + 1), d.deviceId)));
    $('cameras').replaceChildren(...options);
    $('cameras').value = devices.some(d => d.deviceId === selected) ? selected : '';
  } catch { /* permisos limitados: mantener selector básico */ }
}
function stopCamera() {
  state.startupToken++;
  cancelAnimationFrame(state.raf); state.raf = 0; state.running = false;
  gesture.cancel(); state.live = null; state.face = null;
  state.lastVideoTime = -1; state.lastDetectAt = -Infinity;
  state.stream?.getTracks().forEach(track => track.stop()); state.stream = null;
  video.pause(); video.srcObject = null;
}
async function startCamera(deviceId = '') {
  stopCamera(); const token = state.startupToken;
  start.hidden = false; $('start-error').textContent = '';
  $('start-camera').disabled = true; $('start-camera').textContent = 'Iniciando cámara…';
  try {
    if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia)
      throw Error('Esta cámara requiere HTTPS o localhost. Abre la página publicada en GitHub Pages.');
    status('Solicitando permiso para la webcam…');
    const stream = await navigator.mediaDevices.getUserMedia({ audio: false, video: {
      width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 30 },
      ...(deviceId ? { deviceId: { exact: deviceId } } : { facingMode: 'user' })
    } });
    if (token !== state.startupToken) { stream.getTracks().forEach(t => t.stop()); return; }
    state.stream = stream;
    video.srcObject = stream;
    await video.play();
    if (token !== state.startupToken) return;
    if (!state.renderer) state.renderer = new Renderer(canvas);
    if (!state.tracker) {
      status('Descargando MediaPipe y el modelo facial (solo la primera vez)…');
      const { FaceLandmarker, FilesetResolver } = await import(MEDIAPIPE + '/vision_bundle.mjs');
      if (token !== state.startupToken) return;
      const vision = await FilesetResolver.forVisionTasks(MEDIAPIPE + '/wasm');
      if (token !== state.startupToken) return;
      state.tracker = await FaceLandmarker.createFromOptions(vision, {
        baseOptions: { modelAssetPath: MODEL, delegate: 'CPU' },
        runningMode: 'VIDEO', numFaces: 1,
        minFaceDetectionConfidence: .5, minFacePresenceConfidence: .5, minTrackingConfidence: .5
      });
    }
    if (token !== state.startupToken) return;
    start.hidden = true; state.running = true;
    status('Buscando tu rostro…');
    await refreshDevices();
    video.addEventListener('ended', () => { if (token === state.startupToken) { stopCamera(); showError('Cámara desconectada. Conecta una webcam y pulsa Activar cámara.'); } }, { once: true });
    state.raf = requestAnimationFrame(render);
  } catch (err) {
    if (token !== state.startupToken) return;
    stopCamera(); showError(errorText(err));
  } finally {
    if (token === state.startupToken) {
      $('start-camera').disabled = false; $('start-camera').textContent = 'Activar cámara';
    } else if (!state.running) {
      $('start-camera').disabled = false; $('start-camera').textContent = 'Activar cámara';
    }
  }
}
function showError(message) {
  start.hidden = false; $('start-error').textContent = message; status(message);
}
function render(now) {
  if (!state.running) return;
  try {
    if (state.stream?.getVideoTracks().some(t => t.readyState === 'ended')) {
      stopCamera(); showError('Webcam desconectada. Vuelve a conectarla y activa la cámara.'); return;
    }
    if (video.readyState >= 2) {
      if (now - state.lastDetectAt >= 65 && video.currentTime !== state.lastVideoTime) {
        state.lastDetectAt = now; state.lastVideoTime = video.currentTime;
        const landmarks = state.tracker.detectForVideo(video, now).faceLandmarks[0];
        if (landmarks) {
          const next = faceFromLandmarks(landmarks);
          // Suavizado temporal para reducir el jitter sin retener datos al perder el rostro.
          if (next && state.face && next.landmarks.length === state.face.landmarks.length) {
            next.landmarks = next.landmarks.map((p, i) => ({
              x: state.face.landmarks[i].x * .56 + p.x * .44,
              y: state.face.landmarks[i].y * .56 + p.y * .44
            }));
            next.frame = faceFromLandmarks(next.landmarks.map(p => ({ x: 1 - p.x, y: p.y }))).frame;
          }
          if (!state.face) status('Rostro detectado · proceso local', true);
          state.face = next;
        } else {
          if (state.face) status('No se detecta rostro. Se muestra vídeo sin deformar.');
          state.face = null;
        }
      }
      state.renderer.draw(video, controls());
    }
  } catch (err) { stopCamera(); showError('Error de procesamiento: ' + errorText(err)); return; }
  state.raf = requestAnimationFrame(render);
}
function point(event) {
  return mapPointer(event, canvas.getBoundingClientRect(), video.videoWidth, video.videoHeight);
}
canvas.addEventListener('pointerdown', event => {
  if (!state.editable || state.mirror || state.settingsOpen || !state.face) return;
  const p = point(event);
  if (gesture.begin(event.pointerId, p, state.face, state.radius, controls(), video.videoWidth / video.videoHeight)) {
    canvas.setPointerCapture(event.pointerId); event.preventDefault();
  }
});
canvas.addEventListener('pointermove', event => {
  if (!gesture.active) return;
  const p = point(event);
  if (p) state.live = gesture.move(event.pointerId, p);
  event.preventDefault();
});
function pointerEnd(event, commit) {
  if (gesture.active?.id !== event.pointerId) return;
  const stroke = gesture.end(event.pointerId, commit);
  if (stroke) { state.strokes = [...state.strokes, stroke].slice(-MAX_CONTROLS); state.undone = []; }
  state.live = null;
  if (canvas.hasPointerCapture(event.pointerId)) canvas.releasePointerCapture(event.pointerId);
  refreshButtons(); event.preventDefault();
}
canvas.addEventListener('pointerup', event => pointerEnd(event, true));
canvas.addEventListener('pointercancel', event => pointerEnd(event, false));
canvas.addEventListener('lostpointercapture', () => { gesture.cancel(); state.live = null; });
$('start-camera').addEventListener('click', () => startCamera($('cameras').value));
$('cameras').addEventListener('change', e => startCamera(e.target.value));
$('manual').addEventListener('change', e => { state.editable = e.target.checked; if (!state.editable) { gesture.cancel(); state.live = null; } });
$('intensity').addEventListener('input', e => { state.intensity = +e.target.value; refreshNumbers(); });
$('radius').addEventListener('input', e => { state.radius = +e.target.value / 100; refreshNumbers(); });
$('undo').addEventListener('click', () => {
  if (state.strokes.length) state.undone.push(state.strokes.pop()); refreshButtons();
});
$('redo').addEventListener('click', () => {
  if (state.undone.length) state.strokes.push(state.undone.pop()); refreshButtons();
});
$('reset').addEventListener('click', () => {
  state.strokes = []; state.undone = []; state.live = null; gesture.cancel();
  state.preset = 'normal'; state.intensity = 100; $('intensity').value = '100';
  refreshFilters(); refreshNumbers(); refreshButtons();
});
$('save').addEventListener('click', () => {
  const name = prompt('Nombre del filtro personalizado:')?.trim(); if (!name) return;
  try {
    saveFilter({ version: 1, name, preset: state.preset, intensity: state.intensity,
      radius: state.radius, strokes: structuredClone(state.strokes) }); refreshSaved();
  } catch (err) { alert(errorText(err)); }
});
$('fullscreen').addEventListener('click', async () => {
  try { await stage.requestFullscreen(); }
  catch { status('El navegador no permite la pantalla completa.'); }
});
const panel = $('panel');
const gear = $('settings-open');
const scrim = $('settings-scrim');
const closeSettingsButton = $('settings-close');
function openSettings() {
  if (state.mirror || state.settingsOpen) return;
  state.settingsOpen = true;
  gesture.cancel(); state.live = null;
  panel.inert = false;
  $('app').classList.add('settings-open');
  gear.setAttribute('aria-expanded', 'true');
  panel.scrollTop = 0;
  closeSettingsButton.focus({ preventScroll: true });
}
function closeSettings() {
  if (!state.settingsOpen) return;
  state.settingsOpen = false;
  $('app').classList.remove('settings-open');
  panel.inert = true;
  gear.setAttribute('aria-expanded', 'false');
  gear.focus({ preventScroll: true });
}
gear.addEventListener('click', openSettings);
closeSettingsButton.addEventListener('click', closeSettings);
scrim.addEventListener('click', closeSettings);
panel.addEventListener('keydown', event => {
  if (event.key !== 'Tab') return;
  const focusable = [...panel.querySelectorAll('button:not(:disabled), input:not(:disabled), select:not(:disabled), [tabindex="0"]')]
    .filter(el => el.getClientRects().length > 0);
  if (!focusable.length) return;
  const first = focusable[0], last = focusable[focusable.length - 1];
  if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
  else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
});
function revealExit() {
  $('exit-mirror').classList.add('visible'); clearTimeout(state.exitTimer);
  state.exitTimer = setTimeout(() => $('exit-mirror').classList.remove('visible'), 3500);
}
async function enterMirror() {
  closeSettings();
  state.mirror = true; $('app').classList.add('is-mirror'); revealExit();
  try { await stage.requestFullscreen(); } catch { /* vista espejo sigue sin fullscreen */ }
}
async function leaveMirror() {
  state.mirror = false; $('app').classList.remove('is-mirror');
  $('exit-mirror').classList.remove('visible');
  if (document.fullscreenElement) try { await document.exitFullscreen(); } catch { /* ya salió */ }
}
$('mirror-mode').addEventListener('click', enterMirror);
$('exit-mirror').addEventListener('click', leaveMirror);
stage.addEventListener('pointermove', () => { if (state.mirror) revealExit(); });
stage.addEventListener('pointerdown', e => { if (state.mirror && e.clientX > stage.getBoundingClientRect().right - 90 && e.clientY < stage.getBoundingClientRect().top + 85) revealExit(); });
stage.addEventListener('dblclick', () => { if (state.mirror) leaveMirror(); });
document.addEventListener('keydown', e => { if (e.key === 'Escape') { if (state.settingsOpen) closeSettings(); else if (state.mirror) leaveMirror(); } });
document.addEventListener('fullscreenchange', () => { if (!document.fullscreenElement && state.mirror) leaveMirror(); });
window.addEventListener('pagehide', () => { stopCamera(); state.tracker?.close(); state.tracker = null; state.renderer?.dispose(); state.renderer = null; });
document.addEventListener('visibilitychange', () => {
  if (document.hidden) { cancelAnimationFrame(state.raf); state.raf = 0; }
  else if (state.running && !state.raf) state.raf = requestAnimationFrame(render);
});
refreshFilters(); refreshSaved(); refreshButtons(); refreshNumbers();
if (!window.isSecureContext) showError('Para acceder a la webcam abre esta página con HTTPS (GitHub Pages) o localhost.');
