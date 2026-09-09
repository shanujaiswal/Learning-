==> cd --> change directory --> to go inside a folder
==> cd.. --> change directory --> to go outside a folder
--> mkdir -- make new directory
--> mkdir shanu -- make new directory folder name shanu

tab button --> autocomplete the path during cd
==> pwd --> Displays the current working directory of the terminal
==> clear --> this command is used to clear the terminal screen contents will not be deleted but scrolled down
==> / --> Root directory
==> echo --> command that writes its argument to standard output
==> su --> Used to switch to root user (so that super user permissions can be used to execute commands)
==> su username --> Used to switch to a different user
==> sudo --> execute only that comm with roots/ super user privileges
==> ls --> lists directory contents on Linux/macOS/Git Bash/WSL ---> the Windows cmd.exe equivalent is dir
--> To see a list of all non-hidden files in the current directory.
--> ls -a --> to see a list of all files, including hidden files
==> ls -t --> to see a list of files in order of when they were last modified.
==> ls -r --> to see a list of files in the reverse order.
==> ls -C or ls -x --> to see a list of files in multiple columns.
==> ls -m --> to see a list of files in a comma-separated series.
==> ls -l --> to see a list of files with permissions, owner, and last modified date
==> ls -lh --> to see a list of files with file sizes displayed in a human readable format, such as M for MB, K for KB, and G for GB.
==> ls path -->
==>
\*\*\* The ls command is similar to the DOS DIR command.

# File and Directory Operations

--> touch filename.txt --> Creates a new empty file (or updates its timestamp if it already exists).
--> cp source.txt destination.txt --> Copies a file.
--> cp -r sourceFolder destFolder --> Copies a folder recursively (needed for directories, since plain cp only works on files).
--> mv oldname.txt newname.txt --> Renames a file (mv is also used for moving, since Linux treats renaming as "moving to a new name in the same place").
--> mv file.txt /path/to/folder/ --> Moves a file into a folder.
--> rm filename.txt --> Deletes a file permanently (no Recycle Bin/Trash by default).
--> rm -r foldername --> Deletes a folder and everything inside it recursively.
--> rm -rf foldername --> Force-deletes without confirmation prompts -- extremely destructive, no undo, use with caution.
--> rmdir foldername --> Deletes a folder, but ONLY if it's empty (safer than rm -r for that reason).
--> ln -s target linkname --> Creates a symbolic link (shortcut) named linkname that points to target.

# Viewing File Contents

--> cat filename.txt --> Prints the entire file content to the terminal at once.
--> cat file1.txt file2.txt --> Concatenates and prints multiple files together.
--> less filename.txt --> Opens the file for scrollable viewing one screen at a time (better for large files); press q to quit, / to search.
--> more filename.txt --> Older, simpler alternative to less (only scrolls forward).
--> head filename.txt --> Shows the first 10 lines of a file by default.
--> head -n 20 filename.txt --> Shows the first 20 lines.
--> tail filename.txt --> Shows the last 10 lines of a file by default.
--> tail -f filename.txt --> "Follows" the file, printing new lines live as they're appended -- commonly used to watch a live log file.
--> nano filename.txt --> Opens a simple, beginner-friendly terminal text editor (Ctrl+O to save, Ctrl+X to exit).
--> vim filename.txt --> Opens the more powerful (but steeper learning curve) Vim editor -- press i to start typing (insert mode), Esc then :wq to save and quit, Esc then :q! to quit without saving.

# File Permissions

--> Every file/folder has permissions for three groups: Owner (u), Group (g), and Others (o) -- each can have Read (r), Write (w), and Execute (x) permission.
--> ls -l --> Shows permissions in a string like `-rwxr-xr--` (first character is the file type, then 3 groups of rwx for owner/group/others).
--> chmod +x script.sh --> Adds execute permission (commonly needed to run a downloaded script directly, e.g. ./script.sh).
--> chmod 755 filename --> Sets permissions using numeric (octal) notation: read=4, write=2, execute=1, summed per group. 755 = owner rwx (7), group r-x (5), others r-x (5).
--> chmod 644 filename --> Common default for regular files: owner rw- (6), group r-- (4), others r-- (4).
--> chown username filename --> Changes the owner of a file.
--> chown username:groupname filename --> Changes both owner and group.
--> sudo chmod/chown --> Usually needed when changing permissions/ownership on files you don't own.

