# Flux Files Compose

Reescritura independiente de Flux Files en **Kotlin + Jetpack Compose**. Este árbol no reutiliza el código de `FluxFiles-Android/source/`.

## 0.1.0: objetivo de validación UX

Esta primera versión está hecha para validar la experiencia básica antes de añadir operaciones destructivas.

- interfaz Material 3 propia;
- selección de ubicación mediante Storage Access Framework (SAF), sin pedir acceso global al almacenamiento;
- permiso persistente únicamente para la carpeta elegida por el usuario;
- navegación por carpetas con botón Atrás y navegación del sistema;
- carpetas antes que archivos y orden alfabético local;
- tamaño y fecha de modificación en archivos;
- apertura de archivos con aplicaciones Android compatibles;
- estados explícitos de primera ejecución, carga, carpeta vacía y error;
- tema claro/oscuro;
- objetivos táctiles amplios y filas legibles.

## Decisiones de UX

La primera pantalla no solicita permisos invasivos. Explica qué acceso necesita la aplicación y deja al usuario escoger una ubicación mediante el selector del sistema. Flux Files recuerda esa autorización para no obligar a repetirla en cada inicio.

Las funciones de borrar, mover, copiar o renombrar quedan fuera de 0.1.0 de forma intencionada: primero se valida navegación, densidad de información, jerarquía visual y comprensión del modelo de acceso.

## Compilar

Requisitos: JDK 17+, Android SDK 35 y Gradle 8.9.

```bash
gradle :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
