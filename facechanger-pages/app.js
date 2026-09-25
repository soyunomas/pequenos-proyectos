import { FILTERS, FILTER_GROUPS, normalizeEffects, MAX_CONTROLS, compose, faceFromLandmarks, forwardWarp } from './engine/geometry.js';
import { PointerDeformer, mapPointer } from './engine/pointer.js';
import { MAX_FACES, trackFaces, nearestFace } from './engine/faces.js';
import { drawMakeup, makeupStrength } from './engine/makeup.js';
import { Renderer } from './engine/renderer.js';
import { listFilters, removeFilter, saveFilter } from './engine/storage.js';
import { activeCreatures, creatureConfig, creaturePosition, creatureHeading, creatureGaitFrame, localLighting, CREATURE_DEFAULTS, CREATURE_IDS } from './engine/creatures.js?v=clean-topdown-2';

// Sin Node ni bundle. El módulo, WASM y modelo se descargan; la imagen se procesa en el equipo.
const MEDIAPIPE = 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@0.10.35';
const MODEL = 'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task';
const $ = id => document.getElementById(id);
const video = $('webcam'), canvas = $('mirror'), stage = $('stage'), start = $('start');
const creatureLayer = $('creature-layer'), creatureContext = creatureLayer.getContext('2d');
const makeupLayer = $('makeup-layer'), makeupContext = makeupLayer.getContext('2d');
const creatureImages=Object.fromEntries(CREATURE_IDS.map(id=>{
  const img=new Image();img.decoding='async';img.src='./assets/'+id+'.webp'+(id==='spider'?'':'?v=clean-topdown-2');return [id,img];
}));
const creatureSprites=Object.fromEntries(CREATURE_IDS.map(id=>[id,document.createElement('canvas')]));
const creatureFrames={spider:-1,cockroach:-1,wasp:-1};
const lightCanvas=document.createElement('canvas'),lightContext=lightCanvas.getContext('2d',{willReadFrequently:true});
lightCanvas.width=96;lightCanvas.height=54;
let lightPixels=null,lightSampleAt=-Infinity;
let creaturesVisible=false,creatureElapsed=0,creatureLastTick=0;
const reduceMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
const gesture = new PointerDeformer();
const state = {
  preset: 'spider', effects: [], strokes: [], undone: [], intensity: 100, radius: .19, editable: true,
  spiderSpeed: CREATURE_DEFAULTS.speed, spiderSize: CREATURE_DEFAULTS.size,
  creatureCount: CREATURE_DEFAULTS.count,
  face: null, faces: [], live: null, stream: null, tracker: null, renderer: null,
  raf: 0, startupToken: 0, lastDetectAt: -Infinity, lastVideoTime: -1,
  mirror: false, settingsOpen: false, filterPickerOpen: false, exitTimer: 0, running: false,
};

function status(message, good = false) {
  $('status-text').textContent = message;
  $('led').classList.toggle('good', good);
}
function controls(face=state.face) {
  return compose(face, state.preset,
    [...state.strokes, ...(state.live ? [state.live] : [])], state.intensity, state.effects);
}
function refreshButtons() {
  $('undo').disabled = state.strokes.length === 0;
  $('redo').disabled = state.undone.length === 0;
}
const filterName = id => FILTERS.find(([key])=>key===id)?.[1] ?? id;
function makeGroups(select, placeholder=false) {
  const groups=FILTER_GROUPS.map(([group,items])=>{
    const opt=document.createElement('optgroup');opt.label=group;
    for(const [id,label] of items){
      if(placeholder && id==='normal')continue;
      opt.append(new Option(label,id));
    }
    return opt;
  });
  select.replaceChildren(...(placeholder?[new Option('Seleccionar efecto…','')]:[]),...groups);
}
function refreshFilters() {
  $('filters').value = state.preset;
  $('selected-filter-title').textContent = filterName(state.preset);
  $('selected-filter-category').textContent =
    FILTER_GROUPS.find(([,items])=>items.some(([id])=>id===state.preset))?.[0] || 'Efectos';
  for (const button of $('filter-list').querySelectorAll('button[data-filter-id]')) {
    const selected = button.dataset.filterId === state.preset;
    button.setAttribute('aria-pressed', String(selected));
    button.classList.toggle('active', selected);
    const mark = button.querySelector('.filter-check');
    if (mark) mark.textContent = selected ? '✓' : '';
  }
}
function selectPreset(id) {
  if (!FILTERS.some(([key]) => key === id)) return;
  state.preset = id;
  if (id.startsWith('uncanny-') || id === 'eyes-big' || CREATURE_IDS.includes(id) || id === 'makeup-green') {
    state.intensity = id === 'eyes-big' ? 75 : id.startsWith('uncanny-') ? 40 : 100;
    $('intensity').value = String(state.intensity);
    refreshNumbers();
  }
  refreshFilters();
}
const fold = value => value.toLocaleLowerCase('es')
  .normalize('NFD').replace(/[\u0300-\u036f]/g, '');
