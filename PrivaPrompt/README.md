# PrivaPrompt

Herramienta web autocontenida para **anonimizar datos sensibles antes de enviar un texto a una IA** y restaurarlos después en la respuesta.

No usa framework, fuentes remotas ni librerías externas. Todo el procesamiento de texto y el diccionario de sustituciones vive en memoria dentro del navegador; al recargar la página, la sesión desaparece.

## Abrir

- GitHub Pages: `https://soyunomas.github.io/pequenos-proyectos/PrivaPrompt/`
- Local: abre `index.html` directamente en un navegador moderno.

> La lectura automática del portapapeles puede estar restringida cuando se abre como `file://`. En ese caso, usa `Ctrl+V` dentro del editor. En GitHub Pages (HTTPS), el navegador puede permitir el botón **Pegar** según sus permisos.

## Flujo de uso

1. **Editar**: pega o escribe el texto original.
2. **Marcar datos**: selecciona nombres, empresas, lugares o datos críticos y asígnales una categoría. También puedes buscarlos y ampliar el fragmento desde el panel de contexto.
3. **Revisar texto anonimizado**: PrivaPrompt sustituye las apariciones por identificadores como `[PERSONA_1]` o `[SECRETO_1]`.
4. Copia el texto y úsalo en la IA que prefieras.
5. En **Restaurar respuesta**, pega la respuesta anonimizada para recuperar los valores originales.

## Cambios respecto al prototipo inicial

- Interfaz rediseñada con una estética editorial/utilitaria: jerarquía por espacio, líneas y tipografía, sin tarjetas redondeadas, sombras decorativas ni iconos redundantes.
- Eliminadas Bootstrap, Bootstrap Icons y las cargas desde CDN. El proyecto queda autocontenido.
- El modo inicial ahora es un `textarea` editable: `Ctrl+V` funciona donde la interfaz dice que funciona.
- Manejo explícito de las limitaciones de la Clipboard API cuando no hay HTTPS o permiso del navegador.
- Flujo más seguro: no se habilita la generación del texto anonimizado hasta que exista texto y al menos una entidad protegida.
- Búsqueda y detección de solapamientos corregidas para evitar marcas superpuestas.
- Sustitución de entidades con límites de palabra cuando corresponde, reduciendo falsos reemplazos como proteger `Ana` dentro de `Analítica`.
- Modal accesible con cierre por `Esc`, retorno de foco y trampa básica de foco.
- Navegación por pestañas con teclado (`←`, `→`, `Home`, `End`).
- Acción **Nueva sesión** borra de forma coherente texto, reglas y salidas, evitando diccionarios residuales.
- Restauración de identificadores tolerante a cambios de mayúsculas/minúsculas realizados por la IA.

## Privacidad

PrivaPrompt no realiza peticiones de red desde su código ni guarda el texto en `localStorage`, IndexedDB o cookies. Si se publica mediante GitHub Pages, el navegador obviamente descarga el propio archivo HTML desde GitHub, pero **el contenido que escribes o pegas no se envía desde la aplicación**.

Esto no sustituye una revisión humana: antes de copiar el resultado a un servicio externo, conviene comprobar visualmente que no quede información sensible sin marcar.

## Archivos

```text
PrivaPrompt/
├── index.html
└── README.md
```
