// ------ internal/security/cert.go ----
package security

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"fmt"
	"math/big"
	"os"
	"time"
)

// GenerateSelfSignedCert crea archivos cert.pem y key.pem si no existen
func GenerateSelfSignedCert() error {
	// Si ya existen, no hacemos nada
	if _, err := os.Stat("cert.pem"); err == nil {
		if _, err := os.Stat("key.pem"); err == nil {
			return nil
		}
	}

	fmt.Println("🔐 Generando certificados de seguridad (TLS)...")

	// 1. Configurar plantilla del certificado
	max := new(big.Int).Lsh(big.NewInt(1), 128)
	serialNumber, _ := rand.Int(rand.Reader, max)

	template := x509.Certificate{
		SerialNumber: serialNumber,
		Subject: pkix.Name{
			Organization: []string{"Gokvm Secure"},
		},
		NotBefore: time.Now(),
		NotAfter:  time.Now().Add(365 * 24 * time.Hour), // Valido 1 año

		KeyUsage:              x509.KeyUsageKeyEncipherment | x509.KeyUsageDigitalSignature,
		ExtKeyUsage:           []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		BasicConstraintsValid: true,
	}

	// 2. Generar clave privada RSA
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return err
	}

	// 3. Crear el certificado x509
	derBytes, err := x509.CreateCertificate(rand.Reader, &template, &template, &priv.PublicKey, priv)
	if err != nil {
		return err
	}

	// 4. Guardar cert.pem
	certOut, err := os.Create("cert.pem")
	if err != nil {
		return err
	}
	pem.Encode(certOut, &pem.Block{Type: "CERTIFICATE", Bytes: derBytes})
	certOut.Close()

	// 5. Guardar key.pem
	keyOut, err := os.Create("key.pem")
	if err != nil {
		return err
	}
	privBytes := x509.MarshalPKCS1PrivateKey(priv)
	pem.Encode(keyOut, &pem.Block{Type: "RSA PRIVATE KEY", Bytes: privBytes})
	keyOut.Close()

	fmt.Println("🔒 Certificados generados correctamente.")
	return nil
}

// LoadTLSConfig carga los certificados para el servidor
func LoadTLSConfig() (*tls.Config, error) {
	cer, err := tls.LoadX509KeyPair("cert.pem", "key.pem")
	if err != nil {
		return nil, err
	}
	return &tls.Config{Certificates: []tls.Certificate{cer}}, nil
}
