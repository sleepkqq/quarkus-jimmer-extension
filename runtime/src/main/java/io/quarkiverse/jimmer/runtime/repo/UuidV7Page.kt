package io.quarkiverse.jimmer.runtime.repo

import java.util.UUID

data class UuidV7Slice<T>(
    val rows: List<T>,
    val nextCursor: UUID?
) {
    val hasNext: Boolean
        get() = nextCursor != null
}

data class UuidV7Page<T>(
    val rows: List<T>,
    val nextCursor: UUID?,
    val totalRowCount: Long
) {
    val hasNext: Boolean
        get() = nextCursor != null
}
