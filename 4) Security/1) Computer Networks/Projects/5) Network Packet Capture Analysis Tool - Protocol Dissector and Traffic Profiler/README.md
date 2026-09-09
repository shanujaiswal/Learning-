# Network Packet Capture Analysis Tool -- Protocol Dissector and Traffic Profiler

## Real-World Scenario

A network analyst is handed a batch of already-captured packets -- the kind
of raw material Wireshark or `tcpdump` would normally hand you after
sniffing a live interface or opening a `.pcap` file. Instead of individual
packets, the analyst wants three things a security review always needs:

1. **Reconstructed conversations.** Individual packets on their own are
   nearly meaningless -- what matters is the full TCP stream: did the
   3-way handshake (`SYN` -> `SYN,ACK` -> `ACK`) actually happen before
   data flowed, and did the connection close cleanly (`FIN,ACK`)? This is
   exactly what Wireshark's **Follow TCP Stream** feature does.
2. **A traffic profile.** Which protocols dominate the capture, which
   hosts sent the most bytes (top talkers), and which destination ports
   saw the most traffic? This is Wireshark's **Statistics > Conversations**
   / **Protocol Hierarchy** view.
3. **Anomaly flags.** Two patterns matter most in practice:
   - A TCP "stream" that has data packets but **no valid handshake** --
     a classic signature of a port scan or a spoofed source firing stray
     segments at a host it never properly connected to.
   - A single flow that transferred **far more data** than everything
     else in the capture -- could be an innocuous large download, but is
     exactly the shape of traffic a data-exfiltration attempt produces,
     so it deserves a manual look.

This project builds that whole workflow **entirely offline**: no `.pcap`
file, no `scapy` live capture, no root/Administrator privileges, no Npcap
install. All "captured" packets are synthetic Python records (timestamp,
src/dst IP+port, protocol, TCP flags, payload size) generated with a fixed
random seed, so the tool runs identically on any machine.

## Architecture

| Module | Role | Real-world equivalent |
|---|---|---|
| `packet_capture_generator.py` | Builds a synthetic batch of packet records: several complete legitimate TCP conversations, a small UDP exchange, one handshake-less "stream", and one abnormally large TCP transfer. Fixed seed for reproducibility. | The `.pcap` file Wireshark opens, or what `scapy.sniff()` / `tcpdump` would hand you after a live capture. |
| `stream_reassembler.py` | Groups packets by unordered 4-tuple (src/dst IP+port pair, both directions merged), reconstructs each stream's ordered packet and flag sequence, and validates the handshake (`SYN` -> `SYN,ACK` -> `ACK`) and teardown (`FIN,ACK`). | Wireshark's **Follow -> TCP Stream**, and the stream-tracking table behind it. |
| `traffic_profiler.py` | Computes protocol distribution, top talkers by bytes sent, top destination ports, and total conversation count. | Wireshark's **Statistics > Conversations** and **Statistics > Protocol Hierarchy**. |
| `anomaly_flagger.py` | Flags (1) TCP streams with a missing/invalid handshake, and (2) flows whose total bytes exceed `mean + N*std` across all streams in the batch (`numpy`-based statistical threshold, N=2 by default). | The manual analyst workflow of eyeballing a stream with no visible handshake, or sorting Conversations by Bytes descending to spot an outlier -- the same principle SIEM/IDS traffic analysis automates. |
| `main.py` | Runs the full pipeline: generate -> reassemble -> profile -> flag anomalies. Prints a Conversations-style table and the anomaly findings, and saves `traffic_profile.png`. | Opening a capture in Wireshark, checking Conversations/Protocol Hierarchy, and reviewing streams flagged by an analyst or IDS. |

## Run It

Requires Python 3 with `numpy` and `matplotlib` installed (both already
present in this environment; nothing else is needed -- no `scapy`, no
Npcap/libpcap, no admin rights).

```bash
python main.py
```

Each module also runs standalone for quick inspection, e.g.:

```bash
python packet_capture_generator.py   # preview the raw synthetic packet batch
python stream_reassembler.py         # preview reassembled streams only
python traffic_profiler.py           # preview traffic profile only
python anomaly_flagger.py            # preview anomaly findings only
```

## Verified Result (actual output of `python main.py`)

