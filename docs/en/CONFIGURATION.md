# Configuration

[简体中文](../zh-CN/CONFIGURATION.md) | [Back to README](../../README.en.md)

This page is for people who want to **actually understand the configuration**.

[Recipes](RECIPES.md) answers "I want to change X, which lines do I touch?" This page answers "how does this feature actually behave, and therefore what do these fields mean?" Every module explains its in-game behaviour in plain language first, then lists fields.

If you just want a working server quickly, read [Quick Start](QUICKSTART.md) first and come back later.

---

## The 5 things people actually change

Ninety percent of what new owners want is on this list. **After any edit, run `/nekocore config check` and then `/nekocore reload`.**

| I want to change | Field to edit | Details |
| --- | --- | --- |
| The server name | `branding.server-name` | See "branding" below |
| What the main world is called | `survival.world` | See "Worlds and menu entries" below |
| The check-in reward | `checkin.coins` / `checkin.exp` | See "checkin" below |
| The price of one store product | `store.products.<id>.price` | See "store" below |
| Task rewards | `daily-tasks.rewards` | See "daily-tasks" below |

**Every block below opens.** Click a title to unfold that module's behaviour notes and full field table; left closed, the page is a table of contents.

If you only want to know "which lines do I change?", [Recipes](RECIPES.md) gets you there faster than this page.

---

## Before you start

### Two files

| File | What it controls |
| --- | --- |
| `plugins/NekoCore/config.yml` | Server behaviour. Worlds, economy numbers, menu layout, module switches |
| `plugins/NekoCore/messages.yml` | Every string a player can see |

Both carry a `-version` field (`config-version: 9`, `messages-version: 8`). **Leave those numbers alone.** They tell the plugin how to read your files, and changing them doesn't fix anything.

### Editing rules

YAML expresses hierarchy through **spaces**:

- **Spaces only, never tabs.** They look the same and the parser rejects one of them.
- Sibling fields must be indented identically.
- A colon needs a space after it: `server-name: "Cat Cafe"`.
- Quote any value containing `#`, `:`, or colour codes.

### The workflow after every edit

Startup, check and reload share one validator. Common bad Material / EntityType names and message types are collected together; independent loaders are checked separately. At most 20 issues are displayed with the total count. Complex range/cross-field rules may still report only the first issue in a module; check again after fixing it.

Diagnostics identify file, path, in-game purpose, current value and validation rule. Material means a Minecraft item/block English type; EntityType means a creature type. Source marks supply reliable line numbers; aliases, merge/duplicate keys, special keys or malformed YAML fall back to path/purpose, not guessed lines. Replacements are suggested only when available.

For example, bread's `material: bred` suggests `material: BREAD` in the original field. Admins can hold an item and use `/nekocore lookup`, or search `/nekocore lookup bread`. Replace only bad list entries. Complete opt-in [presets](PRESETS.md) complement the existing detailed field tables.

```text
/nekocore config check     ← validates only, applies nothing
/nekocore reload           ← validates, then swaps the whole set
```

`reload` runs **parse → validate → build a complete new configuration object → swap on success**. If any part fails, the server keeps running on the previous valid configuration. There's no half-new, half-old state.

**These changes need a full restart; reload can't cover them:** replacing the JAR, changing `database.filename`, installing a new plugin, or changes to a world plugin.

(Adding or replacing an MMDB file is *not* on that list — a reload re-opens it. See the GeoIP section below.)

`database.filename` is a special case worth knowing: `reload` will **explicitly refuse** a configuration in which the filename changed, telling you a full restart is required, rather than leaving you in a half-applied state.

### What players notice when a reload succeeds

Worth reading once, because a reload **isn't entirely invisible**:

- **Every NekoCore GUI closes** — `/menu`, the store, Bag, tasks, the title shop.
- **Any in-progress custom-quantity input in the store is cancelled.**
- **Every pending `/tpn` request is cancelled**, with both sides told the configuration is updating.
- **The GeoIP session cache is cleared**, and online players are resolved again.

On a busy server, some people will notice. That's usually fine, but **if you're reloading after a large change, do it when the server is quiet** or give people a heads-up.

### Two preconditions for reload

There are two situations where `reload` refuses outright:

1. **Another reload is already running** → you're told to try again shortly.
2. **A store or Bag transaction is in flight, or a title purchase is awaiting confirmation** → you're told to wait a moment.

The second is data protection: a trade partway through a two-stage commit across the database *and* the player's inventory should not be interrupted by a configuration swap.

---

<a id="branding--服务器名字"></a>
<details>
<summary><b>branding — your server's name</b><br><sub>The server name, shown in TAB, the welcome title, GUI titles, and tips</sub></summary>

### What it actually does

You write a name in the config, and NekoCore substitutes it anywhere players can see it.

Places that support `{server}`: the TAB header, the subtitle of the join welcome title, GUI titles, tip messages, the mascot hologram, the daily task called "Good morning, {server}!", and the title shop header.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `branding.server-name` | string / `My Server` | 1–64 displayable characters |

### Example

```yaml
branding:
  server-name: "Cat Cafe"
```

To verify, `reload` and look at the TAB header and the `/menu` title.

**When it fails:** an empty value or control characters reject the entire reload. Quote the value if the name contains `#` or `:`.

**Restart needed?** No, `reload` is enough.

</details>

---

<a id="世界与菜单入口"></a>
<details>
<summary><b>Worlds and menu entries</b><br><sub>Which world each /menu entry points at; disabled entries hide themselves</sub></summary>

### What it actually does

`/menu` is where players reach everything. Each entry on the panel points at a destination:

- **Survival one** points at `survival.world`
- **Survival two** points at `survival-new.world`
- **Minigames** points at the exact coordinates under `minigames`
- **AFK pool** points at the exact coordinates under `afk-pool`

**NekoCore creates none of these worlds.** They must already exist and be loaded, by Multiverse or another world plugin. When a world isn't loaded, its entry **quietly disappears** — not an error, just hidden.

**Auto-layout** is the core behaviour here: entries for disabled or unavailable modules are hidden, and the remaining buttons re-center. So what a player sees in `/menu` is exactly what your server currently offers.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `survival.enabled` | boolean / `true` | Main survival entry |
| `survival.world` | world name / `world` | Main world |
| `survival.command` | string | World teleport command, no leading `/`, must contain `{player}` and `{world}` |
| `survival-new.enabled` | boolean / `false` | Second world entry |
| `survival-new.world` | world name / `world_secondary` | Second world |
| `survival-new.command` | string | As above |
| `minigames.enabled` | boolean / `false` | Minigames entry |
| `minigames.world` | world name | Target world |
| `minigames.x/y/z` | number | Exact coordinates |
| `minigames.yaw/pitch` | number | Facing |
| `gui.enabled` | boolean / `true` | The whole `/menu` |
| `gui.auto-layout` | boolean / `true` | Hide unavailable entries and re-center |
| `gui.title` | string | Panel title, supports `{server}` |
| `gui.rows` | 1–6 / `5` | Panel rows |
| `gui.items.<id>.slot` | integer | Which cell (only used when auto-layout is off) |
| `gui.items.<id>.material` | Material | Icon |
| `gui.items.<id>.name` | string | Display name |
| `gui.items.<id>.lore` | string list | Description lines |

The eight ids under `gui.items` are `profile`, `daily`, `checkin`, `privacy`, `survival`, `survival-new`, `minigames`, and `afk-pool`.

### Example: a single-world survival server

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
gui:
  enabled: true
  auto-layout: true