# Searching for Files and Text

--> find /path -name "filename.txt" --> Searches for a file by name starting from the given path.
--> find . -name "*.js" --> Searches the current directory (and subfolders) for all files matching a wildcard pattern.
--> find . -type d -name "node_modules" --> Searches specifically for directories matching a name.
--> grep "searchtext" filename.txt --> Searches for a text pattern INSIDE a file, printing matching lines.
--> grep -r "searchtext" . --> Searches recursively through all files in the current directory and subfolders.
--> grep -i "searchtext" filename.txt --> Case-insensitive search.
--> grep -n "searchtext" filename.txt --> Shows the line number of each match.
--> which commandname --> Shows the full path of the executable that would run for a given command (useful for checking if/where a tool is installed).

# Piping and Redirection

--> | (pipe) --> Sends the OUTPUT of one command as the INPUT of the next command, chaining commands together. Example: `cat file.txt | grep "error"` searches for "error" only within file.txt's content.
--> > --> Redirects output to a file, OVERWRITING it if it already exists. Example: `echo "Hello" > file.txt`.
--> >> --> Redirects output to a file, APPENDING to the end instead of overwriting.
--> < --> Redirects a file's content as input to a command instead of typing it manually.
--> 2> --> Redirects only error output (stderr) to a file, separate from normal output (stdout).

# Process Management

--> ps --> Lists currently running processes for the current terminal session.
--> ps aux --> Lists ALL running processes on the system, with detailed info (user, CPU%, memory%, etc.).
--> top --> Shows a live, auto-updating view of running processes and system resource usage (press q to quit).
--> kill PID --> Sends a termination signal to a process by its Process ID (found via ps/top).
--> kill -9 PID --> Force-kills a process that won't respond to a normal kill signal.
--> command & --> Runs a command in the background, freeing up the terminal to keep being used.
--> jobs --> Lists background jobs running in the current terminal session.
--> fg --> Brings the most recent background job back to the foreground.
--> Ctrl+Z --> Pauses (suspends) the currently running foreground process without killing it -- resume it later with `bg` (background) or `fg` (foreground).

# Environment Variables and PATH

--> echo $PATH --> Prints the PATH variable -- a list of directories the shell searches through (in order) whenever you type a command name, to find the matching executable.
--> export VAR_NAME="value" --> Sets an environment variable for the current terminal session (and any programs launched from it).
--> env --> Lists all currently set environment variables.
--> Adding a line like `export PATH="$PATH:/new/directory"` to a shell config file (`.bashrc`/`.zshrc`) permanently adds a new directory to PATH for every future terminal session.

# Wildcards (Globbing)

--> `*` --> Matches any number of characters (including zero). Example: `*.txt` matches every file ending in .txt.
--> `?` --> Matches exactly one character. Example: `file?.txt` matches file1.txt, fileA.txt, but not file10.txt.
--> `[abc]` --> Matches any ONE character from the set inside the brackets. Example: `file[123].txt` matches file1.txt, file2.txt, file3.txt.

# Compression and Archives

--> tar -czvf archive.tar.gz foldername --> Creates a compressed archive (c=create, z=gzip compression, v=verbose, f=filename).
--> tar -xzvf archive.tar.gz --> Extracts a .tar.gz archive (x=extract).
--> zip -r archive.zip foldername --> Creates a .zip archive of a folder.
--> unzip archive.zip --> Extracts a .zip archive.

# Networking Basics

--> ping google.com --> Sends test packets to a host to check if it's reachable and measure response time.
--> curl https://example.com --> Fetches a URL's content directly in the terminal -- commonly used to quickly test an API endpoint.
--> curl -X POST -d "key=value" https://example.com/api --> Sends a POST request with data from the terminal.
--> wget https://example.com/file.zip --> Downloads a file from a URL directly to the current directory.
--> ssh username@hostname --> Opens a secure remote terminal session on another machine/server.

