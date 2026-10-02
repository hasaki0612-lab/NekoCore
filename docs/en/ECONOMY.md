# Economy, Store, Bag, and Titles

[简体中文](../zh-CN/ECONOMY.md) | [Back to README](../../README.en.md)

This page covers the part of NekoCore where the numbers move: where coins come from, where they go, how the store prices things, and what Bag and titles are doing inside the economy.

It's more than a table of numbers. The defaults were chosen with an intention behind them — know the intention and you'll know which direction to turn the dial.

---

## Where coins and experience come from and go

Coins, EXP, and Level are three faces of **one player profile system**, not three separate things.

```text
        income                          spending
  ┌──────────────────┐         ┌──────────────────┐
  │ daily check-in   │         │ store purchases  │
  │ daily tasks      │  ────▶  │ daily enchants   │
  │ AFK pool (opt.)  │         │ title purchases  │
  │ admin commands   │         │                  │
  └──────────────────┘         └──────────────────┘
```

**Four sources of income:**

| Source | Default | Character |
| --- | --- | --- |
| Daily check-in | 100 coins + 50 EXP | Steady, no requirements, once a day |
| Daily tasks | 345 coins + 240 EXP for a full clear | You have to do something, with a difficulty gradient |
| AFK pool | A chance of 1–4 coins, EXP guaranteed | Off by default, needs configuring |
| Admin commands | — | `/nekocore coins add` and friends |

**Two places to spend:**

| Destination | Character |
| --- | --- |
| Store | Everyday consumables, with a very wide price range (3 coins for a torch up to 1800 for an ominous trial key) |
| Titles | The big saving goal (2500 / 7500 / 20000) |

**What levels are for:** levels don't cost you anything directly, but they unlock Bag slots (by default, level 10 opens slot 27 and level 25 opens slot 36). Experience is therefore real progress with a real payoff, not just a number going up.

---

## What do these defaults feel like to play?

This is the most worthwhile section on the page.

The default economy numbers **were not tuned for a large server**. The scenario they're aimed at is: **a friend server or small community server, ten people to a few dozen, where everyone knows each other, and the economy exists to give the world a rhythm rather than to create competition.**

### Let's do the arithmetic

Take a player who logs in, checks in, and finishes all nine tasks every day:

```text
check-in              100 coins
all tasks cleared     345 coins
                    ─────────
one day               445 coins
one week             3115 coins
```

Compare that against the title prices:

| Title | Price | A player with perfect attendance needs roughly |
| --- | --- | --- |
| Yuki | 2500 | about 6 days |
| Momo | 7500 | about 17 days |
| Neko | 20000 | about 45 days |

**What that curve is saying:** the first tier is reachable inside a week, which gives a new player a short-term goal they can actually see. The top tier takes over a month — it's meant as a symbol of a long stay, not something you grind out in seven days.

If you'd rather players reached titles faster, raising the check-in and task rewards does it. Think about the cost first, though: once income goes up, everything in the store starts to look cheap, and you'll probably want to move those prices at the same time.

### When the defaults stop fitting

They'll be a poor match in situations like these:

| Your situation | The direction to move |
| --- | --- |
| Your server has efficient mob farms or crop farms | Give the easily farmed goods a `buy-limit`, or switch `sell` off for them individually |
| High player count, frequent trading | Raise luxury prices and watch the trade logs |
| Players say they "can't make any money" | Raise the check-in or task rewards rather than cutting store prices |
| Players say they "have nothing to spend money on" | Add expensive goods, or raise the title prices |
| You want a more hardcore economy | Lower the check-in reward and let tasks carry the weight |

**There is no universally correct answer here.** On pricing, reading your own trade logs beats applying a formula. Give it a week or two after launch and you'll know far better than you do now which way to adjust.

### One caution

> Changing prices, rewards, or limits **does not rewrite trades that already happened**.

Something a player bought yesterday at the old price won't be retroactively charged the difference, and yesterday's check-in reward won't be topped up either. So the best time to change a number is **before the day rolls over** — or just accept that a batch of people claimed at the old rate today.

---

## Store: 109 products to start from

The Public edition ships 109 products as a **starting point you can play on immediately**. They're spread across eight categories:

| Category | Roughly what's in it | Price range (coins) |
| --- | --- | --- |
| Plants and seeds | Seeds, saplings, flowers, crops | 3 – 9 |
| Food | Cooked food, bread, golden apples | 4 – 180 |
| Weapons and armour | Iron gear, diamond gear, bows and shields | 5 – 4800 |
| Tools and odds and ends | Assorted tools, torches, name tags | 3 – 2400 |
| Redstone | Redstone components, pistons, hoppers | 6 – 340 |
| Building materials | Stone, wood, glass, concrete | 3 – 75 |
| Today's enchanted books | Enchanted books that rotate daily | 300 – 3600 |
| Adventure supplies | Ender pearls, blaze rods, trial keys | 45 – 1800 |

