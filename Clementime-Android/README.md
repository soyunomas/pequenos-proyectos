# Clementime by Clemente

Widget Android de reloj digital con fondo transparente, fecha en inglés y 11 estilos visuales.

## Características

- Hora 12/24 h según Android.
- Fecha abreviada en inglés (`MON 24 AUG`).
- 11 temas: original transparente + 10 marcos modernos.
- Primer toque sobre el reloj: revela un engranaje.
- Toque en el engranaje: abre el selector de temas.
- También puedes abrir **Clementime by Clemente** desde el cajón de aplicaciones para elegir tema.
- Sin permisos de red ni servicios en segundo plano.

## APK

- [Clementime_by_Clemente_v1.3.apk](./Clementime_by_Clemente_v1.3.apk)
- Package: `com.clemente.clementime`
- Version: `1.3` (`versionCode 4`)
- El hash SHA-256 del APK compilado está en [`APK_SHA256.txt`](./APK_SHA256.txt).

El APK del repositorio se compila automáticamente desde este código mediante GitHub Actions. Es un build de depuración instalable. Si tienes instalada una versión firmada con otra clave, Android puede pedirte desinstalarla antes.

## Código fuente

Proyecto Android Studio/Gradle en Java y XML. Los diez marcos se implementan como drawables XML para que el proyecto sea autocontenido y reproducible.

## Compilar

1. Abre `Clementime-Android` con Android Studio.
2. Usa JDK 17.
3. Sincroniza Gradle.
4. Ejecuta `gradle :app:assembleDebug` o el task equivalente desde Android Studio.

El APK debug se genera en `app/build/outputs/apk/debug/app-debug.apk`.

## Temas

0. Original transparent
1. Silver double line
2. Cyan HUD
3. Graphite gold
4. Glass
5. Industrial
6. Chrome capsule
7. Architectural
8. Monochrome pinstripe
9. Blue steel
10. Red sport

## Compatibilidad

`minSdk 17`, `targetSdk 29`, `compileSdk 35`. Para Google Play habría que actualizar el target SDK y preparar una firma de publicación.