function renderFilterList(query = '') {
  const q = fold(query.trim());
  const container = $('filter-list');
  const fragment = document.createDocumentFragment();
  let found = 0;
  for (const [group, items] of FILTER_GROUPS) {
    const matches = items.filter(([,name]) =>
      fold(name).includes(q) || fold(group).includes(q));
    if (!matches.length) continue;
    found += matches.length;
    const section = document.createElement('section');
    section.className = 'picker-group';
    const heading = document.createElement('h3');
    heading.textContent = group;
    section.append(heading);
    for (const [id, label] of matches) {
      const button = document.createElement('button');
      button.type = 'button';
      button.dataset.filterId = id;
      button.className = 'filter-option';
      button.setAttribute('aria-pressed', String(id === state.preset));
      if (id === state.preset) button.classList.add('active');
      const title = document.createElement('span');
      title.className = 'filter-option-title';
      title.textContent = label;
      const mark = document.createElement('span');
      mark.className = 'filter-check';
      mark.setAttribute('aria-hidden', 'true');
      mark.textContent = id === state.preset ? '✓' : '';
      button.append(title, mark);
      button.addEventListener('click', () => {
        selectPreset(id);
        closeFilterPicker();
      });
      section.append(button);
    }
    fragment.append(section);
  }
  container.replaceChildren(fragment);
  $('filter-empty').hidden = found > 0;
}

