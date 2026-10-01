# Quick Start: get your first NekoCore server running

[简体中文](../zh-CN/QUICKSTART.md) | [Back to README](../../README.en.md)

This page is written for **someone running a server for the first time**.

Assume you have a computer or a VPS with an internet connection and nothing else — no Java installed, no Minecraft server set up, no experience with YAML. Follow the steps below and in about five to ten minutes you'll be in-game looking at your own server panel.

If you've run a server before and just want a different core, skip ahead to [step 2](#step-2-drop-the-jar-into-plugins) or even [step 4](#step-4-edit-the-config). Nothing earlier will be missed.

> **What you need**
> - A computer (Windows, macOS, or Linux — anything that runs Java)
> - About 2 GB of free memory
> - `NekoCore-1.0.0.jar`
>
> **What you don't need:** Git, Maven, MySQL, Docker, or Linux command-line experience. None of it.

> **Want commands you can just copy?**
> This page explains *why* each step is what it is, and gives the core commands. For the **complete command reference** across remote VPS, local Windows, and panel hosting — `scp` uploads, `ssh` login, `screen`/`systemd` control, passwordless keys, log digging, backup archives, and a cheat sheet — see the [deployment command reference](DEPLOY.md). The two work best together: understand it here, copy it there.

**If you just want it running as fast as possible,** replace `<SERVER_IP>` with your server's address and `<SERVER_DIR>` with your server root (for example `/opt/minecraft`), and these four steps are enough:

```powershell
# === Run on your own Windows PC ===

# 1. Confirm the JAR is present locally
Get-Item 'D:\NekoCore-1.0.0.jar' | Select-Object Name, Length

# 2. Upload it (prompts for your password)
scp "D:\NekoCore-1.0.0.jar" root@<SERVER_IP>:<SERVER_DIR>/plugins/
```

```bash
# === After logging in to the server ===
ssh root@<SERVER_IP>

# 3. Start the server
cd <SERVER_DIR>
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui
screen -r minecraft          # attach to the console; "NekoCore 1.0.0 ready" means success

# 4. Stop, edit config, start again
#    Type stop in the console, then:
nano <SERVER_DIR>/plugins/NekoCore/config.yml
#    Change branding.server-name and survival.world, then Ctrl+O, Enter, Ctrl+X
#    Finally re-run the start command from step 3
```

**What every flag means, and what to do when it fails, is below.** The command reference has fuller versions, including passwordless login, backups, and log troubleshooting.

---

## Step 1: Install Java and run Paper once

### Install Java 25

