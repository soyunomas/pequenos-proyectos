# netx

`netx` es una herramienta de medición de red escrita en Go, inspirada en iperf3 pero diseñada para separar **goodput**, **capacidad**, **ancho de banda disponible** y **responsividad bajo carga** en lugar de reducir la red a una única cifra de “velocidad”.

El proyecto está orientado desde el principio a Linux y OpenWrt: binario autocontenido, `CGO_ENABLED=0`, sin dependencias externas en el hot path y compilación cruzada desde el `Makefile`.

> Estado: **Fase 3 terminada**. A la medición de Fase 2 se añaden `TCP_INFO` en Linux, métricas de CPU/RSS/PSI y diagnóstico determinista con evidencia numérica. El baseline OpenWrt sigue siendo `CGO_ENABLED=0`.

## Inicio rápido

```sh
make help
make phase3-check
make build
```

Servidor:

```sh
./bin/netx server
```

TCP:

```sh
# Baseline single-flow
./bin/netx throughput 192.0.2.10

# Se conserva el baseline de 1 flujo y se compara con 4 flujos
./bin/netx throughput --streams 4 192.0.2.10

# 1 -> 2 -> 4 -> 8 hasta que la ganancia marginal converja
./bin/netx throughput --adaptive --max-streams 8 --convergence 5 192.0.2.10

# Download y bidireccional
./bin/netx throughput --direction download 192.0.2.10
./bin/netx throughput --direction bidir 192.0.2.10
```

UDP:

```sh
./bin/netx udp --rate 100M --packet-size 1200 192.0.2.10
```

Latencia de aplicación:

```sh
./bin/netx latency 192.0.2.10
```

Salida máquina-legible:

```sh
./bin/netx throughput --json 192.0.2.10
./bin/netx throughput --ndjson 192.0.2.10
./bin/netx udp --ndjson --rate 50M 192.0.2.10
```

## Qué mide la Fase 2

### TCP

Cada suite conserva explícitamente dos niveles:

- `single_stream`: resultado con un flujo TCP;
- `aggregate`: mejor stage probado con N flujos.

No se sustituyen entre sí. Si `--streams 4` se usa, primero se ejecuta un stage de 1 flujo y después uno de 4. Con `--adaptive`, se prueban 1, 2, 4, ... hasta el límite o hasta que la ganancia marginal cae por debajo de `--convergence`.

Cada dirección incluye bytes, bit/s, MiB/s, resultado por stream y samples periódicos. `bidir` mantiene upload y download separados, no los colapsa en una cifra única.

### Responsividad

Antes de la carga se obtiene un baseline RTT con un echo de aplicación independiente. Durante cada stage se mantiene otro canal de probes y se calculan:

- min;
- p50, p90, p95 y p99;
- max;
- MAD (median absolute deviation).

Esto permite observar queueing bajo carga sin depender de ICMP.

### UDP

El generador usa pacing por quantum con presupuesto de bits, no un `Write` loop sin límite. El receptor informa:

- goodput útil;
- paquetes esperados/recibidos/perdidos;
- porcentaje de pérdida;
- paquetes observados fuera de orden;
- jitter EWMA basado en la diferencia entre spacing de envío y spacing de llegada;
- RTT idle y bajo carga.

El tamaño configurado incluye una cabecera netx de 36 bytes; el goodput reportado cuenta payload útil, no esa cabecera.

## Hot path y backpressure

Los workers de datos sólo actualizan contadores atómicos. El sampler los lee fuera del hot path. JSON y NDJSON se escriben **después** de finalizar la medición: una consola lenta o un pipe bloqueado no cambia el throughput observado.


## Diagnóstico de Fase 3

Por defecto, los tests TCP capturan telemetría al inicio y final de la ventana medida:

```sh
./bin/netx throughput --json 192.0.2.10
./bin/netx throughput --diagnostics=false 192.0.2.10
```

En Linux, cada stream conserva `TCP_INFO`: congestion control, RTT/RTTvar/minRTT, cwnd/ssthresh, retransmisiones, pacing/delivery rate, bytes sent/acked/retransmitted, `rwnd_limited`, `sndbuf_limited` y ECN/CE cuando el kernel los expone. También se capturan CPU del proceso, CPU global, RSS y PSI.

El diagnóstico no es una puntuación. Las reglas (`queueing-under-load`, `single-flow-limited`, `loss-retransmission-limited`, `receiver-window-limited`, `sender-buffer-limited`, `host-cpu-limited`, `ecn-congestion-signaled`) sólo aparecen cuando se cruza un umbral documentado y siempre incluyen la evidencia numérica. Véase [`docs/DIAGNOSTICS.md`](docs/DIAGNOSTICS.md).

`make diagnostics-overhead` mide el coste real de `TCP_INFO` y verifica que el presupuesto máximo de instrumentación queda por debajo del 1% de la ventana por defecto. `make diagnostics-ab` deja disponible un A/B loopback informativo.

## OpenWrt

```sh
make openwrt
```

Genera binarios estáticos para:

- `linux/amd64`, `linux/386`;
- `linux/arm` con `GOARM=5`, `6` y `7`;
- `linux/arm64`;
- `linux/mips`, `linux/mipsle` y `linux/mips64` soft-float;
- `linux/riscv64`.

Los artefactos quedan en `dist/`.

## Gates

```sh
make check        # gofmt + vet + unit tests
make race         # race detector
make smoke        # E2E loopback: TCP/UDP/latencia/JSON/NDJSON
make netem-check  # namespaces + tc netem; SKIP explícito sin CAP_NET_ADMIN
make openwrt      # cross-builds
make phase2-check # todo lo anterior
```

`netem-check` configura, cuando el host tiene permisos, 20 ms ± 3 ms de delay, 1% loss y 1% reorder entre dos namespaces Linux. En contenedores sin `CAP_SYS_ADMIN/CAP_NET_ADMIN` devuelve un `SKIP` explícito y no modifica la red del host.

La especificación completa y las decisiones derivadas de papers/RFC están en [`docs/SPEC.md`](docs/SPEC.md). El plan de cinco fases está en [`TODO.md`](TODO.md) y las reglas de ingeniería en [`SKILL.md`](SKILL.md).

## Fase 4

Además de TCP/UDP/latency:

```sh
netx available --min-rate 1M --max-rate 100M HOST
netx quic --direction upload --streams 4 HOST
netx scenario --profile request-response --message-size 1024 HOST
netx scenario --profile bursty --burst-messages 32 HOST
netx scenario --profile streaming --rate 10M HOST
netx responsiveness --direction bidir --streams 4 HOST
netx throughput --cc cubic HOST
netx cc-compare --algorithms cubic,bbr,reno HOST
```

`available` no es un alias de throughput. Devuelve una estimación de available bandwidth y puede rechazarla si la señal no es estable.

`responsiveness` publica un RPM aproximado y declara `draft_conformant=false`; consulte `docs/PHASE4.md` antes de compararlo con implementaciones conformes del draft IPPM.
