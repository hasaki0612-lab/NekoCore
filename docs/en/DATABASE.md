# Database, migrations, and data safety

[简体中文](../zh-CN/DATABASE.md) | [Back to README](../../README.en.md)

This page covers NekoCore's data layer: where the data lives, what happens during an upgrade, how to back it up, and how to recover when something goes wrong.

**There is no playful tone on this page, and none is intended.** Data loss is the least reversible kind of accident a server can suffer, and it deserves the plainest wording available.

---

## Where the data lives

One SQLite file:

```text
plugins/NekoCore/nekocore.db
```

Two companion files may also appear in the same directory:

```text
plugins/NekoCore/nekocore.db-wal    write-ahead log
plugins/NekoCore/nekocore.db-shm    shared-memory index
```

**They are not leftovers, and they cannot be deleted on their own.** They are a normal part of SQLite's WAL mode. Recently committed transactions may still be sitting in `.db-wal`, not yet merged into the main file.

**A backup has to copy all three files together, from the same point in time.** Copying only the `.db` can give you a snapshot that is missing the most recent changes, which is the usual cause of a "backup that looked fine but restored a few hours short".

The database holds:

| Content | Notes |
| --- | --- |
| Player profiles | UUID, name, first join, last join, playtime |
| Economy | coin balance, total experience, level |
| Homes | coordinates, per world |
| Check-ins | daily check-in records |
| Titles | titles owned, and the one currently equipped |
| Bag | items held in the portable container |
| Store | daily buy and sell quotas, transaction log |
| Daily tasks | the server-wide rotation and each player's progress |
| Weekly coins | this week's earnings, feeding the weekly leaderboard |

**It contains player UUIDs and gameplay data, which makes it sensitive operational data.** Keep it out of public issues, Git repositories, release attachments, and test fixtures. When someone needs to help you debug, generate a minimal, redacted reproduction database instead.

---

## Versions and migrations

`PRAGMA user_version` for Public 1.0.0 is **5**. Migrations V1 through V5 are retained:

| Migration | Content |
| --- | --- |
| V1 | initial profile, economy, home and other base tables |
| V2 | daily check-ins |
| V3 | store, Bag, titles, and the structures behind safe trading |
| V4 | daily task rotation and progress |
| V5 | weekly coin earnings and the ranking index |

**There is no database V6 in this version.** The migration SQL files live inside the JAR. Do not delete or modify them.

> **Don't confuse database migrations with config snapshots.** The JAR also ships `src/main/resources/migration/vN-config.yml` and `vN-messages.yml` files, which are *historical snapshots* of the configuration format used to upgrade old config files. Those go up to `v6-config.yml` and `v7-config.yml`. They have nothing to do with the database schema — the database stops at V5, while the current config format is version 8. Seeing a file named `v6-config.yml` is expected and does not mean a V6 migration exists.

### How migrations run

- **A brand-new, empty directory:** V1 → V2 → V3 → V4 → V5, each executed inside a transaction.
- **An existing older database:** only the missing versions run. A schema 3 database runs V4 and V5.
- **A database above version 5:** **the plugin refuses to open the database.**

That last rule is protection, not a fault. An older plugin writing into a newer data structure can corrupt it silently. Refusing to open the file and saying why in the log is by far the better outcome.

### Transactions and recovery

SQLite is owned by **a single background executor thread**. Bukkit objects never cross that boundary, which is what keeps database work from colliding with your server's main thread.

The in-memory player profile is updated only after the transaction commits. That ordering is what makes "the game believes what the database holds" true.

**Store and Bag use recovery records**, and understanding them starts with one real constraint:

> A player's inventory and the database **cannot be a single atomic transaction**.

That is a limitation of Minecraft server architecture, not of NekoCore's implementation. A player's inventory lives in server memory and in world playerdata; the database is a separate world. Any crash in between — the server killed, the disk filling, a power cut — can produce "the item was handed over but nothing was recorded", or the reverse.

So each exchange is split into named stages (`BEFORE` → `UNCERTAIN` → `APPLIED`), and every stage leaves a record behind. At startup and on login, NekoCore looks for unfinished operations, reads what the evidence supports, and then **finishes in exactly one direction**:

- evidence that the items really were delivered → the exchange is marked complete;
- evidence that they were not → a full rollback, with coins and store quotas restored;
- **evidence that settles nothing** → the exchange is quarantined for manual reconciliation.

The third case deserves its own explanation. When an exchange is genuinely undecidable — the inventory matches neither the "before" nor the "after" snapshot, which usually means another plugin has touched it — NekoCore refuses to guess, and the record is **quarantined, never replayed**. The log notes that the item record needs manual review and that nothing was re-sent or overwritten; the player is told that one item record needs reconciling and that their item data has been preserved. **Nothing is refunded, no items are granted, and a Multiverse world inventory is never overwritten.** The player is not banned and can play normally; what they cannot do is start another NekoCore exchange or open the store and Bag until the record has been reconciled. That trade is deliberate. When something is ambiguous, one feature going temporarily unavailable beats replaying an operation nobody can be certain about. [Economy, Store, Bag, and Titles](ECONOMY.md) describes the player-visible side of it.

