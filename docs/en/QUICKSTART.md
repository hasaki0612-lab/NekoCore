# Quick Start

[简体中文](../zh-CN/QUICKSTART.md) | [Back to README](../../README.en.md) | [Command reference](DEPLOY.md)

Follow along and in about ten minutes you'll be in-game looking at your own server panel.

**No** Java knowledge needed, **no** config-writing experience, **no** Linux background.

> **Already running Paper 26.2 on Java 25?** Drop the JAR into `plugins/` and start the server — you can try `/menu` straight away. With a main world named `world`, the defaults need no edits first: play with it, then change the server name and rewards later. The first time a config is generated it prints five short guidance lines, and ordinary restarts don't repeat them. To pick a ready-made scenario instead, see [Presets](PRESETS.md) — and if you already have a running server, **never** overwrite its config with a preset.

---

## First, one thing you need to understand: the server directory

Every step below refers to it, so let's settle this in a minute.

**The server directory is the folder your server lives in.** The one containing `paper.jar`.

**It looks like this:**

```text
your-server-directory/
├── paper.jar              ← the server itself; the folder is named after it
├── server.properties      ← settings: main world name, port, and so on
├── eula.txt               ← marks that you accepted Mojang's terms
├── plugins/               ← plugins go here! NekoCore goes here too
├── world/                 ← the main world's map data
└── logs/                  ← logs; this is where you look when something breaks
```

**If you don't know where it is**, log in to the server and run these three commands. Whichever one produces output is the one:

```bash
ls /opt/minecraft
ls /root/minecraft
ls ~/minecraft
```

If you see `paper.jar` in the listing, you've found it.

> Every `<SERVER_DIR>` below gets replaced with the path you just found.
> So if it turned out to be `/opt/minecraft`, you replace `<SERVER_DIR>` with exactly that.

<details>
<summary><b>Where even is my server? I don't know what it looks like</b></summary>

Three common situations. Find yours.

**① You bought a VPS (a remote Linux machine)**

Your provider gave you an **IP address**, a **username** (usually `root`), and a **password**. Those three are enough to connect.

**② You're on a panel host (Pterodactyl, MCSManager — a web control panel)**

Then the server isn't on your machine; it's on the provider's. **You don't need to log into Linux and you don't need to type commands** — you click things in a browser. Skip to the "Panel host" block at the bottom of this page.

**③ The server runs on your own PC**

Then the server directory is just a folder on your computer, like `D:\mc-server`. Skip to the "Local Windows" block at the bottom of this page.

</details>

---

## Step 1: Put NekoCore into the `plugins/` folder

**On your own Windows PC**, press `Win`, type `powershell`, and hit Enter to open it.

Replace the two angle-bracket values below, paste the command in, and press Enter:

```powershell
scp "D:\NekoCore-1.2.0.jar" root@<SERVER_IP>:<SERVER_DIR>/plugins/
```

**The two things to replace:**

| Replace | Where you find it |
|---|---|
| `<SERVER_IP>` | The IP your provider gave you, e.g. `192.0.2.10` |
| `<SERVER_DIR>` | What you found above, e.g. `/opt/minecraft` |

