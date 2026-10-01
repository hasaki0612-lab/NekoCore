# NekoCore Public 1.0.0

[简体中文](README.md) | [English](README.en.md)

> 面向小型 Paper 服务器的开箱即用基础核心。
> 会装 Paper、会改 YAML，大部分事情就不需要再碰 Java。

---

## 这个项目是怎么来的

NekoCore 一开始只是为了让自己的服务器少装一点零散插件。

资料要一个插件，经济要一个插件，菜单要一个插件，Home 要一个插件，签到、商店、TAB、温馨提示又各自一个……装到后来，`plugins/` 里躺着一排 JAR，谁和谁抢 TAB 也说不清楚，配置文件散在七八个文件夹里，玩家数据还被拆成了好几份。

于是这些东西慢慢被收拢进了一个插件里：一套玩家资料、一套经济、一套菜单，配置读同一个目录，数据存同一个数据库。这就是你看到的 NekoCore。

它现在的样子，是一套**可以直接拿来开小服务器的核心**。主世界叫什么、服务器叫什么名字、商店卖多少钱、任务奖励给多少、要不要开挂机池——这些都在 `config.yml` 和 `messages.yml` 里。凡是需要猜的东西（别人的世界、别人的坐标、别人的 GPS 数据），默认一律关着，等你自己的服务器确定了再打开。

---

## 装好以后，玩家会看到什么

先说说"装完默认能玩"具体是什么感觉。下面是玩家视角的描述，不是功能清单。

**进服的那一瞬间。** 屏幕中央浮出一行欢迎标题，写着玩家的名字和你服务器的名字。TAB 列表被填好：上半部分是服务器名、自己的坐标、当前 TPS；下半部分是在线人数、自己的金币、UTC+8 时间和服务器的运行时长。聊天栏里每隔几分钟会有一句 tips，告诉新玩家 `/menu` 在哪、怎么签到、床可以当 Home 用。

**打开 `/menu`。** 一个五行的服务器面板。玩家能看到自己的资料卡（等级、经验、金币、游玩时长、首次加入时间）、每日任务、每日签到、一个"显示网络地区"的隐私开关，以及几个前往其他区域的入口。没有开启的模块不会留下空格——按钮会自动重新居中，玩家看到的就是完整的。

**签到。** 每天一次，按自然日算，不是"每隔 24 小时"。今天没领的话，进服会收到一句提醒。领到的时候有一点金币、一点经验，还有几颗白色的小星星从身上飘起来。这个服务器的时区由你配置，默认是北京时间。

**每日任务。** 每天从简单、普通、困难三个池子里各抽三个，全服共用同一套九个任务，但每个人的进度和奖励是自己的。任务不是"击杀 100 只怪"这种压迫感很强的东西，更像"今天也给羊剪个毛吧""累计在线 20 分钟""做一块蛋糕"。完成了会在聊天栏收到一句有点开心的提示。

**商店。** 八个分类、109 件起步商品：种子、食物、铁装钻石装、工具、红石、建材、冒险用品，还有每日轮换的附魔书。右键买入、左键出售，数量可以从预设里挑，也可以在聊天框里自己填一个数。每天有购买和出售限额，防止有人把刷怪塔直接换成金币。

**Home 和床。** `/sethome` 按世界分别记，所以主世界一个、下界一个、末地一个都可以。成功躺上床的时候，插件会顺手在床边帮你记一个 Home——如果床边正好有能站的地方的话。死亡时聊天栏会告诉你死在哪个坐标，方便跑尸。

**Bag。** 一个跟着玩家走的随身仓库，`/bag` 打开。默认只在主世界允许取放，其他世界（包括大厅，以及你以后新加的世界）都是只读——这样它不会和独立背包规则打架。槽位会随等级慢慢解锁。

**TPN。** `/tpn <玩家名>` 发出一个传送请求，对方用 `/yes` 或 `/no` 回应。不再是"点一下就瞬移过去"，被传的人有决定权。请求会超时，发起方也有冷却时间。

**头衔。** Yuki、Momo、Neko 三档，用金币买。买了以后会显示头衔前缀、NameTag 和聊天前缀，还附带一点小权益：自动签到、安全聊天颜色、更短的传送冷却、挂机时多一点经验。已拥有的头衔只决定外观，权益按你买过的最高档位算，所以随便换戴不会吃亏。

