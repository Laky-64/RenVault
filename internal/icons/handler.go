package icons

import (
	"fmt"
	"net/http"
	"strings"

	"github.com/wailsapp/wails/v3/pkg/application"
)

func (s *IconService) Middleware() application.Middleware {
	return s.handler
}

func (s *IconService) handler(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.HasPrefix(r.URL.Path, "/icons") {
			next.ServeHTTP(w, r)
			return
		}
		src := r.URL.Query().Get("src")
		png, err := s.tile(src)

		if err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}

		w.Header().Set("Content-Type", "image/png")
		w.Header().Set("Content-Length", fmt.Sprint(len(png)))
		w.Header().Set("Cache-Control", "private, max-age=604800, immutable")
		w.Header().Set("Content-Security-Policy", "default-src 'none'; sandbox")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		_, _ = w.Write(png)
	})
}
