# 拾模 / ScanForge · Plan 1 —— 领域层（纯逻辑，JVM 单测）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 ScanForge 重做所需的纯逻辑领域层（自由模式帧角色分类、采集会话模型 + manifest 编解码、文件系统采集库仓库、实时深度叠加的固定量程换算），全部以 JVM 单元测试驱动，无需 Android 设备即可验证。

**Architecture:** 新增独立于 UI/AR 的纯 Kotlin 单元，放在 `capture` / `session` / `depth` 包；用现有 JUnit4 + Gson 测试。这些单元被后续 Plan（Compose 外壳、采集页 + 深度叠加、端到端接通）消费。本计划**不**触碰任何 Android/Compose/AR 代码，因此可独立合并、零真机依赖。

**Tech Stack:** Kotlin 2.0.21 · JUnit4 (4.13.2) · Gson 2.11.0 · Gradle（在 Mac dev 机执行）。配套设计：`docs/superpowers/specs/2026-06-07-scanforge-app-redesign-design.md`（§8 组件映射、§9 持久化、§10 深度叠加）。

---

## 计划系列（4 个顺序计划）

| # | 计划 | 验证方式 | 状态 |
|---|---|---|---|
| **1** | **领域层（本计划）**：FrameRoleClassifier、会话模型 + ManifestCodec、CaptureLibraryRepository、DepthOverlayRange | **JVM 单测（无需真机）** | 本文件 |
| 2 | Compose 外壳 + 品牌：深色 Material3 主题/令牌、Navigation、主屏/采集库/会话详情/帧查看器/设置·诊断、改名资源 | Mac 真机 + Compose 预览 | 待写 |
| 3 | 采集页：独立 Activity + AR 会话 + `DepthOverlayRenderer`（GL 实时深度叠加）+ Compose 浮层（向导/自由、质量条、回看条、快门）| Mac 真机 | 待写 |
| 4 | 接通：识别上传（复用 CaptureUploader）+ 通用导出（PLY/PNG）+ Result/Measurement 移植；端到端闭环 | Mac 真机 e2e | 待写 |

> **后续计划在前一个落地后再写**——这样 Plan 2–4 能吸收前一阶段的真机反馈，避免一次性写出大量未验证的 Compose/AR 代码。本计划完成即产出一组真实、已测试、可被后续消费的逻辑单元。

## 关键约束（务必遵守）

- 本计划所有 `run` 命令在 **Mac dev 机**执行（开发机无 Android SDK/Gradle）。全部为 **JVM 单测，不需要连接手机**。
- 不新增任何依赖；不改 `build.gradle.kts`；不碰 Compose/AR/Activity。
- 测试类放在 `app/src/test/java/com/johnymoo/arverify/`，包名 `com.johnymoo.arverify`（沿用现有约定：测试在根包，import 子包类型）。
- 复用现有类型：`com.johnymoo.arverify.capture.CaptureSlot { TOP, SIDE, ANGLE }`。**不要重新定义它。**

## 文件结构

**新建（主源码 `app/src/main/java/com/johnymoo/arverify/`）：**
- `capture/FrameRoleClassifier.kt` —— 自由模式：相机俯仰角(°) → `CaptureSlot`（纯函数）。
- `session/SessionModels.kt` —— `CaptureMode`、`SessionStatus`、`CapturedFrame`、`RecognizedSummary`、`CaptureSession`（纯数据类/枚举）。
- `session/SessionManifestCodec.kt` —— `CaptureSession` ↔ JSON（Gson tree，写入稳定、解析容错、带 `schema_version`）。
- `session/CaptureLibraryRepository.kt` —— 扫描根目录列出会话（最新在前）、原子写 manifest、删除会话目录。
- `depth/DepthOverlayRange.kt` —— 采集距离量程(m) → 固定深度量程(mm)，供实时深度上色稳定不闪烁。

**新建（测试 `app/src/test/java/com/johnymoo/arverify/`）：**
- `FrameRoleClassifierTest.kt`、`SessionModelsTest.kt`、`SessionManifestCodecTest.kt`、`CaptureLibraryRepositoryTest.kt`、`DepthOverlayRangeTest.kt`。

**不修改任何现有文件。**

---

## Task 1: FrameRoleClassifier（自由模式帧角色分类）

