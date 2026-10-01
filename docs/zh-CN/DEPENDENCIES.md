# 依赖说明：哪些东西必须装，哪些不装也行

[English](../en/DEPENDENCIES.md) | [返回 README](../../README.md)

先解决一个最常见的误会。

打开 `pom.xml`，你会看到一串库的名字。但**"出现在 pom.xml 里"和"服主必须安装它"完全是两件事。** 编译期需要的东西、打包进 JAR 的东西、运行时可选的东西，是三类截然不同的存在。这一页就是把它们分清楚，顺手给每个真正需要你动手的组件一份下载地址。

---

## 一张表看完

| 分类 | 项目 | 它是干嘛的 | 目标/测试版本 | 你要做什么 |
| --- | --- | --- | --- | --- |
| **核心要求** | Paper | 服务器的运行平台和 API | 26.2（API build 129 stable） | **必须装** |
| **核心要求** | Java | 跑 Paper 的虚拟机 | 25 | **必须装** |
| 已打包 | SQLite JDBC | 读写数据库 | 3.50.3.0 | 什么都不用做 |
| 已打包 | MaxMind GeoIP2 reader | 读取可选的 City 数据库 | 5.2.0 | 不用装库；**数据文件要自己取得** |
| 可选集成 | PlaceholderAPI | 让别的插件读 NekoCore 的数据 | 编译测试 2.11.6 | 需要 `%nekocore_*%` 时 |
| 特定功能依赖 | Citizens | 提供吉祥物的 NPC 实体 | 需与 Paper 26.2 兼容，建议实机确认 | 只有开 Mascot 时 |
| 可选 / 特定功能 | Multiverse-Core | 按配置的命令跳转世界 | 5.8.x（当前页面可见 5.8.1） | 只有要用 `mvtp` 路由时 |
| 仅构建与测试 | Maven / JUnit / Mockito | 编译和跑测试 | Maven 3.9+ / 5.13.4 / 5.23.0 | 服务器上**不装** |

**WorldGuard 与 Multiverse-Inventories 不是 NekoCore 的依赖。** 它们可以和 NekoCore 共存，但共存场景不在自动测试的保证范围内（见 [VERIFICATION](../../VERIFICATION.md)）。**Folia 未声明支持。**

> 一句话总结：只有 **Paper** 和 **Java** 是你必须准备的。其余全部按需。

---

## Paper

### 它是干嘛的？

Paper 是服务器软件本身。NekoCore 是一个插件，插件不能独立运行——它需要宿主。NekoCore 用的是 Paper 的 API，所以你不能把它放到 Spigot、CraftBukkit、Forge 或者原版服务端上。

### 你什么时候需要它？

**总是需要。** 这是唯一的硬性平台要求。

### 目标版本

Paper **26.2**，对应 API build `129-stable`。这是开发、编译和测试时使用的版本。

### 下载与安装

