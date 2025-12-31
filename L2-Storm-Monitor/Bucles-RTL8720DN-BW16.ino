/*
 * L2 Network Storm Monitor
 * Hardware: RTL8720DN / BW16
 */

#include <WiFi.h>
#include <stdint.h>
#include <stdio.h>

extern "C" {
    #include "wifi_conf.h"
    #include "wifi_constants.h"
    
    typedef struct {
       unsigned char *buf;
       unsigned int len;
       void* user_data;
    } rtw_packet_info_t;
}

// ----------------------------------------------------------------------
// 1. CREDENCIALES
// ----------------------------------------------------------------------
char AP_SSID[] = "AP_SSID";
char AP_PASS[] = "AP_PASSWORD";

// ----------------------------------------------------------------------
// 2. CONFIGURACIÓN
// ----------------------------------------------------------------------
const bool COMMON_ANODE = false; 

/*
 * ===================================================================================
 * GUÍA RÁPIDA DE CONFIGURACIÓN DE UMBRALES (THRESHOLDS)
 * El led RGB integrado cambia a rojo intenso en caso de detección de bucle
 * Cuando hay bucle al no poderse conectar, pone el led en verde.
 * Cambia los valores de abajo según tu entorno:
 * ===================================================================================
 * 
 * 1. Escenario: Casa o Pequeña Oficina (Poco tráfico)
 *    - Descripción: El tráfico es bajo. Quieres detectar problemas rápido.
 *    - ARP_THRESHOLD:   40
 *    - TOTAL_THRESHOLD: 150
 *
 * 2. Escenario: Oficina Grande / Universidad (Mucho ruido)
 *    - Descripción: Hay muchas impresoras y ordenadores "hablando" a la vez. 
 *      Necesitas umbrales altos para no tener falsas alarmas.
 *    - ARP_THRESHOLD:   150
 *    - TOTAL_THRESHOLD: 600
 *
 * 3. Escenario: Industrial / IoT (Tráfico muy predecible)
 *    - Descripción: Los sensores hablan poco. Cualquier pico es sospechoso.
 *    - ARP_THRESHOLD:   30
 *    - TOTAL_THRESHOLD: 100
 * ===================================================================================
 */

// Ventana de tiempo (en milisegundos) para contar paquetes.
// 1000 = 1 segundo. Si bajas a 500, la detección es más rápida pero más nerviosa.
#define MONITOR_WINDOW  1000

// Límite de paquetes ARP por segundo (Protocolo que suele causar bucles).
// Si supera esto, se marca como tormenta. (Ver guía arriba).
#define ARP_THRESHOLD   80

// Límite total de paquetes por segundo (Detecta inundaciones de video o broadcast).
// Si supera esto, se marca como tormenta. (Ver guía arriba).
#define TOTAL_THRESHOLD 300

// Watchdog Timer: Si el sistema se bloquea por la tormenta, se reinicia a los 8 segundos.
#define WDT_TIMEOUT     8000

// ----------------------------------------------------------------------
// 3. ESTRUCTURAS & CONSTANTES
// ----------------------------------------------------------------------
#define ETH_P_ARP 0x0806
#define SWAP16(x) ((uint16_t)((((x) >> 8) & 0xff) | (((x) & 0xff) << 8)))

struct __attribute__((packed)) EthernetHeader {
    uint8_t dst[6];
    uint8_t src[6];
    uint16_t ethertype;
};

volatile uint32_t g_arp_counter = 0;
volatile uint32_t g_total_counter = 0;

uint32_t last_check_ms = 0;
uint32_t last_log_alive = 0;

bool is_promisc_configured = false; 
bool is_storm_detected = false;
char my_log_buffer[64]; 

// ----------------------------------------------------------------------
// 4. LEDS 
// ----------------------------------------------------------------------
inline void set_color_digital(bool r, bool g, bool b) {
    if (COMMON_ANODE) {
        digitalWrite(LED_R, !r);
        digitalWrite(LED_G, !g);
        digitalWrite(LED_B, !b);
    } else {
        digitalWrite(LED_R, r);
        digitalWrite(LED_G, g);
        digitalWrite(LED_B, b);
    }
}

