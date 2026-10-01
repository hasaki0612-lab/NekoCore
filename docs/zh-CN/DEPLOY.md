# 部署命令手册

[English](../en/DEPLOY.md) | [返回 README](../../README.md)

这一页只干一件事：**给你可以直接复制的完整命令。**

不讲原理，不绕弯子。每一段都告诉你「在哪台机器上执行」「这条命令在做什么」「跑完应该看到什么」。

前提假设：服务器上已经有一台装好 **Paper 26.2** 的实例，现在只是把 NekoCore 装进去。如果连 Paper 都还没装，先看[快速开始](QUICKSTART.md)的前两步。

---

## 先读这一小段（30 秒）

**命令里需要你替换的地方，统一用尖括号标出来：**

| 占位符 | 换成什么 | 例子 |
| --- | --- | --- |
| `<服务器IP>` | 你的服务器地址 | `192.0.2.10`（示例，不是真实地址） |
| `<用户名>` | SSH 登录用户 | `root` 或 `ubuntu` |
| `<服务器目录>` | 服务器根目录的绝对路径 | `/opt/minecraft` |
| `<本地目录>` | 你电脑上放 JAR 的文件夹 | `D:\` |

**怎么分辨在哪台机器上跑：**

- 提示符长这样 → `C:\Users\你>` 或 `PS D:\>` → **你自己的电脑（Windows）**
- 提示符长这样 → `root@server:~#` 或 `ubuntu@vps:~$` → **远程 Linux 服务器**

**Windows 上这些命令开箱即用。** `ssh`、`scp`、`ssh-keygen`、`tar` 都随系统自带（OpenSSH），不用额外装东西。用 **PowerShell** 或 **CMD** 都行，本文的命令两边通吃。

---

## 一、远程 Linux 服务器（主线）

这是最常见的场景：服务器在 VPS 上，你在 Windows 电脑上操作。

### 第 0 步：先确认你本地有 JAR

> **在本地 Windows 上执行**

```powershell
# 看看文件在不在，顺便确认大小（正常约 16.6 MiB）
Get-Item 'D:\NekoCore-1.0.0.jar' | Select-Object Name, Length, LastWriteTime
```

应该输出一行，`Length` 是 `17428288` 左右。

如果提示找不到文件，先把 JAR 下载/复制到 `D:\`，或者把命令里的路径改成你实际存放的位置。

**顺手验一下完整性**（可选但推荐，避免传到一半的坏文件）：

```powershell
Get-FileHash 'D:\NekoCore-1.0.0.jar' -Algorithm SHA256
```

和发布页给出的 SHA-256 对比，一致就说明文件没坏。

### 第 1 步：登录服务器

> **在本地 Windows 上执行**

```powershell
ssh <用户名>@<服务器IP>
```

实际例子：

```powershell
ssh root@192.0.2.10
```

第一次连接会问：

```text
The authenticity of host '192.0.2.10' can't be established.
ED25519 key fingerprint is SHA256:...
Are you sure you want to continue connecting (yes/no/[fingerprint])?
```

输入 `yes` 回车。然后输入密码（**输入时屏幕上不会显示任何字符，这是正常的**，打完直接回车）。

看到提示符变成 `root@xxx:~#` 就成功了。**接下来所有标着「在服务器上执行」的命令，都在这个窗口里跑。**

### 第 2 步：确认服务器环境和目录

> **在服务器上执行**

```bash
# Java 版本必须是 25
java -version

# 找到你的服务器目录（下面是常见位置，挑存在的那个）
ls -d /opt/minecraft /srv/minecraft /root/minecraft ~/minecraft 2>/dev/null

# 确认 plugins 文件夹在（把 <服务器目录> 换成上一步找到的路径）
ls -l <服务器目录>/plugins
```

`java -version` 的第一行要能看到 `25`。看不到就先装 Java 25，本文不展开。

`ls <服务器目录>/plugins` 应该列出你已有的插件。**如果提示 `No such file or directory`**，说明路径不对，或者 Paper 从来没启动过——先用下面的命令启动一次 Paper 生成目录结构。

**如果服务器正在运行，先停掉，避免边改边跑：**

```bash
# 方法一：在控制台里停（推荐）
#   如果你用 screen：        screen -r minecraft   然后输入 stop
#   如果你用 systemd：       sudo systemctl stop minecraft
#   如果你直接前台跑：       在跑服务器的窗口按 Ctrl+C 不行，要输入 stop

# 方法二：直接看进程，确认是不是真的停了
ps -ef | grep -i paper | grep -v grep
```

**这条命令必须没有输出**，才说明服务器停干净了。还有输出就说明进程还在。

