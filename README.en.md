# NekoCore Public 1.0.0

[简体中文](README.md) | [English](README.en.md)

> A ready-to-play base plugin for small Paper servers.
> If you can install Paper and edit YAML, you can run this without touching Java.

---

## Where this project came from

NekoCore started as an attempt to install fewer plugins.

Profiles needed one plugin. Economy needed another. The menu, homes, check-ins, the store, TAB, and a handful of chat tips each wanted their own JAR. Eventually `plugins/` held a row of files that argued over the same display space, the configuration was scattered across eight folders, and player data had been split into pieces that no longer agreed with each other.

So those pieces were gradually pulled into one plugin: one profile system, one economy, one menu, one config directory, one database. That's what you're looking at.

What it grew into is a **base core you can run a small server on directly**. What your main world is called, what your server is named, how much the store charges, how much a daily task pays, whether the AFK pool exists at all — all of that lives in `config.yml` and `messages.yml`. Anything that would require guessing about *your* server — your other worlds, your coordinates, your geolocation data — ships switched off and waits for you to decide.

---

## What players actually see

Here's what "ready to play on first start" means in practice. This is a description of the player experience, not a feature list.

**The moment they join.** A welcome title fades in with their name and your server's name. The TAB list fills in: their coordinates and current TPS on top, online count, their coin balance, UTC+8 time, and server uptime below. Every few minutes a tip scrolls through chat telling them where `/menu` is, that beds can serve as homes, and how to check in.

**Opening `/menu`.** A five-row server panel. Players see their profile card (level, experience, coins, playtime, first join date), the daily tasks, the check-in, a "show my region" privacy toggle, and buttons to travel to other areas. Modules you haven't enabled don't leave gaps — the remaining buttons re-center, so what a player sees is exactly what your server currently offers.

**Checking in.** Once per calendar day, not once per 24 hours. If they haven't claimed it, they get a reminder on join. Claiming awards a little money and experience, and a few small white sparks rise off them. The timezone is yours to configure; it defaults to Beijing time.

**Daily tasks.** Every day, three tasks are drawn from each of the easy, normal, and hard pools — nine total, shared by everyone on the server, with each player's progress and rewards tracked separately. They aren't "kill 100 mobs" grind. They're closer to "shear a sheep today," "be online for 20 minutes," "bake a cake." Finishing one gets you a slightly cheerful message in chat.

**The store.** Eight categories and 109 starter products: seeds, food, iron and diamond gear, tools, redstone, building materials, adventure supplies, plus a rotating enchantment book section. Right-click to buy, left-click to sell. Quantities come from presets or from a number typed into chat. Daily buy and sell limits keep someone from converting a mob farm directly into coins.

**Homes and beds.** `/sethome` stores per world, so a main-world home, a Nether home, and an End home coexist without overwriting each other. When a player successfully lies down in a bed, the plugin quietly records a home next to it — assuming there's somewhere safe to stand. On death, chat shows the coordinates, which makes running back less of a guess.

**Bag.** A portable storage container that follows the player, opened with `/bag`. Writable only in the main world by default; every other world, including your lobby and any world you add later, is read-only. That keeps it from fighting with per-world inventory rules. Slots unlock as the player levels.

**TPN.** `/tpn <player>` sends a teleport request that the other player accepts with `/yes` or declines with `/no`. It isn't "click and appear" — the person being visited gets a say. Requests expire, and the sender has a cooldown.

**Titles.** Three tiers — Yuki, Momo, and Neko — bought with coins. They change the title prefix, the name tag, and the chat prefix, and they carry small perks: automatic check-in, safe chat colors, a shorter teleport cooldown, and slightly more experience while AFK. The perks follow the highest tier a player has ever owned, so swapping the displayed title around never costs them anything.

**Off by default**, because NekoCore won't guess on your behalf: region display (GeoIP), the second survival world, the minigames area, the AFK pool, the weekly coin leaderboard, and the Citizens mascot. Their menu entries disappear along with them, so players never get a button that does nothing.

