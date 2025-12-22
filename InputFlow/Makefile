# --- CONFIGURACIÓN DEL PROYECTO ---
BINARY_NAME=inputflow
CMD_PATH=cmd/inputflow/main.go
INSTALL_PATH=/usr/local/bin/$(BINARY_NAME)
CONFIG_PATH=config.toml

# --- FLAGS DE COMPILACIÓN (OPTIMIZACIÓN) ---
# -s: Elimina la tabla de símbolos (reduce tamaño).
# -w: Elimina información de depuración DWARF (reduce tamaño).
# -trimpath: Elimina rutas absolutas del sistema de archivos (build reproducible y más seguro).
LDFLAGS=-ldflags "-s -w"

.PHONY: all build run scan clean install uninstall help

# Por defecto, compila
all: build

# 🔨 Compilación optimizada
build:
	@echo "⚙️  Compilando binario optimizado (Release Mode)..."
	go mod tidy
	go build $(LDFLAGS) -trimpath -o $(BINARY_NAME) $(CMD_PATH)
	@echo "✅ Binario generado: ./$(BINARY_NAME)"

# 🚀 Ejecutar (Requiere sudo para /dev/input/)
run: build
	@echo "🔥 Ejecutando InputFlow (Root requerido para leer Hardware)..."
	sudo ./$(BINARY_NAME)

# 🔍 Modo Escáner Rápido
scan: build
	@echo "🕵️  Iniciando Escáner de Hardware..."
	sudo ./$(BINARY_NAME) -scan

# 📦 Instalación al sistema (System-wide)
install: build
	@echo "📦 Instalando en $(INSTALL_PATH)..."
	sudo cp $(BINARY_NAME) $(INSTALL_PATH)
	sudo chmod +x $(INSTALL_PATH)
	@echo "✨ Instalación completada. Ejecuta 'sudo inputflow' desde cualquier lugar."

# 🗑️ Desinstalación
uninstall:
	@echo "🗑️  Eliminando $(INSTALL_PATH)..."
	sudo rm -f $(INSTALL_PATH)

# 🧹 Limpieza
clean:
	@echo "🧹 Limpiando artefactos..."
	rm -f $(BINARY_NAME)
	go clean

help:
	@echo "📖 Comandos disponibles:"
	@echo "  make build    - Compila el binario localmente."
	@echo "  make run      - Compila y ejecuta el demonio."
	@echo "  make scan     - Compila y ejecuta el escáner de dispositivos."
	@echo "  make install  - Instala el binario en /usr/local/bin."
	@echo "  make clean    - Borra el binario."
