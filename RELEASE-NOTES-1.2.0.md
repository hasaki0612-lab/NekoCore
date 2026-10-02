# NekoCore Public 1.2.0 — release-note draft

## 简体中文

在原 Public 1.1.0 上增量优化使用门槛：配置错误更易懂，可靠时给出行号；管理员可拿着物品使用 `/nekocore lookup` 查英文名，支持关键词和生物查询。提供单生存、大厅 + 生存、朋友小服三个完整预设，缺失时复制、从不覆盖或自动应用。新生成配置才显示五行指引；doctor 保留原模块行并解释 DISABLED。

已有 Markdown 指南和折叠章节原位补充。运行文案保持简体中文，英文使用说明在 docs/en；不新增语言系统或自动写 YAML 的 set 命令。

升级请完整停服备份，只替换 JAR，保留配置和玩家数据。config 9、messages 8、SQLite 5 不变，旧自定义文字不被重写；新 lookup 默认文案在内存中补全。无新依赖、无 V6，稳定 ID / PDC / PAPI 名称空间、Store/Bag 恢复模型不变。

`mvn -B clean verify`：285 项测试，0 失败 / 错误 / 跳过。自动化不是实服验证；正式使用前按 VERIFICATION 在备用 Paper 26.2 / Java 25 上验证菜单、物品、重载和可选插件。本文件只是草稿，没有创建 GitHub Release。

## English

Incremental usability improvements on Public 1.1.0: readable diagnostics with reliable source lines, admin held-item/English-keyword/entity lookup, three complete safe presets copied only when missing, fresh-config-only guidance, and an appended DISABLED explanation preserving stable doctor lines.

Existing bilingual Markdown is extended in place. Runtime text remains Chinese; no runtime i18n or automatic YAML-writing set command is introduced. Stop and back up before replacing the JAR; retain config and player data. Config 9, messages 8 and SQLite 5 stay unchanged; optional lookup text falls back in memory. No dependencies/V6 or stable namespace/ID/recovery changes.

Java 25 clean verify passed 285 tests with zero failures/errors/skips. This is not live-server validation; follow VERIFICATION on a disposable Paper 26.2 server before production use. This is a draft, not a published GitHub Release.
