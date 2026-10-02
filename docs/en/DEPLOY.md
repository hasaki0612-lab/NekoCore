# Deployment command reference

[简体中文](../zh-CN/DEPLOY.md) | [Back to README](../../README.en.md)

This page does one thing: **it gives you commands you can copy and run.**

No theory, no detours. Every block tells you which machine to run it on, what the command does, and what you should see afterwards.

> **First time installing NekoCore? Read Quick Start instead — this page is the command lookup you come back to afterwards.**

Assumed starting point: a machine that already has a working **Paper 26.2** server, and you just want NekoCore in it. If Paper isn't installed yet, see the "Don't have Java and Paper yet?" block in [Quick Start](QUICKSTART.md).

**Values you need to replace are wrapped in angle brackets:**

| Placeholder | Replace with | Example |
| --- | --- | --- |
| `<SERVER_IP>` | Your server's address | `192.0.2.10` (an example, not a real address) |
| `<USER>` | The SSH login user | `root` or `ubuntu` |
| `<SERVER_DIR>` | Absolute path to the server root | `/opt/minecraft` |
| `<LOCAL_DIR>` | Where the JAR sits on your PC | `D:\` |

**How to tell which machine you're on:**

| Prompt | Where you are |
| --- | --- |
| `PS D:\>` or `C:\Users\you>` | **Your own Windows PC** |
| `root@server:~#` or `ubuntu@vps:~$` | **The remote Linux server** |

**These commands work out of the box on Windows.** `ssh`, `scp`, `ssh-keygen`, and `tar` all ship with the OS as part of OpenSSH — nothing extra to install. PowerShell and Command Prompt both work; the commands below run in either.

**Server not on a remote Linux box?**

| Your setup | Where to look |
| --- | --- |
| The server runs on your own Windows PC | [Quick Start](QUICKSTART.md), collapsed block "Local Windows server" |
| A panel host (Pterodactyl, MCSManager) | [Quick Start](QUICKSTART.md), collapsed block "Panel host (Pterodactyl / MCSManager, etc.)" |

---

## 1. Upload and download

> **Run on your local Windows PC**

```powershell
# Local → server (single file)
scp "<LOCAL_DIR>\NekoCore-1.2.0.jar" root@<SERVER_IP>:<SERVER_DIR>/plugins/

# Local → server (whole folder; -r is recursive)
scp -r "<LOCAL_DIR>\GeoLite2-City.mmdb" root@<SERVER_IP>:<SERVER_DIR>/plugins/NekoCore/

# Server → local (pull a backup down)
scp root@<SERVER_IP>:/root/nekocore-backup.tar.gz "D:\backup\"

# Piping through ssh gives you visible progress on large transfers
# (Windows' bundled scp has no progress bar)
ssh root@<SERVER_IP> "cd /opt/minecraft/plugins && tar -czf - NekoCore/" > "D:\backup\NekoCore.tar.gz"
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
ls -lh <SERVER_DIR>/plugins/NekoCore-1.2.0.jar
```

File present and about `17M` is correct.

> **If the server is running, stop it before uploading.** Copying a JAR into `plugins/` while the server is reading that directory invites strange behaviour.

**On a panel host with only a console and no file manager?** Upload the JAR to the panel's root, then move it from the console:

```bash
mv /NekoCore-1.2.0.jar /plugins/NekoCore-1.2.0.jar
```

The click-by-click version of that flow is in the "Panel host" block in [Quick Start](QUICKSTART.md).

---

## 2. Start and stop

> **Run on the server**

