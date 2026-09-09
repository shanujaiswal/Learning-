"""
04_ssh_automation_demo.py

AUTHORIZED USE ONLY. This script connects only to "localhost" by design, and only works if YOU
have deliberately enabled a local SSH server for this exercise. Never point this at a remote host,
and never hardcode real production credentials in a script like this.

Setup note (do this before running):
  - Windows 11: Settings -> Apps -> Optional Features -> add "OpenSSH Server", then
    `Start-Service sshd` in an elevated PowerShell (and optionally `Set-Service -Name sshd
    -StartupType Automatic`). Make sure a Windows account with a password exists to log in with.
  - Linux/macOS: install/enable `openssh-server` (e.g. `sudo systemctl enable --now ssh`).
  - Update SSH_USERNAME / SSH_PASSWORD below to match a real local account, or better, set up an
    SSH key pair and use key-based auth instead of a password (see the commented alternative).

Integrates Theory Ch.5 (Automating SSH with Paramiko):
  - Connects to a local SSH server.
  - Runs a single benign command.
  - Handles connection failures (server not running, auth failure, timeout) with clear messages
    instead of letting the script crash with a raw traceback.
"""

import sys

import paramiko

SSH_HOST = "localhost"
SSH_PORT = 22
SSH_USERNAME = "your-local-username"   # EDIT ME
SSH_PASSWORD = "your-local-password"   # EDIT ME — consider key-based auth instead, see below
CONNECT_TIMEOUT_SECONDS = 5
COMMAND_TO_RUN = "whoami"  # a harmless, read-only command


def run_remote_command(host: str, port: int, username: str, password: str, command: str) -> None:
    client = paramiko.SSHClient()
    # AutoAddPolicy is convenient for a localhost lab demo; for anything beyond a personal
    # sandbox you should instead load known_hosts and verify the host key explicitly.
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())

    try:
        client.connect(
            hostname=host,
            port=port,
            username=username,
            password=password,
            timeout=CONNECT_TIMEOUT_SECONDS,
        )

        # --- Alternative: key-based auth instead of password ---
        # client.connect(
        #     hostname=host, port=port, username=username,
        #     key_filename=r"C:\Users\<you>\.ssh\id_ed25519",
        #     timeout=CONNECT_TIMEOUT_SECONDS,
        # )

        print(f"[+] Connected to {username}@{host}:{port}")

        stdin, stdout, stderr = client.exec_command(command)
        exit_status = stdout.channel.recv_exit_status()

        out = stdout.read().decode("utf-8", errors="replace").strip()
        err = stderr.read().decode("utf-8", errors="replace").strip()

        print(f"[+] Ran command: {command!r} (exit status {exit_status})")
        if out:
            print("    stdout:", out)
        if err:
            print("    stderr:", err)

    except paramiko.AuthenticationException:
        print(
            "[!] Authentication failed. Check SSH_USERNAME/SSH_PASSWORD, or confirm the account "
            "allows password authentication (or switch to key-based auth above).",
            file=sys.stderr,
        )
    except (paramiko.SSHException, TimeoutError, ConnectionRefusedError, OSError) as exc:
        print(
            f"[!] Could not connect to {host}:{port} — is the local SSH server running? "
            f"Details: {exc}",
            file=sys.stderr,
        )
    finally:
        client.close()


def main() -> None:
    print(f"=== SSH automation demo: {SSH_USERNAME}@{SSH_HOST}:{SSH_PORT} ===")
    run_remote_command(SSH_HOST, SSH_PORT, SSH_USERNAME, SSH_PASSWORD, COMMAND_TO_RUN)


if __name__ == "__main__":
    main()
