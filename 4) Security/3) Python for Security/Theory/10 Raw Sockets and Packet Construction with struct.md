### Raw Sockets and Packet Construction with struct

--> `04 Packet Crafting and Sniffing with Scapy.md` covered packet crafting through Scapy's high-level layer objects. This file goes underneath Scapy entirely -- building the exact same headers BYTE BY BYTE with plain sockets and the `struct` module, which is what Scapy itself is ultimately doing internally. Understanding this layer matters for situations where Scapy isn't available, is too slow for a tight loop, or when you simply need to understand what "raw socket" actually means at the OS level.

## LEGAL AND ETHICAL WARNING

--> Everything in this file requires elevated privileges specifically BECAUSE raw sockets let a program bypass the normal OS-provided TCP/IP stack behavior and inject arbitrary, hand-crafted packets onto the network. Only run these examples against your own lab VMs or an isolated virtual network you administer -- the legal exposure is identical to the Scapy file's warning, and arguably higher here since nothing stops you from constructing a header field wrong in a way that behaves unpredictably on a real network.

## What a raw socket actually is

--> A normal socket (`SOCK_STREAM`, `SOCK_DGRAM`, as used throughout `01 Networking Basics for Python Security Scripts.md`) hands you an abstraction -- the OS's TCP/IP stack builds the actual IP/TCP/UDP headers for you; you just read/write a stream or send/receive datagrams of payload data.
--> `socket.SOCK_RAW` instead gives you access at the IP layer (or, with `AF_PACKET` on Linux, the Ethernet layer) directly -- you construct the IP header (and everything above it) yourself, byte for byte, and hand the OS a fully-formed packet to transmit as-is. This requires root/Administrator privileges on every mainstream OS precisely because it lets a program impersonate arbitrary source addresses and craft protocol-violating packets that a normal socket API would never permit.

```python
import socket

# IPPROTO_TCP here means "I will supply the TCP header myself" -- IP_HDRINCL further tells
# the OS "I am also supplying the IP header myself, don't build one for me"
raw_sock = socket.socket(socket.AF_INET, socket.SOCK_RAW, socket.IPPROTO_TCP)
raw_sock.setsockopt(socket.IPPROTO_IP, socket.IP_HDRINCL, 1)
```

## The `struct` module -- converting Python values to exact wire bytes

--> Network protocol headers are defined as exact sequences of bits/bytes in a fixed layout -- `struct.pack()` takes Python values and packs them into bytes matching a format string describing each field's size and byte order; `struct.unpack()` does the reverse when parsing a captured packet.
--> Network byte order is BIG-ENDIAN (most-significant byte first) -- the format character `!` (or `>`) forces big-endian packing regardless of the host machine's own native byte order, which is essential: a header packed in the WRONG byte order is simply a different, wrong header, byte-for-byte.

```python
import struct

# Common struct format characters:
#   B = unsigned char  (1 byte)      H = unsigned short (2 bytes)
#   I = unsigned int   (4 bytes)     ! = network byte order (big-endian), no padding

# Example: pack a source port (2 bytes) and destination port (2 bytes) as network byte order
header_fragment = struct.pack("!HH", 51000, 80)
print(header_fragment)          # b'\xc7\xb8\x00P' -- exactly 4 raw bytes, ready for the wire

# Unpacking the same bytes back into Python values:
src_port, dst_port = struct.unpack("!HH", header_fragment)
print(src_port, dst_port)       # 51000 80
```

## Building an IPv4 header by hand

--> The IPv4 header is a fixed 20-byte structure (without options) -- every field below maps to a specific bit range defined by RFC 791, and `struct.pack` builds them in that exact order.

```python
import struct, socket

def build_ip_header(src_ip, dst_ip, payload_len, protocol=socket.IPPROTO_TCP):
    version_ihl = (4 << 4) + 5     # version=4 (IPv4), header length=5 (x4 bytes = 20 bytes, no options)
    tos = 0                        # type of service / DSCP -- 0 = default, no special QoS marking
    total_length = 20 + payload_len
    identification = 54321         # arbitrary ID -- used for fragment reassembly if the packet is split
    flags_fragment_offset = 0      # 0 = don't fragment this example, no offset
    ttl = 64                       # time to live -- decremented by each router hop, prevents infinite loops
    checksum = 0                   # computed and inserted afterward -- 0 here is a required placeholder

    header = struct.pack(
        "!BBHHHBBH4s4s",
        version_ihl, tos, total_length,
        identification, flags_fragment_offset,
        ttl, protocol, checksum,
        socket.inet_aton(src_ip),   # inet_aton converts "192.168.1.10" -> its packed 4-byte binary form
        socket.inet_aton(dst_ip),
    )
    return header
```

