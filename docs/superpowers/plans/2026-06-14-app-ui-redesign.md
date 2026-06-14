# App-wide UI 重设计精修 Implementation Plan (Plan 2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the three app-wide UI problems — status-bar overlap, missing back buttons, low 质感 — by adding a centralized inset-aware top bar + a shared component layer (style A "精致扫描"), applied to every screen.

**Architecture:** A single route-aware `TopAppBar` in `ScanForgeApp` owns top+bottom insets for all Navigation-Compose screens (so content never collides with the status bar and sub-screens get a back arrow). Standalone Activities (`ResultActivity` ported to Compose, the already-done `MeasurementWizardActivity`) use the shared `ScreenScaffold`. The immersive camera overlay keeps its own chrome but gains inset padding. Per-screen styling is unified through new components (`StatusPill`, `SectionCard`, `SettingRow`, `EmptyState`).

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2024.09.02, Material3), Navigation-Compose, Coil, WebView via AndroidView.

**Depends on:** Plan 1 (`2026-06-14-needs-measurement-fallback.md`) — `ui/components/ScreenScaffold.kt` and the `SfBorder`/`SfDivider` tokens already exist.

---

## ⚠️ Toolchain reality (read first)

Same as Plan 1: this dev machine has **Java 11, no Android SDK, gradle won't run** — you cannot compile or run anything here. Run every `./gradlew …` on a JDK 17 + Android SDK machine. All screens are Compose → **device-verified**; the only unit-testable unit in this plan is the pure route→chrome mapping (Task 2). Mockup proxy for all screens: `docs/superpowers/specs/assets/needs-measurement-ui-mockups/screen-set.html` (style A) and `design-language.html`.

**Unit-test run command:** `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.<TestClass>"`

---

## File Structure

**Create:**
- `app/src/main/java/com/johnymoo/arverify/ui/components/UiComponents.kt` — `StatusPill`, `SectionCard`, `SettingRow`, `EmptyState`.
- `app/src/main/java/com/johnymoo/arverify/ui/nav/ScreenChrome.kt` — pure `topBarFor(route)` mapping (title + showBack).
- `app/src/test/java/com/johnymoo/arverify/ScreenChromeTest.kt`.

**Modify:**
- `ui/nav/ScanForgeApp.kt` — add centralized route-aware `TopAppBar`.
- `ui/home/HomeScreen.kt`, `ui/library/LibraryScreen.kt`, `ui/session/SessionDetailScreen.kt`, `ui/settings/SettingsScreen.kt`, `ui/settings/DiagnosticsScreen.kt` — drop pseudo-titles, apply components.
- `capture/CaptureScreen.kt` — status-bar / nav-bar inset padding.
- `result/ResultActivity.kt` — port to Compose (`ScreenScaffold` + `AndroidView` WebView).
- `res/values/strings.xml` — add 3 screen-title strings.

**Delete:**
- `app/src/main/res/layout/activity_result.xml` (replaced by Compose).

**No change needed (benefit automatically from Task 3's app bar):** `ui/session/FrameViewerScreen.kt` (gains title + back from the centralized bar).

---

## Task 1: Shared components

Compose; **device-verified**.

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/ui/components/UiComponents.kt`

- [ ] **Step 1: Write `UiComponents.kt`**

```kotlin
package com.johnymoo.arverify.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.johnymoo.arverify.ui.theme.SfBorder
import com.johnymoo.arverify.ui.theme.SfDivider
import com.johnymoo.arverify.ui.theme.SfMuted

/** Small status chip (已识别 / 待测量 / ✓ 支持 …). */
@Composable
fun StatusPill(text: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Surface(color = background, shape = MaterialTheme.shapes.small, modifier = modifier) {
        Text(
            text, color = foreground, fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Elevated content card: solid surface + 1px border + rounded corners (style A). */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, SfBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), content = content)
    }
}

/** A settings row: label on the left, trailing content on the right, optional click + hairline divider. */
@Composable
fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    showDivider: Boolean = true,
    trailing: @Composable () -> Unit = {},
) {
    Column {
        Row(
            modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            trailing()
        }
        if (showDivider) {
            Surface(color = SfDivider, modifier = Modifier.fillMaxWidth().padding(start = 0.dp)) {
                Box(Modifier.fillMaxWidth().padding(top = 0.5.dp)) {}
            }
        }
    }
}

