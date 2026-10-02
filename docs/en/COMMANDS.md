# Commands and Permissions

[简体中文](../zh-CN/COMMANDS.md) | [Back to README](../../README.en.md)

This page has three parts: the commands your players will actually use, the admin commands (with the situations they're for and worked examples), and the complete permission node tables.

First, a shortcut that means nobody has to memorise anything: **most player commands are reachable from `/menu`.** The panel contains entries for check-in, tasks, the store, Bag, the profile card, and travelling between worlds. You don't need to make your players learn command names — this page is mostly for you.

---

## Player commands

### The daily loop

These six commands cover the bulk of what a player does each day.

| Command | What it does |
| --- | --- |
| `/menu` | Opens the server panel |
| `/tasks` | Opens the existing Daily Tasks GUI directly; unavailable when tasks are disabled |
| `/checkin` | Claims today's check-in reward |
| `/coins` | Shows your own coin balance |
| `/sethome` | Records your current position as a home in the current world |
| `/home [worldName]` | Returns you to a home |

`/menu` is the front door. From the panel a player can see their profile card (level, experience, coins, playtime, first join date), the day's nine tasks, their check-in status, and a "show my region" privacy toggle.

`/coins` looks redundant next to the panel, but it earns its place: after spending money in the store, players want to see what's left, and typing two words is faster than opening a GUI.

**About homes:** `/sethome` records homes **per world**. A player can set one in the main world, one in the Nether, and one in the End, and the three never overwrite each other. `/home` with no argument returns to the current world's home; passing a world name lets them travel back across worlds, as long as that world is loaded.

```text
/home
/home world
/home world_nether
```

**When it fails:** a misspelled world name, the wrong capitalisation, or a world that isn't loaded all produce a "can't find that world right now" message. If a player switches worlds while a teleport is in progress, the teleport is cancelled — that's what stops them from being dropped at a position that no longer means anything.

### Confirming where a home actually is

```text
/check home <worldName>
```

For example:

```text
/check home world
```

Output looks something like:

```text
Home in world · 128, 65, -340 (facing 90.0 / 0.0)
```

**This command is more useful than it looks.** "I set a home, why can't I get back?" is a common complaint, and the usual answer is that it was set in the wrong world, or that the coordinates aren't safe any more. `/check` lets players verify that themselves instead of opening a ticket with you.

### Store and portable storage

| Command | What it does |
| --- | --- |
| `/store` | Opens the daily store |
| `/bag` | Opens your portable Bag |

Store interaction is **right-click to buy, left-click to sell**. Quantities can be picked from presets or typed into chat. That typed number **is not broadcast to other players** — it only travels inside the plugin.

**Only plain left- and right-clicks count.** Shift-clicking, middle-clicking, and number-key swapping do nothing here. That's deliberate: these transactions really do move money and items, and nobody wants a misclick to trigger one.

Bag is an item container that follows the player between worlds. By default it can only be written to in the main world; every other world is read-only.

### Meeting up with friends

| Command | What it does |
| --- | --- |
| `/tpn <player>` | Requests a teleport to an online player |
| `/yes` | Accepts a teleport request you received |
| `/no` | Politely declines a teleport request you received |

```text
/tpn Yuki
```

The other player receives a request and has 60 seconds to answer with `/yes`. Once accepted, the requester is sent to "near where the other player was when they accepted" rather than a position that tracks them live — a small detail that avoids the strange landing spots you get when someone accepts mid-sprint.

**A few design choices worth knowing:**

- Teleporting needs **consent from both sides**. It isn't "click and you're there" — the person being visited gets a say.
- The requester is on a cooldown, 180 seconds by default. Title perks can shorten it.
- If either side goes offline, the request is cancelled automatically.
- When the plugin reloads its configuration, in-flight requests are cancelled rather than being processed under a half-old, half-new rule set.

---

## Admin commands

### Admin name lookup

`/nekocore lookup` answers a question you will run into the moment you edit a task target or add a store product: what is this item actually called? A `material:` the server does not recognise is the usual reason a product or a task quietly refuses to work, and this is the fastest way to find the right name. It uses the existing `nekocore.admin` permission, writes no files, and changes no data.

| Command | What you get |
| --- | --- |
| `/nekocore lookup` | Hold an item in your main hand: its true English name, plus the `material: NAME` line to paste in. With an empty hand it reminds you to hold something. |
| `/nekocore lookup bread` | A search over English Material names — exact matches first, then prefix, substring and close spelling — with at most 10 results |
| `/nekocore lookup entity phanton` | The same search over entity types, which offers the close match `PHANTOM` |

Spelling does not have to be perfect, and that is the point: `bred` finds `BREAD`, `phanton` finds `PHANTOM`. Capitalisation is ignored, and `minecraft:bread` works just as well as `bread`.

Chinese keywords are **not** translated. The command says so rather than guessing at a translation, so searching 面包 gets you that message and nothing else. From the console you have to pass an English keyword, since there is no held item to read.

Entity output is a list of candidates to choose from. When you are repairing a task list, swap out only the entry that is actually broken and leave the correct ones alone.

Every admin command lives under `/nekocore` and requires the `nekocore.admin` permission, which defaults to OP only.

If you can't remember the subcommands, just type `/nekocore` — it sends you the usage list.

### Configuration

#### `/nekocore config check`

**Validates the configuration files on disk without applying anything.**

This is the command to run **first** after editing YAML. It parses and validates all of the configuration in full, reports any problems, and hands the result back to you without changing a thing.

**When to use it:**

- You've just edited `config.yml` and want to know whether it's written correctly, but haven't decided to make it live yet;
- you changed a value on a production server that you're not entirely sure about, and want to confirm the syntax and the accepted range first;
- you're troubleshooting and want to rule out "the configuration itself is invalid".

The output is either "configuration check passed; nothing was applied" or an error carrying a YAML path.

#### `/nekocore reload`

**Parse → validate → build the complete new configuration → swap it in as a whole once it succeeds.**

It uses exactly the same validation logic as `config check`. The single difference is that when it passes, the change actually takes effect.

**The distinction is worth remembering:**

| | What it does | When to use it |
| --- | --- | --- |
| `config check` | Validates only, changes nothing | You've just edited YAML and want to confirm it's correct |
| `reload` | Swaps the whole set in after validation passes | You're satisfied, and want it live |

**`reload` is atomic.** The full set is replaced only after every file passes. If any single item fails, the server keeps running on the last valid configuration and tells you which path was wrong and what type it expected. You never end up half-new and half-old.

**What a reload can't cover:** replacing the JAR, changing the database filename, installing new plugins, and world-plugin changes. Those need a full restart.

**What happens after a successful reload:** timer-driven tasks (the Tips rotation, the cleanup countdown, and so on) start their clocks over. In-progress teleport requests are cancelled so nothing is handled halfway under the old rules.

### Diagnostics

#### `/nekocore status`

Shows the NekoCore, Paper, and Java versions, the database schema, and the state of each module:

- Store, Bag, Tasks, GeoIP, Mascot, AFK Pool, Weekly Leaderboard
- whether PlaceholderAPI is available

**This one command resolves most cases of "feature X isn't working".** When something misbehaves, look here before you start digging through logs.

#### `/nekocore doctor`

A synonym for `status`. Both print identical output; use whichever you reach for. (`doctor` simply suits the mood of "something's wrong, take a look at me" a little better.)

### Economy administration

```text
/nekocore coins add <player|UUID> <amount>
/nekocore coins set <player|UUID> <amount>
/nekocore coins take <player|UUID> <amount>
/nekocore exp add <player|UUID> <amount>
/nekocore exp set <player|UUID> <amount>
```

An example — topping someone up with an event reward:

```text
/nekocore coins add Yuki 5000
```

Both player names and UUIDs are accepted. **If a name is ambiguous** (say two players shared a name at different times), the plugin tells you to use a UUID instead. An unknown player gets "there's no profile for that player yet — have them join the server first".

These are all logged operations, and adjusting a balance notifies both you and the player concerned.

### Store administration

```text
/nekocore store refresh-enchants
/nekocore store status <player|UUID>
/nekocore store reset <player|UUID>
```

**`refresh-enchants`** — swaps in a new batch of daily enchantment books immediately and resets the purchase counter for the new batch.

Normally a batch rotates automatically at 04:00 Beijing time and needs no manual help. The point of this command is testing, or having just changed enchantment prices and wanting to see the new batch right away.

**`status`** — shows how much of today's store quota a given player has used.

This is the tool for "a player says they can't buy anything": one look tells you whether the quota really is exhausted or whether something else is going on.

```text
/nekocore store status Yuki
```

**`reset`** — clears a player's buy and sell limits.

**Use this one with care.** It bypasses the daily limits you set, and those limits are your main defence against inflation. Legitimate uses are a bug that consumed a player's quota incorrectly, or a live event where you want certain people unrestricted.

### Daily task administration

```text
/nekocore tasks status
/nekocore tasks reroll confirm
/nekocore tasks reset <player>
/nekocore tasks progress <player> <taskId> <amount>
```

**`status`** — lists the nine tasks drawn for today, with their difficulty, task ID, and name.

Reach for it when you're asking "why are there no hard tasks today" or want to confirm whether a particular task was drawn at all.

**`reroll confirm`** — redraws today's whole set of tasks.

Mind that `confirm`: it's a deliberate second step, because a redraw has a cost. Progress on the old rotation is archived rather than carried over, and the new tasks start from 0. Claim records are untouched, but work in progress is voided.

A good fit for the situation where a task drawn today turns out to be impossible because of a configuration mistake and you want to give the whole server a fresh set.

**`reset <player>`** — clears one player's progress within the current task rotation.

**`progress <player> <taskId> <amount>`** — manually advances one player's progress on one task.

```text
/nekocore tasks progress Yuki hard_iron_golem 1
```

This serves two situations: a player hit a genuine counting bug and you're making it up to them, or you're testing whether a newly configured task counts correctly.

If the task ID you pass isn't part of today's rotation, you get "that task ID isn't in today's rotation — try `status` first". Run `status` to confirm the ID.

### Everything else

```text
/nekocore cleanup now
/nekocore levelshop open <player>
```

**`cleanup now`** — runs a ground-item cleanup immediately instead of waiting for the next scheduled cycle. Players still receive the usual countdown warning beforehand.

**`levelshop open <player>`** — opens the title shop for the specified player.

**Note that the title shop has exactly one entrance.** Players have no command of their own to open it; an admin or the console has to start it. That's intentional — the title shop is positioned as something offered *at a particular place*, usually alongside an NPC or a spot in your lobby.

`levelshop.worlds` limits which worlds it may be opened in, and defaults to only `world`. A player standing somewhere else is told to go back and find the title NPC.

---

## Permission nodes

### Player permissions

All of these default to `true`, which means ordinary players have them.

| Permission | Command | Default |
| --- | --- | --- |
| `nekocore.menu` | `/menu` | `true` |
| `nekocore.tasks` | `/tasks` | `true` |
| `nekocore.coins` | `/coins` | `true` |
| `nekocore.checkin` | `/checkin` | `true` |
| `nekocore.home.set` | `/sethome` | `true` |
| `nekocore.home.use` | `/home` | `true` |
| `nekocore.home.check` | `/check home` | `true` |
| `nekocore.store` | `/store` | `true` |
| `nekocore.bag` | `/bag` | `true` |
| `nekocore.tpn` | `/tpn`, `/yes`, `/no` | `true` |
| `nekocore.levelshop` | Using the title shop | `true` |

**These groups are separate**, which is what makes fine-grained restrictions possible. Some combinations are pointless and some are genuinely useful: giving `nekocore.home.set: true` while `nekocore.home.use: false` achieves nothing, whereas `nekocore.tpn: false` disables teleport requests alone and leaves everything else working — a perfectly reasonable thing to want.

### Admin permissions

| Permission | Description | Default |
| --- | --- | --- |
| `nekocore.admin` | Every `/nekocore` subcommand | `op` |

**This is the only OP-level permission node.** Take it away and a player has no access to admin commands whatsoever.

### Title perk permissions

These three default to `false` and are granted by buying the matching title. A permissions plugin can also grant them directly — that's the hook to use if you want a "VIPs get the perks immediately" arrangement.

| Permission | Effect | Default |
| --- | --- | --- |
| `nekocore.perk.auto-checkin` | Automatic check-in on join | `false` |
| `nekocore.perk.colored-chat` | Safe chat colours | `false` |
| `nekocore.perk.fast-tpn` | Shorter teleport cooldown | `false` |

You can rename all three nodes under `levelshop.permissions` in `config.yml` — useful if your server already has a permission naming convention you'd rather align with.

**Perks follow the highest tier a player has ever owned.** Having bought Neko and then switched the displayed title to Yuki, a player's teleport cooldown is still Neko's 30 seconds. By the same logic, granting `nekocore.perk.fast-tpn` by hand to someone who owns no title gives them exactly the effect that perk describes.

---

## A few words about admin permissions

`nekocore.admin` is a **broad permission**. Anyone holding it can:

- add coins and levels to themselves or to others directly;
- reset store quotas;
- reroll tasks and push task progress forward;
- reload the configuration.

Some of those actions can't be undone (coins you've handed out don't come back on their own), and some change the state of the whole server (a reroll affects everyone).

So:

- **Don't hand this permission to players you don't trust.** If someone needs a slice of admin capability, give them a narrower permission through another plugin instead of `nekocore.admin`.
- **The console already has full admin capability**, so sensitive maintenance is better done straight from the console than by giving yourself OP and doing it in game.
- **Commands that involve a player GUI or a teleport need to run in game.** `levelshop open` is a good example: the console can execute it, and it opens the *player's* interface, which works fine. Commands like `/menu` and `/sethome`, on the other hand, need a player identity behind them — from the console you'll just get "please use this feature in game".

For more on permission safety, see the [security policy](../../SECURITY.md).

---

## Where to next

- **Want to know how a command actually feels in game** → [Quick Start](QUICKSTART.md)
- **Want to change configuration by goal** → [Recipes](RECIPES.md)
- **Want the complete behaviour of one module** → [Configuration](CONFIGURATION.md)
- **A command isn't responding** → [Compatibility and troubleshooting](COMPATIBILITY.md)
