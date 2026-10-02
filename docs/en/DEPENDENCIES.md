# Dependencies: what you must install, and what you can skip

[简体中文](../zh-CN/DEPENDENCIES.md) | [Back to README](../../README.en.md)

Let's clear up the most common misunderstanding first.

Open `pom.xml` and you'll see a list of library names. But **"it appears in `pom.xml`" and "the server owner has to install it" are two entirely different statements.** Something needed at compile time, something shaded into the JAR, and something optional at runtime are three separate categories of thing. This page sorts them out, and along the way it hands you an official download link for every component that does require your attention.

---

## The whole picture in one table

| Category | Component | What it's for | Target / tested version | What you need to do |
| --- | --- | --- | --- | --- |
| **Core requirement** | Paper | The server platform and API | 26.2 (API build 129 stable) | **Must install** |
| **Core requirement** | Java | The virtual machine Paper runs on | 25 | **Must install** |
| Bundled | SQLite JDBC | Reading and writing the database | 3.50.3.0 | Nothing at all |
| Bundled | MaxMind GeoIP2 reader | Reading the optional City database | 5.2.0 | No library to install; **you obtain the data file yourself** |
| Optional integration | PlaceholderAPI | Lets other plugins read NekoCore data | compile-tested against 2.11.6 | Only when you want `%nekocore_*%` |
| Feature-specific | Citizens | Provides the mascot's NPC entity | needs to be compatible with Paper 26.2; verify on a live server | Only when you enable Mascot |
| Optional / feature-specific | Multiverse-Core | Configured command-based world routing | 5.8.x (the current page lists 5.8.1) | Only when you want `mvtp` routing |
| Build and test only | Maven / JUnit / Mockito | Compiling and running tests | Maven 3.9+ / 5.13.4 / 5.23.0 | **Not installed** on the server |

**WorldGuard and Multiverse-Inventories are not NekoCore dependencies.** They can coexist with it, but those combinations fall outside what the automated tests guarantee (see [Verification](../../VERIFICATION.md)). **Folia is not a declared supported platform.**

> In one sentence: **Paper** and **Java** are the only things you have to prepare. Everything else is on demand.

---

## Paper

### What is it for?

Paper is the server software itself. NekoCore is a plugin, and a plugin can't run on its own — it needs a host. NekoCore is built against Paper's API, so it won't load on Spigot, CraftBukkit, Forge, or a vanilla server.

### When do you need it?

**Always.** This is the only hard platform requirement.

### Target version

Paper **26.2**, corresponding to API build `129-stable`. That's the version used for development, compilation, and testing.

### Downloading and installing

