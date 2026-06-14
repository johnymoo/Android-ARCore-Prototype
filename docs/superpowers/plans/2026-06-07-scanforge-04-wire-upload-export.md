# 拾模 / ScanForge · Plan 4 —— 接通：识别上传 · 通用导出 · 结果/测量 · 端到端

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Plan 3 采集页的「上传/导出」接上真实行为：识别采集 → 组 `CapturePackage` → `CaptureUploader` 上传 → 复用 `ResultActivity`（GLB 预览）/`MeasurementFormActivity`（兜底）；通用采集 → 保存点云并通过系统分享导出；采集库会话支持重新上传/预览。完成端到端闭环。

**Architecture:** 沿用既有上传契约与组件（`CaptureUploader`/`MultipartBuilder`/`HttpUrlConnectionTransport`/`ArMetadataSerializer`/`RecognitionResultModel`/`UploadResultHolder`/`ResultActivity`/`MeasurementFormActivity`/`PlyWriter`/`ReportExporter`），**后端零改动**。采集时把识别契约工件（`ar_metadata.json` + `recognition_rgb.jpg` + `recognition_depth.png`）持久化到会话目录，使「即时上传」与「库内重传」走同一条 `CaptureUploadAssembler`（读目录→`CapturePackage`）。状态映射与组包逻辑抽成纯函数（JVM 单测）。

**Tech Stack:** 既有 net/metadata/export 模块 + Plan 1 `session.*` + Plan 3 采集页。设计：spec §5（契约，不变）、§4（结果/测量）、§11、§12（错误处理）。

**前置：** Plan 1/2/3 已合并。

## 关键约束

- **[真机]** AR/上传/分享在 Mac + PLG110 + 局域网后端验证；**[JVM]** Task 1、4 纯逻辑。
- 上传契约严格沿用 spec §5；改动须同步 `BrickStudio` 配对规格与 `docs/contracts/ar-capture-contract.sample.json`。
- 结果/测量**复用既有 Activity**（WebView GLB 预览已验证），不在本计划做 Compose 移植（可后续）。

## 文件结构

**新建（main）：**
- `session/SessionStatusMapper.kt`（**纯，JVM 可测**：`RecognitionStatus` → `SessionStatus`）
- `net/CaptureUploadAssembler.kt`（**纯/IO，JVM 可测**：会话目录 → `CapturePackage`）
- `capture/KindDialog.kt`（Compose 类型选择对话框）

**新建测试：** `SessionStatusMapperTest.kt`、`CaptureUploadAssemblerTest.kt`

**修改：**
- `capture/CaptureSessionWriter.kt`：加 `writeRecognition(...)`（写契约工件）。
- `capture/CaptureRenderer.kt`：识别 TOP 帧写契约工件；通用帧存点云 PLY。
- `capture/CaptureScreenState.kt`：`CaptureUiState` 增 `sessionDir`、`topReady`。
- `capture/ScanCaptureActivity.kt`：`onFinish` 接识别上传 / 通用导出。
- `ui/session/SessionDetailScreen.kt`：重新上传 / 预览模型 接 `CaptureUploadAssembler`。

---

## Task 1: SessionStatusMapper（识别状态 → 会话状态，纯逻辑）

**Files:** Create `session/SessionStatusMapper.kt`, Test `SessionStatusMapperTest.kt`

- [ ] **Step 1: [JVM] 写失败测试**

`app/src/test/java/com/johnymoo/arverify/SessionStatusMapperTest.kt`:

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.net.RecognitionStatus
import com.johnymoo.arverify.session.SessionStatus
import com.johnymoo.arverify.session.SessionStatusMapper
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionStatusMapperTest {
    @Test fun recognizedMapsToRecognized() {
        assertEquals(SessionStatus.RECOGNIZED, SessionStatusMapper.fromRecognition(RecognitionStatus.RECOGNIZED))
    }
    @Test fun needsMeasurementMaps() {
        assertEquals(SessionStatus.NEEDS_MEASUREMENT, SessionStatusMapper.fromRecognition(RecognitionStatus.NEEDS_MEASUREMENT))
    }
    @Test fun unknownStaysPendingUpload() {
        assertEquals(SessionStatus.PENDING_UPLOAD, SessionStatusMapper.fromRecognition(RecognitionStatus.UNKNOWN))
    }
}
```

- [ ] **Step 2: [JVM] 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.SessionStatusMapperTest"`
Expected: FAIL —— `Unresolved reference: SessionStatusMapper`。