```

**What changed:** the two optional areas are switched off, so the menu shows only entries that genuinely exist.

**Why written this way:** disabled modules leave no gaps — auto-layout re-centers what remains. Players see a clean panel with four or five buttons.

**Restart needed:** no, `reload`.

**⚠ About those coordinates:** `0.5 / 64.0 / 0.5` are **placeholders**. Their only purpose is to show the shape of the configuration. As long as `enabled` is `false`, NekoCore will never teleport anyone to them and will never generate anything at `0,0,0`. This is deliberate: **NekoCore does not guess coordinates.**

### Example: lobby plus survival

```yaml
survival:
  enabled: true
  world: survival
  command: 'mvtp {player} {world}'
```

**What changed:** `survival.world` moved from `world` to `survival`.

**Why written this way:** it points the main entry at your survival world. The lobby's spawn and respawn rules belong to your lobby plugin or Multiverse configuration — **NekoCore does not take over death respawn**; here it only handles getting the player from the menu to the world.

**Restart needed:** yes if you just installed Multiverse. If the world is already loaded and you only changed the `world` value, `reload` is fine.

### Example: hand-placed menu layout

```yaml
gui:
  enabled: true
  auto-layout: false
  rows: 5
  items:
    profile:
      slot: 10
    daily:
      slot: 12
    checkin:
      slot: 14
    privacy:
      slot: 16
    survival:
      slot: 28
```

**What changed:** `auto-layout` off, and every entry given a fixed `slot`.

**Why written this way:** some owners prefer a fixed layout because players build muscle memory — "check-in is always middle of the third row." The cost of auto-layout is that positions shift as modules come and go.

**Restart needed:** no, `reload`.

**Common mistake:** a slot out of range (beyond `rows × 9`) or duplicated will fail the reload and tell you which item was at fault.

### What if you don't have Multiverse

The default `survival.command` is `mvtp {player} {world}`, which is a Multiverse command.

**Without Multiverse, ignore it.** When NekoCore finds the command unavailable, it **safely teleports the player to the loaded world's Paper spawn point**. So a single-world server is perfectly fine without Multiverse.

**Additional worlds that rely on command routing** are where Multiverse becomes worth installing.

</details>

---

<a id="database--数据存储"></a>
<details>
<summary><b>database — where data lives</b><br><sub>Which file holds your data, and how often it's flushed</sub></summary>

### What it actually does

NekoCore keeps profiles, economy, homes, check-ins, titles, Bag contents, store quotas, transaction logs, task progress, and weekly statistics in a single SQLite file.

Writes aren't flushed on every operation — `save-interval-seconds` controls how often the background save runs. Which is also why **a clean shutdown matters**: `stop` gives writes a chance to finish properly.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `database.filename` | string / `nekocore.db` | Filename inside the plugin directory |
| `database.save-interval-seconds` | integer / `30` | Background save interval |

### Example

```yaml
database:
  filename: nekocore.db
  save-interval-seconds: 30
```

**Should you change `save-interval-seconds`?** Most servers shouldn't. Lowering it increases disk writes; raising it increases how much data a crash could lose. The 30-second default is a reasonable balance.

**Restart needed:** changing `filename` **requires a full restart** — the database is opened at startup. `save-interval-seconds` takes effect through `reload`.

When setting `filename`, don't include path separators. It's a filename inside the plugin directory, nothing more.

</details>

---

<a id="home--小窝"></a>
<details>
<summary><b>home — where players live</b><br><sub>Player homes, stored per world; sleeping in a bed records one automatically</sub></summary>

### What it actually does

Homes are stored **per world**. A player can set one in the main world, one in the Nether, and one in the End, and none of them overwrite the others.

```text
/sethome                  record the current position
/home                     return to this world's home
/home <worldName>         return to another world's home
/check home <worldName>   see where a home is
```

**Automatic bed homes** are a separate mechanism: when a player successfully lies down in a bed, NekoCore looks for a safe standing position next to it and records that as the home for that world.

**It doesn't change vanilla respawn points or sleeping behaviour.** It simply marks a convenient place to return to.

**When no safe spot exists,** the player is told there's nowhere suitable to stand next to the bed and **their existing home is left untouched**. In other words: **it would rather do nothing than record a position that traps someone.**

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `home.bed-auto-set.enabled` | boolean / `true` | Save a home when lying in a bed |
| `home.bed-auto-set.worlds` | string list / `[world]` | Worlds where this applies, **case-sensitive** |

### Example

```yaml
home:
  bed-auto-set:
    enabled: true
    worlds: [world, world_nether]
```

**What changed:** the Nether was added to `worlds`.

**Why written this way:** the default covers the main world only. Adding the Nether means sleeping there also records a home — and a Nether home is often essential, since beds explode there and players can't rely on respawning in one.

**Restart needed:** no, `reload`.

**Common mistake:** mismatched capitalisation. `World` and `world` are different worlds to Paper. An empty list, or `enabled: false`, turns the whole feature off.

</details>

---

<a id="checkin--每日签到"></a>
<details>
<summary><b>checkin — the daily reward</b><br><sub>Once-a-day coins and experience; the timezone decides when the day rolls over</sub></summary>

### What it actually does

Once per day, a player can claim a little money and experience with `/checkin` or through `/menu`.

**"Once per day" means calendar day, not every 24 hours.** The distinction matters:

- A player who claims at 23:50 can claim again at 00:10 — twenty minutes later, but a different calendar day.
- On a rolling 24-hour rule, that same player would have to wait until 23:50 the next day.

**Players don't need to rejoin when the day rolls over.** Someone idling through midnight simply finds the new check-in available.

**Players who haven't claimed get a reminder** on join.

**On a successful claim:** a little money, a little experience, a sound, and a few small white sparks rising off the player.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `checkin.enabled` | boolean / `true` | Master switch |
| `checkin.timezone` | timezone name / `Asia/Shanghai` | Decides when a "day" starts |
| `checkin.coins` | integer / `100` | Coin reward |
| `checkin.exp` | integer / `50` | Experience reward |
| `checkin.sound.*` | sound config | Claim sound |
| `checkin.particle-count` | integer / `16` | Particle count; `0` disables |

**This `timezone` also affects daily tasks and the enchantment book batch.** Changing it redefines the whole server's "day".

### ⚠ A known comment that doesn't match behaviour

In `config.yml`, above `checkin.enabled`, a comment claims no rejoin is needed when crossing midnight while online. **That comment does not match the actual behaviour**, and it's worth stating plainly:

**Automatic check-in (for players with the perk) fires once, at login.** There is no midnight timer anywhere near the check-in system. So a player idling through midnight:

- **won't be checked in automatically** — that waits for their next login, or a server restart or plugin reload running the join path;
- **can still claim manually with `/checkin`**, because the date is re-read when the claim is written;
- **won't see the panel's status text refresh** to "available" until they reopen `/menu`.

This mismatch lives in a resource-file comment and affects no functionality, but it's worth knowing so you don't reason from the comment. Manual `/checkin` is always the fallback.

### Example

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

**What changed:** `coins` from 100 to 200, `exp` from 50 to 80.

**Why written this way:** doubling the check-in reward. This is a gentler way to answer "players say they can't earn anything" than cutting store prices.

**The trade-off:** once check-in income doubles, store goods become relatively cheaper and titles are reached sooner. Decide deliberately — or adjust prices alongside.

**Restart needed:** no, `reload`.

**Note:** reward changes are **not retroactive**. A player who already claimed today doesn't get the difference.

**When it fails:** negative rewards, an invalid timezone name, or a bad sound name all reject the reload.

</details>

---

<a id="daily-tasks--每日任务"></a>
<details>
<summary><b>daily-tasks</b><br><sub>How many tasks are drawn each day, what they pay, and how the pools are built</sub></summary>

### What it actually does

At 00:00 in the `daily-tasks.timezone`, NekoCore draws a few tasks from each of three pools to form the day's rotation.

**Every player on the server shares the same rotation**, so the thing people discuss in chat is the same thing. **But progress and rewards are per player**, so completing one has no effect on anyone else.

The default is nine tasks a day (three each from easy, normal, and hard), worth **345 coins and 240 experience** if you clear all of them.

Individual task mechanics are documented in [Daily tasks](DAILY-TASKS.md) — all twenty, with how each is completed.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `daily-tasks.enabled` | boolean / `true` | Master switch |
| `daily-tasks.timezone` | timezone name / `Asia/Shanghai` | When the rotation rolls over |
| `daily-tasks.draw-count` | `{easy,normal,hard}` / 3 each | How many to draw per difficulty |
| `daily-tasks.rewards.<difficulty>` | `{coins, exp}` | Reward per difficulty |
| `daily-tasks.pools.<difficulty>` | string list | Candidate task IDs for that difficulty |
| `daily-tasks.rules.<taskId>` | rule object | Target count and applicable blocks/items/entities |
| `daily-tasks.gui.*` | display config | Fillers, slots, sounds, particles |
| `daily-tasks.feedback.*` | feedback config | Sound and particles on completion |

Fields inside `rules` vary by task: most use `target`, one uses `target-minutes`, and several carry `blocks`, `materials`, or `entities` lists.

### Example

```yaml
daily-tasks:
  enabled: true
  timezone: Asia/Shanghai
  draw-count: {easy: 3, normal: 3, hard: 3}
  rewards:
    easy: {coins: 40, exp: 30}
    normal: {coins: 70, exp: 50}
    hard: {coins: 120, exp: 80}
