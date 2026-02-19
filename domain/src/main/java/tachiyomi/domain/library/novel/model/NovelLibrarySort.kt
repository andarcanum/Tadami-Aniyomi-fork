package tachiyomi.domain.library.novel.model

import tachiyomi.domain.library.manga.model.MangaLibrarySort
import tachiyomi.domain.library.model.FlagWithMask
import tachiyomi.domain.library.model.plus

data class NovelLibrarySort(
    val type: Type,
    val direction: Direction,
) : FlagWithMask {

    override val flag: Long
        get() = type + direction

    override val mask: Long
        get() = type.mask or direction.mask

    val isAscending: Boolean
        get() = direction == Direction.Ascending

    sealed class Type(
        override val flag: Long,
    ) : FlagWithMask {

        override val mask: Long = 0b00111100L

        data object Alphabetical : Type(0b00000000)
        data object LastRead : Type(0b00000100)
        data object LastUpdate : Type(0b00001000)
        data object UnreadCount : Type(0b00001100)
        data object TotalChapters : Type(0b00010000)
        data object LatestChapter : Type(0b00010100)
        data object ChapterFetchDate : Type(0b00011000)
        data object DateAdded : Type(0b00011100)
        data object TrackerMean : Type(0b000100000)
        data object Random : Type(0b00111100)

        companion object {
            fun valueOf(flag: Long): Type {
                return types.find { type -> type.flag == flag and type.mask } ?: default.type
            }
        }
    }

    sealed class Direction(
        override val flag: Long,
    ) : FlagWithMask {

        override val mask: Long = 0b01000000L

        data object Ascending : Direction(0b01000000)
        data object Descending : Direction(0b00000000)

        companion object {
            fun valueOf(flag: Long): Direction {
                return directions.find { direction -> direction.flag == flag and direction.mask } ?: default.direction
            }
        }
    }

    object Serializer {
        fun deserialize(serialized: String): NovelLibrarySort {
            return Companion.deserialize(serialized)
        }

        fun serialize(value: NovelLibrarySort): String {
            return value.serialize()
        }
    }

    companion object {
        val types by lazy {
            setOf(
                Type.Alphabetical,
                Type.LastRead,
                Type.LastUpdate,
                Type.UnreadCount,
                Type.TotalChapters,
                Type.LatestChapter,
                Type.ChapterFetchDate,
                Type.DateAdded,
                Type.TrackerMean,
                Type.Random,
            )
        }
        val directions by lazy { setOf(Direction.Ascending, Direction.Descending) }
        val default = NovelLibrarySort(Type.Alphabetical, Direction.Ascending)

        fun valueOf(flag: Long?): NovelLibrarySort {
            if (flag == null) return default
            return NovelLibrarySort(
                Type.valueOf(flag),
                Direction.valueOf(flag),
            )
        }

        fun deserialize(serialized: String): NovelLibrarySort {
            if (serialized.isEmpty()) return default
            return try {
                val mangaSort = MangaLibrarySort.Serializer.deserialize(serialized)
                NovelLibrarySort(
                    type = when (mangaSort.type) {
                        MangaLibrarySort.Type.Alphabetical -> Type.Alphabetical
                        MangaLibrarySort.Type.LastRead -> Type.LastRead
                        MangaLibrarySort.Type.LastUpdate -> Type.LastUpdate
                        MangaLibrarySort.Type.UnreadCount -> Type.UnreadCount
                        MangaLibrarySort.Type.TotalChapters -> Type.TotalChapters
                        MangaLibrarySort.Type.LatestChapter -> Type.LatestChapter
                        MangaLibrarySort.Type.ChapterFetchDate -> Type.ChapterFetchDate
                        MangaLibrarySort.Type.DateAdded -> Type.DateAdded
                        MangaLibrarySort.Type.TrackerMean -> Type.TrackerMean
                        MangaLibrarySort.Type.Random -> Type.Random
                    },
                    direction = when (mangaSort.direction) {
                        MangaLibrarySort.Direction.Ascending -> Direction.Ascending
                        MangaLibrarySort.Direction.Descending -> Direction.Descending
                    },
                )
            } catch (_: Exception) {
                default
            }
        }
    }

    fun serialize(): String {
        val mangaType = when (type) {
            Type.Alphabetical -> MangaLibrarySort.Type.Alphabetical
            Type.LastRead -> MangaLibrarySort.Type.LastRead
            Type.LastUpdate -> MangaLibrarySort.Type.LastUpdate
            Type.UnreadCount -> MangaLibrarySort.Type.UnreadCount
            Type.TotalChapters -> MangaLibrarySort.Type.TotalChapters
            Type.LatestChapter -> MangaLibrarySort.Type.LatestChapter
            Type.ChapterFetchDate -> MangaLibrarySort.Type.ChapterFetchDate
            Type.DateAdded -> MangaLibrarySort.Type.DateAdded
            Type.TrackerMean -> MangaLibrarySort.Type.TrackerMean
            Type.Random -> MangaLibrarySort.Type.Random
        }
        val mangaDirection = when (direction) {
            Direction.Ascending -> MangaLibrarySort.Direction.Ascending
            Direction.Descending -> MangaLibrarySort.Direction.Descending
        }
        return MangaLibrarySort.Serializer.serialize(MangaLibrarySort(mangaType, mangaDirection))
    }
}
