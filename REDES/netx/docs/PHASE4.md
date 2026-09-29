# Fase 4 — metodología y alcance

## Regla de etiquetado

NetX mantiene separadas estas magnitudes:

- `transport_goodput`: bytes útiles entregados por TCP o QUIC bajo una configuración concreta.
- `available_bandwidth_estimate`: estimación activa del ancho de banda actualmente disponible.
- `responsiveness_under_working_conditions`: latencia de interacción mientras existe carga.
- `application_scenario`: rendimiento observado por un patrón de aplicación.

Ninguna de ellas se etiqueta como `capacity` ni como capacidad física del cuello de botella.

## Available bandwidth: chirp-gap-dispersion-v1

El modo `netx available` es **inspirado** en pathChirp/SLoPS; no pretende reproducir literalmente esas implementaciones.

Cada chirp contiene datagramas UDP con tasas de entrada crecientes geométricamente. El emisor incluye el instante monotónico real de envío de cada datagrama. El receptor compara, por parejas consecutivas:

```text
input_rate = udp_datagram_bits / actual_send_gap
gap_ratio  = receiver_arrival_gap / actual_send_gap
```

La tasa es de datagrama UDP de NetX; no incluye overhead L2/IP/UDP.

Se considera una señal de expansión cuando dos pares consecutivos tienen `gap_ratio >= 1.25`. La primera expansión sostenida produce un intervalo [tasa anterior, tasa de cruce] y la estimación individual es su media geométrica.

Un chirp se rechaza si pierde más del 20% de sus paquetes o quedan menos de cuatro pares analizables. Si no aparece cruce antes del techo de probing queda censurado por la derecha; si el cruce está ya en el primer par queda censurado por la izquierda.

La agregación usa la mediana de las estimaciones válidas. Además se calcula un intervalo de confianza aproximado del 95% sobre la media de las estimaciones por chirp. NetX sólo marca `estimate_valid=true` cuando:

- hay al menos tres chirps válidos y al menos la mitad de los chirps solicitados;
- la anchura relativa del intervalo no supera 35%.

Una mayoría de chirps censurados por la derecha devuelve una instrucción explícita para elevar `--max-rate`. Una señal inestable devuelve los samples y el intervalo, pero **no** una estimación válida.

El pacing portable impone un gap mínimo de 50 µs. Si la tasa solicitada exigiría gaps menores, se rechaza la configuración en lugar de fingir precisión que el scheduler no puede proporcionar.

## QUIC

`netx quic` mide goodput QUIC en upload, download y bidireccional con baseline single-stream y stage multi-stream. Se usa `quic-go v0.54.1`; NetX no implementa una pila QUIC propia.

El control plane sigue siendo TCP. Cada stage abre un listener QUIC efímero y usa un token aleatorio anunciado por control para asociar los streams de datos. El certificado TLS QUIC es efímero y autofirmado; el cliente omite su validación porque el objetivo aquí es medir transporte, no proporcionar autenticación de host. Esto **no** convierte el control plane en un protocolo seguro.

La latencia cargada se obtiene con un canal de eco independiente, igual que en los tests TCP, para evitar medir el backlog del propio stream bulk.

## Escenarios de aplicación

`netx scenario` incluye cuatro perfiles TCP:

- `request-response`: una petición, una respuesta y RTT de operación;
- `small-message`: el mismo patrón orientado a payloads pequeños;
- `bursty`: ráfagas de mensajes seguidas de pausa;
- `streaming`: frames a una tasa objetivo sin saturar deliberadamente el enlace.

Los resultados se etiquetan como `application_scenario`, no como capacidad.

## Comparación de congestion control

`netx throughput --cc NAME` solicita `TCP_CONGESTION` en ambos extremos cuando el sistema lo permite.

`netx cc-compare --algorithms ...` ejecuta la misma metodología para cada algoritmo. Un algoritmo inexistente o no permitido se conserva como `supported=false` con el error del sistema. La salida no elige un “ganador”: expone goodput, p95 cargado y porcentaje de bytes retransmitidos.

## Responsiveness under working conditions

El draft IPPM vigente al implementar esta fase es `draft-ietf-ippm-responsiveness-09` (6 julio 2026). Su algoritmo completo usa tráfico HTTP, probes “foreign” y “self”, una regla de estabilización por intervalos y agregación mediante trimmed means.

Fase 4 implementa un modo deliberadamente más pequeño:

- genera carga TCP real;
- mantiene probes de eco de aplicación independientes;
- conserva goodput, RTT idle y distribución de RTT bajo carga;
- publica `working_rpm_approx = 60000 / working_rtt_p95_ms`.

Por ello el resultado contiene siempre:

```json
"draft_conformant": false
```

y una explicación de la desviación. El número RPM aproximado no debe compararse como si fuera el RPM normativo del draft.

## Gates

`make methodology-check` exige:

1. que una señal sintética estable produzca una estimación válida;
2. que una señal sintética bimodal/inestable sea rechazada;
3. que available bandwidth, transport goodput y responsiveness tengan etiquetas distintas y ninguna use `capacity`.

`make phase4-check` añade race, smoke E2E, diagnóstico, netem cuando el host lo permite y la matriz OpenWrt completa.
La estimación de available bandwidth exige una **mayoría estricta** de chirps válidos. Un empate entre chirps acotados y censurados no se considera evidencia suficiente para publicar una estimación estable.
