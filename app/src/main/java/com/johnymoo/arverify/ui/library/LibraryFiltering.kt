package com.johnymoo.arverify.ui.library

import com.johnymoo.arverify.session.SessionEntry
import com.johnymoo.arverify.session.SessionStatus

enum class LibraryFilter { ALL, RECOGNIZED, PENDING }

object LibraryFiltering {
    fun apply(entries: List<SessionEntry>, filter: LibraryFilter): List<SessionEntry> = when (filter) {
        LibraryFilter.ALL -> entries
        LibraryFilter.RECOGNIZED -> entries.filter { it.session.status == SessionStatus.RECOGNIZED }
        LibraryFilter.PENDING -> entries.filter {
            it.session.status == SessionStatus.PENDING_UPLOAD ||
                it.session.status == SessionStatus.NEEDS_MEASUREMENT
        }
    }
}
