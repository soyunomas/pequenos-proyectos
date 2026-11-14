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

## Accesos

Para Ana, usuario y contraseña son ana en SSH, SFTP, MySQL y PostgreSQL. Solo para pruebas controladas. Web: http://apw.local/ana/ o http://IP_DEL_SERVIDOR/ana/. phpMyAdmin: http://apw.local/phpmyadmin/. SSH: `ssh ana@apw.local`. SFTP: `sftp ana@apw.local`. Consulta la IP con `hostname -I`. mDNS depende de la red y del cliente.

Bases de datos: nombre ana, usuario ana, contraseña ana, host **127.0.0.1**, MySQL 3306 y PostgreSQL 5432. SQLite no necesita servidor. La web se edita en ~/public_html; dentro de la jaula corresponde a /web/ana. Las credenciales se guardan en /root/credenciales-aula.csv (solo root). No publiques este fichero.

## Administración

Todas las órdenes requieren sudo y se ejecutan desde la carpeta del proyecto:

| Orden (después de sudo bash script.sh) | Función |
|---|---|
| alta Carlos | Crea cuenta, web, servicios y bases de datos |
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
| backup ana | Copia de archivos y ambas bases de datos |
| baja ana | Suspende cuenta, sin borrar datos |
| reactivar ana | Reactiva cuenta |

## PHP, CMS y Composer

Apache interpreta .htaccess, mod_rewrite y PATH_INFO. WordPress: desde SSH, cd public_html; elimina el index.php de prueba; wget https://wordpress.org/latest.zip; unzip latest.zip; cp -a wordpress/. .; rm -rf wordpress latest.zip. Visita /ana/ e introduce MySQL 127.0.0.1. PrestaShop y Drupal requieren comprobar las versiones de PHP y las extensiones correspondientes. Composer está disponible en la jaula. Laravel: configura la raíz public y guarda secretos fuera de la raíz publicada.

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

Redis opcional escucha únicamente en /run/aula/redis/redis.sock dentro de la jaula; no expone un puerto. Usa ese socket en la aplicación si admite Unix sockets. La caché se limita a 64 MB y no persiste. Las cuotas requieren soporte y configuración previa del sistema de archivos; el gestor no modifica la partición ni activa cuotas por su cuenta. Los límites systemd cubren los servicios gestionados, no toda sesión SSH ni bases compartidas. SSH tiene límites PAM adicionales. Las copias se guardan en /srv/hosting-aula/backups; no son instantáneas atómicas. Para restaurar, prepara una VM de prueba, revisa el tar (no extraigas archivos de alumnos como root sin validar rutas), restaura los ficheros como alumno, importa mysql.sql con un administrador y postgresql.dump con pg_restore bajo el rol correspondiente. No hay restaurador automático.

El chroot no equivale a un contenedor fuerte: se comparte kernel y red; otros usuarios pueden contactar con listeners internos. Las aplicaciones deben respetar el puerto asignado. El puerto público previsto es 80 (SSH 22); limita el acceso mediante firewall de la VM/red. No se configura HTTPS, namespaces de red ni aislamiento de origen entre webs; todas comparten origen con phpMyAdmin. Contraseñas iguales al usuario permiten suplantación trivially: no expongas este sistema a Internet ni a alumnos no confiables. No uses la sesión administrativa de phpMyAdmin en el mismo navegador donde ejecutas código de alumnos. Para despliegue real, necesitas contraseñas únicas, HTTPS, orígenes separados y aislamiento más fuerte.

## Verificación y fallos

La instalación comprueba la sintaxis de Apache y SSH, montaje ro/nosuid/nodev, arranque de PHP y Apache, respuesta web por nombre e IP, .htaccess, PATH_INFO, denegación 403 y conexión MySQL/PostgreSQL. También valida la configuración efectiva de SSH. Tras reiniciar la VM, ejecuta doctor y prueba SSH/SFTP y todas las aplicaciones. No hay rollback global: si falla, conserva el registro, revisa /srv/hosting-aula y restaura una instantánea antes de repetir. Los comandos de administración requieren una instalación terminada. Los archivos de configuración originales se guardan bajo config-original; no los restaures selectivamente sin revisar cambios posteriores.

Referencias: [Apache mod_proxy](https://httpd.apache.org/docs/2.4/mod/mod_proxy.html), [Apache .htaccess](https://httpd.apache.org/docs/2.4/howto/htaccess.html), [Moodle](https://moodledev.io/general/releases), [systemd](https://www.freedesktop.org/software/systemd/man/latest/systemd.exec.html).
