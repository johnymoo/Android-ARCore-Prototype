# Android ARCore 原型验证工具

一个原生 Android（Kotlin）原型应用，用于**权威验证**手机是否支持 **ARCore** 与 **Depth API**，
并在支持时采集彩色深度图与点云。首要目标是回答一个具体问题：**ARCore 在我的安卓手机上能不能用？**

> 来源：本仓库 Issue #1《Design spec》中的设计方案 —— 安卓原型验证方案
> （先验证 ARCore、Depth API，再决定建模路线）。

## 背景与目标

- 验证 **ARCore** 是否可用。
- 验证 **Depth API** 是否支持。
- 不支持时的后备路线：高清照片 + OpenCV + 后端重建（本期不实现，仅在报告中给出建议）。

目标设备：**OPPO Find X9 Pro（PLG110）**，已安装可用的 Google Play 商店与 Google Play 服务。

## 本期范围（第一子项目）

✅ 纳入：
- ARCore 可用性检测与安装引导。
- Depth API 支持检测（AUTOMATIC 深度模式，并探测 Raw Depth）。
- 设备兼容性报告（屏幕显示 + 导出 JSON + 可读文本）。
- 推荐路线判定（深度采集 / 照片建模）。
- 深度采集（支持时）：彩色 2D 深度图预览 + 点云导出 `.ply` + 深度图 `.png`。
- 采集规范提示（光线、背景、尺子/标记、多角度、零件数量）。

⏳ 推迟到后续子项目：
- OpenCV 标记识别 / 照片建模后备流程。
- 后端摄影测量 / 网格重建（`.obj` / `.glb`）。
- 尺寸测量、稠密深度点云、交互式 3D 点云查看器。

## 技术栈

- Kotlin + ARCore SDK `com.google.ar:core:1.54.0`
- AndroidX（AppCompat、Material 3、Activity、ConstraintLayout）
- XML 视图 + ViewBinding；OpenGL ES 2.0；JUnit 4
- AGP 8.7.3 / Gradle 8.9 / Kotlin 2.0.21 / JDK 17
- `minSdk 24`，`compile/targetSdk 35`
- 安装方式为 **AR Optional**：即使设备不支持 AR 也能安装并显示「不支持」结论。

## 当前状态

| 阶段 | 状态 |
| --- | --- |
| 设计方案（Spec） | ✅ 已完成 |
| 实施计划（Plan） | ✅ 已完成 |
| 应用代码实现 | ⏳ 待开始 |

应用代码尚未实现。下一步将按实施计划逐任务生成完整的 Android Studio 工程，
交付后在 Android Studio 中打开、构建并通过 USB 运行到手机。

## 文档索引

- 设计方案：[`docs/superpowers/specs/2026-06-06-arcore-verification-prototype-design.md`](docs/superpowers/specs/2026-06-06-arcore-verification-prototype-design.md)
- 实施计划：[`docs/superpowers/plans/2026-06-06-arcore-verification-prototype.md`](docs/superpowers/plans/2026-06-06-arcore-verification-prototype.md)

## 实现后的使用方式（预览）

> 以下流程将在代码实现后生效。

1. 用 **Android Studio** 打开本仓库根目录，等待 Gradle 同步。
2. 手机开启「开发者选项 → USB 调试」，用数据线连接电脑。
3. 点击 ▶ Run，选择手机（PLG110）安装并启动。
4. 主界面操作：**检查 ARCore** → **检查 Depth API** → 查看「当前检测结果」与推荐路线；
   若支持深度则可 **开始采集**，最后 **导出结果**。

导出文件位置：`Android/data/com.johnymoo.arverify/files/exports/`
（`compat_report.json` / `compat_report.txt` / `depth_*.png` / `points_*.ply`）。

## 采集建议

光线均匀、背景简洁、画面保留尺子或标记物，分别拍正面 / 侧面 / 顶面 / 斜角，每次 1~3 个零件。
