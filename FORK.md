# Fork 维护说明

本仓库是 [Silent1566/webhtv](https://github.com/Silent1566/webhtv) 的 fork，而后者本身 fork 自 [webhtv/webhtv](https://github.com/webhtv/webhtv)。

- `origin`   → https://github.com/nightking8342/webhtv-silent（本 fork）
- `silent`   → https://github.com/Silent1566/webhtv（基线，只读）
- `upstream` → https://github.com/webhtv/webhtv（原始上游，只读）
- 基线提交：`852d8428d3ea207c86bc1ce18c999ffce8df67ee`（`release/v5.6.0-beta-202611080849`，2026-11-08）
- 应用身份：**暂沿用** `com.silent.android.webhtv`，版本 `5.6.0-webhtv.1`（versionCode 561）

维护原则：只保留可解释、可验证、可回退的差异。新增分歧必须先在本文件登记，再改代码。

## 分支与同步

| 分支 | 用途 |
|---|---|
| `main` | 跟随 `silent/main` 的发布基线，只做 merge，不放开发 |
| `work/*` | 本 fork 的开发/移植分支（当前 `work/batch-a`） |

- 同步命令：`git merge silent/main`（按发布节奏，通常每次 release 后一次）。
- **不要** merge `silent/beta` 或 `silent/dev1..dev5`；需要尝鲜时单独开 `preview/*`。
- Silent1566 的仓库历史被上游压缩重写过，`git merge-base` 不代表真实归属；冲突判断以 `git diff` 的树差异为准。
- 冲突处理：下表登记的分歧归我方，其余取 `silent/main`。

状态锚点（每次同步后更新）：

```
last-synced-silent: 852d8428d3ea207c86bc1ce18c999ffce8df67ee
last-synced-upstream: 4e30ffaf219b1db15fe5662aeff6f9820e2b32bf
```

## 与基线的分歧清单

| 范围 | 分歧内容 | 冲突归属 |
|---|---|---|
| `app/src/main/java/com/fongmi/android/tv/web/HomeWebController.java`、新增 `WebHomeVerificationPolicy.java`（含测试） | Cloudflare/Turnstile 验证页以 403/429/503 返回、验证过程中会 `location.replace` 改写地址。不再把这类主框架响应当作加载失败；`onPageFinished` 即使 URL 变化或跨源也收起加载遮罩；放行 `challenges.cloudflare.com`、`/cdn-cgi/challenge-platform`、`__cf_chl`、`/verify` 等验证地址 | 我方（其余 WebHome 策略随 silent） |
| `app/src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java` 的 `setAdblock` | 不启用 Media3 内部 HLS 广告过滤：该层按分段 URI 前缀分组且没有误判保护，交替 CDN（`p16-*`/`p19-*`）会丢一半分段导致时长减半；HLS 广告过滤交给 App 侧带守卫的路径 | 我方（仅该调用点） |
| `PlayerManager.java`、`TrackDialog.java`、`TrackUtil.java`、`Track.java`、`TrackAdapter.java`（含 `TrackUtilTest`） | 选轨持久化使用稳定描述（`v2|...`，不含运行期 id）；运行期 id 重复（外挂字幕合并后 HLS variant 全变 `0:`）时回退稳定描述匹配；“自动”轨道清空显式选择并恢复自适应 | 我方（选轨语义；其余随 silent） |
| `app/src/main/java/com/fongmi/android/tv/service/PlaybackService.java` | 服务实例复用时 `onStartCommand` 恢复 `running=true`，避免 `stopSelf()` 之后无法停止 | 我方（仅运行态恢复） |
| mobile `app/src/mobile/java/com/fongmi/android/tv/ui/activity/VideoActivity.java` | 播放自然停止时 `saveHistory(true)` 后走 `finishPlayback()`，确保停播与服务回收 | 我方（仅该分支） |
| mobile `WebHomeChromeController.java`、`web/WebHomeChromeOptions.java`、`WebHomeChromeStartup.java`、mobile `VodFragment.java` | 手机端 WebHome 默认进入 `immersive`（隐藏状态栏与导航栏），站点显式配置 `edge` 时仍按原语义保留系统栏 | 我方（仅默认值） |
| `FORK.md`、`AGENTS.md` 的 fork 段落 | fork 身份、上游地址与同步规则，仅本仓库存在 | 我方 |

## 暂未分歧、改动前必须登记的敏感点

- **applicationId**：当前沿用 `com.silent.android.webhtv`，与 Silent1566 官方包同名不同签名（安装前需卸载官方版），但能覆盖我们自己的调试/测试包。改成 `com.nightking8342.webhtv` 会与旧 fork App 冲突，且旧库 v37 → 本库 v48 是单向迁移；改名必须单独立项并做全量回归。
- **Room 版本**：基线为 48（原始上游为 37），带 `MIGRATION_37_48`；不要把本仓库的 `tv.db` 拷回旧版。
- **备份格式**：新增 `following` / `followingSource` / `tmdbSeasonProgress` 字段；旧版导入会忽略，新 → 旧会丢这些数据。
- **native/播放器依赖**：`third_party/`、`app/libs/`、Media3/MPV/FFmpeg 的 lock 与预编译产物；改动走 `.codex/skills/upstream-integration-governor/SKILL.md`。
- **签名**：`app/signing/**`、`local.properties` 永不入库；当前 release keystore 为 `nightking8342 WebHTV`，丢失即失去升级路径。
- **发布工作流**：`.github/workflows/*` 沿用 silent 的 release/CNB 配置，未配置 secrets 时不要启用。

## 构建

- JDK 21、compileSdk 37、NDK 29；`local.properties` 需要 `sdk.dir`。
- Chaquopy 需要 Python 3.10：`chaquopyBuildPython=<python3.10>`（本机为 uv 的 `cpython-3.10.20`）。
- 签名：`storeFile` / `keyAlias` / `storePassword` / `keyPassword` 放在 `local.properties`。
- 常用命令：

```
./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.web.WebHomeVerificationPolicyTest"
./gradlew :app:testMobileArm64_v8aDebugUnitTest --tests "com.fongmi.android.tv.player.exo.TrackUtilTest"
./gradlew :app:assembleMobileArm64_v8aRelease
```

## 验证锚点

- `TrackUtilTest` 14/14
- `WebHomeVerificationPolicyTest` 5/5
- 平板实测（2026-11-13）：supjav 的 Cloudflare 验证页可正常显示，完成验证后继续请求，不再卡转圈