```

**What changed:** all three difficulties had `coins` and `exp` doubled.

**Why written this way:** to give tasks a larger share of player income. The defaults are tuned for "worth doing, but not a replacement for the main economy".

**The trade-off:** a full clear goes from 345 to 690 coins a day. If you also run the store and titles, you may want to adjust those together.

**Restart needed:** no, `reload`. **Already-claimed rewards are not rolled back or topped up.**

### About stable task IDs

The ids in `pools` and `rules` (`simple_gardener`, `normal_harvest`, `hard_marksman`, …) are **stable**. They appear in the database's rotation and progress records.

**To change what a task is called, edit `name` and `description` in `messages.yml` — never the ID.** Removing or renaming an ID from a pool breaks the continuity of existing progress.

**A rule that must stay:** Happy Ghast is permanently excluded from the marksman task. That exclusion is enforced in both code and config validation.

**When to change the timezone or pools:** these affect the next rotation. Mid-operation changes are best done **before the rollover, with the server stopped and a backup taken.**

</details>

---

<a id="store--商店"></a>
<details>
<summary><b>store</b><br><sub>109 products, prices, daily buy/sell limits, and the daily enchantment books</sub></summary>

### What it actually does

`/store` opens an eight-category shop with 109 products by default.

**Right-click to buy, left-click to sell.** After picking a product you choose a quantity — from presets, or by typing a number into chat (that input is never broadcast to other players). Confirming completes the trade immediately.

**Daily quotas** are the most important mechanism here:

- **Buy limits** prevent "unlimited coins into unlimited resources";
- **Sell limits** prevent "unlimited mob farm into unlimited coins".

Quotas are per player. Players can see their usage with `/nekocore store status`.

**Daily enchantment books** are a special category that rotates at 04:00 Beijing time: two vanilla-max-level books plus six lower-tier ones. **Books can't be sold back**, so nobody can buy and resell them for profit.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `store.enabled` | boolean / `true` | Master switch |
| `store.category-rows` | integer / `5` | Category page rows |
| `store.product-layout.slots` | integer list | Cells products occupy within a page |
| `store.product-layout.previous-slot` | integer / `45` | Previous-page button |
| `store.product-layout.next-slot` | integer / `46` | Next-page button |
| `store.product-layout.info-slot` | integer / `49` | Information cell |
| `store.default-sell-limit` | integer / `200` | Default daily sell cap per product |
| `store.sell-price` | `{numerator, denominator}` / `2/3` | Sell-back ratio |
| `store.input-timeout-seconds` | integer / `30` | How long a typed quantity stays valid |
| `store.maximum-custom-quantity` | integer / `1000000` | Upper bound for typed quantities |
| `store.categories.<id>` | category object | `name`, `material`, `slot` |
| `store.products.<stable-id>` | product object | See below |
| `store.enchantments.*` | enchantment config | See the next section |

Product object:

| Field | Notes |
| --- | --- |
| `category` | Which category it belongs to |
| `material` | The item actually given |
| `name` | Display name |
| `price` | Price per unit in coins |
| `sell` | Whether players may sell it back |
| `buy-limit` | Daily buy cap. Omitted = the material's vanilla max stack size |
| `sell-limit` | Daily sell cap. Omitted = `store.default-sell-limit` |

### Example: a single product

```yaml
store:
  products:
    bread:
      category: food
      material: BREAD
      name: '面包'
      price: 12
      sell: true
```

**What changed:** `price` from 9 to 12.

**Why written this way:** bread is the most common early-game food, and nudging its price up gives early coins a little more meaning.

**Restart needed:** no, `reload`.

**⚠ Change fields, never IDs.** The `bread` key is a stable product ID that appears in quota records, transaction logs, and crash recovery data. Rename it and NekoCore sees a brand-new product: quotas start fresh and the old records become orphans. To change the display name, change `name`.

### Example: a limit on an easily farmed product

```yaml
store:
  products:
    echo_shard:
      category: special
      material: ECHO_SHARD
      name: '回响碎片'
      price: 1600
      sell: true
      buy-limit: 4
```

**What changed:** `buy-limit: 4` was added.

**Why written this way:** echo shards are among the strongest tradeable items. Capping purchases at four a day means even wealthy players can't finish gearing in one sitting.

**Restart needed:** no, `reload`.

**Worth noting:** the default configuration already ships this product with `buy-limit: 4`, so you can use it as a template.

### Example: adjusting the sell-back ratio

```yaml
store:
  default-sell-limit: 200
  sell-price: {numerator: 1, denominator: 2}
```

**What changed:** `sell-price` from `2/3` to `1/2`.

**Why written this way:** halving the buy-back price. Players earn less from selling, which lowers inflationary pressure.

**The trade-off:** players will feel that selling isn't worth it, and may start dumping surplus into lava instead of converting it to coins. If your server already has a dropped-item clutter problem, cutting the sell price makes it worse.

**Restart needed:** no, `reload`.

**⚠ There's a hard constraint: `numerator` can never exceed `denominator`.** In other words, **the sell price can never be higher than the buy price** — writing `3/2` is rejected outright with "the sell price cannot exceed the buy price". This closes off the "buy then sell for profit" arbitrage path, which means you can't tilt it in the profitable direction. The most you can do is `1/1`, a frictionless round trip.

### About the daily enchantment books

```yaml
store:
  enchantments:
    pool: [protection, fire_protection, /* ...full list in config.yml... */]
    excluded: [binding_curse, vanishing_curse]
    maximum-price: 1800
    normal-price: 300
    prices: {mending: 3600, silk_touch: 2400, infinity: 2400, swift_sneak: 2700, wind_burst: 3600}
