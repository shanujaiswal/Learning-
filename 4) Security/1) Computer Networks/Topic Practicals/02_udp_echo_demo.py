"""
02_udp_echo_demo.py
--------------------
Maps to Theory chapter: "TCP/IP, Ports, IP Addressing" (TCP vs UDP contrast)

A UDP server/client pair using only the standard library `socket` module.
Run in two separate terminals on the SAME machine:

    Terminal 1:  python 02_udp_echo_demo.py server
    Terminal 2:  python 02_udp_echo_demo.py client

WHAT IS STRUCTURALLY DIFFERENT FROM THE TCP VERSION (01_tcp_client_server.py):

  1. NO CONNECTION SETUP:
     - TCP:  client_sock.connect(...) performs a three-way handshake before
             any data can be sent; server must accept() that connection.
     - UDP:  there is no connect()/accept() at all (SOCK_DGRAM). The client
             just calls sendto(data, address) directly -- every packet
             ("datagram") carries its own destination address, independent
             of any prior packet.

  2. NO DELIVERY GUARANTEE:
     - TCP:  guarantees the bytes arrive, in order, exactly once (or the
             connection reports an error).
     - UDP:  "fire and forget". A datagram might be lost, duplicated, or
             arrive out of order, and neither side is automatically told.
             That's why this demo has no retry logic -- illustrating that
             reliability, if needed, is the APPLICATION's job with UDP.

  3. NO PERSISTENT STREAM:
     - TCP:  data is a continuous byte stream; message boundaries are not
             preserved (you must define your own framing, e.g. newlines).
     - UDP:  each sendto() call is one discrete datagram; each recvfrom()
             call returns exactly one datagram as sent (message boundaries
             ARE preserved by the protocol itself).

Safety: bound to 127.0.0.1 (loopback) only -- never exposed to the network.
"""

import socket
import sys

HOST = "127.0.0.1"
PORT = 5051   # different port from the TCP demo


def run_server():
    """Bind a UDP socket and echo back whatever datagrams arrive."""
    # SOCK_DGRAM = UDP. Note: no listen()/accept() -- UDP is connectionless.
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as server_sock:
        server_sock.bind((HOST, PORT))
        print(f"[server] UDP socket bound on {HOST}:{PORT}, waiting for datagrams")
        print("[server] press Ctrl+C to stop")

        try:
            while True:
                # recvfrom blocks until a datagram arrives; it also returns
                # the sender's address, since there is no established
                # "connection" to remember who we're talking to.
                data, client_addr = server_sock.recvfrom(1024)
                message = data.decode("utf-8")
                print(f"[server] received {message!r} from {client_addr}")

                if message.strip().lower() == "quit":
                    server_sock.sendto(b"bye", client_addr)
                    continue

                reply = f"echo: {message}"
                server_sock.sendto(reply.encode("utf-8"), client_addr)
        except KeyboardInterrupt:
            print("\n[server] stopped")


def run_client():
    """Send a few datagrams to the server and print whatever comes back."""
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as client_sock:
        # No connect() call needed -- each sendto() specifies the destination.
        client_sock.settimeout(3.0)  # don't hang forever if a datagram is lost
        server_addr = (HOST, PORT)

        messages = ["hello over UDP", "no handshake needed", "quit"]
        for msg in messages:
            client_sock.sendto(msg.encode("utf-8"), server_addr)
            print(f"[client] sent: {msg!r}")
            try:
                data, _ = client_sock.recvfrom(1024)
                print(f"[client] server replied: {data.decode('utf-8')!r}")
            except socket.timeout:
                print("[client] no reply received (this is possible with UDP!)")
            if msg == "quit":
                break

    print("[client] done")


def main():
    if len(sys.argv) != 2 or sys.argv[1] not in ("server", "client"):
        print("Usage: python 02_udp_echo_demo.py [server|client]")
        print("  Run 'server' in one terminal first, then 'client' in another.")
        sys.exit(1)

    if sys.argv[1] == "server":
        run_server()
    else:
        run_client()


if __name__ == "__main__":
    main()
