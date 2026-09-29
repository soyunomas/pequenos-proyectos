# Fase 5 — fast path, OpenWrt y release

## Criterio de optimización

Fase 5 no activa una optimización sólo porque exista una syscall o una API del kernel. La regla es:

1. perfilar el baseline;
2. medir un candidato reproducible;
3. comprobar que no cambia la magnitud ni la semántica temporal;
4. activar sólo si el beneficio es material en el hardware objetivo.

`make profile-fastpath` genera perfiles CPU/memoria para el writer UDP portable y el candidato batch. `make fastpath-bench` compara `UDPConn.Write` con un batch de 16 datagramas mediante `x/net/ipv4.WriteBatch`, que en Linux usa el camino batch del kernel.

Ese benchmark es evidencia de coste de envío, **no** una medida de capacidad de red.

## Batching, GSO y GRO

`netx capabilities` prueba en tiempo de ejecución:

- disponibilidad del API de batching en Linux;
- `UDP_SEGMENT` (GSO);
- `UDP_GRO`;
- timestamping software de kernel;
- API de timestamping hardware.

El bulk UDP de medición conserva en 0.5 el writer portable. La razón es metodológica: agrupar datagramas modifica el instante en que el userspace puede atribuir cada envío y, por tanto, puede alterar jitter y pacing si se integra sin TX timestamping por paquete. El candidato queda benchmarkeado y aislado; no se cambia una métrica para ganar una cifra de throughput.

QUIC sigue delegando sus optimizaciones de UDP a `quic-go`. NetX no presenta esas optimizaciones internas como una característica propia ni asume que estén activas en todas las plataformas.

## Afinidad CPU y NUMA

Los comandos que generan o reciben carga aceptan:

```text
--cpu N
--numa-node N
```

Son mutuamente excluyentes. En Linux NetX aplica la máscara a los threads ya existentes del proceso. La afinidad es opcional y está desactivada por defecto.

`--numa-node` lee `/sys/devices/system/node/nodeN/cpulist`. No realiza memory binding y no pretende sustituir una política NUMA completa. En routers OpenWrt de un solo nodo normalmente no aporta nada.

## Timestamping UDP

`netx udp` acepta:

```text
--timestamp userspace
--timestamp kernel
--timestamp hardware
```

- `userspace`: reloj tomado después de la recepción en Go.
- `kernel`: `SO_TIMESTAMPNS` / `SCM_TIMESTAMPNS`.
- `hardware`: solicita `SO_TIMESTAMPING` con RX hardware + raw hardware y conserva fallback software.

El modo hardware **no configura el NIC** mediante `SIOCSHWTSTAMP`. El administrador debe habilitar el timestamping del driver/NIC previamente. El resultado indica la fuente realmente observada: `kernel-hardware`, `kernel-software-fallback` o un fallback userspace. La existencia del API de socket no se etiqueta como soporte hardware del NIC.

## AF_XDP

AF_XDP se evaluó como siguiente backend posible y queda desactivado en 0.5. La razón se publica en `netx capabilities`: no existe todavía evidencia en la matriz disponible de que sockets normales sean el cuello de botella del producto.

Esto es deliberado. Añadir AF_XDP antes de demostrar el límite introduciría complejidad de UMEM, colas, afinidad y drivers sin una mejora demostrada, y degradaría la portabilidad OpenWrt.

## OpenWrt

La release genera binarios puros Go para diez variantes Linux/OpenWrt. Para crear un paquete instalable con el SDK:

```sh
make release VERSION=0.5.0
make openwrt-feed OPENWRT_ARCH=arm64 VERSION=0.5.0
cp -a dist/openwrt-feed/netx /ruta/openwrt-sdk/package/netx
cd /ruta/openwrt-sdk
make package/netx/compile V=s
```

El servicio procd queda **deshabilitado por defecto** porque el servidor de medición no debe exponerse automáticamente. Se habilita con UCI sólo cuando el operador lo decide.

## Release reproducible

`make release`:

- usa `CGO_ENABLED=0`;
- fija `-trimpath`, `-buildvcs=false` y `-buildid=`;
- inyecta versión/commit explícitos;
- genera la matriz de diez binarios;
- genera `SHA256SUMS` y `BUILDINFO`.

`make release-repro` construye la matriz dos veces en directorios distintos y exige igualdad byte a byte.

El workflow `.github/workflows/netx-release.yml` publica los artefactos cuando se empuja un tag `netx-v*`.

## Matriz de velocidad

La metodología preparada cubre 1 / 2.5 / 10 / 25 / 40 / 100 GbE. No se publican resultados inventados: cada fila exige hardware real, driver, NIC, MTU, CPU, kernel, congestion control, número de streams y evidencia de CPU/syscalls.

Los resultados disponibles se registran en `benchmarks/README.md`. Una plataforma no medida aparece como `not measured`, no como una extrapolación.

## Gate de Fase 5

```sh
make phase5-check
```

Incluye formato/vet/tests, race, smoke, netem cuando sea posible, diagnóstico, metodología de Fase 4, benchmark fast-path, reproducibilidad de release, receta OpenWrt y la matriz de cross-build.
