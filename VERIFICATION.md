# Release verification

This document describes how a NekoCore release is checked, and — equally important — **what those checks do not prove.**

If you're an administrator rather than a developer, the section that matters most is [The manual compatibility boundary](#the-manual-compatibility-boundary) near the end. That's the list of things you should verify on your own server before trusting a release with your players' data.

---

## The automated gate

```text
mvn -B clean verify
```

Run with Java 25. A valid release reports **zero failures, zero errors, and zero skipped tests**, and produces `target/NekoCore-1.2.0.jar`.

### What this proves

### Public 1.2.0 local result

Verified 2026-10-02 with the same Java/Maven/Paper API toolchain below; no live server was launched and nothing was published.

```text
mvn -B clean verify
Tests run: 285, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The incoming source first passed its 262-test baseline. All existing tests remain; only the plugin/PAPI version assertions now expect 1.2.0. 23 additional tests cover source-line fallback (CRLF, quoted/flow keys, block scalars, aliases/merge/duplicate keys), human diagnostics/cap, aggregate material/entity/message failures, read-only checking, rejected reload retaining settings, missing-message fallback, lookup permissions/held/empty/console/search/limit, complete preset drift/validation/no overwrite, and fresh-only guidance. Paper registries and enchantment access are mocked in offline loader tests; these do not prove live registry/item behavior.

JAR audit: **1,762 entries**, three complete presets, exactly V1→V5, no V6, no MMDB/database/log/backups, no Paper API/server, JUnit/Mockito or test classes. Source comparison against the supplied ZIP shows no original file deleted and unchanged database/migrations, task definitions, transaction/recovery services and migration snapshots. No new dependencies. Pattern scans found no credential/key/webhook or sensitive data files outside build intermediates; this is not a guarantee of detecting every possible secret.

The earlier 1.1.0 result below is retained as historical evidence.

Local verification of the Public 1.1.0 working copy on 2026-10-02 (no release was published):

```text
mvn -B clean verify
Tests run: 262, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Toolchain used: Amazon Corretto OpenJDK 25.0.4.1, Apache Maven 3.9.11, Paper API 26.2.build.129-stable.

Those 262 tests cover configuration parsing, validation and upgrade paths; database migration including a fresh install from an empty directory; store and Bag transaction recovery staging; daily task selection, progress, and event handling; the degradation behaviour when optional dependencies are absent (verified against mocks); command registration and permission mapping; and GUI construction.

**What each of those categories actually verifies, in plain terms:**

| Area | What the tests check |
| --- | --- |
| Configuration | That valid files parse into the expected settings, that invalid values are rejected with a message naming the YAML path, and that a rejected reload leaves the previous configuration intact |
| Upgrade paths | That historical `config.yml` and `messages.yml` snapshots still load, and that older databases migrate forward |
| Fresh install | That empty test directories generate config/messages and migrate a real SQLite database to schema 5; this does not observe an actual Paper startup log |
| Migrations | That V1→V5 runs in order on an empty database, and only the missing versions run on an existing one |
| Transactions | That each recovery stage lands in a determinate outcome — committed or rolled back — and that quota and coin changes aren't double-counted |
| Daily tasks | That each task advances from the right event, with the right target, and that the Happy Ghast exclusion holds |
| Degradation | That the plugin contract still holds with PlaceholderAPI, Citizens, Multiverse, or an MMDB absent |
| Commands | That commands are registered and mapped to the documented permission nodes |

### Public 1.1.0 additions

The original 244 tests are retained; the suite now has 262 tests. Added checks cover `/tasks` routing and permissions, FIXED/CENTER and yaw without duplicate TextDisplays, Mascot whole-block offset and no-Citizens/disabled lifecycle, delayed personal JoinInfo/hidden links/OPEN_URL/reload/quit, 180-second Tips and unavailable tasks, AFK duration/grace/end-once/silent cleanup, and config 8→9/messages 7→8 merge with exact backups and custom-value preservation.

