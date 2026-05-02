package main

import (
	"crypto/subtle"
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"sync"
)

type StationConfig struct {
	Relay       RelayConfig   `json:"relay"`
	API         APIConfig     `json:"api"`
	LegacyAdmin *APIConfig    `json:"admin,omitempty"`
	Tokens      []TokenConfig `json:"tokens"`
}

type RelayConfig struct {
	Bind                 string `json:"bind"`
	ControlPort          int    `json:"controlPort"`
	PublicMin            int    `json:"publicMin"`
	PublicMax            int    `json:"publicMax"`
	ConnectTimeoutMillis int    `json:"connectTimeoutMillis"`
}

type APIConfig struct {
	Bind     string `json:"bind"`
	Username string `json:"username"`
	Password string `json:"password"`
}

type TokenConfig struct {
	Name    string `json:"name"`
	Token   string `json:"token"`
	Enabled bool   `json:"enabled"`
}

type ConfigStore struct {
	mu   sync.RWMutex
	path string
	cfg  StationConfig
}

func ResolveConfigPath(path string) (string, error) {
	path = strings.TrimSpace(path)
	if path == "" {
		path = "config/station.json"
	}

	candidates := []string{path}
	if !filepath.IsAbs(path) {
		if exe, err := os.Executable(); err == nil {
			exeDir := filepath.Dir(exe)
			candidates = append(candidates,
				filepath.Join(exeDir, path),
				filepath.Join(filepath.Dir(exeDir), path),
			)
		}
	}

	seen := map[string]struct{}{}
	var lastErr error
	for _, candidate := range candidates {
		clean := filepath.Clean(candidate)
		abs, err := filepath.Abs(clean)
		if err == nil {
			clean = abs
		}
		if _, ok := seen[clean]; ok {
			continue
		}
		seen[clean] = struct{}{}
		if _, err := os.Stat(clean); err == nil {
			return clean, nil
		} else {
			lastErr = err
		}
	}

	return "", fmt.Errorf("config file not found: %s (%v)", path, lastErr)
}

func LoadConfig(path string) (StationConfig, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return StationConfig{}, err
	}

	var cfg StationConfig
	if err := json.Unmarshal(data, &cfg); err != nil {
		return StationConfig{}, err
	}
	normalizeConfig(&cfg)
	if err := validateConfig(cfg); err != nil {
		return StationConfig{}, err
	}
	return cfg, nil
}

func NewConfigStore(path string, cfg StationConfig) *ConfigStore {
	return &ConfigStore{path: path, cfg: cfg}
}

func (s *ConfigStore) Snapshot() StationConfig {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return cloneConfig(s.cfg)
}

func (s *ConfigStore) CheckAPIAuth(username, password string) bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return subtle.ConstantTimeCompare([]byte(username), []byte(s.cfg.API.Username)) == 1 &&
		subtle.ConstantTimeCompare([]byte(password), []byte(s.cfg.API.Password)) == 1
}

func (s *ConfigStore) FindToken(raw string) (TokenConfig, bool) {
	s.mu.RLock()
	defer s.mu.RUnlock()
	for _, token := range s.cfg.Tokens {
		if token.Enabled && subtle.ConstantTimeCompare([]byte(token.Token), []byte(raw)) == 1 {
			return token, true
		}
	}
	return TokenConfig{}, false
}

func (s *ConfigStore) TokenList() []TokenView {
	s.mu.RLock()
	defer s.mu.RUnlock()

	tokens := make([]TokenView, 0, len(s.cfg.Tokens))
	for _, token := range s.cfg.Tokens {
		tokens = append(tokens, TokenView{
			Name:    token.Name,
			Masked:  maskToken(token.Token),
			Enabled: token.Enabled,
		})
	}
	return tokens
}

func (s *ConfigStore) AddToken(token TokenConfig) error {
	token.Name = strings.TrimSpace(token.Name)
	token.Token = strings.TrimSpace(token.Token)
	if token.Name == "" {
		return errors.New("token name is required")
	}
	if token.Token == "" {
		return errors.New("token value is required")
	}
	if strings.ContainsAny(token.Token, " \t\r\n") {
		return errors.New("token cannot contain whitespace")
	}

	s.mu.Lock()
	defer s.mu.Unlock()
	for _, existing := range s.cfg.Tokens {
		if strings.EqualFold(existing.Name, token.Name) {
			return errors.New("token name already exists")
		}
	}
	s.cfg.Tokens = append(s.cfg.Tokens, token)
	return s.saveLocked()
}

