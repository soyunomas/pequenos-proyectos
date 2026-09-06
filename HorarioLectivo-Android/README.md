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
- **Aula/lugar por casilla**: la misma asignatura puede tener un aula distinta según el día y la hora.
- **Franjas exactas importables**: el JSON puede describir horas reales como `09:00–09:55`, `10:00–10:55`, con huecos o duraciones diferentes, sin obligarlas a encajar en sesiones contiguas.
- Modo claro y oscuro.
- Widget 4×2, redimensionable a 4×1, con actividad actual, siguiente actividad y cuenta atrás. Opcionalmente muestra también el aula/lugar de ambas.
- **Copia de seguridad JSON**: exporta toda la configuración y permite importarla después.
- Si todavía no has rellenado el horario, **Exportar JSON** genera igualmente un archivo completo con todas las opciones, el esquema admitido y los arrays `subjects` y `assignments` vacíos. Está pensado también para poder entregárselo a una IA junto con una foto de un horario en papel y pedirle que rellene el JSON sin cambiar su estructura.

## Configurar turnos

En **Configurar horario** puedes activar o desactivar por separado mañana, tarde y noche. Cada turno tiene comienzo, fin y recreo propios.

Los intervalos entre turnos también son independientes:

- **Entre mañana y tarde**: por defecto `14:00–15:00`.
- **Entre tarde y noche**: disponible para cubrir reuniones, RETA, departamento u otras actividades antes del turno nocturno.

En la tabla semanal no hay una banda `ENTRE TURNOS`. En su lugar, la propia fila correspondiente a esas horas usa el mismo tratamiento ámbar que el recreo y muestra `ENTRE` cuando no tiene nada asignado.

## Recreos e intervalos asignables

Selecciona una asignatura y toca una celda de recreo o de intervalo entre turnos para asignarla. Si seleccionas **BORRAR**, la celda vuelve al estado ámbar vacío (`RECREO` o `ENTRE`).

Las asignaciones de estas franjas se incluyen al copiar un día a otro y también se tienen en cuenta en **AHORA** y **SIGUIENTE** del widget.

## Aulas y lugares

El aula pertenece a cada **casilla del horario**, no a la asignatura global. Esto permite que una misma materia use, por ejemplo, `Aula PB.01` en una hora y `Sala 2.4` en otra.

En **Configurar horario > Semana**, mantén pulsada una casilla que ya tenga asignatura para editar su **Aula o lugar**. La tabla muestra el aula debajo de las siglas. Al copiar un día se copian también las aulas.

En **Apariencia** puedes activar o desactivar **Mostrar aula/lugar en el widget**. Cuando está activo y existe un aula definida, el widget la añade a la información de **AHORA** y **SIGUIENTE**.

## Copia de seguridad e importación JSON

En **Configurar horario > Copia de seguridad** hay dos acciones:

- **Exportar JSON** abre el selector de archivos de Android y guarda la configuración actual.
- **Importar JSON** permite seleccionar una copia previa. Antes de sustituir el horario actual se valida el formato y se pide confirmación.

El formato externo está versionado como `horario-lectivo-backup` / esquema `1`. Incluye apariencia, opciones del widget, duración de sesión, mañana, tarde, noche, ambos intervalos entre turnos, recreos, asignaturas, aulas y asignaciones semanales. Cada objeto de `assignments` puede llevar `room`, porque el aula puede variar por día y hora.

La exportación incluye además un bloque `schema` autocontenido. Desde v1.25, la plantilla indica expresamente a una IA que **las horas del documento mandan sobre los valores de ejemplo**. Cada turno puede incluir `slots` exactos con `start`, `end` y `kind` (`CLASS` o `BREAK`). Esto permite horarios con pausas de 5 minutos, duraciones variables u otros formatos.

Si un JSON antiguo no incluye `slots` pero contiene `assignments` con horas que no encajan en el patrón automático, el importador intenta inferir las franjas exactas a partir de `start` y `end` (o de `sessionMinutes` como fallback). Por ejemplo, un horario con `09:00–09:55` y `10:00–10:55` se conserva con el hueco de cinco minutos y no se transforma en una sesión continua. Describe los campos, tipos, rangos, valores permitidos, reglas y ejemplos necesarios para generar un horario válido. Entre otras restricciones, documenta que las siglas `subjects[].code` son obligatorias, únicas, de **1 a 3 caracteres** y solo admiten `A-Z` y `0-9`.

Si la configuración todavía no contiene asignaturas ni casillas, la aplicación propone el nombre **`HorarioLectivo_plantilla_IA.json`** y marca el documento como `contentState: "BLANK_TEMPLATE"`. No es un archivo vacío: contiene todos los turnos y opciones configurables, además de instrucciones `aiInstructions`, definición de campos `fields`, reglas `rules` y ejemplos `examples`; únicamente `subjects` y `assignments` permanecen vacíos para que una IA con visión los complete a partir de una foto.

El JSON no utiliza comentarios `//` o `/* */`, porque dejaría de ser JSON válido. Las explicaciones viven dentro del propio objeto `schema` y la aplicación las ignora al importar.

Desde **v1.23**, la exportación se prepara completamente en memoria antes de abrir el selector de archivos. Tras guardar, la aplicación fuerza la escritura al almacenamiento y vuelve a leer el documento para verificar que no tenga 0 bytes y que coincida exactamente con el JSON generado. Solo entonces muestra el mensaje de exportación correcta, incluyendo el número de bytes guardados.

También se incluye una plantilla lista para usar: **[horario_backup_template.json](./horario_backup_template.json)**.

Una importación rechaza, entre otros casos, códigos de asignatura inválidos o duplicados, asignaciones a materias no declaradas, turnos desconocidos, sesiones inexistentes y configuraciones horarias incoherentes.

## Instalación

1. Descarga **[HorarioLectivo_v1.25.apk](./HorarioLectivo_v1.25.apk)**.
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

- **APK actual:** [HorarioLectivo_v1.25.apk](./HorarioLectivo_v1.25.apk)
- **SHA-256:** [APK_SHA256.txt](./APK_SHA256.txt)
