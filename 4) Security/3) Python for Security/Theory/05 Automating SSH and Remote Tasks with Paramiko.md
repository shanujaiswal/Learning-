### Automating SSH and Remote Tasks with Paramiko

--> Paramiko is a pure-Python implementation of the SSH2 protocol. It lets scripts connect to remote machines, run commands, and transfer files without shelling out to the `ssh` binary.
--> Common security uses: post-exploitation automation across a list of authorized hosts, config auditing at scale, automated patch/compliance checks, and building simple remote-execution tooling.
--> Install with `pip install paramiko`.

## `SSHClient` basics

--> `SSHClient` is the high-level object most scripts use — it wraps transport setup, authentication, and channel management.

```python
import paramiko

client = paramiko.SSHClient()

# Auto-add unknown host keys. Convenient for lab/scripting use, but it disables
# protection against man-in-the-middle attacks — see the note on host keys below.
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())

client.connect(
    hostname="192.168.1.50",
    port=22,
    username="admin",
    password="labpassword123",
    timeout=10,
)

print("Connected")
client.close()
```

## Connecting with a password vs. a key

--> Password auth is simplest but weakest operationally (credentials in scripts/config). Key-based auth is standard practice for automation.

```python
import paramiko

# --- Password auth ---
client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(hostname="192.168.1.50", username="admin", password="labpassword123")
client.close()

# --- Key-based auth ---
client2 = paramiko.SSHClient()
client2.set_missing_host_key_policy(paramiko.AutoAddPolicy())

private_key = paramiko.RSAKey.from_private_key_file("/home/user/.ssh/id_rsa")
# Or for a passphrase-protected key:
# private_key = paramiko.RSAKey.from_private_key_file("/home/user/.ssh/id_rsa", password="keypass")

client2.connect(hostname="192.168.1.50", username="admin", pkey=private_key)
client2.close()
```

--> Paramiko also supports Ed25519 keys via `paramiko.Ed25519Key.from_private_key_file(...)`, which is the modern recommended key type over RSA for new deployments.

## Running remote commands

--> `exec_command()` runs a single command over a new channel and gives you back file-like objects for stdin/stdout/stderr.

```python
import paramiko

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(hostname="192.168.1.50", username="admin", password="labpassword123")

stdin, stdout, stderr = client.exec_command("uname -a && whoami")

output = stdout.read().decode()
errors = stderr.read().decode()
exit_status = stdout.channel.recv_exit_status()   # blocks until command finishes

print("STDOUT:", output)
print("STDERR:", errors)
print("Exit status:", exit_status)   # 0 usually means success

client.close()
```

--> Always read `exit_status` if the command's success/failure matters to your script's logic — a non-zero exit code means the remote command failed even if `stdout` looks non-empty.
--> Each call to `exec_command()` opens a fresh shell-less channel — environment state (like `cd` in one call) does **not** persist to the next call. Chain commands with `&&` or `;` inside a single call if they depend on each other.

## SFTP file transfer

--> Paramiko can open an SFTP subsystem on the same SSH connection for file upload/download, without needing a separate FTP server.

```python
import paramiko

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(hostname="192.168.1.50", username="admin", password="labpassword123")

sftp = client.open_sftp()

# Upload a local file to the remote host
sftp.put("local_report.txt", "/tmp/remote_report.txt")

# Download a remote file to local disk
sftp.get("/etc/hostname", "downloaded_hostname.txt")

# List remote directory contents
for filename in sftp.listdir("/tmp"):
    print(filename)

sftp.close()
client.close()
```

## Common exceptions

--> Real automation scripts (especially ones running against a list of hosts) need to handle SSH-specific failures gracefully rather than crashing on the first unreachable host.

1. `paramiko.AuthenticationException` – wrong username/password/key, or the account has no valid auth method matching what you tried.
2. `paramiko.SSHException` – general SSH protocol errors (banner issues, bad host key, channel failures).
3. `paramiko.ssh_exception.NoValidConnectionsError` – could not open a socket to any resolved address for the host (host down, port closed, firewall).
4. `socket.timeout` – the connection attempt exceeded the `timeout` you passed to `connect()`.

```python
import paramiko
import socket

def try_connect(host, username, password, timeout=8):
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        client.connect(hostname=host, username=username, password=password, timeout=timeout)
        return client, None
    except paramiko.AuthenticationException:
        return None, "authentication failed"
    except paramiko.SSHException as e:
        return None, f"SSH protocol error: {e}"
    except socket.timeout:
        return None, "connection timed out"
    except OSError as e:
        return None, f"connection error: {e}"

client, error = try_connect("192.168.1.50", "admin", "wrongpassword")
if error:
    print(f"[-] Failed: {error}")   # [-] Failed: authentication failed
else:
    print("[+] Connected")
    client.close()
```

## A note on host key verification

--> `paramiko.AutoAddPolicy()` silently trusts and stores whatever host key the server offers on first connect — convenient for scripting against known lab hosts, but it means a man-in-the-middle on first connection would go unnoticed.
--> For anything beyond throwaway lab automation, load and verify known host keys explicitly:

```python
import paramiko

client = paramiko.SSHClient()
client.load_system_host_keys()          # trust keys already in ~/.ssh/known_hosts
client.set_missing_host_key_policy(paramiko.RejectPolicy())  # reject anything unknown
client.connect(hostname="192.168.1.50", username="admin", password="labpassword123")
```

## Worked example: running a command across a list of hosts

--> A common authorized-use case: quickly check a config value, patch version, or run a compliance command across many machines you administer.

```python
import paramiko
import socket

HOSTS = ["192.168.1.50", "192.168.1.51", "192.168.1.52"]
USERNAME = "admin"
PASSWORD = "labpassword123"
COMMAND = "cat /etc/os-release | grep VERSION="

def run_on_host(host, username, password, command, timeout=8):
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        client.connect(hostname=host, username=username, password=password, timeout=timeout)
        stdin, stdout, stderr = client.exec_command(command)
        output = stdout.read().decode().strip()
        exit_status = stdout.channel.recv_exit_status()
        return {"host": host, "ok": exit_status == 0, "output": output, "error": None}
    except paramiko.AuthenticationException:
        return {"host": host, "ok": False, "output": None, "error": "auth failed"}
    except (paramiko.SSHException, socket.timeout, OSError) as e:
        return {"host": host, "ok": False, "output": None, "error": str(e)}
    finally:
        client.close()

def run_on_fleet(hosts, username, password, command):
    results = []
    for host in hosts:
        result = run_on_host(host, username, password, command)
        status = "OK" if result["ok"] else f"FAILED ({result['error']})"
        print(f"[{host}] {status}")
        if result["ok"]:
            print(f"    {result['output']}")
        results.append(result)
    return results

if __name__ == "__main__":
    run_on_fleet(HOSTS, USERNAME, PASSWORD, COMMAND)
    # [192.168.1.50] OK
    #     VERSION="22.04.3 LTS (Jammy Jellyfish)"
    # [192.168.1.51] OK
    #     VERSION="22.04.3 LTS (Jammy Jellyfish)"
    # [192.168.1.52] FAILED (auth failed)
```

--> This pattern — connect, try/except by exception type, always close in `finally`, collect a results list — scales cleanly to hundreds of hosts and is the backbone of most "run this across the fleet" security tooling. For real fleets, swap the hardcoded password for key-based auth and pull the host list from inventory rather than a literal list.
