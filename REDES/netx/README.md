# netx

**Mide el rendimiento de tu red entre dos equipos y comprueba cómo responde mientras transfieres datos.** netx es una herramienta de línea de comandos escrita en Go, orientada a Linux y OpenWrt. Usa un servidor netx en un extremo y un cliente en el otro.

## Funcionalidades

- **Transferencias TCP y QUIC:** subida, bajada y tráfico bidireccional.
- **Uno o varios flujos:** conserva la medición de un solo flujo y permite compararla con flujos paralelos o aumentar su número de forma adaptativa en TCP.
- **UDP con tasa configurable:** mide datos útiles recibidos, pérdida, reordenamiento y jitter.
- **Latencia en reposo y bajo carga:** muestra percentiles de RTT para observar si una transferencia perjudica la respuesta de la red.
- **Estimación del ancho de banda disponible:** usa sondas UDP y declara cuándo la señal no permite obtener una estimación válida.
- **Escenarios de aplicación:** petición/respuesta, mensajes pequeños, ráfagas y streaming.
- **Diagnóstico TCP en Linux:** telemetría de conexión, retransmisiones, CPU y reglas de diagnóstico con evidencia numérica.
- **Resultados para automatización:** JSON y, en TCP/UDP, NDJSON con muestras de la medición.
- **Binario estático:** compilación sin cgo, opciones de afinidad CPU/NUMA, timestamping UDP y compilación cruzada para distintas arquitecturas de OpenWrt.

Consulta el **[HOWTO: guía de uso, ejemplos y conceptos](HOWTO.md)** para aprender qué mide cada modo y cómo interpretar sus resultados.

## Qué necesitas

- Dos equipos que puedan comunicarse por IP; uno actúa como servidor y el otro como cliente.
- Un binario netx compatible con cada equipo. Para compilarlo: **Go 1.23 o posterior**, Git y, para los comandos `make`, GNU Make.
- Acceso a las dependencias Go durante la compilación. El binario resultante no necesita Go instalado para ejecutarse.

Puedes probar ambos extremos en el mismo equipo usando `127.0.0.1`. Esto comprueba el funcionamiento local; para medir Ethernet, Wi-Fi, una VPN o una ruta por Internet, sitúa los extremos a ambos lados de esa ruta.

netx no selecciona servidores públicos de speed test: necesitas ejecutar tu propio servidor netx.

## Instalación

### Compilar desde el código fuente

```sh
git clone https://github.com/soyunomas/pequenos-proyectos.git
cd pequenos-proyectos/REDES/netx
make build
./bin/netx version
./bin/netx help
```

El ejecutable se genera en `bin/netx`. El target `build` usa `CGO_ENABLED=0` por defecto. Si no tienes Make, puedes compilar desde esa misma carpeta:

```sh
CGO_ENABLED=0 go build -trimpath -o bin/netx ./cmd/netx
```

Para instalarlo en tu usuario en Linux:

```sh
mkdir -p "$HOME/.local/bin"
install -m 0755 bin/netx "$HOME/.local/bin/netx"
export PATH="$HOME/.local/bin:$PATH"
netx version
```

Añade esa línea `export PATH=...` al archivo de inicio de tu shell si quieres conservarla en nuevas terminales. También puedes usar siempre `./bin/netx` sin instalarlo.

### Compilar para OpenWrt

Compila en tu ordenador y copia el ejecutable correspondiente a la arquitectura del router. Por ejemplo, para ARM64:

```sh
make openwrt-arm64
scp dist/netx-linux-arm64 root@192.168.1.1:/tmp/netx
ssh root@192.168.1.1
chmod +x /tmp/netx
/tmp/netx version
```

Sustituye la dirección y la arquitectura por las de tu router. `/tmp` es temporal y se pierde al reiniciar. `make openwrt` genera toda la matriz: amd64, 386, ARM v5/v6/v7, ARM64, MIPS/MIPSLE/MIPS64 soft-float y RISC-V 64. La [guía de compatibilidad](docs/COMPATIBILITY.md) detalla los artefactos y las capacidades opcionales.

