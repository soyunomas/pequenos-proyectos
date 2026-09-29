# Skill del proyecto: especialista en redes y optimización Go

Criterio técnico para cualquier contribución a `netx`.

## Prioridades

1. Corrección de la magnitud medida: no confundir capacity, available bandwidth, TCP/QUIC goodput ni aggregate multi-flow.
2. Perturbación mínima.
3. Reproducibilidad.
4. Portabilidad OpenWrt: baseline sin cgo ni dependencias Linux-only.
5. Rendimiento demostrado con perfil y benchmark before/after.

## Redes

- Separar control plane y data plane.
- Excluir warm-up de la ventana puntuada.
- Single-flow y aggregate multi-flow siempre separados.
- Medir latencia idle y bajo carga.
- Conservar samples crudos.
- Usar percentiles/dispersión para latencia.
- Distinguir sender-, receiver-, CPU- y network-limited sólo con evidencia.
- One-way delay requiere sincronización suficiente; RTT no.
- ICMP no sustituye automáticamente a un probe de aplicación.
- UDP requiere pacing explícito.

## Optimización Go

- Evitar allocations por paquete/chunk en hot path.
- Reutilizar buffers por worker; no añadir `sync.Pool` sin perfil.
- Contadores locales y agregación fuera del hot path.
- Evitar mutex global por sample/paquete.
- No goroutines por paquete ni por sample.
- Medir `net.Conn`/syscalls antes de batching o raw syscalls.
- `CGO_ENABLED=0` es el baseline OpenWrt.
- `TCP_INFO`, GSO/GRO, timestamping, affinity y AF_XDP quedan aislados detrás de backends/build tags.
- Para 100G+, separar generación, métricas y reporting; afinidad/NUMA sólo después de perf/pprof.
- Race detector en desarrollo, nunca para benchmarking.

## Checklist de optimización

- [ ] Cuello de botella demostrado por perfil.
- [ ] Benchmark reproducible.
- [ ] Before/after.
- [ ] CPU, allocs, memoria y syscalls comparados además del throughput.
- [ ] Exactitud preservada.
- [ ] Matriz OpenWrt sigue compilando.
- [ ] Modo portable sigue sin cgo.
- [ ] Trade-off documentado.
