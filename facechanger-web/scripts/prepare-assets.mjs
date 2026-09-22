import { mkdir, copyFile, access } from 'node:fs/promises';
import { createWriteStream } from 'node:fs';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const model = resolve(root, 'public/models/face_landmarker.task');
const url = 'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task';
await mkdir(resolve(root, 'public/models'), { recursive: true });
await mkdir(resolve(root, 'public/wasm'), { recursive: true });
try { await access(model); } catch {
  console.log('Descargando el modelo facial oficial de MediaPipe (solo durante la instalación)…');
  const response = await fetch(url);
  if (!response.ok || !response.body) throw new Error(`Descarga del modelo fallida: HTTP ${response.status}. Descárgalo manualmente en public/models/face_landmarker.task: ${url}`);
  const temp = model + '.part';
  await pipeline(Readable.fromWeb(response.body), createWriteStream(temp));
  const { rename } = await import('node:fs/promises');
  await rename(temp, model);
}
const wasmDir = resolve(root, 'node_modules/@mediapipe/tasks-vision/wasm');
for (const name of ['vision_wasm_internal.wasm', 'vision_wasm_internal.js', 'vision_wasm_nosimd_internal.wasm', 'vision_wasm_nosimd_internal.js']) {
  await copyFile(resolve(wasmDir, name), resolve(root, 'public/wasm', name));
}
console.log('Modelo y binarios WASM disponibles en public/. No se usan CDN durante la ejecución.');
