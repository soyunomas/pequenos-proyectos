# Folio PDF

Visor PDF para Android sin anuncios, cuenta, analítica ni conexión a Internet.

## Versión 1.1.0

La lectura pasa al visor PDF oficial de AndroidX para ofrecer una capa de texto real y una interacción nativa:

- Mantén pulsado sobre texto para seleccionarlo.
- Ajusta los tiradores de selección y usa **Copiar** o **Seleccionar todo**.
- Busca texto con el botón **Buscar**.
- Zoom con gesto de pinza y doble toque.
- Desplazamiento continuo, navegación rápida y enlaces del PDF.
- Apertura desde **Abrir**, desde gestores de archivos o mediante **Abrir con…**.
- Recuerda el último documento cuando Android concede permiso persistente.
- No solicita permiso `INTERNET`.

> La selección requiere que el PDF contenga texto real. En un PDF escaneado que solo contiene imágenes no hay texto que seleccionar; el OCR no forma parte de esta versión.

## UX

Al cargar el primer documento aparece un consejo discreto explicando la pulsación larga para seleccionar. Puede cerrarse y no vuelve a ocupar espacio. La búsqueda solo se habilita cuando el documento ha terminado de cargar.

La interfaz mantiene el documento como elemento principal: barra superior compacta con título, **Buscar** y **Abrir**; el resto del espacio pertenece al PDF y a sus controles nativos de selección.

## Arquitectura

- `androidx.pdf:pdf-viewer-fragment:1.0.0-beta01`
- `Storage Access Framework` para elegir documentos.
- `PdfViewerFragment` para renderizado, selección, copia, búsqueda y zoom.
- Sin SDK publicitario ni telemetría.

## Compatibilidad

- Android 9 (API 28) o superior.
- `applicationId`: `com.soyunomas.foliopdf` — se mantiene respecto a la versión 1.0.
- `versionCode`: 2.
- `versionName`: 1.1.0.

## Compilar

```bash
gradle :app:assembleDebug
```

GitHub Actions compila y publica `lector-pdf/Folio-PDF.apk` en `main`.

### Firma de actualizaciones

El workflow conserva el almacén de firma de depuración en la caché de GitHub Actions para que las compilaciones posteriores mantengan la misma firma. La APK 1.0 se generó antes de configurar esta persistencia, por lo que Android puede exigir una única reinstalación al pasar de 1.0 a 1.1; las siguientes versiones se compilarán con la firma persistente mientras esa caché esté disponible.
