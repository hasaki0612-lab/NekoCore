# 快速开始：把你的第一个 NekoCore 服务器跑起来

[English](../en/QUICKSTART.md) | [返回 README](../../README.md)

这一页写给**第一次开服的人**。

假设你有一台能上网的电脑或者 VPS，除此之外什么都没有——没装过 Java，没开过 Minecraft 服务器，也没写过 YAML。按下面的步骤走，大概五到十分钟，你就能进游戏看到自己的服务器面板了。

如果你已经开过服，只是想换个核心，可以直接跳到 [第 2 步](#第-2-步把-jar-放进-plugins)甚至 [第 4 步](#第-4-步改配置)，前面的部分跳过不影响。

> **你需要准备的东西**
> - 一台电脑（Windows、macOS、Linux 都行，能跑 Java 就可以）
> - 大约 2 GB 空闲内存
> - `NekoCore-1.0.0.jar`
>
> 你**不需要**准备的东西：Git、Maven、MySQL、Docker、Linux 命令经验。这些一个都不用。

> **想要能直接复制的完整命令？**
> 这一页讲「为什么这么做」，命令给的是核心那几条。远程 VPS 部署、本机 Windows 部署、面板部署的**完整命令大全**（含 `scp` 上传、`ssh` 登录、`screen`/`systemd` 启停、备份打包、排错命令）在[部署命令手册](DEPLOY.md)。两页配合看最省事：这里理解，那里抄命令。

**如果你只想最快跑起来**，把 `<服务器目录>` 换成你的服务器根目录（例如 `/opt/minecraft`），下面这四步就够了：

```powershell
# === 在你自己的 Windows 电脑上执行 ===

# 1. 确认 JAR 在本地
Get-Item 'D:\NekoCore-1.0.0.jar' | Select-Object Name, Length

# 2. 上传到服务器（会提示输入密码）
scp "D:\NekoCore-1.0.0.jar" root@<服务器IP>:<服务器目录>/plugins/
```

```bash
# === 登录服务器后执行 ===
ssh root@<服务器IP>

# 3. 启动服务器
cd <服务器目录>
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui
screen -r minecraft          # 进入控制台，看到 "NekoCore 1.0.0 ready" 即成功

# 4. 停服、改配置、再启动
#    在控制台输入 stop 停服，然后：
nano <服务器目录>/plugins/NekoCore/config.yml
#    改 branding.server-name 和 survival.world，Ctrl+O 保存、回车、Ctrl+X 退出
#    最后重新执行第 3 步的启动命令
```

**具体每一个参数是什么意思、出错了怎么查，往下看。** 命令手册里有更完整的版本（包括免密登录、备份、日志排查）。

---

## 第 1 步：装 Java，开一次 Paper

### 装 Java 25

Paper 26.2 需要 Java 25。先去 [Oracle 的 Java 25 安装文档](https://docs.oracle.com/en/java/javase/25/install/)，挑一个适合你系统的安装方式（Windows 用 Installer，macOS 用 `.dmg`，Linux 用包管理器或压缩包都可以）。

装完之后一定要验证一下。打开终端（Windows 是 PowerShell 或 CMD），输入：

```text
java -version
```

第一行应该看到类似 `openjdk version "25.0.x"` 的内容。

**如果提示 `'java' 不是内部或外部命令`**，说明 Java 装好了但系统不知道它在哪。这是新手最常卡住的地方：回去看一下安装过程最后有没有提示安装路径，然后把那个 `bin` 目录加到系统 PATH 里。走完这一步再回来，别急着往下做——后面的每一步都依赖它。

### 下载 Paper 26.2

去 [Paper 官方下载页](https://papermc.io/downloads/paper/)，选 26.2，下载那个 `.jar` 文件。

### 建一个干净的文件夹

在你喜欢的位置新建一个文件夹，比如 `D:\mc-server` 或者 `~/mc-server`，把下载到的 Paper JAR 放进去。

然后把它的名字改成好记又不用打字的——比如 `paper.jar`。以后每次启动都要输这个名字，改短一点会省很多事。

### 第一次启动

在这个文件夹里打开终端，运行：

```text
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

几个参数解释一下：`-Xms2G -Xmx2G` 是给服务器 2 GB 内存（朋友服够用了，人多或者加了插件可以调到 4G），`--nogui` 是不要弹出那个没用的图形窗口。

第一次启动**一定会失败**，这是正常的。它会停下来告诉你需要先接受 Mojang 的 EULA。打开刚刚生成的 `eula.txt`，把里面的 `eula=false` 改成 `eula=true`，保存。

再运行一次同样的命令。这次会看到它开始生成世界、加载区块，最后出现类似 `Done (12.345s)! For help, type "help"` 的字样。

**看到 `Done` 就是成功了。** 在终端里输入：

```text
stop
```

等它完全退出（终端回到可以输入命令的状态）再关窗口。这一步很重要：直接叉掉窗口不算正常停服，服务器可能正在写数据。

> 关于 EULA：接受它就是同意 Mojang 的[最终用户许可协议](https://aka.ms/MinecraftEULA)。开私服必须走这一步，这不是 NekoCore 的要求。

参考文档：[Paper 官方入门指南](https://docs.papermc.io/paper/admin/getting-started/)、[Paper 的 Java 安装说明](https://docs.papermc.io/paper/misc/java-install/)。

---

## 第 2 步：把 JAR 放进 `plugins/`

现在你的服务器文件夹应该长这样：

```text
mc-server/
├── paper.jar
├── eula.txt
├── server.properties
├── logs/
├── world/
└── plugins/          ← 就是这个文件夹
```

**`plugins` 文件夹就在服务器根目录下，和 `paper.jar` 并排。** 第一次启动 Paper 的时候它会自动创建；如果你没看到，就手动建一个，注意名字是全小写的 `plugins`。

把 `NekoCore-1.0.0.jar` 复制进去。**根据服务器在哪，用下面其中一种方式：**

**服务器在本机（Windows）——用 PowerShell：**

```powershell
Copy-Item 'D:\NekoCore-1.0.0.jar' 'D:\mc-server\plugins\' -Force
Get-Item 'D:\mc-server\plugins\NekoCore-1.0.0.jar' | Select-Object Name, Length
```

**服务器在远程 Linux VPS——先 `scp` 上传，再 `ssh` 进去操作：**

```powershell
scp "D:\NekoCore-1.0.0.jar" root@<服务器IP>:<服务器目录>/plugins/
```

```bash
# 上传完登录服务器确认一下
ssh root@<服务器IP>
ls -lh <服务器目录>/plugins/NekoCore-1.0.0.jar
```

**服务器是面板服——** 面板「文件管理」→ 进 `plugins` → 「上传」→ 选 JAR。

放好之后：

```text
mc-server/
└── plugins/
    └── NekoCore-1.0.0.jar
```

> **三个不要**
> - 不要复制 `original-NekoCore-1.0.0.jar`。如果你是从源码自己构建的，`target/` 里会有这个文件——它是打包前的半成品，装了会出问题。
> - 不要把 JAR 放进 `plugins/` 里的某个子文件夹，插件不会去那里找。
> - 不要用来路不明的"汉化版""整合版"。只从项目官方发布页下载。

如果你同时要装 PlaceholderAPI、Citizens 或 Multiverse-Core，现在一起放进 `plugins/` 就好，顺序无所谓。**不放也完全没问题**，NekoCore 的核心功能不依赖它们。（每个组件是干嘛的见[依赖说明](DEPENDENCIES.md)。）

> 每种部署方式的**完整命令**（含免密登录、日志排查、备份打包）在[部署命令手册](DEPLOY.md)。

---

## 第 3 步：启动，然后确认它真的加载了

再启动一次服务器：

```text
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

第一次启动 NekoCore 的时候，它会做三件事：

1. 在 `plugins/NekoCore/` 里生成默认的 `config.yml` 和 `messages.yml`；
2. 创建数据库 `plugins/NekoCore/nekocore.db`，并在里面按顺序执行 V1 到 V5 五个迁移，把表建好；
3. 打印一段简短的状态摘要。

**为什么要让它先生成配置？** 因为 NekoCore 的默认配置是完整的、可以立刻使用的。让插件自己生成一份，你就拿到了一份格式正确、注释齐全、和当前版本严丝合缝的模板，改起来比手写安全得多。

### 怎么确认加载成功

在控制台里滚一下日志，找这几样东西：

| 你应该看到 | 说明 |
| --- | --- |
| `NekoCore` 的启动横幅/信息行 | 插件被 Paper 认出来了 |
| `schema 5` 或迁移相关的行 | 数据库结构正确 |
| 一行包含 `NekoCore 1.0.0 ready` 的摘要 | 初始化全部完成，可以接待玩家了 |
| **没有** `ERROR` 或大段异常堆栈 | 一切正常 |

日志被默认压缩过，所以它不会刷满屏幕——这是有意的。只要看到 `ready`，就可以放心了。

如果你更想用命令确认，进游戏后打 `/nekocore status`，它会列出 NekoCore、Paper、Java 版本、数据库 schema，以及各个模块的状态。

**在 Linux 服务器上，用这两条命令看日志：**

```bash
grep -a "NekoCore" <服务器目录>/logs/latest.log | tail -20    # 只看 NekoCore 相关
tail -f <服务器目录>/logs/latest.log                          # 实时跟看（Ctrl+C 退出）
```

**在 Windows 本机上：**

```powershell
Select-String -Path 'D:\mc-server\logs\latest.log' -Pattern 'NekoCore' | Select-Object -Last 20
```

### 没有 PlaceholderAPI / Citizens / Multiverse / MMDB 是正常的

日志里可能会出现"某功能已停用"之类的提示，比如网络地区前缀因为找不到数据库文件而关闭。**这不是错误。** NekoCore 的设计就是：缺什么就安静地关掉什么，其余功能照常。等你真的需要那个功能了，再去配。

现在，正常停服：

```text
stop
```

---

## 第 4 步：改配置

现在 `plugins/NekoCore/` 里有两个文件可以改：

```text
plugins/NekoCore/
├── config.yml        ← 服务器行为
├── messages.yml      ← 玩家看到的文字
└── nekocore.db       ← 数据，别动它
```

### 先停服

改 `config.yml` 之前请先停服。虽然 NekoCore 支持运行中重载，但第一次配置的时候，你多半会改到一些重载不覆盖的东西（世界、数据库文件名），停服改最省心。养成"改配置 = 先 stop"的习惯不亏。

### 用什么打开

**不要用 Windows 记事本。** 它可能在保存的时候改变文件编码或者缩进，YAML 对这两样都很敏感。

推荐任意一个能明确显示空格缩进、并且以 UTF-8 保存的编辑器：

- **VS Code**（推荐，免费，装个 YAML 扩展还能高亮报错）
- Notepad++
- Sublime Text
- Linux/macOS 上的 `nano`、`vim`（记得存成 UTF-8）

### 至少改这两个地方

打开 `config.yml`，找到最上面几行：

```yaml
config-version: 8
branding:
  # 支持 {server} 的玩家可见文案会使用这个名称；修改后执行 /nekocore reload。
  server-name: "My Server"
database:
  filename: nekocore.db
  save-interval-seconds: 30
survival:
  enabled: true
  world: world
  # 安装 Multiverse-Core 时执行此命令；未安装时安全传送到已加载世界的出生点。
  command: 'mvtp {player} {world}'
```

**改动一：`branding.server-name`**

把 `"My Server"` 换成你服务器的名字。引号建议保留——名字里有空格、井号或者颜色代码的时候，它是必需的。

```yaml
branding:
  server-name: "猫猫服"
```

这个名字会出现在 TAB、欢迎标题、GUI 标题、Tips、吉祥物全息字，以及每日任务里那句"早安！{server}！"。

**改动二：`survival.world`**

看一眼 `server.properties` 里的 `level-name`，它就是你的主世界名字，默认是 `world`。

- 如果 `level-name=world`，那 `survival.world: world` 不用动；
- 如果你改过，比如 `level-name=main`，那这里也要跟着写 `main`。

世界名**区分大小写**，`World` 和 `world` 在 Paper 眼里是两个东西。写错了不会让服务器崩，只会让 `/menu` 里的生存入口消失。

### 关于 `survival.command`

这一行是给装了 Multiverse-Core 的服务器用的。**没装 Multiverse 的话不用管它**，NekoCore 检测到命令不可用时会安全地把你传送到那个已加载世界的出生点。

### YAML 缩进：为什么不能乱

YAML 用**空格的数量**来表达层级关系，这一点和 JSON、和大多数配置文件都不一样。所以：

- **只能用空格，绝对不能用 Tab 键。** 这是新手最常踩的坑——看起来一模一样，解析器会直接报错。VS Code 右下角可以确认当前文件用的是空格。
- **同一层级的字段要缩进一样多。** 上面 `branding` 下面的 `server-name` 前面是两个空格，那 `database` 下面的 `filename` 也必须是两个空格。
- **冒号后面要有一个空格。** 写 `server-name:"猫猫服"` 是错的，要写 `server-name: "猫猫服"`。
- **值里有特殊字符就加引号。** YAML 里 `#` 是注释开始，`:` 是键值分隔，所以 `server-name: "My #1 Server"` 要带引号。

改坏了大不了就是重载失败——服务器会继续用上一份有效配置，不会下线。所以放心试。

---

## 第 5 步：重启，然后进游戏

启动服务器，用 Minecraft 客户端连上 `localhost`（本机测试）或者你的服务器地址。

### 进服第一眼

- 屏幕中央会浮出一行欢迎标题，写着你的游戏名和服务器名；
- TAB 列表被填好了：坐标、TPS、在线人数、金币、时间、运行时长；
- 过一会儿聊天栏会冒出一句 Tips。

### 建议按这个顺序试一遍

```text
/menu
```

先打开面板看看整体长什么样。关着的模块不会显示成灰色按钮，而是直接消失并把其他按钮重新居中——所以你看到的这一屏就是这台服务器目前真正能用的功能。

```text
/coins
```

看看自己的金币。新玩家是 0。

```text
/checkin
```

领今天的签到奖励。默认是 100 金币 + 50 经验。**每天只能领一次**，按服务器配置的时区（默认北京时间）算自然日。

```text
/sethome
/home
```

先记住当前位置，再传送回来。Home 是**按世界分开存的**，所以你以后在下界、末地各设一个都不会互相覆盖。

```text
/check home world
```

看看自己的 Home 具体在哪个坐标。

```text
/store
```

逛商店。**右键是买入，左键是出售**，别按反了。想看看自己买不买得起的话，商店界面里会显示当前余额。

```text
/bag
```

打开随身仓库。在主世界可以正常取放，去别的世界会变成只读——这是默认设计，防止和独立背包规则打架。

```text
/tpn <另一个玩家的名字>
```

让对方用 `/yes` 或 `/no` 回应。一个人测试的话可以先开两个客户端，或者暂时跳过这条。

### 管理员再试这两条

如果你是 OP：

```text
/nekocore status
```

一屏看清所有模块的状态。以后遇到"某个功能没生效"，先看这里。

```text
/nekocore config check
```

只检查配置文件是否合法，**不应用任何改动**。改完配置还没决定要不要生效的时候用它。

想真正应用改动：

```text
/nekocore reload
```

**`config check` 和 `reload` 的区别值得记住：**

| | 做什么 | 什么时候用 |
| --- | --- | --- |
| `config check` | 解析并验证磁盘上的配置，报告问题，什么都不改 | 刚改完 YAML，想先确认没写错 |
| `reload` | 解析 → 验证 → 构建完整的新配置 → 成功后整体切换 | 确认没问题了，要让它生效 |

`reload` 是**原子**的：所有配置全部通过之后才会替换。任何一项失败，服务器继续用旧的跑，并告诉你错在哪个 YAML 路径、期望什么类型。不会出现"一半新一半旧"的状态。

---

## 第一次开服检查清单

正式邀请朋友进来之前，花两分钟走一遍：

**服务器层面**

- [ ] `java -version` 显示 25
- [ ] Paper 版本是 26.2
- [ ] 服务器能正常启动到 `Done`，也能用 `stop` 正常关闭
- [ ] `plugins/NekoCore/` 里有 `config.yml`、`messages.yml`、`nekocore.db` 三个文件

**配置层面**

- [ ] `branding.server-name` 改成了自己的服务器名（不是 `My Server`）
- [ ] `survival.world` 和控制台实际加载的世界名完全一致（含大小写）
- [ ] 跑过 `/nekocore config check`，结果是成功的
- [ ] `config-version: 8` 和 `messages-version: 7` 没有被改动过

**游戏内**

- [ ] `/menu` 能打开，显示的按钮都是你打算提供的功能
- [ ] `/checkin`、`/sethome`、`/home`、`/coins`、`/store`、`/bag` 都能用
- [ ] TAB 显示正常，没有被别的插件盖掉
- [ ] `/nekocore status` 里没有意外的错误状态

**运营准备**

- [ ] **备份做了。** 停服，把整个 `plugins/NekoCore/` 复制一份到别的地方。如果目录里有 `nekocore.db-wal` 或 `nekocore.db-shm`，一起复制。
- [ ] 想好了商店价格和任务奖励适不适合你的玩家数量（默认值是按朋友服调的，见[经济文档](ECONOMY.md)）
- [ ] 如果打算开 GeoIP，已经看过[合规与隐私说明](GEOIP.md)

---

## 接下来做什么

按你现在的需求挑一条：

**想要能直接复制的完整命令**
→ [部署命令手册](DEPLOY.md)。远程 VPS、本机 Windows、面板服三种场景的完整命令：`scp` 上传、`ssh` 登录、`screen`/`systemd` 启停、免密登录、日志排查、备份打包、命令速查表。

**想让服务器更像"你的"服务器**
→ [配方手册](RECIPES.md)。里面是"我想改签到奖励""我想只开一个生存服""我想关掉 TAB"这种一句话目标，每节给最短结论、YAML、操作步骤和验证方法。

**想搞清楚某个字段到底干什么**
→ [配置手册](CONFIGURATION.md)。每个模块都会先用自然语言解释它实际怎么工作，再列字段。

**想加 Citizens 吉祥物、PlaceholderAPI 或者 Multiverse**
→ [依赖说明](DEPENDENCIES.md)。有官方下载地址和安装步骤。

**想让 TAB 里显示玩家省份/国家**
→ [GeoIP 文档](GEOIP.md)。注意这一步需要你自己去 MaxMind 合法取得数据文件，JAR 里没有。

**某个功能没反应**
→ [兼容性与常见问题](COMPATIBILITY.md)。"为什么 TAB 没显示""为什么地区一直没有""为什么菜单里没有挂机池"这类问题都有按顺序的排查步骤。

**准备正式上线了**
→ [安装与升级](INSTALLATION.md)里的升级和回滚章节，以及[数据库](DATABASE.md)的备份流程，建议在上线前读一遍。