Para integrar netx como paquete procd/UCI mediante el SDK de OpenWrt, consulta el [HOWTO](HOWTO.md#openwrt-y-servicio-procduci).

## Primera medición entre dos equipos

Supongamos que el servidor tiene la dirección LAN `192.168.1.10`. En ese equipo:

```sh
netx server --listen 192.168.1.10
```

En el otro equipo:

```sh
# Cliente -> servidor: subida
netx throughput 192.168.1.10

# Servidor -> cliente: bajada
netx throughput --direction download 192.168.1.10

# RTT sin una transferencia de carga generada por netx
netx latency 192.168.1.10
```

Sustituye la IP del ejemplo por la de tu servidor. Las opciones se escriben **antes de la dirección del servidor**. Si no instalaste el ejecutable, sustituye `netx` por `./bin/netx`.

Una prueba TCP usa por defecto una ventana de medición de 10 segundos y 2 segundos de calentamiento; el tiempo total incluye también sondas de latencia y preparación. Para detener el servidor, pulsa `Ctrl+C`.

### Conectividad y acceso

El puerto de control predeterminado es **TCP 5202**. Los canales de datos negocian puertos dinámicos: TCP para transferencias y escenarios TCP; UDP para las pruebas UDP, el estimador y QUIC. Abrir únicamente el puerto de control puede permitir conectar pero impedir completar la prueba.

En redes con firewall o NAT, permite los canales necesarios entre los equipos de prueba o usa una VPN que les dé conectividad directa. `--port` cambia el puerto de control, no fija los puertos de datos. Por defecto, `netx server` escucha en `0.0.0.0`; usa `--listen` para elegir la dirección de escucha.

El servidor está pensado para entornos de prueba controlados: el canal de control no autentica al usuario ni cifra sus mensajes. QUIC usa un certificado efímero autofirmado cuya identidad no valida el cliente. No publiques el servidor como un servicio abierto a Internet.

## Elegir la prueba adecuada

| Quiero saber… | Comando de partida |
| --- | --- |
| Cuánto dato útil transfiere una conexión TCP | `netx throughput HOST` |
| Qué cambia al usar cuatro flujos TCP | `netx throughput --streams 4 HOST` |
| Cómo se comportan subida y bajada simultáneas | `netx throughput --direction bidir HOST` |
| Si una tasa UDP provoca pérdida o jitter | `netx udp --rate 10M HOST` |
| Cuánto tarda una interacción de ida y vuelta | `netx latency HOST` |
| Cómo responde la red durante una transferencia | `netx responsiveness HOST` |
| Qué ancho de banda podría estar disponible ahora | `netx available --min-rate 1M --max-rate 100M HOST` |
| Cuánto dato útil transfiere QUIC | `netx quic HOST` |
| Cómo funciona un patrón de petición/respuesta | `netx scenario --profile request-response HOST` |
| Qué capacidades detecta netx en este equipo | `netx capabilities` |

`HOST` representa la IP o el nombre de tu servidor. En el [HOWTO](HOWTO.md) encontrarás ejemplos completos, opciones, definiciones y resolución de problemas.

## Interpretar la medición

El throughput de TCP/QUIC representa **goodput**, es decir, datos útiles entregados durante la ventana medida. No certifica la capacidad física del enlace. Una conexión Ethernet de 1 Gbit/s puede entregar menos datos útiles por las cabeceras, el transporte o los límites del equipo.

Un resultado con varios flujos conserva el baseline de un flujo. En bidireccional, subida y bajada se presentan por separado. En UDP, `--rate` cuenta el datagrama netx, incluida su cabecera, mientras que el goodput cuenta el payload útil.

La latencia bajo carga ayuda a interpretar el rendimiento: transferir muchos datos con un p95 de RTT muy elevado puede perjudicar una llamada o una sesión interactiva. El estimador `available` puede abstenerse cuando faltan muestras válidas o la señal es inestable. El RPM de `responsiveness` es una aproximación y lleva `draft_conformant=false`.

Consulta las [definiciones y unidades](HOWTO.md#conceptos-y-unidades) y los [ejemplos de interpretación](HOWTO.md#interpretar-los-resultados).

## Desarrollo y documentación técnica

```sh
make help          # Targets disponibles
make check         # Formato, análisis estático y pruebas unitarias
make race          # Detector de condiciones de carrera; necesita cgo/toolchain C
make smoke         # Pruebas cliente/servidor en loopback
make netem-check   # Simulación Linux; SKIP si faltan permisos o herramientas
```

- [HOWTO](HOWTO.md): instalación operativa, recetas, conceptos y problemas frecuentes.
- [Compatibilidad](docs/COMPATIBILITY.md): arquitecturas y requisitos de las capacidades opcionales.
- [Diagnóstico](docs/DIAGNOSTICS.md): reglas y evidencias de las limitaciones detectadas.
- [Especificación](docs/SPEC.md): diseño y metodología de medición.
- [Metodología de los modos avanzados](docs/PHASE4.md): estimador, QUIC, escenarios y responsividad.
- [Capacidades y empaquetado](docs/PHASE5.md): afinidad, timestamping y generación de artefactos.
- [Benchmarks](benchmarks/README.md): medición del rendimiento de la implementación.

## Licencia

netx se distribuye bajo la **[licencia MIT](LICENSE)**. Las dependencias de terceros conservan sus propias licencias.
