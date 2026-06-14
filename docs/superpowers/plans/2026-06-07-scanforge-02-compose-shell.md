# 拾模 / ScanForge · Plan 2 —— Compose 外壳 · 品牌 · 导航 · 非相机屏

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 引入 Jetpack Compose（深色专业扫描风主题/令牌）、Navigation 与底部三 Tab，重命名品牌为「拾模 / ScanForge」，并用 Compose 实现全部**非相机**屏：主屏、采集库、会话详情、帧查看器、设置、设备诊断。消费 Plan 1 的领域层。

**Architecture:** 单 Activity（`MainActivity` → `setContent`）+ Navigation-Compose 托管非相机屏；底部 Tab：首页 / 采集库 / 设置。ViewModel + StateFlow 承载屏幕状态，可测的过滤逻辑抽成纯函数（JVM 单测）。采集库读 Plan 1 的 `CaptureLibraryRepository`。相机采集页在 Plan 3 实现——本计划主屏入口先桥接到既有 `CaptureWizardActivity`（识别）并为通用采集占位，Plan 3 再切到新采集页。

**Tech Stack:** Kotlin 2.0.21 + Compose（BOM 2024.09.02）+ Navigation-Compose 2.8.3 + lifecycle-viewmodel-compose 2.8.6 + Coil 2.7.0 + coroutines 1.9.0。设计：`docs/superpowers/specs/2026-06-07-scanforge-app-redesign-design.md`（§2 IA、§4 库/查看器、§6 品牌、§7 架构）。UI 原型参考：`docs/superpowers/specs/assets/scanforge-ui-mockups/`（①主屏 ③回看·库）。

**前置：** Plan 1 已合并（`session.*`、`CaptureSlot`、`DepthOverlayRange` 可用）。

## 关键约束

- 本机不能编译；**所有 `./gradlew` 与真机步骤在 Mac dev 机执行**。
- 验证分两类：**[JVM]** 单测（无需真机）；**[真机]** 在 OPPO PLG110 上 build+install+肉眼核对。
- 保留既有 XML Activities（`MainActivity` 改造为 Compose 宿主；`CaptureActivity`/`CaptureWizardActivity`/`ResultActivity`/`MeasurementFormActivity` 暂留，Plan 3/4 处理）。因此 `buildFeatures { viewBinding = true }` 与 `compose = true` 并存。

## 文件结构

**修改：**
- `build.gradle.kts`（根）：加 Compose 编译器插件声明。
- `app/build.gradle.kts`：加插件、`buildFeatures.compose`、Compose/Nav/Coil/coroutines 依赖。
- `app/src/main/res/values/strings.xml`：`app_name` → 拾模，新增导航/屏幕字符串。
- `app/src/main/res/values/colors.xml`、`themes.xml`：深色品牌色与 `Theme.ScanForge`。
- `app/src/main/AndroidManifest.xml`：`MainActivity` 用新主题（仍是 launcher）。
- `app/src/main/java/com/johnymoo/arverify/MainActivity.kt`：改为 Compose 宿主（`setContent { ScanForgeApp() }`）。

**新建（`app/src/main/java/com/johnymoo/arverify/ui/...`）：**
- `ui/theme/Color.kt`、`ui/theme/Type.kt`、`ui/theme/Shape.kt`、`ui/theme/Theme.kt`
- `ui/nav/ScanForgeApp.kt`（NavHost + 底部栏 Scaffold）、`ui/nav/Routes.kt`
- `ui/home/HomeScreen.kt`
- `ui/library/LibraryFiltering.kt`（**纯函数，JVM 可测**）、`ui/library/LibraryViewModel.kt`、`ui/library/LibraryScreen.kt`
- `ui/session/SessionDetailViewModel.kt`、`ui/session/SessionDetailScreen.kt`、`ui/session/FrameViewerScreen.kt`
- `ui/settings/SettingsScreen.kt`、`ui/settings/DiagnosticsScreen.kt`
- `ui/common/SessionPaths.kt`（采集根目录助手）

**新建测试：** `app/src/test/java/com/johnymoo/arverify/LibraryFilteringTest.kt`

---

## Task 1: 接入 Compose 构建配置

**Files:** Modify `build.gradle.kts`, `app/build.gradle.kts`

- [ ] **Step 1: 根 build.gradle.kts 加 Compose 编译器插件**

Replace `build.gradle.kts` content with:

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
```

- [ ] **Step 2: app/build.gradle.kts 加插件、buildFeatures、依赖**

In `app/build.gradle.kts`, change the `plugins {}` block to:

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
```

