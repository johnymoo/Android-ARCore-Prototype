# Design Spec: ARCore Capability Verification + Depth Capture Prototype

- **Date:** 2026-06-06
- **Source:** Issue #1 ("Design spec") of `johnymoo/Android-ARCore-Prototype` — a Chinese design doc titled 安卓原型验证方案 (Android Prototype Verification Plan)
- **Target device:** OPPO Find X9 Pro (PLG110), Google Play Store + Play Services working
- **Status:** Approved design, pending implementation plan

## 1. Goal & Scope

Build a single-screen native Android app (Kotlin + ARCore SDK) that gives a **trustworthy yes/no** on whether the target phone supports **ARCore** and the **Depth API**, and — when Depth is supported — captures a depth frame and a point cloud. It produces an exportable **device compatibility report** and a **recommended route** (depth capture vs. photo modeling).

### In scope (this sub-project)
- ARCore availability detection + install flow.
- Depth API support detection (AUTOMATIC depth mode; also probe raw depth).
- Device compatibility report (on-screen + exportable JSON + readable text).
- Recommended-route decision (深度采集 depth capture / 照片建模 photo modeling).
- Depth capture when supported: single colorized 2D depth map preview + point cloud export (`.ply`) + colorized depth `.png`.
- Capture guidance surfaced as on-screen tips (lighting, ruler, angles, part count).

### Explicitly deferred (later sub-projects)
- OpenCV marker recognition / photo-modeling fallback pipeline.
- Backend photogrammetry / mesh reconstruction (`.obj` / `.glb`).
- Dimension/measurement features.
- Interactive 3D point-cloud viewer.

The report still *recommends* the photo route when appropriate; it just does not implement it here.

### Decomposition note
Issue #1 bundles several independent subsystems (capability checks, depth/point-cloud capture, mesh export, OpenCV fallback, backend photogrammetry). This spec covers the **first sub-project**: the capability-verification gate plus basic depth capture. The spec's own MVP conclusion treats verification as the gate before any modeling route, which justifies this slice.

## 2. Key Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Stack | Native Android, Kotlin + ARCore SDK | First-party, authoritative ARCore/Depth APIs; lightest to build; no game engine. |
| UI toolkit | XML Views + ViewBinding + Material 3 | Avoids Jetpack Compose's tight Kotlin-compiler coupling; lower risk of a version-mismatch build break, since the project cannot be compiled on the dev box. |
| Install requirement | **AR Optional** (not AR Required) | The app must install and run on AR-incapable devices so it can *report* "unsupported." |
| Depth preview fidelity | Colorized 2D depth map + `.ply` export | Robust, low-risk; full 3D viewer deferred. |
| Delivery | Complete Android Studio project; user builds & runs to phone over USB | User chose Android Studio handoff. |
| Repo | Local git repo, push to `johnymoo/Android-ARCore-Prototype` (currently empty) | Aligns with issue source. |

## 3. Architecture & Components

Each unit is small, single-purpose, and isolated so device-independent logic is unit-testable on the JVM.

| Component | Responsibility | Android deps | Testable on JVM |
|---|---|---|---|
| `DeviceInfo` | Collect manufacturer, model, Android version, SDK level, OpenGL ES version, build fingerprint | minimal | partial |
| `CapabilityChecker` | `ArCoreApk.checkAvailability()`, `requestInstall()`, create a throwaway `Session` to query `isDepthModeSupported(AUTOMATIC)` and raw depth; return `CapabilityReport` | yes | no |
| `RecommendedRoute` | Pure function: (arcoreOk, depthOk) → route enum | no | **yes** |
| `CapabilityReport` (+ `toJson()` / `toReadableText()`) | Data model + serialization | no | **yes** |
| `ReportExporter` | Write JSON + readable report to files; build Android share intents | yes | no |
| `ArCaptureController` | Minimal AR `Session` lifecycle + `GLSurfaceView` camera-background renderer; on shutter, `acquireDepthImage16Bits()` + `acquirePointCloud()` | yes | no |
| `DepthColorizer` | DEPTH16 `Image` → colorized `Bitmap` (near=warm, far=cool); value→color math isolated | partial | **yes** (mapping) |
| `PlyWriter` | Pure function: point `FloatBuffer` (x,y,z,confidence) → ASCII `.ply` text | no | **yes** |
| `MainActivity` + XML layout | Wire UI to the above | yes | no |