```

**How the pricing works:**

- Each day brings **two vanilla-max-level** books plus **six lower-tier** ones;
- `maximum-price` is the max-tier price and `normal-price` is the lower-tier price;
- `prices` holds **per-book overrides** (not multiplied by level). Books not listed there use the two tier prices above.

So in the example above, Mending is priced separately at 3600, other max-tier books at 1800, and lower-tier books at 300.

**Two constraints:**

- Lower-tier books must be level I–II and **strictly below** the vanilla maximum;
- Curses in `excluded` never enter the pool. **Don't move those two entries out.**

**Rotating the batch:** under normal operation nothing needs doing — it rotates at 04:00 daily. To force it, use `/nekocore store refresh-enchants`, which also resets that period's purchase counts.

</details>

---

<a id="bag--随身仓库"></a>
<details>
<summary><b>bag — portable storage</b><br><sub>Portable storage: which worlds allow taking and placing, and which levels unlock slots</sub></summary>

### What it actually does

Bag is a **cross-world container that follows the player**, opened with `/bag`. It differs from an ender chest in that it doesn't occupy inventory space, grows with the player's level, and obeys a world whitelist.

**`writable-worlds` is a whitelist.** Listed worlds allow taking and placing; **every other world is read-only** — including your lobby, and including any world you add later.

**That "read-only by default" semantic matters:** a new world is automatically safe, without you having to remember to change anything.

**Slot unlocking** is level-gated: 18 slots below level 10, 27 from level 10, and 36 from level 25. It's a gentle growth incentive as players level up.

**Opening Bag in a read-only world** shows a notice pointing the player at a survival world.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `bag.enabled` | boolean / `true` | Master switch |
| `bag.writable-worlds` | world name list / `[world]` | Worlds where taking and placing are allowed |
| `bag.unlock-levels` | `slot: level` map | See the warning below — these do **not** drive capacity |
| `bag.readonly-notice-seconds` | integer / `4` | Throttle interval for the read-only notice |

**`readonly-notice-seconds` is a throttle window, not a display duration.** It means "at most one reminder in this many seconds" — so a player repeatedly trying to place items in a read-only world isn't spammed with a notice on every click.

### Example

```yaml
bag:
  enabled: true
  writable-worlds: [world, world_secondary]
  unlock-levels:
    '27': 10
    '36': 25
  readonly-notice-seconds: 4
```

**What changed:** the second survival world was added to `writable-worlds`.

**Why written this way:** with two survival worlds, allowing both is reasonable — players moving between them shouldn't be restricted.

**Restart needed:** no, `reload`.

**Note on `unlock-levels` syntax:** quote the keys (`'27'`), because they represent slot counts. The values are the required levels.

### ⚠ About `unlock-levels`: an implementation detail you need to know

```yaml
bag:
  unlock-levels:
    '27': 10
    '36': 25
```

**Most owners read these two numbers as "the level at which each size unlocks". That is not what they do.**

Bag **capacity is computed separately and is hardcoded**, recognising only two thresholds:

| Condition | Capacity |
| --- | --- |
| Level ≥ **36** | 36 slots |
| Level ≥ **27** | 27 slots |
| Otherwise | 18 slots |

The **level values** inside `unlock-levels.27` and `unlock-levels.36` (10 and 25 in the example) play no part in that calculation. They do two things:

1. On startup, they seed bag capacity for existing players based on those two thresholds;
2. They supply the `{level27}` and `{level36}` placeholders in `bag-status-lore` in `messages.yml`, which is where the "Lv.X unlocks" wording comes from.

**So if you set `'27': 30`, players get 27 slots the moment they reach level 27, while the interface still says "Lv.30 unlocks 27 slots".** The two will disagree.

If you want to change the unlock pacing, either leave the defaults alone or **also update the matching text in `messages.yml`** so it describes the real thresholds.

Capacity also **only ever increases**: once a player has unlocked 27 slots, levelling back down doesn't take them away.

### Coexisting with Multiverse-Inventories

If you use MVI for per-world inventories, then a fully writable Bag would let players stash valuables and carry them across world boundaries, **bypassing your rules**.

The `writable-worlds` whitelist exists for exactly this: make survival worlds writable and everything else read-only.

**This combination isn't covered by the automated tests.** It's worth verifying world switching, death, disconnection, and a full inventory on a test server.

</details>

---

<a id="tpn--传送请求"></a>
<details>
<summary><b>tpn — teleport requests</b><br><sub>How long teleport requests stay valid, and their cooldowns</sub></summary>

### What it actually does

`/tpn <player>` sends a request; the other player answers with `/yes` or `/no`.

**This is a two-sided consent design.** It isn't "click and appear" — the person being visited gets a say.

**Requests expire**, 60 seconds by default. **The sender has a cooldown**, 180 seconds by default, which a title perk can shorten to 30.

**On acceptance, the requester is moved to near where the other player was when they accepted.** Note the detail: it doesn't track their live position. That avoids the strange landings you'd get if someone accepted while running.

If either side disconnects, or the plugin reloads its configuration, the pending request is cancelled.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `tpn.enabled` | boolean / `true` | Master switch |
| `tpn.timeout-seconds` | integer / `60` | How long a request stays valid |
| `tpn.cooldown-seconds` | integer / `180` | Normal cooldown |
| `tpn.fast-cooldown-seconds` | integer / `30` | Cooldown with `nekocore.perk.fast-tpn` |
| `tpn.sound.*` | sound config | Request sounds |
| `tpn.particle-count` | integer / `12` | Particle count |

### Example

```yaml
tpn:
  enabled: true
  timeout-seconds: 60
  cooldown-seconds: 180
  fast-cooldown-seconds: 30
  sound: {name: 'minecraft:block.amethyst_block.chime', volume: 0.6, pitch: 1.5}
  particle-count: 12
```

**Should you change the cooldown?** It depends on how scarce you want teleport requests to feel.

- **The cooldown exists to prevent pestering.** Too short, and a player can spam the same person indefinitely. 180 seconds is a conservative value that suits "TPN is a privilege to be used with restraint".
- **`fast-cooldown-seconds` is part of a title's value.** Changing it changes what titles are worth.

**Restart needed:** no, `reload`.

</details>

---

<a id="leveling--等级曲线"></a>
<details>
<summary><b>leveling — the level curve</b><br><sub>How the levelling curve is calculated, and what each next level costs</sub></summary>

### What it actually does

Players level up as their accumulated experience crosses a threshold. The curve is:

```text
experience needed to go from level L to L+1 = base + linear × (L-1) + quadratic × (L-1)²
```

The defaults are `base: 100`, `linear: 50`, `quadratic: 0` — each level costs 50 more than the last, a gentle straight line.

**What levels are for:** unlocking Bag slots (27 slots at level 10 by default, 36 at level 25). So experience is real progression, not just a number.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `leveling.base` | integer / `100` | Experience for level 1 → 2 |
| `leveling.linear` | number / `50` | Linear increment per level |
| `leveling.quadratic` | number / `0` | Squared increment per level |
| `leveling.max-level` | integer / `10000` | Level cap |

### Example: a steeper late game

```yaml
leveling:
  base: 100
  linear: 50
  quadratic: 0.5
  max-level: 10000
