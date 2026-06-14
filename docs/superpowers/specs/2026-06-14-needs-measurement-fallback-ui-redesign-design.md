# Design Spec: `needs_measurement` 人工测量建模兜底 + App UI 重设计精修

- **Date:** 2026-06-14
- **Repo:** `ARCore-prototype`（采集端 / Android · Kotlin · ARCore · Compose）
- **Issue:** #3 — *Improve needs_measurement fallback with visual caliper guidance*
- **Branch:** `feat/arcore-verification-prototype`
- **Status:** 设计已逐节确认（含可视化原型），待写实现计划
- **前置设计：** `docs/superpowers/specs/2026-06-07-scanforge-app-redesign-design.md`（已批准的「拾模 / ScanForge · 深色专业扫描风」重做）+ `docs/superpowers/specs/2026-06-07-arcore-capture-client-design.md`（上传/识别契约）。本 spec 在其基础上演进。
- **配对契约（已核对源码，非假设）：** `BrickStudio/apps/api/src/api/v1/parametric_blocks.py`、`BrickStudio/apps/web/src/features/parametric/{api.ts,schema.ts}`、`BrickStudio/docs/measure-block-annotated.svg`。
- **可视化原型（设计代理）：** `docs/superpowers/specs/assets/needs-measurement-ui-mockups/`（`measurement-model.html` / `step-diagram-style.html` / `confirm-page.html` / `design-language.html` / `screen-set.html`，浏览器打开）。
- **关键工程约束（沿用前置 spec）：** 本开发机**无 Android SDK / Gradle / JDK17**，无法在此编译或预览 Compose UI。重逻辑全部下沉到 **JVM 可单测纯函数**；AR/GL/Compose 留薄层，由用户在 **Mac + Android Studio + 真机**验证。上述 HTML 原型是 UI 的设计代理。

---

## 0. 背景与范围

本次工作有两部分，合并为一个实现周期（共享同一套 UI 基座）：

- **Part A — `needs_measurement` 兜底重做（issue #3 核心）。** 现状：后端返回 `needs_measurement` 时，App 仅展示一段长文本 + 5 个工程字段名输入框（`MeasurementFormActivity`），用户不知道卡尺夹哪里，且提交走了错误的 JSON 契约。目标：做成**「自动识别不可信 → 人工测量建模」**的 2 阶段引导流程，提交对齐后端 multipart 契约。
- **Part B — 全 App UI 重设计精修。** 现状：已实现的「深色专业扫描风」存在三个体感问题：(1) 内容与手机顶部状态栏重叠；(2) 子页无返回按钮；(3) 整体缺乏质感。目标：保留 ScanForge 身份，加一层**共享导航/inset 基座 + 设计令牌精修**，逐屏提质。

两部分耦合点：新的测量向导屏复用 Part B 的共享基座（顶栏 + 返回 + inset + 设计令牌），因此一并设计、一并实现。

---

# Part A · `needs_measurement` 人工测量建模兜底

## A.1 流程（2 阶段）

```
needs_measurement 响应
        │
   第0步 建模确认  ──►  第1–5步 测量向导  ──►  提交 / 结果
 (system/kind/units)     (1A→1B→③→②→④)     (multipart → 结果屏)
```

路由不变：现有 3 处调用点（`ScanCaptureActivity`、`CaptureWizardActivity`、`SessionDetailScreen`）在 `status == NEEDS_MEASUREMENT` 时启动测量向导屏（类名变更见 A.7）。

## A.2 后端契约（已核对源码，权威）

`POST /api/v1/parametric-blocks` — `multipart/form-data`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `system` | Form, 枚举 | `duplo` \| `lego` \| `feile` \| `generic` |
| `kind` | Form, 枚举 | `brick` \| `plate` \| `tile` \| `slope` |
| `units_x` | Form, int | 1..16 |
| `units_y` | Form, int | 1..16 |
| `raw_measurements_mm` | Form, **JSON 字符串** | 对象，**恰好** 5 个键（见下），单位 mm，均 > 0 |
| `photos` | File, 可选 | 0–20 张；**不参与重建**，仅视觉对照。本期**传 0 张** |
| `part_id` | Form, 可选 | 默认 `{system}-{kind}-{units_x}x{units_y}` |

