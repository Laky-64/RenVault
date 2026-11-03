package icons

import (
	"crypto/tls"
	"crypto/x509"
	"errors"
	"io"
	nethttp "net/http"
	"net/url"
	"sync"
	"time"
)

const (
	issuerTimeout = 4 * time.Second
	issuerLimit   = 64 << 10
	issuerDepth   = 3
)

var (
	errNoPeerCertificate = errors.New("icons: the server sent no certificate")
	issuerClient         = &nethttp.Client{Timeout: issuerTimeout}
	issuerCache          sync.Map
)

func siteTransport() *nethttp.Transport {
	transport := nethttp.DefaultTransport.(*nethttp.Transport).Clone()
	transport.TLSClientConfig = &tls.Config{
		InsecureSkipVerify: true,
		VerifyConnection:   verifyWithIssuers,
	}
	return transport
}

func verifyWithIssuers(state tls.ConnectionState) error {
	certs := state.PeerCertificates
	if len(certs) == 0 {
		return errNoPeerCertificate
	}
	options := x509.VerifyOptions{DNSName: state.ServerName, Intermediates: x509.NewCertPool()}
	for _, cert := range certs[1:] {
		options.Intermediates.AddCert(cert)
	}
	_, err := certs[0].Verify(options)
	if _, unknown := errors.AsType[x509.UnknownAuthorityError](err); !unknown {
		return err
	}
	current := certs[len(certs)-1]
	for range issuerDepth {
		issuer := fetchIssuer(current)
		if issuer == nil {
			break
		}
		options.Intermediates.AddCert(issuer)
		if _, err = certs[0].Verify(options); err == nil {
			return nil
		}
		current = issuer
	}
	return err
}

func fetchIssuer(cert *x509.Certificate) *x509.Certificate {
	for _, address := range cert.IssuingCertificateURL {
		if cached, ok := issuerCache.Load(address); ok {
			return cached.(*x509.Certificate)
		}
		parsed, err := url.Parse(address)
		if err != nil || (parsed.Scheme != "http" && parsed.Scheme != "https") {
			continue
		}
		issuer, err := downloadIssuer(address)
		if err != nil {
			continue
		}
		issuerCache.Store(address, issuer)
		return issuer
	}
	return nil
}

func downloadIssuer(address string) (*x509.Certificate, error) {
	response, err := issuerClient.Get(address)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	if response.StatusCode != nethttp.StatusOK {
		return nil, errNoSiteIcon
	}
	data, err := io.ReadAll(io.LimitReader(response.Body, issuerLimit))
	if err != nil {
		return nil, err
	}
	return x509.ParseCertificate(data)
}
