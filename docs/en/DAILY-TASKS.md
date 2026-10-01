# Daily Tasks

[简体中文](../zh-CN/DAILY-TASKS.md) | [Back to README](../../README.en.md)

Daily tasks are the coziest module in NekoCore. Each day they hand you nine small things to do. Nothing pushes you, but you log in with a direction.

The page has three parts: how the whole thing plays, how each of the 20 tasks is completed, and the configuration and troubleshooting a server owner cares about.

---

## How it works

### One rotation a day, shared by the server

Each day at **00:00** (in the timezone set by `daily-tasks.timezone`, which defaults to `Asia/Shanghai`), NekoCore draws a few tasks from each of its three pools to make up that day's rotation.

**Every player on the server shares the same nine tasks.** That's what makes people talk about the same thing in chat — "did you do today's Deadeye yet?" — instead of each comparing a different list.

**Progress and rewards, though, are per player.** Your completion doesn't complete mine, and my claim doesn't take anything from you. The design gives the tasks a shared conversational topic without turning them into a competition.

### Three difficulties

| Difficulty | Drawn by default | Default reward (each) | Personality |
| --- | --- | --- | --- |
| Easy | 3 | 20 coins + 15 EXP | Do them in passing, on the way in or out |
| Normal | 3 | 35 coins + 25 EXP | Needs a bit of dedicated time |
| Hard | 3 | 60 coins + 40 EXP | Needs preparation, possibly a trip somewhere |

A full clear is **345 coins + 240 EXP**.

**On how big that number is:** check-in pays 100 coins a day. So tasks are worth roughly three and a half check-ins — worth doing, but not a replacement for the main economy. You don't want players logging in for ten minutes to clear tasks and then logging straight off.

### How progress is kept

Tasks are **per day**. Today's progress and claim state end when the rotation turns over tomorrow.

A task you didn't finish disappears the next day (unless it's drawn again). That isn't a punishment mechanic; it just means every day starts from zero, which is friendlier to new players.

### Reading your tasks in game

```text
/menu
```

Click the "Daily Tasks" entry. The GUI lays them out in three rows by difficulty, three per row:

- each task shows its name, progress (say `12 / 16`), rewards, and state;
- finished ones get a ✓;
- the middle cell shows today's date and when the next refresh happens.

**When you complete one**, chat gets a random congratulation line (six of them in rotation), with the coins and experience you actually received.

---

## Easy tasks (7 in the pool, 3 drawn)

### Little Gardener · `simple_gardener`

> Collect 16 leaves, grass, ferns, or flowers using shears or a Silk Touch tool

**How to do it:** take shears into a forest and clip leaves, or pull grass and pick flowers by hand. The target is 16.

**Why it's built this way:** it's one of the most painless tasks in the pool. Anyone heading out will walk past this stuff anyway. It also teaches a new player a small piece of knowledge — that shears can cut leaves.

**Blocks that count:** every kind of leaves, short grass, tall grass, ferns, large ferns, and a dozen-odd flowers (dandelion, poppy, cornflower, lily of the valley, tulip, sunflower, lilac, rose bush, peony, and so on).

### Stay Healthy · `simple_healthy`

> Successfully eat 1 apple

**How to do it:** find an apple and eat it.

**Note: only a plain apple counts.** Golden apples and enchanted golden apples are different items and **do not count** toward this task.

**Why an apple:** apples are a common drop from oak leaves, and nearly every new player will have one. The barrier here is close to zero — the task exists to let a player experience the positive feedback of "task complete".

### Lighten the Load · `simple_shear_sheep`

> Shear 1 sheep with your own hands

**How to do it:** hold shears and right-click a sheep.

**Mind the "with your own hands":** shearing sheep automatically with a dispenser doesn't count.

### Good Morning, {server}! · `simple_good_morning`

> Be online for a total of 20 minutes today

**How to do it:** spend 20 minutes online. **It can be split across several sessions, and idling still counts.**

**Why cumulative instead of consecutive:** a consecutive requirement punishes players who can only fit in a short session. A cumulative one is friendly to every schedule.

**This task's name carries your server's name.** Change `branding.server-name` and it follows along.

### Homework · `simple_crafting`

> Craft items of 5 different Materials at a crafting table

**How to do it:** make five **different kinds** of item at a crafting table. Note "different Materials" — making five sticks counts as one.

**The 2×2 inventory grid doesn't count.** You have to use a crafting table (the 3×3 interface).