Change `buildFeatures {}` to:

```kotlin
    buildFeatures {
        viewBinding = true
        compose = true
    }
```

Replace the `dependencies {}` block with:

```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.ar:core:1.54.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.02"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
```

- [ ] **Step 3: [真机] 同步 + 构建确认**

Run (Mac): `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL（Compose 插件与依赖解析通过，既有代码仍编译）。

- [ ] **Step 4: 提交**

```bash
git add build.gradle.kts app/build.gradle.kts
git commit -m "build: add Jetpack Compose, Navigation, Coil, coroutines"
```

---

## Task 2: 品牌资源（拾模 / ScanForge）

**Files:** Modify `res/values/strings.xml`, `res/values/colors.xml`, `res/values/themes.xml`, `AndroidManifest.xml`

- [ ] **Step 1: strings.xml —— 改名 + 新增屏幕字符串**

In `app/src/main/res/values/strings.xml`, change the first two strings and append new ones before `</resources>`:

```xml
    <string name="app_name">拾模</string>
    <string name="brand_en">ScanForge</string>

    <!-- ScanForge navigation / screens -->
    <string name="nav_home">首页</string>
    <string name="nav_library">采集库</string>
    <string name="nav_settings">设置</string>
    <string name="home_recognition_title">识别采集</string>
    <string name="home_recognition_desc">拍积木 → 自动识别 → 参数化 3D 模型</string>
    <string name="home_general_title">通用采集</string>
    <string name="home_general_desc">RGB + 深度 + 点云，本地导出</string>
    <string name="home_recent">最近采集</string>
    <string name="home_recent_all">全部 ›</string>
    <string name="lib_filter_all">全部</string>
    <string name="lib_filter_recognized">已识别</string>
    <string name="lib_filter_pending">待上传</string>
    <string name="lib_empty">还没有采集记录</string>
    <string name="frame_rgb">RGB</string>
    <string name="frame_depth">深度</string>
    <string name="action_preview_model">预览模型</string>
    <string name="action_reupload">重新上传</string>
    <string name="action_delete">删除</string>
    <string name="settings_diagnostics">设备诊断</string>
    <string name="general_coming_soon">通用采集将在下一阶段接入</string>
```

- [ ] **Step 2: colors.xml —— 深色品牌色**

In `app/src/main/res/values/colors.xml`, append before `</resources>`:

```xml
    <color name="sf_bg">#FF0E1320</color>
    <color name="sf_surface">#FF161D2B</color>
    <color name="sf_teal">#FF1ED7B5</color>
    <color name="sf_on_bg">#FFE8EDF5</color>
```

- [ ] **Step 3: themes.xml —— Theme.ScanForge（深色）**

Replace `app/src/main/res/values/themes.xml` content with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.ARCoreVerify" parent="Theme.Material3.DayNight.NoActionBar">
        <item name="colorPrimary">@color/sf_teal</item>
    </style>
    <!-- Compose host theme: dark, edge-to-edge friendly. -->
    <style name="Theme.ScanForge" parent="Theme.Material3.Dark.NoActionBar">
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
        <item name="android:windowBackground">@color/sf_bg</item>
    </style>
</resources>
```

- [ ] **Step 4: Manifest —— MainActivity 用新主题**

In `app/src/main/AndroidManifest.xml`, change the `MainActivity` `<activity>` opening tag to add the theme (keep existing attributes):

```xml
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.ScanForge"
            android:configChanges="orientation|screenSize|screenLayout|keyboard|keyboardHidden|navigation">
```

- [ ] **Step 5: [真机] 验证**

Run (Mac): `./gradlew :app:installDebug`
Expected: 桌面图标名显示「拾模」；点开启动不崩溃（MainActivity 此刻仍是旧 XML，下个 Task 替换）。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values/colors.xml \
        app/src/main/res/values/themes.xml app/src/main/AndroidManifest.xml
git commit -m "feat: rebrand to 拾模/ScanForge (strings, dark theme, colors)"
```

---

## Task 3: 设计令牌与 ScanForgeTheme

**Files:** Create `ui/theme/Color.kt`, `ui/theme/Type.kt`, `ui/theme/Shape.kt`, `ui/theme/Theme.kt`

- [ ] **Step 1: Color.kt**

```kotlin
package com.johnymoo.arverify.ui.theme

import androidx.compose.ui.graphics.Color

