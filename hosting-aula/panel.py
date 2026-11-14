#!/usr/bin/env python3
"""Panel de administración de Hosting Aula. No escucha en la red."""
import json
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
STATE = Path("/srv/hosting-aula/estado.json")
READY = STATE.with_name("instalado")
COLOR = sys.stdout.isatty() and os.environ.get("TERM") not in (None, "dumb") and "NO_COLOR" not in os.environ

def style(s, code):
    return f"\033[{code}m{s}\033[0m" if COLOR else s

def clear():
    if COLOR:
        print("\033[2J\033[H", end="", flush=True)

def ask(label, default=None):
    prompt = label + (f" [{default}]" if default is not None else "") + ": "
    value = input(prompt).strip()
    return value if value or default is None else default

def confirm(label):
    return ask(label + " (s/N)", "n").casefold() in ("s", "si", "sí")

def pause():
    input("\nIntro para volver al menú…")

def call(*args):
    print(style("\n▶ " + shlex.join(["script.sh", *args]), "36"), flush=True)
    result = subprocess.run([sys.executable, str(ROOT / "hosting.py"), *args], cwd=ROOT)
    print(style("\nOperación terminada." if result.returncode == 0 else f"\nOperación no completada (código {result.returncode}).", "32" if result.returncode == 0 else "31"))
    return result.returncode == 0

def students():
    if not READY.is_file() or not STATE.is_file():
        return {}
    return json.loads(STATE.read_text())["alumnos"]

def banner(title):
    clear()
    users = students()
    active = sum(bool(x.get("activo")) for x in users.values())
    free = shutil.disk_usage("/srv").free / 1024**3
    print(style("╔══════════════════════════════════════════════════════════════╗", "36"))
    print(style("║  HOSTING AULA  /  PANEL DE ADMINISTRACIÓN                     ║", "1;36"))
    print(style("╚══════════════════════════════════════════════════════════════╝", "36"))
    print(f"  apw.local  ·  {active}/{len(users)} activos  ·  {free:.2f} GiB libres en /srv")
    print(style("  " + title, "1"))
    print("  " + "─" * 60)

def choose_user(all_accounts=False):
    names = [(u, d) for u, d in sorted(students().items()) if all_accounts or d.get("activo")]
    if not names:
        print("No hay alumnos disponibles.")
        return None
    for i, (u, d) in enumerate(names, 1):
        print(f" {i:2}. {u:24} {d.get('php','?'):5} {d.get('mode','?'):5} {'activo' if d.get('activo') else 'suspendido'}")
    value = ask("Número o usuario (0 para cancelar)")
    if value in ("", "0"):
        return None
    if value.isdecimal() and 1 <= int(value) <= len(names):
        return names[int(value)-1][0]
    if value in dict(names):
        return value
    print("Alumno no válido.")
    return None

def user_action(command, *extra, all_accounts=False):
    u = choose_user(all_accounts)
    if u:
        call(command, u, *extra)

def view_student():
    u = choose_user(True)
    if not u:
        return
    d = students()[u]
    print(f"\nAlumno: {d.get('nombre')} ({u})")
    print(f"Web: http://apw.local/{u}/")
    print(f"SSH/SFTP: {u}@apw.local")
    print(f"PHP: {d.get('php')}  |  Modo: {d.get('mode')}  |  Raíz: {d.get('docroot')}")
    print(f"RAM: {d.get('mem')} MiB  |  CPU: {d.get('cpu')}%  |  Redis: {'sí' if d.get('redis') else 'no'}")
    print("MySQL: " + ", ".join(d.get("mysql_dbs", [u])))
    print("PostgreSQL: " + u)
    for kind in ("ssh", "mysql", "postgresql"):
        print(f"Clave {kind}: " + ("cambiada (no almacenada)" if d.get("password_changed", {}).get(kind) else "inicial = usuario, salvo cambio manual"))
    print("Tareas: " + (", ".join(d.get("cron", [])) or "ninguna"))

def new_users():
    names = []
    print("Un nombre por vez. Intro vacío termina la lista.")
    while True:
        n = ask("Nombre completo")
        if not n:
            break
        names.append(n)
    if names and confirm("Crear " + ", ".join(names)):
        call("alta", *names)

