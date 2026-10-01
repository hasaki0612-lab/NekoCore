# 配置手册

[English](../en/CONFIGURATION.md) | [返回 README](../../README.md)

这一页是写给**想真正搞懂配置**的人的。

[配方手册](RECIPES.md)回答"我想改 X 该动哪几行"；这一页回答"这个功能实际是怎么工作的，所以那些字段是什么意思"。每个模块都会先用自然语言讲清楚它在游戏里的行为，然后才列字段表。

如果你只是想快速把服务器跑起来，先看[快速开始](QUICKSTART.md)，之后再回来。

---

## 开始之前

### 两个文件

| 文件 | 管什么 |
| --- | --- |
| `plugins/NekoCore/config.yml` | 服务器行为。世界观、经济数值、菜单布局、模块开关 |
| `plugins/NekoCore/messages.yml` | 所有玩家能看到的文字 |

两个文件都有 `-version` 字段（`config-version: 8`、`messages-version: 7`）。**这两个数字不要动。** 它们决定插件怎么读你的文件，改它们不会修好任何问题。

### 编辑规则

YAML 用**空格缩进**表达层级：

- **只能用空格，不能用 Tab。** 看起来一样，解析器会拒绝。
- 同一层级的字段缩进必须一致。
- 冒号后面要有一个空格：`server-name: "猫猫服"`。
- 值里含 `#`、`:` 或者颜色代码时加引号。

### 改完的流程

```text
/nekocore config check     ← 只验证，不应用
/nekocore reload           ← 验证通过后整体切换
```

`reload` 的流程是 **解析 → 验证 → 构建完整的新配置对象 → 成功后切换**。任何一项失败，服务器继续用旧的有效配置跑着，不会出现半新半旧的状态。

**这些改动需要完整重启，reload 覆盖不到：** 更换 JAR、改 `database.filename`、安装新插件、世界插件的变更。

（**加入或更换 MMDB 文件不在这个列表里**——`reload` 会重新打开它。详见下面的 GeoIP 一节。）

（`database.filename` 是特殊的一项：`reload` 会**明确拒绝**文件名被改动的配置，并告诉你"需要完整重启服务器"，而不是给你一个改了一半的状态。）

### reload 成功之后，玩家那边会发生什么

这一节值得读一遍，因为重载**不是完全无感的**：

- **所有 NekoCore 的界面会被关闭**——`/menu`、商店、Bag、任务、头衔小铺都会关掉。
- **正在填写商店自定义数量的输入会被取消。**
- **所有进行中的 `/tpn` 传送请求会被取消**，双方都会收到一条"配置正在更新，这次传送请求已取消"。
- **GeoIP 的会话缓存会被清空**，在线玩家会被重新解析一次。

所以在玩家活跃的时段重载，会有一些人察觉到。这通常无所谓，但**如果你想在大规模改动后重载，最好挑人少的时候**，或者提前在群里说一声。

### reload 的两个前置条件

有两种情况 `reload` 会直接拒绝执行：

1. **已经有另一次重载在进行中** → 提示"配置正在更新，稍后再试吧"。
2. **有进行中的商店/Bag 交易，或者有待确认的头衔购买** → 提示"正在收好这次物品，稍等一下再操作"。

第二条是保护数据：一个正在跨"数据库 + 玩家背包"两阶段提交的交易，不应该被配置切换打断。

---

## branding —— 服务器名字

### 这个功能实际做什么

你在配置里写一个名字，NekoCore 会把它填进所有玩家可见的地方。

支持 `{server}` 的地方包括：TAB 页眉、进服欢迎标题的副标题、GUI 的标题、Tips 文案、吉祥物的全息字、每日任务里那条「早安！{server}！」，以及头衔小铺的标题。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `branding.server-name` | 文本 / `My Server` | 1–64 个可显示字符 |

### 例子

```yaml
branding:
  server-name: "猫猫服"
```

想验证效果，`reload` 之后看 TAB 页眉和 `/menu` 的标题。

**出错的情况：** 空值或者包含控制字符，整次重载会被拒绝。名字里有 `#` 或 `:` 的时候记得加引号。

**什么时候需要重启：** 不需要，`reload` 就够。

---

## 世界与菜单入口

### 这个功能实际做什么

`/menu` 是玩家进入一切功能的地方。面板上的每个入口对应一个"目的地"：

- **生存一区** 指向 `survival.world`
- **生存二区** 指向 `survival-new.world`
- **小游戏区** 指向 `minigames` 里写的一组精确坐标
- **挂机池** 指向 `afk-pool` 里写的一组精确坐标

**这些世界 NekoCore 一个都不会创建。** 世界得由 Multiverse 或者其他世界插件先建好、加载好。世界没加载的时候，对应的入口就**安静地消失**——不是报错，是隐藏。

**自动布局** 是这里的核心行为：关掉的、不可用的入口会被隐藏，剩下的按钮自动重新居中。所以你打开 `/menu` 看到的那一屏，就是这台服务器**现在真正能用**的功能。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `survival.enabled` | boolean / `true` | 主生存入口 |
| `survival.world` | 世界名 / `world` | 主世界 |
| `survival.command` | 文本 | 世界跳转命令，不带 `/`，必须含 `{player}` 和 `{world}` |
| `survival-new.enabled` | boolean / `false` | 第二世界入口 |
| `survival-new.world` | 世界名 / `world_secondary` | 第二世界 |
| `survival-new.command` | 文本 | 同上 |
| `minigames.enabled` | boolean / `false` | 小游戏区入口 |
| `minigames.world` | 世界名 | 目标世界 |
| `minigames.x/y/z` | 数值 | 精确坐标 |
| `minigames.yaw/pitch` | 数值 | 朝向 |
| `gui.enabled` | boolean / `true` | 整个 `/menu` |
| `gui.auto-layout` | boolean / `true` | 隐藏不可用入口并重新居中 |
| `gui.title` | 文本 | 面板标题，支持 `{server}` |
| `gui.rows` | 1–6 / `5` | 面板行数 |
| `gui.items.<id>.slot` | 整数 | 该项在第几格（关掉 auto-layout 后才生效） |
| `gui.items.<id>.material` | Material | 图标 |
| `gui.items.<id>.name` | 文本 | 显示名 |
| `gui.items.<id>.lore` | 文本列表 | 说明文字 |

`gui.items` 下的八个 id 是 `profile`、`daily`、`checkin`、`privacy`、`survival`、`survival-new`、`minigames`、`afk-pool`。

### 例子：单世界生存服

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

**改了哪几行：** 把两个可选的区域关掉，让菜单只显示真正存在的入口。

**为什么这样写：** 关掉的模块不会在菜单里留下空洞——auto-layout 会把剩下的按钮重新居中。玩家看到的是一块干净的、只有四五个按钮的面板。

**是否需要重启：** 不需要，`reload`。

**⚠ 关于坐标：** `0.5 / 64.0 / 0.5` 是**占位值**。它们存在的意义只是让配置结构看起来完整。只要 `enabled` 还是 `false`，NekoCore 绝不会往这些坐标传送任何人，也不会在 `0,0,0` 生成任何东西。这是刻意的：**NekoCore 从不猜坐标。**

