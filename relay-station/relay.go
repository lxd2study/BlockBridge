package main

import (
	"bufio"
	"context"
	"errors"
	"fmt"
	"net"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

type Relay struct {
	store       *ConfigStore
	startedAt   time.Time
	controlLn   net.Listener
	mu          sync.RWMutex
	tunnels     map[string]*Tunnel
	totalPublic atomic.Int64
	totalToHost atomic.Int64
	totalToUser atomic.Int64
}

type Tunnel struct {
	relay         *Relay
	token         string
	tokenName     string
	clientVersion string
	control       net.Conn
	controlMu     sync.Mutex
	publicLn      net.Listener
	publicPort    int
	createdAt     time.Time
	lastActivity  atomic.Int64
	active        atomic.Bool
	activeStream  atomic.Int64
	totalPublic   atomic.Int64
	totalToHost   atomic.Int64
	totalToUser   atomic.Int64
	pendingMu     sync.Mutex
	pending       map[string]net.Conn
}

type RelaySnapshot struct {
	StartedAt        time.Time        `json:"startedAt"`
	UptimeSeconds    int64            `json:"uptimeSeconds"`
	ActiveTunnels    int              `json:"activeTunnels"`
	ActiveStreams    int64            `json:"activeStreams"`
	PendingClients   int              `json:"pendingClients"`
	PortPoolUsed     int              `json:"portPoolUsed"`
	PortPoolTotal    int              `json:"portPoolTotal"`
	TotalPublic      int64            `json:"totalPublicConnections"`
	TotalBytesToHost int64            `json:"totalBytesToHost"`
	TotalBytesToUser int64            `json:"totalBytesToUser"`
	Tunnels          []TunnelSnapshot `json:"tunnels"`
}

type TunnelSnapshot struct {
	TokenName        string    `json:"tokenName"`
	ClientVersion    string    `json:"clientVersion"`
	PublicPort       int       `json:"publicPort"`
	PublicAddress    string    `json:"publicAddress"`
	CreatedAt        time.Time `json:"createdAt"`
	LastActivity      time.Time `json:"lastActivity"`
	ActiveStreams     int64     `json:"activeStreams"`
	PendingClients    int       `json:"pendingClients"`
	TotalPublic       int64     `json:"totalPublicConnections"`
	TotalBytesToHost  int64     `json:"totalBytesToHost"`
	TotalBytesToUser  int64     `json:"totalBytesToUser"`
}

type TokenUsageSnapshot struct {
	TokenName              string `json:"tokenName"`
	ActiveTunnels          int    `json:"activeTunnels"`
	ActiveStreams          int64  `json:"activeStreams"`
	PendingClients         int    `json:"pendingClients"`
	TotalPublicConnections int64  `json:"totalPublicConnections"`
	TotalBytesToHost       int64  `json:"totalBytesToHost"`
	TotalBytesToUser       int64  `json:"totalBytesToUser"`
}

func NewRelay(store *ConfigStore) *Relay {
	return &Relay{
		store:     store,
		startedAt: time.Now(),
		tunnels:   map[string]*Tunnel{},
	}
}

func (r *Relay) Start(ctx context.Context) error {
	cfg := r.store.Snapshot()
	address := net.JoinHostPort(cfg.Relay.Bind, strconv.Itoa(cfg.Relay.ControlPort))
	ln, err := net.Listen("tcp", address)
	if err != nil {
		return err
	}
	r.controlLn = ln
	fmt.Printf("relay station listening on %s\n", address)
	fmt.Printf("public port range: %d-%d\n", cfg.Relay.PublicMin, cfg.Relay.PublicMax)
	fmt.Printf("node api: %s\n", cfg.API.Bind)

	go func() {
		<-ctx.Done()
		_ = ln.Close()
	}()

	for {
		conn, err := ln.Accept()
		if err != nil {
			if ctx.Err() != nil || errors.Is(err, net.ErrClosed) {
				return nil
			}
			return err
		}
		go r.handleConnection(conn)
	}
}

func (r *Relay) Stop() {
	if r.controlLn != nil {
		_ = r.controlLn.Close()
	}
	r.mu.Lock()
	defer r.mu.Unlock()
	for _, tunnel := range r.tunnels {
		tunnel.Close()
	}
	r.tunnels = map[string]*Tunnel{}
}

func (r *Relay) Snapshot() RelaySnapshot {
	now := time.Now()
	cfg := r.store.Snapshot()
	snapshot := RelaySnapshot{
		StartedAt:        r.startedAt,
		UptimeSeconds:    int64(now.Sub(r.startedAt).Seconds()),
		PortPoolTotal:    availablePublicPortCount(cfg),
		TotalPublic:      r.totalPublic.Load(),
		TotalBytesToHost: r.totalToHost.Load(),
		TotalBytesToUser: r.totalToUser.Load(),
	}

	r.mu.RLock()
	defer r.mu.RUnlock()
	snapshot.ActiveTunnels = len(r.tunnels)
	snapshot.PortPoolUsed = len(r.tunnels)
	for _, tunnel := range r.tunnels {
		item := tunnel.Snapshot()
		snapshot.ActiveStreams += item.ActiveStreams
		snapshot.PendingClients += item.PendingClients
		snapshot.Tunnels = append(snapshot.Tunnels, item)
	}
	return snapshot
}

func (r *Relay) TokenUsage(name string) (TokenUsageSnapshot, bool) {
	r.mu.RLock()
	defer r.mu.RUnlock()

	var usage TokenUsageSnapshot
	for _, tunnel := range r.tunnels {
		if !strings.EqualFold(tunnel.tokenName, name) {
			continue
		}
		item := tunnel.Snapshot()
		usage.TokenName = tunnel.tokenName
		usage.ActiveTunnels++
		usage.ActiveStreams += item.ActiveStreams
		usage.PendingClients += item.PendingClients
		usage.TotalPublicConnections += item.TotalPublic
		usage.TotalBytesToHost += item.TotalBytesToHost
		usage.TotalBytesToUser += item.TotalBytesToUser
	}
	if usage.TokenName == "" {
		return TokenUsageSnapshot{TokenName: name}, false
	}
	return usage, true
}

func (r *Relay) CloseTunnelByName(name string) bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	for token, tunnel := range r.tunnels {
		if strings.EqualFold(tunnel.tokenName, name) {
			tunnel.Close()
			delete(r.tunnels, token)
			return true
		}
	}
	return false
}

