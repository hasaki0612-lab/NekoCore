# 场景预设

[English](../en/PRESETS.md) | [快速开始](QUICKSTART.md) | [配置手册](CONFIGURATION.md)

这是三份**完整 config.yml**，不是只包含差异的片段。默认配置本身已经能运行基础生存服，预设是可选的起点，不是安装前置步骤。

## 文件在哪里

JAR 内置在 `presets/`；启动时复制到 `plugins/NekoCore/presets/`。只补不存在的文件，已存在的预设（包括你改过的内容）永不自动覆盖。不会替换正在使用的 `config.yml` 或 `messages.yml`，也不修改数据库。目录无写入权限会警告，不因此中断核心加载。

| 文件 | 适用场景 | 相对默认配置的区别 |
| --- | --- | --- |
| `survival-only.yml` | 只有一个主世界的新生存服 | 默认配置，世界名 `world` |
| `friends-server.yml` | 朋友小服 | 仅服务器显示名改为「朋友小服」；没有暗改奖励 / 价格 |
| `lobby-survival.yml` | 已经有大厅与生存世界 | 主入口 `lobby`、第二入口 `survival`；世界限制及菜单文字一并匹配 |

所有预设保留资料、经济、菜单、签到、Home、Store、Bag、TPN、任务、TAB、Tips 等内部核心。GeoIP、Mascot、小游戏、AFK、周榜仍关闭；AFK / 周榜位置确认锁仍为 false，不推测坐标，不自行生成全息显示。

## 新服：选择一种起点

1. 用默认配置启动一次，确认能进游戏打开 `/menu`；随后完整停服。
2. 备份 `plugins/NekoCore/config.yml`（如果已有玩家，还要做完整停服备份）。把选中的预设**复制**为 `plugins/NekoCore/config.yml`，保留原预设文件。
3. 修改服务器名字和真实世界名。数据库文件名、config-version 不要顺手改。
4. 正常启动，执行 `/nekocore config check`，验证菜单、商店、任务。以后修改先检查再重载；更換 JAR / 世界插件须重启。

## 旧服：不要整份覆盖

已有自定义价格、奖励、商品、世界限制和布局时，只对照预设逐项编辑原文件。替换整个配置会丢掉你的定制；预设不会为你自动合并。保留自己的 messages.yml 和 SQLite 数据，先备份再改。

1.2.0 保持 config **9**、messages **8**、SQLite **5**。旧文件缺少 lookup 文案时沿用原消息加载器在内存中补默认值，不重写管理员文字或 YAML 注释。已有预设也不随升级刷新；需新版本内容时从源码 `src/main/resources/presets/` 对照获取。

## 大厅 + 生存：必须先有真实世界

`lobby-survival.yml` **不创建 / 自动发现世界**。`lobby` 和 `survival` 必须已经存在且由 Paper 或你的世界插件加载；实际名字不同就修改预设的入口、Home/头衔允许世界和 Bag writable-worlds。没有 Multiverse-Core 时，已加载世界走现有 Paper 出生点回退；没有加载的世界不会出现可用入口。预设不猜传送坐标，也不额外启用小游戏。

## 维护与验证

预设来自当前默认配置，保留全部字段和版本号。测试比较每份预设与默认配置的完整键集合，只允许上表对应的少量字段不同，再经过 Settings、FeatureSettings、DailyTaskSettings 和共用验证入口。默认字段新增或改名而预设未同步会使测试失败，不会静默漂移。

## 常见误解

- 不需要把 status 的全部 DISABLED 都变成 ENABLED；它们多半是你不需要的可选功能。
- 查询英文图标用 `/nekocore lookup`；预设不是中文物品名翻译库。
- 本轮没有 `/nekocore set` 自动写入命令；编辑原 YAML、检查、重载即可。