**以下这些默认是关着的**，因为 NekoCore 不想替你猜：网络地区显示（GeoIP）、第二生存世界、小游戏区、挂机池、金币周榜、Citizens 吉祥物。它们的菜单入口会跟着消失，不会出现"点了没反应"的按钮。

---

## 为什么"默认能玩"和"默认不乱猜"要同时存在

这是这个项目里最花心思的一部分，值得单独说一句。

NekoCore 对所有涉及**外部事实**的功能都加了门闩。所谓外部事实，就是插件自己不可能知道、只能由你告诉它的东西：

- 你的第二生存世界叫什么、有没有建好；
- 挂机池到底在哪一格水里；
- 金币周榜的牌子挂在墙上哪个位置；
- 你从 MaxMind 下载的 GeoLite2 数据库放在哪；
- 你在 Citizens 里创建的那个 NPC 编号是几。

这些东西插件一旦猜错，后果不是"功能没生效"，而是"把玩家传送到虚空"或者"在 0,0,0 生成一个悬浮牌子"。所以设计上是：**两把锁都打开才动作**。比如挂机池需要 `enabled: true` 和 `position-configured: true` 同时成立；周榜需要 `enabled: true` 和 `position-configured: true` 同时成立。只开一把锁，插件会安静地什么都不做，而不是找个默认坐标凑合。

反过来，凡是不依赖外部事实的功能——资料、经济、等级、菜单、Home、签到、商店、Bag、TPN、每日任务、Tips、清理、TAB、欢迎标题——默认全部打开。你装完 JAR 第一次启动，就已经有一个能玩的小服务器了。

---

## 谁适合用

- **第一次开服的人。** 你不需要会 Java，甚至不需要完全看懂 `config.yml`——先按快速开始走一遍，能跑起来之后再回来慢慢研究。
- **朋友服、小社区服。** 十来个到几十个人，不想维护七八个插件，也不想为了一句话的提示去写脚本。
- **想减少基础插件数量的管理员。** 如果你现在装了 Essentials 系的 Home + 一个签到插件 + 一个菜单插件 + 一个商店插件 + 一个 TAB 插件，NekoCore 大体上想把它们合并成一个。
- **在意数据安全的人。** 商店和 Bag 的操作走数据库事务，带恢复记录；崩溃窗口里的复制和丢失是被认真处理过的，不是"祈祷不要出事"。

## 谁可能不适合用

这一节也想认真写，因为用错工具比没有工具更浪费时间。

- **需要 Folia 或跨版本支持的服务器。** NekoCore 的目标平台是 Paper 26.2 + Java 25，没有声明 Folia 支持，也没有为旧版本做兼容层。
- **大型多服网络。** 它是为单个小服务器设计的。跨服同步、BungeeCord 全局经济、多节点共享数据库这些不在设计目标里。
- **想深度改造玩法的人。** NekoCore 不是开发框架，它是一个成品核心。它能配置的东西很多，但没有给你一套 API 去写自己的玩法模块。
- **已经有一套成熟生态的服务器。** 如果你现在的 Essentials + Vault + 一系列专用插件跑得很稳、玩家也习惯了，那么 NekoCore 带来的更多是迁移成本，而不是收益。

---

## 五分钟开始

