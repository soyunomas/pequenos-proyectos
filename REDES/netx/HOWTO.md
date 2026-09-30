# HOWTO de netx: medir, comparar y entender tu red

Esta guía explica cómo usar netx desde una primera prueba hasta mediciones de pérdida, latencia bajo carga y escenarios de aplicación. Si aún no tienes el ejecutable, empieza por la [instalación del README](README.md#instalación).

## Índice

- [Conceptos y unidades](#conceptos-y-unidades)
- [Preparar los equipos](#preparar-los-equipos)
- [Probar en un solo equipo](#probar-en-un-solo-equipo)
- [Medir TCP](#medir-tcp)
- [Medir latencia y respuesta bajo carga](#medir-latencia-y-respuesta-bajo-carga)
- [Medir UDP: tasa, pérdida y jitter](#medir-udp-tasa-pérdida-y-jitter)
- [Estimar el ancho de banda disponible](#estimar-el-ancho-de-banda-disponible)
- [Medir QUIC](#medir-quic)
- [Probar escenarios de aplicación](#probar-escenarios-de-aplicación)
- [Guardar resultados y automatizar](#guardar-resultados-y-automatizar)
- [Colores de la terminal](#colores-de-la-terminal)
- [Diagnóstico y opciones del host](#diagnóstico-y-opciones-del-host)
- [OpenWrt y servicio procd/UCI](#openwrt-y-servicio-procduci)
- [Interpretar los resultados](#interpretar-los-resultados)
- [Resolver problemas](#resolver-problemas)

## Conceptos y unidades

### Throughput, goodput y capacidad

**Throughput** es la cantidad de datos que atraviesa un sistema por unidad de tiempo. Para interpretar una cifra hay que saber qué bytes se cuentan y durante qué intervalo.

**Goodput** es la tasa de datos útiles entregados a la aplicación. netx reporta goodput en sus pruebas de transporte TCP/QUIC y en el payload recibido por UDP. No añade las cabeceras Ethernet/IP/TCP/UDP a esos bytes útiles.

**Capacidad del enlace** es el límite de transmisión del enlace considerado. La capacidad de una ruta depende de su cuello de botella. Una etiqueta «1 Gbit/s» de una interfaz no garantiza 1 Gbit/s de payload en una aplicación: influyen cabeceras, retransmisiones, CPU, buffers y transporte. netx no convierte automáticamente el goodput observado en capacidad física.

**Ancho de banda disponible** es la parte de la capacidad que podría aprovechar tráfico adicional en ese momento, dada la carga existente. Cambia con el tráfico de otros usuarios. `available` realiza una estimación activa con sondas, distinta de una transferencia bulk.

### Latencia, RTT y percentiles

**Latencia** es el tiempo que tarda una comunicación o una operación. Puede referirse a un trayecto de ida o a una interacción completa; conviene indicar cuál se mide.

**RTT (round-trip time)** es el tiempo de ida y vuelta. netx envía una sonda de eco de aplicación y mide hasta recibir la respuesta. No es ICMP ping, no mide sólo la tarjeta de red y no necesita sincronizar los relojes de los dos equipos. Dividir el RTT entre dos no demuestra la latencia de ida: las rutas y las colas pueden ser asimétricas.

**Latencia idle** es el RTT de referencia sin la transferencia de carga de netx. Puede haber tráfico ajeno a la prueba. **Latencia loaded** es el RTT mientras netx genera carga, usando un canal de sondas independiente del flujo de datos.

| Estadística | Qué significa |
| --- | --- |
| min / max | Menor y mayor RTT observados |
| p50 | Mediana: aproximadamente la mitad de las muestras queda por debajo |
| p90 / p95 / p99 | Percentiles: describen la cola de la distribución; p95 resume el umbral del 95% de las muestras |
| MAD | Mediana de las desviaciones absolutas respecto a la mediana; resume dispersión |

Los percentiles altos necesitan suficientes muestras. Un p99 obtenido con muy pocas sondas no caracteriza bien eventos raros.

**Bufferbloat** es latencia excesiva causada por la acumulación de paquetes en colas. Un aumento del RTT bajo carga puede ser compatible con este problema; por sí solo no identifica dónde está la cola ni demuestra una causa concreta.

### Pérdida, jitter y reordenamiento

**Pérdida de paquetes** es la parte de los paquetes esperados que no llegó al receptor durante la prueba. UDP permite observarla directamente. TCP retransmite para entregar un flujo fiable; sus retransmisiones se estudian mediante la telemetría, no con el mismo contador de pérdida UDP.

**Jitter** es variación temporal en la llegada de los paquetes. En netx UDP se calcula una media móvil exponencial (EWMA) de la diferencia entre el espaciado de envío y el de recepción. No es una medición de retardo de ida absoluto ni el mismo estadístico que MAD del RTT. Poca pérdida con jitter alto puede seguir siendo problemática para audio o vídeo en tiempo real.

**Reordenamiento** significa que un paquete llega fuera del orden de secuencia esperado. Es distinto de pérdida: un paquete puede llegar tarde y aun así haberse recibido.

### Flujos, calentamiento y muestras

**Flujo o stream** es una unidad de transferencia. En TCP, varios streams usan conexiones paralelas. En QUIC, los streams del stage comparten una conexión QUIC. Un solo flujo y varios flujos pueden aprovechar la ruta de forma diferente.

**Warm-up o calentamiento** es el tráfico inicial excluido de la ventana puntuada. Permite que transporte y buffers se estabilicen antes de contabilizar el resultado.

**Sample o muestra de throughput** es un intervalo de observación dentro de la ventana. Las muestras permiten ver variaciones que una media global oculta. Las sondas RTT tienen su propio intervalo.

**RPM aproximado** es el indicador de respuesta bajo carga de netx: `60000 / RTT_p95_en_ms`. Un valor mayor corresponde a menor p95, pero no representa transacciones de aplicación medidas durante un minuto. netx declara `draft_conformant=false`: no es una implementación conforme de la metodología IPPM ni debe compararse como si lo fuera.

### Unidades

| Unidad | Interpretación |
| --- | --- |
| bit/s, Mbit/s, Gbit/s | Bits por segundo; M y G son factores decimales |
| B/s, MB/s | Bytes por segundo; un byte contiene 8 bits |
| MiB/s | Mebibytes por segundo; 1 MiB = 1 048 576 bytes |
| ms / µs | Milisegundos / microsegundos |
| % de pérdida | Proporción de paquetes esperados que faltan |

Por ejemplo, 100 Mbit/s equivalen a 12.5 MB/s, aproximadamente 11.92 MiB/s. En `--rate`, `10M` significa 10 000 000 bit/s; `2.5M`, 2 500 000 bit/s. En duraciones usa `500ms`, `2s` o `1m`.

## Preparar los equipos

Los ejemplos usan un servidor `192.168.1.10` y un cliente con conectividad hacia él. Sustituye esa IP por la tuya. Se asume que `netx` está en el PATH; si trabajas desde el código fuente sin instalarlo, usa `./bin/netx`.

En el servidor:

```sh
netx version
netx capabilities
netx server --listen 192.168.1.10
```

En el cliente:

```sh
netx version
netx latency 192.168.1.10
```

Usa preferentemente la misma versión en los dos extremos. Deja el servidor en ejecución y detenlo con `Ctrl+C` al terminar. No necesitas root para las pruebas ordinarias con sockets y puertos no privilegiados.

El control usa TCP 5202 y las pruebas negocian puertos de datos dinámicos. La política de red debe permitir esas conexiones entre los equipos; abrir sólo 5202 no basta. El servidor no proporciona autenticación de usuarios. Usa una LAN de confianza o una VPN y limita el acceso a los equipos de prueba.

### Cambiar el puerto de control

En el servidor:

```sh
netx server --listen 192.168.1.10 --port 5302
```

En el cliente, cada prueba debe usar el mismo puerto:

```sh
netx latency --port 5302 192.168.1.10
netx throughput --port 5302 192.168.1.10
```

`--port` no controla los puertos dinámicos de datos. Escribe todas las opciones antes de `HOST`; el parser deja de leer opciones cuando encuentra el argumento del host.

## Probar en un solo equipo

Abre dos terminales. En la primera:

```sh
netx server --listen 127.0.0.1
```

En la segunda:

```sh
netx latency 127.0.0.1
netx throughput --duration 2s --warmup 500ms 127.0.0.1
netx udp --rate 10M --duration 2s --warmup 500ms 127.0.0.1
```

El tráfico usa loopback: no sale por Ethernet ni Wi-Fi. Un resultado de muchos Gbit/s aquí puede reflejar memoria, CPU y pila de red local; no es la velocidad de tu conexión a Internet.

## Medir TCP

### Subida, bajada y ambas a la vez

```sh
# Subida: cliente -> servidor
netx throughput 192.168.1.10

# Bajada: servidor -> cliente
netx throughput --direction download 192.168.1.10

# Ambos sentidos simultáneamente
netx throughput --direction bidir 192.168.1.10
```

La dirección siempre se interpreta desde el cliente. En `bidir`, subida y bajada permanecen separadas. No las sumes para compararlas con una medición unidireccional.

### Comparar un flujo con cuatro

```sh
netx throughput --streams 4 192.168.1.10
```

netx ejecuta primero un stage de un flujo y después uno de cuatro. `single_stream` conserva el baseline; `aggregate` conserva el mejor stage probado. `--streams 4` no elimina la prueba de un flujo ni garantiza que cuatro sean mejores.

Una mejora con varios flujos sugiere que la prueba de un flujo no aprovechaba todo lo alcanzable con esa configuración. No demuestra por sí sola si el límite era la CPU, la ventana TCP, el transporte o la red.

### Buscar un número de flujos de forma adaptativa

```sh
netx throughput --adaptive --max-streams 8 --convergence 5 192.168.1.10
```

Prueba 1, 2, 4… hasta el límite o hasta que la ganancia marginal no alcance el umbral configurado. `--convergence 5` usa un umbral del 5%. Consulta los stages y el motivo de parada; se conserva el mejor probado aunque un stage posterior empeore.

### Ajustar duración, calentamiento y observación

```sh
netx throughput --duration 30s --warmup 3s --sample 500ms --probe-interval 100ms 192.168.1.10
```

| Opción | Valor predeterminado en `throughput` | Uso |
| --- | --- | --- |
| `--direction` | `upload` | Sentido del tráfico |
| `--duration` | `10s` | Ventana puntuada por stage |
| `--warmup` | `2s` | Tráfico previo excluido del resultado |
| `--streams` | `1` | Número objetivo de flujos |
| `--sample` | `250ms` | Intervalo de muestras de throughput |
| `--probe-interval` | `100ms` | Intervalo de sondas RTT bajo carga |
| `--buffer` | `131072` | Buffer de usuario por flujo, en bytes; no es el buffer del kernel |
| `--dial-timeout` | `5s` | Tiempo máximo para establecer la conexión |
| `--diagnostics` | `true` | Recogida de telemetría y diagnóstico TCP |

La duración total supera `--duration`: incluye preparación, RTT idle, warm-up y todos los stages. Consulta las opciones con `netx throughput -h`.

## Medir latencia y respuesta bajo carga

### RTT sin carga generada por netx

```sh
netx latency --duration 10s --interval 100ms 192.168.1.10
```

La salida de texto resume p50, p95, p99 y MAD; el JSON incluye también min y max. Por defecto, `latency` dura un segundo; este ejemplo amplía el número de muestras. En este comando se usa `--interval`, no `--probe-interval`.

### RTT mientras se transfieren datos

Las pruebas TCP y UDP incluyen RTT idle y loaded. Para observar explícitamente la respuesta bajo carga:

```sh
netx responsiveness --direction bidir --streams 4 --duration 15s 192.168.1.10
netx responsiveness --direction upload --streams 1 --json 192.168.1.10
```

Por defecto, `responsiveness` usa dirección `bidir` y cuatro flujos. Compara el RTT idle con el p95 loaded y revisa cada dirección de transferencia. Su RPM es aproximado; interpreta también las latencias originales.

## Medir UDP: tasa, pérdida y jitter

UDP envía a una tasa solicitada; no intenta descubrir automáticamente la mayor tasa sin pérdidas. Empieza por una tasa moderada y aumenta en pruebas separadas:

```sh
netx udp --rate 10M --duration 10s 192.168.1.10
netx udp --rate 50M --duration 10s 192.168.1.10
netx udp --rate 100M --duration 10s 192.168.1.10
```

La salida incluye goodput recibido, pérdida porcentual, reordenamiento y jitter; el JSON incluye además los contadores de paquetes esperados/recibidos/perdidos. Una tasa que excede lo que pueden sostener ruta o receptor puede provocar pérdida.

### Tamaño de datagrama y payload útil

```sh
netx udp --rate 10M --packet-size 1200 192.168.1.10
```

`--packet-size` incluye la cabecera netx de 36 bytes. Con 1200 bytes, el payload es 1164 bytes y un objetivo de 10 Mbit/s corresponde a aproximadamente **9.7 Mbit/s útiles**, aun sin pérdida. El objetivo no incluye las cabeceras UDP/IP/Ethernet. Evita tamaños que provoquen fragmentación en la ruta, especialmente al atravesar túneles.

### Pacing

```sh
netx udp --rate 20M --pacing-quantum 1ms 192.168.1.10
```

El emisor usa pacing: distribuye el presupuesto de bits por intervalos para controlar la tasa. El scheduler y el equipo pueden alterar el ritmo real; solicitar una tasa no garantiza que pueda producirse con precisión en cualquier host.

### Timestamps de recepción

```sh
netx udp --rate 10M --timestamp userspace 192.168.1.10
netx udp --rate 10M --timestamp kernel --json 192.168.1.10
netx udp --rate 10M --timestamp hardware --json 192.168.1.10
```

- `userspace`: timestamps tomados por el programa; es el modo predeterminado.
- `kernel`: timestamps de recepción software del kernel Linux; requiere soporte y falla si no puede configurarlo.
- `hardware`: solicita timestamps hardware; necesita NIC/driver previamente configurados. netx no configura el NIC y puede observar un fallback.

La recepción UDP ocurre en el servidor. Consulta `timestamp_source` para conocer el origen realmente utilizado; que la API acepte la opción no demuestra que el NIC entregue timestamps hardware.

## Estimar el ancho de banda disponible

```sh
netx available --min-rate 1M --max-rate 100M 192.168.1.10
netx available --min-rate 1M --max-rate 50M --chirps 8 --chirp-packets 12 --chirp-gap 100ms --json 192.168.1.10
```

Un **chirp** es un tren de paquetes con tasas de entrada crecientes. netx compara los intervalos reales de envío y llegada para buscar una expansión sostenida compatible con formación de cola.

| Opción | Qué ajusta |
| --- | --- |
| `--min-rate` / `--max-rate` | Intervalo de tasas de sondeo |
| `--chirps` | Número de trenes de paquetes |
| `--chirp-packets` | Paquetes por tren |
| `--chirp-gap` | Pausa entre trenes |
| `--packet-size` | Tamaño del datagrama netx |

Revisa `estimate_valid`, la estabilidad y el motivo de rechazo. Si no aparece un cruce antes del techo, puede ser necesario elevar `--max-rate` dentro de los límites del pacing. Si la señal es inestable o faltan chirps válidos, no interpretes una cifra como una estimación confirmada. El pacing portable tiene un gap mínimo de 50 µs y rechaza tasas que exijan intervalos menores para el tamaño configurado.

Este modo tampoco certifica capacidad física ni reemplaza la medición de goodput. La [metodología del estimador](docs/PHASE4.md#available-bandwidth-chirp-gap-dispersion-v1) explica sus criterios de aceptación.

## Medir QUIC

```sh
netx quic --direction upload 192.168.1.10
netx quic --direction download 192.168.1.10
netx quic --direction bidir 192.168.1.10
netx quic --streams 4 --duration 15s --json 192.168.1.10
```

QUIC es un transporte sobre UDP con streams y cifrado TLS. netx usa quic-go y conserva un baseline de un stream al solicitar varios. Los streams de un stage comparten la conexión QUIC; cuatro streams QUIC no son cuatro conexiones TCP.

Compara TCP y QUIC con dirección, duración y condiciones equivalentes. El cifrado, la implementación, CPU y buffers pueden influir en el resultado. El control sigue usando TCP y los datos QUIC usan un puerto UDP dinámico. El certificado efímero de esta prueba no verifica la identidad del servidor.

## Probar escenarios de aplicación

### Petición y respuesta

```sh
netx scenario --profile request-response --message-size 1024 --duration 10s 192.168.1.10
```

Mide operaciones de petición/respuesta TCP con mensajes del tamaño indicado. Sirve para estudiar interacción; no ejecuta tu API HTTP ni reproduce su procesamiento interno.

### Mensajes pequeños

```sh
netx scenario --profile small-message --message-size 64 --duration 10s 192.168.1.10
```

Permite observar el comportamiento con payloads pequeños, donde la latencia por operación suele importar más que la tasa bulk.

### Ráfagas

```sh
netx scenario --profile bursty --message-size 1024 --burst-messages 32 --burst-pause 100ms --duration 10s 192.168.1.10
```

Genera grupos de mensajes separados por pausas. Es útil para comparar un patrón intermitente con una transferencia continua.

### Streaming a una tasa objetivo

```sh
netx scenario --profile streaming --message-size 1200 --rate 10M --duration 10s 192.168.1.10
```

Genera frames TCP a una tasa de payload objetivo. No es una simulación completa de un códec de vídeo ni sustituye el test UDP de pérdida y jitter.

## Guardar resultados y automatizar

### JSON: resultado estructurado

```sh
netx throughput --streams 4 --json 192.168.1.10 > tcp.json
netx udp --rate 10M --json 192.168.1.10 > udp.json
netx latency --duration 5s --json 192.168.1.10 > latency.json
netx available --json 192.168.1.10 > available.json
netx quic --json 192.168.1.10 > quic.json
netx scenario --json 192.168.1.10 > scenario.json
netx responsiveness --json 192.168.1.10 > responsiveness.json
netx capabilities --json > capabilities.json
```

Para leer un campo con Python 3, sin instalar un parser adicional:

```sh
python3 -c 'import json; r=json.load(open("tcp.json")); print(r["single_stream"]["upload"]["megabits_per_second"])'
```

Este ejemplo corresponde a una prueba de subida. En una prueba sólo de bajada, usa `download`.

### NDJSON: un objeto JSON por línea

```sh
netx throughput --ndjson 192.168.1.10 > tcp.ndjson
netx udp --rate 10M --ndjson 192.168.1.10 > udp.ndjson
```

NDJSON está disponible en `throughput` y `udp`. Incluye un resumen y registros de muestras de throughput y latencia. La salida se escribe **después de la medición**; no es una transmisión en vivo. Así, una consola o un pipe lento no participa en la ventana medida. `--json` y `--ndjson` son mutuamente excluyentes.

### Repetir una prueba

```sh
mkdir -p resultados
for n in 1 2 3; do
  netx throughput --duration 15s --json 192.168.1.10 > "resultados/tcp-$n.json"
done
```

Guarda también versiones, interfaz usada, conexión cableada o Wi-Fi, distancia/ubicación, hora y carga externa. Compara la dispersión entre ejecuciones; una única cifra puede coincidir con una interferencia o una carga transitoria.

## Colores de la terminal

La salida de texto resalta etiquetas, valores y estados con secuencias ANSI SGR básicas, compatibles con terminales Linux y sesiones SSH habituales de OpenWrt. No usa truecolor, fondos de color, animaciones ni consultas de escape al terminal; no necesita cgo ni nuevos paquetes.

En modo automático, sólo se resalta cuando la salida es una terminal y `TERM` está definido y es distinto de `dumb`. Al guardar en archivos o usar pipes se genera texto plano. JSON y NDJSON siempre permanecen libres de colores, incluso cuando se fuerza el resaltado.

| Variable | Valores | Comportamiento |
| --- | --- | --- |
| `NETX_COLOR` | `auto` (predeterminado), `always`, `never` | Detección automática, forzar resaltado o desactivarlo |
| `NETX_COLOR_THEME` | `auto` (predeterminado), `light`, `dark` | Elegir la paleta según el fondo |
| `NO_COLOR` | Cualquier valor no vacío | Desactivar resaltado; tiene prioridad sobre `always` |
| `COLORFGBG` | La proporciona algunos terminales | Permite inferir fondos convencionales negros o blancos en modo auto |

Para un fondo oscuro, las etiquetas usan cian en negrita y los estados destacados amarillo en negrita; para un fondo claro, azul y rojo estándar. Los valores numéricos mantienen el color de texto del terminal en negrita. Si el fondo es desconocido, se conserva el color de texto del terminal y se usa negrita/subrayado: no se adivina un color que pueda resultar ilegible.

Las paletas ANSI pueden estar personalizadas por el usuario, por lo que no se puede garantizar un contraste universal con colores fijos. El modo auto conservador y `NO_COLOR` permiten respetar esa configuración. El significado sigue presente en el texto; los colores no añaden un diagnóstico ni sustituyen los valores.

```sh
# Si tu terminal no informa del fondo, puedes elegirlo explícitamente
NETX_COLOR_THEME=dark netx capabilities
NETX_COLOR_THEME=light netx throughput 192.168.1.10

# Desactivar todo resaltado
NO_COLOR=1 netx capabilities
NETX_COLOR=never netx throughput 192.168.1.10

# Forzar ANSI, por ejemplo para un paginador que lo interprete
NETX_COLOR=always NETX_COLOR_THEME=dark netx capabilities | less -R
```

`always` también puede añadir escapes a un archivo o pipe de texto si se solicita expresamente. `less` es opcional y puede no estar instalado en OpenWrt. Por SSH, el ejemplo `ssh -t root@192.168.1.1 '/tmp/netx capabilities'` asigna una terminal; sin `-t`, la salida permanece plana en modo auto. SSH no necesariamente transmite `COLORFGBG`: puedes establecer `NETX_COLOR_THEME` en el equipo remoto.

## Diagnóstico y opciones del host

### Telemetría TCP

```sh
netx throughput --json 192.168.1.10
netx throughput --diagnostics=false 192.168.1.10
```

En Linux, el diagnóstico puede recoger TCP_INFO, algoritmo de congestión, RTT TCP, retransmisiones, ventana y límites de buffers, además de CPU/RSS/PSI. Los findings sólo aparecen cuando una regla cruza sus umbrales y muestran evidencia. Un diagnóstico vacío significa que esas reglas no encontraron evidencia suficiente; no demuestra que la red carezca de problemas.

El RTT de TCP_INFO y el RTT del eco de aplicación son mediciones distintas. Consulta las [reglas de diagnóstico](docs/DIAGNOSTICS.md) antes de atribuir una causa.

### Comparar algoritmos de congestión TCP

```sh
netx throughput --cc cubic 192.168.1.10
netx cc-compare --algorithms cubic,bbr,reno --duration 10s --json 192.168.1.10
```

El algoritmo debe estar disponible y permitido por el sistema de los extremos. La comparación conserva errores y `supported=false` cuando no puede usar uno; no instala algoritmos ni selecciona un ganador automáticamente.

### Capacidades, CPU y NUMA

```sh
netx capabilities
netx capabilities --json
```

La salida distingue capacidades de API del soporte observado del hardware. GSO/GRO detectados no implican que se activen en la prueba, y AF_XDP permanece deshabilitado.

**AF_XDP** es una API Linux que permite intercambiar paquetes con programas XDP mediante buffers compartidos, como alternativa a los sockets de red convencionales. Esta versión de netx no implementa ese backend: `af_xdp=false` describe la implementación de netx, no una prueba de que tu tarjeta sea incompatible. Las mediciones usan sockets estándar; AF_XDP no es un requisito para ejecutar TCP, UDP o QUIC.

Se evaluó como optimización y se dejó fuera deliberadamente: su incorporación requiere benchmarks que demuestren que los sockets convencionales son el cuello de botella y que el cambio mejora el rendimiento sin alterar la medición.

Del mismo modo, `hw_ts_api=true` indica que la API de timestamping hardware está disponible. No certifica soporte de la tarjeta ni que se hayan recibido timestamps hardware. Consulta `timestamp_source` en una prueba UDP para ver el origen utilizado.

Si necesitas controlar la afinidad, elige una CPU o un nodo NUMA que exista y esté permitido para el proceso:

```sh
netx server --listen 192.168.1.10 --cpu 2
netx throughput --cpu 2 192.168.1.10
netx throughput --numa-node 0 192.168.1.10
```

Los ejemplos de servidor y cliente se ejecutan en equipos distintos. Estas opciones son optativas y Linux específicas. Fijar una CPU puede reducir el rendimiento si concentra demasiada carga; mide antes y después.

## OpenWrt y servicio procd/UCI

Para ejecutar bajo demanda un binario ya copiado al router:

```sh
/tmp/netx capabilities
/tmp/netx server --listen 192.168.1.1
```

Desde un ordenador de la LAN:

```sh
netx throughput 192.168.1.1
netx throughput --direction download 192.168.1.1
```

El router participa como extremo de la prueba: su CPU puede limitar el resultado. Para estudiar el reenvío a través del router, coloca servidor y cliente en equipos a ambos lados y mide la ruta que lo atraviesa.

### Preparar un paquete para el SDK

Desde la carpeta del código netx, en el equipo de compilación:

```sh
make openwrt-feed VERSION=0.5.0 OPENWRT_ARCH=arm64
```

El target genera los binarios de release y prepara `dist/openwrt-feed/netx`. No instala directamente un paquete en el router. Copia esa carpeta al SDK OpenWrt compatible con tu dispositivo, como `package/netx`, y desde la raíz del SDK ejecuta:

```sh
make package/netx/compile V=s
```

Tras instalar el paquete generado en el router, la configuración UCI mantiene el daemon deshabilitado por defecto. Para habilitarlo, ajusta primero la dirección de escucha a la del router:

```sh
uci set netx.main.listen='192.168.1.1'
uci set netx.main.enabled='1'
uci commit netx
/etc/init.d/netx enable
/etc/init.d/netx start
```

Para detenerlo y deshabilitarlo:

```sh
/etc/init.d/netx stop
/etc/init.d/netx disable
uci set netx.main.enabled='0'
uci commit netx
```

## Interpretar los resultados

Los siguientes casos son ejemplos didácticos, no benchmarks de este repositorio:

| Resultado observado | Cómo leerlo | Siguiente comprobación |
| --- | --- | --- |
| TCP 90 Mbit/s en una ruta con un enlace de 100 Mbit/s | Goodput inferior a la tasa nominal; hay overhead y posibles límites adicionales | Repetir y comprobar negociación, ruta y carga |
| Un flujo 150 Mbit/s; cuatro 500 Mbit/s | Varios flujos obtuvieron más goodput en esas condiciones | Revisar telemetría de ventanas, CPU y retransmisiones |
| RTT idle p95 3 ms; loaded p95 180 ms | La interacción empeoró mucho bajo carga | Comparar subida/bajada y estudiar las colas de la ruta |
| UDP objetivo 10 Mbit/s, datagrama 1200; payload 9.7 Mbit/s y pérdida 0% | Compatible con el descuento de la cabecera netx | No atribuir el descuento del payload a pérdida |
| UDP pierde paquetes al elevar la tasa | Algún punto no sostiene el tráfico solicitado | Reducir tasa y revisar receptor, buffers, CPU y ruta |
| `estimate_valid=false` en `available` | No hay una estimación aceptada por el método | Leer rechazo, chirps y límites de sondeo |
| Loopback 40 Gbit/s | Medición local del host, sin atravesar un enlace físico | Repetir entre dos equipos |

Para comparar Ethernet con Wi-Fi o una VPN, cambia una variable cada vez, mantén dirección/duración/flujos y realiza varias ejecuciones. No compares un test de un flujo con otro de cuatro como si usaran la misma metodología.

## Resolver problemas

| Síntoma | Comprobaciones |
| --- | --- |
| `netx: command not found` | Usa `./bin/netx` o añade el directorio de instalación al PATH |
| `connection refused` | Revisa servidor en ejecución, IP de escucha y puerto de control |
| Timeout al conectar | Comprueba ruta, VPN/firewall, dirección y `--port`; revisa `--dial-timeout` |
| Conecta pero la transferencia no termina | Permite también los puertos dinámicos de datos TCP/UDP; revisa NAT y logs del servidor |
| `address already in use` | Otro proceso ocupa el puerto; elige otro `--port` en ambos extremos |
| Las opciones parecen ignoradas o aparece error de uso | Coloca todas las opciones antes de la dirección del servidor |
| Goodput UDP menor que `--rate` sin pérdida | Descuenta la cabecera netx y comprueba el ritmo real del emisor |
| QUIC advierte sobre el receive buffer UDP | Revisa los límites de buffers del sistema; la prueba puede funcionar con rendimiento limitado |
| `--timestamp kernel` no funciona | Comprueba soporte del kernel del servidor; prueba `userspace` |
| `--timestamp hardware` devuelve fallback | Configura y verifica soporte NIC/driver; consulta el origen realmente observado |
| Afinidad falla | Comprueba CPU/nodo existentes y restricciones del proceso o contenedor |
| Un algoritmo TCP no está soportado | Comprueba disponibilidad y permisos en ambos extremos |
| Un binario no arranca en OpenWrt | Comprueba arquitectura, permisos de ejecución y compatibilidad del artefacto |
| `make race` pide un compilador C | El detector requiere cgo/toolchain C; el binario normal se compila sin cgo |
| `make netem-check` devuelve SKIP | Faltan herramientas o permisos para namespaces/tc; no equivale a una simulación validada |

Puedes consultar las opciones con `netx COMANDO -h`, por ejemplo `netx udp -h`. La implementación actual puede devolver código de salida 1 al mostrar esa ayuda; consulta su texto. Para la lista general, usa `netx help`.

Vuelve al [README](README.md) para instalación, funcionalidades y licencia MIT.
