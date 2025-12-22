# 🌊 InputFlow

![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)

**High-Performance Hardware Event Mapper for Linux**

InputFlow es un demonio escrito en **Go (Golang)** diseñado para interceptar eventos de hardware de bajo nivel (Joysticks, Ratones, Teclados, Pedales) directamente desde el Kernel de Linux (`/dev/input/`) y mapearlos a comandos del sistema con **latencia cero**.

A diferencia de otras herramientas, InputFlow no depende de librerías pesadas, soporta "Hot-Pluggable" (reconectar dispositivos sin reiniciar) y ofrece un motor lógico avanzado para filtrar ruido analógico.

---

## 🚀 Características

*   **⚡ Zero-Latency:** Lee directamente los bytes crudos (`evdev`) del Kernel.
*   **🎮 Soporte Universal:**
    *   **Botones:** Detecta pulsación (`press`), soltar (`release`) y mantener (`hold`).
    *   **Ejes Analógicos:** Soporte para Joysticks con zonas muertas (`deadzones`) configurables.
    *   **Movimiento Relativo:** Soporte para Ruedas de ratón (Scroll) y gestos de movimiento.
*   **🛠️ Scanner Integrado:** Herramienta CLI que detecta tu hardware y **escribe la configuración por ti**.
*   **Noise-Cancelling:** Algoritmos matemáticos para filtrar el "jitter" de mandos analógicos baratos.

---

## 📦 Instalación

### Prerrequisitos
*   Linux (Cualquier distribución).
*   Go 1.20+ instalado (para compilar).

### Compilación
Clona el repositorio y compila el binario estático:

```bash
cd ~/GIT/InputFlow
go mod tidy
go build -ldflags="-s -w" -o inputflow cmd/inputflow/main.go
```

Esto generará el ejecutable `inputflow` en la carpeta actual.

---

## ⚙️ Guía de Uso Rápida

El flujo de trabajo se divide en tres pasos: **Escanear -> Configurar -> Ejecutar**.

### 1. Escanear Hardware (Scanner)
No necesitas adivinar los códigos de los botones. Usa el modo `-scan`.

**Listar dispositivos conectados:**
```bash
sudo ./inputflow -scan
```

**Escanear un dispositivo específico:**
Copia el ID que te dio el comando anterior y ejecuta:

```bash
sudo ./inputflow -scan -dev [TU_ID_AQUI]
```

*   Presiona botones, mueve palancas o gira la rueda del ratón.
*   La terminal te mostrará un bloque de configuración **TOML sugerido**.
*   Copialo.

### 2. Configuración (`config.toml`)
Crea o edita el archivo `config.toml` en la misma carpeta. Pega los bloques que obtuviste del escáner.

#### Ejemplo Completo:
```toml
[[device]]
name = "Mi Mando Personalizado"
# El ID debe coincidir exactamente con el nombre en /dev/input/by-id/
match_id = "usb-Vendor_Product_ID-event-joystick"

    # --- BOTONES (Teclas, Clicks) ---
    [device.buttons.288]
    mode = "press"          # Opciones: press, release, hold
    exec = "firefox"        # Comando a ejecutar

    [device.buttons.298]
    mode = "hold"           # Requiere mantener pulsado 600ms
    exec = "poweroff"       # Ideal para acciones peligrosas

    # --- EJES ANALÓGICOS (Joysticks, Crucetas) ---
    [device.axes.1]
    deadzone = 0.5          # Ignorar el 50% central (elimina ruido/drift)
    on_low = "logger ARRIBA"
    on_high = "logger ABAJO"

    # --- EJES RELATIVOS (Rueda del Ratón, Trackball) ---
    [device.rel_axes.8]     # Código 8 suele ser la Rueda Vertical
    threshold = 1           # Sensibilidad (1 para scroll, +50 para movimiento ratón)
    on_pos = "exec: pactl set-sink-volume @DEFAULT_SINK@ +5%" # Subir vol
    on_neg = "exec: pactl set-sink-volume @DEFAULT_SINK@ -5%" # Bajar vol
```

### 3. Ejecutar (Engine)
Lanza el programa. Necesita `sudo` para leer `/dev/input/`.

```bash
sudo ./inputflow
```

Si todo va bien, verás: `🚀 Engine Ready: [Nombre de tu dispositivo]`.

---

## 📝 Referencia de Configuración

### Modos de Botón (`mode`)
| Modo | Descripción |
| :--- | :--- |
| `"press"` | Se ejecuta instantáneamente al pulsar. (Por defecto). |
| `"release"` | Se ejecuta al **soltar** el botón. Útil para mecánicas de arrastre. |
| `"hold"` | Se ejecuta solo si mantienes el botón pulsado durante **600ms**. |

### Tipos de Ejes
*   **`axes` (Absoluto):** Para Joysticks y Crucetas de mandos. Tienen un centro y extremos. Requieren `deadzone`.
*   **`rel_axes` (Relativo):** Para Ratones y Ruedas. No tienen fin, solo envían "deltas" (+1, -1). Requieren `threshold`.

---

## 🔧 Solución de Problemas Frecuentes

### "Connection Refused" al controlar Audio/PulseAudio
Si ejecutas `inputflow` con `sudo`, el programa corre como **root**. Por seguridad, Linux impide que **root** controle el audio del usuario normal.

**Solución:**
En el `config.toml`, usa `runuser` para ejecutar el comando como tu usuario:

```toml
[device.rel_axes.8]
threshold = 1
# Cambia 'tu_usuario' por tu nombre real
on_pos = "runuser -u tu_usuario -- pactl set-sink-volume @DEFAULT_SINK@ +5%"
```

---

## 👻 Instalación como Servicio (Opcional)

Si quieres que `inputflow` se ejecute solo al encender el PC sin tener que abrir una terminal.

1.  Crea el archivo de servicio:
    ```bash
    sudo nano /etc/systemd/system/inputflow.service
    ```

2.  Pega este contenido (Ajusta la ruta `/home/tu_usuario/GIT...`):
    ```ini
    [Unit]
    Description=InputFlow Daemon
    After=network.target

    [Service]
    Type=simple
    User=root
    WorkingDirectory=/home/tu_usuario/GIT/InputFlow
    ExecStart=/home/tu_usuario/GIT/InputFlow/inputflow
    Restart=always

    [Install]
    WantedBy=multi-user.target
    ```

3.  Actívalo:
    ```bash
    sudo systemctl enable inputflow
    sudo systemctl start inputflow
    ```

---

## 📄 Licencia

Este proyecto está bajo la Licencia **MIT**.