This is also why **you should not edit the database tables by hand**. Those pending and transaction tables look like clutter from an older design, and they are precisely what the recovery logic reads. Emptying them by hand blinds the recovery mechanism.

### Do not run a second instance

Never let **two server instances open the same database file at the same time**. SQLite's file locking is not a cross-process coordination mechanism. A multi-server network sharing one set of NekoCore data is outside the design goals.

---

## Backups

### The correct procedure

1. **Run `stop` from the console.**
2. **Wait for the Java process to exit completely.** Confirm it is gone from the process list, or that the panel reports stopped. A console that stops responding is not the same thing as a process that has exited.
3. **Copy the whole `plugins/NekoCore/` directory.** That carries `.db`, `.db-wal`, `.db-shm`, `config.yml`, and `messages.yml` with it in one pass, so nothing gets left behind.
4. **If you use Store or Bag**, also copy these **from the same point in time**:
   - world playerdata (player inventories)
   - Multiverse-Inventories data, if you run it
5. **Archive the copy somewhere other than the server.** A backup on the same disk is worth nothing when that disk fails.

### Why item data has to share a point in time with the database

Because Bag and Store operations straddle the two. Restore yesterday's database beside this morning's player inventories and you get:

- an item a player bought yesterday, where the database considers the sale settled and the coins spent, while the inventory comes from a moment when that purchase had not happened;
- or the opposite: the database's Bag contents list a set of items that are also still sitting in the inventory data.

**The database rollback and the item rollback have to be the same point in time.** That is why the procedure above keeps insisting on stopping the server, waiting for the process to exit, and copying everything together.

### Rehearse a restore now and then

**Confirming that the archive exists is not the same as confirming the backup works.**

Every so often, run a real drill: stand up a test server in a temporary directory, restore the backup into it, start it, join, and check that your players' coins, homes, and Bag contents are all there.

A drill is how you find out that a backup file was already corrupt, or that you missed a category of data — before the day you actually need it.

---

## Recovery and troubleshooting

### When something has gone wrong

1. **Copy the scene first.** Before you change anything, copy the current `plugins/NekoCore/` somewhere else in full. Worst case, you can still return to a state that is broken but analysable.
2. **Experiment only on the copy.**
3. **Keep the server offline** until you understand what happened.

### Never do this

| Practice | Why it fails |
| --- | --- |
| Delete migration SQL | a new database cannot be created, and the next migration on an old one fails |
| Edit `user_version` | it makes the plugin read your data under the wrong version assumptions |
| Clear the pending or transaction tables | it destroys the evidence the recovery logic reads, and can turn a recoverable state into an unrecoverable one |
| "Repair" the database with a GUI SQLite editor | those tools know nothing about NekoCore's transaction semantics and write inconsistent states easily |
| Swap JARs repeatedly to see what works | every start can trigger a migration, and without a backup that is dangerous |
| Replace the database file while the server is running | guaranteed corruption |

### Directions for common failures

**Disk full.** Stop the server first. SQLite writes fail when the disk is full and can leave an incomplete WAL behind. Free space, restart, and let the recovery logic work.

**Wrong file permissions.** Stop the server, fix read and write permissions on `plugins/NekoCore/`, then start it. On Linux, check whether the user running the server and the file owner are the same.

**The database is locked.** This usually means a second process has it open. Look for a leftover server process, or a SQLite browser sitting on the file.

**Schema version too high.** A newer NekoCore wrote this database and you have gone back to an older one. Restore the newer JAR together with its data, or return to the backup you took before the upgrade.

**Database corruption.** Stop the server, preserve the evidence, restore from a backup. **Do not attempt repairs on the original file** — keep it, and you can analyse it at any time.

### Restoring from an older version

All four parts have to come from the **same point in time**:

```text
JAR + configuration + database + item data
```

Any one of them from a different moment can produce a state that does not add up.

---

## Summary

If you take three sentences away from this page:

1. **Back up with the server stopped, and copy `.db`, `.db-wal`, `.db-shm`, and the item data together.**
2. **Restore from a single point in time, and verify it on a copy first.**
3. **Do not edit the database by hand — the recovery tables that look redundant are the ones protecting you.**

---

## Related documentation

- **The full upgrade and rollback procedure** → [Installation and upgrades](INSTALLATION.md)
- **A data-related feature is misbehaving** → [Compatibility and troubleshooting](COMPATIBILITY.md)
- **What the automated tests cover at release time** → [Release verification](../../VERIFICATION.md)
- **How to redact things when reporting a problem** → [Security policy](../../SECURITY.md)
