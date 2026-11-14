#!/usr/bin/env python3
"""Hosting de aula: administración del host. Ejecutar solo como root."""
import argparse
import csv
import fcntl
import getpass
import json
import os
import pwd
import re
import shutil
import socket
import subprocess as sp
import sys
import time
import unicodedata
from pathlib import Path

BASE = Path("/srv/hosting-aula")
TPL = BASE / "plantilla"
JAILS = BASE / "jaulas"
STATE = BASE / "estado.json"
READY = BASE / "instalado"
FRONT = Path("/etc/apache2/hosting-aula-rutas.conf")
SITE = Path("/etc/apache2/sites-available/000-default.conf")
SSH = Path("/etc/ssh/sshd_config")
UNITS = Path("/etc/systemd/system")
GROUP = "hosting-aula"
HOST = "apw.local"
EXT = "cli fpm mysql pgsql sqlite3 curl gd mbstring xml zip intl soap bcmath ldap".split()
HOSTPKG = "apache2 mysql-server postgresql php-cli php-fpm php-mysql php-pgsql php-mbstring php-xml php-zip php-curl php-gd php-intl openssh-server avahi-daemon debootstrap ca-certificates curl openssl python3 iproute2 util-linux quota".split()
CORE = "apache2-bin media-types ca-certificates bash nano less wget curl zip unzip git mysql-client postgresql-client redis-server redis-tools sqlite3 nodejs npm python3 python3-venv python3-pip composer tzdata".split()
PHPPKG = ["php-" + x for x in EXT] + ["php-imagick", "php-redis"]
EXTRA = "build-essential pkg-config python3-dev ffmpeg imagemagick".split()
MODULES = "mpm_event authn_core authn_file authz_core authz_host authz_user auth_basic access_compat alias dir mime negotiation autoindex env setenvif filter headers expires deflate rewrite proxy proxy_fcgi proxy_http".split()
RESERVED = {"root", "admin", "phpmyadmin", "server-status", "server-info", "icons", "cgi-bin", "hosting-aula"}
GIB = 1024**3

class Error(RuntimeError):
    pass

def check(ok, msg):
    if not ok:
        raise Error(msg)

def run(*args, capture=False, data=None, quiet=False, out=None, env=None):
    cmd = [str(a) for a in args]
    p = sp.run(cmd, input=data, text=True, stdout=sp.PIPE if capture else (out if out is not None else (sp.DEVNULL if quiet else None)), env=env)
    if p.returncode:
        raise Error(f"Falló {cmd[0]} {cmd[1] if len(cmd)>1 else ''} (código {p.returncode})")
    return p.stdout.strip() if capture else None

def ok(*args):
    return sp.run([str(a) for a in args], stdout=sp.DEVNULL, stderr=sp.DEVNULL).returncode == 0

def put(path, content, mode=0o644):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".aula-tmp")
    fd = os.open(tmp, os.O_CREAT | os.O_TRUNC | os.O_WRONLY | os.O_NOFOLLOW, mode)
    with os.fdopen(fd, "w", encoding="utf-8") as f:
        f.write(content)
    os.chmod(tmp, mode)
    os.replace(tmp, path)

def append(path, content):
    put(path, Path(path).read_text() + content)

def installed(pkg, root=None):
    cmd = ["dpkg-query", "-W", "-f=" + "$" + "{Status}", pkg]
    if root:
        cmd = ["chroot", str(root)] + cmd
    p = sp.run(cmd, capture_output=True, text=True)
    return p.returncode == 0 and p.stdout == "install ok installed"

def packages(pkgs, root=None, interactive=False):
    prefix = ["chroot", str(root)] if root else []
    missing = [p for p in dict.fromkeys(pkgs) if not installed(p, root)]
    for p in dict.fromkeys(pkgs):
        print(("[FALTA] " if p in missing else "[OK] ") + p, flush=True)
    if missing:
        env = dict(os.environ)
        if not interactive:
            env["DEBIAN_FRONTEND"] = "noninteractive"
        run(*prefix, "apt-get", "-o", "APT::Update::Error-Mode=any", "update")
        run(*prefix, "apt-get", "-s", "install", "--no-install-recommends", *missing, quiet=True)
        run(*prefix, "apt-get", "install", "-y", "--no-install-recommends", "--no-upgrade", *missing, env=env)
    check(all(installed(p, root) for p in pkgs), "Faltan paquetes después de APT.")
    run(*prefix, "apt-get", "clean")

def has_opcache(v):
    return ok("chroot", TPL, "/usr/bin/php" + version(v), "-r", 'exit(function_exists("opcache_get_status") ? 0 : 1);')


def ensure_opcache(v):
    # PHP 8.5+ lo integra; versiones anteriores pueden necesitar el paquete separado.
    if not has_opcache(v):
        packages([f"php{version(v)}-opcache"], TPL)
    check(has_opcache(v), f"OPcache no disponible para PHP {v}.")
    print(f"[OK] OPcache PHP {v}: disponible en el runtime.")


def disk(minimum=3, path="/srv"):
    free = shutil.disk_usage(path).free
    print(f"Espacio libre en {path}: {free/GIB:.2f} GiB; reserva requerida: {minimum:.2f} GiB")
    check(free >= minimum * GIB, "Espacio insuficiente. Amplía el disco o libera espacio antes de continuar.")

def slug(name):
    v = unicodedata.normalize("NFKD", name.strip()).encode("ascii", "ignore").decode().lower()
    v = re.sub(r"[^a-z0-9]+", "-", v).strip("-")[:24].rstrip("-")
    if v and v[0].isdigit():
        v = "a-" + v
    check(v and v not in RESERVED and re.fullmatch(r"[a-z][a-z0-9-]{0,30}", v), f"Nombre reservado o inválido: {name!r}")
    return v

def valid_user(u):
    check(re.fullmatch(r"[a-z][a-z0-9-]{0,30}", u) and u not in RESERVED, "Usuario inválido.")
    return u

def relative(s):
    check(re.fullmatch(r"[A-Za-z0-9_./-]+", s) and not s.startswith("/") and all(x not in ("", ".", "..") for x in s.split("/")), "Ruta relativa inválida.")
    return s

def version(v):
    check(re.fullmatch(r"[0-9]+\.[0-9]+", v), "Versión: major.minor (ejemplo 8.4).")
    return v

def mysql(sql, capture=False):
    return run("mysql", "--protocol=socket", "-uroot", "-NB", capture=capture, data=sql)

def pg(sql, capture=False):
    return run("runuser", "-u", "postgres", "--", "psql", "-X", "-v", "ON_ERROR_STOP=1", "-At", "-d", "postgres", capture=capture, data=sql)

def state():
    check(READY.is_file() and STATE.is_file(), "No hay una instalación completa. Consulta el log; no sobrescribas un intento parcial.")
    s = json.loads(STATE.read_text())
    # Compatibilidad con instalaciones anteriores: conservar su base original.
    for u, d in s["alumnos"].items():
        d.setdefault("mysql_dbs", [u])
        d.setdefault("password_changed", {})
    return s