`raw_measurements_mm` 的 5 个键（顺序即向导步骤顺序）：
`outer_pitch_mm`、`inner_pitch_mm`、`stud_diameter_mm`、`brick_height_net_mm`、`brick_height_total_mm`。

**响应 201**（`ParametricBlockRead`）关键字段：`capture_id`、`part_id`、`status`（`pending`）、`mode`（`parametric_block`）、`system`、`kind`、`units_x`、`units_y`、`raw_measurements_mm`、`derived_spec_mm`（4 项）、`cross_check_warnings`（字符串数组，可空）、`job_id`、`created_at`。

**进度：** `GET /api/v1/jobs/{job_id}/stream`（SSE）。`GET /api/v1/parametric-blocks/{id}` 在 v0.3 未挂载（404）；POST 201 响应即权威来源。

**错误：** 缺键 / 非数值 / ≤0 → 422，body 带具体字段信息（向导需展示）。

## A.3 第0步 · 建模确认页

目的：建立「这不是普通失败，是进入人工测量建模」的心智，且**不信任**本次低置信识别结果。

- **顶部横幅：** 「⚠️ 自动识别不可信 — 需人工测量生成参数化模型」+ 引用后端原因（`needs_measurement.reason`，如 *"pitch did not match a known system within tolerance/confidence"*）。
- **识别结果（淡化、标注不可信）：** 把 `recognized` 的 `kind / units_x×units_y / pitch_mm / confidence` 以**灰显**卡片 + 「置信度 0.0 · 不可信」标签展示，**仅供参考，不预填进输入**。
- **① 选择体系 `system`：** 4 个 chip（得宝 DUPLO / 乐高 LEGO / 费乐 FEILE / 通用），文案与 hint 镜像 `schema.ts` 的 `BRICK_SYSTEMS`。**默认不选**（后端本次返回 `system=null`，无可信建议）。
- **② 选择类型 `kind`：** 4 个 chip（标准砖 / 板 / 瓦片 / 斜面），镜像 `schema.ts` 的 `BRICK_KINDS`（含 icon/hint）。**自动分类（先复用、后端上）：** 用后端随响应返回的 `recognized.kind`（其源于服务端对俯视图的 `detect_studs`，是图像分类）**预选**对应 chip，并打「识别建议 · 请核对」角标；用户可改。`recognized.kind` 为空时不预选。
- **③ 凸点排数 `units_x × units_y`：** 两个步进器（−/+ 或数字输入），**钳制 1..16**。**默认留空**，不预填本次识别的 `22×14`（超出范围、不可信）；若识别值落在 1..16 也仍留空，强制用户按实物确认。展示提示：「识别给的 22×14 超出 1–16，已忽略，请按实物填写」。
- **行动：** 「开始测量（5 步）」，当 `system` 已选、`kind` 已选、`units_x/units_y` 均为合法整数（1..16）时可点。

> 决策记录：units 留空 + 钳制 1..16（用户已确认）；`kind` 预选自后端建议、可改（用户选「先复用、后端上」）；端上图像分类器列为后续增强（见「非目标」）。

## A.4 第1–5步 · 测量向导

- **逐步单字段：** 每步只显示一个测量的输入框；顶部 5 段进度指示 + 「第 N / 5 步」。
- **聚焦示意图（方案 A，用户已选）：** 每步只画当前测量、其余凸点淡化，高亮线随步骤切换。1A/1B/③ 用俯视图，②/④ 用侧视图。Compose `Canvas` 原生绘制（几何常量来自字段目录，便于一致与维护）。
- **每步文案：** 编号（1A/1B/③/②/④）+ 中文名 + 简短动作提示，镜像 `schema.ts` 的 `MEASUREMENT_FIELDS`：
  - **1A** `outer_pitch_mm` 外径距：砖块总长，跨两端凸点圆周最远点（卡尺跨外）
  - **1B** `inner_pitch_mm` 内径距：相邻两凸点之间最窄缝（卡尺插入两凸点之间）
  - **③** `stud_diameter_mm` 凸点直径：任一凸点外径
  - **②** `brick_height_net_mm` 砖块净高：底面 → 砖顶，不含凸点
  - **④** `brick_height_total_mm` 砖块总高：底面 → 凸点顶，含凸点
