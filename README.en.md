# NekoCore

> Install one plugin instead of a dozen. Start a playable small server right after.

Hello, and welcome to NekoCore.

You probably just wanted fewer plugins on your server. But NekoCore isn't only about stuffing features into one JAR — it's about letting people who **don't know Java** set up profiles, economy, menus, a store and daily tasks. Editing YAML is enough.

Once it's installed, a player types `/menu` and gets a full server panel, `/checkin` claims the day's gift, and `/store` has 109 products to buy and sell. **All of that is on by default** — you don't configure anything to get it.

No database server, no web panel, no extra downloads. **Paper and Java are all it needs.**

## What NekoCore solves for you

| The problem you have | What NekoCore does |
|---|---|
| Base features scattered across seven or eight plugins, config folders everywhere | Profiles, economy, levels, menu, homes, check-in, store, Bag, TPN, daily tasks, TAB and tips all live in one plugin with two config files |
| You installed a plugin and don't know where to start configuring | `/nekocore config check` validates before applying. A wrong field tells you which line, what type was expected, and suggests near-name matches for misspelled materials |
| You're afraid of breaking the server with a bad edit | Reloads are atomic — everything must pass before anything swaps. If one thing fails, the server keeps running on the old config and players notice nothing |
| You don't want a plugin teleporting players or spawning floating objects at random | Everything involving **coordinates or external data** is off by default, and needs *two* locks — `enabled` plus `position-configured` — before it acts |
| Store trades could duplicate items if the server crashes | Store and Bag run through database transactions with recovery records. An uncertain state is **quarantined, never guessed at** — nothing is silently re-sent or overwritten |
| You don't know why a feature "did nothing" | `/nekocore status` lists every module's state on one screen. When a module is off, its menu entry disappears rather than sitting there greyed out |

The important part isn't "lots of features" — it's **playable by default, but never guessing on your behalf**:

```text
Features needing you to supply external facts  →  off by default, two locks
Everything that doesn't                        →  on by default, playable immediately
```

## Ready? Let's install it