/** Centered empty-state message. */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = SfMuted, style = MaterialTheme.typography.bodyMedium)
    }
}
```

- [ ] **Step 2: Device verification** — covered by later screens; nothing to view standalone.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/components/UiComponents.kt
git commit -m "feat: add shared UI components (StatusPill/SectionCard/SettingRow/EmptyState)"
```

---

## Task 2: Route→chrome mapping (pure, JVM-tested) + strings

**Files:**
- Create: `app/src/main/java/com/johnymoo/arverify/ui/nav/ScreenChrome.kt`
- Test: `app/src/test/java/com/johnymoo/arverify/ScreenChromeTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the 3 screen-title strings** — in `strings.xml`, add near the other screen strings:

```xml
    <string name="screen_session_detail">会话详情</string>
    <string name="screen_frame_viewer">帧查看器</string>
    <string name="screen_result">识别结果</string>
```

- [ ] **Step 2: Write the failing test**

```kotlin
package com.johnymoo.arverify

import com.johnymoo.arverify.R
import com.johnymoo.arverify.ui.nav.Routes
import com.johnymoo.arverify.ui.nav.topBarFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenChromeTest {
    @Test fun tabsHaveNoBack() {
        assertFalse(topBarFor(Routes.HOME)!!.showBack)
        assertFalse(topBarFor(Routes.LIBRARY)!!.showBack)
        assertFalse(topBarFor(Routes.SETTINGS)!!.showBack)
    }

    @Test fun subScreensShowBack() {
        assertTrue(topBarFor(Routes.DIAGNOSTICS)!!.showBack)
        assertTrue(topBarFor(Routes.SESSION_DETAIL)!!.showBack)
        assertTrue(topBarFor(Routes.FRAME_VIEWER)!!.showBack)
    }

    @Test fun titlesMapToResources() {
        assertEquals(R.string.nav_library, topBarFor(Routes.LIBRARY)!!.titleRes)
        assertEquals(R.string.screen_session_detail, topBarFor(Routes.SESSION_DETAIL)!!.titleRes)
    }

    @Test fun unknownRouteIsNull() {
        assertNull(topBarFor("nope"))
        assertNull(topBarFor(null))
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.ScreenChromeTest"`
Expected: FAIL — `topBarFor` / `ScreenChrome` not defined.

- [ ] **Step 4: Write `ScreenChrome.kt`**

```kotlin
package com.johnymoo.arverify.ui.nav

import androidx.annotation.StringRes
import com.johnymoo.arverify.R

/** Top-bar descriptor for a NavHost route. */
data class ScreenChrome(@StringRes val titleRes: Int, val showBack: Boolean)

/**
 * Pure route -> top-bar mapping. Tab routes show a title only; sub-screens show
 * title + back. Returns null for routes that render their own chrome (none here)
 * or unknown routes. Matched against NavDestination.route (the pattern, e.g.
 * "session/{dir}"), so the encoded argument value is irrelevant.
 */
fun topBarFor(route: String?): ScreenChrome? = when (route) {
    Routes.HOME -> ScreenChrome(R.string.app_name, showBack = false)
    Routes.LIBRARY -> ScreenChrome(R.string.nav_library, showBack = false)
    Routes.SETTINGS -> ScreenChrome(R.string.nav_settings, showBack = false)
    Routes.DIAGNOSTICS -> ScreenChrome(R.string.settings_diagnostics, showBack = true)
    Routes.SESSION_DETAIL -> ScreenChrome(R.string.screen_session_detail, showBack = true)
    Routes.FRAME_VIEWER -> ScreenChrome(R.string.screen_frame_viewer, showBack = true)
    else -> null
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "com.johnymoo.arverify.ScreenChromeTest"`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/nav/ScreenChrome.kt \
        app/src/test/java/com/johnymoo/arverify/ScreenChromeTest.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat: add route->top-bar chrome mapping (+ screen title strings)"
```

---

## Task 3: Centralized TopAppBar in `ScanForgeApp`

Compose; **device-verified**. This is the core fix for status-bar overlap + back buttons on NavHost screens.

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/ui/nav/ScanForgeApp.kt`

- [ ] **Step 1: Replace `ScanForgeApp.kt` with the version below**

```kotlin
package com.johnymoo.arverify.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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

@OptIn(ExperimentalMaterial3Api::class)
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
        val chrome = topBarFor(current)

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                if (chrome != null) {
                    TopAppBar(
                        title = { Text(stringResource(chrome.titleRes), style = MaterialTheme.typography.titleMedium) },
                        navigationIcon = {
                            if (chrome.showBack) {
                                IconButton(onClick = { nav.popBackStack() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            },
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
            },
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

- [ ] **Step 2: Device verification** — every NavHost screen now shows a top bar below the status bar; SessionDetail / FrameViewer / Diagnostics show a back arrow that pops. Verified in Task 12.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/nav/ScanForgeApp.kt
git commit -m "feat: centralized route-aware top bar (fixes status-bar overlap + back nav)"
```

---

## Task 4: Home — drop pseudo-title, polish entries

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/ui/home/HomeScreen.kt`

- [ ] **Step 1: Remove the title header item** — delete this `item { … }` block (the app bar now shows "拾模"):

```kotlin
        item {
            Row {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge)
                Text("  " + stringResource(R.string.brand_en),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
```

- [ ] **Step 2: Give the primary entry card a teal border accent** — in `EntryCard`, replace the `Card(...)` call with a bordered surface so the primary track reads as the hero (style A). Change the `Card` composable body to:

```kotlin
@Composable
private fun EntryCard(title: String, desc: String, primary: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (primary) SfTeal else com.johnymoo.arverify.ui.theme.SfBorder,
        ),
        modifier = Modifier.fillMaxWidth(),
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

Remove now-unused imports (`Card`, `CardDefaults`, `clickable` if no longer used) — the build will flag unused imports as warnings, not errors; clean them on device.

- [ ] **Step 3: Device verification** — Task 12.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/home/HomeScreen.kt
git commit -m "feat: home polish (drop pseudo-title, teal-accent primary entry)"
```

---

## Task 5: Library — drop pseudo-title, shared StatusPill, EmptyState

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/ui/library/LibraryScreen.kt`

- [ ] **Step 1: Remove the title line** — delete:

```kotlin
        Text(stringResource(R.string.nav_library), style = MaterialTheme.typography.titleLarge)
```

- [ ] **Step 2: Use the shared `EmptyState`** — replace the empty `Box { Text(...) }` block:

```kotlin
        if (!state.loading && state.visible.isEmpty()) {
            EmptyState(stringResource(R.string.lib_empty))
        } else {
```

and add import `import com.johnymoo.arverify.ui.components.EmptyState`.

- [ ] **Step 3: Replace the local `StatusBadge` with the shared `StatusPill`** — delete the private `StatusBadge` composable and change `SessionRow` to call:

```kotlin
        val (text, bg, fg) = statusColors(s.status)
        StatusPill(text, bg, fg)
```

Add a small mapping helper at file scope (keeps the color choices in one place):

```kotlin
@Composable
private fun statusColors(status: SessionStatus): Triple<String, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> =
    when (status) {
        SessionStatus.RECOGNIZED -> Triple("已识别", SfOkBg, SfOk)
        SessionStatus.EXPORTED -> Triple("已导出", SfOkBg, SfOk)
        SessionStatus.PENDING_UPLOAD -> Triple("待上传", SfWaitBg, SfWait)
        SessionStatus.NEEDS_MEASUREMENT -> Triple("待测量", SfWaitBg, SfWait)
        SessionStatus.FAILED -> Triple("失败", SfWaitBg, SfWait)
    }
```

Add import `import com.johnymoo.arverify.ui.components.StatusPill`. Remove the now-unused `FontWeight` import.

- [ ] **Step 4: Device verification** — Task 12.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/library/LibraryScreen.kt
git commit -m "feat: library polish (EmptyState + shared StatusPill, drop pseudo-title)"
```

---

## Task 6: Session Detail — conclusion card + StatusPill

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/ui/session/SessionDetailScreen.kt`

- [ ] **Step 1: Wrap the header conclusion in a `SectionCard` with a status pill** — replace the two header `Text(...)` calls (the `r?.let { … } ?: session.partId` title and the pitch/confidence subtitle) with:

```kotlin
        val r = session.recognized
        SectionCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    r?.let { "${it.system ?: "-"} · ${it.kind ?: "-"} · ${it.unitsX ?: "-"}×${it.unitsY ?: "-"}" }
                        ?: session.partId,
                    style = MaterialTheme.typography.titleMedium,
                )
                val (t, bg, fg) = sessionPill(session.status)
                StatusPill(t, bg, fg)
            }
            Text(
                r?.let { "pitch ${it.pitchMm ?: "-"}mm · 置信度 ${it.confidence ?: "-"}" }
                    ?: "通用采集 · ${session.frames.size} 帧",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
```

- [ ] **Step 2: Add the imports + the pill mapping helper**

Imports to add:
```kotlin
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.johnymoo.arverify.session.SessionStatus
import com.johnymoo.arverify.ui.components.SectionCard
import com.johnymoo.arverify.ui.components.StatusPill
import com.johnymoo.arverify.ui.theme.SfOk
import com.johnymoo.arverify.ui.theme.SfOkBg
import com.johnymoo.arverify.ui.theme.SfWait
import com.johnymoo.arverify.ui.theme.SfWaitBg
```

Helper at file scope:
```kotlin
private fun sessionPill(status: SessionStatus): Triple<String, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> =
    when (status) {
        SessionStatus.RECOGNIZED -> Triple("已识别", SfOkBg, SfOk)
        SessionStatus.EXPORTED -> Triple("已导出", SfOkBg, SfOk)
        SessionStatus.PENDING_UPLOAD -> Triple("待上传", SfWaitBg, SfWait)
        SessionStatus.NEEDS_MEASUREMENT -> Triple("待测量", SfWaitBg, SfWait)
        SessionStatus.FAILED -> Triple("失败", SfWaitBg, SfWait)
    }
```

Note: the outer `Column(Modifier.fillMaxSize().padding(16.dp))` stays; the back arrow now comes from the centralized app bar (Task 3), so no in-screen back button is needed.

- [ ] **Step 3: Device verification** — Task 12.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/session/SessionDetailScreen.kt
git commit -m "feat: session detail polish (conclusion SectionCard + StatusPill)"
```

---

## Task 7: Settings — grouped SettingRow card

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/ui/settings/SettingsScreen.kt`

- [ ] **Step 1: Replace the body with the version below** (drops the pseudo-title; groups server/debug/diagnostics into a `SectionCard` of `SettingRow`s; keeps the same `AppPrefs` save logic):

```kotlin
@Composable
fun SettingsScreen(nav: NavController) {
    val context = LocalContext.current
    val prefs = remember { AppPrefs(context) }
    val initial = remember { prefs.load() }
    var baseUrl by remember { mutableStateOf(initial.baseUrl) }
    var debugGallery by remember { mutableStateOf(initial.saveDebugRgbToGallery) }

    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = baseUrl, onValueChange = { baseUrl = it },
            label = { Text("服务器地址") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SectionCard {
            SettingRow(
                label = "调试：保存原图到相册",
                trailing = { Switch(checked = debugGallery, onCheckedChange = { debugGallery = it }) },
            )
            SettingRow(
                label = stringResource(R.string.settings_diagnostics),
                onClick = { nav.navigate(Routes.DIAGNOSTICS) },
                showDivider = false,
                trailing = { Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            )
        }
        Button(onClick = {
            prefs.setBaseUrl(baseUrl.trim())
            prefs.setSaveDebugRgbToGallery(debugGallery)
        }) { Text("保存") }
    }
}
```

Add imports:
```kotlin
import androidx.compose.foundation.layout.Arrangement
import com.johnymoo.arverify.ui.components.SectionCard
import com.johnymoo.arverify.ui.components.SettingRow
```
Remove the now-unused `Alignment` and `Row` imports.

- [ ] **Step 2: Device verification** — Task 12.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/settings/SettingsScreen.kt
git commit -m "feat: settings polish (grouped SettingRow card, drop pseudo-title)"
```

---

## Task 8: Diagnostics — status cards

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/ui/settings/DiagnosticsScreen.kt`

- [ ] **Step 1: Replace the body with the version below** (drops pseudo-title; ARCore/Depth as `SectionCard` rows with `StatusPill`; recommended route + device in cards):

```kotlin
@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val activity = context as? Activity
        if (activity == null) {
            Text("设备诊断暂不可用")
            return@Column
        }
        val report = remember(activity) { CapabilityChecker(activity).buildReport(activity) }
        CapabilityRow("ARCore", report.arcoreUsable)
        CapabilityRow("Depth API", report.depthAutomaticSupported)
        SectionCard {
            Text("推荐路线", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text(report.recommendedRoute.labelZh, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 2.dp))
        }
        if (report.notes.isNotEmpty()) {
            SectionCard {
                Text(report.notes.joinToString("\n"), color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CapabilityRow(label: String, ok: Boolean) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (ok) StatusPill("✓ 支持", SfOkBg, SfOk) else StatusPill("不支持", SfWaitBg, SfWait)
        }
    }
}
```

Add imports:
```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import com.johnymoo.arverify.ui.components.SectionCard
import com.johnymoo.arverify.ui.components.StatusPill
import com.johnymoo.arverify.ui.theme.SfOk
import com.johnymoo.arverify.ui.theme.SfOkBg
import com.johnymoo.arverify.ui.theme.SfWait
import com.johnymoo.arverify.ui.theme.SfWaitBg
```

- [ ] **Step 2: Device verification** — Task 12.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/ui/settings/DiagnosticsScreen.kt
git commit -m "feat: diagnostics polish (capability status cards)"
```

---

## Task 9: Result — port XML Activity to Compose

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/result/ResultActivity.kt`
- Delete: `app/src/main/res/layout/activity_result.xml`

- [ ] **Step 1: Replace `ResultActivity.kt` with the Compose version below** (uses `ScreenScaffold` from Plan 1 for top bar + back + insets; preserves the existing model-viewer WebView + `assets/{jobId}` contract note):

```kotlin
package com.johnymoo.arverify.result

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.johnymoo.arverify.net.RecognitionStatus
import com.johnymoo.arverify.net.UploadOutcome
import com.johnymoo.arverify.net.UploadResultHolder
import com.johnymoo.arverify.ui.components.SectionCard
import com.johnymoo.arverify.ui.components.StatusPill
import com.johnymoo.arverify.ui.theme.ScanForgeTheme
import com.johnymoo.arverify.ui.theme.SfMuted
import com.johnymoo.arverify.ui.theme.SfOk
import com.johnymoo.arverify.ui.theme.SfOkBg
import com.johnymoo.arverify.ui.components.ScreenScaffold

class ResultActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val outcome = UploadResultHolder.outcome as? UploadOutcome.Success
        val r = outcome?.result
        val jobId = r?.jobId
        // Contract note: confirm asset-id field with the pairing spec.
        val glbUrl = if (r?.status == RecognitionStatus.RECOGNIZED && !jobId.isNullOrBlank())
            UploadResultHolder.baseUrl.trimEnd('/') + "/assets/" + jobId else null

        setContent {
            ScanForgeTheme {
                ScreenScaffold(title = "识别结果", onBack = { finish() }) { pad ->
                    Column(Modifier.padding(pad).fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (r == null) {
                            Text("无识别结果"); return@Column
                        }
                        SectionCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    "${r.recognized?.system ?: "-"} · ${r.recognized?.kind ?: "-"} · " +
                                        "${r.recognized?.unitsX ?: "-"}×${r.recognized?.unitsY ?: "-"}",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                if (r.status == RecognitionStatus.RECOGNIZED) StatusPill("已识别", SfOkBg, SfOk)
                            }
                            Text(
                                "pitch ${r.recognized?.pitchMm ?: "-"}mm · 置信度 ${r.recognized?.confidence ?: "-"}",
                                color = SfMuted, style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        var showModel by remember { mutableStateOf(false) }
                        if (glbUrl != null) {
                            Button(onClick = { showModel = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("预览模型")
                            }
                        }
                        if (showModel && glbUrl != null) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        settings.javaScriptEnabled = true
                                        settings.allowFileAccess = true
                                        webViewClient = object : android.webkit.WebViewClient() {
                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                view?.evaluateJavascript("loadModel('$glbUrl')", null)
                                            }
                                        }
                                        loadUrl("file:///android_asset/viewer.html")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Row(modifier: Modifier, horizontalArrangement: Arrangement.Horizontal, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(modifier = modifier, horizontalArrangement = horizontalArrangement) { content() }
}
```

> Note: the small private `Row` shim above keeps the call sites terse; alternatively import `androidx.compose.foundation.layout.Row` and drop the shim. Pick one on device — do not keep both a shim and the import (name clash).

- [ ] **Step 2: Delete the old layout**

```bash
git rm app/src/main/res/layout/activity_result.xml
```

(`activity_result.xml` is referenced only by the old `ActivityResultBinding`; once the Activity is Compose, that binding is unused. If any leftover reference to `ActivityResultBinding` remains, the compile fails — remove it.)

- [ ] **Step 3: Device verification** — Task 12 (recognized result + 预览模型).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/result/ResultActivity.kt
git commit -m "feat: port ResultActivity to Compose (ScreenScaffold + back + model preview)"
```

---

## Task 10: Capture overlay — status-bar / nav-bar insets

Fixes the camera-overlay overlap: the top control `Row` currently uses `.padding(12.dp)` with no status-bar inset.

**Files:**
- Modify: `app/src/main/java/com/johnymoo/arverify/capture/CaptureScreen.kt`

- [ ] **Step 1: Add inset padding to top + bottom chrome**

Top chrome `Row` — change:
```kotlin
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
```
to:
```kotlin
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
```

Depth toggle chip — change its `.padding(top = 56.dp, end = 12.dp)` to also clear the status bar:
```kotlin
            modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 12.dp)
                .clickableNoRipple { holder.toggleDepth() },
```

Bottom chrome `Row` — change `.padding(16.dp)` to:
```kotlin
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp),
```

- [ ] **Step 2: Add imports**

```kotlin
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
```

- [ ] **Step 3: Device verification** — top mode/back/depth controls sit below the status bar; shutter row clears the nav bar. Task 12.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/johnymoo/arverify/capture/CaptureScreen.kt
git commit -m "fix: capture overlay respects status/navigation bar insets"
```

---

## Task 11: Full unit suite green (regression gate)

- [ ] **Step 1: Run all unit tests** (toolchain machine)

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — including the new `ScreenChromeTest` and all Plan 1 tests. A compile failure here means a leftover reference (e.g. `ActivityResultBinding`, removed `StatusBadge`, unused import causing an error) — fix it.

- [ ] **Step 2: Commit any fixups**

```bash
git add -A && git commit -m "fix: compile fixups after UI redesign"
```

---

## Task 12: End-to-end device verification (the 3 acceptance asks)

No code; acceptance gate on Mac + device. `./gradlew :app:installDebug`.

- [ ] **Status-bar overlap fixed** — Home, Library, Settings, Session Detail, Frame Viewer, Diagnostics, Result, and the Capture overlay: no content/controls collide with the system clock/notch.
- [ ] **Back buttons present** — Session Detail, Frame Viewer, Diagnostics show a top-bar back arrow that pops; Result + Measurement wizard show back; Capture shows "‹". Tab screens (Home/Library/Settings) correctly show no back.
- [ ] **质感 unified (style A)** — every screen uses the top bar + `SectionCard`/`StatusPill`/`SettingRow`/`EmptyState`; spacing/borders/pills are consistent with `screen-set.html`.
- [ ] **No regressions** — capture → upload → recognized → Result preview; needs_measurement → wizard (Plan 1); library filters + session delete/reupload still work.

- [ ] **Commit any device-tuning fixes**

```bash
git add -A && git commit -m "fix: device-tuning for app UI redesign"
```

---

## Self-Review (completed)

- **Spec coverage (spec Part B):** B.1 root-cause fixes → centralized top bar (Task 3) + capture insets (Task 10); B.2 shared base/components → Task 1 + `ScreenScaffold` (Plan 1); B.3 per-screen → Home (4), Library (5), Session Detail (6), Settings (7), Diagnostics (8), Result (9), Capture (10), Frame Viewer (auto via Task 3), Measurement Wizard (Plan 1). ✓
- **Placeholder scan:** no TBD/TODO; each code step ships complete code; UI tasks end in explicit device-verify (no local test is possible). The one pure unit (route→chrome) has a real TDD loop (Task 2).
- **Type consistency:** `ScreenChrome(titleRes, showBack)` + `topBarFor(route)` used in ScanForgeApp + test; `StatusPill(text, background, foreground)`, `SectionCard { }`, `SettingRow(label, onClick?, showDivider, trailing)`, `EmptyState(text)` used consistently in Tasks 4–10; tokens `SfBorder`/`SfDivider` from Plan 1. ✓
- **Known risk (can't compile here):** Compose API specifics — `Surface(onClick=…)` (Task 4), `TopAppBar`/`TopAppBarDefaults` experimental opt-in (Task 3), `statusBarsPadding`/`navigationBarsPadding` imports (Task 10), `AndroidView` WebView (Task 9), and the optional `Row` shim note (Task 9). Resolve on first desktop build.
```