- **数据驱动 + 兜底：** 步骤集由后端 `needs_measurement.fields`（字符串数组，已支持解析）按返回顺序映射到字段目录；目录未收录的键 → 通用兜底步（显示原始键名 + 数字输入），不崩。
- **导航：** 上一步 / 下一步；第 5 步「下一步」= 进入提交。
- **实时校验：** 见 A.5，红/黄即时反馈。

## A.5 校验分级

镜像后端 `_cross_check_raw` 的语义，但端上对「物理不可能」项更严格：

- **红色 · 拦截提交**（任一不满足则禁用「下一步/提交」并标红当前/相关步）：
  - 每个值为正且有限（`> 0`、非 NaN/Inf）。
  - `outer_pitch_mm > inner_pitch_mm`（1A > 1B）。
  - `brick_height_total_mm > brick_height_net_mm`（④ > ②）。
  - 注：后端对 1A≤1B / ④≤② 仅记 warning 不拦截；端上**故意更严**（这两项对真实积木恒成立，违反即录入错误）。
- **黄色 · 仅提醒，不拦截**：
  - `|(outer_pitch_mm − inner_pitch_mm)/2 − stud_diameter_mm| > 0.5mm`（凸点直径互验，后端同阈值 `_CROSS_CHECK_TOL_MM = 0.5`）。

## A.6 提交与结果

- 组 multipart（A.2 契约）：`system / kind / units_x / units_y` + `raw_measurements_mm`（5 键 JSON 字符串）；**photos 传 0 张**；`part_id` 不传（用后端默认）。POST `baseUrl + /api/v1/parametric-blocks`。
- **成功（201）：** 结果屏展示 `status` + `part_id` + `job_id` + `cross_check_warnings`（有则黄色列出）+ `derived_spec_mm`。「查看模型」按钮 = **best-effort**，复用现有 WebView model-viewer 预览（资产地址沿用 `ResultActivity` 既有约定，标注 contract note）。
- **失败（422/网络）：** 展示后端返回的具体字段错误（解析 body）或网络错误；可返回向导修改后重试。
- **本期不做 SSE 实时进度**（见「非目标」）；结果屏显示「已提交，后端生成中」即可。

## A.7 组件（新增 / 改动）

**新增（`measure/` 与 `net/`，纯逻辑均 JVM 单测）**
- `measure/MeasurementCatalog.kt` — 镜像 `schema.ts`：`system`/`kind` 的枚举 + 显示文案/hint/icon；5 字段的 `code(1A/1B/③/②/④)` + 中文名 + 提示 + 示意图目标（TOP/SIDE + 高亮规格）；`MIN_UNITS=1 / MAX_UNITS=16`。提供「后端 `fields[]` → 有序向导步」映射 + 未知键兜底。
- `measure/MeasurementCrossCheck.kt` — A.5 的两级校验，返回 `{errors:[...], warnings:[...]}`（结构化、可定位到字段）。
- `measure/MeasurementDiagram.kt` — Compose `Canvas` 聚焦示意图（方案 A），按当前字段高亮。
- `net/ParametricSubmission.kt` — 用现有 `MultipartBuilder` + `HttpTransport` 组装 A.2 multipart；`raw_measurements_mm` JSON 序列化；解析 201 → `ParametricBlockResult`（`captureId/jobId/status/crossCheckWarnings/derivedSpec`）。

**改动**
- `net/RecognitionResultModel.kt` — `NeedsMeasurement` 增加 `reason: String?` 字段并解析（确认页横幅用）。其余（`fields` 字符串/对象双形态、`recognized` 块）已具备。
- `measure/MeasurementFormActivity.kt` → 重写为 **`MeasurementWizardActivity`**（Compose `setContent`，2 阶段 + 5 步 + 结果）；删除旧 XML 布局 `activity_measurement_form.xml` 与旧 JSON-to-`/parametric-blocks` 提交。更新 3 处调用点的类名引用。
- `measure/MeasurementValidator.kt` — 被 `MeasurementCrossCheck` 取代（或保留为其内部正数校验）；实现计划定夺。

