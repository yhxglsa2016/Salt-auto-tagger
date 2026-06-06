# Salt Auto Tagger Mod

Common development commands: see [COMMANDS.md](COMMANDS.md).

## 中文

`Salt Auto Tagger` 是一个面向 `Salt Player for Windows` 的歌词补全与修正插件。

这个版本的目标是：

- 以纯 SPW 插件方式运行，不依赖 `music-tag-web` 服务启动。
- 不依赖 Docker、Django、Python 后台服务。
- 直接在插件内部集成在线歌词查询、本地歌词读取、保存和诊断能力。

### 当前能力

- 优先读取歌曲同目录 `.lrc/.txt` 歌词。
- 支持读取插件覆盖目录中的修正歌词。
- 内置 3 个在线歌词源：
  - 酷狗
  - QQ 音乐
  - 网易云
- 默认在线查询补齐顺序为：`Kugou -> QQ Music -> Netease`。
- 支持 3 个在线来源顺位：`lyrics.source_order.rank_1` 到 `lyrics.source_order.rank_3`。
- 支持每个歌词源单独启用或关闭。
- 支持保守搜索词清洗，提高常见脏标题的在线命中率，同时保留原始标题作为第一搜索词。
- 支持三种歌词保存策略：
  - 仅加载显示
  - 保存为同目录 `.lrc`
  - 写回歌曲标签
- 支持复制覆盖目录、打开覆盖目录、查看最近日志、复制日志路径、清空日志和查看插件版本。

### 设置页结构

设置页采用“首页入口列表 -> 二级配置页”的组织方式：

- 基础设置：语言、启用歌词修正、歌词接管方式、显示提示消息。
- 歌词来源：在线查询开关、来源顺位 1-3、各歌词源开关。
- 保存与覆盖：同目录歌词、覆盖目录、保存策略、覆盖目录名、路径操作。
- 调试工具：调试日志、源检测、最近日志、日志路径、清空日志、插件版本。

### 调试日志

启用调试日志后，插件会把关键阶段写入日志文件。日志路径可在设置页中复制。

新日志使用稳定的 `event | key=value` 格式，便于定位“哪个阶段、哪个来源、为什么失败”。典型事件包括：

- `search_attempt`
- `source_success`
- `source_failed`
- `save_failed`
- `probe_source_failed`

示例：

```text
2026-06-06T20:30:00 [INFO] search_attempt | source=Kugou | keyword=认真的雪 | candidates=3
2026-06-06T20:30:01 [WARN] source_failed | source=Netease | reason=no_usable_lyrics
```

### 构建方式

```bash
./gradlew plugin
```

如果本机 `JAVA_HOME` 没有正确配置，可以直接使用：

```bat
build-local.cmd
```

打包产物会生成在 `build/libs/`。

### 说明

`music-tag-web-dev_1.0` 在本项目中仅作为歌词源实现参考。插件不会依赖运行中的 `music-tag-web` Web API。

## English

`Salt Auto Tagger` is a lyrics completion and correction plugin for `Salt Player for Windows`.

This version is designed to:

- run as a pure SPW plugin
- avoid depending on a running `music-tag-web` service
- avoid Docker, Django, or Python background services
- embed online lyrics source clients directly inside the plugin

### Current Capabilities

- read sidecar `.lrc/.txt` files first
- support corrected lyrics from the plugin override folder
- use 3 built-in online lyrics sources:
  - Kugou
  - QQ Music
  - Netease
- use the default online fallback order: `Kugou -> QQ Music -> Netease`
- support 3 ranked online source slots: `lyrics.source_order.rank_1` to `lyrics.source_order.rank_3`
- support per-source enable switches
- conservatively clean noisy search terms while keeping the original title as the first search keyword
- support three lyrics handling modes:
  - display only
  - save as sidecar `.lrc`
  - write back to audio tags
- support copying/opening paths, viewing recent logs, clearing logs, probing sources, and displaying the plugin version

### Settings Structure

The settings UI is organized as an entry page with four secondary pages:

- Basics
- Lyrics Sources
- Save & Override
- Debug Tools

### Debug Logs

When debug logging is enabled, the plugin writes key diagnostic events to the log file. The log path can be copied from the settings page.

Log lines use an `event | key=value` format. Common events include `search_attempt`, `source_success`, `source_failed`, `save_failed`, and `probe_source_failed`.

### Notes

`music-tag-web-dev_1.0` is used only as an implementation reference for lyrics-source logic.

This plugin does not depend on a running `music-tag-web` Web API.