- [ ] **Step 3: 实现**

`app/src/main/java/com/johnymoo/arverify/session/SessionStatusMapper.kt`:

```kotlin
package com.johnymoo.arverify.session

import com.johnymoo.arverify.net.RecognitionStatus

/** Maps a backend recognition status to the persisted session status (spec §4). */
object SessionStatusMapper {
    fun fromRecognition(status: RecognitionStatus): SessionStatus = when (status) {
        RecognitionStatus.RECOGNIZED -> SessionStatus.RECOGNIZED
        RecognitionStatus.NEEDS_MEASUREMENT -> SessionStatus.NEEDS_MEASUREMENT
        RecognitionStatus.UNKNOWN -> SessionStatus.PENDING_UPLOAD
    }
}
```

- [ ] **Step 4: [JVM] 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.SessionStatusMapperTest"`
Expected: PASS（3 tests）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/session/SessionStatusMapper.kt \
        app/src/test/java/com/johnymoo/arverify/SessionStatusMapperTest.kt
git commit -m "feat: add SessionStatusMapper (recognition->session status)"
```

---

## Task 2: 持久化识别契约工件（CaptureSessionWriter.writeRecognition）

**Files:** Modify `capture/CaptureSessionWriter.kt`

- [ ] **Step 1: 加 `writeRecognition` 方法**

In `capture/CaptureSessionWriter.kt`, add imports and a method inside the class:

```kotlin
import com.johnymoo.arverify.metadata.ArMetadata
import com.johnymoo.arverify.metadata.ArMetadataSerializer
import com.johnymoo.arverify.metadata.CameraIntrinsics
import com.johnymoo.arverify.metadata.CameraPose
import com.johnymoo.arverify.metadata.DepthDims
import com.johnymoo.arverify.metadata.DeviceMeta
import com.johnymoo.arverify.metadata.RecognitionFrameMeta
import com.johnymoo.arverify.imaging.Depth16PngWriter
```

```kotlin
    /** Writes the upload contract artifacts (spec §5) for the top-down recognition frame. */
    fun writeRecognition(
        intrinsics: CameraIntrinsics, pose: CameraPose, distanceM: Double,
        depthGridMm: IntArray, depthW: Int, depthH: Int,
        rgbJpeg: ByteArray, arcoreVersion: String,
    ) {
        File(dir, "recognition_rgb.jpg").writeBytes(rgbJpeg)
        File(dir, "recognition_depth.png").writeBytes(Depth16PngWriter.encode(depthGridMm, depthW, depthH))
        val meta = ArMetadata(
            device = DeviceMeta(model = deviceModel, arcore = arcoreVersion),
            recognitionFrame = RecognitionFrameMeta(
                imageIntrinsics = intrinsics,
                depth = DepthDims(depthW, depthH),
                cameraPose = pose,
                distanceM = distanceM,
            ),
            coarseHints = null,
        )
        File(dir, "ar_metadata.json").writeText(ArMetadataSerializer.toJson(meta))
    }
```

> `deviceModel` 为构造参数（已存在）。复用既有 `ArMetadata*`、`Depth16PngWriter`、契约字段（spec §5）。

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/CaptureSessionWriter.kt
git commit -m "feat: persist recognition contract artifacts to session dir"
```

---

## Task 3: 采集时落工件（识别 TOP 写契约；通用存点云）

**Files:** Modify `capture/CaptureRenderer.kt`, `capture/CaptureScreenState.kt`

- [ ] **Step 1: CaptureUiState 增字段**

In `capture/CaptureScreenState.kt`, add to `CaptureUiState`:

```kotlin
    val topReady: Boolean = false,   // recognition: top-down contract frame captured
```

- [ ] **Step 2: Renderer——识别 TOP 帧写契约工件**

In `CaptureRenderer.capture(frame)`, after computing `gridVals/gw/gh/dist` and before `writer.addFrame(...)`, insert:

```kotlin
            if (mode == CaptureMode.RECOGNITION && slot == CaptureSlot.TOP &&
                gridVals != null && gw > 0 && gh > 0) {
                val intr = ArFrameExtractor.intrinsics(frame)
                val pose = ArFrameExtractor.pose(frame)
                writer.writeRecognition(intr, pose, dist ?: 0.0, gridVals!!, gw, gh, rgb, arcoreVersion)
                holder.state.value = holder.state.value.copy(topReady = true)
            }
```

Add a constructor param `arcoreVersion: String` to `CaptureRenderer` (passed from the Activity), and `sessionDir: java.io.File = writer.dir` exposure via holder. After `writer.addFrame(...)`, also set the session dir once:

```kotlin
            holder.state.value = holder.state.value.copy(frames = frames, sessionDir = writer.dir.absolutePath, ...)
```

Add `val sessionDir: String = ""` to `CaptureUiState`.

- [ ] **Step 3: Renderer——通用模式存点云 PLY**

In `CaptureRenderer.capture(frame)`, after `writer.addFrame(...)` for `mode == CaptureMode.GENERAL`, append point-cloud export:

```kotlin
            if (mode == CaptureMode.GENERAL) {
                try {
                    frame.acquirePointCloud().use { pc ->
                        val buf = pc.points
                        val arr = FloatArray(buf.remaining()); buf.get(arr)
                        java.io.File(writer.dir, "points_${frames.size - 1}.ply")
                            .writeText(com.johnymoo.arverify.export.PlyWriter.toAsciiPly(arr))
                    }
                } catch (e: com.google.ar.core.exceptions.NotYetAvailableException) { /* no cloud yet */ }
            }
```

- [ ] **Step 4: 传 arcoreVersion**

In `ScanCaptureActivity`, compute and pass `arcoreVersion` into `CaptureRenderer`:

```kotlin
        val arcore = try { packageManager.getPackageInfo("com.google.ar.core", 0).versionName ?: "" } catch (e: Exception) { "" }
        renderer = CaptureRenderer({ session }, config, mode, holder, writer, windowManager, arcore)
```

- [ ] **Step 5: [真机] 验证落盘**

Run (Mac): `./gradlew :app:installDebug`；识别采集拍 TOP 后，`adb shell run-as`/文件管理查看会话目录含 `recognition_rgb.jpg`、`recognition_depth.png`、`ar_metadata.json`；通用采集目录含 `points_*.ply`。比对 `ar_metadata.json` 与 `docs/contracts/ar-capture-contract.sample.json` 字段一致。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/CaptureRenderer.kt \
        app/src/main/java/com/johnymoo/arverify/capture/CaptureScreenState.kt \
        app/src/main/java/com/johnymoo/arverify/capture/ScanCaptureActivity.kt
git commit -m "feat: capture writes recognition contract + general point cloud"
```

---

## Task 4: CaptureUploadAssembler（会话目录 → CapturePackage）

**Files:** Create `net/CaptureUploadAssembler.kt`, Test `CaptureUploadAssemblerTest.kt`

- [ ] **Step 1: [JVM] 写失败测试**

`app/src/test/java/com/johnymoo/arverify/CaptureUploadAssemblerTest.kt`:

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.net.CaptureUploadAssembler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CaptureUploadAssemblerTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun seed(dir: File) {
        dir.mkdirs()
        File(dir, "ar_metadata.json").writeText("""{"device":{"model":"PLG110"}}""")
        File(dir, "recognition_rgb.jpg").writeBytes(byteArrayOf(1, 2))
        File(dir, "recognition_depth.png").writeBytes(byteArrayOf(3, 4))
        File(dir, "frame_0.jpg").writeBytes(byteArrayOf(5))
        File(dir, "frame_1.jpg").writeBytes(byteArrayOf(6))
        File(dir, "depthc_0.png").writeBytes(byteArrayOf(7)) // colorized review copy — must NOT be uploaded
    }

    @Test fun assemblesPackageFromDir() {
        val dir = tmp.newFolder("part-x_123")
        seed(dir)
        val pkg = CaptureUploadAssembler.fromDir(dir, partId = "part-x", kind = "brick", systemHint = null)!!
        assertEquals("part-x", pkg.partId)
        assertEquals("brick", pkg.kind)
        assertTrue(pkg.arMetadataJson.contains("PLG110"))
        assertEquals(2, pkg.recognitionRgb.bytes.size)
        assertEquals(2, pkg.recognitionDepth.bytes.size)
        assertEquals(listOf("frame_0.jpg", "frame_1.jpg"), pkg.images.map { it.filename }) // sorted, no depthc_*
    }

    @Test fun missingContractFilesReturnsNull() {
        val dir = tmp.newFolder("empty_1")
        assertNull(CaptureUploadAssembler.fromDir(dir, "p", "brick", null))
    }
}
```

