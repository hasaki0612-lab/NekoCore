# Installation, Upgrades, Rollback, and Uninstall

[简体中文](../zh-CN/INSTALLATION.md) | [Back to README](../../README.en.md)

[Quick Start](QUICKSTART.md) covers "how do I get a brand-new server running at all". This page covers everything that comes **after a server already exists**: the full fresh-install walkthrough, moving up from an older version, what to do when something goes wrong, and how to take the plugin out cleanly if you decide you don't want it.

If this is your first server, read Quick Start first and come back here for the upgrade chapter. Either way, read the upgrade and backup material **before** you go live — learning how to restore in the middle of an incident is usually too late.

---

## Fresh install

### Requirements

| Requirement | Version |
| --- | --- |
| [Paper](https://papermc.io/downloads/paper/) | 26.2 |
| [Java](https://docs.oracle.com/en/java/javase/25/install/) | 25 |
| NekoCore | `NekoCore-1.2.0.jar` |

After installing Java, run `java -version` and confirm the first line says 25. Paper's first-start EULA flow is described in Quick Start.

### Steps

1. **Let Paper run once on its own.** With no plugins installed, start it until you see `Done`, then run `stop`. Paper generates its own base files, worlds, and `server.properties` this way, and that's the cleanest possible starting state.

2. **With the server stopped**, put `NekoCore-1.2.0.jar` into `plugins/`.

   Do **not** use `original-NekoCore-1.2.0.jar` — that's the pre-shade intermediate. If you also want PlaceholderAPI, Citizens, or Multiverse-Core, drop them in now as well; the order doesn't matter, and **going without them is perfectly fine too**.

3. **Start it.** On the first start, NekoCore will:

   - generate `config.yml` and `messages.yml` under `plugins/NekoCore/`;
   - create `nekocore.db` and run the V1→V5 migrations to build the tables;
   - print a short status summary.

   You should find a line in the log containing `NekoCore 1.2.0 ready`. The logging is **deliberately terse** and won't flood the console; if you see no `ERROR`, everything is normal.

4. **Stop the server**, then edit `plugins/NekoCore/config.yml`. At minimum, change `branding.server-name`; if your main world folder isn't called `world`, change `survival.world` as well.

5. **Restart** and verify in game. Try `/menu`, `/checkin`, `/sethome`, `/home`, `/coins`, `/store`, and `/bag`. Operators can also run `/nekocore status`.

If any of those steps is new territory, [Quick Start](QUICKSTART.md) explains them in more detail — where the `plugins` folder actually lives, which editor to use, why YAML indentation can't be improvised, and a pre-flight checklist for launch day.

### What you don't need for the first start

This deserves its own heading, because it's the step where people tend to over-prepare:

- You do **not** need PlaceholderAPI, Citizens, or Multiverse-Core;
- you do **not** need a GeoLite2 data file;
- you do **not** need a second world, minigame maps, display coordinates, or NPCs.

NekoCore's first start needs Paper and Java, nothing else. Whatever is missing gets quietly switched off, and everything else keeps working. Come back and configure a feature when you actually want it.

### One habit worth forming

**Don't overwrite config files or the JAR while the plugin is starting up.** It sounds like common sense, but on panel-hosted servers an "upload and it takes effect" workflow makes it very easy to swap a JAR while the server is still running. Get into the habit of typing `stop` before you touch any file.

---

## Public 1.1.0 → 1.2.0

Stop the server completely and back up the old JAR, the whole `plugins/NekoCore/` directory — the database included, along with its `-wal` and `-shm` files if they exist — and your world inventory data. Then replace only the JAR.

Your `config.yml`, `messages.yml` and database carry straight over. This release adds no YAML or database migration, so config stays at 9, messages at 8 and the schema at 5. The wording for the new lookup command is filled in from the built-in defaults in memory, which means your own text is never rewritten.

The three scenario presets are copied into `plugins/NekoCore/presets/` only when a file of that name isn't already there. They are never applied for you, and an existing preset is never overwritten. An ordinary restart also won't print the first-run guidance again — that shows up only when `config.yml` is generated fresh. If you'd like to start from one of the presets, see [Presets](PRESETS.md).

The historical 1.0.0 → 1.1.0 migration below is retained. A direct upgrade from 1.0.0 to 1.2.0 still uses that existing migration and backup naming.

## Public 1.0.0 → 1.1.0

Work on the **server machine**. Stop fully and back up the old JAR, the entire `plugins/NekoCore/` directory, and world inventory data; include WAL/SHM files with the database if present. Remove the old JAR and install only `NekoCore-1.2.0.jar`. **Do not replace or delete your config.yml, messages.yml or nekocore.db.**

First startup merges missing fields and updates only values exactly matching the Public 1.0 default snapshots. Administrator changes to server-name, products, task rules, TAB, Tips, worlds, slots, text, links and rewards survive. Original YAML files receive byte-for-byte `*.yml.pre-public-1.1.0-<random-id>.bak` backups. JoinInfo defaults enabled with all four links empty; AFK, Mascot and the leaderboard remain disabled by default.

Internal versions change: config **8 → 9**, messages **7 → 8**, to merge new JoinInfo, billboard and AFK end-message fields through the existing upgrader. SQLite schema **stays 5**, retaining V1→V5 without V6. All historical snapshots remain, with old deployment titles and coordinates generalised. Earlier private configurations may therefore retain those old values as custom settings; review them yourself rather than expecting the plugin to overwrite unknown deployment choices.

After starting, run `/nekocore config check`, `/nekocore status` and `/nekocore doctor`. Check `/tasks`, personal JoinInfo and existing data with a player; where enabled on a test server, also check leaderboard yaw, Mascot height, AFK grace and its end message. Unmodified defaults become 180-second Tips and 60-second AFK reward attempts. Roll back by restoring the **matching stopped-server backup of JAR, YAML, database and inventory data**, not the old JAR alone.

---

## Upgrading from NekoCore 1.4.0 / older versions

Public 1.0.0 originally used the complete private 1.4.0 baseline. Public 1.1.0 incrementally adds general improvements from 1.4.1–1.4.4 to that public repository without rewriting it. The version lines remain independent: private 1.4.4 is not Public 1.1.0. Test backup restoration and data upgrades; a shared baseline alone is not proof of low risk.

### Steps

1. **Get players offline, then run `stop` from the console and wait for the Java process to exit completely.**

   Don't assume the server is down just because the console stopped responding. Confirm the process is really gone (Task Manager, `ps`, or a "stopped" indicator on your panel).

2. **Take a complete backup.** At minimum it should include:
   - the old NekoCore JAR;
   - the entire `plugins/NekoCore/` directory;
   - the world playerdata that holds inventories, plus your Multiverse-Inventories data if you use it.

3. **⚠ About `.db-wal` and `.db-shm`.**

   If `nekocore.db-wal` or `nekocore.db-shm` exists alongside the database in `plugins/NekoCore/`, they **must be backed up together with `nekocore.db`**.

   Copying only the `.db` can hand you an incomplete point in time — the most recently committed transactions may still be sitting in the WAL file. **Never copy just the `.db` while the server is running.**

4. **Move the old JAR out of `plugins/`** and put only `NekoCore-1.2.0.jar` in. **Keep the existing `plugins/NekoCore/` directory** — your configuration and database live there, and carrying them forward is the whole point.

5. **Start the server fully** and watch the log for:
   - the configuration-upgrade backup notice;
   - the V1→V5 migration records (on an older database, only the missing versions run);
   - `NekoCore 1.2.0 ready`.

6. **Run `/nekocore status`** and confirm the schema reads 5 and every module reports a sensible state.

7. **Check player data item by item.** Walk this list:

   - [ ] Coin balance
   - [ ] Accumulated experience and level
   - [ ] Home coordinates in each world
   - [ ] Check-in history (claimed today or not)
   - [ ] Privacy toggle state
   - [ ] Owned titles and the currently equipped title
   - [ ] Items in the Bag
   - [ ] Daily store quotas
   - [ ] Enchantment book batch and purchase counts
   - [ ] Transaction logs
   - [ ] Playtime, first join and last join timestamps
   - [ ] Daily task progress
   - [ ] This week's coin income

### About the version numbers

**The Public version number 1.1.0 does not reset the internal versions.** In other words:

| Internal version | Value |
| --- | --- |
| `config-version` | 9 |
| `messages-version` | 8 |
| SQLite `user_version` (schema) | 5 |
| Migrations | V1, V2, V3, V4, V5 |

**There is no V6 in this release.** If you see a reference to V6 in an older database or in some other piece of documentation, it isn't correct.

**Why these numbers can't be moved:** they determine how the plugin reads your data. Changing `config-version` from 8 to 7 doesn't "fix" anything — it just makes the plugin interpret your files by the wrong set of rules.

### What happens when the data is newer than the plugin

If your database schema version is **higher than 5** — say you previously ran a newer NekoCore build and have now come back to this one — the plugin will **refuse to open the database**.

That is protection, not a malfunction. Writing an older plugin into a newer data structure can silently corrupt player data; refusing to start with a clear error is by far the better outcome.

### New fields in an old configuration

An older `config.yml` won't contain the gates that the Public release added (such as `position-configured`). NekoCore handles that by **falling back to the earlier behaviour** rather than failing because a field is absent.

A freshly generated Public configuration, on the other hand, uses safe defaults — those new gates are `false` out of the box.

**A suggestion:** after upgrading, compare the newly generated default file against your old one **field by field** and see whether any new key is worth adopting. Don't throw away customisations you've built up over months, and don't ignore the new fields just because "the file still loads".

### One boundary to be honest about

Automated tests can only cover so much of the upgrade-from-a-real-backup story. The database layer, the configuration upgrade path, and the plugin contract all have test coverage (including a fresh-install scenario that starts from a completely empty directory) — but *your specific server, with your specific plugin combination and your specific historical data,* still deserves one manual pass.

Upgrading on a test server first is the least stressful way to do it.

---

## Rollback

**You cannot roll back by swapping only the old JAR back in.** That is the single most important sentence on this page.

The reason is simple: the newer NekoCore may already have changed your database (running migrations, creating new tables). Putting the old JAR back by itself leaves it facing a structure it doesn't recognise.

### The correct way to roll back

1. **Stop the server** and wait for the process to exit completely.
2. Restore four things **from the same point in time**:
   - the old JAR
   - the old configuration (`config.yml`, `messages.yml`)
   - the old database (including WAL/SHM)
   - the player inventory data involved in Bag and Store operations (world playerdata, MVI data)
3. **Verify on a copy first** — confirm it starts correctly and the data is intact — before you touch the live server.

### The most common rollback mistakes

| Mistake | What it causes |
| --- | --- |
| Swapping the JAR without the database | The old plugin reads a newer schema; it may refuse to start or write bad data |
| Swapping the database without the configuration | Fields in the configuration no longer line up with the database structure |
| Swapping database and configuration but ignoring inventories | Bag/Store items and database records disagree, producing duplication or loss |
| Restoring only `nekocore.db` and missing the WAL | You get an earlier, possibly incomplete point in time |

**A configuration backup is not a database backup, and a database backup is not an item backup.** All three have to come from the same point in time.

---

## Uninstall

1. **Stop the server** and wait for the process to exit completely.
2. Move `plugins/NekoCore-1.2.0.jar` out of `plugins/`.
3. **Keeping the `plugins/NekoCore/` directory is recommended.** It causes no harm once the plugin is gone, and if you ever come back, your configuration and player data are still sitting there.

### What happens after removal

Removing the plugin does **not** automatically undo any external effect it already produced:

- items already delivered into player inventories are still there;
- coins already credited to player accounts don't vanish (if those values were recorded by another plugin);
- display state saved by other plugins (TAB contents, chat prefixes) may linger.

**Deleting Citizens NPCs is also something you do inside Citizens.** NekoCore never creates, moves, or renames an NPC, so it won't delete one on your behalf either.

Walking the uninstall procedure once on a test server before you go live is worth the twenty minutes — especially if your server already runs other plugins that depend on NekoCore data.

---

## Related documentation

- **First server, hand-held tutorial** → [Quick Start](QUICKSTART.md)
- **Database, migrations, and the full backup-and-restore procedure** → [Database](DATABASE.md)
- **Which optional components to install when you upgrade** → [Dependencies](DEPENDENCIES.md)
- **A feature stopped working after an upgrade** → [Compatibility and troubleshooting](COMPATIBILITY.md)
- **What automated tests cover and what they don't** → [Release verification](../../VERIFICATION.md)