### 例子：lobby + survival

```yaml
survival:
  enabled: true
  world: survival
  command: 'mvtp {player} {world}'
```

**改了哪一行：** `survival.world` 从 `world` 改成 `survival`。

**为什么这样写：** 让主入口指向你的生存世界。大厅的出生点和重生点由你自己的 Lobby 插件或 Multiverse 配置管理——**NekoCore 不接管死亡重生**，它在这里只负责"从菜单把你送过去"。

**是否需要重启：** 装了 Multiverse 的话需要重启。世界已经加载、只是改个 `world` 值的话 `reload` 就行。

### 例子：手动排布菜单

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

**改了哪几行：** `auto-layout` 关掉，然后给每一项指定固定的 `slot`。

**为什么这样写：** 有些服主更喜欢固定布局，因为玩家会形成肌肉记忆——"签到永远在第三排中间"。自动布局的代价就是位置会随启用模块变化。

**是否需要重启：** 不需要，`reload`。

**常见错误：** slot 越界（超出 `rows × 9`）或者重复，重载会失败并告诉你哪个物品有问题。

### 没有 Multiverse 怎么办

`survival.command` 里默认写的 `mvtp {player} {world}` 是 Multiverse 的命令。

**没装 Multiverse 的时候不用管它。** NekoCore 检测到命令不可用时会**安全地把你传送到那个已加载世界的 Paper 出生点**。所以只有一个主世界的话，不装 Multiverse 完全没问题。

**依赖命令路由的额外世界**就需要装 Multiverse 了。

---

## database —— 数据存储

### 这个功能实际做什么

NekoCore 把玩家资料、经济、Home、签到、头衔、Bag、商店额度、交易日志、任务进度和周统计都放在一个 SQLite 文件里。

写入不是每次操作都立刻落盘——`save-interval-seconds` 决定后台多久刷一次。这也意味着**正常停服很重要**：`stop` 会给写入一个干净的收尾机会。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `database.filename` | 文本 / `nekocore.db` | 插件目录内的文件名 |
| `database.save-interval-seconds` | 整数 / `30` | 后台保存间隔 |

### 例子

```yaml
database:
  filename: nekocore.db
  save-interval-seconds: 30
```

**该不该改 `save-interval-seconds`？** 大多数服务器不需要。调小会增加磁盘写入频率，调大会增加崩溃时可能丢失的数据量。默认 30 秒是个平衡点。

**是否需要重启：** 改 `filename` **需要完整重启**——数据库在启动时打开。`save-interval-seconds` 可以通过 `reload` 生效。

配置 `filename` 的时候不要写路径分隔符，它只能是插件目录内的文件名。

---

## home —— 小窝

### 这个功能实际做什么

Home 是**按世界分开存的**。玩家在主世界设一个、下界设一个、末地设一个，三个互不覆盖。

```text
/sethome          记下当前位置
/home             回当前世界的家
/home <worldName> 回指定世界的家
/check home <worldName>   看看家在哪
```

**床上自动设 Home** 是另一个机制：玩家成功躺上床的时候，NekoCore 会在床边找一个安全站立位置，把它记成那个世界的 Home。

**它不改原版的重生点，也不改睡眠机制。** 它只是"顺手帮你标记一个方便回去的地方"。

**找不到安全落点的时候会怎样：** 会告诉玩家"床边还没有合适的落脚处，原来的 Home 没有改动"。也就是说，**它宁可什么都不做，也不会把玩家记到一个会卡住的位置**。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `home.bed-auto-set.enabled` | boolean / `true` | 躺床时自动保存 Home |
| `home.bed-auto-set.worlds` | 文本列表 / `[world]` | 允许这个行为的世界，**区分大小写** |

### 例子

```yaml
home:
  bed-auto-set:
    enabled: true
    worlds: [world, world_nether]
```

**改了哪一行：** `worlds` 列表加上了下界。

**为什么这样写：** 默认只有主世界。加上下界之后，玩家在下界睡觉也会顺手记一个 Home——下界回家通常是刚需，因为床在下界会爆炸，玩家没法靠床重生。

**是否需要重启：** 不需要，`reload`。

**常见错误：** 世界名大小写不一致。`World` 和 `world` 在 Paper 眼里是两个东西。列表为空、或者 `enabled: false` 的时候，这个功能就完全不动作。

---

## checkin —— 每日签到

### 这个功能实际做什么

每天一次，玩家可以从 `/checkin` 或者 `/menu` 领一点金币和经验。

**"每天"是按自然日算的，不是"每隔 24 小时"。** 这个区别很重要：

- 玩家今天 23:50 领了，明天 00:10 就能再领——只隔了 20 分钟，但确实是两个自然日。
- 而如果按 24 小时算，同一个玩家得等到明天 23:50。

**在线跨日不需要重进服务器。** 玩家挂机挂过零点，新的签到就自然可用了。

**没领的玩家会收到提醒。** 进服时会有一句"今天的小礼物还没领取"。

**签到成功时** 有一点金币、一点经验、一个音效，以及从玩家身上飘起的几颗白色小星星。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `checkin.enabled` | boolean / `true` | 总开关 |
| `checkin.timezone` | 时区名 / `Asia/Shanghai` | 决定"一天"从什么时候开始 |
| `checkin.coins` | 整数 / `100` | 金币奖励 |
| `checkin.exp` | 整数 / `50` | 经验奖励 |
| `checkin.sound.*` | 声音配置 | 签到音效 |
| `checkin.particle-count` | 整数 / `16` | 粒子数量，`0` 关闭 |

**这个 `timezone` 也影响每日任务和附魔书批次。** 改它等于重新定义整个服务器的"一天"。

### ⚠ 关于"在线跨日"的一个已知不一致

`config.yml` 里 `checkin.enabled` 上方有一句注释写着"在线跨日无需重进"。**这句话和实际行为不符**，这里如实说明：

**自动签到（拥有权益的玩家）只在登录时触发一次。** 系统里没有任何午夜定时器去碰签到。所以一个挂着过零点的玩家：

- **不会被自动签到**（要等下次重新登录，或者服务器重启、插件重载走了登录流程）；
- **但可以手动 `/checkin`** 领到新一天的奖励——因为落库的时候会重新取当天日期；
- 面板上的签到状态文字**不会自动刷新**成"待签到"，需要重新打开 `/menu`。

这个不一致来自资源文件里的注释，没有影响任何实际功能，但值得知道，免得你按注释去推断行为。手动 `/checkin` 永远是可用的兜底方案。

### 例子

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

**改了哪几行：** `coins` 从 100 改到 200，`exp` 从 50 改到 80。

**为什么这样写：** 把签到奖励翻倍。这在"玩家抱怨赚不到钱"的时候是比降低商店价格更温和的做法。

**代价：** 签到收入翻倍之后，商店里的东西相对变便宜了，头衔也会更快被拿到。想清楚再调——或者干脆一起调价格。

