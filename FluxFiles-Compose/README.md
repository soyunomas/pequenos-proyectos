# Flux Files Compose

Reescritura independiente de Flux Files en **Kotlin + Jetpack Compose**. Este árbol no reutiliza código, clases, recursos ni nombres internos de `FluxFiles-Android/source/`.

## Estado actual — 0.5.4

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

### Cambio de arquitectura en 0.5.4

La ejecución de copiar/mover y su política de conflictos se han extraído de `BrowserViewModelV53` a `TransferOperationEngine`. El ViewModel conserva únicamente la coordinación con el estado de pantalla y las respuestas del usuario.

Es el primer paso para separar operaciones, filesystem y UI antes de introducir almacenamiento remoto. La siguiente refactorización debe sacar de `MainActivity.kt` los modelos y `StorageRepository`, que aún proceden de la evolución incremental del prototipo, y convertir el backend SAF en una implementación explícita de un contrato de filesystem propio.

No se añadirá SFTP, SMB, FTP o WebDAV directamente sobre las clases actuales: los backends remotos deberán entrar detrás de la misma abstracción que el almacenamiento local.

## Principios del proyecto

- Namespace y `applicationId`: `dev.soyunomas.fluxfiles`.
- Implementación independiente; Material Files puede servir como referencia de comportamiento, no como fuente de código.
- SAF por defecto: Flux Files solo trabaja con ubicaciones autorizadas por el usuario y no solicita acceso global al almacenamiento para el flujo normal.
- Las operaciones largas deben quedar fuera de los ViewModels y evolucionar hacia una cola cancelable con progreso y trabajo en segundo plano.

## Próximos pasos

1. Extraer modelos y contrato de filesystem fuera de `MainActivity.kt` y eliminar la implementación antigua que ya no es launcher.
2. Encapsular SAF como backend local independiente de Compose.
3. Mover las operaciones largas a una capa/cola propia con progreso y cancelación.
4. Completar la estabilización 0.5.x y, sobre esa base, comenzar 0.6.x con protocolos remotos por separado.

## Compilar

Requisitos: JDK 17+, Android SDK 35 y Gradle 8.9.

```bash
gradle :app:assembleDebug
```

APK de depuración: `app/build/outputs/apk/debug/app-debug.apk`.
