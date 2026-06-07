package com.johnymoo.arverify.capture

object CaptureButtonPolicy {
    fun isEnabled(step: WizardStep): Boolean = step != WizardStep.READY
}
