# TODO — plan de cinco fases

Regla: **cada fase termina con gates reproducibles y un commit/push a GitHub antes de comenzar la siguiente**.

## Fase 1 — Fundamentos, protocolo y baseline TCP

Estado: **COMPLETADA**.

- [x] Módulo Go independiente en `REDES/netx`.
- [x] Especificación técnica y metodología.
- [x] Protocolo de control versionable y limitado en tamaño.
- [x] Control plane y data plane separados.
- [x] `netx server`.
- [x] `netx throughput HOST` con TCP upload single-flow.
- [x] Warm-up separado de la ventana medida.
- [x] Salida humana y JSON.
- [x] Buffers reutilizados por conexión; cero dependencias externas.
- [x] Validación, deadlines, token de sesión y cierre por señales.
- [x] Makefile con `make help`, gates, smoke y cross-build OpenWrt.
- [x] Tests, vet, formato, race detector y smoke end-to-end.
- [x] Matriz OpenWrt compilada.
- [x] Fase 1 publicada en GitHub.

**Gate:** `make check`, `make race`, `make smoke` y `make openwrt`.

## Fase 2 — Series, latencia bajo carga y paralelismo

- [ ] TCP download y bidireccional.
- [ ] UDP con pacing, pérdida, reorder y jitter.
- [ ] Samples periódicos fuera del hot path.
- [ ] RTT idle y concurrente durante carga.
- [ ] p50/p90/p95/p99 y MAD.
- [ ] Single-flow y multi-flow separados.
- [ ] Paralelismo adaptativo hasta convergencia.
- [ ] JSON estable y NDJSON de samples.
- [ ] Tests de exactitud temporal y backpressure.

**Gate:** loopback y namespace Linux/netem reproducibles; single y aggregate nunca se mezclan.

## Fase 3 — Diagnóstico del stack y causa probable

- [ ] Backend Linux `TCP_INFO`.
- [ ] RTT/RTTvar, cwnd, ssthresh, retransmits, delivery/pacing rate, rwnd/sndbuf.
- [ ] CPU y presión del host.
- [ ] ECN/CE cuando sea viable.
- [ ] Diagnóstico determinista con evidencia numérica.

**Gate:** diagnóstico trazable a métricas brutas y overhead medido <1%.

## Fase 4 — Available bandwidth, QUIC y escenarios

- [ ] Modo inspirado en pathChirp/SLoPS, separado de TCP goodput.
- [ ] Intervalos/confianza y rechazo de estimaciones inestables.
- [ ] QUIC mediante implementación mantenida.
- [ ] Request/response, bursty, mensajes pequeños y streaming.
- [ ] Comparación de congestion control.
- [ ] Responsiveness under working conditions según IPPM.

**Gate:** cada modo declara exactamente qué magnitud mide.

## Fase 5 — Fast path, OpenWrt productizable y release

- [ ] Perfilado antes de optimizar.
- [ ] Batching y GSO/GRO con benchmark.
- [ ] Afinidad/NUMA opcionales.
- [ ] Timestamping kernel/hardware opcional.
- [ ] AF_XDP sólo si sockets normales dejan de escalar.
- [ ] Receta/paquete OpenWrt.
- [ ] Benchmarks 1/2.5/10/25/40/100G según hardware disponible.
- [ ] Release reproducible, checksums y compatibilidad documentada.

**Gate:** release versionada con artefactos OpenWrt y benchmarks publicados.
