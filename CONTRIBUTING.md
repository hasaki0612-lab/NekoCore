# Contributing

Thanks for considering a contribution. This document covers how to get set up, what the project cares about, and what a good pull request looks like.

If something here is unclear or you think a rule doesn't apply to your change, open an issue and ask. It's better to have that conversation before you write the code than after.

---

## The one thing to keep in mind

NekoCore has a product goal that most of its design decisions come back to:

> **Someone who can run Paper and edit YAML must be able to use the feature without reading Java.**

That's the target user. Not a developer, not someone comfortable editing a JAR. So when you're weighing two ways to build something, the one that an administrator can configure, validate, and recover from wins — even if it's more work to implement.

In practice that means preferring safe defaults, clear validation with actionable error messages, a GUI affordance over a hidden command, and documentation over a code-only control. A feature that only its author can configure isn't finished.

---

## Development environment

| Tool | Version |
| --- | --- |
| JDK | 25 |
| Maven | 3.9+ |

Nothing else is required — SQLite and the GeoIP reader are resolved by Maven, and the tests use mocks rather than a running server.

### Clone and build

```text
git clone <repository-url>
cd NekoCore-Public-1.0.0
mvn -B clean verify
```

The first build downloads dependencies, so expect it to take a little longer than subsequent ones.

### Run the tests

`mvn -B clean verify` runs the full suite. A healthy build reports zero failures, zero errors, and zero skipped tests. For a faster loop while working on one area:

```text
mvn -B test -Dtest=AfkPoolServiceTest
```

### Where things live

```text
src/main/java/land/momo/nekocore/
├── command/      command handling, permissions, tab completion
├── config/       configuration parsing, validation, settings records
├── data/         SQLite repositories, migrations, services
├── gui/          menu and inventory interfaces
├── integration/  optional hooks (PlaceholderAPI, GeoIP, TAB, name tags)
├── model/        small value types and pure logic
├── service/      feature services and their event listeners
└── task/         daily task definitions and tracking helpers

src/main/resources/
├── config.yml, messages.yml, plugin.yml
├── db/migration/      V1..V5 SQL
└── migration/         historical config/messages snapshots
```

Tests mirror that structure under `src/test/java/`. Configuration resources are also covered by contract tests, so changing a default in `config.yml` can break a test — that's intentional, not an obstacle.

---

## What the project protects

These constraints exist because breaking them breaks real servers. They aren't style preferences.

### Stable identifiers

**Do not rename or remove any of these:**

- Commands, or the `nekocore.*` permission nodes.
- PDC keys and PlaceholderAPI keys.
- The **109** store product IDs.
- The **20** daily task IDs.
- The title IDs `mame`, `momo`, and `sora`.

These strings are written into databases that belong to other people. A renamed product ID doesn't migrate — it creates a new product with fresh quotas while the old records become orphans. A renamed task ID severs existing progress. There is no safe way to do this quietly, so don't.

Display names, prefixes, prices, descriptions, and messages are all fair game. Those are meant to be edited. The identifier underneath them is not.

### Versions and migrations

**Do not reset or renumber these:**

| Value | Current |
| --- | --- |
| `config-version` | 8 |
| `messages-version` | 7 |
| SQLite `user_version` | 5 |
| Migrations | V1, V2, V3, V4, V5 |

Note: **there is no database V6.** If you're adding one, it becomes V6, and that's a decision worth discussing in an issue first.

One thing that trips people up: the JAR also contains `src/main/resources/migration/vN-config.yml` and `vN-messages.yml` snapshots, which go up to **v6** and **v7**. Those are historical *config format* snapshots used to upgrade old config files. They are unrelated to database migrations — a `v6-config.yml` file existing does not mean a V6 migration exists.

Add a migration only when the stored structure genuinely changes — a new table, a new column, a new index. Changing a default value or a message string is not a migration.

Migrations must be additive and must work on an existing database, not just a fresh one. A migration that only works from empty isn't a migration.

### Database filename changes

The database filename cannot be changed through `/nekocore reload` — the plugin refuses and requires a restart, because the file handle is opened at startup. Keep it that way rather than trying to make reload swap it.

### Threading

- Keep **Bukkit/Paper API calls on the main thread.** Scheduling, entities, inventories, players, worlds — all main thread.
- Keep **SQLite work on the store executor.** Never block the main thread on JDBC.
- **Don't let Bukkit objects cross that boundary.** Pass identifiers (`UUID`, `String`, plain records), not `Player` or `ItemStack`.
- Update in-memory profile state **after** a transaction commits, not before.

### Transaction recovery

Store and Bag operations rely on the reality that a player's inventory and the database **cannot be a single atomic transaction**. The recovery machinery exists to make a crash land in one determinate state rather than a torn one.

If you touch anything in that path, preserve these properties:

- Every stage is recorded before the work it describes happens.
- Recovery either completes or rolls back — never guesses.
- An ambiguous state is **quarantined, not replayed**. Don't refund, don't grant items, and never overwrite a Multiverse world inventory to "fix" a mismatch.
- A quarantined player is not banned. They keep playing; they just can't trade until it's resolved.
- Quota consumption and coin changes that happen during preparation must not be double-counted when a transaction is later committed by the recovery path.

`ShutdownRetryTest`, `CommerceRepositoryTest`, `InventoryExchangeServiceTest`, and `Upgrade111Test` are the ones to read and extend.

---

## Working on the code

### Configuration changes

If you add a field, it needs more than a default:

