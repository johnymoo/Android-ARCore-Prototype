# 拾模 / ScanForge · Plan 3 —— 采集页：AR 会话 + 实时深度热力叠加 + Compose 浮层

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 新建独立的 `ScanCaptureActivity`：复用 ARCore 会话 + 相机渲染，**实时把 DEPTH16 上色叠加到取景上（可一键开关）**，提供向导/自由切换、实时质量条、即时回看缩略图与快门；每帧持久化为会话（manifest + 文件）。

**Architecture:** `ScanCaptureActivity`（ComponentActivity）管理 AR 会话生命周期；`setContent` 中 `CaptureScreen` 用 `AndroidView` 承载 `GLSurfaceView`（相机背景，复用 `BackgroundRenderer`），其上叠 Compose 浮层。深度热力**不走自定义 GLSL**：渲染线程每帧把深度网格用 `DepthColorMap` + Plan 1 的 `DepthOverlayRange` 上色成 `Bitmap`，发布到状态，Compose 用半透明 `Image` 叠加（开关=是否绘制）——简单、复用已测配色、规避 GLSL 风险。捕获写盘用 Plan 1 的 `CaptureLibraryRepository`。

**Tech Stack:** ARCore 1.54 · GLSurfaceView + 既有 `BackgroundRenderer`/`ArFrameExtractor` · Compose 浮层 · Plan 1 `DepthOverlayRange`/`CaptureLibraryRepository`/`SessionManifestCodec` · 既有 `FrameQualityGate`/`Sharpness`/`ScaleMath`/`FocusDistanceModel`/`Depth16PngWriter`。设计：spec §3（采集页）、§10（深度叠加）。原型：`assets/scanforge-ui-mockups/capture-screen.html`。

**前置：** Plan 1、Plan 2 已合并（Compose 主题/导航、`session.*`、`DepthOverlayRange` 可用）。

## 关键约束

- **[真机]** 步骤在 Mac + OPPO PLG110 验证（AR/GL/相机本机不可测）。**[JVM]** 仅 Task 1、2 的纯逻辑。
- 深度叠加用「CPU 上色成 Bitmap → Compose 半透明 Image」方案；**对齐/旋转**为真机调参项（portrait-locked），不追求像素级，先保证「近暖远冷、覆盖可见、可开关」。

## 文件结构

**新建（main）：**
- `capture/CameraPitch.kt`（**纯，JVM 可测**：四元数→俯仰角°，喂 `FrameRoleClassifier`）
- `depth/DepthHeatmap.kt`（`argb(...)` **纯，JVM 可测**；`toBitmap(...)` 设备封装）
- `capture/CaptureSessionWriter.kt`（写帧文件 + 原子写 manifest）
- `capture/CaptureRenderer.kt`（GLSurfaceView.Renderer：相机 + 深度发布 + 质量 + 捕获）
- `capture/CaptureScreenState.kt`（`CaptureUiState` + holder）
- `capture/CaptureScreen.kt`（Compose 浮层）
- `capture/ScanCaptureActivity.kt`（宿主，管理 AR 生命周期）

**新建测试：** `CameraPitchTest.kt`、`DepthHeatmapTest.kt`

**修改：** `AndroidManifest.xml`（注册 `ScanCaptureActivity`）；`ui/home/HomeScreen.kt`（双轨入口改指向 `ScanCaptureActivity` + mode extra）；`res/values/strings.xml`（采集页文案，复用既有 wizard_* 即可）。

---

## Task 1: CameraPitch（四元数 → 俯仰角°，纯逻辑）

**Files:** Create `capture/CameraPitch.kt`, Test `CameraPitchTest.kt`

- [ ] **Step 1: [JVM] 写失败测试**