实现 spec §3「自由模式：后台覆盖度状态机」的纯逻辑部分：由相机俯仰角推断该帧属于 顶/侧/角。累计与"够不够"复用现有 `CaptureWizardController`（它已支持任意顺序 `record()` 与 `canUpload`），本任务只补"分类"这一新纯函数。

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/capture/FrameRoleClassifier.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/FrameRoleClassifierTest.kt`

- [ ] **Step 1: 写失败测试**

Create `app/src/test/java/com/johnymoo/arverify/FrameRoleClassifierTest.kt`:

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CaptureSlot
import com.johnymoo.arverify.capture.FrameRoleClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameRoleClassifierTest {
    @Test fun straightDownIsTop() {
        assertEquals(CaptureSlot.TOP, FrameRoleClassifier.classify(-90.0))
        assertEquals(CaptureSlot.TOP, FrameRoleClassifier.classify(-75.0))
        assertEquals(CaptureSlot.TOP, FrameRoleClassifier.classify(-60.0)) // boundary inclusive
    }

    @Test fun nearHorizontalIsSide() {
        assertEquals(CaptureSlot.SIDE, FrameRoleClassifier.classify(0.0))
        assertEquals(CaptureSlot.SIDE, FrameRoleClassifier.classify(20.0))
        assertEquals(CaptureSlot.SIDE, FrameRoleClassifier.classify(-30.0)) // boundary inclusive
    }

    @Test fun inBetweenIsAngle() {
        assertEquals(CaptureSlot.ANGLE, FrameRoleClassifier.classify(-45.0))
        assertEquals(CaptureSlot.ANGLE, FrameRoleClassifier.classify(-59.9))
        assertEquals(CaptureSlot.ANGLE, FrameRoleClassifier.classify(45.0))
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.FrameRoleClassifierTest"`
Expected: FAIL —— 编译错误 `Unresolved reference: FrameRoleClassifier`。

- [ ] **Step 3: 写最小实现**

Create `app/src/main/java/com/johnymoo/arverify/capture/FrameRoleClassifier.kt`:

```kotlin
package com.johnymoo.arverify.capture

import kotlin.math.abs

/**
 * Free-mode helper (spec §3): infers which coverage role a frame fills from the
 * camera pitch in degrees from horizontal (-90 = pointing straight down at a table).
 * Pure logic — the device layer supplies the pitch from the ARCore camera pose.
 * Frame accumulation / "enough frames?" reuse the existing CaptureWizardController.
 */
object FrameRoleClassifier {
    /** Pitch at/below this (steeply down) is the top-down stud frame. */
    const val TOP_MAX_PITCH_DEG = -60.0
    /** Pitch within +/- this of horizontal is a side frame. */
    const val SIDE_ABS_PITCH_DEG = 30.0

    fun classify(pitchDeg: Double): CaptureSlot = when {
        pitchDeg <= TOP_MAX_PITCH_DEG -> CaptureSlot.TOP
        abs(pitchDeg) <= SIDE_ABS_PITCH_DEG -> CaptureSlot.SIDE
        else -> CaptureSlot.ANGLE
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.FrameRoleClassifierTest"`
Expected: PASS（3 tests）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/FrameRoleClassifier.kt \
        app/src/test/java/com/johnymoo/arverify/FrameRoleClassifierTest.kt
git commit -m "feat: add FrameRoleClassifier (free-mode pitch->slot)"
```

---

## Task 2: 采集会话模型（CaptureSession 等）

定义 spec §9 的会话/帧数据模型（纯数据类与枚举），供 ManifestCodec 与 Repository 使用。

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/session/SessionModels.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/SessionModelsTest.kt`

- [ ] **Step 1: 写失败测试**

