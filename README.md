# 📦 Colección de Proyectos y Herramientas

Este repositorio contiene una colección diversa de herramientas, utilidades y simuladores. Desde aplicaciones de sistema en **Go** hasta herramientas web autocontenidas en un solo archivo **HTML/JS** que utilizan tecnologías avanzadas como TensorFlow.js, Mediapipe y Tesseract.

## ⭐ Proyecto Destacado: InputFlow

Una herramienta de sistema avanzada escrita en **Go** para Linux.

### [🌊 InputFlow](./InputFlow/)
**Hardware Event Mapper de Latencia Cero.**
Un demonio diseñado para interceptar eventos de hardware de bajo nivel (Joysticks, Ratones, Teclados) directamente desde el Kernel de Linux y mapearlos a comandos del sistema.
*   **Tecnología:** Go (Golang).
*   **Características:** Cero latencia, soporte hot-plug, scanner de hardware integrado, motor lógico para filtrar ruido analógico.
*   **Uso:** Ideal para crear macros complejas, controlar el volumen con ruedas de ratón secundarias o mapear mandos de juego a acciones del sistema.
*   [Ver Documentación Completa](./InputFlow/README.md)

---

## 🧠 IA y Visión Artificial (Web)

Herramientas experimentales que se ejecutan 100% en el navegador utilizando librerías de Inteligencia Artificial.

*   **[📸 RedactoMatic (CensureText)](censuretext.html)**
    *   **Qué es:** Una herramienta para censurar información sensible en imágenes automáticamente.
    *   **Tecnología:** OCR con *Tesseract.js*.
    *   **Funcionalidad:** Detecta texto en imágenes y permite redactar (tachar) automáticamente basándose en palabras clave o expresiones regulares (Emails, Teléfonos, etc.).
*   **[🎨 Bodypaint AR](bodypaint.html)**
    *   **Qué es:** Realidad aumentada para pintar sobre el cuerpo humano en tiempo real a través de la webcam.
    *   **Tecnología:** *TensorFlow.js* y *Body Segmentation*.
    *   **Funcionalidad:** Segmenta la silueta humana y permite dibujar sobre ella manteniendo el fondo intacto o viceversa.
*   **[🏀 Cesta Rebote (Motion Control)](OTROS/CESTA-PELOTAS.html)**
    *   **Qué es:** Juego interactivo controlado por movimiento.
    *   **Tecnología:** *Mediapipe*.
    *   **Funcionalidad:** Detecta las muñecas del usuario mediante la webcam para controlar una cesta y rebotar pelotas virtuales.

---

## 🌐 Redes y Ciberseguridad (Simuladores)

Visualizadores interactivos para entender protocolos y conceptos de redes. Ideales para estudiantes y docentes.

*   **[📡 Visualizador de Modulaciones Digitales](REDES/modulaciones.html)**
    *   Explora cómo los bits se convierten en ondas. Cubre codificación de línea (NRZ, AMI, Manchester) y modulación (ASK, FSK, PSK, QAM, OFDM).
*   **[🔒 Cifrado y Firma Digital](REDES/Cifrado_Simetrico_Asimetrico_Hibrido_firma.html)**
    *   Simulación paso a paso de cifrado Simétrico, Asimétrico, Híbrido y Firma Digital entre dos actores.
*   **[🤝 Handshake TLS/SSL](REDES/Handshake-TLS-SSL-HTTPS.html)**
    *   Desglose visual del proceso de conexión segura HTTPS, incluyendo intercambio de certificados y claves.
*   **[📦 Encapsulamiento de Datos](REDES/Encapsulamiento.html)**
    *   Visualiza cómo los datos viajan a través de las capas del modelo OSI/TCP-IP.
    *   **Variantes:** [Con Switch L2](REDES/Encapsulamiento_Switch.html) y [Con Router L3](REDES/Encapsulamiento_Router.html).
*   **[🖧 Enrutamiento IP y Ping](REDES/Ping_static_route.html)**
    *   Simula el flujo de un `ping` (ICMP) a través de routers, resolución ARP y tablas de enrutamiento.
    *   **Ver también:** [Visualizador de Enrutamiento Genérico](REDES/Enrutamiento.html).
*   **[⚡ Cabeceras TCP vs UDP](REDES/TCPvsUDP.html)**
    *   Comparativa interactiva de los campos de cabecera y el comportamiento de conexión.