`app/src/test/java/com/johnymoo/arverify/CameraPitchTest.kt`:

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CameraPitch
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraPitchTest {
    @Test fun identityIsLevel() {
        assertEquals(0.0, CameraPitch.fromQuaternion(0.0, 0.0, 0.0, 1.0), 0.5)
    }

    @Test fun rotatedDownIsNegative90() {
        // -90° about X: q = (sin(-45°),0,0,cos(-45°))
        assertEquals(-90.0, CameraPitch.fromQuaternion(-0.7071, 0.0, 0.0, 0.7071), 0.5)
    }

    @Test fun rotatedUpIsPositive90() {
        assertEquals(90.0, CameraPitch.fromQuaternion(0.7071, 0.0, 0.0, 0.7071), 0.5)
    }
}
```

- [ ] **Step 2: [JVM] 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.CameraPitchTest"`
Expected: FAIL —— `Unresolved reference: CameraPitch`。

- [ ] **Step 3: 实现**

`app/src/main/java/com/johnymoo/arverify/capture/CameraPitch.kt`:

```kotlin
package com.johnymoo.arverify.capture

import kotlin.math.asin
import kotlin.math.atan2 // reserved for future yaw; kept import-free below

/**
 * Pitch (degrees) of the camera's forward axis relative to horizontal, from the
 * ARCore camera-pose quaternion q=[x,y,z,w]. -90 = pointing straight down (table),
 * 0 = level, +90 = straight up. Feeds FrameRoleClassifier in free mode.
 *
 * Forward = R(q) * (0,0,-1); its world-Y component is 2*(w*x - y*z) (Y-up).
 */
object CameraPitch {
    fun fromQuaternion(x: Double, y: Double, z: Double, w: Double): Double {
        val forwardY = (2.0 * (w * x - y * z)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(asin(forwardY))
    }
}
```

> 删去未用 import：实现里不需要 `atan2`，请勿保留该 import（仅注释提示未来 yaw）。最终文件只 import `kotlin.math.asin`。

- [ ] **Step 4: [JVM] 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.CameraPitchTest"`
Expected: PASS（3 tests）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/CameraPitch.kt \
        app/src/test/java/com/johnymoo/arverify/CameraPitchTest.kt
git commit -m "feat: add CameraPitch (quaternion->pitch, JVM-tested)"
```

---

## Task 2: DepthHeatmap（深度网格 → ARGB，纯逻辑 + Bitmap 封装）

**Files:** Create `depth/DepthHeatmap.kt`, Test `DepthHeatmapTest.kt`

- [ ] **Step 1: [JVM] 写失败测试（测纯 argb 部分）**

`app/src/test/java/com/johnymoo/arverify/DepthHeatmapTest.kt`:

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.depth.DepthHeatmap
import com.johnymoo.arverify.depth.DepthRangeMm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthHeatmapTest {
    private val range = DepthRangeMm(150, 700)

    @Test fun invalidDepthIsTransparent() {
        val argb = DepthHeatmap.argb(intArrayOf(0), 1, 1, range)
        assertEquals(0, argb[0]) // mm<=0 -> transparent
    }

    @Test fun nearIsRedishFarIsBluish() {
        val near = DepthHeatmap.argb(intArrayOf(160), 1, 1, range)[0]
        val far = DepthHeatmap.argb(intArrayOf(690), 1, 1, range)[0]
        val nearR = (near shr 16) and 0xFF
        val nearB = near and 0xFF
        val farR = (far shr 16) and 0xFF
        val farB = far and 0xFF
        assertTrue("near should be warmer", nearR > nearB)
        assertTrue("far should be cooler", farB > farR)
    }

    @Test fun sizeMatchesGrid() {
        val argb = DepthHeatmap.argb(IntArray(6) { 300 }, 3, 2, range)
        assertEquals(6, argb.size)
    }
}
```

- [ ] **Step 2: [JVM] 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.DepthHeatmapTest"`
Expected: FAIL —— `Unresolved reference: DepthHeatmap`。

- [ ] **Step 3: 实现（argb 纯函数 + toBitmap 设备封装）**

`app/src/main/java/com/johnymoo/arverify/depth/DepthHeatmap.kt`:

