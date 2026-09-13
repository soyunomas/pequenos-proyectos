# Flux Files Compose

Reescritura independiente de Flux Files en **Kotlin + Jetpack Compose**. Este árbol no reutiliza código, clases, recursos ni nombres internos de `FluxFiles-Android/source/`.

## Estado actual — 0.5.8

Flux Files cubre el flujo local principal sobre Storage Access Framework (SAF):

- selección y persistencia de una ubicación autorizada por el usuario;
- navegación por carpetas y detección de ubicaciones de solo lectura;
- creación de carpetas, renombrado y borrado;
- selección múltiple;
- copiar y mover con resolución de conflictos: reemplazar, omitir, conservar ambos o cancelar;
- apertura e inspección interna de archivos;
- previsualizaciones básicas de imágenes, texto y ZIP;
- inspección y extracción segura de ZIP;
- creación de ZIP desde la selección;
- interfaz propia con Jetpack Compose y Material 3.

### Arquitectura 0.5.8

La capa común ya no modela archivos con `android.net.Uri` ni con `documentId` SAF:

- `StorageBackendId`, `StorageRootRef` y `StorageRef` representan backend, raíz y entradas mediante identificadores opacos;
- `StorageEntry` distingue archivo/directorio con `StorageEntryKind` y no contiene tipos Android;
- `BrowserLocation` y `BrowserUiState` usan referencias neutrales al backend;
- `StorageRepository` expone operaciones de filesystem sobre referencias comunes;
- `StorageContentAccess` añade creación de archivos y acceso mediante `InputStream`/`OutputStream`;
- `StorageFileSystem` combina operaciones y acceso a contenido sin depender de Android;
- `TransferOperationEngine` no importa Android y resuelve identidad/conflictos mediante `StorageRef`;
- `SafStorageRepository` implementa `StorageFileSystem` y encapsula `DocumentsContract`/`ContentResolver`;
- `FileInspectorV5` consume streams del filesystem para imágenes, texto y lectura de ZIP;
- `ZipCreatorV52` y `ZipInspectorV51` ya crean, leen, escriben y eliminan contenido únicamente mediante `StorageFileSystem`;
- la creación/extracción ZIP conserva límites de entradas y tamaño, validación de rutas, progreso, cancelación y limpieza de resultados parciales.

SAF sigue siendo la única implementación conectada a la UI. La separación actual permite que los futuros backends SFTP, SMB, FTP y WebDAV reutilicen el flujo de contenido y ZIP sin simular documentos SAF.

`SafInterop.kt` continúa como frontera temporal para partes antiguas del navegador Compose que todavía requieren conversiones SAF. No forma parte del modelo de dominio ni de las operaciones ZIP nuevas.

## Principios del proyecto

- Namespace y `applicationId`: `dev.soyunomas.fluxfiles`.
- Implementación independiente; Material Files puede servir como referencia de comportamiento, no como fuente de código.
- SAF por defecto: Flux Files solo trabaja con ubicaciones autorizadas por el usuario y no solicita acceso global al almacenamiento para el flujo normal.
- Las operaciones largas deben quedar fuera de los ViewModels y evolucionar hacia una cola cancelable con progreso y trabajo en segundo plano.

## Próximos pasos

1. Eliminar las dependencias restantes de `SafInterop` en el navegador activo y representar favoritos mediante `StorageRef`.
2. Mover copiar, mover y operaciones ZIP largas a una cola propia independiente del ciclo de vida de pantalla, con progreso y cancelación.
3. Definir capacidades por backend y selección de filesystem sin cablear `SafStorageRepository` directamente en la UI.
4. Completar la estabilización 0.5.x y comenzar 0.6.x con backends remotos separados: SFTP, SMB, FTP y WebDAV.

## Compilar

Requisitos: JDK 17+, Android SDK 35 y Gradle 8.9.

```bash
gradle :app:assembleDebug
```

APK de depuración: `app/build/outputs/apk/debug/app-debug.apk`.
