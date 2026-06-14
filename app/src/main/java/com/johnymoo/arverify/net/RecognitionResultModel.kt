package com.johnymoo.arverify.net

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/** Backend status (spec §5). UNKNOWN covers missing/unrecognized status and parse failures. */
enum class RecognitionStatus { RECOGNIZED, NEEDS_MEASUREMENT, UNKNOWN }

/** Recognized fields; all nullable because needs_measurement responses may carry partial values. */
data class Recognized(
    val system: String?, val kind: String?,
    val unitsX: Int?, val unitsY: Int?,
    val pitchMm: Double?, val confidence: Double?,
)

/** One caliper field descriptor (backend-owned shape; parsed tolerantly). */
data class MeasurementField(val key: String?, val label: String?, val unit: String?)

/** Guided-caliper fallback payload. */
data class NeedsMeasurement(val fields: List<MeasurementField>, val guidance: String?)

/** Parsed `POST /api/v1/ar-captures` response. */
data class RecognitionResult(
    val captureId: String?,
    val jobId: String?,
    val status: RecognitionStatus,
    val recognized: Recognized?,
    val needsMeasurement: NeedsMeasurement?,
)

/** Tolerant Gson-tree parser; never throws on bad input (returns an UNKNOWN result). */
object RecognitionResultModel {

    fun parse(json: String): RecognitionResult {
        val root = try {
            JsonParser.parseString(json).takeIf { it.isJsonObject }?.asJsonObject
        } catch (e: Exception) { null } ?: return RecognitionResult(null, null, RecognitionStatus.UNKNOWN, null, null)

        return RecognitionResult(
            captureId = str(root, "capture_id"),
            jobId = str(root, "job_id"),
            status = when (str(root, "status")) {
                "recognized" -> RecognitionStatus.RECOGNIZED
                "needs_measurement" -> RecognitionStatus.NEEDS_MEASUREMENT
                else -> RecognitionStatus.UNKNOWN
            },
            recognized = obj(root, "recognized")?.let {
                Recognized(
                    system = str(it, "system"), kind = str(it, "kind"),
                    unitsX = int(it, "units_x"), unitsY = int(it, "units_y"),
                    pitchMm = dbl(it, "pitch_mm"), confidence = dbl(it, "confidence"),
                )
            },
            needsMeasurement = obj(root, "needs_measurement")?.let { nm ->
                val fields = arr(nm, "fields").map { el ->
                    when {
                        el.isJsonObject -> {
                            val fo = el.asJsonObject
                            MeasurementField(
                                key = str(fo, "key"),
                                label = str(fo, "label"),
                                unit = str(fo, "unit"),
                            )
                        }
                        el.isJsonPrimitive -> {
                            val key = el.asString
                            MeasurementField(key = key, label = key, unit = "mm")
                        }
                        else -> MeasurementField(key = null, label = null, unit = null)
                    }
                }
                NeedsMeasurement(fields = fields, guidance = str(nm, "guidance"))
            },
        )
    }

    private fun member(o: JsonObject, k: String): JsonElement? =
        o.get(k)?.takeIf { !it.isJsonNull }

    private fun str(o: JsonObject, k: String): String? =
        member(o, k)?.takeIf { it.isJsonPrimitive }?.asString

    private fun int(o: JsonObject, k: String): Int? =
        member(o, k)?.takeIf { it.isJsonPrimitive }?.asInt

    private fun dbl(o: JsonObject, k: String): Double? =
        member(o, k)?.takeIf { it.isJsonPrimitive }?.asDouble

    private fun obj(o: JsonObject, k: String): JsonObject? =
        member(o, k)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun arr(o: JsonObject, k: String): List<JsonElement> =
        member(o, k)?.takeIf { it.isJsonArray }?.asJsonArray?.toList() ?: emptyList()
}
