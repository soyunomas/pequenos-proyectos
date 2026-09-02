# Horario Lectivo para Android

<p align="center">
  <img src="./assets/horario-icon.png" alt="Icono de Horario Lectivo" width="160" />
</p>

Aplicación Android para configurar un horario semanal y consultarlo rápidamente desde un widget de pantalla de inicio.

## ✨ Funciones principales

- Turnos de mañana y tarde independientes, con duración de sesiones y recreos configurables.
- **Franja entre turnos** independiente de mañana/tarde para reuniones, RETA, departamento u otras actividades fuera del turno habitual.
- Los **recreos son asignables por día**: si no tienen actividad conservan el diseño de `RECREO`; si se asigna un módulo muestran sus siglas y su color.
- Editor semanal por casillas, modo bloque y copia de días.
- Modo claro y oscuro con colores diferenciados por asignatura.
- Paleta cerrada de 24 colores; los colores ya ocupados se desactivan.
- Cada asignatura se clasifica como **L** (lectiva) o **C** (complementaria) en la interfaz de configuración.
- Las asignaturas se pueden editar después de crearlas: siglas, nombre, tipo y color. Si cambias las siglas, las casillas ya asignadas se migran automáticamente.
- En **Asignatura activa**, cada botón usa el mismo color configurado para esa asignatura. La selección actual se refuerza con una marca ✓ y un contorno destacado.
- La pantalla de configuración tiene **Guardar** tanto en la parte superior como al final del editor semanal.
- Widget **4×2**, redimensionable a **4×1**.
- **AHORA** muestra las siglas reales de la actividad asignada, también si ocurre durante un recreo o en la franja entre turnos.
- Una lectiva se identifica como `AHORA · L` y una complementaria como `AHORA · C`.
- **SIGUIENTE** muestra la próxima actividad real en orden cronológico, incluida una actividad asignada en un recreo o entre turnos.
- Un recreo sin actividad sigue apareciendo como `RECREO` con tratamiento ámbar.
- Solo cuando no queda ninguna actividad relevante se muestra `FIN DEL DÍA`.
- La actividad actual indica los minutos restantes; la siguiente incluye hora de inicio y cuenta atrás.
- Al tocar el widget se abre el horario completo y una sola pulsación de **Atrás** vuelve al launcher.

## 📲 Instalación

1. Descarga **[HorarioLectivo_v1.19.apk](./HorarioLectivo_v1.19.apk)**.
2. Abre el APK desde Android.
3. Si el sistema lo solicita, autoriza temporalmente la instalación de aplicaciones desconocidas para el navegador o gestor de archivos utilizado.
4. Confirma la instalación y abre **Horario Lectivo**.

El SHA-256 de la compilación publicada está en [APK_SHA256.txt](./APK_SHA256.txt).

## 🧭 Uso

### Configurar franjas horarias

En **Configurar** puedes activar mañana, tarde o ambos turnos y definir comienzo, fin, duración de sesión y recreo.

Además existe **Franja entre turnos**, que se configura de forma independiente. Por defecto cubre `14:00–15:00`, pero puedes cambiar comienzo y fin. No depende de que el turno de mañana o el de tarde esté activo, de modo que una persona con un único turno puede seguir registrando una reunión, RETA, departamento u otra actividad fuera de su horario docente habitual.

Si están activos los dos turnos, la franja entre turnos debe quedar entre el final de la mañana y el comienzo de la tarde para evitar solapamientos.

### Crear y editar asignaturas

Pulsa **+ Añadir asignatura**. El editor separa siglas, nombre y tipo:

1. **Siglas**: de 1 a 3 caracteres, por ejemplo `APW` o `RET`.
2. **Nombre**: por ejemplo `Aplicaciones Web` o `RETA`.
3. **Tipo**: selecciona **L** o **C**. La pantalla muestra la leyenda `L = lectiva · C = complementaria`.

Después de crearla, pulsa **Editar** para cambiar siglas, nombre o tipo. El botón coloreado de las siglas abre el selector de color. Los cambios solo se aplican al confirmar; **Cancelar** conserva los valores anteriores.

### Rellenar la semana

En **Asignatura activa**, cada asignatura aparece con su color real. Al seleccionar una, el botón conserva ese color y muestra ✓ para distinguir qué asignatura se aplicará al tocar la tabla. **BORRAR** mantiene un tratamiento visual independiente.

Las filas normales se rellenan como antes. La franja **ENTRE TURNOS** aparece como una fila propia y admite una asignación distinta para cada día.

Cada recreo también tiene ahora cinco celdas, una por día. Si no asignas nada, la celda mantiene el aspecto ámbar y el texto `RECREO`. Si seleccionas una asignatura y tocas esa celda, el recreo pasa a mostrar las siglas y el color de esa actividad. Selecciona **BORRAR** y toca la celda para recuperar el estado `RECREO`.

Con **Modo bloque** puedes tocar inicio y fin para rellenar un intervalo de sesiones normales. Los recreos se editan individualmente para evitar asignaciones accidentales. **Copiar** duplica también las asignaciones de recreos y de la franja entre turnos.

Cuando termines, puedes pulsar **Guardar** en la cabecera o el segundo botón **Guardar** situado justo debajo de la tabla semanal.

### Widget

El widget usa una lógica secuencial: muestra qué actividad ocurre **AHORA** y cuál es la **SIGUIENTE** actividad relevante.

Si una reunión `RET` está asignada durante el recreo, el widget muestra `RET` y la clasifica como L/C según su configuración; no la sustituye por la palabra `RECREO`. Si el recreo está vacío, sí se muestra `RECREO` con el tratamiento ámbar.

La misma lógica se aplica a la franja entre turnos. Por ejemplo:

```text
AHORA · C
RET                        quedan 18 min
14:00–15:00

SIGUIENTE · L
APW                        en 18 min
Empieza 15:00
```

## 🛠️ Compilar desde código fuente

### Requisitos

- JDK **17**.
- Android SDK **API 36**.
- Android Build Tools **36.0.0**.
- Gradle **9.5**.

El proyecto usa Android Gradle Plugin **9.3.0**, `compileSdk 36`, `targetSdk 36` y `minSdk 26`.

### Android Studio

```bash
git clone https://github.com/soyunomas/pequenos-proyectos.git
```

Abre `pequenos-proyectos/HorarioLectivo-Android` en Android Studio, selecciona JDK 17, sincroniza Gradle y ejecuta la app o usa **Build > Build APK(s)**.

### Terminal

El proyecto no incluye Gradle Wrapper, por lo que `gradle` debe estar instalado:

```bash
cd HorarioLectivo-Android
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

APK generado:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## CI y publicación

GitHub Actions ejecuta tests, lint y compilación y verifica específicamente la franja entre turnos, la persistencia de sus asignaciones, las asignaciones durante recreos y su representación en el widget. Al llegar a `main`, publica el APK versionado en este directorio.

## Compatibilidad

- Android **8.0 (API 26)** o superior.
- Java **17**.
- `compileSdk` / `targetSdk`: **36**.

## Descarga

- **APK actual:** [HorarioLectivo_v1.19.apk](./HorarioLectivo_v1.19.apk)
- **SHA-256:** [APK_SHA256.txt](./APK_SHA256.txt)
