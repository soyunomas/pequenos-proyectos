# netx

`netx` es una herramienta de medición de red escrita en Go, inspirada en iperf3 pero diseñada para separar **goodput**, **capacidad**, **ancho de banda disponible** y **responsividad bajo carga** en lugar de reducir la red a una única cifra de “velocidad”.

El proyecto está orientado desde el principio a Linux y OpenWrt: binario autocontenido, `CGO_ENABLED=0`, sin dependencias externas en el hot path y compilación cruzada desde el `Makefile`.

> Estado: **Fase 1 terminada**. Hay un baseline TCP upload funcional, control plane separado del data plane, warm-up fuera de la ventana medida, JSON de salida, tests y matriz de compilación OpenWrt.

## Inicio rápido

```sh
make help
make check
make smoke
make build
```

Servidor:

```sh
./bin/netx server
```

Cliente:

```sh
./bin/netx throughput 192.0.2.10
./bin/netx throughput --duration 5s --warmup 1s --json 192.0.2.10
```

Puerto de control por defecto: `5202`. Cada medición abre un puerto TCP efímero independiente para datos.

## OpenWrt

`make openwrt` genera binarios estáticos para x86/x86_64, ARMv5/6/7, AArch64, MIPS/MIPSLE/MIPS64 soft-float y RISC-V 64. Los artefactos quedan en `dist/`.

## Diseño

```text
control TCP :5202
    │ request / offer / ready / result
    │
    └── data TCP :ephemeral
            token + bulk stream
```

La Fase 1 implementa `connect → warm-up → measurement window → guard/cooldown → result`. El warm-up y el guard no se cuentan.

La especificación completa está en [`docs/SPEC.md`](docs/SPEC.md), el plan de cinco fases en [`TODO.md`](TODO.md) y las reglas de ingeniería en [`SKILL.md`](SKILL.md).

## Desarrollo

`make help` es la interfaz principal. Incluye `build`, `check`, `race`, `smoke`, `openwrt`, `size` y `clean`.

No se optimiza una ruta por intuición. Sockets avanzados, batching, GSO/GRO, afinidad, NUMA o zero-copy entrarán sólo con benchmarks que demuestren el cuello de botella.
