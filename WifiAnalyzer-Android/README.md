# NetScope — Wi-Fi y diagnóstico de redes (Android)

Suite Android nativa para estudiar redes Wi-Fi próximas y diagnosticar una conexión. **Sin cuenta, telemetría ni rastreadores.** Código independiente de aplicaciones comerciales.

## Funciones implementadas en v2.3

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
- Cabecera con margen superior ampliado, botón LAN visible e interfaz oscura. Android 8.0+.

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

- `LanActivity`: pantalla de descubrimiento LAN y exportación.
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

GitHub Actions ejecuta los tests y publica el APK de depuración en `WifiAnalyzer-Android/NetScope_v2.3.apk` al finalizar correctamente. El APK debug es instalable, pero no está firmado para distribución Play Store; la firma de publicación requiere una clave propia.
\n**Instalación de APK debug:** GitHub Actions puede generar firmas de depuración diferentes entre ejecuciones. Si Android indica conflicto de firma al actualizar, desinstala antes la versión antigua (se borrarán sus preferencias locales). Para actualizaciones permanentes sin perder datos, hay que configurar una clave de firma estable y privada.\n