# Flux Files Compose

Reescritura independiente de Flux Files en **Kotlin + Jetpack Compose**. Este árbol no reutiliza código, clases, recursos ni nombres internos de `FluxFiles-Android/source/`.

## Estado actual — 0.5.5

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

### Arquitectura 0.5.5

La estabilización iniciada en 0.5.4 continúa separando UI, operaciones y almacenamiento:

- `BrowserModels.kt` contiene los modelos compartidos del navegador y de las transferencias;
- `StorageRepository` es ahora un contrato que consume `TransferOperationEngine` y el ViewModel;
- `SafStorageRepository` contiene la implementación local basada en `DocumentsContract` y `ContentResolver`;
- la antigua `MainActivity.kt`, que ya no era launcher y mezclaba modelos, almacenamiento, ViewModel y UI del prototipo, se ha eliminado;
- `LauncherActivity` sigue siendo el único punto de entrada de la aplicación.

Los modelos todavía conservan identificadores y `Uri` propios del flujo SAF. Esta versión separa responsabilidades sin alterar el comportamiento público; el siguiente paso es introducir modelos de filesystem neutrales al backend y adaptadores de capacidades para apertura/streams Android.

No se añadirá SFTP, SMB, FTP o WebDAV directamente sobre las clases SAF: los backends remotos deberán entrar detrás de la misma abstracción de filesystem.

## Principios del proyecto

- Namespace y `applicationId`: `dev.soyunomas.fluxfiles`.
- Implementación independiente; Material Files puede servir como referencia de comportamiento, no como fuente de código.
- SAF por defecto: Flux Files solo trabaja con ubicaciones autorizadas por el usuario y no solicita acceso global al almacenamiento para el flujo normal.
- Las operaciones largas deben quedar fuera de los ViewModels y evolucionar hacia una cola cancelable con progreso y trabajo en segundo plano.

## Próximos pasos

1. Sustituir los modelos SAF-específicos por identificadores de ubicación/entrada neutrales al backend.
2. Definir capacidades de filesystem para listar, mutar y abrir streams sin filtrar `Uri` a la capa común.
3. Mover las operaciones largas a una cola propia con progreso, cancelación y ejecución independiente del ciclo de vida de pantalla.
4. Completar la estabilización 0.5.x y comenzar 0.6.x con backends remotos separados: SFTP, SMB, FTP y WebDAV.

## Compilar

Requisitos: JDK 17+, Android SDK 35 y Gradle 8.9.

```bash
gradle :app:assembleDebug
```

APK de depuración: `app/build/outputs/apk/debug/app-debug.apk`.