**Filled in, it looks like this** (example only — don't copy it):

```powershell
scp "D:\NekoCore-1.2.0.jar" root@192.0.2.10:/opt/minecraft/plugins/
```

It will ask for your password. **Nothing appears on screen while you type it** — your keyboard isn't broken, that's just how Linux works. Type it and press Enter.

> **If it errors:**
>
> - `No such file or directory` — the local path `D:\NekoCore-1.2.0.jar` is wrong. Confirm the JAR is really there.
> - `Permission denied` — not enough permission. Replace `root` in the command with your actual username.
> - Hangs for a long time — wrong IP, or the server is unreachable.

---

## Step 2: Start the server

In that same PowerShell window, type:

```powershell
ssh root@<SERVER_IP>
```

(Replace `<SERVER_IP>` with yours; it asks for the password again.)

Once connected, your prompt changes to something like `root@server:~#` — that means **you are now inside the server**.

Then run these two, in order:

```bash
cd <SERVER_DIR>
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui
```

The first one means "go into the server directory". The second one means "start the server".

> **If it says `cd: no such file or directory`** — you got `<SERVER_DIR>` wrong. Go back to the top and check it again.

Wait ten seconds or so, then look for this line:

```text
NekoCore 1.2.0 ready
```

**Seeing it means you succeeded.**

**How do you read the log?** Type:

```bash
screen -r minecraft
```

That attaches you to the server console so you can watch the log scroll. Once you've seen `ready`, press `Ctrl+A` then `D` to detach (**note: this does not stop the server**).

> Messages like "feature disabled" in the log **are not a problem**. NekoCore quietly turns off whatever is missing, and missing PlaceholderAPI, Citizens, or Multiverse is all expected.
>
> **No `ERROR` means you're fine.**

---

## Step 3: Stop the server, change two things

**Stop the server first.** Attach to the console:

```bash
screen -r minecraft
```

Type `stop` and press Enter, then **wait for it to fully exit** (the log stops scrolling and your prompt comes back).

Now edit the config. The server-side editor `nano` is the easiest option:

```bash
nano <SERVER_DIR>/plugins/NekoCore/config.yml
```

Find these lines near the top and **change exactly two things**:

```yaml
branding:
  server-name: "My Server"     # ← your server's name
survival:
  world: world                 # ← your main world's actual folder name
```

**How do you save in `nano`?** Always the same three steps:

```text
Ctrl + O      (the letter O, not zero) → save
Enter         → confirm the filename
Ctrl + X      → exit
```

**What is your main world called?** This command tells you:

```bash
grep '^level-name' <SERVER_DIR>/server.properties
```

If it prints `level-name=world`, then `survival.world` is `world`.

**Capitalisation must match exactly.** `World` and `world` are two different things to the server. Getting it wrong won't crash anything, but a menu entry will disappear in game.

<details>
<summary><b>Indentation errors / the server won't start</b></summary>

Nine times out of ten it's a **Tab character**. This file only accepts **spaces** for indentation. Tabs and spaces look identical, but the server rejects tabs.

**Check for tabs:**

```bash
grep -Pn '\t' <SERVER_DIR>/plugins/NekoCore/config.yml
```

**This command should print nothing.** If it prints anything, those lines contain tabs — retype them with spaces.

**Don't panic if you break it.** Even with a broken config, the server **won't go down** — it keeps running on the last valid configuration and just tells the console what was wrong.

</details>

<details>
<summary><b>Don't open this file in Windows Notepad</b></summary>

Notepad can change the encoding or the indentation when saving, and YAML is sensitive to both.

Recommended instead:

- **VS Code** (free, and a YAML extension will flag errors for you)
- Notepad++
- `nano` on the server — the one used above

If you really must use Notepad, **choose UTF-8** when saving.

</details>

---

## Step 4: Restart, and look around in game

```bash
cd <SERVER_DIR>
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui
```

Then connect with your Minecraft client and **try these four, in order**:

```text
/menu         open the server panel — see what it looks like overall
/checkin      claim today's check-in
/sethome      remember where you're standing
/home         teleport back
```

**If all four work, the install is done.**

> **Fewer things in `/menu` than you expected?** That's normal. **Disabled modules don't show as greyed-out buttons — they disappear entirely**, so the screen you see is exactly what your server currently offers.

**Operators can also try these two:**

```text
/nekocore status          one screen listing every module's state
/nekocore config check    check the config for mistakes (applies nothing)
```

| Command | Difference |
|---|---|
| `config check` | **Checks only, changes nothing.** Use it right after editing YAML, to confirm you didn't make a typo |
| `reload` | **Makes it live** once validation passes. Use it when you're satisfied |

---

## Post-install check

| Check | How |
|---|---|
| Is the JAR in the right place? | `plugins/` should contain `NekoCore-1.2.0.jar` |
| Did the plugin load? | The log has `NekoCore 1.2.0 ready`, with no `ERROR` |
| Were the config files created? | `plugins/NekoCore/` should contain `config.yml`, `messages.yml`, `nekocore.db` |
| Does it work in game? | `/menu` opens and `/checkin` gives you something |
| Did you back up? | **Stop the server**, then copy the whole `plugins/NekoCore/` somewhere else |

**Don't skip the backup.** One command does it:

```bash
cd <SERVER_DIR>/plugins
tar -czf ~/nekocore-backup-$(date +%Y%m%d).tar.gz NekoCore/
```

---

## What next

| I want to… | Read |
|---|---|
| Change check-in rewards, store prices, turn TAB off | [Recipes](RECIPES.md) |
| Understand what a config field actually does | [Configuration](CONFIGURATION.md) |
| Copy commands directly (backups, troubleshooting) | [Command reference](DEPLOY.md) |
| Install Citizens / PlaceholderAPI | [Dependencies](DEPENDENCIES.md) |
| Show player provinces in TAB | [GeoIP](GEOIP.md) |
| Something isn't responding | [Compatibility and troubleshooting](COMPATIBILITY.md) |
| Get ready to go live | [Installation](INSTALLATION.md) |

---

<details>
<summary><b>Panel host (Pterodactyl / MCSManager, etc.)</b></summary>

**On a panel you don't type any commands** — it's all clicking. The server lives on the provider's machine and you only need a browser.

```text
1. Open "File Manager" in the panel
2. Go into the plugins folder
3. Click "Upload" and pick NekoCore-1.2.0.jar from your PC
4. Go back to "Console" and click "Start"
   (if the server is running, click "Stop" first)
5. When the console shows NekoCore 1.2.0 ready → success
6. Click "Stop" to shut the server down
7. Back in File Manager, go into plugins/NekoCore/ and click "Edit" next to config.yml
8. Change these two, then save:
      branding.server-name   → your server's name
      survival.world         → your main world's name
9. Click "Start", join the game, run /menu
```

**Three things to watch on a panel:**

- **Stop before uploading.** Swapping the JAR while the server runs invites strange problems.
- **Mind the indentation.** Many panel editors render a tab exactly like spaces, so it looks aligned when it isn't. If the server won't start after an edit, check this first.
- **No `plugins` folder?** Paper has never started successfully. Click "Start" once to generate it.

**How do I find my main world's name?** Open `server.properties` in File Manager and look for the `level-name=` line. What follows the equals sign is the name.

</details>

<details>
<summary><b>Local Windows server</b></summary>

The server runs on your own Windows PC, so **no SSH and no scp** are involved.

Assuming your server directory is `D:\mc-server`:

```powershell
# 1. Copy the JAR into the plugins folder
Copy-Item 'D:\NekoCore-1.2.0.jar' 'D:\mc-server\plugins\' -Force

# 2. Start the server
Set-Location 'D:\mc-server'
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

Wait for `NekoCore 1.2.0 ready` in the log, then type `stop` in that same window.

**Editing the config:**

```powershell
# Open in VS Code (recommended — it makes indentation problems visible)
code 'D:\mc-server\plugins\NekoCore\config.yml'
```

Change `branding.server-name` and `survival.world`, save, and start the server again.

**Commands you'll reuse:**

```powershell
# Start the server
Set-Location 'D:\mc-server'; java -Xms2G -Xmx2G -jar paper.jar --nogui

# Read the last 50 log lines
Get-Content 'D:\mc-server\logs\latest.log' -Tail 50

# Back up (stop the server first!)
$ts = Get-Date -Format 'yyyyMMdd-HHmm'
Copy-Item 'D:\mc-server\plugins\NekoCore' "D:\backup\NekoCore-$ts" -Recurse -Force
```

</details>

<details>
<summary><b>Don't have Java and Paper yet?</b></summary>

NekoCore is a plugin — it needs a working Paper server to live in. If you don't have one at all, do these two things first.

**Step one: install Java 25**

Go to [Oracle's Java 25 installation docs](https://docs.oracle.com/en/java/javase/25/install/) and pick whatever method suits your system.

Then verify it in PowerShell:

```powershell
java -version
```

The first line should show `25`.

> **If you get `'java' is not recognized as an internal or external command`** — Java is installed but your system can't find it. Look at the path the installer printed at the end, add that `bin` folder to your system PATH, and try again.

**Step two: install Paper 26.2**

1. Go to the [official Paper downloads page](https://papermc.io/downloads/paper/), choose **26.2**, and download the `.jar`
2. Create a new folder (say `D:\mc-server`), put the JAR in it, and **rename it to `paper.jar`**
3. Open PowerShell, `cd` into that folder, and run:

   ```powershell
   java -Xms2G -Xmx2G -jar paper.jar --nogui
   ```

**The first start is guaranteed to "fail"** — it stops and asks you to accept Mojang's terms.

4. Open the freshly created `eula.txt`, change `eula=false` to `eula=true`, and save
5. Run the start command again

Wait for `Done (12.345s)! For help, type "help"` and you're set.

6. Type `stop` to shut down cleanly

**That folder is now your "server directory"**, and `plugins/` is inside it. Go back to Step 1 above and continue.

Reference: [Paper's getting started guide](https://docs.papermc.io/paper/admin/getting-started/) · [Paper's Java install notes](https://docs.papermc.io/paper/misc/java-install/)

</details>