func (r *Relay) handleConnection(conn net.Conn) {
	cfg := r.store.Snapshot()
	_ = conn.SetDeadline(time.Now().Add(time.Duration(cfg.Relay.ConnectTimeoutMillis) * time.Millisecond))
	reader := bufio.NewReader(conn)
	line, err := readLine(reader, 4096)
	_ = conn.SetDeadline(time.Time{})
	if err != nil {
		_ = conn.Close()
		return
	}

	parts := strings.Fields(line)
	if len(parts) == 0 {
		_ = conn.Close()
		return
	}

	switch parts[0] {
	case "HELLO":
		r.handleHello(conn, reader, parts)
	case "HOST":
		r.handleHost(conn, reader, parts)
	case "DATA":
		r.handleData(conn, parts)
	case "TEST":
		r.handleTest(conn, parts)
	default:
		_ = writeLine(conn, "ERR BAD_REQUEST unsupported command")
		_ = conn.Close()
	}
}

func (r *Relay) handleHello(conn net.Conn, reader *bufio.Reader, parts []string) {
	if len(parts) < 2 {
		_ = writeLine(conn, "ERR BAD_REQUEST bad HELLO command")
		_ = conn.Close()
		return
	}
	if err := writeLine(conn, "OK HELLO 0.2"); err != nil {
		_ = conn.Close()
		return
	}
	line, err := readLine(reader, 4096)
	if err != nil {
		_ = conn.Close()
		return
	}
	next := strings.Fields(line)
	if len(next) == 0 {
		_ = conn.Close()
		return
	}
	switch next[0] {
	case "HOST":
		if len(next) == 3 {
			next = append(next, parts[1])
		}
		r.handleHost(conn, reader, next)
	case "TEST":
		if len(next) == 2 {
			next = append(next, parts[1])
		}
		r.handleTest(conn, next)
	default:
		_ = writeLine(conn, "ERR BAD_REQUEST unsupported command after HELLO")
		_ = conn.Close()
	}
}

