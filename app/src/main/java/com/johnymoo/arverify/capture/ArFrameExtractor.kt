package com.johnymoo.arverify.capture

import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import com.google.ar.core.Frame
import com.johnymoo.arverify.metadata.CameraIntrinsics
import com.johnymoo.arverify.metadata.CameraPose
import java.io.ByteArrayOutputStream
import java.nio.ByteOrder

/** Extracts pure data (JPEG bytes, depth mm grid, intrinsics, pose, luma) from ARCore frames/images. */
object ArFrameExtractor {

    data class Grid(val values: IntArray, val width: Int, val height: Int)

    /** YUV_420_888 camera image → JPEG bytes. Caller must close the image. */
    fun rgbJpeg(image: Image, quality: Int = 90): ByteArray {
        val nv21 = yuv420ToNv21(image)
        val yuv = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuv.compressToJpeg(Rect(0, 0, image.width, image.height), quality, out)
        return out.toByteArray()
    }

    /** JPEG bytes -> pure RGB image for visual reference detection. Android runtime bridge only. */
    fun rgbPixelsFromJpeg(jpeg: ByteArray): RgbImage? {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            return RgbImage(bitmap.width, bitmap.height, pixels)
        } finally {
            bitmap.recycle()
        }
    }

    /** ARCore 16-bit depth image → row-major millimeter grid. */
    fun depthGridMm(depth: Image): Grid {
        val w = depth.width
        val h = depth.height
        val plane = depth.planes[0]
        val shorts = plane.buffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val rowStrideShorts = plane.rowStride / 2
        val mm = IntArray(w * h)
        for (y in 0 until h) {
            val rowStart = y * rowStrideShorts
            for (x in 0 until w) {
                val raw = shorts.get(rowStart + x).toInt() and 0xFFFF
                mm[y * w + x] = depth16Millimeters(raw)
            }
        }
        return Grid(mm, w, h)
    }

    fun depth16Millimeters(rawSample: Int): Int = rawSample and 0x1FFF

    /** Y plane of a YUV image → luma grid (0..255), for the sharpness metric. */
    fun lumaGrid(image: Image): Grid {
        val w = image.width
        val h = image.height
        val plane = image.planes[0]
        val buf = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val luma = IntArray(w * h)
        for (y in 0 until h) {
            val rowStart = y * rowStride
            for (x in 0 until w) {
                luma[y * w + x] = buf.get(rowStart + x * pixelStride).toInt() and 0xFF
            }
        }
        return Grid(luma, w, h)
    }

    fun intrinsics(frame: Frame): CameraIntrinsics {
        val i = frame.camera.imageIntrinsics
        val f = i.focalLength      // [fx, fy]
        val pp = i.principalPoint  // [cx, cy]
        val dim = i.imageDimensions // [width, height]
        return CameraIntrinsics(
            fx = f[0].toDouble(), fy = f[1].toDouble(),
            cx = pp[0].toDouble(), cy = pp[1].toDouble(),
            width = dim[0], height = dim[1],
        )
    }

    fun pose(frame: Frame): CameraPose {
        val p = frame.camera.pose
        val t = FloatArray(3).also { p.getTranslation(it, 0) }
        val q = FloatArray(4).also { p.getRotationQuaternion(it, 0) } // [x, y, z, w]
        return CameraPose(
            t = listOf(t[0].toDouble(), t[1].toDouble(), t[2].toDouble()),
            q = listOf(q[0].toDouble(), q[1].toDouble(), q[2].toDouble(), q[3].toDouble()),
        )
    }

    private fun yuv420ToNv21(image: Image): ByteArray {
        val w = image.width
        val h = image.height
        val ySize = w * h
        val nv21 = ByteArray(ySize + ySize / 2)

        val yPlane = image.planes[0]
        val yBuf = yPlane.buffer
        val yRowStride = yPlane.rowStride
        var pos = 0
        for (row in 0 until h) {
            val rowStart = row * yRowStride
            for (col in 0 until w) nv21[pos++] = yBuf.get(rowStart + col)
        }

        // Interleave V,U (NV21 order) from the chroma planes.
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        val uBuf = uPlane.buffer
        val vBuf = vPlane.buffer
        val uvRowStride = uPlane.rowStride
        val uvPixStride = uPlane.pixelStride
        val cw = w / 2
        val ch = h / 2
        for (row in 0 until ch) {
            val uRow = row * uvRowStride
            val vRow = row * uvRowStride
            for (col in 0 until cw) {
                nv21[pos++] = vBuf.get(vRow + col * uvPixStride)
                nv21[pos++] = uBuf.get(uRow + col * uvPixStride)
            }
        }
        return nv21
    }
}
