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
- Cada asignatura se clasifica como **L** (lectiva) o **C** (complementaria) en la interfaz de configuración, evitando etiquetas largas.
- Las asignaturas se pueden **editar después de crearlas**: siglas, nombre, tipo y color. Si cambias las siglas, las casillas ya asignadas se migran automáticamente al nuevo código.
- Widget **4×2**, redimensionable a **4×1**.
- En **AHORA**, una lectiva usa borde verde; una complementaria se identifica en ámbar y aparece como `COMPLEMENTARIA`; una franja sin actividad mantiene el estado neutro/rojo.
- En **SIGUIENTE LECTIVA**, el widget busca la próxima clase lectiva real, aunque haya complementarias antes.
- Si existen complementarias entre la hora actual y la siguiente lectiva, el widget muestra una nota compacta como `COMPLEMENTARIA ANTES · 13:05–14:00`.
- Si ya no quedan clases lectivas, muestra `NO QUEDAN LECTIVAS HOY`.
- La clase actual indica los minutos restantes; la próxima lectiva incluye hora de inicio y cuenta atrás.
- Al tocar el widget se abre el horario completo y una sola pulsación de **Atrás** vuelve al launcher.

## 📲 Instalación

1. Descarga **[HorarioLectivo_v1.16.apk](./HorarioLectivo_v1.16.apk)**.
2. Abre el APK desde Android.
3. Si el sistema lo solicita, autoriza temporalmente la instalación de aplicaciones desconocidas para el navegador o gestor de archivos utilizado.
4. Confirma la instalación y abre **Horario Lectivo**.

El SHA-256 de la compilación publicada está en [APK_SHA256.txt](./APK_SHA256.txt).

## 🧭 Uso

### Configurar turnos

En **Configurar** puedes activar mañana, tarde o ambos turnos, definir comienzo y fin, duración de sesión y recreo.

### Crear y editar asignaturas

Pulsa **+ Añadir asignatura**. El editor presenta tres decisiones separadas para reducir errores:

1. **Siglas**: de 1 a 3 caracteres, por ejemplo `APW`.
2. **Nombre**: por ejemplo `Aplicaciones Web`.
3. **Tipo**: selecciona **L** o **C**. La propia pantalla muestra la leyenda `L = lectiva · C = complementaria`.

**L** corresponde a una clase docente que debe aparecer como próxima clase en el widget. **C** corresponde a una guardia, reunión u otra franja no lectiva.

Después de crearla, pulsa **Editar** en su fila para cambiar siglas, nombre o tipo. En el listado, el tipo se muestra únicamente como **L** o **C**, con descripción de accesibilidad completa. El botón coloreado de las siglas abre el selector de color. Los cambios solo se aplican al confirmar; **Cancelar** conserva los valores anteriores.

### Rellenar la semana

Selecciona una asignatura en **Asignatura activa** y toca las casillas. Con **Modo bloque** puedes tocar inicio y fin para rellenar un intervalo. **Copiar** duplica un día en otro o en todos.

### Widget 4×2

El widget prioriza dos preguntas: qué ocurre **AHORA** y cuál es la **SIGUIENTE LECTIVA**. Una complementaria futura no sustituye a la próxima lectiva: aparece como contexto intermedio.

### Widget 4×1

Al reducirlo a una fila conserva la misma semántica con tipografía más compacta y dos zonas horizontales: **AHORA** a la izquierda y **PRÓX. LECTIVA** a la derecha. La existencia de complementarias intermedias se muestra en una línea breve de contexto.

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

GitHub Actions ejecuta tests, lint y compilación, y verifica específicamente la clasificación lectiva/complementaria, las abreviaturas **L/C**, la búsqueda de la próxima lectiva, la edición de asignaturas y los layouts del widget. Al llegar a `main`, publica el APK versionado en este directorio.

## Compatibilidad

- Android **8.0 (API 26)** o superior.
- Java **17**.
- `compileSdk` / `targetSdk`: **36**.

## Descarga

- **APK actual:** [HorarioLectivo_v1.16.apk](./HorarioLectivo_v1.16.apk)
- **SHA-256:** [APK_SHA256.txt](./APK_SHA256.txt)