- Download: [papermc.io/downloads/paper](https://papermc.io/downloads/paper/)
- Beginner walkthrough: [Paper documentation · Getting Started](https://docs.papermc.io/paper/admin/getting-started/)
- API reference: [Paper 26.2 Javadoc](https://jd.papermc.io/paper/26.2/)

The installation steps are the ones in [Quick start](QUICKSTART.md): download the JAR → drop it in a folder → start once → edit `eula.txt` → start again. No need to repeat them here.

### ⚠ About other server platforms

- **Spigot / CraftBukkit** — untested, and not claimed as supported.
- **Folia** — untested, and not claimed as supported. NekoCore relies on scheduled tasks and main-thread assumptions in places, so moving to Folia isn't a matter of changing a version number.
- **Future Paper versions** — likely fine, but untested. Before a major Paper upgrade, validating on a test server is the sensible move.

---

## Java

### What is it for?

The Java virtual machine. Paper is a Java program; without a JVM, nothing starts.

### When do you need it?

**Always.** The version has to be **25**.

### Why is the version so strict?

Because Paper 26.2 requires Java 25. Starting with an older Java fails immediately and exits, usually with something like `Unsupported class file major version`. That requirement comes from the platform, not from NekoCore.

### Downloading and installing

- [Paper's Java installation guide](https://docs.papermc.io/paper/misc/java-install/) (start here — it's organised by operating system)
- [Oracle JDK 25 installation documentation](https://docs.oracle.com/en/java/javase/25/install/)

Once it's installed, confirm it:

```text
java -version
```

The first line should mention 25.

### When you never have to think about this

If you're on a **managed hosting panel** — many of them, including the Pterodactyl family — the provider usually lets you pick the Java version from a dropdown. Choose 25 and you're done; nothing to install yourself.

---

## PlaceholderAPI

### What is it for?

A bridge that lets plugins read each other's data. With it installed, NekoCore registers a set of placeholders, and you can display NekoCore data inside **other plugins**.

For example: your server already has a TAB plugin you like and you don't want to switch to NekoCore's TAB. Install PlaceholderAPI, write `%nekocore_coins%` in that plugin, and it shows the player's coin balance.

### When do you need it?

- You want coins or level from NekoCore displayed in another plugin (TAB, scoreboard, chat, HolographicDisplays, and so on);
- You want to keep your own TAB plugin but still show NekoCore's data in it.

### When you don't need it at all

If you're happy with NekoCore's built-in TAB and chat prefix, you don't need it. **A missing PlaceholderAPI doesn't affect any core feature** — that integration is simply skipped.

### Version notes

Compilation and testing used API **2.11.6**. For a new deployment, using the current official compatible release is the better idea; there's no need to match that number deliberately.

### Downloading and installing

- [GitHub Releases](https://github.com/PlaceholderAPI/PlaceholderAPI/releases)
- [Official Spigot resource page](https://www.spigotmc.org/resources/placeholderapi.6245/)
- [Official wiki](https://wiki.placeholderapi.com/)

Put the JAR in `plugins/` and **restart fully**. Once it's in place, `/nekocore status` reports PlaceholderAPI as available.

### NekoCore configuration

There's no switch to flip on NekoCore's side — the expansion registers itself when PlaceholderAPI is detected, and is skipped when it isn't.

### Available placeholders

Once installed, the identifier is `nekocore`, which means placeholders are written `%nekocore_<name>%`:

| Placeholder | What it returns |
| --- | --- |
| `%nekocore_coins%` | Coin balance |
| `%nekocore_level%` | Level |
| `%nekocore_exp%` | Experience already earned within the current level |
| `%nekocore_exp_needed%` | Experience still needed for the next level |
| `%nekocore_playtime%` | Formatted playtime |
| `%nekocore_level_prefix%` | Level prefix template (such as `[Lv.12]`) |
| `%nekocore_title%` | Name of the currently equipped title |
| `%nekocore_title_prefix%` | Prefix of the currently equipped title |
| `%nekocore_display_prefix%` | **The prefix actually in effect right now** (title prefix or level prefix) |
| `%nekocore_location%` | Coarse region (such as `Guangdong`) |
| `%nekocore_location_prefix%` | Region prefix with brackets around it |

`%nekocore_display_prefix%` is the one you'll reach for most often — **it's the one to use in another chat or nametag plugin.** When NekoCore notices that a player's scoreboard team has already been taken over by another plugin, it logs a hint telling you to integrate through this placeholder rather than fighting over ownership of the team.

**Common pitfall:** PlaceholderAPI expansions sometimes have to be installed with `/papi ecloud download`. NekoCore's expansion is built into NekoCore itself and needs no extra download. But if you write `%nekocore_coins%` in another plugin and it shows up as literal text, use `/papi list` to check whether the expansion was recognised.

---

## Citizens

### What is it for?

Citizens is the most widely used **NPC plugin** on Minecraft servers. It creates, places, and manages entities that stand in the world looking like players — their appearance, skins, names, pathfinding, and behaviour.

### ⚠ One common misunderstanding, cleared up first

**The Mascot is not a Citizens replacement, and it isn't another third-party plugin either.**

| | What it takes care of |
| --- | --- |
| **Citizens** | The **body** standing there — the entity, skin, position, and facing |
| **NekoCore's Mascot module** | The body's **interaction behaviour** — replies on right-click, particles, hologram lines, click-rate limiting |

So the correct way to picture it: **Mascot is a module built into NekoCore**, and running it needs an NPC created by Citizens as its carrier. You won't find an "install the Mascot plugin" step in NekoCore's configuration, because it's already inside the plugin.

### When do you need it?

Only when you want some NPC to talk, emit particles, and carry hologram text above its head.

### When you don't need it at all

Most servers don't. If you're not using Mascot, there's no problem with leaving Citizens uninstalled — `mascot.enabled` defaults to `false`.

### Version notes

Citizens needs a build compatible with Paper 26.2. **This is one to verify on your own server** — Citizens' release rhythm and Paper's don't move in lockstep, and when they disagree NekoCore just disables the Mascot module quietly, possibly writing a single warning, without affecting anything else.

### Downloading and installing

- [GitHub repository](https://github.com/CitizensDev/Citizens2)
- [Official download instructions](https://wiki.citizensnpcs.co/Downloads)
- [Version notes](https://wiki.citizensnpcs.co/Versions)

Put the JAR in `plugins/` and **restart fully**.

### NekoCore configuration

The [Mascot section of the recipes page](RECIPES.md#i-want-to-add-a-citizens-mascot) walks through the whole flow; here's the outline:

1. After the restart, create and place the NPC with Citizens' own commands. Appearance, name, and skin are entirely up to you.
2. Stand next to the NPC and read its number with `/npc id`.
3. Put that number into `config.yml`:

```yaml
mascot:
  enabled: true
  npc-id: 3
```

4. To change its lines, edit `mascot.replies` and `mascot.over-limit-replies` in `messages.yml`.
5. **Restart fully** (Citizens is a new plugin, so a reload isn't enough).

### One important boundary

**NekoCore doesn't create, rename, move, or reskin any NPC.** All it does is "give an NPC that already exists the ability to interact." Disabling Mascot doesn't delete the NPC either; if you want it gone, delete it in Citizens.

That boundary is deliberate. Your NPCs are your assets inside Citizens, and the plugin has no business making decisions about them for you.

---

## Multiverse-Core

### What is it for?

A multi-world management plugin. It creates worlds, loads worlds, and provides the commands that teleport players between them.

### When do you need it?

When you want NekoCore to hop between worlds **by running a command**.

Concretely, `config.yml` contains a line like this:

```yaml
survival:
  command: 'mvtp {player} {world}'
```

`mvtp` is a Multiverse command. With Multiverse installed, NekoCore executes that command to move the player.

### When you don't need it at all

**With only one world, you don't need it.**

Without Multiverse, NekoCore's **loaded main-world entry safely falls back to Paper's world spawn point**. In other words, the survival entry in `/menu` still works — it just travels the native Paper way instead of through `mvtp`.

If your server has a second or third world, those entries do need command routing, and installing Multiverse is the sensible choice.

### Version notes

Multiverse-Core **5.8.x** (the official page currently lists 5.8.1).

### Downloading and installing

- [Hangar page](https://hangar.papermc.io/Multiverse/Multiverse-Core)
- [Version list](https://hangar.papermc.io/Multiverse/Multiverse-Core/versions)
- [Official installation documentation](https://mvplugins.org/core/fundamentals/installation/)

Put the JAR in `plugins/` and **restart fully**. Then create or import your worlds with Multiverse's commands.

### NekoCore configuration

```yaml
survival:
  enabled: true
  world: survival
  command: 'mvtp {player} {world}'
survival-new:
  enabled: true
  world: world_secondary
  command: 'mvtp {player} {world}'
```

The `command` value **has to contain both the `{player}` and `{world}` placeholders**, otherwise the reload is rejected. Leave off the leading `/` — what goes in the configuration is the command itself, not what you'd type into chat.

### Two boundaries

1. **NekoCore does not create worlds.** Multiverse (or another world plugin) has to create and load them first. While a world isn't loaded, its menu entry quietly disappears.
2. **NekoCore does not own death respawns.** Spawn and respawn rules for each world belong to your world plugin or your lobby plugin.

---

## GeoLite2 City data file

### What is it for?

A database that maps IP addresses to locations. NekoCore uses it to resolve a player's IP into a coarse region such as "Guangdong" or "Japan" and show it in TAB.

### ⚠ It is not a plugin

**GeoLite2 isn't a plugin JAR — it's a data file (`.mmdb`).** It doesn't go into `plugins/` to be loaded as a plugin; it goes into `plugins/NekoCore/` to be read as data. This one gets mixed up constantly.

What's more, **NekoCore's JAR, source repository, and releases contain no MMDB file and no MaxMind license key.** You have to obtain those yourself.

### When do you need it?

Only when you want TAB to show player regions (`location-prefix.enabled: true`). That's off by default.

### When you don't need it at all

If you don't want regions displayed, you can ignore this entirely. **A missing or corrupt MMDB disables the GeoIP feature and nothing else** — everything else carries on, and by default it won't flood your log with a stack trace.

### How to get it

Go to [MaxMind's official GeoLite2 page](https://dev.maxmind.com/geoip/geolite2-free-geolocation-data/), register an account under their terms, and download from there. Get the **City** database, not the Country one — province-level precision requires City data.

Format documentation: [City and Country binary databases](https://dev.maxmind.com/geoip/docs/databases/city-and-country/city-binary/).

### Installation

1. **Stop the server.**
2. Extract the `.mmdb` file from the archive you downloaded and rename it to `GeoLite2-City.mmdb`.
3. Put it in `plugins/NekoCore/` (the same directory as `config.yml`, not the root of `plugins/`).
4. Edit the configuration:

```yaml
location-prefix:
  enabled: true
  database-file: GeoLite2-City.mmdb
```

5. **Run `/nekocore reload` to make it take effect.** (A reload re-opens the MMDB file; a full restart works too if you prefer.)
6. Confirm the GeoIP state with `/nekocore status`.

### ⚠ License and privacy

- **Don't publish the MMDB file.** It's governed by MaxMind's license terms, so keep it out of Git and out of public modpacks.
- **Don't publish your license key.** Your MaxMind account credentials belong to you.
- **Don't use mirrors of unknown origin.** There's no way to verify where those files came from or how current they are.
- You still need to judge whether this is appropriate under **the law where you operate** and your server's privacy policy — and to tell your players about it.

### Where the full explanation lives

The privacy model, caching behaviour, the distinction between Chinese provinces and foreign countries, and troubleshooting steps are all in [GeoIP](GEOIP.md).

---

## Things you need only for building

If you're a **server owner**, you can skip this section entirely.

If you're a **developer**, or you want to build from source yourself:

| Component | Version | Purpose |
| --- | --- | --- |
| Maven | 3.9+ | Build tool |
| JUnit Jupiter | 5.13.4 | Unit tests |
| Mockito | 5.23.0 | Test doubles |

None of these **need to be installed on the server**, and the test libraries never make it into the final JAR. The build is:

```text
mvn -B clean verify
```

The artifact is `target/NekoCore-1.2.0.jar`.

---

## One-page checklist

Before you go live, run your eye down this and confirm the combination makes sense:

| Your situation | What to install |
| --- | --- |
| One main world, using everything NekoCore ships with | Paper + Java 25 |
| You want other plugins to show NekoCore coins or level | The above + PlaceholderAPI |
| Several worlds, with travel between them from the menu | The above + Multiverse-Core |
| You want an NPC that talks | The above + Citizens |
| You want player provinces in TAB | The above + a lawful GeoLite2 City data file |

**A missing optional component on any of these lines will not stop NekoCore from working.** That's what "safe degradation" means here: whatever is absent simply switches its own feature off, and the rest carries on.

---

## Still deciding whether to install it?

- **Want to know what these components feel like in game** → [Recipes](RECIPES.md)
- **Want to know why some feature isn't taking effect** → [Compatibility and troubleshooting](COMPATIBILITY.md)
- **Want to understand GeoIP's privacy model** → [GeoIP](GEOIP.md)
- **Want to change the source yourself** → [Contributing](../../CONTRIBUTING.md)