### 第 3 步：上传 JAR

**这一步回到你本地的 Windows 上开一个新窗口**（原来的 SSH 窗口可以留着，一会儿还要用）。

> **在本地 Windows 上执行**

```powershell
scp "D:\NekoCore-1.0.0.jar" <用户名>@<服务器IP>:<服务器目录>/plugins/
```

实际例子：

```powershell
scp "D:\NekoCore-1.0.0.jar" root@192.0.2.10:/opt/minecraft/plugins/
```

**命令结构拆开看：**

```text
scp      "本地文件路径"            用户@地址:服务器上的目标路径
 │            │                        │              │
 │            │                        │              └─ 结尾的 / 表示「放进这个目录」
 │            │                        └─ 冒号前面是登录信息
 │            └─ 路径有空格就加引号；没有空格也可以加，加了更保险
 └─ secure copy，走的就是 SSH 那条通道
```

**常见坑：**

| 现象 | 原因 | 怎么修 |
| --- | --- | --- |
| `No such file or directory` | 本地路径写错，或者目标目录不存在 | 本地用 `Get-Item` 确认；服务器上用 `ls` 确认目录 |
| `Permission denied` | 登录用户对 `plugins/` 没有写权限 | 换 `root`，或者先 `sudo chown -R <用户名> <服务器目录>` |
| 卡住不动很久 | 服务器防火墙挡了，或者 IP 写错 | 先确认 `ssh` 能连上，`scp` 走同一条路 |
| 中文路径报错 | 路径里的中文没被正确解析 | 把 JAR 挪到纯英文路径（比如 `D:\`）再传 |

**上传完验证一下：**

> **在服务器上执行**

```bash
ls -lh <服务器目录>/plugins/NekoCore-1.0.0.jar
```

看到文件、大小是 `17M` 左右就对了。

> **如果服务器在跑，记得先停服再传。** 传 JAR 的同时服务器在读 `plugins/`，容易出怪问题。

### 第 4 步：启动服务器

> **在服务器上执行**

```bash
cd <服务器目录>
```

然后按你平时的启动方式启动。三种常见情况：

```bash
# 情况 A：用 screen 挂着跑（最常见）
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui
screen -r minecraft          # 进入控制台看输出；按 Ctrl+A 然后 D 退出但不关闭

# 情况 B：用 systemd 托管（重启后自动拉起）
sudo systemctl start minecraft
sudo journalctl -u minecraft -f      # 实时看日志；按 Ctrl+C 退出查看

# 情况 C：直接前台跑（调试用，关掉窗口就停服）
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

**内存参数按需改：** `-Xms2G -Xmx2G` 是 2 GB。朋友服够用；插件多或人多就调成 `4G`，同时确保服务器本身内存够（大致留一倍给系统）。

### 第 5 步：确认 NekoCore 真的加载了

> **在服务器上执行**

```bash
# 看日志里有没有 ready（日志文件方式）
grep -a "NekoCore" <服务器目录>/logs/latest.log | tail -20

# 如果是 screen：进入控制台直接看
screen -r minecraft
```

**你要找的这一行：**

```text
NekoCore 1.0.0 ready
```

它前面应该还有类似 `schema 5` 的迁移记录。**看到 `ready` 就成功了。**

同时确认目录被创建出来：

```bash
ls -lh <服务器目录>/plugins/NekoCore/
```

应该看到三个文件：

```text
config.yml       ← 服务器行为配置
messages.yml     ← 玩家看到的文字
nekocore.db      ← 数据（别手动改）
```

