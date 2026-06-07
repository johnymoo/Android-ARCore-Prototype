package com.johnymoo.arverify.ui.session

import androidx.lifecycle.ViewModel
import com.johnymoo.arverify.session.CaptureSession
import com.johnymoo.arverify.session.SessionManifestCodec
import com.johnymoo.arverify.session.CaptureLibraryRepository
import com.johnymoo.arverify.session.SessionEntry
import java.io.File

class SessionDetailViewModel(private val repo: CaptureLibraryRepository) : ViewModel() {
    fun load(dir: File): CaptureSession? {
        val mf = File(dir, CaptureLibraryRepository.MANIFEST)
        if (!mf.isFile) return null
        return SessionManifestCodec.fromJson(mf.readText())
    }

    fun delete(dir: File, session: CaptureSession) = repo.delete(SessionEntry(dir, session))
}
