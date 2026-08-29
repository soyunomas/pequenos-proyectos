# Horario Lectivo para Android

Aplicación + widget de pantalla de inicio para consultar el horario lectivo de lunes a viernes.

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
- Icono de aplicación y del proveedor del widget basado directamente en el diseño de calendario y reloj suministrado.

## Compatibilidad

- Android 8.0 (API 26) o superior.
- `compileSdk` / `targetSdk`: API 36.

## Pruebas y build

GitHub Actions ejecuta, en este orden:

1. `testDebugUnitTest`
2. `lintDebug`
3. `assembleDebug`
4. Verificación del APK con `aapt dump badging`

El APK generado en `main` se publica como `HorarioLectivo_v1.4.apk` junto a su SHA-256.
