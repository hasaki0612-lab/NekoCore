# NekoCore Public 1.2.0

> 少装一堆零散插件，装完就能开一个能玩的小服务器。

你好，欢迎使用 NekoCore。

你可能只是想让自己的服务器少几个插件，但 NekoCore 不只是把功能塞进一个 JAR。它想让**不会 Java 的人**也能把资料、经济、菜单、商店、任务这些基础东西配起来——改 YAML 就够了。

装完之后，玩家输入 `/menu` 看到的是一个完整的服务器面板，`/checkin` 能领今天的礼物，`/store` 里有 109 件商品可以买卖。这些**默认就开着**，不用你配。

不需要数据库服务器、不需要网页面板、不需要额外下载依赖。**有 Paper 和 Java 就能跑。**

Public 1.2.0 在原基础上补充：更易懂的配置错误、管理员 `/nekocore lookup` 名称查询、三个安全场景预设，以及只在新生成配置时出现的简短指引。默认配置可直接使用；不必先把每个模块配完。详见[场景预设](docs/zh-CN/PRESETS.md)和[升级说明](docs/zh-CN/INSTALLATION.md)。

## NekoCore 能帮你解决什么

| 你遇到的问题 | NekoCore 能做什么 |
|---|---|
| 基础功能散在七八个插件里，配置目录到处是 | 资料、经济、等级、菜单、Home、签到、商店、Bag、TPN、任务、TAB、提示全在一个插件里，配置只有两个文件 |
| 装了插件不知道从哪配起 | `/nekocore config check` 先验证再应用；说明文件、配置项、用途、当前值，可靠定位时显示行号；Material 拼错给相近名称建议 |
| 改配置怕把服务器改崩 | 重载是原子的——全部通过才切换；任何一项失败就继续用旧配置跑，玩家无感知 |
| 怕插件乱传送玩家、乱生成悬浮物 | 所有涉及**坐标和外部数据**的功能默认关闭，且需要 `enabled` + `position-configured` 两把锁同时打开 |
| 商店交易怕崩服复制物品 | Store 和 Bag 走数据库事务，带恢复记录；不确定的状态**只隔离不猜测**，不会擅自重发或覆盖物品 |
| 不知道玩家为什么"点了没反应" | `/nekocore status` 一屏列出所有模块状态；模块关着时菜单入口会直接消失，而不是留个灰按钮 |

这里最重要的不是"功能多"，而是 **默认能玩，但绝不替你猜**：

```text
需要你告诉我外部事实的功能  →  默认关闭，两把锁
不依赖外部事实的功能        →  默认全开，装完即玩
```

## 准备好了，开始安装

| 你的服务器在哪 | 去哪看 |
|---|---|
| 远程 Linux VPS（本机是 Windows） | [快速开始](docs/zh-CN/QUICKSTART.md) —— 就是按这个写的 |
| 服务器就在本机 Windows | [快速开始](docs/zh-CN/QUICKSTART.md) 文末折叠块 |
| 面板服（翼龙 / MCSManager 等） | [快速开始](docs/zh-CN/QUICKSTART.md) 文末折叠块 |
| 想要能复制的完整命令｜备份｜排错 | [部署命令速查](docs/zh-CN/DEPLOY.md) |

**最短路径**（把 `<服务器IP>` 和 `<服务器目录>` 换成你自己的）：

```powershell
# 在你自己 Windows 的 PowerShell 上
scp "D:\NekoCore-1.2.0.jar" root@<服务器IP>:<服务器目录>/plugins/
ssh root@<服务器IP>
```

```bash
# 登录服务器后
cd <服务器目录>
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui
screen -r minecraft          # 看到 "NekoCore 1.2.0 ready" 就是成功了
```

默认主世界叫 `world` 时，已经可以进游戏输入 `/menu`。想改名，或主世界不叫 `world`，再改这两个地方；修改后先检查，再重载：