val SfTeal = Color(0xFF1ED7B5)
val SfTealOn = Color(0xFF04201A)
val SfBg = Color(0xFF0E1320)
val SfSurface = Color(0xFF161D2B)
val SfSurfaceVariant = Color(0xFF1B2433)
val SfOnBg = Color(0xFFE8EDF5)
val SfMuted = Color(0xFF9FB0C7)
val SfOkBg = Color(0xFF163D2A)
val SfOk = Color(0xFF56D39A)
val SfWaitBg = Color(0xFF3D3416)
val SfWait = Color(0xFFE3C156)
val SfError = Color(0xFFFF6B6B)
```

- [ ] **Step 2: Type.kt 与 Shape.kt**

`ui/theme/Type.kt`:

```kotlin
package com.johnymoo.arverify.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val ScanForgeTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp),
)
```

`ui/theme/Shape.kt`:

```kotlin
package com.johnymoo.arverify.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ScanForgeShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
)
```

- [ ] **Step 3: Theme.kt**

```kotlin
package com.johnymoo.arverify.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ScanForgeColors = darkColorScheme(
    primary = SfTeal,
    onPrimary = SfTealOn,
    background = SfBg,
    onBackground = SfOnBg,
    surface = SfSurface,
    onSurface = SfOnBg,
    surfaceVariant = SfSurfaceVariant,
    onSurfaceVariant = SfMuted,
    error = SfError,
)

@Composable
fun ScanForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ScanForgeColors,
        typography = ScanForgeTypography,
        shapes = ScanForgeShapes,
        content = content,
    )
}
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/theme/
git commit -m "feat: add ScanForge Compose design tokens + theme"
```

---

## Task 4: 采集库过滤（纯函数，JVM 单测）

**Files:** Create `ui/library/LibraryFiltering.kt`, Test `LibraryFilteringTest.kt`

- [ ] **Step 1: [JVM] 写失败测试**

`app/src/test/java/com/johnymoo/arverify/LibraryFilteringTest.kt`:

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.capture.CaptureSlot
import com.johnymoo.arverify.session.CaptureMode
import com.johnymoo.arverify.session.CaptureSession
import com.johnymoo.arverify.session.CapturedFrame
import com.johnymoo.arverify.session.SessionEntry
import com.johnymoo.arverify.session.SessionStatus
import com.johnymoo.arverify.ui.library.LibraryFilter
import com.johnymoo.arverify.ui.library.LibraryFiltering
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LibraryFilteringTest {
    private fun entry(id: String, status: SessionStatus) = SessionEntry(
        dir = File("/tmp/$id"),
        session = CaptureSession(
            partId = id, mode = CaptureMode.RECOGNITION, createdAtEpochMs = 1L,
            deviceModel = "PLG110", status = status,
            frames = listOf(CapturedFrame(CaptureSlot.TOP, "rgb.jpg")),
        ),
    )

    private val all = listOf(
        entry("a", SessionStatus.RECOGNIZED),
        entry("b", SessionStatus.PENDING_UPLOAD),
        entry("c", SessionStatus.NEEDS_MEASUREMENT),
        entry("d", SessionStatus.EXPORTED),
    )

    @Test fun allReturnsEverything() {
        assertEquals(4, LibraryFiltering.apply(all, LibraryFilter.ALL).size)
    }

    @Test fun recognizedFilter() {
        val r = LibraryFiltering.apply(all, LibraryFilter.RECOGNIZED)
        assertEquals(listOf("a"), r.map { it.session.partId })
    }

    @Test fun pendingFilterIncludesNeedsMeasurement() {
        val r = LibraryFiltering.apply(all, LibraryFilter.PENDING).map { it.session.partId }
        assertEquals(listOf("b", "c"), r) // PENDING_UPLOAD + NEEDS_MEASUREMENT are "待上传/待处理"
    }
}
```

- [ ] **Step 2: [JVM] 运行确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.LibraryFilteringTest"`
Expected: FAIL —— `Unresolved reference: LibraryFiltering`。

- [ ] **Step 3: 实现**

`app/src/main/java/com/johnymoo/arverify/ui/library/LibraryFiltering.kt`:

