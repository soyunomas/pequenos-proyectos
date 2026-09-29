# Benchmarks de netx

No se extrapolan velocidades de una máquina a otra. Esta tabla sólo se rellena con hardware físico identificado.

| Link nominal | Estado | NIC / driver | CPU | Kernel | MTU | Modo | Resultado | CPU | Notas |
|---:|---|---|---|---|---:|---|---|---|---|
| 1 GbE | not measured | — | — | — | — | — | — | — | hardware no disponible en CI |
| 2.5 GbE | not measured | — | — | — | — | — | — | — | hardware no disponible en CI |
| 10 GbE | not measured | — | — | — | — | — | — | — | hardware no disponible en CI |
| 25 GbE | not measured | — | — | — | — | — | — | — | hardware no disponible en CI |
| 40 GbE | not measured | — | — | — | — | — | — | — | hardware no disponible en CI |
| 100 GbE | not measured | — | — | — | — | — | — | — | hardware no disponible en CI |

## Protocolo de benchmark

Antes de cualquier optimización:

```sh
make profile-fastpath
make fastpath-bench
netx capabilities --json
```

En pruebas de enlace real se deben registrar como mínimo:

- modelo de NIC y versión de driver/firmware;
- kernel;
- CPU, governor y topología NUMA;
- MTU;
- offloads relevantes;
- dirección y número de streams;
- congestion control;
- goodput y percentiles de latencia;
- CPU de proceso/sistema, RSS y retransmisiones;
- perfiles before/after si se cambia el hot path.

El benchmark `fastpath-bench` compara coste de syscall/dispatch en loopback; no se interpreta como Gbit/s alcanzables en una NIC.