```yaml
branding:
  server-name: "My Server"     # ← 你的服务器名
survival:
  world: world                 # ← 主世界的实际文件夹名
```

**详细说明、改配置、进游戏验证** → [快速开始](docs/zh-CN/QUICKSTART.md)

---

## 装好以后，玩家会看到什么

**进服那一瞬间。** 屏幕中央浮出欢迎标题，写着玩家名字和你的服务器名。TAB 列表填好了：坐标、TPS、在线人数、金币、时间、运行时长。

**打开 `/menu`：**

| 入口 | 玩家看到什么 |
|---|---|
| 玩家资料 | 等级、经验、金币、游玩时长、首次加入时间 |
| 每日任务 | 简单/普通/困难各 3 个，全服共用同一套，进度各自保存 |
| 每日签到 | 每天一次，默认 100 金币 + 50 经验 |
| 显示网络地区 | 玩家自己的隐私开关（需要先配 GeoIP） |
| 生存一区 / 二区 | 前往对应世界的公共出生点 |
| 小游戏区 / 挂机池 | 默认关闭，配好坐标后才会出现 |

**没有开启的模块不会留下空格**——按钮会自动重新居中。所以玩家看到的那一屏，就是你这台服务器**真正能用**的功能。

**玩家会常用的命令：**

| 命令 | 做什么 |
|---|---|
| `/menu` | 打开服务器面板 |
| `/checkin` | 领今天的签到 |
| `/sethome` `/home` | 记小窝 / 回小窝（按世界分开存） |
| `/store` | 商店，**右键买入、左键出售** |
| `/bag` | 随身仓库，默认只在主世界能取放 |
| `/tpn <玩家名>` | 发传送请求，对方用 `/yes` 或 `/no` 回应 |
| `/tasks` | 直接打开每日任务 |

**默认关着的**（因为 NekoCore 不想替你猜）：网络地区显示、第二生存世界、小游戏区、挂机池、金币周榜、Citizens 吉祥物。配好对应的世界或坐标再打开就行。

---

## 两个文件管所有配置

| 文件 | 管什么 |
|---|---|
| `config.yml` | 服务器行为。世界观、经济数值、菜单布局、模块开关 |
| `messages.yml` | 所有玩家能看到的文字 |

改完的流程固定是这两条：

```text
/nekocore config check     ← 只检查，不应用任何改动
/nekocore reload           ← 确认没问题了，真正生效
```

**重载失败不会让你的服务器下线。** 它会继续用上一份有效配置跑着，同时在控制台告诉你错在哪个 YAML 路径、期望什么类型。

**需要完整重启的：** 换 JAR、改数据库文件名、装新插件、改世界插件。其他都能 `reload`。

> **别用 Bukkit 的 `/reload`。** 它和 NekoCore 的重载完全是两件事，用它只会让服务器进入更难预测的状态。

具体改法看[配方手册](docs/zh-CN/RECIPES.md)，字段详解看[配置手册](docs/zh-CN/CONFIGURATION.md)。

---

## 数据存在哪里

一个 SQLite 文件：`plugins/NekoCore/nekocore.db`。

**备份记住三件事：**

1. **先停服**，等 Java 进程真的退出再复制
2. 目录里还有 `nekocore.db-wal` 和 `nekocore.db-shm` 的话，**一起复制**（只复制 `.db` 可能拿到不完整的时点）
3. 用独立背包（如 Multiverse-Inventories）的话，物品数据要和数据库**同一时点**

```bash
# 一条命令打包整个目录，不会漏文件
cd <服务器目录>/plugins
tar -czf ~/nekocore-$(date +%Y%m%d).tar.gz NekoCore/
```

**不要在服务器运行时用 SQLite 编辑器手工改表。** 那些看起来多余的恢复表，正是交易恢复逻辑的判断依据。详见[数据库文档](docs/zh-CN/DATABASE.md)。

---

## 出问题先看哪里

按这个顺序，通常三步内定位：

