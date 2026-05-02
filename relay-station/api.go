package main

import (
	"context"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"net/url"
	"strings"
	"time"
)

type APIServer struct {
	store  *ConfigStore
	relay  *Relay
	server *http.Server
}

type StatusResponse struct {
	Relay   RelayConfig   `json:"relay"`
	API     APIView       `json:"api"`
	Tokens  []TokenView   `json:"tokens"`
	Runtime RelaySnapshot `json:"runtime"`
}

type APIView struct {
	Bind     string `json:"bind"`
	Username string `json:"username"`
}

type TokenView struct {
	Name    string `json:"name"`
	Masked  string `json:"masked"`
	Enabled bool   `json:"enabled"`
}

func NewAPIServer(store *ConfigStore, relay *Relay) *APIServer {
	return &APIServer{store: store, relay: relay}
}

func (a *APIServer) Start(ctx context.Context) error {
	cfg := a.store.Snapshot()
	mux := http.NewServeMux()
	mux.HandleFunc("/api/status", a.handleStatus)
	mux.HandleFunc("/api/tokens", a.handleTokens)
	mux.HandleFunc("/api/tokens/", a.handleTokenByName)
	mux.HandleFunc("/api/tunnels/", a.handleTunnelByName)
	mux.HandleFunc("/", a.handleNotFound)

	a.server = &http.Server{
		Addr:              cfg.API.Bind,
		Handler:           a.requireAuth(mux),
		ReadHeaderTimeout: 5 * time.Second,
	}

	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = a.server.Shutdown(shutdownCtx)
	}()

	log.Printf("node api listening on http://%s", cfg.API.Bind)
	err := a.server.ListenAndServe()
	if errors.Is(err, http.ErrServerClosed) {
		return nil
	}
	return err
}

func (a *APIServer) Stop(ctx context.Context) error {
	if a.server == nil {
		return nil
	}
	return a.server.Shutdown(ctx)
}

func (a *APIServer) requireAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		username, password, ok := r.BasicAuth()
		if !ok || !a.store.CheckAPIAuth(username, password) {
			w.Header().Set("WWW-Authenticate", `Basic realm="LAN Tunnel Relay Node API"`)
			http.Error(w, "authentication required", http.StatusUnauthorized)
			return
		}
		next.ServeHTTP(w, r)
	})
}

func (a *APIServer) handleNotFound(w http.ResponseWriter, r *http.Request) {
	http.NotFound(w, r)
}

func (a *APIServer) handleStatus(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	cfg := a.store.Snapshot()
	writeJSON(w, StatusResponse{
		Relay:   cfg.Relay,
		API:     APIView{Bind: cfg.API.Bind, Username: cfg.API.Username},
		Tokens:  a.store.TokenList(),
		Runtime: a.relay.Snapshot(),
	})
}

func (a *APIServer) handleTokens(w http.ResponseWriter, r *http.Request) {
	switch r.Method {
	case http.MethodGet:
		writeJSON(w, map[string]any{"tokens": a.store.TokenList()})
	case http.MethodPost:
		var token TokenConfig
		if err := json.NewDecoder(r.Body).Decode(&token); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		if err := a.store.AddToken(token); err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		w.WriteHeader(http.StatusCreated)
		writeJSON(w, map[string]string{"status": "created"})
	default:
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
	}
}

func (a *APIServer) handleTokenByName(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodDelete {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	name := pathTail(r.URL.Path, "/api/tokens/")
	if name == "" {
		http.Error(w, "token name is required", http.StatusBadRequest)
		return
	}
	if err := a.store.DeleteToken(name); err != nil {
		http.Error(w, err.Error(), http.StatusNotFound)
		return
	}
	a.relay.CloseTunnelByName(name)
	writeJSON(w, map[string]string{"status": "deleted"})
}

func (a *APIServer) handleTunnelByName(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost || !strings.HasSuffix(r.URL.Path, "/close") {
		http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
		return
	}
	name := strings.TrimSuffix(pathTail(r.URL.Path, "/api/tunnels/"), "/close")
	if name == "" {
		http.Error(w, "tunnel name is required", http.StatusBadRequest)
		return
	}
	if !a.relay.CloseTunnelByName(name) {
		http.Error(w, "tunnel not found", http.StatusNotFound)
		return
	}
	writeJSON(w, map[string]string{"status": "closed"})
}

func pathTail(path, prefix string) string {
	raw := strings.TrimPrefix(path, prefix)
	value, err := url.PathUnescape(raw)
	if err != nil {
		return raw
	}
	return value
}

func writeJSON(w http.ResponseWriter, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	encoder := json.NewEncoder(w)
	encoder.SetIndent("", "  ")
	_ = encoder.Encode(value)
}
