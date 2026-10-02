# 快速开始

[English](../en/QUICKSTART.md) | [返回 README](../../README.md) | [命令速查](DEPLOY.md)

跟着做，大约 10 分钟，你就能进游戏看到自己的服务器面板。

**不需要**会 Java、不需要写过配置、不需要懂 Linux。

**已装好 Paper 26.2 / Java 25？** 把 JAR 放进 `plugins/` 并启动，就能先用 `/menu`。主世界叫 `world` 时无需预先编辑配置；先体验，再改服务器名和奖励。第一次生成配置会打印五行指引，普通重启不重复。想选一种场景再看[预设](PRESETS.md)，旧服不要整份覆盖配置。

---

## 先搞清楚一样东西：服务器目录

后面每一步都要用到它，所以先花一分钟弄明白。

**服务器目录 = 你服务器那个文件夹。** 就是装着 `paper.jar` 的那个文件夹。

**它长这样：**

```text
你的服务器目录/
├── paper.jar              ← 服务器本体，这个文件夹因它得名
├── server.properties      ← 配置：主世界叫什么、端口是多少
├── eula.txt               ← 同意 Mojang 条款的标记文件
├── plugins/               ← 插件都放这里！NekoCore 也要放进去
├── world/                 ← 主世界的地图数据
└── logs/                  ← 日志，出问题要看这里
```

**如果你不知道它在哪**，登录服务器后依次跑这三条命令，哪条有输出就是它：

```bash
ls /opt/minecraft
ls /root/minecraft
ls ~/minecraft
```

看到 `paper.jar` 就说明找对了。

> 下面所有命令里的 `<服务器目录>`，都换成你刚才找到的路径。
> 例如找到的是 `/opt/minecraft`，就把 `<服务器目录>` 整个替换成 `/opt/minecraft`。

<details>
<summary><b>我的服务器在哪？我连它长什么样都不知道</b></summary>

三种常见情况，对号入座就行：

**① 你买了 VPS（就是一台远程的 Linux 电脑）**

服务商会给你一个 **IP 地址**、一个**用户名**（通常是 `root`）、一个**密码**。用这三样就能连上去。

**② 你用了"面板服"（翼龙、MCSManager 这类网页控制台）**

那服务器不在你手上，在服务商的机器上。**你不需要登录 Linux，也不需要敲命令**——网页上点鼠标就行。直接跳到文末的折叠块「面板服」。

**③ 你就在自己电脑上开服**

那服务器目录就是你电脑上的某个文件夹，比如 `D:\mc-server`。跳到文末的折叠块「本机 Windows」。

</details>

---

## 第 1 步：把 NekoCore 传进 `plugins/` 文件夹

在**你自己的 Windows 电脑**上，按 `Win` 键，输入 `powershell`，回车打开。

把下面这条命令里的两个地方换掉，然后粘贴进去回车：

```powershell
scp "D:\NekoCore-1.2.0.jar" root@<服务器IP>:<服务器目录>/plugins/
```

**换哪两个地方：**

| 换成 | 你从哪知道 |
|---|---|
| `<服务器IP>` | 服务商给你的 IP 地址，例如 `192.0.2.10` |
| `<服务器目录>` | 上一步找到的，例如 `/opt/minecraft` |

**换完长这样**（只是例子，别照抄）：

```powershell
scp "D:\NekoCore-1.2.0.jar" root@192.0.2.10:/opt/minecraft/plugins/
```

回车后会让你输密码。**输入的时候屏幕上什么都不会显示**——不是键盘坏了，是 Linux 就是这样，打完直接回车。

> **如果报错：**
>
> - `No such file or directory` —— `D:\NekoCore-1.2.0.jar` 这个路径不对，确认 JAR 真的在那里
> - `Permission denied` —— 权限不够，把命令里的 `root` 换成你的实际用户名
> - 卡住不动半天 —— IP 写错了，或者服务器连不上

---

## 第 2 步：启动服务器

还是刚才那个 PowerShell 窗口，输入：

```powershell
ssh root@<服务器IP>
```

（`<服务器IP>` 换成你的，还是要输一次密码。）

连上之后，提示符会变成类似 `root@server:~#` 的样子，说明你现在**在服务器里面**了。

然后依次输入这两条：

```bash
cd <服务器目录>
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui
```

第一条是"进入服务器目录"，第二条是"启动服务器"。

> **如果提示 `cd: no such file or directory`** —— `<服务器目录>` 写错了，回到最上面重新确认。

等十几秒，然后看有没有这一行：

```text
NekoCore 1.2.0 ready
```

**看到它就成功了。**

**怎么看日志？** 输入：

```bash
screen -r minecraft
```

这会进入服务器控制台，你就能看到滚动的日志了。看到 `ready` 之后，按 `Ctrl+A` 再按 `D` 退出（**注意：这样退出不会关掉服务器**）。

