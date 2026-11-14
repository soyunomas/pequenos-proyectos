# Hosting Aula

Plataforma de alojamiento educativo para Ubuntu Server (objetivo: 26.04 LTS). Apache frontal en el puerto 80, Apache y PHP-FPM independientes por alumno, jaulas chroot con /usr compartido en solo lectura, MySQL, PostgreSQL, Node.js y Python. No requiere una IP fija.

**Estado: prototipo para validar en una VM nueva. No desplegar en producción ni sobre una instalación anterior.** No se han ejecutado las pruebas de integración completas en Ubuntu 26.04.

## Instalación

1. Crea una VM Ubuntu Server, red de aula, 15 GB dinámicos para una prueba pequeña (ampliable) y preferiblemente 4 GB de RAM. Asigna todo el disco al volumen raíz si usas LVM. Toma una instantánea.
2. Descarga la carpeta completa (no solo script.sh). Edita alumnos.csv: una columna sin cabecera, nombres únicos.
3. Ejecuta:

```bash
sudo bash script.sh
```

El gestor comprueba los paquetes instalados con dpkg y solicita a APT únicamente los que falten. Configura el LAMP, phpMyAdmin, OpenSSH, Avahi, PostgreSQL y una plantilla Ubuntu debootstrap. No fija PHP; detecta la versión de la plantilla. Los paquetes adicionales de compilación y multimedia son opcionales: `sudo bash script.sh instalar alumnos.csv --completo`. Si ya instalaste el modo básico: `sudo bash script.sh extras`.

No añade repositorios externos. Los paquetes deben existir para la distribución detectada. APT puede actualizar dependencias necesarias. phpMyAdmin puede pedir seleccionar apache2 y aceptar dbconfig-common. Se requiere el sitio HTTP estándar 000-default.conf y autenticación local de root MySQL mediante socket. Si MySQL tiene validate_password, detiene la instalación antes de crear cuentas: no debilita la política global.

## Panel de administración en terminal

```bash
sudo bash panel.sh
# Alternativa:
sudo bash script.sh --menu
```

No necesita servidor web adicional ni librerías de interfaz. Muestra el espacio disponible, los alumnos activos y cuatro secciones: alumnos y cuentas; bases de datos y contraseñas; web, PHP y aplicaciones; mantenimiento y recursos. Selecciona el alumno por número o usuario, responde a las preguntas y confirma las operaciones sensibles. Si el alojamiento todavía no existe, ofrece instalar desde el CSV. Puedes seguir utilizando todos los comandos de la CLI para automatizar tareas.

## Accesos

Para Ana, usuario y contraseña son ana en SSH, SFTP, MySQL y PostgreSQL. Solo para pruebas controladas. Web: http://apw.local/ana/ o http://IP_DEL_SERVIDOR/ana/. phpMyAdmin: http://apw.local/phpmyadmin/. SSH: `ssh ana@apw.local`. SFTP: `sftp ana@apw.local`. Consulta la IP con `hostname -I`. mDNS depende de la red y del cliente.

Bases de datos MySQL iniciales de Ana: **db_ana, db_ana1, db_ana3**, todas accesibles con el usuario ana y contraseña inicial ana. PostgreSQL mantiene su base ana; host **127.0.0.1**, MySQL 3306 y PostgreSQL 5432. SQLite no necesita servidor. La web se edita en ~/public_html; dentro de la jaula corresponde a /web/ana. Las credenciales iniciales y la lista de bases se guardan en /root/credenciales-aula.csv (solo root). Las contraseñas personalizadas que se cambian desde el gestor no se guardan: se marcan como cambiadas. No publiques este fichero.

## Administración

Todas las órdenes requieren sudo y se ejecutan desde la carpeta del proyecto:

