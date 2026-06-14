package com.johnymoo.arverify.measure

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.johnymoo.arverify.net.HttpUrlConnectionTransport
import com.johnymoo.arverify.net.ParametricBlockRequest
import com.johnymoo.arverify.net.ParametricBlockResult
import com.johnymoo.arverify.net.ParametricOutcome
import com.johnymoo.arverify.net.ParametricSubmission
import com.johnymoo.arverify.net.UploadOutcome
import com.johnymoo.arverify.net.UploadResultHolder
import com.johnymoo.arverify.ui.components.ScreenScaffold
import com.johnymoo.arverify.ui.theme.ScanForgeTheme
import com.johnymoo.arverify.ui.theme.SfError
import com.johnymoo.arverify.ui.theme.SfMuted
import com.johnymoo.arverify.ui.theme.SfOk
import com.johnymoo.arverify.ui.theme.SfOkBg
import com.johnymoo.arverify.ui.theme.SfOnBg
import com.johnymoo.arverify.ui.theme.SfSurface
import com.johnymoo.arverify.ui.theme.SfTeal
import com.johnymoo.arverify.ui.theme.SfTealOn
import com.johnymoo.arverify.ui.theme.SfWait
import com.johnymoo.arverify.ui.theme.SfWaitBg
import java.util.concurrent.Executors

class MeasurementWizardActivity : ComponentActivity() {
    private val io = Executors.newSingleThreadExecutor()
    override fun onDestroy() { io.shutdown(); super.onDestroy() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val outcome = UploadResultHolder.outcome as? UploadOutcome.Success
        val recognized = outcome?.result?.recognized
        val nm = outcome?.result?.needsMeasurement
        val baseUrl = UploadResultHolder.baseUrl
        val steps = MeasurementCatalog.stepsFor(nm?.fields?.mapNotNull { it.key } ?: emptyList())

        setContent {
            ScanForgeTheme {
                WizardRoot(
                    reason = nm?.reason,
                    recognizedKind = recognized?.kind,
                    recognizedUnits = recognized?.let { (it.unitsX ?: 0) to (it.unitsY ?: 0) },
                    recognizedConfidence = recognized?.confidence,
                    steps = steps,
                    onBack = { finish() },
                    onSubmit = { req, cb ->
                        io.execute {
                            val out = ParametricSubmission(HttpUrlConnectionTransport()).submit(baseUrl, req)
                            runOnUiThread { cb(out) }
                        }
                    },
                )
            }
        }
    }
}

private enum class Phase { CONFIRM, MEASURE, RESULT }

