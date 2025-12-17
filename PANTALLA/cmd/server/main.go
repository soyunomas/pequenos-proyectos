// ------ cmd/server/main.go ----
package main

import (
	"bytes"
	"context"
	"crypto/tls"
	"fmt"
	"log"
	"net"
	"strings"
	"sync"
	"time"

	"github.com/getlantern/systray"
	"github.com/go-vgo/robotgo"
	gohook "github.com/robotn/gohook"
	"golang.design/x/clipboard"

	"github.com/usuario/gokvm/internal/compression"
	"github.com/usuario/gokvm/internal/config"
	"github.com/usuario/gokvm/internal/protocol"
	"github.com/usuario/gokvm/internal/security"
	"github.com/usuario/gokvm/internal/transport"
	"github.com/usuario/gokvm/internal/ui"
)

var (
	cfg          *config.AppConfig
	clients      []net.Conn
	clientsMu    sync.Mutex
	isActive     bool
	screenWidth  int
	screenHeight int
	stateMu      sync.Mutex
	lastClipText []byte
	lastClipImg  []byte
)

func main() {
	systray.Run(onReady, onExit)
}

func onReady() {
	systray.SetIcon(ui.Data)
	systray.SetTitle("GoKVM Server")

	mQuit := systray.AddMenuItem("Salir", "Detener")
	go func() {
		<-mQuit.ClickedCh
		systray.Quit()
	}()

	go runServer()
}

func onExit() {
	gohook.End()
}

func runServer() {
	var err error
	cfg, err = config.LoadConfig("config.json")
	if err != nil {
		log.Fatalf("Error config: %v", err)
	}

	if err := security.GenerateSelfSignedCert(); err != nil {
		log.Fatalf("❌ Error certs: %v", err)
	}

	screenWidth, screenHeight = robotgo.GetScreenSize()

	if err := clipboard.Init(); err != nil {
		log.Printf("⚠️ Error clipboard: %v", err)
	} else {
		go watchLocalClipboard(clipboard.FmtText, protocol.ClipboardText)
		go watchLocalClipboard(clipboard.FmtImage, protocol.ClipboardImage)
	}

	go startTLSServer()
	startInputHooks()
}

func startTLSServer() {
	tlsConfig, err := security.LoadTLSConfig()
	if err != nil {
		log.Fatalf("❌ TLS error: %v", err)
	}

	port := cfg.Server.Port
	if port == "" {
		port = ":8080"
	}

	listener, err := tls.Listen("tcp", port, tlsConfig)
	if err != nil {
		log.Fatalf("Error bind: %v", err)
	}
	fmt.Printf("🔒 Server TLS en %s%s\n", transport.GetLocalIP(), port)

	for {
		conn, err := listener.Accept()
		if err != nil {
			continue
		}

		go handleClientHandshake(conn)
	}
}

func handleClientHandshake(conn net.Conn) {
	// Leer primer paquete (Handshake) con timeout de 5 seg
	conn.SetReadDeadline(time.Now().Add(5 * time.Second))
	var pkt protocol.Packet
	if err := transport.ReadPacket(conn, &pkt); err != nil {
		conn.Close()
		return
	}
	// Quitar deadline
	conn.SetReadDeadline(time.Time{})

	if pkt.Type != protocol.Handshake {
		fmt.Println("⛔ Cliente rechazado: No envió handshake")
		conn.Close()
		return
	}

	// Payload esperado: "TOKEN|NOMBRE"
	payloadStr := string(pkt.Payload)
	parts := strings.SplitN(payloadStr, "|", 2)

	tokenReceived := ""
	clientName := "Unknown"

	if len(parts) >= 1 {
		tokenReceived = parts[0]
	}
	if len(parts) >= 2 {
		clientName = parts[1]
	}

	// VALIDACIÓN DE TOKEN
	if tokenReceived != cfg.Server.AuthToken {
		fmt.Printf("⛔ Cliente '%s' rechazado: Token inválido\n", clientName)
		conn.Close()
		return
	}

	fmt.Printf("✅ Cliente '%s' ACEPTADO\n", clientName)

	clientsMu.Lock()
	clients = append(clients, conn)
	clientsMu.Unlock()

	handleClientMessages(conn)
}

