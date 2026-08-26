# Clementime by Clemente

Widget Android de reloj digital con fondo transparente, fecha en inglés y 21 estilos visuales.

## Características

- Hora 12/24 h según Android.
- Fecha abreviada en inglés (`MON 24 AUG`).
- 21 temas: original transparente + 10 marcos transparentes con geometrías realmente distintas + 10 fondos visuales generados con IA.
- Los 10 marcos clásicos no rellenan el fondo: mantienen visible el wallpaper y cambian forma, trazado y ornamentación, no sólo el color.
- Los 10 fondos con imagen tienen control de opacidad de 0% a 100% desde el selector de temas.
- Al tocar el reloj, el engranaje aparece durante 1 segundo y se oculta automáticamente.
- Si vuelves a tocar antes de que pase el segundo, el temporizador se reinicia para evitar ocultaciones prematuras.
- Toque en el engranaje: abre el selector de temas.
- Los 10 fondos IA se almacenan en una hoja de sprites compacta codificada en recursos `raw` y se recortan en tiempo de ejecución.
- Sin permisos de red ni servicios persistentes en segundo plano.

## APK

El workflow `.github/workflows/build-clementime.yml` compila y publica `Clementime-Android/Clementime_by_Clemente_v1.5.apk` junto con su SHA-256.