def import_users():
    path = ask("Ruta del CSV", str(ROOT / "alumnos.csv"))
    if confirm("Importar alumnos nuevos, conservando las cuentas existentes"):
        call("altas", str(Path(path).expanduser().resolve()))

def suspend_user(restore=False):
    u = choose_user(True)
    if u and confirm(("Reactivar " if restore else "Suspender, sin borrar los datos de ") + u):
        call("reactivar" if restore else "baja", u)

def password(kind):
    u = choose_user()
    if not u:
        return
    print("El cambio no modifica los archivos de configuración de los CMS.")
    if confirm("Restablecer al nombre de usuario en lugar de introducir una nueva clave"):
        call("clave", u, kind, "--restablecer")
    else:
        call("clave", u, kind)

def add_db():
    u = choose_user()
    if u:
        suffix = ask(f"Sufijo numérico (ejemplo 2 → db_{u}2)", "2")
        call("db-alta", u, suffix)

def drop_db():
    u = choose_user()
    if u:
        call("db-listar", u)
        db = ask("Nombre exacto de la base adicional que quieres borrar")
        if db:
            call("db-baja", u, db)

def repair_db():
    if confirm("Crear/reparar las tres bases iniciales de todos los alumnos activos"):
        call("db-reparar", "todos")
    else:
        user_action("db-reparar")

def change_php():
    u = choose_user()
    if u:
        v = ask("Versión de PHP (major.minor)")
        if v:
            call("php", u, v)

def change_root():
    u = choose_user()
    if u:
        root = ask("Ruta relativa a public_html (punto para raíz)", ".")
        args = ["documentroot", u, root]
        if confirm("Activar enrutamiento para Moodle"):
            args.append("--moodle")
        call(*args)

def publish(kind):
    u = choose_user()
    if not u:
        return
    path = ask("Ruta relativa al home del alumno", "apps/app.js" if kind == "node" else "apps/app.py")
    if path:
        call(kind, u, path)

def custom_app():
    u = choose_user()
    if u:
        args = shlex.split(ask("Ejecutable absoluto y argumentos dentro de la jaula"))
        if args:
            call("app", u, *args)

def cron_add():
    u = choose_user()
    if u:
        task = ask("Nombre de la tarea")
        interval = ask("Intervalo en minutos", "5")
        script = ask("Ruta interna absoluta del script PHP/SH/PY/JS")
        call("cron", u, task, interval, script)

def cron_drop():
    u = choose_user()
    if u:
        print("Tareas: " + ", ".join(students()[u].get("cron", [])))
        task = ask("Tarea para eliminar")
        if task and confirm("Eliminar tarea " + task):
            call("cron-baja", u, task)

def limits():
    u = choose_user()
    if u:
        d = students()[u]
        call("limites", u, ask("RAM máxima (MiB)", str(d.get("mem", 1024))), ask("CPU (% de núcleo)", str(d.get("cpu", 100))))

def quota():
    u = choose_user()
    if u:
        print("El sistema de archivos debe admitir cuotas y tenerlas activadas.")
        call("cuota", u, ask("Límite de disco MiB", "1024"))

def install():
    if READY.is_file():
        print("El alojamiento ya está instalado. Usa la sección de altas.")
        return
    path = ask("CSV inicial", str(ROOT / "alumnos.csv"))
    args = ["instalar", str(Path(path).expanduser().resolve())]
    if confirm("Incluir herramientas extra de compilación y multimedia"):
        args.append("--completo")
    if confirm("Iniciar instalación en esta máquina"):
        call(*args)

def versions():
    for command in (["php", "-v"], ["mysql", "--version"], ["psql", "--version"], ["apache2ctl", "-v"], ["df", "-h", "/srv"]):
        if shutil.which(command[0]):
            subprocess.run(command)
    template = Path("/srv/hosting-aula/plantilla")
    if template.is_dir():
        for command in (["node", "--version"], ["npm", "--version"], ["python3", "--version"], ["composer", "--version"]):
            subprocess.run(["chroot", str(template), *command])

