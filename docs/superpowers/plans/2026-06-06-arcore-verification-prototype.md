# ARCore Verification + Depth Capture Prototype — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a native Android (Kotlin) single-screen app that authoritatively verifies whether ARCore and the Depth API work on an OPPO Find X9 Pro (PLG110), reports device compatibility with a recommended route, and — when Depth is supported — captures a colorized depth map plus a `.ply` point cloud.

**Architecture:** One `MainActivity` runs capability checks (ARCore availability, Depth support) and renders a status card; a separate `CaptureActivity` runs a minimal ARCore GL session to grab a depth frame + point cloud. Device-independent logic (route decision, PLY serialization, JSON report, depth→color mapping) lives in small pure Kotlin units that are JVM-unit-tested; Android/AR/GL code is verified on-device.

**Tech Stack:** Kotlin, ARCore SDK `com.google.ar:core:1.54.0`, AndroidX (AppCompat, Material 3, Activity, ConstraintLayout), XML Views + ViewBinding, OpenGL ES 2.0, JUnit 4. AGP 8.7.3 / Gradle 8.9 / Kotlin 2.0.21 / JDK 17. `minSdk 24`, `compile/targetSdk 35`.

---

## ⚠️ Environment note (read first)

The original dev box has **no Android SDK, Gradle, or JDK 17**, so it **cannot compile or run** this project. All `./gradlew ...` verification commands below must run on a machine with the Android toolchain (i.e., in **Android Studio** on the user's machine, which is the chosen delivery path). When executing this plan, author the files exactly as specified; the build/test/device verification happens in Android Studio. Treat each "Run / Expected" block as the check the implementer performs on a capable machine.

## Scope

In scope: ARCore availability + install flow, Depth API support detection, device compatibility report (on-screen + JSON + readable text export), recommended-route decision, depth capture (colorized 2D depth PNG + sparse point cloud `.ply`), capture-guidance tips.

Deferred (separate future plans, per spec): OpenCV photo-modeling fallback, backend photogrammetry / mesh, dimension measurement, dense depth-unprojected point clouds, interactive 3D viewer.

## File Structure

```
ARCore-prototype/                      (repo root = Android project root)
├── settings.gradle.kts
├── build.gradle.kts                   (root)
├── gradle.properties
├── gradlew, gradlew.bat               (generated in Task 1)
├── gradle/wrapper/gradle-wrapper.properties
├── gradle/wrapper/gradle-wrapper.jar  (generated in Task 1)
├── .gitignore
├── README.md                          (Task 14)
├── docs/superpowers/...               (specs + this plan; already present)
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/johnymoo/arverify/
        │   │   ├── MainActivity.kt
        │   │   ├── CaptureActivity.kt
        │   │   ├── model/RecommendedRoute.kt
        │   │   ├── model/DeviceInfo.kt
        │   │   ├── model/CapabilityReport.kt        (+ enum ArCoreStatus)
        │   │   ├── ar/CapabilityChecker.kt
        │   │   ├── depth/DepthColorMap.kt           (pure)
        │   │   ├── depth/DepthColorizer.kt          (android)
        │   │   ├── export/PlyWriter.kt              (pure)
        │   │   ├── export/ReportExporter.kt
        │   │   └── render/BackgroundRenderer.kt
        │   └── res/
        │       ├── layout/activity_main.xml
        │       ├── layout/activity_capture.xml
        │       ├── values/strings.xml
        │       ├── values/colors.xml
        │       ├── values/themes.xml
        │       ├── drawable/ic_launcher.xml
        │       └── xml/file_paths.xml
        └── test/java/com/johnymoo/arverify/
            ├── RecommendedRouteTest.kt
            ├── CapabilityReportJsonTest.kt
            ├── PlyWriterTest.kt
            └── DepthColorMapTest.kt
```

**Package / applicationId:** `com.johnymoo.arverify`. **FileProvider authority:** `${applicationId}.fileprovider`.

---

## Task 1: Gradle project scaffolding + wrapper

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `.gitignore`
- Create: `app/build.gradle.kts`, `app/proguard-rules.pro`
- Create: `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "ARCoreVerify"
include(":app")
```

- [ ] **Step 2: Create root `build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
```

- [ ] **Step 3: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 4: Create `gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.9-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 5: Create `app/build.gradle.kts`**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.johnymoo.arverify"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.johnymoo.arverify"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.ar:core:1.54.0")
    testImplementation("junit:junit:4.13.2")
}
```

- [ ] **Step 6: Create `app/proguard-rules.pro`** (empty placeholder is fine; release is not minified)

```proguard
# No custom ProGuard rules; release build is not minified for this prototype.
```

- [ ] **Step 7: Create `.gitignore`**

```gitignore
*.iml
.gradle/
/local.properties
.idea/
.DS_Store
build/
/captures
.externalNativeBuild
.cxx
```

- [ ] **Step 8: Generate the Gradle wrapper jar + scripts**

The wrapper binary (`gradle/wrapper/gradle-wrapper.jar`, `gradlew`, `gradlew.bat`) is binary/generated and is not authored by hand. Generate it once on the build machine:

Run (from a terminal that has a Gradle install, or Android Studio's embedded terminal):
`gradle wrapper --gradle-version 8.9 --distribution-type bin`
Expected: creates `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`.
Alternative: simply open the project folder in **Android Studio** → it syncs with its bundled Gradle and generates the wrapper automatically.

- [ ] **Step 9: Verify the project configures**

Run: `./gradlew :app:tasks` (in Android Studio terminal)
Expected: BUILD SUCCESSFUL, prints available tasks (no plugin/version resolution errors).

- [ ] **Step 10: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties .gitignore \
        app/build.gradle.kts app/proguard-rules.pro gradle/wrapper/gradle-wrapper.properties \
        gradlew gradlew.bat gradle/wrapper/gradle-wrapper.jar
git commit -m "build: scaffold Android Gradle project (ARCore SDK 1.54.0)"
```

---

## Task 2: Manifest + base resources

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`, `values/colors.xml`, `values/themes.xml`
- Create: `app/src/main/res/drawable/ic_launcher.xml`, `res/xml/file_paths.xml`

- [ ] **Step 1: Create `AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.CAMERA" />

    <!-- AR Optional: the app installs and runs on AR-incapable devices so it can REPORT status. -->
    <uses-feature android:name="android.hardware.camera.ar" android:required="false" />
    <uses-feature android:glEsVersion="0x00020000" android:required="true" />

    <application
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.ARCoreVerify"
        tools:targetApi="24">

        <!-- AR Optional marker for Google Play Services for AR -->
        <meta-data android:name="com.google.ar.core" android:value="optional" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|screenLayout|keyboard|keyboardHidden|navigation">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".CaptureActivity"
            android:exported="false"
            android:screenOrientation="locked"
            android:configChanges="orientation|screenSize|screenLayout|keyboard|keyboardHidden|navigation" />

        <provider
            android:name="androidx.core.content.FileProvider"
            android:authorities="${applicationId}.fileprovider"
            android:exported="false"
            android:grantUriPermissions="true">
            <meta-data
                android:name="android.support.FILE_PROVIDER_PATHS"
                android:resource="@xml/file_paths" />
        </provider>
    </application>
</manifest>
```

- [ ] **Step 2: Create `res/xml/file_paths.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<paths>
    <external-files-path name="exports" path="exports/" />
</paths>
```

- [ ] **Step 3: Create `res/values/strings.xml`** (Chinese UI to match the issue #1 mockup)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">原型验证工具</string>
    <string name="title">原型验证工具</string>
    <string name="btn_check_arcore">检查 ARCore</string>
    <string name="btn_check_depth">检查 Depth API</string>
    <string name="btn_capture">开始采集</string>
    <string name="btn_export">导出结果</string>
    <string name="section_result">当前检测结果</string>
    <string name="supported">支持</string>
    <string name="not_supported">不支持</string>
    <!-- %1$s is replaced with 支持 / 不支持 or the route label -->
    <string name="status_arcore">ARCore：%1$s</string>
    <string name="status_depth">Depth：%1$s</string>
    <string name="status_route">推荐路线：%1$s</string>
    <string name="capture_tips">光线均匀 · 背景简洁 · 保留尺子/标记 · 正面/侧面/顶面/斜角 · 每次 1~3 个零件</string>
    <string name="btn_shutter">拍摄</string>
    <string name="btn_done">完成</string>
</resources>
```

- [ ] **Step 4: Create `res/values/colors.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>
    <color name="primary">#FF1565C0</color>
    <color name="card_bg">#FFF1F5F9</color>
</resources>
```

- [ ] **Step 5: Create `res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.ARCoreVerify" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
    </style>
</resources>
```

- [ ] **Step 6: Create `res/drawable/ic_launcher.xml`** (vector launcher icon — no binary assets)

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp" android:height="48dp"
    android:viewportWidth="48" android:viewportHeight="48">
    <path android:fillColor="#1565C0" android:pathData="M0,0h48v48h-48z" />
    <path android:fillColor="#FFFFFF"
        android:pathData="M24,10 L38,18 L38,30 L24,38 L10,30 L10,18 Z" />
    <path android:fillColor="#1565C0"
        android:pathData="M24,16 L32,20.5 L32,27.5 L24,32 L16,27.5 L16,20.5 Z" />
</vector>
```

- [ ] **Step 7: Verify resources compile**

Run: `./gradlew :app:processDebugResources`
Expected: BUILD SUCCESSFUL (no missing-resource or manifest-merge errors). (Activities `.MainActivity`/`.CaptureActivity` don't exist yet — this task only compiles resources, not Kotlin; that's fine. If you instead run a full build now it will fail until Task 13. Use the resource task here.)

- [ ] **Step 8: Commit**

```bash
git add app/src/main/AndroidManifest.xml app/src/main/res
git commit -m "feat: add manifest (AR Optional) + Chinese strings, theme, icon, file paths"
```

---

## Task 3: RecommendedRoute (pure, TDD)

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/model/RecommendedRoute.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/RecommendedRouteTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.model.RecommendedRoute
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendedRouteTest {
    @Test fun arcoreAndDepth_givesDepthCapture() {
        assertEquals(RecommendedRoute.DEPTH_CAPTURE, RecommendedRoute.decide(arcoreOk = true, depthOk = true))
    }

    @Test fun arcoreButNoDepth_givesPhotoModeling() {
        assertEquals(RecommendedRoute.PHOTO_MODELING, RecommendedRoute.decide(arcoreOk = true, depthOk = false))
    }

    @Test fun noArcore_alwaysPhotoModeling() {
        assertEquals(RecommendedRoute.PHOTO_MODELING, RecommendedRoute.decide(arcoreOk = false, depthOk = true))
        assertEquals(RecommendedRoute.PHOTO_MODELING, RecommendedRoute.decide(arcoreOk = false, depthOk = false))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.RecommendedRouteTest"`
Expected: FAIL — unresolved reference `RecommendedRoute`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.johnymoo.arverify.model

/** Which modeling route the report recommends, per the issue #1 MVP conclusion. */
enum class RecommendedRoute(val labelZh: String) {
    DEPTH_CAPTURE("深度采集"),
    PHOTO_MODELING("照片建模");

    companion object {
        fun decide(arcoreOk: Boolean, depthOk: Boolean): RecommendedRoute =
            if (arcoreOk && depthOk) DEPTH_CAPTURE else PHOTO_MODELING
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.RecommendedRouteTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/model/RecommendedRoute.kt \
        app/src/test/java/com/johnymoo/arverify/RecommendedRouteTest.kt
git commit -m "feat: add RecommendedRoute decision (depth capture vs photo modeling)"
```

---

## Task 4: DeviceInfo

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/model/DeviceInfo.kt`

No unit test: the data class is pure but its only logic (`collect`) reads Android `Build`/`ActivityManager`, verified on-device. It is constructed directly in Task 5's test.

- [ ] **Step 1: Create `DeviceInfo.kt`**

```kotlin
package com.johnymoo.arverify.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** Plain device facts captured for the compatibility report. */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val sdkInt: Int,
    val fingerprint: String,
    val glesVersion: String,
) {
    companion object {
        fun collect(context: Context): DeviceInfo {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val gles = am.deviceConfigurationInfo.glEsVersion ?: "unknown"
            return DeviceInfo(
                manufacturer = Build.MANUFACTURER ?: "",
                model = Build.MODEL ?: "",
                device = Build.DEVICE ?: "",
                androidRelease = Build.VERSION.RELEASE ?: "",
                sdkInt = Build.VERSION.SDK_INT,
                fingerprint = Build.FINGERPRINT ?: "",
                glesVersion = gles,
            )
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (other Kotlin files not yet referenced by this one).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/model/DeviceInfo.kt
git commit -m "feat: add DeviceInfo capture (build + OpenGL ES version)"
```

---

## Task 5: CapabilityReport + ArCoreStatus (pure serialization, TDD)

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/model/CapabilityReport.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/CapabilityReportJsonTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.model.ArCoreStatus
import com.johnymoo.arverify.model.CapabilityReport
import com.johnymoo.arverify.model.DeviceInfo
import com.johnymoo.arverify.model.RecommendedRoute
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityReportJsonTest {
    private fun sample() = CapabilityReport(
        deviceInfo = DeviceInfo(
            manufacturer = "OPPO", model = "PLG110", device = "x9pro",
            androidRelease = "15", sdkInt = 35,
            fingerprint = "oppo/PLG110/x9pro", glesVersion = "OpenGL ES 3.2"
        ),
        arcoreStatus = ArCoreStatus.SUPPORTED_INSTALLED,
        depthAutomaticSupported = true,
        rawDepthSupported = false,
        recommendedRoute = RecommendedRoute.DEPTH_CAPTURE,
        notes = listOf("ok \"quote\" test"),
        timestampIso = "2026-06-06T10:30:00",
    )

    @Test fun jsonContainsKeyFields() {
        val j = sample().toJson()
        assertTrue(j.contains("\"model\": \"PLG110\""))
        assertTrue(j.contains("\"arcoreStatus\": \"SUPPORTED_INSTALLED\""))
        assertTrue(j.contains("\"depthAutomaticSupported\": true"))
        assertTrue(j.contains("\"recommendedRoute\": \"DEPTH_CAPTURE\""))
    }

    @Test fun jsonEscapesQuotes() {
        assertTrue(sample().toJson().contains("\\\"quote\\\""))
    }

    @Test fun readableTextHasChineseRoute() {
        assertTrue(sample().toReadableText().contains("深度采集"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.CapabilityReportJsonTest"`
Expected: FAIL — unresolved reference `CapabilityReport` / `ArCoreStatus`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.johnymoo.arverify.model

/** Normalized ARCore availability status (mapped from ArCoreApk.Availability). */
enum class ArCoreStatus {
    SUPPORTED_INSTALLED,
    SUPPORTED_NOT_INSTALLED,
    SUPPORTED_APK_TOO_OLD,
    UNSUPPORTED,
    UNKNOWN,
}

/** The full device compatibility report; serializable to JSON and human-readable text. */
data class CapabilityReport(
    val deviceInfo: DeviceInfo,
    val arcoreStatus: ArCoreStatus,
    val depthAutomaticSupported: Boolean,
    val rawDepthSupported: Boolean,
    val recommendedRoute: RecommendedRoute,
    val notes: List<String>,
    val timestampIso: String,
) {
    val arcoreUsable: Boolean get() = arcoreStatus == ArCoreStatus.SUPPORTED_INSTALLED

    fun toJson(): String = buildString {
        append("{\n")
        append("  \"timestamp\": \"").append(esc(timestampIso)).append("\",\n")
        append("  \"device\": {\n")
        append("    \"manufacturer\": \"").append(esc(deviceInfo.manufacturer)).append("\",\n")
        append("    \"model\": \"").append(esc(deviceInfo.model)).append("\",\n")
        append("    \"device\": \"").append(esc(deviceInfo.device)).append("\",\n")
        append("    \"androidRelease\": \"").append(esc(deviceInfo.androidRelease)).append("\",\n")
        append("    \"sdkInt\": ").append(deviceInfo.sdkInt).append(",\n")
        append("    \"fingerprint\": \"").append(esc(deviceInfo.fingerprint)).append("\",\n")
        append("    \"glesVersion\": \"").append(esc(deviceInfo.glesVersion)).append("\"\n")
        append("  },\n")
        append("  \"arcoreStatus\": \"").append(arcoreStatus.name).append("\",\n")
        append("  \"depthAutomaticSupported\": ").append(depthAutomaticSupported).append(",\n")
        append("  \"rawDepthSupported\": ").append(rawDepthSupported).append(",\n")
        append("  \"recommendedRoute\": \"").append(recommendedRoute.name).append("\",\n")
        append("  \"notes\": [")
        append(notes.joinToString(", ") { "\"" + esc(it) + "\"" })
        append("]\n")
        append("}\n")
    }

    fun toReadableText(): String = buildString {
        appendLine("ARCore 兼容性报告")
        appendLine("时间: $timestampIso")
        appendLine("设备: ${deviceInfo.manufacturer} ${deviceInfo.model} (${deviceInfo.device})")
        appendLine("Android: ${deviceInfo.androidRelease} (SDK ${deviceInfo.sdkInt})")
        appendLine("OpenGL: ${deviceInfo.glesVersion}")
        appendLine("ARCore 状态: ${arcoreStatus.name}")
        appendLine("Depth (AUTOMATIC): ${if (depthAutomaticSupported) "支持" else "不支持"}")
        appendLine("Raw Depth: ${if (rawDepthSupported) "支持" else "不支持"}")
        appendLine("推荐路线: ${recommendedRoute.labelZh}")
        if (notes.isNotEmpty()) {
            appendLine("备注:")
            notes.forEach { appendLine("  - $it") }
        }
    }

    private fun esc(s: String): String = buildString {
        for (c in s) when (c) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(c)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.CapabilityReportJsonTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/model/CapabilityReport.kt \
        app/src/test/java/com/johnymoo/arverify/CapabilityReportJsonTest.kt
git commit -m "feat: add CapabilityReport with JSON + readable-text serialization"
```

---

## Task 6: PlyWriter (pure, TDD)

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/export/PlyWriter.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/PlyWriterTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.export.PlyWriter
import org.junit.Assert.assertTrue
import org.junit.Test

class PlyWriterTest {
    @Test fun writesHeaderAndOneVertex() {
        val ply = PlyWriter.toAsciiPly(floatArrayOf(1f, 2f, 3f, 0.5f))
        assertTrue(ply.startsWith("ply"))
        assertTrue(ply.contains("format ascii 1.0"))
        assertTrue(ply.contains("element vertex 1"))
        assertTrue(ply.contains("property float x"))
        assertTrue(ply.contains("property float confidence"))
        assertTrue(ply.contains("end_header"))
        assertTrue(ply.contains("1.000000 2.000000 3.000000 0.500000"))
    }

    @Test fun emptyInputHasZeroVertices() {
        assertTrue(PlyWriter.toAsciiPly(floatArrayOf()).contains("element vertex 0"))
    }

    @Test fun ignoresTrailingPartialPoint() {
        // 5 floats => only 1 complete (x,y,z,confidence) point
        assertTrue(PlyWriter.toAsciiPly(floatArrayOf(1f, 2f, 3f, 1f, 9f)).contains("element vertex 1"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.PlyWriterTest"`
Expected: FAIL — unresolved reference `PlyWriter`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.johnymoo.arverify.export

import java.util.Locale

/** Serializes an ARCore point cloud (x,y,z,confidence per point) to ASCII PLY. */
object PlyWriter {
    /**
     * @param points flat array of 4 floats per point: x, y, z, confidence.
     *               A trailing partial group (size not a multiple of 4) is ignored.
     */
    fun toAsciiPly(points: FloatArray): String {
        val count = points.size / 4
        return buildString {
            append("ply\n")
            append("format ascii 1.0\n")
            append("comment Generated by ARCoreVerify\n")
            append("element vertex ").append(count).append('\n')
            append("property float x\n")
            append("property float y\n")
            append("property float z\n")
            append("property float confidence\n")
            append("end_header\n")
            for (i in 0 until count) {
                val o = i * 4
                append(
                    String.format(
                        Locale.US, "%.6f %.6f %.6f %.6f\n",
                        points[o], points[o + 1], points[o + 2], points[o + 3]
                    )
                )
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.PlyWriterTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/export/PlyWriter.kt \
        app/src/test/java/com/johnymoo/arverify/PlyWriterTest.kt
git commit -m "feat: add PlyWriter (ASCII PLY from point cloud)"
```

---

## Task 7: DepthColorMap (pure, TDD)

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/depth/DepthColorMap.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/DepthColorMapTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.depth.DepthColorMap
import org.junit.Assert.assertEquals
import org.junit.Test

class DepthColorMapTest {
    @Test fun invalidDepthIsTransparent() {
        assertEquals(0, DepthColorMap.toArgb(0, 100, 1000))
    }

    @Test fun nearIsOpaqueRed() {
        val c = DepthColorMap.toArgb(100, 100, 1000)
        assertEquals(0xFF, (c ushr 24) and 0xFF) // opaque
        assertEquals(0xFF, (c ushr 16) and 0xFF) // red max near
        assertEquals(0x00, c and 0xFF)           // blue zero near
    }

    @Test fun farIsBlue() {
        val c = DepthColorMap.toArgb(1000, 100, 1000)
        assertEquals(0x00, (c ushr 16) and 0xFF) // red zero far
        assertEquals(0xFF, c and 0xFF)           // blue max far
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.DepthColorMapTest"`
Expected: FAIL — unresolved reference `DepthColorMap`.

- [ ] **Step 3: Write minimal implementation**

```kotlin
package com.johnymoo.arverify.depth

import kotlin.math.abs

/** Pure depth(mm) -> ARGB color mapping. Near = warm (red), far = cool (blue). */
object DepthColorMap {
    /** Returns packed ARGB. Invalid depth (mm <= 0) maps to fully transparent (0). */
    fun toArgb(mm: Int, minMm: Int, maxMm: Int): Int {
        if (mm <= 0) return 0
        val lo = minMm.coerceAtLeast(1)
        val hi = maxMm.coerceAtLeast(lo + 1)
        val t = ((mm - lo).toFloat() / (hi - lo)).coerceIn(0f, 1f)
        val r = ((1f - t) * 255f).toInt().coerceIn(0, 255)
        val b = (t * 255f).toInt().coerceIn(0, 255)
        val g = ((1f - abs(t - 0.5f) * 2f) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.DepthColorMapTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/depth/DepthColorMap.kt \
        app/src/test/java/com/johnymoo/arverify/DepthColorMapTest.kt
git commit -m "feat: add DepthColorMap (depth-to-color mapping)"
```

---

## Task 8: DepthColorizer (Android image → bitmap)

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/depth/DepthColorizer.kt`

No JVM unit test (uses `android.media.Image` / `Bitmap`); verified on-device in Task 12.

- [ ] **Step 1: Create `DepthColorizer.kt`**

```kotlin
package com.johnymoo.arverify.depth

import android.graphics.Bitmap
import android.media.Image
import java.nio.ByteOrder

/** Converts an ARCore DEPTH16 image (16-bit millimeters per pixel) to a colorized Bitmap. */
object DepthColorizer {
    private const val MAX_VALID_MM = 8000

    fun colorize(image: Image): Bitmap {
        val width = image.width
        val height = image.height
        val plane = image.planes[0]
        val shorts = plane.buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
        val rowStrideShorts = plane.rowStride / 2

        val mmGrid = IntArray(width * height)
        var minMm = Int.MAX_VALUE
        var maxMm = 0
        for (y in 0 until height) {
            val rowStart = y * rowStrideShorts
            for (x in 0 until width) {
                val mm = shorts.get(rowStart + x).toInt() and 0xFFFF
                mmGrid[y * width + x] = mm
                if (mm in 1..MAX_VALID_MM) {
                    if (mm < minMm) minMm = mm
                    if (mm > maxMm) maxMm = mm
                }
            }
        }
        if (minMm == Int.MAX_VALUE) { minMm = 0; maxMm = 1 }

        val pixels = IntArray(width * height)
        for (i in pixels.indices) pixels[i] = DepthColorMap.toArgb(mmGrid[i], minMm, maxMm)

        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/depth/DepthColorizer.kt
git commit -m "feat: add DepthColorizer (DEPTH16 image to colorized bitmap)"
```

---

## Task 9: CapabilityChecker (ARCore availability + depth probe)

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/ar/CapabilityChecker.kt`

No JVM unit test (uses ARCore + Android); verified on-device.

- [ ] **Step 1: Create `CapabilityChecker.kt`**

```kotlin
package com.johnymoo.arverify.ar

import android.app.Activity
import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.johnymoo.arverify.model.ArCoreStatus
import com.johnymoo.arverify.model.CapabilityReport
import com.johnymoo.arverify.model.DeviceInfo
import com.johnymoo.arverify.model.RecommendedRoute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Runs ARCore availability + Depth API support checks and assembles a CapabilityReport. */
class CapabilityChecker(private val context: Context) {

    fun availability(): ArCoreApk.Availability =
        ArCoreApk.getInstance().checkAvailability(context)

    /** Launches the Play Store install dialog if needed. Must be called from an Activity. */
    fun requestInstall(activity: Activity, userRequested: Boolean): ArCoreApk.InstallStatus =
        ArCoreApk.getInstance().requestInstall(activity, userRequested)

    data class DepthSupport(val automatic: Boolean, val rawDepth: Boolean, val note: String?)

    /** Creates a throwaway Session to probe depth support. Requires ARCore installed. */
    fun probeDepth(): DepthSupport {
        var session: Session? = null
        return try {
            session = Session(context)
            val auto = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
            val raw = session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)
            DepthSupport(auto, raw, null)
        } catch (e: Exception) {
            DepthSupport(false, false, "Depth probe failed: ${e.javaClass.simpleName}")
        } finally {
            session?.close()
        }
    }

    fun buildReport(activity: Activity): CapabilityReport {
        val deviceInfo = DeviceInfo.collect(activity)
        val availability = availability()
        val status = mapStatus(availability)
        val notes = mutableListOf<String>()

        val depth = if (status == ArCoreStatus.SUPPORTED_INSTALLED) probeDepth()
        else DepthSupport(false, false, "ARCore 未安装，未探测 Depth")
        depth.note?.let { notes.add(it) }

        when (availability) {
            ArCoreApk.Availability.UNKNOWN_CHECKING,
            ArCoreApk.Availability.UNKNOWN_ERROR,
            ArCoreApk.Availability.UNKNOWN_TIMED_OUT ->
                notes.add("ARCore 可用性尚未确定（$availability），请再次点击“检查 ARCore”。")
            else -> {}
        }
        if (status == ArCoreStatus.SUPPORTED_NOT_INSTALLED) {
            notes.add("若安装失败：可从 OPPO 应用商店或侧载安装 “Google Play Services for AR”。")
        }

        val route = RecommendedRoute.decide(
            arcoreOk = status == ArCoreStatus.SUPPORTED_INSTALLED,
            depthOk = depth.automatic
        )
        return CapabilityReport(
            deviceInfo = deviceInfo,
            arcoreStatus = status,
            depthAutomaticSupported = depth.automatic,
            rawDepthSupported = depth.rawDepth,
            recommendedRoute = route,
            notes = notes,
            timestampIso = nowIso(),
        )
    }

    private fun mapStatus(a: ArCoreApk.Availability): ArCoreStatus = when (a) {
        ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArCoreStatus.SUPPORTED_INSTALLED
        ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArCoreStatus.SUPPORTED_NOT_INSTALLED
        ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> ArCoreStatus.SUPPORTED_APK_TOO_OLD
        ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> ArCoreStatus.UNSUPPORTED
        else -> ArCoreStatus.UNKNOWN
    }

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/ar/CapabilityChecker.kt
git commit -m "feat: add CapabilityChecker (ARCore availability + depth probe + report)"
```

---

## Task 10: ReportExporter (write files + share)

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/export/ReportExporter.kt`

No JVM unit test (uses Android `Context`/`FileProvider`); verified on-device.

- [ ] **Step 1: Create `ReportExporter.kt`**

```kotlin
package com.johnymoo.arverify.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.johnymoo.arverify.model.CapabilityReport
import java.io.File

/** Writes the report to the app's external files dir and shares files via the system chooser. */
class ReportExporter(private val context: Context) {

    fun exportsDir(): File =
        File(context.getExternalFilesDir(null), "exports").apply { if (!exists()) mkdirs() }

    fun writeReport(report: CapabilityReport): List<File> {
        val dir = exportsDir()
        val json = File(dir, "compat_report.json").apply { writeText(report.toJson()) }
        val txt = File(dir, "compat_report.txt").apply { writeText(report.toReadableText()) }
        return listOf(json, txt)
    }

    fun share(files: List<File>, title: String) {
        if (files.isEmpty()) return
        val uris = ArrayList<Uri>(files.map {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it)
        })
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/export/ReportExporter.kt
git commit -m "feat: add ReportExporter (write JSON/text + share sheet)"
```

---

## Task 11: BackgroundRenderer (OpenGL camera feed)

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/render/BackgroundRenderer.kt`

No JVM unit test (OpenGL); verified on-device (camera viewfinder appears in Task 12).

- [ ] **Step 1: Create `BackgroundRenderer.kt`**

```kotlin
package com.johnymoo.arverify.render

import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Renders the ARCore camera feed to a full-screen quad using an external OES texture. */
class BackgroundRenderer {

    var textureId = -1
        private set

    private lateinit var quadCoords: FloatBuffer
    private lateinit var quadTexCoords: FloatBuffer
    private var program = 0
    private var positionAttrib = 0
    private var texCoordAttrib = 0

    fun createOnGlThread() {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        val target = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        GLES20.glBindTexture(target, textureId)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        // Full-screen quad in normalized device coordinates (triangle strip order).
        val quad = floatArrayOf(-1f, -1f, +1f, -1f, -1f, +1f, +1f, +1f)
        quadCoords = ByteBuffer.allocateDirect(quad.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()
        quadCoords.put(quad)
        quadCoords.position(0)
        quadTexCoords = ByteBuffer.allocateDirect(quad.size * 4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer()

        val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        GLES20.glUseProgram(program)
        positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")
    }

    fun draw(frame: Frame) {
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quadCoords,
                Coordinates2d.TEXTURE_NORMALIZED, quadTexCoords
            )
        }
        if (frame.timestamp == 0L) return
        quadCoords.position(0)
        quadTexCoords.position(0)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glUseProgram(program)
        GLES20.glVertexAttribPointer(positionAttrib, 2, GLES20.GL_FLOAT, false, 0, quadCoords)
        GLES20.glVertexAttribPointer(texCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, quadTexCoords)
        GLES20.glEnableVertexAttribArray(positionAttrib)
        GLES20.glEnableVertexAttribArray(texCoordAttrib)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(positionAttrib)
        GLES20.glDisableVertexAttribArray(texCoordAttrib)
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        check(status[0] != 0) { "Shader compile failed: " + GLES20.glGetShaderInfoLog(shader) }
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 v_TexCoord;
            uniform samplerExternalOES sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, v_TexCoord);
            }
        """
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/render/BackgroundRenderer.kt
git commit -m "feat: add BackgroundRenderer (GL camera feed)"
```

---

## Task 12: CaptureActivity + layout (AR session + depth/point-cloud capture)

**Files:**
- Create: `app/src/main/res/layout/activity_capture.xml`
- Create: `app/src/main/java/com/johnymoo/arverify/CaptureActivity.kt`

Verified on-device (GL/AR/camera).

- [ ] **Step 1: Create `res/layout/activity_capture.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <android.opengl.GLSurfaceView
        android:id="@+id/surface_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <ImageView
        android:id="@+id/iv_preview"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:background="#CC000000"
        android:scaleType="fitCenter"
        android:visibility="gone"
        android:contentDescription="@string/btn_capture" />

    <TextView
        android:id="@+id/tv_tips"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="top"
        android:background="#80000000"
        android:padding="12dp"
        android:textColor="@color/white"
        android:text="@string/capture_tips" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:orientation="horizontal"
        android:gravity="center"
        android:padding="16dp"
        android:background="#80000000">

        <Button
            android:id="@+id/btn_shutter"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/btn_shutter" />

        <Button
            android:id="@+id/btn_done"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="24dp"
            android:text="@string/btn_done" />
    </LinearLayout>
</FrameLayout>
```

- [ ] **Step 2: Create `CaptureActivity.kt`**

```kotlin
package com.johnymoo.arverify

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.johnymoo.arverify.databinding.ActivityCaptureBinding
import com.johnymoo.arverify.depth.DepthColorizer
import com.johnymoo.arverify.export.PlyWriter
import com.johnymoo.arverify.render.BackgroundRenderer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CaptureActivity : AppCompatActivity(), GLSurfaceView.Renderer {

    private lateinit var binding: ActivityCaptureBinding
    private val background = BackgroundRenderer()
    private var session: Session? = null
    private var installRequested = false

    @Volatile private var captureRequested = false
    private var viewportWidth = 0
    private var viewportHeight = 0
    private var displayRotation = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.surfaceView.preserveEGLContextOnPause = true
        binding.surfaceView.setEGLContextClientVersion(2)
        binding.surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        binding.surfaceView.setRenderer(this)
        binding.surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        binding.btnShutter.setOnClickListener { captureRequested = true }
        binding.btnDone.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (session == null) {
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> { installRequested = true; return }
                    ArCoreApk.InstallStatus.INSTALLED -> {}
                    else -> {}
                }
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED
                ) { toast("需要相机权限"); finish(); return }

                session = Session(this).also { s ->
                    val config = s.config
                    config.depthMode =
                        if (s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) Config.DepthMode.AUTOMATIC
                        else Config.DepthMode.DISABLED
                    config.focusMode = Config.FocusMode.AUTO
                    s.configure(config)
                }
            } catch (e: Exception) {
                toast("无法启动 AR 会话：${e.javaClass.simpleName}"); finish(); return
            }
        }
        try {
            session?.resume()
        } catch (e: CameraNotAvailableException) {
            toast("相机不可用"); session = null; finish(); return
        }
        binding.surfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (session != null) {
            binding.surfaceView.onPause()
            session?.pause()
        }
    }

    override fun onDestroy() {
        session?.close()
        session = null
        super.onDestroy()
    }

    // --- GLSurfaceView.Renderer ---

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        background.createOnGlThread()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        viewportWidth = width
        viewportHeight = height
        @Suppress("DEPRECATION")
        displayRotation = windowManager.defaultDisplay.rotation
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return
        if (background.textureId == -1) return
        s.setCameraTextureName(background.textureId)
        if (viewportWidth > 0) s.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight)
        try {
            val frame = s.update()
            background.draw(frame)
            if (captureRequested && frame.camera.trackingState == TrackingState.TRACKING) {
                captureRequested = false
                doCapture(frame)
            }
        } catch (e: CameraNotAvailableException) {
            runOnUiThread { toast("相机不可用") }
        }
    }

    private fun doCapture(frame: Frame) {
        try {
            val dir = File(getExternalFilesDir(null), "exports").apply { mkdirs() }
            val stamp = ts()
            val saved = mutableListOf<String>()

            try {
                frame.acquireDepthImage16Bits().use { depth ->
                    val bmp = DepthColorizer.colorize(depth)
                    val png = File(dir, "depth_$stamp.png")
                    png.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    saved.add(png.name)
                    runOnUiThread {
                        binding.ivPreview.setImageBitmap(bmp)
                        binding.ivPreview.visibility = View.VISIBLE
                    }
                }
            } catch (e: NotYetAvailableException) {
                runOnUiThread { toast("深度数据未就绪，请对准物体后重试") }
            }

            frame.acquirePointCloud().use { pc ->
                val buf = pc.points
                val arr = FloatArray(buf.remaining())
                buf.get(arr)
                val ply = File(dir, "points_$stamp.ply")
                ply.writeText(PlyWriter.toAsciiPly(arr))
                saved.add(ply.name)
            }

            runOnUiThread { toast("已保存：${saved.joinToString(" , ")}") }
        } catch (e: Exception) {
            runOnUiThread { toast("采集失败：${e.javaClass.simpleName}") }
        }
    }

    private fun ts(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
```

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/activity_capture.xml \
        app/src/main/java/com/johnymoo/arverify/CaptureActivity.kt
git commit -m "feat: add CaptureActivity (AR session + depth/point-cloud capture)"
```

---

## Task 13: MainActivity + layout (wire everything)

**Files:**
- Create: `app/src/main/res/layout/activity_main.xml`
- Create: `app/src/main/java/com/johnymoo/arverify/MainActivity.kt`

- [ ] **Step 1: Create `res/layout/activity_main.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:fillViewport="true">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="20dp">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/title"
            android:textSize="22sp"
            android:textStyle="bold"
            android:paddingBottom="16dp" />

        <Button
            android:id="@+id/btn_check_arcore"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/btn_check_arcore" />

        <Button
            android:id="@+id/btn_check_depth"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/btn_check_depth" />

        <Button
            android:id="@+id/btn_capture"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:enabled="false"
            android:text="@string/btn_capture" />

        <Button
            android:id="@+id/btn_export"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:text="@string/btn_export" />

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:text="@string/section_result"
            android:textStyle="bold"
            android:textSize="16sp" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:background="@color/card_bg"
            android:padding="16dp"
            android:layout_marginTop="8dp">

            <TextView
                android:id="@+id/tv_arcore"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:textSize="16sp" />

            <TextView
                android:id="@+id/tv_depth"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:textSize="16sp" />

            <TextView
                android:id="@+id/tv_route"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:textSize="16sp"
                android:textStyle="bold" />

            <TextView
                android:id="@+id/tv_notes"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="10dp"
                android:textSize="13sp"
                android:textColor="#FF555555" />
        </LinearLayout>
    </LinearLayout>
</ScrollView>
```

- [ ] **Step 2: Create `MainActivity.kt`**

```kotlin
package com.johnymoo.arverify

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.johnymoo.arverify.ar.CapabilityChecker
import com.johnymoo.arverify.databinding.ActivityMainBinding
import com.johnymoo.arverify.export.ReportExporter
import com.johnymoo.arverify.model.CapabilityReport

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var checker: CapabilityChecker
    private lateinit var exporter: ReportExporter
    private var lastReport: CapabilityReport? = null

    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) launchCapture() else toast("需要相机权限才能采集") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        checker = CapabilityChecker(this)
        exporter = ReportExporter(this)

        binding.btnCheckArcore.setOnClickListener { checkArcore() }
        binding.btnCheckDepth.setOnClickListener { refreshReport() }
        binding.btnCapture.setOnClickListener { onCaptureClicked() }
        binding.btnExport.setOnClickListener { onExportClicked() }
    }

    override fun onResume() {
        super.onResume()
        refreshReport()
    }

    private fun checkArcore() {
        when (checker.availability()) {
            ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
            ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> {
                try {
                    checker.requestInstall(this, true) // launches Play dialog; onResume re-runs
                } catch (e: Exception) {
                    toast("无法安装 ARCore：${e.javaClass.simpleName}")
                }
            }
            else -> refreshReport()
        }
    }

    private fun refreshReport() {
        val report = checker.buildReport(this)
        lastReport = report
        render(report)
    }

    private fun render(r: CapabilityReport) {
        val yes = getString(R.string.supported)
        val no = getString(R.string.not_supported)
        binding.tvArcore.text = getString(R.string.status_arcore, if (r.arcoreUsable) yes else no)
        binding.tvDepth.text = getString(R.string.status_depth, if (r.depthAutomaticSupported) yes else no)
        binding.tvRoute.text = getString(R.string.status_route, r.recommendedRoute.labelZh)
        binding.btnCapture.isEnabled = r.arcoreUsable && r.depthAutomaticSupported
        binding.tvNotes.text = r.notes.joinToString("\n")
    }

    private fun onCaptureClicked() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) launchCapture()
        else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun launchCapture() = startActivity(Intent(this, CaptureActivity::class.java))

    private fun onExportClicked() {
        val r = lastReport ?: checker.buildReport(this).also { lastReport = it }
        val files = exporter.writeReport(r)
        exporter.share(files, getString(R.string.btn_export))
        toast("已写入：${files.joinToString(" , ") { it.name }}")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
```

- [ ] **Step 3: Verify the full app builds**

Run: `./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL; APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Run all unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS (RecommendedRoute 3, CapabilityReportJson 3, PlyWriter 3, DepthColorMap 3).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/res/layout/activity_main.xml \
        app/src/main/java/com/johnymoo/arverify/MainActivity.kt
git commit -m "feat: add MainActivity (capability checks, status card, capture/export wiring)"
```

---

## Task 14: README, on-device verification, push

**Files:**
- Create: `README.md`

- [ ] **Step 1: Create `README.md`** (Chinese)

````markdown
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
````

- [ ] **Step 2: On-device verification (manual, on the phone)**

Perform on the connected PLG110 and record results:
1. Launch app → status card shows ARCore/Depth/route (expected on a capable phone: ARCore 支持).
2. Tap **检查 ARCore** → if prompted, install Google Play Services for AR; status updates to 支持.
3. Tap **检查 Depth API** → Depth shows 支持 or 不支持 (this is the verification result).
4. If Depth 支持: **开始采集** is enabled → camera viewfinder appears → aim at a part → **拍摄** → colorized depth preview overlays; toast lists saved `depth_*.png` and `points_*.ply`.
5. Tap **导出结果** → share sheet appears with `compat_report.json` + `.txt`.
6. Confirm files exist under `Android/data/com.johnymoo.arverify/files/exports/`.

Expected: the app never crashes; every branch (supported / not supported) produces a clear on-screen result and an exportable report.

- [ ] **Step 3: Commit README**

```bash
git add README.md
git commit -m "docs: add Chinese README (build, run, usage, export locations)"
```

- [ ] **Step 4: Push to GitHub**

Run:
```bash
git push -u origin main
```
Expected: branch `main` pushed to `git@github.com:johnymoo/Android-ARCore-Prototype.git`.

---

## Self-Review (completed by plan author)

**Spec coverage:**
- ARCore availability + install → Task 9 (`CapabilityChecker`), Task 13 (MainActivity flow). ✓
- Depth API support detection → Task 9 (`probeDepth`). ✓
- Device compatibility report (on-screen + JSON + text) → Tasks 5, 9, 10, 13. ✓
- Recommended route → Task 3. ✓
- Depth capture: colorized 2D depth map → Tasks 7, 8, 12. ✓
- Point cloud `.ply` export → Tasks 6, 12. ✓
- Capture guidance tips → Task 2 (string) + Task 12 (layout). ✓
- AR Optional install setting → Task 2 (manifest). ✓
- China-ROM defensive note → Task 9 (notes). ✓
- Deliverables: Android Studio project + README + push → Tasks 1–14. ✓
- Deferred items (OpenCV, photogrammetry, mesh, measurement, 3D viewer) → intentionally absent. ✓

**Placeholder scan:** No "TBD"/"TODO"/"add error handling" — all code is concrete. The single binary artifact (Gradle wrapper jar) is explicitly generated in Task 1 Step 8. ✓

**Type/name consistency:** `RecommendedRoute.decide(arcoreOk, depthOk)` + `labelZh`; `ArCoreStatus` 5 values mapped in `CapabilityChecker.mapStatus`; `CapabilityReport` fields (`arcoreUsable`, `depthAutomaticSupported`, `recommendedRoute`, `notes`) consistent across `toJson`, `render`, tests; `PlyWriter.toAsciiPly(FloatArray)`, `DepthColorMap.toArgb(mm,min,max)`, `DepthColorizer.colorize(Image)`, `BackgroundRenderer.createOnGlThread()/draw()/textureId`, `ReportExporter.writeReport()/share()` all match their call sites. ViewBinding ids (`btn_check_arcore`→`btnCheckArcore`, `surface_view`→`surfaceView`, `iv_preview`→`ivPreview`, etc.) match the layouts. ✓
