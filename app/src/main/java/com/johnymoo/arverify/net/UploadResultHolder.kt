package com.johnymoo.arverify.net

/** Hands the last upload outcome + base URL to ResultActivity/MeasurementFormActivity (prototype-simple). */
object UploadResultHolder {
    @Volatile var outcome: UploadOutcome? = null
    @Volatile var baseUrl: String = ""
}
