# WiFi Analyzer para Android

Aplicación Android nativa (Java, sin librerías UI externas) para inspeccionar redes Wi-Fi cercanas. Proyecto independiente, inspirado en las funciones habituales de los analizadores de Wi-Fi; no contiene código ni recursos propietarios de otras aplicaciones.

## Funciones
- Redes cercanas: SSID, BSSID, canal, frecuencia, ancho, RSSI, seguridad, calidad y conexión actual.
- Gráficos de uso de canales por banda: 2,4 / 5 / 6 GHz.
- Historial de hasta 40 lecturas por BSSID, mantenido solo en memoria.
- Recomendaciones **heurísticas** de canales; no son mediciones de capacidad o interferencia no Wi-Fi.
- Exportación CSV sin acceso a Internet.

## Requisitos y privacidad
Android 8.0+ (API 26), Wi-Fi y servicios de ubicación encendidos. El usuario debe conceder `ACCESS_FINE_LOCATION`; desde Android 13 también `NEARBY_WIFI_DEVICES`. La ubicación no se guarda, transmite ni registra. La app no solicita permiso INTERNET, no necesita una cuenta y no usa rastreadores.

Los escaneos están restringidos por el sistema operativo. Si Android limita una solicitud, se muestran los datos anteriores; no se finge un nuevo escaneo. La app solicita actualizaciones aproximadamente cada 15 segundos mientras está visible, aunque el sistema puede entregar una frecuencia menor. El historial refleja las respuestas recibidas, que pueden estar almacenadas en caché. En algunas versiones, el acceso a datos de conexión requiere restricciones adicionales.

En 5/6 GHz algunos canales pueden no estar disponibles según país, hardware y regulación. No se detectan redes ocultas sin balizas ni interferencias de otros dispositivos. La app no cambia la configuración del router.

## Compilación
Desde el directorio `WifiAnalyzer-Android`:

```sh
gradle testDebugUnitTest assembleDebug
```

APK generado: `app/build/outputs/apk/debug/app-debug.apk`.
También se compila con GitHub Actions: [Build WiFi Analyzer APK](../.github/workflows/wifi-analyzer.yml).
Después del workflow en la rama principal, si termina correctamente, el APK se publica como `WifiAnalyzer-Android/WiFi_Analyzer_v1.0.apk`.

## Arquitectura
- `MainActivity`: adquisición por `WifiManager`, permisos, interfaz, gráficos y SAF para CSV
- `NetworkEntry`: modelo de lectura inmutable
- `ChannelMath`: conversiones de frecuencia, evaluación de calidad y estimación de interferencia
- `CsvExport`: exportación RFC-4180 con protección básica frente a fórmulas de hojas de cálculo
- `app/src/test`: pruebas JVM de lógica de dominio

Esta es una primera versión funcional, no una reproducción exacta de ninguna interfaz comercial. Se recomienda probarla en un teléfono físico para validar el comportamiento específico del chipset y la versión de Android.
