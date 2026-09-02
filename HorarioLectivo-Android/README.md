# Horario Lectivo para Android

<p align="center">
  <img src="./assets/horario-icon.png" alt="Icono de Horario Lectivo" width="160" />
</p>

Aplicación Android para configurar un horario semanal y consultarlo rápidamente desde un widget de pantalla de inicio.

## Funciones principales

- Turnos independientes de **mañana**, **tarde** y **noche**.
- Intervalo configurable **entre mañana y tarde** y otro **entre tarde y noche**.
- Los intervalos entre turnos ya no aparecen como una cabecera o banda naranja separada: se muestran como **filas horarias ámbar**, del mismo modo que un recreo.
- Tanto los intervalos entre turnos como los recreos admiten una asignación distinta para cada día. Si están vacíos conservan el tratamiento ámbar; si tienen un módulo muestran sus siglas y color.
- Duración de sesiones y recreos configurable.
- Editor semanal por casillas, modo bloque y copia de días.
- Asignaturas **L** (lectivas) y **C** (complementarias), con siglas, nombre y color editables.
- Modo claro y oscuro.
- Widget 4×2, redimensionable a 4×1, con actividad actual, siguiente actividad y cuenta atrás.

## Configurar turnos

En **Configurar horario** puedes activar o desactivar por separado mañana, tarde y noche. Cada turno tiene comienzo, fin y recreo propios.

Los intervalos entre turnos también son independientes:

- **Entre mañana y tarde**: por defecto `14:00–15:00`.
- **Entre tarde y noche**: disponible para cubrir reuniones, RETA, departamento u otras actividades antes del turno nocturno.

En la tabla semanal no hay una banda `ENTRE TURNOS`. En su lugar, la propia fila correspondiente a esas horas usa el mismo tratamiento ámbar que el recreo y muestra `ENTRE` cuando no tiene nada asignado.

## Recreos e intervalos asignables

Selecciona una asignatura y toca una celda de recreo o de intervalo entre turnos para asignarla. Si seleccionas **BORRAR**, la celda vuelve al estado ámbar vacío (`RECREO` o `ENTRE`).

Las asignaciones de estas franjas se incluyen al copiar un día a otro y también se tienen en cuenta en **AHORA** y **SIGUIENTE** del widget.

## Instalación

1. Descarga **[HorarioLectivo_v1.20.apk](./HorarioLectivo_v1.20.apk)**.
2. Abre el APK desde Android.
3. Autoriza la instalación de aplicaciones desconocidas si Android lo solicita.
4. Confirma la instalación y abre **Horario Lectivo**.

El SHA-256 de la compilación publicada está en [APK_SHA256.txt](./APK_SHA256.txt).

## Compilar desde código fuente

Requisitos:

- JDK 17.
- Android SDK API 36.
- Android Build Tools 36.0.0.
- Gradle 9.5.

El proyecto usa Android Gradle Plugin 9.3.0, `compileSdk 36`, `targetSdk 36` y `minSdk 26`.

```bash
cd HorarioLectivo-Android
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

APK generado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Descarga

- **APK actual:** [HorarioLectivo_v1.20.apk](./HorarioLectivo_v1.20.apk)
- **SHA-256:** [APK_SHA256.txt](./APK_SHA256.txt)
