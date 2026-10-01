# Deployment command reference

[简体中文](../zh-CN/DEPLOY.md) | [Back to README](../../README.en.md)

This page does one thing: **it gives you commands you can copy and run.**

No theory, no detours. Every block tells you which machine to run it on, what the command does, and what you should see afterwards.

Assumed starting point: a machine that already has a working **Paper 26.2** server, and you just want NekoCore in it. If Paper isn't installed yet, do steps 1 and 2 of [Quick Start](QUICKSTART.md) first.

---

## Read this bit first (30 seconds)

**Values you need to replace are wrapped in angle brackets:**

| Placeholder | Replace with | Example |
| --- | --- | --- |
| `<SERVER_IP>` | Your server's address | `192.0.2.10` (an example, not a real address) |
| `<USER>` | The SSH login user | `root` or `ubuntu` |
| `<SERVER_DIR>` | Absolute path to the server root | `/opt/minecraft` |
| `<LOCAL_DIR>` | Where the JAR sits on your PC | `D:\` |

**How to tell which machine you're on:**

- Prompt looks like `C:\Users\you>` or `PS D:\>` → **your own Windows PC**
- Prompt looks like `root@server:~#` or `ubuntu@vps:~$` → **the remote Linux server**

**These commands work out of the box on Windows.** `ssh`, `scp`, `ssh-keygen`, and `tar` all ship with the OS as part of OpenSSH — nothing extra to install. PowerShell and Command Prompt both work; the commands below run in either.

---

## 1. Remote Linux server (the main path)

The most common setup: the server lives on a VPS, you work from a Windows PC.

### Step 0: Confirm you have the JAR locally

> **Run on your local Windows PC**

```powershell
# Is the file there, and is the size right? (about 16.6 MiB)
Get-Item 'D:\NekoCore-1.0.0.jar' | Select-Object Name, Length, LastWriteTime
```

You should get one row with `Length` around `17428288`.

If it says the file doesn't exist, download or copy the JAR to `D:\` first, or change the path in the command to wherever you actually keep it.

**Worth verifying while you're here** (optional, but it catches a half-finished download):

```powershell
Get-FileHash 'D:\NekoCore-1.0.0.jar' -Algorithm SHA256
```

Compare against the SHA-256 on the release page. If they match, the file is intact.

### Step 1: Log in to the server

> **Run on your local Windows PC**

```powershell
ssh <USER>@<SERVER_IP>
```

A real example:

```powershell
ssh root@192.0.2.10
```

The first connection asks:

```text
The authenticity of host '192.0.2.10' can't be established.
ED25519 key fingerprint is SHA256:...
Are you sure you want to continue connecting (yes/no/[fingerprint])?
```

Type `yes` and press Enter. Then enter the password — **nothing appears on screen while you type it, which is normal.** Type it and press Enter.

Once the prompt becomes `root@xxx:~#`, you're in. **Every command marked "run on the server" goes in this window.**

### Step 2: Check the server environment and directories

> **Run on the server**

```bash
# Java must be version 25
java -version

# Find your server directory (common locations; pick whichever exists)
ls -d /opt/minecraft /srv/minecraft /root/minecraft ~/minecraft 2>/dev/null

# Confirm the plugins folder is there (swap in the path you just found)
ls -l <SERVER_DIR>/plugins
```

The first line of `java -version` must show `25`. If it doesn't, install Java 25 first — this page won't cover that.

`ls <SERVER_DIR>/plugins` should list the plugins you already have. **If it says `No such file or directory`,** either the path is wrong or Paper has never been started — start it once to generate the directory structure.

**If the server is running, stop it first** so you're not editing a live instance:

```bash
# Option 1: stop it from the console (preferred)
#   screen:  screen -r minecraft   then type stop
#   systemd: sudo systemctl stop minecraft

# Option 2: check the process, to confirm it really stopped
ps -ef | grep -i paper | grep -v grep
```

**This command must print nothing.** Any output means the process is still alive.

### Step 3: Upload the JAR