```kotlin
package com.johnymoo.arverify.depth

import android.graphics.Bitmap

/**
 * Colorizes a depth (mm) grid into ARGB using DepthColorMap over a FIXED range
 * (spec §10, stable/flicker-free). [argb] is pure (JVM-testable); [toBitmap] wraps it.
 */
object DepthHeatmap {
    fun argb(gridMm: IntArray, width: Int, height: Int, range: DepthRangeMm): IntArray {
        val out = IntArray(width * height)
        for (i in out.indices) out[i] = DepthColorMap.toArgb(gridMm[i], range.minMm, range.maxMm)
        return out
    }

    fun toBitmap(gridMm: IntArray, width: Int, height: Int, range: DepthRangeMm): Bitmap {
        val pixels = argb(gridMm, width, height, range)
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }
}
```

- [ ] **Step 4: [JVM] 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.DepthHeatmapTest"`
Expected: PASS（3 tests）。

> 注：`toBitmap` 用到 `android.graphics.Bitmap`；因 `testOptions.unitTests.isReturnDefaultValues = true`，单测**只覆盖 `argb`**，`toBitmap` 真机覆盖。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/depth/DepthHeatmap.kt \
        app/src/test/java/com/johnymoo/arverify/DepthHeatmapTest.kt
git commit -m "feat: add DepthHeatmap (fixed-range depth colorize)"
```

---

## Task 3: CaptureSessionWriter（写帧文件 + 原子写 manifest）

**Files:** Create `capture/CaptureSessionWriter.kt`

- [ ] **Step 1: 实现**

```kotlin
package com.johnymoo.arverify.capture

import android.graphics.Bitmap
import com.johnymoo.arverify.depth.DepthHeatmap
import com.johnymoo.arverify.depth.DepthRangeMm
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.session.CaptureMode
import com.johnymoo.arverify.session.CaptureSession
import com.johnymoo.arverify.session.CapturedFrame
import com.johnymoo.arverify.session.SessionStatus
import com.johnymoo.arverify.imaging.Depth16PngWriter
import java.io.File

/**
 * Owns one on-disk capture session directory and appends frames to it.
 * Writes RGB jpg, lossless 16-bit depth png (contract), and a colorized depth png
 * (for in-app review), then rewrites the manifest atomically after each frame.
 */