1. **看控制台日志** —— 正常启动会有一行 `NekoCore 1.2.0 ready`
2. **`/nekocore status`** —— 一屏列出所有模块状态，九成"没生效"的答案在这
3. **`/nekocore config check`** —— 改过 YAML 就先确认配置合法

| 现象 | 通常原因 | 怎么处理 |
|---|---|---|
| 日志里没有 `NekoCore ready` | JAR 没放对位置 | `ls <服务器目录>/plugins/ \| grep -i neko` |
| 某个模块在菜单里消失了 | 模块关着，或它依赖的世界没加载 | 这是**设计行为**不是故障，查 `/nekocore status` |
| 挂机池 / 周榜点了没用 | 两把锁只开了一把 | `enabled` 和 `position-configured` 都要 `true` |
| TAB 不显示，或被覆盖 | 另一个 TAB 插件在抢所有权 | 两个插件只能有一个负责 TAB，明确选一边 |
| 地区一直空着 | 没重载，或 `cache-session` 被改成 `false` | `/nekocore reload` 后检查配置 |
| 改了配置没生效 | 只跑了 `config check`，没跑 `reload` | 这两条是不同的命令 |
| 服务器起不来 | `eula.txt` 还是 `false`，或端口被占用 | `cat eula.txt` / `ss -tlnp \| grep 25565` |

**还有别的症状？** 看[兼容性与常见问题](docs/zh-CN/COMPATIBILITY.md)。

---

## 环境要求

