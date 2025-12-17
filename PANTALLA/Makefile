# ==============================================================================
# GOKVM MAKEFILE
# ==============================================================================

# Variables del proyecto
BINARY_DIR=bin
SERVER_SRC=./cmd/server
CLIENT_SRC=./cmd/client
APP_NAME=gokvm

# Banderas de compilación
# -s: Omitir la tabla de símbolos (menor tamaño)
# -w: Omitir información de depuración DWARF (menor tamaño)
LDFLAGS=-ldflags "-s -w"

# Detectar el Sistema Operativo para los comandos de limpieza
ifeq ($(OS),Windows_NT)
    RM = del /Q /F
    MKDIR = mkdir
else
    RM = rm -rf
    MKDIR = mkdir -p
endif

.PHONY: all build build-linux build-windows run-server run-client clean dep fmt vet help

# ==============================================================================
# COMANDOS PRINCIPALES
# ==============================================================================

# Target por defecto: Ayuda
all: help

## build: Compila el proyecto para el SO actual (Linux)
build: dep fmt vet
	@echo "🔨 Compilando para Linux..."
	@$(MKDIR) $(BINARY_DIR)
	go build $(LDFLAGS) -o $(BINARY_DIR)/server $(SERVER_SRC)
	go build $(LDFLAGS) -o $(BINARY_DIR)/client $(CLIENT_SRC)
	@echo "✅ ¡Compilación completada! Binarios en /$(BINARY_DIR)"

## build-windows: Compilación cruzada para Windows desde Linux
build-windows: dep
	@echo "🪟 Compilando para Windows (amd64)..."
	@$(MKDIR) $(BINARY_DIR)
	GOOS=windows GOARCH=amd64 go build $(LDFLAGS) -o $(BINARY_DIR)/server.exe $(SERVER_SRC)
	GOOS=windows GOARCH=amd64 go build $(LDFLAGS) -o $(BINARY_DIR)/client.exe $(CLIENT_SRC)
	@echo "✅ ¡Ejecutables .exe generados en /$(BINARY_DIR)!"

## run-server: Ejecuta el servidor (Server)
run-server:
	@echo "🚀 Ejecutando Servidor..."
	@go run $(SERVER_SRC)/main.go

## run-client: Ejecuta el cliente (Client) por defecto
run-client:
	@echo "💻 Ejecutando Cliente..."
	@go run $(CLIENT_SRC)/main.go

# ==============================================================================
# DESARROLLO Y MANTENIMIENTO
# ==============================================================================

## dep: Descarga y limpia dependencias (go mod tidy)
dep:
	@echo "📦 Gestionando dependencias..."
	@go mod tidy

## fmt: Formatea todo el código .go del proyecto
fmt:
	@echo "🧹 Formateando código..."
	@go fmt ./...

## vet: Analiza el código en busca de errores sospechosos
vet:
	@echo "🔍 Analizando código (go vet)..."
	@go vet ./...

## clean: Elimina la carpeta de binarios y archivos temporales
clean:
	@echo "🗑️  Limpiando..."
	@$(RM) $(BINARY_DIR)
	@echo "✨ Limpio."

# ==============================================================================
# AYUDA
# ==============================================================================

## help: Muestra esta ayuda
help:
	@echo ""
	@echo "Uso: make [comando]"
	@echo ""
	@echo "Comandos disponibles:"
	@sed -n 's/^##//p' $(MAKEFILE_LIST) | column -t -s ':' |  sed -e 's/^/ /'
	@echo ""
