# NetScope — Wi-Fi y diagnóstico de redes (Android)

Suite Android nativa para estudiar redes Wi-Fi próximas y diagnosticar una conexión. **Sin cuenta, telemetría ni rastreadores.** Código independiente de aplicaciones comerciales.

## Funciones implementadas en v2.1

- Exploración pasiva de redes: SSID, BSSID, RSSI, canal, banda, seguridad, ancho y estándar.
- Gráficos de ocupación estimada por bandas 2,4 / 5 / 6 GHz y puntuación orientativa de canales.
- Historial de señal de sesión con deduplicación por timestamp de escaneo Android (hasta 100 muestras por punto de acceso).
- Auditoría pasiva de seguridad: red abierta, WEP, WPA, WPA2, WPA3, OWE y Enterprise, con recomendaciones.
- Centro de diagnóstico: interfaz, IP locales, rutas/gateway, DNS, DNS privado, MTU, velocidad de enlace y validación Android.
- Prueba DNS voluntaria contra example.com, y comprobación HTTPS voluntaria contra www.google.com/generate_204 (sobre la red Wi-Fi).
- Calculadora IPv4/CIDR con /0, /31 y /32 y rango de hosts.
- Exportación CSV de redes, CSV de historial, JSON de redes y JSON con SSID/BSSID anonimizados.
- Búsqueda por SSID/BSSID/seguridad; favoritos locales y filtro de favoritos.\n- Escaneo automático opcional, con posibilidad de pausar y seguir explorando manualmente.\n- Interfaz oscura; compatible con Android 8.0+.

## Privacidad y permisos

El escaneo Wi-Fi requiere ubicación precisa y ubicación activada en el dispositivo, y en Android 13+ también permiso de dispositivos Wi-Fi cercanos. Se solicitan ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, ACCESS_FINE_LOCATION, NEARBY_WIFI_DEVICES, ACCESS_NETWORK_STATE e INTERNET. Los resultados de los escaneos y el historial viven en memoria; los BSSID marcados como favoritos se guardan localmente en las preferencias de la app. No se conecta a servidores propios ni se hace ningún rastreo de la LAN. Se permite exportar voluntariamente los datos capturados.

**La aplicación solo realiza peticiones a Internet cuando el usuario inicia expresamente una prueba DNS o HTTPS.** El servidor de destino puede observar la IP pública y metadatos normales de la conexión. Las pruebas no envían el SSID, BSSID ni la lista de puntos de acceso.

## Límites técnicos

- Android limita las exploraciones y puede entregar datos en caché; se muestra la antigüedad aproximada de los resultados.
- La evaluación de seguridad se basa en capacidades anunciadas, no en intentar vulnerar autenticación.
- No se puede medir ruido no Wi-Fi con las API ordinarias, ni garantizar que un canal sea utilizable en una región.
- Las velocidades Tx/Rx negociadas no equivalen a velocidad efectiva de Internet.
- Las pruebas de HTTPS no representan un test de velocidad ni ping ICMP.
- Algunas propiedades están ocultas por restricciones de Android o del fabricante; se deben validar en dispositivos físicos.

## Arquitectura y pruebas

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
gradle --no-daemon testDebugUnitTest assembleDebug
```

GitHub Actions ejecuta los tests y publica el APK de depuración en `WifiAnalyzer-Android/NetScope_v2.1.apk` al finalizar correctamente. El APK debug es instalable, pero no está firmado para distribución Play Store; la firma de publicación requiere una clave propia.