func (r *Relay) handleHost(conn net.Conn, reader *bufio.Reader, parts []string) {
	if len(parts) < 3 {
		_ = writeLine(conn, "ERR BAD_REQUEST bad HOST command")
		_ = conn.Close()
		return
	}

	tokenText := parts[1]
	token, ok := r.store.FindToken(tokenText)
	if !ok {
		_ = writeLine(conn, "ERR TOKEN_REJECTED token rejected")
		_ = conn.Close()
		return
	}

	requestedPort, _ := strconv.Atoi(parts[2])
	clientVersion := ""
	if len(parts) >= 4 {
		clientVersion = parts[3]
	}

	r.mu.Lock()
	if old := r.tunnels[tokenText]; old != nil {
		old.Close()
		delete(r.tunnels, tokenText)
	}

	publicLn, err := r.allocatePublicListenerLocked(requestedPort)
	if err != nil {
		r.mu.Unlock()
		_ = writeLine(conn, "ERR PORT_BIND_FAILED "+err.Error())
		_ = conn.Close()
		return
	}

	tunnel := &Tunnel{
		relay:         r,
		token:         tokenText,
		tokenName:     token.Name,
		clientVersion: clientVersion,
		control:       conn,
		publicLn:      publicLn,
		publicPort:    publicLn.Addr().(*net.TCPAddr).Port,
		createdAt:     time.Now(),
		pending:       map[string]net.Conn{},
	}
	tunnel.active.Store(true)
	tunnel.touch()

	r.tunnels[tokenText] = tunnel
	r.mu.Unlock()

	go tunnel.acceptPublicClients()
	if err := writeLine(conn, fmt.Sprintf("OK %d ready", tunnel.publicPort)); err != nil {
		r.mu.Lock()
		if r.tunnels[tokenText] == tunnel {
			delete(r.tunnels, tokenText)
		}
		r.mu.Unlock()
		tunnel.Close()
		return
	}
	fmt.Printf("host %s published port %d\n", tunnel.tokenName, tunnel.publicPort)

	for tunnel.active.Load() {
		line, err := readLine(reader, 4096)
		if err != nil {
			break
		}
		if strings.HasPrefix(line, "PING") {
			parts := strings.Fields(line)
			if len(parts) >= 2 {
				_ = tunnel.sendControl("PONG " + parts[1])
			} else {
				_ = tunnel.sendControl("PONG")
			}
		}
	}

	r.mu.Lock()
	if r.tunnels[tokenText] == tunnel {
		delete(r.tunnels, tokenText)
	}
	r.mu.Unlock()
	tunnel.Close()
	fmt.Printf("host %s disconnected from port %d\n", tunnel.tokenName, tunnel.publicPort)
}

func (r *Relay) handleTest(conn net.Conn, parts []string) {
	defer conn.Close()
	if len(parts) < 2 {
		_ = writeLine(conn, "ERR BAD_REQUEST bad TEST command")
		return
	}
	if _, ok := r.store.FindToken(parts[1]); !ok {
		_ = writeLine(conn, "ERR TOKEN_REJECTED token rejected")
		return
	}
	_ = writeLine(conn, "OK ready")
}

func (r *Relay) handleData(conn net.Conn, parts []string) {
	if len(parts) < 3 {
		_ = writeLine(conn, "ERR BAD_REQUEST bad DATA command")
		_ = conn.Close()
		return
	}

	token := parts[1]
	id := parts[2]
	r.mu.RLock()
	tunnel := r.tunnels[token]
	r.mu.RUnlock()
	if tunnel == nil {
		_ = writeLine(conn, "ERR NODE_UNAVAILABLE tunnel not found")
		_ = conn.Close()
		return
	}
	tunnel.attachData(id, conn)
}

func (r *Relay) allocatePublicListenerLocked(requestedPort int) (net.Listener, error) {
	cfg := r.store.Snapshot()
	if requestedPort > 0 {
		if requestedPort < cfg.Relay.PublicMin || requestedPort > cfg.Relay.PublicMax {
			return nil, fmt.Errorf("requested port is outside the allowed range")
		}
		if reservedPublicPort(requestedPort, cfg) {
			return nil, fmt.Errorf("requested port is reserved")
		}
		if r.publicPortInUseLocked(requestedPort) {
			return nil, fmt.Errorf("requested port is already in use")
		}
		return net.Listen("tcp", net.JoinHostPort(cfg.Relay.Bind, strconv.Itoa(requestedPort)))
	}

	for port := cfg.Relay.PublicMin; port <= cfg.Relay.PublicMax; port++ {
		if reservedPublicPort(port, cfg) || r.publicPortInUseLocked(port) {
			continue
		}
		ln, err := net.Listen("tcp", net.JoinHostPort(cfg.Relay.Bind, strconv.Itoa(port)))
		if err == nil {
			return ln, nil
		}
	}
	return nil, fmt.Errorf("no free public ports")
}

func (r *Relay) publicPortInUseLocked(port int) bool {
	for _, tunnel := range r.tunnels {
		if tunnel.active.Load() && tunnel.publicPort == port {
			return true
		}
	}
	return false
}

func reservedPublicPort(port int, cfg StationConfig) bool {
	if port == cfg.Relay.ControlPort {
		return true
	}
	return port == bindPort(cfg.API.Bind)
}

