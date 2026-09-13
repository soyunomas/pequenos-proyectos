# Flux Files Compose

Reescritura independiente de Flux Files en **Kotlin + Jetpack Compose**. Este árbol no reutiliza código, clases, recursos ni nombres internos de `FluxFiles-Android/source/`.

## Estado actual — 0.5.9

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
- búsqueda en la carpeta actual, ordenación, lista/cuadrícula y favoritos;
- interfaz propia con Jetpack Compose y Material 3.

### Arquitectura 0.5.9

La capa común no modela archivos con `android.net.Uri` ni con `documentId` SAF:

- `StorageBackendId`, `StorageRootRef` y `StorageRef` representan backend, raíz y entradas mediante identificadores opacos;
- `StorageEntry` distingue archivo/directorio con `StorageEntryKind` y no contiene tipos Android;
- `BrowserLocation` y `BrowserUiState` usan referencias neutrales al backend;
- `StorageRepository` expone operaciones de filesystem sobre referencias comunes;
- `StorageContentAccess` añade creación de archivos y acceso mediante `InputStream`/`OutputStream`;
- `StorageFileSystem` combina operaciones y acceso a contenido sin depender de Android;
- `TransferOperationEngine` no importa Android y resuelve identidad/conflictos mediante `StorageRef`;
- `SafStorageRepository` implementa `StorageFileSystem` y encapsula `DocumentsContract`/`ContentResolver`;
- `FileInspectorV5` consume streams del filesystem para imágenes, texto y lectura de ZIP;
- `ZipCreatorV52` y `ZipInspectorV51` crean, leen, escriben y eliminan contenido mediante `StorageFileSystem`;
- el navegador activo `BrowserScreenV5` ya no usa `treeUri`, `documentId` ni `DocumentsContract` para navegación, selección o favoritos;
- los favoritos se persisten con `StorageRef` completo (`backend + opaqueId`) y se abren sin reconstruir documentos SAF;
- `OpenDocumentTree` vive en `LauncherActivity`, como frontera Android, y la pantalla solo solicita `onChooseLocation`.

SAF sigue siendo la única implementación conectada a la aplicación. La separación actual permite que futuros backends SFTP, SMB, FTP y WebDAV reutilicen navegación, favoritos, contenido y ZIP sin simular documentos SAF.

`SafInterop.kt` continúa temporalmente para compatibilidad con código Compose antiguo y para acciones Android que necesitan un `Uri`, como `ACTION_VIEW`. Ya no participa en la identidad ni en los favoritos del navegador activo.

> Nota de migración: los favoritos guardados por versiones anteriores usaban `documentId` SAF. 0.5.9 inicia el formato neutral de favoritos, por lo que esas entradas antiguas no se importan automáticamente.

## Principios del proyecto

- Namespace y `applicationId`: `dev.soyunomas.fluxfiles`.
- Implementación independiente; Material Files puede servir como referencia de comportamiento, no como fuente de código.
- SAF por defecto: Flux Files solo trabaja con ubicaciones autorizadas por el usuario y no solicita acceso global al almacenamiento para el flujo normal.
- Las operaciones largas deben quedar fuera de los ViewModels y evolucionar hacia una cola cancelable con progreso y trabajo en segundo plano.

## Próximos pasos

1. Sustituir el adaptador SAF de apertura externa por una capacidad Android separada que pueda preparar también contenido remoto.
2. Mover copiar, mover y operaciones ZIP largas a una cola propia independiente del ciclo de vida de pantalla, con progreso y cancelación.
3. Introducir un registro/fábrica de backends para seleccionar `StorageFileSystem` sin instanciar SAF directamente en los puntos de composición.
4. Completar la estabilización 0.5.x y comenzar 0.6.x con backends remotos separados: SFTP, SMB, FTP y WebDAV.

## Compilar

Requisitos: JDK 17+, Android SDK 35 y Gradle 8.9.

```bash
gradle :app:assembleDebug
```

APK de depuración: `app/build/outputs/apk/debug/app-debug.apk`.
