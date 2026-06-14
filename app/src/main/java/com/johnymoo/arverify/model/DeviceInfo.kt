package com.johnymoo.arverify.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build

/** Plain device facts captured for the compatibility report. */
data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val sdkInt: Int,
    val fingerprint: String,
    val glesVersion: String,
) {
    companion object {
        fun collect(context: Context): DeviceInfo {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val gles = am.deviceConfigurationInfo.glEsVersion ?: "unknown"
            return DeviceInfo(
                manufacturer = Build.MANUFACTURER ?: "",
                model = Build.MODEL ?: "",
                device = Build.DEVICE ?: "",
                androidRelease = Build.VERSION.RELEASE ?: "",
                sdkInt = Build.VERSION.SDK_INT,
                fingerprint = Build.FINGERPRINT ?: "",
                glesVersion = gles,
            )
        }
    }
}
