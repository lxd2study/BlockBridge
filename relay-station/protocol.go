package main

import (
	"bufio"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"io"
	"net"
	"strings"
	"sync"
	"sync/atomic"
	"time"
)

func readLine(reader *bufio.Reader, maxBytes int) (string, error) {
	var builder strings.Builder
	for {
		part, err := reader.ReadString('\n')
		if len(part) > 0 {
			part = strings.TrimRight(part, "\r\n")
			builder.WriteString(part)
		}
		if builder.Len() > maxBytes {
			return "", fmt.Errorf("protocol line is too long")
		}
		if err == nil {
			return builder.String(), nil
		}
		if err == io.EOF && builder.Len() > 0 {
			return builder.String(), nil
		}
		if err != nil {
			return "", err
		}
	}
}

func writeLine(conn net.Conn, line string) error {
	_, err := io.WriteString(conn, line+"\n")
	return err
}

func randomID() string {
	bytes := make([]byte, 16)
	if _, err := rand.Read(bytes); err != nil {
		return fmt.Sprintf("%x", time.Now().UnixNano())
	}
	return hex.EncodeToString(bytes)
}

func bridge(publicConn, dataConn net.Conn, tunnelToHost, tunnelToUser, relayToHost, relayToUser *atomic.Int64) {
	var once sync.Once
	closeBoth := func() {
		publicConn.Close()
		dataConn.Close()
	}

	var wait sync.WaitGroup
	wait.Add(2)
	go func() {
		defer wait.Done()
		n, _ := io.Copy(dataConn, publicConn)
		tunnelToHost.Add(n)
		relayToHost.Add(n)
		once.Do(closeBoth)
	}()
	go func() {
		defer wait.Done()
		n, _ := io.Copy(publicConn, dataConn)
		tunnelToUser.Add(n)
		relayToUser.Add(n)
		once.Do(closeBoth)
	}()
	wait.Wait()
}
