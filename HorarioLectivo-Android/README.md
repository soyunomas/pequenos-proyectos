# Horario Lectivo para Android

<p align="center">
  <img src="./assets/horario-icon.png" alt="Icono de Horario Lectivo" width="160" />
</p>

Aplicación Android para configurar un horario semanal y consultarlo rápidamente desde un widget de pantalla de inicio.

## ✨ Funciones principales

- Turnos de mañana y tarde, duración de sesiones y recreos configurables.
- Editor semanal por casillas, modo bloque y copia de días.
- Modo claro y oscuro con colores diferenciados por asignatura.
- Paleta cerrada de 24 colores; los colores ya ocupados se desactivan.
- Cada asignatura se clasifica como **L** (lectiva) o **C** (complementaria) en la interfaz de configuración.
- Las asignaturas se pueden editar después de crearlas: siglas, nombre, tipo y color. Si cambias las siglas, las casillas ya asignadas se migran automáticamente.
- En **Asignatura activa**, cada botón usa el mismo color configurado para esa asignatura. La selección actual se refuerza con una marca ✓ y un contorno destacado.
- La pantalla de configuración tiene **Guardar** tanto en la parte superior como al final del editor semanal, para evitar tener que volver arriba después de editar el horario.
- Widget **4×2**, redimensionable a **4×1**.
- **AHORA** muestra siempre las siglas reales de la actividad asignada. Una lectiva se identifica como `AHORA · L` y una complementaria como `AHORA · C`.
- Las complementarias muestran sus siglas configuradas, por ejemplo `GU`, `REU` o `JD`.
- **SIGUIENTE** muestra la próxima actividad real en orden cronológico, sea lectiva o complementaria.
- El tipo de la siguiente actividad se indica de forma compacta como `SIGUIENTE · L` o `SIGUIENTE · C`.
- Solo cuando no queda ninguna actividad asignada se muestra `FIN DEL DÍA`.
- La actividad actual indica los minutos restantes; la siguiente incluye hora de inicio y cuenta atrás.
- Al tocar el widget se abre el horario completo y una sola pulsación de **Atrás** vuelve al launcher.

## 📲 Instalación

1. Descarga **[HorarioLectivo_v1.18.apk](./HorarioLectivo_v1.18.apk)**.
2. Abre el APK desde Android.
3. Si el sistema lo solicita, autoriza temporalmente la instalación de aplicaciones desconocidas para el navegador o gestor de archivos utilizado.
4. Confirma la instalación y abre **Horario Lectivo**.

El SHA-256 de la compilación publicada está en [APK_SHA256.txt](./APK_SHA256.txt).

## 🧭 Uso

### Configurar turnos

En **Configurar** puedes activar mañana, tarde o ambos turnos, definir comienzo y fin, duración de sesión y recreo.

### Crear y editar asignaturas

Pulsa **+ Añadir asignatura**. El editor separa siglas, nombre y tipo:

1. **Siglas**: de 1 a 3 caracteres, por ejemplo `APW`.
2. **Nombre**: por ejemplo `Aplicaciones Web`.
3. **Tipo**: selecciona **L** o **C**. La pantalla muestra la leyenda `L = lectiva · C = complementaria`.

Después de crearla, pulsa **Editar** para cambiar siglas, nombre o tipo. El botón coloreado de las siglas abre el selector de color. Los cambios solo se aplican al confirmar; **Cancelar** conserva los valores anteriores.

### Rellenar la semana

En **Asignatura activa**, cada asignatura aparece con su color real. Al seleccionar una, el botón conserva ese color y muestra ✓ para distinguir claramente qué asignatura se aplicará al tocar la tabla. **BORRAR** mantiene un tratamiento visual independiente.

Con **Modo bloque** puedes tocar inicio y fin para rellenar un intervalo. **Copiar** duplica un día en otro o en todos.

Cuando termines, puedes pulsar **Guardar** en la cabecera o el segundo botón **Guardar** situado justo debajo de la tabla semanal.

### Widget

El widget usa una lógica secuencial: muestra qué actividad ocurre **AHORA** y cuál es la **SIGUIENTE** actividad asignada, independientemente de que sea lectiva o complementaria.

Ejemplo:

```text
AHORA · C
GU                         quedan 18 min
12:10–13:05

SIGUIENTE · C
REU                        en 18 min
Empieza 13:05
```

Al comenzar `REU`, el widget vuelve a calcular automáticamente la siguiente actividad. Si después hay una lectiva `APW`, pasará a mostrar `SIGUIENTE · L` y `APW`.

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

GitHub Actions ejecuta tests, lint y compilación y verifica específicamente los colores del selector de asignaturas, el estado seleccionado y el botón Guardar inferior. Al llegar a `main`, publica el APK versionado en este directorio.

## Compatibilidad

- Android **8.0 (API 26)** o superior.
- Java **17**.
- `compileSdk` / `targetSdk`: **36**.

## Descarga

- **APK actual:** [HorarioLectivo_v1.18.apk](./HorarioLectivo_v1.18.apk)
- **SHA-256:** [APK_SHA256.txt](./APK_SHA256.txt)