**Why the inventory grid is excluded:** 2×2 is too easy. A player could knock this out by making a few planks.

### Eat Well Today · `simple_eat_food`

> Successfully eat 5 of any food, cumulatively

**How to do it:** trigger the act of eating five times.

**The implementation is quite direct:** it listens to the consumption event itself and **does not check hunger**. Anything that successfully triggers the eating action in game counts once. Vanilla already blocks the "spam food while full" case — right-clicking food at full saturation never fires the consumption event — so this task needs no extra gate of its own.

It can be finished in the same action as Stay Healthy: eating one apple advances both tasks.

### Pastoral Idyll · `simple_pastoral`

> Successfully plant seeds 8 times with your own hands

**How to do it:** plant eight times. Seeds, carrots, and potatoes all work.

**What's judged is the "place" action**, so right-clicking farmland with seeds in hand will count.

**Can I plant, dig it up, and plant again?** Technically yes, but there's no need — eight plantings go by quickly on a normal farm. The task just wants players to touch a hoe and some seeds.

---

## Normal tasks (8 in the pool, 3 drawn)

### Bumper Harvest! · `normal_harvest`

> Harvest 24 mature crops

**How to do it:** collect 24 mature wheat, carrots, potatoes, beetroots, nether wart, or cocoa beans.

**Immature crops don't count.** So you can't farm progress by breaking crops that haven't grown yet.

**Block list:** `WHEAT`, `CARROTS`, `POTATOES`, `BEETROOTS`, `NETHER_WART`, `COCOA`.

### Dessert Master · `normal_dessert`

> Craft 1 cake, 1 pumpkin pie, or 9 cookies in total

**How to do it:** pick any one of three routes — bake a cake, bake a pumpkin pie, or accumulate 9 cookies.

**How it's actually counted internally:** the target in the config reads 9, but **a cake or a pumpkin pie fills it in a single craft**. So what the line really means is "make one cake (or one pumpkin pie), or make 9 cookies the honest way".

**Why it's designed like that:** cake and pumpkin pie cost noticeably more in materials than cookies. Counting them at "1 item = 1 point" would mean baking nine cakes, which is absurd. The rule is there to make the three routes roughly comparable in real effort.

### Tax Collector · `normal_tax_collector`

> Complete 1 valid trade with a villager

**How to do it:** swap something with a villager once.

**What "valid" means:** the trade has to actually go through. Opening the trade screen and looking at it doesn't count.

### Daily Cleanup · `normal_cleanup`

> Defeat 8 hostile mobs with your own hands

**How to do it:** kill eight hostile mobs — zombies, skeletons, creepers, spiders and their like all count.

**Note the "hostile":** killing cows and pigs doesn't count. That's also what makes this take longer than it looks.

### Deep Dweller · `normal_deepslate_worker`

> Mine 12 deepslate ores with your own hands

**How to do it:** head down to the deep layers, at very low Y coordinates, and go mining.

**Why deepslate ore specifically:** it's proof that you really did go deep. Ores in ordinary stone don't count.

**Blocks that count:** deepslate coal ore, iron ore, copper ore, gold ore, redstone ore, lapis lazuli ore, diamond ore, and emerald ore.

**Worth mentioning in passing:** the 12 deepslate ores are worth something on their own, so this task pays better in practice than it looks on paper.

### Worth the Wait… · `normal_smelt`

> Take 16 smelted items out of a furnace, smoker, or blast furnace in total

**How to do it:** smelt things and take out 16 finished items. All three furnace types count.

**Why it's called "Worth the Wait":** it's the one task that is fundamentally about waiting while you do something else. Load up the ore, go do other things, come back and collect — which pairs beautifully with the other tasks on the list.

### The Blacksmith's Forge · `normal_blacksmith`

> Craft 3 iron tools

**How to do it:** make three iron tools. **By default this does not include the iron sword.**

**What you can make:** iron pickaxe, iron axe, iron shovel, iron hoe.

**Why the iron sword is excluded:** it costs a little less to craft and it's the tool people use most. Counting it would put this task out of step with the normal tier.

### Dinner's On! · `normal_fishing`

> Genuinely fish up 3 fish with a fishing rod

**How to do it:** go fishing and catch three.

**What "genuinely fish up" means:** only fish pulled out of the water count. Fish found in chests, or handed to you by another player, don't.

**Fish that count:** cod, salmon, pufferfish, tropical fish.

---

## Hard tasks (5 in the pool, 3 drawn)