# Getting Help

--> man commandname --> Opens the manual page (full documentation) for a command; press q to quit.
--> commandname --help --> Shows a quick summary of a command's options (usually faster to check than the full man page).

# Useful Keyboard Shortcuts

--> Ctrl+C --> Interrupts/kills the currently running foreground command.
--> Ctrl+D --> Signals "end of input" -- often used to exit a shell or a program reading from stdin.
--> Ctrl+L --> Clears the screen (same effect as typing `clear`).
--> Ctrl+R --> Searches backward through command history interactively -- start typing to find a previous command, press Enter to run it.
--> Ctrl+A / Ctrl+E --> Jump the cursor to the beginning / end of the current line being typed.
--> Up/Down arrow keys --> Cycle through previously run commands (command history).
--> !! --> Re-runs the last command (e.g. `sudo !!` re-runs the last command with sudo, useful after forgetting it the first time).

# Deep Dive -- xargs -- Turning Output Into Arguments

--> Many commands (like `find` and `grep -l`) produce a LIST of items as output -- `xargs` takes that piped list and uses it to build/run a NEW command, once per item (or in batches) -- something a plain pipe alone can't do, since a normal pipe feeds another command's STDIN, not its argument list.

```bash
find . -name "*.log" | xargs rm              # Deletes every .log file found -- find's output becomes rm's arguments
find . -name "*.tmp" | xargs -I {} mv {} /tmp/    # -I {} lets you reference each item explicitly, for more complex commands
grep -l "TODO" -r . | xargs code               # Opens every file containing "TODO" directly in an editor
```

--> Without `xargs`, `find . -name "*.log" | rm` wouldn't work as expected -- `rm` doesn't read filenames from STDIN at all, it expects them as command-line ARGUMENTS, which is exactly the gap `xargs` bridges.

# Deep Dive -- Disk Usage -- df and du

--> `df` (disk free) -- shows overall disk space usage per MOUNTED FILESYSTEM/partition -- "how full is this entire drive."
--> `du` (disk usage) -- shows how much space specific FILES/DIRECTORIES are consuming -- "what's actually taking up all this space."

```bash
df -h                          # Human-readable overall disk space per filesystem (size, used, available, % used)
du -sh /var/log                 # Total size of a specific directory (summarized, human-readable)
du -h --max-depth=1 /home        # Size of each immediate subdirectory, one level deep -- useful for finding what's eating space
du -sh * | sort -rh | head -10    # A classic combo -- find the 10 largest items in the current directory
```

--> The `du -sh * | sort -rh | head -10` pattern above is a genuinely common, practical troubleshooting command for "my disk is full, what's taking up all the space" -- directly demonstrating the pipe-chaining philosophy referenced throughout this file (`du` lists sizes, `sort` orders them, `head` limits the output), each tool doing one job well.

# Deep Dive -- systemd and Service Management

--> Most modern Linux distributions use `systemd` to manage background services (web servers, databases, the SSH daemon) -- directly connecting to the Boot Process file in the Security Operating Systems track, where `systemd` is the very first user-space process (PID 1) started by the kernel.

```bash
sudo systemctl status nginx      # Check whether a service is running
sudo systemctl start nginx        # Start a service
sudo systemctl stop nginx          # Stop a service
sudo systemctl restart nginx        # Restart a service (stop then start)
sudo systemctl enable nginx          # Configure a service to auto-start on every future boot
journalctl -u nginx -f                # Follow a specific service's logs live, similar to `tail -f` but for systemd-managed services
```

--> This is precisely how a Node.js application (covered in the Node.js Fundamentals file) or any long-running server process is typically kept running reliably on a real Linux production server -- registered as a `systemd` service so it automatically restarts on crash and starts on server reboot, an alternative/complement to the PM2-based process management covered in the Production Deployment file.