---

## Why "playable by default" and "never guesses" coexist

This is the part of the project that got the most thought, and it's worth explaining on its own.

Every feature that depends on an **external fact** — something the plugin cannot possibly know and only you can supply — sits behind a gate:

- What your second survival world is called, and whether it exists yet
- Which block of water the AFK pool actually is
- Where on the wall the weekly leaderboard should hang
- Where you put the GeoLite2 database you downloaded from MaxMind
- Which NPC number Citizens gave the mascot you created

When a plugin guesses these wrong, the result isn't "the feature didn't work." The result is teleporting players into the void, or spawning a floating sign at 0,0,0. So the design is: **two locks must both be open before anything happens.** The AFK pool needs `enabled: true` *and* `position-configured: true`. The leaderboard needs `enabled: true` *and* `position-configured: true`. With one lock open, the plugin quietly does nothing rather than falling back to a default coordinate.

Conversely, everything that doesn't depend on external facts — profiles, economy, levels, the menu, homes, check-ins, the store, Bag, TPN, daily tasks, tips, cleanup, TAB, join welcome — is on by default. The first time you start the server with the JAR in place, you already have something playable.

---

## Who this is for

- **First-time server owners.** You don't need to know Java, and you don't need to fully understand `config.yml` up front — walk through the quick start, get it running, then come back and explore.
- **Friend servers and small communities.** Ten to a few dozen players, no appetite for maintaining eight plugins, and no desire to write a script just to change one chat message.
- **Admins who want fewer base plugins.** If you're currently running a homes plugin, a check-in plugin, a menu plugin, a shop plugin, and a TAB plugin, NekoCore is roughly trying to be all of them at once.
- **People who care about data safety.** Store and Bag operations run through database transactions with recovery records. Item duplication and loss during a crash window are handled deliberately, not left to luck.

## Who might be better served elsewhere

This section is here on purpose, because the wrong tool costs more than no tool.

- **Servers that need Folia or cross-version support.** NekoCore targets Paper 26.2 on Java 25. Folia isn't claimed and there's no compatibility layer for older versions.
- **Large multi-server networks.** It's built for a single small server. Cross-server sync, network-wide economy, and multi-node shared databases are outside the design goals.
- **People who want to build custom gameplay.** NekoCore isn't a development framework; it's a finished core. There's a great deal you can configure, but it doesn't hand you an API for writing your own gameplay modules.
- **Servers with a mature existing stack.** If your Essentials + Vault + specialised plugin setup is stable and your players know it, adopting NekoCore brings migration cost more than it brings benefit.

---

## Start in five minutes