---

# Part B · 全 App UI 重设计精修

保留「拾模 / ScanForge · 深色 + 青色 `#1ED7B5`」身份，加共享基座 + 令牌精修。视觉方向 **A · 精致扫描**（实色表面 + 1px 描边 + 发丝分隔线 + 扁平状态药丸 + 充足留白；真机还原度最高），用户已选。

## B.1 三个体感问题的根因与修复

| 问题 | 根因（已核对代码） | 修复 |
|---|---|---|
| 与状态栏重叠 | `MainActivity.enableEdgeToEdge()`，但子屏（`SessionDetailScreen`/`FrameViewerScreen`/`DiagnosticsScreen`）与 XML Activity（`ResultActivity`/`MeasurementForm`）无顶栏、未消费 top inset | 统一 `ScreenScaffold`：带 `TopAppBar` 并正确消费 `WindowInsets.systemBars`/`statusBars` |
| 无返回按钮 | 非 Tab 屏均无带 up 的顶栏，只能系统返回 | `ScreenScaffold` 顶栏含返回箭头（`navigationIcon` → `popBackStack`/`finish`） |
| 缺质感 | 屏幕为裸 `Column`/`LazyColumn` + 默认 Material3，卡片扁平、无间距节奏、无状态药丸/空态 | 设计令牌精修（见 B.2）+ 逐屏套用共享组件 |

## B.2 共享基座与设计令牌（方案 A）

**共享组件（新增 `ui/components/`）**
- `ScreenScaffold` — 统一顶栏（标题 + 可选返回 + 可选尾部动作）+ 正确 inset 处理 + 背景；非相机屏统一用它。Tab 屏（首页/采集库/设置）顶栏无返回，子屏有返回。
- `StatusPill`（已识别/待测量/通用/✓ 支持 等）、`SectionCard`（实色表面 + 1px 描边 + 16 圆角）、`FilterChipRow`、`EmptyState`、`PrimaryButton/GhostButton`、`SettingRow`（带发丝分隔）。

**令牌（在现有 `ui/theme/` 上扩展，不改主色）**
- 颜色沿用 `Color.kt`（`SfTeal/SfBg/SfSurface/SfSurfaceVariant/SfOnBg/SfMuted/SfOk*/SfWait*/SfError`）；新增描边/分隔线令牌（如 `SfBorder #243042`、`SfDivider #1b2433`）。
- 间距尺度（4/8/12/16/20）、圆角（pill 20、卡片 16、缩略图 8–10）、状态药丸样式、tabular 数字用于测量/读数。

> AR 相机页（`ScanCaptureActivity` 等独立 Activity）不套 `ScreenScaffold`（全屏沉浸），但其 Compose 浮层统一用上述令牌 + 顶部返回箭头，并对顶部控件做 inset 避让。

## B.3 逐屏精修（设计代理见 `screen-set.html`）

