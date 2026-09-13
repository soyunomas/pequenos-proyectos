# Flux Files Compose

Reescritura independiente de Flux Files en **Kotlin + Jetpack Compose**. Este árbol no reutiliza código, clases, recursos ni nombres internos de `FluxFiles-Android/source/`.

## Estado actual — 0.5.6

Flux Files ya cubre el flujo local principal sobre Storage Access Framework (SAF):

- selección y persistencia de una ubicación autorizada por el usuario;
- navegación por carpetas y detección de ubicaciones de solo lectura;
- creación de carpetas, renombrado y borrado;
- selección múltiple;
- copiar y mover con resolución de conflictos: reemplazar, omitir, conservar ambos o cancelar;
- apertura e inspección interna de archivos;
- previsualizaciones básicas;
- inspección y extracción segura de ZIP;
- creación de ZIP desde la selección;
- interfaz propia con Jetpack Compose y Material 3.

### Arquitectura 0.5.6

La capa común deja de modelar archivos con `android.net.Uri` o `documentId` SAF:

- `StorageBackendId`, `StorageRootRef` y `StorageRef` representan backend, raíz y entradas mediante identificadores opacos;
- `StorageEntry` distingue archivo/directorio con `StorageEntryKind` y ya no contiene tipos Android;
- `BrowserLocation` y `BrowserUiState` usan referencias neutrales al backend;
- `StorageRepository` ya no acepta `Uri` y expone operaciones de filesystem sobre referencias comunes;
- `TransferOperationEngine` no importa Android y resuelve identidad/conflictos mediante `StorageRef`;
- `SafStorageRepository` interpreta las referencias opacas y encapsula `DocumentsContract`/`ContentResolver`;
- `SafInterop.kt` mantiene temporalmente la compatibilidad con previews y ZIP de la rama 0.5.x, convirtiendo refs SAF a `Uri` solo en la frontera Android.

Esto todavía no es el backend remoto completo: SAF sigue siendo la única implementación conectada a la UI. El siguiente paso es separar capacidades de lectura/escritura de streams y eliminar el uso directo de `Uri` en inspector/ZIP antes de introducir SFTP, SMB, FTP y WebDAV.

## Principios del proyecto

- Namespace y `applicationId`: `dev.soyunomas.fluxfiles`.
- Implementación independiente; Material Files puede servir como referencia de comportamiento, no como fuente de código.
- SAF por defecto: Flux Files solo trabaja con ubicaciones autorizadas por el usuario y no solicita acceso global al almacenamiento para el flujo normal.
- Las operaciones largas deben quedar fuera de los ViewModels y evolucionar hacia una cola cancelable con progreso y trabajo en segundo plano.

## Próximos pasos

1. Definir capacidades de filesystem para abrir streams, exponer contenido y resolver acciones externas sin filtrar `Uri` a la capa común.
2. Migrar inspector, previews y ZIP para consumir esas capacidades en lugar de `SafInterop`.
3. Mover las operaciones largas a una cola propia con progreso, cancelación y ejecución independiente del ciclo de vida de pantalla.
4. Completar la estabilización 0.5.x y comenzar 0.6.x con backends remotos separados: SFTP, SMB, FTP y WebDAV.

## Compilar

Requisitos: JDK 17+, Android SDK 35 y Gradle 8.9.

```bash
gradle :app:assembleDebug
```

APK de depuración: `app/build/outputs/apk/debug/app-debug.apk`.