**是否需要重启：** 不需要，`reload`。

**注意：** 奖励变更**不追溯**。今天已经领过的玩家不会补差额。

**出错的情况：** 负数奖励、非法时区名、错误的 sound 名称都会让整次重载失败。

---

## daily-tasks —— 每日任务

### 这个功能实际做什么

每天 00:00（按 `daily-tasks.timezone`）从三个池子里各抽几个任务，组成当天的一轮。

**全服玩家共用同一轮**，这样大家在聊天里聊的是同一件事。**但进度和奖励是每个人自己的**，完成与否互不影响。

默认一天九个任务（简单、普通、困难各 3 个），全清共 **345 金币 + 240 EXP**。

每个任务的具体玩法见[每日任务文档](DAILY-TASKS.md)——那里有 20 个任务分别怎么完成的说明。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `daily-tasks.enabled` | boolean / `true` | 总开关 |
| `daily-tasks.timezone` | 时区名 / `Asia/Shanghai` | 换日时间 |
| `daily-tasks.draw-count` | `{easy,normal,hard}` / 各 3 | 每个难度抽几个 |
| `daily-tasks.rewards.<难度>` | `{coins, exp}` | 每个难度的奖励 |
| `daily-tasks.pools.<难度>` | 文本列表 | 该难度的候选 task ID |
| `daily-tasks.rules.<taskId>` | 规则对象 | 每个任务的目标数和适用对象 |
| `daily-tasks.gui.*` | 显示配置 | 填充物、槽位、音效、粒子 |
| `daily-tasks.feedback.*` | 反馈配置 | 完成时的音效和粒子 |

`rules` 里的字段随任务而变：多数用 `target`，有的用 `target-minutes`，有的还带 `blocks`、`materials` 或 `entities` 列表。

### 例子

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

**改了哪几行：** 三个难度的 `coins` 和 `exp` 全部翻倍。

**为什么这样写：** 想让任务在玩家收入里占更大比重。默认值是按"值得做但不取代主要经济玩法"调的。

**代价：** 一天全清从 345 变成 690 金币。如果你同时还有商店和头衔，可能需要一起调整。

**是否需要重启：** 不需要，`reload`。**已领取的记录不会回滚或补差额。**

### 关于 stable task ID

`pools` 里和 `rules` 里的那些 id（`simple_gardener`、`normal_harvest`、`hard_marksman`……）是**稳定的**。它们出现在数据库的 rotation 和 progress 记录里。

**改显示名请改 `messages.yml` 里的 `name` 和 `description`，不要动 ID。** 从池子里删掉或重命名 ID 会破坏已有进度的连续性。

**必须保留的规则：** 乐魂（Happy Ghast）永久排除"百步穿杨"。这一条在代码和配置校验里都是硬性的。

**改时区或任务池的时机：** 这类改动会影响下一轮的选择。运营中改建议在**换日前停服修改，并先备份**。

---

## store —— 商店

### 这个功能实际做什么

`/store` 打开一个八分类的商店，默认有 109 件商品。

**交互方式：右键买入、左键出售。** 选中商品后先选数量——可以从预设里挑，也可以在聊天框里自己填一个数（那条输入不会发给其他玩家）。确认之后交易立刻完成。

**每日限额** 是这里最重要的机制：

- **买入限额** 防"用金币无限换资源"；
- **出售限额** 防"用刷怪塔无限换金币"。

限额按**玩家**算。玩家可以在 `/nekocore store status` 里看到自己的使用情况。

**每日附魔书** 是一个特殊分类，每天 04:00（北京时间）换一批：2 本原版最高等级 + 6 本低阶。**附魔书不回收**，所以玩家没法买来再卖回去刷钱。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `store.enabled` | boolean / `true` | 总开关 |
| `store.category-rows` | 整数 / `5` | 分类页行数 |
| `store.product-layout.slots` | 整数列表 | 商品在页内占哪些格 |
| `store.product-layout.previous-slot` | 整数 / `45` | 上一页按钮 |
| `store.product-layout.next-slot` | 整数 / `46` | 下一页按钮 |
| `store.product-layout.info-slot` | 整数 / `49` | 信息格 |
| `store.default-sell-limit` | 整数 / `200` | 未单独指定时，每个商品的每日出售上限 |
| `store.sell-price` | `{numerator, denominator}` / `2/3` | 回购比例 |
| `store.input-timeout-seconds` | 整数 / `30` | 自定义数量输入的等待时间 |
| `store.maximum-custom-quantity` | 整数 / `1000000` | 自定义数量的上限 |
| `store.categories.<id>` | 分类对象 | `name`、`material`、`slot` |
| `store.products.<stable-id>` | 商品对象 | 见下表 |
| `store.enchantments.*` | 附魔配置 | 见下节 |

商品对象：

| 字段 | 说明 |
| --- | --- |
| `category` | 归到哪个分类 |
| `material` | 实际给玩家的物品 |
| `name` | 显示名 |
| `price` | 每件售价（金币） |
| `sell` | 允不允许卖回来 |
| `buy-limit` | 每日买入上限。省略 = 该物品的原版最大堆叠数 |
| `sell-limit` | 每日出售上限。省略 = `default-sell-limit` |

### 例子：单件商品

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

**改了哪一行：** `price` 从 9 改成 12。

**为什么这样写：** 面包是新手最常见的食物，稍微提价能让前期的金币有意义一点。

**是否需要重启：** 不需要，`reload`。

**⚠ 只改字段，不改 ID。** `bread` 这个 key 是稳定商品 ID，出现在每日限额记录、交易日志和崩溃恢复数据里。改成别的名字，NekoCore 会当成一个全新商品：额度从头开始，旧记录变成孤儿。想改显示名就改 `name`。

### 例子：给易刷商品加限额

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

**改了哪一行：** 加了 `buy-limit: 4`。

**为什么这样写：** 回响碎片是最强的可交易物品之一。限制每天 4 个，让富有的玩家也没法一次毕业。

**是否需要重启：** 不需要，`reload`。

**顺带一提：** 默认配置里这个商品本身就带 `buy-limit: 4`，可以作为参考模板。

### 例子：调整回购比例

```yaml
store:
  default-sell-limit: 200
  sell-price: {numerator: 1, denominator: 2}
```

**改了哪一行：** `sell-price` 从 `2/3` 改成 `1/2`。

**为什么这样写：** 把回购价压到买入价的一半。玩家卖东西的收益变低，服务器的通胀压力随之变小。

**代价：** 玩家会觉得"卖东西不值"，可能干脆把多余产出扔进岩浆而不是换成金币。如果你的服务器本来就有掉落物堆积的问题，压低保回购价会让它更严重。

**是否需要重启：** 不需要，`reload`。

**⚠ 有一条硬性限制：`numerator` 不能大于 `denominator`。** 也就是说**卖价永远不能高于买价**——写成 `3/2` 会被直接拒绝，报一句"卖价不能高于买价"。这一条是为了堵死"买进再卖出获利"的套利路径，所以你不能把它调成赚钱的方向。最多只能调到 `1/1`（平价买卖，没有摩擦）。

