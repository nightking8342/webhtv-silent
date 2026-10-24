# EXO / IJK 播放时长识别偏短（约 2 小时显示成 1 小时多）

状态：根因已确认并修复（广告过滤误删分段），待真机验收
记录时间：2026-10-24 16:55 +08:00（19:20 补充根因与修复）
当前 HEAD：`5c3a9ac4430305997096d79a15270e42a9bfacfa`

## Recovery anchor

- 目标：EXO / IJK 内核播放 HLS 时，进度条总时长与片源真实时长相符，
  可以正常拖到片尾（当前现象：约 2 小时的内容被识别成 1 小时多）。
- 当前阶段：已完成真机对照取证，锁定“同一片源 MPV 正确、EXO 只有前
  435/857 段的时长”；根因未定，未修改代码。
- 保护边界：任务开始前的 5 个未跟踪路径保持不动：
  `.codex/home-before.png`、`.codex/home-edge.png`、`.playwright-cli/`、
  `app/.cxx/`、`cf.yaml`；同时不改动
  `docs/fork-mpv-external-subtitle.md` 记录的问题。
- 下一步：确认 IJK 在同一片源上的 `durationMs`，或给 EXO 路径加一次性
  时间线打印，判定 Media3 为何只给出前 435 段的时间线（见下节真机数据）。

## 真机取证（2026-10-24 17:04，设备 192.168.1.31:37151）

片源：7MMTV / `https://mmsi02.com/stream/.../76057064/master.m3u8`，
3 档码率（480P/720P/1080P），分段是 TikTok CDN 上伪装成 PNG 的 TS。

### 1. 片源本身完全正常（PC 直连抓取）

| 项 | 值 |
|---|---|
| master.m3u8 | 470 字节，3 个 `#EXT-X-STREAM-INF` |
| index-f1-v1-a1.m3u8 | 189182 字节，857 条 `#EXTINF` |
| index-f2-v1-a1.m3u8 | 189184 字节，857 条 `#EXTINF` |
| index-f3-v1-a1.m3u8 | 189154 字节，857 条 `#EXTINF` |
| `#EXTINF` 总和 | 8564.133 秒 = **2:22:44** |
| `#EXT-X-PLAYLIST-TYPE` | `VOD`，末尾有 `#EXT-X-ENDLIST` |
| 分段时长分布 | 853×10.022s + 3×5.011s + 1×0.334s |

### 2. 两个内核在同一片源上的读数

| 内核 | 路由 | 上报 `durationMs` | 换算 |
|---|---|---|---|
| MPV | `owner=app-hls-proxy`，`playerPath=app-internal-service` | `8564133` | 2:22:44（与 playlist 完全一致） |
| EXO | `route=direct_remote_http`，`owner=remote-origin` | `4359570` | 1:12:40 |

### 3. EXO 拿到的是完整 playlist，不是被截断的响应

Media3 的 `input.read.summary` 事件：

```text
loadTaskId=0  bytes=470     contentType=application/vnd.apple.mpegurl  (master)
loadTaskId=1  bytes=189182  contentType=application/vnd.apple.mpegurl  (media)
```

`189182` 与 PC 直连抓到的 `index-f1-v1-a1.m3u8` 字节数完全相同，
说明 HTTP 层没有截断；而且 EXO 报出的是一个有限时长，说明它读到了
末尾的 `#EXT-X-ENDLIST`（读不到会按 live 处理，`getDuration()` 返回
`TIME_UNSET`，而不是 4359570）。

### 4. 差额的量化

- `4359570 ms = 435 × 10.022s`：正好等于 playlist 前 435 条 10.022s 分段
  的总和，即整份 857 段的前 **50.8%**；
- 不是“整份文件被截成一半”：一半字节 94591 落在第 429/430 段之间，
  对应约 4299s，不等于 4359570ms；
- 因此排除“代理截断字节流”，指向 Media3 侧的时间线构建结果本身。

### 5. 由此排除的候选

