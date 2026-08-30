# Horario Lectivo para Android

<p align="center">
  <img src="./assets/horario-icon.png" alt="Icono de Horario Lectivo" width="160" />
</p>

Aplicación Android con widget de pantalla de inicio para consultar el horario lectivo de lunes a viernes.

## Funciones

- Turno de mañana y/o tarde, cada uno con hora de inicio y fin.
- Duración global configurable de las sesiones.
- Recreo configurable por turno: después de qué sesión y cuántos minutos.
- Cálculo automático de todas las franjas horarias.
- Asignaturas con abreviatura de 3 caracteres y nombre completo.
- Editor semanal tipo “pintar”: eliges una asignatura una vez y tocas las casillas.
- Modo **Bloque**: dos toques rellenan todas las sesiones intermedias del mismo día y turno.
- Copiar un día completo a otro día o a todos los días.
- Primera columna con hora de inicio y fin.
- Recuadro rojo para la franja actual y la siguiente del día actual.
- Widget con **AHORA** y **SIGUIENTE**; al pulsarlo abre la semana completa.
- Icono personalizado de calendario y reloj para la aplicación y el proveedor del widget.
- Espacio inferior adicional en la pantalla de configuración para evitar que la última fila del horario quede tapada por la barra de navegación de Android.

## Compatibilidad

- Android 8.0 (API 26) o superior.
- `compileSdk` / `targetSdk`: API 36.

## Descargar

- **APK actual:** [HorarioLectivo_v1.6.apk](./HorarioLectivo_v1.6.apk)
- **SHA-256:** [APK_SHA256.txt](./APK_SHA256.txt)

## Pruebas y build

GitHub Actions ejecuta, en este orden:

1. `testDebugUnitTest`
2. `lintDebug`
3. `assembleDebug`
4. Verificación de los recursos del launcher y del manifiesto del APK.

El APK generado en `main` se publica como `HorarioLectivo_v1.6.apk` junto a su SHA-256.