### 关于每日附魔书

```yaml
store:
  enchantments:
    pool: [protection, fire_protection, /* ...完整列表见 config.yml... */]
    excluded: [binding_curse, vanishing_curse]
    maximum-price: 1800
    normal-price: 300
    prices: {mending: 3600, silk_touch: 2400, infinity: 2400, swift_sneak: 2700, wind_burst: 3600}
```

**这套价格怎么算：**

- 每天有 **2 本原版最高等级** + **6 本低阶**的书；
- `maximum-price` 是最高等级档的价格，`normal-price` 是低阶档的价格；
- `prices` 里是**每一本的覆盖价**（不乘等级），没写进去的用上面两个档位价。

所以上例中经验修补单独 3600，其他最高等级书 1800，低阶书 300。

**两个约束：**

- 低阶书必须是 I~II 级且**严格低于**原版最高等级；
- `excluded` 里的诅咒附魔永远不进池子。**别把这两项移出去。**

**换批次：** 正常运营不需要干预，每天 04:00 自动换。想立刻换用 `/nekocore store refresh-enchants`，同时会重置本期购买次数。

---

## bag —— 随身仓库

### 这个功能实际做什么

Bag 是一个**跨世界跟着玩家的物品容器**，`/bag` 打开。它和末影箱的区别是：不占物品栏、容量随等级增长、并且遵守世界白名单。

**`writable-worlds` 是白名单。** 列表里的世界可以取放，**其他所有世界一律只读**——包括大厅，也包括你以后新加的任何世界。

**这个"默认只读"的语义很重要：** 新增世界的时候它会自动是安全的，不需要你记得去改配置。

**槽位解锁** 用等级控制：不满 10 级是 18 格，10 级起 27 格，25 级起 36 格。玩家升级的时候会慢慢多出格子，是个温和的成长激励。

**在只读世界打开 Bag** 时玩家会看到一条提示，告诉他们去生存世界就能正常取放。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `bag.enabled` | boolean / `true` | 总开关 |
| `bag.writable-worlds` | 世界名列表 / `[world]` | 允许取放的世界 |
| `bag.unlock-levels` | `槽位: 等级` 映射 | 哪些槽位需要多少级 |
| `bag.readonly-notice-seconds` | 整数 / `4` | 只读提示的节流间隔 |

**`readonly-notice-seconds` 是节流窗口，不是显示时长。** 它的含义是"这么多秒内最多提醒一次"——玩家在只读世界里反复尝试取放时，不会每点一下都被刷一条提示。

### 例子

```yaml
bag:
  enabled: true
  writable-worlds: [world, world_secondary]
  unlock-levels:
    '27': 10
    '36': 25
  readonly-notice-seconds: 4
```

**改了哪一行：** `writable-worlds` 加上了第二生存世界。

**为什么这样写：** 如果你有两个生存世界，让它们都可以取放是合理的——玩家在两个世界之间来回跑的时候不该被限制。

**是否需要重启：** 不需要，`reload`。

**注意 `unlock-levels` 的写法：** key 要用引号包起来（`'27'`），因为它代表槽位编号。值是需要的等级。

### ⚠ 关于 `unlock-levels`：一个必须知道的实现细节

```yaml
bag:
  unlock-levels:
    '27': 10
    '36': 25
```

**很多管理员会以为这两个数字决定"几级开多少格"。实际上不是。**

Bag 的**容量计算是独立硬编码**的，只认两个阈值：

| 条件 | 容量 |
| --- | --- |
| 等级 ≥ **36** | 36 格 |
| 等级 ≥ **27** | 27 格 |
| 其他 | 18 格 |

`unlock-levels.27` 和 `unlock-levels.36` 里填的**等级值**（上面例子的 10 和 25）不参与这个计算。它们的实际用途有两个：

1. 启动时按这两个阈值给已有玩家的 Bag 记录做一次容量回填；
2. 作为 `messages.yml` 里 `bag-status-lore` 的 `{level27}` / `{level36}` 占位符，用来显示"Lv.X 解锁"的文案。

**所以如果把它们改成 `'27': 30`，玩家练到 27 级就已经拿到 27 格了，但界面文案还写着"Lv.30 解锁 27 格"——两者会对不上。**

想改解锁节奏的话，要么保持默认值，要么**同时**改 `messages.yml` 里对应的文案，让它如实描述实际阈值。

另外，容量**只增不减**：玩家升到 27 级之后即使等级因为某种原因掉下来，已经解锁的格子不会收回去。

### 和 Multiverse-Inventories 共存时

如果你用 MVI 做了"不同世界独立背包"的规则，那么 Bag 如果全图可写，玩家就能把贵重物品塞进 Bag 穿过世界边界，**绕过你的规则**。

`writable-worlds` 白名单就是为这个场景准备的：只让生存世界可写，其他世界只读。

**这个组合不在自动测试的保证范围内**，值得在测试服验证跨世界切换、死亡、断线和满背包这几个场景。

---

## tpn —— 传送请求

### 这个功能实际做什么

`/tpn <玩家名>` 发出一个请求，对方用 `/yes` 或 `/no` 回应。

**这是双向同意的设计。** 不是"点一下就瞬移过去"，被传的人有决定权。

**请求会超时**，默认 60 秒。**发起方有冷却时间**，默认 180 秒，通过头衔权益可以缩短到 30 秒。

**接受之后，请求方会被送到"对方接受请求时所在的位置附近"。** 注意这个细节：不是实时跟随对方的位置。这避免了对方一边跑一边接受导致的诡异落点。

有一方离线、或者插件重载配置的时候，进行中的请求会被取消。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `tpn.enabled` | boolean / `true` | 总开关 |
| `tpn.timeout-seconds` | 整数 / `60` | 请求有效期 |
| `tpn.cooldown-seconds` | 整数 / `180` | 普通冷却 |
| `tpn.fast-cooldown-seconds` | 整数 / `30` | 拥有 `nekocore.perk.fast-tpn` 时的冷却 |
| `tpn.sound.*` | 声音配置 | 请求相关的音效 |
| `tpn.particle-count` | 整数 / `12` | 粒子数量 |

### 例子

```yaml
tpn:
  enabled: true
  timeout-seconds: 60
  cooldown-seconds: 180
  fast-cooldown-seconds: 30
  sound: {name: 'minecraft:block.amethyst_block.chime', volume: 0.6, pitch: 1.5}
  particle-count: 12
```

**该不该改冷却？** 这取决于你的服务器想让传送请求有多"稀缺"。

- **冷却是为了防止骚扰。** 如果冷却太短，玩家可以不停给同一个人发请求。180 秒是个保守的值，适合"TPN 是需要克制的功能"这个定位。
- **`fast-cooldown-seconds` 是头衔权益的一部分。** 改它等于改头衔的价值。

**是否需要重启：** 不需要，`reload`。

---

## leveling —— 等级曲线

### 这个功能实际做什么

