# Clementime by Clemente

Widget Android de reloj digital y fecha con 21 estilos visuales.

## Clementime 1.7

- 10 marcos transparentes con diseños diferentes: rayitas, HUD, circuitos, franjas, remaches, cheurones, hexágonos, art déco, pinstripe y segmentos sci-fi.
- Trazos de los marcos transparentes notablemente más finos para que el reloj tenga más protagonismo.
- 10 fondos con imagen con opacidad ajustable de 0 a 100%.
- Tamaño inicial solicitado de 4 columnas (`targetCellWidth=4`) y `minWidth=320dp`; continúa siendo redimensionable.
- Android no redimensiona automáticamente widgets que ya estaban colocados. Para probar el nuevo tamaño inicial hay que quitar el widget y volverlo a añadir.
- Al tocar el reloj, el engranaje aparece durante 1 segundo; otro toque reinicia el temporizador.
- Hora 12/24 h según Android y fecha abreviada en inglés.

## APK

El workflow `.github/workflows/build-clementime.yml` compila y publica `Clementime-Android/Clementime_by_Clemente_v1.7.apk` junto con su SHA-256.