def save(s):
    put(STATE, json.dumps(s, ensure_ascii=False, indent=2) + "\n", 0o600)
    path = Path("/root/credenciales-aula.csv")
    tmp = path.with_suffix(".tmp")
    fd = os.open(tmp, os.O_CREAT | os.O_TRUNC | os.O_WRONLY | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, "w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(["nombre", "usuario", "clave_ssh", "mysql", "clave_mysql", "postgresql", "clave_postgresql", "web", "activo", "bases_mysql"])
        for u, d in s["alumnos"].items():
            def label(kind):
                return "CAMBIADA (no almacenada)" if d.get("password_changed", {}).get(kind) else u
            w.writerow([d["nombre"], u, label("ssh"), u, label("mysql"), u, label("postgresql"),
                        f"http://{HOST}/{u}/", d["activo"], ";".join(d.get("mysql_dbs", [u]))])
    os.chmod(tmp, 0o600)
    os.replace(tmp, path)

def jail(u):
    return JAILS / valid_user(u)

def as_user(u, *cmd, capture=False, env=None):
    p = pwd.getpwnam(u)
    return run("chroot", "--groups=", f"--userspec={p.pw_uid}:{p.pw_gid}", jail(u), *cmd, capture=capture, env=env)

def student(s, u, active=True):
    valid_user(u)
    check(u in s["alumnos"], f"No existe {u}.")
    d = s["alumnos"][u]
    check(not active or d["activo"], "Cuenta suspendida.")
    return d

def private_dir(u, path, mode=0o700):
    p = pwd.getpwnam(u)
    path.mkdir(parents=True, exist_ok=True)
    os.chown(path, p.pw_uid, p.pw_gid)
    os.chmod(path, mode)

def port(s):
    used = {d[k] for d in s["alumnos"].values() for k in ("port_web", "port_app")}
    for n in range(18000, 20000):
        if n in used:
            continue
        with socket.socket() as x:
            try:
                x.bind(("127.0.0.1", n))
                return n
            except OSError:
                continue
    raise Error("No quedan puertos internos.")

def unitname(u, kind):
    return f"hosting-aula-{kind}-{u}.service"

def sdquote(s):
    check(isinstance(s, str) and all(32 <= ord(c) != 127 for c in s), "Argumento no válido para systemd.")
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("%", "%%") + '"'

def limits(u, d):
    put(UNITS / f"hosting-aula-{u}.slice", f"""[Unit]
Description=Recursos del hosting de {u}
[Slice]
CPUQuota={d["cpu"]}%
MemoryMax={d["mem"]}M
TasksMax=128
""")

def service(u, kind, argv, env=None, oneshot=False, after=""):
    check(argv and argv[0].startswith("/") and ".." not in argv[0].split("/"), "Ejecutable absoluto interno requerido.")
    args = " ".join(sdquote(a) for a in argv)
    # El prefijo : desactiva la expansión de variables de systemd; no se usa shell.
    config = f"""[Unit]
Description=Hosting {kind} de {u}
RequiresMountsFor={jail(u)}/usr
After=network.target {after}
[Service]
Type={"oneshot" if oneshot else "simple"}
User={u}
Group={u}
RootDirectory={jail(u)}
WorkingDirectory=/home/{u}
MountAPIVFS=no
NoNewPrivileges=yes
CapabilityBoundingSet=
UMask=0027
Slice=hosting-aula-{u}.slice
Environment=HOME=/home/{u}
Environment=PATH=/usr/local/bin:/usr/bin:/bin
ExecStart=:{args}
"""
    for k, v in (env or {}).items():
        check(re.fullmatch(r"[A-Z_][A-Z0-9_]*", k), "Variable inválida.")
        config += "Environment=" + sdquote(k + "=" + str(v)) + "\n"
    if not oneshot:
        config += "Restart=on-failure\nRestartSec=2\n[Install]\nWantedBy=multi-user.target\n"
    else:
        config += "TimeoutStartSec=15min\n"
    put(UNITS / unitname(u, kind), config)

def ctl(*args):
    return run("systemctl", *args)

def wait_for(predicate, label, seconds=30):
    for _ in range(seconds):
        if predicate():
            return
        time.sleep(1)
    raise Error("No está disponible: " + label)

def mounted(u):
    p = jail(u) / "usr"
    o = run("findmnt", "-n", "-o", "VFS-OPTIONS", "--mountpoint", p, capture=True)
    check({"ro", "nosuid", "nodev"}.issubset(o.split(",")), f"Montaje inseguro: {p}: {o}")

def fix_shell_profile(root):
    # systemd OSC 3008 necesita /proc; las jaulas deliberadamente no lo montan.
    # Máscara oficial de tmpfiles, limitada a la plantilla y las jaulas.
    root = Path(root)
    check(root == TPL or (root.parent == JAILS and not root.is_symlink()), "Raíz de jaula inválida.")
    check(root.is_dir() and not root.is_symlink() and root.stat().st_uid == 0, "Raíz de jaula no segura.")
    profile = root / "etc/profile.d/80-systemd-osc-context.sh"
    mask = root / "etc/tmpfiles.d/20-systemd-osc-context.conf"
    for parent in (root / "etc", profile.parent, mask.parent):
        check(not parent.is_symlink(), "Directorio de configuración inesperado.")
        parent.mkdir(exist_ok=True)
        check(parent.stat().st_uid == 0 and not parent.stat().st_mode & 0o022, "Directorio de configuración no seguro.")
    if os.path.lexists(mask):
        check(mask.is_symlink() and os.readlink(mask) == "/dev/null",
              f"Máscara personalizada existente: {mask}. No se modifica.")
    else:
        mask.symlink_to("/dev/null")
    if profile.is_symlink():
        profile.unlink()
    elif profile.is_file():
        original = profile.read_text()
        check("__systemd_osc_context" in original, f"Perfil personalizado: {profile}. No se modifica.")
        backup = profile.with_suffix(".sh.disabled")
        check(not os.path.lexists(backup), f"Ya existe {backup}.")
        profile.rename(backup)
    print(f"[OK] Terminal sin integración OSC de systemd: {root.name}")


def sync_php(u, d):
    # Destinos root-owned, nunca se sincronizan directorios editables por alumnos.
    j = jail(u)
    for rel in ("etc/php", "etc/ssl/certs"):
        src = TPL / rel
        if src.is_dir():
            # cp -aT conserva enlaces absolutos internos y reemplaza los ya existentes.
            # copytree(symlinks=True, dirs_exist_ok=True) falla con EEXIST.
            run("cp", "-aT", src, j / rel)
    a = j / "etc/alternatives/php"
    a.parent.mkdir(exist_ok=True)
    a.unlink(missing_ok=True)
    a.symlink_to("/usr/bin/php" + d["php"])
    p = j / "etc/php" / d["php"] / "cli/conf.d/99-aula.ini"
    put(p, "memory_limit=512M\nmax_input_vars=5000\n")

def php_config(u, d):
    j = jail(u)
    v = version(d["php"])
    check((TPL / f"usr/sbin/php-fpm{v}").is_file(), f"PHP {v} no está instalado en la plantilla.")
    put(j / "etc/aula/fpm.conf", f"""[global]
daemonize = no
error_log = /run/aula/fpm/error.log
pid = /run/aula/fpm/fpm.pid
[aula]
user = {u}
group = {u}
listen = /run/aula/fpm/php.sock
listen.mode = 0600
pm = ondemand
pm.max_children = 2
pm.process_idle_timeout = 15s
pm.max_requests = 200
clear_env = yes
chdir = /home/{u}
security.limit_extensions = .php
request_terminate_timeout = 180s
php_admin_value[memory_limit] = 512M
php_admin_value[max_input_vars] = 5000
php_admin_value[post_max_size] = 128M
php_admin_value[upload_max_filesize] = 128M
php_admin_value[session.save_path] = /home/{u}/tmp
php_admin_value[upload_tmp_dir] = /home/{u}/tmp
php_admin_flag[log_errors] = on
php_admin_flag[display_errors] = off
""")
    service(u, "fpm", [f"/usr/sbin/php-fpm{v}", "-F", "-y", "/etc/aula/fpm.conf", "-d", "opcache.enable=0", "-d", "cgi.fix_pathinfo=1"])
    as_user(u, f"/usr/sbin/php-fpm{v}", "-t", "-y", "/etc/aula/fpm.conf", "-d", "opcache.enable=0", "-d", "cgi.fix_pathinfo=1")

def apache_config(u, d):
    j = jail(u)
    v = d["docroot"]
    path = f"/web/{u}" + ("/" + relative(v) if v != "." else "")
    mods = ""
    for m in MODULES:
        check((TPL / f"usr/lib/apache2/modules/mod_{m}.so").is_file(), f"Falta módulo {m}.")
        mods += f"LoadModule {m}_module /usr/lib/apache2/modules/mod_{m}.so\n"
    fallback = f"    FallbackResource /{u}/r.php\n" if d.get("moodle") else ""
    put(j / "etc/aula/apache.conf", f"""ServerRoot /etc/aula
ServerName {HOST}:80
Listen 127.0.0.1:{d["port_web"]}
ServerTokens Prod
ServerSignature Off
UseCanonicalName Off
UseCanonicalPhysicalPort Off
PidFile /run/aula/http/apache.pid
DefaultRuntimeDir /run/aula/http
Mutex file:/run/aula/http default
ErrorLog /run/aula/http/error.log
LogLevel warn
User {u}
Group {u}
{mods}
ServerLimit 1
ThreadLimit 16
StartServers 1
ThreadsPerChild 8
MinSpareThreads 1
MaxSpareThreads 16
MaxRequestWorkers 8
KeepAlive On
KeepAliveTimeout 2
Timeout 180
ProxyRequests Off
ProxyTimeout 180
TypesConfig /etc/mime.types
DocumentRoot /var/empty/aula
DirectoryIndex index.php index.html
<Directory />
    AllowOverride None
    Require all denied
</Directory>
Alias "/{u}/" "{path}/"
<Directory "{path}">
    Options FollowSymLinks
    AllowOverride All
    Require all granted
    AcceptPathInfo On
    <FilesMatch "(?i)\\.php$">
        SetHandler "proxy:unix:/run/aula/fpm/php.sock|fcgi://localhost/"
    </FilesMatch>
    <FilesMatch "(?i)^(?:\\..*|.*\\.(?:phtml|phar|php[0-9]+|inc|sql|bak|ini))$">
        Require all denied
    </FilesMatch>
{fallback}</Directory>
""")
    service(u, "web", ["/usr/sbin/apache2", "-f", "/etc/aula/apache.conf", "-DFOREGROUND"], after=unitname(u, "fpm"))
    as_user(u, "/usr/sbin/apache2", "-t", "-f", "/etc/aula/apache.conf")

def routes(s):
    cfg = "# Generado por Hosting Aula. No editar a mano.\nServerAlias apw.local\nProxyPreserveHost On\nProxyRequests Off\n"
    for u, d in sorted(s["alumnos"].items()):
        if not d["activo"]:
            continue
        upstream = f'http://127.0.0.1:{d["port_web"]}/{u}/'
        if d["mode"] == "app":
            upstream = f'http://127.0.0.1:{d["port_app"]}/'
        cfg += f"""RedirectMatch 302 "^/{u}$" "/{u}/"
ProxyPass "/{u}/" "{upstream}" connectiontimeout=5 timeout=180 upgrade=websocket
ProxyPassReverse "/{u}/" "{upstream}"
<Location "/{u}/">
    Require all granted
    SetHandler proxy-server
    RequestHeader set X-Forwarded-Prefix "/{u}"
    RequestHeader set X-Forwarded-Proto "expr=%{{REQUEST_SCHEME}}"
</Location>
"""
    old = FRONT.read_text() if FRONT.exists() else ""
    put(FRONT, cfg)
    try:
        run("apache2ctl", "configtest")
        ctl("reload", "apache2")
    except Error:
        put(FRONT, old)
        run("apache2ctl", "configtest")
        ctl("reload", "apache2")
        raise

def init_template(codename, full):
    arch = run("dpkg", "--print-architecture", capture=True)
    mirror = "http://archive.ubuntu.com/ubuntu" if arch == "amd64" else "http://ports.ubuntu.com/ubuntu-ports"
    check(Path("/usr/share/debootstrap/scripts", codename).exists(), f"debootstrap no reconoce {codename}.")
    run("debootstrap", "--variant=minbase", "--components=main,universe", codename, TPL, mirror)
    check(all((TPL / x).is_symlink() for x in ("bin", "sbin", "lib")), "La plantilla debe usar usrmerge.")
    (TPL / "etc/resolv.conf").unlink(missing_ok=True)
    shutil.copyfile("/etc/resolv.conf", TPL / "etc/resolv.conf")
    put(TPL / "usr/sbin/policy-rc.d", "#!/bin/sh\nexit 101\n", 0o755)
    put(TPL / "etc/apt/sources.list", "".join(f"deb {mirror} {suite} main universe\n" for suite in (codename, codename+"-updates", codename+"-security")))
    packages(CORE + PHPPKG + (EXTRA if full else []), TPL)
    for f in (TPL / "var/lib/apt/lists").glob("*"):
        if f.is_file():
            f.unlink()
    # Se eliminan setuid/setgid; el montaje nosuid es una segunda protección.
    run("find", TPL / "usr", "-xdev", "-type", "f", "-perm", "/6000", "-exec", "chmod", "a-s", "{}", "+")
    php = run("chroot", TPL, "/usr/bin/php", "-r", 'echo PHP_MAJOR_VERSION.".".PHP_MINOR_VERSION;', capture=True)
    ensure_opcache(php)
    fix_shell_profile(TPL)
    for tool in ("php", "node", "npm", "python3", "composer", "git", "wget", "curl", "zip", "unzip", "mysql", "psql", "redis-server", "sqlite3"):
        run("chroot", TPL, "/bin/sh", "-c", 'command -v "$1"', "sh", tool, quiet=True)
    return version(php)

def host_setup():
    packages(HOSTPKG)
    if not installed("phpmyadmin"):
        print("Instalando phpMyAdmin: selecciona apache2 y acepta dbconfig-common.")
        run("debconf-set-selections", data="phpmyadmin phpmyadmin/reconfigure-webserver multiselect apache2\nphpmyadmin phpmyadmin/dbconfig-install boolean true\n")
        packages(["phpmyadmin"], interactive=True)
    v = run("php", "-r", 'echo PHP_MAJOR_VERSION.".".PHP_MINOR_VERSION;', capture=True)
    packages([f"php{version(v)}-fpm"])
    for p in Path("/etc/apache2/mods-enabled").glob("php*.load"):
        run("a2dismod", p.stem)
    run("a2enmod", "proxy", "proxy_http", "proxy_fcgi", "alias", "setenvif", "rewrite", "headers", "expires", "deflate")
    run("a2enconf", f"php{v}-fpm")
    if Path("/etc/phpmyadmin/apache.conf").is_file():
        link = Path("/etc/apache2/conf-available/phpmyadmin.conf")
        if not link.exists():
            link.symlink_to("/etc/phpmyadmin/apache.conf")
        run("a2enconf", "phpmyadmin")
    run("hostnamectl", "set-hostname", "apw")
    hosts = Path("/etc/hosts").read_text()
    if not any("apw" in ln.split()[1:] for ln in hosts.splitlines() if ln.startswith("127.0.1.1")):
        append("/etc/hosts", "\n127.0.1.1 apw\n")
    put("/etc/apache2/conf-available/hosting-aula-servername.conf", "ServerName apw.local\n")
    run("a2enconf", "hosting-aula-servername")
    ctl("enable", "--now", "apache2", "mysql", "postgresql", f"php{v}-fpm", "avahi-daemon")
    ctl("start", "ssh")
    run("apache2ctl", "configtest")
    ctl("restart", "apache2")
    mysql("SELECT 1;")
    pg("SELECT 1;")
    check(not mysql("SHOW VARIABLES LIKE 'validate_password%';", capture=True), "MySQL tiene validate_password. No se cambia su política automáticamente.")
    check(SITE.is_file() and Path("/etc/apache2/sites-enabled/000-default.conf").exists(), "Se requiere el sitio HTTP estándar 000-default.conf activo.")
    txt = SITE.read_text()
    check(len(re.findall(r"(?im)^\s*<VirtualHost\b", txt)) == 1 and re.search(r"(?im)^\s*<VirtualHost\s+\*:80\s*>\s*$", txt), "El sitio debe tener un único VirtualHost *:80.")
    check(len(re.findall(r"(?im)^\s*</VirtualHost>\s*$", txt)) == 1, "Cierre de VirtualHost ambiguo.")
    listing = run("apache2ctl", "-S", capture=True)
    check(not re.search(r"(?m)^\*:80\s+is a NameVirtualHost", listing), "Hay varios sitios HTTP; se requiere VM con un sitio HTTP único.")

def default_dbs(u):
    valid_user(u)
    return [f"db_{u}{suffix}" for suffix in ("", "1", "3")]

def owned_dbs(d, u):
    return list(d.get("mysql_dbs", [u]))

def db_ident(name):
    check(re.fullmatch(r"[a-z][a-z0-9_-]{0,63}", name), "Nombre de base de datos inválido.")
    return chr(96) + name + chr(96)

def db_scope(name, partial_revokes):
    db_ident(name)
    # MySQL interpreta _ y % como comodines en GRANT si partial_revokes=OFF.
    return chr(96) + (name if partial_revokes else name.replace("_", "\\_")) + chr(96)

def partial_revokes():
    return mysql("SHOW VARIABLES LIKE 'partial_revokes';", capture=True).split()[-1:] == ["ON"]

def grants_sql(u, name, verb="GRANT", partial=False):
    valid_user(u)
    check(verb in ("GRANT", "REVOKE"), "Operación inválida.")
    connector = "TO" if verb == "GRANT" else "FROM"
    return "".join(f"{verb} ALL PRIVILEGES ON {db_scope(name, partial)}.* {connector} '{u}'@'{h}';\n"
                   for h in ("localhost", "127.0.0.1"))

def db_exists(name):
    db_ident(name)
    return mysql(f"SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='{name}';", capture=True) != "0"

def validate_db_plan(s, users=()):
    # Reserva los nombres por adelantado: ana y ana1, por ejemplo, colisionan.
    owners = {}
    names = set(s["alumnos"]) | set(users)
    for u in sorted(names):
        d = s["alumnos"].get(u)
        dbs = default_dbs(u) + (owned_dbs(d, u) if d is not None else [])
        for name in dbs:
            previous = owners.setdefault(name, u)
            check(previous == u, f"Colisión MySQL: {name} corresponde a {previous} y {u}. Elige otro usuario.")
    return owners

def create_db_sql(name):
    return f"CREATE DATABASE {db_ident(name)} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;\n"

def add_mysql_db(s, u, name):
    d = student(s, u)
    db_ident(name)
    dbs = d.setdefault("mysql_dbs", [u])
    owners = validate_db_plan(s)
    check(owners.get(name, u) == u, f"Base reservada para otro alumno: {name}")
    if name not in dbs:
        check(not db_exists(name), f"Base existente ajena al registro: {name}. No se adopta automáticamente.")
        mysql(create_db_sql(name))
        dbs.append(name)
        save(s)
    elif not db_exists(name):
        mysql(create_db_sql(name))
    mysql(grants_sql(u, name, partial=partial_revokes()))
    print(f"[OK] {u}: {name}")

def mysql_dbs_list(s, u):
    d = student(s, u, False)
    for name in sorted(set(owned_dbs(d, u)) | set(default_dbs(u))):
        managed = name in owned_dbs(d, u)
        print(f"{name:36s} " + ("registrada" if managed else "pendiente de alta") +
              (" | existe" if db_exists(name) else " | no existe"))
    for h in ("localhost", "127.0.0.1"):
        print(mysql(f"SHOW GRANTS FOR '{u}'@'{h}';", capture=True))

def ensure_defaults(s, users):
    validate_db_plan(s)
    # Prevuelo completo: una base ajena nunca se concede a un alumno.
    for u in users:
        d = student(s, u)
        for name in default_dbs(u):
            if name not in owned_dbs(d, u):
                check(not db_exists(name), f"Base ya existente y no registrada: {name}.")
    for u in users:
        for name in default_dbs(u):
            add_mysql_db(s, u, name)

def extra_db(s, u, suffix):
    check(re.fullmatch(r"[0-9]{1,8}", suffix), "Indica un sufijo numérico de 1 a 8 cifras.")
    add_mysql_db(s, u, f"db_{u}{suffix}")

def drop_mysql_db(s, u, name):
    d = student(s, u)
    db_ident(name)
    check(name in owned_dbs(d, u), "La base no pertenece al alumno.")
    check(name not in default_dbs(u), "Las tres bases iniciales no se eliminan desde el panel.")
    check(len(owned_dbs(d, u)) > 3, "Se deben conservar al menos tres bases registradas.")
    check(all(db_exists(x) for x in default_dbs(u)), "Repara primero las tres bases iniciales.")
    check(input(f"BORRADO IRREVERSIBLE. Escribe {name} para confirmar: ") == name, "Cancelado.")
    # Revocar primero para impedir que el alumno la recree tras eliminarla.
    mysql(grants_sql(u, name, verb="REVOKE", partial=partial_revokes()))
    mysql(f"DROP DATABASE IF EXISTS {db_ident(name)};")
    d["mysql_dbs"].remove(name)
    save(s)
    print(f"Eliminada: {name}")

def ask_password(u, reset=False):
    if reset:
        check(input(f"Restablecer contraseña a {u}. Escribe SI: ") == "SI", "Cancelado.")
        return u
    check(sys.stdin.isatty(), "Cambia contraseñas desde una terminal interactiva.")
    p = getpass.getpass("Nueva contraseña (no se muestra): ")
    check(p and len(p) <= 128 and all(32 <= ord(ch) != 127 for ch in p), "Contraseña vacía, demasiado larga o con caracteres de control.")
    check(p == getpass.getpass("Repite la contraseña: "), "Las contraseñas no coinciden.")
    return p

def sql_string(value):
    # Para sesiones con NO_BACKSLASH_ESCAPES (MySQL) o standard_conforming_strings (PG).
    return "'" + value.replace("'", "''") + "'"

def change_password(s, u, kind, reset=False):
    d = student(s, u)
    check(kind in ("mysql", "postgresql", "ssh"), "Tipo de contraseña inválido.")
    p = ask_password(u, reset)
    if kind == "mysql":
        accounts = ", ".join(f"'{u}'@'{h}' IDENTIFIED BY {sql_string(p)}" for h in ("localhost", "127.0.0.1"))
        cmd = ["mysql", "--binary-mode", "--protocol=socket", "-uroot", "-NB"]
        sql = "SET SESSION sql_mode='NO_BACKSLASH_ESCAPES';\nALTER USER " + accounts + ";\n"
    elif kind == "postgresql":
        cmd = ["runuser", "-u", "postgres", "--", "psql", "-X", "-v", "ON_ERROR_STOP=1", "-d", "postgres"]
        sql = f'SET standard_conforming_strings=on;\nALTER ROLE "{u}" PASSWORD {sql_string(p)};\n'
    else:
        cmd = ["chpasswd"]
        sql = f"{u}:{p}\n"
    # Nunca pasar claves en argv ni reproducir stderr potencialmente sensible.
    result = sp.run(cmd, input=sql, text=True, stdout=sp.DEVNULL, stderr=sp.PIPE)
    check(result.returncode == 0, "No se pudo cambiar la contraseña. Comprueba la política y los registros del servicio.")
    d.setdefault("password_changed", {})[kind] = p != u
    save(s)
    print(f"[OK] Contraseña {kind} actualizada para {u}.")
    if kind != "ssh":
        print("Actualiza también las credenciales en las aplicaciones de este alumno si procede.")

def precheck_name(u):
    check(not ok("getent", "passwd", u) and not ok("getent", "group", u), "Cuenta Linux existente: " + u)
    check(not os.path.lexists("/home/" + u) and not os.path.lexists(str(jail(u))) and not os.path.lexists("/var/www/html/" + u), "Ruta existente: " + u)
    precheck_databases(u)


def precheck_databases(u):
    valid_user(u)
    check(mysql(f"SELECT COUNT(*) FROM mysql.user WHERE User='{u}';", capture=True) == "0", "Cuenta MySQL existente: " + u)
    for name in default_dbs(u):
        check(not db_exists(name), "BD MySQL existente: " + name)
    check(pg(f"SELECT count(*) FROM pg_roles WHERE rolname='{u}';", capture=True) == "0", "Rol PostgreSQL existente: " + u)
    check(pg(f"SELECT count(*) FROM pg_database WHERE datname='{u}';", capture=True) == "0", "BD PostgreSQL existente: " + u)

def new_student(s, name, u):
    precheck_name(u)
    validate_db_plan(s, [u])
    disk(0.6)
    d = {"mysql_dbs": default_dbs(u), "password_changed": {}, "nombre": name, "php": s["php_default"], "docroot": ".", "moodle": False, "activo": True, "mode": "php", "port_web": port(s), "port_app": 0, "cpu": 100, "mem": 1024, "redis": False, "app": [], "cron": []}
    s["alumnos"][u] = d
    d["port_app"] = port(s)
    run("useradd", "-M", "-U", "-d", "/home/" + u, "-s", "/bin/bash", "-G", GROUP, u)
    j = jail(u)
    j.mkdir(mode=0o755)
    # Una única copia de usr; las configuraciones y dispositivos mínimos son privados.
    a = sp.Popen(["tar", "-C", str(TPL), "--one-file-system", "--exclude=./usr", "-cpf", "-", "."], stdout=sp.PIPE)
    b = sp.run(["tar", "-C", str(j), "--numeric-owner", "-xpf", "-"], stdin=a.stdout)
    a.stdout.close()
    check(a.wait() == 0 and b.returncode == 0, "No se pudo copiar la estructura privada.")
    os.chmod(j, 0o755)
    (j / "usr").mkdir()
    run("mount", "--bind", TPL / "usr", j / "usr")
    run("mount", "-o", "remount,bind,ro,nosuid,nodev", j / "usr")
    mounted(u)
    append("/etc/fstab", f"\n{TPL}/usr {j}/usr none bind,ro,nosuid,nodev 0 0\n")
    p = pwd.getpwnam(u)
    # groupadd/useradd usan la raíz privada, y se ejecutan antes de habilitar SSH.
    run("chroot", j, "groupadd", "-g", p.pw_gid, u)
    run("chroot", j, "useradd", "-m", "-u", p.pw_uid, "-g", p.pw_gid, "-d", "/home/" + u, "-s", "/bin/bash", u)
    private_dir(u, j / "home" / u)
    for rel in ("tmp", "moodledata", ".ssh", "apps"):
        private_dir(u, j / "home" / u / rel)
    (j / "web").mkdir(mode=0o755, exist_ok=True)
    private_dir(u, j / "web" / u, 0o755)
    (j / "home" / u / "public_html").symlink_to(f"../../web/{u}")
    Path("/home", u).symlink_to(j / "home" / u)
    (j / "run/aula").mkdir(parents=True, exist_ok=True)
    for rel in ("fpm", "http", "redis"):
        private_dir(u, j / "run/aula" / rel)
    (j / "var/empty/aula").mkdir(parents=True, exist_ok=True)
    (j / "etc/aula").mkdir(exist_ok=True)
    finish_student(s, u, d)


def finish_student(s, u, d):
    j = jail(u)
    fix_shell_profile(j)
    sync_php(u, d)
    # Cada alumno recibe tres bases MySQL y permiso exacto sobre cada una.
    mysql("".join(f"CREATE USER '{u}'@'{h}' IDENTIFIED BY '{u}' WITH MAX_USER_CONNECTIONS 8;\n" for h in ("localhost", "127.0.0.1")))
    partial = partial_revokes()
    for name_db in d["mysql_dbs"]:
        mysql(create_db_sql(name_db) + grants_sql(u, name_db, partial=partial))
    pg(f"CREATE ROLE \"{u}\" LOGIN PASSWORD '{u}' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION CONNECTION LIMIT 8;\n")
    pg(f'CREATE DATABASE "{u}" OWNER "{u}";\n')
    pg(f'REVOKE ALL ON DATABASE "{u}" FROM PUBLIC;\n')
    limits(u, d)
    php_config(u, d)
    apache_config(u, d)
    # Ejecutar las escrituras en espacio del alumno con su UID, no como root.
    as_user(u, "/bin/sh", "-c", 'printf "%s\n" "$1" > "$2/index.php"; printf "%s\n" "$3" > "$2/.htaccess"; printf "%s\n" privado > "$2/privado-aula.txt"', "sh",
            '<?php echo "AULA_OK|".PHP_VERSION."|".($_SERVER["PATH_INFO"]??"");', f"/web/{u}", "RewriteEngine On\nRewriteRule ^prueba-aula$ index.php [L]\n<Files privado-aula.txt>\nRequire all denied\n</Files>")
    ctl("daemon-reload")
    ctl("enable", "--now", unitname(u, "fpm"))
    wait_for(lambda: (j / "run/aula/fpm/php.sock").is_socket(), "socket PHP de " + u)
    ctl("enable", "--now", unitname(u, "web"))
    wait_for(lambda: ok("curl", "--noproxy", "*", "-fsS", "--max-time", "3", f'http://127.0.0.1:{d["port_web"]}/{u}/'), "Apache individual de " + u)
    routes(s)
    verify_student(s, u, d)


def verify_student(s, u, d):
    def fetch(path, host=HOST, ip="127.0.0.1"):
        return run("curl", "--noproxy", "*", "--max-time", "15", "--resolve", f"{host}:80:{ip}", "-fsS", f"http://{host}/{u}/{path}", capture=True)
    check(fetch("prueba-aula").startswith("AULA_OK|"), "Falló rewrite/PHP.")
    check(fetch("index.php/segmento").endswith("|/segmento"), "Falló PATH_INFO.")
    status = run("curl", "--noproxy", "*", "--resolve", HOST + ":80:127.0.0.1", "-sS", "-o", "/dev/null", "-w", "%{http_code}", f"http://{HOST}/{u}/privado-aula.txt", capture=True)
    check(status == "403", "No se aplica la protección .htaccess.")
    ips = run("ip", "-4", "-o", "addr", "show", "scope", "global", capture=True)
    for line in ips.splitlines():
        ip = line.split()[3].split("/")[0]
        check(run("curl", "--noproxy", "*", "-fsS", "--max-time", "10", f"http://{ip}/{u}/", capture=True).startswith("AULA_OK|"), "Falló acceso por IP: " + ip)
    for name_db in d["mysql_dbs"]:
        check(as_user(u, "mysql", "--protocol=TCP", "-h", "127.0.0.1", "-u", u, "-NBe", "SELECT DATABASE()", name_db, capture=True, env=dict(os.environ, MYSQL_PWD=u)) == name_db, "Falló acceso MySQL: " + name_db)
    check(as_user(u, "psql", "-h", "127.0.0.1", "-U", u, "-d", u, "-Atc", "SELECT current_database()", capture=True, env=dict(os.environ, PGPASSWORD=u)) == u, "Falló acceso PostgreSQL.")
    effective = run("sshd", "-T", "-C", f"user={u},host=localhost,addr=127.0.0.1", capture=True)
    check(any(x in effective.splitlines() for x in (f"chrootdirectory {JAILS}/%u", f"chrootdirectory {JAILS}/{u}")) and "passwordauthentication yes" in effective.splitlines(), "No se aplica aislamiento SSH.")
    as_user(u, "/bin/rm", "-f", f"/web/{u}/.htaccess", f"/web/{u}/privado-aula.txt")
    as_user(u, "/bin/sh", "-c", 'printf "%s\n" "$1" > "$2/index.php"', "sh", f'<?php echo "<h1>Hosting de {u}</h1>PHP ".PHP_VERSION;', f"/web/{u}")
    run("chpasswd", data=f"{u}:{u}\n")
    save(s)
    print(f"[OK] {u}: HTTP, PHP, rewrite, PATH_INFO, 403, MySQL, PostgreSQL y política SSH. http://{HOST}/{u}/")

def read_students(csvfile):
    names = []
    with open(csvfile, encoding="utf-8-sig", newline="") as f:
        for row in csv.reader(f):
            if not row or not any(x.strip() for x in row):
                continue
            check(len(row) == 1, "El CSV debe contener una sola columna sin cabecera.")
            names.append((row[0].strip(), slug(row[0])))
    check(names and len({u for _, u in names}) == len(names), "CSV vacío o nombres normalizados duplicados; distingue los nombres antes de instalar.")
    return names

def add_students(s, names):
    # Valida todo el lote antes de modificar cuentas; no recicla nombres de alumnos.
    check(names and len({u for _, u in names}) == len(names),
          "Lista vacía o usuarios normalizados duplicados.")
    validate_db_plan(s, [u for _, u in names])
    pending = []
    for name, u in names:
        if u in s["alumnos"]:
            existing = s["alumnos"][u]
            check(existing["nombre"].casefold() == name.casefold(),
                  f"Colisión de usuario {u}: {existing['nombre']} / {name}. Usa nombres distintos.")
            print(f"[EXISTE] {u} ({'activo' if existing['activo'] else 'suspendido'}): sin cambios.")
        else:
            precheck_name(u)
            pending.append((name, u))
    if pending:
        disk(0.6 + len(pending) * 0.08)
    for name, u in pending:
        new_student(s, name, u)
    print(f"Altas completadas: {len(pending)}; existentes sin cambios: {len(names) - len(pending)}.")


def bootstrap(csvfile, full):
    check(not BASE.exists() and not FRONT.exists() and not Path("/root/credenciales-aula.csv").exists(), "Existe un intento anterior de hosting. No se sobrescribe.")
    check(not ok("getent", "group", GROUP), "El grupo del aula ya existe.")
    check("# BEGIN HOSTING AULA" not in SSH.read_text() if SSH.exists() else True, "Ya existe configuración SSH.")
    names = read_students(csvfile)
    validate_db_plan({"alumnos": {}}, [u for _, u in names])
    print("Alumnos:", ", ".join(u for _, u in names))
    osrel = dict(x.split("=", 1) for x in Path("/etc/os-release").read_text().splitlines() if "=" in x)
    check(osrel.get("ID", "").strip('"') == "ubuntu", "Se requiere Ubuntu Server.")
    codename = osrel.get("VERSION_CODENAME", "").strip('"')
    check(re.fullmatch(r"[a-z][a-z0-9-]*", codename), "No se conoce la distribución.")
    disk(5.5 if full else 4.0)
    host_setup()
    for _, u in names:
        precheck_name(u)
    disk((3.0 if full else 2.0) + len(names)*0.08)
    BASE.mkdir(mode=0o755)
    JAILS.mkdir(mode=0o711)
    (BASE / "backups").mkdir(mode=0o700)
    (BASE / "config-original").mkdir(mode=0o700)
    for p in (SITE, SSH, Path("/etc/fstab")):
        shutil.copy2(p, BASE / "config-original" / p.name)
    v = init_template(codename, full)
    s = {"php_default": v, "codename": codename, "full": full, "alumnos": {}}
    save(s)
    run("groupadd", GROUP)
    put("/etc/security/limits.d/90-hosting-aula.conf", f"@{GROUP} hard nproc 100\n@{GROUP} hard nofile 2048\n")
    ssh = SSH.read_text()
    ssh = re.sub(r"(?m)^\s*Subsystem\s+sftp\s+.*$", "Subsystem sftp internal-sftp", ssh)
    check(re.search(r"(?m)^Subsystem sftp internal-sftp$", ssh), "No se encuentra el subsistema SFTP estándar.")
    put(SSH, ssh.rstrip() + f"\n\n# BEGIN HOSTING AULA\nMatch Group {GROUP}\n    ChrootDirectory {JAILS}/%u\n    PasswordAuthentication yes\n    DisableForwarding yes\n    PermitTunnel no\n    X11Forwarding no\n# END HOSTING AULA\n")
    run("sshd", "-t")
    ctl("reload", "ssh")
    put(FRONT, "# Hosting Aula\n")
    put(SITE, re.sub(r"(?im)^\s*</VirtualHost>\s*$", f"    Include {FRONT}\n</VirtualHost>\n", SITE.read_text(), count=1))
    routes(s)
    for name, u in names:
        new_student(s, name, u)
    put(READY, "Instalación terminada\n", 0o600)
    print("Instalación finalizada. Credenciales: /root/credenciales-aula.csv")

def resume_initial(csvfile):
    """Retoma una instalación inicial detenida antes de habilitar el alumno."""
    check(not READY.exists() and STATE.is_file() and TPL.is_dir() and JAILS.is_dir(),
          "No hay una instalación inicial recuperable por esta orden.")
    check(FRONT.is_file() and str(FRONT) in SITE.read_text() and "# BEGIN HOSTING AULA" in SSH.read_text(),
          "No se terminó la configuración global. No se reanuda automáticamente.")
    names = read_students(csvfile)
    s = json.loads(STATE.read_text())
    known = {u: name for name, u in names}
    check(all(known.get(u) == d["nombre"] for u, d in s["alumnos"].items()),
          "Usa el CSV original, sin modificar los alumnos ya creados.")
    validate_db_plan(s, list(known))
    pending = [p.name for p in JAILS.iterdir() if p.name not in s["alumnos"]]
    check(len(pending) <= 1 and all(u in known for u in pending),
          "Hay jaulas parciales ambiguas. No se modifica nada.")
    # Validar antes de continuar: no borrar ni adoptar cuentas o bases ajenas.
    for name, u in names:
        if u in s["alumnos"] or u in pending:
            continue
        precheck_name(u)
    if pending:
        u = pending[0]
        first = next((x for _, x in names if x not in s["alumnos"]), None)
        check(u == first, "La jaula parcial no coincide con el primer alumno pendiente.")
        j = jail(u)
        check(j.is_dir() and not j.is_symlink() and j.stat().st_uid == 0, "Jaula parcial inválida.")
        p = pwd.getpwnam(u)
        check(p.pw_dir == f"/home/{u}" and p.pw_shell == "/bin/bash" and GROUP in run("id", "-nG", u, capture=True).split(),
              "La cuenta no coincide con el alojamiento.")
        check(run("passwd", "-S", u, capture=True).split()[1] == "L", "La cuenta no está bloqueada.")
        check(Path("/home", u).is_symlink() and os.readlink(Path("/home", u)) == str(j/"home"/u),
              "El home no coincide con la jaula.")
        mounted(u)
        check(f"{TPL}/usr {j}/usr none bind,ro,nosuid,nodev 0 0" in Path("/etc/fstab").read_text(),
              "Falta el montaje persistente de la jaula.")
        check((j/"web"/u).is_dir() and not (j/"web"/u).is_symlink(), "Directorio web parcial inválido.")
        check(all((j / rel).is_dir() for rel in ("etc/aula", "run/aula/fpm", "run/aula/http", "run/aula/redis", "var/empty/aula")),
              "La estructura de la jaula está incompleta.")
        web = j / "web" / u
        entries = {p.name for p in web.iterdir()}
        d = {"mysql_dbs": default_dbs(u), "password_changed": {}, "nombre": known[u],
             "php": s["php_default"], "docroot": ".", "moodle": False, "activo": True,
             "mode": "php", "port_web": 0, "port_app": 0, "cpu": 100,
             "mem": 1024, "redis": False, "app": [], "cron": []}
        if not entries:
            check(not any((UNITS / unitname(u, k)).exists() for k in ("web", "fpm", "redis", "app")),
                  "Ya existen servicios del alumno. Requiere revisión manual.")
            precheck_databases(u)
            d["port_web"] = port(s)
            s["alumnos"][u] = d
            d["port_app"] = port(s)
            print(f"Continuando alta incompleta de {u}; se conservan plantilla, cuenta y montaje.")
            finish_student(s, u, d)
        else:
            # Fase de verificación HTTP: no volver a crear cuentas, bases o archivos.
            check(entries == {"index.php", ".htaccess", "privado-aula.txt"},
                  "La web parcial contiene archivos distintos de la prueba. No se sobrescriben.")
            check(all((web / n).is_file() and not (web / n).is_symlink() for n in entries),
                  "Archivos de prueba inválidos.")
            expected = {'index.php': '<?php echo "AULA_OK|".PHP_VERSION."|".($_SERVER["PATH_INFO"]??"");',
                        '.htaccess': "RewriteEngine On\nRewriteRule ^prueba-aula$ index.php [L]\n<Files privado-aula.txt>\nRequire all denied\n</Files>",
                        'privado-aula.txt': "privado"}
            for n, value in expected.items():
                check(as_user(u, "/usr/bin/cat", f"/web/{u}/{n}", capture=True) == value,
                      f"Archivo de prueba modificado: {n}. No se sobrescribe.")
            check(all((UNITS / unitname(u, k)).is_file() for k in ("web", "fpm")) and
                  not any((UNITS / unitname(u, k)).exists() for k in ("redis", "app")),
                  "Los servicios no corresponden a una alta pendiente.")
            for k in ("web", "fpm"):
                unit = (UNITS / unitname(u, k)).read_text()
                check(f"RootDirectory={j}\n" in unit and f"User={u}\n" in unit, "Unidad inesperada.")
            ac = (j / "etc/aula/apache.conf").read_text()
            match = re.search(r"(?m)^Listen 127\.0\.0\.1:([0-9]+)$", ac)
            check(match and 18000 <= int(match.group(1)) < 20000, "No se conoce el puerto de la jaula parcial.")
            d["port_web"] = int(match.group(1))
            check(d["port_web"] not in {x[k] for x in s["alumnos"].values() for k in ("port_web", "port_app")},
                  "Puerto interno duplicado.")
            check(f'ProxyPass "/{u}/" "http://127.0.0.1:{d["port_web"]}/{u}/"' in FRONT.read_text(),
                  "La ruta del proxy no coincide.")
            check(all(db_exists(n) for n in d["mysql_dbs"]), "Faltan bases MySQL: no se reanudará a ciegas.")
            check(pg(f"SELECT count(*) FROM pg_database WHERE datname='{u}';", capture=True) == "1",
                  "Falta la base PostgreSQL.")
            s["alumnos"][u] = d
            d["port_app"] = port(s)
            print(f"Reanudando verificación de {u}; conservando cuentas, bases y archivos.")
            php_config(u, d)
            ctl("daemon-reload")
            ctl("enable", "--now", unitname(u, "fpm"), unitname(u, "web"))
            ctl("restart", unitname(u, "fpm"), unitname(u, "web"))
            wait_for(lambda: ok("curl", "--noproxy", "*", "-fsS", "--max-time", "3", f'http://127.0.0.1:{d["port_web"]}/{u}/'), "Apache y PHP de " + u)
            routes(s)
            verify_student(s, u, d)
    for name, u in names:
        if u not in s["alumnos"]:
            new_student(s, name, u)
    put(READY, "Instalación terminada\n", 0o600)
    print("Instalación finalizada. Credenciales: /root/credenciales-aula.csv")


def app(s, u, argv):
    d = student(s, u)
    check(argv and argv[0].startswith("/") and ".." not in argv[0].split("/"), "Usa el ejecutable absoluto dentro de la jaula.")
    as_user(u, "/usr/bin/test", "-x", argv[0])
    service(u, "app", argv, {"PORT": d["port_app"], "HOST": "127.0.0.1", "BASE_PATH": f"/{u}/", "NODE_ENV": "production", "PYTHONUNBUFFERED": "1"})
    ctl("daemon-reload")
    ctl("enable", "--now", unitname(u, "app"))
    ctl("restart", unitname(u, "app"))
    wait_for(lambda: ok("curl", "--noproxy", "*", "--max-time", "2", "-sS", "-o", "/dev/null", f'http://127.0.0.1:{d["port_app"]}/'), "aplicación (debe escuchar en HOST y PORT)")
    d["app"] = argv
    d["mode"] = "app"
    routes(s)
    save(s)
    print(f"Aplicación activa: http://{HOST}/{u}/ (el proxy elimina el prefijo de la petición).")

def switch_php(s, u, v):
    d = student(s, u)
    old = d["php"]
    check((TPL / f"usr/sbin/php-fpm{version(v)}").is_file(), "Instala esa versión con --instalar-php.")
    d["php"] = v
    try:
        sync_php(u, d)
        php_config(u, d)
        ctl("daemon-reload")
        ctl("restart", unitname(u, "fpm"))
        wait_for(lambda: ok("systemctl", "is-active", "--quiet", unitname(u, "fpm")) and (jail(u) / "run/aula/fpm/php.sock").is_socket(), "nuevo PHP")
        as_user(u, "/usr/bin/php", "-v")
    except Error:
        d["php"] = old
        sync_php(u, d)
        php_config(u, d)
        ctl("daemon-reload")
        ctl("restart", unitname(u, "fpm"))
        raise
    save(s)

def documentroot(s, u, sub, moodle):
    d = student(s, u)
    path = f"/web/{u}" + ("/" + relative(sub) if sub != "." else "")
    actual = as_user(u, "/usr/bin/realpath", "-e", path, capture=True)
    check(actual == f"/web/{u}" or actual.startswith(f"/web/{u}/"), "La ruta debe quedar dentro de public_html.")
    as_user(u, "/usr/bin/test", "-d", path)
    if moodle:
        as_user(u, "/usr/bin/test", "-f", path + "/r.php")
    old = d.copy()
    d.update(docroot=sub, moodle=moodle)
    try:
        apache_config(u, d)
        ctl("daemon-reload")
        ctl("restart", unitname(u, "web"))
    except Error:
        apache_config(u, old)
        ctl("daemon-reload")
        ctl("restart", unitname(u, "web"))
        raise
    save(s)

def cron(s, u, task, minutes, path):
    d = student(s, u)
    check(re.fullmatch(r"[a-z][a-z0-9-]{0,30}", task) and 1 <= minutes <= 10080, "Nombre o intervalo inválido.")
    check(path.startswith(f"/home/{u}/") or path.startswith(f"/web/{u}/"), "Ruta absoluta dentro del espacio del alumno.")
    relative(path[1:])
    as_user(u, "/usr/bin/test", "-f", path)
    executable = {".php": "/usr/bin/php", ".sh": "/bin/bash", ".py": "/usr/bin/python3", ".js": "/usr/bin/node"}.get(Path(path).suffix)
    check(executable, "Extensión permitida: php, sh, py, js.")
    kind = "cron-" + task
    # El separador punto distingue nombres de alumnos con guiones.
    kind = "cron." + u + "." + task
    service(u, kind, [executable, path], oneshot=True)
    name = unitname(u, kind)
    timer = name.replace(".service", ".timer")
    put(UNITS / timer, f"""[Unit]
Description=Tarea programada {u}/{task}
[Timer]
OnBootSec=1min
OnUnitInactiveSec={minutes}min
AccuracySec=1s
Unit={name}
[Install]
WantedBy=timers.target
""")
    ctl("daemon-reload")
    ctl("enable", "--now", timer)
    if task not in d["cron"]:
        d["cron"].append(task)
    save(s)

def redis(s, u):
    d = student(s, u)
    service(u, "redis", ["/usr/bin/redis-server", "--port", "0", "--unixsocket", "/run/aula/redis/redis.sock", "--unixsocketperm", "700", "--save", "", "--appendonly", "no", "--maxmemory", "64mb", "--maxmemory-policy", "allkeys-lru", "--daemonize", "no"])
    ctl("daemon-reload")
    ctl("enable", "--now", unitname(u, "redis"))
    wait_for(lambda: (jail(u) / "run/aula/redis/redis.sock").is_socket(), "Redis")
    check(as_user(u, "redis-cli", "-s", "/run/aula/redis/redis.sock", "PING", capture=True) == "PONG", "Redis no contesta.")
    d["redis"] = True
    save(s)

def backup(s, u):
    d = student(s, u, False)
    folder = BASE / "backups" / f"{u}-{time.strftime('%Y%m%d-%H%M%S')}"
    folder.mkdir(mode=0o700)
    run("tar", "--one-file-system", "--numeric-owner", "-C", jail(u), "-czf", folder/"archivos.tar.gz", f"home/{u}", f"web/{u}")
    dbs = [name for name in owned_dbs(d, u) if db_exists(name)]
    missing = sorted(set(owned_dbs(d, u)) - set(dbs))
    if missing:
        print("Aviso: bases registradas que no existen:", ", ".join(missing))
    with open(folder/"mysql.sql", "w") as f:
        if dbs:
            run("mysqldump", "--protocol=socket", "-uroot", "--single-transaction", "--no-tablespaces", "--set-gtid-purged=OFF", "--databases", *dbs, out=f)
        else:
            f.write("-- No hay bases MySQL existentes.\n")
    with open(folder/"postgresql.dump", "wb") as f:
        # Sin shell; stdout es el archivo abierto por root. pg_dump escribe formato personalizado binario.
        check(sp.run(["runuser", "-u", "postgres", "--", "pg_dump", "-Fc", u], stdout=f).returncode == 0, "Falló pg_dump.")
    put(folder/"alumno.json", json.dumps(d, ensure_ascii=False, indent=2), 0o600)
    print("Copia:", folder, "(no es atómica; detén escrituras para una copia coherente).")

def suspend(s, u, restore):
    d = student(s, u, False)
    check(d["activo"] != restore, "La cuenta ya se encuentra en ese estado.")
    timers = [unitname(u, "cron."+u+"."+t).replace(".service", ".timer") for t in d["cron"]]
    units = [unitname(u, "fpm"), unitname(u, "web")]
    if d["redis"]:
        units.append(unitname(u, "redis"))
    if d["mode"] == "app":
        units.append(unitname(u, "app"))
    if restore:
        run("usermod", "-e", "", "-U", u)
        mysql("".join(f"ALTER USER '{u}'@'{h}' ACCOUNT UNLOCK;\n" for h in ("localhost", "127.0.0.1")))
        pg(f'ALTER ROLE "{u}" LOGIN;')
        ctl("enable", "--now", *units, *timers)
        d["activo"] = True
    else:
        run("usermod", "-L", "-e", "1", u)
        ctl("disable", "--now", *timers, *reversed(units))
        for t in d["cron"]:
            ctl("stop", unitname(u, "cron."+u+"."+t))
        sp.run(["pkill", "-KILL", "-u", str(pwd.getpwnam(u).pw_uid)], check=False)
        mysql("".join(f"ALTER USER '{u}'@'{h}' ACCOUNT LOCK;\n" for h in ("localhost", "127.0.0.1")))
        pg(f'ALTER ROLE "{u}" NOLOGIN;\nSELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE usename={chr(39)}{u}{chr(39)} AND pid<>pg_backend_pid();')
        d["activo"] = False
    routes(s)
    save(s)

def doctor(s):
    for pkg in HOSTPKG + ["phpmyadmin"]:
        print(f'{"OK" if installed(pkg) else "FALTA"} host {pkg}')
    for pkg in CORE + PHPPKG + EXTRA:
        print(f'{"OK" if installed(pkg,TPL) else "NO INSTALADO"} plantilla {pkg}')
    for v in sorted({s["php_default"]} | {d["php"] for d in s["alumnos"].values()}):
        print(f'{"OK" if has_opcache(v) else "FALTA"} OPcache en runtime PHP {v}')
    for u, d in s["alumnos"].items():
        print(f'{u}: PHP {d["php"]}, {d["mode"]}, activo={d["activo"]}')
        mounted(u)
        if d["activo"]:
            for k in ("web", "fpm", *(("app",) if d["mode"]=="app" else ()), *(("redis",) if d["redis"] else ())):
                print("  ", k, run("systemctl", "is-active", unitname(u,k), capture=True))
    run("apache2ctl", "configtest")
    run("sshd", "-t")
    run("df", "-h", "/srv")

def help_parser():
    p = argparse.ArgumentParser(description="Hosting Aula multipropósito (Ubuntu, PHP/Node/Python).")
    sub = p.add_subparsers(dest="cmd")
    q = sub.add_parser("instalar"); q.add_argument("csv", nargs="?", default=str(Path(__file__).with_name("alumnos.csv"))); q.add_argument("--completo", action="store_true")
    for cmd in ("listar", "doctor", "dependencias", "extras"):
        sub.add_parser(cmd)
    q = sub.add_parser("reanudar", help="Retomar instalación detenida durante un alta inicial"); q.add_argument("csv", nargs="?", default=str(Path(__file__).with_name("alumnos.csv")))
    sub.add_parser("reparar-shell", help="Corregir avisos de systemd en la plantilla y las jaulas")
    sub.add_parser("referencia", help="Lista de órdenes de administración")
    q = sub.add_parser("db-listar", help="Bases MySQL y permisos"); q.add_argument("usuario")
    q = sub.add_parser("db-alta", help="Añadir una base MySQL con sufijo numérico"); q.add_argument("usuario"); q.add_argument("sufijo")
    q = sub.add_parser("db-baja", help="Eliminar una base adicional"); q.add_argument("usuario"); q.add_argument("base")
    q = sub.add_parser("db-reparar", help="Crear/reparar las tres bases iniciales"); q.add_argument("usuario", nargs="?", default="todos")
    q = sub.add_parser("clave", help="Cambiar contraseña sin mostrarla"); q.add_argument("usuario")
    q.add_argument("servicio", choices=("mysql", "postgresql", "ssh")); q.add_argument("--restablecer", action="store_true")
    q = sub.add_parser("alta", help="Añadir uno o varios alumnos")
    q.add_argument("nombres", nargs="+", help="Nombres; entre comillas si tienen espacios")
    q = sub.add_parser("altas", help="Añadir alumnos desde CSV, sin recrear los existentes")
    q.add_argument("csv")
    for cmd in ("baja", "reactivar", "backup", "redis", "web", "reiniciar", "logs"):
        q = sub.add_parser(cmd); q.add_argument("usuario")
    q = sub.add_parser("php"); q.add_argument("usuario"); q.add_argument("version")
    q = sub.add_parser("instalar-php"); q.add_argument("version")
    q = sub.add_parser("documentroot"); q.add_argument("usuario"); q.add_argument("ruta"); q.add_argument("--moodle", action="store_true")
    for cmd in ("node", "python"):
        q = sub.add_parser(cmd); q.add_argument("usuario"); q.add_argument("ruta")
    q = sub.add_parser("app"); q.add_argument("usuario"); q.add_argument("argv", nargs=argparse.REMAINDER)
    q = sub.add_parser("cron"); q.add_argument("usuario"); q.add_argument("tarea"); q.add_argument("minutos", type=int); q.add_argument("ruta")
    q = sub.add_parser("cron-baja"); q.add_argument("usuario"); q.add_argument("tarea")
    q = sub.add_parser("limites"); q.add_argument("usuario"); q.add_argument("ram_mib", type=int); q.add_argument("cpu", type=int)
    q = sub.add_parser("cuota"); q.add_argument("usuario"); q.add_argument("mib", type=int)
    return p

def cli():
    parser = help_parser()
    argv = sys.argv[1:]
    # Compatibilidad con las órdenes anteriores --alta, --php, etc.
    if argv and argv[0].startswith("--") and argv[0][2:] not in ("help",):
        argv[0] = argv[0][2:]
    if not argv:
        argv = ["instalar"]
    a = parser.parse_args(argv)
    if a.cmd == "referencia":
        parser.print_help()
        return
    check(os.geteuid() == 0, "Ejecuta mediante sudo bash script.sh.")
    with open("/run/lock/hosting-aula-admin.lock", "w") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise Error("Hay otra operación de administración en curso.")
        if a.cmd == "instalar":
            bootstrap(a.csv, a.completo)
            return
        if a.cmd == "reanudar":
            resume_initial(a.csv)
            return
        s = state()
        c = a.cmd
        if c == "alta":
            add_students(s, [(n.strip(), slug(n)) for n in a.nombres])
        elif c == "altas":
            add_students(s, read_students(a.csv))
        elif c == "db-reparar":
            users = [u for u, d in s["alumnos"].items() if d["activo"]] if a.usuario == "todos" else [a.usuario]
            ensure_defaults(s, users)
        elif c == "db-listar":
            mysql_dbs_list(s, a.usuario)
        elif c == "db-alta":
            student(s, a.usuario)
            extra_db(s, a.usuario, a.sufijo)
        elif c == "db-baja":
            drop_mysql_db(s, a.usuario, a.base)
        elif c == "clave":
            change_password(s, a.usuario, a.servicio, a.restablecer)
        elif c == "listar":
            for u, d in s["alumnos"].items():
                print(u, d["php"], d["mode"], "activo" if d["activo"] else "suspendido", f"http://{HOST}/{u}/")
        elif c == "doctor":
            doctor(s)
        elif c == "reparar-shell":
            fix_shell_profile(TPL)
            for u in sorted(s["alumnos"]):
                fix_shell_profile(jail(u))
        elif c in ("dependencias", "extras", "instalar-php"):
            # Se instalan paquetes de confianza de los repositorios configurados.
            # No se agrega un repositorio externo ni se mezclan versiones de Ubuntu.
            disk(1.5)
            if c == "instalar-php":
                v = version(a.version)
                packages([f"php{v}-{x}" for x in EXT], TPL)
                ensure_opcache(v)
            elif c == "extras":
                packages(EXTRA, TPL)
                s["full"] = True
            else:
                packages(HOSTPKG + ["phpmyadmin"])
                packages(CORE + PHPPKG + (EXTRA if s["full"] else []), TPL)
                for v in sorted({s["php_default"]} | {d["php"] for d in s["alumnos"].values()}):
                    ensure_opcache(v)
            run("find", TPL/"usr", "-xdev", "-type", "f", "-perm", "/6000", "-exec", "chmod", "a-s", "{}", "+")
            fix_shell_profile(TPL)
            for u, d in s["alumnos"].items():
                fix_shell_profile(jail(u))
                sync_php(u, d)
            save(s)
            print("Dependencias instaladas. Reinicia los servicios de los alumnos afectados antes de probar extensiones nuevas.")
        else:
            u = a.usuario
            d = student(s,u, c not in ("baja","reactivar","backup"))
            if c == "php":
                switch_php(s,u,a.version)
            elif c == "documentroot":
                documentroot(s,u,a.ruta,a.moodle)
            elif c in ("node", "python"):
                path = f"/home/{u}/" + relative(a.ruta)
                as_user(u, "/usr/bin/test", "-f", path)
                exe = "/usr/bin/node" if c == "node" else "/usr/bin/python3"
                app(s,u,[exe,path])
            elif c == "app":
                app(s,u,a.argv)
            elif c == "web":
                if d["mode"] == "app":
                    ctl("disable", "--now", unitname(u,"app"))
                d["mode"] = "php"
                routes(s)
                save(s)
            elif c == "reiniciar":
                ctl("restart", unitname(u, "app" if d["mode"]=="app" else "fpm"))
                if d["mode"]=="php":
                    ctl("restart",unitname(u,"web"))
            elif c == "logs":
                run("journalctl", "--no-pager", "-n", "100", "-u", unitname(u,"app" if d["mode"]=="app" else "web"), "-u", unitname(u,"fpm"))
            elif c == "cron":
                cron(s,u,a.tarea,a.minutos,a.ruta)
            elif c == "cron-baja":
                check(a.tarea in d["cron"], "Tarea inexistente.")
                name=unitname(u,"cron."+u+"."+a.tarea)
                timer=name.replace(".service",".timer")
                ctl("disable","--now",timer)
                ctl("stop",name)
                (UNITS/name).unlink()
                (UNITS/timer).unlink()
                d["cron"].remove(a.tarea)
                ctl("daemon-reload")
                save(s)
            elif c == "redis":
                redis(s,u)
            elif c == "backup":
                backup(s,u)
            elif c in ("baja","reactivar"):
                suspend(s,u,c=="reactivar")
            elif c == "limites":
                check(256 <= a.ram_mib <= 16384 and 10 <= a.cpu <= 800, "RAM/CPU fuera de rango.")
                d.update(mem=a.ram_mib,cpu=a.cpu)
                limits(u,d)
                ctl("daemon-reload")
                ctl("set-property",f"hosting-aula-{u}.slice",f"MemoryMax={a.ram_mib}M",f"CPUQuota={a.cpu}%")
                save(s)
            elif c == "cuota":
                check(a.mib > 0, "Indica una cuota positiva.")
                mp=run("findmnt","-n","-o","TARGET","-T",jail(u),capture=True)
                check(ok("quotaon","-p",mp), "El volumen debe tener cuotas habilitadas previamente.")
                run("setquota","-u",u,str(a.mib*1024),str(a.mib*1024),"0","0",mp)
                run("quota","-u",u)
            else:
                parser.error("Orden desconocida.")

if __name__ == "__main__":
    try:
        cli()
    except (Error, OSError, ValueError, KeyError, EOFError) as e:
        print(f"ERROR: {e}\nNo se realiza rollback general. Consulta los logs y el estado; no repitas una instalación parcial a ciegas.", file=sys.stderr)
        sys.exit(1)
