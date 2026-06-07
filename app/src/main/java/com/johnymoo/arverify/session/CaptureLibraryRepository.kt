package com.johnymoo.arverify.session

import java.io.File

data class SessionEntry(val dir: File, val session: CaptureSession)

class CaptureLibraryRepository(private val rootDir: File) {

    fun listSessions(): List<SessionEntry> {
        val dirs = rootDir.listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.mapNotNull { dir ->
            val mf = File(dir, MANIFEST)
            if (!mf.isFile) return@mapNotNull null
            val s = SessionManifestCodec.fromJson(mf.readText()) ?: return@mapNotNull null
            SessionEntry(dir, s)
        }.sortedByDescending { it.session.createdAtEpochMs }
    }

    fun writeManifest(dir: File, session: CaptureSession) {
        dir.mkdirs()
        val tmp = File(dir, TMP)
        tmp.writeText(SessionManifestCodec.toJson(session))
        val dest = File(dir, MANIFEST)
        if (!tmp.renameTo(dest)) {
            tmp.copyTo(dest, overwrite = true)
            tmp.delete()
        }
    }

    fun delete(entry: SessionEntry): Boolean = entry.dir.deleteRecursively()

    companion object {
        const val MANIFEST = "manifest.json"
        private const val TMP = "manifest.json.tmp"
    }
}