- **首页 Home** — 顶栏（品牌 + 设置入口）；双轨入口卡（识别=主/青色图标块，通用=次）；「最近采集」列表行 + 状态药丸。
- **采集库 Library** — 顶栏「采集库」；筛选 chip（全部 / 已识别 / 待测量 / 通用）；列表行（缩略图 + 标题 + 元信息 + 状态药丸）；空态。
- **会话详情 Session Detail** — 顶栏 + **返回**；识别结论卡 + 状态药丸；3 列帧网格；操作（预览模型 / 重新上传 / 删除）。保留现有重传 → 识别/测量路由逻辑。
- **帧查看器 Frame Viewer** — 全屏沉浸 + 返回箭头；RGB / 深度分段切换；深度态热力图 + 色标图例（近暖远冷 + 量程）+ 清晰度读数；底部 删除 / 重拍 / 上一张 / 下一张。
- **设置 Settings** — 顶栏「设置」；分组行（服务器地址 / 采集参数 / 调试存图开关 / 设备诊断 ›）+ 版本页脚。
- **设备诊断 Diagnostics** — 顶栏 + **返回**；状态卡（ARCore ✓ / Depth ✓ / 推荐路线 / 设备型号）。
- **采集页 Capture overlay** — 分段 向导/自由；深度热力开关（核心）；中心识别框（仅识别采集）含距离/覆盖勾；四项质量药丸（不达标仍可强拍，沿用 `CaptureButtonPolicy`）；左下回看条；右侧深度色标；大快门 + 「上传 N/4」。通用采集同骨架（无识别框、主行动「导出」）。顶部控件 inset 避让 + 返回箭头。
- **识别结果 Result** — 顶栏 + **返回**；识别结论卡 + 状态药丸；GLB 预览区 + 「预览模型」。由现有 XML `ResultActivity` 移植为 Compose（逻辑层 `RecognitionResultModel` 不变）。
- **测量向导 Measurement Wizard** — Part A，套同一基座（顶栏 + 返回 + 令牌）。

> 移植口径：逻辑层（`RecognitionResultModel` 等）不变；`ResultActivity` / 测量屏由 XML 移植为 Compose 屏，统一走 `ScreenScaffold`。是否并入 `ScanForgeApp` 的 NavHost 或保留独立 Activity，由实现计划按生命周期/启动方式定夺（相机页保持独立 Activity）。

---

# 跨切面

## C.1 测试（对应 issue 验收）

**JVM 纯逻辑单测（本机可跑）**
- `RecognitionResultModelTest` — 扩展：`needs_measurement.fields` 字符串数组（已覆盖）+ 新增 `reason` 解析。
- `MeasurementCatalogTest` — 键 → code/中文名/提示 映射；后端 `fields[]` 顺序还原；未知键兜底。
- `MeasurementCrossCheckTest` — 正数/有限；`1A>1B` 拦截；`④>②` 拦截；凸点直径互验在 0.5mm 边界给/不给 warning。
- `ParametricSubmissionTest` — 用 fake `HttpTransport`：端点路径 `/api/v1/parametric-blocks`；表单字段齐全；`raw_measurements_mm` 为合法 JSON 且恰含 5 键；photos 为 0。

**真机验证清单（用户在 Mac + 设备执行）**
- 用 fixture `part-e6ac1916_1781407101219`（手机路径 `/sdcard/Android/data/com.johnymoo.arverify/files/captures/...`；本机副本 `/tmp/arverify-latest-upload/...`）上传，稳定复现 `needs_measurement` → 进入「人工测量建模」流程（而非工程字段表单）→ 完成 5 步 → 成功 POST `/api/v1/parametric-blocks` → 结果屏。
- 不看开发文档即可知 5 个值各量哪里（聚焦图）。
- 确认/修正 `system / kind / units_x / units_y`；units 钳制 1..16。
- 红/黄校验生效；422 字段错误可读。
- 全 App：所有非 Tab 屏有返回箭头、内容不与状态栏重叠；视觉为统一精致扫描风。

## C.2 非目标 / 后续增强（YAGNI）

- **端上图像分类器**（TFLite 等，独立于后端再判 kind/system）—— 后续增强；本期复用后端 `recognized.kind` 作建议。
- **SSE 实时建模进度**（`/api/v1/jobs/{id}/stream`）—— 后续；本期结果屏展示已提交状态 + best-effort 预览。
- 不改后端 `/parametric-blocks` 契约（标注「不改」）；不改 `applicationId`/源码包名。
- 不做 photos 上传（端点可选、不参与重建）。

## C.3 风险 / 约束

- 本机无法编译/预览 Compose；以 HTML 原型为设计代理，真机由用户验证。重逻辑下沉纯函数以最大化本机可测面。
- 跨仓契约以 `BrickStudio` 源码为准；任何字段变化需回查上述文件并同步。
- best-effort 模型预览的资产地址为跨仓未定项，沿用现有 `ResultActivity` 约定并标注 contract note。
