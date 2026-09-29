# Especificación técnica de netx

## 1. Propósito

`netx` será una herramienta moderna de medición y diagnóstico de red en Go. Toma iperf3 como referencia de ergonomía y generación de tráfico, pero evita convertir toda la calidad de una ruta en una cifra única.

Principio rector:

> No existe una única “velocidad de la red”. Hay propiedades distintas que deben medirse y etiquetarse de forma explícita.

Las magnitudes principales son:

- **Capacity:** límite físico/lógico del cuello de botella de la ruta.
- **Available bandwidth:** capacidad que queda disponible bajo el tráfico cruzado existente.
- **TCP/QUIC goodput:** bytes útiles entregados por un transporte concreto bajo un conjunto concreto de condiciones.
- **Responsiveness:** latencia/servicio interactivo, especialmente mientras la ruta transporta carga.

Nunca se presentarán estas magnitudes como equivalentes.

## 2. Objetivos

- Binario único, portable y fácil de desplegar en OpenWrt.
- TCP, UDP y QUIC como transportes de primera clase a medida que avance el roadmap.
- Single-flow y multi-flow diferenciados.
- Latencia idle y bajo carga.
- Diagnóstico basado en métricas del kernel/host cuando estén disponibles.
- Series temporales y resultados máquina-legibles.
- Available bandwidth de baja intrusión inspirado en investigación activa (pathChirp/SLoPS).
- Escalar desde routers modestos hasta hosts de 10/25/40/100G mediante backends opcionales, sin sacrificar el baseline portable.

## 3. No objetivos

- No producir un “network score” opaco.
- No maximizar artificialmente la cifra abriendo muchos flujos sin mostrar el single-flow.
- No exigir PTP/hardware timestamping para el caso normal de RTT.
- No adoptar DPDK/AF_XDP como dependencia base.
- No reimplementar QUIC.
- No llamar “capacidad” al throughput observado de TCP.

## 4. Metodología prevista

### 4.1 Throughput / goodput

Debe reportar al menos:

```text
single_stream_goodput
aggregate_goodput (si hay paralelismo)
duración efectiva
bytes medidos
serie temporal
```

El paralelismo futuro será adaptativo: 1 → 2 → 4 → 8… hasta que la ganancia marginal quede por debajo de un umbral durante ventanas suficientes. El resultado single-flow se conserva siempre.

### 4.2 Fases temporales

```text
connect → warm-up → ramp/steady-state detection → measurement → cooldown
```

La Fase 1 implementa `connect → warm-up → measurement → guard`. Los samples de warm-up nunca se mezclan con la ventana puntuada.

### 4.3 Responsividad

Durante upload y download se mantendrán probes ligeros independientes del bulk flow. Se registrarán al menos:

```text
idle RTT: p50/p95/p99
loaded RTT: p50/p95/p99
queueing delay = loaded RTT - baseline/idle reference
MAD/jitter
```

Cuando sea útil se compararán ICMP y application echo (UDP/QUIC), porque una red puede tratar ICMP de forma distinta al tráfico de aplicación.

### 4.4 Estadística

No esconder variabilidad bajo un promedio único. Conservar samples brutos y derivar, según la magnitud:

- mediana;
- p5/p95 para throughput por intervalos;
- p50/p90/p95/p99 para latencia;
- MAD y coeficiente de variación;
- intervalos de confianza donde tenga sentido.

La precisión mostrada debe corresponder a la incertidumbre real; evitar cifras como `942.318742 Mbit/s` cuando el método no las justifica.

## 5. Diagnóstico previsto

En Linux, `TCP_INFO` permitirá incorporar, según soporte del kernel:

```text
RTT / RTT variance
cwnd
ssthresh
retransmits / bytes_retrans
unacked / lost / reordering
RTO
min RTT
delivery_rate
pacing_rate
bytes_acked
rwnd-limited time
sndbuf-limited time
```

Reglas diagnósticas futuras serán deterministas y mostrarán evidencia. Ejemplos:

```text
aggregate >> single        -> single-flow limited
loaded RTT >> idle RTT     -> queueing under load
retransmission rate high   -> loss/retransmission limited
sender CPU saturated       -> sender CPU limited
rwnd-limited time high     -> receiver-window limited
```

No se emitirá un diagnóstico sin los datos que lo justifican.

## 6. Transportes

### TCP

Baseline portable. Debe exponer el algoritmo de congestion control y estado interno donde el OS lo permita.

