# NekoCore Public 1.1.0 — Release Notes Draft

This is an incremental update from Public 1.0.0, bringing general improvements into the existing public codebase. The Public and private version lines remain independent.

## Highlights

- **/tasks shortcut:** opens the same Daily Tasks GUI as the menu; permission `nekocore.tasks` defaults to true.
- **Configurable JoinInfo:** delayed, personal chat after profile preparation, with cache-based statistics and four optional administrator-provided links. Empty URLs are hidden; buttons only use OPEN_URL.
- **Fixed-orientation weekly leaderboard:** FIXED by default, respects yaw, supports CENTER, and preserves marked-entity cleanup and weekly accounting.
- **Improved Mascot height:** overall offset defaults to 2.25 without changing line spacing or NPC placement/skin. No Citizens or disabled Mascot means no Mascot listener/task.
- **Refined TAB:** generic `{server}` title, simple separators, compact footer; existing cache-only refresh and ownership protection remain.
- **Three-minute Tips:** default interval 180 seconds; task hints are skipped when Daily Tasks is disabled.
- **60-second AFK reward attempts:** configurable with safe fallback; five-tick detection, exit grace, probability and reward pools remain unchanged.
- **AFK end duration:** a configurable personal message once on normal online session end. Grace return continues the session; quit/reload/disable/shutdown remain silent.

## Upgrade from Public 1.0.0

Stop fully and make a matching backup of the old JAR, the whole plugin data directory, database plus any WAL/SHM, and inventory/world data. Replace only the JAR; keep your YAML and database.

The existing versioned upgrader merges missing/default fields while preserving customised values and creates original-file backups:

- config-version **8 → 9**
- messages-version **7 → 8**
- SQLite schema **5 unchanged**, with V1→V5 and no V6

JoinInfo defaults enabled, with all external URLs empty. AFK, Mascot and leaderboard remain opt-in. See the bilingual installation guide for upgrade and rollback steps.

## Verification and boundary

`mvn -B clean verify`: **BUILD SUCCESS**, 262 tests, zero failures/errors/skips. Java 25, Paper API 26.2. Ship **NekoCore-1.1.0.jar**, not the `original-` intermediate.

No new dependencies, MMDB, credentials or private deployment content are bundled. Existing credits and negative-test vocabulary are documented scan exceptions. Automated checks do not replace a disposable live Paper test for Citizens, display ownership, inventory recovery and startup/shutdown. This file is a draft; no tag, push or GitHub Release has been created.

---

# NekoCore Public 1.1.0 — 中文发布说明草稿

在 Public 1.0.0 上增量同步通用改进，不重建项目，也不将公版号改成私服版本号。

新增 `/tasks`、个人 JoinInfo 与可选网址入口；周榜默认 FIXED 并尊重 yaw；Mascot 调整整体高度；默认 TAB 更紧凑；Tips 改为 3 分钟；AFK 奖励尝试改为 60 秒，并在正常会话结束时向本人显示时长。任务关闭时跳过相关 Tips；退出、重载和停服不误发挂机结束消息。

停服并完整备份后只替换 JAR，保留自己的配置与数据库。config 8→9、messages 7→8 使用原有合并与备份机制；SQLite schema 仍为 5，V1→V5 不变。四个 JoinInfo 链接默认留空；AFK、Mascot、排行榜仍默认关闭。

完整构建通过，262 项自动测试全部通过。发布前仍需在隔离 Paper 测试服验证真实显示、NPC、交易和停服流程。当前仅交付草稿和本地产物，未 push、tag、发布 Release 或部署实服。
