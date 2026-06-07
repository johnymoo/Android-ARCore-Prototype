# Design Spec: 拾模 / ScanForge —— App 重命名 · UI 重设计 · 功能/逻辑/UX 重做

- **Date:** 2026-06-07
- **Repo:** `ARCore-prototype`（采集端 / Android · Kotlin · ARCore）
- **Status:** 已批准设计（分节逐项确认），待实现计划
- **前置：** 在 PR #2 的基础上演进。能力验证 + 引导式智能采集已真机跑通（OPPO PLG110：ARCore 支持 / Depth 支持），采集页稳定、无崩溃、系统栏与取景已处理；端到端上传识别闭环尚未真机验证。
- **配对规格（不变）：** 上传/识别/建模契约见 `docs/superpowers/specs/2026-06-07-arcore-capture-client-design.md` §5（与 `BrickStudio` 共享锁定）。
- **关键工程约束：** 本开发机**无 Android SDK / Gradle / JDK 17**，Android 端无法在此编译或预览；只能在此跑 **JVM 纯逻辑单测**。AR 会话、GL、Compose UI 由用户在 **Mac + Android Studio + 真机**验证。设计据此把重逻辑全部留在可单测纯函数，AR/GL/Compose 留薄层。

---

## 0. 背景与定位

应用已从「**原型验证工具**」（检测某手机是否支持 ARCore/Depth）演进为一款**拍照采集 + 建模**工具。本次重做覆盖用户提出的三件事：**改名 · UI 重设计（符合主流）· 功能/逻辑/UX 重做**（核心痛点：取景时无法实时预览深度、拍完无法在 App 内查看）。

**新定位（双轨）：**
1. **识别采集（主线）**：引导式最少操作采集标准积木 → `BrickStudio` 后端自动识别（system + kind + 凸点网格）→ 参数化 GLB；置信度不足时走引导测量兜底。
2. **通用采集（次轨）**：任意小物体的 RGB + 深度 + 点云采集，**本地导出/分享**（不依赖后端识别），兼容未来离线建模/重建。

旧「能力检测/推荐路线」降级为**设置内的「设备诊断」**；旧「基础深度拍摄」（一拍即彩色深度图 + PLY）**并入通用采集**。

## 1. 决策摘要（已逐项确认）

| 维度 | 决定 |
|---|---|
| 核心定位 | 双轨：识别采集（主）+ 通用深度采集导出（次）|
| 旧功能 | 能力检测 → 设置内「设备诊断」；基础深度采集 → 并入通用采集 |
| 采集交互模型 | 混合：默认分步**向导**，可切**自由**模式 |
| 实时深度 | **全屏深度热力叠加（可开关）**，近暖远冷 + 色标图例 |
| 采集后查看 | **二合一**：采集中即时回看条 + 主屏持久「采集库」|
| 命名 | 显示名 **拾模 / ScanForge**；**仅改品牌资源**（`applicationId` 与源码包名 `com.johnymoo.arverify` 不变）|
| 视觉风格 | **深色专业扫描风**（深色 + 青色强调 `#1ED7B5`）|
| 实现架构 | **Jetpack Compose 重写 UI**；AR 相机页用独立 Activity 隔离 + Compose 浮层 |
| 持久化 | **文件系统**（每会话 `manifest.json` + 文件，原子写），非数据库 |

## 2. 信息架构与导航

**单 Activity + Navigation-Compose** 托管所有**非相机**屏；底部 3 个常驻 Tab：**首页 / 采集库 / 设置**。采集流程为**全屏沉浸（无底栏）**。

屏幕清单：
1. **首页 Home**：双轨入口卡（识别采集=主/青色强调，通用采集=次）+「最近采集」快捷 + 设置入口。
2. **识别采集流**（全屏，独立 Activity）：取景 → 类型轻确认 → 上传进度 → 结果 / 引导测量。
3. **通用采集流**（全屏，独立 Activity）：取景 → 选帧 → 本地导出（RGB / 16-bit 深度 PNG / PLY 点云）+ 分享。
4. **采集库 Library**：历史会话列表（筛选：全部 / 已识别 / 待上传）→ **会话详情**（逐帧网格 + 操作）→ **帧查看器**。
5. **设置 Settings**：服务器地址、采集参数（距离区间 / 锐度阈值）、调试存图开关、**设备诊断**（ARCore / Depth / 推荐路线，由原能力检测降级而来）。

