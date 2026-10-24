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
- **Aula general por asignatura** con excepciones por casilla: la materia hereda su aula habitual y solo necesitas editar las horas que se impartan en otro lugar.
- **Grupo por casilla**: cada hora puede indicar grupo o subgrupo (`PA101`, `PE102`, `PE101 / TU101`, etc.) sin asumir una nomenclatura concreta.
- **Franjas exactas importables**: el JSON puede describir horas reales como `09:00–09:55`, `10:00–10:55`, con huecos o duraciones diferentes, sin obligarlas a encajar en sesiones contiguas.
- Modo claro y oscuro.
- Widget 4×2, redimensionable a 4×1, con actividad actual, siguiente actividad y cuenta atrás. Opcionalmente muestra también el aula/lugar de ambas.
- **Copia de seguridad JSON**: exporta toda la configuración y permite importarla después.
- Si todavía no has rellenado el horario, **Exportar JSON** genera igualmente un archivo completo con todas las opciones, el esquema admitido y los arrays `subjects` y `assignments` vacíos. Está pensado también para poder entregárselo a una IA junto con una foto de un horario en papel y pedirle que rellene el JSON sin cambiar su estructura.

## Recordatorios y alarmas

Desde **Avisos**, en la pantalla principal, puedes crear, editar y borrar recordatorios independientes del horario. Cada recordatorio tiene título, fecha y hora, notas de hasta **5000 caracteres** y sonido opcional. El aviso aparece como una **notificación de Android ampliable** y al pulsarlo se abre el texto completo. Los avisos se guardan en el propio dispositivo y se reprograman al reiniciarlo o cambiar la hora.

En Android 13 o superior, permite el acceso a notificaciones. Para precisión horaria, Android 12 o superior puede requerir activar **Alarmas y recordatorios** desde la propia pantalla de avisos. Sin este permiso se programa una alternativa que el sistema puede demorar; el ahorro de batería y los ajustes del fabricante también pueden afectar la entrega. El sonido depende de los canales de notificación de Android y se puede silenciar desde Ajustes del sistema. Los recordatorios no se incluyen en las copias JSON del horario.

### Avisos directamente desde las casillas del horario

En la pantalla principal, **toca cualquier casilla del horario**, incluidas las vacías, los recreos y los intervalos entre turnos. La ficha muestra los datos de la franja y sus recordatorios pendientes, además del historial de avisos vinculados. Desde esa misma ficha puedes **añadir, leer completos, editar o eliminar** los avisos.

El formulario propone un aviso **10 minutos antes del inicio de la próxima ocurrencia** de esa hora semanal (un solo aviso, no se repite automáticamente). Puedes modificar título, texto (hasta 5000 caracteres), fecha, hora y sonido. Los avisos de una casilla quedan vinculados a su identidad semanal (día, turno y número de franja); si cambias radicalmente el horario, revisa los avisos asociados. Los recordatorios independientes de versiones anteriores permanecen en **Avisos**, sin asociación automática a ninguna casilla.

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

Cada asignatura puede tener un **aula general**. Se configura desde **Configurar horario > Asignaturas > Editar** y se hereda automáticamente en todas sus casillas.

Si una hora concreta se imparte excepcionalmente en otra aula, mantén pulsada esa casilla en **Semana** y define una **Aula excepcional**. La excepción tiene prioridad únicamente en esa casilla; **Usar aula general** elimina la excepción y vuelve a heredar el valor habitual.

La tabla prioriza la lectura en este orden: **siglas → grupo → aula**. El grupo es opcional y aparece justo debajo de las siglas; el aula queda como información terciaria. Al copiar un día se copian también grupos y excepciones de aula.

En **Apariencia** puedes activar o desactivar **Mostrar aula/lugar en el widget**. Cuando está activo, el widget utiliza el aula efectiva: primero la excepción de esa casilla y, si no existe, el aula general de la asignatura.