```kotlin
package com.johnymoo.arverify.ui.library

import com.johnymoo.arverify.session.SessionEntry
import com.johnymoo.arverify.session.SessionStatus

/** Library filter tabs (spec §4). */
enum class LibraryFilter { ALL, RECOGNIZED, PENDING }

/** Pure filtering used by LibraryViewModel — kept separate so it is JVM-unit-testable. */
object LibraryFiltering {
    fun apply(entries: List<SessionEntry>, filter: LibraryFilter): List<SessionEntry> = when (filter) {
        LibraryFilter.ALL -> entries
        LibraryFilter.RECOGNIZED -> entries.filter { it.session.status == SessionStatus.RECOGNIZED }
        LibraryFilter.PENDING -> entries.filter {
            it.session.status == SessionStatus.PENDING_UPLOAD ||
                it.session.status == SessionStatus.NEEDS_MEASUREMENT
        }
    }
}
```

- [ ] **Step 4: [JVM] 运行确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.LibraryFilteringTest"`
Expected: PASS（3 tests）。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/library/LibraryFiltering.kt \
        app/src/test/java/com/johnymoo/arverify/LibraryFilteringTest.kt
git commit -m "feat: add library filtering (pure, JVM-tested)"
```

---

## Task 5: 采集根目录助手 + ViewModels

**Files:** Create `ui/common/SessionPaths.kt`, `ui/library/LibraryViewModel.kt`, `ui/session/SessionDetailViewModel.kt`

- [ ] **Step 1: SessionPaths.kt**

```kotlin
package com.johnymoo.arverify.ui.common

import android.content.Context
import java.io.File

/** Single source of truth for the on-disk capture sessions root (spec §9). */
object SessionPaths {
    fun captureRoot(context: Context): File =
        File(context.getExternalFilesDir(null), "captures").apply { mkdirs() }
}
```

- [ ] **Step 2: LibraryViewModel.kt**

```kotlin
package com.johnymoo.arverify.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.session.SessionEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val loading: Boolean = true,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val all: List<SessionEntry> = emptyList(),
) {
    val visible: List<SessionEntry> get() = LibraryFiltering.apply(all, filter)
}

class LibraryViewModel(private val repo: CaptureLibraryRepository) : ViewModel() {
    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    fun refresh() {
        _state.value = _state.value.copy(loading = true)
        viewModelScope.launch {
            val entries = withContext(Dispatchers.IO) { repo.listSessions() }
            _state.value = _state.value.copy(loading = false, all = entries)
        }
    }

    fun setFilter(f: LibraryFilter) { _state.value = _state.value.copy(filter = f) }

    fun delete(entry: SessionEntry) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(entry) }
            refresh()
        }
    }
}
```

- [ ] **Step 3: SessionDetailViewModel.kt**

```kotlin
package com.johnymoo.arverify.ui.session

import androidx.lifecycle.ViewModel
import com.johnymoo.arverify.session.CaptureSession
import com.johnymoo.arverify.session.SessionManifestCodec
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.session.SessionEntry
import java.io.File

class SessionDetailViewModel(private val repo: CaptureLibraryRepository) : ViewModel() {
    /** Reads one session from its directory; null if the manifest is missing/invalid. */
    fun load(dir: File): CaptureSession? {
        val mf = File(dir, CaptureLibraryRepository.MANIFEST)
        if (!mf.isFile) return null
        return SessionManifestCodec.fromJson(mf.readText())
    }

    fun delete(dir: File, session: CaptureSession) = repo.delete(SessionEntry(dir, session))
}
```

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/common/SessionPaths.kt \
        app/src/main/java/com/johnymoo/arverify/ui/library/LibraryViewModel.kt \
        app/src/main/java/com/johnymoo/arverify/ui/session/SessionDetailViewModel.kt
git commit -m "feat: add session paths + library/detail view models"
```

---

## Task 6: 导航与底部栏（ScanForgeApp）+ MainActivity 宿主

**Files:** Create `ui/nav/Routes.kt`, `ui/nav/ScanForgeApp.kt`; Modify `MainActivity.kt`

- [ ] **Step 1: Routes.kt**

```kotlin
package com.johnymoo.arverify.ui.nav

import java.net.URLEncoder

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"
    const val SESSION_DETAIL = "session/{dir}"
    const val FRAME_VIEWER = "frame/{dir}/{index}"

    fun sessionDetail(dirPath: String) = "session/${enc(dirPath)}"
    fun frameViewer(dirPath: String, index: Int) = "frame/${enc(dirPath)}/$index"
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
```

- [ ] **Step 2: ScanForgeApp.kt（Scaffold + NavHost + 底部三 Tab）**