**没看到 `ready`？** 跳到最后面的[排错速查](#四排错速查)。

### 第 6 步：改配置（最少改两个地方）

**先停服：**

```bash
screen -r minecraft      # 进入控制台
# 输入 stop 回车，等它完全退出
```

**然后编辑：**

```bash
cd <服务器目录>/plugins/NekoCore
nano config.yml
```

`nano` 是最省事的编辑器。**保存退出：`Ctrl+O` → 回车 → `Ctrl+X`。**

要改这两处：

```yaml
branding:
  server-name: "My Server"        # ← 改成你的服务器名

survival:
  enabled: true
  world: world                    # ← 改成你主世界的实际文件夹名
```

**主世界叫什么？** 看 `server.properties`：

```bash
grep '^level-name' <服务器目录>/server.properties
```

输出的名字要和 `survival.world` 完全一致，**大小写也要一致**。

保存后确认一下没改坏：

```bash
# 检查缩进有没有变成 Tab（YAML 只能用空格）
grep -Pn '\t' config.yml
```

**这条命令必须没有输出。** 有输出就说明某行混进了 Tab，回去用空格重打那一行。

### 第 7 步：重启并验证

> **在服务器上执行**

```bash
cd <服务器目录>
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui
screen -r minecraft
```

进游戏后依次试：

```text
/menu
/checkin
/sethome
/home
/coins
```

管理员再跑这两条：

```text
/nekocore status
/nekocore config check
```

**装完收工。** 后面的内容是「想省事」和「出问题」时才需要看。

### 进阶：配置免密登录（可选，但很省事）

每次 `scp` 都要输密码很烦。三行命令解决：

> **在本地 Windows 上执行**

```powershell
# 1. 生成密钥（一路回车即可，不用设密码）
ssh-keygen -t ed25519

# 2. 把公钥装到服务器上（会要一次密码）
type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh <用户名>@<服务器IP> "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && chmod 700 ~/.ssh"

# 3. 测试：这次应该不用输密码了
ssh <用户名>@<服务器IP> "echo 免密登录成功"
```

之后 `scp` 和 `ssh` 都不用再输密码。**公钥可以给别人，私钥（`id_ed25519`，没有 `.pub` 后缀的那个）绝对不能。**

---

## 二、本机 Windows 开服

服务器就在你自己的 Windows 电脑上，不涉及 SSH 和 scp。

### 快速版：四行命令

> **在 PowerShell 里执行**

```powershell
# 假设服务器目录是 D:\mc-server，JAR 在 D:\
Copy-Item 'D:\NekoCore-1.0.0.jar' 'D:\mc-server\plugins\' -Force
Set-Location 'D:\mc-server'
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

等日志里出现 `NekoCore 1.0.0 ready`，然后在控制台输入：

```text
stop
```

### 完整流程

```powershell
# --- 1. 准备：确认服务器目录和 plugins 文件夹存在 ---
$server = 'D:\mc-server'                      # ← 改成你的服务器目录
Test-Path "$server\paper.jar"                 # 应该是 True
Test-Path "$server\plugins"                   # 应该是 True；False 的话先启动一次 Paper

# --- 2. 确认服务器没在运行（有输出就说明还在跑）---
Get-Process java -ErrorAction SilentlyContinue | Select-Object Id, ProcessName, StartTime

# --- 3. 复制 JAR ---
Copy-Item 'D:\NekoCore-1.0.0.jar' "$server\plugins\" -Force

# --- 4. 确认复制成功 ---
Get-Item "$server\plugins\NekoCore-1.0.0.jar" | Select-Object Name, Length

# --- 5. 启动 ---
Set-Location $server
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

**看到 `NekoCore 1.0.0 ready` 后，在同一个窗口输入 `stop` 回车**，等它完全退出。

### 改配置

```powershell
# --- 1. 用记事本打开（简单；但注意保存为 UTF-8）---
notepad "$server\plugins\NekoCore\config.yml"

# --- 2. 或者用 VS Code（推荐，能看出缩进问题）---
code "$server\plugins\NekoCore\config.yml"
```

改完这两处：

```yaml
branding:
  server-name: "猫猫服"

survival:
  world: world          # ← 要和 server.properties 里的 level-name 一致
```

确认主世界名字：

```powershell
Select-String -Path "$server\server.properties" -Pattern '^level-name'
```

检查有没有误用 Tab：

```powershell
Select-String -Path "$server\plugins\NekoCore\config.yml" -Pattern "`t"
```

**没有输出就是对的。**

然后重新启动服务器即可。

### 经常要用的几条

```powershell
# 启动
Set-Location 'D:\mc-server'; java -Xms2G -Xmx2G -jar paper.jar --nogui

# 看最近日志
Get-Content 'D:\mc-server\logs\latest.log' -Tail 50

# 备份（先停服！）
$ts = Get-Date -Format 'yyyyMMdd-HHmm'
Copy-Item 'D:\mc-server\plugins\NekoCore' "D:\backup\NekoCore-$ts" -Recurse -Force

# 确认备份里有什么
Get-ChildItem "D:\backup\NekoCore-$ts" | Select-Object Name, Length
```

---

## 三、面板服（翼龙 / MCSManager 等）

面板服不用敲命令，全程点鼠标。这里的「命令」是**填进面板输入框**的内容。

### 通用流程

```text
1. 面板里找到「文件管理」
2. 进入 /plugins 目录
3. 点「上传」，选择本地的 NekoCore-1.0.0.jar
4. 回到控制台，点「启动」（如果已经在跑，先点「停止」）
5. 在控制台看有没有出现 NekoCore 1.0.0 ready
6. 停止服务器
7. 文件管理进 /plugins/NekoCore/，点 config.yml 的「编辑」
8. 改 branding.server-name 和 survival.world，保存
9. 启动服务器，进游戏打 /menu
```

### 面板上的注意事项

**「上传」和「启动」的顺序。** 先停服再上传，不要边跑边换 JAR。

**编辑 YAML 用面板自带的编辑器时，留意缩进。** 很多面板编辑器会把 Tab 显示得和空格一样。改完保存后，如果服务器起不来，第一件事就是检查缩进。

**找不到 `plugins` 文件夹？** 说明 Paper 还没成功启动过。先启动一次，让它生成目录结构。

**翼龙（Pterodactyl）面板：**

```text
文件 → /plugins → Upload → 选 JAR
Console → Start
```

**MCSManager 面板：**

```text
文件管理 → 进入 plugins → 上传文件
终端/控制台 → 启动
```

**面板不给文件管理权限，只有控制台？** 那用面板的「上传到根目录」功能，然后在控制台执行移动命令（部分面板提供 `cmd` 入口）：

```bash
mv /NekoCore-1.0.0.jar /plugins/NekoCore-1.0.0.jar
```

---

## 四、排错速查

按「症状 → 原因 → 命令」组织。**每条都先给你能直接跑的诊断命令。**

### 连不上服务器

```powershell
# 本地测试端口通不通（把 25565 换成你的端口）
Test-NetConnection <服务器IP> -Port 22      # SSH 端口
Test-NetConnection <服务器IP> -Port 25565   # 游戏端口
```

`TcpTestSucceeded : True` 才是通的。`False` 说明防火墙或安全组没放行。

### 上传报 Permission denied

> **在服务器上执行**

```bash
ls -ld <服务器目录>/plugins
whoami
```

看清楚 `plugins` 目录归谁所有。当前用户不在所有者/组里，就用：

```bash
sudo chown -R $(whoami) <服务器目录>/plugins
```

然后再从本地重传。

### 传到一半断了 / 文件大小不对

```bash
# 服务器上：看实际大小
ls -lh <服务器目录>/plugins/NekoCore-1.0.0.jar
```

```powershell
# 本地：看正确大小和哈希
Get-Item 'D:\NekoCore-1.0.0.jar' | Select-Object Length
Get-FileHash 'D:\NekoCore-1.0.0.jar' -Algorithm SHA256
```

两边大小不一致就是没传完，删掉服务器上那个文件重传：

```bash
rm <服务器目录>/plugins/NekoCore-1.0.0.jar
```

### 日志里没有 NekoCore ready

> **在服务器上执行**

```bash
# 看有没有 ERROR 或异常
grep -aE "ERROR|Exception|NekoCore" <服务器目录>/logs/latest.log | tail -40
```

**按出现的内容对号入座：**

| 日志里看到 | 意思 | 怎么办 |
| --- | --- | --- |
| `Unsupported class file major version` | Java 版本太低 | `java -version` 确认是 25 |
| 完全搜不到 `NekoCore` | JAR 没放对位置 | `ls <服务器目录>/plugins/ \| grep -i neko` |
| `Unknown/missing dependency` | 装了不兼容的版本 | 确认是给 Paper 26.2 的构建 |
| 一堆 `Caused by` | 看第一条就行 | 后面通常是连锁反应 |

### 服务器起不来 / 立刻退出

```bash
# 前台跑一次，让报错直接打在屏幕上
cd <服务器目录>
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

报错会直接显示。**最常见的两个原因：** `eula.txt` 里还是 `false`；或者端口被占用。

```bash
# 检查 eula
cat <服务器目录>/eula.txt

# 检查端口占用
ss -tlnp | grep 25565
```

### 配置改完没生效

**先确认配置本身是合法的，再让它生效——这是两条不同的命令：**

```text
/nekocore config check     ← 只检查，不改任何东西
/nekocore reload           ← 检查通过后真正切换
```

**如果 `config check` 报错**，看它给的那个 YAML 路径（比如 `store.products.bred.material`），照着改。

**如果两个都成功了但游戏里没变化**，检查是不是属于「reload 覆盖不到」的改动：换 JAR、改数据库文件名、装新插件、改世界插件——这些要完整重启。

**改坏配置不会让服务器下线。** 重载失败时它会继续用上一份有效配置跑，只在控制台告诉你哪里错了。

### YAML 报错但看不出哪错了

```bash
# 找 Tab（YAML 只允许空格）
grep -Pn '\t' <服务器目录>/plugins/NekoCore/config.yml

# 看某个字段前后几行
grep -n -A5 -B2 'branding' <服务器目录>/plugins/NekoCore/config.yml
```

**没有输出就是干净的。** 有输出就把那些行的 Tab 换成空格。

### 权限 / 属主混乱

```bash
# 看目录归谁
ls -ld <服务器目录> <服务器目录>/plugins <服务器目录>/plugins/NekoCore

# 一次性修正（把用户和组改成跑服务器的那个账号）
sudo chown -R minecraft:minecraft <服务器目录>
```

如果是 `root` 跑服务器，那 `root` 拥有就够了。

### 想备份，但不确定有没有漏文件

```bash
# 先停服，然后整个目录打包（一条命令带走 .db / .db-wal / .db-shm / 配置）
cd <服务器目录>/plugins
tar -czf ~/nekocore-backup-$(date +%Y%m%d).tar.gz NekoCore/

# 确认包里有什么
tar -tzf ~/nekocore-backup-$(date +%Y%m%d).tar.gz
```

**打包整个 `NekoCore/` 目录**，就不会漏掉 `nekocore.db-wal` 和 `nekocore.db-shm`。这两个文件和 `.db` 必须来自同一个时点。

---

## 五、命令速查表

按「我想……」找。**`<服务器目录>` 记得替换。**

### 上传与下载

```powershell
# 本地 → 服务器（传单个文件）
scp "D:\NekoCore-1.0.0.jar" root@<服务器IP>:/opt/minecraft/plugins/

# 本地 → 服务器（传整个文件夹，-r 递归）
scp -r "D:\GeoLite2-City.mmdb" root@<服务器IP>:/opt/minecraft/plugins/NekoCore/

# 服务器 → 本地（下载备份）
scp root@<服务器IP>:/root/nekocore-backup.tar.gz "D:\backup\"

# 传大文件时显示进度（Windows 自带 scp 没有进度条，用 ssh + tar 更直观）
ssh root@<服务器IP> "cd /opt/minecraft/plugins && tar -czf - NekoCore/" > "D:\backup\NekoCore.tar.gz"
```

### 服务控制

```bash
# screen 方式
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui   # 后台启动
screen -ls                                                        # 列出所有会话
screen -r minecraft                                               # 进入控制台
# 在控制台里输入 stop 停服；按 Ctrl+A 再按 D 只退出不关闭

# systemd 方式
sudo systemctl start minecraft
sudo systemctl stop minecraft
sudo systemctl restart minecraft
sudo systemctl status minecraft
sudo journalctl -u minecraft -f
```

### 看日志

```bash
tail -f <服务器目录>/logs/latest.log                    # 实时跟看（Ctrl+C 退出）
tail -100 <服务器目录>/logs/latest.log                  # 最后 100 行
grep -a "NekoCore" <服务器目录>/logs/latest.log         # 只看 NekoCore 相关
grep -aE "ERROR|WARN" <服务器目录>/logs/latest.log      # 只看错误和警告
```

### 改配置

```bash
cd <服务器目录>/plugins/NekoCore
nano config.yml                    # 编辑；Ctrl+O 保存，回车确认，Ctrl+X 退出
grep -Pn '\t' config.yml           # 找 Tab（应该没有输出）
cp config.yml config.yml.bak       # 改之前先备份一份，很省事
```

### 备份与恢复

```bash
# 备份（先停服）
cd <服务器目录>/plugins
tar -czf ~/nekocore-$(date +%Y%m%d-%H%M).tar.gz NekoCore/

# 看备份内容
tar -tzf ~/nekocore-*.tar.gz

# 恢复（先停服）
cd <服务器目录>/plugins
mv NekoCore NekoCore.old                              # 先把现在的挪开，别直接覆盖
tar -xzf ~/nekocore-20261001-1530.tar.gz              # 解出 NekoCore/ 目录
ls -l NekoCore/                                       # 确认文件都在
```

### 检查 JAR 是否完整

```bash
# 服务器上
unzip -l <服务器目录>/plugins/NekoCore-1.0.0.jar | head -20   # 能列出内容说明是完整 zip
sha256sum <服务器目录>/plugins/NekoCore-1.0.0.jar             # 和发布页比对
```

```powershell
# 本地
Get-FileHash 'D:\NekoCore-1.0.0.jar' -Algorithm SHA256
```

---

## 六、下一步

**装完了，想改点什么**
→ [配方手册](RECIPES.md)，「我想改签到奖励」这种一句话目标。

**想搞懂每个字段**
→ [配置手册](CONFIGURATION.md)。

**装了但某个功能没反应**
→ [兼容性与常见问题](COMPATIBILITY.md)。

**要升级或回滚**
→ [安装与升级](INSTALLATION.md)。

**要备份和恢复**
→ [数据库](DATABASE.md)。