| 候选 | 结论 |
|---|---|
| A：playlist 缺 ENDLIST / 只有部分分段 | 排除，有 ENDLIST，857 段完整 |
| B：App 侧 HLS 代理截断 playlist | 排除，EXO 走直连且字节数与源一致；MPV 走代理反而正确 |
| D：服务端本身只有 1 小时多 | 排除，`#EXTINF` 总和 8564.133s |
| E：多码率 variant 时长不一致 | 排除，三档都是 857 段 / 8564.133s |
| C：时长被一次性快照锁定 | **仍待验证**，需确认 IJK 是否同样偏短 |

### 6. EXO 自己的缓存里就是完整 playlist（决定性证据）

`/data/data/com.nightking8342.webhtv/cache/exo/<bucket>/*.v3.exo`（Media3
SimpleCache）里能直接读到 EXO 本次会话下载的 playlist：

```text
2250.0.1792832680736.v3.exo  189182 字节  857 条 #EXTINF   (f1 480P)
2015.0.1792827560244.v3.exo  189184 字节  857 条 #EXTINF   (f2 720P)
2252.0.1792832682004.v3.exo  189154 字节  857 条 #EXTINF   (f3 1080P)
```

即：Media3 拿到并缓存了完整 857 段，却只给出 435 段的时长。

### 7. 行为观察（17:26:38 seek 到进度条最右端）

```text
video.positionDiscontinuity=1 old=4301080 new=4359569 dur=4359570 state=2
```

进度条最右端就是 4359570（= 435×10.022s），说明 seek 区间被时间线限制在
1:12:40。seek 之后 `state=3 READY`、`bufferedMs=0`，解码计数
（submittedBuffers=2701）不再增长；用户观察到"会继续播"。

结论：**Media3 的 HLS 时间线时长比内容短一半**，但播放器本身并不认为是
ENDED（state=READY 而非 4），说明底层分段仍在、只是时间线时长被少算。
这解释了"能继续播、但进度条只有 1:12:40、拖不到后面"。

## 根因（2026-10-24 19:20，已确认）

IJK 在 19:12 复现同样的 `durationMs=4359570`，日志给出直接证据：

```text
mpv-proxy: adblock filtered session=2 bytes=189182->96048
mpv-proxy: adblock filtered session=2 bytes=189184->97394
mpv-proxy: adblock filtered session=2 bytes=189154->96081
```

`189182 -> 96048` 不是普通广告过滤，而是把 playlist **砍掉了一半**。

### 为什么正好砍掉一半

该片的 857 条分段来自两个交替的 CDN 主机：

| 主机 | 分段数 | 总时长 |
|---|---|---|
| `p16-ad-site-sign-sg.tiktokcdn.com` | **422** | 4229.28s |
| `p19-ad-site-sign-sg.tiktokcdn.com` | **435** | 4359.57s |

本地把 playlist 只保留 `p19` 那 435 段，UTF-8 字节数**正好是 96048**，
与日志中的过滤结果完全一致；`4359.57s` 也正是两个内核上报的
`4359570ms`。

即：Media3 的 `HlsAdsParser` 在没有任何广告标记
（`#EXT-X-CUE-OUT` / `#EXT-X-DATERANGE` / `#EXT-X-ASSET` /
`#EXT-X-DISCONTINUITY`）的 playlist 上，退化为"按分段 URI 前缀分组、
把少数派分组当广告"的启发式判断，把 `p16` 那 422 段全部删掉。

### 三层路径为什么结果不同

| 内核 | 广告过滤发生的位置 | 结果 |
|---|---|---|
| MPV | `MpvHlsProxy.applyAdblock()` 对 MPV 直接 `return text`，只记录广告区间 | 完整 857 段 → 8564133ms |
| IJK | `MpvHlsProxy.applyAdblock()` 真正过滤（IJK 走 App 本地代理） | 被砍成 435 段 → 4359570ms |
| EXO | 不走本地代理，用 Media3 fork 的 `MediaItem.Builder.setAdblock(true)`，在播放器内部调用同一个 `HlsAdsParser` | 被砍成 435 段 → 4359570ms |

三条路径用的是同一个 `HlsAdsParser`，只是入口不同；EXO 这一层没有任何
守卫，所以修好 IJK 之后 EXO 仍然偏短。

## 修复（已实施）