class CaptureSessionWriter(
    private val rootDir: File,
    private val partId: String,
    private val mode: CaptureMode,
    private val createdAtEpochMs: Long,
    private val deviceModel: String,
    private val depthRange: DepthRangeMm,
    private val repo: CaptureLibraryRepository = CaptureLibraryRepository(rootDir),
) {
    val dir: File = File(rootDir, "${partId}_$createdAtEpochMs").apply { mkdirs() }
    private val frames = mutableListOf<CapturedFrame>()

    /** Persists one frame; returns the running frame list. Pass depth=null for RGB-only frames. */
    fun addFrame(
        slot: CaptureSlot,
        rgbJpeg: ByteArray,
        depthGridMm: IntArray?, depthW: Int = 0, depthH: Int = 0,
        distanceM: Double? = null, sharpness: Double? = null,
    ): List<CapturedFrame> {
        val i = frames.size
        val rgbName = "frame_${i}.jpg"
        File(dir, rgbName).writeBytes(rgbJpeg)

        var depthName: String? = null
        if (depthGridMm != null && depthW > 0 && depthH > 0) {
            depthName = "depth_${i}.png"
            File(dir, depthName).writeBytes(Depth16PngWriter.encode(depthGridMm, depthW, depthH))
            // colorized review copy
            val colored = DepthHeatmap.toBitmap(depthGridMm, depthW, depthH, depthRange)
            File(dir, "depthc_${i}.png").outputStream().use { colored.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        frames.add(CapturedFrame(slot, rgbName, depthName, distanceM, sharpness))
        rewriteManifest(if (mode == CaptureMode.RECOGNITION) SessionStatus.PENDING_UPLOAD else SessionStatus.EXPORTED)
        return frames.toList()
    }

    fun snapshot(status: SessionStatus): CaptureSession =
        CaptureSession(partId, mode, createdAtEpochMs, deviceModel, status, frames.toList())

    private fun rewriteManifest(status: SessionStatus) = repo.writeManifest(dir, snapshot(status))
}
```

> 复用既有 `imaging.Depth16PngWriter.encode(values,w,h): ByteArray`（无损 16-bit 深度 PNG，契约用）。会话目录命名 `<partId>_<epochMs>`，与 Plan 1 仓库扫描兼容。

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/CaptureSessionWriter.kt
git commit -m "feat: add CaptureSessionWriter (frame files + atomic manifest)"
```

---

## Task 4: CaptureRenderer（相机渲染 + 深度发布 + 质量 + 捕获）

**Files:** Create `capture/CaptureRenderer.kt`, `capture/CaptureScreenState.kt`

- [ ] **Step 1: CaptureScreenState.kt（UI 状态 + holder）**

```kotlin
package com.johnymoo.arverify.capture

import android.graphics.Bitmap
import com.johnymoo.arverify.session.CapturedFrame
import kotlinx.coroutines.flow.MutableStateFlow

enum class CaptureModeUi { GUIDED, FREE }

data class CaptureUiState(
    val modeUi: CaptureModeUi = CaptureModeUi.GUIDED,
    val depthOn: Boolean = true,
    val qualityReason: QualityReason = QualityReason.NOT_TRACKING,
    val distanceM: Double = 0.0,
    val sharpness: Double = 0.0,
    val frames: List<CapturedFrame> = emptyList(),
    val wizardStep: WizardStep = WizardStep.NEED_TOP,
    val canFinish: Boolean = false,
)

/** Thread-safe holder shared between the GL render thread and Compose. */
class CaptureHolder {
    val state = MutableStateFlow(CaptureUiState())
    val depthBitmap = MutableStateFlow<Bitmap?>(null)

    fun toggleDepth() { state.value = state.value.copy(depthOn = !state.value.depthOn) }
    fun setMode(m: CaptureModeUi) { state.value = state.value.copy(modeUi = m) }
}
```

- [ ] **Step 2: CaptureRenderer.kt**

```kotlin
package com.johnymoo.arverify.capture

import android.view.WindowManager
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.johnymoo.arverify.config.CaptureConfig
import com.johnymoo.arverify.depth.DepthHeatmap
import com.johnymoo.arverify.depth.DepthOverlayRange
import com.johnymoo.arverify.render.BackgroundRenderer
import com.johnymoo.arverify.session.CaptureMode
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * GLSurfaceView.Renderer: draws the ARCore camera, publishes a colorized depth bitmap
 * + live quality each frame, and writes a frame on capture. AR/GL — device-verified.
 */
class CaptureRenderer(
    private val sessionProvider: () -> Session?,
    private val config: CaptureConfig,
    private val mode: CaptureMode,
    private val holder: CaptureHolder,
    private val writer: CaptureSessionWriter,
    private val windowManager: WindowManager,
) : GLSurfaceView.Renderer {

    private val background = BackgroundRenderer()
    private val controller = CaptureWizardController()
    private val gate = FrameQualityGate(config.thresholds())
    private val depthRange = DepthOverlayRange.fromDistanceBand(config.minDistanceM, config.maxDistanceM)

    @Volatile var captureRequested = false
    private var vpW = 0; private var vpH = 0; private var rotation = 0
    private var frameTick = 0

    override fun onSurfaceCreated(gl: GL10?, c: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f); background.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h); vpW = w; vpH = h
        @Suppress("DEPRECATION") run { rotation = windowManager.defaultDisplay.rotation }
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = sessionProvider() ?: return
        if (background.textureId == -1) return
        s.setCameraTextureName(background.textureId)
        if (vpW > 0) s.setDisplayGeometry(rotation, vpW, vpH)
        try {
            val frame = s.update()
            background.draw(frame)
            evaluate(frame)
            if (captureRequested && frame.camera.trackingState == TrackingState.TRACKING) {
                captureRequested = false
                capture(frame)
            }
        } catch (e: CameraNotAvailableException) { /* transient */ }
    }

    private fun evaluate(frame: Frame) {
        var distance = 0.0; var sharp = 0.0
        try {
            frame.acquireDepthImage16Bits().use { d ->
                val g = ArFrameExtractor.depthGridMm(d)
                distance = ScaleMath.medianCenterDistanceMeters(g.values, g.width, g.height, 0.2)
                if ((frameTick++ % 2) == 0) { // throttle overlay to every other frame
                    holder.depthBitmap.value = DepthHeatmap.toBitmap(g.values, g.width, g.height, depthRange)
                }
            }
        } catch (e: NotYetAvailableException) { /* warming up */ }
        try {
            frame.acquireCameraImage().use { img ->
                val luma = ArFrameExtractor.lumaGrid(img)
                sharp = Sharpness.varianceOfLaplacian(luma.values, luma.width, luma.height)
            }
        } catch (e: NotYetAvailableException) { /* not ready */ }

        val tracking = when (frame.camera.trackingState) {
            TrackingState.TRACKING -> TrackingStateLite.TRACKING
            TrackingState.PAUSED -> TrackingStateLite.PAUSED
            TrackingState.STOPPED -> TrackingStateLite.STOPPED
        }
        val reason = gate.evaluate(tracking, distance, sharp, null)
        val st = controller.state
        holder.state.value = holder.state.value.copy(
            qualityReason = reason, distanceM = distance, sharpness = sharp,
            wizardStep = st.step, canFinish = st.canUpload,
        )
    }

    private fun capture(frame: Frame) {
        try {
            val rgb = frame.acquireCameraImage().use { ArFrameExtractor.rgbJpeg(it) }
            val slot = chooseSlot(frame)
            var gridVals: IntArray? = null; var gw = 0; var gh = 0; var dist: Double? = null
            try {
                frame.acquireDepthImage16Bits().use { d ->
                    val g = ArFrameExtractor.depthGridMm(d)
                    gridVals = g.values; gw = g.width; gh = g.height
                    dist = ScaleMath.medianCenterDistanceMeters(g.values, g.width, g.height, 0.2)
                }
            } catch (e: NotYetAvailableException) { /* RGB-only frame */ }

            val frames = writer.addFrame(slot, rgb, gridVals, gw, gh, dist, holder.state.value.sharpness)
            controller.record(slot)
            holder.state.value = holder.state.value.copy(
                frames = frames, wizardStep = controller.state.step, canFinish = controller.state.canUpload,
            )
        } catch (e: Exception) { /* surfaced via toast in Activity if needed */ }
    }

    /** Guided mode: slot follows the wizard step. Free mode: infer from camera pitch. */
    private fun chooseSlot(frame: Frame): CaptureSlot {
        if (holder.state.value.modeUi == CaptureModeUi.GUIDED) {
            return when (controller.state.step) {
                WizardStep.NEED_TOP -> CaptureSlot.TOP
                WizardStep.NEED_SIDE -> CaptureSlot.SIDE
                else -> CaptureSlot.ANGLE
            }
        }
        val q = frame.camera.pose.let { FloatArray(4).also { arr -> it.getRotationQuaternion(arr, 0) } }
        val pitch = CameraPitch.fromQuaternion(q[0].toDouble(), q[1].toDouble(), q[2].toDouble(), q[3].toDouble())
        return FrameRoleClassifier.classify(pitch)
    }
}
```

> 复用既有：`BackgroundRenderer`、`ArFrameExtractor.{depthGridMm,lumaGrid,rgbJpeg}`、`ScaleMath.medianCenterDistanceMeters`、`Sharpness.varianceOfLaplacian`、`FrameQualityGate`/`QualityReason`/`TrackingStateLite`、`CaptureWizardController`/`WizardStep`/`CaptureSlot`。焦点信号此处传 null（焦点诊断属可选增强，spec 未强制；如需可后续把 `FocusDistanceModel` 接回）。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/CaptureScreenState.kt \
        app/src/main/java/com/johnymoo/arverify/capture/CaptureRenderer.kt
git commit -m "feat: add CaptureRenderer + capture UI state holder"
```

---

## Task 5: CaptureScreen（Compose 浮层）

**Files:** Create `capture/CaptureScreen.kt`

- [ ] **Step 1: 实现**

```kotlin
package com.johnymoo.arverify.capture

import android.opengl.GLSurfaceView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.johnymoo.arverify.session.CapturedFrame

/**
 * Full-screen capture chrome over the camera GLSurfaceView. [glViewFactory] supplies
 * the configured GLSurfaceView (created/owned by ScanCaptureActivity).
 */
@Composable
fun CaptureScreen(
    holder: CaptureHolder,
    glViewFactory: (android.content.Context) -> GLSurfaceView,
    onShutter: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
    finishLabel: String,
) {
    val state by holder.state.collectAsStateWithLifecycle()
    val depthBmp by holder.depthBitmap.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = glViewFactory, modifier = Modifier.fillMaxSize())

        if (state.depthOn) {
            depthBmp?.let {
                Image(it.asImageBitmap(), contentDescription = "depth", contentScale = ContentScale.FillBounds,
                    alpha = 0.5f, modifier = Modifier.fillMaxSize())
            }
        }

        // Top chrome
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.padding(end = 8.dp).clickableNoRipple(onBack))
            Text(stepText(state), color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.weight(1f))
            FilterChip(selected = state.modeUi == CaptureModeUi.GUIDED, onClick = { holder.setMode(CaptureModeUi.GUIDED) }, label = { Text("向导") })
            FilterChip(selected = state.modeUi == CaptureModeUi.FREE, onClick = { holder.setMode(CaptureModeUi.FREE) }, label = { Text("自由") }, modifier = Modifier.padding(start = 6.dp))
        }

        // Depth toggle chip (top-right, below chrome)
        Surface(color = androidx.compose.ui.graphics.Color(0x88000000), shape = MaterialTheme.shapes.large,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 56.dp, end = 12.dp).clickableNoRipple { holder.toggleDepth() }) {
            Text(if (state.depthOn) "深度 ● 开" else "深度 ○ 关", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
        }

        // Quality + prompt (center-bottom)
        Text(qualityText(state), color = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.align(Alignment.Center).padding(top = 120.dp))

        // Bottom chrome: thumbnails + shutter + finish
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                items(state.frames) { f -> ThumbnailDot(f) }
            }
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp).clickableNoRipple(onShutter)) {}
            Button(onClick = onFinish, enabled = state.canFinish) { Text("$finishLabel ${state.frames.size}") }
        }
    }
}