### Product IDs are stable

Each product's key in `config.yml` is a **stable product ID**:

```yaml
store:
  products:
    bread: {category: food, material: BREAD, name: 'Bread', price: 9, sell: true}
```

That `bread` is what shows up in daily quota records, trade logs, and crash-recovery data.

**To change the display name, change the `name` field — not the ID.** Rename `bread` to `mianbao` and NekoCore sees a brand-new product: the quota starts from zero, and the old records become orphans.

### The fields you can change

| Field | What it does |
| --- | --- |
| `category` | Which category it belongs to |
| `material` | The item the player actually receives |
| `name` | Display name |
| `price` | Price per unit |
| `sell` | Whether players may sell it back |
| `buy-limit` | Maximum bought per day. Omitted = the item's vanilla max stack size |
| `sell-limit` | Maximum sold per day. Omitted = `default-sell-limit` |

The material has to be a valid, item-capable enum name in your Paper version. A typo gets the reload rejected, with a suggestion for the closest matching names.

### How the limits are designed

Every product has an upper bound in each direction:

**`buy-limit`** — this is what stops "player converts unlimited coins into unlimited resources". Left out, it equals the item's vanilla max stack size, which is already a natural ceiling.

**`sell-limit`** — this is what stops "player converts a mob farm or crop farm into unlimited coins". Defaults to 200 (`default-sell-limit`).

**Why a sell limit matters at all:** it's the most commonly overlooked hole in a server economy. A well-built mob farm can produce over a thousand drops a day, and with no limit a player can turn all of them into coins. Inflation will outrun you before you notice.

For things that are especially easy to farm, a smaller `sell-limit` on that one product is usually more precise than lowering the global buyback ratio:

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

Limits are counted **per player**, not shared across the server. To inspect and reset them:

```text
/nekocore store status <player>
/nekocore store reset <player>
```

### The buyback ratio

```yaml
store:
  default-sell-limit: 200
  sell-price: {numerator: 2, denominator: 3}
```

`sell-price` is a fraction. What a player receives for selling = the buy price × `numerator / denominator`. The default is `2/3`, roughly 66.7% of the original price.

**The ratio trades two things against each other:**

- **Raise it** → players are more willing to convert spare output into coins, but the loss on "buy it, sell it straight back" gets smaller;
- **Lower it** → less inflation pressure, but players feel selling isn't worth the trip and may just throw things away.

**⚠ There is a hard ceiling: the sell price can never exceed the buy price.** A `numerator` larger than `denominator` is rejected outright (with the message "sell price cannot exceed buy price"), so `1/1` is as far as you can go. The entire point of that constraint is to close the arbitrage path where buying and reselling turns a profit — the store must not become a money printer.

For most friend servers, `2/3` sits in a comfortable spot. The real source of inflation usually isn't this ratio at all; it's **certain products being too easy to farm**. Handle that with `sell: false` or a small `sell-limit`.

### The daily enchanted books

Enchanted books are a rotating special product and deserve their own explanation.

**What's on offer each day:**

- **2 books at the vanilla maximum level**;
- **6 low-tier books** (levels I–II, and strictly below the vanilla maximum level).

**When it rotates:** automatically at **04:00 Beijing time**. The batch and its purchase state live in the database, so reloading the config does **not** reset the day's batch.

**How they're priced:**

```yaml
store:
  enchantments:
    maximum-price: 1800      # maximum-level tier
    normal-price: 300        # low-tier
    prices: {mending: 3600, silk_touch: 2400, infinity: 2400, swift_sneak: 2700, wind_burst: 3600}
```

What you write in `prices` is a **per-book override price** (not multiplied by level). Books not listed there use the two tier prices above.

**Curses never enter the pool.** Leave `excluded: [binding_curse, vanishing_curse]` alone.

**Enchanted books are not bought back.** The store shows "not bought back" for them, so nobody can buy a book and sell it straight back for profit.

To roll a fresh batch right now:

```text
/nekocore store refresh-enchants
```

Normal operation doesn't need this command — it's mainly for testing, or for when you've just changed enchantment prices and want to see the effect immediately.

---

## Safe trading and recovery

Store and Bag operations run through database transactions with recorded recovery stages.

There's a **real constraint you have to understand** here:

> A player's inventory and the database **cannot be made into a single atomic transaction**.

This isn't a shortcoming of NekoCore's implementation; it's a limitation of how Minecraft servers are built. The player's inventory lives in server memory and world playerdata, the database lives in another world entirely. A crash at any point in between — the server being killed, a full disk, a power cut — can produce "item delivered but the database didn't record it", or the reverse.

NekoCore's approach is to split a trade into named stages (`BEFORE` → `UNCERTAIN` → `APPLIED`), leaving a record at every step. On startup, or when a player logs in, it checks for unfinished trades and then **reads those records to decide which way to finish**:

