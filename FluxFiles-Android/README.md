# Flux Files para Android

Gestor de archivos Android basado en **Material Files 1.7.4**, adaptado como **Flux Files**. Esta carpeta contiene el APK instalable y el código fuente correspondiente a la compilación validada por CI.

## Instalación

- APK: [`FluxFiles-v1.0.0.apk`](./FluxFiles-v1.0.0.apk)
- SHA-256: `5673f8e18b46caad648d20ec029016db3d97c240373d49f606e8a0985f5eb99c`
- Paquete: `dev.local.fluxfiles`
- Nombre: `Flux Files`
- Versión: `1.7.4-flux.1`
- Android mínimo: API 23 / Android 6.0

El APK está firmado con la clave de depuración usada por CI; es válido para instalación manual, no como firma de publicación para Google Play.

## Contenido

- `source/`: Material Files fijado al commit `fc1250038496ebf4d4c139f62d16f0071f2c995a` con los cambios de Flux Files aplicados.
- `third_party/dav4jvm/`: dav4jvm fijado a `02fe1a95e6b86e323bec3784d7d2fe2d4081dde6` y ajustado para usar el módulo Kotlin `build`.
- `build-from-source.sh`: recompila dav4jvm localmente y después la aplicación.
- `FluxFiles-v1.0.0.apk`: APK previamente validado por CI.
- `FluxFiles-v1.0.0.sha256`: hash para comprobar integridad.

## Compilar

Requiere JDK 21 y Android SDK. Desde esta carpeta:

```bash
./build-from-source.sh
```

El APK recompilado se genera en `source/app/build/outputs/apk/debug/app-debug.apk`.

## Modificaciones realizadas

- `applicationId`: `dev.local.fluxfiles`.
- Nombre visible: **Flux Files**.
- `versionName`: `1.7.4-flux.1`.
- Eliminación de bloques `NONFREE` y del código de Firebase/Crashlytics/Google Services.
- Repositorio Maven local para dav4jvm.
- dav4jvm fijado a la revisión exacta usada por el build y compilado con `moduleName = build` para conservar el ABI esperado.

## Licencias y procedencia

`source/` deriva de [Material Files](https://github.com/zhanghai/MaterialFiles), publicado bajo **GPL-3.0**. Se conservan sus archivos de licencia y avisos. La dependencia [dav4jvm](https://github.com/bitfireAT/dav4jvm) se incluye en `third_party/` con sus avisos y licencia correspondientes.

## Validación del APK

El APK aquí incluido procede del run que superó `assembleDebug`, `lintVitalRelease`, verificación con `apksigner` y comprobación de identidad de paquete/etiqueta.