void update_led_heartbeat() {
    static uint32_t last_beat_ms = 0;
    static uint8_t phase = 0; 
    uint32_t now = millis();

    if (!is_storm_detected) {
        switch (phase) {
            case 0:
                set_color_digital(0, 0, 0);
                if (now - last_beat_ms > 1200) { phase = 1; last_beat_ms = now; }
                break;

            case 1:
                // set_color_digital(0, 1, 1); // AZUL/CYAN DESHABILITADO
                set_color_digital(0, 0, 0);
                if (now - last_beat_ms > 100) { phase = 2; last_beat_ms = now; }
                break;

            case 2:
                set_color_digital(0, 0, 0);
                if (now - last_beat_ms > 100) { phase = 3; last_beat_ms = now; }
                break;

            case 3:
                // set_color_digital(0, 1, 1); // AZUL/CYAN DESHABILITADO
                set_color_digital(0, 0, 0);
                if (now - last_beat_ms > 100) { phase = 0; last_beat_ms = now; }
                break;
        }
    } else {
        bool on = (now / 100) & 1;
        set_color_digital(on, false, false); // rojo intermitente en alerta
    }
}

// ----------------------------------------------------------------------
// 5. CALLBACK PROMISCUO
// ----------------------------------------------------------------------
void promisc_callback(unsigned char *buf, unsigned int len, void* user_data) {
    (void)user_data;

    if (len < sizeof(EthernetHeader)) return;

    g_total_counter++;

    EthernetHeader* eth = (EthernetHeader*)buf;
    if (SWAP16(eth->ethertype) == ETH_P_ARP) {
        g_arp_counter++;
    }
}

// ----------------------------------------------------------------------
// 6. SETUP
// ----------------------------------------------------------------------
void setup() {
    wdt_enable(WDT_TIMEOUT); 

    pinMode(LED_R, OUTPUT);
    pinMode(LED_G, OUTPUT);
    pinMode(LED_B, OUTPUT);

    set_color_digital(1, 1, 0); // amarillo fijo en arranque

    Serial.begin(115200);
    Serial.println(F("\n[SYS] L2 Monitor v9.2: BOOT"));

    WiFi.begin(AP_SSID, AP_PASS);
}

// ----------------------------------------------------------------------
// 7. LOOP
// ----------------------------------------------------------------------
void loop() {
    wdt_reset();
    update_led_heartbeat();

    uint32_t now = millis();
    uint8_t wifi_status = WiFi.status();

    // ---------------------------------------------------------
    // MANTENIMIENTO WIFI / PROMISC
    // ---------------------------------------------------------
    if (wifi_status != WL_CONNECTED) {
        is_promisc_configured = false;

        if (now - last_log_alive >= 2000) {
            last_log_alive = now;
            Serial.println(F("[WARN] WiFi Link Lost. Reconnecting..."));
            WiFi.begin(AP_SSID, AP_PASS);
        }
    } else {
        if (!is_promisc_configured) {
            Serial.println(F("[NET] Link Up. Re-Arming Promisc Mode..."));
            wifi_set_promisc(0, NULL, 0);
            delay(10);
            wifi_set_promisc(1, promisc_callback, 1);
            is_promisc_configured = true;
            Serial.println(F("[NET] Sniffer ACTIVE"));
        }
    }

    // ---------------------------------------------------------
    // LOGICA DE MONITORIZACIÓN
    // ---------------------------------------------------------
    if (now - last_check_ms >= MONITOR_WINDOW) {
        last_check_ms = now;

        taskENTER_CRITICAL();
        uint32_t snap_arp = g_arp_counter;
        uint32_t snap_total = g_total_counter;
        g_arp_counter = 0;
        g_total_counter = 0;
        taskEXIT_CRITICAL();

        bool alarm_condition = false;

        if (snap_arp >= ARP_THRESHOLD) {
            snprintf(my_log_buffer, sizeof(my_log_buffer),
                     "[ALERTA] ARP: %lu pps", (unsigned long)snap_arp);
            Serial.println(my_log_buffer);
            alarm_condition = true;
        }
        else if (snap_total >= TOTAL_THRESHOLD) {
            snprintf(my_log_buffer, sizeof(my_log_buffer),
                     "[ALERTA] VOL: %lu pps", (unsigned long)snap_total);
            Serial.println(my_log_buffer);
            alarm_condition = true;
        }

        is_storm_detected = alarm_condition;
    }
}