## 3. 采集页设计（识别采集 + 通用采集 共用骨架）

全屏深色取景上叠加 Compose 浮层控件：

- **深度热力可开关**（右上角 chip）：开时把实时 DEPTH16 上色（近暖远冷）半透明叠加在 RGB 上 + 底部色标图例；一键开/关。这是"取景时看得见深度"的核心修复。
- **向导 / 自由 切换**（顶部分段控件）：
  - **向导**：分步提示 + 进度点 —— ① 俯视凸点帧 → ② 侧视帧 → ③ ≥2 张补充角度（凑够 ≥4 张）。
  - **自由**：随意拍，后台**覆盖度状态机**判断"顶/侧/角是否齐、是否可上传"，不强制顺序。
- **实时质量条**：追踪 / 距离 / 清晰度 / 焦点 四个 chip，绿色达标、橙色提示原因。**不达标时快门仍可按**（保留真机调试所需的"强拍"，沿用现有 `CaptureButtonPolicy` 思路）。
- **识别框**（仅识别采集）：中心裁剪/对齐框，与后端识别裁剪区一致；显示框内距离与覆盖勾。
- **即时回看条**（左下）：已采帧缩略图（RGB + 深度角标），点开进帧查看器（见 §4）。
- **快门 + 主行动**：中间大快门；识别采集凑够帧后右侧「上传 N/4」高亮 → 类型轻确认（brick/plate/tile/slope，可选 system_hint）→ 进度；通用采集为「导出」。

通用采集页与识别采集页**布局一致**，差异：无识别框、无类型确认，主行动为「导出」而非「上传」。

## 4. 帧回看 / 会话详情 / 采集库

- **帧查看器**：大图 + 顶部 **RGB / 深度** 分段切换；深度态显示色标图例 + 距离/清晰度读数；底部 删除 / 重拍 / 上一张 / 下一张。删除会同步清理本地留存文件。
- **会话详情**：头部识别结论（system · kind · units · pitch · 置信度 · 时间 · 来源）+ 逐帧网格（俯视/侧/角，带深度角标）+ 操作（预览模型 / 重新上传 / 删除；通用会话为 再次导出/分享）。
- **采集库列表**：历史会话，按 全部 / 已识别 / 待上传 / 已导出 筛选；标注来源（识别 / 通用）与设备；空态友好提示。

## 5. 结果与引导测量兜底（复用）

- `status = "recognized"`：展示识别结论 + 下载并预览 GLB（沿用 `assets/{id}` + 打包的 `model-viewer` WebView）。
- `status = "needs_measurement"`：进入引导测量表单（沿用 `MeasurementValidator` + 现有提交路径），按后端 `fields` + `guidance` 高亮卡位。

> 这两条路径的 UI 由现有 Activity **移植为 Compose 屏**，逻辑层（`RecognitionResultModel` / `MeasurementValidator`）不变。

## 6. 命名与品牌

- **显示名**：`app_name` → **拾模**（英文品牌 ScanForge，可作副标题/启动页）。
- **仅改品牌资源**：`app_name`、启动图标、主题色/设计令牌。
- **不动**：`applicationId` 与 Kotlin 源码包名保持 `com.johnymoo.arverify`，`FileProvider` authority 不变 —— **零重构风险**，可平滑覆盖安装。
- 若将来上架需要干净的安装身份，再单独评估改 `applicationId`（与源码包名可不同）。

## 7. 工程架构（Compose 重写 + AR 隔离）

```
UI 层（Compose）              screens + 深色 Material3 主题（颜色/形状/排版令牌）
状态层（ViewModel + StateFlow）每屏一个 VM；状态转移委托给纯逻辑控制器
领域层（纯 Kotlin · JVM 可测）  复用现有控制器 + 新增 3 个纯逻辑单元
AR/GL 层（薄 · 真机验证）      ARCore 会话 + RGB 渲染 + 新增实时深度热力着色器
数据层（文件系统）             每会话 manifest.json + 帧文件；库 = 扫描 captures/ 目录
```

