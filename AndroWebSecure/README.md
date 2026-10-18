# AndroWeb Secure · Servidor web privado para Android

Servidor web **estático y local** para Android 8+ con panel nativo, servicio foreground controlado por el usuario y restricción de red a `127.0.0.1:8787`. No abre la Wi-Fi, no acepta subidas HTTP, no ejecuta backend dinámico y no recopila datos.

## Qué incluye

- Código fuente Android en `app/` y núcleo HTTP en `app/src/main/java/es/local/androweb/core/`.
- Archivos de especificación completos en `docs/`: PRD, TRD, App Flow, UI/UX Brief, Backend Schema, Implementation Plan y Threat Model.
- Tests autocontenidos del núcleo en `scripts/test-core.sh`.
- Workflow de GitHub Actions `.github/workflows/build-apk.yml` para crear un APK debug instalable.

## Construir e instalar el APK

Requisitos: JDK 17; Android SDK Platform 36, Build Tools 36.0.0; Gradle 9.3.1; Android Emulator/ADB opcional. También puedes abrir el proyecto en una versión de Android Studio compatible e instalar estos SDKs desde su gestor. No se adjunta `gradle-wrapper.jar` porque el SDK y Gradle no están disponibles en este entorno; el workflow fija la versión de Gradle.

```bash
bash scripts/test-core.sh
gradle --no-daemon :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Descargar APK desde CI:** coloca esta carpeta como repositorio GitHub; ve a Actions → «Build and test Android APK» → «Run workflow». Al finalizar, descarga el artefacto `AndroWebSecure-debug-apk` que contiene `app-debug.apk`. Se trata de un APK debug firmado automáticamente para pruebas, no de una publicación en Play.

## Uso

1. Abre AndroWeb Secure y pulsa «Iniciar»; atiende la solicitud opcional de notificaciones.
2. Pulsa «Abrir en el navegador»: verás el sitio de ejemplo en `http://127.0.0.1:8787/`.
3. Importa tu propio `index.html`, hojas CSS, JavaScript e imágenes desde el selector nativo. Usa nombres planos (sin carpetas).
4. «Detener» en la app o en la notificación cierra el servidor. «Restaurar sitio de ejemplo» borra solo las copias privadas dentro de la app.

**Importante:** otros teléfonos u ordenadores no pueden acceder. Es intencionado. No guardes claves, datos personales o archivos privados en el directorio de la web. No hay autenticación entre procesos del mismo Android.

## Verificación realizada

El núcleo Java se compila y se prueba con `javac` y `java` sin SDK; ejecutar `bash scripts/test-core.sh` muestra el resultado. En este entorno no se han podido compilar `Activity` y `Service` contra el Android SDK, ejecutar un emulador ni generar un APK auténtico. **El proyecto no debe calificarse como APK probado hasta ejecutar el workflow o una compilación Android real.**

## Licencia

Código de demostración entregado para revisión y adaptación. La distribución en producción requiere pruebas de seguridad, política de privacidad y firma de release propia.