- 新增 `androidx/media3/mpvplayer/HlsAdblockGuard`：
  - playlist 里没有任何广告结构标记时，若过滤结果丢掉超过 **25%** 的
    总时长，则拒绝这次过滤并保留原始 playlist；
  - 有真实广告标记时行为不变。
- `MpvHlsProxy.applyAdblock()` 在拿到 `HlsAdsParser.process()` 结果后
  先过这个守卫；被拒绝时打印
  `adblock rejected ... reason=unmarked-major-removal`。
- 单测：`HlsAdblockGuardTest`（5 例：交替 CDN 主机误判、少量删除、
  有广告标记、无变化、EXTINF 求和）。
- `ExoUtil.getMediaItem()` 不再把 `setAdblock(true)` 传给 Media3：该层过滤
  在 AAR 内部、无法加守卫，且与 App 侧代理重复。EXO 的广告过滤改由
  带守卫的 App 侧路径负责（后续如需 EXO 广告过滤，应把 EXO 的 HLS 也接到
  `MpvHlsProxy`，而不是放开 AAR 内的过滤器）。

### 验收

1. IJK 播同一片源，日志出现 `adblock rejected ... reason=unmarked-major-removal`；
2. `durationMs=8564133`，进度条 2:22:44，可拖到片尾；
3. EXO 同样恢复；
4. 真正带广告标记的片源过滤行为不变。

## 现象

- 内核：EXO 与 IJK 两个内核都会出现，不是单一内核的实现差异。
- 触发条件：**只在部分片源上出现**，同一 App、同一网络下别的片子时长正常。
- 表现：真实时长约 2 小时的内容，进度条总时长显示为 1 小时多；比例上像是
  只统计了 playlist 中的一部分分段（约 50%–80%）。
- 对照：同一片源上 MPV 上报 8564133ms（正确），EXO 上报 4359570ms
  （偏短），已确认是内核相关而非片源相关（见上节）。
- 用户报告的“刷新/切换内核后正常/异常”尚未系统记录，需在复现时一并记录。

## 现在的关键疑问（决定后续补什么证据）

1. 播放到“错误时长”之后是否仍在继续播放？
   - 若继续播放：时长只是被少算，源头多半是 playlist 只解析到一部分分段。
   - 若在错误时长处停止：片源本身被服务端拆成多段，是内容问题而非播放器问题。
2. 错误时长是否会随播放时间增长？
   - 若增长：说明拿到的是 live 形态的滑动窗口 playlist。
   - 若不增长：说明时长被一次性快照锁定。
3. 同一片源换 MPV 是否正确？
   - 若 MPV 正确：问题在 EXO/IJK 的 HLS 时长计算或快照时机，而非 URL 或代理。

## 已确认的相关代码路径

| 位置 | 作用 | 备注 |
|---|---|---|
| `PlayerManager.java:872-877` | `getDuration()` / `getDurationTime()` 直接返回 `player.getDuration()` | 没有任何补偿或修正逻辑 |
| `ExoPlayerEngine.java:569-589` | `player.getDuration()`、`isCurrentMediaItemLive()` 参与时长分类 | 时长 < 1 分钟或被判定为 live 会走另一条策略 |
| `IjkSimplePlayer.java:1192-1205` | `duration()` → `safeDuration()` → `ijk.getDuration()` | 直接读 native 元数据，无二次校正 |
| `IjkSimplePlayer.java:183-199` | `MediaItemData.setDurationUs(...)` | IJK 的对外时长就是 native 值 |
| `VideoActivity.java:5938-5945` | `updatePlaybackHistoryPosition()` 用 `player().getDuration()` 写入历史 | 错误时长会被持久化到历史记录 |
| `VideoActivity.java:5929-5933` | 结束判定使用 `mHistory.getDuration()` | 时长偏短会导致提前触发“已看完” |
| `server/process/M3u8.java` | 本地 `/m3u8?url=` 兼容代理 | `rewrite()` 只改 URI，不改 `#EXTINF`；但会整份读取后重新输出 |
| `server/process/Proxy.java` | 通用代理 | 需确认 HLS 播放是否走它、是否有 Range/截断处理 |