Existing TAB tests still cover its single global scheduler, cache-only updates, GeoIP session-cache use and ownership restoration. Database, commerce/recovery, stable task definitions, product/title IDs and PDC/Placeholder namespaces are untouched. SQLite remains schema 5 with exactly V1→V5.

### What it does not prove

It does not prove that the plugin behaves correctly on your server, with your plugin set, on your worlds. That boundary is described at the end of this document, and it isn't boilerplate.

---

## Inspecting the artifact

Beyond the test suite, a release is checked by hand in two ways.

### The JAR contents

A valid release JAR must contain:

- `plugin.yml`
- `config.yml`
- `messages.yml`
- Database migrations **V1 through V5**

It must **not** contain:

- Any MMDB file or database file
- JUnit, Mockito, or test classes
- Paper server classes (only the API is compiled against, and it's `provided`, so it isn't bundled)
- A `V6` migration

The Public 1.1.0 JAR has 1,751 entries. The audit found zero MMDB files, zero databases, zero SQLite data files, zero Paper server or API classes, zero JUnit, zero Mockito, and zero test classes.

### Secret scanning

The tracked source is scanned for credentials, private addresses, production UUIDs and player data, `.db`, WAL/SHM files, `.mmdb`, logs, private backups, SSH material, API keys, webhooks, and provider-specific branding.

For Public 1.1.0, the explicit source scan includes tracked and new files, hidden `.github` files and all historical YAML snapshots, excluding `.git` and `target`. Reviewed results: no private deployment branding or coordinates remain in shipped YAML, no detected credentials/private keys/webhooks, and no sensitive files (database, WAL, SHM, MMDB, logs, keys, private backups). This is a pattern-and-manual review, not proof that every possible secret is detectable.

Two notes on interpreting that:

- **Matches must be reviewed by hand.** Words like "token" appear legitimately in documentation that forbids committing credentials, and a scan hit isn't automatically a leak.
- **There are reviewed matches, not a raw zero-hit scan:** DeepSeek appears in existing development credits, the negative branding test, and this scan explanation — never in deployment text. The negative test intentionally lists banned terms. Legacy `land.momo` namespaces and the stable Momo title preset stay for compatibility. IPv4-shaped matches are documentation-reserved addresses, public DNS/loopback/private-range test fixtures, and dependency/toolchain version numbers — not private server addresses. No production player data or credentials were found.

An automated contract test checks default resources and every historical YAML snapshot for private-server branding and routable IP literals. Historical deployment titles and coordinates were generalised without deleting migration files; Public 1.0 snapshots remain exact for its upgrade path.

---

## Fresh-install scenario

On a disposable Paper 26.2 server with Java 25 and **no optional plugins installed**:

1. Place only the release JAR in `plugins/` and start the server normally.
2. Confirm `plugins/NekoCore/config.yml`, `messages.yml`, and `nekocore.db` are created.
3. Confirm `PRAGMA user_version` reaches **5** via V1→V5, and that startup produces no ERROR.
4. Join and run `/menu`, `/tasks`, `/coins`, `/sethome`, `/home`, `/checkin`, `/store`, `/bag`, and `/tpn` with suitable players.
5. Confirm GeoIP, Mascot, the AFK destination, the second world, minigames, and the leaderboard all remain disabled — and that **nothing is displayed at 0,0,0**.
6. Stop normally and confirm a clean shutdown, including the database executor shutting down properly.

This scenario has an automated counterpart: `PublicFreshInstallTest` generates config and messages from a completely empty `plugins/NekoCore/` directory, creates the SQLite database, runs V1→V5, and confirms the core commands are present in the plugin contract with zero severe log entries — with no MMDB, no second world, no Mascot, and no leaderboard coordinates configured.

**The automated test is not a replacement for the manual run.** It doesn't start a real server, so it can't observe a real log, real GUI behaviour, or a real shutdown.