```kotlin
package com.johnymoo.arverify.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.johnymoo.arverify.R
import com.johnymoo.arverify.ui.home.HomeScreen
import com.johnymoo.arverify.ui.library.LibraryScreen
import com.johnymoo.arverify.ui.session.FrameViewerScreen
import com.johnymoo.arverify.ui.session.SessionDetailScreen
import com.johnymoo.arverify.ui.settings.DiagnosticsScreen
import com.johnymoo.arverify.ui.settings.SettingsScreen
import com.johnymoo.arverify.ui.theme.ScanForgeTheme
import java.net.URLDecoder

private data class Tab(val route: String, val labelRes: Int, val icon: ImageVector)

@Composable
fun ScanForgeApp() {
    ScanForgeTheme {
        val nav = rememberNavController()
        val tabs = listOf(
            Tab(Routes.HOME, R.string.nav_home, Icons.Outlined.Home),
            Tab(Routes.LIBRARY, R.string.nav_library, Icons.Outlined.GridView),
            Tab(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings),
        )
        val backStack by nav.currentBackStackEntryAsState()
        val current = backStack?.destination?.route

        Scaffold(
            bottomBar = {
                if (current in tabs.map { it.route }) {
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = current == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = null) },
                                label = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }
                }
            }
        ) { pad ->
            NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(pad)) {
                composable(Routes.HOME) { HomeScreen(nav) }
                composable(Routes.LIBRARY) { LibraryScreen(nav) }
                composable(Routes.SETTINGS) { SettingsScreen(nav) }
                composable(Routes.DIAGNOSTICS) { DiagnosticsScreen() }
                composable(Routes.SESSION_DETAIL) { entry ->
                    val dir = URLDecoder.decode(entry.arguments?.getString("dir").orEmpty(), "UTF-8")
                    SessionDetailScreen(nav, dir)
                }
                composable(Routes.FRAME_VIEWER) { entry ->
                    val dir = URLDecoder.decode(entry.arguments?.getString("dir").orEmpty(), "UTF-8")
                    val index = entry.arguments?.getString("index")?.toIntOrNull() ?: 0
                    FrameViewerScreen(dir, index)
                }
            }
        }
    }
}
```

- [ ] **Step 3: MainActivity.kt 改为 Compose 宿主**

Replace `app/src/main/java/com/johnymoo/arverify/MainActivity.kt` content with:

```kotlin
package com.johnymoo.arverify

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.johnymoo.arverify.ui.nav.ScanForgeApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ScanForgeApp() }
    }
}
```

> 注：原 `MainActivity` 的能力检测逻辑迁移到 `DiagnosticsScreen`（Task 8）。`CameraPermissionPurpose` 等旧符号随旧 MainActivity 一并移除；旧 `activity_main.xml` 不再使用（可保留或删除，留作 Plan 清理）。

- [ ] **Step 4: [真机] 验证**

Run (Mac): `./gradlew :app:installDebug`
Expected: 启动进入深色主屏（占位内容随后续 Task 充实）；底部三 Tab 可切换不崩溃。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/nav/ app/src/main/java/com/johnymoo/arverify/MainActivity.kt
git commit -m "feat: Compose single-activity nav shell with bottom tabs"
```

---

## Task 7: HomeScreen（双轨入口 + 最近采集）

**Files:** Create `ui/home/HomeScreen.kt`

- [ ] **Step 1: 实现**

```kotlin
package com.johnymoo.arverify.ui.home

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.navigation.NavController
import com.johnymoo.arverify.R
import com.johnymoo.arverify.capture.CaptureWizardActivity
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.session.SessionEntry
import com.johnymoo.arverify.ui.common.SessionPaths
import com.johnymoo.arverify.ui.library.SessionRow
import com.johnymoo.arverify.ui.nav.Routes
import com.johnymoo.arverify.ui.theme.SfTeal

@Composable
fun HomeScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = remember { CaptureLibraryRepository(SessionPaths.captureRoot(context)) }
    var recent by remember { mutableStateOf(emptyList<SessionEntry>()) }
    LifecycleResumeEffect(Unit) { recent = repo.listSessions().take(3); onPauseOrDispose {} }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                Text("  " + stringResource(R.string.brand_en),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            EntryCard(
                title = stringResource(R.string.home_recognition_title),
                desc = stringResource(R.string.home_recognition_desc),
                primary = true,
            ) {
                // Plan 3 replaces this with the new ScanCaptureActivity (mode=RECOGNITION).
                context.startActivity(Intent(context, CaptureWizardActivity::class.java))
            }
        }
        item {
            EntryCard(
                title = stringResource(R.string.home_general_title),
                desc = stringResource(R.string.home_general_desc),
                primary = false,
            ) {
                Toast.makeText(context, R.string.general_coming_soon, Toast.LENGTH_SHORT).show()
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.home_recent), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.home_recent_all), color = SfTeal,
                    modifier = Modifier.clickable { nav.navigate(Routes.LIBRARY) })
            }
        }
        items(recent) { entry ->
            SessionRow(entry) { nav.navigate(Routes.sessionDetail(entry.dir.absolutePath)) }
        }
    }
}

