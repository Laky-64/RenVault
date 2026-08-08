package hybrid

import (
	"encoding/hex"
	"fmt"
	"strings"
)

const authenticatorDomain = 0

var assignedDomains = []string{
	"cable.ua5v.com",
	"cable.auth.com",
}

func AssignedDomainCount() uint16 {
	return uint16(len(assignedDomains))
}

func TunnelDomain(index uint16) (string, error) {
	if int(index) < len(assignedDomains) {
		return assignedDomains[index], nil
	}
	return "", fmt.Errorf("hybrid: tunnel domain %d is not assigned", index)
}

func NewTunnelURL(domain uint16, tunnelID []byte) (string, error) {
	host, err := TunnelDomain(domain)
	if err != nil {
		return "", err
	}
	if len(tunnelID) != TunnelIDSize {
		return "", fmt.Errorf("hybrid: tunnel id is %d bytes, want %d", len(tunnelID), TunnelIDSize)
	}
	return "wss://" + host + "/cable/new/" + strings.ToUpper(hex.EncodeToString(tunnelID)), nil
}

func ConnectTunnelURL(domain uint16, routingID, tunnelID []byte) (string, error) {
	host, err := TunnelDomain(domain)
	if err != nil {
		return "", err
	}
	if len(routingID) != RoutingIDSize {
		return "", fmt.Errorf("hybrid: routing id is %d bytes, want %d", len(routingID), RoutingIDSize)
	}
	if len(tunnelID) != TunnelIDSize {
		return "", fmt.Errorf("hybrid: tunnel id is %d bytes, want %d", len(tunnelID), TunnelIDSize)
	}
	return "wss://" + host + "/cable/connect/" +
		strings.ToUpper(hex.EncodeToString(routingID)) + "/" +
		strings.ToUpper(hex.EncodeToString(tunnelID)), nil
}
