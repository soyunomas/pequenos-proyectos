# NetScope 4.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Añadir las 20 herramientas no-root 26–45 manteniendo NetScope fiable, usable y comprobable.

**Architecture:** `ToolCatalog` y `ToolEngine` despachan a motores pequeños: `FieldDiscovery`, `FieldDns`, `FieldNat`, `FieldServices`, `FieldProtocols`; sesiones gráficas `FieldSessionActivity`, `HeatmapActivity`, `RttActivity` y `PeerTestActivity`. Se reutilizan `NetworkDiagnostics`, `DnsTools` y `ToolHistory`.

**Tech Stack:** Android Java, minSdk 26, targetSdk 35; sockets TLS/UDP/TCP y APIs públicas Android. Test JUnit y GitHub Actions Gradle 8.9.

**Spec:** `WifiAnalyzer-Android/docs/superpowers/specs/2026-11-26-nonroot-20-tools.md`.

## Global Constraints
- Todas las pruebas son voluntarias; ninguna se ejecuta en segundo plano de forma silenciosa.
- Enlace a Wi-Fi cuando sea posible; no root, no bypass TLS.
- Tope de 8 segundos por prueba UDP/red general, muestras con interrupción y buffers acotados.
- No confundir protocolos anunciados con servicios verificados.
- La interfaz explicará cuando la red aisle clientes o una función no esté soportada.

## Review Focus
- Entrada IP/puerto inválida o pública en pruebas locales: rechazar o informar, sin sondeo indiscriminado.
- Un dispositivo no compatible con RTT: mensaje claro, nunca distancia inventada.
- Dos móviles aislados en Wi-Fi: timeout cancelable, no bloquear interfaz.
- Respuesta binaria malformada DNS/IPP/STUN: comprobar longitudes antes de leer.
- Al detener prueba/cerrar Activity: finalizar el socket/hilo y no sobrescribir UI.

### Task 1 — Catálogo y pruebas de contratos
**Files:** `ToolCatalog.java`, `ToolCatalogTest.java`, `FieldWire.java`, `FieldWireTest.java`
- [ ] Añadir 20 IDs 26–45 únicos con entrada y advertencia.
- [ ] Pruebas de límites y codecs UDP/DNS/IPP/STUN; verificar fallo RED y éxito GREEN.

### Task 2 — Protocolos de descubrimiento y servicios locales
**Files:** `FieldDiscovery.java`, `FieldServices.java`, `FieldNat.java`
- [ ] WS-Discovery y anuncios mDNS/SSDP acotados.
- [ ] STUN Binding, IGD read-only, PCP/NAT-PMP, UDP, IPP, ONVIF, SMB, SSH y WebSocket.
- [ ] Test de parsing/validación.

### Task 3 — DNS/HTTP/Internet
**Files:** `FieldDns.java`, `FieldHttp.java`
- [ ] Split DNS, DNSSEC con validador externo identificado, EDNS/TCP y portal cautivo.
- [ ] Negociación HTTP/2 y prueba de HTTP/3 según capacidad con declaración precisa.

### Task 4 — Actividades de medición
**Files:** `FieldSessionActivity.java`, `PeerTestActivity.java`, `HeatmapActivity.java`, `RttActivity.java`
- [ ] Sesiones de roaming y cambios de red limitadas a dos minutos.
- [ ] Mapa de cobertura con foto elegida, toques y mapa de interpolación RSSI.
- [ ] RTT mediante WifiRttManager con detección y permisos.
- [ ] Pares Android TCP/UDP con control de servidor/cliente, límites y cancelación.

### Task 5 — Integración, UX y privacidad
**Files:** `ToolEngine.java`, `AdvancedToolsActivity.java`, `AndroidManifest.xml`, `README.md`
- [ ] Nuevos IDs en la suite y redirección a sesiones gráficas.
- [ ] UX B+D y mensajes de error, permisos y restricciones.
- [ ] Documentar limitaciones verificadas; evitar afirmar que protocolos simulan otros.

### Task 6 — Pruebas y publicación
**Files:** `app/build.gradle`, `.github/workflows/wifi-analyzer.yml`
- [ ] `gradle --no-daemon testDebugUnitTest assembleDebug`.
- [ ] Validación ZIP del APK, reejecución tras cada fallo y prueba de catálogo.
- [ ] Publicación del APK de nueva versión, enlaces y limitaciones.
