# Folio PDF

Lector y editor ligero de PDF para Android.

## Funciones

- Apertura de PDF desde el selector de archivos y mediante «Abrir con…».
- Lectura con zoom y desplazamiento.
- Selección y copia de texto cuando el documento contiene texto real.
- Búsqueda dentro del documento.
- Rellenado de formularios PDF compatibles y guardado de una copia.
- Modo compatible para documentos que el visor avanzado no procese correctamente.
- Registro de diagnóstico copiable para investigar errores.

- Organizador de páginas con miniaturas, selección por intervalos, extracción, separación, giro, eliminación y ordenación mediante arrastre.
- Unión local de PDF, compartir, información y lectura inmersiva.
- Las operaciones generan copias; por seguridad se rechazan documentos cifrados, firmados o con formularios interactivos. Los enlaces internos y marcadores pueden no conservarse al extraer páginas.

## Interfaz

La pantalla prioriza el documento. Las acciones frecuentes están en la barra superior y las opciones de compatibilidad y diagnóstico quedan en el menú secundario. Los formularios se rellenan tocando directamente sus campos.

## Compatibilidad

- Android 9 (API 28) o superior.
- `applicationId`: `com.soyunomas.foliopdf`.
- `versionCode`: 10.
- `versionName`: 1.4.0.

## Compilar

```bash
gradle :app:assembleDebug
```

GitHub Actions compila y publica `lector-pdf/Folio-PDF.apk` en `main`.