玩家的经验累积到一定程度就升级。曲线是：

```text
当前 L 级升到下一级需要的经验 = base + linear × (L-1) + quadratic × (L-1)²
```

默认 `base: 100`、`linear: 50`、`quadratic: 0`，也就是每级比前一级多要 50 点经验——一条平缓的直线。

**等级的作用：** 解锁 Bag 槽位（默认 10 级开 27 号、25 级开 36 号）。所以经验是有实际价值的进度，不只是数字。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `leveling.base` | 整数 / `100` | 1 级升 2 级需要的经验 |
| `leveling.linear` | 数值 / `50` | 每级的线性增量 |
| `leveling.quadratic` | 数值 / `0` | 每级的平方增量 |
| `leveling.max-level` | 整数 / `10000` | 等级上限 |

### 例子：让后期更陡

```yaml
leveling:
  base: 100
  linear: 50
  quadratic: 0.5
  max-level: 10000
```

**改了哪一行：** `quadratic` 从 `0` 改成 `0.5`。

**为什么这样写：** 加上平方项之后，前期升级速度和默认差不多，但后期会明显变慢——适合"等级是长期目标"的设计。

**代价：** 后期升级会变得很慢。如果你的 Bag 槽位解锁挂在 25 级上，玩家达到 25 级的时间会明显变长。改这条之前先算一下你希望的节奏。

**是否需要重启：** 不需要，`reload`。

**注意：** 改曲线**不会**让玩家掉级或者涨级——它只影响下一次升级需要多少经验。

---

## levelshop —— 头衔

### 这个功能实际做什么

三个头衔预设：Yuki、Momo、Neko。用金币买，买了之后：

- 显示对应的头衔前缀；
- 影响 NameTag 和聊天前缀；
- 附带权益：自动签到、安全聊天颜色、更短的传送冷却、挂机时多一点经验。

**三条设计规则值得知道：**

1. **权益按已拥有的最高档位给。** 玩家买了 Neko 之后换戴 Yuki 的外观，仍然享受 Neko 的传送冷却。随便换戴不会吃亏。
2. **当前装备项只决定外观。** 换来换去不会丢任何东西。
3. **头衔小铺只由管理员打开。** `/nekocore levelshop open <player>`，没有玩家自己的命令。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `levelshop.enabled` | boolean / `true` | 总开关 |
| `levelshop.worlds` | 世界名列表 / `[world]` | 允许打开小铺的世界 |
| `levelshop.permissions.*` | 权限节点名 | 三个权益对应的权限 |
| `levelshop.titles.<id>.name` | 文本 | 显示名 |
| `levelshop.titles.<id>.prefix` | 文本 | 聊天/头顶前缀 |
| `levelshop.titles.<id>.material` | Material | 图标 |
| `levelshop.titles.<id>.slot` | 整数 | 在小铺里的位置 |
| `levelshop.titles.<id>.rank` | 整数 | 档位（决定权益阶梯） |
| `levelshop.titles.<id>.price` | 整数 | 价格 |
| `levelshop.titles.<id>.auto-checkin` | boolean | 是否给自动签到权益 |
| `levelshop.titles.<id>.colored-chat` | boolean | 是否给聊天颜色权益 |
| `levelshop.titles.<id>.tpn-cooldown-seconds` | 整数 | 该档位的传送冷却 |
| `levelshop.titles.<id>.afk-exp-multiplier` | 数值 | 该档位的挂机经验倍率 |
| `levelshop.titles.<id>.description` | 文本列表 | 详情页说明 |

### 例子

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

**改了哪一行：** `price` 从 2500 降到 1500。

**为什么这样写：** 让第一档头衔更容易拿到，给新玩家一个更近的短期目标。

**是否需要重启：** 不需要，`reload`。**已购买的头衔不会被撤销，改价格也不退款。**

**⚠ 不能改的：** `mame`、`momo`、`sora` 这三个 key。它们是稳定头衔 ID，玩家的购买记录挂在上面。

**⚠ 另外两条硬性校验：**

- **必须正好配三个头衔。** 删掉一个或者加一个都会被拒绝，报"请配置三个头衔档位"。
- **`slot` 的取值范围是 0–17**，而且三个头衔的槽位和 `rank` 都不能重复。

**`rank` 要小心改。** 它决定谁是"最高档位"，改它等于重新定义权益阶梯。三个头衔的 `rank` 应该是 1/2/3。

**改 `description` 记得同步。** 详情页里写的"传送冷却 120 秒"是你手写的文字，改了 `tpn-cooldown-seconds` 之后它不会自动更新——玩家看到的就是不一致的信息。

---

## tab / nametag / chat / welcome-title —— 显示层

这一组放在一起讲，因为它们的关系比较微妙。

### 这些功能实际做什么

**TAB** 是玩家按 Tab 键看到的列表。NekoCore 会填上页眉和页脚：

- **页眉：** 服务器名、玩家自己的坐标、TPS（1 分钟 / 5 分钟 / 15 分钟）
- **页脚：** 在线人数、玩家自己的金币、UTC+8 时间、服务器运行时长

**刷新是全服共用一个任务**，数据来自 Paper 和 NekoCore 的内存缓存，**不查数据库**——所以它很轻。

**NameTag** 是玩家头顶的名字。NekoCore 会检查 scoreboard team 有没有被其他插件接管；如果被接管了，它会跳过并给出一条提示，告诉你改用 `%nekocore_display_prefix%` 集成。**它不会抢占其他插件的 team。**

**聊天前缀** 是聊天栏里玩家名字前面的 `[Lv.12]`。如果你的服务器已经有聊天插件显示这个前缀，把它关掉避免重复。

**Join Welcome** 是进服时屏幕中央弹出的标题。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `tab.enabled` | boolean / `true` | 全局 TAB |
| `tab.show-location-prefix` | boolean / `true` | TAB 里是否显示地区 |
| `tab.refresh-seconds` | 整数 / `1` | 刷新间隔 |
| `nametag.enabled` | boolean / `true` | 头顶前缀 |
| `nametag.scoreboard-check-seconds` | 整数 / `5` | 检查 team 归属的间隔 |
| `chat.level-prefix-enabled` | boolean / `true` | 聊天里的等级前缀 |
| `chat.level-prefix` | 文本 | 前缀格式 |
| `welcome-title.enabled` | boolean / `true` | 进服欢迎标题 |
| `welcome-title.delay-ticks` | ticks / `15` | 延迟多久弹出 |
| `welcome-title.fade-in-ticks` | ticks / `10` | 淡入 |
| `welcome-title.stay-ticks` | ticks / `60` | 停留 |
| `welcome-title.fade-out-ticks` | ticks / `10` | 淡出 |

（20 ticks = 1 秒。）

TAB 和欢迎标题的**文字内容**在 `messages.yml` 里：`tab.header`、`tab.footer`、`welcome-title.title`、`welcome-title.subtitle`。

### 例子：关掉 TAB，用别的插件

```yaml
tab:
  enabled: false
  show-location-prefix: false
  refresh-seconds: 1
chat:
  level-prefix-enabled: false
```

