# TODO — plan de cinco fases

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

Estado: pendiente.

- [ ] Perfilado CPU/memoria antes de cada optimización.
- [ ] Linux batching y GSO/GRO donde aporten mejora demostrable.
- [ ] Afinidad de CPU/NUMA opcional para hosts de alto rendimiento.
- [ ] Timestamping de kernel y hardware como backends opcionales.
- [ ] Evaluar AF_XDP sólo si sockets normales dejan de escalar y el benchmark lo prueba.
- [ ] Paquete/receta OpenWrt e instrucciones de despliegue para routers con almacenamiento limitado.
- [ ] Benchmarks 1/2.5/10/25/40/100G según hardware disponible.
- [ ] Release reproducible, checksums y documentación de compatibilidad.

**Gate de salida:** release versionada, artefactos OpenWrt, benchmarks publicados y fast path opcional sin degradar el modo portable.