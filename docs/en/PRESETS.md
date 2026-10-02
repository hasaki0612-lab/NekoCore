# Scenario presets

[简体中文](../zh-CN/PRESETS.md) | [Quick Start](QUICKSTART.md) | [Configuration](CONFIGURATION.md)

These are three complete `config.yml` files, not fragments showing only the differences. The defaults already run a basic survival server, so a preset is an optional starting point rather than something you have to do before installing.

## Where the files live

They ship inside the JAR under `presets/`, and startup copies them into `plugins/NekoCore/presets/`.

That copying only ever fills in what is missing. A preset that already exists — including one you have edited — is never overwritten. Nothing here replaces the `config.yml` or `messages.yml` you are actually running, and nothing touches the database. If the folder cannot be written to, you get a warning; core loading carries on regardless.

| File | Scenario | Difference from the defaults |
| --- | --- | --- |
| `survival-only.yml` | A new survival server with one main world | The defaults, with the main world named `world` |
| `friends-server.yml` | A small friends-only server | Only the displayed server name changes, to 朋友小服; rewards and prices are untouched |
| `lobby-survival.yml` | A server that already has a lobby and a survival world | Main entry `lobby`, second entry `survival`; the world restrictions and menu text match |

All three keep the internal core: profiles, economy, menus, check-in, Home, Store, Bag, TPN, tasks, TAB and Tips. GeoIP, the Mascot, minigames, the AFK pool and the weekly leaderboard stay off, and the AFK and leaderboard position-confirmation locks stay `false` — a preset never guesses a coordinate or builds a hologram on your behalf.

## Fresh server: pick one starting point

1. Start once with the default configuration, confirm you can join and open `/menu`, then stop the server completely.
2. Back up `plugins/NekoCore/config.yml` — and if players have already joined, take a full stopped-server backup while you are at it. **Copy** the preset you chose to `plugins/NekoCore/config.yml`, and leave the original preset file where it is.
3. Set your server name and your real world names. Do not casually change the database filename or `config-version` while you are in there.
4. Start normally, run `/nekocore config check`, then look at the menu, the store and the tasks in game. From here on it is edit → check → reload; swapping the JAR or changing a world plugin still needs a full restart.

## Existing server: never overwrite the whole file

If you already have custom prices, rewards, products, world restrictions or layouts, read the preset and edit your own file field by field. Replacing the whole configuration throws that customisation away, and presets do not merge themselves into what you have. Keep your `messages.yml` and your SQLite data. Back up first, then edit.

Public 1.2.0 stays at config **9**, messages **8** and SQLite **5**. When an older file has no lookup text yet, the existing message loader supplies the default in memory — your own wording and YAML comments are not rewritten. Existing presets are not refreshed by an upgrade either; when you want the new content, compare against `src/main/resources/presets/` in the source tree.

## Lobby plus survival: the worlds must already exist

`lobby-survival.yml` does not create or discover worlds. `lobby` and `survival` have to exist already and be loaded by Paper or by your world plugin.

If your worlds are named something else, change all of it together: the menu entries, the Home and title world restrictions, and Bag's `writable-worlds`. Without Multiverse-Core an already-loaded world uses the existing Paper-spawn fallback; a world that is not loaded simply has no working entry. The preset guesses no teleport coordinates and does not switch minigames on for you.

## Maintenance and verification

Each preset is derived from the current default configuration and keeps every field and version number. The tests compare each preset's complete key set against the defaults and allow only the handful of fields in the table above to differ; the result then has to pass `Settings`, `FeatureSettings`, `DailyTaskSettings` and the shared validation entry point. If a default field is added or renamed and a preset is not updated with it, the test fails — the drift does not happen quietly.

## Common misunderstandings

- Not every `DISABLED` line in `/nekocore status` needs to become `ENABLED`. Most of them are optional features you have no use for.
- To find an item's English type, use `/nekocore lookup`. A preset is not a Chinese-to-English item dictionary.
- There is no `/nekocore set` command that writes YAML for you this round. Edit the file, check it, reload it.
