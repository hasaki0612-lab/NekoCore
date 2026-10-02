# 部署命令速查

[English](../en/DEPLOY.md) | [返回 README](../../README.md) | [快速开始](QUICKSTART.md)

这一页是**命令大全**：想复制哪条就抄哪条。

> **第一次装 NekoCore？** 先看[快速开始](QUICKSTART.md)，那里是手把手步骤。这一页是装完之后回来查命令用的。

每一段都标了**在哪台机器上执行**：

| 提示符 | 你在哪 |
|---|---|
| `PS D:\>` 或 `C:\Users\你>` | **你自己的 Windows 电脑** |
| `root@server:~#` 或 `ubuntu@vps:~$` | **远程 Linux 服务器** |

**占位符对照：**

| 占位符 | 换成什么 | 例子 |
|---|---|---|
| `<服务器IP>` | 你的服务器地址 | `192.0.2.10`（示例，不是真实地址） |
| `<用户名>` | SSH 登录用户 | `root` 或 `ubuntu` |
| `<服务器目录>` | 服务器根目录的绝对路径 | `/opt/minecraft` |

**Windows 上这些命令开箱即用。** `ssh`、`scp`、`ssh-keygen`、`tar` 都随系统自带，不用额外装。PowerShell 和 CMD 都能跑。

**服务器不在远程 Linux 上？**

| 你的情况 | 去哪看 |
|---|---|
| 本机 Windows 开服 | [快速开始](QUICKSTART.md) 文末折叠块「本机 Windows」 |
| 面板服（翼龙 / MCSManager） | [快速开始](QUICKSTART.md) 文末折叠块「面板服」 |

---

## 一、上传与下载

```powershell
# 本地 → 服务器（传 JAR）
scp "D:\NekoCore-1.2.0.jar" root@<服务器IP>:<服务器目录>/plugins/

# 本地 → 服务器（传整个文件夹，-r 递归）
scp -r "D:\GeoLite2-City.mmdb" root@<服务器IP>:<服务器目录>/plugins/NekoCore/

# 服务器 → 本地（下载备份）
scp root@<服务器IP>:/root/nekocore-backup.tar.gz "D:\backup\"

# 传大文件想看进度（Windows 自带 scp 没有进度条，用 ssh + tar 更直观）
ssh root@<服务器IP> "cd /opt/minecraft/plugins && tar -czf - NekoCore/" > "D:\backup\NekoCore.tar.gz"
```

**命令结构拆开看：**

```text
scp      "本地文件路径"     用户@地址:服务器上的目标路径
 │            │                 │           │
 │            │                 │           └─ 结尾的 / 表示「放进这个目录」
 │            │                 └─ 冒号前面是登录信息
 │            └─ 路径有空格就加引号
 └─ secure copy，走的就是 SSH 那条通道
```

**上传失败常见原因：**

| 现象 | 原因 | 怎么修 |
|---|---|---|
| `No such file or directory` | 本地路径或目标目录写错 | 本地 `Get-Item` 确认；服务器 `ls` 确认 |
| `Permission denied` | 登录用户对 `plugins/` 没写权限 | 换 `root`，或先 `sudo chown -R <用户名> <服务器目录>` |
| 卡住不动 | 防火墙挡了，或 IP 写错 | 先确认 `ssh` 能连上，`scp` 走同一条路 |
| 中文路径报错 | 路径编码问题 | 把 JAR 挪到纯英文路径（如 `D:\`）再传 |

**传完验证：**

```bash
ls -lh <服务器目录>/plugins/NekoCore-1.2.0.jar
```

---

## 二、启动与停止

```bash
# --- screen 方式（最常见）---
screen -dmS minecraft java -Xms2G -Xmx2G -jar paper.jar --nogui   # 后台启动
screen -ls                                                        # 列出所有会话
screen -r minecraft                                               # 进入控制台
# 在控制台里输入 stop 停服
# 按 Ctrl+A 再按 D → 只退出控制台，不关服务器

