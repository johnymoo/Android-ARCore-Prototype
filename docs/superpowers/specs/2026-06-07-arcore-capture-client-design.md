# Design Spec: ARCore 智能采集客户端（采集建模 · 采集端）

- **Date:** 2026-06-07
- **Repo:** `ARCore-prototype`（采集端 / Android·Kotlin·ARCore）
- **配对规格:** `BrickStudio/docs/ar-capture-recognition-design.md`（识别/建模后端）
- **Status:** 已批准设计，待实现计划
- **前置:** 能力验证 + 基础深度采集已完成并真机验证（PR #2，OPPO PLG110：ARCore 支持 / Depth 支持 / 推荐路线 深度采集）。

## 0. 背景与定位

这是"采集建模"子项目的**采集端**。整体走 **方案 C（识别优先，混合分阶段）**：用户用 ARCore app 做**最少操作**的引导式采集，由 BrickStudio 后端**自动识别**标准积木（system + kind + 凸点网格 `units_x×units_y`），用 AR 的公制尺度判别与校验，**零卡尺**生成精确参数化 GLB；仅当识别置信度不足时，才以**带视觉指引的最小卡尺**兜底。

**职责切分**：本仓库 = **智能采集客户端**（看得清、采得全、带公制尺度）；`BrickStudio` = 识别 + 建模（权威）。两侧的集成缝是第 5 节的**上传契约**。

> 关键工程约束：本开发机**无 Android SDK / Gradle / JDK 17，Android 端无法在此编译**。只能在此跑 JVM 纯逻辑单测；AR 会话与图像 CV 由用户在 Android Studio **真机验证**。因此重的识别智能放后端（见配对规格），本端只保留薄层。

## 1. 目标与范围

### 本子项目做（In scope）
- **引导式采集**：拍少量关键帧——
  1. **俯视凸点帧（最关键）**：正上方对准凸点面，附 **RGB + DEPTH16 + 相机内参 + 相机位姿**（后端据此数凸点、算公制节距）。
  2. **侧视帧**：供 kind（brick/plate/tile/slope）的用户轻确认。
  3. **≥2 张补充角度**：凑够 ≥4 张，兼容 BrickStudio 照片路径 / v0.5 并排对比 / 将来重建。
- **端上"粗引导"层（轻量、不权威，D1）**：基于 ARCore 现成信号 + 简单启发式做实时采集质量提示（距离在量程内、追踪稳定、帧锐度、覆盖 顶/侧/角）。可选粗略凸点计数预览（仅展示，权威计数在后端）。
- **公制尺度自动化（D2）**：用 ARCore 内参/位姿/到物体距离自动携带公制信息，**无需用户摆标尺/标记物**。
- **上传与闭环**：打包上传到 BrickStudio 新接入口 → 复用其 job/SSE 显示进度 → 拿回 GLB + 识别结论 + 置信度 → 端上预览。
- **引导卡尺兜底（基础版）**：后端返回 `needs_measurement` 时，弹出 5 测量表单并配清晰视觉指引（高亮"卡哪两条边"，而非"找凸点中心"）。

### 本子项目不做（Deferred）
- 任意工件/工装的**真实公制重建**（方案 B，后续阶段）。
- 亚毫米级 AR 自动测量、AR 卡尺实时高亮叠加。
- 端上**权威**识别（本期识别权威在后端）。

## 2. 采集流程与端上 UX

单次采集向导（在现有 `CaptureActivity` 的 AR 会话基础上扩展）：

1. **进入采集** → AR 会话启动（复用现有 `Session` + `BackgroundRenderer` + 深度配置）。
2. **俯视凸点帧引导**：提示"正上方对准凸点面、距离 ~20–30cm、保持清晰"。满足条件 + 追踪 `TRACKING` 时采集该帧的：
   - RGB（相机图）
   - `acquireDepthImage16Bits()` → DEPTH16
   - `frame.camera.getImageIntrinsics()` → fx/fy/cx/cy + 图像尺寸
   - `frame.camera.pose` → 平移 + 四元数
   - 到物体距离（深度图中心区域中位值，或平面 hit-test）
3. **侧视帧**：提示拍一张侧面（供 kind 确认与未来用途）。
4. **补充角度**：再拍 ≥2 张，凑够 ≥4 张。
5. **kind 轻确认**：弹出 brick/plate/tile/slope 选择（默认 brick）；可选 `system_hint`（含"非标/未知"，选中即触发兜底）。
6. **打包上传** → 进度（SSE）→ 结果。

### 端上"粗引导"层（不权威）
仅用：追踪状态、到平面/物体距离是否在量程、帧锐度（简单指标）、覆盖度状态机（顶/侧/角是否已采）。粗略凸点计数（若做）仅供用户参考。**任何需要图像 CV 的部分保持很薄，真机验证**。

## 3. 端上组件与可测性

| 组件 | 职责 | JVM 可单测 |
|---|---|---|
| `CaptureWizardController` | 采集向导状态机（顶/侧/角覆盖度、是否可上传） | **是**（状态机纯逻辑） |
| `FrameQualityGate` | 距离区间、追踪稳定、锐度阈值判定 | **是**（纯逻辑；锐度取值来自薄 CV 层） |
| `ScaleMath` | 像素↔公制换算、深度中心距离估计的纯数学 | **是** |
| `ArMetadataSerializer` | 组装 `ar_metadata` JSON（内参/位姿/距离/设备/粗提示） | **是** |
| `CaptureUploader` | multipart 组包 + 上传 + 重试 + 本地留存 | 部分（网络层 mock） |
| `RecognitionResultModel` | 解析后端响应（recognized / needs_measurement） | **是** |
| AR 会话 / 深度获取 / 图像 CV / 渲染 | ARCore + GL + 薄 CV | 否（真机验证） |