- 下载：[papermc.io/downloads/paper](https://papermc.io/downloads/paper/)
- 入门教程：[Paper 官方文档 · Getting Started](https://docs.papermc.io/paper/admin/getting-started/)
- API 参考：[Paper 26.2 Javadoc](https://jd.papermc.io/paper/26.2/)

安装步骤就是[快速开始](QUICKSTART.md)里写的那套：下 JAR → 放文件夹 → 启动一次 → 改 `eula.txt` → 再启动。这里不重复。

### ⚠ 关于其他服务端

- **Spigot / CraftBukkit** —— 没测试过，不声明支持。
- **Folia** —— 没测试过，不声明支持。NekoCore 有一些定时任务和主线程假设，迁移到 Folia 不是"改个版本号"的事。
- **未来的 Paper 版本** —— 大概率能用，但没测试过。升级 Paper 大版本之前建议先在测试服验证。

---

## Java

### 它是干嘛的？

Java 虚拟机。Paper 是 Java 程序，没有 JVM 就跑不起来。

### 你什么时候需要它？

**总是需要。** 版本必须是 **25**。

### 版本为什么这么严格？

因为 Paper 26.2 要求 Java 25。用旧版本 Java 启动会直接报错退出，通常提示 `Unsupported class file major version` 之类。这不是 NekoCore 的要求，是平台的要求。

### 下载与安装

- [Paper 的 Java 安装指南](https://docs.papermc.io/paper/misc/java-install/)（推荐先看这个，它按操作系统分好了）
- [Oracle JDK 25 安装文档](https://docs.oracle.com/en/java/javase/25/install/)

装完记得验证：

```text
java -version
```

第一行要能看到 25。

### 什么时候完全不用操心这件事？

如果你用的是**托管服务器面板**（比如很多国内面板和 Pterodactyl 类的服务），服务商通常让你在下拉菜单里选 Java 版本。选 25 就行，不用自己装。

---

## PlaceholderAPI

### 它是干嘛的？

一个让插件之间互相"读数据"的中间层。装上它之后，NekoCore 会注册一批占位符，你就能在**别的插件**里显示 NekoCore 的数据。

比如：你的服务器已经有一个很喜欢的 TAB 插件，不想换成 NekoCore 的 TAB——那就装 PlaceholderAPI，然后在那个 TAB 插件里写 `%nekocore_coins%`，它就会显示玩家的金币。

### 你什么时候需要它？

- 你想在别的插件（TAB、计分板、聊天、HolographicDisplays 等）里显示 NekoCore 的金币或等级；
- 你想用别的 TAB 插件，但仍然想显示 NekoCore 的数据。

### 什么时候完全不用装？

如果你就用 NekoCore 自带的 TAB 和聊天前缀，那不需要它。**缺失 PlaceholderAPI 不会影响任何核心功能**，只是跳过这部分集成。

### 版本说明

编译和测试时用的是 API **2.11.6**。新部署时建议直接使用官方当前发布的兼容版本，不必刻意对齐这个数字。

### 下载与安装

- [GitHub Releases](https://github.com/PlaceholderAPI/PlaceholderAPI/releases)
- [Spigot 官方资源页](https://www.spigotmc.org/resources/placeholderapi.6245/)
- [官方 Wiki](https://wiki.placeholderapi.com/)

把 JAR 放进 `plugins/`，**完整重启**。装好之后 `/nekocore status` 会显示 PlaceholderAPI 为可用状态。

### NekoCore 的配置

NekoCore 这边没有需要改的开关——检测到 PlaceholderAPI 就自动注册 expansion，没检测到就跳过。

### 可用的占位符

装好之后，identifier 是 `nekocore`，也就是写作 `%nekocore_<名字>%`：

| 占位符 | 内容 |
| --- | --- |
| `%nekocore_coins%` | 金币余额 |
| `%nekocore_level%` | 等级 |
| `%nekocore_exp%` | 当前等级内已获得的经验 |
| `%nekocore_exp_needed%` | 升到下一级还需要的经验 |
| `%nekocore_playtime%` | 格式化后的游玩时长 |
| `%nekocore_level_prefix%` | 等级前缀模板（如 `[Lv.12]`） |
| `%nekocore_title%` | 当前装备头衔的名字 |
| `%nekocore_title_prefix%` | 当前装备头衔的前缀 |
| `%nekocore_display_prefix%` | **当前实际生效的前缀**（头衔前缀或等级前缀） |
| `%nekocore_location%` | 粗粒度地区（如 `广东`） |
| `%nekocore_location_prefix%` | 带方括号的地区前缀 |

`%nekocore_display_prefix%` 是最常用的一个——**它就是你该用在别的聊天或头顶插件里的那个**。当 NekoCore 检测到某个玩家的 scoreboard team 已经被别的插件接管时，它会在日志里提示你用这个占位符来做集成，而不是去抢 team 的所有权。

**常见坑：** PlaceholderAPI 的 expansion 有时需要用 `/papi ecloud download` 安装。NekoCore 的 expansion 是内置在 NekoCore 自己里面的，不需要额外下载。但如果你在别的插件里写 `%nekocore_coins%` 却显示成原文，用 `/papi list` 确认 expansion 有没有被识别出来。

---

## Citizens

### 它是干嘛的？

Citizens 是 Minecraft 服务器上最常用的 **NPC 插件**。它负责创建、放置、管理那些像玩家一样站在世界里的实体——包括它们的外观、皮肤、名字、寻路和行为。

### ⚠ 先澄清一个常见的误会

**Mascot 不是 Citizens 的替代品，也不是另一个第三方插件。**

| | 负责什么 |
| --- | --- |
| **Citizens** | 那个站在那里的**身体**——实体、皮肤、位置、朝向 |
| **NekoCore 的 Mascot 模块** | 那个身体的**互动行为**——右键说话、粒子、全息字、点击频率限制 |

所以正确的理解是：**Mascot 是 NekoCore 内置的一个模块**，它的运行需要一个由 Citizens 创建的 NPC 作为载体。你在 NekoCore 的配置里找不到"安装 Mascot 插件"这一步，因为它本来就在插件里。

### 你什么时候需要它？

只有你想让某个 NPC 会说话、会冒粒子、头上有全息字的时候。

### 什么时候完全不用装？

绝大多数服务器都不需要。不用 Mascot 的话，Citizens 完全不装也没关系——`mascot.enabled` 默认就是 `false`。

### 版本说明

Citizens 需要与 Paper 26.2 兼容的构建。**这一点建议你实机确认**——Citizens 的更新节奏和 Paper 不完全同步，遇到不兼容时 NekoCore 只会安静地禁用 Mascot 模块并可能记一条 warning，不会影响其他功能。

### 下载与安装

- [GitHub 仓库](https://github.com/CitizensDev/Citizens2)
- [官方下载说明](https://wiki.citizensnpcs.co/Downloads)
- [版本说明](https://wiki.citizensnpcs.co/Versions)

把 JAR 放进 `plugins/`，**完整重启**。

### NekoCore 的配置

完整流程见[配方手册的 Mascot 一节](RECIPES.md#我想加一个-citizens-吉祥物)，这里给个提纲：

1. 重启后，用 Citizens 自己的命令创建并放置 NPC。外观、名字、皮肤都由你决定。
2. 站到 NPC 旁边，用 `/npc id` 读出编号。
3. 把编号填进 `config.yml`：

```yaml
mascot:
  enabled: true
  npc-id: 3
```

4. 想改台词就编辑 `messages.yml` 里的 `mascot.replies` 和 `mascot.over-limit-replies`。
5. **完整重启**（Citizens 是新插件，reload 不够）。

### 一条重要的边界

**NekoCore 不创建、不重命名、不移动、不换皮任何 NPC。** 它只是"给一个已经存在的 NPC 加上互动能力"。停用 Mascot 也不会删除 NPC；要删 NPC 请在 Citizens 里自己删。

这条边界是刻意的。你的 NPC 是你在 Citizens 里的资产，插件不应该替你做主。

---

## Multiverse-Core

### 它是干嘛的？

多世界管理插件。负责创建世界、加载世界，以及提供跨世界传送的命令。

### 你什么时候需要它？

当你想让 NekoCore 用**命令**来跳转世界的时候。

具体来说，`config.yml` 里有这么一行：

```yaml
survival:
  command: 'mvtp {player} {world}'
```

`mvtp` 是 Multiverse 的命令。装了 Multiverse，NekoCore 就会执行这条命令来把玩家送过去。

### 什么时候完全不用装？

**只有一个世界的话，完全不用装。**

没装 Multiverse 的时候，NekoCore 的**已加载主世界入口会安全地回退到 Paper 的世界出生点**。也就是说，`/menu` 里的"生存一区"照样能用，只是走的是 Paper 原生的传送方式，而不是 `mvtp`。

如果你的服务器有第二、第三个世界，那些入口就需要命令路由了——这种情况下建议装 Multiverse。

### 版本说明

Multiverse-Core **5.8.x**（官方页面当前可见 5.8.1）。

### 下载与安装

- [Hangar 页面](https://hangar.papermc.io/Multiverse/Multiverse-Core)
- [版本列表](https://hangar.papermc.io/Multiverse/Multiverse-Core/versions)
- [官方安装文档](https://mvplugins.org/core/fundamentals/installation/)

把 JAR 放进 `plugins/`，**完整重启**。然后用 Multiverse 的命令创建或导入世界。

### NekoCore 的配置

```yaml
survival:
  enabled: true
  world: survival
  command: 'mvtp {player} {world}'
survival-new:
  enabled: true
  world: world_secondary
  command: 'mvtp {player} {world}'
```

`command` 里**必须同时包含 `{player}` 和 `{world}` 两个占位符**，否则重载会被拒绝。不要带开头的 `/`——配置里写的是命令本身，不是聊天栏里输入的东西。

### 两条边界

1. **NekoCore 不创建世界。** 世界得由 Multiverse（或者其他世界插件）先建好、加载好。世界没加载的时候，对应的菜单入口会安静地消失。
2. **NekoCore 不接管死亡重生。** 每个世界的出生点和重生点规则归你的世界插件或 Lobby 插件管。

---

## GeoLite2 City 数据文件

### 它是干嘛的？

一个 IP 到地理位置的映射数据库。NekoCore 用它把玩家 IP 解析成"广东"或者"Japan"这样的粗粒度地区，显示在 TAB 里。

### ⚠ 它不是插件

**GeoLite2 不是一个插件 JAR，而是一个数据文件（`.mmdb`）。** 它不放进 `plugins/` 当插件加载，而是放进 `plugins/NekoCore/` 当数据文件读取。这一点经常被搞混。

而且，**NekoCore 的 JAR、源码仓库和 Release 里不包含任何 MMDB 文件，也不包含 MaxMind 的 license key。** 你必须自己去取得。

### 你什么时候需要它？

只有当你想让 TAB 显示玩家地区的时候（`location-prefix.enabled: true`）。默认是关闭的。

### 什么时候完全不用装？

不想显示地区就完全不用管。**缺失或损坏的 MMDB 只会禁用 GeoIP 这一个功能**，其他一切照常，默认也不会刷一大段堆栈。

### 怎么取得

去 [MaxMind 的 GeoLite2 官方页面](https://dev.maxmind.com/geoip/geolite2-free-geolocation-data/)按他们的条款注册账号并下载。要下载 **City** 版数据库，不是 Country 版——省级精度需要 City 数据。

数据库格式说明：[City 和 Country 二进制数据库](https://dev.maxmind.com/geoip/docs/databases/city-and-country/city-binary/)。

### 安装

1. **停服。**
2. 从下载到的压缩包里解出 `.mmdb` 文件，重命名为 `GeoLite2-City.mmdb`。
3. 放到 `plugins/NekoCore/`（和 `config.yml` 同一个目录，不是 `plugins/` 根目录）。
4. 改配置：

```yaml
location-prefix:
  enabled: true
  database-file: GeoLite2-City.mmdb
```

5. **执行 `/nekocore reload` 让它生效。**（重载会重新打开 MMDB 文件；如果你想直接完整重启也可以。）
6. `/nekocore status` 确认 GeoIP 状态。

### ⚠ 许可与隐私

- **不要公开 MMDB 文件。** 它受 MaxMind 的许可条款约束，不要提交到 Git、不要放进公开整合包。
- **不要公开 license key。** 你的 MaxMind 账号凭据属于你自己。
- **不要用不明来源的镜像。** 那些文件的来源和时效都无法核实。
- 你仍然需要根据**你所在地区的法律**和服务器隐私政策评估这样做是否合适，并且告知玩家。

### 完整的说明在哪

隐私模型、缓存策略、中国省份与外国国家的区别、排查步骤，全部在 [GeoIP 文档](GEOIP.md)。

---

## 只在构建时需要的东西

如果你是**服主**，这一节可以完全跳过。

如果你是**开发者**，或者想从源码自己构建：

| 组件 | 版本 | 用途 |
| --- | --- | --- |
| Maven | 3.9+ | 构建工具 |
| JUnit Jupiter | 5.13.4 | 单元测试 |
| Mockito | 5.23.0 | 测试替身 |

这些**不需要装到服务器上**，测试库也不会进最终的 JAR。构建步骤如下：

```text
mvn -B clean verify
```

成品是 `target/NekoCore-1.0.0.jar`。

---

## 一键核对表

上线前扫一眼，确认你的组合是合理的：

| 你的情况 | 需要装什么 |
| --- | --- |
| 就一个主世界，用 NekoCore 自带的一切 | Paper + Java 25 |
| 想让别的插件显示 NekoCore 的金币/等级 | 上面 + PlaceholderAPI |
| 有多个世界，要在菜单里跳转 | 上面 + Multiverse-Core |
| 想让 NPC 会说话 | 上面 + Citizens |
| 想在 TAB 里显示玩家省份 | 上面 + 合法的 GeoLite2 City 数据 |

**任何一行里的可选组件缺失，都不会让 NekoCore 停止工作。** 这就是"安全降级"的意思：缺什么关什么，其余照常。

---

## 还在犹豫装不装？

- **想知道这些组件在游戏里具体是什么体验** → [配方手册](RECIPES.md)
- **想知道某个功能为什么没生效** → [兼容性与常见问题](COMPATIBILITY.md)
- **想搞清楚 GeoIP 的隐私模型** → [GeoIP 文档](GEOIP.md)
- **想自己改源码** → [贡献指南](../../CONTRIBUTING.md)
