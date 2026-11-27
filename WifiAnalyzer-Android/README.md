# NetScope — Wi-Fi y diagnóstico de redes (Android)

Suite Android nativa para estudiar redes Wi-Fi próximas y diagnosticar una conexión. **Sin cuenta, telemetría ni rastreadores.** Código independiente de aplicaciones comerciales.

## Funciones implementadas en v4.1

- Exploración pasiva de redes: SSID, BSSID, RSSI, canal, banda, seguridad, ancho y estándar.
- Gráficos de ocupación estimada por bandas 2,4 / 5 / 6 GHz y puntuación orientativa de canales.
- Historial de señal de sesión con deduplicación por timestamp de escaneo Android (hasta 100 muestras por punto de acceso).
- Auditoría pasiva de seguridad: red abierta, WEP, WPA, WPA2, WPA3, OWE y Enterprise, con recomendaciones.
- Centro de diagnóstico: interfaz, IP locales, rutas/gateway, DNS, DNS privado, MTU, velocidad de enlace y validación Android.
- Prueba DNS voluntaria contra example.com, y comprobación HTTPS voluntaria contra www.google.com/generate_204 (sobre la red Wi-Fi).
- Calculadora IPv4/CIDR con /0, /31 y /32 y rango de hosts.
- Exportación CSV de redes, CSV de historial, JSON de redes y JSON con SSID/BSSID anonimizados.
- Búsqueda por SSID/BSSID/seguridad; favoritos locales y filtro de favoritos.
- Escaneo automático opcional, con posibilidad de pausar y seguir explorando manualmente.
- Interfaz técnica blanca/azul con navegación inferior protegida de las barras Android. Android 8.0+.

## Gráficas Wi-Fi con identificación inequívoca (v3.3)

- Cada BSSID recibe **un color ARGB único durante la sesión**, incluso si hay más puntos de acceso que los doce colores de la paleta inicial.
- La leyenda muestra un **número identificador** además de SSID, BSSID, canal y RSSI. Las curvas alternan entre trazos continuos, discontinuos y punteados para identificarse en zonas congestionadas.
- La asignación de color se conserva entre escaneos y cuando Android recrea la actividad; los BSSID que desaparecen no transfieren su color a otra red durante la sesión.
- Selecciona una fila de la leyenda para resaltar la curva correspondiente y atenuar las restantes. Los colores distinguen redes, **no son un indicador de calidad o seguridad**.
- Pruebas JVM verifican 80 BSSID diferentes sin colores duplicados, asignación estable y restauración en el mismo orden.

## NetScope 4.1 — mapa de cobertura Wi-Fi local, sin extrapolación falsa