```

**What changed:** `quadratic` from `0` to `0.5`.

**Why written this way:** adding the squared term keeps early levelling close to the default while noticeably slowing the late game — suited to "levels are a long-term goal".

**The trade-off:** later levels become genuinely slow. If your Bag unlocking hangs off level 25, players will take considerably longer to get there. Work out the pacing you want before changing this.

**Restart needed:** no, `reload`.

**Note:** changing the curve doesn't make anyone lose or gain levels — it only affects what the next level costs.

</details>

---

<a id="levelshop--头衔"></a>
<details>
<summary><b>levelshop — titles</b><br><sub>Three titles: prices, prefixes, and the perks they carry</sub></summary>

### What it actually does

Three title presets: Yuki, Momo, and Neko. Bought with coins, and once owned they:

- display the matching title prefix;
- affect the name tag and chat prefix;
- carry perks: automatic check-in, safe chat colours, a shorter teleport cooldown, and slightly more experience while AFK.

**Three design rules worth knowing:**

1. **Perks follow the highest tier owned.** A player who buys Neko and then displays Yuki keeps Neko's teleport cooldown. Swapping never costs them anything.
2. **The equipped title only changes appearance.** Switching around never loses anything.
3. **The title shop is opened by admins only.** `/nekocore levelshop open <player>` — there's no player command for it.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `levelshop.enabled` | boolean / `true` | Master switch |
| `levelshop.worlds` | world name list / `[world]` | Worlds where the shop may be opened |
| `levelshop.permissions.*` | permission node names | The three perk nodes |
| `levelshop.titles.<id>.name` | string | Display name |
| `levelshop.titles.<id>.prefix` | string | Chat / name tag prefix |
| `levelshop.titles.<id>.material` | Material | Icon |
| `levelshop.titles.<id>.slot` | integer | Position within the shop |
| `levelshop.titles.<id>.rank` | integer | Tier (drives the perk ladder) |
| `levelshop.titles.<id>.price` | integer | Price |
| `levelshop.titles.<id>.auto-checkin` | boolean | Grants automatic check-in |
| `levelshop.titles.<id>.colored-chat` | boolean | Grants chat colours |
| `levelshop.titles.<id>.tpn-cooldown-seconds` | integer | Teleport cooldown at this tier |
| `levelshop.titles.<id>.afk-exp-multiplier` | number | AFK experience multiplier at this tier |
| `levelshop.titles.<id>.description` | string list | Detail-page text |

### Example

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
      price: 1500
      auto-checkin: true
      colored-chat: false
      tpn-cooldown-seconds: 120
      afk-exp-multiplier: 1.10
      description: ['&f像初雪一样轻盈', '&#B2C3CF上线自动签到', '&#B2C3CF传送冷却 120 秒 · 挂机 EXP ×1.10']
```

**What changed:** `price` from 2500 to 1500.

**Why written this way:** making the first tier easier to reach gives new players a closer short-term goal.

**Restart needed:** no, `reload`. **Already-purchased titles are not revoked, and price changes are not refunded.**

**⚠ What you can't change:** the keys `mame`, `momo`, and `sora`. They're stable title IDs, and players' purchase records hang off them.

**⚠ Two more hard validations:**

- **Exactly three titles must be configured.** Removing one or adding one is rejected with "configure three title tiers".
- **`slot` must be between 0 and 17**, and neither the slots nor the `rank` values may repeat.

**Be careful with `rank`.** It decides who is the "highest tier", so changing it redefines the perk ladder. The three titles should rank 1/2/3.

**Keep `description` in sync.** The detail page text like "teleport cooldown 120 seconds" is hand-written; changing `tpn-cooldown-seconds` won't update it, and players will read inconsistent information.

</details>

---

<a id="tab--nametag--chat--welcome-title--显示层"></a>
<details>
<summary><b>tab / nametag / chat / welcome-title — the display layer</b><br><sub>TAB, name tags, chat prefix, and the join welcome title</sub></summary>

Public 1.1 keeps a centred `{server}` title, simple whitespace/plain separators and a compact footer, without a subtitle or extra section labels. XYZ, TPS, location prefix, title/level, player name, online count, coins, time and uptime remain. Regions read only the session cache; disable/restore ownership protection remains unchanged. Existing administrator TAB templates survive upgrades.

These are grouped together because their interactions are subtle.

### What they actually do

**TAB** is the list players see when holding Tab. NekoCore fills in the header and footer:

- **Header:** server name, the player's own coordinates, TPS (1 / 5 / 15 minute)
- **Footer:** online count, the player's own coins, UTC+8 time, server uptime

**Refreshing uses a single server-wide task**, reading from Paper and NekoCore's in-memory cache and **never querying the database** — which keeps it cheap.

**Name tags** are the names above players' heads. NekoCore checks whether the scoreboard team has been taken over by another plugin; if it has, it steps aside and logs a hint suggesting the `%nekocore_display_prefix%` integration instead. **It will not fight another plugin for the team.**

**Chat prefix** is the `[Lv.12]` in front of a player's name in chat. If another chat plugin already shows this, turn it off to avoid duplication.

**Join welcome** is the title that fades in when a player joins.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `tab.enabled` | boolean / `true` | Global TAB |
| `tab.show-location-prefix` | boolean / `true` | Whether TAB shows regions |
| `tab.refresh-seconds` | integer / `1` | Refresh interval |
| `nametag.enabled` | boolean / `true` | Name tag prefixes |
| `nametag.scoreboard-check-seconds` | integer / `5` | How often team ownership is checked |
| `chat.level-prefix-enabled` | boolean / `true` | Level prefix in chat |
| `chat.level-prefix` | string | Prefix format |
| `welcome-title.enabled` | boolean / `true` | Join welcome title |
| `welcome-title.delay-ticks` | ticks / `15` | Delay before it appears |
| `welcome-title.fade-in-ticks` | ticks / `10` | Fade in |
| `welcome-title.stay-ticks` | ticks / `60` | Hold |
| `welcome-title.fade-out-ticks` | ticks / `10` | Fade out |

(20 ticks = 1 second.)

The **text** for TAB and the welcome title lives in `messages.yml`: `tab.header`, `tab.footer`, `welcome-title.title`, and `welcome-title.subtitle`.

### Example: turn TAB off and use another plugin

```yaml
tab:
  enabled: false
  show-location-prefix: false
  refresh-seconds: 1
chat:
  level-prefix-enabled: false
```

**What changed:** TAB off entirely, and the chat level prefix off as well.

**Why written this way:** if you already have a TAB plugin you like, hand over ownership explicitly. **When two plugins write the same TAB, what appears depends on who writes last — and that order can change across restarts**, which shows up as "sometimes it works and sometimes it doesn't".

**Restart needed:** no, `reload`.

**If you still want NekoCore data in it:** install PlaceholderAPI and use placeholders like `%nekocore_coins%` and `%nekocore_level%` in the other plugin. NekoCore supplies the data; the other plugin owns the display.

### Example: turn off the chat level prefix

```yaml
chat:
  level-prefix-enabled: false
```

**When you need this:** your chat plugin already uses `%nekocore_display_prefix%`. Without turning this off, the prefix appears twice.

**Restart needed:** no, `reload`.

### Why name tags never show a region

**By design.** Regions appear only in TAB and the chat prefix, never above a player's head. Even in a crowd, a name tag can't leak location information.

**If name tags aren't applying:** check the log for a message about the scoreboard team already being managed by another plugin. That means a scoreboard or prefix plugin has claimed the team and NekoCore deliberately stepped back. The answer is the PlaceholderAPI integration, not competing harder.

</details>

---