- If it finds evidence that the **item really was delivered** → the trade is marked complete;
- If the evidence says it wasn't delivered → a full rollback (coins returned, quota restored);
- **If the evidence isn't enough to decide** → **it goes to manual review**, with no guessing at all.

That third case deserves its own explanation.

### What "manual review" (quarantine) means

When a trade's state can't be determined — say the player's inventory matches neither "before delivery" nor "after delivery", which usually means **another plugin** touched it in between — NekoCore **refuses to guess**:

- A line goes to the log: "item record needs manual review, **item was not resent or overwritten**";
- That player sees a notice: "one item record needs review, trading and Bag access are unavailable for now. Please contact an administrator; the item record has been kept."
- **The item data is preserved exactly as it is. It will not be resent, overwritten, or refunded.**

**The key point: this player is not banned and can keep playing normally.** All they lose, until the problem is resolved, is the ability to start any NekoCore trade or open the store and Bag. That's deliberate — when something goes wrong, it's better for one feature to be temporarily unavailable than to replay an operation whose outcome is unknown.

The source comments state the principle plainly: an ambiguous storage failure involving a third-party plugin is **quarantined, never replayed** — no guessing, no refunds, no re-sent items, and no overwriting Multiverse's per-world inventories.

**What to do if it happens:** this is a situation that needs human judgement. Preserve the scene, compare the player's inventory, coin balance, store quota, and the trade logs, then decide how to handle it. **Do not** try to "make it go away" by dropping tables or editing data.

### What happens on shutdown

When the server runs a normal `stop`, NekoCore tries to finish the trades in flight:

| Stage | Handling on shutdown |
| --- | --- |
| Delivery confirmed | Marked complete |
| Definitely not delivered | Rolled back |
| **Uncertain** | **Nothing is done; it's left for the next login to recover** |

The third case is intentional — since there's no way to confirm whether the player inventory's `saveData()` actually succeeded, a hard guess could just as easily duplicate or destroy items. If you see "trade shutdown review incomplete, recovery record retained" in the shutdown log, that's normal protective behaviour, not a fault.

### Why these rules exist

| Rule | Reason |
| --- | --- |
| Don't bypass the GUI and edit tables directly | Those tables are exactly what the recovery logic reads |
| Don't hot-unload the plugin | In-flight transactions lose their chance to finish |
| Don't swap the database while running | Guaranteed corruption |
| Backups must cover the database **and** player inventories | A database rollback and an item rollback have to be the same point in time |
| Let the server `stop` normally before a restart | Gives the recovery logic a clean chance to finish |

### When you find unfinished operations

If startup reports operations waiting to be recovered:

1. **Keep the server offline.**
2. **Take a full copy** (the whole `plugins/NekoCore/` directory plus the relevant player inventory data).
3. **Let the recovery logic run.** Start the server and it will try to finish them.
4. **Verify afterwards**: player inventories, coin balances, store quotas, trade logs.

**What not to do:** swapping JARs repeatedly to see what sticks, deleting the trade tables, hand-"fixing" pending records. Any of those turns a recoverable state into an unrecoverable one.

The full backup and recovery procedure is in the [database documentation](DATABASE.md).

---

## Bag

Bag is an **item container stored in the database that follows the player across worlds**.

### Two settings

```yaml
bag:
  enabled: true
  writable-worlds: [world]
  unlock-levels:
    '27': 10
    '36': 25
  readonly-notice-seconds: 4
```

**`enabled`** controls whether the entrance exists. Switched off, `/bag` reports that the feature is disabled, but **players' items are not deleted** — turn it back on and everything is still there.

**`writable-worlds` is a whitelist.** Worlds on the list allow taking items out and putting them in; **every other world is read-only**, including your lobby, and including any world you add later.

That "read-only by default" semantic matters: **a new world is automatically safe**, and you don't have to remember to come back and edit the config.

**`unlock-levels`** controls slot capacity. The default mapping is:

| Level | Bag capacity |
| --- | --- |
| Level 1 – 9 | 18 slots |
| Level 10 – 24 | 27 slots |
| Level 25 and above | 36 slots |

Those three capacities are **hardcoded at 18 / 27 / 36**, and `unlock-levels` supplies only the levels at which the next one arrives — `'27': 10` means "27 slots from level 10", not "27 slots, configured as 10". The values inside `unlock-levels` do **not** drive capacity.

Unlocking slots by level is a gentle growth incentive — players will level up to get a few more slots of space.

**`readonly-notice-seconds`** is how long the read-only-world notice stays on screen.

### When you actually want Bag

Bag solves this problem: **the player has run out of carrying space and doesn't want to walk home just to put things down.**

