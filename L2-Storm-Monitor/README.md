# L2 Network Storm Monitor

Este proyecto convierte un microcontrolador económico **RTL8720DN** (como el B&T BW16) en un detector de "tormentas de red" o bucles de Capa 2. Es una herramienta de bajo coste para administradores de red que ayuda a identificar visualmente cuándo la red está saturada por tráfico anómalo (típicamente tormentas de broadcast o ARP).

## ✨ Características

*   **Detección Dual:** Monitorea tanto tormentas específicas de ARP como inundaciones de tráfico general.
*   **Alerta Visual:** Utiliza un LED RGB para mostrar el estado del sistema de un vistazo.
*   **Altamente Configurable:** Los umbrales de detección se pueden ajustar fácilmente para diferentes tipos de redes (hogar, oficina, industrial).
*   **Autónomo:** Una vez configurado, solo necesita alimentación para funcionar.
*   **Resistente:** Incluye un Watchdog Timer para reiniciarse automáticamente si el procesador se bloquea.

## 🛠️ Hardware Necesario

*   **Placa de desarrollo RTL8720DN** (Ej: B&T BW16).
*   **LED RGB** (compatible con ánodo común o cátodo común).
*   Fuente de alimentación Micro-USB.

## ⚙️ ¿Cómo Funciona?

1.  El dispositivo se conecta a tu red Wi-Fi existente.
2.  Pone su tarjeta de red en **modo promiscuo**, lo que le permite "escuchar" todo el tráfico que pasa por el aire, no solo el que va dirigido a él.
3.  Cada segundo, cuenta dos cosas:
    *   El número de **paquetes ARP**.
    *   El número **total de paquetes** de cualquier tipo.
4.  Compara estos contadores con los umbrales definidos (`ARP_THRESHOLD` y `TOTAL_THRESHOLD`).
5.  Si algún contador supera su umbral, activa el estado de **ALERTA** (LED rojo parpadeante). De lo contrario, permanece en estado normal.

## 🔧 Configuración del Código

Para adaptar el monitor a tu red, solo necesitas modificar unas pocas líneas en la parte superior del archivo principal.

### 1. Credenciales Wi-Fi

Introduce el nombre (SSID) y la contraseña de la red Wi-Fi a la que se conectará el dispositivo para monitorear.

```cpp
// 1. CREDENCIALES
char AP_SSID[] = "EL_NOMBRE_DE_TU_WIFI";
char AP_PASS[] = "LA_CONTRASEÑA_DE_TU_WIFI";
```

### 2. Tipo de LED (Opcional)

Si tu LED RGB es de **ánodo común**, cambia `false` por `true`. Si no sabes cuál es, prueba con `false` primero. Si los colores se ven invertidos, cámbialo a `true`.

```cpp
// 2. CONFIGURACIÓN
const bool COMMON_ANODE = false; // cambiar a 'true' si usas un LED de ánodo común
```

### 3. Umbrales de Detección (¡Lo más importante!)

Esta es la configuración clave. Ajústala según el tamaño y el tipo de tu red para evitar falsas alarmas o para que la detección sea más sensible.

```cpp
#define MONITOR_WINDOW  1000   // Ventana de tiempo en ms para contar (1 seg). No tocar.
#define ARP_THRESHOLD   80     // Límite de paquetes ARP por segundo.
#define TOTAL_THRESHOLD 300    // Límite total de paquetes por segundo.
```

**Guía rápida para ajustar los umbrales:**

*   **Escenario: Casa o Pequeña Oficina (Poco tráfico)**
    *   El tráfico es bajo. Quieres detectar problemas rápido.
    *   `ARP_THRESHOLD`: **40**
    *   `TOTAL_THRESHOLD`: **150**

*   **Escenario: Oficina Grande / Universidad (Mucho ruido)**
    *   Hay muchas impresoras y ordenadores "hablando" a la vez. Necesitas umbrales altos para no tener falsas alarmas.
    *   `ARP_THRESHOLD`: **150**
    *   `TOTAL_THRESHOLD`: **600**

*   **Escenario: Industrial / IoT (Tráfico muy predecible)**
    *   Los sensores hablan poco. Cualquier pico es sospechoso.
    *   `ARP_THRESHOLD`: **30**
    *   `TOTAL_THRESHOLD`: **100**

## 🚦 Indicadores del LED

El LED RGB te informa del estado del monitor en tiempo real.

| Color | Patrón | Significado |
| :--- | :--- | :--- |
| **Verde** | Fijo | Arrancando / Conectando a la red Wi-Fi. |
| **Apagado** | N/A | Operación Normal. La red está estable. |
| **Rojo** | Parpadeo rápido | **¡ALERTA!** Tormenta de red detectada. |

> **Nota**: El código original tiene una animación de "pulso de vida" cian/azul para el estado normal que está deshabilitada por defecto. Si prefieres un indicador visual constante, puedes reactivarlo en la función `update_led_heartbeat()`.

## 🚀 Instalación

1.  Instala el soporte para placas **RTL8720DN** en tu IDE de Arduino.
2.  Abre el archivo `.ino` del proyecto.
3.  Modifica la sección de **Configuración** como se describe arriba.
4.  Conecta tu placa al ordenador.
5.  Selecciona la placa y el puerto correctos en el menú `Herramientas`.
6.  Haz clic en `Subir` para flashear el firmware en el dispositivo.
