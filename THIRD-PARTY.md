# Third-party components

NekoCore stands on other people's work. This file is an inventory of what it uses, so you can check the licensing situation yourself.

Two disclaimers up front, because they matter:

- **This is an inventory, not legal advice.** It doesn't replace reading the actual license text of each dependency.
- **NekoCore itself does not carry a project LICENSE yet.** Third-party licenses do not grant you a license for NekoCore, and nothing here should be read as one.

---

## Libraries compiled or bundled into the JAR

| Component | What it does | How it's packaged |
| --- | --- | --- |
| Paper API 26.2, build 129 stable | The server API NekoCore compiles against | `provided` — **not** bundled |
| Xerial SQLite JDBC 3.50.3.0 | Reading and writing the SQLite database | Bundled by shading |
| MaxMind GeoIP2 5.2.0, plus its transitive Jackson components | Reading an optional City MMDB | Bundled and relocated — **no MMDB data is included** |
| PlaceholderAPI API 2.11.6 | Compile-time API for the optional placeholder expansion | `provided` — **not** bundled |
| JUnit Jupiter 5.13.4 | Tests | Test scope only — not bundled |
| Mockito 5.23.0 | Tests | Test scope only — not bundled |

"Bundled by shading" means the library's classes are compiled into the release JAR, so you don't install it separately. In practice that's SQLite JDBC and the GeoIP reader. Everything else is either provided by the server at runtime or exists only for building and testing.

**Before distributing a build yourself**, resolve the transitive graph and check each upstream artifact's published license:

```text
mvn dependency:tree
```

Maven pulls transitive dependencies in automatically, and those bring their own licenses. Reading the top-level entries in `pom.xml` isn't enough to know what you're actually shipping.

Release verification checks that Paper, JUnit, Mockito, test classes, database files, and MMDB files are all **absent** from the final JAR. See [VERIFICATION.md](VERIFICATION.md) for the full list.

---

## Optional server plugins and external data

None of these are bundled, and none are required by NekoCore's core.

**PlaceholderAPI** — an optional integration. When present, NekoCore registers a placeholder expansion so other plugins can read NekoCore data. When absent, only that integration is skipped.

**Citizens** — a feature-scoped requirement, and only for the built-in Mascot interaction. It supplies the NPC entity; the interaction behaviour belongs to NekoCore. **NekoCore never creates, renames, moves, or re-skins an NPC** — that boundary is deliberate, and it also means deleting an NPC is your job in Citizens.

**Multiverse-Core** — an optional dispatch target for the `mvtp` world routing you can configure. Without it, a loaded primary world falls back to its Paper spawn point, and entries for worlds that depend on command routing become unavailable.

**GeoLite2 City data** — optional external **data**, not software. This repository doesn't contain it, and neither does the JAR. Administrators obtain it directly from MaxMind under MaxMind's terms. Note that because it's licensed data rather than a library, redistributing it is a different question from redistributing code — see [GeoIP](docs/en/GEOIP.md).

---

## Where to check the details

Official download links, tested versions, and installation steps for each of these live in the dependency documentation:

- [Dependencies (English)](docs/en/DEPENDENCIES.md)
- [依赖说明（简体中文）](docs/zh-CN/DEPENDENCIES.md)

For what NekoCore guarantees about its own build artifact, see [VERIFICATION.md](VERIFICATION.md). For how to report a licensing concern, see [SECURITY.md](SECURITY.md) — licensing questions aren't security issues, but that document describes the private reporting channel, which is the right place for anything you'd rather not raise publicly.
