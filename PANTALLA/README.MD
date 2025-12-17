**GoKVM** es un KVM (Keyboard, Video, Mouse) por software ligero, seguro y rápido escrito en Go. Permite controlar múltiples computadoras utilizando un único teclado y mouse, compartiendo el portapapeles (texto e imágenes) entre ellas sin problemas.

Está diseñado con un enfoque en la **baja latencia** (protocolo binario custom), la **seguridad** (TLS 1.3 + Token de Autenticación) y la **facilidad de uso** (configuración simple y System Tray).

## ✨ Características

*   🚀 **Baja Latencia:** Protocolo binario personalizado sobre TCP (NoDelay) para movimiento de mouse fluido.
*   🔒 **Seguridad Total:** Todo el tráfico está encriptado con TLS 1.3.
*   🔑 **Autenticación:** Sistema de tokens para prevenir conexiones no autorizadas.
*   📋 **Portapapeles Sincronizado:** Copia texto e imágenes entre computadoras.
*   📦 **Compresión Inteligente:** Las imágenes se comprimen (Zlib) antes de enviarse para no saturar la red.
*   🖱️ **Transición Fluida:** Mueve el mouse al borde de tu pantalla para saltar a la otra PC.
*   🔄 **Resiliencia:** El cliente se reconecta automáticamente si el servidor se reinicia o la red falla.
*   💻 **Multiplataforma:** Soporte probado en Linux y Windows (macOS experimental).
*   Tray Icon: Icono en la barra de tareas para ver el estado y cerrar la aplicación limpiamente.

---

## 🛠️ Requisitos Previos

Necesitas tener instalado **Go (Golang) 1.20+** y un compilador de C (`gcc`) ya que el proyecto utiliza CGO para controlar los inputs del sistema.

### 🐧 Linux (Ubuntu/Debian)
Necesitas las librerías de desarrollo para el portapapeles y la interfaz gráfica:

```bash
sudo apt-get update
sudo apt-get install gcc libx11-dev xorg-dev libxtst-dev libpng-dev libxkbcommon-x11-dev
# Para la interfaz de sistema (System Tray):
sudo apt-get install libgtk-3-dev libayatana-appindicator3-dev
```

### 🪟 Windows
*   Instalar [TDM-GCC](https://jmeubank.github.io/tdm-gcc/) (necesario para CGO).
*   Instalar Go.

---

## ⚙️ Instalación y Compilación

El proyecto incluye un `Makefile` para facilitar la compilación.

1.  **Clonar el repositorio:**
    ```bash
    git clone https://github.com/tu-usuario/gokvm.git
    cd gokvm
    ```

2.  **Descargar dependencias:**
    ```bash
    make dep
    ```

3.  **Compilar para tu sistema actual:**
    ```bash
    make build
    ```
    *Esto generará los binarios en la carpeta `/bin` (`server` y `client`).*

4.  **Compilación Cruzada (Opcional):**
    Si estás en Linux y quieres generar el `.exe` para Windows:
    ```bash
    make build-windows
    ```

---

## 📝 Configuración

La primera vez que ejecutes el programa, se generará automáticamente un archivo `config.json`. **Debes editarlo para emparejar las computadoras.**

### Estructura de `config.json`

```json
{
  "server": {
    "client_edge": "right",          
    "port": ":8080",
  },
  "client": {
    "server_ip": "192.168.1.50:8080", 
    "name": "Laptop-Trabajo",
    "auth_token": "MI_SECRETO_SUPER_SEGURO"
  }
}
```

*   **Server (PC Principal - Mouse/Teclado físico):**
    *   `client_edge`: Define dónde está la otra pantalla. Usa `"right"` (derecha) o `"left"` (izquierda).
    *   `auth_token`: Contraseña que debe coincidir con la del cliente.
*   **Client (PC Remota):**
    *   `server_ip`: La dirección IP de la PC Servidor.
    *   `auth_token`: Debe ser idéntico al del servidor.

---

## 🚀 Uso

### 1. Iniciar el Servidor (PC Principal)
Ejecuta el binario o usa Make:
```bash
make run-server
# O manualmente: ./bin/server
```
*   Verás un icono en la barra de tareas.
*   Se generarán certificados TLS (`cert.pem`, `key.pem`) automáticamente.
*   Asegúrate de permitir el puerto `8080` en tu Firewall.

### 2. Iniciar el Cliente (PC Secundaria)
Copia el binario `client` (y el `config.json` configurado) a la otra computadora y ejecútalo:
```bash
make run-client
# O manualmente: ./bin/client
```
*   El cliente intentará conectarse cada 3 segundos hasta tener éxito.

### 3. ¡Controlar! 🖱️
1.  Mueve el mouse de tu PC Servidor hacia el borde configurado (ej. Derecha).
2.  El mouse "saltará" a la pantalla del Cliente.
3.  Ahora tu teclado y mouse controlan la PC Cliente.
4.  Copia texto o una imagen en el Cliente, espera un segundo y pégalo en el Servidor (y viceversa).
5.  Para volver, mueve el mouse del Cliente hacia el borde contrario (hacia el Servidor).

---

## ⚠️ Solución de Problemas

**1. El mouse se mueve lento o con lag.**
*   Asegúrate de estar en una red local (LAN), preferiblemente por cable Ethernet. El WiFi puede introducir latencia.

**2. Error al compilar en Linux: `fatal error: gtk/gtk.h: No such file...`**
*   Te faltan las librerías de desarrollo. Ejecuta: `sudo apt-get install libgtk-3-dev libayatana-appindicator3-dev`.

**3. No puedo copiar imágenes grandes.**
*   El límite actual de seguridad es de 50MB. Imágenes muy grandes pueden tardar unos segundos en comprimirse y enviarse.

**4. Linux: Wayland vs X11.**
*   Esta herramienta utiliza `robotgo` que funciona nativamente en **X11**.
*   Si usas Wayland (por defecto en Ubuntu reciente), puede que necesites cerrar sesión y elegir "Ubuntu on Xorg" en la pantalla de login, o configurar permisos especiales.

---

## 📜 Licencia

Este proyecto es de código abierto. Siéntete libre de modificarlo y mejorarlo.