```bash
# --- screen (most common) ---
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui   # start detached
screen -ls                                                        # list sessions
screen -r minecraft                                               # attach to the console
# type stop in the console to shut down; Ctrl+A then D detaches only

# --- systemd (starts again after a reboot) ---
sudo systemctl start minecraft
sudo systemctl stop minecraft
sudo systemctl restart minecraft
sudo systemctl status minecraft
sudo journalctl -u minecraft -f

# --- foreground (for debugging — closing the window stops the server) ---
cd <SERVER_DIR>
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

**Adjust the memory flags to taste.** `-Xms2G -Xmx2G` is 2 GB — plenty for a friend server. Bump to `4G` if you add players or plugins, and make sure the machine itself has roughly double that available for the OS.

**Confirm the server really stopped:**

```bash
ps -ef | grep -i paper | grep -v grep
```

**This command must print nothing.** Any output means the process is still alive.

---

## 3. Logs

> **Run on the server**

```bash
tail -f <SERVER_DIR>/logs/latest.log                    # follow live (Ctrl+C to stop)
tail -100 <SERVER_DIR>/logs/latest.log                  # last 100 lines
grep -a "NekoCore" <SERVER_DIR>/logs/latest.log         # NekoCore only
grep -aE "ERROR|WARN" <SERVER_DIR>/logs/latest.log      # errors and warnings only
```

```powershell
# On your own Windows PC
Get-Content 'D:\mc-server\logs\latest.log' -Tail 50
Select-String -Path 'D:\mc-server\logs\latest.log' -Pattern 'NekoCore' | Select-Object -Last 20
```

**The line you're looking for:**

```text
NekoCore 1.2.0 ready
```

There should also be something like `schema 5` just before it, from the migrations. **Seeing `ready` means you're done.**

Confirm the plugin's own directory was created:

```bash
ls -lh <SERVER_DIR>/plugins/NekoCore/
```

You should see three files:

```text
config.yml       ← server behaviour
messages.yml     ← text players see
nekocore.db      ← data (don't hand-edit it)
```

**No `ready` line?** Jump to [troubleshooting](#8-troubleshooting).

---

## 4. Editing config

> **Run on the server**

```bash
cd <SERVER_DIR>/plugins/NekoCore
cp config.yml config.yml.bak       # back it up before editing; cheap insurance
nano config.yml                    # Ctrl+O to save, Enter to confirm, Ctrl+X to quit
grep -Pn '\t' config.yml           # find tabs (should print nothing)
```

**Two things are enough to get a server running:**

```yaml
branding:
  server-name: "My Server"     # ← your server's name
survival:
  world: world                 # ← your main world's actual folder name
```

**What is your main world called?**

```bash
grep '^level-name' <SERVER_DIR>/server.properties
```

That name must match `survival.world` exactly, **including capitalisation**.

**Check it, then apply it, in game:**

```text
/nekocore config check     ← validates only, applies nothing
/nekocore reload           ← validates, then swaps the whole set in
```

**Then confirm the server looks right in game:**

```text
/menu
/checkin
/sethome
/home
/coins
```

Operators can also run `/nekocore status`.

**What a reload can't cover:**

| Kind of change | How it takes effect |
| --- | --- |
| Names, numbers, switches, text | `/nekocore reload` |
| World names, coordinates, materials | `/nekocore reload` |
| Adding or replacing an MMDB file | `/nekocore reload` (it re-opens the file) |
| `database.filename` | **Full restart** |
| Replacing the JAR | **Full restart** |
| Installing a new third-party plugin | **Full restart** |
| Changes to a world plugin | **Full restart** |

---

## 5. Backup and restore

> **Run on the server**

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

```powershell
# Local Windows backup (stop the server first)
$ts = Get-Date -Format 'yyyyMMdd-HHmm'
Copy-Item 'D:\mc-server\plugins\NekoCore' "D:\backup\NekoCore-$ts" -Recurse -Force
Get-ChildItem "D:\backup\NekoCore-$ts" | Select-Object Name, Length
```

**Archiving the entire `NekoCore/` directory** is what stops you missing `nekocore.db-wal` and `nekocore.db-shm`. Those two must come from the same point in time as the `.db`.

---

## 6. Verifying file integrity

```bash
# On the server
unzip -l <SERVER_DIR>/plugins/NekoCore-1.2.0.jar | head -20   # listing works = valid zip
sha256sum <SERVER_DIR>/plugins/NekoCore-1.2.0.jar             # compare with the release page
```

```powershell
# On your own Windows PC
Get-Item '<LOCAL_DIR>\NekoCore-1.2.0.jar' | Select-Object Name, Length, LastWriteTime
# Length should be around 17428288 — about 16.6 MiB
Get-FileHash '<LOCAL_DIR>\NekoCore-1.2.0.jar' -Algorithm SHA256   # compare with the release page
```

If the file doesn't exist locally, the download never finished or the path is wrong. Mismatched sizes mean an incomplete transfer — delete the server-side file and upload again:

```bash
rm <SERVER_DIR>/plugins/NekoCore-1.2.0.jar
```

---

## 7. Passwordless login

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

## 8. Troubleshooting

Organised as symptom → cause → command. **Each entry starts with a diagnostic you can run immediately.**

### Can't connect to the server

```powershell
# Test the ports from your PC (swap 25565 for your port)
Test-NetConnection <SERVER_IP> -Port 22      # SSH
Test-NetConnection <SERVER_IP> -Port 25565   # game
```

`TcpTestSucceeded : True` means it's reachable. `False` means a firewall or security group is blocking it.

**Connecting for the first time?** SSH asks you to confirm the host fingerprint:

```text
The authenticity of host '192.0.2.10' can't be established.
ED25519 key fingerprint is SHA256:...
Are you sure you want to continue connecting (yes/no/[fingerprint])?
```

Type `yes` and press Enter. Then type the password — **nothing appears on screen while you type it, which is normal.**

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
ls -lh <SERVER_DIR>/plugins/NekoCore-1.2.0.jar
```

```powershell
# Locally: correct size and hash
Get-Item '<LOCAL_DIR>\NekoCore-1.2.0.jar' | Select-Object Length
Get-FileHash '<LOCAL_DIR>\NekoCore-1.2.0.jar' -Algorithm SHA256
```

Mismatched sizes mean an incomplete transfer. Delete the server-side file and retry:

```bash
rm <SERVER_DIR>/plugins/NekoCore-1.2.0.jar
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

**If both succeed but nothing changed in game,** check whether your edit is one a reload can't cover: replacing the JAR, changing the database filename, installing a new plugin, or world-plugin changes. Those need a full restart — the table in **Editing config** above lists them.

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

## 9. What next

| I want to… | Read |
| --- | --- |
| A hand-held first install | [Quick Start](QUICKSTART.md) |
| Change check-in rewards, store prices | [Recipes](RECIPES.md) |
| Understand every field in the config | [Configuration](CONFIGURATION.md) |
| A feature isn't responding | [Compatibility and troubleshooting](COMPATIBILITY.md) |
| Upgrade or roll back | [Installation](INSTALLATION.md) |
| The full backup and recovery procedure | [Database](DATABASE.md) |
