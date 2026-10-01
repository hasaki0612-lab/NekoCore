# GeoIP: Installing region data legally, and the privacy model

[简体中文](../zh-CN/GEOIP.md) | [Back to README](../../README.en.md)

NekoCore can give your server's TAB list a little more of a community feel by putting a coarse region tag in front of each player's name, like `[Guangdong]` or `[Japan]`. People from the same city recognise each other a little faster.

This feature is different from the rest of the plugin in one important way. **It depends on a data file you have to obtain yourself, and how you may use that file is governed by someone else's terms.** This page covers the installation and the privacy boundary.

---

## Three things to know first

1. **No MMDB file is present in the NekoCore JAR, the source repository, or any release.** Not a single byte.
2. **No MaxMind license key or account credential is present either.**
3. **It is off by default.** `location-prefix.enabled` ships as `false`. Until you turn it on, NekoCore performs no IP lookups at all.

The model, in short: you opt in, and you bring the data. That is deliberate.

---

## Obtaining the data

Go to [MaxMind's GeoLite2 page](https://dev.maxmind.com/geoip/geolite2-free-geolocation-data/), register an account under whatever terms they are offering at the time, and download the database.

**Download the City database, not the Country one.** The difference is not cosmetic:

| Database | Precision |
| --- | --- |
| GeoLite2-**City** | Province level (this is what produces province names for Chinese players) |
| GeoLite2-Country | Country only |

If Chinese players should see their own province, City is required. The file format is described in MaxMind's [City/Country binary database documentation](https://dev.maxmind.com/geoip/docs/databases/city-and-country/city-binary/).

**Do not use a mirror you cannot account for.** You cannot verify where those files came from, when they were last updated, or whether they are intact, and using them may breach MaxMind's terms. The file is read into your server process. An unattributable copy is not worth that risk.

---

## Installing it

### Steps

1. **Extract the archive you downloaded.** What MaxMind hands you is a `.tar.gz`, and the `.mmdb` is inside it. **Renaming the archive and dropping it in will not work** — this is the trap people fall into most often.

2. **Rename the file to `GeoLite2-City.mmdb`** and place it at:

   ```text
   plugins/NekoCore/GeoLite2-City.mmdb
   ```

   Note the path: `plugins/NekoCore/`, **the same directory as `config.yml`**. Not the `plugins/` root.

   Copying the file in can be done while the server is running; that alone changes nothing. What matters is the next step.

3. **Edit the configuration:**

   ```yaml
   location-prefix:
     enabled: true
     database-file: GeoLite2-City.mmdb
     cache-session: true
     show-china-province: true
     show-foreign-country: true
     hide-unknown: true
   tab:
     enabled: true
     show-location-prefix: true
     refresh-seconds: 1
   ```

4. **Make it take effect.** `/nekocore reload` is enough — a reload re-opens the MMDB file and clears the session cache, so **a newly placed data file starts working after a reload.** A full restart works too, if you prefer that habit.

5. **Verify.** Run `/nekocore status` and read the GeoIP state. While you are there, glance at the log; a healthy start does not dump a wall of stack trace.

### How to tell it worked

Join the game and look at TAB. Your own name should carry a prefix along these lines:

```text
[Guangdong] YourName
[Japan] AnotherPlayer
```

The prefix format itself is editable through `location-prefix-format` in `messages.yml`.

### What each setting does

| Field | Effect |
| --- | --- |
| `enabled` | Master switch. Default `false` |
| `database-file` | MMDB file name, relative to `plugins/NekoCore/`. Case-sensitive |
| `cache-session` | Session-level cache. A resolved result is reused for the rest of this run and is gone after a stop |
| `show-china-province` | Show Chinese players down to province |
| `show-foreign-country` | Show non-Chinese players down to country |
| `hide-unknown` | Reserved field. "Show nothing when the lookup fails" is already guaranteed by the formatting logic |

`tab.show-location-prefix` is an **independent global switch** that decides whether TAB shows regions at all. It is a separate mechanism from a player's own privacy toggle.

### `cache-session: false` does not mean "query it live every time"

This one needs its own section, because it is counter-intuitive and the consequences are visible server-wide.

Setting `cache-session` to `false` does **not** make the plugin query the database on every render. It stops the resolved result from being written to the cache at all — and the cache is what the downstream code reads when it needs a player's region.

The result: **no player shows a region.**

So unless you are chasing one very specific problem and know exactly what you are doing, **leave `cache-session` at `true`**.

### About `hide-unknown`

The field is parsed and stored normally, but **nothing in the main code reads it in this version**. It is a reserved field.

"Show nothing when the lookup fails" is **already the default behaviour**. It falls out of the region formatting logic returning an empty string whenever it has no result, and it does not depend on this switch. `true` and `false` produce the same output.

It is documented here so that you know changing it and seeing no effect is expected, and not evidence that your configuration was ignored.

---

## The privacy model

This part is serious, because it concerns real players' personal information.

### What NekoCore does

- **It does not write the full IP into the database.** There is no column for one.
- **It does not show the full IP** in chat or TAB. Only the province or country level result is ever displayed.
- **NameTags never show region.** The name above a player's head never carries location data, so there is nothing here for you to switch off.
- **The session cache holds nothing but resolved results** — coarse regions rather than IPs — and it disappears when the server stops.

### What players control

Players can toggle "show my region" in `/menu`.

With it off, **their own region is not displayed**. That is a player-level choice, and no global setting overrides it.

**Why the toggle exists:** region information is personal information even when it is coarse. Some people simply do not want their location inferred by anyone, not even down to a province. Handing them a switch respects them more than deciding on their behalf.

### How the two switches relate

| Layer | Where | Scope |
| --- | --- | --- |
| Global | `tab.show-location-prefix` | Whether TAB shows regions anywhere on the server |
| Personal | the privacy toggle in `/menu` | Whether one player's own region is shown |

**A region appears only when both are on.** So you can leave the global switch on, let players who are comfortable with it show their region, and let everyone else turn it off themselves. That is the recommended arrangement.

### What you have to judge for yourself

- **The law where you operate.** Displaying players' geographic information carries extra compliance obligations in some jurisdictions. NekoCore cannot make that call for you.
- **Your server's privacy policy.** If you publish one, it should mention that coarse region information is processed.
- **MaxMind's licence terms.** Use of GeoLite2 data is governed by their terms, including requirements around attribution and redistribution. Read them properly when you register.
- **Telling your players.** One sentence — "TAB shows a rough region" — is better than letting them find out on their own.

### What not to publish

| Do not publish | Why |
| --- | --- |
| the `GeoLite2-City.mmdb` file | governed by MaxMind's licence; do not commit it to Git, and do not put it in a public modpack |
| the license key for your MaxMind account | it is your account credential |
| real IP addresses from production logs | logs can contain player connection details |
| the database file | it holds player UUIDs and gameplay data |

If you are helping someone else debug, redact all of it. The [security policy](../../SECURITY.md) has the procedure.

---

## When nothing appears

Work through this in order; the first few steps cover the great majority of cases.

1. **Is `location-prefix.enabled` on?** It defaults to `false`.
2. **Have you reloaded or restarted since?** After placing the file and editing the config, `/nekocore reload` (or a full restart) is what actually reads it. **Copying the file in alone does nothing automatically** — this is the step most often skipped.
3. **Is the file in the right place?** It has to be `plugins/NekoCore/GeoLite2-City.mmdb`.
4. **Does the capitalisation match?** What `database-file` says and what is on disk must be identical, character for character. This matters especially on Linux.
5. **Is it the City build?** Country has no province data.
6. **Was it extracted?** The `.mmdb` has to come out of the `.tar.gz`.
7. **Is the file complete?** An interrupted download leaves a truncated file, and the reader will refuse it.
8. **Did the player turn their own privacy toggle off?** That is the feature working, not a bug.
9. **Is `tab.show-location-prefix` set to `true`?**
10. **Has `cache-session` been changed to `false`?** That hides regions for the whole server. Set it back to `true`.
11. **Is another TAB plugin writing over the same area?** When two plugins claim one display surface, the last writer wins.

### When you need the full error

A missing or corrupt MMDB **disables GeoIP quietly by default**; it does not flood the log with an exception trace. That is meant to spare ordinary administrators, at the cost of hiding the details until you ask for them.

Set this temporarily:

```yaml
advanced:
  debug: true
```

Restart, and the complete exception is logged. **Turn it back off once you are finished.**

---

## Related documentation

- **What the feature looks like in game** → [Quick start](QUICKSTART.md)
- **The shortest path to enabling it** → [Recipes](RECIPES.md#i-want-players-to-see-which-province-theyre-in)
- **Where GeoLite2 sits in the dependency picture** → [Dependencies](DEPENDENCIES.md)
- **Other TAB problems** → [Compatibility and troubleshooting](COMPATIBILITY.md)
- **How to redact things when reporting a problem** → [Security policy](../../SECURITY.md)
