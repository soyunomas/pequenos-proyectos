# TODO — plan de cinco fases

Regla de proyecto: **cada fase termina con gates reproducibles y un commit directo a `main` antes de comenzar la siguiente**. No se usan ramas para el desarrollo ordinario de `netx`.

## Fase 1 — Fundamentos, protocolo y baseline TCP

Estado: **COMPLETADA**.

- [x] Módulo Go independiente dentro de `REDES/netx`.
- [x] Especificación técnica y metodología de medición.
- [x] Protocolo de control versionable y limitado en tamaño.
- [x] Control plane y data plane separados.
- [x] `netx server`.
- [x] TCP upload single-flow.
- [x] Warm-up separado de la ventana medida.
- [x] Salida humana y JSON.
- [x] Validación, deadlines, token de sesión y cierre por señales.
- [x] `Makefile` con `make help` y matriz OpenWrt.
- [x] Tests, vet, race, smoke y cross-build.

## Fase 2 — Medición útil: series, latencia bajo carga y paralelismo

Estado: **COMPLETADA**.

- [x] TCP upload, download y bidireccional.
- [x] UDP upload con pacing por presupuesto de bits.
- [x] Pérdida, reorder y jitter UDP.
- [x] Samples periódicos mediante contadores atómicos fuera del hot path.
- [x] RTT idle y RTT concurrente durante carga mediante echo de aplicación separado.
- [x] Percentiles p50/p90/p95/p99 y MAD para latencia.
- [x] Single-flow y multi-flow como métricas distintas.
- [x] Paralelismo adaptativo 1,2,4,... hasta convergencia o límite configurable.
- [x] Selección del mejor stage probado; un stage de convergencia más lento no reemplaza al mejor resultado.
- [x] JSON versionado (`schema_version=1`) y NDJSON de samples crudos.
- [x] NDJSON se serializa post-medición para aislar backpressure del hot path.
- [x] Tests de límites temporales, percentiles/MAD, bitrate y propagación de backpressure del writer.
- [x] Smoke E2E en loopback para TCP/UDP/latencia/JSON/NDJSON.
- [x] Harness reproducible Linux namespace + `tc netem`; reporta SKIP explícito cuando faltan capacidades del kernel/contenedor.
- [x] Matriz OpenWrt continúa compilando con `CGO_ENABLED=0`.
- [x] Fase 2 publicada directamente en `main`.

**Gate de salida:** `make phase2-check`. En hosts con `CAP_NET_ADMIN`, `netem-check` ejecuta el escenario namespace; en entornos restringidos el resto del gate pasa y ese subtest queda marcado `SKIP` de forma explícita.

## Fase 3 — Diagnóstico del stack y causa probable

Estado: pendiente.

- [ ] Backend Linux `TCP_INFO` sin contaminar plataformas no Linux.
- [ ] RTT, RTTvar, cwnd, ssthresh, retransmits, delivery/pacing rate y límites rwnd/sndbuf cuando el kernel los exponga.
- [ ] CPU y presión del host con coste de observación acotado.
- [ ] Detección ECN/CE cuando sea viable.
- [ ] Reglas deterministas de diagnóstico: CPU-limited, rwnd-limited, loss/retransmission-limited, queueing-under-load, single-flow-limited.
- [ ] Evidencia numérica junto a cada diagnóstico; nunca una “puntuación de red” opaca.

**Gate de salida:** diagnóstico trazable a métricas brutas y degradación medible <1% frente al hot path sin diagnóstico.

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