**改了哪几行：** TAB 整体关掉，聊天等级前缀也关掉。

**为什么这样写：** 如果你已经有一个很满意的 TAB 插件，那就明确让出所有权。**两个插件同时写 TAB，最后显示什么取决于谁后写——而这个顺序在重启之后可能变化**，表现出来就是"有时候正常有时候不正常"。

**是否需要重启：** 不需要，`reload`。

**如果还想显示 NekoCore 的数据：** 装 PlaceholderAPI，在别的 TAB 插件里用 `%nekocore_coins%`、`%nekocore_level%` 这类占位符。这样 NekoCore 管数据、对方管显示。

### 例子：关掉聊天的等级前缀

```yaml
chat:
  level-prefix-enabled: false
```

**什么时候需要：** 你的聊天插件已经用了 `%nekocore_display_prefix%`。不关的话前缀会出现两次。

**是否需要重启：** 不需要，`reload`。

### NameTag 为什么不显示地区

**这是刻意的。** 地区只在 TAB 和聊天前缀里出现，头顶名字里从来没有。这样即使用户在拥挤的地方，头顶也不会暴露位置信息。

**如果 NameTag 没生效：** 看日志里有没有"该 scoreboard team 已由其他插件管理"的提示。这说明另一个插件（通常是计分板或前缀插件）已经接管了 team，NekoCore 主动让位了。解决办法是用 PlaceholderAPI 集成，而不是强行竞争。

---

## tips / cleanup —— 氛围与维护

### 这两个功能实际做什么

**Tips** 每隔一段时间在聊天栏播报一条提示，循环轮播。默认每 5 分钟一条，末尾之后回到第一条。重载之后从第一条重新计时。

默认的 Tips 内容覆盖了新手需要知道的东西：`/menu` 在哪、床可以当 Home、怎么签到、商店怎么用、`/tpn` 怎么发请求。

**Cleanup** 每隔一段时间清理地面的掉落物，清理前会先给玩家倒计时提醒和提示音，让他们有机会捡起自己的东西。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `tips.enabled` | boolean / `true` | 总开关 |
| `tips.interval-seconds` | 整数 / `300` | 播报间隔 |
| `tips.prefix` | 文本 | 每条 Tips 的前缀 |
| `tips.messages` | 文本列表 | 播报内容，支持 `{server}` |
| `cleanup.enabled` | boolean / `true` | 总开关 |
| `cleanup.interval-seconds` | 整数 / `600` | 清理间隔（最小 61） |
| `cleanup.sound.*` | 声音配置 | 清理前的提示音 |

### 例子

```yaml
tips:
  enabled: true
  interval-seconds: 300
  prefix: '&#9FD9F6tips &f>> '
  messages:
    - '&f欢迎来到 &#9FD9F6{server}&f！输入 &#9FD9F6/menu &f打开服务器面板。'
    - '&f睡床或用 &#9FD9F6/sethome &f记下小窝，输入 &#9FD9F6/home &f即可返回。'
    - '&f输入 &#9FD9F6/checkin &f领取今天的小礼物，慢慢积攒冒险金币。'
```

**改了哪几行：** 把默认的十一条精简成三条。

**为什么这样写：** 十一条 Tips 意味着玩家要等将近一小时才能看完全部。如果你服务器的新手引导很短，三条就够了。

**是否需要重启：** 不需要，`reload`。

**⚠ `tips.enabled: true` 的时候 `messages` 不能是空列表**，否则重载会被拒绝。

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

**改了哪一行：** `interval-seconds` 从 600 改成 300（10 分钟变 5 分钟）。

**什么时候该改：** 如果你的服务器掉落物堆积很快（刷怪塔多、玩家多），缩短间隔有帮助。但**清理太频繁会打断正在整理箱子的玩家**——10 分钟是个比较舒服的位置。

想立刻清理一次用 `/nekocore cleanup now`，不用改间隔。

---

## AFK Pool —— 挂机池

### 这个功能实际做什么

这是最需要在游戏里体验过才看得懂的一个模块，所以先讲行为。

**完整流程：**

1. 玩家从 `/menu` 点"挂机池"入口。
2. 被传送到 `afk-pool.teleport` 指定的位置（水池上方）。
3. **进入配置世界的水体之后**才开始计时——不是落地就开始，是下水才开始。
4. 每隔 `interval-seconds`，玩家收到奖励：固定的 `base-exp` 经验（再乘倍率），加上按概率给的金币。
5. **短暂踩出水不会丢计时。** 如果玩家跳起来换气、或者在 2 秒内回到水里，倒计时继续。
6. **离水超过 `exit-grace-seconds`（默认 2 秒）就重置。**

**一个容易被忽略的条件：计时只在 `afk-pool.world` 那个世界里进行。** 玩家在别的世界泡在水里不会累积任何东西——这是刻意的，避免玩家在自己家的水池里挂机拿奖励。

**为什么要有宽限期这个设计：** 因为"AFK 计时"这个机制如果做得太严格，玩家会觉得自己在跟系统对抗——不小心跳出水面就要从头开始，体验很差。2 秒的宽限让正常的呼吸动作不会被惩罚，但也不至于让玩家在岸上晃悠着刷奖励。

**屏幕上的提示标题** 显示当前的挂机状态。`hide-while-inventory-open: true` 的意思是玩家开着背包的时候把标题藏起来（看得清背包），**但计时不会停止**。

**两把锁：** `enabled` 和 `position-configured` 必须**同时**为 `true`。这个设计的原因是坐标——如果只有一把锁，一个手快的管理员打开功能、坐标还没填，全服玩家点一下就会被送到占位坐标。**两把锁的意思是：NekoCore 永远不会用占位坐标传送任何人。**

> `position-configured` 在新生成的配置里默认是 `false`，所以你必须手动改成 `true`。而旧版本留下的、没有这一行的配置会被当成 `true` 处理，以免静默关掉老服务器已经在用的功能。

**和头衔的关系（这一条容易理解错）：** `reward.normal-multiplier` 和头衔的 `afk-exp-multiplier` **不是相乘关系，而是替换关系**。

```text
玩家没有任何头衔     → 用 reward.normal-multiplier（默认 1.10）
玩家拥有任意头衔     → 用那个头衔的 afk-exp-multiplier，基础倍率被整体替换
```

取的是**已拥有的最高档位**，和当前装备哪个头衔无关。三档默认是 Yuki 1.10、Momo 1.10、Neko 1.20——所以只有买到 Neko 的玩家才真正比无头衔玩家多拿经验。

这个倍率**只作用于挂机池的经验**，不影响签到、任务或其他任何来源。

