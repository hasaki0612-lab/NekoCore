# 配方手册：我想……

[English](../en/RECIPES.md) | [返回 README](../../README.md)

这一页是最不用动脑子的一页。

不解释原理，不讲设计思路，只回答一件事：**你想做某个改动的时候，到底该改哪几行。** 每节的结构都一样：

> 最短结论 → YAML → 操作步骤 → 怎么确认成功 → 常见坑

想理解背后机制的话，[配置手册](CONFIGURATION.md)才是那个地方；这里只负责"照着抄能work"。

---

## 抄之前先看这三句

1. **改的是"合并"不是"替换"。** 下面的片段请**并进**你现有的 `config.yml`，把它当成"这几个字段应该长这样"。别整份文件删掉重贴，那样会把没展示出来的同级字段一起丢掉。

2. **改完的验证节奏是固定的：**

   ```text
   /nekocore config check     ← 先确认没写错，不会应用任何改动
   /nekocore reload           ← 确认没问题了，真正生效
   ```

   如果改动涉及**安装插件、换世界插件、改数据库文件名**，就老老实实停服重启。这类东西 reload 覆盖不到。（加入 MMDB 文件是例外，`reload` 就行——见下面的 GeoIP 一节。）

3. **YAML 只用空格缩进，不要用 Tab。** 这一条在这份文档里会显得啰嗦，但它确实是新手翻车率最高的地方。

---

## 玩家体验

### 我想改服务器名字

```yaml
branding:
  server-name: "猫猫服"
```

改完 `reload`。

所有用了 `{server}` 的地方都会跟着变：TAB 页眉、欢迎标题、GUI 标题、Tips 文案、吉祥物全息字、每日任务里那句「早安！{server}！」，以及签到 GUI 里的提示。

**常见坑：** 名字里有空格、`#`、`:` 的时候一定要包在引号里。`server-name: My #1 Server` 会被 YAML 理解成"值是 `My`，后面是注释"。写成 `server-name: "My #1 Server"` 才对。名字长度限制在 1–64 个可显示字符；填了空值或者控制字符，整次重载会被拒绝（服务器照常跑，用旧配置）。

---

### 我想改欢迎标题和 Tips

在 `config.yml` 里调节奏：

```yaml
welcome-title:
  enabled: true
  delay-ticks: 15
  fade-in-ticks: 10
  stay-ticks: 60
  fade-out-ticks: 10
tips:
  enabled: true
  interval-seconds: 300
  prefix: '&#9FD9F6tips &f>> '
  messages:
    - '&f欢迎来到 &#9FD9F6{server}&f！输入 &#9FD9F6/menu &f打开服务器面板。'
    - '&f睡床或用 &#9FD9F6/sethome &f记下小窝，输入 &#9FD9F6/home &f即可返回。'
```

改完 `reload`。

**几个概念：** `ticks` 是 Minecraft 的时间单位，20 ticks = 1 秒。所以 `delay-ticks: 15` 是进服 0.75 秒后弹出标题，`stay-ticks: 60` 是停留 3 秒。`interval-seconds` 是 Tips 的间隔，默认 300 秒 = 5 分钟。

Tips 是**每轮只播一条**，播完最后一条再回到第一条。重载之后会从第一条重新计时。

**常见坑：** `tips.enabled: true` 的时候，`messages` 列表不能是空的，否则重载会被拒绝。

---

### 我想要自己的欢迎语 / 不要 Tips

关掉就行：

```yaml
tips:
  enabled: false
```

玩家进服时中央标题还在，只是不再周期性地收到 Tips。`welcome-title.enabled: false` 则会连中央标题一起关掉。

**关掉这些不会影响任何核心玩法**，只是少了一点氛围。如果你的服务器已经有自己的欢迎插件，果断关掉，别让两套提示同时刷屏。

---

### 我想让玩家看到自己在哪个省

这个需要额外的数据文件，不是改一行就能用的。最短版本：

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

