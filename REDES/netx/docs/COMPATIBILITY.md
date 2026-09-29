# Compatibilidad de netx 0.5

## Baseline portable

El baseline soportado es Go 1.23, `CGO_ENABLED=0` y sockets estándar. TCP, UDP, latency y los modos de Fase 4 no necesitan cgo.

Matriz de release:

| Artefacto | GOARCH | Ajuste |
|---|---|---|
| netx-linux-amd64 | amd64 | — |
| netx-linux-386 | 386 | — |
| netx-linux-armv5 | arm | GOARM=5 |
| netx-linux-armv6 | arm | GOARM=6 |
| netx-linux-armv7 | arm | GOARM=7 |
| netx-linux-arm64 | arm64 | — |
| netx-linux-mips-softfloat | mips | GOMIPS=softfloat |
| netx-linux-mipsle-softfloat | mipsle | GOMIPS=softfloat |
| netx-linux-mips64-softfloat | mips64 | GOMIPS64=softfloat |
| netx-linux-riscv64 | riscv64 | — |

## Capacidades Linux opcionales

| Capacidad | Requisito | Comportamiento si falta |
|---|---|---|
| TCP_INFO | Linux | telemetría no soportada; la prueba sigue |
| CPU/NUMA affinity | Linux + sched_setaffinity | sólo se usa si se solicita |
| SO_TIMESTAMPNS | kernel Linux | `--timestamp kernel` falla al configurar |
| SO_TIMESTAMPING hardware | kernel + driver/NIC preconfigurado | se informa fallback; no se afirma HW |
| UDP_SEGMENT / UDP_GRO | kernel/driver | sólo capability probe en 0.5 |
| AF_XDP | Linux + driver/UMEM | no habilitado en 0.5 |

`netx capabilities --json` es la fuente de verdad para el host concreto.

## OpenWrt

El paquete preparado por `make openwrt-feed` no necesita runtime Go ni cgo. El servicio queda deshabilitado en UCI por defecto.

Routers con almacenamiento o RAM muy limitados pueden omitir el daemon y ejecutar el binario bajo demanda. QUIC incrementa el tamaño del binario respecto a un medidor TCP/UDP mínimo porque incorpora `quic-go`.