> 原则：凡能抽成"输入数组/数值 → 输出数值/状态"的，一律抽成可单测纯函数；ARCore 与图像处理留薄层。

## 4. 结果处理与 UI

- **`status = "recognized"`**：展示识别结论（system / kind / units_x×units_y / pitch_mm / confidence）+ 下载并预览 GLB（`GET /assets/{id}`）。
- **`status = "needs_measurement"`**：进入**引导卡尺兜底**——按后端返回的 `fields` + `guidance` 弹出 5 测量表单，复用 BrickStudio 现有 `measure-block-*.svg` 标注图与"卡可夹位置、`(1A+1B)/2` 互消误差"的原则，高亮该卡的边；提交走**现有 `POST /parametric-blocks`**。

## 5. 上传契约（集成缝 · 与后端共享）

> **这是两仓库的权威集成契约；任何改动必须在配对规格 `BrickStudio/docs/ar-capture-recognition-design.md` 同步镜像。**

**`POST /api/v1/ar-captures`** · `multipart/form-data`

| 字段 | 类型 | 说明 |
|---|---|---|
| `part_id` | str (1–64) | 客户端生成 |
| `kind` | enum | 用户轻确认：`brick`/`plate`/`tile`/`slope`（默认 `brick`）|
| `system_hint` | enum? | 可选；`lego`/`duplo`/`feile`/`generic`/`unknown`。`unknown` 触发兜底 |
| `images[]` | File[] | 角度照片（≥4，JPEG/PNG）|
| `recognition_rgb` | File | 俯视凸点帧 RGB |
| `recognition_depth` | File | 该帧 DEPTH16，编码为 **16-bit PNG（毫米/像素，无损）** |
| `ar_metadata` | str(JSON) | 见下 |

`ar_metadata` JSON：
```json
{
  "device": {"model": "PLG110", "arcore": "1.54.260890493"},
  "recognition_frame": {
    "image_intrinsics": {"fx": 0, "fy": 0, "cx": 0, "cy": 0, "width": 0, "height": 0},
    "depth": {"width": 0, "height": 0, "format": "DEPTH16_MM"},
    "camera_pose": {"t": [0, 0, 0], "q": [0, 0, 0, 1]},
    "distance_m": 0.25
  },
  "coarse_hints": {"rough_units_x": 2, "rough_units_y": 4, "rough_pitch_mm": 16.2}
}
```

约定：
- **深度↔RGB 分辨率不同**（ARCore 深度图分辨率更低、与相机图对齐）：契约同时携带 RGB 内参与深度图尺寸，**由后端把凸点像素坐标缩放到深度坐标**再反投影。
- `coarse_hints` 为端上粗引导结果，仅作建议/校验，**不权威**。
- 传输：原生 app 走明文 HTTP 到局域网 IP（不受浏览器 HTTPS/getUserMedia 限制）。

**响应**（后端定义，本端解析）：
```json
{
  "capture_id": "uuid", "job_id": "uuid",
  "recognized": {"system": "feile", "kind": "brick", "units_x": 2, "units_y": 4,
                 "pitch_mm": 16.1, "confidence": 0.93},
  "status": "recognized",
  "needs_measurement": null
}
```
`status = "needs_measurement"` 时 `recognized` 可为部分值，`needs_measurement = {fields: [...], guidance: "..."}`。

## 6. 错误处理（端侧）

- **AR/相机层**：复用现有处理（ARCore 安装/可用性异常、相机权限、`CameraNotAvailableException`、`NotYetAvailableException`/深度未就绪 → 提示对准重试）。
- **采集质量**：距离超量程/追踪不稳/不清晰 → `FrameQualityGate` 实时拦截，不采坏帧。
- **网络/上传失败**：本地先留一份采集包（导出目录，复用现有 exports 路径），失败可重试，数据不丢。
- **配置**：BrickStudio base URL 在 app 内可配置。

## 7. 测试与验证

- **JVM 单测**（本机可跑，无设备）：`CaptureWizardController` 状态机、`FrameQualityGate` 阈值、`ScaleMath` 换算、`ArMetadataSerializer` JSON、`RecognitionResultModel` 解析。
- **真机验证**（Android Studio，用户执行）：AR 会话、深度获取、图像锐度、上传闭环、GLB 预览、兜底卡尺表单。
- **契约对齐**：`ar_metadata` JSON 与 multipart 字段须与后端离线夹具（见配对规格）一致；建议用一份共享样例 JSON 双向锁定。

## 8. 落地与配置

- 复用现有 Gradle 8.9 / AGP 8.7.3 / Kotlin 2.0.21 / JDK 17 / ARCore 1.54.0 骨架。
- 新增网络依赖（建议轻量：`HttpURLConnection` 或 OkHttp）+ multipart 组包。
- 推荐落地顺序（全局）：**后端 + 离线夹具 e2e 先行**（本机可验证、去风险）→ 再做本采集端（真机验证）。
- 可配置项：BrickStudio base URL、采集距离区间、锐度阈值。

## 9. 成功标准

- 用户对一块标准积木完成引导采集（俯视 + 侧视 + 角度，全程实时提示），**无需卡尺**。
- 上传后端返回正确的 system / kind / units，并在端上预览到对应 GLB。
- 非标/未知件能稳定走入引导卡尺兜底，并成功经 `/parametric-blocks` 出 GLB。
- 端上纯逻辑单测通过；真机闭环验证通过。