<a id="join-info--进服个人信息"></a>
<details>
<summary><b>join-info — personal arrival information</b><br><sub>One personal message, sent to the joining player after a short delay</sub></summary>

JoinInfo is a **chat message sent only to the joining player**, after their cached profile is ready. It is separate from the welcome title, TAB, and holograms.

| Field | Type / default | Notes |
| --- | --- | --- |
| `join-info.enabled` | boolean / `true` | Personal chat block |
| `join-info.delay-ticks` | integer / `30` | Delay after profile preparation; 0–1200 ticks |
| `join-info.links.docs/website/community/discord` | string / `""` | Administrator-provided HTTP(S) URL |

```yaml
join-info:
  enabled: true
  delay-ticks: 30
  links:
    docs: ""
    website: ""
    community: ""
    discord: ""
```

Edit this on the **server machine**, in `plugins/NekoCore/config.yml`. Put your own real URL into an entry only when you have one; empty entries vanish entirely, without empty buttons or placeholder URLs. Text, button labels and hover text live in `messages.yml` under `join-info`.

The implementation uses Adventure **OPEN_URL**, never a command click. Only HTTP(S) URLs with a host and without credentials or whitespace are accepted. `{player}`, `{server}`, `{playtime}`, `{level}`, and `{coins}` read the prepared cache: no SQLite query or extra GeoIP lookup is made.

Run `/nekocore config check`, then `/nekocore reload`. Pending arrival messages are cancelled on reload, quit, and shutdown; new joins use the new settings. To test, rejoin with a player — reload does not rebroadcast to everyone.

</details>

---

<a id="tips--cleanup--氛围与维护"></a>
<details>
<summary><b>tips / cleanup — atmosphere and housekeeping</b><br><sub>Periodic tips, and clearing dropped items off the ground</sub></summary>

### What these actually do

**Tips** broadcast one message to chat at an interval, cycling through the list and returning to the first after the last. Three minutes by default. After a reload, the cycle restarts from the first message.

The default tips cover what new players need: where `/menu` is, that beds work as homes, how to check in, how the store works, how to send a `/tpn` request.

**Cleanup** removes dropped items from the ground at an interval, giving players a countdown warning and a sound so they can pick up their belongings first.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `tips.enabled` | boolean / `true` | Master switch |
| `tips.interval-seconds` | integer / `180` | Broadcast interval |
| `tips.prefix` | string | Prefix in front of each tip |
| `tips.messages` | string list | The messages; supports `{server}` |
| `cleanup.enabled` | boolean / `true` | Master switch |
| `cleanup.interval-seconds` | integer / `600` | Cleanup interval (minimum 61) |
| `cleanup.sound.*` | sound config | Warning sound |

### Example

```yaml
tips:
  enabled: true
  interval-seconds: 180
  prefix: '&#9FD9F6tips &f>> '
  messages:
    - '&fWelcome to &#9FD9F6{server}&f! Type &#9FD9F6/menu &fto open the server panel.'
    - '&fSleep in a bed or use &#9FD9F6/sethome &fto mark a spot, then &#9FD9F6/home &fto return.'
    - '&fType &#9FD9F6/checkin &fto claim today''s little gift and build up your coins.'
```

**What changed:** the default eleven messages were trimmed to three.

**Why written this way:** eleven tips means a player waits nearly an hour to see them all. If your onboarding is short, three is plenty.

**Restart needed:** no, `reload`.

**⚠ With `tips.enabled: true`, `messages` can't be an empty list** — the reload will be rejected.

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

**What changed:** `interval-seconds` from 600 to 180 (ten minutes to three).

**When to change it:** if dropped items pile up fast (many mob farms, many players), a shorter interval helps. But **cleaning too often interrupts players mid-chest-sorting** — ten minutes is a comfortable middle ground.

To clean immediately, use `/nekocore cleanup now` rather than changing the interval.

---

Tips defaults to one looping message every 180 seconds and includes a `/tasks` hint. With `daily-tasks.enabled: false`, `/tasks` hints are skipped without sending extra messages.

</details>

---

<a id="afk-pool--挂机池"></a>
<details>
<summary><b>AFK Pool</b><br><sub>The AFK pool: where it is, how often it pays, and how much</sub></summary>

### What it actually does

This is the module that makes most sense once you've experienced it in game, so behaviour first.

**The full flow:**

1. A player clicks the AFK pool entry in `/menu`.
2. They're teleported to the location in `afk-pool.teleport` (above the pool).
3. **Timing starts when they enter water in the configured world** — not on arrival, on entering the water.
4. Every `interval-seconds`, they receive a reward: a fixed amount of `base-exp` experience (times a multiplier), plus coins on a probability roll.
5. **Briefly stepping out doesn't reset the timer.** If they surface for air or return to the water within two seconds, the countdown continues.
6. **Staying out longer than `exit-grace-seconds` (default 2) resets it.**

**One easily missed condition: timing only accumulates in the `afk-pool.world` world.** A player soaking in water somewhere else gains nothing — deliberately, so nobody farms rewards in their own backyard pool.

**Why the grace period exists:** an AFK timer that's too strict makes players feel they're fighting the system — one accidental hop out of the water and they start over, which feels bad. Two seconds means normal breathing isn't punished, without letting someone loiter on the bank collecting rewards.

**The on-screen title** shows the current AFK state. `hide-while-inventory-open: true` hides it while the player has an inventory open (so they can see their items), **but the timer keeps running**.

**Two locks:** `enabled` and `position-configured` must both be `true`. The reason is coordinates — with only one lock, a quick-fingered admin could enable the feature before filling in the destination, and every player who clicked would be sent to placeholder coordinates. **Two locks means NekoCore never teleports anyone using a placeholder.**

> `position-configured` defaults to `false` in a freshly generated config, so you have to change it yourself. An older config with no such line is treated as `true`, so that the feature isn't silently switched off on an existing server that was already using it.

**How it relates to titles (easy to get wrong):** `reward.normal-multiplier` and a title's `afk-exp-multiplier` are **not multiplied together — the title value replaces the base**.

```text
Player owns no title   → reward.normal-multiplier is used (default 1.10)
Player owns any title  → that title's afk-exp-multiplier is used instead
```

It takes the **highest tier owned**, regardless of which title is displayed. The defaults are Yuki 1.10, Momo 1.10, and Neko 1.20 — so only someone who has bought Neko actually earns more than a titleless player.

This multiplier **applies to AFK pool experience only**, never to check-ins, tasks, or any other source.

**Fractional experience accumulates.** When a payout computes `base experience × multiplier`, the fractional part isn't discarded — it carries over to the next payout. So 10 × 1.10 = 11 comes out whole, but with `base-exp: 1` and a fractional multiplier you'd get exactly 11 over ten payouts rather than having each one flattened down.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `afk-pool.enabled` | boolean / `false` | Master switch (first lock) |
| `afk-pool.position-configured` | boolean / `false` | Destination confirmed (second lock) |
| `afk-pool.world` | world name / `world` | Target world |
| `afk-pool.teleport.x/y/z` | number | Destination coordinates |
| `afk-pool.teleport.yaw/pitch` | number | Facing on arrival |
| `afk-pool.exit-grace-seconds` | integer / `2` | Grace period out of water |
| `afk-pool.reward.interval-seconds` | integer / `60` | Reward interval |
| `afk-pool.end-message-enabled` | boolean / `true` | Personal duration message at normal session end |
| `afk-pool.reward.base-exp` | integer / `10` | Base experience per payout |
| `afk-pool.reward.normal-multiplier` | number / `1.10` | Base multiplier |
| `afk-pool.reward.coin-chance` | 0–1 / `0.45` | Probability of a coin payout |
| `afk-pool.reward.coin-min` / `coin-max` | integer / `1` / `4` | Coin range |
| `afk-pool.title.enabled` | boolean / `true` | On-screen title |
| `afk-pool.title.hide-while-inventory-open` | boolean / `true` | Hide it while an inventory is open |
| `afk-pool.title.stay-ticks` | ticks / `40` | Title duration |
| `afk-pool.title.fade-out-ticks` | ticks / `10` | Fade-out duration |

