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

## 13. Protocolo wire y metodología — Fase 2

La Fase 2 eleva el protocolo wire a `protocol=2`. El control sigue usando TCP + JSON newline-delimited, con frames acotados. Cada sesión de datos recibe un token aleatorio de 128 bits.

### 13.1 TCP multi-stream

Una solicitud TCP incluye dirección (`tcp-upload`, `tcp-download`, `tcp-bidir`) y número de streams. El servidor anuncia un único puerto efímero; cada socket se autentica una vez con `DataHello{token, stream}`. Sólo después de recibir todos los streams se envía `ready`.

El receptor es quien calcula goodput. En upload mide el servidor; en download mide el cliente; en bidireccional cada extremo mide los bytes que recibe. Los emisores mantienen un guard corto fuera de la ventana puntuada para que el receptor pueda cerrar su ventana sin truncarla.

Los workers incrementan contadores atómicos por stream. Un sampler independiente toma snapshots a intervalos configurables. La serialización de resultados ocurre después de detener el test.

### 13.2 Single-flow y aggregate

Una suite TCP siempre comienza por un stage de 1 stream. Si se pide `--streams N`, después ejecuta N streams. Si se pide `--adaptive`, prueba 1,2,4,... hasta convergencia o `--max-streams`.

La convergencia se define mediante ganancia marginal porcentual sobre el stage anterior. Se conservan todos los stages y `aggregate` apunta al mejor score observado; un stage posterior peor no reemplaza al mejor.

Para `bidir`, el score usado sólo para la lógica de convergencia es la suma de ambos goodputs. La salida sigue mostrando upload y download como magnitudes separadas.

### 13.3 Responsividad

El RTT se mide con un canal TCP de echo de aplicación distinto de los sockets bulk. No incluye handshake: el socket de probe se establece antes de la serie. Se mide un baseline idle y otra serie durante la ventana cargada.

Se conservan samples RTT y se derivan min, p50, p90, p95, p99, max y MAD. Los timestamps para RTT usan el reloj monotónico local, por lo que no requieren sincronización entre hosts.

### 13.4 UDP paced

El datagrama de Fase 2 contiene 36 bytes de cabecera netx:

```text
magic[4] | token[16] | sequence[8] | sender_elapsed_ns[8] | payload...
```

El emisor usa un presupuesto de bits acumulado por `pacing-quantum`; no intenta representar una tasa mediante un loop de `Write` ilimitado. El `sender_elapsed_ns` permite clasificar paquetes según la ventana del emisor sin sincronizar relojes absolutos.

El receptor reporta:

- payload goodput;
- expected/received/lost;
- reorder observado;
- loss percentage;
- jitter EWMA, donde cada actualización usa `abs(arrival_spacing - sender_spacing)` y factor 1/16.

El target `--rate` representa bit/s del datagrama completo; el throughput reportado representa payload útil, de modo que será ligeramente menor incluso sin pérdida.

### 13.5 JSON y NDJSON

Los resultados estructurados llevan `schema_version=1` y `protocol_version=2`. JSON conserva summary y samples. NDJSON emite primero un summary sin arrays de samples y luego eventos `throughput_sample` / `latency_sample`.

La emisión es post-medición por diseño; un consumidor lento no aplica backpressure sobre los sockets bulk.

### 13.6 Netem

`scripts/netem.sh` crea dos network namespaces unidos por veth y aplica `tc netem` en el lado cliente (20 ms ± 3 ms, 1% loss, 1% reorder). El script requiere las capacidades de red correspondientes. Si el host no permite crear namespaces, informa `SKIP` explícitamente y no altera la red global.


## 14. Diagnóstico — Fase 3

La Fase 3 eleva el protocolo a `protocol_version=3` y el schema de resultados a `schema_version=2`. El cambio añade telemetría sin modificar la semántica de goodput, latencia ni UDP de Fase 2.

### 14.1 TCP_INFO

En Linux se realizan dos snapshots por stream, alineados con el inicio y final de la ventana medida. El decoder respeta la longitud real devuelta por `getsockopt(TCP_INFO)`, por lo que campos no presentes en kernels antiguos no se suponen válidos.

Se conservan tanto snapshots como deltas para retransmisiones, bytes, `busy_time`, `rwnd_limited`, `sndbuf_limited`, delivered y delivered CE. El congestion control se obtiene con `TCP_CONGESTION`.

Linux/386 usa el ABI `socketcall(GETSOCKOPT)`; las demás arquitecturas Linux de la matriz usan `getsockopt` directo. Ambos caminos son pure Go y no requieren cgo.

### 14.2 Host telemetry

La misma ventana captura CPU del proceso, CPU global, RSS y PSI cuando `/proc/pressure` existe. `process_cpu_percent_normalized` divide el consumo del proceso por el número de CPUs disponibles y es la métrica usada por la regla de saturación del proceso.

### 14.3 Diagnóstico

El diagnóstico se evalúa después de terminar los stages. Nunca modifica el hot path ni los contadores medidos. Las reglas y umbrales exactos están versionados en `docs/DIAGNOSTICS.md`; cada finding incluye los valores y thresholds que lo activaron.

### 14.4 Coste

La instrumentación TCP es O(streams) y hace dos `TCP_INFO` por stream. El gate `make diagnostics-overhead` mide el coste real de los snapshots `TCP_INFO` y verifica que, incluso al máximo de streams, el presupuesto de instrumentación sea <1% de la ventana por defecto. El A/B loopback queda como `make diagnostics-ab` informativo porque su varianza depende del scheduler y de la carga del host.

## 15. Implementación — Fase 4

La Fase 4 eleva el wire protocol a `4` y el schema de resultados a `3`.

Se añaden cuatro `measurement_kind` explícitos:

```text
transport_goodput
available_bandwidth_estimate
application_scenario
responsiveness_under_working_conditions
```

El estimador available-bandwidth usa `chirp-gap-dispersion-v1`; su definición, censura, intervalo de confianza, umbral de estabilidad y pacing portable están especificados en `docs/PHASE4.md`.

QUIC usa `quic-go v0.54.1` con listener efímero por stage. Los resultados QUIC reutilizan la semántica de `DirectionResult`: el receptor calcula bytes útiles dentro de la ventana puntuada y warm-up queda fuera.

`TCP_CONGESTION` puede fijarse en ambos endpoints Linux. El modo de comparación no presupone que todos los algoritmos estén instalados ni permitidos por el kernel.

El modo responsiveness sigue el principio IPPM de medir goodput, idle latency y latencia durante working conditions, pero Fase 4 no reproduce todavía el algoritmo HTTP foreign/self del draft -09. Por eso la salida máquina-legible impide confundir la aproximación con conformidad normativa.
