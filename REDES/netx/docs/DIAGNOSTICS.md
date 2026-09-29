# Diagnóstico determinista — Fase 3

NetX no asigna una puntuación opaca a la red. El diagnóstico de Fase 3 es un conjunto de reglas deterministas que siempre devuelve la evidencia numérica que activó cada regla.

## Fuentes de telemetría

En Linux, NetX toma dos snapshots `TCP_INFO` por stream: uno al inicio y otro al final de la ventana medida. No se consulta `TCP_INFO` por paquete ni por sample de throughput.

Campos expuestos cuando el kernel los entrega:

- congestion control activo;
- RTT, RTTvar, min RTT y RTO;
- cwnd, ssthresh, unacked, lost, retrans y total retrans;
- pacing rate y delivery rate;
- bytes acked/received/sent/retransmitted;
- busy time, rwnd-limited time y sndbuf-limited time;
- delivered y delivered CE;
- ventanas de envío/recepción y reordering.

`tcp_info_length` indica cuántos bytes devolvió el kernel. NetX decodifica sólo campos presentes en esa longitud; así puede funcionar con kernels OpenWrt antiguos sin asumir que existen campos recientes.

El bit ECN negociado se obtiene de `tcpi_options`. `delivered_ce` se usa como evidencia de congestión marcada cuando el kernel lo expone.

## Métricas del host

En Linux se captura al inicio y al final de la ventana:

- CPU del proceso NetX como porcentaje de un core;
- CPU del proceso normalizada por número de CPUs disponibles;
- utilización CPU global derivada de `/proc/stat`;
- RSS del proceso;
- PSI `cpu some avg10` y `memory some avg10` cuando `/proc/pressure` está disponible.

Las métricas del host se calculan sobre la misma ventana que el goodput. En plataformas no Linux el bloque queda marcado como no soportado; el test de red sigue funcionando.

## Roles

Las reglas que dependen del comportamiento del emisor sólo inspeccionan endpoints con rol `sender` o `bidirectional`.

- upload: cliente = sender, servidor = receiver;
- download: cliente = receiver, servidor = sender;
- bidirectional: ambos = bidirectional.

Esto evita interpretar `rwnd_limited`, pacing o delivery rate de un endpoint que sólo recibe como si fueran métricas del emisor del flujo medido.

## Reglas

### `queueing-under-load`

Se activa si:

```text
loaded_p95 - idle_p95 >= max(5 ms, idle_p95 * 50%)
```

Evidencia: RTT p95 idle, p95 bajo carga e incremento absoluto.

### `single-flow-limited`

Se activa si el mejor stage agregado usa más de un stream y mejora al single-flow al menos un 15%:

```text
(aggregate_goodput - single_goodput) / single_goodput >= 15%
```

En bidireccional la suma de upload+download sólo se usa para esta comparación interna; la salida mantiene ambas direcciones separadas.

### `loss-retransmission-limited`

Se activa si los bytes retransmitidos durante la ventana representan al menos 0,1% de los bytes enviados por el endpoint emisor.

El contador absoluto de retransmisiones se muestra como evidencia, pero por sí solo no dispara la regla: unos pocos segmentos retransmitidos en cientos de MB no justifican etiquetar la prueba como limitada por pérdida.

### `receiver-window-limited`

Se activa si:

```text
rwnd_limited_usec / busy_time_usec >= 5%
```

Sólo se evalúa en emisores.

### `sender-buffer-limited`

Se activa si:

```text
sndbuf_limited_usec / busy_time_usec >= 5%
```

Sólo se evalúa en emisores.

### `host-cpu-limited`

Se activa si el proceso NetX consume al menos el 90% de la capacidad total de CPU disponible durante la ventana:

```text
process_cpu_percent_normalized >= 90%
```

Para un stage single-flow también se activa si `process_cpu_percent_one_core >= 90%`, porque un único flujo puede quedar limitado por un core aunque existan más CPUs ociosas. La utilización global del host se incluye como evidencia adicional.

### `ecn-congestion-signaled`

Se activa si TCP negoció ECN y `delivered_ce` aumentó durante la ventana. Se reportan segmentos CE y porcentaje respecto a `delivered` cuando esté disponible.

## Coste de observación

El diseño evita instrumentación por paquete. La validación incluye:

1. `make diagnostics-overhead` ejecuta `TestSnapshotCostBudget`, que mide el syscall real de `TCP_INFO`. El límite es 500 µs por snapshot. Incluso con 64 streams y dos snapshots por stream, ese techo mantiene el presupuesto síncrono por debajo de 1% de la ventana por defecto de 10 s. En el host de validación de Fase 3 se midieron 3,965 µs por snapshot en el gate final.
2. `make diagnostics-ab` ofrece un A/B loopback adicional con diagnóstico apagado/encendido. Es informativo, no un gate: loopback tiene variación material por scheduler, frecuencia de CPU y carga del host, y no debe convertir ruido de máquina en un fallo de corrección.

El criterio obligatorio <1% se aplica al coste directo de la instrumentación que NetX añade al hot path, no a la variación de un benchmark loopback compartido.

## Portabilidad

El decoder usa el layout UAPI estable de `struct tcp_info` y longitudes devueltas por `getsockopt`. Linux/386 necesita el ABI histórico `socketcall(GETSOCKOPT)`; el resto de arquitecturas Linux de la matriz usa `getsockopt` directo. Ambos caminos están aislados por build tags.

No se usa cgo. La matriz OpenWrt sigue siendo `CGO_ENABLED=0`.