1. Install [Java 25](https://docs.oracle.com/en/java/javase/25/install/) and download [Paper 26.2](https://papermc.io/downloads/paper/).
2. Start Paper once, accept the Mojang EULA in `eula.txt`, then stop it cleanly.
3. Drop `NekoCore-1.0.0.jar` into the server's `plugins/` folder and start it. You should see `NekoCore 1.0.0 ready` at the end of the log.
4. Stop the server. Open `plugins/NekoCore/config.yml` and change at least two things: `branding.server-name` to your server's name, and `survival.world` if your main world folder isn't called `world`.
5. Restart, join, and run `/menu`. Operators can also try `/nekocore status` and `/nekocore config check`.

If this is your first time running a server, [Quick Start](docs/en/QUICKSTART.md) goes much slower — where the `plugins` folder actually is, why YAML indentation matters, when to stop the server, and how to confirm the plugin really loaded. Start there.

> One habit worth forming now: stop the server normally when you upgrade the plugin, change databases, or add a world plugin. Bukkit's `/reload` and hot-unloading plugins don't make NekoCore better; they make it unpredictable.

---

## What the configuration looks like

NekoCore has exactly two config files, both in `plugins/NekoCore/`:

- **`config.yml`** — server behaviour. Worlds, economy numbers, menu layout, module switches.
- **`messages.yml`** — every string a player can see. Anything with a `{placeholder}` in it is meant to be edited.

Changing your server's name:

```yaml
branding:
  server-name: "Cat Cafe"
```

Every player-facing surface follows: TAB, the join welcome, GUI titles, tips, the mascot hologram, and the daily task named "Good morning, {server}!".

Changing the check-in reward:

```yaml
checkin:
  coins: 100
  exp: 50
```

You don't have to restart for this. `/nekocore config check` tells you whether the configuration is valid without applying anything; once you're satisfied, `/nekocore reload` switches it over. Reloads are atomic — every file parses and validates first, then the whole set swaps at once. If a field is wrong, the server keeps running on the last valid configuration and tells you which line was the problem and what it expected. A broken config won't take your server down.

Not sure where to start with all the fields? [Configuration](docs/en/CONFIGURATION.md) explains what each module actually does, and [Recipes](docs/en/RECIPES.md) is organised as "I want to…".

---

## Where the data lives

Everything is in `plugins/NekoCore/nekocore.db`, a single SQLite database: player profiles, coins and experience, home coordinates, check-in history, titles, Bag contents, store quotas, transaction logs, daily task progress, and weekly coin earnings.

Three things matter when you back it up:

1. **Stop the server first** and wait for the Java process to actually exit before copying anything.
2. If `nekocore.db-wal` and `nekocore.db-shm` are also present, **copy them too**. Copying only the `.db` can give you an incomplete point in time.
3. If you run per-world inventories (Multiverse-Inventories, for instance), the item data involved in Bag and Store operations needs to come from the **same point in time** as the database. Otherwise things won't line up after a restore.

Don't edit the tables by hand with a SQLite browser while the server is running. It's fast, and it also steps directly on the assumptions the transaction recovery logic depends on. [Database](docs/en/DATABASE.md) has the full backup and restore procedure.

---

## Where to look when something's wrong

In this order, three steps usually finds it:

1. **Read the startup log.** A healthy start has a line containing `NekoCore 1.0.0 ready`, preceded by a short summary of the schema, core modules, and optional modules. It deliberately doesn't dump stack traces — turn on `advanced.debug` only when you want to dig.
2. **`/nekocore status`** (or `/nekocore doctor`, which is identical). It lists the NekoCore, Paper, and Java versions, the database schema, and the state of Store, Bag, Tasks, GeoIP, Mascot, AFK, Leaderboard, and PlaceholderAPI. Nine times out of ten, "feature X isn't working" is answered on that screen.
3. **`/nekocore config check`** if you've edited any YAML, to confirm the configuration itself is legal.

The reasons modules go quiet are fairly concentrated: the menu entry is gone because the module is off or its world isn't loaded, only one of the two locks is open, or another plugin is writing to the same display surface. [Compatibility and troubleshooting](docs/en/COMPATIBILITY.md) turns these into questions with ordered steps.

---

## Requirements and integrations

Only two hard requirements on the server side:

| Requirement | Version |
| --- | --- |
| [Paper](https://papermc.io/downloads/paper/) | 26.2 (API build 129 stable) |
| [Java](https://docs.oracle.com/en/java/javase/25/install/) | 25 |

SQLite JDBC and the GeoIP reader are shaded into the JAR, so there's nothing extra to download for those.

Optional third-party components are only needed when you want the matching feature:

- **PlaceholderAPI** — when you want other plugins to read NekoCore data, e.g. showing `%nekocore_coins%` in a different TAB plugin.
- **Citizens** — only for the built-in mascot interaction module.
- **Multiverse-Core** — when you want world routing through `mvtp {player} {world}`. Without it, a loaded main world safely falls back to the Paper spawn point.
- **GeoLite2 City data** — only for region display, and you fetch it from MaxMind under their terms. There is **no** MMDB file in the JAR or in this repository.

WorldGuard is not a NekoCore dependency. It can coexist, but that combination isn't covered by the automated tests.

[Dependencies](docs/en/DEPENDENCIES.md) covers what each component is for, official download links, tested versions, and installation steps. It also clears up one common confusion: **the Mascot is a module built into NekoCore, not a third-party plugin. Citizens is what provides the NPC entity itself.**

---

## Documentation

If you're new here, read them in this order: quick start → recipes → configuration.

| English | 简体中文 | What it covers |
| --- | --- | --- |
| [Quick start](docs/en/QUICKSTART.md) | [快速开始](docs/zh-CN/QUICKSTART.md) | Hand-held first server, with a pre-flight checklist |
| [Deployment commands](docs/en/DEPLOY.md) | [部署命令手册](docs/zh-CN/DEPLOY.md) | **Copy-paste commands**: scp uploads, ssh login, screen/systemd, backups, troubleshooting |
| [Installation](docs/en/INSTALLATION.md) | [安装与升级](docs/zh-CN/INSTALLATION.md) | Fresh install, upgrading from 1.4.0, rollback, uninstall |
| [Configuration](docs/en/CONFIGURATION.md) | [配置手册](docs/zh-CN/CONFIGURATION.md) | What each module does, how to fill in the fields |
| [Recipes](docs/en/RECIPES.md) | [配方手册](docs/zh-CN/RECIPES.md) | "I want to…" — shortest answer plus YAML |
| [Dependencies](docs/en/DEPENDENCIES.md) | [依赖说明](docs/zh-CN/DEPENDENCIES.md) | Every third-party component and its official source |
| [Commands](docs/en/COMMANDS.md) | [命令与权限](docs/zh-CN/COMMANDS.md) | Player commands, admin commands, permission nodes |
| [Economy](docs/en/ECONOMY.md) | [经济与商店](docs/zh-CN/ECONOMY.md) | Where coins come from and go, how the store is priced |
| [Daily tasks](docs/en/DAILY-TASKS.md) | [每日任务](docs/zh-CN/DAILY-TASKS.md) | How each of the 20 tasks is completed |
| [GeoIP](docs/en/GEOIP.md) | [GeoIP](docs/zh-CN/GEOIP.md) | Installing region data legally, and the privacy model |
| [Database](docs/en/DATABASE.md) | [数据库](docs/zh-CN/DATABASE.md) | SQLite, migrations, backup and recovery |
| [Compatibility](docs/en/COMPATIBILITY.md) | [兼容性与常见问题](docs/zh-CN/COMPATIBILITY.md) | "Why isn't it working" with ordered steps |

Also see [CHANGELOG](CHANGELOG.md), [Contributing](CONTRIBUTING.md), [Security](SECURITY.md), [Credits](CREDITS.md), [Third party](THIRD-PARTY.md), and [Verification](VERIFICATION.md).

---

## Building from source

You'll need JDK 25 and Maven 3.9+, then from the project root:

```text
mvn -B clean verify
```

The artifact is `target/NekoCore-1.0.0.jar`.

Shading leaves a second file, `original-NekoCore-1.0.0.jar`, in `target/`. That's the pre-shade intermediate — **don't distribute it**. CI runs the same command on pushes and pull requests.

---

## Credits and license

Project owner and maintainer: **秋山羽咲**.
Development and design assistance: **GPT-5.6 Sol (OpenAI)** and **DeepSeek-V41-Flash (深度求索)**.

That line records where assistance came from. It does not mean OpenAI or DeepSeek maintains, publishes, or endorses this project.

NekoCore is released under the **MIT License** — the full text is in [LICENSE](LICENSE). In short: you're free to use, modify, and redistribute this code, including in closed-source projects, as long as you keep the copyright notice. Third-party component licenses remain separate and don't replace the project license.