En **Configurar horario > Semana**, mantén pulsada una casilla asignada para abrir **Detalles de esta hora**. Desde un único diálogo puedes editar el grupo y el aula excepcional, evitando repartir dos acciones relacionadas en gestos distintos.

En la pantalla de consulta, tocar una casilla con asignatura abre una ficha con animación de aparición/zoom. Muestra siglas, nombre completo, tipo L/C, día, hora, turno, **grupo**, aula efectiva y, cuando corresponde, diferencia entre **aula excepcional** y **aula habitual**.

## Copia de seguridad e importación JSON

En **Configurar horario > Copia de seguridad** hay dos acciones:

- **Exportar JSON** abre el selector de archivos de Android y guarda la configuración actual.
- **Importar JSON** permite seleccionar una copia previa. Antes de sustituir el horario actual se valida el formato y se pide confirmación.

El formato externo está versionado como `horario-lectivo-backup` / esquema `1`. Cada objeto de `subjects` puede llevar `defaultRoom` como aula habitual. Cada `assignment.room` representa una **excepción** para esa casilla; si está vacío, se hereda `defaultRoom`. Cada `assignment.group` guarda opcionalmente el grupo o subgrupo de esa franja y admite texto libre de hasta 80 caracteres, incluidos varios grupos en una misma casilla.

La exportación incluye además un bloque `schema` autocontenido. Desde v1.25, la plantilla indica expresamente a una IA que **las horas del documento mandan sobre los valores de ejemplo**. Cada turno puede incluir `slots` exactos con `start`, `end` y `kind` (`CLASS` o `BREAK`). Esto permite horarios con pausas de 5 minutos, duraciones variables u otros formatos.

Si un JSON antiguo no incluye `slots` pero contiene `assignments` con horas que no encajan en el patrón automático, el importador intenta inferir las franjas exactas a partir de `start` y `end` (o de `sessionMinutes` como fallback). Por ejemplo, un horario con `09:00–09:55` y `10:00–10:55` se conserva con el hueco de cinco minutos y no se transforma en una sesión continua. Describe los campos, tipos, rangos, valores permitidos, reglas y ejemplos necesarios para generar un horario válido. Entre otras restricciones, documenta que las siglas `subjects[].code` son obligatorias, únicas, de **1 a 3 caracteres** y solo admiten `A-Z` y `0-9`.

Si la configuración todavía no contiene asignaturas ni casillas, la aplicación propone el nombre **`HorarioLectivo_plantilla_IA.json`** y marca el documento como `contentState: "BLANK_TEMPLATE"`. No es un archivo vacío: contiene todos los turnos y opciones configurables, además de instrucciones `aiInstructions`, definición de campos `fields`, reglas `rules` y ejemplos `examples`; únicamente `subjects` y `assignments` permanecen vacíos para que una IA con visión los complete a partir de una foto.

El JSON no utiliza comentarios `//` o `/* */`, porque dejaría de ser JSON válido. Las explicaciones viven dentro del propio objeto `schema` y la aplicación las ignora al importar.

Desde **v1.23**, la exportación se prepara completamente en memoria antes de abrir el selector de archivos. Tras guardar, la aplicación fuerza la escritura al almacenamiento y vuelve a leer el documento para verificar que no tenga 0 bytes y que coincida exactamente con el JSON generado. Solo entonces muestra el mensaje de exportación correcta, incluyendo el número de bytes guardados.

También se incluye una plantilla lista para usar: **[horario_backup_template.json](./horario_backup_template.json)**.

Una importación rechaza, entre otros casos, códigos de asignatura inválidos o duplicados, asignaciones a materias no declaradas, turnos desconocidos, sesiones inexistentes y configuraciones horarias incoherentes.

## Instalación

1. Descarga **[HorarioLectivo_v1.29.apk](./HorarioLectivo_v1.29.apk)**.
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

- **APK actual:** [HorarioLectivo_v1.29.apk](./HorarioLectivo_v1.29.apk)
- **SHA-256:** [APK_SHA256.txt](./APK_SHA256.txt)
