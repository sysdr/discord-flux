#!/usr/bin/env python3
"""Open N WebSocket connections to the load balancer so dashboard shows live metrics."""
import socket
import base64
import os
import time
import sys

HOST = "127.0.0.1"
PORT = 8080
NUM_CONNECTIONS = 6
HOLD_SECONDS = 20

def open_connection(n):
    key = base64.b64encode(os.urandom(16)).decode()
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(10)
    try:
        sock.connect((HOST, PORT))
        req = (
            f"GET / HTTP/1.1\r\n"
            f"Host: {HOST}:{PORT}\r\n"
            f"Upgrade: websocket\r\n"
            f"Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            f"Sec-WebSocket-Version: 13\r\n\r\n"
        )
        sock.send(req.encode())
        data = sock.recv(4096).decode()
        if "101" in data:
            return sock
    except Exception as e:
        print(f"Connection {n} failed: {e}", file=sys.stderr)
    return None

def main():
    socks = []
    for i in range(NUM_CONNECTIONS):
        s = open_connection(i)
        if s:
            socks.append(s)
            print(f"  Opened connection {len(socks)}/{NUM_CONNECTIONS}")
    print(f"  Holding {len(socks)} connections for {HOLD_SECONDS}s. Check dashboard: http://localhost:9090/dashboard")
    time.sleep(HOLD_SECONDS)
    for s in socks:
        try:
            s.close()
        except Exception:
            pass
    print("  Done.")

if __name__ == "__main__":
    main()