**⚠ 别把 `cache-session` 改成 `false`。** 它不会变成"实时查询"，而是让所有玩家都不显示地区。保持 `true`。

步骤：

1. 去 [MaxMind 的 GeoLite2 官方页面](https://dev.maxmind.com/geoip/geolite2-free-geolocation-data/)按其条款注册并下载 **City** 版数据库（不是 Country 版，也不是压缩包本身）。
2. **停服。**
3. 把文件命名为 `GeoLite2-City.mmdb`，放到 `plugins/NekoCore/`（和 `config.yml` 同一个目录）。
4. 改上面的配置。
5. **执行 `/nekocore reload`**（重载会重新打开数据文件并清空会话缓存，新文件立刻可用；想完整重启也可以）。
6. 运行 `/nekocore status` 看 GeoIP 状态。

怎么确认成功：进游戏看 TAB，自己名字前面应该出现类似 `[广东]` 或 `[Japan]` 的粗粒度前缀。

**常见坑：**

- **别把压缩包直接丢进去。** 从 MaxMind 下载到的是 `.tar.gz`，要先解压出里面的 `.mmdb`。
- **别漏了重启。** 这是最容易白折腾半小时的一条。
- **玩家自己可以关。** `/menu` 里有个"显示网络地区"的隐私开关，玩家关掉之后就不会显示。这是有意设计的，不是 bug。
- **绝不公开 MMDB 文件和 license key。** 它受 MaxMind 的许可约束，不要提交到 Git，也不要放进公开的整合包。

详细说明和隐私模型见 [GeoIP 文档](GEOIP.md)。

---

### 我想不显示玩家地区

```yaml
location-prefix:
  enabled: false
tab:
  show-location-prefix: false
```

改完 `reload`。

TAB 的 `show-location-prefix` 只管全局列表；玩家的个人隐私开关是**另一套**，两个都关掉才算彻底不显示。

顺带一提：NameTag（头顶名字）**从来就不显示地区**，这一项不需要额外关。完整 IP 也从不写进数据库、不显示在任何地方。

---

### 我想让 TAB 只显示基础信息

TAB 的内容本身写在 `messages.yml` 的 `tab.header` 和 `tab.footer` 里。想精简的话，直接删掉你不想要的行：

```yaml
# messages.yml
tab:
  header: |-
    &#61C8F2✦ {server} &#A88BE8· 全局面板 &#61C8F2✦

    &#B2C3CF位置  &fX {x}   Y {y}   Z {z}
    &#B2C3CFTPS   {tps_1m} &#B2C3CF· {tps_5m} &#B2C3CF· {tps_15m}
```

可用变量：`{server}`、`{x}`、`{y}`、`{z}`、`{tps_1m}`、`{tps_5m}`、`{tps_15m}`、`{online}`、`{max_players}`、`{coins}`、`{time}`、`{uptime}`。

改完 `reload`。

**为什么这里要整段替换？** 因为 `|-` 是 YAML 的多行文本写法，缩进决定内容边界。删中间某一行的时候，注意别把其他行的缩进搞乱。

---

### 我已经有自己的 TAB 插件了

```yaml
tab:
  enabled: false
  show-location-prefix: false
  refresh-seconds: 1
```

改完 `reload`，然后让另一个 TAB 插件接管列表。

**为什么要明确选一边？** 两个插件同时往 TAB 里写内容，最后显示什么取决于谁后写——而且这个顺序在重启之后可能变化。看起来像"随机失灵"，实际上是所有权没定清楚。

如果你还是想在别的 TAB 插件里显示 NekoCore 的数据（金币、等级），装 PlaceholderAPI，然后使用 `%nekocore_*%`。这样 NekoCore 管数据、对方管显示，各司其职。

同理，聊天栏的等级前缀也可以关：

```yaml
chat:
  level-prefix-enabled: false
```

已经用了 `%nekocore_display_prefix%` 的聊天插件必须关掉这一项，否则前缀会出现两次。

---

## 服务器结构

### 我想只开一个普通生存服

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

改完 `reload`。

只要 `world` 这个已加载就行。`/menu` 会自动隐藏关掉的入口并把剩下的按钮重新居中——玩家看到的是一块干净的、只有三四个按钮的面板，不会有一堆灰色占位。

**常见坑：** 上面那些 `0.5 / 64.0 / 0.5` 是**占位值**，不是可用坐标。它们存在的意义只是让配置结构看得完整。只要对应的 `enabled` 还是 `false`，NekoCore 绝不会往这些坐标传送任何人，也不会在 0,0,0 生成任何东西。

---

### 我想做 lobby + survival 双世界

把主入口指向你的生存世界：

```yaml
survival:
  enabled: true
  world: survival
  command: 'mvtp {player} {world}'
```

步骤：

1. 安装 [Multiverse-Core](https://hangar.papermc.io/Multiverse/Multiverse-Core)。
2. 导入并加载名为 `survival` 的世界。
3. **停服**，改上面的配置。
4. 重启（装了新插件必须重启，reload 不够）。
5. 进游戏看 `/menu`，点"生存一区"。

怎么确认成功：点击后应该被送到 `survival` 世界的出生点。

**要清楚的边界：** NekoCore **不创建世界**，也**不接管死亡重生**。大厅的出生点和重生点由你自己的 Lobby 插件或 Multiverse 配置管理。NekoCore 在这里只负责"从菜单把你送过去"。

---

### 我想加第二个生存世界

```yaml
survival-new:
  enabled: true
  world: world_secondary
  command: 'mvtp {player} {world}'
```

步骤：

1. 先让你的世界插件把这个世界创建/导入并**加载**好。
2. 停服，改配置。
3. 重启。
4. 看 `/menu` 里有没有多出"生存二区"。

**常见坑：** 世界没加载的时候，这个入口会安静地消失，不会报错。所以"看不到按钮"的第一反应应该是**去控制台确认世界名**，而不是怀疑插件坏了。世界名区分大小写。

---

### 我想改菜单的排版

默认是自动布局：关掉或不可用的模块会隐藏，剩下的按钮重新居中。

想自己排的话：

```yaml
gui:
  enabled: true
  auto-layout: false
  title: '&#9FD9F6{server} &7· &#9FD9F6服务器面板'
  rows: 5
  items:
    profile:
      slot: 10
    daily:
      slot: 12
    checkin:
      slot: 14
```

改完 `reload`。

`rows` 可以是 1–6。八个入口的 `slot` 必须互不重复，而且都在 `rows × 9` 的格数范围内（5 行 = 45 格，合法编号 0–44）。

**什么时候该关掉 auto-layout？** 当你希望按钮**永远待在同一个位置**的时候。自动布局的好处是好看，代价是位置会随启用模块变化。有些服主更喜欢固定布局，因为玩家会形成肌肉记忆。两种都合理。

**常见坑：** slot 越界或者重复，重载会被拒绝，并告诉你是哪个物品的问题。

---

### 我想改某个菜单按钮的图标或说明

GUI 的每一项都在 `config.yml` 的 `gui.items.<id>` 下面，图标和文字都能改：

```yaml
gui:
  items:
    checkin:
      slot: 14
      material: SUNFLOWER
      name: '&#9FD9F6每日签到'
      lore:
        - '&#B2C3CF今日状态：{checkin_status}'
        - '&#B2C3CF小礼物：&#FFE49A{checkin_coins} 金币 &7+ &#FFE49A{checkin_exp} 经验'
        - ''
        - '&#C5E9FA每天见一面，成长多一点喵~'
```

改完 `reload`。

`material` 用的是 Paper 的 Material 枚举名，必须是大写、下划线分隔，而且当前版本得真的有这个物品（比如 `SUNFLOWER`、`NAME_TAG`、`DIAMOND_SWORD`）。

**常见坑：** 名字拼错了重载会失败，但 NekoCore 会给你**相近名称的建议**——在控制台里找那句 "你是否想填……"，直接照着改就行。

---

### 我想关掉整个面板

```yaml
gui:
  enabled: false
```

改完 `reload`。`/menu` 会提示这个功能暂时停用，但所有对应的命令（`/checkin`、`/store`、`/bag`、`/sethome`……）**照常可用**。

菜单只是一个入口，不是功能本身。所以如果你打算用 NPC 或者告示牌来做导航，关掉 GUI 完全可行。

---

## 经济与数值

### 我想改签到奖励

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

改完 `reload`。

**奖励变更不会追溯。** 已经领过今天奖励的玩家不会补差额，也不会被回滚。所以调数值最好的时机是换日之前，或者干脆接受"今天有一批人按旧价领了"。

**关于 `timezone`：** 它决定"一天"从什么时候开始。默认 `Asia/Shanghai` 就是北京时间 00:00 换日。改这个值会影响签到、每日任务，以及附魔书批次。运营中改时区会打断当天节奏，建议在换日前停服改，顺手备份一下。

**关于 `particle-count`：** 这是签到成功时从玩家身上飘起来的白色小星星数量。设成 `0` 就关掉粒子，功能不受影响。

**常见坑：** 负数奖励、非法时区名、错误的 sound 名称都会让整次重载失败。想测数值的话，先在 `config check` 阶段确认。

---

### 我想改商店里某件商品的价格

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

改完 `reload`。

**关键规矩：只改字段，不改 ID。**

`bread` 这个 key 是**稳定商品 ID**。它出现在每日限额记录、交易日志和崩溃恢复数据里。你把它改成 `mianbao`，NekoCore 看到的是一个全新的商品：额度从头开始，而旧的 `bread` 记录变成孤儿。想改显示名？改 `name` 字段就行。

**价格修改同样不追溯。** 已经完成的交易按当时的价格结算，不会被重写。

**其他可改字段：**

| 字段 | 作用 |
| --- | --- |
| `category` | 归到哪个分类（必须是已定义的分类 ID） |
| `material` | 实际给的物品 |
| `name` | 显示名 |
| `price` | 每件售价（金币） |
| `sell` | 允不允许玩家卖回来 |
| `buy-limit` | 每天最多买几件；省略时 = 该物品的原版最大堆叠数 |
| `sell-limit` | 每天最多卖几件；省略时用 `default-sell-limit`（默认 200） |

定价没有标准答案。上线前大概估一下：玩家一天通过签到 + 任务能拿多少金币、刷怪塔或者农场一天产出多少可卖的东西、服务器大概多少人。**先抬高奢侈品价格、给容易刷的东西加买卖限额**，然后观察交易日志，比一开始就精确算公式实用得多。详细思路见[经济文档](ECONOMY.md)。

---

### 我想给商品加买卖限额

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

改完 `reload`。

这个例子里，回响碎片每人每天最多买 4 个。默认配置里这个商品本身就带 `buy-limit: 4`，可以作为参考模板。

限额是按**玩家**算的，不是全服共享。重置和管理可以用：

```text
/nekocore store status <玩家名>
/nekocore store reset <玩家名>
```

---

### 我想调整出售回购比例

```yaml
store:
  default-sell-limit: 200
  sell-price: {numerator: 2, denominator: 3}
```

改完 `reload`。

`sell-price` 是一个分数：玩家卖东西拿到的钱 = 买入价 × `numerator / denominator`。默认 `2/3`，也就是卖回原价的约 66.7%。

**这个比例的调法：** 分数越高，玩家越愿意把多余产出换成钱，但"买了再卖"的损失也越小。分数越低，通胀压力越小，但玩家可能觉得卖东西不值。

**⚠ 有一个硬性上限：`numerator` 不能大于 `denominator` —— 卖价永远不能高于买价。** 写成 `3/2` 会被直接拒绝。所以最多只能调到 `1/1`，不能调到"买卖就赚钱"的方向。这是刻意的防套利设计。

如果某件商品特别容易刷，单独给它设 `sell: false` 通常比调低全局比例更精准。

---

### 我想改每日任务奖励

```yaml
daily-tasks:
  rewards:
    easy: {coins: 40, exp: 30}
    normal: {coins: 70, exp: 50}
    hard: {coins: 120, exp: 80}
```

改完 `reload`。

这是把默认值（20/15、35/25、60/40）翻倍。**已领取的记录不会回滚或补差额。**

默认值设计成一天全清共 **345 金币 / 240 EXP**。这个量级的意义是"值得做，但不会取代主要经济玩法"——你不会希望玩家每天上线十分钟领完任务就下线。想清楚你希望任务在玩家收入里占多大比例，再决定要不要翻倍。

难易梯度的比例也值得保留：困难任务给的是简单任务的三倍左右，跨度太平会让难度失去意义。

20 个任务各自怎么完成，见[每日任务文档](DAILY-TASKS.md)。

---

### 我想换掉今天的任务池

```yaml
daily-tasks:
  pools:
    easy: [simple_gardener, simple_healthy, simple_shear_sheep, simple_good_morning, simple_crafting, simple_eat_food, simple_pastoral]
    normal: [normal_harvest, normal_dessert, normal_tax_collector, normal_cleanup, normal_deepslate_worker, normal_smelt, normal_blacksmith, normal_fishing]
    hard: [hard_enchant, hard_tycoon, hard_marksman, hard_mlg_water, hard_iron_golem]
  draw-count: {easy: 3, normal: 3, hard: 3}
```

改完 `reload`（清单本身的改动），然后如果要立刻换掉今天已经抽出来的任务：

```text
/nekocore tasks status
/nekocore tasks reroll confirm
```

`draw-count` 决定每个难度抽几个。默认各 3 个，一天共 9 个任务。注意每个池子里的任务总数要 ≥ 抽取数量，否则抽不满。

**常见坑：** 从池子里**删掉**某个 task ID 会破坏已有进度的连续性——`reroll` 的提示里也说了"旧 rotation 进度保留归档，新任务从 0 开始"。改结构（池子、时区、目标类型）建议在换日前停服做，并先备份。只改显示文字（`messages.yml` 里的 `name`、`description`）则随时可以，不影响任何进度。

---

### 我想调头衔的价格和权益

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
      description: ['&f像初雪一样轻盈', '&#B2C3CF上线自动签到', '&#B2C3CF传送冷却 120 秒 · 挂机 EXP ×1.10']
```

改完 `reload`。已购买的头衔不会被撤销，改价格也不退款。

**可以改的：** 显示名、前缀、图标、槽位、档位、价格、权益开关和数值、说明文字。

**不能改的：** `mame`、`momo`、`sora` 这三个 key。它们是稳定头衔 ID，玩家的购买记录挂在上面。

**三条设计规则值得知道：**

1. **权益按已拥有的最高档位给。** 玩家买了 Neko 之后换戴 Yuki 的外观，仍然享受 Neko 的传送冷却。
2. **当前装备项只决定外观。** 换来换去不会丢任何东西。
3. **`worlds` 决定头衔小铺在哪些世界能打开。** 默认只有 `world`。玩家不在这个列表里的时候会提示回大厅找头衔 NPC。

三档的`rank`（1/2/3）决定谁是"最高档位"，改它等于重新定义权益阶梯，改之前想清楚。

---

### 我想改每日附魔书的价格

```yaml
store:
  enchantments:
    pool: [protection, fire_protection, /* ...完整列表见 config.yml... */]
    excluded: [binding_curse, vanishing_curse]
    maximum-price: 1800
    normal-price: 300
    prices: {mending: 3600, silk_touch: 2400, infinity: 2400, swift_sneak: 2700, wind_burst: 3600}
```

改完 `reload`。

**这套价格怎么算的：**

- 每天会有一批附魔书，包含 **2 本原版最高等级**的书和 **6 本低阶**书；
- `maximum-price` 是最高等级档位的价格，`normal-price` 是低阶档位的价格；
- `prices` 里写的是**每一本的覆盖价**（不乘等级）。没写进 `prices` 的书用上面两个档位价。

所以上例中经验修补单独定价 3600，其他最高等级书 1800，低阶书 300。

**两个约束：** 低阶书必须是 I~II 级且严格低于原版最高等级；`excluded` 里的诅咒附魔永远不会进池子。改池子的时候别把 `binding_curse` / `vanishing_curse` 从排除列表里拿出来。

想立刻换一批：

```text
/nekocore store refresh-enchants
```

附魔书**不回收**（商店里会显示"不回收"），所以不用担心玩家买来又卖回去刷钱。

---

## 模块开关

### 我想做挂机池

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
    interval-seconds: 300
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

步骤：

1. 先在游戏里**建好那个水池**，然后站在水池中央水面位置，用 `F3` 读出自己的精确坐标。
2. 填进 `teleport`，`yaw` 是水平朝向、`pitch` 是俯仰，都填 `0.0` 通常就行。
3. 把 `enabled` 和 `position-configured` **同时**改成 `true`。
4. `reload`。
5. 进游戏打开 `/menu`，应该能看到"挂机池"入口了。

**为什么有两把锁？** 因为坐标是最容易出事的东西。如果只有 `enabled` 一把锁，一个手快的管理员打开功能、坐标还没填，全服玩家点一下就会被传送到 `x: 0.5, y: 64.0, z: 0.5`——可能是虚空、可能是别人家的地基。两把锁的意思是：**插件永远不会用占位坐标传送任何人。**

**实际怎么工作：** 玩家点菜单入口被送到水池上方。真正开始计时是**进入水体之后**，不是落地就开始。中途短暂踩出水（比如跳起来换气）时，`exit-grace-seconds: 2` 给了 2 秒的宽限——2 秒内回到水里，倒计时继续；超过 2 秒就重置。

奖励按 `interval-seconds` 节奏发放，默认每 300 秒（5 分钟）一次：固定 `base-exp` 经验，再乘 `normal-multiplier`；金币是按 `coin-chance` 概率给的，一次给 `coin-min` 到 `coin-max` 之间。

**常见坑：**

- **示例坐标千万别照抄。** 上面那组 `120.5 / 65.0 / -30.5` 是演示用的，抄到你的服务器上就是把玩家送去不知道哪里。
- **`enabled: true` 但 `position-configured: false` 的时候什么都不会发生**，这是正常的，不是 bug。
- **开着背包界面不等于停止计时。** `hide-while-inventory-open: true` 只是把屏幕上的提示标题藏起来，让玩家看得清背包，计时照旧。

---

### 我想放一个金币周榜

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

步骤：

1. 站到你想挂榜单的位置，用 `F3` 读坐标。
2. 填进配置，两把锁都开成 `true`。
3. `reload`。
4. 回游戏看那个位置应该出现一块悬浮的文字牌。

**显示什么：** 前 10 名的本周金币**收入**排名。注意是"本周赚了多少"而不是"现在有多少余额"——一个富有的老玩家如果这周没赚过钱，不会自动霸榜。榜单文字在 `messages.yml` 的 `weekly-coins` 里改（第一、二、三名有独立的颜色）。

**两把锁的行为差别：** 这里和挂机池略有不同。`position-configured: false` 的时候**统计照常进行**，只是不生成显示，并且会在日志里记一条 WARNING 提醒你。所以你可以放心地先开着 `enabled: true` 收集数据，等想好挂哪儿了再补坐标。

**常见坑：**

- **看不到牌子时先确认两件事：** 世界已加载，两个开关都是 `true`。
- NekoCore **只管理带自己标记的 TextDisplay**。它会清理自己创建的，但绝不会去动其他插件的悬浮实体。反过来说，如果你用别的插件在同一个位置放了东西，两边都可能显示。
- **默认绝不会在 0,0,0 生成任何东西。**

---

### 我想加一个 Citizens 吉祥物

先澄清一件事，因为这可能是最容易误解的地方：

> **Mascot 是 NekoCore 自己内置的模块，不是另一个第三方插件。**
> **Citizens 负责的只是"NPC 实体"本身**——那个站在那里的模型。

所以流程是：Citizens 提供身体，NekoCore 提供灵魂。

```yaml
mascot:
  enabled: true
  npc-id: 3
  hologram-enabled: true
  hologram-lines:
    - '&#9FD9F6✦ {server} Mascot ✦'
    - '&#B2C3CF右键和我打招呼'
  hologram-y-offset: 2.85
  interaction-window-seconds: 15
  normal-click-limit: 5
  over-limit-chat-cooldown-seconds: 2
  normal-particle: minecraft:end_rod
  normal-particle-count: 8
  over-limit-particle: minecraft:smoke
  over-limit-particle-count: 18
```

步骤：

1. 装 [Citizens](https://github.com/CitizensDev/Citizens2)（官方下载说明见 [Citizens 下载页](https://wiki.citizensnpcs.co/Downloads)），**完整重启**。
2. 自己用 Citizens 的命令创建并放置 NPC——外观、名字、皮肤都由你决定。
3. 站在 NPC 旁边用 `/npc id` 读出它的编号。
4. 把编号填进 `mascot.npc-id`，`enabled` 改成 `true`。
5. 想改回复内容的话，编辑 `messages.yml` 里的 `mascot.replies`（正常回复池）和 `mascot.over-limit-replies`（点太快时的回复池）。
6. **完整重启。**

怎么确认成功：靠近 NPC 时头顶应该出现两行全息字；右键点它，聊天栏会冒出一句回复，同时有粒子飘出来。

**互动是怎么设计的：** 右键触发一句随机回复 + 一点粒子。如果短时间内狂点（默认 15 秒窗口内超过 5 次），会切到"超限"回复池——那些回复的语气是"慢一点啦"，同时换成另一套粒子，并且有 2 秒的对话冷却防止刷屏。

**NekoCore 不创建、不重命名、不移动、不换皮任何 NPC。** 删除 NPC 也是你在 Citizens 里自己删。这条边界是刻意的：你的 NPC 归你管。

**常见坑：**

- **点了没反应**，先按顺序查：Citizens 装了吗？NPC 真的 spawn 了吗？`npc-id` 和 `/npc id` 的数字一致吗？`enabled` 是 `true` 吗？用的是主手右键吗？
- 旧配置里的 `citizens-npc-id` 键仍然被兼容读取，新配置请用 `npc-id`。
- 冷门但真实的一条：Citizens 本身的版本要和 Paper 26.2 兼容。不兼容的话 NekoCore 只会安静地禁用 Mascot，日志里可能有一次性 warning。

---

### 我想让 NPC 被点的时候说别的话

改 `messages.yml`：

```yaml
mascot:
  replies:
    - '&#9FD9F6Mascot：&f欢迎来到 {server}！'
    - '&#9FD9F6Mascot：&f今天也祝你冒险顺利。'
    - '&#9FD9F6Mascot：&f需要帮助时可以问问管理员。'
  over-limit-replies:
    - '&#EDB9CEMascot：&f请慢一点，我还在这里。'
    - '&#EDB9CEMascot：&f互动太快啦，稍等一下吧。'
```

改完 `reload`。

`replies` 是正常点击时的随机回复池，`over-limit-replies` 是点太快时的回复池。两边都可以随意增删条目，支持 `{server}`。

如果你想让 NPC 有性格，**这两套回复的语气差异是关键**：正常的可以热情一点，超限的最好是那种"我还在这儿呢"的温和抱怨，而不是报错。玩家会把它当成角色性格，而不是系统提示。

---

### 我想关掉 Bag

```yaml
bag:
  enabled: false
```

改完 `reload`。

**数据不会被删除。** 玩家的物品还好好地躺在数据库里，重新打开 `enabled` 之后照常能用。

**什么时候该关：** 如果你的服务器已经有一套成熟的背包方案，让两套同时存在会让玩家困惑"我的东西到底在哪"。关掉一个入口比让玩家猜要友好。

**注意：** 关之前最好确认没有玩家正开着 Bag 界面操作。已经在界面里的操作会自然结束，但正在取放的物品需要一个干净的收尾。

---

### 我想只让主世界能用 Bag

```yaml
bag:
  enabled: true
  writable-worlds: [world]
  unlock-levels:
    '27': 10
    '36': 25
  readonly-notice-seconds: 4
```

改完 `reload`。

`writable-worlds` 是**白名单**：列表里的世界可以取放，**其他所有世界一律只读**——包括大厅，也包括你以后新加的任何世界。

这个"默认只读"的语义很重要：新增世界的时候，它会自动是安全的，不需要你记得去改配置。

槽位解锁由 `unlock-levels` 控制：上面例子的意思是 27 号槽位需要 10 级、36 号槽位需要 25 级。没达到等级时那些槽位不可用。

`readonly-notice-seconds` 是只读世界里那条提示的显示时长（秒）。玩家在只读世界打开 Bag 时会看到"这里暂时只能看看 Bag 哦～"。

**什么时候需要这个功能：** 当你用 Multiverse-Inventories 之类的插件做了"不同世界独立背包"的规则时。Bag 如果全图可写，玩家就能把贵重物品塞进 Bag 穿过世界边界，绕过你的规则。

---

## 上线前的最后几件事

### 我想在改配置之前先确认它合法

```text
/nekocore config check
```

这条命令会解析并验证磁盘上的所有配置，报告问题，**但不会应用任何改动**。

它和 `reload` 的区别值得专门记一下：

| | 做什么 | 什么时候用 |
| --- | --- | --- |
| `config check` | 只验证，什么都不改 | 刚改完 YAML，先确认没写错 |
| `reload` | 验证 → 构建完整新配置 → 成功后整体切换 | 确认没问题了，要让它生效 |

两个命令用的是同一套验证逻辑。所以 `config check` 通过，`reload` 基本不会因为配置本身失败。

**遇到报错怎么读：** 错误信息里会有 YAML 路径（比如 `store.products.bred.material`）和期望的类型或范围。Material 拼错的时候还会给相近名称建议。照着提示改就行。

**`reload` 失败不是灾难。** 服务器会继续用旧的、有效的配置跑着，玩家完全感知不到。修好文件再试一次就好。

---

### 我想确认各个模块现在是什么状态

```text
/nekocore status
```

（`/nekocore doctor` 是同义词，两个都行。）

它会显示 NekoCore、Paper、Java 的版本、数据库 schema，以及 Store / Bag / Tasks / GeoIP / Mascot / AFK / Leaderboard / PlaceholderAPI 各自的状态。

**这一条命令能解决九成"某功能没生效"的疑惑。** 遇到问题先看这里，比翻日志快得多。

---

### 我想清理一下地上的掉落物

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

改完 `reload`。

每隔 `interval-seconds` 秒清理一次地面的掉落物，清理前会先给玩家一个倒计时提醒和提示音，让玩家有机会捡起自己的东西。

想手动立刻清理一次：

```text
/nekocore cleanup now
```

**常见坑：** `interval-seconds` 最小是 61 秒。想更频繁的话，用 `cleanup now` 手动触发，或者重新想想是不是真的需要——清理太频繁会打断正在整理箱子的玩家。

---

## 找不到你要的改动？

- **想理解某个字段为什么这么设计** → [配置手册](CONFIGURATION.md)
- **想调整经济数字但不确定调到多少** → [经济文档](ECONOMY.md)
- **想搞懂某个每日任务到底怎么算完成** → [每日任务文档](DAILY-TASKS.md)
- **改完没生效、或者某个模块没反应** → [兼容性与常见问题](COMPATIBILITY.md)
- **想装 Citizens、PlaceholderAPI、Multiverse 或 MMDB** → [依赖说明](DEPENDENCIES.md)
- **要升级、回滚或者卸载** → [安装与升级](INSTALLATION.md)