What separates it from an ender chest is that Bag is **shared across worlds** (within the whitelisted ones), its capacity grows with level, and it doesn't consume inventory space.

### Coexisting with Multiverse-Inventories

If you use Multiverse-Inventories to give different worlds separate inventories, then a Bag that's writable everywhere lets players stuff valuables into it and carry them across a world boundary, **going around your rules**.

The `writable-worlds` whitelist exists for exactly that scenario: make only the survival world writable and leave the rest read-only.

**If you run both, test these on a staging server:**

- whether Bag behaves as expected when switching between worlds;
- whether Bag contents are affected when a player dies;
- whether Bag is still there after a player disconnects and reconnects;
- what happens when someone puts items into Bag with a full inventory.

Those combinations are **not covered by the automated test guarantees**.

---

## Titles

Yuki, Momo, and Neko are the three general-purpose default title presets.

| Display name | Stable ID | Price | Tier | Perks |
| --- | --- | --- | --- | --- |
| Yuki | `mame` | 2500 | 1 | Auto check-in, 120-second teleport cooldown, AFK EXP ×1.10 |
| Momo | `momo` | 7500 | 2 | Auto check-in, safe chat colours, 60-second teleport cooldown, AFK EXP ×1.10 |
| Neko | `sora` | 20000 | 3 | Auto check-in, safe chat colours, 30-second teleport cooldown, AFK EXP ×1.20 |

### Three design rules

**1. Perks follow the highest tier owned.**

A player who bought Neko and then switches the displayed title to Yuki still gets Neko's 30-second teleport cooldown. **Switching around never costs them anything**, which is what makes titles a genuine cosmetic choice instead of a stats trade-off.

**2. The equipped title only decides the appearance.**

Nobody loses a perk they paid for by changing which title they display.

**3. The title shop is opened by administrators only.**

`/nekocore levelshop open <player>`, with no command for players to open it themselves. That's deliberate — the title shop is positioned as something offered at a specific place, typically wired to an NPC or a spot in your lobby.

`levelshop.worlds` limits which worlds it may be opened in, defaulting to `world` alone.

### What you can and can't change

**You can change:** display name, prefix, icon, slot, tier, price, perk switches and their values, and the description text.

**You can't change:** the `mame`, `momo`, and `sora` keys. They're stable title IDs, and players' purchase records hang off them — the same reasoning as product IDs.

The `rank` field (1/2/3) decides who counts as the "highest tier". Changing it redefines the whole perk ladder, so think it through before you do.

### Perk permission nodes

```yaml
levelshop:
  permissions:
    auto-checkin: nekocore.perk.auto-checkin
    colored-chat: nekocore.perk.colored-chat
    fast-tpn: nekocore.perk.fast-tpn
```

All three nodes default to `false`. If you'd rather grant perks through a permissions plugin (say, handing VIPs auto check-in directly), just rename these to match your permission naming convention.

One mechanical detail worth knowing: a title's `afk-exp-multiplier` **replaces** the AFK pool's `normal-multiplier` rather than multiplying with it, and the value used is always the one on the highest tier the player owns — not the one on the title they happen to be displaying.

---

## Weekly coin leaderboard

```yaml
weekly-coin-leaderboard:
  enabled: false
  position-configured: false
```

The board tracks **this week's coin income**, not the current balance.

**The week starts on Monday.** A week's boundary is **Monday 00:00 Beijing time** (that timezone is fixed and doesn't follow any configuration setting). So coins earned at 23:59 on Sunday count toward the previous week, and anything after 00:00 on Monday counts toward the new one.

**What counts:** check-in, daily tasks, the AFK pool, and store sales.

**What doesn't:** coins an administrator added by hand (`/nekocore coins add`), spending on store purchases, and any change related to transaction recovery or rollback.

So the board measures "**who earned the most this week through normal play**" — administrator gifts can't buy the top spot, and a wealthy veteran who earned nothing this week won't camp on it by default.

**Two locks:** both `enabled` and `position-configured` must be `true` before anything is rendered.

**But tracking is independent of display:** with `position-configured: false` the tracking **continues as normal** — it simply doesn't spawn the TextDisplay, and logs one WARNING. So you can safely turn on `enabled: true` first to collect data, and fill in coordinates later once you've decided where it hangs.

**Ties:** equal income is ordered by ascending UUID. The names shown on the board are **current** names, so a player who renames will change on the board too.

The sorting and display logic keeps the week-income structure from schema 5.

---

## Related documentation

- **The full product list and every price** → look at `store.products` in `src/main/resources/config.yml`
- **How to change store prices and add limits** → [Recipes](RECIPES.md#i-want-to-change-one-products-price)
- **What the store feels like in game** → [Quick Start](QUICKSTART.md)
- **Database backup and recovery** → [Database](DATABASE.md)
- **Every configuration field explained** → [Configuration](CONFIGURATION.md)
