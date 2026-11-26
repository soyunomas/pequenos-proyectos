# NetScope 4.0 — 20 herramientas adicionales sin root (especificación)

Fecha: 2026-11-26.

## Objetivo
Incorporar las 20 funciones solicitadas, numeradas 26 a 45, a la suite experta Android sin root, conservando el diseño azul/blanco B+D y resultados verificables.

## Contrato de la interfaz
- 45 herramientas enumeradas con descripción y punto de entrada.
- Herramientas de terminal invocadas por la suite; sesiones especiales para servidores, roaming, mapa y RTT, con control explícito e indicadores de estado.
- Operaciones locales por Wi-Fi actual cuando es posible; no realizar sondeos activos a redes públicas por defecto.
- Resultados con protocolo, método, duración, limitaciones, causa de fallo y exportación. Nada se interpreta como prueba de acceso, disponibilidad o seguridad sin respuesta real.
- Sin root. No se suplantan identidades ni se eluden permisos o certificados TLS.
- Respuestas acotadas (tamaño, tiempo, sesiones) y cancelación operativa al cerrar.
- Ubicación solo para Wi-Fi RSSI/RTT, con permisos del sistema. El mapa se suministra localmente por selector de archivos.
- No guardar claves, tráfico de usuarios ni datos de terceros fuera del dispositivo. Herramientas de Internet revelan IP/hostname al servicio consultado.

## 20 capacidades
26. Descubrimiento fuera de subred por anuncios multicast mDNS/SSDP/WS-Discovery; sin falsas garantías de ARP.
27. Rendimiento LAN entre dos Android: servidor y cliente TCP y UDP acotados, sin equipo externo.
28. Seguimiento del roaming BSSID/RSSI con cronología.
29. Mapa local de cobertura Wi-Fi a partir de foto/plano y mediciones manuales, incluyendo interpolación visual.
30. Distancia real WifiRttManager a APs compatibles 802.11mc.
31. Registro temporal de cambios de red (SSID, BSSID, IP, DNS, gateway, interfaz).
32. Test de portal cautivo con código de comprobación HTTP y señal de Android.
33. Comparación DNS privado local y público.
34. Verificación DNSSEC mediante bandera AD de resolvedor validador confiable y consultas DO/CD; se aclara que no implementa validación criptográfica local independiente.
35. Diagnóstico EDNS(0), TC y fallback DNS TCP con parsing de wire format.
36. Pruebas STUN Binding y dirección asignada/NAT detectables sin prometer clasificación NAT completa.
37. UPnP IGD: descubrir root IGD y leer datos WAN por servicio HTTP SOAP solo lectura, con validación de URL LAN.
38. PCP ANNOUNCE y NAT-PMP consulta de IP externa sin crear mapeos.
39. Inspector UDP: datagramas pequeños, espera de respuesta, sin inferir puertos cerrados de timeout.
40. IPP Get-Printer-Attributes de lectura a impresora elegida.
41. ONVIF WS-Discovery con IP y XAddrs visibles.
42. SMB protocolo SMB2: negociación real, diálogo de recursos accesibles si soporte guest/capacidad adicional; no inventar listado.
43. SSH banner y KEXINIT algoritmos publicados sin autenticar, clave de host solo si se completa KEX.
44. WebSocket ws/wss texto RFC6455 con handshake y frames enmascarados, sin root.
45. HTTP/2 y HTTP/3: negociación ALPN y prueba real por cliente compatible, no confundir Alt-Svc con HTTP/3 negociado.

## Limitaciones estructurales
- Cliente SMB que enumere shares autenticados y cliente HTTP/3 completo pueden requerir bibliotecas externas actualizadas; preferir dependencias revisadas y si no son viables informar del alcance real.
- Sin root no se pueden transmitir ARP raw ni capturar todas las tramas 802.11.
- Dos móviles se deben emparejar expresamente para medir rendimiento; cada uno inicia/acepta la conexión y sólo pueden comunicarse si la Wi-Fi permite tráfico entre clientes.
- Protocolos locales se prueban contra destinos del usuario, no se enumeran dispositivos externos indiscriminadamente.
