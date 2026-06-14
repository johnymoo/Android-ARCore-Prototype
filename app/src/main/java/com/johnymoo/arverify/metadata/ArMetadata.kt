package com.johnymoo.arverify.metadata

/** RGB camera intrinsics (pixels) + image dimensions, from frame.camera.getImageIntrinsics(). */
data class CameraIntrinsics(
    val fx: Double, val fy: Double, val cx: Double, val cy: Double,
    val width: Int, val height: Int,
)

/** Depth map dimensions; format fixed by the contract (16-bit mm/pixel). */
data class DepthDims(val width: Int, val height: Int, val format: String = "DEPTH16_MM")

/** Camera pose: translation t=[x,y,z] (m) and rotation quaternion q=[x,y,z,w]. */
data class CameraPose(val t: List<Double>, val q: List<Double>)

/** Device identity for the metadata header. */
data class DeviceMeta(val model: String, val arcore: String)

/** Optional on-device coarse hints (NON-authoritative; null this phase, see plan scope). */
data class CoarseHints(val roughUnitsX: Int, val roughUnitsY: Int, val roughPitchMm: Double)

/** The recognition (top-down stud) frame's metadata block. */
data class RecognitionFrameMeta(
    val imageIntrinsics: CameraIntrinsics,
    val depth: DepthDims,
    val cameraPose: CameraPose,
    /** Backward-compatible scale distance: visual reference when available, ARCore fallback otherwise. */
    val distanceM: Double,
    /** Explicit ARCore gate distance from depth or HitTest. */
    val arcoreDistanceM: Double = distanceM,
    val arcoreDistanceSource: String = "UNKNOWN",
    val visualDistanceM: Double? = null,
    val visualReferenceWidthPx: Double? = null,
    val visualReferenceWidthMm: Double = 33.84,
    val visualReferenceStatus: String = "NOT_EVALUATED",
    val distanceSourceForScale: String = "ARCORE_DISTANCE",
    val distanceConfidence: String = "LOW",
)

/** Full `ar_metadata` payload (spec §5). */
data class ArMetadata(
    val device: DeviceMeta,
    val recognitionFrame: RecognitionFrameMeta,
    val coarseHints: CoarseHints?,
)