### Power of Knowledge · `hard_enchant`

> Successfully enchant 1 item at an enchanting table

**How to do it:** enchant something at an enchanting table.

**Anvils don't count.** Applying a book at an anvil doesn't do it — you have to use the enchanting table itself.

**Why this is hard:** it needs experience levels, lapis lazuli, bookshelves, and an enchanting table. For a new player that's a whole construction project in front of the goal.

### Tycoon! · `hard_tycoon`

> Complete 5 villager trades in total today

**How to do it:** trade with villagers five times.

**Note "in total today":** this isn't five trades in one sitting, it's five across the whole day. So you can pick them up while working on the other tasks.

**How it differs from Tax Collector:** Tax Collector needs one trade, Tycoon needs five. If both are drawn on the same day, doing five completes the two of them at once.

### Deadeye · `hard_marksman`

> Defeat 1 eligible flying mob with a bow or crossbow

**How to do it:** shoot down one flying mob with a ranged weapon.

**You can shoot:** phantoms, ordinary ghasts, vexes, bees, bats, parrots, and allays.

**⚠ The Happy Ghast never counts.** That is a **hard exclusion** in both the code and the config validation — whatever you change in the `entities` list, the Happy Ghast will not be counted.

**Why the exception exists:** in this version the Happy Ghast is positioned as a friendly, rideable creature. Encouraging players to shoot it with arrows goes against the design intent. Don't try to work around this rule.

**An ordinary ghast is fine.** It and the Happy Ghast are two different entities.

### Muscle Memory · `hard_mlg_water`

> Complete an MLG water landing from a height of at least 16 blocks

**How to do it:** fall from 16 blocks or higher, place a bucket of water before you land, and land in the water.

**Two important constraints:**

- **The water has to be placed by you during this fall.** A pool you prepared on the ground beforehand doesn't count — that would take the "MLG" out of it.
- **There's a 3-second window on the placement.** From the moment you empty the bucket to the moment you land in the water, no more than 3 seconds may pass. So you can't place the water early and drift down at your leisure.

**What is measured is the fall itself.** The distance is counted from the highest point of your fall down to the water you land in — not your absolute height above the world. A drop of 20 blocks clears the requirement whether you started at Y 90 or at Y -30.

**Why this lands in the hard tier:** it takes genuine execution and timing judgement. Of the whole task pool, this is the one that tests your reflexes hardest.

### Village Villain · `hard_iron_golem`

> Defeat 1 iron golem with your own hands

**How to do it:** kill an iron golem.

**Mind the consequences:** iron golems are a village's guards. Killing one upsets the villagers, and it hits back hard. This is a hard task that earns the name.

---

## The complete task ID table

These 20 IDs are **stable**. They appear in the database's rotation and progress records.

| Difficulty | Display name | Stable task ID |
| --- | --- | --- |
| Easy | Little Gardener | `simple_gardener` |
| Easy | Stay Healthy | `simple_healthy` |
| Easy | Lighten the Load | `simple_shear_sheep` |
| Easy | Good Morning, {server}! | `simple_good_morning` |
| Easy | Homework | `simple_crafting` |
| Easy | Eat Well Today | `simple_eat_food` |
| Easy | Pastoral Idyll | `simple_pastoral` |
| Normal | Bumper Harvest! | `normal_harvest` |
| Normal | Dessert Master | `normal_dessert` |
| Normal | Tax Collector | `normal_tax_collector` |
| Normal | Daily Cleanup | `normal_cleanup` |
| Normal | Deep Dweller | `normal_deepslate_worker` |
| Normal | Worth the Wait… | `normal_smelt` |
| Normal | The Blacksmith's Forge | `normal_blacksmith` |
| Normal | Dinner's On! | `normal_fishing` |
| Hard | Power of Knowledge | `hard_enchant` |
| Hard | Tycoon! | `hard_tycoon` |
| Hard | Deadeye | `hard_marksman` |
| Hard | Muscle Memory | `hard_mlg_water` |
| Hard | Village Villain | `hard_iron_golem` |

**Display names can be changed freely; IDs cannot be touched.** Renaming `simple_gardener` to `gardener` orphans every progress record that already exists.

Display names and descriptions live under `daily-tasks.tasks.<id>` in `messages.yml`, and editing them doesn't affect any progress. 🐾

---

## Server owner configuration

### The basic structure