- **导航**：单 Activity + Navigation-Compose（非相机屏）。
- **相机页隔离（降风险）**：采集页仍是 Compose，但 ARCore 相机 Surface 用 `AndroidView` 包 `GLSurfaceView`，放在**独立 Activity** 中，以隔离 AR 会话生命周期（resume/pause/camera）；Compose 控件作为浮层叠加。这是 ARCore + Compose 的主流稳妥写法。
- **降风险原则**：UI（Compose）不可在本机测，故把判断逻辑全部下沉到纯函数 + ViewModel 纯状态归约，Compose 仅做渲染与事件转发。

## 8. 组件映射

**直接复用（已单测/真机验证）：**
`CaptureWizardController`、`FrameQualityGate`、`ScaleMath`、`Sharpness`、`FocusDistanceModel`、`ArFrameExtractor`、`ArMetadata`/`ArMetadataSerializer`、`RecognitionResultModel`、`MeasurementValidator`、`CaptureConfig`、`Depth16PngWriter`、`MultipartBuilder`、`CaptureUploader`、`HttpUrlConnectionTransport`、`LocalRetention`、`PlyWriter`、`DepthColorMap`/`DepthColorizer`、`BackgroundRenderer`、`CapabilityChecker`/`RecommendedRoute`（移入诊断页）、`SystemBarInsetsPolicy`、model-viewer GLB 预览。

**新增纯逻辑（JVM 可测）：**
- `CoverageStateMachine`：自由模式"顶/侧/角是否齐、是否可上传"。
- `CaptureSession` / `Frame` 模型 + `SessionManifestCodec`：会话 manifest 读写（含 schema 版本号）。
- `CaptureLibraryRepository`：扫描 / 解析 / 删除会话（注入目录即可单测）。

**新增 AR/GL 薄层（真机验证）：**
- `DepthOverlayRenderer`：见 §10。

**新增状态层：**
- `CaptureViewModel`、`LibraryViewModel`、`SessionDetailViewModel`、`ResultViewModel`、`SettingsViewModel` 等；状态归约为纯函数。

## 9. 数据流与持久化

- **识别采集**：AR 帧 → `ArFrameExtractor`（RGB JPEG / DEPTH16 grid / 内参 / 位姿 / 距离）→ 写入 `CaptureSession` 并持久化 → 上传时组 `CapturePackage` → `CaptureUploader`（multipart + 重试）→ `RecognitionResultModel` → 结果屏 / 测量兜底。
- **通用采集**：AR 帧 → RGB + 16-bit 深度 PNG + PLY 点云 → 持久化会话 → 导出分享（不上传）。
- **实时深度叠加**：独立可视化路径，不持久化。
- **持久化（文件系统）**：`getExternalFilesDir()/captures/<session>/` 下放 `manifest.json` + 图像文件。
  - **写入用临时文件 + 原子 rename**，避免崩溃留下残缺 manifest。
  - 采集库通过**扫描该目录并解析 manifest** 构建（无数据库；会话数量级小，O(N) 扫描可接受）。
  - 删除会话 = 删整个会话目录。
  - 演进路径：若将来会话达上千 / 需富查询，再加 Room 作为**索引缓存**（manifest 仍为权威源）。

## 10. 实时深度热力叠加（核心新功能）

- `DepthOverlayRenderer`（GL 薄层）：每帧取 `acquireDepthImage16Bits()` → 上传为纹理 → 片元着色器用 `DepthColorMap` 把毫米值映射为颜色 → 按 alpha 混合到相机背景之上。开关 = 是否绘制该 pass。
- **纯逻辑可单测**：深度归一化 + `DepthColorMap` 配色映射。**默认按采集距离量程做固定量程上色**（保证逐帧颜色稳定、不闪烁），动态 min/max 作为兜底；策略可配置、真机调参。
- **真机验证**：GL 纹理上传、着色器、与 `BackgroundRenderer` 的混合顺序、低分辨率深度（约 160×120）与相机图对齐缩放。
- 深度不支持的设备：禁用深度开关并提示（沿用现有能力探测）。

