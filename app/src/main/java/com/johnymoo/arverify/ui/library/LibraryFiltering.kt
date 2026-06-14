package com.johnymoo.arverify.ui.library

import com.johnymoo.arverify.session.SessionEntry
import com.johnymoo.arverify.session.SessionStatus

enum class LibraryFilter { ALL, RECOGNIZED, PENDING }

object LibraryFiltering {
    fun apply(entries: List<SessionEntry>, filter: LibraryFilter): List<SessionEntry> {
        val visibleEntries = entries.filter { it.session.frames.isNotEmpty() }
        return when (filter) {
            LibraryFilter.ALL -> visibleEntries
            LibraryFilter.RECOGNIZED -> visibleEntries.filter { it.session.status == SessionStatus.RECOGNIZED }
            LibraryFilter.PENDING -> visibleEntries.filter {
                it.session.status == SessionStatus.PENDING_UPLOAD ||
                    it.session.status == SessionStatus.NEEDS_MEASUREMENT
            }
        }
    }
}
