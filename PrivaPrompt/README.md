# PrivaPrompt

Herramienta web para **anonimizar datos sensibles antes de enviar un texto a una IA** y restaurarlos después en la respuesta.

No usa frameworks, fuentes remotas ni librerías externas. El texto y el diccionario de sustituciones se procesan en memoria dentro del navegador; al recargar la página, la sesión desaparece.

## Abrir

- GitHub Pages: `https://soyunomas.github.io/pequenos-proyectos/PrivaPrompt/`
- Local: abre `index.html` en un navegador moderno.

> La lectura automática del portapapeles puede estar restringida cuando se abre como `file://`. En ese caso, usa `Ctrl+V` dentro del editor. En GitHub Pages (HTTPS), el navegador puede permitir el botón **Pegar** según sus permisos.

## Flujo de uso

1. **Editar**: pega o escribe el texto original.
2. **Marcar datos**: selecciona nombres, empresas, lugares o datos críticos y asígnales una categoría. También puedes buscarlos y ampliar el fragmento desde el panel de contexto.
3. **Revisar texto anonimizado**: PrivaPrompt sustituye las apariciones por identificadores como `[PERSONA_1]` o `[SECRETO_1]`.
4. Copia el texto y úsalo en la IA que prefieras.
5. En **Restaurar respuesta**, pega la respuesta anonimizada para recuperar los valores originales.

## Diseño y UX

La interfaz evita deliberadamente el aspecto de plantilla generada por IA: usa tipografía del sistema, una paleta neutra con un único acento índigo, superficies planas y una jerarquía compacta. La cabecera se limita al nombre de la herramienta y el contenido útil aparece inmediatamente debajo.

También se han corregido varios problemas funcionales del prototipo inicial:

- Eliminadas Bootstrap, Bootstrap Icons y todas las cargas desde CDN.
- El modo inicial es un `textarea` real: `Ctrl+V` funciona donde la interfaz indica que funciona.
- Manejo explícito de las limitaciones de la Clipboard API cuando no hay HTTPS o permiso del navegador.
- La generación del texto anonimizado exige texto y al menos una entidad protegida.
- Detección de solapamientos corregida para evitar marcas superpuestas.
- Sustitución con límites de palabra cuando corresponde, reduciendo falsos reemplazos como proteger `Ana` dentro de `Analítica`.
- Modal con cierre por `Esc`, retorno de foco y control básico del foco.
- Navegación entre pestañas con teclado (`←`, `→`, `Home`, `End`).
- **Nueva sesión** elimina texto, reglas y resultados de forma coherente.
- Restauración tolerante a cambios de mayúsculas/minúsculas en los identificadores.

## Privacidad

PrivaPrompt no realiza peticiones de red desde su código ni guarda el texto en `localStorage`, IndexedDB o cookies. Si se publica mediante GitHub Pages, el navegador descarga los archivos de la aplicación desde GitHub, pero **el contenido que escribes o pegas no se envía desde PrivaPrompt**.

Antes de copiar el resultado a un servicio externo, conviene comprobar visualmente que no quede información sensible sin marcar.

## Archivos

```text
PrivaPrompt/
├── index.html
├── styles.css
├── app.js
└── README.md
```