func (s *ConfigStore) DeleteToken(name string) error {
	name = strings.TrimSpace(name)
	s.mu.Lock()
	defer s.mu.Unlock()

	next := s.cfg.Tokens[:0]
	removed := false
	for _, token := range s.cfg.Tokens {
		if strings.EqualFold(token.Name, name) {
			removed = true
			continue
		}
		next = append(next, token)
	}
	if !removed {
		return errors.New("token not found")
	}
	s.cfg.Tokens = next
	return s.saveLocked()
}

func (s *ConfigStore) saveLocked() error {
	if err := validateConfig(s.cfg); err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(s.path), 0755); err != nil {
		return err
	}
	data, err := json.MarshalIndent(s.cfg, "", "  ")
	if err != nil {
		return err
	}
	data = append(data, '\n')
	return os.WriteFile(s.path, data, 0600)
}

func normalizeConfig(cfg *StationConfig) {
	cfg.Relay.Bind = strings.TrimSpace(cfg.Relay.Bind)
	if cfg.API.Bind == "" && cfg.LegacyAdmin != nil {
		cfg.API = *cfg.LegacyAdmin
	}
	cfg.LegacyAdmin = nil
	cfg.API.Bind = strings.TrimSpace(cfg.API.Bind)
	cfg.API.Username = strings.TrimSpace(cfg.API.Username)

	if cfg.Relay.Bind == "" {
		cfg.Relay.Bind = "0.0.0.0"
	}
	if cfg.Relay.ControlPort == 0 {
		cfg.Relay.ControlPort = 25566
	}
	if cfg.Relay.PublicMin == 0 {
		cfg.Relay.PublicMin = 25565
	}
	if cfg.Relay.PublicMax == 0 {
		cfg.Relay.PublicMax = 25665
	}
	if cfg.Relay.ConnectTimeoutMillis == 0 {
		cfg.Relay.ConnectTimeoutMillis = 15000
	}
	if cfg.API.Bind == "" {
		cfg.API.Bind = "0.0.0.0:8080"
	}
	if cfg.API.Username == "" {
		cfg.API.Username = "node-api"
	}
}

func validateConfig(cfg StationConfig) error {
	if !isPort(cfg.Relay.ControlPort) {
		return fmt.Errorf("controlPort must be between 1 and 65535")
	}
	if !isPort(cfg.Relay.PublicMin) || !isPort(cfg.Relay.PublicMax) {
		return fmt.Errorf("public port range must be between 1 and 65535")
	}
	if cfg.Relay.PublicMin > cfg.Relay.PublicMax {
		return fmt.Errorf("publicMin cannot be greater than publicMax")
	}
	if cfg.Relay.ConnectTimeoutMillis < 1000 {
		return fmt.Errorf("connectTimeoutMillis must be at least 1000")
	}
	if cfg.API.Password == "" {
		return fmt.Errorf("api password is required")
	}

	names := map[string]struct{}{}
	values := map[string]struct{}{}
	for _, token := range cfg.Tokens {
		if strings.TrimSpace(token.Name) == "" {
			return fmt.Errorf("token name is required")
		}
		if strings.TrimSpace(token.Token) == "" {
			return fmt.Errorf("token value is required")
		}
		key := strings.ToLower(strings.TrimSpace(token.Name))
		if _, exists := names[key]; exists {
			return fmt.Errorf("duplicate token name: %s", token.Name)
		}
		names[key] = struct{}{}
		if _, exists := values[token.Token]; exists {
			return fmt.Errorf("duplicate token value for %s", token.Name)
		}
		values[token.Token] = struct{}{}
	}
	return nil
}

func cloneConfig(cfg StationConfig) StationConfig {
	cfg.Tokens = append([]TokenConfig(nil), cfg.Tokens...)
	cfg.LegacyAdmin = nil
	return cfg
}

func isPort(port int) bool {
	return port >= 1 && port <= 65535
}

func maskToken(token string) string {
	if len(token) <= 8 {
		return "****"
	}
	return token[:4] + "..." + token[len(token)-4:]
}