| Orden (después de sudo bash script.sh) | Función |
|---|---|
| alta Carlos | Crea cuenta, web, servicios y bases de datos |
| alta Carlos Lucía "María José" | Añade varios alumnos en una operación |
| altas nuevos.csv | Añade alumnos de un CSV sin recrear los existentes |
| listar | Enumera alumnos, PHP y modo |
| doctor | Comprueba paquetes, montajes y servicios |
| dependencias | Instala dependencias faltantes |
| extras | Compiladores, FFmpeg, ImageMagick |
| instalar-php 8.4 | Añade PHP si está en los repositorios configurados |
| php ana 8.4 | Selecciona versión CLI y FPM |
| documentroot ana public | Cambia la raíz pública relativa a public_html |
| documentroot ana . | Restablece raíz original |
| node ana apps/app.js | Publica una aplicación Node.js |
| python ana apps/app.py | Publica una aplicación Python |
| app ana /home/ana/apps/venv/bin/python /home/ana/apps/app.py | Comando personalizado dentro de jaula |
| web ana | Recupera el modo Apache/PHP |
| reiniciar ana | Reinicia servicios de la web o aplicación |
| logs ana | Consulta journal |
| redis ana | Redis privado mediante socket Unix |
| cron ana tarea 5 /home/ana/apps/tarea.sh | Programa tarea cada cinco minutos |
| cron-baja ana tarea | Elimina tarea |
| limites ana 1024 100 | RAM MiB y porcentaje de un núcleo, para servicios gestionados |
| cuota ana 1024 | Cuota de disco si el FS ya tiene cuotas habilitadas |
| backup ana | Copia de archivos, todas las bases MySQL registradas existentes y PostgreSQL |
| db-listar ana | Muestra bases MySQL y permisos |
| db-alta ana 2 | Crea db_ana2 |
| db-baja ana db_ana2 | Elimina una base adicional, con confirmación exacta |
| db-reparar ana | Crea las bases iniciales que falten y restablece sus permisos |
| db-reparar | Aplica la operación a todos los alumnos activos |
| clave ana mysql | Cambia contraseña MySQL (dos cuentas locales) |
| clave ana postgresql | Cambia contraseña PostgreSQL |
| clave ana ssh | Cambia contraseña SSH/SFTP |
| clave ana mysql --restablecer | Vuelve al nombre de usuario con confirmación |
| baja ana | Suspende cuenta, sin borrar datos |
| reactivar ana | Reactiva cuenta |

### Incorporar alumnos después de instalar

```bash
sudo bash script.sh alta Carlos
sudo bash script.sh alta Lucía "María José"
sudo bash script.sh altas alumnos.csv
sudo bash script.sh listar
```

Se admite también la sintaxis `--alta` y `--altas`. Puedes añadir nombres al CSV inicial y volver a importarlo mediante **altas**, no mediante instalar. Los alumnos existentes con el mismo nombre se omiten (incluidos los suspendidos); no se cambian sus contraseñas ni se eliminan sus datos. Las colisiones de nombres normalizados y las cuentas ajenas al proyecto se rechazan antes de iniciar el lote. Cada alta utiliza la plantilla compartida. Si falla una alta, las anteriores que hayan terminado se conservan; no hay rollback general.

## Gestión de las bases de datos y claves

Por cada alumno nuevo se crean exactamente tres bases MySQL iniciales: db_usuario, db_usuario1, db_usuario3. El salto del 1 al 3 es intencionado. Se conceden permisos independientes sobre los nombres exactos, no sobre patrones con comodines; se comprueba el modo partial_revokes de MySQL. Se rechazan colisiones entre alumnos (por ejemplo Ana y Ana1) antes de crear las cuentas. Elige nombres distintos si esto sucede.

```bash
sudo bash script.sh db-listar ana
sudo bash script.sh db-alta ana 2
sudo bash script.sh clave ana mysql
sudo bash script.sh db-baja ana db_ana2
```

El sufijo de una base adicional es numérico; no puede invadir los nombres de otro alumno. Las tres bases iniciales están protegidas contra borrado **desde el gestor** (el alumno tiene privilegios de su propio esquema y puede borrarlo desde MySQL). Reparar vuelve a crear las bases ausentes, pero no recupera sus datos. Antes de eliminar una base adicional, realiza una copia si quieres conservarla. El borrado requiere que escribas el nombre exacto. No se almacenan las claves nuevas ni se pasan por argumentos de proceso; el programa pide confirmación oculta para las claves personalizadas y respeta las políticas del motor. Debes actualizar wp-config.php, .env u otros ficheros de aplicación si cambias una clave de base de datos. Las cuentas suspendidas deben reactivarse antes de modificar claves o crear bases.

### Actualizar una instalación anterior del mismo proyecto

Descarga/actualiza la carpeta del proyecto sin tocar /srv/hosting-aula. **No vuelvas a ejecutar la instalación inicial.**

```bash
sudo bash script.sh db-reparar
sudo bash panel.sh
```