- **Corrección de raíz:** antes se coloreaba todo el plano mediante interpolación IDW global aunque solo existieran tres lecturas; esto interpretaba áreas sin medir como si tuvieran buena cobertura.
- El nuevo algoritmo \`CoverageSurface.estimate\` interpola **solo dentro de un radio delimitado de cada observación**. Fuera de esos radios se muestra el plano sin color: **SIN DATOS**. No se inventan estimaciones de cobertura, ni siquiera cuando tres muestras son iguales y fuertes.
- La distancia se calcula respetando el aspecto del plano. El botón **Radio** alterna entre corto, medio y amplio (9, 15 y 21 % del lado corto); es una distancia en el **plano digital**, no en metros ni un modelo físico de propagación.
- Los colores responden a un gradiente continuo de RSSI, con escala visible: aproximadamente rojo ≤ −90 dBm, ámbar alrededor de −78, amarillo alrededor de −65 y verdes para señales mejores, sin transformar estos valores en calidad garantizada.
- Cada punto indica su RSSI numérico. Con valores casi idénticos el mapa avisa de que **no hay contraste medido**; la visualización no puede descubrir problemas inexistentes en la muestra.
- Se fija el **BSSID real del primer punto** y se rechazan lecturas de otro AP: medir solo el SSID puede mezclar distintos radios bajo una red con repetidores o mesh.
- Existe una separación mínima de cuatro segundos entre puntos. Es un límite contra capturas accidentales, no una garantía de que Android haya actualizado el RSSI. Camina hasta cada ubicación y espera a que se estabilice la conexión.
- Controles **Deshacer**, **Reiniciar**, **Radio** y exportación PNG; observaciones locales, sin envío a servidores. Los colores representan observaciones e interpolación local, nunca una medición de espectro ni de la atenuación por paredes.
- Pruebas JVM verifican que tres muestras intensas no coloreen áreas lejanas, que las ubicaciones medidas conserven sus lecturas, que el radio respete la relación de aspecto y que el gradiente sea continuo.

**Cómo medir de forma útil:** importa un plano a escala, camina físicamente a 10–20 puntos representativos de la vivienda y toca la ubicación correspondiente. Busca habitaciones con RSSI por debajo de −75 dBm, observa el punto de acceso fijado y compara las zonas de los distintos AP en mediciones separadas. Evita interpretar el silencio de datos como mala cobertura.

## NetScope 4.0 — 20 herramientas adicionales sin root

Se amplía **Suite experta** de 25 a 45 herramientas. Todas las pruebas son voluntarias, se ejecutan sin privilegios root y quedan limitadas por tiempo o tamaño. La interfaz mantiene los estilos B + D.

| ID | Función | Alcance real |
|---|---|---|
| 26 | Equipos fuera de subred | SSDP, mDNS y WS-Discovery con respuestas observadas; no genera ARP ni garantiza hallar IPs desconocidas |
| 27 | Dos móviles | Servidor y cliente TCP 5202 / UDP 5203, 4 MiB TCP, hasta 80 datagramas UDP; requiere que dos teléfonos estén en la misma red sin aislamiento |
| 28 | Roaming Wi-Fi | Sesión de 120 segundos; cambios BSSID, RSSI y marcas de tiempo |
| 29 | Mapa de cobertura | Plano local importado, puntos RSSI manuales, interpolación tras 3 muestras y exportación PNG |
| 30 | Wi-Fi RTT | API WifiRttManager 802.11mc para hardware/routers compatibles y ubicación autorizada |
| 31 | Cambios de red | Registro de IP, BSSID, DNS, gateway e interfaz durante 120 s |
| 32 | Portal cautivo | Capability Android y GET HTTP 204 sin seguir redirecciones |
| 33 | Split DNS | Compara respuestas A/AAAA del DNS Wi-Fi y resolutores públicos |
| 34 | DNSSEC | Consulta con DO al validador 1.1.1.1 y compara AD/RCODE; **no verifica firmas de forma independiente** |
| 35 | EDNS/TCP | Consulta OPT con DO y TCP DNS real, informe TC y RCODE |
| 36 | NAT STUN | STUN Binding y dirección/puerto reflejados, con validación de transaction ID |
| 37 | UPnP IGD | Descubre IGD y consulta WAN IP vía SOAP **solo lectura**; control URL limitada al gateway anunciante |
| 38 | PCP/NAT-PMP | PCP ANNOUNCE y consulta de dirección NAT-PMP, **sin configurar mapeos** |
| 39 | UDP | Envía máximo 512 bytes, recibe máximo 2 KiB; silencio no significa puerto cerrado |
| 40 | IPP | Get-Printer-Attributes de lectura a impresora de red; no imprime |
| 41 | ONVIF | WS-Discovery (Probe) y XAddrs anunciados; no pide credenciales |
| 42 | SMB | Negociación SMB2 y dialecto; **la enumeración de recursos compartidos requiere sesión y está pendiente** |
| 43 | SSH | Banner y algoritmos KEXINIT; no inicia sesión ni autentica clave host |
| 44 | WebSocket | Cliente ws/wss con handshake RFC6455, mensaje enmascarado y lectura acotada |
| 45 | HTTP/2 / HTTP/3 | Negociación HTTP/2 por ALPN y anuncio HTTP/3 por Alt-Svc. **No negocia QUIC/HTTP3 real sin motor adicional** |

### Seguridad y privacidad

- El escaneo de anuncios multicast se limita al dominio de broadcast actual. Sin root no equivale a arp-scan de capa 2.
- Para IPP, SMB, SSH, IGD y UDP personalizado se aceptan destinos LAN RFC1918 y RFC6598; las direcciones públicas no se examinan de forma accidental.
- Las sesiones de roaming, cambio de red y tests entre móviles se detienen al salir; no mantienen servicios invisibles.
- El mapa usa únicamente una imagen seleccionada por el usuario y el RSSI obtenido por Android, con exportación voluntaria local.
- STUN, DNS público y HTTP pueden contactar servicios de terceros; estos observarán la IP pública y el destino solicitado. El cliente no envía SSID, BSSID ni lista de Wi-Fi a dichos servicios por iniciativa propia.
- HTTP/3, DNSSEC con validación criptográfica y exploración SMB de recursos autenticados **no deben considerarse terminados**. Necesitan motores completos, pruebas específicas y posiblemente dependencias externas.
- El acceso a dispositivos de la LAN puede quedar restringido por aislamiento Wi-Fi, cortafuegos o permisos de Android; validar en hardware real.

## Compatibilidad con LAN 100.64.0.0/10 y subredes /22 (v3.2)

- El escáner admite ahora **RFC6598 (100.64.0.0/10)** además de las redes RFC1918. No autoriza automáticamente 100.0.0.0/8, pues fuera de 100.64.0.0/10 muchas direcciones son públicas.
- Usa el prefijo DHCP/Android real para explorar la **subred /22 completa** (hasta 1022 direcciones útiles), también /23, /24 y prefijos más estrechos. Para redes superiores a /22 limita la exploración al /22 donde está el teléfono, indicado en pantalla.
- Las pruebas JVM validan los límites 100.64–100.127, octetos inválidos, los 1021 destinos de una /22 excluyendo la IP propia y el alcance de /20, /23 y /24.
- Se conserva el vínculo del escaneo al transporte Wi-Fi y la exploración TCP paralela. En redes con aislamiento AP/clientes, VPN/CGNAT a nivel operador o filtros de puertos, algunos dispositivos no serán detectables.
- Esta compatibilidad requiere **una IP Wi-Fi en ese rango**, no una IP de CGNAT pública del operador ni una dirección de otra interfaz distinta de Wi-Fi.

## Análisis de un equipo y herramientas de experto (v2.6)

- Pulsa un dispositivo para abrir su ficha individual. La primera visita pregunta si deseas escanear puertos; acciones **Rápido**, **Ampliado** y **Puerto personalizado**.
- Sondeo TCP bajo demanda en la Wi-Fi actual, limitado a una IP privada del segmento detectado, con 16 conexiones paralelas, 380 ms por conexión, máximo de 80 puertos y 18 segundos por operación. Resultados progresivos y cancelación.
- Abiertos: puerto y latencia de conexión, identificación orientativa de servicio, detalle de respuesta, lectura de banner TCP, petición `HEAD` sin TLS, o texto manual enviado como socket TCP (`nc` básico).
- Para HTTP/HTTPS, apertura mediante navegador externo del teléfono. Certificados HTTPS autofirmados pueden ocasionar avisos del navegador.
- Respuestas truncadas a 2048 bytes; bytes de control escapados. Envíos de texto explícitos y limitados a 512 caracteres. Sin autenticaciones, ataques ni almacenamiento de credenciales.
- Los nombres asociados a puertos son convenciones, no detección probada de protocolo o versiones.
- Pictogramas vectoriales propios para router, móvil, PC/NAS, impresora, TV, servidor y domótica.
- Las barras inferiores reservan explícitamente el área de navegación y gestos de Android mediante `WindowInsets`, en lugar de solo añadir un margen arbitrario.
- Se requiere prueba visual en el móvil para confirmar que no hay solapamientos específicos del fabricante.

## NetScope 3.1 — 25 herramientas y gráfica identificada

- Gráficas Wi-Fi por banda con **leyenda interactiva por SSID y BSSID**, colores deterministas y selección para destacar una curva; curva aproximada a partir de RSSI y ancho de canal. El eje X muestra frecuencia en MHz, no se confunden canales con frecuencias.
- Acceso «25 herramientas de red» desde el panel principal y el Centro de red.
- Diagnóstico: **ping/alcanzabilidad Android**, 10 muestras con variación media de tiempos, traceroute TTL si el dispositivo admite ping `-t`, MTR nativo o alternativa PathPing experimental por TTL, monitorización TCP, IPv6, MTU/sonda DF cuando el binario lo permite, NTP y DHCP.
- DNS: A, AAAA, MX, TXT, NS, CNAME, SOA, SRV, CAA, PTR inverso; comparador de DNS público por UDP, DoH y DoT (dependiendo de las restricciones de red).
- Servicios: inspección TLS con verificación de certificados y SAN; cliente HTTP GET, HEAD u OPTIONS (prefijo del campo URL), cabeceras y hasta 2 KiB de respuesta GET.
- LAN: mDNS/DNS-SD por multicast, SSDP/UPnP, consulta SNMP v1 de solo lectura de `sysDescr.0` con comunidad pública, Wake-on-LAN y un inventario de equipos con comparación local entre sesiones.
- Rendimiento: prueba HTTPS de hasta 3 MB; subida corta solo si se usa el servidor de Cloudflare. **iPerf3** ejecuta un binario nativo compatible si ya existe en el sistema; si no, solo verifica disponibilidad TCP y muestra las instrucciones, **sin fingir una medición**.
- Internet: RDAP, RIPEstat ASN/BGP, IP pública Wi-Fi, VPN y guía para investigar CGNAT.
- Informe técnico exportable desde el selector de documentos Android, con hasta 30 resultados realizados en esta sesión.

### Advertencias y límites

Todas las acciones de red se inician al pulsar «Ejecutar». Algunas consultan servicios públicos externos y revelan al destino la IP pública o el nombre consultado (DNS público, rdap.org, RIPE NCC, ipify, Cloudflare y otros servicios elegidos).
`InetAddress.isReachable` no garantiza ICMP: puede depender de TCP; los tiempos no se deben etiquetar como RTT ICMP auténtico. Traceroute, MTR nativo y Path MTU completos pueden no ser ejecutables sin binarios nativos o permisos. La disponibilidad TCP de iperf3 **no equivale** a la prueba iPerf3.
Se habilitan conexiones HTTP sin cifrar **únicamente para permitir diagnósticos voluntarios de servicios HTTP**; las consultas de proveedores externos utilizan HTTPS salvo DNS UDP explícito. No se introducen contraseñas, no se realizan ataques ni se hacen consultas automáticas.
El inventario almacena IP y MAC observadas localmente; las demás acciones se mantienen en el historial de esta sesión. Resultados de servicios y DNS pueden estar afectados por bloqueos, DNS privado y VPN. Hay que probar en teléfonos físicos.

## Escaneo LAN rápido (v2.3)

- Exploración rápida iniciada al abrir la sección LAN; sondeo TCP de tres puertos por IP con 48 tareas simultáneas y esperas cortas, sin hacer siete intentos lentos por host.
- Los resultados se actualizan **en directo**, sin esperar a que terminen todas las direcciones. El gateway y la IP del teléfono aparecen primero.
- Descubrimiento adicional SSDP/UPnP para equipos que responden a anuncios de servicios de red.
- Modo **Más completo** con siete puertos y más tiempo de espera para ampliar detección, y botón Detener.
- El tiempo real depende de la latencia del router, filtrado de puertos, aislamiento Wi-Fi y restricciones del fabricante. No garantiza identificar todos los dispositivos ni obtener sus MAC.
- La base IEEE se carga fuera del hilo principal para no bloquear la interfaz.

## Descubrimiento LAN / ARP y fabricantes

- Botón principal **LAN / ARP** de acceso inmediato; exploración TCP iniciada al abrir la sección sobre la subred Wi-Fi privada, limitada a un máximo de 254 direcciones.
- La sección lista IP, estado, gateway y MAC **solo cuando Android deja consultar ARP**. En Android 10+ el acceso a `/proc/net/arp` suele estar bloqueado: estas MAC aparecen como «No disponible». **No es un escaneo ARP de tramas raw** y no sustituye `arp-scan` de Linux con privilegios.
- Fabricantes de puntos de acceso Wi-Fi mediante BSSID anunciado (MAC real) y fabricante de LAN cuando haya MAC disponible.
- El índice OUI se genera antes de compilar desde los registros públicos IEEE MA-L, MA-M y MA-S; se integra offline en el APK, sin consultas externas por MAC. El titular de un OUI no siempre es el fabricante real del producto; MAC local/aleatoria se identifica como tal.
- Los hosts que bloqueen todos los puertos TCP examinados pueden no aparecer. Solo se sondean redes privadas al entrar voluntariamente en la sección LAN o al solicitar un escaneo adicional.

## Privacidad y permisos

El escaneo Wi-Fi requiere ubicación precisa y ubicación activada en el dispositivo, y en Android 13+ también permiso de dispositivos Wi-Fi cercanos. Se solicitan ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, ACCESS_FINE_LOCATION, NEARBY_WIFI_DEVICES, ACCESS_NETWORK_STATE e INTERNET. Los resultados de los escaneos y el historial viven en memoria; los BSSID marcados como favoritos se guardan localmente en las preferencias de la app. No se conecta a servidores propios; el descubrimiento local de LAN solo se ejecuta al abrir voluntariamente la sección LAN o pulsar un botón de escaneo. Se permite exportar voluntariamente los datos capturados.

**La aplicación solo realiza peticiones a Internet desde el teléfono cuando el usuario inicia expresamente una prueba DNS o HTTPS.** En la compilación CI se descargan los registros públicos IEEE para crear la base OUI local. El servidor de destino puede observar la IP pública y metadatos normales de la conexión. Las pruebas no envían el SSID, BSSID ni la lista de puntos de acceso.

## Límites técnicos

- Android limita las exploraciones y puede entregar datos en caché; se muestra la antigüedad aproximada de los resultados.
- La evaluación de seguridad se basa en capacidades anunciadas, no en intentar vulnerar autenticación.
- No se puede medir ruido no Wi-Fi con las API ordinarias, ni garantizar que un canal sea utilizable en una región.
- Las velocidades Tx/Rx negociadas no equivalen a velocidad efectiva de Internet.
- Las pruebas de HTTPS no representan un test de velocidad ni ping ICMP.
- Algunas propiedades están ocultas por restricciones de Android o del fabricante; se deben validar en dispositivos físicos.

## Arquitectura y pruebas

- `LanActivity`: pantalla de descubrimiento LAN y exportación.\n- `DeviceDetailActivity`: detalle y acciones explícitas por dispositivo.\n- `PortScanner`: sondeo TCP acotado y concurrente.\n- `PortCatalog`: puertos habituales, nombres tentativos y validación.\n- `ResponseInspector`: lectura acotada de banner, HTTP HEAD y texto TCP.\n- `DeviceIconView`: iconos vectoriales de categorías.
- `LanScanner`: sondeos TCP limitados de hosts y lectura de ARP cuando esté autorizada.
- `OuiDatabase`: asignación offline de prefijos MAC usando IEEE.
- `scripts/update_oui.py`: descarga y valida las listas públicas IEEE para incluirlas en el APK (ejecutar antes de compilar fuera de CI).

- `MainActivity`: interfaz, escaneo, espectro, historial, seguridad y exportación.
- `ToolkitActivity`: centro de diagnóstico y calculadora de subredes.
- `NetworkDiagnostics`: conectividad Wi-Fi y pruebas de red bajo demanda.
- `ChannelMath`: dominio puro de canales y estimación de solapamiento.
- `SurveyHistory`: historial de señal deduplicado.
- `SecurityAdvisor`: auditoría pasiva de cifrado/anuncios.
- `SubnetTools`: calculadora CIDR.
- `JsonExport` / `CsvExport`: exportaciones.

Compila con JDK 17, Android SDK 35 y Gradle 8.9:

```bash
cd WifiAnalyzer-Android
python3 scripts/update_oui.py
gradle --no-daemon testDebugUnitTest assembleDebug
```

GitHub Actions ejecuta los tests y publica el APK de depuración en `WifiAnalyzer-Android/NetScope_v4.1.apk` al finalizar correctamente. El APK debug es instalable, pero no está firmado para distribución Play Store; la firma de publicación requiere una clave propia.
\n**Instalación de APK debug:** GitHub Actions puede generar firmas de depuración diferentes entre ejecuciones. Si Android indica conflicto de firma al actualizar, desinstala antes la versión antigua (se borrarán sus preferencias locales). Para actualizaciones permanentes sin perder datos, hay que configurar una clave de firma estable y privada.\n