@Composable
private fun WizardRoot(
    reason: String?,
    recognizedKind: String?,
    recognizedUnits: Pair<Int, Int>?,
    recognizedConfidence: Double?,
    steps: List<FieldGuide>,
    onBack: () -> Unit,
    onSubmit: (ParametricBlockRequest, (ParametricOutcome) -> Unit) -> Unit,
) {
    var phase by remember { mutableStateOf(Phase.CONFIRM) }
    var system by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf(recognizedKind) } // backend suggestion, editable
    var unitsX by remember { mutableStateOf("") } // blank; not the untrusted 22
    var unitsY by remember { mutableStateOf("") }
    var stepIndex by remember { mutableStateOf(0) }
    val values = remember { mutableStateMapOf<String, String>() }
    var submitting by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ParametricBlockResult?>(null) }
    var submitError by remember { mutableStateOf<String?>(null) }

    val title = when (phase) {
        Phase.CONFIRM -> "建模确认"
        Phase.MEASURE -> "测量 ${stepIndex + 1}/${steps.size}"
        Phase.RESULT -> "提交结果"
    }
    val back: () -> Unit = when (phase) {
        Phase.CONFIRM -> onBack
        Phase.MEASURE -> { { if (stepIndex == 0) phase = Phase.CONFIRM else stepIndex-- } }
        Phase.RESULT -> onBack
    }

    ScreenScaffold(title = title, onBack = back) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (phase) {
                Phase.CONFIRM -> ConfirmPhase(
                    reason, recognizedKind, recognizedUnits, recognizedConfidence,
                    system, { system = it }, kind, { kind = it }, unitsX, { unitsX = it }, unitsY, { unitsY = it },
                    canStart = system != null && kind != null && unitsX.toUnit() != null && unitsY.toUnit() != null,
                    onStart = { phase = Phase.MEASURE; stepIndex = 0 },
                )
                Phase.MEASURE -> MeasurePhase(
                    step = steps[stepIndex], stepIndex = stepIndex, total = steps.size,
                    value = values[steps[stepIndex].key] ?: "",
                    onValue = { values[steps[stepIndex].key] = it },
                    crossCheck = MeasurementCrossCheck.check(values.numeric()),
                    onPrev = { if (stepIndex == 0) phase = Phase.CONFIRM else stepIndex-- },
                    onNext = {
                        if (stepIndex < steps.size - 1) {
                            stepIndex++
                        } else {
                            submitting = true
                            submitError = null
                            onSubmit(
                                ParametricBlockRequest(
                                    system!!, kind!!, unitsX.toUnit()!!, unitsY.toUnit()!!, values.numeric5(),
                                ),
                            ) { out ->
                                submitting = false
                                when (out) {
                                    is ParametricOutcome.Success -> { result = out.result; phase = Phase.RESULT }
                                    is ParametricOutcome.Failure -> submitError = out.message
                                }
                            }
                        }
                    },
                    submitting = submitting, submitError = submitError, isLast = stepIndex == steps.size - 1,
                )
                Phase.RESULT -> ResultPhase(result, onDone = onBack)
            }
        }
    }
}

private fun String.toUnit(): Int? =
    toIntOrNull()?.takeIf { it in MeasurementCatalog.MIN_UNITS..MeasurementCatalog.MAX_UNITS }

private fun Map<String, String>.numeric(): Map<String, Double?> = mapValues { it.value.toDoubleOrNull() }

private fun Map<String, String>.numeric5(): Map<String, Double> =
    MeasurementCatalog.CANONICAL_KEYS.associateWith { (this[it]?.toDoubleOrNull()) ?: 0.0 }

