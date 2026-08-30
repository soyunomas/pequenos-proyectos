# 📦 Colección de Herramientas y Proyectos Web

Este repositorio aloja una colección de herramientas, simuladores educativos y utilidades. La mayoría son aplicaciones web autocontenidas que puedes **ejecutar directamente desde el navegador**, aunque también incluye proyectos de sistema y hardware.

---

## 📱 Aplicaciones Android

### [🕒 Clementime by Clemente](./Clementime-Android/)
**Widget Android de reloj digital y fecha con estilos visuales personalizables.**
Muestra la hora en formato 12/24 h según la configuración de Android y permite cambiar el aspecto del widget desde un selector integrado.
*   **21 estilos visuales:** transparente, 10 marcos transparentes con geometrías distintas y 10 fondos generados con IA con opacidad ajustable.
*   **Engranaje temporal:** al tocar el reloj aparece durante 1 segundo y después se oculta automáticamente; los toques consecutivos reinician el temporizador.
*   **APK:** [Clementime_by_Clemente_v1.5.apk](./Clementime-Android/Clementime_by_Clemente_v1.5.apk)
*   **Fuente y documentación:** [Clementime-Android](./Clementime-Android/)

### [📁 Flux Files](./FluxFiles-Android/)
**Gestor de archivos Android basado en Material Files 1.7.4.**
Incluye APK instalable, código fuente reproducible y documentación de los cambios realizados.
*   **APK:** [FluxFiles-v1.0.0.apk](./FluxFiles-Android/FluxFiles-v1.0.0.apk)
*   **Fuente y documentación:** [FluxFiles-Android](./FluxFiles-Android/)

### [📅 Horario Lectivo](./HorarioLectivo-Android/)
**Aplicación Android y widget de pantalla de inicio para consultar y editar un horario lectivo semanal.**
Permite configurar turnos de mañana y tarde, duración de sesiones, recreos, asignaturas y la distribución semanal. El widget muestra la clase **AHORA** y la **SIGUIENTE**, y abre la vista completa al tocarlo.
*   **Editor semanal:** asignación rápida por casillas, modo bloque y copia de días completos.
*   **Diseño:** modo claro/oscuro, resaltado de la franja actual y del siguiente tramo, e icono personalizado de calendario y reloj.
*   **Compatibilidad:** Android 8.0 (API 26) o superior.
*   **APK:** [HorarioLectivo_v1.6.apk](./HorarioLectivo-Android/HorarioLectivo_v1.6.apk)
*   **Fuente y documentación:** [HorarioLectivo-Android](./HorarioLectivo-Android/)

---

## 🔌 Proyectos de Hardware y Sistemas

Herramientas que requieren instalación local o hardware específico (no son web).

### [⚡ L2 Network Storm Monitor](./L2-Storm-Monitor/)
**Detector de Tormentas de Red y Bucles (C++ / Arduino).**
Firmware para microcontroladores **RTL8720DN (BW16)** que monitorea la red en modo promiscuo. Detecta y alerta visualmente sobre tormentas de ARP y saturación de tráfico broadcast en tiempo real.
*   **Ideal para:** Administradores de red y diagnóstico físico.
*   **Configurable:** Umbrales ajustables para entornos Hogar, Oficina o Industrial.

### [🌊 InputFlow](./InputFlow/)
**Hardware Event Mapper de Latencia Cero (Go).**
Un demonio para Linux escrito en **Go** que intercepta eventos de hardware (mandos, teclados) y los mapea a acciones del sistema.
*   **Código Fuente:**[Ver carpeta del proyecto](./InputFlow/)
*   **Documentación:** [Leer README](./InputFlow/README.md)

---

## 🧠 IA y Visión Artificial (Web)

Herramientas que se ejecutan en el navegador usando TensorFlow.js, Mediapipe o Tesseract.

