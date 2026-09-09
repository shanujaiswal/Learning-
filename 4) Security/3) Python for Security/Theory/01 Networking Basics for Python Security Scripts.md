### Networking Basics for Python Security Scripts

--> Almost every security tool (scanners, sniffers, exploit PoCs, C2 clients) eventually talks to a network socket.
--> Python's built-in `socket` module gives direct access to the same BSD socket API that tools like nmap and netcat use under the hood.
--> Understanding sockets is the foundation before touching higher-level libraries like `requests` or `scapy`.

## TCP vs UDP

--> Both are transport-layer protocols that sit on top of IP. They solve different problems.

1. TCP (Transmission Control Protocol) – Connection-oriented. Performs a handshake (SYN, SYN-ACK, ACK) before data flows. Guarantees ordered, reliable delivery. Used by HTTP, SSH, FTP.
2. UDP (User Datagram Protocol) – Connectionless. Fire-and-forget datagrams, no handshake, no guarantee of delivery or order. Used by DNS, DHCP, streaming, and by many scanners for speed.

--> Security implication: TCP scanning is more reliable but noisier (full handshake or half-open SYN scan). UDP scanning is faster but unreliable because a closed UDP port often just... doesn't reply, so you can't easily tell "closed" from "filtered by firewall".

## The `socket` module

--> A socket is created with a family (address type) and a type (protocol).

```python
import socket

# AF_INET  -> IPv4 addressing
# SOCK_STREAM -> TCP
tcp_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

# AF_INET + SOCK_DGRAM -> UDP
udp_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
```

--> Core methods you will use constantly:

1. `connect((host, port))` – used by a client to open a TCP connection.
2. `bind((host, port))` – used by a server to claim an address/port.
3. `listen(backlog)` – puts a TCP server socket into listening mode.
4. `accept()` – blocks until a client connects, returns `(conn_socket, addr)`.
5. `send(data)` / `sendall(data)` – send bytes on a connected socket.
6. `recv(bufsize)` – receive up to `bufsize` bytes (blocking by default).
7. `close()` – release the socket.

--> IMPORTANT: `send`/`recv` work with **bytes**, not `str`. You must `.encode()` before sending and `.decode()` after receiving.

## A minimal TCP server

```python
import socket

HOST = "127.0.0.1"
PORT = 9009

server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
# SO_REUSEADDR lets you restart the server quickly without "Address already in use"
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server.bind((HOST, PORT))
server.listen(1)
print(f"[*] Listening on {HOST}:{PORT}")

conn, addr = server.accept()
print(f"[+] Connection from {addr}")

data = conn.recv(1024)
print(f"[*] Received: {data.decode()}")   # Received: hello server

conn.sendall(b"ACK: message received")
conn.close()
server.close()
```

## A minimal TCP client

```python
import socket

client = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
client.connect(("127.0.0.1", 9009))
client.sendall(b"hello server")

response = client.recv(1024)
print(response.decode())   # ACK: message received

client.close()
```

--> Run the server script first, then the client script in a second terminal. This client/server pair is the skeleton behind every custom C2 listener, reverse shell handler, and bind shell you will ever read about.

## Timeouts

--> By default, `connect()` and `recv()` block forever if the remote host never responds. In a scanner that is fatal — one dead host would freeze the whole scan.
--> Always set a timeout for security tooling.

```python
import socket

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.settimeout(2.0)   # seconds

try:
    s.connect(("10.0.0.5", 445))
    print("Port open")
except socket.timeout:
    print("No response within 2 seconds (likely filtered)")
except ConnectionRefusedError:
    print("Port closed (RST received)")
finally:
    s.close()
```

## Handling `socket.error` and friends

--> `socket.error` is actually an alias for the built-in `OSError` in modern Python. Specific, more useful exceptions inherit from it:

1. `socket.timeout` – operation exceeded the timeout you set.
2. `ConnectionRefusedError` – remote host actively rejected the connection (RST), meaning the port is closed.
3. `socket.gaierror` – DNS/address resolution failed (bad hostname).
4. `OSError` (generic) – covers "Network is unreachable", permission errors, etc.

```python
import socket

def probe(host, port, timeout=1.5):
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.settimeout(timeout)
            s.connect((host, port))
            return "open"
    except socket.timeout:
        return "filtered"
    except ConnectionRefusedError:
        return "closed"
    except socket.gaierror:
        return "dns-error"
    except OSError as e:
        return f"error: {e}"

print(probe("scanme.nmap.org", 22))   # open
print(probe("scanme.nmap.org", 4444)) # filtered / closed
```

--> Using `with socket.socket(...) as s:` automatically calls `close()` for you, even if an exception is raised. Prefer this pattern over manual `close()` calls.

## Worked example: a single-threaded TCP port scanner

--> This mirrors what nmap's `-sT` (TCP connect scan) does conceptually, just slower because it is single-threaded and sequential.
--> Only ever run this against hosts you own or are explicitly authorized to test (e.g. `scanme.nmap.org`, which Nmap's maintainers leave up specifically for practice).

```python
import socket
import errno

def scan_port(host, port, timeout=1.0):
    """Return 'open', 'closed', or 'filtered' for a single TCP port."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.settimeout(timeout)
    try:
        result = sock.connect_ex((host, port))  # connect_ex returns an errno instead of raising
        if result == 0:
            return "open"
        elif result in (errno.ECONNREFUSED,):
            return "closed"
        else:
            return "filtered"
    except socket.timeout:
        return "filtered"
    except socket.error:
        return "error"
    finally:
        sock.close()

def scan_range(host, start_port, end_port):
    print(f"[*] Scanning {host} ports {start_port}-{end_port}")
    open_ports = []
    for port in range(start_port, end_port + 1):
        status = scan_port(host, port)
        if status == "open":
            print(f"    {port}/tcp  open")
            open_ports.append(port)
    return open_ports

if __name__ == "__main__":
    target = "scanme.nmap.org"
    found = scan_range(target, 20, 100)
    print(f"[+] Open ports: {found}")   # e.g. [22, 80]
```

--> `connect_ex()` is preferred over `connect()` inside a scanner loop: it returns an error code (an `errno` value) instead of raising an exception, so the hot loop stays simple and fast. `0` means success (port open).
--> A real scanner would add threading or `asyncio` to parallelize thousands of ports — sequential scanning of a /24 network at 1 second timeout per port would take hours. That is a topic for a later concurrency-focused note; the socket fundamentals above are the base every faster version builds on.

## Ethical note

--> Port scanning, connecting to services, and probing hosts you do not own or do not have written authorization to test can be illegal (e.g. under the U.S. Computer Fraud and Abuse Act, UK Computer Misuse Act, or local equivalents). Practice only on lab machines, deliberately vulnerable VMs (Metasploitable, DVWA), or hosts that explicitly permit testing (scanme.nmap.org).
