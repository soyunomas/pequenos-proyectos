// ------ cmd/client/main.go ----
package main

import (
	"bytes"
	"context"
	"fmt"
	"log"
	"net"
	"time"

	"github.com/getlantern/systray"
	"github.com/go-vgo/robotgo"
	"golang.design/x/clipboard"

	"github.com/usuario/gokvm/internal/compression"
	"github.com/usuario/gokvm/internal/config"
	"github.com/usuario/gokvm/internal/protocol"
	"github.com/usuario/gokvm/internal/transport"
	"github.com/usuario/gokvm/internal/ui"
)

var (
	lastClipText []byte
	lastClipImg  []byte
	cfg          *config.AppConfig
	menuStatus   *systray.MenuItem
)

func main() {
	systray.Run(onReady, onExit)
}

func onReady() {
	systray.SetIcon(ui.Data)
	systray.SetTitle("GoKVM Client")

	menuStatus = systray.AddMenuItem("🔴 Desconectado", "Estado")
	menuStatus.Disable()
	mQuit := systray.AddMenuItem("Salir", "Cerrar")

	go func() {
		<-mQuit.ClickedCh
		systray.Quit()
	}()

	var err error
	cfg, err = config.LoadConfig("config.json")
	if err != nil {
		log.Printf("Error config: %v", err)
		return
	}

	robotgo.MouseSleep = 0
	robotgo.KeySleep = 0

	if err := clipboard.Init(); err != nil {
		log.Printf("⚠️ Error clipboard: %v", err)
	}

	go connectionLoop()
}

func onExit() {
	fmt.Println("Saliendo...")
}

func connectionLoop() {
	for {
		menuStatus.SetTitle("🔄 Conectando...")
		conn, err := transport.DialSecure(cfg.Client.ServerIP)
		if err != nil {
			time.Sleep(3 * time.Second)
			continue
		}

		fmt.Println("✅ Conexión establecida. Autenticando...")

		// 1. ENVIAR HANDSHAKE CON TOKEN
		// Payload formato: "TOKEN|NOMBRE"
		authPayload := fmt.Sprintf("%s|%s", cfg.Client.AuthToken, cfg.Client.Name)
		err = transport.SendPacket(conn, protocol.Packet{
			Type:    protocol.Handshake,
			Payload: []byte(authPayload),
		})

		if err != nil {
			conn.Close()
			continue
		}

		menuStatus.SetTitle("🟢 Conectado")
		handleConnection(conn)

		menuStatus.SetTitle("🔴 Desconectado")
		conn.Close()
		time.Sleep(1 * time.Second)
	}
}

func handleConnection(conn net.Conn) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	go watchLocalClipboard(ctx, conn, clipboard.FmtText, protocol.ClipboardText)
	go watchLocalClipboard(ctx, conn, clipboard.FmtImage, protocol.ClipboardImage)

	for {
		var pkt protocol.Packet
		if err := transport.ReadPacket(conn, &pkt); err != nil {
			return
		}
		processPacket(pkt)
	}
}

func processPacket(pkt protocol.Packet) {
	switch pkt.Type {
	case protocol.MouseMove:
		robotgo.MoveRelative(int(pkt.X), int(pkt.Y))
	case protocol.MouseDown:
		if pkt.Button != "" {
			robotgo.Toggle(pkt.Button, "down")
		}
	case protocol.MouseUp:
		if pkt.Button != "" {
			robotgo.Toggle(pkt.Button, "up")
		}
	case protocol.KeyDown:
		if len(pkt.Payload) > 0 {
			robotgo.KeyToggle(string(pkt.Payload), "down")
		}
	case protocol.KeyUp:
		if len(pkt.Payload) > 0 {
			robotgo.KeyToggle(string(pkt.Payload), "up")
		}
	case protocol.ClipboardText:
		if !bytes.Equal(pkt.Payload, lastClipText) {
			lastClipText = pkt.Payload
			go clipboard.Write(clipboard.FmtText, pkt.Payload)
		}
	case protocol.ClipboardImage:
		decompressed, err := compression.Decompress(pkt.Payload)
		if err == nil && !bytes.Equal(decompressed, lastClipImg) {
			lastClipImg = decompressed
			go clipboard.Write(clipboard.FmtImage, decompressed)
		}
	}
}

func watchLocalClipboard(ctx context.Context, conn net.Conn, fmtType clipboard.Format, protoType protocol.MsgType) {
	ch := clipboard.Watch(ctx, fmtType)
	var lastSentTime time.Time

	for {
		select {
		case <-ctx.Done():
			return
		case data, ok := <-ch:
			if !ok {
				return
			}

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
				if time.Since(lastSentTime) < 500*time.Millisecond {
					continue
				}
				lastSentTime = time.Now()

				payload := data
				if protoType == protocol.ClipboardImage {
					if compressed, err := compression.Compress(data); err == nil {
						payload = compressed
					}
				}
				transport.SendPacket(conn, protocol.Packet{Type: protoType, Payload: payload})
			}
		}
	}
}