```yaml
daily-tasks:
  enabled: true
  timezone: Asia/Shanghai
  draw-count: {easy: 3, normal: 3, hard: 3}
  rewards:
    easy: {coins: 20, exp: 15}
    normal: {coins: 35, exp: 25}
    hard: {coins: 60, exp: 40}
  pools:
    easy: [simple_gardener, simple_healthy, simple_shear_sheep, simple_good_morning, simple_crafting, simple_eat_food, simple_pastoral]
    normal: [normal_harvest, normal_dessert, normal_tax_collector, normal_cleanup, normal_deepslate_worker, normal_smelt, normal_blacksmith, normal_fishing]
    hard: [hard_enchant, hard_tycoon, hard_marksman, hard_mlg_water, hard_iron_golem]
```

| Field | What it does |
| --- | --- |
| `enabled` | The master switch |
| `timezone` | When the day turns over. Affects check-in and the enchantment batch too |
| `draw-count` | How many are drawn per difficulty |
| `rewards` | Coins and experience per difficulty |
| `pools` | The candidate tasks per difficulty |

### How far you can take it

**You can change:** the reward numbers, the draw counts, which tasks sit in which pool, each task's target number, its name and description, and the Material / EntityType / sound and particle values for the tasks that use them.

**You cannot change:** the 20 task IDs. Deleting or renaming them breaks the continuity of existing rotation and progress records.

**You must preserve:** the rule that permanently excludes the Happy Ghast from Deadeye.

### When to change the config

Reward changes take effect through `reload` and are **not retroactive for tasks already claimed** — a player who claimed today under the old rewards won't be topped up.

**Be careful when changing the timezone or the task pools.** Those changes affect what the next rotation selects, and doing it mid-operation can make the current day's tasks look strange. The recommendation is to **stop the server, make the change before the day turns over, and take a backup first**.

If you're only changing display text, go ahead any time and `reload` — no progress is affected.

### Constraints on target values

- The target must be a positive number;
- Materials and EntityTypes must exist in Paper 26.2;
- The number of tasks drawn can't exceed the number of tasks in the pool. With `draw-count.easy` at 3, for example, the easy pool needs at least 3 tasks in it.

### Admin commands

```text
/nekocore tasks status
```

Lists the nine tasks drawn for today, with difficulty, task ID, and name.

```text
/nekocore tasks reroll confirm
```

Redraws today's set of tasks. **That `confirm` is a deliberate second confirmation** — the old rotation's progress is kept as an archive, and the new tasks start from 0.

```text
/nekocore tasks reset <player>
```

Clears one player's progress in the current rotation.

```text
/nekocore tasks progress <player> <taskId> <amount>
```

Manually advances one player's progress on a task. For fixing a bug or testing a new configuration.

---

## Troubleshooting

Work through it in this order:

1. **`/nekocore status`** to confirm Tasks is enabled.
2. **Do the server's timezone and `daily-tasks.timezone` agree?** The day turning over at the wrong moment almost always comes from here.
3. **Is the task ID still defined?** Delete an ID from a pool and, if it was already drawn, you'll get a "definition not found" situation.
4. **Are the target types spelled correctly?** A misspelled Material or EntityType makes the reload fail, and NekoCore will suggest the closest matching names.
5. **Has the player already completed and claimed it today?** This is the most common case, and the easiest one to misread as a bug.
6. **Run `/nekocore config check`** to confirm the configuration itself is legal.

**Never do this: delete database tables to force the tasks to refresh.**

That breaks the link between rotation and progress. If you want different tasks, use `/nekocore tasks reroll confirm`.

---

## What the automated tests cover

The automated tests cover the task selection logic, progress advancement, and the various event scenarios (harvesting, kills, crafting, trading, enchanting, MLG water, and so on).

**Not covered:** real mob behaviour, edge cases in block events, and how things behave when another plugin cancels an event.

For example: if WorldGuard blocks block breaking in a region, what happens to task progress? The answer depends on when the event is cancelled, and **you'll need to verify it on a real server**. The full boundaries of verification are in [release verification](../../VERIFICATION.md).

---

## Related documentation

- **What the tasks feel like in game** → [Quick Start](QUICKSTART.md)
- **Changing rewards or swapping the task pools** → [Recipes](RECIPES.md#i-want-to-change-daily-task-rewards)
- **Where task rewards sit in the wider economy** → [Economy](ECONOMY.md)
- **Every configuration field explained** → [Configuration](CONFIGURATION.md)
- **Tasks aren't responding** → [Compatibility and common problems](COMPATIBILITY.md)
