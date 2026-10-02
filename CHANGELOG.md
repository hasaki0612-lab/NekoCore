# Changelog

## NekoCore Public 1.2.0

Incremental usability work on the supplied Public 1.1.0 source, not a rewrite.

- Shared startup/check/reload diagnostics identify file, path, purpose, current value and validation rule. Reliable YAML source marks supply line numbers; ambiguous sources fall back without guessing. Common independent name/message issues aggregate with a 20-issue display cap; complex domain rules remain fail-first within their loader.
- Existing Material spelling suggestions remain. Admin `/nekocore lookup` supports a held item, English exact/prefix/substring/fuzzy search (10 max), console keywords and optional entity lookup, using the existing admin permission.
- Three full presets: survival-only, lobby-survival, friends-server. Copy missing preset files only; never overwrite or auto-apply. Core stays available, coordinate/external-data features stay gated. Tests track complete keys and non-scenario values against defaults.
- Five-line first-config guidance, no database flag or repeated restart banner. Stable doctor/status module lines retained, with a short Chinese DISABLED explanation appended.
- Existing bilingual guides are extended in place, including folded Chinese configuration chapters; no runtime i18n system or automatic YAML writer is added.
- Public artifact/PAPI version 1.2.0; config 9, messages 8, SQLite schema 5 unchanged. No new dependencies or V6; IDs/PDC/namespaces and Store/Bag recovery are preserved. Optional new lookup text falls back in memory without rewriting old YAML.
- Java 25 `mvn -B clean verify`: 285 tests, zero failures/errors/skips. All 262 existing tests retained; two version assertions updated only. Live Paper/inventory/plugin combinations still require the documented manual checks.

## NekoCore Public 1.1.0

Backward-compatible additions to Public 1.0.0, not a new architecture or a renumbering of the private release line.

### Added

- `/tasks` opens the existing Daily Tasks GUI; `nekocore.tasks` defaults to true.
- Personal, delayed, cache-only JoinInfo with configurable text and four administrator-provided OPEN_URL links; empty links stay hidden.
- Configurable AFK session-end duration message, sent once on normal online termination and suppressed on quit/reload/shutdown.

### Changed / fixed

- Weekly leaderboard defaults to `FIXED`, respects yaw, supports `CENTER`, and reuses its marked TextDisplay.
- Mascot overall Y offset defaults to 2.25, without moving NPCs or changing line spacing; disabled or missing-Citizens modules register no listener/task.
- Compact default TAB with `{server}`, simple separators and no redundant subtitle; cache-only refresh and ownership restore remain.
- Tips defaults to 180 seconds; the `/tasks` hint is skipped when Daily Tasks is disabled.
- AFK reward attempts default to 60 seconds, with warning/fallback for invalid intervals; detection, grace, reward probability and pools are preserved.
- Bilingual documentation updated in place. Historical migration snapshots retained with deployment-specific titles and coordinates generalised.

### Compatibility and verification

- Config 8 → 9; messages 7 → 8. Existing customised values survive the versioned merge with original-file backups.
- SQLite schema stays 5; V1→V5, stable IDs, namespace/PDC keys, and Store/Bag recovery are unchanged.
- No new dependencies. Public GeoIP remains opt-in with no MMDB shipped.
- 262 automated tests, zero failures/errors/skips. Live Paper/Citizens/display and inventory combinations still need the documented manual checks.

## NekoCore Public 1.0.0

This is the first public release of NekoCore.

Everything below already existed in the 1.4.0 codebase the public distribution is built from. What 1.0.0 adds is the work of making that codebase safe and understandable for people who aren't its original author: configurable branding, explicit gates around anything involving coordinates or external data, a validation and diagnostics story, and documentation.

Internal versions were deliberately **not** reset. A 1.4.0 database upgrades in place.

### Added

- **Configurable branding.** `branding.server-name` supplies a `{server}` token rendered across TAB, the join welcome, GUI titles, tips, the mascot hologram, daily task text, and the title shop.
- **Safe module gates.** Second world, minigames, AFK pool, weekly leaderboard, GeoIP, and Mascot now default to off, and features that depend on coordinates require both `enabled` and `position-configured` before doing anything.
- **Menu enable and auto-layout.** Disabled or unavailable modules hide their entries and the remaining buttons re-center. Manual slot placement is still available with `gui.auto-layout: false`.
- **A configurable Mascot.** The previous hard-coded Citizens interaction is now driven entirely by configuration — NPC id, hologram lines, particle sets, click window, and two separate reply pools — with NPC lifecycle deliberately left to the administrator.
- **Atomic configuration validation.** `/nekocore reload` parses, validates, and builds a complete new configuration before switching anything over, and keeps the previous one running on any failure. `/nekocore config check` runs the same validation without applying it.
- **Diagnostics.** `/nekocore status` and `/nekocore doctor` report the NekoCore, Paper, and Java versions, the database schema, and the state of each module.
- **Bilingual documentation and CI.** Simplified Chinese and English documentation, a runnable quick start, and a GitHub Actions workflow running `mvn -B clean verify` on pushes and pull requests.

### Changed

- **Default configuration rewritten for a generic server.** Private-server branding, vendor names, real addresses, bespoke NPCs, and coordinates specific to one deployment were removed. The shipped defaults are safe starter values: everything that can work without local knowledge is on, and everything that can't is off.
- **Startup logging condensed.** A normal start logs the schema, core modules, optional modules, and a ready line, rather than a wall of text. Detailed exception output from optional components now requires `advanced.debug: true`.
- **Missing optional data no longer noisy.** A missing or unreadable MMDB disables GeoIP quietly instead of dumping a stack trace.

### Compatibility

Retained intentionally, and safe to rely on:

- Database schema **5**, with migrations **V1 through V5**. There is **no V6**.
- `config-version` **8** and `messages-version` **7**.
- All commands, the `nekocore.*` permission nodes, PlaceholderAPI keys, and PDC keys.
- All stable IDs: **109** store product IDs, **20** daily task IDs, and the title IDs `mame`, `momo`, and `sora`.
- Store and Bag transaction recovery semantics.
- The permanent exclusion of Happy Ghast from the marksman task.

Older `config.yml` files that lack the newly introduced gates are read with their previous behaviour preserved, so an existing server doesn't silently lose a feature on upgrade. A freshly generated Public config uses the safe defaults instead.

### Notes

- **Licensed under MIT.** See [LICENSE](LICENSE). You're free to use, modify, and redistribute NekoCore, including in closed-source projects, as long as the copyright notice is kept. Third-party component licenses remain separate.
- **A security contact has not been published.** See [SECURITY.md](SECURITY.md); the owner should add a channel before release.
- **Automated tests do not cover every live-server combination.** Entity and UI behaviour, coexistence with Citizens, Multiverse-Core, Multiverse-Inventories and WorldGuard, display ownership alongside other TAB/name tag/chat plugins, TextDisplay cleanup, inventory transactions under interruption, shutdown under load, and upgrades from a real backup all still need hands-on verification on a Paper 26.2 test server. See [VERIFICATION.md](VERIFICATION.md) for the boundary in detail.
