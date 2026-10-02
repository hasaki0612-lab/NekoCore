# Recipes: "I want to…"

[简体中文](../zh-CN/RECIPES.md) | [Back to README](../../README.en.md)

This is the page where you don't have to think very hard.

There's no theory here and no design rationale. It answers one question, over and over: **when you want to change something, which lines do you actually edit?** Every section has the same shape:

> shortest answer → YAML → the steps → how to confirm it worked → common pitfalls

If you'd rather understand the machinery underneath, [Configuration](CONFIGURATION.md) is the place for that. This page only promises that copying it will work.

---

## Three things to read before you copy anything

1. **You are merging, not replacing.** The fragments below go *into* your existing `config.yml`. Read them as "these particular fields should end up looking like this." Don't delete the whole file and paste a fragment over it — that quietly throws away every sibling field the fragment doesn't show.

2. **The verification rhythm is always the same:**

   ```text
   /nekocore config check     ← confirm nothing is mistyped; applies nothing
   /nekocore reload           ← once you're happy, make it take effect
   ```

   If the change involves **installing a plugin, swapping your world plugin, or renaming the database file**, stop the server and restart it properly instead. Reload can't reach those. (Adding an MMDB file is fine to reload — see the GeoIP recipe below.)

3. **YAML indentation is spaces only, never a Tab.** This line repeats throughout the document, but it genuinely is the single most common way a first-timer derails an evening.

---

## Player experience

### I want an icon but do not know its English type

As an admin, hold the item in your main hand and run `/nekocore lookup`: it prints the item's true English name and the `material: NAME` line you want. With an empty hand, search by keyword instead — `/nekocore lookup bread`, or `/nekocore lookup entity phanton` when you are after an entity type. Close spelling is good enough (`bred` finds `BREAD`, `phanton` finds `PHANTOM`), and `minecraft:bread` works as well as `bread`.

Copy that `material:` line into the existing button or product field, rather than creating a second group with the same name. Check, then reload. The command never writes YAML for you, and Chinese names are not searched — it says so instead of guessing at a translation.

### I want to change the server name

```yaml
branding:
  server-name: "Cat Cafe"
```

Reload afterwards, and that's the whole job.

Everything that uses `{server}` follows along: the TAB header, the join welcome title, GUI titles, the tips messages, the mascot hologram, the daily task that reads "Good morning, {server}!", and the notices inside the check-in GUI.

**Common pitfall:** whenever the name contains a space, a `#`, or a `:`, wrap it in quotes. YAML reads `server-name: My #1 Server` as "the value is `My`, and everything after it is a comment." Writing `server-name: "My #1 Server"` is what you meant. Names are limited to 1–64 display characters, and an empty value or stray control characters cause the entire reload to be rejected — the server keeps running on the old configuration, so nothing breaks, but your change won't be live either.

---

### I want to change the welcome title and tips

Both are tuned in `config.yml`:

```yaml
welcome-title:
  enabled: true
  delay-ticks: 15
  fade-in-ticks: 10
  stay-ticks: 60
  fade-out-ticks: 10
tips:
  enabled: true
  interval-seconds: 180
  prefix: '&#9FD9F6tips &f>> '
  messages:
    - '&fWelcome to &#9FD9F6{server}&f! Type &#9FD9F6/menu &fto open the server panel.'
    - '&fSleep in a bed or use &#9FD9F6/sethome &fto save your spot, then type &#9FD9F6/home &fto return.'
```

Reload when you're done.

**A couple of concepts first.** `ticks` is Minecraft's unit of time, and 20 ticks make one second. So `delay-ticks: 15` means the title appears 0.75 seconds after the player joins, while `stay-ticks: 60` keeps it on screen for three seconds. `interval-seconds` is how far apart the tips are, and the default of 180 works out to three minutes.

Tips play **one per round**, and after the last message it loops back to the first. A reload restarts that cycle from the top.

**Common pitfall:** while `tips.enabled: true`, the `messages` list can't be empty, or the reload will be rejected.

---

### I want my own welcome message / no tips at all

Switching them off is enough:

```yaml
tips:
  enabled: false
```

Players still get the centre-screen title when they join; they simply stop receiving periodic tips. Setting `welcome-title.enabled: false` turns the title off as well.

**Neither change touches gameplay**, so you lose nothing but a bit of atmosphere. If your server already runs its own welcome plugin, switching these off is the friendlier choice — two sets of prompts fighting for the same chat line wears players down.