### Example

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

**What changed:** both locks set to `true`, and the coordinates replaced with real measurements.

**How to get the real coordinates:** build the pool in game, stand at the centre position above it, and read the exact coordinates from `F3`. `yaw` and `pitch` of `0.0` are usually fine.

**⚠ Never copy the example coordinates.** `120.5 / 65.0 / -30.5` is illustrative only. Copying it onto your server sends players somewhere unknowable — possibly the void, possibly someone's base.

**Restart needed:** no, `reload`.

**No entry in the menu?** Check that both locks are open. The menu's auto-layout hides disabled modules, so a missing button is the plugin telling you the feature isn't available.

### About the reward pacing

The default `interval-seconds: 60` means one reward attempt every minute: 10 base experience × 1.10 ≈ 11, plus coins 1–4 with a 45% chance.

**Doing the arithmetic:** an uninterrupted hour permits about 60 attempts: roughly 660 experience and 67.5 coins in expectation at these defaults. Coin rewards are random; interrupted sessions may earn less. Compare these rates with your own gameplay economy before enabling AFK.

That balance is deliberate. If AFK paid better than adventuring, players would AFK instead of play. Before changing it, work out the ratio between your AFK income and your normal gameplay income.

---

### Reward cadence is not session detection

60 seconds controls **reward attempts only**. Water eligibility is still checked every 5 ticks, exit grace still defaults to 2 seconds, and reward pools, probability and multipliers are unchanged. `afk-pool.reward.interval-seconds` must be an integer from 1 through 31536000; an invalid value logs a warning and safely falls back to 60.

Returning to water within grace continues the same session without an end message. A normal end (leaving, changing world, or death) sends the online player exactly one `messages.yml → afk-pool.end-message`. `{duration}` is formatted as `42秒`, `3分18秒`, or `1小时12分05秒`, without milliseconds. Duration starts at session entry and includes grace within that session. Quit, reload, disable and shutdown clean up silently. Set `end-message-enabled: false` to suppress this message. AFK remains disabled by default.

</details>

---

<a id="weekly-coin-leaderboard--金币周榜"></a>
<details>
<summary><b>weekly-coin-leaderboard</b><br><sub>Where the weekly coin board hangs, and how often it refreshes</sub></summary>

### What it actually does

Generates a floating text display at a given world coordinate showing the top 10 by **this week's coin income**.

**Note that this is "how much was earned this week", not "how much is held".** A wealthy veteran who earned nothing this week won't top the board. The board reflects who has been most active.

**Two locks, like the AFK pool**, with one important behavioural difference:

> With `position-configured: false`, **statistics keep accumulating** — only the display is skipped, and a single WARNING is logged.

So you **can safely leave `enabled: true` to start collecting data** and fill in coordinates later. That's unlike the AFK pool, where both locks closed means nothing happens at all.

**NekoCore only manages TextDisplays carrying its own marker.** It cleans up its own and never touches another plugin's floating entities.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `weekly-coin-leaderboard.enabled` | boolean / `false` | Master switch |
| `weekly-coin-leaderboard.position-configured` | boolean / `false` | Position confirmed |
| `weekly-coin-leaderboard.world` | world name / `world` | Target world |
| `weekly-coin-leaderboard.x/y/z` | number | Coordinates |
| `weekly-coin-leaderboard.yaw` | number / `0.0` | Facing |
| `weekly-coin-leaderboard.billboard` | enum / `FIXED` | `FIXED` respects yaw; `CENTER` follows the viewer |
| `weekly-coin-leaderboard.refresh-seconds` | integer / `30` | Refresh interval |

The board's **text** lives in `messages.yml` under `weekly-coins`, with separate colours for first, second, and third place.

### Example

```yaml
weekly-coin-leaderboard:
  enabled: true
  position-configured: true
  world: world
  x: 10.5
  y: 70.0
  z: -5.5
  yaw: 180.0
  billboard: FIXED
  refresh-seconds: 30
```

**What changed:** both locks opened, coordinates replaced with real measurements.

**Why written this way:** hanging the board in a lobby or near spawn means players see it in passing.

**Restart needed:** no, `reload`.

**Board not appearing?** Check in order: is the world loaded? Are both locks open? Is there open space at those coordinates (a display inside a solid block is invisible)? **And does this week actually have any coin records?** (An empty board shows "no coin records this week".)

**⚠ NekoCore never generates anything at `0,0,0` by default.** If you see something there, another plugin put it there.

---

The default `FIXED` billboard makes `yaw` determine the board's orientation. `CENTER` faces viewers instead, so it is not a fixed-yaw test. Refresh reuses the existing TextDisplay; reload removes the owned display before rebuilding it, without leaving duplicates. PDC cleanup, timezone, Top N and weekly income accounting remain intact; this is not a global FIXED change.

</details>

---

<a id="mascot--吉祥物"></a>
<details>
<summary><b>mascot</b><br><sub>Citizens NPC interaction: right-click replies, particles, hologram text</sub></summary>

### What it actually does

First, the clarification that matters most:

> **Mascot is a module built into NekoCore, not a third-party plugin.**
> **Citizens provides only the NPC entity itself.**

So the arrangement is: Citizens supplies the body, NekoCore supplies the personality.

**Full behaviour:**

1. An NPC created and placed by an admin in Citizens stands there.
2. When a player approaches, a few lines of hologram text appear above it (generated by NekoCore).
3. Right-clicking makes the NPC "speak" — a random line from the reply pool appears in chat, with particles.
4. **If the player clicks rapidly** (more than five times within a 15-second window by default), it switches to the "over-limit" reply pool — lines whose tone is "slow down a little" — with a different particle set and a two-second chat cooldown to prevent flooding.

**Why the over-limit behaviour exists:** any NPC that talks will get clicked repeatedly. Rather than letting it flood chat, it's turned into a reaction with a bit of personality — **players read it as the character's mood, not a system error**. That's why the tone difference between the two reply pools is worth writing carefully.

**Both pools live in `messages.yml`:** `mascot.replies` and `mascot.over-limit-replies`.

**⚠ NekoCore never creates, renames, moves, or re-skins an NPC.** Deleting one is also your job, in Citizens. The boundary is deliberate: your NPC is your asset in Citizens, and the plugin shouldn't make decisions about it.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `mascot.enabled` | boolean / `false` | Master switch |
| `mascot.npc-id` | integer / `-1` | The Citizens NPC's id |
| `mascot.hologram-enabled` | boolean / `true` | Hologram above the NPC |
| `mascot.hologram-lines` | string list | Hologram text; supports `{server}` |
| `mascot.hologram-y-offset` | number / `2.25` | Vertical offset of the hologram |
| `mascot.interaction-window-seconds` | integer / `15` | Length of the click-counting window |
| `mascot.normal-click-limit` | integer / `5` | Normal responses allowed within the window |
| `mascot.over-limit-chat-cooldown-seconds` | integer / `2` | Chat cooldown once over the limit |
| `mascot.normal-particle` | particle name | Particle for normal clicks |
| `mascot.normal-particle-count` | integer / `8` | Count for normal clicks |
| `mascot.over-limit-particle` | particle name | Particle once over the limit |
| `mascot.over-limit-particle-count` | integer / `18` | Count once over the limit |

