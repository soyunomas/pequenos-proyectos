# Horario Lectivo para Android

<p align="center">
  <img src="./assets/horario-icon.png" alt="Icono de Horario Lectivo" width="160" />
</p>

Aplicación Android con widget de pantalla de inicio para consultar el horario lectivo de lunes a viernes.

## Funciones

- Turno de mañana y/o tarde, duración de sesiones y recreos configurables.
- Editor semanal tipo “pintar”, modo bloque y copia de días.
- Vista completa con resaltado de la franja actual y siguiente.
- Interfaz con modo claro y oscuro, tarjetas y controles tonales.
- **Selector de color por asignatura** con una paleta predefinida de 24 colores diseñada para mantener buen contraste en claro y oscuro.
- La aplicación evita que dos asignaturas usen el mismo color elegido mientras haya colores disponibles.
- Las pantallas de configuración y horario completo respetan los **insets de la barra de estado y navegación**, evitando solaparse con hora, batería o botones del sistema.
- Widget 4×2 redimensionable a 4×1, con clase actual, siguiente, hora de comienzo y cuenta atrás.
- Icono personalizado de calendario y reloj.

## Compatibilidad

- Android 8.0 (API 26) o superior.
- `compileSdk` / `targetSdk`: API 36.

## Descargar

- **APK actual:** [HorarioLectivo_v1.11.apk](./HorarioLectivo_v1.11.apk)
- **SHA-256:** [APK_SHA256.txt](./APK_SHA256.txt)

## Pruebas y build

GitHub Actions ejecuta tests, lint, compilación y comprobaciones del selector de colores, persistencia, insets y widget antes de publicar el APK.