func bindPort(bind string) int {
	_, portText, err := net.SplitHostPort(bind)
	if err != nil {
		index := strings.LastIndex(bind, ":")
		if index < 0 || index == len(bind)-1 {
			return 0
		}
		portText = bind[index+1:]
	}
	port, err := strconv.Atoi(portText)
	if err != nil {
		return 0
	}
	return port
}

func availablePublicPortCount(cfg StationConfig) int {
	total := 0
	for port := cfg.Relay.PublicMin; port <= cfg.Relay.PublicMax; port++ {
		if reservedPublicPort(port, cfg) {
			continue
		}
		total++
	}
	return total
}

func (t *Tunnel) acceptPublicClients() {
	for t.active.Load() {
		conn, err := t.publicLn.Accept()
		if err != nil {
			return
		}
		t.totalPublic.Add(1)
		t.relay.totalPublic.Add(1)
		t.touch()
		if err := t.requestDataConnection(conn); err != nil {
			_ = conn.Close()
		}
	}
}

func (t *Tunnel) requestDataConnection(conn net.Conn) error {
	cfg := t.relay.store.Snapshot()
	id := randomID()

	t.pendingMu.Lock()
	if len(t.pending) >= cfg.Relay.MaxPendingClientsPerToken {
		t.pendingMu.Unlock()
		return fmt.Errorf("pending client limit reached")
	}
	if t.activeStream.Load() >= int64(cfg.Relay.MaxConcurrentStreamsPerToken) {
		t.pendingMu.Unlock()
		return fmt.Errorf("stream limit reached")
	}
	t.pending[id] = conn
	t.pendingMu.Unlock()

	timeout := time.Duration(cfg.Relay.PendingClientTimeoutMillis) * time.Millisecond
	time.AfterFunc(timeout, func() {
		t.pendingMu.Lock()
		pending := t.pending[id]
		delete(t.pending, id)
		t.pendingMu.Unlock()
		if pending != nil {
			_ = pending.Close()
		}
	})

	if err := t.sendControl("OPEN " + id); err != nil {
		t.pendingMu.Lock()
		delete(t.pending, id)
		t.pendingMu.Unlock()
		return err
	}
	return nil
}

func (t *Tunnel) attachData(id string, dataConn net.Conn) {
	cfg := t.relay.store.Snapshot()
	if t.activeStream.Load() >= int64(cfg.Relay.MaxConcurrentStreamsPerToken) {
		_ = writeLine(dataConn, "ERR LIMIT_EXCEEDED stream limit reached")
		_ = dataConn.Close()
		return
	}
	t.pendingMu.Lock()
	publicConn := t.pending[id]
	delete(t.pending, id)
	t.pendingMu.Unlock()

	if publicConn == nil {
		_ = writeLine(dataConn, "ERR NODE_UNAVAILABLE pending client not found")
		_ = dataConn.Close()
		return
	}

	t.activeStream.Add(1)
	t.touch()
	go func() {
		defer t.activeStream.Add(-1)
		bridge(publicConn, dataConn, &t.totalToHost, &t.totalToUser, &t.relay.totalToHost, &t.relay.totalToUser)
		t.touch()
	}()
}

func (t *Tunnel) sendControl(line string) error {
	t.controlMu.Lock()
	defer t.controlMu.Unlock()
	return writeLine(t.control, line)
}

func (t *Tunnel) Snapshot() TunnelSnapshot {
	t.pendingMu.Lock()
	pending := len(t.pending)
	t.pendingMu.Unlock()

	last := time.Unix(t.lastActivity.Load(), 0)
	return TunnelSnapshot{
		TokenName:       t.tokenName,
		ClientVersion:   t.clientVersion,
		PublicPort:      t.publicPort,
		PublicAddress:   "",
		CreatedAt:       t.createdAt,
		LastActivity:     last,
		ActiveStreams:    t.activeStream.Load(),
		PendingClients:   pending,
		TotalPublic:      t.totalPublic.Load(),
		TotalBytesToHost: t.totalToHost.Load(),
		TotalBytesToUser: t.totalToUser.Load(),
	}
}

func (t *Tunnel) Close() {
	if !t.active.Swap(false) {
		return
	}
	_ = t.publicLn.Close()
	_ = t.control.Close()
	t.pendingMu.Lock()
	for _, conn := range t.pending {
		_ = conn.Close()
	}
	t.pending = map[string]net.Conn{}
	t.pendingMu.Unlock()
}

func (t *Tunnel) touch() {
	t.lastActivity.Store(time.Now().Unix())
}