function refreshEffects() {
  const host=$('effects');host.replaceChildren();
  for(const effect of state.effects){
    const row=document.createElement('div');row.className='effect-row';
    const title=document.createElement('span');title.className='effect-title';
    title.textContent=filterName(effect.preset);
    const value=document.createElement('output');value.textContent=effect.intensity+' %';
    const slider=document.createElement('input');slider.type='range';slider.min='0';slider.max='100';
    slider.value=effect.intensity;slider.setAttribute('aria-label','Intensidad de '+filterName(effect.preset));
    slider.addEventListener('input',()=>{effect.intensity=+slider.value;value.textContent=slider.value+' %'});
    const remove=document.createElement('button');remove.type='button';remove.className='remove-effect';
    remove.textContent='×';remove.setAttribute('aria-label','Quitar '+filterName(effect.preset));
    remove.addEventListener('click',()=>{state.effects=state.effects.filter(e=>e!==effect);refreshEffects()});
    row.append(title,value,slider,remove);host.append(row);
  }
  $('add-effect').disabled=state.effects.length>=4;
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
      state.preset = filter.preset; state.effects = normalizeEffects(filter.effects); state.intensity = filter.intensity; state.radius = filter.radius;
      const creature=creatureConfig(filter.creatures,filter.spider);
      state.spiderSpeed=creature.speed;state.spiderSize=creature.size;state.creatureCount=creature.count;
      $('spider-speed').value=String(creature.speed);$('spider-size').value=String(creature.size);
      $('creature-count').value=String(creature.count);
      $('intensity').value = state.intensity; $('radius').value = Math.round(state.radius * 100);
      refreshNumbers(); refreshButtons(); refreshFilters(); refreshEffects();
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
  $('spider-speed-value').textContent=state.spiderSpeed+' %';
  $('spider-size-value').textContent=state.spiderSize+' %';
  $('creature-count-value').textContent=String(state.creatureCount);
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
  gesture.cancel(); state.live = null; state.face = null; state.faces = [];
  state.lastVideoTime = -1; state.lastDetectAt = -Infinity;
  state.stream?.getTracks().forEach(track => track.stop()); state.stream = null;
  video.pause(); video.srcObject = null;
  clearCreatures();
  makeupContext.clearRect(0,0,makeupLayer.width,makeupLayer.height);
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
        runningMode: 'VIDEO', numFaces: MAX_FACES,
        minFaceDetectionConfidence: .5, minFacePresenceConfidence: .5, minTrackingConfidence: .5
      });
    }
    if (token !== state.startupToken) return;
    start.hidden = true; state.running = true;
    status('Buscando hasta cinco rostros…');
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
        const detected=state.tracker.detectForVideo(video,now).faceLandmarks;
        const before=state.faces.length;
        state.faces=trackFaces(state.faces,detected);
        state.face=state.faces[0]||null;
        if(before!==state.faces.length){
          const n=state.faces.length;
          status(n===0?'No se detectan caras. Se muestra vídeo sin deformar.':
            n+' '+(n===1?'rostro detectado':'rostros detectados')+' · proceso local',n>0);
        }
      }
      const applied=state.faces.map(face=>controls(face));
      state.renderer.draw(video,applied);
      drawMakeups(applied);
      drawCreatures(now,applied);
    }
  } catch (err) { stopCamera(); showError('Error de procesamiento: ' + errorText(err)); return; }
  state.raf = requestAnimationFrame(render);
}
function clearCreatures() {
  if(creaturesVisible)creatureContext.clearRect(0,0,creatureLayer.width,creatureLayer.height);
  creaturesVisible=false;creatureElapsed=0;creatureLastTick=0;
  for(const id of CREATURE_IDS)creatureFrames[id]=-1;
}
function sampleLighting(now) {
  // Unfiltered local webcam, downsampled and read at most five times per second.
  if(now-lightSampleAt<190 && lightPixels)return;
  lightSampleAt=now;
  try {
    lightContext.drawImage(video,0,0,lightCanvas.width,lightCanvas.height);
    lightPixels=lightContext.getImageData(0,0,lightCanvas.width,lightCanvas.height).data;
  } catch {lightPixels=null;}
}
function colorBelow(anchor) {
  if(!lightPixels)return null;
  const w=lightCanvas.width,h=lightCanvas.height;
  // Mirrored landmarks -> unmirrored camera video.
  const x=Math.max(1,Math.min(w-2,Math.round((1-anchor.x)*w)));
  const y=Math.max(1,Math.min(h-2,Math.round(anchor.y*h)));
  const values=[0,0,0];
  for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){
    const at=((y+dy)*w+x+dx)*4;
    for(let channel=0;channel<3;channel++)values[channel]+=lightPixels[at+channel];
  }
  return values.map(v=>v/9);
}
function drawMakeups(applied){
  const w=video.videoWidth,h=video.videoHeight;
  if(makeupLayer.width!==w||makeupLayer.height!==h){
    makeupLayer.width=w;makeupLayer.height=h;
  }
  makeupContext.clearRect(0,0,w,h);
  const strength=makeupStrength(state.preset,state.effects,state.intensity);
  if(!strength)return;
  state.faces.forEach((face,i)=>drawMakeup(makeupContext,face,applied[i],strength,w,h));
}
function drawCreatures(now,applied) {
  const entries=activeCreatures(state.preset,state.effects,state.intensity,state.creatureCount)
    .filter(entry=>creatureImages[entry.id].complete&&creatureImages[entry.id].naturalWidth);
  if(!state.faces.length||!entries.length){clearCreatures();return;}
  if(creatureLayer.width!==video.videoWidth||creatureLayer.height!==video.videoHeight){
    creatureLayer.width=video.videoWidth;creatureLayer.height=video.videoHeight;
    lightSampleAt=-Infinity;
  }
  const aspect=video.videoWidth/video.videoHeight;
  if(!creatureLastTick)creatureLastTick=now;
  const delta=Math.max(0,Math.min(50,now-creatureLastTick));
  creatureLastTick=now;
  if(!reduceMotion)creatureElapsed+=delta*state.spiderSpeed/100;
  sampleLighting(now);
  const ctx=creatureContext;
  ctx.clearRect(0,0,creatureLayer.width,creatureLayer.height);
  creaturesVisible=true;
  state.faces.forEach((face,faceIndex)=>{
    for(const entry of entries){
      const id=entry.id,image=creatureImages[id],sprite=creatureSprites[id];
      const elapsed=reduceMotion?0:creatureElapsed*entry.speed;
      const progress=(elapsed/16000+entry.phase+faceIndex*.137)%1;
      const source=creaturePosition(face,progress,aspect);
      const aheadSource=creaturePosition(face,(progress+.0015)%1,aspect);
      if(!source||!aheadSource)continue;
      const anchor=forwardWarp(source,applied[faceIndex],aspect);
      const ahead=forwardWarp(aheadSource,applied[faceIndex],aspect);
      const heading=creatureHeading(id,(ahead.x-anchor.x)*creatureLayer.width,
        (ahead.y-anchor.y)*creatureLayer.height);
      const tick=reduceMotion?0:Math.floor(elapsed/110);
      if(creatureFrames[id]!==tick){
        creatureGaitFrame(image,sprite,tick*Math.PI/2,id);
        creatureFrames[id]=tick;
      }
      const x=anchor.x*creatureLayer.width,y=anchor.y*creatureLayer.height;
      const size=Math.max(12,face.frame.width*creatureLayer.width*.19*
        state.spiderSize/100*entry.scale*(id==='wasp'?.82:id==='cockroach'?1.04:1));
      const light=localLighting(colorBelow(source),id);
      ctx.save();ctx.translate(x,y);ctx.rotate(heading);
      ctx.translate(0,reduceMotion?0:Math.sin(elapsed/110)*size*.012);
      ctx.save();ctx.globalAlpha=entry.intensity*light.shadow*.55;
      ctx.filter='blur(5px)';ctx.fillStyle='#000';
      ctx.beginPath();ctx.ellipse(3,size*.08,size*.27,size*.12,0,0,Math.PI*2);
      ctx.fill();ctx.restore();
      ctx.save();ctx.globalAlpha=entry.intensity*light.shadow;
      ctx.filter='brightness(0) blur(2.5px)';
      ctx.drawImage(sprite,-size/2+2,-size/2+3,size,size);ctx.restore();
      ctx.globalAlpha=entry.intensity;
      ctx.filter='brightness('+light.brightness+') contrast(1.08)';
      ctx.drawImage(sprite,-size/2,-size/2,size,size);
      ctx.restore();
    }
  });
}
function point(event) {
  return mapPointer(event, canvas.getBoundingClientRect(), video.videoWidth, video.videoHeight);
}
canvas.addEventListener('pointerdown', event => {
  if (!state.editable || state.mirror || state.settingsOpen || !state.faces.length) return;
  const p = point(event);
  const face=nearestFace(state.faces,p,video.videoWidth/video.videoHeight);
  if (gesture.begin(event.pointerId, p, face, state.radius, controls(face), video.videoWidth / video.videoHeight)) {
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
makeGroups($('filters'));
makeGroups($('extra-filter'),true);
$('filters').addEventListener('change',event=>selectPreset(event.target.value));
$('add-effect').addEventListener('click',()=>{
  const preset=$('extra-filter').value;
  if(!preset || state.effects.length>=4 || state.preset===preset || state.effects.some(e=>e.preset===preset))return;
  state.effects.push({preset,intensity:CREATURE_IDS.includes(preset)||preset==='makeup-green'?100:40});$('extra-filter').value='';
  refreshEffects();
});
$('manual').addEventListener('change', e => { state.editable = e.target.checked; if (!state.editable) { gesture.cancel(); state.live = null; } });
$('intensity').addEventListener('input', e => { state.intensity = +e.target.value; refreshNumbers(); });
$('radius').addEventListener('input', e => { state.radius = +e.target.value / 100; refreshNumbers(); });
$('spider-speed').addEventListener('input',e=>{state.spiderSpeed=+e.target.value;refreshNumbers();});
$('spider-size').addEventListener('input',e=>{state.spiderSize=+e.target.value;refreshNumbers();});
$('creature-count').addEventListener('input',e=>{state.creatureCount=+e.target.value;refreshNumbers();});
$('undo').addEventListener('click', () => {
  if (state.strokes.length) state.undone.push(state.strokes.pop()); refreshButtons();
});
$('redo').addEventListener('click', () => {
  if (state.undone.length) state.strokes.push(state.undone.pop()); refreshButtons();
});
function resetEffects() {
  state.strokes = []; state.undone = []; state.live = null; gesture.cancel();
  state.preset = 'normal'; state.effects = []; state.intensity = 100; $('intensity').value = '100';
  state.spiderSpeed=CREATURE_DEFAULTS.speed;state.spiderSize=CREATURE_DEFAULTS.size;
  state.creatureCount=CREATURE_DEFAULTS.count;
  $('spider-speed').value=String(CREATURE_DEFAULTS.speed);
  $('spider-size').value=String(CREATURE_DEFAULTS.size);
  $('creature-count').value=String(CREATURE_DEFAULTS.count);
  clearCreatures();
  refreshFilters(); refreshEffects(); refreshNumbers(); refreshButtons();
}
$('reset').addEventListener('click', resetEffects);
$('quick-reset').addEventListener('click', resetEffects);
$('save').addEventListener('click', () => {
  const name = prompt('Nombre del filtro personalizado:')?.trim(); if (!name) return;
  try {
    saveFilter({ version: 1, name, preset: state.preset, effects: state.effects, intensity: state.intensity,
      radius: state.radius, strokes: structuredClone(state.strokes),
      creatures:{speed:state.spiderSpeed,size:state.spiderSize,count:state.creatureCount} }); refreshSaved();
  } catch (err) { alert(errorText(err)); }
});
$('fullscreen').addEventListener('click', async () => {
  try { await stage.requestFullscreen(); }
  catch { status('El navegador no permite la pantalla completa.'); }
});
const panel = $('panel');
const gear = $('settings-open');
const closeSettingsButton = $('settings-close');
const picker = $('filter-picker');
const pickerButton = $('filter-picker-open');
function openFilterPicker() {
  if (!state.settingsOpen || state.filterPickerOpen) return;
  state.filterPickerOpen = true;
  $('filter-search').value = '';
  renderFilterList();
  panel.inert = true;
  picker.inert = false;
  $('app').classList.add('picker-open');
  picker.scrollTop = 0;
  $('filter-picker-back').focus({ preventScroll: true });
}
function closeFilterPicker(restoreFocus = true) {
  if (!state.filterPickerOpen) return;
  state.filterPickerOpen = false;
  $('app').classList.remove('picker-open');
  picker.inert = true;
  panel.inert = false;
  if (restoreFocus) pickerButton.focus({ preventScroll: true });
}
pickerButton.addEventListener('click', openFilterPicker);
$('filter-picker-back').addEventListener('click', () => closeFilterPicker());
$('filter-search').addEventListener('input', e => renderFilterList(e.target.value));
picker.addEventListener('keydown', event => {
  if (event.key !== 'Tab') return;
  const focusable = [...picker.querySelectorAll('button:not(:disabled), input:not(:disabled)')]
    .filter(el => el.getClientRects().length > 0);
  if (!focusable.length) return;
  if (event.shiftKey && document.activeElement === focusable[0]) {
    event.preventDefault(); focusable.at(-1).focus();
  } else if (!event.shiftKey && document.activeElement === focusable.at(-1)) {
    event.preventDefault(); focusable[0].focus();
  }
});
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
  closeFilterPicker(false);
  state.settingsOpen = false;
  $('app').classList.remove('settings-open');
  panel.inert = true;
  gear.setAttribute('aria-expanded', 'false');
  gear.focus({ preventScroll: true });
}
gear.addEventListener('click', openSettings);
closeSettingsButton.addEventListener('click', closeSettings);
$('settings-done').addEventListener('click', closeSettings);
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
document.addEventListener('keydown', e => { if (e.key === 'Escape') {
  if (state.filterPickerOpen) closeFilterPicker();
  else if (state.settingsOpen) closeSettings();
  else if (state.mirror) leaveMirror();
} });
document.addEventListener('fullscreenchange', () => { if (!document.fullscreenElement && state.mirror) leaveMirror(); });
window.addEventListener('pagehide', () => { stopCamera(); state.tracker?.close(); state.tracker = null; state.renderer?.dispose(); state.renderer = null; });
document.addEventListener('visibilitychange', () => {
  if (document.hidden) { cancelAnimationFrame(state.raf); state.raf = 0; }
  else if (state.running && !state.raf) state.raf = requestAnimationFrame(render);
});
refreshFilters(); refreshEffects(); refreshSaved(); refreshButtons(); refreshNumbers();
if (!window.isSecureContext) showError('Para acceder a la webcam abre esta página con HTTPS (GitHub Pages) o localhost.');