@Composable private fun ThumbnailDot(f: CapturedFrame) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small,
        modifier = Modifier.size(width = 34.dp, height = 28.dp)) {
        Text(f.slot.name.first().toString(), style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(4.dp))
    }
}

private fun stepText(s: CaptureUiState): String =
    if (s.modeUi == CaptureModeUi.GUIDED) when (s.wizardStep) {
        WizardStep.NEED_TOP -> "向导 · 俯视凸点面"
        WizardStep.NEED_SIDE -> "向导 · 侧面"
        WizardStep.NEED_ANGLES -> "向导 · 补角度"
        WizardStep.READY -> "向导 · 已就绪"
    } else "自由 · 已采 ${s.frames.size}"

private fun qualityText(s: CaptureUiState): String {
    val d = if (s.distanceM > 0) "%.2fm".format(s.distanceM) else "--"
    val base = "深度 $d · 清晰度 %.0f".format(s.sharpness)
    return if (s.qualityReason == QualityReason.OK) base else "${s.qualityReason.messageZh}\n$base"
}

/** Click without the default ripple (overlay chrome). */
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable(
        interactionSource = androidx.compose.foundation.interaction.MutableInteractionSource(),
        indication = null, onClick = onClick))
```

> `QualityReason.messageZh`、`QualityReason.OK` 为既有 API（`CaptureWizardActivity.qualityText` 使用过）。缩略图先用 slot 首字母占位；显示真实缩略图可在真机阶段把 `CaptureSessionWriter` 写出的 `frame_i.jpg` 路径回传到 `CaptureUiState` 后用 Coil 加载（标注为真机增强）。

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/CaptureScreen.kt
git commit -m "feat: add CaptureScreen Compose overlay (depth toggle, mode, shutter)"
```