1. 安装 [Java 25](https://docs.oracle.com/en/java/javase/25/install/)，下载 [Paper 26.2](https://papermc.io/downloads/paper/)。
2. 先启动一次 Paper，按提示在 `eula.txt` 里接受 Mojang EULA，再正常 `stop`。
3. 把 `NekoCore-1.0.0.jar` 放进服务器的 `plugins/` 文件夹，启动。日志末尾应该出现 `NekoCore 1.0.0 ready`。
4. 停服。打开 `plugins/NekoCore/config.yml`，至少改两个地方：`branding.server-name` 改成你的服务器名；如果主世界文件夹不叫 `world`，把 `survival.world` 也改掉。
5. 重启，进游戏打 `/menu`。管理员可以再试试 `/nekocore status` 和 `/nekocore config check`。

第一次开服的话，[快速开始](docs/zh-CN/QUICKSTART.md)写得更细——包括 `plugins` 文件夹在哪、YAML 缩进为什么不能乱、什么时候该停服、怎么确认插件真的加载成功了。建议从那里看。

> 顺带一提：升级插件、换数据库、加世界插件的时候请正常停服重启。Bukkit 的 `/reload` 和热卸载插件不会让 NekoCore 变得更好，只会让它变得难以预测。

---

## 配置大概长什么样

NekoCore 只有两个配置文件，都在 `plugins/NekoCore/`：

- **`config.yml`** —— 服务器行为。世界观、经济数值、菜单布局、模块开关。
- **`messages.yml`** —— 所有玩家能看到的文字。带 `{placeholder}` 的地方可以随意改。

改服务器名字：

```yaml
branding:
  server-name: "猫猫服"
```

所有玩家可见的地方（TAB、欢迎标题、GUI 标题、Tips、吉祥物全息字、每日任务里的"早安！{server}！"）都会跟着变。

改签到奖励：

```yaml
checkin:
  coins: 100
  exp: 50
```

改完不用急着重启。`/nekocore config check` 会告诉你配置有没有问题，但**不会**应用任何改动；确认没问题了再用 `/nekocore reload` 真正切换。重载是原子的——所有文件都解析、验证通过之后才会整体替换。如果某个字段写错了，服务器会继续用上一份有效配置跑着，同时在控制台告诉你错在哪一行、期望什么类型。改坏配置不会让你的服务器下线。

字段太多不知道从哪看起？[配置手册](docs/zh-CN/CONFIGURATION.md)按模块解释每个功能实际怎么工作，[配方手册](docs/zh-CN/RECIPES.md)则是"我想……"的懒人做法。

---

## 数据存在哪里

运行数据都在 `plugins/NekoCore/nekocore.db`，一个 SQLite 数据库。里面装着玩家资料、金币与经验、Home 坐标、签到记录、头衔、Bag 内容、商店额度、交易日志、每日任务进度和周金币统计。

备份的时候注意三件事：

1. **先停服**，等 Java 进程真的退出，再复制文件。
2. 如果目录里同时有 `nekocore.db-wal` 和 `nekocore.db-shm`，**一起复制**。只复制 `.db` 可能拿到一个不完整的时点。
3. 如果你的服务器在用独立背包（比如 Multiverse-Inventories），Bag 和商店涉及的物品数据要和数据库是**同一个时点**的备份，否则恢复之后会出现对不上的情况。

不要在服务器运行时用 SQLite 编辑器手工改表。看起来很快，但它是把交易恢复逻辑的假设直接踩碎。[数据库文档](docs/zh-CN/DATABASE.md)里有完整的备份与恢复流程。

---

## 出问题先去哪看

按这个顺序排查，通常三步之内就能定位：

1. **看控制台启动日志。** 正常启动会有一行 `NekoCore 1.0.0 ready`，前面是简短的 schema、核心模块和可选模块摘要。默认不刷大堆栈——只有你想深挖时才把 `advanced.debug` 打开。
2. **`/nekocore status`**（或同义的 `/nekocore doctor`）。它会列出 NekoCore、Paper、Java 的版本，数据库 schema，以及 Store / Bag / Tasks / GeoIP / Mascot / AFK / Leaderboard / PlaceholderAPI 各自的状态。九成"某个功能没生效"的问题，答案就在这一屏。
3. **`/nekocore config check`**。如果你改过 YAML，先确认配置本身是不是合法的。

模块"没反应"的常见原因其实很集中：菜单里没有入口（模块关着，或者它依赖的世界没加载）、两把锁只开了一把、或者另一个插件也在写同一块界面。[兼容性与常见问题](docs/zh-CN/COMPATIBILITY.md)把这些问题写成了问答，逐个给了排查顺序。

---

## 环境要求与依赖

服务器这边只有两个硬要求：

| 要求 | 版本 |
| --- | --- |
| [Paper](https://papermc.io/downloads/paper/) | 26.2（API build 129 stable） |
| [Java](https://docs.oracle.com/en/java/javase/25/install/) | 25 |

SQLite JDBC 和 GeoIP 读取库已经打进 JAR 了，你不需要额外下载。

可选的第三方组件只有在你需要对应功能时才装：

- **PlaceholderAPI** —— 想让别的插件读 NekoCore 的数据（比如在别的 TAB 插件里显示 `%nekocore_coins%`）时安装。
- **Citizens** —— 只有要开内置的吉祥物互动模块才需要。
- **Multiverse-Core** —— 想用 `mvtp {player} {world}` 这种命令来管理世界跳转时安装。不装的话，已加载的主世界会安全地回退到 Paper 的出生点。
- **GeoLite2 City 数据文件** —— 只有要开网络地区显示才需要，而且要你自己去 MaxMind 按他们的条款取得。JAR 和源码仓库里**没有**任何 MMDB 文件。

WorldGuard 不是 NekoCore 的依赖。它可以和 NekoCore 共存，但不在这套自动测试的保证范围内。

每个组件的用途、官方下载地址、测试版本和安装步骤写在[依赖说明](docs/zh-CN/DEPENDENCIES.md)里。那里也澄清了一个容易搞混的地方：**Mascot 是 NekoCore 自己内置的模块，不是第三方插件；Citizens 负责的只是 NPC 实体本身。**

---

## 文档

第一次接触的话，建议按这个顺序读：快速开始 → 配方 → 配置手册。

| 简体中文 | English | 内容 |
| --- | --- | --- |
| [快速开始](docs/zh-CN/QUICKSTART.md) | [Quick start](docs/en/QUICKSTART.md) | 手把手第一次开服，含检查清单 |
| [部署命令手册](docs/zh-CN/DEPLOY.md) | [Deployment commands](docs/en/DEPLOY.md) | **可直接复制的完整命令**：scp 上传、ssh 登录、screen/systemd、备份、排错 |
| [安装与升级](docs/zh-CN/INSTALLATION.md) | [Installation](docs/en/INSTALLATION.md) | 全新安装、从 1.4.0 升级、回滚、卸载 |
| [配置手册](docs/zh-CN/CONFIGURATION.md) | [Configuration](docs/en/CONFIGURATION.md) | 每个模块做什么，字段怎么填 |
| [配方手册](docs/zh-CN/RECIPES.md) | [Recipes](docs/en/RECIPES.md) | “我想……”，最短结论加 YAML |
| [依赖说明](docs/zh-CN/DEPENDENCIES.md) | [Dependencies](docs/en/DEPENDENCIES.md) | 每个第三方组件的用途与官方来源 |
| [命令与权限](docs/zh-CN/COMMANDS.md) | [Commands](docs/en/COMMANDS.md) | 玩家命令、管理命令、权限节点 |
| [经济与商店](docs/zh-CN/ECONOMY.md) | [Economy](docs/en/ECONOMY.md) | 金币怎么来怎么去，商店怎么定价 |
| [每日任务](docs/zh-CN/DAILY-TASKS.md) | [Daily tasks](docs/en/DAILY-TASKS.md) | 20 个任务分别怎么完成 |
| [GeoIP](docs/zh-CN/GEOIP.md) | [GeoIP](docs/en/GEOIP.md) | 合法安装地区数据与隐私模型 |
| [数据库](docs/zh-CN/DATABASE.md) | [Database](docs/en/DATABASE.md) | SQLite、迁移、备份与恢复 |
| [兼容性与常见问题](docs/zh-CN/COMPATIBILITY.md) | [Compatibility](docs/en/COMPATIBILITY.md) | “为什么没生效”逐条排查 |

另见 [CHANGELOG](CHANGELOG.md)、[贡献指南](CONTRIBUTING.md)、[安全策略](SECURITY.md)、[Credits](CREDITS.md)、[第三方组件](THIRD-PARTY.md)与[发布验证](VERIFICATION.md)。

---

## 从源码构建

需要 JDK 25 和 Maven 3.9+，在项目根目录：

```text
mvn -B clean verify
```

成品是 `target/NekoCore-1.0.0.jar`。

打包过程会用 shade 处理依赖，因此 `target/` 里还会留下一个 `original-NekoCore-1.0.0.jar`——那个是 shade 之前的原始产物，**不要发布它**。GitHub Actions 上的 CI 在 push 和 pull request 时执行同一条命令。

---

## Credits 与 License

项目所有者与维护者：**秋山羽咲**。
开发与设计协助：**GPT-5.6 Sol (OpenAI)** 与 **DeepSeek-V41-Flash (深度求索)**。

这一条只是如实记录协助来源，不表示 OpenAI 或深度求索维护、发布或背书这个项目。

本项目以 **MIT 许可证**发布，全文见 [LICENSE](LICENSE)。意思是你可以自由地使用、修改、分发这份代码，甚至用于闭源项目——只要保留版权声明。第三方组件的许可证仍然各自独立，不能代替本项目的许可证。