### Example

```yaml
mascot:
  enabled: true
  npc-id: 3
  hologram-enabled: true
  hologram-lines:
    - '&#9FD9F6✦ {server} Mascot ✦'
    - '&#B2C3CF右键和我打招呼'
  hologram-y-offset: 2.25
  interaction-window-seconds: 15
  normal-click-limit: 5
  over-limit-chat-cooldown-seconds: 2
  normal-particle: minecraft:end_rod
  normal-particle-count: 8
  over-limit-particle: minecraft:smoke
  over-limit-particle-count: 18
```

**What changed:** `enabled` turned on, and `npc-id` set to the real id.

**How to get `npc-id`:** after installing Citizens and doing a full restart, create and place the NPC with Citizens' own commands, then stand next to it and run `/npc id`. That number is what goes here.

**Restart needed:** **yes, a full restart** — Citizens is a new plugin, and reload can't cover that.

**Adjusting `hologram-y-offset`:** the height of the whole hologram relative to the NPC's location, in blocks. Start with 2.25 and adjust after viewing your own NPC; do not change line spacing to fix overall placement.

**Interpreting the window:** `interaction-window-seconds` is a **rolling window, not a cooldown**. Someone clicking once every three seconds stays under the limit forever. It's only rapid clicking that trips it.

---

`hologram-y-offset` moves the entire text block (default reduced from 2.85 to 2.25); it does not change line spacing. It is relative to the NPC's location, not an extra offset above its head. The NPC's position, name and skin remain untouched. Mascot still uses `CENTER`; disabled Mascot or missing Citizens registers no Mascot listener or refresh task.

</details>

---

<a id="location-prefix--网络地区geoip"></a>
<details>
<summary><b>location-prefix — regions (GeoIP)</b><br><sub>Player region display (needs a GeoIP data file)</sub></summary>

### What it actually does

Resolves player IPs into coarse geographic regions, shown in TAB and the chat prefix.

**The privacy design is the important part:**

- **The full IP is never written to the database.** There's no column for it.
- **The full IP is never shown** in chat or TAB — only province or country granularity.
- **Name tags never show a region**, so there's nothing extra to turn off.
- **The session cache stores only resolved regions**, not IPs, and it disappears on shutdown.

**Player control:** there's a "show my region" toggle in `/menu`. Turning it off hides that player's region. This is a **second, independent layer** on top of the global `tab.show-location-prefix` — both must be on for a region to appear.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `location-prefix.enabled` | boolean / `false` | Master switch |
| `location-prefix.database-file` | string / `GeoLite2-City.mmdb` | Filename, relative to `plugins/NekoCore/` |
| `location-prefix.cache-session` | boolean / `true` | Session cache. **Setting it to `false` hides regions for everyone** |
| `location-prefix.show-china-province` | boolean / `true` | Province-level for China |
| `location-prefix.show-foreign-country` | boolean / `true` | Country-level elsewhere |
| `location-prefix.hide-unknown` | boolean / `true` | Reserved field with no effect in this version |

**⚠ Don't set `cache-session` to `false`.** It doesn't become "query live on every lookup" — it stops writing to the cache that downstream reads, and the result is that nobody shows a region. See [GeoIP](GEOIP.md).

### Example

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

**What changed:** the master switch turned on, filename confirmed.

**Restart needed: no, a full restart isn't necessary.** `/nekocore reload` re-opens the MMDB file and clears the session cache, so data you've just dropped into `plugins/NekoCore/` starts working after a reload. **But simply copying the file in, with no reload and no restart, does nothing** — that's the step people skip. The same goes for changing `database-file`: reload covers it.

**⚠ There is no MMDB file in the JAR.** You must obtain City data yourself from [MaxMind](https://dev.maxmind.com/geoip/geolite2-free-geolocation-data/) and place it in `plugins/NekoCore/`. And note: **the download arrives as a `.tar.gz` that you have to extract** — renaming the archive won't work.

**When it's missing or corrupt:** only GeoIP is disabled; everything else continues. By default it **won't** dump a stack trace — turn on `advanced.debug: true` temporarily if you want the details.

The full install procedure, privacy model, and troubleshooting steps are in [GeoIP](GEOIP.md).

</details>

---

<a id="holograms--全息字全局设置"></a>
<details>
<summary><b>holograms — global display settings</b><br><sub>Hologram text width and shadow</sub></summary>

### What it actually does

Controls the appearance of TextDisplays NekoCore generates. Currently used by the mascot hologram and the weekly coin leaderboard.

**Important boundary: NekoCore only manages TextDisplays carrying its own marker.** It cleans up its own and never touches another plugin's floating entities.

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `holograms.line-width` | integer / `240` | Maximum width of a text line |
| `holograms.shadowed` | boolean / `true` | Whether text has a drop shadow |

### Example

```yaml
holograms:
  line-width: 240
  shadowed: true
```

**When to change it:** if your hologram text wraps awkwardly or looks cramped, adjust `line-width`. `shadowed: false` gives text a flatter look that reads better against some backgrounds.

**Restart needed:** no, `reload`.

</details>

---

<a id="advanced--排障开关"></a>
<details>
<summary><b>advanced — the debugging switch</b><br><sub>The debugging switch for troubleshooting; leave it off normally</sub></summary>

### Fields

| Field | Type / default | Notes |
| --- | --- | --- |
| `advanced.debug` | boolean / `false` | Makes optional components log full exception traces |

### Example

```yaml
advanced:
  debug: true
```

**When to use it:** only while diagnosing a problem. Leave it `false` otherwise.

**Why it's off by default:** many "feature isn't working" cases are actually **normal, safe degradation** — no MMDB disables GeoIP, no Citizens disables Mascot. If all of those dumped stack traces, the log would be flooded and the real error would be invisible.

With `debug` on, those components log the full exception, which makes it clear whether the cause is a missing file, a format problem, or something else.

**⚠ Turn it back off when you're done.**

**Restart needed:** no, `reload`.

</details>

---

## Quick reference: what needs a restart

| Kind of change | How it takes effect |
| --- | --- |
| Names, numbers, switches, text | `/nekocore reload` |
| World names, coordinates, materials | `/nekocore reload` |
| `database.filename` | **Full restart** |
| Replacing the JAR | **Full restart** |
| Adding or replacing an MMDB file | `/nekocore reload` (it re-opens the file) |
| Installing a new third-party plugin | **Full restart** |
| Changes to a world plugin | **Full restart** |

**When unsure, run `/nekocore config check`.** It validates without applying, so one run tells you whether the configuration itself is sound.

**A failed reload is not a disaster.** The server keeps running on the previous valid configuration and players notice nothing. Read the first error — it contains the YAML path and the expected type — fix it, and try again.

---

## Related documentation

- **Goal-oriented changes** → [Recipes](RECIPES.md)
- **Hand-held first-server walkthrough** → [Quick Start](QUICKSTART.md)
- **How to tune the economy numbers** → [Economy](ECONOMY.md)
- **How the twenty daily tasks are played** → [Daily tasks](DAILY-TASKS.md)
- **GeoIP compliance and privacy** → [GeoIP](GEOIP.md)
- **A module isn't responding** → [Compatibility and troubleshooting](COMPATIBILITY.md)