Create `app/src/test/java/com/johnymoo/arverify/SessionModelsTest.kt`:

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CaptureSlot
import com.johnymoo.arverify.session.CaptureMode
import com.johnymoo.arverify.session.CaptureSession
import com.johnymoo.arverify.session.CapturedFrame
import com.johnymoo.arverify.session.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionModelsTest {
    @Test fun hasDepthReflectsDepthFileName() {
        val withDepth = CapturedFrame(CaptureSlot.TOP, "rgb0.jpg", depthFileName = "depth0.png")
        val noDepth = CapturedFrame(CaptureSlot.ANGLE, "rgb1.jpg")
        assertTrue(withDepth.hasDepth)
        assertFalse(noDepth.hasDepth)
    }

    @Test fun sessionHoldsFramesAndMode() {
        val s = CaptureSession(
            partId = "part-abc",
            mode = CaptureMode.RECOGNITION,
            createdAtEpochMs = 1_000L,
            deviceModel = "PLG110",
            status = SessionStatus.PENDING_UPLOAD,
            frames = listOf(CapturedFrame(CaptureSlot.TOP, "rgb0.jpg", "depth0.png")),
        )
        assertEquals(1, s.frames.size)
        assertEquals(CaptureMode.RECOGNITION, s.mode)
        assertEquals(SessionStatus.PENDING_UPLOAD, s.status)
        assertEquals(null, s.recognized)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.SessionModelsTest"`
Expected: FAIL —— `Unresolved reference: session`。

- [ ] **Step 3: 写最小实现**

Create `app/src/main/java/com/johnymoo/arverify/session/SessionModels.kt`:

```kotlin
package com.johnymoo.arverify.session

import com.johnymoo.arverify.capture.CaptureSlot

/** Which capture pipeline produced this session (spec §0 dual-track). */
enum class CaptureMode { RECOGNITION, GENERAL }

/** Lifecycle state shown in the capture library (spec §4). */
enum class SessionStatus { PENDING_UPLOAD, RECOGNIZED, NEEDS_MEASUREMENT, EXPORTED, FAILED }

/** One captured frame as stored on disk; [rgbFileName]/[depthFileName] are names within the session dir. */
data class CapturedFrame(
    val slot: CaptureSlot,
    val rgbFileName: String,
    val depthFileName: String? = null,
    val distanceM: Double? = null,
    val sharpness: Double? = null,
) {
    val hasDepth: Boolean get() = depthFileName != null
}

/** Recognition summary persisted for library display (mirrors backend `recognized`). */
data class RecognizedSummary(
    val system: String? = null,
    val kind: String? = null,
    val unitsX: Int? = null,
    val unitsY: Int? = null,
    val pitchMm: Double? = null,
    val confidence: Double? = null,
)

/** A persisted capture session (one folder on disk). [createdAtEpochMs] is supplied by the caller. */
data class CaptureSession(
    val partId: String,
    val mode: CaptureMode,
    val createdAtEpochMs: Long,
    val deviceModel: String,
    val status: SessionStatus,
    val frames: List<CapturedFrame>,
    val recognized: RecognizedSummary? = null,
)
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.SessionModelsTest"`
Expected: PASS（2 tests）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/session/SessionModels.kt \
        app/src/test/java/com/johnymoo/arverify/SessionModelsTest.kt
git commit -m "feat: add capture session domain models"
```

---

## Task 3: SessionManifestCodec（会话 ↔ JSON，容错）

`CaptureSession` 与 manifest JSON 互转。写入用 Gson tree 保证稳定字段名 + `schema_version`；解析用 tree 容错（缺字段/坏 JSON 返回 null，跳过坏帧），风格对齐现有 `RecognitionResultModel`。

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/session/SessionManifestCodec.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/SessionManifestCodecTest.kt`

- [ ] **Step 1: 写失败测试**

Create `app/src/test/java/com/johnymoo/arverify/SessionManifestCodecTest.kt`:

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CaptureSlot
import com.johnymoo.arverify.session.CaptureMode
import com.johnymoo.arverify.session.CaptureSession
import com.johnymoo.arverify.session.CapturedFrame
import com.johnymoo.arverify.session.RecognizedSummary
import com.johnymoo.arverify.session.SessionManifestCodec
import com.johnymoo.arverify.session.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManifestCodecTest {
    private val sample = CaptureSession(
        partId = "part-9f3a",
        mode = CaptureMode.RECOGNITION,
        createdAtEpochMs = 1_780_000_000_000L,
        deviceModel = "PLG110",
        status = SessionStatus.RECOGNIZED,
        frames = listOf(
            CapturedFrame(CaptureSlot.TOP, "rgb0.jpg", "depth0.png", distanceM = 0.25, sharpness = 182.0),
            CapturedFrame(CaptureSlot.SIDE, "rgb1.jpg"),
        ),
        recognized = RecognizedSummary("feile", "brick", 2, 4, 16.1, 0.93),
    )

    @Test fun roundTripPreservesAllFields() {
        val parsed = SessionManifestCodec.fromJson(SessionManifestCodec.toJson(sample))
        assertEquals(sample, parsed)
    }

    @Test fun jsonCarriesSchemaVersionAndSnakeCaseKeys() {
        val json = SessionManifestCodec.toJson(sample)
        assertTrue(json.contains("\"schema_version\""))
        assertTrue(json.contains("\"part_id\""))
        assertTrue(json.contains("\"created_at_epoch_ms\""))
    }

    @Test fun badJsonReturnsNull() {
        assertNull(SessionManifestCodec.fromJson("not json"))
        assertNull(SessionManifestCodec.fromJson("[]"))
        assertNull(SessionManifestCodec.fromJson("{\"mode\":\"RECOGNITION\"}")) // missing part_id
    }

    @Test fun missingOptionalFieldsTolerated() {
        val json = """{"part_id":"p","mode":"GENERAL","created_at_epoch_ms":5,
            "device_model":"X","status":"EXPORTED","frames":[
              {"slot":"ANGLE","rgb":"a.jpg"},
              {"slot":"BOGUS","rgb":"skip.jpg"},
              {"rgb":"norole.jpg"}
            ]}"""
        val parsed = SessionManifestCodec.fromJson(json)!!
        assertEquals(CaptureMode.GENERAL, parsed.mode)
        assertEquals(1, parsed.frames.size)            // bad/roleless frames skipped
        assertEquals(CaptureSlot.ANGLE, parsed.frames[0].slot)
        assertNull(parsed.recognized)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.SessionManifestCodecTest"`
Expected: FAIL —— `Unresolved reference: SessionManifestCodec`。

- [ ] **Step 3: 写最小实现**

Create `app/src/main/java/com/johnymoo/arverify/session/SessionManifestCodec.kt`:

```kotlin
package com.johnymoo.arverify.session

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.johnymoo.arverify.capture.CaptureSlot

/**
 * CaptureSession <-> manifest JSON (spec §9). Writing uses a Gson tree for stable
 * snake_case keys + schema_version; reading is tolerant (bad JSON / missing required
 * fields -> null; unparseable frames skipped), matching RecognitionResultModel's style.
 */
object SessionManifestCodec {
    const val SCHEMA_VERSION = 1
    private val gson = Gson()

    fun toJson(s: CaptureSession): String {
        val root = JsonObject()
        root.addProperty("schema_version", SCHEMA_VERSION)
        root.addProperty("part_id", s.partId)
        root.addProperty("mode", s.mode.name)
        root.addProperty("created_at_epoch_ms", s.createdAtEpochMs)
        root.addProperty("device_model", s.deviceModel)
        root.addProperty("status", s.status.name)
        val frames = JsonArray()
        for (f in s.frames) {
            val fo = JsonObject()
            fo.addProperty("slot", f.slot.name)
            fo.addProperty("rgb", f.rgbFileName)
            f.depthFileName?.let { fo.addProperty("depth", it) }
            f.distanceM?.let { fo.addProperty("distance_m", it) }
            f.sharpness?.let { fo.addProperty("sharpness", it) }
            frames.add(fo)
        }
        root.add("frames", frames)
        s.recognized?.let { r ->
            val ro = JsonObject()
            r.system?.let { ro.addProperty("system", it) }
            r.kind?.let { ro.addProperty("kind", it) }
            r.unitsX?.let { ro.addProperty("units_x", it) }
            r.unitsY?.let { ro.addProperty("units_y", it) }
            r.pitchMm?.let { ro.addProperty("pitch_mm", it) }
            r.confidence?.let { ro.addProperty("confidence", it) }
            root.add("recognized", ro)
        }
        return gson.toJson(root)
    }

    fun fromJson(json: String): CaptureSession? {
        val root = try {
            JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) { null } ?: return null

        val partId = str(root, "part_id") ?: return null
        val mode = enumOrNull<CaptureMode>(str(root, "mode")) ?: return null
        val status = enumOrNull<SessionStatus>(str(root, "status")) ?: SessionStatus.PENDING_UPLOAD
        val createdAt = lng(root, "created_at_epoch_ms") ?: 0L
        val device = str(root, "device_model") ?: ""

        val frames = arr(root, "frames").mapNotNull { el ->
            val fo = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val slot = enumOrNull<CaptureSlot>(str(fo, "slot")) ?: return@mapNotNull null
            val rgb = str(fo, "rgb") ?: return@mapNotNull null
            CapturedFrame(slot, rgb, str(fo, "depth"), dbl(fo, "distance_m"), dbl(fo, "sharpness"))
        }

        val recognized = obj(root, "recognized")?.let { ro ->
            RecognizedSummary(
                system = str(ro, "system"), kind = str(ro, "kind"),
                unitsX = int(ro, "units_x"), unitsY = int(ro, "units_y"),
                pitchMm = dbl(ro, "pitch_mm"), confidence = dbl(ro, "confidence"),
            )
        }
        return CaptureSession(partId, mode, createdAt, device, status, frames, recognized)
    }

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    private fun member(o: JsonObject, k: String): JsonElement? = o.get(k)?.takeIf { !it.isJsonNull }
    private fun str(o: JsonObject, k: String): String? = member(o, k)?.takeIf { it.isJsonPrimitive }?.asString
    private fun int(o: JsonObject, k: String): Int? = member(o, k)?.takeIf { it.isJsonPrimitive }?.asInt
    private fun dbl(o: JsonObject, k: String): Double? = member(o, k)?.takeIf { it.isJsonPrimitive }?.asDouble
    private fun lng(o: JsonObject, k: String): Long? = member(o, k)?.takeIf { it.isJsonPrimitive }?.asLong
    private fun obj(o: JsonObject, k: String): JsonObject? = member(o, k)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun arr(o: JsonObject, k: String): List<JsonElement> =
        member(o, k)?.takeIf { it.isJsonArray }?.asJsonArray?.toList() ?: emptyList()
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.SessionManifestCodecTest"`
Expected: PASS（4 tests）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/session/SessionManifestCodec.kt \
        app/src/test/java/com/johnymoo/arverify/SessionManifestCodecTest.kt
git commit -m "feat: add SessionManifestCodec (tolerant session<->json)"
```

---

## Task 4: CaptureLibraryRepository（扫描 / 原子写 / 删除）

spec §9 文件系统持久化：列出会话（最新在前）、**原子写 manifest（临时文件 + rename）**、删除会话目录。用 JUnit `TemporaryFolder` 隔离测试。

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/session/CaptureLibraryRepository.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/CaptureLibraryRepositoryTest.kt`

- [ ] **Step 1: 写失败测试**

Create `app/src/test/java/com/johnymoo/arverify/CaptureLibraryRepositoryTest.kt`:

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CaptureSlot
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.session.CaptureMode
import com.johnymoo.arverify.session.CaptureSession
import com.johnymoo.arverify.session.CapturedFrame
import com.johnymoo.arverify.session.SessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CaptureLibraryRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun session(partId: String, createdAt: Long, status: SessionStatus = SessionStatus.PENDING_UPLOAD) =
        CaptureSession(
            partId = partId, mode = CaptureMode.RECOGNITION, createdAtEpochMs = createdAt,
            deviceModel = "PLG110", status = status,
            frames = listOf(CapturedFrame(CaptureSlot.TOP, "rgb0.jpg", "depth0.png")),
        )

    @Test fun writeThenListReturnsNewestFirst() {
        val root = tmp.newFolder("captures")
        val repo = CaptureLibraryRepository(root)
        repo.writeManifest(File(root, "part-old"), session("part-old", 1_000L))
        repo.writeManifest(File(root, "part-new"), session("part-new", 2_000L))

        val entries = repo.listSessions()
        assertEquals(2, entries.size)
        assertEquals("part-new", entries[0].session.partId) // newest first
        assertEquals("part-old", entries[1].session.partId)
    }

    @Test fun writeIsAtomicNoTmpLeftAndOverwrites() {
        val root = tmp.newFolder("captures")
        val repo = CaptureLibraryRepository(root)
        val dir = File(root, "part-x")
        repo.writeManifest(dir, session("part-x", 1_000L, SessionStatus.PENDING_UPLOAD))
        repo.writeManifest(dir, session("part-x", 1_000L, SessionStatus.RECOGNIZED)) // overwrite

        assertFalse(File(dir, "manifest.json.tmp").exists())
        assertTrue(File(dir, "manifest.json").exists())
        val only = repo.listSessions().single()
        assertEquals(SessionStatus.RECOGNIZED, only.session.status)
    }

    @Test fun listSkipsDirsWithoutValidManifest() {
        val root = tmp.newFolder("captures")
        val repo = CaptureLibraryRepository(root)
        repo.writeManifest(File(root, "good"), session("good", 1_000L))
        File(root, "empty").mkdirs()                         // no manifest
        File(File(root, "bad").apply { mkdirs() }, "manifest.json").writeText("garbage")

        assertEquals(1, repo.listSessions().size)
    }

    @Test fun deleteRemovesSessionDir() {
        val root = tmp.newFolder("captures")
        val repo = CaptureLibraryRepository(root)
        val dir = File(root, "part-del")
        repo.writeManifest(dir, session("part-del", 1_000L))
        File(dir, "rgb0.jpg").writeBytes(byteArrayOf(1, 2, 3)) // a frame file alongside manifest

        val entry = repo.listSessions().single()
        assertTrue(repo.delete(entry))
        assertFalse(dir.exists())
        assertTrue(repo.listSessions().isEmpty())
    }

    @Test fun missingRootListsEmpty() {
        val repo = CaptureLibraryRepository(File(tmp.root, "does-not-exist"))
        assertTrue(repo.listSessions().isEmpty())
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.CaptureLibraryRepositoryTest"`
Expected: FAIL —— `Unresolved reference: CaptureLibraryRepository`。

- [ ] **Step 3: 写最小实现**

Create `app/src/main/java/com/johnymoo/arverify/session/CaptureLibraryRepository.kt`:

```kotlin
package com.johnymoo.arverify.session

import java.io.File

/** A session on disk: its directory + parsed manifest. */
data class SessionEntry(val dir: File, val session: CaptureSession)

/**
 * Filesystem-backed capture library (spec §9). Sessions live under [rootDir] as
 * one folder each containing [MANIFEST] + frame files. No database: the library is
 * built by scanning + parsing manifests. Manifests are written atomically.
 */
class CaptureLibraryRepository(private val rootDir: File) {

    /** Scans [rootDir] for subdirectories holding a valid manifest, newest first. */
    fun listSessions(): List<SessionEntry> {
        val dirs = rootDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val mf = File(dir, MANIFEST)
            if (!mf.isFile) return@mapNotNull null
            val s = SessionManifestCodec.fromJson(mf.readText()) ?: return@mapNotNull null
            SessionEntry(dir, s)
        }.sortedByDescending { it.session.createdAtEpochMs }
    }

    /** Atomically writes the manifest into [dir] (temp file + rename) so a crash can't leave it partial. */
    fun writeManifest(dir: File, session: CaptureSession) {
        dir.mkdirs()
        val tmp = File(dir, TMP)
        tmp.writeText(SessionManifestCodec.toJson(session))
        val dest = File(dir, MANIFEST)
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    /** Deletes the whole session directory (manifest + all frame files). */
    fun delete(entry: SessionEntry): Boolean = entry.dir.deleteRecursively()

    companion object {
        const val MANIFEST = "manifest.json"
        private const val TMP = "manifest.json.tmp"
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.CaptureLibraryRepositoryTest"`
Expected: PASS（5 tests）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/session/CaptureLibraryRepository.kt \
        app/src/test/java/com/johnymoo/arverify/CaptureLibraryRepositoryTest.kt
git commit -m "feat: add CaptureLibraryRepository (scan/atomic-write/delete)"
```

---

## Task 5: DepthOverlayRange（实时深度叠加的固定量程）

spec §10：实时深度上色用**固定量程**（按采集距离量程换算 m→mm）保证逐帧颜色稳定不闪烁。本任务实现该纯换算；GL 着色器与上色调用（用现有 `DepthColorMap`）在 Plan 3 真机实现。

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/depth/DepthOverlayRange.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/DepthOverlayRangeTest.kt`

- [ ] **Step 1: 写失败测试**

Create `app/src/test/java/com/johnymoo/arverify/DepthOverlayRangeTest.kt`:

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.depth.DepthOverlayRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DepthOverlayRangeTest {
    @Test fun convertsCaptureBandMetersToMillimeters() {
        val r = DepthOverlayRange.fromDistanceBand(0.15, 0.70) // CaptureConfig defaults
        assertEquals(150, r.minMm)
        assertEquals(700, r.maxMm)
    }

    @Test fun clampsDegenerateBandToValidRange() {
        val r = DepthOverlayRange.fromDistanceBand(0.0, 0.0)
        assertTrue(r.minMm >= 1)
        assertTrue(r.maxMm > r.minMm)
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.DepthOverlayRangeTest"`
Expected: FAIL —— `Unresolved reference: DepthOverlayRange`。

- [ ] **Step 3: 写最小实现**

Create `app/src/main/java/com/johnymoo/arverify/depth/DepthOverlayRange.kt`:

```kotlin
package com.johnymoo.arverify.depth

/** Fixed depth range (mm) for stable live colorization (spec §10). */
data class DepthRangeMm(val minMm: Int, val maxMm: Int)

/**
 * Derives a fixed mm range from the capture distance band (meters). Using a fixed
 * range (vs per-frame min/max) keeps the live depth heatmap colors stable / flicker-free.
 * Consumed by the GL overlay shader (Plan 3) together with DepthColorMap.
 */
object DepthOverlayRange {
    fun fromDistanceBand(minDistanceM: Double, maxDistanceM: Double): DepthRangeMm {
        val lo = (minDistanceM * 1000.0).toInt().coerceAtLeast(1)
        val hi = (maxDistanceM * 1000.0).toInt().coerceAtLeast(lo + 1)
        return DepthRangeMm(lo, hi)
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.DepthOverlayRangeTest"`
Expected: PASS（2 tests）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/depth/DepthOverlayRange.kt \
        app/src/test/java/com/johnymoo/arverify/DepthOverlayRangeTest.kt
git commit -m "feat: add DepthOverlayRange (fixed mm band for live depth)"
```

---

## 收尾：整组单测回归

- [ ] **Step 1: 跑本计划全部新测试**

Run:
```bash
./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.FrameRoleClassifierTest" \
  --tests "com.johnymoo.arverify.SessionModelsTest" \
  --tests "com.johnymoo.arverify.SessionManifestCodecTest" \
  --tests "com.johnymoo.arverify.CaptureLibraryRepositoryTest" \
  --tests "com.johnymoo.arverify.DepthOverlayRangeTest"
```
Expected: PASS（16 tests）。

- [ ] **Step 2: 跑全量单测确认无回归**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL，含既有测试与本计划新增测试全部通过。

> 备注：本计划无需真机；`assembleDebug` / `installDebug` 不在范围内（无 UI/AR 改动）。Plan 2 起才涉及真机验证。

---

## 自检（spec 覆盖 / 占位符 / 类型一致性）

- **spec 覆盖**：本计划落地 spec §8「新增纯逻辑」的可单测部分——
  - `CoverageStateMachine（自由模式）` → 由 `FrameRoleClassifier`（角色分类，Task 1）+ 复用既有 `CaptureWizardController`（任意顺序累计 + `canUpload`）共同实现；**未新增重复的状态机**（DRY）。
  - `CaptureSession/SessionManifestCodec`（Task 2、3）、`CaptureLibraryRepository`（Task 4）、深度归一化/配色的量程部分 `DepthOverlayRange`（Task 5，§10）。
  - 余下 §10 的 GL 上传/着色器、§7 Compose/AR、§4 各屏 UI、§11 上传/§5 契约接通 → Plan 2–4（已在"计划系列"列明，非本计划遗漏）。
- **占位符**：无 TODO/TBD；每个代码步骤含完整可编译代码与确切命令/期望输出。
- **类型一致性**：复用既有 `CaptureSlot{TOP,SIDE,ANGLE}`；`CaptureSession` 字段（`partId/mode/createdAtEpochMs/deviceModel/status/frames/recognized`）在 Task 2 定义，Task 3/4 一致引用；`SessionManifestCodec.toJson/fromJson`、`CaptureLibraryRepository.listSessions/writeManifest/delete/SessionEntry`、`DepthOverlayRange.fromDistanceBand`/`DepthRangeMm` 命名前后一致；`DepthOverlayRange` 量程默认与 `CaptureConfig`（minDistanceM=0.15 / maxDistanceM=0.70）对齐。