Paper 26.2 requires Java 25. Start at [Oracle's Java 25 installation docs](https://docs.oracle.com/en/java/javase/25/install/) and pick whatever method suits your system (the installer on Windows, the `.dmg` on macOS, a package manager or archive on Linux).

Then verify it, because this is worth thirty seconds. Open a terminal (PowerShell or Command Prompt on Windows) and run:

```text
java -version
```

The first line should show something like `openjdk version "25.0.x"`.

**If you get `'java' is not recognized as an internal or external command`,** Java is installed but your system doesn't know where it is. This is where most first-timers get stuck: look at the end of the installation output for the install path, then add its `bin` directory to your system PATH. Don't skip past this — every later step depends on it.

### Download Paper 26.2

Go to the [official Paper downloads page](https://papermc.io/downloads/paper/), choose 26.2, and download the `.jar`.

### Make a clean folder

Create a folder wherever you like — `D:\mc-server` or `~/mc-server` — and put the Paper JAR in it.

Rename it to something short and easy to type, like `paper.jar`. You'll be typing this name every time you start the server, so a short one pays off.

### Start it the first time

Open a terminal in that folder and run:

```text
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

A quick note on those flags: `-Xms2G -Xmx2G` gives the server 2 GB of memory (plenty for a friend server; raise it to 4G if you add players or plugins), and `--nogui` skips the useless graphical window.

**The first start is guaranteed to fail.** That's expected. It will stop and tell you to accept Mojang's EULA. Open the freshly created `eula.txt`, change `eula=false` to `eula=true`, and save.

Run the same command again. This time you'll see it generate the world and load chunks, and eventually print something like `Done (12.345s)! For help, type "help"`.

**Seeing `Done` means it worked.** Type this into the terminal:

```text
stop
```

Wait until it fully exits — until the terminal is back to accepting commands — before closing the window. This matters: closing the window isn't a clean shutdown, and the server may have been mid-write.

> About the EULA: accepting it means agreeing to [Mojang's end user licence agreement](https://aka.ms/MinecraftEULA). Every private server has to do this. It isn't a NekoCore requirement.

Reference material: [Paper's getting started guide](https://docs.papermc.io/paper/admin/getting-started/) and [Paper's Java install notes](https://docs.papermc.io/paper/misc/java-install/).

---

## Step 2: Drop the JAR into `plugins/`

Your server folder should now look like this:

```text
mc-server/
├── paper.jar
├── eula.txt
├── server.properties
├── logs/
├── world/
└── plugins/          ← this folder
```

**The `plugins` folder sits in the server root, right next to `paper.jar`.** Paper creates it on first start. If you don't see it, create it by hand — the name is lowercase `plugins`.

Copy `NekoCore-1.0.0.jar` into it. **Which method depends on where the server lives:**

**Server on this machine (Windows) — use PowerShell:**

```powershell
Copy-Item 'D:\NekoCore-1.0.0.jar' 'D:\mc-server\plugins\' -Force
Get-Item 'D:\mc-server\plugins\NekoCore-1.0.0.jar' | Select-Object Name, Length
```

**Server on a remote Linux VPS — `scp` it up, then `ssh` in to work:**

```powershell
scp "D:\NekoCore-1.0.0.jar" root@<SERVER_IP>:<SERVER_DIR>/plugins/
```

```bash
# Log in afterwards to confirm it arrived
ssh root@<SERVER_IP>
ls -lh <SERVER_DIR>/plugins/NekoCore-1.0.0.jar
```

**Panel host —** File Manager → open `plugins` → Upload → pick the JAR.

Once it's in place:

```text
mc-server/
└── plugins/
    └── NekoCore-1.0.0.jar
```

> **Three don'ts**
> - Don't copy `original-NekoCore-1.0.0.jar`. If you built from source, `target/` contains that file too — it's the pre-shading intermediate and will cause problems.
> - Don't put the JAR in a subfolder inside `plugins/`. Plugins don't look there.
> - Don't use a "translated" or "repackaged" build from somewhere unknown. Get it from the project's official release page.

If you also want PlaceholderAPI, Citizens, or Multiverse-Core, drop them into `plugins/` now too. Order doesn't matter. **Leaving them out is completely fine** — nothing in the core depends on them. (For what each one does, see [Dependencies](DEPENDENCIES.md).)

> **Complete commands for every setup**, including passwordless login, log digging, and backup archives, are in the [deployment command reference](DEPLOY.md).

---

## Step 3: Start it and confirm it actually loaded

Start the server again:

```text
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

On its first start, NekoCore does three things:

1. Generates default `config.yml` and `messages.yml` in `plugins/NekoCore/`.
2. Creates the database at `plugins/NekoCore/nekocore.db`, running migrations V1 through V5 in order to build the tables.
3. Prints a short status summary.

**Why let it generate the config first?** Because NekoCore's defaults are complete and immediately usable. Letting the plugin write its own file gives you a template that's correctly formatted, fully commented, and exactly matched to the version you're running. Editing that is far safer than writing YAML by hand.

### How to confirm it loaded

Scroll through the console and look for these:

| What you should see | What it means |
| --- | --- |
| A NekoCore startup banner or info line | Paper recognised the plugin |
| `schema 5` or migration-related lines | The database structure is correct |
| A summary line containing `NekoCore 1.0.0 ready` | Initialisation finished; it can serve players |
| **No** `ERROR` or long exception stack traces | All good |

The log is deliberately condensed, so it won't flood your screen. Once you see `ready`, you're fine.

If you'd rather confirm from in-game, run `/nekocore status`. It lists the NekoCore, Paper, and Java versions, the database schema, and the state of each module.

**On a Linux server, two commands handle the log:**

```bash
grep -a "NekoCore" <SERVER_DIR>/logs/latest.log | tail -20    # NekoCore lines only
tail -f <SERVER_DIR>/logs/latest.log                          # follow live (Ctrl+C to stop)
```

**On a local Windows machine:**

```powershell
Select-String -Path 'D:\mc-server\logs\latest.log' -Pattern 'NekoCore' | Select-Object -Last 20
```

### Missing PlaceholderAPI, Citizens, Multiverse, or an MMDB is normal

You may see messages like "region prefix disabled" because no database file was found. **That isn't an error.** NekoCore is designed to quietly turn off whatever is missing and keep everything else running. Configure it later, when you actually want that feature.

Now stop the server normally:

```text
stop
```

---

## Step 4: Edit the config

There are now two files you can edit in `plugins/NekoCore/`:

```text
plugins/NekoCore/
├── config.yml        ← server behaviour
├── messages.yml      ← text players see
└── nekocore.db       ← data; leave it alone
```

### Stop the server first

Please stop the server before editing `config.yml`. NekoCore does support live reloading, but during your first setup you're likely to touch things a reload can't cover (worlds, the database filename), and stopping first is simply less to think about. "Editing config means stopping first" is a habit worth keeping.

### What to open it with

**Don't use Windows Notepad.** It may change the file's encoding or indentation when saving, and YAML is sensitive to both.

Use any editor that shows whitespace explicitly and saves as UTF-8:

- **VS Code** (recommended — free, and a YAML extension will highlight errors for you)
- Notepad++
- Sublime Text
- `nano` or `vim` on Linux/macOS (save as UTF-8)

### Change at least these two things

Open `config.yml` and find the top of the file:

```yaml
config-version: 8
branding:
  # Player-facing text that supports {server} uses this name; run /nekocore reload after changing it.
  server-name: "My Server"
database:
  filename: nekocore.db
  save-interval-seconds: 30
survival:
  enabled: true
  world: world
  # Run this command when Multiverse-Core is installed; without it, teleports safely to the loaded world's spawn.
  command: 'mvtp {player} {world}'
```

> The comments above are translated for readability. The `config.yml` that ships in the JAR has its comments in Simplified Chinese, so don't be surprised when the file you open looks different in that respect — the keys, values, and defaults are identical.

**Change 1: `branding.server-name`**

Replace `"My Server"` with your server's name. Keep the quotes — they're required if the name contains spaces, a `#`, or colour codes.

```yaml
branding:
  server-name: "Cat Cafe"
```

This name shows up in TAB, the join welcome title, GUI titles, tips, the mascot hologram, and the daily task named "Good morning, {server}!".

**Change 2: `survival.world`**

Check `level-name` in `server.properties` — that's your main world's name, and it defaults to `world`.

- If `level-name=world`, leave `survival.world: world` alone.
- If you changed it, say to `level-name=main`, then set `survival.world: main` to match.

World names are **case-sensitive**. `World` and `world` are two different things to Paper. Getting this wrong won't crash anything, but the survival entry will vanish from `/menu`.

### About `survival.command`

That line is for servers running Multiverse-Core. **If you don't have Multiverse, ignore it** — when NekoCore finds the command unavailable, it safely teleports the player to the loaded world's spawn point instead.

### YAML indentation, and why it can't be fudged

YAML expresses hierarchy through **the number of spaces**. That's different from JSON and from most config formats, and it's the source of most beginner frustration:

- **Spaces only, never the Tab key.** They look identical and the parser will reject one of them. VS Code shows the current file's indentation in the bottom-right corner.
- **Siblings must be indented identically.** If `server-name` under `branding` has two leading spaces, then `filename` under `database` needs two as well.
- **A colon needs a space after it.** `server-name:"Cat Cafe"` is wrong; `server-name: "Cat Cafe"` is right.
- **Quote anything containing special characters.** In YAML, `#` starts a comment and `:` separates keys from values, so write `server-name: "My #1 Server"` with the quotes.

Breaking it is survivable — a failed reload leaves the server running on the previous good config. So experiment freely.

---

## Step 5: Restart and join

Start the server, then connect with a Minecraft client to `localhost` (for local testing) or your server address.

### What you'll see on join

- A welcome title fades in with your username and your server's name.
- The TAB list fills in: coordinates, TPS, online count, coins, time, uptime.
- After a moment, a tip scrolls through chat.

### Try these in order

```text
/menu
```

Start here and look at the overall shape of things. Disabled modules don't appear as greyed-out buttons — they disappear and the others re-center, so this screen is exactly what your server currently offers.

```text
/coins
```

Check your balance. New players start at zero.

```text
/checkin
```

Claim today's reward. The default is 100 coins and 50 experience. **Once per day only**, counted as a calendar day in the server's configured timezone (Beijing time by default).

```text
/sethome
/home
```

Record where you are, then teleport back. Homes are stored **per world**, so setting one in the Nether and another in the End later won't overwrite this one.

```text
/check home world
```

See exactly where your home is.

```text
/store
```

Browse the shop. **Right-click buys, left-click sells** — try not to mix them up. Your current balance is shown in the interface.

```text
/bag
```

Open your portable storage. Writable in the main world, read-only elsewhere by default. That's deliberate: it keeps Bag from fighting with per-world inventory rules.

```text
/tpn <another player's name>
```

Have them answer with `/yes` or `/no`. Testing alone? Open a second client, or skip this one for now.

### Two more if you're an operator

```text
/nekocore status
```

One screen showing the state of every module. Whenever something "isn't working," look here first.

```text
/nekocore config check
```

Validates the config files and **applies nothing**. Use it when you've edited YAML but haven't decided to make it live.

To actually apply changes:

```text
/nekocore reload
```

**The difference is worth remembering:**

| | What it does | When to use it |
| --- | --- | --- |
| `config check` | Parses and validates the files on disk, reports problems, changes nothing | Right after editing YAML, to confirm you didn't make a mistake |
| `reload` | Parses → validates → builds a complete new config → swaps it in on success | When you're satisfied and want it live |

`reload` is **atomic**: every file must pass before anything is replaced. If any part fails, the server keeps running on the old configuration and tells you which YAML path was the problem and what it expected. You'll never end up half-new and half-old.

---

## First-start checklist

Before you invite anyone, spend two minutes on this:

**Server level**

- [ ] `java -version` reports 25
- [ ] Paper is version 26.2
- [ ] The server starts to `Done` and shuts down cleanly with `stop`
- [ ] `plugins/NekoCore/` contains `config.yml`, `messages.yml`, and `nekocore.db`

**Configuration level**

- [ ] `branding.server-name` is your server's name (not `My Server`)
- [ ] `survival.world` matches the world the console actually loaded, including case
- [ ] You ran `/nekocore config check` and it succeeded
- [ ] `config-version: 8` and `messages-version: 7` are untouched

**In game**

- [ ] `/menu` opens, and the buttons shown are the features you intend to offer
- [ ] `/checkin`, `/sethome`, `/home`, `/coins`, `/store`, and `/bag` all work
- [ ] TAB displays correctly and isn't being overridden by another plugin
- [ ] `/nekocore status` shows no unexpected error states

**Before you go public**

- [ ] **You made a backup.** Stop the server, then copy the whole `plugins/NekoCore/` folder somewhere else. If `nekocore.db-wal` or `nekocore.db-shm` are present, copy those too.
- [ ] You've considered whether the store prices and task rewards suit your player count (the defaults are tuned for a friend server — see [Economy](ECONOMY.md))
- [ ] If you plan to enable GeoIP, you've read the [compliance and privacy notes](GEOIP.md)

---

## Where to go next

Pick whichever matches what you want right now.

**You want commands you can copy without thinking**
→ [Deployment command reference](DEPLOY.md). Complete commands for all three setups — remote VPS, local Windows, and panel hosting: `scp` uploads, `ssh` login, `screen`/`systemd` control, passwordless keys, log digging, backup archives, and a cheat sheet.

**Make the server feel like yours**
→ [Recipes](RECIPES.md). Things like "I want to change the check-in reward," "I want a single survival server," "I want to turn TAB off." Each one gives the shortest answer, the YAML, the steps, and how to confirm it worked.

**Understand what a field actually does**
→ [Configuration](CONFIGURATION.md). Every module explains how it behaves in-game before listing any fields.

**Add Citizens, PlaceholderAPI, or Multiverse**
→ [Dependencies](DEPENDENCIES.md), with official download links and installation steps.

**Show player provinces or countries in TAB**
→ [GeoIP](GEOIP.md). Note that this step requires you to obtain the data file from MaxMind yourself; there's none in the JAR.

**Something isn't responding**
→ [Compatibility and troubleshooting](COMPATIBILITY.md). "Why isn't TAB showing," "why is the region always blank," "why is there no AFK pool in the menu" — each with ordered troubleshooting steps.

**Getting ready to go live**
→ The upgrade and rollback sections of [Installation](INSTALLATION.md), plus the backup procedure in [Database](DATABASE.md). Both are worth reading *before* you need them.