# --- systemd 方式（重启后自动拉起）---
sudo systemctl start minecraft
sudo systemctl stop minecraft
sudo systemctl restart minecraft
sudo systemctl status minecraft
sudo journalctl -u minecraft -f

# --- 前台方式（调试用，关掉窗口就停服）---
cd <服务器目录>
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

**内存参数按需改：** `-Xms2G -Xmx2G` 是 2 GB。插件多或人多就调成 `4G`。

**确认服务器真的停了：**

```bash
ps -ef | grep -i paper | grep -v grep
```

**这条必须没有输出。**

---

## 三、看日志

```bash
tail -f <服务器目录>/logs/latest.log                    # 实时跟看（Ctrl+C 退出）
tail -100 <服务器目录>/logs/latest.log                  # 最后 100 行
grep -a "NekoCore" <服务器目录>/logs/latest.log         # 只看 NekoCore 相关
grep -aE "ERROR|WARN" <服务器目录>/logs/latest.log      # 只看错误和警告
```

```powershell
# 本机 Windows
Get-Content 'D:\mc-server\logs\latest.log' -Tail 50
Select-String -Path 'D:\mc-server\logs\latest.log' -Pattern 'NekoCore' | Select-Object -Last 20
```

**要确认加载成功，找这一行：**

```text
NekoCore 1.2.0 ready
```

---

## 四、改配置

```bash
cd <服务器目录>/plugins/NekoCore
cp config.yml config.yml.bak       # 改之前先备份一份，很省事
nano config.yml                    # Ctrl+O 保存，回车确认，Ctrl+X 退出

grep -Pn '\t' config.yml           # 找 Tab（应该没有输出）
```

**只需要改这两个地方就能开服：**

```yaml
branding:
  server-name: "My Server"     # ← 你的服务器名
survival:
  world: world                 # ← 主世界的实际文件夹名
```

```bash
# 主世界叫什么
grep '^level-name' <服务器目录>/server.properties
```

**游戏内验证与应用：**

```text
/nekocore config check     ← 只检查，不应用任何改动
/nekocore reload           ← 确认没问题了，真正生效
```

| 改动类型 | 生效方式 |
|---|---|
| 名字、数值、开关、文字 | `/nekocore reload` |
| 世界名、坐标、Material | `/nekocore reload` |
| 加入或更换 MMDB 文件 | `/nekocore reload`（它会重新打开该文件） |
| `database.filename` | **完整重启** |
| 更换 JAR | **完整重启** |
| 安装新的第三方插件 | **完整重启** |
| 世界插件的变更 | **完整重启** |

---

## 五、备份与恢复

```bash
# --- 备份（先停服！）---
cd <服务器目录>/plugins
tar -czf ~/nekocore-$(date +%Y%m%d-%H%M).tar.gz NekoCore/

# 看备份里有什么
tar -tzf ~/nekocore-*.tar.gz

# --- 恢复（先停服！）---
cd <服务器目录>/plugins
mv NekoCore NekoCore.old                # 先把现在的挪开，别直接覆盖
tar -xzf ~/nekocore-20261001-1530.tar.gz
ls -l NekoCore/                         # 确认文件都在
```

```powershell
# 本机 Windows 备份（先停服！）
$ts = Get-Date -Format 'yyyyMMdd-HHmm'
Copy-Item 'D:\mc-server\plugins\NekoCore' "D:\backup\NekoCore-$ts" -Recurse -Force
Get-ChildItem "D:\backup\NekoCore-$ts" | Select-Object Name, Length
```

**打包整个 `NekoCore/` 目录**，就不会漏掉 `nekocore.db-wal` 和 `nekocore.db-shm`——这两个必须和 `.db` 来自同一时点。

---

## 六、校验文件完整性

```bash
# 服务器上
unzip -l <服务器目录>/plugins/NekoCore-1.2.0.jar | head -20   # 能列出内容 = 完整 zip
sha256sum <服务器目录>/plugins/NekoCore-1.2.0.jar             # 和发布页比对
```

