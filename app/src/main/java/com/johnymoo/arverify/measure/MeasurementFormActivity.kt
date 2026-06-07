package com.johnymoo.arverify.measure

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.johnymoo.arverify.databinding.ActivityMeasurementFormBinding
import com.johnymoo.arverify.net.HttpUrlConnectionTransport
import com.johnymoo.arverify.net.MeasurementField
import com.johnymoo.arverify.net.RecognitionResult
import com.johnymoo.arverify.net.UploadOutcome
import com.johnymoo.arverify.net.UploadResultHolder
import java.util.concurrent.Executors

class MeasurementFormActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMeasurementFormBinding
    private val inputs = LinkedHashMap<String, EditText>()
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMeasurementFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val outcome = UploadResultHolder.outcome as? UploadOutcome.Success
        val nm = outcome?.result?.needsMeasurement
        binding.tvGuidance.text = nm?.guidance ?: "请按提示填写测量值（毫米）"

        val fields = nm?.fields ?: emptyList()
        fields.forEach { addFieldRow(it) }
        binding.btnSubmit.setOnClickListener { onSubmit(outcome?.result, fields) }
    }

    override fun onDestroy() { io.shutdown(); super.onDestroy() }

    private fun addFieldRow(field: MeasurementField) {
        val key = field.key ?: field.label ?: return
        val label = TextView(this).apply {
            text = listOfNotNull(field.label ?: field.key, field.unit?.let { "（$it）" }).joinToString("")
            textSize = 15f
        }
        val edit = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = "毫米"
        }
        binding.fieldsContainer.addView(label)
        binding.fieldsContainer.addView(edit, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        inputs[key] = edit
    }

    private fun onSubmit(result: RecognitionResult?, fields: List<MeasurementField>) {
        val required = fields.mapNotNull { it.key ?: it.label }
        val values: Map<String, Double?> = inputs.mapValues { (_, e) -> e.text.toString().toDoubleOrNull() }
        val err = MeasurementValidator.firstError(required, values)
        if (err != null) { toast(err); return }

        val partId = result?.captureId ?: "part"
        val kind = result?.recognized?.kind ?: "brick"
        // Contract note: /parametric-blocks body schema is backend-owned (confirm with pairing spec).
        val body = buildString {
            append("{\"part_id\":\"").append(partId).append("\",")
            append("\"kind\":\"").append(kind).append("\",")
            append("\"measurements\":{")
            append(required.joinToString(",") { k -> "\"$k\":${values[k]}" })
            append("}}")
        }.toByteArray(Charsets.UTF_8)
        val url = UploadResultHolder.baseUrl.trimEnd('/') + "/parametric-blocks"

        binding.btnSubmit.isEnabled = false
        io.execute {
            val resp = try {
                HttpUrlConnectionTransport().post(url, "application/json", body)
            } catch (e: Exception) { null }
            runOnUiThread {
                binding.btnSubmit.isEnabled = true
                if (resp != null && resp.code in 200..299) toast("已提交，后端生成中")
                else toast("提交失败：${resp?.code ?: "网络错误"}")
            }
        }
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
