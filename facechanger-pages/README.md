# FaceChanger Pages — JavaScript puro, sin Node.js

Versión independiente para **GitHub Pages**, realizada solo con HTML, CSS, JavaScript (módulos ES), MediaPipe Face Landmarker y WebGL2. No usa React, TypeScript en tiempo de ejecución, npm, bundler ni servidor propio. El vídeo se procesa en el navegador.

## Abrirlo en GitHub Pages

Esta carpeta se llama `facechanger-pages/` y está publicada en la rama `main` del repositorio `soyunomas/pequenos-proyectos`:

1. Abre **Settings → Pages** del repositorio, si necesitas revisar la configuración.
2. En **Build and deployment → Source** selecciona **Deploy from a branch**; usa la rama **main** y la carpeta **/(root)**. Guarda.
3. Cuando GitHub publique el repositorio, abre **https://soyunomas.github.io/pequenos-proyectos/facechanger-pages/**.

**No se ha activado ni cambiado la configuración Pages del repositorio:** hacerlo puede sustituir una publicación preexistente. La URL anterior es la dirección esperada si se usa la ruta normal de GitHub Pages sin dominio personalizado. GitHub Pages solo admite `/(root)` o `/docs` como origen de una rama; no permite seleccionar directamente una carpeta de nombre arbitrario como `facechanger-pages/`. Por eso, este proyecto se publica como subruta del sitio de todo el repositorio.

La primera apertura de la webcam debe hacerse desde un origen seguro: GitHub Pages ofrece HTTPS. No abras `index.html` con `file://` para probar la cámara y los módulos; si quieres servirlo localmente sin Node, por ejemplo con Python **solo para servir HTML** (nunca procesa la cámara): `python -m http.server 8000` dentro de esta carpeta y abre `http://localhost:8000`. Python tampoco es necesario para usarlo en GitHub Pages.

## Cómo funciona

1. Pulsa **Activar cámara** y concede permiso de navegador.
2. Elige un efecto y regula su intensidad. Con la deformación manual activada, toca un rasgo y arrástralo.
3. El shader hace **muestreo inverso de píxeles**: modifica la textura de vídeo original mediante un campo de influencia suave, no dibuja una pegatina ni una malla duplicada sobre el rostro.
4. Los gestos se almacenan en coordenadas relativas al ancho/ángulo facial y ancladas a un landmark; se reproyectan cada vez que MediaPipe localiza el rostro. Se pueden combinar, deshacer y rehacer. **Normal** desactiva el preset pero no borra los gestos; usa **Restablecer** para eliminarlos.
5. **Guardar filtro** almacena JSON en localStorage; **Mis filtros** permite cargar o eliminar configuraciones. **Modo espejo** oculta todo el panel, conserva la inversión horizontal y solicita pantalla completa; si está bloqueada funciona como vista sin editor. Pulsa Esc, doble clic o «Salir del espejo» para regresar.

El cambio de webcam, la denegación de permisos y la pérdida de rostro tienen mensajes específicos. Con el rostro ausente se muestra el vídeo normal. En móviles de poca potencia baja la fluidez: la detección va aproximadamente a 15 FPS como máximo y WebGL2 dibuja cuadros entre detecciones. No se garantiza una tasa de FPS medida.

## Archivos

- `index.html` y `style.css`: interfaz oscura en español, responsive.
- `app.js`: cámara, MediaPipe, eventos de puntero, editor, pantalla completa y estados.
- `engine/geometry.js`: transformación en coordenadas faciales, filtros, inversión de controles.
- `engine/pointer.js`: compensación de `object-fit: cover` y máquina de gestos.
- `engine/renderer.js`: shaders GLSL y textura dinámica WebGL2.
- `engine/storage.js`: JSON local versionado y validado.
- `tests/index.html`: pruebas del motor que también se abren desde Pages.
- `tests/logic.mjs`: tests opcionales de Node **solo para desarrollo**, no forman parte de la instalación ni de la web.

## Dependencias externas y privacidad

MediaPipe Tasks Vision **0.10.35** se sirve desde `cdn.jsdelivr.net`, junto con su WASM, y el modelo Face Landmarker float16 se obtiene de `storage.googleapis.com`. **No hay instalación**, pero el primer acceso requiere Internet y que esos dominios estén permitidos; el navegador puede cachear recursos. No se incluyen los binarios en Git por tamaño y restricciones de las herramientas de publicación. Si la CDN falla se mostrará un error de carga. La ejecución facial y la deformación suceden en el dispositivo: no hay una API de vídeo ni servidor nuestro ni analíticas propias. MediaPipe puede realizar mediciones de uso según su aviso de privacidad; revisa las condiciones del proveedor. La configuración de filtros queda en el navegador.

Código de esta versión: implementación original reutilizando la arquitectura y las fórmulas elaboradas para `facechanger-web/`; no incorpora código del repositorio `pascscha/FaceChanger` (GPL-3.0). MediaPipe: Apache-2.0; consulta también las condiciones de distribución del modelo oficial en https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker/web_js. Licencias y disponibilidad de jsDelivr y Google Cloud Storage según sus proveedores.

## Verificaciones

Se han pasado 13 tests de lógica en Node (uso **únicamente durante el desarrollo**) con mapeo, filtrado, persistencia, cancelación, anclaje y gestos. Se ha comprobado `node --check` sobre los módulos; no hay compilación necesaria para publicar. La prueba con navegador automatizado en este entorno no ha podido abrir páginas por la política del ejecutable Chromium (`ERR_BLOCKED_BY_ADMINISTRATOR`). Quedan por validar en un dispositivo real la inicialización del modelo remoto, webcam física, deformación visual WebGL2 y precisión de pantalla táctil. No se afirma que esas pruebas se hayan superado.

## Versión 1.1: precisión facial y experiencia móvil

La cámara ahora ocupa toda la pantalla tanto en móvil como escritorio. El engranaje superior abre un panel superpuesto, con zona exterior para cerrarlo, botón «Cerrar ajustes», tecla Esc, foco accesible y controles táctiles de al menos 48 px. El canvas mantiene `touch-action: none` y no cambia de tamaño al abrir el panel, de modo que el dedo no pierde alineación con la imagen.

Los presets se revisaron con las conexiones anatómicas oficiales de Face Landmarker:
[face_landmarks_connections.ts](https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/tasks/web/vision/face_landmarker/face_landmarks_connections.ts).
Se emplean punta/base de nariz 1/2, alas nasales 98/327, párpados 33/133/159/145 y 263/362/386/374, comisuras 61/291, barbilla 152 y frente 10. Los centros de los ojos ya no usan iris 468/473, que se desplaza al mirar a los lados. Además, el shader usa una máscara elíptica de soporte compacto (influencia exactamente cero fuera de su radio) para evitar que el filtro de nariz modifique también un ojo. Las máscaras del shader y del sistema de toque utilizan la misma fórmula.

**Validación de esta revisión:** 13/13 pruebas de geometría, gestos, persistencia y contaminación ojo/nariz; comprobación de sintaxis en los módulos; automatización de interacción y dimensiones de interfaz en Chromium emulado a 390×844, 320×640 y 1440×900, sin excepciones JavaScript (la automatización inyectó archivos ya disponibles en vez de navegar por la red, bloqueada en este entorno). La GPU de ese Chromium no dispone de WebGL2; por ello, la nueva apariencia de la deformación visual aún necesita comprobación real en tu móvil y webcam. No se afirma que haya sido comprobada una cámara física ni que todos los rostros tengan proporciones idénticas.
