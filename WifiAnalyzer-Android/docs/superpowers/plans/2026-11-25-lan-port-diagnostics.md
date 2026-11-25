# NetScope 2.6 — Diagnóstico LAN avanzado

> For agentic workers: REQUIRED SUB-SKILL: use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fichas individuales con escaneo TCP, respuestas de servicio, iconografía moderna y barras inferiores sin solapamiento.

**Architecture:** `LanActivity` pasa la IP a `DeviceDetailActivity`; `PortScanner` prueba los puertos sobre la red Wi-Fi; `PortCatalog` etiqueta servicios tentativos; `ResponseInspector` inspecciona bytes acotados. `DeviceIconView` pinta iconos propios.

**Tech Stack:** Android Java, API 26–35, SDK 35, JDK17, Gradle 8.9. Sin librerías externas.

**Spec:** Petición del usuario del 25 de noviembre de 2026.

## Global Constraints
- Solo IPv4 privada y dentro de la subred Wi-Fi actual.
- Consentimiento antes de escanear; sin autenticaciones ni explotación.
- Como máximo 80 puertos, 16 workers, 18 s.
- Máximo 512 caracteres enviados manualmente, 2048 bytes recibidos.
- No afirmar un fabricante/servicio sin evidencia.

## Review Focus
- IP manipulada fuera de la subred: rechazo.
- Host que desaparece o puerto filtrado: timeout sin congelación de UI.
- Cancelación: preservar resultados parciales.
- HTTPS: navegador externo, sin petición HTTP sin cifrar al puerto TLS.
- Barra de navegación del sistema: controles inferiores completamente visibles.

### Task 1 — Catálogo y pruebas
**Files:** `PortCatalog.java`, `PortCatalogTest.java`
- [x] Catalogar puertos, validar 1..65535 y URL HTTP/HTTPS.
- [x] Pruebas de perfiles y objetivos privados.

### Task 2 — Motor de red y pruebas
**Files:** `PortScanner.java`, `ResponseInspector.java`, `ResponseInspectorTest.java`
- [x] Sondeos TCP acotados, notificaciones incrementales, cancelación.
- [x] Lectura de banner / HEAD / cadena manual en límites de bytes.
- [x] Prueba de escapado y rechazo de objetivo inválido.

### Task 3 — Pantallas, iconos e insets
**Files:** `DeviceDetailActivity.java`, `LanActivity.java`, `DeviceIconView.java`, `MainActivity.java`, `AndroidManifest.xml`
- [x] Detalles de host, confirmación de escaneo, acciones por puerto.
- [x] Iconos router, PC, impresora, móvil, TV, servidor y domótica.
- [x] Aplicar insets reales de navegación / gestos a las barras inferiores.

### Task 4 — Entrega
**Files:** `app/build.gradle`, `.github/workflows/wifi-analyzer.yml`, `README.md`
- [ ] Ejecutar `testDebugUnitTest assembleDebug` y validar ZIP.
- [ ] Publicar APK 2.6 y registrar el resultado de GitHub Actions.
