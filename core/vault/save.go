package vault

import (
	"cmp"
	"errors"
	"slices"
	"strings"

	"github.com/Laky-64/appleservices/keychain"
)

const appLinkScheme = "androidapp://"

var errSaveIncomplete = errors.New("vault: a saved login needs a password and a website or an app")

type SaveResult struct {
	ID      string
	Added   bool
	Updated bool
}

func isAppLink(s string) bool {
	return strings.HasPrefix(s, appLinkScheme)
}

func AppLink(pkg string) string {
	pkg = strings.TrimSpace(pkg)
	if pkg == "" {
		return ""
	}
	return appLinkScheme + pkg
}

func sameHost(a, b string) bool {
	return strings.TrimPrefix(strings.ToLower(a), "www.") == strings.TrimPrefix(strings.ToLower(b), "www.")
}

func (v *Vault) SaveLogin(title, website, app, username, password string) (SaveResult, error) {
	website = strings.ToLower(strings.TrimSpace(website))
	username = strings.TrimSpace(username)
	link := AppLink(app)
	if password == "" || (website == "" && link == "") {
		return SaveResult{}, errSaveIncomplete
	}
	if website != "" && !keychain.IsWebsiteDomain(strings.TrimPrefix(website, "www.")) {
		website = ""
		if link == "" {
			return SaveResult{}, errSaveIncomplete
		}
	}

	v.mu.Lock()
	if v.p == nil {
		v.mu.Unlock()
		return SaveResult{}, errLocked
	}
	var live []keychain.WebPassword
	for _, w := range v.p.Web {
		if !w.IsDeleted {
			live = append(live, w)
		}
	}
	v.mu.Unlock()

	matches := func(w keychain.WebPassword) bool {
		if website != "" && w.Website && (sameHost(w.Domain, website) || slices.ContainsFunc(w.Domains, func(d string) bool { return sameHost(d, website) })) {
			return true
		}
		return link != "" && slices.Contains(w.Domains, link)
	}
	var found *keychain.WebPassword
	for i := range live {
		if live[i].Username == username && matches(live[i]) {
			found = &live[i]
			break
		}
	}
	if found == nil && link != "" {
		for i := range live {
			if live[i].Username == username && live[i].Password == password {
				found = &live[i]
				break
			}
		}
	}

	if found == nil {
		if strings.TrimSpace(title) == "" {
			title = cmp.Or(website, app)
		}
		var links []string
		if link != "" {
			links = []string{link}
		}
		if err := v.addPassword(title, website, username, password, "", links, true); err != nil {
			return SaveResult{}, err
		}
		return SaveResult{Added: true}, nil
	}

	w := *found
	domains := slices.Clone(w.Domains)
	if w.Website && len(domains) == 0 {
		domains = []string{w.Domain}
	}
	if website != "" && w.Website && !slices.ContainsFunc(domains, func(d string) bool { return sameHost(d, website) }) {
		domains = append(domains, website)
	}
	if link != "" && !slices.Contains(domains, link) {
		domains = append(domains, link)
	}
	if w.Password == password && slices.Equal(domains, w.Domains) {
		return SaveResult{ID: webID(w)}, nil
	}
	site := ""
	if w.Website {
		site = w.Domain
	}
	id, err := v.EditPassword(webID(w), w.Name, site, w.Username, password, w.Note, "", domains, false)
	if err != nil {
		return SaveResult{}, err
	}
	return SaveResult{ID: id, Updated: w.Password != password}, nil
}