---

## Task 6: ScanCaptureActivity + 接线（Manifest、Home 入口）

**Files:** Create `capture/ScanCaptureActivity.kt`; Modify `AndroidManifest.xml`, `ui/home/HomeScreen.kt`

- [ ] **Step 1: ScanCaptureActivity.kt**

```kotlin
package com.johnymoo.arverify.capture

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.johnymoo.arverify.config.AppPrefs
import com.johnymoo.arverify.depth.DepthOverlayRange
import com.johnymoo.arverify.session.CaptureMode
import com.johnymoo.arverify.ui.common.SessionPaths
import com.johnymoo.arverify.ui.theme.ScanForgeTheme
import java.util.UUID

class ScanCaptureActivity : ComponentActivity() {
    companion object { const val EXTRA_MODE = "mode" } // "RECOGNITION" | "GENERAL"

    private var session: Session? = null
    private var installRequested = false
    private var glView: GLSurfaceView? = null
    private val holder = CaptureHolder()
    private lateinit var renderer: CaptureRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val mode = if (intent.getStringExtra(EXTRA_MODE) == "GENERAL") CaptureMode.GENERAL else CaptureMode.RECOGNITION
        val config = AppPrefs(this).load()
        val writer = CaptureSessionWriter(
            rootDir = SessionPaths.captureRoot(this),
            partId = "part-" + UUID.randomUUID().toString().take(8),
            mode = mode,
            createdAtEpochMs = System.currentTimeMillis(),
            deviceModel = android.os.Build.MODEL ?: "",
            depthRange = DepthOverlayRange.fromDistanceBand(config.minDistanceM, config.maxDistanceM),
        )
        renderer = CaptureRenderer({ session }, config, mode, holder, writer, windowManager)

        setContent {
            ScanForgeTheme {
                CaptureScreen(
                    holder = holder,
                    glViewFactory = { ctx ->
                        GLSurfaceView(ctx).apply {
                            preserveEGLContextOnPause = true
                            setEGLContextClientVersion(2)
                            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                            setRenderer(renderer)
                            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                            glView = this
                        }
                    },
                    onShutter = { renderer.captureRequested = true },
                    onFinish = { /* Plan 4: recognition upload / general export */
                        Toast.makeText(this, "接通在 Plan 4：${mode}", Toast.LENGTH_SHORT).show()
                    },
                    onBack = { finish() },
                    finishLabel = if (mode == CaptureMode.RECOGNITION) "上传" else "导出",
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> { installRequested = true; return }
                    else -> {}
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "需要相机权限", Toast.LENGTH_LONG).show(); finish(); return
                }
                session = Session(this).also { s ->
                    val c = s.config
                    c.depthMode = if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) Config.DepthMode.AUTOMATIC else Config.DepthMode.DISABLED
                    c.focusMode = Config.FocusMode.AUTO
                    s.configure(c)
                }
            } catch (e: Exception) { Toast.makeText(this, "无法启动 AR：${e.javaClass.simpleName}", Toast.LENGTH_LONG).show(); finish(); return }
        }
        try { session?.resume() } catch (e: CameraNotAvailableException) { Toast.makeText(this, "相机不可用", Toast.LENGTH_LONG).show(); session = null; finish(); return }
        glView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (session != null) { glView?.onPause(); session?.pause() }
    }

    override fun onDestroy() { session?.close(); session = null; super.onDestroy() }
}
```