func handleClientMessages(conn net.Conn) {
	defer conn.Close()
	defer func() {
		clientsMu.Lock()
		for i, c := range clients {
			if c == conn {
				clients = append(clients[:i], clients[i+1:]...)
				break
			}
		}
		clientsMu.Unlock()
		fmt.Println("❌ Cliente desconectado")
	}()

	for {
		var pkt protocol.Packet
		if err := transport.ReadPacket(conn, &pkt); err != nil {
			return
		}

		if pkt.Type == protocol.ClipboardText {
			if !bytes.Equal(pkt.Payload, lastClipText) {
				lastClipText = pkt.Payload
				clipboard.Write(clipboard.FmtText, pkt.Payload)
			}
		} else if pkt.Type == protocol.ClipboardImage {
			decompressed, err := compression.Decompress(pkt.Payload)
			if err == nil && !bytes.Equal(decompressed, lastClipImg) {
				lastClipImg = decompressed
				clipboard.Write(clipboard.FmtImage, decompressed)
			}
		}
	}
}

func watchLocalClipboard(fmtType clipboard.Format, protoType protocol.MsgType) {
	ch := clipboard.Watch(context.TODO(), fmtType)
	for data := range ch {
		isNew := false
		if protoType == protocol.ClipboardText {
			if !bytes.Equal(data, lastClipText) {
				lastClipText = data
				isNew = true
			}
		} else {
			if !bytes.Equal(data, lastClipImg) {
				lastClipImg = data
				isNew = true
			}
		}

		if isNew {
			payload := data
			if protoType == protocol.ClipboardImage {
				if compressed, err := compression.Compress(data); err == nil {
					payload = compressed
				}
			}
			broadcastPacket(protocol.Packet{Type: protoType, Payload: payload})
		}
	}
}

func broadcastPacket(pkt protocol.Packet) {
	clientsMu.Lock()
	defer clientsMu.Unlock()
	for _, conn := range clients {
		transport.SendPacket(conn, pkt)
	}
}

func startInputHooks() {
	evChan := gohook.Start()

	go func() {
		for {
			stateMu.Lock()
			active := isActive
			stateMu.Unlock()

			if active {
				cx, cy := screenWidth/2, screenHeight/2
				curX, curY := robotgo.GetMousePos()
				distSq := (curX-cx)*(curX-cx) + (curY-cy)*(curY-cy)

				if distSq > 400 {
					dx := curX - cx
					dy := curY - cy

					if dx != 0 || dy != 0 {
						broadcastPacket(protocol.Packet{Type: protocol.MouseMove, X: int32(dx), Y: int32(dy)})
					}
					robotgo.Move(cx, cy)

					shouldReturn := false
					if cfg.Server.ClientEdge == config.EdgeRight && dx < -100 {
						shouldReturn = true
					}
					if cfg.Server.ClientEdge == config.EdgeLeft && dx > 100 {
						shouldReturn = true
					}

					if shouldReturn {
						stateMu.Lock()
						isActive = false
						stateMu.Unlock()
						fmt.Println("🏠 Regresando a Local")
						if cfg.Server.ClientEdge == config.EdgeRight {
							robotgo.Move(screenWidth-50, cy)
						} else {
							robotgo.Move(50, cy)
						}
					}
				}
			}
			time.Sleep(5 * time.Millisecond)
		}
	}()

	for ev := range evChan {
		if ev.Kind == gohook.MouseMove {
			stateMu.Lock()
			active := isActive
			stateMu.Unlock()

			if !active {
				shouldSwitch := false
				if cfg.Server.ClientEdge == config.EdgeRight && int(ev.X) >= screenWidth-2 {
					shouldSwitch = true
				}
				if cfg.Server.ClientEdge == config.EdgeLeft && int(ev.X) <= 2 {
					shouldSwitch = true
				}

				if shouldSwitch {
					stateMu.Lock()
					isActive = true
					stateMu.Unlock()
					fmt.Println("🚀 Saltando a Cliente")
					robotgo.Move(screenWidth/2, screenHeight/2)
				}
			}
		}

		stateMu.Lock()
		active := isActive
		stateMu.Unlock()

		if active && ev.Kind != gohook.MouseMove {
			switch ev.Kind {
			case gohook.MouseDown:
				broadcastPacket(protocol.Packet{Type: protocol.MouseDown, Button: mouseButtonToString(ev.Button)})
			case gohook.MouseUp:
				broadcastPacket(protocol.Packet{Type: protocol.MouseUp, Button: mouseButtonToString(ev.Button)})
			case gohook.KeyDown:
				broadcastPacket(protocol.Packet{Type: protocol.KeyDown, Payload: []byte(string(ev.Keychar))})
			case gohook.KeyUp:
				broadcastPacket(protocol.Packet{Type: protocol.KeyUp, Payload: []byte(string(ev.Keychar))})
			}
		}
	}
}

func mouseButtonToString(btn uint16) string {
	switch btn {
	case 1:
		return "left"
	case 2:
		return "right"
	case 3:
		return "center"
	default:
		return "left"
	}
}