1. **Parse and validate it.** Reject bad values with a message naming the YAML path and the expected form or range. A silently ignored field is worse than a rejected reload.
2. **Choose the safe default.** Anything involving coordinates, external data, or another plugin's objects defaults to off. This is a firm rule — see the two-lock pattern used by `afk-pool` and `weekly-coin-leaderboard`.
3. **Decide reload or restart** and make sure it actually behaves that way.
4. **Verify atomicity.** A failure partway through validation must leave the previous configuration untouched. Build the complete new object, then swap.
5. **Add a contract test** covering the default in `src/main/resources/config.yml`.

If you're adding a material or entity list, validate it against Paper 26.2 and — where the value is user-typed — provide a near-name suggestion. The suggestion logic uses Levenshtein distance and only fires within a sensible threshold.

### Messages

All player-facing text belongs in `messages.yml`. No hard-coded strings in Java.

Keep the tone consistent with the existing file: warm and plain, occasionally light. **Never playful** in messages about storage errors, corruption, recovery, or anything a player needs to act on urgently. If a message tells someone their items are at risk, it should sound like it.

### Tests

Add or update tests for behaviour you change. The existing suite is worth reading before you write new ones — several tests encode intent that isn't obvious from the code, particularly around optional-dependency degradation and transaction stages.

Mock external plugins rather than requiring them. A test that needs a real Citizens install won't run in CI, which means it won't run at all.

---

## Documentation

**Documentation ships with the change, in both languages.** This isn't optional bookkeeping — for a project whose whole premise is "figure it out from the docs," an undocumented field is an unfinished field.

Files that may need updating:

| Change | Update |
| --- | --- |
| A new or changed config field | `docs/zh-CN/CONFIGURATION.md` **and** `docs/en/CONFIGURATION.md` |
| A new or changed command or permission | `docs/zh-CN/COMMANDS.md` **and** `docs/en/COMMANDS.md` |
| A new goal-oriented recipe | `docs/zh-CN/RECIPES.md` **and** `docs/en/RECIPES.md` |
| Anything a user notices | `CHANGELOG.md` |
| Default values | The tables in both `CONFIGURATION.md` files |

The two language versions must agree on **facts**: paths, commands, permissions, defaults, version numbers, and safety warnings. They don't need to be sentence-for-sentence translations — natural technical English and natural Chinese read differently, and forcing them into the same shape makes both worse. But if the Chinese says a default is `300` and the English says `600`, that's a bug.

All relative Markdown links must resolve. Cross-document links between `docs/zh-CN/` and `docs/en/` are relative (`../en/NAME.md`), and links to repository-root files use `../../`.

---

## Testing configuration compatibility

Configuration compatibility is the easiest thing to break accidentally, and the most annoying for an administrator to discover. Before opening a PR that touches configuration:

1. **Fresh install.** Delete `plugins/NekoCore/` entirely, start the server, and confirm `config.yml`, `messages.yml`, and `nekocore.db` are generated and the schema reaches 5 through V1→V5 with no ERROR in the log.
2. **Old configuration.** Take a `config.yml` from before your change and start with it. Confirm the plugin reads it with the previous behaviour preserved rather than silently disabling a feature or refusing to load. `PublicFreshInstallTest` and `ConfigUpgraderTest` cover parts of this; extend them if your change adds a case.
3. **Invalid values.** Break one field deliberately — a bad material, an out-of-range number, a missing placeholder — and confirm the reload fails with a helpful message while the server keeps running on the old config.
4. **Both lock states.** If your feature gained a gate, test all four combinations of the two booleans.

---

## Pull requests

A good description answers these:

- **What does this change, and why?** Link the issue if there is one.
- **Does it affect configuration compatibility?** New fields, renamed fields, changed defaults, changed validation.
- **Does it affect migrations or schema?** If yes, say what happens to an existing database.
- **What did you test?** The command you ran, and the result. If you tested manually on a Paper server, describe the setup — which optional plugins were present, what you clicked, what you saw.
- **What did you *not* test?** Being explicit about the gap is far more useful than implying full coverage.
- **Does documentation need updating?** Confirm both language versions are done.

Keep pull requests focused. One logical change per PR is much easier to review and to revert than a bundle.

### Before you push

```text
mvn -B clean verify
```

Zero failures, zero errors, zero skipped. Then inspect the shaded JAR and confirm it contains `plugin.yml`, `config.yml`, `messages.yml`, and migrations V1 through V5 — and does **not** contain an MMDB, a database, JUnit, Mockito, test classes, Paper server classes, or a `V6` migration.

---

## What must never be committed

Add these to `.gitignore` if you generate them locally, and never force-add them:

**Player and production data**

- `nekocore.db`, `*.db-wal`, `*.db-shm`
- Server logs, `logs/`
- Player data directories, world folders
- Production configuration backups

**Licensed or licensed-adjacent data**

- `*.mmdb` and any GeoLite2 archive
- MaxMind account credentials or license keys

**Credentials and machine-specific material**

- SSH keys, tokens, webhooks, API keys
- `.env` files and anything else holding secrets
- Absolute local paths, private server addresses, or vendor branding

**Build output**

- `target/` and `original-*.jar`
- IDE project files

If you think you've committed a secret, **treat it as compromised** — rotate it first, then clean the history. Removing the commit is not enough once it's been pushed.

---

## Reporting bugs and security issues

Bugs go in the normal issue tracker. Include your NekoCore, Paper, and Java versions, the relevant configuration, what you expected, what happened, and the smallest set of steps that reproduces it.

Security issues go through the private channel described in [SECURITY.md](SECURITY.md). Don't open a public issue for those. That document also covers what to redact and how.

---

## A closing note

NekoCore is maintained around a fairly specific idea of who it's for. If you're unsure whether a change fits, the question to ask is usually: *would an administrator who has never read the source be better off?* If the answer is yes, it probably belongs here — and if you're not sure, ask first. That's a welcome kind of question, not a bothersome one.