--> `socket.inet_aton()` / `socket.inet_ntoa()` convert between dotted-decimal string IPs and the raw 4-byte binary form the header actually needs -- exactly analogous to `struct.pack`/`unpack` but specialized for addresses.
--> The checksum field is deliberately left as 0 in the packed bytes above, then computed over the assembled header (with that field temporarily treated as 0) and patched in afterward -- this two-pass approach (build with a zero placeholder, checksum, then splice the real value in) is the standard pattern for every protocol with a header checksum.

```python
def checksum(data):
    """Standard IP/TCP/UDP one's-complement checksum algorithm."""
    if len(data) % 2:
        data += b"\x00"   # pad to an even number of bytes if needed
    total = sum(struct.unpack(f"!{len(data)//2}H", data))
    total = (total >> 16) + (total & 0xFFFF)   # fold any carry-out back in
    total += total >> 16
    return struct.pack("!H", ~total & 0xFFFF)

def build_ip_header_with_checksum(src_ip, dst_ip, payload_len, protocol=socket.IPPROTO_TCP):
    header_without_checksum = build_ip_header(src_ip, dst_ip, payload_len, protocol)
    csum = checksum(header_without_checksum)
    # splice the real checksum into the placeholder position (bytes 10-11 of the header)
    return header_without_checksum[:10] + csum + header_without_checksum[12:]
```

## Building a bare TCP header by hand

```python
def build_tcp_header(src_port, dst_port, seq=0, ack_seq=0, flags="S"):
    flag_bits = {"F": 0x01, "S": 0x02, "R": 0x04, "P": 0x08, "A": 0x10, "U": 0x20}
    flag_value = sum(flag_bits[f] for f in flags)   # e.g. "S" -> just SYN; "SA" -> SYN+ACK combined

    data_offset_reserved = (5 << 4)   # header length = 5 x 4 bytes = 20 bytes, no TCP options
    window = 8192                      # receive window size advertised to the peer
    checksum_placeholder = 0
    urgent_pointer = 0

    header = struct.pack(
        "!HHLLBBHHH",
        src_port, dst_port,
        seq, ack_seq,
        data_offset_reserved, flag_value,
        window, checksum_placeholder, urgent_pointer,
    )
    return header

# This is the exact SYN flag Scapy's TCP(flags="S") sets under the hood in file 04 --
# here every single bit of that flags byte is assembled explicitly rather than named for you.
```

--> The TCP checksum is actually computed over the TCP header/payload PLUS a "pseudo-header" (source IP, dest IP, protocol number, TCP length) borrowed from the IP layer -- this cross-layer dependency is exactly why TCP checksums can't be computed from the TCP header alone, unlike a purely self-contained field.

## Sending the hand-built packet

```python
def send_raw_syn(src_ip, dst_ip, src_port, dst_port):
    payload = b""
    ip_header = build_ip_header_with_checksum(src_ip, dst_ip, payload_len=20, protocol=socket.IPPROTO_TCP)
    tcp_header = build_tcp_header(src_port, dst_port, flags="S")
    packet = ip_header + tcp_header + payload

    raw_sock = socket.socket(socket.AF_INET, socket.SOCK_RAW, socket.IPPROTO_TCP)
    raw_sock.setsockopt(socket.IPPROTO_IP, socket.IP_HDRINCL, 1)
    raw_sock.sendto(packet, (dst_ip, dst_port))
    raw_sock.close()
```

--> In practice, hand-rolling every header like this is exactly the tedious, error-prone, byte-fiddly work Scapy exists to eliminate -- this file's value is understanding what Scapy (or any packet-crafting tool in any language) is actually doing underneath its convenient object layer, not replacing Scapy for day-to-day use. When correctness matters and time is limited, prefer Scapy's `IP()/TCP()` layering from `04`; reach for raw `struct`-built packets only when you need something Scapy's abstractions don't expose, or need to avoid the dependency entirely.

## Cross-references

--> Builds on the `socket` fundamentals in `01 Networking Basics for Python Security Scripts.md` and directly parallels the layer-by-layer packet model in `04 Packet Crafting and Sniffing with Scapy.md` -- read that file first for the conceptual model (Ethernet/IP/TCP stacking) before this file's byte-level implementation of the same idea.