- [ ] **Step 2: [JVM] 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.CaptureUploadAssemblerTest"`
Expected: FAIL —— `Unresolved reference: CaptureUploadAssembler`。

- [ ] **Step 3: 实现**

`app/src/main/java/com/johnymoo/arverify/net/CaptureUploadAssembler.kt`:

```kotlin
package com.johnymoo.arverify.net

import java.io.File

/**
 * Builds a CapturePackage from a persisted session directory (spec §5 contract files).
 * Used by both immediate upload and library re-upload. Returns null if the required
 * recognition artifacts are missing. Pure file IO — JVM-testable with a temp dir.
 */
object CaptureUploadAssembler {
    fun fromDir(dir: File, partId: String, kind: String, systemHint: String?): CapturePackage? {
        val metaFile = File(dir, "ar_metadata.json")
        val rgbFile = File(dir, "recognition_rgb.jpg")
        val depthFile = File(dir, "recognition_depth.png")
        if (!metaFile.isFile || !rgbFile.isFile || !depthFile.isFile) return null

        val images = (dir.listFiles { f -> f.isFile && f.name.matches(Regex("frame_\\d+\\.jpg")) } ?: emptyArray())
            .sortedBy { it.name }
            .map { FilePart(it.name, "image/jpeg", it.readBytes()) }

        return CapturePackage(
            partId = partId,
            kind = kind,
            systemHint = systemHint,
            arMetadataJson = metaFile.readText(),
            recognitionRgb = FilePart("recognition_rgb.jpg", "image/jpeg", rgbFile.readBytes()),
            recognitionDepth = FilePart("recognition_depth.png", "image/png", depthFile.readBytes()),
            images = images,
        )
    }
}
```

- [ ] **Step 4: [JVM] 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.CaptureUploadAssemblerTest"`
Expected: PASS（2 tests）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/net/CaptureUploadAssembler.kt \
        app/src/test/java/com/johnymoo/arverify/CaptureUploadAssemblerTest.kt