@Composable
private fun EntryCard(title: String, desc: String, primary: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (primary) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                color = if (primary) SfTeal else MaterialTheme.colorScheme.onSurface)
            Text(desc, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
```

> 依赖 `ui/library/SessionRow.kt`（Task 8 定义的可复用列表行）。

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/home/HomeScreen.kt
git commit -m "feat: add Home screen (dual-track entries + recent)"
```

---

## Task 8: LibraryScreen + SessionRow

**Files:** Create `ui/library/LibraryScreen.kt`

- [ ] **Step 1: 实现（含可复用 SessionRow + 状态徽标）**

```kotlin
package com.johnymoo.arverify.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import com.johnymoo.arverify.R
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.session.SessionEntry
import com.johnymoo.arverify.session.SessionStatus
import com.johnymoo.arverify.ui.common.SessionPaths
import com.johnymoo.arverify.ui.nav.Routes
import com.johnymoo.arverify.ui.theme.SfOk
import com.johnymoo.arverify.ui.theme.SfOkBg
import com.johnymoo.arverify.ui.theme.SfWait
import com.johnymoo.arverify.ui.theme.SfWaitBg

@Composable
fun LibraryScreen(nav: NavController) {
    val context = LocalContext.current
    val repo = remember { CaptureLibraryRepository(SessionPaths.captureRoot(context)) }
    val vm: LibraryViewModel = viewModel(factory = viewModelFactory {
        initializer { LibraryViewModel(repo) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) { vm.refresh(); onPauseOrDispose {} }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.nav_library), style = MaterialTheme.typography.titleLarge)
        Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterTab(stringResource(R.string.lib_filter_all), state.filter == LibraryFilter.ALL) { vm.setFilter(LibraryFilter.ALL) }
            FilterTab(stringResource(R.string.lib_filter_recognized), state.filter == LibraryFilter.RECOGNIZED) { vm.setFilter(LibraryFilter.RECOGNIZED) }
            FilterTab(stringResource(R.string.lib_filter_pending), state.filter == LibraryFilter.PENDING) { vm.setFilter(LibraryFilter.PENDING) }
        }
        if (!state.loading && state.visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.lib_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(state.visible) { entry ->
                    SessionRow(entry) { nav.navigate(Routes.sessionDetail(entry.dir.absolutePath)) }
                }
            }
        }
    }
}

@Composable
private fun FilterTab(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}

@Composable
fun SessionRow(entry: SessionEntry, onClick: () -> Unit) {
    val s = entry.session
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("${s.partId} · ${s.frames.size} 帧", style = MaterialTheme.typography.bodyMedium)
            Text(s.deviceModel, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        StatusBadge(s.status)
    }
}

@Composable
private fun StatusBadge(status: SessionStatus) {
    val (text, bg, fg) = when (status) {
        SessionStatus.RECOGNIZED -> Triple("已识别", SfOkBg, SfOk)
        SessionStatus.EXPORTED -> Triple("已导出", SfOkBg, SfOk)
        SessionStatus.PENDING_UPLOAD -> Triple("待上传", SfWaitBg, SfWait)
        SessionStatus.NEEDS_MEASUREMENT -> Triple("待测量", SfWaitBg, SfWait)
        SessionStatus.FAILED -> Triple("失败", SfWaitBg, SfWait)
    }
    androidx.compose.material3.Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Text(text, color = fg, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
    }
}
```

- [ ] **Step 2: [真机] 验证**

Run (Mac): `./gradlew :app:installDebug`；用既有 `CaptureWizardActivity` 跑一次采集留存（若有），进采集库 Tab 应看到会话行 + 状态徽标；筛选切换正常；空态显示「还没有采集记录」。

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/library/LibraryScreen.kt
git commit -m "feat: add Library screen (filters, session rows, empty state)"
```

---

## Task 9: SessionDetailScreen（逐帧网格 + 操作）

**Files:** Create `ui/session/SessionDetailScreen.kt`

- [ ] **Step 1: 实现**

```kotlin
package com.johnymoo.arverify.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.johnymoo.arverify.R
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.session.CaptureMode
import com.johnymoo.arverify.ui.common.SessionPaths
import com.johnymoo.arverify.ui.nav.Routes
import java.io.File

@Composable
fun SessionDetailScreen(nav: NavController, dirPath: String) {
    val context = LocalContext.current
    val repo = remember { CaptureLibraryRepository(SessionPaths.captureRoot(context)) }
    val vm: SessionDetailViewModel = viewModel(factory = viewModelFactory { initializer { SessionDetailViewModel(repo) } })
    val dir = remember(dirPath) { File(dirPath) }
    val session = remember(dirPath) { vm.load(dir) }

    if (session == null) {
        Text("会话已删除或损坏", Modifier.padding(20.dp)); return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        val r = session.recognized
        Text(
            r?.let { "${it.system ?: "-"} · ${it.kind ?: "-"} · ${it.unitsX ?: "-"}×${it.unitsY ?: "-"}" }
                ?: session.partId,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            r?.let { "pitch ${it.pitchMm ?: "-"}mm · 置信度 ${it.confidence ?: "-"}" } ?: "通用采集 · ${session.frames.size} 帧",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.weight(1f),
        ) {
            itemsIndexed(session.frames) { index, frame ->
                AsyncImage(
                    model = File(dir, frame.rgbFileName),
                    contentDescription = frame.slot.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.2f)
                        .clickable { nav.navigate(Routes.frameViewer(dirPath, index)) },
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (session.mode == CaptureMode.RECOGNITION) {
                Button(onClick = { /* Plan 4: re-upload via CaptureUploader */ }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_preview_model))
                }
            }
            OutlinedButton(onClick = {
                vm.delete(dir, session)
                nav.popBackStack()
            }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.action_delete))
            }
        }
    }
}
```

> 「预览模型 / 重新上传」按钮的真实行为在 Plan 4 接入（复用 `CaptureUploader` + `ResultActivity`/Compose 结果屏）；本计划先占位为可点击但不触发网络（注释标明）。

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/session/SessionDetailScreen.kt
git commit -m "feat: add Session detail (frame grid + actions)"
```