## 11. 上传契约（不变）

`POST /api/v1/ar-captures`（multipart）与 `ar_metadata` JSON **完全沿用**配对规格 §5，后端零改动。任何改动须在 `BrickStudio/docs/ar-capture-recognition-design.md` 同步镜像，并与 `docs/contracts/ar-capture-contract.sample.json` 双向锁定。

## 12. 错误处理

- **复用**：ARCore 安装/可用性、相机权限、`CameraNotAvailableException`、深度 `NotYetAvailableException` 预热、厂商元数据类型容错（AF-state）、网络失败 → 本地留存 → 重试。
- **新增（Compose 层）**：相机权限说明态；AR 不可用 → 引导去「设备诊断」；深度不支持 → 禁用深度开关并提示；采集库空态；删除/导出的文件 IO 错误提示；manifest 解析失败 → 跳过坏会话并告警，不致崩溃。

## 13. 测试与验证

- **本机 JVM 单测（无需设备）**：全部领域纯逻辑（现有 + 新增 `CoverageStateMachine` / `SessionManifestCodec` / `CaptureLibraryRepository` 用临时目录）+ 深度归一化/配色 + **ViewModel 纯状态归约**（`kotlinx-coroutines-test`）。这是 Compose 重写的核心保障：UI 不可测但逻辑全可测。
- **Mac 真机验证清单（用户执行）**：
  - 实时深度叠加 + 开关；向导 & 自由采集流程。
  - 即时回看条 + 帧查看器 RGB/深度切换 + 删除/重拍。
  - 采集库 列表/筛选/详情/删除；通用导出 + 分享。
  - 识别上传 e2e → recognized → GLB 预览；`needs_measurement` → 测量表单提交。
  - 断网中途上传 → 失败提示 + 本地留存完整。
  - 留存 `ar_metadata.json` 与 `docs/contracts/ar-capture-contract.sample.json` 比对一致。

## 14. 依赖

- **新增**：Compose BOM、`activity-compose`、`navigation-compose`、`material3`(Compose)、`lifecycle-viewmodel-compose`、Coil（缩略图/帧加载）、`kotlinx-coroutines`(+`-test`)。
- **保留**：Gson、`HttpURLConnection` 传输、ARCore 1.54.0、现有 Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21 / JDK 17 骨架。

## 15. 实现阶段（高层顺序，详细顺序见实现计划）

1. 深色 Material3 主题 + 设计令牌；Compose 脚手架（MainActivity → Navigation）+ 品牌资源（拾模/ScanForge、图标、色）。
2. 领域新增（`CoverageStateMachine`、`CaptureSession`/`SessionManifestCodec`、`CaptureLibraryRepository`）+ **JVM 单测**（本机完成、去风险）。
3. Compose 屏：首页、采集库、会话详情、帧查看器、设置/诊断（含结果/测量移植）—— 真机验证。
4. 采集页（独立 Activity）：AR 渲染 + `DepthOverlayRenderer` 实时深度叠加 + Compose 浮层（向导/自由、质量条、回看条、快门）—— 真机验证。
5. 接通识别上传（复用）+ 通用导出；端到端闭环 —— 真机验证。

## 16. 成功标准

- 取景时能**实时看到深度热力**并可一键开关；不再只是一个距离数字。
- 拍完能在 App 内**逐帧回看 RGB/深度**，并在**采集库**查看历史会话、重传/导出/删除。
- 一块标准积木完成引导（或自由）采集 → 上传 → 正确 system/kind/units → 端上预览 GLB；非标件稳定走入引导测量兜底。
- 通用采集可导出 RGB / 16-bit 深度 PNG / PLY 并分享。
- 全 App 呈现统一的深色专业扫描风；显示名为「拾模 / ScanForge」。
- 本机 JVM 纯逻辑 + ViewModel 归约单测通过；Mac 真机闭环验证通过。

## 17. 非目标 / YAGNI

- 不做任意工件的真实公制重建（方案 B，后续阶段）。
- 不做端上**权威**识别（识别权威仍在后端）。
- 现阶段不引入数据库、不改 `applicationId`/源码包名、不做云同步/账户体系。