- [ ] **Step 2: Manifest 注册（在 `MeasurementFormActivity` 之后插入）**

```xml
        <activity
            android:name=".capture.ScanCaptureActivity"
            android:exported="false"
            android:screenOrientation="locked"
            android:configChanges="orientation|screenSize|screenLayout|keyboard|keyboardHidden|navigation" />
```

- [ ] **Step 3: Home 双轨入口改指向 ScanCaptureActivity**

In `ui/home/HomeScreen.kt`, replace the two entry-card `onClick` bodies:

```kotlin
            // 识别采集
            {
                context.startActivity(
                    Intent(context, com.johnymoo.arverify.capture.ScanCaptureActivity::class.java)
                        .putExtra(com.johnymoo.arverify.capture.ScanCaptureActivity.EXTRA_MODE, "RECOGNITION")
                )
            }
```

```kotlin
            // 通用采集
            {
                context.startActivity(
                    Intent(context, com.johnymoo.arverify.capture.ScanCaptureActivity::class.java)
                        .putExtra(com.johnymoo.arverify.capture.ScanCaptureActivity.EXTRA_MODE, "GENERAL")
                )
            }
```

并删除不再需要的 `CaptureWizardActivity` import 与 `general_coming_soon` Toast（通用采集现已可进入采集页）。