---

## Task 10: FrameViewerScreen（RGB / 深度 切换）

**Files:** Create `ui/session/FrameViewerScreen.kt`

- [ ] **Step 1: 实现**

```kotlin
package com.johnymoo.arverify.ui.session

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.johnymoo.arverify.R
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.ui.common.SessionPaths
import java.io.File

@Composable
fun FrameViewerScreen(dirPath: String, index: Int) {
    val context = LocalContext.current
    val repo = remember { CaptureLibraryRepository(SessionPaths.captureRoot(context)) }
    val vm = remember { SessionDetailViewModel(repo) }
    val dir = remember(dirPath) { File(dirPath) }
    val session = remember(dirPath) { vm.load(dir) } ?: return
    val frame = session.frames.getOrNull(index) ?: return

    var showDepth by remember { mutableStateOf(false) }
    val canDepth = frame.hasDepth

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !showDepth, onClick = { showDepth = false },
                shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text(stringResource(R.string.frame_rgb)) }
            SegmentedButton(selected = showDepth, enabled = canDepth, onClick = { showDepth = true },
                shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.frame_depth)) }
        }
        Box(Modifier.fillMaxSize().padding(top = 10.dp), contentAlignment = Alignment.Center) {
            val file = if (showDepth && frame.depthFileName != null) File(dir, frame.depthFileName!!) else File(dir, frame.rgbFileName)
            AsyncImage(model = file, contentDescription = frame.slot.name,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Text(
                "第 ${index + 1}/${session.frames.size} 帧 · ${frame.slot.name}" +
                    (frame.distanceM?.let { " · ${"%.2f".format(it)}m" } ?: ""),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            )
        }
    }
}
```

> 注：深度帧文件为 16-bit 灰度 PNG（`Depth16PngWriter` 产物），Coil 直接显示为灰度；彩色热力可在 Plan 3/后续用 `DepthColorizer` 预渲染一张彩色 PNG 存入会话目录，再在此显示（本计划先显示原始深度 PNG）。

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/session/FrameViewerScreen.kt
git commit -m "feat: add Frame viewer (RGB/depth toggle)"
```

---

## Task 11: SettingsScreen + DiagnosticsScreen

**Files:** Create `ui/settings/SettingsScreen.kt`, `ui/settings/DiagnosticsScreen.kt`

- [ ] **Step 1: SettingsScreen（服务器地址 + 调试开关 + 进入诊断）**

```kotlin
package com.johnymoo.arverify.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.johnymoo.arverify.R
import com.johnymoo.arverify.config.AppPrefs
import com.johnymoo.arverify.ui.nav.Routes