| 要求 | 版本 |
|---|---|
| [Paper](https://papermc.io/downloads/paper/) | 26.2（API build 129 stable） |
| [Java](https://docs.oracle.com/en/java/javase/25/install/) | 25 |

SQLite JDBC 和 GeoIP 读取库**已经打进 JAR**，不用额外下载。

**可选的第三方组件**（不装也完全能用）：

| 组件 | 什么时候需要 |
|---|---|
| PlaceholderAPI | 想让别的插件读 NekoCore 数据（如 `%nekocore_coins%`） |
| Multiverse-Core | 想用 `mvtp {player} {world}` 管理世界跳转 |
| Citizens | 只有要开内置的吉祥物互动模块 |
| GeoLite2 City 数据 | 只有要显示玩家地区，需自己从 MaxMind 取得 |

WorldGuard **不是** NekoCore 的依赖，可以共存但不在自动测试范围内。

> 常见误解：**Mascot 是 NekoCore 自己内置的模块，不是第三方插件。** Citizens 负责的只是 NPC 实体本身。

详见[依赖说明](docs/zh-CN/DEPENDENCIES.md)。

---

## 文档

第一次接触的话，按这个顺序读：**快速开始 → 配方手册 → 配置手册**。

| 文档 | 内容 |
|---|---|
| [快速开始](docs/zh-CN/QUICKSTART.md) | 手把手第一次开服，含检查清单 |
| [部署命令速查](docs/zh-CN/DEPLOY.md) | 命令大全：scp、ssh、screen、备份、排错 |
| [场景预设](docs/zh-CN/PRESETS.md) | 三份可直接使用的完整配置：单生存、大厅+生存、朋友服 |
| [配方手册](docs/zh-CN/RECIPES.md) | "我想改签到奖励"这种一句话目标 |
| [配置手册](docs/zh-CN/CONFIGURATION.md) | 每个模块做什么，字段怎么填 |
| [兼容性与常见问题](docs/zh-CN/COMPATIBILITY.md) | "为什么没生效"逐条排查 |
| [依赖说明](docs/zh-CN/DEPENDENCIES.md) | 第三方组件的用途与官方来源 |
| [命令与权限](docs/zh-CN/COMMANDS.md) | 玩家命令、管理命令、权限节点 |
| [经济与商店](docs/zh-CN/ECONOMY.md) | 金币怎么来怎么去，商店怎么定价 |
| [每日任务](docs/zh-CN/DAILY-TASKS.md) | 20 个任务分别怎么完成 |
| [数据库](docs/zh-CN/DATABASE.md) | SQLite、迁移、备份与恢复 |
| [安装与升级](docs/zh-CN/INSTALLATION.md) | 全新安装、升级、回滚、卸载 |
| [GeoIP](docs/zh-CN/GEOIP.md) | 合法安装地区数据与隐私模型 |

English: [README.en.md](README.en.md) · [docs/en](docs/en)

<details>
<summary><b>Public 1.1.0 更新了什么</b></summary>

在 Public 1.0.0 上的增量更新：保留最初的 1.4.0 完整基线，同步私有 1.4.1～1.4.4 中的通用改进，两条版本线独立。

- `/tasks` 命令，直接打开每日任务 GUI（权限 `nekocore.tasks`，默认 true）
- 配置驱动的个人 JoinInfo，可选四个管理员提供的链接
- 周榜默认 `FIXED` 朝向，尊重 `yaw`，并支持 `CENTER`
- Mascot 全息字整体高度默认从 2.85 调整为 2.25（不改行距，不动 NPC）
- 默认 TAB 更紧凑，Tips 间隔改为 180 秒
- AFK 奖励尝试改为 60 秒，并在正常会话结束时向本人显示挂机时长

**升级方式：** 停服并完整备份后**只替换 JAR**，保留自己的 YAML 和数据库。config **8 → 9**、messages **7 → 8** 走原有合并与备份机制，不会覆盖你的自定义字段。**SQLite schema 仍为 5**，V1→V5 不变。

升级前先读[安装与升级](docs/zh-CN/INSTALLATION.md)，**不要直接用默认 YAML 覆盖旧文件**。

</details>

<details>
<summary><b>从源码构建</b></summary>

需要 JDK 25 和 Maven 3.9+：

```text
mvn -B clean verify
```

成品是 `target/NekoCore-1.2.0.jar`。

打包会用 shade 处理依赖，因此 `target/` 里还会留下 `original-NekoCore-1.2.0.jar`——**那是 shade 之前的半成品，不要发布它**。CI 在 push 和 pull request 时执行同一条命令。

</details>

<details>
<summary><b>内部版本与验证范围</b></summary>

| 项目 | 值 |
|---|---|
| `config-version` | 9 |
| `messages-version` | 8 |
| SQLite schema | 5（V1→V5，**没有 V6**） |

这些数字**不要手动改动**。它们决定插件怎么读你的数据，改了不会修好任何问题。

**稳定 ID 保持不变：** 109 个商品 ID、20 个任务 ID、3 个头衔 ID（`mame` / `momo` / `sora`）。

**自动测试覆盖了什么：** 配置解析与升级、数据库迁移（含全新空目录）、商店与 Bag 的事务恢复、每日任务的事件处理、缺少可选组件时的降级行为、命令与权限映射。

**没覆盖什么：** 真实 Paper 上的实体与 UI 行为、Citizens/Multiverse/WorldGuard 的真实共存、与其他 TAB/聊天插件的显示所有权冲突、真实崩溃下的背包事务、高负载关停、从真实备份升级。

**这些不是"应该没问题"，而是"需要你在测试服验证"的清单。** 详见[发布验证](VERIFICATION.md)。

</details>

---

## Credits 与 License

项目所有者与维护者：**秋山羽咲**。
开发与设计协助：**GPT-5.6 Sol (OpenAI)** 与 **DeepSeek-V41-Flash (深度求索)**。

这一条只是如实记录协助来源，不表示 OpenAI 或深度求索维护、发布或背书这个项目。

本项目以 **MIT 许可证**发布，全文见 [LICENSE](LICENSE)。你可以自由使用、修改、分发这份代码，甚至用于闭源项目——只要保留版权声明。第三方组件的许可证仍然各自独立。

> **关于运行语言：** 游戏内的消息是简体中文；英文版只提供**文档**，没有做运行时的多语言系统。这一点写出来，免得英文用户装完才发现。
