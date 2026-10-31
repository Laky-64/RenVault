//go:build linux && !android

package localkey

import (
	"fmt"
	"os/exec"
)

const (
	rulePath = "/etc/udev/rules.d/70-renvault-uhid.rules"
	rule     = `KERNEL=="uhid", SUBSYSTEM=="misc", TAG+="uaccess"`
)

func Grant() error {
	script := fmt.Sprintf("printf '%%s\\n' '%s' > %s && udevadm control --reload-rules && udevadm trigger --action=change --name-match=uhid", rule, rulePath)
	out, err := exec.Command("pkexec", "/bin/sh", "-c", script).CombinedOutput()
	if err != nil {
		return fmt.Errorf("localkey: grant access: %w: %s", err, out)
	}
	return nil
}
