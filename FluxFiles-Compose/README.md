# Flux Files Compose

Reescritura independiente de Flux Files en **Kotlin + Jetpack Compose**. Este árbol no reutiliza código, clases, recursos ni nombres internos de `FluxFiles-Android/source/`.

## Estado actual — 0.5.10

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

### Arquitectura 0.5.10

La capa de navegación y operaciones trabaja con referencias neutrales al backend:

- `StorageBackendId`, `StorageRootRef` y `StorageRef` representan backend, raíz y entradas mediante identificadores opacos;
- `StorageEntry`, `BrowserLocation` y `BrowserUiState` no contienen `Uri`, `documentId` ni tipos Android;
- `StorageRepository` declara ahora su `backendId` y modela el acceso a una raíz con `persistRootAccess`, `hasRootAccess` y `canWrite`, sin asumir permisos SAF;
- `StorageContentAccess` expone creación de archivos y streams, y `StorageFileSystem` combina ambas capacidades;
- `StorageBackendRegistry` resuelve cada `StorageRootRef`, `StorageRef`, `BrowserLocation` o `StorageEntry` al filesystem que lo posee;
- `AndroidStorageServices` es el punto de composición Android: registra SAF hoy y podrá registrar backends adicionales sin modificar navegador, previews o ZIP;
- `BrowserViewModelV53` ya no importa `Uri`, no instancia `SafStorageRepository` y selecciona el filesystem mediante el registro;
- la raíz persistida guarda `backend + opaqueId`; la preferencia histórica `tree_uri` se migra una sola vez;
- `FileInspectorV5` recibe el filesystem y un callback de apertura externa, sin construir SAF ni convertir referencias a `Uri`;
- `AndroidExternalFileOpener` mantiene `ACTION_VIEW` y la conversión SAF en un adaptador de plataforma separado;
- `BrowserScreenV5` delega la apertura externa a un helper de plataforma respaldado por adaptadores por backend y no contiene lógica SAF;
- `BrowserScreenV4.kt` y `SafInterop.kt` se han eliminado al no tener consumidores activos.

SAF sigue siendo el único backend real registrado en esta versión. El registro no implementa todavía SFTP, SMB, FTP o WebDAV, ni habilita transferencias entre backends; prepara el punto de extensión para hacerlo sin volver a acoplar la UI.

Los favoritos introducidos en 0.5.9 continúan usando el formato neutral `StorageRef` (`backend + opaqueId`).

## Principios del proyecto

- Namespace y `applicationId`: `dev.soyunomas.fluxfiles`.
- Implementación independiente; Material Files puede servir como referencia de comportamiento, no como fuente de código.
- SAF por defecto: Flux Files solo trabaja con ubicaciones autorizadas por el usuario y no solicita acceso global al almacenamiento para el flujo normal.
- Las operaciones largas deben quedar fuera de los ViewModels y evolucionar hacia una cola cancelable con progreso y trabajo en segundo plano.

## Próximos pasos

1. Sacar copiar/mover del `viewModelScope` y ejecutar transferencias largas mediante una cola de operaciones independiente del ciclo de vida de pantalla.
2. Llevar creación/extracción ZIP a la misma cola, con progreso y cancelación compartidos.
3. Definir persistencia/reanudación y notificaciones para operaciones que continúen fuera de la pantalla.
4. Tras estabilizar la cola, comenzar 0.6.x con backends remotos separados (SFTP, SMB, FTP y WebDAV) registrados mediante `StorageBackendRegistry`.

## Compilar

Requisitos: JDK 17+, Android SDK 35 y Gradle 8.9.

```bash
gradle :app:assembleDebug
```

APK de depuración: `app/build/outputs/apk/debug/app-debug.apk`.