### UDP

Necesita pacing explícito, secuencia, pérdida, reorder y jitter. El generador no puede depender de un `Write` loop descontrolado para representar una tasa objetivo.

### QUIC

Se incorporará mediante una implementación mantenida (p. ej. quic-go), no mediante una pila propia. Permitirá comparar coste userspace y comportamiento con TCP en la misma ruta.

## 7. Available bandwidth y capacidad

Se implementará un modo distinto del bulk throughput, inspirado en:

- SLoPS / Pathload: detectar congestión inducida mediante trenes a tasas controladas.
- pathChirp: chirps de tasa creciente y análisis de dispersión/interarrival.
- IGI/PTR: sensibilidad a tamaño de paquetes, longitud de tren y burstiness.

El modo deberá reportar intervalo/estabilidad y abstenerse de dar una estimación precisa si la señal no es fiable.

## 8. Arquitectura de software

Objetivo evolutivo:

```text
cmd/netx
internal/control + protocol
internal/runner
transport/tcp
transport/udp
transport/quic
measurement/throughput
measurement/latency
measurement/responsiveness
measurement/chirp
metrics/tcpinfo
metrics/host
platform/linux | darwin | windows
output/terminal | json | ndjson
```

En Fase 1 se mantiene una estructura menor pero compatible con esta evolución. El control plane y el data plane ya están separados.

## 9. Protocolo wire — Fase 1

- Control: TCP en puerto 5202 por defecto.
- Framing: JSON delimitado por newline, máximo 64 KiB por frame.
- Data: TCP en puerto efímero anunciado por control.
- Asociación: token aleatorio de 128 bits codificado en hexadecimal.
- Modo inicial: `tcp-upload`.
- Timeouts/deadlines obligatorios.
- Parámetros acotados para evitar allocations/timers absurdos.

Flujo:

```text
client                     server
  |--- Request ----------->| control :5202
  |<-- Offer(port,token) ---|
  |--- data connect ------>| ephemeral
  |--- token ------------->|
  |<-- ready --------------|
  |=== TCP bulk ==========>|
  |<-- result -------------|
```

## 10. OpenWrt y compilación cruzada

Baseline:

```text
CGO_ENABLED=0
-trimpath
-ldflags "-s -w -buildid= ..."
```

La matriz inicial cubre x86, x86_64, ARMv5/6/7, AArch64, MIPS/MIPSLE/MIPS64 soft-float y RISC-V 64. Los backends Linux avanzados se aislarán para no romper esta propiedad.

## 11. Fast path futuro

Orden de escalado:

1. sockets Go normales + buffers preasignados;
2. batching/syscalls Linux sólo con benchmark;
3. GSO/GRO si la plataforma lo soporta y mejora el resultado;
4. timestamping de kernel/hardware opcional;
5. afinidad/NUMA para máquinas grandes;
6. AF_XDP/XDP sólo si el perfil demuestra que sockets normales son el límite.

Para 10/25/100G se evitarán allocations en hot path, mutex globales y reporting síncrono. Los acumuladores serán locales por worker cuando aparezca paralelismo.

## 12. Referencias que guían el diseño

- Manish Jain, Constantinos Dovrolis, *End-to-End Available Bandwidth: Measurement Methodology, Dynamics, and Relation with TCP Throughput*.
- Vinay Ribeiro et al., *pathChirp: Efficient Available Bandwidth Estimation for Network Paths*.
- Ningning Hu, Peter Steenkiste, trabajos sobre IGI/PTR y caracterización de available-bandwidth probing.
- Jain & Dovrolis, *Ten Fallacies and Pitfalls on End-to-End Available Bandwidth Estimation*.
- Flowgrind, por instrumentación de TCP y generación de escenarios.
- MoonGen, por generación de paquetes y timestamping de alto rendimiento.
- Cardwell et al., BBR, por separar bottleneck bandwidth y propagation RTT.
- RFC 6349, por BDP/TCP efficiency/buffer delay como conceptos de diagnóstico.
- RFC 9330 y familia L4S/ECN, para trabajo futuro de latencia baja bajo carga.
- IETF IPPM, *Responsiveness under Working Conditions*, como base metodológica actual para responsividad.
- Estudios comparativos de Ookla/NDT7 que muestran cómo la metodología del test modifica el resultado observado.

Estas referencias justifican el diseño; no implican copiar una implementación concreta.
