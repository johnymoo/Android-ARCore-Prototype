package com.johnymoo.arverify

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AndroidManifestContractTest {
    @Test fun declaresInternetPermissionForUpload() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(
            "Capture upload requires android.permission.INTERNET",
            manifest.contains("""android.permission.INTERNET"""),
        )
    }
}
