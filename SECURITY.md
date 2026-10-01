# Security Policy

## What counts as a security issue

Please report these privately rather than in a public issue:

- Anything that lets a player gain permissions, items, or coins they shouldn't have — including duplication bugs in the store or Bag, quota bypasses, and transaction-recovery paths that can be driven to grant or lose items deliberately.
- Anything that exposes data it shouldn't: full IP addresses, player UUIDs, database contents, or another player's data.
- Anything that lets a player with no `nekocore.admin` run admin-only operations, escalate their own permissions, or reach a command they should be denied.
- Anything that lets an untrusted player crash the server, wedge the plugin, or corrupt the database.
- A path where configuration supplied by an administrator can be turned into code execution or file access outside `plugins/NekoCore/`.
- Secrets that leaked into the repository, the JAR, or a release artifact — a MaxMind license key, a database file, a production log, or player data.

## What is an ordinary bug, not a security issue

These belong in the normal issue tracker, and reporting them publicly is fine:

- A feature that doesn't work, or works differently from what the documentation says.
- A message with a typo, or text that reads oddly.
- A crash caused by an invalid configuration value, where the plugin fails safely and the server keeps running.
- Cosmetic problems — display glitches, wrong colours, holograms positioned oddly.
- A missing or unclear piece of documentation.
- Compatibility problems with another plugin, where nothing is exposed and no player gains an advantage.

If you're genuinely unsure which one you've found, **report it privately anyway.** A false alarm costs a short conversation; a public write-up of a real vulnerability costs considerably more.

## How to report

Please report vulnerabilities through **GitHub's private vulnerability reporting** for this repository:

1. Open the repository's **Security** tab.
2. Click **Report a vulnerability**.
3. Fill in the form with the details requested below.

This creates a private advisory that only the maintainer can see, so nothing is exposed publicly while the issue is being investigated. If you can't use that form for some reason, contact the maintainer directly through their GitHub profile rather than opening a public issue.

**Please don't open a public issue describing a vulnerability.** A public report gives every server running the affected version a window to be attacked before a fix is available.

## What to include

A useful report contains:

1. **The affected version.** The NekoCore version, and the Paper and Java versions you tested on. `NekoCore 1.0.0` / `Paper 26.2` / `Java 25` is a complete answer.
2. **What the impact is.** Which guarantee is broken — item duplication, permission bypass, data exposure, availability, integrity? Be concrete about who can do what.
3. **The configuration needed to reproduce it.** Only the relevant parts. Which modules were enabled, which settings differ from the defaults.
4. **Minimal reproduction steps.** The shortest reliable sequence that demonstrates the problem. "It happened once during normal play" is hard to act on; a numbered list that reproduces it every time is easy to act on.
5. **What you expected instead.** One sentence is usually enough.
6. **Any supporting evidence** — a log excerpt or a screenshot, with the redaction below applied.

## What not to publish

Never put any of these in a public issue, discussion, or pull request:

| Don't publish | Why |
| --- | --- |
| A working exploit | Gives every server running the affected version a window to be attacked before a fix ships |
| Your `nekocore.db`, or any copy of it | Contains player UUIDs, economies, and inventory contents |
| `.db-wal` or `.db-shm` files | Same data, and they may contain recently committed records not yet in the main file |
| Full server logs | Connection logs can carry player IP addresses |
| Player UUIDs, names, or IP addresses | Personal data belonging to people who didn't consent to being in your bug report |
| Your MaxMind license key or account credentials | Your credentials, and their exposure may breach MaxMind's terms |
| Your `GeoLite2-City.mmdb` | Licensed data that shouldn't be redistributed |
| SSH keys, tokens, webhooks, or API keys | Obviously |

## Redacting properly

If a log line or config excerpt is genuinely needed to explain the problem, replace the sensitive parts rather than deleting the line — the surrounding context is often what makes the report useful.

```text
Before:  Yuki[/203.0.113.47:51234] logged in with uuid 7f3a9c12-...
After:   Yuki[/<IP>:<PORT>] logged in with uuid <UUID>
```

Guidance for specific values:

- **IP addresses** → `<IP>`, or keep only the network prefix if it's relevant.
- **UUIDs** → `<UUID>`. If you need to distinguish two players, use `PlayerA` and `PlayerB`.
- **Player names** → `PlayerA`, `PlayerB`. If the name itself matters (encoding issues, for instance), say so and keep one.
- **Tokens, keys, secrets** → `<REDACTED>`. Delete these rather than masking them; a partially masked key is sometimes still recoverable.
- **Server names, domains, and addresses** → `<SERVER>`.
- **World coordinates of a private build** → round them, or replace them.

If you need to demonstrate a database-level issue, build a **minimal reproduction database** with invented players. Don't send a trimmed copy of production data — trimming is easy to get wrong, and the file is small enough that it's not worth the risk.

## Operational security guidance

These are the practices NekoCore's own design assumes of its administrator.

**Backups.** Stop the server before copying. Copy the database together with its `.db-wal` and `.db-shm` companions. If you use Bag or the store, take the player inventory data from the same point in time. A backup that can't be restored isn't a backup — test the restore occasionally.

**Privilege.** The server console and `nekocore.admin` are both privileged. `nekocore.admin` can mint coins and experience, reset store quotas, reroll the global task rotation, and reload configuration. Don't hand it to players who only need one narrow capability; grant something narrower instead. Console-console operations are preferred for sensitive maintenance.

**GeoIP.** Obtain GeoLite data through MaxMind's official channel under their terms. Keep account and license material outside YAML files and outside version control. Treat the MMDB as licensed data that you may not redistribute.

**Reloading and upgrading.** Don't use Bukkit's `/reload` or hot-unloaders as a substitute for a restart. Upgrade with the server stopped, and restore the JAR, configuration, database, and inventory data as one point in time if you need to roll back.

**Third-party plugins.** Review the other plugins you run and their permissions independently. NekoCore stepping back from a display surface, or degrading safely when an integration is missing, doesn't mean the integration itself is safe.

**PlaceholderAPI and other consumers.** Anything you hand `%nekocore_display_prefix%` or similar to is a plugin that will render it. It gets the same trust you'd extend to any plugin with access to player-facing text.

## Supported versions

Public **1.0.0** is the supported public line described by this repository.

Security fixes aim to preserve stable IDs and data migrations wherever possible, because breaking them would be a worse outcome for an existing server than the original issue. If a fix genuinely requires an identifier or migration change, that will be stated explicitly in the release notes rather than shipped quietly.
