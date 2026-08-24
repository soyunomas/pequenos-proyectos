# Clementime by Clemente

Widget Android de reloj digital con fondo transparente, fecha en inglés y 11 estilos visuales.

## Características

- Hora 12/24 h según Android.
- Fecha abreviada en inglés (`MON 24 AUG`).
- 11 temas: original transparente + 10 marcos modernos.
- Primer toque sobre el reloj: revela un engranaje.
- Toque en el engranaje: abre el selector de temas.
- El selector también está disponible abriendo **Clementime by Clemente** desde el cajón de aplicaciones.
- Sin permisos de red ni servicios en segundo plano.

## APK

- [Clementime_by_Clemente_v1.3.apk](./Clementime_by_Clemente_v1.3.apk)
- Package: `com.clemente.clementime`
- Version: `1.3` (`versionCode 4`)
- SHA-256: `a14906153a249ea9a1b2dd345f8816de9d4cd72cf62210fee75a3e842809f19a`

## Código fuente

El directorio `app/` contiene una versión fuente normalizada en Java/XML de la arquitectura del prototipo. Los 10 marcos PNG se conservan dentro del APK incluido y el task Gradle `extractThemeAssets` los extrae automáticamente antes de compilar. También puedes extraerlos manualmente con:

```bash
python3 tools/extract_theme_assets.py
```

> El proyecto fuente reproduce la arquitectura y comportamiento del prototipo, pero no pretende generar un APK byte-a-byte idéntico al APK firmado incluido.

## Compilar

1. Abrir `Clementime-Android` con Android Studio.
2. Usar JDK 17.
3. Sincronizar Gradle.
4. Ejecutar `./gradlew assembleDebug`.

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

El prototipo mantiene `minSdk 17` y `targetSdk 29`. Para una publicación en Google Play habría que actualizar el target SDK y firmar con una clave de publicación propia.

## Recursos visuales

Los 10 marcos PNG originales no se duplican en el repositorio. El task Gradle `extractThemeAssets` los extrae directamente del APK incluido antes de `preBuild` y los incorpora como recursos generados. Si prefieres materializarlos en el árbol fuente, ejecuta:

```bash
python3 tools/extract_theme_assets.py
```
