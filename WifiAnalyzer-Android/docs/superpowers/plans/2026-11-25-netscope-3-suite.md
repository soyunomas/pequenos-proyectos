# NetScope 3.0 — Suite de 25 herramientas de red

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Convertir NetScope en una caja de herramientas de red con 25 acciones reales de diagnóstico, distinguir claramente cada curva de canal Wi-Fi y publicar una APK comprobada.

**Architecture:** `MainActivity` mejora la visualización de bandas con una leyenda estable por BSSID. `AdvancedToolsActivity` muestra un catálogo organizado y ejecuta acciones a petición del usuario, con resultados y cancelación. Motores separados cubren DNS, conectividad/rutas, servicios y rendimiento; los flujos específicos de descubrimiento e inventario reutilizan `LanScanner`.

**Tech Stack:** Java 17, Android SDK 35, min SDK 26, Gradle 8.9, API nativas, dependencias mínimas.

**Spec:** Petición del 25 de noviembre de 2026; implementar las 25 herramientas enumeradas en el hilo previo.

## Condiciones
- Herramientas de diagnóstico: solo bajo acción expresa del usuario, límites de tiempo/tráfico y controles de cancelación.
- Escaneos locales restringidos a la Wi-Fi privada que usa el dispositivo; no probar terceros involuntariamente.
- Funciones dependientes del sistema (traceroute/MTR ICMP, Path MTU, iPerf3 nativo) deben comprobar capacidad y declarar cuando el dispositivo no lo permite.
- DNS, RDAP, BGP y velocidad usan servidores externos concretos y revelan IP o nombre consultado; informar en interfaz.
- No confundir pruebas TCP con ICMP ni interpretar puertos como servicios confirmados.
- No asumir que un dispositivo soporta root o una herramienta nativa; no fingir salidas ni medidas.

## Tareas
### 1. Gráfica de canales
- [ ] Crear colores/estilos deterministas por BSSID y leyenda legible, seleccionable.
- [ ] Arreglar eje de frecuencia y selección con ficha por AP.
- [ ] Pruebas JVM de identidad de color/orden de leyenda.

### 2. Catálogo y UI de herramientas
- [ ] `AdvancedToolCatalog.java`: 25 títulos, familias, entradas y avisos.
- [ ] `AdvancedToolsActivity.java`: buscador, entradas, ejecución en hilo, cancelación, exportación.
- [ ] Navegación desde pantalla principal, Toolkit y manifest.

### 3. DNS y DNS seguro
- [ ] Consultas A/AAAA/MX/TXT/NS/CNAME/SOA/SRV/CAA/PTR por el transporte elegido.
- [ ] DNS inverso, comparador de resolutores clásicos y opciones DoH/DoT.
- [ ] Validación de nombres y respuestas DNS de tamaño acotado, pruebas JVM.

### 4. Diagnóstico de rutas y servicio
- [ ] Ping simple/continuo, traceroute/MTR cuando el sistema lo permita.
- [ ] IPv6, pruebas de MTU compatibles con Android, disponibilidad y NTP.
- [ ] TLS y HTTP inspectores con tiempos, cadenas, cabeceras y comprobaciones.
- [ ] Pruebas de DNS/Ping/HTTP/SSL según disponibilidad de red.

### 5. Descubrimiento e inventario
- [ ] mDNS/DNS-SD, UPnP/SSDP, SNMP read-only, Wake-on-LAN.
- [ ] Inventario local persistente y comparación básica de observaciones.
- [ ] Uso bajo solicitud, sin buscar credenciales ni contraseñas.

### 6. Servicios públicos y rendimiento
- [ ] RDAP, ASN/BGP RIPE, IP pública/VPN, velocidad sobre servidor explícito y conexión LAN.
- [ ] Integración iPerf3 mediante componente compatible cuando haya soporte, sin emular su protocolo.
- [ ] Exportación de informe consolidado.
- [ ] Registrar limitaciones explícitas.

### 7. Verificación
- [ ] Pruebas unitarias y compilación de debug APK en GitHub Actions.
- [ ] Validación ZIP y publicación `NetScope_v3.0.apk`.
- [ ] Auditoría honesta de qué funciona de forma autónoma y qué requiere permisos, hardware o servidor.
