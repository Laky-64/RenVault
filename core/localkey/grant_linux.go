//go:build linux && !android

package localkey

import (
	"fmt"
	"os/exec"
)

const (
	rulePath   = "/etc/udev/rules.d/70-renvault-uhid.rules"
	rule       = `KERNEL=="uhid", SUBSYSTEM=="misc", TAG+="uaccess"`
	modulePath = "/etc/modules-load.d/renvault-uhid.conf"
)

func Grant() error {
	script := fmt.Sprintf("modprobe uhid && printf 'uhid\\n' > %s && printf '%%s\\n' '%s' > %s && udevadm control --reload-rules && udevadm trigger --action=change --name-match=uhid", modulePath, rule, rulePath)
	out, err := exec.Command("pkexec", "/bin/sh", "-c", script).CombinedOutput()
	if err != nil {
		return fmt.Errorf("localkey: grant access: %w: %s", err, out)
	}
	return nil
}