**经验的小数会累积。** 每次结算时 `基础经验 × 倍率` 的小数部分不会被丢掉，而是留到下一次继续累加。所以 10 × 1.10 = 11 是整数，但用 `base-exp: 1` 这种小数倍率的话，十轮下来会精确得到 11 点而不是被逐次抹平成 10 点。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `afk-pool.enabled` | boolean / `false` | 总开关（第一把锁） |
| `afk-pool.position-configured` | boolean / `false` | 坐标已确认（第二把锁） |
| `afk-pool.world` | 世界名 / `world` | 目标世界 |
| `afk-pool.teleport.x/y/z` | 数值 | 目的坐标 |
| `afk-pool.teleport.yaw/pitch` | 数值 | 到达时的朝向 |
| `afk-pool.exit-grace-seconds` | 整数 / `2` | 离水宽限 |
| `afk-pool.reward.interval-seconds` | 整数 / `300` | 奖励间隔 |
| `afk-pool.reward.base-exp` | 整数 / `10` | 每次的基础经验 |
| `afk-pool.reward.normal-multiplier` | 数值 / `1.10` | 基础倍率 |
| `afk-pool.reward.coin-chance` | 0–1 / `0.45` | 给金币的概率 |
| `afk-pool.reward.coin-min` / `coin-max` | 整数 / `1` / `4` | 金币数量范围 |
| `afk-pool.title.enabled` | boolean / `true` | 屏幕提示标题 |
| `afk-pool.title.hide-while-inventory-open` | boolean / `true` | 开背包时隐藏标题 |
| `afk-pool.title.stay-ticks` | ticks / `40` | 标题停留 |
| `afk-pool.title.fade-out-ticks` | ticks / `10` | 淡出时长 |

### 例子

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

**改了哪几行：** 两把锁都开成 `true`，坐标填成实测值。

**怎么得到真实坐标：** 在游戏里建好水池，站到水池上方的中央位置，按 `F3` 读出精确坐标。`yaw` 和 `pitch` 填 `0.0` 通常就行。

**⚠ 千万别照抄示例坐标。** `120.5 / 65.0 / -30.5` 只是演示用的，抄到你的服务器上就是把玩家送去不知道哪里——可能是虚空，可能是别人家的地基。

**是否需要重启：** 不需要，`reload`。

**看不到入口？** 检查两把锁是不是都开了。菜单的自动布局会隐藏关闭的模块，所以"没有按钮"本身就是插件在告诉你"这个功能现在不可用"。

### 关于奖励节奏

默认 `interval-seconds: 300` 是 5 分钟一次，每次 10 点基础经验 × 1.10 ≈ 11 点，加上 45% 概率给 1–4 金币。

**换算一下：** 挂机一小时大约 132 点经验、约 67 金币。对照签到一天 100 金币——挂机收益是有的，但**不会比正常玩法更划算**。

这个平衡是刻意的。如果挂机比打怪赚钱，玩家就会挂机而不是玩。想调的时候先算一下你的挂机收益和正常玩法的收益比例。

---

## weekly-coin-leaderboard —— 金币周榜

### 这个功能实际做什么

在一个指定的世界坐标生成一块悬浮文字牌，显示**本周金币收入**的前 10 名。

**注意是"本周赚了多少"，不是"现在有多少"。** 一个富有的老玩家如果这周没赚过钱，不会自动霸榜。榜单反映的是"这周谁最活跃"。

**两把锁和挂机池一样**，但行为有个重要的不同：

> `position-configured: false` 的时候，**统计照常进行**，只是不生成显示，并且会记一条 WARNING。

所以你**可以放心地先开着 `enabled: true` 收集数据**，等想好挂在哪儿了再补坐标。这是和挂机池不一样的地方——挂机池两把锁没开就是彻底不工作。

**NekoCore 只管理带自己标记的 TextDisplay。** 它会清理自己创建的，但绝不会去动其他插件的悬浮实体。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `weekly-coin-leaderboard.enabled` | boolean / `false` | 总开关 |
| `weekly-coin-leaderboard.position-configured` | boolean / `false` | 坐标已确认 |
| `weekly-coin-leaderboard.world` | 世界名 / `world` | 目标世界 |
| `weekly-coin-leaderboard.x/y/z` | 数值 | 坐标 |
| `weekly-coin-leaderboard.yaw` | 数值 / `0.0` | 朝向 |
| `weekly-coin-leaderboard.refresh-seconds` | 整数 / `30` | 刷新间隔 |

榜单的**文字内容**在 `messages.yml` 的 `weekly-coins` 里（第一、二、三名有独立的颜色）。

### 例子

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

**改了哪几行：** 两把锁打开，坐标替换成实测值。

**为什么这样写：** 把榜单挂在大厅或者出生点附近，玩家路过就能看到。

**是否需要重启：** 不需要，`reload`。

**看不到牌子？** 按顺序查：世界加载了吗？两把锁都开了吗？坐标那里有空间吗（生成在实心方块里是看不见的）？**本周真的有金币记录吗？**（空榜会显示"本周还没有金币记录"）。

**⚠ 默认绝不会在 `0,0,0` 生成任何东西。** 如果你在 0,0,0 看到了东西，那是别的插件放的。

---

## mascot —— 吉祥物

### 这个功能实际做什么

先澄清最容易误解的一点：

> **Mascot 是 NekoCore 自己内置的模块，不是第三方插件。**
> **Citizens 负责的只是 NPC 实体本身。**

所以流程是：Citizens 提供身体，NekoCore 提供灵魂。

**完整行为：**

1. 一个由管理员在 Citizens 里创建并放置的 NPC 站在那里。
2. 玩家靠近时，NPC 头顶显示几行全息字（由 NekoCore 生成）。
3. 玩家右键点击，NPC "说话"——聊天栏冒出一句从回复池里随机选的台词，同时有粒子飘出来。
4. **如果玩家短时间内狂点**（默认 15 秒窗口内超过 5 次），会切换到"超限回复池"——那些回复的语气是"慢一点啦"，同时换成另一套粒子，并且有 2 秒的对话冷却防止刷屏。

**为什么要有超限机制：** 因为一个会说话的 NPC 一定会被玩家狂点。与其让它刷屏，不如把它做成一个有点性格的反应——**玩家会把它当成角色的脾气，而不是系统的报错**。这也是为什么两套回复池的语气差异值得认真写。

**两套回复池在 `messages.yml` 里：** `mascot.replies` 和 `mascot.over-limit-replies`。

**⚠ NekoCore 不创建、不重命名、不移动、不换皮任何 NPC。** 删除 NPC 也要你在 Citizens 里自己删。这条边界是刻意的：你的 NPC 是你在 Citizens 里的资产，插件不应该替你做主。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `mascot.enabled` | boolean / `false` | 总开关 |
| `mascot.npc-id` | 整数 / `-1` | Citizens NPC 的编号 |
| `mascot.hologram-enabled` | boolean / `true` | 头顶全息字 |
| `mascot.hologram-lines` | 文本列表 | 全息字内容，支持 `{server}` |
| `mascot.hologram-y-offset` | 数值 / `2.85` | 全息字的垂直偏移 |
| `mascot.interaction-window-seconds` | 整数 / `15` | 点击计数的窗口长度 |
| `mascot.normal-click-limit` | 整数 / `5` | 窗口内允许的正常点击数 |
| `mascot.over-limit-chat-cooldown-seconds` | 整数 / `2` | 超限后的对话冷却 |
| `mascot.normal-particle` | 粒子名 | 正常点击的粒子 |
| `mascot.normal-particle-count` | 整数 / `8` | 正常粒子的数量 |
| `mascot.over-limit-particle` | 粒子名 | 超限时的粒子 |
| `mascot.over-limit-particle-count` | 整数 / `18` | 超限粒子的数量 |

