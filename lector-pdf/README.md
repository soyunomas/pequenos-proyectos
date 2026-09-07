# PDF Limpio

Visor PDF para Android centrado en una sola tarea: abrir un PDF y leerlo sin anuncios, cuentas ni conexión a Internet.

## UX y decisiones de producto

- Apertura directa con «Abrir PDF» mediante el selector de archivos de Android.
- Compatible con «Abrir con…» desde gestores de archivos, mensajería y correo.
- Página a pantalla completa, con interfaz mínima y controles de navegación grandes.
- Pinza para zoom y doble toque para ampliar/restablecer.
- Deslizamiento horizontal para cambiar de página cuando la vista está sin zoom.
- Toque sobre el indicador `página / total` para saltar directamente a una página.
- Recuerda el último PDF cuando Android concede permiso persistente sobre el documento.
- Sin permiso `INTERNET`, anuncios, analítica, telemetría ni subida de documentos.

## Arquitectura

La aplicación usa exclusivamente APIs de Android: `Storage Access Framework`, `PdfRenderer`, `ExecutorService` y una `ZoomableImageView` propia. No incluye SDKs de terceros.

## Requisitos

- Android 5.0 (API 21) o superior.
- Android SDK 35.
- JDK 17+ y Gradle 8.9 para compilar.

## Compilar

Desde esta carpeta:

```bash
gradle :app:assembleDebug
```

La APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
