# TODO — mejoras pendientes e historial

## Próxima sesión: salida legible y feedback de las pruebas

Estado: **PENDIENTE**. Anotado el 1 de octubre de 2026 para retomar en la siguiente sesión; estas funciones todavía no están implementadas.

- [ ] **Resumen legible por defecto.** Sustituir las líneas largas separadas por `|` por bloques cortos de velocidad, latencia y estado. Adaptar la presentación al ancho disponible, con un formato utilizable en terminales de 80 columnas sin necesitar pantalla completa. Mantener separados subida/bajada y baseline de un flujo/resultados de varios flujos.
- [ ] **Detalle técnico opcional.** Mantener la recogida de diagnósticos cuando esté habilitada, pero mostrar por defecto sólo lo relevante para el usuario. Llevar TCP_INFO, CPU, ventanas, pacing y evidencias completas a una opción de detalle explícita, por ejemplo `--verbose`; conservar `--diagnostics=false` para desactivar su recogida.
- [ ] **Selección de idioma.** Permitir elegir español o inglés, por ejemplo con `--lang es|en`, con idioma predeterminado y fallback documentados. Traducir de forma coherente ayuda, etiquetas, estados, errores y explicaciones de diagnóstico en cliente y servidor. Mantener estables las claves, códigos, unidades y esquema de JSON/NDJSON.
- [ ] **Situación actual y posibles problemas.** Ofrecer un resumen en lenguaje sencillo: rendimiento observado, respuesta en reposo/bajo carga y posibles límites respaldados por las reglas existentes. Explicar qué significan los hallazgos y qué comprobar después. Diferenciar límites del equipo de problemas de la ruta; identificar loopback como prueba local, sin atribuir sus resultados al router o a Internet. No presentar hipótesis como causas confirmadas ni ausencia de findings como garantía de una red perfecta.
- [ ] **Feedback visible en el cliente.** Informar de conexión, preparación, calentamiento, medición por stage y finalización. Durante la medición, mostrar una indicación discreta de actividad/progreso, duración y posibilidad de cancelar, sin esperar al resultado final para saber que la prueba arrancó. Mostrar errores y cancelación con claridad. Usar stderr para el feedback, respetar TTY/NO_COLOR y evitar animaciones o ruido en archivos, pipes y automatización.
- [ ] **Actividad y resultados en el servidor.** Mostrar cuándo se conecta un cliente, su dirección, tipo de prueba, sentido y parámetros relevantes. Al terminar, mostrar los resultados disponibles para el servidor, duración y estado de éxito/error/cancelación. Asociar los mensajes a cada sesión para distinguir clientes concurrentes y separar las sondas de control del inicio real de una transferencia; evitar registrar cada paquete o exponer tokens de sesión.
- [ ] **Preservar la medición y OpenWrt.** Mantener colores de contraste conservador, salida ASCII cuando sea necesario y compilación sin cgo. El feedback no debe bloquear workers ni añadir trabajo por paquete: diseñar actualización limitada y verificar su coste. Los resultados JSON/NDJSON siguen publicándose tras la medición y permanecen parseables, sin progreso ni escapes ANSI en stdout.
- [ ] **Validación y documentación.** Comprobar terminal estrecha, fondos claros/oscuros, idiomas, errores, cancelación y sesiones simultáneas; validar pipes, JSON/NDJSON y compatibilidad OpenWrt. Actualizar README/HOWTO con ejemplos de la salida compacta y del modo detallado.

## Historial del desarrollo inicial

Regla de proyecto: **cada fase termina con gates reproducibles y un commit directo a `main` antes de comenzar la siguiente**. No se usan ramas para el desarrollo ordinario de `netx`.

## Fase 1 — Fundamentos, protocolo y baseline TCP

Estado: **COMPLETADA**.

- [x] Módulo Go independiente dentro de `REDES/netx`.
- [x] Especificación técnica y metodología de medición.
- [x] Protocolo de control versionable y control/data plane separados.
- [x] TCP upload single-flow, warm-up separado y JSON.
- [x] Validación, deadlines y token de sesión.
- [x] `Makefile` con `make help` y matriz OpenWrt.
- [x] Tests, vet, race, smoke y cross-build.

## Fase 2 — Medición útil: series, latencia bajo carga y paralelismo

Estado: **COMPLETADA**.

- [x] TCP upload, download y bidireccional.
- [x] UDP paced con pérdida, reorder y jitter.
- [x] Samples periódicos fuera del hot path.
- [x] RTT idle/cargado, p50/p90/p95/p99 y MAD.
- [x] Single-flow y multi-flow separados.
- [x] Paralelismo adaptativo y selección del mejor stage.
- [x] JSON versionado y NDJSON post-medición.
- [x] Smoke E2E y harness namespace + `tc netem`.
- [x] Matriz OpenWrt con `CGO_ENABLED=0`.

## Fase 3 — Diagnóstico del stack y causa probable

Estado: **COMPLETADA**.