- [ ] **Step 4: [真机] 验证（核心交付）**

Run (Mac): `./gradlew :app:installDebug`，在 PLG110 上：
1. 主屏「识别采集」→ 进入采集页，相机取景正常。
2. **右上「深度」开/关**：开时画面叠加近暖远冷半透明热力，关时纯 RGB。
3. 顶部「向导/自由」切换；底部质量文字随距离/清晰度更新；不达标也可按快门。
4. 按快门：缩略图条增加一项；多拍几张，凑够（向导：顶+侧+≥2 角）后「上传 N」可点（暂为 Plan 4 占位 Toast）。
5. 「通用采集」入口进入同款页，主行动为「导出」。
6. 进「采集库」能看到刚才的会话（Plan 2 列表），点进去帧网格可见 RGB；帧查看器可看深度灰度 PNG。
7. logcat 无崩溃。
> 调参项（记录到 PR）：深度叠加的旋转/缩放对齐、节流帧率、热力 alpha。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/ScanCaptureActivity.kt \
        app/src/main/AndroidManifest.xml app/src/main/java/com/johnymoo/arverify/ui/home/HomeScreen.kt
git commit -m "feat: add ScanCaptureActivity + wire Home dual-track entries"
```

---

## 收尾

- [ ] **Step 1: [JVM] 回归**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS（Plan 1/2 + 新增 `CameraPitchTest`、`DepthHeatmapTest`）。

- [ ] **Step 2: [真机] 全量构建安装**

Run: `./gradlew assembleDebug :app:installDebug` → 完成 Task 6 走查清单。

---

## 自检

- **spec 覆盖**：§3 采集页（深度热力可开关=Compose 半透明 Image + 节流上色；向导/自由=`FilterChip`+`FrameRoleClassifier`；质量条=`FrameQualityGate`；快门/缩略图/上传按钮）、§10 实时深度叠加（`DepthHeatmap` + `DepthOverlayRange` 固定量程）。上传/导出真实行为=Plan 4（`onFinish` 占位，已注释）。
- **占位符**：`onFinish` 与缩略图真实图为 Plan 4 / 真机增强的明确桥接点（注释标注），非遗漏。
- **类型一致性**：`CaptureHolder`/`CaptureUiState`/`CaptureModeUi` 在 Task 1/4 定义，Task 5/6 一致引用；`CaptureSessionWriter.addFrame(...)` 签名与 `CaptureRenderer.capture` 调用一致；复用既有 `BackgroundRenderer`/`ArFrameExtractor`/`ScaleMath`/`Sharpness`/`FrameQualityGate`/`QualityReason`/`TrackingStateLite`/`CaptureWizardController`/`WizardStep`/`CaptureSlot`、Plan 1 `DepthOverlayRange`/`DepthRangeMm`/`CaptureLibraryRepository`、Plan 2 `SessionPaths`/`ScanForgeTheme`。`CameraPitch.fromQuaternion` 出参喂 `FrameRoleClassifier.classify`（均为度）。
