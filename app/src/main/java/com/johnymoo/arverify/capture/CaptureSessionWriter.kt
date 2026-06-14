package com.johnymoo.arverify.capture

import android.graphics.Bitmap
import com.johnymoo.arverify.depth.DepthHeatmap
import com.johnymoo.arverify.depth.DepthRangeMm
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.session.CaptureMode
import com.johnymoo.arverify.session.CaptureSession
import com.johnymoo.arverify.session.CapturedFrame
import com.johnymoo.arverify.session.SessionManifestCodec
import com.johnymoo.arverify.session.SessionStatus
import com.johnymoo.arverify.imaging.Depth16PngWriter
import com.johnymoo.arverify.metadata.ArMetadata
import com.johnymoo.arverify.metadata.ArMetadataSerializer
import com.johnymoo.arverify.metadata.CameraIntrinsics
import com.johnymoo.arverify.metadata.CameraPose
import com.johnymoo.arverify.metadata.DepthDims
import com.johnymoo.arverify.metadata.DeviceMeta
import com.johnymoo.arverify.metadata.RecognitionFrameMeta
import java.io.File

class CaptureSessionWriter(
    private val rootDir: File,
    private val partId: String,
    private val mode: CaptureMode,
    private val createdAtEpochMs: Long,
    private val deviceModel: String,
    private val depthRange: DepthRangeMm,
    private val repo: CaptureLibraryRepository = CaptureLibraryRepository(rootDir),
    existingDir: File? = null,
    initialFrames: List<CapturedFrame> = emptyList(),
) {
    val dir: File = existingDir ?: File(rootDir, "${partId}_$createdAtEpochMs").apply { mkdirs() }
    private val frames = initialFrames.toMutableList()

    fun addFrame(
        slot: CaptureSlot,
        rgbJpeg: ByteArray,
        depthGridMm: IntArray?, depthW: Int = 0, depthH: Int = 0,
        distanceM: Double? = null, sharpness: Double? = null,
    ): List<CapturedFrame> {
        val i = frames.size
        val rgbName = "frame_${i}.jpg"
        File(dir, rgbName).writeBytes(rgbJpeg)

        var depthName: String? = null
        if (depthGridMm != null && depthW > 0 && depthH > 0) {
            depthName = "depth_${i}.png"
            File(dir, depthName).writeBytes(Depth16PngWriter.encode(depthGridMm, depthW, depthH))
            val colored = DepthHeatmap.toBitmap(depthGridMm, depthW, depthH, depthRange)
            File(dir, "depthc_${i}.png").outputStream().use { colored.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        frames.add(CapturedFrame(slot, rgbName, depthName, distanceM, sharpness))
        rewriteManifest(if (mode == CaptureMode.RECOGNITION) SessionStatus.PENDING_UPLOAD else SessionStatus.EXPORTED)
        return frames.toList()
    }

    fun writeRecognition(
        intrinsics: CameraIntrinsics, pose: CameraPose, distanceM: Double,
        depthGridMm: IntArray, depthW: Int, depthH: Int,
        rgbJpeg: ByteArray, arcoreVersion: String,
    ) {
        writeRecognition(
            intrinsics = intrinsics,
            pose = pose,
            scaleDistance = VisualScaleEstimator.fallback(
                arcoreDistanceM = distanceM,
                arcoreDistanceSource = TargetDistanceSource.DEPTH,
                visualReferenceStatus = "NOT_EVALUATED",
            ),
            depthGridMm = depthGridMm,
            depthW = depthW,
            depthH = depthH,
            rgbJpeg = rgbJpeg,
            arcoreVersion = arcoreVersion,
        )
    }

    fun writeRecognition(
        intrinsics: CameraIntrinsics,
        pose: CameraPose,
        scaleDistance: ScaleDistanceEstimate,
        depthGridMm: IntArray,
        depthW: Int,
        depthH: Int,
        rgbJpeg: ByteArray,
        arcoreVersion: String,
    ) {
        File(dir, "recognition_rgb.jpg").writeBytes(rgbJpeg)
        File(dir, "recognition_depth.png").writeBytes(Depth16PngWriter.encode(depthGridMm, depthW, depthH))
        val meta = ArMetadata(
            device = DeviceMeta(model = deviceModel, arcore = arcoreVersion),
            recognitionFrame = RecognitionFrameMeta(
                imageIntrinsics = intrinsics,
                depth = DepthDims(depthW, depthH),
                cameraPose = pose,
                distanceM = scaleDistance.distanceM,
                arcoreDistanceM = scaleDistance.arcoreDistanceM,
                arcoreDistanceSource = scaleDistance.arcoreDistanceSource.name,
                visualDistanceM = scaleDistance.visualDistanceM,
                visualReferenceWidthPx = scaleDistance.visualReferenceWidthPx,
                visualReferenceWidthMm = scaleDistance.visualReferenceWidthMm,
                visualReferenceStatus = scaleDistance.visualReferenceStatus,
                distanceSourceForScale = scaleDistance.distanceSourceForScale.name,
                distanceConfidence = scaleDistance.distanceConfidence.name,
                referenceModeEnabled = scaleDistance.referenceModeEnabled,
                manualMeasurementRecommended = scaleDistance.manualMeasurementRecommended,
            ),
            coarseHints = null,
        )
        File(dir, "ar_metadata.json").writeText(ArMetadataSerializer.toJson(meta))
    }

    fun snapshot(status: SessionStatus): CaptureSession =
        CaptureSession(partId, mode, createdAtEpochMs, deviceModel, status, frames.toList())

    private fun rewriteManifest(status: SessionStatus) = repo.writeManifest(dir, snapshot(status))

    companion object {
        fun resume(
            sessionDir: File,
            depthRange: DepthRangeMm,
            repo: CaptureLibraryRepository = CaptureLibraryRepository(
                sessionDir.parentFile ?: error("Cannot resume capture session without parent directory")
            ),
        ): CaptureSessionWriter {
            val rootDir = sessionDir.parentFile
                ?: error("Cannot resume capture session without parent directory")
            val manifestFile = File(sessionDir, CaptureLibraryRepository.MANIFEST)
            val session = SessionManifestCodec.fromJson(manifestFile.readText())
                ?: error("Cannot resume damaged capture session")
            return CaptureSessionWriter(
                rootDir = rootDir,
                partId = session.partId,
                mode = session.mode,
                createdAtEpochMs = session.createdAtEpochMs,
                deviceModel = session.deviceModel,
                depthRange = depthRange,
                repo = repo,
                existingDir = sessionDir,
                initialFrames = session.frames,
            )
        }
    }
}