### 例子

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

**改了哪几行：** `enabled` 打开，`npc-id` 填成真实编号。

**怎么得到 `npc-id`：** 装好 Citizens 并完整重启之后，用 Citizens 自己的命令创建并放置 NPC，然后站到它旁边执行 `/npc id`。那个数字就是要填的值。

**是否需要重启：** **需要完整重启**——Citizens 是新增插件，reload 覆盖不到。

**`hologram-y-offset` 怎么调：** 全息字相对 NPC 头顶往上偏移多少格。默认 2.85 适合大多数玩家模型的 NPC。如果你的 NPC 特别高或者特别矮，微调这个值。

**旧配置的兼容：** 旧版本用的键是 `citizens-npc-id`，仍然被兼容读取。新配置请用 `npc-id`。

---

## location-prefix —— 网络地区（GeoIP）

### 这个功能实际做什么

把玩家 IP 解析成粗粒度的地理位置，显示在 TAB 和聊天前缀里。

**关键的隐私设计：**

- **不把完整 IP 写进数据库。** 数据库里根本没有存 IP 的字段。
- **不向聊天或 TAB 显示完整 IP。** 只显示省份或国家。
- **NameTag 不显示地区**，这一项不需要额外关。
- **会话缓存只缓存解析结果**，缓存的也是粗粒度地区而不是 IP，停服就消失。

**玩家自己的控制权：** `/menu` 里有个"显示网络地区"开关。玩家关掉之后自己的地区就不显示。这是**独立于**全局 `tab.show-location-prefix` 的第二层控制——两个都开才会显示。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `location-prefix.enabled` | boolean / `false` | 总开关 |
| `location-prefix.database-file` | 文本 / `GeoLite2-City.mmdb` | 文件名，相对 `plugins/NekoCore/` |
| `location-prefix.cache-session` | boolean / `true` | 会话级缓存。**设成 `false` 会导致所有玩家都不显示地区** |
| `location-prefix.show-china-province` | boolean / `true` | 中国玩家显示到省份 |
| `location-prefix.show-foreign-country` | boolean / `true` | 外国玩家显示到国家 |
| `location-prefix.hide-unknown` | boolean / `true` | 保留字段，当前版本无实际作用 |

**⚠ `cache-session` 不要改成 `false`。** 它不会变成"每次实时查询"，而是让解析结果完全不写缓存，而下游读的就是缓存——结果是全服都不显示地区。详见 [GeoIP 文档](GEOIP.md)。

### 例子

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

**改了哪几行：** 打开总开关，确认文件名。

**是否需要重启：不需要完整重启。** `/nekocore reload` 会重新打开 MMDB 文件并清空会话缓存，所以新放进 `plugins/NekoCore/` 的数据文件在重载后就会开始工作。**但光把文件拷进去、不做任何重载或重启是不会自动生效的**——这一条最容易被忽略。改了 `database-file` 文件名也一样，reload 能覆盖。

**⚠ JAR 里没有任何 MMDB 文件。** 你必须自己从 [MaxMind 官方](https://dev.maxmind.com/geoip/geolite2-free-geolocation-data/)取得 City 版数据库，放到 `plugins/NekoCore/`。而且——**下载到的是 `.tar.gz`，要先解压出里面的 `.mmdb`**，直接改压缩包的名字是读不出来的。

**缺失或损坏时的表现：** 只会禁用 GeoIP 这一个功能，其他一切照常。默认**不会**刷一大段异常堆栈——想看详细错误要临时打开 `advanced.debug: true`。

完整的安装流程、隐私模型和排查步骤见 [GeoIP 文档](GEOIP.md)。

---

## holograms —— 全息字全局设置

### 这个功能实际做什么

控制 NekoCore 生成的全息字（TextDisplay）的外观。目前 Mascot 的头顶文字和金币周榜都用它。

**重要边界：NekoCore 只管理带自己标记的 TextDisplay。** 它会清理自己创建的，但绝不会去动其他插件的悬浮实体。

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `holograms.line-width` | 整数 / `240` | 文字行的最大宽度 |
| `holograms.shadowed` | boolean / `true` | 是否带阴影 |

### 例子

```yaml
holograms:
  line-width: 240
  shadowed: true
```

**什么时候改：** 如果你的全息字排得很难看（文字挤在一起或者换行奇怪），调 `line-width` 试试。`shadowed: false` 会让文字看起来更"平"，在某些背景下更清晰。

**是否需要重启：** 不需要，`reload`。

---

## advanced —— 排障开关

### 字段

| 字段 | 类型 / 默认 | 说明 |
| --- | --- | --- |
| `advanced.debug` | boolean / `false` | 让可选组件输出详细异常堆栈 |

### 例子

```yaml
advanced:
  debug: true
```

**什么时候用：** 只在排查问题的时候。平时保持 `false`。

**为什么默认关着：** 因为很多"功能没生效"的情况其实是**正常的安全降级**——缺 MMDB 就禁用 GeoIP，缺 Citizens 就禁用 Mascot。如果这些情况都刷一大段堆栈，日志会被淹没，真正的错误反而看不见。

打开 `debug` 之后这些组件会输出完整的异常信息，方便你定位是文件缺失、格式错误还是别的原因。

**⚠ 排查完记得关回去。**

**是否需要重启：** 不需要，`reload`。

---

## 速查：哪些改动需要重启

| 改动类型 | 生效方式 |
| --- | --- |
| 名字、数值、开关、文字 | `/nekocore reload` |
| 世界名、坐标、Material | `/nekocore reload` |
| `database.filename` | **完整重启** |
| 更换 JAR | **完整重启** |
| 加入或更换 MMDB 文件 | `/nekocore reload`（它会重新打开该文件） |
| 安装新的第三方插件 | **完整重启** |
| 世界插件的变更 | **完整重启** |

**不确定的时候用 `/nekocore config check`。** 它只验证不应用，跑一下就知道配置本身有没有问题。

**重载失败不是灾难。** 服务器会继续用旧的有效配置跑着，玩家完全感知不到。读第一条报错（里面有 YAML 路径和期望的类型），改完再试。

---

## 相关文档

- **按目标找改法** → [配方手册](RECIPES.md)
- **第一次开服的手把手教程** → [快速开始](QUICKSTART.md)
- **经济数值怎么调** → [经济文档](ECONOMY.md)
- **20 个每日任务的玩法说明** → [每日任务文档](DAILY-TASKS.md)
- **GeoIP 的合规与隐私** → [GeoIP 文档](GEOIP.md)
- **某个模块没生效** → [兼容性与常见问题](COMPATIBILITY.md)