**Open a new local Windows window for this** (keep the SSH session open — you'll need it again shortly).

> **Run on your local Windows PC**

```powershell
scp "<LOCAL_DIR>\NekoCore-1.0.0.jar" <USER>@<SERVER_IP>:<SERVER_DIR>/plugins/
```

A real example:

```powershell
scp "D:\NekoCore-1.0.0.jar" root@192.0.2.10:/opt/minecraft/plugins/
```

**Breaking the command down:**

```text
scp      "local file path"      user@host:path/on/server
 │              │                    │            │
 │              │                    │            └─ trailing / means "put it in this directory"
 │              │                    └─ login details, then a colon
 │              └─ quote it if the path has spaces; quoting never hurts
 └─ secure copy — it rides the same channel as SSH
```

**Common failures:**

| Symptom | Cause | Fix |
| --- | --- | --- |
| `No such file or directory` | Local path is wrong, or the target directory doesn't exist | Verify locally with `Get-Item`; verify on the server with `ls` |
| `Permission denied` | The login user can't write to `plugins/` | Use `root`, or run `sudo chown -R <USER> <SERVER_DIR>` first |
| Hangs for a long time | Firewall blocking, or the IP is wrong | Confirm plain `ssh` connects — `scp` uses the same path |
| Errors on a non-ASCII path | The path's non-ASCII characters aren't being handled | Move the JAR somewhere ASCII-only (e.g. `D:\`) and retry |

**Verify the upload:**

> **Run on the server**

```bash
ls -lh <SERVER_DIR>/plugins/NekoCore-1.0.0.jar
```

File present and about `17M` is correct.

> **If the server is running, stop it before uploading.** Copying a JAR into `plugins/` while the server is reading that directory invites strange behaviour.

### Step 4: Start the server

> **Run on the server**

```bash
cd <SERVER_DIR>
```

Then start it whichever way you normally do. Three common cases:

```bash
# Case A: running it under screen (most common)
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui
screen -r minecraft          # attach to the console; Ctrl+A then D detaches without stopping it

# Case B: managed by systemd (starts again after a reboot)
sudo systemctl start minecraft
sudo journalctl -u minecraft -f      # live log; Ctrl+C to stop watching

# Case C: foreground (for debugging — closing the window stops the server)
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

**Adjust the memory flags to taste.** `-Xms2G -Xmx2G` is 2 GB — plenty for a friend server. Bump to `4G` if you add players or plugins, and make sure the machine itself has roughly double that available for the OS.

### Step 5: Confirm NekoCore actually loaded

> **Run on the server**

```bash
# Check the log for "ready"
grep -a "NekoCore" <SERVER_DIR>/logs/latest.log | tail -20

# Or, under screen, look at the console directly
screen -r minecraft
```

**The line you're looking for:**

```text
NekoCore 1.0.0 ready
```

There should also be something like `schema 5` just before it, from the migrations. **Seeing `ready` means you're done.**

Confirm the directory was created:

```bash
ls -lh <SERVER_DIR>/plugins/NekoCore/
```

You should see three files:

```text
config.yml       ← server behaviour
messages.yml     ← text players see
nekocore.db      ← data (don't hand-edit it)
```

**No `ready` line?** Jump to [troubleshooting](#4-troubleshooting).

### Step 6: Edit the config (two things minimum)

**Stop the server first:**

```bash
screen -r minecraft      # attach to the console
# type stop and press Enter, then wait for a full exit
```

**Then edit:**

```bash
cd <SERVER_DIR>/plugins/NekoCore
nano config.yml
```

`nano` is the least fuss. **Save and quit: `Ctrl+O` → Enter → `Ctrl+X`.**

Change these two:

```yaml
branding:
  server-name: "My Server"        # ← your server's name

survival:
  enabled: true
  world: world                    # ← your main world's actual folder name
```

**What is your main world called?** Check `server.properties`:

```bash
grep '^level-name' <SERVER_DIR>/server.properties
```

That name must match `survival.world` exactly, **including capitalisation**.

Once saved, confirm you didn't break anything:

```bash
# Check for tabs (YAML allows spaces only)
grep -Pn '\t' config.yml
```

**This must print nothing.** If it prints something, a line has a tab in it — retype that line with spaces.

### Step 7: Restart and verify

> **Run on the server**

```bash
cd <SERVER_DIR>
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui
screen -r minecraft
```

In game, try these in order:

```text
/menu
/checkin
/sethome
/home
/coins
```

Operators can also run:

```text
/nekocore status
/nekocore config check
```

**That's the install finished.** Everything after this point is for when you want to save effort or something went wrong.

### Optional: passwordless login

Typing a password for every `scp` gets old. Three commands fix it:

> **Run on your local Windows PC**

```powershell
# 1. Generate a key (press Enter at every prompt; no passphrase needed)
ssh-keygen -t ed25519

# 2. Install the public key on the server (asks for the password once)
type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh <USER>@<SERVER_IP> "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && chmod 700 ~/.ssh"

# 3. Test: this should not ask for a password
ssh <USER>@<SERVER_IP> "echo passwordless login works"
```

After this, neither `scp` nor `ssh` asks again. **The public key is shareable. The private key (`id_ed25519`, the one without `.pub`) is not — ever.**

---

## 2. Local Windows server

The server runs on your own Windows machine. No SSH, no `scp`.

### Quick version: four commands

> **Run in PowerShell**

```powershell
# Assumes the server is at D:\mc-server and the JAR is at D:\
Copy-Item 'D:\NekoCore-1.0.0.jar' 'D:\mc-server\plugins\' -Force
Set-Location 'D:\mc-server'
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

Wait for `NekoCore 1.0.0 ready` in the log, then type this into the console:

```text
stop
```

### Full walkthrough

```powershell
# --- 1. Prep: confirm the server directory and plugins folder exist ---
$server = 'D:\mc-server'                      # ← your server directory
Test-Path "$server\paper.jar"                 # should be True
Test-Path "$server\plugins"                   # should be True; if False, start Paper once first

# --- 2. Confirm the server isn't running (any output means it is) ---
Get-Process java -ErrorAction SilentlyContinue | Select-Object Id, ProcessName, StartTime

# --- 3. Copy the JAR in ---
Copy-Item 'D:\NekoCore-1.0.0.jar' "$server\plugins\" -Force

# --- 4. Confirm the copy ---
Get-Item "$server\plugins\NekoCore-1.0.0.jar" | Select-Object Name, Length

# --- 5. Start ---
Set-Location $server
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

**Once you see `NekoCore 1.0.0 ready`, type `stop` in the same window** and wait for a full exit.

### Editing the config

```powershell
# --- Option 1: Notepad (simple; remember to save as UTF-8) ---
notepad "$server\plugins\NekoCore\config.yml"

# --- Option 2: VS Code (recommended — it makes indentation problems visible) ---
code "$server\plugins\NekoCore\config.yml"
```

Change these two:

```yaml
branding:
  server-name: "Cat Cafe"

survival:
  world: world          # ← must match level-name in server.properties
```

Check your main world's name:

```powershell
Select-String -Path "$server\server.properties" -Pattern '^level-name'
```

Check for stray tabs:

```powershell
Select-String -Path "$server\plugins\NekoCore\config.yml" -Pattern "`t"
```

**No output means you're clean.**

Then just start the server again.

### The commands you'll actually reuse

```powershell
# Start
Set-Location 'D:\mc-server'; java -Xms2G -Xmx2G -jar paper.jar --nogui

# Read the recent log
Get-Content 'D:\mc-server\logs\latest.log' -Tail 50

# Back up (stop the server first!)
$ts = Get-Date -Format 'yyyyMMdd-HHmm'
Copy-Item 'D:\mc-server\plugins\NekoCore' "D:\backup\NekoCore-$ts" -Recurse -Force

# Confirm what's in the backup
Get-ChildItem "D:\backup\NekoCore-$ts" | Select-Object Name, Length
```

---

## 3. Panel hosts (Pterodactyl, MCSManager, and similar)

On a panel you click instead of typing. The "commands" here are what you paste into panel fields.

### The general flow

```text
1. Open File Manager in the panel
2. Navigate into /plugins
3. Click Upload and pick NekoCore-1.0.0.jar from your PC
4. Go back to the console and click Start (if it was running, click Stop first)
5. Watch the console for NekoCore 1.0.0 ready
6. Stop the server
7. In File Manager, open /plugins/NekoCore/ and edit config.yml
8. Change branding.server-name and survival.world, then save
9. Start the server and run /menu in game
```

### Things that bite people on panels

**Upload before start, not after.** Stop the server, upload, then start.

**Watch indentation in the panel's built-in editor.** Many render a tab exactly like spaces. If the server won't start after an edit, check indentation first.

**No `plugins` folder?** Paper has never started successfully. Start it once to generate the structure.

**Pterodactyl:**

```text
Files → /plugins → Upload → pick the JAR
Console → Start
```

**MCSManager:**

```text
File Manager → enter plugins → Upload
Terminal / Console → Start
```

**Only a console, no file manager?** Use whatever upload-to-root feature the panel offers, then move it from the console (some panels expose a `cmd` entry):

```bash
mv /NekoCore-1.0.0.jar /plugins/NekoCore-1.0.0.jar
```

---

## 4. Troubleshooting

Organised as symptom → cause → command. **Each entry starts with a diagnostic you can run immediately.**

### Can't connect to the server

```powershell
# Test the ports from your PC (swap 25565 for your port)
Test-NetConnection <SERVER_IP> -Port 22      # SSH
Test-NetConnection <SERVER_IP> -Port 25565   # game
```

`TcpTestSucceeded : True` means it's reachable. `False` means a firewall or security group is blocking it.

### Upload says Permission denied

> **Run on the server**

```bash
ls -ld <SERVER_DIR>/plugins
whoami
```

See who owns the `plugins` directory. If your user isn't the owner or in the group:

```bash
sudo chown -R $(whoami) <SERVER_DIR>/plugins
```

Then upload again from your PC.

### The transfer died halfway, or the size looks wrong

```bash
# On the server: actual size
ls -lh <SERVER_DIR>/plugins/NekoCore-1.0.0.jar
```

```powershell
# Locally: correct size and hash
Get-Item 'D:\NekoCore-1.0.0.jar' | Select-Object Length
Get-FileHash 'D:\NekoCore-1.0.0.jar' -Algorithm SHA256
```

Mismatched sizes mean an incomplete transfer. Delete the server-side file and retry:

```bash
rm <SERVER_DIR>/plugins/NekoCore-1.0.0.jar
```

### No "NekoCore ready" in the log

> **Run on the server**

```bash
# Look for ERROR or exceptions
grep -aE "ERROR|Exception|NekoCore" <SERVER_DIR>/logs/latest.log | tail -40
```

**Match what you see against this:**

| What the log says | What it means | What to do |
| --- | --- | --- |
| `Unsupported class file major version` | Java is too old | Check `java -version` reports 25 |
| No mention of `NekoCore` at all | The JAR isn't in the right place | `ls <SERVER_DIR>/plugins/ \| grep -i neko` |
| `Unknown/missing dependency` | An incompatible build is installed | Confirm it's the build for Paper 26.2 |
| A wall of `Caused by` | Read the first one only | The rest are usually knock-on effects |

### The server won't start, or exits immediately

```bash
# Run it in the foreground so errors print straight to the screen
cd <SERVER_DIR>
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

The error appears directly. **The two most common causes:** `eula.txt` still says `false`, or the port is already in use.

```bash
# Check the EULA
cat <SERVER_DIR>/eula.txt

# Check what's holding the port
ss -tlnp | grep 25565
```

### Config changes aren't taking effect

**First confirm the config is legal, then make it live — these are two different commands:**

```text
/nekocore config check     ← validates only, changes nothing
/nekocore reload           ← validates, then swaps it in
```

**If `config check` reports an error,** read the YAML path it names (something like `store.products.bred.material`) and fix that.

**If both succeed but nothing changed in game,** check whether your edit is one a reload can't cover: replacing the JAR, changing the database filename, installing a new plugin, or world-plugin changes. Those need a full restart.

**A broken config won't take your server down.** A failed reload keeps running on the last valid configuration and tells the console what was wrong.

### YAML errors, but you can't see where

```bash
# Find tabs (YAML allows spaces only)
grep -Pn '\t' <SERVER_DIR>/plugins/NekoCore/config.yml

# Show a field with surrounding context
grep -n -A5 -B2 'branding' <SERVER_DIR>/plugins/NekoCore/config.yml
```

**No output from the first command means it's clean.** If there is output, replace those tabs with spaces.

### Permissions or ownership are a mess

```bash
# See who owns what
ls -ld <SERVER_DIR> <SERVER_DIR>/plugins <SERVER_DIR>/plugins/NekoCore

# Fix it in one pass (set the user and group to whoever runs the server)
sudo chown -R minecraft:minecraft <SERVER_DIR>
```

If you run the server as `root`, `root` owning everything is fine.

### You want a backup but aren't sure you got every file

```bash
# Stop the server, then archive the whole directory in one command
# (.db, .db-wal, .db-shm, and the configs all come along)
cd <SERVER_DIR>/plugins
tar -czf ~/nekocore-backup-$(date +%Y%m%d).tar.gz NekoCore/

# Confirm what's inside
tar -tzf ~/nekocore-backup-$(date +%Y%m%d).tar.gz
```

**Archiving the entire `NekoCore/` directory** is what stops you missing `nekocore.db-wal` and `nekocore.db-shm`. Those two must come from the same point in time as the `.db`.

---

## 5. Command cheat sheet

Find the "I want to…" and copy. **Remember to replace `<SERVER_DIR>`.**

### Upload and download

```powershell
# Local → server (single file)
scp "D:\NekoCore-1.0.0.jar" root@<SERVER_IP>:/opt/minecraft/plugins/

# Local → server (whole folder; -r is recursive)
scp -r "D:\GeoLite2-City.mmdb" root@<SERVER_IP>:/opt/minecraft/plugins/NekoCore/

# Server → local (pull a backup down)
scp root@<SERVER_IP>:/root/nekocore-backup.tar.gz "D:\backup\"

# Piping through ssh gives you visible progress on large transfers
# (Windows' bundled scp has no progress bar)
ssh root@<SERVER_IP> "cd /opt/minecraft/plugins && tar -czf - NekoCore/" > "D:\backup\NekoCore.tar.gz"
```

### Service control

```bash
# screen
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui   # start detached
screen -ls                                                        # list sessions
screen -r minecraft                                               # attach to console
# type stop in the console to shut down; Ctrl+A then D detaches only

# systemd
sudo systemctl start minecraft
sudo systemctl stop minecraft
sudo systemctl restart minecraft
sudo systemctl status minecraft
sudo journalctl -u minecraft -f
```

### Reading logs

```bash
tail -f <SERVER_DIR>/logs/latest.log                    # follow live (Ctrl+C to stop)
tail -100 <SERVER_DIR>/logs/latest.log                  # last 100 lines
grep -a "NekoCore" <SERVER_DIR>/logs/latest.log         # NekoCore only
grep -aE "ERROR|WARN" <SERVER_DIR>/logs/latest.log      # errors and warnings only
```

### Editing config

```bash
cd <SERVER_DIR>/plugins/NekoCore
nano config.yml                    # Ctrl+O to save, Enter to confirm, Ctrl+X to quit
grep -Pn '\t' config.yml           # find tabs (should print nothing)
cp config.yml config.yml.bak       # back it up before editing; cheap insurance
```

### Backup and restore

```bash
# Back up (stop the server first)
cd <SERVER_DIR>/plugins
tar -czf ~/nekocore-$(date +%Y%m%d-%H%M).tar.gz NekoCore/

# Inspect a backup
tar -tzf ~/nekocore-*.tar.gz

# Restore (stop the server first)
cd <SERVER_DIR>/plugins
mv NekoCore NekoCore.old                              # move the current one aside; don't overwrite
tar -xzf ~/nekocore-20261001-1530.tar.gz              # extracts NekoCore/
ls -l NekoCore/                                       # confirm the files are there
```

### Checking the JAR is intact

```bash
# On the server
unzip -l <SERVER_DIR>/plugins/NekoCore-1.0.0.jar | head -20   # listing works = valid zip
sha256sum <SERVER_DIR>/plugins/NekoCore-1.0.0.jar             # compare with the release page
```

```powershell
# Locally
Get-FileHash 'D:\NekoCore-1.0.0.jar' -Algorithm SHA256
```

---

## 6. Where to go next

**Installed, and you want to change something**
→ [Recipes](RECIPES.md) — one-line goals like "I want to change the check-in reward."

**You want to understand each field**
→ [Configuration](CONFIGURATION.md).

**Something isn't responding**
→ [Compatibility and troubleshooting](COMPATIBILITY.md).

**Upgrading or rolling back**
→ [Installation](INSTALLATION.md).

**Backup and recovery**
→ [Database](DATABASE.md).
