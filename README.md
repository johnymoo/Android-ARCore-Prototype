# 原型验证工具 (ARCore Verification)

验证安卓手机是否支持 **ARCore** 与 **Depth API**，并在支持时采集深度图与点云。
对应设计：`docs/superpowers/specs/2026-06-06-arcore-verification-prototype-design.md`。

## 构建与运行
1. 用 **Android Studio** 打开本项目根目录，等待 Gradle 同步（首次会自动生成 wrapper）。
2. 手机开启「开发者选项 → USB 调试」，用数据线连接电脑。
3. 点击 ▶ Run，选择你的手机（OPPO Find X9 Pro / PLG110），安装并启动。
   - 命令行等价：`./gradlew assembleDebug` 生成 `app/build/outputs/apk/debug/app-debug.apk`。

## 使用
- **检查 ARCore**：若未安装会跳转 Play 商店安装「Google Play Services for AR」。
- **检查 Depth API**：刷新检测结果。
- **当前检测结果**：显示 ARCore / Depth 是否支持，以及推荐路线（深度采集 / 照片建模）。
- **开始采集**（ARCore + Depth 均支持时可用）：进入相机界面，对准零件点「拍摄」，
  生成彩色深度图预览，并保存深度图 PNG 与点云 `.ply`。
- **导出结果**：写出兼容性报告（JSON + 文本）并通过系统分享。

## 导出文件位置
`Android/data/com.johnymoo.arverify/files/exports/`
- `compat_report.json` / `compat_report.txt`：兼容性报告
- `depth_*.png`：彩色深度图
- `points_*.ply`：点云（可用 MeshLab / CloudCompare 打开）

## 采集建议
光线均匀、背景简洁、画面保留尺子或标记物，分别拍正面/侧面/顶面/斜角，每次 1~3 个零件。

## 备注
- 应用为 **AR Optional**：即使设备不支持 AR 也能安装并显示「不支持」结论。
- 若为无 Google 服务的国行 ROM 且安装失败，可从 OPPO 应用商店或侧载安装
  「Google Play Services for AR」。