git commit -m "feat: add CaptureUploadAssembler (session dir -> CapturePackage)"
```

---

## Task 5: 接通采集页 onFinish（识别上传 / 通用导出）

**Files:** Create `capture/KindDialog.kt`; Modify `capture/ScanCaptureActivity.kt`

- [ ] **Step 1: KindDialog.kt（Compose 类型选择）**

```kotlin
package com.johnymoo.arverify.capture

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun KindDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val kinds = listOf("brick", "plate", "tile", "slope")
    var chosen by remember { mutableStateOf(kinds.first()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("确认类型") },
        text = {
            Column {
                kinds.forEach { k ->
                    Row(Modifier.selectable(selected = chosen == k, onClick = { chosen = k }).padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = chosen == k, onClick = { chosen = k })
                        Text(k, Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(chosen) }) { Text("上传") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
```

- [ ] **Step 2: ScanCaptureActivity——接 onFinish**

In `ScanCaptureActivity`, add fields and replace the `onFinish` lambda. Add at class top:

```kotlin
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()
```

Add Compose state + dialog in `setContent` (wrap existing CaptureScreen in a Box; show KindDialog when pending). Replace the `setContent { ScanForgeTheme { CaptureScreen(...) } }` body with:

```kotlin
        setContent {
            ScanForgeTheme {
                var showKind by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                CaptureScreen(
                    holder = holder,
                    glViewFactory = { ctx -> makeGlView(ctx) },
                    onShutter = { renderer.captureRequested = true },
                    onFinish = {
                        if (mode == CaptureMode.RECOGNITION) showKind = true
                        else exportGeneral()
                    },
                    onBack = { finish() },
                    finishLabel = if (mode == CaptureMode.RECOGNITION) "上传" else "导出",
                )
                if (showKind) KindDialog(
                    onConfirm = { kind -> showKind = false; uploadRecognition(kind) },
                    onDismiss = { showKind = false },
                )
            }
        }
```

Extract the GLSurfaceView factory into `makeGlView(ctx)` (same body as Plan 3's `glViewFactory`). Add these methods:

```kotlin
    private fun makeGlView(ctx: android.content.Context): android.opengl.GLSurfaceView =
        android.opengl.GLSurfaceView(ctx).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(renderer)
            renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY
            glView = this
        }

    private fun uploadRecognition(kind: String) {
        val dir = java.io.File(holder.state.value.sessionDir)
        val partId = dir.name.substringBeforeLast('_')
        val pkg = com.johnymoo.arverify.net.CaptureUploadAssembler.fromDir(dir, partId, kind, null)
            ?: run { toast("缺少识别帧，请先拍俯视凸点面"); return }
        val config = com.johnymoo.arverify.config.AppPrefs(this).load()
        toast("正在上传与识别…")
        io.execute {
            val outcome = com.johnymoo.arverify.net.CaptureUploader(
                com.johnymoo.arverify.net.HttpUrlConnectionTransport()
            ).upload(config.baseUrl, pkg)
            runOnUiThread { onUploadResult(outcome, config.baseUrl) }
        }
    }

    private fun onUploadResult(outcome: com.johnymoo.arverify.net.UploadOutcome, baseUrl: String) {
        com.johnymoo.arverify.net.UploadResultHolder.outcome = outcome
        com.johnymoo.arverify.net.UploadResultHolder.baseUrl = baseUrl
        when (outcome) {
            is com.johnymoo.arverify.net.UploadOutcome.Failure ->
                toast("上传失败：${outcome.message}（已本地留存，可在采集库重试）")
            is com.johnymoo.arverify.net.UploadOutcome.Success -> when (outcome.result.status) {
                com.johnymoo.arverify.net.RecognitionStatus.NEEDS_MEASUREMENT ->
                    startActivity(android.content.Intent(this, com.johnymoo.arverify.measure.MeasurementFormActivity::class.java))
                else ->
                    startActivity(android.content.Intent(this, com.johnymoo.arverify.result.ResultActivity::class.java))
            }
        }
    }

    private fun exportGeneral() {
        val dir = java.io.File(holder.state.value.sessionDir)
        val files = (dir.listFiles { f -> f.isFile } ?: emptyArray()).toList()
        if (files.isEmpty()) { toast("还没有可导出的帧"); return }
        com.johnymoo.arverify.export.ReportExporter(this).share(files, "导出采集")
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
```

Add `override fun onDestroy()` cleanup: `io.shutdown()` before `session?.close()`.

> 复用既有 `CaptureUploader`/`HttpUrlConnectionTransport`/`UploadResultHolder`/`ResultActivity`/`MeasurementFormActivity`/`ReportExporter.share(files,title)`。本地留存：会话目录本身即留存（含契约工件），上传失败数据不丢（spec §6/§12）。

- [ ] **Step 3: [真机] 端到端验证（核心）**

Run (Mac，连后端局域网)：`./gradlew :app:installDebug`
1. 识别采集：拍 TOP（俯视）+ 侧 + ≥2 角 → 「上传」→ 选类型 → 进度 → `recognized` 进 `ResultActivity` 看结论 + GLB 预览；或 `needs_measurement` 进测量表单。
2. 断网中途上传 → 失败提示；会话目录工件仍在（采集库可重传）。
3. 通用采集：拍几帧 → 「导出」→ 系统分享面板含 RGB/深度/点云文件。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/KindDialog.kt \
        app/src/main/java/com/johnymoo/arverify/capture/ScanCaptureActivity.kt
git commit -m "feat: wire capture finish to upload (recognition) / share (general)"
```

---

## Task 6: 采集库重新上传 / 预览模型

**Files:** Modify `ui/session/SessionDetailScreen.kt`

- [ ] **Step 1: 接通「重新上传」（识别会话）**

In `SessionDetailScreen`, replace the recognition `Button`'s `onClick` (the `预览模型` button) to re-upload from the persisted dir:

```kotlin
                Button(onClick = {
                    val pkg = com.johnymoo.arverify.net.CaptureUploadAssembler
                        .fromDir(dir, session.partId, session.recognized?.kind ?: "brick", null)
                    if (pkg == null) { android.widget.Toast.makeText(context, "缺少识别帧", android.widget.Toast.LENGTH_SHORT).show() }
                    else {
                        val baseUrl = com.johnymoo.arverify.config.AppPrefs(context).load().baseUrl
                        android.widget.Toast.makeText(context, "正在重新上传…", android.widget.Toast.LENGTH_SHORT).show()
                        Thread {
                            val outcome = com.johnymoo.arverify.net.CaptureUploader(
                                com.johnymoo.arverify.net.HttpUrlConnectionTransport()
                            ).upload(baseUrl, pkg)
                            com.johnymoo.arverify.net.UploadResultHolder.outcome = outcome
                            com.johnymoo.arverify.net.UploadResultHolder.baseUrl = baseUrl
                            (context as? android.app.Activity)?.runOnUiThread {
                                if (outcome is com.johnymoo.arverify.net.UploadOutcome.Success) {
                                    context.startActivity(android.content.Intent(context, com.johnymoo.arverify.result.ResultActivity::class.java))
                                } else {
                                    android.widget.Toast.makeText(context, "上传失败，可重试", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }.start()
                    }
                }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_reupload))
                }
```

> 已识别会话「预览模型」直接走 `ResultActivity`（其 `UploadResultHolder` 已含上次结果）属增强；为简单起见此处统一为「重新上传」→ 拿回结果再预览。文案用 `action_reupload`。

- [ ] **Step 2: [真机] 验证**

采集库 → 进一个识别会话 → 「重新上传」→ 进度 → `ResultActivity` 结论 + GLB。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/session/SessionDetailScreen.kt
git commit -m "feat: library session re-upload via CaptureUploadAssembler"
```

---

## 收尾：全量回归 + e2e

- [ ] **Step 1: [JVM] 全量单测**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS（Plan 1–4 全部纯逻辑：Frame/Session/Manifest/Library/DepthRange/LibraryFiltering/CameraPitch/DepthHeatmap/SessionStatusMapper/CaptureUploadAssembler + 既有测试）。

- [ ] **Step 2: [真机] e2e 验收（对照 spec §9 成功标准）**

Run (Mac)：`./gradlew assembleDebug :app:installDebug`
- 取景实时深度热力可开关；向导/自由均可采集；不达标可强拍。
- 拍完 App 内逐帧回看 RGB/深度；采集库历史会话、重传/导出/删除。
- 识别上传 → 正确 system/kind/units → GLB 预览；非标 → 引导测量提交。
- 通用采集导出 RGB/深度 PNG/PLY 并分享。
- 断网中途上传 → 失败提示 + 会话留存完好。
- 全 App 深色专业扫描风；显示名「拾模 / ScanForge」。

---

## 自检

- **spec 覆盖**：§5 上传契约（`CaptureUploadAssembler` 组包，字段不变；Task 3 落 `ar_metadata.json`/`recognition_*`）、§4 结果（复用 `ResultActivity` GLB）/测量兜底（复用 `MeasurementFormActivity`）、通用导出（`ReportExporter.share` + `PlyWriter` 点云）、§12 错误处理（上传失败→会话留存→库内重传）、§4 库内重传（Task 6）。结果/测量 Compose 移植为可选后续（已说明）。
- **占位符**：无遗留 TODO；Plan 3 中标注的 `onFinish` 占位在本计划被真实实现替换。
- **类型一致性**：`CaptureUploadAssembler.fromDir(dir,partId,kind,systemHint): CapturePackage?` 在 Task 4 定义，Task 5/6 一致调用；复用既有 `CapturePackage`/`FilePart`/`CaptureUploader`/`UploadOutcome`/`RecognitionStatus`/`UploadResultHolder`/`ArMetadata*`/`Depth16PngWriter`/`PlyWriter`/`ReportExporter`/`AppPrefs`；`SessionStatusMapper.fromRecognition` 覆盖 `RecognitionStatus` 三枚举；契约文件名（`ar_metadata.json`/`recognition_rgb.jpg`/`recognition_depth.png`/`frame_*.jpg`）在写（Task 2/3）与读（Task 4）两侧一致。