## 4. Data Flow

1. **Launch** → `MainActivity` runs `ArCoreApk.checkAvailability()` → render status card.
2. **检查 ARCore** → availability check; if `SUPPORTED_NOT_INSTALLED`, call `requestInstall()` → Play Store installs *Google Play Services for AR* → re-check.
3. **检查 Depth API** → create temporary `Session`, query `isDepthModeSupported(AUTOMATIC)` (and raw depth) → update card.
4. **Status → route** → `RecommendedRoute` computes 深度采集 / 照片建模.
5. **开始采集** (enabled only if ARCore + camera ok) → capture screen with AR session + live camera background + tips card → shutter → acquire depth image + point cloud → colorize → preview → save `.ply` + depth `.png`.
6. **导出结果** → `ReportExporter` writes JSON + readable report (+ captured files) → Android share sheet / save to Downloads.

### Recommended-route truth table
| ARCore | Depth | Route |
|---|---|---|
| not supported | — | 照片建模 (photo modeling) |
| supported | not supported | 照片建模 (photo modeling) |
| supported | supported | 深度采集 (depth capture) |

## 5. Main Screen (matches issue #1 mockup)

One screen titled **原型验证工具**:
- Buttons: **检查 ARCore · 检查 Depth API · 开始采集 · 导出结果**
- **当前检测结果** card: `ARCore: 支持/不支持`, `Depth: 支持/不支持`, `推荐路线: 深度采集 / 照片建模`
- Capture is a second screen (camera background + shutter + colorized depth preview).
- UI strings in **Chinese** to match the mockup; English kept as secondary comments in `strings.xml`.

## 6. Capture Guidance (spec §4)

Lightweight tips card, informational only (no enforcement):
- Uniform lighting + simple background.
- Keep a ruler / marker in frame.
- Shoot front / side / top / angle.
- Test 1–3 parts at a time.

## 7. Error Handling

- **Camera permission**: runtime request; on denial show rationale; report notes AR can't run.
- **ARCore install/availability exceptions** (`UnavailableDeviceNotCompatibleException`, `UnavailableApkTooOldException`, `UnavailableSdkTooOldException`, `UnavailableUserDeclinedInstallationException`, `CameraNotAvailableException`): caught and surfaced in the report rather than crashing — a failure is still a useful verification result.
- **Depth unsupported**: capture button disabled, route → 照片建模.
- **Defensive China-ROM note**: even though the target's Play Store works, if `requestInstall` ever fails, show a hint to install *Google Play Services for AR* from the OPPO App Market / sideload.

## 8. Testing & Verification

- **JVM unit tests** (Gradle, no device) for pure logic: `RecommendedRoute` truth table, `PlyWriter` output, `CapabilityReport` JSON, depth value→color mapping.
- **Manual on-device verification** for AR/session/UI following the capture spec — the real proof that ARCore works on the phone.
- **Key risk:** the Linux dev box has **no Android SDK, Gradle, or JDK 17**, so the full app cannot be compiled here. Mitigation: known-good project skeleton with pinned, mutually-compatible versions and canonical ARCore boilerplate; final compile/run happens in the user's Android Studio. Optional later: a GitHub Actions workflow as a compile safety net (out of scope now).

## 9. Versions & Configuration (targets, to be finalized in plan)

- `minSdk 24` (ARCore minimum), `compileSdk` / `targetSdk` 35.
- AGP 8.x / Gradle 8.x / Kotlin 2.x / JDK 17.
- ARCore SDK: latest stable `com.google.ar:core` (exact version pinned during implementation).
- Permissions: `CAMERA`. Manifest: `com.google.ar.core` meta-data = `optional`; `uses-feature android.hardware.camera.ar` = not required.

## 10. Deliverables

- Complete Android Studio Gradle project (wrapper included) under this working directory, as a git repo pushable to `johnymoo/Android-ARCore-Prototype`.
- `README` (Chinese): open in Android Studio, enable USB debugging, run to phone, interpret the report, where exports land, troubleshooting.

## 11. Success Criteria

- App installs and runs on the PLG110 regardless of AR capability (AR Optional).
- Correctly reports ARCore availability and Depth API support, with device info.
- When Depth is supported: produces a colorized depth preview and exports a valid `.ply` point cloud.
- Exports a shareable compatibility report (JSON + readable text) with a recommended route.
- Pure-logic unit tests pass.
