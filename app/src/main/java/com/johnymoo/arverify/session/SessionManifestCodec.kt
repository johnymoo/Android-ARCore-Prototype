package com.johnymoo.arverify.session

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.johnymoo.arverify.capture.CaptureSlot

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