> 日志里出现「某功能已停用」之类的提示**不用管**。NekoCore 缺什么就安静地关掉什么，缺 PlaceholderAPI、Citizens、Multiverse 都是正常的。
>
> **没有 `ERROR` 就是没问题。**

---

## 第 3 步：停服，改两个地方

**先停服。** 进入控制台：

```bash
screen -r minecraft
```

输入 `stop` 回车，**等它完全退干净**（屏幕上不再滚动，提示符回来）。

现在改配置。这个文件**必须用能保存 UTF-8 的编辑器**打开，服务器上用 `nano` 最方便：

```bash
nano <服务器目录>/plugins/NekoCore/config.yml
```

找到最上面这几行，**只改两处**：

```yaml
branding:
  server-name: "My Server"     # ← 改成你的服务器名
survival:
  world: world                 # ← 改成你主世界的实际文件夹名
```

**改完怎么保存？** `nano` 的操作是固定的三步：

```text
Ctrl + O      （字母 O，不是数字 0）→ 这是"保存"
回车          → 确认文件名
Ctrl + X      → 退出
```

**主世界叫什么？** 这条命令会告诉你：

```bash
grep '^level-name' <服务器目录>/server.properties
```

比如输出 `level-name=world`，那 `survival.world` 就填 `world`。

**注意大小写必须完全一致。** `World` 和 `world` 在服务器眼里是两个不同的东西。写错了不会崩服，但游戏里菜单会少一个入口。

<details>
<summary><b>保存时提示缩进错误 / 服务器起不来了</b></summary>

九成是**用了 Tab 键**。这个文件只允许用**空格**缩进，Tab 和空格看起来一模一样，但服务器会拒绝。

**检查有没有 Tab：**

```bash
grep -Pn '\t' <服务器目录>/plugins/NekoCore/config.yml
```

**这条命令应该什么输出都没有。** 有输出就说明那几行混进了 Tab——用空格重新打一遍那几行。

**改坏了也别慌。** 就算配置写错，服务器也**不会下线**——它会继续用上一份有效的配置跑着，只在控制台告诉你哪里错了。

</details>

<details>
<summary><b>不要用 Windows 记事本打开这个文件</b></summary>

记事本可能在保存时改掉编码或缩进，而 YAML 对这两样都很敏感。

推荐：

- **VS Code**（免费，装了 YAML 扩展还会直接给你标红）
- Notepad++
- 服务器上的 `nano`（就是上面用的那个）

实在要用记事本，保存时**编码选 UTF-8**。

</details>

---

## 第 4 步：重新启动，进游戏看看

```bash
cd <服务器目录>
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui
```

然后用 Minecraft 客户端连上你的服务器，**按顺序试这四条**：

```text
/menu         打开服务器面板（先看看整体长什么样）
/checkin      领今天的签到
/sethome      记住你站的位置
/home         传送回来
```

**四步都正常，就算装好了。**

> **`/menu` 里东西比想象中少？** 那是正常的。**关掉的模块不会显示成灰色按钮，而是直接消失**——所以你看到的那一屏，就是你服务器现在真正能用的功能。

**管理员（OP）再试这两条：**

```text
/nekocore status          一屏列出所有模块的状态
/nekocore config check    检查配置有没有写错（不会应用改动）
```

| 命令 | 区别 |
|---|---|
| `config check` | **只检查，什么都不改**。改完配置文件想先确认没错，用这个 |
| `reload` | 检查通过后**真正生效**。确认没问题了用这个 |

---

## 装完检查一下

| 检查项 | 怎么看 |
|---|---|
| JAR 放对了吗 | `plugins/` 里应该有 `NekoCore-1.2.0.jar` |
| 插件加载了吗 | 日志里有 `NekoCore 1.2.0 ready`，且没有 `ERROR` |
| 配置文件生成了吗 | `plugins/NekoCore/` 里应该有 `config.yml`、`messages.yml`、`nekocore.db` |
| 游戏里能用吗 | `/menu` 能打开，`/checkin` 能领到东西 |
| 备份了吗 | **停服**后把整个 `plugins/NekoCore/` 复制到别的地方 |

**备份这一条别跳过。** 一条命令就能打包好：

```bash
cd <服务器目录>/plugins
tar -czf ~/nekocore-backup-$(date +%Y%m%d).tar.gz NekoCore/
```

---

## 接下来想做什么

| 我想…… | 看这份 |
|---|---|
| 改签到奖励、改商店价格、关掉 TAB | [配方手册](RECIPES.md) |
| 搞懂某个配置项是干什么的 | [配置手册](CONFIGURATION.md) |
| 要能直接复制的命令（备份、排错） | [命令速查](DEPLOY.md) |
| 装 Citizens / PlaceholderAPI | [依赖说明](DEPENDENCIES.md) |
| 让 TAB 显示玩家省份 | [GeoIP](GEOIP.md) |
| 某个功能没反应 | [兼容性与常见问题](COMPATIBILITY.md) |
| 准备正式开服 | [安装与升级](INSTALLATION.md) |

