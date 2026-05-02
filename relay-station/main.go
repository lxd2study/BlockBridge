package main

import (
	"context"
	"flag"
	"log"
	"os/signal"
	"syscall"
	"time"
)

func main() {
	configPath := flag.String("config", "config/station.json", "path to station JSON config")
	flag.Parse()

	resolvedConfigPath, err := ResolveConfigPath(*configPath)
	if err != nil {
		log.Fatalf("resolve config: %v", err)
	}

	cfg, err := LoadConfig(resolvedConfigPath)
	if err != nil {
		log.Fatalf("load config: %v", err)
	}

	store := NewConfigStore(resolvedConfigPath, cfg)
	relay := NewRelay(store)
	api := NewAPIServer(store, relay)

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	errCh := make(chan error, 2)
	go func() { errCh <- relay.Start(ctx) }()
	go func() { errCh <- api.Start(ctx) }()

	select {
	case err := <-errCh:
		if err != nil {
			log.Printf("service stopped: %v", err)
		}
		stop()
	case <-ctx.Done():
	}

	relay.Stop()
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_ = api.Stop(shutdownCtx)
}