def menu(title, options):
    while True:
        banner(title)
        for key, label, _ in options:
            print(f"  {key:>2}  {label}")
        print("   0  Volver / salir")
        choice = ask("Opción")
        if choice == "0":
            return
        action = next((fn for key, _, fn in options if key == choice), None)
        if action is None:
            print("Opción no válida.")
            pause()
            continue
        try:
            action()
        except (ValueError, OSError, KeyError) as exc:
            print(style(f"No se pudo completar: {exc}", "31"))
        except KeyboardInterrupt:
            print("\nOperación interrumpida; consulta el estado antes de repetir.")
        pause()

def accounts():
    menu("ALUMNOS Y CUENTAS", [
        ("1", "Listado de alumnos", lambda: call("listar")),
        ("2", "Ficha de un alumno", view_student),
        ("3", "Alta de uno o varios alumnos", new_users),
        ("4", "Importar CSV incremental", import_users),
        ("5", "Suspender cuenta", suspend_user),
        ("6", "Reactivar cuenta", lambda: suspend_user(True)),
        ("7", "Cambiar o restablecer clave SSH", lambda: password("ssh")),
    ])

def databases():
    menu("BASES DE DATOS", [
        ("1", "Ver bases MySQL y permisos", lambda: user_action("db-listar", all_accounts=True)),
        ("2", "Crear base MySQL adicional", add_db),
        ("3", "Eliminar base MySQL adicional", drop_db),
        ("4", "Crear/reparar las tres bases iniciales", repair_db),
        ("5", "Cambiar o restablecer clave MySQL", lambda: password("mysql")),
        ("6", "Cambiar o restablecer clave PostgreSQL", lambda: password("postgresql")),
        ("7", "Activar Redis privado", lambda: user_action("redis")),
    ])

def web():
    menu("WEB, PHP Y APLICACIONES", [
        ("1", "Instalar otra versión PHP disponible", lambda: call("instalar-php", ask("Versión PHP"))),
        ("2", "Seleccionar PHP de un alumno", change_php),
        ("3", "Cambiar DocumentRoot / Moodle", change_root),
        ("4", "Publicar Node.js", lambda: publish("node")),
        ("5", "Publicar Python", lambda: publish("python")),
        ("6", "Publicar aplicación con comando personalizado", custom_app),
        ("7", "Volver a Apache/PHP", lambda: user_action("web")),
        ("8", "Reiniciar servicios de un alumno", lambda: user_action("reiniciar")),
        ("9", "Registros de un alumno", lambda: user_action("logs")),
    ])

def maintenance():
    menu("MANTENIMIENTO Y RECURSOS", [
        ("1", "Comprobar instalación y servicios", lambda: call("doctor")),
        ("2", "Comprobar/instalar dependencias faltantes", lambda: call("dependencias")),
        ("3", "Instalar herramientas extra", lambda: call("extras") if confirm("Instalar extras") else None),
        ("4", "Versiones de las tecnologías y disco", versions),
        ("5", "Copiar archivos y bases de un alumno", lambda: user_action("backup", all_accounts=True)),
        ("6", "Modificar límites de RAM y CPU", limits),
        ("7", "Configurar cuota de disco", quota),
        ("8", "Programar tarea", cron_add),
        ("9", "Eliminar tarea programada", cron_drop),
        ("10", "Ver referencia de comandos", lambda: call("referencia")),
    ])

def main():
    if os.geteuid() != 0:
        sys.exit("Ejecuta sudo bash panel.sh")
    if not sys.stdin.isatty():
        sys.exit("El panel necesita una terminal interactiva.")
    try:
        if not READY.is_file():
            menu("INSTALACIÓN INICIAL", [("1", "Instalar Hosting Aula desde CSV", install), ("2", "Ver versiones y espacio", versions)])
        if READY.is_file():
            menu("MENÚ PRINCIPAL", [
                ("1", "Alumnos y cuentas", accounts),
                ("2", "Bases de datos y contraseñas", databases),
                ("3", "Web, PHP y aplicaciones", web),
                ("4", "Mantenimiento, recursos y copias", maintenance),
                ("5", "Resumen de alumnos", lambda: call("listar")),
            ])
    except (EOFError, KeyboardInterrupt):
        print("\nHasta luego.")

if __name__ == "__main__":
    main()