La base original (por ejemplo ana) se conserva; no se renombra, no se mueve ni se altera su contenido. Se añade el nuevo conjunto de tres bases, de modo que un alumno antiguo puede tener cuatro. Si una base de destino ya existe fuera del registro gestionado, el script se detiene en vez de conceder acceso a datos ajenos. Las cuentas suspendidas se omiten en la reparación colectiva. No es una migración desde otras arquitecturas/scripts anteriores. Recomendable hacer una instantánea o copia antes de actualizar. Si cambiaste contraseñas manualmente fuera del gestor, este no puede conocerlas.

## PHP, CMS y Composer

Apache interpreta .htaccess, mod_rewrite y PATH_INFO. WordPress: desde SSH, cd public_html; elimina el index.php de prueba; wget https://wordpress.org/latest.zip; unzip latest.zip; cp -a wordpress/. .; rm -rf wordpress latest.zip. Visita /ana/ e introduce base db_ana (o db_ana1/db_ana3), usuario y clave inicial ana, host MySQL 127.0.0.1. PrestaShop y Drupal requieren comprobar las versiones de PHP y las extensiones correspondientes. Composer está disponible en la jaula. Laravel: configura la raíz public y guarda secretos fuera de la raíz publicada.

Moodle: verifica requisitos de la versión exacta (https://moodledev.io/general/releases). Si necesita otro PHP, instala y selecciona una versión disponible para tu distribución; no mezcles releases Ubuntu. Para Moodle con carpeta public: descomprime en public_html/moodle; el profesor ejecuta `sudo bash script.sh documentroot ana moodle/public --moodle`. Datos en /home/ana/moodledata; URL canónica http://apw.local/ana. Cron: `sudo bash script.sh cron ana moodle 1 /web/ana/moodle/admin/cli/cron.php` (ajusta la ruta si la distribución la cambia). No alternes IP y nombre después de fijar la URL canónica de un CMS. Los CMS no se instalan automáticamente.

## Node.js

Desde SSH:

```bash
cd ~/apps
cat > app.js <<'JS'
const http = require('node:http');
http.createServer((req,res) => res.end('Hola desde Node.js')).listen(Number(process.env.PORT), process.env.HOST);
JS
```

Como profesor: `sudo bash script.sh node ana apps/app.js`. Accede a /ana/. La aplicación debe escuchar en HOST y PORT proporcionados por el servicio. El proxy quita el prefijo /ana/ y envía X-Forwarded-Prefix. Configura las URLs base, recursos, cookies y WebSockets de tu aplicación en consecuencia. npm instala paquetes locales; para producción no uses servidores de desarrollo. Strapi, Ghost, Directus, Payload y Next.js dependen de versiones y adaptadores concretos; algunos requieren raíz de dominio, otros Docker o bases adicionales. No se garantiza que funcionen bajo un prefijo ni se instalan por defecto. pnpm/Yarn y gestores multiversión Node no se preinstalan: usa herramientas de proyecto compatibles bajo la cuenta del alumno.

## Python

Desde SSH:

```bash
cd ~/apps
python3 -m venv venv
venv/bin/pip install flask gunicorn
cat > app.py <<'PY'
from flask import Flask
app = Flask(__name__)
@app.get("/")
def index():
    return "Hola desde Python"
PY
cat > start.sh <<'SH'
#!/bin/bash
cd "$HOME/apps"
exec "$HOME/apps/venv/bin/gunicorn" --bind "$HOST:$PORT" app:app
SH
chmod +x start.sh
```

Como profesor: `sudo bash script.sh app ana /bin/bash /home/ana/apps/start.sh`. Django, Wagtail y FastAPI necesitan configurar su prefijo y archivos estáticos. Cada entorno virtual es privado.

## Redis, recursos, copias y seguridad

Redis opcional escucha únicamente en /run/aula/redis/redis.sock dentro de la jaula; no expone un puerto. Usa ese socket en la aplicación si admite Unix sockets. La caché se limita a 64 MB y no persiste. Las cuotas requieren soporte y configuración previa del sistema de archivos; el gestor no modifica la partición ni activa cuotas por su cuenta. Los límites systemd cubren los servicios gestionados, no toda sesión SSH ni bases compartidas. SSH tiene límites PAM adicionales. Las copias de todas las bases MySQL registradas existentes y PostgreSQL se guardan junto con los archivos en /srv/hosting-aula/backups; no son instantáneas atómicas. Para restaurar, prepara una VM de prueba, revisa el tar (no extraigas archivos de alumnos como root sin validar rutas), restaura los ficheros como alumno, importa mysql.sql con un administrador y postgresql.dump con pg_restore bajo el rol correspondiente. No hay restaurador automático.

El chroot no equivale a un contenedor fuerte: se comparte kernel y red; otros usuarios pueden contactar con listeners internos. Las aplicaciones deben respetar el puerto asignado. El puerto público previsto es 80 (SSH 22); limita el acceso mediante firewall de la VM/red. No se configura HTTPS, namespaces de red ni aislamiento de origen entre webs; todas comparten origen con phpMyAdmin. Contraseñas iguales al usuario permiten suplantación trivial: no expongas este sistema a Internet ni a alumnos no confiables. No uses la sesión administrativa de phpMyAdmin en el mismo navegador donde ejecutas código de alumnos. Para despliegue real, necesitas contraseñas únicas, HTTPS, orígenes separados y aislamiento más fuerte.

## Pruebas sin modificar el servidor

```bash
python3 -m py_compile hosting.py panel.py
bash -n script.sh panel.sh
python3 -m unittest -v test_hosting
```

Las pruebas unitarias incluidas comprueban nombres, colisiones, SQL y opciones de administración. No sustituyen una instalación de prueba con MySQL, SSH y Apache reales.

## Verificación y fallos

La instalación comprueba la sintaxis de Apache y SSH, montaje ro/nosuid/nodev, arranque de PHP y Apache, respuesta web por nombre e IP, .htaccess, PATH_INFO, denegación 403 y conexión MySQL/PostgreSQL. También valida la configuración efectiva de SSH. Tras reiniciar la VM, ejecuta doctor y prueba SSH/SFTP y todas las aplicaciones. No hay rollback global: si falla, conserva el registro, revisa /srv/hosting-aula y restaura una instantánea antes de repetir. Los comandos de administración requieren una instalación terminada. Los archivos de configuración originales se guardan bajo config-original; no los restaures selectivamente sin revisar cambios posteriores.

Referencias: [Apache mod_proxy](https://httpd.apache.org/docs/2.4/mod/mod_proxy.html), [Apache .htaccess](https://httpd.apache.org/docs/2.4/howto/htaccess.html), [Moodle](https://moodledev.io/general/releases), [systemd](https://www.freedesktop.org/software/systemd/man/latest/systemd.exec.html).

## Estado observado en la VM de pruebas — 14 de noviembre de 2026

Las comprobaciones de lectura realizadas en la VM confirman servicios Apache,
SSH, MySQL y PostgreSQL activos, además de los servicios web y PHP-FPM de cinco
alumnos. Sus páginas de bienvenida responden por HTTP y ejecutan PHP 8.5.4.
phpMyAdmin responde por HTTP; su inicio de sesión y sus consultas siguen pendientes.

La red, systemd y los sockets son accesibles desde la sesión actual de auditoría.
Las restricciones de una sesión anterior ya no describen este entorno.
La administración sigue bloqueada por la autenticación interactiva de sudo:
el intento de ejecutar `sudo -n bash script.sh alta arturo` se detuvo antes de
iniciar el gestor. Ese intento no creó la cuenta.

Para añadir un alumno a una instalación terminada, ejecuta en una terminal de
la VM con acceso a sudo:

```bash
cd /ruta/al/proyecto/hosting-aula
sudo bash script.sh alta arturo
sudo bash script.sh listar
```

Ajusta la ruta si descargaste el proyecto en otra carpeta. Introduce la contraseña
de sudo únicamente en la terminal. Si el alta termina correctamente, el gestor
crea la cuenta, la web y las bases; la contraseña inicial del alumno es su nombre
de usuario. No vuelvas a ejecutar la instalación inicial para añadir alumnos.

Se han comprobado respuestas 403 para varios nombres de archivos sensibles y
montajes de /usr de las jaulas en modo ro,nosuid,nodev. Estas comprobaciones
puntuales no certifican el aislamiento completo. Siguen pendientes la integración
privilegiada, la autenticación SSH/SFTP y SQL, las aplicaciones, las copias y su
restauración, y la persistencia tras reiniciar. Las funciones descritas en este
README expresan el comportamiento previsto; no todas cuentan con una prueba
real completa.

Para continuar el diagnóstico con permisos administrativos:

```bash
sudo bash script.sh doctor
```

Los 4 GB de RAM y 15 GB de disco indicados para una prueba pequeña son
orientativos; no constituyen un dimensionamiento validado para un aula completa.
