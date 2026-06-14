package com.johnymoo.arverify.net

import android.util.Log
import com.google.gson.JsonParser
import com.johnymoo.arverify.measure.MeasurementCatalog
import java.util.UUID

/** Input for POST /api/v1/parametric-blocks (rawMeasurementsMm keyed by the 5 canonical keys). */
data class ParametricBlockRequest(
    val system: String,
    val kind: String,
    val unitsX: Int,
    val unitsY: Int,
    val rawMeasurementsMm: Map<String, Double>,
)

/** Parsed 201 body (tolerant; fields the wizard's result screen needs). */
data class ParametricBlockResult(
    val captureId: String?,
    val jobId: String?,
    val partId: String?,
    val status: String?,
    val crossCheckWarnings: List<String>,
)

sealed class ParametricOutcome {
    data class Success(val result: ParametricBlockResult, val rawBody: String) : ParametricOutcome()
    data class Failure(val message: String, val code: Int?) : ParametricOutcome()
}

/**
 * Builds the parametric-block multipart and POSTs it. Mirrors
 * BrickStudio apps/web/src/features/parametric/api.ts: system/kind/units_x/units_y +
 * raw_measurements_mm (JSON string); zero photos (optional, not used for
 * reconstruction); part_id omitted (backend defaults it).
 */
class ParametricSubmission(
    private val transport: HttpTransport,
    private val boundaryProvider: () -> String = { "param" + UUID.randomUUID().toString().replace("-", "") },
) {
    fun submit(baseUrl: String, req: ParametricBlockRequest): ParametricOutcome {
        val url = baseUrl.trimEnd('/') + ENDPOINT
        val mb = MultipartBuilder(boundaryProvider())
            .addFormField("system", req.system)
            .addFormField("kind", req.kind)
            .addFormField("units_x", req.unitsX.toString())
            .addFormField("units_y", req.unitsY.toString())
            .addFormField("raw_measurements_mm", rawMeasurementsJson(req.rawMeasurementsMm))
        val body = mb.build()
        return try {
            val resp = transport.post(url, mb.contentType(), body)
            when {
                resp.code in 200..299 ->
                    ParametricOutcome.Success(parse(resp.body), resp.body)
                else ->
                    ParametricOutcome.Failure("HTTP ${resp.code}: ${shortBody(resp.body)}", resp.code)
            }
        } catch (e: Exception) {
            Log.w(TAG, "parametric submit failed", e)
            ParametricOutcome.Failure(e.javaClass.simpleName, null)
        }
    }

    companion object {
        const val ENDPOINT = "/api/v1/parametric-blocks"
        private const val TAG = "ParametricSubmission"

        /** Serialize the 5 caliper values in canonical key order as a JSON object string. */
        fun rawMeasurementsJson(values: Map<String, Double>): String =
            MeasurementCatalog.CANONICAL_KEYS.joinToString(
                prefix = "{", postfix = "}", separator = ",",
            ) { k -> "\"$k\":${values[k]}" }

        private fun parse(body: String): ParametricBlockResult {
            val root = try {
                JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
            } catch (e: Exception) { null }
            fun s(k: String) = root?.get(k)?.takeIf { it.isJsonPrimitive }?.asString
            val warns = root?.get("cross_check_warnings")?.takeIf { it.isJsonArray }
                ?.asJsonArray?.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString } ?: emptyList()
            return ParametricBlockResult(
                captureId = s("capture_id"), jobId = s("job_id"),
                partId = s("part_id"), status = s("status"), crossCheckWarnings = warns,
            )
        }

        private fun shortBody(b: String) =
            b.replace(Regex("\\s+"), " ").trim().take(200).ifBlank { "no response body" }
    }
}
