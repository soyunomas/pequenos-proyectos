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

La cámara ocupa toda la pantalla en móvil y escritorio. Actualmente, el engranaje abre una **pantalla completa de ajustes**, con botón de regreso al espejo y controles grandes. El canvas mantiene `touch-action: none` y no cambia de tamaño al abrir el panel, de modo que el dedo no pierde alineación con la imagen.

Los presets se revisaron con las conexiones anatómicas oficiales de Face Landmarker:
[face_landmarks_connections.ts](https://github.com/google-ai-edge/mediapipe/blob/master/mediapipe/tasks/web/vision/face_landmarker/face_landmarks_connections.ts).
Se emplean punta/base de nariz 1/2, alas nasales 98/327, párpados 33/133/159/145 y 263/362/386/374, comisuras 61/291, barbilla 152 y frente 10. Los centros de los ojos ya no usan iris 468/473, que se desplaza al mirar a los lados. Además, el shader usa una máscara elíptica de soporte compacto (influencia exactamente cero fuera de su radio) para evitar que el filtro de nariz modifique también un ojo. Las máscaras del shader y del sistema de toque utilizan la misma fórmula.

**Validación de esta revisión:** 13/13 pruebas de geometría, gestos, persistencia y contaminación ojo/nariz; comprobación de sintaxis en los módulos; automatización de interacción y dimensiones de interfaz en Chromium emulado a 390×844, 320×640 y 1440×900, sin excepciones JavaScript (la automatización inyectó archivos ya disponibles en vez de navegar por la red, bloqueada en este entorno). La GPU de ese Chromium no dispone de WebGL2; por ello, la nueva apariencia de la deformación visual aún necesita comprobación real en tu móvil y webcam. No se afirma que haya sido comprobada una cámara física ni que todos los rostros tengan proporciones idénticas.

## Versión 1.2 — Ojos y boca

Se ha aumentado la intensidad visual de «Ojos grandes» con controles elípticos más amplios y un factor de escala máximo mayor, manteniendo la máscara fuera de la nariz. El nuevo preset «Boca grande» agranda los labios respecto a los puntos MediaPipe 0, 17, 13 y 14, y separa suavemente las comisuras 61 y 291. El regulador global de intensidad controla ambos. Los filtros JSON siguen siendo compatibles; los guardados nuevos pueden utilizar `mouth-big`.

Las pruebas geométricas con una cara sintética verifican la ampliación ocular en los bordes, la ausencia de influencia sobre la nariz y la persistencia del preset de boca. Los resultados visuales definitivos requieren probar una webcam real; no existe una garantía universal del aspecto sobre todas las caras.

## Versión 1.3 — todos los filtros, selector táctil y corrección de ojo caído

El listado pasa a **32 opciones** (Normal y 31 efectos), organizadas en categorías dentro de un `select` nativo de tamaño cómodo en móvil. Se conservan los efectos antiguos y se incorporan: ojos pequeños/separados/juntos/de alienígena; cejas levantadas/enfadadas; nariz pequeña/de Pinocho/de cerdito/fina; boca pequeña/sonrisa gigante/boca triste/labios grandes/boca de pez; cara redonda/estrecha/de huevo/cuadrada/de alienígena; barbilla gigante, frente gigante y mejillas de hámster. **Ojo caído** usa desplazamiento acotado y muestreo inverso del campo anclado en la imagen de origen para impedir la apariencia del ojo original duplicado. Los trazos manuales demasiado largos aumentan automáticamente su área y limitan el desplazamiento en vez de crear un recorte disjunto.

La pantalla de ajustes usa controles nativos y tipografía de lectura móvil. El zoom interno de la cámara fue retirado en la versión 1.4; no hace falta ampliar el navegador para manejar los controles.

**Combinar efectos** permite añadir hasta cuatro filtros adicionales con intensidades independientes; la intensidad general afecta a todo el conjunto, incluido el filtro principal y los gestos. Los formatos JSON antiguos siguen cargándose sin cambios y los nuevos pueden almacenar opcionalmente `effects: [{preset, intensity}]` (versión 1). La GPU procesa un máximo de 32 controles espaciales en el mismo cuadro.

**Pruebas de lógica de esta edición:** 22 comprobaciones pasadas, incluidos todos los filtros, la inversión en origen y destino del ojo desplazado, las mezclas y la persistencia. Requiere todavía comprobar personalmente la estética y velocidad reales en una webcam, especialmente con combinaciones grandes; no se garantiza ausencia de artefactos con deformaciones extremas ni 30 FPS en móviles de gama baja.

### Validaciones pendientes sobre un móvil físico

Las pruebas de lógica y la inspección de la interfaz no sustituyen la prueba de una webcam real. Hay que verificar el rendimiento a 720p, deformaciones combinadas intensas, ojo caído con un rostro real de frente y perfil, permiso de cámara y zoom táctil en Chrome y Safari móviles. El selector y los controles no requieren descargar, instalar ni compilar nada.

## Versión 1.4 — ajustes como pantalla completa de móvil

La interfaz ya no abre un panel lateral. Al tocar el engranaje se presenta **una pantalla completa, a todo el ancho y alto del teléfono**, independiente de la vista de cámara. La cabecera fija «← Espejo · Ajustes» y el botón «Ver mi reflejo» devuelven la cámara en un solo toque. La sección inicial muestra el filtro en un selector nativo de tipografía grande (19 px) y el control de intensidad. Debajo está el ajuste de arrastre; los filtros combinados y el almacenamiento/cámara/modo espejo están en secciones desplegables para que no estorben. La pantalla tiene desplazamiento vertical propio cuando hace falta. Ya no hay un control de zoom de la imagen: se ha eliminado para evitar una interfaz que obligue a ampliar y restablecer. No se ha deshabilitado el zoom de accesibilidad del navegador.

El preset seleccionado **al abrir por primera vez** es «Ojos grandes» (100 %). «Restablecer» sigue permitiendo quitarlo y recuperar la imagen sin filtros; las configuraciones guardadas pueden aplicar cualquier otro preset.

**Verificación:** suite de lógica, sintaxis y Playwright en GitHub Actions, incluido el menú completo, legibilidad del selector, desplazamiento a opciones avanzadas y vuelta al espejo a 320 × 640, 390 × 844 y 1440 × 900. La comprobación del aspecto facial con webcam física requiere un dispositivo real.

## Versión 1.5 — navegación estilo aplicación Android

La opción anterior de desplegable nativo se sustituyó por una **tarjeta de filtro principal de gran tamaño**. Al tocarla se abre una segunda pantalla completa con los 32 filtros separados en categorías, filas de al menos 75 px y nombres de 22–23 px. El buscador acepta nombres sin escribir tildes (por ejemplo, `hamster`). Al elegir un filtro se vuelve a Ajustes, donde se modifica su intensidad; el botón «Ver mi reflejo» devuelve la webcam. El botón «Volver» y Escape respetan la jerarquía de navegación: catálogo → ajustes → espejo. La pantalla de ajustes lleva título de 27–36 px, botones de gran tamaño y texto principal de 21–23 px. Las opciones avanzadas continúan plegadas para evitar saturación visual. No se oculta ni impide el zoom de accesibilidad del navegador; tampoco existe un control propio de zoom.

La configuración inicial sigue siendo «Ojos grandes» al 100 %. Se conserva el formato de filtros guardados, la deformación de píxeles y el funcionamiento sin npm ni compilación en GitHub Pages. Se han ampliado las comprobaciones de Playwright con mediciones del tamaño real del texto, ocupación total del viewport, navegación, búsqueda sin tildes, botones táctiles y cambio de filtro en 320×640, 390×844 y 1440×900. La experiencia exacta en tu teléfono y la webcam real requieren una comprobación física.

## Tema Material 3 (versión web)

La pantalla de ajustes y el catálogo de filtros adoptan el lenguaje visual **Material Design 3**: esquema tonal violeta, superficies `surface` y `surface-container`, texto `on-surface` de alto contraste, tarjetas de radio amplio, botones rellenos/tonales, barras superiores, interruptor Android y filas de selección grandes. El modo claro y oscuro sigue las preferencias del sistema. Los títulos se presentan a 27–38 px, los nombres de filtro a 29–36 px y cada fila de opción tiene un objetivo táctil mínimo de 75 px. La cámara se mantiene a pantalla completa; el engranaje lleva a otra pantalla, sin alterar el canvas.

Es **una aplicación web con estética Material 3**, no una aplicación nativa de Android ni el paquete Jetpack Compose. El tema está implementado en CSS/HTML propios, sin fuente remota ni nuevas dependencias. Referencia de diseño: https://developer.android.com/develop/ui/compose/designsystems/material3 y https://developer.android.com/design/ui/mobile/guides/styles/color. La capa de detección y deformación del rostro no cambia en esta revisión.

Verificación de esta edición: GitHub Actions ejecuta el motor y pruebas de interfaz en Chromium con móvil pequeño (320×640), móvil Android (390×844), escritorio (1440×900) y móvil con tema oscuro. Comprueba las medidas computadas de superficie, tipografía, radios, áreas táctiles, navegación y búsqueda. La apariencia exacta en un teléfono físico sigue siendo una comprobación manual.

## Corrección de anchura móvil (v1.7)

Se eliminó el contenedor centrado `max-width: 620px` y el patrón de tarjeta dentro de tarjeta. La pantalla de ajustes, su **fila Filtro principal**, los apartados y las opciones del catálogo ocupan todo el ancho CSS del móvil (de x=0 a x=ancho del viewport). Las zonas interactivas siguen teniendo unos 18 px de *padding interior* para que el texto no choque con el borde; ese padding ya no estrecha el fondo de las filas. Se quitó «Dale otra cara a tu reflejo» porque ocupaba el espacio que debe corresponder a los controles. La cabecera fija muestra la flecha de regreso y «Ajustes», sin texto adicional que pueda solaparse en pantallas de 320 píxeles. El botón inferior «Ver mi reflejo» ocupa asimismo todo el ancho.

El CSS se ha **reescrito en una sola hoja coherente**, en vez de acumular modificaciones al final sobre reglas contradictorias. Los tokens Material 3 (superficie, primario, contenedor, texto) se mantienen en modos claro/oscuro. Esta es una web con apariencia de pantalla de ajustes Android; la barra del navegador y del sistema no pueden ser eliminadas desde GitHub Pages.

La prueba de interfaz ahora mide explícitamente `getBoundingClientRect()` para exigir que la pantalla de ajustes, fila principal, catálogo y botón inferior abarquen toda la anchura de 320, 390 y 1440 píxeles, que no haya desbordamiento horizontal y que la fila no tenga radio de tarjeta. Las capturas completas de Chromium se adjuntan al workflow de GitHub Actions para inspección visual. Las comprobaciones con webcam física siguen siendo manuales.

Se han inspeccionado capturas reales del navegador Chromium en 320×640 y 390×844. La primera prueba visual reveló solapamiento entre «Espejo» y «Ajustes» en la cabecera pequeña; se eliminó ese texto visible, conservando su nombre accesible, y se añadió una aserción automatizada contra el solapamiento en ambas cabeceras.

## Versión 2.0 — tipografía extragrande para móvil

Se ha sustituido el engranaje por un menú de tres líneas («Abrir menú» para lectores de pantalla). Los textos del menú pasan a una escala deliberadamente extragrande: filtro seleccionado 45–48 px, títulos de sección 40 px, reguladores 37–39 px, filas del catálogo 39 px, ayudas 30 px y botón inferior 36 px. Las cifras muestran su valor en una línea independiente para que no compitan por el ancho con las etiquetas. Todas las secciones se pueden recorrer mediante desplazamiento vertical dentro de la pantalla completa; se mantienen todas las opciones, incluidos filtros combinados y ajustes avanzados. El título «Elige un filtro» usa 36 px para no solaparse con la flecha de regreso en móviles de 320 px.

La pantalla de cámara también tiene un botón de menú de 70 × 70 px con icono SVG de tres trazos. `style.css?v=2-0-large-type` evita que el navegador reutilice una hoja de estilos anterior del sitio. No se limita el zoom de accesibilidad del navegador. La suite de Playwright comprueba fuente computada, ancho completo, ausencia de scroll horizontal, accesibilidad del último filtro mediante scroll y el icono de tres líneas en 320×640, 390×844, escritorio y tema oscuro. La revisión visual en un teléfono físico requiere tu comprobación; las capturas automatizadas se adjuntan en GitHub Actions.

### Efectos nuevos

- Inicio con **Ojos grandes al 75 %**. «Restablecer», junto al menú del espejo, limpia la mezcla y los trazos y vuelve a Normal.
- **Extrañeza sutil** combina el ojo caído y la boca caída; al seleccionarlo comienza al 40 %, ajustable con el deslizador principal. La variante asimétrica combina mirada y media sonrisa.
- **Araña en la mejilla**: recorte fotorealista WebP con transparencia generado para esta aplicación (no SVG), superpuesto sobre el espejo con seguimiento de landmarks, desplazamiento animado y sombra de contacto. Es un movimiento de la imagen completa, no una animación articulada de cada pata. Se oculta cuando desaparece la cara y puede combinarse con deformaciones desde «Combinar efectos».
