SHELL := /bin/sh

APP            := netx
MODULE         := ./cmd/netx
BIN_DIR        := bin
DIST_DIR       := dist
GO             ?= go
VERSION        ?= 0.2.0-dev
COMMIT         ?= $(shell git rev-parse --short HEAD 2>/dev/null || printf unknown)
CGO_ENABLED    ?= 0

LDFLAGS := -s -w -buildid= -X github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/buildinfo.Version=$(VERSION) -X github.com/soyunomas/pequenos-proyectos/REDES/netx/internal/buildinfo.Commit=$(COMMIT)
GOFLAGS_BUILD := -trimpath -ldflags "$(LDFLAGS)"

.PHONY: help build run server test race vet fmt fmt-check check smoke netem-check phase2-check clean install size openwrt \
        openwrt-amd64 openwrt-386 openwrt-armv5 openwrt-armv6 openwrt-armv7 \
        openwrt-arm64 openwrt-mips openwrt-mipsle openwrt-mips64 openwrt-riscv64

help: ## Show this help
	@printf '%s\n' 'netx development targets' ''
	@awk 'BEGIN {FS = ":.*## "; printf "Usage: make <target>\n\n"} /^[a-zA-Z0-9_.-]+:.*## / {printf "  %-20s %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@printf '%s\n' '' 'OpenWrt builds are static pure-Go binaries (CGO_ENABLED=0).' \
		'phase2-check runs the complete Phase 2 gate; netem-check reports SKIP when the host lacks network-admin capabilities.'

build: ## Build native binary into bin/netx
	@mkdir -p $(BIN_DIR)
	CGO_ENABLED=$(CGO_ENABLED) $(GO) build $(GOFLAGS_BUILD) -o $(BIN_DIR)/$(APP) $(MODULE)

run: build ## Show CLI help from the freshly built binary
	./$(BIN_DIR)/$(APP) help

server: build ## Start a local server on the default port
	./$(BIN_DIR)/$(APP) server

test: ## Run unit tests
	$(GO) test ./...

race: ## Run unit tests with Go race detector (native development only)
	CGO_ENABLED=1 $(GO) test -race ./...

vet: ## Run go vet
	$(GO) vet ./...

fmt: ## Format Go sources
	gofmt -w $$(find . -name '*.go' -type f)

fmt-check: ## Fail if Go sources need formatting
	@files=$$(gofmt -l $$(find . -name '*.go' -type f)); \
	if [ -n "$$files" ]; then printf 'gofmt required:\n%s\n' "$$files"; exit 1; fi

check: fmt-check vet test ## Run the fast quality gate

smoke: build ## Run Phase 2 loopback E2E tests: TCP, UDP, latency, JSON and NDJSON
	BIN=./$(BIN_DIR)/$(APP) ./scripts/smoke.sh

netem-check: build ## Run Linux namespace/netem integration test, or explicit SKIP without CAP_NET_ADMIN
	@sh -n ./scripts/netem.sh
	BIN=./$(BIN_DIR)/$(APP) ./scripts/netem.sh

phase2-check: check race smoke netem-check openwrt ## Run the complete Phase 2 validation gate
	@printf '%s\n' 'Phase 2 gate complete.'

install: ## Install netx into GOBIN/GOPATH bin
	CGO_ENABLED=$(CGO_ENABLED) $(GO) install -trimpath -ldflags "$(LDFLAGS)" $(MODULE)

size: build ## Show native binary size
	@ls -lh $(BIN_DIR)/$(APP)
	@du -h $(BIN_DIR)/$(APP)

openwrt: openwrt-amd64 openwrt-386 openwrt-armv5 openwrt-armv6 openwrt-armv7 openwrt-arm64 openwrt-mips openwrt-mipsle openwrt-mips64 openwrt-riscv64 ## Build common OpenWrt/Linux architectures
	@printf '%s\n' 'OpenWrt matrix complete:'
	@ls -lh $(DIST_DIR)/

openwrt-amd64: ## Build linux/amd64 (OpenWrt x86_64)
	@mkdir -p $(DIST_DIR)
	CGO_ENABLED=0 GOOS=linux GOARCH=amd64 $(GO) build $(GOFLAGS_BUILD) -o $(DIST_DIR)/$(APP)-linux-amd64 $(MODULE)

openwrt-386: ## Build linux/386 (OpenWrt x86 32-bit)
	@mkdir -p $(DIST_DIR)
	CGO_ENABLED=0 GOOS=linux GOARCH=386 $(GO) build $(GOFLAGS_BUILD) -o $(DIST_DIR)/$(APP)-linux-386 $(MODULE)

openwrt-armv5: ## Build linux/arm GOARM=5 (legacy ARM routers)
	@mkdir -p $(DIST_DIR)
	CGO_ENABLED=0 GOOS=linux GOARCH=arm GOARM=5 $(GO) build $(GOFLAGS_BUILD) -o $(DIST_DIR)/$(APP)-linux-armv5 $(MODULE)

openwrt-armv6: ## Build linux/arm GOARM=6
	@mkdir -p $(DIST_DIR)
	CGO_ENABLED=0 GOOS=linux GOARCH=arm GOARM=6 $(GO) build $(GOFLAGS_BUILD) -o $(DIST_DIR)/$(APP)-linux-armv6 $(MODULE)

openwrt-armv7: ## Build linux/arm GOARM=7
	@mkdir -p $(DIST_DIR)
	CGO_ENABLED=0 GOOS=linux GOARCH=arm GOARM=7 $(GO) build $(GOFLAGS_BUILD) -o $(DIST_DIR)/$(APP)-linux-armv7 $(MODULE)

openwrt-arm64: ## Build linux/arm64 (AArch64)
	@mkdir -p $(DIST_DIR)
	CGO_ENABLED=0 GOOS=linux GOARCH=arm64 $(GO) build $(GOFLAGS_BUILD) -o $(DIST_DIR)/$(APP)-linux-arm64 $(MODULE)

openwrt-mips: ## Build linux/mips soft-float
	@mkdir -p $(DIST_DIR)
	CGO_ENABLED=0 GOOS=linux GOARCH=mips GOMIPS=softfloat $(GO) build $(GOFLAGS_BUILD) -o $(DIST_DIR)/$(APP)-linux-mips-softfloat $(MODULE)

openwrt-mipsle: ## Build linux/mipsle soft-float
	@mkdir -p $(DIST_DIR)
	CGO_ENABLED=0 GOOS=linux GOARCH=mipsle GOMIPS=softfloat $(GO) build $(GOFLAGS_BUILD) -o $(DIST_DIR)/$(APP)-linux-mipsle-softfloat $(MODULE)

openwrt-mips64: ## Build linux/mips64 soft-float
	@mkdir -p $(DIST_DIR)
	CGO_ENABLED=0 GOOS=linux GOARCH=mips64 GOMIPS64=softfloat $(GO) build $(GOFLAGS_BUILD) -o $(DIST_DIR)/$(APP)-linux-mips64-softfloat $(MODULE)

openwrt-riscv64: ## Build linux/riscv64
	@mkdir -p $(DIST_DIR)
	CGO_ENABLED=0 GOOS=linux GOARCH=riscv64 $(GO) build $(GOFLAGS_BUILD) -o $(DIST_DIR)/$(APP)-linux-riscv64 $(MODULE)

clean: ## Remove generated binaries and profiles
	rm -rf $(BIN_DIR) $(DIST_DIR) *.prof *.test
