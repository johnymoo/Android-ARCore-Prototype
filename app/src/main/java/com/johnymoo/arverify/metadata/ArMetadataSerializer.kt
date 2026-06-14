package com.johnymoo.arverify.metadata

/** Hand-rolled, deterministic compact JSON matching the contract in spec §5 (golden-tested). */
object ArMetadataSerializer {

    fun toJson(meta: ArMetadata): String = buildString {
        append('{')
        append("\"device\":").append(device(meta.device)).append(',')
        append("\"recognition_frame\":").append(frame(meta.recognitionFrame))
        meta.coarseHints?.let { append(",\"coarse_hints\":").append(hints(it)) }
        append('}')
    }

    private fun device(d: DeviceMeta) = buildString {
        append('{')
        append("\"model\":\"").append(esc(d.model)).append("\",")
        append("\"arcore\":\"").append(esc(d.arcore)).append('"')
        append('}')
    }

    private fun frame(f: RecognitionFrameMeta) = buildString {
        val i = f.imageIntrinsics
        append('{')
        append("\"image_intrinsics\":{")
        append("\"fx\":").append(num(i.fx)).append(',')
        append("\"fy\":").append(num(i.fy)).append(',')
        append("\"cx\":").append(num(i.cx)).append(',')
        append("\"cy\":").append(num(i.cy)).append(',')
        append("\"width\":").append(i.width).append(',')
        append("\"height\":").append(i.height)
        append("},")
        append("\"depth\":{")
        append("\"width\":").append(f.depth.width).append(',')
        append("\"height\":").append(f.depth.height).append(',')
        append("\"format\":\"").append(esc(f.depth.format)).append('"')
        append("},")
        append("\"camera_pose\":{")
        append("\"t\":").append(numArray(f.cameraPose.t)).append(',')
        append("\"q\":").append(numArray(f.cameraPose.q))
        append("},")
        append("\"distance_m\":").append(num(f.distanceM)).append(',')
        append("\"arcore_distance_m\":").append(num(f.arcoreDistanceM)).append(',')
        append("\"arcore_distance_source\":\"").append(esc(f.arcoreDistanceSource)).append("\",")
        append("\"visual_distance_m\":").append(numOrNull(f.visualDistanceM)).append(',')
        append("\"visual_reference_width_px\":").append(numOrNull(f.visualReferenceWidthPx)).append(',')
        append("\"visual_reference_width_mm\":").append(num(f.visualReferenceWidthMm)).append(',')
        append("\"visual_reference_status\":\"").append(esc(f.visualReferenceStatus)).append("\",")
        append("\"distance_source_for_scale\":\"").append(esc(f.distanceSourceForScale)).append("\",")
        append("\"distance_confidence\":\"").append(esc(f.distanceConfidence)).append('"')
        append('}')
    }

    private fun hints(h: CoarseHints) = buildString {
        append('{')
        append("\"rough_units_x\":").append(h.roughUnitsX).append(',')
        append("\"rough_units_y\":").append(h.roughUnitsY).append(',')
        append("\"rough_pitch_mm\":").append(num(h.roughPitchMm))
        append('}')
    }

    private fun numArray(xs: List<Double>): String =
        xs.joinToString(separator = ",", prefix = "[", postfix = "]") { num(it) }

    /** Whole doubles print as integers ("0", "1"); others as their natural decimal ("0.25", "16.2"). */
    private fun num(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

    private fun numOrNull(d: Double?): String = d?.let { num(it) } ?: "null"

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