| Where your server lives | Go here |
|---|---|
| Remote Linux VPS (you're on Windows) | [Quick Start](docs/en/QUICKSTART.md) — that's what it's written for |
| A panel host (Pterodactyl, MCSManager…) | [Quick Start](docs/en/QUICKSTART.md), collapsible block at the end |
| Local Windows machine | [Quick Start](docs/en/QUICKSTART.md), collapsible block at the end |
| You want copyable commands, backup and troubleshooting | [Command reference](docs/en/DEPLOY.md) |

**The shortest path** (replace `<SERVER_IP>` and `<SERVER_DIR>` with your own):

```powershell
# In PowerShell on your own Windows PC
scp "D:\NekoCore-1.2.0.jar" root@<SERVER_IP>:<SERVER_DIR>/plugins/
ssh root@<SERVER_IP>
```

```bash
# After logging in to the server
cd <SERVER_DIR>
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui
screen -r minecraft          # seeing "NekoCore 1.2.0 ready" means it worked
```

Then stop the server, change these two things, and start it again:

```yaml
branding:
  server-name: "My Server"     # ← your server's name
survival:
  world: world                 # ← your main world's actual folder name
```

**Full walkthrough, config editing, and in-game verification** → [Quick Start](docs/en/QUICKSTART.md)

---

## What players see once it's installed

**The moment they join.** A welcome title fades in with their name and your server's name. The TAB list fills in: coordinates, TPS, online count, coins, time, uptime.

**Opening `/menu`:**

| Entry | What the player sees |
|---|---|
| Profile | Level, experience, coins, playtime, first join date |
| Daily tasks | Three easy, three normal, three hard — shared rotation, per-player progress |
| Check-in | Once per day, 100 coins + 50 experience by default |
| Show my region | The player's own privacy toggle (needs GeoIP configured first) |
| Survival one / two | Travel to that world's public spawn |
| Minigames / AFK pool | Off by default; appear once you configure coordinates |

**Disabled modules leave no gaps** — the remaining buttons re-center. So the screen a player sees is exactly what your server currently offers.

**Commands players actually use:**

| Command | What it does |
|---|---|
| `/menu` | Open the server panel |
| `/checkin` | Claim today's check-in |
| `/sethome` `/home` | Set / return to a home (stored per world) |
| `/store` | The shop — **right-click buys, left-click sells** |
| `/bag` | Portable storage; writable in the main world by default |
| `/tpn <player>` | Send a teleport request; the other player answers `/yes` or `/no` |
| `/tasks` | Open the daily tasks directly |

**Off by default** (because NekoCore won't guess): region display, second survival world, minigames area, AFK pool, weekly coin leaderboard, Citizens mascot. Configure the relevant world or coordinates and switch them on.

---

## Two files hold every setting

| File | What it controls |
|---|---|
| `config.yml` | Server behaviour: worlds, economy numbers, menu layout, module switches |
| `messages.yml` | Every string a player can see |

The workflow after any edit is always these two:

```text
/nekocore config check     ← checks only, applies nothing
/nekocore reload           ← once you're satisfied, makes it live
```

**A failed reload won't take your server down.** It keeps running on the last valid configuration and tells the console which YAML path was wrong and what it expected.

**Needs a full restart:** replacing the JAR, changing the database filename, installing a new plugin, world-plugin changes. Everything else can `reload`.

> **Don't use Bukkit's `/reload`.** It's a completely different thing from NekoCore's reload, and it only makes your server harder to predict.

For specific changes see [Recipes](docs/en/RECIPES.md); for what each field means see [Configuration](docs/en/CONFIGURATION.md).

---

## Where the data lives

One SQLite file: `plugins/NekoCore/nekocore.db`.

**Three things matter when backing up:**

1. **Stop the server first** and wait for the Java process to actually exit
2. If `nekocore.db-wal` and `nekocore.db-shm` are also present, **copy them too** (copying only the `.db` can give you an incomplete point in time)
3. If you run per-world inventories (Multiverse-Inventories, say), the item data must come from the **same point in time** as the database

```bash
# One command archives the whole directory, so nothing is missed
cd <SERVER_DIR>/plugins
tar -czf ~/nekocore-$(date +%Y%m%d).tar.gz NekoCore/
```

**Don't hand-edit the tables with a SQLite browser while the server is running.** Those recovery tables that look redundant are exactly what the transaction recovery logic reads. See [Database](docs/en/DATABASE.md).

---

## Where to look when something's wrong

In this order — three steps usually finds it:

1. **Read the console log** — a healthy start has a line `NekoCore 1.2.0 ready`
2. **`/nekocore status`** — one screen listing every module; this answers most "it's not working" questions
3. **`/nekocore config check`** — if you edited YAML, confirm the config is legal first

| Symptom | Usual cause | What to do |
|---|---|---|
| No `NekoCore ready` in the log | The JAR isn't in the right place | `ls <SERVER_DIR>/plugins/ \| grep -i neko` |
| A module vanished from the menu | It's disabled, or its world isn't loaded | This is **by design**, not a fault — check `/nekocore status` |
| AFK pool / leaderboard does nothing | Only one of the two locks is on | Both `enabled` and `position-configured` must be `true` |
| TAB missing or overridden | Another TAB plugin is competing | Only one plugin can own TAB — pick a side explicitly |
| Region stays blank | Not reloaded, or `cache-session` was set to `false` | `/nekocore reload`, then check the config |
| Config change had no effect | You ran `config check` but not `reload` | They're different commands |
| Server won't start | `eula.txt` still says `false`, or the port is taken | `cat eula.txt` / `ss -tlnp \| grep 25565` |

**Something else?** See [Compatibility and troubleshooting](docs/en/COMPATIBILITY.md).

---

## Requirements

| Requirement | Version |
|---|---|
| [Paper](https://papermc.io/downloads/paper/) | 26.2 (API build 129 stable) |
| [Java](https://docs.oracle.com/en/java/javase/25/install/) | 25 |

SQLite JDBC and the GeoIP reader are **shaded into the JAR** — nothing extra to download.

**Optional third-party components** (everything works without them):

| Component | When you need it |
|---|---|
| PlaceholderAPI | To let other plugins read NekoCore data (e.g. `%nekocore_coins%`) |
| Multiverse-Core | To route world travel through `mvtp {player} {world}` |
| Citizens | Only for the built-in mascot interaction module |
| GeoLite2 City data | Only for region display; you fetch it from MaxMind yourself |

WorldGuard is **not** a NekoCore dependency. It can coexist, but that combination isn't covered by the automated tests.

> A common mix-up: **the Mascot is a module built into NekoCore, not a third-party plugin.** Citizens supplies only the NPC entity.

See [Dependencies](docs/en/DEPENDENCIES.md).

---

## Documentation

New here? Read them in this order: **Quick Start → Recipes → Configuration**.

| Document | What it covers |
|---|---|
| [Quick Start](docs/en/QUICKSTART.md) | Hand-held first install, with a checklist |
| [Command reference](docs/en/DEPLOY.md) | Copyable commands: scp, ssh, screen, backups, troubleshooting |
| [Presets](docs/en/PRESETS.md) | Complete ready-made configs for three server scenarios |
| [Recipes](docs/en/RECIPES.md) | "I want to change the check-in reward" style goals |
| [Configuration](docs/en/CONFIGURATION.md) | What each module does and how to fill in its fields |
| [Compatibility](docs/en/COMPATIBILITY.md) | "Why isn't it working" with ordered steps |
| [Dependencies](docs/en/DEPENDENCIES.md) | Each third-party component and its official source |
| [Commands](docs/en/COMMANDS.md) | Player commands, admin commands, permission nodes |
| [Economy](docs/en/ECONOMY.md) | Where coins come from and go, how the store is priced |
| [Daily tasks](docs/en/DAILY-TASKS.md) | How each of the 20 tasks is completed |
| [Database](docs/en/DATABASE.md) | SQLite, migrations, backup and recovery |
| [Installation](docs/en/INSTALLATION.md) | Fresh install, upgrades, rollback, uninstall |
| [GeoIP](docs/en/GEOIP.md) | Installing region data legally, and the privacy model |

简体中文: [README.md](README.md) · [docs/zh-CN](docs/zh-CN)

<details>
<summary><b>What Public 1.2.0 adds</b></summary>

An incremental update on Public 1.1.0, focused on lowering the barrier to getting started.

- **Readable config errors.** A mistake now names the file, the field path, what the field is for, the current value and the rule — and the source line number when that can be determined reliably. Aliased or nested keys don't get a guessed line number. `config.yml` and `messages.yml` are reported together, so you don't fix one only to discover the other.
- **`/nekocore lookup`.** Hold an item and ask for its correct English name; search by keyword; look up entity types. Approximate spelling works (`bred` → `BREAD`, `phanton` → `PHANTOM`). Chinese keywords are **not** translated — the command says so rather than inventing a mapping.
- **Three scenario presets** in `presets/`: `survival-only.yml`, `lobby-survival.yml`, `friends-server.yml`. Copied into `plugins/NekoCore/presets/` **only when missing** — never overwritten, never applied automatically. See [Presets](docs/en/PRESETS.md).
- **A five-line first-run guide**, printed only when `config.yml` is newly generated. Ordinary restarts don't repeat it.
- **`doctor` / `status`** keep their original module lines and append a `DISABLED` explanation.

**Upgrading:** stop the server, take a full backup, replace **only the JAR**, and keep your YAML and database. `config-version` stays **9**, `messages-version` stays **8**, SQLite schema stays **5** — existing configs are not rewritten. Versions 1.0 → 1.1 used the existing upgrader, and going straight to 1.2 still uses it.

Read [Installation](docs/en/INSTALLATION.md) first, and **don't overwrite an existing server's config with a preset**.

<sub>`/nekocore set` was deliberately not implemented this round: reload has asynchronous database and transaction preconditions, and an automatic YAML writer would need its own design and failure testing. Edit by hand → `config check` → `reload` is the supported path.</sub>

</details>

<details>
<summary><b>Building from source</b></summary>

You'll need JDK 25 and Maven 3.9+:

```text
mvn -B clean verify
```

The artifact is `target/NekoCore-1.2.0.jar`.

Shading leaves a second file, `original-NekoCore-1.2.0.jar`, in `target/` — **that's the pre-shade intermediate, don't distribute it**. CI runs the same command on pushes and pull requests.

</details>

<details>
<summary><b>Internal versions and what's verified</b></summary>

| Value | Current |
|---|---|
| `config-version` | 9 |
| `messages-version` | 8 |
| SQLite schema | 5 (V1→V5, **no V6**) |

**Don't change these by hand.** They decide how the plugin reads your data, and changing them won't fix anything.

**Stable IDs are preserved:** 109 product IDs, 20 daily task IDs, and the title IDs `mame` / `momo` / `sora`.

**What the automated tests cover:** config parsing and upgrades, database migration (including a fresh empty directory), Store and Bag transaction recovery, daily task event handling, degradation when optional components are missing, and command/permission mapping.

**What they don't cover:** real Paper entity and UI behaviour, coexistence with Citizens / Multiverse / WorldGuard, display-ownership conflicts with other TAB or chat plugins, inventory transactions during a real crash, shutdown under load, or upgrading from a real backup.

**This isn't a "should be fine" list — it's a "verify on a test server" list.** See [Verification](VERIFICATION.md).

</details>

---

## Credits and license

Project owner and maintainer: **秋山羽咲**.
Development and design assistance: **GPT-5.6 Sol (OpenAI)** and **DeepSeek-V41-Flash (深度求索)**.

That line records where assistance came from. It does not mean OpenAI or DeepSeek maintains, publishes, or endorses this project.

NekoCore is released under the **MIT License** — the full text is in [LICENSE](LICENSE). You're free to use, modify, and redistribute this code, including in closed-source projects, as long as you keep the copyright notice. Third-party component licenses remain separate.

> **Note on runtime language:** the messages shown in game are **Simplified Chinese**. English is provided as **documentation only** — there is no runtime language switching, so an English reader will still see Chinese text in game. This is stated up front so you know before installing, rather than finding out afterwards.
