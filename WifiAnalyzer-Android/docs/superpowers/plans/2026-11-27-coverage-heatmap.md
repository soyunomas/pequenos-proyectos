# NetScope 4.1 — Correct local Wi-Fi heatmap extrapolation

> **For agentic workers:** REQUIRED SUB-SKILL: use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Evitar que tres mediciones de buena señal coloreen una vivienda entera sin evidencia.

**Architecture:** Clase pura \`CoverageSurface\` calcula valores locales y devuelve null fuera del radio de observación. \`HeatmapActivity\` conserva el plano fuera del radio, presenta leyenda/dBm, BSSID fijo y controles en dos filas.

**Tech Stack:** Android Java 17, JUnit 4, Gradle 8.9, Android SDK 35.

**Spec:** Feedback del 2026-11-27: «Pongo 3 puntos, y me lo marca todo en verde. No sirve.»

## Global Constraints
- Sin permisos root ni librerías de terceros.
- No extrapolar por todo el plano con tres muestras: zona no medida permanece sin color.
- No combinar muestras de distintos BSSID.
- Las distancias son relativas al dibujo; no se presuponen metros ni atenuación a través de paredes.
- Visibilidad de controles dentro de los insets de Android.

## Review Focus
- Tres puntos fuertes: lejos no hay color; puntos conservan RSSI.
- Plano panorámico: radio escala según proporción.
- BSSID cambia a mitad: rechazar captura y explicar.
- Tres lecturas iguales: advertir que falta contraste; no inventar variabilidad.
- Valores RSSI restringidos por permisos: pedir comprobación de Wi-Fi/ubicación.

### Task 1 — Prueba primero, calcular cobertura local
**Files:** \`CoverageSurfaceTest.java\`, \`CoverageSurface.java\`
- [x] Definir pruebas de área desconocida, valores observados, radio, proporción y gradiente.
- [x] Crear motor local puro separado de Canvas.

### Task 2 — Integración UI
**Files:** \`HeatmapActivity.java\`
- [x] Aplicar estimación local a celdas del plano; no dibujar celdas sin cobertura.
- [x] Fijar BSSID; cooldown entre muestras; indicador de señales idénticas.
- [x] Leyenda con degradado, radio corto/medio/amplio, reinicio y exportar.

### Task 3 — Validación y publicación
**Files:** \`app/build.gradle\`, \`.github/workflows/wifi-analyzer.yml\`, \`README.md\`
- [ ] Ejecutar testDebugUnitTest y assembleDebug en GitHub Actions.
- [ ] Validar ZIP y comprobar NetScope_v4.1.apk publicado.
