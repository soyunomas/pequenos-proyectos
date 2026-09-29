# Skill del proyecto: especialista en redes y optimización Go

Criterio técnico obligatorio para cualquier contribución a `netx`.

## Prioridades

1. Corrección de la magnitud medida: no confundir capacity, available bandwidth, TCP/QUIC goodput ni aggregate multi-flow.
2. Perturbación mínima de la ruta y del host.
3. Reproducibilidad de metodología, parámetros y resultados.
4. Portabilidad OpenWrt: baseline sin cgo ni dependencias Linux-only.
5. Rendimiento demostrado con perfil y benchmark before/after.

## Reglas de redes

- Separar control plane y data plane.
- Excluir warm-up de la ventana puntuada.
- Single-flow y aggregate multi-flow siempre se conservan por separado.
- En bidireccional, upload y download no se suman en una cifra de “velocidad”.
- Medir latencia idle y bajo carga con un probe de aplicación independiente.
- Conservar samples crudos y derivar estadística desde ellos.
- Usar percentiles y dispersión, no sólo promedio.
- One-way delay requiere sincronización suficiente; RTT no.
- ICMP no sustituye automáticamente a un probe de aplicación.
- UDP requiere pacing explícito y debe declarar si reporta bytes wire o payload útil.
- Jitter debe tener definición reproducible; Fase 2 usa EWMA de la variación entre spacing enviado y spacing recibido.
- Una medición adaptativa debe conservar todos los stages y seleccionar el mejor probado; no ocultar una regresión.

## Reglas de optimización Go

- Evitar allocations por paquete/chunk en el hot path cuando sea viable.
- Reutilizar buffers por worker; no añadir `sync.Pool` sin perfil.
- Contadores locales/atómicos y agregación fuera del hot path.
- Evitar mutex global por sample/paquete.
- No goroutines por paquete ni por sample.
- Reporting/JSON/NDJSON post-medición: el backpressure de stdout no entra en el benchmark.
- Medir el coste real de `net.Conn`/syscalls antes de batching o raw syscalls.
- `CGO_ENABLED=0` es el baseline OpenWrt.
- `TCP_INFO`, GSO/GRO, timestamping, affinity y AF_XDP quedan aislados tras backends/build tags.
- Para 100G+, separar generación, métricas y reporting; afinidad/NUMA sólo después de perf/pprof.
- Race detector en desarrollo, nunca para benchmarking.

## Checklist de optimización

- [ ] Cuello de botella demostrado por perfil.
- [ ] Benchmark reproducible.
- [ ] Before/after.
- [ ] CPU, allocs, memoria y syscalls comparados además del throughput.
- [ ] Exactitud de métricas preservada.
- [ ] Matriz OpenWrt sigue compilando.
- [ ] Modo portable sigue sin cgo.
- [ ] Backpressure de reporting sigue fuera del hot path.
- [ ] Trade-off documentado.


## Diagnóstico determinista

- Nunca inferir una causa sólo por correlación visual: cada diagnóstico necesita una regla y evidencia numérica.
- Las métricas de emisor (`cwnd`, pacing/delivery rate, `rwnd_limited`, `sndbuf_limited`, bytes retransmitidos) sólo se interpretan en endpoints con rol sender/bidirectional.
- `TCP_INFO` se toma en los límites de la ventana medida, no por paquete ni por sample.
- Un kernel que devuelve un `tcp_info` más corto no es un error: `tcp_info_length` gobierna qué campos pueden interpretarse.
- ECN negociado no equivale a congestión observada; sólo `delivered_ce`/contadores CE aportan evidencia de marcado.
- El diagnóstico puede estar vacío. Ausencia de una regla disparada significa “sin evidencia suficiente para estas reglas”, no “red perfecta”.
- El coste de instrumentación forma parte del gate; no se acepta telemetría que cambie de forma material lo que intenta medir.
