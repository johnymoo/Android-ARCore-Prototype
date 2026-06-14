package com.johnymoo.arverify

enum class CameraPermissionPurpose(val deniedToast: String) {
    CHECK_DEPTH("需要相机权限才能探测 Depth API"),
    CAPTURE("需要相机权限才能采集"),
}