由此可知：UI 显示、历史记录、片尾判定都直接使用播放器上报的时长，
所以只要底层时长偏短，三个地方会一起偏短；不存在 UI 单独算错的分支。

## 候选根因（按可能性排序，逐条排除）

### A. 片源 m3u8 缺少 `#EXT-X-ENDLIST`，或只给出部分分段

服务器按需生成 media playlist，播放器启动时只能看到已生成的分段，
把当时的 `#EXTINF` 总和当成总时长；后续分段的到达没有反映到总时长。

判定：抓原始 m3u8，检查是否存在 `#EXT-X-ENDLIST`、`#EXT-X-PLAYLIST-TYPE`，
以及 `#EXTINF` 总和与播放器上报时长的比值。

### B. App 侧 HLS 代理返回的 playlist 被截断

`M3u8.playlist()` 用 `body.string()` 整份读取后重新输出，理论上不会截断，
但若上游是 chunked 响应、或经过 `Proxy` 走了不同的读取路径，仍可能只拿到
前半段。截断发生在 `#EXTINF` 行之间时，播放器会把它当作完整 playlist 解析，
表现恰好就是“时长少一截”。

判定：用 `adb forward` 后在 PC 上分别请求直连 URL 和本地代理 URL，
对比字节数、行数、末行内容（是否需要 `#EXT-X-ENDLIST`）。

### C. 时长被一次性快照锁定（EXO / IJK 各自的更新时机不同）

IJK 的 `ijk.getDuration()` 来自 native 元数据，只在 prepare 阶段确定；
EXO 的 `player.getDuration()` 来自 timeline。两者都没有“时长变化后重新
校正”的逻辑，因此初始值若偏短就会一直偏短。

判定：同一片源播放 30 分钟后再读一次时长，看是否变化；
抓 `onTimelineChanged` / `durationMs` 日志，确认上报值是否曾经更大。

### D. 片源在服务器侧本身就是分段发布

服务端把一个 2 小时的片子拆成多集/多段 m3u8，App 拿到的是第一段。

判定：抓到的 playlist 中最大 `#EXTINF` 累计值若就只有 1 小时多，
且最后分段 PTS 也在 1 小时多，则属于内容侧问题，播放器无错误。

### E. 多码率 master playlist 的选择问题

不同 variant 的 media playlist 时长不一致（例如高码率档只发布了部分分段），
切档后时长跟着变化。

判定：逐个 variant 抓 media playlist 对比 `#EXTINF` 总和；
在失败片源上手动切档，观察总时长是否跳变。

## 取证计划（下次复现时执行，一次抓齐）

1. 记录片源 URL 与当前内核（EXO / IJK / MPV 各一次）。
2. `adb logcat -s TV-player TV-player-engine TV-mpv TV-webhome-console`
   抓取启动阶段的 duration 上报与 timeline 变化。
3. `curl -sI` + `curl -s` 直连 master / media playlist，保存原始文本。
4. `adb forward tcp:9978 tcp:9978` 后请求本地代理版本，保存文本并对比
   行数、字节数、末行。
5. 计算 `#EXTINF` 总和，与播放器上报的 `durationMs` 做对照表。
6. 播放到超过错误时长后，再次记录 `positionMs` / `durationMs`。

## 验收标准（修复时）

- 进度条总时长与媒体 playlist 中 `#EXTINF` 总和（或最后分段结束 PTS）一致，
  误差在数秒以内；
- 进度条可拖到真实片尾，且播放到片尾才触发“已看完”；
- 错误时长不再被写入历史记录（`mHistory.setDuration()`）；
- 同一片源在 EXO / IJK 下结果一致，MPV 与正常片源不回归；
- 诊断日志能区分“playlist 本身只有这么长”和“播放器少算了”。

## 与已有记录的关系

- 与 `docs/fork-mpv-external-subtitle.md`（MPV 外挂字幕）无关，两者独立。
- 与 `docs/fork-playback-track-intent.md`（选轨语义）无关；但两者同样依赖
  `player.getDuration()`，修复时不要改动选轨路径。

## 回滚

本记录只新增文档，不涉及代码、依赖、二进制或数据库 schema。