@Composable
private fun ConfirmPhase(
    reason: String?,
    recKind: String?,
    recUnits: Pair<Int, Int>?,
    recConf: Double?,
    system: String?,
    onSystem: (String) -> Unit,
    kind: String?,
    onKind: (String) -> Unit,
    unitsX: String,
    onX: (String) -> Unit,
    unitsY: String,
    onY: (String) -> Unit,
    canStart: Boolean,
    onStart: () -> Unit,
) {
    Surface(color = SfWaitBg, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            Text("⚠️ 自动识别不可信", color = SfWait, style = MaterialTheme.typography.titleSmall)
            Text("本次未能可信识别，需人工测量生成参数化模型。", color = SfOnBg, style = MaterialTheme.typography.bodySmall)
            if (!reason.isNullOrBlank()) {
                Text(
                    "后端原因：$reason", color = SfMuted, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    if (recKind != null || recUnits != null) {
        Surface(color = SfSurface, shape = MaterialTheme.shapes.medium) {
            Row(Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "本次识别：${recKind ?: "-"} · ${recUnits?.first ?: "-"}×${recUnits?.second ?: "-"}",
                    color = SfMuted, style = MaterialTheme.typography.bodySmall,
                )
                Text("置信度 ${recConf ?: 0.0} · 不可信", color = SfError, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Text("① 选择体系", style = MaterialTheme.typography.titleSmall)
    ChipRow(MeasurementCatalog.SYSTEMS.map { it.value to it.label }, system, onSystem)
    Text(
        "② 选择类型" + if (kind == recKind && recKind != null) "（识别建议·请核对）" else "",
        style = MaterialTheme.typography.titleSmall,
    )
    ChipRow(MeasurementCatalog.KINDS.map { it.value to (it.icon + " " + it.label) }, kind, onKind)
    Text(
        "③ 凸点排数（${MeasurementCatalog.MIN_UNITS}–${MeasurementCatalog.MAX_UNITS}）",
        style = MaterialTheme.typography.titleSmall,
    )
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        UnitField(unitsX, onX)
        Text("×", color = SfMuted)
        UnitField(unitsY, onY)
    }
    Text(
        "识别给的 ${recUnits?.first ?: "-"}×${recUnits?.second ?: "-"} 超出范围或不可信，已忽略，请按实物填写",
        color = SfError, style = MaterialTheme.typography.labelSmall,
    )
    Button(onClick = onStart, enabled = canStart, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("开始测量（5 步）")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(items: List<Pair<String, String>>, selected: String?, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (value, label) ->
            FilterChip(selected = selected == value, onClick = { onSelect(value) }, label = { Text(label) })
        }
    }
}

@Composable
private fun UnitField(value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValue(it.filter { c -> c.isDigit() }.take(2)) },
        modifier = Modifier.width(96.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        placeholder = { Text("排") },
    )
}

@Composable
private fun MeasurePhase(
    step: FieldGuide,
    stepIndex: Int,
    total: Int,
    value: String,
    onValue: (String) -> Unit,
    crossCheck: CrossCheckResult,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    submitting: Boolean,
    submitError: String?,
    isLast: Boolean,
) {
    LinearProgressIndicator(progress = { (stepIndex + 1f) / total }, modifier = Modifier.fillMaxWidth())
    MeasurementDiagram(step.view, step.code, Modifier.padding(vertical = 4.dp))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = SfTeal, shape = MaterialTheme.shapes.small) {
            Text(step.code, color = SfTealOn, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Text(step.label, style = MaterialTheme.typography.titleSmall)
    }
    Text(step.hint, color = SfMuted, style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        suffix = { Text("mm") },
        placeholder = { Text("输入测量值") },
    )
    val fieldErr = crossCheck.errors.firstOrNull { it.contains(step.code) }
    if (value.isNotBlank() && fieldErr != null) {
        Text(fieldErr, color = SfError, style = MaterialTheme.typography.labelSmall)
    }
    crossCheck.warnings.forEach { Text("⚠ $it", color = SfWait, style = MaterialTheme.typography.labelSmall) }
    if (submitError != null) {
        Text("提交失败：$submitError", color = SfError, style = MaterialTheme.typography.bodySmall)
    }
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPrev, modifier = Modifier.weight(1f)) { Text("上一步") }
        Button(
            onClick = onNext,
            modifier = Modifier.weight(1f),
            enabled = !submitting && (
                if (isLast) crossCheck.canSubmit else value.toDoubleOrNull()?.let { it > 0 } == true
                ),
        ) { Text(if (submitting) "提交中…" else if (isLast) "提交" else "下一步") }
    }
}

@Composable
private fun ResultPhase(result: ParametricBlockResult?, onDone: () -> Unit) {
    Surface(color = SfOkBg, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(12.dp)) {
            Text("✓ 已提交，后端生成中", color = SfOk, style = MaterialTheme.typography.titleSmall)
            Text("part_id：${result?.partId ?: "-"}", color = SfOnBg, style = MaterialTheme.typography.bodySmall)
            Text("job_id：${result?.jobId ?: "-"}", color = SfMuted, style = MaterialTheme.typography.labelSmall)
        }
    }
    val warns = result?.crossCheckWarnings ?: emptyList()
    if (warns.isNotEmpty()) {
        Surface(color = SfWaitBg, shape = MaterialTheme.shapes.medium) {
            Column(Modifier.padding(12.dp)) {
                Text("后端互验提醒", color = SfWait, style = MaterialTheme.typography.titleSmall)
                warns.forEach { Text("⚠ $it", color = SfOnBg, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("完成") }
}