```
Generating synthetic packet capture batch (fixed seed)...

Reassembling TCP streams from packets (4-tuple grouping)...

====================================================================================================
CONVERSATIONS
====================================================================================================
Protocol Endpoint A               Endpoint B               Packets    Bytes Duration(s)  Handshake
----------------------------------------------------------------------------------------------
TCP      10.0.0.11:51000          93.184.216.34:443             11     2967      0.1663         OK
TCP      10.0.0.12:51050          93.184.216.34:443              9     3480      0.1638         OK
TCP      10.0.0.13:51100          192.168.1.50:22               13     5641      0.2812         OK
TCP      10.0.0.11:51200          192.168.1.10:80                8     2135      0.1315         OK
UDP      10.0.0.14:53650          8.8.8.8:53                     1       70      0.0000        N/A
UDP      10.0.0.14:53250          8.8.8.8:53                     1      100      0.0000        N/A
UDP      10.0.0.14:53276          8.8.8.8:53                     1      119      0.0000        N/A
UDP      10.0.0.14:53570          8.8.8.8:53                     1      108      0.0000        N/A
UDP      10.0.0.14:53863          8.8.8.8:53                     1      109      0.0000        N/A
UDP      10.0.0.14:53234          8.8.8.8:53                     1      185      0.0000        N/A
TCP      10.0.0.20:8080           203.0.113.66:40444             5      311      0.0193    MISSING
TCP      10.0.0.15:52500          198.51.100.9:443             405   572028     11.2772         OK

====================================================================================================
TRAFFIC PROFILE
====================================================================================================
Total packets captured:  457
Total bytes captured:    587253
Total conversations:     12

Protocol distribution:
  TCP     451 packets  ( 98.7%)
  UDP       6 packets  (  1.3%)

Top talkers (by bytes sent):
  198.51.100.9         286093 bytes
  10.0.0.15            285935 bytes
  192.168.1.50           2985 bytes
  10.0.0.11              2859 bytes
  10.0.0.13              2656 bytes

Top destination ports:
  port 443     214 packets
  port 52500   202 packets
  port 22        7 packets
  port 51100     6 packets
  port 51000     5 packets

====================================================================================================
ANOMALY FINDINGS
====================================================================================================
Volume threshold used: mean (48937.8) + 2.0 * std (157727.3) = 364392.3 bytes

[!] Handshake anomalies -- streams with no valid SYN->SYN/ACK->ACK (1 found):
    10.0.0.20:8080 <-> 203.0.113.66:40444  packets=5  flags_seen=['PSH,ACK', 'PSH,ACK', 'PSH,ACK', 'PSH,ACK', 'PSH,ACK']
    -> Looks like a scan/spoofed source: data packets arrived with no completed 3-way handshake.

[!] Volume anomalies -- flows exceeding the statistical threshold (1 found):
    10.0.0.15:52500 <-> 198.51.100.9:443  total_bytes=572028  packets=405
    -> Unusually large single-flow transfer: could be a big legitimate download, or exfiltration -- worth reviewing.

Saved traffic profile chart to: traffic_profile.png
```

Both scenario anomalies are correctly detected:
- The **handshake-less stream** (`10.0.0.20:8080 <-> 203.0.113.66:40444`) is
  flagged -- 5 bare `PSH,ACK` packets, no `SYN` anywhere in the flag
  sequence.
- The **oversized transfer** (`10.0.0.15:52500 <-> 198.51.100.9:443`,
  ~572 KB / 405 packets, otherwise a completely normal handshake +
  teardown) is flagged as the only flow above the `mean + 2*std` byte
  threshold.

`traffic_profile.png` is also generated: a two-panel chart with the
protocol distribution (TCP vs UDP packet counts) on the left and the top
talkers by bytes sent on the right.

## Things to Try Changing

- **Tighten/loosen the volume threshold** -- pass a different `n_std` to
  `build_anomaly_report()` in `main.py` (e.g. `n_std=1.0` flags more
  flows as anomalous, `n_std=3.0` flags fewer).
- **Add a second handshake-less stream** or a half-open scan (`SYN` sent,
  no reply, no data) in `packet_capture_generator.py` and confirm it's
  also caught.
- **Change the handshake validation rule** in `stream_reassembler.py` to
  also require the teardown (`_has_teardown`) before calling a stream
  fully "OK", and see how that reclassifies streams that never see a
  `FIN,ACK` (e.g. truncated captures).
- **Add a new protocol** (e.g. ICMP) to the generator and extend
  `traffic_profiler.py`'s distribution/top-talkers logic to confirm it
  slots in without special-casing.
- **Randomize the seed** (`generate_packet_batch(seed=...)`) to see the
  pipeline behave correctly across different synthetic batches, not just
  the one baked into this README.