```powershell
# 本地
Get-Item 'D:\NekoCore-1.2.0.jar' | Select-Object Name, Length
Get-FileHash 'D:\NekoCore-1.2.0.jar' -Algorithm SHA256
```

两边大小不一致 = 没传完，删掉服务器上那个重传：

```bash
rm <服务器目录>/plugins/NekoCore-1.2.0.jar
```

---

## 七、免密登录

```powershell
# 1. 生成密钥（一路回车，不用设密码）
ssh-keygen -t ed25519

# 2. 把公钥装到服务器上（会要一次密码）
type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh root@<服务器IP> "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys && chmod 700 ~/.ssh"

# 3. 测试：这次不该再问密码
ssh root@<服务器IP> "echo 免密登录成功"
```

之后 `scp` 和 `ssh` 都不用再输密码。

> **公钥可以给别人，私钥（`id_ed25519`，没有 `.pub` 后缀的那个）绝对不能。**

---

## 八、排错速查

按「症状 → 诊断命令 → 对号入座」组织。

### 连不上服务器

```powershell
# 本地测试端口通不通
Test-NetConnection <服务器IP> -Port 22      # SSH 端口
Test-NetConnection <服务器IP> -Port 25565   # 游戏端口
```

`TcpTestSucceeded : True` 才是通的。`False` = 防火墙或安全组没放行。

### 日志里没有 NekoCore ready

```bash
grep -aE "ERROR|Exception|NekoCore" <服务器目录>/logs/latest.log | tail -40
```

| 日志里看到 | 意思 | 怎么办 |
|---|---|---|
| `Unsupported class file major version` | Java 版本太低 | `java -version` 确认是 25 |
| 完全搜不到 `NekoCore` | JAR 没放对位置 | `ls <服务器目录>/plugins/ \| grep -i neko` |
| `Unknown/missing dependency` | 装了不兼容的版本 | 确认是给 Paper 26.2 的构建 |
| 一堆 `Caused by` | 看**第一条**就行 | 后面通常是连锁反应 |

### 服务器起不来 / 立刻退出

```bash
# 前台跑一次，让报错直接打在屏幕上
cd <服务器目录>
java -Xms2G -Xmx2G -jar paper.jar --nogui
```

```bash
cat <服务器目录>/eula.txt              # 检查 eula 是不是 true
ss -tlnp | grep 25565                  # 检查端口占用
```

### 配置改完没生效

```text
/nekocore config check     ← 只检查
/nekocore reload           ← 真正生效
```

**两个都成功但游戏里没变化？** 检查是不是属于「reload 覆盖不到」的改动（见上面**改配置**一节的表格）。

**改坏配置不会让服务器下线**——重载失败时它会继续用上一份有效配置跑，只在控制台告诉你哪里错了。

### YAML 报错但看不出哪错了

```bash
grep -Pn '\t' <服务器目录>/plugins/NekoCore/config.yml        # 找 Tab
grep -n -A5 -B2 'branding' <服务器目录>/plugins/NekoCore/config.yml   # 看上下文
```

**第一条必须没有输出。**

### 权限 / 属主混乱

```bash
ls -ld <服务器目录> <服务器目录>/plugins <服务器目录>/plugins/NekoCore
sudo chown -R minecraft:minecraft <服务器目录>     # 改成跑服务器的那个账号
```

---

## 九、下一步

| 你想做什么 | 看哪份 |
|---|---|
| 手把手第一次开服 | [快速开始](QUICKSTART.md) |
| 改签到奖励、商店价格 | [配方手册](RECIPES.md) |
| 搞懂每个字段 | [配置手册](CONFIGURATION.md) |
| 某个功能没反应 | [兼容性与常见问题](COMPATIBILITY.md) |
| 升级或回滚 | [安装与升级](INSTALLATION.md) |
| 备份与恢复的完整流程 | [数据库](DATABASE.md) |