---

## Optional dependency matrix

Startup is repeated **without** each of PlaceholderAPI, Citizens, an MMDB, Multiverse, and WorldGuard. The core must remain enabled in every combination.

Each integration is then tested on its own:

| Integration | What to verify |
| --- | --- |
| PlaceholderAPI | Placeholder keys resolve, and `%nekocore_display_prefix%` returns the correct active prefix |
| Citizens | A pre-created NPC with a matching `npc-id` responds to right-clicks; the hologram appears; a missing NPC logs one warning and doesn't spawn a substitute |
| GeoLite2 City | A legally obtained MMDB resolves regions at province and country granularity, and a corrupt file disables only GeoIP |
| Multiverse-Core | Configured `mvtp` world routing works, and a loaded primary world falls back to the Paper spawn without it |

Degradation behaviour also has automated coverage: `NekoExpansionTest`, `MascotServiceTest`, `GeoIpServiceTest`, and `DestinationServiceTest` verify that the core path stays available when each optional dependency is missing.

**WorldGuard is not in `depend` or `softdepend`**, and coexistence with it is a manual check rather than an automated one.

---

## Upgrade and recovery

Make a full stopped-server backup first. Then test:

- An existing **schema 1–4** database, migrating forward to 5.
- An existing **schema 5** database, opening without migration.
- A database whose schema is **higher than 5**, which must be **refused** rather than opened.

After upgrading, verify these survive intact:

profiles, coins, experience and levels, homes in every world, check-in history, owned titles, the equipped title, Bag contents, store quotas, enchantment batches, transaction logs, playtime, daily rotation and per-player progress, and weekly coin records.

### Interruption testing

Interrupt a disposable store or Bag transaction at **each recovery stage** and verify that no duplication and no loss results:

- Interrupt after preparation, before delivery — expect a rollback.
- Interrupt after delivery, before commit — expect recovery to commit.
- Interrupt in the uncertain window — expect **quarantine**, not a guess.

Then confirm the quarantined player can still play normally and simply can't trade until it's resolved, and that their item records were left untouched.

---

## The manual compatibility boundary

**Automated tests do not prove every live-server combination.** They run against mocks and a build environment, not against a running Paper server with real players, real inventories, and real third-party plugins.

The following still require hands-on verification on a Paper 26.2 test server before a release is considered properly validated:

- **Paper startup and shutdown**, including `stop` under load.
- **Citizens** — NPC spawning, right-click interaction, hologram placement on a real entity.
- **Multiverse-Core** and **Multiverse-Inventories** — world routing, and how per-world inventories interact with Bag.
- **WorldGuard coexistence** — whether region protection interferes with teleports, homes, or bed homes.
- **Display ownership** — TAB, name tags, and chat prefixes alongside other plugins that write to the same surfaces.
- **TextDisplay lifecycle** — that holograms are created where expected and cleaned up when they should be.
- **Beds and death** — bed home recording, safe-landing detection, and death coordinates on real terrain.
- **Player inventories** — store and Bag operations under real conditions, including disconnection, death, and a full inventory.
- **Shutdown under load** and, specifically, transactions that are uncertain at shutdown, which are deliberately left for next-login recovery.
- **Upgrading from a real backup** rather than from a synthetic fixture.

**Do not claim these are covered by the unit tests.** They aren't, and treating them as if they were is exactly the failure mode this section exists to prevent.

---

## Reporting the result of a manual run

If you verify a release on your own server — whether it passed or something went wrong — that information is genuinely useful. Please include:

- NekoCore, Paper, and Java versions
- Which optional plugins were installed
- What you tested, and what you observed
- Any log output, **with player IPs, UUIDs, and server addresses redacted** (see [SECURITY.md](SECURITY.md) for how)

A successful report from an unusual plugin combination is as valuable as a failure report, because it extends the set of configurations the project can honestly claim to have seen.
