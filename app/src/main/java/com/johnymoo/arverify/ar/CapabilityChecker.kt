package com.johnymoo.arverify.ar

import android.app.Activity
import android.content.Context
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.johnymoo.arverify.model.ArCoreStatus
import com.johnymoo.arverify.model.CapabilityReport
import com.johnymoo.arverify.model.DeviceInfo
import com.johnymoo.arverify.model.RecommendedRoute
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Runs ARCore availability + Depth API support checks and assembles a CapabilityReport. */
class CapabilityChecker(private val context: Context) {

    fun availability(): ArCoreApk.Availability =
        ArCoreApk.getInstance().checkAvailability(context)

    /** Launches the Play Store install dialog if needed. Must be called from an Activity. */
    fun requestInstall(activity: Activity, userRequested: Boolean): ArCoreApk.InstallStatus =
        ArCoreApk.getInstance().requestInstall(activity, userRequested)

    data class DepthSupport(val automatic: Boolean, val rawDepth: Boolean, val note: String?)

    /** Creates a throwaway Session to probe depth support. Requires ARCore installed. */
    fun probeDepth(): DepthSupport {
        var session: Session? = null
        return try {
            session = Session(context)
            val auto = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
            val raw = session.isDepthModeSupported(Config.DepthMode.RAW_DEPTH_ONLY)
            DepthSupport(auto, raw, null)
        } catch (e: Exception) {
            DepthSupport(false, false, "Depth probe failed: ${e.javaClass.simpleName}")
        } finally {
            session?.close()
        }
    }

    fun buildReport(activity: Activity): CapabilityReport {
        val deviceInfo = DeviceInfo.collect(activity)
        val availability = availability()
        val status = mapStatus(availability)
        val notes = mutableListOf<String>()

        val depth = if (status == ArCoreStatus.SUPPORTED_INSTALLED) probeDepth()
        else DepthSupport(false, false, "ARCore 未安装，未探测 Depth")
        depth.note?.let { notes.add(it) }

        when (availability) {
            ArCoreApk.Availability.UNKNOWN_CHECKING,
            ArCoreApk.Availability.UNKNOWN_ERROR,
            ArCoreApk.Availability.UNKNOWN_TIMED_OUT ->
                notes.add("ARCore 可用性尚未确定（$availability），请再次点击「检查 ARCore」。")
            else -> {}
        }
        if (status == ArCoreStatus.SUPPORTED_NOT_INSTALLED) {
            notes.add("若安装失败：可从 OPPO 应用商店或侧载安装「Google Play Services for AR」。")
        }

        val route = RecommendedRoute.decide(
            arcoreOk = status == ArCoreStatus.SUPPORTED_INSTALLED,
            depthOk = depth.automatic
        )
        return CapabilityReport(
            deviceInfo = deviceInfo,
            arcoreStatus = status,
            depthAutomaticSupported = depth.automatic,
            rawDepthSupported = depth.raw,
            recommendedRoute = route,
            notes = notes,
            timestampIso = nowIso(),
        )
    }

    private fun mapStatus(a: ArCoreApk.Availability): ArCoreStatus = when (a) {
        ArCoreApk.Availability.SUPPORTED_INSTALLED -> ArCoreStatus.SUPPORTED_INSTALLED
        ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> ArCoreStatus.SUPPORTED_NOT_INSTALLED
        ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD -> ArCoreStatus.SUPPORTED_APK_TOO_OLD
        ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> ArCoreStatus.UNSUPPORTED
        else -> ArCoreStatus.UNKNOWN
    }

    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date())
}
