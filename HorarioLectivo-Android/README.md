# Horario Lectivo para Android

<p align="center">
  <img src="./assets/horario-icon.png" alt="Icono de Horario Lectivo" width="160" />
</p>

Aplicación Android ligera para configurar un horario lectivo semanal y consultarlo rápidamente desde un widget de la pantalla de inicio. Está pensada para mostrar con claridad la clase actual, la siguiente y el horario completo, con soporte para modo claro y oscuro.

## ✨ Funciones principales

- Turno de mañana y/o tarde, con hora de inicio y fin configurables.
- Duración global de las sesiones y recreos configurables.
- Editor semanal tipo “pintar”: selecciona una asignatura y toca las casillas del horario.
- Modo **Bloque** para rellenar varias sesiones consecutivas con dos toques.
- Copia de un día completo a otro día o a toda la semana.
- Vista completa con la franja actual y la siguiente resaltadas.
- Cuadrícula adaptable al ancho real de la pantalla; se centra cuando cabe y permite desplazamiento horizontal solo cuando es necesario.
- Respeto de las barras de estado y navegación de Android mediante `WindowInsets`, evitando solapamientos con hora, batería o botones del sistema.
- Modo claro y oscuro.
- **Selector de color por asignatura** con una paleta cerrada de 24 colores predefinidos.
- Flujo de selección de color **seleccionar → Aceptar**. **Cancelar** conserva el color anterior.
- Los colores ya utilizados por otras asignaturas aparecen desactivados para evitar duplicados mientras haya colores disponibles.
- Widget solicitado como **4×2** y redimensionable a **4×1**.
- En **AHORA** se muestra la clase actual, su franja horaria y los **minutos que quedan** para que termine.
- El marco de **AHORA** cambia a **verde únicamente cuando la franja actual tiene una asignatura/módulo asignado**; si está vacía, es recreo o no hay clase, mantiene el estado rojo.
- En **SIGUIENTE** se muestra la clase siguiente, su hora de comienzo y cuánto falta para que empiece.
- El widget actualiza el tiempo restante de la clase actual por minutos y también se refresca al cambiar de franja.
- Al pulsar el widget se abre el horario completo reutilizando la actividad existente, para que **una sola pulsación de Atrás vuelva directamente a la pantalla de inicio**.
- Icono personalizado de calendario y reloj.

## 📲 Instalación

### Opción recomendada: instalar el APK

1. Descarga la versión actual: **[HorarioLectivo_v1.14.apk](./HorarioLectivo_v1.14.apk)**.
2. Abre el APK desde el navegador o el gestor de archivos de Android.
3. Si Android lo solicita, permite temporalmente **Instalar aplicaciones desconocidas** para la aplicación desde la que abriste el APK.
4. Confirma la instalación.
5. Abre **Horario Lectivo** desde el launcher.

El APK que publica este repositorio se genera mediante el workflow de GitHub Actions y está pensado para instalación directa y pruebas personales. El hash de la compilación publicada está disponible en **[APK_SHA256.txt](./APK_SHA256.txt)**.

### Actualizar desde una versión anterior

Normalmente puedes instalar el APK nuevo encima del anterior y conservar la configuración. Si el launcher mantiene un widget o icono antiguo en caché, elimina el widget de la pantalla de inicio y vuelve a añadirlo después de actualizar.

## 🧭 Cómo se usa

### 1. Configura el horario

Al abrir la aplicación por primera vez se muestra la pantalla de configuración.

1. Elige **modo claro u oscuro**.
2. Define la duración de las sesiones.
3. Configura el turno de **mañana**, **tarde** o ambos.
4. Indica el comienzo, el final y, si corresponde, la posición y duración del recreo.
5. Añade las asignaturas con una abreviatura de hasta 3 caracteres y su nombre completo.
6. Toca la etiqueta de color de una asignatura para abrir la paleta. Selecciona un color disponible y pulsa **Aceptar**. Si pulsas **Cancelar**, no se modifica el color anterior.
7. En **Semana**, selecciona una asignatura y toca las casillas en las que se imparte.
8. Activa **Modo bloque** si quieres marcar un intervalo completo tocando la primera y la última sesión.
9. Usa **Copiar** para duplicar un día en otro o en todos los días.
10. Pulsa **Guardar**.

### 2. Consulta el horario completo

La pantalla principal muestra un resumen de **AHORA** y **SIGUIENTE** y, debajo, la tabla semanal completa. La tabla adapta sus columnas al ancho disponible y resalta la franja actual y la siguiente.

### 3. Añade el widget

1. Mantén pulsada una zona vacía de la pantalla de inicio.
2. Abre el selector de **Widgets** de tu launcher.
3. Busca **Horario Lectivo**.
4. Añádelo a la pantalla de inicio. El tamaño objetivo por defecto es **4×2**.
5. Si quieres una versión más compacta, redimensiónalo a **4×1**. La información se reorganiza para conservar la clase actual y la siguiente.

En **AHORA** se muestra el módulo actual y, por ejemplo, `quedan 37 min`. El borde pasa a verde solo si esa franja tiene un módulo asignado. En **SIGUIENTE** se muestra la asignatura, la hora a la que comienza y una cuenta atrás hasta su inicio. Tocar el widget abre la aplicación y el horario completo; al pulsar **Atrás** una vez vuelves directamente al launcher.

## 🛠️ Compilar desde el código fuente

### Requisitos

- JDK **17**.
- Android SDK con **API 36** y Build Tools **36.0.0**.
- Gradle **9.5** para reproducir el entorno usado en CI.
- Conexión a Internet en la primera compilación para descargar las dependencias de Gradle.

El proyecto usa Android Gradle Plugin **9.3.0**, `compileSdk 36`, `targetSdk 36` y `minSdk 26`.

### Con Android Studio

1. Clona el repositorio:

   ```bash
   git clone https://github.com/soyunomas/pequenos-proyectos.git
   ```

2. En Android Studio selecciona **Open** y abre la carpeta:

   ```text
   pequenos-proyectos/HorarioLectivo-Android
   ```

3. Usa JDK 17 para Gradle y asegúrate de tener instalada la plataforma Android 36.
4. Espera a que finalice la sincronización de Gradle.
5. Ejecuta la aplicación desde Android Studio o usa **Build > Build APK(s)** para generar un APK de depuración.

### Desde terminal

Este proyecto no incluye actualmente Gradle Wrapper, por lo que el comando `gradle` debe estar disponible en el sistema. Para reproducir la compilación del CI:

```bash
cd HorarioLectivo-Android
gradle --no-daemon testDebugUnitTest lintDebug assembleDebug
```

El APK resultante queda en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

También puedes ejecutar únicamente la compilación:

```bash
gradle assembleDebug
```

## CI y publicación

El workflow `.github/workflows/horario-lectivo-android.yml` ejecuta automáticamente tests, `lintDebug`, compilación del APK y comprobaciones específicas del widget, incluyendo el contador de minutos de **AHORA**, el estado verde cuando hay módulo asignado y la navegación desde el widget sin duplicar la actividad. Cuando los cambios llegan a `main`, publica el APK versionado en este directorio.

## Compatibilidad

- Android **8.0 (API 26)** o superior.
- `compileSdk`: **36**.
- `targetSdk`: **36**.
- Java: **17**.

## Descarga

- **APK actual:** [HorarioLectivo_v1.14.apk](./HorarioLectivo_v1.14.apk)
- **SHA-256:** [APK_SHA256.txt](./APK_SHA256.txt)