---

### I want players to see which province they're in

This one needs an extra data file, so it isn't a one-line change. The short version of the configuration:

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

**⚠ Don't set `cache-session` to `false`.** It doesn't turn anything into a real-time lookup; it makes every player show no region at all. Leave it at `true`.

The steps:

1. Go to [MaxMind's official GeoLite2 page](https://dev.maxmind.com/geoip/geolite2-free-geolocation-data/), register under their terms, and download the **City** database — not the Country one, and not the archive file itself.
2. Unpack the `.mmdb` out of the downloaded `.tar.gz`.
3. Rename it to `GeoLite2-City.mmdb` and put it in `plugins/NekoCore/` (the same directory as `config.yml`). Copying the file in while the server runs is fine — nothing happens until the next step.
4. Apply the configuration above.
5. **Run `/nekocore reload`.** A reload re-opens the MMDB file and clears the session cache, so the new file starts working immediately. A full restart also works.
6. Run `/nekocore status` to check the GeoIP state.

To confirm it worked, look at TAB in game: a coarse prefix like `[Guangdong]` or `[Japan]` should appear in front of your name.

**Common pitfalls:**

- **Don't drop the archive in as-is.** What you download from MaxMind is a `.tar.gz`; unpack the `.mmdb` file from inside it first.
- **Don't forget to reload.** Just copying the file in changes nothing on its own — this is the one that costs people half an hour for nothing.
- **Players can turn it off themselves.** `/menu` carries a "show network region" privacy toggle, and once a player disables it, their region stops displaying. That's deliberate, not a bug.
- **Never publish the MMDB file or your license key.** Both are governed by MaxMind's terms — keep them out of Git and out of any public modpack.

The full explanation and the privacy model live in [GeoIP](GEOIP.md).

---

### I want to hide player regions

```yaml
location-prefix:
  enabled: false
tab:
  show-location-prefix: false
```

Then reload.

`show-location-prefix` under `tab` only governs the global list. Each player's personal privacy toggle is a **separate** mechanism, so you need both switched off before regions stop appearing entirely.

Worth knowing while you're here: the NameTag above a player's head **has never shown a region**, so there's nothing extra to disable for it. Full IP addresses are never written to the database either, and never displayed anywhere.

---

### I want TAB to show only the basics

The content of TAB lives in `messages.yml`, under `tab.header` and `tab.footer`. To slim it down, delete the lines you don't want:

```yaml
# messages.yml
tab:
  header: |-
    &#61C8F2✦ {server} &#A88BE8· Global Panel &#61C8F2✦

    &#B2C3CFPosition  &fX {x}   Y {y}   Z {z}
    &#B2C3CFTPS   {tps_1m} &#B2C3CF· {tps_5m} &#B2C3CF· {tps_15m}
```

The variables you can use are `{server}`, `{x}`, `{y}`, `{z}`, `{tps_1m}`, `{tps_5m}`, `{tps_15m}`, `{online}`, `{max_players}`, `{coins}`, `{time}`, and `{uptime}`.

Reload to apply it.

**Why replace the whole block here?** Because `|-` is YAML's multi-line text form, and indentation is what defines where the content ends. If you delete a line from the middle, watch that you don't disturb the indentation of the lines around it.

---

### I already have my own TAB plugin

```yaml
tab:
  enabled: false
  show-location-prefix: false
  refresh-seconds: 1
```

Reload, then let the other TAB plugin own the list.

**Why pick a side at all?** When two plugins write into TAB at once, what players end up seeing depends on who wrote last — and that order can change after a restart. It looks like random breakage, but the real problem is that ownership was never decided.

If you'd still like NekoCore data (coins, level) inside your other TAB plugin, install PlaceholderAPI and use `%nekocore_*%`. NekoCore keeps the data, the other plugin keeps the display, and each side does its own job.

The same reasoning applies to the level prefix in chat, which can also be switched off:

```yaml
chat:
  level-prefix-enabled: false
```

Any chat plugin already using `%nekocore_display_prefix%` has to have this turned off, otherwise the prefix shows up twice.

---

### Arrival links and the tasks shortcut

Players type `/tasks` to open the existing Daily Tasks GUI; `nekocore.tasks` is allowed by default. Turning daily tasks off also skips the relevant Tips.

To add your own arrival links, edit `plugins/NekoCore/config.yml` on the **server machine**: fill the needed docs / website / community / discord entries in `join-info.links` with your own real HTTP(S) URLs and leave other entries as `""`. Never copy an address you do not own. Edit player-facing text under `messages.yml → join-info`; run `/nekocore config check`, reload and rejoin to verify. Buttons only open URLs, never run commands. See [JoinInfo configuration](CONFIGURATION.md#join-info--进服个人信息).

## Server structure

### I want a single plain survival server

```yaml
survival:
  enabled: true
  world: world
  command: 'mvtp {player} {world}'
survival-new:
  enabled: false
  world: world_secondary
  command: 'mvtp {player} {world}'
minigames:
  enabled: false
  world: world
  x: 0.5
  y: 64.0
  z: 0.5
  yaw: 0.0
  pitch: 0.0
afk-pool:
  enabled: false
  position-configured: false
weekly-coin-leaderboard:
  enabled: false
  position-configured: false
mascot:
  enabled: false
  npc-id: -1
```

Reload, and you're running.

The only requirement is that the world named in `world` is actually loaded. `/menu` hides the entries you've turned off and re-centres whatever is left, so players see a clean panel with three or four buttons instead of a field of grey placeholders.

**Common pitfall:** those `0.5 / 64.0 / 0.5` values are **placeholders, not usable coordinates**. They exist so the configuration structure stays readable. As long as the matching `enabled` is still `false`, NekoCore will never teleport anyone to those coordinates, and never spawn anything at 0,0,0.

---

### I want a lobby plus survival

Point the main entry at your survival world:

```yaml
survival:
  enabled: true
  world: survival
  command: 'mvtp {player} {world}'
```

The steps:

1. Install [Multiverse-Core](https://hangar.papermc.io/Multiverse/Multiverse-Core).
2. Import and load a world called `survival`.
3. **Stop the server** and apply the configuration above.
4. Restart. (You've added a plugin, so a reload isn't enough.)
5. Join, open `/menu`, and click the survival entry.

To confirm it worked, clicking should drop you at the `survival` world's spawn point.

**The boundary is worth stating plainly:** NekoCore **does not create worlds**, and it **does not own death respawns**. Your lobby's spawn and respawn rules belong to your lobby plugin or to Multiverse. All NekoCore does here is take the player from the menu to the world.

---

### I want to add a second survival world

```yaml
survival-new:
  enabled: true
  world: world_secondary
  command: 'mvtp {player} {world}'
```

The steps:

1. Have your world plugin create or import that world, and make sure it's **loaded**.
2. Stop the server and edit the configuration.
3. Restart.
4. Check `/menu` for the new second-survival entry.

**Common pitfall:** when the world isn't loaded, the entry disappears quietly instead of throwing an error. So the first reaction to a missing button should be **checking the world name in the console**, not suspecting the plugin is broken. World names are case-sensitive.

---

### I want to rearrange the menu

The default is automatic layout: modules that are off or unavailable hide themselves, and the remaining buttons re-centre.

To arrange things yourself:

```yaml
gui:
  enabled: true
  auto-layout: false
  title: '&#9FD9F6{server} &7· &#9FD9F6Server Panel'
  rows: 5
  items:
    profile:
      slot: 10
    daily:
      slot: 12
    checkin:
      slot: 14
```

Reload when you're done.

`rows` accepts 1–6. The `slot` values of all eight entries have to be unique, and each one has to sit inside `rows × 9` cells (5 rows = 45 cells, so legal numbers run from 0 to 44).

**When is it worth turning auto-layout off?** When you want the buttons to **stay in exactly the same place, always**. Automatic layout looks tidier, and the price is that positions shift as modules come and go. Plenty of owners prefer a fixed layout, because players build muscle memory around it. Both choices are reasonable.

**Common pitfall:** a slot that's out of range or used twice causes the reload to be rejected, and the error names the item at fault.

---

### I want to change a menu button's icon or text

Every menu entry lives under `gui.items.<id>` in `config.yml`, and both the icon and the text are yours to edit:

```yaml
gui:
  items:
    checkin:
      slot: 14
      material: SUNFLOWER
      name: '&#9FD9F6Daily Check-in'
      lore:
        - '&#B2C3CFToday: {checkin_status}'
        - '&#B2C3CFLittle gift: &#FFE49A{checkin_coins} coins &7+ &#FFE49A{checkin_exp} EXP'
        - ''
        - '&#C5E9FASee you every day, grow a little more~'
```

Reload to see it.

`material` takes a Paper `Material` enum name: uppercase, underscore-separated, and it has to exist in the version you're running (things like `SUNFLOWER`, `NAME_TAG`, `DIAMOND_SWORD`).

**Common pitfall:** a misspelled name fails the reload, but NekoCore hands you **a suggestion for the nearest match** — look for the "did you mean…" line in the console and type that instead.

---

### I want to turn the whole panel off

```yaml
gui:
  enabled: false
```

Reload, and `/menu` will tell players the feature is currently disabled. Every command behind it — `/checkin`, `/store`, `/bag`, `/sethome`, and the rest — **keeps working exactly as before**.

The menu is an entrance, not the feature itself. So if you'd rather navigate with NPCs or signs, switching the GUI off is a perfectly good plan.

---

## Economy and numbers

### I want to change the check-in reward

```yaml
checkin:
  enabled: true
  timezone: Asia/Shanghai
  coins: 200
  exp: 80
  sound:
    name: minecraft:block.amethyst_block.chime
    volume: 0.6
    pitch: 1.4
  particle-count: 16
```

Reload to apply it.

**Reward changes are not retroactive.** Anyone who already claimed today's reward won't be topped up, and nothing gets rolled back. The best moment to adjust numbers is just before the day rolls over — or you simply accept that a batch of players claimed at the old rate.

**About `timezone`:** it decides when "a day" begins. The default, `Asia/Shanghai`, means the day turns over at 00:00 Beijing time. Changing it affects check-ins, daily tasks, and the enchantment book batches together. Switching timezones on a live server cuts the current day short, so do it before a rollover with the server stopped, and take a backup while you're at it.

**About `particle-count`:** this is how many small white sparks rise off the player when a check-in succeeds. Set it to `0` to drop the particles entirely; the feature itself is unaffected.

**Common pitfall:** negative rewards, an invalid timezone name, or a wrong sound name will fail the whole reload. If you're testing numbers, confirm them at the `config check` stage first.

---

### I want to change one product's price

```yaml
store:
  products:
    bread:
      category: food
      material: BREAD
      name: 'Bread'
      price: 12
      sell: true
```

Reload afterwards.

**The key rule: change fields, never IDs.**

That `bread` key is a **stable product ID**. It shows up in daily limit records, transaction logs, and crash-recovery data. Rename it to `bread_loaf` and NekoCore sees a brand-new product: its limit starts from zero, while the old `bread` records are left orphaned. Want a different display name? Change the `name` field.

**Price edits aren't retroactive either.** Transactions that already completed settle at the price in force at the time.

**Other fields you can edit:**

| Field | What it does |
| --- | --- |
| `category` | Which category it belongs to (must be an already-defined category ID) |
| `material` | The item actually handed over |
| `name` | Display name |
| `price` | Price per unit, in coins |
| `sell` | Whether players may sell it back |
| `buy-limit` | Maximum purchases per day; when omitted, the item's vanilla max stack size |
| `sell-limit` | Maximum sales per day; when omitted, `default-sell-limit` (200 by default) |

There's no universal right answer for pricing. Before launch, make a rough estimate: how many coins a player earns in a day from check-ins plus tasks, how much sellable material a mob farm or a farm produces daily, and roughly how many people play. **Raise the price of luxuries first, and put buy/sell limits on anything that's easy to farm**, then read the transaction log. That's far more practical than working out an exact formula up front. [Economy](ECONOMY.md) goes into the thinking in more depth.

---

### I want to add buy or sell limits to a product

```yaml
store:
  products:
    echo_shard:
      category: special
      material: ECHO_SHARD
      name: 'Echo Shard'
      price: 1600
      sell: true
      buy-limit: 4
```

Reload to apply it.

In this example a player can buy at most four echo shards per day. The default configuration already ships this product with `buy-limit: 4`, so it doubles as a template you can copy.

Limits are counted **per player**, not shared across the server. To inspect or clear them:

```text
/nekocore store status <player>
/nekocore store reset <player>
```

---

### I want to adjust the sell-back ratio

```yaml
store:
  default-sell-limit: 200
  sell-price: {numerator: 2, denominator: 3}
```

Reload when you're done.

`sell-price` is a fraction: what a player receives for selling equals the buy price × `numerator / denominator`. The default `2/3` pays back roughly 66.7% of the purchase price.

**How to think about the ratio.** A higher fraction makes players happier to convert surplus production into coins, but "buy it, then sell it back" also costs them less. A lower fraction keeps inflation down, at the risk of players deciding that selling isn't worth the trip.

**⚠ There is one hard ceiling: `numerator` can never exceed `denominator` — a sell price may never be higher than the buy price.** Writing `3/2` is rejected outright. The most you can set is `1/1`, and there's no direction in which buying and selling turns a profit. That restriction is deliberate anti-arbitrage design.

If one specific item is especially easy to farm, giving it `sell: false` is usually a more precise fix than lowering the global ratio.

---

### I want to change daily task rewards

```yaml
daily-tasks:
  rewards:
    easy: {coins: 40, exp: 30}
    normal: {coins: 70, exp: 50}
    hard: {coins: 120, exp: 80}
```

Reload to apply it.

This doubles the defaults (20/15, 35/25, 60/40). **Rewards already claimed aren't rolled back or topped up.**

The default values add up to **345 coins / 240 EXP** for a full day's clear. That scale is meant to be "worth doing, but not a replacement for the main economy" — you probably don't want players logging in for ten minutes, collecting their tasks, and logging out. Work out what share of player income you want tasks to represent before you double anything.

The difficulty gradient is also worth keeping: hard tasks pay roughly three times what easy ones do, and flattening that spread makes the difficulty labels meaningless.

For how each of the 20 tasks is completed, see [Daily tasks](DAILY-TASKS.md).

---

### I want to swap today's task pool

```yaml
daily-tasks:
  pools:
    easy: [simple_gardener, simple_healthy, simple_shear_sheep, simple_good_morning, simple_crafting, simple_eat_food, simple_pastoral]
    normal: [normal_harvest, normal_dessert, normal_tax_collector, normal_cleanup, normal_deepslate_worker, normal_smelt, normal_blacksmith, normal_fishing]
    hard: [hard_enchant, hard_tycoon, hard_marksman, hard_mlg_water, hard_iron_golem]
  draw-count: {easy: 3, normal: 3, hard: 3}
```

Reload to apply the roster change itself. Then, if you want today's already-drawn tasks replaced right away:

```text
/nekocore tasks status
/nekocore tasks reroll confirm
```

`draw-count` decides how many tasks are drawn per difficulty. Three each is the default, which makes nine tasks a day. Keep in mind that each pool needs at least as many entries as its draw count, or you'll come up short.

**Common pitfall:** **deleting** a task ID from a pool breaks the continuity of existing progress — the reroll notice says as much, that the old rotation progress is archived and new tasks start from 0. Structural changes (pools, timezone, target types) are best made before a rollover with the server stopped, and with a backup taken first. Changing display text only — the `name` and `description` entries in `messages.yml` — is safe at any time and touches no progress.

---

### I want to adjust title prices and perks

```yaml
levelshop:
  enabled: true
  worlds: [world]
  titles:
    mame:
      name: Yuki
      prefix: '&#B6E3D0[Yuki] &r'
      material: SNOWBALL
      slot: 11
      rank: 1
      price: 2500
      auto-checkin: true
      colored-chat: false
      tpn-cooldown-seconds: 120
      afk-exp-multiplier: 1.10
      description: ['&fLight as the first snowfall', '&#B2C3CFAutomatic check-in on join', '&#B2C3CFTeleport cooldown 120s · AFK EXP ×1.10']
```

Reload to apply it. Titles players have already bought aren't revoked, and changing a price doesn't trigger a refund.

**What you can change:** display name, prefix, icon, slot, tier, price, the perk toggles and their values, and the description text.

**What you can't change:** the `mame`, `momo`, and `sora` keys. Those are stable title IDs, and purchase records hang off them.

**Three design rules worth knowing:**

1. **Perks come from the highest tier a player has ever owned.** If someone buys Neko and then wears Yuki's appearance, they still get Neko's teleport cooldown.
2. **The equipped title decides appearance only.** Swapping back and forth never costs anything.
3. **`worlds` decides where the title shop can be opened.** The default is `world` alone. A player standing outside that list is told to head back to the lobby and find the title NPC.

The three tiers' `rank` values (1/2/3) are what define "highest tier" — editing them redefines the whole perk ladder, so think it through before you do.

---

### I want to change the daily enchantment book prices

```yaml
store:
  enchantments:
    pool: [protection, fire_protection, /* ...full list in config.yml... */]
    excluded: [binding_curse, vanishing_curse]
    maximum-price: 1800
    normal-price: 300
    prices: {mending: 3600, silk_touch: 2400, infinity: 2400, swift_sneak: 2700, wind_burst: 3600}
```

Reload when you're ready.

**How the pricing works out:**

- Each day's batch holds **2 books at the vanilla maximum level** and **6 low-tier books**;
- `maximum-price` is what the max-level tier costs, and `normal-price` is what the low tier costs;
- Entries in `prices` are **per-book overrides** (no level multiplication involved). Books that aren't listed fall back to the two tier prices.

So in the example above, Mending is priced separately at 3600, the other max-level books cost 1800, and low-tier books cost 300.

**Two constraints:** low-tier books must be level I–II *and* strictly below the vanilla maximum level; the curses listed in `excluded` never enter the pool. When you edit the pool, leave `binding_curse` and `vanishing_curse` in the exclusion list.

To roll a fresh batch right now:

```text
/nekocore store refresh-enchants
```

Enchantment books are **not bought back** (the store says so on the item), so there's no way for players to buy one and sell it straight back for profit.

---

## Module switches

### I want an AFK pool

```yaml
afk-pool:
  enabled: true
  position-configured: true
  world: world
  teleport:
    x: 120.5
    y: 65.0
    z: -30.5
    yaw: 90.0
    pitch: 0.0
  exit-grace-seconds: 2
  reward:
    interval-seconds: 60
    base-exp: 10
    normal-multiplier: 1.10
    coin-chance: 0.45
    coin-min: 1
    coin-max: 4
  title:
    enabled: true
    hide-while-inventory-open: true
    stay-ticks: 40
    fade-out-ticks: 10
```

The steps:

1. **Build the pool in game first**, then stand at the water's surface in the middle of it and read your exact coordinates with `F3`.
2. Fill those into `teleport`. `yaw` is the horizontal facing and `pitch` is the vertical tilt; `0.0` for both is usually fine.
3. Set `enabled` and `position-configured` to `true` **together**.
4. Reload.
5. Open `/menu` in game and the AFK pool entry should be there.

**Why two locks?** Because coordinates are the easiest thing in this file to get wrong. With only `enabled` as a gate, an admin in a hurry could switch the feature on before the coordinates are filled in, and every player on the server who clicks would be sent to `x: 0.5, y: 64.0, z: 0.5` — the void, or somebody's base. Two locks mean **the plugin will never teleport anyone to a placeholder coordinate.**

**How it actually behaves.** Clicking the menu entry sends the player above the pool. The timer only starts **once they're in the water**, not the moment they land. If they briefly step out — jumping up for air, say — `exit-grace-seconds: 2` gives them two seconds of slack: back in the water within that window and the count continues, longer than that and it resets.

Rewards are handed out on the `interval-seconds` rhythm, every 60 seconds (one minute) by default: a fixed `base-exp` amount of experience multiplied by `normal-multiplier`, plus coins rolled against `coin-chance`, paying somewhere between `coin-min` and `coin-max`.

**Common pitfalls:**

- **Never copy the sample coordinates.** That `120.5 / 65.0 / -30.5` set is there for demonstration; pasted onto your server it sends players somewhere nobody has ever seen.
- **`enabled: true` with `position-configured: false` does nothing at all.** That's expected behaviour, not a bug.
- **Having the inventory open doesn't pause the timer.** `hide-while-inventory-open: true` only hides the on-screen title so the player can see their inventory; the count keeps running.

---

### I want a weekly coin leaderboard

```yaml
weekly-coin-leaderboard:
  enabled: true
  position-configured: true
  world: world
  x: 10.5
  y: 70.0
  z: -5.5
  yaw: 180.0
  refresh-seconds: 30
```

The steps:

1. Stand where you want the board to hang and read the coordinates with `F3`.
2. Fill them into the configuration and open both locks (`true`).
3. Reload.
4. Back in game, a floating text board should appear at that spot.

**What it shows:** the top 10 players by this week's coin **income**. Note the wording — it's "how much was earned this week", not "how much is in the balance right now". A wealthy veteran who earned nothing this week doesn't automatically sit at the top. The board's text lives under `weekly-coins` in `messages.yml` (first, second, and third place each have their own colour).

**The two locks behave differently here** than they do for the AFK pool. With `position-configured: false`, **tracking carries on as normal** — no display is created, and a WARNING goes into the log to remind you. So you can safely run `enabled: true` early to collect data, and add the coordinates later once you've decided where the board should go.

**Common pitfalls:**

- **If the board isn't visible, check two things first:** the world is loaded, and both switches are `true`.
- NekoCore **only manages TextDisplays carrying its own marker**. It cleans up the ones it created and never touches floating entities belonging to other plugins. The flip side: if another plugin has put something at the same spot, both may show.
- **Nothing is ever spawned at 0,0,0 by default.**

---

### I want to add a Citizens mascot

One clarification first, because this is probably the most misunderstood part of the plugin:

> **The Mascot is a module built into NekoCore, not a separate third-party plugin.**
> **Citizens only provides the NPC entity itself** — the model standing there.

So the arrangement is: Citizens supplies the body, NekoCore supplies the soul.

```yaml
mascot:
  enabled: true
  npc-id: 3
  hologram-enabled: true
  hologram-lines:
    - '&#9FD9F6✦ {server} Mascot ✦'
    - '&#B2C3CFRight-click to say hello'
  hologram-y-offset: 2.25
  interaction-window-seconds: 15
  normal-click-limit: 5
  over-limit-chat-cooldown-seconds: 2
  normal-particle: minecraft:end_rod
  normal-particle-count: 8
  over-limit-particle: minecraft:smoke
  over-limit-particle-count: 18
```

The steps:

1. Install [Citizens](https://github.com/CitizensDev/Citizens2) (the [Citizens downloads page](https://wiki.citizensnpcs.co/Downloads) has the official instructions), then **restart fully**.
2. Create and place the NPC yourself with Citizens' own commands — appearance, name, and skin are all your call.
3. Stand next to the NPC and read its number with `/npc id`.
4. Put that number into `mascot.npc-id` and set `enabled` to `true`.
5. To change what it says, edit `mascot.replies` (the ordinary reply pool) and `mascot.over-limit-replies` (the pool used when someone clicks too fast) in `messages.yml`.
6. **Restart fully.**

To confirm it worked, two lines of hologram text should appear above the NPC as you approach; right-clicking it produces a line of chat and a puff of particles.

**How the interaction is designed.** A right-click triggers one random reply plus a few particles. Click rapidly — more than 5 times inside the default 15-second window — and it switches to the "over limit" pool, whose replies have a "slow down a moment" tone, along with a different particle set and a 2-second chat cooldown to keep the spam down.

**NekoCore doesn't create, rename, move, or reskin any NPC.** Deleting one is also your job, done in Citizens. That boundary is intentional: your NPCs belong to you.

**Common pitfalls:**

- **Clicking does nothing?** Work through it in order: is Citizens installed? Did the NPC actually spawn? Does `npc-id` match the number from `/npc id`? Is `enabled` set to `true`? Are you right-clicking with your main hand?
- The old `citizens-npc-id` key in existing configurations is still read for compatibility, but new configurations should use `npc-id`.
- An uncommon but real one: the Citizens build itself has to be compatible with Paper 26.2. If it isn't, NekoCore simply disables Mascot quietly, possibly with a one-time warning in the log.

---

### I want the NPC to say something else when clicked

Edit `messages.yml`:

```yaml
mascot:
  replies:
    - '&#9FD9F6Mascot: &fWelcome to {server}!'
    - '&#9FD9F6Mascot: &fGood luck on your adventures today.'
    - '&#9FD9F6Mascot: &fAsk an admin if you need a hand.'
  over-limit-replies:
    - '&#EDB9CEMascot: &fSlow down a little, I am still here.'
    - '&#EDB9CEMascot: &fThat was quick, give me a moment.'
```

Reload when you're done.

`replies` is the pool used for ordinary clicks, and `over-limit-replies` is the pool for clicking too fast. Add or remove entries in either one freely; both support `{server}`.

If you want the NPC to feel like a character, **the difference in tone between the two pools is the whole trick**: the ordinary replies can be warm and enthusiastic, while the over-limit ones work best as gentle complaining — "I'm still here, you know" — rather than an error message. Players read that as personality, not as a system notice.

---

### I want to turn Bag off

```yaml
bag:
  enabled: false
```

Reload to apply it.

**Nothing is deleted.** Players' items stay exactly where they are in the database, and they'll be available again the moment you set `enabled` back to `true`.

**When it's worth turning off:** if your server already has a mature storage system, running two of them leaves players wondering where their things actually are. Closing one entrance is friendlier than making people guess.

**One note:** before switching it off, it's worth making sure nobody is in the middle of an operation in the Bag screen. Anything already open finishes on its own, but items mid-transfer deserve a clean ending.

---

### I want Bag writable only in the main world

```yaml
bag:
  enabled: true
  writable-worlds: [world]
  unlock-levels:
    '27': 10
    '36': 25
  readonly-notice-seconds: 4
```

Reload to apply it.

`writable-worlds` is a **whitelist**: worlds on the list allow deposits and withdrawals, and **every other world is read-only** — the lobby included, along with any world you add later.

That "read-only by default" behaviour matters: a new world is automatically safe, and you don't have to remember to come back and edit this.

Slot unlocking is handled by `unlock-levels`, and the example above means slot 27 needs level 10, while slot 36 needs level 25. Below that level, those slots stay unavailable.

`readonly-notice-seconds` is how long the notice in a read-only world stays on screen, in seconds. A player opening the Bag in a read-only world sees a short message explaining that they can only look for now.

**When you need this:** when you've used something like Multiverse-Inventories to give each world its own inventory. If Bag were writable everywhere, players could tuck valuables inside it and carry them straight across world boundaries, straight through the rule you set up.

---

## Final things before going live

### I want to confirm the config is valid before changing it

```text
/nekocore config check
```

This parses and validates every configuration file on disk, reports any problems, and **applies none of it**.

The difference between it and `reload` is worth committing to memory:

| | What it does | When to use it |
| --- | --- | --- |
| `config check` | Validates only, changes nothing | Right after editing YAML, to confirm it's written correctly |
| `reload` | Validates → builds a complete new configuration → swaps it in as a whole on success | Once you're satisfied and want it live |

Both commands run the same validation logic, so if `config check` passes, `reload` will almost never fail on the configuration itself.

**How to read an error:** the message gives you the YAML path (something like `store.products.bred.material`) and the type or range it expected. When a Material name is misspelled you also get a near-name suggestion. Fix it as instructed and try again.

**A failed `reload` is not a disaster.** The server carries on with the last valid configuration and players notice nothing at all. Repair the file and try once more.

---

### I want to see what state each module is in

```text
/nekocore status
```

(`/nekocore doctor` is a synonym; either one works.)

It reports the NekoCore, Paper, and Java versions, the database schema, and the state of Store, Bag, Tasks, GeoIP, Mascot, AFK, Leaderboard, and PlaceholderAPI individually.

**This single command answers nine out of ten "feature X isn't working" questions.** When something looks wrong, start here — it's faster than digging through logs.

---

### I want to clean up dropped items

```yaml
cleanup:
  enabled: true
  interval-seconds: 600
  sound:
    enabled: true
    name: minecraft:block.note_block.pling
    volume: 0.6
    pitch: 1.6
```

Reload to apply it.

Every `interval-seconds` seconds, items lying on the ground are cleared. Before the sweep, players get a countdown warning and a sound cue, which gives them a chance to pick up their own things.

To run one sweep right now:

```text
/nekocore cleanup now
```

**Common pitfall:** the smallest allowed `interval-seconds` is 61. If you want it more often than that, trigger `cleanup now` by hand — or reconsider whether you really do. Cleaning too eagerly interrupts players who are in the middle of sorting a chest.

---

## Can't find the change you want?

- **Want to understand why a field is designed the way it is** → [Configuration](CONFIGURATION.md)
- **Want to tune the economy numbers but aren't sure where to land** → [Economy](ECONOMY.md)
- **Want to know exactly how a daily task counts as complete** → [Daily tasks](DAILY-TASKS.md)
- **Edited something that didn't take effect, or a module isn't responding** → [Compatibility and troubleshooting](COMPATIBILITY.md)
- **Want to install Citizens, PlaceholderAPI, Multiverse, or the MMDB** → [Dependencies](DEPENDENCIES.md)
- **Upgrading, rolling back, or uninstalling** → [Installation](INSTALLATION.md)
