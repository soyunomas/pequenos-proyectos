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

## Código fuente

Proyecto Android Studio/Gradle en Java y XML. Los diez marcos se implementan como drawables XML para que el proyecto sea autocontenido y reproducible.

## APK

El repositorio incluye el workflow `.github/workflows/build-clementime.yml`, preparado para compilar un APK debug instalable desde este código y publicarlo como `Clementime-Android/Clementime_by_Clemente_v1.3.apk` junto con su SHA-256.

La primera transferencia binaria realizada mediante el conector de GitHub quedó truncada y se eliminó expresamente para no conservar un APK inválido en el repositorio.

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