*   **[🔄 Funcionamiento Switch y VLANs](REDES/Switch_aprendizaje.html)**
    *   Simula el aprendizaje de direcciones MAC y la tabla CAM.
    *   **Ver también:** [Visualizador de VLANs (802.1Q)](REDES/Vlans.html).
*   **[🔢 Calculadora IP Avanzada](REDES/Calculadora_IP.html)**
    *   Subnetting, CIDR, wildcard y conversión binaria.

---

## 💾 Bases de Datos y Desarrollo

Herramientas para desarrolladores y administradores de bases de datos.

*   **[🔗 Visualizador de SQL JOINs](BASES%20DE%20DATOS/Explicacion_Visual_JOIN.html)**
    *   Herramienta visual para entender `INNER`, `LEFT`, `RIGHT` JOINs con conjuntos de datos interactivos.
    *   **Versión alternativa:** [Funcionamiento Visual](BASES%20DE%20DATOS/Funcionamiento_JOIN-Visual.html).
*   **[💉 Generador de Payloads SQLi](BASES%20DE%20DATOS/SQL-Inyectado-Payloads.html)**
    *   Generador educativo de payloads para pruebas de inyección SQL (Error-based/Union-based) para varios motores (MySQL, PostgreSQL, MSSQL).
*   **[📄 PresentaFácil Ultra (Markdown Slides)](Presentaciones.html)**
    *   Editor web para crear diapositivas tipo *Reveal.js* escribiendo simplemente Markdown. Soporta exportación a HTML.
*   **[📜 Generador de Índices Markdown](Markdown_a_Indices.html)**
    *   Convierte texto Markdown en HTML con un índice de contenidos (TOC) navegable automáticamente generado.

---

## 🛠️ Productividad y Utilidades Varias

Herramientas web para tareas cotidianas de manipulación de archivos y medios.

*   **[🏷️ Diseñador de Etiquetas Pro](OTROS/Etiquetas.html)**
    *   Diseña e imprime hojas de etiquetas con textos, contadores secuenciales, códigos de barras e imágenes.
*   **[📂 Concatenador de Archivos](OTROS/concatenador.html)**
    *   Arrastra múltiples archivos de texto o código para unificarlos en uno solo (útil para pasar contextos a LLMs).
    *   **Versión alternativa:** [UniTexto](unirtexto.html).
*   **[✂️ Recortador de Imágenes](recortador-imagenes.html)**
    *   Extrae múltiples elementos de una imagen y genera *Sprite Sheets* ordenados.
*   **[🎞️ Convertidor MP4 a GIF](test.html)**
    *   Convierte clips de video a GIF directamente en el navegador usando *FFmpeg.wasm*.
*   **[📍 Mi Ritmo GPS](recorrido.html)**
    *   Graba rutas GPS, guárdalas y compáralas en tiempo real para ver si vas más rápido o lento que tu récord anterior.

---

## 🎮 Juegos Clásicos y Visuales

Implementaciones web de juegos y efectos visuales.

*   **[🕹️ Juegos de Lógica y Mesa:]**
    *   [Mastermind](JUEGOS_CLASICOS/Mastermind.html)
    *   [Damas](JUEGOS_CLASICOS/damas.html)
    *   [Buscaminas (ExploraMinas)](JUEGOS_CLASICOS/exploraminas.html)
    *   [Poker Texas Hold'em](JUEGOS_CLASICOS/poker.html)
    *   [MemoReto (Memoria)](JUEGOS_CLASICOS/memoreto.html)
    *   [TerraTank Duel](JUEGOS_CLASICOS/terratankDuel.html) - Juego de artillería por turnos.
    *   [Interceptor Urbano](JUEGOS_CLASICOS/interceptor_humano.html) - Defensa de misiles.

*   **[🎲 Sorteos y Azar:]**
    *   [Sorteo de Números](OTROS/sorteo.html) - Con animaciones y modo oscuro.
    *   [Sorteo de Nombres (Alumnos)](OTROS/sorteo_por_nombre.html) - Incluye opciones de "Comodín" y "Pase Libre".

*   **[👁️ Efectos Visuales:]**
    *   [Matrix Webcam](OTROS/matrix.html) - Efecto de lluvia de código ASCII sobre la imagen de tu webcam.
    *   [Visualizador de Audio ASCII](OTROS/ascii-micro.html) - Visualizador de espectro de audio en la terminal/navegador.
    *   [Simulador de Error Crítico](OTROS/Error_critico.html) - Interfaz de ciencia ficción de fallo de sistema.
