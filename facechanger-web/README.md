# FaceChanger Web

Espejo digital con deformación real de píxeles: webcam → MediaPipe Face Landmarker → campo de deformación inverso GLSL/WebGL2 → canvas. Código TypeScript original, inspirado en la experiencia de [pascscha/FaceChanger](https://github.com/pascscha/FaceChanger), sin copiar su código GPL-3.0.

## Windows 10/11 · Chrome o Edge

Requisitos: Node.js 22 o superior, npm, webcam y WebGL2 con aceleración gráfica.

```powershell
git clone --branch feat/facechanger-web https://github.com/soyunomas/pequenos-proyectos.git
cd pequenos-proyectos\facechanger-web
npm install
npm run dev
```

Abre la dirección local que imprime Vite (normalmente `http://localhost:5173`). Acepta el permiso de cámara. `npm install` descarga una sola vez el modelo oficial en `public/models/` y copia los WASM desde el paquete npm a `public/wasm/`: después la ejecución del espejo no depende de un CDN. Los archivos binarios descargados no se versionan en Git. Si falla el acceso al modelo durante la instalación, sitúa manualmente `face_landmarker.task` en `public/models/` desde la URL de `scripts/prepare-assets.mjs` y vuelve a ejecutar el script después de instalar las dependencias. La primera instalación npm requiere internet.

Para usar otra máquina de la red, despliega sobre **HTTPS** (salvo `localhost`, `getUserMedia` requiere contexto seguro). No se transmiten vídeo ni landmarks; no hay servidor biométrico ni analíticas. El navegador sigue mostrando sus controles de privacidad.

## Uso

Selecciona un filtro y regula la intensidad. Activa «Deformación manual» y arrastra la nariz, un ojo, una mejilla o una comisura con ratón, dedo o lápiz. Arrastrar modifica los píxeles durante el gesto. Al soltar, se almacena el desplazamiento en unidades relativas al ancho del rostro con un landmark de referencia; se recalcula en cada detección, incluso si se traslada o gira moderadamente la cabeza. El radio controla la caída gaussiana y afecta a nuevos trazos. Se pueden acumular 48 controles; al superar ese límite se descartan los más antiguos para proteger rendimiento. «Normal» desactiva solamente el preset: los trazos manuales siguen presentes hasta «Restablecer». «Deshacer» y «Rehacer» afectan a los trazos manuales.

«Guardar filtro» añade la configuración JSON versionada a `localStorage` en este navegador; desde «Mis filtros» puedes cargarla o eliminarla. No se exportan imágenes. «Modo espejo» solicita pantalla completa y oculta el editor; el botón para salir aparece brevemente al mover el puntero, y también sirven Esc o doble clic. Si falla la Fullscreen API, sigue en modo espejo sin fullscreen. La imagen siempre se muestra horizontalmente invertida, también en el editor, para mantener alineado el tacto.

## Motor

- `FaceTracker`: modelo Face Landmarker en modo VIDEO, una cara, detección aproximadamente a 18 Hz y suavizado de posiciones (la detección sincronizada puede bloquear brevemente el hilo UI).
- `CoordinateMapper`: deshace el recorte de `object-fit: cover`, tamaño CSS e inversión del espejo. Coordenadas de contacto y landmarks comparten el mismo espacio top-left normalizado.
- `TouchDeformer`: máquina de estados de `pointerdown/move/up/cancel` con captura del puntero; anclaje al landmark más cercano y desplazamiento almacenado en la base rotada de la cara, normalizada por anchura.
- `FaceWarpEngine`: mezcla trazos y ocho filtros predefinidos. En cada fotograma proyecta controles relativos a los nuevos landmarks; no hornea la transformación en píxeles de una imagen anterior.
- `WebGLRenderer` + GLSL: subida de frame por textura; un quad de pantalla completa y muestreo inverso por píxel con influencia gaussiana decreciente. No dibuja mallas desplazadas encima de caras originales ni introduce huecos geométricos. La presentación puede continuar entre inferencias reutilizando los landmarks anteriores.

## Calidad y diagnóstico

```powershell
npm run test
npm run build
npx playwright install chromium
npm run test:e2e
```

Las pruebas unitarias cubren `cover`, inversión, bases faciales, gestos y JSON. Playwright comprueba el editor sin exigir una webcam física; para probar el permiso se necesita un dispositivo real o Chrome con vídeo simulado. **Estado de la entrega:** se pasó una comprobación TypeScript independiente del motor y la persistencia; en el entorno de creación no se pudo descargar npm por fallo DNS, por lo que la compilación completa, Vitest y Playwright están pendientes de ejecución tras `npm install`. No se ha medido un FPS garantizado: prueba sobre el PC objetivo a 720p.

**Pruebas manuales pendientes en hardware real:** latencia y precisión al tocar nariz y comisura en pantalla táctil Windows, webcam física a 720p, rotación de cabeza amplia, varias cámaras y pantalla HDMI, pérdida y reconexión física, calidad al acumular desplazamientos extremos. Solo se sigue la primera cara. No hay ventana secundaria HDMI en este MVP (se puede usar la ventana principal sobre ese monitor). El anclaje a landmarks y la deformación inversa mantienen coherencia en movimientos moderados; expresiones fuertes, oclusiones, perfiles pronunciados y superposición de muchos trazos pueden producir artefactos. Una ausencia de rostro muestra el vídeo normal, sin aplicar trazos hasta recuperarlo.

## Dependencias y derechos

- [MediaPipe](https://github.com/google-ai-edge/mediapipe), paquete `@mediapipe/tasks-vision`: Apache-2.0. Modelo Face Landmarker oficial distribuido por Google; consulta la documentación y condiciones del artefacto en el [catálogo MediaPipe](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker) y conserva sus atribuciones. Se descarga desde Google Cloud Storage con el script local. No se incluye una redistribución modificada del modelo.
- React, Vite, TypeScript, Vitest y Playwright: licencias de sus respectivos paquetes; consulta `node_modules/<paquete>/LICENSE` tras instalar.
- `pascscha/FaceChanger`: GPL-3.0. Solo referencia funcional; el algoritmo WebGL y todo el código web de este directorio son implementación independiente. No se incorpora ningún archivo de ese proyecto.