@Composable
fun SettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val initial = remember { prefs.load() }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var debugGallery by remember { mutableStateOf(initial.saveDebugRgbToGallery) }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = baseUrl, onValueChange = { baseUrl = it },
            label = { Text("服务器地址") }, singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("调试：保存原图到相册", modifier = Modifier.weight(1f))
            Switch(checked = debugGallery, onCheckedChange = { debugGallery = it })
        }
        Button(onClick = {
            prefs.setBaseUrl(baseUrl.trim())
            prefs.setSaveDebugRgbToGallery(debugGallery)
        }, modifier = Modifier.padding(top = 12.dp)) { Text("保存") }

        Text(stringResource(R.string.settings_diagnostics), color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 24.dp)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        )
        Button(onClick = { nav.navigate(Routes.DIAGNOSTICS) }) { Text(stringResource(R.string.settings_diagnostics)) }
    }
}
```

- [ ] **Step 2: DiagnosticsScreen（复用 CapabilityChecker）**

```kotlin
package com.johnymoo.arverify.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.johnymoo.arverify.ar.CapabilityChecker

@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    val report = remember { CapabilityChecker(context).buildReport(context) }
    val yes = "支持"; val no = "不支持"
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("设备诊断", style = MaterialTheme.typography.titleLarge)
        Text("ARCore：${if (report.arcoreUsable) yes else no}", modifier = Modifier.padding(top = 12.dp))
        Text("Depth：${if (report.depthAutomaticSupported) yes else no}", modifier = Modifier.padding(top = 6.dp))
        Text("推荐路线：${report.recommendedRoute.labelZh}", style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 6.dp))
        Text(report.notes.joinToString("\n"), color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp))
    }
}
```

> `CapabilityChecker.buildReport(context)` 与 `CapabilityReport.{arcoreUsable, depthAutomaticSupported, recommendedRoute.labelZh, notes}` 为既有 API（原 `MainActivity.render` 使用）。

- [ ] **Step 3: [真机] 验证**

Run (Mac): `./gradlew :app:installDebug`；设置页可改服务器地址并保存（重进生效）；诊断页显示 ARCore/Depth/推荐路线（PLG110 应为 支持/支持）。

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/settings/
git commit -m "feat: add Settings + Diagnostics screens"
```

---

## 收尾：回归 + 真机走查

- [ ] **Step 1: [JVM] 单测回归**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS（含 Plan 1 全部 + 新增 `LibraryFilteringTest`）。

- [ ] **Step 2: [真机] 走查清单**

Run (Mac): `./gradlew :app:installDebug`，逐项核对（对照原型 `assets/scanforge-ui-mockups/`）：
- 启动深色主屏，双轨入口卡（识别=青色主、通用=次），点「识别采集」进入既有采集向导。
- 底部三 Tab 切换；采集库列表 + 筛选 + 空态；点会话进详情；详情帧网格点开帧查看器，RGB/深度切换。
- 设置改地址保存；诊断页显示能力结论。
- 状态栏/导航栏不遮挡（edge-to-edge）。

- [ ] **Step 3: 提交（如有走查微调）**

```bash
git add -A && git commit -m "chore: Plan 2 device walkthrough fixes"
```

---

## 自检

- **spec 覆盖**：§2 IA/导航（Task 6）、§4 库/详情/帧查看器（Task 8–10）、§6 品牌（Task 2）、§7 Compose 单 Activity + ViewModel（Task 3–6）、设置/诊断（Task 11，能力检测降级）。采集页（§3）与上传/导出/结果（§5/§11）属 Plan 3/4。
- **占位符**：HomeScreen 通用采集与 SessionDetail「预览模型/重新上传」明确标注为 Plan 3/4 接入的桥接占位（非遗漏，有注释与计划归属）；其余均为完整可编译代码。
- **类型一致性**：消费 Plan 1 的 `CaptureLibraryRepository.{listSessions,delete,MANIFEST}`、`SessionEntry(dir,session)`、`SessionManifestCodec.fromJson`、`CaptureSession/CapturedFrame/SessionStatus/CaptureMode`；`LibraryFilter{ALL,RECOGNIZED,PENDING}`/`LibraryFiltering.apply` 前后一致；`Routes.{sessionDetail,frameViewer}` 编码与 NavHost 解码对应；`SessionRow` 在 Home 与 Library 共用同一定义（library 包）。