*   **[📸 RedactoMatic (CensureText)](https://soyunomas.github.io/pequenos-proyectos/censuretext.html)**
    *   Detecta texto en imágenes automáticamente y permite censurar/tachar información sensible (emails, teléfonos) usando OCR.
*   **[🎨 Bodypaint AR](https://soyunomas.github.io/pequenos-proyectos/bodypaint.html)**
    *   Realidad aumentada mediante webcam que segmenta la silueta humana para pintar sobre el cuerpo o el fondo en tiempo real.
*   **[🏀 Cesta Rebote (Motion Control)](https://soyunomas.github.io/pequenos-proyectos/OTROS/CESTA-PELOTAS.html)**
    *   Juego interactivo donde controlas una cesta con el movimiento de tus manos (detectadas por webcam) para rebotar pelotas.
*   **[🖐 HandTrigger (Gestures to Webhook)](https://soyunomas.github.io/pequenos-proyectos/handtrigger.html)**
    *   Disparador de eventos HTTP controlado por gestos. Detecta la mano derecha abierta (usando MediaPipe) para lanzar peticiones web a servidores locales o remotos (IoT/Domótica). Incluye configuración de *cooldown*, visualización del esqueleto en tiempo real y validación estricta para evitar falsos positivos.

---

## 🌐 Simuladores de Redes y Ciberseguridad

Visualizadores educativos interactivos para entender el flujo de datos y protocolos.

### Fundamentos y Protocolos
*   **[📡 Visualizador de Modulaciones Digitales](https://soyunomas.github.io/pequenos-proyectos/REDES/modulaciones.html)**: Codificación de línea (NRZ, AMI, Manchester) y modulación (ASK, FSK, PSK, QAM, OFDM).
*   **[📦 Encapsulamiento de Datos (OSI)](https://soyunomas.github.io/pequenos-proyectos/REDES/Encapsulamiento.html)**: Viaje de los datos por las capas.
*   **[⚡ Cabeceras TCP vs UDP](https://soyunomas.github.io/pequenos-proyectos/REDES/TCPvsUDP.html)**: Comparativa interactiva de estructuras y flags.
*   **[📖 Visor de Cabeceras IPv4 y RFCs](https://soyunomas.github.io/pequenos-proyectos/REDES/CabeceraIPv4-RFCs.html)**: Explicación campo a campo de la cabecera IP.
*   **[📑 Visor de Cabeceras de Protocolos](https://soyunomas.github.io/pequenos-proyectos/REDES/Cabeceras_Protocolos.html)**: Ethernet, ARP, Wi-Fi, PPP, etc.

### Enrutamiento y Switching
*   **[🖧 Enrutamiento IP y Ping](https://soyunomas.github.io/pequenos-proyectos/REDES/Ping_static_route.html)**: Simulación de ICMP, ARP y tablas de rutas.
*   **[🗺️ Visualizador de Enrutamiento Genérico](https://soyunomas.github.io/pequenos-proyectos/REDES/Enrutamiento.html)**: Lógica de salto entre routers.
*   **[🔄 Funcionamiento Switch (Tabla MAC)](https://soyunomas.github.io/pequenos-proyectos/REDES/Switch_aprendizaje.html)**: Aprendizaje de direcciones y reenvío.
*   **[🏷️ Visualizador de VLANs (802.1Q)](https://soyunomas.github.io/pequenos-proyectos/REDES/Vlans.html)**: Simulación de trunking y etiquetado.
*   **[📦 Encapsulamiento con Switch L2](https://soyunomas.github.io/pequenos-proyectos/REDES/Encapsulamiento_Switch.html)**: Flujo de trama a través de un switch.
*   **[📦 Encapsulamiento con Router L3](https://soyunomas.github.io/pequenos-proyectos/REDES/Encapsulamiento_Router.html)**: Flujo de paquete a través de un router.
*   **[💥 Simulador CSMA/CD (Ethernet)](https://soyunomas.github.io/pequenos-proyectos/REDES/CSMA-CD.html)**: Detección de colisiones y backoff.
*   **[📶 Simulador CSMA/CA (Wi-Fi)](https://soyunomas.github.io/pequenos-proyectos/REDES/CSMA-CA.html)**: Mecanismo RTS/CTS y prevención de colisiones.

### Ciberseguridad y Cifrado
*   **[🔒 Cifrado y Firma Digital](https://soyunomas.github.io/pequenos-proyectos/REDES/Cifrado_Simetrico_Asimetrico_Hibrido_firma.html)**: Simulación de criptografía Simétrica, Asimétrica e Híbrida.
*   **[🤝 Handshake TLS/SSL (HTTPS)](https://soyunomas.github.io/pequenos-proyectos/REDES/Handshake-TLS-SSL-HTTPS.html)**: Paso a paso de la negociación segura.
*   **[🔢 Calculadora IP Avanzada](https://soyunomas.github.io/pequenos-proyectos/REDES/Calculadora_IP.html)**: Subnetting, CIDR, wildcard y binario.

---

## 💾 Bases de Datos y Desarrollo

Herramientas para entender lógica de datos y programación.

*   **[🔗 Visualizador de SQL JOINs](https://soyunomas.github.io/pequenos-proyectos/BASES%20DE%20DATOS/Explicacion_Visual_JOIN.html)**
    *   Diagramas interactivos de `INNER`, `LEFT`, `RIGHT` JOIN.
    *   **Versión alternativa:** [Funcionamiento Visual](https://soyunomas.github.io/pequenos-proyectos/BASES%20DE%20DATOS/Funcionamiento_JOIN-Visual.html).
*   **[💉 Generador de Payloads SQLi](https://soyunomas.github.io/pequenos-proyectos/BASES%20DE%20DATOS/SQL-Inyectado-Payloads.html)**
    *   Generador educativo de inyecciones SQL para pruebas de seguridad (MySQL, PostgreSQL, MSSQL).
*   **[⚖️ Lógica Booleana](https://soyunomas.github.io/pequenos-proyectos/OTROS/Logica_booleana.html)**
    *   Tabla interactiva de leyes lógicas y su equivalencia en SQL y programación.
*   **[📄 PresentaFácil Ultra](https://soyunomas.github.io/pequenos-proyectos/Presentaciones.html)**
    *   Crea presentaciones estilo *Reveal.js* escribiendo Markdown. Exportable a HTML.
*   **[📜 Generador de Índices Markdown](https://soyunomas.github.io/pequenos-proyectos/Markdown_a_Indices.html)**
    *   Convierte texto Markdown en HTML con un índice de contenidos (TOC) automático.

---

## 🛠️ Productividad y Archivos

Utilidades para manipulación de archivos directamente en el navegador.

*   **[✂️ Recortador de Imágenes](https://soyunomas.github.io/pequenos-proyectos/recortador-imagenes.html)**
    *   Extrae múltiples elementos de una imagen y genera *Sprite Sheets* ordenados automáticamente.
*   **[📂 Concatenador de Archivos](https://soyunomas.github.io/pequenos-proyectos/OTROS/concatenador.html)**
    *   Arrastra archivos de código o texto para unificarlos en uno solo (útil para contextos de LLMs).
    *   **Versión alternativa:** [UniTexto](https://soyunomas.github.io/pequenos-proyectos/unirtexto.html).
*   **[🏷️ Diseñador de Etiquetas Pro](https://soyunomas.github.io/pequenos-proyectos/OTROS/Etiquetas.html)**
    *   Generador de hojas de etiquetas imprimibles con códigos de barras y contadores.
*   **[🎞️ Convertidor MP4 a GIF](https://soyunomas.github.io/pequenos-proyectos/test.html)**
    *   Convierte vídeo a GIF usando *FFmpeg.wasm*.
*   **[📍 Mi Ritmo GPS](https://soyunomas.github.io/pequenos-proyectos/recorrido.html)**
    *   Graba rutas GPS y compáralas en tiempo real contra tus propios récords.
*   **[📋 Listado de Repositorios](https://soyunomas.github.io/pequenos-proyectos/listado.html)**
    *   Utilidad simple para listar repositorios públicos de GitHub.

---

## 🎮 Juegos y Efectos Visuales

*   **[⏱️ Chrono Precision (Detén el Reloj)](https://soyunomas.github.io/pequenos-proyectos/Reloj/index.html)**: Juego de precisión y reflejos. Intenta detener el cronómetro en el milisegundo exacto. Soporta control remoto mediante el protocolo MQTT (WebSockets), ideal para jugar usando botones físicos de hardware o IoT.
*   **[👁️ Matrix Webcam](https://soyunomas.github.io/pequenos-proyectos/OTROS/matrix.html)**: Efecto de lluvia de código sobre tu cámara.
*   **[🎤 Visualizador Audio ASCII](https://soyunomas.github.io/pequenos-proyectos/OTROS/ascii-micro.html)**: Espectro de audio del micrófono en caracteres.
*   **[⚠️ Simulador Error Crítico](https://soyunomas.github.io/pequenos-proyectos/OTROS/Error_critico.html)**: Interfaz de fallo de sistema estilo Sci-Fi.
*   **[🎲 Sorteo de Números](https://soyunomas.github.io/pequenos-proyectos/OTROS/sorteo.html)**: Bombo virtual con animaciones.
*   **[🎟️ Sorteo de Nombres](https://soyunomas.github.io/pequenos-proyectos/OTROS/sorteo_por_nombre.html)**: Sorteo de alumnos con opciones de comodín.

### Juegos Clásicos
*   [🧠 Mastermind](https://soyunomas.github.io/pequenos-proyectos/JUEGOS_CLASICOS/Mastermind.html)
*[⚫ Damas](https://soyunomas.github.io/pequenos-proyectos/JUEGOS_CLASICOS/damas.html)
*   [💣 Buscaminas (ExploraMinas)](https://soyunomas.github.io/pequenos-proyectos/JUEGOS_CLASICOS/exploraminas.html)
*   [♠️ Poker Texas Hold'em](https://soyunomas.github.io/pequenos-proyectos/JUEGOS_CLASICOS/poker.html)
*   [🧩 MemoReto (Memoria)](https://soyunomas.github.io/pequenos-proyectos/JUEGOS_CLASICOS/memoreto.html)
*[🚜 TerraTank Duel](https://soyunomas.github.io/pequenos-proyectos/JUEGOS_CLASICOS/terratankDuel.html)
*   [🚀 Interceptor Urbano](https://soyunomas.github.io/pequenos-proyectos/JUEGOS_CLASICOS/interceptor_humano.html)
