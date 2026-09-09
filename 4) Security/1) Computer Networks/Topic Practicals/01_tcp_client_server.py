"""
01_tcp_client_server.py
------------------------
Maps to Theory chapter: "TCP/IP, Ports, IP Addressing"

A minimal but REAL TCP server and client using only the standard library
`socket` module. Run in two separate terminals on the SAME machine:

    Terminal 1:  python 01_tcp_client_server.py server
    Terminal 2:  python 01_tcp_client_server.py client

What this demonstrates concretely:
  - TCP is CONNECTION-ORIENTED: the client must connect() before any data
    flows, and the server must listen()/accept() that connection.
  - Ports: the server binds to a specific port (5050) on localhost; the
    client must know/target that exact port to reach the right application.
  - Reliable, ordered, byte-stream delivery: TCP guarantees the bytes sent
    arrive in order and complete (this is what "connection-oriented" buys us,
    contrast with 02_udp_echo_demo.py).

Safety: bound to 127.0.0.1 (loopback) only -- never exposed to the network.
"""

import socket
import sys

HOST = "127.0.0.1"   # loopback only -- do not change to 0.0.0.0 for this demo
PORT = 5050           # arbitrary unprivileged port (>1023) for our demo service


def run_server():
    """Bind, listen, accept a single connection, echo lines back, then exit."""
    # AF_INET = IPv4, SOCK_STREAM = TCP
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server_sock:
        # Allow quick restart of the server without waiting for the OS to
        # release the port (common in dev/demo scenarios).
        server_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

        server_sock.bind((HOST, PORT))   # claim the (IP, port) pair
        server_sock.listen(1)            # start listening, backlog of 1 pending connection
        print(f"[server] listening on {HOST}:{PORT} ... waiting for a client")

        conn, addr = server_sock.accept()  # BLOCKS here until a client connects
        with conn:
            print(f"[server] connection established from {addr}")
            while True:
                data = conn.recv(1024)     # read up to 1024 bytes
                if not data:                # empty bytes == client closed connection
                    print("[server] client disconnected")
                    break
                message = data.decode("utf-8")
                print(f"[server] received: {message!r}")
                if message.strip().lower() == "quit":
                    conn.sendall(b"bye\n")
                    break
                reply = f"echo: {message}"
                conn.sendall(reply.encode("utf-8"))
        print("[server] shutting down")


def run_client():
    """Connect to the server, send a couple of test lines, print replies."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as client_sock:
        print(f"[client] connecting to {HOST}:{PORT} ...")
        client_sock.connect((HOST, PORT))   # the TCP three-way handshake happens here
        print("[client] connected")

        messages = ["hello from client", "TCP guarantees delivery order", "quit"]
        for msg in messages:
            client_sock.sendall((msg + "\n").encode("utf-8"))
            print(f"[client] sent: {msg!r}")
            response = client_sock.recv(1024)
            print(f"[client] server replied: {response.decode('utf-8')!r}")
            if msg == "quit":
                break

    print("[client] connection closed")


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("server", "client"):
        print("Usage: python 01_tcp_client_server.py [server|client]")
        print("  Run 'server' in one terminal first, then 'client' in another.")
        sys.exit(1)

    if sys.argv[1] == "server":
        run_server()
    else:
        run_client()


if __name__ == "__main__":
    main()