- [x] Backend Linux `TCP_INFO` aislado por build tags, sin cgo.
- [x] Linux/386 soportado mediante `socketcall(GETSOCKOPT)`; resto de Linux mediante syscall `getsockopt` directo.
- [x] RTT, RTTvar, min RTT, RTO, cwnd, ssthresh, lost/retrans, reordering y ventanas.
- [x] Pacing rate, delivery rate, bytes acked/sent/retrans y estado app-limited cuando el kernel los expone.
- [x] Busy time, rwnd-limited y sndbuf-limited con deltas sobre la ventana medida.
- [x] Algoritmo de congestion control por stream.
- [x] Detección ECN negociado y `delivered_ce` cuando está disponible.
- [x] CPU de proceso, CPU global, RSS y PSI CPU/memoria del host en Linux.
- [x] Roles sender/receiver/bidirectional para no aplicar métricas de emisor al extremo incorrecto.
- [x] Diagnóstico determinista con evidencia: queueing, single-flow, retransmission/loss, receiver-window, sender-buffer, CPU y ECN/CE.
- [x] Umbrales documentados en `docs/DIAGNOSTICS.md`.
- [x] `--diagnostics=false` para A/B y plataformas donde se quiera omitir instrumentación.
- [x] JSON schema `2`, protocol `3`, conservando telemetría bruta además del diagnóstico.
- [x] Test TCP real que verifica `TCP_INFO`, congestion control y contadores crecientes.
- [x] Coste directo de snapshot bajo presupuesto: límite 500 µs; medición del host de validación 3,965 µs/snapshot en el gate final.
- [x] Gate determinista `make diagnostics-overhead`: coste directo de los dos snapshots por stream <1% de la ventana por defecto.
- [x] A/B loopback disponible como `make diagnostics-ab`, informativo y fuera del gate por ruido de scheduler/CPU.
- [x] Race, smoke y matriz OpenWrt vuelven a formar parte del gate.
- [x] Fase 3 publicada directamente en `main`.

**Gate de salida:** `make phase3-check`. El subtest `netem-check` ejecuta namespaces cuando el host tiene `CAP_NET_ADMIN`; en contenedores restringidos informa `SKIP` explícitamente.

## Fase 4 — Capacidad, available bandwidth, QUIC y escenarios

Estado: **COMPLETADA**.

- [x] Modo `available_bandwidth_estimate` con chirps UDP de tasa creciente, inspirado en pathChirp/SLoPS y separado de goodput.
- [x] Intervalo de confianza aproximado del 95%, censura y rechazo explícito de estimaciones inestables.
- [x] QUIC upload/download/bidir mediante `quic-go v0.54.1`, sin reimplementar QUIC.
- [x] Baseline single-stream y multi-stream separados también en QUIC.
- [x] Escenarios request/response, small-message, bursty y streaming.
- [x] `TCP_CONGESTION` seleccionable y `cc-compare` que conserva algoritmos no soportados como evidencia, sin ranking.
- [x] Responsiveness bajo carga con goodput + idle RTT + working RTT + RPM aproximado.
- [x] El modo responsiveness declara `draft_conformant=false`: alineado con los indicadores del draft IPPM -09, sin fingir implementar sus probes HTTP/trimmed means.
- [x] `measurement_kind` diferencia available bandwidth, transport goodput, responsiveness y escenarios.
- [x] Tests metodológicos prueban estimación estable, rechazo inestable y separación de magnitudes.
- [x] Smoke E2E cubre available, QUIC, scenarios, responsiveness y CC.
- [x] Matriz OpenWrt continúa con `CGO_ENABLED=0`.
- [x] Fase 4 publicada directamente en `main`.

**Gate de salida:** `make phase4-check`. Ningún resultado de Fase 4 se etiqueta como capacidad del path; el estimador se abstiene cuando la señal no es estable.

## Fase 5 — Fast path, OpenWrt productizable y release

Estado: **COMPLETADA**.

- [x] Perfilado CPU/memoria reproducible para el baseline portable y el candidato UDP batch mediante pprof.
- [x] Candidato Linux batching benchmarkeado; GSO/GRO se prueban como capabilities y no se activan en el hot path sin beneficio demostrado y semántica temporal preservada.
- [x] Afinidad CPU/NUMA opcional mediante `--cpu` / `--numa-node`, desactivada por defecto.
- [x] Timestamping UDP userspace/kernel/hardware como backends opcionales; hardware conserva fallback explícito y no afirma soporte del NIC sin observarlo.
- [x] AF_XDP evaluado y deliberadamente no habilitado: falta evidencia de que sockets normales sean el cuello de botella en la matriz disponible.
- [x] Receta OpenWrt procd/UCI y staging de paquete desde los binarios estáticos; servicio deshabilitado por defecto.
- [x] Matriz 1/2.5/10/25/40/100G publicada con protocolo de benchmark; plataformas sin hardware real quedan como `not measured`, sin extrapolaciones.
- [x] Release reproducible de diez arquitecturas, `SHA256SUMS`, `BUILDINFO`, gate byte-identical y documentación de compatibilidad.
- [x] Workflow de release preparado para tags `netx-v*`.
- [x] Fase 5 publicada directamente en `main`.

**Gate de salida:** `make phase5-check`. La release se construye dos veces y debe resultar byte-identical; el fast path no sustituye al modo portable por defecto.
