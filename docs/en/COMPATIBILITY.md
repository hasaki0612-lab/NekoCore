# Compatibility and Common Problems

[简体中文](../zh-CN/COMPATIBILITY.md) | [Back to README](../../README.en.md)

The first half of this page is about scope: the environments NekoCore has been verified in, and the ones where nothing is promised. The second half is questions and answers, written as "why does this happen" followed by "check these in this order".

When something goes wrong, work in this sequence: **`/nekocore status` → read the console log → find the matching question below → `/nekocore config check`.** Nine times out of ten, the first two steps already tell you what's happening.

---

## Compatibility scope

### Explicitly verified

| Component | Version |
| --- | --- |
| Paper | 26.2 (API build 129 stable) |
| Java | 25 |

### Declared behaviour when a component is missing

Each of these **degrades safely when absent** — meaning whether you install it or not has no effect on core functionality:

| Component | Behaviour when missing |
| --- | --- |
| PlaceholderAPI | Only the placeholder integration is skipped |
| Citizens | Only the Mascot module is disabled |
| Multiverse-Core | A loaded main world falls back to the Paper world spawn point |
| GeoLite2 MMDB | Only region display is disabled |

### Not claimed

| Platform | Status |
| --- | --- |
| Spigot / CraftBukkit | Untested, not claimed |
| Folia | Untested, not claimed |
| Future Paper versions | Likely to work, but untested |
| Cross-server networks (BungeeCord and similar) | Outside the design goals |

### Can coexist, but not guaranteed

**WorldGuard** and **Multiverse-Inventories** are not NekoCore dependencies and do not appear in `depend` or `softdepend`. They can run on the same server as NekoCore, but **coexistence scenarios are not covered by the automated tests**.

Specifically, these combinations are worth verifying yourself on a test server:

- Whether WorldGuard region protection interferes with NekoCore teleports and Bed Homes;
- whether Multiverse-Inventories' per-world inventories conflict with NekoCore's Bag rules (Bag's `writable-worlds` allowlist exists for exactly this scenario);
- whether NekoCore's death coordinate message is still accurate once another plugin owns death and respawn rules.

**The automated tests cover plugin contracts and degradation paths using mocks, and that cannot substitute for testing a real plugin combination on a real server.** That sentence is meant literally, not as boilerplate.

### Display ownership conflicts

This class of problem is worth knowing about in advance, because it's the hardest one to diagnose on your own:

**The TAB list, the name tag above a player's head, and the chat prefix can only have one owner each, technically speaking.** When two plugins write to the same surface, what appears on screen depends on **which one writes last** — and that ordering can change across restarts.

So the symptom is usually "sometimes it's fine, sometimes it isn't", which people reasonably mistake for a random bug.

**The resolution is to pick one side deliberately:**

- Using NekoCore's TAB → turn off your other TAB plugin;
- using another TAB plugin → set `tab.enabled: false`, and install PlaceholderAPI and read `%nekocore_*%` if you still want NekoCore data in it;
- using another chat plugin to show prefixes → set `chat.level-prefix-enabled: false` so the prefix doesn't appear twice.

---

## Common problems

### Why isn't TAB showing?

TAB doesn't appear at all, or shows up blank or in the default style.

**Check in this order:**

1. **`/nekocore status`** — is the TAB entry enabled? If `tab.enabled` is `false`, or a failed reload left an older configuration in place, it shows up here.
2. **Confirm no second TAB plugin is competing.** This is the most common cause. Any TAB, scoreboard, or list plugin may be fighting NekoCore for the same display area. Move the other TAB plugin out and start once: if NekoCore's TAB behaves normally on its own, you're looking at an ownership conflict.
3. **Check `tab.header` / `tab.footer` in `messages.yml`.** If you edited those two multi-line blocks and broke the indentation or the `|-` marker, the content can end up empty. Validate with `/nekocore config check`.
4. **Look at `tab.refresh-seconds`.** The default is 1 second. Setting it to something large (600, say) makes TAB look frozen.
5. **Confirm your Paper version is 26.2.** TAB behaviour on newer or older Paper builds isn't guaranteed.

**An easily missed detail:** the TAB refresh is **one shared task for the whole server**, and it reads from Paper's and NekoCore's in-memory caches. **It does not query the database.** So when coins don't show up in TAB, the cause is usually configuration or display ownership, not the database.

### Why is the region prefix always missing?

A prefix like `[Guangdong]` should appear in TAB or chat, but it's empty.

Work through these **in order — the first few steps resolve the large majority of cases**:

1. **Is `location-prefix.enabled` set to `true`?** It defaults to `false`, and plenty of people simply forget to switch it on.
2. **Have you reloaded or restarted since?** Once the file is in place and the config edited, `/nekocore reload` (or a full restart) is what actually reads it. **Just copying the file in changes nothing on its own** — this is the single most common cause.
3. **Is the file in the right place?** It has to be `plugins/NekoCore/GeoLite2-City.mmdb`, in the same directory as `config.yml`. **Not** the root of `plugins/`, and not next to the JAR.
4. **Does the filename case match?** Whatever `database-file` says in `config.yml` is what the file on disk has to be called. Linux filesystems are case-sensitive.
5. **Did you download the City edition?** The Country edition carries no province-level data, so Chinese players will show only a country — or nothing at all. You want `GeoLite2-City`.
6. **Have you extracted it?** What you download from MaxMind is a `.tar.gz`; the `.mmdb` inside has to be extracted. Renaming the archive and dropping it in won't be readable.
7. **Has the player turned their own privacy toggle off?** `/menu` has a "show my region" switch, and a player who disables it stops being displayed. That's a designed feature, not a bug.
8. **Is `tab.show-location-prefix` set to `true`?** That global switch and the player's personal one are **two independent** controls; both must be on for anything to display.
9. **Have you set `cache-session: false`?** This one catches people out, because the setting sounds harmless. With session caching off, a player's region is not resolved for them at all, so the prefix disappears **for everyone** — it does not fall back to querying the database live on each refresh. Set it back to `true` unless you have a specific reason not to.
10. **Is another TAB plugin overwriting it?** See the previous question.

**If you've ruled all of that out and it still doesn't show:** temporarily set `advanced.debug` to `true` and start once more to see the detailed exception. By default, a missing or corrupt MMDB only disables GeoIP quietly without dumping a stack trace — that's so nobody gets alarmed, but it also means you have to opt into debug output to see the details.

Full installation and privacy guidance is in the [GeoIP documentation](GEOIP.md).

### Why does right-clicking the Mascot do nothing?

Right-clicking the NPC produces no reaction.

**Check in this order:**

1. **What does `/nekocore status` say about Mascot?** If it's disabled, either Citizens wasn't detected or `mascot.enabled` is `false`.
2. **Is Citizens installed, and is its version compatible with Paper 26.2?** Citizens' release cadence doesn't track Paper exactly. When they're incompatible, NekoCore simply disables the Mascot quietly, possibly with one warning line in the log.
3. **Has the NPC actually been spawned?** Creating an NPC in Citizens isn't the same as placing it in the world.
4. **Is `npc-id` correct?** Stand next to the NPC and run `/npc id`; that number has to match `mascot.npc-id` in `config.yml` exactly.
5. **Are you right-clicking with your main hand?** Off-hand interaction doesn't trigger it.
6. **Are you clicking too fast?** More than 5 clicks within 15 seconds switches to the "over-limit reply pool", the tone of the replies changes ("slow down a bit"), and a 2-second cooldown applies. If `over-limit-replies` in `messages.yml` has been edited down to nothing, there will be no output at all at that point — which looks exactly like "clicking does nothing".
7. **Have you restarted since installing Citizens?** Adding Citizens is adding a plugin, and that needs a full restart.

**One boundary, worth repeating:** NekoCore does not create, rename, move, or reskin any NPC. If the NPC itself is the problem, that gets resolved inside Citizens.

Configuration details are in [the Mascot recipe](RECIPES.md#i-want-to-add-a-citizens-mascot).

### Why hasn't the leaderboard appeared?

The weekly coin board should be showing floating text at a particular set of coordinates, but there's nothing there.

**Check in this order:**

1. **Are both switches `true`?** `weekly-coin-leaderboard` needs `enabled: true` **and** `position-configured: true` **at the same time**. That double gate is deliberate: it stops anything from being spawned in a location nobody has confirmed.
2. **Is the world loaded?** The world named in the `world` field has to actually be loaded on the server.
3. **Is there space at those coordinates?** A board generated inside a solid block is invisible.
4. **Is there any coin income recorded this week?** The board tracks **coins earned this week**, not current balances. If nobody has earned anything this week, the board is empty (it will read "no coin records this week").
5. **Check the log when `position-configured: false`.** In that state, tracking **continues as normal** — only the display isn't generated, and the plugin logs a WARNING to tell you. So it's entirely possible to have `enabled` on and simply have forgotten to fill in the coordinates.
6. **Have you restarted or reloaded?**

**About 0,0,0:** NekoCore **never generates anything at `0,0,0` by default**. The `x: 0.0, y: 0.0, z: 0.0` in the configuration file is a placeholder value, intended to be used together with `position-configured: false`. If you see something at 0,0,0, another plugin put it there.

**One boundary:** NekoCore only manages TextDisplays carrying **its own marker**. It cleans up the ones it created and won't touch floating entities belonging to other plugins. So if something else is already displaying at that spot, both may show at once.

### Why isn't there an AFK pool in `/menu`?

There's no AFK pool entry in the menu.

The answer here is usually **that it's working as designed**:

1. **Is `afk-pool.enabled` set to `true`?** It defaults to `false`.
2. **Is `afk-pool.position-configured` set to `true`?** Both locks have to be open.
3. **The menu's auto-layout hides modules that are off.** So "I can't see the button" isn't a fault in itself — it's the designed behaviour. NekoCore won't hand you a greyed-out button that does nothing when clicked.

**Why two locks are necessary:** coordinates are the easiest thing to get wrong. With only the `enabled` lock, an admin in a hurry could switch the feature on before filling in a position, and every player on the server who clicks it would be sent to `x: 0.5, y: 64.0, z: 0.5`. Two locks mean the plugin will never teleport anyone to a placeholder coordinate.

**So the sequence is:** build the pool → read its exact coordinates → put them into `teleport` → open both locks → `reload` → the entry appears.

**If both locks are open and it's still invisible:** check whether the world named in the `world` field is loaded.

Configuration details are in [the AFK pool recipe](RECIPES.md#i-want-an-afk-pool).

### Why didn't my config change take effect?

You edited `config.yml` and nothing changed in game.

1. **Did you run `reload`?** `config check` **validates only and applies nothing** — that's the entire point of it. Once you're happy with the change, `/nekocore reload` is what makes it live. The distinction is worth committing to memory:

   | | What it does |
   | --- | --- |
   | `config check` | Validates only, changes nothing |
   | `reload` | Swaps the whole set in after validation passes |

2. **Did `reload` fail quietly?** When a reload fails, the server **keeps running on the old configuration** and players notice nothing at all — that's the protection working, but it does mean you have to read the response message. On failure it tells you which YAML path was wrong and what type or range it expected. A misspelled Material even gets a suggestion for the nearest match.
3. **Is this a change a reload can't cover?** These require a full restart:
   - replacing the JAR
   - changing `database.filename`
   - adding or replacing an MMDB file
   - installing a new third-party plugin
   - world-plugin changes
4. **Which file did you edit?** `config.yml` governs behaviour; `messages.yml` governs wording. Editing the wrong file naturally has no effect.
5. **Is auto-layout hiding an entry?** After changing `enabled`, a menu entry **appearing or disappearing** is normal behaviour, not a change that "didn't take".

**A useful diagnostic:** if you're unsure whether a configuration was accepted, run `/nekocore config check`. It uses exactly the same validation logic as `reload`, so if it passes, the configuration file itself is fine.

### A world entry disappeared?

One of the world buttons in `/menu` is gone.

**Check in this order:**

1. **Is that world's `enabled` set to `true`?**
2. **Does the world name match exactly, including case?** `World` and `world` are two different things as far as Paper is concerned.
3. **Is the world actually loaded?** Run `worlds` from the console, or check the startup log. **NekoCore does not create worlds** — a world has to be created and loaded by Multiverse or another plugin first.
4. **Are the placeholders in `survival.command` complete?** It must contain both `{player}` and `{world}`, otherwise the entire reload is rejected. Note that this is the command itself, written without a leading `/`.
5. **What if you don't have Multiverse?** The **main world** entry can safely fall back to the Paper world spawn point, so Multiverse isn't required for it. **Additional worlds that rely on command routing** do need Multiverse installed.

**Remember the semantics of auto-layout:** entries that are disabled or whose world isn't loaded **get hidden**, and the remaining buttons re-centre. So "a button is missing" is the plugin telling you honestly that this feature isn't currently available.

### `/nekocore reload` failed?

The console reported a reload error.

**First, relax: the server is still running on the last valid configuration.** Players notice nothing, and you are not in a dangerous state.

**Then:**

1. **Read the first error.** The message carries a YAML path (for example `store.products.bred.material`), the error type, and the expected range. **The first one is enough** — later errors are frequently consequences of the first.
2. **Fix it against the file.** The usual suspects are tabs used for indentation, a missing space after a colon, a misspelled Material name, a value outside its accepted range, a malformed world name, or a missing placeholder.
3. **Take the suggestion when a Material is misspelled.** NekoCore offers the nearest matching names ("did you mean…"); just follow it.
4. **Run `/nekocore config check` once you've fixed it.** Then `reload` after that passes.
5. **Don't repeatedly run Bukkit's `/reload`.** It is an entirely different operation from a NekoCore reload, and using it only pushes the server into a less stable state.

**If the error mentions the JAR, the database filename, a dependency, or the MMDB:** do a full restart — a reload can't handle any of those.

**If you want to be certain the configuration file itself is clean:** `config check` uses the same validation logic, so a pass means both the syntax and the values are fine.

---

## What the automated tests cover, and what they don't

This section is deliberately blunt, because it affects how you read every "guarantee" above.

**Covered by automated tests:**

- configuration parsing, validation, and the upgrade path;
- database schema migrations (including a fresh install starting from a completely empty directory);
- the transactional recovery semantics of the store and Bag;
- daily task selection, progress, and event handling;
- degradation behaviour when optional components are absent (plugin contracts verified with mocks);
- command registration and permission mapping.

**Not covered by automated tests:**

- real Paper entity and UI behaviour;
- real coexistence with Citizens, Multiverse-Core, Multiverse-Inventories, or WorldGuard;
- display ownership conflicts with other TAB / name tag / chat plugins;
- TextDisplay creation and cleanup in a real world;
- how player inventory operations behave during real disconnects, crashes, and full inventories;
- shutdown behaviour under heavy load;
- upgrading from your own real backup.

**These aren't a list of "should be fine" — they're a list of things for you to verify on a test server.** Detailed verification steps are in [Release verification](../../VERIFICATION.md).

---

## Still stuck?

Try these in order:

1. **Temporarily set `advanced.debug` to `true`**, restart, and read the full exception. **Remember to turn it back off afterwards** — it prints a great deal you don't normally need.
2. **Read [Release verification](../../VERIFICATION.md)** and see whether your scenario appears in the "needs manual verification" list. If it does, the problem may not be your configuration at all — that server simply hasn't been verified yet.
3. **If you're going to ask someone else for help**, read the [security policy](../../SECURITY.md) first — it explains how to redact databases, IPs, and player information. **Don't post a whole `nekocore.db` or an unedited log in a public place.**

---

## Related documentation

- **Every configuration field explained** → [Configuration](CONFIGURATION.md)
- **Find the edit by goal** → [Recipes](RECIPES.md)
- **What each third-party component is for** → [Dependencies](DEPENDENCIES.md)
- **Database and recovery** → [Database](DATABASE.md)
- **GeoIP compliance and privacy** → [GeoIP documentation](GEOIP.md)
