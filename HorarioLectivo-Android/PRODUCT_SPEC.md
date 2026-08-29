# Contrato de implementación / criterios de aceptación

Este archivo actúa como especificación estricta del producto.

## UX

La configuración debe reducir al mínimo la repetición: las asignaturas se crean una sola vez, el usuario selecciona una abreviatura y pinta casillas, puede rellenar bloques con dos toques y copiar días completos. El usuario nunca debe introducir manualmente las horas de cada sesión: se calculan desde inicio, fin, duración y recreo.

## Horario

- Lunes a viernes.
- Mañana y tarde opcionales e independientes.
- Inicio/fin por turno.
- Duración de sesión configurable.
- Recreo por turno configurable por posición y duración.
- La tabla siempre muestra inicio y fin en la primera columna.
- En el día actual se resaltan en rojo la franja actual y la siguiente.

## Widget

- Muestra AHORA y SIGUIENTE con abreviatura/recreo y hora.
- Pulsarlo abre la tabla semanal.
- Se actualiza periódicamente y programa una actualización en el siguiente cambio de franja.

## Puertas de calidad

No se considera publicable una revisión hasta que pasen pruebas unitarias, lint y compilación de APK sin errores.
