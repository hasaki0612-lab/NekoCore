# Changelog

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

- **No LICENSE is included yet.** The project owner needs to choose one before public distribution. Third-party component licenses do not license NekoCore itself.
- **A security contact has not been published.** See [SECURITY.md](SECURITY.md); the owner should add a channel before release.
- **Automated tests do not cover every live-server combination.** Entity and UI behaviour, coexistence with Citizens, Multiverse-Core, Multiverse-Inventories and WorldGuard, display ownership alongside other TAB/name tag/chat plugins, TextDisplay cleanup, inventory transactions under interruption, shutdown under load, and upgrades from a real backup all still need hands-on verification on a Paper 26.2 test server. See [VERIFICATION.md](VERIFICATION.md) for the boundary in detail.