---

<details>
<summary><b>面板服（翼龙 / MCSManager 等）</b></summary>

**面板服不用敲任何命令**，全程点鼠标。服务器在服务商的机器上，你只需要一个网页。

```text
1. 在面板里打开「文件管理」
2. 进入 plugins 文件夹
3. 点「上传」，选你电脑上的 NekoCore-1.2.0.jar
4. 回到「控制台」，点「启动」
   （如果服务器正在运行，先点「停止」）
5. 控制台里出现 NekoCore 1.2.0 ready → 成功
6. 点「停止」，关掉服务器
7. 回「文件管理」，进 plugins/NekoCore/，点 config.yml 右边「编辑」
8. 改这两个地方，保存：
      branding.server-name   → 你的服务器名
      survival.world         → 你主世界的名字
9. 点「启动」，进游戏打 /menu
```

**面板上要注意三件事：**

- **先停服再上传。** 服务器开着的时候换 JAR 容易出怪问题。
- **编辑时留意缩进。** 很多面板编辑器把 Tab 显示得和空格一样，看着对齐其实不是。改完服务器起不来，第一个就查这个。
- **找不到 `plugins` 文件夹？** 说明 Paper 从来没成功启动过。先点「启动」跑一次，让它生成。

**怎么知道主世界叫什么？** 在「文件管理」里打开 `server.properties`，找 `level-name=` 那一行，等号后面的就是。

</details>

<details>
<summary><b>本机 Windows 开服</b></summary>

服务器就在你自己的 Windows 电脑上，**不需要 SSH，也不需要 scp**。

假设你的服务器目录是 `D:\mc-server`：

```powershell
# 1. 把 JAR 复制进 plugins 文件夹
Copy-Item 'D:\NekoCore-1.2.0.jar' 'D:\mc-server\plugins\' -Force

# 2. 启动服务器
Set-Location 'D:\mc-server'
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

等日志里出现 `NekoCore 1.2.0 ready`，然后在同一个窗口输入 `stop` 停服。

**改配置：**

```powershell
# 用 VS Code 打开（推荐，能看出缩进问题）
code 'D:\mc-server\plugins\NekoCore\config.yml'
```

改 `branding.server-name` 和 `survival.world` 这两处，保存，然后重新启动。

**平时常用的命令：**

```powershell
# 启动服务器
Set-Location 'D:\mc-server'; java -Xms2G -Xmx2G -jar paper.jar --nogui

# 看最近 50 行日志
Get-Content 'D:\mc-server\logs\latest.log' -Tail 50

# 备份（先停服！）
$ts = Get-Date -Format 'yyyyMMdd-HHmm'
Copy-Item 'D:\mc-server\plugins\NekoCore' "D:\backup\NekoCore-$ts" -Recurse -Force
```

</details>

<details>
<summary><b>还没装 Java 和 Paper？</b></summary>

NekoCore 是插件，它需要一个跑得起来的 Paper 服务器。如果连服务器都还没有，先做这两步。

**第一步：装 Java 25**

去 [Oracle 的 Java 25 安装文档](https://docs.oracle.com/en/java/javase/25/install/)，挑适合你系统的安装方式。

装完打开 PowerShell 验证：

```powershell
java -version
```

第一行要能看到 `25`。

> **如果提示 `'java' 不是内部或外部命令`** —— Java 装好了但系统找不到它。回去看安装过程最后显示的路径，把那个 `bin` 文件夹加进系统 PATH，再回来试。

**第二步：装 Paper 26.2**

1. 去 [Paper 官方下载页](https://papermc.io/downloads/paper/)，选 **26.2**，下载 `.jar`
2. 新建一个文件夹（比如 `D:\mc-server`），把 JAR 放进去，**改名叫 `paper.jar`**
3. 打开 PowerShell，`cd` 进这个文件夹，运行：

   ```powershell
   java -Xms2G -Xmx2G -jar paper.jar --nogui
   ```

**第一次启动一定会"失败"** —— 它会停下来让你同意 Mojang 的条款。

4. 打开刚生成的 `eula.txt`，把里面的 `eula=false` 改成 `eula=true`，保存
5. 再运行一次上面的启动命令

等它跑完出现 `Done (12.345s)! For help, type "help"` 就成功了。

6. 输入 `stop` 正常停服

**现在这个文件夹就是你的「服务器目录」**，`plugins/` 也在里面了，可以回到上面第 1 步继续。

参考资料：[Paper 官方入门指南](https://docs.papermc.io/paper/admin/getting-started/) · [Paper 的 Java 安装说明](https://docs.papermc.io/paper/misc/java-install/)

</details>
