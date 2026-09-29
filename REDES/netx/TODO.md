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

Estado: pendiente.

- [ ] Modo de available bandwidth inspirado en pathChirp/SLoPS, separado de goodput TCP.
- [ ] Intervalos/confianza y rechazo de estimaciones inestables.
- [ ] QUIC como transporte de primera clase sin reimplementar QUIC.
- [ ] Escenarios request/response, bursty, mensajes pequeños y streaming.
- [ ] Comparación de congestion control cuando el sistema lo permita.
- [ ] Responsiveness under working conditions alineada con IPPM vigente.

**Gate de salida:** cada modo declara exactamente qué magnitud mide; pruebas controladas demuestran que capacity, available bandwidth y TCP goodput no se etiquetan como equivalentes.

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
